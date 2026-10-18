<%@ page contentType="text/html;charset=UTF-8" import="java.util.*,vn.edu.fpt.ams.model.*,vn.edu.fpt.ams.util.WebSupport" %>
<%
    var t=(AssignmentTemplate)request.getAttribute("template");
    boolean create=t==null, read="view".equals(request.getAttribute("mode")), confirm="status".equals(request.getAttribute("mode"));
    boolean primary=Boolean.TRUE.equals(request.getAttribute("primary")),teacher=Boolean.TRUE.equals(request.getAttribute("teacherView"));
    long subject=(Long)request.getAttribute("subjectId");
    var subjects=(Map<Long,String>)request.getAttribute("subjects");
    String token=WebSupport.csrf(request);
    boolean submitted=Boolean.TRUE.equals(request.getAttribute("submitted"));
    String title=submitted?request.getParameter("title"):create?"":t.title();
    String instructions=submitted?request.getParameter("instructions"):create?"":t.instructions();
    String chosenStatus=submitted && request.getParameter("status")!=null ? request.getParameter("status") : create ? "ACTIVE" : t.status().name();
    Object score=submitted?request.getParameter("maximumScore"):create?"10":t.maximumScore().stripTrailingZeros().toPlainString();
%>
<!doctype html>
<html lang="vi">
    <head>
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <meta charset="UTF-8">
        <title>Chi tiết mẫu bài tập | AMS</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/template-detail.css">
        <script defer src="${pageContext.request.contextPath}/js/template-detail.js"></script>
    </head>
    <body class="ams-ui">
        <%@ include file="/WEB-INF/views/shared/shell.jspf" %>
        <main class="ams-content" id="page-content">
            <nav>
                <a href="?subjectId=<%=subject%>">Mẫu bài tập</a>
            </nav>
            <h1>
                <%=create?"Tạo mẫu bài tập":"Chi tiết mẫu bài tập"%>
            </h1>
            <p>
                Môn học: <%=WebSupport.html(subjects.get(subject))%><%=create?"":" · "+vn.edu.fpt.ams.util.Vietnamese.label(t.status())%>
            </p>
            <%
                if(session.getAttribute("notice")!=null){
                %>
                <p role="status">
                    <%=WebSupport.html(vn.edu.fpt.ams.util.Vietnamese.label(session.getAttribute("notice")))%>
                </p>
                <%
                    session.removeAttribute("notice");
                }
                if(request.getAttribute("error")!=null){
                %>
                <p role="alert">
                    <%=WebSupport.html(vn.edu.fpt.ams.util.Vietnamese.label(request.getAttribute("error")))%>
                </p>
                <%
                }
            %>
            <%
                if(confirm){
                %>
                <form method="post">
                    <input type="hidden" name="csrf" value="<%=token%>">
                    <input type="hidden" name="action" value="status">
                    <input type="hidden" name="id" value="<%=t.templateId()%>">
                    <input type="hidden" name="status" value="<%=t.status()==TemplateStatus.ACTIVE?"INACTIVE":"ACTIVE"%>">
                    <p>
                        Xác nhận thay đổi trạng thái của <%=WebSupport.html(t.title())%>. Các bài tập đã tạo giữ nguyên nội dung.
                    </p>
                    <button>
                        <%=t.status()==TemplateStatus.ACTIVE?"Vô hiệu hóa":"Kích hoạt"%> mẫu bài tập
                    </button>
                </form>
                <%
                }
                else {
                %>
                <form method="post">
                    <input type="hidden" name="csrf" value="<%=token%>">
                    <input type="hidden" name="action" value="<%=create?"create":"update"%>">
                    <input type="hidden" name="subjectId" value="<%=subject%>">
                    <%
                        if(!create){
                        %>
                        <input type="hidden" name="id" value="<%=t.templateId()%>">
                        <%
                        }
                    %>
                    <div class="template-detail-grid">
                        <div class="template-detail-column">
                    <label>
                        Tiêu đề
                        <input name="title" required maxlength="200" value="<%=WebSupport.html(title)%>" <%=read?"readonly":""%>>
                    </label>
                    <label>
                        Hướng dẫn
                        <textarea name="instructions" rows="10" required <%=read?"readonly":""%>><%=WebSupport.html(instructions)%></textarea>
                    </label>
                        </div>
                        <div class="template-detail-column">
                    <label>
                        Điểm tối đa
                        <input
                            id="template-maximum-score"
                            name="maximumScore"
                            type="number"
                            required
                            min="1"
                            max="100"
                            step="1"
                            aria-describedby="template-score-hint template-score-error"
                            value="<%=WebSupport.html(score)%>"
                            <%=read?"readonly":""%>>
                        <small id="template-score-hint">Nhập số nguyên từ 1 đến 100.</small>
                        <small id="template-score-error" role="alert" hidden></small>
                    </label>
                    <% if(!create){ %>
                        <fieldset id="template-status" class="template-status">
                            <legend>Trạng thái</legend>
                            <div class="template-status-options">
                                <% for(var state:TemplateStatus.values()){ %>
                                    <label>
                                        <input type="radio" name="status" value="<%=state.name()%>"
                                            <%=state.name().equals(chosenStatus)?"checked":""%>
                                            <%=read||!primary?"disabled":""%> required>
                                        <%=vn.edu.fpt.ams.util.Vietnamese.label(state)%>
                                    </label>
                                <% } %>
                            </div>
                            <% if(!primary&&!teacher){ %>
                                <small>Chỉ chuyên gia chính được thay đổi trạng thái mẫu.</small>
                            <% } %>
                        </fieldset>
                    <% } %>
                        </div>
                    </div>
                    <%
                        if(!read){
                        %>
                        <div class="template-detail-actions">
                            <button type="submit" class="ams-button ams-button-primary">Lưu mẫu bài tập</button>
                        </div>
                        <%
                        }
                    %>
                </form>
                <%
                    if(read&&!teacher){
                    %>
                    <p>
                        <a href="?mode=edit&amp;id=<%=t.templateId()%>">Sửa nội dung</a>

                    </p>
                    <%
                    }
                }
            %>
        </main>
    </div>
</body>
</html>
