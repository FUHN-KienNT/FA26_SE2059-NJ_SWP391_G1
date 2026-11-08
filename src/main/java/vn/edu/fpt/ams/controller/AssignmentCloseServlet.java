package vn.edu.fpt.ams.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import vn.edu.fpt.ams.model.UserRole;
import vn.edu.fpt.ams.service.StudentObjectiveService;
import vn.edu.fpt.ams.util.WebSupport;

/** Permanently closes reception before grading without cancelling accepted work. */
@WebServlet("/teacher/assignments/close")
public class AssignmentCloseServlet extends HttpServlet {
    private final StudentObjectiveService work = new StudentObjectiveService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setHeader("Cache-Control", "no-store");

        try {
            if (req.getSession(false) == null
                    || req.getSession(false).getAttribute("role") != UserRole.TEACHER)
                throw new SecurityException("Cần đăng nhập bằng tài khoản giảng viên.");

            WebSupport.checkCsrf(req);
            long assignmentId = WebSupport.id(req.getParameter("assignmentId"));
            int count = work.closeSubmissions(WebSupport.actor(req), assignmentId);
            req.getSession().setAttribute("actionNotice",
                    "Đã khóa nhận bài vĩnh viễn. Đã chốt " + count
                            + " bài làm đang lưu. Bạn có thể bắt đầu chấm điểm.");
            req.getSession().setAttribute("actionNoticeType", "success");
            res.setStatus(303);
            res.setHeader("Location", req.getContextPath()
                    + "/teacher/assignments?mode=view&assignmentId=" + assignmentId);
        } catch (RuntimeException ex) {
            WebSupport.failure(req, res, ex);
        }
    }
}
