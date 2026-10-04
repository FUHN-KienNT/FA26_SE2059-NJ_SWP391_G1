package vn.edu.fpt.ams.dto;

import java.util.List;
import vn.edu.fpt.ams.model.SubjectStatus;

public record SubjectSummaryDTO(
    long subjectId,
    String subjectCode,
    String subjectName,
    SubjectStatus status,
    List<SubjectExpertDTO> primaryExperts,
    List<SubjectExpertDTO> supportingExperts) {}
