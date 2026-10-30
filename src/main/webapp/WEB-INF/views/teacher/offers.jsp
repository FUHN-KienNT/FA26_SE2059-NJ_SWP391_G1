<%@ page contentType="text/html;charset=UTF-8" import="java.util.*,vn.edu.fpt.ams.dto.AssignmentOfferView,vn.edu.fpt.ams.model.AssignmentOfferStatus,vn.edu.fpt.ams.service.AssignmentOfferService,vn.edu.fpt.ams.util.WebSupport,vn.edu.fpt.ams.util.AssignmentListView" %>
<%
List<AssignmentOfferView> offers=(List<AssignmentOfferView>)request.getAttribute("offers");
String keyword=(String)request.getAttribute("keyword");
AssignmentOfferStatus selectedStatus=(AssignmentOfferStatus)request.getAttribute("status");
String context=request.getContextPath();
%>
<!doctype html>
<html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Lời mời dạy lớp bổ sung | AMS</title>
<link rel="stylesheet" href="<%=context%>/css/app.css">
<%@ include file="/WEB-INF/views/shared/assets.jspf" %>
<link rel="stylesheet" href="<%=context%>/css/teacher-offers.css">
</head><body class="ams-ui teacher-app">
<%@ include file="/WEB-INF/views/shared/shell.jspf" %>
<main class="ams-content" id="page-content">
    <h1>Lời mời dạy lớp bổ sung</h1>
    <p class="muted">Bạn có <strong><%=request.getAttribute("pendingCount")%></strong> lời mời đang chờ. Lời mời có hiệu lực 48 giờ từ khi gửi.</p>
    <% if(request.getAttribute("notice")!=null) { %><p role="status"><%=WebSupport.html(request.getAttribute("notice"))%></p><% } %>
    <form class="ams-panel offer-filters" method="get" action="<%=context%>/teacher/offers" data-live-search>
        <label>Tìm kiếm<input name="keyword" maxlength="100" value="<%=WebSupport.html(keyword)%>" placeholder="Lớp, môn học, học kỳ hoặc người gửi"></label>
        <label>Trạng thái<select name="status"><option value="">Tất cả trạng thái</option>
        <% for(AssignmentOfferStatus option:AssignmentOfferStatus.values()) { %>
            <option value="<%=option.name()%>" <%=option==selectedStatus?"selected":""%>><%=AssignmentOfferService.statusLabel(option)%></option>
        <% } %></select></label>
        <button type="submit">Tìm kiếm</button>
        <a href="<%=context%>/teacher/offers">Xóa bộ lọc</a>
    </form>
    <p class="muted">Nhận lời mời sẽ hoàn tất phân công lớp bổ sung, không thay giảng viên của lớp nguồn. Nếu chưa có lớp cấu hình sẵn, lớp mới dùng sĩ số và các chuyên gia hợp lệ của lớp nguồn. Sinh viên overflow được xử lý trong luồng ghi danh riêng.</p>
    <div class="ams-table-wrap"><table class="offer-table">
        <thead><tr><th>Môn / học kỳ</th><th>Lớp và yêu cầu</th><th>Người gửi</th><th>Thời gian</th><th>Trạng thái / phản hồi</th></tr></thead>
        <tbody>
        <% if(offers.isEmpty()) { %><tr><td colspan="5">Không có lời mời phù hợp.</td></tr><% } %>
        <% for(AssignmentOfferView offer:offers) { %>
            <tr>
                <td><strong><%=WebSupport.html(offer.subjectCode())%></strong><br><%=WebSupport.html(offer.subjectName())%><br><span class="muted"><%=WebSupport.html(offer.termCode())%></span></td>
                <td>Lớp nguồn: <strong><%=WebSupport.html(offer.sourceClassCode())%></strong><br>
                    Lớp bổ sung: <%=offer.createdClassCode()==null?"Chưa tạo":WebSupport.html(offer.createdClassCode())%><br>
                    Sĩ số dự kiến: <%=offer.proposedCapacity()%>
                    <details><summary>Yêu cầu #<%=offer.requestId()%></summary><p><%=WebSupport.html(offer.overflowContext())%></p></details>
                </td>
                <td><%=WebSupport.html(offer.offeredByName())%></td>
                <td>Gửi: <%=AssignmentListView.deadline(offer.sentAt())%><br>Hết hạn: <%=AssignmentListView.deadline(offer.expiresAt())%>
                    <% if(offer.respondedAt()!=null) { %><br>Phản hồi: <%=AssignmentListView.deadline(offer.respondedAt())%><% } %>
                </td>
                <td><strong class="offer-state <%=offer.status().name().toLowerCase(Locale.ROOT)%>"><%=AssignmentOfferService.statusLabel(offer.status())%></strong>
                    <% if(offer.status()==AssignmentOfferStatus.PENDING) { %>
                    <form class="offer-response" method="post" action="<%=context%>/teacher/offers" data-confirm="Xác nhận phản hồi lời mời dạy lớp bổ sung?">
                        <input type="hidden" name="csrf" value="<%=WebSupport.html(WebSupport.csrf(request))%>">
                        <input type="hidden" name="offerId" value="<%=offer.offerId()%>">
                        <label>Ghi chú (không bắt buộc)<input name="note" maxlength="500"></label>
                        <div class="offer-actions"><button type="submit" name="decision" value="ACCEPTED">Nhận lời mời</button>
                        <button type="submit" name="decision" value="DECLINED" class="offer-decline">Từ chối</button></div>
                    </form>
                    <% } else if(offer.responseNote()!=null) { %><p><%=WebSupport.html(offer.responseNote())%></p><% } %>
                </td>
            </tr>
        <% } %>
        </tbody>
    </table></div>
    <p><a class="ams-button" href="<%=context%>/teacher/enrollments">Xem lớp phụ trách</a></p>
</main></div></body></html>
