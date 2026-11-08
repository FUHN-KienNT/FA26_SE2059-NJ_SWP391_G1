package vn.edu.fpt.ams.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vn.edu.fpt.ams.dto.AssignmentCreatePage;
import vn.edu.fpt.ams.dto.AssignmentGroupOption;
import vn.edu.fpt.ams.dto.AssignmentGroupTarget;
import vn.edu.fpt.ams.dto.GroupAssignmentConfig;
import vn.edu.fpt.ams.util.AssignmentDraftValidator.ValidationException;
import vn.edu.fpt.ams.util.AssignmentEditConflictException;
import vn.edu.fpt.ams.util.GroupAssignmentSchema;
import vn.edu.fpt.ams.util.Jdbc;

/** Audience persistence participates in the AssignmentService's assignment-leading transaction. */
public class GroupAssignmentDAO {
    public GroupAssignmentConfig read(Connection c, long assignmentId) throws SQLException {
        if (!GroupAssignmentSchema.installed(c)) return GroupAssignmentConfig.individual();
        try (var p = Jdbc.statement(c, "SELECT * FROM assignment_group_settings WHERE assignment_id=?", assignmentId);
                var r = p.executeQuery()) {
            if (!r.next()) return GroupAssignmentConfig.individual();
            List<Long> ids = new ArrayList<>();
            if ("SELECTED".equals(r.getString("target_mode"))) {
                try (var targets = Jdbc.statement(c,
                        "SELECT group_id FROM assignment_group_targets WHERE assignment_id=? ORDER BY group_id", assignmentId);
                        var rows = targets.executeQuery()) {
                    while (rows.next()) ids.add(rows.getLong(1));
                }
            }
            return new GroupAssignmentConfig(r.getString("delivery_mode"), r.getString("target_mode"),
                    r.getBigDecimal("teacher_weight"), r.getBigDecimal("peer_weight"), r.getLong("revision"), ids);
        }
    }

    public AssignmentCreatePage enrich(Connection c, long teacherId, AssignmentCreatePage context)
            throws SQLException {
        return new AssignmentCreatePage(context.classId(), context.subjectId(), context.classCode(),
                context.className(), context.subjectCode(), context.subjectName(), context.templates(),
                context.eligibleClasses(), options(c, teacherId, context.subjectId()), GroupAssignmentSchema.installed(c));
    }

    public List<AssignmentGroupOption> options(Connection c, long teacherId, long subjectId) throws SQLException {
        if (!GroupAssignmentSchema.installed(c)) return List.of();
        try (var p = Jdbc.statement(c, """
                SELECT g.group_id,g.class_id,c.class_code,g.group_name,
                    (SELECT count(*) FROM project_group_members m
                     JOIN class_enrollments e ON e.enrollment_id=m.enrollment_id AND e.class_id=g.class_id AND e.status='ACTIVE'
                     JOIN subject_registrations r ON r.registration_id=e.registration_id
                         AND r.student_id=m.student_id AND r.subject_term_id=c.subject_term_id AND r.status='ACTIVE'
                     JOIN users u ON u.user_id=m.student_id AND u.role='STUDENT' AND u.account_status='ACTIVE'
                     WHERE m.group_id=g.group_id AND m.class_id=g.class_id AND m.left_at IS NULL) AS member_count
                FROM project_groups g JOIN subject_classes c ON c.class_id=g.class_id
                JOIN subject_terms st ON st.subject_term_id=c.subject_term_id
                WHERE c.teacher_id=? AND st.subject_id=? AND c.status='ACTIVE' AND g.status='ACTIVE'
                ORDER BY c.class_code,g.group_name,g.group_id
                """, teacherId, subjectId); var r = p.executeQuery()) {
            List<AssignmentGroupOption> result = new ArrayList<>();
            while (r.next()) result.add(new AssignmentGroupOption(r.getLong(1), r.getLong(2),
                    r.getString(3), r.getString(4), r.getLong(5)));
            return List.copyOf(result);
        }
    }

    /** Validate the entire multi-class selection before creating any per-class copy. */
    public Map<Long, List<Long>> validateSelection(Connection c, long teacherId, long subjectId,
            List<Long> classIds, GroupAssignmentConfig config) throws SQLException {
        if (!config.group()) return Map.of();
        requireInstalled(c);
        Map<Long, List<Long>> result = new HashMap<>();
        for (long classId : classIds) {
            if (Jdbc.scalar(c, "SELECT count(*) FROM subject_classes WHERE class_id=? AND teacher_id=? AND status='ACTIVE'",
                    classId, teacherId) != 1)
                throw invalid("selectedGroupIds", "Chỉ giao bài nhóm cho lớp đang hoạt động mà bạn phụ trách.");
            result.put(classId, new ArrayList<>());
        }
        if ("SELECTED".equals(config.targetMode())) {
            var choices = options(c, teacherId, subjectId);
            for (long id : config.selectedGroupIds()) {
                var option = choices.stream().filter(g -> g.groupId() == id).findFirst()
                        .orElseThrow(() -> invalid("selectedGroupIds", "Nhóm đã chọn không tồn tại, ngừng hoạt động hoặc không thuộc lớp bạn phụ trách."));
                if (!result.containsKey(option.classId()))
                    throw invalid("selectedGroupIds", "Nhóm đã chọn phải thuộc các lớp đang được giao bài.");
                if (option.memberCount() == 0)
                    throw invalid("selectedGroupIds", "Nhóm " + option.name() + " chưa có thành viên đang học hợp lệ.");
                result.get(option.classId()).add(id);
            }
            if (result.values().stream().anyMatch(List::isEmpty))
                throw invalid("selectedGroupIds", "Chọn ít nhất một nhóm cho mỗi lớp được giao bài.");
        }
        return result;
    }

    public void assertRevision(Connection c, long assignmentId, long expected) throws SQLException {
        if (read(c, assignmentId).revision() != expected)
            throw new AssignmentEditConflictException("Cấu hình giao bài theo nhóm đã thay đổi. Hãy tải lại bản nháp trước khi lưu.");
    }

    /** Settings and draft recipients are saved with the same commit as the Assignment itself. */
    public void saveDraft(Connection c, long assignmentId, GroupAssignmentConfig config) throws SQLException {
        if (!GroupAssignmentSchema.installed(c)) {
            if (config.group()) requireInstalled(c);
            return;
        }
        var stored = read(c, assignmentId);
        assertRevision(c, assignmentId, config.revision());
        try (var p = Jdbc.statement(c, "SELECT status,published_at FROM assignments WHERE assignment_id=?", assignmentId);
                var r = p.executeQuery()) {
            if (!r.next() || !"DRAFT".equals(r.getString(1))
                    || (r.getObject(2) != null && (stored.group() || config.group())))
                throw new AssignmentEditConflictException("Chỉ thay đổi cấu hình nhóm trước lần công bố đầu tiên.");
        }
        if (Jdbc.scalar(c, "SELECT count(*) FROM assignment_group_targets WHERE assignment_id=? AND frozen_at IS NOT NULL", assignmentId) > 0)
            throw new AssignmentEditConflictException("Danh sách nhóm và thành viên đã được chốt, không thể ghi đè.");
        int updated = Jdbc.update(c, """
                UPDATE assignment_group_settings SET delivery_mode=?,target_mode=?,teacher_weight=?,peer_weight=?,revision=revision+1
                WHERE assignment_id=? AND revision=?
                """, config.deliveryMode(), config.targetMode(), config.teacherWeight(), config.peerWeight(), assignmentId, config.revision());
        if (updated == 0) {
            if (config.revision() != 0 || Jdbc.scalar(c,
                    "SELECT count(*) FROM assignment_group_settings WHERE assignment_id=?", assignmentId) > 0)
                throw new AssignmentEditConflictException("Cấu hình nhóm đã thay đổi. Hãy tải lại bản nháp.");
            Jdbc.update(c, """
                    INSERT INTO assignment_group_settings(assignment_id,delivery_mode,target_mode,teacher_weight,peer_weight)
                    VALUES(?,?,?,?,?)
                    """, assignmentId, config.deliveryMode(), config.targetMode(), config.teacherWeight(), config.peerWeight());
        }
        Jdbc.update(c, "DELETE FROM assignment_group_targets WHERE assignment_id=?", assignmentId);
        if (config.group() && "SELECTED".equals(config.targetMode())) {
            for (long groupId : config.selectedGroupIds()) {
                int inserted = Jdbc.update(c, """
                        INSERT INTO assignment_group_targets(assignment_id,class_id,group_id,group_name)
                        SELECT a.assignment_id,a.class_id,g.group_id,g.group_name
                        FROM assignments a JOIN project_groups g ON g.class_id=a.class_id
                        WHERE a.assignment_id=? AND g.group_id=? AND g.status='ACTIVE'
                        """, assignmentId, groupId);
                if (inserted != 1) throw invalid("selectedGroupIds", "Nhóm đã chọn không thuộc lớp của bài tập.");
            }
        }
    }

    /** Freeze all recipients while the owning class is locked against group membership writes. */
    public void freezeTargets(Connection c, long assignmentId, Instant now) throws SQLException {
        GroupAssignmentConfig config = read(c, assignmentId);
        if (!config.group()) return;
        requireInstalled(c);
        // TeacherGroupService takes FOR UPDATE on this class for membership writes.
        // The caller already leads with the Assignment lock; keep that lock ordering.
        try (var p = Jdbc.statement(c, """
                SELECT cl.class_id FROM assignments a JOIN subject_classes cl ON cl.class_id=a.class_id
                WHERE a.assignment_id=? AND a.status='DRAFT' AND a.published_at IS NULL AND cl.status='ACTIVE'
                FOR SHARE OF cl
                """, assignmentId); var r = p.executeQuery()) {
            if (!r.next()) throw invalid("selectedGroupIds", "Chỉ chốt nhóm lần đầu khi công bố bản nháp trong lớp đang hoạt động.");
        }
        if (Jdbc.scalar(c, "SELECT count(*) FROM assignment_group_targets WHERE assignment_id=? AND frozen_at IS NOT NULL", assignmentId) > 0)
            throw new AssignmentEditConflictException("Danh sách nhóm của bài tập đã được chốt.");
        if ("ALL".equals(config.targetMode())) {
            Jdbc.update(c, """
                    INSERT INTO assignment_group_targets(assignment_id,class_id,group_id,group_name)
                    SELECT a.assignment_id,a.class_id,g.group_id,g.group_name
                    FROM assignments a JOIN project_groups g ON g.class_id=a.class_id
                    JOIN subject_classes cl ON cl.class_id=a.class_id
                    WHERE a.assignment_id=? AND cl.status='ACTIVE' AND g.status='ACTIVE'
                    ORDER BY g.group_id
                    """, assignmentId);
        }
        List<Long> targetIds = new ArrayList<>();
        try (var p = Jdbc.statement(c,
                "SELECT target_id FROM assignment_group_targets WHERE assignment_id=? ORDER BY group_id", assignmentId);
                var r = p.executeQuery()) {
            while (r.next()) targetIds.add(r.getLong(1));
        }
        if (targetIds.isEmpty()) throw invalid("selectedGroupIds", "Lớp chưa có nhóm đang hoạt động để công bố bài tập.");
        Set<Long> assignedStudents = new HashSet<>();
        for (long targetId : targetIds) {
            if (Jdbc.scalar(c, """
                    SELECT count(*) FROM assignment_group_targets t
                    JOIN project_groups g ON g.group_id=t.group_id AND g.class_id=t.class_id
                    JOIN subject_classes cl ON cl.class_id=t.class_id
                    WHERE t.target_id=? AND g.status='ACTIVE' AND cl.status='ACTIVE'
                    """, targetId) != 1)
                throw invalid("selectedGroupIds", "Nhóm hoặc lớp đã ngừng hoạt động. Hãy chọn lại nhóm trước khi công bố.");
            long owner = 0;
            int count = 0;
            try (var p = Jdbc.statement(c, """
                    SELECT m.student_id,m.enrollment_id,u.full_name
                    FROM assignment_group_targets t
                    JOIN subject_classes cl ON cl.class_id=t.class_id
                    JOIN project_group_members m ON m.group_id=t.group_id AND m.class_id=t.class_id AND m.left_at IS NULL
                    JOIN class_enrollments e ON e.enrollment_id=m.enrollment_id AND e.class_id=t.class_id AND e.status='ACTIVE'
                    JOIN subject_registrations r ON r.registration_id=e.registration_id
                        AND r.student_id=m.student_id AND r.subject_term_id=cl.subject_term_id AND r.status='ACTIVE'
                    JOIN users u ON u.user_id=m.student_id AND u.role='STUDENT' AND u.account_status='ACTIVE'
                    WHERE t.target_id=? ORDER BY m.enrollment_id,m.student_id
                    """, targetId); var r = p.executeQuery()) {
                while (r.next()) {
                    long studentId = r.getLong(1), enrollmentId = r.getLong(2);
                    if (!assignedStudents.add(studentId))
                        throw invalid("selectedGroupIds", "Một sinh viên đang có mặt ở nhiều nhóm nhận cùng bài tập.");
                    if (owner == 0) owner = enrollmentId;
                    Jdbc.update(c, "INSERT INTO assignment_group_members(target_id,student_id,enrollment_id,student_name) VALUES(?,?,?,?)",
                            targetId, studentId, enrollmentId, r.getString(3));
                    count++;
                }
            }
            if (count == 0) throw invalid("selectedGroupIds", "Mỗi nhóm nhận bài phải có ít nhất một thành viên đang học hợp lệ.");
            Jdbc.update(c, """
                    UPDATE assignment_group_targets t SET owner_enrollment_id=?,frozen_at=?,
                        group_name=(SELECT g.group_name FROM project_groups g WHERE g.group_id=t.group_id)
                    WHERE t.target_id=? AND frozen_at IS NULL
                    """, owner, now, targetId);
        }
    }

    public List<AssignmentGroupTarget> targets(Connection c, long assignmentId) throws SQLException {
        if (!GroupAssignmentSchema.installed(c)) return List.of();
        try (var p = Jdbc.statement(c, """
                SELECT t.target_id,t.group_id,t.group_name,t.frozen_at,
                    (SELECT count(*) FROM assignment_group_members m WHERE m.target_id=t.target_id) AS member_count
                FROM assignment_group_targets t WHERE t.assignment_id=? ORDER BY t.group_name,t.group_id
                """, assignmentId); var r = p.executeQuery()) {
            List<AssignmentGroupTarget> result = new ArrayList<>();
            while (r.next()) result.add(new AssignmentGroupTarget(r.getLong(1), r.getLong(2), r.getString(3),
                    r.getLong(5), r.getObject(4) != null));
            return List.copyOf(result);
        }
    }

    public Set<Long> groupAssignmentIds(Connection c, List<Long> assignmentIds) throws SQLException {
        if (assignmentIds.isEmpty() || !GroupAssignmentSchema.installed(c)) return Set.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(assignmentIds.size(), "?"));
        try (var p = Jdbc.statement(c, "SELECT assignment_id FROM assignment_group_settings WHERE delivery_mode='GROUP' AND assignment_id IN ("
                + placeholders + ")", assignmentIds.toArray()); var r = p.executeQuery()) {
            Set<Long> result = new HashSet<>();
            while (r.next()) result.add(r.getLong(1));
            return Set.copyOf(result);
        }
    }

    private static void requireInstalled(Connection c) throws SQLException {
        if (!GroupAssignmentSchema.installed(c))
            throw invalid("deliveryMode", "Chưa cài cấu trúc giao bài nhóm. Hãy chạy migration 009 trước khi dùng chức năng này.");
    }

    private static ValidationException invalid(String field, String message) {
        return new ValidationException(Map.of(field, message));
    }
}
