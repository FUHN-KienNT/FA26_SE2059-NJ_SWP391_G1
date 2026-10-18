package vn.edu.fpt.ams.controller;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.edu.fpt.ams.service.AuthenticationService;
import vn.edu.fpt.ams.util.*;

@WebServlet({"/auth/signin", "/auth/signout"})
public class AuthenticationServlet extends HttpServlet {
  private final AuthenticationService service = new AuthenticationService();

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    if (req.getServletPath().endsWith("/signout")) {
      res.setHeader("Allow", "POST");
      res.sendError(405, "Vui lòng sử dụng nút Đăng xuất");
      return;
    }
    res.setHeader("Cache-Control", "no-store");
    WebSupport.csrf(req);
    req.getRequestDispatcher("/WEB-INF/views/auth/signin.jsp").forward(req, res);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    req.setCharacterEncoding("UTF-8");
    res.setHeader("Cache-Control", "no-store");
    try {
      WebSupport.checkCsrf(req);
    } catch (SecurityException e) {
      res.setStatus(403);
      req.setAttribute("error", "Biểu mẫu đã hết hạn. Vui lòng tải lại trang và thử lại.");
      req.getRequestDispatcher("/WEB-INF/views/auth/signin.jsp").forward(req, res);
      return;
    }
    if (req.getServletPath().endsWith("/signout")) {
      req.getSession(false).invalidate();
      res.sendRedirect(req.getContextPath() + "/auth/signin?signedOut=1");
      return;
    }
    try {
      var user = service.signIn(req.getParameter("email"), req.getParameter("password"));
      AuthSession.establish(req, user);
      res.sendRedirect(req.getContextPath() + AuthSession.landing(user.role()));
    } catch (SecurityException e) {
      res.setStatus(401);
      req.setAttribute("error", e.getMessage());
      req.getRequestDispatcher("/WEB-INF/views/auth/signin.jsp").forward(req, res);
    } catch (IllegalStateException e) {
      req.getServletContext().log("Local sign-in unavailable", e);
      res.setStatus(503);
      req.setAttribute("error", "Sign-in is temporarily unavailable. Try again later.");
      req.getRequestDispatcher("/WEB-INF/views/auth/signin.jsp").forward(req, res);
    }
  }
}
