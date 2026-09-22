package vn.edu.fpt.ams.dto;

import vn.edu.fpt.ams.model.*;

public record UserCreateRequest(String fullName, String email, UserRole role) {}
