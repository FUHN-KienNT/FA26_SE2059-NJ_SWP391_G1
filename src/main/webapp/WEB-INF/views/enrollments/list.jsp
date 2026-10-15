<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="vn.edu.fpt.ams.dto.EnrollmentDTO" %>
<%@ page import="vn.edu.fpt.ams.dto.TeacherClassSummary" %>
<%@ page import="vn.edu.fpt.ams.model.StudentInvitation" %>
<%@ page import="vn.edu.fpt.ams.util.WebSupport" %>
<%
  String context = request.getContextPath();
  @SuppressWarnings("unchecked")
  List<TeacherClassSummary> classes = (List<TeacherClassSummary>) request.getAttribute("classes");
  TeacherClassSummary selected = (TeacherClassSummary) request.getAttribute("selected");
  @SuppressWarnings("unchecked")
  List<EnrollmentDTO> students = (List<EnrollmentDTO>) request.getAttribute("students");
  @SuppressWarnings("unchecked")
  List<StudentInvitation> invitations = (List<StudentInvitation>) request.getAttribute("invitations");
  String query = request.getAttribute("query") == null ? "" : (String) request.getAttribute("query");
  String studentQuery = request.getAttribute("studentQuery") == null ? "" : (String) request.getAttribute("studentQuery");
  String inviteQuery = request.getAttribute("inviteQuery") == null ? "" : (String) request.getAttribute("inviteQuery");
  int classTotal = request.getAttribute("classTotal") == null ? classes.size() : (Integer) request.getAttribute("classTotal");
  String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
%>
<!doctype html>
<html lang="vi">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Lớp phụ trách | AMS</title>
  <link rel="stylesheet" href="<%= context %>/css/app.css">
<%@ include file="/WEB-INF/views/shared/assets.jspf" %></head>
<body class="ams-ui teacher-app">
<%@ include file="/WEB-INF/views/shared/shell.jspf" %><main class="ams-content" id="page-content">
  <h1>Lớp phụ trách</h1>
  <p class="muted">Đây là các lớp bạn đang dạy. Lớp được phân công cho chuyên gia là việc khác: chuyên gia được giao theo dõi môn, không quản lý sĩ số ở màn này.</p>
  <form class="users-toolbar" method="get" action="<%= context %>/teacher/enrollments">
    <% if (selected != null) { %><input type="hidden" name="classId" value="<%= selected.classId() %>"><% } %>
    <label>Tìm lớp <input type="search" name="q" value="<%= WebSupport.html(query) %>" placeholder="Mã lớp, tên lớp, môn hoặc học kỳ"></label>
    <button type="submit">Tìm</button>
    <a class="ams-button" href="<%= context %>/teacher/enrollments<%= selected == null ? "" : "?classId=" + selected.classId() %>">Xóa tìm</a>
  </form>
  <table>
    <tr><th>Mã lớp</th><th>Môn</th><th>Học kỳ</th><th>Sĩ số</th><th>Chỗ trống</th><th></th></tr>
    <% for (TeacherClassSummary item : classes) { %>
      <tr>
        <td><a href="<%= context %>/teacher/enrollments?classId=<%= item.classId() %>&amp;q=<%= encodedQuery %>"><%= WebSupport.html(item.classCode()) %></a><br><%= WebSupport.html(item.className()) %></td>
        <td><%= WebSupport.html(item.subjectCode()) %> <%= WebSupport.html(item.subjectName()) %></td>
        <td><%= WebSupport.html(item.termCode()) %></td>
        <td><%= item.activeRoster() %> / <%= item.capacity() %></td>
        <td><%= item.availableSeats() %></td>
        <td><a href="<%= context %>/teacher/enrollments?classId=<%= item.classId() %>&amp;mode=manage">Quản lý ghi danh</a></td>
      </tr>
    <% } %>
  </table>
  <% if (classes.isEmpty()) { %><p><%= query.isBlank() ? "Bạn chưa phụ trách lớp nào." : "Không có lớp phụ trách khớp từ khóa." %></p><% } else if (!query.isBlank()) { %><p class="muted">Đang hiện <%= classes.size() %> / <%= classTotal %> lớp.</p><% } %>

  <% if (selected != null) { %>
    <h2>Sĩ số lớp · <%= WebSupport.html(selected.classCode()) %></h2>
    <p><%= selected.activeRoster() %> sinh viên đang học, <%= selected.pendingInvitations() %> lời mời còn hạn, <%= selected.availableSeats() %> chỗ trống.</p>
    <form class="users-toolbar" method="get" action="<%= context %>/teacher/enrollments">
      <input type="hidden" name="classId" value="<%= selected.classId() %>">
      <input type="hidden" name="q" value="<%= WebSupport.html(query) %>">
      <input type="hidden" name="invite" value="<%= WebSupport.html(inviteQuery) %>">
      <label>Tìm sinh viên <input type="search" name="student" value="<%= WebSupport.html(studentQuery) %>" placeholder="Tên hoặc email"></label>
      <button type="submit">Tìm</button>
    </form>
    <table>
      <tr><th>Sinh viên</th><th>Email</th><th>Trạng thái</th></tr>
      <% for (EnrollmentDTO student : students) { %>
        <tr>
          <td><%= WebSupport.html(student.studentName()) %></td>
          <td><%= WebSupport.html(student.studentEmail()) %></td>
          <td><%= WebSupport.html(student.status().name()) %></td>
        </tr>
      <% } %>
    </table>
    <% if (students.isEmpty()) { %><p><%= studentQuery.isBlank() ? "Lớp chưa có sinh viên." : "Không có sinh viên khớp từ khóa." %></p><% } %>

    <h2>Lời mời sinh viên</h2>
    <form class="users-toolbar" method="get" action="<%= context %>/teacher/enrollments">
      <input type="hidden" name="classId" value="<%= selected.classId() %>">
      <input type="hidden" name="q" value="<%= WebSupport.html(query) %>">
      <input type="hidden" name="student" value="<%= WebSupport.html(studentQuery) %>">
      <label>Tìm lời mời <input type="search" name="invite" value="<%= WebSupport.html(inviteQuery) %>" placeholder="Tên hoặc email"></label>
      <button type="submit">Tìm</button>
    </form>
    <table>
      <tr><th>Tên</th><th>Email</th><th>Trạng thái</th><th>Hết hạn</th></tr>
      <% for (StudentInvitation invitation : invitations) { %>
        <tr>
          <td><%= WebSupport.html(invitation.fullName()) %></td>
          <td><%= WebSupport.html(invitation.email()) %></td>
          <td><%= WebSupport.html(invitation.status().name()) %></td>
          <td><%= invitation.expiresAt() %></td>
        </tr>
      <% } %>
    </table>
    <% if (invitations.isEmpty()) { %><p><%= inviteQuery.isBlank() ? "Lớp chưa có lời mời." : "Không có lời mời khớp từ khóa." %></p><% } %>
  <% } %>
</main></div>
</body>
</html>
