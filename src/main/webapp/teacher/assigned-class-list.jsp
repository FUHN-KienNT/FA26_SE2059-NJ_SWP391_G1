<%@ page contentType="text/html;charset=UTF-8" %>
<!doctype html><html><head><meta charset="UTF-8"><title>Assigned Classes | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"></head>
<body><main><nav><a href="${pageContext.request.contextPath}/">AMS</a></nav><h1>Assigned Classes</h1>
<p>Classes assigned to the current Teacher will appear here after JDBC access is implemented.</p>
<table><tr><th>Class code</th><th>Subject</th><th>Term</th><th>Roster</th></tr></table>
<form action="${pageContext.request.contextPath}/teacher/enrollments" method="get"><label>Class ID <input type="number" name="classId" min="1" required></label><button>Open roster</button></form>
</main></body></html>
