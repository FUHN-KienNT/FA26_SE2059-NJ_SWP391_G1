package vn.edu.fpt.ams.dao;

import java.sql.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.ObjectiveAnswerGrader;

/** Student membership is checked at this persistence boundary for every operation. */
public class StudentObjectiveDAO {
    public record Owned(Assignment assignment, long enrollmentId, String classCode) {}

    private static final String MEMBERSHIP =
            """
            FROM assignments a JOIN subject_classes c ON c.class_id=a.class_id
            JOIN class_enrollments e ON e.class_id=c.class_id AND e.status='ACTIVE'
            JOIN subject_registrations r ON r.registration_id=e.registration_id
                AND r.subject_term_id=c.subject_term_id AND r.status='ACTIVE'
            JOIN users u ON u.user_id=r.student_id AND u.role='STUDENT' AND u.account_status='ACTIVE'
            WHERE u.user_id=? AND c.status='ACTIVE' AND a.status='PUBLISHED'
                AND a.response_type IN ('OBJECTIVE','ESSAY')
            """;

    public List<Owned> list(Connection connection, long studentId) throws SQLException {
        try (var s =
                connection.prepareStatement(
                        "SELECT a.*,e.enrollment_id,c.class_code "
                                + MEMBERSHIP
                                + " AND a.due_at>CURRENT_TIMESTAMP AND a.closed_at IS NULL ORDER BY a.assignment_id DESC")) {
            s.setLong(1, studentId);

            try (var rows = s.executeQuery()) {
                List<Owned> result = new ArrayList<>();

                while (rows.next()) result.add(map(rows));

                return result;
            }
        }
    }

    public Owned owned(Connection connection, long studentId, long assignmentId, boolean lock)
            throws SQLException {
        try (var s =
                connection.prepareStatement(
                        "SELECT a.*,e.enrollment_id,c.class_code "
                                + MEMBERSHIP
                                + " AND a.assignment_id=?"
                                + (lock ? " FOR UPDATE OF a" : ""))) {
            s.setLong(1, studentId);
            s.setLong(2, assignmentId);

            try (var rows = s.executeQuery()) {
                if (!rows.next())
                    throw new NoSuchElementException(
                            "Không tìm thấy bài tập hoặc bạn không có quyền truy cập.");

                return map(rows);
            }
        }
    }

    private Owned map(ResultSet rows) throws SQLException {
        return new Owned(
                AssignmentDAO.mapAssignment(rows),
                rows.getLong("enrollment_id"),
                rows.getString("class_code"));
    }

    public List<Submission> attempts(Connection connection, long studentId, long assignmentId)
            throws SQLException {
        try (var align = connection.prepareStatement("""
                UPDATE submissions sub SET deadline_at=a.due_at
                FROM assignments a,class_enrollments e,subject_registrations r
                WHERE sub.assignment_id=a.assignment_id AND sub.enrollment_id=e.enrollment_id
                  AND r.registration_id=e.registration_id AND r.student_id=?
                  AND a.assignment_id=? AND sub.status='IN_PROGRESS'
                  AND a.due_at>sub.started_at AND sub.deadline_at IS DISTINCT FROM a.due_at
                """)) {
            align.setLong(1, studentId);
            align.setLong(2, assignmentId);
            align.executeUpdate();
        }

        try (var s =
                connection.prepareStatement(
                        """
                SELECT sub.* FROM submissions sub
                JOIN class_enrollments e ON e.enrollment_id=sub.enrollment_id
                JOIN subject_registrations r ON r.registration_id=e.registration_id
                WHERE r.student_id=? AND sub.assignment_id=? ORDER BY sub.attempt_number,sub.submission_id FOR UPDATE OF sub
                """)) {
            s.setLong(1, studentId);
            s.setLong(2, assignmentId);

            try (var rows = s.executeQuery()) {
                List<Submission> result = new ArrayList<>();

                while (rows.next()) result.add(SubmissionDAO.mapSubmission(rows));

                return result;
            }
        }
    }

    public long start(
            Connection connection, Owned owned, int attempt, Instant now, Instant deadline)
            throws SQLException {
        long id;

        try (var s =
                connection.prepareStatement(
                        """
                INSERT INTO submissions(assignment_id,enrollment_id,attempt_number,started_at,deadline_at)
                VALUES(?,?,?,?,?) RETURNING submission_id
                """)) {
            s.setLong(1, owned.assignment().assignmentId());
            s.setLong(2, owned.enrollmentId());
            s.setInt(3, attempt);
            s.setObject(4, now.atOffset(ZoneOffset.UTC));
            s.setObject(5, deadline.atOffset(ZoneOffset.UTC));

            try (var rows = s.executeQuery()) {
                rows.next();
                id = rows.getLong(1);
            }
        }

        try (var s =
                connection.prepareStatement(
                        """
                INSERT INTO submission_content_snapshots(submission_id,assignment_data,questions_data)
                SELECT ?,to_jsonb(a),COALESCE((SELECT jsonb_agg(to_jsonb(q) ORDER BY q.question_order,q.question_id)
                FROM questions q WHERE q.assignment_id=a.assignment_id),'[]'::jsonb)
                FROM assignments a WHERE a.assignment_id=?
                """)) {
            s.setLong(1, id);
            s.setLong(2, owned.assignment().assignmentId());
            s.executeUpdate();
        }

        return id;
    }

    public List<Question> snapshotQuestions(Connection connection, long submissionId)
            throws SQLException {
        try (var s =
                connection.prepareStatement(
                        """
                SELECT (q->>'question_id')::bigint question_id,(q->>'assignment_id')::bigint assignment_id,
                    (q->>'question_order')::integer question_order,q->>'question_text' question_text,
                    (q->>'maximum_points')::numeric maximum_points,q->'options' options
                FROM submission_content_snapshots s CROSS JOIN LATERAL jsonb_array_elements(s.questions_data) q
                WHERE s.submission_id=? ORDER BY question_order,question_id
                """)) {
            s.setLong(1, submissionId);

            try (var rows = s.executeQuery()) {
                List<Question> result = new ArrayList<>();

                while (rows.next()) result.add(AssignmentDAO.mapQuestion(rows));

                return result;
            }
        }
    }

    public long revision(Connection connection, long id) throws SQLException {
        try (var s =
                connection.prepareStatement(
                        "SELECT answer_revision FROM submissions WHERE submission_id=?")) {
            s.setLong(1, id);

            try (var rows = s.executeQuery()) {
                if (!rows.next()) throw new SQLException("Không tìm thấy lượt làm bài.");

                return rows.getLong(1);
            }
        }
    }

    public void save(Connection connection, long id, long revision, String answers)
            throws SQLException {
        try (var s =
                connection.prepareStatement(
                        "UPDATE submissions SET answer=?::jsonb,answer_revision=answer_revision+1 WHERE submission_id=? AND status='IN_PROGRESS' AND answer_revision=?")) {
            s.setString(1, answers);
            s.setLong(2, id);
            s.setLong(3, revision);

            if (s.executeUpdate() != 1)
                throw new ConcurrentModificationException(
                        "Đáp án đã được cập nhật ở phiên khác. Vui lòng tải lại trang trước khi tiếp tục.");
        }
    }

    public void submit(
            Connection connection,
            long id,
            Instant submittedAt,
            Instant gradedAt,
            ObjectiveAnswerGrader.Result grade)
            throws SQLException {
        try (var s =
                connection.prepareStatement(
                        """
                UPDATE submissions SET answer=?::jsonb,answer_revision=answer_revision+1,score=?,status='RELEASED',submitted_at=?,graded_at=?,released_at=?,graded_by=NULL
                WHERE submission_id=? AND status='IN_PROGRESS'
                """)) {
            s.setString(1, grade.answerJson());
            s.setBigDecimal(2, grade.score());
            s.setObject(3, submittedAt.atOffset(ZoneOffset.UTC));
            s.setObject(4, gradedAt.atOffset(ZoneOffset.UTC));
            s.setObject(5, gradedAt.atOffset(ZoneOffset.UTC));
            s.setLong(6, id);

            if (s.executeUpdate() != 1) throw new SQLException("Lượt làm bài đã kết thúc.");
        }
    }

    public void submitSavedWork(Connection connection, long id, Instant submittedAt)
            throws SQLException {
        try (var s =
                connection.prepareStatement(
                        """
                UPDATE submissions SET status='SUBMITTED',submitted_at=?
                WHERE submission_id=? AND status='IN_PROGRESS'
                """)) {
            s.setObject(1, submittedAt.atOffset(ZoneOffset.UTC));
            s.setLong(2, id);

            if (s.executeUpdate() != 1) throw new SQLException("Lượt làm bài đã kết thúc.");
        }
    }

    public List<Submission> waitingForGrading(Connection connection, long assignmentId)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM submissions WHERE assignment_id=? AND status='SUBMITTED' AND submitted_at IS NOT NULL ORDER BY submission_id FOR UPDATE")) {
            statement.setLong(1, assignmentId);

            try (var rows = statement.executeQuery()) {
                var result = new ArrayList<Submission>();

                while (rows.next()) result.add(SubmissionDAO.mapSubmission(rows));

                return result;
            }
        }
    }

    public void releaseObjective(Connection connection, long submissionId, Instant now,
            ObjectiveAnswerGrader.Result grade) throws SQLException {
        try (var statement = connection.prepareStatement("""
                UPDATE submissions SET answer=?::jsonb,score=?,status='RELEASED',graded_at=?,released_at=?,graded_by=NULL
                WHERE submission_id=? AND status='SUBMITTED'
                """)) {
            statement.setString(1, grade.answerJson());
            statement.setBigDecimal(2, grade.score());
            statement.setObject(3, now.atOffset(ZoneOffset.UTC));
            statement.setObject(4, now.atOffset(ZoneOffset.UTC));
            statement.setLong(5, submissionId);

            if (statement.executeUpdate() != 1)
                throw new ConcurrentModificationException("Bài nộp đã thay đổi trước khi chấm.");
        }
    }

    public void saveEssay(Connection connection, long id, long revision, String content)
            throws SQLException {
        try (var s =
                connection.prepareStatement(
                        "UPDATE submissions SET content=?,answer_revision=answer_revision+1 WHERE submission_id=? AND status='IN_PROGRESS' AND answer_revision=?")) {
            s.setString(1, content);
            s.setLong(2, id);
            s.setLong(3, revision);

            if (s.executeUpdate() != 1)
                throw new ConcurrentModificationException(
                        "Bài làm đã được cập nhật ở phiên khác. Vui lòng tải lại trang.");
        }
    }

    /** Replace accepted work only after the new final has been accepted in this transaction. */
    public void keepLatestAccepted(Connection connection, long submissionId) throws SQLException {
        String obsolete = """
                SELECT previous.submission_id
                FROM submissions previous
                JOIN class_enrollments previous_e ON previous_e.enrollment_id=previous.enrollment_id
                JOIN subject_registrations previous_r ON previous_r.registration_id=previous_e.registration_id
                JOIN submissions current ON current.submission_id=?
                JOIN class_enrollments current_e ON current_e.enrollment_id=current.enrollment_id
                JOIN subject_registrations current_r ON current_r.registration_id=current_e.registration_id
                WHERE previous.assignment_id=current.assignment_id
                  AND previous_r.student_id=current_r.student_id
                  AND previous.submission_id<>current.submission_id
                """;

        String archive = """
                INSERT INTO submission_evaluation_history(
                    submission_id,assignment_id,student_id,actor_id,action,reason,
                    previous_evaluation,next_evaluation,previous_score,next_score,
                    previous_status,next_status)
                SELECT old.submission_id,old.assignment_id,r.student_id,r.student_id,
                    'RESUBMITTED','Sinh viên đã nộp bản làm mới.',
                    COALESCE(old.evaluation,'{"format":"ams-evaluation-v1","marks":[]}'::jsonb)
                        ||jsonb_build_object('feedback',COALESCE(old.feedback,'')),
                    '{"format":"ams-evaluation-v1","marks":[],"feedback":""}'::jsonb,
                    old.score,NULL,old.status,'SUBMITTED'
                FROM submissions old
                JOIN class_enrollments e ON e.enrollment_id=old.enrollment_id
                JOIN subject_registrations r ON r.registration_id=e.registration_id
                WHERE old.submission_id IN (
                """ + obsolete + """
                ) AND (old.status IN ('GRADED','RELEASED')
                    OR COALESCE(old.feedback,'')<>''
                    OR jsonb_array_length(COALESCE(old.evaluation->'marks','[]'::jsonb))>0)
                """;

        try (var statement = connection.prepareStatement(archive)) {
            statement.setLong(1, submissionId);
            statement.executeUpdate();
        }

        try (var statement = connection.prepareStatement(
                "DELETE FROM submission_content_snapshots WHERE submission_id IN (" + obsolete + ")")) {
            statement.setLong(1, submissionId);
            statement.executeUpdate();
        }

        try (var statement = connection.prepareStatement(
                "DELETE FROM submissions WHERE submission_id IN (" + obsolete + ")")) {
            statement.setLong(1, submissionId);
            statement.executeUpdate();
        }
    }

    public Assignment snapshotAssignment(
            Connection connection, long submissionId, long assignmentId) throws SQLException {
        try (var s =
                connection.prepareStatement(
                        "SELECT assignment_data FROM submission_content_snapshots WHERE submission_id=?")) {
            s.setLong(1, submissionId);

            try (var rows = s.executeQuery()) {
                if (!rows.next())
                    throw new IllegalStateException(
                            "Không tìm thấy bản chụp đề bài của lượt làm này.");

                return vn.edu.fpt.ams.util.SubmissionContentSnapshot.assignment(
                        rows.getString(1), assignmentId);
            }
        }
    }

    public List<Long> overdueAssignments(Connection connection) throws SQLException {
        try (var s =
                        connection.prepareStatement(
                                """
                SELECT DISTINCT a.assignment_id FROM assignments a JOIN submissions sub ON sub.assignment_id=a.assignment_id
                WHERE a.status='PUBLISHED'
                  AND (sub.status='IN_PROGRESS' OR (a.response_type='OBJECTIVE' AND sub.status='SUBMITTED'))
                  AND (a.due_at<=clock_timestamp() OR a.closed_at IS NOT NULL)
                ORDER BY a.assignment_id LIMIT 100
                """);
                var rows = s.executeQuery()) {
            List<Long> result = new ArrayList<>();

            while (rows.next()) result.add(rows.getLong(1));

            return result;
        }
    }

    /** Worker follows the same assignment -> submission lock order as all student/teacher writes. */
    public Assignment closeSubmissions(Connection connection, long assignmentId, Instant now)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "UPDATE assignments SET closed_at=? WHERE assignment_id=? AND closed_at IS NULL RETURNING *")) {
            statement.setObject(1, now.atOffset(ZoneOffset.UTC));
            statement.setLong(2, assignmentId);

            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new ConcurrentModificationException("Bài tập đã được khóa.");

                return AssignmentDAO.mapAssignment(rows);
            }
        }
    }

    public Optional<Assignment> lockPublished(Connection connection, long assignmentId)
            throws SQLException {
        try (var s =
                connection.prepareStatement(
                        "SELECT * FROM assignments WHERE assignment_id=? AND status='PUBLISHED' FOR UPDATE")) {
            s.setLong(1, assignmentId);

            try (var rows = s.executeQuery()) {
                return rows.next()
                        ? Optional.of(AssignmentDAO.mapAssignment(rows))
                        : Optional.empty();
            }
        }
    }

    public List<Submission> inProgress(Connection connection, long assignmentId)
            throws SQLException {
        try (var align = connection.prepareStatement("""
                UPDATE submissions sub SET deadline_at=a.due_at
                FROM assignments a
                WHERE a.assignment_id=sub.assignment_id AND a.assignment_id=?
                  AND sub.status='IN_PROGRESS' AND a.due_at>sub.started_at
                  AND sub.deadline_at IS DISTINCT FROM a.due_at
                """)) {
            align.setLong(1, assignmentId);
            align.executeUpdate();
        }

        try (var s =
                connection.prepareStatement(
                        "SELECT * FROM submissions WHERE assignment_id=? AND status='IN_PROGRESS' ORDER BY submission_id FOR UPDATE")) {
            s.setLong(1, assignmentId);

            try (var rows = s.executeQuery()) {
                List<Submission> result = new ArrayList<>();

                while (rows.next()) result.add(SubmissionDAO.mapSubmission(rows));

                return result;
            }
        }
    }
}
