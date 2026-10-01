package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.http.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.model.*;

class AuthSessionTest {
  @Test
  void establishmentInvalidatesOldSessionAndSetsTypedRoleAndFreshCsrf() {
    var old = new HashMap<String, Object>();
    old.put("csrf", "old-token");
    var fresh = new HashMap<String, Object>();
    boolean[] invalid = {false};
    var prior =
        (HttpSession)
            Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class[] {HttpSession.class},
                (p, m, a) -> {
                  if (m.getName().equals("invalidate")) {
                    invalid[0] = true;
                    old.clear();
                    return null;
                  }
                  throw new UnsupportedOperationException();
                });
    var next =
        (HttpSession)
            Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class[] {HttpSession.class},
                (p, m, a) ->
                    switch (m.getName()) {
                      case "getAttribute" -> fresh.get(a[0]);
                      case "setAttribute" -> {
                        fresh.put((String) a[0], a[1]);
                        yield null;
                      }
                      case "setMaxInactiveInterval" -> {
                        assertEquals(1800, a[0]);
                        yield null;
                      }
                      default -> throw new UnsupportedOperationException();
                    });
    var request =
        (HttpServletRequest)
            Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class[] {HttpServletRequest.class},
                (p, m, a) -> {
                  if (m.getName().equals("getSession")) return invalid[0] ? next : prior;
                  throw new UnsupportedOperationException();
                });
    AuthSession.establish(
        request,
        new UserAccount(
            1,
            "Name",
            "name@test.edu",
            UserRole.TEACHER,
            AccountStatus.ACTIVE,
            null,
            "hash",
            null,
            null,
            null));
    assertTrue(invalid[0]);
    assertEquals(1L, fresh.get("userId"));
    assertEquals(UserRole.TEACHER, fresh.get("role"));
    assertNotEquals("old-token", fresh.get("csrf"));
  }

  @Test
  void eachRoleHasItsOwnFixedLanding() {
    for (var role : UserRole.values())
      assertEquals("/" + role.name().toLowerCase() + "/dashboard", AuthSession.landing(role));
  }
}
