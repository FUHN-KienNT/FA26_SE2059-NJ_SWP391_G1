<%@ page contentType="text/html;charset=UTF-8" %>
<!doctype html><html><head><meta charset="UTF-8"><title>Subject Classes | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"></head>
<body><main><nav><a href="${pageContext.request.contextPath}/">AMS</a></nav><h1>Subject Classes</h1>
<p>Classes for Subjects assigned to the current Expert. Data loading is pending.</p>
<form action="${pageContext.request.contextPath}/expert/classes" method="get">
  <label>Subject ID <input type="number" min="1" name="subjectId"></label>
  <label>Academic Term ID <input type="number" min="1" name="termId"></label><button>Filter</button>
</form>
<p><a href="${pageContext.request.contextPath}/expert/create-subject-class.jsp">Create Subject Class</a></p>
<table><tr><th>Code</th><th>Name</th><th>Capacity / Roster</th><th>Teacher</th><th>Status</th><th>Actions</th></tr></table>
<p><a href="${pageContext.request.contextPath}/expert/update-subject-class.jsp">Update class</a> · <a href="${pageContext.request.contextPath}/expert/assign-teacher.jsp">Assign Teacher</a></p>
</main></body></html>
