package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.edu.fpt.ams.dto.UserCreateRequest;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.UserService;
import vn.edu.fpt.ams.util.WebSupport;

@WebServlet("/admin/users")
public class UserServlet extends HttpServlet {
  private static AccountStatus requiredStatus(String value) {
    AccountStatus status = WebSupport.option(AccountStatus.class, value);
    if (status == null) throw new IllegalArgumentException("Vui lòng chọn trạng thái tài khoản.");
    return status;
  }
  private final UserService service = new UserService();

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    String view = "/WEB-INF/views/users/list.jsp";
    try {
      String mode = req.getParameter("mode");
      if ("view".equals(mode) || "create".equals(mode) || "edit".equals(mode)) {
        view = "/WEB-INF/views/users/detail.jsp";
        req.setAttribute("mode", mode);
        long actor = WebSupport.actor(req);
        req.setAttribute("subjects", service.subjects(actor));
        req.setAttribute("expertSubjects", service.expertSubjects(actor, 0));
        req.setAttribute("roleOptions", service.roleOptions(actor));
        if (!"create".equals(mode)) {
          long id = WebSupport.id(req.getParameter("id"));
          req.setAttribute("user", service.detail(actor, id));
          req.setAttribute("eligibility", service.eligibility(actor, id));
          req.setAttribute("expertSubjects", service.expertSubjects(actor, id));
        }
        WebSupport.csrf(req);
      } else {
        req.setAttribute("page", WebSupport.page(req.getParameter("page")));
        var users =
            service.list(
                WebSupport.actor(req),
                req.getParameter("keyword"),
                WebSupport.option(UserRole.class, req.getParameter("role")),
                WebSupport.option(AccountStatus.class, req.getParameter("status")),
                WebSupport.page(req.getParameter("page")));
        req.setAttribute("users", users);
        req.setAttribute("total", service.count(WebSupport.actor(req), req.getParameter("keyword"), WebSupport.option(UserRole.class, req.getParameter("role")), WebSupport.option(AccountStatus.class, req.getParameter("status"))));
        req.setAttribute(
            "reviewReasons",
            new vn.edu.fpt.ams.service.AccountReviewService()
                .reasons(WebSupport.actor(req), users));
      }
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
      long actor = WebSupport.actor(req), id = 0;
      String action = req.getParameter("action"), notice = "Changes saved";
      String continueValue = req.getParameter("continueSubjectId");
      Long continueSubject = "update".equals(action) && continueValue != null && !continueValue.isBlank() ? WebSupport.id(continueValue) : null;
      if ("create".equals(action)) {
        var result =
            service.create(
                actor,
                new UserCreateRequest(
                    req.getParameter("fullName"),
                    req.getParameter("email"),
                    WebSupport.option(UserRole.class, req.getParameter("role"))),
                WebSupport.ids(req.getParameterValues("subjects")));
        id = result.userId();
        notice =
            result.delivered()
                ? "Account created; credential email sent"
                : "Account created, but email failed. Configure SMTP and use Reissue credential.";
      } else {
        id = WebSupport.id(req.getParameter("id"));
        if ("update".equals(action))
          service.update(
              actor,
              id,
              req.getParameter("fullName"),
              req.getParameter("email"),
              WebSupport.option(UserRole.class, req.getParameter("role")),
              WebSupport.ids(req.getParameterValues("subjects")), requiredStatus(req.getParameter("status")));
        else if ("status".equals(action))
          service.status(
              actor, id, WebSupport.option(AccountStatus.class, req.getParameter("status")));
        else if ("credential".equals(action)) {
          var result = service.resendCredential(actor, id);
          notice =
              result.delivered()
                  ? "New credential sent"
                  : "Credential replaced, but email failed. Check SMTP and retry.";
        } else throw new IllegalArgumentException("Unknown action");
      }
      req.getSession().setAttribute("notice", notice);
      if (continueSubject != null) {
        res.sendRedirect(req.getContextPath() + "/admin/subjects?mode=experts&id=" + continueSubject + "&returnUserId=" + id);
        return;
      }
      res.sendRedirect(req.getContextPath() + ("list".equals(req.getParameter("returnTo")) && "status".equals(action) ? "/admin/users" : "/admin/users?mode=edit&id=" + id));
    } catch (IllegalArgumentException e) {
      try {
        long actor = WebSupport.actor(req);
        boolean create = "create".equals(req.getParameter("action"));
        req.setAttribute("mode", create ? "create" : "edit");
        req.setAttribute("subjects", service.subjects(actor));
        req.setAttribute("expertSubjects", service.expertSubjects(actor, 0));
        req.setAttribute("roleOptions", service.roleOptions(actor));
        if (!create) {
          long id = WebSupport.id(req.getParameter("id"));
          req.setAttribute("user", service.detail(actor, id));
          req.setAttribute("eligibility", service.eligibility(actor, id));
          req.setAttribute("expertSubjects", service.expertSubjects(actor, id));
        }
        req.setAttribute("error", e.getMessage());
        req.setAttribute("submitted", create || "update".equals(req.getParameter("action")));
        res.setStatus(400);
        req.getRequestDispatcher("/WEB-INF/views/users/detail.jsp").forward(req, res);
      } catch (RuntimeException retry) {
        WebSupport.failure(req, res, retry);
      }
    } catch (RuntimeException e) {
      WebSupport.failure(req, res, e);
    }
  }
}
