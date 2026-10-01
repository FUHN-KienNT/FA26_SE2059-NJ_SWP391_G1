package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import vn.edu.fpt.ams.dto.TeacherClassSummary;
import vn.edu.fpt.ams.service.TeacherDashboardService;

@WebServlet("/teacher/dashboard")
public class TeacherDashboardServlet extends HttpServlet {
    private final TeacherDashboardService service = new TeacherDashboardService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        long teacherId = (Long) request.getSession().getAttribute("userId");
        try {
            List<TeacherClassSummary> classes = service.assignedClasses(teacherId);
            request.setAttribute("teacher", service.requireActiveTeacher(teacherId));
            request.setAttribute("classes", classes);
            request.setAttribute("submissionCount", service.submissionCount(teacherId));
            request.setAttribute("assignmentCount", classes.stream().mapToLong(TeacherClassSummary::assignmentCount).sum());
            request.setAttribute("rosterCount", classes.stream().mapToLong(TeacherClassSummary::activeRoster).sum());
        } catch (IllegalArgumentException ex) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
            return;
        }
        request.getRequestDispatcher("/WEB-INF/views/teacher/dashboard.jsp").forward(request, response);
    }
}
