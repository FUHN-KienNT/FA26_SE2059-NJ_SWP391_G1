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
                <%= vn.edu.fpt.ams.util.WebSupport.html(teacher.fullName()) %> · <%= vn.edu.fpt.ams.util.WebSupport.html(teacher.email()) %>
            </p>
            <p class="muted">Tổng quan tất cả học kỳ của các lớp bạn hiện phụ trách.</p>
            <section class="ams-metrics" aria-label="Tổng quan giảng dạy">
                <div class="ams-metric"><span>Lớp phụ trách</span><strong><%= classes.size() %></strong></div>
                <div class="ams-metric"><span>Lượt ghi danh đang học</span><strong><%= rosterCount %></strong></div>
                <div class="ams-metric"><span>Bài tập (tất cả trạng thái)</span><strong><%= assignmentCount %></strong></div>
                <div class="ams-metric"><span>Lượt làm / bài nộp (tất cả trạng thái)</span><strong><%= submissionCount %></strong></div>
            </section>
            <section class="ams-panel">
                <h2>Không gian giảng dạy</h2>
                <p>Chọn lớp để xem sinh viên, quản lý ghi danh hoặc mở danh sách bài tập.</p>
                <a class="ams-button ams-button-primary" href="<%= context %>/teacher/enrollments">Xem lớp phụ trách</a>
            </section>
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
