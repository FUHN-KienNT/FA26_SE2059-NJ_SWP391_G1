package vn.edu.fpt.ams.dto;

import vn.edu.fpt.ams.model.ResponsibilityType;

public record SubjectExpertDTO(
    long subjectExpertId,
    long expertId,
    String fullName,
    String email,
    ResponsibilityType responsibilityType,
    boolean active) {}
