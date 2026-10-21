<%@ page contentType="text/html;charset=UTF-8" import="java.util.*,vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*,vn.edu.fpt.ams.util.WebSupport" %>
<%
    String mode=(String)request.getAttribute("mode"),base=request.getContextPath()+"/admin/subjects",token=WebSupport.csrf(request);
    boolean create="create".equals(mode),submitted=Boolean.TRUE.equals(request.getAttribute("submitted")) && (create || "update".equals(request.getParameter("action")));
    SubjectDetailsDTO details=(SubjectDetailsDTO)request.getAttribute("details");
    SubjectDTO s=create?null:details.subject();
    var eligible=(List<ExpertOptionDTO>)request.getAttribute("eligibleExperts");
    if(eligible==null)eligible=List.of();
    Set<Long> primary=submitted?WebSupport.ids(request.getParameterValues("primaryExperts")):Set.of(),supporting=submitted?WebSupport.ids(request.getParameterValues("supportingExperts")):Set.of();
    String code=submitted?request.getParameter("subjectCode"):(create?"":s.subjectCode()),name=submitted?request.getParameter("subjectName"):(create?"":s.subjectName()),description=submitted?request.getParameter("description"):(create?"":s.description());
    String feedbackAction=request.getAttribute("error")!=null?request.getParameter("action"):(String)session.getAttribute("subjectEditNoticeAction");
    Object feedbackNotice=session.getAttribute("notice");
    session.removeAttribute("notice");
    session.removeAttribute("subjectEditNoticeAction");
    boolean localFeedback=!create && feedbackAction!=null && Set.of("experts","status","term").contains(feedbackAction);
%>
<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title><%=create?"Thêm":"Sửa"%> môn học | AMS</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
        <%@ include file="/WEB-INF/views/subjects/assets.jspf" %>
    </head>
    <body class="admin-users admin-app admin-subjects ams-ui">
        <%@ include file="/WEB-INF/views/admin/shell.jspf" %>
        <main class="users-main subject-form ams-content bao-mode-page" id="page-content">
            <nav class="bao-context" aria-label="Ngữ cảnh"><a href="${pageContext.request.contextPath}/admin/subjects">Môn học</a><span aria-hidden="true">›</span><span><%=create?"Thêm môn học":"Chỉnh sửa"%></span></nav>
            <div class="subject-heading">
                <div>
                    <h1>
                        <%=create?"Thêm môn học":"Sửa môn học"%>
                    </h1>
                    <p class="muted">
                        <%=create?"Thông tin môn, chuyên gia và học kỳ.":"Thông tin môn và đăng ký theo kỳ."%>
                    </p>
                </div>
                <a class="secondary-button" href="<%=create?base:base+"?mode=view&id="+s.subjectId()%>">← <%=create?"Quay lại danh sách":"Quay lại chi tiết"%></a>
            </div>
            <%
                if(!create){
                %>
                <div class="bao-edit-context">
                    <strong><%=WebSupport.html(s.subjectCode())%></strong><span class="status-badge <%=s.status()==SubjectStatus.ACTIVE?"active":"inactive"%>"><%=s.status()==SubjectStatus.ACTIVE?"Đang hoạt động":"Ngừng hoạt động"%></span>
                </div>
                <%
                }
            %>
            <%
                if(request.getAttribute("error")!=null && !localFeedback){
                %>
                <p role="alert" tabindex="-1" data-subject-feedback>
                    <%=WebSupport.html(request.getAttribute("error"))%>
                </p>
                <%
                }
            %>
            <% if(feedbackNotice!=null && !localFeedback){ %>
            <p role="status" tabindex="-1" data-subject-feedback><%=WebSupport.html(feedbackNotice)%></p>
            <% } %>
            <% if(!create){ %>
            <nav class="subject-edit-nav" aria-label="Các phần chỉnh sửa">
                <a href="#subject-information">Thông tin môn</a><a href="#subject-experts">Chuyên gia</a><a href="#subject-status">Trạng thái môn</a><a href="#subject-registration">Đăng ký theo kỳ</a>
            </nav>
            <% } %>
            <form class="bao-focused-form" method="post" action="<%=base%>">
                <input type="hidden" name="csrf" value="<%=token%>">
                <input type="hidden" name="action" value="<%=create?"create":"update"%>">
                <% if(!create){ %>
                <input type="hidden" name="returnTo" value="edit">
                <input type="hidden" name="termId" value="<%=request.getAttribute("editTermId")==null?"":request.getAttribute("editTermId")%>">
                <% } %>
                <%
                    if(!create){
                    %>
                    <input type="hidden" name="id" value="<%=s.subjectId()%>">
                    <%
                    }
                %>
                <section class="bao-card" id="subject-information">
                    <h2>
                        Thông tin môn học
                    </h2>
                    <div class="form-grid">
                        <label>
                            Mã môn học
                            <input name="subjectCode" maxlength="30" required value="<%=WebSupport.html(code)%>" <%=create?"":"readonly"%>>
                        </label>
                        <label>
                            Tên môn học
                            <input name="subjectName" maxlength="150" required value="<%=WebSupport.html(name)%>">
                        </label>
                    </div>
                    <label>
                        Mô tả
                        <textarea name="description" rows="4"><%=WebSupport.html(description)%></textarea>
                    </label>
                </section>
                <%
                    if(create){
                        String selected=request.getParameter("initialStatus");
                        if(selected==null)selected="ACTIVE";
                    %>
                    <section class="bao-card bao-create-responsibilities">
                        <h2>
                            Phân công ban đầu
                        </h2>
                        <label>
                            Trạng thái ban đầu
                            <select name="initialStatus" required>
                                <option value="ACTIVE" <%="ACTIVE".equals(selected)?"selected":""%>>
                                    Hoạt động
                                </option>
                                <option value="INACTIVE" <%="INACTIVE".equals(selected)?"selected":""%>>
                                    Ngừng hoạt động
                                </option>
                            </select>
                        </label>
                        <div class="expert-grid">
                            <fieldset>
                                <legend>
                                    Chuyên gia chính (bắt buộc 1–2)
                                </legend>
                                <% if(eligible.isEmpty()){ %><p class="muted">Chưa có chuyên gia đang hoạt động.</p><% } %>
                                <%
                                    for(var e:eligible){
                                    %>
                                    <label class="check">
                                        <input
                                            type="checkbox"
                                            name="primaryExperts"
                                            value="<%=e.userId()%>"
                                            <%=primary.contains(e.userId())?"checked":""%>>
                                        <span><strong><%=WebSupport.html(e.fullName())%></strong>
                                        <br>
                                        <small><%=WebSupport.html(e.email())%></small></span>
                                    </label>
                                    <%
                                    }
                                %>
                            </fieldset>
                            <fieldset>
                                <legend>
                                    Chuyên gia hỗ trợ (tối đa 2)
                                </legend>
                                <% if(eligible.isEmpty()){ %><p class="muted">Chưa có chuyên gia đang hoạt động.</p><% } %>
                                <%
                                    for(var e:eligible){
                                    %>
                                    <label class="check">
                                        <input
                                            type="checkbox"
                                            name="supportingExperts"
                                            value="<%=e.userId()%>"
                                            <%=supporting.contains(e.userId())?"checked":""%>>
                                        <span><strong><%=WebSupport.html(e.fullName())%></strong>
                                        <br>
                                        <small><%=WebSupport.html(e.email())%></small></span>
                                    </label>
                                    <%
                                    }
                                %>
                            </fieldset>
                        </div>
                    </section>
                    <%
                    }
                %>
                <% if(create){ %>
                    <%@ include file="/WEB-INF/views/subjects/create-term.jspf" %>
                <% } %>
                <div class="form-actions">
                    <button class="primary-button">
                        <%=create?"Tạo môn học":"Lưu thông tin môn"%>
                    </button>
                    <a class="secondary-button" href="<%=create?base:base+"?mode=view&id="+s.subjectId()%>">Hủy</a>
                </div>
            </form>
            <% if(!create){ %>
                <%@ include file="/WEB-INF/views/subjects/edit-controls.jspf" %>
            <% } %>
        </main>
    </div>
    <script src="${pageContext.request.contextPath}/js/subject-edit.js?v=20261021-review1" defer></script>
    <script>document.querySelectorAll('[name=primaryExperts]').forEach(p=>p.addEventListener('change',()=>{if(p.checked){const s=document.querySelector('[name=supportingExperts][value="'+p.value+'"]');if(s)s.checked=false}}));document.querySelectorAll('[name=supportingExperts]').forEach(s=>s.addEventListener('change',()=>{if(s.checked){const p=document.querySelector('[name=primaryExperts][value="'+s.value+'"]');if(p)p.checked=false}}));</script>
</body>
</html>
