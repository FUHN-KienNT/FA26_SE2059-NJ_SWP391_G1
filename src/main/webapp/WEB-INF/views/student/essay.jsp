<%@ page contentType="text/html;charset=UTF-8" import="java.util.*,vn.edu.fpt.ams.dto.StudentObjectivePage,vn.edu.fpt.ams.model.SubmissionStatus,vn.edu.fpt.ams.util.*" %>

<%
    var data = (StudentObjectivePage) request.getAttribute("assignmentPage");
    String base = request.getContextPath();
    boolean working = data.status() == SubmissionStatus.IN_PROGRESS;
    boolean open = data.assignment().closedAt() == null
            && data.assignment().availableFrom() != null
            && !data.serverTime().isBefore(data.assignment().availableFrom())
            && data.assignment().dueAt() != null
            && data.serverTime().isBefore(data.assignment().dueAt());
    var tasks =
            working
                    ? EssayQuestions.read(data.assignment().taskChecklistJson())
                    : List.<EssayQuestions.Task>of();
    var answers =
            working
                    ? EssayAnswerCodec.normalize(
                                    data.assignment().taskChecklistJson(), data.essayContent())
                            .answers()
                    : Map.<String, EssayAnswerCodec.Answer>of();
%>

<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Bài tự luận | AMS</title>
        <link rel="stylesheet" href="<%=base%>/css/app.css">
        <link rel="stylesheet" href="<%=base%>/css/common-ui.css">
        <link rel="stylesheet" href="<%=base%>/css/student-objective.css">
        <link rel="stylesheet" href="<%=base%>/css/student-essay.css">
    </head>
    <body class="ams-ui student-objective">
        <%@ include file="../shared/shell.jspf" %>

        <main id="page-content" class="ams-content student-work">
            <%@ include file="/WEB-INF/views/shared/action-notice.jspf" %>
            <div class="student-work-inner">
                <p><a href="<%=base%>/student/dashboard">Bảng điều khiển</a> / <a
                        href="<%=base%>/student/classes?classId=<%=data.assignment().classId()%>">Chi tiết lớp</a> / Bài tự luận
                </p>
                <h1><%=WebSupport.html(data.assignment().title())%></h1>
                <p>Lớp: <%=WebSupport.html(data.classCode())%> · Điểm tối đa: <%=WebSupport.html(data.assignment().maximumScore())%></p>
                <%@ include file="group-context.jspf" %>

                <section class="work-card">
                    <h2>Thông tin bài tập</h2>
                    <% if (data.assignment().instructions() != null && !data.assignment().instructions().isBlank()) { %>
                        <p class="pre-wrap"><%=WebSupport.html(data.assignment().instructions())%></p>
                    <% } %>
                    <p>Mở bài: <%=WebSupport.html(AssignmentListView.deadline(data.assignment().availableFrom()))%> · Hạn nộp: <%=WebSupport.html(AssignmentListView.deadline(data.assignment().dueAt()))%> (UTC+07)</p>
                    <p>Bạn có thể làm và nộp lại trong thời gian mở bài. Hệ thống chỉ giữ bản nộp cuối cùng.
                        Phần bài làm đã lưu được tự nộp khi đến hạn hoặc giảng viên khóa nộp bài.</p>
                </section>

                <% if (data.submissionId() == 0) { %>
                    <p>Nội dung câu hỏi và các yêu cầu sẽ hiển thị sau khi bạn bắt đầu lượt làm trong thời gian mở bài.</p>

                    <% if (open) { %>
                    <form method="post" action="<%=base%>/student/assignments"><input type="hidden" name="csrf"
                            value="<%=WebSupport.html(WebSupport.csrf(request))%>"><input type="hidden" name="assignmentId"
                            value="<%=data.assignment().assignmentId()%>"><button type="submit" name="action" value="start">Bắt đầu / tiếp tục
                            làm bài</button></form>
                    <% } else { %>
                        <p class="work-notice" role="status">Bài tập chưa mở, đã hết hạn hoặc đã khóa nộp bài.</p>
                    <% } %>
                <% } else if (working) { %>
                    <p class="work-notice">Lượt <%=data.attemptNumber()%> · Nộp trước <%=WebSupport.html(AssignmentListView.deadline(data.deadline()))%> (UTC+07). Trả lời riêng từng
                        yêu cầu; sơ đồ được lưu cùng bài làm.</p>
                    <div class="work-timer" role="status">Thời gian còn lại: <strong id="essay-countdown">Đang
                            tải…</strong><span id="essay-save-status" aria-live="polite">Nội dung được tự động lưu khi bạn
                            nhập.</span></div>

                    <form id="essay-work" method="post" action="<%=base%>/student/assignments"
                        data-deadline="<%=data.deadline().toEpochMilli()%>" data-server-time="<%=data.serverTime().toEpochMilli()%>">
                        <input type="hidden" name="csrf" value="<%=WebSupport.html(WebSupport.csrf(request))%>"><input type="hidden" name="assignmentId"
                            value="<%=data.assignment().assignmentId()%>"><input type="hidden" name="submissionId"
                            value="<%=data.submissionId()%>"><input type="hidden" name="answerRevision"
                            value="<%=data.answerRevision()%>"><input type="hidden" name="action" value="submitEssay">

                        <% for (var task : tasks) { %>
                            <section class="work-card essay-task">
                                <div class="essay-task-heading">
                                    <h2>Câu <%=task.order()%></h2><strong><%=task.points().stripTrailingZeros().toPlainString()%> điểm</strong>
                                </div><p class="pre-wrap"><%=WebSupport.html(task.title())%></p>

                                <%
                                    for (var r : task.requirements()) {
                                        var answer = answers.get(EssayAnswerCodec.key(task.order(), r.order()));
                                        String control = "essay-" + task.order() + "-" + r.order();
                                %>

                                    <article class="essay-requirement" data-task="<%=task.order()%>"
                                        data-requirement="<%=r.order()%>" data-answer-type="<%=r.answerType()%>">
                                        <div class="essay-requirement-heading">
                                            <h3>Yêu cầu <%=r.order()%> · <%=r.points().stripTrailingZeros().toPlainString()%> điểm</h3><span><%=r.answerType().equals("TEXT")?"Viết đáp án":r.answerType().equals("DIAGRAM")?"Vẽ sơ đồ":"Viết và vẽ sơ đồ"%></span>
                                        </div><p class="pre-wrap"><%=WebSupport.html(r.title())%></p>

                                        <% if (!r.answerType().equals("DIAGRAM")) { %>
                                            <label for="<%=control%>">Câu trả lời của bạn</label><textarea id="<%=control%>" class="essay-text" name="text_<%=task.order()%>_<%=r.order()%>" rows="6" maxlength="10000"><%=WebSupport.html(answer.text())%></textarea>
                                        <% } %>

                                        <% if (!r.answerType().equals("TEXT")) { %>
                                            <div class="essay-diagram">
                                                <div class="essay-diagram-actions">
                                                    <% if (r.allowedTools().contains("DRAWIO")) { %>
                                                        <button type="button" class="open-diagram">Mở trình vẽ draw.io</button>
                                                    <% } %>
                                                    <% if (r.allowedTools().contains("PLANTUML")) { %>
                                                        <button type="button" class="open-plantuml">Vẽ bằng PlantUML</button>
                                                    <% } %>
                                                    <button type="button" class="remove-diagram"
                                                        <%=answer.image().isBlank()?"hidden":""%>>Xóa sơ đồ</button></div>
                                                <p class="essay-hint">Sử dụng công cụ giảng viên cho phép ở yêu cầu này. Hình được
                                                    tự cắt khoảng trắng và lưu vào đúng yêu cầu này; mở lại cùng trình vẽ để tiếp tục
                                                    sửa. draw.io cần kết nối Internet.</p>
                                                <textarea class="diagram-xml" name="diagram_xml_<%=task.order()%>_<%=r.order()%>" hidden><%=WebSupport.html(answer.xml())%></textarea><textarea class="diagram-plantuml" name="diagram_plantuml_<%=task.order()%>_<%=r.order()%>" hidden><%=WebSupport.html(answer.plantuml())%></textarea><textarea class="diagram-image" name="diagram_image_<%=task.order()%>_<%=r.order()%>" hidden><%=WebSupport.html(answer.image())%></textarea><img
                                                    class="diagram-preview" alt="Sơ đồ trả lời yêu cầu <%=r.order()%>"
                                                    <%=answer.image().isBlank()?"hidden":"src=\""+WebSupport.html(answer.image())+"\""%>>
                                            </div>
                                        <% } %>

                                    </article>
                                <% } %>

                            </section>
                        <% } %>

                        <div class="essay-submit-actions"><button type="button" id="essay-save">Lưu bài làm</button><button
                                type="submit" class="ams-button-primary">Nộp bài</button></div><noscript>
                            <p>JavaScript đang tắt: không có tự lưu, đếm ngược hoặc trình vẽ. Bạn vẫn có thể nhập chữ và
                                nhấn Nộp bài trước hạn.</p>
                        </noscript>
                    </form>

                    <dialog id="diagram-dialog" aria-labelledby="diagram-dialog-title">
                        <div class="diagram-dialog-heading">
                            <h2 id="diagram-dialog-title">Vẽ sơ đồ</h2><button type="button" id="diagram-close"
                                aria-label="Đóng trình vẽ">Đóng</button>
                        </div>
                        <p id="diagram-status" role="status">Đang tải trình vẽ…</p>
                        <div id="diagram-frame"></div>
                    </dialog>

                    <dialog id="plantuml-dialog" aria-labelledby="plantuml-dialog-title">
                        <div class="diagram-dialog-heading">
                            <h2 id="plantuml-dialog-title">Vẽ sơ đồ bằng PlantUML</h2><button type="button"
                                id="plantuml-close">Đóng</button>
                        </div>
                        <p class="essay-hint">Viết mã sơ đồ giữa @startuml và @enduml. Tối đa 20.000 ký tự; không hỗ trợ
                            nhập tệp hoặc liên kết ngoài.</p>
                        <div class="plantuml-workspace">
                            <div><label for="plantuml-source">Mã PlantUML</label><textarea id="plantuml-source" rows="16" maxlength="20000" spellcheck="false" autocomplete="off"></textarea></div>
                            <div class="plantuml-output">
                                <h3>Ảnh xem trước</h3><img id="plantuml-preview" alt="Sơ đồ PlantUML xem trước" hidden>
                            </div>
                        </div>
                        <p id="plantuml-status" role="status" aria-live="polite"></p>
                        <div class="plantuml-actions"><button type="button" id="plantuml-render">Xem trước</button><button
                                type="button" id="plantuml-apply" class="ams-button-primary" disabled>Đưa vào bài
                                làm</button></div>
                    </dialog>
                <% } else { %>
                    <section class="work-card">
                        <h2><%=data.status()==SubmissionStatus.VOID?"Lượt làm đã bị vô hiệu hóa":"Đã nhận bài làm"%></h2>
                        <p>Lượt <%=data.attemptNumber()%>. Nội dung đã được lưu; kết quả sẽ hiển thị sau khi giảng viên công bố.</p>

                        <%
                            if (data.status() == SubmissionStatus.SUBMITTED
                                    || data.status() == SubmissionStatus.GRADED
                                    || data.status() == SubmissionStatus.RELEASED) {
                        %>
                            <p><a
                                    href="<%=base%>/student/results?assignmentId=<%=data.assignment().assignmentId()%>&amp;submissionId=<%=data.submissionId()%>"><%=data.status()==SubmissionStatus.RELEASED?"Xem kết quả":"Xem trạng thái kết quả"%></a>
                                · <a href="<%=base%>/student/results?assignmentId=<%=data.assignment().assignmentId()%>&amp;submissionId=<%=data.submissionId()%>&amp;download=1">Xuất bài làm của tôi</a>
                            </p>
                        <% } %>

                        <% if (open && data.status() != SubmissionStatus.VOID) { %>
                            <form method="post" action="<%=base%>/student/assignments">
                                <input type="hidden" name="csrf" value="<%=WebSupport.html(WebSupport.csrf(request))%>">
                                <input type="hidden" name="assignmentId" value="<%=data.assignment().assignmentId()%>">
                                <button type="submit" name="action" value="start">Làm lại bài</button>
                            </form>
                        <% } %>

                        <a href="<%=base%>/student/classes?classId=<%=data.assignment().classId()%>">Quay về bài tập của lớp</a>
                    </section>
                <% } %>

            </div>
        </main>
        </div><script src="<%=base%>/js/common-ui.js" defer></script><script src="<%=base%>/js/student-essay.js" defer></script>
    </body>
</html>
