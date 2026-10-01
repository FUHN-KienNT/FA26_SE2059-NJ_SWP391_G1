package vn.edu.fpt.ams.util;

import java.security.*;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHash {
  private PasswordHash() {}

  public static String hash(String password) {
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    return "pbkdf2-sha256$600000$"
        + Base64.getEncoder().encodeToString(salt)
        + "$"
        + Base64.getEncoder().encodeToString(derive(password, salt, 600000));
  }

  public static boolean verify(String password, String encoded) {
    try {
      String[] a = encoded.split("\\$");
      if (a.length != 4 || !a[0].equals("pbkdf2-sha256")) return false;
      int rounds = Integer.parseInt(a[1]);
      if (rounds < 10000 || rounds > 2000000) return false;
      return MessageDigest.isEqual(
          Base64.getDecoder().decode(a[3]),
          derive(password, Base64.getDecoder().decode(a[2]), rounds));
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static byte[] derive(String password, byte[] salt, int rounds) {
    var spec = new PBEKeySpec(password.toCharArray(), salt, rounds, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    } finally {
      spec.clearPassword();
    }
  }
}
