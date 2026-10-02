package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.AssignmentService;

class AssignmentServletTest {
    private static class Exchange {
        Map<String,String> params = new HashMap<>();
        Map<String,Object> session = new HashMap<>(Map.<String,Object>of("userId",1L,"role",UserRole.TEACHER,"accountStatus",AccountStatus.ACTIVE));
        Map<String,Object> attributes = new HashMap<>();
        String forward;
        int status = 200;
        boolean noSession;
        boolean localPreview;
        String cacheControl;
        HttpSession httpSession = proxy(HttpSession.class, (method,args) -> session.get((String)args[0]));
        RequestDispatcher dispatcher = proxy(RequestDispatcher.class, (method,args) -> null);
        HttpServletRequest request = proxy(HttpServletRequest.class, (method,args) -> switch(method) {
            case "getMethod" -> "GET";
            case "getServletPath" -> "/teacher/assignments";
            case "getRemoteAddr" -> localPreview ? "127.0.0.1" : "192.168.1.20";
            case "getSession" -> noSession ? null : httpSession;
            case "getParameter" -> params.get((String)args[0]);
            case "setAttribute" -> { attributes.put((String)args[0],args[1]); yield null; }
            case "getRequestDispatcher" -> { forward=(String)args[0]; yield dispatcher; }
            default -> null;
        });
        HttpServletResponse response = proxy(HttpServletResponse.class, (method,args) -> {
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
        };
        return new AssignmentServlet(service) {
            @Override public ServletContext getServletContext() {
                return proxy(ServletContext.class,(method,args)->null);
            }
        };
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
    @Test void anonymousLocalPreviewUsesDatabaseTeacherWithoutLoginSession() throws Exception {
        String previous = System.getProperty("ams.assignment.preview");
        try {
            System.setProperty("ams.assignment.preview", "true");
            var e = new Exchange(); e.noSession = true; e.localPreview = true;
            servlet(null).doGet(e.request, e.response);
            assertEquals(200, e.status);
            assertEquals(7L, e.attributes.get("assignmentPreviewTeacherId"));
            assertEquals("/WEB-INF/views/assignments/list.jsp", e.forward);
        } finally {
            if (previous == null) System.clearProperty("ams.assignment.preview");
            else System.setProperty("ams.assignment.preview", previous);
        }
    }
}
