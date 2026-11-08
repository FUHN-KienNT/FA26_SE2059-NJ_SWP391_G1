package vn.edu.fpt.ams.service;

import com.google.gson.JsonParser;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import vn.edu.fpt.ams.dao.StudentResultDAO;
import vn.edu.fpt.ams.dto.StudentResultPage;
import vn.edu.fpt.ams.dto.SubmissionEvaluation;
import vn.edu.fpt.ams.model.Question;
import vn.edu.fpt.ams.model.ResponseType;
import vn.edu.fpt.ams.util.DBConnection;
import vn.edu.fpt.ams.util.EssaySubmissionView;
import vn.edu.fpt.ams.util.EssayEvaluationRules;
import vn.edu.fpt.ams.util.SubmissionContentSnapshot;
import vn.edu.fpt.ams.util.TeacherSubmissionView;
import vn.edu.fpt.ams.util.StudentWorkExport;

/** A read-only projection: no GET can finalize, grade, or release an attempt. */
public class StudentResultService {
    @FunctionalInterface
    public interface Connections {
        Connection open() throws SQLException;
    }

    private final StudentResultDAO dao;
    private final Connections connections;

    public StudentResultService() {
        this(new StudentResultDAO(), DBConnection::getConnection);
    }

    public StudentResultService(StudentResultDAO dao, Connections connections) {
        this.dao = dao;
        this.connections = connections;
    }

    public StudentResultPage load(long studentId, long assignmentId, Long submissionId) {
        if (studentId <= 0)
            throw new SecurityException(
                    "Vui lòng đăng nhập bằng tài khoản sinh viên đang hoạt động.");

        if (assignmentId <= 0 || submissionId != null && submissionId <= 0)
            throw new IllegalArgumentException("Mã bài tập không hợp lệ.");

        try (var connection = connections.open()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);

            try {
                dao.requireStudent(connection, studentId);
                var attempts = dao.history(connection, studentId, assignmentId);
                StudentResultPage page;

                if (submissionId == null)
                    page = new StudentResultPage(assignmentId, attempts, null, null);
                else {
                    var row =
                            dao.detail(connection, studentId, assignmentId, submissionId)
                                    .orElseThrow(
                                            () ->
                                                    new NoSuchElementException(
                                                            "Không tìm thấy lượt làm bài."));

                    // Do not even parse saved answers, evaluation, or answer keys until the release
                    // gate passes.
                    page =
                            new StudentResultPage(
                                    assignmentId,
                                    attempts,
                                    row.attempt(),
                                    row.attempt().released() ? released(row) : null);
                }

                connection.commit();

                return page;
            } catch (SQLException | RuntimeException ex) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    ex.addSuppressed(rollback);
                }

                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Không tải được kết quả. Vui lòng thử lại.", ex);
        }
    }

    public StudentResultPage catalog(long studentId, int page) {
        if (studentId <= 0 || page < 1 || page > 100_000)
            throw new IllegalArgumentException("Trang bài làm không hợp lệ.");

        try (var connection = connections.open()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);

            try {
                dao.requireStudent(connection, studentId);
                long total = dao.countCatalog(connection, studentId);
                int pages = (int) Math.max(1, Math.min(Integer.MAX_VALUE,
                        (total + StudentResultPage.PAGE_SIZE - 1) / StudentResultPage.PAGE_SIZE));
                int selectedPage = Math.min(page, pages);
                var result = new StudentResultPage(
                        0, dao.catalog(connection, studentId, selectedPage), null, null, selectedPage, total);
                connection.commit();

                return result;
            } catch (SQLException | RuntimeException ex) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    ex.addSuppressed(rollback);
                }

                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Không tải được danh sách bài làm.", ex);
        }
    }

    public String export(long studentId, long assignmentId, long submissionId) {
        if (studentId <= 0 || assignmentId <= 0 || submissionId <= 0)
            throw new IllegalArgumentException("Bài làm không hợp lệ.");

        try (var connection = connections.open()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);

            try {
                dao.requireStudent(connection, studentId);
                var row = dao.exportRow(connection, studentId, assignmentId, submissionId)
                        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy bài làm của bạn."));
                String html = StudentWorkExport.html(row);
                connection.commit();

                return html;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();

                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Không xuất được bài làm. Vui lòng thử lại.", ex);
        }
    }

    static StudentResultPage.Released released(StudentResultDAO.Row row) {
        SubmissionEvaluation evaluation;
        boolean evaluationUnavailable = false;

        try {
            evaluation =
                    SubmissionEvaluation.read(row.evaluation(), row.revision(), row.feedback());
        } catch (IllegalArgumentException ex) {
            evaluation = emptyEvaluation();
            evaluationUnavailable = row.evaluation() != null;
        }

        // Older accepted work may predate content snapshots. Keep its published summary without
        // inventing paired answers.
        var unavailable =
                new StudentResultPage.Released(
                        row.attempt().title(),
                        null,
                        row.maximumScore(),
                        row.attempt().score(),
                        row.feedback(),
                        emptyEvaluation(),
                        null,
                        null,
                        true,
                        evaluationUnavailable);

        if (row.assignmentSnapshot() == null || row.questionsSnapshot() == null) return unavailable;

        try {
            var assignment =
                    SubmissionContentSnapshot.assignment(
                            row.assignmentSnapshot(), row.attempt().assignmentId());

            if (assignment.classId() != row.attempt().classId()) return unavailable;

            if (assignment.responseType() == ResponseType.ESSAY) {
                if (!evaluation.marks().isEmpty()) {
                    try {
                        EssayEvaluationRules.validate(assignment, evaluation, true);

                        if (row.attempt().score() == null
                                || evaluation.total().compareTo(row.attempt().score()) != 0)
                            throw new IllegalArgumentException("Tổng điểm không khớp.");
                    } catch (IllegalArgumentException ex) {
                        evaluation = emptyEvaluation();
                        evaluationUnavailable = true;
                    }
                }

                return new StudentResultPage.Released(
                        assignment.title(),
                        assignment.instructions(),
                        assignment.maximumScore(),
                        row.attempt().score(),
                        row.feedback(),
                        evaluation,
                        EssaySubmissionView.read(assignment.taskChecklistJson(), row.content()),
                        null,
                        false,
                        evaluationUnavailable);
            }

            var questions = new ArrayList<Question>();

            for (var element : JsonParser.parseString(row.questionsSnapshot()).getAsJsonArray()) {
                var question = element.getAsJsonObject();

                if (question.get("assignment_id").getAsLong() != assignment.assignmentId())
                    return unavailable;

                questions.add(
                        new Question(
                                question.get("question_id").getAsLong(),
                                assignment.assignmentId(),
                                question.get("question_order").getAsInt(),
                                question.get("question_text").getAsString(),
                                question.get("maximum_points").getAsBigDecimal(),
                                question.get("options").toString()));
            }

            return new StudentResultPage.Released(
                    assignment.title(),
                    assignment.instructions(),
                    assignment.maximumScore(),
                    row.attempt().score(),
                    row.feedback(),
                    emptyEvaluation(),
                    null,
                    TeacherSubmissionView.answers(row.answer(), questions),
                    false,
                    evaluationUnavailable);
        } catch (RuntimeException invalidSnapshot) {
            return unavailable;
        }
    }

    private static SubmissionEvaluation emptyEvaluation() {
        return new SubmissionEvaluation(0, java.util.List.of(), "");
    }
}
