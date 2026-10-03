package vn.edu.fpt.ams.dto;

import vn.edu.fpt.ams.model.ResponsibilityType;
import vn.edu.fpt.ams.model.SubjectStatus;

/** One active Expert responsibility shown by UC18. */
public record AssignedSubjectDTO(
    long subjectId,
    String subjectCode,
    String subjectName,
    SubjectStatus status,
    ResponsibilityType responsibility,
    long accessibleClassCount) {}
