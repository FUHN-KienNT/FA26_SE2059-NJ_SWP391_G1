package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.*;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.StudentSubjectDTO.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.StudentSubjectService;

class StudentSubjectServletTest {
    @Test void getUsesOnlySessionIdentityForListAndDetail()throws Exception{
        var h=new Harness();h.params.put("studentId","2");h.params.put("userId","2");
        h.servlet().doGet(h.request(),h.response());assertEquals(1,h.actor);assertEquals("/WEB-INF/views/student/subjects.jsp",h.view);
        h.params.put("id","10");h.servlet().doGet(h.request(),h.response());
        assertEquals(1,h.actor);assertEquals(10,h.offering);assertEquals("/WEB-INF/views/student/subject-detail.jsp",h.view);
    }
    @Test void postRequiresCsrfAndUsesSessionStudentThenRedirectsAfterSuccess()throws Exception{
        var h=new Harness();h.params.putAll(Map.of("csrf","expected-csrf","action","register","id","10","studentId","2"));
        h.servlet().doPost(h.request(),h.response());assertEquals(1,h.actor);assertEquals(10,h.offering);
        assertEquals("/ams/student/subjects?id=10&registered=1",h.redirect);
    }
    @Test void wrongRolesMissingSessionAndBadCsrfNeverRegister()throws Exception{
        for(var role:List.of(UserRole.ADMIN,UserRole.EXPERT,UserRole.TEACHER)){
            var h=new Harness();h.role=role;h.servlet().doGet(h.request(),h.response());assertEquals(403,h.status);assertEquals(0,h.actor);
        }
        var inactive=new Harness();inactive.account=AccountStatus.INACTIVE;inactive.servlet().doGet(inactive.request(),inactive.response());assertEquals(403,inactive.status);
        var missing=new Harness();missing.role=null;missing.servlet().doGet(missing.request(),missing.response());assertEquals(403,missing.status);
        var h=new Harness();h.params.putAll(Map.of("csrf","wrong","action","register","id","10"));
        h.servlet().doPost(h.request(),h.response());assertEquals(403,h.status);assertEquals(0,h.actor);
    }
    @Test void invalidActionAndIdAreRejectedBeforeWrites()throws Exception{
        for(var params:List.of(Map.of("action","delete","id","10"),Map.of("action","register","id","-1"))){
            var h=new Harness();h.params.putAll(params);h.params.put("csrf","expected-csrf");
            h.servlet().doPost(h.request(),h.response());assertEquals(400,h.status);assertEquals(0,h.actor);
        }
    }
    @Test void businessRejectionIsRenderedWithoutSuccessRedirect()throws Exception{
        var h=new Harness();h.reject=true;h.params.putAll(Map.of("csrf","expected-csrf","action","register","id","10"));
        h.servlet().doPost(h.request(),h.response());assertEquals(400,h.status);assertNull(h.redirect);
        assertEquals("Môn học đã hết chỗ.",h.attributes.get("error"));assertEquals("/WEB-INF/views/student/subject-detail.jsp",h.view);
    }
    private static class Harness{
        UserRole role=UserRole.STUDENT;AccountStatus account=AccountStatus.ACTIVE;
        Map<String,String> params=new HashMap<>();Map<String,Object> attributes=new HashMap<>();
        long actor,offering;int status=200;String view,redirect;boolean reject;
        StudentSubjectServlet servlet(){return new StudentSubjectServlet(new StudentSubjectService(){
            @Override public Page list(long id,String mode,String q){actor=id;return new Page(List.of(),"available","");}
            @Override public Offering detail(long id,long term){actor=id;offering=term;return new Offering(term,"SWP","Project","","ACTIVE","T","Term",true,true,true,Instant.now().minusSeconds(10),Instant.now().plusSeconds(100),null,null,null,null,null,1,Instant.now());}
            @Override public long register(long id,long term){actor=id;offering=term;if(reject)throw new IllegalArgumentException("Môn học đã hết chỗ.");return 1;}
        });}
        HttpServletRequest request(){
            var session=(HttpSession)Proxy.newProxyInstance(HttpSession.class.getClassLoader(),new Class<?>[]{HttpSession.class},(p,m,a)->switch(m.getName()){
                case "getAttribute" -> switch((String)a[0]){case "userId"->1L;case "role"->role;case "accountStatus"->account;case "csrf"->"expected-csrf";default->null;};
                default->null;
            });
            return (HttpServletRequest)Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),new Class<?>[]{HttpServletRequest.class},(p,m,a)->switch(m.getName()){
                case "getSession" -> role==null?null:session;
                case "getParameter" -> params.get((String)a[0]);
                case "getContextPath" -> "/ams";
                case "setAttribute" -> {attributes.put((String)a[0],a[1]);yield null;}
                case "getRequestDispatcher" -> {view=(String)a[0];yield Proxy.newProxyInstance(RequestDispatcher.class.getClassLoader(),new Class<?>[]{RequestDispatcher.class},(d,method,args)->null);}
                default->throw new UnsupportedOperationException(m.getName());
            });
        }
        HttpServletResponse response(){return (HttpServletResponse)Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),new Class<?>[]{HttpServletResponse.class},(p,m,a)->{
            if(m.getName().equals("sendError")||m.getName().equals("setStatus"))status=(int)a[0];
            if(m.getName().equals("sendRedirect"))redirect=(String)a[0];return null;
        });}
    }
}
