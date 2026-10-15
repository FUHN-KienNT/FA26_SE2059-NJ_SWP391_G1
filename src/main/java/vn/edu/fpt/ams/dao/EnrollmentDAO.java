package vn.edu.fpt.ams.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import vn.edu.fpt.ams.dto.EnrollmentDTO;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.DBConnection;

/** JDBC boundary for class placement episodes and active roster counts. */
public class EnrollmentDAO {
    private static UnsupportedOperationException pending() {
        return new UnsupportedOperationException("TODO: implement Enrollment JDBC access");
    }

    public Optional<ClassEnrollment> findById(long enrollmentId) { throw pending(); }
    public List<ClassEnrollment> findByClassId(long classId) { throw pending(); }
    public Optional<ClassEnrollment> findActiveByRegistrationId(long registrationId) { throw pending(); }

    /** Kiểm tra lớp có đang được giao cho giảng viên này hay không. */
    public boolean teacherOwnsClass(long teacherId, long classId) {
        String sql = "SELECT 1 FROM subject_classes WHERE class_id = ? AND teacher_id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, classId);
            statement.setLong(2, teacherId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot verify class ownership", ex);
        }
    }

    /** Đọc sinh viên đã ghi danh vào lớp, kèm tên, email và trạng thái. */
    public List<EnrollmentDTO> listRoster(long classId) {
        String sql = """
                SELECT e.enrollment_id, e.registration_id, e.class_id, e.status,
                       u.user_id, u.full_name, u.email
                FROM class_enrollments e
                JOIN subject_registrations r ON r.registration_id = e.registration_id
                JOIN users u ON u.user_id = r.student_id
                WHERE e.class_id = ?
                ORDER BY u.full_name, e.enrollment_id
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, classId);
            try (ResultSet rows = statement.executeQuery()) {
                List<EnrollmentDTO> roster = new ArrayList<>();
                while (rows.next()) {
                    roster.add(new EnrollmentDTO(rows.getLong("enrollment_id"),
                            rows.getLong("registration_id"), rows.getLong("class_id"),
                            rows.getLong("user_id"), rows.getString("full_name"),
                            rows.getString("email"),
                            EnrollmentStatus.valueOf(rows.getString("status"))));
                }
                return roster;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot load class roster", ex);
        }
    }

    /** Đếm ghi danh đang ACTIVE của một lớp. */
    public long countActiveStudents(long classId) {
        return count(classId, """
                SELECT count(*) FROM class_enrollments
                WHERE class_id = ? AND status = 'ACTIVE'
                """);
    }

    /** Đếm lời mời PENDING còn hạn, vì mỗi lời mời đó đang giữ một chỗ. */
    public long countActiveReservedInvitationSeats(long classId) {
        return count(classId, """
                SELECT count(*) FROM student_invitations
                WHERE class_id = ? AND status = 'PENDING' AND expires_at > CURRENT_TIMESTAMP
                """);
    }

    /** Mở một transaction, khóa lớp, kiểm tra chỗ trống rồi tạo ghi danh cho sinh viên đã có tài khoản. */
    public long placeExistingStudent(long teacherId, long classId, long studentId, EnrollmentMethod method) {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long enrollmentId = insertPlacement(connection, teacherId, classId, studentId, method);
                connection.commit();
                return enrollmentId;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                if (ex instanceof IllegalArgumentException business) throw business;
                throw new IllegalStateException("Cannot place student", ex);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot place student", ex);
        }
    }

    /** Mở một transaction rồi đổi trạng thái một ghi danh thuộc lớp của giảng viên này. */
    public void updateOwnedStatus(long teacherId, long enrollmentId, EnrollmentStatus status) {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                applyStatus(connection, teacherId, enrollmentId, status);
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                if (ex instanceof IllegalArgumentException business) throw business;
                throw new IllegalStateException("Cannot update enrollment status", ex);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot update enrollment status", ex);
        }
    }

    public long insertEnrollment(ClassEnrollment enrollment) { throw pending(); }
    public void updateStatus(long enrollmentId, EnrollmentStatus status) { throw pending(); }

    public Optional<StudentInvitation> findInvitationByToken(String token) { throw pending(); }

    /** Đọc mọi lời mời của một lớp, lời mời mới gửi đứng trước. */
    public List<StudentInvitation> findInvitationsByClass(long classId) {
        String sql = """
                SELECT * FROM student_invitations
                WHERE class_id = ?
                ORDER BY sent_at DESC, invitation_id DESC
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, classId);
            try (ResultSet rows = statement.executeQuery()) {
                List<StudentInvitation> invitations = new ArrayList<>();
                while (rows.next()) invitations.add(mapInvitation(rows));
                return invitations;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot load invitations", ex);
        }
    }

    /** Chạy một câu đếm theo class_id và trả về số đếm được. */
    private long count(long classId, String sql) {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, classId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot count class occupancy", ex);
        }
    }

    /** Khóa lớp, từ chối nếu sinh viên đã có ghi danh đang học, rồi mới kiểm tra sĩ số và chèn ghi danh. */
    private long insertPlacement(Connection connection, long teacherId, long classId, long studentId,
                                 EnrollmentMethod method) throws SQLException {
        long subjectTermId;
        int capacity;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT subject_term_id, capacity, status FROM subject_classes
                WHERE class_id = ? AND teacher_id = ? FOR UPDATE
                """)) {
            statement.setLong(1, classId);
            statement.setLong(2, teacherId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("This class is not assigned to the current teacher");
                if (!"ACTIVE".equals(rows.getString("status"))) {
                    throw new IllegalArgumentException("Students can be added only to an active class");
                }
                subjectTermId = rows.getLong("subject_term_id");
                capacity = rows.getInt("capacity");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT role, account_status FROM users WHERE user_id = ?")) {
            statement.setLong(1, studentId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || !"STUDENT".equals(rows.getString("role"))
                        || !"ACTIVE".equals(rows.getString("account_status"))) {
                    throw new IllegalArgumentException("An active Student account is required");
                }
            }
        }

        Long registrationId = null;
        String registrationStatus = null;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT registration_id, status FROM subject_registrations
                WHERE student_id = ? AND subject_term_id = ? FOR UPDATE
                """)) {
            statement.setLong(1, studentId);
            statement.setLong(2, subjectTermId);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    registrationId = rows.getLong("registration_id");
                    registrationStatus = rows.getString("status");
                }
            }
        }
        if (registrationId != null) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT enrollment_id FROM class_enrollments
                    WHERE registration_id = ? AND status = 'ACTIVE'
                    """)) {
                statement.setLong(1, registrationId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) {
                        throw new IllegalArgumentException("This student already has an active enrollment for the subject term");
                    }
                }
            }
        }
        long occupied = countOn(connection, """
                SELECT count(*) FROM class_enrollments WHERE class_id = ? AND status = 'ACTIVE'
                """, classId) + countOn(connection, """
                SELECT count(*) FROM student_invitations
                WHERE class_id = ? AND status = 'PENDING' AND expires_at > CURRENT_TIMESTAMP
                """, classId);
        if (occupied >= capacity) throw new IllegalArgumentException("The class has no available seat");
        if (registrationId == null) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO subject_registrations (student_id, subject_term_id, status)
                    VALUES (?, ?, 'ACTIVE')
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, studentId);
                statement.setLong(2, subjectTermId);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    registrationId = keys.getLong(1);
                }
            }
        } else if ("INACTIVE".equals(registrationStatus)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE subject_registrations
                    SET status = 'ACTIVE', deactivated_at = NULL
                    WHERE registration_id = ?
                    """)) {
                statement.setLong(1, registrationId);
                statement.executeUpdate();
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT enrollment_id FROM class_enrollments
                WHERE registration_id = ? AND status = 'ACTIVE'
                """)) {
            statement.setLong(1, registrationId);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    throw new IllegalArgumentException("This student already has an active enrollment for the subject term");
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO class_enrollments (registration_id, class_id, enrollment_method)
                VALUES (?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, registrationId);
            statement.setLong(2, classId);
            statement.setString(3, method.name());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** Khóa ghi danh của giảng viên này, kiểm tra chỗ trống khi bật lại, rồi ghi trạng thái mới. */
    private void applyStatus(Connection connection, long teacherId, long enrollmentId,
                             EnrollmentStatus status) throws SQLException {
        long classId;
        long registrationId;
        String current;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT e.class_id, e.registration_id, e.status
                FROM class_enrollments e
                JOIN subject_classes c ON c.class_id = e.class_id
                WHERE e.enrollment_id = ? AND c.teacher_id = ?
                FOR UPDATE OF e
                """)) {
            statement.setLong(1, enrollmentId);
            statement.setLong(2, teacherId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("Enrollment was not found for this teacher");
                classId = rows.getLong("class_id");
                registrationId = rows.getLong("registration_id");
                current = rows.getString("status");
            }
        }
        if (status.name().equals(current)) return;
        if (status == EnrollmentStatus.ACTIVE) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT capacity, status FROM subject_classes WHERE class_id = ? FOR UPDATE
                    """)) {
                statement.setLong(1, classId);
                try (ResultSet rows = statement.executeQuery()) {
                    rows.next();
                    if (!"ACTIVE".equals(rows.getString("status"))) {
                        throw new IllegalArgumentException("An inactive class cannot receive an active enrollment");
                    }
                    int capacity = rows.getInt("capacity");
                    long occupied = countOn(connection, """
                            SELECT count(*) FROM class_enrollments
                            WHERE class_id = ? AND status = 'ACTIVE' AND enrollment_id <> ?
                            """, classId, enrollmentId) + countOn(connection, """
                            SELECT count(*) FROM student_invitations
                            WHERE class_id = ? AND status = 'PENDING' AND expires_at > CURRENT_TIMESTAMP
                            """, classId);
                    if (occupied >= capacity) throw new IllegalArgumentException("The class has no available seat");
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT enrollment_id FROM class_enrollments
                    WHERE registration_id = ? AND status = 'ACTIVE' AND enrollment_id <> ?
                    """)) {
                statement.setLong(1, registrationId);
                statement.setLong(2, enrollmentId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) {
                        throw new IllegalArgumentException("This student already has an active enrollment for the subject term");
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE subject_registrations
                    SET status = 'ACTIVE', deactivated_at = NULL
                    WHERE registration_id = ? AND status = 'INACTIVE'
                    """)) {
                statement.setLong(1, registrationId);
                statement.executeUpdate();
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE class_enrollments
                SET status = ?, deactivated_at = CASE WHEN ? = 'INACTIVE' THEN CURRENT_TIMESTAMP ELSE NULL END
                WHERE enrollment_id = ?
                """)) {
            statement.setString(1, status.name());
            statement.setString(2, status.name());
            statement.setLong(3, enrollmentId);
            statement.executeUpdate();
        }
    }

    /** Đếm trên connection đang mở, dùng khi transaction đã khóa lớp. */
    private long countOn(Connection connection, String sql, long classId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, classId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    /** Đếm trên connection đang mở và bỏ qua một ghi danh, để không tính chính dòng sắp bật lại. */
    private long countOn(Connection connection, String sql, long classId, long excludedEnrollmentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, classId);
            statement.setLong(2, excludedEnrollmentId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }
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
