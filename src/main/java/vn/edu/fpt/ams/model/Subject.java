package vn.edu.fpt.ams.model;

/** Immutable data snapshot; persistence and validation are owned by DAO and Service. */
public record Subject(long subjectId, String subjectCode, String subjectName, String description, SubjectStatus status) {}
