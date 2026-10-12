package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.fpt.ams.controller.AssignmentServletTest.proxy;
import java.util.*;
import java.math.BigDecimal;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.AssignmentService;
import vn.edu.fpt.ams.util.AssignmentDraftValidator.ValidationException;

class AssignmentCreateServletTest {
    static class Exchange {
        Map<String,String> params=new HashMap<>(Map.of("action","create","classId","10","csrf","token","title","Keep my title","templateId","1"));
        Map<String,Object> session=new HashMap<>(Map.of("userId",1L,"role",UserRole.TEACHER,"accountStatus",AccountStatus.ACTIVE,"csrf","token"));
        Map<String,Object> attributes=new HashMap<>();
        int status=200; String forward,redirect; boolean anonymous;
        HttpSession httpSession=proxy(HttpSession.class,(m,a)->switch(m){
            case "getAttribute"->session.get(a[0]);
            case "setAttribute"->{session.put((String)a[0],a[1]);yield null;}
            default->null;
        });
        HttpServletRequest request=proxy(HttpServletRequest.class,(m,a)->switch(m){
            case "getSession"->anonymous?null:httpSession;
            case "getParameter"->params.get(a[0]);
            case "getContextPath"->"/ams";
            case "setAttribute"->{attributes.put((String)a[0],a[1]);yield null;}
            case "getAttribute"->attributes.get(a[0]);
            case "getRequestDispatcher"->{forward=(String)a[0];yield proxy(RequestDispatcher.class,(n,b)->null);}
            default->null;
        });
        HttpServletResponse response=proxy(HttpServletResponse.class,(m,a)->{
            if(m.equals("sendError")||m.equals("setStatus")) status=(Integer)a[0];
            if(m.equals("sendRedirect")){redirect=(String)a[0];status=302;}
            return null;
        });
    }
    static class Service extends AssignmentService {
        boolean called; RuntimeException failure, loadFailure;
        @Override public AssignmentCreatePage createPage(long actor,long classId){
            if(loadFailure!=null)throw loadFailure;
            assertEquals(1,actor);assertEquals(10,classId);
            return new AssignmentCreatePage(10,100,"A","Class A","SWP391","Project",
                    List.of(new AssignmentCreatePage.TemplateOption(1,"Template","Instructions",BigDecimal.TEN)));
        }
        @Override public long createAssignment(long actor,AssignmentForm form){
            called=true;assertEquals(1,actor);assertEquals("Keep my title",form.value("title"));
            if(failure!=null)throw failure;return 101;
        }
    }
    AssignmentServlet servlet(Service service){return new AssignmentServlet(service){
        @Override public ServletContext getServletContext(){return proxy(ServletContext.class,(m,a)->null);}
    };}
    @Test void getLoadsCreateFormWithoutWriting()throws Exception{
        var e=new Exchange();e.params.put("mode","create");var service=new Service();
        servlet(service).doGet(e.request,e.response);
        assertEquals("/WEB-INF/views/assignments/form.jsp",e.forward);assertFalse(service.called);
        assertInstanceOf(AssignmentCreatePage.class,e.attributes.get("createPage"));
    }
    @Test void postRequiresLoginTeacherAndCsrfBeforeWriting()throws Exception{
        for(String scenario:List.of("anonymous","student","inactive","csrf")){
            var e=new Exchange();var service=new Service();
            if(scenario.equals("anonymous"))e.anonymous=true;
            if(scenario.equals("student"))e.session.put("role",UserRole.STUDENT);
            if(scenario.equals("inactive"))e.session.put("accountStatus",AccountStatus.INACTIVE);
            if(scenario.equals("csrf"))e.params.put("csrf","wrong");
            servlet(service).doPost(e.request,e.response);
            assertEquals(scenario.equals("anonymous")||scenario.equals("inactive")?401:403,e.status);
            assertFalse(service.called);assertNull(e.redirect);
        }
    }
    @Test void successfulPostRedirectsToNewDetailWithOneTimeNotice()throws Exception{
        var e=new Exchange();var service=new Service();servlet(service).doPost(e.request,e.response);
        assertTrue(service.called);assertEquals(302,e.status);
        assertEquals("/ams/teacher/assignments?mode=view&classId=10&assignmentId=101",e.redirect);
        assertEquals(101L,e.session.get("createdAssignmentId"));assertNull(e.forward);
    }
    @Test void expiredFormTokenRetainsDraftAndRequiresExplicitRetryWithCurrentToken()throws Exception{
        var e=new Exchange();var service=new Service();
        e.session.put("csrf","new-session-token");e.params.put("csrf","old-form-token");
        e.params.put("instructions","Keep full instructions");
        e.params.put("questionsJson","[{\"question_text\":\"Keep question\",\"maximum_points\":\"10\",\"options\":[]}]");
        servlet(service).doPost(e.request,e.response);
        assertEquals(403,e.status);assertFalse(service.called);assertNull(e.redirect);
        assertEquals("/WEB-INF/views/assignments/form.jsp",e.forward,"Stale token must show the recoverable form, not Tomcat's error page");
        assertInstanceOf(AssignmentCreatePage.class,e.attributes.get("createPage"));
        var kept=(AssignmentForm)e.attributes.get("assignmentForm");
        assertEquals("Keep my title",kept.value("title"));assertEquals("Keep full instructions",kept.value("instructions"));
        assertEquals(e.params.get("questionsJson"),kept.value("questionsJson"));
        assertNotNull(e.attributes.get("createError"));
        assertFalse(e.session.containsKey("createdAssignmentId"));
        assertEquals("new-session-token",vn.edu.fpt.ams.util.WebSupport.csrf(e.request));
        e.params.put("csrf","new-session-token");
        servlet(service).doPost(e.request,e.response);
        assertTrue(service.called);assertEquals(302,e.status);assertTrue(e.redirect.contains("assignmentId=101"));
    }
    @Test void missingTokenCanRecoverButNeverWritesAndStillChecksClassAccess()throws Exception{
        var e=new Exchange();e.params.remove("csrf");e.session.remove("csrf");
        var service=new Service();servlet(service).doPost(e.request,e.response);
        assertEquals(403,e.status);assertFalse(service.called);assertNull(e.redirect);
        assertEquals("/WEB-INF/views/assignments/form.jsp",e.forward);
        assertInstanceOf(String.class,e.session.get("csrf"));
        var forbidden=new Exchange();forbidden.params.put("csrf","wrong");
        var denied=new Service();denied.loadFailure=new SecurityException("Forbidden class");
        servlet(denied).doPost(forbidden.request,forbidden.response);
        assertEquals(403,forbidden.status);assertNull(forbidden.forward);assertFalse(denied.called);
        assertFalse(forbidden.attributes.containsKey("createPage"));
    }
    @Test void validationAndDatabaseFailureKeepSubmittedValues()throws Exception{
        for(RuntimeException failure:List.of(new ValidationException(Map.of("title","Invalid title")),new IllegalStateException("secret SQL"))){
            var e=new Exchange();var service=new Service();service.failure=failure;
            servlet(service).doPost(e.request,e.response);
            assertEquals(failure instanceof ValidationException?400:503,e.status);
            assertEquals("/WEB-INF/views/assignments/form.jsp",e.forward);assertNull(e.redirect);
            assertEquals("Keep my title",((AssignmentForm)e.attributes.get("assignmentForm")).value("title"));
            assertFalse(Objects.toString(e.attributes.get("createError")).contains("secret"));
        }
    }
    @Test void contextQueryFailureStillRendersRetryableSubmittedDraft()throws Exception{
        var e=new Exchange();var service=new Service();service.loadFailure=new IllegalStateException("private database error");
        e.params.put("tasksJson","[{\"title\":\"Keep task\",\"description\":\"Keep details\"}]");
        servlet(service).doPost(e.request,e.response);
        assertEquals(503,e.status);assertFalse(service.called);assertNull(e.redirect);
        assertEquals("/WEB-INF/views/assignments/form.jsp",e.forward);
        var context=(AssignmentCreatePage)e.attributes.get("createPage");
        assertEquals(10,context.classId());assertEquals(1,context.templates().get(0).templateId());
        var form=(AssignmentForm)e.attributes.get("assignmentForm");
        assertEquals("Keep my title",form.value("title"));assertEquals(e.params.get("tasksJson"),form.value("tasksJson"));
        assertFalse(e.attributes.get("createError").toString().contains("private"));
    }
}
