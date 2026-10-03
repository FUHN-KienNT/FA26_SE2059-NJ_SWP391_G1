package vn.edu.fpt.ams.dto;

import java.util.List;
import vn.edu.fpt.ams.model.Assignment;

public record AssignmentListPage(List<AssignmentClassOption> classes, Long selectedClassId,
                                 AssignmentListFilter filter, List<Assignment> assignments) {
    public AssignmentListPage {
        classes = List.copyOf(classes);
        assignments = List.copyOf(assignments);
    }
}
