<%@ page contentType="text/html;charset=UTF-8" import="vn.edu.fpt.ams.dto.StudentResultPage,vn.edu.fpt.ams.util.WebSupport,vn.edu.fpt.ams.util.TeacherSubmissionView,vn.edu.fpt.ams.util.AssignmentListView" %>
<%@ taglib prefix="ams" tagdir="/WEB-INF/tags" %>

<%
    var resultPage = (StudentResultPage) request.getAttribute("resultPage");
    var selected = resultPage.selected();
    var result = resultPage.detail();
    String base = request.getContextPath();
%>

<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Kết quả bài tập | AMS</title>
        <link rel="stylesheet" href="<%=base%>/css/app.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>

        <link rel="stylesheet" href="<%=base%>/css/student-result.css?v=20261108-pagination">
        <link rel="stylesheet" href="<%=base%>/css/submission-diagram.css">
        <script src="<%=base%>/js/submission-diagram.js" defer></script>
    </head>
    <body class="ams-ui student-result">
        <%@ include file="/WEB-INF/views/shared/shell.jspf" %>

        <main id="page-content" class="ams-content result-main">
            <%@ include file="/WEB-INF/views/shared/action-notice.jspf" %>
            <div class="result-page">
                <nav class="result-breadcrumb" aria-label="Đường dẫn">
                    <a href="<%=base%>/student/dashboard">Bảng điều khiển</a><span aria-hidden="true">/</span>
                    <a href="<%=base%>/student/results">Bài làm đã nộp</a>

                    <% if (selected != null) { %>
                        <span aria-hidden="true">/</span><span aria-current="page">Lượt <%=selected.attemptNumber()%></span>
                    <% } %>

                </nav>

                <header class="result-heading">
                    <p class="result-eyebrow">KẾT QUẢ BÀI TẬP</p>
                    <h1><%=result!=null?WebSupport.html(result.title()):selected!=null?WebSupport.html(selected.title()):"Lịch sử bài nộp"%></h1>

                    <% if (selected != null) { %>
                        <p><%=WebSupport.html(selected.classCode())%> · Lượt <%=selected.attemptNumber()%> · Đã nộp <%=WebSupport.html(AssignmentListView.deadline(selected.submittedAt()))%> (UTC+07)</p>
                    <% } %>

                </header>

                <% if (selected != null) { %>
                    <p><a class="ams-button" href="<%=base%>/student/results?assignmentId=<%=selected.assignmentId()%>&amp;submissionId=<%=selected.submissionId()%>&amp;download=1">Xuất bài làm của tôi</a></p>
                <% } %>

                <% if (selected != null && result == null) { %>
                    <section class="result-card result-pending" role="status">
                        <h2>Bài làm đã được ghi nhận</h2>
                        <p>Kết quả chưa được công bố. Điểm, nhận xét và đáp án sẽ hiển thị sau khi giảng viên công bố kết
                            quả.</p>
                    </section>
                <% } %>

                <% if (result != null) { %>
                    <section class="result-card result-summary" aria-labelledby="score-title">
                        <div>
                            <p class="result-eyebrow" id="score-title">ĐIỂM CỦA BẠN</p>
                            <p class="result-score"><strong><%=WebSupport.html(TeacherSubmissionView.score(result.score()))%></strong><span> / <%=WebSupport.html(TeacherSubmissionView.score(result.maximumScore()))%></span></p>
                            <p>Công bố <%=WebSupport.html(AssignmentListView.deadline(selected.releasedAt()))%> (UTC+07)</p>
                        </div>
                        <div class="result-general">
                            <h2>Nhận xét chung</h2>
                            <p class="result-text <%=result.evaluation().feedbackAi()?"result-ai-feedback":""%>"><%=result.feedback()==null||result.feedback().isBlank()?"Chưa có nhận xét chung.":WebSupport.html(result.feedback())%></p>
                            <% if (result.evaluation().feedbackAi()) { %><span class="result-ai-label">Nhận xét do AI viết, đã được giảng viên kiểm tra và công bố.</span><% } %>
                        </div>
                    </section>

                    <% if (result.contentUnavailable()) { %>
                        <p class="result-notice" role="status">Không có bản chụp đề bài hợp lệ cho lượt làm này. Điểm và nhận
                            xét đã công bố vẫn được giữ; phần đối chiếu câu trả lời hiện chưa khả dụng.</p>
                    <% } %>

                    <% if (result.evaluationUnavailable()) { %>
                        <p class="result-notice" role="status">Điểm chi tiết đã lưu không khớp với đề bài hoặc tổng điểm. Vui
                            lòng liên hệ giảng viên để kiểm tra; phần điểm chi tiết chưa được hiển thị.</p>
                    <% } %>

                    <% if (result.instructions() != null && !result.instructions().isBlank()) { %>
                        <details class="result-card">
                            <summary>Hướng dẫn của đề đã làm</summary>
                            <p class="result-text"><%=WebSupport.html(result.instructions())%></p>
                        </details>
                    <% } %>

                    <%
                        if (result.essay() != null) {
                            var essay = result.essay();
                    %>

                        <% if (essay.invalid()) { %>
                            <p class="result-notice">Một phần bài làm cũ không thể đối chiếu với yêu cầu. Nội dung gốc được giữ dưới
                                dạng văn bản.</p>
                        <% } %>

                        <% for (var task : essay.items()) { %>
                            <section class="result-card result-task">
                                <h2>Câu <%=task.order()>0?task.order():"không xác định"%> <% if (task.points() != null) { %>

                                        <span class="result-maximum"><%=WebSupport.html(task.points())%> điểm tối đa</span>
                                    <% } %>

                                </h2>
                                <p class="result-text"><%=WebSupport.html(task.title())%></p>

                                <% if (task.description() != null) { %>
                                    <p class="result-text"><%=WebSupport.html(task.description())%></p>
                                <% } %>

                                <%
                                    for (var requirement : task.requirements()) {
                                        var mark = result.evaluation().mark(task.order(), requirement.order());
                                %>

                                    <article class="result-requirement">
                                        <h3>Yêu cầu <%=requirement.order()%> · <%=WebSupport.html(requirement.points())%> điểm tối đa</h3>
                                        <p class="result-text"><%=WebSupport.html(requirement.title())%></p>
                                        <div class="result-mark">
                                            <p><strong>Điểm: <%=WebSupport.html(TeacherSubmissionView.score(mark==null?null:mark.score()))%> / <%=WebSupport.html(requirement.points())%></strong></p>
                                            <p class="result-text <%=mark!=null&&mark.aiGenerated()?"result-ai-feedback":""%>"><strong>Nhận xét: </strong><%=mark==null||mark.feedback().isBlank()?"Chưa có nhận xét cho yêu cầu này.":WebSupport.html(mark.feedback())%></p>
                                            <% if (mark != null && mark.aiGenerated()) { %><span class="result-ai-label">Nhận xét do AI viết, đã được giảng viên kiểm tra và công bố.</span><% } %>
                                        </div>
                                        <h4>Câu trả lời của bạn</h4>

                                        <% if (requirement.answer() != null && !requirement.answer().isBlank()) { %>
                                            <p class="result-text result-answer"><%=WebSupport.html(requirement.answer())%></p>
                                        <% } %>

                                        <% if (requirement.image() != null && !requirement.image().isBlank()) { %>
                                            <figure class="result-diagram-figure"><img class="result-diagram" src="<%=WebSupport.html(requirement.image())%>"
                                                    alt="Sơ đồ trả lời câu <%=task.order()%>, yêu cầu <%=requirement.order()%>">
                                                <figcaption><button type="button" data-diagram-zoom>Phóng to sơ đồ</button></figcaption>
                                            </figure>
                                        <% } %>

                                        <%
                                            if ((requirement.answer() == null || requirement.answer().isBlank())
                                                    && (requirement.image() == null || requirement.image().isBlank())) {
                                        %>
                                            <p class="result-muted">Không có câu trả lời.</p>
                                        <% } %>

                                    </article>
                                <% } %>

                                <% if (task.requirements().isEmpty() && task.answer() != null) { %>
                                    <h3>Câu trả lời của bạn</h3>
                                    <p class="result-text result-answer"><%=WebSupport.html(task.answer())%></p>
                                <% } %>

                                <%
                                    if (task.requirements().isEmpty()) {
                                        for (var mark : result.evaluation().marks()) {
                                            if (mark.taskOrder() == task.order()) {
                                %>
                                            <div class="result-mark">
                                                <p><strong>Điểm yêu cầu <%=mark.requirementOrder()%>: <%=WebSupport.html(TeacherSubmissionView.score(mark.score()))%></strong></p>
                                                <p class="result-text <%=mark.aiGenerated()?"result-ai-feedback":""%>"><%=mark.feedback().isBlank()?"Chưa có nhận xét cho yêu cầu này.":WebSupport.html(mark.feedback())%></p>
                                                <% if (mark.aiGenerated()) { %><span class="result-ai-label">Nhận xét do AI viết, đã được giảng viên kiểm tra và công bố.</span><% } %>
                                            </div>
                                <%
                                            }
                                        }
                                    }
                                %>

                            </section>
                        <% } %>

                        <% if (essay.legacyContent() != null && !essay.legacyContent().isBlank()) { %>
                            <section class="result-card">
                                <h2>Nội dung bài làm đã lưu</h2>
                                <p class="result-text result-answer"><%=WebSupport.html(essay.legacyContent())%></p>
                            </section>
                        <% } %>

                        <% for (var unmatched : essay.unmatched()) { %>
                            <section class="result-card">
                                <h2><%=WebSupport.html(unmatched.reference())%></h2>
                                <p class="result-text result-answer"><%=WebSupport.html(unmatched.text())%></p>
                            </section>
                        <% } %>
                    <% } %>

                    <% if (result.objective() != null) { %>
                        <p class="result-legend"><span class="result-correct">✓ Đáp án đúng</span><span class="result-wrong">×
                                Lựa chọn sai của bạn</span><span>“Bạn đã chọn” đánh dấu lựa chọn đã nộp.</span></p>

                        <% if (result.objective().invalid()) { %>
                            <p class="result-notice">Một phần dữ liệu câu trả lời cũ không thể đối chiếu. Không đánh dấu đáp án đúng
                                khi đáp án lưu không hợp lệ.</p>
                        <% } %>

                        <% for (var answer : result.objective().items()) { %>
                            <section class="result-card result-task">
                                <h2 class="result-text"><%=WebSupport.html(answer.question())%></h2>
                                <p class="result-question-score"><strong>Điểm đạt: <%=WebSupport.html(answer.awardedPoints())%></strong></p>
                                <ul class="result-options">
                                    <%
                                        for (var choice : answer.choices()) {
                                            boolean correct = Boolean.TRUE.equals(choice.correct());
                                            boolean wrong = choice.selected() && Boolean.FALSE.equals(choice.correct());
                                    %>

                                        <li class="result-option <%=correct?"result-correct":wrong?"result-wrong":""%>"><span class="result-option-icon"
                                                aria-hidden="true"><%=correct?"✓":wrong?"×":"○"%></span><span><strong><%=WebSupport.html(choice.label())%>.</strong>
                                                <%=WebSupport.html(choice.text())%></span>

                                            <% if (correct) { %>
                                                <span class="result-option-state">Đáp án đúng</span>
                                            <% } %>

                                            <% if (wrong) { %>
                                                <span class="result-option-state">Lựa chọn sai</span>
                                            <% } %>

                                            <% if (choice.selected()) { %>
                                                <span class="result-selected">Bạn đã chọn</span>
                                            <% } %>

                                        </li>
                                    <% } %>

                                </ul>

                                <% if (answer.choices().isEmpty()) { %>
                                    <p class="result-text"><%=WebSupport.html(answer.selectedOption())%></p>
                                <% } %>

                            </section>
                        <% } %>
                    <% } %>
                <% } %>

                <section class="result-card" aria-labelledby="attempt-history">
                    <h2 id="attempt-history">Bản nộp cuối cùng</h2>

                    <% if (resultPage.attempts().isEmpty()) { %>
                        <p>Chưa có lượt nộp hợp lệ để xem.</p>
                    <% } else { %>
                        <div class="result-table-wrap">
                            <table class="result-history">
                                <caption class="result-sr-only">Lịch sử nộp bài của bạn</caption>
                                <thead>
                                    <tr>
                                        <th scope="col">Bài tập</th>
                                        <th scope="col">Nộp lúc (UTC+07)</th>
                                        <th scope="col">Kết quả</th>
                                        <th scope="col">Thao tác</th>
                                    </tr>
                                </thead>
                                <tbody>

                                    <% for (var attempt : resultPage.attempts().stream().limit(10).toList()) { %>
                                        <tr>
                                            <th scope="row"><%=WebSupport.html(attempt.title())%><small><%=WebSupport.html(attempt.classCode())%></small></th>
                                            <td><%=WebSupport.html(AssignmentListView.deadline(attempt.submittedAt()))%></td>
                                            <td><%=attempt.released()?"Đã công bố · "+WebSupport.html(TeacherSubmissionView.score(attempt.score()))+" điểm":"Chờ công bố"%></td>
                                            <td><a href="<%=base%>/student/results?assignmentId=<%=attempt.assignmentId()%>&amp;submissionId=<%=attempt.submissionId()%>"
                                                    <%=selected!=null&&selected.submissionId()==attempt.submissionId()?"aria-current=\"page\"":""%>><%=attempt.released()?"Xem kết quả":"Xem trạng thái"%><span class="result-sr-only"> lượt
                                                        <%=attempt.attemptNumber()%></span></a>
                                                · <a href="<%=base%>/student/results?assignmentId=<%=attempt.assignmentId()%>&amp;submissionId=<%=attempt.submissionId()%>&amp;download=1">Xuất bài làm</a></td>
                                        </tr>
                                    <% } %>

                                </tbody>
                            </table>
                        </div>
                        <% if (request.getAttribute("resultCatalogPage") instanceof Integer) {
                            int resultNumber = (Integer) request.getAttribute("resultCatalogPage"); %>
                            <ams:pagination currentPage="<%= resultNumber %>"
                                totalPages="<%= resultPage.catalogPages() %>"
                                baseUrl='<%= base + "/student/results?page=" + resultNumber %>'
                                ariaLabel="Phân trang bài làm" />
                        <% } %>
                    <% } %>

                </section>
            </div>
        </main>
        </div>
    </body>
</html>
