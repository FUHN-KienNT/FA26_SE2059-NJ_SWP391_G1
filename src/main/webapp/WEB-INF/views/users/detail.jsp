<%@ page contentType="text/html;charset=UTF-8" %>
<!doctype html><html><head><title>User Details | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"></head><body><main>
<nav><a href="${pageContext.request.contextPath}/admin/users">User List</a></nav><h1>User Details</h1><p>Mode: ${mode}</p>
<form action="${pageContext.request.contextPath}/admin/users" method="post"><input type="hidden" name="action" value="save"><label>Full name <input name="fullName" required></label><label>Email <input type="email" name="email" required></label><label>Role <select name="role"><option>ADMIN</option><option>EXPERT</option><option>TEACHER</option><option>STUDENT</option></select></label><button>Save</button></form>
</main></body></html>
