package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.StudentClassFilter;
import vn.edu.fpt.ams.dto.StudentClassPage.AssignmentRow;

class StudentClassViewTest {
    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");

    private static AssignmentRow row(
            String status, String work, long submission, Instant start, Instant end) {
        return new AssignmentRow(
                85,
                "Câu hỏi",
                "REGULAR",
                "ESSAY",
                BigDecimal.TEN,
                start,
                end,
                status,
                submission,
                submission == 0 ? 0 : 1,
                work,
                null);
    }

    @Test
    void navigationEncodesSearchAndKeepsClassFilterAndPage() {
        String url =
                StudentClassView.pageUrl(
                        "/ams",
                        9,
                        new StudentClassFilter("Câu & 100%_", "ESSAY", "NOT_STARTED", 1),
                        2);

        assertEquals(
                "/ams/student/classes?classId=9&q=C%C3%A2u+%26+100%25_&responseType=ESSAY&status=NOT_STARTED&page=2",
                url);
    }

    @Test
    void linksOpenTheSpecificAttemptButNeverOpenCancelledWorkOrPretendResultsExist() {
        var submitted = row("PUBLISHED", "RELEASED", 44, NOW.minusSeconds(1), NOW.plusSeconds(1));

        assertEquals(
                "/ams/student/assignments?assignmentId=85&submissionId=44&classId=9",
                StudentClassView.assignmentUrl("/ams", 9, submitted));
        assertEquals("Xem trạng thái", StudentClassView.actionLabel(submitted));
        assertNull(
                StudentClassView.assignmentUrl(
                        "/ams", 9, row("CANCELLED", "SUBMITTED", 44, NOW, NOW)));
        assertEquals(
                "/ams/student/assignments?assignmentId=85&classId=9",
                StudentClassView.assignmentUrl(
                        "/ams", 9, row("PUBLISHED", "NOT_STARTED", 0, NOW, NOW.plusSeconds(1))));
    }

    @Test
    void availabilityIncludesOpeningAndExcludesDeadline() {
        assertEquals(
                "Chưa mở",
                StudentClassView.availability(
                        row("PUBLISHED", "NOT_STARTED", 0, NOW.plusSeconds(1), NOW.plusSeconds(60)),
                        NOW));
        assertEquals(
                "Đang mở",
                StudentClassView.availability(
                        row("PUBLISHED", "NOT_STARTED", 0, NOW, NOW.plusSeconds(60)), NOW));
        assertEquals(
                "Đã đóng",
                StudentClassView.availability(
                        row("PUBLISHED", "NOT_STARTED", 0, NOW.minusSeconds(60), NOW), NOW));
        assertEquals(
                "Đã hủy",
                StudentClassView.availability(
                        row("CANCELLED", "SUBMITTED", 1, NOW, NOW.plusSeconds(60)), NOW));
        assertEquals(
                "Chưa có lịch",
                StudentClassView.availability(row("PUBLISHED", "NOT_STARTED", 0, NOW, null), NOW));
        assertEquals("Chưa làm", StudentClassView.workLabel("NOT_STARTED"));
    }
}
