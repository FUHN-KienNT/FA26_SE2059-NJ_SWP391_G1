package vn.edu.fpt.ams.dto;

import vn.edu.fpt.ams.model.*;

/** Read-only UC19 row after applying Primary/Supporting scope. */
public record AssignedClassDTO(
    long classId,
    long subjectId,
    String subjectCode,
    String subjectName,
    ResponsibilityType responsibility,
    long termId,
    String termCode,
    String termName,
    String classCode,
    String className,
    int capacity,
    long activeRosterCount,
    String teacherName,
    String teacherEmail,
    boolean registrationEnabled,
    ClassStatus status) {}
