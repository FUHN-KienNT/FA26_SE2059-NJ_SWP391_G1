package vn.edu.fpt.ams.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import vn.edu.fpt.ams.util.EssaySubmissionView;
import vn.edu.fpt.ams.util.TeacherSubmissionView;

/** Only explicitly released evaluations can populate detail; pending attempts contain metadata only. */
public record StudentResultPage(
        long assignmentId, List<Attempt> attempts, Attempt selected, Released detail,
        int catalogPage, long catalogTotal, StudentGroupResult groupResult) {
    public static final int PAGE_SIZE = 10;

    public StudentResultPage(
            long assignmentId, List<Attempt> attempts, Attempt selected, Released detail) {
        this(assignmentId, attempts, selected, detail, 1, attempts.size(), null);
    }

    public StudentResultPage(long assignmentId, List<Attempt> attempts, Attempt selected, Released detail,
                             int catalogPage, long catalogTotal) {
        this(assignmentId, attempts, selected, detail, catalogPage, catalogTotal, null);
    }

    public StudentResultPage {
        attempts = List.copyOf(attempts);
    }

    public int catalogPages() {
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, (catalogTotal + PAGE_SIZE - 1) / PAGE_SIZE));
    }

    public record Attempt(
            long submissionId,
            long assignmentId,
            long classId,
            String title,
            String classCode,
            int attemptNumber,
            Instant submittedAt,
            boolean released,
            Instant releasedAt,
            BigDecimal score) {}

    public record Released(
            String title,
            String instructions,
            BigDecimal maximumScore,
            BigDecimal score,
            String feedback,
            SubmissionEvaluation evaluation,
            EssaySubmissionView.Essay essay,
            TeacherSubmissionView.Answers objective,
            boolean contentUnavailable,
            boolean evaluationUnavailable) {}
}
