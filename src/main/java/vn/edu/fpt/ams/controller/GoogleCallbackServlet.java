package vn.edu.fpt.ams.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import vn.edu.fpt.ams.integration.GoogleIdentityClient;
import vn.edu.fpt.ams.service.GoogleAccountService;
import vn.edu.fpt.ams.util.*;

@WebServlet({"/auth/google/start", "/auth/google/callback"})
public class GoogleCallbackServlet extends HttpServlet {
  private final GoogleIdentityClient google = new GoogleIdentityClient();
  private final GoogleAccountService accounts = new GoogleAccountService();

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    try {
      if (req.getServletPath().endsWith("/start")) {
        String state = new PasswordGenerator().generate(),
            nonce = new PasswordGenerator().generate();
        var s = req.getSession(true);
        s.setAttribute("googleState", state);
        s.setAttribute("googleNonce", nonce);
        s.setAttribute("googleStartedAt", System.currentTimeMillis());
        s.setAttribute("googleInvitation", req.getParameter("invitation") != null ? req.getParameter("invitation") : s.getAttribute("pendingStudentInvitation"));
        res.sendRedirect(
            "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&scope=openid%20email%20profile&client_id="
                + enc(AppConfig.required("GOOGLE_CLIENT_ID", "google.clientId"))
                + "&redirect_uri="
                + enc(AppConfig.required("GOOGLE_REDIRECT_URI", "google.redirectUri"))
                + "&state="
                + enc(state)
                + "&nonce="
                + enc(nonce));
        return;
      }
      var s = req.getSession(false);
      if (s == null) throw new SecurityException("Phiên đăng nhập Google không tồn tại");
      String nonce, invitation;
      synchronized (s) {
        String supplied = req.getParameter("state");
        String expected = (String) s.getAttribute("googleState");
        Long started = (Long) s.getAttribute("googleStartedAt");
        if (expected == null
            || supplied == null
            || started == null
            || System.currentTimeMillis() - started > 600000
            || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8)))
          throw new SecurityException("Phiên xác thực Google không hợp lệ hoặc đã hết hạn");
        nonce = (String) s.getAttribute("googleNonce");
        invitation = (String) s.getAttribute("googleInvitation");
        s.removeAttribute("googleState");
        s.removeAttribute("googleNonce");
        s.removeAttribute("googleStartedAt");
        s.removeAttribute("googleInvitation");
      }
      if (req.getParameter("error") != null || req.getParameter("code") == null)
        throw new SecurityException("Chưa hoàn tất xác thực Google");
      var account = accounts.signIn(google.exchange(req.getParameter("code"), nonce), invitation);
      var verification = new vn.edu.fpt.ams.service.StudentVerificationService();
      if (verification.pending(account.userId())) {
        req.getSession().removeAttribute("userId"); req.getSession().removeAttribute("role"); req.getSession().removeAttribute("accountStatus");
        req.getSession().setAttribute("pendingStudentId", account.userId());
        req.getSession().setAttribute("pendingStudentInvitation", invitation);
        try { verification.send(account.userId()); } catch (IllegalArgumentException cooldown) { /* Existing email can still be used. */ }
        res.sendRedirect(req.getContextPath() + "/auth/student-verification"); return;
      }
      req.getSession().removeAttribute("pendingStudentInvitation");
      AuthSession.establish(req, account);
      res.sendRedirect(req.getContextPath() + AuthSession.landing(account.role()));
    } catch (SecurityException e) {
      res.sendError(403, Vietnamese.label(e.getMessage()));
    } catch (IllegalArgumentException e) {
      res.sendError(400, Vietnamese.label(e.getMessage()));
    } catch (java.security.GeneralSecurityException e) {
      res.sendError(403, "Xác minh đăng nhập Google thất bại");
    } catch (IllegalStateException e) {
      res.sendError(503, "Cần cấu hình cơ sở dữ liệu và đăng nhập Google trước khi sử dụng");
    } catch (IOException e) {
      res.sendError(503, "Đăng nhập Google tạm thời không khả dụng. Vui lòng thử lại");
    }
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
