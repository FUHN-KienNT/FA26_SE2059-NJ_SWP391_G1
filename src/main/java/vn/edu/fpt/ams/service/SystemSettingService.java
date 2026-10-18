package vn.edu.fpt.ams.service;

import java.util.List;
import java.util.NoSuchElementException;
import vn.edu.fpt.ams.dao.SystemSettingDAO;
import vn.edu.fpt.ams.model.SettingType;
import vn.edu.fpt.ams.model.SystemSetting;
import vn.edu.fpt.ams.util.DBConnection;
import vn.edu.fpt.ams.util.Jdbc;

public class SystemSettingService {
  private final Jdbc.Connections connections;
  private final SystemSettingDAO dao = new SystemSettingDAO();

  public SystemSettingService() { this(DBConnection::getConnection); }

  public SystemSettingService(Jdbc.Connections connections) { this.connections = connections; }

  public List<SystemSetting> list(long actor, String keyword, SettingType type, Boolean active) {
    if (keyword != null && keyword.length() > 100)
      throw new IllegalArgumentException("Từ khóa không được quá 100 ký tự.");
    return Jdbc.transaction(connections, c -> {
      Access.admin(c, actor);
      return dao.search(c, keyword, type, active);
    });
  }

  public SystemSetting detail(long actor, long id) {
    if (id <= 0) throw new IllegalArgumentException("Mã thiết lập không hợp lệ.");
    return Jdbc.transaction(connections, c -> {
      Access.admin(c, actor);
      return dao.findById(c, id, false)
          .orElseThrow(() -> new NoSuchElementException("Không tìm thấy thiết lập."));
    });
  }

  public void update(long actor, long id, String name, int priority) {
    validateUpdate(id, name, priority);
    Jdbc.transaction(connections, c -> {
      Access.admin(c, actor);
      dao.findById(c, id, true)
          .orElseThrow(() -> new NoSuchElementException("Không tìm thấy thiết lập."));
      dao.update(c, id, name.strip(), priority);
      return null;
    });
  }

  /** Edit metadata and status together so a rejected status leaves both unchanged. */
  public void updateWithStatus(long actor, long id, String name, int priority,
      boolean active, boolean expectedActive) {
    validateUpdate(id, name, priority);
    Jdbc.transaction(connections, c -> {
      Access.admin(c, actor);
      var locked = lockForStatus(c, id);
      if (locked.setting().active() != expectedActive)
        throw new IllegalArgumentException("Trạng thái thiết lập đã thay đổi. Vui lòng tải lại trước khi lưu.");
      if (locked.setting().active() != active)
        applyStatus(c, id, active, locked.setting(), locked.choices());
      dao.update(c, id, name.strip(), priority);
      return null;
    });
  }

  public void setActive(long actor, long id, boolean active) {
    if (id <= 0) throw new IllegalArgumentException("Mã thiết lập không hợp lệ.");
    Jdbc.transaction(connections, c -> {
      Access.admin(c, actor);
      var locked = lockForStatus(c, id);
      if (locked.setting().active() == active)
        throw new IllegalArgumentException("Thiết lập đã ở trạng thái này.");
      applyStatus(c, id, active, locked.setting(), locked.choices());
      return null;
    });
  }

  private record LockedStatus(SystemSetting setting, List<SystemSetting> choices) {}

  private LockedStatus lockForStatus(java.sql.Connection c, long id) throws java.sql.SQLException {
    SystemSetting before = dao.findById(c, id, false)
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy thiết lập."));
    boolean assignmentChoice = before.type() == SettingType.ASSIGNMENT_TYPE
        || before.type() == SettingType.RESPONSE_TYPE;
    // Match AssignmentService's stable lock order for both Edit and status actions.
    List<SystemSetting> choices = assignmentChoice ? dao.lockAssignmentChoices(c) : List.of();
    SystemSetting setting = assignmentChoice
        ? choices.stream().filter(choice -> choice.settingId() == id).findFirst()
            .orElseThrow(() -> new NoSuchElementException("Không tìm thấy thiết lập."))
        : dao.findById(c, id, true)
            .orElseThrow(() -> new NoSuchElementException("Không tìm thấy thiết lập."));
    return new LockedStatus(setting, choices);
  }

  private void applyStatus(java.sql.Connection c, long id, boolean active,
      SystemSetting setting, List<SystemSetting> choices) throws java.sql.SQLException {
    boolean assignmentChoice = setting.type() == SettingType.ASSIGNMENT_TYPE
        || setting.type() == SettingType.RESPONSE_TYPE;
    if (assignmentChoice && !active) {
        List<SystemSetting> after = choices.stream()
            .map(choice -> choice.settingId() == id
                ? new SystemSetting(choice.settingId(), choice.type(), choice.value(),
                    choice.name(), choice.priority(), false)
                : choice)
            .toList();
        if (!AssignmentSettingRules.hasAvailableCombination(after))
          throw new IllegalArgumentException("Không thể vô hiệu hóa thiết lập này vì hệ thống phải duy trì ít nhất một tổ hợp loại bài tập và hình thức trả lời hợp lệ.");
    }
    dao.updateStatus(c, id, active);
  }

  private static void validateUpdate(long id, String name, int priority) {
    if (id <= 0) throw new IllegalArgumentException("Mã thiết lập không hợp lệ.");
    if (name == null || name.isBlank() || name.strip().length() > 100)
      throw new IllegalArgumentException("Tên thiết lập phải có từ 1 đến 100 ký tự.");
    if (priority < 1 || priority > 9999)
      throw new IllegalArgumentException("Độ ưu tiên phải từ 1 đến 9999.");
  }

  public List<SystemSetting> activeOptions(long actor, SettingType type) {
    return Jdbc.transaction(connections, c -> {
      Access.active(c, actor);
      return dao.activeOptions(c, type);
    });
  }
}
