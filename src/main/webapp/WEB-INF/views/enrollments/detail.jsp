<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="vn.edu.fpt.ams.dto.EnrollmentDTO" %>
<%@ page import="vn.edu.fpt.ams.dto.TeacherClassSummary" %>
<%
  String context = request.getContextPath();
  TeacherClassSummary selected = (TeacherClassSummary) request.getAttribute("selected");
  @SuppressWarnings("unchecked")
  List<EnrollmentDTO> students = (List<EnrollmentDTO>) request.getAttribute("students");
  String error = (String) request.getAttribute("error");
  String notice = (String) request.getAttribute("notice");
%>
<!doctype html>
<html>
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Student Enrollment Management | AMS</title>
  <link rel="stylesheet" href="<%= context %>/css/app.css">
<%@ include file="/WEB-INF/views/shared/assets.jspf" %></head>
<body class="ams-ui teacher-app">
<%@ include file="/WEB-INF/views/shared/shell.jspf" %><main class="ams-content" id="page-content">
  <nav>
    <a href="<%= context %>/teacher/dashboard">Teacher Dashboard</a>
    · <a href="<%= context %>/teacher/enrollments?classId=<%= selected.classId() %>">Class roster</a>
  </nav>
  <h1>Student Enrollment Management</h1>
  <p><%= selected.classCode() %> · <%= selected.subjectCode() %> · <%= selected.activeRoster() %> / <%= selected.capacity() %> active · <%= selected.availableSeats() %> seat(s) left</p>
  <% if (notice != null) { %><p><%= notice %></p><% } %>
  <% if (error != null) { %><p><%= error %></p><% } %>
  <table>
    <tr><th>Student</th><th>Email</th><th>Status</th><th></th></tr>
    <% for (EnrollmentDTO student : students) { %>
      <tr>
        <td><%= student.studentName() %></td>
        <td><%= student.studentEmail() %></td>
        <td><%= student.status() %></td>
        <td>
          <form action="<%= context %>/teacher/enrollments" method="post">
            <input type="hidden" name="action" value="updateStatus">
            <input type="hidden" name="classId" value="<%= selected.classId() %>">
            <input type="hidden" name="enrollmentId" value="<%= student.enrollmentId() %>">
            <input type="hidden" name="status" value="<%= student.status().name().equals("ACTIVE") ? "INACTIVE" : "ACTIVE" %>">
            <button><%= student.status().name().equals("ACTIVE") ? "Deactivate" : "Activate" %></button>
          </form>
        </td>
      </tr>
    <% } %>
  </table>

  <h2>Add an existing student</h2>
  <form action="<%= context %>/teacher/enrollments" method="post">
    <input type="hidden" name="action" value="addStudent">
    <input type="hidden" name="classId" value="<%= selected.classId() %>">
    <label>Student email <input type="email" name="email" required></label>
    <button>Add student</button>
  </form>

  <h2>Import students</h2>
  <form action="<%= context %>/teacher/enrollments" method="post" enctype="multipart/form-data">
    <input type="hidden" name="action" value="importStudents">
    <input type="hidden" name="classId" value="<%= selected.classId() %>">
    <label>CSV file <input type="file" name="file" accept=".csv,text/csv" required></label>
    <button>Import students</button>
  </form>
</main></div>
</body>
</html>
