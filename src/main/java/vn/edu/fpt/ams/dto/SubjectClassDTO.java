package vn.edu.fpt.ams.dto;

import vn.edu.fpt.ams.model.ClassStatus;

/** Class list/detail view, including a possible unassigned Teacher. */
public record SubjectClassDTO(long classId, long subjectTermId, String classCode,
                              String className, int capacity, long activeRosterCount,
                              Long teacherId, String teacherName, String teacherEmail,
                              ClassStatus status) {}
