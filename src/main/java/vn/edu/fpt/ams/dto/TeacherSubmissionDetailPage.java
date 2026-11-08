package vn.edu.fpt.ams.dto;

public record TeacherSubmissionDetailPage(
        AssignmentDetailPage assignment, TeacherSubmissionRow row, GroupGradePage group) {
    public TeacherSubmissionDetailPage(AssignmentDetailPage assignment, TeacherSubmissionRow row) {
        this(assignment,row,null);
    }
}
