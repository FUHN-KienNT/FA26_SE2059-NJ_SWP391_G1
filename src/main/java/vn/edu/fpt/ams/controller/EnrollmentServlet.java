package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.util.List;
import vn.edu.fpt.ams.dto.TeacherClassSummary;
import vn.edu.fpt.ams.model.EnrollmentStatus;
import vn.edu.fpt.ams.service.EnrollmentService;
import vn.edu.fpt.ams.service.TeacherDashboardService;

/** Teacher routes for assigned classes, roster, enrollment changes and CSV import. */
@WebServlet("/teacher/enrollments")
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 6 * 1024 * 1024)
public class EnrollmentServlet extends HttpServlet {
    private final EnrollmentService service;
    private final TeacherDashboardService dashboardService;

    /** Initialize the servlet with database-backed enrollment and class services. */
    public EnrollmentServlet() { this(new EnrollmentService(), new TeacherDashboardService()); }

    /** Supply services for isolated routing and ownership tests. */
    EnrollmentServlet(EnrollmentService service, TeacherDashboardService dashboardService) {
        this.service = service;
        this.dashboardService = dashboardService;
    }

    /** Load either the searchable class list or one authorized class roster. */
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        long teacherId = (Long) request.getSession().getAttribute("userId");
        try {
            Long classId = optionalId(request, "classId");
            if (classId != null && "manage".equals(request.getParameter("mode"))) {
                loadEnrollmentPage(request, teacherId, classId);
                request.getRequestDispatcher("/WEB-INF/views/enrollments/detail.jsp").forward(request, response);
                return;
            }
            List<TeacherClassSummary> classes = dashboardService.assignedClasses(teacherId);
            String keyword = parameter(request, "keyword");
            String term = parameter(request, "term");
            request.setAttribute("keyword", keyword);
            request.setAttribute("term", term);
            request.setAttribute("terms", classes.stream().map(TeacherClassSummary::termCode).distinct().sorted().toList());
            request.setAttribute("listQuery", "keyword=" + encode(keyword) + "&amp;term=" + encode(term));
            request.setAttribute("classes", classes.stream()
                    .filter(item -> term.isEmpty() || term.equals(item.termCode()))
                    .filter(item -> matches(item, keyword)).toList());
            if (classId != null) {
                TeacherClassSummary selected = classes.stream()
                        .filter(item -> item.classId() == classId)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Lớp này không thuộc phân công của giảng viên hiện tại"));
                request.setAttribute("selected", selected);
                request.setAttribute("students", service.listStudents(teacherId, classId));
                request.setAttribute("invitations", service.listInvitations(teacherId, classId));
            }
            Object notice = request.getSession().getAttribute("enrollmentNotice");
            if (notice != null) {
                request.setAttribute("notice", notice);
                request.getSession().removeAttribute("enrollmentNotice");
            }
            request.getRequestDispatcher("/WEB-INF/views/enrollments/list.jsp").forward(request, response);
        } catch (IllegalArgumentException ex) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, message(ex.getMessage()));
        }
    }

    /** Submit an enrollment action and reopen its popup when validation fails. */
    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        long teacherId = (Long) request.getSession().getAttribute("userId");
        request.setCharacterEncoding("UTF-8");
        long classId;
        try { classId = requiredId(request, "classId"); }
        catch (IllegalArgumentException ex) { response.sendError(400, "Mã lớp không hợp lệ."); return; }
        String action = request.getParameter("action");
        try {
            if ("addStudent".equals(action)) {
                service.addStudentByEmail(teacherId, classId, request.getParameter("email"));
                request.getSession().setAttribute("enrollmentNotice", "Đã thêm sinh viên vào lớp.");
            } else if ("importStudents".equals(action)) {
                Part upload = request.getPart("file");
                if (upload == null || upload.getSize() == 0 || upload.getSubmittedFileName() == null
                        || !upload.getSubmittedFileName().toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) {
                    throw new IllegalArgumentException("Vui lòng chọn tệp CSV");
                }
                try (var stream = upload.getInputStream()) {
                    var result = service.importStudentsFromCsv(teacherId, classId, stream);
                    request.getSession().setAttribute("enrollmentImportResult:" + classId, result);
                    request.getSession().setAttribute("enrollmentNotice",
                            "Đã nhập danh sách. Thêm thành công " + result.imported()
                                    + ", bỏ qua " + result.skipped()
                                    + ", không hợp lệ " + result.rejected() + ".");
                }
            } else if ("updateStatus".equals(action)) {
                long enrollmentId = requiredId(request, "enrollmentId");
                if (service.listStudents(teacherId, classId).stream().noneMatch(item -> item.enrollmentId() == enrollmentId)) {
                    throw new IllegalArgumentException("Sinh viên không thuộc lớp đã chọn.");
                }
                service.updateEnrollmentStatus(teacherId, enrollmentId,
                        EnrollmentStatus.valueOf(parameter(request, "status")));
                request.getSession().setAttribute("enrollmentNotice", "Đã cập nhật trạng thái sinh viên trong lớp.");
            } else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Thao tác quản lý sinh viên không hợp lệ");
                return;
            }
            response.sendRedirect(request.getContextPath() + "/teacher/enrollments?classId=" + classId + "&mode=manage");
        } catch (IllegalArgumentException ex) {
            try {
                loadEnrollmentPage(request, teacherId, classId);
            } catch (IllegalArgumentException ignored) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, message(ex.getMessage()));
                return;
            }
            request.setAttribute("error", message(ex.getMessage()));
            request.setAttribute("openDialog", action);
            request.setAttribute("submittedEmail", request.getParameter("email"));
            request.getRequestDispatcher("/WEB-INF/views/enrollments/detail.jsp").forward(request, response);
        }
    }

    /** Resolve class ownership before loading editable enrollment data and flash notices. */
    private void loadEnrollmentPage(HttpServletRequest request, long teacherId, long classId) {
        List<TeacherClassSummary> classes = dashboardService.assignedClasses(teacherId);
        TeacherClassSummary selected = classes.stream()
                .filter(item -> item.classId() == classId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Lớp này không thuộc phân công của giảng viên hiện tại"));
        request.setAttribute("selected", selected);
        request.setAttribute("students", service.listStudents(teacherId, classId));
        String importKey = "enrollmentImportResult:" + classId;
        Object importResult = request.getSession().getAttribute(importKey);
        if (importResult != null) {
            request.setAttribute("importResult", importResult);
            request.getSession().removeAttribute(importKey);
        }
        Object notice = request.getSession().getAttribute("enrollmentNotice");
        if (notice != null) {
            request.setAttribute("notice", notice);
            request.getSession().removeAttribute("enrollmentNotice");
        }
    }

    /** Translate database business messages at the UI boundary; import classification stays unchanged. */
    private static String message(String text) {
        if (text == null) return "Dữ liệu lớp học không hợp lệ.";
        return switch (text) {
            case "This class is not assigned to the current teacher" -> "Lớp này không thuộc phân công của giảng viên hiện tại.";
            case "Students can be added only to an active class" -> "Chỉ có thể thêm sinh viên vào lớp đang hoạt động.";
            case "An active Student account is required" -> "Cần tài khoản sinh viên đang hoạt động.";
            case "The class has no available seat" -> "Lớp đã đủ sĩ số.";
            case "This student already has an active enrollment for the subject term" -> "Sinh viên đã tham gia một lớp của môn học trong học kỳ này.";
            case "Enrollment was not found for this teacher" -> "Không tìm thấy sinh viên trong lớp của giảng viên này.";
            case "An inactive class cannot receive an active enrollment" -> "Không thể thêm sinh viên đang học vào lớp đã ngừng hoạt động.";
            default -> text.replace(" is required", " là thông tin bắt buộc");
        };
    }

    /** Parse positive identifiers and reject malformed or nonpositive class context. */
    private static Long optionalId(HttpServletRequest request, String name) {
        String raw = request.getParameter(name);
        if (raw == null || raw.isBlank()) return null;
        long id = Long.parseLong(raw);
        if (id <= 0) throw new IllegalArgumentException("Mã định danh phải lớn hơn 0.");
        return id;
    }

    /** Require a supplied positive identifier for a write action. */
    private static long requiredId(HttpServletRequest request, String name) {
        Long id = optionalId(request, name);
        if (id == null) throw new IllegalArgumentException(name + " là thông tin bắt buộc");
        return id;
    }

    /** Normalize bounded list filters without changing enrollment data. */
    private static String parameter(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        value = value == null ? "" : value.trim();
        if (value.length() > 100) throw new IllegalArgumentException("Bộ lọc tối đa 100 ký tự.");
        return value;
    }

    /** Encode a filter value before including it in a return link. */
    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Match a class by code, name, or subject, ignoring letter case. */
    static boolean matches(TeacherClassSummary item, String keyword) {
        String text = String.join(" ", item.classCode(), item.className(), item.subjectCode(), item.subjectName());
        return text.toLowerCase(java.util.Locale.ROOT).contains(keyword.toLowerCase(java.util.Locale.ROOT));
    }
}
