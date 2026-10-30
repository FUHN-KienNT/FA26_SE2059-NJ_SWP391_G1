package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.AssignmentOfferView;
import vn.edu.fpt.ams.filter.AuthFilter;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.AssignmentOfferService;

class AssignmentOfferServletTest {
    /** Recipient identity comes from the session despite a forged teacherId parameter. */
    @Test void searchAndStatusUseSessionRecipient() throws Exception {
        var h=new Harness();
        h.params.putAll(Map.of("teacherId","999","keyword","SWP","status","PENDING"));
        h.servlet().doGet(h.request(),h.response());
        assertEquals(1,h.actor);
        assertEquals(1,((List<?>)h.attributes.get("offers")).size());
        assertEquals(1L,h.attributes.get("pendingCount"));
        assertEquals("/WEB-INF/views/teacher/offers.jsp",h.view);
    }
    /** A forged or missing CSRF token never reaches consent processing. */
    @Test void invalidCsrfDoesNotRespond() throws Exception {
        for(String token:List.of("","forged")) {
            var h=new Harness(); h.params.putAll(Map.of("offerId","20","decision","ACCEPTED","csrf",token));
            h.servlet().doPost(h.request(),h.response());
            assertEquals(403,h.status); assertFalse(h.responded);
        }
    }
    /** Successful responses redirect instead of resubmitting on page refresh. */
    @Test void verifiedPostUsesSessionAndRedirects() throws Exception {
        var h=new Harness();
        h.params.putAll(Map.of("offerId","20","teacherId","999","decision","ACCEPTED","csrf","known"));
        h.servlet().doPost(h.request(),h.response());
        assertTrue(h.responded); assertEquals(1,h.actor);
        assertEquals("/ams/teacher/offers",h.redirect);
        assertTrue(h.session.get("offerNotice").toString().contains("Đã nhận"));
    }
    /** Expired acceptance is reported without claiming that a class was assigned. */
    @Test void expiryNoticeAndFlashAreConsumedOnce() throws Exception {
        var h=new Harness();h.result=AssignmentOfferService.Response.EXPIRED;
        h.params.putAll(Map.of("offerId","20","decision","ACCEPTED","csrf","known"));
        h.servlet().doPost(h.request(),h.response());
        h.servlet().doGet(h.request(),h.response());
        assertTrue(h.attributes.get("notice").toString().contains("hết hạn"));
        assertFalse(h.session.containsKey("offerNotice"));
    }
    /** Malformed record identifiers and filters return validation errors. */
    @Test void invalidIdAndFiltersAreRejected() throws Exception {
        var h=new Harness();h.params.putAll(Map.of("offerId","-1","decision","ACCEPTED","csrf","known"));
        h.servlet().doPost(h.request(),h.response());
        assertEquals(400,h.status);assertFalse(h.responded);
        h=new Harness();h.params.put("keyword","x".repeat(101));
        h.servlet().doGet(h.request(),h.response());assertEquals(400,h.status);
        h=new Harness();h.params.put("status","BOGUS");
        h.servlet().doGet(h.request(),h.response());assertEquals(400,h.status);
    }
    /** The existing area filter rejects non-Teacher roles before consulting the database. */
    @Test void wrongRoleCannotEnterOfferRoute() throws Exception {
        for(var role:List.of(UserRole.ADMIN,UserRole.EXPERT,UserRole.STUDENT)) {
            var h=new Harness();h.role=role;
            new AuthFilter().doFilter(h.request(),h.response(),(req,res)->fail("Wrong role reached offer route"));
            assertEquals(403,h.status);
        }
    }
    /** Missing sessions cannot reach the list service even when calling the servlet directly. */
    @Test void missingSessionIsDenied() throws Exception {
        var h=new Harness();h.signedIn=false;
        h.servlet().doGet(h.request(),h.response());assertEquals(403,h.status);assertEquals(0,h.actor);
    }

    private static final class Harness {
        final Map<String,String> params=new HashMap<>();
        final Map<String,Object> attributes=new HashMap<>(),session=new HashMap<>(Map.of("csrf","known"));
        int status=200;long actor;boolean responded,signedIn=true;String view,redirect;UserRole role=UserRole.TEACHER;
        AssignmentOfferService.Response result=AssignmentOfferService.Response.ACCEPTED;
        /** Supply deterministic consent results without a database or external side effects. */
        AssignmentOfferServlet servlet() {
            return new AssignmentOfferServlet(new AssignmentOfferService() {
                /** Return one pending offer and one completed historical episode. */
                @Override public List<AssignmentOfferView> list(long id) {
                    actor=id;
                    return List.of(offer(20,AssignmentOfferStatus.PENDING),offer(21,AssignmentOfferStatus.DECLINED));
                }
                /** Capture which authenticated recipient reaches the response boundary. */
                @Override public Response respond(long id,long offerId,AssignmentOfferStatus decision,String note) {
                    actor=id;responded=true;return result;
                }
            });
        }
        /** Build a display row for filtering and JSP attribute assertions. */
        AssignmentOfferView offer(long id,AssignmentOfferStatus state) {
            Instant now=Instant.now();
            return new AssignmentOfferView(id,10,"SE2059","SWP391","Project","FALL2026","Expert",
                    "Overflow",state,now.minusSeconds(60),now.plusSeconds(172740),null,null,null,null,30);
        }
        /** Expose session identity, parameters and forwarding without a servlet container. */
        HttpServletRequest request() {
            var state=(HttpSession)Proxy.newProxyInstance(HttpSession.class.getClassLoader(),new Class<?>[]{HttpSession.class},(p,m,a)->switch(m.getName()) {
                case "getAttribute" -> switch((String)a[0]) {case "userId"->1L;case "role"->role;case "accountStatus"->AccountStatus.ACTIVE;default->session.get(a[0]);};
                case "setAttribute" -> {session.put((String)a[0],a[1]);yield null;}
                case "removeAttribute" -> session.remove(a[0]);default->null;
            });
            return (HttpServletRequest)Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),new Class<?>[]{HttpServletRequest.class},(p,m,a)->switch(m.getName()) {
                case "getSession" -> signedIn?state:null;
                case "getParameter" -> params.get(a[0]);
                case "setAttribute" -> {attributes.put((String)a[0],a[1]);yield null;}
                case "getContextPath" -> "/ams";
                case "getServletPath" -> "/teacher/offers";
                case "getRequestDispatcher" -> {view=(String)a[0];yield Proxy.newProxyInstance(RequestDispatcher.class.getClassLoader(),new Class<?>[]{RequestDispatcher.class},(x,y,z)->null);}
                default->null;
            });
        }
        /** Capture HTTP validation outcomes and post/redirect/get targets. */
        HttpServletResponse response() {
            return (HttpServletResponse)Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),new Class<?>[]{HttpServletResponse.class},(p,m,a)->{
                if(m.getName().equals("sendError"))status=(int)a[0];
                if(m.getName().equals("sendRedirect"))redirect=(String)a[0];
                return null;
            });
        }
    }
}
