package vn.edu.fpt.ams.service;

import java.util.*;
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
}
