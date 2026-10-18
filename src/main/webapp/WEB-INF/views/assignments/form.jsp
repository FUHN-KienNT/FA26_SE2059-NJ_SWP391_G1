<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.*,com.google.gson.Gson,vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.util.WebSupport" %>
<%@ page import="static vn.edu.fpt.ams.util.WebSupport.html" %>
<%
    AssignmentCreatePage context = (AssignmentCreatePage) request.getAttribute("createPage");
    AssignmentForm form = (AssignmentForm) request.getAttribute("assignmentForm");
    if (form == null) form = AssignmentForm.empty(0);
    Map<String,String> errors = (Map<String,String>) request.getAttribute("formErrors");
    if (errors == null) errors = Map.of();
    String createError = (String) request.getAttribute("createError");
    boolean editing = Boolean.TRUE.equals(request.getAttribute("assignmentEdit"));
    boolean editConflict = Boolean.TRUE.equals(request.getAttribute("editConflict"));
    String pageTitle = editing ? "Chỉnh sửa bài tập" : "Tạo bài tập";
    String savedResponseType = (String) request.getAttribute("savedResponseType");
    if (savedResponseType == null) savedResponseType = form.value("responseType");
    String savedEssayContent = request.getAttribute("savedEssayContent") == null ? ""
    : Boolean.toString(Boolean.TRUE.equals(request.getAttribute("savedEssayContent")));
    String savedObjectiveContent = request.getAttribute("savedObjectiveContent") == null ? ""
    : Boolean.toString(Boolean.TRUE.equals(request.getAttribute("savedObjectiveContent")));
    String base = request.getContextPath();
    String back = base + "/teacher/assignments" + (context == null ? "" : "?classId=" + context.classId());
    String reload = base + "/teacher/assignments?mode=edit&assignmentId=" + form.value("assignmentId");
%>
<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title><%= pageTitle %> | AMS</title>
        <link rel="stylesheet" href="<%= html(base) %>/css/assignment-list.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
        <link rel="stylesheet" href="<%= html(base) %>/css/assignment-form.css">
        <script src="<%= html(base) %>/js/assignment-form.js" defer></script>
    </head>
    <body class="ams-ui teacher-app">
        <%@ include file="/WEB-INF/views/shared/shell.jspf" %>
        <main class="ams-content assignment-create" id="page-content">
            <nav class="breadcrumb" aria-label="Đường dẫn">
                <a href="<%= html(base) %>/teacher/dashboard">Bảng điều khiển</a><span>/</span><a href="<%= html(back) %>">Bài tập</a><span>/</span><span><%= editing ? "Chỉnh sửa" : "Tạo mới" %></span>
            </nav>
            <div class="page-heading">
                <div>
                    <h1>
                        <%= pageTitle %>
                    </h1>
                    <p>
                        <%= editing ? "Chỉnh sửa bản nháp bài tập, sau đó lưu thay đổi." : "Chọn mẫu, nhập nội dung bài tập rồi lưu bản nháp." %>
                    </p>
                </div>
                <span class="badge draft"><%= editing ? "Bản nháp" : "Bản nháp mới" %></span>
            </div>
            <%
                if (createError != null) {
                %>
                <div class="notice error" role="alert">
                    <p>
                        <%= html(createError) %>
                    </p>
                    <%
                        if(context == null){
                        %>
                        <a href="">Thử lại</a>
                        <%
                        }
                    %>
                </div>
                <%
                }
            %>
            <%
                if (editConflict) {
                %>
                <div class="notice error edit-conflict" role="alert">
                    <h2>
                        Bản nháp đã được thay đổi
                    </h2>
                    <p>
                        Nội dung bạn vừa nhập vẫn được giữ lại. Hãy tải bản nháp mới nhất trước khi lưu.
                    </p>
                    <a class="button leave-form" href="<%= html(reload) %>">Tải bản nháp mới nhất</a>
                </div>
                <%
                }
            %>
            <%
                if (!errors.isEmpty()) {
                %>
                <div class="notice error" role="alert" tabindex="-1" id="form-errors">
                    <h2>
                        Vui lòng kiểm tra dữ liệu đã nhập
                    </h2>
                    <ul>
                        <%
                            for(var entry : errors.entrySet()) {
                            %>
                            <li>
                                <a href="#<%= html(entry.getKey()) %>-error"><%= html(entry.getValue()) %></a>
                            </li>
                            <%
                            }
                        %>
                    </ul>
                </div>
                <%
                }
            %>
            <%
                if (context == null) {
                %>
                <a class="button" href="<%= html(back) %>">Về danh sách bài tập</a>
                <%
                }
                else {
                %>
                <p class="class-context">
                    <strong><%= html(context.classCode()) %> — <%= html(context.className()) %></strong>
                    <br>
                    <%= html(context.subjectCode()) %> — <%= html(context.subjectName()) %>
                </p>
                <%
                    if(context.templates().isEmpty()) {
                    %>
                    <div class="notice">
                        <h2>
                            Chưa có mẫu bài tập đang hoạt động
                        </h2>
                        <p>
                            Chuyên gia cần cung cấp mẫu bài tập đang hoạt động cho môn học này trước khi bạn tạo bài tập.
                        </p>
                    </div>
                    <%
                    }
                %>
                <form method="post" action="<%= html(base) %>/teacher/assignments" id="assignment-form"
          data-edit="<%= editing %>" data-edit-conflict="<%= editConflict %>"
          data-saved-response-type="<%= html(savedResponseType) %>"
          data-saved-essay-content="<%= savedEssayContent %>"
          data-saved-objective-content="<%= savedObjectiveContent %>">
                    <input type="hidden" name="action" value="<%= editing ? "update" : "create" %>">
                    <input type="hidden" name="csrf" value="<%= html(WebSupport.csrf(request)) %>">
                    <input type="hidden" name="classId" value="<%= context.classId() %>">
                    <%
                        if (editing) {
                        %>
                        <input type="hidden" name="assignmentId" value="<%= html(form.value("assignmentId")) %>">
                        <input type="hidden" name="revision" value="<%= html(form.value("revision")) %>">
                        <%
                        }
                    %>
                    <textarea hidden id="template-data"><%= html(new Gson().toJson(Boolean.TRUE.equals(request.getAttribute("createContextUnavailable")) ? List.of() : context.templates())) %></textarea>
                    <textarea hidden id="tasks-data" name="tasksJson"><%= html(form.value("tasksJson")) %></textarea>
                    <textarea hidden id="questions-data" name="questionsJson"><%= html(form.value("questionsJson")) %></textarea>
                    <section class="form-card" aria-labelledby="general-heading">
                        <h2 id="general-heading">
                            Thông tin chung
                        </h2>
                        <label for="templateId">
                            Mẫu bài tập *
                        </label>
                        <select id="templateId" name="templateId" required aria-describedby="templateId-error">
                            <option value="">
                                Chọn mẫu bài tập
                            </option>
                            <%
                                boolean selectedTemplateExists = false;
                                for(var template : context.templates()) {
                                    boolean selected = Long.toString(template.templateId()).equals(form.value("templateId"));
                                    selectedTemplateExists |= selected;
                                %>
                                <option value="<%= template.templateId() %>" <%= selected ? "selected" : "" %>>
                                    <%= html(template.displayTitle()) %>
                                </option>
                                <%
                                }
                                if(!form.value("templateId").isBlank() && !selectedTemplateExists) {
                                %>
                                <option selected disabled value="">
                                    Mẫu đã chọn trước đó hiện không còn khả dụng
                                </option>
                                <%
                                }
                            %>
                        </select>
                        <small class="field-error" id="templateId-error"><%= html(errors.get("templateId")) %></small>
                        <p class="form-hint">
                            Chọn mẫu để điền sẵn tiêu đề, hướng dẫn và điểm tối đa. Bạn có thể chỉnh sửa các thông tin này.
                        </p>
                        <div class="form-grid">
                            <div>
                                <label for="purpose">
                                    Mục đích *
                                </label>
                                <select id="purpose" name="purpose" required>
                                    <option value="REGULAR" <%= "REGULAR".equals(form.value("purpose")) ? "selected" : "" %>>
                                        Bài tập chính thức
                                    </option>
                                    <option value="PRACTICE" <%= "PRACTICE".equals(form.value("purpose")) ? "selected" : "" %>>
                                        Luyện tập
                                    </option>
                                </select>
                                <small class="field-error" id="purpose-error"><%= html(errors.get("purpose")) %></small>
                            </div>
                            <div>
                                <label for="responseType">
                                    Hình thức trả lời *
                                </label>
                                <select id="responseType" name="responseType" required>
                                    <option value="ESSAY" <%= "ESSAY".equals(form.value("responseType")) ? "selected" : "" %>>
                                        Tự luận
                                    </option>
                                    <option value="OBJECTIVE" <%= "OBJECTIVE".equals(form.value("responseType")) ? "selected" : "" %>>
                                        Trắc nghiệm
                                    </option>
                                </select>
                                <small class="field-error" id="responseType-error"><%= html(errors.get("responseType")) %></small>
                            </div>
                        </div>
                        <label for="title">
                            Tiêu đề *
                        </label>
                        <input
                            id="title"
                            name="title"
                            maxlength="200"
                            required
                            value="<%= html(form.value("title")) %>"
                            aria-describedby="title-error">
                        <small class="field-error" id="title-error"><%= html(errors.get("title")) %></small>
                        <label for="instructions">
                            Hướng dẫn
                        </label>
                        <textarea id="instructions" name="instructions" rows="5" data-character-limit="500" aria-describedby="instructions-count instructions-error"><%= html(form.value("instructions")) %></textarea>
                        <small class="character-count" id="instructions-count" aria-live="polite"></small><small class="field-error" id="instructions-error"><%= html(errors.get("instructions")) %></small>
                        <label for="maximumScore">
                            Điểm tối đa *
                        </label>
                        <input
                            id="maximumScore"
                            name="maximumScore"
                            type="text"
                            inputmode="numeric"
                            pattern="[0-9]+"
                            data-integer-min="1"
                            data-integer-max="100"
                            required
                            value="<%= html(form.value("maximumScore")) %>"
                            aria-describedby="maximumScore-hint maximumScore-error">
                        <small class="field-error" id="maximumScore-error"><%= html(errors.get("maximumScore")) %></small>
                        <p class="form-hint" id="maximumScore-hint">
                            Nhập số nguyên từ 1 đến 100. Không chấp nhận số thập phân hoặc phép tính như 5+5.
                        </p>
                    </section>
                    <section class="form-card" id="essay-section" aria-labelledby="essay-heading" <%= "OBJECTIVE".equals(form.value("responseType")) ? "hidden" : "" %>>
                        <h2 id="essay-heading">
                            Câu hỏi tự luận
                        </h2>
                        <p class="form-hint">
                            Thêm từ 1 đến 200 câu hỏi. Mỗi câu chỉ gồm nội dung và điểm, ít nhất 0.01 điểm, tối đa 2 chữ số thập phân. Tổng điểm các câu phải bằng điểm tối đa của bài tập trước khi lưu.
                        </p>
                        <fieldset id="essay-fields" <%= "OBJECTIVE".equals(form.value("responseType")) ? "disabled" : "" %>>
                            <div id="task-editor">
                            </div>
                            <small class="field-error" id="tasksJson-error"><%= html(errors.get("tasksJson")) %></small>
                            <div class="editor-footer">
                                <button type="button" class="button" id="add-task">
                                    Thêm câu tự luận
                                </button>
                                <span id="task-count" aria-live="polite"></span><span id="task-points-total" aria-live="polite"></span>
                            </div>
                        </fieldset>
                    </section>
                    <section class="form-card" id="question-section" aria-labelledby="questions-heading" <%= "OBJECTIVE".equals(form.value("responseType")) ? "" : "hidden" %>>
                        <h2 id="questions-heading">
                            Câu hỏi trắc nghiệm
                        </h2>
                        <p class="form-hint">
                            Thêm từ 1 đến 500 câu hỏi, tối đa 10 lựa chọn mỗi câu. Đánh dấu tất cả đáp án đúng. Điểm mỗi câu ít nhất 0.01, tối đa 2 chữ số thập phân; tổng điểm phải bằng điểm tối đa của bài tập. Nếu chỉ một câu chưa có điểm, hệ thống sẽ điền phần điểm còn lại khi lưu. Trước khi công bố, mỗi câu cần ít nhất 2 lựa chọn và ít nhất 1 đáp án đúng.
                        </p>
                        <fieldset id="question-fields" <%= "OBJECTIVE".equals(form.value("responseType")) ? "" : "disabled" %>>
                            <div id="question-editor">
                            </div>
                            <small class="field-error" id="questionsJson-error"><%= html(errors.get("questionsJson")) %></small>
                            <div class="editor-footer">
                                <button type="button" class="button" id="add-question">
                                    Thêm câu trắc nghiệm
                                </button>
                                <span id="question-count" aria-live="polite"></span><span id="points-total" aria-live="polite"></span>
                            </div>
                            <p class="form-hint" id="points-auto-fill" aria-live="polite">
                            </p>
                        </fieldset>
                    </section>
                    <section class="form-card" aria-labelledby="timing-heading">
                        <h2 id="timing-heading">
                            Thời gian và giới hạn
                        </h2>
                        <p class="form-hint">
                            Tất cả thời gian theo giờ Việt Nam (UTC+07). Có thể để trống thời gian mở bài và hạn nộp khi lưu nháp; cần điền trước khi công bố. Bài được tự động nộp khi hết thời gian làm bài hoặc đến hạn nộp, tùy thời điểm nào đến trước.
                        </p>
                        <div class="form-grid">
                            <div>
                                <label for="availableFrom">
                                    Thời gian mở bài (UTC+07)
                                </label>
                                <input
                                    id="availableFrom"
                                    name="availableFrom"
                                    type="datetime-local"
                                    step="any"
                                    value="<%= html(form.value("availableFrom")) %>">
                                <small class="field-error" id="availableFrom-error"><%= html(errors.get("availableFrom")) %></small>
                            </div>
                            <div>
                                <label for="dueAt">
                                    Hạn nộp (UTC+07)
                                </label>
                                <input id="dueAt" name="dueAt" type="datetime-local" step="any" value="<%= html(form.value("dueAt")) %>">
                                <small class="field-error" id="dueAt-error"><%= html(errors.get("dueAt")) %></small>
                            </div>
                            <div>
                                <label for="timeLimitMinutes">
                                    Thời gian làm bài (phút)
                                </label>
                                <input
                                    id="timeLimitMinutes"
                                    name="timeLimitMinutes"
                                    type="text"
                                    inputmode="numeric"
                                    pattern="[0-9]+"
                                    data-integer-min="20"
                                    data-integer-max="720"
                                    value="<%= html(form.value("timeLimitMinutes")) %>">
                                <small class="field-error" id="timeLimitMinutes-error"><%= html(errors.get("timeLimitMinutes")) %></small>
                                <p class="form-hint">
                                    Không bắt buộc. Nhập từ 20 đến 720 phút (12 giờ). Để trống nếu chỉ giới hạn theo hạn nộp. Ví dụ: hạn nộp 19:00, bắt đầu lúc 18:30 thì chỉ còn 30 phút dù giới hạn là 60 phút.
                                </p>
                            </div>
                            <div id="attempt-field">
                                <label for="attemptLimit">
                                    Số lượt làm tối đa *
                                </label>
                                <input
                                    id="attemptLimit"
                                    name="attemptLimit"
                                    type="text"
                                    inputmode="numeric"
                                    pattern="[0-9]+"
                                    data-integer-min="1"
                                    data-integer-max="2147483647"
                                    value="<%= html(form.value("attemptLimit")) %>">
                                <small class="field-error" id="attemptLimit-error"><%= html(errors.get("attemptLimit")) %></small>
                                <p class="form-hint">
                                    Chỉ áp dụng cho luyện tập. Nhập số nguyên từ 1 trở lên trước khi lưu.
                                </p>
                            </div>
                        </div>
                    </section>
                    <p class="form-hint">
                        <%= editing ? "Thao tác lưu sẽ cập nhật bản nháp. Sinh viên chỉ xem được bài sau khi công bố." : "Thao tác lưu sẽ tạo bản nháp. Sinh viên chỉ xem được bài sau khi công bố." %>
                    </p>
                    <noscript>
                        <p role="alert">
                            Bật JavaScript để chọn mẫu và chỉnh sửa câu hỏi.
                        </p>
                    </noscript>
                    <p class="field-error" id="client-form-error" role="alert" tabindex="-1">
                    </p>
                    <div class="form-actions">
                        <button
                            type="submit"
                            id="save-draft"
                            class="button primary"
                            disabled
                            data-can-save="<%= !context.templates().isEmpty() && !editConflict %>">
                            <%= editing ? "Lưu thay đổi" : "Lưu bản nháp" %>
                        </button>
                        <a class="button leave-form" href="<%= html(back) %>">Về danh sách bài tập</a>
                    </div>
                </form>
                <%
                }
            %>
        </main>
    </div>
</body>
</html>
