<%@ page contentType="text/html;charset=UTF-8" import="vn.edu.fpt.ams.util.WebSupport" %>
<%
    Object errorStatus = request.getAttribute("jakarta.servlet.error.status_code");
    int code = errorStatus instanceof Integer ? (Integer) errorStatus : 400;
    String explanation = "Thông tin gửi lên không hợp lệ. Vui lòng kiểm tra và thử lại.";
    if(code == 401) explanation = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
    else if(code == 403) explanation = "Bạn không có quyền thực hiện thao tác này hoặc biểu mẫu đã hết hạn. Vui lòng tải lại trang.";
    else if(code == 404) explanation = "Không tìm thấy dữ liệu hoặc trang được yêu cầu.";
    else if(code == 405) explanation = "Thao tác này không được hỗ trợ. Vui lòng sử dụng các nút trên giao diện.";
    else if(code == 409) explanation = "Dữ liệu đã thay đổi hoặc bị trùng. Vui lòng kiểm tra lại.";
    else if(code >= 500) explanation = "Không thể xử lý lúc này. Vui lòng thử lại hoặc liên hệ quản trị viên.";
    Object supplied = request.getAttribute("jakarta.servlet.error.message");
    if(code < 500 && supplied instanceof String && !((String)supplied).isBlank()) {
        String translated = vn.edu.fpt.ams.util.Vietnamese.label(supplied);
        if(translated.matches("(?s).*[\\u00C0-\\u1EFF].*")) explanation = translated;
    }
    response.setHeader("Cache-Control", "no-store");
%>
<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Thông báo | AMS</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
        <%@ include file="assets.jspf" %>
    </head>
    <body class="ams-ui ams-auth">
        <main>
            <h1>
                Không thể hoàn tất thao tác
            </h1>
            <p role="alert">
                <%=WebSupport.html(explanation)%>
            </p>
            <p>
                <a class="ams-button" href="${pageContext.request.contextPath}/home">Về trang chính</a>
                <a href="${pageContext.request.contextPath}/auth/signin">Đăng nhập lại</a>
            </p>
        </main>
    </body>
</html>
