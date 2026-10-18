package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.fpt.ams.controller.AssignmentServletTest.proxy;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.service.SystemSettingService;

class SettingsServletTest {
  @Test
  void directPostCannotDeactivateTheLastAssignmentCombination() throws Exception {
    String url = "jdbc:h2:mem:settings_post_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    try (Connection c = DriverManager.getConnection(url); var s = c.createStatement()) {
      s.execute("CREATE TABLE users(user_id BIGINT PRIMARY KEY,full_name VARCHAR(50),email VARCHAR(100),role VARCHAR(20),account_status VARCHAR(20),google_subject VARCHAR(255),password_hash VARCHAR(255),created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,last_login_at TIMESTAMP WITH TIME ZONE,updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)");
      s.execute("INSERT INTO users(user_id,full_name,email,role,account_status,password_hash) VALUES (1,'Admin','admin@test.edu','ADMIN','ACTIVE','fixture')");
      s.execute("CREATE TABLE system_settings(setting_id BIGINT PRIMARY KEY,setting_type VARCHAR(30),setting_value VARCHAR(30),display_name VARCHAR(100),priority INTEGER,is_active BOOLEAN)");
      s.execute("INSERT INTO system_settings VALUES (1,'ASSIGNMENT_TYPE','REGULAR','Regular',1,TRUE),(2,'ASSIGNMENT_TYPE','PRACTICE','Practice',2,FALSE),(3,'RESPONSE_TYPE','ESSAY','Essay',1,TRUE),(4,'RESPONSE_TYPE','OBJECTIVE','Objective',2,FALSE)");
    }
    var params = new HashMap<String, String>(Map.of("csrf", "token", "action", "status",
        "id", "1", "active", "false", "returnTo", "list"));
    var session = new HashMap<String, Object>(Map.of("csrf", "token", "userId", 1L));
    var attributes = new HashMap<String, Object>();
    var result = new int[] {200};
    var forwarded = new String[1];
    var httpSession = proxy(HttpSession.class, (method, args) -> switch (method) {
      case "getAttribute" -> session.get(args[0]);
      case "setAttribute" -> { session.put((String) args[0], args[1]); yield null; }
      default -> null;
    });
    var request = proxy(HttpServletRequest.class, (method, args) -> switch (method) {
      case "getParameter" -> params.get(args[0]);
      case "getSession" -> httpSession;
      case "setAttribute" -> { attributes.put((String) args[0], args[1]); yield null; }
      case "getRequestDispatcher" -> {
        forwarded[0] = (String) args[0];
        yield proxy(RequestDispatcher.class, (name, ignored) -> null);
      }
      default -> null;
    });
    var response = proxy(HttpServletResponse.class, (method, args) -> {
      if ("setStatus".equals(method) || "sendError".equals(method)) result[0] = (Integer) args[0];
      return null;
    });
    var service = new SystemSettingService(() -> DriverManager.getConnection(url));

    new SettingsServlet(service).doPost(request, response);

    assertEquals(400, result[0]);
    assertEquals("/WEB-INF/views/settings/list.jsp", forwarded[0]);
    assertEquals("Không thể vô hiệu hóa thiết lập này vì hệ thống phải duy trì ít nhất một tổ hợp loại bài tập và hình thức trả lời hợp lệ.", attributes.get("error"));
    assertTrue(service.detail(1, 1).active());

    params.put("action", "update");
    params.put("name", "Changed");
    params.put("priority", "8");
    params.put("currentActive", "true");
    result[0] = 200;
    new SettingsServlet(service).doPost(request, response);
    assertEquals(400, result[0]);
    assertEquals("Regular", service.detail(1, 1).name());
    assertEquals(1, service.detail(1, 1).priority());
    assertTrue(service.detail(1, 1).active());

    params.put("active", "unknown");
    result[0] = 200;
    new SettingsServlet(service).doPost(request, response);
    assertEquals(400, result[0]);
    assertEquals("Trạng thái thiết lập không hợp lệ.", attributes.get("error"));
    assertEquals("Regular", service.detail(1, 1).name());
  }
}
