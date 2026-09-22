package vn.edu.fpt.ams.model;

/** Immutable data snapshot; persistence and validation are owned by DAO and Service. */
public record SettingHistory(long historyId, String settingKey, String oldValue, String newValue, long changedBy, java.time.Instant changedAt) {}
