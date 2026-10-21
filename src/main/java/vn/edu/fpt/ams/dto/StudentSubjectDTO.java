package vn.edu.fpt.ams.dto;

import java.time.Instant;
import java.util.List;

/** Student views contain only public offering information and the caller's own placement. */
public final class StudentSubjectDTO {
    private StudentSubjectDTO() {}
    /** termActive is effective eligibility: enabled and not ended at the database snapshot time. */
    public record Offering(long id, String subjectCode, String subjectName, String description,
            String subjectStatus, String termCode, String termName, boolean termActive,
            boolean currentTerm, boolean enabled, Instant opensAt, Instant closesAt,
            String registrationStatus, String classCode, String className, String teacherName,
            String classStatus, long availableSeats, Instant now) {
        public boolean enrolled() { return "ACTIVE".equals(registrationStatus); }
        public boolean open() { return "ACTIVE".equals(subjectStatus) && termActive
                && enabled && !now.isBefore(opensAt) && now.isBefore(closesAt); }
        public boolean canRegister() { return open() && !enrolled() && availableSeats > 0; }
        public String stateLabel() {
            if (enrolled()) return "Đã đăng ký";
            if (!"ACTIVE".equals(subjectStatus) || !termActive || !enabled) return "Không mở đăng ký";
            if (now.isBefore(opensAt)) return "Chưa mở đăng ký";
            if (!now.isBefore(closesAt)) return "Đã đóng đăng ký";
            return availableSeats > 0 ? "Đang mở đăng ký" : "Chưa có lớp còn chỗ phù hợp";
        }
    }
    public record Page(List<Offering> offerings, String view, String search) {}
}
