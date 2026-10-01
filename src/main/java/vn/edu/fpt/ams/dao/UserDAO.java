package vn.edu.fpt.ams.dao;

import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** JDBC operations for UserAccount; implementations are reserved for the next iteration. */
public class UserDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement transactional JDBC access according to SDS");
    }

public Optional<UserAccount> findById(long id) {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapUser(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot load user", ex);
        }
    }
public Optional<UserAccount> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE lower(btrim(email)) = lower(btrim(?))";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapUser(rows)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot load user by email", ex);
        }
    }
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
