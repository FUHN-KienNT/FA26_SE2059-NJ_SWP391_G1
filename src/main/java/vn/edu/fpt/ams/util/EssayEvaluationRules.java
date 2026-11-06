package vn.edu.fpt.ams.util;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import vn.edu.fpt.ams.dto.SubmissionEvaluation;
import vn.edu.fpt.ams.model.Assignment;
import vn.edu.fpt.ams.model.ResponseType;

/** Resolves the saved rubric and validates awarded points without distributing unused points. */
public final class EssayEvaluationRules {
    private EssayEvaluationRules() {}

    public static List<EssayQuestions.Task> questions(Assignment assignment) {
        if (assignment.responseType() != ResponseType.ESSAY)
            throw new IllegalArgumentException("Chỉ chấm theo yêu cầu đối với bài tập tự luận.");

        if (!AssignmentRules.validMaximum(assignment.maximumScore()))
            throw new IllegalArgumentException("Điểm tối đa của đề bài đã lưu không hợp lệ.");

        List<EssayQuestions.Task> questions = EssayQuestions.read(assignment.taskChecklistJson());
        BigDecimal total =
                questions.stream()
                        .map(EssayQuestions.Task::points)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.compareTo(assignment.maximumScore()) > 0)
            throw new IllegalArgumentException(
                    "Tổng điểm câu hỏi đã lưu vượt quá điểm tối đa của bài tập.");

        return questions;
    }

    public static void validate(
            Assignment assignment, SubmissionEvaluation evaluation, boolean complete) {
        List<EssayQuestions.Task> questions = questions(assignment);
        Map<String, BigDecimal> limits = new HashMap<>();

        for (var task : questions)
            for (var requirement : task.requirements())
                limits.put(task.order() + ":" + requirement.order(), requirement.points());

        for (var mark : evaluation.marks()) {
            BigDecimal limit = limits.get(mark.taskOrder() + ":" + mark.requirementOrder());

            if (limit == null)
                throw new IllegalArgumentException("Yêu cầu chấm điểm không thuộc đề bài đã nộp.");

            if (mark.score() != null && mark.score().compareTo(limit) > 0)
                throw new IllegalArgumentException(
                        "Điểm yêu cầu không được vượt quá điểm tối đa của yêu cầu.");
        }

        for (var task : questions) {
            BigDecimal total = BigDecimal.ZERO;

            for (var requirement : task.requirements()) {
                var mark = evaluation.mark(task.order(), requirement.order());

                if (mark == null || mark.score() == null) {
                    if (complete)
                        throw new IllegalArgumentException(
                                "Cần chấm điểm tất cả yêu cầu trước khi hoàn tất hoặc công bố.");
                } else total = total.add(mark.score());
            }

            if (total.compareTo(task.points()) > 0)
                throw new IllegalArgumentException("Tổng điểm yêu cầu vượt quá điểm của câu hỏi.");
        }

        if (evaluation.total().compareTo(assignment.maximumScore()) > 0)
            throw new IllegalArgumentException("Tổng điểm vượt quá điểm tối đa của bài tập.");
    }
}
