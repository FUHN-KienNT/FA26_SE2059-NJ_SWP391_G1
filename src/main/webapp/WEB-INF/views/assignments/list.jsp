<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*" %>
<%@ page import="vn.edu.fpt.ams.util.AssignmentDetailView" %>
<%@ page import="static vn.edu.fpt.ams.util.AssignmentListView.*" %>
<%
    AssignmentListPage data = (AssignmentListPage) request.getAttribute("assignmentPage");
    String error = (String) request.getAttribute("assignmentListError");
    String base = request.getContextPath();
    String listUrl = base + "/teacher/assignments";
    Long classId = data == null ? null : data.selectedClassId();
    AssignmentListFilter filters = data == null ? AssignmentListFilter.from(null, null, null, null) : data.filter();
%>
<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Bài tập | AMS</title>
        <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-list.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
    </head>
    <body class="ams-ui teacher-app">
        <%@ include file="/WEB-INF/views/shared/shell.jspf" %>
        <main class="ams-content" id="page-content">
            <nav class="breadcrumb" aria-label="Đường dẫn">
                <a href="<%= escape(base) %>/teacher/dashboard">Bảng điều khiển</a><span aria-hidden="true">/</span><span>Bài tập</span>
            </nav>
            <div class="page-heading">
                <div>
                    <h1>
                        Bài tập
                    </h1>
                    <%
                        if (data != null && classId != null) {
                            for (AssignmentClassOption option : data.classes()) {
                                if (option.classId() == classId) {
                                %>
                                <p>
                                    Lớp <%= escape(option.classCode()) %> — <%= escape(option.className()) %>
                                </p>
                                <%
                                }
                            }
                        }
                    %>
                    <a href="<%= escape(base) %>/teacher/dashboard">Quay về danh sách lớp</a>
                </div>
                <%
                    if (classId != null) {
                    %>
                    <a class="button primary" href="<%= escape(listUrl) %>?mode=create&amp;classId=<%= classId %>">Tạo bài tập</a>
                    <%
                    }
                %>
            </div>
            <%
                if (error != null) {
                %>
                <section class="notice error" role="alert">
                    <h2>
                        Không thể tải bài tập
                    </h2>
                    <p>
                        <%= escape(error) %>
                    </p>
                    <a href="">Thử lại</a>
                </section>
                <%
                }
                else if (data == null || data.classes().isEmpty()) {
                %>
                <section class="empty-state">
                    <h2>
                        Chưa được phân công lớp
                    </h2>
                    <p>
                        Bài tập sẽ hiển thị khi bạn được phân công phụ trách lớp.
                    </p>
                </section>
                <%
                }
                else {
                %>
                <form class="filters" method="get" action="<%= escape(listUrl) %>">
                    <input type="hidden" name="classId" value="<%= classId %>">
                    <div class="search-row">
                        <label class="search-label" for="search">
                            Tìm theo tiêu đề
                            <input
                                type="search"
                                id="search"
                                name="search"
                                maxlength="200"
                                placeholder="Nhập tiêu đề bài tập…"
                                value="<%= escape(filters.search()) %>">
                        </label>
                        <button class="button primary" type="submit">
                            Áp dụng bộ lọc
                        </button>
                        <a class="button" href="<%= escape(listUrl) %>?classId=<%= classId %>">Đặt lại</a>
                    </div>
                    <div class="filter-grid">
                        <label for="purpose">
                            Mục đích
                            <select id="purpose" name="purpose">
                                <option value="">
                                    Tất cả
                                </option>
                                <%
                                    for (AssignmentPurpose value : AssignmentPurpose.values()) {
                                    %>
                                    <option value="<%= value.name() %>" <%= value == filters.purpose() ? "selected" : "" %>>
                                        <%= label(value) %>
                                    </option>
                                    <%
                                    }
                                %>
                            </select>
                        </label>
                        <label for="responseType">
                            Hình thức trả lời
                            <select id="responseType" name="responseType">
                                <option value="">
                                    Tất cả
                                </option>
                                <%
                                    for (ResponseType value : ResponseType.values()) {
                                    %>
                                    <option value="<%= value.name() %>" <%= value == filters.responseType() ? "selected" : "" %>>
                                        <%= label(value) %>
                                    </option>
                                    <%
                                    }
                                %>
                            </select>
                        </label>
                        <label for="status">
                            Trạng thái
                            <select id="status" name="status">
                                <option value="">
                                    Tất cả
                                </option>
                                <%
                                    for (AssignmentStatus value : AssignmentStatus.values()) {
                                    %>
                                    <option value="<%= value.name() %>" <%= value == filters.status() ? "selected" : "" %>>
                                        <%= label(value) %>
                                    </option>
                                    <%
                                    }
                                %>
                            </select>
                        </label>
                    </div>
                </form>
                <div class="table-wrap">
                    <table>
                        <caption class="sr-only">
                            Bài tập của lớp đã chọn. Thời hạn hiển thị theo giờ Việt Nam, UTC+07:00.
                        </caption>
                        <thead>
                            <tr>
                                <th scope="col">
                                    Tiêu đề bài tập
                                </th>
                                <th scope="col">
                                    Mục đích
                                </th>
                                <th scope="col">
                                    Hình thức trả lời
                                </th>
                                <th scope="col">
                                    Hạn nộp <small>(UTC+07)</small>
                                </th>
                                <th scope="col">
                                    Trạng thái
                                </th>
                                <th scope="col">
                                    Thao tác
                                </th>
                            </tr>
                        </thead>
                        <tbody>
                            <%
                                for (Assignment assignment : data.assignments()) {
                                    String actionUrl = AssignmentDetailView.listUrl(base, classId, filters) + "&assignmentId=" + assignment.assignmentId();
                                %>
                                <tr>
                                    <td class="assignment-title">
                                        <%= escape(assignment.title()) %>
                                    </td>
                                    <td>
                                        <%= label(assignment.assignmentPurpose()) %>
                                    </td>
                                    <td>
                                        <%= label(assignment.responseType()) %>
                                    </td>
                                    <td class="deadline">
                                        <%= deadline(assignment.dueAt()) %>
                                    </td>
                                    <td>
                                        <span class="badge <%= assignment.status().name().toLowerCase(java.util.Locale.ROOT) %>"><%= label(assignment.status()) %></span>
                                    </td>
                                    <td>
                                        <div class="actions">
                                            <a href="<%= escape(actionUrl) %>&amp;mode=view" aria-label="Xem <%= escape(assignment.title()) %>">Xem</a>
                                            <%
                                                if (assignment.status() == AssignmentStatus.DRAFT) {
                                                %>
                                                <a href="<%= escape(actionUrl) %>&amp;mode=edit" aria-label="Sửa <%= escape(assignment.title()) %>">Sửa</a>
                                                <%
                                                }
                                            %>
                                            <%
                                                if (assignment.status() != AssignmentStatus.CANCELLED || data.submissionAssignmentIds().contains(assignment.assignmentId())) {
                                                %>
                                                <details class="more-actions">
                                                    <summary aria-label="Thao tác khác cho <%= escape(assignment.title()) %>">
                                                        Thêm
                                                    </summary>
                                                    <div class="action-menu">
                                                        <%
                                                            if (assignment.status() == AssignmentStatus.DRAFT) {
                                                            %>
                                                            <a href="<%= escape(actionUrl) %>&amp;mode=publish">Công bố bài tập</a>
                                                            <%
                                                            }
                                                        %>
                                                        <%
                                                            if (assignment.status() != AssignmentStatus.CANCELLED) {
                                                            %>
                                                            <a href="<%= escape(actionUrl) %>&amp;mode=cancel">Hủy bài tập</a>
                                                            <%
                                                            }
                                                        %>
                                                        <%
                                                            if (data.submissionAssignmentIds().contains(assignment.assignmentId())) {
                                                            %>
                                                            <a href="<%= escape(base) %>/teacher/submissions?assignmentId=<%= assignment.assignmentId() %>">Xem bài nộp</a>
                                                            <%
                                                            }
                                                        %>
                                                    </div>
                                                </details>
                                                <%
                                                }
                                            %>
                                        </div>
                                    </td>
                                </tr>
                                <%
                                }
                                if (data.assignments().isEmpty()) {
                                %>
                                <tr>
                                    <td colspan="6" class="empty-results">
                                        Không có bài tập phù hợp với các bộ lọc trong lớp này.
                                    </td>
                                </tr>
                                <%
                                }
                            %>
                        </tbody>
                    </table>
                </div>
                <footer class="results">
                    <p role="status">
                        Đang hiển thị <strong><%= data.assignments().size() %></strong> bài tập
                    </p>
                    <p>
                        Chỉ có thể chỉnh sửa bài tập ở trạng thái bản nháp.
                    </p>
                </footer>
                <%
                }
            %>
        </main>
    </div>
</body>
</html>
