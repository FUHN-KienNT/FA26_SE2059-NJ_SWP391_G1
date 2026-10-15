package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import vn.edu.fpt.ams.dto.TeacherClassSummary;
import vn.edu.fpt.ams.service.AssignmentOfferService;
import vn.edu.fpt.ams.service.TeacherDashboardService;
import vn.edu.fpt.ams.util.TextMatch;

@WebServlet("/teacher/dashboard")
public class TeacherDashboardServlet extends HttpServlet {
    private final TeacherDashboardService service = new TeacherDashboardService();
    private final AssignmentOfferService offers = new AssignmentOfferService();

    /** Lấy lớp phụ trách, sĩ số, bài tập, bài nộp và số lời mời lớp bổ sung còn hạn, rồi mở dashboard. */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        long teacherId = (Long) request.getSession().getAttribute("userId");
        try {
            List<TeacherClassSummary> classes = service.assignedClasses(teacherId);
            String query = request.getParameter("q");
            request.setAttribute("teacher", service.requireActiveTeacher(teacherId));
            request.setAttribute("classTotal", classes.size());
            request.setAttribute("query", query == null ? "" : query);
            request.setAttribute("classes", classes.stream()
                    .filter(item -> TextMatch.contains(query, item.classCode(), item.className(),
                            item.subjectCode(), item.subjectName(), item.termCode()))
                    .toList());
            request.setAttribute("submissionCount", service.submissionCount(teacherId));
            request.setAttribute("assignmentCount", classes.stream().mapToLong(TeacherClassSummary::assignmentCount).sum());
            request.setAttribute("rosterCount", classes.stream().mapToLong(TeacherClassSummary::activeRoster).sum());
            request.setAttribute("openOfferCount", offers.openCount(teacherId));
        } catch (IllegalArgumentException ex) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
            return;
        }
        request.getRequestDispatcher("/WEB-INF/views/teacher/dashboard.jsp").forward(request, response);
    }
}
