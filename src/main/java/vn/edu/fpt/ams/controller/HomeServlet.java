package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import vn.edu.fpt.ams.dao.UserDAO;
import vn.edu.fpt.ams.model.AccountStatus;
import vn.edu.fpt.ams.model.UserAccount;
import vn.edu.fpt.ams.model.UserRole;

@WebServlet("/home")
public class HomeServlet extends HttpServlet {
    static final String DEMO_TEACHER_EMAIL = "teacher@example.test";
    private final UserDAO userDAO = new UserDAO();

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.getRequestDispatcher("/WEB-INF/views/home.jsp").forward(request, response);
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (!"demo-teacher".equals(request.getParameter("action"))) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown home action");
            return;
        }
        Optional<UserAccount> found = userDAO.findByEmail(DEMO_TEACHER_EMAIL);
        if (found.isEmpty() || found.get().role() != UserRole.TEACHER
                || found.get().accountStatus() != AccountStatus.ACTIVE) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Seeded teacher account is not available");
            return;
        }
        UserAccount teacher = found.get();
        HttpSession session = request.getSession(true);
        session.setAttribute("userId", teacher.userId());
        session.setAttribute("role", teacher.role());
        session.setAttribute("accountStatus", teacher.accountStatus());
        response.sendRedirect(request.getContextPath() + "/teacher/dashboard");
    }
}
