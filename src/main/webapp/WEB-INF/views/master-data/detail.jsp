<%@ page contentType="text/html;charset=UTF-8" import="vn.edu.fpt.ams.model.*,vn.edu.fpt.ams.util.*,java.time.*" %>
<%
    String mode=(String)request.getAttribute("mode");
    AcademicTerm term=(AcademicTerm)request.getAttribute("term"),submitted=(AcademicTerm)request.getAttribute("submittedTerm");
    AcademicTerm shown=submitted!=null?submitted:term;
    boolean view="view".equals(mode),create="create".equals(mode);
    Instant now=Instant.now();
    boolean ended=shown!=null && shown.endsAt()!=null && !shown.endsAt().isAfter(now);
    String token=WebSupport.csrf(request),base=request.getContextPath()+"/admin/master-data",list=request.getContextPath()+"/admin/terms",detail=create?list:base+"?mode=view&id="+shown.termId();
%>
<!doctype html><html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title><%=create?"Thêm học kỳ":view?"Chi tiết học kỳ":"Cập nhật học kỳ"%> | AMS</title>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"><%@ include file="/WEB-INF/views/shared/assets.jspf" %>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/academic-terms.css?v=20261021-2">
</head>
<body class="admin-users admin-app admin-terms ams-ui">
<%@ include file="/WEB-INF/views/admin/shell.jspf" %>
<main class="users-main subject-form ams-content bao-mode-page" id="page-content">
<nav class="bao-context" aria-label="Ngữ cảnh"><a href="<%=list%>">Học kỳ</a><span aria-hidden="true">›</span><span><%=create?"Thêm học kỳ":view?"Chi tiết":"Chỉnh sửa"%></span></nav>
<div class="subject-heading"><div><h1><%=create?"Thêm học kỳ":view?"Chi tiết học kỳ":"Cập nhật học kỳ"%></h1><p class="muted">Học kỳ hiện tại được xác định tự động theo ngày bắt đầu và kết thúc.</p></div><a class="secondary-button" href="<%=view||create?list:detail%>">← <%=view||create?"Quay lại danh sách":"Quay lại chi tiết"%></a></div>
<% if(request.getAttribute("error")!=null){ %><p role="alert"><%=WebSupport.html(request.getAttribute("error"))%></p><% } %>
<% if("1".equals(request.getParameter("saved"))){ %><p role="status">Đã lưu học kỳ.</p><% } %>
<% if(view){ %>
<div class="bao-term-layout">
<section class="bao-card"><h2>Thông tin học kỳ</h2><dl class="bao-detail-rows">
<div><dt>Mã học kỳ</dt><dd><%=WebSupport.html(term.termCode())%></dd></div>
<div><dt>Tên học kỳ</dt><dd><%=WebSupport.html(term.termName())%></dd></div>
<div><dt>Ngày bắt đầu</dt><dd><%=term.startsAt().atZone(TermTimeline.ZONE).toLocalDate()%></dd></div>
<div><dt>Ngày kết thúc</dt><dd><%=term.endsAt().atZone(TermTimeline.ZONE).toLocalDate()%></dd></div>
</dl></section>
<section class="bao-card"><h2>Trạng thái</h2><dl class="bao-state-list">
<div><dt>Theo lịch</dt><dd><span class="term-phase <%=term.current()?"current":""%>"><%=term.current()?"Hiện tại":TermTimeline.phase(term.startsAt(),term.endsAt(),now)%></span></dd></div>
<div><dt>Sử dụng</dt><dd><span class="status-badge <%=!ended&&term.active()?"active":"inactive"%>"><%=ended?"Chỉ tra cứu":term.active()?"Được sử dụng":"Đã tắt"%></span></dd></div>
</dl></section></div>
<% if(ended){ %><p class="muted">Học kỳ đã kết thúc. Dữ liệu được giữ để tra cứu.</p><% } else { %><div class="form-actions"><a class="primary-button" href="<%=base%>?mode=edit&amp;id=<%=term.termId()%>">Sửa học kỳ</a></div><% } %>
<% } else { %>
<form class="bao-focused-form bao-academic-form" method="post" action="${pageContext.request.contextPath}/admin/master-data">
<input type="hidden" name="csrf" value="<%=token%>"><input type="hidden" name="action" value="<%=create?"create":"update"%>">
<% if(shown!=null && shown.termId()>0){ %><input type="hidden" name="termId" value="<%=shown.termId()%>"><% } %>
<section class="bao-card"><h2>Thông tin học kỳ</h2><div class="form-grid">
<label>Mã học kỳ <span aria-hidden="true">*</span><input name="termCode" maxlength="20" required placeholder="Ví dụ: FALL2026" value="<%=shown==null?"":WebSupport.html(shown.termCode())%>"></label>
<label>Tên học kỳ <span aria-hidden="true">*</span><input name="termName" maxlength="100" required placeholder="Ví dụ: Fall 2026" value="<%=shown==null?"":WebSupport.html(shown.termName())%>"></label>
<label>Ngày bắt đầu <span aria-hidden="true">*</span><input type="date" name="startsAt" required value="<%=shown==null||shown.startsAt()==null?"":shown.startsAt().atZone(TermTimeline.ZONE).toLocalDate()%>"></label>
<label>Ngày kết thúc <span aria-hidden="true">*</span><input type="date" name="endsAt" required value="<%=shown==null||shown.endsAt()==null?"":shown.endsAt().atZone(TermTimeline.ZONE).toLocalDate()%>"></label>
</div><p class="muted">Ngày kết thúc tính đến hết ngày, theo UTC+07.</p>
<label class="term-enabled"><input type="checkbox" name="active" <%=shown!=null&&shown.active()?"checked":""%>><span><strong>Được sử dụng</strong><small>Bật để cho phép mở môn trong kỳ này. Lịch các kỳ được sử dụng không được trùng nhau.</small></span></label>
</section><div class="form-actions"><a class="secondary-button" href="<%=create?list:detail%>">Hủy</a><button class="primary-button"><%=create?"Tạo học kỳ":"Lưu thay đổi"%></button></div>
</form><% } %>
</main></div></body></html>
