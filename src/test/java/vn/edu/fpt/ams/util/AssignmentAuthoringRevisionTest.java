package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.AssignmentForm;

class AssignmentAuthoringRevisionTest {
    private static final String TASKS = """
            [{"task_order":1,"title":"Phân tích hệ thống","maximum_points":10,
              "rubric":"Tiêu chí chung bí mật",
              "requirements":[{"requirement_order":1,"title":"Vẽ sơ đồ",
                "maximum_points":10,"answer_type":"DIAGRAM",
                "rubric":"Tiêu chí riêng bí mật","allowed_tools":["PLANTUML"]}]}]
            """;

    @Test
    void storesPrivateRubricsButPublicProjectionNeverContainsThem() {
        var normalized = EssayQuestions.normalize(JsonParser.parseString(TASKS).getAsJsonArray(), null);
        var question = EssayQuestions.read(normalized.toString()).get(0);
        assertEquals("Tiêu chí chung bí mật", question.rubric());
        assertEquals("Tiêu chí riêng bí mật", question.requirements().get(0).rubric());
        assertEquals(List.of("PLANTUML"), question.requirements().get(0).allowedTools());

        String publicJson = EssayQuestions.studentJson(normalized.toString());
        assertFalse(publicJson.contains("rubric"));
        assertFalse(publicJson.contains("bí mật"));
        assertTrue(publicJson.contains("PLANTUML"));
        assertEquals("Vẽ sơ đồ", EssayQuestions.read(publicJson).get(0).requirements().get(0).title());
    }

    @Test
    void validatesToolChoicesAndLegacyDefaults() {
        String legacy = TASKS.replace(",\"allowed_tools\":[\"PLANTUML\"]", "");
        assertEquals(List.of("DRAWIO", "PLANTUML"),
                EssayQuestions.read(legacy).get(0).requirements().get(0).allowedTools());
        assertThrows(IllegalArgumentException.class,
                () -> EssayQuestions.read(TASKS.replace("[\"PLANTUML\"]", "[]")));
        assertThrows(IllegalArgumentException.class,
                () -> EssayQuestions.read(TASKS.replace("[\"PLANTUML\"]", "[\"OTHER\"]")));
        assertThrows(IllegalArgumentException.class,
                () -> EssayQuestions.read(TASKS.replace("\"DIAGRAM\"", "\"TEXT\"")));
        assertThrows(IllegalArgumentException.class,
                () -> EssayQuestions.read(TASKS.replace("Tiêu chí riêng bí mật", "x".repeat(2001))));
    }

    @Test
    void scenarioAllows4000CharactersAndRemovedLimitsAreDiscarded() {
        Map<String, String> values = new HashMap<>(Map.of(
                "classId", "1", "templateId", "2", "purpose", "REGULAR",
                "responseType", "ESSAY", "title", "T".repeat(4000),
                "maximumScore", "10", "tasksJson", TASKS));
        values.put("timeLimitMinutes", "not-a-limit");
        values.put("attemptLimit", "not-a-limit");
        var draft = AssignmentDraftValidator.validate(new AssignmentForm(values));
        assertEquals(4000, draft.assignment().title().length());
        assertNull(draft.assignment().timeLimitMinutes());
        assertNull(draft.assignment().attemptLimit());

        values.put("title", "T".repeat(4001));
        assertThrows(AssignmentDraftValidator.ValidationException.class,
                () -> AssignmentDraftValidator.validate(new AssignmentForm(values)));
    }

    @Test
    void selectedClassesDefaultToContextAndDeduplicateInStableLockOrder() {
        assertEquals(List.of(9L), new AssignmentForm(Map.of("classId", "9")).selectedClassIds());
        assertEquals(List.of(9L, 10L), new AssignmentForm(
                Map.of("classId", "9", "targetClassIds", "10,9,10")).selectedClassIds());
        assertThrows(IllegalArgumentException.class, () -> new AssignmentForm(
                Map.of("classId", "9", "targetClassIds", "9,0")).selectedClassIds());
    }
}
