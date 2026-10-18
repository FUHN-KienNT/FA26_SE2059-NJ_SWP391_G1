<%@ page contentType="text/html;charset=UTF-8" import="java.util.*,vn.edu.fpt.ams.model.*,vn.edu.fpt.ams.util.WebSupport" %>
<%
    var subjects=(Map<Long,String>)request.getAttribute("subjects");
    var templates=(List<AssignmentTemplate>)request.getAttribute("templates");
    long selected=(Long)request.getAttribute("subjectId");
    boolean primary=Boolean.TRUE.equals(request.getAttribute("primary"));
    boolean teacher=Boolean.TRUE.equals(request.getAttribute("teacherView"));
    var pageData=(vn.edu.fpt.ams.dto.TemplatePage)request.getAttribute("templatePage");
%>
<!doctype html>
<html lang="vi">
    <head>
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <meta charset="UTF-8">
        <title>Mẫu bài tập | AMS</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/template-list.css">
    </head>
    <body class="ams-ui">
        <%@ include file="/WEB-INF/views/shared/shell.jspf" %>
        <main class="ams-content" id="page-content">
            <nav>
                <a href="${pageContext.request.contextPath}/home">Trang chủ</a>
            </nav>
            <h1>
                Mẫu bài tập
            </h1>
            <div class="template-toolbar">
            <form method="get" class="template-filters">
                <label>
                    Môn học
                    <select name="subjectId">
                        <%
                            for(var s:subjects.entrySet()){
                            %>
                            <option value="<%=s.getKey()%>" <%=s.getKey()==selected?"selected":""%>>
                                <%=WebSupport.html(s.getValue())%>
                            </option>
                            <%
                            }
                        %>
                    </select>
                </label>
                <label>
                    Tiêu đề
                    <input name="keyword" placeholder="Tìm theo tiêu đề mẫu…" maxlength="200" value="<%=WebSupport.html(request.getParameter("keyword"))%>">
                </label>
                <%
                    if(!teacher){
                    %>
                    <label>
                        Trạng thái
                        <select name="status">
                            <option value="">
                                Tất cả trạng thái
                            </option>
                            <%
                                for(var s:TemplateStatus.values()){
                                %>
                                <option value="<%=s.name()%>" <%=s.name().equals(request.getParameter("status"))?"selected":""%>>
                                    <%=vn.edu.fpt.ams.util.Vietnamese.label(s)%>
                                </option>
                                <%
                                }
                            %>
                        </select>
                    </label>
                    <%
                    }
                %>
                <button>
                    Lọc
                </button>
            </form>
            <%
                if(primary){
                %>
                <p>
                    <a class="ams-button ams-button-primary template-create" href="?mode=create&amp;subjectId=<%=selected%>">+ Tạo mẫu bài tập</a>
                </p>
                <%
                }
            %>
            </div>
            <div class="ams-table-wrap"><table>
                <thead>
                    <tr>
                        <th>
                            Tiêu đề
                        </th>
                        <th>
                            Điểm tối đa
                        </th>
                        <th>
                            Trạng thái
                        </th>
                        <th>
                            Cập nhật (UTC)
                        </th>
                        <th>Thao tác</th>
                    </tr>
                </thead>
                <tbody>
                    <%
                        for(var t:templates){
                        %>
                        <tr>
                            <td>
                                <a href="?mode=view&amp;id=<%=t.templateId()%>"><%=WebSupport.html(t.title())%></a>
                            </td>
                            <td>
                                <%=t.maximumScore()%>
                            </td>
                            <td>
                                <%=vn.edu.fpt.ams.util.Vietnamese.label(t.status())%>
                            </td>
                            <td>
                                <%=WebSupport.html(t.updatedAt())%>
                            </td>
                            <td>
                                <div class="template-actions">
                                    <a class="ams-button" href="?mode=view&amp;id=<%=t.templateId()%>" aria-label="Xem mẫu <%=WebSupport.html(t.title())%>">Xem</a>
                                    <% if(!teacher){ %>
                                        <a class="ams-button" href="?mode=edit&amp;id=<%=t.templateId()%>" aria-label="Sửa mẫu <%=WebSupport.html(t.title())%>">Sửa</a>
                                    <% } %>
                                    <% if(primary){ %>
                                        <a class="ams-button template-status-action" href="?mode=status&amp;id=<%=t.templateId()%>" aria-label="Đổi trạng thái mẫu <%=WebSupport.html(t.title())%>"><%=t.status()==TemplateStatus.ACTIVE?"Vô hiệu hóa":"Kích hoạt"%></a>
                                    <% } %>
                                </div>
                            </td>
                        </tr>
                        <%
                        }
                        if(templates.isEmpty()){
                        %>
                        <tr>
                            <td colspan="5">
                                Không có mẫu bài tập phù hợp.
                            </td>
                        </tr>
                        <%
                        }
                    %>
                </tbody>
            </table>
            </div>
            <div class="template-footer">
                <p class="muted">Hiển thị <%=pageData.total()==0?0:(pageData.page()-1)*pageData.pageSize()+1%>–<%=(pageData.page()-1)*pageData.pageSize()+templates.size()%> trong <%=pageData.total()%> mẫu bài tập · <%=pageData.pageSize()%> mẫu mỗi trang</p>
                <form method="get" class="pagination" aria-label="Phân trang mẫu bài tập">
                    <input type="hidden" name="subjectId" value="<%=selected%>">
                    <input type="hidden" name="keyword" value="<%=WebSupport.html(request.getParameter("keyword"))%>">
                    <input type="hidden" name="status" value="<%=WebSupport.html(request.getParameter("status"))%>">
                    <button name="page" value="<%=pageData.page()-1%>" <%=pageData.page()==1?"disabled":""%>>Trước</button>
                    <% for(long number=Math.max(1,pageData.page()-2); number<=Math.min(pageData.pages(),pageData.page()+2);number++){ %>
                        <button name="page" value="<%=number%>" <%=number==pageData.page()?"aria-current=\"page\"":""%>><%=number%></button>
                    <% } %>
                    <button name="page" value="<%=pageData.page()+1%>" <%=pageData.page()>=pageData.pages()?"disabled":""%>>Sau</button>
                </form>
            </div>
        </main>
    </div>
</body>
</html>
