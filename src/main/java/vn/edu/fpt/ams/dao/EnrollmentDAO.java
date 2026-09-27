package vn.edu.fpt.ams.dao;

import java.util.List;
import java.util.Optional;
import java.sql.ResultSet;
import java.sql.SQLException;
import vn.edu.fpt.ams.model.*;

/** JDBC boundary for class placement episodes and active roster counts. */
public class EnrollmentDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement Enrollment JDBC access");
    }

    public Optional<ClassEnrollment> findById(long enrollmentId) { throw pending(); }
    public List<ClassEnrollment> findByClassId(long classId) { throw pending(); }
    public Optional<ClassEnrollment> findActiveByRegistrationId(long registrationId) { throw pending(); }
    public long countActiveStudents(long classId) { throw pending(); }
    /** Pending invitations reserve seats until accepted, expired or cancelled. */
    public long countActiveReservedInvitationSeats(long classId) { throw pending(); }
    public long insertEnrollment(ClassEnrollment enrollment) { throw pending(); }
    public void updateStatus(long enrollmentId, EnrollmentStatus status) { throw pending(); }

    public Optional<StudentInvitation> findInvitationByToken(String token) { throw pending(); }
    public List<StudentInvitation> findInvitationsByClass(long classId) { throw pending(); }
    public long insertInvitation(StudentInvitation invitation) { throw pending(); }
    public void updateInvitationStatus(long invitationId, InvitationStatus status,
                                       java.time.Instant acceptedAt, Long acceptedBy,
                                       Long enrollmentId) { throw pending(); }

    public Optional<OverflowBatch> findOverflowBatch(long overflowBatchId) { throw pending(); }
    public List<OverflowBatch> findOverflowBatchesByClass(long classId) { throw pending(); }
    public long insertOverflowBatch(OverflowBatch batch) { throw pending(); }
    public void updateOverflowBatchStatus(long overflowBatchId, OverflowBatchStatus status) { throw pending(); }

    public Optional<AdditionalClassRequest> findAdditionalClassRequest(long requestId) { throw pending(); }
    public List<AdditionalClassRequest> findRequestsByOverflowBatch(long overflowBatchId) { throw pending(); }
    public long insertAdditionalClassRequest(AdditionalClassRequest request) { throw pending(); }
    public void updateAdditionalClassRequest(AdditionalClassRequest request) { throw pending(); }
    public Optional<AdditionalClassAssignmentOffer> findAssignmentOffer(long offerId) { throw pending(); }
    public List<AdditionalClassAssignmentOffer> findOffersByRequest(long requestId) { throw pending(); }
    public long insertAssignmentOffer(AdditionalClassAssignmentOffer offer) { throw pending(); }
    public void updateAssignmentOffer(long offerId, AssignmentOfferStatus status,
                                      java.time.Instant respondedAt) { throw pending(); }

    static ClassEnrollment mapEnrollment(ResultSet rs) throws SQLException {
        return new ClassEnrollment(rs.getLong("enrollment_id"), rs.getLong("registration_id"),
                rs.getLong("class_id"), EnrollmentStatus.valueOf(rs.getString("status")),
                EnrollmentMethod.valueOf(rs.getString("enrollment_method")),
                ResultSetSupport.instant(rs, "enrolled_at"),
                ResultSetSupport.instant(rs, "deactivated_at"));
    }

    static StudentInvitation mapInvitation(ResultSet rs) throws SQLException {
        return new StudentInvitation(rs.getLong("invitation_id"), rs.getLong("class_id"),
                rs.getLong("invited_by"), rs.getString("email"), rs.getString("full_name"),
                rs.getString("token"), InvitationStatus.valueOf(rs.getString("status")),
                ResultSetSupport.instant(rs, "sent_at"), ResultSetSupport.instant(rs, "expires_at"),
                ResultSetSupport.instant(rs, "accepted_at"),
                ResultSetSupport.nullableLong(rs, "accepted_by"),
                ResultSetSupport.nullableLong(rs, "enrollment_id"));
    }

    static OverflowBatch mapOverflowBatch(ResultSet rs) throws SQLException {
        return new OverflowBatch(rs.getLong("overflow_batch_id"), rs.getLong("source_class_id"),
                rs.getLong("imported_by"), rs.getString("source_file_name"),
                OverflowBatchStatus.valueOf(rs.getString("status")),
                rs.getString("overflow_students"), ResultSetSupport.instant(rs, "created_at"));
    }

    static AdditionalClassRequest mapAdditionalClassRequest(ResultSet rs) throws SQLException {
        return new AdditionalClassRequest(rs.getLong("request_id"), rs.getLong("overflow_batch_id"),
                rs.getLong("requested_by"), ResultSetSupport.nullableLong(rs, "preferred_teacher_id"),
                rs.getBoolean("willing_to_teach"), rs.getString("overflow_context"),
                rs.getString("note"),
                AdditionalClassRequestStatus.valueOf(rs.getString("status")),
                ResultSetSupport.instant(rs, "requested_at"),
                ResultSetSupport.instant(rs, "review_due_at"),
                ResultSetSupport.nullableLong(rs, "reviewed_by"),
                ResultSetSupport.instant(rs, "reviewed_at"),
                rs.getString("decision_reason"), ResultSetSupport.nullableLong(rs, "created_class_id"),
                ResultSetSupport.instant(rs, "resolved_at"));
    }

    static AdditionalClassAssignmentOffer mapAssignmentOffer(ResultSet rs) throws SQLException {
        return new AdditionalClassAssignmentOffer(rs.getLong("offer_id"), rs.getLong("request_id"),
                rs.getLong("teacher_id"), rs.getLong("offered_by"),
                AssignmentOfferStatus.valueOf(rs.getString("status")),
                ResultSetSupport.instant(rs, "sent_at"), ResultSetSupport.instant(rs, "expires_at"),
                ResultSetSupport.instant(rs, "responded_at"), rs.getString("response_note"));
    }
}
