package vn.edu.fpt.ams.service;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.util.DBConnection;

/** Opt-in PostgreSQL checks using temporary tables or a dedicated test schema. */
class SystemSettingPostgresTest {
  private static final String REQUIRED_MESSAGE = "Không thể vô hiệu hóa thiết lập này vì hệ thống phải duy trì ít nhất một tổ hợp loại bài tập và hình thức trả lời hợp lệ.";

  @Test
  void transactionLocksAndRollbackWorkOnPostgres() throws Exception {
    Assumptions.assumeTrue(Boolean.getBoolean("assignment.db.tests"));
    try (Connection real = DBConnection.getConnection(); var s = real.createStatement()) {
      s.execute("CREATE TEMP TABLE users(user_id BIGINT PRIMARY KEY,full_name TEXT,email TEXT,role TEXT,account_status TEXT,google_subject TEXT,password_hash TEXT,created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,last_login_at TIMESTAMPTZ,updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP)");
      s.execute("CREATE TEMP TABLE system_settings(setting_id BIGINT PRIMARY KEY,setting_type TEXT,setting_value TEXT,display_name TEXT,priority INTEGER,is_active BOOLEAN)");
      s.execute("SET search_path TO pg_temp");
      s.execute("INSERT INTO users(user_id,full_name,email,role,account_status,password_hash) VALUES (1,'Admin','admin@test.edu','ADMIN','ACTIVE','fixture')");
      s.execute("INSERT INTO system_settings VALUES (1,'ASSIGNMENT_TYPE','REGULAR','Regular',1,TRUE),(2,'ASSIGNMENT_TYPE','PRACTICE','Practice',2,FALSE),(3,'RESPONSE_TYPE','ESSAY','Essay',1,TRUE),(4,'RESPONSE_TYPE','OBJECTIVE','Objective',2,TRUE)");
      Connection borrowed = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
          new Class<?>[] {Connection.class}, (proxy, method, args) -> {
            if ("close".equals(method.getName())) return null;
            try { return method.invoke(real, args); }
            catch (InvocationTargetException ex) { throw ex.getCause(); }
          });
      var service = new SystemSettingService(() -> borrowed);
      service.setActive(1, 4, false);
      assertFalse(service.detail(1, 4).active());
      var error = assertThrows(IllegalArgumentException.class,
          () -> service.setActive(1, 1, false));
      assertTrue(error.getMessage().contains("ít nhất một tổ hợp"));
      assertTrue(service.detail(1, 1).active());
      service.setActive(1, 4, true);
      assertTrue(service.detail(1, 4).active());
    }
  }

  @Test
  void twoAdminsCannotCommitConflictingDeactivationsOnPostgres() throws Exception {
    Assumptions.assumeTrue(Boolean.getBoolean("assignment.db.tests"));
    // This schema contains only test rows and is dropped even when an assertion fails.
    String schema = "ams_settings_test_" + UUID.randomUUID().toString().replace("-", "");
    try (Connection setup = DBConnection.getConnection(); var s = setup.createStatement()) {
      s.execute("CREATE SCHEMA " + schema);
    }
    try {
      try (Connection setup = DBConnection.getConnection(); var s = setup.createStatement()) {
        s.execute("CREATE TABLE " + schema + ".users(user_id BIGINT PRIMARY KEY,full_name TEXT,email TEXT,role TEXT,account_status TEXT,google_subject TEXT,password_hash TEXT,created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,last_login_at TIMESTAMPTZ,updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP)");
        s.execute("CREATE TABLE " + schema + ".system_settings(setting_id BIGINT PRIMARY KEY,setting_type TEXT,setting_value TEXT,display_name TEXT,priority INTEGER,is_active BOOLEAN)");
        s.execute("INSERT INTO " + schema + ".users(user_id,full_name,email,role,account_status,password_hash) VALUES (1,'Admin One','one@test.edu','ADMIN','ACTIVE','fixture'),(2,'Admin Two','two@test.edu','ADMIN','ACTIVE','fixture')");
        s.execute("INSERT INTO " + schema + ".system_settings VALUES (1,'ASSIGNMENT_TYPE','REGULAR','Regular',1,TRUE),(2,'ASSIGNMENT_TYPE','PRACTICE','Practice',2,FALSE),(3,'RESPONSE_TYPE','ESSAY','Essay',1,TRUE),(4,'RESPONSE_TYPE','OBJECTIVE','Objective',2,TRUE)");
      }
      var service = new SystemSettingService(() -> {
        Connection c = DBConnection.getConnection();
        try (var s = c.createStatement()) { s.execute("SET search_path TO " + schema); }
        catch (SQLException ex) { c.close(); throw ex; }
        return c;
      });
      var start = new CountDownLatch(1);
      var pool = Executors.newFixedThreadPool(2);
      try {
        var first = pool.submit(() -> deactivateAfter(start, service, 1, 3));
        var second = pool.submit(() -> deactivateAfter(start, service, 2, 4));
        start.countDown();
        assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
      } finally {
        pool.shutdownNow();
      }
      assertTrue(service.detail(1, 1).active());
      assertTrue(service.detail(1, 3).active() || service.detail(1, 4).active());
    } finally {
      try (Connection cleanup = DBConnection.getConnection(); var s = cleanup.createStatement()) {
        s.execute("DROP SCHEMA " + schema + " CASCADE");
      }
    }
  }

  private static boolean deactivateAfter(CountDownLatch start, SystemSettingService service,
      long actor, long settingId) throws InterruptedException {
    start.await();
    try {
      service.setActive(actor, settingId, false);
      return true;
    } catch (IllegalArgumentException rejected) {
      assertEquals(REQUIRED_MESSAGE, rejected.getMessage());
      return false;
    }
  }
}
