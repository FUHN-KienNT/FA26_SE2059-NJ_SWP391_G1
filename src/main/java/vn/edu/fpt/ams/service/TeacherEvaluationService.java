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

/** Atomic Essay draft, completion, reopening and explicit publication workflow. */
public class TeacherEvaluationService {
    private static final Set<String> ACTIONS =
            Set.of("saveEvaluation", "completeEvaluation", "reopenEvaluation", "releaseEvaluation");
    private final TeacherEvaluationDAO evaluations;
    private final TeacherSubmissionDAO submissions;
    private final AssignmentDAO assignments;
    private final ConnectionProvider connections;

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
        this.evaluations = Objects.requireNonNull(evaluations);
        this.submissions = Objects.requireNonNull(submissions);
        this.assignments = Objects.requireNonNull(assignments);
        this.connections = Objects.requireNonNull(connections);
    }

    public TeacherEvaluationPage page(long teacherId, long assignmentId, long submissionId) {
        validateIds(teacherId, assignmentId, submissionId);

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

                    if (blocked == null)
                        EssayEvaluationRules.validate(
                                detail.assignment().assignment(), evaluation, false);
                } catch (IllegalArgumentException ex) {
                    blocked = ex.getMessage();
                }

                connection.commit();

                return new TeacherEvaluationPage(detail, evaluation, questions, blocked);
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
        validateIds(teacherId, assignmentId, submissionId);

        if (action == null || !ACTIONS.contains(action))
            throw new IllegalArgumentException("Thao tác chấm bài không hợp lệ.");

        if (revision < 0) throw new IllegalArgumentException("Phiên bản chấm bài không hợp lệ.");

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
                var saved = evaluations.read(connection, assignmentId, submissionId);

                if (saved.revision() != revision)
                    throw new ConcurrentModificationException(
                            "Bài chấm đã thay đổi. Hãy tải lại trang trước khi tiếp tục.");

                // Read every time so a malformed stored evaluation cannot be silently replaced.
                var stored =
                        SubmissionEvaluation.read(
                                saved.json(), saved.revision(), submission.feedback());
                boolean editing =
                        action.equals("saveEvaluation") || action.equals("completeEvaluation");

                if (editing) {
                    requireStatus(submission, SubmissionStatus.SUBMITTED);
                    var proposed = new SubmissionEvaluation(revision, marks, feedback);

                    EssayEvaluationRules.validate(
                            detail.assignment().assignment(),
                            proposed,
                            action.equals("completeEvaluation"));
                    evaluations.captureSnapshot(connection, assignmentId, submissionId);
                    evaluations.save(
                            connection,
                            submissionId,
                            teacherId,
                            proposed,
                            action.equals("completeEvaluation"));
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
                }

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
                SubmissionContentSnapshot.displayPage(assignment, row), row);
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
