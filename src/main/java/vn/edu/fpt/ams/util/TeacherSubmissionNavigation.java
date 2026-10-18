package vn.edu.fpt.ams.util;

import vn.edu.fpt.ams.dto.TeacherSubmissionFilter;

/** Retains the list's filters and page when opening an attempt and returning to it. */
public final class TeacherSubmissionNavigation {
    private TeacherSubmissionNavigation() { }

    public static String listUrl(String base, long assignmentId, TeacherSubmissionFilter filter) {
        return TeacherSubmissionView.listUrl(base, assignmentId, filter)
                + (filter.page() > 1 ? "&page=" + filter.page() : "");
    }

    public static String detailUrl(String base, long assignmentId, long submissionId,
            TeacherSubmissionFilter filter) {
        return listUrl(base, assignmentId, filter) + "&mode=view&submissionId=" + submissionId;
    }
}
