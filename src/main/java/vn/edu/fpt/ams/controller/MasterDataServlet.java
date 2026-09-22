package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.edu.fpt.ams.service.MasterDataService;

@WebServlet("/admin/terms")
public class MasterDataServlet extends HttpServlet {
    private final MasterDataService service = new MasterDataService();
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            req.setAttribute("terms", service.listTerms(req.getParameter("keyword"), null));
        } catch (UnsupportedOperationException | IllegalArgumentException ex) {
            req.setAttribute("featurePending", "Data loading is pending in this initial iteration.");
        }
        req.getRequestDispatcher("/admin/terms/term-list.jsp").forward(req, res);
    }
    @Override protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        // Mutations remain unavailable until ownership checks and JDBC transactions are implemented.
        res.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "TODO: complete SDS workflow before enabling writes");
    }
}
