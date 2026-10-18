package vn.edu.fpt.ams.dao;
import java.sql.*;
import java.util.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.util.Jdbc;
/** Data access only. Caller owns the connection and transaction. */
public final class StudentVerificationDAO {
  public static String findPendingEmailForUpdate(Connection c, long id) throws SQLException {
    try(var p=Jdbc.statement(c,"SELECT email FROM users WHERE user_id=? AND role='STUDENT' AND email_verified=FALSE AND account_status='ACTIVE' FOR UPDATE",id);var r=p.executeQuery()) {
      return r.next()?r.getString(1):null;
    }
  }
  public static long countPendingStudent(Connection c, long userId) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM users WHERE user_id=? AND role='STUDENT' AND email_verified=FALSE", userId);
  }

  public static long countRecentMessages(Connection c, long userId, java.time.Instant since) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM student_email_verifications WHERE user_id=? AND sent_at>?", userId, since);
  }

  public static int deleteTokens(Connection c, long userId) throws SQLException {
    return Jdbc.update(c, "DELETE FROM student_email_verifications WHERE user_id=?", userId);
  }

  public static int insertToken(Connection c, long userId, String tokenHash, java.time.Instant expiresAt) throws SQLException {
    return Jdbc.update(c, "INSERT INTO student_email_verifications(user_id,token_hash,expires_at) VALUES(?,?,?)", userId, tokenHash, expiresAt);
  }

  public static int deleteMatchingToken(Connection c, long userId, String tokenHash) throws SQLException {
    return Jdbc.update(c, "DELETE FROM student_email_verifications WHERE user_id=? AND token_hash=?", userId, tokenHash);
  }

  public static long findTokenUserId(Connection c, String tokenHash) throws SQLException {
    return Jdbc.scalar(c, "SELECT user_id FROM student_email_verifications WHERE token_hash=?", tokenHash);
  }

  public static long lockPendingStudent(Connection c, long userId) throws SQLException {
    return Jdbc.scalar(c, "SELECT user_id FROM users WHERE user_id=? AND role='STUDENT' AND account_status='ACTIVE' AND email_verified=FALSE FOR UPDATE", userId);
  }

  public static int deleteValidToken(Connection c, long userId, String tokenHash) throws SQLException {
    return Jdbc.update(c, "DELETE FROM student_email_verifications WHERE user_id=? AND token_hash=? AND expires_at>CURRENT_TIMESTAMP", userId, tokenHash);
  }

  public static int markEmailVerified(Connection c, long userId) throws SQLException {
    return Jdbc.update(c, "UPDATE users SET email_verified=TRUE,updated_at=CURRENT_TIMESTAMP WHERE user_id=?", userId);
  }
}
