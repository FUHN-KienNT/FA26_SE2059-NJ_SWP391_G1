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
import vn.edu.fpt.ams.util.Vietnamese;
import vn.edu.fpt.ams.util.WebSupport;

@WebServlet("/admin/subjects")
public class SubjectServlet extends HttpServlet {
  private final SubjectService service;

  public SubjectServlet() { this(new SubjectService()); }

  SubjectServlet(SubjectService service) { this.service = Objects.requireNonNull(service); }

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
      if ("edit".equals(mode) && req.getAttribute("editTermId") instanceof Long selectedTerm) {
        @SuppressWarnings("unchecked")
        var editable = (List<AcademicTerm>) req.getAttribute("editableTerms");
        if (editable.stream().noneMatch(t -> t.termId() == selectedTerm)) {
          req.getSession().setAttribute("notice", "Học kỳ này chỉ dùng để tra cứu.");
          res.sendRedirect(req.getContextPath() + "/admin/subjects?mode=view&id=" + WebSupport.id(req.getParameter("id")));
          return;
        }
      }
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
      Long returnTerm = null;
      if ("edit".equals(req.getParameter("returnTo"))) {
        String rawTerm = req.getParameter("termId");
        if (rawTerm != null && !rawTerm.isBlank()) {
          returnTerm = WebSupport.id(rawTerm);
          long selected = returnTerm;
          if (service.termOptions(actor).stream().noneMatch(t -> t.termId() == selected))
            throw new IllegalArgumentException("Học kỳ không hợp lệ.");
        }
      }
      String returnValue = req.getParameter("returnUserId");
      Long returnUser = "experts".equals(action) && returnValue != null && !returnValue.isBlank() ? WebSupport.id(returnValue) : null;
      long id;
      String notice;
      if ("create".equals(action)) {
        String openNow = req.getParameter("openForTerm");
        if (openNow != null && !"true".equals(openNow))
          throw new IllegalArgumentException("Lựa chọn thiết lập học kỳ không hợp lệ.");
        SubjectService.InitialTerm initialTerm = openNow == null ? null : new SubjectService.InitialTerm(
            WebSupport.id(req.getParameter("termId")), req.getParameter("registrationEnabled") != null,
            instant(req.getParameter("registrationOpensAt")), instant(req.getParameter("registrationClosesAt")));
        id =
            service.create(
                actor,
                new SubjectDTO(
                    0,
                    req.getParameter("subjectCode"),
                    req.getParameter("subjectName"),
                    req.getParameter("description"),
                    subjectStatus(req.getParameter("initialStatus"))),
                assignments(req), initialTerm);
        notice = initialTerm == null ? "Đã tạo môn học trong danh mục." : "Đã tạo môn học và thiết lập đăng ký theo học kỳ.";
      } else {
        id = WebSupport.id(req.getParameter("id"));
        if ("update".equals(action)) {
          service.update(
              actor,
              id,
              req.getParameter("subjectCode"),
              req.getParameter("subjectName"),
              req.getParameter("description"));
          notice = "Đã cập nhật môn học.";
        } else if ("experts".equals(action)) {
          service.assignExperts(actor, id, responsibilityAssignments(req));
          notice = "Đã cập nhật phân công chuyên gia.";
        } else if ("status".equals(action)) {
          if ("edit".equals(req.getParameter("returnTo"))
              && !"true".equals(req.getParameter("confirmed"))
              && !"true".equals(req.getParameter("confirmedWithoutScript")))
            throw new IllegalArgumentException("Vui lòng xác nhận thay đổi trạng thái môn học.");
          service.changeStatus(
              actor,
              id,
              subjectStatus(req.getParameter("status")));
          notice = "Đã cập nhật trạng thái môn học.";
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
          notice = "Đã lưu đăng ký môn học theo kỳ.";
        } else {
          throw new IllegalArgumentException("Thao tác môn học không hợp lệ.");
        }
      }
      req.getSession().setAttribute("notice", notice);
      if ("edit".equals(req.getParameter("returnTo")))
        req.getSession().setAttribute("subjectEditNoticeAction", action);
      else req.getSession().removeAttribute("subjectEditNoticeAction");
      String destination =
          "create".equals(action)
              ? "/admin/subjects?mode=view&id=" + id
              : "status".equals(action) && "list".equals(req.getParameter("returnTo"))
                  ? "/admin/subjects"
                  : "/admin/subjects?mode=" + successMode(action) + "&id=" + id;
      if (returnUser != null) destination += "&returnUserId=" + returnUser;
      if ("edit".equals(req.getParameter("returnTo"))) {
        destination = "/admin/subjects?mode=edit&id=" + id;
        if (returnTerm != null) destination += "&termId=" + returnTerm;
        destination += "#" + editSection(action);
      }
      res.sendRedirect(req.getContextPath() + destination);
    } catch (IllegalArgumentException e) {
      redisplay(req, res, e);
    } catch (RuntimeException e) {
      WebSupport.failure(req, res, e);
    }
  }

  private void prepareList(HttpServletRequest req, long actor) {
    int page = WebSupport.page(req.getParameter("page"));
    SubjectStatus status = subjectStatus(req.getParameter("status"));
    String rawTerm = req.getParameter("termId");
    Long termId = rawTerm == null || rawTerm.isBlank() ? null : WebSupport.id(rawTerm);
    var terms = service.termOptions(actor);
    if (termId != null) {
      long selected = termId;
      if (terms.stream().noneMatch(t -> t.termId() == selected))
        throw new IllegalArgumentException("Học kỳ không hợp lệ.");
      req.setAttribute("selectedTermReadOnly", service.eligibleTerms(actor).stream()
          .noneMatch(t -> t.termId() == selected));
    }
    req.setAttribute("termOptions", terms);
    req.setAttribute("selectedTermId", termId);
    req.setAttribute("page", page);
    req.setAttribute(
        "subjects",
        service.list(actor, req.getParameter("keyword"), status, termId, page));
    req.setAttribute(
        "total",
        service.count(actor, req.getParameter("keyword"), status, termId));
    WebSupport.csrf(req);
  }

  private void prepareScreen(HttpServletRequest req, long actor, String mode) {
    req.setAttribute("mode", mode);
    if ("create".equals(mode)) {
      req.setAttribute("eligibleExperts", service.eligibleExperts(actor, null));
      req.setAttribute("createTerms", service.eligibleTerms(actor));
    } else if (Set.of("view", "edit", "experts", "status", "term").contains(mode)) {
      long id = WebSupport.id(req.getParameter("id"));
      req.setAttribute("details", service.details(actor, id));
      if ("view".equals(mode)) req.setAttribute("termOptions", service.termOptions(actor));
      if ("edit".equals(mode)) {
        req.setAttribute("eligibleExperts", service.eligibleExperts(actor, null));
        var terms = service.termOptions(actor);
        var editableTerms = service.eligibleTerms(actor);
        req.setAttribute("editTerms", terms);
        req.setAttribute("editableTerms", editableTerms);
        String rawTerm = req.getParameter("termId");
        Long selected = rawTerm == null || rawTerm.isBlank() ? null : WebSupport.id(rawTerm);
        if (selected == null)
          selected = editableTerms.stream().filter(AcademicTerm::current).findFirst()
              .or(() -> editableTerms.stream().findFirst()).map(AcademicTerm::termId).orElse(null);
        Long selectedId = selected;
        if (selectedId != null && terms.stream().noneMatch(t -> t.termId() == selectedId))
          throw new IllegalArgumentException("Học kỳ không hợp lệ.");
        req.setAttribute("editTermId", selected);
      }
      if ("experts".equals(mode))
        req.setAttribute(
            "eligibleExperts",
            service.eligibleExperts(actor, null));
      if ("term".equals(mode))
        req.setAttribute("eligibleTerms", service.eligibleTerms(actor));
    } else {
      throw new IllegalArgumentException("Màn hình môn học không hợp lệ.");
    }
    WebSupport.csrf(req);
  }

  private void redisplay(
      HttpServletRequest req, HttpServletResponse res, IllegalArgumentException error)
      throws IOException, ServletException {
    try {
      long actor = WebSupport.actor(req);
      String action = req.getParameter("action");
      String returnValue = req.getParameter("returnUserId");
      Long returnUser = "experts".equals(action) && returnValue != null && !returnValue.isBlank() ? WebSupport.id(returnValue) : null;
      String mode =
          switch (action == null ? "" : action) {
            case "create" -> "create";
            case "update" -> "edit";
            case "experts" -> "experts";
            case "status" -> "status";
            case "term" -> "term";
            default -> throw new IllegalArgumentException("Thao tác môn học không hợp lệ.");
          };
      if ("edit".equals(req.getParameter("returnTo"))) mode = "edit";
      prepareScreen(req, actor, mode);
      req.setAttribute("error", Vietnamese.label(error.getMessage()));
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
      throw new IllegalArgumentException("Chuyên gia bị chọn trùng trong cùng một vai trò.");
    var overlap = new HashSet<>(primary);
    overlap.retainAll(supporting);
    if (!overlap.isEmpty())
      throw new IllegalArgumentException(
          "Một chuyên gia không thể đồng thời là chuyên gia chính và chuyên gia hỗ trợ.");
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
        throw new IllegalArgumentException("Vai trò phụ trách của chuyên gia không hợp lệ.");
      }
      if (expertId <= 0 || result.containsKey(expertId))
        throw new IllegalArgumentException("Vai trò phụ trách của chuyên gia không hợp lệ.");
      String[] values = req.getParameterValues(name);
      if (values == null || values.length != 1)
        throw new IllegalArgumentException("Mỗi chuyên gia chỉ được chọn một vai trò phụ trách.");
      String value = values[0];
      if ("NOT_ASSIGNED".equals(value)) continue;
      try {
        result.put(expertId, ResponsibilityType.valueOf(value));
      } catch (RuntimeException e) {
        throw new IllegalArgumentException("Vai trò phụ trách của chuyên gia không hợp lệ.");
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
          "Vui lòng nhập thời gian mở và đóng đăng ký hợp lệ.");
    }
  }

  private static String view(String mode) {
    return switch (mode) {
      case "create", "edit" -> "/WEB-INF/views/subjects/form.jsp";
      case "view" -> "/WEB-INF/views/subjects/detail.jsp";
      case "experts" -> "/WEB-INF/views/subjects/experts.jsp";
      case "status" -> "/WEB-INF/views/subjects/status.jsp";
      case "term" -> "/WEB-INF/views/subjects/term.jsp";
      default -> throw new IllegalArgumentException("Màn hình môn học không hợp lệ.");
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

  private static String editSection(String action) {
    return switch (action) {
      case "term" -> "subject-registration";
      case "status" -> "subject-status";
      case "experts" -> "subject-experts";
      default -> "subject-information";
    };
  }

  private static SubjectStatus subjectStatus(String value) {
    try {
      return WebSupport.option(SubjectStatus.class, value);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Trạng thái môn học không hợp lệ.");
    }
  }
}
