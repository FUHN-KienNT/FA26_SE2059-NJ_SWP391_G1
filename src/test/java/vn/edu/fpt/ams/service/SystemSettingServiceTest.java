package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.model.SettingType;
import vn.edu.fpt.ams.model.SystemSetting;
import vn.edu.fpt.ams.util.Jdbc;

class SystemSettingServiceTest {
  private static final String REQUIRED_MESSAGE = "Không thể vô hiệu hóa thiết lập này vì hệ thống phải duy trì ít nhất một tổ hợp loại bài tập và hình thức trả lời hợp lệ.";
  private String url;
  private SystemSettingService service;

  @BeforeEach
  void setup() throws SQLException {
    url = "jdbc:h2:mem:setting_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    service = new SystemSettingService(() -> DriverManager.getConnection(url));
    try (Connection c = DriverManager.getConnection(url); var s = c.createStatement()) {
      s.execute("CREATE TABLE users(user_id BIGINT PRIMARY KEY,full_name VARCHAR(50),email VARCHAR(100),role VARCHAR(20),account_status VARCHAR(20),google_subject VARCHAR(255),password_hash VARCHAR(255),created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,last_login_at TIMESTAMP WITH TIME ZONE,updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)");
      s.execute("INSERT INTO users(user_id,full_name,email,role,account_status,password_hash) VALUES (1,'Admin One','admin1@test.edu','ADMIN','ACTIVE','fixture'),(2,'Admin Two','admin2@test.edu','ADMIN','ACTIVE','fixture')");
      s.execute("CREATE TABLE system_settings(setting_id BIGINT PRIMARY KEY,setting_type VARCHAR(30),setting_value VARCHAR(30),display_name VARCHAR(100),priority INTEGER,is_active BOOLEAN)");
      s.execute("INSERT INTO system_settings VALUES (1,'ASSIGNMENT_TYPE','REGULAR','Regular',1,TRUE),(2,'ASSIGNMENT_TYPE','PRACTICE','Practice',2,TRUE),(3,'RESPONSE_TYPE','ESSAY','Essay',1,TRUE),(4,'RESPONSE_TYPE','OBJECTIVE','Objective',2,TRUE),(5,'USER_ROLE','ADMIN','Admin',1,TRUE),(6,'USER_ROLE','EXPERT','Expert',2,TRUE)");
    }
  }

  private void active(long id, boolean value) throws SQLException {
    try (Connection c = DriverManager.getConnection(url);
        var p = c.prepareStatement("UPDATE system_settings SET is_active=? WHERE setting_id=?")) {
      p.setBoolean(1, value);
      p.setLong(2, id);
      p.executeUpdate();
    }
  }

  private boolean active(long id) {
    return service.detail(1, id).active();
  }

  private List<SystemSetting> choices() {
    return Jdbc.transaction(() -> DriverManager.getConnection(url), c -> {
      var result = new java.util.ArrayList<SystemSetting>();
      var dao = new vn.edu.fpt.ams.dao.SystemSettingDAO();
      result.addAll(dao.activeOptions(c, SettingType.ASSIGNMENT_TYPE));
      result.addAll(dao.activeOptions(c, SettingType.RESPONSE_TYPE));
      return result;
    });
  }

  @Test
  void allActiveAndEachSingleSupportedCombination() throws SQLException {
    assertTrue(AssignmentSettingRules.hasAvailableCombination(choices()));
    active(2, false);
    active(4, false);
    assertTrue(AssignmentSettingRules.hasAvailableCombination(choices())); // REGULAR + ESSAY
    active(4, true);
    active(3, false);
    assertTrue(AssignmentSettingRules.hasAvailableCombination(choices())); // REGULAR + OBJECTIVE
    active(2, true);
    active(1, false);
    assertTrue(AssignmentSettingRules.hasAvailableCombination(choices())); // PRACTICE + OBJECTIVE
    active(4, false);
    assertFalse(AssignmentSettingRules.hasAvailableCombination(choices()));
  }

  @Test
  void deactivationKeepsAValidPairAndRejectedWritesRollBack() throws SQLException {
    service.setActive(1, 2, false); // REGULAR still works.
    assertFalse(active(2));
    service.setActive(1, 4, false); // REGULAR + ESSAY still works.
    assertFalse(active(4));
    var error = assertThrows(IllegalArgumentException.class, () -> service.setActive(1, 1, false));
    assertEquals(REQUIRED_MESSAGE, error.getMessage());
    assertTrue(active(1));
    error = assertThrows(IllegalArgumentException.class, () -> service.setActive(1, 3, false));
    assertEquals(REQUIRED_MESSAGE, error.getMessage());
    assertTrue(active(3));
    assertFalse(active(2));
    assertFalse(active(4));
  }

  @Test
  void practiceObjectiveCanBeTheOnlyPairAndRolesAreIndependent() throws SQLException {
    active(3, false);
    service.setActive(1, 1, false);
    assertTrue(AssignmentSettingRules.hasAvailableCombination(choices()));
    assertThrows(IllegalArgumentException.class, () -> service.setActive(1, 2, false));
    assertThrows(IllegalArgumentException.class, () -> service.setActive(1, 4, false));
    assertTrue(active(2));
    assertTrue(active(4));
    service.setActive(1, 5, false);
    service.setActive(1, 6, false);
    assertFalse(active(5));
    assertFalse(active(6));
    assertTrue(AssignmentSettingRules.hasAvailableCombination(choices()));
  }

  @Test
  void activatingRepairsAnInvalidHistoricConfiguration() throws SQLException {
    active(1, false);
    active(2, false);
    active(3, false);
    active(4, false);
    service.setActive(1, 1, true);
    assertFalse(AssignmentSettingRules.hasAvailableCombination(choices()));
    service.setActive(1, 3, true);
    assertTrue(AssignmentSettingRules.hasAvailableCombination(choices()));
  }

  @Test
  void editStatusUsesSameRuleAndRollsBackMetadataWhenRejected() throws SQLException {
    service.setActive(1, 2, false);
    service.setActive(1, 4, false);
    var before = service.detail(1, 1);
    var error = assertThrows(IllegalArgumentException.class,
        () -> service.updateWithStatus(1, 1, "Changed", 77, false, true));
    assertEquals(REQUIRED_MESSAGE, error.getMessage());
    assertEquals(before, service.detail(1, 1));
    service.updateWithStatus(1, 1, "Changed", 77, true, true);
    assertEquals("Changed", service.detail(1, 1).name());
    assertEquals(77, service.detail(1, 1).priority());
    service.updateWithStatus(1, 5, "Quản trị viên", 9, false, true);
    assertFalse(active(5));
    assertEquals("Quản trị viên", service.detail(1, 5).name());
    assertThrows(IllegalArgumentException.class,
        () -> service.updateWithStatus(1, 5, "Stale edit", 20, true, true));
    assertEquals("Quản trị viên", service.detail(1, 5).name());
  }

  @Test
  void simultaneousAdminsCannotRemoveTheLastPair() throws Exception {
    active(2, false);
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var first = pool.submit(() -> deactivateAfter(start, 1, 3));
      var second = pool.submit(() -> deactivateAfter(start, 2, 4));
      start.countDown();
      boolean firstSucceeded = first.get(10, TimeUnit.SECONDS);
      boolean secondSucceeded = second.get(10, TimeUnit.SECONDS);
      assertNotEquals(firstSucceeded, secondSucceeded);
      assertTrue(AssignmentSettingRules.hasAvailableCombination(choices()));
      assertTrue(active(1));
    } finally {
      pool.shutdownNow();
    }
  }

  private boolean deactivateAfter(CountDownLatch start, long actor, long id) throws InterruptedException {
    start.await();
    try {
      service.setActive(actor, id, false);
      return true;
    } catch (IllegalArgumentException rejected) {
      assertEquals(REQUIRED_MESSAGE, rejected.getMessage());
      return false;
    }
  }
}
