package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.AssignmentForm;
import vn.edu.fpt.ams.model.Assignment;
import vn.edu.fpt.ams.model.AssignmentPurpose;
import vn.edu.fpt.ams.model.AssignmentStatus;
import vn.edu.fpt.ams.model.ResponseType;

class EssayQuestionsTest {
    private static final Instant NOW = Instant.parse("2026-10-12T07:00:00Z");

    @Test void canonicalizesNestedQuestionsWithoutMutatingEditorInput() {
        JsonArray input = array("""
                [{"task_order":99,"title":"  Question\\r\\ntext  ","description":"Old details","maximum_points":"4.50",
                  "requirements":[
                    {"requirement_order":8,"title":" Explain ","maximum_points":"1.25","answer_type":"TEXT"},
                    {"requirement_order":2,"title":"Draw","maximum_points":2,"answer_type":"DIAGRAM"},
                    {"title":"Combine","maximum_points":"0.50","answer_type":"BOTH"}]}]
                """);
        String before = input.toString();
        JsonArray normalized = EssayQuestions.normalize(input, new BigDecimal("10"));
        JsonObject task = normalized.get(0).getAsJsonObject();
        assertEquals(before, input.toString());
        assertEquals(1, task.get("task_order").getAsInt());
        assertEquals("Question\ntext\nOld details", task.get("title").getAsString());
        assertEquals("", task.get("description").getAsString());
        assertEquals(new BigDecimal("4.50"), task.get("maximum_points").getAsBigDecimal());
        var saved = EssayQuestions.read(normalized.toString()).get(0);
        assertEquals(List.of("TEXT", "DIAGRAM", "BOTH"), saved.requirements().stream()
                .map(EssayQuestions.Requirement::answerType).toList());
        assertEquals(List.of(1, 2, 3), saved.requirements().stream().map(EssayQuestions.Requirement::order).toList());
        assertEquals("Explain", saved.requirements().get(0).title());
        assertThrows(UnsupportedOperationException.class, () -> saved.requirements().clear());
    }

    @Test void legacyScoredQuestionsKeepTheirTextAndReceiveOneTextRequirement() {
        String json = """
                [{"task_order":3,"title":"Legacy question","description":"Keep these instructions","maximum_points":5.50}]
                """;
        var task = EssayQuestions.read(json).get(0);
        assertEquals("Legacy question\nKeep these instructions", task.title());
        assertEquals(List.of(new EssayQuestions.Requirement(1, "Trả lời câu hỏi", new BigDecimal("5.50"), "TEXT")),
                task.requirements());
        JsonArray normalized = EssayQuestions.normalize(array(json), new BigDecimal("10"));
        assertEquals(task.requirements(), EssayQuestions.read(normalized.toString()).get(0).requirements());
        assertThrows(IllegalArgumentException.class, () -> EssayQuestions.read("[\"Unscored legacy text\"]"));
    }

    @Test void enforcesQuestionAndRequirementCounts() {
        for (int count : List.of(0, 1, 200, 201)) {
            JsonArray tasks = new JsonArray();
            for (int i = 0; i < count; i++) tasks.add(task("0.01", null));
            if (count == 1 || count == 200)
                assertDoesNotThrow(() -> EssayQuestions.normalize(tasks, new BigDecimal("100")), "tasks=" + count);
            else assertThrows(IllegalArgumentException.class,
                    () -> EssayQuestions.normalize(tasks, new BigDecimal("100")), "tasks=" + count);
        }
        for (int count : List.of(0, 1, 10, 11)) {
            JsonArray requirements = new JsonArray();
            for (int i = 0; i < count; i++) requirements.add(requirement("0.01", "TEXT"));
            JsonArray tasks = new JsonArray();
            tasks.add(task("1", requirements));
            if (count == 1 || count == 10)
                assertDoesNotThrow(() -> EssayQuestions.normalize(tasks, BigDecimal.ONE), "requirements=" + count);
            else assertThrows(IllegalArgumentException.class,
                    () -> EssayQuestions.normalize(tasks, BigDecimal.ONE), "requirements=" + count);
        }
    }

    @Test void permitsUnallocatedPointsButRejectsEitherLevelExceedingItsParent() {
        JsonArray tasks = array("""
                [{"title":"Question","maximum_points":"5","requirements":[
                  {"title":"First","maximum_points":"1.25","answer_type":"TEXT"},
                  {"title":"Second","maximum_points":"2.50","answer_type":"DIAGRAM"}]}]
                """);
        assertDoesNotThrow(() -> EssayQuestions.normalize(tasks, new BigDecimal("10")));
        assertDoesNotThrow(() -> EssayQuestions.normalize(tasks, new BigDecimal("5")));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> EssayQuestions.normalize(tasks, new BigDecimal("4"))).getMessage().contains("Tổng điểm các câu hỏi"));
        tasks.get(0).getAsJsonObject().getAsJsonArray("requirements").get(1).getAsJsonObject()
                .addProperty("maximum_points", "3.76");
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> EssayQuestions.normalize(tasks, new BigDecimal("10"))).getMessage().contains("Tổng điểm các yêu cầu"));
    }

    @Test void rejectsInvalidQuestionAndRequirementScoresWithoutRoundingOrCoercion() {
        for (String points : List.of("", "0", "-1", "0.001", "100.01", "1.000", "1e0", "2+2", "abc")) {
            JsonArray tasks = new JsonArray();
            tasks.add(task(points, null));
            assertThrows(IllegalArgumentException.class,
                    () -> EssayQuestions.normalize(tasks, new BigDecimal("100")), "task=" + points);
            JsonArray requirements = new JsonArray();
            requirements.add(requirement(points, "TEXT"));
            tasks.set(0, task("100", requirements));
            assertThrows(IllegalArgumentException.class,
                    () -> EssayQuestions.normalize(tasks, new BigDecimal("100")), "requirement=" + points);
        }
        for (String points : List.of("null", "true", "{}", "[]")) {
            JsonObject task = task("1", null);
            task.add("maximum_points", JsonParser.parseString(points));
            JsonArray tasks = new JsonArray(); tasks.add(task);
            assertThrows(IllegalArgumentException.class, () -> EssayQuestions.normalize(tasks, BigDecimal.ONE));
        }
        JsonArray minimum = new JsonArray(); minimum.add(task("0.01", null));
        assertEquals(new BigDecimal("0.01"), EssayQuestions.read(
                EssayQuestions.normalize(minimum, BigDecimal.ONE).toString()).get(0).points());
    }

    @Test void rejectsMalformedExplicitRequirementsAndUnknownAnswerTypes() {
        for (String malformed : List.of("null", "{}", "true", "[]", "[null]", "[42]",
                "[{\"title\":\"\",\"maximum_points\":1,\"answer_type\":\"TEXT\"}]",
                "[{\"title\":\"Requirement\",\"maximum_points\":1}]",
                "[{\"title\":\"Requirement\",\"maximum_points\":1,\"answer_type\":false}]")) {
            JsonObject task = task("1", null);
            task.add("requirements", JsonParser.parseString(malformed));
            JsonArray tasks = new JsonArray(); tasks.add(task);
            assertThrows(IllegalArgumentException.class,
                    () -> EssayQuestions.normalize(tasks, BigDecimal.ONE), malformed);
        }
        for (String type : List.of("", "text", "FILE", "SVG", "TEXT_AND_DIAGRAM")) {
            JsonArray requirements = new JsonArray(); requirements.add(requirement("1", type));
            JsonArray tasks = new JsonArray(); tasks.add(task("1", requirements));
            assertThrows(IllegalArgumentException.class,
                    () -> EssayQuestions.normalize(tasks, BigDecimal.ONE), type);
        }
    }

    @Test void readsSavedOrderAndRejectsInvalidSavedIdentitiesAndTypes() {
        JsonArray tasks = EssayQuestions.normalize(array("""
                [{"title":"First","maximum_points":1,"requirements":[
                  {"title":"First child","maximum_points":0.4,"answer_type":"TEXT"},
                  {"title":"Second child","maximum_points":0.4,"answer_type":"DIAGRAM"}]},
                 {"title":"Second","maximum_points":1}]
                """), new BigDecimal("10"));
        tasks.get(0).getAsJsonObject().addProperty("task_order", 5);
        tasks.get(1).getAsJsonObject().addProperty("task_order", 2);
        JsonArray requirements = tasks.get(0).getAsJsonObject().getAsJsonArray("requirements");
        requirements.get(0).getAsJsonObject().addProperty("requirement_order", 7);
        requirements.get(1).getAsJsonObject().addProperty("requirement_order", 3);
        List<EssayQuestions.Task> read = EssayQuestions.read(tasks.toString());
        assertEquals(List.of("Second", "First"), read.stream().map(EssayQuestions.Task::title).toList());
        assertEquals(List.of("Second child", "First child"), read.get(1).requirements().stream()
                .map(EssayQuestions.Requirement::title).toList());
        for (String order : List.of("null", "0", "-1", "1.5", "\"1\"", "2147483648")) {
            JsonArray copy = tasks.deepCopy();
            copy.get(0).getAsJsonObject().add("task_order", JsonParser.parseString(order));
            String taskJson = copy.toString();
            assertThrows(IllegalArgumentException.class, () -> EssayQuestions.read(taskJson), order);
            copy = tasks.deepCopy();
            copy.get(0).getAsJsonObject().getAsJsonArray("requirements").get(0).getAsJsonObject()
                    .add("requirement_order", JsonParser.parseString(order));
            String json = copy.toString();
            assertThrows(IllegalArgumentException.class, () -> EssayQuestions.read(json), order);
        }
        JsonArray duplicate = tasks.deepCopy();
        duplicate.get(0).getAsJsonObject().addProperty("task_order", 2);
        assertThrows(IllegalArgumentException.class, () -> EssayQuestions.read(duplicate.toString()));
        JsonArray stringPoints = tasks.deepCopy();
        stringPoints.get(0).getAsJsonObject().addProperty("maximum_points", "1.00");
        assertThrows(IllegalArgumentException.class, () -> EssayQuestions.read(stringPoints.toString()));
        for (String invalid : List.of("", "{}", "null", "[]", "[", "['Task']", "[/*comment*/{}]"))
            assertThrows(IllegalArgumentException.class, () -> EssayQuestions.read(invalid), invalid);
    }

    @Test void draftAndPublicationShareBothScoreCeilings() {
        JsonArray tasks = new JsonArray();
        JsonArray requirements = new JsonArray(); requirements.add(requirement("2", "BOTH"));
        tasks.add(task("5", requirements));
        var form = form(tasks.toString());
        var draft = AssignmentDraftValidator.validate(form);
        assertTrue(AssignmentPublicationValidator.errors(saved(draft.assignment().taskChecklistJson()), List.of(), NOW).isEmpty());
        requirements.get(0).getAsJsonObject().addProperty("maximum_points", "5.01");
        assertTrue(assertThrows(AssignmentDraftValidator.ValidationException.class,
                () -> AssignmentDraftValidator.validate(form(tasks.toString()))).errors().containsKey("tasksJson"));
        JsonArray invalidSaved = JsonParser.parseString(draft.assignment().taskChecklistJson()).getAsJsonArray();
        invalidSaved.get(0).getAsJsonObject().getAsJsonArray("requirements").get(0).getAsJsonObject()
                .addProperty("maximum_points", new BigDecimal("5.01"));
        assertTrue(AssignmentPublicationValidator.errors(saved(invalidSaved.toString()), List.of(), NOW).containsKey("tasksJson"));
        invalidSaved.get(0).getAsJsonObject().getAsJsonArray("requirements").get(0).getAsJsonObject()
                .addProperty("maximum_points", new BigDecimal("2.00"));
        invalidSaved.get(0).getAsJsonObject().addProperty("maximum_points", new BigDecimal("10.01"));
        assertTrue(AssignmentPublicationValidator.errors(saved(invalidSaved.toString()), List.of(), NOW).containsKey("tasksJson"));
        assertTrue(assertThrows(AssignmentDraftValidator.ValidationException.class,
                () -> AssignmentDraftValidator.validate(form(invalidSaved.toString()))).errors().containsKey("tasksJson"));
    }

    @Test void editingKeepsAllRequirementsAndUpgradesLegacyQuestions() {
        JsonArray tasks = new JsonArray();
        JsonArray requirements = new JsonArray();
        requirements.add(requirement("1.25", "DIAGRAM")); requirements.add(requirement("2.50", "BOTH"));
        tasks.add(task("5", requirements));
        String stored = EssayQuestions.normalize(tasks, new BigDecimal("10")).toString();
        AssignmentForm edit = AssignmentEditSupport.form(saved(stored), List.of());
        String savedAgain = AssignmentEditSupport.validate(edit, List.of()).assignment().taskChecklistJson();
        assertEquals(EssayQuestions.read(stored), EssayQuestions.read(savedAgain));
        String legacy = "[{\"task_order\":1,\"title\":\"Legacy\",\"description\":\"Details\",\"maximum_points\":5}]";
        AssignmentForm legacyEdit = AssignmentEditSupport.form(saved(legacy), List.of());
        var legacyChild = array(legacyEdit.value("tasksJson")).get(0).getAsJsonObject().getAsJsonArray("requirements").get(0).getAsJsonObject();
        assertEquals("Trả lời câu hỏi", legacyChild.get("title").getAsString());
        assertEquals("TEXT", legacyChild.get("answer_type").getAsString());
        assertEquals(EssayQuestions.read(legacy).get(0).requirements(), EssayQuestions.read(
                AssignmentEditSupport.validate(legacyEdit, List.of()).assignment().taskChecklistJson()).get(0).requirements());
    }

    @Test void detailShowsRequirementTextTypeAndPointsAsEscapablePlainText() {
        JsonArray tasks = EssayQuestions.normalize(array("""
                [{"title":"Question","maximum_points":"5","requirements":[
                  {"title":"<img src=x onerror=alert(1)>","maximum_points":"1.25","answer_type":"DIAGRAM"},
                  {"title":"Explain","maximum_points":"2.50","answer_type":"BOTH"}]}]
                """), new BigDecimal("10"));
        JsonArray requirements = tasks.get(0).getAsJsonObject().getAsJsonArray("requirements");
        requirements.get(0).getAsJsonObject().addProperty("requirement_order", 8);
        requirements.get(1).getAsJsonObject().addProperty("requirement_order", 3);
        var checklist = AssignmentDetailView.checklist(tasks.toString());
        assertFalse(checklist.invalid());
        String text = checklist.items().get(0);
        assertTrue(text.contains("Yêu cầu 1: Explain (Văn bản và sơ đồ, 2.5 điểm)"));
        assertTrue(text.contains("Yêu cầu 2: <img src=x onerror=alert(1)> (Sơ đồ, 1.25 điểm)"));
        assertFalse(AssignmentListView.escape(text).contains("<img"));
        requirements.get(0).getAsJsonObject().addProperty("answer_type", "FILE");
        assertTrue(AssignmentDetailView.checklist(tasks.toString()).invalid());
    }

    private static JsonArray array(String json) { return JsonParser.parseString(json).getAsJsonArray(); }
    private static JsonObject task(String points, JsonElement requirements) {
        JsonObject task = new JsonObject();
        task.addProperty("title", "Question"); task.addProperty("maximum_points", points);
        if (requirements != null) task.add("requirements", requirements);
        return task;
    }
    private static JsonObject requirement(String points, String type) {
        JsonObject requirement = new JsonObject();
        requirement.addProperty("title", "Requirement"); requirement.addProperty("maximum_points", points);
        requirement.addProperty("answer_type", type);
        return requirement;
    }
    private static AssignmentForm form(String tasks) {
        Map<String, String> values = new HashMap<>(Map.of("classId", "1", "templateId", "1", "title", "Assignment",
                "instructions", "Instructions", "purpose", "REGULAR", "responseType", "ESSAY", "maximumScore", "10",
                "tasksJson", tasks));
        return new AssignmentForm(values);
    }
    private static Assignment saved(String tasks) {
        return new Assignment(1, 1, 1, "Assignment", AssignmentPurpose.REGULAR, ResponseType.ESSAY,
                "Instructions", "", tasks, new BigDecimal("10"), AssignmentStatus.DRAFT,
                NOW, NOW.plusSeconds(3600), null, null, null, null);
    }
}
