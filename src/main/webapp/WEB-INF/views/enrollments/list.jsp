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
<html>
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Assigned Class List | AMS</title>
  <link rel="stylesheet" href="<%= context %>/css/app.css">
<%@ include file="/WEB-INF/views/shared/assets.jspf" %></head>
<body class="ams-ui teacher-app">
<%@ include file="/WEB-INF/views/shared/shell.jspf" %><main class="ams-content" id="page-content">
  <nav>
    <a href="<%= context %>/home">AMS</a>
    · <a href="<%= context %>/teacher/dashboard">Teacher Dashboard</a>
  </nav>
  <h1>Assigned Class List</h1>
  <table>
    <tr><th>Class code</th><th>Subject</th><th>Term</th><th>Roster</th><th>Seats left</th><th></th></tr>
    <% for (TeacherClassSummary item : classes) { %>
      <tr>
        <td><a href="<%= context %>/teacher/enrollments?classId=<%= item.classId() %>"><%= item.classCode() %></a></td>
        <td><%= item.subjectCode() %> <%= item.subjectName() %></td>
        <td><%= item.termCode() %></td>
        <td><%= item.activeRoster() %> / <%= item.capacity() %></td>
        <td><%= item.availableSeats() %></td>
        <td><a href="<%= context %>/teacher/enrollments?classId=<%= item.classId() %>&amp;mode=manage">Manage enrollment</a></td>
      </tr>
    <% } %>
  </table>
  <% if (classes.isEmpty()) { %><p>No class is assigned to this teacher.</p><% } %>

  <% if (selected != null) { %>
    <h2>Class roster · <%= selected.classCode() %></h2>
    <p><%= selected.activeRoster() %> active student(s), <%= selected.pendingInvitations() %> pending invitation(s), <%= selected.availableSeats() %> seat(s) left.</p>
    <table>
      <tr><th>Student</th><th>Email</th><th>Status</th></tr>
      <% for (EnrollmentDTO student : students) { %>
        <tr>
          <td><%= student.studentName() %></td>
          <td><%= student.studentEmail() %></td>
          <td><%= student.status() %></td>
        </tr>
      <% } %>
    </table>
    <% if (students.isEmpty()) { %><p>No student is enrolled in this class.</p><% } %>
    <h2>Pending invitations</h2>
    <table>
      <tr><th>Name</th><th>Email</th><th>Status</th><th>Expires</th></tr>
      <% for (StudentInvitation invitation : invitations) { %>
        <tr>
          <td><%= invitation.fullName() == null ? "" : invitation.fullName() %></td>
          <td><%= invitation.email() %></td>
          <td><%= invitation.status() %></td>
          <td><%= invitation.expiresAt() %></td>
        </tr>
      <% } %>
    </table>
    <% if (invitations.isEmpty()) { %><p>No invitation is stored for this class.</p><% } %>
  <% } %>
</main></div>
</body>
</html>
