package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.AssignmentCreatePage;
import vn.edu.fpt.ams.integration.GeminiAssignmentClient;
import vn.edu.fpt.ams.util.AssignmentDocumentReader;
import vn.edu.fpt.ams.util.EssayQuestions;

class AssignmentAuthoringServiceTest {
    static class Assignments extends AssignmentService {
        int checks;
        int denyOn;
        RuntimeException failure = new SecurityException("Class access revoked");

        @Override
        public AssignmentCreatePage createPage(long teacher, long classId) {
            assertEquals(1, teacher);
            assertEquals(10, classId);

            if (++checks == denyOn) throw failure;

            return new AssignmentCreatePage(
                    10, 100, "C1", "Class", "SWP391", "Software Project", List.of());
        }
    }

    static class Gemini extends GeminiAssignmentClient {
        int calls;
        JsonArray result;
        String request;

        Gemini(JsonArray result) {
            super(
                    "",
                    "",
                    request -> {
                        throw new AssertionError("No HTTP in test");
                    });
            this.result = result;
        }

        @Override
        public JsonArray generate(String prompt) {
            calls++;
            request = prompt;

            return result;
        }
    }

    private static JsonArray tasks() {
        return EssayQuestions.normalize(
                new AssignmentDocumentReader().parseQuestions(AssignmentDocumentReader.template()),
                BigDecimal.TEN);
    }

    private static List<AssignmentAuthoringService.Document> file(String name, String text) {
        return List.of(
                new AssignmentAuthoringService.Document(
                        name, text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void importsCompleteDocumentsAndRechecksClassBeforeReturning() {
        var access = new Assignments();
        var gemini = new Gemini(tasks());
        var service =
                new AssignmentAuthoringService(access, new AssignmentDocumentReader(), gemini);
        var result =
                service.importDocuments(
                        1, 10, "10", file("questions.txt", AssignmentDocumentReader.template()));

        assertEquals(2, result.size());
        assertEquals(2, access.checks);
        assertEquals(0, gemini.calls);
        assertEquals("", result.get(0).getAsJsonObject().get("description").getAsString());
        var excess = new ArrayList<>(file("first.txt", AssignmentDocumentReader.template()));

        excess.addAll(file("second.txt", AssignmentDocumentReader.template()));
        assertThrows(
                IllegalArgumentException.class, () -> service.importDocuments(1, 10, "10", excess));
    }

    @Test
    void authorizesBeforeReadingDocumentsOrGeneratingAndRefreshesAfterGeneration() {
        var access = new Assignments();

        access.denyOn = 1;
        var gemini = new Gemini(tasks());
        var reader =
                new AssignmentDocumentReader() {
                    @Override
                    public String read(String name, byte[] bytes) {
                        fail("Must authorize before reading");

                        return null;
                    }
                };
        var denied = new AssignmentAuthoringService(access, reader, gemini);

        assertThrows(
                SecurityException.class,
                () -> denied.importDocuments(1, 10, "10", file("x.txt", "content")));
        access.checks = 0;
        assertThrows(
                SecurityException.class,
                () -> denied.generate(1, 10, "10", "2", "2", "context", "prompt", List.of()));
        assertEquals(0, gemini.calls);
        access.checks = 0;
        access.denyOn = 2;
        var generated =
                new AssignmentAuthoringService(access, new AssignmentDocumentReader(), gemini);
        // Both questions must have the requested number of requirements before the refresh can run.
        JsonArray output = tasks();

        output.remove(1);
        output.get(0).getAsJsonObject().addProperty("maximum_points", 10);
        gemini.result = output;
        assertThrows(
                SecurityException.class,
                () -> generated.generate(1, 10, "10", "1", "2", "context", "prompt", List.of()));
        assertEquals(1, gemini.calls);
        assertEquals(2, access.checks);
    }

    @Test
    void generatedProposalHasRequestedCountsAndExactAssignmentScore() {
        JsonArray output = tasks();

        output.remove(1);
        output.get(0).getAsJsonObject().addProperty("maximum_points", 10);
        var gemini = new Gemini(output);
        var access = new Assignments();
        var service =
                new AssignmentAuthoringService(access, new AssignmentDocumentReader(), gemini);
        var result =
                service.generate(
                        1,
                        10,
                        "10",
                        "1",
                        "2",
                        "Use cases",
                        "Design task",
                        file("reference.txt", "Library context"));

        assertEquals(1, result.size());
        assertEquals(2, access.checks);
        assertTrue(gemini.request.contains("SWP391"));
        assertTrue(gemini.request.contains("Library context"));
        assertTrue(gemini.request.contains("Use cases"));
        assertTrue(gemini.request.contains("Design task"));

        for (String scenario : List.of("count", "requirements", "points", "invalid")) {
            JsonArray broken = output.deepCopy();

            if (scenario.equals("count")) broken.add(output.get(0).deepCopy());

            if (scenario.equals("requirements"))
                broken.get(0).getAsJsonObject().getAsJsonArray("requirements").remove(1);

            if (scenario.equals("points"))
                broken.get(0).getAsJsonObject().addProperty("maximum_points", 9);

            if (scenario.equals("invalid"))
                broken.get(0)
                        .getAsJsonObject()
                        .getAsJsonArray("requirements")
                        .get(0)
                        .getAsJsonObject()
                        .addProperty("answer_type", "SCRIPT");

            gemini.result = broken;
            assertThrows(
                    GeminiAssignmentClient.ProviderException.class,
                    () -> service.generate(1, 10, "10", "1", "2", "context", "prompt", List.of()));
        }
    }

    @Test
    void invalidBoundsFailBeforeCallingProvider() {
        var gemini = new Gemini(tasks());
        var service =
                new AssignmentAuthoringService(
                        new Assignments(), new AssignmentDocumentReader(), gemini);

        for (String score : List.of("0", "101", "10.5", "bad"))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.generate(1, 10, score, "1", "1", "context", "prompt", List.of()));

        for (String count : List.of("0", "201", "bad"))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            service.generate(
                                    1, 10, "10", count, "1", "context", "prompt", List.of()));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.generate(1, 10, "10", "1", "11", "context", "prompt", List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.generate(1, 10, "10", "1", "1", "", "", List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.generate(
                                1, 10, "10", "1", "1", "x".repeat(12_001), "prompt", List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.generate(
                                1, 10, "10", "1", "1", "context", "x".repeat(4_001), List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.importDocuments(1, 10, "10", List.of()));
        var files = new ArrayList<AssignmentAuthoringService.Document>();

        for (int i = 0; i < 4; i++) files.addAll(file("x.txt", "reference"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.generate(1, 10, "10", "1", "1", "context", "prompt", files));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.generate(
                                1,
                                10,
                                "10",
                                "1",
                                "1",
                                "context",
                                "prompt",
                                file("x.txt", "x".repeat(60_001))));
        assertEquals(0, gemini.calls);
    }
}
