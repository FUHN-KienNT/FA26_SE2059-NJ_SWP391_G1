package vn.edu.fpt.ams.dto;

import vn.edu.fpt.ams.model.*;

public record SubjectDTO(long subjectId, String subjectCode, String subjectName, String description, SubjectStatus status) {}
