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
import vn.edu.fpt.ams.dto.EnrollmentDTO;
import vn.edu.fpt.ams.dto.ImportResultDTO;
import vn.edu.fpt.ams.dto.TeacherClassSummary;
import vn.edu.fpt.ams.model.EnrollmentStatus;
import vn.edu.fpt.ams.service.EnrollmentService;
import vn.edu.fpt.ams.service.TeacherDashboardService;
import vn.edu.fpt.ams.util.TextMatch;

/** Teacher routes for classes they teach, roster, enrollment changes and CSV import. */
@WebServlet("/teacher/enrollments")
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 6 * 1024 * 1024)
public class EnrollmentServlet extends HttpServlet {
    private final EnrollmentService service = new EnrollmentService();
    private final TeacherDashboardService dashboardService = new TeacherDashboardService();

    /** Mở danh sách lớp phụ trách. Có classId thì kèm sĩ số và lời mời; mode=manage thì mở trang quản lý ghi danh. */
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        long teacherId = (Long) request.getSession().getAttribute("userId");
        Long classId = optionalId(request, "classId");
        try {
            if (classId != null && "manage".equals(request.getParameter("mode"))) {
                loadEnrollmentPage(request, teacherId, classId);
                request.getRequestDispatcher("/WEB-INF/views/enrollments/detail.jsp").forward(request, response);
                return;
            }
            List<TeacherClassSummary> classes = dashboardService.assignedClasses(teacherId);
            String query = parameter(request, "q");
            request.setAttribute("classTotal", classes.size());
            request.setAttribute("classes", classes.stream()
                    .filter(item -> matchesClass(query, item))
                    .toList());
            request.setAttribute("query", query);
            if (classId != null) {
                TeacherClassSummary selected = classes.stream()
                        .filter(item -> item.classId() == classId)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Bạn không phụ trách lớp này"));
                String studentQuery = parameter(request, "student");
                String inviteQuery = parameter(request, "invite");
                request.setAttribute("selected", selected);
                request.setAttribute("studentQuery", studentQuery);
                request.setAttribute("inviteQuery", inviteQuery);
                request.setAttribute("students", service.listStudents(teacherId, classId).stream()
                        .filter(student -> TextMatch.contains(studentQuery, student.studentName(), student.studentEmail(),
                                student.status().name()))
                        .toList());
                request.setAttribute("invitations", service.listInvitations(teacherId, classId).stream()
                        .filter(invitation -> TextMatch.contains(inviteQuery, invitation.fullName(), invitation.email(),
                                invitation.status().name()))
                        .toList());
            }
            moveNotice(request);
            request.getRequestDispatcher("/WEB-INF/views/enrollments/list.jsp").forward(request, response);
        } catch (IllegalArgumentException ex) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        }
    }

    /** Thêm sinh viên theo email, nhập CSV, hoặc đổi trạng thái ghi danh, rồi quay lại trang quản lý của lớp đó. */
    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        long teacherId = (Long) request.getSession().getAttribute("userId");
        long classId = requiredId(request, "classId");
        String action = request.getParameter("action");
        try {
            if ("addStudent".equals(action)) {
                service.addStudentByEmail(teacherId, classId, request.getParameter("email"));
                request.getSession().setAttribute("enrollmentNotice", "Đã thêm sinh viên vào lớp.");
            } else if ("importStudents".equals(action)) {
                Part upload = request.getPart("file");
                if (upload == null || upload.getSize() == 0 || upload.getSubmittedFileName() == null
                        || !upload.getSubmittedFileName().toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) {
                    throw new IllegalArgumentException("Cần một file CSV");
                }
                try (var stream = upload.getInputStream()) {
                    ImportResultDTO result = service.importStudentsFromCsv(teacherId, classId, stream);
                    request.getSession().setAttribute("enrollmentImport", result);
                    request.getSession().setAttribute("enrollmentNotice",
                            "Nhập xong. Đã thêm " + result.imported()
                                    + ", bỏ qua " + result.skipped()
                                    + ", từ chối " + result.rejected() + ".");
                }
            } else if ("updateStatus".equals(action)) {
                service.updateEnrollmentStatus(teacherId, requiredId(request, "enrollmentId"),
                        EnrollmentStatus.valueOf(request.getParameter("status")));
                request.getSession().setAttribute("enrollmentNotice", "Đã cập nhật trạng thái ghi danh.");
            } else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown enrollment action");
                return;
            }
            response.sendRedirect(request.getContextPath() + "/teacher/enrollments?classId=" + classId + "&mode=manage");
        } catch (IllegalArgumentException ex) {
            try {
                loadEnrollmentPage(request, teacherId, classId);
            } catch (IllegalArgumentException ignored) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
                return;
            }
            request.setAttribute("error", EnrollmentService.publicMessage(ex));
            request.setAttribute("openDialog", "addStudent".equals(action) ? "add-dialog" : "import-dialog");
            request.getRequestDispatcher("/WEB-INF/views/enrollments/detail.jsp").forward(request, response);
        }
    }

    /** Gắn lớp phụ trách và danh sách sinh viên đã lọc vào request cho trang quản lý ghi danh. */
    private void loadEnrollmentPage(HttpServletRequest request, long teacherId, long classId) {
        List<TeacherClassSummary> classes = dashboardService.assignedClasses(teacherId);
        TeacherClassSummary selected = classes.stream()
                .filter(item -> item.classId() == classId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Bạn không phụ trách lớp này"));
        String query = parameter(request, "q");
        List<EnrollmentDTO> students = service.listStudents(teacherId, classId);
        request.setAttribute("selected", selected);
        request.setAttribute("query", query);
        request.setAttribute("studentTotal", students.size());
        request.setAttribute("students", students.stream()
                .filter(student -> TextMatch.contains(query, student.studentName(), student.studentEmail(),
                        student.status().name()))
                .toList());
        moveNotice(request);
    }

    /** Chuyển thông báo và kết quả nhập từ phiên sang request để trang chỉ hiện một lần. */
    private static void moveNotice(HttpServletRequest request) {
        Object notice = request.getSession().getAttribute("enrollmentNotice");
        if (notice != null) {
            request.setAttribute("notice", notice);
            request.getSession().removeAttribute("enrollmentNotice");
        }
        Object imported = request.getSession().getAttribute("enrollmentImport");
        if (imported instanceof ImportResultDTO result) {
            request.setAttribute("importResult", result);
            request.getSession().removeAttribute("enrollmentImport");
        }
    }

    /** Đúng khi lớp khớp mã lớp, tên lớp, môn hoặc học kỳ. */
    private static boolean matchesClass(String query, TeacherClassSummary item) {
        return TextMatch.contains(query, item.classCode(), item.className(), item.subjectCode(),
                item.subjectName(), item.termCode());
    }

    /** Đọc tham số chữ. Thiếu thì trả chuỗi rỗng. */
    private static String parameter(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value;
    }

    /** Đọc một id từ request. Thiếu hoặc chuỗi rỗng thì trả về null. */
    private static Long optionalId(HttpServletRequest request, String name) {
        String raw = request.getParameter(name);
        return raw == null || raw.isBlank() ? null : Long.valueOf(raw);
    }

    /** Đọc một id bắt buộc. Thiếu thì báo lỗi tham số. */
    private static long requiredId(HttpServletRequest request, String name) {
        Long id = optionalId(request, name);
        if (id == null) throw new IllegalArgumentException(name + " is required");
        return id;
    }
}
