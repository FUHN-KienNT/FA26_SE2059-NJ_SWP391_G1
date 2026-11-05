package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import vn.edu.fpt.ams.dto.StudentClassFilter;
import vn.edu.fpt.ams.model.AccountStatus;
import vn.edu.fpt.ams.model.UserRole;
import vn.edu.fpt.ams.service.StudentClassService;
import vn.edu.fpt.ams.util.WebSupport;

@WebServlet("/student/classes")
public class StudentClassServlet extends HttpServlet {
    private final StudentClassService service;

    public StudentClassServlet() {
        this(new StudentClassService());
    }

    StudentClassServlet(StudentClassService service) {
        this.service = service;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        res.setHeader("Cache-Control", "no-store");

        try {
            var session = req.getSession(false);

            if (session == null
                    || session.getAttribute("role") != UserRole.STUDENT
                    || session.getAttribute("accountStatus") != AccountStatus.ACTIVE)
                throw new SecurityException(
                        "Vui lòng đăng nhập bằng tài khoản sinh viên đang hoạt động.");

            long studentId = WebSupport.actor(req);
            Long classId =
                    req.getParameter("classId") == null
                            ? null
                            : WebSupport.id(req.getParameter("classId"));
            var filter =
                    StudentClassFilter.from(
                            req.getParameter("q"),
                            req.getParameter("responseType"),
                            req.getParameter("status"),
                            req.getParameter("page"));

            req.setAttribute("classPage", service.load(studentId, classId, filter));
            req.getRequestDispatcher("/WEB-INF/views/student/class-detail.jsp").forward(req, res);
        } catch (RuntimeException ex) {
            WebSupport.failure(req, res, ex);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setHeader("Cache-Control", "no-store");
        res.setHeader("Allow", "GET, HEAD, OPTIONS");
        res.sendError(
                HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                "Trang lớp học chỉ hỗ trợ xem thông tin.");
    }
}
