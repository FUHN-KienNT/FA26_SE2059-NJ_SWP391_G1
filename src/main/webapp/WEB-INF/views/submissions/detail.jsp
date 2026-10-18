<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*" %>
<%@ page import="vn.edu.fpt.ams.util.TeacherSubmissionView" %>
<%@ page import="vn.edu.fpt.ams.util.EssaySubmissionView" %>
<%@ page import="static vn.edu.fpt.ams.util.AssignmentListView.*" %>
<%
    TeacherSubmissionDetailPage data = (TeacherSubmissionDetailPage) request.getAttribute("submissionDetail");
    TeacherSubmissionFilter filters = (TeacherSubmissionFilter) request.getAttribute("submissionFilters");
    if (filters == null) filters = TeacherSubmissionFilter.from(null, null);
    String error = (String) request.getAttribute("submissionError");
    String base = request.getContextPath();
    Long assignmentId = data == null ? (Long) request.getAttribute("selectedAssignmentId")
    : data.assignment().assignment().assignmentId();
    String backUrl = assignmentId == null ? base + "/teacher/assignments"
    : vn.edu.fpt.ams.util.TeacherSubmissionNavigation.listUrl(base, assignmentId, filters);
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
    </head>
    <body class="ams-ui teacher-app">
        <%@ include file="/WEB-INF/views/shared/shell.jspf" %>
        <main class="ams-content submission-page" id="page-content">
            <nav class="breadcrumb" aria-label="Đường dẫn">
                <a href="<%= escape(base) %>/teacher/dashboard">Bảng điều khiển</a><span aria-hidden="true">/</span><a href="<%= escape(backUrl) %>">Bài nộp</a>
                <span aria-hidden="true">/</span><span aria-current="page">Chi tiết</span>
            </nav>
            <%
                if (error != null) {
                %>
                <section class="notice error" role="alert">
                    <h1>
                        Không thể tải bài nộp
                    </h1>
                    <p>
                        <%= escape(error) %>
                    </p>
                    <a href="">Thử lại</a> · <a href="<%= escape(backUrl) %>">Về danh sách bài nộp</a>
                </section>
                <%
                }
                else if (data == null) {
                %>
                <section class="empty-state">
                    <h1>
                        Bài nộp không khả dụng
                    </h1>
                    <p>
                        Không thể tải bài nộp này.
                    </p>
                    <a class="button back-button" href="<%= escape(backUrl) %>">Về danh sách bài nộp</a>
                </section>
                <%
                }
                else {
                    Assignment assignment = data.assignment().assignment();
                    TeacherSubmissionRow row = data.row();
                    Submission submission = row.submission();
                    String safeLink = TeacherSubmissionView.safeLink(submission.submissionLink());
                %>
                <div class="page-heading">
                    <div>
                        <p class="eyebrow">
                            Chi tiết bài nộp
                        </p>
                        <h1>
                            <%= escape(row.studentName()) %> · Lượt làm <%= submission.attemptNumber() %>
                        </h1>
                        <p>
                            <%= escape(row.studentEmail()) %>
                        </p>
                    </div>
                    <div class="submission-detail-actions">
                        <span class="badge submission-status <%= submission.status().name().toLowerCase(java.util.Locale.ROOT) %>"><%= escape(TeacherSubmissionView.statusLabel(submission.status())) %></span>
                        <a class="button" href="<%= escape(backUrl) %>">Về danh sách bài nộp</a>
                    </div>
                </div>
                <section class="submission-card assignment-context" aria-labelledby="assignment-context-heading">
                    <div class="context-heading">
                        <h2 id="assignment-context-heading">
                            <%= escape(assignment.title()) %>
                        </h2>
                        <span class="badge <%= assignment.status().name().toLowerCase(java.util.Locale.ROOT) %>"><%= label(assignment.status()) %></span>
                    </div>
                    <p>
                        <%= escape(data.assignment().classCode()) %> — <%= escape(data.assignment().className()) %> · <%= label(assignment.responseType()) %>
                    </p>
                </section>
                <%
                    if (submission.status() == SubmissionStatus.IN_PROGRESS) {
                    %>
                    <p class="state-message">
                        Sinh viên đang làm lượt này. Nội dung dưới đây là bản lưu tạm, chưa phải bài đã nộp.
                    </p>
                    <%
                    }
                %>
                <%
                    if (submission.status() == SubmissionStatus.EXPIRED) {
                    %>
                    <p class="state-message">
                        Lượt làm này đã hết hạn. Nội dung được giữ để tham khảo và không được tính là bài nộp hợp lệ.
                    </p>
                    <%
                    }
                %>
                <%
                    if (submission.status() == SubmissionStatus.VOID) {
                    %>
                    <p class="state-message">
                        Lượt làm này đã bị vô hiệu hóa. Nội dung được giữ để tham khảo và không được tính là bài nộp hợp lệ.
                    </p>
                    <%
                    }
                %>
                <div class="detail-layout">
                    <div class="detail-content">
                        <%
                            if (assignment.responseType() == ResponseType.ESSAY) {
                                boolean hasInstructions = assignment.instructions() != null && !assignment.instructions().isBlank();
                            %>
                            <%
                                if (hasInstructions) {
                                %>
                                <details class="detail-card essay-context">
                                    <summary>
                                        Hướng dẫn làm bài
                                    </summary>
                                    <%
                                        if (hasInstructions) {
                                        %>
                                        <h3>
                                            Hướng dẫn
                                        </h3>
                                        <div class="content-text">
                                            <%= escape(assignment.instructions()) %>
                                        </div>
                                        <%
                                        }
                                    %>
                                </details>
                                <%
                                }
                            %>
                            <%
                            }
                        %>
                        <section class="detail-card student-response" aria-labelledby="submission-content-heading">
                            <h2 id="submission-content-heading">
                                Câu hỏi và câu trả lời của sinh viên
                            </h2>
                            <%
                                if (assignment.responseType() == ResponseType.ESSAY) {
                                    var essay = EssaySubmissionView.read(assignment.taskChecklistJson(), submission.content());
                                    boolean hasLegacy = essay.legacyContent() != null && !essay.legacyContent().isBlank();
                                %>
                                <%
                                    if (hasLegacy && !essay.items().isEmpty()) {
                                    %>
                                    <p class="content-warning">
                                        Bài nộp cũ được lưu thành một câu trả lời chung, chưa tách theo từng câu hỏi. Nội dung gốc được hiển thị bên dưới.
                                    </p>
                                    <%
                                    }
                                %>
                                <%
                                    for (var item : essay.items()) {
                                    %>
                                    <article class="submission-answer essay-question-answer">
                                        <div class="essay-question-block">
                                            <p class="review-eyebrow">
                                                <%= item.order() > 0 ? "Câu hỏi " + item.order() : "Câu hỏi" %>
                                            </p>
                                            <%
                                                if (item.points() != null) {
                                                %>
                                                <p class="submission-note">
                                                    <%= escape(item.points()) %> điểm
                                                </p>
                                                <%
                                                }
                                            %>
                                            <h3 class="content-text">
                                                <%= escape(item.title()) %>
                                            </h3>
                                            <%
                                                if (item.description() != null && !item.description().isBlank()) {
                                                %>
                                                <div class="content-text">
                                                    <%= escape(item.description()) %>
                                                </div>
                                                <%
                                                }
                                            %>
                                        </div>
                                        <div class="essay-response-block">
                                            <h4>
                                                <%= item.order() > 0 ? "Câu trả lời của sinh viên cho câu " + item.order() : "Câu trả lời của sinh viên" %>
                                            </h4>
                                            <%
                                                if (item.answer() != null && !item.answer().isBlank()) {
                                                %>
                                                <div class="content-text essay-answer-text">
                                                    <%= escape(item.answer()) %>
                                                </div>
                                                <%
                                                }
                                                else if (hasLegacy) {
                                                %>
                                                <p class="empty-content">
                                                    Xem câu trả lời chung bên dưới; bài nộp này chưa lưu câu trả lời riêng.
                                                </p>
                                                <%
                                                }
                                                else {
                                                %>
                                                <p class="empty-content">
                                                    Chưa có câu trả lời cho câu hỏi này.
                                                </p>
                                                <%
                                                }
                                            %>
                                        </div>
                                    </article>
                                    <%
                                    }
                                %>
                                <%
                                    if (hasLegacy) {
                                    %>
                                    <section class="legacy-essay-answer">
                                        <h3>
                                            <%= essay.items().isEmpty() ? "Câu trả lời của sinh viên" : "Câu trả lời chung ban đầu" %>
                                        </h3>
                                        <div class="content-text essay-answer-text">
                                            <%= escape(essay.legacyContent()) %>
                                        </div>
                                    </section>
                                    <%
                                    }
                                %>
                                <%
                                    for (var unmatched : essay.unmatched()) {
                                    %>
                                    <section class="legacy-essay-answer">
                                        <h3>
                                            Câu trả lời chưa ghép được với câu hỏi
                                        </h3>
                                        <p class="submission-note">
                                            <%= escape(unmatched.reference()) %>
                                        </p>
                                        <div class="content-text essay-answer-text">
                                            <%= escape(unmatched.text()) %>
                                        </div>
                                    </section>
                                    <%
                                    }
                                %>
                                <%
                                    if (essay.invalid()) {
                                    %>
                                    <p class="content-warning" role="alert">
                                        Một số câu hỏi và câu trả lời chưa ghép được chính xác. Nội dung chưa ghép được hiển thị riêng.
                                    </p>
                                    <%
                                    }
                                %>
                                <%
                                    if (essay.items().isEmpty() && !hasLegacy && essay.unmatched().isEmpty()) {
                                    %>
                                    <p class="empty-content">
                                        Chưa có câu hỏi hoặc câu trả lời được lưu.
                                    </p>
                                    <%
                                    }
                                %>
                                <%
                                }
                            %>
                            <%
                                if (assignment.responseType() == ResponseType.OBJECTIVE) {
                                    var answers = TeacherSubmissionView.answers(submission.answer(), data.assignment().questions());
                                %>
                                <p class="answer-legend">
                                    <span class="legend-correct">✓ Đáp án đúng</span><span class="legend-incorrect">✗ Sinh viên chọn sai</span>
                                </p>
                                <%
                                    if (answers.items().isEmpty()) {
                                    %>
                                    <p class="empty-content">
                                        Chưa có câu hỏi hoặc đáp án đã lưu.
                                    </p>
                                    <%
                                    }
                                %>
                                <%
                                    for (var answer : answers.items()) {
                                    %>
                                    <article class="submission-answer">
                                        <h3 class="content-text">
                                            <%= escape(answer.question()) %>
                                        </h3>
                                        <ul class="answer-choice-list" aria-label="Các lựa chọn trả lời">
                                            <%
                                                for (var choice : answer.choices()) {
                                                    boolean correct = Boolean.TRUE.equals(choice.correct());
                                                    boolean wrongSelected = choice.selected() && Boolean.FALSE.equals(choice.correct());
                                                    String choiceClass = correct ? "choice-correct" : wrongSelected ? "choice-incorrect" : choice.selected() ? "choice-selected" : "";
                                                %>
                                                <li class="answer-choice <%= choiceClass %>">
                                                    <span class="choice-marker" aria-hidden="true"><%= correct ? "✓" : wrongSelected ? "✗" : choice.selected() ? "●" : "○" %></span>
                                                    <div class="choice-body">
                                                        <div class="choice-text">
                                                            <strong class="choice-label"><%= escape(choice.label()) %>.</strong><span class="content-text"><%= escape(choice.text()) %></span>
                                                        </div>
                                                        <%
                                                            if (choice.selected() || correct) {
                                                            %>
                                                            <p class="choice-caption">
                                                                <%= choice.selected() ? "Câu trả lời của sinh viên" : "" %><%= choice.selected() && correct ? " · Đúng" : wrongSelected ? " · Sai" : correct ? "Đáp án đúng" : " · Chưa xác định đúng sai" %>
                                                            </p>
                                                            <%
                                                            }
                                                        %>
                                                    </div>
                                                </li>
                                                <%
                                                }
                                            %>
                                        </ul>
                                        <dl class="answer-metadata answer-summary">
                                            <dt>
                                                Câu trả lời của sinh viên
                                            </dt>
                                            <dd class="content-text">
                                                <%= escape(answer.selectedOption()) %>
                                            </dd>
                                            <dt>
                                                Điểm đạt được
                                            </dt>
                                            <dd>
                                                <%= escape(answer.awardedPoints()) %>
                                            </dd>
                                        </dl>
                                    </article>
                                    <%
                                    }
                                %>
                                <%
                                    if (answers.invalid()) {
                                    %>
                                    <p class="content-warning" role="alert">
                                        Một số đáp án đã lưu chưa ghép được hoặc chưa hiển thị được. Nội dung bài nộp gốc vẫn được giữ nguyên.
                                    </p>
                                    <%
                                    }
                                %>
                                <%
                                }
                            %>
                            <%
                                if (assignment.responseType() != ResponseType.ESSAY && submission.content() != null && !submission.content().isBlank()) {
                                %>
                                <h3>
                                    Nội dung bổ sung
                                </h3>
                                <div class="content-text essay-answer-text">
                                    <%= escape(submission.content()) %>
                                </div>
                                <%
                                }
                            %>
                            <%
                                if (submission.submissionLink() != null && !submission.submissionLink().isBlank()) {
                                %>
                                <h3>
                                    Liên kết bài nộp
                                </h3>
                                <%
                                    if (!safeLink.isEmpty()) {
                                    %>
                                    <a class="submission-link" href="<%= escape(safeLink) %>" target="_blank" rel="noopener noreferrer"><%= escape(submission.submissionLink()) %></a>
                                    <%
                                    }
                                    else {
                                    %>
                                    <p class="content-text">
                                        <%= escape(submission.submissionLink()) %>
                                    </p>
                                    <p class="content-warning">
                                        Không thể mở liên kết này vì địa chỉ HTTP hoặc HTTPS không hợp lệ.
                                    </p>
                                    <%
                                    }
                                %>
                                <%
                                }
                            %>
                        </section>
                        <section class="detail-card" aria-labelledby="evaluation-heading">
                            <h2 id="evaluation-heading">
                                Đánh giá
                            </h2>
                            <dl class="metadata">
                                <dt>
                                    Điểm
                                </dt>
                                <dd>
                                    <%
                                        if (submission.score() == null) {
                                        %>
                                        Chưa chấm
                                        <%
                                        }
                                        else {
                                        %>
                                        <%= escape(TeacherSubmissionView.score(submission.score())) %> / <%= escape(TeacherSubmissionView.score(assignment.maximumScore())) %>
                                        <%
                                        }
                                    %>
                                </dd>
                                <dt>
                                    Nhận xét
                                </dt>
                                <dd class="content-text">
                                    <%= submission.feedback() == null || submission.feedback().isBlank() ? "Chưa có nhận xét." : escape(submission.feedback()) %>
                                </dd>
                                <dt>
                                    Người chấm
                                </dt>
                                <dd>
                                    <%= row.graderName() == null || row.graderName().isBlank() ? "Chưa ghi nhận" : escape(row.graderName()) %>
                                </dd>
                                <dt>
                                    Thời điểm chấm
                                </dt>
                                <dd>
                                    <%= deadline(submission.gradedAt()) %>
                                </dd>
                                <dt>
                                    Thời điểm công bố
                                </dt>
                                <dd>
                                    <%= deadline(submission.releasedAt()) %>
                                </dd>
                            </dl>
                            <p class="submission-note">
                                <%= submission.releasedAt() == null ? "Kết quả này chưa được công bố cho sinh viên." : "Kết quả này đã được công bố cho sinh viên." %>
                            </p>
                        </section>
                    </div>
                    <aside class="detail-summary" aria-label="Thông tin bài nộp">
                        <section class="detail-card">
                            <h2>
                                Thông tin lượt làm
                            </h2>
                            <p class="section-description">
                                Tất cả thời gian theo giờ Việt Nam (UTC+07).
                            </p>
                            <dl class="metadata">
                                <dt>
                                    Sinh viên
                                </dt>
                                <dd>
                                    <%= escape(row.studentName()) %>
                                </dd>
                                <dt>
                                    Email
                                </dt>
                                <dd>
                                    <%= escape(row.studentEmail()) %>
                                </dd>
                                <dt>
                                    Lượt làm
                                </dt>
                                <dd>
                                    <%= submission.attemptNumber() %>
                                </dd>
                                <dt>
                                    Trạng thái
                                </dt>
                                <dd>
                                    <%= escape(TeacherSubmissionView.statusLabel(submission.status())) %>
                                </dd>
                                <dt>
                                    Bắt đầu lúc
                                </dt>
                                <dd>
                                    <%= deadline(submission.startedAt()) %>
                                </dd>
                                <dt>
                                    Hạn nộp của lượt làm
                                </dt>
                                <dd>
                                    <%= deadline(submission.deadlineAt()) %>
                                </dd>
                                <dt>
                                    Nộp lúc
                                </dt>
                                <dd>
                                    <%= escape(TeacherSubmissionView.submittedAt(submission.submittedAt())) %>
                                </dd>
                            </dl>
                        </section>
                        <section class="detail-card">
                            <h2>
                                Bài tập
                            </h2>
                            <dl class="metadata">
                                <dt>
                                    Mục đích
                                </dt>
                                <dd>
                                    <%= label(assignment.assignmentPurpose()) %>
                                </dd>
                                <dt>
                                    Hình thức trả lời
                                </dt>
                                <dd>
                                    <%= label(assignment.responseType()) %>
                                </dd>
                                <dt>
                                    Lớp
                                </dt>
                                <dd>
                                    <%= escape(data.assignment().classCode()) %> — <%= escape(data.assignment().className()) %>
                                </dd>
                                <dt>
                                    Điểm tối đa
                                </dt>
                                <dd>
                                    <%= escape(TeacherSubmissionView.score(assignment.maximumScore())) %> điểm
                                </dd>
                            </dl>
                            <a class="button back-button" href="<%= escape(base) %>/teacher/assignments?mode=view&amp;assignmentId=<%= assignment.assignmentId() %>">Xem bài tập</a>
                        </section>
                    </aside>
                </div>
                <%
                }
            %>
        </main>
    </div>
</body>
</html>
