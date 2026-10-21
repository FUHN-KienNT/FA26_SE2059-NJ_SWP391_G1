package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import vn.edu.fpt.ams.dao.*;
import vn.edu.fpt.ams.model.SubmissionStatus;
import vn.edu.fpt.ams.util.DBConnection;

/** PostgreSQL TEMP fixtures and a TEMP identity sequence: application data is never changed. */
class StudentObjectiveDatabaseTest {
    Connection connection;
    @BeforeEach void setup() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("assignment.db.tests"));
        connection=DBConnection.getConnection();
        try(var s=connection.createStatement()) {
            s.execute("CREATE TEMP TABLE users(user_id BIGINT PRIMARY KEY,role TEXT,account_status TEXT)");
            s.execute("CREATE TEMP TABLE subject_classes(class_id BIGINT PRIMARY KEY,subject_term_id BIGINT,class_code TEXT,status TEXT)");
            s.execute("CREATE TEMP TABLE subject_registrations(registration_id BIGINT PRIMARY KEY,student_id BIGINT,subject_term_id BIGINT,status TEXT)");
            s.execute("CREATE TEMP TABLE class_enrollments(enrollment_id BIGINT PRIMARY KEY,registration_id BIGINT,class_id BIGINT,status TEXT)");
            s.execute("CREATE TEMP TABLE assignments(LIKE public.assignments INCLUDING ALL)");
            s.execute("CREATE TEMP TABLE questions(LIKE public.questions INCLUDING ALL)");
            s.execute("CREATE TEMP TABLE submissions(LIKE public.submissions INCLUDING ALL)");
            s.execute("ALTER TABLE submissions ALTER COLUMN submission_id DROP IDENTITY IF EXISTS");
            s.execute("CREATE TEMP SEQUENCE student_objective_test_ids");
            s.execute("ALTER TABLE submissions ALTER COLUMN submission_id SET DEFAULT nextval('student_objective_test_ids')");
            s.execute("CREATE TEMP TABLE submission_content_snapshots(submission_id BIGINT PRIMARY KEY,assignment_data JSONB,questions_data JSONB)");
            s.execute("SET search_path TO pg_temp");
            s.execute("INSERT INTO users VALUES(1,'STUDENT','ACTIVE'),(2,'STUDENT','ACTIVE'),(3,'TEACHER','ACTIVE')");
            s.execute("INSERT INTO subject_classes VALUES(10,100,'CLASS A','ACTIVE'),(20,200,'FOREIGN','ACTIVE')");
            s.execute("INSERT INTO subject_registrations VALUES(11,1,100,'ACTIVE'),(22,2,200,'ACTIVE'),(33,3,100,'ACTIVE')");
            s.execute("INSERT INTO class_enrollments VALUES(1,11,10,'ACTIVE'),(2,22,20,'ACTIVE'),(3,33,10,'ACTIVE')");
            s.execute("""
                    INSERT INTO assignments(assignment_id,class_id,template_id,title,assignment_purpose,response_type,
                    instructions,maximum_score,status,available_from,due_at,time_limit_minutes,attempt_limit,published_at)
                    VALUES(50,10,1,'Quiz','PRACTICE','OBJECTIVE','Choose answers',10,'PUBLISHED',
                    CURRENT_TIMESTAMP-INTERVAL '1 day',CURRENT_TIMESTAMP+INTERVAL '1 day',20,2,CURRENT_TIMESTAMP-INTERVAL '1 day')
                    """);
            s.execute("""
                    INSERT INTO questions(question_id,assignment_id,question_order,question_text,maximum_points,options)
                    VALUES(51,50,1,'Choose primes',5,'[{"option_id":"A","option_text":"2","is_correct":true},{"option_id":"B","option_text":"4","is_correct":false},{"option_id":"C","option_text":"3","is_correct":true}]'),
                    (52,50,2,'One plus one',5,'[{"option_id":"A","option_text":"2","is_correct":true},{"option_id":"B","option_text":"4","is_correct":false}]')
                    """);
        }
    }
    @Test void regularAssignmentListRetainsOwnershipAndHidesPracticeWithoutDeletingIt() throws Exception {
        sql("""
                INSERT INTO assignments(assignment_id,class_id,template_id,title,assignment_purpose,response_type,
                    instructions,maximum_score,status,available_from,due_at,published_at)
                VALUES(60,10,1,'Regular own','REGULAR','OBJECTIVE','Instructions',10,'PUBLISHED',
                        CURRENT_TIMESTAMP-INTERVAL '1 hour',CURRENT_TIMESTAMP+INTERVAL '1 day',CURRENT_TIMESTAMP),
                      (61,20,1,'Regular foreign','REGULAR','OBJECTIVE','Instructions',10,'PUBLISHED',
                        CURRENT_TIMESTAMP-INTERVAL '1 hour',CURRENT_TIMESTAMP+INTERVAL '1 day',CURRENT_TIMESTAMP),
                      (62,10,1,'Draft own','REGULAR','OBJECTIVE','Instructions',10,'DRAFT',NULL,NULL,NULL),
                      (63,10,1,'Essay own','REGULAR','ESSAY','Instructions',10,'PUBLISHED',
                        CURRENT_TIMESTAMP-INTERVAL '1 hour',CURRENT_TIMESTAMP+INTERVAL '1 day',CURRENT_TIMESTAMP)
                """);
        assertEquals(List.of(60L),service().list(1).stream().map(x->x.assignment().assignmentId()).toList());
        assertEquals(List.of(61L),service().list(2).stream().map(x->x.assignment().assignmentId()).toList());
        assertTrue(service().list(3).isEmpty());
        assertEquals(vn.edu.fpt.ams.model.AssignmentPurpose.PRACTICE,
                new StudentObjectiveDAO().owned(connection,1,50,false).assignment().assignmentPurpose());
        assertEquals(5,count("assignments"));
        assertEquals(0,count("submissions"));
    }
    @AfterEach void close() throws Exception { if(connection!=null)connection.close(); }
    StudentObjectiveService service() {
        return service(new AssignmentDAO());
    }
    StudentObjectiveService service(AssignmentDAO clock) {
        Connection borrowed=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
            if(m.getName().equals("close"))return null;
            try{return m.invoke(connection,a);}catch(InvocationTargetException ex){throw ex.getCause();}
        });
        return new StudentObjectiveService(new StudentObjectiveDAO(),clock,()->borrowed);
    }
    void sql(String sql) throws Exception {try(var s=connection.createStatement()){s.execute(sql);}if(!connection.getAutoCommit())connection.commit();}
    long count(String table) throws Exception {try(var s=connection.createStatement();var r=s.executeQuery("SELECT count(*) FROM "+table)){r.next();return r.getLong(1);}}
    @Test void startsResumesAndGradesMixedSelectionsWithoutReleasingOrOverwriting() throws Exception {
        var service=service();long id=service.start(1,50);
        assertEquals(id,service.start(1,50));assertEquals(1,count("submissions"));assertEquals(1,count("submission_content_snapshots"));
        var page=service.page(1,50,id);assertTrue(page.questions().get(0).multiple());assertFalse(page.questions().get(1).multiple());
        service.submit(1,50,id,0,Map.of(51L,List.of("A","C"),52L,List.of("B")));
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT * FROM submissions")) {
            r.next();assertEquals("GRADED",r.getString("status"));assertEquals(5,r.getBigDecimal("score").intValue());
            assertNull(r.getObject("released_at"));assertNotNull(r.getObject("graded_at"));assertTrue(r.getString("answer").contains("selected_option_ids"));
        }
        service.submit(1,50,id,0,Map.of(51L,List.of("B")));
        assertEquals(SubmissionStatus.GRADED,service.page(1,50,id).status());
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT score FROM submissions")){r.next();assertEquals(5,r.getInt(1));}
        assertTrue(service.page(1,50,id).questions().isEmpty());
    }
    @Test void ownershipRoleMembershipAndPublishedStatusAreRequired() throws Exception {
        var service=service();assertEquals(1,service.list(1).size());assertTrue(service.list(2).isEmpty());assertTrue(service.list(3).isEmpty());
        assertThrows(NoSuchElementException.class,()->service.start(2,50));
        assertThrows(NoSuchElementException.class,()->service.start(3,50));
        long id=service.start(1,50);
        assertThrows(NoSuchElementException.class,()->service.submit(2,50,id,0,Map.of()));
        sql("UPDATE class_enrollments SET status='INACTIVE' WHERE enrollment_id=1");
        assertThrows(NoSuchElementException.class,()->service.page(1,50,id));
        sql("UPDATE class_enrollments SET status='ACTIVE';UPDATE assignments SET status='CANCELLED',cancelled_at=CURRENT_TIMESTAMP");
        assertThrows(NoSuchElementException.class,()->service.submit(1,50,id,0,Map.of()));
        assertEquals(1,count("submissions"));
    }
    @Test void invalidSelectionsRollbackAndAttemptLimitIsEnforced() throws Exception {
        var service=service();long id=service.start(1,50);
        assertThrows(IllegalArgumentException.class,()->service.submit(1,50,id,0,Map.of(51L,List.of("foreign"))));
        assertEquals(SubmissionStatus.IN_PROGRESS,service.page(1,50,id).status());
        service.submit(1,50,id,0,Map.of());
        long second=service.start(1,50);assertNotEquals(id,second);service.submit(1,50,second,0,Map.of());
        assertThrows(IllegalArgumentException.class,()->service.start(1,50));assertEquals(2,count("submissions"));
    }
    @Test void overdueAttemptIsSubmittedWithSavedAnswersAndIgnoresLateWork() throws Exception {
        var service=service();long id=service.start(1,50);
        assertFalse(service.save(1,50,id,0,Map.of(51L,List.of("A","C"))).finished());
        sql("UPDATE submissions SET started_at=CURRENT_TIMESTAMP-INTERVAL '21 minutes',deadline_at=CURRENT_TIMESTAMP-INTERVAL '1 minute'");
        service.submit(1,50,id,0,Map.of(51L,List.of("B"),52L,List.of("A")));
        assertEquals(SubmissionStatus.GRADED,service.page(1,50,id).status());
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT submitted_at,deadline_at,score,answer FROM submissions")) {
            r.next();assertEquals(r.getObject(2),r.getObject(1));assertEquals(5,r.getInt(3));
            assertFalse(r.getString(4).contains("\"B\""));
        }
    }
    @Test void saveHasNoAwardedScoreAndPageRestoresSelections() throws Exception {
        var service=service();long id=service.start(1,50);
        service.save(1,50,id,0,Map.of(51L,List.of("A","C"),52L,List.of("B")));
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT status,score,submitted_at,answer FROM submissions")) {
            r.next();assertEquals("IN_PROGRESS",r.getString(1));assertNull(r.getObject(2));assertNull(r.getObject(3));
            assertFalse(r.getString(4).contains("awarded_points"));
        }
        assertEquals(List.of("A","C"),service.page(1,50,id).selected().get(51L));
        assertThrows(IllegalArgumentException.class,()->service.save(1,50,id,1,Map.of(51L,List.of("foreign"))));
        assertEquals(List.of("A","C"),service.page(1,50,id).selected().get(51L));
        assertThrows(NoSuchElementException.class,()->service.save(2,50,id,0,Map.of()));
    }
    @Test void deadlineUsesDueTimeWhenItComesBeforeTheTimeLimit() throws Exception {
        Instant now=Instant.parse("2026-10-16T11:30:00Z"); // 18:30 Vietnam time.
        var service=service(new AssignmentDAO(){@Override public Instant currentTime(Connection c){return now;}});
        sql("UPDATE assignments SET available_from='2026-10-16T10:00:00Z',due_at='2026-10-16T12:00:00Z',time_limit_minutes=60");
        long id=service.start(1,50);
        assertEquals(Instant.parse("2026-10-16T12:00:00Z"),service.page(1,50,id).deadline());
    }
    @Test void exactDeadlineFinalizesAndRejectsEvenValidLateAnswers() throws Exception {
        Instant[] clock={Instant.parse("2026-10-16T11:30:00Z")};
        var service=service(new AssignmentDAO(){@Override public Instant currentTime(Connection c){return clock[0];}});
        sql("UPDATE assignments SET available_from='2026-10-16T10:00:00Z',due_at='2026-10-16T12:00:00Z',time_limit_minutes=20");
        long id=service.start(1,50);
        assertEquals(Instant.parse("2026-10-16T11:50:00Z"),service.page(1,50,id).deadline());
        clock[0]=Instant.parse("2026-10-16T11:49:59.999Z");
        service.save(1,50,id,0,Map.of(51L,List.of("A","C")));
        clock[0]=Instant.parse("2026-10-16T11:50:00Z");
        assertTrue(service.save(1,50,id,0,Map.of(51L,List.of("B"),52L,List.of("A"))).finished());
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT score,submitted_at=deadline_at FROM submissions")){r.next();assertEquals(5,r.getInt(1));assertTrue(r.getBoolean(2));}
        assertEquals(SubmissionStatus.GRADED,service.page(1,50,id).status());
    }
    @Test void scheduledClosureWorksWithoutBrowserAndNeverTouchesCancelledOrVoidWork() throws Exception {
        var service=service();long id=service.start(1,50);
        service.save(1,50,id,0,Map.of(51L,List.of("A","C")));
        sql("UPDATE submissions SET started_at=CURRENT_TIMESTAMP-INTERVAL '21 minutes',deadline_at=CURRENT_TIMESTAMP-INTERVAL '1 minute'");
        assertEquals(1,service.finishOverdue());assertEquals(0,service.finishOverdue());
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT score,submitted_at=deadline_at FROM submissions")){r.next();assertEquals(5,r.getInt(1));assertTrue(r.getBoolean(2));}
        long second=service.start(1,50);
        sql("UPDATE submissions SET started_at=CURRENT_TIMESTAMP-INTERVAL '21 minutes',deadline_at=CURRENT_TIMESTAMP-INTERVAL '1 minute' WHERE submission_id="+second);
        sql("UPDATE assignments SET status='CANCELLED',cancelled_at=CURRENT_TIMESTAMP");
        assertEquals(0,service.finishOverdue());
        sql("UPDATE submissions SET status='VOID' WHERE submission_id="+second);
        assertEquals(0,service.finishOverdue());
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT status,score,submitted_at FROM submissions WHERE submission_id="+second)) {
            r.next();assertEquals("VOID",r.getString(1));assertNull(r.getObject(2));assertNull(r.getObject(3));
        }
    }
    @Test void openingAnOverdueAttemptAlsoFinalizesLastSavedAnswers() throws Exception {
        var service=service();long id=service.start(1,50);
        service.save(1,50,id,0,Map.of(52L,List.of("A")));
        sql("UPDATE submissions SET started_at=CURRENT_TIMESTAMP-INTERVAL '21 minutes',deadline_at=CURRENT_TIMESTAMP-INTERVAL '1 minute'");
        assertEquals(SubmissionStatus.GRADED,service.page(1,50,id).status());
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT score FROM submissions")){r.next();assertEquals(5,r.getInt(1));}
    }
    @Test void staleAutosaveOrManualSubmitCannotOverwriteNewerAnswers() throws Exception {
        var service=service();long id=service.start(1,50);
        var saved=service.save(1,50,id,0,Map.of(51L,List.of("A","C")));
        assertEquals(1,saved.revision());assertEquals(1,service.page(1,50,id).answerRevision());
        assertThrows(ConcurrentModificationException.class,()->service.save(1,50,id,0,Map.of(51L,List.of("B"))));
        assertThrows(ConcurrentModificationException.class,()->service.submit(1,50,id,0L,Map.of(51L,List.of("B"))));
        assertEquals(List.of("A","C"),service.page(1,50,id).selected().get(51L));
        service.submit(1,50,id,1L,Map.of(51L,List.of("A","C")));
        assertEquals(SubmissionStatus.GRADED,service.page(1,50,id).status());
    }
    @Test void malformedLegacyAttemptDoesNotRollbackOtherAttemptsInTheSameAssignment() throws Exception {
        var service=service();long broken=service.start(1,50);
        sql("UPDATE submissions SET answer='[{\"question_id\":51,\"selected_option_id\":\"A\"}]',content='Nội dung cũ cần giữ nguyên' WHERE submission_id="+broken);
        sql("UPDATE submissions SET started_at=CURRENT_TIMESTAMP-INTERVAL '21 minutes',deadline_at=CURRENT_TIMESTAMP-INTERVAL '1 minute'");
        sql("UPDATE submission_content_snapshots SET questions_data='[]' WHERE submission_id="+broken);
        sql("INSERT INTO users VALUES(4,'STUDENT','ACTIVE');INSERT INTO subject_registrations VALUES(44,4,100,'ACTIVE');INSERT INTO class_enrollments VALUES(4,44,10,'ACTIVE')");
        long valid=service.start(4,50);
        service.save(4,50,valid,0,Map.of(51L,List.of("A","C")));
        sql("UPDATE submissions SET started_at=CURRENT_TIMESTAMP-INTERVAL '21 minutes',deadline_at=CURRENT_TIMESTAMP-INTERVAL '1 minute' WHERE submission_id="+valid);
        assertEquals(2,service.finishOverdue());
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT score,status FROM submissions WHERE submission_id="+valid)) {
            r.next();assertEquals(5,r.getInt(1));assertEquals("GRADED",r.getString(2));
        }
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT * FROM submissions WHERE submission_id="+broken)) {
            r.next();assertEquals("SUBMITTED",r.getString("status"));assertNull(r.getObject("score"));assertNull(r.getObject("graded_at"));
            assertEquals(r.getObject("deadline_at"),r.getObject("submitted_at"));assertEquals("Nội dung cũ cần giữ nguyên",r.getString("content"));
            assertTrue(r.getString("answer").contains("selected_option_id"));assertTrue(r.getString("answer").contains("\"A\""));
        }
    }
    @Test void workerSubmitsSavedEssayAtDeadlineWithoutGradingOrChangingAnswers() throws Exception {
        sql("UPDATE assignments SET response_type='ESSAY',assignment_purpose='REGULAR',attempt_limit=NULL");
        sql("""
                INSERT INTO submissions(assignment_id,enrollment_id,attempt_number,started_at,deadline_at,content,answer)
                VALUES(50,1,1,CURRENT_TIMESTAMP-INTERVAL '21 minutes',CURRENT_TIMESTAMP-INTERVAL '1 minute',
                'Nội dung tự luận đã lưu','[{"task_id":1,"answer_text":"Câu trả lời đã lưu"}]')
                """);
        assertEquals(1,service().finishOverdue());
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT * FROM submissions")) {
            r.next();assertEquals("SUBMITTED",r.getString("status"));
            assertEquals(r.getObject("deadline_at"),r.getObject("submitted_at"));
            assertEquals("Nội dung tự luận đã lưu",r.getString("content"));
            assertTrue(r.getString("answer").contains("Câu trả lời đã lưu"));
            assertNull(r.getObject("score"));assertNull(r.getObject("graded_at"));assertNull(r.getObject("released_at"));
        }
    }
    @Test void workerGradesLegacySingleAnswersButPreservesMalformedAnswersUngraded() throws Exception {
        var service=service();long legacy=service.start(1,50);
        sql("UPDATE submissions SET answer='[{\"question_id\":51,\"selected_option_id\":null},{\"question_id\":52,\"selected_option_id\":\"A\"}]',started_at=CURRENT_TIMESTAMP-INTERVAL '21 minutes',deadline_at=CURRENT_TIMESTAMP-INTERVAL '1 minute'");
        assertEquals(1,service.finishOverdue());
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT status,score FROM submissions WHERE submission_id="+legacy)) {
            r.next();assertEquals("GRADED",r.getString(1));assertEquals(5,r.getInt(2));
        }
        long malformed=service.start(1,50);
        sql("UPDATE submissions SET answer='[{\"question_id\":51.5,\"selected_option_id\":\"A\"}]',started_at=CURRENT_TIMESTAMP-INTERVAL '21 minutes',deadline_at=CURRENT_TIMESTAMP-INTERVAL '1 minute' WHERE submission_id="+malformed);
        assertEquals(1,service.finishOverdue());assertEquals(0,service.finishOverdue());
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT status,score,answer,submitted_at=deadline_at FROM submissions WHERE submission_id="+malformed)) {
            r.next();assertEquals("SUBMITTED",r.getString(1));assertNull(r.getObject(2));assertTrue(r.getString(3).contains("51.5"));assertTrue(r.getBoolean(4));
        }
    }
    @Test void gradingUsesTheFrozenQuestionSet() throws Exception {
        var service=service();long id=service.start(1,50);
        sql("DELETE FROM questions");
        service.submit(1,50,id,0,Map.of(51L,List.of("A","C"),52L,List.of("A")));
        try(var s=connection.createStatement();var r=s.executeQuery("SELECT score FROM submissions")){r.next();assertEquals(10,r.getInt(1));}
    }
    @Test void cannotStartBeforeOpeningOrAfterDeadline() throws Exception {
        var service=service();sql("UPDATE assignments SET available_from=CURRENT_TIMESTAMP+INTERVAL '1 hour'");
        assertThrows(IllegalArgumentException.class,()->service.start(1,50));
        sql("UPDATE assignments SET available_from=CURRENT_TIMESTAMP-INTERVAL '2 days',due_at=CURRENT_TIMESTAMP-INTERVAL '1 day'");
        assertThrows(IllegalArgumentException.class,()->service.start(1,50));assertEquals(0,count("submissions"));
    }
}
