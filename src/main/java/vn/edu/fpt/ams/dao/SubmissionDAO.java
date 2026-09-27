package vn.edu.fpt.ams.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import vn.edu.fpt.ams.model.Submission;
import vn.edu.fpt.ams.model.SubmissionStatus;

/** Persistence boundary for the Submission aggregate and its embedded answer collection. */
public class SubmissionDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement Submission aggregate JDBC access");
    }

    public Optional<Submission> findById(long submissionId) { throw pending(); }
    public List<Submission> findByEnrollmentAndAssignment(long enrollmentId, long assignmentId) { throw pending(); }
    public long insert(Submission submission) { throw pending(); }
    public void updateWork(Submission submission) { throw pending(); }
    public void updateStatus(long submissionId, SubmissionStatus status) { throw pending(); }
    public void updateEvaluation(long submissionId, java.math.BigDecimal score, String feedback,
                                 Long gradedBy, Instant gradedAt, Instant releasedAt,
                                 SubmissionStatus status) { throw pending(); }

    static Submission mapSubmission(ResultSet rs) throws SQLException {
        return new Submission(rs.getLong("submission_id"), rs.getLong("assignment_id"),
                rs.getLong("enrollment_id"), rs.getInt("attempt_number"),
                SubmissionStatus.valueOf(rs.getString("status")),
                ResultSetSupport.instant(rs, "started_at"), ResultSetSupport.instant(rs, "deadline_at"),
                ResultSetSupport.instant(rs, "submitted_at"), rs.getString("content"),
                rs.getString("submission_link"), rs.getString("answer"),
                rs.getBigDecimal("score"), rs.getString("feedback"),
                ResultSetSupport.nullableLong(rs, "graded_by"), ResultSetSupport.instant(rs, "graded_at"),
                ResultSetSupport.instant(rs, "released_at"));
    }
}
