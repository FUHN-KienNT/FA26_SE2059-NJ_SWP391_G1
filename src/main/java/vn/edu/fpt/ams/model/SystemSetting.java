package vn.edu.fpt.ams.model;

public record SystemSetting(
    long settingId,
    SettingType type,
    String value,
    String name,
    int priority,
    boolean active) {}
