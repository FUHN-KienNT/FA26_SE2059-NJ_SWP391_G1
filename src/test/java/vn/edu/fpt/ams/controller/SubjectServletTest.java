package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.fpt.ams.controller.AssignmentServletTest.proxy;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.SubjectService;

class SubjectServletTest {
  private static class Exchange {
    final Map<String,String> params = new HashMap<>(Map.of("id","7","csrf","token","returnTo","edit","termId","2"));
    final Map<String,Object> attributes = new HashMap<>();
    final Map<String,Object> session = new HashMap<>(Map.of("userId",1L,"csrf","token"));
    int status = 200;
    String forward, redirect;
    final HttpSession httpSession = proxy(HttpSession.class, (method,args) -> switch(method) {
      case "getAttribute" -> session.get(args[0]);
      case "setAttribute" -> { session.put((String)args[0],args[1]); yield null; }
      default -> null;
    });
    final HttpServletRequest request = proxy(HttpServletRequest.class, (method,args) -> switch(method) {
      case "getSession" -> httpSession;
      case "getParameter" -> params.get(args[0]);
      case "getParameterValues" -> params.containsKey(args[0]) ? new String[]{params.get(args[0])} : null;
      case "getParameterNames" -> Collections.enumeration(params.keySet());
      case "getContextPath" -> "/ams";
      case "setAttribute" -> { attributes.put((String)args[0],args[1]); yield null; }
      case "getAttribute" -> attributes.get(args[0]);
      case "getRequestDispatcher" -> { forward=(String)args[0]; yield proxy(RequestDispatcher.class,(m,a)->null); }
      default -> null;
    });
    final HttpServletResponse response = proxy(HttpServletResponse.class,(method,args)->{
      if ("setStatus".equals(method)||"sendError".equals(method)) status=(Integer)args[0];
      if ("sendRedirect".equals(method)) { status=302; redirect=(String)args[0]; }
      return null;
    });
  }

  private static class Service extends SubjectService {
    List<AcademicTerm> terms = List.of(
        new AcademicTerm(1,"OLD","Old term",Instant.EPOCH,Instant.EPOCH.plusSeconds(100),false,false),
        new AcademicTerm(2,"NOW","Current term",Instant.EPOCH,Instant.EPOCH.plusSeconds(100),true,true));
    int updates, statuses, registrations, experts, lists, creates;
    Long listTerm;
    SubjectTerm saved;
    SubjectStatus target;
    InitialTerm initialTerm;
    @Override public List<AcademicTerm> termOptions(long actor) { return terms; }
    @Override public List<AcademicTerm> eligibleTerms(long actor) { return terms.stream().filter(AcademicTerm::active).toList(); }
    @Override public long create(long actor,SubjectDTO request,Map<Long,ResponsibilityType> assignments,InitialTerm opening) {
      creates++; initialTerm=opening; return 7;
    }
    @Override public SubjectDetailsDTO details(long actor,long id) {
      return new SubjectDetailsDTO(new SubjectDTO(id,"SWP391","Project","Description",SubjectStatus.ACTIVE),List.of(),List.of(),List.of(),List.of());
    }
    @Override public List<ExpertOptionDTO> eligibleExperts(long actor,String keyword) { return List.of(); }
    @Override public void update(long actor,long id,String code,String name,String description) { updates++; }
    @Override public void changeStatus(long actor,long id,SubjectStatus value) {
      if(value==null) throw new IllegalArgumentException("Trạng thái môn học không hợp lệ.");
      statuses++; target=value;
    }
    @Override public long openForTerm(long actor,SubjectTerm value) { registrations++; saved=value; return 9; }
    @Override public void assignExperts(long actor,long id,Map<Long,ResponsibilityType> values) { experts++; }
    @Override public List<SubjectSummaryDTO> list(long actor,String keyword,SubjectStatus status,Long term,int page) { lists++; listTerm=term; return List.of(); }
    @Override public long count(long actor,String keyword,SubjectStatus status,Long term) { return 0; }
    int writes() { return updates+statuses+registrations+experts+creates; }
  }

  @Test void editLoadsEligibleTermAndHistoricalEditUrlRedirectsToView() throws Exception {
    for (Long term : Arrays.asList(null,1L)) {
      var x=new Exchange(); var s=new Service(); x.params.put("mode","edit");
      if(term==null) x.params.remove("termId"); else x.params.put("termId",term.toString());
      new SubjectServlet(s).doGet(x.request,x.response);
      if(term==null) assertEquals("/WEB-INF/views/subjects/form.jsp",x.forward);
      else {
        assertEquals("/ams/admin/subjects?mode=view&id=7",x.redirect);
        assertNull(x.forward);
      }
      assertEquals(term==null?2L:term,x.attributes.get("editTermId"));
      if(term==null) assertEquals(1,((List<?>)x.attributes.get("editableTerms")).size());
      assertEquals(0,s.writes());
    }
  }

  @Test void unifiedListOnlyFiltersByExplicitTermAndLegacyTabDoesNotHideSubjects() throws Exception {
    var x=new Exchange(); var s=new Service(); x.params.clear(); x.params.put("tab","terms");
    new SubjectServlet(s).doGet(x.request,x.response);
    assertNull(s.listTerm); assertEquals(1,s.lists);
    x.params.put("termId","1"); new SubjectServlet(s).doGet(x.request,x.response);
    assertEquals(1L,s.listTerm); x.params.remove("termId");
    assertEquals(true,x.attributes.get("selectedTermReadOnly"));
    s.terms=List.of(); s.lists=0;
    new SubjectServlet(s).doGet(x.request,x.response);
    assertEquals(1,s.lists); assertEquals(0L,x.attributes.get("total"));
  }

  @Test void statusNeedsConfirmationBeforeInvokingAnyWrite() throws Exception {
    for (String confirmed : Arrays.asList(null,"false","TRUE","invalid")) {
      var x=new Exchange(); var s=new Service(); x.params.put("action","status"); x.params.put("status","INACTIVE");
      if(confirmed!=null) x.params.put("confirmed",confirmed);
      new SubjectServlet(s).doPost(x.request,x.response);
      assertEquals(400,x.status); assertEquals(0,s.writes());
      assertEquals("/WEB-INF/views/subjects/form.jsp",x.forward);
    }
  }

  @Test void confirmedActivationAndDeactivationUseExistingStatusServiceAndReturnToEdit() throws Exception {
    for(String target:List.of("ACTIVE","INACTIVE")) {
      var x=new Exchange(); var s=new Service(); x.params.put("action","status");
      x.params.put("status",target); x.params.put("confirmed","true");
      new SubjectServlet(s).doPost(x.request,x.response);
      assertEquals(1,s.writes()); assertEquals(SubjectStatus.valueOf(target),s.target);
      assertEquals("/ams/admin/subjects?mode=edit&id=7&termId=2#subject-status",x.redirect);
    }
  }

  @Test void invalidStatusAndMissingCsrfNeverWrite() throws Exception {
    for(String target:List.of("","inactive","INVALID")) {
      var x=new Exchange(); var s=new Service(); x.params.put("action","status");
      x.params.put("status",target); x.params.put("confirmed","true");
      new SubjectServlet(s).doPost(x.request,x.response);
      assertEquals(400,x.status); assertEquals(0,s.writes());
    }
    var x=new Exchange(); var s=new Service(); x.params.put("action","status");
    x.params.put("status","INACTIVE"); x.params.put("confirmed","true"); x.params.remove("csrf");
    new SubjectServlet(s).doPost(x.request,x.response);
    assertEquals(403,x.status); assertEquals(0,s.writes());
  }

  @Test void termSaveKeepsChosenTermAndConvertsUtcPlusSevenWithoutChangingSubjectStatus() throws Exception {
    var x=new Exchange(); var s=new Service(); x.params.put("action","term");
    x.params.put("registrationOpensAt","2026-12-01T07:00"); x.params.put("registrationClosesAt","2026-12-25T23:59");
    new SubjectServlet(s).doPost(x.request,x.response);
    assertEquals(1,s.writes()); assertEquals(2,s.saved.termId()); assertFalse(s.saved.registrationEnabled());
    assertEquals(Instant.parse("2026-12-01T00:00:00Z"),s.saved.registrationOpensAt());
    assertEquals("/ams/admin/subjects?mode=edit&id=7&termId=2#subject-registration",x.redirect);
    assertEquals("term",x.session.get("subjectEditNoticeAction"));
    assertNotNull(x.session.get("notice"));
  }

  @Test void invalidDatesRedisplayEditWithoutWritingOrRedirecting() throws Exception {
    var x=new Exchange(); var s=new Service(); x.params.put("action","term");
    x.params.put("registrationOpensAt","invalid"); x.params.put("registrationClosesAt","2026-12-25T23:59");
    new SubjectServlet(s).doPost(x.request,x.response);
    assertEquals(400,x.status); assertEquals(0,s.writes()); assertNull(x.redirect);
    assertEquals("/WEB-INF/views/subjects/form.jsp",x.forward); assertEquals(true,x.attributes.get("submitted"));
    assertNotNull(x.attributes.get("error"));
    assertNull(x.session.get("subjectEditNoticeAction"));
  }

  @Test void metadataAndExpertSavesDoNotInvokeOtherOperations() throws Exception {
    for (String action:List.of("update","experts")) {
      var x=new Exchange(); var s=new Service(); x.params.put("action",action);
      x.params.put("subjectCode","SWP391"); x.params.put("subjectName","Project");
      x.params.put("responsibility_2","PRIMARY");
      new SubjectServlet(s).doPost(x.request,x.response);
      assertEquals(1,s.writes()); assertEquals(0,s.statuses); assertEquals(0,s.registrations);
      assertEquals(302,x.status); assertTrue(x.redirect.contains("mode=edit&id=7&termId=2#subject-"));
    }
  }

  @Test void invalidReturnTermIsValidatedBeforeAnyStatusWrite() throws Exception {
    for(String term:List.of("invalid","0","999")) {
      var x=new Exchange(); var s=new Service(); x.params.put("termId",term); x.params.put("action","status");
      x.params.put("status","INACTIVE"); x.params.put("confirmed","true");
      new SubjectServlet(s).doPost(x.request,x.response);
      assertEquals(400,x.status); assertEquals(0,s.writes());
    }
  }

  @Test void createWithOpeningUsesOneServiceCallAndReturnsTheCreatedSubject() throws Exception {
    var x=new Exchange(); var s=new Service(); x.params.remove("returnTo"); x.params.put("action","create");
    x.params.put("subjectCode","NEW"); x.params.put("subjectName","New subject"); x.params.put("initialStatus","ACTIVE");
    x.params.put("primaryExperts","2"); x.params.put("openForTerm","true"); x.params.put("registrationEnabled","true");
    x.params.put("registrationOpensAt","2026-12-01T07:00"); x.params.put("registrationClosesAt","2026-12-25T23:59");
    new SubjectServlet(s).doPost(x.request,x.response);
    assertEquals(1,s.creates); assertEquals(1,s.writes()); assertEquals(0,s.registrations);
    assertEquals(2,s.initialTerm.termId()); assertTrue(s.initialTerm.registrationEnabled());
    assertEquals(Instant.parse("2026-12-01T00:00:00Z"),s.initialTerm.registrationOpensAt());
    assertEquals("/ams/admin/subjects?mode=view&id=7",x.redirect);
    assertEquals("Đã tạo môn học và thiết lập đăng ký theo học kỳ.",x.session.get("notice"));
  }

  @Test void invalidOpeningDatesRedisplayCreateWithoutCallingCreate() throws Exception {
    var x=new Exchange(); var s=new Service(); x.params.remove("returnTo"); x.params.put("action","create");
    x.params.put("openForTerm","true"); x.params.put("registrationOpensAt","invalid");
    new SubjectServlet(s).doPost(x.request,x.response);
    assertEquals(400,x.status); assertEquals(0,s.writes());
    assertEquals("/WEB-INF/views/subjects/form.jsp",x.forward);
    assertEquals(true,x.attributes.get("submitted"));
  }

  @Test void uncheckedOpeningIgnoresUnusedDateFields() throws Exception {
    var x=new Exchange(); var s=new Service(); x.params.remove("returnTo"); x.params.put("action","create");
    x.params.put("initialStatus","ACTIVE"); x.params.put("registrationOpensAt","unused-invalid");
    new SubjectServlet(s).doPost(x.request,x.response);
    assertEquals(1,s.creates); assertNull(s.initialTerm); assertEquals(302,x.status);
  }

  @Test void currentTermListKeepsEditActionEvenIfItsStoredEndDateIsPast() throws Exception {
    var x=new Exchange(); var s=new Service(); x.params.clear(); x.params.put("termId","2");
    new SubjectServlet(s).doGet(x.request,x.response);
    assertEquals(false,x.attributes.get("selectedTermReadOnly"));
    assertEquals("/WEB-INF/views/subjects/list.jsp",x.forward);
    assertEquals(0,s.writes());
  }
}
