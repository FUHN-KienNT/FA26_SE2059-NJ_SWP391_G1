<%@ page contentType="text/html;charset=UTF-8" import="vn.edu.fpt.ams.dto.StudentSubjectDTO.Page,vn.edu.fpt.ams.util.WebSupport,vn.edu.fpt.ams.util.AssignmentListView" %>
<% var subjects=(Page)request.getAttribute("subjects"); boolean enrolled="enrolled".equals(subjects.view()); %>
<!doctype html><html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Môn học của sinh viên | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
<%@ include file="/WEB-INF/views/shared/assets.jspf" %>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/student-dashboard.css?v=20261019-2">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/student-subjects.css?v=20261019-2"></head>
<body class="ams-ui student-dashboard student-subjects"><%@ include file="/WEB-INF/views/shared/shell.jspf" %>
<main class="ams-content student-main" id="page-content"><div class="student-page">
<nav class="student-breadcrumb" aria-label="Đường dẫn"><a href="${pageContext.request.contextPath}/student/dashboard">Bảng điều khiển</a><span>›</span><span>Môn học</span></nav>
<header class="student-heading"><div><p class="student-eyebrow">MÔN HỌC CỦA BẠN</p><h1><%=enrolled?"Môn học đã đăng ký":"Môn học mở đăng ký"%></h1>
<p>Xem môn học và đăng ký để được xếp vào lớp phù hợp. Dữ liệu học tập được giữ theo từng học kỳ.</p></div></header>
<nav class="student-tabs" aria-label="Danh sách môn học">
<a href="${pageContext.request.contextPath}/student/subjects" <%=!enrolled?"aria-current=\"page\"":""%>>Mở đăng ký</a>
<a href="${pageContext.request.contextPath}/student/subjects?view=enrolled" <%=enrolled?"aria-current=\"page\"":""%>>Đã đăng ký</a></nav>
<section class="student-panel"><form class="student-subject-filter" method="get" action="${pageContext.request.contextPath}/student/subjects">
<input type="hidden" name="view" value="<%=subjects.view()%>"><label for="subject-search">Tìm môn học<input id="subject-search" name="q" maxlength="100" value="<%=WebSupport.html(subjects.search())%>" placeholder="Mã hoặc tên môn học"></label>
<button class="primary-button" type="submit">Tìm kiếm</button></form>
<% if(subjects.offerings().isEmpty()){ %>
<div class="student-empty"><p><%=enrolled?"Bạn chưa có đăng ký môn học phù hợp.":"Chưa có môn học đang mở đăng ký phù hợp trong học kỳ hiện tại."%></p>
<% if(enrolled){ %><a href="${pageContext.request.contextPath}/student/subjects">Xem môn học mở đăng ký →</a><% }else{ %><p>Admin cần mở thời gian đăng ký môn; lớp phải còn chỗ và có giảng viên đủ điều kiện.</p><% } %></div>
<% }else{ %><div class="student-table-wrap"><table><thead><tr><th>Môn học</th><th>Học kỳ</th><th><%=enrolled?"Lớp được xếp":"Chỗ còn trống"%></th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>
<% for(var item:subjects.offerings()){ %><tr><td><strong><%=WebSupport.html(item.subjectCode())%></strong><small><%=WebSupport.html(item.subjectName())%></small></td>
<td><%=WebSupport.html(item.termCode())%><small><%=WebSupport.html(item.termName())%></small></td>
<td><% if(enrolled){ %><%=item.classCode()==null?"Chưa có lớp đang tham gia":WebSupport.html(item.classCode())%><small><%=WebSupport.html(item.teacherName())%></small><% }else{ %><%=item.availableSeats()%><% } %></td>
<td><span class="student-status"><%=enrolled&&!item.enrolled()?"Đăng ký đã ngừng hoạt động":item.stateLabel()%></span></td>
<td><a class="student-outline-button" href="${pageContext.request.contextPath}/student/subjects?id=<%=item.id()%>">Xem chi tiết</a></td></tr><% } %>
</tbody></table></div><p class="student-panel-footnote">Tìm thấy <%=subjects.offerings().size()%> môn học theo học kỳ.</p><% } %></section>
</div></main></div></body></html>
