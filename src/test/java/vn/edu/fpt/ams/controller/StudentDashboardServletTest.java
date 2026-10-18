package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.*;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.StudentDashboardDTO;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.StudentDashboardService;

class StudentDashboardServletTest {
    @Test void forgedStudentIdCannotReplaceAuthenticatedIdentity() throws Exception {
        var harness = new Harness(UserRole.STUDENT, AccountStatus.ACTIVE);
        harness.servlet().doGet(harness.request(), harness.response());
        assertEquals(1L, harness.loadedStudent);
        assertEquals("Student One", ((StudentDashboardDTO) harness.attributes.get("dashboard")).fullName());
        assertEquals("/WEB-INF/views/student/dashboard.jsp", harness.view);
        assertEquals("no-store", harness.cacheControl);
        assertTrue(harness.forwarded);
    }

    @Test void wrongRoleInactiveAndMissingSessionNeverReachDashboardQueries() throws Exception {
        for (var role : List.of(UserRole.ADMIN, UserRole.EXPERT, UserRole.TEACHER)) {
            var harness = new Harness(role, AccountStatus.ACTIVE);
            harness.servlet().doGet(harness.request(), harness.response());
            assertEquals(403, harness.status);
            assertEquals(0, harness.loadedStudent);
        }
        for (var harness : List.of(new Harness(UserRole.STUDENT, AccountStatus.INACTIVE), new Harness(null, null))) {
            harness.servlet().doGet(harness.request(), harness.response());
            assertEquals(403, harness.status);
            assertEquals(0, harness.loadedStudent);
        }
    }

    private static final class Harness {
        final UserRole role;
        final AccountStatus accountStatus;
        final Map<String, Object> attributes = new HashMap<>();
        long loadedStudent;
        int status = 200;
        String view, cacheControl;
        boolean forwarded;
        Harness(UserRole role, AccountStatus status) { this.role = role; this.accountStatus = status; }
        StudentDashboardServlet servlet() {
            return new StudentDashboardServlet(new StudentDashboardService() {
                @Override public StudentDashboardDTO load(long id) {
                    loadedStudent = id;
                    return new StudentDashboardDTO("Student One", "one@test.edu", Instant.now(),
                            0, 0, 0, 0, 0, List.of(), List.of(), List.of());
                }
            });
        }
        HttpServletRequest request() {
            var session = (HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(),
                    new Class<?>[] {HttpSession.class}, (p, m, args) -> {
                        if (m.getName().equals("getAttribute")) return switch ((String) args[0]) {
                            case "userId" -> 1L;
                            case "role" -> role;
                            case "accountStatus" -> accountStatus;
                            default -> null;
                        };
                        throw new UnsupportedOperationException(m.getName());
                    });
            return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                    new Class<?>[] {HttpServletRequest.class}, (p, m, args) -> {
                        return switch (m.getName()) {
                            case "getSession" -> role == null ? null : session;
                            case "getParameter" -> "2"; // Forged studentId/userId must not be consulted.
                            case "setAttribute" -> { attributes.put((String) args[0], args[1]); yield null; }
                            case "getRequestDispatcher" -> {
                                view = (String) args[0];
                                yield Proxy.newProxyInstance(RequestDispatcher.class.getClassLoader(),
                                        new Class<?>[] {RequestDispatcher.class}, (d, method, params) -> {
                                            if (method.getName().equals("forward")) forwarded = true;
                                            return null;
                                        });
                            }
                            default -> throw new UnsupportedOperationException(m.getName());
                        };
                    });
        }
        HttpServletResponse response() {
            return (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
                    new Class<?>[] {HttpServletResponse.class}, (p, m, args) -> {
                        if (m.getName().equals("sendError")) status = (int) args[0];
                        else if (m.getName().equals("setHeader") && args[0].equals("Cache-Control")) cacheControl = (String) args[1];
                        return null;
                    });
        }
    }
}
