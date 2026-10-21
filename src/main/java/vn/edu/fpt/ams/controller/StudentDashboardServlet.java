package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.edu.fpt.ams.model.AccountStatus;
import vn.edu.fpt.ams.model.UserRole;
import vn.edu.fpt.ams.service.StudentDashboardService;
import vn.edu.fpt.ams.util.WebSupport;

@WebServlet("/student/dashboard")
public class StudentDashboardServlet extends HttpServlet {
    private final StudentDashboardService service;

    public StudentDashboardServlet() { this(new StudentDashboardService()); }
    StudentDashboardServlet(StudentDashboardService service) { this.service = service; }

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        res.setHeader("Cache-Control", "no-store");
        try {
            var session = req.getSession(false);
            if (session == null || session.getAttribute("role") != UserRole.STUDENT
                    || session.getAttribute("accountStatus") != AccountStatus.ACTIVE)
                throw new SecurityException("Vui lòng đăng nhập bằng tài khoản sinh viên đang hoạt động.");
            // Deliberately ignore studentId/userId query parameters; identity comes from the session.
            req.setAttribute("dashboard", service.load(WebSupport.actor(req)));
            req.setAttribute("termOptions", service.termOptions(WebSupport.actor(req)));
            req.getRequestDispatcher("/WEB-INF/views/student/dashboard.jsp").forward(req, res);
        } catch (RuntimeException ex) {
            WebSupport.failure(req, res, ex);
        }
    }
}
