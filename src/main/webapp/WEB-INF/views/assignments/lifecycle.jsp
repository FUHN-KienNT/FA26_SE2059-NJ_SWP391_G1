<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.Map,vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*" %>
<%@ page import="vn.edu.fpt.ams.util.AssignmentDetailView,vn.edu.fpt.ams.util.WebSupport" %>
<%@ page import="static vn.edu.fpt.ams.util.AssignmentListView.*" %>
<%
    AssignmentLifecyclePage lifecycle = (AssignmentLifecyclePage) request.getAttribute("lifecyclePage");
    AssignmentDetailPage detail = lifecycle == null ? null : lifecycle.detail();
    Assignment assignment = detail == null ? null : detail.assignment();
    AssignmentListFilter filters = (AssignmentListFilter) request.getAttribute("assignmentFilters");
    if (filters == null) filters = AssignmentListFilter.from(null, null, null, null);
    Map<String, String> errors = (Map<String, String>) request.getAttribute("lifecycleErrors");
    if (errors == null) errors = lifecycle == null || lifecycle.errors() == null ? Map.of() : lifecycle.errors();
    String error = (String) request.getAttribute("lifecycleError");
    boolean hasError = error != null && !error.isBlank();
    String action = lifecycle == null ? (String) request.getAttribute("lifecycleAction") : lifecycle.action();
    boolean publishing = "publish".equals(action);
    boolean cancelling = "cancel".equals(action);
    String pageTitle = publishing ? "Công bố bài tập" : cancelling ? "Hủy bài tập" : "Thao tác bài tập";
    String base = request.getContextPath();
    String listUrl = base + "/teacher/assignments";
    String backUrl = assignment == null ? listUrl : AssignmentDetailView.listUrl(base, assignment.classId(), filters);
    String actionUrl = assignment == null ? backUrl : backUrl + "&assignmentId=" + assignment.assignmentId();
    String detailUrl = assignment == null ? backUrl : actionUrl + "&mode=view";
    boolean alreadyPublished = publishing && assignment != null && assignment.status() == AssignmentStatus.PUBLISHED;
    boolean alreadyCancelled = cancelling && assignment != null && assignment.status() == AssignmentStatus.CANCELLED;
    boolean statusAllowsAction = assignment != null && (publishing && assignment.status() == AssignmentStatus.DRAFT
    || cancelling && (assignment.status() == AssignmentStatus.DRAFT || assignment.status() == AssignmentStatus.PUBLISHED));
    boolean hasRevision = lifecycle != null && lifecycle.revision() != null && lifecycle.revision().matches("[a-fA-F0-9]{64}");
    boolean canConfirm = statusAllowsAction && errors.isEmpty() && hasRevision;
%>
<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title><%= escape(pageTitle) %> | AMS</title>
        <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-list.css">
        <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-detail.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
        <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-lifecycle.css">
    </head>
    <body class="ams-ui teacher-app">
        <%@ include file="/WEB-INF/views/shared/shell.jspf" %>
        <main class="ams-content assignment-lifecycle" id="page-content">
            <nav class="breadcrumb" aria-label="Đường dẫn">
                <a href="<%= escape(base) %>/teacher/dashboard">Bảng điều khiển</a><span aria-hidden="true">/</span><a href="<%= escape(backUrl) %>">Bài tập</a>
                <span aria-hidden="true">/</span>
                <%
                    if (assignment != null) {
                    %>
                    <a href="<%= escape(detailUrl) %>">Chi tiết</a><span aria-hidden="true">/</span>
                    <%
                    }
                %>
                <span aria-current="page"><%= publishing ? "Công bố" : cancelling ? "Hủy" : "Thao tác" %></span>
            </nav>
            <div class="page-heading">
                <div>
                    <p class="eyebrow">
                        Kiểm tra bài tập
                    </p>
                    <h1>
                        <%= escape(pageTitle) %>
                    </h1>
                    <p>
                        Kiểm tra thông tin đã lưu và tác động của thao tác trước khi xác nhận.
                    </p>
                </div>
            </div>
            <%
                if (hasError) {
                %>
                <section class="notice error lifecycle-notice" role="alert">
                    <h2>
                        Không thể hoàn tất thao tác
                    </h2>
                    <p>
                        <%= escape(error) %>
                    </p>
                </section>
                <%
                }
            %>
            <%
                if (assignment == null && !errors.isEmpty()) {
                %>
                <section class="notice error lifecycle-notice" role="alert">
                    <h2>
                        Không thể hoàn tất thao tác
                    </h2>
                    <ul>
                        <%
                            for (String message : errors.values()) {
                            %>
                            <li>
                                <%= escape(message) %>
                            </li>
                            <%
                            }
                        %>
                    </ul>
                </section>
                <%
                }
            %>
            <%
                if (assignment == null) {
                %>
                <section class="empty-state">
                    <h2>
                        Bài tập không khả dụng
                    </h2>
                    <p>
                        Không thể tải bài tập đã lưu. Hãy quay về danh sách để chọn bài tập.
                    </p>
                    <a class="ams-button back-button" href="<%= escape(backUrl) %>">Quay về danh sách</a>
                </section>
                <%
                }
                else {
                %>
                <section class="detail-card lifecycle-panel" aria-labelledby="assignment-heading">
                    <div class="lifecycle-heading">
                        <div>
                            <h2 id="assignment-heading">
                                <%= escape(assignment.title()) %>
                            </h2>
                            <p>
                                <%= escape(detail.classCode()) %> — <%= escape(detail.className()) %> · <%= escape(detail.subjectCode()) %> · <%= escape(detail.termCode()) %>
                            </p>
                        </div>
                        <a href="<%= escape(detailUrl) %>">Xem nội dung bài tập</a>
                    </div>
                    <div class="lifecycle-transition" role="group" aria-label="Trạng thái bài tập">
                        <span class="lifecycle-status"><span>Trạng thái hiện tại</span>
                        <strong class="badge <%= assignment.status() == null ? "" : assignment.status().name().toLowerCase(java.util.Locale.ROOT) %>"><%= assignment.status() == null ? "Chưa xác định" : escape(label(assignment.status())) %></strong></span>
                        <%
                            if (publishing || cancelling) {
                            %>
                            <span class="lifecycle-arrow" aria-hidden="true">→</span><span class="sr-only">Chuyển thành</span><span class="lifecycle-status"><span>Trạng thái sau thao tác</span>
                            <strong class="badge <%= publishing ? "published" : cancelling ? "cancelled" : "draft" %>"><%= publishing ? "Đã công bố" : cancelling ? "Đã hủy" : "Bản nháp" %></strong></span>
                            <%
                            }
                        %>
                    </div>
                    <dl class="metadata lifecycle-metadata">
                        <div>
                            <dt>
                                Lớp
                            </dt>
                            <dd>
                                <%= escape(detail.classCode()) %> — <%= escape(detail.className()) %>
                            </dd>
                        </div>
                        <div>
                            <dt>
                                Mục đích
                            </dt>
                            <dd>
                                <%= escape(label(assignment.assignmentPurpose())) %>
                            </dd>
                        </div>
                        <div>
                            <dt>
                                Hình thức trả lời
                            </dt>
                            <dd>
                                <%= escape(label(assignment.responseType())) %>
                            </dd>
                        </div>
                        <div>
                            <dt>
                                Điểm tối đa
                            </dt>
                            <dd>
                                <%= assignment.maximumScore() == null ? "Chưa đặt" : escape(assignment.maximumScore().stripTrailingZeros().toPlainString()) + " điểm" %>
                            </dd>
                        </div>
                        <div>
                            <dt>
                                Thời gian mở bài (UTC+07)
                            </dt>
                            <dd>
                                <%= escape(deadline(assignment.availableFrom())) %>
                            </dd>
                        </div>
                        <div>
                            <dt>
                                Hạn nộp (UTC+07)
                            </dt>
                            <dd>
                                <%= escape(deadline(assignment.dueAt())) %>
                            </dd>
                        </div>
                    </dl>
                    <%
                        if (alreadyPublished) {
                        %>
                        <p class="state-message" role="status">
                            Bài tập đã được công bố. Nội dung và cài đặt không thể chỉnh sửa. Không cần xác nhận thêm.
                        </p>
                        <%
                        }
                        else if (alreadyCancelled) {
                        %>
                        <p class="state-message" role="status">
                            Bài tập đã bị hủy. Sinh viên không thể bắt đầu hoặc tiếp tục làm bài. Không cần xác nhận thêm.
                        </p>
                        <%
                        }
                        else if (!statusAllowsAction) {
                        %>
                        <div class="notice error lifecycle-notice" role="alert">
                            <h2>
                                Thao tác không khả dụng
                            </h2>
                            <p>
                                <%= publishing && assignment.status() == AssignmentStatus.CANCELLED ? "Bài tập đã hủy không thể chuyển về bản nháp hoặc công bố lại." : publishing || cancelling ? "Không thể thực hiện thao tác với trạng thái hiện tại của bài tập." : "Thao tác bài tập này không được hỗ trợ." %>
                            </p>
                        </div>
                        <%
                        }
                        else if (publishing) {
                        %>
                        <section class="lifecycle-effect" aria-labelledby="effect-heading">
                            <h3 id="effect-heading">
                                Sau khi công bố
                            </h3>
                            <ul>
                                <li>
                                    Nội dung và cài đặt được khóa. Bài tập đã công bố không thể chỉnh sửa.
                                </li>
                                <li>
                                    Sinh viên đủ điều kiện chỉ có thể bắt đầu làm bài trong khoảng thời gian hiển thị ở trên.
                                </li>
                            </ul>
                        </section>
                        <%
                        }
                        else {
                        %>
                        <section class="lifecycle-effect lifecycle-cancel-effect" aria-labelledby="effect-heading">
                            <h3 id="effect-heading">
                                Sau khi hủy
                            </h3>
                            <ul>
                                <li>
                                    Sinh viên không thể bắt đầu hoặc tiếp tục làm bài.
                                </li>
                                <li>
                                    Các lượt đang làm bị vô hiệu hóa.
                                </li>
                                <li>
                                    Bài đã nộp và kết quả hiện có được giữ nguyên.
                                </li>
                                <li>
                                    Bài tập đã hủy không thể chuyển về bản nháp hoặc công bố lại.
                                </li>
                            </ul>
                            <p class="lifecycle-count">
                                Hiện có: <strong><%= Math.max(0, lifecycle.inProgressCount()) %></strong> lượt đang làm. Số lượng có thể thay đổi trước khi bạn xác nhận.
                            </p>
                        </section>
                        <%
                        }
                    %>
                    <%
                        if (!errors.isEmpty()) {
                        %>
                        <section class="notice error lifecycle-notice" role="alert" aria-labelledby="lifecycle-errors-heading">
                            <h3 id="lifecycle-errors-heading">
                                <%= publishing ? "Hãy xử lý các vấn đề sau trước khi công bố" : "Không thể hủy bài tập này" %>
                            </h3>
                            <ul>
                                <%
                                    for (String message : errors.values()) {
                                    %>
                                    <li>
                                        <%= escape(message) %>
                                    </li>
                                    <%
                                    }
                                %>
                            </ul>
                        </section>
                        <%
                        }
                        else if (statusAllowsAction && !hasRevision && !hasError) {
                        %>
                        <section class="notice error lifecycle-notice" role="alert">
                            <h3>
                                Chưa thể xác nhận
                            </h3>
                            <p>
                                Chưa thể xác minh thông tin đã lưu. Hãy tải lại thông tin mới nhất trước khi xác nhận.
                            </p>
                        </section>
                        <%
                        }
                    %>
                    <%
                        if (canConfirm) {
                        %>
                        <form method="post" action="<%= escape(listUrl) %>" id="lifecycle-form" class="lifecycle-actions">
                            <input type="hidden" name="action" value="<%= escape(action) %>">
                            <input type="hidden" name="assignmentId" value="<%= assignment.assignmentId() %>">
                            <input type="hidden" name="classId" value="<%= assignment.classId() %>">
                            <input type="hidden" name="revision" value="<%= escape(lifecycle.revision()) %>">
                            <input type="hidden" name="csrf" value="<%= escape(WebSupport.csrf(request)) %>">
                            <input type="hidden" name="search" value="<%= escape(filters.search()) %>">
                            <input
                                type="hidden"
                                name="purpose"
                                value="<%= filters.purpose() == null ? "" : escape(filters.purpose().name()) %>">
                            <input
                                type="hidden"
                                name="responseType"
                                value="<%= filters.responseType() == null ? "" : escape(filters.responseType().name()) %>">
                            <input
                                type="hidden"
                                name="status"
                                value="<%= filters.status() == null ? "" : escape(filters.status().name()) %>">
                            <button type="submit" class="<%= cancelling ? "lifecycle-cancel-button" : "ams-button-primary" %>">
                                <%= publishing ? "Xác nhận công bố" : "Xác nhận hủy" %>
                            </button>
                            <a class="ams-button" href="<%= escape(detailUrl) %>">Quay về chi tiết</a>
            <a href="<%= escape(backUrl) %>">Quay về danh sách</a>
                        </form>
                        <%
                        }
                        else {
                        %>
                        <div class="lifecycle-actions">
                            <%
                                if (publishing && assignment.status() == AssignmentStatus.DRAFT && !errors.isEmpty()) {
                                %>
                                <a class="ams-button ams-button-primary" href="<%= escape(actionUrl) %>&amp;mode=edit">Sửa bài tập</a>
                                <%
                                }
                            %>
                            <%
                                if (statusAllowsAction && (hasError || !errors.isEmpty() || !hasRevision)) {
                                %>
                                <a class="ams-button" href="<%= escape(actionUrl) %>&amp;mode=<%= escape(action) %>">Tải lại thông tin mới nhất</a>
                                <%
                                }
                            %>
                            <a class="ams-button" href="<%= escape(detailUrl) %>">Quay về chi tiết</a>
            <a href="<%= escape(backUrl) %>">Quay về danh sách</a>
                        </div>
                        <%
                        }
                    %>
                </section>
                <%
                }
            %>
        </main>
    </div>
    <script>
    (() => {
        const form = document.getElementById('lifecycle-form');
        if (!form) return;
        const button = form.querySelector('button[type="submit"]');
        const label = button.textContent;
        form.addEventListener('submit', event => {
            if (form.dataset.submitting === 'true') { event.preventDefault(); return; }
            form.dataset.submitting = 'true';
            button.disabled = true;
            button.textContent = 'Đang xử lý…';
        });
        window.addEventListener('pageshow', () => {
            form.dataset.submitting = 'false'; button.disabled = false; button.textContent = label;
        });
    })();
</script>
</body>
</html>
