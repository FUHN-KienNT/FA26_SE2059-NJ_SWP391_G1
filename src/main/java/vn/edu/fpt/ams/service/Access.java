package vn.edu.fpt.ams.service;

import java.sql.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.Jdbc;

public final class Access {
  private Access() {}

  public static UserRole active(Connection c, long actor) throws SQLException {
    try (var p =
            Jdbc.statement(
                c, "SELECT role,account_status FROM users WHERE user_id=? FOR UPDATE", actor);
        var r = p.executeQuery()) {
      if (!r.next() || !"ACTIVE".equals(r.getString(2)))
        throw new SecurityException("An active account is required");
      return UserRole.valueOf(r.getString(1));
    }
  }

  public static void admin(Connection c, long actor) throws SQLException {
    if (active(c, actor) != UserRole.ADMIN)
      throw new SecurityException("Administrator access is required");
  }

  public static ResponsibilityType expert(Connection c, long actor, long subject)
      throws SQLException {
    if (active(c, actor) != UserRole.EXPERT)
      throw new SecurityException("Expert access is required");
    try (var p =
            Jdbc.statement(
                c,
                "SELECT responsibility_type FROM subject_experts WHERE subject_id=? AND expert_id=?"
                    + " AND is_active=TRUE FOR UPDATE",
                subject,
                actor);
        var r = p.executeQuery()) {
      if (!r.next()) throw new SecurityException("No active Subject responsibility");
      return ResponsibilityType.valueOf(r.getString(1));
    }
  }
}
