package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.http.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;

class GoogleCallbackStateTest {
  @Test
  void missingSessionAndMismatchedAndExpiredStateRejectBeforeTokenExchange() throws Exception {
    assertEquals(403, call(null, "state", null));
    var values = new HashMap<String, Object>();
    values.put("googleState", "expected");
    values.put("googleStartedAt", System.currentTimeMillis());
    assertEquals(403, call(values, "other", null));
    assertTrue(values.containsKey("googleState"));
    values.put("googleStartedAt", System.currentTimeMillis() - 600001);
    assertEquals(403, call(values, "expected", null));
  }

  @Test
  void validStateIsConsumedEvenWhenGoogleAuthorizationWasDenied() throws Exception {
    var values = new HashMap<String, Object>();
    values.put("googleState", "expected");
    values.put("googleNonce", "nonce");
    values.put("googleStartedAt", System.currentTimeMillis());
    values.put("googleInvitation", "invite");
    assertEquals(403, call(values, "expected", "access_denied"));
    assertTrue(values.isEmpty());
    assertEquals(403, call(values, "expected", null));
  }

  private static int call(Map<String, Object> values, String state, String error) throws Exception {
    HttpSession session =
        values == null
            ? null
            : (HttpSession)
                Proxy.newProxyInstance(
                    HttpSession.class.getClassLoader(),
                    new Class[] {HttpSession.class},
                    (p, m, a) ->
                        switch (m.getName()) {
                          case "getAttribute" -> values.get(a[0]);
                          case "removeAttribute" -> {
                            values.remove(a[0]);
                            yield null;
                          }
                          default -> throw new UnsupportedOperationException(m.getName());
                        });
    var req =
        (HttpServletRequest)
            Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class[] {HttpServletRequest.class},
                (p, m, a) ->
                    switch (m.getName()) {
                      case "getServletPath" -> "/auth/google/callback";
                      case "getSession" -> session;
                      case "getParameter" ->
                          switch ((String) a[0]) {
                            case "state" -> state;
                            case "error" -> error;
                            default -> null;
                          };
                      default -> throw new UnsupportedOperationException(m.getName());
                    });
    int[] status = {200};
    var res =
        (HttpServletResponse)
            Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class[] {HttpServletResponse.class},
                (p, m, a) -> {
                  if (m.getName().equals("sendError")) {
                    status[0] = (Integer) a[0];
                    return null;
                  }
                  throw new UnsupportedOperationException(m.getName());
                });
    new GoogleCallbackServlet().doGet(req, res);
    return status[0];
  }
}
