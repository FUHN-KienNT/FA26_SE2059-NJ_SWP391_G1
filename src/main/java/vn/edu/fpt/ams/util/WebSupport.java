package vn.edu.fpt.ams.util;

import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class WebSupport {
  private WebSupport() {}

  public static String html(Object value) {
    return value == null
        ? ""
        : value
            .toString()
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
  }

  public static long actor(HttpServletRequest r) {
    Object id = r.getSession(false) == null ? null : r.getSession(false).getAttribute("userId");
    if (!(id instanceof Long)) throw new SecurityException("Sign-in required");
    return (Long) id;
  }

  public static String csrf(HttpServletRequest r) {
    var s = r.getSession();
    String token = (String) s.getAttribute("csrf");
    if (token == null) {
      token = new PasswordGenerator().generate();
      s.setAttribute("csrf", token);
    }
    return token;
  }

  public static void checkCsrf(HttpServletRequest r) {
    String submitted = r.getParameter("csrf");
    Object stored = r.getSession(false) == null ? null : r.getSession(false).getAttribute("csrf");
    if (!(stored instanceof String)
        || submitted == null
        || !MessageDigest.isEqual(
            ((String) stored).getBytes(StandardCharsets.UTF_8),
            submitted.getBytes(StandardCharsets.UTF_8)))
      throw new SecurityException("Form token is invalid; refresh the page");
  }

  public static long id(String v) {
    try {
      long n = Long.parseLong(v);
      if (n <= 0) throw new IllegalArgumentException();
      return n;
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("A positive record ID is required");
    }
  }

  public static int page(String v) {
    if (v == null || v.isBlank()) return 1;
    try {
      int n = Integer.parseInt(v);
      if (n < 1 || n > 100000) throw new IllegalArgumentException();
      return n;
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Invalid page");
    }
  }

  public static <T extends Enum<T>> T option(Class<T> type, String v) {
    return v == null || v.isBlank() ? null : Enum.valueOf(type, v);
  }

  public static Set<Long> ids(String[] values) {
    var out = new HashSet<Long>();
    if (values != null) for (String v : values) out.add(id(v));
    return out;
  }

  public static void failure(HttpServletRequest req, HttpServletResponse res, RuntimeException e)
      throws java.io.IOException {
    if (e instanceof SecurityException) res.sendError(403, Vietnamese.label(e.getMessage()));
    else if (e instanceof NoSuchElementException) res.sendError(404, "Không tìm thấy bản ghi");
    else if (e instanceof IllegalArgumentException) res.sendError(400, Vietnamese.label(e.getMessage()));
    else {
      req.getServletContext().log("AMS request failed", e);
      res.sendError(503, "Thao tác tạm thời không khả dụng. Vui lòng kiểm tra cấu hình ứng dụng và cơ sở dữ liệu.");
    }
  }
}
