package vn.edu.fpt.ams.service;

import java.sql.*;
import java.util.*;
import vn.edu.fpt.ams.dao.UserDAO;
import vn.edu.fpt.ams.dto.UserCreateRequest;
import vn.edu.fpt.ams.integration.EmailService;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.*;

/** UC05/06 administration; never provisions Students or changes Google identities. */
public class UserService {
  private final Jdbc.Connections connections;
  private final EmailService mail;
  private final UserDAO users = new UserDAO();

  public UserService() {
    this(DBConnection::getConnection, new EmailService());
  }

  public UserService(Jdbc.Connections source, EmailService mail) {
    this.connections = source;
    this.mail = mail;
  }

  public record Delivery(long userId, boolean delivered) {}

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
      String sql="SELECT COUNT(*) FROM users WHERE (lower(full_name) LIKE ? OR lower(email) LIKE ?)";
      var args=new ArrayList<Object>();
      String term="%"+(keyword==null?"":keyword.toLowerCase(Locale.ROOT))+"%";
      args.add(term); args.add(term);
      if(role!=null){sql+=" AND role=?";args.add(role.name());}
      if(status!=null){sql+=" AND account_status=?";args.add(status.name());}
      try(var ps=Jdbc.statement(c,sql,args.toArray());var rs=ps.executeQuery()){rs.next();return rs.getLong(1);}
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
          var out = new LinkedHashMap<Long, String>();
          try (var p =
                  Jdbc.statement(
                      c,
                      "SELECT subject_id,subject_code,subject_name FROM subjects WHERE"
                          + " status='ACTIVE' ORDER BY subject_code");
              var r = p.executeQuery()) {
            while (r.next()) out.put(r.getLong(1), r.getString(2) + " â€” " + r.getString(3));
          }
          return out;
        });
  }

  public Set<Long> eligibility(long actor, long user) {
    return Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          var out = new HashSet<Long>();
          try (var p =
                  Jdbc.statement(
                      c,
                      "SELECT subject_id FROM teacher_subjects WHERE teacher_id=? AND"
                          + " is_active=TRUE",
                      user);
              var r = p.executeQuery()) {
            while (r.next()) out.add(r.getLong(1));
          }
          return out;
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
          Jdbc.update(
              c,
              "UPDATE users SET full_name=?,email=?,role=?,updated_at=CURRENT_TIMESTAMP WHERE"
                  + " user_id=?",
              n,
              e,
              role.name(),
              id);
          setEligibility(c, id, role, eligible);
          audit(c, actor, id, "UPDATE_ACCOUNT");
          return null;
        });
  }

  public void status(long actor, long id, AccountStatus target) {
    if (target == null) throw new IllegalArgumentException("Status required");
    Jdbc.transaction(
        connections,
        c -> {
          lockUsers(c);
          Access.admin(c, actor);
          UserAccount u = users.findById(c, id).orElseThrow();
          if (u.accountStatus() == target) return null;
          if (target == AccountStatus.INACTIVE) {
            if (actor == id) throw new IllegalArgumentException("Cannot deactivate yourself");
            // Parent locks serialize responsibility coverage with cooperating class/subject
            // workflows.
            try (var p =
                    Jdbc.statement(
                        c, "SELECT subject_id FROM subjects ORDER BY subject_id FOR UPDATE");
                var r = p.executeQuery()) {
              while (r.next()) {}
            }
            try (var p =
                    Jdbc.statement(
                        c, "SELECT class_id FROM subject_classes ORDER BY class_id FOR UPDATE");
                var r = p.executeQuery()) {
              while (r.next()) {}
            }
            if (Jdbc.scalar(
                    c,
                    "SELECT COUNT(*) FROM subject_classes WHERE teacher_id=? AND status='ACTIVE'",
                    id)
                > 0)
              throw new IllegalArgumentException("Replace the Teacher of active Classes first");
            if (Jdbc.scalar(
                    c,
                    "SELECT COUNT(*) FROM subjects s JOIN subject_experts e ON"
                        + " e.subject_id=s.subject_id WHERE s.status='ACTIVE' AND e.expert_id=? AND"
                        + " e.is_active=TRUE AND e.responsibility_type='PRIMARY' AND NOT"
                        + " EXISTS(SELECT 1 FROM subject_experts x JOIN users u ON"
                        + " u.user_id=x.expert_id WHERE x.subject_id=s.subject_id AND"
                        + " x.expert_id<>? AND x.is_active=TRUE AND x.responsibility_type='PRIMARY'"
                        + " AND u.account_status='ACTIVE')",
                    id,
                    id)
                > 0)
              throw new IllegalArgumentException("An active Subject would lose its Primary Expert");
            if (Jdbc.scalar(
                    c,
                    "SELECT COUNT(*) FROM subject_classes sc JOIN class_experts e ON"
                        + " e.class_id=sc.class_id WHERE sc.status='ACTIVE' AND e.expert_id=? AND"
                        + " e.is_active=TRUE AND NOT EXISTS(SELECT 1 FROM class_experts x JOIN"
                        + " users u ON u.user_id=x.expert_id JOIN subject_experts se ON"
                        + " se.subject_id=x.subject_id AND se.expert_id=x.expert_id WHERE"
                        + " x.class_id=sc.class_id AND x.expert_id<>? AND x.is_active=TRUE AND"
                        + " u.account_status='ACTIVE' AND se.is_active=TRUE)",
                    id,
                    id)
                > 0)
              throw new IllegalArgumentException("An active Class would lose its Class Expert");
            Jdbc.update(
                c,
                "UPDATE class_experts SET is_active=FALSE,deactivated_at=CURRENT_TIMESTAMP WHERE"
                    + " expert_id=? AND is_active=TRUE",
                id);
            Jdbc.update(
                c,
                "UPDATE subject_experts SET is_active=FALSE,deactivated_at=CURRENT_TIMESTAMP WHERE"
                    + " expert_id=? AND is_active=TRUE",
                id);
            Jdbc.update(
                c,
                "UPDATE teacher_subjects SET is_active=FALSE,deactivated_at=CURRENT_TIMESTAMP WHERE"
                    + " teacher_id=? AND is_active=TRUE",
                id);
            Jdbc.update(
                c,
                "UPDATE additional_class_assignment_offers SET status=CASE WHEN"
                    + " expires_at<=CURRENT_TIMESTAMP THEN 'EXPIRED' ELSE 'CANCELLED' END WHERE"
                    + " teacher_id=? AND status='PENDING'",
                id);
            Jdbc.update(
                c,
                "UPDATE submissions SET status='VOID' WHERE status='IN_PROGRESS' AND enrollment_id"
                    + " IN(SELECT e.enrollment_id FROM class_enrollments e JOIN"
                    + " subject_registrations r ON r.registration_id=e.registration_id WHERE"
                    + " r.student_id=?)",
                id);
          }
          Jdbc.update(
              c,
              "UPDATE users SET account_status=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?",
              target.name(),
              id);
          audit(c, actor, id, "STATUS_" + target);
          return null;
        });
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
              Jdbc.update(
                  c,
                  "UPDATE users SET password_hash=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?",
                  hash,
                  id);
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
    try (var p = Jdbc.statement(c, "SELECT user_id FROM users ORDER BY user_id FOR UPDATE");
        var r = p.executeQuery()) {
      while (r.next()) {}
    }
  }

  private static void audit(Connection c, long actor, long id, String event) throws SQLException {
    Jdbc.update(
        c,
        "INSERT INTO account_audit_events(actor_id,user_id,event_type) VALUES(?,?,?)",
        actor,
        id,
        event);
  }

  private static boolean hasResponsibilities(Connection c, long id) throws SQLException {
    return Jdbc.scalar(
                c, "SELECT COUNT(*) FROM subject_experts WHERE expert_id=? AND is_active=TRUE", id)
            + Jdbc.scalar(
                c, "SELECT COUNT(*) FROM class_experts WHERE expert_id=? AND is_active=TRUE", id)
            + Jdbc.scalar(
                c,
                "SELECT COUNT(*) FROM teacher_subjects WHERE teacher_id=? AND is_active=TRUE",
                id)
            + Jdbc.scalar(
                c,
                "SELECT COUNT(*) FROM subject_classes WHERE teacher_id=? AND status='ACTIVE'",
                id)
            + Jdbc.scalar(
                c,
                "SELECT COUNT(*) FROM additional_class_assignment_offers WHERE teacher_id=? AND"
                    + " status='PENDING' AND expires_at>CURRENT_TIMESTAMP",
                id)
        > 0;
  }

  private static void setEligibility(Connection c, long id, UserRole role, Set<Long> selected)
      throws SQLException {
    Set<Long> desired = selected == null ? Set.of() : Set.copyOf(selected);
    if (role != UserRole.TEACHER && !desired.isEmpty())
      throw new IllegalArgumentException("Eligibility belongs only to Teachers");
    if (!desired.isEmpty()
        && Jdbc.scalar(
                c, "SELECT COUNT(*) FROM users WHERE user_id=? AND account_status='ACTIVE'", id)
            == 0) throw new IllegalArgumentException("Eligibility requires an active Teacher");
    var existing = new HashMap<Long, Boolean>();
    try (var p =
            Jdbc.statement(
                c,
                "SELECT subject_id,is_active FROM teacher_subjects WHERE teacher_id=? FOR UPDATE",
                id);
        var r = p.executeQuery()) {
      while (r.next()) existing.put(r.getLong(1), r.getBoolean(2));
    }
    for (var entry : existing.entrySet())
      if (entry.getValue() && !desired.contains(entry.getKey())) {
        if (Jdbc.scalar(
                c,
                "SELECT COUNT(*) FROM subject_classes sc JOIN subject_terms st ON"
                    + " st.subject_term_id=sc.subject_term_id WHERE sc.teacher_id=? AND"
                    + " sc.status='ACTIVE' AND st.subject_id=?",
                id,
                entry.getKey())
            > 0)
          throw new IllegalArgumentException(
              "Cannot remove eligibility while teaching an active Class");
        Jdbc.update(
            c,
            "UPDATE teacher_subjects SET is_active=FALSE,deactivated_at=CURRENT_TIMESTAMP WHERE"
                + " teacher_id=? AND subject_id=?",
            id,
            entry.getKey());
      }
    for (long subject : desired) {
      if (Jdbc.scalar(
              c, "SELECT COUNT(*) FROM subjects WHERE subject_id=? AND status='ACTIVE'", subject)
          == 0) throw new IllegalArgumentException("Select an active Subject");
      if (!existing.containsKey(subject))
        Jdbc.update(
            c, "INSERT INTO teacher_subjects(teacher_id,subject_id) VALUES(?,?)", id, subject);
      else if (!existing.get(subject))
        Jdbc.update(
            c,
            "UPDATE teacher_subjects SET is_active=TRUE,deactivated_at=NULL WHERE teacher_id=? AND"
                + " subject_id=?",
            id,
            subject);
    }
  }
}
