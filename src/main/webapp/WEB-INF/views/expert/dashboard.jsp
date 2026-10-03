<%@ page contentType="text/html;charset=UTF-8" import="vn.edu.fpt.ams.model.*,vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.util.WebSupport" %>
<% var account=(UserAccount)request.getAttribute("account"); var summary=(ExpertDashboardDTO)request.getAttribute("summary"); %>
<!doctype html><html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Bảng điều khiển chuyên gia | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"></head><body class="expert-app">
<%@ include file="shell.jspf" %>
<header class="expert-page-heading"><h1>Bảng điều khiển chuyên gia</h1><p>Xin chào <%=WebSupport.html(account.fullName())%>. Dưới đây là tổng quan các môn học, lớp và mẫu bài tập của bạn.</p></header>
<section class="expert-metrics" aria-label="Tổng quan">
  <a class="expert-metric blue" href="${pageContext.request.contextPath}/expert/classes"><span class="metric-icon" aria-hidden="true">▦</span><strong><%=summary.assignedSubjects()%></strong><span>Môn học<br>được phân công</span><small>Xem chi tiết →</small></a>
  <a class="expert-metric green" href="${pageContext.request.contextPath}/expert/classes"><span class="metric-icon" aria-hidden="true">♙</span><strong><%=summary.accessibleClasses()%></strong><span>Lớp có thể xem</span><small>Xem chi tiết →</small></a>
  <a class="expert-metric violet" href="${pageContext.request.contextPath}/expert/templates"><span class="metric-icon" aria-hidden="true">▣</span><strong><%=summary.visibleTemplates()%></strong><span>Mẫu bài tập<br>có thể xem</span><small>Xem chi tiết →</small></a>
  <a id="additional-class-requests" class="expert-metric amber" href="#additional-class-requests"><span class="metric-icon" aria-hidden="true">◷</span><strong><%=summary.pendingAdditionalClassRequests()%></strong><span>Yêu cầu lớp bổ sung<br>chờ xem xét</span><small>Chức năng đang được hoàn thiện</small></a>
</section>
<section class="expert-quick"><h2>Truy cập nhanh</h2><div class="expert-quick-links"><a href="${pageContext.request.contextPath}/expert/classes"><span aria-hidden="true">▦</span>Môn học và lớp<br>được phân công</a><a href="${pageContext.request.contextPath}/expert/templates"><span aria-hidden="true">▣</span>Mẫu bài tập</a><a href="#additional-class-requests"><span aria-hidden="true">◷</span>Yêu cầu lớp bổ sung</a></div></section>
<aside class="expert-note"><strong><span aria-hidden="true">●</span> Lưu ý</strong><p>Dữ liệu hiển thị dựa trên các môn học và lớp bạn được phân công với vai trò Chuyên gia chính hoặc Chuyên gia hỗ trợ.</p></aside>
</main></div></body></html>
