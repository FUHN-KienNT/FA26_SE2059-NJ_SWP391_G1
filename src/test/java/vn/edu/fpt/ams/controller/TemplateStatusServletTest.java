package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.http.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.TemplateService;

class TemplateStatusServletTest {
  @Test void statusPostPreservesFiltersAndReturnsToListAfterSuccessfulWrite() throws Exception {
    for (var target : TemplateStatus.values()) {
      var params = new HashMap<String,String>();
      params.put("action","status"); params.put("id","17"); params.put("csrf","test-token");
      params.put("status",target.name()); params.put("returnTo","list"); params.put("page","2");
      params.put("keyword","Mẫu & quiz"); params.put("filterStatus","ACTIVE");
      var attributes = new HashMap<String,Object>();
      attributes.put("userId",2L); attributes.put("csrf","test-token");
      var session = (HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(),new Class[]{HttpSession.class},(p,m,a)->{
        if(m.getName().equals("getAttribute")) return attributes.get(a[0]);
        if(m.getName().equals("setAttribute")){attributes.put((String)a[0],a[1]);return null;}
        throw new UnsupportedOperationException(m.getName());
      });
      var req = (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),new Class[]{HttpServletRequest.class},(p,m,a)->switch(m.getName()){
        case "getSession" -> session;
        case "getParameter" -> params.get(a[0]);
        case "getServletPath" -> "/expert/templates";
        case "getContextPath" -> "/ams";
        case "setCharacterEncoding" -> null;
        default -> throw new UnsupportedOperationException(m.getName());
      });
      boolean[] saved={false}; String[] redirect={null};
      var res = (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),new Class[]{HttpServletResponse.class},(p,m,a)->{
        if(m.getName().equals("sendRedirect")){assertTrue(saved[0]);redirect[0]=(String)a[0];return null;}
        throw new UnsupportedOperationException(m.getName());
      });
      var service = new TemplateService(){
        @Override public AssignmentTemplate findTemplate(long actor,long id){
          assertEquals(2,actor);assertEquals(17,id);
          return new AssignmentTemplate(id,5,"Quiz","Instructions",BigDecimal.TEN,TemplateStatus.ACTIVE,null,null);
        }
        @Override public void changeStatus(long actor,long id,TemplateStatus status){
          assertEquals(target,status);assertEquals(2,actor);assertEquals(17,id);saved[0]=true;
        }
      };
      new TemplateServlet(service).doPost(req,res);
      assertEquals("/ams/expert/templates?subjectId=5&page=2&keyword=M%E1%BA%ABu+%26+quiz&status=ACTIVE",redirect[0]);
      assertNotNull(attributes.get("notice"));
    }
  }
}
