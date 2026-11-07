package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.edu.fpt.ams.service.StaffAccountService;
import vn.edu.fpt.ams.util.WebSupport;

@WebServlet({"/auth/forgot-password", "/auth/reset-password"})
public class PasswordServlet extends HttpServlet {
  private final StaffAccountService service = new StaffAccountService();

  private boolean reset(HttpServletRequest request) {
    return request.getServletPath().endsWith("/reset-password");
  }

  private void render(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Referrer-Policy", "no-referrer");
    request.setAttribute("resetMode", reset(request));
    WebSupport.csrf(request);
    request.getRequestDispatcher("/WEB-INF/views/auth/password.jsp").forward(request, response);
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    render(request, response);
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    request.setCharacterEncoding("UTF-8");
    try {
      WebSupport.checkCsrf(request);
      if (reset(request)) {
        service.resetPassword(
            request.getParameter("token"),
            request.getParameter("password"),
            request.getParameter("confirmation"));
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        request.setAttribute("completed", true);
        request.setAttribute(
            "notice", "Đã đặt lại mật khẩu. Vui lòng đăng nhập bằng mật khẩu mới.");
      } else {
        vn.edu.fpt.ams.util.AccountValidation.email(request.getParameter("email"));
        Long next = (Long) request.getSession().getAttribute("passwordResetNextRequest");
        if (next == null || System.currentTimeMillis() >= next) {
          service.requestReset(request.getParameter("email"));
          request
              .getSession()
              .setAttribute("passwordResetNextRequest", System.currentTimeMillis() + 60000);
        }
        request.setAttribute(
            "notice",
            "Nếu email thuộc tài khoản nhân viên đang hoạt động, hệ thống sẽ gửi liên kết đặt lại"
                + " mật khẩu. Kiểm tra cả thư mục Spam; chờ 60 giây trước khi yêu cầu lại.");
      }
    } catch (IllegalArgumentException e) {
      request.setAttribute("error", e.getMessage());
    } catch (SecurityException e) {
      response.setStatus(403);
      request.setAttribute("error", "Biểu mẫu đã hết hạn. Vui lòng tải lại trang.");
    } catch (IllegalStateException e) {
      getServletContext().log("Password workflow unavailable", e);
      response.setStatus(503);
      request.setAttribute("error", "Chưa thể xử lý lúc này. Vui lòng thử lại sau.");
    }
    render(request, response);
  }
}
