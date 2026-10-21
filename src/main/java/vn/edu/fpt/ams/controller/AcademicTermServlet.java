package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import vn.edu.fpt.ams.service.MasterDataService;
import vn.edu.fpt.ams.util.WebSupport;

/** Academic Term list; existing /admin/master-data detail and action URLs remain valid. */
@WebServlet("/admin/terms")
public class AcademicTermServlet extends HttpServlet {
  private final MasterDataService service = new MasterDataService();

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    try {
      long actor = WebSupport.actor(req);
      String status = req.getParameter("status");
      Boolean active = null;
      if ("ACTIVE".equals(status)) active = true;
      else if ("INACTIVE".equals(status)) active = false;
      else if (status != null && !status.isBlank())
        throw new IllegalArgumentException("Trạng thái học kỳ không hợp lệ.");
      req.setAttribute("terms", service.listTerms(actor, req.getParameter("keyword"), active));
      req.setAttribute("calendarTerms", service.listTerms(actor, null, null));
      WebSupport.csrf(req);
      req.getRequestDispatcher("/WEB-INF/views/master-data/list.jsp").forward(req, res);
    } catch (RuntimeException e) {
      WebSupport.failure(req, res, e);
    }
  }
}
