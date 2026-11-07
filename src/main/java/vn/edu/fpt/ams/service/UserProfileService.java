package vn.edu.fpt.ams.service;

import vn.edu.fpt.ams.dao.UserDAO;
import vn.edu.fpt.ams.model.AccountStatus;
import vn.edu.fpt.ams.model.UserAccount;
import vn.edu.fpt.ams.util.AccountValidation;
import vn.edu.fpt.ams.util.DBConnection;
import vn.edu.fpt.ams.util.Jdbc;

/** Self-service profile access; email and role cannot be changed here. */
public class UserProfileService {
  private final Jdbc.Connections connections;
  private final UserDAO dao = new UserDAO();

  public UserProfileService() {
    this(DBConnection::getConnection);
  }

  public UserProfileService(Jdbc.Connections connections) {
    this.connections = connections;
  }

  private static void requireActive(UserAccount account) {
    if (account == null || account.accountStatus() != AccountStatus.ACTIVE) {
      throw new SecurityException("Cần tài khoản đang hoạt động.");
    }
  }

  public UserAccount profile(long actor) {
    return Jdbc.transaction(
        connections,
        connection -> {
          var account = dao.lockById(connection, actor);
          requireActive(account);
          return account;
        });
  }

  public void updateProfile(long actor, String name) {
    String validated = AccountValidation.name(name);
    Jdbc.transaction(
        connections,
        connection -> {
          requireActive(dao.lockById(connection, actor));
          dao.updateFullName(connection, actor, validated);
          return null;
        });
  }
}
