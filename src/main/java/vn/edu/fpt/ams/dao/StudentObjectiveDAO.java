package vn.edu.fpt.ams.dao;

import java.sql.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.ObjectiveAnswerGrader;

/** Student membership is checked at this persistence boundary for every operation. */
public class StudentObjectiveDAO {
    public record Owned(Assignment assignment, long enrollmentId, String classCode) { }
    private static final String MEMBERSHIP = """
            FROM assignments a JOIN subject_classes c ON c.class_id=a.class_id
            JOIN class_enrollments e ON e.class_id=c.class_id AND e.status='ACTIVE'
            JOIN subject_registrations r ON r.registration_id=e.registration_id
                AND r.subject_term_id=c.subject_term_id AND r.status='ACTIVE'
            JOIN users u ON u.user_id=r.student_id AND u.role='STUDENT' AND u.account_status='ACTIVE'
            WHERE u.user_id=? AND c.status='ACTIVE' AND a.status='PUBLISHED'
                AND a.response_type='OBJECTIVE'
            """;

    public List<Owned> list(Connection connection, long studentId) throws SQLException {
        try (var s=connection.prepareStatement("SELECT a.*,e.enrollment_id,c.class_code " + MEMBERSHIP
                + " AND a.assignment_purpose='REGULAR' ORDER BY a.due_at,a.assignment_id")) {
            s.setLong(1,studentId);
            try(var rows=s.executeQuery()) {
                List<Owned> result=new ArrayList<>();
                while(rows.next()) result.add(map(rows));
                return result;
            }
        }
    }
    public Owned owned(Connection connection,long studentId,long assignmentId,boolean lock) throws SQLException {
        try(var s=connection.prepareStatement("SELECT a.*,e.enrollment_id,c.class_code " + MEMBERSHIP
                + " AND a.assignment_id=?" + (lock ? " FOR UPDATE OF a" : ""))) {
            s.setLong(1,studentId);s.setLong(2,assignmentId);
            try(var rows=s.executeQuery()) {
                if(!rows.next()) throw new NoSuchElementException("Không tìm thấy bài tập hoặc bạn không có quyền truy cập.");
                return map(rows);
            }
        }
    }
    private Owned map(ResultSet rows) throws SQLException {
        return new Owned(AssignmentDAO.mapAssignment(rows),rows.getLong("enrollment_id"),rows.getString("class_code"));
    }

    public List<Submission> attempts(Connection connection,long studentId,long assignmentId) throws SQLException {
        try(var s=connection.prepareStatement("""
                SELECT sub.* FROM submissions sub
                JOIN class_enrollments e ON e.enrollment_id=sub.enrollment_id
                JOIN subject_registrations r ON r.registration_id=e.registration_id
                WHERE r.student_id=? AND sub.assignment_id=? ORDER BY sub.attempt_number,sub.submission_id FOR UPDATE OF sub
                """)) {
            s.setLong(1,studentId);s.setLong(2,assignmentId);
            try(var rows=s.executeQuery()) {
                List<Submission> result=new ArrayList<>();
                while(rows.next()) result.add(SubmissionDAO.mapSubmission(rows));
                return result;
            }
        }
    }
    public long start(Connection connection,Owned owned,int attempt,Instant now,Instant deadline) throws SQLException {
        long id;
        try(var s=connection.prepareStatement("""
                INSERT INTO submissions(assignment_id,enrollment_id,attempt_number,started_at,deadline_at)
                VALUES(?,?,?,?,?) RETURNING submission_id
                """)) {
            s.setLong(1,owned.assignment().assignmentId());s.setLong(2,owned.enrollmentId());s.setInt(3,attempt);
            s.setObject(4,now.atOffset(ZoneOffset.UTC));s.setObject(5,deadline.atOffset(ZoneOffset.UTC));
            try(var rows=s.executeQuery()){rows.next();id=rows.getLong(1);}
        }
        try(var s=connection.prepareStatement("""
                INSERT INTO submission_content_snapshots(submission_id,assignment_data,questions_data)
                SELECT ?,to_jsonb(a),COALESCE((SELECT jsonb_agg(to_jsonb(q) ORDER BY q.question_order,q.question_id)
                FROM questions q WHERE q.assignment_id=a.assignment_id),'[]'::jsonb)
                FROM assignments a WHERE a.assignment_id=?
                """)) {
            s.setLong(1,id);s.setLong(2,owned.assignment().assignmentId());s.executeUpdate();
        }
        return id;
    }
    public List<Question> snapshotQuestions(Connection connection,long submissionId) throws SQLException {
        try(var s=connection.prepareStatement("""
                SELECT (q->>'question_id')::bigint question_id,(q->>'assignment_id')::bigint assignment_id,
                    (q->>'question_order')::integer question_order,q->>'question_text' question_text,
                    (q->>'maximum_points')::numeric maximum_points,q->'options' options
                FROM submission_content_snapshots s CROSS JOIN LATERAL jsonb_array_elements(s.questions_data) q
                WHERE s.submission_id=? ORDER BY question_order,question_id
                """)) {
            s.setLong(1,submissionId);
            try(var rows=s.executeQuery()) {
                List<Question> result=new ArrayList<>();
                while(rows.next()) result.add(AssignmentDAO.mapQuestion(rows));
                return result;
            }
        }
    }
    public long revision(Connection connection,long id) throws SQLException {
        try(var s=connection.prepareStatement("SELECT answer_revision FROM submissions WHERE submission_id=?")) {
            s.setLong(1,id);try(var rows=s.executeQuery()){if(!rows.next())throw new SQLException("Không tìm thấy lượt làm bài.");return rows.getLong(1);}
        }
    }
    public void save(Connection connection,long id,long revision,String answers) throws SQLException {
        try(var s=connection.prepareStatement("UPDATE submissions SET answer=?::jsonb,answer_revision=answer_revision+1 WHERE submission_id=? AND status='IN_PROGRESS' AND answer_revision=?")) {
            s.setString(1,answers);s.setLong(2,id);s.setLong(3,revision);
            if(s.executeUpdate()!=1) throw new ConcurrentModificationException("Đáp án đã được cập nhật ở phiên khác. Vui lòng tải lại trang trước khi tiếp tục.");
        }
    }
    public void submit(Connection connection,long id,Instant submittedAt,Instant gradedAt,ObjectiveAnswerGrader.Result grade) throws SQLException {
        try(var s=connection.prepareStatement("""
                UPDATE submissions SET answer=?::jsonb,answer_revision=answer_revision+1,score=?,status='GRADED',submitted_at=?,graded_at=?
                WHERE submission_id=? AND status='IN_PROGRESS'
                """)) {
            s.setString(1,grade.answerJson());s.setBigDecimal(2,grade.score());
            s.setObject(3,submittedAt.atOffset(ZoneOffset.UTC));s.setObject(4,gradedAt.atOffset(ZoneOffset.UTC));s.setLong(5,id);
            if(s.executeUpdate()!=1) throw new SQLException("Lượt làm bài đã kết thúc.");
        }
    }
    public void submitSavedWork(Connection connection,long id,Instant submittedAt) throws SQLException {
        try(var s=connection.prepareStatement("""
                UPDATE submissions SET status='SUBMITTED',submitted_at=?
                WHERE submission_id=? AND status='IN_PROGRESS'
                """)) {
            s.setObject(1,submittedAt.atOffset(ZoneOffset.UTC));s.setLong(2,id);
            if(s.executeUpdate()!=1)throw new SQLException("Lượt làm bài đã kết thúc.");
        }
    }

    public List<Long> overdueAssignments(Connection connection) throws SQLException {
        try(var s=connection.prepareStatement("""
                SELECT DISTINCT a.assignment_id FROM assignments a JOIN submissions sub ON sub.assignment_id=a.assignment_id
                WHERE a.status='PUBLISHED' AND sub.status='IN_PROGRESS'
                AND LEAST(sub.deadline_at,a.due_at)<=clock_timestamp() ORDER BY a.assignment_id LIMIT 100
                """);var rows=s.executeQuery()) {
            List<Long> result=new ArrayList<>();while(rows.next())result.add(rows.getLong(1));return result;
        }
    }
    /** Worker follows the same assignment -> submission lock order as all student/teacher writes. */
    public Optional<Assignment> lockPublished(Connection connection,long assignmentId) throws SQLException {
        try(var s=connection.prepareStatement("SELECT * FROM assignments WHERE assignment_id=? AND status='PUBLISHED' FOR UPDATE")) {
            s.setLong(1,assignmentId);try(var rows=s.executeQuery()) {
                return rows.next()?Optional.of(AssignmentDAO.mapAssignment(rows)):Optional.empty();
            }
        }
    }
    public List<Submission> inProgress(Connection connection,long assignmentId) throws SQLException {
        try(var s=connection.prepareStatement("SELECT * FROM submissions WHERE assignment_id=? AND status='IN_PROGRESS' ORDER BY submission_id FOR UPDATE")) {
            s.setLong(1,assignmentId);try(var rows=s.executeQuery()) {
                List<Submission> result=new ArrayList<>();while(rows.next())result.add(SubmissionDAO.mapSubmission(rows));return result;
            }
        }
    }
}
