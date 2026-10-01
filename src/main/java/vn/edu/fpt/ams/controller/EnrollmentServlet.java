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
    private final EnrollmentService service = new EnrollmentService();
    private final TeacherDashboardService dashboardService = new TeacherDashboardService();

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
            request.setAttribute("classes", classes);
            if (classId != null) {
                TeacherClassSummary selected = classes.stream()
                        .filter(item -> item.classId() == classId)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("This class is not assigned to the current teacher"));
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
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        }
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        long teacherId = (Long) request.getSession().getAttribute("userId");
        long classId = requiredId(request, "classId");
        String action = request.getParameter("action");
        try {
            if ("addStudent".equals(action)) {
                service.addStudentByEmail(teacherId, classId, request.getParameter("email"));
                request.getSession().setAttribute("enrollmentNotice", "Student added to the class.");
            } else if ("importStudents".equals(action)) {
                Part upload = request.getPart("file");
                if (upload == null || upload.getSize() == 0 || upload.getSubmittedFileName() == null
                        || !upload.getSubmittedFileName().toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) {
                    throw new IllegalArgumentException("A CSV file is required");
                }
                try (var stream = upload.getInputStream()) {
                    var result = service.importStudentsFromCsv(teacherId, classId, stream);
                    request.getSession().setAttribute("enrollmentNotice",
                            "Import finished. Added " + result.imported()
                                    + ", skipped " + result.skipped()
                                    + ", rejected " + result.rejected() + ".");
                }
            } else if ("updateStatus".equals(action)) {
                service.updateEnrollmentStatus(teacherId, requiredId(request, "enrollmentId"),
                        EnrollmentStatus.valueOf(request.getParameter("status")));
                request.getSession().setAttribute("enrollmentNotice", "Enrollment status updated.");
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
            request.setAttribute("error", ex.getMessage());
            request.getRequestDispatcher("/WEB-INF/views/enrollments/detail.jsp").forward(request, response);
        }
    }

    private void loadEnrollmentPage(HttpServletRequest request, long teacherId, long classId) {
        List<TeacherClassSummary> classes = dashboardService.assignedClasses(teacherId);
        TeacherClassSummary selected = classes.stream()
                .filter(item -> item.classId() == classId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("This class is not assigned to the current teacher"));
        request.setAttribute("selected", selected);
        request.setAttribute("students", service.listStudents(teacherId, classId));
        Object notice = request.getSession().getAttribute("enrollmentNotice");
        if (notice != null) {
            request.setAttribute("notice", notice);
            request.getSession().removeAttribute("enrollmentNotice");
        }
    }

    private static Long optionalId(HttpServletRequest request, String name) {
        String raw = request.getParameter(name);
        return raw == null || raw.isBlank() ? null : Long.valueOf(raw);
    }

    private static long requiredId(HttpServletRequest request, String name) {
        Long id = optionalId(request, name);
        if (id == null) throw new IllegalArgumentException(name + " is required");
        return id;
    }
}
