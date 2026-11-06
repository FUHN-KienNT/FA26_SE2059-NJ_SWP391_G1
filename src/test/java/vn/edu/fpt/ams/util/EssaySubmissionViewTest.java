package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class EssaySubmissionViewTest {
    private static final String TASKS =
            """
            [{"task_order":2,"title":"Explain permissions","description":"Teacher and Student"},
             {"task_order":1,"title":"Describe the Web flow","description":"Servlet → Service → DAO"}]
            """;
    private static final String NESTED_TASKS =
            """
            [{"task_order":2,"title":"Permissions question","description":"","maximum_points":4,"requirements":[
               {"requirement_order":2,"title":"Draw permissions","maximum_points":1.50,"answer_type":"DIAGRAM"},
               {"requirement_order":1,"title":"Explain permissions","maximum_points":1,"answer_type":"TEXT"}]},
             {"task_order":1,"title":"Web flow question","description":"Keep context","maximum_points":6,"requirements":[
               {"requirement_order":2,"title":"Describe and draw the flow","maximum_points":3,"answer_type":"BOTH"},
               {"requirement_order":1,"title":"Explain the flow","maximum_points":2.50,"answer_type":"TEXT"}]}]
            """;
    private static final String PNG = png();

    @Test
    void pairsByFrozenTaskIdentityRatherThanArrayPosition() {
        var result =
                EssaySubmissionView.read(
                        TASKS,
                        """
                {"format":"ams-essay-v1","answers":[
                  {"task_order":2,"text":"Only the assigned Teacher may view work."},
                  {"task_order":1,"text":"The Servlet calls Service, which reads through DAO."}]}
                """);

        assertTrue(result.structured());
        assertFalse(result.invalid());
        assertNull(result.legacyContent());
        assertEquals(
                List.of(1, 2),
                result.items().stream().map(EssaySubmissionView.Item::order).toList());
        assertEquals("Describe the Web flow", result.items().get(0).title());
        assertEquals("Servlet → Service → DAO", result.items().get(0).description());
        assertEquals(
                "The Servlet calls Service, which reads through DAO.",
                result.items().get(0).answer());
        assertEquals("Only the assigned Teacher may view work.", result.items().get(1).answer());
        assertTrue(result.unmatched().isEmpty());
    }

    @Test
    void missingAnswerIsNullAndBlankSavedAnswerRemainsBlank() {
        var result =
                EssaySubmissionView.read(
                        TASKS,
                        "{\"format\":\"ams-essay-v1\",\"answers\":[{\"task_order\":2,\"text\":\"\"}]}");

        assertFalse(result.invalid());
        assertNull(result.items().get(0).answer());
        assertEquals("", result.items().get(1).answer());
        var empty = EssaySubmissionView.read(TASKS, "{\"format\":\"ams-essay-v1\",\"answers\":[]}");

        assertTrue(empty.structured());
        assertFalse(empty.invalid());
        assertTrue(empty.items().stream().allMatch(item -> item.answer() == null));
    }

    @Test
    void eachAnswerPreservesMultipleParagraphsWhitespaceAndUnicode() {
        var result =
                EssaySubmissionView.read(
                        TASKS,
                        "{\"format\":\"ams-essay-v1\",\"answers\":[{\"task_order\":1,\"text\":\"  Dòng 1.\\n\\nDòng 2.\\n  \"}]}");

        assertEquals("  Dòng 1.\n\nDòng 2.\n  ", result.items().get(0).answer());
    }

    @Test
    void plaintextIsNeverSplitIntoQuestionsIncludingASingleTask() {
        String content = "  Câu 1: the first paragraph.\n\nCâu 2: another paragraph.\n ";

        for (String tasks : List.of(TASKS, "[{\"task_order\":1,\"title\":\"Only task\"}]", "[]")) {
            var result = EssaySubmissionView.read(tasks, content);

            assertFalse(result.structured());
            assertFalse(result.invalid());
            assertEquals(content, result.legacyContent());
            assertTrue(result.items().stream().allMatch(item -> item.answer() == null));
            assertTrue(result.unmatched().isEmpty());
        }
    }

    @Test
    void jsonLookingLegacyWorkNeedsTheExactVersionedEnvelope() {
        for (String content :
                List.of(
                        "{\"answers\":[{\"task_order\":1,\"text\":\"A\"}]}",
                        "{\"format\":\"ams-essay-v3\",\"answers\":[]}",
                        "{\"format\":true,\"answers\":[]}",
                        "[\"first paragraph\",\"second paragraph\"]",
                        "\"This is a JSON example.\"",
                        "null")) {
            var result = EssaySubmissionView.read(TASKS, content);

            assertFalse(result.structured());
            assertFalse(result.invalid());
            assertEquals(content, result.legacyContent());
            assertTrue(result.items().stream().allMatch(item -> item.answer() == null));
        }
    }

    @Test
    void malformedOrInvalidEnvelopePreservesTheWholeOriginalWork() {
        for (String content :
                List.of(
                        "{\"format\":\"ams-essay-v1\",\"answers\":[",
                        "{\"format\":\"ams-essay-v1\"}",
                        "{\"format\":\"ams-essay-v1\",\"answers\":null}",
                        "{\"format\":\"ams-essay-v1\",\"answers\":{}}",
                        "{format:'ams-essay-v1',answers:[]}",
                        "{\"format\":\"ams-essay-v1\",\"answers\":[]} trailing")) {
            var result = EssaySubmissionView.read(TASKS, content);

            assertTrue(result.invalid());
            assertFalse(result.structured());
            assertEquals(content, result.legacyContent());
            assertTrue(result.items().stream().allMatch(item -> item.answer() == null));
        }
    }

    @Test
    void unknownTaskAnswerStaysVisibleButIsNotAttachedToAnotherQuestion() {
        var result =
                EssaySubmissionView.read(
                        TASKS,
                        "{\"format\":\"ams-essay-v1\",\"answers\":[{\"task_order\":99,\"text\":\"Original work\"}]}");

        assertTrue(result.structured());
        assertTrue(result.invalid());
        assertTrue(result.items().stream().allMatch(item -> item.answer() == null));
        assertEquals(1, result.unmatched().size());
        assertTrue(result.unmatched().get(0).reference().contains("99"));
        assertEquals("Original work", result.unmatched().get(0).text());
    }

    @Test
    void duplicateAnswerReferencesKeepBothTextsUnmatchedRatherThanTakingFirstOrLast() {
        var result =
                EssaySubmissionView.read(
                        TASKS,
                        """
                {"format":"ams-essay-v1","answers":[{"task_order":1,"text":"First answer"},
                  {"task_order":2,"text":"Unambiguous answer"},{"task_order":1,"text":"Second answer"}]}
                """);

        assertTrue(result.invalid());
        assertNull(result.items().get(0).answer());
        assertEquals("Unambiguous answer", result.items().get(1).answer());
        assertEquals(
                List.of("First answer", "Second answer"),
                result.unmatched().stream()
                        .map(EssaySubmissionView.UnmatchedAnswer::text)
                        .toList());
    }

    @Test
    void invalidDuplicateAnswerStillMakesTheIdentityAmbiguous() {
        var result =
                EssaySubmissionView.read(
                        TASKS,
                        """
                {"format":"ams-essay-v1","answers":[{"task_order":1,"text":"Keep me"},
                  {"task_order":1,"text":false}]}
                """);

        assertTrue(result.invalid());
        assertNull(result.items().get(0).answer());
        assertEquals(2, result.unmatched().size());
        assertTrue(result.unmatched().stream().anyMatch(answer -> answer.text().equals("Keep me")));
    }

    @Test
    void duplicateTaskReferencesKeepReadablePromptsButDoNotPairAnAnswer() {
        var result =
                EssaySubmissionView.read(
                        """
                [{"task_order":1,"title":"First prompt"},{"task_order":1,"title":"Another prompt"}]
                """,
                        "{\"format\":\"ams-essay-v1\",\"answers\":[{\"task_order\":1,\"text\":\"Original work\"}]}");

        assertTrue(result.invalid());
        assertEquals(2, result.items().size());
        assertTrue(result.items().stream().allMatch(item -> item.answer() == null));
        assertEquals("Original work", result.unmatched().get(0).text());
        // A duplicate whose title is corrupt cannot make the other task appear unambiguous.
        var corrupt =
                EssaySubmissionView.read(
                        "[{\"task_order\":1,\"title\":\"Valid title\"},{\"task_order\":1}]",
                        "{\"format\":\"ams-essay-v1\",\"answers\":[{\"task_order\":1,\"text\":\"Original work\"}]}");

        assertTrue(corrupt.invalid());
        assertNull(corrupt.items().get(0).answer());
        assertEquals("Original work", corrupt.unmatched().get(0).text());
    }

    @Test
    void legacyTasksStayReadableWithoutInventingAnIdentityFromTheirPosition() {
        var result =
                EssaySubmissionView.read(
                        "[\"Legacy task\",{\"title\":\"Another task\",\"description\":\"Details\"}]",
                        "{\"format\":\"ams-essay-v1\",\"answers\":[{\"task_order\":1,\"text\":\"Work\"}]}");

        assertTrue(result.invalid());
        assertEquals(
                List.of(0, 0),
                result.items().stream().map(EssaySubmissionView.Item::order).toList());
        assertEquals("Legacy task", result.items().get(0).title());
        assertEquals("Details", result.items().get(1).description());
        assertTrue(result.items().stream().allMatch(item -> item.answer() == null));
        assertEquals("Work", result.unmatched().get(0).text());
    }

    @Test
    void invalidIdentitiesCannotCoerceIntoAValidTaskReference() {
        for (String id : List.of("0", "-1", "1.0", "1e0", "true", "\"1\"", "2147483648", "null")) {
            var result =
                    EssaySubmissionView.read(
                            TASKS,
                            "{\"format\":\"ams-essay-v1\",\"answers\":[{\"task_order\":"
                                    + id
                                    + ",\"text\":\"Work\"}]}");

            assertTrue(result.invalid());
            assertTrue(result.items().stream().allMatch(item -> item.answer() == null));
            assertEquals("Work", result.unmatched().get(0).text());
        }
    }

    @Test
    void duplicateJsonPropertiesAreRejectedBeforeTheyCanChangeAReference() {
        String content =
                """
                {"format":"ams-essay-v1","answers":[{"task_order":1,"task_order":2,"text":"Work"}]}
                """;
        var result = EssaySubmissionView.read(TASKS, content);

        assertTrue(result.invalid());
        assertFalse(result.structured());
        assertEquals(content, result.legacyContent());
        assertTrue(result.items().stream().allMatch(item -> item.answer() == null));
        var tasks =
                EssaySubmissionView.read(
                        "[{\"task_order\":1,\"task_order\":2,\"title\":\"Prompt\"}]",
                        "{\"format\":\"ams-essay-v1\",\"answers\":[{\"task_order\":2,\"text\":\"Work\"}]}");

        assertTrue(tasks.invalid());
        assertTrue(tasks.items().isEmpty());
        assertEquals("Work", tasks.unmatched().get(0).text());
    }

    @Test
    void malformedEntriesAndTaskListsDoNotDiscardStoredAnswerText() {
        var result =
                EssaySubmissionView.read(
                        "[",
                        """
                {"format":"ams-essay-v1","answers":[{"task_order":1,"text":"Keep this"},false,{"task_order":2,"text":5}]}
                """);

        assertTrue(result.invalid());
        assertTrue(result.items().isEmpty());
        assertEquals(3, result.unmatched().size());
        assertTrue(
                result.unmatched().stream().anyMatch(answer -> answer.text().equals("Keep this")));
        assertTrue(result.unmatched().stream().anyMatch(answer -> answer.text().equals("false")));
        assertTrue(
                result.unmatched().stream()
                        .anyMatch(answer -> answer.text().contains("\"text\":5")));
    }

    @Test
    void blankWorkRemainsBlankAndReturnedListsAreImmutable() {
        for (String content : new String[] {null, "", "  \n"}) {
            var result = EssaySubmissionView.read(TASKS, content);

            assertFalse(result.structured());
            assertFalse(result.invalid());
            assertEquals(content, result.legacyContent());
            assertEquals(2, result.items().size());
            assertThrows(UnsupportedOperationException.class, () -> result.items().clear());
            assertThrows(UnsupportedOperationException.class, () -> result.unmatched().clear());
        }
    }

    @Test
    void htmlIsUntrustedPlainTextForTheViewToEscape() {
        var result =
                EssaySubmissionView.read(
                        "[{\"task_order\":1,\"title\":\"<script> & prompt\",\"description\":\"<b>\"}]",
                        "{\"format\":\"ams-essay-v1\",\"answers\":[{\"task_order\":1,\"text\":\"<img onerror=alert(1)> & work\"}]}");

        assertFalse(result.invalid());
        var item = result.items().get(0);

        assertEquals("<img onerror=alert(1)> & work", item.answer());
        assertEquals("&lt;script&gt; &amp; prompt", AssignmentListView.escape(item.title()));
        assertEquals("&lt;b&gt;", AssignmentListView.escape(item.description()));
        assertEquals(
                "&lt;img onerror=alert(1)&gt; &amp; work",
                AssignmentListView.escape(item.answer()));
    }

    @Test
    void versionTwoPairsEachTextAndDiagramWithItsOwnFrozenRequirement() {
        JsonObject content = versionTwo();
        JsonArray answers = content.getAsJsonArray("answers");

        answers.add(answer(2, 2, "", "<mxfile/>", PNG));
        answers.add(
                answer(1, 2, "  First paragraph.\n\nSecond paragraph.  ", "<mxGraphModel/>", PNG));
        answers.add(answer(2, 1, "Teacher permissions", "", ""));
        answers.add(answer(1, 1, "Servlet calls Service", "", ""));
        var result = EssaySubmissionView.read(NESTED_TASKS, content.toString());

        assertTrue(result.structured());
        assertFalse(result.invalid());
        assertNull(result.legacyContent());
        assertTrue(result.unmatched().isEmpty());
        assertEquals(
                List.of(1, 2),
                result.items().stream().map(EssaySubmissionView.Item::order).toList());
        var first = result.items().get(0);

        assertEquals("Web flow question\nKeep context", first.title());
        assertEquals("6", first.points());
        assertNull(first.answer());
        assertEquals(
                List.of(1, 2),
                first.requirements().stream()
                        .map(EssaySubmissionView.RequirementAnswer::order)
                        .toList());
        assertEquals("Explain the flow", first.requirements().get(0).title());
        assertEquals("2.5", first.requirements().get(0).points());
        assertEquals("Servlet calls Service", first.requirements().get(0).answer());
        assertEquals("", first.requirements().get(0).image());
        assertEquals("BOTH", first.requirements().get(1).answerType());
        assertEquals("Văn bản và sơ đồ", first.requirements().get(1).answerTypeLabel());
        assertEquals(
                "  First paragraph.\n\nSecond paragraph.  ", first.requirements().get(1).answer());
        assertEquals(PNG, first.requirements().get(1).image());
        var second = result.items().get(1);

        assertEquals("Teacher permissions", second.requirements().get(0).answer());
        assertEquals("Văn bản", second.requirements().get(0).answerTypeLabel());
        assertEquals("Draw permissions", second.requirements().get(1).title());
        assertEquals("Sơ đồ", second.requirements().get(1).answerTypeLabel());
        assertEquals("", second.requirements().get(1).answer());
        assertEquals(PNG, second.requirements().get(1).image());
        assertThrows(UnsupportedOperationException.class, () -> first.requirements().clear());
    }

    @Test
    void versionTwoShowsEveryRequirementEvenWhenNoAnswerWasSavedForIt() {
        JsonObject content = versionTwo();

        content.getAsJsonArray("answers").add(answer(2, 1, "Answered", "", ""));
        var result = EssaySubmissionView.read(NESTED_TASKS, content.toString());

        assertTrue(result.structured());
        assertFalse(result.invalid());
        assertEquals(4, result.items().stream().mapToInt(item -> item.requirements().size()).sum());
        assertTrue(
                result.items().get(0).requirements().stream()
                        .allMatch(
                                requirement ->
                                        requirement.answer().isEmpty()
                                                && requirement.image().isEmpty()));
        assertEquals("Answered", result.items().get(1).requirements().get(0).answer());
        assertEquals("", result.items().get(1).requirements().get(1).image());
    }

    @Test
    void versionTwoSupportsLegacyScoredQuestionsThroughTheirDefaultTextRequirement() {
        JsonObject content = versionTwo();

        content.getAsJsonArray("answers").add(answer(3, 1, "Legacy question answer", "", ""));
        var result =
                EssaySubmissionView.read(
                        "[{\"task_order\":3,\"title\":\"Original prompt\",\"description\":\"Original details\",\"maximum_points\":5}]",
                        content.toString());

        assertTrue(result.structured());
        assertFalse(result.invalid());
        var requirement = result.items().get(0).requirements().get(0);

        assertEquals("Trả lời câu hỏi", requirement.title());
        assertEquals("TEXT", requirement.answerType());
        assertEquals("5", requirement.points());
        assertEquals("Legacy question answer", requirement.answer());
    }

    @Test
    void invalidVersionTwoKeepsTheExactOriginalInsteadOfAttachingPartialAnswers() {
        List<String> invalid =
                new ArrayList<>(
                        List.of(
                                "{\"format\":\"ams-essay-v2\"}",
                                "{\"format\":\"ams-essay-v2\",\"answers\":{}}",
                                "{\"format\":\"ams-essay-v2\",\"answers\":[{\"task_order\":1,\"requirement_order\":1,\"requirement_order\":2,\"text\":\"Keep me\"}]}"));

        for (JsonObject invalidAnswer :
                List.of(
                        answer(99, 1, "Unknown question", "", ""),
                        answer(1, 99, "Unknown requirement", "", ""),
                        answer(1, 1, "Text requirement with diagram", "<mxfile/>", PNG),
                        answer(2, 2, "Diagram requirement with text", "", ""),
                        answer(2, 2, "", "<mxfile/>", "data:image/svg+xml,<svg onload=alert(1)>"),
                        answer(
                                2,
                                2,
                                "",
                                "<!DOCTYPE mxfile SYSTEM 'file:///sensitive'><mxfile/>",
                                PNG),
                        answer(2, 2, "", "<mxfile/>", "data:image/png;base64,broken"),
                        answer(2, 2, "", "", PNG))) {
            JsonObject content = versionTwo();

            content.getAsJsonArray("answers")
                    .add(answer(1, 1, "Valid answer must also stay in original", "", ""));
            content.getAsJsonArray("answers").add(invalidAnswer);
            invalid.add(content.toString());
        }

        JsonObject duplicate = versionTwo();

        duplicate.getAsJsonArray("answers").add(answer(1, 1, "First", "", ""));
        duplicate.getAsJsonArray("answers").add(answer(1, 1, "Second", "", ""));
        invalid.add(duplicate.toString());

        for (String content : invalid) {
            var result = EssaySubmissionView.read(NESTED_TASKS, content);

            assertTrue(result.invalid(), content);
            assertFalse(result.structured(), content);
            assertEquals(content, result.legacyContent());
            assertTrue(
                    result.items().stream()
                            .allMatch(
                                    item ->
                                            item.answer() == null
                                                    && item.requirements().isEmpty()));
            assertTrue(result.unmatched().isEmpty());
        }
    }

    @Test
    void versionTwoRejectsAmbiguousFrozenRequirementsAndInvalidTaskData() {
        String content = versionTwo().toString();

        for (String tasks :
                List.of(
                        TASKS,
                        "[]",
                        "[",
                        NESTED_TASKS.replace("\"requirement_order\":2", "\"requirement_order\":1"),
                        NESTED_TASKS.replace(
                                "\"task_order\":2", "\"task_order\":2,\"task_order\":1"))) {
            var result = EssaySubmissionView.read(tasks, content);

            assertTrue(result.invalid(), tasks);
            assertFalse(result.structured(), tasks);
            assertEquals(content, result.legacyContent());
            assertTrue(result.items().stream().allMatch(item -> item.requirements().isEmpty()));
        }
    }

    @Test
    void versionTwoPromptAndAnswerHtmlStayPlainTextWhileImageIsVerifiedPng() {
        String tasks =
                NESTED_TASKS.replace("Explain the flow", "<script>alert(1)</script> & prompt");
        JsonObject content = versionTwo();

        content.getAsJsonArray("answers")
                .add(answer(1, 1, "<img onerror=alert(1)> & answer", "", ""));
        var result = EssaySubmissionView.read(tasks, content.toString());

        assertFalse(result.invalid());
        var requirement = result.items().get(0).requirements().get(0);

        assertEquals(
                "&lt;script&gt;alert(1)&lt;/script&gt; &amp; prompt",
                AssignmentListView.escape(requirement.title()));
        assertEquals(
                "&lt;img onerror=alert(1)&gt; &amp; answer",
                AssignmentListView.escape(requirement.answer()));
        assertTrue(requirement.image().isBlank());
    }

    @Test
    void previousItemConstructorsRemainAvailableWithImmutableEmptyRequirements() {
        var four = new EssaySubmissionView.Item(1, "Title", "Description", "Answer");
        var five = new EssaySubmissionView.Item(1, "Title", "Description", "Answer", "5");

        assertNull(four.points());
        assertEquals("5", five.points());
        assertTrue(four.requirements().isEmpty());
        assertTrue(five.requirements().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> five.requirements().clear());
    }

    private static JsonObject versionTwo() {
        JsonObject content = new JsonObject();

        content.addProperty("format", EssayAnswerCodec.FORMAT);
        content.add("answers", new JsonArray());

        return content;
    }

    private static JsonObject answer(
            int task, int requirement, String text, String xml, String image) {
        JsonObject answer = new JsonObject();

        answer.addProperty("task_order", task);
        answer.addProperty("requirement_order", requirement);
        answer.addProperty("text", text);
        answer.addProperty("diagram_xml", xml);
        answer.addProperty("diagram_image", image);

        return answer;
    }

    private static String png() {
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", output);

            return "data:image/png;base64,"
                    + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
