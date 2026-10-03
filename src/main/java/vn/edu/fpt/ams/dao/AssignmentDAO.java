package vn.edu.fpt.ams.dao;

import vn.edu.fpt.ams.model.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import vn.edu.fpt.ams.dto.AssignmentClassOption;
import vn.edu.fpt.ams.dto.AssignmentListFilter;

/** JDBC operations for Assignment; implementations are reserved for the next iteration. */
public class AssignmentDAO {
    public long findPreviewTeacherId(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT u.user_id FROM users u
                WHERE u.role = 'TEACHER' AND u.account_status = 'ACTIVE'
                ORDER BY EXISTS (SELECT 1 FROM subject_classes c WHERE c.teacher_id = u.user_id) DESC,
                         u.user_id
                LIMIT 1
                """); var rows = statement.executeQuery()) {
            if (!rows.next()) throw new IllegalStateException("Local preview requires an active Teacher in the database.");
            return rows.getLong("user_id");
        }
    }

    public boolean isActiveTeacher(Connection connection, long teacherId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM users WHERE user_id = ? AND role = 'TEACHER' AND account_status = 'ACTIVE'")) {
            statement.setLong(1, teacherId);
            try (var rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    public List<AssignmentClassOption> findClassesForTeacher(Connection connection, long teacherId)
            throws SQLException {
        // Include inactive/historical classes still assigned to this Teacher for read access.
        try (var statement = connection.prepareStatement("""
                SELECT class_id, class_code, class_name FROM subject_classes
                WHERE teacher_id = ? ORDER BY class_code, class_id
                """)) {
            statement.setLong(1, teacherId);
            try (var rows = statement.executeQuery()) {
                List<AssignmentClassOption> classes = new ArrayList<>();
                while (rows.next()) classes.add(new AssignmentClassOption(rows.getLong("class_id"),
                        rows.getString("class_code"), rows.getString("class_name")));
                return classes;
            }
        }
    }

    public List<Assignment> findForTeacher(Connection connection, long teacherId, long classId,
                                            AssignmentListFilter filter) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT a.* FROM assignments a
                JOIN subject_classes c ON c.class_id = a.class_id
                JOIN users u ON u.user_id = c.teacher_id
                WHERE c.teacher_id = ? AND c.class_id = ?
                  AND u.role = 'TEACHER' AND u.account_status = 'ACTIVE'
                """);
        if (!filter.search().isEmpty()) sql.append(" AND strpos(lower(a.title), lower(?)) > 0");
        if (filter.purpose() != null) sql.append(" AND a.assignment_purpose = ?");
        if (filter.responseType() != null) sql.append(" AND a.response_type = ?");
        if (filter.status() != null) sql.append(" AND a.status = ?");
        sql.append(" ORDER BY a.assignment_id DESC");
        try (var statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            statement.setLong(index++, teacherId);
            statement.setLong(index++, classId);
            if (!filter.search().isEmpty()) statement.setString(index++, filter.search());
            if (filter.purpose() != null) statement.setString(index++, filter.purpose().name());
            if (filter.responseType() != null) statement.setString(index++, filter.responseType().name());
            if (filter.status() != null) statement.setString(index++, filter.status().name());
            try (var rows = statement.executeQuery()) {
                List<Assignment> assignments = new ArrayList<>();
                while (rows.next()) assignments.add(mapAssignment(rows));
                return assignments;
            }
        }
    }

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
