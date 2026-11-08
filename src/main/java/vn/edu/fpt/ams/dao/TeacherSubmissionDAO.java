package vn.edu.fpt.ams.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import vn.edu.fpt.ams.dto.TeacherSubmissionFilter;
import vn.edu.fpt.ams.dto.TeacherSubmissionRow;
import vn.edu.fpt.ams.util.GroupAssignmentSchema;

/** Read-only Teacher access; the student's unfinished write workflow stays separate. */
public class TeacherSubmissionDAO {
    private static final String FROM_OWNED =
            """
            FROM submissions sub
            JOIN assignments a ON a.assignment_id = sub.assignment_id
            JOIN subject_classes c ON c.class_id = a.class_id
            JOIN users teacher ON teacher.user_id = c.teacher_id
            JOIN class_enrollments e ON e.enrollment_id = sub.enrollment_id
                                   AND e.class_id = c.class_id
            JOIN subject_registrations r ON r.registration_id = e.registration_id
                                       AND r.subject_term_id = c.subject_term_id
            JOIN users student ON student.user_id = r.student_id
            LEFT JOIN users grader ON grader.user_id = sub.graded_by
            LEFT JOIN submission_content_snapshots snapshot ON snapshot.submission_id = sub.submission_id
            WHERE c.teacher_id = ? AND a.assignment_id = ?
              AND teacher.role = 'TEACHER' AND teacher.account_status = 'ACTIVE'
            """;
    private static final String FROM_ROSTER =
            """
            FROM assignments a
            JOIN subject_classes c ON c.class_id = a.class_id
            JOIN users teacher ON teacher.user_id = c.teacher_id
            JOIN class_enrollments e ON e.class_id = c.class_id
            JOIN subject_registrations r ON r.registration_id = e.registration_id
                                       AND r.subject_term_id = c.subject_term_id
            JOIN users student ON student.user_id = r.student_id
            LEFT JOIN submissions sub ON sub.assignment_id=a.assignment_id
                AND EXISTS (SELECT 1 FROM class_enrollments work_e
                    JOIN subject_registrations work_r ON work_r.registration_id=work_e.registration_id
                    WHERE work_e.enrollment_id=sub.enrollment_id AND work_e.class_id=c.class_id
                      AND work_r.student_id=student.user_id AND work_r.subject_term_id=c.subject_term_id)
                AND NOT EXISTS (SELECT 1 FROM submissions newer
                    JOIN class_enrollments newer_e ON newer_e.enrollment_id=newer.enrollment_id
                    JOIN subject_registrations newer_r ON newer_r.registration_id=newer_e.registration_id
                    WHERE newer.assignment_id=a.assignment_id AND newer_e.class_id=c.class_id
                      AND newer_r.student_id=student.user_id
                      AND newer_r.subject_term_id=c.subject_term_id
                      AND (CASE WHEN newer.submitted_at IS NOT NULL AND newer.status IN ('SUBMITTED','GRADED','RELEASED')
                                THEN 1 ELSE 0 END >
                           CASE WHEN sub.submitted_at IS NOT NULL AND sub.status IN ('SUBMITTED','GRADED','RELEASED')
                                THEN 1 ELSE 0 END
                        OR (CASE WHEN newer.submitted_at IS NOT NULL AND newer.status IN ('SUBMITTED','GRADED','RELEASED')
                                 THEN 1 ELSE 0 END =
                            CASE WHEN sub.submitted_at IS NOT NULL AND sub.status IN ('SUBMITTED','GRADED','RELEASED')
                                 THEN 1 ELSE 0 END AND newer.submission_id>sub.submission_id)))
            LEFT JOIN users grader ON grader.user_id = sub.graded_by
            LEFT JOIN submission_content_snapshots snapshot ON snapshot.submission_id = sub.submission_id
            WHERE c.teacher_id = ? AND a.assignment_id = ?
              AND teacher.role = 'TEACHER' AND teacher.account_status = 'ACTIVE'
              AND e.enrollment_id = (
                  SELECT selected_e.enrollment_id FROM class_enrollments selected_e
                  JOIN subject_registrations selected_r ON selected_r.registration_id=selected_e.registration_id
                  WHERE selected_e.class_id=c.class_id AND selected_r.student_id=student.user_id
                    AND selected_r.subject_term_id=c.subject_term_id
                  ORDER BY (selected_e.status='ACTIVE' AND selected_r.status='ACTIVE') DESC,
                           selected_e.enrollment_id DESC LIMIT 1)
              AND (sub.submission_id IS NOT NULL
                   OR (e.status = 'ACTIVE' AND r.status = 'ACTIVE' AND student.role = 'STUDENT'
                       AND NOT EXISTS (
                           SELECT 1 FROM submissions previous_sub
                           JOIN class_enrollments previous_e
                             ON previous_e.enrollment_id = previous_sub.enrollment_id
                            AND previous_e.class_id = c.class_id
                           JOIN subject_registrations previous_r
                             ON previous_r.registration_id = previous_e.registration_id
                            AND previous_r.subject_term_id = c.subject_term_id
                           WHERE previous_sub.assignment_id = a.assignment_id
                             AND previous_r.student_id = student.user_id)))
            """;
    private static final String SELECT_COLUMNS =
            """
            SELECT sub.*, student.user_id AS student_id, student.full_name AS student_name,
                   student.email AS student_email, grader.full_name AS grader_name,
                   snapshot.assignment_data AS assignment_snapshot, snapshot.questions_data AS questions_snapshot
            """;
    private static final String SELECT_OWNED = SELECT_COLUMNS + FROM_OWNED;
    private static final String GROUP_COLUMNS = """
            SELECT sub.*,student.user_id AS student_id,gt.group_name AS student_name,
                (SELECT count(*)::text || ' thành viên' FROM assignment_group_members members
                    WHERE members.target_id=gt.target_id) AS student_email,
                grader.full_name AS grader_name,snapshot.assignment_data AS assignment_snapshot,
                snapshot.questions_data AS questions_snapshot
            """;
    private static final String FROM_GROUP_ROSTER = """
            FROM assignments a
            JOIN subject_classes c ON c.class_id=a.class_id
            JOIN users teacher ON teacher.user_id=c.teacher_id
            JOIN assignment_group_targets gt ON gt.assignment_id=a.assignment_id AND gt.frozen_at IS NOT NULL
            JOIN class_enrollments e ON e.enrollment_id=gt.owner_enrollment_id AND e.class_id=c.class_id
            JOIN subject_registrations r ON r.registration_id=e.registration_id AND r.subject_term_id=c.subject_term_id
            JOIN users student ON student.user_id=r.student_id
            LEFT JOIN LATERAL (
                SELECT work.* FROM submissions work WHERE work.assignment_group_target_id=gt.target_id
                ORDER BY (work.submitted_at IS NOT NULL AND work.status IN ('SUBMITTED','GRADED','RELEASED')) DESC,
                    work.submission_id DESC LIMIT 1
            ) sub ON TRUE
            LEFT JOIN users grader ON grader.user_id=sub.graded_by
            LEFT JOIN submission_content_snapshots snapshot ON snapshot.submission_id=sub.submission_id
            WHERE c.teacher_id=? AND a.assignment_id=?
                AND teacher.role='TEACHER' AND teacher.account_status='ACTIVE'
            """;
    private static final String FROM_GROUP_DETAIL = """
            FROM submissions sub JOIN assignments a ON a.assignment_id=sub.assignment_id
            JOIN subject_classes c ON c.class_id=a.class_id
            JOIN users teacher ON teacher.user_id=c.teacher_id
            JOIN assignment_group_targets gt ON gt.target_id=sub.assignment_group_target_id
                AND gt.assignment_id=a.assignment_id AND gt.class_id=c.class_id AND gt.frozen_at IS NOT NULL
            JOIN class_enrollments e ON e.enrollment_id=gt.owner_enrollment_id AND e.enrollment_id=sub.enrollment_id
                AND e.class_id=c.class_id
            JOIN subject_registrations r ON r.registration_id=e.registration_id AND r.subject_term_id=c.subject_term_id
            JOIN users student ON student.user_id=r.student_id
            LEFT JOIN users grader ON grader.user_id=sub.graded_by
            LEFT JOIN submission_content_snapshots snapshot ON snapshot.submission_id=sub.submission_id
            WHERE c.teacher_id=? AND a.assignment_id=?
                AND teacher.role='TEACHER' AND teacher.account_status='ACTIVE'
            """;

    public List<TeacherSubmissionRow> findForTeacher(
            Connection connection,
            long teacherId,
            long assignmentId,
            TeacherSubmissionFilter filter)
            throws SQLException {
        boolean group=GroupAssignmentSchema.isGroup(connection,assignmentId);
        StringBuilder sql = new StringBuilder(group?GROUP_COLUMNS:SELECT_COLUMNS)
                .append(group?FROM_GROUP_ROSTER:FROM_ROSTER)
                .append(group?groupFilterSql(filter):filterSql(filter));

        sql.append(
                " ORDER BY COALESCE(sub.submitted_at,sub.started_at) DESC NULLS LAST,"
                        + " sub.submission_id DESC NULLS LAST,"+(group?"gt.target_id":"student.user_id")+" DESC LIMIT ? OFFSET ?");

        try (var statement = connection.prepareStatement(sql.toString())) {
            int index = bindFilter(statement, teacherId, assignmentId, filter);

            statement.setInt(index++, TeacherSubmissionFilter.PAGE_SIZE);
            statement.setLong(index, filter.offset());

            try (var rows = statement.executeQuery()) {
                List<TeacherSubmissionRow> result = new ArrayList<>();

                while (rows.next()) result.add(mapRow(rows));

                return result;
            }
        }
    }

    public long countForTeacher(
            Connection connection,
            long teacherId,
            long assignmentId,
            TeacherSubmissionFilter filter)
            throws SQLException {
        boolean group=GroupAssignmentSchema.isGroup(connection,assignmentId);
        try (var statement = connection.prepareStatement("SELECT count(*) "
                +(group?FROM_GROUP_ROSTER:FROM_ROSTER)+(group?groupFilterSql(filter):filterSql(filter)))) {
            bindFilter(statement, teacherId, assignmentId, filter);

            try (var rows = statement.executeQuery()) {
                rows.next();

                return rows.getLong(1);
            }
        }
    }

    public boolean hasSubmittedWork(Connection connection, long teacherId, long assignmentId)
            throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        "SELECT 1 "
                                + FROM_OWNED
                                + " AND sub.submitted_at IS NOT NULL AND sub.status IN ('SUBMITTED','GRADED','RELEASED') LIMIT 1")) {
            statement.setLong(1, teacherId);
            statement.setLong(2, assignmentId);

            try (var rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    /** Latest attempt per student, independent of the current page and filters. */
    public java.util.Map<String, Long> studentCounts(
            Connection connection, long teacherId, long assignmentId) throws SQLException {
        boolean group=GroupAssignmentSchema.isGroup(connection,assignmentId);
        String sql = group?"SELECT COALESCE(sub.status,'NOT_STARTED'),count(*) "+FROM_GROUP_ROSTER
                +" GROUP BY COALESCE(sub.status,'NOT_STARTED')":
                "SELECT work_status,count(*) FROM (SELECT COALESCE(sub.status,'NOT_STARTED') work_status,"
                        + " row_number() OVER(PARTITION BY student.user_id ORDER BY sub.attempt_number DESC NULLS LAST,sub.submission_id DESC NULLS LAST) rn "
                        + FROM_ROSTER
                        + ") roster WHERE rn=1 GROUP BY work_status";

        try (var p = connection.prepareStatement(sql)) {
            p.setLong(1, teacherId);
            p.setLong(2, assignmentId);

            try (var r = p.executeQuery()) {
                var result = new java.util.LinkedHashMap<String, Long>();

                while (r.next()) result.put(r.getString(1), r.getLong(2));

                return result;
            }
        }
    }

    private static String groupFilterSql(TeacherSubmissionFilter filter) {
        String sql=filter.search().isEmpty()?"":" AND (strpos(lower(gt.group_name),lower(?))>0"
                +" OR EXISTS(SELECT 1 FROM assignment_group_members member WHERE member.target_id=gt.target_id"
                +" AND strpos(lower(member.student_name),lower(?))>0))";
        if(filter.notStartedOnly()) return sql+" AND sub.submission_id IS NULL";
        return filter.status()==null?sql:sql+" AND sub.status=?";
    }

    private static String filterSql(TeacherSubmissionFilter filter) {
        // strpos treats percent, underscore and SQL-looking text as literal characters.
        String sql =
                filter.search().isEmpty()
                        ? ""
                        : " AND (strpos(lower(student.full_name), lower(?)) > 0"
                                + " OR strpos(lower(student.email), lower(?)) > 0)";

        if (filter.notStartedOnly()) return sql + " AND sub.submission_id IS NULL";

        return filter.status() == null ? sql : sql + " AND sub.status = ?";
    }

    private static int bindFilter(
            PreparedStatement statement,
            long teacherId,
            long assignmentId,
            TeacherSubmissionFilter filter)
            throws SQLException {
        int index = 1;

        statement.setLong(index++, teacherId);
        statement.setLong(index++, assignmentId);

        if (!filter.search().isEmpty()) {
            statement.setString(index++, filter.search());
            statement.setString(index++, filter.search());
        }

        if (filter.status() != null) statement.setString(index++, filter.status().name());

        return index;
    }

    public Optional<TeacherSubmissionRow> findDetailForTeacher(
            Connection connection, long teacherId, long assignmentId, long submissionId)
            throws SQLException {
        boolean group=GroupAssignmentSchema.isGroup(connection,assignmentId);
        try (var statement =
                connection.prepareStatement(
                        (group?GROUP_COLUMNS+FROM_GROUP_DETAIL:SELECT_OWNED)
                                + " AND sub.submission_id = ?"
                                + " AND sub.submitted_at IS NOT NULL AND sub.status IN ('SUBMITTED','GRADED','RELEASED')")) {
            statement.setLong(1, teacherId);
            statement.setLong(2, assignmentId);
            statement.setLong(3, submissionId);

            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapRow(rows)) : Optional.empty();
            }
        }
    }

    private static TeacherSubmissionRow mapRow(ResultSet rows) throws SQLException {
        return new TeacherSubmissionRow(
                rows.getObject("submission_id") == null ? null : SubmissionDAO.mapSubmission(rows),
                rows.getLong("student_id"),
                rows.getString("student_name"),
                rows.getString("student_email"),
                rows.getString("grader_name"),
                rows.getString("assignment_snapshot"),
                rows.getString("questions_snapshot"));
    }
}
