package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import vn.edu.fpt.ams.dao.AssignmentDAO;
import vn.edu.fpt.ams.dto.AssignmentListFilter;
import vn.edu.fpt.ams.dto.AssignmentListPage;
import vn.edu.fpt.ams.util.DBConnection;

/** Opt in with -Dassignment.db.tests=true. All fixtures live in session-local TEMP tables. */
class AssignmentListDatabaseTest {
    private AssignmentService service;

    @BeforeEach void setup() {
        Assumptions.assumeTrue(Boolean.getBoolean("assignment.db.tests"), "Enable assignment.db.tests for PostgreSQL checks");
        service = new AssignmentService(new AssignmentDAO(), AssignmentListDatabaseTest::fixtureConnection);
    }

    private static Connection fixtureConnection() throws SQLException {
        Connection c = DBConnection.getConnection();
        try (var s = c.createStatement()) {
            s.execute("CREATE TEMP TABLE users (user_id BIGINT PRIMARY KEY, role TEXT, account_status TEXT)");
            s.execute("CREATE TEMP TABLE subject_classes (class_id BIGINT PRIMARY KEY, teacher_id BIGINT, class_code TEXT, class_name TEXT)");
            s.execute("CREATE TEMP TABLE assignments (LIKE public.assignments INCLUDING DEFAULTS)");
            s.execute("SET search_path TO pg_temp");
            s.execute("INSERT INTO users VALUES (1,'TEACHER','ACTIVE'), (2,'TEACHER','ACTIVE'), (3,'TEACHER','ACTIVE'), (4,'STUDENT','ACTIVE'), (5,'TEACHER','INACTIVE')");
            s.execute("INSERT INTO subject_classes VALUES (10,1,'SE2059','Project'), (11,1,'SE2060','Empty class'), (20,2,'PRIVATE','Other teacher'), (40,4,'STUDENT','Invalid role'), (50,5,'INACTIVE','Inactive owner')");
            s.execute("""
                INSERT INTO assignments (assignment_id,class_id,template_id,title,assignment_purpose,
                    response_type,instructions,maximum_score,status,due_at)
                VALUES (101,10,1,'Project Proposal','REGULAR','ESSAY','Instructions',10,'DRAFT',NULL),
                       (102,10,1,'UML Quiz','REGULAR','OBJECTIVE','Instructions',10,'PUBLISHED','2026-10-05 16:59:00+00'),
                       (103,10,1,'Practice Quiz','PRACTICE','OBJECTIVE','Instructions',10,'PUBLISHED','2026-10-06 16:59:00+00'),
                       (104,10,1,'100%_done <script>','REGULAR','ESSAY','Instructions',10,'CANCELLED',NULL),
                       (201,20,1,'Private assignment','REGULAR','ESSAY','Instructions',10,'DRAFT',NULL)
                """);
            return c;
        } catch (SQLException ex) { c.close(); throw ex; }
    }

    private AssignmentListPage page(Long classId, String search, String purpose, String type, String status) {
        return service.listPage(1, classId, AssignmentListFilter.from(search, purpose, type, status));
    }
    @Test void loadsExplicitOwnedClassAndOrdersNewestFirst() {
        var result = page(10L, null, null, null, null);
        assertEquals(10L, result.selectedClassId());
        assertEquals(List.of(10L, 11L), result.classes().stream().map(c -> c.classId()).toList());
        assertEquals(List.of(104L,103L,102L,101L), result.assignments().stream().map(a -> a.assignmentId()).toList());
        assertNull(result.assignments().get(3).dueAt());
    }
    @Test void previewChoosesActiveTeacherWithClassesAndHandlesMissingTeacher() throws Exception {
        assertEquals(1L, service.previewTeacherId());
        try (var c = fixtureConnection(); var s = c.createStatement()) {
            var dao = new AssignmentDAO();
            s.executeUpdate("UPDATE users SET account_status='INACTIVE' WHERE user_id=1");
            assertEquals(2L, dao.findPreviewTeacherId(c));
            s.executeUpdate("UPDATE users SET account_status='INACTIVE' WHERE user_id=2");
            assertEquals(3L, dao.findPreviewTeacherId(c));
            s.executeUpdate("UPDATE users SET account_status='INACTIVE' WHERE user_id=3");
            assertThrows(IllegalStateException.class, () -> dao.findPreviewTeacherId(c));
        }
    }
    @Test void combinesTitlePurposeResponseAndStatusFilters() {
        var result = page(10L,"  QUIZ  ","regular","objective","published");
        assertEquals(List.of(102L), result.assignments().stream().map(a -> a.assignmentId()).toList());
    }
    @Test void allFiltersResetWithinTheSelectedClass() {
        assertEquals(4, page(10L,"","ALL","ALL","ALL").assignments().size());
        assertEquals(11L, page(11L,"","","","").selectedClassId());
    }
    @Test void searchesLiteralPercentAndUnderscoreWithoutWildcardExpansion() {
        assertEquals(1, page(10L,"%_",null,null,null).assignments().size());
        assertTrue(page(10L,"' OR 1=1 --",null,null,null).assignments().isEmpty());
    }
    @Test void keepsEmptyOwnedClassDistinctFromForbiddenClass() {
        assertTrue(page(11L,null,null,null,null).assignments().isEmpty());
        assertThrows(SecurityException.class, () -> page(20L,null,null,null,null));
        assertThrows(SecurityException.class, () -> page(999L,null,null,null,null));
    }
    @Test void teacherWithoutClassesCannotAccessAnotherClass() {
        assertThrows(SecurityException.class, () -> service.listPage(3,10L,AssignmentListFilter.from(null,null,null,null)));
    }
    @Test void rejectsInactiveWrongRoleAndMissingAccountsDespiteSessionId() {
        for (long id : new long[]{4,5,999})
            assertThrows(SecurityException.class, () -> service.listPage(id,10L,AssignmentListFilter.from(null,null,null,null)));
    }
    @Test void rejectsInvalidInputBeforeOpeningConnection() {
        var withoutDb = new AssignmentService(new AssignmentDAO(), () -> { throw new AssertionError("Must not connect"); });
        assertThrows(IllegalArgumentException.class, () -> withoutDb.listPage(1,null,AssignmentListFilter.from(null,null,null,null)));
        assertThrows(IllegalArgumentException.class, () -> withoutDb.listPage(1,0L,AssignmentListFilter.from(null,null,null,null)));
        assertThrows(SecurityException.class, () -> withoutDb.listPage(0,null,AssignmentListFilter.from(null,null,null,null)));
    }
}
