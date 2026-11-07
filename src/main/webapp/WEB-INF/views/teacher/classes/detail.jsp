<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="vn.edu.fpt.ams.dto.TeacherClassSummary" %>
<%@ page import="vn.edu.fpt.ams.util.WebSupport" %>
<%@ page import="java.util.List" %>
<%
    String context = request.getContextPath();
    TeacherClassSummary selected = (TeacherClassSummary) request.getAttribute("selected");
%>
<!doctype html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Chi tiết lớp | AMS</title>
    <link rel="stylesheet" href="<%= context %>/css/app.css">
    <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
</head>
<body class="ams-ui teacher-app">
    <%@ include file="/WEB-INF/views/shared/shell.jspf" %>
    <main class="ams-content" id="page-content">
        <nav><a href="<%= context %>/teacher/classes">Danh sách lớp phụ trách</a></nav>
        <h1>Chi tiết lớp · <%= WebSupport.html(selected.classCode()) %></h1>
        <section class="ams-panel" aria-label="Thông tin lớp">
            <dl>
                <dt>Tên lớp</dt><dd><%= WebSupport.html(selected.className()) %></dd>
                <dt>Môn học</dt><dd><%= WebSupport.html(selected.subjectCode()) %> · <%= WebSupport.html(selected.subjectName()) %></dd>
                <dt>Học kỳ</dt><dd><%= WebSupport.html(selected.termCode()) %></dd>
                <dt>Trạng thái</dt><dd><%= "ACTIVE".equals(selected.status()) ? "Đang hoạt động" : "Ngừng hoạt động" %></dd>
            </dl>
        </section>
        <section class="ams-metrics" aria-label="Thống kê lớp">
            <div class="ams-metric"><span>Sức chứa</span><strong><%= selected.capacity() %></strong></div>
            <div class="ams-metric"><span>Sinh viên đang học</span><strong><%= selected.activeRoster() %></strong></div>
            <div class="ams-metric"><span>Chỗ đã giữ</span><strong><%= selected.pendingInvitations() %></strong></div>
            <div class="ams-metric"><span>Chỗ còn lại</span><strong><%= selected.availableSeats() %></strong></div>
        </section>
        <p>
            <a class="ams-button ams-button-primary" href="<%= context %>/teacher/students?classId=<%= selected.classId() %>">Danh sách sinh viên</a>
            <a class="ams-button" href="<%= context %>/teacher/groups?classId=<%= selected.classId() %>">Danh sách nhóm</a>
            <a class="ams-button" href="<%= context %>/teacher/assignments?classId=<%= selected.classId() %>">Xem bài tập</a>
        </p>
    </main>
    </div>
</body>
</html>
