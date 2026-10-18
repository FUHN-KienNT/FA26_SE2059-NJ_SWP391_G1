package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.TeacherSubmissionService;

class TeacherSubmissionServletTest {
    private static class Exchange {
        Map<String,String> params = new HashMap<>(Map.of("assignmentId", "101"));
        Map<String,Object> session = new HashMap<>(Map.<String,Object>of("userId", 7L, "role", UserRole.TEACHER,
                "accountStatus", AccountStatus.ACTIVE));
        Map<String,Object> attributes = new HashMap<>();
        Map<String,String> headers = new HashMap<>();
        int status = 200;
        String error, forward;
        boolean anonymous, called;
        List<TeacherSubmissionRow> listRows = List.of();
        HttpSession httpSession = proxy(HttpSession.class, (method,args) -> session.get(args[0]));
        RequestDispatcher dispatcher = proxy(RequestDispatcher.class, (method,args) -> null);
        HttpServletRequest request = proxy(HttpServletRequest.class, (method,args) -> switch (method) {
            case "getSession" -> anonymous ? null : httpSession;
            case "getParameter" -> params.get(args[0]);
            case "setAttribute" -> { attributes.put((String) args[0], args[1]); yield null; }
            case "getRequestDispatcher" -> { forward = (String) args[0]; yield dispatcher; }
            default -> null;
        });
        HttpServletResponse response = proxy(HttpServletResponse.class, (method,args) -> {
            if (method.equals("sendError") || method.equals("setStatus")) status = (int) args[0];
            if (method.equals("sendError") && args.length > 1) error = (String) args[1];
            if (method.equals("setHeader")) headers.put((String) args[0], (String) args[1]);
            return null;
        });
    }
    @FunctionalInterface private interface Call { Object invoke(String method, Object[] args); }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, Call call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (object,method,args) -> call.invoke(method.getName(), args));
    }
    private TeacherSubmissionServlet servlet(Exchange e, RuntimeException failure) {
        var service = new TeacherSubmissionService() {
            @Override public TeacherSubmissionListPage listPage(long teacherId, long assignmentId, TeacherSubmissionFilter filter) {
                e.called = true;
                assertEquals(7, teacherId); assertEquals(101, assignmentId);
                if (failure != null) throw failure;
                return new TeacherSubmissionListPage(context(), filter, e.listRows);
            }
            @Override public TeacherSubmissionDetailPage detailPage(long teacherId, long assignmentId, long submissionId) {
                e.called = true;
                assertEquals(7, teacherId); assertEquals(101, assignmentId); assertEquals(55, submissionId);
                if (failure != null) throw failure;
                var submission = new Submission(55,101,12,1,SubmissionStatus.SUBMITTED, Instant.EPOCH,
                        Instant.EPOCH.plusSeconds(3600), Instant.EPOCH.plusSeconds(100),"Work",null,"[]",
                        null,null,null,null,null);
                return new TeacherSubmissionDetailPage(context(), new TeacherSubmissionRow(submission,8,"Sinh viên","student@example.test",null));
            }
        };
        return new TeacherSubmissionServlet(service) {
            @Override public ServletContext getServletContext() {
                return proxy(ServletContext.class, (method,args) -> null);
            }
        };
    }
    private AssignmentDetailPage context() {
        var assignment = new Assignment(101,10,1,"Bài tập",AssignmentPurpose.REGULAR,ResponseType.ESSAY,
                "Hướng dẫn","Scope","[]",BigDecimal.TEN,AssignmentStatus.PUBLISHED,
                Instant.EPOCH,Instant.EPOCH.plusSeconds(3600),null,null,Instant.EPOCH,null);
        return new AssignmentDetailPage(assignment,"CL-A","Class A","SUB","Subject","FA26","Template",List.of());
    }

    @Test void listUsesSessionTeacherAndNormalizesFiltersWithoutTrustingBrowserActorOrClass() throws Exception {
        var e = new Exchange(); e.params.putAll(Map.of("teacherId","999","classId","999","search"," Student ","status"," submitted "));
        servlet(e,null).doGet(e.request,e.response);
        assertEquals(200,e.status); assertEquals("no-store",e.headers.get("Cache-Control"));
        assertEquals("/WEB-INF/views/submissions/list.jsp",e.forward);
        var page = (TeacherSubmissionListPage) e.attributes.get("submissionPage");
        assertEquals("Student",page.filter().search()); assertEquals(SubmissionStatus.SUBMITTED,page.filter().status());
        assertEquals(10,page.assignment().assignment().classId());
    }
    @Test void viewLoadsOnlyTheRequestedAttemptAndRetainsListFilters() throws Exception {
        var e = new Exchange(); e.params.putAll(Map.of("mode","view","submissionId","55","search","name & email","status","GRADED","page","2"));
        servlet(e,null).doGet(e.request,e.response);
        assertEquals(200,e.status); assertEquals("/WEB-INF/views/submissions/detail.jsp",e.forward);
        assertEquals(55,((TeacherSubmissionDetailPage)e.attributes.get("submissionDetail")).row().submission().submissionId());
        assertEquals("name & email",((TeacherSubmissionFilter)e.attributes.get("submissionFilters")).search());
        assertEquals(SubmissionStatus.GRADED,((TeacherSubmissionFilter)e.attributes.get("submissionFilters")).status());
        assertEquals(2,((TeacherSubmissionFilter)e.attributes.get("submissionFilters")).page());
    }
    @Test void listAcceptsNotStartedFilterAndForwardsStudentWithoutInventingSubmission() throws Exception {
        var e = new Exchange();
        e.params.putAll(Map.of("search"," New student ","status"," not_started ","page","2"));
        e.listRows=List.of(new TeacherSubmissionRow(null,8,"New student","new@example.test",null));
        servlet(e,null).doGet(e.request,e.response);
        assertEquals(200,e.status); assertEquals("/WEB-INF/views/submissions/list.jsp",e.forward);
        var page=(TeacherSubmissionListPage)e.attributes.get("submissionPage");
        assertEquals("New student",page.filter().search());
        assertTrue(page.filter().notStartedOnly()); assertNull(page.filter().status());
        assertEquals("NOT_STARTED",page.filter().statusValue()); assertEquals(2,page.filter().page());
        assertEquals(8,page.submissions().get(0).studentId());
        assertFalse(page.submissions().get(0).hasSubmission()); assertNull(page.submissions().get(0).submission());
    }
    @Test void detailRetainsNotStartedFilterForReturnNavigation() throws Exception {
        var e = new Exchange(); e.params.putAll(Map.of("mode","view","submissionId","55","status","NOT_STARTED","page","2"));
        servlet(e,null).doGet(e.request,e.response);
        assertEquals(200,e.status); assertEquals("/WEB-INF/views/submissions/detail.jsp",e.forward);
        var filter=(TeacherSubmissionFilter)e.attributes.get("submissionFilters");
        assertTrue(filter.notStartedOnly()); assertEquals("NOT_STARTED",filter.statusValue()); assertEquals(2,filter.page());
        assertEquals(55,((TeacherSubmissionDetailPage)e.attributes.get("submissionDetail")).row().submission().submissionId());
    }
    @Test void getAndPostRequireAnActiveTeacherSessionBeforeServiceAccess() throws Exception {
        for (boolean post : new boolean[]{false,true}) for (String scenario : List.of("anonymous","student","inactive","badId")) {
            var e = new Exchange();
            if (scenario.equals("anonymous")) e.anonymous = true;
            if (scenario.equals("student")) e.session.put("role",UserRole.STUDENT);
            if (scenario.equals("inactive")) e.session.put("accountStatus",AccountStatus.INACTIVE);
            if (scenario.equals("badId")) e.session.put("userId",0L);
            var controller = servlet(e,new AssertionFailure());
            if (post) controller.doPost(e.request,e.response); else controller.doGet(e.request,e.response);
            assertEquals(scenario.equals("student")?403:401,e.status); assertFalse(e.called); assertNull(e.forward);
        }
    }
    private static class AssertionFailure extends RuntimeException { }
    @Test void invalidIdsEnumsSearchAndModesReturn400WithoutQuerying() throws Exception {
        for (Map<String,String> params : List.of(Map.of("assignmentId",""),Map.of("assignmentId","abc"),
                Map.of("assignmentId","0"),Map.of("assignmentId","-1"),Map.of("assignmentId","99999999999999999999999"),
                Map.of("mode","view"),Map.of("mode","view","submissionId","-1"),Map.of("mode","view","submissionId","abc"),
                Map.of("mode","edit"),Map.of("status","DELETED"),Map.of("search","L".repeat(201)),
                Map.of("page","0"),Map.of("page","-1"),Map.of("page","1.5"),Map.of("page","abc"),
                Map.of("page","2147483648"))) {
            var e = new Exchange(); e.params.putAll(params);
            servlet(e,null).doGet(e.request,e.response);
            assertEquals(400,e.status); assertFalse(e.called); assertNull(e.forward);
        }
        var missing = new Exchange(); missing.params.clear();
        servlet(missing,null).doGet(missing.request,missing.response); assertEquals(400,missing.status); assertFalse(missing.called);
    }
    @Test void foreignAndMissingAssignmentOrSubmissionHaveSame404WithNoContent() throws Exception {
        for (boolean detail : new boolean[]{false,true}) {
            var e = new Exchange(); if (detail) e.params.putAll(Map.of("mode","view","submissionId","55"));
            servlet(e,new NoSuchElementException("Private student identity")).doGet(e.request,e.response);
            assertEquals(404,e.status); assertFalse(e.error.contains("Private")); assertNull(e.forward);
            assertFalse(e.attributes.containsKey("submissionPage")); assertFalse(e.attributes.containsKey("submissionDetail"));
        }
    }
    @Test void accountRevalidationFailureReturns403WithoutRenderingSavedData() throws Exception {
        var e = new Exchange(); servlet(e,new SecurityException("Private account context")).doGet(e.request,e.response);
        assertEquals(403,e.status); assertNull(e.forward); assertFalse(e.error.contains("Private"));
    }
    @Test void databaseFailureHasRecoverableListOrDetailErrorWithoutSqlDetails() throws Exception {
        for (boolean detail : new boolean[]{false,true}) {
            var e = new Exchange(); if (detail) e.params.putAll(Map.of("mode","view","submissionId","55"));
            servlet(e,new IllegalStateException("SQL password private error")).doGet(e.request,e.response);
            assertEquals(503,e.status); assertEquals(detail?"/WEB-INF/views/submissions/detail.jsp":"/WEB-INF/views/submissions/list.jsp",e.forward);
            assertFalse(e.attributes.get("submissionError").toString().contains("private"));
            assertFalse(e.attributes.containsKey("submissionPage")); assertFalse(e.attributes.containsKey("submissionDetail"));
        }
    }
    @Test void authenticatedPostIs405AndNeverReadsOrChangesSubmissions() throws Exception {
        var e = new Exchange(); e.params.put("action","evaluate"); servlet(e,null).doPost(e.request,e.response);
        assertEquals(405,e.status); assertEquals("GET, HEAD",e.headers.get("Allow")); assertFalse(e.called); assertNull(e.forward);
    }
}
