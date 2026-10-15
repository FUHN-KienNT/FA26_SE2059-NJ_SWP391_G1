<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.*,com.google.gson.Gson,vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.util.WebSupport" %>
<%@ page import="static vn.edu.fpt.ams.util.WebSupport.html" %>
<%
    AssignmentCreatePage context = (AssignmentCreatePage) request.getAttribute("createPage");
    AssignmentForm form = (AssignmentForm) request.getAttribute("assignmentForm");
    if (form == null) form = AssignmentForm.empty(0);
    Map<String,String> errors = (Map<String,String>) request.getAttribute("formErrors");
    if (errors == null) errors = Map.of();
    String createError = (String) request.getAttribute("createError");
    boolean editing = Boolean.TRUE.equals(request.getAttribute("assignmentEdit"));
    boolean editConflict = Boolean.TRUE.equals(request.getAttribute("editConflict"));
    String pageTitle = editing ? "Edit Assignment" : "Create Assignment";
    String savedResponseType = (String) request.getAttribute("savedResponseType");
    if (savedResponseType == null) savedResponseType = form.value("responseType");
    String savedEssayContent = request.getAttribute("savedEssayContent") == null ? ""
            : Boolean.toString(Boolean.TRUE.equals(request.getAttribute("savedEssayContent")));
    String savedObjectiveContent = request.getAttribute("savedObjectiveContent") == null ? ""
            : Boolean.toString(Boolean.TRUE.equals(request.getAttribute("savedObjectiveContent")));
    String base = request.getContextPath();
    String back = base + "/teacher/assignments" + (context == null ? "" : "?classId=" + context.classId());
    String reload = base + "/teacher/assignments?mode=edit&assignmentId=" + form.value("assignmentId");
%>
<!doctype html>
<html lang="en"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title><%= pageTitle %> | AMS</title>
    <link rel="stylesheet" href="<%= html(base) %>/css/assignment-list.css">
    <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
    <link rel="stylesheet" href="<%= html(base) %>/css/assignment-form.css">
    <script src="<%= html(base) %>/js/assignment-form.js" defer></script>
</head><body class="ams-ui teacher-app">
<%@ include file="/WEB-INF/views/shared/shell.jspf" %>
<main class="ams-content assignment-create" id="page-content">
    <nav class="breadcrumb" aria-label="Breadcrumb"><a href="<%= html(base) %>/teacher/dashboard">Dashboard</a><span>/</span><a href="<%= html(back) %>">Assignments</a><span>/</span><span><%= editing ? "Edit" : "Create" %></span></nav>
    <div class="page-heading"><div><h1><%= pageTitle %></h1><p><%= editing ? "Update your assignment draft, then save your changes." : "Choose a template, customize your assignment, then save it as a draft." %></p></div><span class="badge draft"><%= editing ? "Draft" : "New draft" %></span></div>
    <% if (createError != null) { %><div class="notice error" role="alert"><p><%= html(createError) %></p><% if(context == null){ %><a href="">Try again</a><% } %></div><% } %>
    <% if (editConflict) { %><div class="notice error edit-conflict" role="alert"><h2>The draft has changed</h2><p>Your input is still here. Reload the latest draft before saving changes.</p><a class="button leave-form" href="<%= html(reload) %>">Reload latest draft</a></div><% } %>
    <% if (!errors.isEmpty()) { %><div class="notice error" role="alert" tabindex="-1" id="form-errors"><h2>Please check your input</h2><ul><% for(var entry : errors.entrySet()) { %><li><a href="#<%= html(entry.getKey()) %>-error"><%= html(entry.getValue()) %></a></li><% } %></ul></div><% } %>
    <% if (context == null) { %><a class="button" href="<%= html(back) %>">Back to list</a>
    <% } else { %>
    <p class="class-context"><strong><%= html(context.classCode()) %> — <%= html(context.className()) %></strong><br><%= html(context.subjectCode()) %> — <%= html(context.subjectName()) %></p>
    <% if(context.templates().isEmpty()) { %><div class="notice"><h2>No active templates</h2><p>An Expert needs to provide an active template for this subject before you can create an assignment.</p></div><% } %>
    <form method="post" action="<%= html(base) %>/teacher/assignments" id="assignment-form"
          data-edit="<%= editing %>" data-edit-conflict="<%= editConflict %>"
          data-saved-response-type="<%= html(savedResponseType) %>"
          data-saved-essay-content="<%= savedEssayContent %>"
          data-saved-objective-content="<%= savedObjectiveContent %>">
        <input type="hidden" name="action" value="<%= editing ? "update" : "create" %>">
        <input type="hidden" name="csrf" value="<%= html(WebSupport.csrf(request)) %>">
        <input type="hidden" name="classId" value="<%= context.classId() %>">
        <% if (editing) { %><input type="hidden" name="assignmentId" value="<%= html(form.value("assignmentId")) %>"><input type="hidden" name="revision" value="<%= html(form.value("revision")) %>"><% } %>
        <textarea hidden id="template-data"><%= html(new Gson().toJson(Boolean.TRUE.equals(request.getAttribute("createContextUnavailable")) ? List.of() : context.templates())) %></textarea>
        <textarea hidden id="tasks-data" name="tasksJson"><%= html(form.value("tasksJson")) %></textarea>
        <textarea hidden id="questions-data" name="questionsJson"><%= html(form.value("questionsJson")) %></textarea>
        <section class="form-card" aria-labelledby="general-heading">
            <h2 id="general-heading">General information</h2>
            <label for="templateId">Template *</label>
            <select id="templateId" name="templateId" required aria-describedby="templateId-error">
                <option value="">Select a template</option>
                <% boolean selectedTemplateExists = false; for(var template : context.templates()) {
                    boolean selected = Long.toString(template.templateId()).equals(form.value("templateId"));
                    selectedTemplateExists |= selected; %>
                <option value="<%= template.templateId() %>" <%= selected ? "selected" : "" %>><%= html(template.displayTitle()) %></option>
                <% } if(!form.value("templateId").isBlank() && !selectedTemplateExists) { %>
                <option selected disabled value="">Previously selected template is no longer available</option><% } %>
            </select>
            <small class="field-error" id="templateId-error"><%= html(errors.get("templateId")) %></small>
            <p class="form-hint">Selecting a template fills the title, instructions and maximum score. You can change them below.</p>
            <div class="form-grid">
                <div><label for="purpose">Purpose *</label><select id="purpose" name="purpose" required><option value="REGULAR" <%= "REGULAR".equals(form.value("purpose")) ? "selected" : "" %>>Regular</option><option value="PRACTICE" <%= "PRACTICE".equals(form.value("purpose")) ? "selected" : "" %>>Practice</option></select><small class="field-error" id="purpose-error"><%= html(errors.get("purpose")) %></small></div>
                <div><label for="responseType">Response type *</label><select id="responseType" name="responseType" required><option value="ESSAY" <%= "ESSAY".equals(form.value("responseType")) ? "selected" : "" %>>Essay</option><option value="OBJECTIVE" <%= "OBJECTIVE".equals(form.value("responseType")) ? "selected" : "" %>>Objective</option></select><small class="field-error" id="responseType-error"><%= html(errors.get("responseType")) %></small></div>
            </div>
            <label for="title">Title *</label><input id="title" name="title" maxlength="200" required value="<%= html(form.value("title")) %>" aria-describedby="title-error"><small class="field-error" id="title-error"><%= html(errors.get("title")) %></small>
            <label for="instructions">Instructions</label><textarea id="instructions" name="instructions" rows="5"><%= html(form.value("instructions")) %></textarea>
            <label for="maximumScore">Maximum score *</label><input id="maximumScore" name="maximumScore" type="number" min="0.01" max="99999999.99" step="0.01" required value="<%= html(form.value("maximumScore")) %>" aria-describedby="maximumScore-error"><small class="field-error" id="maximumScore-error"><%= html(errors.get("maximumScore")) %></small>
        </section>
        <section class="form-card" id="essay-section" aria-labelledby="scope-heading" <%= "OBJECTIVE".equals(form.value("responseType")) ? "hidden" : "" %>>
            <h2 id="scope-heading">Essay content</h2><p class="form-hint">Describe the essay scope and tasks. You can save an incomplete draft.</p>
            <label for="scopeText">Scope</label><textarea id="scopeText" name="scopeText" rows="3"><%= html(form.value("scopeText")) %></textarea>
            <fieldset id="essay-fields" <%= "OBJECTIVE".equals(form.value("responseType")) ? "disabled" : "" %>>
            <h3>Tasks</h3><div id="task-editor"></div><small class="field-error" id="tasksJson-error"><%= html(errors.get("tasksJson")) %></small>
            <button type="button" class="button" id="add-task">Add task</button>
            </fieldset>
        </section>
        <section class="form-card" id="question-section" aria-labelledby="questions-heading" <%= "OBJECTIVE".equals(form.value("responseType")) ? "" : "hidden" %>>
            <h2 id="questions-heading">Objective questions</h2><p class="form-hint">Before publication, each question needs at least two options and exactly one correct answer. Total question points must match the maximum score.</p>
            <fieldset id="question-fields" <%= "OBJECTIVE".equals(form.value("responseType")) ? "" : "disabled" %>>
            <div id="question-editor"></div><small class="field-error" id="questionsJson-error"><%= html(errors.get("questionsJson")) %></small>
            <div class="editor-footer"><button type="button" class="button" id="add-question">Add question</button><span id="points-total" aria-live="polite"></span></div>
            </fieldset>
        </section>
        <section class="form-card" aria-labelledby="timing-heading">
            <h2 id="timing-heading">Timing &amp; limits</h2><p class="form-hint">All times use Vietnam time (UTC+07). Opening time and deadline may be left blank until publication.</p>
            <div class="form-grid">
                <div><label for="availableFrom">Available from (UTC+07)</label><input id="availableFrom" name="availableFrom" type="datetime-local" step="any" value="<%= html(form.value("availableFrom")) %>"><small class="field-error" id="availableFrom-error"><%= html(errors.get("availableFrom")) %></small></div>
                <div><label for="dueAt">Due at (UTC+07)</label><input id="dueAt" name="dueAt" type="datetime-local" step="any" value="<%= html(form.value("dueAt")) %>"><small class="field-error" id="dueAt-error"><%= html(errors.get("dueAt")) %></small></div>
                <div><label for="timeLimitMinutes">Time limit (minutes)</label><input id="timeLimitMinutes" name="timeLimitMinutes" type="number" min="1" max="2147483647" step="1" value="<%= html(form.value("timeLimitMinutes")) %>"><small class="field-error" id="timeLimitMinutes-error"><%= html(errors.get("timeLimitMinutes")) %></small><p class="form-hint">Optional. Leave blank for no additional time limit.</p></div>
                <div id="attempt-field"><label for="attemptLimit">Attempt limit</label><input id="attemptLimit" name="attemptLimit" type="number" min="1" max="2147483647" step="1" value="<%= html(form.value("attemptLimit")) %>"><small class="field-error" id="attemptLimit-error"><%= html(errors.get("attemptLimit")) %></small><p class="form-hint">Practice only. A positive limit is required before publication.</p></div>
            </div>
        </section>
        <p class="form-hint"><%= editing ? "Saving updates this draft. Students cannot access it until it is published." : "Saving creates a Draft. Students cannot access it until it is published." %></p>
        <noscript><p role="alert">Enable JavaScript to select template content and edit tasks or questions.</p></noscript>
        <div class="form-actions"><button type="submit" id="save-draft" class="button primary" disabled data-can-save="<%= !context.templates().isEmpty() && !editConflict %>"><%= editing ? "Save Changes" : "Save Draft" %></button><a class="button leave-form" href="<%= html(back) %>">Back to list</a></div>
    </form>
    <% } %>
</main></div></body></html>
