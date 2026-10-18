package vn.edu.fpt.ams.service;
import vn.edu.fpt.ams.dao.*;

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
            out.put(t, DashboardDAO.countEntities(c, t));
          out.put(
              "active_users",
              DashboardDAO.countActiveUsers(c));
          out.put(
              "pending_invitations",
              DashboardDAO.countPendingInvitations(c));
          return out;
        });
  }

  public String currentTerm(long actor) {
    return Jdbc.transaction(
        connections,
        c -> {
          Access.admin(c, actor);
          return DashboardDAO.findCurrentTermLabel(c);
        });
  }

  public ExpertDashboardDTO expertSummary(long actor) {
    return Jdbc.transaction(
        connections,
        c -> {
          if (Access.active(c, actor) != UserRole.EXPERT)
            throw new SecurityException("Expert access is required");
          long subjects = DashboardDAO.countExpertSubjects(c, actor);
          long classes = DashboardDAO.countExpertClasses(c, actor);
          long templates = DashboardDAO.countExpertTemplates(c, actor);
          long requests = DashboardDAO.countExpertRequests(c, actor);
          return new ExpertDashboardDTO(subjects, classes, templates, requests);
        });
  }
}
