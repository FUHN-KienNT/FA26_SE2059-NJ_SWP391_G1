<%@ page contentType="text/html;charset=UTF-8" import="vn.edu.fpt.ams.util.WebSupport" %>
<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Xác nhận email | AMS</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
    </head>
    <body class="ams-ui ams-auth">
        <main>
            <h1>
                Xác nhận email sinh viên
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
                if(request.getAttribute("notice")!=null){
                %>
                <p role="status">
                    <%=WebSupport.html(request.getAttribute("notice"))%>
                </p>
                <%
                }
            %>
            <%
                if(Boolean.TRUE.equals(request.getAttribute("confirmed"))){
                %>
                <p role="status">
                    Email đã được xác nhận. Bạn có thể đăng nhập bằng Google để vào AMS.
                </p>
                <%
                }
                else if(request.getParameter("token")!=null){
                %>
                <p>
                    Bấm xác nhận để hoàn tất đăng ký tài khoản AMS.
                </p>
                <form method="post">
                    <input type="hidden" name="csrf" value="<%=WebSupport.csrf(request)%>">
                    <input type="hidden" name="action" value="confirm">
                    <input type="hidden" name="token" value="<%=WebSupport.html(request.getParameter("token"))%>">
                    <button type="submit">
                        Xác nhận email
                    </button>
                </form>
                <%
                }
                else {
                %>
                <p>
                    Vui lòng mở email xác nhận trước khi đăng nhập vào AMS. Liên kết có hiệu lực 24 giờ. Kiểm tra cả thư mục Spam.
                </p>
                <%
                    if(session.getAttribute("pendingStudentId")!=null){
                    %>
                    <form method="post">
                        <input type="hidden" name="csrf" value="<%=WebSupport.csrf(request)%>">
                        <input type="hidden" name="action" value="resend">
                        <button type="submit">
                            Gửi lại email xác nhận
                        </button>
                    </form>
                    <%
                    }
                %>
                <%
                }
            %>
            <p>
                <a href="${pageContext.request.contextPath}/auth/signin">Về trang đăng nhập</a>
            </p>
        </main>
    </body>
</html>
