package vn.edu.fpt.ams.util;

import jakarta.servlet.http.HttpServletRequest;

/** Legacy preview hook retained for compatibility; Assignments always require sign-in. */
public final class AssignmentPreviewAccess {
    private AssignmentPreviewAccess() {}

    public static boolean allows(HttpServletRequest request) {
        return false;
    }
}
