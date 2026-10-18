package vn.edu.fpt.ams.service;
import vn.edu.fpt.ams.dao.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import vn.edu.fpt.ams.integration.EmailService;
import vn.edu.fpt.ams.util.*;

/** Email confirmation is independent of administrator account status. */
public class StudentVerificationService {
  private final Jdbc.Connections connections;
  private final EmailService mail;
  public StudentVerificationService() { this(DBConnection::getConnection, new EmailService()); }
  public StudentVerificationService(Jdbc.Connections connections, EmailService mail) {
    this.connections = connections; this.mail = mail;
  }
  public boolean pending(long id) {
    return Jdbc.transaction(connections, c -> StudentVerificationDAO.countPendingStudent(c, id) == 1);
  }
  public boolean send(long id) {
    String token = new PasswordGenerator().generate();
    String address = Jdbc.transaction(connections, c -> {
      String email = StudentVerificationDAO.findPendingEmailForUpdate(c, id);
      if (email == null) throw new IllegalArgumentException("Tài khoản không cần xác nhận hoặc đã bị vô hiệu hóa.");
        if (StudentVerificationDAO.countRecentMessages(c, id, java.time.Instant.now().minusSeconds(60)) > 0)
          return null;
        StudentVerificationDAO.deleteTokens(c, id);
        StudentVerificationDAO.insertToken(c, id, hash(token), java.time.Instant.now().plusSeconds(86400));
        return email;
    });
    if (address == null) throw new IllegalArgumentException("Vui lòng chờ 60 giây trước khi gửi lại email.");
    try {
      String base = AppConfig.get("AMS_BASE_URL", "app.baseUrl", "http://localhost:8080/ams").replaceAll("/+$", "");
      mail.sendNotification(address, "AMS - Xác nhận tài khoản sinh viên",
          "Bạn vừa đăng ký tài khoản AMS bằng Google. Bấm liên kết dưới đây để xác nhận email:\n"
          + base + "/auth/student-verification?token=" + token
          + "\nLiên kết có hiệu lực 24 giờ và chỉ dùng một lần. Sau khi xác nhận, hãy đăng nhập lại bằng Google."
          + "\nNếu không phải bạn đăng ký, vui lòng bỏ qua email này.");
      return true;
    } catch (RuntimeException e) {
      // Allow a retry after SMTP has been configured, without storing plaintext tokens.
      Jdbc.transaction(connections, c -> StudentVerificationDAO.deleteMatchingToken(c, id, hash(token)));
      return false;
    }
  }
  public void confirm(String token) {
    if (token == null || token.length() > 256 || token.isBlank()) throw invalid();
    Jdbc.transaction(connections, c -> {
      long id = StudentVerificationDAO.findTokenUserId(c, hash(token));
      if (id == 0) throw invalid();
      // Same lock order as resend: user first, then token.
      if (StudentVerificationDAO.lockPendingStudent(c, id) == 0) throw invalid();
      if (StudentVerificationDAO.deleteValidToken(c, id, hash(token)) != 1) throw invalid();
      StudentVerificationDAO.markEmailVerified(c, id);
      return null;
    });
  }
  private static IllegalArgumentException invalid() { return new IllegalArgumentException("Liên kết không hợp lệ, đã dùng hoặc hết hạn. Đăng nhập Google để gửi lại email."); }
  private static String hash(String token) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
    catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
}
