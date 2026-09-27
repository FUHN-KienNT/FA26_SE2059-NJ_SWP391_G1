<%@ page contentType="text/html;charset=UTF-8" %>
<!doctype html><html><head><title>Assigned Class List | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"></head><body><main>
<nav><a href="${pageContext.request.contextPath}/home">AMS</a></nav><h1>Assigned Class List</h1><table><tr><th>Class code</th><th>Subject</th><th>Term</th><th>Roster</th></tr></table><form action="${pageContext.request.contextPath}/teacher/enrollments" method="get"><label>Class ID <input type="number" name="classId" required></label><button>Open Student Enrollment Management</button></form>
</main></body></html>
