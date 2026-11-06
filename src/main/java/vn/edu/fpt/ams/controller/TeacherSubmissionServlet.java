package vn.edu.fpt.ams.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import vn.edu.fpt.ams.dto.*;
import vn.edu.fpt.ams.model.AccountStatus;
import vn.edu.fpt.ams.model.UserRole;
import vn.edu.fpt.ams.service.TeacherSubmissionService;
import vn.edu.fpt.ams.service.TeacherEvaluationService;
import vn.edu.fpt.ams.util.TeacherSubmissionNavigation;
import vn.edu.fpt.ams.util.WebSupport;

/** Teacher-owned submission review and explicit evaluation transitions. */
@WebServlet("/teacher/submissions")
public class TeacherSubmissionServlet extends HttpServlet {
    private final TeacherSubmissionService service;
    private final TeacherEvaluationService evaluations;
    private static final Set<String> ACTIONS =
            Set.of("saveEvaluation", "completeEvaluation", "reopenEvaluation", "releaseEvaluation");

    public TeacherSubmissionServlet() {
        this(new TeacherSubmissionService(), new TeacherEvaluationService());
    }

    TeacherSubmissionServlet(TeacherSubmissionService service) {
        this(service, null);
    }

    TeacherSubmissionServlet(
            TeacherSubmissionService service, TeacherEvaluationService evaluations) {
        this.service = service;
        this.evaluations = evaluations;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        res.setHeader("Cache-Control", "no-store");
        Long teacherId = teacher(req, res);

        if (teacherId == null) return;

        String view = "/WEB-INF/views/submissions/list.jsp";

        try {
            singletonParameters(req);

            if (req.getParameter("action") != null)
                throw new IllegalArgumentException(
                        "Thao tác đánh giá phải được gửi bằng biểu mẫu.");

            String mode = req.getParameter("mode");

            if (mode != null && !mode.isBlank() && !"list".equals(mode) && !"view".equals(mode))
                throw new IllegalArgumentException("Thao tác với bài nộp không hợp lệ.");

            long assignmentId = positiveId(req.getParameter("assignmentId"));
            var filter =
                    TeacherSubmissionFilter.from(
                            req.getParameter("search"),
                            req.getParameter("status"),
                            req.getParameter("page"));

            req.setAttribute("selectedAssignmentId", assignmentId);
            req.setAttribute("submissionFilters", filter);

            if ("view".equals(mode)) {
                view = "/WEB-INF/views/submissions/detail.jsp";
                long submissionId = positiveId(req.getParameter("submissionId"));

                if (evaluations == null)
                    req.setAttribute(
                            "submissionDetail",
                            service.detailPage(teacherId, assignmentId, submissionId));
                else showPage(req, evaluations.page(teacherId, assignmentId, submissionId));

                String saved = req.getParameter("evaluationSaved");

                if (ACTIONS.contains(saved == null ? "" : saved))
                    req.setAttribute("evaluationSuccess", successMessage(saved));
            } else {
                req.setAttribute(
                        "submissionPage", service.listPage(teacherId, assignmentId, filter));
            }
        } catch (IllegalArgumentException ex) {
            res.sendError(400, "Bài tập, bài nộp hoặc bộ lọc không hợp lệ.");

            return;
        } catch (NoSuchElementException ex) {
            // Missing and inaccessible records have the same response.
            res.sendError(404, "Không tìm thấy bài nộp hoặc bài tập, hoặc bạn không có quyền xem.");

            return;
        } catch (SecurityException ex) {
            res.sendError(403, "Cần đăng nhập bằng tài khoản giảng viên đang hoạt động.");

            return;
        } catch (IllegalStateException ex) {
            getServletContext().log("Teacher submissions could not be loaded", ex);
            res.setStatus(503);
            req.setAttribute(
                    "submissionError", "Không thể tải danh sách bài nộp. Vui lòng thử lại.");
        }

        req.getRequestDispatcher(view).forward(req, res);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        res.setHeader("Cache-Control", "no-store");
        req.setCharacterEncoding("UTF-8");
        Long teacherId = teacher(req, res);

        if (teacherId == null) return;

        TeacherEvaluationPage page = null;
        long assignmentId = 0, submissionId = 0;

        try {
            // Reject ambiguous CSRF/identity values before looking up private student work.
            for (String name : List.of("csrf", "assignmentId", "submissionId"))
                singleton(req, name);

            WebSupport.checkCsrf(req);
            assignmentId = positiveId(req.getParameter("assignmentId"));
            submissionId = positiveId(req.getParameter("submissionId"));

            if (evaluations == null)
                throw new IllegalArgumentException("Thao tác đánh giá không hợp lệ.");

            page = evaluations.page(teacherId, assignmentId, submissionId);
            showPage(req, page);
            req.setAttribute("selectedAssignmentId", assignmentId);
            req.setAttribute("evaluationInput", rawInput(req));
            singletonParameters(req);
            var filter =
                    TeacherSubmissionFilter.from(
                            req.getParameter("search"),
                            req.getParameter("status"),
                            req.getParameter("page"));

            req.setAttribute("submissionFilters", filter);
            String action = req.getParameter("action");

            if (action == null || !ACTIONS.contains(action))
                throw new IllegalArgumentException("Thao tác đánh giá không hợp lệ.");

            long revision = revision(req.getParameter("revision"));

            // An invalid stale form must not be redisplayed with the latest revision token.
            if (revision != page.evaluation().revision())
                throw new ConcurrentModificationException(
                        "Đánh giá đã thay đổi. Vui lòng tải lại trang.");

            List<SubmissionEvaluation.Mark> marks = List.of();
            String feedback = "";

            if (action.equals("saveEvaluation") || action.equals("completeEvaluation")) {
                marks = marks(req, page);
                feedback = optional(req, "feedback");

                if (feedback.length() > 5000)
                    throw new IllegalArgumentException(
                            "Nhận xét chung không được vượt quá 5000 ký tự.");
            }

            if (action.equals("releaseEvaluation")
                    && !"yes".equals(req.getParameter("confirmRelease")))
                throw new IllegalArgumentException(
                        "Vui lòng xác nhận công bố điểm và nhận xét cho sinh viên.");

            evaluations.apply(
                    teacherId, assignmentId, submissionId, action, revision, marks, feedback);
            res.setStatus(HttpServletResponse.SC_SEE_OTHER);
            res.setHeader(
                    "Location",
                    TeacherSubmissionNavigation.detailUrl(
                                    req.getContextPath(), assignmentId, submissionId, filter)
                            + "&evaluationSaved="
                            + action);

            return;
        } catch (ConcurrentModificationException ex) {
            res.setStatus(409);
            req.removeAttribute("evaluationInput");
            req.setAttribute("evaluationConflict", true);
            req.setAttribute(
                    "evaluationError",
                    "Đánh giá đã thay đổi trong một phiên khác. Dữ liệu bạn gửi chưa được lưu. Tải lại bài nộp và kiểm tra kết quả mới nhất trước khi tiếp tục.");
            // Never combine stale form values with a fresh revision token.
        } catch (IllegalArgumentException ex) {
            if (page == null) {
                res.sendError(400, "Biểu mẫu đánh giá không hợp lệ.");

                return;
            }

            res.setStatus(400);
            req.setAttribute("evaluationError", ex.getMessage());
        } catch (NoSuchElementException ex) {
            res.sendError(404, "Không tìm thấy bài nộp hoặc bài tập, hoặc bạn không có quyền xem.");

            return;
        } catch (SecurityException ex) {
            res.sendError(
                    403,
                    "Phiên biểu mẫu không hợp lệ hoặc bạn không có quyền đánh giá bài nộp này.");

            return;
        } catch (IllegalStateException ex) {
            getServletContext().log("Teacher evaluation could not be saved", ex);
            res.setStatus(503);

            if (page == null)
                req.setAttribute("submissionError", "Không thể tải bài nộp. Vui lòng thử lại.");
            else
                req.setAttribute(
                        "evaluationError",
                        "Chưa thể lưu đánh giá. Nội dung bạn nhập được giữ lại; vui lòng thử lại.");
        }

        req.getRequestDispatcher("/WEB-INF/views/submissions/detail.jsp").forward(req, res);
    }

    private static void showPage(HttpServletRequest req, TeacherEvaluationPage page) {
        req.setAttribute("evaluationPage", page);
        req.setAttribute("submissionDetail", page.detail());
    }

    private static String optional(HttpServletRequest req, String name) {
        String value = req.getParameter(name);

        return value == null ? "" : value;
    }

    private static long positiveId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException("Mã bản ghi không hợp lệ.");

        return WebSupport.id(value);
    }

    private static long revision(String value) {
        if (value == null || !value.matches("0|[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException(
                    "Phiên bản đánh giá không hợp lệ. Vui lòng tải lại trang.");

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Phiên bản đánh giá không hợp lệ. Vui lòng tải lại trang.");
        }
    }

    private static void singleton(HttpServletRequest req, String name) {
        String[] values = req.getParameterValues(name);

        if (values != null && values.length != 1)
            throw new IllegalArgumentException(
                    "Biểu mẫu có trường bị lặp. Vui lòng tải lại trang.");
    }

    private static void singletonParameters(HttpServletRequest req) {
        Map<String, String[]> parameters = req.getParameterMap();

        if (parameters != null) for (String name : parameters.keySet()) singleton(req, name);
    }

    private static Map<String, String> rawInput(HttpServletRequest req) {
        Map<String, String> values = new HashMap<>();
        Map<String, String[]> parameters = req.getParameterMap();

        if (parameters != null)
            parameters.forEach(
                    (name, input) -> {
                        if ((name.startsWith("score_")
                                        || name.startsWith("requirementFeedback_")
                                        || name.equals("feedback"))
                                && input != null
                                && input.length > 0) values.put(name, input[0]);
                    });

        return values;
    }

    private static List<SubmissionEvaluation.Mark> marks(
            HttpServletRequest req, TeacherEvaluationPage page) {
        List<SubmissionEvaluation.Mark> marks = new ArrayList<>();
        Set<String> fields = new HashSet<>();

        for (var task : page.questions())
            for (var requirement : task.requirements()) {
                String suffix = task.order() + "_" + requirement.order();
                String scoreName = "score_" + suffix,
                        feedbackName = "requirementFeedback_" + suffix;

                fields.add(scoreName);
                fields.add(feedbackName);
                String value = optional(req, scoreName), feedback = optional(req, feedbackName);
                BigDecimal score = null;

                if (!value.isEmpty()) {
                    if (!value.matches("[0-9]{1,3}(\\.[0-9]{1,2})?"))
                        throw new IllegalArgumentException(
                                "Điểm phải là số không âm, có tối đa 2 chữ số thập phân. Để trống nếu chưa chấm.");

                    score = new BigDecimal(value);

                    if (score.compareTo(requirement.points()) > 0)
                        throw new IllegalArgumentException(
                                "Câu "
                                        + task.order()
                                        + ", yêu cầu "
                                        + requirement.order()
                                        + ": điểm vượt quá điểm tối đa.");
                }

                if (feedback.length() > 2000)
                    throw new IllegalArgumentException(
                            "Nhận xét mỗi yêu cầu không được vượt quá 2000 ký tự.");

                marks.add(
                        new SubmissionEvaluation.Mark(
                                task.order(), requirement.order(), score, feedback));
            }

        Map<String, String[]> parameters = req.getParameterMap();

        if (parameters != null)
            for (String name : parameters.keySet()) {
                if ((name.startsWith("score_") || name.startsWith("requirementFeedback_"))
                        && !fields.contains(name))
                    throw new IllegalArgumentException(
                            "Biểu mẫu chứa yêu cầu không thuộc bài tập này.");
            }

        return marks;
    }

    private static String successMessage(String action) {
        return switch (action) {
            case "saveEvaluation" -> "Đã lưu đánh giá nháp. Sinh viên chưa xem được kết quả.";
            case "completeEvaluation" ->
                    "Đã hoàn tất chấm. Kết quả chưa được công bố cho sinh viên.";
            case "reopenEvaluation" -> "Đã mở lại đánh giá để chỉnh sửa.";
            case "releaseEvaluation" ->
                    "Đã công bố điểm và nhận xét cho sinh viên. Đánh giá đã được khóa.";
            default -> "";
        };
    }

    private Long teacher(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession(false);

        if (session == null
                || !(session.getAttribute("userId") instanceof Long id)
                || id <= 0
                || session.getAttribute("accountStatus") != AccountStatus.ACTIVE) {
            res.sendError(401, "Vui lòng đăng nhập.");

            return null;
        }

        if (session.getAttribute("role") != UserRole.TEACHER) {
            res.sendError(403);

            return null;
        }

        return (Long) session.getAttribute("userId");
    }
}
