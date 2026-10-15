<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="vn.edu.fpt.ams.dto.TeacherClassSummary" %>
<%@ page import="vn.edu.fpt.ams.model.UserAccount" %>
<%@ page import="vn.edu.fpt.ams.util.WebSupport" %>
<%
  UserAccount teacher = (UserAccount) request.getAttribute("teacher");
  @SuppressWarnings("unchecked")
  List<TeacherClassSummary> classes = (List<TeacherClassSummary>) request.getAttribute("classes");
  long submissionCount = (Long) request.getAttribute("submissionCount");
  long assignmentCount = (Long) request.getAttribute("assignmentCount");
  long rosterCount = (Long) request.getAttribute("rosterCount");
  long openOfferCount = (Long) request.getAttribute("openOfferCount");
  int classTotal = (Integer) request.getAttribute("classTotal");
  String query = (String) request.getAttribute("query");
  String context = request.getContextPath();
%>
<!doctype html>
<html lang="vi">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Bảng điều khiển giảng viên | AMS</title>
  <link rel="stylesheet" href="<%= context %>/css/app.css">
<%@ include file="/WEB-INF/views/shared/assets.jspf" %></head>
<body class="ams-ui teacher-app">
<%@ include file="/WEB-INF/views/shared/shell.jspf" %><main class="ams-content" id="page-content">
  <h1>Bảng điều khiển giảng viên</h1>
  <p><%= WebSupport.html(teacher.fullName()) %> · <%= WebSupport.html(teacher.email()) %></p>
  <p class="muted">Lớp phụ trách là lớp bạn đang dạy, vì bạn là giảng viên của lớp. Lớp được phân công là lớp giao cho chuyên gia theo dõi môn học, không hiện ở màn này.</p>
  <p><%= classTotal %> lớp phụ trách · <%= rosterCount %> sinh viên đang học · <%= assignmentCount %> bài tập · <%= submissionCount %> bài nộp · <%= openOfferCount %> lời mời lớp bổ sung còn hạn</p>
  <p><a href="<%= context %>/teacher/offers">Xem lời mời lớp bổ sung</a></p>
  <form class="users-toolbar" method="get" action="<%= context %>/teacher/dashboard">
    <label>Tìm lớp <input type="search" name="q" value="<%= WebSupport.html(query) %>" placeholder="Mã lớp, tên lớp, môn hoặc học kỳ"></label>
    <button type="submit">Tìm</button>
    <a class="ams-button" href="<%= context %>/teacher/dashboard">Xóa tìm</a>
  </form>
  <table>
    <tr>
      <th>Lớp</th><th>Môn</th><th>Học kỳ</th><th>Sĩ số</th><th>Lời mời</th><th>Chỗ trống</th><th>Bài tập</th><th></th>
    </tr>
    <% for (TeacherClassSummary item : classes) { %>
      <tr>
        <td><a href="<%= context %>/teacher/enrollments?classId=<%= item.classId() %>"><%= WebSupport.html(item.classCode()) %></a><br><%= WebSupport.html(item.className()) %></td>
        <td><%= WebSupport.html(item.subjectCode()) %> <%= WebSupport.html(item.subjectName()) %></td>
        <td><%= WebSupport.html(item.termCode()) %></td>
        <td><%= item.activeRoster() %> / <%= item.capacity() %></td>
        <td><%= item.pendingInvitations() %></td>
        <td><%= item.availableSeats() %></td>
        <td><%= item.assignmentCount() %></td>
        <td><a href="<%= context %>/teacher/assignments?classId=<%= item.classId() %>">Xem bài tập</a></td>
      </tr>
    <% } %>
  </table>
  <% if (classes.isEmpty()) { %>
    <p><%= query.isBlank() ? "Bạn chưa phụ trách lớp nào." : "Không có lớp phụ trách khớp từ khóa." %></p>
  <% } else if (!query.isBlank()) { %>
    <p class="muted">Đang hiện <%= classes.size() %> / <%= classTotal %> lớp.</p>
  <% } %>
</main></div>
</body>
</html>
