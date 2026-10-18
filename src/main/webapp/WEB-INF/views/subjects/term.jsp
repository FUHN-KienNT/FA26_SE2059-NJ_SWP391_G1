<%@ page contentType="text/html;charset=UTF-8" import="java.util.*,java.time.*,java.time.format.*,vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*,vn.edu.fpt.ams.util.WebSupport,vn.edu.fpt.ams.util.Vietnamese" %>
<%
    var details=(SubjectDetailsDTO)request.getAttribute("details");
    var s=details.subject();
    var terms=(List<AcademicTerm>)request.getAttribute("eligibleTerms");
    String base=request.getContextPath()+"/admin/subjects",token=WebSupport.csrf(request);
    var date=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
%>
<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Mở môn theo học kỳ | AMS</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
    </head>
    <body class="admin-users admin-app admin-subjects ams-ui">
        <%@ include file="/WEB-INF/views/admin/shell.jspf" %>
        <main class="users-main subject-form ams-content" id="page-content">
            <nav class="bao-context" aria-label="Ngữ cảnh"><a href="${pageContext.request.contextPath}/admin/subjects">Môn học</a><span aria-hidden="true">›</span><span>Học kỳ</span></nav>
            <p>
                <a class="back-link" href="<%=base%>?mode=view&amp;id=<%=s.subjectId()%>">← Chi tiết môn học</a>
            </p>
            <h1>
                Thiết lập học kỳ cho môn học
            </h1>
            <p>
                <strong><%=WebSupport.html(s.subjectCode()+" · "+s.subjectName())%></strong> ·
                <span class="status-badge <%=s.status()==SubjectStatus.ACTIVE?"active":"inactive"%>"><%=Vietnamese.label(s.status())%></span>
            </p>
            <%
                if(request.getAttribute("error")!=null){
                %>
                <p role="alert">
                    <%=WebSupport.html(request.getAttribute("error"))%>
                </p>
                <%
                }
                if(session.getAttribute("notice")!=null){
                %>
                <p role="status">
                    <%=WebSupport.html(session.getAttribute("notice"))%>
                </p>
                <%
                    session.removeAttribute("notice");
                }
            %>
            <%
                if(s.status()==SubjectStatus.ACTIVE){
                %>
                <form method="post" action="<%=base%>">
                    <input type="hidden" name="csrf" value="<%=token%>">
                    <input type="hidden" name="action" value="term">
                    <input type="hidden" name="id" value="<%=s.subjectId()%>">
                    <label>
                        Học kỳ
                        <select name="termId" required>
                            <option value="">
                                Chọn học kỳ
                            </option>
                            <%
                                for(var term:terms){
                                %>
                                <option
                                    value="<%=term.termId()%>"
                                    <%=String.valueOf(term.termId()).equals(request.getParameter("termId"))?"selected":""%>>
                                    <%=WebSupport.html(term.termCode()+" · "+term.termName())%>
                                </option>
                                <%
                                }
                            %>
                        </select>
                    </label>
                    <div class="form-grid">
                        <label>
                            Mở đăng ký
                            <input
                                type="datetime-local"
                                name="registrationOpensAt"
                                required
                                value="<%=WebSupport.html(request.getParameter("registrationOpensAt"))%>">
                        </label>
                        <label>
                            Đóng đăng ký
                            <input
                                type="datetime-local"
                                name="registrationClosesAt"
                                required
                                value="<%=WebSupport.html(request.getParameter("registrationClosesAt"))%>">
                        </label>
                    </div>
                    <label class="check">
                        <input
                            type="checkbox"
                            name="registrationEnabled"
                            value="true"
                            <%=request.getParameter("registrationEnabled")!=null?"checked":""%>>
                        Cho phép đăng ký
                    </label>
                    <div class="form-actions">
                        <button class="primary-button">
                            Lưu thiết lập
                        </button>
                        <a class="secondary-button" href="<%=base%>?mode=view&amp;id=<%=s.subjectId()%>">Hủy</a>
                    </div>
                </form>
                <%
                }
                else{
                %>
                <p role="alert">
                    Chỉ môn học đang hoạt động mới được mở theo học kỳ.
                </p>
                <%
                }
            %>
            <section>
                <h2>
                    Các học kỳ đã thiết lập
                </h2>
                <p class="muted">
                    Chỉ học kỳ đang hoạt động mới có thể thiết lập ở biểu mẫu trên. Các cấu hình đã lưu vẫn được hiển thị để tra cứu.
                </p>
                <div class="users-table-wrap">
                    <table>
                        <thead>
                            <tr>
                                <th>
                                    Học kỳ
                                </th>
                                <th>
                                    Mở đăng ký
                                </th>
                                <th>
                                    Đóng đăng ký
                                </th>
                                <th>
                                    Cho phép
                                </th>
                                <th>
                                    Trạng thái đăng ký
                                </th>
                            </tr>
                        </thead>
                        <tbody>
                            <%
                                for(var term:details.subjectTerms()){
                                %>
                                <tr>
                                    <td>
                                        <%=WebSupport.html(term.termCode()+" · "+term.termName())%>
                                    </td>
                                    <td>
                                        <%=date.format(term.registrationOpensAt())%>
                                    </td>
                                    <td>
                                        <%=date.format(term.registrationClosesAt())%>
                                    </td>
                                    <td>
                                        <%=term.registrationEnabled()?"Có":"Không"%>
                                    </td>
                                    <td>
                                        <%=WebSupport.html(Vietnamese.registrationStatus(term.registrationStatus()))%>
                                    </td>
                                </tr>
                                <%
                                }
                                if(details.subjectTerms().isEmpty()){
                                %>
                                <tr>
                                    <td colspan="5" class="empty-state">
                                        Chưa có cấu hình học kỳ.
                                    </td>
                                </tr>
                                <%
                                }
                            %>
                        </tbody>
                    </table>
                </div>
            </section>
        </main>
    </div>
</body>
</html>