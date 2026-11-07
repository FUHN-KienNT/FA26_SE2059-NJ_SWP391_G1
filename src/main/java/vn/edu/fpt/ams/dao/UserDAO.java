package vn.edu.fpt.ams.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.Jdbc;

/** JDBC operations; callers use UserService for authorization and business rules. */
public class UserDAO {
  public java.util.Optional<UserAccount> findById(long id) {
    return vn.edu.fpt.ams.util.Jdbc.transaction(
        vn.edu.fpt.ams.util.DBConnection::getConnection, c -> findById(c, id));
  }

  public java.util.Optional<UserAccount> findById(java.sql.Connection c, long id)
      throws SQLException {
    try (var p = vn.edu.fpt.ams.util.Jdbc.statement(c, "SELECT * FROM users WHERE user_id=?", id);
        var r = p.executeQuery()) {
      return r.next() ? java.util.Optional.of(mapUser(r)) : java.util.Optional.empty();
    }
  }

  public java.util.Optional<UserAccount> findByEmail(String email) {
    return vn.edu.fpt.ams.util.Jdbc.transaction(
        vn.edu.fpt.ams.util.DBConnection::getConnection,
        c -> {
          try (var p =
                  vn.edu.fpt.ams.util.Jdbc.statement(
                      c,
                      "SELECT * FROM users WHERE lower(trim(email))=?",
                      email.strip().toLowerCase(java.util.Locale.ROOT));
              var r = p.executeQuery()) {
            return r.next() ? java.util.Optional.of(mapUser(r)) : java.util.Optional.empty();
          }
        });
  }

  public java.util.List<UserAccount> search(
      String keyword, UserRole role, AccountStatus status, int page) {
    return vn.edu.fpt.ams.util.Jdbc.transaction(
        vn.edu.fpt.ams.util.DBConnection::getConnection,
        c -> search(c, keyword, role, status, page));
  }

  public java.util.List<UserAccount> search(
      java.sql.Connection c, String keyword, UserRole role, AccountStatus status, int page)
      throws SQLException {
    String sql = "SELECT * FROM users WHERE (lower(full_name) LIKE ? OR lower(email) LIKE ?)";
    var args = new java.util.ArrayList<Object>();
    String term = "%" + (keyword == null ? "" : keyword.toLowerCase(java.util.Locale.ROOT)) + "%";
    args.add(term);
    args.add(term);
    if (role != null) {
      sql += " AND role=?";
      args.add(role.name());
    }
    if (status != null) {
      sql += " AND account_status=?";
      args.add(status.name());
    }
    sql += " ORDER BY user_id LIMIT 20 OFFSET ?";
    args.add(Math.multiplyExact(Math.max(1, page) - 1, 20));
    try (var p = vn.edu.fpt.ams.util.Jdbc.statement(c, sql, args.toArray());
        var r = p.executeQuery()) {
      var out = new java.util.ArrayList<UserAccount>();
      while (r.next()) out.add(mapUser(r));
      return out;
    }
  }

  public long insert(UserAccount u) {
    return vn.edu.fpt.ams.util.Jdbc.transaction(
        vn.edu.fpt.ams.util.DBConnection::getConnection, c -> insert(c, u));
  }

  public long insert(java.sql.Connection c, UserAccount u) throws SQLException {
    return vn.edu.fpt.ams.util.Jdbc.insert(
        c,
        "INSERT INTO users(full_name,email,role,account_status,google_subject,password_hash)"
            + " VALUES(?,?,?,?,?,?)",
        u.fullName(),
        u.email(),
        u.role().name(),
        u.accountStatus().name(),
        u.googleSubject(),
        u.passwordHash());
  }

  public void update(UserAccount u) {
    vn.edu.fpt.ams.util.Jdbc.transaction(
        vn.edu.fpt.ams.util.DBConnection::getConnection,
        c -> {
          vn.edu.fpt.ams.util.Jdbc.update(
              c,
              "UPDATE users SET full_name=?,email=?,role=?,updated_at=CURRENT_TIMESTAMP WHERE"
                  + " user_id=?",
              u.fullName(),
              u.email(),
              u.role().name(),
              u.userId());
          return null;
        });
  }

  public void updateStatus(long id, AccountStatus status) {
    vn.edu.fpt.ams.util.Jdbc.transaction(
        vn.edu.fpt.ams.util.DBConnection::getConnection,
        c -> {
          vn.edu.fpt.ams.util.Jdbc.update(
              c,
              "UPDATE users SET account_status=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?",
              status.name(),
              id);
          return null;
        });
  }

  static UserAccount mapUser(ResultSet rs) throws SQLException {
    return new UserAccount(
        rs.getLong("user_id"),
        rs.getString("full_name"),
        rs.getString("email"),
        UserRole.valueOf(rs.getString("role")),
        AccountStatus.valueOf(rs.getString("account_status")),
        rs.getString("google_subject"),
        rs.getString("password_hash"),
        ResultSetSupport.instant(rs, "created_at"),
        ResultSetSupport.instant(rs, "last_login_at"),
        ResultSetSupport.instant(rs, "updated_at"));
  }

  // The caller owns the connection and transaction for locked account/reset operations.

  public UserAccount lockByEmail(Connection connection, String email) throws SQLException {
    try (var statement =
            Jdbc.statement(
                connection, "SELECT * FROM users WHERE lower(trim(email))=? FOR UPDATE", email);
        var resultSet = statement.executeQuery()) {
      return resultSet.next() ? UserDAO.mapUser(resultSet) : null;
    }
  }

  public UserAccount lockById(Connection connection, long id) throws SQLException {
    try (var statement =
            Jdbc.statement(connection, "SELECT * FROM users WHERE user_id=? FOR UPDATE", id);
        var resultSet = statement.executeQuery()) {
      return resultSet.next() ? UserDAO.mapUser(resultSet) : null;
    }
  }

  public boolean hasRecentPasswordReset(Connection connection, long id, Instant since)
      throws SQLException {
    return Jdbc.scalar(
            connection,
            "SELECT COUNT(*) FROM staff_password_resets WHERE user_id=? AND sent_at>?",
            id,
            since)
        > 0;
  }

  public void replacePasswordReset(
      Connection connection, long id, String hash, String credentialHash, Instant expires)
      throws SQLException {
    deletePasswordReset(connection, id);
    Jdbc.update(
        connection,
        "INSERT INTO staff_password_resets(user_id,token_hash,credential_hash,expires_at)"
            + " VALUES(?,?,?,?)",
        id,
        hash,
        credentialHash,
        expires);
  }

  public long findPasswordResetUserId(Connection connection, String hash) throws SQLException {
    return Jdbc.scalar(
        connection, "SELECT user_id FROM staff_password_resets WHERE token_hash=?", hash);
  }

  public boolean consumePasswordReset(
      Connection connection, long id, String tokenHash, String credentialHash) throws SQLException {
    return Jdbc.update(
            connection,
            "DELETE FROM staff_password_resets WHERE user_id=? AND token_hash=? AND"
                + " credential_hash=? AND expires_at>CURRENT_TIMESTAMP",
            id,
            tokenHash,
            credentialHash)
        == 1;
  }

  public void deletePasswordReset(Connection connection, long id) throws SQLException {
    Jdbc.update(connection, "DELETE FROM staff_password_resets WHERE user_id=?", id);
  }

  public void deleteMatchingPasswordReset(Connection connection, long id, String hash)
      throws SQLException {
    Jdbc.update(
        connection, "DELETE FROM staff_password_resets WHERE user_id=? AND token_hash=?", id, hash);
  }

  public void updatePassword(Connection connection, long id, String hash) throws SQLException {
    Jdbc.update(
        connection,
        "UPDATE users SET password_hash=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?",
        hash,
        id);
  }

  public void updateFullName(Connection connection, long id, String name) throws SQLException {
    Jdbc.update(
        connection,
        "UPDATE users SET full_name=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?",
        name,
        id);
  }
}
