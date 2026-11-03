package vn.edu.fpt.ams.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import vn.edu.fpt.ams.dto.StudentClassFilter;
import vn.edu.fpt.ams.dto.StudentClassPage.AssignmentRow;

/** Presentation and navigation for a student's authorized class page. */
public final class StudentClassView {
    private StudentClassView() {}

    public static String pageUrl(String contextPath, long classId, StudentClassFilter filter, int page) {
        return contextPath + "/student/classes?classId=" + classId
                + "&q=" + encode(filter.search()) + "&responseType=" + encode(filter.responseType())
                + "&status=" + encode(filter.status()) + "&page=" + page;
    }

    public static String assignmentUrl(String contextPath, long classId, AssignmentRow assignment) {
        if (!"PUBLISHED".equals(assignment.status())) return null;
        return contextPath + "/student/assignments?assignmentId=" + assignment.assignmentId()
                + (assignment.submissionId() > 0 ? "&submissionId=" + assignment.submissionId() : "")
                + "&classId=" + classId;
    }

    public static String actionLabel(AssignmentRow assignment) {
        return switch (assignment.workStatus()) {
            case "NOT_STARTED" -> "Xem bài";
            case "IN_PROGRESS" -> "Xem bài / tiếp tục";
            default -> "Xem trạng thái";
        };
    }

    public static String workLabel(String status) {
        return "NOT_STARTED".equals(status) ? "Chưa làm" : Vietnamese.label(status);
    }

    public static String workTone(String status) {
        return switch (status) {
            case "IN_PROGRESS" -> "blue";
            case "SUBMITTED", "GRADED", "RELEASED" -> "green";
            case "EXPIRED", "VOID" -> "muted";
            default -> "amber";
        };
    }

    public static String availability(AssignmentRow assignment, Instant now) {
        if ("CANCELLED".equals(assignment.status())) return "Đã hủy";
        if (assignment.availableFrom() == null || assignment.dueAt() == null) return "Chưa có lịch";
        if (assignment.availableFrom() != null && now.isBefore(assignment.availableFrom())) return "Chưa mở";
        if (assignment.dueAt() != null && !now.isBefore(assignment.dueAt())) return "Đã đóng";
        return "Đang mở";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
