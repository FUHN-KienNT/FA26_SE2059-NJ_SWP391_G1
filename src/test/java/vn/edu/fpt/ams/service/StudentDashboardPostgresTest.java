package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.util.DBConnection;

class StudentDashboardPostgresTest {
    @Test void postgresTempTablesVerifyOwnershipHistoryAndReadOnlyQueries() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("assignment.db.tests"));
        try (Connection connection = DBConnection.getConnection()) {
            StudentDashboardFixture.create(connection, true);
            String before = StudentDashboardFixture.snapshot(connection);
            Connection borrowed = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class}, (p, m, args) -> {
                        if (m.getName().equals("close")) return null;
                        try { return m.invoke(connection, args); }
                        catch (InvocationTargetException ex) { throw ex.getCause(); }
                    });
            var service = new StudentDashboardService(() -> borrowed);
            var view = service.load(1);
            assertEquals(1, view.classCount());
            assertEquals(4, view.assignmentCount());
            assertEquals(5, view.submittedAttemptCount());
            assertEquals(List.of(9, 7), view.results().stream().map(x -> x.score().intValueExact()).toList());
            assertEquals(1, service.load(2).assignmentCount());
            assertTrue(service.load(5).results().isEmpty());
            assertThrows(SecurityException.class, () -> service.load(3));
            assertEquals(before, StudentDashboardFixture.snapshot(connection));
        }
    }
}
