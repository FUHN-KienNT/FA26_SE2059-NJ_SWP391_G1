package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dao.AssignmentDAO;
import vn.edu.fpt.ams.dto.AssignmentCreatePage;
import vn.edu.fpt.ams.dto.AssignmentForm;
import vn.edu.fpt.ams.model.Assignment;
import vn.edu.fpt.ams.model.AssignmentStatus;
import vn.edu.fpt.ams.model.Question;
import vn.edu.fpt.ams.util.AssignmentDraftValidator.ValidationException;
import vn.edu.fpt.ams.util.AssignmentEditConflictException;
import vn.edu.fpt.ams.util.DBConnection;

/** Opt in with -Dassignment.db.tests=true; all writes stay in session-local TEMP tables. */
class AssignmentLifecycleDatabaseTest {
    private Connection connection;

    @BeforeEach void setup() throws SQLException {
        Assumptions.assumeTrue(Boolean.getBoolean("assignment.db.tests"),
                "Enable assignment.db.tests for PostgreSQL checks");
        connection = DBConnection.getConnection();
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE users (user_id BIGINT PRIMARY KEY, role TEXT, account_status TEXT)");
            statement.execute("CREATE TEMP TABLE subjects (subject_id BIGINT PRIMARY KEY, subject_code TEXT, subject_name TEXT, status TEXT)");
            statement.execute("CREATE TEMP TABLE academic_terms (term_id BIGINT PRIMARY KEY, term_code TEXT, term_name TEXT, is_active BOOLEAN)");
            statement.execute("CREATE TEMP TABLE subject_terms (subject_term_id BIGINT PRIMARY KEY, subject_id BIGINT, term_id BIGINT)");
            statement.execute("CREATE TEMP TABLE subject_classes (class_id BIGINT PRIMARY KEY, subject_term_id BIGINT, teacher_id BIGINT, class_code TEXT, class_name TEXT, is_active BOOLEAN)");
            statement.execute("""
                    CREATE TEMP TABLE assignment_templates (template_id BIGINT PRIMARY KEY, subject_id BIGINT,
                        title TEXT, instructions TEXT, maximum_score NUMERIC, status TEXT,
                        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP)
                    """);
            statement.execute("CREATE TEMP TABLE assignments (LIKE public.assignments INCLUDING ALL)");
            statement.execute("CREATE TEMP TABLE questions (LIKE public.questions INCLUDING ALL)");
            statement.execute("CREATE TEMP TABLE submissions (LIKE public.submissions INCLUDING ALL)");
            statement.execute("ALTER TABLE assignments DROP CONSTRAINT IF EXISTS ck_assignments_draft_timestamps");
            statement.execute("ALTER TABLE assignments ADD CONSTRAINT ck_assignments_draft_timestamps CHECK (status <> 'DRAFT' OR cancelled_at IS NULL)");
            statement.execute("CREATE TEMP TABLE submission_content_snapshots (submission_id BIGINT PRIMARY KEY, assignment_data JSONB NOT NULL, questions_data JSONB NOT NULL)");
            // LIKE can copy serial defaults. Replace both serial and identity IDs so that
            // fixture setup and service writes cannot advance an application sequence.
            statement.execute("ALTER TABLE assignments ALTER COLUMN assignment_id DROP IDENTITY IF EXISTS");
            statement.execute("CREATE TEMP SEQUENCE lifecycle_assignment_test_ids START WITH 1000000");
            statement.execute("ALTER TABLE assignments ALTER COLUMN assignment_id SET DEFAULT nextval('lifecycle_assignment_test_ids')");
            statement.execute("ALTER TABLE questions ALTER COLUMN question_id DROP IDENTITY IF EXISTS");
            statement.execute("CREATE TEMP SEQUENCE lifecycle_question_test_ids START WITH 1000000");
            statement.execute("ALTER TABLE questions ALTER COLUMN question_id SET DEFAULT nextval('lifecycle_question_test_ids')");
            statement.execute("ALTER TABLE submissions ALTER COLUMN submission_id DROP IDENTITY IF EXISTS");
            statement.execute("CREATE TEMP SEQUENCE lifecycle_submission_test_ids START WITH 1000000");
            statement.execute("ALTER TABLE submissions ALTER COLUMN submission_id SET DEFAULT nextval('lifecycle_submission_test_ids')");
            statement.execute("SET search_path TO pg_temp");
            statement.execute("""
                    INSERT INTO users VALUES (1,'TEACHER','ACTIVE'), (2,'TEACHER','ACTIVE'),
                        (3,'TEACHER','INACTIVE'), (4,'STUDENT','ACTIVE'),
                        (5,'EXPERT','ACTIVE'), (6,'TEACHER','ACTIVE')
                    """);
            statement.execute("INSERT INTO subjects VALUES (100,'SWP391','Project','ACTIVE'), (200,'PRJ301','Java','ACTIVE')");
            statement.execute("INSERT INTO academic_terms VALUES (300,'FALL2026','Fall 2026',TRUE)");
            statement.execute("INSERT INTO subject_terms VALUES (1000,100,300), (2000,200,300)");
            statement.execute("""
                    INSERT INTO subject_classes VALUES (10,1000,1,'SE2059','Owned',TRUE),
                        (20,2000,2,'PRIVATE','Foreign',TRUE),
                        (30,1000,3,'INACTIVE','Inactive Teacher',TRUE),
                        (40,1000,4,'STUDENT','Wrong role',TRUE)
                    """);
            statement.execute("""
                    INSERT INTO assignment_templates (template_id,subject_id,title,instructions,maximum_score,status)
                    VALUES (1,100,'Essay template','Template instructions',10,'ACTIVE'),
                        (2,200,'Foreign template','Foreign template instructions',10,'ACTIVE')
                    """);
            statement.execute("""
                    INSERT INTO assignments (assignment_id,class_id,template_id,title,assignment_purpose,response_type,
                        instructions,scope_text,task_checklist,maximum_score,status,available_from,due_at,
                        time_limit_minutes,attempt_limit,published_at,cancelled_at)
                    VALUES (101,10,1,'Ready objective','REGULAR','OBJECTIVE','Answer every question.','','[]'::jsonb,
                        10,'DRAFT',clock_timestamp()+interval '1 hour',clock_timestamp()+interval '2 days',30,NULL,NULL,NULL),
                        (102,10,1,'Ready essay','REGULAR','ESSAY','Explain the design.','UML and Java',
                        '[{"task_order":1,"title":"Describe the design","description":"Include one diagram.","maximum_points":10}]'::jsonb,
                        10,'DRAFT',clock_timestamp()+interval '1 hour',clock_timestamp()+interval '2 days',NULL,NULL,NULL,NULL),
                        (103,10,1,'Published essay','REGULAR','ESSAY','Explain the design.','UML and Java',
                        '[{"task_order":1,"title":"Describe the design","description":"Include one diagram.","maximum_points":10}]'::jsonb,
                        10,'PUBLISHED',clock_timestamp()-interval '1 day',clock_timestamp()+interval '2 days',NULL,NULL,
                        clock_timestamp()-interval '2 days',NULL),
                        (104,10,1,'Cancelled essay','REGULAR','ESSAY','Explain the design.','UML and Java',
                        '[{"task_order":1,"title":"Describe the design","description":"Include one diagram.","maximum_points":10}]'::jsonb,
                        10,'CANCELLED',clock_timestamp()-interval '1 day',clock_timestamp()+interval '2 days',NULL,NULL,
                        clock_timestamp()-interval '2 days',clock_timestamp()-interval '1 day'),
                        (105,10,1,'Incomplete objective','REGULAR','OBJECTIVE','  ','','[]'::jsonb,
                        10,'DRAFT',NULL,NULL,NULL,NULL,NULL,NULL),
                        (106,10,1,'Past deadline','REGULAR','ESSAY','Explain the design.','UML and Java',
                        '[{"task_order":1,"title":"Describe the design","description":"Include one diagram.","maximum_points":10}]'::jsonb,
                        10,'DRAFT',clock_timestamp()-interval '2 days',clock_timestamp()-interval '1 day',NULL,NULL,NULL,NULL),
                        (107,10,1,'Incorrect score total','REGULAR','OBJECTIVE','Answer the question.','','[]'::jsonb,
                        10,'DRAFT',clock_timestamp()+interval '1 hour',clock_timestamp()+interval '2 days',NULL,NULL,NULL,NULL),
                        (108,10,1,'Practice without attempts','PRACTICE','OBJECTIVE','Try again.','','[]'::jsonb,
                        10,'DRAFT',clock_timestamp()+interval '1 hour',clock_timestamp()+interval '2 days',20,NULL,NULL,NULL),
                        (201,20,2,'Foreign draft','REGULAR','ESSAY','Foreign instructions.','Foreign scope',
                        '[{"task_order":1,"title":"Foreign task","description":"Foreign detail"}]'::jsonb,
                        10,'DRAFT',clock_timestamp()+interval '1 hour',clock_timestamp()+interval '2 days',NULL,NULL,NULL,NULL),
                        (301,30,1,'Inactive Teacher draft','REGULAR','ESSAY','Instructions.','Scope',
                        '[{"task_order":1,"title":"Task","description":"Details"}]'::jsonb,
                        10,'DRAFT',clock_timestamp()+interval '1 hour',clock_timestamp()+interval '2 days',NULL,NULL,NULL,NULL),
                        (401,40,1,'Wrong role draft','REGULAR','ESSAY','Instructions.','Scope',
                        '[{"task_order":1,"title":"Task","description":"Details"}]'::jsonb,
                        10,'DRAFT',clock_timestamp()+interval '1 hour',clock_timestamp()+interval '2 days',NULL,NULL,NULL,NULL)
                    """);
            statement.execute("""
                    INSERT INTO questions (question_id,assignment_id,question_order,question_text,maximum_points,options)
                    VALUES (1001,101,1,'First saved question',4,
                        '[{"option_id":11,"option_order":1,"option_text":"Correct first answer","is_correct":true},
                          {"option_id":12,"option_order":2,"option_text":"Alternative first answer","is_correct":false}]'::jsonb),
                        (1002,101,2,'Second saved question',6,
                        '[{"option_id":"saved-uuid","option_order":1,"option_text":"Correct second answer","is_correct":true},
                          {"option_id":22,"option_order":2,"option_text":"Alternative second answer","is_correct":false}]'::jsonb),
                        (1007,107,1,'Score mismatch question',9,
                        '[{"option_id":71,"option_order":1,"option_text":"Correct answer","is_correct":true},
                          {"option_id":72,"option_order":2,"option_text":"Alternative","is_correct":false}]'::jsonb),
                        (1008,108,1,'Practice question',10,
                        '[{"option_id":81,"option_order":1,"option_text":"Correct answer","is_correct":true},
                          {"option_id":82,"option_order":2,"option_text":"Alternative","is_correct":false}]'::jsonb)
                    """);
            statement.execute("""
                    INSERT INTO submissions (submission_id,assignment_id,enrollment_id,attempt_number,status,
                        started_at,deadline_at,submitted_at,content,submission_link,answer,score,feedback,
                        graded_by,graded_at,released_at)
                    SELECT a.assignment_id*100+s.n,a.assignment_id,s.n,1,s.status,
                        clock_timestamp()-interval '1 hour',clock_timestamp()+interval '1 day',
                        CASE WHEN s.status IN ('SUBMITTED','GRADED','RELEASED')
                            THEN clock_timestamp()-interval '30 minutes' END,
                        'Saved content '||s.status,'https://example.test/submission/'||s.n,
                        '[{"question_id":1001,"option_id":11}]'::jsonb,
                        CASE WHEN s.status IN ('GRADED','RELEASED') THEN 7.5 END,
                        CASE WHEN s.status IN ('GRADED','RELEASED') THEN 'Saved feedback' END,
                        CASE WHEN s.status IN ('GRADED','RELEASED') THEN 1 END,
                        CASE WHEN s.status IN ('GRADED','RELEASED')
                            THEN clock_timestamp()-interval '20 minutes' END,
                        CASE WHEN s.status='RELEASED' THEN clock_timestamp()-interval '10 minutes' END
                    FROM (VALUES (101),(103),(201)) AS a(assignment_id)
                    CROSS JOIN (VALUES (1,'IN_PROGRESS'),(2,'IN_PROGRESS'),(3,'SUBMITTED'),
                        (4,'GRADED'),(5,'RELEASED'),(6,'EXPIRED'),(7,'VOID')) AS s(n,status)
                    """);
        }
    }

    @AfterEach void close() throws SQLException {
        if (connection != null) connection.close();
    }

    private void resetConnection() throws SQLException {
        connection.setAutoCommit(true);
        connection.setReadOnly(false);
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
    }

    private AssignmentService service(AssignmentDAO dao) {
        Connection borrowed = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("close")) return null;
                    try { return method.invoke(connection, arguments); }
                    catch (InvocationTargetException ex) { throw ex.getCause(); }
                });
        return new AssignmentService(dao, () -> {
            resetConnection();
            return borrowed;
        });
    }

    private AssignmentService service() { return service(new AssignmentDAO()); }

    private void execute(String sql, Object... arguments) throws SQLException {
        resetConnection();
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < arguments.length; i++) statement.setObject(i + 1, arguments[i]);
            statement.executeUpdate();
        }
    }

    private Instant databaseTime() throws SQLException {
        resetConnection();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT clock_timestamp()")) {
            assertTrue(rows.next());
            return rows.getTimestamp(1).toInstant();
        }
    }

    private record Snapshot(JsonObject assignment, JsonArray questions, Map<Long, JsonObject> submissions) {}

    private Snapshot snapshot(long assignmentId) throws SQLException {
        resetConnection();
        JsonObject assignment;
        try (var statement = connection.prepareStatement("SELECT row_to_json(a)::text FROM assignments a WHERE assignment_id=?")) {
            statement.setLong(1, assignmentId);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assignment = JsonParser.parseString(rows.getString(1)).getAsJsonObject();
            }
        }
        var questions = new JsonArray();
        try (var statement = connection.prepareStatement("""
                SELECT row_to_json(q)::text FROM questions q WHERE assignment_id=? ORDER BY question_order,question_id
                """)) {
            statement.setLong(1, assignmentId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) questions.add(JsonParser.parseString(rows.getString(1)));
            }
        }
        Map<Long, JsonObject> submissions = new LinkedHashMap<>();
        try (var statement = connection.prepareStatement("""
                SELECT row_to_json(s)::text FROM submissions s WHERE assignment_id=? ORDER BY submission_id
                """)) {
            statement.setLong(1, assignmentId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    var submission = JsonParser.parseString(rows.getString(1)).getAsJsonObject();
                    submissions.put(submission.get("submission_id").getAsLong(), submission);
                }
            }
        }
        return new Snapshot(assignment, questions, Map.copyOf(submissions));
    }

    private static JsonObject without(JsonObject row, String... fields) {
        var copy = row.deepCopy();
        for (String field : fields) copy.remove(field);
        return copy;
    }

    private static Instant timestamp(JsonObject row, String field) {
        return Instant.parse(row.get(field).getAsString());
    }

    private static AssignmentForm with(AssignmentForm form, String... changes) {
        var values = new HashMap<>(form.values());
        for (int i = 0; i < changes.length; i += 2) values.put(changes[i], changes[i + 1]);
        return new AssignmentForm(values);
    }

    private Map<Long, JsonObject> contentSnapshots(long assignmentId) throws SQLException {
        resetConnection();
        Map<Long,JsonObject> saved=new LinkedHashMap<>();
        try(var statement=connection.prepareStatement("""
                SELECT snapshot.submission_id,row_to_json(snapshot)::text
                FROM submission_content_snapshots snapshot
                JOIN submissions s ON s.submission_id=snapshot.submission_id
                WHERE s.assignment_id=? ORDER BY snapshot.submission_id
                """)) {
            statement.setLong(1,assignmentId);
            try(var rows=statement.executeQuery()) {
                while(rows.next()) saved.put(rows.getLong(1),JsonParser.parseString(rows.getString(2)).getAsJsonObject());
            }
        }
        return saved;
    }

    private static void assertOnlyInProgressVoided(Snapshot before, Snapshot after) {
        assertEquals(before.submissions().keySet(), after.submissions().keySet());
        for (var entry : before.submissions().entrySet()) {
            var expected = entry.getValue().deepCopy();
            if (expected.get("status").getAsString().equals("IN_PROGRESS")) expected.addProperty("status", "VOID");
            assertEquals(expected, after.submissions().get(entry.getKey()),
                    "Cancellation must preserve every other field of submission " + entry.getKey());
        }
    }

    @Test void confirmationGetReportsStoredContentReadinessAndCountWithoutWriting() throws SQLException {
        var service = service();
        var before = snapshot(101);
        var page = service.lifecyclePage(1, 101, "publish");
        assertAll(
                () -> assertEquals(101L, page.detail().assignment().assignmentId()),
                () -> assertEquals(AssignmentStatus.DRAFT, page.detail().assignment().status()),
                () -> assertEquals("publish", page.action()),
                () -> assertTrue(page.revision().matches("[0-9a-f]{64}")),
                () -> assertTrue(page.errors().isEmpty()),
                () -> assertEquals(0, page.inProgressCount()),
                () -> assertEquals(List.of(1001L, 1002L), page.detail().questions().stream().map(Question::questionId).toList()));
        assertEquals(before.questions().get(0).getAsJsonObject().get("options"),
                JsonParser.parseString(page.detail().questions().get(0).options()));
        assertEquals(before.questions().get(1).getAsJsonObject().get("options"),
                JsonParser.parseString(page.detail().questions().get(1).options()));
        assertEquals(before, snapshot(101));
        var cancel = service.lifecyclePage(1, 101, "cancel");
        assertEquals("cancel", cancel.action());
        assertEquals(page.revision(), cancel.revision());
        assertEquals(2, cancel.inProgressCount());
        assertEquals(before, snapshot(101));
        var essayBefore = snapshot(102);
        var essay = service.lifecyclePage(1, 102, "publish");
        assertTrue(essay.errors().isEmpty());
        assertEquals("UML and Java", essay.detail().assignment().scopeText());
        assertTrue(essay.detail().questions().isEmpty());
        assertEquals(essayBefore, snapshot(102));
    }

    @Test void incompleteAndPastDueDraftsShowErrorsAndFailedPublishChangesNothing() throws SQLException {
        var service = service();
        for (long assignmentId : new long[]{105, 106, 107, 108}) {
            var before = snapshot(assignmentId);
            var page = service.lifecyclePage(1, assignmentId, "publish");
            assertFalse(page.errors().isEmpty(), "Fixture " + assignmentId + " must not be publishable");
            var failure = assertThrows(ValidationException.class,
                    () -> service.publishAssignment(1, assignmentId, page.revision()));
            assertFalse(failure.errors().isEmpty());
            assertEquals(before, snapshot(assignmentId));
            // Cancellation can discard an incomplete draft without publication validation.
            var cancellation = service.lifecyclePage(1, assignmentId, "cancel");
            assertTrue(cancellation.errors().isEmpty());
            assertEquals(before, snapshot(assignmentId));
        }
        var errors = service.lifecyclePage(1, 105, "publish").errors();
        assertTrue(errors.containsKey("instructions"));
        assertTrue(errors.containsKey("availableFrom"));
        assertTrue(errors.containsKey("dueAt"));
        assertTrue(errors.containsKey("questionsJson"));
        assertTrue(service.lifecyclePage(1, 106, "publish").errors().containsKey("dueAt"));
        assertTrue(service.lifecyclePage(1, 108, "publish").errors().containsKey("attemptLimit"));
    }

    @Test void objectiveNeedsTwoOptionsAtLeastOneCorrectAnswerAndMatchingScore() throws SQLException {
        var service = service();
        var originalOptions = snapshot(101).questions().get(0).getAsJsonObject().get("options").toString();
        for (String options : List.of(
                "[]",
                "[{\"option_id\":11,\"option_order\":1,\"option_text\":\"Only answer\",\"is_correct\":true}]",
                "[{\"option_id\":11,\"option_order\":1,\"option_text\":\"A\",\"is_correct\":false},"
                        + "{\"option_id\":12,\"option_order\":2,\"option_text\":\"B\",\"is_correct\":false}]")) {
            execute("UPDATE questions SET options=CAST(? AS jsonb) WHERE question_id=1001", options);
            var before = snapshot(101);
            var page = service.lifecyclePage(1, 101, "publish");
            assertTrue(page.errors().containsKey("questionsJson"));
            assertThrows(ValidationException.class, () -> service.publishAssignment(1, 101, page.revision()));
            assertEquals(before, snapshot(101));
        }
        execute("UPDATE questions SET options=CAST(? AS jsonb) WHERE question_id=1001", originalOptions);
        var mismatch = service.lifecyclePage(1, 107, "publish");
        assertFalse(mismatch.errors().isEmpty());
        execute("UPDATE questions SET maximum_points=10 WHERE question_id=1007");
        assertTrue(service.lifecyclePage(1, 107, "publish").errors().isEmpty());
        execute("UPDATE assignments SET attempt_limit=3 WHERE assignment_id=108");
        var practice = service.lifecyclePage(1, 108, "publish");
        assertTrue(practice.errors().isEmpty());
        service.publishAssignment(1, 108, practice.revision());
        assertEquals("PUBLISHED", snapshot(108).assignment().get("status").getAsString());
    }

    @Test void essayNeedsInstructionsAndScoredQuestionsButNoScopeBeforePublication() throws SQLException {
        execute("UPDATE assignments SET instructions=' ',scope_text='',task_checklist='[]'::jsonb WHERE assignment_id=102");
        var service = service();
        var before = snapshot(102);
        var page = service.lifecyclePage(1, 102, "publish");
        assertTrue(page.errors().containsKey("instructions"));
        assertFalse(page.errors().containsKey("scopeText"));
        assertTrue(page.errors().containsKey("tasksJson"));
        assertThrows(ValidationException.class, () -> service.publishAssignment(1, 102, page.revision()));
        assertEquals(before, snapshot(102));
        execute("UPDATE assignments SET instructions='Valid instructions.' WHERE assignment_id=102");
        assertFalse(service.lifecyclePage(1, 102, "publish").errors().isEmpty(),
                "An essay still needs scored questions");
        execute("UPDATE assignments SET scope_text='Valid scope.' WHERE assignment_id=102");
        assertTrue(service.lifecyclePage(1, 102, "publish").errors().containsKey("tasksJson"));
        execute("""
                UPDATE assignments SET scope_text='',
                    task_checklist='[{"task_order":1,"title":"Valid task","description":"Details","maximum_points":10}]'::jsonb
                WHERE assignment_id=102
                """);
        assertTrue(service.lifecyclePage(1, 102, "publish").errors().isEmpty());
    }

    @Test void publishingPreservesAllSavedQuestionsOptionsAndSubmissionsAndRepeatsSafely() throws SQLException {
        var service = service();
        var before = snapshot(101);
        var page = service.lifecyclePage(1, 101, "publish");
        var earliest = databaseTime();
        service.publishAssignment(1, 101, page.revision());
        var latest = databaseTime();
        var published = snapshot(101);
        assertEquals("PUBLISHED", published.assignment().get("status").getAsString());
        assertFalse(published.assignment().get("published_at").isJsonNull());
        var publishedAt = timestamp(published.assignment(), "published_at");
        assertFalse(publishedAt.isBefore(earliest));
        assertFalse(publishedAt.isAfter(latest));
        assertTrue(published.assignment().get("cancelled_at").isJsonNull());
        assertEquals(without(before.assignment(), "status", "published_at"),
                without(published.assignment(), "status", "published_at"));
        assertEquals(before.questions(), published.questions());
        assertEquals(before.submissions(), published.submissions());
        // A resubmitted confirmation has the revision from before publication.
        service.publishAssignment(1, 101, page.revision());
        assertEquals(published, snapshot(101));
        assertEquals(AssignmentStatus.PUBLISHED, service.lifecyclePage(1, 101, "publish").detail().assignment().status());
        assertEquals(published, snapshot(101));
    }

    @Test void validEssayPublishesWithScopeAndTasksIntact() throws SQLException {
        var service = service();
        var before = snapshot(102);
        var page = service.lifecyclePage(1, 102, "publish");
        service.publishAssignment(1, 102, page.revision());
        var after = snapshot(102);
        assertEquals("PUBLISHED", after.assignment().get("status").getAsString());
        assertEquals(without(before.assignment(), "status", "published_at"),
                without(after.assignment(), "status", "published_at"));
        assertEquals(before.questions(), after.questions());
        assertEquals(before.submissions(), after.submissions());
    }

    @Test void cancellationVoidsOnlyInProgressForBothDraftAndPublishedAndRepeatsSafely() throws SQLException {
        var service = service();
        var foreign = snapshot(201);
        for (long assignmentId : new long[]{101, 103}) {
            var before = snapshot(assignmentId);
            var page = service.lifecyclePage(1, assignmentId, "cancel");
            assertEquals(2, page.inProgressCount());
            var earliest = databaseTime();
            assertEquals(2, service.cancelAssignment(1, assignmentId, page.revision()));
            var latest = databaseTime();
            var cancelled = snapshot(assignmentId);
            assertEquals("CANCELLED", cancelled.assignment().get("status").getAsString());
            var cancelledAt = timestamp(cancelled.assignment(), "cancelled_at");
            assertFalse(cancelledAt.isBefore(earliest));
            assertFalse(cancelledAt.isAfter(latest));
            assertEquals(before.assignment().get("published_at"), cancelled.assignment().get("published_at"));
            assertEquals(without(before.assignment(), "status", "cancelled_at"),
                    without(cancelled.assignment(), "status", "cancelled_at"));
            assertEquals(before.questions(), cancelled.questions());
            assertOnlyInProgressVoided(before, cancelled);
            assertEquals(0, service.cancelAssignment(1, assignmentId, page.revision()));
            assertEquals(cancelled, snapshot(assignmentId));
            var repeat = service.lifecyclePage(1, assignmentId, "cancel");
            assertEquals(0, repeat.inProgressCount());
            assertEquals(AssignmentStatus.CANCELLED, repeat.detail().assignment().status());
            assertEquals(cancelled, snapshot(assignmentId));
        }
        assertEquals(foreign, snapshot(201));
    }

    @Test void incompleteDraftCanBeCancelledWithoutSettingPublishedTimestamp() throws SQLException {
        var service = service();
        var before = snapshot(105);
        var page = service.lifecyclePage(1, 105, "cancel");
        assertTrue(page.errors().isEmpty());
        assertEquals(0, service.cancelAssignment(1, 105, page.revision()));
        var after = snapshot(105);
        assertEquals("CANCELLED", after.assignment().get("status").getAsString());
        assertTrue(after.assignment().get("published_at").isJsonNull());
        assertFalse(after.assignment().get("cancelled_at").isJsonNull());
        assertEquals(without(before.assignment(), "status", "cancelled_at"),
                without(after.assignment(), "status", "cancelled_at"));
    }

    @Test void cancelledAssignmentCannotBePublishedAndExistingCancellationRepeatsWithoutWrites() throws SQLException {
        var service = service();
        var before = snapshot(104);
        var cancellation = service.lifecyclePage(1, 104, "cancel");
        assertEquals(0, service.cancelAssignment(1, 104, cancellation.revision()));
        assertEquals(before, snapshot(104));
        var publication = service.lifecyclePage(1, 104, "publish");
        assertEquals(AssignmentStatus.CANCELLED, publication.detail().assignment().status());
        assertEquals("publish", publication.action());
        assertEquals(before, snapshot(104));
        assertThrows(AssignmentEditConflictException.class,
                () -> service.publishAssignment(1, 104, cancellation.revision()));
        assertEquals(before, snapshot(104));
    }

    @Test void cancelledAssignmentRejectsRestoreWithoutChangingContentSubmissionsOrSnapshots() throws SQLException {
        var service=service();
        service.cancelAssignment(1,103,service.lifecyclePage(1,103,"cancel").revision());
        var before=snapshot(103);
        var storedSnapshots=contentSnapshots(103);
        assertThrows(IllegalArgumentException.class,()->service.lifecyclePage(1,103,"restore"));
        for(String revision:new String[]{null,"", "a".repeat(64)})
            assertThrows(IllegalArgumentException.class,()->service.restoreAssignment(1,103,revision));
        assertThrows(AssignmentEditConflictException.class,()->service.editPage(1,103));
        assertThrows(AssignmentEditConflictException.class,()->service.publishAssignment(1,103,service.lifecyclePage(1,103,"publish").revision()));
        assertEquals(before,snapshot(103));
        assertEquals(storedSnapshots,contentSnapshots(103));
    }

    @Test void deniesForeignMissingInactiveAndWrongRoleActorsForLoadAndBothActions() throws SQLException {
        var service = service();
        var revision = service.lifecyclePage(1, 101, "publish").revision();
        var own = snapshot(101);
        var foreign = snapshot(201);
        for (long assignmentId : new long[]{201, 999999}) {
            assertThrows(NoSuchElementException.class, () -> service.lifecyclePage(1, assignmentId, "publish"));
            assertThrows(NoSuchElementException.class, () -> service.lifecyclePage(1, assignmentId, "cancel"));
            assertThrows(NoSuchElementException.class, () -> service.publishAssignment(1, assignmentId, revision));
            assertThrows(NoSuchElementException.class, () -> service.cancelAssignment(1, assignmentId, revision));
        }
        for (long actor : new long[]{2, 6}) {
            assertThrows(NoSuchElementException.class, () -> service.lifecyclePage(actor, 101, "publish"));
            assertThrows(NoSuchElementException.class, () -> service.lifecyclePage(actor, 101, "cancel"));
            assertThrows(NoSuchElementException.class, () -> service.publishAssignment(actor, 101, revision));
            assertThrows(NoSuchElementException.class, () -> service.cancelAssignment(actor, 101, revision));
        }
        for (long actor : new long[]{3, 4, 5, 999999}) {
            assertThrows(SecurityException.class, () -> service.lifecyclePage(actor, 101, "publish"));
            assertThrows(SecurityException.class, () -> service.lifecyclePage(actor, 101, "cancel"));
            assertThrows(SecurityException.class, () -> service.publishAssignment(actor, 101, revision));
            assertThrows(SecurityException.class, () -> service.cancelAssignment(actor, 101, revision));
        }
        assertThrows(SecurityException.class, () -> service.lifecyclePage(3, 301, "publish"));
        assertThrows(SecurityException.class, () -> service.lifecyclePage(4, 401, "cancel"));
        assertEquals(own, snapshot(101));
        assertEquals(foreign, snapshot(201));
    }

    @Test void repeatedActionsStillRequireAnActiveTeacherAndCurrentClassOwnership() throws SQLException {
        var service = service();
        var publication = service.lifecyclePage(1, 103, "publish");
        var cancellation = service.lifecyclePage(1, 104, "cancel");
        var published = snapshot(103);
        var cancelled = snapshot(104);
        execute("UPDATE users SET account_status='INACTIVE' WHERE user_id=1");
        assertThrows(SecurityException.class, () -> service.publishAssignment(1, 103, publication.revision()));
        assertThrows(SecurityException.class, () -> service.cancelAssignment(1, 104, cancellation.revision()));
        execute("UPDATE users SET account_status='ACTIVE' WHERE user_id=1");
        execute("UPDATE subject_classes SET teacher_id=2 WHERE class_id=10");
        assertThrows(NoSuchElementException.class, () -> service.publishAssignment(1, 103, publication.revision()));
        assertThrows(NoSuchElementException.class, () -> service.cancelAssignment(1, 104, cancellation.revision()));
        assertEquals(published, snapshot(103));
        assertEquals(cancelled, snapshot(104));
    }

    @Test void missingMalformedAndStaleConfirmationRevisionsCannotChangeAssignments() throws SQLException {
        var service = service();
        var before = snapshot(101);
        for (String revision : new String[]{null, "", " ", "not-a-revision", "a".repeat(63), "g".repeat(64)}) {
            var publish = assertThrows(ValidationException.class, () -> service.publishAssignment(1, 101, revision));
            assertTrue(publish.errors().containsKey("revision"));
            var cancel = assertThrows(ValidationException.class, () -> service.cancelAssignment(1, 101, revision));
            assertTrue(cancel.errors().containsKey("revision"));
            assertEquals(before, snapshot(101));
        }
        String stale = "0".repeat(64);
        assertThrows(AssignmentEditConflictException.class, () -> service.publishAssignment(1, 101, stale));
        assertThrows(AssignmentEditConflictException.class, () -> service.cancelAssignment(1, 101, stale));
        assertEquals(before, snapshot(101));
    }

    @Test void editAfterConfirmationBlocksBothStalePublishAndStaleCancel() throws SQLException {
        var service = service();
        var publication = service.lifecyclePage(1, 101, "publish");
        var cancellation = service.lifecyclePage(1, 101, "cancel");
        var editor = service.editPage(1, 101).form();
        service.updateAssignment(1, 101, with(editor, "title", "Latest saved title",
                "instructions", "Latest saved instructions."));
        var edited = snapshot(101);
        assertNotEquals(publication.revision(), service.lifecyclePage(1, 101, "publish").revision());
        assertThrows(AssignmentEditConflictException.class,
                () -> service.publishAssignment(1, 101, publication.revision()));
        assertThrows(AssignmentEditConflictException.class,
                () -> service.cancelAssignment(1, 101, cancellation.revision()));
        assertEquals(edited, snapshot(101));
        assertEquals("DRAFT", edited.assignment().get("status").getAsString());
    }

    @Test void failureAfterPublishingRollsBackParentAndPreservesNestedData() throws SQLException {
        var normal = service();
        var page = normal.lifecyclePage(1, 101, "publish");
        var before = snapshot(101);
        var failing = new AssignmentDAO() {
            @Override public void markPublished(Connection c, long assignmentId, Instant publishedAt) throws SQLException {
                super.markPublished(c, assignmentId, publishedAt);
                throw new SQLException("Injected failure after marking published");
            }
        };
        assertThrows(IllegalStateException.class, () -> service(failing).publishAssignment(1, 101, page.revision()));
        assertEquals(before, snapshot(101));
        assertEquals(page.revision(), normal.lifecyclePage(1, 101, "publish").revision());
    }

    @Test void failureAfterVoidingRollsBackCancellationAndEverySubmission() throws SQLException {
        var normal = service();
        var page = normal.lifecyclePage(1, 103, "cancel");
        var before = snapshot(103);
        var foreign = snapshot(201);
        var failing = new AssignmentDAO() {
            @Override public int voidInProgressSubmissions(Connection c, long assignmentId) throws SQLException {
                int count = super.voidInProgressSubmissions(c, assignmentId);
                assertEquals(2, count, "Injected failure must occur after children have actually been changed");
                throw new SQLException("Injected failure after voiding in-progress submissions");
            }
        };
        assertThrows(IllegalStateException.class, () -> service(failing).cancelAssignment(1, 103, page.revision()));
        assertEquals(before, snapshot(103));
        assertEquals(foreign, snapshot(201));
        assertEquals(page.revision(), normal.lifecyclePage(1, 103, "cancel").revision());
        assertEquals(2, normal.lifecyclePage(1, 103, "cancel").inProgressCount());
    }

    @Test void writesLockTeacherParentClassThenQuestionsBeforeReadingCurrentTime() {
        List<String> locks = new ArrayList<>();
        var recording = new AssignmentDAO() {
            @Override public boolean lockActiveTeacher(Connection c, long teacherId) throws SQLException {
                locks.add("teacher");
                return super.lockActiveTeacher(c, teacherId);
            }
            @Override public Assignment lockOwnedAssignment(Connection c, long teacherId, long assignmentId) throws SQLException {
                locks.add("assignment");
                return super.lockOwnedAssignment(c, teacherId, assignmentId);
            }
            @Override public AssignmentCreatePage findCreatePage(Connection c, long teacherId, long classId,
                    boolean lockClass) throws SQLException {
                if (lockClass) locks.add("class");
                return super.findCreatePage(c, teacherId, classId, lockClass);
            }
            @Override public List<Question> findAllDraftQuestions(Connection c, long assignmentId, boolean lock)
                    throws SQLException {
                if (lock) locks.add("questions");
                return super.findAllDraftQuestions(c, assignmentId, lock);
            }
            @Override public Instant currentTime(Connection c) throws SQLException {
                locks.add("clock");
                return super.currentTime(c);
            }
        };
        var normal = service();
        var service = service(recording);
        service.publishAssignment(1, 101, normal.lifecyclePage(1, 101, "publish").revision());
        assertEquals(List.of("teacher", "assignment", "class", "questions", "clock"), locks);
        locks.clear();
        service.cancelAssignment(1, 103, normal.lifecyclePage(1, 103, "cancel").revision());
        assertEquals(List.of("teacher", "assignment", "class", "questions", "clock"), locks);
    }
}
