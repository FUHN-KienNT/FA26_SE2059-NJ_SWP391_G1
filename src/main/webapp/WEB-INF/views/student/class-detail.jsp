<%@ page contentType="text/html;charset=UTF-8" import="vn.edu.fpt.ams.dto.StudentClassPage,vn.edu.fpt.ams.util.WebSupport,vn.edu.fpt.ams.util.AssignmentListView,vn.edu.fpt.ams.util.StudentClassView,vn.edu.fpt.ams.util.Vietnamese" %>
<%
var classPage = (StudentClassPage) request.getAttribute("classPage");
var classroom = classPage.classroom();
var classFilter = classPage.filter();
String classContext = request.getContextPath();
String[] workOptions = {"NOT_STARTED", "IN_PROGRESS", "SUBMITTED", "GRADED", "RELEASED", "EXPIRED", "VOID"};
%>
<!doctype html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title><%=classroom == null ? "Lớp học của tôi" : WebSupport.html(classroom.classCode()) + " · Chi tiết lớp học"%> | AMS</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
    <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/student-dashboard.css?v=20261021-iter2">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/student-class.css?v=20261103">
</head>
<body class="ams-ui student-dashboard student-class">
<%@ include file="/WEB-INF/views/shared/shell.jspf" %>
<main class="ams-content student-main" id="page-content">
<div class="student-page">
    <nav class="class-breadcrumb" aria-label="Đường dẫn">
        <a href="${pageContext.request.contextPath}/student/dashboard">Bảng điều khiển</a>
        <span aria-hidden="true">›</span><span aria-current="page">Lớp học của tôi</span>
    </nav>
    <header class="student-heading">
        <div><p class="student-eyebrow">LỚP HỌC CỦA TÔI</p>
            <h1><%=classroom == null ? "Lớp học của tôi" : WebSupport.html(classroom.classCode())%></h1>
            <p><%=classroom == null ? "Theo dõi thông tin lớp và bài tập được giao cho bạn." : WebSupport.html(classroom.className())%></p>
        </div>
        <% if (classroom != null) { %>
        <form class="class-switch" method="get" action="${pageContext.request.contextPath}/student/classes">
            <label for="class-picker">Chọn lớp đang tham gia</label>
            <div><select id="class-picker" name="classId">
                <% for (var ownedClass : classPage.classes()) { %>
                <option value="<%=ownedClass.classId()%>" <%=ownedClass.classId() == classroom.classId() ? "selected" : ""%>><%=WebSupport.html(ownedClass.classCode() + " · " + ownedClass.subjectCode() + " · " + ownedClass.termCode())%></option>
                <% } %>
            </select><button class="ams-button" type="submit">Xem lớp</button></div>
        </form>
        <% } %>
    </header>

    <% if (classroom == null) { %>
    <section class="student-panel" aria-labelledby="no-class-title">
        <div class="student-empty"><h2 id="no-class-title">Bạn chưa tham gia lớp nào</h2>
            <p>Lớp học sẽ xuất hiện khi bạn có ghi danh đang hoạt động trong lớp.</p>
            <a class="ams-button ams-button-primary" href="${pageContext.request.contextPath}/student/subjects">Xem và đăng ký môn học</a>
        </div>
    </section>
    <% } else { %>
    <section class="student-panel class-info" aria-labelledby="class-info-title">
        <h2 id="class-info-title">Thông tin lớp học</h2>
        <dl>
            <div><dt>Lớp học</dt><dd><%=WebSupport.html(classroom.classCode())%><small><%=WebSupport.html(classroom.className())%></small></dd></div>
            <div><dt>Môn học</dt><dd><%=WebSupport.html(classroom.subjectCode())%><small><%=WebSupport.html(classroom.subjectName())%></small></dd></div>
            <div><dt>Học kỳ</dt><dd><%=WebSupport.html(classroom.termCode())%><small><%=WebSupport.html(classroom.termName())%></small></dd></div>
            <div><dt>Giảng viên</dt><dd><%=classroom.teacherName() == null || classroom.teacherName().isBlank() ? "Chưa phân công" : WebSupport.html(classroom.teacherName())%></dd></div>
        </dl>
    </section>

    <section class="student-summary class-summary" aria-label="Tổng quan bài tập của lớp">
        <article class="student-summary-card"><h2>Tổng bài tập</h2><strong><%=classPage.counts().total()%></strong><p>Bài đã công bố hoặc đã hủy</p></article>
        <article class="student-summary-card"><h2>Đang mở</h2><strong><%=classPage.counts().open()%></strong><p>Trong thời gian làm bài</p></article>
        <article class="student-summary-card"><h2>Chưa làm</h2><strong><%=classPage.counts().notStarted()%></strong><p>Chưa có lượt làm bài</p></article>
        <article class="student-summary-card"><h2>Đã nộp</h2><strong><%=classPage.counts().submitted()%></strong><p>Bao gồm bài đã được chấm</p></article>
    </section>

    <section class="student-panel class-assignments" aria-labelledby="class-assignments-title">
        <header class="student-panel-heading"><div><h2 id="class-assignments-title">Bài tập trong lớp</h2>
            <p>Trạng thái lượt làm hiển thị lượt mới nhất của bạn. Thời gian theo giờ Việt Nam (UTC+07).</p></div>
            <span class="student-count"><%=classPage.total()%> bài tập</span>
        </header>
        <form class="class-filters" method="get" action="${pageContext.request.contextPath}/student/classes">
            <input type="hidden" name="classId" value="<%=classroom.classId()%>">
            <label for="class-search">Tìm bài tập<input id="class-search" type="search" name="q" maxlength="200" value="<%=WebSupport.html(classFilter.search())%>" placeholder="Nhập tên bài tập"></label>
            <label for="class-response">Hình thức<select id="class-response" name="responseType">
                <option value="">Tất cả hình thức</option>
                <option value="OBJECTIVE" <%= "OBJECTIVE".equals(classFilter.responseType()) ? "selected" : ""%>>Trắc nghiệm</option>
                <option value="ESSAY" <%= "ESSAY".equals(classFilter.responseType()) ? "selected" : ""%>>Tự luận</option>
            </select></label>
            <label for="class-work-status">Trạng thái lượt làm<select id="class-work-status" name="status">
                <option value="">Tất cả trạng thái</option>
                <% for (String workOption : workOptions) { %><option value="<%=WebSupport.html(workOption)%>" <%=workOption.equals(classFilter.status()) ? "selected" : ""%>><%=WebSupport.html(StudentClassView.workLabel(workOption))%></option><% } %>
            </select></label>
            <div class="class-filter-actions"><button class="ams-button ams-button-primary" type="submit">Lọc bài tập</button>
                <a class="class-reset" href="${pageContext.request.contextPath}/student/classes?classId=<%=classroom.classId()%>">Xóa bộ lọc</a></div>
        </form>

        <% if (classPage.counts().total() == 0) { %>
        <div class="student-empty"><h3>Chưa có bài tập trong lớp</h3><p>Bài tập sẽ xuất hiện khi giảng viên công bố cho lớp này.</p></div>
        <% } else if (classPage.assignments().isEmpty()) { %>
        <div class="student-empty"><h3>Không có bài tập phù hợp</h3><p>Thử từ khóa hoặc trạng thái khác để tìm bài tập của bạn.</p>
            <a href="${pageContext.request.contextPath}/student/classes?classId=<%=classroom.classId()%>">Xem tất cả bài tập của lớp</a></div>
        <% } else { %>
        <div class="student-table-wrap class-table-wrap" role="region" tabindex="0" aria-label="Danh sách bài tập, có thể cuộn ngang">
            <table class="class-assignment-table">
                <caption class="class-visually-hidden">Bài tập của lớp <%=WebSupport.html(classroom.classCode())%></caption>
                <thead><tr><th scope="col">Bài tập</th><th scope="col">Hình thức / mục đích</th><th scope="col">Điểm tối đa</th><th scope="col">Thời gian (UTC+07)</th><th scope="col">Trạng thái bài tập</th><th scope="col">Lượt làm của bạn</th><th scope="col">Thao tác</th></tr></thead>
                <tbody>
                <% for (var assignment : classPage.assignments()) { %>
                <tr>
                    <th scope="row" class="class-assignment-title"><%=WebSupport.html(assignment.title())%></th>
                    <td><%=WebSupport.html(Vietnamese.label(assignment.responseType()))%><small><%=WebSupport.html(Vietnamese.label(assignment.purpose()))%></small></td>
                    <td><%=assignment.maximumScore() == null ? "Chưa đặt" : WebSupport.html(assignment.maximumScore().stripTrailingZeros().toPlainString())%></td>
                    <td><span class="class-time-label">Mở:</span> <%=WebSupport.html(AssignmentListView.deadline(assignment.availableFrom()))%><small><span class="class-time-label">Hạn nộp:</span> <%=WebSupport.html(AssignmentListView.deadline(assignment.dueAt()))%></small></td>
                    <td><span class="class-status <%= "CANCELLED".equals(assignment.status()) ? "muted" : "blue"%>"><%=WebSupport.html(Vietnamese.label(assignment.status()))%></span>
                        <% if (!"CANCELLED".equals(assignment.status())) { %><small><%=WebSupport.html(StudentClassView.availability(assignment, classPage.now()))%></small><% } %>
                    </td>
                    <td><span class="class-status <%=WebSupport.html(StudentClassView.workTone(assignment.workStatus()))%>"><%=WebSupport.html(StudentClassView.workLabel(assignment.workStatus()))%></span>
                        <% if (assignment.submissionId() > 0) { %><small>Lượt <%=assignment.attemptNumber()%></small><% } %>
                        <% if (assignment.submittedAt() != null) { %><small>Nộp: <%=WebSupport.html(AssignmentListView.deadline(assignment.submittedAt()))%></small><% } %>
                    </td>
                    <td><% if ("PUBLISHED".equals(assignment.status())) { %>
                        <a class="class-assignment-action" href="<%=WebSupport.html(StudentClassView.assignmentUrl(classContext, classroom.classId(), assignment))%>" aria-label="<%=WebSupport.html(StudentClassView.actionLabel(assignment) + ": " + assignment.title())%>"><%=WebSupport.html(StudentClassView.actionLabel(assignment))%><span aria-hidden="true"> →</span></a>
                        <% } else { %><span class="class-unavailable">Bài tập đã hủy</span><% } %>
                        <% if (assignment.submittedAt()!=null && ("SUBMITTED".equals(assignment.workStatus())||"GRADED".equals(assignment.workStatus())||"RELEASED".equals(assignment.workStatus()))) { %><small><a href="<%=classContext%>/student/results?assignmentId=<%=assignment.assignmentId()%>&amp;submissionId=<%=assignment.submissionId()%>"><%="RELEASED".equals(assignment.workStatus())?"Xem kết quả":"Trạng thái kết quả"%></a> · <a href="<%=classContext%>/student/results?assignmentId=<%=assignment.assignmentId()%>">Lịch sử nộp</a></small><% } %>
                    </td>
                </tr>
                <% } %>
                </tbody>
            </table>
        </div>
        <div class="class-list-footer">
            <p>Trang <%=classPage.page()%> / <%=classPage.totalPages()%> · <%=classPage.total()%> bài tập</p>
            <% if (classPage.totalPages() > 1) { %>
            <nav class="class-pagination" aria-label="Phân trang bài tập">
                <% if (classPage.page() > 1) { %><a href="<%=WebSupport.html(StudentClassView.pageUrl(classContext, classroom.classId(), classFilter, classPage.page() - 1))%>" rel="prev">Trước</a><% } %>
                <% for (int classPageNumber = Math.max(1, classPage.page() - 2); classPageNumber <= Math.min(classPage.totalPages(), classPage.page() + 2); classPageNumber++) { %>
                <% if (classPageNumber == classPage.page()) { %><span aria-current="page"><%=classPageNumber%></span><% } else { %><a href="<%=WebSupport.html(StudentClassView.pageUrl(classContext, classroom.classId(), classFilter, classPageNumber))%>" aria-label="Trang <%=classPageNumber%>"><%=classPageNumber%></a><% } %>
                <% } %>
                <% if (classPage.page() < classPage.totalPages()) { %><a href="<%=WebSupport.html(StudentClassView.pageUrl(classContext, classroom.classId(), classFilter, classPage.page() + 1))%>" rel="next">Sau</a><% } %>
            </nav>
            <% } %>
        </div>
        <p class="student-panel-footnote">Chọn “Xem bài” để xem thông tin và điều kiện làm bài. Lượt làm chỉ bắt đầu khi bạn xác nhận.</p>
        <% } %>
    </section>
    <% } %>
</div>
</main>
</div>
</body>
</html>
