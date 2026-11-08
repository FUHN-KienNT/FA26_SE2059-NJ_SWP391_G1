package vn.edu.fpt.ams.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import vn.edu.fpt.ams.dao.AssignmentDAO;
import vn.edu.fpt.ams.dao.TeacherEvaluationDAO;
import vn.edu.fpt.ams.dao.TeacherSubmissionDAO;
import vn.edu.fpt.ams.dto.SubmissionEvaluation;
import vn.edu.fpt.ams.dto.AssignmentDetailPage;
import vn.edu.fpt.ams.dto.TeacherEvaluationPage;
import vn.edu.fpt.ams.dto.TeacherSubmissionDetailPage;
import vn.edu.fpt.ams.model.Submission;
import vn.edu.fpt.ams.model.SubmissionStatus;
import vn.edu.fpt.ams.model.ResponseType;
import vn.edu.fpt.ams.util.AssignmentSubmissionAccess;
import vn.edu.fpt.ams.util.DBConnection;
import vn.edu.fpt.ams.util.EssayEvaluationRules;
import vn.edu.fpt.ams.util.SubmissionContentSnapshot;
import vn.edu.fpt.ams.integration.GeminiEvaluationClient;

/** Atomic Essay draft, completion, reopening and explicit publication workflow. */
public class TeacherEvaluationService {
    private static final Set<String> ACTIONS =
            Set.of("saveEvaluation", "completeEvaluation", "reopenEvaluation", "releaseEvaluation",
                    "publishEvaluation", "reviseReleasedEvaluation", "restoreEvaluation");
    private final TeacherEvaluationDAO evaluations;
    private final TeacherSubmissionDAO submissions;
    private final AssignmentDAO assignments;
    private final ConnectionProvider connections;
    private final GeminiEvaluationClient gemini;

    @FunctionalInterface
    public interface ConnectionProvider {
        Connection open() throws SQLException;
    }

    public TeacherEvaluationService() {
        this(
                new TeacherEvaluationDAO(),
                new TeacherSubmissionDAO(),
                new AssignmentDAO(),
                DBConnection::getConnection);
    }

    public TeacherEvaluationService(
            TeacherEvaluationDAO evaluations,
            TeacherSubmissionDAO submissions,
            AssignmentDAO assignments,
            ConnectionProvider connections) {
        this(evaluations, submissions, assignments, connections, new GeminiEvaluationClient());
    }

    public TeacherEvaluationService(
            TeacherEvaluationDAO evaluations, TeacherSubmissionDAO submissions,
            AssignmentDAO assignments, ConnectionProvider connections, GeminiEvaluationClient gemini) {
        this.evaluations = Objects.requireNonNull(evaluations);
        this.submissions = Objects.requireNonNull(submissions);
        this.assignments = Objects.requireNonNull(assignments);
        this.connections = Objects.requireNonNull(connections);
        this.gemini = Objects.requireNonNull(gemini);
    }

    public TeacherEvaluationPage page(long teacherId, long assignmentId, long submissionId) {
        return page(teacherId, assignmentId, submissionId, 1);
    }

    public TeacherEvaluationPage page(long teacherId, long assignmentId, long submissionId, int historyPage) {
        validateIds(teacherId, assignmentId, submissionId);

        if (historyPage < 1) throw new IllegalArgumentException("Trang nhật ký không hợp lệ.");

        try (Connection connection = connections.open()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);

            try {
                if (!assignments.isActiveTeacher(connection, teacherId)) throw inactive();

                var detail = detail(connection, teacherId, assignmentId, submissionId);
                var saved = evaluations.read(connection, assignmentId, submissionId);
                SubmissionEvaluation evaluation;
                String blocked = null;

                try {
                    evaluation =
                            SubmissionEvaluation.read(
                                    saved.json(),
                                    saved.revision(),
                                    detail.row().submission().feedback());
                } catch (IllegalArgumentException ex) {
                    evaluation = new SubmissionEvaluation(saved.revision(), List.of(), "");
                    blocked = "Dữ liệu chấm bài đã lưu không hợp lệ; không thể sửa hoặc công bố.";
                }

                List<vn.edu.fpt.ams.util.EssayQuestions.Task> questions = List.of();

                try {
                    questions = EssayEvaluationRules.questions(detail.assignment().assignment());
                    requireAccepted(detail.row().submission());
                    var currentAssignment = assignments.findDetailForTeacher(connection, teacherId, assignmentId)
                            .orElseThrow(() -> new NoSuchElementException("Không tìm thấy bài tập."));
                    requireGradingOpen(currentAssignment.assignment(), assignments.currentTime(connection));
                    new vn.edu.fpt.ams.dao.GroupGradeDAO().requireFinalSubmission(connection, submissionId);

                    if (blocked == null)
                        EssayEvaluationRules.validate(
                                detail.assignment().assignment(), evaluation, false);
                } catch (IllegalArgumentException ex) {
                    blocked = ex.getMessage();
                }

                var history = evaluations.history(connection, assignmentId, detail.row().studentId(), historyPage);
                connection.commit();

                return new TeacherEvaluationPage(detail, evaluation, questions, blocked, history);
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Không thể tải trang chấm bài.", ex);
        }
    }

    public void apply(
            long teacherId,
            long assignmentId,
            long submissionId,
            String action,
            long revision,
            List<SubmissionEvaluation.Mark> marks,
            String feedback) {
        apply(teacherId, assignmentId, submissionId, action, revision, marks, feedback, "", false, 0);
    }

    public void apply(
            long teacherId, long assignmentId, long submissionId, String action, long revision,
            List<SubmissionEvaluation.Mark> marks, String feedback, String reason,
            boolean feedbackAi, long historyId) {
        validateIds(teacherId, assignmentId, submissionId);

        if (action == null || !ACTIONS.contains(action))
            throw new IllegalArgumentException("Thao tác chấm bài không hợp lệ.");

        if (revision < 0) throw new IllegalArgumentException("Phiên bản chấm bài không hợp lệ.");

        String changeReason = reason == null ? "" : reason.strip();

        if (vn.edu.fpt.ams.util.AssignmentRules.length(changeReason) > 2000)
            throw new IllegalArgumentException("Lý do sửa điểm tối đa 2000 ký tự.");

        try (Connection connection = connections.open()) {
            connection.setReadOnly(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);

            try {
                // Same leading locks as assignment edits/cancellation, before any submission lock.
                if (!assignments.lockActiveTeacher(connection, teacherId)) throw inactive();

                var assignment =
                        assignments.lockOwnedAssignment(connection, teacherId, assignmentId);

                evaluations.lockClass(connection, teacherId, assignment.classId());
                evaluations.lockSubmission(connection, teacherId, assignmentId, submissionId);
                var detail = detail(connection, teacherId, assignmentId, submissionId);
                var submission = detail.row().submission();

                requireAccepted(submission);
                requireGradingOpen(assignment, assignments.currentTime(connection));
                new vn.edu.fpt.ams.dao.GroupGradeDAO().requireFinalSubmission(connection, submissionId);
                var saved = evaluations.read(connection, assignmentId, submissionId);

                if (saved.revision() != revision)
                    throw new ConcurrentModificationException(
                            "Bài chấm đã thay đổi. Hãy tải lại trang trước khi tiếp tục.");

                // Read every time so a malformed stored evaluation cannot be silently replaced.
                var stored =
                        SubmissionEvaluation.read(
                                saved.json(), saved.revision(), submission.feedback());
                boolean released = submission.status() == SubmissionStatus.RELEASED;
                boolean restoring = action.equals("restoreEvaluation");
                boolean editing = Set.of("saveEvaluation", "completeEvaluation", "publishEvaluation",
                                "reviseReleasedEvaluation", "restoreEvaluation").contains(action);
                SubmissionEvaluation next = stored;
                String nextStatus = submission.status().name();
                java.math.BigDecimal nextScore = submission.score();

                if (released && (!editing || (!action.equals("reviseReleasedEvaluation") && !restoring)))
                    throw new IllegalArgumentException("Dùng chức năng sửa điểm đã công bố và ghi rõ lý do.");

                if ((released || action.equals("reviseReleasedEvaluation")) && changeReason.isBlank())
                    throw new IllegalArgumentException("Nhập lý do sửa điểm và nhận xét đã công bố.");

                if (action.equals("reviseReleasedEvaluation") && !released)
                    throw new IllegalArgumentException("Bài nộp chưa có kết quả đã công bố.");

                if (editing) {
                    var proposed = restoring
                            ? evaluations.previousEvaluation(connection, historyId, assignmentId,
                                    detail.row().studentId(), submissionId, revision)
                            : new SubmissionEvaluation(revision, marks, feedback, feedbackAi);
                    boolean completing = released || action.equals("completeEvaluation")
                            || action.equals("publishEvaluation")
                            || (restoring && submission.status() == SubmissionStatus.GRADED);

                    EssayEvaluationRules.validate(
                            detail.assignment().assignment(),
                            proposed,
                            completing);
                    evaluations.captureSnapshot(connection, assignmentId, submissionId);

                    if (released) {
                        evaluations.reviseReleased(connection, submissionId, teacherId, proposed);
                    } else {
                        evaluations.save(connection, submissionId, teacherId, proposed, completing);

                        if (action.equals("publishEvaluation"))
                            evaluations.release(connection, submissionId, revision + 1);
                    }

                    next = proposed;
                    nextScore = proposed.total();
                    nextStatus = released || action.equals("publishEvaluation") ? "RELEASED"
                            : completing ? "GRADED" : "SUBMITTED";
                } else {
                    requireStatus(submission, SubmissionStatus.GRADED);
                    EssayEvaluationRules.validate(
                            detail.assignment().assignment(),
                            stored,
                            action.equals("releaseEvaluation"));

                    if (action.equals("releaseEvaluation")
                            && (submission.score() == null
                                    || submission.score().compareTo(stored.total()) != 0
                                    || submission.gradedAt() == null))
                        throw new IllegalArgumentException(
                                "Điểm tổng đã lưu không khớp với điểm các yêu cầu; cần mở lại và hoàn tất bài chấm.");

                    evaluations.captureSnapshot(connection, assignmentId, submissionId);

                    if (action.equals("reopenEvaluation"))
                        evaluations.reopen(connection, submissionId, revision);
                    else evaluations.release(connection, submissionId, revision);

                    nextStatus = action.equals("reopenEvaluation") ? "SUBMITTED" : "RELEASED";
                }

                evaluations.journal(connection, assignmentId, submissionId, detail.row().studentId(),
                        teacherId, action, changeReason, stored, next, submission.score(), nextScore,
                        submission.status().name(), nextStatus);
                if("RELEASED".equals(nextStatus))
                    new vn.edu.fpt.ams.dao.GroupGradeDAO().publish(connection,submissionId);
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Không thể lưu bài chấm. Hãy tải lại trang trước khi thử lại.", ex);
        }
    }

    public SubmissionEvaluation aiSuggestion(
            long teacherId, long assignmentId, long submissionId, long revision) {
        var before = page(teacherId, assignmentId, submissionId);

        if (before.blockedReason() != null)
            throw new IllegalArgumentException(before.blockedReason());

        if (before.evaluation().revision() != revision)
            throw new ConcurrentModificationException("Đánh giá đã thay đổi. Vui lòng tải lại trang.");

        var suggestion = gemini.evaluate(before.detail(), before.questions(), revision);
        EssayEvaluationRules.validate(before.detail().assignment().assignment(), suggestion, true);
        var after = page(teacherId, assignmentId, submissionId);

        if (after.blockedReason() != null || after.evaluation().revision() != revision
                || !Objects.equals(before.detail().row().submission().content(),
                        after.detail().row().submission().content())
                || !Objects.equals(before.detail().assignment().assignment().taskChecklistJson(),
                        after.detail().assignment().assignment().taskChecklistJson()))
            throw new ConcurrentModificationException("Bài nộp hoặc đánh giá đã thay đổi trong lúc AI chấm.");

        EssayEvaluationRules.validate(after.detail().assignment().assignment(), suggestion, true);

        return suggestion;
    }

    private TeacherSubmissionDetailPage detail(
            Connection connection, long teacherId, long assignmentId, long submissionId)
            throws SQLException {
        var assignment =
                assignments
                        .findDetailForTeacher(connection, teacherId, assignmentId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Không tìm thấy bài tập hoặc bạn không có quyền xem."));
        var row =
                submissions
                        .findDetailForTeacher(connection, teacherId, assignmentId, submissionId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Không tìm thấy bài nộp hoặc bạn không có quyền xem."));
        boolean hasWork =
                row.submission().submittedAt() != null
                        && Set.of(
                                        SubmissionStatus.SUBMITTED,
                                        SubmissionStatus.GRADED,
                                        SubmissionStatus.RELEASED)
                                .contains(row.submission().status());

        if (!AssignmentSubmissionAccess.canView(assignment.assignment(), hasWork))
            throw new NoSuchElementException("Không tìm thấy bài tập hoặc bạn không có quyền xem.");

        if (row.assignmentSnapshot() == null
                && assignment.assignment().responseType() == ResponseType.OBJECTIVE)
            assignment =
                    new AssignmentDetailPage(
                            assignment.assignment(),
                            assignment.classCode(),
                            assignment.className(),
                            assignment.subjectCode(),
                            assignment.subjectName(),
                            assignment.termCode(),
                            assignment.templateTitle(),
                            assignments.findQuestionsForTeacher(
                                    connection, teacherId, assignmentId),
                            assignment.canViewSubmissions());

        return new TeacherSubmissionDetailPage(
                SubmissionContentSnapshot.displayPage(assignment, row), row,
                new vn.edu.fpt.ams.dao.GroupGradeDAO().teacherDetail(connection,submissionId));
    }

    private static void requireAccepted(Submission submission) {
        if (submission.submittedAt() == null
                || !Set.of(
                                SubmissionStatus.SUBMITTED,
                                SubmissionStatus.GRADED,
                                SubmissionStatus.RELEASED)
                        .contains(submission.status()))
            throw new IllegalArgumentException(
                    "Chỉ có thể chấm bài nộp đã được hệ thống tiếp nhận.");
    }

    private static void requireGradingOpen(
            vn.edu.fpt.ams.model.Assignment assignment, java.time.Instant now) {
        if (assignment.closedAt() == null
                && (assignment.dueAt() == null || assignment.dueAt().isAfter(now)))
            throw new IllegalArgumentException(
                    "Chỉ được chấm sau hạn nộp hoặc sau khi giảng viên đóng nhận bài vĩnh viễn.");
    }

    private static void requireStatus(Submission submission, SubmissionStatus expected) {
        if (submission.status() == SubmissionStatus.RELEASED)
            throw new IllegalArgumentException("Kết quả đã công bố không thể thay đổi.");

        if (submission.status() != expected)
            throw new IllegalArgumentException(
                    "Trạng thái bài nộp không phù hợp với thao tác. Hãy tải lại trang.");
    }

    private static void validateIds(long teacherId, long assignmentId, long submissionId) {
        if (teacherId <= 0) throw inactive();

        if (assignmentId <= 0 || submissionId <= 0)
            throw new IllegalArgumentException("Mã bài tập và bài nộp phải là số nguyên dương.");
    }

    private static SecurityException inactive() {
        return new SecurityException("Cần đăng nhập bằng tài khoản giảng viên đang hoạt động.");
    }

    private static void rollback(Connection connection, Exception error) {
        try {
            connection.rollback();
        } catch (SQLException rollback) {
            error.addSuppressed(rollback);
        }
    }
}
