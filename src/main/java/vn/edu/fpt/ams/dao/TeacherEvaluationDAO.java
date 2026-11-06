package vn.edu.fpt.ams.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import vn.edu.fpt.ams.dto.SubmissionEvaluation;

/** Evaluation persistence; the service owns authorization, lock order and the transaction. */
public class TeacherEvaluationDAO {
    public record Saved(String json, long revision) {}

    public void lockClass(Connection connection, long teacherId, long classId) throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        """
                SELECT class_id FROM subject_classes WHERE class_id=? AND teacher_id=? FOR SHARE
                """)) {
            statement.setLong(1, classId);
            statement.setLong(2, teacherId);

            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw missing();
            }
        }
    }

    public void lockSubmission(
            Connection connection, long teacherId, long assignmentId, long submissionId)
            throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        """
                SELECT sub.submission_id FROM submissions sub
                JOIN assignments a ON a.assignment_id=sub.assignment_id
                JOIN subject_classes c ON c.class_id=a.class_id
                JOIN users teacher ON teacher.user_id=c.teacher_id
                JOIN class_enrollments e ON e.enrollment_id=sub.enrollment_id AND e.class_id=c.class_id
                JOIN subject_registrations r ON r.registration_id=e.registration_id AND r.subject_term_id=c.subject_term_id
                WHERE c.teacher_id=? AND a.assignment_id=? AND sub.submission_id=?
                  AND teacher.role='TEACHER' AND teacher.account_status='ACTIVE'
                FOR UPDATE OF sub FOR SHARE OF e,r
                """)) {
            statement.setLong(1, teacherId);
            statement.setLong(2, assignmentId);
            statement.setLong(3, submissionId);

            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw missing();
            }
        }
    }

    /** Called only after the service has loaded the same teacher-owned submission in this transaction. */
    public Saved read(Connection connection, long assignmentId, long submissionId)
            throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        """
                SELECT evaluation::text,evaluation_revision FROM submissions WHERE assignment_id=? AND submission_id=?
                """)) {
            statement.setLong(1, assignmentId);
            statement.setLong(2, submissionId);

            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw missing();

                return new Saved(rows.getString(1), rows.getLong(2));
            }
        }
    }

    /** Preserve legacy accepted work as well; an existing attempt snapshot is never replaced. */
    public void captureSnapshot(Connection connection, long assignmentId, long submissionId)
            throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        """
                INSERT INTO submission_content_snapshots(submission_id,assignment_data,questions_data)
                SELECT sub.submission_id,to_jsonb(a),COALESCE((
                    SELECT jsonb_agg(to_jsonb(q) ORDER BY q.question_order,q.question_id)
                    FROM questions q WHERE q.assignment_id=a.assignment_id),'[]'::jsonb)
                FROM assignments a JOIN submissions sub ON sub.assignment_id=a.assignment_id
                WHERE a.assignment_id=? AND sub.submission_id=?
                ON CONFLICT (submission_id) DO NOTHING
                """)) {
            statement.setLong(1, assignmentId);
            statement.setLong(2, submissionId);
            statement.executeUpdate();
        }
    }

    public void save(
            Connection connection,
            long submissionId,
            long teacherId,
            SubmissionEvaluation evaluation,
            boolean complete)
            throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        """
                UPDATE submissions SET evaluation=CAST(? AS jsonb),evaluation_revision=evaluation_revision+1,
                    score=?,feedback=?,graded_by=?,graded_at=CASE WHEN ? THEN clock_timestamp() ELSE NULL END,
                    status=?,released_at=NULL
                WHERE submission_id=? AND evaluation_revision=? AND status='SUBMITTED' AND submitted_at IS NOT NULL
                """)) {
            statement.setString(1, evaluation.json());
            statement.setBigDecimal(2, evaluation.total());
            statement.setString(3, evaluation.feedback());
            statement.setLong(4, teacherId);
            statement.setBoolean(5, complete);
            statement.setString(6, complete ? "GRADED" : "SUBMITTED");
            statement.setLong(7, submissionId);
            statement.setLong(8, evaluation.revision());
            requireUpdated(statement.executeUpdate());
        }
    }

    public void reopen(Connection connection, long submissionId, long revision)
            throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        """
                UPDATE submissions SET status='SUBMITTED',graded_at=NULL,evaluation_revision=evaluation_revision+1
                WHERE submission_id=? AND evaluation_revision=? AND status='GRADED' AND released_at IS NULL
                """)) {
            statement.setLong(1, submissionId);
            statement.setLong(2, revision);
            requireUpdated(statement.executeUpdate());
        }
    }

    public void release(Connection connection, long submissionId, long revision)
            throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        """
                UPDATE submissions SET status='RELEASED',released_at=clock_timestamp(),evaluation_revision=evaluation_revision+1
                WHERE submission_id=? AND evaluation_revision=? AND status='GRADED' AND released_at IS NULL
                """)) {
            statement.setLong(1, submissionId);
            statement.setLong(2, revision);
            requireUpdated(statement.executeUpdate());
        }
    }

    private static void requireUpdated(int count) {
        if (count != 1)
            throw new ConcurrentModificationException(
                    "Bài chấm đã thay đổi. Hãy tải lại trang trước khi tiếp tục.");
    }

    private static NoSuchElementException missing() {
        return new NoSuchElementException("Không tìm thấy bài nộp hoặc bạn không có quyền xem.");
    }
}
