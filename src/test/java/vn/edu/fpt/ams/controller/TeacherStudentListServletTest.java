package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.*;

class TeacherStudentListServletTest {
    /** Filters apply to the list without broadening the authenticated teacher's scope. */
    @Test void filtersAndReturnLinkPreserveContext() throws Exception {
        var h = new Harness();
        h.params.putAll(Map.of("keyword", "software", "term", "FALL2026"));
        h.classServlet().doGet(h.request(), h.response());
        assertEquals(1, ((List<?>) h.attributes.get("classes")).size());
        assertEquals("software", h.attributes.get("keyword"));
        assertEquals(7, h.teacher);
        assertEquals("/WEB-INF/views/teacher/classes/list.jsp", h.view);
    }

    /** Class details load without fetching the roster. */
    @Test void classDetailLoadsOnlySelectedClass() throws Exception {
        var h = new Harness();
        h.params.put("classId", "1");
        h.path = "/teacher/classes/detail";
        h.classServlet().doGet(h.request(), h.response());
        assertEquals(1, ((TeacherClassSummary) h.attributes.get("selected")).classId());
        assertEquals(0, h.rosterReads);
        assertEquals("/WEB-INF/views/teacher/classes/detail.jsp", h.view);
    }
    /** Foreign and malformed class identifiers must never load another roster. */
    @Test void invalidAndForeignClassIdsAreRejected() throws Exception {
        for (String id : List.of("999", "0", "-1", "abc")) {
            var h = new Harness(); h.params.put("classId", id);
            h.servlet().doGet(h.request(), h.response());
            assertEquals(400, h.status); assertEquals(0, h.rosterReads);
        }
    }

    /** Add validation returns to its popup and retains the submitted email. */
    @Test void invalidEmailReopensAddPopup() throws Exception {
        var h = new Harness();
        h.params.putAll(Map.of("classId", "1", "action", "addStudent", "email", "missing@example.test"));
        h.servlet().doPost(h.request(), h.response());
        assertEquals("addStudent", h.attributes.get("openDialog"));
        assertEquals("missing@example.test", h.attributes.get("submittedEmail"));
        assertEquals("/WEB-INF/views/teacher/students/list.jsp", h.view);
    }

    /** Missing uploads return to the import popup without changing enrollment. */
    @Test void missingCsvReopensImportPopup() throws Exception {
        var h = new Harness();
        h.params.putAll(Map.of("classId", "1", "action", "importStudents"));
        h.servlet().doPost(h.request(), h.response());
        assertEquals("importStudents", h.attributes.get("openDialog"));
        assertNotNull(h.attributes.get("error"));
    }

    /** Status writes cannot use an enrollment identifier from another class. */
    @Test void foreignEnrollmentCannotBeUpdated() throws Exception {
        var h = new Harness();
        h.params.putAll(Map.of("classId", "1", "action", "updateStatus", "enrollmentId", "99", "status", "ACTIVE"));
        h.servlet().doPost(h.request(), h.response());
        assertFalse(h.updated); assertNotNull(h.attributes.get("error"));
    }

    /** A completed import is delivered once, only after resolving its owned class. */
    @Test void importDetailsAreScopedToTheSelectedClassAndConsumedOnce() throws Exception {
        var h = new Harness();
        var result = new ImportResultDTO(1, 0, 0, List.of(
                new ImportLineResult(2, "one@example.test", ImportLineResult.Outcome.IMPORTED, "Đã thêm sinh viên vào lớp.")));
        h.session.put("enrollmentImportResult:1", result);
        h.session.put("enrollmentImportResult:2", result);
        h.params.putAll(Map.of("classId", "1"));
        h.servlet().doGet(h.request(), h.response());
        assertSame(result, h.attributes.get("importResult"));
        assertFalse(h.session.containsKey("enrollmentImportResult:1"));
        assertTrue(h.session.containsKey("enrollmentImportResult:2"));
        h.attributes.clear();
        h.servlet().doGet(h.request(), h.response());
        assertFalse(h.attributes.containsKey("importResult"));
    }

    /** Search and status filters retain the selected class boundary. */
    @Test void studentFiltersUseNameEmailAndEnrollmentStatus() throws Exception {
        var h = new Harness();
        h.params.putAll(Map.of("classId", "1", "keyword", "ONE@", "enrollmentStatus", "ACTIVE"));
        h.servlet().doGet(h.request(), h.response());
        assertEquals(1, ((List<?>) h.attributes.get("students")).size());
        h.params.put("enrollmentStatus", "INACTIVE");
        h.servlet().doGet(h.request(), h.response());
        assertTrue(((List<?>) h.attributes.get("students")).isEmpty());
    }

    /** Details resolve the enrollment only inside the assigned class roster. */
    @Test void studentDetailRejectsForeignEnrollmentAndLoadsOwnedStudent() throws Exception {
        var h = new Harness();
        h.path = "/teacher/students/detail";
        h.params.putAll(Map.of("classId", "1", "enrollmentId", "99"));
        h.servlet().doGet(h.request(), h.response());
        assertEquals(400, h.status);
        h.status = 200;
        h.params.put("enrollmentId", "5");
        h.servlet().doGet(h.request(), h.response());
        assertEquals("/WEB-INF/views/teacher/students/detail.jsp", h.view);
        assertEquals(10, ((EnrollmentDTO) h.attributes.get("student")).studentId());
    }

    /** Reject an expired or forged form token before any enrollment mutation. */
    @Test void rejectsInvalidCsrf() throws Exception {
        var h = new Harness();
        h.params.putAll(Map.of("classId", "1", "action", "updateStatus", "enrollmentId", "5", "status", "INACTIVE", "csrf", "wrong"));
        h.servlet().doPost(h.request(), h.response());
        assertEquals(403, h.status);
        assertFalse(h.updated);
    }
    private static final class Harness {
        final Map<String,String> params = new HashMap<>();
        final Map<String,Object> attributes = new HashMap<>(), session = new HashMap<>();
        /** Set a valid session token for enrollment form submissions. */
        Harness() {
            params.put("csrf", "fixture-token");
            session.put("csrf", "fixture-token");
        }
        long teacher; int status=200, rosterReads; String view; String path = "/teacher/classes"; boolean updated;
        /** Supply deterministic classes and enrollment failures without a live database. */
        TeacherStudentListServlet servlet() {
            return new TeacherStudentListServlet(new EnrollmentService() {
                /** Read a roster only for the owned fixture class. */
                @Override public List<EnrollmentDTO> listStudents(long actor,long id) {
                    assertEquals(1,id); rosterReads++; return List.of(new EnrollmentDTO(5, 3, 1, 10, "Student One", "one@example.test", EnrollmentStatus.ACTIVE));
                }
                /** Return no pending invitations for the fixture. */
                @Override public List<StudentInvitation> listInvitations(long actor,long id) { return List.of(); }
                /** Simulate an unknown account to exercise form validation. */
                @Override public long addStudentByEmail(long actor,long id,String email) { throw new IllegalArgumentException("Không có tài khoản sử dụng email này"); }
                /** Record whether an unauthorized status write reaches the service. */
                @Override public void updateEnrollmentStatus(long actor,long id,EnrollmentStatus state) { updated=true; }
            }, dashboardService(), new TeacherGroupService() {
                @Override public List<TeacherGroup> list(long actor, long classId) {
                    return List.of();
                }
            });
        }
        /** Build the class screens with the same Teacher ownership fixture. */
        TeacherClassServlet classServlet() {
            return new TeacherClassServlet(dashboardService());
        }
        /** Provide only the assigned class. */
        TeacherDashboardService dashboardService() {
            return new TeacherDashboardService() {
                /** Return only classes owned by the authenticated fixture teacher. */
                @Override public List<TeacherClassSummary> assignedClasses(long id) {
                    teacher=id; return List.of(new TeacherClassSummary(1,"SE2059","Class One","SWP391","Software Project","FALL2026",30,0,0,0,"ACTIVE"));
                }
            };
        }
        /** Provide request parameters, attributes, session identity and dispatch capture. */
        HttpServletRequest request() {
            HttpSession state=(HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(),new Class<?>[]{HttpSession.class},(p,m,a)->switch(m.getName()) {
                case "getAttribute" -> a[0].equals("userId")?7L:session.get(a[0]);
                case "setAttribute" -> { session.put((String)a[0],a[1]); yield null; }
                case "removeAttribute" -> session.remove(a[0]); default -> null;
            });
            return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),new Class<?>[]{HttpServletRequest.class},(p,m,a)->switch(m.getName()) {
                case "getServletPath" -> path; case "getSession" -> state; case "getParameter" -> params.get(a[0]);
                case "setAttribute" -> { attributes.put((String)a[0],a[1]); yield null; }
                case "getRequestDispatcher" -> { view=(String)a[0]; yield Proxy.newProxyInstance(RequestDispatcher.class.getClassLoader(),new Class<?>[]{RequestDispatcher.class},(d,n,b)->null); }
                default -> null;
            });
        }
        /** Capture rejected HTTP requests without a servlet container. */
        HttpServletResponse response() {
            return (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),new Class<?>[]{HttpServletResponse.class},(p,m,a)->{if(m.getName().equals("sendError"))status=(int)a[0];return null;});
        }
    }
}
