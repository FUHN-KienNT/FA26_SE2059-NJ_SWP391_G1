package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class EssayAnswerCodecTest {
    private static final String XML =
            "<mxGraphModel><root><mxCell id=\"0\"/></root></mxGraphModel>";
    private static final String TASKS =
            """
            [{"task_order":7,"title":"Question","description":"","maximum_points":10,"requirements":[
              {"requirement_order":9,"title":"Explain","maximum_points":2,"answer_type":"TEXT"},
              {"requirement_order":3,"title":"Draw","maximum_points":3,"answer_type":"DIAGRAM"},
              {"requirement_order":5,"title":"Both","maximum_points":5,"answer_type":"BOTH"}]}]
            """;

    @Test
    void explicitlyKeyedAnswersAreRestoredByIdsAndMissingAnswersBecomeBlank() {
        var content = EssayAnswerCodec.normalize(TASKS, payload(row(7, 9, "Explanation", "", "")));

        assertEquals(3, content.answers().size());
        assertEquals("Explanation", content.answers().get("7:9").text());
        assertEquals("", content.answers().get("7:3").text());
        assertEquals("", content.answers().get("7:5").xml());
        var rows =
                JsonParser.parseString(content.json()).getAsJsonObject().getAsJsonArray("answers");

        assertEquals(
                List.of(3, 5, 9),
                rows.asList().stream()
                        .map(x -> x.getAsJsonObject().get("requirement_order").getAsInt())
                        .toList());
        assertEquals(content.json(), EssayAnswerCodec.normalize(TASKS, content.json()).json());
        assertThrows(UnsupportedOperationException.class, () -> content.answers().clear());
    }

    @Test
    void blankAndLegacyQuestionContentGetOneTextAnswerPerRequirement() {
        assertEquals(3, EssayAnswerCodec.normalize(TASKS, null).answers().size());
        var old =
                EssayAnswerCodec.normalize(
                        "[{\"task_order\":2,\"title\":\"Legacy\",\"maximum_points\":5}]", "");

        assertEquals(1, old.answers().size());
        assertTrue(old.answers().containsKey("2:1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> EssayAnswerCodec.normalize(TASKS, "old free-form response"));
        assertThrows(
                IllegalArgumentException.class,
                () -> EssayAnswerCodec.normalize(TASKS, "{\"format\":\"legacy\",\"answers\":[]}"));
    }

    @Test
    void foreignDuplicateAndNonIntegerIdsCannotBeMappedByPosition() {
        for (var invalid :
                List.of(
                        payload(row(1, 9, "foreign task", "", "")),
                        payload(row(7, 1, "foreign requirement", "", "")),
                        payload(row(7, 9, "first", "", ""), row(7, 9, "second", "", ""))))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> EssayAnswerCodec.normalize(TASKS, invalid));

        for (String id : List.of("0", "-1", "7.0", "7e0", "\"7\"", "null", "10000")) {
            String input =
                    "{\"format\":\"ams-essay-v2\",\"answers\":[{\"task_order\":"
                            + id
                            + ",\"requirement_order\":9,\"text\":\"answer\"}]}";

            assertThrows(
                    IllegalArgumentException.class,
                    () -> EssayAnswerCodec.normalize(TASKS, input),
                    id);
        }
    }

    @Test
    void duplicateJsonKeysTrailingValuesAndExcessiveNestingAreRejected() {
        for (String input :
                List.of(
                        "{\"format\":\"ams-essay-v2\",\"format\":\"ams-essay-v2\",\"answers\":[]}",
                        "{\"format\":\"ams-essay-v2\",\"answers\":[{\"task_order\":7,\"requirement_order\":9,\"text\":\"old\",\"text\":\"new\"}]}",
                        "{\"format\":\"ams-essay-v2\",\"answers\":[],\"extra\":{\"x\":1,\"x\":2}}",
                        "{\"format\":\"ams-essay-v2\",\"answers\":[]} {}",
                        "{\"format\":\"ams-essay-v2\",\"answers\":[],\"extra\":"
                                + "[".repeat(18)
                                + "0"
                                + "]".repeat(18)
                                + "}"))
            assertThrows(
                    IllegalArgumentException.class, () -> EssayAnswerCodec.normalize(TASKS, input));
    }

    @Test
    void textAndDiagramRestrictionsAreEnforcedAndBothPreservesEachRepresentation()
            throws Exception {
        String png = png(2, 2);

        assertThrows(
                IllegalArgumentException.class,
                () -> EssayAnswerCodec.normalize(TASKS, payload(row(7, 9, "text", XML, png))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        EssayAnswerCodec.normalize(
                                TASKS, payload(row(7, 3, "not allowed", XML, png))));
        assertThrows(
                IllegalArgumentException.class,
                () -> EssayAnswerCodec.normalize(TASKS, payload(row(7, 5, "text", XML, ""))));
        assertThrows(
                IllegalArgumentException.class,
                () -> EssayAnswerCodec.normalize(TASKS, payload(row(7, 5, "text", "", png))));
        var content =
                EssayAnswerCodec.normalize(
                        TASKS, payload(row(7, 5, "Reasoning", XML, png), row(7, 3, "", XML, png)));

        assertEquals("Reasoning", content.answers().get("7:5").text());
        assertEquals(XML, content.answers().get("7:5").xml());
        assertEquals(png, content.answers().get("7:3").image());
    }

    @Test
    void plantUmlRoundTripsByRequirementWithoutChangingOlderDrawIoAnswers() throws Exception {
        String source = "@startuml\nSinhVien -> HeThong : Nộp bài\n@enduml";
        String png = png(2, 2);
        JsonObject plant = row(7, 5, "Giải thích luồng nộp bài.", "", png);

        plant.addProperty("diagram_plantuml", source);
        var normalized = EssayAnswerCodec.normalize(TASKS, payload(plant, row(7, 3, "", XML, png)));
        var answer = normalized.answers().get("7:5");

        assertEquals(PlantUmlSource.normalize(source), answer.plantuml());
        assertEquals("", answer.xml());
        assertEquals(png, answer.image());
        assertEquals("Giải thích luồng nộp bài.", answer.text());
        assertEquals(XML, normalized.answers().get("7:3").xml());
        assertEquals("", normalized.answers().get("7:3").plantuml());
        assertEquals(
                normalized.json(), EssayAnswerCodec.normalize(TASKS, normalized.json()).json());
    }

    @Test
    void plantUmlMustMatchAnswerTypeAndHaveOneEditableSourcePlusPng() throws Exception {
        String source = "@startuml\nA -> B : Submit\n@enduml";
        String png = png(2, 2);
        JsonObject textOnly = row(7, 9, "", "", png);

        textOnly.addProperty("diagram_plantuml", source);
        JsonObject twoEditors = row(7, 5, "", XML, png);

        twoEditors.addProperty("diagram_plantuml", source);
        JsonObject noPreview = row(7, 5, "", "", "");

        noPreview.addProperty("diagram_plantuml", source);
        JsonObject unsafe = row(7, 5, "", "", png);

        unsafe.addProperty(
                "diagram_plantuml", "@startuml\n!include https://example.test/private\n@enduml");

        for (var invalid : List.of(textOnly, twoEditors, noPreview, unsafe))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> EssayAnswerCodec.normalize(TASKS, payload(invalid)));
    }

    @Test
    void pngMustDecodeAsABoundedRasterInsteadOfSvgBase64OrJunk() throws Exception {
        assertDoesNotThrow(() -> EssayAnswerCodec.validateImage(png(1, 1)));
        String svg =
                "data:image/svg+xml;base64,"
                        + Base64.getEncoder()
                                .encodeToString("<svg/>".getBytes(StandardCharsets.UTF_8));
        String fakePng =
                "data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[40]);

        for (String image :
                List.of(
                        svg,
                        fakePng,
                        "data:image/png;base64,%%%",
                        "https://example.test/image.png",
                        "data:image/png;base64,"
                                + Base64.getEncoder()
                                        .encodeToString(new byte[EssayAnswerCodec.MAX_IMAGE + 1]),
                        png(4097, 1)))
            assertThrows(
                    IllegalArgumentException.class, () -> EssayAnswerCodec.validateImage(image));

        byte[] header = new byte[24];

        header[0] = (byte) 137;
        header[1] = 80;
        header[2] = 78;
        header[3] = 71;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        EssayAnswerCodec.validateImage(
                                "data:image/png;base64,"
                                        + Base64.getEncoder().encodeToString(header)));
    }

    @Test
    void xmlAcceptsDrawIoRootsAndRejectsDoctypeSvgJunkAndOversizedMarkup() {
        assertDoesNotThrow(() -> EssayAnswerCodec.validateXml(XML));
        assertDoesNotThrow(
                () ->
                        EssayAnswerCodec.validateXml(
                                "<mxfile><diagram name=\"Page 1\">encoded-model</diagram></mxfile>"));

        for (String xml :
                List.of(
                        "not xml",
                        "<mxGraphModel>",
                        "<svg xmlns=\"http://www.w3.org/2000/svg\"/>",
                        "<!DOCTYPE mxfile [<!ENTITY xxe SYSTEM \"file:///nonexistent-essay-codec-fixture\">]><mxfile>&xxe;</mxfile>",
                        "<mxfile>" + "x".repeat(EssayAnswerCodec.MAX_XML) + "</mxfile>"))
            assertThrows(IllegalArgumentException.class, () -> EssayAnswerCodec.validateXml(xml));
    }

    @Test
    void textLimitCountsUnicodeCodePointsAndPayloadLimitCountsUtf8Bytes() {
        String unicode = "😀".repeat(10_000);

        assertEquals(
                unicode,
                EssayAnswerCodec.normalize(TASKS, payload(row(7, 9, unicode, "", "")))
                        .answers()
                        .get("7:9")
                        .text());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        EssayAnswerCodec.normalize(
                                TASKS, payload(row(7, 9, unicode + "😀", "", ""))));
        String oversized =
                "{\"format\":\"ams-essay-v2\",\"answers\":[],\"padding\":\""
                        + "ệ".repeat(EssayAnswerCodec.MAX_PAYLOAD / 3 + 1)
                        + "\"}";

        assertTrue(oversized.length() < EssayAnswerCodec.MAX_PAYLOAD);
        assertThrows(
                IllegalArgumentException.class, () -> EssayAnswerCodec.normalize(TASKS, oversized));
    }

    @Test
    void canonicalBlankFieldsCannotExpandStoredContentPastPayloadLimit() {
        JsonArray questions = new JsonArray(), answers = new JsonArray();

        for (int task = 1; task <= 15; task++) {
            JsonObject question = new JsonObject();

            question.addProperty("task_order", task);
            question.addProperty("title", "Q");
            question.addProperty("maximum_points", 1);
            JsonArray requirements = new JsonArray();

            for (int requirement = 1; requirement <= 10; requirement++) {
                JsonObject child = new JsonObject();

                child.addProperty("requirement_order", requirement);
                child.addProperty("title", "R");
                child.addProperty("maximum_points", 0.01);
                child.addProperty("answer_type", "TEXT");
                requirements.add(child);
                JsonObject answer = row(task, requirement, "x".repeat(9_930), "", "");

                answer.remove("diagram_xml");
                answer.remove("diagram_image");
                answers.add(answer);
            }

            question.add("requirements", requirements);
            questions.add(question);
        }

        JsonObject root = new JsonObject();

        root.addProperty("format", EssayAnswerCodec.FORMAT);
        root.add("answers", answers);
        String input = new GsonBuilder().disableHtmlEscaping().create().toJson(root);

        assertTrue(input.getBytes(StandardCharsets.UTF_8).length < EssayAnswerCodec.MAX_PAYLOAD);
        assertThrows(
                IllegalArgumentException.class,
                () -> EssayAnswerCodec.normalize(questions.toString(), input));
    }

    private static JsonObject row(
            int task, int requirement, String text, String xml, String image) {
        JsonObject row = new JsonObject();

        row.addProperty("task_order", task);
        row.addProperty("requirement_order", requirement);
        row.addProperty("text", text);
        row.addProperty("diagram_xml", xml);
        row.addProperty("diagram_image", image);

        return row;
    }

    private static String payload(JsonObject... answers) {
        JsonArray rows = new JsonArray();

        for (var answer : answers) rows.add(answer);

        JsonObject root = new JsonObject();

        root.addProperty("format", EssayAnswerCodec.FORMAT);
        root.add("answers", rows);

        return root.toString();
    }

    private static String png(int width, int height) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        assertTrue(
                ImageIO.write(
                        new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB),
                        "png",
                        bytes));

        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
}
