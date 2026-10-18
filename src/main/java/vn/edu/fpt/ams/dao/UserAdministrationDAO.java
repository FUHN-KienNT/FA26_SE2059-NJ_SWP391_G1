package vn.edu.fpt.ams.dao;
import java.sql.*;
import java.util.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.util.Jdbc;
/** Data access only. Caller owns the connection and transaction. */
public final class UserAdministrationDAO {
  public static List<ExpertSubjectDTO> findExpertSubjects(Connection c, long id) throws SQLException {
      var result = new ArrayList<ExpertSubjectDTO>();
      try (var p = Jdbc.statement(c,
          "SELECT s.subject_id,s.subject_code,s.subject_name,e.responsibility_type,e.is_active"
          + " FROM subjects s LEFT JOIN subject_experts e ON s.subject_id=e.subject_id AND e.expert_id=?"
          + " ORDER BY CASE WHEN e.is_active=TRUE THEN 0 ELSE 1 END,s.subject_code", id);
          var r = p.executeQuery()) {
        while (r.next()) result.add(new ExpertSubjectDTO(r.getLong(1), r.getString(2),
            r.getString(3), r.getString(4)==null ? null : ResponsibilityType.valueOf(r.getString(4)), r.getBoolean(5)));
      }
      return result;

  }

  public static long countSearch(Connection c, String keyword, UserRole role, AccountStatus status) throws SQLException {
      String sql="SELECT COUNT(*) FROM users WHERE (lower(full_name) LIKE ? OR lower(email) LIKE ?)";
      var args=new ArrayList<Object>();
      String term="%"+(keyword==null?"":keyword.toLowerCase(Locale.ROOT))+"%";
      args.add(term); args.add(term);
      if(role!=null){sql+=" AND role=?";args.add(role.name());}
      if(status!=null){sql+=" AND account_status=?";args.add(status.name());}
      try(var ps=Jdbc.statement(c,sql,args.toArray());var rs=ps.executeQuery()){rs.next();return rs.getLong(1);}

  }

  public static Map<Long,String> findActiveSubjectLabels(Connection c) throws SQLException {
          var out = new LinkedHashMap<Long, String>();
          try (var p =
                  Jdbc.statement(
                      c,
                      "SELECT subject_id,subject_code,subject_name FROM subjects WHERE"
                          + " status='ACTIVE' ORDER BY subject_code");
              var r = p.executeQuery()) {
            while (r.next()) out.put(r.getLong(1), r.getString(2) + " — " + r.getString(3));
          }
          return out;

  }

  public static Set<Long> findEligibility(Connection c, long user) throws SQLException {
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

  }

  public static void lockResponsibilityParents(Connection c) throws SQLException {
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

  }

  public static void lockUsers(Connection c) throws SQLException {
    try (var p = Jdbc.statement(c, "SELECT user_id FROM users ORDER BY user_id FOR UPDATE");
        var r = p.executeQuery()) {
      while (r.next()) {}
    }
  }

  public static Map<Long,Boolean> findEligibilityForUpdate(Connection c, long id) throws SQLException {
    var existing = new HashMap<Long, Boolean>();
    try (var p =
            Jdbc.statement(
                c,
                "SELECT subject_id,is_active FROM teacher_subjects WHERE teacher_id=? FOR UPDATE",
                id);
        var r = p.executeQuery()) {
      while (r.next()) existing.put(r.getLong(1), r.getBoolean(2));
    }
    return existing;
  }
  public static int updateIdentity(Connection c, String fullName, String email, String roleName, long userId) throws SQLException {
    return Jdbc.update(c, "UPDATE users SET full_name=?,email=?,role=?,updated_at=CURRENT_TIMESTAMP WHERE"
                  + " user_id=?", fullName, email, roleName, userId);
  }

  public static long countActiveClasses(Connection c, long userId) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM subject_classes WHERE teacher_id=? AND status='ACTIVE'", userId);
  }

  public static long countUncoveredPrimarySubjects(Connection c, long userId, long userId2) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM subjects s JOIN subject_experts e ON"
                        + " e.subject_id=s.subject_id WHERE s.status='ACTIVE' AND e.expert_id=? AND"
                        + " e.is_active=TRUE AND e.responsibility_type='PRIMARY' AND NOT"
                        + " EXISTS(SELECT 1 FROM subject_experts x JOIN users u ON"
                        + " u.user_id=x.expert_id WHERE x.subject_id=s.subject_id AND"
                        + " x.expert_id<>? AND x.is_active=TRUE AND x.responsibility_type='PRIMARY'"
                        + " AND u.account_status='ACTIVE')", userId, userId2);
  }

  public static long countUncoveredExpertClasses(Connection c, long userId, long userId2) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM subject_classes sc JOIN class_experts e ON"
                        + " e.class_id=sc.class_id WHERE sc.status='ACTIVE' AND e.expert_id=? AND"
                        + " e.is_active=TRUE AND NOT EXISTS(SELECT 1 FROM class_experts x JOIN"
                        + " users u ON u.user_id=x.expert_id JOIN subject_experts se ON"
                        + " se.subject_id=x.subject_id AND se.expert_id=x.expert_id WHERE"
                        + " x.class_id=sc.class_id AND x.expert_id<>? AND x.is_active=TRUE AND"
                        + " u.account_status='ACTIVE' AND se.is_active=TRUE)", userId, userId2);
  }

  public static int endClassExpertAssignments(Connection c, long userId) throws SQLException {
    return Jdbc.update(c, "UPDATE class_experts SET is_active=FALSE,deactivated_at=CURRENT_TIMESTAMP WHERE"
                    + " expert_id=? AND is_active=TRUE", userId);
  }

  public static int endSubjectExpertAssignments(Connection c, long userId) throws SQLException {
    return Jdbc.update(c, "UPDATE subject_experts SET is_active=FALSE,deactivated_at=CURRENT_TIMESTAMP WHERE"
                    + " expert_id=? AND is_active=TRUE", userId);
  }

  public static int endTeachingEligibility(Connection c, long userId) throws SQLException {
    return Jdbc.update(c, "UPDATE teacher_subjects SET is_active=FALSE,deactivated_at=CURRENT_TIMESTAMP WHERE"
                    + " teacher_id=? AND is_active=TRUE", userId);
  }

  public static int cancelPendingOffers(Connection c, long userId) throws SQLException {
    return Jdbc.update(c, "UPDATE additional_class_assignment_offers SET status=CASE WHEN"
                    + " expires_at<=CURRENT_TIMESTAMP THEN 'EXPIRED' ELSE 'CANCELLED' END WHERE"
                    + " teacher_id=? AND status='PENDING'", userId);
  }

  public static int voidStudentAttempts(Connection c, long userId) throws SQLException {
    return Jdbc.update(c, "UPDATE submissions SET status='VOID' WHERE status='IN_PROGRESS' AND enrollment_id"
                    + " IN(SELECT e.enrollment_id FROM class_enrollments e JOIN"
                    + " subject_registrations r ON r.registration_id=e.registration_id WHERE"
                    + " r.student_id=?)", userId);
  }

  public static int updateAccountStatus(Connection c, String status, long userId) throws SQLException {
    return Jdbc.update(c, "UPDATE users SET account_status=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?", status, userId);
  }

  public static int updatePassword(Connection c, String passwordHash, long userId) throws SQLException {
    return Jdbc.update(c, "UPDATE users SET password_hash=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?", passwordHash, userId);
  }

  public static int recordAudit(Connection c, long actorId, long userId, String eventType) throws SQLException {
    return Jdbc.update(c, "INSERT INTO account_audit_events(actor_id,user_id,event_type) VALUES(?,?,?)", actorId, userId, eventType);
  }

  public static long countSubjectResponsibilities(Connection c, long userId) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM subject_experts WHERE expert_id=? AND is_active=TRUE", userId);
  }

  public static long countClassResponsibilities(Connection c, long userId) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM class_experts WHERE expert_id=? AND is_active=TRUE", userId);
  }

  public static long countTeachingEligibility(Connection c, long userId) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM teacher_subjects WHERE teacher_id=? AND is_active=TRUE", userId);
  }

  public static long countPendingOffers(Connection c, long userId) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM additional_class_assignment_offers WHERE teacher_id=? AND"
                    + " status='PENDING' AND expires_at>CURRENT_TIMESTAMP", userId);
  }

  public static long countActiveAccount(Connection c, long userId) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM users WHERE user_id=? AND account_status='ACTIVE'", userId);
  }

  public static long countActiveTeachingClasses(Connection c, long userId, long subjectId) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM subject_classes sc JOIN subject_terms st ON"
                    + " st.subject_term_id=sc.subject_term_id WHERE sc.teacher_id=? AND"
                    + " sc.status='ACTIVE' AND st.subject_id=?", userId, subjectId);
  }

  public static int endSubjectEligibility(Connection c, long userId, long subjectId) throws SQLException {
    return Jdbc.update(c, "UPDATE teacher_subjects SET is_active=FALSE,deactivated_at=CURRENT_TIMESTAMP WHERE"
                + " teacher_id=? AND subject_id=?", userId, subjectId);
  }

  public static long countActiveSubject(Connection c, long subjectId) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM subjects WHERE subject_id=? AND status='ACTIVE'", subjectId);
  }

  public static int insertEligibility(Connection c, long userId, long subjectId) throws SQLException {
    return Jdbc.update(c, "INSERT INTO teacher_subjects(teacher_id,subject_id) VALUES(?,?)", userId, subjectId);
  }

  public static int reactivateEligibility(Connection c, long userId, long subjectId) throws SQLException {
    return Jdbc.update(c, "UPDATE teacher_subjects SET is_active=TRUE,deactivated_at=NULL WHERE teacher_id=? AND"
                + " subject_id=?", userId, subjectId);
  }
}
