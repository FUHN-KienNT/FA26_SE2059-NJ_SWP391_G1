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
<head>
  <title>Teacher Dashboard | AMS</title>
  <link rel="stylesheet" href="<%= context %>/css/app.css">
</head>
<body>
<main>
  <nav>
    <a href="<%= context %>/home">AMS</a>
    · <a href="<%= context %>/teacher/enrollments">Assigned classes</a>
  </nav>
  <h1>Teacher Dashboard</h1>
  <p><%= teacher.fullName() %> · <%= teacher.email() %></p>
  <p><%= classes.size() %> assigned class(es) · <%= rosterCount %> active student(s) · <%= assignmentCount %> assignment(s) · <%= submissionCount %> submission(s)</p>
  <table>
    <tr>
      <th>Class</th><th>Subject</th><th>Term</th><th>Roster</th><th>Invitations</th><th>Seats left</th><th>Assignments</th>
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
      </tr>
    <% } %>
  </table>
  <% if (classes.isEmpty()) { %>
    <p>No class is assigned to this teacher.</p>
  <% } %>
</main>
</body>
</html>
