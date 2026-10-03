package vn.edu.fpt.ams.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import vn.edu.fpt.ams.dao.UserDAO;
import vn.edu.fpt.ams.model.UserRole;
import vn.edu.fpt.ams.service.DashboardService;
import vn.edu.fpt.ams.util.WebSupport;

/** Role landing pages; business dashboards remain owned by their tracking tasks. */
@WebServlet({"/expert/dashboard", "/student/dashboard"})
public class RoleDashboardServlet extends HttpServlet {
  private final DashboardService dashboards = new DashboardService();
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws java.io.IOException, jakarta.servlet.ServletException {
    try {
      long actor=WebSupport.actor(req);
      var account=new UserDAO().findById(actor).orElseThrow();
      req.setAttribute("account", account);
      if(account.role()==UserRole.EXPERT){
        req.setAttribute("expertPage","dashboard");
        req.setAttribute("summary",dashboards.expertSummary(actor));
        req.getRequestDispatcher("/WEB-INF/views/expert/dashboard.jsp").forward(req,res);
      } else req.getRequestDispatcher("/WEB-INF/views/auth/role-dashboard.jsp").forward(req, res);
    } catch (RuntimeException e) {
      WebSupport.failure(req, res, e);
    }
  }
}
