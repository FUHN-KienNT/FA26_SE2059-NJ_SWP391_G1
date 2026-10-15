package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.NoSuchElementException;
import vn.edu.fpt.ams.dto.TeacherSubmissionFilter;
import vn.edu.fpt.ams.model.AccountStatus;
import vn.edu.fpt.ams.model.UserRole;
import vn.edu.fpt.ams.service.TeacherSubmissionService;
import vn.edu.fpt.ams.util.WebSupport;

/** Teacher browsing only. Grading and release are separate workflows. */
@WebServlet("/teacher/submissions")
public class TeacherSubmissionServlet extends HttpServlet {
    private final TeacherSubmissionService service;

    public TeacherSubmissionServlet() { this(new TeacherSubmissionService()); }
    TeacherSubmissionServlet(TeacherSubmissionService service) { this.service = service; }

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        res.setHeader("Cache-Control", "no-store");
        Long teacherId = teacher(req, res);
        if (teacherId == null) return;
        String view = "/WEB-INF/views/submissions/list.jsp";
        try {
            String mode = req.getParameter("mode");
            if (mode != null && !mode.isBlank() && !"list".equals(mode) && !"view".equals(mode))
                throw new IllegalArgumentException("Unknown submission action.");
            long assignmentId = WebSupport.id(req.getParameter("assignmentId"));
            var filter = TeacherSubmissionFilter.from(req.getParameter("search"), req.getParameter("status"));
            req.setAttribute("selectedAssignmentId", assignmentId);
            req.setAttribute("submissionFilters", filter);
            if ("view".equals(mode)) {
                view = "/WEB-INF/views/submissions/detail.jsp";
                long submissionId = WebSupport.id(req.getParameter("submissionId"));
                req.setAttribute("submissionDetail", service.detailPage(teacherId, assignmentId, submissionId));
            } else {
                req.setAttribute("submissionPage", service.listPage(teacherId, assignmentId, filter));
            }
        } catch (IllegalArgumentException ex) {
            res.sendError(400, "Invalid assignment, submission or filters.");
            return;
        } catch (NoSuchElementException ex) {
            // Missing and inaccessible records have the same response.
            res.sendError(404, "Submission or assignment not found or unavailable.");
            return;
        } catch (SecurityException ex) {
            res.sendError(403, "An active Teacher account is required.");
            return;
        } catch (IllegalStateException ex) {
            getServletContext().log("Teacher submissions could not be loaded", ex);
            res.setStatus(503);
            req.setAttribute("submissionError", "Submissions could not be loaded. Please try again.");
        }
        req.getRequestDispatcher(view).forward(req, res);
    }

    @Override protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setHeader("Cache-Control", "no-store");
        if (teacher(req, res) == null) return;
        res.setHeader("Allow", "GET, HEAD");
        res.sendError(405, "This page only displays saved submissions.");
    }

    private Long teacher(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || !(session.getAttribute("userId") instanceof Long id) || id <= 0
                || session.getAttribute("accountStatus") != AccountStatus.ACTIVE) {
            res.sendError(401, "Sign-in is required.");
            return null;
        }
        if (session.getAttribute("role") != UserRole.TEACHER) {
            res.sendError(403);
            return null;
        }
        return (Long) session.getAttribute("userId");
    }
}
