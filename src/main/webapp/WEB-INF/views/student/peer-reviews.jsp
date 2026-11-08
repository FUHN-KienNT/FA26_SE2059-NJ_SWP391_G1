<%@ page contentType="text/html;charset=UTF-8" import="vn.edu.fpt.ams.dto.PeerReviewPage,vn.edu.fpt.ams.util.WebSupport,java.util.Map" %>
<%@ taglib prefix="ams" tagdir="/WEB-INF/tags" %>
<%!
    private String reviewInput(jakarta.servlet.http.HttpServletRequest request,String key,Object saved) {
        Object raw=request.getAttribute("peerInput");
        if(!Boolean.TRUE.equals(request.getAttribute("peerConflict")) && raw instanceof Map<?,?>) {
            Object values=((Map<?,?>)raw).get(key);
            if(values instanceof String[] && ((String[])values).length==1) return ((String[])values)[0];
        }
        return saved==null?"":saved.toString();
    }
%>
<%
    PeerReviewPage pageData=(PeerReviewPage)request.getAttribute("peerPage");
    String base=request.getContextPath();
    boolean reviewConflict=Boolean.TRUE.equals(request.getAttribute("peerConflict"));
%>
<!doctype html><html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Đánh giá đồng đội | AMS</title>
    <link rel="stylesheet" href="<%=base%>/css/app.css">
    <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
    <style>.peer-table{width:100%;border-collapse:collapse}.peer-table td,.peer-table th{padding:14px;border:1px solid #d4dfed;text-align:left}.peer-table textarea{width:100%;box-sizing:border-box}.peer-table input{max-width:120px}.peer-panel{padding:24px;margin:20px 0;background:white;border:1px solid #d4dfed;border-radius:8px}.peer-scroll{overflow-x:auto}.peer-table caption{text-align:left;margin:12px 0}.peer-table label{display:block;margin-bottom:6px}</style>
</head><body class="ams-ui student-dashboard">
    <%@ include file="/WEB-INF/views/shared/shell.jspf" %>
    <main class="ams-content" id="page-content">
        <%@ include file="/WEB-INF/views/shared/action-notice.jspf" %>
        <nav><a href="<%=base%>/student/classes">Lớp của tôi</a> / Đánh giá đồng đội</nav>
        <h1>Đánh giá đồng đội</h1>
        <p>Đánh giá thành viên cùng nhóm của từng bài. Bạn không tự chấm mình. Điểm giáo viên và điểm đồng đội được tính theo hệ số đã công bố trong đề.</p>
        <% if(request.getAttribute("peerError")!=null) { %><p class="notice error" role="alert"><%=WebSupport.html(request.getAttribute("peerError"))%></p><% } %>
        <% if(pageData.selected()!=null) { var selected=pageData.selected(); %>
            <section class="peer-panel"><h2><%=WebSupport.html(selected.title())%></h2>
                <p><strong><%=WebSupport.html(selected.groupName())%></strong> · Lớp <%=WebSupport.html(selected.classCode())%></p>
                <p>Điểm tối đa: <%=WebSupport.html(selected.maximumScore())%> · Giáo viên <%=WebSupport.html(selected.teacherWeight())%>% · Đồng đội <%=WebSupport.html(selected.peerWeight())%>%</p>
                <p role="status"><%=WebSupport.html(selected.state())%></p>
                <p>Phần điểm đồng đội là trung bình các điểm nhận được. Chưa nhận đánh giá nào thì phần này bằng 0. Điểm và nhận xét bạn nhập chỉ hiển thị trong phiếu của bạn.</p>
                <% if(reviewConflict) { %><a href="<%=base%>/student/peer-reviews?targetId=<%=selected.targetId()%>">Tải lại bản mới nhất</a><% } %>
                <% if(pageData.reviews().isEmpty()) { %><p>Nhóm không có thành viên khác để đánh giá.</p><% } else { %>
                    <form method="post" action="<%=base%>/student/peer-reviews">
                        <input type="hidden" name="csrf" value="<%=WebSupport.html(WebSupport.csrf(request))%>">
                        <input type="hidden" name="targetId" value="<%=selected.targetId()%>">
                        <input type="hidden" name="page" value="<%=pageData.page()%>">
                        <input type="hidden" name="reviewPage" value="<%=pageData.reviewPage()%>">
                        <div class="peer-scroll"><table class="peer-table"><caption>Đánh giá của bạn cho các thành viên</caption><thead><tr><th>Thành viên</th><th>Điểm / <%=WebSupport.html(selected.maximumScore())%></th><th>Nhận xét</th></tr></thead><tbody>
                            <% for(var review:pageData.reviews()) { boolean enabled=selected.editable()&&!reviewConflict; %>
                                <tr><td><%=WebSupport.html(review.name())%></td><td>
                                    <input type="hidden" name="revision_<%=review.studentId()%>" value="<%=review.revision()%>">
                                    <label for="peer-score-<%=review.studentId()%>">Điểm cho <%=WebSupport.html(review.name())%></label>
                                    <input id="peer-score-<%=review.studentId()%>" name="score_<%=review.studentId()%>" inputmode="decimal" type="text" pattern="[0-9]{1,3}(\.[0-9]{1,2})?"
                                        value="<%=WebSupport.html(reviewInput(request,"score_"+review.studentId(),review.score()))%>" <%=enabled?"":"disabled"%>>
                                </td><td><label for="peer-feedback-<%=review.studentId()%>">Nhận xét cho <%=WebSupport.html(review.name())%></label>
                                    <textarea id="peer-feedback-<%=review.studentId()%>" name="feedback_<%=review.studentId()%>" rows="3" maxlength="2000" <%=enabled?"":"disabled"%>><%=WebSupport.html(reviewInput(request,"feedback_"+review.studentId(),review.feedback()))%></textarea>
                                </td></tr>
                            <% } %>
                        </tbody></table></div>
                        <% if(selected.editable()&&!reviewConflict) { %><p>Để trống những người chưa đánh giá; điểm đã lưu không bị xóa khi để trống.</p><button class="ams-button ams-button-primary" type="submit">Lưu đánh giá</button><% } %>
                    </form>
                    <ams:pagination currentPage="<%=pageData.reviewPage()%>" totalPages="<%=pageData.reviewPages()%>"
                        baseUrl='<%=base+"/student/peer-reviews?targetId="+selected.targetId()+"&page="+pageData.page()%>' pageParameter="reviewPage" ariaLabel="Trang thành viên"/>
                <% } %>
            </section>
        <% } %>
        <section class="peer-panel"><h2>Bài tập nhóm của bạn</h2>
            <% if(pageData.targets().isEmpty()) { %><p>Hiện chưa có bài tập nhóm được giao cho bạn.</p><% } else { %>
                <div class="peer-scroll"><table class="peer-table"><thead><tr><th>Bài tập</th><th>Nhóm / lớp</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>
                    <% for(var target:pageData.targets()) { %><tr>
                        <td><%=WebSupport.html(target.title())%></td><td><%=WebSupport.html(target.groupName())%> / <%=WebSupport.html(target.classCode())%></td>
                        <td><%=WebSupport.html(target.state())%></td><td><a href="<%=base%>/student/peer-reviews?targetId=<%=target.targetId()%>&amp;page=<%=pageData.page()%>">Xem phiếu đánh giá</a></td>
                    </tr><% } %>
                </tbody></table></div>
                <ams:pagination currentPage="<%=pageData.page()%>" totalPages="<%=pageData.pages()%>" baseUrl='<%=base+"/student/peer-reviews"%>' ariaLabel="Trang bài nhóm"/>
            <% } %>
        </section>
    </main></div>
</body></html>
