<%@ page contentType="text/html;charset=UTF-8" %>
<!doctype html><html><head><meta charset="UTF-8"><title>Assign Teacher | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"></head>
<body><main><nav><a href="${pageContext.request.contextPath}/expert/classes">Subject Classes</a></nav><h1>Assign Teacher</h1>
<p>Only an active TEACHER account may be assigned. Writes are pending in this initial iteration.</p>
<form action="${pageContext.request.contextPath}/expert/classes" method="post"><input type="hidden" name="action" value="assignTeacher">
<label>Class ID <input type="number" name="classId" min="1" required></label>
<label>Teacher ID <input type="number" name="teacherId" min="1" required></label><button>Assign Teacher</button></form>
</main></body></html>
