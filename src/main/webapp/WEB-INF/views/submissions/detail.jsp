<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="ams" tagdir="/WEB-INF/tags" %>
<%@ page import="vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*,java.util.*" %>
<%@ page import="vn.edu.fpt.ams.util.TeacherSubmissionView,vn.edu.fpt.ams.util.EssaySubmissionView,vn.edu.fpt.ams.util.EssayQuestions,vn.edu.fpt.ams.util.WebSupport" %>
<%@ page import="static vn.edu.fpt.ams.util.AssignmentListView.*" %>
<%!
    private String evaluationValue(jakarta.servlet.http.HttpServletRequest request, String name, String saved) {
        Object input = request.getAttribute("evaluationInput");
        if (input instanceof Map<?, ?> && ((Map<?, ?>) input).containsKey(name))
            return (String) ((Map<?, ?>) input).get(name);
        return saved == null ? "" : saved;
    }

    private String historyAction(String action) {
        if (action == null) return "Cập nhật đánh giá";
        switch (action) {
            case "saveEvaluation": return "Lưu chấm dở";
            case "completeEvaluation": return "Hoàn tất chấm";
            case "publishEvaluation":
            case "releaseEvaluation": return "Công bố điểm";
            case "reopenEvaluation": return "Mở lại đánh giá";
            case "reviseReleasedEvaluation": return "Sửa điểm đã công bố";
            case "restoreEvaluation": return "Khôi phục bản chấm trước";
            case "RESUBMITTED": return "Sinh viên nộp bản mới";
            default: return "Cập nhật đánh giá";
        }
    }
%>
<%
    TeacherSubmissionDetailPage data = (TeacherSubmissionDetailPage) request.getAttribute("submissionDetail");
    TeacherEvaluationPage evaluationPage = (TeacherEvaluationPage) request.getAttribute("evaluationPage");
    TeacherSubmissionFilter filters = (TeacherSubmissionFilter) request.getAttribute("submissionFilters");
    if (filters == null) filters = TeacherSubmissionFilter.from(null, null);
    String base = request.getContextPath();
    String error = (String) request.getAttribute("submissionError");
    String evaluationError = (String) request.getAttribute("evaluationError");
    String success = (String) request.getAttribute("evaluationSuccess");
    boolean conflict = Boolean.TRUE.equals(request.getAttribute("evaluationConflict"));
    Long selectedId = data == null ? (Long) request.getAttribute("selectedAssignmentId") : data.assignment().assignment().assignmentId();
    String backUrl = selectedId == null ? base + "/teacher/assignments"
            : vn.edu.fpt.ams.util.TeacherSubmissionNavigation.listUrl(base, selectedId, filters);
%>
<!doctype html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Chấm bài và nhận xét | AMS</title>
    <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-list.css">
    <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-detail.css">
    <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-dialog.css">
    <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
    <link rel="stylesheet" href="<%= escape(base) %>/css/submissions.css">
    <link rel="stylesheet" href="<%= escape(base) %>/css/teacher-evaluation.css">
    <link rel="stylesheet" href="<%= escape(base) %>/css/teacher-grading.css">
    <link rel="stylesheet" href="<%= escape(base) %>/css/submission-diagram.css">
    <script src="<%= escape(base) %>/js/teacher-evaluation.js?v=20261108-confirm" defer></script>
    <script src="<%= escape(base) %>/js/submission-diagram.js" defer></script>
</head>
<body class="ams-ui teacher-app">
    <%@ include file="/WEB-INF/views/shared/shell.jspf" %>
    <main class="ams-content submission-page" id="page-content">
        <%@ include file="/WEB-INF/views/shared/action-notice.jspf" %>
        <nav class="breadcrumb" aria-label="Đường dẫn">
            <a href="<%= escape(base) %>/teacher/dashboard">Bảng điều khiển</a><span>/</span>
            <a href="<%= escape(backUrl) %>">Bài nộp</a><span>/</span><span>Chấm bài</span>
        </nav>
        <div id="evaluation-notification" class="evaluation-notice" role="status" tabindex="-1" hidden></div>
        <% if (error != null || data == null) { %>
            <section class="notice error" role="alert"><h1>Không thể tải bài nộp</h1>
                <p><%= escape(error == null ? "Bài nộp không khả dụng." : error) %></p>
                <a class="button" href="<%= escape(backUrl) %>">Về danh sách bài nộp</a>
            </section>
        <% } else {
            Assignment assignment = data.assignment().assignment();
            TeacherSubmissionRow row = data.row();
            Submission submission = row.submission();
            boolean essayMode = assignment.responseType() == ResponseType.ESSAY;
            SubmissionEvaluation evaluation = evaluationPage == null ? null : evaluationPage.evaluation();
            boolean gradingAvailable = essayMode && evaluation != null && !conflict
                    && (evaluationPage.blockedReason() == null || evaluationPage.blockedReason().isBlank());
            boolean evaluationEditable = gradingAvailable && Set.of(SubmissionStatus.SUBMITTED,
                    SubmissionStatus.GRADED, SubmissionStatus.RELEASED).contains(submission.status());
            boolean released = submission.status() == SubmissionStatus.RELEASED;
            String detailUrl = vn.edu.fpt.ams.util.TeacherSubmissionNavigation.detailUrl(
                    base, assignment.assignmentId(), submission.submissionId(), filters);
        %>
            <% if (success != null) { %><p class="evaluation-notice evaluation-success" role="status"><%= escape(success) %></p><% } %>
            <% if (evaluationError != null) { %>
                <div class="evaluation-notice evaluation-error" role="alert" tabindex="-1" data-evaluation-error>
                    <p><%= escape(evaluationError) %></p>
                    <% if (conflict) { %><a href="<%= escape(detailUrl) %>">Tải lại bản mới nhất</a><% } %>
                </div>
            <% } %>
            <% if (essayMode && evaluationPage != null && evaluationPage.blockedReason() != null) { %>
                <p class="evaluation-notice evaluation-error" role="alert"><%= escape(evaluationPage.blockedReason()) %></p>
                <a class="button" href="<%= escape(base) %>/teacher/assignments?mode=view&amp;assignmentId=<%= assignment.assignmentId() %>">Đến bài tập để kiểm tra hoặc khóa nhận bài</a>
            <% } %>
            <div class="page-heading">
                <div><h1><%= escape(row.studentName()) %></h1><p><%= escape(row.studentEmail()) %></p></div>
                <div class="submission-detail-actions">
                    <span class="badge submission-status <%= submission.status().name().toLowerCase(Locale.ROOT) %>"><%= escape(TeacherSubmissionView.statusLabel(submission.status())) %></span>
                    <a class="button" href="<%= escape(backUrl) %>">Về danh sách bài nộp</a>
                </div>
            </div>
            <% if (evaluationEditable) { %>
                <form id="evaluation-form" method="post" action="<%= escape(base) %>/teacher/submissions" data-evaluation-form>
                    <input type="hidden" name="csrf" value="<%= escape(WebSupport.csrf(request)) %>">
                    <input type="hidden" name="assignmentId" value="<%= assignment.assignmentId() %>">
                    <input type="hidden" name="submissionId" value="<%= submission.submissionId() %>">
                    <input type="hidden" name="revision" value="<%= evaluation.revision() %>">
                    <input type="hidden" name="confirmRelease" value="yes">
                    <input type="hidden" name="search" value="<%= escape(filters.search()) %>">
                    <input type="hidden" name="status" value="<%= escape(filters.statusValue()) %>">
                    <input type="hidden" name="page" value="<%= filters.page() %>">
                </form>
            <% } %>
            <div class="grading-layout">
                <div class="grading-main">
                    <% if (essayMode) {
                        var essay = EssaySubmissionView.read(assignment.taskChecklistJson(), submission.content());
                        List<EssayQuestions.Task> questions = evaluationPage == null ? List.of() : evaluationPage.questions();
                        Map<String, EssaySubmissionView.RequirementAnswer> answers = new HashMap<>();
                        for (var item : essay.items()) for (var answer : item.requirements())
                            answers.put(item.order() + "_" + answer.order(), answer);
                        boolean firstTab = true;
                    %>
                        <div class="grading-tabs" role="tablist" aria-label="Câu hỏi và yêu cầu">
                            <% for (var task : questions) for (var requirement : task.requirements()) {
                                String key = task.order() + "_" + requirement.order();
                            %>
                                <button type="button" id="tab-<%= key %>" role="tab" aria-selected="<%= firstTab %>"
                                    aria-controls="answer-<%= key %>" tabindex="<%= firstTab ? 0 : -1 %>" data-grade-tab="<%= key %>">Câu <%= task.order() %> · Yêu cầu <%= requirement.order() %></button>
                            <% firstTab = false; } %>
                        </div>
                        <% boolean firstPanel = true;
                            for (var task : questions) for (var requirement : task.requirements()) {
                                String key = task.order() + "_" + requirement.order();
                                var answer = answers.get(key);
                        %>
                            <section id="answer-<%= key %>" role="tabpanel" aria-labelledby="tab-<%= key %>" data-answer-panel="<%= key %>" <%= firstPanel ? "" : "hidden" %>>
                                <article class="detail-card grading-question">
                                    <p class="review-eyebrow">Câu <%= task.order() %> · <%= escape(TeacherSubmissionView.score(task.points())) %> điểm</p>
                                    <h2 class="content-text"><%= escape(task.title()) %></h2>
                                    <div class="grading-requirement"><h3>Yêu cầu <%= requirement.order() %> · <%= escape(TeacherSubmissionView.score(requirement.points())) %> điểm</h3>
                                        <p class="content-text"><%= escape(requirement.title()) %></p>
                                    </div>
                                    <h3>Câu trả lời của sinh viên</h3>
                                    <% if (answer != null && answer.answer() != null && !answer.answer().isBlank()) { %>
                                        <div class="content-text essay-answer-text"><%= escape(answer.answer()) %></div>
                                    <% } %>
                                    <% if (answer != null && answer.image() != null && !answer.image().isBlank()) { %>
                                        <figure class="essay-diagram-answer"><img src="<%= escape(answer.image()) %>" alt="Sơ đồ câu <%= task.order() %>, yêu cầu <%= requirement.order() %>">
                                            <figcaption><button type="button" data-diagram-zoom>Phóng to sơ đồ</button></figcaption>
                                        </figure>
                                    <% } %>
                                    <% if (answer == null || ((answer.answer() == null || answer.answer().isBlank()) && (answer.image() == null || answer.image().isBlank()))) { %>
                                        <p class="empty-content">Chưa có câu trả lời riêng cho yêu cầu này.</p>
                                    <% } %>
                                    <section class="grading-rubric"><h3>Tiêu chí chấm yêu cầu <%= requirement.order() %></h3>
                                        <p class="content-text"><%= escape(requirement.rubric().isBlank() ? "Chưa có tiêu chí riêng cho yêu cầu này." : requirement.rubric()) %></p>
                                    </section>
                                </article>
                                <section class="detail-card grading-rubric question-rubric"><h3>Tiêu chí chung của câu <%= task.order() %></h3>
                                    <p class="content-text"><%= escape(task.rubric().isBlank() ? "Chưa có tiêu chí chung cho câu hỏi này." : task.rubric()) %></p>
                                    <p class="submission-note">Tiêu chí chấm chỉ dành cho giảng viên.</p>
                                </section>
                            </section>
                        <% firstPanel = false; } %>
                        <% if (essay.legacyContent() != null && !essay.legacyContent().isBlank()) { %>
                            <section class="detail-card"><h2>Nội dung bài làm lưu theo định dạng cũ</h2>
                                <p class="submission-note">Đối chiếu đoạn trả lời chung này với các yêu cầu khi chấm.</p>
                                <div class="content-text essay-answer-text"><%= escape(essay.legacyContent()) %></div>
                            </section>
                        <% } %>
                        <% for (var unmatched : essay.unmatched()) { %>
                            <section class="detail-card"><h3>Câu trả lời chưa ghép được</h3><p><%= escape(unmatched.reference()) %></p><p class="content-text"><%= escape(unmatched.text()) %></p></section>
                        <% } %>
                        <% if (questions.isEmpty()) { %>
                            <section class="detail-card"><h2>Nội dung bài nộp</h2>
                                <% for (var item : essay.items()) { %><h3 class="content-text"><%= escape(item.title()) %></h3><% } %>
                                <p class="submission-note">Chưa có bảng yêu cầu hợp lệ để chấm.</p>
                            </section>
                        <% } %>
                    <% } else {
                        var objective = TeacherSubmissionView.answers(submission.answer(), data.assignment().questions());
                    %>
                        <section class="detail-card student-response"><h2>Câu hỏi và câu trả lời của sinh viên</h2>
                            <p class="answer-legend"><span class="legend-correct">✓ Đáp án đúng</span><span class="legend-incorrect">✗ Sinh viên chọn sai</span></p>
                            <% for (var answer : objective.items()) { %>
                                <article class="submission-answer"><h3 class="content-text"><%= escape(answer.question()) %></h3>
                                    <ul class="answer-choice-list">
                                        <% for (var choice : answer.choices()) {
                                            boolean correct = Boolean.TRUE.equals(choice.correct());
                                            boolean wrong = choice.selected() && Boolean.FALSE.equals(choice.correct());
                                        %>
                                            <li class="answer-choice <%= correct ? "choice-correct" : wrong ? "choice-incorrect" : "" %>">
                                                <span class="choice-marker"><%= correct ? "✓" : wrong ? "✗" : choice.selected() ? "●" : "○" %></span>
                                                <div><strong><%= escape(choice.label()) %>.</strong> <span class="content-text"><%= escape(choice.text()) %></span>
                                                    <% if (choice.selected()) { %><p class="choice-caption">Sinh viên đã chọn</p><% } %>
                                                </div>
                                            </li>
                                        <% } %>
                                    </ul><p>Điểm đạt được <strong><%= escape(answer.awardedPoints()) %></strong></p>
                                </article>
                            <% } %>
                            <% if (objective.invalid()) { %><p class="content-warning">Một số dữ liệu đáp án đã lưu chưa đọc được đầy đủ.</p><% } %>
                        </section>
                    <% } %>
                    <% if (submission.submissionLink() != null && !submission.submissionLink().isBlank()) {
                        String safe = TeacherSubmissionView.safeLink(submission.submissionLink());
                    %>
                        <section class="detail-card"><h3>Liên kết bài làm</h3>
                            <% if (!safe.isEmpty()) { %><a href="<%= escape(safe) %>" target="_blank" rel="noopener noreferrer"><%= escape(submission.submissionLink()) %></a>
                            <% } else { %><p class="content-text"><%= escape(submission.submissionLink()) %></p><% } %>
                        </section>
                    <% } %>
                </div>
                <aside class="grading-sidebar" aria-label="Chấm điểm và thông tin bài nộp">
                    <%@ include file="group-objective-fields.jspf" %>
                    <% if (essayMode && evaluation != null) { %>
                        <section class="detail-card grading-score-card" id="evaluation-heading"><h2>Chấm điểm</h2>
                            <% if (evaluationEditable) { %>
                                <button type="button" class="button" data-ai-grade>Chấm bằng AI</button>
                                <p class="submission-note">Nhận xét do AI viết được đánh dấu màu tím. Giảng viên kiểm tra trước khi lưu.</p>
                            <% } %>
                            <% boolean firstScore = true;
                                for (var task : evaluationPage.questions()) for (var gradingRequirement : task.requirements()) {
                                    int gradingTaskOrder = task.order();
                                    String panelKey = task.order() + "_" + gradingRequirement.order();
                            %>
                                <div data-score-panel="<%= panelKey %>" <%= firstScore ? "" : "hidden" %>>
                                    <h3>Câu <%= task.order() %> · Yêu cầu <%= gradingRequirement.order() %></h3>
                                    <%@ include file="evaluation-fields.jspf" %>
                                </div>
                            <% firstScore = false; } %>
                            <div class="evaluation-total"><span>Tổng điểm</span><strong><output data-evaluation-total><%= escape(TeacherSubmissionView.score(evaluation.total())) %></output> / <%= escape(TeacherSubmissionView.score(assignment.maximumScore())) %></strong>
                                <p data-evaluation-progress data-requirement-count="<%= evaluationPage.questions().stream().mapToInt(task -> task.requirements().size()).sum() %>"></p>
                            </div>
                            <% if (evaluationEditable) {
                                String overall = evaluationValue(request, "feedback", evaluation.feedback());
                                String overallAi = evaluationValue(request, "feedbackAi", String.valueOf(evaluation.feedbackAi()));
                            %>
                                <label for="evaluation-feedback">Nhận xét chung</label>
                                <textarea id="evaluation-feedback" name="feedback" form="evaluation-form" rows="4" maxlength="5000"
                                    data-saved-value="<%= escape(evaluation.feedback()) %>"
                                    data-ai-feedback="feedbackAi" class="<%= overallAi.equals("true") ? "ai-feedback" : "" %>"><%= escape(overall) %></textarea>
                                <input type="hidden" name="feedbackAi" form="evaluation-form" value="<%= escape(overallAi) %>" data-saved-value="<%= evaluation.feedbackAi() %>">
                                <span class="ai-feedback-label" data-ai-label="feedbackAi" <%= overallAi.equals("true") ? "" : "hidden" %>>Nhận xét do AI viết</span>
                                <% if (released) { %>
                                    <label for="change-reason">Lý do sửa điểm/nhận xét đã công bố *</label>
                                    <textarea id="change-reason" name="changeReason" form="evaluation-form" rows="3" maxlength="2000" required><%= escape(evaluationValue(request, "changeReason", "")) %></textarea>
                                    <p class="submission-note">Mỗi lần sửa được ghi vào nhật ký và cập nhật kết quả sinh viên thấy.</p>
                                <% } %>
                                <div class="evaluation-actions">
                                    <button type="button" class="button" data-reset-evaluation>Khôi phục bản đã lưu gần nhất</button>
                                    <% if (released) { %>
                                        <button type="submit" class="button primary" form="evaluation-form" name="action" value="reviseReleasedEvaluation" data-confirm-publish="evaluation">Lưu sửa điểm đã công bố</button>
                                    <% } else { %>
                                        <button type="submit" class="button" form="evaluation-form" name="action" value="saveEvaluation">Lưu chấm dở</button>
                                        <button type="submit" class="button" form="evaluation-form" name="action" value="completeEvaluation">Hoàn tất chấm</button>
                                        <button type="submit" class="button primary" form="evaluation-form" name="action" value="publishEvaluation" data-confirm-publish="evaluation">Công bố điểm</button>
                                    <% } %>
                                </div>
                            <% } else { %>
                                <h3>Nhận xét chung</h3><p class="content-text <%= evaluation.feedbackAi() ? "ai-feedback" : "" %>"><%= escape(evaluation.feedback().isBlank() ? "Chưa có nhận xét." : evaluation.feedback()) %></p>
                            <% } %>
                        </section>
                    <% } %>
                    <section class="detail-card"><h2>Thông tin lượt làm</h2>
                        <dl class="metadata">
                            <dt>Sinh viên</dt><dd><%= escape(row.studentName()) %></dd>
                            <dt>Email</dt><dd><%= escape(row.studentEmail()) %></dd>
                            <dt>Trạng thái</dt><dd><%= escape(TeacherSubmissionView.statusLabel(submission.status())) %></dd>
                            <dt>Nộp lúc (UTC+07)</dt><dd><%= escape(TeacherSubmissionView.submittedAt(submission.submittedAt())) %></dd>
                            <dt>Điểm đã lưu</dt><dd><%= submission.score() == null ? "Chưa chấm" : escape(TeacherSubmissionView.score(submission.score())) %></dd>
                            <dt>Người chấm</dt><dd><%= escape(row.graderName() == null ? "Chưa ghi nhận" : row.graderName()) %></dd>
                            <dt>Chấm lúc (UTC+07)</dt><dd><%= deadline(submission.gradedAt()) %></dd>
                            <dt>Công bố lúc (UTC+07)</dt><dd><%= deadline(submission.releasedAt()) %></dd>
                        </dl>
                    </section>
                    <section class="detail-card"><h2>Bài tập</h2><h3 class="content-text"><%= escape(assignment.title()) %></h3>
                        <dl class="metadata"><dt>Lớp</dt><dd><%= escape(data.assignment().classCode()) %></dd>
                            <dt>Hình thức</dt><dd><%= label(assignment.responseType()) %></dd>
                            <dt>Điểm tối đa</dt><dd><%= escape(TeacherSubmissionView.score(assignment.maximumScore())) %></dd>
                            <dt>Hạn nộp (UTC+07)</dt><dd><%= deadline(assignment.dueAt()) %></dd>
                        </dl>
                    </section>
                </aside>
            </div>
            <%@ include file="group-grade.jspf" %>
            <% if (essayMode && evaluationPage != null) {
                TeacherEvaluationHistory history = evaluationPage.history();
            %>
                <section class="detail-card evaluation-history" id="evaluation-history"><h2>Nhật ký chấm và sửa điểm</h2>
                    <% if (history.entries().isEmpty()) { %><p>Chưa có lần cập nhật đánh giá.</p><% } else { %>
                        <div class="table-scroll"><table><thead><tr><th>Thời gian (UTC+07)</th><th>Người thực hiện</th><th>Thao tác</th><th>Điểm trước</th><th>Điểm sau</th><th>Lý do</th><th>Khôi phục</th></tr></thead>
                            <tbody><% for (var entry : history.entries()) { %>
                                <tr><td><%= deadline(entry.createdAt()) %></td><td><%= escape(entry.actor() == null ? "Hệ thống" : entry.actor()) %></td>
                                    <td><%= escape(historyAction(entry.action())) %></td>
                                    <td><%= entry.previousScore() == null ? "—" : escape(TeacherSubmissionView.score(entry.previousScore())) %></td>
                                    <td><%= entry.nextScore() == null ? "—" : escape(TeacherSubmissionView.score(entry.nextScore())) %></td>
                                    <td class="content-text"><%= escape(entry.reason() == null || entry.reason().isBlank() ? "—" : entry.reason()) %></td>
                                    <td><% if (evaluationEditable && entry.submissionId() != null && entry.submissionId() == submission.submissionId()) { %>
                                        <button class="button" type="button" data-restore-history="<%= entry.id() %>">Khôi phục bản trước</button>
                                    <% } else { %>—<% } %></td>
                                </tr>
                            <% } %></tbody>
                        </table></div>
                        <ams:pagination currentPage="<%= history.page() %>"
                            totalPages="<%= history.pages() %>"
                            baseUrl='<%= detailUrl + "#evaluation-history" %>'
                            pageParameter="historyPage" ariaLabel="Trang nhật ký" />
                    <% } %>
                </section>
            <% } %>
            <% if (evaluationEditable) { %>
                <dialog id="restore-evaluation-dialog" class="evaluation-dialog">
                    <form method="post" action="<%= escape(base) %>/teacher/submissions"><h2>Khôi phục bản chấm trước</h2>
                        <p>Điểm và nhận xét sẽ được thay bằng bản trước của lần cập nhật đã chọn. Thao tác được ghi vào nhật ký.</p>
                        <% if (released) { %><p>Kết quả đã công bố cho sinh viên cũng sẽ được cập nhật.</p><% } %>
                        <input type="hidden" name="csrf" value="<%= escape(WebSupport.csrf(request)) %>">
                        <input type="hidden" name="assignmentId" value="<%= assignment.assignmentId() %>">
                        <input type="hidden" name="submissionId" value="<%= submission.submissionId() %>">
                        <input type="hidden" name="revision" value="<%= evaluation.revision() %>">
                        <input type="hidden" name="action" value="restoreEvaluation">
                        <input type="hidden" name="historyId" id="restore-history-id">
                        <label for="restore-reason">Lý do khôi phục<%= released ? " *" : "" %></label>
                        <textarea id="restore-reason" name="changeReason" rows="3" maxlength="2000" <%= released ? "required" : "" %>></textarea>
                        <div class="evaluation-actions"><button class="button primary" type="submit">Xác nhận khôi phục</button><button class="button" type="button" data-close-restore>Hủy</button></div>
                    </form>
                </dialog>
            <% } %>
        <% } %>
    </main>
</div>
</body>
</html>
