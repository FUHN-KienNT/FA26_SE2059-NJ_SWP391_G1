<%@ page contentType="text/html;charset=UTF-8" import="java.util.*,vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*,vn.edu.fpt.ams.util.WebSupport,vn.edu.fpt.ams.util.Vietnamese" %>
<%
AssignedSubjectDTO subject=(AssignedSubjectDTO)request.getAttribute("subject");
List<AssignedClassDTO> classes=(List<AssignedClassDTO>)request.getAttribute("classes");
Collection<TermOptionDTO> terms=(Collection<TermOptionDTO>)request.getAttribute("terms");
String termId=request.getParameter("termId"),keyword=request.getParameter("keyword");
%>
<!doctype html>
<html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Danh sách lớp học | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css?v=20261018-ui-4"><%@ include file="/WEB-INF/views/shared/assets.jspf" %></head>
<body class="expert-app baonvg-expert expert-assigned ams-ui"><%@ include file="/WEB-INF/views/expert/shell.jspf" %>
<nav class="expert-crumb" aria-label="Ngữ cảnh"><a href="${pageContext.request.contextPath}/expert/classes">Môn học được phân công</a><span aria-hidden="true">›</span><span>Danh sách lớp học</span></nav>
<header class="expert-page-heading expert-class-heading"><div><h1>Danh sách lớp học</h1><p>Các lớp bạn có quyền xem trong môn học đã chọn.</p></div><a class="expert-back" href="${pageContext.request.contextPath}/expert/classes">← Quay lại môn học</a></header>
<section class="expert-subject-context"><div><span class="expert-context-label">Môn học</span><h2><%=WebSupport.html(subject.subjectCode())%> — <%=WebSupport.html(subject.subjectName())%></h2></div><span class="responsibility-badge <%=subject.responsibility().name().toLowerCase(Locale.ROOT)%>"><%=subject.responsibility()==ResponsibilityType.PRIMARY?"Chuyên gia chính":"Chuyên gia hỗ trợ"%></span></section>
<section class="expert-list-card" aria-label="Lớp học của môn học">
  <form class="expert-list-filters expert-class-filters" method="get" action="${pageContext.request.contextPath}/expert/classes">
    <input type="hidden" name="mode" value="subject"><input type="hidden" name="subjectId" value="<%=subject.subjectId()%>">
    <label>Tìm lớp<input name="keyword" maxlength="50" value="<%=WebSupport.html(keyword)%>" placeholder="Mã hoặc tên lớp"></label>
    <label>Học kỳ<select name="termId"><option value="">Tất cả học kỳ</option><% if(terms!=null) for(var term:terms){ %><option value="<%=term.termId()%>" <%=String.valueOf(term.termId()).equals(termId)?"selected":""%>><%=WebSupport.html(term.termCode())%> — <%=WebSupport.html(term.termName())%></option><% } %></select></label>
    <button type="submit">Tìm kiếm</button>
  </form>
  <div class="expert-list-table-wrap"><table class="expert-list-table expert-class-table"><thead><tr><th>Mã lớp</th><th>Học kỳ</th><th>Giảng viên</th><th>Sĩ số</th><th>Trạng thái</th></tr></thead><tbody>
  <% if(classes==null||classes.isEmpty()){ %><tr><td colspan="5" class="expert-list-empty">Không có lớp phù hợp với bộ lọc hoặc chưa có lớp được phân công.</td></tr><% } else for(var item:classes){ %>
    <tr><td><strong><%=WebSupport.html(item.classCode())%></strong><% if(item.className()!=null&&!item.className().isBlank()){ %><small><%=WebSupport.html(item.className())%></small><% } %></td>
    <td><%=WebSupport.html(item.termCode())%><small><%=WebSupport.html(item.termName())%></small></td>
    <td><%=item.teacherName()==null?"Chưa phân công":WebSupport.html(item.teacherName())%><% if(item.teacherEmail()!=null){ %><small><%=WebSupport.html(item.teacherEmail())%></small><% } %></td>
    <td><%=item.activeRosterCount()%> / <%=item.capacity()%></td><td><span class="status-badge <%=item.status().name().toLowerCase(Locale.ROOT)%>"><%=Vietnamese.label(item.status())%></span></td></tr>
  <% } %>
  </tbody></table></div><p class="expert-list-count">Tìm thấy <%=classes==null?0:classes.size()%> lớp học</p>
</section></main></div></body></html>
