package vn.edu.fpt.ams.dto;
import vn.edu.fpt.ams.model.ResponsibilityType;
public record ExpertSubjectDTO(long subjectId, String code, String name,
                               ResponsibilityType responsibility, boolean active) {}
