<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*" %>
<%@ page import="vn.edu.fpt.ams.util.AssignmentDetailView" %>
<%@ page import="static vn.edu.fpt.ams.util.AssignmentListView.*" %>
<%
    AssignmentDetailPage data = (AssignmentDetailPage) request.getAttribute("assignmentDetail");
    AssignmentListFilter filters = (AssignmentListFilter) request.getAttribute("assignmentFilters");
    if (filters == null) filters = AssignmentListFilter.from(null, null, null, null);
    String error = (String) request.getAttribute("assignmentDetailError");
    String base = request.getContextPath();
    String listUrl = base + "/teacher/assignments";
    String backUrl = data == null ? listUrl : AssignmentDetailView.listUrl(base, data.assignment().classId(), filters);
%>
<!doctype html>
<html lang="en"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title><%= data == null ? "Assignment Details" : escape(data.assignment().title()) %> | AMS</title>
    <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-list.css">
    <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-detail.css">
    <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
    <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-lifecycle.css">
</head><body class="ams-ui teacher-app">
<%@ include file="/WEB-INF/views/shared/shell.jspf" %>
<main class="ams-content" id="page-content">
            <nav class="breadcrumb" aria-label="Breadcrumb"><a href="<%= escape(base) %>/teacher/dashboard">Dashboard</a><span aria-hidden="true">/</span><a href="<%= escape(backUrl) %>">Assignments</a><span aria-hidden="true">/</span><span aria-current="page">Details</span></nav>
            <% if (error != null) { %>
            <section class="notice error" role="alert"><h1>Unable to load assignment</h1><p><%= escape(error) %></p><a href="">Try again</a> · <a href="<%= escape(backUrl) %>">Back to list</a></section>
            <% } else if (data == null) { %>
            <section class="empty-state"><h1>Assignment unavailable</h1><p>This assignment could not be loaded.</p><a class="ams-button back-button" href="<%= escape(backUrl) %>">Back to list</a></section>
            <% } else {
                Assignment assignment = data.assignment();
                var checklist = AssignmentDetailView.checklist(assignment.taskChecklistJson());
                String actionUrl = backUrl + "&assignmentId=" + assignment.assignmentId();
            %>
            <% if (Long.valueOf(assignment.assignmentId()).equals(session.getAttribute("createdAssignmentId"))) {
                session.removeAttribute("createdAssignmentId"); %>
            <p class="state-message" role="status">Assignment created successfully as Draft.</p>
            <% } %>
            <% if(Long.valueOf(assignment.assignmentId()).equals(session.getAttribute("updatedAssignmentId"))) {
                session.removeAttribute("updatedAssignmentId"); %>
            <p class="state-message" role="status">Changes saved successfully. This assignment remains a Draft.</p>
            <% } %>
            <% if (Long.valueOf(assignment.assignmentId()).equals(session.getAttribute("publishedAssignmentId"))) {
                session.removeAttribute("publishedAssignmentId"); %>
            <p class="state-message" role="status"><%= escape("Assignment published successfully. Its content and settings are now frozen. Eligible students can start during its availability window.") %></p>
            <% } %>
            <% if (Long.valueOf(assignment.assignmentId()).equals(session.getAttribute("cancelledAssignmentId"))) {
                session.removeAttribute("cancelledAssignmentId"); %>
            <p class="state-message" role="status"><%= escape("Assignment cancelled successfully. Students can no longer start or continue attempts. In-progress attempts are void; submitted attempts and results are preserved.") %></p>
            <% } %>
            <div class="page-heading">
                <div><p class="eyebrow">Assignment details</p><h1><%= escape(assignment.title()) %></h1><p><%= escape(data.classCode()) %> — <%= escape(data.className()) %> · <%= escape(data.subjectCode()) %> · <%= escape(data.termCode()) %></p></div>
                <div class="detail-actions"><span class="badge <%= assignment.status().name().toLowerCase(java.util.Locale.ROOT) %>"><%= label(assignment.status()) %></span><% if(assignment.status()==AssignmentStatus.DRAFT){ %><a class="ams-button" href="<%= escape(actionUrl) %>&amp;mode=edit">Edit Assignment</a><a class="ams-button ams-button-primary" href="<%= escape(actionUrl) %>&amp;mode=publish">Publish Assignment</a><% } if(assignment.status()==AssignmentStatus.DRAFT || assignment.status()==AssignmentStatus.PUBLISHED){ %><a class="ams-button" href="<%= escape(actionUrl) %>&amp;mode=cancel">Cancel Assignment</a><% } %><a class="ams-button" href="<%= escape(base) %>/teacher/submissions?assignmentId=<%= assignment.assignmentId() %>">View submissions</a><a class="ams-button" href="<%= escape(backUrl) %>">Back to list</a></div>
            </div>
            <% if (assignment.status() == AssignmentStatus.CANCELLED) { %><p class="state-message">This assignment has been cancelled. Students cannot start or continue attempts. In-progress attempts are void; submitted attempts and results are preserved. Its content is retained for reference.</p><% } %>
            <div class="detail-layout">
                <div class="detail-content">
                    <section class="detail-card" aria-labelledby="instructions-heading">
                        <h2 id="instructions-heading">Assignment content</h2>
                        <h3>Instructions</h3>
                        <% if (assignment.instructions() == null || assignment.instructions().isBlank()) { %><p>No instructions have been added yet.</p>
                        <% } else { %><div class="content-text"><%= escape(assignment.instructions()) %></div><% } %>
                        <% if (assignment.responseType() == ResponseType.ESSAY) { %>
                        <% if (assignment.scopeText() != null && !assignment.scopeText().isBlank()) { %>
                        <h3>Scope</h3><div class="content-text"><%= escape(assignment.scopeText()) %></div><% } %>
                        <% if (!checklist.items().isEmpty()) { %>
                        <h3>Tasks to complete</h3><ul class="task-list"><% for (String task : checklist.items()) { %><li class="content-text"><%= escape(task) %></li><% } %></ul><% } %>
                        <% if (checklist.invalid()) { %><p class="content-warning" role="alert">Some task content could not be displayed because its saved format is invalid.</p><% } %>
                        <% } %>
                    </section>
                    <% if (assignment.responseType() == ResponseType.OBJECTIVE) { %>
                    <section class="detail-card" aria-labelledby="questions-heading">
                        <h2 id="questions-heading">Questions (<%= data.questions().size() %>)</h2>
                        <p class="section-description">Correct answers are shown in this Teacher view.</p>
                        <% if (data.questions().isEmpty()) { %><p class="empty-content">No questions have been added yet.</p><% } %>
                        <% for (Question question : data.questions()) { var options = AssignmentDetailView.options(question.options()); int displayOption=0; %>
                        <article class="question-block">
                            <div class="question-heading"><h3>Question <%= question.questionOrder() %></h3><span><%= escape(question.maximumPoints().stripTrailingZeros().toPlainString()) %> points</span></div>
                            <div class="content-text"><%= escape(question.questionText()) %></div>
                            <ul class="option-list"><% for (var option : options.items()) { %>
                                <li class="option <%= option.correct() ? "correct-option" : "" %>"><span class="option-label"><%= ++displayOption %>.</span><span class="content-text"><%= escape(option.text()) %></span><% if (option.correct()) { %><span class="correct-label">Correct answer</span><% } %></li>
                            <% } %></ul>
                            <% if (options.invalid()) { %><p class="content-warning" role="alert">Some answer options could not be displayed because their saved format is invalid.</p>
                            <% } else if (options.items().isEmpty()) { %><p class="empty-content">No answer options have been added yet.</p><% } %>
                        </article><% } %>
                    </section><% } %>
                </div>
                <aside class="detail-summary" aria-label="Assignment information">
                    <section class="detail-card"><h2>Overview</h2><dl class="metadata">
                        <dt>Class</dt><dd><%= escape(data.classCode()) %> — <%= escape(data.className()) %></dd>
                        <dt>Subject</dt><dd><%= escape(data.subjectCode()) %> — <%= escape(data.subjectName()) %></dd>
                        <dt>Academic term</dt><dd><%= escape(data.termCode()) %></dd>
                        <dt>Purpose</dt><dd><%= label(assignment.assignmentPurpose()) %></dd>
                        <dt>Response type</dt><dd><%= label(assignment.responseType()) %></dd>
                        <dt>Maximum score</dt><dd><%= escape(assignment.maximumScore().stripTrailingZeros().toPlainString()) %> points</dd>
                        <dt>Assignment template</dt><dd><%= escape(data.templateTitle()) %></dd>
                    </dl></section>
                    <section class="detail-card"><h2>Timing &amp; limits</h2><p class="section-description">All times use Vietnam time (UTC+07).</p><dl class="metadata">
                        <dt>Available from</dt><dd><%= deadline(assignment.availableFrom()) %></dd>
                        <dt>Due at</dt><dd><%= deadline(assignment.dueAt()) %></dd>
                        <dt>Time limit</dt><dd><%= assignment.timeLimitMinutes() == null ? "Not set" : assignment.timeLimitMinutes() + " minutes per attempt" %></dd>
                        <% if (assignment.assignmentPurpose() == AssignmentPurpose.PRACTICE) { %><dt>Attempt limit</dt><dd><%= assignment.attemptLimit() == null ? "Not set" : assignment.attemptLimit() + " attempts" %></dd><% } %>
                        <% if (assignment.publishedAt() != null) { %><dt>Published at</dt><dd><%= deadline(assignment.publishedAt()) %></dd><% } %>
                        <% if (assignment.cancelledAt() != null) { %><dt>Cancelled at</dt><dd><%= deadline(assignment.cancelledAt()) %></dd><% } %>
                    </dl></section>
                </aside>
            </div>
            <% } %>
</main></div>
</body></html>
