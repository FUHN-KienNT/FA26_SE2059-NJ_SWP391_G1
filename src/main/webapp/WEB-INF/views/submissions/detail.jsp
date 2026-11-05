<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*" %>
<%@ page import="vn.edu.fpt.ams.util.TeacherSubmissionView" %>
<%@ page import="vn.edu.fpt.ams.util.EssaySubmissionView" %>
<%@ page import="vn.edu.fpt.ams.util.EssayQuestions,vn.edu.fpt.ams.util.WebSupport,java.util.*,java.math.BigDecimal" %>
<%@ page import="static vn.edu.fpt.ams.util.AssignmentListView.*" %>

<%!
    private String evaluationValue(
            jakarta.servlet.http.HttpServletRequest request, String name, String saved) {
        Object input = request.getAttribute("evaluationInput");
        if (input instanceof Map<?, ?>) {
            Map<?, ?> values = (Map<?, ?>) input;
            if (values.containsKey(name)) return (String) values.get(name);
        }
        return saved == null ? "" : saved;
    }
%>

<%
    TeacherSubmissionDetailPage data =
            (TeacherSubmissionDetailPage) request.getAttribute("submissionDetail");
    TeacherSubmissionFilter filters =
            (TeacherSubmissionFilter) request.getAttribute("submissionFilters");
    if (filters == null) filters = TeacherSubmissionFilter.from(null, null);
    String error = (String) request.getAttribute("submissionError");
    String base = request.getContextPath();
    TeacherEvaluationPage evaluationPage =
            (TeacherEvaluationPage) request.getAttribute("evaluationPage");
    SubmissionEvaluation evaluation =
            evaluationPage == null ? null : evaluationPage.evaluation();
    String evaluationError = (String) request.getAttribute("evaluationError");
    String evaluationSuccess = (String) request.getAttribute("evaluationSuccess");
    boolean evaluationConflict =
            Boolean.TRUE.equals(request.getAttribute("evaluationConflict"));
    Long assignmentId =
            data == null
                    ? (Long) request.getAttribute("selectedAssignmentId")
                    : data.assignment().assignment().assignmentId();
    String backUrl =
            assignmentId == null
                    ? base + "/teacher/assignments"
                    : vn.edu.fpt.ams.util.TeacherSubmissionNavigation.listUrl(
                            base, assignmentId, filters);
%>

<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Chi tiết bài nộp | AMS</title>
        <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-list.css">
        <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-detail.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>

        <link rel="stylesheet" href="<%= escape(base) %>/css/submissions.css">
        <link rel="stylesheet" href="<%= escape(base) %>/css/teacher-evaluation.css">
        <script src="<%= escape(base) %>/js/teacher-evaluation.js" defer></script>
        <link rel="stylesheet" href="<%= escape(base) %>/css/submission-diagram.css">
        <script src="<%= escape(base) %>/js/submission-diagram.js" defer></script>
    </head>
    <body class="ams-ui teacher-app">
        <%@ include file="/WEB-INF/views/shared/shell.jspf" %>

        <main class="ams-content submission-page" id="page-content">
            <nav class="breadcrumb" aria-label="Đường dẫn">
                <a href="<%= escape(base) %>/teacher/dashboard">Bảng điều khiển</a><span aria-hidden="true">/</span><a
                    href="<%= escape(backUrl) %>">Bài nộp</a>
                <span aria-hidden="true">/</span><span aria-current="page">Chi tiết</span>
            </nav>

            <% if (error != null) { %>
                <section class="notice error" role="alert">
                    <h1> Không thể tải bài nộp </h1>
                    <p> <%= escape(error) %> </p>
                    <a href="">Thử lại</a> · <a href="<%= escape(backUrl) %>">Về danh sách bài nộp</a>
                </section>
            <% } else if (data == null) { %>
                <section class="empty-state">
                    <h1> Bài nộp không khả dụng </h1>
                    <p> Không thể tải bài nộp này. </p>
                    <a class="button back-button" href="<%= escape(backUrl) %>">Về danh sách bài nộp</a>
                </section>
            <%
                } else {
                    Assignment assignment = data.assignment().assignment();
                    TeacherSubmissionRow row = data.row();
                    Submission submission = row.submission();
                    String safeLink = TeacherSubmissionView.safeLink(submission.submissionLink());
                    boolean essayEvaluation =
                            evaluationPage != null && assignment.responseType() == ResponseType.ESSAY;
                    boolean gradingAvailable =
                            essayEvaluation
                                    && evaluation != null
                                    && (evaluationPage.blockedReason() == null
                                            || evaluationPage.blockedReason().isBlank())
                                    && !evaluationConflict;
                    boolean evaluationEditable =
                            gradingAvailable && submission.status() == SubmissionStatus.SUBMITTED;
                    boolean evaluationComplete =
                            gradingAvailable && submission.status() == SubmissionStatus.GRADED;
                    Set<String> displayedGradingFields = new HashSet<>();
                    String detailUrl =
                            vn.edu.fpt.ams.util.TeacherSubmissionNavigation.detailUrl(
                                    base, assignment.assignmentId(), submission.submissionId(), filters);
            %>

                <div class="page-heading">
                    <div>
                        <p class="eyebrow"> Chi tiết bài nộp </p>
                        <h1> <%= escape(row.studentName()) %> · Lượt làm <%= submission.attemptNumber() %> </h1>
                        <p> <%= escape(row.studentEmail()) %> </p>
                    </div>
                    <div class="submission-detail-actions">
                        <span class="badge submission-status <%= submission.status().name().toLowerCase(java.util.Locale.ROOT) %>"><%= escape(TeacherSubmissionView.statusLabel(submission.status())) %></span>
                        <a class="button" href="<%= escape(backUrl) %>">Về danh sách bài nộp</a>

                        <% if (evaluationEditable || evaluationComplete) { %>
                            <a class="button primary" href="#evaluation-heading"><%= evaluationComplete?"Công bố điểm và nhận xét":"Chấm điểm và nhận xét" %></a>
                        <% } %>

                    </div>
                </div>

                <section class="submission-card assignment-context" aria-labelledby="assignment-context-heading">
                    <div class="context-heading">
                        <h2 id="assignment-context-heading"> <%= escape(assignment.title()) %> </h2>
                        <span class="badge <%= assignment.status().name().toLowerCase(java.util.Locale.ROOT) %>"><%= label(assignment.status()) %></span>
                    </div>
                    <p> <%= escape(data.assignment().classCode()) %> — <%= escape(data.assignment().className()) %> · <%= label(assignment.responseType()) %> </p>
                </section>

                <% if (assignment.responseType() == ResponseType.OBJECTIVE) { %>
                    <p class="evaluation-notice">Bài trắc nghiệm được hệ thống tự chấm và công bố khi sinh viên nộp. Không có ô
                        chấm tay. <%= submission.status()!=SubmissionStatus.RELEASED?"Lượt làm cũ này chưa có kết quả tự công bố; điểm và trạng thái đã lưu được giữ nguyên.":"Sinh viên đã có thể xem kết quả." %></p>
                <% } else if (evaluationEditable || evaluationComplete) { %>
                    <p class="evaluation-notice">Chấm điểm ở từng yêu cầu bên dưới → Lưu đánh giá nháp hoặc Hoàn tất chấm → Công
                        bố điểm và nhận xét. Sinh viên chỉ thấy điểm sau bước công bố.</p>
                <% } %>

                <% if (evaluationSuccess != null) { %>
                    <p class="evaluation-notice evaluation-success" role="status"><%= escape(evaluationSuccess) %></p>
                <% } %>

                <% if (evaluationError != null) { %>
                    <div class="evaluation-notice evaluation-error" role="alert" tabindex="-1" data-evaluation-error>
                        <p><%= escape(evaluationError) %></p>

                        <% if (evaluationConflict) { %>
                            <a href="<%= escape(detailUrl) %>">Tải lại đánh giá mới nhất</a>
                        <% } %>

                    </div>
                <% } %>

                <%
                    if (essayEvaluation
                            && evaluationPage.blockedReason() != null
                            && !evaluationPage.blockedReason().isBlank()) {
                %>
                    <p class="evaluation-notice evaluation-error" role="alert"><%= escape(evaluationPage.blockedReason()) %></p>
                <% } %>

                <% if (evaluationEditable || evaluationComplete) { %>
                    <form id="evaluation-form" method="post" action="<%= escape(base) %>/teacher/submissions" data-evaluation-form>
                        <input type="hidden" name="csrf" value="<%= escape(WebSupport.csrf(request)) %>">
                        <input type="hidden" name="assignmentId" value="<%= assignment.assignmentId() %>">
                        <input type="hidden" name="submissionId" value="<%= submission.submissionId() %>">
                        <input type="hidden" name="revision" value="<%= evaluation.revision() %>">
                        <input type="hidden" name="search" value="<%= escape(filters.search()) %>">
                        <input type="hidden" name="status" value="<%= escape(filters.statusValue()) %>">
                        <input type="hidden" name="page" value="<%= filters.page() %>">
                    </form>
                <% } %>

                <% if (submission.status() == SubmissionStatus.IN_PROGRESS) { %>
                    <p class="state-message"> Sinh viên đang làm lượt này. Nội dung dưới đây là bản lưu tạm, chưa phải bài đã
                        nộp. </p>
                <% } %>

                <% if (submission.status() == SubmissionStatus.EXPIRED) { %>
                    <p class="state-message"> Lượt làm này đã hết hạn. Nội dung được giữ để tham khảo và không được tính là bài
                        nộp hợp lệ. </p>
                <% } %>

                <% if (submission.status() == SubmissionStatus.VOID) { %>
                    <p class="state-message"> Lượt làm này đã bị vô hiệu hóa. Nội dung được giữ để tham khảo và không được tính
                        là bài nộp hợp lệ. </p>
                <% } %>

                <div class="detail-layout">
                    <div class="detail-content">
                        <%
                            if (assignment.responseType() == ResponseType.ESSAY) {
                                boolean hasInstructions =
                                        assignment.instructions() != null && !assignment.instructions().isBlank();
                        %>

                            <% if (hasInstructions) { %>
                                <details class="detail-card essay-context">
                                    <summary> Hướng dẫn làm bài </summary>

                                    <% if (hasInstructions) { %>
                                        <h3> Hướng dẫn </h3>
                                        <div class="content-text">
                                            <%= escape(assignment.instructions()) %>
                                        </div>
                                    <% } %>

                                </details>
                            <% } %>
                        <% } %>

                        <section class="detail-card student-response" aria-labelledby="submission-content-heading">
                            <h2 id="submission-content-heading"> Câu hỏi và câu trả lời của sinh viên </h2>

                            <%
                                if (assignment.responseType() == ResponseType.ESSAY) {
                                    var essay =
                                            EssaySubmissionView.read(assignment.taskChecklistJson(), submission.content());
                                    boolean hasLegacy = essay.legacyContent() != null && !essay.legacyContent().isBlank();
                            %>

                                <% if (hasLegacy && !essay.items().isEmpty()) { %>
                                    <p class="content-warning"> <%= essay.invalid() ? "Không thể ghép chính xác nội dung đã lưu với từng yêu cầu. Nội dung gốc được giữ nguyên và hiển thị bên dưới."
                                                : "Bài nộp cũ được lưu thành một câu trả lời chung, chưa tách theo từng câu hỏi. Nội dung gốc được hiển thị bên dưới." %> </p>
                                <% } %>

                                <% for (var item : essay.items()) { %>
                                    <article class="submission-answer essay-question-answer">
                                        <div class="essay-question-block">
                                            <p class="review-eyebrow"> <%= item.order() > 0 ? "Câu hỏi " + item.order() : "Câu hỏi" %> </p>

                                            <% if (item.points() != null) { %>
                                                <p class="submission-note"> <%= escape(item.points()) %> điểm </p>
                                            <% } %>

                                            <h3 class="content-text">
                                                <%= escape(item.title()) %>
                                            </h3>

                                            <% if (item.description() != null && !item.description().isBlank()) { %>
                                                <div class="content-text">
                                                    <%= escape(item.description()) %>
                                                </div>
                                            <% } %>

                                        </div>
                                        <div class="essay-response-block">
                                            <%
                                                if (!item.requirements().isEmpty()) {
                                                    for (var requirement : item.requirements()) {
                                            %>
                                                    <section class="essay-requirement-answer">
                                                        <div class="essay-requirement-prompt">
                                                            <p class="review-eyebrow">Yêu cầu <%= requirement.order() %></p>
                                                            <h4 class="content-text"><%= escape(requirement.title()) %></h4>
                                                            <p class="essay-requirement-meta"><%= escape(requirement.answerTypeLabel()) %> · <%= escape(requirement.points()) %> điểm</p>
                                                        </div>

                                                        <% if (requirement.answer() != null && !requirement.answer().isBlank()) { %>
                                                            <div class="content-text essay-answer-text"><%= escape(requirement.answer()) %></div>
                                                        <%
                                                            }
                                                            if (requirement.image() != null && !requirement.image().isBlank()) {
                                                        %>
                                                            <figure class="essay-diagram-answer">
                                                                <img src="<%= escape(requirement.image()) %>"
                                                                    alt="Sơ đồ sinh viên đã lưu cho yêu cầu <%= requirement.order() %>" loading="lazy">
                                                                <figcaption><button type="button" data-diagram-zoom>Phóng to sơ đồ</button>
                                                                </figcaption>
                                                            </figure>
                                                        <%
                                                            }
                                                            if ((requirement.answer() == null || requirement.answer().isBlank())
                                                                    && (requirement.image() == null || requirement.image().isBlank())) {
                                                        %>
                                                            <p class="empty-content">Chưa có câu trả lời cho yêu cầu này.</p>
                                                        <% } %>

                                                        <%
                                                            if (essayEvaluation && evaluation != null) {
                                                                int gradingTaskOrder = item.order();
                                                                EssayQuestions.Requirement gradingRequirement =
                                                                        evaluationPage.questions().stream()
                                                                                .filter(task -> task.order() == gradingTaskOrder)
                                                                                .flatMap(task -> task.requirements().stream())
                                                                                .filter(candidate -> candidate.order() == requirement.order())
                                                                                .findFirst()
                                                                                .orElse(null);
                                                                if (gradingRequirement != null) {
                                                                    displayedGradingFields.add(gradingTaskOrder + "_" + gradingRequirement.order());
                                                        %>

                                                                <%@ include file="evaluation-fields.jspf" %>
                                                        <%
                                                                }
                                                            }
                                                        %>

                                                    </section>
                                            <%
                                                    }
                                                } else {
                                            %>
                                                <h4> <%= item.order() > 0 ? "Câu trả lời của sinh viên cho câu " + item.order() : "Câu trả lời của sinh viên" %> </h4>

                                                <% if (item.answer() != null && !item.answer().isBlank()) { %>
                                                    <div class="content-text essay-answer-text">
                                                    <%= escape(item.answer()) %>
                                                </div>
                                                <% } else if (hasLegacy) { %>
                                                    <p class="empty-content"> Xem câu trả lời chung bên dưới; bài nộp này chưa lưu câu trả
                                                        lời riêng. </p>
                                                <% } else { %>
                                                    <p class="empty-content"> Chưa có câu trả lời cho câu hỏi này. </p>
                                                <% } %>
                                            <% } %>

                                        </div>
                                    </article>
                                <% } %>

                                <% if (hasLegacy) { %>
                                    <section class="legacy-essay-answer">
                                        <h3> <%= essay.items().isEmpty() ? "Câu trả lời của sinh viên" : "Câu trả lời chung ban đầu" %> </h3>
                                        <div class="content-text essay-answer-text">
                                            <%= escape(essay.legacyContent()) %>
                                        </div>
                                    </section>
                                <% } %>

                                <% for (var unmatched : essay.unmatched()) { %>
                                    <section class="legacy-essay-answer">
                                        <h3> Câu trả lời chưa ghép được với câu hỏi </h3>
                                        <p class="submission-note"> <%= escape(unmatched.reference()) %> </p>
                                        <div class="content-text essay-answer-text">
                                            <%= escape(unmatched.text()) %>
                                        </div>
                                    </section>
                                <% } %>

                                <% if (essay.invalid()) { %>
                                    <p class="content-warning" role="alert"> Một số câu hỏi và câu trả lời chưa ghép được chính xác.
                                        Nội dung chưa ghép được hiển thị riêng. </p>
                                <% } %>

                                <% if (essay.items().isEmpty() && !hasLegacy && essay.unmatched().isEmpty()) { %>
                                    <p class="empty-content"> Chưa có câu hỏi hoặc câu trả lời được lưu. </p>
                                <% } %>
                            <% } %>

                            <%
                                if (assignment.responseType() == ResponseType.OBJECTIVE) {
                                    var answers =
                                            TeacherSubmissionView.answers(
                                                    submission.answer(), data.assignment().questions());
                            %>

                                <p class="answer-legend">
                                    <span class="legend-correct">✓ Đáp án đúng</span><span class="legend-incorrect">✗ Sinh viên
                                        chọn sai</span>
                                </p>

                                <% if (answers.items().isEmpty()) { %>
                                    <p class="empty-content"> Chưa có câu hỏi hoặc đáp án đã lưu. </p>
                                <% } %>

                                <% for (var answer : answers.items()) { %>
                                    <article class="submission-answer">
                                        <h3 class="content-text">
                                            <%= escape(answer.question()) %>
                                        </h3>
                                        <ul class="answer-choice-list" aria-label="Các lựa chọn trả lời">
                                            <%
                                                for (var choice : answer.choices()) {
                                                    boolean correct = Boolean.TRUE.equals(choice.correct());
                                                    boolean wrongSelected = choice.selected() && Boolean.FALSE.equals(choice.correct());
                                                    String choiceClass =
                                                            correct
                                                                    ? "choice-correct"
                                                                    : wrongSelected
                                                                            ? "choice-incorrect"
                                                                            : choice.selected() ? "choice-selected" : "";
                                            %>

                                                <li class="answer-choice <%= choiceClass %>">
                                                    <span class="choice-marker" aria-hidden="true"><%= correct ? "✓" : wrongSelected ? "✗" : choice.selected() ? "●" : "○" %></span>
                                                    <div class="choice-body">
                                                        <div class="choice-text">
                                                            <strong class="choice-label"><%= escape(choice.label()) %>.</strong><span class="content-text"><%= escape(choice.text()) %></span>
                                                        </div>

                                                        <% if (choice.selected() || correct) { %>
                                                            <p class="choice-caption"> <%= choice.selected() ? "Câu trả lời của sinh viên" : "" %><%= choice.selected() && correct ? " · Đúng" : wrongSelected ? " · Sai" : correct ? "Đáp án đúng" : " · Chưa xác định đúng sai" %> </p>
                                                        <% } %>

                                                    </div>
                                                </li>
                                            <% } %>

                                        </ul>
                                        <dl class="answer-metadata answer-summary">
                                            <dt> Câu trả lời của sinh viên </dt>
                                            <dd class="content-text">
                                                <%= escape(answer.selectedOption()) %>
                                            </dd>
                                            <dt> Điểm đạt được </dt>
                                            <dd> <%= escape(answer.awardedPoints()) %> </dd>
                                        </dl>
                                    </article>
                                <% } %>

                                <% if (answers.invalid()) { %>
                                    <p class="content-warning" role="alert"> Một số đáp án đã lưu chưa ghép được hoặc chưa hiển thị
                                        được. Nội dung bài nộp gốc vẫn được giữ nguyên. </p>
                                <% } %>
                            <% } %>

                            <%
                                if (assignment.responseType() != ResponseType.ESSAY
                                        && submission.content() != null
                                        && !submission.content().isBlank()) {
                            %>
                                <h3> Nội dung bổ sung </h3>
                                <div class="content-text essay-answer-text">
                                    <%= escape(submission.content()) %>
                                </div>
                            <% } %>

                            <% if (submission.submissionLink() != null && !submission.submissionLink().isBlank()) { %>
                                <h3> Liên kết bài nộp </h3>

                                <% if (!safeLink.isEmpty()) { %>
                                    <a class="submission-link" href="<%= escape(safeLink) %>" target="_blank"
                                        rel="noopener noreferrer"><%= escape(submission.submissionLink()) %></a>
                                <% } else { %>
                                    <p class="content-text">
                                        <%= escape(submission.submissionLink()) %>
                                    </p>
                                    <p class="content-warning"> Không thể mở liên kết này vì địa chỉ HTTP hoặc HTTPS không hợp lệ.
                                    </p>
                                <% } %>
                            <% } %>

                        </section>

                        <%
                            if (essayEvaluation
                                    && evaluation != null
                                    && evaluationPage.questions().stream()
                                            .flatMap(
                                                    task ->
                                                            task.requirements().stream()
                                                                    .map(
                                                                            requirement ->
                                                                                    task.order()
                                                                                            + "_"
                                                                                            + requirement.order()))
                                            .anyMatch(key -> !displayedGradingFields.contains(key))) {
                        %>
                            <section class="detail-card" aria-labelledby="legacy-rubric-heading">
                                <h2 id="legacy-rubric-heading">Bảng chấm theo yêu cầu</h2>
                                <p class="submission-note">Đối chiếu nội dung bài nộp ở trên để chấm từng yêu cầu của đề bài.
                                </p>

                                <%
                                    for (var gradingTask : evaluationPage.questions()) {
                                        int gradingTaskOrder = gradingTask.order();
                                        for (var gradingRequirement : gradingTask.requirements()) {
                                            if (!displayedGradingFields.contains(
                                                    gradingTaskOrder + "_" + gradingRequirement.order())) {
                                %>
                                            <div class="legacy-rubric-requirement">
                                                <h3>Câu <%= gradingTaskOrder %>, yêu cầu <%= gradingRequirement.order() %></h3>
                                                <p class="content-text"><%= escape(gradingTask.title()) %></p>
                                                <p class="content-text"><%= escape(gradingRequirement.title()) %></p>
                                                <%@ include file="evaluation-fields.jspf" %>

                                            </div>
                                <%
                                            }
                                        }
                                    }
                                %>

                            </section>
                        <% } %>

                        <section class="detail-card" aria-labelledby="evaluation-heading">
                            <h2 id="evaluation-heading"> Đánh giá bài nộp </h2>

                            <%
                                if (essayEvaluation && evaluation != null) {
                                    int requirementCount =
                                            evaluationPage.questions().stream()
                                                    .mapToInt(task -> task.requirements().size())
                                                    .sum();
                                    int scoredCount = 0;
                                    BigDecimal displayedTotal = BigDecimal.ZERO;
                                    boolean invalidEnteredScore = false;
                                    for (var task : evaluationPage.questions())
                                        for (var requirement : task.requirements()) {
                                            var savedMark = evaluation.mark(task.order(), requirement.order());
                                            String savedScore =
                                                    savedMark == null || savedMark.score() == null
                                                            ? ""
                                                            : savedMark.score().toPlainString();
                                            String enteredScore =
                                                    evaluationEditable
                                                            ? evaluationValue(
                                                                    request,
                                                                    "score_" + task.order() + "_" + requirement.order(),
                                                                    savedScore)
                                                            : savedScore;
                                            if (!enteredScore.isEmpty()) {
                                                if (enteredScore.matches("[0-9]{1,3}(\\.[0-9]{1,2})?")
                                                        && new BigDecimal(enteredScore).compareTo(requirement.points())
                                                                <= 0) {
                                                    displayedTotal = displayedTotal.add(new BigDecimal(enteredScore));
                                                    scoredCount++;
                                                } else invalidEnteredScore = true;
                                            }
                                        }
                                    BigDecimal allocatedPoints =
                                            evaluationPage.questions().stream()
                                                    .flatMap(task -> task.requirements().stream())
                                                    .map(EssayQuestions.Requirement::points)
                                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                            %>

                                <div class="evaluation-total">
                                    <div>
                                        <span>Tổng điểm theo yêu cầu</span>
                                        <p><output data-evaluation-total><%= !evaluationEditable && evaluation.marks().isEmpty() ? "Chưa có điểm thành phần" : invalidEnteredScore ? "Kiểm tra điểm" : escape(TeacherSubmissionView.score(displayedTotal)) %></output> / <%= escape(TeacherSubmissionView.score(assignment.maximumScore())) %></p>
                                    </div>
                                    <p class="evaluation-progress" data-evaluation-progress
                                        data-requirement-count="<%= requirementCount %>"> Đã chấm <%= scoredCount %>/<%= requirementCount %> yêu
                                        cầu </p>
                                </div>
                                <dl class="evaluation-question-totals">

                                    <%
                                        for (var task : evaluationPage.questions()) {
                                            BigDecimal taskTotal = BigDecimal.ZERO;
                                            int taskMarked = 0;
                                            boolean taskInvalid = false;
                                            for (var child : task.requirements()) {
                                                var mark = evaluation.mark(task.order(), child.order());
                                                String entered =
                                                        evaluationEditable
                                                                ? evaluationValue(
                                                                        request,
                                                                        "score_" + task.order() + "_" + child.order(),
                                                                        mark == null || mark.score() == null
                                                                                ? ""
                                                                                : mark.score().toPlainString())
                                                                : mark == null || mark.score() == null
                                                                        ? ""
                                                                        : mark.score().toPlainString();
                                                if (!entered.isEmpty()) {
                                                    if (entered.matches("[0-9]{1,3}(\\.[0-9]{1,2})?")
                                                            && new BigDecimal(entered).compareTo(child.points()) <= 0) {
                                                        taskTotal = taskTotal.add(new BigDecimal(entered));
                                                        taskMarked++;
                                                    } else taskInvalid = true;
                                                }
                                            }
                                    %>

                                        <dt>Tổng câu <%= task.order() %></dt>
                                        <dd><output data-question-total="<%= task.order() %>"><%= taskInvalid ? "Kiểm tra điểm" : taskMarked == 0 ? "Chưa chấm" : escape(TeacherSubmissionView.score(taskTotal)) %></output> /
                                            <%= escape(TeacherSubmissionView.score(task.points())) %></dd>
                                    <% } %>

                                </dl>
                                <p class="submission-note">Tổng tự động cộng từ điểm từng yêu cầu. Điểm được phân bổ:
                                    <%= escape(TeacherSubmissionView.score(allocatedPoints)) %>. Phần điểm chưa phân bổ không được cộng hoặc quy đổi.</p>

                                <% if (evaluationEditable) { %>
                                    <div class="evaluation-overall-feedback">
                                        <label for="evaluation-feedback">Nhận xét chung <span>(không bắt buộc)</span></label>
                                        <textarea id="evaluation-feedback" name="feedback" form="evaluation-form" rows="5" maxlength="5000"
                                          aria-describedby="evaluation-feedback-hint"><%= escape(evaluationValue(request, "feedback", evaluation.feedback())) %></textarea>
                                        <small id="evaluation-feedback-hint">Tối đa 5000 ký tự. Nhận xét chỉ hiển thị cho sinh viên
                                            sau khi công bố kết quả.</small>
                                    </div>
                                    <div class="evaluation-actions">
                                        <button class="button" type="submit" form="evaluation-form" name="action"
                                            value="saveEvaluation">Lưu đánh giá nháp</button>
                                        <button class="button primary" type="submit" form="evaluation-form" name="action"
                                            value="completeEvaluation">Hoàn tất chấm</button>
                                    </div>
                                    <p class="submission-note">Có thể lưu khi chưa chấm đủ. Hoàn tất chấm yêu cầu có điểm cho tất cả
                                        yêu cầu và chưa công bố kết quả.</p>
                                <% } else { %>
                                    <h3>Nhận xét chung</h3>
                                    <p class="content-text"><%= evaluation.feedback() == null || evaluation.feedback().isBlank() ? "Chưa có nhận xét." : escape(evaluation.feedback()) %></p>
                                <% } %>

                                <% if (evaluationComplete) { %>
                                    <div class="evaluation-actions">
                                        <button class="button" type="submit" form="evaluation-form" name="action"
                                            value="reopenEvaluation">Mở lại để chỉnh sửa</button>
                                    </div>
                                    <div class="evaluation-release">
                                        <h3>Công bố kết quả cho sinh viên</h3>
                                        <p>Sinh viên sẽ xem được điểm và toàn bộ nhận xét. Sau khi công bố, đánh giá được khóa.</p>
                                        <label class="evaluation-release-confirm" for="confirm-release">
                                            <input id="confirm-release" type="checkbox" form="evaluation-form" name="confirmRelease"
                                                value="yes">
                                            <span>Tôi đã kiểm tra và muốn công bố kết quả này.</span>
                                        </label>
                                        <button class="button primary" type="submit" form="evaluation-form" name="action"
                                            value="releaseEvaluation" data-release-evaluation>Công bố điểm và nhận xét</button>
                                    </div>
                                <% } %>

                                <% if (submission.status() == SubmissionStatus.RELEASED) { %>
                                    <p class="evaluation-notice evaluation-success">Đã công bố cho sinh viên. Điểm và nhận xét đã
                                        được khóa.</p>
                                <% } %>
                            <% } %>

                            <dl class="metadata">
                                <dt> Điểm đã lưu </dt>
                                <dd>

                                    <% if (submission.score() == null) { %> Chưa chấm <% } else { %> <%= escape(TeacherSubmissionView.score(submission.score())) %> /
                                        <%= escape(TeacherSubmissionView.score(assignment.maximumScore())) %> <% } %>

                                </dd>

                                <% if (!essayEvaluation || evaluation == null) { %>
                                    <dt> Nhận xét </dt>
                                    <dd class="content-text">
                                    <%= submission.feedback() == null || submission.feedback().isBlank() ? "Chưa có nhận xét." : escape(submission.feedback()) %>
                                </dd>
                                <% } %>

                                <dt> Người chấm </dt>
                                <dd> <%= row.graderName() == null || row.graderName().isBlank() ? "Chưa ghi nhận" : escape(row.graderName()) %> </dd>
                                <dt> Thời điểm chấm </dt>
                                <dd> <%= deadline(submission.gradedAt()) %> </dd>
                                <dt> Thời điểm công bố </dt>
                                <dd> <%= deadline(submission.releasedAt()) %> </dd>
                            </dl>
                            <p class="submission-note"> <%= submission.releasedAt() == null ? "Kết quả này chưa được công bố cho sinh viên." : "Kết quả này đã được công bố cho sinh viên." %> </p>
                        </section>
                    </div>
                    <aside class="detail-summary" aria-label="Thông tin bài nộp">

                        <section class="detail-card">
                            <h2> Thông tin lượt làm </h2>
                            <p class="section-description"> Tất cả thời gian theo giờ Việt Nam (UTC+07). </p>
                            <dl class="metadata">
                                <dt> Sinh viên </dt>
                                <dd> <%= escape(row.studentName()) %> </dd>
                                <dt> Email </dt>
                                <dd> <%= escape(row.studentEmail()) %> </dd>
                                <dt> Lượt làm </dt>
                                <dd> <%= submission.attemptNumber() %> </dd>
                                <dt> Trạng thái </dt>
                                <dd> <%= escape(TeacherSubmissionView.statusLabel(submission.status())) %> </dd>
                                <dt> Bắt đầu lúc </dt>
                                <dd> <%= deadline(submission.startedAt()) %> </dd>
                                <dt> Hạn nộp của lượt làm </dt>
                                <dd> <%= deadline(submission.deadlineAt()) %> </dd>
                                <dt> Nộp lúc </dt>
                                <dd> <%= escape(TeacherSubmissionView.submittedAt(submission.submittedAt())) %> </dd>
                            </dl>
                        </section>

                        <section class="detail-card">
                            <h2> Bài tập </h2>
                            <dl class="metadata">
                                <dt> Mục đích </dt>
                                <dd> <%= label(assignment.assignmentPurpose()) %> </dd>
                                <dt> Hình thức trả lời </dt>
                                <dd> <%= label(assignment.responseType()) %> </dd>
                                <dt> Lớp </dt>
                                <dd> <%= escape(data.assignment().classCode()) %> — <%= escape(data.assignment().className()) %> </dd>
                                <dt> Điểm tối đa </dt>
                                <dd> <%= escape(TeacherSubmissionView.score(assignment.maximumScore())) %> điểm </dd>
                            </dl>
                            <a class="button back-button"
                                href="<%= escape(base) %>/teacher/assignments?mode=view&amp;assignmentId=<%= assignment.assignmentId() %>">Xem
                                bài tập</a>
                        </section>
                    </aside>
                </div>
            <% } %>

        </main>
        </div>
    </body>
</html>
