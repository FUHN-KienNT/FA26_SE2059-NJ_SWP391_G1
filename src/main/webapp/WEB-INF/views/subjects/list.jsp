<%@ page contentType="text/html;charset=UTF-8" %>
<!doctype html><html><head><title>Subject List | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"></head><body><main>
<nav><a href="${pageContext.request.contextPath}/home">AMS</a></nav><h1>Subject List</h1><form action="${pageContext.request.contextPath}/admin/subjects" method="get"><label>Search <input name="keyword"></label><button>Search</button></form>
<p><a href="${pageContext.request.contextPath}/admin/subjects?mode=create">Subject Creation</a></p><table><tr><th>Code</th><th>Name</th><th>Status</th></tr></table>
</main></body></html>
