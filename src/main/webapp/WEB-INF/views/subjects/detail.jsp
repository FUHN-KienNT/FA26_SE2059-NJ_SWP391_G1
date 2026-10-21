<%@ page contentType="text/html;charset=UTF-8" import="java.util.*,java.time.*,java.time.format.*,vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*,vn.edu.fpt.ams.util.WebSupport,vn.edu.fpt.ams.util.Vietnamese,vn.edu.fpt.ams.util.TermTimeline" %>
<%
    var details=(SubjectDetailsDTO)request.getAttribute("details");
    var s=details.subject();
    var calendarOptions=(List<AcademicTerm>)request.getAttribute("termOptions");
    if(calendarOptions==null) calendarOptions=List.of();
    var calendarById=new HashMap<Long,AcademicTerm>();
    for(var option:calendarOptions) calendarById.put(option.termId(),option);
    var currentCalendar=calendarOptions.stream().filter(AcademicTerm::current).findFirst().orElse(null);
    boolean configuredCurrent=false;
    for(var configured:details.subjectTerms()) if(currentCalendar!=null && configured.termId()==currentCalendar.termId()) configuredCurrent=true;
    Instant calendarNow=Instant.now();
    String base=request.getContextPath()+"/admin/subjects",token=WebSupport.csrf(request);
    var date=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
%>
<!doctype html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Chi tiết <%=WebSupport.html(s.subjectCode())%> | AMS</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/app.css">
        <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
        <%@ include file="/WEB-INF/views/subjects/assets.jspf" %>
    </head>
    <body class="admin-users admin-app admin-subjects ams-ui">
        <%@ include file="/WEB-INF/views/admin/shell.jspf" %>
        <main class="users-main subject-form ams-content bao-mode-page subject-detail-page" id="page-content">
            <nav class="bao-context" aria-label="Ngữ cảnh"><a href="${pageContext.request.contextPath}/admin/subjects">Môn học</a><span aria-hidden="true">›</span><span>Chi tiết</span></nav>
            <div class="subject-heading">
                <div>
                    <h1>
                        Chi tiết môn học
                    </h1>
                    <p class="muted">
                        Thông tin và trạng thái môn học trong hệ thống.
                    </p>
                </div>
                <a class="secondary-button" href="<%=base%>">← Quay lại danh sách</a>
            </div>
            <div class="bao-summary">
                <span class="section-symbol" aria-hidden="true">▤</span>
                <div class="bao-summary-title">
                    <strong><%=WebSupport.html(s.subjectCode())%></strong>
                    <p>
                        <%=WebSupport.html(s.subjectName())%>
                    </p>
                    <span class="status-badge <%=s.status()==SubjectStatus.ACTIVE?"active":"inactive"%>"><%=Vietnamese.label(s.status())%></span>
                </div>
                <div class="bao-summary-dates">
                    <span>Chuyên gia<strong><%=details.primaryExperts().size()+details.supportingExperts().size()%></strong></span><span>Học kỳ đã thiết lập<strong><%=details.subjectTerms().size()%></strong></span>
                </div>
            </div>
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
            <section class="bao-card">
                <div class="subject-offerings-heading">
                    <div><h2>Học kỳ đã thiết lập</h2>
                        <p>Học kỳ hiện tại: <strong><%=currentCalendar==null?"Chưa có học kỳ hiện tại":WebSupport.html(currentCalendar.termCode()+" · "+currentCalendar.termName())%></strong></p>
                        <% if(currentCalendar!=null && !configuredCurrent){ %><p>Môn chưa được mở trong kỳ hiện tại.</p><% } %>
                    </div>
                    <a class="secondary-button" href="<%=base%>?mode=edit&amp;id=<%=s.subjectId()%><%=currentCalendar==null?"":"&amp;termId="+currentCalendar.termId()%>#subject-registration"><%=currentCalendar!=null&&!configuredCurrent?"Mở môn trong kỳ "+WebSupport.html(currentCalendar.termCode()):"Sửa đăng ký theo kỳ"%></a>
                </div>
                <div class="users-table-wrap" role="region" aria-label="Học kỳ và đăng ký" tabindex="0">
                    <table class="subject-offering-table">
                        <thead>
                            <tr>
                                <th scope="col">
                                    Học kỳ
                                </th>
                                <th scope="col">
                                    Trạng thái học kỳ
                                </th>
                                <th scope="col">
                                    Mở đăng ký
                                </th>
                                <th scope="col">
                                    Đóng đăng ký
                                </th>
                                <th scope="col">
                                    Cho phép
                                </th>
                                <th scope="col">
                                    Trạng thái đăng ký
                                </th>
                            </tr>
                        </thead>
                        <tbody>
                            <%
                                for(var term:details.subjectTerms()){
                                    var offeringCalendar=calendarById.get(term.termId());
                                    String calendarLabel=offeringCalendar==null?Vietnamese.registrationStatus(term.registrationStatus()):TermTimeline.label(offeringCalendar,calendarNow);
                                %>
                                <tr>
                                    <td>
                                        <%=WebSupport.html(term.termCode()+" · "+term.termName())%>
                                        <% if(term.currentTerm()){ %><span class="subject-current-badge">Hiện tại</span><% } %>
                                    </td>
                                    <td>
                                        <span class="status-badge <%=offeringCalendar!=null && offeringCalendar.active() && offeringCalendar.endsAt().isAfter(calendarNow)?"active":"inactive"%>"><%=WebSupport.html(calendarLabel)%></span>
                                    </td>
                                    <td>
                                        <%=date.format(term.registrationOpensAt())%>
                                    </td>
                                    <td>
                                        <%=date.format(term.registrationClosesAt())%>
                                    </td>
                                    <td>
                                        <%=term.registrationEnabled()?"Có":"Không"%>
                                    </td>
                                    <td>
                                        <%=WebSupport.html(Vietnamese.registrationStatus(term.registrationStatus()))%>
                                    </td>
                                </tr>
                                <%
                                }
                                if(details.subjectTerms().isEmpty()){
                                %>
                                <tr>
                                    <td colspan="6" class="empty-state">
                                        Môn học chưa được mở trong học kỳ nào.
                                    </td>
                                </tr>
                                <%
                                }
                            %>
                        </tbody>
                    </table>
                </div>
            </section>
            <div class="bao-term-layout subject-detail-layout">
                <section class="bao-card">
                    <h2>
                        Thông tin môn học
                    </h2>
                    <dl class="bao-detail-rows">
                        <div>
                            <dt>
                                Mã môn học
                            </dt>
                            <dd><%=WebSupport.html(s.subjectCode())%></dd>
                        </div>
                        <div>
                            <dt>
                                Tên môn học
                            </dt>
                            <dd><%=WebSupport.html(s.subjectName())%></dd>
                        </div>
                        <div class="wide">
                            <dt>
                                Mô tả
                            </dt>
                            <dd class="subject-description"><%=s.description()==null?"Không có":WebSupport.html(s.description())%></dd>
                        </div>
                    </dl>
                </section>
                <section class="bao-card subject-detail-status">
                    <h2>
                        Trạng thái
                    </h2>
                    <dl class="bao-state-list">
                        <div>
                            <dt>
                                Trạng thái
                            </dt>
                            <dd>
                                <span class="status-badge <%=s.status()==SubjectStatus.ACTIVE?"active":"inactive"%>"><%=Vietnamese.label(s.status())%></span>
                            </dd>
                        </div>
                    </dl>
                </section>
            </div>
            <section class="bao-card">
                <h2>
                    Phân công chuyên gia
                </h2>
                <div class="expert-grid">
                    <div>
                        <h3>
                            Chuyên gia chính
                        </h3>
                        <%
                            if(details.primaryExperts().isEmpty()){
                            %>
                            <p class="muted">
                                Chưa phân công.
                            </p>
                            <%
                            }
                            for(var e:details.primaryExperts()){
                            %>
                            <p>
                                <strong><%=WebSupport.html(e.fullName())%></strong>
                                <br>
                                <small><%=WebSupport.html(e.email())%></small>
                            </p>
                            <%
                            }
                        %>
                    </div>
                    <div>
                        <h3>
                            Chuyên gia hỗ trợ
                        </h3>
                        <%
                            if(details.supportingExperts().isEmpty()){
                            %>
                            <p class="muted">
                                Không có.
                            </p>
                            <%
                            }
                            for(var e:details.supportingExperts()){
                            %>
                            <p>
                                <strong><%=WebSupport.html(e.fullName())%></strong>
                                <br>
                                <small><%=WebSupport.html(e.email())%></small>
                            </p>
                            <%
                            }
                        %>
                    </div>
                </div>
            </section>

            <div class="form-actions">
                <a class="primary-button" href="<%=base%>?mode=edit&amp;id=<%=s.subjectId()%>">Sửa</a>
            </div>
        </main>
    </div>
</body>
</html>
