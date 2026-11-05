package vn.edu.fpt.ams.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import vn.edu.fpt.ams.util.EssaySubmissionView;
import vn.edu.fpt.ams.util.TeacherSubmissionView;

/** Only explicitly released evaluations can populate detail; pending attempts contain metadata only. */
public record StudentResultPage(long assignmentId, List<Attempt> attempts, Attempt selected, Released detail) {
    public StudentResultPage { attempts = List.copyOf(attempts); }

    public record Attempt(long submissionId, long assignmentId, long classId, String title, String classCode,
            int attemptNumber, Instant submittedAt, boolean released, Instant releasedAt, BigDecimal score) { }

    public record Released(String title, String instructions, BigDecimal maximumScore, BigDecimal score,
            String feedback, SubmissionEvaluation evaluation, EssaySubmissionView.Essay essay,
            TeacherSubmissionView.Answers objective, boolean contentUnavailable, boolean evaluationUnavailable) { }
}
