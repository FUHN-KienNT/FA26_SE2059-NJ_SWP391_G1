package vn.edu.fpt.ams.dao;
import java.sql.*;
import java.util.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.util.Jdbc;
/** Data access only. Caller owns the connection and transaction. */
public final class AccessDAO {
  public static UserAccount findAccountForUpdate(Connection c, long actor) throws SQLException {
    try(var p=Jdbc.statement(c,"SELECT * FROM users WHERE user_id=? FOR UPDATE",actor);var r=p.executeQuery()) {
      return r.next()?UserDAO.mapUser(r):null;
    }
  }

  public static ResponsibilityType findResponsibilityForUpdate(Connection c, long subject, long actor) throws SQLException {
    try(var p=Jdbc.statement(c,"SELECT responsibility_type FROM subject_experts WHERE subject_id=? AND expert_id=? AND is_active=TRUE FOR UPDATE",subject,actor);var r=p.executeQuery()) {
      return r.next()?ResponsibilityType.valueOf(r.getString(1)):null;
    }
  }
}
