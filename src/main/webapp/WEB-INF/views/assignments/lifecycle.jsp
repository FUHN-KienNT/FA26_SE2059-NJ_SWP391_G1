<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.Map,vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*" %>
<%@ page import="vn.edu.fpt.ams.util.AssignmentDetailView,vn.edu.fpt.ams.util.WebSupport" %>
<%@ page import="static vn.edu.fpt.ams.util.AssignmentListView.*" %>
<%
    AssignmentLifecyclePage lifecycle = (AssignmentLifecyclePage) request.getAttribute("lifecyclePage");
    AssignmentDetailPage detail = lifecycle == null ? null : lifecycle.detail();
    Assignment assignment = detail == null ? null : detail.assignment();
    AssignmentListFilter filters = (AssignmentListFilter) request.getAttribute("assignmentFilters");
    if (filters == null) filters = AssignmentListFilter.from(null, null, null, null);
    Map<String, String> errors = (Map<String, String>) request.getAttribute("lifecycleErrors");
    if (errors == null) errors = lifecycle == null || lifecycle.errors() == null ? Map.of() : lifecycle.errors();
    String error = (String) request.getAttribute("lifecycleError");
    boolean hasError = error != null && !error.isBlank();
    String action = lifecycle == null ? (String) request.getAttribute("lifecycleAction") : lifecycle.action();
    boolean publishing = "publish".equals(action);
    boolean cancelling = "cancel".equals(action);
    String pageTitle = publishing ? "Publish Assignment" : cancelling ? "Cancel Assignment" : "Assignment action";
    String base = request.getContextPath();
    String listUrl = base + "/teacher/assignments";
    String backUrl = assignment == null ? listUrl : AssignmentDetailView.listUrl(base, assignment.classId(), filters);
    String actionUrl = assignment == null ? backUrl : backUrl + "&assignmentId=" + assignment.assignmentId();
    String detailUrl = assignment == null ? backUrl : actionUrl + "&mode=view";
    boolean alreadyPublished = publishing && assignment != null && assignment.status() == AssignmentStatus.PUBLISHED;
    boolean alreadyCancelled = cancelling && assignment != null && assignment.status() == AssignmentStatus.CANCELLED;
    boolean statusAllowsAction = assignment != null && (publishing && assignment.status() == AssignmentStatus.DRAFT
            || cancelling && (assignment.status() == AssignmentStatus.DRAFT || assignment.status() == AssignmentStatus.PUBLISHED));
    boolean hasRevision = lifecycle != null && lifecycle.revision() != null && lifecycle.revision().matches("[a-fA-F0-9]{64}");
    boolean canConfirm = statusAllowsAction && errors.isEmpty() && hasRevision;
%>
<!doctype html>
<html lang="en"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title><%= escape(pageTitle) %> | AMS</title>
    <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-list.css">
    <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-detail.css">
    <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
    <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-lifecycle.css">
</head><body class="ams-ui teacher-app">
<%@ include file="/WEB-INF/views/shared/shell.jspf" %>
<main class="ams-content assignment-lifecycle" id="page-content">
    <nav class="breadcrumb" aria-label="Breadcrumb"><a href="<%= escape(base) %>/teacher/dashboard">Dashboard</a><span aria-hidden="true">/</span><a href="<%= escape(backUrl) %>">Assignments</a><span aria-hidden="true">/</span><% if (assignment != null) { %><a href="<%= escape(detailUrl) %>">Details</a><span aria-hidden="true">/</span><% } %><span aria-current="page"><%= publishing ? "Publish" : cancelling ? "Cancel" : "Action" %></span></nav>
    <div class="page-heading"><div><p class="eyebrow">Review assignment</p><h1><%= escape(pageTitle) %></h1><p>Review the saved configuration and the effect of this action.</p></div></div>
    <% if (hasError) { %>
    <section class="notice error lifecycle-notice" role="alert"><h2>Unable to complete this action</h2><p><%= escape(error) %></p></section>
    <% } %>
    <% if (assignment == null && !errors.isEmpty()) { %>
    <section class="notice error lifecycle-notice" role="alert"><h2>Unable to complete this action</h2><ul><% for (String message : errors.values()) { %><li><%= escape(message) %></li><% } %></ul></section>
    <% } %>
    <% if (assignment == null) { %>
    <section class="empty-state"><h2>Assignment unavailable</h2><p>The saved assignment could not be loaded. Return to the list to choose an assignment.</p><a class="ams-button back-button" href="<%= escape(backUrl) %>">Back to list</a></section>
    <% } else { %>
    <section class="detail-card lifecycle-panel" aria-labelledby="assignment-heading">
        <div class="lifecycle-heading"><div><h2 id="assignment-heading"><%= escape(assignment.title()) %></h2><p><%= escape(detail.classCode()) %> — <%= escape(detail.className()) %> · <%= escape(detail.subjectCode()) %> · <%= escape(detail.termCode()) %></p></div><a href="<%= escape(detailUrl) %>">Review assignment content</a></div>
        <div class="lifecycle-transition" role="group" aria-label="Assignment status">
            <span class="lifecycle-status"><span>Current status</span><strong class="badge <%= assignment.status() == null ? "" : assignment.status().name().toLowerCase(java.util.Locale.ROOT) %>"><%= assignment.status() == null ? "Unknown" : escape(label(assignment.status())) %></strong></span>
            <% if (publishing || cancelling) { %><span class="lifecycle-arrow" aria-hidden="true">→</span><span class="sr-only">Changes to</span><span class="lifecycle-status"><span>Proposed status</span><strong class="badge <%= publishing ? "published" : "cancelled" %>"><%= publishing ? "Published" : "Cancelled" %></strong></span><% } %>
        </div>
        <dl class="metadata lifecycle-metadata">
            <div><dt>Class</dt><dd><%= escape(detail.classCode()) %> — <%= escape(detail.className()) %></dd></div>
            <div><dt>Purpose</dt><dd><%= escape(label(assignment.assignmentPurpose())) %></dd></div>
            <div><dt>Response type</dt><dd><%= escape(label(assignment.responseType())) %></dd></div>
            <div><dt>Maximum score</dt><dd><%= assignment.maximumScore() == null ? "Not set" : escape(assignment.maximumScore().stripTrailingZeros().toPlainString()) + " points" %></dd></div>
            <div><dt>Available from (UTC+07)</dt><dd><%= escape(deadline(assignment.availableFrom())) %></dd></div>
            <div><dt>Due at (UTC+07)</dt><dd><%= escape(deadline(assignment.dueAt())) %></dd></div>
        </dl>
        <% if (alreadyPublished) { %>
        <p class="state-message" role="status">This assignment is already published. Its content and settings are frozen. No further confirmation is needed.</p>
        <% } else if (alreadyCancelled) { %>
        <p class="state-message" role="status">This assignment is already cancelled. Students cannot start or continue attempts. No further confirmation is needed.</p>
        <% } else if (!statusAllowsAction) { %>
        <div class="notice error lifecycle-notice" role="alert"><h2>Action unavailable</h2><p><%= publishing || cancelling ? "This action is unavailable for an assignment in its current status." : "This assignment action is not supported." %></p></div>
        <% } else if (publishing) { %>
        <section class="lifecycle-effect" aria-labelledby="effect-heading"><h3 id="effect-heading">What happens when you publish</h3><ul><li>Assignment content and settings are frozen. Published assignments cannot be edited.</li><li>Eligible students can start attempts only during the availability window shown above.</li></ul></section>
        <% } else { %>
        <section class="lifecycle-effect lifecycle-cancel-effect" aria-labelledby="effect-heading"><h3 id="effect-heading">What happens when you cancel</h3><ul><li>Students can no longer start or continue attempts.</li><li>In-progress attempts become void (VOID).</li><li>Submitted attempts and existing results are preserved.</li></ul><p class="lifecycle-count">Current preview: <strong><%= Math.max(0, lifecycle.inProgressCount()) %></strong> in-progress attempt<%= lifecycle.inProgressCount() == 1 ? "" : "s" %>. This count can change before you confirm.</p></section>
        <% } %>
        <% if (!errors.isEmpty()) { %>
        <section class="notice error lifecycle-notice" role="alert" aria-labelledby="lifecycle-errors-heading"><h3 id="lifecycle-errors-heading"><%= publishing ? "Resolve these issues before publishing" : "Unable to cancel this assignment" %></h3><ul><% for (String message : errors.values()) { %><li><%= escape(message) %></li><% } %></ul></section>
        <% } else if (statusAllowsAction && !hasRevision && !hasError) { %>
        <section class="notice error lifecycle-notice" role="alert"><h3>Confirmation unavailable</h3><p>The saved configuration could not be verified. Reload the latest preview before confirming.</p></section>
        <% } %>
        <% if (canConfirm) { %>
        <form method="post" action="<%= escape(listUrl) %>" id="lifecycle-form" class="lifecycle-actions">
            <input type="hidden" name="action" value="<%= escape(action) %>">
            <input type="hidden" name="assignmentId" value="<%= assignment.assignmentId() %>">
            <input type="hidden" name="classId" value="<%= assignment.classId() %>">
            <input type="hidden" name="revision" value="<%= escape(lifecycle.revision()) %>">
            <input type="hidden" name="csrf" value="<%= escape(WebSupport.csrf(request)) %>">
            <input type="hidden" name="search" value="<%= escape(filters.search()) %>">
            <input type="hidden" name="purpose" value="<%= filters.purpose() == null ? "" : escape(filters.purpose().name()) %>">
            <input type="hidden" name="responseType" value="<%= filters.responseType() == null ? "" : escape(filters.responseType().name()) %>">
            <input type="hidden" name="status" value="<%= filters.status() == null ? "" : escape(filters.status().name()) %>">
            <button type="submit" class="<%= cancelling ? "lifecycle-cancel-button" : "ams-button-primary" %>"><%= publishing ? "Confirm publication" : "Confirm cancellation" %></button>
            <a class="ams-button" href="<%= escape(detailUrl) %>">Go back to details</a>
            <a href="<%= escape(backUrl) %>">Back to list</a>
        </form>
        <% } else { %>
        <div class="lifecycle-actions">
            <% if (publishing && assignment.status() == AssignmentStatus.DRAFT && !errors.isEmpty()) { %><a class="ams-button ams-button-primary" href="<%= escape(actionUrl) %>&amp;mode=edit">Edit Assignment</a><% } %>
            <% if (statusAllowsAction && (hasError || !errors.isEmpty() || !hasRevision)) { %><a class="ams-button" href="<%= escape(actionUrl) %>&amp;mode=<%= escape(action) %>">Reload latest preview</a><% } %>
            <a class="ams-button" href="<%= escape(detailUrl) %>">Go back to details</a>
            <a href="<%= escape(backUrl) %>">Back to list</a>
        </div>
        <% } %>
    </section>
    <% } %>
</main></div>
<script>
    (() => {
        const form = document.getElementById('lifecycle-form');
        if (!form) return;
        const button = form.querySelector('button[type="submit"]');
        const label = button.textContent;
        form.addEventListener('submit', event => {
            if (form.dataset.submitting === 'true') { event.preventDefault(); return; }
            form.dataset.submitting = 'true';
            button.disabled = true;
            button.textContent = 'Applying change…';
        });
        window.addEventListener('pageshow', () => {
            form.dataset.submitting = 'false'; button.disabled = false; button.textContent = label;
        });
    })();
</script>
</body></html>
