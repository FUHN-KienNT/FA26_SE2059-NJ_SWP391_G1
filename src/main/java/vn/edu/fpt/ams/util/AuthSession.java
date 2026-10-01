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
    session.setMaxInactiveInterval(1800);
    WebSupport.csrf(request);
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
