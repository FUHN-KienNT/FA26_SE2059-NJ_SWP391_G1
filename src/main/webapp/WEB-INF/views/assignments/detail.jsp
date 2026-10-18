<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*" %>
<%@ page import="vn.edu.fpt.ams.util.AssignmentDetailView" %>
<%@ page import="static vn.edu.fpt.ams.util.AssignmentListView.*" %>
<%
    AssignmentDetailPage data = (AssignmentDetailPage) request.getAttribute("assignmentDetail");
    AssignmentListFilter filters = (AssignmentListFilter) request.getAttribute("assignmentFilters");
    if (filters == null) filters = AssignmentListFilter.from(null, null, null, null);
    String error = (String) request.getAttribute("assignmentDetailError");
    String base = request.getContextPath();
    String listUrl = base + "/teacher/assignments";
    String backUrl = data == null ? listUrl : AssignmentDetailView.listUrl(base, data.assignment().classId(), filters);
%>
<!doctype html>
<html lang="vi"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title><%= data == null ? "Chi tiết bài tập" : escape(data.assignment().title()) %> | AMS</title>
    <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-list.css">
    <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-detail.css">
    <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
    <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-lifecycle.css">
</head><body class="ams-ui teacher-app">
<%@ include file="/WEB-INF/views/shared/shell.jspf" %>
<main class="ams-content" id="page-content">
            <nav class="breadcrumb" aria-label="Đường dẫn"><a href="<%= escape(base) %>/teacher/dashboard">Bảng điều khiển</a><span aria-hidden="true">/</span><a href="<%= escape(backUrl) %>">Bài tập</a><span aria-hidden="true">/</span><span aria-current="page">Chi tiết</span></nav>
            <% if (error != null) { %>
            <section class="notice error" role="alert"><h1>Không thể tải bài tập</h1><p><%= escape(error) %></p><a href="">Thử lại</a> · <a href="<%= escape(backUrl) %>">Quay về danh sách</a></section>
            <% } else if (data == null) { %>
            <section class="empty-state"><h1>Bài tập không khả dụng</h1><p>Không thể tải bài tập này.</p><a class="ams-button back-button" href="<%= escape(backUrl) %>">Quay về danh sách</a></section>
            <% } else {
                Assignment assignment = data.assignment();
                var checklist = AssignmentDetailView.checklist(assignment.taskChecklistJson());
                String actionUrl = backUrl + "&assignmentId=" + assignment.assignmentId();
            %>
            <% if (Long.valueOf(assignment.assignmentId()).equals(session.getAttribute("createdAssignmentId"))) {
                session.removeAttribute("createdAssignmentId"); %>
            <p class="state-message" role="status">Đã tạo bản nháp bài tập.</p>
            <% } %>
            <% if(Long.valueOf(assignment.assignmentId()).equals(session.getAttribute("updatedAssignmentId"))) {
                session.removeAttribute("updatedAssignmentId"); %>
            <p class="state-message" role="status">Đã lưu thay đổi. Bài tập vẫn ở trạng thái bản nháp.</p>
            <% } %>
            <% if (Long.valueOf(assignment.assignmentId()).equals(session.getAttribute("publishedAssignmentId"))) {
                session.removeAttribute("publishedAssignmentId"); %>
            <p class="state-message" role="status"><%= escape("Đã công bố bài tập. Không thể chỉnh sửa nội dung và cài đặt. Sinh viên đủ điều kiện có thể làm bài trong thời gian cho phép.") %></p>
            <% } %>
            <% if (Long.valueOf(assignment.assignmentId()).equals(session.getAttribute("cancelledAssignmentId"))) {
                session.removeAttribute("cancelledAssignmentId"); %>
            <p class="state-message" role="status"><%= escape("Đã hủy bài tập. Sinh viên không thể bắt đầu hoặc tiếp tục làm bài. Lượt đang làm bị vô hiệu hóa; bài đã nộp và kết quả được giữ nguyên.") %></p>
            <% } %>
            <div class="page-heading">
                <div><p class="eyebrow">Chi tiết bài tập</p><h1><%= escape(assignment.title()) %></h1><p><%= escape(data.classCode()) %> — <%= escape(data.className()) %> · <%= escape(data.subjectCode()) %> · <%= escape(data.termCode()) %></p></div>
                <div class="detail-actions"><span class="badge <%= assignment.status().name().toLowerCase(java.util.Locale.ROOT) %>"><%= label(assignment.status()) %></span><% if(assignment.status()==AssignmentStatus.DRAFT){ %><a class="ams-button" href="<%= escape(actionUrl) %>&amp;mode=edit">Sửa bài tập</a><a class="ams-button ams-button-primary" href="<%= escape(actionUrl) %>&amp;mode=publish">Công bố bài tập</a><% } if(assignment.status()==AssignmentStatus.DRAFT || assignment.status()==AssignmentStatus.PUBLISHED){ %><a class="ams-button" href="<%= escape(actionUrl) %>&amp;mode=cancel">Hủy bài tập</a><% } if(data.canViewSubmissions()){ %><a class="ams-button" href="<%= escape(base) %>/teacher/submissions?assignmentId=<%= assignment.assignmentId() %>">Xem bài nộp</a><% } %><a class="ams-button" href="<%= escape(backUrl) %>">Quay về danh sách</a></div>
            </div>
            <% if (assignment.status() == AssignmentStatus.CANCELLED) { %><p class="state-message">Bài tập đã bị hủy và không thể mở lại. Sinh viên không thể bắt đầu hoặc tiếp tục làm bài. Lượt đang làm bị vô hiệu hóa; bài đã nộp và kết quả được giữ nguyên để tham khảo.</p><% } %>
            <div class="detail-layout">
                <div class="detail-content">
                    <section class="detail-card" aria-labelledby="instructions-heading">
                        <h2 id="instructions-heading">Nội dung bài tập</h2>
                        <h3>Hướng dẫn</h3>
                        <% if (assignment.instructions() == null || assignment.instructions().isBlank()) { %><p>Chưa có hướng dẫn.</p>
                        <% } else { %><div class="content-text"><%= escape(assignment.instructions()) %></div><% } %>
                        <% if (assignment.responseType() == ResponseType.ESSAY) { %>
                        <% if (!checklist.items().isEmpty()) { %>
                        <h3>Câu hỏi tự luận</h3><ul class="task-list"><% for (String task : checklist.items()) { %><li class="content-text"><%= escape(task) %></li><% } %></ul><% } %>
                        <% if (checklist.invalid()) { %><p class="content-warning" role="alert">Một số câu hỏi không thể hiển thị vì dữ liệu đã lưu không hợp lệ.</p><% } %>
                        <% } %>
                    </section>
                    <% if (assignment.responseType() == ResponseType.OBJECTIVE) { %>
                    <section class="detail-card" aria-labelledby="questions-heading">
                        <h2 id="questions-heading">Câu hỏi (<%= data.questions().size() %>)</h2>
                        <p class="section-description">Giảng viên được xem đáp án đúng trên trang này.</p>
                        <% if (data.questions().isEmpty()) { %><p class="empty-content">Chưa có câu hỏi.</p><% } %>
                        <% for (Question question : data.questions()) { var options = AssignmentDetailView.options(question.options()); int displayOption=0; %>
                        <article class="question-block">
                            <div class="question-heading"><h3>Câu hỏi <%= question.questionOrder() %></h3><span><%= escape(question.maximumPoints().stripTrailingZeros().toPlainString()) %> điểm</span></div>
                            <div class="content-text"><%= escape(question.questionText()) %></div>
                            <ul class="option-list"><% for (var option : options.items()) { %>
                                <li class="option <%= option.correct() ? "correct-option" : "" %>"><span class="option-label"><%= ++displayOption %>.</span><span class="content-text"><%= escape(option.text()) %></span><% if (option.correct()) { %><span class="correct-label">Đáp án đúng</span><% } %></li>
                            <% } %></ul>
                            <% if (options.invalid()) { %><p class="content-warning" role="alert">Một số lựa chọn không thể hiển thị vì dữ liệu đã lưu không hợp lệ.</p>
                            <% } else if (options.items().isEmpty()) { %><p class="empty-content">Chưa có lựa chọn trả lời.</p><% } %>
                        </article><% } %>
                    </section><% } %>
                </div>
                <aside class="detail-summary" aria-label="Thông tin bài tập">
                    <section class="detail-card"><h2>Tổng quan</h2><dl class="metadata">
                        <dt>Lớp</dt><dd><%= escape(data.classCode()) %> — <%= escape(data.className()) %></dd>
                        <dt>Môn học</dt><dd><%= escape(data.subjectCode()) %> — <%= escape(data.subjectName()) %></dd>
                        <dt>Học kỳ</dt><dd><%= escape(data.termCode()) %></dd>
                        <dt>Mục đích</dt><dd><%= label(assignment.assignmentPurpose()) %></dd>
                        <dt>Hình thức trả lời</dt><dd><%= label(assignment.responseType()) %></dd>
                        <dt>Điểm tối đa</dt><dd><%= escape(assignment.maximumScore().stripTrailingZeros().toPlainString()) %> điểm</dd>
                        <dt>Mẫu bài tập</dt><dd><%= escape(data.templateTitle()) %></dd>
                    </dl></section>
                    <section class="detail-card"><h2>Thời gian và giới hạn</h2><p class="section-description">Tất cả thời gian theo giờ Việt Nam (UTC+07).</p><dl class="metadata">
                        <dt>Thời gian mở bài</dt><dd><%= deadline(assignment.availableFrom()) %></dd>
                        <dt>Hạn nộp</dt><dd><%= deadline(assignment.dueAt()) %></dd>
                        <dt>Thời gian làm bài</dt><dd><%= assignment.timeLimitMinutes() == null ? "Chưa đặt" : assignment.timeLimitMinutes() + " phút/lượt" %></dd>
                        <% if (assignment.assignmentPurpose() == AssignmentPurpose.PRACTICE) { %><dt>Số lượt làm tối đa</dt><dd><%= assignment.attemptLimit() == null ? "Chưa đặt" : assignment.attemptLimit() + " lượt" %></dd><% } %>
                        <% if (assignment.publishedAt() != null) { %><dt>Thời điểm công bố</dt><dd><%= deadline(assignment.publishedAt()) %></dd><% } %>
                        <% if (assignment.cancelledAt() != null) { %><dt>Thời điểm hủy</dt><dd><%= deadline(assignment.cancelledAt()) %></dd><% } %>
                    </dl></section>
                </aside>
            </div>
            <% } %>
</main></div>
</body></html>
