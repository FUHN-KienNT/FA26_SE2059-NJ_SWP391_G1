package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.model.Assignment;
import vn.edu.fpt.ams.model.AssignmentPurpose;
import vn.edu.fpt.ams.model.AssignmentStatus;
import vn.edu.fpt.ams.model.Question;
import vn.edu.fpt.ams.model.ResponseType;

class AssignmentPublicationValidatorTest {
    private static final Instant NOW = Instant.parse("2026-10-12T07:00:00Z");
    private static final String TASKS = """
            [{"task_order":1,"title":"Explain the design","description":"Include a diagram.","maximum_points":10}]
            """;
    private static final String OPTIONS = """
            [{"option_id":"answer-a","option_order":1,"option_text":"A","is_correct":true},
             {"option_id":2,"option_order":2,"option_text":"B","is_correct":false}]
            """;

    @Test void acceptsTheThreeSupportedPublicationTypes() {
        for (var type : List.of(new Type(AssignmentPurpose.REGULAR, ResponseType.ESSAY),
                new Type(AssignmentPurpose.REGULAR, ResponseType.OBJECTIVE),
                new Type(AssignmentPurpose.PRACTICE, ResponseType.OBJECTIVE))) {
            Fixture fixture = new Fixture(type.purpose(), type.response());
            assertTrue(fixture.errors().isEmpty(), type.toString());
        }
        Fixture unsupported = new Fixture(AssignmentPurpose.PRACTICE, ResponseType.ESSAY);
        assertTrue(unsupported.errors().containsKey("responseType"));
    }

    @Test void reportsInvalidSavedFieldsInAnImmutableInsertionOrderedMap() {
        Fixture fixture = new Fixture();
        fixture.assignmentId = 0;
        fixture.classId = -1;
        fixture.templateId = 0;
        fixture.title = " ";
        fixture.purpose = null;
        fixture.response = null;
        fixture.maximum = null;
        fixture.instructions = null;
        fixture.opens = null;
        fixture.due = null;
        fixture.minutes = 0;
        Map<String, String> errors = fixture.errors();
        assertEquals(List.of("assignmentId", "classId", "templateId", "title", "purpose", "responseType",
                "maximumScore", "instructions", "availableFrom", "dueAt", "timeLimitMinutes"),
                new ArrayList<>(errors.keySet()));
        assertThrows(UnsupportedOperationException.class, () -> errors.put("title", "Changed"));
        assertThrows(UnsupportedOperationException.class, errors::clear);
        assertEquals(Map.of("assignmentId", "Chọn một bài tập đã lưu để công bố."),
                AssignmentPublicationValidator.errors(null, List.of(), NOW));
    }

    @Test void countsTitleUnicodeCodePointsAndRejectsBlankInstructions() {
        Fixture fixture = new Fixture();
        fixture.title = "\uD83D\uDE80".repeat(200);
        assertTrue(fixture.errors().isEmpty());
        fixture.title += "\uD83D\uDE80";
        assertTrue(fixture.errors().containsKey("title"));
        for (String title : Arrays.asList(null, "", "\t\n", "x".repeat(201))) {
            fixture.title = title;
            assertTrue(fixture.errors().containsKey("title"));
        }
        fixture.title = "Valid title";
        for (String instructions : Arrays.asList(null, "", " \t\n")) {
            fixture.instructions = instructions;
            assertTrue(fixture.errors().containsKey("instructions"));
        }
    }

    @Test void enforcesWholeMaximumAndExactDecimalQuestionTotals() {
        Fixture fixture = new Fixture();
        for (String maximum : List.of("1", "1.00", "1.000", "100")) {
            fixture.maximum = new BigDecimal(maximum);
            fixture.tasks = "[{\"task_order\":1,\"title\":\"Task\",\"maximum_points\":"+maximum+"}]";
            assertTrue(fixture.errors().isEmpty(), maximum);
        }
        for (String maximum : List.of("0", "0.01", "-0.01", "0.001", "1.001", "101", "100000000", "99999999.999")) {
            fixture.maximum = new BigDecimal(maximum);
            assertTrue(fixture.errors().containsKey("maximumScore"), maximum);
        }
        Fixture objective = new Fixture(AssignmentPurpose.REGULAR, ResponseType.OBJECTIVE);
        objective.maximum = new BigDecimal("1");
        objective.questions = List.of(question(101, 1, "0.10", OPTIONS), question(102, 2, "0.90", OPTIONS));
        assertTrue(objective.errors().isEmpty());
        objective.maximum = new BigDecimal("2");
        assertTrue(objective.errors().get("questionsJson").contains("Tổng điểm"));
        objective.maximum = new BigDecimal("1.0000");
        assertTrue(objective.errors().isEmpty());
    }

    @Test void requiresBothDatesAndAFutureDueDateAfterOpening() {
        Fixture fixture = new Fixture();
        fixture.opens = NOW.minusSeconds(3600);
        assertTrue(fixture.errors().isEmpty());
        fixture.opens = NOW.plusSeconds(3600);
        assertTrue(fixture.errors().isEmpty());
        fixture.opens = null;
        assertTrue(fixture.errors().containsKey("availableFrom"));
        fixture.opens = NOW.minusSeconds(3600);
        fixture.due = null;
        assertTrue(fixture.errors().containsKey("dueAt"));
        for (Instant due : List.of(NOW.minusSeconds(1), NOW, fixture.opens, fixture.opens.minusSeconds(1))) {
            fixture.due = due;
            assertTrue(fixture.errors().containsKey("dueAt"), due.toString());
        }
        fixture.opens = NOW.plusSeconds(3600);
        fixture.due = fixture.opens;
        assertTrue(fixture.errors().containsKey("dueAt"));
        fixture.due = fixture.opens.minusSeconds(1);
        assertTrue(fixture.errors().containsKey("dueAt"));
        fixture.due = fixture.opens.plusNanos(1);
        assertTrue(fixture.errors().isEmpty());
    }

    @Test void appliesTimeAndAttemptLimitsByPurpose() {
        Fixture regular = new Fixture();
        for (Integer minutes : Arrays.asList(null, 20, 21, 719, 720)) {
            regular.minutes = minutes;
            assertTrue(regular.errors().isEmpty());
        }
        for (int minutes : List.of(0, -1, 19, 721, Integer.MAX_VALUE)) {
            regular.minutes = minutes;
            assertTrue(regular.errors().containsKey("timeLimitMinutes"));
        }
        regular.minutes = null;
        for (int attempts : List.of(0, -1, 1, Integer.MAX_VALUE)) {
            regular.attempts = attempts;
            assertTrue(regular.errors().containsKey("attemptLimit"));
        }
        Fixture practice = new Fixture(AssignmentPurpose.PRACTICE, ResponseType.OBJECTIVE);
        for (Integer attempts : Arrays.asList(null, 0, -1)) {
            practice.attempts = attempts;
            assertTrue(practice.errors().containsKey("attemptLimit"));
        }
        for (int attempts : List.of(1, Integer.MAX_VALUE)) {
            practice.attempts = attempts;
            assertTrue(practice.errors().isEmpty());
        }
    }

    @Test void doesNotRequireEssayScopeButStillRequiresAtLeastOneQuestion() {
        Fixture fixture = new Fixture();
        for (String scope : Arrays.asList(null, "", " \n")) {
            fixture.scope = scope;
            assertFalse(fixture.errors().containsKey("scopeText"));
            assertTrue(fixture.errors().isEmpty());
        }
        fixture.scope = "The project design";
        for (String tasks : Arrays.asList(null, "", "[]")) {
            fixture.tasks = tasks;
            assertTrue(fixture.errors().containsKey("tasksJson"));
        }
    }

    @Test void acceptsScoredTasksAndOptionalDescriptionsWithoutChangingTheirOrder() {
        Fixture fixture = new Fixture();
        for (String tasks : List.of("""
                [{"task_order":7,"title":"First saved task","maximum_points":3},
                 {"task_order":3,"title":"Second saved task","description":"","maximum_points":3},
                 {"task_order":9,"title":"Third saved task","description":null,"maximum_points":4}]
                """)) {
            fixture.tasks = tasks;
            Assignment before = fixture.assignment();
            assertTrue(fixture.errors().isEmpty(), tasks);
            assertEquals(tasks, before.taskChecklistJson());
            assertEquals(before, fixture.assignment());
        }
        fixture.tasks = "[\"Legacy task without points\"]";
        assertTrue(fixture.errors().containsKey("tasksJson"));
    }

    @Test void rejectsMalformedTasksAndInvalidSavedOrdersWithoutCoercingValues() {
        Fixture fixture = new Fixture();
        for (String tasks : List.of("{broken", "{}", "null", "[null]", "[42]", "[\" \" ]", "['Task']",
                "[/*comment*/\"Task\"]", "[{\"title\":\"Task\"}]",
                "[{\"task_order\":0,\"title\":\"Task\"}]", "[{\"task_order\":-1,\"title\":\"Task\"}]",
                "[{\"task_order\":1.5,\"title\":\"Task\"}]", "[{\"task_order\":\"1\",\"title\":\"Task\"}]",
                "[{\"task_order\":2147483648,\"title\":\"Task\"}]", "[{\"task_order\":1,\"title\":42}]",
                "[{\"task_order\":1,\"title\":\" \"}]", "[{\"task_order\":1,\"title\":\"Task\",\"description\":42}]",
                "[{\"task_order\":1,\"title\":\"A\"},{\"task_order\":1,\"title\":\"B\"}]",
                "[\"Legacy task\",{\"task_order\":1,\"title\":\"Task\"}]")) {
            fixture.tasks = tasks;
            assertTrue(fixture.errors().containsKey("tasksJson"), tasks);
        }
    }

    @Test void rejectsStaleContentForBothResponseTypes() {
        Fixture essay = new Fixture();
        essay.questions = List.of(question(101, 1, "10", OPTIONS));
        assertTrue(essay.errors().containsKey("questionsJson"));
        assertEquals(1, essay.questions.size());
        for (AssignmentPurpose purpose : AssignmentPurpose.values()) {
            Fixture objective = new Fixture(purpose, ResponseType.OBJECTIVE);
            objective.scope = "Stale Essay scope";
            objective.tasks = TASKS;
            assertFalse(objective.errors().containsKey("scopeText"));
            assertTrue(objective.errors().containsKey("tasksJson"));
            for (String tasks : List.of("{broken", "{}", "null", "[null]")) {
                objective.tasks = tasks;
                assertTrue(objective.errors().containsKey("tasksJson"), tasks);
            }
            objective.scope = null;
            for (String tasks : Arrays.asList(null, "", "[]")) {
                objective.tasks = tasks;
                assertTrue(objective.errors().isEmpty());
            }
        }
    }

    @Test void requiresAnObjectiveQuestionWithSavedIdentityAndOwnership() {
        Fixture fixture = new Fixture(AssignmentPurpose.REGULAR, ResponseType.OBJECTIVE);
        for (List<Question> questions : Arrays.asList(null, List.<Question>of(), Arrays.<Question>asList((Question) null))) {
            fixture.questions = questions;
            assertTrue(fixture.errors().containsKey("questionsJson"));
        }
        for (Question question : List.of(question(0, 1, "10", OPTIONS), question(-1, 1, "10", OPTIONS),
                new Question(101, 0, 1, "Question", new BigDecimal("10"), OPTIONS),
                new Question(101, 99, 1, "Question", new BigDecimal("10"), OPTIONS),
                question(101, 0, "10", OPTIONS), question(101, -1, "10", OPTIONS),
                new Question(101, 10, 1, null, new BigDecimal("10"), OPTIONS),
                new Question(101, 10, 1, " \t", new BigDecimal("10"), OPTIONS))) {
            fixture.questions = List.of(question);
            assertTrue(fixture.errors().containsKey("questionsJson"), question.toString());
        }
    }

    @Test void rejectsDuplicateQuestionIdentitiesAndOrders() {
        Fixture fixture = new Fixture(AssignmentPurpose.REGULAR, ResponseType.OBJECTIVE);
        for (List<Question> questions : List.of(
                List.of(question(101, 1, "5", OPTIONS), question(101, 2, "5", OPTIONS)),
                List.of(question(101, 1, "5", OPTIONS), question(102, 1, "5", OPTIONS)))) {
            fixture.questions = questions;
            assertTrue(fixture.errors().containsKey("questionsJson"));
        }
        fixture.questions = List.of(question(102, 7, "5", OPTIONS), question(101, 3, "5", OPTIONS));
        assertTrue(fixture.errors().isEmpty());
    }

    @Test void rejectsInvalidQuestionPointsAndRequiresAnExactTotal() {
        Fixture fixture = new Fixture(AssignmentPurpose.REGULAR, ResponseType.OBJECTIVE);
        for (String points : List.of("0", "-1", "0.001", "100000000", "5")) {
            fixture.questions = List.of(question(101, 1, points, OPTIONS));
            assertTrue(fixture.errors().containsKey("questionsJson"), points);
        }
        fixture.questions = List.of(new Question(101, 10, 1, "Question", null, OPTIONS));
        assertTrue(fixture.errors().containsKey("questionsJson"));
        fixture.maximum = new BigDecimal("100");
        fixture.questions = List.of(question(101, 1, "100", OPTIONS));
        assertTrue(fixture.errors().isEmpty());
    }

    @Test void requiresAtLeastTwoStructuredOptionsAndAtLeastOneBooleanCorrectAnswer() {
        Fixture fixture = new Fixture(AssignmentPurpose.REGULAR, ResponseType.OBJECTIVE);
        for (String options : Arrays.asList(null, "", "{broken", "null", "{}", "[]", "[null,null]", "[\"A\",\"B\"]",
                "[{\"option_id\":1,\"option_order\":1,\"option_text\":\"A\",\"is_correct\":true}]")) {
            fixture.questions = List.of(question(101, 1, "10", options));
            assertTrue(fixture.errors().containsKey("questionsJson"), String.valueOf(options));
        }
        for (String correct : List.of("false", "\"true\"", "1", "null")) {
            var options = JsonParser.parseString(OPTIONS).getAsJsonArray();
            options.get(0).getAsJsonObject().add("is_correct", JsonParser.parseString(correct));
            fixture.questions = List.of(question(101, 1, "10", options.toString()));
            assertTrue(fixture.errors().containsKey("questionsJson"), correct);
        }
        var options = JsonParser.parseString(OPTIONS).getAsJsonArray();
        options.get(1).getAsJsonObject().addProperty("is_correct", true);
        fixture.questions = List.of(question(101, 1, "10", options.toString()));
        assertTrue(fixture.errors().isEmpty());
        options.get(0).getAsJsonObject().remove("is_correct");
        fixture.questions = List.of(question(101, 1, "10", options.toString()));
        assertTrue(fixture.errors().containsKey("questionsJson"));
    }

    @Test void validatesOptionIdentitiesOrdersAndTextWithoutCoercingTheirTypes() {
        Fixture fixture = new Fixture(AssignmentPurpose.REGULAR, ResponseType.OBJECTIVE);
        for (var change : List.of(Map.entry("option_id", "null"), Map.entry("option_id", "true"),
                Map.entry("option_id", "\" \""), Map.entry("option_id", "{}"),
                Map.entry("option_order", "0"), Map.entry("option_order", "-1"),
                Map.entry("option_order", "1.5"), Map.entry("option_order", "\"1\""),
                Map.entry("option_order", "2147483648"), Map.entry("option_order", "null"),
                Map.entry("option_text", "null"), Map.entry("option_text", "42"),
                Map.entry("option_text", "\" \""))) {
            var options = JsonParser.parseString(OPTIONS).getAsJsonArray();
            options.get(0).getAsJsonObject().add(change.getKey(), JsonParser.parseString(change.getValue()));
            fixture.questions = List.of(question(101, 1, "10", options.toString()));
            assertTrue(fixture.errors().containsKey("questionsJson"), change.toString());
        }
        for (String missing : List.of("option_id", "option_order", "option_text")) {
            var options = JsonParser.parseString(OPTIONS).getAsJsonArray();
            options.get(0).getAsJsonObject().remove(missing);
            fixture.questions = List.of(question(101, 1, "10", options.toString()));
            assertTrue(fixture.errors().containsKey("questionsJson"), missing);
        }
        for (String duplicate : List.of("option_id", "option_order")) {
            var options = JsonParser.parseString(OPTIONS).getAsJsonArray();
            options.get(1).getAsJsonObject().add(duplicate, options.get(0).getAsJsonObject().get(duplicate));
            fixture.questions = List.of(question(101, 1, "10", options.toString()));
            assertTrue(fixture.errors().containsKey("questionsJson"), duplicate);
        }
        fixture.questions = List.of(question(101, 1, "10", """
                [{"option_id":1,"option_order":1,"option_text":"A","is_correct":true},
                 {"option_id":"1","option_order":2,"option_text":"B","is_correct":false}]
                """));
        assertTrue(fixture.errors().containsKey("questionsJson"));
    }

    @Test void leavesSavedFieldsQuestionIdentitiesOptionIdentitiesAndListOrderIntact() {
        Fixture fixture = new Fixture(AssignmentPurpose.REGULAR, ResponseType.OBJECTIVE);
        String options = """
                [{"option_id":"keep-first-id","option_order":7,"option_text":"A","is_correct":false},
                 {"option_id":23,"option_order":3,"option_text":"B","is_correct":true}]
                """;
        fixture.questions = new ArrayList<>(List.of(question(902, 9, "6.00", options), question(701, 4, "4", OPTIONS)));
        Assignment assignment = fixture.assignment();
        List<Question> before = List.copyOf(fixture.questions);
        Map<String, String> errors = AssignmentPublicationValidator.errors(assignment, fixture.questions, NOW);
        assertTrue(errors.isEmpty());
        assertEquals(before, fixture.questions);
        assertEquals(assignment, fixture.assignment());
        assertEquals(options, fixture.questions.get(0).options());
        assertThrows(UnsupportedOperationException.class, () -> errors.put("questionsJson", "Changed"));
    }

    private record Type(AssignmentPurpose purpose, ResponseType response) {}

    private static Question question(long id, int order, String points, String options) {
        return new Question(id, 10, order, "Question " + id, new BigDecimal(points), options);
    }

    private static final class Fixture {
        long assignmentId = 10;
        long classId = 20;
        long templateId = 30;
        String title = "Ready assignment";
        AssignmentPurpose purpose;
        ResponseType response;
        String instructions = "Complete all questions and review your work.";
        String scope;
        String tasks;
        BigDecimal maximum = new BigDecimal("10.00");
        Instant opens = NOW;
        Instant due = NOW.plusSeconds(7200);
        Integer minutes;
        Integer attempts;
        List<Question> questions;

        Fixture() { this(AssignmentPurpose.REGULAR, ResponseType.ESSAY); }

        Fixture(AssignmentPurpose purpose, ResponseType response) {
            this.purpose = purpose;
            this.response = response;
            scope = response == ResponseType.ESSAY ? "The project design" : "";
            tasks = response == ResponseType.ESSAY ? TASKS : "[]";
            attempts = purpose == AssignmentPurpose.PRACTICE ? 1 : null;
            questions = response == ResponseType.OBJECTIVE ? List.of(question(101, 1, "10", OPTIONS)) : List.of();
        }

        Assignment assignment() {
            return new Assignment(assignmentId, classId, templateId, title, purpose, response, instructions,
                    scope, tasks, maximum, AssignmentStatus.DRAFT, opens, due, minutes, attempts, null, null);
        }

        Map<String, String> errors() { return AssignmentPublicationValidator.errors(assignment(), questions, NOW); }
    }
}
