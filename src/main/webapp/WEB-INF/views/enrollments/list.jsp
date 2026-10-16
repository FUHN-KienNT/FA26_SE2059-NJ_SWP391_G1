<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="vn.edu.fpt.ams.dto.EnrollmentDTO" %>
<%@ page import="vn.edu.fpt.ams.dto.TeacherClassSummary" %>
<%@ page import="vn.edu.fpt.ams.model.StudentInvitation" %>
<%
  String context = request.getContextPath();
  @SuppressWarnings("unchecked")
  List<TeacherClassSummary> classes = (List<TeacherClassSummary>) request.getAttribute("classes");
  TeacherClassSummary selected = (TeacherClassSummary) request.getAttribute("selected");
  @SuppressWarnings("unchecked")
  List<EnrollmentDTO> students = (List<EnrollmentDTO>) request.getAttribute("students");
  @SuppressWarnings("unchecked")
  List<StudentInvitation> invitations = (List<StudentInvitation>) request.getAttribute("invitations");
%>
<!doctype html>
<html lang="vi">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Danh sách lớp phụ trách | AMS</title>
  <link rel="stylesheet" href="<%= context %>/css/app.css">
<%@ include file="/WEB-INF/views/shared/assets.jspf" %><style>.ams-content table th,.ams-content table td{word-break:normal;overflow-wrap:normal}.ams-content table .class-actions{min-width:150px}.class-actions a{white-space:nowrap}</style></head>
<body class="ams-ui teacher-app">
<%@ include file="/WEB-INF/views/shared/shell.jspf" %><main class="ams-content" id="page-content">
  <nav>
    <a href="<%= context %>/home">AMS</a>
    · <a href="<%= context %>/teacher/dashboard">Bảng điều khiển giảng viên</a>
  </nav>
  <h1>Danh sách lớp phụ trách</h1>
  <table>
    <tr><th>Mã lớp</th><th>Môn học</th><th>Học kỳ</th><th>Sĩ số</th><th>Chỗ còn lại</th><th>Thao tác</th></tr>
    <% for (TeacherClassSummary item : classes) { %>
      <tr>
        <td><a href="<%= context %>/teacher/enrollments?classId=<%= item.classId() %>"><%= item.classCode() %></a></td>
        <td><%= item.subjectCode() %> <%= item.subjectName() %></td>
        <td><%= item.termCode() %></td>
        <td><%= item.activeRoster() %> / <%= item.capacity() %></td>
        <td><%= item.availableSeats() %></td>
        <td class="class-actions"><a href="<%= context %>/teacher/assignments?classId=<%= item.classId() %>">Xem bài tập</a><br><a href="<%= context %>/teacher/enrollments?classId=<%= item.classId() %>&amp;mode=manage">Quản lý sinh viên</a></td>
      </tr>
    <% } %>
  </table>
  <% if (classes.isEmpty()) { %><p>Bạn chưa được phân công phụ trách lớp nào.</p><% } %>

  <% if (selected != null) { %>
    <h2>Danh sách sinh viên · <%= selected.classCode() %></h2>
    <p><%= selected.activeRoster() %> sinh viên đang học, <%= selected.pendingInvitations() %> lời mời đang chờ, <%= selected.availableSeats() %> chỗ còn lại.</p>
    <table>
      <tr><th>Sinh viên</th><th>Email</th><th>Trạng thái</th></tr>
      <% for (EnrollmentDTO student : students) { %>
        <tr>
          <td><%= student.studentName() %></td>
          <td><%= student.studentEmail() %></td>
          <td><%= vn.edu.fpt.ams.util.WebSupport.html(vn.edu.fpt.ams.util.Vietnamese.label(student.status())) %></td>
        </tr>
      <% } %>
    </table>
    <% if (students.isEmpty()) { %><p>Lớp này chưa có sinh viên.</p><% } %>
    <h2>Lời mời đang chờ</h2>
    <table>
      <tr><th>Họ tên</th><th>Email</th><th>Trạng thái</th><th>Hết hạn</th></tr>
      <% for (StudentInvitation invitation : invitations) { %>
        <tr>
          <td><%= invitation.fullName() == null ? "" : invitation.fullName() %></td>
          <td><%= invitation.email() %></td>
          <td><%= vn.edu.fpt.ams.util.WebSupport.html(vn.edu.fpt.ams.util.Vietnamese.label(invitation.status())) %></td>
          <td><%= vn.edu.fpt.ams.util.AssignmentListView.deadline(invitation.expiresAt()) %></td>
        </tr>
      <% } %>
    </table>
    <% if (invitations.isEmpty()) { %><p>Lớp này chưa có lời mời.</p><% } %>
  <% } %>
</main></div>
</body>
</html>
