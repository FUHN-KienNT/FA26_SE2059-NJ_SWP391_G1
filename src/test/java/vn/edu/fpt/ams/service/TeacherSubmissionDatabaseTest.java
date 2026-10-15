package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import vn.edu.fpt.ams.dao.AssignmentDAO;
import vn.edu.fpt.ams.dao.TeacherSubmissionDAO;
import vn.edu.fpt.ams.dto.TeacherSubmissionFilter;
import vn.edu.fpt.ams.model.SubmissionStatus;
import vn.edu.fpt.ams.util.DBConnection;

/** Database cases opt in; every fixture and mutation remains in session-local TEMP tables. */
class TeacherSubmissionDatabaseTest {
    private Connection connection;

    @BeforeEach void setup(TestInfo test) throws SQLException {
        if (!test.getTags().contains("database")) return;
        Assumptions.assumeTrue(Boolean.getBoolean("assignment.db.tests"),
                "Enable assignment.db.tests for PostgreSQL checks");
        connection = DBConnection.getConnection();
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE users (user_id BIGINT PRIMARY KEY, role TEXT, account_status TEXT, full_name TEXT, email TEXT)");
            statement.execute("CREATE TEMP TABLE subjects (subject_id BIGINT PRIMARY KEY, subject_code TEXT, subject_name TEXT)");
            statement.execute("CREATE TEMP TABLE academic_terms (term_id BIGINT PRIMARY KEY, term_code TEXT)");
            statement.execute("CREATE TEMP TABLE subject_terms (subject_term_id BIGINT PRIMARY KEY, subject_id BIGINT, term_id BIGINT)");
            statement.execute("CREATE TEMP TABLE subject_classes (class_id BIGINT PRIMARY KEY, subject_term_id BIGINT, teacher_id BIGINT, class_code TEXT, class_name TEXT, status TEXT)");
            statement.execute("CREATE TEMP TABLE assignment_templates (template_id BIGINT PRIMARY KEY, title TEXT)");
            statement.execute("CREATE TEMP TABLE subject_registrations (registration_id BIGINT PRIMARY KEY, student_id BIGINT, subject_term_id BIGINT, status TEXT)");
            statement.execute("CREATE TEMP TABLE class_enrollments (enrollment_id BIGINT PRIMARY KEY, registration_id BIGINT, class_id BIGINT, status TEXT)");
            for (String table : List.of("assignments", "questions", "submissions")) {
                String id = table.equals("assignments") ? "assignment_id"
                        : table.equals("questions") ? "question_id" : "submission_id";
                statement.execute("CREATE TEMP TABLE " + table + " (LIKE public." + table + " INCLUDING ALL)");
                statement.execute("ALTER TABLE " + table + " ALTER COLUMN " + id + " DROP IDENTITY IF EXISTS");
                statement.execute("CREATE TEMP SEQUENCE teacher_submission_" + table + "_ids START WITH 1000000");
                statement.execute("ALTER TABLE " + table + " ALTER COLUMN " + id
                        + " SET DEFAULT nextval('teacher_submission_" + table + "_ids')");
            }
            statement.execute("SET search_path TO pg_temp");
            statement.execute("""
                    INSERT INTO users VALUES
                      (1,'TEACHER','ACTIVE','Teacher A','a@example.test'),
                      (2,'TEACHER','ACTIVE','Teacher B','b@example.test'),
                      (3,'TEACHER','INACTIVE','Inactive Teacher','inactive@example.test'),
                      (4,'STUDENT','ACTIVE','Student actor','student@example.test'),
                      (5,'EXPERT','ACTIVE','Expert actor','expert@example.test'),
                      (6,'TEACHER','ACTIVE','Empty Teacher','empty@example.test'),
                      (101,'STUDENT','ACTIVE','Alice 100%_','alice@example.test'),
                      (102,'STUDENT','ACTIVE','Bob','bob@example.test'),
                      (103,'STUDENT','INACTIVE','Nguyễn Quang','quang@example.test'),
                      (104,'STUDENT','ACTIVE','Literal '' OR 1=1 --','literal@example.test'),
                      (105,'STUDENT','ACTIVE','Foreign Student','foreign@example.test'),
                      (106,'STUDENT','ACTIVE','Wrong Term','term@example.test')
                    """);
            statement.execute("INSERT INTO subjects VALUES (100,'SWP391','Project'),(200,'PRJ301','Java')");
            statement.execute("INSERT INTO academic_terms VALUES (300,'FALL2026')");
            statement.execute("INSERT INTO subject_terms VALUES (1000,100,300),(2000,200,300)");
            statement.execute("""
                    INSERT INTO subject_classes VALUES (10,1000,1,'A1','Owned','ACTIVE'),
                      (11,1000,1,'A2','Owned other class','ACTIVE'),
                      (20,2000,2,'B1','Foreign','ACTIVE'),
                      (30,1000,3,'INACTIVE','Inactive Teacher class','ACTIVE'),
                      (40,1000,4,'STUDENT','Wrong role class','ACTIVE')
                    """);
            statement.execute("INSERT INTO assignment_templates VALUES (1,'Template'),(2,'Foreign template')");
            statement.execute("""
                    INSERT INTO subject_registrations VALUES (1001,101,1000,'ACTIVE'),
                      (1002,102,1000,'ACTIVE'),(1003,103,1000,'INACTIVE'),(1004,104,1000,'ACTIVE'),
                      (2001,105,2000,'ACTIVE'),(3001,106,2000,'ACTIVE')
                    """);
            statement.execute("""
                    INSERT INTO class_enrollments VALUES (100,1001,10,'ACTIVE'),(101,1002,10,'ACTIVE'),
                      (102,1003,10,'INACTIVE'),(103,1004,10,'ACTIVE'),(200,2001,20,'ACTIVE'),
                      (300,1001,11,'INACTIVE'),(400,3001,10,'ACTIVE')
                    """);
            statement.execute("""
                    INSERT INTO assignments (assignment_id,class_id,template_id,title,assignment_purpose,
                      response_type,instructions,maximum_score,status,available_from,due_at,published_at,cancelled_at)
                    SELECT v.id,v.class_id,1,'Assignment '||v.id,'REGULAR',v.response_type,'Saved instructions',
                      10,v.status,clock_timestamp()-interval '2 days',clock_timestamp()+interval '2 days',
                      CASE WHEN v.status <> 'DRAFT' THEN clock_timestamp()-interval '1 day' END,
                      CASE WHEN v.status = 'CANCELLED' THEN clock_timestamp() END
                    FROM (VALUES (501,10,'OBJECTIVE','PUBLISHED'),(502,11,'ESSAY','DRAFT'),
                      (503,10,'OBJECTIVE','CANCELLED'),(504,10,'ESSAY','DRAFT'),
                      (601,20,'ESSAY','PUBLISHED'),(701,30,'ESSAY','PUBLISHED'),
                      (801,40,'ESSAY','PUBLISHED')) AS v(id,class_id,response_type,status)
                    """);
            statement.execute("""
                    INSERT INTO questions (question_id,assignment_id,question_order,question_text,maximum_points,options)
                    VALUES (901,501,1,'Saved objective question',10,
                      '[{"option_id":11,"option_order":1,"option_text":"Correct","is_correct":true},
                        {"option_id":12,"option_order":2,"option_text":"Alternative","is_correct":false}]'::jsonb),
                      (903,503,1,'Cancelled objective question',10,'[]'::jsonb)
                    """);
            statement.execute("""
                    INSERT INTO submissions (submission_id,assignment_id,enrollment_id,attempt_number,status,
                      started_at,deadline_at,submitted_at,content,submission_link,answer,score,feedback,
                      graded_by,graded_at,released_at)
                    SELECT v.id,v.assignment_id,v.enrollment_id,v.attempt,v.status,
                      '2026-10-11 08:00:00+00'::timestamptz,'2026-10-11 18:00:00+00'::timestamptz,
                      CASE WHEN v.status IN ('SUBMITTED','GRADED','RELEASED')
                        THEN '2026-10-11 09:00:00+00'::timestamptz + v.minutes * interval '1 minute' END,
                      'Saved content '||v.id,'https://example.test/work/'||v.id,
                      '[{"question_id":901,"selected_option_id":12,"awarded_points":0}]'::jsonb,
                      CASE WHEN v.status IN ('GRADED','RELEASED') THEN 8.5 END,
                      CASE WHEN v.status IN ('GRADED','RELEASED') THEN 'Saved feedback' END,
                      CASE WHEN v.status IN ('GRADED','RELEASED') THEN 2 END,
                      CASE WHEN v.status IN ('GRADED','RELEASED') THEN '2026-10-11 12:00:00+00'::timestamptz END,
                      CASE WHEN v.status='RELEASED' THEN '2026-10-11 13:00:00+00'::timestamptz END
                    FROM (VALUES (1,501,100,1,'SUBMITTED',0),(2,501,101,1,'GRADED',0),
                      (3,501,102,1,'RELEASED',60),(4,501,100,2,'EXPIRED',0),
                      (5,501,101,2,'VOID',0),(6,501,102,2,'IN_PROGRESS',0),
                      (7,501,103,1,'SUBMITTED',90),(8,502,300,1,'SUBMITTED',120),
                      (9,601,200,1,'SUBMITTED',120),(10,501,200,1,'SUBMITTED',120),
                      (11,501,400,1,'SUBMITTED',120),(12,503,100,1,'VOID',0),
                      (13,504,100,1,'IN_PROGRESS',0),(14,701,100,1,'SUBMITTED',120),
                      (15,801,100,1,'SUBMITTED',120)) AS v(id,assignment_id,enrollment_id,attempt,status,minutes)
                    """);
        }
    }

    @AfterEach void close() throws SQLException {
        if (connection != null) connection.close();
    }

    private void writableFixture() throws SQLException {
        connection.setAutoCommit(true);
        connection.setReadOnly(false);
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
    }

    private TeacherSubmissionService service() {
        return service(new TeacherSubmissionDAO(), new AssignmentDAO());
    }

    private TeacherSubmissionService service(TeacherSubmissionDAO dao, AssignmentDAO assignments) {
        Connection borrowed = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("close")) return null;
                    try { return method.invoke(connection, arguments); }
                    catch (InvocationTargetException ex) { throw ex.getCause(); }
                });
        return new TeacherSubmissionService(dao, assignments, () -> {
            writableFixture();
            return borrowed;
        });
    }

    private List<Long> ids(String search, String status) {
        return service().listPage(1,501,TeacherSubmissionFilter.from(search,status)).submissions().stream()
                .map(row -> row.submission().submissionId()).toList();
    }

    private void execute(String sql) throws SQLException {
        writableFixture();
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private String snapshot() throws SQLException {
        writableFixture();
        StringBuilder snapshot = new StringBuilder();
        for (String table : List.of("assignments","questions","submissions","class_enrollments",
                "subject_registrations","users","subject_classes")) {
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "SELECT jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text FROM " + table + " t")) {
                rows.next(); snapshot.append(table).append(':').append(rows.getString(1)).append('\n');
            }
        }
        return snapshot.toString();
    }

    @Test void filterNormalizesAllStatusesAndUnicodeCodePointLength() {
        assertEquals("Nguyễn", TeacherSubmissionFilter.from("  Nguyễn  "," all ").search());
        assertNull(TeacherSubmissionFilter.from(null,null).status());
        assertNull(TeacherSubmissionFilter.from("", " ").status());
        for (SubmissionStatus status : SubmissionStatus.values())
            assertEquals(status,TeacherSubmissionFilter.from(null," "+status.name().toLowerCase()+" ").status());
        assertEquals(200, TeacherSubmissionFilter.from("😀".repeat(200),null).search().codePointCount(0,400));
        assertThrows(IllegalArgumentException.class,()->TeacherSubmissionFilter.from("😀".repeat(201),null));
        assertThrows(IllegalArgumentException.class,()->TeacherSubmissionFilter.from(null,"DELETED"));
    }

    @Test void validatesActorIdsAndFilterBeforeOpeningDatabase() {
        AtomicInteger openings = new AtomicInteger();
        var service = new TeacherSubmissionService(new TeacherSubmissionDAO(),new AssignmentDAO(),()-> {
            openings.incrementAndGet(); throw new SQLException("Must not connect");
        });
        assertThrows(SecurityException.class,()->service.listPage(0,501,TeacherSubmissionFilter.from(null,null)));
        assertThrows(IllegalArgumentException.class,()->service.listPage(1,0,TeacherSubmissionFilter.from(null,null)));
        assertThrows(NullPointerException.class,()->service.listPage(1,501,null));
        assertThrows(IllegalArgumentException.class,()->service.detailPage(1,501,-1));
        assertEquals(0,openings.get());
    }

    @Test void databaseFailuresRemainGenericAtServiceBoundary() {
        var service = new TeacherSubmissionService(new TeacherSubmissionDAO(),new AssignmentDAO(),()-> {
            throw new SQLException("Private JDBC connection failure");
        });
        var error=assertThrows(IllegalStateException.class,()->service.listPage(1,501,TeacherSubmissionFilter.from(null,null)));
        assertEquals("Submissions could not be loaded.",error.getMessage());
        assertInstanceOf(SQLException.class,error.getCause());
    }

    @Test @Tag("database") void ownedListOrdersSubmittedTimesThenIdsAndNullTimesLast() {
        assertEquals(List.of(7L,3L,2L,1L,6L,5L,4L),ids(null,null));
        var page=service().listPage(1,501,TeacherSubmissionFilter.from(null,null));
        assertEquals("A1",page.assignment().classCode());
        assertEquals(7,page.submissions().size());
        assertThrows(UnsupportedOperationException.class,()->page.submissions().clear());
    }

    @Test @Tag("database") void searchesStudentNameOrEmailLiterallyWithCaseInsensitiveMatching() {
        assertEquals(List.of(1L,4L),ids("  ALICE  ",null));
        assertEquals(List.of(3L,6L),ids("quang@EXAMPLE.test",null));
        assertEquals(List.of(1L,4L),ids("100%_",null));
        assertEquals(List.of(1L,4L),ids("%",null));
        assertEquals(List.of(1L,4L),ids("_",null));
        assertEquals(List.of(7L),ids("' OR 1=1 --",null));
        assertEquals(List.of(),ids("does not exist",null));
    }

    @Test @Tag("database") void filtersAllSixStatusesAndCombinesWithSearch() {
        assertEquals(List.of(6L),ids(null,"IN_PROGRESS"));
        assertEquals(List.of(7L,1L),ids(null,"SUBMITTED"));
        assertEquals(List.of(2L),ids(null,"GRADED"));
        assertEquals(List.of(3L),ids(null,"RELEASED"));
        assertEquals(List.of(4L),ids(null,"EXPIRED"));
        assertEquals(List.of(5L),ids(null,"VOID"));
        assertEquals(List.of(1L),ids("alice"," submitted "));
        assertEquals(ids(null,null),ids(" "," ALL "));
    }

    @Test @Tag("database") void foreignAndMissingAssignmentsHaveSameNotFoundBehavior() {
        var service=service();
        var foreign=assertThrows(NoSuchElementException.class,()->service.listPage(1,601,TeacherSubmissionFilter.from(null,null)));
        var missing=assertThrows(NoSuchElementException.class,()->service.listPage(1,999,TeacherSubmissionFilter.from(null,null)));
        assertEquals(foreign.getMessage(),missing.getMessage());
        assertEquals(9,service.detailPage(2,601,9).row().submission().submissionId());
        assertThrows(NoSuchElementException.class,()->service.listPage(6,501,TeacherSubmissionFilter.from(null,null)));
    }

    @Test @Tag("database") void requiresCurrentActiveTeacherRole() throws SQLException {
        for(long actor:List.of(3L,4L,5L,999L)) {
            assertThrows(SecurityException.class,()->service().listPage(actor,501,TeacherSubmissionFilter.from(null,null)));
            assertThrows(SecurityException.class,()->service().detailPage(actor,501,1));
        }
        execute("UPDATE users SET account_status='INACTIVE' WHERE user_id=1");
        assertThrows(SecurityException.class,()->service().listPage(1,501,TeacherSubmissionFilter.from(null,null)));
    }

    @Test @Tag("database") void reassignmentImmediatelyChangesWhoCanReadOnNextRequest() throws SQLException {
        assertEquals(7,service().listPage(1,501,TeacherSubmissionFilter.from(null,null)).submissions().size());
        execute("UPDATE subject_classes SET teacher_id=2 WHERE class_id=10");
        assertThrows(NoSuchElementException.class,()->service().detailPage(1,501,1));
        assertEquals(1,service().detailPage(2,501,1).row().submission().submissionId());
    }

    @Test @Tag("database") void preservesInactiveHistoricalStudentEnrollmentRegistrationAndClass() throws SQLException {
        execute("UPDATE subject_classes SET status='INACTIVE' WHERE class_id=10");
        var detail=service().detailPage(1,501,3);
        assertEquals("Nguyễn Quang",detail.row().studentName());
        assertEquals("quang@example.test",detail.row().studentEmail());
        assertEquals(SubmissionStatus.RELEASED,detail.row().submission().status());
        assertEquals("Teacher B",detail.row().graderName());
        assertTrue(ids(null,null).contains(6L));
    }

    @Test @Tag("database") void rejectsCrossAssignmentCrossClassAndWrongTermSubmissionIds() {
        for(long submission:List.of(8L,9L,10L,11L,999L))
            assertThrows(NoSuchElementException.class,()->service().detailPage(1,501,submission));
        assertEquals(8,service().detailPage(1,502,8).row().submission().submissionId());
        assertFalse(ids(null,null).contains(10L));
        assertFalse(ids(null,null).contains(11L));
    }

    @Test @Tag("database") void includesCancelledAndDraftAssignmentHistoryWithoutMutation() {
        var cancelled=service().listPage(1,503,TeacherSubmissionFilter.from(null,null));
        assertEquals(1,cancelled.submissions().size());
        assertEquals(SubmissionStatus.VOID,cancelled.submissions().get(0).submission().status());
        assertEquals(13,service().detailPage(1,504,13).row().submission().submissionId());
    }

    @Test @Tag("database") void detailRetainsSavedWorkEvaluationAndObjectiveQuestionContext() {
        var detail=service().detailPage(1,501,3);
        var submission=detail.row().submission();
        assertEquals(103,detail.row().studentId());
        assertEquals("Saved content 3",submission.content());
        assertEquals("https://example.test/work/3",submission.submissionLink());
        assertTrue(submission.answer().contains("selected_option_id"));
        assertEquals("8.50",submission.score().toPlainString());
        assertEquals("Saved feedback",submission.feedback());
        assertEquals(2L,submission.gradedBy());
        assertNotNull(submission.gradedAt());
        assertNotNull(submission.releasedAt());
        assertEquals(901,detail.assignment().questions().get(0).questionId());
        assertTrue(detail.assignment().questions().get(0).options().contains("option_id"));
        assertTrue(service().detailPage(1,502,8).assignment().questions().isEmpty());
    }

    @Test @Tag("database") void listAndDetailLeaveEveryAcademicRowUnchanged() throws SQLException {
        String before=snapshot();
        ids(null,null);ids("alice","SUBMITTED");
        service().detailPage(1,501,3);service().detailPage(1,503,12);
        assertEquals(before,snapshot());
    }

    @Test @Tag("database") void allQueriesRunInsideOneReadOnlyRepeatableReadTransaction() {
        List<Connection> seen=new ArrayList<>();
        var dao=new TeacherSubmissionDAO() {
            @Override public List<vn.edu.fpt.ams.dto.TeacherSubmissionRow> findForTeacher(Connection c,
                    long teacher,long assignment,TeacherSubmissionFilter filter) throws SQLException {
                assertTrue(c.isReadOnly());
                assertFalse(c.getAutoCommit());
                assertEquals(Connection.TRANSACTION_REPEATABLE_READ,c.getTransactionIsolation());
                seen.add(c);return super.findForTeacher(c,teacher,assignment,filter);
            }
        };
        service(dao,new AssignmentDAO()).listPage(1,501,TeacherSubmissionFilter.from(null,null));
        assertEquals(1,seen.size());
    }
}
