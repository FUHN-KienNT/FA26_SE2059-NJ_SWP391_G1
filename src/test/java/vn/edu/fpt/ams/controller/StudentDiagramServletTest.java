package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.StudentObjectivePage;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.*;

class StudentDiagramServletTest {
    private static final String TASKS =
            "[{\"task_order\":1,\"title\":\"Câu 1\",\"maximum_points\":5,\"requirements\":[{\"requirement_order\":1,\"title\":\"Vẽ\",\"maximum_points\":5,\"answer_type\":\"DIAGRAM\"}]}]";

    static class Exchange {
        Map<String, Object> session =
                new HashMap<>(
                        Map.of(
                                "userId",
                                4L,
                                "role",
                                UserRole.STUDENT,
                                "accountStatus",
                                AccountStatus.ACTIVE,
                                "csrf",
                                "valid"));
        Map<String, String[]> params = new HashMap<>();
        int status = 200;
        StringWriter body = new StringWriter();

        Exchange() {
            for (var pair :
                    Map.of(
                                    "csrf",
                                    "valid",
                                    "assignmentId",
                                    "50",
                                    "submissionId",
                                    "8",
                                    "taskOrder",
                                    "1",
                                    "requirementOrder",
                                    "1",
                                    "source",
                                    "A -> B: Nộp bài")
                            .entrySet()) params.put(pair.getKey(), new String[] {pair.getValue()});
        }

        HttpSession hs =
                (HttpSession)
                        Proxy.newProxyInstance(
                                HttpSession.class.getClassLoader(),
                                new Class<?>[] {HttpSession.class},
                                (p, m, a) ->
                                        m.getName().equals("getAttribute")
                                                ? session.get(a[0])
                                                : null);
        HttpServletRequest request =
                (HttpServletRequest)
                        Proxy.newProxyInstance(
                                HttpServletRequest.class.getClassLoader(),
                                new Class<?>[] {HttpServletRequest.class},
                                (p, m, a) ->
                                        switch (m.getName()) {
                                            case "getSession" -> hs;
                                            case "getParameterValues" -> params.get(a[0]);
                                            case "getParameter" ->
                                                    params.containsKey(a[0])
                                                            ? params.get(a[0])[0]
                                                            : null;
                                            case "getContentLengthLong" -> 0L;
                                            default -> null;
                                        });
        HttpServletResponse response =
                (HttpServletResponse)
                        Proxy.newProxyInstance(
                                HttpServletResponse.class.getClassLoader(),
                                new Class<?>[] {HttpServletResponse.class},
                                (p, m, a) -> {
                                    if (m.getName().equals("setStatus")) status = (int) a[0];

                                    if (m.getName().equals("getWriter"))
                                        return new PrintWriter(body);

                                    return null;
                                });
    }

    static StudentObjectivePage page(SubmissionStatus status) {
        var now = Instant.now();
        var a =
                new Assignment(
                        50,
                        1,
                        1,
                        "Đề",
                        AssignmentPurpose.REGULAR,
                        ResponseType.ESSAY,
                        "",
                        "",
                        TASKS,
                        new BigDecimal("5"),
                        AssignmentStatus.PUBLISHED,
                        now.minusSeconds(60),
                        now.plusSeconds(3600),
                        null,
                        1,
                        now.minusSeconds(60),
                        null);

        return new StudentObjectivePage(
                a, "Lớp", 8, 1, status, now.plusSeconds(300), List.of(), now, Map.of(), 0, null);
    }

    @Test
    void sessionIdentityAndRequirementAreRecheckedAfterRendering() throws Exception {
        int[] calls = {0};
        var ex = new Exchange();

        ex.params.put("studentId", new String[] {"99"});
        var service =
                new StudentObjectiveService() {
                    @Override
                    public StudentObjectivePage page(long s, long a, long sub) {
                        assertEquals(4, s);
                        assertEquals(50, a);
                        assertEquals(8, sub);
                        calls[0]++;

                        return pageData();
                    }

                    private StudentObjectivePage pageData() {
                        return StudentDiagramServletTest.page(SubmissionStatus.IN_PROGRESS);
                    }
                };
        var renderer =
                new PlantUmlRenderer() {
                    @Override
                    public String render(String s) {
                        assertTrue(s.contains("Nộp bài"));

                        return "data:image/png;base64,test";
                    }
                };

        new StudentDiagramServlet(service, renderer).doPost(ex.request, ex.response);
        assertEquals(200, ex.status);
        assertEquals(2, calls[0]);
        assertTrue(ex.body.toString().contains("image"));
        calls[0] = 0;
        ex = new Exchange();
        var expires =
                new StudentObjectiveService() {
                    @Override
                    public StudentObjectivePage page(long s, long a, long sub) {
                        return StudentDiagramServletTest.page(
                                ++calls[0] == 1
                                        ? SubmissionStatus.IN_PROGRESS
                                        : SubmissionStatus.SUBMITTED);
                    }
                };

        new StudentDiagramServlet(expires, renderer).doPost(ex.request, ex.response);
        assertEquals(409, ex.status);
        assertFalse(ex.body.toString().contains("image"));
    }

    @Test
    void unauthorizedAndUneditableRequestsNeverInvokeRenderer() throws Exception {
        for (String mode :
                List.of(
                        "csrf",
                        "teacher",
                        "inactive",
                        "duplicate",
                        "foreign",
                        "submitted",
                        "requirement")) {
            var ex = new Exchange();

            if (mode.equals("csrf")) ex.params.put("csrf", new String[] {"bad"});

            if (mode.equals("teacher")) ex.session.put("role", UserRole.TEACHER);

            if (mode.equals("inactive")) ex.session.put("accountStatus", AccountStatus.INACTIVE);

            if (mode.equals("duplicate")) ex.params.put("submissionId", new String[] {"8", "9"});

            if (mode.equals("requirement")) ex.params.put("requirementOrder", new String[] {"2"});

            var service =
                    new StudentObjectiveService() {
                        @Override
                        public StudentObjectivePage page(long s, long a, long sub) {
                            if (mode.equals("foreign"))
                                throw new NoSuchElementException("Không có bài làm.");

                            return StudentDiagramServletTest.page(
                                    mode.equals("submitted")
                                            ? SubmissionStatus.SUBMITTED
                                            : SubmissionStatus.IN_PROGRESS);
                        }
                    };

            new StudentDiagramServlet(
                            service,
                            new PlantUmlRenderer() {
                                @Override
                                public String render(String s) {
                                    fail("Must not render: " + mode);

                                    return null;
                                }
                            })
                    .doPost(ex.request, ex.response);
            assertEquals(
                    switch (mode) {
                        case "csrf", "teacher", "inactive" -> 403;
                        case "foreign" -> 404;
                        case "submitted" -> 409;
                        default -> 400;
                    },
                    ex.status,
                    mode);
        }
    }
}
