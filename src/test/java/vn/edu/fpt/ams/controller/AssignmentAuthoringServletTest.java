package vn.edu.fpt.ams.controller;

import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.fpt.ams.controller.AssignmentServletTest.proxy;
import com.google.gson.*;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dto.AssignmentCreatePage;
import vn.edu.fpt.ams.integration.GeminiAssignmentClient;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.AssignmentAuthoringService;
import vn.edu.fpt.ams.util.AssignmentDocumentReader;

class AssignmentAuthoringServletTest {
    static class Exchange {
        final Map<String, String> params =
                new HashMap<>(
                        Map.of(
                                "action",
                                "import",
                                "classId",
                                "10",
                                "csrf",
                                "token",
                                "maximumScore",
                                "10"));
        final Map<String, Object> session =
                new HashMap<>(
                        Map.of(
                                "userId",
                                1L,
                                "role",
                                UserRole.TEACHER,
                                "accountStatus",
                                AccountStatus.ACTIVE,
                                "csrf",
                                "token"));
        final Map<String, String> headers = new HashMap<>();
        final StringWriter output = new StringWriter();
        final ByteArrayOutputStream binary = new ByteArrayOutputStream();
        final ServletOutputStream binaryOutput =
                new ServletOutputStream() {
                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(WriteListener listener) {}

                    @Override
                    public void write(int value) {
                        binary.write(value);
                    }
                };
        List<Part> parts = new ArrayList<>();
        int status = 200, partsRead;
        boolean anonymous;
        long length = 100;
        String contentType;
        HttpSession httpSession =
                proxy(
                        HttpSession.class,
                        (m, a) -> "getAttribute".equals(m) ? session.get(a[0]) : null);
        HttpServletRequest request =
                proxy(
                        HttpServletRequest.class,
                        (m, a) ->
                                switch (m) {
                                    case "getSession" -> anonymous ? null : httpSession;
                                    case "getParameter" -> params.get(a[0]);
                                    case "getContentType" -> "multipart/form-data; boundary=Test";
                                    case "getContentLengthLong" -> length;
                                    case "getParts" -> {
                                        partsRead++;
                                        yield parts;
                                    }
                                    default -> null;
                                });
        HttpServletResponse response =
                proxy(
                        HttpServletResponse.class,
                        (m, a) -> {
                            if (m.equals("setStatus")) status = (Integer) a[0];

                            if (m.equals("setHeader")) headers.put((String) a[0], (String) a[1]);

                            if (m.equals("setContentType")) contentType = (String) a[0];

                            if (m.equals("getOutputStream")) return binaryOutput;

                            return m.equals("getWriter") ? new PrintWriter(output) : null;
                        });

        void file(String name, byte[] bytes) {
            parts.add(
                    proxy(
                            Part.class,
                            (m, a) ->
                                    switch (m) {
                                        case "getSubmittedFileName" -> name;
                                        case "getName" -> "files";
                                        case "getSize" -> (long) bytes.length;
                                        case "getInputStream" -> new ByteArrayInputStream(bytes);
                                        default -> null;
                                    }));
        }
    }

    static class Service extends AssignmentAuthoringService {
        int checks, imports, generates;
        RuntimeException failure;

        @Override
        public AssignmentCreatePage authorize(long teacher, long classId) {
            checks++;
            assertEquals(1, teacher);
            assertEquals(10, classId);

            if (failure != null) throw failure;

            return null;
        }

        @Override
        public JsonArray importDocuments(
                long teacher, long classId, String score, List<Document> files) {
            imports++;
            assertEquals(1, files.size());
            assertEquals("10", score);

            return new AssignmentDocumentReader()
                    .parseQuestions(AssignmentDocumentReader.template());
        }

        @Override
        public JsonArray generate(
                long teacher,
                long classId,
                String score,
                String questionCount,
                String requirementCount,
                String context,
                String prompt,
                List<Document> files) {
            generates++;

            return new AssignmentDocumentReader()
                    .parseQuestions(AssignmentDocumentReader.template());
        }
    }

    private AssignmentAuthoringServlet servlet(Service service) {
        return new AssignmentAuthoringServlet(service) {
            @Override
            public ServletContext getServletContext() {
                return proxy(ServletContext.class, (m, a) -> null);
            }
        };
    }

    @Test
    void requiresActiveTeacherCsrfAndClassPermissionBeforeReadingFiles() throws Exception {
        for (String scenario : List.of("anonymous", "inactive", "student", "csrf", "class")) {
            var e = new Exchange();
            var service = new Service();

            if (scenario.equals("anonymous")) e.anonymous = true;

            if (scenario.equals("inactive")) e.session.put("accountStatus", AccountStatus.INACTIVE);

            if (scenario.equals("student")) e.session.put("role", UserRole.STUDENT);

            if (scenario.equals("csrf")) e.params.put("csrf", "wrong");

            if (scenario.equals("class")) service.failure = new SecurityException("Forbidden");

            servlet(service).doPost(e.request, e.response);
            assertEquals(
                    scenario.equals("anonymous") || scenario.equals("inactive") ? 401 : 403,
                    e.status);
            assertEquals(0, e.partsRead);
            assertEquals(0, service.imports + service.generates);
            assertTrue(JsonParser.parseString(e.output.toString()).getAsJsonObject().has("error"));
        }
    }

    @Test
    void successfulImportReturnsProposalAndTemplateIsUtf8Download() throws Exception {
        var e = new Exchange();
        var service = new Service();

        e.file(
                "questions.txt",
                AssignmentDocumentReader.template().getBytes(StandardCharsets.UTF_8));
        servlet(service).doPost(e.request, e.response);
        assertEquals(200, e.status);
        assertEquals(1, service.imports);
        assertEquals(
                2,
                JsonParser.parseString(e.output.toString())
                        .getAsJsonObject()
                        .getAsJsonArray("tasks")
                        .size());
        assertEquals("no-store", e.headers.get("Cache-Control"));
        var template = new Exchange();

        template.params.put("action", "template");
        servlet(service).doGet(template.request, template.response);
        assertEquals(200, template.status);
        assertTrue(template.contentType.contains("UTF-8"));
        assertTrue(template.headers.get("Content-Disposition").contains("attachment"));
        assertTrue(template.output.toString().contains("[DIAGRAM]"));
    }

    @Test
    void rejectsOversizeAndTooManyFilesWithoutProcessing() throws Exception {
        var service = new Service();
        var big = new Exchange();

        big.length = 7 * 1024 * 1024;
        servlet(service).doPost(big.request, big.response);
        assertEquals(413, big.status);
        assertEquals(0, big.partsRead);
        var bigFile = new Exchange();

        bigFile.file("big.txt", new byte[AssignmentDocumentReader.MAX_FILE_BYTES + 1]);
        servlet(service).doPost(bigFile.request, bigFile.response);
        assertEquals(413, bigFile.status);
        var many = new Exchange();

        for (int i = 0; i < 4; i++) many.file("file.txt", new byte[] {1});

        servlet(service).doPost(many.request, many.response);
        assertEquals(400, many.status);
        assertEquals(0, service.imports);
    }

    @Test
    void excelTemplateDownloadRequiresClassAccessAndStreamsThePackagedFile() throws Exception {
        byte[] templateBytes = {'P', 'K', 3, 4, 42};
        int[] opened = {0};
        var service = new Service();
        var endpoint =
                new AssignmentAuthoringServlet(service) {
                    @Override
                    public ServletContext getServletContext() {
                        return proxy(
                                ServletContext.class,
                                (method, args) -> {
                                    if (!method.equals("getResourceAsStream")) return null;

                                    assertEquals(
                                            "/WEB-INF/templates/essay-import-template.xlsx",
                                            args[0]);
                                    opened[0]++;

                                    return new ByteArrayInputStream(templateBytes);
                                });
                    }
                };
        var download = new Exchange();

        download.params.put("action", "template");
        download.params.put("format", "xlsx");
        endpoint.doGet(download.request, download.response);
        assertEquals(200, download.status);
        assertEquals(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                download.contentType);
        assertTrue(
                download.headers.get("Content-Disposition").contains("mau-cau-hoi-tu-luan.xlsx"));
        assertArrayEquals(templateBytes, download.binary.toByteArray());
        assertEquals(1, service.checks);
        assertEquals(1, opened[0]);

        service.failure = new SecurityException("Forbidden");
        var denied = new Exchange();

        denied.params.put("action", "template");
        denied.params.put("format", "xlsx");
        endpoint.doGet(denied.request, denied.response);
        assertEquals(403, denied.status);
        assertEquals(1, opened[0]);
        assertEquals(0, denied.binary.size());

        service.failure = null;
        var invalid = new Exchange();

        invalid.params.put("action", "template");
        invalid.params.put("format", "../../outside");
        endpoint.doGet(invalid.request, invalid.response);
        assertEquals(400, invalid.status);
        assertEquals(1, opened[0]);
    }

    @Test
    void serviceFailuresUseSafeJsonStatusCodes() throws Exception {
        for (RuntimeException failure :
                List.of(
                        new NoSuchElementException(),
                        new IllegalStateException("private database details"),
                        new GeminiAssignmentClient.UnavailableException("API key unavailable"),
                        new GeminiAssignmentClient.ProviderException("Malformed Gemini output"))) {
            var e = new Exchange();
            var service = new Service();

            service.failure = failure;
            servlet(service).doPost(e.request, e.response);
            assertEquals(
                    failure instanceof NoSuchElementException
                            ? 404
                            : failure instanceof GeminiAssignmentClient.ProviderException
                                    ? 502
                                    : 503,
                    e.status);
            assertFalse(e.output.toString().contains("private database details"));
        }
    }
}
