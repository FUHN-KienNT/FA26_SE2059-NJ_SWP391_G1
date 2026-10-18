<%@ page contentType="text/html;charset=UTF-8" import="vn.edu.fpt.ams.model.SystemSetting,vn.edu.fpt.ams.util.WebSupport" %>
<%
SystemSetting setting=(SystemSetting)request.getAttribute("setting");
boolean edit="edit".equals(request.getAttribute("mode")),submitted=Boolean.TRUE.equals(request.getAttribute("submitted"));
String priority=submitted?request.getParameter("priority"):String.valueOf(setting.priority());
String selectedActive=submitted?request.getParameter("active"):String.valueOf(setting.active());
String base=request.getContextPath()+"/admin/settings";
%>
<!doctype html><html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title><%=edit?"Sửa":"Chi tiết"%> thiết lập | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css?v=20261018-ui-4"><%@ include file="/WEB-INF/views/shared/assets.jspf" %><% if(edit){ %><script src="${pageContext.request.contextPath}/js/settings-edit.js" defer></script><% } %></head>
<body class="admin-users admin-app admin-system-settings ams-ui"><%@ include file="/WEB-INF/views/admin/shell.jspf" %>
<main class="users-main ams-content setting-detail-page" id="page-content">
<nav class="bao-context" aria-label="Ngữ cảnh"><a href="<%=base%>">Cài đặt hệ thống</a><span aria-hidden="true">›</span><span><%=edit?"Sửa thiết lập":"Chi tiết thiết lập"%></span></nav>
<header class="setting-detail-heading"><div><h1><%=edit?"Sửa thiết lập":"Chi tiết thiết lập"%></h1><p class="muted"><%=edit?"Cập nhật ưu tiên và trạng thái của thiết lập.":"Xem thông tin và quản lý trạng thái của thiết lập."%></p></div><a class="secondary-button" href="<%=base%>">← Quay lại danh sách</a></header>
<% if(request.getAttribute("error")!=null){ %><p role="alert"><%=WebSupport.html(request.getAttribute("error"))%></p><% } %>
<% if("1".equals(request.getParameter("saved"))){ %><p role="status">Đã lưu thiết lập.</p><% } %>
<section class="setting-identity-card" aria-label="Định danh thiết lập">
  <div><span>Nhóm thiết lập</span><strong><%=WebSupport.html(setting.type().label())%></strong></div>
  <% if(edit){ %><div><span>Tên loại hiển thị</span><strong><%=WebSupport.html(setting.name())%></strong></div><% } %>
  <div><span>Giá trị</span><code><%=WebSupport.html(setting.value())%></code></div>
  <% if(!edit){ %><div><span>Trạng thái</span><span class="status-badge <%=setting.active()?"active":"inactive"%>"><%=setting.active()?"Đang hoạt động":"Ngừng hoạt động"%></span></div><% } %>
</section>
<% if(edit){ %>
  <form class="setting-edit-form" method="post" action="<%=base%>" data-setting-edit data-current-active="<%=setting.active()%>">
    <input type="hidden" name="csrf" value="<%=WebSupport.html(WebSupport.csrf(request))%>"><input type="hidden" name="action" value="update"><input type="hidden" name="id" value="<%=setting.settingId()%>"><input type="hidden" name="currentActive" value="<%=setting.active()%>"><input type="hidden" name="name" value="<%=WebSupport.html(setting.name())%>">
    <section class="setting-form-card"><h2>Thông tin được chỉnh sửa</h2><div class="setting-form-grid">
      <label>Ưu tiên<input type="number" name="priority" min="1" max="9999" required value="<%=WebSupport.html(priority)%>"></label>
      <label>Trạng thái<select name="active" required><option value="true" <%="true".equals(selectedActive)?"selected":""%>>Đang hoạt động</option><option value="false" <%="false".equals(selectedActive)?"selected":""%>>Ngừng hoạt động</option></select></label>
    </div><p class="setting-helper">Đổi trạng thái cần xác nhận. Ngừng hoạt động sẽ ẩn giá trị này khỏi các lựa chọn mới; dữ liệu đã lưu vẫn được giữ.<% if("USER_ROLE".equals(setting.type().name())){ %> Tài khoản đang sử dụng vai trò này không bị vô hiệu hóa và vẫn có thể đăng nhập nếu tài khoản đang hoạt động.<% } %></p></section>
    <div class="setting-action-bar"><a class="secondary-button" href="<%=base%>?mode=view&amp;id=<%=setting.settingId()%>">Hủy</a><button class="primary-button" type="submit">Lưu thay đổi</button></div>
  </form>
<% } else { %>
  <section class="setting-form-card"><h2>Thông tin hiển thị</h2><dl class="setting-detail-grid"><div><dt>Loại</dt><dd><%=WebSupport.html(setting.name())%></dd></div><div><dt>Ưu tiên</dt><dd><%=setting.priority()%></dd></div></dl><p class="setting-helper">Ưu tiên thấp hơn được hiển thị trước trong danh sách lựa chọn. Ngừng hoạt động sẽ ẩn giá trị khỏi các lựa chọn mới; dữ liệu đã lưu vẫn được giữ.<% if("USER_ROLE".equals(setting.type().name())){ %> Tài khoản đang sử dụng vai trò này không bị vô hiệu hóa.<% } %></p></section>
  <div class="setting-action-bar"><a class="secondary-button" href="<%=base%>?mode=edit&amp;id=<%=setting.settingId()%>">Sửa thiết lập</a>
    <form class="setting-status-form" method="post" action="<%=base%>" data-confirm="<%=setting.active()?"Xác nhận vô hiệu hóa thiết lập này? Giá trị sẽ không còn là lựa chọn cho dữ liệu mới; dữ liệu đã lưu vẫn được giữ.":"Xác nhận kích hoạt thiết lập này?"%>"><input type="hidden" name="csrf" value="<%=WebSupport.html(WebSupport.csrf(request))%>"><input type="hidden" name="action" value="status"><input type="hidden" name="id" value="<%=setting.settingId()%>"><input type="hidden" name="active" value="<%=!setting.active()%>">
      <button class="<%=setting.active()?"setting-danger-button":"primary-button"%>" type="submit"><%=setting.active()?"Vô hiệu hóa":"Kích hoạt"%></button>
    </form>
  </div>
<% } %>
</main></div></body></html>
