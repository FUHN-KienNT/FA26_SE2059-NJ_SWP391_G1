package vn.edu.fpt.ams.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import vn.edu.fpt.ams.dto.StudentResultPage;

/** Historical access follows the original enrollment, never a student's current class membership. */
public class StudentResultDAO {
    private static final String OWNED =
            """
            FROM submissions sub
            JOIN assignments a ON a.assignment_id=sub.assignment_id
            JOIN subject_classes c ON c.class_id=a.class_id
            JOIN class_enrollments e ON e.enrollment_id=sub.enrollment_id AND e.class_id=c.class_id
            JOIN subject_registrations r ON r.registration_id=e.registration_id AND r.subject_term_id=c.subject_term_id
            JOIN users u ON u.user_id=r.student_id AND u.role='STUDENT' AND u.account_status='ACTIVE'
            LEFT JOIN submission_content_snapshots snapshot ON snapshot.submission_id=sub.submission_id
            WHERE u.user_id=? AND a.assignment_id=?
              AND sub.status IN ('SUBMITTED','GRADED','RELEASED')
              AND sub.started_at IS NOT NULL AND sub.deadline_at>sub.started_at
              AND sub.submitted_at>=sub.started_at AND sub.submitted_at<=sub.deadline_at
              AND sub.submitted_at<=CURRENT_TIMESTAMP
            """;
    private static final String CATALOG_OWNED = OWNED.replace(" AND a.assignment_id=?", "");
    private static final String RELEASED =
            """
            (sub.status='RELEASED' AND sub.graded_at>=sub.submitted_at
             AND sub.released_at>=sub.graded_at AND sub.released_at<=CURRENT_TIMESTAMP
             AND (a.due_at<=CURRENT_TIMESTAMP OR a.closed_at IS NOT NULL))
            """;
    private static final String SUMMARY =
            """
            SELECT sub.submission_id,sub.assignment_id,a.class_id,a.title,c.class_code,
                   sub.attempt_number,sub.submitted_at,
            """
                    + RELEASED
                    + " AS visible, CASE WHEN "
                    + RELEASED
                    + " THEN sub.released_at END AS visible_released_at,"
                    + " CASE WHEN "
                    + RELEASED
                    + " THEN sub.score END AS visible_score";

    /** Raw payloads stay at the persistence boundary and are SQL-redacted before mapping pending work. */
    public record Row(
            StudentResultPage.Attempt attempt,
            java.math.BigDecimal maximumScore,
            String assignmentSnapshot,
            String questionsSnapshot,
            String content,
            String answer,
            String feedback,
            String evaluation,
            long revision) {}

    public void requireStudent(Connection connection, long studentId) throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        "SELECT 1 FROM users WHERE user_id=? AND role='STUDENT' AND account_status='ACTIVE'")) {
            statement.setLong(1, studentId);

            try (var rows = statement.executeQuery()) {
                if (!rows.next())
                    throw new SecurityException("Tài khoản sinh viên không còn hoạt động.");
            }
        }
    }

    public List<StudentResultPage.Attempt> history(
            Connection connection, long studentId, long assignmentId) throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        SUMMARY
                                + " "
                                + OWNED
                                + " ORDER BY sub.attempt_number DESC,sub.submission_id DESC LIMIT 1")) {
            statement.setLong(1, studentId);
            statement.setLong(2, assignmentId);

            try (var rows = statement.executeQuery()) {
                List<StudentResultPage.Attempt> result = new ArrayList<>();

                while (rows.next()) result.add(attempt(rows));

                return result;
            }
        }
    }

    public Optional<Row> detail(
            Connection connection, long studentId, long assignmentId, long submissionId)
            throws SQLException {
        String sql =
                SUMMARY
                        + ",a.maximum_score"
                        + visible("snapshot.assignment_data", "assignment_snapshot")
                        + visible("snapshot.questions_data", "questions_snapshot")
                        + visible("sub.content", "visible_content")
                        + visible("sub.answer", "visible_answer")
                        + visible("sub.feedback", "visible_feedback")
                        + visible("sub.evaluation", "visible_evaluation")
                        + visible("sub.evaluation_revision", "visible_revision")
                        + " "
                        + OWNED
                        + " AND sub.submission_id=?";

        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            statement.setLong(2, assignmentId);
            statement.setLong(3, submissionId);

            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();

                return Optional.of(
                        new Row(
                                attempt(rows),
                                rows.getBigDecimal("maximum_score"),
                                rows.getString("assignment_snapshot"),
                                rows.getString("questions_snapshot"),
                                rows.getString("visible_content"),
                                rows.getString("visible_answer"),
                                rows.getString("visible_feedback"),
                                rows.getString("visible_evaluation"),
                                rows.getLong("visible_revision")));
            }
        }
    }

    public List<StudentResultPage.Attempt> catalog(
            Connection connection, long studentId, int page) throws SQLException {
        String sql = "SELECT * FROM ("
                + SUMMARY.replace("SELECT sub.submission_id", "SELECT DISTINCT ON (a.assignment_id) sub.submission_id")
                + " " + CATALOG_OWNED
                + " ORDER BY a.assignment_id DESC,sub.attempt_number DESC,sub.submission_id DESC) latest"
                + " ORDER BY submitted_at DESC,submission_id DESC LIMIT " + StudentResultPage.PAGE_SIZE + " OFFSET ?";

        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            statement.setLong(2, (long) (page - 1) * StudentResultPage.PAGE_SIZE);

            try (var rows = statement.executeQuery()) {
                var result = new ArrayList<StudentResultPage.Attempt>();

                while (rows.next()) result.add(attempt(rows));

                return result;
            }
        }
    }

    public long countCatalog(Connection connection, long studentId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT count(DISTINCT a.assignment_id) " + CATALOG_OWNED)) {
            statement.setLong(1, studentId);

            try (var rows = statement.executeQuery()) {
                rows.next();

                return rows.getLong(1);
            }
        }
    }

    /** Own accepted content is exportable after expiry, without opening the evaluation gate. */
    public Optional<Row> exportRow(
            Connection connection, long studentId, long assignmentId, long submissionId)
            throws SQLException {
        String sql = SUMMARY + ",a.maximum_score,snapshot.assignment_data AS assignment_snapshot,"
                + "snapshot.questions_data AS questions_snapshot,sub.content AS visible_content,"
                + "sub.answer AS visible_answer " + OWNED + " AND sub.submission_id=?";

        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            statement.setLong(2, assignmentId);
            statement.setLong(3, submissionId);

            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();

                return Optional.of(new Row(attempt(rows), rows.getBigDecimal("maximum_score"),
                        rows.getString("assignment_snapshot"), rows.getString("questions_snapshot"),
                        rows.getString("visible_content"), rows.getString("visible_answer"),
                        null, null, 0));
            }
        }
    }

    private static String visible(String column, String alias) {
        return ", CASE WHEN " + RELEASED + " THEN " + column + " END AS " + alias;
    }

    private static StudentResultPage.Attempt attempt(ResultSet rows) throws SQLException {
        return new StudentResultPage.Attempt(
                rows.getLong("submission_id"),
                rows.getLong("assignment_id"),
                rows.getLong("class_id"),
                rows.getString("title"),
                rows.getString("class_code"),
                rows.getInt("attempt_number"),
                instant(rows, "submitted_at"),
                rows.getBoolean("visible"),
                instant(rows, "visible_released_at"),
                rows.getBigDecimal("visible_score"));
    }

    private static Instant instant(ResultSet rows, String name) throws SQLException {
        var value = rows.getTimestamp(name);

        return value == null ? null : value.toInstant();
    }
}
