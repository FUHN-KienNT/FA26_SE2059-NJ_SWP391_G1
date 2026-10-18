package vn.edu.fpt.ams.util;

import vn.edu.fpt.ams.model.Assignment;
import vn.edu.fpt.ams.model.AssignmentStatus;

/** Shared visibility rule; ownership must still be checked by the calling service. */
public final class AssignmentSubmissionAccess {
    private AssignmentSubmissionAccess() { }

    public static boolean canView(Assignment assignment, boolean hasSubmittedWork) {
        return assignment != null && (assignment.status() == AssignmentStatus.PUBLISHED
                || (assignment.publishedAt() != null && hasSubmittedWork));
    }
}
