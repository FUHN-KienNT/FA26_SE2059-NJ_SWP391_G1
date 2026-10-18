<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="vn.edu.fpt.ams.dto.TeacherClassSummary" %>
<%@ page import="vn.edu.fpt.ams.model.UserAccount" %>
<%
    UserAccount teacher = (UserAccount) request.getAttribute("teacher");
    @SuppressWarnings("unchecked")
    List<TeacherClassSummary> classes = (List<TeacherClassSummary>) request.getAttribute("classes");
    long submissionCount = (Long) request.getAttribute("submissionCount");
    long assignmentCount = (Long) request.getAttribute("assignmentCount");
    long rosterCount = (Long) request.getAttribute("rosterCount");
    String context = request.getContextPath();
%>
<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Bảng điều khiển giảng viên | AMS</title>
        <link rel="stylesheet" href="<%= context %>/css/app.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
        <style>.ams-content table th,.ams-content table td{word-break:normal;overflow-wrap:normal}.ams-content table .class-actions{min-width:150px}.class-actions a{white-space:nowrap}</style>
    </head>
    <body class="ams-ui teacher-app">
        <%@ include file="/WEB-INF/views/shared/shell.jspf" %>
        <main class="ams-content" id="page-content">
            <nav>
                <a href="<%= context %>/home">AMS</a>
    · <a href="<%= context %>/teacher/enrollments">Lớp phụ trách</a>
            </nav>
            <h1>
                Bảng điều khiển giảng viên
            </h1>
            <p>
                <%= teacher.fullName() %> · <%= teacher.email() %>
            </p>
            <p>
                <%= classes.size() %> lớp phụ trách · <%= rosterCount %> sinh viên đang học · <%= assignmentCount %> bài tập · <%= submissionCount %> bài nộp
            </p>
            <table>
                <tr>
                    <th>
                        Lớp
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
                        Lời mời
                    </th>
                    <th>
                        Chỗ còn lại
                    </th>
                    <th>
                        Bài tập
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
                            <a href="<%= context %>/teacher/enrollments?classId=<%= item.classId() %>"><%= item.classCode() %></a>
                            <br>
                            <%= item.className() %>
                        </td>
                        <td>
                            <%= item.subjectCode() %> <%= item.subjectName() %>
                        </td>
                        <td>
                            <%= item.termCode() %>
                        </td>
                        <td>
                            <%= item.activeRoster() %> / <%= item.capacity() %>
                        </td>
                        <td>
                            <%= item.pendingInvitations() %>
                        </td>
                        <td>
                            <%= item.availableSeats() %>
                        </td>
                        <td>
                            <%= item.assignmentCount() %>
                        </td>
                        <td class="class-actions">
                            <a href="<%= context %>/teacher/assignments?classId=<%= item.classId() %>">Xem bài tập</a>
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
                    Bạn chưa được phân công phụ trách lớp nào.
                </p>
                <%
                }
            %>
        </main>
    </div>
</body>
</html>
