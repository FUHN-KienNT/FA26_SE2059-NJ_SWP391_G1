package vn.edu.fpt.ams.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import vn.edu.fpt.ams.model.*;

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
}
