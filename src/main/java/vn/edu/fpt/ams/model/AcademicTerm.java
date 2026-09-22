package vn.edu.fpt.ams.model;

/** Immutable data snapshot; persistence and validation are owned by DAO and Service. */
public record AcademicTerm(long termId, String termCode, String termName, java.time.Instant startsAt, java.time.Instant endsAt, boolean current, boolean active) {}
