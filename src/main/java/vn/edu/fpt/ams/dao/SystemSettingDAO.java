package vn.edu.fpt.ams.dao;

import vn.edu.fpt.ams.model.*;

/** JDBC operations for SystemSetting; implementations are reserved for the next iteration. */
public class SystemSettingDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement transactional JDBC access according to SDS");
    }

public java.util.List<SystemSetting> findAll(String keyword, Boolean active) { throw pending(); }
public java.util.Optional<SystemSetting> findByKey(String key) { throw pending(); }
public java.util.List<SettingHistory> findHistory(String key) { throw pending(); }
public void save(SystemSetting setting, long changedBy) { throw pending(); }

}
