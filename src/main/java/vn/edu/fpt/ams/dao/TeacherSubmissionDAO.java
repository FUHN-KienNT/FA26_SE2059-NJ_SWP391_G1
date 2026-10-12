package vn.edu.fpt.ams.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import vn.edu.fpt.ams.dto.TeacherSubmissionFilter;
import vn.edu.fpt.ams.dto.TeacherSubmissionRow;

/** Read-only Teacher access; the student's unfinished write workflow stays separate. */
public class TeacherSubmissionDAO {
    private static final String SELECT_OWNED = """
            SELECT sub.*, student.user_id AS student_id, student.full_name AS student_name,
                   student.email AS student_email, grader.full_name AS grader_name
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
            WHERE c.teacher_id = ? AND a.assignment_id = ?
              AND teacher.role = 'TEACHER' AND teacher.account_status = 'ACTIVE'
            """;

    public List<TeacherSubmissionRow> findForTeacher(Connection connection, long teacherId,
            long assignmentId, TeacherSubmissionFilter filter) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_OWNED);
        // strpos treats percent, underscore and SQL-looking text as literal characters.
        if (!filter.search().isEmpty())
            sql.append(" AND (strpos(lower(student.full_name), lower(?)) > 0"
                    + " OR strpos(lower(student.email), lower(?)) > 0)");
        if (filter.status() != null) sql.append(" AND sub.status = ?");
        sql.append(" ORDER BY sub.submitted_at DESC NULLS LAST, sub.submission_id DESC");
        try (var statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            statement.setLong(index++, teacherId);
            statement.setLong(index++, assignmentId);
            if (!filter.search().isEmpty()) {
                statement.setString(index++, filter.search());
                statement.setString(index++, filter.search());
            }
            if (filter.status() != null) statement.setString(index, filter.status().name());
            try (var rows = statement.executeQuery()) {
                List<TeacherSubmissionRow> result = new ArrayList<>();
                while (rows.next()) result.add(mapRow(rows));
                return result;
            }
        }
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
        return new TeacherSubmissionRow(SubmissionDAO.mapSubmission(rows), rows.getLong("student_id"),
                rows.getString("student_name"), rows.getString("student_email"),
                rows.getString("grader_name"));
    }
}
