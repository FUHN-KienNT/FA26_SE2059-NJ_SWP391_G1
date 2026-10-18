package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.fpt.ams.controller.AssignmentServletTest.proxy;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.model.AcademicTerm;
import vn.edu.fpt.ams.service.MasterDataService;

class MasterDataServletTest {
  private static class Exchange {
    final Map<String, String> params = new HashMap<>(Map.of(
        "action", "status", "termId", "7", "csrf", "token"));
    final Map<String, Object> session = new HashMap<>(Map.of("userId", 1L, "csrf", "token"));
    final Map<String, Object> attributes = new HashMap<>();
    int status = 200;
    String forward;
    String redirect;
    final HttpSession httpSession = proxy(HttpSession.class, (method, args) -> switch (method) {
      case "getAttribute" -> session.get(args[0]);
      case "setAttribute" -> { session.put((String) args[0], args[1]); yield null; }
      default -> null;
    });
    final HttpServletRequest request = proxy(HttpServletRequest.class, (method, args) -> switch (method) {
      case "getSession" -> httpSession;
      case "getParameter" -> params.get(args[0]);
      case "getContextPath" -> "/ams";
      case "setAttribute" -> { attributes.put((String) args[0], args[1]); yield null; }
      case "getRequestDispatcher" -> {
        forward = (String) args[0];
        yield proxy(RequestDispatcher.class, (name, parameters) -> null);
      }
      default -> null;
    });
    final HttpServletResponse response = proxy(HttpServletResponse.class, (method, args) -> {
      if ("setStatus".equals(method) || "sendError".equals(method)) status = (Integer) args[0];
      if ("sendRedirect".equals(method)) { status = 302; redirect = (String) args[0]; }
      return null;
    });
  }

  private static class Service extends MasterDataService {
    int statusWrites;
    Boolean requestedStatus;

    @Override public List<AcademicTerm> listTerms(long actor, String keyword, Boolean active) {
      assertEquals(1L, actor);
      return List.of();
    }

    @Override public void setTermStatus(long actor, long termId, boolean active) {
      assertEquals(1L, actor);
      assertEquals(7L, termId);
      statusWrites++;
      requestedStatus = active;
    }
  }

  @Test
  void invalidStatusPostIsRejectedBeforeAnyWrite() throws Exception {
    for (String raw : new String[] {null, "", " ", "invalid", "FALSE", "0"}) {
      Exchange exchange = new Exchange();
      Service service = new Service();
      if (raw != null) exchange.params.put("active", raw);

      new MasterDataServlet(service).doPost(exchange.request, exchange.response);

      assertEquals(400, exchange.status, String.valueOf(raw));
      assertEquals("/WEB-INF/views/master-data/list.jsp", exchange.forward);
      assertEquals("Trạng thái học kỳ không hợp lệ.", exchange.attributes.get("error"));
      assertNull(exchange.redirect);
      assertEquals(0, service.statusWrites, String.valueOf(raw));
    }
  }

  @Test
  void validStatusPostPassesExactBooleanToService() throws Exception {
    for (String raw : List.of("true", "false")) {
      Exchange exchange = new Exchange();
      Service service = new Service();
      exchange.params.put("active", raw);

      new MasterDataServlet(service).doPost(exchange.request, exchange.response);

      assertEquals(1, service.statusWrites);
      assertEquals(Boolean.valueOf(raw), service.requestedStatus);
      assertEquals(302, exchange.status);
      assertEquals("/ams/admin/master-data?mode=view&id=7&saved=1", exchange.redirect);
    }
  }
}
