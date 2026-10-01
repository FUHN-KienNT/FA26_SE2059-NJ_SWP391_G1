package vn.edu.fpt.ams.util;

/** Cryptographically random credentials and opaque OAuth/CSRF tokens. */
public final class PasswordGenerator {
  public String generate() {
    byte[] bytes = new byte[24];
    new java.security.SecureRandom().nextBytes(bytes);
    return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
