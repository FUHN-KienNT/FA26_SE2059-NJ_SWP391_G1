package vn.edu.fpt.ams.dto;

/** Fields submitted by the Expert's create and update class forms. */
public record SubjectClassFormDTO(long classId, long subjectTermId, String classCode,
                                  String className, int capacity, boolean registrationEnabled) {}
