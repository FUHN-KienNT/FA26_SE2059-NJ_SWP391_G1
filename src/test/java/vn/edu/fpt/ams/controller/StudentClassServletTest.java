package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.StudentClassService;

class StudentClassServletTest {
    @Test void classAndFiltersUseOnlyTheAuthenticatedStudentAndForwardWithoutWriting() throws Exception {
        var h = new Exchange();
        h.params.putAll(Map.of("studentId", "99", "classId", "10", "q", "Câu & hỏi",
                "responseType", "essay", "status", "submitted", "page", "2"));
        h.servlet().doGet(h.request(), h.response());
        assertEquals(7, h.actor);
        assertEquals(10L, h.classId);
        assertEquals(new StudentClassFilter("Câu & hỏi", "ESSAY", "SUBMITTED", 2), h.filter);
        assertEquals("/WEB-INF/views/student/class-detail.jsp", h.forward);
        assertEquals("no-store", h.headers.get("Cache-Control"));
        assertTrue(h.attributes.containsKey("classPage"));
    }

    @Test void missingClassUsesTheServicesOwnClassSelection() throws Exception {
        var h = new Exchange();
        h.servlet().doGet(h.request(), h.response());
        assertEquals(7, h.actor);
        assertNull(h.classId);
        assertEquals(new StudentClassFilter("", "", "", 1), h.filter);
    }

    @Test void unauthenticatedInactiveAndWrongRolesCannotLoadClassData() throws Exception {
        for (String scenario : List.of("missing", "inactive", "teacher", "expert", "admin")) {
            var h = new Exchange();
            switch (scenario) {
                case "missing" -> h.hasSession = false;
                case "inactive" -> h.account = AccountStatus.INACTIVE;
                default -> h.role = UserRole.valueOf(scenario.toUpperCase(Locale.ROOT));
            }
            h.servlet().doGet(h.request(), h.response());
            assertEquals(403, h.status, scenario);
            assertEquals(0, h.actor);
            assertNull(h.forward);
        }
    }

    @Test void invalidClassAndFilterInputsDoNotReachTheService() throws Exception {
        for (var input : List.of(Map.of("classId", "0"), Map.of("classId", "no"),
                Map.of("classId", "99999999999999999999"), Map.of("page", "0"),
                Map.of("responseType", "FILE"), Map.of("status", "PUBLISHED"), Map.of("q", "x".repeat(201)))) {
            var h = new Exchange(); h.params.putAll(input);
            h.servlet().doGet(h.request(), h.response());
            assertEquals(400, h.status, input.toString());
            assertEquals(0, h.actor);
            assertNull(h.forward);
        }
    }

    @Test void inaccessibleClassReturnsNotFoundAndPostNeverChangesData() throws Exception {
        var h = new Exchange(); h.rejection = new NoSuchElementException("foreign class");
        h.servlet().doGet(h.request(), h.response());
        assertEquals(404, h.status); assertNull(h.forward);
        var post = new Exchange(); post.params.put("action", "start");
        post.servlet().doPost(post.request(), post.response());
        assertEquals(405, post.status); assertEquals(0, post.actor);
        assertEquals("GET, HEAD, OPTIONS", post.headers.get("Allow"));
    }

    private static class Exchange {
        final Map<String, String> params = new HashMap<>(), headers = new HashMap<>();
        final Map<String, Object> attributes = new HashMap<>();
        UserRole role = UserRole.STUDENT;
        AccountStatus account = AccountStatus.ACTIVE;
        boolean hasSession = true;
        long actor; Long classId; StudentClassFilter filter; String forward; int status = 200;
        RuntimeException rejection;

        StudentClassServlet servlet() {
            return new StudentClassServlet(new StudentClassService() {
                @Override public StudentClassPage load(long student, Long selected, StudentClassFilter inputs) {
                    actor = student; classId = selected; filter = inputs;
                    if (rejection != null) throw rejection;
                    return null;
                }
            });
        }
        HttpServletRequest request() {
            var session = (HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(),
                    new Class<?>[]{HttpSession.class}, (p, m, a) -> m.getName().equals("getAttribute")
                    ? switch ((String) a[0]) { case "userId" -> 7L; case "role" -> role; case "accountStatus" -> account; default -> null; } : null);
            return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                    new Class<?>[]{HttpServletRequest.class}, (p, m, a) -> switch (m.getName()) {
                        case "getSession" -> hasSession ? session : null;
                        case "getParameter" -> params.get(a[0]);
                        case "setAttribute" -> { attributes.put((String) a[0], a[1]); yield null; }
                        case "getRequestDispatcher" -> {
                            forward = (String) a[0];
                            yield Proxy.newProxyInstance(RequestDispatcher.class.getClassLoader(),
                                    new Class<?>[]{RequestDispatcher.class}, (d, method, args) -> null);
                        }
                        default -> null;
                    });
        }
        HttpServletResponse response() {
            return (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class}, (p, m, a) -> {
                        if (m.getName().equals("sendError") || m.getName().equals("setStatus")) status = (int) a[0];
                        if (m.getName().equals("setHeader")) headers.put((String) a[0], (String) a[1]);
                        return null;
                    });
        }
    }
}
