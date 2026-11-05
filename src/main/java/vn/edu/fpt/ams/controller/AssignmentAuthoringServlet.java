package vn.edu.fpt.ams.controller;

import com.google.gson.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import vn.edu.fpt.ams.integration.GeminiAssignmentClient;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.AssignmentAuthoringService;
import vn.edu.fpt.ams.util.*;

@WebServlet("/teacher/assignment-authoring")
@MultipartConfig(
        fileSizeThreshold = 0,
        maxFileSize = 2 * 1024 * 1024,
        maxRequestSize = 6 * 1024 * 1024 + 256 * 1024)
public class AssignmentAuthoringServlet extends HttpServlet {
    private static final long MAX_REQUEST = 6 * 1024 * 1024 + 256 * 1024;
    private final AssignmentAuthoringService service;

    public AssignmentAuthoringServlet() {
        this(new AssignmentAuthoringService());
    }

    AssignmentAuthoringServlet(AssignmentAuthoringService service) {
        this.service = service;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        responseHeaders(res);
        Long teacher = activeTeacher(req, res);

        if (teacher == null) return;

        try {
            if (!"template".equals(req.getParameter("action")))
                throw new IllegalArgumentException("Thao tác không hợp lệ.");

            service.authorize(teacher, WebSupport.id(req.getParameter("classId")));
            String format = req.getParameter("format");

            if ("xlsx".equals(format)) {
                try (InputStream template =
                        getServletContext()
                                .getResourceAsStream(
                                        "/WEB-INF/templates/essay-import-template.xlsx")) {
                    if (template == null)
                        throw new IllegalStateException("Excel template is unavailable");

                    res.setContentType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    res.setHeader(
                            "Content-Disposition", "attachment; filename=mau-cau-hoi-tu-luan.xlsx");
                    template.transferTo(res.getOutputStream());
                }
            } else if (format == null || format.isBlank() || "txt".equals(format)) {
                res.setContentType("text/plain; charset=UTF-8");
                res.setHeader(
                        "Content-Disposition", "attachment; filename=essay-import-template.txt");
                res.getWriter().write(AssignmentDocumentReader.template());
            } else throw new IllegalArgumentException("Chọn file mẫu .txt hoặc .xlsx.");
        } catch (RuntimeException ex) {
            failure(res, ex);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        req.setCharacterEncoding("UTF-8");
        responseHeaders(res);
        Long teacher = activeTeacher(req, res);

        if (teacher == null) return;

        Collection<Part> parts = List.of();

        try {
            if (req.getContentLengthLong() > MAX_REQUEST) throw new UploadTooLarge();

            String type = req.getContentType();

            if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("multipart/form-data"))
                throw new IllegalArgumentException("Hãy gửi biểu mẫu tải tệp hợp lệ.");

            String action = parameter(req, "action");

            if (!"import".equals(action) && !"generate".equals(action))
                throw new IllegalArgumentException("Thao tác không hợp lệ.");

            // Parameter parsing is container-bounded. Do not extract uploaded documents or call
            // Gemini before authorization.
            parameter(req, "csrf");
            WebSupport.checkCsrf(req);
            long classId = WebSupport.id(parameter(req, "classId"));

            service.authorize(teacher, classId);

            try {
                parts = req.getParts();
            } catch (IllegalStateException ex) {
                throw new UploadTooLarge();
            } catch (ServletException ex) {
                throw new IllegalArgumentException("Biểu mẫu tải tệp không hợp lệ.");
            } catch (IOException ex) {
                throw new IllegalArgumentException("Không đọc được tệp tải lên. Vui lòng thử lại.");
            }

            List<AssignmentAuthoringService.Document> files = new ArrayList<>();

            for (Part part : parts) {
                String filename = part.getSubmittedFileName();

                if (filename == null || filename.isBlank()) continue;

                if (!"files".equals(part.getName()))
                    throw new IllegalArgumentException("Trường tải tệp không hợp lệ.");

                if (files.size() >= AssignmentAuthoringService.MAX_FILES)
                    throw new IllegalArgumentException("Được tải tối đa 3 tệp.");

                if (part.getSize() > AssignmentDocumentReader.MAX_FILE_BYTES)
                    throw new UploadTooLarge();

                byte[] bytes;

                try (InputStream input = part.getInputStream()) {
                    bytes = input.readNBytes(AssignmentDocumentReader.MAX_FILE_BYTES + 1);
                } catch (IOException ex) {
                    throw new IllegalArgumentException(
                            "Không đọc được tệp tải lên. Vui lòng thử lại.");
                }

                if (bytes.length > AssignmentDocumentReader.MAX_FILE_BYTES)
                    throw new UploadTooLarge();

                files.add(new AssignmentAuthoringService.Document(filename, bytes));
            }

            JsonArray tasks =
                    "import".equals(action)
                            ? service.importDocuments(
                                    teacher, classId, parameter(req, "maximumScore"), files)
                            : service.generate(
                                    teacher,
                                    classId,
                                    parameter(req, "maximumScore"),
                                    parameter(req, "questionCount"),
                                    parameter(req, "requirementCount"),
                                    parameter(req, "context"),
                                    parameter(req, "prompt"),
                                    files);
            JsonObject body = new JsonObject();

            body.add("tasks", tasks);
            body.addProperty(
                    "message",
                    "import".equals(action)
                            ? "Đã đọc "
                                    + tasks.size()
                                    + " câu hỏi. Hãy kiểm tra nội dung và điểm trước khi áp dụng."
                            : "AI đã tạo đề xuất. Hãy kiểm tra nội dung, hình thức trả lời và điểm trước khi áp dụng.");
            json(res, 200, body);
        } catch (RuntimeException ex) {
            failure(res, ex);
        } finally {
            for (Part part : parts)
                try {
                    part.delete();
                } catch (IOException ignored) {
                }
        }
    }

    private static String parameter(HttpServletRequest req, String name) {
        try {
            return req.getParameter(name);
        } catch (IllegalStateException ex) {
            throw new UploadTooLarge();
        }
    }

    private static void responseHeaders(HttpServletResponse res) {
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store");
        res.setHeader("X-Content-Type-Options", "nosniff");
    }

    private static Long activeTeacher(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        HttpSession session = req.getSession(false);

        if (session == null
                || !(session.getAttribute("userId") instanceof Long)
                || session.getAttribute("accountStatus") != AccountStatus.ACTIVE) {
            error(res, 401, "Vui lòng đăng nhập bằng tài khoản đang hoạt động.");

            return null;
        }

        if (session.getAttribute("role") != UserRole.TEACHER) {
            error(res, 403, "Chỉ giảng viên được soạn đề.");

            return null;
        }

        return (Long) session.getAttribute("userId");
    }

    private void failure(HttpServletResponse res, RuntimeException ex) throws IOException {
        if (ex instanceof UploadTooLarge)
            error(res, 413, "Mỗi tệp được tối đa 2 MB, tối đa 3 tệp mỗi lần.");
        else if (ex instanceof SecurityException) error(res, 403, ex.getMessage());
        else if (ex instanceof NoSuchElementException)
            error(res, 404, "Không tìm thấy lớp hoặc bạn không có quyền truy cập.");
        else if (ex instanceof IllegalArgumentException) error(res, 400, ex.getMessage());
        else if (ex instanceof GeminiAssignmentClient.UnavailableException)
            error(res, 503, ex.getMessage());
        else if (ex instanceof GeminiAssignmentClient.ProviderException)
            error(res, 502, ex.getMessage());
        else {
            getServletContext().log("Assignment authoring unavailable", ex);
            error(res, 503, "Chưa thể xử lý đề bài lúc này. Hãy thử lại sau.");
        }
    }

    private static void error(HttpServletResponse res, int status, String message)
            throws IOException {
        JsonObject body = new JsonObject();

        body.addProperty("error", message == null ? "Yêu cầu không hợp lệ." : message);
        json(res, status, body);
    }

    private static void json(HttpServletResponse res, int status, JsonObject body)
            throws IOException {
        res.setStatus(status);
        res.setContentType("application/json; charset=UTF-8");
        res.getWriter().write(body.toString());
    }

    private static final class UploadTooLarge extends IllegalArgumentException {}
}
