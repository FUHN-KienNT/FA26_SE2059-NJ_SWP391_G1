package vn.edu.fpt.ams.dto;

import java.util.List;
import vn.edu.fpt.ams.util.EssayQuestions;

public record TeacherEvaluationPage(TeacherSubmissionDetailPage detail, SubmissionEvaluation evaluation,
        List<EssayQuestions.Task> questions, String blockedReason) {
    public TeacherEvaluationPage { questions = List.copyOf(questions); }
}
