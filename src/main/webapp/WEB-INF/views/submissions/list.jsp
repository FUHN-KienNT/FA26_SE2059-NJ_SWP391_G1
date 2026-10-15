<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*" %>
<%@ page import="vn.edu.fpt.ams.util.TeacherSubmissionView" %>
<%@ page import="static vn.edu.fpt.ams.util.AssignmentListView.*" %>
<%
    TeacherSubmissionListPage data = (TeacherSubmissionListPage) request.getAttribute("submissionPage");
    TeacherSubmissionFilter filters = data == null
            ? (TeacherSubmissionFilter) request.getAttribute("submissionFilters") : data.filter();
    if (filters == null) filters = TeacherSubmissionFilter.from(null, null);
    String error = (String) request.getAttribute("submissionError");
    String base = request.getContextPath();
    Long assignmentId = data == null ? (Long) request.getAttribute("selectedAssignmentId")
            : data.assignment().assignment().assignmentId();
    String backUrl = assignmentId == null ? base + "/teacher/assignments"
            : base + "/teacher/assignments?mode=view&assignmentId=" + assignmentId;
    String retryUrl = assignmentId == null ? base + "/teacher/assignments"
            : TeacherSubmissionView.listUrl(base, assignmentId, filters);
%>
<!doctype html>
<html lang="en"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Submissions | AMS</title>
    <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-list.css">
    <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
    <link rel="stylesheet" href="<%= escape(base) %>/css/submissions.css">
</head><body class="ams-ui teacher-app">
<%@ include file="/WEB-INF/views/shared/shell.jspf" %>
<main class="ams-content submission-page" id="page-content">
    <nav class="breadcrumb" aria-label="Breadcrumb"><a href="<%= escape(base) %>/teacher/dashboard">Dashboard</a><span aria-hidden="true">/</span><a href="<%= escape(backUrl) %>">Assignment details</a><span aria-hidden="true">/</span><span aria-current="page">Submissions</span></nav>
    <div class="page-heading"><div><h1>Submissions</h1><p>Review student attempts for this assignment.</p></div><a class="button" href="<%= escape(backUrl) %>">Back to assignment</a></div>
    <% if (error != null) { %>
    <section class="notice error" role="alert"><h2>Unable to load submissions</h2><p><%= escape(error) %></p><a href="<%= escape(retryUrl) %>">Try again</a></section>
    <% } else if (data == null) { %>
    <section class="empty-state"><h2>Submissions unavailable</h2><p>This assignment's submissions could not be loaded.</p></section>
    <% } else {
        Assignment assignment = data.assignment().assignment();
    %>
    <section class="submission-card assignment-context" aria-labelledby="assignment-context-heading">
        <div class="context-heading"><h2 id="assignment-context-heading"><%= escape(assignment.title()) %></h2><span class="badge <%= assignment.status().name().toLowerCase(java.util.Locale.ROOT) %>"><%= label(assignment.status()) %></span></div>
        <dl class="context-grid"><div><dt>Class</dt><dd><%= escape(data.assignment().classCode()) %> — <%= escape(data.assignment().className()) %></dd></div><div><dt>Purpose</dt><dd><%= label(assignment.assignmentPurpose()) %></dd></div><div><dt>Response type</dt><dd><%= label(assignment.responseType()) %></dd></div><div><dt>Maximum score</dt><dd><%= escape(TeacherSubmissionView.score(assignment.maximumScore())) %> points</dd></div></dl>
        <% if (assignment.status() == AssignmentStatus.CANCELLED) { %><p class="submission-note">This assignment is cancelled. Previous attempts and results remain available for reference.</p><% } %>
    </section>
    <form class="filters submission-filters" method="get" action="<%= escape(base) %>/teacher/submissions">
        <input type="hidden" name="assignmentId" value="<%= assignment.assignmentId() %>">
        <label for="search">Search student<input type="search" id="search" name="search" maxlength="200" value="<%= escape(filters.search()) %>" placeholder="Student name or email…"></label>
        <label for="status">Status<select id="status" name="status"><option value="">All</option><% for (SubmissionStatus value : SubmissionStatus.values()) { %><option value="<%= value.name() %>" <%= value == filters.status() ? "selected" : "" %>><%= escape(TeacherSubmissionView.statusLabel(value)) %></option><% } %></select></label>
        <div class="filter-actions"><button type="submit" class="button primary">Apply filters</button><a class="button" href="<%= escape(base) %>/teacher/submissions?assignmentId=<%= assignment.assignmentId() %>">Reset</a></div>
    </form>
    <div class="table-wrap submission-table"><table>
        <caption class="sr-only">Student attempts for <%= escape(assignment.title()) %>. Submission times use Vietnam time, UTC+07.</caption>
        <thead><tr><th scope="col">Student</th><th scope="col">Attempt</th><th scope="col">Submitted at <small>(UTC+07)</small></th><th scope="col">Status</th><th scope="col">Score</th><th scope="col">Actions</th></tr></thead>
        <tbody><% for (TeacherSubmissionRow row : data.submissions()) { Submission submission = row.submission(); %>
        <tr><td class="student-cell"><strong><%= escape(row.studentName()) %></strong><span><%= escape(row.studentEmail()) %></span></td><td><%= submission.attemptNumber() %></td><td class="deadline"><%= escape(TeacherSubmissionView.submittedAt(submission.submittedAt())) %></td><td><span class="badge submission-status <%= submission.status().name().toLowerCase(java.util.Locale.ROOT) %>"><%= escape(TeacherSubmissionView.statusLabel(submission.status())) %></span></td><td class="score-cell"><% if (submission.score() == null) { %>Not graded<% } else { %><%= escape(TeacherSubmissionView.score(submission.score())) %> / <%= escape(TeacherSubmissionView.score(assignment.maximumScore())) %><% } %></td><td><a href="<%= escape(TeacherSubmissionView.detailUrl(base, assignment.assignmentId(), submission.submissionId(), filters)) %>" aria-label="View submission from <%= escape(row.studentName()) %>, attempt <%= submission.attemptNumber() %>">View</a></td></tr>
        <% } if (data.submissions().isEmpty()) { boolean filtered = !filters.search().isBlank() || filters.status() != null; %>
        <tr><td colspan="6" class="empty-results"><strong><%= filtered ? "No submissions match these filters." : "No submissions yet." %></strong><p><%= filtered ? "Try another student name or status, or reset the filters." : "Student attempts will appear here after they start this assignment." %></p></td></tr>
        <% } %></tbody>
    </table></div>
    <footer class="results"><p role="status">Showing <strong><%= data.submissions().size() %></strong> attempt<%= data.submissions().size() == 1 ? "" : "s" %></p><p>Each attempt is shown separately. All times use UTC+07.</p></footer>
    <% } %>
</main></div></body></html>
