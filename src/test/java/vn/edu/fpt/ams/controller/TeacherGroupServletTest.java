package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.service.*;

/** Regression checks for the random action's class context, CSRF and form validation. */
class TeacherGroupServletTest {
    /** Random division uses classId and group size, then redirects to the class group list. */
    @Test void randomActionDoesNotRequireAnExistingGroup() throws Exception {
        var h = new Harness();
        h.params.put("membersPerGroup", "3");
        h.servlet().doPost(h.request(), h.response());
        assertEquals(3, h.size);
        assertEquals("/ams/teacher/groups?classId=100", h.redirect);
        assertEquals("Đã chia ngẫu nhiên thành 2 nhóm.", h.session.get("groupNotice"));
    }

    /** A decimal or overflow is returned to the group list without performing a division. */
    @Test void invalidGroupSizeRetainsTheForm() throws Exception {
        for (String value : List.of("2.5", "abc", "", "9999999999999999999")) {
            var h = new Harness();
            h.params.put("membersPerGroup", value);
            h.servlet().doPost(h.request(), h.response());
            assertEquals(0, h.size);
            assertEquals(400, h.status);
            assertEquals(value, h.attributes.get("membersPerGroup"));
            assertEquals("/WEB-INF/views/teacher/groups/list.jsp", h.view);
        }
    }

    /** Reject a forged request before invoking the random grouping service. */
    @Test void randomActionRequiresCsrf() throws Exception {
        var h = new Harness();
        h.params.putAll(Map.of("csrf", "wrong", "membersPerGroup", "3"));
        h.servlet().doPost(h.request(), h.response());
        assertEquals(403, h.status);
        assertEquals(0, h.size);
    }

    private static class Harness {
        final Map<String, String> params = new HashMap<>(Map.of(
                "csrf", "token", "action", "random", "classId", "100"));
        final Map<String, Object> session = new HashMap<>(Map.of("csrf", "token", "userId", 1L));
        final Map<String, Object> attributes = new HashMap<>();
        int size;
        int status = 200;
        String redirect;
        String view;

        /** Supply only the assigned class and a deterministic grouping service. */
        TeacherGroupServlet servlet() {
            return new TeacherGroupServlet(new TeacherGroupService() {
                @Override public int divideRandomly(long actor, long classId, int members) {
                    assertEquals(1, actor);
                    assertEquals(100, classId);
                    size = members;
                    return 2;
                }
                @Override public List<TeacherGroup> list(long actor, long classId) { return List.of(); }
                @Override public List<EnrollmentDTO> unassignedStudents(long actor, long classId) { return List.of(); }
            }, new TeacherDashboardService() {
                @Override public TeacherClassSummary requireAssignedClass(long actor, long classId) {
                    return new TeacherClassSummary(100, "C1", "Class One", "SWP", "Project",
                            "FALL2026", 30, 5, 0, 0, "ACTIVE");
                }
            });
        }

        /** Model only the request and session methods used by the form flow. */
        HttpServletRequest request() {
            var state = (HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(),
                    new Class<?>[] {HttpSession.class}, (p, m, a) -> switch (m.getName()) {
                        case "getAttribute" -> session.get(a[0]);
                        case "setAttribute" -> { session.put((String) a[0], a[1]); yield null; }
                        default -> null;
                    });
            return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                    new Class<?>[] {HttpServletRequest.class}, (p, m, a) -> switch (m.getName()) {
                        case "getSession" -> state;
                        case "getParameter" -> params.get(a[0]);
                        case "getContextPath" -> "/ams";
                        case "getServletPath" -> "/teacher/groups";
                        case "setAttribute" -> { attributes.put((String) a[0], a[1]); yield null; }
                        case "getRequestDispatcher" -> {
                            view = (String) a[0];
                            yield Proxy.newProxyInstance(RequestDispatcher.class.getClassLoader(),
                                    new Class<?>[] {RequestDispatcher.class}, (d, n, b) -> null);
                        }
                        default -> null;
                    });
        }

        /** Capture redirects and HTTP failures without starting a servlet container. */
        HttpServletResponse response() {
            return (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
                    new Class<?>[] {HttpServletResponse.class}, (p, m, a) -> {
                        if (m.getName().equals("sendRedirect")) redirect = (String) a[0];
                        if (m.getName().equals("sendError") || m.getName().equals("setStatus")) status = (int) a[0];
                        return null;
                    });
        }
    }
}
