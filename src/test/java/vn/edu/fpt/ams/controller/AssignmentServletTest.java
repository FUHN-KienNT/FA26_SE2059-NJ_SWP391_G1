package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.math.BigDecimal;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.AssignmentService;

class AssignmentServletTest {
    private static class Exchange {
        Map<String,String> params = new HashMap<>(Map.of("classId","10"));
        Map<String,Object> session = new HashMap<>(Map.<String,Object>of("userId",1L,"role",UserRole.TEACHER,"accountStatus",AccountStatus.ACTIVE));
        Map<String,Object> attributes = new HashMap<>();
        String forward, redirect;
        int status = 200;
        boolean noSession;
        boolean localPreview;
        String cacheControl;
        HttpSession httpSession = proxy(HttpSession.class, (method,args) -> session.get((String)args[0]));
        RequestDispatcher dispatcher = proxy(RequestDispatcher.class, (method,args) -> null);
        HttpServletRequest request = proxy(HttpServletRequest.class, (method,args) -> switch(method) {
            case "getMethod" -> "GET";
            case "getContextPath" -> "/ams";
            case "getServletPath" -> "/teacher/assignments";
            case "getRemoteAddr" -> localPreview ? "127.0.0.1" : "192.168.1.20";
            case "getSession" -> noSession ? null : httpSession;
            case "getParameter" -> params.get((String)args[0]);
            case "setAttribute" -> { attributes.put((String)args[0],args[1]); yield null; }
            case "getRequestDispatcher" -> { forward=(String)args[0]; yield dispatcher; }
            default -> null;
        });
        HttpServletResponse response = proxy(HttpServletResponse.class, (method,args) -> {
            if (method.equals("sendRedirect")) { status=302;redirect=(String)args[0]; }
            if (method.equals("sendError") || method.equals("setStatus")) status=(Integer)args[0];
            if (method.equals("setHeader") && args[0].equals("Cache-Control")) cacheControl=(String)args[1];
            return null;
        });
    }
    @FunctionalInterface interface Call { Object invoke(String method,Object[] args); }
    @SuppressWarnings("unchecked") static <T> T proxy(Class<T> type,Call call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->call.invoke(m.getName(),a));
    }
    private AssignmentServlet servlet(RuntimeException failure) {
        var service = new AssignmentService() {
            @Override public long previewTeacherId() { return 7L; }
            @Override public AssignmentListPage listPage(long teacherId,Long classId,AssignmentListFilter filter) {
                if(failure!=null) throw failure;
                return new AssignmentListPage(List.of(new AssignmentClassOption(10,"SE2059","Project")),
                        classId==null?10L:classId,filter,List.of());
            }
            @Override public AssignmentDetailPage detailPage(long teacherId, long assignmentId) {
                if (failure != null) throw failure;
                assertEquals(1L, teacherId);
                assertEquals(101L, assignmentId);
                var assignment = new Assignment(101, 10, 1, "UML assignment", AssignmentPurpose.REGULAR,
                        ResponseType.ESSAY, "Full instructions", "UML", "[]", BigDecimal.TEN,
                        AssignmentStatus.DRAFT, null, null, null, null, null, null);
                return new AssignmentDetailPage(assignment, "SE2059", "Project", "SWP391", "Software project",
                        "FA26", "Report template", List.of());
            }
        };
        return new AssignmentServlet(service) {
            @Override public ServletContext getServletContext() {
                return proxy(ServletContext.class,(method,args)->null);
            }
        };
    }
    @Test void missingClassRedirectsToDashboardInsteadOfSelectingAnArbitraryClass() throws Exception {
        for (String classId : new String[]{null, "", "  "}) {
            var e=new Exchange();
            if(classId==null)e.params.remove("classId");else e.params.put("classId",classId);
            servlet(new IllegalStateException("List must not load without a class")).doGet(e.request,e.response);
            assertEquals(302,e.status);assertEquals("/ams/teacher/dashboard",e.redirect);
            assertNull(e.forward);assertFalse(e.attributes.containsKey("assignmentPage"));
        }
    }
    @Test void passesFiltersAndClassToListAndDisablesCaching() throws Exception {
        var e=new Exchange(); e.params.putAll(Map.of("classId","10","search","Quiz","purpose","PRACTICE"));
        servlet(null).doGet(e.request,e.response);
        assertEquals(200,e.status); assertEquals("no-store",e.cacheControl);
        assertEquals("/WEB-INF/views/assignments/list.jsp",e.forward);
        var page=(AssignmentListPage)e.attributes.get("assignmentPage");
        assertEquals("Quiz",page.filter().search()); assertEquals(AssignmentPurpose.PRACTICE,page.filter().purpose());
    }
    @Test void rejectsAnonymousAndWrongRole() throws Exception {
        var anonymous=new Exchange(); anonymous.noSession=true;
        servlet(null).doGet(anonymous.request,anonymous.response); assertEquals(401,anonymous.status); assertNull(anonymous.forward);
        var student=new Exchange(); student.session.put("role",UserRole.STUDENT);
        servlet(null).doGet(student.request,student.response); assertEquals(403,student.status); assertNull(student.forward);
    }
    @Test void invalidClassAndEnumsReturnBadRequest() throws Exception {
        for (var params : List.of(Map.of("classId","oops"),Map.of("classId","99999999999999999999"),
                Map.of("status","DELETED"),Map.of("purpose","SQL"),Map.of("responseType","FILE"),Map.of("mode","unknown"))) {
            var e=new Exchange(); e.params.putAll(params); servlet(null).doGet(e.request,e.response);
            assertEquals(400,e.status); assertNull(e.forward);
        }
    }
    @Test void unauthorizedClassDoesNotForwardData() throws Exception {
        var e=new Exchange(); servlet(new SecurityException("Forbidden")).doGet(e.request,e.response);
        assertEquals(403,e.status); assertNull(e.forward); assertFalse(e.attributes.containsKey("assignmentPage"));
    }
    @Test void databaseFailureRendersRecoverableErrorWithoutSqlDetails() throws Exception {
        var e=new Exchange(); servlet(new IllegalStateException("private SQL error")).doGet(e.request,e.response);
        assertEquals(503,e.status); assertEquals("/WEB-INF/views/assignments/list.jsp",e.forward);
        assertFalse(e.attributes.get("assignmentListError").toString().contains("private"));
    }
    @Test void listDoesNotEnableMutations() throws Exception {
        var e=new Exchange(); servlet(null).doPost(e.request,e.response); assertEquals(501,e.status);
    }
    @Test void viewLoadsSelectedAssignmentAndKeepsReturnFiltersWithoutTrustingBrowserClass() throws Exception {
        var e = new Exchange();
        e.params.putAll(Map.of("mode", "view", "assignmentId", "101", "classId", "999",
                "search", " UML ", "responseType", "ESSAY", "status", "DRAFT"));
        servlet(null).doGet(e.request, e.response);
        assertEquals(200, e.status);
        assertEquals("no-store", e.cacheControl);
        assertEquals("/WEB-INF/views/assignments/detail.jsp", e.forward);
        var detail = (AssignmentDetailPage) e.attributes.get("assignmentDetail");
        assertEquals(10L, detail.assignment().classId());
        assertEquals("Full instructions", detail.assignment().instructions());
        var filters = (AssignmentListFilter) e.attributes.get("assignmentFilters");
        assertEquals("UML", filters.search());
        assertEquals(ResponseType.ESSAY, filters.responseType());
    }
    @Test void viewRejectsMissingMalformedAndNonpositiveIds() throws Exception {
        for (String id : new String[]{null, "", "oops", "0", "-1", "99999999999999999999999"}) {
            var e = new Exchange(); e.params.put("mode", "view");
            if (id != null) e.params.put("assignmentId", id);
            servlet(null).doGet(e.request, e.response);
            assertEquals(400, e.status);
            assertNull(e.forward);
            assertFalse(e.attributes.containsKey("assignmentDetail"));
        }
    }
    @Test void missingOrInaccessibleAssignmentDoesNotExposeContent() throws Exception {
        var e = new Exchange(); e.params.putAll(Map.of("mode", "view", "assignmentId", "101"));
        servlet(new NoSuchElementException("private assignment")).doGet(e.request, e.response);
        assertEquals(404, e.status); assertNull(e.forward);
        assertFalse(e.attributes.containsKey("assignmentDetail"));
    }
    @Test void viewDatabaseFailureHasRecoverableErrorWithoutSqlDetails() throws Exception {
        var e = new Exchange(); e.params.putAll(Map.of("mode", "view", "assignmentId", "101"));
        servlet(new IllegalStateException("private SQL error")).doGet(e.request, e.response);
        assertEquals(503, e.status);
        assertEquals("/WEB-INF/views/assignments/detail.jsp", e.forward);
        assertFalse(e.attributes.get("assignmentDetailError").toString().contains("private"));
        assertFalse(e.attributes.containsKey("assignmentDetail"));
    }
    @Test void viewRequiresActiveTeacherSession() throws Exception {
        for (String scenario : List.of("anonymous", "student", "inactive")) {
            var e = new Exchange(); e.params.putAll(Map.of("mode", "view", "assignmentId", "101"));
            if (scenario.equals("anonymous")) e.noSession = true;
            if (scenario.equals("student")) e.session.put("role", UserRole.STUDENT);
            if (scenario.equals("inactive")) e.session.put("accountStatus", AccountStatus.INACTIVE);
            servlet(new IllegalStateException("Service must not be called")).doGet(e.request, e.response);
            assertEquals(scenario.equals("student") ? 403 : 401, e.status);
            assertNull(e.forward); assertTrue(e.attributes.isEmpty());
        }
    }
    @Test void anonymousLocalAssignmentGetRequiresLoginWithoutCallingServiceEvenWhenPreviewIsEnabled() throws Exception {
        String previous = System.getProperty("ams.assignment.preview");
        try {
            System.setProperty("ams.assignment.preview", "true");
            var e = new Exchange(); e.noSession = true; e.localPreview = true;
            boolean[] serviceCalled = {false};
            var service = new AssignmentService() {
                @Override public long previewTeacherId() { serviceCalled[0] = true; return 7L; }
                @Override public AssignmentListPage listPage(long teacherId, Long classId, AssignmentListFilter filter) {
                    serviceCalled[0] = true;
                    return new AssignmentListPage(List.of(), null, filter, List.of());
                }
            };
            new AssignmentServlet(service).doGet(e.request, e.response);
            assertAll(
                    () -> assertEquals(401, e.status),
                    () -> assertFalse(serviceCalled[0], "Anonymous requests must not access Assignment data"),
                    () -> assertNull(e.forward),
                    () -> assertTrue(e.attributes.isEmpty()));
        } finally {
            if (previous == null) System.clearProperty("ams.assignment.preview");
            else System.setProperty("ams.assignment.preview", previous);
        }
    }
}
