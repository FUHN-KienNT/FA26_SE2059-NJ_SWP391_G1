package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.TemplateService;
import vn.edu.fpt.ams.util.WebSupport;

@WebServlet({"/expert/templates", "/teacher/templates"})
public class TemplateServlet extends HttpServlet {
  private final TemplateService service = new TemplateService();
  private static java.math.BigDecimal parseScore(String score) {
    if (score == null || !score.strip().matches("[0-9]{1,3}"))
      throw new IllegalArgumentException("Điểm tối đa phải là số nguyên từ 1 đến 100.");
    var value = new java.math.BigDecimal(score.strip());
    if (!vn.edu.fpt.ams.util.AssignmentRules.validMaximum(value))
      throw new IllegalArgumentException("Điểm tối đa phải là số nguyên từ 1 đến 100.");
    return value;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    String view = "/WEB-INF/views/assignment-templates/list.jsp";
    try {
      long actor = WebSupport.actor(req);
      boolean teacher = req.getServletPath().startsWith("/teacher/");
      var subjects = service.subjects(actor);
      req.setAttribute("subjects", subjects);
      req.setAttribute("teacherView", teacher);
      String raw = req.getParameter("subjectId");
      long subject =
          raw == null || raw.isBlank()
              ? (subjects.isEmpty() ? 0 : subjects.keySet().iterator().next())
              : WebSupport.id(raw);
      String mode = req.getParameter("mode");
      if ("view".equals(mode)
          || "create".equals(mode)
          || "edit".equals(mode)
          || "status".equals(mode)) {
        view = "/WEB-INF/views/assignment-templates/detail.jsp";
        req.setAttribute("mode", mode);
        if (teacher && !"view".equals(mode))
          throw new SecurityException("Teachers have read-only Template access");
        if ("create".equals(mode)) {
          if (service.responsibility(actor, subject) != ResponsibilityType.PRIMARY)
            throw new SecurityException("Only Primary Experts create Templates");
        } else {
          var template = service.findTemplate(actor, WebSupport.id(req.getParameter("id")));
          req.setAttribute("template", template);
          subject = template.subjectId();
        }
        if (!teacher) {
          var responsibility = service.responsibility(actor, subject);
          req.setAttribute("primary", responsibility == ResponsibilityType.PRIMARY);
          if ("status".equals(mode) && responsibility != ResponsibilityType.PRIMARY)
            throw new SecurityException("Only Primary Experts change status");
        }
        WebSupport.csrf(req);
      } else {
        var page = service.page(actor, subject, req.getParameter("keyword"),
            WebSupport.option(TemplateStatus.class, req.getParameter("status")),
            WebSupport.page(req.getParameter("page")));
        req.setAttribute("templates", page.items());
        req.setAttribute("templatePage", page);
        if (!teacher && subject != 0)
          req.setAttribute(
              "primary", service.responsibility(actor, subject) == ResponsibilityType.PRIMARY);
      }
      req.setAttribute("subjectId", subject);
    } catch (RuntimeException ex) {
      WebSupport.failure(req, res, ex);
      return;
    }
    req.getRequestDispatcher(view).forward(req, res);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    req.setCharacterEncoding("UTF-8");
    try {
      WebSupport.checkCsrf(req);
      if (req.getServletPath().startsWith("/teacher/"))
        throw new SecurityException("Teachers cannot modify Templates");
      long actor = WebSupport.actor(req), id = 0;
      String action = req.getParameter("action");
      if ("status".equals(action)) {
        id = WebSupport.id(req.getParameter("id"));
        service.changeStatus(
            actor, id, WebSupport.option(TemplateStatus.class, req.getParameter("status")));
      } else {
        long subject = WebSupport.id(req.getParameter("subjectId"));
        String score = req.getParameter("maximumScore");
        if (score == null || score.isBlank())
          throw new IllegalArgumentException("Vui lòng nhập điểm tối đa là số nguyên từ 1 đến 100.");
        var template =
            new AssignmentTemplate(
                "create".equals(action) ? 0 : WebSupport.id(req.getParameter("id")),
                subject,
                req.getParameter("title") == null ? null : req.getParameter("title").strip(),
                req.getParameter("instructions"),
                parseScore(score),
                TemplateStatus.ACTIVE,
                null,
                null);
        if ("create".equals(action)) id = service.createTemplate(actor, template);
        else if ("update".equals(action)) {
          service.updateTemplate(actor, template,
              WebSupport.option(TemplateStatus.class, req.getParameter("status")));
          id = template.templateId();
        } else throw new IllegalArgumentException("Unknown action");
      }
      req.getSession()
          .setAttribute("notice", "Template saved; existing Assignments remain unchanged");
      res.sendRedirect(req.getContextPath() + "/expert/templates?mode=view&id=" + id);
    } catch (IllegalArgumentException e) {
      try {
        long actor = WebSupport.actor(req);
        String action = req.getParameter("action");
        boolean create = "create".equals(action);
        long subject;
        if (create) {
          subject = WebSupport.id(req.getParameter("subjectId"));
          if (service.responsibility(actor, subject) != ResponsibilityType.PRIMARY)
            throw new SecurityException("Primary Expert required");
        } else {
          var t = service.findTemplate(actor, WebSupport.id(req.getParameter("id")));
          req.setAttribute("template", t);
          subject = t.subjectId();
        }
        req.setAttribute("subjects", service.subjects(actor));
        req.setAttribute("subjectId", subject);
        req.setAttribute("teacherView", false);
        req.setAttribute(
            "primary", service.responsibility(actor, subject) == ResponsibilityType.PRIMARY);
        req.setAttribute("mode", create ? "create" : "status".equals(action) ? "status" : "edit");
        req.setAttribute("error", e.getMessage());
        req.setAttribute("submitted", !"status".equals(action));
        res.setStatus(400);
        req.getRequestDispatcher("/WEB-INF/views/assignment-templates/detail.jsp")
            .forward(req, res);
      } catch (RuntimeException retry) {
        WebSupport.failure(req, res, retry);
      }
    } catch (RuntimeException e) {
      WebSupport.failure(req, res, e);
    }
  }
}
