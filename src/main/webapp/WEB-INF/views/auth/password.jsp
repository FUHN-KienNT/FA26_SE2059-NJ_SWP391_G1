<%@ page contentType="text/html;charset=UTF-8" import="vn.edu.fpt.ams.util.WebSupport" %>
<%
    boolean reset=Boolean.TRUE.equals(request.getAttribute("resetMode"));
%>
<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title><%=reset?"Đặt lại mật khẩu":"Quên mật khẩu"%> | AMS</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
    </head>
    <body class="ams-ui ams-auth">
        <main>
            <h1>
                <%=reset?"Đặt lại mật khẩu":"Quên mật khẩu"%>
            </h1>
            <%
                if(request.getAttribute("error")!=null){
                %>
                <p role="alert">
                    <%=WebSupport.html(request.getAttribute("error"))%>
                </p>
                <%
                }
                if(request.getAttribute("notice")!=null){
                %>
                <p role="status">
                    <%=WebSupport.html(request.getAttribute("notice"))%>
                </p>
                <%
                }
                if(!Boolean.TRUE.equals(request.getAttribute("completed"))){
                %>
                <p>
                    Chức năng dành cho Admin, Chuyên gia và Giảng viên. Sinh viên tiếp tục đăng nhập bằng Google.
                </p>
                <form method="post">
                    <input type="hidden" name="csrf" value="<%=WebSupport.csrf(request)%>">
                    <%
                        if(reset){
                        %>
                        <input type="hidden" name="token" value="<%=WebSupport.html(request.getParameter("token"))%>">
                        <label>
                            Mật khẩu mới
                            <input type="password" name="password" minlength="8" maxlength="128" required autocomplete="new-password">
                        </label>
                        <label>
                            Xác nhận mật khẩu mới
                            <input type="password" name="confirmation" minlength="8" maxlength="128" required autocomplete="new-password">
                        </label>
                        <p class="muted">
                            8–128 ký tự, có chữ hoa, chữ thường, chữ số và không chứa khoảng trắng.
                        </p>
                        <button type="submit">
                            Đặt lại mật khẩu
                        </button>
                        <%
                        }
                        else {
                        %>
                        <label>
                            Email nhân viên
                            <input
                                type="email"
                                name="email"
                                maxlength="100" required
                                autocomplete="email"
                                value="<%=WebSupport.html(request.getParameter("email"))%>">
                        </label>
                        <button type="submit">
                            Gửi liên kết đặt lại mật khẩu
                        </button>
                        <%
                        }
                    %>
                </form>
                <%
                }
            %>
            <p>
                <a href="${pageContext.request.contextPath}/auth/signin">Về trang đăng nhập</a>
            </p>
            <%
                if(reset){
                %>
                <p>
                    <a href="${pageContext.request.contextPath}/auth/forgot-password">Yêu cầu liên kết mới</a>
                </p>
                <%
                }
            %>
        </main>
    </body>
</html>
