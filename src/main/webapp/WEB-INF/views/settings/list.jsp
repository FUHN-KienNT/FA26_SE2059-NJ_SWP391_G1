<%@ page contentType="text/html;charset=UTF-8" %>
<% boolean subjects="subjects".equals(request.getAttribute("settingsTab")); %>
<!doctype html><html lang="vi"><head><meta charset="UTF-8"><title>Cài đặt | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"></head><body><main>
<%@ include file="../auth/session-nav.jspf" %>
<nav><a href="${pageContext.request.contextPath}/admin/dashboard">Bảng điều khiển quản trị</a></nav>
<h1>Cài đặt</h1><p>Cấu hình học kỳ và môn học của AMS.</p>
<nav aria-label="Cài đặt" class="settings-tabs">
<a href="?tab=terms" <%=!subjects?"aria-current=\"page\"":""%>>Học kỳ</a>
<a href="?tab=subjects" <%=subjects?"aria-current=\"page\"":""%>>Môn học</a>
</nav>
<p role="status">Các chức năng xem và quản lý Cài đặt sẽ được hoàn thiện trong buổi sau.</p>
<% if(!subjects){ %>
<section id="academic-terms"><h2>Học kỳ</h2><p>Quản lý mã học kỳ, tên, ngày bắt đầu–kết thúc, trạng thái và Học kỳ hiện tại.</p><p>Chỉ có tối đa một Học kỳ hiện tại và học kỳ được chọn phải đang hoạt động.</p></section>
<% } else { %>
<section id="subjects"><h2>Môn học</h2><p>Quản lý danh sách môn học. Khi chọn một môn, trang Chi tiết môn học sẽ gồm:</p>
<h3>Thông tin môn học</h3><p>Mã, tên, mô tả và trạng thái. Mã môn không thay đổi sau khi tạo.</p>
<h3>Phân công chuyên gia</h3><p>Phân công 1–2 chuyên gia chính và 0–2 chuyên gia hỗ trợ cho môn học.</p>
<h3>Môn học theo học kỳ</h3><p>Các học kỳ đã mở môn, bật/tắt đăng ký và thời gian mở–đóng đăng ký. Mỗi môn chỉ mở một lần trong cùng học kỳ.</p>
</section>
<% } %>
</main></body></html>
