package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.StudentObjectiveService;

class StudentAssignmentServletTest {
    private static class Exchange {
        Map<String,Object> session=new HashMap<>(Map.of("userId",4L,"role",UserRole.STUDENT,"accountStatus",AccountStatus.ACTIVE,"csrf","valid"));
        Map<String,String[]> params=new HashMap<>(Map.of("csrf",new String[]{"valid"},"assignmentId",new String[]{"50"},"submissionId",new String[]{"8"},"action",new String[]{"submit"},"answerRevision",new String[]{"0"}));
        Map<String,String> headers=new HashMap<>();int status=200;
        StringWriter body=new StringWriter();
        HttpSession httpSession=(HttpSession)Proxy.newProxyInstance(HttpSession.class.getClassLoader(),new Class<?>[]{HttpSession.class},(p,m,a)->m.getName().equals("getAttribute")?session.get(a[0]):null);
        HttpServletRequest request=(HttpServletRequest)Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),new Class<?>[]{HttpServletRequest.class},(p,m,a)->switch(m.getName()){
            case "getSession"->httpSession;
            case "getParameter"->params.containsKey(a[0])?params.get(a[0])[0]:null;
            case "getParameterMap"->params;
            case "getContextPath"->"/ams";
            default->null;
        });
        HttpServletResponse response=(HttpServletResponse)Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),new Class<?>[]{HttpServletResponse.class},(p,m,a)->{
            if(m.getName().equals("sendError")||m.getName().equals("setStatus"))status=(int)a[0];
            if(m.getName().equals("setHeader"))headers.put((String)a[0],(String)a[1]);
            if(m.getName().equals("getWriter"))return new PrintWriter(body);
            return null;
        });
    }
    @Test void autosaveNeedsCsrfAndReturnsServerTimeWithoutAnswersOrScore() throws Exception {
        var e=new Exchange();e.params.put("action",new String[]{"save"});e.params.put("answer_51",new String[]{"A","C"});
        var service=new StudentObjectiveService(){@Override public SaveResult save(long u,long a,long s,long revision,Map<Long,List<String>> values) {
            assertEquals(4,u);assertEquals(50,a);assertEquals(8,s);assertEquals(List.of("A","C"),values.get(51L));
            return new SaveResult(false,Instant.ofEpochMilli(1000),Instant.ofEpochMilli(2000),1);
        }};
        new StudentAssignmentServlet(service).doPost(e.request,e.response);
        assertEquals(200,e.status);assertEquals("{\"finished\":false,\"serverTime\":1000,\"deadline\":2000,\"revision\":1}",e.body.toString());
        var rejected=new Exchange();rejected.params.put("action",new String[]{"save"});rejected.params.put("csrf",new String[]{"wrong"});
        new StudentAssignmentServlet(new StudentObjectiveService(){@Override public SaveResult save(long u,long a,long s,long revision,Map<Long,List<String>> values){fail("CSRF must be checked before saving");return null;}})
                .doPost(rejected.request,rejected.response);
        assertEquals(403,rejected.status);
    }
    @Test void postsNeedActiveStudentAndMatchingCsrfBeforeServiceAccess() throws Exception {
        for(String scenario:List.of("teacher","inactive","missing","csrf")) {
            var e=new Exchange();
            if(scenario.equals("teacher"))e.session.put("role",UserRole.TEACHER);
            if(scenario.equals("inactive"))e.session.put("accountStatus",AccountStatus.INACTIVE);
            if(scenario.equals("missing"))e.session.clear();
            if(scenario.equals("csrf"))e.params.put("csrf",new String[]{"wrong"});
            var service=new StudentObjectiveService(){@Override public long submit(long u,long a,long s,long revision,Map<Long,List<String>> values){fail("Service must not run");return 0;}};
            new StudentAssignmentServlet(service).doPost(e.request,e.response);assertEquals(403,e.status);
        }
    }
    @Test void postsAllSelectionsWithSessionIdentityAndRedirectsAfterSuccess() throws Exception {
        var e=new Exchange();e.params.put("answer_51",new String[]{"A","C"});e.params.put("studentId",new String[]{"99"});
        var service=new StudentObjectiveService(){@Override public long submit(long u,long a,long s,long revision,Map<Long,List<String>> values){
            assertEquals(4,u);assertEquals(50,a);assertEquals(8,s);assertEquals(List.of("A","C"),values.get(51L));return s;
        }};
        new StudentAssignmentServlet(service).doPost(e.request,e.response);
        assertEquals(303,e.status);assertEquals("/ams/student/assignments?assignmentId=50&submissionId=8",e.headers.get("Location"));
    }
    @Test void staleSaveReturnsConflictInsteadOfSilentlyRetrying() throws Exception {
        var e=new Exchange();e.params.put("action",new String[]{"save"});
        var service=new StudentObjectiveService(){@Override public SaveResult save(long u,long a,long s,long revision,Map<Long,List<String>> values) {
            throw new ConcurrentModificationException("Vui lòng tải lại trang.");
        }};
        new StudentAssignmentServlet(service).doPost(e.request,e.response);assertEquals(409,e.status);
    }
}
