<%@ page contentType="text/html;charset=UTF-8" import="vn.edu.fpt.ams.util.WebSupport" %>
<!doctype html><html lang="vi"><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta charset="UTF-8"><title>Đăng nhập | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"><%@ include file="/WEB-INF/views/shared/assets.jspf" %></head><body class="ams-auth ams-ui"><main>
<span class="ams-logo" aria-hidden="true">A</span><h1>Đăng nhập AMS</h1><p class="muted">Hệ thống quản lý bài tập</p>
<% if(request.getAttribute("error")!=null){ %><p role="alert"><%=WebSupport.html(vn.edu.fpt.ams.util.Vietnamese.label(request.getAttribute("error")))%></p><% } if("1".equals(request.getParameter("signedOut"))){ %><p role="status">Bạn đã đăng xuất.</p><% } %>
<div class="ams-auth-grid"><section><h2>Quản trị viên · Chuyên gia · Giảng viên</h2><p>Dùng tài khoản nội bộ của AMS. Hệ thống tự điều hướng theo vai trò của tài khoản.</p>
<form method="post" action="${pageContext.request.contextPath}/auth/signin"><input type="hidden" name="csrf" value="<%=WebSupport.csrf(request)%>">
<label>Email <input type="email" name="email" maxlength="100" autocomplete="username" required value="<%=WebSupport.html(request.getParameter("email"))%>"></label>
<label>Mật khẩu <input type="password" name="password" maxlength="1024" autocomplete="current-password" required></label><button type="submit">Đăng nhập nội bộ</button></form>
</section><section><h2>Sinh viên</h2><p>Dùng tài khoản Google với Gmail cá nhân của bạn.</p><a class="ams-button" href="${pageContext.request.contextPath}/auth/google/start">Đăng nhập bằng Google</a>
</section></div></main></body></html>
