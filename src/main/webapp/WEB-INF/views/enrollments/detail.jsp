<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="vn.edu.fpt.ams.dto.EnrollmentDTO" %>
<%@ page import="vn.edu.fpt.ams.dto.ImportLineResult" %>
<%@ page import="vn.edu.fpt.ams.dto.ImportOutcome" %>
<%@ page import="vn.edu.fpt.ams.dto.ImportResultDTO" %>
<%@ page import="vn.edu.fpt.ams.dto.TeacherClassSummary" %>
<%@ page import="vn.edu.fpt.ams.util.WebSupport" %>
<%
  String context = request.getContextPath();
  TeacherClassSummary selected = (TeacherClassSummary) request.getAttribute("selected");
  @SuppressWarnings("unchecked")
  List<EnrollmentDTO> students = (List<EnrollmentDTO>) request.getAttribute("students");
  String error = (String) request.getAttribute("error");
  String notice = (String) request.getAttribute("notice");
  ImportResultDTO importResult = (ImportResultDTO) request.getAttribute("importResult");
  String query = request.getAttribute("query") == null ? "" : (String) request.getAttribute("query");
  int studentTotal = request.getAttribute("studentTotal") == null ? students.size() : (Integer) request.getAttribute("studentTotal");
  String openDialog = (String) request.getAttribute("openDialog");
%>
<!doctype html>
<html lang="vi">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Quản lý ghi danh | AMS</title>
  <link rel="stylesheet" href="<%= context %>/css/app.css">
<%@ include file="/WEB-INF/views/shared/assets.jspf" %></head>
<body class="ams-ui teacher-app">
<%@ include file="/WEB-INF/views/shared/shell.jspf" %><main class="ams-content" id="page-content">
  <nav>
    <a href="<%= context %>/teacher/dashboard">Bảng điều khiển</a>
    · <a href="<%= context %>/teacher/enrollments?classId=<%= selected.classId() %>">Sĩ số lớp</a>
  </nav>
  <h1>Quản lý ghi danh</h1>
  <p><%= WebSupport.html(selected.classCode()) %> · <%= WebSupport.html(selected.subjectCode()) %> · <%= selected.activeRoster() %> / <%= selected.capacity() %> đang học · <%= selected.availableSeats() %> chỗ trống</p>
  <% if (notice != null) { %><p role="status"><%= WebSupport.html(notice) %></p><% } %>
  <% if (error != null) { %><p role="alert"><%= WebSupport.html(error) %></p><% } %>
  <% if (importResult != null) { %>
    <section>
      <h2>Kết quả nhập CSV</h2>
      <p>Đã thêm <%= importResult.imported() %>. Bỏ qua <%= importResult.skipped() %>. Từ chối <%= importResult.rejected() %>.</p>
      <% if (importResult.lines().isEmpty()) { %>
        <p>File không có dòng sinh viên.</p>
      <% } else { %>
        <table>
          <tr><th>Dòng</th><th>Email</th><th>Kết quả</th><th>Lý do</th></tr>
          <% for (ImportLineResult line : importResult.lines()) {
               String rowClass = line.outcome() == ImportOutcome.SKIPPED ? "import-skipped"
                       : line.outcome() == ImportOutcome.REJECTED ? "import-rejected" : "";
               String outcome = line.outcome() == ImportOutcome.ADDED ? "Đã thêm"
                       : line.outcome() == ImportOutcome.SKIPPED ? "Bỏ qua" : "Từ chối";
          %>
            <tr class="<%= rowClass %>">
              <td><%= line.line() %></td>
              <td><%= WebSupport.html(line.email()) %></td>
              <td><%= outcome %></td>
              <td><%= WebSupport.html(line.reason()) %></td>
            </tr>
          <% } %>
        </table>
      <% } %>
    </section>
  <% } %>

  <form class="users-toolbar" method="get" action="<%= context %>/teacher/enrollments">
    <input type="hidden" name="classId" value="<%= selected.classId() %>">
    <input type="hidden" name="mode" value="manage">
    <label>Tìm sinh viên <input type="search" name="q" value="<%= WebSupport.html(query) %>" placeholder="Tên, email hoặc trạng thái"></label>
    <button type="submit">Tìm</button>
    <a class="ams-button" href="<%= context %>/teacher/enrollments?classId=<%= selected.classId() %>&amp;mode=manage">Xóa tìm</a>
  </form>
  <table>
    <tr><th>Sinh viên</th><th>Email</th><th>Trạng thái</th><th></th></tr>
    <% for (EnrollmentDTO student : students) { %>
      <tr>
        <td><%= WebSupport.html(student.studentName()) %></td>
        <td><%= WebSupport.html(student.studentEmail()) %></td>
        <td><%= WebSupport.html(student.status().name()) %></td>
        <td>
          <form action="<%= context %>/teacher/enrollments" method="post">
            <input type="hidden" name="action" value="updateStatus">
            <input type="hidden" name="classId" value="<%= selected.classId() %>">
            <input type="hidden" name="enrollmentId" value="<%= student.enrollmentId() %>">
            <input type="hidden" name="status" value="<%= student.status().name().equals("ACTIVE") ? "INACTIVE" : "ACTIVE" %>">
            <button><%= student.status().name().equals("ACTIVE") ? "Ngừng học" : "Kích hoạt lại" %></button>
          </form>
        </td>
      </tr>
    <% } %>
  </table>
  <% if (students.isEmpty()) { %><p><%= query.isBlank() ? "Lớp chưa có sinh viên." : "Không có sinh viên khớp từ khóa." %></p>
  <% } else if (!query.isBlank()) { %><p class="muted">Đang hiện <%= students.size() %> / <%= studentTotal %> sinh viên.</p><% } %>

  <div class="ams-actions">
    <button type="button" id="open-add">Thêm sinh viên</button>
    <button type="button" id="open-import">Nhập CSV</button>
  </div>

  <dialog class="ams-dialog" id="add-dialog">
    <form action="<%= context %>/teacher/enrollments" method="post">
      <h2>Thêm một sinh viên</h2>
      <p class="muted">Dùng cho một tài khoản đã có sẵn. Việc này không dùng file CSV.</p>
      <input type="hidden" name="action" value="addStudent">
      <input type="hidden" name="classId" value="<%= selected.classId() %>">
      <label>Email sinh viên <input type="email" name="email" required></label>
      <menu>
        <button type="button" class="dialog-close">Đóng</button>
        <button type="submit">Thêm sinh viên</button>
      </menu>
    </form>
  </dialog>

  <dialog class="ams-dialog" id="import-dialog">
    <form action="<%= context %>/teacher/enrollments" method="post" enctype="multipart/form-data">
      <h2>Nhập danh sách CSV</h2>
      <p class="muted">Cột đầu là email. Dòng trùng, dòng sai và dòng bị từ chối sẽ được liệt kê sau khi nhập.</p>
      <input type="hidden" name="action" value="importStudents">
      <input type="hidden" name="classId" value="<%= selected.classId() %>">
      <label>File CSV <input type="file" name="file" accept=".csv,text/csv" required></label>
      <menu>
        <button type="button" class="dialog-close">Đóng</button>
        <button type="submit">Nhập sinh viên</button>
      </menu>
    </form>
  </dialog>
</main></div>
<script>
  const addDialog = document.getElementById("add-dialog");
  const importDialog = document.getElementById("import-dialog");
  document.getElementById("open-add").addEventListener("click", () => addDialog.showModal());
  document.getElementById("open-import").addEventListener("click", () => importDialog.showModal());
  document.querySelectorAll(".dialog-close").forEach(button => button.addEventListener("click", () => button.closest("dialog").close()));
  <% if ("add-dialog".equals(openDialog) || "import-dialog".equals(openDialog)) { %>
  document.getElementById("<%= openDialog %>").showModal();
  <% } %>
</script>
</body>
</html>
