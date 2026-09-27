<%@ page contentType="text/html;charset=UTF-8" %>
<!doctype html><html><head><title>Subject Class List | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"></head><body><main>
<nav><a href="${pageContext.request.contextPath}/home">AMS</a></nav><h1>Subject Class List</h1><form action="${pageContext.request.contextPath}/expert/classes" method="get"><label>Subject ID <input type="number" name="subjectId"></label><label>Academic Term ID <input type="number" name="termId"></label><button>Filter</button></form>
<p><a href="${pageContext.request.contextPath}/expert/classes?mode=create">Subject Class Creation</a></p><table><tr><th>Code</th><th>Name</th><th>Capacity / Roster</th><th>Teacher</th><th>Status</th></tr></table>
</main></body></html>
