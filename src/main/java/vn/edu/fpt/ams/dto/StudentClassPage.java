package vn.edu.fpt.ams.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Class and assignment summaries only: no answer keys or student scores. */
public record StudentClassPage(
        ClassInfo classroom,
        List<ClassInfo> classes,
        StudentClassFilter filter,
        Instant now,
        Counts counts,
        List<AssignmentRow> assignments,
        long total,
        int page,
        int totalPages) {
    public StudentClassPage {
        classes = List.copyOf(classes);
        assignments = List.copyOf(assignments);
    }

    public record ClassInfo(
            long classId,
            long subjectTermId,
            String classCode,
            String className,
            String subjectCode,
            String subjectName,
            String termCode,
            String termName,
            String teacherName) {}

    public record Counts(long total, long open, long notStarted, long submitted) {}

    public record AssignmentRow(
            long assignmentId,
            String title,
            String purpose,
            String responseType,
            BigDecimal maximumScore,
            Instant availableFrom,
            Instant dueAt,
            String status,
            long submissionId,
            int attemptNumber,
            String workStatus,
            Instant submittedAt,
            String groupName) {
        public AssignmentRow(long assignmentId, String title, String purpose, String responseType,
                             BigDecimal maximumScore, Instant availableFrom, Instant dueAt, String status,
                             long submissionId, int attemptNumber, String workStatus, Instant submittedAt) {
            this(assignmentId, title, purpose, responseType, maximumScore, availableFrom, dueAt,
                    status, submissionId, attemptNumber, workStatus, submittedAt, null);
        }
    }
}
