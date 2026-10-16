package vn.edu.fpt.ams.dto;

import java.util.List;
import java.util.Set;
import vn.edu.fpt.ams.model.Assignment;

public record AssignmentListPage(List<AssignmentClassOption> classes, Long selectedClassId,
                                 AssignmentListFilter filter, List<Assignment> assignments,
                                 Set<Long> submissionAssignmentIds) {
    public AssignmentListPage(List<AssignmentClassOption> classes, Long selectedClassId,
            AssignmentListFilter filter, List<Assignment> assignments) {
        this(classes,selectedClassId,filter,assignments,Set.of());
    }
    public AssignmentListPage {
        classes = List.copyOf(classes);
        assignments = List.copyOf(assignments);
        submissionAssignmentIds = Set.copyOf(submissionAssignmentIds);
    }
}
