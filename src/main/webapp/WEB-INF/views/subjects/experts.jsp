<%@ page contentType="text/html;charset=UTF-8" import="java.util.*,vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*,vn.edu.fpt.ams.util.WebSupport" %>
<%
var details=(SubjectDetailsDTO)request.getAttribute("details");
var s=details.subject();
var eligible=(List<ExpertOptionDTO>)request.getAttribute("eligibleExperts");
Map<Long,ResponsibilityType> current=new HashMap<>();
for(var e:details.primaryExperts())current.put(e.expertId(),ResponsibilityType.PRIMARY);
for(var e:details.supportingExperts())current.putIfAbsent(e.expertId(),ResponsibilityType.SUPPORTING);
String base=request.getContextPath()+"/admin/subjects",token=WebSupport.csrf(request);
%>
<!doctype html><html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Phân công chuyên gia | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"><%@ include file="/WEB-INF/views/shared/assets.jspf" %></head><body class="admin-users admin-app admin-subjects ams-ui">
<%@ include file="/WEB-INF/views/admin/shell.jspf" %>
<main class="users-main subject-form ams-content" id="page-content">
<nav class="bao-context" aria-label="Ngữ cảnh"><span>Cài đặt</span><span aria-hidden="true">›</span><span>Môn học</span></nav>
<p><a class="back-link" href="<%=base%>?mode=view&amp;id=<%=s.subjectId()%>">← Chi tiết môn học</a></p><div class="subject-heading"><div><h1>Phân công chuyên gia</h1><p class="muted"><strong><%=WebSupport.html(s.subjectCode()+" · "+s.subjectName())%></strong></p></div></div>
<% if(request.getAttribute("error")!=null){ %><p role="alert"><%=WebSupport.html(request.getAttribute("error"))%></p><% }if(session.getAttribute("notice")!=null){ %><p role="status"><%=WebSupport.html(session.getAttribute("notice"))%></p><% session.removeAttribute("notice"); } %>

<section aria-labelledby="current-experts-heading"><h2 id="current-experts-heading">Phân công hiện tại</h2><div class="expert-grid current-expert-grid">
<div class="assignment-summary"><h3>Chuyên gia chính (<%=details.primaryExperts().size()%>/2)</h3><% if(details.primaryExperts().isEmpty()){ %><p class="empty-state">Chưa phân công chuyên gia chính.</p><% }else{ %><ul class="expert-summary-list"><% for(var e:details.primaryExperts()){ %><li><strong><%=WebSupport.html(e.fullName())%></strong><small><%=WebSupport.html(e.email())%></small></li><% } %></ul><% } %></div>
<div class="assignment-summary"><h3>Chuyên gia hỗ trợ (<%=details.supportingExperts().size()%>/2)</h3><% if(details.supportingExperts().isEmpty()){ %><p class="empty-state">Chưa phân công chuyên gia hỗ trợ.</p><% }else{ %><ul class="expert-summary-list"><% for(var e:details.supportingExperts()){ %><li><strong><%=WebSupport.html(e.fullName())%></strong><small><%=WebSupport.html(e.email())%></small></li><% } %></ul><% } %></div>
</div></section>

<section aria-labelledby="available-experts-heading"><h2 id="available-experts-heading">Danh sách chuyên gia</h2><p class="muted">Mỗi chuyên gia chỉ có một trạng thái trách nhiệm. Máy chủ sẽ kiểm tra các giới hạn trước khi lưu.</p>
<label class="inline-filter">Tìm chuyên gia <input id="expert-search" type="search" maxlength="80" placeholder="Tên hoặc email"></label>
<form method="post" action="<%=base%>" autocomplete="off"><input type="hidden" name="csrf" value="<%=token%>"><input type="hidden" name="action" value="experts"><input type="hidden" name="id" value="<%=s.subjectId()%>">
<div id="available-experts" class="expert-assignment-list"><% for(var e:eligible){ var selected=current.get(e.userId()); %><fieldset class="expert-option"><legend><strong><%=WebSupport.html(e.fullName())%></strong><small><%=WebSupport.html(e.email())%></small></legend><div class="responsibility-options">
<label><input type="radio" name="responsibility_<%=e.userId()%>" value="PRIMARY" <%=selected==ResponsibilityType.PRIMARY?"checked":""%> required> Chuyên gia chính</label>
<label><input type="radio" name="responsibility_<%=e.userId()%>" value="SUPPORTING" <%=selected==ResponsibilityType.SUPPORTING?"checked":""%>> Chuyên gia hỗ trợ</label>
<label><input type="radio" name="responsibility_<%=e.userId()%>" value="NOT_ASSIGNED" <%=selected==null?"checked":""%>> Chưa phân công</label>
</div></fieldset><% }if(eligible.isEmpty()){ %><p class="empty-state">Không có tài khoản chuyên gia đang hoạt động.</p><% } %></div><div class="form-actions"><button class="primary-button">Lưu phân công</button><a class="secondary-button" href="<%=base%>?mode=view&amp;id=<%=s.subjectId()%>">Hủy</a></div></form></section>

<section><h2>Lịch sử phân công</h2><p class="muted">Bao gồm cả phân công đang hoạt động và quan hệ đã ngừng.</p><div class="users-table-wrap"><table><thead><tr><th>Chuyên gia</th><th>Trách nhiệm</th><th>Trạng thái</th></tr></thead><tbody><% for(var e:details.responsibilityHistory()){ %><tr><td><%=WebSupport.html(e.fullName())%><br><small><%=WebSupport.html(e.email())%></small></td><td><%=e.responsibilityType()==ResponsibilityType.PRIMARY?"Chuyên gia chính":"Chuyên gia hỗ trợ"%></td><td><span class="status-badge <%=e.active()?"active":"inactive"%>"><%=e.active()?"Đang phụ trách":"Đã ngừng"%></span></td></tr><% }if(details.responsibilityHistory().isEmpty()){ %><tr><td colspan="3" class="empty-state">Chưa có lịch sử phân công.</td></tr><% } %></tbody></table></div></section>
</main></div><script>const options=[...document.querySelectorAll('.expert-option')],search=document.getElementById('expert-search');addEventListener('pageshow',()=>document.querySelectorAll('[name^=responsibility_]').forEach(x=>x.checked=x.defaultChecked));search.addEventListener('input',()=>{const q=search.value.toLocaleLowerCase();options.forEach(x=>x.hidden=!x.textContent.toLocaleLowerCase().includes(q))});</script></body></html>
