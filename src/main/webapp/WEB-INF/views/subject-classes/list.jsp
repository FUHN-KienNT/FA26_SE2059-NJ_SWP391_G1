<%@ page contentType="text/html;charset=UTF-8" import="java.util.*,vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*,vn.edu.fpt.ams.util.WebSupport,vn.edu.fpt.ams.util.Vietnamese" %>
<%
List<AssignedSubjectDTO> subjects=(List<AssignedSubjectDTO>)request.getAttribute("subjects");
String keyword=request.getParameter("keyword"),role=request.getParameter("role"),status=request.getParameter("status");
%>
<!doctype html>
<html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Môn học được phân công | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css?v=20261018-ui-4"><%@ include file="/WEB-INF/views/shared/assets.jspf" %></head>
<body class="expert-app baonvg-expert expert-assigned ams-ui"><%@ include file="/WEB-INF/views/expert/shell.jspf" %>
<nav class="expert-crumb" aria-label="Ngữ cảnh"><span>Chuyên gia</span><span aria-hidden="true">›</span><span>Môn học được phân công</span></nav>
<header class="expert-page-heading"><h1>Môn học được phân công</h1><p>Xem các môn học bạn đang phụ trách và mở danh sách lớp trong phạm vi được phân công.</p></header>
<section class="expert-list-card" aria-label="Danh sách môn học được phân công">
  <form class="expert-list-filters" method="get" action="${pageContext.request.contextPath}/expert/classes">
    <label>Tìm môn học<input name="keyword" maxlength="50" value="<%=WebSupport.html(keyword)%>" placeholder="Mã hoặc tên môn học"></label>
    <label>Vai trò<select name="role"><option value="">Tất cả vai trò</option><option value="PRIMARY" <%="PRIMARY".equals(role)?"selected":""%>>Chuyên gia chính</option><option value="SUPPORTING" <%="SUPPORTING".equals(role)?"selected":""%>>Chuyên gia hỗ trợ</option></select></label>
    <label>Trạng thái<select name="status"><option value="">Tất cả trạng thái</option><option value="ACTIVE" <%="ACTIVE".equals(status)?"selected":""%>>Đang hoạt động</option><option value="INACTIVE" <%="INACTIVE".equals(status)?"selected":""%>>Ngừng hoạt động</option></select></label>
    <button type="submit">Tìm kiếm</button>
  </form>
  <div class="expert-list-table-wrap"><table class="expert-list-table"><thead><tr><th>Mã môn</th><th>Tên môn</th><th>Vai trò Expert</th><th>Số lớp</th><th>Trạng thái</th><th>Hành động</th></tr></thead><tbody>
  <% if(subjects==null||subjects.isEmpty()){ %><tr><td colspan="6" class="expert-list-empty">Không có môn học được phân công phù hợp với bộ lọc.</td></tr><% } else for(var subject:subjects){ %>
    <tr><td><strong><%=WebSupport.html(subject.subjectCode())%></strong></td><td><%=WebSupport.html(subject.subjectName())%></td>
    <td><span class="responsibility-badge <%=subject.responsibility().name().toLowerCase(Locale.ROOT)%>"><%=subject.responsibility()==ResponsibilityType.PRIMARY?"Chuyên gia chính":"Chuyên gia hỗ trợ"%></span></td>
    <td><%=subject.accessibleClassCount()%></td><td><span class="status-badge <%=subject.status().name().toLowerCase(Locale.ROOT)%>"><%=Vietnamese.label(subject.status())%></span></td>
    <td><a class="expert-view-classes" href="${pageContext.request.contextPath}/expert/classes?mode=subject&amp;subjectId=<%=subject.subjectId()%>">Xem lớp <span aria-hidden="true">→</span></a></td></tr>
  <% } %>
  </tbody></table></div><p class="expert-list-count">Tìm thấy <%=subjects==null?0:subjects.size()%> môn học</p>
</section></main></div></body></html>
