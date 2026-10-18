<%@ page contentType="text/html;charset=UTF-8" import="vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*,vn.edu.fpt.ams.util.WebSupport,vn.edu.fpt.ams.util.Vietnamese" %>
<%
    var details=(SubjectDetailsDTO)request.getAttribute("details");
    var s=details.subject();
    var target=s.status()==SubjectStatus.ACTIVE?SubjectStatus.INACTIVE:SubjectStatus.ACTIVE;
    String base=request.getContextPath()+"/admin/subjects",token=WebSupport.csrf(request);
%>
<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Trạng thái môn học | AMS</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
    </head>
    <body class="admin-users admin-app admin-subjects ams-ui">
        <%@ include file="/WEB-INF/views/admin/shell.jspf" %>
        <main class="users-main subject-form ams-content" id="page-content">
            <nav class="bao-context" aria-label="Ngữ cảnh"><a href="${pageContext.request.contextPath}/admin/subjects">Môn học</a><span aria-hidden="true">›</span><span>Trạng thái</span></nav>
            <p>
                <a class="back-link" href="<%=base%>?mode=view&amp;id=<%=s.subjectId()%>">← Chi tiết môn học</a>
            </p>
            <h1>
                Kích hoạt / Ngừng hoạt động
            </h1>
            <p>
                <strong><%=WebSupport.html(s.subjectCode()+" · "+s.subjectName())%></strong>
            </p>
            <%
                if(request.getAttribute("error")!=null){
                %>
                <p role="alert">
                    <%=WebSupport.html(request.getAttribute("error"))%>
                </p>
                <%
                }
            %>
            <dl class="details-grid">
                <div>
                    <dt>
                        Trạng thái hiện tại
                    </dt>
                    <dd>
                        <%=Vietnamese.label(s.status())%>
                    </dd>
                </div>
                <div>
                    <dt>
                        Trạng thái mới
                    </dt>
                    <dd>
                        <%=Vietnamese.label(target)%>
                    </dd>
                </div>
                <div>
                    <dt>
                        Chuyên gia chính
                    </dt>
                    <dd>
                        <%=details.primaryExperts().size()%>
                    </dd>
                </div>
                <div>
                    <dt>
                        Chuyên gia hỗ trợ
                    </dt>
                    <dd>
                        <%=details.supportingExperts().size()%>
                    </dd>
                </div>
            </dl>
            <%
                if(target==SubjectStatus.INACTIVE){
                %>
                <p class="bao-callout bao-warning">
                    Ngừng hoạt động sẽ đóng đăng ký mới của các cấu hình học kỳ. Dữ liệu lịch sử không bị xóa.
                </p>
                <%
                }
                else{
                %>
                <p class="bao-callout">
                    Kích hoạt yêu cầu 1–2 chuyên gia chính đang hoạt động và tối đa 2 chuyên gia hỗ trợ.
                </p>
                <%
                }
            %>
            <form method="post" action="<%=base%>" data-confirm="Xác nhận thay đổi trạng thái môn học?">
                <input type="hidden" name="csrf" value="<%=token%>">
                <input type="hidden" name="action" value="status">
                <input type="hidden" name="id" value="<%=s.subjectId()%>">
                <input type="hidden" name="status" value="<%=target%>">
                <div class="form-actions">
                    <button class="<%=target==SubjectStatus.ACTIVE?"primary-button":"destructive-button"%>">
                        <%=target==SubjectStatus.ACTIVE?"Kích hoạt":"Ngừng hoạt động"%>
                    </button>
                    <a class="secondary-button" href="<%=base%>?mode=view&amp;id=<%=s.subjectId()%>">Hủy</a>
                </div>
            </form>
        </main>
    </div>
    <script>document.querySelector('[data-confirm]').addEventListener('submit',e=>{if(!confirm(e.currentTarget.dataset.confirm))e.preventDefault()});</script>
</body>
</html>