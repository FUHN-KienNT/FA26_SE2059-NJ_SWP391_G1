package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.edu.fpt.ams.service.TemplateService;

@WebServlet("/expert/templates")
public class TemplateServlet extends HttpServlet {
    private final TemplateService service = new TemplateService();
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            req.setAttribute("templates", service.listTemplates((Long) req.getSession().getAttribute("userId"), Long.parseLong(req.getParameter("subjectId"))));
        } catch (UnsupportedOperationException | IllegalArgumentException ex) {
            req.setAttribute("featurePending", "Data loading is pending in this initial iteration.");
        }
        req.getRequestDispatcher("/expert/templates/template-list.jsp").forward(req, res);
    }
    @Override protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        // Mutations remain unavailable until ownership checks and JDBC transactions are implemented.
        res.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "TODO: complete SDS workflow before enabling writes");
    }
}
