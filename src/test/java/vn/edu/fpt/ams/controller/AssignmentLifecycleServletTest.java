package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.fpt.ams.controller.AssignmentServletTest.proxy;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dao.AssignmentDAO;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.AssignmentService;
import vn.edu.fpt.ams.util.AssignmentDraftValidator.ValidationException;
import vn.edu.fpt.ams.util.AssignmentEditConflictException;

class AssignmentLifecycleServletTest {
    private static final String VIEW="/WEB-INF/views/assignments/lifecycle.jsp";
    private static final String REVISION="a".repeat(64);

    private static class Exchange {
        Map<String,String> params=new HashMap<>(Map.of("assignmentId","101","action","publish","csrf","token","revision",REVISION));
        Map<String,Object> session=new HashMap<>(Map.of("userId",1L,"role",UserRole.TEACHER,"accountStatus",AccountStatus.ACTIVE,"csrf","token"));
        Map<String,Object> attributes=new HashMap<>();
        int status=200; String forward,redirect; boolean anonymous;
        HttpSession httpSession=proxy(HttpSession.class,(method,args)->switch(method) {
            case "getAttribute" -> session.get(args[0]);
            case "setAttribute" -> {session.put((String)args[0],args[1]);yield null;}
            case "removeAttribute" -> {session.remove(args[0]);yield null;}
            default -> null;
        });
        HttpServletRequest request=proxy(HttpServletRequest.class,(method,args)->switch(method) {
            case "getSession" -> anonymous?null:httpSession;
            case "getParameter" -> params.get(args[0]);
            case "getContextPath" -> "/ams";
            case "getAttribute" -> attributes.get(args[0]);
            case "setAttribute" -> {attributes.put((String)args[0],args[1]);yield null;}
            case "removeAttribute" -> {attributes.remove(args[0]);yield null;}
            case "getRequestDispatcher" -> {forward=(String)args[0];yield proxy(RequestDispatcher.class,(name,arguments)->null);}
            default -> null;
        });
        HttpServletResponse response=proxy(HttpServletResponse.class,(method,args)-> {
            if(method.equals("sendError")||method.equals("setStatus")) status=(Integer)args[0];
            if(method.equals("sendRedirect")) {status=302;redirect=(String)args[0];}
            return null;
        });
    }

    private static class Service extends AssignmentService {
        int loads,writes; RuntimeException loadFailure,writeFailure;
        Service() { super(new AssignmentDAO(),()->{throw new AssertionError("No real database in servlet tests");}); }
        @Override public AssignmentLifecyclePage lifecyclePage(long actor,long id,String action) {
            loads++; assertEquals(1,actor); assertEquals(101,id);
            if(loadFailure!=null) throw loadFailure;
            var a=new Assignment(id,10,1,"Saved <title>",AssignmentPurpose.REGULAR,ResponseType.ESSAY,
                    "Instructions","Scope","[{\"title\":\"Task\",\"task_order\":1}]",BigDecimal.TEN,
                    AssignmentStatus.DRAFT,Instant.parse("2030-01-01T00:00:00Z"),Instant.parse("2030-01-02T00:00:00Z"),null,null,null,null);
            var detail=new AssignmentDetailPage(a,"Class A","Owned","SUB","Subject","TERM","Template",List.of());
            return new AssignmentLifecyclePage(detail,action,loads>1?"b".repeat(64):REVISION,Map.of(),2);
        }
        @Override public void publishAssignment(long actor,long id,String revision) { write(actor,id,revision); }
        @Override public int cancelAssignment(long actor,long id,String revision) { write(actor,id,revision);return 2; }
        private void write(long actor,long id,String revision) {
            writes++; assertEquals(1,actor);assertEquals(101,id);assertEquals(REVISION,revision);
            if(writeFailure!=null) throw writeFailure;
        }
    }

    private AssignmentServlet servlet(Service service) {
        return new AssignmentServlet(service) {
            @Override public ServletContext getServletContext() {return proxy(ServletContext.class,(method,args)->null);}
        };
    }

    @Test void getOnlyLoadsConfirmationAndRetainsFilters() throws Exception {
        for(String action:List.of("publish","cancel")) {
            var e=new Exchange(); e.params.putAll(Map.of("mode",action,"search","A & B","status","DRAFT"));
            var service=new Service();servlet(service).doGet(e.request,e.response);
            assertEquals(200,e.status);assertEquals(VIEW,e.forward);assertEquals(0,service.writes);
            var page=assertInstanceOf(AssignmentLifecyclePage.class,e.attributes.get("lifecyclePage"));
            assertEquals(action,page.action());assertEquals("Saved <title>",page.detail().assignment().title());
            assertEquals("A & B",((AssignmentListFilter)e.attributes.get("assignmentFilters")).search());
        }
    }

    @Test void successfulPostUsesSessionActorSavedClassAndSameAssignment() throws Exception {
        for(String action:List.of("publish","cancel")) {
            var e=new Exchange();e.params.putAll(Map.of("action",action,"classId","999","teacherId","999","search","A & B","status","DRAFT"));
            e.session.put("createdAssignmentId",101L);e.session.put("updatedAssignmentId",101L);
            var service=new Service();servlet(service).doPost(e.request,e.response);
            assertEquals(1,service.writes);assertEquals(302,e.status);assertNull(e.forward);
            assertTrue(e.redirect.contains("classId=10"));assertTrue(e.redirect.contains("assignmentId=101"));
            assertTrue(e.redirect.contains("search=A+%26+B"));assertTrue(e.redirect.contains("status=DRAFT"));
            assertEquals(101L,e.session.get(action.equals("publish")?"publishedAssignmentId":"cancelledAssignmentId"));
            assertFalse(e.session.containsKey("updatedAssignmentId"));assertFalse(e.session.containsKey("createdAssignmentId"));
        }
    }

    @Test void missingOrExpiredCsrfNeverMutatesAndRequiresExplicitRetry() throws Exception {
        for(String token:new String[]{null,"old-token"}) {
            var e=new Exchange(); if(token==null)e.params.remove("csrf");else e.params.put("csrf",token);
            var service=new Service();servlet(service).doPost(e.request,e.response);
            assertEquals(403,e.status);assertEquals(VIEW,e.forward);assertEquals(0,service.writes);assertNull(e.redirect);
            assertTrue(e.attributes.get("lifecycleError").toString().contains("Nothing was changed"));
            e.params.put("csrf",(String)e.session.get("csrf"));
            servlet(service).doPost(e.request,e.response);assertEquals(302,e.status);assertEquals(1,service.writes);
        }
    }

    @Test void wrongSessionsRejectBothReadAndWriteBeforeLoadingData() throws Exception {
        for(boolean post:List.of(false,true)) for(String state:List.of("anonymous","student","inactive")) {
            var e=new Exchange();e.params.put("mode","publish");
            if(state.equals("anonymous"))e.anonymous=true;
            if(state.equals("student"))e.session.put("role",UserRole.STUDENT);
            if(state.equals("inactive"))e.session.put("accountStatus",AccountStatus.INACTIVE);
            var service=new Service();if(post)servlet(service).doPost(e.request,e.response);else servlet(service).doGet(e.request,e.response);
            assertEquals(state.equals("student")?403:401,e.status);assertEquals(0,service.loads);assertEquals(0,service.writes);assertNull(e.forward);
        }
    }

    @Test void foreignAndMissingAssignmentsHaveNoContentOrMutation() throws Exception {
        for(boolean post:List.of(false,true)) {
            var e=new Exchange();e.params.put("mode","cancel");var service=new Service();service.loadFailure=new NoSuchElementException("Private ownership");
            if(post)servlet(service).doPost(e.request,e.response);else servlet(service).doGet(e.request,e.response);
            assertEquals(404,e.status);assertNull(e.forward);assertFalse(e.attributes.containsKey("lifecyclePage"));assertEquals(0,service.writes);
        }
    }

    @Test void invalidIdentifiersAndFiltersNeverReachWrites() throws Exception {
        for(Map<String,String> params:List.of(Map.of("assignmentId","0"),Map.of("assignmentId","abc"),
                Map.of("assignmentId","999999999999999999999"),Map.of("status","DELETED"))) {
            var e=new Exchange();e.params.putAll(params);var service=new Service();servlet(service).doPost(e.request,e.response);
            assertEquals(400,e.status);assertEquals(0,service.writes);assertNull(e.forward);
        }
    }

    @Test void validationAndStaleRevisionRedisplayLatestConfirmationWithoutSuccess() throws Exception {
        for(RuntimeException failure:List.of(new ValidationException(Map.of("dueAt","Deadline passed")),
                new AssignmentEditConflictException("Saved content changed"))) {
            var e=new Exchange();var service=new Service();service.writeFailure=failure;
            servlet(service).doPost(e.request,e.response);
            assertEquals(failure instanceof ValidationException?400:409,e.status);assertEquals(VIEW,e.forward);assertNull(e.redirect);
            assertEquals("b".repeat(64),((AssignmentLifecyclePage)e.attributes.get("lifecyclePage")).revision());
            assertFalse(e.session.containsKey("publishedAssignmentId"));
            if(failure instanceof ValidationException) assertEquals(Map.of("dueAt","Deadline passed"),e.attributes.get("lifecycleErrors"));
        }
    }

    @Test void databaseFailureDoesNotExposeSqlAndUnavailableReloadDisablesConfirmation() throws Exception {
        var e=new Exchange();var service=new Service();service.loadFailure=new IllegalStateException("private SQL password details");
        servlet(service).doPost(e.request,e.response);
        assertEquals(503,e.status);assertEquals(VIEW,e.forward);assertFalse(e.attributes.containsKey("lifecyclePage"));
        assertFalse(e.attributes.get("lifecycleError").toString().contains("private"));assertEquals(0,service.writes);
    }
}
