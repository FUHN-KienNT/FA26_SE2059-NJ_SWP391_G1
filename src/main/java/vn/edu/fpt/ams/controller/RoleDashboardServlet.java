package vn.edu.fpt.ams.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import vn.edu.fpt.ams.dao.UserDAO;
import vn.edu.fpt.ams.util.WebSupport;

/** Role landing pages; business dashboards remain owned by their tracking tasks. */
@WebServlet({"/expert/dashboard", "/student/dashboard"})
public class RoleDashboardServlet extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws java.io.IOException, jakarta.servlet.ServletException {
    try {
      req.setAttribute("account", new UserDAO().findById(WebSupport.actor(req)).orElseThrow());
      req.getRequestDispatcher("/WEB-INF/views/auth/role-dashboard.jsp").forward(req, res);
    } catch (RuntimeException e) {
      WebSupport.failure(req, res, e);
    }
  }
}
