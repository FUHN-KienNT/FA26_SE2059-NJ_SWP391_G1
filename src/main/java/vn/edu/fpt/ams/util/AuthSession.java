package vn.edu.fpt.ams.util;

import jakarta.servlet.http.*;
import vn.edu.fpt.ams.model.*;

public final class AuthSession {
  private AuthSession() {}

  public static void establish(HttpServletRequest request, UserAccount account) {
    var old = request.getSession(false);
    if (old != null) old.invalidate();
    var session = request.getSession(true);
    session.setAttribute("userId", account.userId());
    session.setAttribute("role", account.role());
    session.setAttribute("accountStatus", account.accountStatus());
    session.setAttribute("credentialFingerprint", fingerprint(account.passwordHash()));
    session.setMaxInactiveInterval(1800);
    WebSupport.csrf(request);
  }

  public static String fingerprint(String hash) {
    if (hash == null) return "";
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(hash.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public static String landing(UserRole role) {
    return switch (role) {
      case ADMIN -> "/admin/dashboard";
      case EXPERT -> "/expert/dashboard";
      case TEACHER -> "/teacher/dashboard";
      case STUDENT -> "/student/dashboard";
    };
  }
}
