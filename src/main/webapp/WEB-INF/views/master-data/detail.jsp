<%@ page contentType="text/html;charset=UTF-8" %>
<!doctype html><html><head><title>Academic Term Details | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"></head><body><main>
<nav><a href="${pageContext.request.contextPath}/admin/master-data">Master Data List</a></nav><h1>Academic Term Details</h1><p>Mode: ${mode}</p>
<form action="${pageContext.request.contextPath}/admin/master-data" method="post"><input type="hidden" name="action" value="saveTerm"><label>Term code <input name="termCode" required></label><label>Name <input name="termName" required></label><label>Start <input type="datetime-local" name="startsAt" required></label><label>End <input type="datetime-local" name="endsAt" required></label><label><input type="checkbox" name="active"> Active</label><button>Save Academic Term</button></form>
</main></body></html>
