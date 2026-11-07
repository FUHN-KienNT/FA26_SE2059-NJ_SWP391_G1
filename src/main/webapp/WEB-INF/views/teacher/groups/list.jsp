<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="vn.edu.fpt.ams.dto.TeacherClassSummary" %>
<%@ page import="vn.edu.fpt.ams.dto.TeacherGroup" %>
<%@ page import="vn.edu.fpt.ams.util.WebSupport" %>
<%
    String context = request.getContextPath();
    TeacherClassSummary selected = (TeacherClassSummary) request.getAttribute("selected");
    Object notice = session.getAttribute("groupNotice");
    session.removeAttribute("groupNotice");
    List<TeacherGroup> groups = (List<TeacherGroup>) request.getAttribute("groups");
%>
<!doctype html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Danh sách nhóm | AMS</title>
    <link rel="stylesheet" href="<%= context %>/css/app.css">
    <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
</head>
<body class="ams-ui teacher-app">
    <%@ include file="/WEB-INF/views/shared/shell.jspf" %>
    <main class="ams-content" id="page-content">
        <nav><a href="<%= context %>/teacher/classes/detail?classId=<%= selected.classId() %>">Chi tiết lớp</a>
            · <a href="<%= context %>/teacher/students?classId=<%= selected.classId() %>">Danh sách sinh viên</a></nav>
        <h1>Danh sách nhóm · <%= WebSupport.html(selected.classCode()) %></h1>
        <% if (notice != null) { %><p role="status"><%= WebSupport.html(notice) %></p><% } %>
        <% if (request.getAttribute("error") != null) { %><p role="alert"><%= WebSupport.html(request.getAttribute("error")) %></p><% } %>
        <form class="ams-panel" method="get" action="<%= context %>/teacher/groups">
            <input type="hidden" name="classId" value="<%= selected.classId() %>">
            <label>Tên nhóm <input name="keyword" maxlength="100" value="<%= WebSupport.html(request.getAttribute("keyword")) %>"></label>
            <label>Trạng thái <select name="status">
                <option value="">Tất cả</option>
                <option value="ACTIVE" <%= "ACTIVE".equals(request.getAttribute("status")) ? "selected" : "" %>>Đang hoạt động</option>
                <option value="INACTIVE" <%= "INACTIVE".equals(request.getAttribute("status")) ? "selected" : "" %>>Ngừng hoạt động</option>
            </select></label>
            <button type="submit">Tìm kiếm</button>
        </form>
        <% if ("ACTIVE".equals(selected.status())) { %>
        <p><a class="ams-button ams-button-primary" href="<%= context %>/teacher/groups/detail?classId=<%= selected.classId() %>">Tạo nhóm</a></p>
        <% } %>
        <% if (groups.isEmpty()) { %>
        <p>Chưa có nhóm phù hợp với bộ lọc.</p>
        <% } else { %>
        <div class="ams-table-wrap"><table>
            <thead><tr><th>Tên nhóm</th><th>Trạng thái</th><th>Thành viên hiện tại</th><th>Thao tác</th></tr></thead>
            <tbody>
            <% for (TeacherGroup group : groups) { %>
            <tr>
                <td><%= WebSupport.html(group.name()) %></td>
                <td><%= "ACTIVE".equals(group.status()) ? "Đang hoạt động" : "Ngừng hoạt động" %></td>
                <td><%= group.memberCount() %></td>
                <td><a href="<%= context %>/teacher/groups/detail?classId=<%= selected.classId() %>&amp;groupId=<%= group.groupId() %>">Xem chi tiết</a></td>
            </tr>
            <% } %>
            </tbody>
        </table></div>
        <% } %>
    </main>
    </div>
</body>
</html>
