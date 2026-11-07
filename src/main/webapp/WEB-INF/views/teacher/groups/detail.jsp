<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="vn.edu.fpt.ams.dto.TeacherClassSummary" %>
<%@ page import="vn.edu.fpt.ams.dto.TeacherGroup" %>
<%@ page import="vn.edu.fpt.ams.dto.EnrollmentDTO" %>
<%@ page import="vn.edu.fpt.ams.util.WebSupport" %>
<%
    String context = request.getContextPath();
    TeacherClassSummary selected = (TeacherClassSummary) request.getAttribute("selected");
    TeacherGroup group = (TeacherGroup) request.getAttribute("group");
    List<EnrollmentDTO> candidates = (List<EnrollmentDTO>) request.getAttribute("candidates");
    String csrf = WebSupport.csrf(request);
    boolean editable = "ACTIVE".equals(selected.status());
    boolean active = group != null && "ACTIVE".equals(group.status()) && editable;
    String name = request.getAttribute("submittedName") == null
            ? (group == null ? "" : group.name()) : (String) request.getAttribute("submittedName");
    String description = request.getAttribute("submittedDescription") == null
            ? (group == null ? "" : group.description()) : (String) request.getAttribute("submittedDescription");
    Object notice = session.getAttribute("groupNotice");
    session.removeAttribute("groupNotice");
%>
<!doctype html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title><%= group == null ? "Tạo nhóm" : "Chi tiết nhóm" %> | AMS</title>
    <link rel="stylesheet" href="<%= context %>/css/app.css">
    <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
</head>
<body class="ams-ui teacher-app">
    <%@ include file="/WEB-INF/views/shared/shell.jspf" %>
    <main class="ams-content" id="page-content">
        <nav><a href="<%= context %>/teacher/classes/detail?classId=<%= selected.classId() %>">Chi tiết lớp</a>
            · <a href="<%= context %>/teacher/groups?classId=<%= selected.classId() %>">Danh sách nhóm</a></nav>
        <h1><%= group == null ? "Tạo nhóm" : "Chi tiết nhóm" %> · <%= WebSupport.html(selected.classCode()) %></h1>
        <% if (notice != null) { %><p role="status"><%= WebSupport.html(notice) %></p><% } %>
        <% if (request.getAttribute("error") != null) { %><p role="alert"><%= WebSupport.html(request.getAttribute("error")) %></p><% } %>
        <form class="ams-panel" action="<%= context %>/teacher/groups/detail" method="post">
            <input type="hidden" name="csrf" value="<%= csrf %>">
            <input type="hidden" name="action" value="save">
            <input type="hidden" name="classId" value="<%= selected.classId() %>">
            <% if (group != null) { %><input type="hidden" name="groupId" value="<%= group.groupId() %>"><% } %>
            <label>Tên nhóm <input name="name" required maxlength="100" value="<%= WebSupport.html(name) %>" <%= editable ? "" : "disabled" %>></label>
            <label>Mô tả <textarea name="description" maxlength="2000" rows="3" <%= editable ? "" : "disabled" %>><%= WebSupport.html(description) %></textarea></label>
            <% if (editable) { %><button type="submit"><%= group == null ? "Tạo nhóm" : "Lưu thông tin" %></button><% } %>
        </form>
        <% if (group != null) { %>
        <p>Trạng thái · <%= "ACTIVE".equals(group.status()) ? "Đang hoạt động" : "Ngừng hoạt động" %></p>
        <% if (editable) { %>
        <form action="<%= context %>/teacher/groups/detail" method="post">
            <input type="hidden" name="csrf" value="<%= csrf %>">
            <input type="hidden" name="action" value="status">
            <input type="hidden" name="classId" value="<%= selected.classId() %>">
            <input type="hidden" name="groupId" value="<%= group.groupId() %>">
            <input type="hidden" name="status" value="<%= active ? "INACTIVE" : "ACTIVE" %>">
            <% if (active) { %><p>Ngừng nhóm sẽ kết thúc các lượt tham gia hiện tại và bỏ phân công trưởng nhóm. Lịch sử được lưu lại.</p><% } %>
            <button type="submit"><%= active ? "Ngừng hoạt động nhóm" : "Kích hoạt lại nhóm" %></button>
        </form>
        <% } %>
        <h2>Thành viên và lịch sử tham gia</h2>
        <% if (group.members().isEmpty()) { %><p>Nhóm chưa có thành viên.</p><% } else { %>
        <div class="ams-table-wrap"><table>
            <thead><tr><th>Sinh viên</th><th>Email</th><th>Vai trò</th><th>Trạng thái</th><th>Tham gia</th><th>Kết thúc</th><th>Thao tác</th></tr></thead>
            <tbody>
            <% for (TeacherGroup.Member member : group.members()) { %>
            <tr>
                <td><%= WebSupport.html(member.name()) %></td>
                <td><%= WebSupport.html(member.email()) %></td>
                <td><%= member.current() && Long.valueOf(member.studentId()).equals(group.leaderStudentId()) ? "Trưởng nhóm" : "Thành viên" %></td>
                <td><%= member.current() ? "Đang tham gia" : "Đã kết thúc hoặc ghi danh không còn hoạt động" %></td>
                <td><%= WebSupport.html(member.joinedAt()) %></td><td><%= WebSupport.html(member.leftAt()) %></td>
                <td>
                <% if (active && member.leftAt() == null) { %>
                    <form action="<%= context %>/teacher/groups/detail" method="post">
                        <input type="hidden" name="csrf" value="<%= csrf %>">
                        <input type="hidden" name="action" value="removeMember">
                        <input type="hidden" name="classId" value="<%= selected.classId() %>">
                        <input type="hidden" name="groupId" value="<%= group.groupId() %>">
                        <input type="hidden" name="studentId" value="<%= member.studentId() %>">
                        <button type="submit">Bỏ khỏi nhóm</button>
                    </form>
                <% } %>
                </td>
            </tr>
            <% } %>
            </tbody>
        </table></div>
        <% } %>
        <% if (active) { %>
        <h2>Thêm thành viên</h2>
        <% if (candidates.isEmpty()) { %><p>Không có sinh viên đang học chưa thuộc nhóm trong lớp.</p><% } else { %>
        <form class="ams-panel" action="<%= context %>/teacher/groups/detail" method="post">
            <input type="hidden" name="csrf" value="<%= csrf %>">
            <input type="hidden" name="action" value="addMember">
            <input type="hidden" name="classId" value="<%= selected.classId() %>">
            <input type="hidden" name="groupId" value="<%= group.groupId() %>">
            <label>Sinh viên <select name="studentId" required>
                <% for (EnrollmentDTO student : candidates) { %>
                <option value="<%= student.studentId() %>"><%= WebSupport.html(student.studentName()) %> · <%= WebSupport.html(student.studentEmail()) %></option>
                <% } %>
            </select></label>
            <button type="submit">Thêm vào nhóm</button>
        </form>
        <% } %>
        <h2>Phân công trưởng nhóm</h2>
        <form class="ams-panel" action="<%= context %>/teacher/groups/detail" method="post">
            <input type="hidden" name="csrf" value="<%= csrf %>">
            <input type="hidden" name="action" value="leader">
            <input type="hidden" name="classId" value="<%= selected.classId() %>">
            <input type="hidden" name="groupId" value="<%= group.groupId() %>">
            <label>Trưởng nhóm <select name="studentId">
                <option value="">Chưa phân công</option>
                <% for (TeacherGroup.Member member : group.members()) { if (member.current()) { %>
                <option value="<%= member.studentId() %>" <%= Long.valueOf(member.studentId()).equals(group.leaderStudentId()) ? "selected" : "" %>><%= WebSupport.html(member.name()) %> · <%= WebSupport.html(member.email()) %></option>
                <% } } %>
            </select></label>
            <button type="submit">Lưu trưởng nhóm</button>
        </form>
        <% } %>
        <% } %>
    </main>
    </div>
</body>
</html>
