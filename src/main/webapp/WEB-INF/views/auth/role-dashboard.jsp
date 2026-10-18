<%@ page contentType="text/html;charset=UTF-8" import="vn.edu.fpt.ams.model.*,vn.edu.fpt.ams.util.WebSupport" %>
<% var account=(UserAccount)request.getAttribute("account"); %>
<!doctype html><html lang="vi"><head><meta charset="UTF-8"><title><%=vn.edu.fpt.ams.util.Vietnamese.label(account.role())%> | AMS</title><link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css"></head><body><main>
<%@ include file="session-nav.jspf" %><h1><%=vn.edu.fpt.ams.util.Vietnamese.label(account.role())%> Bảng điều khiển</h1><p>Xin chào <%=WebSupport.html(account.fullName())%>.</p>
<% if(account.role()==UserRole.EXPERT){ %><p><a href="${pageContext.request.contextPath}/expert/templates">Mẫu bài tập</a></p>
<% } else if(account.role()==UserRole.TEACHER){ %><p><a href="${pageContext.request.contextPath}/teacher/templates">Mẫu bài tập</a></p>
<% } else { %><p><a href="${pageContext.request.contextPath}/student/assignments">Xem bài tập và làm bài trắc nghiệm</a></p><% } %>
</main></body></html>
