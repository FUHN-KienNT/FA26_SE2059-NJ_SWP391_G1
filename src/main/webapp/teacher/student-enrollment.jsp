<%@ page contentType="text/html;charset=UTF-8" %>
<!doctype html><html><head><meta charset="UTF-8"><title>Student Enrollment | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"></head>
<body><main><nav><a href="${pageContext.request.contextPath}/teacher/enrollments">Assigned Classes</a></nav><h1>Student Enrollment</h1>
<p>Roster writes and CSV parsing are pending in this initial iteration.</p>
<h2>Add existing Student</h2><form action="${pageContext.request.contextPath}/teacher/enrollments" method="post"><input type="hidden" name="action" value="addStudent">
<label>Class ID <input type="number" name="classId" min="1" required></label><label>Student ID <input type="number" name="studentId" min="1" required></label><button>Add Student</button></form>
<h2>Import Students</h2><form action="${pageContext.request.contextPath}/teacher/enrollments" method="post" enctype="multipart/form-data"><input type="hidden" name="action" value="importStudents">
<label>Class ID <input type="number" name="classId" min="1" required></label><label>CSV file <input type="file" name="file" accept=".csv,text/csv" required></label><button>Import</button></form>
<h2>Update enrollment status</h2><form action="${pageContext.request.contextPath}/teacher/enrollments" method="post"><input type="hidden" name="action" value="updateStatus">
<label>Class ID <input type="number" name="classId" min="1" required></label><label>Enrollment ID <input type="number" name="enrollmentId" min="1" required></label>
<label>Status <select name="status"><option>ACTIVE</option><option>INACTIVE</option></select></label><button>Update status</button></form>
</main></body></html>
