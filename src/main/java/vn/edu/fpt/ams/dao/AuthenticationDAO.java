package vn.edu.fpt.ams.dao;
import java.sql.*;
import java.util.*;
import vn.edu.fpt.ams.util.Jdbc;
/** Data access only; never opens or commits the caller's transaction. */
public final class AuthenticationDAO {
  public static long lockUserIdByEmail(Connection c, String email) throws SQLException {
    return Jdbc.scalar(c, "SELECT user_id FROM users WHERE lower(trim(email))=? FOR UPDATE", email);
  }

  public static int updateLastLogin(Connection c, long userId) throws SQLException {
    return Jdbc.update(c, "UPDATE users SET last_login_at=CURRENT_TIMESTAMP WHERE user_id=?", userId);
  }
}
