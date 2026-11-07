package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import vn.edu.fpt.ams.service.TeacherDashboardService;
import vn.edu.fpt.ams.service.TeacherGroupService;
import vn.edu.fpt.ams.util.WebSupport;

/** Class-scoped Teacher screens for group creation and membership management. */
@WebServlet({"/teacher/groups", "/teacher/groups/detail"})
public class TeacherGroupServlet extends HttpServlet {
    private final TeacherGroupService groups;
    private final TeacherDashboardService classes;

    /** Initialize group screens with the application services. */
    public TeacherGroupServlet() {
        this(new TeacherGroupService(), new TeacherDashboardService());
    }

    /** Supply service boundaries for controller tests. */
    TeacherGroupServlet(TeacherGroupService groups, TeacherDashboardService classes) {
        this.groups = groups;
        this.classes = classes;
    }

    /** Show the group list, a new-group form, or one assigned class group's details. */
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            long actor = WebSupport.actor(request);
            long classId = WebSupport.id(request.getParameter("classId"));
            request.setAttribute("selected", classes.requireAssignedClass(actor, classId));
            var allGroups = groups.list(actor, classId);
            if ("/teacher/groups/detail".equals(request.getServletPath())) {
                String rawId = request.getParameter("groupId");
                if (rawId != null && !rawId.isBlank()) {
                    var group = groups.detail(actor, classId, WebSupport.id(rawId));
                    request.setAttribute("group", group);
                    request.setAttribute("candidates", groups.unassignedStudents(actor, classId));
                }
                request.getRequestDispatcher("/WEB-INF/views/teacher/groups/detail.jsp").forward(request, response);
                return;
            }
            String keyword = parameter(request, "keyword");
            String status = parameter(request, "status");
            if (!status.isEmpty() && !Set.of("ACTIVE", "INACTIVE").contains(status)) {
                throw new IllegalArgumentException("Trạng thái nhóm không hợp lệ.");
            }
            request.setAttribute("keyword", keyword);
            request.setAttribute("status", status);
            request.setAttribute("groups", allGroups.stream()
                    .filter(group -> status.isEmpty() || group.status().equals(status))
                    .filter(group -> group.name().toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)))
                    .toList());
            request.getRequestDispatcher("/WEB-INF/views/teacher/groups/list.jsp").forward(request, response);
        } catch (RuntimeException ex) {
            WebSupport.failure(request, response, ex);
        }
    }

    /** Validate CSRF and apply one group action under current class ownership. */
    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        try {
            WebSupport.checkCsrf(request);
            long actor = WebSupport.actor(request);
            long classId = WebSupport.id(request.getParameter("classId"));
            String action = request.getParameter("action");
            Long groupId = null;
            if ("save".equals(action)) {
                String rawId = request.getParameter("groupId");
                groupId = groups.save(actor, classId, rawId == null || rawId.isBlank() ? null : WebSupport.id(rawId),
                        request.getParameter("name"), request.getParameter("description"));
            } else {
                groupId = WebSupport.id(request.getParameter("groupId"));
                switch (action == null ? "" : action) {
                    case "addMember" -> groups.addMember(actor, classId, groupId,
                            WebSupport.id(request.getParameter("studentId")));
                    case "removeMember" -> groups.removeMember(actor, classId, groupId,
                            WebSupport.id(request.getParameter("studentId")));
                    case "leader" -> {
                        String studentId = request.getParameter("studentId");
                        groups.setLeader(actor, classId, groupId,
                                studentId == null || studentId.isBlank() ? null : WebSupport.id(studentId));
                    }
                    case "status" -> groups.changeStatus(actor, classId, groupId, request.getParameter("status"));
                    default -> throw new IllegalArgumentException("Thao tác nhóm không hợp lệ.");
                }
            }
            request.getSession().setAttribute("groupNotice", "Đã cập nhật nhóm.");
            response.sendRedirect(request.getContextPath() + "/teacher/groups/detail?classId=" + classId + "&groupId=" + groupId);
        } catch (IllegalArgumentException ex) {
            // Render the current authorized page and retain text input after validation errors.
            request.setAttribute("error", ex.getMessage());
            request.setAttribute("submittedName", request.getParameter("name"));
            request.setAttribute("submittedDescription", request.getParameter("description"));
            response.setStatus(400);
            doGet(request, response);
        } catch (RuntimeException ex) {
            WebSupport.failure(request, response, ex);
        }
    }

    /** Normalize a bounded group list filter. */
    private String parameter(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        value = value == null ? "" : value.trim();
        if (value.length() > 100) throw new IllegalArgumentException("Bộ lọc tối đa 100 ký tự.");
        return value;
    }
}
