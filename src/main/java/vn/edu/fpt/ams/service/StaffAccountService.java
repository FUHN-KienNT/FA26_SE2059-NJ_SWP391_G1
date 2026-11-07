package vn.edu.fpt.ams.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import vn.edu.fpt.ams.dao.UserDAO;
import vn.edu.fpt.ams.integration.EmailService;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.*;

public class StaffAccountService {
  private final Jdbc.Connections connections;
  private final EmailService mail;
  private final UserDAO dao = new UserDAO();

  public StaffAccountService() {
    this(DBConnection::getConnection, new EmailService());
  }

  public StaffAccountService(Jdbc.Connections connections, EmailService mail) {
    this.connections = connections;
    this.mail = mail;
  }

  private static boolean staff(UserAccount account) {
    return account != null
        && account.role() != UserRole.STUDENT
        && account.accountStatus() == AccountStatus.ACTIVE;
  }

  private static void requireStaff(UserAccount account) {
    if (!staff(account)) throw new SecurityException("Cần tài khoản nhân viên đang hoạt động.");
  }

  public UserAccount profile(long actor) {
    return Jdbc.transaction(
        connections,
        connection -> {
          var account = dao.lockById(connection, actor);
          requireStaff(account);
          return account;
        });
  }

  public void updateProfile(long actor, String name) {
    String validated = AccountValidation.name(name);
    Jdbc.transaction(
        connections,
        connection -> {
          requireStaff(dao.lockById(connection, actor));
          dao.updateFullName(connection, actor, validated);
          return null;
        });
  }

  private static String newPassword(String password, String confirmation) {
    if (password == null
        || password.length() < 8
        || password.length() > 128
        || password.codePoints().anyMatch(Character::isWhitespace)
        || !password.matches("(?s).*[a-z].*")
        || !password.matches("(?s).*[A-Z].*")
        || !password.matches("(?s).*[0-9].*"))
      throw new IllegalArgumentException(
          "Mật khẩu mới cần 8–128 ký tự, có chữ hoa, chữ thường, chữ số và không chứa khoảng"
              + " trắng.");
    if (!password.equals(confirmation))
      throw new IllegalArgumentException("Xác nhận mật khẩu không khớp.");
    return password;
  }

  public void changePassword(long actor, String current, String password, String confirmation) {
    newPassword(password, confirmation);
    Jdbc.transaction(
        connections,
        connection -> {
          var account = dao.lockById(connection, actor);
          requireStaff(account);
          if (current == null
              || current.length() > 1024
              || !PasswordHash.verify(current, account.passwordHash()))
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng.");
          if (PasswordHash.verify(password, account.passwordHash()))
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại.");
          dao.updatePassword(connection, actor, PasswordHash.hash(password));
          dao.deletePasswordReset(connection, actor);
          return null;
        });
  }

  /** Deliberately returns the same UI outcome for missing, inactive and Student addresses. */
  public void requestReset(String rawEmail) {
    String email = AccountValidation.email(rawEmail), token = new PasswordGenerator().generate();
    var account =
        Jdbc.transaction(
            connections,
            connection -> {
              var lockedAccount = dao.lockByEmail(connection, email);
              if (!staff(lockedAccount)
                  || dao.hasRecentPasswordReset(
                      connection, lockedAccount.userId(), Instant.now().minusSeconds(60)))
                return null;
              dao.replacePasswordReset(
                  connection,
                  lockedAccount.userId(),
                  hash(token),
                  resetIdentity(lockedAccount),
                  Instant.now().plusSeconds(1800));
              return lockedAccount;
            });
    if (account == null) return;
    try {
      String base =
          AppConfig.get("AMS_BASE_URL", "app.baseUrl", "http://localhost:8080/ams")
              .replaceAll("/+$", "");
      mail.sendPasswordReset(
          email,
          "Bạn đã yêu cầu đặt lại mật khẩu AMS. Mở liên kết sau:\n"
              + base
              + "/auth/reset-password?token="
              + token
              + "\n"
              + "Liên kết có hiệu lực 30 phút và chỉ dùng một lần. Nếu không yêu cầu, hãy bỏ qua"
              + " email này.");
    } catch (RuntimeException ex) {
      Jdbc.transaction(
          connections,
          connection -> {
            dao.deleteMatchingPasswordReset(connection, account.userId(), hash(token));
            return null;
          });
      // Do not reveal account existence through delivery errors.
      java.util.logging.Logger.getLogger(getClass().getName())
          .warning("Staff reset email delivery failed");
    }
  }

  public void resetPassword(String token, String password, String confirmation) {
    if (token == null || !token.matches("[A-Za-z0-9_-]{32}")) throw invalidToken();
    newPassword(password, confirmation);
    Jdbc.transaction(
        connections,
        connection -> {
          long id = dao.findPasswordResetUserId(connection, hash(token));
          if (id == 0) throw invalidToken();
          var account = dao.lockById(connection, id);
          if (!staff(account)) throw invalidToken();
          if (PasswordHash.verify(password, account.passwordHash()))
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại.");
          if (!dao.consumePasswordReset(connection, id, hash(token), resetIdentity(account)))
            throw invalidToken();
          dao.updatePassword(connection, id, PasswordHash.hash(password));
          return null;
        });
  }

  private static IllegalArgumentException invalidToken() {
    return new IllegalArgumentException(
        "Liên kết đặt lại mật khẩu không hợp lệ, đã dùng hoặc hết hạn. Vui lòng yêu cầu liên kết"
            + " mới.");
  }

  private static String resetIdentity(UserAccount account) {
    return hash(account.email() + "\u0000" + account.passwordHash());
  }

  public static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
