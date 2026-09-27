<%@ page contentType="text/html;charset=UTF-8" %>
<!doctype html><html><head><title>Student Enrollment Management | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"></head><body><main>
<nav><a href="${pageContext.request.contextPath}/teacher/enrollments">Assigned Class List</a></nav><h1>Student Enrollment Management</h1><table><tr><th>Student</th><th>Email</th><th>Registration</th><th>Enrollment status</th></tr></table>
<h2>Student Import</h2><form action="${pageContext.request.contextPath}/teacher/enrollments" method="post" enctype="multipart/form-data"><input type="hidden" name="action" value="importStudents"><label>Class ID <input type="number" name="classId" required></label><label>CSV file <input type="file" name="file" accept=".csv,text/csv" required></label><button>Import Students</button></form>
</main></body></html>
