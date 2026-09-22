package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import vn.edu.fpt.ams.dto.SubjectClassFormDTO;
import vn.edu.fpt.ams.service.SubjectClassService;

/** Expert routes for Subject Class setup and Teacher assignment. */
@WebServlet("/expert/classes")
public class SubjectClassServlet extends HttpServlet {
    private final SubjectClassService service = new SubjectClassService();

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        try {
            long expertId = (Long) req.getSession().getAttribute("userId");
            req.setAttribute("classes", service.listClasses(expertId,
                    optionalId(req, "subjectId"), optionalId(req, "termId")));
        } catch (UnsupportedOperationException ex) {
            req.setAttribute("featurePending", "Class data loading is pending in this initial iteration.");
        } catch (IllegalArgumentException ex) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
            return;
        }
        req.getRequestDispatcher("/expert/subject-class-list.jsp").forward(req, res);
    }

    @Override protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            long expertId = (Long) req.getSession().getAttribute("userId");
            String action = req.getParameter("action");
            if ("create".equals(action)) service.createClass(expertId, form(req));
            else if ("update".equals(action)) service.updateClass(expertId, form(req));
            else if ("assignTeacher".equals(action))
                service.assignTeacherToClass(expertId, requiredId(req, "classId"), requiredId(req, "teacherId"));
            else { res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown class action"); return; }
            res.sendRedirect(req.getContextPath() + "/expert/classes");
        } catch (UnsupportedOperationException ex) {
            res.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        }
    }

    private static SubjectClassFormDTO form(HttpServletRequest req) {
        Long classId = optionalId(req, "classId");
        return new SubjectClassFormDTO(classId == null ? 0 : classId,
                requiredId(req, "subjectTermId"), req.getParameter("classCode"),
                req.getParameter("className"), Math.toIntExact(requiredId(req, "capacity")),
                req.getParameter("registrationEnabled") != null);
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
