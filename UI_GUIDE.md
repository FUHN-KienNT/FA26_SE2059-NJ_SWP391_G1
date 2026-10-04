# Khung giao diện chung AMS

Assets chung: `css/common-ui.css`, `js/common-ui.js`. `app.css` giữ các kiểu cũ để chuyển từng màn dần.

Trang bảo vệ dùng mẫu sau (đổi title và nội dung; shell tự chọn menu theo role trong session):

```jsp
<%@ page contentType="text/html;charset=UTF-8" %>
<!doctype html><html lang="vi"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Tên màn | AMS</title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
<%@ include file="/WEB-INF/views/shared/assets.jspf" %>
</head><body class="ams-ui">
<%@ include file="/WEB-INF/views/shared/shell.jspf" %>
<main class="ams-content" id="page-content">
<h1>Tên màn</h1>
<p class="muted">Mô tả ngắn</p>
<section class="ams-panel">Nội dung form</section>
<div class="ams-table-wrap"><table>...</table></div>
</main></div></body></html>
```

- Nút chính: `ams-button ams-button-primary`; nút phụ: `ams-button`.
- Thông báo lỗi: `role="alert"`; thành công: `role="status"`.
- Form xác nhận: `data-confirm="Nội dung xác nhận"` (vẫn cần CSRF và kiểm tra quyền server).
- Form tìm kiếm GET: `data-live-search`, input `name="keyword"`; tự submit sau 650 ms và khi đổi select. Không thêm JS trùng trên trang.
- Các JSP Admin cũ có thể tiếp tục include `admin/shell.jspf`; Expert có thể dùng `expert/shell.jspf`, fragment này đã mở main, nên chỉ đóng `</main></div>` ở cuối.
- Menu theo role chỉ phục vụ điều hướng. AuthFilter và service tiếp tục quyết định quyền truy cập.
- Màn chưa chuyển sang khung mới: sử dụng mẫu này khi hoàn thiện; không sao chép CSS của màn khác hoặc định nghĩa lại màu/font toàn cục.
