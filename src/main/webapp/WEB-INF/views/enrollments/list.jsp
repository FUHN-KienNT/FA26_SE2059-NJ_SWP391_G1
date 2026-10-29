<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="vn.edu.fpt.ams.dto.EnrollmentDTO" %>
<%@ page import="vn.edu.fpt.ams.dto.TeacherClassSummary" %>
<%@ page import="vn.edu.fpt.ams.model.StudentInvitation" %>
<%
    String context = request.getContextPath();
    @SuppressWarnings("unchecked")
    List<TeacherClassSummary> classes = (List<TeacherClassSummary>) request.getAttribute("classes");
    TeacherClassSummary selected = (TeacherClassSummary) request.getAttribute("selected");
    @SuppressWarnings("unchecked")
    List<EnrollmentDTO> students = (List<EnrollmentDTO>) request.getAttribute("students");
    @SuppressWarnings("unchecked")
    List<StudentInvitation> invitations = (List<StudentInvitation>) request.getAttribute("invitations");
%>
<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title><%= selected == null ? "Lớp phụ trách" : "Danh sách sinh viên" %> | AMS</title>
        <link rel="stylesheet" href="<%= context %>/css/app.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
        <link rel="stylesheet" href="<%= context %>/css/teacher-enrollment.css">
        <style>.ams-content table th,.ams-content table td{word-break:normal;overflow-wrap:normal}.ams-content table .class-actions{min-width:150px}.class-actions a{white-space:nowrap}</style>
    </head>
    <body class="ams-ui teacher-app">
        <%@ include file="/WEB-INF/views/shared/shell.jspf" %>
        <main class="ams-content" id="page-content">
            <nav>
                <a href="<%= context %>/home">AMS</a>
    · <a href="<%= context %>/teacher/dashboard">Bảng điều khiển giảng viên</a>
            </nav>
            <h1><%= selected == null ? "Lớp phụ trách" : "Danh sách sinh viên" %></h1>
            <% if (selected == null) { %>
            <form method="get" class="ams-panel" data-live-search>
                <label>Tìm lớp hoặc môn học <input name="keyword" maxlength="100" value="<%= vn.edu.fpt.ams.util.WebSupport.html((String) request.getAttribute("keyword")) %>"></label>
                <label>Học kỳ <select name="term"><option value="">Tất cả học kỳ</option>
                <% for (String term : (List<String>) request.getAttribute("terms")) { %>
                    <option value="<%= vn.edu.fpt.ams.util.WebSupport.html(term) %>" <%= term.equals(request.getAttribute("term")) ? "selected" : "" %>><%= vn.edu.fpt.ams.util.WebSupport.html(term) %></option>
                <% } %></select></label>
                <button type="submit">Tìm kiếm</button>
            </form>
            <table>
                <tr>
                    <th>
                        Mã lớp
                    </th>
                    <th>
                        Môn học
                    </th>
                    <th>
                        Học kỳ
                    </th>
                    <th>
                        Sĩ số
                    </th>
                    <th>
                        Chỗ còn lại
                    </th>
                    <th>
                        Thao tác
                    </th>
                </tr>
                <%
                    for (TeacherClassSummary item : classes) {
                    %>
                    <tr>
                        <td>
                            <a href="<%= context %>/teacher/enrollments?classId=<%= item.classId() %>&amp;<%= request.getAttribute("listQuery") %>"><%= vn.edu.fpt.ams.util.WebSupport.html(item.classCode()) %></a>
                        </td>
                        <td>
                            <%= vn.edu.fpt.ams.util.WebSupport.html(item.subjectCode()) %> <%= vn.edu.fpt.ams.util.WebSupport.html(item.subjectName()) %>
                        </td>
                        <td>
                            <%= vn.edu.fpt.ams.util.WebSupport.html(item.termCode()) %>
                        </td>
                        <td>
                            <%= item.activeRoster() %> / <%= item.capacity() %>
                        </td>
                        <td>
                            <%= item.availableSeats() %>
                        </td>
                        <td class="class-actions">
                            <a href="<%= context %>/teacher/assignments?classId=<%= item.classId() %>">Xem bài tập</a>
                            <br>
                            <a href="<%= context %>/teacher/enrollments?classId=<%= item.classId() %>&amp;mode=manage">Quản lý sinh viên</a>
                        </td>
                    </tr>
                    <%
                    }
                %>
            </table>
            <%
                if (classes.isEmpty()) {
                %>
                <p>
                    Không có lớp phù hợp. Thử đổi bộ lọc hoặc kiểm tra phân công lớp.
                </p>
                <%
                }
            %>
            <% } %>
            <%
                if (selected != null) {
                %>
                <p><a class="ams-button" href="<%= context %>/teacher/enrollments?<%= request.getAttribute("listQuery") %>">Quay lại danh sách lớp</a>
                <a class="ams-button" href="<%= context %>/teacher/enrollments?classId=<%= selected.classId() %>&amp;mode=manage">Quản lý ghi danh</a></p>
                <h2>
                    Danh sách sinh viên · <%= vn.edu.fpt.ams.util.WebSupport.html(selected.classCode()) %>
                </h2>
                <p>
                    <%= selected.activeRoster() %> sinh viên đang học, <%= selected.pendingInvitations() %> lời mời đang chờ, <%= selected.availableSeats() %> chỗ còn lại.
                </p>
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
                        </tr>
                        <%
                        }
                    %>
                </table>
                <%
                    if (students.isEmpty()) {
                    %>
                    <p>
                        Lớp này chưa có sinh viên.
                    </p>
                    <%
                    }
                %>
                <h2>
                    Lời mời sinh viên
                </h2>
                <table>
                    <tr>
                        <th>
                            Họ tên
                        </th>
                        <th>
                            Email
                        </th>
                        <th>
                            Trạng thái
                        </th>
                        <th>
                            Hết hạn
                        </th>
                    </tr>
                    <%
                        for (StudentInvitation invitation : invitations) {
                        %>
                        <tr>
                            <td>
                                <%= vn.edu.fpt.ams.util.WebSupport.html(invitation.fullName()) %>
                            </td>
                            <td>
                                <%= vn.edu.fpt.ams.util.WebSupport.html(invitation.email()) %>
                            </td>
                            <td>
                                <%= vn.edu.fpt.ams.util.WebSupport.html(vn.edu.fpt.ams.util.Vietnamese.label(invitation.status())) %>
                            </td>
                            <td>
                                <%= vn.edu.fpt.ams.util.AssignmentListView.deadline(invitation.expiresAt()) %>
                            </td>
                        </tr>
                        <%
                        }
                    %>
                </table>
                <%
                    if (invitations.isEmpty()) {
                    %>
                    <p>
                        Lớp này chưa có lời mời.
                    </p>
                    <%
                    }
                %>
                <%
                }
            %>
        </main>
    </div>
</body>
</html>
