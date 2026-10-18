package vn.edu.fpt.ams.service;
import vn.edu.fpt.ams.dao.*;

import java.sql.*;
import java.util.*;
import vn.edu.fpt.ams.dao.UserDAO;
import vn.edu.fpt.ams.dao.SystemSettingDAO;
import vn.edu.fpt.ams.dto.UserCreateRequest;
import vn.edu.fpt.ams.dto.ExpertSubjectDTO;
import vn.edu.fpt.ams.integration.EmailService;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.*;

/** UC05/06 administration; never provisions Students or changes Google identities. */
public class UserService {
  private final Jdbc.Connections connections;
  private final EmailService mail;
  private final UserDAO users = new UserDAO();
  private final SystemSettingDAO settings = new SystemSettingDAO();

  public UserService() {
    this(DBConnection::getConnection, new EmailService());
  }

  public UserService(Jdbc.Connections source, EmailService mail) {
    this.connections = source;
    this.mail = mail;
  }

  public record Delivery(long userId, boolean delivered) {}

  public List<ExpertSubjectDTO> expertSubjects(long actor, long id) {
    return Jdbc.transaction(connections, c -> {
      Access.admin(c, actor);
      if (id < 0) throw new IllegalArgumentException("Invalid user ID");
      UserAccount account = id == 0 ? null : users.findById(c, id).orElseThrow();
      return UserAdministrationDAO.findExpertSubjects(c,
          account != null && account.role() == UserRole.EXPERT ? id : 0);
    });
  }

  /** Available choices for new staff accounts, ordered by configured priority. */
  public List<SystemSetting> roleOptions(long actor) {
    return Jdbc.transaction(connections, c -> {
      Access.admin(c, actor);
      return settings.activeOptions(c, SettingType.USER_ROLE).stream()
          .filter(s -> !"STUDENT".equals(s.value()))
          .toList();
    });
  }

  public List<UserAccount> list(
      long actor, String keyword, UserRole role, AccountStatus status, int page) {
    if (keyword != null && keyword.length() > 50)
      throw new IllegalArgumentException("Search maximum is 50 characters");
    return Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          return users.search(c, keyword, role, status, page);
        });
  }

  public long count(long actor, String keyword, UserRole role, AccountStatus status) {
    if (keyword != null && keyword.length() > 50) throw new IllegalArgumentException("Search maximum is 50 characters");
    return Jdbc.transaction(connections, c -> {
      Access.admin(c, actor);
      return UserAdministrationDAO.countSearch(c, keyword, role, status);
    });
  }

  public UserAccount detail(long actor, long id) {
    return Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          return users
              .findById(c, id)
              .orElseThrow(() -> new NoSuchElementException("User not found"));
        });
  }

  public Map<Long, String> subjects(long actor) {
    return Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          return UserAdministrationDAO.findActiveSubjectLabels(c);
        });
  }

  public Set<Long> eligibility(long actor, long user) {
    return Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          return UserAdministrationDAO.findEligibility(c, user);
        });
  }

  public Delivery create(long actor, UserCreateRequest request, Set<Long> eligible) {
    if (request.role() == null || request.role() == UserRole.STUDENT)
      throw new IllegalArgumentException("Only staff accounts can be created");
    String name = AccountValidation.name(request.fullName()),
        address = AccountValidation.email(request.email());
    String credential = new PasswordGenerator().generate();
    String hash = PasswordHash.hash(credential);
    long id =
        Jdbc.transaction(
            connections,
            c -> {
              lockUsers(c);
              Access.admin(c, actor);
              settings.requireActiveForNew(c, SettingType.USER_ROLE, request.role().name());
              long key =
                  users.insert(
                      c,
                      new UserAccount(
                          0,
                          name,
                          address,
                          request.role(),
                          AccountStatus.ACTIVE,
                          null,
                          hash,
                          null,
                          null,
                          null));
              setEligibility(c, key, request.role(), eligible);
              audit(c, actor, key, "CREATE_STAFF");
              return key;
            });
    return deliver(id, address, credential);
  }

  public void update(
      long actor, long id, String name, String email, UserRole role, Set<Long> eligible) {
    update(actor, id, name, email, role, eligible, null);
  }
  public void update(long actor, long id, String name, String email, UserRole role, Set<Long> eligible, AccountStatus target) {
    String n = AccountValidation.name(name), e = AccountValidation.email(email);
    Jdbc.transaction(
        connections,
        c -> {
          lockUsers(c);
          Access.admin(c, actor);
          UserAccount old = users.findById(c, id).orElseThrow();
          if (role == null) throw new IllegalArgumentException("Role is required");
          if (old.role() == UserRole.STUDENT) {
            if (role != UserRole.STUDENT || !old.email().equals(e))
              throw new IllegalArgumentException(
                  "Student Google identity and role cannot be changed");
          } else if (role == UserRole.STUDENT)
            throw new IllegalArgumentException("Staff accounts cannot become Google Students");
          if (id == actor && role != UserRole.ADMIN)
            throw new IllegalArgumentException("Cannot remove your own Admin role");
          if (role != old.role() && hasResponsibilities(c, id))
            throw new IllegalArgumentException(
                "Resolve active responsibilities before changing role");
          if (role != old.role() && old.accountStatus() == AccountStatus.INACTIVE)
            throw new IllegalArgumentException("Reactivate before changing staff role");
          if (role != old.role())
            settings.requireActiveForNew(c, SettingType.USER_ROLE, role.name());
          UserAdministrationDAO.updateIdentity(c, n, e, role.name(), id);
          setEligibility(c, id, role, eligible);
          if (target != null) changeStatus(c, actor, id, target);
          audit(c, actor, id, "UPDATE_ACCOUNT");
          return null;
        });
  }

  public void status(long actor, long id, AccountStatus target) {
    if (target == null) throw new IllegalArgumentException("Status required");
    Jdbc.transaction(connections, c -> changeStatus(c, actor, id, target));
  }
  private Void changeStatus(Connection c, long actor, long id, AccountStatus target) throws SQLException {
          lockUsers(c);
          Access.admin(c, actor);
          UserAccount u = users.findById(c, id).orElseThrow();
          if (u.accountStatus() == target) return null;
          if (target == AccountStatus.INACTIVE) {
            if (actor == id) throw new IllegalArgumentException("Cannot deactivate yourself");
            // Parent locks serialize responsibility coverage with cooperating class/subject
            // workflows.
            UserAdministrationDAO.lockResponsibilityParents(c);
            if (UserAdministrationDAO.countActiveClasses(c, id)
                > 0)
              throw new IllegalArgumentException("Replace the Teacher of active Classes first");
            if (UserAdministrationDAO.countUncoveredPrimarySubjects(c, id, id)
                > 0)
              throw new IllegalArgumentException("An active Subject would lose its Primary Expert");
            if (UserAdministrationDAO.countUncoveredExpertClasses(c, id, id)
                > 0)
              throw new IllegalArgumentException("An active Class would lose its Class Expert");
            UserAdministrationDAO.endClassExpertAssignments(c, id);
            UserAdministrationDAO.endSubjectExpertAssignments(c, id);
            UserAdministrationDAO.endTeachingEligibility(c, id);
            UserAdministrationDAO.cancelPendingOffers(c, id);
            UserAdministrationDAO.voidStudentAttempts(c, id);
          }
          UserAdministrationDAO.updateAccountStatus(c, target.name(), id);
          audit(c, actor, id, "STATUS_" + target);
          return null;
  }

  public Delivery resendCredential(long actor, long id) {
    String credential = new PasswordGenerator().generate(), hash = PasswordHash.hash(credential);
    String address =
        Jdbc.transaction(
            connections,
            c -> {
              lockUsers(c);
              Access.admin(c, actor);
              UserAccount u = users.findById(c, id).orElseThrow();
              if (u.role() == UserRole.STUDENT || u.accountStatus() != AccountStatus.ACTIVE)
                throw new IllegalArgumentException("Active staff account required");
              UserAdministrationDAO.updatePassword(c, hash, id);
              audit(c, actor, id, "REISSUE_STAFF_CREDENTIAL");
              return u.email();
            });
    return deliver(id, address, credential);
  }

  private Delivery deliver(long id, String address, String credential) {
    try {
      mail.sendNotification(
          address,
          "AMS staff account",
          "Your AMS email: "
              + address
              + "\nInitial password: "
              + credential
              + "\nChange your password after signing in.");
      return new Delivery(id, true);
    } catch (RuntimeException e) {
      return new Delivery(id, false);
    }
  }

  private static void lockUsers(Connection c) throws SQLException {
    UserAdministrationDAO.lockUsers(c);
  }

  private static void audit(Connection c, long actor, long id, String event) throws SQLException {
    UserAdministrationDAO.recordAudit(c, actor, id, event);
  }

  private static boolean hasResponsibilities(Connection c, long id) throws SQLException {
    return UserAdministrationDAO.countSubjectResponsibilities(c, id)
            + UserAdministrationDAO.countClassResponsibilities(c, id)
            + UserAdministrationDAO.countTeachingEligibility(c, id)
            + UserAdministrationDAO.countActiveClasses(c, id)
            + UserAdministrationDAO.countPendingOffers(c, id)
        > 0;
  }

  private static void setEligibility(Connection c, long id, UserRole role, Set<Long> selected)
      throws SQLException {
    Set<Long> desired = selected == null ? Set.of() : Set.copyOf(selected);
    if (role != UserRole.TEACHER && !desired.isEmpty())
      throw new IllegalArgumentException("Eligibility belongs only to Teachers");
    if (!desired.isEmpty()
        && UserAdministrationDAO.countActiveAccount(c, id)
            == 0) throw new IllegalArgumentException("Eligibility requires an active Teacher");
    var existing = UserAdministrationDAO.findEligibilityForUpdate(c, id);
    for (var entry : existing.entrySet())
      if (entry.getValue() && !desired.contains(entry.getKey())) {
        if (UserAdministrationDAO.countActiveTeachingClasses(c, id, entry.getKey())
            > 0)
          throw new IllegalArgumentException(
              "Cannot remove eligibility while teaching an active Class");
        UserAdministrationDAO.endSubjectEligibility(c, id, entry.getKey());
      }
    for (long subject : desired) {
      if (UserAdministrationDAO.countActiveSubject(c, subject)
          == 0) throw new IllegalArgumentException("Select an active Subject");
      if (!existing.containsKey(subject))
        UserAdministrationDAO.insertEligibility(c, id, subject);
      else if (!existing.get(subject))
        UserAdministrationDAO.reactivateEligibility(c, id, subject);
    }
  }
}
