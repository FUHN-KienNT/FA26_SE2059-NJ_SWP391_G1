package vn.edu.fpt.ams.dao;

import vn.edu.fpt.ams.model.*;
import java.sql.ResultSet;
import java.sql.SQLException;

/** JDBC operations for Assignment; implementations are reserved for the next iteration. */
public class AssignmentDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement transactional JDBC access according to SDS");
    }

public java.util.List<Assignment> findByClassId(long classId) { throw pending(); }
public java.util.Optional<Assignment> findById(long assignmentId) { throw pending(); }
public long insertWithContent(Assignment assignment, long teacherId) { throw pending(); }
public void updateDraftWithContent(Assignment assignment, long teacherId) { throw pending(); }
public boolean publishIfUnchanged(long assignmentId, long teacherId) { throw pending(); }
public void cancelAndVoidInProgress(long assignmentId, long teacherId) { throw pending(); }
public java.util.List<Question> findQuestions(long assignmentId) { throw pending(); }
public long insertQuestion(Question question) { throw pending(); }
public void updateQuestion(Question question) { throw pending(); }
public void deleteDraftQuestion(long questionId) { throw pending(); }

static Assignment mapAssignment(ResultSet rs) throws SQLException {
    Integer timeLimit = (Integer) rs.getObject("time_limit_minutes");
    Integer attemptLimit = (Integer) rs.getObject("attempt_limit");
    return new Assignment(rs.getLong("assignment_id"), rs.getLong("class_id"),
            rs.getLong("template_id"), rs.getString("title"),
            AssignmentPurpose.valueOf(rs.getString("assignment_purpose")),
            ResponseType.valueOf(rs.getString("response_type")), rs.getString("instructions"),
            rs.getString("scope_text"), rs.getString("task_checklist"),
            rs.getBigDecimal("maximum_score"), AssignmentStatus.valueOf(rs.getString("status")),
            ResultSetSupport.instant(rs, "available_from"), ResultSetSupport.instant(rs, "due_at"),
            timeLimit, attemptLimit, ResultSetSupport.instant(rs, "published_at"),
            ResultSetSupport.instant(rs, "cancelled_at"));
}

static Question mapQuestion(ResultSet rs) throws SQLException {
    return new Question(rs.getLong("question_id"), rs.getLong("assignment_id"),
            rs.getInt("question_order"), rs.getString("question_text"),
            rs.getBigDecimal("maximum_points"), rs.getString("options"));
}

}
