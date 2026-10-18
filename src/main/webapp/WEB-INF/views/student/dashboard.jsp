<%@ page contentType="text/html;charset=UTF-8" import="vn.edu.fpt.ams.dto.StudentDashboardDTO,vn.edu.fpt.ams.util.WebSupport,vn.edu.fpt.ams.util.AssignmentListView" %>
<% var dashboard = (StudentDashboardDTO) request.getAttribute("dashboard"); %>
<!doctype html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Bảng điều khiển sinh viên | AMS</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
    <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/student-dashboard.css?v=20261019-1">
</head>
<body class="ams-ui student-dashboard">
<%@ include file="/WEB-INF/views/shared/shell.jspf" %>
<main class="ams-content student-main" id="page-content">
<div class="student-page">
    <header class="student-heading">
        <div><p class="student-eyebrow">TỔNG QUAN HỌC TẬP</p><h1>Bảng điều khiển sinh viên</h1>
            <p>Xin chào <strong><%=WebSupport.html(dashboard.fullName())%></strong>. Theo dõi lớp, bài tập và kết quả học tập của bạn.</p>
        </div>
        <a class="primary-button" href="${pageContext.request.contextPath}/student/subjects">Xem môn học mở đăng ký <span aria-hidden="true">→</span></a>
    </header>

    <section class="student-summary" aria-label="Tổng quan của bạn">
        <article class="student-summary-card"><span class="student-metric-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M12 5C8 3 5 3 3 4v15c3-1 6-1 9 1 3-2 6-2 9-1V4c-2-1-5-1-9 1Zm0 0v15"/></svg></span><h2>Môn đang đăng ký</h2><strong><%=dashboard.subjectCount()%></strong><p>Đăng ký còn hoạt động</p></article>
        <article class="student-summary-card"><span class="student-metric-icon green" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="9" cy="7" r="3"/><path d="M3 21v-4a6 6 0 0 1 12 0v4M16 4a3 3 0 0 1 0 6M18 13a5 5 0 0 1 3 5v3"/></svg></span><h2>Lớp đang tham gia</h2><strong><%=dashboard.classCount()%></strong><p>Lớp và ghi danh hoạt động</p></article>
        <article class="student-summary-card"><span class="student-metric-icon violet" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M5 3h10l4 4v14H5ZM14 3v5h5M8 12h8M8 16h6"/></svg></span><h2>Bài tập đã công bố</h2><strong><%=dashboard.assignmentCount()%></strong><p>Trong các lớp đang tham gia</p></article>
        <article class="student-summary-card"><span class="student-metric-icon amber" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M9 4H5v17h14V4h-4M9 2h6v4H9ZM8 13l3 3 5-6"/></svg></span><h2>Lượt đã nộp</h2><strong><%=dashboard.submittedAttemptCount()%></strong><p>Bao gồm lịch sử học tập</p></article>
        <article class="student-summary-card"><span class="student-metric-icon teal" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M4 20V10h4v10M10 20V4h4v16M16 20v-7h4v7M2 21h20"/></svg></span><h2>Kết quả đã công bố</h2><strong><%=dashboard.releasedResultCount()%></strong><p>Điểm được phép xem</p></article>
    </section>

    <section class="student-panel" id="student-classes" aria-labelledby="student-classes-title">
        <header class="student-panel-heading"><div><h2 id="student-classes-title">Lớp đang tham gia</h2><p>Các lớp bạn đang có ghi danh hoạt động.</p></div><span class="student-count"><%=dashboard.classCount()%> lớp</span></header>
        <% if (dashboard.classes().isEmpty()) { %>
            <div class="student-empty"><p>Bạn chưa tham gia lớp đang hoạt động nào. Các số liệu được tính từ đăng ký và lớp của riêng bạn.</p><a href="${pageContext.request.contextPath}/student/subjects">Xem và đăng ký môn học →</a><p>Hoặc liên hệ giảng viên để được thêm vào lớp hoặc nhận lời mời.</p></div>
        <% } else { %>
        <div class="student-table-wrap"><table class="student-class-table"><thead><tr><th>Môn học</th><th>Lớp</th><th>Học kỳ</th><th>Giảng viên</th></tr></thead><tbody>
        <% for (var item : dashboard.classes()) { %>
            <tr><td><strong><%=WebSupport.html(item.subjectCode())%></strong><small><%=WebSupport.html(item.subjectName())%></small></td><td><%=WebSupport.html(item.classCode())%></td><td><%=WebSupport.html(item.termCode())%><small><%=WebSupport.html(item.termName())%></small></td><td><%=item.teacherName() == null ? "Chưa phân công" : WebSupport.html(item.teacherName())%></td></tr>
        <% } %>
        </tbody></table></div>
        <p class="student-panel-footnote">Hiển thị <%=dashboard.classes().size()%> trong <%=dashboard.classCount()%> lớp.</p>
        <% } %>
    </section>

    <div class="student-detail-grid">
        <section class="student-panel" aria-labelledby="student-open-title">
            <header class="student-panel-heading"><div><h2 id="student-open-title">Bài tập đang mở</h2><p>Ưu tiên bài có hạn nộp gần nhất. Thời gian hiển thị theo UTC+07.</p></div></header>
            <% if (dashboard.openAssignments().isEmpty()) { %>
                <p class="student-empty">Chưa có bài tập đang mở trong các lớp của bạn.</p>
            <% } else { %>
            <ul class="student-assignment-list">
            <% for (var item : dashboard.openAssignments()) { %>
                <li><div class="student-item-title"><h3><%=WebSupport.html(item.title())%></h3><span class="student-response"><%=item.hasAssignmentPage() ? "Trắc nghiệm" : "Tự luận"%></span></div>
                    <p><%=WebSupport.html(item.classCode())%></p><div class="student-item-bottom"><span>Hạn nộp: <%=WebSupport.html(AssignmentListView.deadline(item.dueAt()))%></span>
                    <% if (item.hasAssignmentPage()) { %><a href="${pageContext.request.contextPath}/student/assignments?assignmentId=<%=item.assignmentId()%>">Xem bài <span aria-hidden="true">→</span></a><% } %></div>
                </li>
            <% } %>
            </ul>
            <% } %>
            <a class="student-panel-link" href="${pageContext.request.contextPath}/student/assignments">Danh sách bài tập trắc nghiệm <span aria-hidden="true">→</span></a>
        </section>

        <section class="student-panel" aria-labelledby="student-results-title">
            <header class="student-panel-heading"><div><h2 id="student-results-title">Kết quả gần đây</h2><p>Chỉ hiển thị điểm đã công bố, bao gồm các lớp trước đây.</p></div></header>
            <% if (dashboard.results().isEmpty()) { %>
                <p class="student-empty">Chưa có kết quả được công bố.</p>
            <% } else { %>
            <ul class="student-result-list">
            <% for (var item : dashboard.results()) { %>
                <li><div><h3><%=WebSupport.html(item.title())%></h3><p><%=WebSupport.html(item.classCode())%> · Lượt <%=item.attemptNumber()%></p><small>Công bố: <%=WebSupport.html(AssignmentListView.deadline(item.releasedAt()))%> (UTC+07)</small></div><div class="student-score"><strong><%=WebSupport.html(item.score().stripTrailingZeros().toPlainString())%></strong><span>Điểm</span></div></li>
            <% } %>
            </ul><p class="student-panel-footnote">Hiển thị <%=dashboard.results().size()%> trong <%=dashboard.releasedResultCount()%> kết quả đã công bố.</p>
            <% } %>
        </section>
    </div>
</div>
</main></div>
</body></html>
