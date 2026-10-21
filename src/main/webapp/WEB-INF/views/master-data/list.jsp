<%@ page contentType="text/html;charset=UTF-8" import="java.util.*,java.time.*,vn.edu.fpt.ams.model.*,vn.edu.fpt.ams.util.*" %>
<%
    var terms=(List<AcademicTerm>)request.getAttribute("terms");
    var calendar=(List<AcademicTerm>)request.getAttribute("calendarTerms");
    if(calendar==null) calendar=terms==null?List.of():terms;
    String keyword=request.getParameter("keyword"),status=request.getParameter("status");
    Instant now=Instant.now();
    var current=calendar.stream().filter(AcademicTerm::current).findFirst();
    var next=calendar.stream().filter(t -> t.active() && t.startsAt().isAfter(now)).min(Comparator.comparing(AcademicTerm::startsAt));
    String base=request.getContextPath()+"/admin/master-data";
%>
<!doctype html>
<html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Danh sách học kỳ | AMS</title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"><%@ include file="/WEB-INF/views/shared/assets.jspf" %>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/academic-terms.css?v=20261021-2"></head>
<body class="admin-users admin-app admin-terms ams-ui">
<%@ include file="/WEB-INF/views/admin/shell.jspf" %>
<main class="users-main ams-content" id="page-content">
<nav class="bao-context" aria-label="Ngữ cảnh"><span>Quản lý học kỳ</span><span aria-hidden="true">›</span><span>Danh sách</span></nav>
<div class="setting-page-heading"><p class="page-eyebrow">QUẢN TRỊ HỌC THUẬT</p><h1>Danh sách học kỳ</h1><p class="muted">Học kỳ hiện tại được xác định tự động theo lịch, giờ Việt Nam (UTC+07).</p></div>
<div class="term-calendar-summary">
<section class="bao-card"><span class="muted">Học kỳ hiện tại</span><strong><%=current.isPresent()?WebSupport.html(current.get().termCode()+" · "+current.get().termName()):"Chưa có học kỳ hiện tại"%></strong></section>
<section class="bao-card"><span class="muted">Học kỳ tiếp theo</span><strong><%=next.isPresent()?WebSupport.html(next.get().termCode()+" · "+next.get().termName()):"Chưa có lịch sắp tới"%></strong></section>
</div>
<section class="setting-list-card">
<div class="term-toolbar"><form method="get" action="${pageContext.request.contextPath}/admin/terms">
<label>Tìm học kỳ<input name="keyword" maxlength="50" value="<%=WebSupport.html(keyword)%>" placeholder="Mã hoặc tên học kỳ…"></label>
<label>Sử dụng<select name="status"><option value="">Tất cả</option><option value="ACTIVE" <%= "ACTIVE".equals(status)?"selected":""%>>Được sử dụng</option><option value="INACTIVE" <%= "INACTIVE".equals(status)?"selected":""%>>Đã tắt</option></select></label><button class="primary-button">Tìm kiếm</button></form>
<a class="primary-button" href="<%=base%>?mode=create">+ Thêm học kỳ</a></div>
<% if(request.getAttribute("error")!=null){ %><p role="alert"><%=WebSupport.html(request.getAttribute("error"))%></p><% } %>
<div class="users-table-wrap" role="region" aria-label="Danh sách học kỳ" tabindex="0"><table class="setting-records term-records"><thead><tr><th scope="col">Mã học kỳ</th><th scope="col">Tên học kỳ</th><th scope="col">Ngày bắt đầu</th><th scope="col">Ngày kết thúc</th><th scope="col">Theo lịch</th><th scope="col">Sử dụng</th><th scope="col">Thao tác</th></tr></thead><tbody>
<% if(terms==null||terms.isEmpty()){ %><tr><td colspan="7" class="empty-state">Không có học kỳ phù hợp.</td></tr><% } else {for(var term:terms){ boolean ended=!now.isBefore(term.endsAt()); %>
<tr><td><strong><%=WebSupport.html(term.termCode())%></strong></td><td><%=WebSupport.html(term.termName())%></td><td><%=term.startsAt().atZone(TermTimeline.ZONE).toLocalDate()%></td><td><%=term.endsAt().atZone(TermTimeline.ZONE).toLocalDate()%></td>
<td><span class="term-phase <%=term.current()?"current":""%>"><%=term.current()?"Hiện tại":TermTimeline.phase(term.startsAt(),term.endsAt(),now)%></span></td>
<td><span class="status-badge <%=!ended&&term.active()?"active":"inactive"%>"><%=ended?"Chỉ tra cứu":term.active()?"Được sử dụng":"Đã tắt"%></span></td>
<td><div class="row-actions"><a href="<%=base%>?mode=view&amp;id=<%=term.termId()%>">Xem</a><% if(!ended){ %><a href="<%=base%>?mode=edit&amp;id=<%=term.termId()%>">Sửa</a><% } %></div></td></tr>
<% } } %></tbody></table></div><p class="bao-record-count">Tổng cộng <%=terms==null?0:terms.size()%> học kỳ</p>
</section></main></div></body></html>
