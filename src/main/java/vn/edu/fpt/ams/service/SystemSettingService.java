package vn.edu.fpt.ams.service;

import vn.edu.fpt.ams.dao.*;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.ValidationUtil;

/** Workflow boundary; unfinished writes fail closed instead of claiming success. */
public class SystemSettingService {
    private final SystemSettingDAO dao = new SystemSettingDAO();

    private static UnsupportedOperationException pending() { return new UnsupportedOperationException("TODO: implement SDS workflow and authorization"); }
    public java.util.List<SystemSetting> list(String keyword, Boolean active) { return dao.findAll(keyword, active); }
    public void update(String key, SettingDTO request, long adminId) { ValidationUtil.required(key, "setting key"); throw pending(); }
    public void disable(String key, long adminId) { throw pending(); }
    public java.util.List<SettingHistory> history(String key) { return dao.findHistory(key); }
}
