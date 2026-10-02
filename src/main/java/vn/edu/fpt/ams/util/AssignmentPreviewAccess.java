package vn.edu.fpt.ams.util;

import jakarta.servlet.http.HttpServletRequest;

/** Temporary, local-only Assignment List preview until the login module is ready. */
public final class AssignmentPreviewAccess {
    private AssignmentPreviewAccess() {}

    public static boolean allows(HttpServletRequest request) {
        // Disabled for shared deployments; opt in only in ignored local configuration.
        String enabled = System.getProperty("ams.assignment.preview",
                AppConfig.get("AMS_ASSIGNMENT_PREVIEW", "assignment.preview.enabled", "false"));
        if (!Boolean.parseBoolean(enabled)) return false;
        if (!"GET".equals(request.getMethod())
                || !"/teacher/assignments".equals(request.getServletPath())) return false;
        String address = request.getRemoteAddr();
        if (!"127.0.0.1".equals(address) && !"::1".equals(address)
                && !"0:0:0:0:0:0:0:1".equals(address)) return false;
        var session = request.getSession(false);
        // Never replace an existing authenticated identity, including another role.
        return session == null || (session.getAttribute("userId") == null
                && session.getAttribute("role") == null && session.getAttribute("accountStatus") == null);
    }
}
