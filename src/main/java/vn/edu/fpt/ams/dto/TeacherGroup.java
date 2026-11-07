package vn.edu.fpt.ams.dto;

import java.util.List;

/** Teacher-owned group with its current and previous membership episodes. */
public record TeacherGroup(long groupId, long classId, String name, String description,
                           String status, Long leaderStudentId, List<Member> members) {
    /** One membership episode, retaining its enrollment context. */
    public record Member(long memberId, long studentId, String name, String email,
                         boolean current, String joinedAt, String leftAt) {}

    /** Count the members whose episode and enrollment are still active. */
    public long memberCount() {
        return members.stream().filter(Member::current).count();
    }
}
