package vn.edu.fpt.ams.model;

/** Immutable data snapshot; persistence and validation are owned by DAO and Service. */
public record AssignmentTemplate(long templateId, long subjectId, String title, String instructions,
                                 java.math.BigDecimal maximumScore, TemplateStatus status,
                                 java.time.Instant createdAt, java.time.Instant updatedAt) {}
