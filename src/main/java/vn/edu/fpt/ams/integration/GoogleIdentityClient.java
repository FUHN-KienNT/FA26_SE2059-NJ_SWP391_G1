package vn.edu.fpt.ams.integration;

import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import java.util.List;
import java.util.logging.Logger;
import vn.edu.fpt.ams.util.AppConfig;

/** Never trust profile claims supplied directly by the browser. */
public class GoogleIdentityClient {
  private static final Logger LOG = Logger.getLogger(GoogleIdentityClient.class.getName());
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
    return verifyIdentity(tokens.getIdToken(), nonce, verifierBuilder(transport, json, client).build());
  }

  static GoogleIdTokenVerifier.Builder verifierBuilder(HttpTransport transport, JsonFactory json, String client) {
    // Retain Google's default clock tolerance; a minor local clock offset is not an invalid identity.
    return new GoogleIdTokenVerifier.Builder(transport, json).setAudience(List.of(client));
  }

  static Identity verifyIdentity(String rawToken, String nonce, GoogleIdTokenVerifier verifier)
      throws java.io.IOException, java.security.GeneralSecurityException {
    if (rawToken == null || rawToken.isBlank())
      throw rejected("ID_TOKEN_MISSING", "Google không trả về thông tin xác thực. Vui lòng đăng nhập lại.");
    GoogleIdToken token;
    try { token = GoogleIdToken.parse(verifier.getJsonFactory(), rawToken); }
    catch (java.io.IOException | IllegalArgumentException ex) {
      throw rejected("ID_TOKEN_PARSE", "Thông tin xác thực Google không hợp lệ. Vui lòng đăng nhập lại.");
    }
    // These checks only reject untrusted input. Identity is returned after signature verification.
    if (!token.verifyIssuer(verifier.getIssuers()))
      throw rejected("ISSUER", "Nguồn cấp thông tin xác thực Google không hợp lệ.");
    if (!token.verifyAudience(verifier.getAudience()))
      throw rejected("AUDIENCE", "Thông tin xác thực Google không dành cho ứng dụng AMS này.");
    var p = token.getPayload();
    if (p.getIssuedAtTimeSeconds() == null || p.getExpirationTimeSeconds() == null
        || !token.verifyTime(verifier.getClock().currentTimeMillis(), verifier.getAcceptableTimeSkewSeconds()))
      throw rejected("TIME", "Thông tin xác thực Google đã hết hạn hoặc giờ máy chưa chính xác. Vui lòng đăng nhập lại.");
    if (!verifier.verify(token))
      throw rejected("SIGNATURE", "Không xác minh được chữ ký Google. Vui lòng đăng nhập lại.");
    if (!Boolean.TRUE.equals(p.getEmailVerified()))
      throw rejected("EMAIL_VERIFIED", "Địa chỉ email chưa được Google xác minh.");
    if (nonce == null || nonce.isBlank() || !nonce.equals(p.getNonce()))
      throw rejected("NONCE", "Phiên đăng nhập Google không khớp. Vui lòng bắt đầu đăng nhập lại.");
    if (p.getSubject() == null || p.getSubject().isBlank() || p.getEmail() == null || p.getEmail().isBlank())
      throw rejected("IDENTITY_CLAIMS", "Google chưa cung cấp đủ thông tin tài khoản.");
    return new Identity(p.getSubject(), p.getEmail(), p.get("name") instanceof String name ? name : null);
  }

  private static SecurityException rejected(String stage, String message) {
    // Fixed stage labels only: never log the token, authorization code, nonce or profile claims.
    LOG.warning("Google sign-in rejected at " + stage);
    return new SecurityException(message);
  }
}
