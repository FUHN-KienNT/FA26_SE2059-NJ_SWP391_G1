package vn.edu.fpt.ams.integration;

import static org.junit.jupiter.api.Assertions.*;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.auth.openidconnect.IdTokenVerifier;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.testing.http.MockHttpTransport;
import java.security.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GoogleIdentityClientTest {
  private static final GsonFactory JSON = GsonFactory.getDefaultInstance();
  private static final long NOW = 1_800_000_000L;
  private static final String CLIENT = "ams-test-client";
  private static final String NONCE = "test-session-nonce";
  private static KeyPair trustedKey, otherKey;

  @BeforeAll static void keys() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    trustedKey = generator.generateKeyPair();
    otherKey = generator.generateKeyPair();
  }

  private GoogleIdToken.Payload payload() {
    return new GoogleIdToken.Payload().setIssuer("https://accounts.google.com").setAudience(CLIENT)
        .setSubject("google-test-subject").setIssuedAtTimeSeconds(NOW).setExpirationTimeSeconds(NOW + 3600)
        .setEmail("student@example.test").setEmailVerified(true).setNonce(NONCE).set("name", "Test Student");
  }

  private GoogleIdTokenVerifier verifier(boolean zeroSkew) {
    var builder = GoogleIdentityClient.verifierBuilder(new MockHttpTransport(), JSON, CLIENT)
        .setClock(() -> NOW * 1000);
    if (zeroSkew) builder.setAcceptableTimeSkewSeconds(0);
    return new OfflineVerifier(builder, trustedKey.getPublic());
  }

  private String signed(GoogleIdToken.Payload payload, KeyPair key) throws Exception {
    return JsonWebSignature.signUsingRsaSha256(key.getPrivate(), JSON,
        new JsonWebSignature.Header().setAlgorithm("RS256"), payload);
  }

  @Test void reproducesZeroSkewFailureAndAcceptsMinorClockDifferenceWithGoogleDefault() throws Exception {
    String token = signed(payload().setIssuedAtTimeSeconds(NOW + 2), trustedKey);
    assertThrows(SecurityException.class, () -> GoogleIdentityClient.verifyIdentity(token, NONCE, verifier(true)));
    var normal = verifier(false);
    assertEquals(IdTokenVerifier.DEFAULT_TIME_SKEW_SECONDS, normal.getAcceptableTimeSkewSeconds());
    var identity = GoogleIdentityClient.verifyIdentity(token, NONCE, normal);
    assertEquals("google-test-subject", identity.subject());
    assertEquals("student@example.test", identity.email());
  }

  @Test void rejectsExpiredAndExcessivelyFutureTokens() throws Exception {
    String expired = signed(payload().setIssuedAtTimeSeconds(NOW - 4000).setExpirationTimeSeconds(NOW - 301), trustedKey);
    String future = signed(payload().setIssuedAtTimeSeconds(NOW + 301), trustedKey);
    assertThrows(SecurityException.class, () -> GoogleIdentityClient.verifyIdentity(expired, NONCE, verifier(false)));
    assertThrows(SecurityException.class, () -> GoogleIdentityClient.verifyIdentity(future, NONCE, verifier(false)));
  }

  @Test void rejectsWrongIssuerAndAudience() throws Exception {
    String issuer = signed(payload().setIssuer("https://untrusted.example.test"), trustedKey);
    String audience = signed(payload().setAudience("another-client"), trustedKey);
    assertThrows(SecurityException.class, () -> GoogleIdentityClient.verifyIdentity(issuer, NONCE, verifier(false)));
    assertThrows(SecurityException.class, () -> GoogleIdentityClient.verifyIdentity(audience, NONCE, verifier(false)));
  }

  @Test void rejectsSignatureFromAnUntrustedKey() throws Exception {
    String token = signed(payload(), otherKey);
    assertThrows(SecurityException.class, () -> GoogleIdentityClient.verifyIdentity(token, NONCE, verifier(false)));
  }

  @Test void rejectsMissingOrMismatchedNonce() throws Exception {
    String token = signed(payload(), trustedKey);
    assertThrows(SecurityException.class, () -> GoogleIdentityClient.verifyIdentity(token, null, verifier(false)));
    assertThrows(SecurityException.class, () -> GoogleIdentityClient.verifyIdentity(token, "another-session", verifier(false)));
    String missing = signed(payload().setNonce(null), trustedKey);
    assertThrows(SecurityException.class, () -> GoogleIdentityClient.verifyIdentity(missing, NONCE, verifier(false)));
  }

  @Test void rejectsUnverifiedEmailAndMissingIdentityClaims() throws Exception {
    for (var payload : new GoogleIdToken.Payload[] {payload().setEmailVerified(false),
        payload().setEmail(null), payload().setSubject(null)}) {
      String token = signed(payload, trustedKey);
      assertThrows(SecurityException.class, () -> GoogleIdentityClient.verifyIdentity(token, NONCE, verifier(false)));
    }
  }

  @Test void rejectsMissingMalformedTokensAndMissingTimeClaimsWithoutExposingInput() throws Exception {
    for (String raw : new String[] {null, "", "not-a-valid-token"}) {
      var error = assertThrows(SecurityException.class,
          () -> GoogleIdentityClient.verifyIdentity(raw, NONCE, verifier(false)));
      if (raw != null && !raw.isEmpty()) assertFalse(error.getMessage().contains(raw));
    }
    String token = signed(payload().setIssuedAtTimeSeconds(null), trustedKey);
    assertThrows(SecurityException.class, () -> GoogleIdentityClient.verifyIdentity(token, NONCE, verifier(false)));
  }

  /** Real payload and RSA signature checks with an in-memory test key; no Google network call. */
  private static final class OfflineVerifier extends GoogleIdTokenVerifier {
    private final PublicKey key;
    OfflineVerifier(Builder builder, PublicKey key) { super(builder); this.key = key; }
    @Override public boolean verify(GoogleIdToken token) throws GeneralSecurityException {
      return super.verifyPayload(token) && token.verifySignature(key);
    }
  }
}
