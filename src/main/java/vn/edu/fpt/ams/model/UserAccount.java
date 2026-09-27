package vn.edu.fpt.ams.model;

/** Immutable data snapshot; persistence and validation are owned by DAO and Service. */
public record UserAccount(long userId, String fullName, String email, UserRole role,
                          AccountStatus accountStatus, String googleSubject,
                          String passwordHash, java.time.Instant createdAt,
                          java.time.Instant lastLoginAt, java.time.Instant updatedAt) {}
