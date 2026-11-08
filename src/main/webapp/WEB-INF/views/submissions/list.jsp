<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="ams" tagdir="/WEB-INF/tags" %>
<%@ page import="vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*" %>
<%@ page import="vn.edu.fpt.ams.util.TeacherSubmissionView" %>
<%@ page import="vn.edu.fpt.ams.util.TeacherSubmissionNavigation" %>
<%@ page import="vn.edu.fpt.ams.util.SubmissionContentSnapshot" %>
<%@ page import="static vn.edu.fpt.ams.util.AssignmentListView.*" %>

<%
    TeacherSubmissionListPage data =
            (TeacherSubmissionListPage) request.getAttribute("submissionPage");
    TeacherSubmissionFilter filters =
            data == null
                    ? (TeacherSubmissionFilter) request.getAttribute("submissionFilters")
                    : data.filter();
    if (filters == null) filters = TeacherSubmissionFilter.from(null, null);
    String error = (String) request.getAttribute("submissionError");
    String base = request.getContextPath();
    Long assignmentId =
            data == null
                    ? (Long) request.getAttribute("selectedAssignmentId")
                    : data.assignment().assignment().assignmentId();
    String backUrl =
            assignmentId == null
                    ? base + "/teacher/assignments"
                    : base + "/teacher/assignments?mode=view&assignmentId=" + assignmentId;
    String retryUrl =
            assignmentId == null
                    ? base + "/teacher/assignments"
                    : TeacherSubmissionNavigation.listUrl(base, assignmentId, filters);
%>

<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Danh sách bài nộp | AMS</title>
        <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-list.css">
        <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-dialog.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>

        <link rel="stylesheet" href="<%= escape(base) %>/css/submissions.css">
    </head>
    <body class="ams-ui teacher-app">
        <%@ include file="/WEB-INF/views/shared/shell.jspf" %>

        <main class="ams-content submission-page" id="page-content">
            <nav class="breadcrumb" aria-label="Đường dẫn">
                <a href="<%= escape(base) %>/teacher/dashboard">Bảng điều khiển</a><span aria-hidden="true">/</span><a
                    href="<%= escape(backUrl) %>">Chi tiết bài tập</a>
                <span aria-hidden="true">/</span><span aria-current="page">Bài nộp</span>
            </nav>

            <div class="page-heading">
                <div>
                    <h1> Bài nộp </h1>
                    <p> Theo dõi bài làm trong lớp: chưa làm, đang làm và đã nộp bài. </p>
                </div>
                <a class="button" href="<%= escape(backUrl) %>">Về chi tiết bài tập</a>
            </div>

            <% if (error != null) { %>
                <section class="notice error" role="alert">
                    <h2> Không thể tải danh sách bài nộp </h2>
                    <p> <%= escape(error) %> </p>
                    <a href="<%= escape(retryUrl) %>">Thử lại</a>
                </section>
            <% } else if (data == null) { %>
                <section class="empty-state">
                    <h2> Danh sách bài nộp không khả dụng </h2>
                    <p> Không thể tải danh sách bài nộp của bài tập này. </p>
                </section>
            <%
                } else {
                    Assignment assignment = data.assignment().assignment();
            %>

                <section class="submission-card assignment-context" aria-labelledby="assignment-context-heading">
                    <div class="context-heading">
                        <h2 id="assignment-context-heading"> <%= escape(assignment.title()) %> </h2>
                        <span class="badge <%= assignment.status().name().toLowerCase(java.util.Locale.ROOT) %>"><%= label(assignment.status()) %></span>
                    </div>
                    <dl class="context-grid">
                        <div>
                            <dt> Lớp </dt>
                            <dd> <%= escape(data.assignment().classCode()) %> — <%= escape(data.assignment().className()) %> </dd>
                        </div>
                        <div>
                            <dt> Mục đích </dt>
                            <dd> <%= label(assignment.assignmentPurpose()) %> </dd>
                        </div>
                        <div>
                            <dt> Hình thức trả lời </dt>
                            <dd> <%= label(assignment.responseType()) %> </dd>
                        </div>
                        <div>
                            <dt> Điểm tối đa </dt>
                            <dd> <%= escape(TeacherSubmissionView.score(assignment.maximumScore())) %> điểm </dd>
                        </div>
                    </dl>
                    <p><strong><%= data.totalStudents() %> <%=data.groupMode()?"nhóm được giao bài":"sinh viên trong danh sách lớp"%></strong>. Thống kê theo bài nộp mới nhất đã tiếp nhận.</p>
                    <div class="actions roster-summary" aria-label="Thống kê trạng thái sinh viên">
                        <%
                            for (String workStatus :
                                    java.util.List.of(
                                            "NOT_STARTED",
                                            "IN_PROGRESS",
                                            "SUBMITTED",
                                            "GRADED",
                                            "RELEASED",
                                            "EXPIRED",
                                            "VOID")) {
                                String workLabel =
                                        "NOT_STARTED".equals(workStatus)
                                                ? "Chưa làm"
                                                : TeacherSubmissionView.statusLabel(
                                                        SubmissionStatus.valueOf(workStatus));
                        %>

                            <span class="roster-count"><%= escape(workLabel) %>: <strong><%= data.studentCounts().getOrDefault(workStatus,0L) %></strong></span>
                        <% } %>

                    </div>
                    <p class="submission-note">Danh sách gồm các <%=data.groupMode()?"nhóm":"sinh viên"%> chưa làm, đang làm và đã nộp. Chỉ bài đã nộp mới mở được nội dung. Mỗi trang tối đa 10 dòng.</p>

                    <% if (assignment.status() == AssignmentStatus.CANCELLED) { %>
                        <p class="submission-note"> Bài tập đã bị hủy. Các lượt làm và kết quả trước đó được giữ để tham khảo.
                        </p>
                    <% } %>

                </section>

                <form class="filters submission-filters" method="get" action="<%= escape(base) %>/teacher/submissions">
                    <input type="hidden" name="assignmentId" value="<%= assignment.assignmentId() %>">
                    <label for="search"> <%=data.groupMode()?"Tìm nhóm hoặc thành viên":"Tìm sinh viên"%> <input
                            type="search"
                            id="search"
                            name="search"
                            maxlength="200"
                            value="<%= escape(filters.search()) %>"
                            placeholder="Tên hoặc email sinh viên…">
                    </label>
                    <label for="status"> Trạng thái <select id="status" name="status">
                            <option value="" <%= filters.statusValue().isEmpty() ? "selected" : "" %>> Tất cả </option>
                            <option value="NOT_STARTED" <%= filters.notStartedOnly() ? "selected" : "" %>> Chưa làm </option>

                            <% for (SubmissionStatus value : SubmissionStatus.values()) { %>
                                <option value="<%= value.name() %>" <%= value == filters.status() ? "selected" : "" %>> <%= escape(TeacherSubmissionView.statusLabel(value)) %> </option>
                            <% } %>

                        </select>
                    </label>
                    <div class="filter-actions">
                        <button type="submit" class="button primary"> Áp dụng bộ lọc </button>
                        <a class="button" href="<%= escape(base) %>/teacher/submissions?assignmentId=<%= assignment.assignmentId() %>">Đặt
                            lại</a>
                    </div>
                </form>

                <div class="table-wrap submission-table">
                    <table>
                        <caption class="sr-only"> Sinh viên và các lượt làm của bài tập <%= escape(assignment.title()) %>, bao gồm sinh viên
                            chưa làm bài. Thời điểm nộp theo giờ Việt Nam, UTC+07. </caption>
                        <thead>
                            <tr>
                                <th scope="col"> <%=data.groupMode()?"Nhóm":"Sinh viên"%> </th>
                                <th scope="col"> Lượt làm </th>
                                <th scope="col"> Nộp lúc <small>(UTC+07)</small>
                                </th>
                                <th scope="col"> Trạng thái </th>
                                <th scope="col"> Điểm </th>
                                <th scope="col"> Thao tác </th>
                            </tr>
                        </thead>
                        <tbody>

                            <%
                                for (TeacherSubmissionRow row : data.submissions()) {
                                    Submission submission = row.submission();
                            %>

                                <tr>
                                    <td class="student-cell">
                                        <strong><%= escape(row.studentName()) %></strong><span><%= escape(row.studentEmail()) %></span>
                                    </td>

                                    <% if (!row.hasSubmission()) { %>
                                        <td> — </td>
                                        <td class="deadline"> Chưa nộp </td>
                                        <td>
                                            <span class="badge submission-status not-started">Chưa làm</span>
                                        </td>
                                        <td class="score-cell"> — </td>
                                        <td>
                                            <span class="submission-unavailable">Chưa có bài làm</span>
                                        </td>
                                    <% } else { %>
                                        <td> <%= submission.attemptNumber() %> </td>
                                        <td class="deadline"> <%= escape(TeacherSubmissionView.submittedAt(submission.submittedAt())) %> </td>
                                        <td>
                                            <span class="badge submission-status <%= submission.status().name().toLowerCase(java.util.Locale.ROOT) %>"><%= escape(TeacherSubmissionView.statusLabel(submission.status())) %></span>
                                        </td>
                                        <td class="score-cell">

                                            <% if (submission.score() == null) { %> <%= row.canViewWork()?"Chưa chấm":"—" %> <% } else { %> <%= escape(TeacherSubmissionView.score(submission.score())) %> /
                                                <%= escape(TeacherSubmissionView.score(SubmissionContentSnapshot.maximumScore(row, assignment.maximumScore()))) %> <% } %>

                                        </td>
                                        <td>

                                            <% if (row.canViewWork()) { %>
                                                <a href="<%= escape(TeacherSubmissionNavigation.detailUrl(base, assignment.assignmentId(), submission.submissionId(), filters)) %>"
                                                    aria-label="Xem bài làm và đánh giá của <%= escape(row.studentName()) %>, lượt làm <%= submission.attemptNumber() %>"><%= assignment.responseType() == ResponseType.ESSAY && submission.status() == SubmissionStatus.SUBMITTED ? "Chấm bài" : assignment.responseType() == ResponseType.ESSAY && submission.status() == SubmissionStatus.GRADED ? "Công bố / xem đánh giá" : "Xem" %></a>
                                            <% } else { %>
                                                <span class="submission-unavailable">Chưa nộp bài</span>
                                            <% } %>

                                        </td>
                                    <% } %>

                                </tr>
                            <%
                                }
                                if (data.submissions().isEmpty()) {
                                    boolean filtered = !filters.search().isBlank() || !filters.statusValue().isEmpty();
                            %>

                                <tr>
                                    <td colspan="6" class="empty-results">
                                        <strong><%= filtered ? "Không có sinh viên hoặc lượt làm phù hợp với bộ lọc." : "Lớp chưa có sinh viên hoặc bài làm được lưu." %></strong>
                                        <p> <%= filtered ? "Thử tên sinh viên hoặc trạng thái khác, hoặc đặt lại bộ lọc." : "Sinh viên sẽ xuất hiện trong danh sách ngay khi được thêm vào lớp." %> </p>
                                    </td>
                                </tr>
                            <% } %>

                        </tbody>
                    </table>
                </div>
                <footer class="results">
                    <p role="status"> Hiển thị <strong><%= data.firstRow() %>–<%= data.lastRow() %></strong> trên
                        <strong><%= data.totalCount() %></strong> dòng </p>
                    <p> Mỗi sinh viên hiển thị bản nộp mới nhất. Bản nháp đang làm tiếp được giữ đến khi nộp thành công. </p>
                </footer>

                <% if (data.totalPages() > 1) { %>
                    <ams:pagination currentPage="<%= filters.page() %>"
                        totalPages="<%= data.totalPages() %>"
                        baseUrl="<%= TeacherSubmissionNavigation.listUrl(base, assignment.assignmentId(), filters) %>"
                        ariaLabel="Các trang bài nộp" />
                <% } %>
            <% } %>

        </main>
        </div>
    </body>
</html>
