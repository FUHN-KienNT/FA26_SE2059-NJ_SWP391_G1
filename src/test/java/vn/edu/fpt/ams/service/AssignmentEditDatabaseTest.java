package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dao.AssignmentDAO;
import vn.edu.fpt.ams.dto.AssignmentForm;
import vn.edu.fpt.ams.model.Question;
import vn.edu.fpt.ams.util.AssignmentDraftValidator.ValidationException;
import vn.edu.fpt.ams.util.AssignmentEditConflictException;
import vn.edu.fpt.ams.util.DBConnection;

/** Opt in with -Dassignment.db.tests=true; writes only session-local TEMP fixtures. */
class AssignmentEditDatabaseTest {
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
            // Replace both identity and serial defaults, so no application sequence is advanced.
            statement.execute("ALTER TABLE assignments ALTER COLUMN assignment_id DROP IDENTITY IF EXISTS");
            statement.execute("CREATE TEMP SEQUENCE edit_assignment_test_ids START WITH 10000");
            statement.execute("ALTER TABLE assignments ALTER COLUMN assignment_id SET DEFAULT nextval('edit_assignment_test_ids')");
            statement.execute("ALTER TABLE questions ALTER COLUMN question_id DROP IDENTITY IF EXISTS");
            statement.execute("CREATE TEMP SEQUENCE edit_question_test_ids START WITH 10000");
            statement.execute("ALTER TABLE questions ALTER COLUMN question_id SET DEFAULT nextval('edit_question_test_ids')");
            statement.execute("SET search_path TO pg_temp");
            statement.execute("""
                    INSERT INTO users VALUES (1,'TEACHER','ACTIVE'), (2,'TEACHER','ACTIVE'),
                        (3,'TEACHER','INACTIVE'), (4,'STUDENT','ACTIVE'), (5,'EXPERT','ACTIVE'),
                        (6,'TEACHER','ACTIVE')
                    """);
            statement.execute("""
                    INSERT INTO subjects VALUES (100,'SWP391','Project','ACTIVE'),
                        (200,'PRJ301','Java','ACTIVE')
                    """);
            statement.execute("INSERT INTO academic_terms VALUES (300,'FALL2026','Fall 2026',TRUE)");
            statement.execute("INSERT INTO subject_terms VALUES (1000,100,300), (2000,200,300)");
            statement.execute("""
                    INSERT INTO subject_classes VALUES (10,1000,1,'SE2059','Owned',TRUE),
                        (11,1000,1,'SE2060','Another owned class',TRUE),
                        (20,2000,2,'PRIVATE','Foreign',TRUE),
                        (30,1000,3,'INACTIVE','Inactive Teacher',TRUE),
                        (40,1000,4,'STUDENT','Wrong role',TRUE)
                    """);
            statement.execute("""
                    INSERT INTO assignment_templates (template_id,subject_id,title,instructions,maximum_score,status)
                    VALUES (1,100,'Active template title','Active template instructions',10,'ACTIVE'),
                        (2,100,'Inactive current template','Inactive template instructions',10,'INACTIVE'),
                        (3,200,'Wrong subject template','Foreign template instructions',10,'ACTIVE'),
                        (4,100,'Other inactive template','Other inactive instructions',10,'INACTIVE')
                    """);
            statement.execute("""
                    INSERT INTO assignments (assignment_id,class_id,template_id,title,
                        assignment_purpose,response_type,instructions,scope_text,task_checklist,
                        maximum_score,status,available_from,due_at,time_limit_minutes,attempt_limit,
                        published_at,cancelled_at)
                    VALUES (101,10,2,'Saved title <teacher>','PRACTICE','OBJECTIVE',
                        'Saved instructions & teacher changes.','','[]'::jsonb,20,'DRAFT',
                        '2026-10-12 00:30:00+00','2026-10-13 16:59:00+00',25,3,NULL,NULL),
                        (102,10,1,'Saved essay','REGULAR','ESSAY','Saved essay instructions',
                        'Saved UML scope','[{"task_order":1,"title":"Saved task","description":"Saved details","maximum_points":15}]'::jsonb,
                        15,'DRAFT',NULL,NULL,NULL,NULL,NULL,NULL),
                        (103,10,1,'Published fixture','REGULAR','ESSAY','Published instructions',
                        '','[]'::jsonb,10,'PUBLISHED','2026-10-05 00:00:00+00','2026-10-20 00:00:00+00',
                        NULL,NULL,'2026-10-01 00:00:00+00',NULL),
                        (104,10,1,'Cancelled fixture','REGULAR','ESSAY','Cancelled instructions',
                        '','[]'::jsonb,10,'CANCELLED',NULL,NULL,NULL,NULL,NULL,'2026-10-02 00:00:00+00'),
                        (201,20,3,'Foreign draft','REGULAR','OBJECTIVE','Foreign instructions',
                        '','[]'::jsonb,10,'DRAFT',NULL,NULL,NULL,NULL,NULL,NULL),
                        (301,30,1,'Inactive Teacher draft','REGULAR','ESSAY','Instructions',
                        '','[]'::jsonb,10,'DRAFT',NULL,NULL,NULL,NULL,NULL,NULL),
                        (401,40,1,'Wrong role draft','REGULAR','ESSAY','Instructions',
                        '','[]'::jsonb,10,'DRAFT',NULL,NULL,NULL,NULL,NULL,NULL)
                    """);
            statement.execute("""
                    INSERT INTO questions (question_id,assignment_id,question_order,question_text,maximum_points,options)
                    VALUES (1001,101,1,'Saved first question',2.50,
                        '[{"option_id":11,"option_order":1,"option_text":"Saved correct answer","is_correct":true},
                          {"option_id":12,"option_order":2,"option_text":"Saved alternative","is_correct":false}]'::jsonb),
                        (1002,101,2,'Saved second question',17.50,
                        '[{"option_id":21,"option_order":1,"option_text":"Second correct answer","is_correct":true},
                          {"option_id":22,"option_order":2,"option_text":"Second alternative","is_correct":false}]'::jsonb),
                        (2001,201,1,'Foreign question',10,
                        '[{"option_id":31,"option_order":1,"option_text":"Foreign answer","is_correct":true}]'::jsonb)
                    """);
        }
    }

    @AfterEach void close() throws SQLException {
        if (connection != null) connection.close();
    }

    private AssignmentService service(AssignmentDAO dao) {
        Connection borrowed = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("close")) return null;
                    try { return method.invoke(connection, arguments); }
                    catch (InvocationTargetException ex) { throw ex.getCause(); }
                });
        return new AssignmentService(dao, () -> {
            // Each service call normally receives a fresh connection. Keep the TEMP session,
            // but clear connection-level settings left behind by the preceding read/write.
            connection.setAutoCommit(true);
            connection.setReadOnly(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            return borrowed;
        });
    }

    private AssignmentService service() { return service(new AssignmentDAO()); }

    private static AssignmentForm with(AssignmentForm form, String... changes) {
        var values = new HashMap<>(form.values());
        for (int i = 0; i < changes.length; i += 2) values.put(changes[i], changes[i + 1]);
        return new AssignmentForm(values);
    }

    private static JsonArray questions(AssignmentForm form) {
        return JsonParser.parseString(form.value("questionsJson")).getAsJsonArray();
    }

    private static JsonArray options(JsonObject question) { return question.getAsJsonArray("options"); }

    private JsonArray savedQuestions(long assignmentId) throws SQLException {
        var result = new JsonArray();
        try (var statement = connection.prepareStatement("""
                SELECT question_id,question_order,question_text,maximum_points,options
                FROM questions WHERE assignment_id=? ORDER BY question_order,question_id
                """)) {
            statement.setLong(1, assignmentId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    var question = new JsonObject();
                    question.addProperty("question_id", rows.getLong("question_id"));
                    question.addProperty("question_order", rows.getInt("question_order"));
                    question.addProperty("question_text", rows.getString("question_text"));
                    question.addProperty("maximum_points", rows.getBigDecimal("maximum_points").toPlainString());
                    question.add("options", JsonParser.parseString(rows.getString("options")));
                    result.add(question);
                }
            }
        }
        return result;
    }

    private List<Long> questionIds(JsonArray questions) {
        return questions.asList().stream().map(q -> q.getAsJsonObject().get("question_id").getAsLong()).toList();
    }

    private List<String> optionIds(JsonObject question) {
        // Legacy numeric IDs and newly generated UUID strings are both identities.
        return options(question).asList().stream().map(o -> o.getAsJsonObject().get("option_id").getAsString()).toList();
    }

    private record Snapshot(String assignment, JsonArray questions) {}

    private Snapshot snapshot(long assignmentId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT row_to_json(a)::text FROM assignments a WHERE assignment_id=?")) {
            statement.setLong(1, assignmentId);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return new Snapshot(rows.getString(1), savedQuestions(assignmentId));
            }
        }
    }

    @Test void prefillUsesStoredContentTimesQuestionAndOptionIdentities() {
        var page = service().editPage(1, 101);
        var form = page.form();
        assertAll(
                () -> assertEquals(10L, page.context().classId()),
                () -> assertEquals(100L, page.context().subjectId()),
                () -> assertEquals("SE2059", page.context().classCode()),
                () -> assertEquals("SWP391", page.context().subjectCode()),
                () -> assertEquals("101", form.value("assignmentId")),
                () -> assertFalse(form.value("revision").isBlank()),
                () -> assertEquals("10", form.value("classId")),
                () -> assertEquals("2", form.value("templateId")),
                () -> assertEquals("Saved title <teacher>", form.value("title")),
                () -> assertEquals("Saved instructions & teacher changes.", form.value("instructions")),
                () -> assertEquals("PRACTICE", form.value("purpose")),
                () -> assertEquals("OBJECTIVE", form.value("responseType")),
                () -> assertEquals(0,new BigDecimal("20").compareTo(new BigDecimal(form.value("maximumScore")))),
                () -> assertEquals(LocalDateTime.of(2026, 10, 12, 7, 30), LocalDateTime.parse(form.value("availableFrom"))),
                () -> assertEquals(LocalDateTime.of(2026, 10, 13, 23, 59), LocalDateTime.parse(form.value("dueAt"))),
                () -> assertEquals("25", form.value("timeLimitMinutes")),
                () -> assertEquals("3", form.value("attemptLimit")));
        var entered = questions(form);
        assertEquals(List.of(1001L, 1002L), questionIds(entered));
        assertEquals("Saved first question", entered.get(0).getAsJsonObject().get("question_text").getAsString());
        assertEquals(List.of("11", "12"), optionIds(entered.get(0).getAsJsonObject()));
        assertEquals("Saved correct answer", options(entered.get(0).getAsJsonObject()).get(0)
                .getAsJsonObject().get("option_text").getAsString());
        assertTrue(options(entered.get(0).getAsJsonObject()).get(0).getAsJsonObject().get("is_correct").getAsBoolean());

        var essay = service().editPage(1, 102).form();
        assertEquals("", essay.value("scopeText"));
        var task = JsonParser.parseString(essay.value("tasksJson")).getAsJsonArray().get(0).getAsJsonObject();
        assertEquals("Saved task\nSaved details", task.get("title").getAsString());
        assertEquals("", task.get("description").getAsString());
    }

    @Test void unchangedInactiveTemplateRemainsValidAndKeepsTeacherContent() throws SQLException {
        var service = service();
        var page = service.editPage(1, 101);
        var original = page.form();
        var currentSource = page.context().templates().stream().filter(t -> t.templateId() == 2).findFirst().orElseThrow();
        assertEquals(original.value("title"), currentSource.title());
        assertEquals(original.value("instructions"), currentSource.instructions());
        assertTrue(currentSource.displayTitle().contains("đã ngừng hoạt động"));
        assertFalse(currentSource.title().contains("đã ngừng hoạt động"));
        service.updateAssignment(1, 101, with(original, "title", "Teacher revision", "instructions", "Own revised instructions"));
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM assignments WHERE assignment_id=101")) {
            assertTrue(rows.next());
            assertEquals(2L, rows.getLong("template_id"));
            assertEquals("Teacher revision", rows.getString("title"));
            assertEquals("Own revised instructions", rows.getString("instructions"));
            assertEquals("DRAFT", rows.getString("status"));
            assertNull(rows.getObject("published_at"));
            assertNull(rows.getObject("cancelled_at"));
        }
        var reloaded = service.editPage(1, 101).form();
        assertEquals("2", reloaded.value("templateId"));
        assertNotEquals(original.value("revision"), reloaded.value("revision"));
    }

    @Test void updatesFieldsAndReordersQuestionsAndOptionsWithoutReplacingTheirIds() throws SQLException {
        var service = service();
        var original = service.editPage(1, 101).form();
        var entered = questions(original);
        var first = entered.get(0).getAsJsonObject();
        var second = entered.get(1).getAsJsonObject();
        first.addProperty("question_text", "First question revised");
        first.addProperty("maximum_points", "4.25");
        second.addProperty("maximum_points", "25.75");
        var reorderedOptions = new JsonArray();
        var alternative = options(first).get(1).getAsJsonObject();
        alternative.addProperty("option_text", "Alternative revised");
        reorderedOptions.add(alternative);
        reorderedOptions.add(options(first).get(0));
        first.add("options", reorderedOptions);
        var reordered = new JsonArray();
        reordered.add(second);
        reordered.add(first);
        service.updateAssignment(1, 101, with(original, "templateId", "1", "title", "Updated own title",
                "instructions", "Updated own instructions", "maximumScore", "30", "availableFrom", "2026-10-15T09:15",
                "dueAt", "2026-10-16T18:45", "timeLimitMinutes", "40", "attemptLimit", "5",
                "questionsJson", reordered.toString()));

        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM assignments WHERE assignment_id=101")) {
            assertTrue(rows.next());
            assertAll(
                    () -> assertEquals(10L, rows.getLong("class_id")),
                    () -> assertEquals(1L, rows.getLong("template_id")),
                    () -> assertEquals("Updated own title", rows.getString("title")),
                    () -> assertEquals("Updated own instructions", rows.getString("instructions")),
                    () -> assertEquals(0,new BigDecimal("30").compareTo(rows.getBigDecimal("maximum_score"))),
                    () -> assertEquals(Instant.parse("2026-10-15T02:15:00Z"), rows.getTimestamp("available_from").toInstant()),
                    () -> assertEquals(Instant.parse("2026-10-16T11:45:00Z"), rows.getTimestamp("due_at").toInstant()),
                    () -> assertEquals(40, rows.getInt("time_limit_minutes")),
                    () -> assertEquals(5, rows.getInt("attempt_limit")),
                    () -> assertEquals("DRAFT", rows.getString("status")));
        }
        var saved = savedQuestions(101);
        assertEquals(List.of(1002L, 1001L), questionIds(saved));
        assertEquals(1, saved.get(0).getAsJsonObject().get("question_order").getAsInt());
        assertEquals(2, saved.get(1).getAsJsonObject().get("question_order").getAsInt());
        var revised = saved.get(1).getAsJsonObject();
        assertEquals("First question revised", revised.get("question_text").getAsString());
        assertEquals(new BigDecimal("4.25"), new BigDecimal(revised.get("maximum_points").getAsString()));
        assertEquals(List.of("12", "11"), optionIds(revised));
        assertEquals("Alternative revised", options(revised).get(0).getAsJsonObject().get("option_text").getAsString());
        assertEquals(1, options(revised).get(0).getAsJsonObject().get("option_order").getAsInt());
        assertEquals(2, options(revised).get(1).getAsJsonObject().get("option_order").getAsInt());
        assertEquals(List.of("21", "22"), optionIds(saved.get(0).getAsJsonObject()));
    }

    @Test void addsAndDeletesQuestionsAndOptionsWhilePreservingSurvivingIds() throws SQLException {
        var service = service();
        var original = service.editPage(1, 101).form();
        var kept = questions(original).get(0).getAsJsonObject();
        var keptOptions = new JsonArray();
        var surviving = options(kept).get(1).getAsJsonObject();
        surviving.addProperty("is_correct", true);
        keptOptions.add(surviving);
        keptOptions.add(JsonParser.parseString("{\"option_text\":\"New alternative\",\"is_correct\":false}"));
        kept.add("options", keptOptions);
        var desired = new JsonArray();
        desired.add(kept);
        desired.add(JsonParser.parseString("""
                {"question_text":"New question","maximum_points":"17.50",
                 "options":[{"option_text":"New correct answer","is_correct":true},
                            {"option_text":"New wrong answer","is_correct":false}]}
                """));
        service.updateAssignment(1, 101, with(original, "questionsJson", desired.toString()));

        var saved = savedQuestions(101);
        assertEquals(2, saved.size());
        assertEquals(1001L, saved.get(0).getAsJsonObject().get("question_id").getAsLong());
        var newQuestion = saved.get(1).getAsJsonObject();
        long newId = newQuestion.get("question_id").getAsLong();
        assertTrue(newId > 0);
        assertFalse(Set.of(1001L, 1002L, 2001L).contains(newId));
        assertEquals("New question", newQuestion.get("question_text").getAsString());
        assertEquals(2, newQuestion.get("question_order").getAsInt());
        var ids = optionIds(saved.get(0).getAsJsonObject());
        assertEquals("12", ids.get(0));
        assertFalse(ids.get(1).isBlank());
        assertFalse(Set.of("11", "12").contains(ids.get(1)), "New options must not reuse a saved option identity");
        assertEquals(2, Set.copyOf(optionIds(newQuestion)).size());
        assertTrue(optionIds(newQuestion).stream().noneMatch(String::isBlank));
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT count(*) FROM questions WHERE question_id=1002")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
        }
        assertEquals(List.of(2001L), questionIds(savedQuestions(201)));
        // Newly generated string identities must survive form prefill and the next save.
        var reloaded = service.editPage(1, 101).form();
        assertEquals(ids, optionIds(questions(reloaded).get(0).getAsJsonObject()));
        assertEquals(optionIds(newQuestion), optionIds(questions(reloaded).get(1).getAsJsonObject()));
        service.updateAssignment(1, 101, with(reloaded, "title", "Save retaining generated option IDs"));
        assertEquals(saved, savedQuestions(101));
    }

    @Test void rejectsForeignUnknownAndDuplicateQuestionIdentitiesWithoutChangingData() throws SQLException {
        var service = service();
        var form = service.editPage(1, 101).form();
        var before = snapshot(101);
        for (long invalidId : new long[]{2001, 999999}) {
            var entered = questions(form);
            entered.get(0).getAsJsonObject().addProperty("question_id", invalidId);
            var ex = assertThrows(ValidationException.class,
                    () -> service.updateAssignment(1, 101, with(form, "questionsJson", entered.toString())));
            assertTrue(ex.errors().containsKey("questionsJson"));
            assertEquals(before, snapshot(101));
        }
        var duplicates = questions(form);
        // Keep the points total valid so this test specifically reaches identity validation.
        duplicates.get(1).getAsJsonObject().addProperty("question_id",1001);
        assertThrows(ValidationException.class,
                () -> service.updateAssignment(1, 101, with(form, "questionsJson", duplicates.toString())));
        assertEquals(before, snapshot(101));
    }

    @Test void rejectsWrongQuestionUnknownAndDuplicateOptionIdentitiesWithoutChangingData() throws SQLException {
        var service = service();
        var form = service.editPage(1, 101).form();
        var before = snapshot(101);
        for (long invalidId : new long[]{21, 999999}) {
            var entered = questions(form);
            options(entered.get(0).getAsJsonObject()).get(0).getAsJsonObject().addProperty("option_id", invalidId);
            var ex = assertThrows(ValidationException.class,
                    () -> service.updateAssignment(1, 101, with(form, "questionsJson", entered.toString())));
            assertTrue(ex.errors().containsKey("questionsJson"));
            assertEquals(before, snapshot(101));
        }
        var duplicates = questions(form);
        var answerOptions = options(duplicates.get(0).getAsJsonObject());
        answerOptions.get(1).getAsJsonObject().addProperty("option_id", 11);
        assertThrows(ValidationException.class,
                () -> service.updateAssignment(1, 101, with(form, "questionsJson", duplicates.toString())));
        assertEquals(before, snapshot(101));
    }

    @Test void rejectsClassChangesAndIneligibleChangedTemplatesWithoutChangingData() throws SQLException {
        var service = service();
        var form = service.editPage(1, 101).form();
        var before = snapshot(101);
        for (String classId : List.of("11", "20")) {
            var ex = assertThrows(ValidationException.class,
                    () -> service.updateAssignment(1, 101, with(form, "classId", classId)));
            assertTrue(ex.errors().containsKey("classId"));
            assertEquals(before, snapshot(101));
        }
        for (String templateId : List.of("3", "4", "999999")) {
            var ex = assertThrows(ValidationException.class,
                    () -> service.updateAssignment(1, 101, with(form, "templateId", templateId)));
            assertTrue(ex.errors().containsKey("templateId"));
            assertEquals(before, snapshot(101));
        }
    }

    @Test void deniesMissingOrForeignAssignmentsAndInactiveOrWrongRoleActors() throws SQLException {
        var service = service();
        var valid = service.editPage(1, 101).form();
        var before = snapshot(101);
        for (long assignmentId : new long[]{201, 999999}) {
            assertThrows(NoSuchElementException.class, () -> service.editPage(1, assignmentId));
            assertThrows(NoSuchElementException.class,
                    () -> service.updateAssignment(1, assignmentId, with(valid, "assignmentId", Long.toString(assignmentId))));
        }
        for (long actor : new long[]{2, 6}) {
            assertThrows(NoSuchElementException.class, () -> service.editPage(actor, 101));
            assertThrows(NoSuchElementException.class, () -> service.updateAssignment(actor, 101, valid));
        }
        for (long actor : new long[]{3, 4, 5, 999999}) {
            assertThrows(SecurityException.class, () -> service.editPage(actor, 101));
            assertThrows(SecurityException.class, () -> service.updateAssignment(actor, 101, valid));
        }
        assertThrows(SecurityException.class, () -> service.editPage(3, 301));
        assertThrows(SecurityException.class, () -> service.editPage(4, 401));
        assertEquals(before, snapshot(101));
    }

    @Test void refusesPublishedAndCancelledAssignmentsForBothLoadAndSave() throws SQLException {
        var service = service();
        var draft = service.editPage(1, 102).form();
        for (long assignmentId : new long[]{103, 104}) {
            var before = snapshot(assignmentId);
            assertThrows(AssignmentEditConflictException.class, () -> service.editPage(1, assignmentId));
            assertThrows(AssignmentEditConflictException.class,
                    () -> service.updateAssignment(1, assignmentId, with(draft, "assignmentId", Long.toString(assignmentId))));
            assertEquals(before, snapshot(assignmentId));
        }
    }

    @Test void staleRevisionCannotOverwriteTheLatestSavedContent() throws SQLException {
        var service = service();
        var firstEditor = service.editPage(1, 101).form();
        var secondEditor = service.editPage(1, 101).form();
        service.updateAssignment(1, 101, with(firstEditor, "title", "First editor saved", "instructions", "Latest instructions"));
        var saved = snapshot(101);
        var reloaded = service.editPage(1, 101).form();
        assertNotEquals(firstEditor.value("revision"), reloaded.value("revision"));
        assertThrows(AssignmentEditConflictException.class,
                () -> service.updateAssignment(1, 101, with(secondEditor, "title", "Stale editor title",
                        "instructions", "Stale instructions", "questionsJson", "[]")));
        assertEquals(saved, snapshot(101));
        assertEquals("First editor saved", service.editPage(1, 101).form().value("title"));
    }

    @Test void nestedUpdateFailureRollsBackAllParentAndQuestionChanges() throws SQLException {
        var normal = service();
        var form = normal.editPage(1, 101).form();
        var before = snapshot(101);
        var entered = questions(form);
        entered.remove(0);
        entered.get(0).getAsJsonObject().addProperty("question_text", "Changed before injected failure");
        entered.add(JsonParser.parseString("""
                {"question_text":"Inserted before injected failure","maximum_points":"22.50",
                 "options":[{"option_text":"Answer","is_correct":true}]}
                """));
        var failing = new AssignmentDAO() {
            @Override public void replaceDraftQuestions(Connection c, long assignmentId,
                    List<Question> existing, List<Question> desired) throws SQLException {
                super.replaceDraftQuestions(c, assignmentId, existing, desired);
                throw new SQLException("Injected failure after nested replacement");
            }
        };
        assertThrows(IllegalStateException.class,
                () -> service(failing).updateAssignment(1, 101, with(form, "templateId", "1",
                        "title", "Parent update that must roll back", "maximumScore", "40",
                        "questionsJson", entered.toString())));
        assertEquals(before, snapshot(101));
        assertEquals(form.value("revision"), normal.editPage(1, 101).form().value("revision"));
        assertEquals(List.of(2001L), questionIds(savedQuestions(201)));
    }

    @Test void switchingToEssayDeletesSavedObjectiveContentAndSavesOrderedTasks() throws SQLException {
        var service = service();
        var original = service.editPage(1, 101).form();
        service.updateAssignment(1, 101, with(original, "purpose", "REGULAR", "responseType", "ESSAY",
                "attemptLimit", "", "scopeText", "UML & Java essay scope", "questionsJson", "{broken inactive questions",
                "tasksJson", """
                        [{"task_order":99,"title":"Essay task one","description":"First description","maximum_points":"10"},
                         {"task_order":4,"title":"Essay task two","description":"Second description","maximum_points":"10"}]
                        """));
        assertTrue(savedQuestions(101).isEmpty());
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM assignments WHERE assignment_id=101")) {
            assertTrue(rows.next());
            assertEquals("ESSAY", rows.getString("response_type"));
            assertEquals("REGULAR", rows.getString("assignment_purpose"));
            assertEquals("", rows.getString("scope_text"));
            assertNull(rows.getObject("attempt_limit"));
            var tasks = JsonParser.parseString(rows.getString("task_checklist")).getAsJsonArray();
            assertEquals(2, tasks.size());
            assertEquals(1, tasks.get(0).getAsJsonObject().get("task_order").getAsInt());
            assertEquals(2, tasks.get(1).getAsJsonObject().get("task_order").getAsInt());
            assertEquals("Essay task one\nFirst description", tasks.get(0).getAsJsonObject().get("title").getAsString());
            assertEquals("", tasks.get(0).getAsJsonObject().get("description").getAsString());
            assertEquals("Essay task two\nSecond description", tasks.get(1).getAsJsonObject().get("title").getAsString());
            assertEquals("", tasks.get(1).getAsJsonObject().get("description").getAsString());
        }
        assertEquals(List.of(2001L), questionIds(savedQuestions(201)));
    }

    @Test void switchingToObjectiveClearsSavedEssayScopeAndTasks() throws SQLException {
        var service = service();
        var original = service.editPage(1, 102).form();
        service.updateAssignment(1, 102, with(original, "responseType", "OBJECTIVE", "scopeText", "Inactive scope",
                "tasksJson", "{broken inactive tasks", "questionsJson", """
                        [{"question_text":"Objective replacement","maximum_points":"15",
                          "options":[{"option_text":"Answer","is_correct":true}]}]
                        """));
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM assignments WHERE assignment_id=102")) {
            assertTrue(rows.next());
            assertEquals("OBJECTIVE", rows.getString("response_type"));
            assertEquals("", rows.getString("scope_text"));
            assertEquals("[]", rows.getString("task_checklist"));
            assertEquals("DRAFT", rows.getString("status"));
        }
        var saved = savedQuestions(102);
        assertEquals(1, saved.size());
        assertEquals("Objective replacement", saved.get(0).getAsJsonObject().get("question_text").getAsString());
        assertTrue(saved.get(0).getAsJsonObject().get("question_id").getAsLong() > 0);
        assertFalse(optionIds(saved.get(0).getAsJsonObject()).get(0).isBlank());
    }
}
