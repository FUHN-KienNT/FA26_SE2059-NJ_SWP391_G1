package vn.edu.fpt.ams.model;

/** Immutable data snapshot; persistence and validation are owned by DAO and Service. */
public record SubjectExpert(long subjectExpertId, long subjectId, long expertId,
                            ResponsibilityType responsibilityType, boolean active,
                            java.time.Instant assignedAt, java.time.Instant deactivatedAt) {}
