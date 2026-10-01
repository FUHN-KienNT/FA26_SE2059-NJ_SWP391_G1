package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PasswordHashTest {
  @Test
  void saltedHashesVerifyAndRejectWrongOrMalformedCredentials() {
    String first = PasswordHash.hash("secret"), second = PasswordHash.hash("secret");
    assertNotEquals(first, second);
    assertTrue(PasswordHash.verify("secret", first));
    assertFalse(PasswordHash.verify("wrong", first));
    assertFalse(PasswordHash.verify("secret", "placeholder"));
    assertFalse(PasswordHash.verify("secret", "pbkdf2-sha256$2147483647$bad$bad"));
  }
}
