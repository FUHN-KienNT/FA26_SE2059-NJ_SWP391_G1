<%@ page contentType="text/html;charset=UTF-8" import="java.util.*,vn.edu.fpt.ams.dao.StudentObjectiveDAO,vn.edu.fpt.ams.dto.StudentObjectivePage,vn.edu.fpt.ams.model.SubmissionStatus,vn.edu.fpt.ams.util.WebSupport,vn.edu.fpt.ams.util.AssignmentListView" %>
<% var data=(StudentObjectivePage)request.getAttribute("assignmentPage");
   String base=request.getContextPath();
   var items=(List<StudentObjectiveDAO.Owned>)request.getAttribute("assignments"); %>
<!doctype html><html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Bài tập trắc nghiệm | AMS</title><link rel="stylesheet" href="<%=base%>/css/app.css"><link rel="stylesheet" href="<%=base%>/css/common-ui.css"><link rel="stylesheet" href="<%=base%>/css/student-objective.css"></head><body>
<%@ include file="../shared/shell.jspf" %>
<main id="page-content" class="ams-content student-work">
<p><a href="<%=base%>/student/dashboard">Bảng điều khiển</a> / <a href="<%=base%>/student/assignments">Bài tập trắc nghiệm</a></p>
<% if(data==null){ %>
<h1>Bài tập trắc nghiệm</h1><p>Các bài đã công bố trong lớp bạn đang học.</p>
<% if(items==null || items.isEmpty()){ %><p>Chưa có bài tập trắc nghiệm.</p><% }else{ for(var item:items){ %>
<article class="work-card"><h2><%=WebSupport.html(item.assignment().title())%></h2>
<p>Lớp: <%=WebSupport.html(item.classCode())%> · Hạn nộp: <%=WebSupport.html(AssignmentListView.deadline(item.assignment().dueAt()))%> (UTC+07)</p>
<a href="<%=base%>/student/assignments?assignmentId=<%=item.assignment().assignmentId()%>">Xem bài tập</a></article>
<% } } }else{ %>
<h1><%=WebSupport.html(data.assignment().title())%></h1>
<p>Lớp: <%=WebSupport.html(data.classCode())%> · Điểm tối đa: <%=WebSupport.html(data.assignment().maximumScore())%></p>
<div class="work-card"><h2>Hướng dẫn</h2><p class="pre-wrap"><%=WebSupport.html(data.assignment().instructions())%></p>
<p>Mở: <%=WebSupport.html(AssignmentListView.deadline(data.assignment().availableFrom()))%> · Hạn nộp: <%=WebSupport.html(AssignmentListView.deadline(data.assignment().dueAt()))%> (UTC+07)</p>
<p>Thời gian làm bài: <%=data.assignment().timeLimitMinutes()==null?"Đến hạn nộp":data.assignment().timeLimitMinutes()+" phút"%> · Số lượt: <%=data.assignment().attemptLimit()==null?1:data.assignment().attemptLimit()%></p></div>
<p>Bài được tự động nộp khi hết thời gian làm bài hoặc đến hạn nộp, tùy thời điểm nào đến trước. Chỉ các đáp án đã được lưu trước thời điểm đó được tính.</p>
<% if(request.getAttribute("formError")!=null){ %><p class="work-error" role="alert"><%=WebSupport.html(request.getAttribute("formError"))%></p><% } %>
<% if(data.submissionId()==0){ %>
<form method="post" action="<%=base%>/student/assignments"><input type="hidden" name="csrf" value="<%=WebSupport.html(WebSupport.csrf(request))%>">
<input type="hidden" name="assignmentId" value="<%=data.assignment().assignmentId()%>"><button type="submit" name="action" value="start">Bắt đầu / tiếp tục làm bài</button></form>
<% }else if(data.status()==SubmissionStatus.IN_PROGRESS){ %>
<p class="work-notice">Lượt <%=data.attemptNumber()%> · Nộp trước <%=WebSupport.html(AssignmentListView.deadline(data.deadline()))%> (UTC+07). Câu nhiều đáp án: chọn tất cả đáp án đúng. Câu chỉ được điểm khi chọn đúng và đủ.</p>
<div class="work-timer" role="status">Thời gian còn lại: <strong id="work-countdown">Đang tải...</strong><span id="work-save-status" aria-live="polite">Đáp án được tự động lưu khi bạn chọn.</span></div>
<form id="objective-work" method="post" action="<%=base%>/student/assignments" data-deadline="<%=data.deadline().toEpochMilli()%>" data-server-time="<%=data.serverTime().toEpochMilli()%>">
<input type="hidden" name="action" value="submit">
<input type="hidden" name="answerRevision" value="<%=data.answerRevision()%>">
<input type="hidden" name="csrf" value="<%=WebSupport.html(WebSupport.csrf(request))%>"><input type="hidden" name="assignmentId" value="<%=data.assignment().assignmentId()%>"><input type="hidden" name="submissionId" value="<%=data.submissionId()%>">
<% for(var q:data.questions()){ %>
<fieldset class="work-card"><legend>Câu <%=q.order()%> · <%=WebSupport.html(q.points().stripTrailingZeros().toPlainString())%> điểm</legend>
<p class="pre-wrap"><%=WebSupport.html(q.text())%></p><p><%=q.multiple()?"Chọn nhiều đáp án":"Chọn một đáp án"%></p>
<% for(var option:q.options()){
    String[] submitted=request.getParameterValues("answer_"+q.id());
    boolean checked=submitted!=null ? Arrays.asList(submitted).contains(option.id()) : data.selected().getOrDefault(q.id(),List.of()).contains(option.id()); %>
<label class="answer-option"><input type="<%=q.multiple()?"checkbox":"radio"%>" name="answer_<%=q.id()%>" value="<%=WebSupport.html(option.id())%>" <%=checked?"checked":""%>><span><%=WebSupport.html(option.text())%></span></label>
<% } %></fieldset><% } %>
<button type="submit">Nộp bài</button></form>
<noscript><p class="work-error">Vui lòng bật JavaScript để tự động lưu đáp án và hiển thị đồng hồ. Nếu không bật, bạn cần nộp bài trước hạn; các lựa chọn chưa gửi lên máy chủ sẽ không được lưu.</p></noscript>
<% }else{ %>
<div class="work-card" role="status"><h2><%=data.status()==SubmissionStatus.EXPIRED?"Lượt làm bài đã hết thời gian":data.status()==SubmissionStatus.VOID?"Lượt làm bài đã bị hủy":"Đã nhận bài nộp"%></h2>
<p>Lượt <%=data.attemptNumber()%>. <%=data.status()==SubmissionStatus.GRADED||data.status()==SubmissionStatus.SUBMITTED?"Kết quả sẽ được hiển thị khi giáo viên công bố.":""%></p></div>
<a href="<%=base%>/student/assignments?assignmentId=<%=data.assignment().assignmentId()%>">Trở về bài tập</a>
<% } } %>
</main></div><script src="<%=base%>/js/common-ui.js" defer></script><script src="<%=base%>/js/student-objective.js" defer></script></body></html>
