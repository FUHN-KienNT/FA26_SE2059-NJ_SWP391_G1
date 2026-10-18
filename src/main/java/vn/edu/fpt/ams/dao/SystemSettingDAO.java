package vn.edu.fpt.ams.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import vn.edu.fpt.ams.model.SettingType;
import vn.edu.fpt.ams.model.SystemSetting;
import vn.edu.fpt.ams.util.Jdbc;

public class SystemSettingDAO {
  public List<SystemSetting> search(Connection c, String keyword, SettingType type, Boolean active)
      throws SQLException {
    String sql = "SELECT setting_id,setting_type,setting_value,display_name,priority,is_active"
        + " FROM system_settings WHERE (lower(display_name) LIKE ? OR lower(setting_value) LIKE ?)"
        + (type == null ? "" : " AND setting_type=?")
        + (active == null ? "" : " AND is_active=?")
        + " ORDER BY setting_type,priority,setting_value";
    var args = new ArrayList<Object>();
    String pattern = "%" + (keyword == null ? "" : keyword.strip().toLowerCase(java.util.Locale.ROOT)) + "%";
    args.add(pattern);
    args.add(pattern);
    if (type != null) args.add(type.name());
    if (active != null) args.add(active);
    try (var p = Jdbc.statement(c, sql, args.toArray()); var r = p.executeQuery()) {
      var result = new ArrayList<SystemSetting>();
      while (r.next()) result.add(map(r));
      return result;
    }
  }

  public List<SystemSetting> activeOptions(Connection c, SettingType type) throws SQLException {
    try (var p = Jdbc.statement(c,
        "SELECT setting_id,setting_type,setting_value,display_name,priority,is_active"
            + " FROM system_settings WHERE setting_type=? AND is_active=TRUE"
            + " ORDER BY priority,setting_value", type.name());
        var r = p.executeQuery()) {
      var result = new ArrayList<SystemSetting>();
      while (r.next()) result.add(map(r));
      return result;
    }
  }

  public Optional<SystemSetting> findById(Connection c, long id, boolean lock) throws SQLException {
    try (var p = Jdbc.statement(c,
        "SELECT setting_id,setting_type,setting_value,display_name,priority,is_active"
            + " FROM system_settings WHERE setting_id=?" + (lock ? " FOR UPDATE" : ""), id);
        var r = p.executeQuery()) {
      return r.next() ? Optional.of(map(r)) : Optional.empty();
    }
  }

  /** Lock every Assignment choice in one stable order before changing any of them. */
  public List<SystemSetting> lockAssignmentChoices(Connection c) throws SQLException {
    try (var p = Jdbc.statement(c,
        "SELECT setting_id,setting_type,setting_value,display_name,priority,is_active"
            + " FROM system_settings WHERE setting_type IN ('ASSIGNMENT_TYPE','RESPONSE_TYPE')"
            + " ORDER BY setting_type,setting_value,setting_id FOR UPDATE");
        var r = p.executeQuery()) {
      var result = new ArrayList<SystemSetting>();
      while (r.next()) result.add(map(r));
      return result;
    }
  }

  public void requireActiveForNew(Connection c, SettingType type, String value)
      throws SQLException {
    try (var p = Jdbc.statement(c,
        "SELECT is_active FROM system_settings WHERE setting_type=? AND setting_value=? FOR UPDATE",
        type.name(), value);
        var r = p.executeQuery()) {
      if (!r.next() || !r.getBoolean(1))
        throw new IllegalArgumentException("Lựa chọn này đã ngừng hoạt động và không thể dùng cho dữ liệu mới.");
    }
  }

  public void update(Connection c, long id, String name, int priority) throws SQLException {
    if (Jdbc.update(c,
        "UPDATE system_settings SET display_name=?,priority=? WHERE setting_id=?",
        name, priority, id) != 1)
      throw new IllegalArgumentException("Không tìm thấy thiết lập.");
  }

  public void updateStatus(Connection c, long id, boolean active) throws SQLException {
    if (Jdbc.update(c, "UPDATE system_settings SET is_active=? WHERE setting_id=?", active, id) != 1)
      throw new IllegalArgumentException("Không tìm thấy thiết lập.");
  }

  private static SystemSetting map(ResultSet r) throws SQLException {
    return new SystemSetting(r.getLong(1), SettingType.valueOf(r.getString(2)),
        r.getString(3), r.getString(4), r.getInt(5), r.getBoolean(6));
  }
}
