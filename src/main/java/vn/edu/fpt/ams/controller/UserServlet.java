package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.edu.fpt.ams.service.UserService;

@WebServlet("/admin/users")
public class UserServlet extends HttpServlet {
    private final UserService service = new UserService();
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            req.setAttribute("users", service.listUsers(req.getParameter("keyword"), null, null, 1));
        } catch (UnsupportedOperationException | IllegalArgumentException ex) {
            req.setAttribute("featurePending", "Data loading is pending in this initial iteration.");
        }
        req.getRequestDispatcher("/admin/users/user-list.jsp").forward(req, res);
    }
    @Override protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        // Mutations remain unavailable until ownership checks and JDBC transactions are implemented.
        res.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "TODO: complete SDS workflow before enabling writes");
    }
}
