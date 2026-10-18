package vn.edu.fpt.ams.service;
import vn.edu.fpt.ams.dao.*;

import java.sql.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.Jdbc;

public final class Access {
  private Access() {}

  public static UserRole active(Connection c, long actor) throws SQLException {
    var account = AccessDAO.findAccountForUpdate(c, actor);
    if (account == null || account.accountStatus() != AccountStatus.ACTIVE)
      throw new SecurityException("An active account is required");
    return account.role();
  }

  public static void admin(Connection c, long actor) throws SQLException {
    if (active(c, actor) != UserRole.ADMIN)
      throw new SecurityException("Administrator access is required");
  }

  public static ResponsibilityType expert(Connection c, long actor, long subject)
      throws SQLException {
    if (active(c, actor) != UserRole.EXPERT)
      throw new SecurityException("Expert access is required");
    var responsibility = AccessDAO.findResponsibilityForUpdate(c, subject, actor);
    if (responsibility == null) throw new SecurityException("No active Subject responsibility");
    return responsibility;
  }
}
