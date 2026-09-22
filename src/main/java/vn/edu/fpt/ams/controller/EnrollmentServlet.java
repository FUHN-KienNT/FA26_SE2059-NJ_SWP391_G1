package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.IOException;
import vn.edu.fpt.ams.model.EnrollmentStatus;
import vn.edu.fpt.ams.service.EnrollmentService;
import vn.edu.fpt.ams.service.SubjectClassService;

/** Teacher routes for assigned classes, roster and Student import. */
@WebServlet("/teacher/enrollments")
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 6 * 1024 * 1024)
public class EnrollmentServlet extends HttpServlet {
    private final EnrollmentService service = new EnrollmentService();
    private final SubjectClassService classService = new SubjectClassService();

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        String view = "/teacher/assigned-class-list.jsp";
        try {
            long teacherId = (Long) req.getSession().getAttribute("userId");
            Long classId = optionalId(req, "classId");
            if (classId == null) {
                req.setAttribute("classes", classService.listAssignedClasses(teacherId));
            } else {
                view = "/teacher/class-roster.jsp";
                req.setAttribute("students", service.listStudents(teacherId, classId));
            }
        } catch (UnsupportedOperationException ex) {
            req.setAttribute("featurePending", "Roster data loading is pending in this initial iteration.");
        } catch (IllegalArgumentException ex) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
            return;
        }
        req.getRequestDispatcher(view).forward(req, res);
    }

    @Override protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        try {
            long teacherId = (Long) req.getSession().getAttribute("userId");
            long classId = requiredId(req, "classId");
            String action = req.getParameter("action");
            if ("addStudent".equals(action))
                service.addStudent(teacherId, classId, requiredId(req, "studentId"));
            else if ("importStudents".equals(action)) {
                Part upload = req.getPart("file");
                if (upload == null || upload.getSize() == 0 || upload.getSubmittedFileName() == null
                        || !upload.getSubmittedFileName().toLowerCase(java.util.Locale.ROOT).endsWith(".csv")) {
                    throw new IllegalArgumentException("A CSV file is required");
                }
                try (var stream = upload.getInputStream()) {
                    service.importStudentsFromCsv(teacherId, classId, stream);
                }
            } else if ("updateStatus".equals(action))
                service.updateEnrollmentStatus(teacherId, requiredId(req, "enrollmentId"),
                        EnrollmentStatus.valueOf(req.getParameter("status")));
            else { res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown enrollment action"); return; }
            res.sendRedirect(req.getContextPath() + "/teacher/enrollments?classId=" + classId);
        } catch (UnsupportedOperationException ex) {
            res.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        }
    }

    private static Long optionalId(HttpServletRequest req, String name) {
        String raw = req.getParameter(name);
        return raw == null || raw.isBlank() ? null : Long.valueOf(raw);
    }

    private static long requiredId(HttpServletRequest req, String name) {
        Long id = optionalId(req, name);
        if (id == null) throw new IllegalArgumentException(name + " is required");
        return id;
    }
}
