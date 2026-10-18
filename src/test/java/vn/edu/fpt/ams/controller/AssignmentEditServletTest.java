package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.fpt.ams.controller.AssignmentServletTest.proxy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dao.AssignmentDAO;
import vn.edu.fpt.ams.dto.AssignmentCreatePage;
import vn.edu.fpt.ams.dto.AssignmentEditPage;
import vn.edu.fpt.ams.dto.AssignmentForm;
import vn.edu.fpt.ams.model.AccountStatus;
import vn.edu.fpt.ams.model.UserRole;
import vn.edu.fpt.ams.service.AssignmentService;
import vn.edu.fpt.ams.util.AssignmentDraftValidator.ValidationException;
import vn.edu.fpt.ams.util.AssignmentEditConflictException;
import vn.edu.fpt.ams.util.WebSupport;

class AssignmentEditServletTest {
    private static final String FORM_VIEW = "/WEB-INF/views/assignments/form.jsp";
    private static final String REVISION = "a".repeat(64);
    private static final String QUESTIONS = "[{\"question_id\":1001,\"question_text\":\"Keep question\","
            + "\"maximum_points\":\"10.00\",\"options\":[{\"option_id\":2001,\"option_order\":1,"
            + "\"option_text\":\"Keep option\",\"is_correct\":true}]}]";

    private static class Exchange {
        final Map<String, String> params = new HashMap<>(Map.ofEntries(
                Map.entry("action", "update"), Map.entry("assignmentId", "101"),
                Map.entry("revision", REVISION), Map.entry("classId", "10"),
                Map.entry("csrf", "token"), Map.entry("templateId", "1"),
                Map.entry("title", " Keep my title "), Map.entry("instructions", "Keep full\ninstructions"),
                Map.entry("purpose", "REGULAR"), Map.entry("responseType", "OBJECTIVE"),
                Map.entry("maximumScore", "10.00"), Map.entry("scopeText", "Keep essay scope"),
                Map.entry("tasksJson", "[{\"title\":\"Keep task\",\"description\":\"Keep details\"}]"),
                Map.entry("questionsJson", QUESTIONS),
                Map.entry("availableFrom", "2026-10-12T08:10:20.123456789"),
                Map.entry("dueAt", "2026-10-13T09:15:30.987654321"),
                Map.entry("timeLimitMinutes", "60"), Map.entry("attemptLimit", "")));
        final Map<String, Object> session = new HashMap<>(Map.of(
                "userId", 1L, "role", UserRole.TEACHER,
                "accountStatus", AccountStatus.ACTIVE, "csrf", "token"));
        final Map<String, Object> attributes = new HashMap<>();
        int status = 200;
        String forward, redirect, errorMessage, cacheControl;
        boolean anonymous;
        final HttpSession httpSession = proxy(HttpSession.class, (method, args) -> switch (method) {
            case "getAttribute" -> session.get(args[0]);
            case "setAttribute" -> { session.put((String) args[0], args[1]); yield null; }
            case "removeAttribute" -> { session.remove(args[0]); yield null; }
            default -> null;
        });
        final HttpServletRequest request = proxy(HttpServletRequest.class, (method, args) -> switch (method) {
            case "getSession" -> anonymous ? null : httpSession;
            case "getParameter" -> params.get(args[0]);
            case "getContextPath" -> "/ams";
            case "setAttribute" -> { attributes.put((String) args[0], args[1]); yield null; }
            case "getAttribute" -> attributes.get(args[0]);
            case "getRequestDispatcher" -> {
                forward = (String) args[0];
                yield proxy(RequestDispatcher.class, (dispatchMethod, dispatchArgs) -> null);
            }
            default -> null;
        });
        final HttpServletResponse response = proxy(HttpServletResponse.class, (method, args) -> {
            if (method.equals("sendError") || method.equals("setStatus")) status = (Integer) args[0];
            if (method.equals("sendError") && args.length > 1) errorMessage = (String) args[1];
            if (method.equals("sendRedirect")) { redirect = (String) args[0]; status = 302; }
            if (method.equals("setHeader") && "Cache-Control".equals(args[0])) cacheControl = (String) args[1];
            return null;
        });

        void editGet() { params.put("mode", "edit"); }
    }

    private static class Service extends AssignmentService {
        int loads, updates;
        RuntimeException loadFailure, updateFailure;
        AssignmentForm submitted;
        AssignmentEditPage page;

        Service() {
            super(new AssignmentDAO(), () -> {
                throw new AssertionError("Servlet tests must not open a database connection");
            });
            var values = new HashMap<>(AssignmentForm.empty(10).values());
            values.putAll(Map.of("assignmentId", "101", "revision", REVISION,
                    "templateId", "1", "title", "Saved title", "maximumScore", "10.00",
                    "scopeText", "Saved essay scope", "tasksJson", "[{\"title\":\"Saved task\"}]"));
            page = new AssignmentEditPage(new AssignmentCreatePage(10, 100, "SE2059", "Class A",
                    "SWP391", "Project", List.of(new AssignmentCreatePage.TemplateOption(
                            1, "Current source (inactive)", "Saved instructions", BigDecimal.TEN))),
                    new AssignmentForm(values));
        }

        @Override public AssignmentEditPage editPage(long actor, long assignmentId) {
            loads++;
            assertEquals(1L, actor);
            assertEquals(101L, assignmentId);
            if (loadFailure != null) throw loadFailure;
            return page;
        }

        @Override public void updateAssignment(long actor, long assignmentId, AssignmentForm form) {
            updates++;
            assertEquals(1L, actor);
            assertEquals(101L, assignmentId);
            submitted = form;
            if (updateFailure != null) throw updateFailure;
        }
    }

    private AssignmentServlet servlet(Service service) {
        return new AssignmentServlet(service) {
            @Override public ServletContext getServletContext() {
                return proxy(ServletContext.class, (method, args) -> null);
            }
        };
    }

    private AssignmentForm assertRetainedForm(Exchange exchange, int status) {
        assertEquals(status, exchange.status);
        assertEquals(FORM_VIEW, exchange.forward);
        assertNull(exchange.redirect);
        assertEquals(Boolean.TRUE, exchange.attributes.get("assignmentEdit"));
        var kept = assertInstanceOf(AssignmentForm.class, exchange.attributes.get("assignmentForm"));
        for (String field : AssignmentForm.FIELDS)
            assertEquals(exchange.params.getOrDefault(field, ""), kept.value(field), "Retain " + field);
        assertFalse(exchange.session.containsKey("updatedAssignmentId"));
        return kept;
    }

    @Test void getLoadsSavedDraftAndConfirmationContextWithoutWriting() throws Exception {
        var exchange = new Exchange(); exchange.editGet(); exchange.session.remove("csrf");
        var service = new Service();
        servlet(service).doGet(exchange.request, exchange.response);
        assertEquals(200, exchange.status);
        assertEquals("no-store", exchange.cacheControl);
        assertEquals(FORM_VIEW, exchange.forward);
        assertEquals(1, service.loads); assertEquals(0, service.updates);
        assertEquals(Boolean.TRUE, exchange.attributes.get("assignmentEdit"));
        assertSame(service.page.context(), exchange.attributes.get("createPage"));
        assertSame(service.page.form(), exchange.attributes.get("assignmentForm"));
        assertEquals("ESSAY", exchange.attributes.get("savedResponseType"));
        assertEquals(Boolean.TRUE, exchange.attributes.get("savedEssayContent"));
        assertEquals(Boolean.FALSE, exchange.attributes.get("savedObjectiveContent"));
        assertInstanceOf(String.class, exchange.session.get("csrf"));
        assertNull(exchange.redirect);
    }

    @Test void getExposesSavedObjectiveContentForTypeChangeConfirmation() throws Exception {
        var exchange = new Exchange(); exchange.editGet(); var service = new Service();
        var values = new HashMap<>(service.page.form().values());
        values.putAll(Map.of("responseType", "OBJECTIVE", "scopeText", "", "tasksJson", "[]", "questionsJson", QUESTIONS));
        service.page = new AssignmentEditPage(service.page.context(), new AssignmentForm(values));
        servlet(service).doGet(exchange.request, exchange.response);
        assertEquals(200, exchange.status);
        assertEquals("OBJECTIVE", exchange.attributes.get("savedResponseType"));
        assertEquals(Boolean.FALSE, exchange.attributes.get("savedEssayContent"));
        assertEquals(Boolean.TRUE, exchange.attributes.get("savedObjectiveContent"));
        assertEquals(0, service.updates);
    }

    @Test void successfulPostUpdatesSameAssignmentAndRedirectsWithOneTimeNotice() throws Exception {
        for (long createdId : List.of(101L, 202L)) {
            var exchange = new Exchange(); exchange.session.put("createdAssignmentId", createdId);
            var service = new Service(); servlet(service).doPost(exchange.request, exchange.response);
            assertEquals(1, service.loads); assertEquals(1, service.updates);
            assertEquals(302, exchange.status); assertEquals("no-store", exchange.cacheControl);
            assertEquals("/ams/teacher/assignments?mode=view&classId=10&assignmentId=101", exchange.redirect);
            assertEquals(101L, exchange.session.get("updatedAssignmentId"));
            if (createdId == 101L) assertFalse(exchange.session.containsKey("createdAssignmentId"));
            else assertEquals(createdId, exchange.session.get("createdAssignmentId"));
            for (String field : AssignmentForm.FIELDS)
                assertEquals(exchange.params.get(field), service.submitted.value(field));
            assertNull(exchange.forward);
        }
    }

    @Test void editGetAndPostRequireAnActiveTeacherSessionBeforeLoadingData() throws Exception {
        for (boolean post : List.of(false, true)) {
            for (String scenario : List.of("anonymous", "missingId", "invalidIdType", "student", "inactive")) {
                var exchange = new Exchange(); exchange.editGet(); var service = new Service();
                switch (scenario) {
                    case "anonymous" -> exchange.anonymous = true;
                    case "missingId" -> exchange.session.remove("userId");
                    case "invalidIdType" -> exchange.session.put("userId", "1");
                    case "student" -> exchange.session.put("role", UserRole.STUDENT);
                    case "inactive" -> exchange.session.put("accountStatus", AccountStatus.INACTIVE);
                    default -> throw new AssertionError(scenario);
                }
                if (post) servlet(service).doPost(exchange.request, exchange.response);
                else servlet(service).doGet(exchange.request, exchange.response);
                assertEquals(scenario.equals("student") ? 403 : 401, exchange.status);
                assertEquals(0, service.loads); assertEquals(0, service.updates);
                assertNull(exchange.forward); assertNull(exchange.redirect); assertTrue(exchange.attributes.isEmpty());
            }
        }
    }

    @Test void editGetAndPostRejectInvalidAssignmentIdsWithoutLoadingData() throws Exception {
        for (boolean post : List.of(false, true)) {
            for (String id : new String[]{null, "", "oops", "0", "-1", "999999999999999999999"}) {
                var exchange = new Exchange(); exchange.editGet(); var service = new Service();
                if (id == null) exchange.params.remove("assignmentId");
                else exchange.params.put("assignmentId", id);
                if (post) servlet(service).doPost(exchange.request, exchange.response);
                else servlet(service).doGet(exchange.request, exchange.response);
                assertEquals(400, exchange.status);
                assertEquals(0, service.loads); assertEquals(0, service.updates);
                assertNull(exchange.forward); assertNull(exchange.redirect);
                assertFalse(exchange.attributes.containsKey("createPage"));
            }
        }
    }

    @Test void staleCsrfRetainsInputAndRequiresExplicitRetryWithCurrentToken() throws Exception {
        var exchange = new Exchange(); var service = new Service();
        exchange.session.put("csrf", "current-session-token"); exchange.params.put("csrf", "stale-form-token");
        var controller = servlet(service); controller.doPost(exchange.request, exchange.response);
        assertRetainedForm(exchange, 403);
        assertEquals(1, service.loads); assertEquals(0, service.updates);
        assertSame(service.page.context(), exchange.attributes.get("createPage"));
        assertEquals("ESSAY", exchange.attributes.get("savedResponseType"));
        assertNotNull(exchange.attributes.get("createError"));
        assertEquals("current-session-token", WebSupport.csrf(exchange.request));
        exchange.params.put("csrf", WebSupport.csrf(exchange.request)); exchange.forward = null;
        controller.doPost(exchange.request, exchange.response);
        assertEquals(2, service.loads); assertEquals(1, service.updates);
        assertEquals(302, exchange.status);
        assertEquals("/ams/teacher/assignments?mode=view&classId=10&assignmentId=101", exchange.redirect);
        assertEquals(101L, exchange.session.get("updatedAssignmentId")); assertNull(exchange.forward);
    }

    @Test void missingCsrfRefreshesTokenWithoutWriting() throws Exception {
        var exchange = new Exchange(); var service = new Service();
        exchange.params.remove("csrf"); exchange.session.remove("csrf");
        servlet(service).doPost(exchange.request, exchange.response);
        assertRetainedForm(exchange, 403);
        assertEquals(1, service.loads); assertEquals(0, service.updates);
        String refreshed = assertInstanceOf(String.class, exchange.session.get("csrf"));
        assertFalse(refreshed.isBlank()); assertEquals(refreshed, WebSupport.csrf(exchange.request));
    }

    @Test void validationFailureRetainsAllInputAndOriginalSavedContentContext() throws Exception {
        var exchange = new Exchange(); var service = new Service();
        var errors = Map.of("title", "Invalid title", "revision", "Reopen Edit before saving");
        service.updateFailure = new ValidationException(errors);
        servlet(service).doPost(exchange.request, exchange.response);
        assertRetainedForm(exchange, 400);
        assertEquals(1, service.loads); assertEquals(1, service.updates);
        assertEquals(errors, exchange.attributes.get("formErrors"));
        assertEquals("ESSAY", exchange.attributes.get("savedResponseType"));
        assertEquals(Boolean.TRUE, exchange.attributes.get("savedEssayContent"));
        assertEquals(Boolean.FALSE, exchange.attributes.get("savedObjectiveContent"));
        assertFalse(exchange.attributes.containsKey("editConflict"));
    }

    @Test void updateConflictRetainsInputAndRevisionAndRequiresReload() throws Exception {
        var exchange = new Exchange(); var service = new Service();
        exchange.params.put("revision", "b".repeat(64));
        String message = "This draft changed. Reload the latest draft before saving.";
        service.updateFailure = new AssignmentEditConflictException(message);
        servlet(service).doPost(exchange.request, exchange.response);
        var kept = assertRetainedForm(exchange, 409);
        assertEquals(1, service.loads); assertEquals(1, service.updates);
        assertEquals("b".repeat(64), kept.value("revision"));
        assertEquals(Boolean.TRUE, exchange.attributes.get("editConflict"));
        assertEquals(message, exchange.attributes.get("createError"));
        assertSame(service.page.context(), exchange.attributes.get("createPage"));
    }

    @Test void missingOrForeignAssignmentReturnsSameNotFoundResponseOnGetAndPost() throws Exception {
        for (boolean post : List.of(false, true)) {
            for (String message : List.of("Missing private assignment", "Another teacher owns private assignment")) {
                var exchange = new Exchange(); exchange.editGet(); var service = new Service();
                service.loadFailure = new NoSuchElementException(message);
                if (post) servlet(service).doPost(exchange.request, exchange.response);
                else servlet(service).doGet(exchange.request, exchange.response);
                assertEquals(404, exchange.status);
                assertEquals("Không tìm thấy bài tập hoặc bạn không có quyền truy cập.", exchange.errorMessage);
                assertEquals(1, service.loads); assertEquals(0, service.updates);
                assertNull(exchange.forward); assertNull(exchange.redirect);
                assertFalse(exchange.attributes.containsKey("createPage"));
            }
        }
    }

    @Test void assignmentRemovedDuringUpdateReturnsNotFoundWithoutRedirect() throws Exception {
        var exchange = new Exchange(); var service = new Service();
        service.updateFailure = new NoSuchElementException("private ownership details");
        servlet(service).doPost(exchange.request, exchange.response);
        assertEquals(404, exchange.status);
        assertEquals("Không tìm thấy bài tập hoặc bạn không có quyền truy cập.", exchange.errorMessage);
        assertEquals(1, service.updates); assertNull(exchange.forward); assertNull(exchange.redirect);
        assertFalse(exchange.session.containsKey("updatedAssignmentId"));
    }

    @Test void nonDraftGetReturnsConflictWithoutShowingEditForm() throws Exception {
        var exchange = new Exchange(); exchange.editGet(); var service = new Service();
        service.loadFailure = new AssignmentEditConflictException("Only Draft assignments can be edited.");
        servlet(service).doGet(exchange.request, exchange.response);
        assertEquals(409, exchange.status);
        assertEquals(1, service.loads); assertEquals(0, service.updates);
        assertNull(exchange.forward); assertNull(exchange.redirect);
        assertFalse(exchange.attributes.containsKey("createPage"));
        assertFalse(exchange.attributes.containsKey("assignmentForm"));
    }
}
