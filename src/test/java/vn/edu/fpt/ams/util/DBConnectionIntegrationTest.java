package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class DBConnectionIntegrationTest {
    @Test
    void opensQueriesSeededDatabaseAndCloses() throws Exception {
        Assumptions.assumeTrue(hasDatabaseEnvironment(),
                "DB integration test requires DB_HOST, DB_PORT, DB_NAME, DB_USER and DB_PASSWORD");

        Connection connection = DBConnection.getConnection();
        try (connection; Statement statement = connection.createStatement()) {
            assertFalse(connection.isClosed());
            try (ResultSet result = statement.executeQuery("SELECT 1")) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
            try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM users")) {
                assertTrue(result.next());
                assertTrue(result.getLong(1) > 0, "seeded users table should contain rows");
            }
        }
        assertTrue(connection.isClosed());
    }

    private static boolean hasDatabaseEnvironment() {
        return present("DB_HOST") && present("DB_PORT") && present("DB_NAME")
                && present("DB_USER") && present("DB_PASSWORD");
    }

    private static boolean present(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }
}
