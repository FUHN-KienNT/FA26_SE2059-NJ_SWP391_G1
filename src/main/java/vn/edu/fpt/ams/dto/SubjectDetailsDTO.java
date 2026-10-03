package vn.edu.fpt.ams.dto;

import java.util.List;

public record SubjectDetailsDTO(
    SubjectDTO subject,
    List<SubjectExpertDTO> primaryExperts,
    List<SubjectExpertDTO> supportingExperts,
    List<SubjectExpertDTO> responsibilityHistory,
    List<SubjectTermViewDTO> subjectTerms) {}
