package vn.edu.fpt.ams.model;

/** Immutable data snapshot; persistence and validation are owned by DAO and Service. */
public record SubjectTerm(long subjectTermId, long subjectId, long termId, boolean registrationEnabled, java.time.Instant registrationOpensAt, java.time.Instant registrationClosesAt) {}
