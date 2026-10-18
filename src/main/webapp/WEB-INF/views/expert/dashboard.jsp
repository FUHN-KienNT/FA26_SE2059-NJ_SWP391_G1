<%@ page contentType="text/html;charset=UTF-8" import="vn.edu.fpt.ams.model.*,vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.util.WebSupport" %>
<%
    var account=(UserAccount)request.getAttribute("account");
    var summary=(ExpertDashboardDTO)request.getAttribute("summary");
%>
<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Bảng điều khiển chuyên gia | AMS</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css?v=expert-dashboard-3">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
    </head>
    <body class="expert-app baonvg-expert expert-dashboard ams-ui">
        <%@ include file="shell.jspf" %>
        <header class="expert-page-heading">
            <span class="expert-eyebrow">BẢNG ĐIỀU KHIỂN</span>
            <h1>
                Bảng điều khiển chuyên gia
            </h1>
            <p>
                Xin chào <%=WebSupport.html(account.fullName())%>. Dưới đây là tổng quan các môn học, lớp và mẫu bài tập của bạn.
            </p>
        </header>
        <section class="expert-metrics" aria-label="Tổng quan">
            <a class="expert-metric" href="${pageContext.request.contextPath}/expert/classes"><span class="metric-icon book">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M12 5c-3-2-6-2-9-1v15c3-1 6-1 9 1 3-2 6-2 9-1V4c-3-1-6-1-9 1Z"/><path d="M12 5v15"/></svg></span><span class="metric-label">Môn học được phân công</span>
            <strong><%=summary.assignedSubjects()%></strong><small>Xem môn học <span aria-hidden="true">→</span></small></a>
            <a class="expert-metric" href="${pageContext.request.contextPath}/expert/classes"><span class="metric-icon users">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <circle cx="9" cy="7" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3M17 4a3 3 0 0 1 0 6M18 14a5 5 0 0 1 3 4v3"/></svg></span>
            <span class="metric-label">Lớp có thể xem</span><strong><%=summary.accessibleClasses()%></strong><small>Xem danh sách lớp
            <span aria-hidden="true">→</span></small></a>
  <a class="expert-metric" href="${pageContext.request.contextPath}/expert/templates">
            <span class="metric-icon file">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M14 2H5v20h14V7Z M14 2v5h5 M8 12h8 M8 16h8"/></svg></span><span class="metric-label">Mẫu bài tập có thể xem</span><strong><%=summary.visibleTemplates()%></strong>
            <small>Xem mẫu bài tập <span aria-hidden="true">→</span></small></a>
            <div id="additional-class-requests" class="expert-metric">
                <span class="metric-icon clock">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <circle cx="12" cy="12" r="9"/><path d="M12 6v6l4 2"/></svg></span><span class="metric-label">Yêu cầu lớp bổ sung</span><strong><%=summary.pendingAdditionalClassRequests()%></strong>
                <small>Chờ xem xét</small>
            </div>
        </section>
        <section class="expert-quick" aria-labelledby="quick-heading">
            <h2 id="quick-heading">
                Truy cập nhanh
            </h2>
            <p class="expert-quick-description">
                Truy cập nhanh các chức năng bạn thường sử dụng.
            </p>
            <div class="expert-quick-links">
                <a href="${pageContext.request.contextPath}/expert/classes"><span class="quick-icon book">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M12 5c-3-2-6-2-9-1v15c3-1 6-1 9 1 3-2 6-2 9-1V4c-3-1-6-1-9 1Z"/><path d="M12 5v15"/></svg></span><span class="quick-copy"><strong>Môn học và lớp được phân công</strong>
                <small>Xem danh sách các môn học và lớp bạn được phân công.</small></span><span class="quick-arrow" aria-hidden="true">›</span></a>
                <a href="${pageContext.request.contextPath}/expert/templates"><span class="quick-icon file">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M14 2H5v20h14V7Z M14 2v5h5 M8 12h8 M8 16h8"/></svg></span><span class="quick-copy"><strong>Mẫu bài tập</strong><small>Xem các mẫu bài tập bạn có thể sử dụng.</small></span>
                <span class="quick-arrow" aria-hidden="true">›</span></a>
            </div>
        </section>
    </main>
</div>
</body>
</html>
