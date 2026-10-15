<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="vn.edu.fpt.ams.dto.*,vn.edu.fpt.ams.model.*" %>
<%@ page import="vn.edu.fpt.ams.util.TeacherSubmissionView" %>
<%@ page import="vn.edu.fpt.ams.util.EssaySubmissionView" %>
<%@ page import="static vn.edu.fpt.ams.util.AssignmentListView.*" %>
<%
    TeacherSubmissionDetailPage data = (TeacherSubmissionDetailPage) request.getAttribute("submissionDetail");
    TeacherSubmissionFilter filters = (TeacherSubmissionFilter) request.getAttribute("submissionFilters");
    if (filters == null) filters = TeacherSubmissionFilter.from(null, null);
    String error = (String) request.getAttribute("submissionError");
    String base = request.getContextPath();
    Long assignmentId = data == null ? (Long) request.getAttribute("selectedAssignmentId")
            : data.assignment().assignment().assignmentId();
    String backUrl = assignmentId == null ? base + "/teacher/assignments"
            : TeacherSubmissionView.listUrl(base, assignmentId, filters);
%>
<!doctype html>
<html lang="en"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Submission Details | AMS</title>
    <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-list.css">
    <link rel="stylesheet" href="<%= escape(base) %>/css/assignment-detail.css">
    <%@ include file="/WEB-INF/views/shared/assets.jspf" %>
    <link rel="stylesheet" href="<%= escape(base) %>/css/submissions.css">
</head><body class="ams-ui teacher-app">
<%@ include file="/WEB-INF/views/shared/shell.jspf" %>
<main class="ams-content submission-page" id="page-content">
    <nav class="breadcrumb" aria-label="Breadcrumb"><a href="<%= escape(base) %>/teacher/dashboard">Dashboard</a><span aria-hidden="true">/</span><a href="<%= escape(backUrl) %>">Submissions</a><span aria-hidden="true">/</span><span aria-current="page">Details</span></nav>
    <% if (error != null) { %>
    <section class="notice error" role="alert"><h1>Unable to load submission</h1><p><%= escape(error) %></p><a href="">Try again</a> · <a href="<%= escape(backUrl) %>">Back to submissions</a></section>
    <% } else if (data == null) { %>
    <section class="empty-state"><h1>Submission unavailable</h1><p>This submission could not be loaded.</p><a class="button back-button" href="<%= escape(backUrl) %>">Back to submissions</a></section>
    <% } else {
        Assignment assignment = data.assignment().assignment();
        TeacherSubmissionRow row = data.row();
        Submission submission = row.submission();
        String safeLink = TeacherSubmissionView.safeLink(submission.submissionLink());
    %>
    <div class="page-heading"><div><p class="eyebrow">Submission details</p><h1><%= escape(row.studentName()) %> · Attempt <%= submission.attemptNumber() %></h1><p><%= escape(row.studentEmail()) %></p></div><div class="submission-detail-actions"><span class="badge submission-status <%= submission.status().name().toLowerCase(java.util.Locale.ROOT) %>"><%= escape(TeacherSubmissionView.statusLabel(submission.status())) %></span><a class="button" href="<%= escape(backUrl) %>">Back to submissions</a></div></div>
    <section class="submission-card assignment-context" aria-labelledby="assignment-context-heading"><div class="context-heading"><h2 id="assignment-context-heading"><%= escape(assignment.title()) %></h2><span class="badge <%= assignment.status().name().toLowerCase(java.util.Locale.ROOT) %>"><%= label(assignment.status()) %></span></div><p><%= escape(data.assignment().classCode()) %> — <%= escape(data.assignment().className()) %> · <%= label(assignment.responseType()) %></p></section>
    <% if (submission.status() == SubmissionStatus.IN_PROGRESS) { %><p class="state-message">This attempt is still in progress. The content below is its saved work, not a submitted result.</p><% } %>
    <% if (submission.status() == SubmissionStatus.EXPIRED) { %><p class="state-message">This attempt has expired. Its saved content is retained for reference and is not an accepted submission.</p><% } %>
    <% if (submission.status() == SubmissionStatus.VOID) { %><p class="state-message">This attempt is void. Its saved content is retained for reference and is not an accepted submission.</p><% } %>
    <div class="detail-layout">
        <div class="detail-content">
            <% if (assignment.responseType() == ResponseType.ESSAY) {
                boolean hasInstructions = assignment.instructions() != null && !assignment.instructions().isBlank();
                boolean hasScope = assignment.scopeText() != null && !assignment.scopeText().isBlank();
            %>
            <% if (hasInstructions || hasScope) { %>
            <details class="detail-card essay-context"><summary>Assignment instructions and scope</summary>
                <% if (hasInstructions) { %><h3>Instructions</h3><div class="content-text"><%= escape(assignment.instructions()) %></div><% } %>
                <% if (hasScope) { %><h3>Scope</h3><div class="content-text"><%= escape(assignment.scopeText()) %></div><% } %>
            </details>
            <% } %>
            <% } %>
            <section class="detail-card student-response" aria-labelledby="submission-content-heading"><h2 id="submission-content-heading">Questions and student answers</h2>
                <% if (assignment.responseType() == ResponseType.ESSAY) {
                    var essay = EssaySubmissionView.read(assignment.taskChecklistJson(), submission.content());
                    boolean hasLegacy = essay.legacyContent() != null && !essay.legacyContent().isBlank();
                %>
                <% if (hasLegacy && !essay.items().isEmpty()) { %><p class="content-warning">This submission was saved as one combined answer. Individual answers were not recorded for each question. The original answer is shown below.</p><% } %>
                <% for (var item : essay.items()) { %>
                <article class="submission-answer essay-question-answer">
                    <div class="essay-question-block">
                        <p class="review-eyebrow"><%= item.order() > 0 ? "Question " + item.order() : "Question / task" %></p>
                        <h3 class="content-text"><%= escape(item.title()) %></h3>
                        <% if (item.description() != null && !item.description().isBlank()) { %><div class="content-text"><%= escape(item.description()) %></div><% } %>
                    </div>
                    <div class="essay-response-block">
                        <h4><%= item.order() > 0 ? "Student's answer to question " + item.order() : "Student's answer" %></h4>
                        <% if (item.answer() != null && !item.answer().isBlank()) { %><div class="content-text essay-answer-text"><%= escape(item.answer()) %></div>
                        <% } else if (hasLegacy) { %><p class="empty-content">See the original combined answer below; no separate answer was recorded.</p>
                        <% } else { %><p class="empty-content">No answer has been saved for this question.</p><% } %>
                    </div>
                </article>
                <% } %>
                <% if (hasLegacy) { %><section class="legacy-essay-answer"><h3><%= essay.items().isEmpty() ? "Student's answer" : "Original combined answer" %></h3><div class="content-text essay-answer-text"><%= escape(essay.legacyContent()) %></div></section><% } %>
                <% for (var unmatched : essay.unmatched()) { %><section class="legacy-essay-answer"><h3>Answer without a matching question</h3><p class="submission-note"><%= escape(unmatched.reference()) %></p><div class="content-text essay-answer-text"><%= escape(unmatched.text()) %></div></section><% } %>
                <% if (essay.invalid()) { %><p class="content-warning" role="alert">Some questions or answers could not be matched reliably. Unmatched saved work is kept separate.</p><% } %>
                <% if (essay.items().isEmpty() && !hasLegacy && essay.unmatched().isEmpty()) { %><p class="empty-content">No questions or written answers have been recorded.</p><% } %>
                <% } %>
                <% if (assignment.responseType() == ResponseType.OBJECTIVE) { var answers = TeacherSubmissionView.answers(submission.answer(), data.assignment().questions()); %>
                <p class="answer-legend"><span class="legend-correct">✓ Correct answer</span><span class="legend-incorrect">✗ Incorrect student choice</span></p>
                <% if (answers.items().isEmpty()) { %><p class="empty-content">No questions or saved answers are available.</p><% } %>
                <% for (var answer : answers.items()) { %>
                <article class="submission-answer">
                    <h3 class="content-text"><%= escape(answer.question()) %></h3>
                    <ul class="answer-choice-list" aria-label="Answer choices">
                        <% for (var choice : answer.choices()) {
                            boolean correct = Boolean.TRUE.equals(choice.correct());
                            boolean wrongSelected = choice.selected() && Boolean.FALSE.equals(choice.correct());
                            String choiceClass = correct ? "choice-correct" : wrongSelected ? "choice-incorrect" : choice.selected() ? "choice-selected" : "";
                        %>
                        <li class="answer-choice <%= choiceClass %>">
                            <span class="choice-marker" aria-hidden="true"><%= correct ? "✓" : wrongSelected ? "✗" : choice.selected() ? "●" : "○" %></span>
                            <div class="choice-body"><div class="choice-text"><strong class="choice-label"><%= escape(choice.label()) %>.</strong><span class="content-text"><%= escape(choice.text()) %></span></div>
                                <% if (choice.selected() || correct) { %><p class="choice-caption"><%= choice.selected() ? "Student's answer" : "" %><%= choice.selected() && correct ? " · Correct" : wrongSelected ? " · Incorrect" : correct ? "Correct answer" : " · Correctness unavailable" %></p><% } %>
                            </div>
                        </li>
                        <% } %>
                    </ul>
                    <dl class="answer-metadata answer-summary"><dt>Student's answer</dt><dd class="content-text"><%= escape(answer.selectedOption()) %></dd><dt>Awarded points</dt><dd><%= escape(answer.awardedPoints()) %></dd></dl>
                </article>
                <% } %>
                <% if (answers.invalid()) { %><p class="content-warning" role="alert">Some saved answers could not be matched or displayed. The original submission remains unchanged.</p><% } %>
                <% } %>
                <% if (assignment.responseType() != ResponseType.ESSAY && submission.content() != null && !submission.content().isBlank()) { %><h3>Additional written content</h3><div class="content-text essay-answer-text"><%= escape(submission.content()) %></div><% } %>
                <% if (submission.submissionLink() != null && !submission.submissionLink().isBlank()) { %><h3>Submission link</h3><% if (!safeLink.isEmpty()) { %><a class="submission-link" href="<%= escape(safeLink) %>" target="_blank" rel="noopener noreferrer"><%= escape(submission.submissionLink()) %></a><% } else { %><p class="content-text"><%= escape(submission.submissionLink()) %></p><p class="content-warning">This saved link cannot be opened because it is not a valid HTTP or HTTPS address.</p><% } %><% } %>
            </section>
            <section class="detail-card" aria-labelledby="evaluation-heading"><h2 id="evaluation-heading">Evaluation</h2><dl class="metadata"><dt>Score</dt><dd><% if (submission.score() == null) { %>Not graded<% } else { %><%= escape(TeacherSubmissionView.score(submission.score())) %> / <%= escape(TeacherSubmissionView.score(assignment.maximumScore())) %><% } %></dd><dt>Feedback</dt><dd class="content-text"><%= submission.feedback() == null || submission.feedback().isBlank() ? "No feedback has been recorded." : escape(submission.feedback()) %></dd><dt>Graded by</dt><dd><%= row.graderName() == null || row.graderName().isBlank() ? "Not recorded" : escape(row.graderName()) %></dd><dt>Graded at</dt><dd><%= deadline(submission.gradedAt()) %></dd><dt>Released at</dt><dd><%= deadline(submission.releasedAt()) %></dd></dl><p class="submission-note"><%= submission.releasedAt() == null ? "This evaluation has not been released to the student." : "This evaluation has been released to the student." %></p></section>
        </div>
        <aside class="detail-summary" aria-label="Submission information"><section class="detail-card"><h2>Attempt information</h2><p class="section-description">All times use Vietnam time (UTC+07).</p><dl class="metadata"><dt>Student</dt><dd><%= escape(row.studentName()) %></dd><dt>Email</dt><dd><%= escape(row.studentEmail()) %></dd><dt>Attempt</dt><dd><%= submission.attemptNumber() %></dd><dt>Status</dt><dd><%= escape(TeacherSubmissionView.statusLabel(submission.status())) %></dd><dt>Started at</dt><dd><%= deadline(submission.startedAt()) %></dd><dt>Attempt deadline</dt><dd><%= deadline(submission.deadlineAt()) %></dd><dt>Submitted at</dt><dd><%= escape(TeacherSubmissionView.submittedAt(submission.submittedAt())) %></dd></dl></section><section class="detail-card"><h2>Assignment</h2><dl class="metadata"><dt>Purpose</dt><dd><%= label(assignment.assignmentPurpose()) %></dd><dt>Response type</dt><dd><%= label(assignment.responseType()) %></dd><dt>Class</dt><dd><%= escape(data.assignment().classCode()) %> — <%= escape(data.assignment().className()) %></dd><dt>Maximum score</dt><dd><%= escape(TeacherSubmissionView.score(assignment.maximumScore())) %> points</dd></dl><a class="button back-button" href="<%= escape(base) %>/teacher/assignments?mode=view&amp;assignmentId=<%= assignment.assignmentId() %>">View assignment</a></section></aside>
    </div>
    <% } %>
</main></div></body></html>
