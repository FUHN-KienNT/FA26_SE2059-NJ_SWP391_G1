package vn.edu.fpt.ams.util;

import java.sql.*;

/** Services own transactions; every participating DAO receives the same connection. */
public final class Jdbc {
  private Jdbc() {}

  @FunctionalInterface
  public interface Connections {
    Connection open() throws SQLException;
  }

  @FunctionalInterface
  public interface Work<T> {
    T run(Connection c) throws SQLException;
  }

  public static <T> T transaction(Connections source, Work<T> work) {
    try (Connection c = source.open()) {
      c.setAutoCommit(false);
      try {
        T value = work.run(c);
        c.commit();
        return value;
      } catch (SQLException | RuntimeException e) {
        c.rollback();
        throw e;
      }
    } catch (SQLException e) {
      if ("23505".equals(e.getSQLState()))
        throw new IllegalArgumentException("This identity already exists", e);
      throw new IllegalStateException("Database operation failed", e);
    }
  }

  public static PreparedStatement statement(Connection c, String sql, Object... args)
      throws SQLException {
    var p = c.prepareStatement(sql);
    try {
      for (int i = 0; i < args.length; i++) bind(p, i + 1, args[i]);
      return p;
    } catch (SQLException e) {
      p.close();
      throw e;
    }
  }

  public static int update(Connection c, String sql, Object... args) throws SQLException {
    try (var p = statement(c, sql, args)) {
      return p.executeUpdate();
    }
  }

  public static long scalar(Connection c, String sql, Object... args) throws SQLException {
    try (var p = statement(c, sql, args);
        var r = p.executeQuery()) {
      return r.next() ? r.getLong(1) : 0;
    }
  }

  public static long insert(Connection c, String sql, Object... args) throws SQLException {
    try (var p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      for (int i = 0; i < args.length; i++) bind(p, i + 1, args[i]);
      p.executeUpdate();
      try (var r = p.getGeneratedKeys()) {
        if (!r.next()) throw new SQLException("Missing generated key");
        return r.getLong(1);
      }
    }
  }

  private static void bind(PreparedStatement p, int index, Object value) throws SQLException {
    p.setObject(
        index,
        value instanceof java.time.Instant instant
            ? instant.atOffset(java.time.ZoneOffset.UTC)
            : value);
  }
}
