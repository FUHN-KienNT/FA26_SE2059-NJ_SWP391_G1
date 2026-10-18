package vn.edu.fpt.ams.service;
import vn.edu.fpt.ams.dao.*;

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
          UserAdministrationDAO.lockUsers(c);
          long bySubject =
              GoogleEnrollmentDAO.findUserIdByGoogleSubject(c, identity.subject());
          long byEmail =
              GoogleEnrollmentDAO.findUserIdByEmail(c, email);
          if (byEmail != 0 && byEmail != bySubject)
            throw new SecurityException("Google identity conflicts with an existing account");
          long id = bySubject;
          if (id == 0) {
            String name = googleDisplayName(identity.name(), email);
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
          if (bySubject == 0) GoogleEnrollmentDAO.markEmailPending(c, id);
          UserAccount account = users.findById(c, id).orElseThrow();
          if (account.role() != UserRole.STUDENT || account.accountStatus() != AccountStatus.ACTIVE)
            throw new SecurityException("Active Student account required");
          if (GoogleEnrollmentDAO.countPendingVerification(c, id) == 1) return account;
          if (invitation != null && !invitation.isBlank()) accept(c, id, email, invitation);
          GoogleEnrollmentDAO.updateLastLogin(c, id);
          return users.findById(c, id).orElseThrow();
        });
  }

  /** Google display names can contain digits; email/subject establish identity. */
  private static String googleDisplayName(String raw, String email) {
    String name = raw == null ? "" : raw.codePoints()
        .filter(cp -> !Character.isISOControl(cp))
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
        .toString().strip();
    if (name.isBlank()) name = email.substring(0, email.indexOf('@'));
    // Fit the existing VARCHAR(50), without splitting Unicode surrogate pairs.
    if (name.codePointCount(0, name.length()) > 50)
      name = name.substring(0, name.offsetByCodePoints(0, 50));
    return name;
  }

  private static void accept(Connection c, long student, String email, String token)
      throws SQLException {
    long classId = GoogleEnrollmentDAO.findInvitedClassId(c, token);
    if (classId == 0) throw new IllegalArgumentException("Invitation not found");
    var placement = GoogleEnrollmentDAO.findClassForUpdate(c, classId);
    if (placement == null || !"ACTIVE".equals(placement.status()) || !placement.registrationEnabled())
      throw new IllegalArgumentException("Class is not accepting enrollment");
    long term = placement.term();
    int capacity = placement.capacity();
    long teacher = placement.teacher();
    if (GoogleEnrollmentDAO.countEligibleOfferingTeachers(c, term, teacher)
        != 1) throw new IllegalArgumentException("Class Teacher or offering is ineligible");
    var invitation = GoogleEnrollmentDAO.findInvitationForUpdate(c, token);
    if (invitation == null) throw new IllegalArgumentException("Invitation not found");
    long invitationId = invitation.id();
    if (!email.equals(invitation.email().strip().toLowerCase(java.util.Locale.ROOT)))
      throw new SecurityException("Use the invited Google email");
    if ("ACCEPTED".equals(invitation.status()) && invitation.acceptedBy() == student) return;
    if (!"PENDING".equals(invitation.status()) || !invitation.expiresAt().isAfter(Instant.now()))
      throw new IllegalArgumentException("Invitation is no longer valid");
    long occupied =
        GoogleEnrollmentDAO.countActiveEnrollments(c, classId)
            + GoogleEnrollmentDAO.countPendingInvitations(c, classId);
    if (occupied > capacity)
      throw new IllegalArgumentException("Class capacity changed; contact the Teacher");
    if (GoogleEnrollmentDAO.countConflictingInvitations(c, term, email, invitationId)
        > 0) throw new IllegalArgumentException("Conflicting invitation in this Subject Term");
    long registration =
        GoogleEnrollmentDAO.findRegistrationId(c, student, term);
    if (registration == 0)
      registration =
          GoogleEnrollmentDAO.insertRegistration(c, student, term);
    else
      GoogleEnrollmentDAO.reactivateRegistration(c, registration);
    long enrollment =
        GoogleEnrollmentDAO.findActiveEnrollmentId(c, registration);
    if (enrollment != 0)
      throw new IllegalArgumentException(
          "Student already has an active placement in this Subject Term");
    enrollment =
        GoogleEnrollmentDAO.insertInvitationEnrollment(c, registration, classId);
    GoogleEnrollmentDAO.acceptInvitation(c, student, enrollment, invitationId);
  }
}
