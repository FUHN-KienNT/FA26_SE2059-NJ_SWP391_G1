<%@ page contentType="text/html;charset=UTF-8" import="vn.edu.fpt.ams.dto.StudentSubjectDTO.Offering,vn.edu.fpt.ams.util.WebSupport,vn.edu.fpt.ams.util.AssignmentListView" %>
<% var offering=(Offering)request.getAttribute("offering"); %>
<!doctype html><html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Chi tiết môn học | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
<%@ include file="/WEB-INF/views/shared/assets.jspf" %>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/student-dashboard.css?v=20261019-2">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/student-subjects.css?v=20261019-2"></head>
<body class="ams-ui student-dashboard student-subjects"><%@ include file="/WEB-INF/views/shared/shell.jspf" %>
<main class="ams-content student-main" id="page-content"><div class="student-page">
<nav class="student-breadcrumb" aria-label="Đường dẫn"><a href="${pageContext.request.contextPath}/student/dashboard">Bảng điều khiển</a><span>›</span><a href="${pageContext.request.contextPath}/student/subjects">Môn học</a><span>›</span><span>Chi tiết</span></nav>
<header class="student-heading"><div><p class="student-eyebrow"><%=WebSupport.html(offering.termCode())%></p><h1><%=WebSupport.html(offering.subjectCode())%> · <%=WebSupport.html(offering.subjectName())%></h1><p>Thông tin môn học, thời gian đăng ký và lớp của bạn.</p></div>
<a class="student-outline-button" href="${pageContext.request.contextPath}/student/subjects<%=offering.registrationStatus()!=null?"?view=enrolled":""%>">← Quay lại danh sách</a></header>
<% if(request.getAttribute("error")!=null){ %><p class="student-notice error" role="alert"><%=WebSupport.html(request.getAttribute("error"))%></p><% } %>
<% if("1".equals(request.getParameter("registered"))&&offering.enrolled()){ %><p class="student-notice" role="status">Đã đăng ký môn học và xếp lớp thành công. Bạn có thể xem thông tin trên Bảng điều khiển.</p><% } %>
<div class="student-detail-grid"><section class="student-panel"><h2>Thông tin môn học</h2><dl class="student-subject-info">
<div><dt>Mã môn học</dt><dd><%=WebSupport.html(offering.subjectCode())%></dd></div><div><dt>Tên môn học</dt><dd><%=WebSupport.html(offering.subjectName())%></dd></div>
<div><dt>Học kỳ</dt><dd><%=WebSupport.html(offering.termCode())%> · <%=WebSupport.html(offering.termName())%></dd></div>
<div><dt>Mô tả</dt><dd class="student-description"><%=offering.description()==null||offering.description().isBlank()?"Chưa có mô tả.":WebSupport.html(offering.description())%></dd></div></dl></section>
<section class="student-panel"><h2>Đăng ký môn học</h2><dl class="student-subject-info">
<div><dt>Mở đăng ký</dt><dd><%=WebSupport.html(AssignmentListView.deadline(offering.opensAt()))%> (UTC+07)</dd></div>
<div><dt>Đóng đăng ký</dt><dd><%=WebSupport.html(AssignmentListView.deadline(offering.closesAt()))%> (UTC+07)</dd></div>
<div><dt>Trạng thái</dt><dd><span class="student-status"><%=offering.stateLabel()%></span></dd></div>
<div><dt>Chỗ còn trống đủ điều kiện</dt><dd><%=offering.availableSeats()%></dd></div></dl>
<% if(offering.canRegister()){ %><p class="student-panel-footnote">Hệ thống tự xếp vào một lớp còn chỗ và có giảng viên đủ điều kiện. Không thu phí đăng ký.</p>
<form method="post" action="${pageContext.request.contextPath}/student/subjects" data-confirm="Xác nhận đăng ký môn học này và được hệ thống xếp lớp?">
<input type="hidden" name="csrf" value="<%=WebSupport.csrf(request)%>"><input type="hidden" name="action" value="register"><input type="hidden" name="id" value="<%=offering.id()%>"><button class="primary-button" type="submit">Đăng ký môn học</button></form>
<% }else if(!offering.enrolled()){ %><p class="student-panel-footnote">Chỉ đăng ký khi môn học đang mở trong học kỳ hiện tại và còn lớp đủ điều kiện. Nếu hết chỗ, vui lòng liên hệ giảng viên.</p><% } %>
</section></div>
<% if(offering.registrationStatus()!=null){ %><section class="student-panel"><h2>Đăng ký và lớp của bạn</h2>
<dl class="student-subject-info student-placement"><div><dt>Đăng ký</dt><dd><%=offering.enrolled()?"Đang hoạt động":"Ngừng hoạt động"%></dd></div>
<div><dt>Lớp đang được xếp</dt><dd><%=offering.classCode()==null?"Không có lớp đang tham gia":WebSupport.html(offering.classCode())%><small><%=WebSupport.html(offering.className())%></small></dd></div>
<div><dt>Giảng viên</dt><dd><%=offering.teacherName()==null?"Chưa phân công":WebSupport.html(offering.teacherName())%></dd></div>
<div><dt>Trạng thái lớp</dt><dd><%=offering.classStatus()==null?"—":"ACTIVE".equals(offering.classStatus())?"Đang hoạt động":"Ngừng hoạt động"%></dd></div></dl>
<a class="student-outline-button" href="${pageContext.request.contextPath}/student/dashboard">Về Bảng điều khiển</a></section><% } %>
</div></main></div></body></html>
