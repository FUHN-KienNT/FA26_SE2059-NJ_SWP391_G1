package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.edu.fpt.ams.service.StudentVerificationService;
import vn.edu.fpt.ams.util.WebSupport;

@WebServlet("/auth/student-verification")
public class StudentVerificationServlet extends HttpServlet {
  private final StudentVerificationService verification = new StudentVerificationService();
  @Override protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
    res.setHeader("Cache-Control", "no-store");
    // GET only displays a confirmation form, so mail scanners cannot consume the link.
    req.getRequestDispatcher("/WEB-INF/views/auth/student-verification.jsp").forward(req, res);
  }
  @Override protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
    req.setCharacterEncoding("UTF-8");
    res.setHeader("Cache-Control", "no-store");
    try {
      WebSupport.checkCsrf(req);
      if ("confirm".equals(req.getParameter("action"))) {
        verification.confirm(req.getParameter("token"));
        req.setAttribute("confirmed", true);
        req.getSession().removeAttribute("pendingStudentId");
      } else {
        Object id = req.getSession().getAttribute("pendingStudentId");
        if (!(id instanceof Long)) throw new SecurityException("Vui lòng đăng nhập Google trước khi gửi lại email.");
        boolean sent = verification.send((Long) id);
        req.setAttribute("notice", sent ? "Đã gửi email xác nhận. Vui lòng kiểm tra cả thư mục Spam." : "Chưa gửi được email. Vui lòng liên hệ quản trị viên kiểm tra SMTP.");
      }
    } catch (IllegalArgumentException e) { req.setAttribute("error", e.getMessage()); }
    catch (SecurityException e) { res.setStatus(403); req.setAttribute("error", e.getMessage()); }
    catch (IllegalStateException e) { res.setStatus(503); req.setAttribute("error", "Không thể xử lý xác nhận lúc này. Vui lòng thử lại."); }
    req.getRequestDispatcher("/WEB-INF/views/auth/student-verification.jsp").forward(req, res);
  }
}
