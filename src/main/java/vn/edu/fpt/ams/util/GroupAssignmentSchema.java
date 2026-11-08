package vn.edu.fpt.ams.util;

import java.sql.Connection;
import java.sql.SQLException;

/** Optional group-assignment boundary, including isolated individual-only test schemas. */
public final class GroupAssignmentSchema {
    private GroupAssignmentSchema() {}

    public static boolean installed(Connection connection) throws SQLException {
        var metadata = connection.getMetaData();
        if (metadata == null || !"PostgreSQL".equals(metadata.getDatabaseProductName())) return false;

        // Never mix session-local assignment fixtures with permanent application group data.
        try (var statement = connection.prepareStatement("""
                SELECT settings.relnamespace = assignments.relnamespace
                FROM pg_class settings, pg_class assignments
                WHERE settings.oid=to_regclass('assignment_group_settings')
                  AND assignments.oid=to_regclass('assignments')
                """); var rows = statement.executeQuery()) {
            return rows.next() && rows.getBoolean(1);
        }
    }

    public static boolean isGroup(Connection connection, long assignmentId) throws SQLException {
        if (!installed(connection)) return false;

        try (var statement = Jdbc.statement(connection, """
                SELECT 1 FROM assignment_group_settings
                WHERE assignment_id=? AND delivery_mode='GROUP'
                """, assignmentId); var rows = statement.executeQuery()) {
            return rows.next();
        }
    }

    public static Long targetForSubmission(Connection connection, long submissionId) throws SQLException {
        if (!installed(connection)) return null;

        try (var statement = Jdbc.statement(connection,
                "SELECT assignment_group_target_id FROM submissions WHERE submission_id=?",
                submissionId); var rows = statement.executeQuery()) {
            if (!rows.next()) return null;
            long id = rows.getLong(1);
            return rows.wasNull() ? null : id;
        }
    }
}
