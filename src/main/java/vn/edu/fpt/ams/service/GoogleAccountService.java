package vn.edu.fpt.ams.service;

import java.sql.*;
import java.time.Instant;
import vn.edu.fpt.ams.dao.UserDAO;
import vn.edu.fpt.ams.integration.GoogleIdentityClient.Identity;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.*;

/** Verified identity provisioning and UC17 invitation handoff share one commit. */
public class GoogleAccountService {
  private final Jdbc.Connections connections;
  private final UserDAO users = new UserDAO();

  public GoogleAccountService() {
    this(DBConnection::getConnection);
  }

  public GoogleAccountService(Jdbc.Connections source) {
    connections = source;
  }

  public UserAccount signIn(Identity identity, String invitation) {
    if (identity == null || identity.subject() == null || identity.subject().isBlank())
      throw new SecurityException("Verified Google subject required");
    String email = AccountValidation.email(identity.email());
    return Jdbc.transaction(
        connections,
        c -> {
          try (var p = Jdbc.statement(c, "SELECT user_id FROM users ORDER BY user_id FOR UPDATE");
              var r = p.executeQuery()) {
            while (r.next()) {}
          }
          long bySubject =
              Jdbc.scalar(
                  c, "SELECT user_id FROM users WHERE google_subject=?", identity.subject());
          long byEmail =
              Jdbc.scalar(c, "SELECT user_id FROM users WHERE lower(trim(email))=?", email);
          if (byEmail != 0 && byEmail != bySubject)
            throw new SecurityException("Google identity conflicts with an existing account");
          long id = bySubject;
          if (id == 0) {
            String name = AccountValidation.name(identity.name());
            id =
                users.insert(
                    c,
                    new UserAccount(
                        0,
                        name,
                        email,
                        UserRole.STUDENT,
                        AccountStatus.ACTIVE,
                        identity.subject(),
                        null,
                        null,
                        null,
                        null));
          }
          UserAccount account = users.findById(c, id).orElseThrow();
          if (account.role() != UserRole.STUDENT || account.accountStatus() != AccountStatus.ACTIVE)
            throw new SecurityException("Active Student account required");
          if (invitation != null && !invitation.isBlank()) accept(c, id, email, invitation);
          Jdbc.update(c, "UPDATE users SET last_login_at=CURRENT_TIMESTAMP WHERE user_id=?", id);
          return users.findById(c, id).orElseThrow();
        });
  }

  private static void accept(Connection c, long student, String email, String token)
      throws SQLException {
    long classId = Jdbc.scalar(c, "SELECT class_id FROM student_invitations WHERE token=?", token);
    if (classId == 0) throw new IllegalArgumentException("Invitation not found");
    long term;
    int capacity;
    long teacher;
    try (var p =
            Jdbc.statement(
                c,
                "SELECT subject_term_id,capacity,teacher_id,status,registration_enabled FROM"
                    + " subject_classes WHERE class_id=? FOR UPDATE",
                classId);
        var r = p.executeQuery()) {
      if (!r.next() || !"ACTIVE".equals(r.getString(4)) || !r.getBoolean(5))
        throw new IllegalArgumentException("Class is not accepting enrollment");
      term = r.getLong(1);
      capacity = r.getInt(2);
      teacher = r.getLong(3);
    }
    if (Jdbc.scalar(
            c,
            "SELECT COUNT(*) FROM subject_terms st JOIN subjects s ON s.subject_id=st.subject_id"
                + " JOIN academic_terms a ON a.term_id=st.term_id JOIN teacher_subjects t ON"
                + " t.subject_id=s.subject_id JOIN users u ON u.user_id=t.teacher_id WHERE"
                + " st.subject_term_id=? AND s.status='ACTIVE' AND a.is_active=TRUE AND"
                + " t.teacher_id=? AND t.is_active=TRUE AND u.role='TEACHER' AND"
                + " u.account_status='ACTIVE'",
            term,
            teacher)
        != 1) throw new IllegalArgumentException("Class Teacher or offering is ineligible");
    long invitationId;
    try (var p =
            Jdbc.statement(c, "SELECT * FROM student_invitations WHERE token=? FOR UPDATE", token);
        var r = p.executeQuery()) {
      if (!r.next()) throw new IllegalArgumentException("Invitation not found");
      invitationId = r.getLong("invitation_id");
      if (!email.equals(r.getString("email").strip().toLowerCase(java.util.Locale.ROOT)))
        throw new SecurityException("Use the invited Google email");
      if ("ACCEPTED".equals(r.getString("status")) && r.getLong("accepted_by") == student) return;
      if (!"PENDING".equals(r.getString("status"))
          || !r.getTimestamp("expires_at").toInstant().isAfter(Instant.now()))
        throw new IllegalArgumentException("Invitation is no longer valid");
    }
    long occupied =
        Jdbc.scalar(
                c,
                "SELECT COUNT(*) FROM class_enrollments WHERE class_id=? AND status='ACTIVE'",
                classId)
            + Jdbc.scalar(
                c,
                "SELECT COUNT(*) FROM student_invitations WHERE class_id=? AND status='PENDING' AND"
                    + " expires_at>CURRENT_TIMESTAMP",
                classId);
    if (occupied > capacity)
      throw new IllegalArgumentException("Class capacity changed; contact the Teacher");
    if (Jdbc.scalar(
            c,
            "SELECT COUNT(*) FROM student_invitations i JOIN subject_classes sc ON"
                + " sc.class_id=i.class_id WHERE sc.subject_term_id=? AND lower(trim(i.email))=?"
                + " AND i.status='PENDING' AND i.expires_at>CURRENT_TIMESTAMP AND"
                + " i.invitation_id<>?",
            term,
            email,
            invitationId)
        > 0) throw new IllegalArgumentException("Conflicting invitation in this Subject Term");
    long registration =
        Jdbc.scalar(
            c,
            "SELECT registration_id FROM subject_registrations WHERE student_id=? AND"
                + " subject_term_id=?",
            student,
            term);
    if (registration == 0)
      registration =
          Jdbc.insert(
              c,
              "INSERT INTO subject_registrations(student_id,subject_term_id) VALUES(?,?)",
              student,
              term);
    else
      Jdbc.update(
          c,
          "UPDATE subject_registrations SET status='ACTIVE',deactivated_at=NULL WHERE"
              + " registration_id=?",
          registration);
    long enrollment =
        Jdbc.scalar(
            c,
            "SELECT enrollment_id FROM class_enrollments WHERE registration_id=? AND"
                + " status='ACTIVE'",
            registration);
    if (enrollment != 0)
      throw new IllegalArgumentException(
          "Student already has an active placement in this Subject Term");
    enrollment =
        Jdbc.insert(
            c,
            "INSERT INTO class_enrollments(registration_id,class_id,enrollment_method)"
                + " VALUES(?,?,'INVITATION')",
            registration,
            classId);
    Jdbc.update(
        c,
        "UPDATE student_invitations SET"
            + " status='ACCEPTED',accepted_at=CURRENT_TIMESTAMP,accepted_by=?,enrollment_id=? WHERE"
            + " invitation_id=?",
        student,
        enrollment,
        invitationId);
  }
}
