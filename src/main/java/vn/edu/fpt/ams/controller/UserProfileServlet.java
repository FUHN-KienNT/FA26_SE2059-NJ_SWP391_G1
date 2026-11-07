package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.edu.fpt.ams.service.StaffAccountService;
import vn.edu.fpt.ams.service.UserProfileService;
import vn.edu.fpt.ams.util.WebSupport;

@WebServlet({"/admin/profile", "/expert/profile", "/teacher/profile", "/student/profile"})
public class UserProfileServlet extends HttpServlet {
  private final StaffAccountService service = new StaffAccountService();
  private final UserProfileService profiles = new UserProfileService();

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.setHeader("Cache-Control", "no-store");
    try {
      request.setAttribute("profile", profiles.profile(WebSupport.actor(request)));
      WebSupport.csrf(request);
      request.getRequestDispatcher("/WEB-INF/views/users/profile.jsp").forward(request, response);
    } catch (RuntimeException e) {
      WebSupport.failure(request, response, e);
    }
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    request.setCharacterEncoding("UTF-8");
    try {
      WebSupport.checkCsrf(request);
      long actor = WebSupport.actor(request);
      if ("password".equals(request.getParameter("action"))) {
        service.changePassword(
            actor,
            request.getParameter("currentPassword"),
            request.getParameter("password"),
            request.getParameter("confirmation"));
        request.getSession().invalidate();
        response.sendRedirect(request.getContextPath() + "/auth/signin?passwordChanged=1");
        return;
      } else if ("profile".equals(request.getParameter("action"))) {
        profiles.updateProfile(actor, request.getParameter("fullName"));
        request.getSession().setAttribute("notice", "Đã cập nhật hồ sơ cá nhân.");
        response.sendRedirect(request.getContextPath() + request.getServletPath());
        return;
      } else throw new IllegalArgumentException("Thao tác không hợp lệ.");
    } catch (IllegalArgumentException e) {
      request.setAttribute("error", e.getMessage());
    } catch (SecurityException e) {
      request.setAttribute("error", "Phiên hoặc biểu mẫu đã hết hạn. Vui lòng tải lại trang.");
    } catch (IllegalStateException e) {
      getServletContext().log("Profile update unavailable", e);
      request.setAttribute("error", "Không thể lưu lúc này. Vui lòng thử lại sau.");
    }
    doGet(request, response);
  }
}
