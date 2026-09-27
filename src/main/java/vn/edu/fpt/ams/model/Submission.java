package vn.edu.fpt.ams.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Submission(long submissionId, long assignmentId, long enrollmentId,
                         int attemptNumber, SubmissionStatus status, Instant startedAt,
                         Instant deadlineAt, Instant submittedAt, String content,
                         String submissionLink, String answer, BigDecimal score, String feedback,
                         Long gradedBy, Instant gradedAt, Instant releasedAt) {}
