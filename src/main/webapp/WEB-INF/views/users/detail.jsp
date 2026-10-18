<%@ page contentType="text/html;charset=UTF-8" import="java.util.*,vn.edu.fpt.ams.model.*,vn.edu.fpt.ams.util.WebSupport" %>
<%
    var u=(UserAccount)request.getAttribute("user");
    boolean create=u==null;
    boolean read="view".equals(request.getAttribute("mode"));
    var subjects=(Map<Long,String>)request.getAttribute("subjects");
    var eligible=(Set<Long>)request.getAttribute("eligibility");
    if(eligible==null)eligible=Set.of();
    String token=WebSupport.csrf(request);
    boolean submitted=Boolean.TRUE.equals(request.getAttribute("submitted"));
    String name=submitted?request.getParameter("fullName"):create?"":u.fullName();
    String email=submitted?request.getParameter("email"):create?"":u.email();
    String role=submitted?request.getParameter("role"):create?"TEACHER":u.role().name();
    String selectedStatus=submitted?request.getParameter("status"):create?"ACTIVE":u.accountStatus().name();
    if(submitted)eligible=WebSupport.ids(request.getParameterValues("subjects"));
%>
<!doctype html>
<html lang="vi">
    <head>
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <meta charset="UTF-8">
        <title>Chi tiết tài khoản | AMS</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/user-detail.css">
        <script defer src="${pageContext.request.contextPath}/js/user-detail.js"></script>
    </head>
    <body class="ams-ui">
        <%@ include file="/WEB-INF/views/admin/shell.jspf" %>
        <main class="ams-content" id="page-content">
            <nav>
                <a href="${pageContext.request.contextPath}/admin/users">Tài khoản</a>
            </nav>
            <h1>
                <%=create?"Tạo tài khoản nội bộ":"Chi tiết tài khoản"%>
            </h1>
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
                if(!create){
                %>
                <p>
                    Tài khoản #<%=u.userId()%> · <%=vn.edu.fpt.ams.util.Vietnamese.label(u.accountStatus())%> · Đăng nhập gần nhất: <%=u.lastLoginAt()==null?"Chưa từng":WebSupport.html(u.lastLoginAt())%>
                </p>
                <%
                }
            %>
            <form method="post" id="user-detail-form" data-user-detail>
                <input type="hidden" name="continueSubjectId" value="">
                <input type="hidden" name="csrf" value="<%=token%>">
                <input type="hidden" name="action" value="<%=create?"create":"update"%>">
                <%
                    if(!create){
                    %>
                    <input type="hidden" name="id" value="<%=u.userId()%>">
                    <%
                    }
                %>
                <div class="user-detail-grid">
                    <label>
                        Họ tên
                        <input name="fullName" maxlength="50" required value="<%=WebSupport.html(name)%>" <%=read?"readonly":""%>>
                    </label>
                    <label>
                        Email
                        <input
                            type="email"
                            name="email"
                            maxlength="100"
                            required
                            value="<%=WebSupport.html(email)%>"
                            <%=read||!create&&u.role()==UserRole.STUDENT?"readonly":""%>>
                    </label>
                    <label>
                        Vai trò
                        <select name="role" id="user-role" <%=read?"disabled":""%>>
                            <%
                                if(!create&&u.role()==UserRole.STUDENT){
                                %>
                                <option value="STUDENT">
                                    Sinh viên
                                </option>
                                <%
                                }
                                else for(var r:List.of(UserRole.ADMIN,UserRole.EXPERT,UserRole.TEACHER)){
                                %>
                                <option value="<%=r.name()%>" <%=r.name().equals(role)?"selected":""%>>
                                    <%=vn.edu.fpt.ams.util.Vietnamese.label(r)%>
                                </option>
                                <%
                                }
                            %>
                        </select>
                    </label>
                    <div class="user-status" role="group" aria-labelledby="user-status-label">
                        <span id="user-status-label">Trạng thái</span>
                        <div class="user-status-options">
                            <%
                                for(var accountState:AccountStatus.values()){
                                %>
                                <label class="check">
                                    <input
                                        type="radio"
                                        name="status"
                                        value="<%=accountState.name()%>"
                                        <%=accountState.name().equals(selectedStatus)?"checked":""%>
                                        <%=read||create&&accountState==AccountStatus.INACTIVE?"disabled":""%>
                                        required>
                                    <%=vn.edu.fpt.ams.util.Vietnamese.label(accountState)%>
                                </label>
                                <%
                                }
                            %>
                        </div>
                        <%
                            if(create){
                            %>
                            <small>Tài khoản mới được tạo ở trạng thái hoạt động.</small>
                            <%
                            }
                            else if(!read){
                            %>
                            <small>Vô hiệu hóa sẽ kết thúc phân công liên quan. Kích hoạt lại không tự khôi phục phân công.</small>
                            <%
                            }
                        %>
                    </div>
                </div>
                <fieldset id="teaching-eligibility" <%= "TEACHER".equals(role)?"":"hidden disabled" %>>
                    <legend>
                        Môn học đủ điều kiện giảng dạy
                    </legend>
                    <p>
                        Chỉ chọn môn cho giảng viên. Không thể bỏ điều kiện giảng dạy khi đang phụ trách lớp hoạt động.
                    </p>
                    <label class="eligibility-search-label" for="eligibility-search">
                        Tìm môn học
                    </label>
                    <input
                        type="search"
                        id="eligibility-search"
                        placeholder="Nhập mã hoặc tên môn học…"
                        autocomplete="off"
                        aria-controls="eligibility-subjects">
                    <div id="eligibility-subjects" class="eligibility-subjects">
                        <%
                            for(var s:subjects.entrySet()){
                            %>
                            <label class="check eligibility-subject">
                                <input
                                    type="checkbox"
                                    name="subjects"
                                    value="<%=s.getKey()%>"
                                    <%=eligible.contains(s.getKey())?"checked":""%>
                                    <%=read?"disabled":""%>>
                                <span><%=WebSupport.html(s.getValue())%></span>
                            </label>
                            <%
                            }
                        %>
                    </div>
                    <p class="eligibility-empty muted" hidden>
                        Không tìm thấy môn học phù hợp.
                    </p>
                </fieldset>
                <section id="expert-subjects" class="ams-panel" <%= "EXPERT".equals(role)?"":"hidden" %>>
                    <h2>
                        Môn học phụ trách
                    </h2>
                    <p class="muted">
                        Hiển thị tất cả môn học. Chọn Phân công tại môn chưa phụ trách để mở màn quản lý phân công chuyên gia.
                    </p>
                    <%
                        var expertSubjects=(java.util.List<vn.edu.fpt.ams.dto.ExpertSubjectDTO>)request.getAttribute("expertSubjects");
                        if(expertSubjects==null||expertSubjects.isEmpty()){
                        %>
                        <p>
                            Chưa có môn học trong hệ thống.
                        </p>
                        <%
                        }
                        else {
                        %>
                        <label for="expert-subject-search">Tìm môn học</label>
                        <input type="search" id="expert-subject-search" placeholder="Nhập mã hoặc tên môn học…" aria-controls="expert-subject-table" autocomplete="off">
                        <% if(create){ %>
                            <p class="muted">Lưu tài khoản trước để có thể phân công môn học.</p>
                        <% } %>
                        <div class="ams-table-wrap expert-subject-scroll" tabindex="0" role="region" aria-label="Danh sách môn học phụ trách">
                            <table id="expert-subject-table">
                                <thead>
                                    <tr>
                                        <th>
                                            Mã môn
                                        </th>
                                        <th>
                                            Tên môn
                                        </th>
                                        <th>
                                            Vai trò chuyên gia
                                        </th>
                                        <th>
                                            Trạng thái phân công
                                        </th>
                                        <th>
                                            Thao tác
                                        </th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <%
                                        for(var assignment:expertSubjects){
                                        %>
                                        <tr data-expert-subject>
                                            <td>
                                                <%=WebSupport.html(assignment.code())%>
                                            </td>
                                            <td>
                                                <%=WebSupport.html(assignment.name())%>
                                            </td>
                                            <td>
                                                <%=assignment.responsibility()==null?"—":assignment.responsibility()==ResponsibilityType.PRIMARY?"Chuyên gia chính":"Chuyên gia hỗ trợ"%>
                                            </td>
                                            <td>
                                                <span class="status-badge <%=assignment.active()?"active":"inactive"%>"><%=assignment.responsibility()==null?"Chưa phân công":assignment.active()?"Đang phụ trách":"Đã kết thúc"%></span>
                                            </td>
                                            <td>
                                                <% if(create){ %>
                                                    <span class="muted">Lưu tài khoản trước</span>
                                                <% } else { %>
                                                    <a data-manage-assignment data-subject-id="<%=assignment.subjectId()%>" href="${pageContext.request.contextPath}/admin/subjects?mode=experts&amp;id=<%=assignment.subjectId()%>&amp;returnUserId=<%=u.userId()%>"><%=assignment.active()?"Quản lý phân công":"Phân công"%></a>
                                                <% } %>
                                            </td>
                                        </tr>
                                        <%
                                        }
                                    %>
                                    <tr id="expert-subject-empty" hidden><td colspan="5">Không tìm thấy môn học phù hợp.</td></tr>
                                </tbody>
                            </table>
                        </div>
                        <%
                        }
                    %>
                </section>
            </form>
            <%
                if(!create){
                    if(read){
                    %>
                    <p>
                        <a href="?mode=edit&amp;id=<%=u.userId()%>">Sửa tài khoản</a>
                    </p>
                    <%
                    }
                    else {
                    %>
                    <%
                        if(u.role()!=UserRole.STUDENT&&u.accountStatus()==AccountStatus.ACTIVE){
                        %>
                        <form method="post">
                            <input type="hidden" name="csrf" value="<%=token%>">
                            <input type="hidden" name="id" value="<%=u.userId()%>">
                            <input type="hidden" name="action" value="credential">
                            <p>
                                Cấp lại sẽ thay mật khẩu hiện tại và gửi thông tin đăng nhập mới qua email.
                            </p>
                            <label class="check">
                                <input type="checkbox" required>
                                Xác nhận thay mật khẩu
                            </label>
                            <button>
                                Cấp lại mật khẩu
                            </button>
                        </form>
                        <%
                        }
                    }
                }
            %>
            <%
                if(!read){
                %>
                <div class="user-detail-footer">
                    <a class="ams-button" href="${pageContext.request.contextPath}/admin/users">Hủy</a>
                    <button type="submit" class="ams-button ams-button-primary" form="user-detail-form">
                        <%=create?"Tạo và gửi thông tin đăng nhập":"Lưu thay đổi"%>
                    </button>
                </div>
                <%
                }
            %>
            <dialog id="unsaved-account-dialog" aria-labelledby="unsaved-account-title">
                <h2 id="unsaved-account-title">
                    Bạn có thay đổi chưa lưu
                </h2>
                <p>
                    Lưu thông tin tài khoản trước khi chuyển sang quản lý phân công?
                </p>
                <div class="unsaved-actions">
                    <button type="button" data-unsaved="save" class="ams-button ams-button-primary">
                        Lưu và tiếp tục
                    </button>
                    <button type="button" data-unsaved="discard" class="ams-button">
                        Bỏ thay đổi và tiếp tục
                    </button>
                    <button type="button" data-unsaved="stay" class="ams-button">
                        Ở lại
                    </button>
                </div>
            </dialog>
        </main>
    </div>
</body>
</html>
