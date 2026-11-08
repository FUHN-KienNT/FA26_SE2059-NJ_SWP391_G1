<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*" %>
<%@ page import="vn.edu.fpt.ams.util.AssignmentDetailView" %>
<%@ page import="static vn.edu.fpt.ams.util.AssignmentListView.*" %>

<%
    AssignmentDetailPage data = (AssignmentDetailPage) request.getAttribute("assignmentDetail");
    AssignmentListFilter filters =
            (AssignmentListFilter) request.getAttribute("assignmentFilters");
    if (filters == null) filters = AssignmentListFilter.from(null, null, null, null);
    String error = (String) request.getAttribute("assignmentDetailError");
    String base = request.getContextPath();
    String listUrl = base + "/teacher/assignments";
    String backUrl =
            data == null
                    ? listUrl
                    : AssignmentDetailView.listUrl(base, data.assignment().classId(), filters);
%>

<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title><%= data == null ? "Chi tiết bài tập" : escape(data.assignment().title()) %> | AMS</title>
        <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-list.css">
        <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-detail.css">
        <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-dialog.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>

        <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-lifecycle.css">
    </head>
    <body class="ams-ui teacher-app">
        <%@ include file="/WEB-INF/views/shared/shell.jspf" %>

        <main class="ams-content" id="page-content">
            <%@ include file="/WEB-INF/views/shared/action-notice.jspf" %>
            <nav class="breadcrumb" aria-label="Đường dẫn">
                <a href="<%= escape(base) %>/teacher/dashboard">Bảng điều khiển</a><span aria-hidden="true">/</span><a
                    href="<%= escape(backUrl) %>">Bài tập</a>
                <span aria-hidden="true">/</span><span aria-current="page">Chi tiết</span>
            </nav>

            <% if (error != null) { %>
                <section class="notice error" role="alert">
                    <h1> Không thể tải bài tập </h1>
                    <p> <%= escape(error) %> </p>
                    <a href="">Thử lại</a> · <a href="<%= escape(backUrl) %>">Quay về danh sách</a>
                </section>
            <% } else if (data == null) { %>
                <section class="empty-state">
                    <h1> Bài tập không khả dụng </h1>
                    <p> Không thể tải bài tập này. </p>
                    <a class="ams-button back-button" href="<%= escape(backUrl) %>">Quay về danh sách</a>
                </section>
            <%
                } else {
                    Assignment assignment = data.assignment();
                    var checklist = AssignmentDetailView.checklist(assignment.taskChecklistJson());
                    String actionUrl = backUrl + "&assignmentId=" + assignment.assignmentId();
            %>

                <%
                    if (Long.valueOf(assignment.assignmentId())
                            .equals(session.getAttribute("createdAssignmentId"))) {
                        session.removeAttribute("createdAssignmentId");
                %>

                    <p class="state-message" role="status"> Đã tạo bài tập. </p>
                <% } %>

                <%
                    if (Long.valueOf(assignment.assignmentId())
                            .equals(session.getAttribute("updatedAssignmentId"))) {
                        session.removeAttribute("updatedAssignmentId");
                %>

                    <p class="state-message" role="status"> Đã lưu thay đổi bài tập. </p>
                <% } %>

                <%
                    if (Long.valueOf(assignment.assignmentId())
                            .equals(session.getAttribute("publishedAssignmentId"))) {
                        session.removeAttribute("publishedAssignmentId");
                %>

                    <p class="state-message" role="status"> <%= escape("Đã công bố bài tập. Không thể chỉnh sửa nội dung và cài đặt. Sinh viên đủ điều kiện có thể làm bài trong thời gian cho phép.") %> </p>
                <% } %>

                <%
                    if (Long.valueOf(assignment.assignmentId())
                            .equals(session.getAttribute("cancelledAssignmentId"))) {
                        session.removeAttribute("cancelledAssignmentId");
                %>

                    <p class="state-message" role="status"> <%= escape("Đã hủy bài tập. Sinh viên không thể bắt đầu hoặc tiếp tục làm bài. Lượt đang làm bị vô hiệu hóa; bài đã nộp và kết quả được giữ nguyên.") %> </p>
                <% } %>

                <div class="page-heading">
                    <div>
                        <p class="eyebrow"> Chi tiết bài tập </p>
                        <h1> <%= escape(assignment.title()) %> </h1>
                        <p> <%= escape(data.classCode()) %> — <%= escape(data.className()) %> · <%= escape(data.subjectCode()) %> · <%= escape(data.termCode()) %> </p>
                    </div>
                    <div class="detail-actions">
                        <span class="badge <%= assignment.status().name().toLowerCase(java.util.Locale.ROOT) %>"><%= label(assignment.status()) %></span>

                        <% if (assignment.status() == AssignmentStatus.DRAFT) { %>
                            <a class="ams-button" href="<%= escape(actionUrl) %>&amp;mode=edit">Sửa bài tập</a>
                            <a class="ams-button ams-button-primary" data-lifecycle-dialog href="<%= escape(actionUrl) %>&amp;mode=publish">Công bố bài
                                tập</a>
                        <%
                            }
                            if (assignment.status() == AssignmentStatus.DRAFT
                                    || assignment.status() == AssignmentStatus.PUBLISHED) {
                        %>
                            <a class="ams-button" data-lifecycle-dialog href="<%= escape(actionUrl) %>&amp;mode=cancel">Hủy bài tập</a>
                        <%
                            }
                            if (data.canViewSubmissions()) {
                        %>
                            <a class="ams-button" href="<%= escape(base) %>/teacher/submissions?assignmentId=<%= assignment.assignmentId() %>">Xem
                                bài nộp</a>
                        <% } %>

                        <% if (assignment.status() == AssignmentStatus.PUBLISHED && assignment.closedAt() == null) { %>
                            <button class="ams-button" type="button" data-lifecycle-dialog data-close-template="close-assignment-template">Khóa nhận bài để chấm</button>
                            <template id="close-assignment-template">
                                <section class="detail-card">
                                    <h2>Khóa nhận bài vĩnh viễn?</h2>
                                    <p>Sinh viên sẽ dừng làm và nộp lại. Phần bài làm đã lưu của các lượt đang làm sẽ được chốt. Sau khi khóa, bạn có thể chấm điểm và không thể mở nhận bài trở lại.</p>
                                    <form method="post" action="<%= escape(base) %>/teacher/assignments/close">
                                        <input type="hidden" name="assignmentId" value="<%= assignment.assignmentId() %>">
                                        <input type="hidden" name="csrf" value="<%= escape(vn.edu.fpt.ams.util.WebSupport.csrf(request)) %>">
                                        <button class="ams-button ams-button-primary" type="submit">Xác nhận khóa vĩnh viễn</button>
                                    </form>
                                </section>
                            </template>
                        <% } %>

                        <a class="ams-button" href="<%= escape(backUrl) %>">Quay về danh sách</a>
                    </div>
                </div>

                <% if (assignment.closedAt() != null) { %>
                    <p class="state-message" role="status">Đã khóa nhận bài vĩnh viễn lúc <%= deadline(assignment.closedAt()) %>. Giảng viên có thể chấm điểm các bản nộp cuối cùng.</p>
                <% } %>

                <% if (assignment.status() == AssignmentStatus.CANCELLED) { %>
                    <p class="state-message"> Bài tập đã bị hủy và không thể mở lại. Sinh viên không thể bắt đầu hoặc tiếp tục
                        làm bài. Lượt đang làm bị vô hiệu hóa; bài đã nộp và kết quả được giữ nguyên để tham khảo. </p>
                <% } %>

                <div class="detail-layout">
                    <div class="detail-content">
                        <section class="detail-card" aria-labelledby="instructions-heading">
                            <h2 id="instructions-heading"> Nội dung bài tập </h2>
                            <% if (assignment.instructions() != null && !assignment.instructions().isBlank()) { %>
                                <h3> Hướng dẫn </h3>
                                <div class="content-text">
                                    <%= escape(assignment.instructions()) %>
                                </div>
                            <% } %>

                            <% if (assignment.responseType() == ResponseType.ESSAY) { %>
                                <% if (!checklist.items().isEmpty()) { %>
                                    <h3> Câu hỏi tự luận </h3>
                                    <ul class="task-list">
                                        <% for (String task : checklist.items()) { %>
                                            <li class="content-text">
                                                <%= escape(task) %>
                                            </li>
                                        <% } %>

                                    </ul>
                                <% } %>

                                <% if (checklist.invalid()) { %>
                                    <p class="content-warning" role="alert"> Một số câu hỏi không thể hiển thị vì dữ liệu đã lưu
                                        không hợp lệ. </p>
                                <% } %>

                                <%
                                    java.util.List<vn.edu.fpt.ams.util.EssayQuestions.Task> rubricTasks = java.util.List.of();
                                    try {
                                        rubricTasks = vn.edu.fpt.ams.util.EssayQuestions.read(assignment.taskChecklistJson());
                                    } catch (IllegalArgumentException invalidQuestions) { }
                                    for (var rubricTask : rubricTasks) {
                                        boolean hasRubric = !rubricTask.rubric().isBlank()
                                                || rubricTask.requirements().stream().anyMatch(r -> !r.rubric().isBlank());
                                        if (!hasRubric) continue;
                                %>
                                    <details class="teacher-rubric">
                                        <summary>Tiêu chí chấm câu <%= rubricTask.order() %> · Chỉ giảng viên thấy</summary>
                                        <% if (!rubricTask.rubric().isBlank()) { %>
                                            <p class="content-text"><%= escape(rubricTask.rubric()) %></p>
                                        <% } %>
                                        <% for (var criterion : rubricTask.requirements()) { if (!criterion.rubric().isBlank()) { %>
                                            <h4>Yêu cầu <%= criterion.order() %></h4>
                                            <p class="content-text"><%= escape(criterion.rubric()) %></p>
                                        <% } } %>
                                    </details>
                                <% } %>
                            <% } %>

                        </section>

                        <% if (assignment.responseType() == ResponseType.OBJECTIVE) { %>
                            <section class="detail-card" aria-labelledby="questions-heading">
                                <h2 id="questions-heading"> Câu hỏi (<%= data.questions().size() %>) </h2>
                                <p class="section-description"> Giảng viên được xem đáp án đúng trên trang này. </p>

                                <% if (data.questions().isEmpty()) { %>
                                    <p class="empty-content"> Chưa có câu hỏi. </p>
                                <% } %>

                                <%
                                    for (Question question : data.questions()) {
                                        var options = AssignmentDetailView.options(question.options());
                                        int displayOption = 0;
                                %>

                                    <article class="question-block">
                                        <div class="question-heading">
                                            <h3> Câu hỏi <%= question.questionOrder() %> </h3>
                                            <span><%= escape(question.maximumPoints().stripTrailingZeros().toPlainString()) %> điểm</span>
                                        </div>
                                        <div class="content-text">
                                            <%= escape(question.questionText()) %>
                                        </div>
                                        <ul class="option-list">
                                            <% for (var option : options.items()) { %>
                                                <li class="option <%= option.correct() ? "correct-option" : "" %>">
                                                    <span class="option-label"><%= ++displayOption %>.</span><span class="content-text"><%= escape(option.text()) %></span>

                                                    <% if (option.correct()) { %>
                                                        <span class="correct-label">Đáp án đúng</span>
                                                    <% } %>

                                                </li>
                                            <% } %>

                                        </ul>

                                        <% if (options.invalid()) { %>
                                            <p class="content-warning" role="alert"> Một số lựa chọn không thể hiển thị vì dữ liệu đã
                                                lưu không hợp lệ. </p>
                                        <% } else if (options.items().isEmpty()) { %>
                                            <p class="empty-content"> Chưa có lựa chọn trả lời. </p>
                                        <% } %>

                                    </article>
                                <% } %>

                            </section>
                        <% } %>

                    </div>
                    <aside class="detail-summary" aria-label="Thông tin bài tập">

                        <section class="detail-card">
                            <h2> Tổng quan </h2>
                            <dl class="metadata">
                                <dt> Lớp </dt>
                                <dd> <%= escape(data.classCode()) %> — <%= escape(data.className()) %> </dd>
                                <dt> Môn học </dt>
                                <dd> <%= escape(data.subjectCode()) %> — <%= escape(data.subjectName()) %> </dd>
                                <dt> Học kỳ </dt>
                                <dd> <%= escape(data.termCode()) %> </dd>
                                <dt> Mục đích </dt>
                                <dd> <%= label(assignment.assignmentPurpose()) %> </dd>
                                <dt> Hình thức trả lời </dt>
                                <dd> <%= label(assignment.responseType()) %> </dd>
                                <dt> Điểm tối đa </dt>
                                <dd> <%= escape(assignment.maximumScore().stripTrailingZeros().toPlainString()) %> điểm </dd>
                                <dt> Mẫu bài tập </dt>
                                <dd> <%= escape(data.templateTitle()) %> </dd>
                            </dl>
                        </section>

                        <section class="detail-card">
                            <h2> Thời gian và giới hạn </h2>
                            <p class="section-description"> Tất cả thời gian theo giờ Việt Nam (UTC+07). </p>
                            <dl class="metadata">
                                <dt> Thời gian mở bài </dt>
                                <dd> <%= deadline(assignment.availableFrom()) %> </dd>
                                <dt> Hạn nộp </dt>
                                <dd> <%= deadline(assignment.dueAt()) %> </dd>
                                <dt> Làm bài và nộp lại </dt>
                                <dd>Không giới hạn số lần trong thời gian nhận bài. Chỉ giữ bản nộp cuối cùng.</dd>

                                <% if (assignment.assignmentPurpose() == AssignmentPurpose.PRACTICE) { %>
                                    <dt> Trạng thái nhận bài </dt>
                                    <dd><%= assignment.closedAt() == null ? "Theo thời gian mở và hạn nộp" : "Đã khóa vĩnh viễn" %></dd>
                                <% } %>

                                <% if (assignment.publishedAt() != null) { %>
                                    <dt> Thời điểm công bố </dt>
                                    <dd> <%= deadline(assignment.publishedAt()) %> </dd>
                                <% } %>

                                <% if (assignment.cancelledAt() != null) { %>
                                    <dt> Thời điểm hủy </dt>
                                    <dd> <%= deadline(assignment.cancelledAt()) %> </dd>
                                <% } %>

                            </dl>
                        </section>
                    </aside>
                </div>
            <% } %>

        </main>
        </div>
        <script src="<%= escape(base) %>/js/assignment-lifecycle.js" defer></script>
    </body>
</html>
