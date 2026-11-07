<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="vn.edu.fpt.ams.dto.TeacherClassSummary" %>
<%@ page import="vn.edu.fpt.ams.util.WebSupport" %>
<%@ page import="java.util.List" %>
<%
    String context = request.getContextPath();
    List<TeacherClassSummary> classes = (List<TeacherClassSummary>) request.getAttribute("classes");
    List<String> terms = (List<String>) request.getAttribute("terms");
%>
<!doctype html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Danh sách lớp | AMS</title>
    <link rel="stylesheet" href="<%= context %>/css/app.css">
    <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
</head>
<body class="ams-ui teacher-app">
    <%@ include file="/WEB-INF/views/shared/shell.jspf" %>
    <main class="ams-content" id="page-content">
        <nav><a href="<%= context %>/teacher/dashboard">Bảng điều khiển giảng viên</a></nav>
        <h1>Danh sách lớp phụ trách</h1>
        <form method="get" action="<%= context %>/teacher/classes" class="ams-panel" data-live-search>
            <label>Tìm lớp hoặc môn học
                <input name="keyword" maxlength="100" value="<%= WebSupport.html((String) request.getAttribute("keyword")) %>">
            </label>
            <label>Học kỳ
                <select name="term">
                    <option value="">Tất cả học kỳ</option>
                    <% for (String term : terms) { %>
                    <option value="<%= WebSupport.html(term) %>" <%= term.equals(request.getAttribute("term")) ? "selected" : "" %>><%= WebSupport.html(term) %></option>
                    <% } %>
                </select>
            </label>
            <button type="submit">Tìm kiếm</button>
        </form>
        <% if (classes.isEmpty()) { %>
        <p>Không có lớp phù hợp. Thử đổi bộ lọc hoặc kiểm tra phân công lớp.</p>
        <% } else { %>
        <table>
            <thead><tr><th>Mã lớp</th><th>Tên lớp</th><th>Môn học</th><th>Học kỳ</th><th>Sĩ số</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
            <tbody>
            <% for (TeacherClassSummary item : classes) { %>
            <tr>
                <td><%= WebSupport.html(item.classCode()) %></td>
                <td><%= WebSupport.html(item.className()) %></td>
                <td><%= WebSupport.html(item.subjectCode()) %> · <%= WebSupport.html(item.subjectName()) %></td>
                <td><%= WebSupport.html(item.termCode()) %></td>
                <td><%= item.activeRoster() %> / <%= item.capacity() %></td>
                <td><%= "ACTIVE".equals(item.status()) ? "Đang hoạt động" : "Ngừng hoạt động" %></td>
                <td><a href="<%= context %>/teacher/classes/detail?classId=<%= item.classId() %>">Xem chi tiết</a></td>
            </tr>
            <% } %>
            </tbody>
        </table>
        <% } %>
    </main>
    </div>
</body>
</html>
