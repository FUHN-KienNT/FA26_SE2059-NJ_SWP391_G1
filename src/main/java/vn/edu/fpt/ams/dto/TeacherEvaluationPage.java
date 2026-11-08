package vn.edu.fpt.ams.dto;

import java.util.List;
import vn.edu.fpt.ams.util.EssayQuestions;

public record TeacherEvaluationPage(
        TeacherSubmissionDetailPage detail,
        SubmissionEvaluation evaluation,
        List<EssayQuestions.Task> questions,
        String blockedReason,
        TeacherEvaluationHistory history) {
    public TeacherEvaluationPage(
            TeacherSubmissionDetailPage detail,
            SubmissionEvaluation evaluation,
            List<EssayQuestions.Task> questions,
            String blockedReason) {
        this(detail, evaluation, questions, blockedReason, TeacherEvaluationHistory.empty());
    }

    public TeacherEvaluationPage {
        questions = List.copyOf(questions);
        if (history == null) history = TeacherEvaluationHistory.empty();
    }
}
