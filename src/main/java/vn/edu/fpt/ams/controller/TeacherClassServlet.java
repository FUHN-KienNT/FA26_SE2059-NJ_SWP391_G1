package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import vn.edu.fpt.ams.dto.TeacherClassSummary;
import vn.edu.fpt.ams.service.TeacherDashboardService;

/** Read-only class screens for the current Teacher. */
@WebServlet({"/teacher/classes", "/teacher/classes/detail"})
public class TeacherClassServlet extends HttpServlet {
    private final TeacherDashboardService service;

    /** Initialize the database-backed class views. */
    public TeacherClassServlet() {
        this(new TeacherDashboardService());
    }

    /** Supply the class service for routing tests. */
    TeacherClassServlet(TeacherDashboardService service) {
        this.service = service;
    }

    /** Render a searchable class list or the details of one assigned class. */
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        long teacherId = (Long) request.getSession().getAttribute("userId");
        try {
            if ("/teacher/classes/detail".equals(request.getServletPath())) {
                long classId = Long.parseLong(parameter(request, "classId"));
                if (classId <= 0) throw new IllegalArgumentException("Mã lớp không hợp lệ.");
                request.setAttribute("selected", service.requireAssignedClass(teacherId, classId));
                request.getRequestDispatcher("/WEB-INF/views/teacher/classes/detail.jsp").forward(request, response);
                return;
            }
            var classes = service.assignedClasses(teacherId);
            String keyword = parameter(request, "keyword");
            String term = parameter(request, "term");
            request.setAttribute("keyword", keyword);
            request.setAttribute("term", term);
            request.setAttribute("terms", classes.stream().map(TeacherClassSummary::termCode).distinct().sorted().toList());
            request.setAttribute("classes", classes.stream()
                    .filter(item -> term.isEmpty() || term.equals(item.termCode()))
                    .filter(item -> matches(item, keyword)).toList());
            request.getRequestDispatcher("/WEB-INF/views/teacher/classes/list.jsp").forward(request, response);
        } catch (NumberFormatException ex) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Mã lớp không hợp lệ.");
        } catch (IllegalArgumentException ex) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        }
    }

    /** Read a bounded filter value from the request. */
    private static String parameter(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        value = value == null ? "" : value.trim();
        if (value.length() > 100) throw new IllegalArgumentException("Bộ lọc tối đa 100 ký tự.");
        return value;
    }

    /** Match class and subject codes or names without case sensitivity. */
    private static boolean matches(TeacherClassSummary item, String keyword) {
        String text = String.join(" ", item.classCode(), item.className(), item.subjectCode(), item.subjectName());
        return text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }
}
