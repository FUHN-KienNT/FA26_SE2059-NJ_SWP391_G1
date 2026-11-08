package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.StudentClassFilter;
import vn.edu.fpt.ams.dto.StudentClassPage.AssignmentRow;
import vn.edu.fpt.ams.dto.StudentClassPage.Counts;
import vn.edu.fpt.ams.util.DBConnection;
import vn.edu.fpt.ams.util.Jdbc;

/** Actual PostgreSQL queries against connection-local tables; application tables are never changed. */
class StudentClassPostgresTest {
    private static final StudentClassFilter ALL = StudentClassFilter.from(null, null, null, null);
    private Connection connection;
    private StudentClassService service;
    private final List<String> serviceQueries = new ArrayList<>();
    private final List<Boolean> snapshots = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("assignment.db.tests"));
        connection = DBConnection.getConnection();
        createFixture(connection);
        Connection borrowed =
                (Connection)
                        Proxy.newProxyInstance(
                                Connection.class.getClassLoader(),
                                new Class<?>[] {Connection.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("close")) return null;

                                    if (method.getName().equals("prepareStatement")) {
                                        serviceQueries.add((String) args[0]);
                                        snapshots.add(
                                                connection.isReadOnly()
                                                        && !connection.getAutoCommit()
                                                        && connection.getTransactionIsolation()
                                                                == Connection
                                                                        .TRANSACTION_REPEATABLE_READ);
                                    }

                                    try {
                                        return method.invoke(connection, args);
                                    } catch (InvocationTargetException ex) {
                                        throw ex.getCause();
                                    }
                                });

        service = new StudentClassService(() -> borrowed);
    }

    @AfterEach
    void close() throws Exception {
        if (connection != null) connection.close();
    }

    @Test
    void selectsFirstOwnedActiveClassAndRejectsEveryOtherMembership() {
        var page = service.load(1, null, ALL);

        assertEquals(10, page.classroom().classId());
        assertEquals(
                List.of(10L, 11L, 12L), page.classes().stream().map(x -> x.classId()).toList());
        assertEquals("Software Project", page.classroom().subjectName());
        assertEquals("Teacher", page.classroom().teacherName());

        for (long id : new long[] {20, 30, 40, 50, 999, 0, -1})
            assertThrows(
                    NoSuchElementException.class, () -> service.load(1, id, ALL), "class=" + id);

        assertThrows(NoSuchElementException.class, () -> service.load(2, 11L, ALL));
        assertEquals(
                List.of(300L),
                service.load(2, 20L, ALL).assignments().stream()
                        .map(AssignmentRow::assignmentId)
                        .toList());
    }

    @Test
    void activeStudentMustBeValidatedAgainstDatabaseEvenWithAnEmptyClassList() {
        for (long id : new long[] {0, -1, 3, 4, 6, 7, 999})
            assertThrows(SecurityException.class, () -> service.load(id, null, ALL), "actor=" + id);

        var page = service.load(5, null, ALL.withPage(99));

        assertNull(page.classroom());
        assertTrue(page.classes().isEmpty());
        assertTrue(page.assignments().isEmpty());
        assertEquals(new Counts(0, 0, 0, 0), page.counts());
        assertEquals(0, page.totalPages());
        assertEquals(1, page.page());
        assertThrows(NoSuchElementException.class, () -> service.load(5, 10L, ALL));
    }

    @Test
    void thirteenCurrentAssignmentsUseTenRowPagesWithoutOverlap() throws Exception {
        Instant now = Instant.now();

        for (int i = 113; i <= 115; i++) {
            assignment(connection, i, 10, "New current assignment " + i, "REGULAR", "ESSAY",
                    "PUBLISHED", now.minusSeconds(60), now.plusSeconds(3600), now);
        }

        var first = service.load(1, 10L, ALL);
        var second = service.load(1, 10L, ALL.withPage(2));

        assertEquals(13, first.total());
        assertEquals(10, first.assignments().size());
        assertEquals(3, second.assignments().size());
        assertEquals(115, first.assignments().get(0).assignmentId());
        var identities = new java.util.HashSet<Long>();

        first.assignments().forEach(row -> assertTrue(identities.add(row.assignmentId())));
        second.assignments().forEach(row -> assertTrue(identities.add(row.assignmentId())));
        assertEquals(13, identities.size());
    }

    @Test
    void timeSortRunsBeforePagingWithStableTiesAndPreservesVisibility() throws Exception {
        Instant now = Instant.now();

        for (int i = 0; i < 13; i++) {
            int offset = Math.max(1, i);
            assignment(connection, 400 + i, 10, "Date sort " + i, "REGULAR", "ESSAY",
                    "PUBLISHED", now.minusSeconds(86400).plusSeconds(offset * 60L),
                    now.plusSeconds(7200).minusSeconds(offset * 60L), now);
        }

        assignment(connection, 500, 20, "Date sort foreign", "REGULAR", "ESSAY", "PUBLISHED",
                now.minusSeconds(172800), now.plusSeconds(86400), now);
        assignment(connection, 501, 10, "Date sort expired", "REGULAR", "ESSAY", "PUBLISHED",
                now.minusSeconds(172800), now.minusSeconds(60), now);
        assignment(connection, 502, 10, "Date sort closed", "REGULAR", "ESSAY", "PUBLISHED",
                now.minusSeconds(172800), now.plusSeconds(86400), now);
        Jdbc.update(connection, "UPDATE assignments SET closed_at=? WHERE assignment_id=502", now);
        var opening = StudentClassFilter.from("Date sort", "ESSAY", "", "1", "OPEN_ASC");
        var first = service.load(1, 10L, opening);
        var second = service.load(1, 10L, opening.withPage(2));
        var oldestFirst = List.of(401L, 400L, 402L, 403L, 404L, 405L, 406L, 407L, 408L, 409L);
        var newestFirst = List.of(412L, 411L, 410L, 409L, 408L, 407L, 406L, 405L, 404L, 403L);

        assertEquals(13, first.total());
        assertEquals(2, first.totalPages());
        assertEquals(oldestFirst, first.assignments().stream().map(AssignmentRow::assignmentId).toList());
        assertEquals(List.of(410L, 411L, 412L), second.assignments().stream().map(AssignmentRow::assignmentId).toList());
        assertEquals(first.assignments(), service.load(1, 10L, opening).assignments());
        assertEquals(newestFirst, service.load(1, 10L, opening.withSort("OPEN_DESC"))
                .assignments().stream().map(AssignmentRow::assignmentId).toList());
        assertEquals(oldestFirst, service.load(1, 10L, opening.withSort("DUE_DESC"))
                .assignments().stream().map(AssignmentRow::assignmentId).toList());
        assertEquals(newestFirst, service.load(1, 10L, opening.withSort("DUE_ASC"))
                .assignments().stream().map(AssignmentRow::assignmentId).toList());
        var clamped = service.load(1, 10L, opening.withPage(Integer.MAX_VALUE));

        assertEquals(2, clamped.page());
        assertEquals("OPEN_ASC", clamped.filter().sort());
        assertEquals(second.assignments(), clamped.assignments());
        Jdbc.update(connection, "UPDATE assignments SET available_from=NULL WHERE assignment_id=400");
        // The borrowed connection retains autoCommit=false after service calls.
        // Finish this fixture mutation before the next read-only snapshot starts.
        connection.commit();

        assertEquals(List.of(411L, 412L, 400L), service.load(1, 10L, opening.withPage(2))
                .assignments().stream().map(AssignmentRow::assignmentId).toList());
        assertEquals(List.of(402L, 401L, 400L), service.load(1, 10L, opening.withSort("OPEN_DESC").withPage(2))
                .assignments().stream().map(AssignmentRow::assignmentId).toList());
    }

    @Test
    void showsPublishedRegularAndPracticeAndOnlyLatestOwnAlignedAttempt() {
        var page = service.load(1, 10L, ALL);
        var rows = new ArrayList<>(page.assignments());

        assertEquals(10, page.total());
        assertEquals(10, rows.size());
        Map<Long, AssignmentRow> byId =
                rows.stream().collect(Collectors.toMap(AssignmentRow::assignmentId, x -> x));

        assertEquals(
                List.of("ESSAY", "OBJECTIVE"),
                rows.stream().map(AssignmentRow::responseType).distinct().sorted().toList());
        assertEquals(
                List.of("PRACTICE", "REGULAR"),
                rows.stream().map(AssignmentRow::purpose).distinct().sorted().toList());
        assertTrue(rows.stream().allMatch(x -> x.assignmentId() >= 100 && x.assignmentId() <= 112));
        assertFalse(byId.containsKey(104L));
        assertEquals(3, byId.get(100L).submissionId());
        assertEquals(2, byId.get(100L).attemptNumber());
        assertEquals("VOID", byId.get(100L).workStatus());
        assertEquals("GRADED", byId.get(101L).workStatus());
        assertEquals("RELEASED", byId.get(102L).workStatus());
        assertEquals("NOT_STARTED", byId.get(103L).workStatus());
        assertEquals(0, byId.get(103L).submissionId());
        assertEquals(0, byId.get(103L).attemptNumber());
        assertNull(byId.get(103L).submittedAt());
        assertFalse(byId.containsKey(105L));
        assertFalse(byId.containsKey(112L));
        assertEquals("GRADED", byId.get(107L).workStatus());
        assertEquals("GRADED", byId.get(108L).workStatus());
        assertEquals("IN_PROGRESS", byId.get(109L).workStatus());
        assertEquals(new Counts(10, 8, 4, 4), page.counts());
        assertTrue(
                serviceQueries.stream()
                        .noneMatch(
                                sql ->
                                        sql.contains("sub.score")
                                                || sql.contains("answer")
                                                || sql.contains("correct")));
    }

    @Test
    void combinesLiteralSearchTypeAndWorkStatusWithoutChangingClassCounts() {
        var page =
                service.load(
                        1, 10L, StudentClassFilter.from("pRaCtIcE", "objective", "released", "1"));

        assertEquals(
                List.of(102L),
                page.assignments().stream().map(AssignmentRow::assignmentId).toList());
        assertEquals(1, page.total());
        assertEquals(new Counts(10, 8, 4, 4), page.counts());

        for (String literal : new String[] {"%", "_", "\\", "<SCRIPT>", "50%_done\\review"}) {
            var matching = service.load(1, 10L, StudentClassFilter.from(literal, "", "", "1"));

            assertEquals(
                    List.of(110L),
                    matching.assignments().stream().map(AssignmentRow::assignmentId).toList(),
                    literal);
        }

        var none = service.load(1, 10L, StudentClassFilter.from("' OR 1=1 --", "", "", "1"));

        assertEquals(0, none.total());
        assertTrue(none.assignments().isEmpty());
        var graded = service.load(1, 10L, StudentClassFilter.from("", "", "GRADED", "1"));

        assertEquals(
                List.of(108L, 107L, 101L),
                graded.assignments().stream().map(AssignmentRow::assignmentId).toList());
        assertEquals(
                List.of(102L),
                service
                        .load(1, 10L, StudentClassFilter.from("", "", "RELEASED", "1"))
                        .assignments()
                        .stream()
                        .map(AssignmentRow::assignmentId)
                        .toList());
    }

    @Test
    void paginatesStablyClampsLargePagesAndKeepsNullDeadlineLast() {
        var first = service.load(1, 10L, ALL);

        assertEquals(
                List.of(111L, 110L, 109L, 108L, 107L, 106L, 103L, 102L, 101L, 100L),
                first.assignments().stream().map(AssignmentRow::assignmentId).toList());
        assertEquals(1, first.totalPages());
        var last = service.load(1, 10L, ALL.withPage(Integer.MAX_VALUE));

        assertEquals(1, last.page());
        assertEquals(1, last.filter().page());
        assertEquals(
                List.of(111L, 110L, 109L, 108L, 107L, 106L, 103L, 102L, 101L, 100L),
                last.assignments().stream().map(AssignmentRow::assignmentId).toList());
        var emptyClass = service.load(1, 11L, ALL.withPage(9));

        assertEquals(0, emptyClass.total());
        assertEquals(0, emptyClass.totalPages());
        assertEquals(1, emptyClass.page());
        assertEquals(11, emptyClass.classroom().classId());
    }

    @Test
    void retainedOwnAttemptsStillMatchTheOriginalClassAndSubjectTerm() throws Exception {
        Jdbc.update(
                connection,
                "INSERT INTO submissions VALUES (60,111,57,1,'SUBMITTED',?,NULL,42)",
                Instant.now());
        var row =
                service.load(1, 10L, StudentClassFilter.from("Ordinary", "", "", "1"))
                        .assignments()
                        .get(0);

        assertEquals(60, row.submissionId());
        assertEquals("SUBMITTED", row.workStatus());
    }

    @Test
    void loadingReadsOneSnapshotAndNeverFinalizesExpiredOrOtherAttempts() throws Exception {
        String before = snapshot(connection);

        service.load(1, 10L, ALL);
        service.load(1, 10L, ALL.withPage(2));
        service.load(5, null, ALL);
        assertEquals(before, snapshot(connection));
        assertFalse(snapshots.isEmpty());
        assertTrue(snapshots.stream().allMatch(Boolean::booleanValue));
        assertTrue(
                serviceQueries.stream()
                        .allMatch(
                                sql ->
                                        sql.stripLeading().startsWith("SELECT")
                                                || sql.stripLeading().startsWith("WITH")));
    }

    private static void createFixture(Connection c) throws SQLException {
        try (var s = c.createStatement()) {
            s.execute("SET search_path TO pg_temp");
        }

        for (String table :
                List.of(
                        "users(user_id BIGINT PRIMARY KEY,full_name TEXT,role TEXT,account_status TEXT)",
                        "subjects(subject_id BIGINT PRIMARY KEY,subject_code TEXT,subject_name TEXT)",
                        "academic_terms(term_id BIGINT PRIMARY KEY,term_code TEXT,term_name TEXT,starts_at TIMESTAMPTZ)",
                        "subject_terms(subject_term_id BIGINT PRIMARY KEY,subject_id BIGINT,term_id BIGINT)",
                        "subject_classes(class_id BIGINT PRIMARY KEY,subject_term_id BIGINT,class_code TEXT,class_name TEXT,teacher_id BIGINT,status TEXT)",
                        "subject_registrations(registration_id BIGINT PRIMARY KEY,student_id BIGINT,subject_term_id BIGINT,status TEXT)",
                        "class_enrollments(enrollment_id BIGINT PRIMARY KEY,registration_id BIGINT,class_id BIGINT,status TEXT)",
                        "assignments(assignment_id BIGINT PRIMARY KEY,class_id BIGINT,title TEXT,assignment_purpose TEXT,response_type TEXT,maximum_score NUMERIC(10,2),status TEXT,available_from TIMESTAMPTZ,due_at TIMESTAMPTZ,published_at TIMESTAMPTZ,closed_at TIMESTAMPTZ)",
                        "submissions(submission_id BIGINT PRIMARY KEY,assignment_id BIGINT,enrollment_id BIGINT,attempt_number INTEGER,status TEXT,submitted_at TIMESTAMPTZ,released_at TIMESTAMPTZ,score NUMERIC(10,2))"))
            try (var s = c.createStatement()) {
                s.execute("CREATE TEMP TABLE " + table);
            }

        Instant now = Instant.now();

        Jdbc.update(
                c,
                "INSERT INTO users VALUES (1,'Student One','STUDENT','ACTIVE'),(2,'Student Two','STUDENT','ACTIVE'),"
                        + "(3,'Teacher','TEACHER','ACTIVE'),(4,'Inactive','STUDENT','INACTIVE'),(5,'New Student','STUDENT','ACTIVE'),"
                        + "(6,'Admin','ADMIN','ACTIVE'),(7,'Expert','EXPERT','ACTIVE')");
        Jdbc.update(
                c,
                "INSERT INTO subjects VALUES (101,'SWP391','Software Project'),(102,'DBI202','Databases')");
        Jdbc.update(
                c,
                "INSERT INTO academic_terms VALUES (11,'CURRENT','Current Term',?),(12,'OLD','Old Term',?)",
                now.minusSeconds(86400),
                now.minusSeconds(86400 * 100));
        Jdbc.update(
                c, "INSERT INTO subject_terms VALUES (1001,101,11),(1002,102,11),(1003,101,12)");
        Jdbc.update(
                c,
                "INSERT INTO subject_classes VALUES (10,1001,'AAA','My Class',3,'ACTIVE'),"
                        + "(11,1001,'BBB','Another Class',3,'ACTIVE'),(12,1003,'OLD','Past Term Class',3,'ACTIVE'),"
                        + "(20,1002,'FOREIGN','Foreign Class',3,'ACTIVE'),(30,1001,'INACTIVE_CLASS','Inactive Class',3,'INACTIVE'),"
                        + "(40,1001,'INACTIVE_ENROLLMENT','Inactive Enrollment',3,'ACTIVE'),"
                        + "(50,1002,'INACTIVE_REGISTRATION','Inactive Registration',3,'ACTIVE')");
        Jdbc.update(
                c,
                "INSERT INTO subject_registrations VALUES (1,1,1001,'ACTIVE'),(2,2,1002,'ACTIVE'),"
                        + "(3,1,1003,'ACTIVE'),(4,1,1002,'INACTIVE'),(21,2,1001,'ACTIVE')");
        Jdbc.update(
                c,
                "INSERT INTO class_enrollments VALUES (11,1,10,'ACTIVE'),(12,1,11,'ACTIVE'),(13,3,12,'ACTIVE'),"
                        + "(21,21,10,'ACTIVE'),(22,2,20,'ACTIVE'),(30,1,30,'ACTIVE'),(40,1,40,'INACTIVE'),"
                        + "(50,4,50,'ACTIVE'),(55,1,20,'ACTIVE'),(56,4,10,'ACTIVE'),(57,1,10,'INACTIVE')");
        String[] titles = {
            "Regular Objective",
            "Regular Essay",
            "Practice Objective",
            "Practice Essay",
            "Cancelled Published",
            "Closed Objective",
            "Soon Available",
            "Future Release",
            "Missing Release",
            "Progress Essay",
            "50%_done\\review <script>alert(1)</script>",
            "Ordinary Text",
            "Null Deadline"
        };

        for (int i = 0; i < titles.length; i++) {
            Instant available =
                    i == 3
                            ? now.plusSeconds(3600)
                            : i == 6 ? now.plusSeconds(1200) : now.minusSeconds(7200);
            Instant due =
                    i == 5
                            ? now.minusSeconds(3600)
                            : i == 3
                                    ? now.plusSeconds(14400)
                                    : i == 6
                                            ? now.plusSeconds(7200)
                                            : i == 12 ? null : now.plusSeconds(3600);

            assignment(
                    c,
                    100 + i,
                    10,
                    titles[i],
                    i == 2 || i == 3 ? "PRACTICE" : "REGULAR",
                    i == 1 || i == 3 || i == 9 ? "ESSAY" : "OBJECTIVE",
                    i == 4 ? "CANCELLED" : "PUBLISHED",
                    available,
                    due,
                    now.minusSeconds(86400));
        }

        assignment(c, 200, 10, "Hidden Draft", "REGULAR", "OBJECTIVE", "DRAFT", null, null, null);
        assignment(
                c, 201, 10, "Cancelled Draft", "REGULAR", "ESSAY", "CANCELLED", null, null, null);
        assignment(
                c,
                202,
                10,
                "Missing Publication",
                "REGULAR",
                "ESSAY",
                "PUBLISHED",
                now,
                now.plusSeconds(3600),
                null);
        assignment(
                c,
                203,
                10,
                "Unknown Purpose",
                "OTHER",
                "ESSAY",
                "PUBLISHED",
                now,
                now.plusSeconds(3600),
                now);
        assignment(
                c,
                204,
                10,
                "Unknown Type",
                "REGULAR",
                "OTHER",
                "PUBLISHED",
                now,
                now.plusSeconds(3600),
                now);
        assignment(
                c,
                300,
                20,
                "Foreign Work",
                "REGULAR",
                "OBJECTIVE",
                "PUBLISHED",
                now.minusSeconds(60),
                now.plusSeconds(3600),
                now);
        Jdbc.update(
                c,
                "INSERT INTO submissions VALUES (1,100,11,1,'SUBMITTED',?,NULL,NULL),"
                        + "(2,100,11,2,'IN_PROGRESS',NULL,NULL,NULL),(3,100,11,2,'VOID',NULL,NULL,NULL),"
                        + "(4,101,11,1,'GRADED',?,NULL,8),(5,102,11,1,'RELEASED',?,?,9),"
                        + "(6,104,11,1,'SUBMITTED',?,NULL,NULL),(7,105,11,1,'EXPIRED',NULL,NULL,NULL),"
                        + "(8,107,11,1,'RELEASED',?,?,7),(9,108,11,1,'RELEASED',?,NULL,6),"
                        + "(10,109,11,1,'IN_PROGRESS',NULL,NULL,NULL),"
                        + "(40,100,21,99,'RELEASED',?,?,99),(41,100,22,100,'RELEASED',?,?,98),"
                        + "(42,100,56,101,'RELEASED',?,?,97),(43,300,55,1,'RELEASED',?,?,96)",
                now.minusSeconds(60),
                now.minusSeconds(60),
                now.minusSeconds(60),
                now.minusSeconds(30),
                now.minusSeconds(60),
                now.minusSeconds(60),
                now.plusSeconds(86400),
                now.minusSeconds(60),
                now.minusSeconds(60),
                now.minusSeconds(30),
                now.minusSeconds(60),
                now.minusSeconds(30),
                now.minusSeconds(60),
                now.minusSeconds(30),
                now.minusSeconds(60),
                now.minusSeconds(30));
    }

    private static void assignment(
            Connection c,
            long id,
            long classId,
            String title,
            String purpose,
            String response,
            String status,
            Instant available,
            Instant due,
            Instant published)
            throws SQLException {
        Jdbc.update(
                c,
                "INSERT INTO assignments(assignment_id,class_id,title,assignment_purpose,response_type,maximum_score,status,available_from,due_at,published_at) VALUES (?,?,?,?,?,10,?,?,?,?)",
                id,
                classId,
                title,
                purpose,
                response,
                status,
                available,
                due,
                published);
    }

    private static String snapshot(Connection c) throws SQLException {
        var out = new StringBuilder();

        for (String table :
                List.of(
                        "users",
                        "subjects",
                        "academic_terms",
                        "subject_terms",
                        "subject_classes",
                        "subject_registrations",
                        "class_enrollments",
                        "assignments",
                        "submissions"))
            try (var s = c.createStatement();
                    var r = s.executeQuery("SELECT * FROM " + table + " ORDER BY 1")) {
                out.append(table);

                while (r.next())
                    for (int col = 1; col <= r.getMetaData().getColumnCount(); col++)
                        out.append('|').append(r.getString(col));
            }

        return out.toString();
    }
}
