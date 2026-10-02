<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*" %>
<%@ page import="static vn.edu.fpt.ams.util.AssignmentListView.*" %>
<%
    AssignmentListPage data = (AssignmentListPage) request.getAttribute("assignmentPage");
    String error = (String) request.getAttribute("assignmentListError");
    String base = request.getContextPath();
    String listUrl = base + "/teacher/assignments";
    Long classId = data == null ? null : data.selectedClassId();
    AssignmentListFilter filters = data == null ? AssignmentListFilter.from(null, null, null, null) : data.filter();
%>
<!doctype html>
<html lang="en"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Assignments | AMS</title>
    <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-list.css">
</head><body>
<div class="assignment-shell">
    <aside class="sidebar" aria-label="Main navigation">
        <a class="brand" href="<%= escape(base) %>/home"><span class="brand-mark" aria-hidden="true">A</span> AMS</a>
        <nav><a href="<%= escape(base) %>/home">Dashboard</a><a href="<%= escape(base) %>/teacher/enrollments">My Classes</a><a class="active" href="<%= escape(listUrl) %>" aria-current="page">Assignments</a></nav>
    </aside>
    <div class="workspace">
        <header class="topbar"><span>Assignment Management System</span><span class="account"><span class="avatar" aria-hidden="true">T</span> Teacher</span></header>
        <main>
            <% if (request.getAttribute("assignmentPreviewTeacherId") != null) { %>
            <p class="preview-notice" role="status">Local test mode — Teacher #<%= escape(String.valueOf(request.getAttribute("assignmentPreviewTeacherId"))) %>. Sign-in is temporarily skipped.</p>
            <% } %>
            <nav class="breadcrumb" aria-label="Breadcrumb"><a href="<%= escape(base) %>/home">Home</a><span aria-hidden="true">/</span><span>Assignments</span></nav>
            <div class="page-heading"><div><h1>Assignments</h1><p>Manage assignments for your assigned classes.</p></div>
                <% if (classId != null) { %><a class="button primary" href="<%= escape(listUrl) %>?mode=create&amp;classId=<%= classId %>">Create Assignment</a><% } %>
            </div>
            <% if (error != null) { %>
            <section class="notice error" role="alert"><h2>Unable to load assignments</h2><p><%= escape(error) %></p><a href="<%= escape(listUrl) %>">Try again</a></section>
            <% } else if (data == null || data.classes().isEmpty()) { %>
            <section class="empty-state"><h2>No assigned classes</h2><p>Your assignments will appear here when a class is assigned to you.</p></section>
            <% } else { %>
            <form class="filters" method="get" action="<%= escape(listUrl) %>">
                <div class="search-row"><label class="search-label" for="search">Search by title<input type="search" id="search" name="search" maxlength="200" placeholder="Search by assignment title…" value="<%= escape(filters.search()) %>"></label><button class="button primary" type="submit">Apply filters</button><a class="button" href="<%= escape(listUrl) %>?classId=<%= classId %>">Reset</a></div>
                <div class="filter-grid">
                    <label for="classId">Class<select id="classId" name="classId">
                    <% for (AssignmentClassOption option : data.classes()) { %>
                        <option value="<%= option.classId() %>" <%= option.classId() == classId ? "selected" : "" %>><%= escape(option.classCode()) %> — <%= escape(option.className()) %></option>
                    <% } %></select></label>
                    <label for="purpose">Purpose<select id="purpose" name="purpose"><option value="">All</option>
                    <% for (AssignmentPurpose value : AssignmentPurpose.values()) { %>
                        <option value="<%= value.name() %>" <%= value == filters.purpose() ? "selected" : "" %>><%= label(value) %></option>
                    <% } %></select></label>
                    <label for="responseType">Response type<select id="responseType" name="responseType"><option value="">All</option>
                    <% for (ResponseType value : ResponseType.values()) { %>
                        <option value="<%= value.name() %>" <%= value == filters.responseType() ? "selected" : "" %>><%= label(value) %></option>
                    <% } %></select></label>
                    <label for="status">Status<select id="status" name="status"><option value="">All</option>
                    <% for (AssignmentStatus value : AssignmentStatus.values()) { %>
                        <option value="<%= value.name() %>" <%= value == filters.status() ? "selected" : "" %>><%= label(value) %></option>
                    <% } %></select></label>
                </div>
            </form>
            <div class="table-wrap"><table>
                <caption class="sr-only">Assignments for the selected class. Deadlines use Vietnam time, UTC+07:00.</caption>
                <thead><tr><th scope="col">Assignment title</th><th scope="col">Purpose</th><th scope="col">Response type</th><th scope="col">Due at <small>(UTC+07)</small></th><th scope="col">Status</th><th scope="col">Actions</th></tr></thead><tbody>
                <% for (Assignment assignment : data.assignments()) {
                    String actionUrl = listUrl + "?classId=" + classId + "&assignmentId=" + assignment.assignmentId();
                %>
                <tr><td class="assignment-title"><%= escape(assignment.title()) %></td><td><%= label(assignment.assignmentPurpose()) %></td><td><%= label(assignment.responseType()) %></td><td class="deadline"><%= deadline(assignment.dueAt()) %></td><td><span class="badge <%= assignment.status().name().toLowerCase(java.util.Locale.ROOT) %>"><%= label(assignment.status()) %></span></td>
                    <td><div class="actions"><a href="<%= escape(actionUrl) %>&amp;mode=view" aria-label="View <%= escape(assignment.title()) %>">View</a>
                        <% if (assignment.status() == AssignmentStatus.DRAFT) { %>
                        <a href="<%= escape(actionUrl) %>&amp;mode=edit" aria-label="Edit <%= escape(assignment.title()) %>">Edit</a>
                        <% } if (assignment.status() != AssignmentStatus.CANCELLED) { %>
                        <details class="more-actions"><summary aria-label="More actions for <%= escape(assignment.title()) %>">More</summary><div class="action-menu">
                            <% if (assignment.status() == AssignmentStatus.DRAFT) { %><a href="<%= escape(actionUrl) %>&amp;mode=publish">Publish Assignment</a><% } %>
                            <a href="<%= escape(actionUrl) %>&amp;mode=cancel">Cancel Assignment</a>
                        </div></details><% } %>
                    </div></td>
                </tr>
                <% } if (data.assignments().isEmpty()) { %><tr><td colspan="6" class="empty-results">No assignments match this class and these filters.</td></tr><% } %>
                </tbody>
            </table></div>
            <footer class="results"><p role="status">Showing <strong><%= data.assignments().size() %></strong> assignment<%= data.assignments().size() == 1 ? "" : "s" %></p><p>Only draft assignments can be edited.</p></footer>
            <% } %>
        </main>
    </div>
</div>
</body></html>
