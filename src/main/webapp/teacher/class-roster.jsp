<%@ page contentType="text/html;charset=UTF-8" %>
<!doctype html><html><head><meta charset="UTF-8"><title>Class Roster | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"></head>
<body><main><nav><a href="${pageContext.request.contextPath}/teacher/enrollments">Assigned Classes</a></nav><h1>Class Roster</h1>
<p>Student identity, registration and active enrollment status will appear here after JDBC access is implemented.</p>
<table><tr><th>Student</th><th>Email</th><th>Registration</th><th>Enrollment status</th></tr></table>
<p><a href="${pageContext.request.contextPath}/teacher/student-enrollment.jsp">Manage Student Enrollment</a></p>
</main></body></html>
