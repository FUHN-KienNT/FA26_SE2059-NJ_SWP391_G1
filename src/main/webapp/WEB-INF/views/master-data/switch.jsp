<%@ page contentType="text/html;charset=UTF-8" import="vn.edu.fpt.ams.model.AcademicTerm,vn.edu.fpt.ams.util.WebSupport" %>
<%
    AcademicTerm oldTerm=(AcademicTerm)request.getAttribute("activeTerm");
    AcademicTerm newTerm=(AcademicTerm)request.getAttribute("term");
    String token=WebSupport.csrf(request);
%>
<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Chuyển học kỳ | AMS</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
    </head>
    <body class="admin-users admin-app admin-terms ams-ui">
        <%@ include file="/WEB-INF/views/admin/shell.jspf" %>
        <main class="users-main subject-form ams-content bao-mode-page" id="page-content">
            <nav class="bao-context" aria-label="Ngữ cảnh"><a href="${pageContext.request.contextPath}/admin/terms">Học kỳ</a><span aria-hidden="true">›</span><span>Chuyển học kỳ</span></nav>
            <div class="subject-heading">
                <div>
                    <h1>
                        Chuyển học kỳ hoạt động
                    </h1>
                    <p class="muted">
                        Xem lại học kỳ cũ và học kỳ thay thế trước khi xác nhận.
                    </p>
                </div>
                <a class="secondary-button" href="${pageContext.request.contextPath}/admin/terms">← Quay lại danh sách</a>
            </div>
            <div class="bao-term-layout">
                <section class="bao-card">
                    <h2>
                        Học kỳ sẽ ngừng hoạt động
                    </h2>
                    <p>
                        <strong><%=WebSupport.html(oldTerm.termCode())%></strong> — <%=WebSupport.html(oldTerm.termName())%>
                    </p>
                    <span class="status-badge active">Đang hoạt động</span>
                    <%
                        if(oldTerm.current()){
                        %>
                        <span class="term-current-badge current">Hiện tại</span>
                        <%
                        }
                    %>
                </section>
                <section class="bao-card">
                    <h2>
                        Học kỳ sẽ hoạt động và thành hiện tại
                    </h2>
                    <p>
                        <strong><%=WebSupport.html(newTerm.termCode())%></strong> — <%=WebSupport.html(newTerm.termName())%>
                    </p>
                    <span class="status-badge inactive">Ngừng hoạt động</span>
                </section>
            </div>
            <p class="muted">
                Xác nhận sẽ ngừng hoạt động học kỳ cũ, kích hoạt học kỳ mới và đặt học kỳ mới thành hiện tại. Dữ liệu học kỳ không bị xóa.
            </p>
            <form method="post" action="${pageContext.request.contextPath}/admin/master-data" class="form-actions">
                <input type="hidden" name="csrf" value="<%=WebSupport.html(token)%>">
                <input type="hidden" name="action" value="switch">
                <input type="hidden" name="termId" value="<%=newTerm.termId()%>">
                <input type="hidden" name="previousTermId" value="<%=oldTerm.termId()%>">
                <a class="secondary-button" href="${pageContext.request.contextPath}/admin/terms">Hủy</a>
                <button type="submit" class="primary-button">
                    Xác nhận chuyển học kỳ
                </button>
            </form>
        </main>
    </div>
</body>
</html>