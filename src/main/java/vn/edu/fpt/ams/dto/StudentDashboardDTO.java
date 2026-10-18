package vn.edu.fpt.ams.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Student overview only; no answer keys or unpublished evaluation data. */
public record StudentDashboardDTO(
        String fullName, String email, Instant asOf,
        long subjectCount, long classCount, long assignmentCount,
        long submittedAttemptCount, long releasedResultCount,
        List<ClassOverview> classes, List<OpenAssignment> openAssignments,
        List<ReleasedResult> results) {
    public StudentDashboardDTO {
        classes = List.copyOf(classes);
        openAssignments = List.copyOf(openAssignments);
        results = List.copyOf(results);
    }

    public record ClassOverview(String subjectCode, String subjectName, String classCode,
            String termCode, String termName, String teacherName) {}

    public record OpenAssignment(long assignmentId, String title, String classCode,
            String responseType, Instant dueAt) {
        public boolean hasAssignmentPage() { return "OBJECTIVE".equals(responseType); }
    }

    public record ReleasedResult(String title, String classCode, int attemptNumber,
            BigDecimal score, Instant releasedAt) {}
}
