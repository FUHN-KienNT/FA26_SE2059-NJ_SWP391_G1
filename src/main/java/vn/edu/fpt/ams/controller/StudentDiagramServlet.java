package vn.edu.fpt.ams.controller;

import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.service.*;
import vn.edu.fpt.ams.util.*;

@WebServlet("/student/diagrams/plantuml")
public class StudentDiagramServlet extends HttpServlet {
    private final StudentObjectiveService assignments;
    private final PlantUmlRenderer renderer;

    public StudentDiagramServlet() {
        this(new StudentObjectiveService(), new PlantUmlRenderer());
    }

    StudentDiagramServlet(StudentObjectiveService assignments, PlantUmlRenderer renderer) {
        this.assignments = assignments;
        this.renderer = renderer;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        req.setCharacterEncoding("UTF-8");
        res.setContentType("application/json;charset=UTF-8");
        res.setHeader("Cache-Control", "no-store");

        try {
            if (req.getContentLengthLong() > 200_000)
                throw new IllegalArgumentException("Mã sơ đồ quá lớn.");

            var session = req.getSession(false);

            if (session == null
                    || session.getAttribute("role") != UserRole.STUDENT
                    || session.getAttribute("accountStatus") != AccountStatus.ACTIVE)
                throw new SecurityException(
                        "Vui lòng đăng nhập bằng tài khoản sinh viên đang hoạt động.");

            for (String key :
                    List.of(
                            "csrf",
                            "assignmentId",
                            "submissionId",
                            "taskOrder",
                            "requirementOrder",
                            "source")) {
                String[] values = req.getParameterValues(key);

                if (values == null || values.length != 1)
                    throw new IllegalArgumentException("Dữ liệu trình vẽ không hợp lệ.");
            }

            WebSupport.checkCsrf(req);
            long student = WebSupport.actor(req),
                    assignment = WebSupport.id(req.getParameter("assignmentId")),
                    submission = WebSupport.id(req.getParameter("submissionId"));
            long task = WebSupport.id(req.getParameter("taskOrder")),
                    requirement = WebSupport.id(req.getParameter("requirementOrder"));

            checkEditable(student, assignment, submission, task, requirement);
            String source = PlantUmlSource.normalize(req.getParameter("source"));
            String image = renderer.render(source);

            // Rendering can finish after the deadline or a submission in another tab.
            checkEditable(student, assignment, submission, task, requirement);
            JsonObject json = new JsonObject();

            json.addProperty("source", source);
            json.addProperty("image", image);
            res.getWriter().write(json.toString());
        } catch (RuntimeException ex) {
            int status =
                    ex instanceof SecurityException
                            ? 403
                            : ex instanceof NoSuchElementException
                                    ? 404
                                    : ex instanceof ConcurrentModificationException
                                            ? 409
                                            : ex instanceof IllegalArgumentException ? 400 : 503;

            res.setStatus(status);
            JsonObject json = new JsonObject();

            json.addProperty(
                    "error",
                    status == 503
                            ? "Trình vẽ đang bận hoặc tạm thời không khả dụng. Vui lòng thử lại."
                            : ex.getMessage());
            res.getWriter().write(json.toString());
        }
    }

    private void checkEditable(
            long student, long assignment, long submission, long task, long requirement) {
        var page = assignments.page(student, assignment, submission);

        if (page.submissionId() != submission
                || page.status() != SubmissionStatus.IN_PROGRESS
                || page.deadline() == null
                || !page.serverTime().isBefore(page.deadline()))
            throw new ConcurrentModificationException(
                    "Lượt làm đã nộp hoặc hết hạn. Không thể sửa sơ đồ.");

        if (page.assignment().responseType() != ResponseType.ESSAY)
            throw new IllegalArgumentException("Trình vẽ chỉ dùng cho bài tự luận.");

        boolean allowed =
                EssayQuestions.read(page.assignment().taskChecklistJson()).stream()
                        .filter(t -> t.order() == task)
                        .flatMap(t -> t.requirements().stream())
                        .anyMatch(r -> r.order() == requirement
                                && !"TEXT".equals(r.answerType())
                                && r.allowedTools().contains("PLANTUML"));

        if (!allowed)
            throw new IllegalArgumentException("Giảng viên không cho phép dùng PlantUML ở yêu cầu này.");
    }
}
