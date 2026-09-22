package vn.edu.fpt.ams.dao;

import vn.edu.fpt.ams.model.*;

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

}
