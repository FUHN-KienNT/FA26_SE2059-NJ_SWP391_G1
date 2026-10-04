package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dao.AssignmentDAO;
import vn.edu.fpt.ams.model.AssignmentPurpose;
import vn.edu.fpt.ams.model.AssignmentStatus;
import vn.edu.fpt.ams.model.ResponseType;
import vn.edu.fpt.ams.util.DBConnection;

/** Opt in with -Dassignment.db.tests=true. Fixtures only write session-local TEMP tables. */
class AssignmentDetailDatabaseTest {
    private AssignmentService service;

    @BeforeEach void setup() {
        Assumptions.assumeTrue(Boolean.getBoolean("assignment.db.tests"),
                "Enable assignment.db.tests for PostgreSQL checks");
        service = new AssignmentService(new AssignmentDAO(), AssignmentDetailDatabaseTest::fixtureConnection);
    }

    private static Connection fixtureConnection() throws SQLException {
        Connection connection = DBConnection.getConnection();
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE users (user_id BIGINT PRIMARY KEY, role TEXT, account_status TEXT)");
            statement.execute("CREATE TEMP TABLE subjects (subject_id BIGINT PRIMARY KEY, subject_code TEXT, subject_name TEXT, status TEXT)");
            statement.execute("CREATE TEMP TABLE academic_terms (term_id BIGINT PRIMARY KEY, term_code TEXT, term_name TEXT, is_active BOOLEAN)");
            statement.execute("CREATE TEMP TABLE subject_terms (subject_term_id BIGINT PRIMARY KEY, subject_id BIGINT, term_id BIGINT)");
            statement.execute("CREATE TEMP TABLE subject_classes (class_id BIGINT PRIMARY KEY, subject_term_id BIGINT, teacher_id BIGINT, class_code TEXT, class_name TEXT, is_active BOOLEAN)");
            statement.execute("CREATE TEMP TABLE assignment_templates (template_id BIGINT PRIMARY KEY, subject_id BIGINT, title TEXT, status TEXT)");
            statement.execute("CREATE TEMP TABLE assignments (LIKE public.assignments INCLUDING DEFAULTS)");
            statement.execute("CREATE TEMP TABLE questions (LIKE public.questions INCLUDING DEFAULTS)");
            statement.execute("SET search_path TO pg_temp");
            statement.execute("""
                    INSERT INTO users VALUES (1,'TEACHER','ACTIVE'), (2,'TEACHER','ACTIVE'),
                        (3,'TEACHER','ACTIVE'), (4,'STUDENT','ACTIVE'), (5,'TEACHER','INACTIVE'),
                        (6,'EXPERT','ACTIVE')
                    """);
            statement.execute("""
                    INSERT INTO subjects VALUES (200,'SWP391','Software Development Project','ACTIVE'),
                        (201,'PRJ301','Java Web Application Development','ACTIVE')
                    """);
            statement.execute("INSERT INTO academic_terms VALUES (300,'FALL2026','Fall 2026',TRUE)");
            statement.execute("INSERT INTO subject_terms VALUES (100,200,300), (200,201,300)");
            statement.execute("""
                    INSERT INTO subject_classes VALUES (10,100,1,'SE2059','Project class',TRUE),
                        (20,200,2,'PRIVATE','Other Teacher class',TRUE),
                        (40,100,4,'STUDENT','Invalid role class',TRUE),
                        (50,100,5,'INACTIVE','Inactive Teacher class',TRUE)
                    """);
            statement.execute("""
                    INSERT INTO assignment_templates VALUES (1,200,'UML Quiz template','ACTIVE'),
                        (2,200,'Project report template','ACTIVE'),
                        (3,201,'Private assignment template','ACTIVE')
                    """);
            statement.execute("""
                    INSERT INTO assignments (assignment_id,class_id,template_id,title,
                        assignment_purpose,response_type,instructions,scope_text,task_checklist,
                        maximum_score,status,available_from,due_at,time_limit_minutes,attempt_limit,
                        published_at,cancelled_at)
                    VALUES (101,10,1,'UML Quiz <review>','PRACTICE','OBJECTIVE',
                        'Read carefully & explain your choice.','Chương 1 — UML',
                        '["Review UML", "Answer every question"]'::jsonb,
                        10.50,'PUBLISHED','2026-10-04 00:00:00+00','2026-10-05 16:59:00+00',
                        20,3,'2026-10-03 09:00:00+00',NULL),
                        (102,10,2,'Project report','REGULAR','ESSAY','Upload your project report.',
                        NULL,'[]'::jsonb,10,'DRAFT',NULL,NULL,NULL,NULL,NULL,NULL),
                        (201,20,3,'Private assignment','REGULAR','OBJECTIVE','Private instructions',
                        NULL,'[]'::jsonb,10,'DRAFT',NULL,NULL,NULL,NULL,NULL,NULL),
                        (401,40,1,'Student-owned fixture','REGULAR','ESSAY','Instructions',
                        NULL,'[]'::jsonb,10,'DRAFT',NULL,NULL,NULL,NULL,NULL,NULL),
                        (501,50,1,'Inactive Teacher fixture','REGULAR','ESSAY','Instructions',
                        NULL,'[]'::jsonb,10,'DRAFT',NULL,NULL,NULL,NULL,NULL,NULL)
                    """);
            statement.execute("""
                    INSERT INTO questions (question_id,assignment_id,question_order,question_text,
                        maximum_points,options)
                    VALUES (1001,101,3,'Last question',4.50,'["C", "D"]'::jsonb),
                        (1003,101,1,'First question',2,'["A", "B"]'::jsonb),
                        (1002,101,2,'Second question',4,'["True", "False"]'::jsonb),
                        (2001,201,1,'Foreign question',10,'[]'::jsonb),
                        (9001,102,1,'Stray essay question',1,'[]'::jsonb)
                    """);
            return connection;
        } catch (SQLException ex) {
            connection.close();
            throw ex;
        }
    }

    @Test void returnsOwnedAssignmentContentAndJoinedClassContext() {
        var page = service.detailPage(1, 101);
        var assignment = page.assignment();
        assertAll(
                () -> assertEquals(101L, assignment.assignmentId()),
                () -> assertEquals(10L, assignment.classId()),
                () -> assertEquals(1L, assignment.templateId()),
                () -> assertEquals("UML Quiz <review>", assignment.title()),
                () -> assertEquals(AssignmentPurpose.PRACTICE, assignment.assignmentPurpose()),
                () -> assertEquals(ResponseType.OBJECTIVE, assignment.responseType()),
                () -> assertEquals("Read carefully & explain your choice.", assignment.instructions()),
                () -> assertEquals("Chương 1 — UML", assignment.scopeText()),
                () -> assertEquals("[\"Review UML\", \"Answer every question\"]", assignment.taskChecklistJson()),
                () -> assertEquals(new BigDecimal("10.50"), assignment.maximumScore()),
                () -> assertEquals(AssignmentStatus.PUBLISHED, assignment.status()),
                () -> assertEquals(Instant.parse("2026-10-04T00:00:00Z"), assignment.availableFrom()),
                () -> assertEquals(Instant.parse("2026-10-05T16:59:00Z"), assignment.dueAt()),
                () -> assertEquals(Integer.valueOf(20), assignment.timeLimitMinutes()),
                () -> assertEquals(Integer.valueOf(3), assignment.attemptLimit()),
                () -> assertEquals(Instant.parse("2026-10-03T09:00:00Z"), assignment.publishedAt()),
                () -> assertNull(assignment.cancelledAt()),
                () -> assertEquals("SE2059", page.classCode()),
                () -> assertEquals("Project class", page.className()),
                () -> assertEquals("SWP391", page.subjectCode()),
                () -> assertEquals("Software Development Project", page.subjectName()),
                () -> assertEquals("FALL2026", page.termCode()),
                () -> assertEquals("UML Quiz template", page.templateTitle()));
    }

    @Test void objectiveQuestionsAreOrderedAndLimitedToTheSelectedAssignment() {
        var questions = service.detailPage(1, 101).questions();
        assertEquals(List.of(1003L, 1002L, 1001L),
                questions.stream().map(question -> question.questionId()).toList());
        assertEquals(List.of(1, 2, 3),
                questions.stream().map(question -> question.questionOrder()).toList());
        var first = questions.get(0);
        assertAll(
                () -> assertTrue(questions.stream().allMatch(question -> question.assignmentId() == 101)),
                () -> assertEquals("First question", first.questionText()),
                () -> assertEquals(new BigDecimal("2.00"), first.maximumPoints()),
                () -> assertEquals("[\"A\", \"B\"]", first.options()));
    }

    @Test void essayHasNoQuestionsEvenWhenStrayQuestionRowsExist() {
        var page = service.detailPage(1, 102);
        assertAll(
                () -> assertEquals(ResponseType.ESSAY, page.assignment().responseType()),
                () -> assertEquals("Upload your project report.", page.assignment().instructions()),
                () -> assertEquals("Project report template", page.templateTitle()),
                () -> assertTrue(page.questions().isEmpty()),
                () -> assertNull(page.assignment().scopeText()),
                () -> assertNull(page.assignment().dueAt()),
                () -> assertNull(page.assignment().timeLimitMinutes()),
                () -> assertNull(page.assignment().attemptLimit()));
    }

    @Test void foreignAndMissingAssignmentsBothReturnNotFound() {
        for (long assignmentId : new long[]{201, 999}) {
            assertThrows(NoSuchElementException.class, () -> service.detailPage(1, assignmentId));
        }
        assertThrows(NoSuchElementException.class, () -> service.detailPage(3, 101));
    }

    @Test void rejectsInactiveWrongRoleAndMissingTeachersDespiteAnAssignmentId() {
        assertThrows(SecurityException.class, () -> service.detailPage(4, 401));
        assertThrows(SecurityException.class, () -> service.detailPage(5, 501));
        assertThrows(SecurityException.class, () -> service.detailPage(6, 101));
        assertThrows(SecurityException.class, () -> service.detailPage(999, 101));
    }

    @Test void rejectsInvalidIdsBeforeOpeningConnection() {
        var withoutDb = new AssignmentService(new AssignmentDAO(), () -> {
            throw new AssertionError("Invalid IDs must not open a database connection");
        });
        for (long invalidId : new long[]{0, -1, Long.MIN_VALUE}) {
            assertThrows(SecurityException.class, () -> withoutDb.detailPage(invalidId, 101));
            assertThrows(IllegalArgumentException.class, () -> withoutDb.detailPage(1, invalidId));
        }
    }
}
