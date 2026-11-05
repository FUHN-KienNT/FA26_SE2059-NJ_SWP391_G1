package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import vn.edu.fpt.ams.model.AccountStatus;
import vn.edu.fpt.ams.model.UserRole;
import vn.edu.fpt.ams.service.StudentResultService;
import vn.edu.fpt.ams.util.WebSupport;

@WebServlet("/student/results")
public class StudentResultServlet extends HttpServlet {
    private final StudentResultService service;

    public StudentResultServlet() {
        this(new StudentResultService());
    }

    StudentResultServlet(StudentResultService service) {
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

            long assignmentId = WebSupport.id(req.getParameter("assignmentId"));
            Long submissionId =
                    req.getParameter("submissionId") == null
                            ? null
                            : WebSupport.id(req.getParameter("submissionId"));

            req.setAttribute(
                    "resultPage", service.load(WebSupport.actor(req), assignmentId, submissionId));
            req.getRequestDispatcher("/WEB-INF/views/student/result.jsp").forward(req, res);
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
                "Trang kết quả chỉ hỗ trợ xem thông tin.");
    }
}
