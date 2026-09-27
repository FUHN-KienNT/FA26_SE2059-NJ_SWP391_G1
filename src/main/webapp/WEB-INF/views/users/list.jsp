<%@ page contentType="text/html;charset=UTF-8" %>
<!doctype html><html><head><title>User List | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"></head><body><main>
<nav><a href="${pageContext.request.contextPath}/home">AMS</a></nav><h1>User List</h1>
<form action="${pageContext.request.contextPath}/admin/users" method="get"><label>Search <input name="keyword"></label><button>Search</button></form>
<p><a href="${pageContext.request.contextPath}/admin/users?mode=create">User Creation</a></p><table><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th></tr></table>
</main></body></html>
