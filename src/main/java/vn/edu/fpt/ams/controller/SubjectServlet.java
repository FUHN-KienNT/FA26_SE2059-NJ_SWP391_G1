package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.edu.fpt.ams.service.SubjectService;

@WebServlet("/admin/subjects")
public class SubjectServlet extends HttpServlet {
    private final SubjectService service = new SubjectService();
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            req.setAttribute("subjects", service.listSubjects(req.getParameter("keyword"), null));
        } catch (UnsupportedOperationException | IllegalArgumentException ex) {
            req.setAttribute("featurePending", "Data loading is pending in this initial iteration.");
        }
        req.getRequestDispatcher("/admin/subjects/subject-list.jsp").forward(req, res);
    }
    @Override protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        // Mutations remain unavailable until ownership checks and JDBC transactions are implemented.
        res.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "TODO: complete SDS workflow before enabling writes");
    }
}
