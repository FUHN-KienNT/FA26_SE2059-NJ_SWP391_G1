package vn.edu.fpt.ams.dto;

import java.util.List;

public record TeacherSubmissionListPage(AssignmentDetailPage assignment,
        TeacherSubmissionFilter filter, List<TeacherSubmissionRow> submissions, long totalCount) {
    public TeacherSubmissionListPage(AssignmentDetailPage assignment, TeacherSubmissionFilter filter,
            List<TeacherSubmissionRow> submissions) {
        this(assignment, filter, submissions, submissions.size());
    }

    public TeacherSubmissionListPage {
        submissions = List.copyOf(submissions);
        if (totalCount < 0) throw new IllegalArgumentException("Submission count cannot be negative.");
    }

    public long totalPages() {
        return Math.max(1, (totalCount + TeacherSubmissionFilter.PAGE_SIZE - 1) / TeacherSubmissionFilter.PAGE_SIZE);
    }
    public boolean hasPrevious() { return filter.page() > 1; }
    public boolean hasNext() { return filter.page() < totalPages(); }
    public long firstRow() { return submissions.isEmpty() ? 0 : filter.offset() + 1; }
    public long lastRow() { return submissions.isEmpty() ? 0 : filter.offset() + submissions.size(); }
}
