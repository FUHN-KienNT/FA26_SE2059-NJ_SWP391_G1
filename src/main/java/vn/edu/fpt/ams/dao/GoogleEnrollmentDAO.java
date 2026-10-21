package vn.edu.fpt.ams.dao;
import java.sql.*;
import java.util.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.util.Jdbc;
/** Data access only. Caller owns the connection and transaction. */
public final class GoogleEnrollmentDAO {
  public record ClassPlacement(long term,int capacity,long teacher,String status,boolean registrationEnabled) {}
  public record Invitation(long id,String email,String status,long acceptedBy,java.time.Instant expiresAt) {}
  public static ClassPlacement findClassForUpdate(Connection c, long id) throws SQLException {
    try(var p=Jdbc.statement(c,"SELECT subject_term_id,capacity,teacher_id,status,registration_enabled FROM subject_classes WHERE class_id=? FOR UPDATE",id);var r=p.executeQuery()) {
      return r.next()?new ClassPlacement(r.getLong(1),r.getInt(2),r.getLong(3),r.getString(4),r.getBoolean(5)):null;
    }
  }

  public static Invitation findInvitationForUpdate(Connection c, String token) throws SQLException {
    try(var p=Jdbc.statement(c,"SELECT invitation_id,email,status,accepted_by,expires_at FROM student_invitations WHERE token=? FOR UPDATE",token);var r=p.executeQuery()) {
      return r.next()?new Invitation(r.getLong(1),r.getString(2),r.getString(3),r.getLong(4),r.getTimestamp(5).toInstant()):null;
    }
  }
  public static long findUserIdByGoogleSubject(Connection c, String googleSubject) throws SQLException {
    return Jdbc.scalar(c, "SELECT user_id FROM users WHERE google_subject=?", googleSubject);
  }

  public static long findUserIdByEmail(Connection c, String email) throws SQLException {
    return Jdbc.scalar(c, "SELECT user_id FROM users WHERE lower(trim(email))=?", email);
  }

  public static int markEmailPending(Connection c, long userId) throws SQLException {
    return Jdbc.update(c, "UPDATE users SET email_verified=FALSE WHERE user_id=?", userId);
  }

  public static long countPendingVerification(Connection c, long userId) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM users WHERE user_id=? AND email_verified=FALSE", userId);
  }

  public static int updateLastLogin(Connection c, long userId) throws SQLException {
    return Jdbc.update(c, "UPDATE users SET last_login_at=CURRENT_TIMESTAMP WHERE user_id=?", userId);
  }

  public static long findInvitedClassId(Connection c, String token) throws SQLException {
    return Jdbc.scalar(c, "SELECT class_id FROM student_invitations WHERE token=?", token);
  }

  public static long countEligibleOfferingTeachers(Connection c, long termId, long teacherId) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM subject_terms st JOIN subjects s ON s.subject_id=st.subject_id"
                + " JOIN academic_terms a ON a.term_id=st.term_id JOIN teacher_subjects t ON"
                + " t.subject_id=s.subject_id JOIN users u ON u.user_id=t.teacher_id WHERE"
                + " st.subject_term_id=? AND s.status='ACTIVE' AND a.is_active=TRUE AND a.ends_at>CURRENT_TIMESTAMP AND"
                + " t.teacher_id=? AND t.is_active=TRUE AND u.role='TEACHER' AND"
                + " u.account_status='ACTIVE'", termId, teacherId);
  }

  public static long countActiveEnrollments(Connection c, long classId) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM class_enrollments WHERE class_id=? AND status='ACTIVE'", classId);
  }

  public static long countPendingInvitations(Connection c, long classId) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM student_invitations WHERE class_id=? AND status='PENDING' AND"
                    + " expires_at>CURRENT_TIMESTAMP", classId);
  }

  public static long countConflictingInvitations(Connection c, long termId, String email, long invitationId) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM student_invitations i JOIN subject_classes sc ON"
                + " sc.class_id=i.class_id WHERE sc.subject_term_id=? AND lower(trim(i.email))=?"
                + " AND i.status='PENDING' AND i.expires_at>CURRENT_TIMESTAMP AND"
                + " i.invitation_id<>?", termId, email, invitationId);
  }

  public static long findRegistrationId(Connection c, long studentId, long termId) throws SQLException {
    return Jdbc.scalar(c, "SELECT registration_id FROM subject_registrations WHERE student_id=? AND"
                + " subject_term_id=?", studentId, termId);
  }

  public static long insertRegistration(Connection c, long studentId, long termId) throws SQLException {
    return Jdbc.insert(c, "INSERT INTO subject_registrations(student_id,subject_term_id) VALUES(?,?)", studentId, termId);
  }

  public static int reactivateRegistration(Connection c, long registrationId) throws SQLException {
    return Jdbc.update(c, "UPDATE subject_registrations SET status='ACTIVE',deactivated_at=NULL WHERE"
              + " registration_id=?", registrationId);
  }

  public static long findActiveEnrollmentId(Connection c, long registrationId) throws SQLException {
    return Jdbc.scalar(c, "SELECT enrollment_id FROM class_enrollments WHERE registration_id=? AND"
                + " status='ACTIVE'", registrationId);
  }

  public static long insertInvitationEnrollment(Connection c, long registrationId, long classId) throws SQLException {
    return Jdbc.insert(c, "INSERT INTO class_enrollments(registration_id,class_id,enrollment_method)"
                + " VALUES(?,?,'INVITATION')", registrationId, classId);
  }

  public static int acceptInvitation(Connection c, long studentId, long enrollmentId, long invitationId) throws SQLException {
    return Jdbc.update(c, "UPDATE student_invitations SET"
            + " status='ACCEPTED',accepted_at=CURRENT_TIMESTAMP,accepted_by=?,enrollment_id=? WHERE"
            + " invitation_id=?", studentId, enrollmentId, invitationId);
  }
}
