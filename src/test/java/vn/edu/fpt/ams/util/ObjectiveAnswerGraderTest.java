package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.model.Question;

class ObjectiveAnswerGraderTest {
    private List<Question> questions() {
        return List.of(new Question(11,1,1,"Choose primes",new BigDecimal("7.25"),"""
            [{"option_id":"A","option_text":"2","is_correct":true},
             {"option_id":"B","option_text":"4","is_correct":false},
             {"option_id":"C","option_text":"3","is_correct":true}]
            """),new Question(12,1,2,"One plus one",new BigDecimal("2.75"),"""
            [{"option_id":"A","option_text":"2","is_correct":true},
             {"option_id":"B","option_text":"4","is_correct":false}]
            """));
    }
    @Test void multipleAnswerQuestionRequiresExactSetAndSupportsReverseOrder() {
        for(var selection:List.of(List.of("A"),List.of("A","B","C"),List.of("B"),List.<String>of()))
            assertEquals(0,ObjectiveAnswerGrader.grade(questions(),Map.of(11L,selection)).score().signum());
        var result=ObjectiveAnswerGrader.grade(questions(),Map.of(11L,List.of("C","A"),12L,List.of("A")));
        assertEquals(new BigDecimal("10.00"),result.score());
        var saved=JsonParser.parseString(result.answerJson()).getAsJsonArray();
        assertEquals(2,saved.get(0).getAsJsonObject().getAsJsonArray("selected_option_ids").size());
        assertEquals(new BigDecimal("7.25"),saved.get(0).getAsJsonObject().get("awarded_points").getAsBigDecimal());
        assertFalse(TeacherSubmissionView.answers(result.answerJson(),questions()).invalid());
    }
    @Test void forgedForeignDuplicateOrMultipleSingleSelectionsAreRejected() {
        for(var answers:List.of(Map.of(99L,List.of("A")),Map.of(11L,List.of("Z")),
                Map.of(11L,List.of("A","A")),Map.of(12L,List.of("A","B"))))
            assertThrows(IllegalArgumentException.class,()->ObjectiveAnswerGrader.grade(questions(),answers));
    }
    @Test void studentDisplayHasSelectionModeButNoAnswerKey() {
        var display=ObjectiveAnswerGrader.display(questions());
        assertTrue(display.get(0).multiple());assertFalse(display.get(1).multiple());
        assertEquals(List.of("id","text"),Arrays.stream(display.get(0).options().get(0).getClass().getRecordComponents()).map(c->c.getName()).toList());
    }
    @Test void brokenStoredKeyFailsClosed() {
        var q=questions().get(0);
        assertThrows(IllegalStateException.class,()->ObjectiveAnswerGrader.display(List.of(
                new Question(11,1,1,"Q",BigDecimal.ONE,q.options().replace("true","false")))));
        assertThrows(IllegalStateException.class,()->ObjectiveAnswerGrader.display(List.of(q,q)));
    }
    @Test void savedSelectionsRoundTripWithoutScoresOrAnswerKey() {
        var selections=Map.of(11L,List.of("A","C"),12L,List.of("B"));
        String draft=ObjectiveAnswerGrader.draft(questions(),selections);
        assertFalse(draft.contains("awarded_points"));assertFalse(draft.contains("is_correct"));
        assertEquals(selections,ObjectiveAnswerGrader.selections(draft));
        assertEquals(new BigDecimal("7.25"),ObjectiveAnswerGrader.grade(questions(),ObjectiveAnswerGrader.selections(draft)).score());
        assertEquals(Map.of(),ObjectiveAnswerGrader.selections("[]"));
    }
    @Test void oldSingleSelectionsAndUnansweredQuestionsAreSupportedWithoutTruncatingIds() {
        assertEquals(Map.of(11L,List.of("A"),12L,List.of()),ObjectiveAnswerGrader.selections("""
                [{"question_id":11,"selected_option_id":"A"},{"question_id":"12","selected_option_id":null}]
                """));
        assertEquals(Map.of(11L,List.of()),ObjectiveAnswerGrader.selections("[{\"question_id\":11}]"));
        assertEquals(Map.of(11L,List.of("2")),ObjectiveAnswerGrader.selections("[{\"question_id\":11,\"selected_option_id\":2}]"));
        for(String questionId:List.of("11.5","11.0","1e1","0","-1","9223372036854775808","true","null"))
            assertThrows(IllegalStateException.class,()->ObjectiveAnswerGrader.selections("[{\"question_id\":"+questionId+",\"selected_option_id\":\"A\"}]"));
        for(String options:List.of("[true]","[null]","[{}]","[\"A\",\"A\"]","[\"\"]"))
            assertThrows(IllegalStateException.class,()->ObjectiveAnswerGrader.selections("[{\"question_id\":11,\"selected_option_ids\":"+options+"}]"));
    }
}
