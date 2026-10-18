<%@ page contentType="text/html;charset=UTF-8" import="java.util.*,vn.edu.fpt.ams.model.*,vn.edu.fpt.ams.util.WebSupport,vn.edu.fpt.ams.util.Vietnamese" %>
<%
    var users=(List<UserAccount>)request.getAttribute("users");
    int number=(Integer)request.getAttribute("page");
    long total=(Long)request.getAttribute("total"), pages=Math.max(1,(total+19)/20);
    String base=request.getContextPath()+"/admin/users", token=WebSupport.csrf(request);
%>
<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Danh sách tài khoản | AMS</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
    </head>
    <body class="admin-users ams-ui">
        <%@ include file="/WEB-INF/views/admin/shell.jspf" %>
        <main class="users-main ams-content" id="page-content">
            <h1>
                Danh sách tài khoản
            </h1>
            <p class="muted">
                Quản trị viên, chuyên gia và giảng viên dùng tài khoản nội bộ. Sinh viên đăng nhập bằng Google.
            </p>
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
                <form method="get" id="user-filters" data-live-search>
                    <label>
                        <span class="sr-only">Tên hoặc email</span>
                        <input
                            name="keyword"
                            maxlength="50"
                            placeholder="Tìm theo tên hoặc email…"
                            value="<%=WebSupport.html(request.getParameter("keyword"))%>">
                    </label>
                    <label>
                        <span class="sr-only">Vai trò</span>
                        <select name="role">
                            <option value="">
                                Tất cả vai trò
                            </option>
                            <%
                                for(var role:UserRole.values()){
                                %>
                                <option value="<%=role.name()%>" <%=role.name().equals(request.getParameter("role"))?"selected":""%>>
                                    <%=Vietnamese.label(role)%>
                                </option>
                                <%
                                }
                            %>
                        </select>
                    </label>
                    <label>
                        <span class="sr-only">Trạng thái</span>
                        <select name="status">
                            <option value="">
                                Tất cả trạng thái
                            </option>
                            <%
                                for(var status:AccountStatus.values()){
                                %>
                                <option value="<%=status.name()%>" <%=status.name().equals(request.getParameter("status"))?"selected":""%>>
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
                <a class="primary-button" href="<%=base%>?mode=create">+ Thêm tài khoản</a>
            </div>
            <div class="users-table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th>
                                Họ tên
                            </th>
                            <th>
                                Email
                            </th>
                            <th>
                                Vai trò
                            </th>
                            <th>
                                Đăng nhập
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
                            for(var u:users){
                                boolean active=u.accountStatus()==AccountStatus.ACTIVE;
                            %>
                            <tr>
                                <td>
                                    <a href="<%=base%>?mode=view&amp;id=<%=u.userId()%>"><%=WebSupport.html(u.fullName())%></a>
                                </td>
                                <td>
                                    <%=WebSupport.html(u.email())%>
                                </td>
                                <td>
                                    <%=Vietnamese.label(u.role())%>
                                </td>
                                <td>
                                    <%=u.role()==UserRole.STUDENT?"Google":"Nội bộ"%>
                                </td>
                                <td>
                                    <span class="status-badge <%=active?"active":"inactive"%>"><%=Vietnamese.label(u.accountStatus())%></span>
                                </td>
                                <td>
                                    <div class="row-actions">
                                        <a href="<%=base%>?mode=edit&amp;id=<%=u.userId()%>">Sửa</a>
                                        <%
                                            if(u.userId()!=WebSupport.actor(request)){
                                            %>
                                            <form
                                                method="post"
                                                action="<%=base%>"
                                                class="status-action"
                                                data-confirm="<%=active?"Vô hiệu hóa tài khoản này? Các phân công và đề nghị liên quan sẽ được kiểm tra.":"Kích hoạt lại tài khoản này? Các phân công cũ không tự khôi phục."%>">
                                                <input type="hidden" name="csrf" value="<%=token%>">
                                                <input type="hidden" name="id" value="<%=u.userId()%>">
                                                <input type="hidden" name="action" value="status">
                                                <input type="hidden" name="returnTo" value="list">
                                                <input type="hidden" name="status" value="<%=active?"INACTIVE":"ACTIVE"%>">
                                                <button>
                                                    <%=active?"Vô hiệu hóa":"Kích hoạt"%>
                                                </button>
                                            </form>
                                            <%
                                            }
                                        %>
                                    </div>
                                </td>
                            </tr>
                            <%
                            }
                        %>
                        <%
                            if(users.isEmpty()){
                            %>
                            <tr>
                                <td colspan="6" class="empty-state">
                                    Không có tài khoản phù hợp.
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
                    Hiển thị <%=users.isEmpty()?0:(number-1)*20+1%>–<%=users.isEmpty()?0:(number-1)*20+users.size()%> trong <%=total%> tài khoản · 20 tài khoản mỗi trang
                </p>
                <form method="get" class="pagination" aria-label="Phân trang">
                    <input type="hidden" name="keyword" value="<%=WebSupport.html(request.getParameter("keyword"))%>">
                    <input type="hidden" name="role" value="<%=WebSupport.html(request.getParameter("role"))%>">
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
        </main>
    </div>
</body>
</html>
