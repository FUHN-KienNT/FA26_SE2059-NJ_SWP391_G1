package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.edu.fpt.ams.service.AssignmentService;
import vn.edu.fpt.ams.dto.AssignmentListFilter;
import vn.edu.fpt.ams.model.AccountStatus;
import vn.edu.fpt.ams.model.UserRole;
import vn.edu.fpt.ams.util.AssignmentPreviewAccess;

@WebServlet("/teacher/assignments")
public class AssignmentServlet extends HttpServlet {
    private final AssignmentService service;
    public AssignmentServlet() { this(new AssignmentService()); }
    AssignmentServlet(AssignmentService service) { this.service = service; }
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setHeader("Cache-Control", "no-store");
        HttpSession session = req.getSession(false);
        boolean preview = AssignmentPreviewAccess.allows(req);
        if (!preview && (session == null || !(session.getAttribute("userId") instanceof Long)
                || session.getAttribute("accountStatus") != AccountStatus.ACTIVE)) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sign-in is required.");
            return;
        }
        if (!preview && session.getAttribute("role") != UserRole.TEACHER) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String view = "/WEB-INF/views/assignments/list.jsp";
        try {
            long teacherId = preview ? service.previewTeacherId() : (Long) session.getAttribute("userId");
            if (preview) req.setAttribute("assignmentPreviewTeacherId", teacherId);
            String mode = req.getParameter("mode");
            if ("view".equals(mode) || "create".equals(mode) || "edit".equals(mode)
                    || "publish".equals(mode) || "cancel".equals(mode)) {
                view = "/WEB-INF/views/assignments/detail.jsp";
                req.setAttribute("mode", mode);
            } else {
                if (mode != null && !mode.isBlank() && !"list".equals(mode))
                    throw new IllegalArgumentException("Unknown assignment action.");
                String rawClassId = req.getParameter("classId");
                Long classId = rawClassId == null || rawClassId.isBlank() ? null : Long.valueOf(rawClassId);
                var filter = AssignmentListFilter.from(req.getParameter("search"), req.getParameter("purpose"),
                        req.getParameter("responseType"), req.getParameter("status"));
                var page = service.listPage(teacherId, classId, filter);
                req.setAttribute("assignmentPage", page);
                req.setAttribute("assignments", page.assignments());
            }
        } catch (IllegalArgumentException ex) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid class or assignment filters.");
            return;
        } catch (SecurityException ex) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
            return;
        } catch (IllegalStateException ex) {
            getServletContext().log("Assignment List could not be loaded", ex);
            res.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            req.setAttribute("assignmentListError", "Assignments could not be loaded. Please try again.");
        }
        req.getRequestDispatcher(view).forward(req, res);
    }
    @Override protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        // Mutations remain unavailable until ownership checks and JDBC transactions are implemented.
        res.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "TODO: complete SDS workflow before enabling writes");
    }
}
