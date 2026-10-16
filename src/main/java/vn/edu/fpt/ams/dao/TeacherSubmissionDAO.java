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

/** Read-only Teacher access; the student's unfinished write workflow stays separate. */
public class TeacherSubmissionDAO {
    private static final String FROM_OWNED = """
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
    private static final String FROM_ROSTER = """
            FROM assignments a
            JOIN subject_classes c ON c.class_id = a.class_id
            JOIN users teacher ON teacher.user_id = c.teacher_id
            JOIN class_enrollments e ON e.class_id = c.class_id
            JOIN subject_registrations r ON r.registration_id = e.registration_id
                                       AND r.subject_term_id = c.subject_term_id
            JOIN users student ON student.user_id = r.student_id
            LEFT JOIN submissions sub ON sub.assignment_id = a.assignment_id
                                     AND sub.enrollment_id = e.enrollment_id
            LEFT JOIN users grader ON grader.user_id = sub.graded_by
            LEFT JOIN submission_content_snapshots snapshot ON snapshot.submission_id = sub.submission_id
            WHERE c.teacher_id = ? AND a.assignment_id = ?
              AND teacher.role = 'TEACHER' AND teacher.account_status = 'ACTIVE'
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
    private static final String SELECT_COLUMNS = """
            SELECT sub.*, student.user_id AS student_id, student.full_name AS student_name,
                   student.email AS student_email, grader.full_name AS grader_name,
                   snapshot.assignment_data AS assignment_snapshot, snapshot.questions_data AS questions_snapshot
            """;
    private static final String SELECT_OWNED = SELECT_COLUMNS + FROM_OWNED;

    public List<TeacherSubmissionRow> findForTeacher(Connection connection, long teacherId,
            long assignmentId, TeacherSubmissionFilter filter) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS).append(FROM_ROSTER).append(filterSql(filter));
        sql.append(" ORDER BY student.full_name, student.user_id, sub.attempt_number DESC,"
                + " sub.submission_id DESC LIMIT ? OFFSET ?");
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

    public long countForTeacher(Connection connection, long teacherId, long assignmentId,
            TeacherSubmissionFilter filter) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT count(*) " + FROM_ROSTER + filterSql(filter))) {
            bindFilter(statement, teacherId, assignmentId, filter);
            try (var rows = statement.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }

    public boolean hasSubmittedWork(Connection connection, long teacherId, long assignmentId)
            throws SQLException {
        try (var statement = connection.prepareStatement("SELECT 1 " + FROM_OWNED
                + " AND sub.submitted_at IS NOT NULL AND sub.status IN ('SUBMITTED','GRADED','RELEASED') LIMIT 1")) {
            statement.setLong(1, teacherId);
            statement.setLong(2, assignmentId);
            try (var rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    private static String filterSql(TeacherSubmissionFilter filter) {
        // strpos treats percent, underscore and SQL-looking text as literal characters.
        String sql = filter.search().isEmpty() ? "" : " AND (strpos(lower(student.full_name), lower(?)) > 0"
                + " OR strpos(lower(student.email), lower(?)) > 0)";
        if (filter.notStartedOnly()) return sql + " AND sub.submission_id IS NULL";
        return filter.status() == null ? sql : sql + " AND sub.status = ?";
    }

    private static int bindFilter(PreparedStatement statement, long teacherId, long assignmentId,
            TeacherSubmissionFilter filter) throws SQLException {
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

    public Optional<TeacherSubmissionRow> findDetailForTeacher(Connection connection,
            long teacherId, long assignmentId, long submissionId) throws SQLException {
        try (var statement = connection.prepareStatement(SELECT_OWNED + " AND sub.submission_id = ?")) {
            statement.setLong(1, teacherId);
            statement.setLong(2, assignmentId);
            statement.setLong(3, submissionId);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapRow(rows)) : Optional.empty();
            }
        }
    }

    private static TeacherSubmissionRow mapRow(ResultSet rows) throws SQLException {
        return new TeacherSubmissionRow(rows.getObject("submission_id") == null ? null : SubmissionDAO.mapSubmission(rows),
                rows.getLong("student_id"),
                rows.getString("student_name"), rows.getString("student_email"),
                rows.getString("grader_name"), rows.getString("assignment_snapshot"),
                rows.getString("questions_snapshot"));
    }
}
