package vn.edu.fpt.ams.dto;

/** Counts for the Expert Iteration 2 landing page. */
public record ExpertDashboardDTO(
    long assignedSubjects,
    long accessibleClasses,
    long visibleTemplates,
    long pendingAdditionalClassRequests) {}
