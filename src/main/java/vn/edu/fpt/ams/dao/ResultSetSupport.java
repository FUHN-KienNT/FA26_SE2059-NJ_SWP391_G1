package vn.edu.fpt.ams.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

final class ResultSetSupport {
    private ResultSetSupport() {}

    static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
