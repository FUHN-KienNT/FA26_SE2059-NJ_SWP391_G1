package vn.edu.fpt.ams.dto;

import java.util.List;
import java.util.Set;
import vn.edu.fpt.ams.model.Assignment;

public record AssignmentListPage(
        List<AssignmentClassOption> classes,
        Long selectedClassId,
        AssignmentListFilter filter,
        List<Assignment> assignments,
        Set<Long> submissionAssignmentIds,
        long totalCount,
        java.util.Map<Long, java.time.Instant> creationTimes,
        Set<Long> groupAssignmentIds) {
    public AssignmentListPage(List<AssignmentClassOption> classes, Long selectedClassId,
            AssignmentListFilter filter, List<Assignment> assignments,
            Set<Long> submissionAssignmentIds, long totalCount,
            java.util.Map<Long, java.time.Instant> creationTimes) {
        this(classes, selectedClassId, filter, assignments, submissionAssignmentIds, totalCount,
                creationTimes, Set.of());
    }
    public AssignmentListPage(List<AssignmentClassOption> classes, Long selectedClassId,
            AssignmentListFilter filter, List<Assignment> assignments,
            Set<Long> submissionAssignmentIds) {
        this(classes, selectedClassId, filter, assignments, submissionAssignmentIds,
                assignments.size(), java.util.Map.of());
    }

    public AssignmentListPage(
            List<AssignmentClassOption> classes,
            Long selectedClassId,
            AssignmentListFilter filter,
            List<Assignment> assignments) {
        this(classes, selectedClassId, filter, assignments, Set.of());
    }

    public AssignmentListPage {
        classes = List.copyOf(classes);
        assignments = List.copyOf(assignments);
        submissionAssignmentIds = Set.copyOf(submissionAssignmentIds);
        creationTimes = java.util.Map.copyOf(creationTimes);
        groupAssignmentIds = Set.copyOf(groupAssignmentIds);
    }

    public long totalPages() {
        return Math.max(1, (totalCount + AssignmentListFilter.PAGE_SIZE - 1)
                / AssignmentListFilter.PAGE_SIZE);
    }

    public long firstRow() {
        return assignments.isEmpty() ? 0 : filter.offset() + 1;
    }

    public long lastRow() {
        return assignments.isEmpty() ? 0 : filter.offset() + assignments.size();
    }
}
