package vn.edu.fpt.ams.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import vn.edu.fpt.ams.dto.EnrollmentDTO;
import vn.edu.fpt.ams.model.EnrollmentStatus;
import java.util.List;
import vn.edu.fpt.ams.dto.TeacherGroup;
import vn.edu.fpt.ams.util.DBConnection;
import vn.edu.fpt.ams.util.Jdbc;

/** Serialize group writes on the assigned class and preserve membership history. */
public class TeacherGroupService {
    private final Jdbc.Connections connections;

    /** Use the configured application database. */
    public TeacherGroupService() {
        this(DBConnection::getConnection);
    }

    /** Supply an isolated database for transaction tests. */
    public TeacherGroupService(Jdbc.Connections connections) {
        this.connections = connections;
    }

    /** Read groups only after checking the current active Teacher's assignment. */
    public List<TeacherGroup> list(long teacherId, long classId) {
        return Jdbc.transaction(connections, c -> {
            requireClass(c, teacherId, classId, false);
            return groups(c, classId);
        });
    }

    /** Resolve a group within the explicitly selected assigned class. */
    public TeacherGroup detail(long teacherId, long classId, long groupId) {
        return list(teacherId, classId).stream().filter(g -> g.groupId() == groupId)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm trong lớp đã chọn."));
    }

    /** List eligible Students without a current membership in the selected class. */
    public List<EnrollmentDTO> unassignedStudents(long teacherId, long classId) {
        return Jdbc.transaction(connections, c -> {
            requireClass(c, teacherId, classId, false);
            return unassignedStudents(c, classId);
        });
    }

    /** Select actively enrolled Students without a current group membership. */
    private List<EnrollmentDTO> unassignedStudents(Connection c, long classId) throws SQLException {
        var students = new ArrayList<EnrollmentDTO>();
        try (var p = Jdbc.statement(c, """
                SELECT e.enrollment_id, e.registration_id, e.class_id,
                       u.user_id, u.full_name, u.email
                FROM class_enrollments e
                JOIN subject_registrations r ON r.registration_id = e.registration_id
                JOIN users u ON u.user_id = r.student_id
                WHERE e.class_id = ? AND e.status = 'ACTIVE' AND r.status = 'ACTIVE'
                  AND u.role = 'STUDENT' AND u.account_status = 'ACTIVE'
                  AND NOT EXISTS (
                      SELECT 1 FROM project_group_members m
                      WHERE m.class_id = e.class_id AND m.student_id = u.user_id
                        AND m.enrollment_id = e.enrollment_id AND m.left_at IS NULL)
                ORDER BY e.enrollment_id
                """, classId); var r = p.executeQuery()) {
            while (r.next()) {
                students.add(new EnrollmentDTO(r.getLong("enrollment_id"),
                        r.getLong("registration_id"), r.getLong("class_id"),
                        r.getLong("user_id"), r.getString("full_name"),
                        r.getString("email"), EnrollmentStatus.ACTIVE));
            }
        }
        return students;
    }
    /** Create or edit group metadata with a unique class-scoped name. */
    public long save(long teacherId, long classId, Long groupId, String name, String description) {
        String cleanName = text(name, 100, true);
        String cleanDescription = text(description, 2000, false);
        try {
            return Jdbc.transaction(connections, c -> {
                requireClass(c, teacherId, classId, true);
                if (Jdbc.scalar(c, """
                        SELECT count(*) FROM project_groups
                        WHERE class_id = ? AND lower(trim(group_name)) = lower(?)
                        AND group_id <> ?
                        """, classId, cleanName, groupId == null ? 0 : groupId) > 0) {
                    throw new IllegalArgumentException("Tên nhóm đã tồn tại trong lớp.");
                }
                if (groupId == null) {
                    return Jdbc.insert(c, """
                            INSERT INTO project_groups(class_id, group_name, description)
                            VALUES (?, ?, ?)
                            """, classId, cleanName, cleanDescription);
                }
                requireGroup(c, classId, groupId, false);
                Jdbc.update(c, "UPDATE project_groups SET group_name = ?, description = ? WHERE group_id = ?",
                        cleanName, cleanDescription, groupId);
                return groupId;
            });
        } catch (IllegalArgumentException ex) {
            if (ex.getCause() instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                throw new IllegalArgumentException("Tên nhóm đã tồn tại trong lớp.", ex);
            }
            throw ex;
        }
    }

    /** Add one actively enrolled Student, preventing duplicate active class memberships. */
    public void addMember(long teacherId, long classId, long groupId, long studentId) {
        Jdbc.transaction(connections, c -> {
            requireClass(c, teacherId, classId, true);
            requireGroup(c, classId, groupId, true);
            long enrollmentId = Jdbc.scalar(c, """
                    SELECT e.enrollment_id FROM class_enrollments e
                    JOIN subject_registrations r ON r.registration_id = e.registration_id
                    JOIN users u ON u.user_id = r.student_id
                    WHERE e.class_id = ? AND r.student_id = ?
                      AND e.status = 'ACTIVE' AND r.status = 'ACTIVE'
                      AND u.role = 'STUDENT' AND u.account_status = 'ACTIVE'
                    """, classId, studentId);
            if (enrollmentId == 0) {
                throw new IllegalArgumentException("Chỉ được thêm sinh viên đang học và có tài khoản hoạt động trong lớp.");
            }
            // Close previous enrollment episodes that no longer grant membership.
            Jdbc.update(c, """
                    UPDATE project_group_members SET left_at = CURRENT_TIMESTAMP
                    WHERE class_id = ? AND student_id = ? AND left_at IS NULL AND enrollment_id <> ?
                    """, classId, studentId, enrollmentId);
            if (Jdbc.scalar(c, """
                    SELECT count(*) FROM project_group_members
                    WHERE class_id = ? AND student_id = ? AND left_at IS NULL
                    """, classId, studentId) > 0) {
                throw new IllegalArgumentException("Sinh viên đã thuộc một nhóm trong lớp. Hãy gỡ khỏi nhóm cũ trước.");
            }
            Jdbc.insert(c, """
                    INSERT INTO project_group_members(group_id, class_id, student_id, enrollment_id)
                    VALUES (?, ?, ?, ?)
                    """, groupId, classId, studentId, enrollmentId);
            return null;
        });
    }

    /** Close one membership and clear leadership if that Student leaves the group. */
    public void removeMember(long teacherId, long classId, long groupId, long studentId) {
        Jdbc.transaction(connections, c -> {
            requireClass(c, teacherId, classId, true);
            requireGroup(c, classId, groupId, true);
            int changed = Jdbc.update(c, """
                    UPDATE project_group_members SET left_at = CURRENT_TIMESTAMP
                    WHERE group_id = ? AND student_id = ? AND left_at IS NULL
                    """, groupId, studentId);
            if (changed == 0) throw new IllegalArgumentException("Sinh viên không còn là thành viên của nhóm.");
            Jdbc.update(c, """
                    UPDATE project_groups SET leader_student_id = NULL
                    WHERE group_id = ? AND leader_student_id = ?
                    """, groupId, studentId);
            return null;
        });
    }

    /** Select a current member as leader, or explicitly clear the assignment. */
    public void setLeader(long teacherId, long classId, long groupId, Long studentId) {
        Jdbc.transaction(connections, c -> {
            requireClass(c, teacherId, classId, true);
            requireGroup(c, classId, groupId, true);
            if (studentId != null && Jdbc.scalar(c, """
                    SELECT count(*) FROM project_group_members m
                    JOIN class_enrollments e ON e.enrollment_id = m.enrollment_id
                    JOIN subject_registrations r ON r.registration_id = e.registration_id
                    JOIN users u ON u.user_id = m.student_id
                    WHERE m.group_id = ? AND m.student_id = ? AND m.left_at IS NULL
                      AND e.status = 'ACTIVE' AND r.status = 'ACTIVE'
                      AND u.role = 'STUDENT' AND u.account_status = 'ACTIVE'
                    """, groupId, studentId) == 0) {
                throw new IllegalArgumentException("Trưởng nhóm phải là thành viên đang hoạt động của nhóm.");
            }
            Jdbc.update(c, "UPDATE project_groups SET leader_student_id = ? WHERE group_id = ?", studentId, groupId);
            return null;
        });
    }

    /** Retire a group and release memberships, or reopen it with its history preserved. */
    public void changeStatus(long teacherId, long classId, long groupId, String status) {
        if (!List.of("ACTIVE", "INACTIVE").contains(status == null ? "" : status)) {
            throw new IllegalArgumentException("Trạng thái nhóm không hợp lệ.");
        }
        Jdbc.transaction(connections, c -> {
            requireClass(c, teacherId, classId, true);
            requireGroup(c, classId, groupId, false);
            if (Jdbc.scalar(c, "SELECT count(*) FROM project_groups WHERE group_id = ? AND status = ?", groupId, status) > 0) {
                return null;
            }
            if ("INACTIVE".equals(status)) {
                Jdbc.update(c, """
                        UPDATE project_group_members SET left_at = CURRENT_TIMESTAMP
                        WHERE group_id = ? AND left_at IS NULL
                        """, groupId);
            }
            Jdbc.update(c, "UPDATE project_groups SET status = ?, leader_student_id = NULL WHERE group_id = ?",
                    status, groupId);
            return null;
        });
    }

    /** Check actor state and current assignment, locking class writes in a fixed order. */
    private void requireClass(Connection c, long teacherId, long classId, boolean write) throws SQLException {
        try (var p = Jdbc.statement(c, """
                SELECT c.status FROM subject_classes c
                JOIN users u ON u.user_id = c.teacher_id
                WHERE c.class_id = ? AND c.teacher_id = ?
                  AND u.role = 'TEACHER' AND u.account_status = 'ACTIVE'
                """ + (write ? " FOR UPDATE OF c" : ""), classId, teacherId);
             var r = p.executeQuery()) {
            if (!r.next()) throw new SecurityException("Lớp này không thuộc phân công của giảng viên hiện tại.");
            if (write && !"ACTIVE".equals(r.getString(1))) {
                throw new IllegalArgumentException("Chỉ được quản lý nhóm trong lớp đang hoạt động.");
            }
        }
    }

    /** Enforce group ownership and activity after locking the parent class. */
    private void requireGroup(Connection c, long classId, long groupId, boolean active) throws SQLException {
        try (var p = Jdbc.statement(c, "SELECT status FROM project_groups WHERE class_id = ? AND group_id = ?",
                classId, groupId); var r = p.executeQuery()) {
            if (!r.next()) throw new IllegalArgumentException("Không tìm thấy nhóm trong lớp đã chọn.");
            if (active && !"ACTIVE".equals(r.getString(1))) {
                throw new IllegalArgumentException("Nhóm đã ngừng hoạt động.");
            }
        }
    }

    /** Read each group and retain membership episodes for the detail history. */
    private List<TeacherGroup> groups(Connection c, long classId) throws SQLException {
        List<TeacherGroup> result = new ArrayList<>();
        try (var p = Jdbc.statement(c, """
                SELECT * FROM project_groups WHERE class_id = ? ORDER BY lower(group_name), group_id
                """, classId); var r = p.executeQuery()) {
            while (r.next()) {
                long groupId = r.getLong("group_id");
                Long leader = r.getObject("leader_student_id", Long.class);
                var members = members(c, groupId);
                if (leader != null) {
                    long leaderId = leader;
                    if (members.stream().noneMatch(m -> m.studentId() == leaderId && m.current())) leader = null;
                }
                result.add(new TeacherGroup(groupId, classId, r.getString("group_name"),
                        r.getString("description"), r.getString("status"), leader, List.copyOf(members)));
            }
        }
        return result;
    }

    /** Read membership history with current eligibility derived from enrollment and registration. */
    private List<TeacherGroup.Member> members(Connection c, long groupId) throws SQLException {
        List<TeacherGroup.Member> result = new ArrayList<>();
        try (var p = Jdbc.statement(c, """
                SELECT m.*, u.full_name, u.email,
                       CASE WHEN m.left_at IS NULL AND e.status = 'ACTIVE' AND r.status = 'ACTIVE'
                            AND u.role = 'STUDENT' AND u.account_status = 'ACTIVE' THEN TRUE ELSE FALSE END AS current_member
                FROM project_group_members m
                JOIN users u ON u.user_id = m.student_id
                JOIN class_enrollments e ON e.enrollment_id = m.enrollment_id
                JOIN subject_registrations r ON r.registration_id = e.registration_id
                WHERE m.group_id = ? ORDER BY m.member_id DESC
                """, groupId); var r = p.executeQuery()) {
            while (r.next()) result.add(new TeacherGroup.Member(r.getLong("member_id"),
                    r.getLong("student_id"), r.getString("full_name"), r.getString("email"),
                    r.getBoolean("current_member"), r.getString("joined_at"), r.getString("left_at")));
        }
        return result;
    }

    /** Normalize bounded text while preserving human-readable descriptions. */
    private static String text(String value, int maximum, boolean required) {
        String clean = value == null ? "" : value.trim();
        if ((required && clean.isEmpty()) || clean.length() > maximum) {
            throw new IllegalArgumentException("Tên nhóm bắt buộc và tối đa 100 ký tự. Mô tả tối đa 2000 ký tự.");
        }
        return clean;
    }
}
