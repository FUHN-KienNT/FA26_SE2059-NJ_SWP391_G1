package vn.edu.fpt.ams.dto;

import java.math.BigDecimal;
import java.time.Instant;
import vn.edu.fpt.ams.model.TemplateStatus;

/** UC23 template row decorated with its assigned Subject. */
public record ExpertTemplateDTO(
    long templateId, long subjectId, String subjectCode, String subjectName,
    String title, BigDecimal maximumScore, TemplateStatus status, Instant updatedAt) {}
