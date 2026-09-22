<%@ page contentType="text/html;charset=UTF-8" %>
<!doctype html><html><head><meta charset="UTF-8"><title>Create Subject Class | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"></head>
<body><main><nav><a href="${pageContext.request.contextPath}/expert/classes">Subject Classes</a></nav><h1>Create Subject Class</h1>
<p>A class may be saved without a Teacher during setup. Writes are pending in this initial iteration.</p>
<form action="${pageContext.request.contextPath}/expert/classes" method="post"><input type="hidden" name="action" value="create">
<label>Subject Term ID <input type="number" name="subjectTermId" min="1" required></label>
<label>Class code <input name="classCode" maxlength="30" required></label>
<label>Class name <input name="className" maxlength="150" required></label>
<label>Capacity <input type="number" name="capacity" min="1" required></label>
<label><input type="checkbox" name="registrationEnabled"> Registration enabled</label><button>Create class</button></form>
</main></body></html>
