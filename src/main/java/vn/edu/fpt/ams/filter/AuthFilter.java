package vn.edu.fpt.ams.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.AssignmentPreviewAccess;

@WebFilter(urlPatterns = {"/admin/*", "/expert/*", "/teacher/*", "/student/*"})
public class AuthFilter implements Filter {
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse res = (HttpServletResponse) response;
    res.setHeader("Cache-Control", "no-store");
    if (AssignmentPreviewAccess.allows(req)) {
      chain.doFilter(request, response);
      return;
    }
    HttpSession session = req.getSession(false);
    Object id = session == null ? null : session.getAttribute("userId");
    Object role = session == null ? null : session.getAttribute("role");
    Object status = session == null ? null : session.getAttribute("accountStatus");
    if (!(id instanceof Long) || !(role instanceof UserRole) || status != AccountStatus.ACTIVE) {
      if ("GET".equals(req.getMethod())) res.sendRedirect(req.getContextPath() + "/auth/signin");
      else res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Vui lòng đăng nhập");
      return;
    }
    String path = req.getServletPath();
    String area = path.split("/")[1];
    if (!((area.equals("admin") && role == UserRole.ADMIN)
        || (area.equals("expert") && role == UserRole.EXPERT)
        || (area.equals("teacher") && role == UserRole.TEACHER)
        || (area.equals("student") && role == UserRole.STUDENT))) {
      res.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    try {
      var current = new vn.edu.fpt.ams.dao.UserDAO().findById((Long) id);
      if (current.isEmpty()
          || current.get().accountStatus() != AccountStatus.ACTIVE
          || current.get().role() != role) {
        session.invalidate();
        if ("GET".equals(req.getMethod())) res.sendRedirect(req.getContextPath() + "/auth/signin");
        else
          res.sendError(
              HttpServletResponse.SC_UNAUTHORIZED, "Quyền truy cập đã thay đổi. Vui lòng đăng nhập lại");
        return;
      }
    } catch (IllegalStateException ex) {
      req.getServletContext().log("Account revalidation failed", ex);
      res.sendError(503, "Không thể kết nối cơ sở dữ liệu");
      return;
    }
    chain.doFilter(request, response);
  }
}
