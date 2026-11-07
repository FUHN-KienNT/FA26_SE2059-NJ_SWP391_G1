<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="vn.edu.fpt.ams.dto.TeacherClassSummary" %>
<%@ page import="vn.edu.fpt.ams.dto.EnrollmentDTO" %>
<%@ page import="vn.edu.fpt.ams.dto.TeacherGroup" %>
<%@ page import="vn.edu.fpt.ams.util.WebSupport" %>
<%
    String context = request.getContextPath();
    TeacherClassSummary selected = (TeacherClassSummary) request.getAttribute("selected");
    EnrollmentDTO student = (EnrollmentDTO) request.getAttribute("student");
    List<TeacherGroup> groups = (List<TeacherGroup>) request.getAttribute("studentGroups");
%>
<!doctype html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Chi tiết sinh viên | AMS</title>
    <link rel="stylesheet" href="<%= context %>/css/app.css">
    <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
</head>
<body class="ams-ui teacher-app">
    <%@ include file="/WEB-INF/views/shared/shell.jspf" %>
    <main class="ams-content" id="page-content">
        <nav><a href="<%= context %>/teacher/classes/detail?classId=<%= selected.classId() %>">Chi tiết lớp</a>
            · <a href="<%= context %>/teacher/students?classId=<%= selected.classId() %>">Danh sách sinh viên</a></nav>
        <h1>Chi tiết sinh viên</h1>
        <section class="ams-panel">
            <dl>
                <dt>Họ và tên</dt><dd><%= WebSupport.html(student.studentName()) %></dd>
                <dt>Email</dt><dd><%= WebSupport.html(student.studentEmail()) %></dd>
                <dt>Mã tài khoản</dt><dd><%= student.studentId() %></dd>
                <dt>Lớp</dt><dd><%= WebSupport.html(selected.classCode()) %> · <%= WebSupport.html(selected.className()) %></dd>
                <dt>Môn học</dt><dd><%= WebSupport.html(selected.subjectCode()) %> · <%= WebSupport.html(selected.subjectName()) %></dd>
                <dt>Học kỳ</dt><dd><%= WebSupport.html(selected.termCode()) %></dd>
                <dt>Trạng thái ghi danh</dt><dd><%= vn.edu.fpt.ams.util.Vietnamese.label(student.status()) %></dd>
            </dl>
        </section>
        <h2>Nhóm hiện tại trong lớp</h2>
        <% if (groups.isEmpty()) { %>
        <p>Sinh viên chưa tham gia nhóm đang hoạt động trong lớp này.</p>
        <% } %>
        <% for (TeacherGroup group : groups) { %>
        <p><a href="<%= context %>/teacher/groups/detail?classId=<%= selected.classId() %>&amp;groupId=<%= group.groupId() %>"><%= WebSupport.html(group.name()) %></a>
            <%= Long.valueOf(student.studentId()).equals(group.leaderStudentId()) ? " · Trưởng nhóm" : " · Thành viên" %></p>
        <% } %>
        <a class="ams-button" href="<%= context %>/teacher/groups?classId=<%= selected.classId() %>">Quản lý nhóm của lớp</a>
    </main>
    </div>
</body>
</html>
