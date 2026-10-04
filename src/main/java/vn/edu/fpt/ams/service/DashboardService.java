package vn.edu.fpt.ams.service;

import java.util.*;
import vn.edu.fpt.ams.dto.ExpertDashboardDTO;
import vn.edu.fpt.ams.model.UserRole;
import vn.edu.fpt.ams.util.*;

public class DashboardService {
  private final Jdbc.Connections connections;

  public DashboardService() {
    this(DBConnection::getConnection);
  }

  public DashboardService(Jdbc.Connections source) {
    connections = source;
  }

  public Map<String, Long> summary(long actor) {
    return Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          var out = new LinkedHashMap<String, Long>();
          for (String t :
              List.of(
                  "users", "subjects", "academic_terms", "subject_classes", "assignment_templates"))
            out.put(t, Jdbc.scalar(c, "SELECT COUNT(*) FROM " + t));
          out.put(
              "active_users",
              Jdbc.scalar(c, "SELECT COUNT(*) FROM users WHERE account_status='ACTIVE'"));
          out.put(
              "pending_invitations",
              Jdbc.scalar(
                  c,
                  "SELECT COUNT(*) FROM student_invitations WHERE status='PENDING' AND"
                      + " expires_at>CURRENT_TIMESTAMP"));
          return out;
        });
  }

  public String currentTerm(long actor) {
    return Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
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
        });
  }

  public ExpertDashboardDTO expertSummary(long actor) {
    return Jdbc.transaction(
        connections,
        c -> {
          if (Access.active(c, actor) != UserRole.EXPERT)
            throw new SecurityException("Expert access is required");
          long subjects = Jdbc.scalar(c,
              "SELECT COUNT(*) FROM subject_experts WHERE expert_id=? AND is_active=TRUE", actor);
          long classes = Jdbc.scalar(c,
              "SELECT COUNT(DISTINCT sc.class_id) FROM subject_experts se"
                  + " JOIN subject_terms st ON st.subject_id=se.subject_id"
                  + " JOIN subject_classes sc ON sc.subject_term_id=st.subject_term_id"
                  + " WHERE se.expert_id=? AND se.is_active=TRUE AND"
                  + " (se.responsibility_type='PRIMARY' OR EXISTS (SELECT 1 FROM class_experts ce"
                  + " WHERE ce.class_id=sc.class_id AND ce.subject_id=se.subject_id"
                  + " AND ce.expert_id=se.expert_id AND ce.is_active=TRUE))", actor);
          long templates = Jdbc.scalar(c,
              "SELECT COUNT(DISTINCT t.template_id) FROM assignment_templates t"
                  + " JOIN subject_experts se ON se.subject_id=t.subject_id"
                  + " WHERE se.expert_id=? AND se.is_active=TRUE", actor);
          long requests = Jdbc.scalar(c,
              "SELECT COUNT(DISTINCT r.request_id) FROM additional_class_requests r"
                  + " JOIN overflow_batches b ON b.overflow_batch_id=r.overflow_batch_id"
                  + " JOIN subject_classes sc ON sc.class_id=b.source_class_id"
                  + " JOIN subject_terms st ON st.subject_term_id=sc.subject_term_id"
                  + " JOIN subject_experts se ON se.subject_id=st.subject_id"
                  + " WHERE se.expert_id=? AND se.is_active=TRUE"
                  + " AND se.responsibility_type='PRIMARY' AND r.status='PENDING_REVIEW'", actor);
          return new ExpertDashboardDTO(subjects, classes, templates, requests);
        });
  }
}
