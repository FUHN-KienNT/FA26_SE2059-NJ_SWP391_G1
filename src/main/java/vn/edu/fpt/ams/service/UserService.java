package vn.edu.fpt.ams.service;

import vn.edu.fpt.ams.dao.*;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.ValidationUtil;

/** Workflow boundary; unfinished writes fail closed instead of claiming success. */
public class UserService {
    private final UserDAO dao = new UserDAO();

    private static UnsupportedOperationException pending() { return new UnsupportedOperationException("TODO: implement SDS workflow and authorization"); }
    public java.util.List<UserAccount> listUsers(String keyword, UserRole role, AccountStatus status, int page) { return dao.search(keyword, role, status, page); }
    public java.util.Optional<UserAccount> getUser(long id) { return dao.findById(id); }
    public long create(UserCreateRequest request) { ValidationUtil.required(request.email(), "email"); throw pending(); }
    public void update(UserAccount user) { throw pending(); }
    public void setStatus(long id, AccountStatus status) { throw pending(); }
}
