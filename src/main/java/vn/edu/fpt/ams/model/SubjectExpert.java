package vn.edu.fpt.ams.model;

/** Immutable data snapshot; persistence and validation are owned by DAO and Service. */
public record SubjectExpert(long subjectId, long expertId, boolean active, java.time.Instant assignedAt) {}
