package vn.edu.fpt.ams.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Immutable data snapshot; persistence and validation are owned by DAO and Service. */
public record Assignment(
        long assignmentId, long classId, long templateId, String title,
        AssignmentPurpose assignmentPurpose, ResponseType responseType, String instructions,
        String scopeText, String taskChecklistJson, BigDecimal maximumScore, AssignmentStatus status,
        Instant availableFrom, Instant dueAt, Integer timeLimitMinutes, Integer attemptLimit,
        Instant publishedAt, Instant cancelledAt, Instant closedAt) {

    public Assignment(long assignmentId, long classId, long templateId, String title,
                      AssignmentPurpose assignmentPurpose, ResponseType responseType, String instructions,
                      String scopeText, String taskChecklistJson, BigDecimal maximumScore, AssignmentStatus status,
                      Instant availableFrom, Instant dueAt, Integer timeLimitMinutes, Integer attemptLimit,
                      Instant publishedAt, Instant cancelledAt) {
        this(assignmentId, classId, templateId, title, assignmentPurpose, responseType, instructions,
                scopeText, taskChecklistJson, maximumScore, status, availableFrom, dueAt,
                timeLimitMinutes, attemptLimit, publishedAt, cancelledAt, null);
    }
}
