package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.edu.fpt.ams.service.AssignmentService;

@WebServlet("/teacher/assignments")
public class AssignmentServlet extends HttpServlet {
    private final AssignmentService service = new AssignmentService();
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String view = "/WEB-INF/views/assignments/list.jsp";
        try {
            String mode = req.getParameter("mode");
            if ("view".equals(mode) || "create".equals(mode) || "edit".equals(mode)
                    || "publish".equals(mode) || "cancel".equals(mode)) {
                view = "/WEB-INF/views/assignments/detail.jsp";
                req.setAttribute("mode", mode);
            } else {
                req.setAttribute("assignments", service.listAssignments((Long) req.getSession().getAttribute("userId"), Long.parseLong(req.getParameter("classId"))));
            }
        } catch (UnsupportedOperationException | IllegalArgumentException ex) {
            req.setAttribute("featurePending", "Data loading is pending in this initial iteration.");
        }
        req.getRequestDispatcher(view).forward(req, res);
    }
    @Override protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        // Mutations remain unavailable until ownership checks and JDBC transactions are implemented.
        res.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "TODO: complete SDS workflow before enabling writes");
    }
}
