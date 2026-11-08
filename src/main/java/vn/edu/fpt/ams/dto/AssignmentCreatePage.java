package vn.edu.fpt.ams.dto;

import java.math.BigDecimal;
import java.util.List;

/** Context and eligible templates for the class selected in Assignment List. */
public record AssignmentCreatePage(
        long classId,
        long subjectId,
        String classCode,
        String className,
        String subjectCode,
        String subjectName,
        List<TemplateOption> templates,
        List<vn.edu.fpt.ams.dto.AssignmentClassOption> eligibleClasses) {
    public AssignmentCreatePage(long classId, long subjectId, String classCode, String className,
                                String subjectCode, String subjectName, List<TemplateOption> templates) {
        this(classId, subjectId, classCode, className, subjectCode, subjectName, templates, List.of());
    }

    public AssignmentCreatePage {
        templates = List.copyOf(templates);
        eligibleClasses = List.copyOf(eligibleClasses);
    }

    public record TemplateOption(
            long templateId,
            String title,
            String instructions,
            BigDecimal maximumScore,
            String displayTitle) {
        public TemplateOption(
                long templateId, String title, String instructions, BigDecimal maximumScore) {
            this(templateId, title, instructions, maximumScore, title);
        }
    }
}
