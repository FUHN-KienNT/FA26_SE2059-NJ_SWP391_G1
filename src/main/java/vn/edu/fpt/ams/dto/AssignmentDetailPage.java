package vn.edu.fpt.ams.dto;

import java.util.List;
import vn.edu.fpt.ams.model.Assignment;
import vn.edu.fpt.ams.model.Question;

/** Data for one assignment that the current Teacher is allowed to view. */
public record AssignmentDetailPage(
        Assignment assignment,
        String classCode,
        String className,
        String subjectCode,
        String subjectName,
        String termCode,
        String templateTitle,
        List<Question> questions,
        boolean canViewSubmissions,
        GroupAssignmentConfig groupConfig,
        List<AssignmentGroupTarget> groupTargets) {
    public AssignmentDetailPage(Assignment assignment, String classCode, String className,
            String subjectCode, String subjectName, String termCode, String templateTitle,
            List<Question> questions, boolean canViewSubmissions) {
        this(assignment, classCode, className, subjectCode, subjectName, termCode, templateTitle,
                questions, canViewSubmissions, GroupAssignmentConfig.individual(), List.of());
    }
    public AssignmentDetailPage(
            Assignment assignment,
            String classCode,
            String className,
            String subjectCode,
            String subjectName,
            String termCode,
            String templateTitle,
            List<Question> questions) {
        this(
                assignment,
                classCode,
                className,
                subjectCode,
                subjectName,
                termCode,
                templateTitle,
                questions,
                false);
    }

    public AssignmentDetailPage {
        questions = List.copyOf(questions);
        if (groupConfig == null) groupConfig = GroupAssignmentConfig.individual();
        groupTargets = List.copyOf(groupTargets);
    }
}
