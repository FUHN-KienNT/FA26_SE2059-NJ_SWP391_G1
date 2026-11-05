package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.SubmissionEvaluation;
import vn.edu.fpt.ams.dto.SubmissionEvaluation.Mark;
import vn.edu.fpt.ams.model.*;

class EssayEvaluationRulesTest {
    private static final String RUBRIC = """
            [{"task_order":1,"title":"Explain","maximum_points":5,"requirements":[
              {"requirement_order":1,"title":"Text","maximum_points":2,"answer_type":"TEXT"},
              {"requirement_order":2,"title":"Diagram","maximum_points":2,"answer_type":"DIAGRAM"}]},
             {"task_order":2,"title":"Legacy question","maximum_points":3}]
            """;

    private Assignment assignment(String rubric, String maximum) {
        return new Assignment(1, 1, 1, "Essay", AssignmentPurpose.REGULAR, ResponseType.ESSAY,
                "", "", rubric, new BigDecimal(maximum), AssignmentStatus.PUBLISHED,
                null, null, null, null, null, null);
    }

    private SubmissionEvaluation evaluation(Mark... marks) { return new SubmissionEvaluation(0, List.of(marks), ""); }
    private Mark mark(int task, int requirement, String score) {
        return new Mark(task, requirement, score == null ? null : new BigDecimal(score), "");
    }

    @Test void partialDraftAndZeroAreValidButCompletionNeedsEveryRequirement() {
        var partial = evaluation(mark(1, 1, "0"), mark(1, 2, null));
        assertDoesNotThrow(() -> EssayEvaluationRules.validate(assignment(RUBRIC, "10"), partial, false));
        assertThrows(IllegalArgumentException.class,
                () -> EssayEvaluationRules.validate(assignment(RUBRIC, "10"), partial, true));
        var complete = evaluation(mark(1, 1, "0"), mark(1, 2, "0"), mark(2, 1, "0"));
        assertDoesNotThrow(() -> EssayEvaluationRules.validate(assignment(RUBRIC, "10"), complete, true));
    }

    @Test void unusedParentAndAssignmentPointsAreNeverRescaled() {
        var complete = evaluation(mark(1, 1, "2"), mark(1, 2, "2"), mark(2, 1, "3"));
        EssayEvaluationRules.validate(assignment(RUBRIC, "10"), complete, true);
        assertEquals(new BigDecimal("7.00"), complete.total());
    }

    @Test void legacyTasksHaveAnImplicitRequirementWithTheirOwnMaximum() {
        var tasks = EssayEvaluationRules.questions(assignment(RUBRIC, "10"));
        assertEquals(1, tasks.get(1).requirements().size());
        assertEquals(1, tasks.get(1).requirements().get(0).order());
        assertEquals(new BigDecimal("3.00"), tasks.get(1).requirements().get(0).points());
    }

    @Test void rejectsUnknownDuplicateNegativeOverMaximumAndOverPrecisionMarks() {
        var assignment = assignment(RUBRIC, "10");
        for (Mark invalid : List.of(mark(1, 1, "2.01"), mark(2, 1, "3.01"), mark(7, 1, "0"), mark(1, 7, "0")))
            assertThrows(IllegalArgumentException.class, () -> EssayEvaluationRules.validate(assignment, evaluation(invalid), false));
        for (String score : List.of("-0.01", "0.001", "100.01"))
            assertThrows(IllegalArgumentException.class, () -> mark(1, 1, score));
        assertThrows(IllegalArgumentException.class, () -> evaluation(mark(1, 1, "1"), mark(1, 1, "2")));
    }

    @Test void invalidSavedRubricNeverBecomesGradable() {
        for (String invalid : List.of("plain text", "[]", RUBRIC.replace("\"maximum_points\":5", "\"maximum_points\":3"),
                RUBRIC.replace("\"requirement_order\":2", "\"requirement_order\":1"),
                RUBRIC.replace("\"maximum_points\":3", "\"maximum_points\":9")))
            assertThrows(IllegalArgumentException.class, () -> EssayEvaluationRules.questions(assignment(invalid, "10")));
    }

    @Test void evaluationRoundTripsNumericPointsDraftFeedbackAndStableOrdering() {
        var original = new SubmissionEvaluation(7, List.of(new Mark(2, 1, null, "<script>\r\nnhận xét"),
                new Mark(1, 1, new BigDecimal("1.25"), "😀")), "Overall\r\nfeedback");
        var restored = SubmissionEvaluation.read(original.json(), 7, original.feedback());
        assertEquals(original, restored);
        assertEquals(1, restored.marks().get(0).taskOrder());
        assertNull(restored.mark(2, 1).score());
        assertNull(restored.mark(9, 9));
        assertEquals(new BigDecimal("1.25"), restored.total());
        assertThrows(UnsupportedOperationException.class, () -> restored.marks().clear());
    }

    @Test void feedbackLimitsCountUnicodeCharactersAndNormalizeNewlines() {
        assertDoesNotThrow(() -> new Mark(1, 1, null, "😀".repeat(2000)));
        assertThrows(IllegalArgumentException.class, () -> new Mark(1, 1, null, "😀".repeat(2001)));
        assertDoesNotThrow(() -> new SubmissionEvaluation(0, List.of(), "😀".repeat(5000)));
        assertThrows(IllegalArgumentException.class, () -> new SubmissionEvaluation(0, List.of(), "😀".repeat(5001)));
    }

    @Test void malformedStoredEvaluationCannotBeSilentlyReinterpreted() {
        for (String invalid : List.of("null", "[]", "{}", "{\"format\":\"other\",\"marks\":[]}",
                "{\"format\":\"ams-evaluation-v1\",\"marks\":[{\"task_order\":1,\"requirement_order\":1,\"score\":\"1.00\"}]}",
                "{\"format\":\"ams-evaluation-v1\",\"marks\":[{\"task_order\":1.5,\"requirement_order\":1,\"score\":1}]}",
                "{\"format\":\"ams-evaluation-v1\",\"marks\":[{\"task_order\":1,\"requirement_order\":1,\"score\":1,\"feedback\":7}]}"))
            assertThrows(IllegalArgumentException.class, () -> SubmissionEvaluation.read(invalid, 0, ""), invalid);
    }
}
