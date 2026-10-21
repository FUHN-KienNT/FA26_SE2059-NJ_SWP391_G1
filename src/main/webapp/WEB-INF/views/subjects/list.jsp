<%@ page contentType="text/html;charset=UTF-8" import="java.util.*,vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*,vn.edu.fpt.ams.util.WebSupport,vn.edu.fpt.ams.util.Vietnamese" %>
<%
    var subjects=(List<SubjectSummaryDTO>)request.getAttribute("subjects");
    int number=(Integer)request.getAttribute("page");
    long total=(Long)request.getAttribute("total"),pages=Math.max(1,(total+19)/20);
    String base=request.getContextPath()+"/admin/subjects",token=WebSupport.csrf(request);
    var terms=(List<AcademicTerm>)request.getAttribute("termOptions");
    Long selectedTerm=(Long)request.getAttribute("selectedTermId");
    boolean readOnlyTerm=Boolean.TRUE.equals(request.getAttribute("selectedTermReadOnly"));
%>
<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Danh sách môn học | AMS</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
        <%@ include file="/WEB-INF/views/subjects/assets.jspf" %>
    </head>
    <body class="admin-users admin-app admin-subjects ams-ui">
        <%@ include file="/WEB-INF/views/admin/shell.jspf" %>
        <main class="users-main ams-content" id="page-content">
            <nav class="bao-context" aria-label="Ngữ cảnh"><span>Quản lý môn học</span><span aria-hidden="true">›</span><span>Danh sách</span></nav>
            <div class="setting-page-heading">
                <p class="page-eyebrow">
                    QUẢN TRỊ HỌC THUẬT
                </p>
                <h1>
                    Quản lý môn học
                </h1>
                <p class="muted">
                    Danh mục môn học và các học kỳ đã mở.
                </p>
                <% for(var term:terms) if(term.current()){ %>
                <p class="subject-current-term">Học kỳ hiện tại: <strong><%=WebSupport.html(term.termCode()+" · "+term.termName())%></strong></p>
                <% } %>
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
                    <form method="get" id="subject-filters" class="subject-catalog-filters" data-live-search>
                        <label>
                            <span>Tìm môn học</span>
                            <input
                                name="keyword"
                                maxlength="80"
                                placeholder="Tìm theo mã hoặc tên…"
                                value="<%=WebSupport.html(request.getParameter("keyword"))%>">
                        </label>
                        <label>
                            <span>Học kỳ</span>
                            <select name="termId">
                                <option value="">Tất cả học kỳ</option>
                                <% for(var term:terms){ %>
                                <option value="<%=term.termId()%>" <%=Objects.equals(selectedTerm,term.termId())?"selected":""%>><%=WebSupport.html(term.termCode()+" · "+term.termName())%><%=term.current()?" (Hiện tại)":""%></option>
                                <% } %>
                            </select>
                        </label>
                        <label>
                            <span>Trạng thái môn</span>
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
                    <a class="primary-button" href="<%=base%>?mode=create<%=selectedTerm==null?"":"&amp;termId="+selectedTerm%>">+ Thêm môn học</a>
                </div>
                <% if(readOnlyTerm){ %><p class="muted">Học kỳ này chỉ dùng để tra cứu.</p><% } %>
                <div class="users-table-wrap" role="region" aria-label="Danh sách môn học" tabindex="0">
                    <table class="setting-records subject-catalog-table">
                        <caption class="sr-only">Danh sách môn học, học kỳ, chuyên gia và trạng thái</caption>
                        <colgroup><col class="subject-col-code"><col class="subject-col-name"><col class="subject-col-term"><col class="subject-col-expert"><col class="subject-col-expert"><col class="subject-col-status"><col class="subject-col-actions"></colgroup>
                        <thead>
                            <tr>
                                <th scope="col">
                                    Mã
                                </th>
                                <th scope="col">
                                    Tên môn học
                                </th>
                                <th scope="col">
                                    Học kỳ đã thiết lập
                                </th>
                                <th scope="col">
                                    Chuyên gia chính
                                </th>
                                <th scope="col">
                                    Chuyên gia hỗ trợ
                                </th>
                                <th scope="col" title="Trạng thái chung của môn, áp dụng cho tất cả học kỳ">
                                    Trạng thái môn
                                </th>
                                <th scope="col">
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
                                    <td class="subject-term-cell">
                                        <% if(s.subjectTerms().isEmpty()){ %><span class="muted">Chưa thiết lập học kỳ</span><% } %>
                                        <% for(var term:s.subjectTerms()) if(selectedTerm==null || selectedTerm.longValue()==term.termId()){ %>
                                        <div class="subject-term-entry">
                                            <span class="subject-term-code"><%=WebSupport.html(term.termCode())%></span>
                                            <small><%=WebSupport.html(term.termName())%></small>
                                        </div>
                                        <% } %>
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
                                            <a href="<%=base%>?mode=view&amp;id=<%=s.subjectId()%>">Xem</a>
                                            <% if(!readOnlyTerm){ %><a href="<%=base%>?mode=edit&amp;id=<%=s.subjectId()%><%=selectedTerm==null?"":"&amp;termId="+selectedTerm%>">Sửa</a><% } %>
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
                                    <td colspan="7" class="empty-state">
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
                        <input type="hidden" name="termId" value="<%=selectedTerm==null?"":selectedTerm%>">
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
</body>
</html>
