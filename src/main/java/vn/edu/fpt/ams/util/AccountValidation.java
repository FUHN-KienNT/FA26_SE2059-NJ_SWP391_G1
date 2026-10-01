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
}
