package vn.edu.fpt.ams.dto;

import vn.edu.fpt.ams.model.*;

public record TemplateDTO(long templateId, long subjectId, String title, String instructions, java.math.BigDecimal maximumScore, TemplateStatus status) {}
