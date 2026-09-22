package vn.edu.fpt.ams.model;

/** Immutable data snapshot; persistence and validation are owned by DAO and Service. */
public record Assignment(long assignmentId, long classId, long templateId, String title, AssignmentPurpose assignmentPurpose, ResponseType responseType, String instructions, String scopeText, String taskChecklistJson, java.math.BigDecimal maximumScore, AssignmentStatus status, java.time.Instant availableFrom, java.time.Instant dueAt, Integer timeLimitMinutes, Integer attemptLimit, java.time.Instant publishedAt, java.time.Instant cancelledAt) {}
