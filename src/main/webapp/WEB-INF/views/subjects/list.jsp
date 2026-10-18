<%@ page contentType="text/html;charset=UTF-8" import="java.util.*,vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*,vn.edu.fpt.ams.util.WebSupport,vn.edu.fpt.ams.util.Vietnamese" %>
<%
    var subjects=(List<SubjectSummaryDTO>)request.getAttribute("subjects");
    int number=(Integer)request.getAttribute("page");
    long total=(Long)request.getAttribute("total"),pages=Math.max(1,(total+19)/20);
    String base=request.getContextPath()+"/admin/subjects",token=WebSupport.csrf(request);
%>
<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Danh sách môn học | AMS</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
    </head>
    <body class="admin-users admin-app admin-subjects ams-ui">
        <%@ include file="/WEB-INF/views/admin/shell.jspf" %>
        <main class="users-main ams-content" id="page-content">
            <nav class="bao-context" aria-label="Ngữ cảnh">
                <span>Cài đặt</span><span aria-hidden="true">›</span><span>Môn học</span>
            </nav>
            <div class="setting-page-heading">
                <p class="page-eyebrow">
                    QUẢN TRỊ HỌC THUẬT
                </p>
                <h1>
                    Danh sách môn học
                </h1>
                <p class="muted">
                    Tra cứu, cập nhật và quản lý môn học.
                </p>
            </div>
            <section class="setting-list-card">
                <%
                    if(session.getAttribute("notice")!=null){
                    %>
                    <p role="status">
                        <%=WebSupport.html(Vietnamese.label(session.getAttribute("notice")))%>
                    </p>
                    <%
                        session.removeAttribute("notice");
                    }
                %>
                <div class="users-toolbar">
                    <form method="get" id="subject-filters">
                        <label>
                            <span class="sr-only">Mã hoặc tên môn học</span>
                            <input
                                name="keyword"
                                maxlength="80"
                                placeholder="Tìm theo mã hoặc tên…"
                                value="<%=WebSupport.html(request.getParameter("keyword"))%>">
                        </label>
                        <label>
                            <span class="sr-only">Trạng thái</span>
                            <select name="status">
                                <option value="">
                                    Tất cả trạng thái
                                </option>
                                <%
                                    for(var status:SubjectStatus.values()){
                                    %>
                                    <option value="<%=status%>" <%=status.name().equals(request.getParameter("status"))?"selected":""%>>
                                        <%=Vietnamese.label(status)%>
                                    </option>
                                    <%
                                    }
                                %>
                            </select>
                        </label>
                        <button>
                            Tìm kiếm
                        </button>
                    </form>
                    <a class="primary-button" href="<%=base%>?mode=create">+ Thêm môn học</a>
                </div>
                <div class="users-table-wrap">
                    <table class="setting-records">
                        <thead>
                            <tr>
                                <th>
                                    Mã
                                </th>
                                <th>
                                    Tên môn học
                                </th>
                                <th>
                                    Chuyên gia chính
                                </th>
                                <th>
                                    Chuyên gia hỗ trợ
                                </th>
                                <th>
                                    Trạng thái
                                </th>
                                <th>
                                    Thao tác
                                </th>
                            </tr>
                        </thead>
                        <tbody>
                            <%
                                for(var s:subjects){
                                    boolean active=s.status()==SubjectStatus.ACTIVE;
                                %>
                                <tr>
                                    <td>
                                        <strong><%=WebSupport.html(s.subjectCode())%></strong>
                                    </td>
                                    <td>
                                        <%=WebSupport.html(s.subjectName())%>
                                    </td>
                                    <td>
                                        <%
                                            if(s.primaryExperts().isEmpty()){
                                            %>
                                            <span class="muted">Chưa phân công</span>
                                            <%
                                            }
                                            for(var e:s.primaryExperts()){
                                            %>
                                            <div>
                                                <%=WebSupport.html(e.fullName())%>
                                            </div>
                                            <%
                                            }
                                        %>
                                    </td>
                                    <td>
                                        <%
                                            if(s.supportingExperts().isEmpty()){
                                            %>
                                            <span class="muted">Không có</span>
                                            <%
                                            }
                                            for(var e:s.supportingExperts()){
                                            %>
                                            <div>
                                                <%=WebSupport.html(e.fullName())%>
                                            </div>
                                            <%
                                            }
                                        %>
                                    </td>
                                    <td>
                                        <span class="status-badge <%=active?"active":"inactive"%>"><%=Vietnamese.label(s.status())%></span>
                                    </td>
                                    <td>
                                        <div class="row-actions">
                                            <a href="<%=base%>?mode=view&amp;id=<%=s.subjectId()%>">Xem</a><a href="<%=base%>?mode=edit&amp;id=<%=s.subjectId()%>">Sửa</a>
                                            <a href="<%=base%>?mode=experts&amp;id=<%=s.subjectId()%>">Chuyên gia</a><a href="<%=base%>?mode=term&amp;id=<%=s.subjectId()%>">Học kỳ</a>
                                            <a class="<%=active?"destructive-link":""%>" href="<%=base%>?mode=status&amp;id=<%=s.subjectId()%>"><%=active?"Ngừng hoạt động":"Kích hoạt"%></a>
                                        </div>
                                    </td>
                                </tr>
                                <%
                                }
                            %>
                            <%
                                if(subjects.isEmpty()){
                                %>
                                <tr>
                                    <td colspan="6" class="empty-state">
                                        Không có môn học phù hợp.
                                    </td>
                                </tr>
                                <%
                                }
                            %>
                        </tbody>
                    </table>
                </div>
                <div class="users-footer">
                    <p>
                        Hiển thị <%=subjects.isEmpty()?0:(number-1)*20+1%>–<%=subjects.isEmpty()?0:(number-1)*20+subjects.size()%> trong <%=total%> môn học
                    </p>
                    <form method="get" class="pagination">
                        <input type="hidden" name="keyword" value="<%=WebSupport.html(request.getParameter("keyword"))%>">
                        <input type="hidden" name="status" value="<%=WebSupport.html(request.getParameter("status"))%>">
                        <%
                            if(number>1){
                            %>
                            <button name="page" value="<%=number-1%>">
                                Trước
                            </button>
                            <%
                            }
                            for(long n=Math.max(1,number-2);n<=Math.min(pages,number+2);n++){
                            %>
                            <button name="page" value="<%=n%>" <%=n==number?"aria-current=\"page\"":""%>>
                                <%=n%>
                            </button>
                            <%
                            }
                            if(number<pages){
                            %>
                            <button name="page" value="<%=number+1%>">
                                Sau
                            </button>
                            <%
                            }
                        %>
                    </form>
                </div>
            </section>
        </main>
    </div>
    <script>const f=document.getElementById('subject-filters');let timer;f.elements.keyword.addEventListener('input',()=>{clearTimeout(timer);timer=setTimeout(()=>f.requestSubmit(),650)});f.elements.status.addEventListener('change',()=>f.requestSubmit());</script>
</body>
</html>
