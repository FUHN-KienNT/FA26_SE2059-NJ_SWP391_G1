package vn.edu.fpt.ams.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import vn.edu.fpt.ams.service.DashboardService;
import vn.edu.fpt.ams.util.WebSupport;

@WebServlet("/admin/dashboard")
public class AdminDashboardServlet extends HttpServlet {
  private final DashboardService service = new DashboardService();

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws java.io.IOException, jakarta.servlet.ServletException {
    try {
      long actor = WebSupport.actor(req);
      req.setAttribute("summary", service.summary(actor));
      req.setAttribute("currentTerm", service.currentTerm(actor));
      req.getRequestDispatcher("/WEB-INF/views/admin/dashboard.jsp").forward(req, res);
    } catch (RuntimeException e) {
      WebSupport.failure(req, res, e);
    }
  }
}
