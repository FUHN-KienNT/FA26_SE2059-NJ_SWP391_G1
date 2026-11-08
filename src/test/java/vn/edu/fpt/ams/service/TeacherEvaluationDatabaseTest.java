package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import vn.edu.fpt.ams.dao.AssignmentDAO;
import vn.edu.fpt.ams.dao.TeacherEvaluationDAO;
import vn.edu.fpt.ams.dao.TeacherSubmissionDAO;
import vn.edu.fpt.ams.dto.SubmissionEvaluation;
import vn.edu.fpt.ams.dto.SubmissionEvaluation.Mark;
import vn.edu.fpt.ams.model.SubmissionStatus;
import vn.edu.fpt.ams.util.DBConnection;

/** PostgreSQL checks opt in and mutate only connection-local TEMP fixtures. */
class TeacherEvaluationDatabaseTest {
    private Connection connection;
    private static final String RUBRIC =
            """
            [{"task_order":1,"title":"Explain","maximum_points":5,"requirements":[
              {"requirement_order":1,"title":"Reason","maximum_points":2,"answer_type":"TEXT"},
              {"requirement_order":2,"title":"Diagram","maximum_points":2,"answer_type":"DIAGRAM"}]},
             {"task_order":2,"title":"Legacy task","maximum_points":3}]
            """;

    @BeforeEach
    void fixture(TestInfo info) throws SQLException {
        if (!info.getTags().contains("database")) return;

        Assumptions.assumeTrue(
                Boolean.getBoolean("assignment.db.tests"),
                "Enable assignment.db.tests for PostgreSQL checks");
        connection = DBConnection.getConnection();

        try (var statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TEMP TABLE users(user_id BIGINT PRIMARY KEY,role TEXT,account_status TEXT,full_name TEXT,email TEXT)");
            statement.execute(
                    "CREATE TEMP TABLE subjects(subject_id BIGINT PRIMARY KEY,subject_code TEXT,subject_name TEXT)");
            statement.execute(
                    "CREATE TEMP TABLE academic_terms(term_id BIGINT PRIMARY KEY,term_code TEXT)");
            statement.execute(
                    "CREATE TEMP TABLE subject_terms(subject_term_id BIGINT PRIMARY KEY,subject_id BIGINT,term_id BIGINT)");
            statement.execute(
                    "CREATE TEMP TABLE subject_classes(class_id BIGINT PRIMARY KEY,subject_term_id BIGINT,teacher_id BIGINT,class_code TEXT,class_name TEXT,status TEXT)");
            statement.execute(
                    "CREATE TEMP TABLE assignment_templates(template_id BIGINT PRIMARY KEY,title TEXT)");
            statement.execute(
                    "CREATE TEMP TABLE subject_registrations(registration_id BIGINT PRIMARY KEY,student_id BIGINT,subject_term_id BIGINT,status TEXT)");
            statement.execute(
                    "CREATE TEMP TABLE class_enrollments(enrollment_id BIGINT PRIMARY KEY,registration_id BIGINT,class_id BIGINT,status TEXT)");
            statement.execute(
                    "CREATE TEMP TABLE submission_content_snapshots(submission_id BIGINT PRIMARY KEY,assignment_data JSONB,questions_data JSONB)");

            for (String table : List.of("assignments", "questions", "submissions"))
                statement.execute(
                        "CREATE TEMP TABLE "
                                + table
                                + " (LIKE public."
                                + table
                                + " INCLUDING ALL)");

            statement.execute("CREATE TEMP TABLE submission_evaluation_history (LIKE public.submission_evaluation_history INCLUDING ALL)");
            statement.execute("SET search_path TO pg_temp");
            statement.execute(
                    "ALTER TABLE submissions ADD COLUMN IF NOT EXISTS evaluation JSONB NOT NULL DEFAULT '{\"format\":\"ams-evaluation-v1\",\"marks\":[]}'::jsonb");
            statement.execute(
                    "ALTER TABLE submissions ADD COLUMN IF NOT EXISTS evaluation_revision BIGINT NOT NULL DEFAULT 0");
            statement.execute(
                    "INSERT INTO users VALUES(1,'TEACHER','ACTIVE','Teacher A','a@example.test'),(2,'TEACHER','ACTIVE','Teacher B','b@example.test'),(3,'TEACHER','INACTIVE','Inactive','i@example.test'),(4,'STUDENT','ACTIVE','Student','s@example.test')");
            statement.execute("INSERT INTO subjects VALUES(1,'SWP','Project')");
            statement.execute("INSERT INTO academic_terms VALUES(1,'FALL2026')");
            statement.execute("INSERT INTO subject_terms VALUES(1,1,1),(2,1,1)");
            statement.execute(
                    "INSERT INTO subject_classes VALUES(10,1,1,'A','Owned','ACTIVE'),(20,1,2,'B','Foreign','ACTIVE')");
            statement.execute("INSERT INTO assignment_templates VALUES(1,'Essay')");
            statement.execute(
                    "INSERT INTO subject_registrations VALUES(1,4,1,'ACTIVE'),(2,4,2,'ACTIVE')");
            statement.execute(
                    "INSERT INTO class_enrollments VALUES(1,1,10,'ACTIVE'),(2,1,20,'ACTIVE'),(3,2,10,'ACTIVE')");
            statement.execute(
                    """
                    INSERT INTO assignments(assignment_id,class_id,template_id,title,assignment_purpose,response_type,
                        instructions,scope_text,task_checklist,maximum_score,status,available_from,due_at,published_at)
                    VALUES(100,10,1,'Essay','REGULAR','ESSAY','Explain','Course','[]',10,'PUBLISHED',
                        now()-interval '3 days',now()+interval '1 day',now()-interval '3 days'),
                        (200,20,1,'Foreign','REGULAR','ESSAY','Explain','Course','[]',10,'PUBLISHED',
                        now()-interval '3 days',now()+interval '1 day',now()-interval '3 days')
                    """);
            statement.execute("UPDATE assignments SET closed_at=CURRENT_TIMESTAMP");
            statement.execute(
                    """
                    INSERT INTO submissions(submission_id,assignment_id,enrollment_id,attempt_number,status,
                        started_at,deadline_at,submitted_at,content,answer)
                    VALUES(1000,100,1,1,'SUBMITTED',now()-interval '2 days',now()+interval '1 day',now()-interval '1 day','Legacy plain text answer','[]'),
                        (2000,200,2,1,'SUBMITTED',now()-interval '2 days',now()+interval '1 day',now()-interval '1 day','Foreign','[]'),
                        (1001,100,2,1,'SUBMITTED',now()-interval '2 days',now()+interval '1 day',now()-interval '1 day','Wrong class','[]'),
                        (1002,100,3,1,'SUBMITTED',now()-interval '2 days',now()+interval '1 day',now()-interval '1 day','Wrong term','[]')
                    """);
        }

        try (var statement =
                connection.prepareStatement(
                        "UPDATE assignments SET task_checklist=CAST(? AS jsonb)")) {
            statement.setString(1, RUBRIC);
            statement.executeUpdate();
        }
    }

    @AfterEach
    void cleanup() throws SQLException {
        if (connection != null) connection.close();
    }

    private void writable() throws SQLException {
        connection.setAutoCommit(true);
        connection.setReadOnly(false);
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
    }

    private TeacherEvaluationService service() {
        return service(new TeacherEvaluationDAO(), new AssignmentDAO());
    }

    private TeacherEvaluationService service(TeacherEvaluationDAO dao, AssignmentDAO assignments) {
        Connection borrowed =
                (Connection)
                        Proxy.newProxyInstance(
                                Connection.class.getClassLoader(),
                                new Class<?>[] {Connection.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("close")) return null;

                                    try {
                                        return method.invoke(connection, args);
                                    } catch (InvocationTargetException ex) {
                                        throw ex.getCause();
                                    }
                                });

        return new TeacherEvaluationService(
                dao,
                new TeacherSubmissionDAO(),
                assignments,
                () -> {
                    writable();

                    return borrowed;
                });
    }

    private void execute(String sql) throws SQLException {
        writable();

        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String snapshot() throws SQLException {
        writable();
        var result = new StringBuilder();

        for (String table :
                List.of(
                        "submissions",
                        "submission_evaluation_history",
                        "submission_content_snapshots",
                        "assignments",
                        "class_enrollments",
                        "subject_registrations"))
            try (var statement = connection.createStatement();
                    var rows =
                            statement.executeQuery(
                                    "SELECT jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text FROM "
                                            + table
                                            + " t")) {
                rows.next();
                result.append(table).append(':').append(rows.getString(1));
            }

        return result.toString();
    }

    private List<Mark> complete() {
        return List.of(
                new Mark(1, 1, new BigDecimal("1.25"), "Text feedback"),
                new Mark(1, 2, new BigDecimal("2"), "Diagram feedback"),
                new Mark(2, 1, new BigDecimal("3"), ""));
    }

    private void apply(String action, long revision, List<Mark> marks, String feedback) {
        service().apply(1, 100, 1000, action, revision, marks, feedback);
    }

    @Test
    void validatesIdentityActionAndRevisionWithoutOpeningConnection() {
        AtomicInteger opened = new AtomicInteger();
        var service =
                new TeacherEvaluationService(
                        new TeacherEvaluationDAO(),
                        new TeacherSubmissionDAO(),
                        new AssignmentDAO(),
                        () -> {
                            opened.incrementAndGet();
                            throw new SQLException("Must not connect");
                        });

        assertThrows(SecurityException.class, () -> service.page(0, 100, 1000));
        assertThrows(IllegalArgumentException.class, () -> service.page(1, 0, 1000));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.apply(1, 100, 1000, "other", 0, List.of(), ""));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.apply(1, 100, 1000, "saveEvaluation", -1, List.of(), ""));
        assertEquals(0, opened.get());
    }

    @Test
    @Tag("database")
    void draftCompleteReopenAndReleasePreserveAcceptedWorkAndUseStoredReleaseData() {
        var original = service().page(1, 100, 1000).detail().row().submission();

        apply(
                "saveEvaluation",
                0,
                List.of(new Mark(1, 1, new BigDecimal("0.50"), "Draft")),
                "Draft overall");
        var draft = service().page(1, 100, 1000);

        assertEquals(SubmissionStatus.SUBMITTED, draft.detail().row().submission().status());
        assertNull(draft.detail().row().submission().gradedAt());
        assertNull(draft.detail().row().submission().releasedAt());
        assertEquals(1, draft.evaluation().revision());
        assertEquals(new BigDecimal("0.50"), draft.evaluation().total());
        apply("completeEvaluation", 1, complete(), "Overall <script>alert(1)</script>");
        var graded = service().page(1, 100, 1000);

        assertEquals(SubmissionStatus.GRADED, graded.detail().row().submission().status());
        assertNotNull(graded.detail().row().submission().gradedAt());
        assertNull(graded.detail().row().submission().releasedAt());
        apply("reopenEvaluation", 2, null, "ignored");
        var reopened = service().page(1, 100, 1000);

        assertNull(reopened.detail().row().submission().gradedAt());
        assertEquals(graded.evaluation().marks(), reopened.evaluation().marks());
        assertEquals(graded.evaluation().feedback(), reopened.evaluation().feedback());
        apply("completeEvaluation", 3, complete(), "Final feedback");
        var completed = service().page(1, 100, 1000);

        apply(
                "releaseEvaluation",
                4,
                List.of(new Mark(99, 99, BigDecimal.ZERO, "ignored")),
                "ignored");
        var released = service().page(1, 100, 1000);

        assertEquals(SubmissionStatus.RELEASED, released.detail().row().submission().status());
        assertNotNull(released.detail().row().submission().releasedAt());
        assertEquals(
                completed.detail().row().submission().gradedAt(),
                released.detail().row().submission().gradedAt());
        assertEquals(new BigDecimal("6.25"), released.detail().row().submission().score());
        assertEquals("Final feedback", released.evaluation().feedback());
        assertEquals(5, released.evaluation().revision());
        assertEquals(original.content(), released.detail().row().submission().content());
        assertEquals(original.answer(), released.detail().row().submission().answer());
        assertEquals(original.submittedAt(), released.detail().row().submission().submittedAt());
        assertEquals(original.startedAt(), released.detail().row().submission().startedAt());
        assertEquals(original.deadlineAt(), released.detail().row().submission().deadlineAt());
    }

    @Test
    @Tag("database")
    void incompleteOutOfRangeAndForgedMarksRollbackEntireRequest() throws SQLException {
        for (var marks :
                List.of(
                        List.of(new Mark(1, 1, BigDecimal.ONE, "")),
                        List.of(new Mark(1, 1, new BigDecimal("2.01"), "")),
                        List.of(new Mark(9, 9, BigDecimal.ZERO, "")))) {
            String before = snapshot();

            assertThrows(
                    IllegalArgumentException.class,
                    () -> apply("completeEvaluation", 0, marks, "attempt"));
            assertEquals(before, snapshot());
        }
    }

    @Test
    @Tag("database")
    void staleTabsAndRepeatedPostsCannotOverwriteOrChangeTimestamps() throws SQLException {
        apply("saveEvaluation", 0, complete(), "first");
        String saved = snapshot();

        assertThrows(
                ConcurrentModificationException.class,
                () -> apply("saveEvaluation", 0, List.of(), "second tab"));
        assertThrows(
                ConcurrentModificationException.class,
                () -> apply("completeEvaluation", 0, complete(), "stale completion"));
        assertEquals(saved, snapshot());
        apply("completeEvaluation", 1, complete(), "done");
        String completed = snapshot();

        assertThrows(
                ConcurrentModificationException.class,
                () -> apply("completeEvaluation", 1, complete(), "done"));
        assertEquals(completed, snapshot());
        apply("releaseEvaluation", 2, null, null);
        String released = snapshot();

        for (String action :
                List.of(
                        "saveEvaluation",
                        "completeEvaluation",
                        "reopenEvaluation",
                        "releaseEvaluation")) {
            assertThrows(
                    IllegalArgumentException.class, () -> apply(action, 3, complete(), "change"));
            assertEquals(released, snapshot());
        }

        assertThrows(
                ConcurrentModificationException.class,
                () -> apply("releaseEvaluation", 2, null, null));
        assertEquals(released, snapshot());
    }

    @Test
    @Tag("database")
    void ownershipRoleClassAndMatchingAcademicContextAreCheckedEveryRequest() throws SQLException {
        String before = snapshot();

        for (long actor : List.of(3L, 4L, 999L))
            assertThrows(
                    SecurityException.class,
                    () -> service().apply(actor, 100, 1000, "saveEvaluation", 0, complete(), ""));

        for (long id : List.of(1001L, 1002L, 2000L, 9999L))
            assertThrows(
                    NoSuchElementException.class,
                    () -> service().apply(1, 100, id, "saveEvaluation", 0, complete(), ""));

        assertThrows(
                NoSuchElementException.class,
                () -> service().apply(2, 100, 1000, "saveEvaluation", 0, complete(), ""));
        assertEquals(before, snapshot());
        execute("UPDATE subject_classes SET teacher_id=2 WHERE class_id=10");
        assertThrows(
                NoSuchElementException.class, () -> apply("saveEvaluation", 0, complete(), ""));
        service().apply(2, 100, 1000, "saveEvaluation", 0, complete(), "new owner");
        assertEquals(2L, service().page(2, 100, 1000).detail().row().submission().gradedBy());
        execute("UPDATE users SET account_status='INACTIVE' WHERE user_id=2");
        assertThrows(
                SecurityException.class,
                () -> service().apply(2, 100, 1000, "completeEvaluation", 1, complete(), ""));
    }

    @Test
    @Tag("database")
    void unfinishedVoidedAndExpiredAttemptsCannotBeGraded() throws SQLException {
        for (String status : List.of("IN_PROGRESS", "VOID", "EXPIRED")) {
            execute(
                    "UPDATE submissions SET status='"
                            + status
                            + "',submitted_at=NULL WHERE submission_id=1000");
            String before = snapshot();

            assertThrows(NoSuchElementException.class, () -> service().page(1, 100, 1000));
            assertThrows(
                    NoSuchElementException.class, () -> apply("saveEvaluation", 0, complete(), ""));
            assertEquals(before, snapshot());
        }
    }

    @Test
    @Tag("database")
    void frozenRubricSurvivesChangedCurrentContentAndHistoricalMembership() throws SQLException {
        apply("saveEvaluation", 0, complete(), "legacy plain text can be graded");
        execute(
                "UPDATE assignments SET response_type='OBJECTIVE',task_checklist='[]',maximum_score=1 WHERE assignment_id=100");
        execute("UPDATE class_enrollments SET status='INACTIVE' WHERE enrollment_id=1");
        execute("UPDATE subject_registrations SET status='INACTIVE' WHERE registration_id=1");
        execute("UPDATE subject_classes SET status='INACTIVE' WHERE class_id=10");
        execute(
                "UPDATE assignments SET status='CANCELLED',cancelled_at=now() WHERE assignment_id=100");
        var page = service().page(1, 100, 1000);

        assertNull(page.blockedReason());
        assertEquals(2, page.questions().size());
        apply("completeEvaluation", 1, complete(), "historical work");
        apply("releaseEvaluation", 2, null, null);
        assertEquals(new BigDecimal("6.25"), service().page(1, 100, 1000).evaluation().total());
    }

    @Test
    @Tag("database")
    void invalidRubricBlocksPageAndMutationWithoutRepairingIt() throws SQLException {
        execute("UPDATE assignments SET task_checklist='[]' WHERE assignment_id=100");
        String before = snapshot();

        assertNotNull(service().page(1, 100, 1000).blockedReason());
        assertThrows(
                IllegalArgumentException.class, () -> apply("saveEvaluation", 0, complete(), ""));
        assertEquals(before, snapshot());
    }

    @Test
    @Tag("database")
    void databaseRejectsInvalidEvaluationEnvelopeAndNegativeRevision() throws SQLException {
        for (String json :
                List.of(
                        "[]",
                        "{}",
                        "{\"format\":\"ams-evaluation-v1\"}",
                        "{\"format\":\"ams-evaluation-v1\",\"marks\":{}}")) {
            writable();

            try (var update =
                    connection.prepareStatement(
                            "UPDATE submissions SET evaluation=CAST(? AS jsonb) WHERE submission_id=1000")) {
                update.setString(1, json);
                assertThrows(SQLException.class, update::executeUpdate);
            }
        }

        assertThrows(
                SQLException.class,
                () ->
                        execute(
                                "UPDATE submissions SET evaluation_revision=-1 WHERE submission_id=1000"));
        assertEquals(0, service().page(1, 100, 1000).evaluation().revision());
    }

    @Test
    @Tag("database")
    void releaseRevalidatesCompletenessAndStoredTotal() throws SQLException {
        apply("completeEvaluation", 0, complete(), "done");
        execute(
                "UPDATE submissions SET evaluation='{\"format\":\"ams-evaluation-v1\",\"marks\":[]}' WHERE submission_id=1000");
        String before = snapshot();

        assertThrows(
                IllegalArgumentException.class, () -> apply("releaseEvaluation", 1, null, null));
        assertEquals(before, snapshot());
        apply("reopenEvaluation", 1, null, null);
        apply("completeEvaluation", 2, complete(), "done again");
        execute("UPDATE submissions SET score=9 WHERE submission_id=1000");
        before = snapshot();
        assertThrows(
                IllegalArgumentException.class, () -> apply("releaseEvaluation", 3, null, null));
        assertEquals(before, snapshot());
    }

    @Test
    @Tag("database")
    void writeFailureRollsBackMarksStatusAndLegacySnapshot() throws SQLException {
        String before = snapshot();
        var failure =
                new TeacherEvaluationDAO() {
                    @Override
                    public void save(
                            Connection c,
                            long id,
                            long teacher,
                            SubmissionEvaluation evaluation,
                            boolean complete)
                            throws SQLException {
                        super.save(c, id, teacher, evaluation, complete);
                        throw new SQLException("Injected failure after UPDATE");
                    }
                };

        assertThrows(
                IllegalStateException.class,
                () ->
                        service(failure, new AssignmentDAO())
                                .apply(1, 100, 1000, "completeEvaluation", 0, complete(), ""));
        assertEquals(before, snapshot());
    }

    @Test
    @Tag("database")
    void locksMatchAssignmentThenClassThenSubmissionOrderInOneTransaction() {
        List<String> locks = new ArrayList<>();
        var assignments =
                new AssignmentDAO() {
                    @Override
                    public boolean lockActiveTeacher(Connection c, long id) throws SQLException {
                        locks.add("teacher");

                        return super.lockActiveTeacher(c, id);
                    }

                    @Override
                    public vn.edu.fpt.ams.model.Assignment lockOwnedAssignment(
                            Connection c, long teacher, long id) throws SQLException {
                        locks.add("assignment");

                        return super.lockOwnedAssignment(c, teacher, id);
                    }
                };
        var dao =
                new TeacherEvaluationDAO() {
                    @Override
                    public void lockClass(Connection c, long teacher, long id) throws SQLException {
                        locks.add("class");
                        super.lockClass(c, teacher, id);
                    }

                    @Override
                    public void lockSubmission(Connection c, long teacher, long assignment, long id)
                            throws SQLException {
                        assertFalse(c.getAutoCommit());
                        assertFalse(c.isReadOnly());
                        locks.add("submission");
                        super.lockSubmission(c, teacher, assignment, id);
                    }
                };

        service(dao, assignments).apply(1, 100, 1000, "saveEvaluation", 0, complete(), "");
        assertEquals(List.of("teacher", "assignment", "class", "submission"), locks);
    }

    @Test
    @Tag("database")
    void futureDeadlineBlocksEveryGradingActionUntilPermanentlyClosed() throws SQLException {
        execute("UPDATE assignments SET closed_at=NULL WHERE assignment_id=100");
        String before = snapshot();

        assertTrue(service().page(1, 100, 1000).blockedReason().contains("Chỉ được chấm"));

        for (String action : List.of("saveEvaluation", "completeEvaluation", "publishEvaluation"))
            assertThrows(IllegalArgumentException.class, () -> apply(action, 0, complete(), "blocked"));

        assertEquals(before, snapshot());
        execute("UPDATE assignments SET closed_at=clock_timestamp() WHERE assignment_id=100");
        assertNull(service().page(1, 100, 1000).blockedReason());
        apply("completeEvaluation", 0, complete(), "allowed after closure");

        // A frozen snapshot containing closed_at must not authorize a changed current assignment.
        execute("UPDATE assignments SET closed_at=NULL WHERE assignment_id=100");
        assertNotNull(service().page(1, 100, 1000).blockedReason());
        assertThrows(IllegalArgumentException.class, () -> apply("releaseEvaluation", 1, null, null));
    }

    @Test
    @Tag("database")
    void deadlinePassedAllowsAtomicSaveAndPublicationWithoutManualClosure() throws SQLException {
        execute("UPDATE assignments SET closed_at=NULL,due_at=clock_timestamp()-interval '1 second' WHERE assignment_id=100");
        assertNull(service().page(1, 100, 1000).blockedReason());
        apply("publishEvaluation", 0, complete(), "published directly");
        var page = service().page(1, 100, 1000);

        assertEquals(SubmissionStatus.RELEASED, page.detail().row().submission().status());
        assertEquals(new BigDecimal("6.25"), page.detail().row().submission().score());
        assertEquals(2, page.evaluation().revision());
        assertEquals("published directly", page.evaluation().feedback());
        assertEquals(1, page.history().total());
        assertEquals("publishEvaluation", page.history().entries().get(0).action());
    }

    @Test
    @Tag("database")
    void releasedAmendmentRequiresReasonAndCanRestoreMatchingPriorEvaluation() throws SQLException {
        apply("publishEvaluation", 0, complete(), "original overall");
        var revised = List.of(
                new Mark(1, 1, new BigDecimal("1.75"), "AI text", true),
                new Mark(1, 2, new BigDecimal("2"), "Diagram", true),
                new Mark(2, 1, new BigDecimal("3"), "Other", false));
        String before = snapshot();

        assertThrows(IllegalArgumentException.class, () -> service().apply(1, 100, 1000,
                "reviseReleasedEvaluation", 2, revised, "new", "   ", true, 0));
        assertThrows(IllegalArgumentException.class, () -> service().apply(1, 100, 1000,
                "reviseReleasedEvaluation", 2, revised, "new", "x".repeat(2001), true, 0));
        assertEquals(before, snapshot());

        service().apply(1, 100, 1000, "reviseReleasedEvaluation", 2, revised, "AI overall",
                "Đối chiếu lại tiêu chí chấm.", true, 0);
        var changed = service().page(1, 100, 1000);

        assertEquals(SubmissionStatus.RELEASED, changed.detail().row().submission().status());
        assertEquals(new BigDecimal("6.75"), changed.evaluation().total());
        assertTrue(changed.evaluation().feedbackAi());
        assertTrue(changed.evaluation().mark(1, 1).aiGenerated());
        assertEquals(2, changed.history().total());
        var change = changed.history().entries().get(0);
        assertEquals("Teacher A", change.actor());
        assertEquals("Đối chiếu lại tiêu chí chấm.", change.reason());
        assertEquals(new BigDecimal("6.25"), change.previousScore());
        assertEquals(new BigDecimal("6.75"), change.nextScore());

        assertThrows(IllegalArgumentException.class, () -> service().apply(1, 100, 1000,
                "restoreEvaluation", 3, null, null, "", false, change.id()));
        service().apply(1, 100, 1000, "restoreEvaluation", 3, null, null,
                "Khôi phục sau khi đối chiếu.", false, change.id());
        var restored = service().page(1, 100, 1000);

        assertEquals(SubmissionStatus.RELEASED, restored.detail().row().submission().status());
        assertEquals(new BigDecimal("6.25"), restored.evaluation().total());
        assertEquals("original overall", restored.evaluation().feedback());
        assertFalse(restored.evaluation().feedbackAi());
        assertEquals(3, restored.history().total());
    }

    @Test
    @Tag("database")
    void historyOfPreviousSubmittedWorkCannotBeRestoredIntoAReplacementSubmission() throws SQLException {
        apply("saveEvaluation", 0, complete(), "old work");
        long historyId = service().page(1, 100, 1000).history().entries().get(0).id();
        execute("""
                INSERT INTO submissions(submission_id,assignment_id,enrollment_id,attempt_number,status,
                    started_at,deadline_at,submitted_at,content,answer)
                SELECT 1003,assignment_id,enrollment_id,2,'SUBMITTED',started_at,deadline_at,
                    submitted_at,'Replacement content','[]' FROM submissions WHERE submission_id=1000
                """);
        String before = snapshot();

        assertThrows(NoSuchElementException.class, () -> service().apply(1, 100, 1003,
                "restoreEvaluation", 0, null, null, "Should not restore", false, historyId));
        assertEquals(before, snapshot());
    }

    @Test
    @Tag("database")
    void gradeJournalUsesTenRowsPerPageAndClampsPageAfterFilteringByStudent() {
        for (int i = 0; i < 11; i++) apply("saveEvaluation", i, complete(), "revision " + i);
        var first = service().page(1, 100, 1000, 1).history();
        var second = service().page(1, 100, 1000, 2).history();

        assertEquals(11, first.total());
        assertEquals(10, first.entries().size());
        assertEquals(1, second.entries().size());
        assertTrue(first.entries().get(0).id() > second.entries().get(0).id());
        assertEquals(2, service().page(1, 100, 1000, 999).history().page());
    }

    @Test
    void evaluationRoundTripKeepsAiProvenanceAndReadsLegacyMarksWithoutIt() {
        var original = new SubmissionEvaluation(7,
                List.of(new Mark(1, 1, new BigDecimal("0.50"), "Nhận xét AI", true)), "Chung AI", true);
        var read = SubmissionEvaluation.read(original.json(), 7, original.feedback());

        assertEquals(original, read);
        var legacy = SubmissionEvaluation.read("""
                {"format":"ams-evaluation-v1","marks":[{"task_order":1,"requirement_order":1,
                  "score":0,"feedback":"Old"}]}
                """, 0, "Old overall");
        assertFalse(legacy.feedbackAi());
        assertFalse(legacy.mark(1, 1).aiGenerated());
        assertThrows(IllegalArgumentException.class, () -> SubmissionEvaluation.read("""
                {"format":"ams-evaluation-v1","feedback_ai":"true","marks":[]}
                """, 0, ""));
    }
}
