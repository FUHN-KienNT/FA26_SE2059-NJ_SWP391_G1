package vn.edu.fpt.ams.integration;

import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.util.List;
import vn.edu.fpt.ams.util.AppConfig;

/** Never trust profile claims supplied directly by the browser. */
public class GoogleIdentityClient {
  public record Identity(String subject, String email, String name) {}

  public Identity exchange(String code, String nonce)
      throws java.io.IOException, java.security.GeneralSecurityException {
    String client = AppConfig.required("GOOGLE_CLIENT_ID", "google.clientId");
    var transport = new NetHttpTransport();
    var json = GsonFactory.getDefaultInstance();
    var tokens =
        new GoogleAuthorizationCodeTokenRequest(
                transport,
                json,
                client,
                AppConfig.required("GOOGLE_CLIENT_SECRET", "google.clientSecret"),
                code,
                AppConfig.required("GOOGLE_REDIRECT_URI", "google.redirectUri"))
            .execute();
    var verifier =
        new GoogleIdTokenVerifier.Builder(transport, json)
            .setAudience(List.of(client))
            .setAcceptableTimeSkewSeconds(0)
            .build();
    var token = verifier.verify(tokens.getIdToken());
    if (token == null
        || !Boolean.TRUE.equals(token.getPayload().getEmailVerified())
        || nonce == null
        || !nonce.equals(token.getPayload().get("nonce")))
      throw new SecurityException("Google identity verification failed");
    var p = token.getPayload();
    return new Identity(p.getSubject(), p.getEmail(), (String) p.get("name"));
  }
}
