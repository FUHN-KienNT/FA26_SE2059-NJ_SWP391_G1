package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.http.*;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import vn.edu.fpt.ams.filter.AuthFilter;
import vn.edu.fpt.ams.model.*;

class AssignmentPreviewAccessTest {
    private String originalPreview;
    @BeforeEach void enablePreviewForTest() {
        originalPreview = System.getProperty("ams.assignment.preview");
        System.setProperty("ams.assignment.preview", "true");
    }
    @AfterEach void restorePreviewSetting() {
        if (originalPreview == null) System.clearProperty("ams.assignment.preview");
        else System.setProperty("ams.assignment.preview", originalPreview);
    }
    private static class Exchange {
        String path = "/teacher/assignments", method = "GET", address = "127.0.0.1";
        Map<String, Object> identity = new HashMap<>();
        int status = 200;
        String redirect;
        boolean continued;
        HttpSession session = (HttpSession) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpSession.class}, (p, m, a) -> {
                    if (m.getName().equals("getAttribute")) return identity.get(a[0]);
                    throw new AssertionError("Preview must not create or mutate a login session");
                });
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (p, m, a) -> switch (m.getName()) {
                    case "getServletPath" -> path;
                    case "getMethod" -> method;
                    case "getRemoteAddr" -> address;
                    case "getContextPath" -> "/ams";
                    case "getSession" -> { assertEquals(false, a[0]); yield session; }
                    default -> null;
                });
        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, (p, m, a) -> {
                    if (m.getName().equals("sendError")) status = (Integer) a[0];
                    if (m.getName().equals("sendRedirect")) { status = 302; redirect = (String) a[0]; }
                    return null;
                });
        void filter() throws Exception { new AuthFilter().doFilter(request, response, (a, b) -> continued = true); }
    }

    @Test void allowsAnonymousLocalAssignmentGetWithoutCreatingSession() throws Exception {
        for (String address : new String[]{"127.0.0.1", "::1", "0:0:0:0:0:0:0:1"}) {
            var e = new Exchange(); e.address = address; e.filter();
            assertTrue(e.continued); assertTrue(e.identity.isEmpty());
        }
    }
    @Test void doesNotBypassOtherModulesRemoteClientsOrPost() throws Exception {
        var remote = new Exchange(); remote.address = "192.168.1.20";
        var other = new Exchange(); other.path = "/teacher/enrollments";
        var post = new Exchange(); post.method = "POST";
        for (var e : new Exchange[]{remote, other, post}) {
            e.filter(); assertFalse(e.continued);
            assertEquals("GET".equals(e.method) ? 302 : 401, e.status);
            if (e.status == 302) assertEquals("/ams/auth/signin", e.redirect);
        }
    }
    @Test void existingStudentOrInactiveTeacherCannotBecomePreviewTeacher() throws Exception {
        var student = new Exchange(); student.identity.putAll(Map.of("userId", 8L,
                "role", UserRole.STUDENT, "accountStatus", AccountStatus.ACTIVE));
        student.filter(); assertFalse(student.continued); assertEquals(403, student.status);
        var inactive = new Exchange(); inactive.identity.putAll(Map.of("userId", 9L,
                "role", UserRole.TEACHER, "accountStatus", AccountStatus.INACTIVE));
        inactive.filter(); assertFalse(inactive.continued); assertEquals(302, inactive.status);
        assertEquals("/ams/auth/signin", inactive.redirect);
    }
    @Test void previewCanBeDisabledWithOneSetting() throws Exception {
        String previous = System.getProperty("ams.assignment.preview");
        try {
            System.setProperty("ams.assignment.preview", "false");
            var e = new Exchange(); e.filter(); assertFalse(e.continued); assertEquals(302, e.status);
            assertEquals("/ams/auth/signin", e.redirect);
        } finally {
            if (previous == null) System.clearProperty("ams.assignment.preview");
            else System.setProperty("ams.assignment.preview", previous);
        }
    }
}
