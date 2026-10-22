<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="vn.edu.fpt.ams.dto.EnrollmentDTO" %>
<%@ page import="vn.edu.fpt.ams.dto.TeacherClassSummary" %>
<%
    String context = request.getContextPath();
    TeacherClassSummary selected = (TeacherClassSummary) request.getAttribute("selected");
    @SuppressWarnings("unchecked")
    List<EnrollmentDTO> students = (List<EnrollmentDTO>) request.getAttribute("students");
    String error = (String) request.getAttribute("error");
    String notice = (String) request.getAttribute("notice");
%>
<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Quản lý sinh viên trong lớp | AMS</title>
        <link rel="stylesheet" href="<%= context %>/css/app.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
        <link rel="stylesheet" href="<%= context %>/css/teacher-enrollment.css">
    </head>
    <body class="ams-ui teacher-app">
        <%@ include file="/WEB-INF/views/shared/shell.jspf" %>
        <main class="ams-content" id="page-content">
            <nav>
                <a href="<%= context %>/teacher/dashboard">Bảng điều khiển giảng viên</a>
    ·
                <a href="<%= context %>/teacher/enrollments?classId=<%= selected.classId() %>">Danh sách sinh viên</a>
    ·
                <a href="<%= context %>/teacher/assignments?classId=<%= selected.classId() %>">Xem bài tập</a>
            </nav>
            <h1>
                Quản lý sinh viên trong lớp
            </h1>
            <p>
                <%= vn.edu.fpt.ams.util.WebSupport.html(selected.classCode()) %> · <%= vn.edu.fpt.ams.util.WebSupport.html(selected.subjectCode()) %> · <%= selected.activeRoster() %> / <%= selected.capacity() %> đang học · <%= selected.availableSeats() %> chỗ còn lại
            </p>
            <%
                if (notice != null) {
                %>
                <p role="status">
                    <%= vn.edu.fpt.ams.util.WebSupport.html(notice) %>
                </p>
                <%
                }
            %>
            <%
                if (error != null) {
                %>
                <p role="alert">
                    <%= vn.edu.fpt.ams.util.WebSupport.html(error) %>
                </p>
                <%
                }
            %>
            <% if (students.isEmpty()) { %><p>Lớp này chưa có sinh viên.</p><% } %>
            <table>
                <tr>
                    <th>
                        Sinh viên
                    </th>
                    <th>
                        Email
                    </th>
                    <th>
                        Trạng thái
                    </th>
                    <th>
                    </th>
                </tr>
                <%
                    for (EnrollmentDTO student : students) {
                    %>
                    <tr>
                        <td>
                            <%= vn.edu.fpt.ams.util.WebSupport.html(student.studentName()) %>
                        </td>
                        <td>
                            <%= vn.edu.fpt.ams.util.WebSupport.html(student.studentEmail()) %>
                        </td>
                        <td>
                            <%= vn.edu.fpt.ams.util.WebSupport.html(vn.edu.fpt.ams.util.Vietnamese.label(student.status())) %>
                        </td>
                        <td>
                            <form action="<%= context %>/teacher/enrollments" method="post">
                                <input type="hidden" name="action" value="updateStatus">
                                <input type="hidden" name="classId" value="<%= selected.classId() %>">
                                <input type="hidden" name="enrollmentId" value="<%= student.enrollmentId() %>">
                                <input
                                    type="hidden"
                                    name="status"
                                    value="<%= student.status().name().equals("ACTIVE") ? "INACTIVE" : "ACTIVE" %>">
                                <button>
                                    <%= student.status().name().equals("ACTIVE") ? "Ngừng hoạt động" : "Kích hoạt" %>
                                </button>
                            </form>
                        </td>
                    </tr>
                    <%
                    }
                %>
            </table>
            <div class="teacher-actions">
                <button type="button" data-open-dialog="add-student">Thêm sinh viên</button>
                <button type="button" data-open-dialog="import-students">Nhập CSV</button>
            </div>
            <dialog id="add-student" aria-labelledby="add-student-title" <%= "addStudent".equals(request.getAttribute("openDialog")) ? "data-reopen" : "" %>>
            <h2 id="add-student-title">Thêm sinh viên đã có tài khoản</h2>
            <% if (error != null && "addStudent".equals(request.getAttribute("openDialog"))) { %><p role="alert"><%= vn.edu.fpt.ams.util.WebSupport.html(error) %></p><% } %>
            <form action="<%= context %>/teacher/enrollments" method="post">
                <input type="hidden" name="action" value="addStudent">
                <input type="hidden" name="classId" value="<%= selected.classId() %>">
                <label>
                    Email sinh viên
                    <input type="email" name="email" required value="<%= vn.edu.fpt.ams.util.WebSupport.html((String) request.getAttribute("submittedEmail")) %>">
                </label>
                <button>
                    Thêm sinh viên
                </button>
            </form>
            <button type="button" data-close-dialog>Đóng</button>
            </dialog>
            <dialog id="import-students" aria-labelledby="import-students-title" <%= "importStudents".equals(request.getAttribute("openDialog")) ? "data-reopen" : "" %>>
            <h2 id="import-students-title">Nhập danh sách sinh viên</h2>
            <% if (error != null && "importStudents".equals(request.getAttribute("openDialog"))) { %><p role="alert"><%= vn.edu.fpt.ams.util.WebSupport.html(error) %></p><% } %>
            <p>Tệp CSV UTF-8, email ở cột đầu tiên; có thể có dòng tiêu đề email. Chức năng hiện tại chỉ thêm tài khoản sinh viên đã tồn tại.</p>
            <form action="<%= context %>/teacher/enrollments" method="post" enctype="multipart/form-data">
                <input type="hidden" name="action" value="importStudents">
                <input type="hidden" name="classId" value="<%= selected.classId() %>">
                <label>
                    Tệp CSV
                    <input type="file" name="file" accept=".csv,text/csv" required>
                </label>
                <button>
                    Nhập danh sách sinh viên
                </button>
            </form>
            <button type="button" data-close-dialog>Đóng</button>
            </dialog>
            <script src="<%= context %>/js/teacher-enrollment.js" defer></script>
        </main>
    </div>
</body>
</html>
