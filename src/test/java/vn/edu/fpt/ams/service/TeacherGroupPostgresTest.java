package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import org.junit.jupiter.api.*;
import vn.edu.fpt.ams.util.DBConnection;
import vn.edu.fpt.ams.util.Jdbc;

/** Exercise actual PostgreSQL group constraints on disposable session-local tables. */
class TeacherGroupPostgresTest {
    private Connection connection;
    private TeacherGroupService service;

    /** Shadow application tables with isolated temporary fixtures. */
    @BeforeEach void setup() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("assignment.db.tests"));
        connection = DBConnection.getConnection();
        try (var s = connection.createStatement()) {
            s.execute("CREATE TEMP TABLE users(user_id BIGINT PRIMARY KEY, role VARCHAR(20), account_status VARCHAR(20), full_name VARCHAR(100), email VARCHAR(100))");
            s.execute("CREATE TEMP TABLE subject_classes(class_id BIGINT PRIMARY KEY, teacher_id BIGINT, status VARCHAR(20))");
            s.execute("CREATE TEMP TABLE subject_registrations(registration_id BIGINT PRIMARY KEY, student_id BIGINT, status VARCHAR(20))");
            s.execute("CREATE TEMP TABLE class_enrollments(enrollment_id BIGINT PRIMARY KEY, class_id BIGINT, registration_id BIGINT, status VARCHAR(20))");
            String migration = Files.readString(Path.of("database/migrations/007-teacher-project-groups.sql"))
                    .replace("CREATE TABLE ", "CREATE TEMP TABLE ");
            s.execute(migration);
            s.execute("""
                    INSERT INTO users VALUES
                    (1,'TEACHER','ACTIVE','Teacher','teacher@test'),
                    (2,'TEACHER','ACTIVE','Other','other@test'),
                    (10,'STUDENT','ACTIVE','One','one@test'),
                    (11,'STUDENT','ACTIVE','Two','two@test'),
                    (12,'STUDENT','ACTIVE','Foreign','foreign@test'),
                    (13,'STUDENT','INACTIVE','Inactive','inactive@test');
                    INSERT INTO subject_classes VALUES (100,1,'ACTIVE'),(200,2,'ACTIVE');
                    INSERT INTO subject_registrations VALUES (10,10,'ACTIVE'),(11,11,'ACTIVE'),(12,12,'ACTIVE'),(13,13,'ACTIVE');
                    INSERT INTO class_enrollments VALUES (10,100,10,'ACTIVE'),(11,100,11,'ACTIVE'),(12,200,12,'ACTIVE'),(13,100,13,'ACTIVE');
                    """);
        }
        Connection borrowed = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("close")) return null;
                    try { return method.invoke(connection, args); }
                    catch (InvocationTargetException ex) { throw ex.getCause(); }
                });
        service = new TeacherGroupService(() -> borrowed);
    }

    /** Dispose of the entire fixture without touching persistent application records. */
    @AfterEach void close() throws Exception {
        if (connection != null) connection.close();
    }

    /** Create and edit real names and descriptions while preserving group identity. */
    @Test void metadataAndDuplicateNames() {
        long id = service.save(1, 100, null, " Alpha ", "Project one");
        assertEquals("Alpha", service.detail(1, 100, id).name());
        assertThrows(IllegalArgumentException.class, () -> service.save(1, 100, null, "alpha", ""));
        service.save(1, 100, id, "Beta", "Updated");
        assertEquals("Updated", service.detail(1, 100, id).description());
        assertThrows(IllegalArgumentException.class, () -> service.save(1, 100, null, " ", ""));
    }

    /** Reject foreign classes and groups on both reads and writes. */
    @Test void classAndGroupOwnership() {
        long id = service.save(1, 100, null, "Alpha", "");
        assertThrows(SecurityException.class, () -> service.list(2, 100));
        assertThrows(SecurityException.class, () -> service.save(1, 200, null, "Foreign", ""));
        assertThrows(IllegalArgumentException.class, () -> service.addMember(1, 100, id + 99, 10));
        assertThrows(SecurityException.class, () -> service.detail(1, 200, id));
    }

    /** Membership requires a current eligible enrollment in the same class. */
    @Test void rejectsForeignAndInactiveStudents() {
        long id = service.save(1, 100, null, "Alpha", "");
        assertThrows(IllegalArgumentException.class, () -> service.addMember(1, 100, id, 12));
        assertThrows(IllegalArgumentException.class, () -> service.addMember(1, 100, id, 13));
        assertEquals(0, service.detail(1, 100, id).memberCount());
    }

    /** A Student can be moved only after closing the previous group's episode. */
    @Test void oneActiveGroupAndRetainedHistory() {
        long first = service.save(1, 100, null, "Alpha", "");
        long second = service.save(1, 100, null, "Beta", "");
        service.addMember(1, 100, first, 10);
        service.setLeader(1, 100, first, 10L);
        assertThrows(IllegalArgumentException.class, () -> service.addMember(1, 100, second, 10));
        service.removeMember(1, 100, first, 10);
        assertNull(service.detail(1, 100, first).leaderStudentId());
        assertEquals(1, service.detail(1, 100, first).members().size());
        service.addMember(1, 100, second, 10);
        assertEquals(1, service.detail(1, 100, second).memberCount());
    }

    /** Reject a nonmember leader and make same-status retries harmless. */
    @Test void leaderEligibilityAndIdempotentStatus() {
        long id = service.save(1, 100, null, "Alpha", "");
        assertThrows(IllegalArgumentException.class, () -> service.setLeader(1, 100, id, 11L));
        service.addMember(1, 100, id, 10);
        service.setLeader(1, 100, id, 10L);
        service.changeStatus(1, 100, id, "ACTIVE");
        assertEquals(10L, service.detail(1, 100, id).leaderStudentId());
        service.setLeader(1, 100, id, null);
        assertNull(service.detail(1, 100, id).leaderStudentId());
    }

    /** Retiring releases members, keeps history and reopens an empty group. */
    @Test void retireAndReactivateGroup() {
        long id = service.save(1, 100, null, "Alpha", "");
        service.addMember(1, 100, id, 10);
        service.setLeader(1, 100, id, 10L);
        service.changeStatus(1, 100, id, "INACTIVE");
        assertEquals(0, service.detail(1, 100, id).memberCount());
        assertThrows(IllegalArgumentException.class, () -> service.addMember(1, 100, id, 11));
        service.changeStatus(1, 100, id, "ACTIVE");
        assertEquals(1, service.detail(1, 100, id).members().size());
        assertNull(service.detail(1, 100, id).leaderStudentId());
    }

    /** Activity changes invalidate membership in both detail and leader checks. */
    @Test void enrollmentAndTeacherRevocation() throws Exception {
        long id = service.save(1, 100, null, "Alpha", "");
        service.addMember(1, 100, id, 10);
        service.setLeader(1, 100, id, 10L);
        Jdbc.update(connection, "UPDATE class_enrollments SET status='INACTIVE' WHERE enrollment_id=10");
        connection.commit();
        assertEquals(0, service.detail(1, 100, id).memberCount());
        assertNull(service.detail(1, 100, id).leaderStudentId());
        assertThrows(IllegalArgumentException.class, () -> service.setLeader(1, 100, id, 10L));
        Jdbc.update(connection, "UPDATE subject_classes SET teacher_id=2 WHERE class_id=100");
        connection.commit();
        assertThrows(SecurityException.class, () -> service.list(1, 100));
    }

    /** A partially written lifecycle operation rolls back all its effects. */
    @Test void failedLifecycleRollsBackMembershipClosure() throws Exception {
        long id = service.save(1, 100, null, "Alpha", "");
        service.addMember(1, 100, id, 10);
        try (var s = connection.createStatement()) {
            s.execute("ALTER TABLE project_groups ADD CONSTRAINT fixture_active CHECK (status='ACTIVE')");
        }
        connection.commit();
        assertThrows(IllegalStateException.class, () -> service.changeStatus(1, 100, id, "INACTIVE"));
        assertEquals(1, service.detail(1, 100, id).memberCount());
    }
}
