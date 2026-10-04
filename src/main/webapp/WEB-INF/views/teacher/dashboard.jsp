<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="vn.edu.fpt.ams.dto.TeacherClassSummary" %>
<%@ page import="vn.edu.fpt.ams.model.UserAccount" %>
<%
  UserAccount teacher = (UserAccount) request.getAttribute("teacher");
  @SuppressWarnings("unchecked")
  List<TeacherClassSummary> classes = (List<TeacherClassSummary>) request.getAttribute("classes");
  long submissionCount = (Long) request.getAttribute("submissionCount");
  long assignmentCount = (Long) request.getAttribute("assignmentCount");
  long rosterCount = (Long) request.getAttribute("rosterCount");
  String context = request.getContextPath();
%>
<!doctype html>
<html>
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Teacher Dashboard | AMS</title>
  <link rel="stylesheet" href="<%= context %>/css/app.css">
<%@ include file="/WEB-INF/views/shared/assets.jspf" %></head>
<body class="ams-ui teacher-app">
<%@ include file="/WEB-INF/views/shared/shell.jspf" %><main class="ams-content" id="page-content">
  <nav>
    <a href="<%= context %>/home">AMS</a>
    · <a href="<%= context %>/teacher/enrollments">Assigned classes</a>
    · <a href="<%= context %>/teacher/assignments">Assignments</a>
  </nav>
  <h1>Teacher Dashboard</h1>
  <p><%= teacher.fullName() %> · <%= teacher.email() %></p>
  <p><%= classes.size() %> assigned class(es) · <%= rosterCount %> active student(s) · <%= assignmentCount %> assignment(s) · <%= submissionCount %> submission(s)</p>
  <table>
    <tr>
      <th>Class</th><th>Subject</th><th>Term</th><th>Roster</th><th>Invitations</th><th>Seats left</th><th>Assignments</th><th>Actions</th>
    </tr>
    <% for (TeacherClassSummary item : classes) { %>
      <tr>
        <td><a href="<%= context %>/teacher/enrollments?classId=<%= item.classId() %>"><%= item.classCode() %></a><br><%= item.className() %></td>
        <td><%= item.subjectCode() %> <%= item.subjectName() %></td>
        <td><%= item.termCode() %></td>
        <td><%= item.activeRoster() %> / <%= item.capacity() %></td>
        <td><%= item.pendingInvitations() %></td>
        <td><%= item.availableSeats() %></td>
        <td><%= item.assignmentCount() %></td>
        <td><a href="<%= context %>/teacher/assignments?classId=<%= item.classId() %>">View assignments</a></td>
      </tr>
    <% } %>
  </table>
  <% if (classes.isEmpty()) { %>
    <p>No class is assigned to this teacher.</p>
  <% } %>
</main></div>
</body>
</html>
