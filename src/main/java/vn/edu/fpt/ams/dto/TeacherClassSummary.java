package vn.edu.fpt.ams.dto;

/** One class row on the teacher dashboard and class list. */
public record TeacherClassSummary(long classId, String classCode, String className,
                                  String subjectCode, String subjectName, String termCode,
                                  int capacity, long activeRoster, long pendingInvitations,
                                  long assignmentCount) {
    /** Số chỗ còn lại sau khi trừ sinh viên đang học và lời mời đang giữ chỗ. */
    public long availableSeats() {
        return Math.max(0, capacity - activeRoster - pendingInvitations);
    }
}
