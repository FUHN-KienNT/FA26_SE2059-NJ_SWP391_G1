package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import jakarta.servlet.http.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import vn.edu.fpt.ams.controller.StudentAssignmentServlet;
import vn.edu.fpt.ams.dao.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.*;

/** Opt-in PostgreSQL TEMP fixtures only; public data and public identity sequences are never changed. */
class StudentEssayDatabaseTest {
    private static final String TASKS = """
            [{"task_order":1,"title":"Explain and draw","description":"","maximum_points":5,"requirements":[
              {"requirement_order":1,"title":"Explain","maximum_points":2,"answer_type":"TEXT"},
              {"requirement_order":2,"title":"Draw","maximum_points":3,"answer_type":"DIAGRAM"}]},
             {"task_order":2,"title":"Discuss model","description":"","maximum_points":5,"requirements":[
              {"requirement_order":1,"title":"Discuss and draw","maximum_points":5,"answer_type":"BOTH"}]}]
            """;
    private Connection connection;

    @BeforeEach void setup() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("assignment.db.tests"));
        connection = DBConnection.getConnection();
        try (var statement = connection.createStatement()) {
            statement.execute("SET search_path TO pg_temp");
            statement.execute("CREATE TEMP TABLE users(user_id BIGINT PRIMARY KEY,role TEXT,account_status TEXT)");
            statement.execute("CREATE TEMP TABLE subject_classes(class_id BIGINT PRIMARY KEY,subject_term_id BIGINT,class_code TEXT,status TEXT)");
            statement.execute("CREATE TEMP TABLE subject_registrations(registration_id BIGINT PRIMARY KEY,student_id BIGINT,subject_term_id BIGINT,status TEXT)");
            statement.execute("CREATE TEMP TABLE class_enrollments(enrollment_id BIGINT PRIMARY KEY,registration_id BIGINT,class_id BIGINT,status TEXT)");
            statement.execute("CREATE TEMP TABLE assignments(LIKE public.assignments INCLUDING ALL)");
            statement.execute("CREATE TEMP TABLE questions(LIKE public.questions INCLUDING ALL)");
            statement.execute("CREATE TEMP TABLE submissions(LIKE public.submissions INCLUDING ALL)");
            statement.execute("ALTER TABLE submissions ALTER COLUMN submission_id DROP IDENTITY IF EXISTS");
            statement.execute("CREATE TEMP SEQUENCE student_essay_test_ids");
            statement.execute("ALTER TABLE submissions ALTER COLUMN submission_id SET DEFAULT nextval('student_essay_test_ids')");
            statement.execute("CREATE TEMP TABLE submission_content_snapshots(submission_id BIGINT PRIMARY KEY,assignment_data JSONB,questions_data JSONB)");
            statement.execute("INSERT INTO users VALUES(1,'STUDENT','ACTIVE'),(2,'STUDENT','ACTIVE'),(3,'TEACHER','ACTIVE')");
            statement.execute("INSERT INTO subject_classes VALUES(10,100,'ESSAY A','ACTIVE'),(20,200,'FOREIGN','ACTIVE')");
            statement.execute("INSERT INTO subject_registrations VALUES(11,1,100,'ACTIVE'),(22,2,200,'ACTIVE'),(33,3,100,'ACTIVE')");
            statement.execute("INSERT INTO class_enrollments VALUES(1,11,10,'ACTIVE'),(2,22,20,'ACTIVE'),(3,33,10,'ACTIVE')");
        }
        try (var statement = connection.prepareStatement("""
                INSERT INTO assignments(assignment_id,class_id,template_id,title,assignment_purpose,response_type,
                  instructions,task_checklist,maximum_score,status,available_from,due_at,time_limit_minutes,published_at)
                VALUES(50,10,1,'Essay original','REGULAR','ESSAY','Original instructions',?::jsonb,10,'PUBLISHED',
                  CURRENT_TIMESTAMP-INTERVAL '1 day',CURRENT_TIMESTAMP+INTERVAL '1 day',20,CURRENT_TIMESTAMP-INTERVAL '1 day')
                """)) { statement.setString(1, TASKS); statement.executeUpdate(); }
    }
    @AfterEach void close() throws Exception { if (connection != null) connection.close(); }

    @Test void startingSnapshotsQuestionRequirementsAndResumingKeepsTheSameAttempt() throws Exception {
        var service = service(); long id = service.start(1, 50);
        assertEquals(id, service.start(1, 50)); assertEquals(1, count("submissions")); assertEquals(1, count("submission_content_snapshots"));
        sql("UPDATE assignments SET title='Changed live title',instructions='Changed live instructions',task_checklist='[]'");
        var page = service.page(1, 50, id);
        assertEquals("Essay original", page.assignment().title()); assertEquals("Original instructions", page.assignment().instructions());
        assertEquals(JsonParser.parseString(TASKS), JsonParser.parseString(page.assignment().taskChecklistJson()));
        assertEquals(3, EssayAnswerCodec.normalize(page.assignment().taskChecklistJson(), page.essayContent()).answers().size());
        assertEquals(SubmissionStatus.IN_PROGRESS, page.status()); assertTrue(page.questions().isEmpty());
        service.saveEssay(1, 50, id, 0, text("Snapshot-scoped answer"));
        assertEquals("Snapshot-scoped answer", answers(service.page(1, 50, id).essayContent()).get("1:1").text());
        assertEquals(id, service.start(1, 50));
    }

    @Test void autosaveReloadsEachRequirementTextAndDiagramWithoutAnyScore() throws Exception {
        var service = service(); long id = service.start(1, 50);
        String firstXml = "<mxGraphModel><root><mxCell id=\"first\"/></root></mxGraphModel>";
        String secondXml = "<mxfile><diagram name=\"second\">second-model</diagram></mxfile>";
        String firstPng = png(0xffff0000), secondPng = png(0xff0000ff);
        String payload = payload(row(1, 1, "Written answer", "", ""), row(1, 2, "", firstXml, firstPng), row(2, 1, "Model reasoning", secondXml, secondPng));
        var saved = service.saveEssay(1, 50, id, 0, payload);
        assertFalse(saved.finished()); assertEquals(1, saved.revision());
        var page = service.page(1, 50, id); assertEquals(1, page.answerRevision());
        var restored = answers(page.essayContent());
        assertEquals("Written answer", restored.get("1:1").text()); assertEquals("", restored.get("1:1").xml());
        assertEquals(firstXml, restored.get("1:2").xml()); assertEquals(firstPng, restored.get("1:2").image());
        assertEquals("", restored.get("1:2").text()); assertEquals("Model reasoning", restored.get("2:1").text());
        assertEquals(secondXml, restored.get("2:1").xml()); assertEquals(secondPng, restored.get("2:1").image());
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT * FROM submissions")) {
            assertTrue(result.next()); assertEquals("IN_PROGRESS", result.getString("status"));
            assertEquals(page.essayContent(), result.getString("content"));
            assertNull(result.getObject("score")); assertNull(result.getObject("submitted_at")); assertNull(result.getObject("graded_at")); assertNull(result.getObject("released_at"));
            assertEquals("[]", result.getString("answer"));
        }
    }

    @Test void submittingFinalizesWithoutGradingAndDoubleSubmitCannotOverwrite() throws Exception {
        var service = service(); long id = service.start(1, 50);
        assertEquals(id, service.submitEssay(1, 50, id, 0, text("Final answer")));
        String content = savedContent();
        assertEquals(id, service.submitEssay(1, 50, id, 0, text("Late replacement")));
        assertTrue(service.saveEssay(1, 50, id, 0, text("Another replacement")).finished()); assertEquals(content, savedContent());
        assertEquals(SubmissionStatus.SUBMITTED, service.page(1, 50, id).status()); assertNull(service.page(1, 50, id).essayContent());
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT * FROM submissions")) {
            assertTrue(result.next()); assertEquals("SUBMITTED", result.getString("status")); assertNotNull(result.getObject("submitted_at"));
            assertNull(result.getObject("score")); assertNull(result.getObject("graded_by")); assertNull(result.getObject("graded_at")); assertNull(result.getObject("released_at"));
            assertEquals(1, result.getLong("answer_revision"));
        }
        assertThrows(IllegalArgumentException.class, () -> service.start(1, 50));
    }

    @Test void staleAutosaveAndSubmitReturnConflictWithoutReplacingNewerContent() throws Exception {
        var service = service(); long id = service.start(1, 50);
        service.saveEssay(1, 50, id, 0, text("Newest answer"));
        assertThrows(ConcurrentModificationException.class, () -> service.saveEssay(1, 50, id, 0, text("Stale autosave")));
        assertThrows(ConcurrentModificationException.class, () -> service.submitEssay(1, 50, id, 0, text("Stale submit")));
        assertEquals(409, post(service, id, "saveEssay", 0, text("HTTP stale autosave")));
        assertEquals(409, post(service, id, "submitEssay", 0, text("HTTP stale submit")));
        var page = service.page(1, 50, id); assertEquals(1, page.answerRevision()); assertEquals(SubmissionStatus.IN_PROGRESS, page.status());
        assertEquals("Newest answer", answers(page.essayContent()).get("1:1").text());
        service.submitEssay(1, 50, id, 1, page.essayContent()); assertEquals(SubmissionStatus.SUBMITTED, service.page(1, 50, id).status());
    }

    @Test void exactDeadlineIgnoresEvenMalformedLatePayloadAndRetainsSavedContent() throws Exception {
        Instant[] clock = {Instant.parse("2026-10-16T11:30:00Z")};
        var service = service(new AssignmentDAO() { @Override public Instant currentTime(Connection c) { return clock[0]; } });
        sql("UPDATE assignments SET available_from='2026-10-16T10:00:00Z',due_at='2026-10-16T12:00:00Z',published_at='2026-10-16T09:00:00Z'");
        long id = service.start(1, 50); service.saveEssay(1, 50, id, 0, text("Before deadline")); String saved = savedContent();
        clock[0] = Instant.parse("2026-10-16T11:50:00Z");
        assertTrue(service.saveEssay(1, 50, id, 0, "malformed late data").finished());
        service.submitEssay(1, 50, id, 0, text("Late answer"));
        assertEquals(saved, savedContent()); assertSubmittedAtDeadline();
    }

    @Test void workerFinalizesWithoutBrowserAndPreservesPerRequirementDiagramsAndLegacyContent() throws Exception {
        var service = service(); long id = service.start(1, 50);
        String input = payload(row(1, 2, "", "<mxGraphModel/>", png(0xff112233)), row(2, 1, "Reason", "<mxfile/>", png(0xff778899)));
        service.saveEssay(1, 50, id, 0, input); String saved = savedContent();
        sql("UPDATE submissions SET started_at=CURRENT_TIMESTAMP-INTERVAL '21 minutes',deadline_at=CURRENT_TIMESTAMP-INTERVAL '1 minute'");
        assertEquals(1, service.finishOverdue()); assertEquals(0, service.finishOverdue());
        assertEquals(saved, savedContent()); assertSubmittedAtDeadline();
        assertEquals(id, service.submitEssay(1, 50, id, 0, text("Late answer"))); assertEquals(saved, savedContent());
        // A second enrolled student proves malformed historical work does not disappear during worker closure.
        sql("INSERT INTO users VALUES(4,'STUDENT','ACTIVE');INSERT INTO subject_registrations VALUES(44,4,100,'ACTIVE');INSERT INTO class_enrollments VALUES(4,44,10,'ACTIVE')");
        long legacy = service.start(4, 50);
        try (var statement = connection.prepareStatement("UPDATE submissions SET content=?,started_at=CURRENT_TIMESTAMP-INTERVAL '21 minutes',deadline_at=CURRENT_TIMESTAMP-INTERVAL '1 minute' WHERE submission_id=?")) {
            statement.setString(1, "Historical unstructured essay"); statement.setLong(2, legacy); statement.executeUpdate(); connection.commit();
        }
        assertEquals(1, service.finishOverdue());
        try (var statement = connection.prepareStatement("SELECT content,status,score,graded_at FROM submissions WHERE submission_id=?")) {
            statement.setLong(1, legacy); try (var result = statement.executeQuery()) {
                assertTrue(result.next()); assertEquals("Historical unstructured essay", result.getString(1)); assertEquals("SUBMITTED", result.getString(2));
                assertNull(result.getObject(3)); assertNull(result.getObject(4));
            }
        }
    }

    @Test void ownershipActiveMembershipAndEssayTypeAreRequiredForEveryWrite() throws Exception {
        var service = service();
        assertEquals(List.of(50L), service.list(1).stream().map(x -> x.assignment().assignmentId()).toList());
        assertTrue(service.list(2).isEmpty()); assertTrue(service.list(3).isEmpty());
        assertThrows(NoSuchElementException.class, () -> service.start(2, 50)); assertThrows(NoSuchElementException.class, () -> service.start(3, 50));
        long id = service.start(1, 50);
        sql("INSERT INTO users VALUES(4,'STUDENT','ACTIVE');INSERT INTO subject_registrations VALUES(44,4,100,'ACTIVE');INSERT INTO class_enrollments VALUES(4,44,10,'ACTIVE')");
        long peer = service.start(4, 50); assertNotEquals(id, peer);
        assertThrows(NoSuchElementException.class, () -> service.page(4, 50, id));
        assertThrows(NoSuchElementException.class, () -> service.saveEssay(4, 50, id, 0, text("Peer's attempt")));
        assertThrows(NoSuchElementException.class, () -> service.submitEssay(4, 50, id, 0, text("Peer's attempt")));
        assertThrows(NoSuchElementException.class, () -> service.page(2, 50, id));
        assertThrows(NoSuchElementException.class, () -> service.saveEssay(2, 50, id, 0, text("Foreign")));
        assertThrows(NoSuchElementException.class, () -> service.submitEssay(2, 50, id, 0, text("Foreign")));
        assertThrows(SecurityException.class, () -> service.saveEssay(0, 50, id, 0, text("Anonymous")));
        sql("UPDATE class_enrollments SET status='INACTIVE' WHERE enrollment_id=1");
        assertThrows(NoSuchElementException.class, () -> service.saveEssay(1, 50, id, 0, text("Inactive")));
        sql("UPDATE class_enrollments SET status='ACTIVE';UPDATE assignments SET status='CANCELLED',cancelled_at=CURRENT_TIMESTAMP");
        assertThrows(NoSuchElementException.class, () -> service.submitEssay(1, 50, id, 0, text("Cancelled")));
        assertEquals(0, service.finishOverdue());
        sql("UPDATE assignments SET status='PUBLISHED',cancelled_at=NULL,response_type='OBJECTIVE'");
        assertThrows(IllegalArgumentException.class, () -> service.saveEssay(1, 50, id, 0, text("Wrong type")));
        assertNull(savedContent()); assertEquals(2, count("submissions"));
    }

    private StudentObjectiveService service() { return service(new AssignmentDAO()); }
    private StudentObjectiveService service(AssignmentDAO clock) {
        Connection borrowed = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (p, method, args) -> {
            if (method.getName().equals("close")) return null;
            try { return method.invoke(connection, args); } catch (InvocationTargetException ex) { throw ex.getCause(); }
        });
        return new StudentObjectiveService(new StudentObjectiveDAO(), clock, () -> borrowed);
    }
    private void sql(String sql) throws Exception {
        try (var statement = connection.createStatement()) { statement.execute(sql); } if (!connection.getAutoCommit()) connection.commit();
    }
    private long count(String table) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT count(*) FROM " + table)) { result.next(); return result.getLong(1); }
    }
    private String savedContent() throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT content FROM submissions ORDER BY submission_id LIMIT 1")) { result.next(); return result.getString(1); }
    }
    private void assertSubmittedAtDeadline() throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT * FROM submissions ORDER BY submission_id LIMIT 1")) {
            assertTrue(result.next()); assertEquals("SUBMITTED", result.getString("status")); assertEquals(result.getObject("deadline_at"), result.getObject("submitted_at"));
            assertNull(result.getObject("score")); assertNull(result.getObject("graded_at")); assertNull(result.getObject("released_at"));
        }
    }
    private static Map<String, EssayAnswerCodec.Answer> answers(String input) { return EssayAnswerCodec.normalize(TASKS, input).answers(); }
    private static String text(String text) { return payload(row(1, 1, text, "", "")); }
    private static JsonObject row(int task, int requirement, String text, String xml, String image) {
        JsonObject row = new JsonObject(); row.addProperty("task_order", task); row.addProperty("requirement_order", requirement);
        row.addProperty("text", text); row.addProperty("diagram_xml", xml); row.addProperty("diagram_image", image); return row;
    }
    private static String payload(JsonObject... answers) {
        JsonArray rows = new JsonArray(); for (var row : answers) rows.add(row);
        JsonObject root = new JsonObject(); root.addProperty("format", EssayAnswerCodec.FORMAT); root.add("answers", rows); return root.toString();
    }
    private static String png(int color) throws Exception {
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB); image.setRGB(0, 0, color); var bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", bytes)); return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
    private static int post(StudentObjectiveService service, long id, String action, long revision, String content) throws Exception {
        var constructor = StudentAssignmentServlet.class.getDeclaredConstructor(StudentObjectiveService.class); constructor.setAccessible(true);
        var servlet = constructor.newInstance(service);
        Map<String, Object> attributes = Map.of("userId", 1L, "role", UserRole.STUDENT, "accountStatus", AccountStatus.ACTIVE, "csrf", "test-token");
        HttpSession session = (HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(), new Class<?>[]{HttpSession.class},
                (p, method, args) -> method.getName().equals("getAttribute") ? attributes.get(args[0]) : null);
        Map<String, String> parameters = Map.of("csrf", "test-token", "assignmentId", "50", "submissionId", Long.toString(id),
                "action", action, "answerRevision", Long.toString(revision), "essayContent", content);
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (p, method, args) -> switch (method.getName()) {
                    case "getSession" -> session;
                    case "getParameter" -> parameters.get(args[0]);
                    case "getContentLengthLong" -> 100L;
                    default -> null;
                });
        int[] status = {200};
        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (p, method, args) -> { if (method.getName().equals("sendError") || method.getName().equals("setStatus")) status[0] = (Integer) args[0]; return null; });
        Method doPost = StudentAssignmentServlet.class.getDeclaredMethod("doPost", HttpServletRequest.class, HttpServletResponse.class); doPost.setAccessible(true);
        doPost.invoke(servlet, request, response); return status[0];
    }
}
