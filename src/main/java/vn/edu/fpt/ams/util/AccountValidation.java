package vn.edu.fpt.ams.util;

import java.util.Locale;

public final class AccountValidation {
  private AccountValidation() {}

  public static String email(String v) {
    String s = v == null ? "" : v.strip().toLowerCase(Locale.ROOT);
    if (s.length() > 100 || !s.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw new IllegalArgumentException("Valid email up to 100 characters required");
    return s;
  }

  public static String name(String v) {
    String s = v == null ? "" : v.strip();
    if (s.isEmpty() || s.length() > 50 || s.codePoints().anyMatch(Character::isDigit))
      throw new IllegalArgumentException("Full name requires 1–50 characters without digits");
    return s;
  }

  public static String studentName(String value) {
    String name = value == null ? "" : value.strip();
    if (name.isBlank() || name.codePointCount(0, name.length()) > 50
        || name.codePoints().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Tên sinh viên cần từ 1 đến 50 ký tự hợp lệ.");
    return name;
  }
}
