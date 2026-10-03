package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import vn.edu.fpt.ams.dto.SubjectDTO;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.SubjectService;
import vn.edu.fpt.ams.util.WebSupport;

@WebServlet("/admin/subjects")
public class SubjectServlet extends HttpServlet {
  private final SubjectService service = new SubjectService();

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    try {
      long actor = WebSupport.actor(req);
      String mode = req.getParameter("mode");
      if (mode == null || mode.isBlank()) {
        prepareList(req, actor);
        req.getRequestDispatcher("/WEB-INF/views/subjects/list.jsp").forward(req, res);
        return;
      }
      prepareScreen(req, actor, mode);
      req.getRequestDispatcher(view(mode)).forward(req, res);
    } catch (RuntimeException e) {
      WebSupport.failure(req, res, e);
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    req.setCharacterEncoding("UTF-8");
    try {
      WebSupport.checkCsrf(req);
      long actor = WebSupport.actor(req);
      String action = req.getParameter("action");
      long id;
      String notice;
      if ("create".equals(action)) {
        id =
            service.create(
                actor,
                new SubjectDTO(
                    0,
                    req.getParameter("subjectCode"),
                    req.getParameter("subjectName"),
                    req.getParameter("description"),
                    WebSupport.option(
                        SubjectStatus.class, req.getParameter("initialStatus"))),
                assignments(req));
        notice = "Subject created";
      } else {
        id = WebSupport.id(req.getParameter("id"));
        if ("update".equals(action)) {
          service.update(
              actor,
              id,
              req.getParameter("subjectCode"),
              req.getParameter("subjectName"),
              req.getParameter("description"));
          notice = "Subject updated";
        } else if ("experts".equals(action)) {
          service.assignExperts(actor, id, responsibilityAssignments(req));
          notice = "Expert responsibilities updated";
        } else if ("status".equals(action)) {
          service.changeStatus(
              actor,
              id,
              WebSupport.option(SubjectStatus.class, req.getParameter("status")));
          notice = "Subject status updated";
        } else if ("term".equals(action)) {
          service.openForTerm(
              actor,
              new SubjectTerm(
                  0,
                  id,
                  WebSupport.id(req.getParameter("termId")),
                  req.getParameter("registrationEnabled") != null,
                  instant(req.getParameter("registrationOpensAt")),
                  instant(req.getParameter("registrationClosesAt"))));
          notice = "Subject Term configured";
        } else {
          throw new IllegalArgumentException("Unknown action");
        }
      }
      req.getSession().setAttribute("notice", notice);
      String destination =
          "create".equals(action)
              ? "/admin/subjects?mode=view&id=" + id
              : "status".equals(action) && "list".equals(req.getParameter("returnTo"))
                  ? "/admin/subjects"
                  : "/admin/subjects?mode=" + successMode(action) + "&id=" + id;
      res.sendRedirect(req.getContextPath() + destination);
    } catch (IllegalArgumentException e) {
      redisplay(req, res, e);
    } catch (RuntimeException e) {
      WebSupport.failure(req, res, e);
    }
  }

  private void prepareList(HttpServletRequest req, long actor) {
    int page = WebSupport.page(req.getParameter("page"));
    SubjectStatus status =
        WebSupport.option(SubjectStatus.class, req.getParameter("status"));
    req.setAttribute("page", page);
    req.setAttribute(
        "subjects",
        service.list(actor, req.getParameter("keyword"), status, page));
    req.setAttribute(
        "total",
        service.count(actor, req.getParameter("keyword"), status));
    WebSupport.csrf(req);
  }

  private void prepareScreen(HttpServletRequest req, long actor, String mode) {
    req.setAttribute("mode", mode);
    if ("create".equals(mode)) {
      req.setAttribute("eligibleExperts", service.eligibleExperts(actor, null));
    } else if (Set.of("view", "edit", "experts", "status", "term").contains(mode)) {
      long id = WebSupport.id(req.getParameter("id"));
      req.setAttribute("details", service.details(actor, id));
      if ("experts".equals(mode))
        req.setAttribute(
            "eligibleExperts",
            service.eligibleExperts(actor, null));
      if ("term".equals(mode))
        req.setAttribute("eligibleTerms", service.eligibleTerms(actor));
    } else {
      throw new IllegalArgumentException("Unknown Subject screen");
    }
    WebSupport.csrf(req);
  }

  private void redisplay(
      HttpServletRequest req, HttpServletResponse res, IllegalArgumentException error)
      throws IOException, ServletException {
    try {
      long actor = WebSupport.actor(req);
      String action = req.getParameter("action");
      String mode =
          switch (action == null ? "" : action) {
            case "create" -> "create";
            case "update" -> "edit";
            case "experts" -> "experts";
            case "status" -> "status";
            case "term" -> "term";
            default -> throw new IllegalArgumentException("Unknown action");
          };
      prepareScreen(req, actor, mode);
      req.setAttribute("error", error.getMessage());
      req.setAttribute("submitted", true);
      res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      req.getRequestDispatcher(view(mode)).forward(req, res);
    } catch (RuntimeException retry) {
      WebSupport.failure(req, res, retry);
    }
  }

  static Map<Long, ResponsibilityType> assignments(
      HttpServletRequest req) {
    String[] primaryValues = req.getParameterValues("primaryExperts");
    String[] supportingValues = req.getParameterValues("supportingExperts");
    Set<Long> primary = WebSupport.ids(primaryValues);
    Set<Long> supporting = WebSupport.ids(supportingValues);
    if ((primaryValues != null && primary.size() != primaryValues.length)
        || (supportingValues != null
            && supporting.size() != supportingValues.length))
      throw new IllegalArgumentException("Duplicate Expert assignment");
    var overlap = new HashSet<>(primary);
    overlap.retainAll(supporting);
    if (!overlap.isEmpty())
      throw new IllegalArgumentException(
          "An Expert cannot be both PRIMARY and SUPPORTING");
    var result = new LinkedHashMap<Long, ResponsibilityType>();
    primary.forEach(id -> result.put(id, ResponsibilityType.PRIMARY));
    supporting.forEach(id -> result.put(id, ResponsibilityType.SUPPORTING));
    return result;
  }

  static Map<Long, ResponsibilityType> responsibilityAssignments(
      HttpServletRequest req) {
    var result = new LinkedHashMap<Long, ResponsibilityType>();
    Enumeration<String> names = req.getParameterNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      if (!name.startsWith("responsibility_")) continue;
      String rawId = name.substring("responsibility_".length());
      long expertId;
      try {
        expertId = Long.parseLong(rawId);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid Expert responsibility");
      }
      if (expertId <= 0 || result.containsKey(expertId))
        throw new IllegalArgumentException("Invalid Expert responsibility");
      String[] values = req.getParameterValues(name);
      if (values == null || values.length != 1)
        throw new IllegalArgumentException("Select one responsibility per Expert");
      String value = values[0];
      if ("NOT_ASSIGNED".equals(value)) continue;
      try {
        result.put(expertId, ResponsibilityType.valueOf(value));
      } catch (RuntimeException e) {
        throw new IllegalArgumentException("Invalid Expert responsibility");
      }
    }
    return result;
  }

  private static Instant instant(String value) {
    try {
      if (value == null || value.isBlank()) throw new IllegalArgumentException();
      return LocalDateTime.parse(value)
          .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
          .toInstant();
    } catch (RuntimeException e) {
      throw new IllegalArgumentException(
          "Valid registration timestamps are required");
    }
  }

  private static String view(String mode) {
    return switch (mode) {
      case "create", "edit" -> "/WEB-INF/views/subjects/form.jsp";
      case "view" -> "/WEB-INF/views/subjects/detail.jsp";
      case "experts" -> "/WEB-INF/views/subjects/experts.jsp";
      case "status" -> "/WEB-INF/views/subjects/status.jsp";
      case "term" -> "/WEB-INF/views/subjects/term.jsp";
      default -> throw new IllegalArgumentException("Unknown Subject screen");
    };
  }

  private static String successMode(String action) {
    return switch (action) {
      case "update" -> "view";
      case "experts" -> "experts";
      case "status" -> "view";
      case "term" -> "term";
      default -> "view";
    };
  }
}
