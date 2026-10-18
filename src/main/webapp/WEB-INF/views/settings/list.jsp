<%@ page contentType="text/html;charset=UTF-8" import="java.util.*,vn.edu.fpt.ams.model.*,vn.edu.fpt.ams.util.WebSupport" %>
<%
List<SystemSetting> settings=(List<SystemSetting>)request.getAttribute("settings");
String keyword=request.getParameter("keyword"),type=request.getParameter("type"),status=request.getParameter("status");
%>
<!doctype html><html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Cài đặt hệ thống | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css?v=20261018-ui-4"><%@ include file="/WEB-INF/views/shared/assets.jspf" %></head>
<body class="admin-users admin-app admin-system-settings ams-ui"><%@ include file="/WEB-INF/views/admin/shell.jspf" %>
<main class="users-main ams-content" id="page-content">
<nav class="bao-context" aria-label="Ngữ cảnh"><span>Cài đặt hệ thống</span><span aria-hidden="true">›</span><span>Danh sách</span></nav>
<header class="setting-page-heading"><h1>Danh sách thiết lập</h1><p class="muted">Tra cứu các loại thiết lập, quản lý thứ tự hiển thị và trạng thái sử dụng.</p></header>
<section class="setting-list-card">
  <% if(request.getAttribute("error")!=null){ %><p role="alert"><%=WebSupport.html(request.getAttribute("error"))%></p><% } %>
  <% if("1".equals(request.getParameter("saved"))){ %><p role="status">Đã cập nhật thiết lập.</p><% } %>
  <form class="setting-filters" method="get" action="${pageContext.request.contextPath}/admin/settings">
    <label>Tìm thiết lập<input name="keyword" maxlength="100" value="<%=WebSupport.html(keyword)%>" placeholder="Tên loại hoặc giá trị"></label>
    <label>Nhóm thiết lập<select name="type"><option value="">Tất cả nhóm</option><% for(var t:SettingType.values()){ %><option value="<%=t.name()%>" <%=t.name().equals(type)?"selected":""%>><%=WebSupport.html(t.label())%></option><% } %></select></label>
    <label>Trạng thái<select name="status"><option value="">Tất cả trạng thái</option><option value="ACTIVE" <%="ACTIVE".equals(status)?"selected":""%>>Đang hoạt động</option><option value="INACTIVE" <%="INACTIVE".equals(status)?"selected":""%>>Ngừng hoạt động</option></select></label>
    <button class="primary-button" type="submit">Tìm kiếm</button>
  </form>
  <div class="users-table-wrap setting-table-wrap"><table class="setting-records"><thead><tr><th>Nhóm thiết lập</th><th>Loại</th><th>Giá trị</th><th>Ưu tiên</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>
  <% if(settings==null||settings.isEmpty()){ %><tr><td colspan="6" class="empty-state">Không có thiết lập phù hợp.</td></tr><% } else for(var setting:settings){ %>
    <tr><td><%=WebSupport.html(setting.type().label())%></td><td><strong><%=WebSupport.html(setting.name())%></strong></td><td><code><%=WebSupport.html(setting.value())%></code></td><td><%=setting.priority()%></td><td><span class="status-badge <%=setting.active()?"active":"inactive"%>"><%=setting.active()?"Đang hoạt động":"Ngừng hoạt động"%></span></td>
    <td><div class="setting-row-actions"><a href="${pageContext.request.contextPath}/admin/settings?mode=view&amp;id=<%=setting.settingId()%>">Xem</a><a href="${pageContext.request.contextPath}/admin/settings?mode=edit&amp;id=<%=setting.settingId()%>">Sửa</a>
    <form method="post" action="${pageContext.request.contextPath}/admin/settings" <% if(setting.active()){ %>data-confirm="Xác nhận vô hiệu hóa thiết lập này? Giá trị sẽ không còn là lựa chọn cho dữ liệu mới; dữ liệu đã lưu vẫn được giữ."<% } %>>
      <input type="hidden" name="csrf" value="<%=WebSupport.html(WebSupport.csrf(request))%>"><input type="hidden" name="action" value="status"><input type="hidden" name="returnTo" value="list"><input type="hidden" name="id" value="<%=setting.settingId()%>"><input type="hidden" name="active" value="<%=!setting.active()%>"><input type="hidden" name="keyword" value="<%=WebSupport.html(keyword)%>"><input type="hidden" name="type" value="<%=WebSupport.html(type)%>"><input type="hidden" name="status" value="<%=WebSupport.html(status)%>">
      <button type="submit" class="<%=setting.active()?"setting-danger":""%>"><%=setting.active()?"Vô hiệu hóa":"Kích hoạt"%></button></form></div></td></tr>
  <% } %></tbody></table></div>
  <p class="bao-record-count">Tổng cộng <%=settings==null?0:settings.size()%> thiết lập</p>
</section></main></div></body></html>
