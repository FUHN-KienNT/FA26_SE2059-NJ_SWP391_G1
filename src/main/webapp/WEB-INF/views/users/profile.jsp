<%@ page contentType="text/html;charset=UTF-8" import="vn.edu.fpt.ams.model.UserAccount,vn.edu.fpt.ams.util.*" %>
<%
    var profile=(UserAccount)request.getAttribute("profile");
    boolean studentProfile = profile.role() == vn.edu.fpt.ams.model.UserRole.STUDENT;
%>
<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Hồ sơ cá nhân | AMS</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/user-profile.css">
    </head>
    <body class="ams-ui">
        <%@ include file="/WEB-INF/views/shared/shell.jspf" %>
        <main class="ams-content" id="page-content">
            <h1>
                Hồ sơ cá nhân
            </h1>
            <%
                if(request.getAttribute("error")!=null){
                %>
                <p role="alert">
                    <%=WebSupport.html(request.getAttribute("error"))%>
                </p>
                <%
                }
            %>
            <%
                if(session.getAttribute("notice")!=null){
                %>
                <p role="status">
                    <%=WebSupport.html(session.getAttribute("notice"))%>
                </p>
                <%
                    session.removeAttribute("notice");
                }
            %>
            <div class="user-profile-grid <%=studentProfile ? "student-profile-grid" : ""%>">
                <section class="ams-panel">
                    <h2>
                        Thông tin cá nhân
                    </h2>
                    <form method="post">
                        <input type="hidden" name="csrf" value="<%=WebSupport.csrf(request)%>">
                        <input type="hidden" name="action" value="profile">
                        <label>
                            Họ tên
                            <input
                                name="fullName"
                                maxlength="50" required
                                value="<%=WebSupport.html("profile".equals(request.getParameter("action"))?request.getParameter("fullName"):profile.fullName())%>">
                        </label>
                        <label>
                            Email
                            <input type="email" value="<%=WebSupport.html(profile.email())%>" readonly>
                        </label>
                        <label>
                            Vai trò
                            <input value="<%=WebSupport.html(Vietnamese.label(profile.role()))%>" readonly>
                        </label>
                        <button type="submit">
                            Lưu hồ sơ
                        </button>
                    </form>
                </section>
                <% if (!studentProfile) { %>
                <section class="ams-panel">
                    <h2>
                        Đổi mật khẩu
                    </h2>
                    <form method="post">
                        <input type="hidden" name="csrf" value="<%=WebSupport.csrf(request)%>">
                        <input type="hidden" name="action" value="password">
                        <label>
                            Mật khẩu hiện tại
                            <input type="password" name="currentPassword" maxlength="1024" required autocomplete="current-password">
                        </label>
                        <label>
                            Mật khẩu mới
                            <input type="password" name="password" minlength="8" maxlength="128" required autocomplete="new-password">
                        </label>
                        <label>
                            Xác nhận mật khẩu mới
                            <input type="password" name="confirmation" minlength="8" maxlength="128" required autocomplete="new-password">
                        </label>
                        <p class="muted">
                            8–128 ký tự, có chữ hoa, chữ thường, chữ số và không chứa khoảng trắng. Sau khi đổi, bạn cần đăng nhập lại.
                        </p>
                        <button type="submit">
                            Đổi mật khẩu
                        </button>
                    </form>
                </section>
                <% } else { %>
                    <p class="muted">Bạn đăng nhập bằng Google. Mật khẩu được quản lý trong tài khoản Google của bạn.</p>
                <% } %>
            </div>
        </main>
    </div>
</body>
</html>
