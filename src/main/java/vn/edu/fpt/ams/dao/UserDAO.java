package vn.edu.fpt.ams.dao;

import vn.edu.fpt.ams.model.*;
import java.sql.ResultSet;
import java.sql.SQLException;

/** JDBC operations for UserAccount; implementations are reserved for the next iteration. */
public class UserDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement transactional JDBC access according to SDS");
    }

public java.util.Optional<UserAccount> findById(long id) { throw pending(); }
public java.util.Optional<UserAccount> findByEmail(String email) { throw pending(); }
public java.util.List<UserAccount> search(String keyword, UserRole role, AccountStatus status, int page) { throw pending(); }
public long insert(UserAccount user) { throw pending(); }
public void update(UserAccount user) { throw pending(); }
public void updateStatus(long id, AccountStatus status) { throw pending(); }

static UserAccount mapUser(ResultSet rs) throws SQLException {
    return new UserAccount(rs.getLong("user_id"), rs.getString("full_name"),
            rs.getString("email"), UserRole.valueOf(rs.getString("role")),
            AccountStatus.valueOf(rs.getString("account_status")),
            rs.getString("google_subject"), rs.getString("password_hash"),
            ResultSetSupport.instant(rs, "created_at"),
            ResultSetSupport.instant(rs, "last_login_at"),
            ResultSetSupport.instant(rs, "updated_at"));
}

}
