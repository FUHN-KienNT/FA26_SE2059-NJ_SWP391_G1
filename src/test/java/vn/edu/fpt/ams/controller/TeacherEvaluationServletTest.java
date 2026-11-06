package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.*;
import vn.edu.fpt.ams.util.EssayQuestions;

class TeacherEvaluationServletTest {
    @FunctionalInterface
    interface Call {
        Object invoke(String name, Object[] args);
    }

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, Call call) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (p, m, a) -> call.invoke(m.getName(), a));
    }

    static class Exchange {
        final Map<String, String[]> parameters = new HashMap<>();
        final Map<String, Object> attributes = new HashMap<>();
        final Map<String, Object> session =
                new HashMap<>(
                        Map.of(
                                "userId",
                                7L,
                                "role",
                                UserRole.TEACHER,
                                "accountStatus",
                                AccountStatus.ACTIVE,
                                "csrf",
                                "test-token"));
        final Map<String, String> headers = new HashMap<>();
        int status = 200, reads, writes;
        long revision;
        String action, feedback, forward;
        List<SubmissionEvaluation.Mark> marks;
        RuntimeException failure;

        Exchange() {
            put("assignmentId", "101");
            put("submissionId", "55");
            put("revision", "0");
            put("csrf", "test-token");
            put("action", "saveEvaluation");
        }

        void put(String key, String value) {
            parameters.put(key, new String[] {value});
        }

        HttpSession hs =
                proxy(
                        HttpSession.class,
                        (m, a) -> m.equals("getAttribute") ? session.get(a[0]) : null);
        ServletContext context = proxy(ServletContext.class, (m, a) -> null);
        HttpServletRequest req =
                proxy(
                        HttpServletRequest.class,
                        (m, a) ->
                                switch (m) {
                                    case "getSession" -> hs;
                                    case "getContextPath" -> "/ams";
                                    case "getParameterMap" -> parameters;
                                    case "getParameterValues" -> parameters.get(a[0]);
                                    case "getParameter" ->
                                            parameters.containsKey(a[0])
                                                    ? parameters.get(a[0])[0]
                                                    : null;
                                    case "setAttribute" -> {
                                        attributes.put((String) a[0], a[1]);
                                        yield null;
                                    }
                                    case "removeAttribute" -> {
                                        attributes.remove(a[0]);
                                        yield null;
                                    }
                                    case "getAttribute" -> attributes.get(a[0]);
                                    case "getRequestDispatcher" -> {
                                        forward = (String) a[0];
                                        yield proxy(RequestDispatcher.class, (n, b) -> null);
                                    }
                                    default -> null;
                                });
        HttpServletResponse res =
                proxy(
                        HttpServletResponse.class,
                        (m, a) -> {
                            if (m.equals("sendError") || m.equals("setStatus")) status = (int) a[0];

                            if (m.equals("setHeader")) headers.put((String) a[0], (String) a[1]);

                            return null;
                        });

        TeacherSubmissionServlet servlet() {
            var evaluations =
                    new TeacherEvaluationService() {
                        @Override
                        public TeacherEvaluationPage page(
                                long teacher, long assignment, long submission) {
                            reads++;
                            assertEquals(7, teacher);
                            assertEquals(101, assignment);
                            assertEquals(55, submission);

                            return pageFixture(revision);
                        }

                        @Override
                        public void apply(
                                long teacher,
                                long assignment,
                                long submission,
                                String selected,
                                long expected,
                                List<SubmissionEvaluation.Mark> entered,
                                String comment) {
                            writes++;
                            assertEquals(7, teacher);
                            assertEquals(revision, expected);

                            if (failure != null) throw failure;

                            action = selected;
                            marks = entered;
                            feedback = comment;
                        }
                    };

            return new TeacherSubmissionServlet(new TeacherSubmissionService(), evaluations) {
                @Override
                public ServletContext getServletContext() {
                    return context;
                }
            };
        }

        void post() throws Exception {
            servlet().doPost(req, res);
        }
    }

    static TeacherEvaluationPage pageFixture(long revision) {
        var a =
                new Assignment(
                        101,
                        10,
                        1,
                        "Bài tập",
                        AssignmentPurpose.REGULAR,
                        ResponseType.ESSAY,
                        "Hướng dẫn",
                        null,
                        "[]",
                        BigDecimal.TEN,
                        AssignmentStatus.PUBLISHED,
                        Instant.EPOCH,
                        Instant.EPOCH.plusSeconds(3600),
                        null,
                        null,
                        Instant.EPOCH,
                        null);
        var context =
                new AssignmentDetailPage(
                        a, "CL-A", "Lớp A", "SWP", "Project", "FA26", "Mẫu", List.of());
        var sub =
                new Submission(
                        55,
                        101,
                        12,
                        1,
                        SubmissionStatus.SUBMITTED,
                        Instant.EPOCH,
                        Instant.EPOCH.plusSeconds(3600),
                        Instant.EPOCH.plusSeconds(100),
                        "Bài làm",
                        null,
                        "[]",
                        null,
                        null,
                        null,
                        null,
                        null);
        var tasks =
                List.of(
                        new EssayQuestions.Task(
                                1,
                                "Câu hỏi",
                                BigDecimal.TEN,
                                List.of(
                                        new EssayQuestions.Requirement(
                                                1, "Yêu cầu 1", new BigDecimal("4"), "TEXT"),
                                        new EssayQuestions.Requirement(
                                                2, "Yêu cầu 2", new BigDecimal("6"), "TEXT"))));

        return new TeacherEvaluationPage(
                new TeacherSubmissionDetailPage(
                        context,
                        new TeacherSubmissionRow(
                                sub, 8, "Sinh viên", "student@example.test", null)),
                new SubmissionEvaluation(revision, List.of(), ""),
                tasks,
                null);
    }

    @Test
    void csrfAndDuplicateIdentityAreRejectedBeforeReadingPrivateWork() throws Exception {
        for (String scenario : List.of("missing", "wrong", "duplicate")) {
            var e = new Exchange();

            if (scenario.equals("missing")) e.parameters.remove("csrf");

            if (scenario.equals("wrong")) e.put("csrf", "bad");

            if (scenario.equals("duplicate"))
                e.parameters.put("submissionId", new String[] {"55", "56"});

            e.post();
            assertEquals(scenario.equals("duplicate") ? 400 : 403, e.status);
            assertEquals(0, e.reads);
            assertEquals(0, e.writes);
        }
    }

    @Test
    void saveDraftPreservesZeroBlankAndCommentsAndRedirectsWithFilters() throws Exception {
        var e = new Exchange();

        e.put("score_1_1", "0");
        e.put("score_1_2", "");
        e.put("requirementFeedback_1_1", "Nhận xét <script>text</script>");
        e.put("feedback", "Cần bổ sung.");
        e.put("teacherId", "999");
        e.put("search", "Sinh viên & A");
        e.put("status", "SUBMITTED");
        e.put("page", "2");
        e.post();
        assertEquals(303, e.status);
        assertEquals(1, e.writes);
        assertEquals(new BigDecimal("0.00"), e.marks.get(0).score());
        assertNull(e.marks.get(1).score());
        assertEquals("Nhận xét <script>text</script>", e.marks.get(0).feedback());
        assertEquals("Cần bổ sung.", e.feedback);
        assertTrue(e.headers.get("Location").contains("&page=2"));
        assertTrue(e.headers.get("Location").contains("search=Sinh+vi%C3%AAn+%26+A"));
        assertNull(e.forward);
    }

    @Test
    void invalidScoresRemainInFormAndNeverWrite() throws Exception {
        for (String score : List.of("5+5", "-1", "0.001", "1e0", "4.01", " ")) {
            var e = new Exchange();

            e.put("score_1_1", score);
            e.put("feedback", "giữ nội dung");
            e.post();
            assertEquals(400, e.status);
            assertEquals(0, e.writes);
            assertEquals(score, ((Map<?, ?>) e.attributes.get("evaluationInput")).get("score_1_1"));
            assertEquals("/WEB-INF/views/submissions/detail.jsp", e.forward);
        }
    }

    @Test
    void forgedOrDuplicateRequirementFieldsAreRejected() throws Exception {
        var forged = new Exchange();

        forged.put("score_999_1", "0");
        forged.post();
        assertEquals(400, forged.status);
        assertEquals(0, forged.writes);
        var duplicate = new Exchange();

        duplicate.parameters.put("score_1_1", new String[] {"1", "2"});
        duplicate.post();
        assertEquals(400, duplicate.status);
        assertEquals(0, duplicate.writes);
    }

    @Test
    void staleInvalidInputCannotAcquireCurrentRevisionOnRedisplay() throws Exception {
        var e = new Exchange();

        e.revision = 2;
        e.put("score_1_1", "invalid");
        e.post();
        assertEquals(409, e.status);
        assertEquals(0, e.writes);
        assertFalse(e.attributes.containsKey("evaluationInput"));
        assertEquals(true, e.attributes.get("evaluationConflict"));
    }

    @Test
    void releaseRequiresConfirmationAndNeverUsesPostedGrades() throws Exception {
        var e = new Exchange();

        e.put("action", "releaseEvaluation");
        e.post();
        assertEquals(400, e.status);
        assertEquals(0, e.writes);
        e = new Exchange();
        e.put("action", "releaseEvaluation");
        e.put("confirmRelease", "yes");
        e.put("score_99_99", "100");
        e.put("feedback", "ignored");
        e.post();
        assertEquals(303, e.status);
        assertEquals("releaseEvaluation", e.action);
        assertTrue(e.marks.isEmpty());
        assertEquals("", e.feedback);
    }

    @Test
    void concurrentWriteAndDatabaseFailureDoNotClaimSuccess() throws Exception {
        var conflict = new Exchange();

        conflict.failure = new ConcurrentModificationException();
        conflict.post();
        assertEquals(409, conflict.status);
        assertFalse(conflict.attributes.containsKey("evaluationInput"));
        assertNull(conflict.headers.get("Location"));
        var broken = new Exchange();

        broken.put("feedback", "Giữ nhận xét.");
        broken.failure = new IllegalStateException("private sql details");
        broken.post();
        assertEquals(503, broken.status);
        assertEquals(
                "Giữ nhận xét.",
                ((Map<?, ?>) broken.attributes.get("evaluationInput")).get("feedback"));
        assertFalse(broken.attributes.get("evaluationError").toString().contains("private"));
        assertNull(broken.headers.get("Location"));
    }
}
