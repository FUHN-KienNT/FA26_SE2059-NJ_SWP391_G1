<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.time.ZoneId" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.util.List" %>
<%@ page import="vn.edu.fpt.ams.dto.AssignmentOfferView" %>
<%@ page import="vn.edu.fpt.ams.service.AssignmentOfferService" %>
<%@ page import="vn.edu.fpt.ams.util.WebSupport" %>
<%
  String context = request.getContextPath();
  @SuppressWarnings("unchecked")
  List<AssignmentOfferView> offers = (List<AssignmentOfferView>) request.getAttribute("offers");
  String query = request.getAttribute("query") == null ? "" : (String) request.getAttribute("query");
  String notice = (String) request.getAttribute("notice");
  String error = (String) request.getAttribute("error");
  DateTimeFormatter when = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
%>
<!doctype html>
<html lang="vi">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Lời mời lớp bổ sung | AMS</title>
  <link rel="stylesheet" href="<%= context %>/css/app.css">
<%@ include file="/WEB-INF/views/shared/assets.jspf" %></head>
<body class="ams-ui teacher-app">
<%@ include file="/WEB-INF/views/shared/shell.jspf" %><main class="ams-content" id="page-content">
  <h1>Lời mời lớp bổ sung</h1>
  <p class="muted">Đây chưa phải lớp phụ trách. Nhận lời mời là đồng ý dạy thêm lớp được đề nghị. Từ chối thì lớp đó không giao cho bạn. Lời mời hết hạn sau 48 giờ.</p>
  <% if (notice != null) { %><p role="status"><%= WebSupport.html(notice) %></p><% } %>
  <% if (error != null) { %><p role="alert"><%= WebSupport.html(error) %></p><% } %>
  <form class="users-toolbar" method="get" action="<%= context %>/teacher/offers">
    <label>Tìm lời mời <input type="search" name="q" value="<%= WebSupport.html(query) %>" placeholder="Lớp, môn, người gửi hoặc trạng thái"></label>
    <button type="submit">Tìm</button>
    <a class="ams-button" href="<%= context %>/teacher/offers">Xóa tìm</a>
  </form>
  <table>
    <tr><th>Lớp nguồn</th><th>Nội dung</th><th>Người gửi</th><th>Hết hạn</th><th>Trạng thái</th><th></th></tr>
    <% for (AssignmentOfferView offer : offers) { %>
      <tr>
        <td><%= WebSupport.html(offer.classCode()) %><br><%= WebSupport.html(offer.className()) %><br><%= WebSupport.html(offer.subjectCode()) %></td>
        <td><%= WebSupport.html(offer.overflowContext()) %></td>
        <td><%= WebSupport.html(offer.offeredByName()) %></td>
        <td><%= offer.expiresAt() == null ? "" : when.format(offer.expiresAt()) %></td>
        <td><%= WebSupport.html(AssignmentOfferService.statusLabel(offer.status())) %>
          <% if (offer.responseNote() != null && !offer.responseNote().isBlank()) { %><br><small><%= WebSupport.html(offer.responseNote()) %></small><% } %>
        </td>
        <td>
          <% if (offer.open()) { %>
            <form action="<%= context %>/teacher/offers" method="post">
              <input type="hidden" name="offerId" value="<%= offer.offerId() %>">
              <input type="hidden" name="q" value="<%= WebSupport.html(query) %>">
              <label>Ghi chú <input name="note" maxlength="500" placeholder="Không bắt buộc"></label>
              <div class="ams-actions">
                <button type="submit" name="decision" value="ACCEPTED">Nhận</button>
                <button type="submit" class="danger" name="decision" value="DECLINED">Từ chối</button>
              </div>
            </form>
          <% } else { %>
            <span class="muted">Đã đóng</span>
          <% } %>
        </td>
      </tr>
    <% } %>
  </table>
  <% if (offers.isEmpty()) { %><p><%= query.isBlank() ? "Bạn chưa có lời mời lớp bổ sung." : "Không có lời mời khớp từ khóa." %></p><% } %>
</main></div>
</body>
</html>
