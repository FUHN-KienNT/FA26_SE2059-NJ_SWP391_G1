package vn.edu.fpt.ams.dao;

import vn.edu.fpt.ams.model.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import vn.edu.fpt.ams.dto.AssignmentClassOption;
import vn.edu.fpt.ams.dto.AssignmentListFilter;
import vn.edu.fpt.ams.dto.AssignmentDetailPage;
import vn.edu.fpt.ams.dto.AssignmentCreatePage;
import java.util.Optional;

/** Teacher-scoped assignment persistence. The Service owns write transactions. */
public class AssignmentDAO {
    /** Read after acquiring write locks, so publication checks use the actual server time. */
    public java.time.Instant currentTime(Connection connection) throws SQLException {
        try(var statement=connection.prepareStatement("SELECT clock_timestamp()"); var rows=statement.executeQuery()) {
            rows.next(); return rows.getObject(1,java.time.OffsetDateTime.class).toInstant();
        }
    }

    public int countInProgressSubmissions(Connection connection,long assignmentId) throws SQLException {
        try(var statement=connection.prepareStatement("SELECT count(*) FROM submissions WHERE assignment_id=? AND status='IN_PROGRESS'")) {
            statement.setLong(1,assignmentId);
            try(var rows=statement.executeQuery()) { rows.next(); return rows.getInt(1); }
        }
    }

    public boolean hasSubmittedWork(Connection connection,long assignmentId) throws SQLException {
        try(var statement=connection.prepareStatement("SELECT EXISTS (SELECT 1 FROM submissions WHERE assignment_id=? AND submitted_at IS NOT NULL)")) {
            statement.setLong(1,assignmentId);
            try(var rows=statement.executeQuery()) { rows.next(); return rows.getBoolean(1); }
        }
    }

    /** Freeze the question content seen by each existing attempt before reopening editing. */
    public void captureSubmissionContent(Connection connection,long assignmentId) throws SQLException {
        try(var statement=connection.prepareStatement("""
                INSERT INTO submission_content_snapshots(submission_id,assignment_data,questions_data)
                SELECT s.submission_id,to_jsonb(a),COALESCE((
                    SELECT jsonb_agg(to_jsonb(q) ORDER BY q.question_order,q.question_id)
                    FROM questions q WHERE q.assignment_id=a.assignment_id
                ),'[]'::jsonb)
                FROM assignments a JOIN submissions s ON s.assignment_id=a.assignment_id
                WHERE a.assignment_id=?
                ON CONFLICT (submission_id) DO NOTHING
                """)) {
            statement.setLong(1,assignmentId); statement.executeUpdate();
        }
    }

    public void markDraft(Connection connection,long assignmentId) throws SQLException {
        // published_at records prior publication; attempts and their results remain untouched.
        try(var statement=connection.prepareStatement("UPDATE assignments SET status='DRAFT',cancelled_at=NULL WHERE assignment_id=? AND status='CANCELLED'")) {
            statement.setLong(1,assignmentId);
            if(statement.executeUpdate()!=1) throw new SQLException("The locked cancelled assignment could not be restored.");
        }
    }

    public void markPublished(Connection connection,long assignmentId,java.time.Instant now) throws SQLException {
        try(var statement=connection.prepareStatement("UPDATE assignments SET status='PUBLISHED',published_at=COALESCE(published_at,?) WHERE assignment_id=? AND status='DRAFT'")) {
            statement.setObject(1,now.atOffset(java.time.ZoneOffset.UTC)); statement.setLong(2,assignmentId);
            if(statement.executeUpdate()!=1) throw new SQLException("The locked Draft could not be published.");
        }
    }

    public void markCancelled(Connection connection,long assignmentId,java.time.Instant now) throws SQLException {
        try(var statement=connection.prepareStatement("UPDATE assignments SET status='CANCELLED',cancelled_at=? WHERE assignment_id=? AND status IN ('DRAFT','PUBLISHED')")) {
            statement.setObject(1,now.atOffset(java.time.ZoneOffset.UTC)); statement.setLong(2,assignmentId);
            if(statement.executeUpdate()!=1) throw new SQLException("The locked assignment could not be cancelled.");
        }
    }

    public int voidInProgressSubmissions(Connection connection,long assignmentId) throws SQLException {
        // Accepted work and results are never touched. Keep unfinished content for history.
        try(var statement=connection.prepareStatement("UPDATE submissions SET status='VOID' WHERE assignment_id=? AND status='IN_PROGRESS'")) {
            statement.setLong(1,assignmentId); return statement.executeUpdate();
        }
    }

    public Assignment lockOwnedAssignment(Connection connection, long teacherId, long assignmentId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT a.* FROM assignments a JOIN subject_classes c ON c.class_id=a.class_id
                WHERE a.assignment_id=? AND c.teacher_id=? FOR UPDATE OF a
                """)) {
            statement.setLong(1,assignmentId); statement.setLong(2,teacherId);
            try(var rows=statement.executeQuery()) {
                if(!rows.next()) throw new java.util.NoSuchElementException("Assignment not found or unavailable.");
                return mapAssignment(rows);
            }
        }
    }

    /** Service must authorize the parent Assignment before reading or locking its children. */
    public List<Question> findAllDraftQuestions(Connection connection, long assignmentId, boolean lock) throws SQLException {
        try(var statement=connection.prepareStatement("SELECT * FROM questions WHERE assignment_id=? ORDER BY question_order,question_id" + (lock ? " FOR UPDATE" : ""))) {
            statement.setLong(1,assignmentId);
            try(var rows=statement.executeQuery()) {
                List<Question> questions=new ArrayList<>();
                while(rows.next()) questions.add(mapQuestion(rows));
                return questions;
            }
        }
    }

    public void updateDraft(Connection connection, long assignmentId, Assignment a) throws SQLException {
        try(var statement=connection.prepareStatement("""
                UPDATE assignments SET template_id=?,title=?,assignment_purpose=?,response_type=?,instructions=?,
                    scope_text=?,task_checklist=CAST(? AS jsonb),maximum_score=?,available_from=?,due_at=?,
                    time_limit_minutes=?,attempt_limit=? WHERE assignment_id=? AND status='DRAFT'
                """)) {
            statement.setLong(1,a.templateId()); statement.setString(2,a.title());
            statement.setString(3,a.assignmentPurpose().name()); statement.setString(4,a.responseType().name());
            statement.setString(5,a.instructions()); statement.setString(6,a.scopeText());
            statement.setString(7,a.taskChecklistJson()); statement.setBigDecimal(8,a.maximumScore());
            statement.setObject(9,a.availableFrom()==null?null:a.availableFrom().atOffset(java.time.ZoneOffset.UTC));
            statement.setObject(10,a.dueAt()==null?null:a.dueAt().atOffset(java.time.ZoneOffset.UTC));
            statement.setObject(11,a.timeLimitMinutes()); statement.setObject(12,a.attemptLimit()); statement.setLong(13,assignmentId);
            if(statement.executeUpdate()!=1) throw new SQLException("Draft update did not affect the locked assignment.");
        }
    }

    public void replaceDraftQuestions(Connection connection, long assignmentId, List<Question> existing, List<Question> desired) throws SQLException {
        List<Long> retained=desired.stream().map(Question::questionId).filter(id->id>0).toList();
        String delete="DELETE FROM questions WHERE assignment_id=?";
        if(!retained.isEmpty()) delete+=" AND question_id NOT IN ("+String.join(",",java.util.Collections.nCopies(retained.size(),"?"))+")";
        try(var statement=connection.prepareStatement(delete)) {
            statement.setLong(1,assignmentId);
            for(int i=0;i<retained.size();i++) statement.setLong(i+2,retained.get(i));
            statement.executeUpdate();
        }
        // Stage retained rows outside both their old orders and the final 1..N range.
        // All temporary orders stay positive to satisfy CHECK and immediate UNIQUE constraints.
        java.util.Set<Integer> used=new java.util.HashSet<>(); existing.forEach(q->used.add(q.questionOrder()));
        int stage=desired.size()+1;
        try(var statement=connection.prepareStatement("UPDATE questions SET question_order=? WHERE assignment_id=? AND question_id=?")) {
            for(long id:retained) {
                while(used.contains(stage)) stage++;
                statement.setInt(1,stage); statement.setLong(2,assignmentId); statement.setLong(3,id);
                if(statement.executeUpdate()!=1) throw new SQLException("Question is no longer in this draft.");
                used.add(stage++);
            }
        }
        try(var update=connection.prepareStatement("UPDATE questions SET question_order=?,question_text=?,maximum_points=?,options=CAST(? AS jsonb) WHERE assignment_id=? AND question_id=?")) {
            List<Question> added=new ArrayList<>();
            for(Question q:desired) {
                if(q.questionId()==0) { added.add(q); continue; }
                update.setInt(1,q.questionOrder()); update.setString(2,q.questionText());
                update.setBigDecimal(3,q.maximumPoints()); update.setString(4,q.options());
                update.setLong(5,assignmentId); update.setLong(6,q.questionId());
                if(update.executeUpdate()!=1) throw new SQLException("Question update failed.");
            }
            insertDraftQuestions(connection,assignmentId,added);
        }
    }

    public boolean lockActiveTeacher(Connection connection, long teacherId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT user_id FROM users WHERE user_id = ?
                AND role = 'TEACHER' AND account_status = 'ACTIVE' FOR SHARE
                """)) {
            statement.setLong(1, teacherId);
            try (var rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    public AssignmentCreatePage findCreatePage(Connection connection, long teacherId, long classId,
                                                boolean lockClass) throws SQLException {
        String sql = """
                SELECT c.class_id,c.class_code,c.class_name,s.subject_id,s.subject_code,s.subject_name
                FROM subject_classes c
                JOIN subject_terms st ON st.subject_term_id=c.subject_term_id
                JOIN subjects s ON s.subject_id=st.subject_id
                WHERE c.class_id=? AND c.teacher_id=?
                """ + (lockClass ? " FOR SHARE OF c, st" : "");
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, classId);
            statement.setLong(2, teacherId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new SecurityException("You cannot create assignments for this class.");
                long subjectId = rows.getLong("subject_id");
                return new AssignmentCreatePage(classId, subjectId, rows.getString("class_code"),
                        rows.getString("class_name"), rows.getString("subject_code"), rows.getString("subject_name"),
                        lockClass ? List.of() : findActiveTemplates(connection, subjectId));
            }
        }
    }

    private List<AssignmentCreatePage.TemplateOption> findActiveTemplates(Connection connection, long subjectId)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT template_id,title,instructions,maximum_score FROM assignment_templates
                WHERE subject_id=? AND status='ACTIVE' ORDER BY title,template_id
                """)) {
            statement.setLong(1, subjectId);
            try (var rows = statement.executeQuery()) {
                List<AssignmentCreatePage.TemplateOption> templates = new ArrayList<>();
                while (rows.next()) templates.add(new AssignmentCreatePage.TemplateOption(rows.getLong("template_id"),
                        rows.getString("title"), rows.getString("instructions"), rows.getBigDecimal("maximum_score")));
                return templates;
            }
        }
    }

    public boolean lockActiveTemplate(Connection connection, long templateId, long subjectId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT template_id FROM assignment_templates
                WHERE template_id=? AND subject_id=? AND status='ACTIVE' FOR SHARE
                """)) {
            statement.setLong(1, templateId);
            statement.setLong(2, subjectId);
            try (var rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    public long insertDraft(Connection connection, Assignment a) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO assignments (class_id,template_id,title,assignment_purpose,response_type,instructions,
                    scope_text,task_checklist,maximum_score,status,available_from,due_at,time_limit_minutes,attempt_limit)
                VALUES (?,?,?,?,?,?,?,CAST(? AS jsonb),?,'DRAFT',?,?,?,?) RETURNING assignment_id
                """)) {
            statement.setLong(1, a.classId());
            statement.setLong(2, a.templateId());
            statement.setString(3, a.title());
            statement.setString(4, a.assignmentPurpose().name());
            statement.setString(5, a.responseType().name());
            statement.setString(6, a.instructions());
            statement.setString(7, a.scopeText());
            statement.setString(8, a.taskChecklistJson());
            statement.setBigDecimal(9, a.maximumScore());
            statement.setObject(10, a.availableFrom() == null ? null : a.availableFrom().atOffset(java.time.ZoneOffset.UTC));
            statement.setObject(11, a.dueAt() == null ? null : a.dueAt().atOffset(java.time.ZoneOffset.UTC));
            statement.setObject(12, a.timeLimitMinutes());
            statement.setObject(13, a.attemptLimit());
            try (var rows = statement.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }

    public void insertDraftQuestions(Connection connection, long assignmentId, List<Question> questions)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO questions (assignment_id,question_order,question_text,maximum_points,options)
                VALUES (?,?,?,?,CAST(? AS jsonb))
                """)) {
            for (Question question : questions) {
                statement.setLong(1, assignmentId);
                statement.setInt(2, question.questionOrder());
                statement.setString(3, question.questionText());
                statement.setBigDecimal(4, question.maximumPoints());
                statement.setString(5, question.options());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

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

    public Optional<AssignmentDetailPage> findDetailForTeacher(Connection connection, long teacherId,
                                                               long assignmentId) throws SQLException {
        // Authorization is part of the query; classId supplied by a browser is never trusted.
        try (var statement = connection.prepareStatement("""
                SELECT a.*, c.class_code, c.class_name, s.subject_code, s.subject_name,
                       t.term_code, tpl.title AS template_title
                FROM assignments a
                JOIN subject_classes c ON c.class_id = a.class_id
                JOIN users u ON u.user_id = c.teacher_id
                JOIN subject_terms st ON st.subject_term_id = c.subject_term_id
                JOIN subjects s ON s.subject_id = st.subject_id
                JOIN academic_terms t ON t.term_id = st.term_id
                JOIN assignment_templates tpl ON tpl.template_id = a.template_id
                WHERE a.assignment_id = ? AND c.teacher_id = ?
                  AND u.role = 'TEACHER' AND u.account_status = 'ACTIVE'
                """)) {
            statement.setLong(1, assignmentId);
            statement.setLong(2, teacherId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                return Optional.of(new AssignmentDetailPage(mapAssignment(rows), rows.getString("class_code"),
                        rows.getString("class_name"), rows.getString("subject_code"), rows.getString("subject_name"),
                        rows.getString("term_code"), rows.getString("template_title"), List.of()));
            }
        }
    }

    public List<Question> findQuestionsForTeacher(Connection connection, long teacherId, long assignmentId)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT q.* FROM questions q
                JOIN assignments a ON a.assignment_id = q.assignment_id
                JOIN subject_classes c ON c.class_id = a.class_id
                JOIN users u ON u.user_id = c.teacher_id
                WHERE a.assignment_id = ? AND c.teacher_id = ? AND a.response_type = 'OBJECTIVE'
                  AND u.role = 'TEACHER' AND u.account_status = 'ACTIVE'
                ORDER BY q.question_order, q.question_id
                """)) {
            statement.setLong(1, assignmentId);
            statement.setLong(2, teacherId);
            try (var rows = statement.executeQuery()) {
                List<Question> questions = new ArrayList<>();
                while (rows.next()) questions.add(mapQuestion(rows));
                return questions;
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
