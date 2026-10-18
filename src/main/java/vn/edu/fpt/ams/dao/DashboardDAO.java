package vn.edu.fpt.ams.dao;
import java.sql.*;
import java.util.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.util.Jdbc;
/** Data access only. Caller owns the connection and transaction. */
public final class DashboardDAO {
  public static String findCurrentTermLabel(Connection c) throws SQLException {
          try (var p =
                  Jdbc.statement(
                      c,
                      "SELECT term_code,term_name FROM academic_terms WHERE is_current=TRUE AND"
                          + " is_active=TRUE");
              var r = p.executeQuery()) {
            return r.next()
                ? r.getString(1) + " — " + r.getString(2)
                : "No active Current Term selected";
          }

  }
  public static long countEntities(Connection c, String table) throws SQLException {
    if (!Set.of("users","subjects","academic_terms","subject_classes","assignment_templates").contains(table)) throw new IllegalArgumentException("Unsupported dashboard entity");
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM " + table);
  }

  public static long countActiveUsers(Connection c) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM users WHERE account_status='ACTIVE'");
  }

  public static long countPendingInvitations(Connection c) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM student_invitations WHERE status='PENDING' AND"
                      + " expires_at>CURRENT_TIMESTAMP");
  }

  public static long countExpertSubjects(Connection c, long actorId) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(*) FROM subject_experts WHERE expert_id=? AND is_active=TRUE", actorId);
  }

  public static long countExpertClasses(Connection c, long actorId) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(DISTINCT sc.class_id) FROM subject_experts se"
                  + " JOIN subject_terms st ON st.subject_id=se.subject_id"
                  + " JOIN subject_classes sc ON sc.subject_term_id=st.subject_term_id"
                  + " WHERE se.expert_id=? AND se.is_active=TRUE AND"
                  + " (se.responsibility_type='PRIMARY' OR EXISTS (SELECT 1 FROM class_experts ce"
                  + " WHERE ce.class_id=sc.class_id AND ce.subject_id=se.subject_id"
                  + " AND ce.expert_id=se.expert_id AND ce.is_active=TRUE))", actorId);
  }

  public static long countExpertTemplates(Connection c, long actorId) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(DISTINCT t.template_id) FROM assignment_templates t"
                  + " JOIN subject_experts se ON se.subject_id=t.subject_id"
                  + " WHERE se.expert_id=? AND se.is_active=TRUE", actorId);
  }

  public static long countExpertRequests(Connection c, long actorId) throws SQLException {
    return Jdbc.scalar(c, "SELECT COUNT(DISTINCT r.request_id) FROM additional_class_requests r"
                  + " JOIN overflow_batches b ON b.overflow_batch_id=r.overflow_batch_id"
                  + " JOIN subject_classes sc ON sc.class_id=b.source_class_id"
                  + " JOIN subject_terms st ON st.subject_term_id=sc.subject_term_id"
                  + " JOIN subject_experts se ON se.subject_id=st.subject_id"
                  + " WHERE se.expert_id=? AND se.is_active=TRUE"
                  + " AND se.responsibility_type='PRIMARY' AND r.status='PENDING_REVIEW'", actorId);
  }
}
