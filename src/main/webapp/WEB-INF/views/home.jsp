<%@ page contentType="text/html;charset=UTF-8" %>
<!doctype html><html><head><title>AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"></head>
<body><main><h1>AMS Assignment Management System</h1><p>Initial codebase synchronized with the current domain model.</p>
<nav><a href="${pageContext.request.contextPath}/admin/users">User Management</a> · <a href="${pageContext.request.contextPath}/admin/master-data">Master Data</a> · <a href="${pageContext.request.contextPath}/admin/subjects">Subjects</a> · <a href="${pageContext.request.contextPath}/expert/classes">Subject Classes</a> · <a href="${pageContext.request.contextPath}/teacher/enrollments">Enrollments</a></nav>
<form action="${pageContext.request.contextPath}/home" method="post">
  <input type="hidden" name="action" value="demo-teacher">
  <button>Open Teacher Dashboard as teacher@example.test</button>
</form>
</main></body></html>
