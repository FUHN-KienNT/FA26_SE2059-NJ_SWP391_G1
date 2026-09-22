package vn.edu.fpt.ams.dto;

import vn.edu.fpt.ams.model.*;

public record UserDTO(long id, String fullName, String email, UserRole role, AccountStatus status) {}
