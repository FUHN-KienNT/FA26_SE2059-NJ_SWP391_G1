<%@ page contentType="text/html;charset=UTF-8" import="java.util.*,vn.edu.fpt.ams.util.WebSupport" %>
<!doctype html>
<html lang="vi">
    <head>
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <meta charset="UTF-8">
        <title>Bảng điều khiển quản trị | AMS</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
    </head>
    <body class="ams-ui">
        <%@ include file="/WEB-INF/views/admin/shell.jspf" %>
        <main class="ams-content" id="page-content">
            <h1>
                Bảng điều khiển quản trị
            </h1>
            <p>
                Học kỳ hiện tại: <%=WebSupport.html(vn.edu.fpt.ams.util.Vietnamese.label(request.getAttribute("currentTerm")))%>
            </p>
            <div class="ams-metrics">
                <%
                    for(var entry:((Map<String,Long>)request.getAttribute("summary")).entrySet()){
                    %>
                    <article class="ams-metric">
                        <span><%=WebSupport.html(vn.edu.fpt.ams.util.Vietnamese.label(entry.getKey()))%></span><strong><%=entry.getValue()%></strong>
                    </article>
                    <%
                    }
                %>
            </div>
        </main>
    </div>
</body>
</html>
