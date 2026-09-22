package vn.edu.fpt.ams.model;

/** One teaching class in a Subject Term. Teacher may be absent during setup. */
public record SubjectClass(long classId, long subjectTermId, Long teacherId,
                           String classCode, String className, int capacity,
                           boolean registrationEnabled, ClassStatus status) {}
