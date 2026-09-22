package vn.edu.fpt.ams.model;

/** Immutable data snapshot; persistence and validation are owned by DAO and Service. */
public record SystemSetting(String settingKey, String value, String description, boolean active, java.time.Instant updatedAt) {}
