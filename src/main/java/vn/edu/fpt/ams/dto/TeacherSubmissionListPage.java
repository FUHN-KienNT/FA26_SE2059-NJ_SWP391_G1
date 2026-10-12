package vn.edu.fpt.ams.dto;

import java.util.List;

public record TeacherSubmissionListPage(AssignmentDetailPage assignment,
        TeacherSubmissionFilter filter, List<TeacherSubmissionRow> submissions) {
    public TeacherSubmissionListPage {
        submissions = List.copyOf(submissions);
    }
}
