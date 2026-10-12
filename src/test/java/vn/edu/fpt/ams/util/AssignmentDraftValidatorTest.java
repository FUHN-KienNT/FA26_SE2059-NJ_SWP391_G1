package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;
import vn.edu.fpt.ams.dto.AssignmentForm;
import vn.edu.fpt.ams.model.AssignmentStatus;

class AssignmentDraftValidatorTest {
    static Map<String,String> input() {
        return new HashMap<>(Map.of("classId","10", "templateId","1", "title","My edited title",
                "instructions","Edited instructions", "maximumScore","10", "purpose","REGULAR",
                "responseType","ESSAY", "tasksJson","[]", "questionsJson","[]"));
    }
    @Test void allowsAllSupportedDraftTypesWithoutPublicationContent() {
        for (String[] type : List.of(new String[]{"REGULAR","ESSAY"}, new String[]{"REGULAR","OBJECTIVE"},
                new String[]{"PRACTICE","OBJECTIVE"})) {
            var values = input(); values.put("purpose",type[0]); values.put("responseType",type[1]);
            var draft = AssignmentDraftValidator.validate(new AssignmentForm(values));
            assertEquals(AssignmentStatus.DRAFT,draft.assignment().status());
            assertEquals("My edited title",draft.assignment().title());
            assertEquals("Edited instructions",draft.assignment().instructions());
            assertNull(draft.assignment().dueAt()); assertNull(draft.assignment().attemptLimit());
            assertNull(draft.assignment().publishedAt()); assertTrue(draft.questions().isEmpty());
            assertEquals("",draft.assignment().scopeText()); assertEquals("[]",draft.assignment().taskChecklistJson());
        }
    }
    @Test void preservesEssayScopeAndCanonicalizesTaskOrder() {
        var values = input();
        values.put("scopeText","UML & Java");
        values.put("tasksJson","""
                [{"title":"Task one","description":"Full description","task_order":99},
                 {"title":"Task two","description":"Second description","task_order":7}]
                """);
        var result = AssignmentDraftValidator.validate(new AssignmentForm(values));
        assertEquals("UML & Java",result.assignment().scopeText());
        var tasks=JsonParser.parseString(result.assignment().taskChecklistJson()).getAsJsonArray();
        assertEquals(2,tasks.size());
        assertEquals(1,tasks.get(0).getAsJsonObject().get("task_order").getAsInt());
        assertEquals("Task one",tasks.get(0).getAsJsonObject().get("title").getAsString());
        assertEquals("Full description",tasks.get(0).getAsJsonObject().get("description").getAsString());
        assertEquals(2,tasks.get(1).getAsJsonObject().get("task_order").getAsInt());
        assertEquals("Second description",tasks.get(1).getAsJsonObject().get("description").getAsString());
        assertTrue(result.questions().isEmpty());
    }
    @Test void convertsVietnamTimesAndCanonicalizesObjectiveQuestionIdentity() {
        var values = input(); values.put("responseType","OBJECTIVE");
        values.put("availableFrom","2026-10-12T14:00"); values.put("dueAt","2026-10-13T00:00");
        values.put("questionsJson","""
                [{"question_text":"Question <one>","maximum_points":"2.50","options":[
                  {"option_id":"injected-id","option_text":"A","is_correct":false},
                  {"option_text":"B","is_correct":true}]}]
                """);
        var result = AssignmentDraftValidator.validate(new AssignmentForm(values));
        assertEquals(Instant.parse("2026-10-12T07:00:00Z"),result.assignment().availableFrom());
        assertEquals(Instant.parse("2026-10-12T17:00:00Z"),result.assignment().dueAt());
        var question=result.questions().get(0);
        assertEquals(new BigDecimal("2.50"),question.maximumPoints()); // Total need not match until publication.
        var options=JsonParser.parseString(question.options()).getAsJsonArray();
        assertEquals(1,options.get(0).getAsJsonObject().get("option_id").getAsInt());
        assertTrue(options.get(1).getAsJsonObject().get("is_correct").getAsBoolean());
    }
    @Test void objectiveDraftsIgnoreInactiveEssayContentEvenWhenTasksAreMalformed() {
        for (String purpose : List.of("REGULAR","PRACTICE")) {
            for (String tasks : List.of("[{\"title\":\"Inactive task\",\"description\":\"Inactive details\"}]",
                    "{broken","{}","[null]","[{\"description\":\"Missing title\"}]")) {
                var values=input(); values.put("purpose",purpose); values.put("responseType","OBJECTIVE");
                values.put("scopeText","Inactive Essay scope"); values.put("tasksJson",tasks);
                var result=assertDoesNotThrow(()->AssignmentDraftValidator.validate(new AssignmentForm(values)),tasks);
                assertEquals("",result.assignment().scopeText(),purpose+": "+tasks);
                assertEquals("[]",result.assignment().taskChecklistJson(),purpose+": "+tasks);
                assertTrue(result.questions().isEmpty());
                assertEquals(AssignmentStatus.DRAFT,result.assignment().status());
            }
        }
    }
    @Test void essayDraftsIgnoreInactiveObjectiveContentEvenWhenQuestionsAreMalformed() {
        for (String questions : List.of("[{\"question_text\":\"Inactive question\",\"maximum_points\":\"2\",\"options\":[]}]",
                "{broken","{}","[null]","[{\"question_text\":\"Missing points and options\"}]")) {
            var values=input(); values.put("scopeText","Essay scope");
            values.put("tasksJson","[{\"title\":\"Essay task\",\"description\":\"Essay details\"}]");
            values.put("questionsJson",questions);
            var result=assertDoesNotThrow(()->AssignmentDraftValidator.validate(new AssignmentForm(values)),questions);
            assertEquals("Essay scope",result.assignment().scopeText());
            var tasks=JsonParser.parseString(result.assignment().taskChecklistJson()).getAsJsonArray();
            assertEquals(1,tasks.size()); assertEquals("Essay task",tasks.get(0).getAsJsonObject().get("title").getAsString());
            assertTrue(result.questions().isEmpty(),questions);
            assertEquals(AssignmentStatus.DRAFT,result.assignment().status());
        }
    }
    @Test void rejectsInvalidFieldsWithoutCoercingThem() {
        for (var change : List.of(Map.entry("classId","-1"),Map.entry("templateId","abc"),
                Map.entry("title"," "),Map.entry("title","x".repeat(201)),Map.entry("maximumScore","0"),
                Map.entry("maximumScore","1.001"),Map.entry("maximumScore","100000000"),
                Map.entry("timeLimitMinutes","-1"),Map.entry("timeLimitMinutes","2147483648"),
                Map.entry("attemptLimit","2"),Map.entry("availableFrom","invalid"),Map.entry("purpose","OTHER"),
                Map.entry("responseType","FILE"),Map.entry("tasksJson","[null]"),Map.entry("tasksJson","{}"),
                Map.entry("tasksJson","{broken"),
                Map.entry("questionsJson","{broken"))) {
            var values=input(); values.put(change.getKey(),change.getValue());
            if (change.getKey().equals("questionsJson")) values.put("responseType","OBJECTIVE");
            var ex=assertThrows(AssignmentDraftValidator.ValidationException.class,
                    ()->AssignmentDraftValidator.validate(new AssignmentForm(values)),change.toString());
            assertTrue(ex.errors().containsKey(change.getKey()),change.toString());
        }
    }
    @Test void rejectsIncompatibleTypeDatesAndMalformedQuestionOptions() {
        var values=input(); values.put("purpose","PRACTICE");
        assertThrows(AssignmentDraftValidator.ValidationException.class,()->AssignmentDraftValidator.validate(new AssignmentForm(values)));
        values.put("purpose","REGULAR"); values.put("availableFrom","2026-10-12T12:00"); values.put("dueAt","2026-10-12T12:00");
        assertThrows(AssignmentDraftValidator.ValidationException.class,()->AssignmentDraftValidator.validate(new AssignmentForm(values)));
        values.remove("dueAt"); values.put("responseType","OBJECTIVE");
        for(String options : List.of("null","[null]","[{\"option_text\":\"A\",\"is_correct\":\"true\"}]",
                "[{\"option_text\":\"A\",\"is_correct\":true},{\"option_text\":\"B\",\"is_correct\":true}]")) {
            values.put("questionsJson","[{\"question_text\":\"Q\",\"maximum_points\":\"1\",\"options\":"+options+"}]");
            assertThrows(AssignmentDraftValidator.ValidationException.class,()->AssignmentDraftValidator.validate(new AssignmentForm(values)));
        }
        values.put("questionsJson","[{\"question_text\":\"Q\",\"maximum_points\":\"1\",\"options\":[]}]");
        assertEquals(1,AssignmentDraftValidator.validate(new AssignmentForm(values)).questions().size());
    }
}
