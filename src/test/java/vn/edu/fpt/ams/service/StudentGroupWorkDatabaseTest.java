package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import vn.edu.fpt.ams.dao.*;
import vn.edu.fpt.ams.dto.StudentClassFilter;
import vn.edu.fpt.ams.model.SubmissionStatus;
import vn.edu.fpt.ams.util.*;

/** Shared work and individual grade projections on session-local tables and sequences only. */
class StudentGroupWorkDatabaseTest {
    private static final String TASKS = """
            [{"task_order":1,"title":"Câu nhóm","maximum_points":10,"rubric":"PRIVATE_RUBRIC",
            "requirements":[{"requirement_order":1,"title":"Giải thích","maximum_points":10,
            "answer_type":"TEXT","rubric":"PRIVATE_CHILD_RUBRIC"}]}]
            """;
    private Connection connection;
    private Connection borrowed;

    @BeforeEach void setup() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("assignment.db.tests"));
        connection = DBConnection.getConnection();
        sql("""
                SET search_path TO pg_temp;
                CREATE TEMP TABLE users(user_id BIGINT PRIMARY KEY,role TEXT,account_status TEXT,full_name TEXT,email TEXT);
                CREATE TEMP TABLE subject_classes(class_id BIGINT PRIMARY KEY,subject_term_id BIGINT,class_code TEXT,status TEXT,teacher_id BIGINT);
                CREATE TEMP TABLE subject_registrations(registration_id BIGINT PRIMARY KEY,student_id BIGINT,subject_term_id BIGINT,status TEXT);
                CREATE TEMP TABLE class_enrollments(enrollment_id BIGINT PRIMARY KEY,registration_id BIGINT,class_id BIGINT,status TEXT);
                CREATE TEMP TABLE project_groups(group_id BIGINT PRIMARY KEY,class_id BIGINT,UNIQUE(group_id,class_id));
                CREATE TEMP TABLE assignments(LIKE public.assignments INCLUDING ALL);
                CREATE TEMP TABLE questions(LIKE public.questions INCLUDING ALL);
                CREATE TEMP TABLE submissions(LIKE public.submissions INCLUDING ALL);
                ALTER TABLE submissions ALTER COLUMN submission_id DROP IDENTITY IF EXISTS;
                CREATE TEMP SEQUENCE group_student_work_ids;
                ALTER TABLE submissions ALTER COLUMN submission_id SET DEFAULT nextval('group_student_work_ids');
                CREATE TEMP TABLE submission_content_snapshots(submission_id BIGINT PRIMARY KEY,assignment_data JSONB,questions_data JSONB);
                CREATE TEMP TABLE submission_evaluation_history(history_id BIGINT GENERATED ALWAYS AS IDENTITY,submission_id BIGINT,assignment_id BIGINT,student_id BIGINT,actor_id BIGINT,action TEXT,reason TEXT,previous_evaluation JSONB,next_evaluation JSONB,previous_score NUMERIC,next_score NUMERIC,previous_status TEXT,next_status TEXT,created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP);
                INSERT INTO users VALUES(1,'STUDENT','ACTIVE','Một','one@test'),(2,'STUDENT','ACTIVE','Hai','two@test'),
                  (3,'STUDENT','ACTIVE','Ngoài nhóm','other@test'),(9,'TEACHER','ACTIVE','Giảng viên','teacher@test');
                INSERT INTO subject_classes VALUES(10,100,'GROUP-A','ACTIVE',9);
                INSERT INTO subject_registrations VALUES(11,1,100,'ACTIVE'),(22,2,100,'ACTIVE'),(33,3,100,'ACTIVE');
                INSERT INTO class_enrollments VALUES(1,11,10,'ACTIVE'),(2,22,10,'ACTIVE'),(3,33,10,'ACTIVE');
                INSERT INTO project_groups VALUES(7,10);
                """);
        String migration = Files.readString(Path.of("database/migrations/009-group-assignments-peer-grading.sql"))
                .replace("CREATE TABLE IF NOT EXISTS", "CREATE TEMP TABLE IF NOT EXISTS")
                .replace("BEGIN;", "").replace("COMMIT;", "");
        sql(migration);
        try (var p = connection.prepareStatement("""
                INSERT INTO assignments(assignment_id,class_id,template_id,title,assignment_purpose,response_type,
                    instructions,task_checklist,maximum_score,status,available_from,due_at,published_at)
                SELECT id,10,1,'Bài '||id,'REGULAR',CASE WHEN id=51 THEN 'OBJECTIVE' ELSE 'ESSAY' END,
                    '',?::jsonb,10,'PUBLISHED',now()-interval '1 day',now()+interval '1 day',now()-interval '1 day'
                FROM (VALUES(50),(51),(52)) value(id)
                """)) {
            p.setString(1, TASKS);
            p.executeUpdate();
        }
        sql("""
                INSERT INTO assignment_group_settings(assignment_id,delivery_mode,target_mode,teacher_weight,peer_weight)
                    VALUES(50,'GROUP','ALL',70,30),(51,'GROUP','ALL',70,30);
                INSERT INTO assignment_group_targets(target_id,assignment_id,class_id,group_id,group_name,owner_enrollment_id,frozen_at)
                    VALUES(70,50,10,7,'Nhóm gốc',1,now()),(71,51,10,7,'Nhóm gốc',1,now());
                INSERT INTO assignment_group_members(target_id,student_id,enrollment_id,student_name)
                    VALUES(70,1,1,'Một'),(70,2,2,'Hai'),(71,1,1,'Một'),(71,2,2,'Hai');
                INSERT INTO questions(question_id,assignment_id,question_order,question_text,maximum_points,options)
                    VALUES(501,51,1,'Chọn đúng',10,'[{"option_id":"A","option_text":"Đúng","is_correct":true},{"option_id":"B","option_text":"Sai","is_correct":false}]');
                """);
        borrowed = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (p, method, args) -> {
                    if (method.getName().equals("close")) {
                        if (!connection.getAutoCommit()) connection.rollback();
                        connection.setReadOnly(false);
                        connection.setAutoCommit(true);
                        return null;
                    }
                    try { return method.invoke(connection, args); }
                    catch (InvocationTargetException ex) { throw ex.getCause(); }
                });
    }

    @AfterEach void close() throws Exception { if (connection != null) connection.close(); }

    private StudentObjectiveService work() {
        return new StudentObjectiveService(new StudentObjectiveDAO(), new AssignmentDAO(), () -> borrowed);
    }

    private StudentResultService results() { return new StudentResultService(new StudentResultDAO(), () -> borrowed); }

    private void sql(String value) throws Exception {
        if (!connection.getAutoCommit()) connection.commit();
        connection.setReadOnly(false);
        connection.setAutoCommit(true);
        try (var p = connection.createStatement()) { p.execute(value); }
    }

    private static String answer(String text) {
        return "{\"format\":\"ams-essay-v2\",\"answers\":[{\"task_order\":1,\"requirement_order\":1,\"text\":\"" + text + "\"}]}";
    }

    @Test void sharedDraftUsesOneCanonicalAttemptAndRejectsStaleWritesAndOutsiders() {
        var service = work();
        long first = service.start(2, 50);
        assertEquals(first, service.start(1, 50));
        service.saveEssay(1, 50, first, 0, answer("Nội dung đầu"));
        var otherMember = service.page(2, 50, first);
        assertEquals("Nhóm gốc", otherMember.group().groupName());
        assertTrue(otherMember.essayContent().contains("Nội dung đầu"));
        assertFalse(otherMember.assignment().taskChecklistJson().contains("PRIVATE"));
        assertThrows(ConcurrentModificationException.class,
                () -> service.saveEssay(2, 50, first, 0, answer("Ghi đè")));
        service.saveEssay(2, 50, first, 1, answer("Cùng hoàn thiện"));
        assertTrue(service.page(1, 50, first).essayContent().contains("Cùng hoàn thiện"));
        assertThrows(NoSuchElementException.class, () -> service.page(3, 50, first));
        assertThrows(NoSuchElementException.class, () -> service.start(3, 50));
    }

    @Test void acceptedSharedFinalRemainsUntilReplacementIsSubmittedAndEveryMemberCanExport() {
        var service = work();
        long old = service.start(1, 50);
        service.submitEssay(2, 50, old, 0, answer("Bản đã nộp"));
        long next = service.start(2, 50);
        assertNotEquals(old, next);
        assertTrue(results().export(1, 50, old).contains("Bản đã nộp"));
        assertTrue(results().export(2, 50, old).contains("Bản đã nộp"));
        assertFalse(results().export(2, 50, old).contains("PRIVATE"));
        assertThrows(NoSuchElementException.class, () -> results().export(3, 50, old));
        assertThrows(IllegalArgumentException.class, () -> service.submitEssay(1, 50, next, 0, "invalid"));
        assertTrue(results().export(2, 50, old).contains("Bản đã nộp"));
        service.submitEssay(1, 50, next, 0, answer("Bản cuối"));
        assertEquals(next, results().load(2, 50, null).attempts().get(0).submissionId());
        assertThrows(NoSuchElementException.class, () -> results().export(2, 50, old));
    }

    @Test void currentListsShowOnlyFrozenRecipientsAndShareTheLatestStatus() throws Exception {
        var service = work();
        long id = service.start(1, 50);
        service.submitEssay(1, 50, id, 0, answer("Chung"));
        var dao = new StudentClassDAO();
        var now = dao.currentTime(connection);
        var all = StudentClassFilter.from("", "", "", "1");
        assertEquals(3, dao.count(connection, 2, 10, now, all));
        assertEquals(1, dao.count(connection, 3, 10, now, all));
        assertEquals(id, dao.assignments(connection, 2, 10, now, all).stream()
                .filter(row -> row.assignmentId() == 50).findFirst().orElseThrow().submissionId());
        assertEquals("Nhóm gốc", dao.assignments(connection, 2, 10, now, all).stream()
                .filter(row -> row.assignmentId() == 50).findFirst().orElseThrow().groupName());
        assertNull(dao.assignments(connection, 3, 10, now, all).get(0).groupName());
        var dashboard = new StudentDashboardDAO();
        assertEquals(3, dashboard.assignmentCount(connection, 2));
        assertEquals(1, dashboard.assignmentCount(connection, 3));
        assertEquals(3, dashboard.openAssignments(connection, 2, now).size());
        assertEquals(1, dashboard.openAssignments(connection, 3, now).size());
    }

    @Test void groupObjectiveAutoGradingWaitsForExplicitTeacherPublication() throws Exception {
        var service = work();
        long id = service.start(2, 51);
        service.submit(1, 51, id, 0, Map.of(501L, List.of("A")));
        assertEquals(SubmissionStatus.SUBMITTED, service.page(2, 51, id).status());
        sql("UPDATE assignments SET closed_at=clock_timestamp() WHERE assignment_id=51");
        service.finishOverdue();
        assertEquals(SubmissionStatus.GRADED, service.page(2, 51, id).status());
        var row = new StudentResultDAO().detail(connection, 2, 51, id).orElseThrow();
        assertFalse(row.attempt().released());
        assertNull(row.attempt().score());
        assertNull(row.answer());
    }

    @Test void releasedGroupGradeIsIndividualAndRequiresItsOwnPublishedResultRow() throws Exception {
        long id = work().start(1, 50);
        work().submitEssay(2, 50, id, 0, answer("Bài chung"));
        sql("UPDATE assignments SET closed_at=clock_timestamp() WHERE assignment_id=50;"
                + "UPDATE submissions SET status='RELEASED',score=8,graded_at=clock_timestamp(),released_at=clock_timestamp(),"
                + "evaluation='{\"format\":\"ams-evaluation-v1\",\"marks\":[{\"task_order\":1,\"requirement_order\":1,\"score\":8,\"feedback\":\"Tốt\"}]}'::jsonb WHERE submission_id=" + id);
        var hidden = new StudentResultDAO().detail(connection, 2, 50, id).orElseThrow();
        assertNull(hidden.attempt().score());
        assertNull(hidden.content());
        assertNull(hidden.evaluation());
        assertFalse(hidden.attempt().released());
        var dashboard = new StudentDashboardDAO();
        assertEquals(0, dashboard.releasedCount(connection, 2, java.time.Instant.now()));
        assertTrue(dashboard.results(connection, 2, java.time.Instant.now()).isEmpty());
        sql("INSERT INTO assignment_group_results VALUES"
                + "(70,1," + id + ",8,6,7.4,70,30,1,1,clock_timestamp()),"
                + "(70,2," + id + ",8,9,8.3,70,30,1,1,clock_timestamp())");
        var first = results().load(1, 50, id);
        var second = results().load(2, 50, id);
        assertEquals(new BigDecimal("7.40"), first.detail().score().setScale(2));
        assertEquals(new BigDecimal("8.30"), second.detail().score().setScale(2));
        assertEquals(0, new BigDecimal("8").compareTo(second.groupResult().teacherScore()));
        assertFalse(second.detail().evaluationUnavailable());
        assertFalse(second.toString().contains("PRIVATE"));
        assertEquals(1, dashboard.releasedCount(connection, 2, java.time.Instant.now()));
        assertEquals(new BigDecimal("8.30"), dashboard.results(connection, 2, java.time.Instant.now())
                .get(0).score().setScale(2));
        assertThrows(NoSuchElementException.class, () -> results().load(3, 50, id));
        sql("UPDATE class_enrollments SET status='INACTIVE' WHERE enrollment_id=1");
        assertEquals(id, work().page(2, 50, id).submissionId());
        assertEquals(new BigDecimal("8.30"), results().load(2, 50, id).detail().score().setScale(2));
    }
}
