<%@ page contentType="text/html;charset=UTF-8" %>
<!doctype html><html><head><title>Assignment Details | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"></head><body><main>
<nav><a href="${pageContext.request.contextPath}/teacher/assignments?classId=1">Assignment List</a></nav><h1>Assignment Details</h1><p>Mode: ${mode}. Assignment Publication Confirmation and Assignment Cancellation Confirmation use this page.</p><form action="${pageContext.request.contextPath}/teacher/assignments" method="post"><label>Class ID <input type="number" name="classId" required></label><label>Template ID <input type="number" name="templateId" required></label><label>Title <input name="title"></label><button>Save Assignment</button></form>
</main></body></html>
