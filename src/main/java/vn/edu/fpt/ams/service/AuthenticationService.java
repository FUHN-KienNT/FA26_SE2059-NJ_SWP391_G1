package vn.edu.fpt.ams.service;

import vn.edu.fpt.ams.dao.UserDAO;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.*;

/** Local sign-in never provisions accounts or authenticates Students. */
public class AuthenticationService {
  private final Jdbc.Connections connections;
  private static final String FAILURE =
      "Sign-in could not be completed. Check your credentials or authentication method.";
  private static final String DUMMY_HASH = PasswordHash.hash(new PasswordGenerator().generate());

  public AuthenticationService() {
    this(DBConnection::getConnection);
  }

  public AuthenticationService(Jdbc.Connections connections) {
    this.connections = connections;
  }

  public UserAccount signIn(String email, String password) {
    if (password == null || password.isEmpty() || password.length() > 1024)
      throw new SecurityException(FAILURE);
    String normalized;
    try {
      normalized = AccountValidation.email(email);
    } catch (IllegalArgumentException e) {
      throw new SecurityException(FAILURE);
    }
    return Jdbc.transaction(
        connections,
        c -> {
          long id =
              Jdbc.scalar(
                  c, "SELECT user_id FROM users WHERE lower(trim(email))=? FOR UPDATE", normalized);
          UserAccount user = id == 0 ? null : new UserDAO().findById(c, id).orElseThrow();
          String hash =
              user == null || user.passwordHash() == null ? DUMMY_HASH : user.passwordHash();
          boolean valid = PasswordHash.verify(password, hash);
          if (!valid
              || user == null
              || user.role() == UserRole.STUDENT
              || user.accountStatus() != AccountStatus.ACTIVE) throw new SecurityException(FAILURE);
          Jdbc.update(c, "UPDATE users SET last_login_at=CURRENT_TIMESTAMP WHERE user_id=?", id);
          return new UserDAO().findById(c, id).orElseThrow();
        });
  }
}
