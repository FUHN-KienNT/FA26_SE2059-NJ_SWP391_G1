package vn.edu.fpt.ams.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import vn.edu.fpt.ams.dto.AssignmentOfferView;
import vn.edu.fpt.ams.model.AssignmentOfferStatus;
import vn.edu.fpt.ams.util.DBConnection;

/** Đọc và phản hồi lời mời dạy lớp bổ sung của một giảng viên. */
public class AssignmentOfferDAO {

    /** Chuyển lời mời PENDING đã quá hạn của giảng viên này sang EXPIRED. */
    public void expireDue(long teacherId) {
        String sql = """
                UPDATE additional_class_assignment_offers
                SET status = 'EXPIRED'
                WHERE teacher_id = ? AND status = 'PENDING' AND expires_at <= CURRENT_TIMESTAMP
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, teacherId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Không cập nhật được lời mời hết hạn", ex);
        }
    }

    /** Đếm lời mời PENDING còn hạn của giảng viên này. */
    public long countOpen(long teacherId) {
        String sql = """
                SELECT count(*) FROM additional_class_assignment_offers
                WHERE teacher_id = ? AND status = 'PENDING' AND expires_at > CURRENT_TIMESTAMP
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, teacherId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Không đếm được lời mời lớp bổ sung", ex);
        }
    }

    /** Đọc mọi lời mời gửi cho giảng viên, kèm lớp nguồn và người gửi, lời mời mới đứng trước. */
    public List<AssignmentOfferView> listForTeacher(long teacherId) {
        String sql = """
                SELECT o.offer_id, o.request_id, o.status, o.sent_at, o.expires_at, o.responded_at, o.response_note,
                       r.overflow_context, c.class_code, c.class_name, s.subject_code, s.subject_name,
                       u.full_name AS offered_by_name
                FROM additional_class_assignment_offers o
                JOIN additional_class_requests r ON r.request_id = o.request_id
                JOIN overflow_batches b ON b.overflow_batch_id = r.overflow_batch_id
                JOIN subject_classes c ON c.class_id = b.source_class_id
                JOIN subject_terms st ON st.subject_term_id = c.subject_term_id
                JOIN subjects s ON s.subject_id = st.subject_id
                JOIN users u ON u.user_id = o.offered_by
                WHERE o.teacher_id = ?
                ORDER BY o.sent_at DESC, o.offer_id DESC
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, teacherId);
            try (ResultSet rows = statement.executeQuery()) {
                List<AssignmentOfferView> offers = new ArrayList<>();
                while (rows.next()) offers.add(map(rows));
                return offers;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Không đọc được lời mời lớp bổ sung", ex);
        }
    }

    /** Khóa một lời mời của đúng giảng viên rồi nhận hoặc từ chối nếu lời mời còn PENDING và chưa hết hạn. */
    public void respond(long teacherId, long offerId, AssignmentOfferStatus decision, String note) {
        if (decision != AssignmentOfferStatus.ACCEPTED && decision != AssignmentOfferStatus.DECLINED) {
            throw new IllegalArgumentException("Chỉ nhận hoặc từ chối lời mời");
        }
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                applyResponse(connection, teacherId, offerId, decision, note);
                connection.commit();
            } catch (IllegalArgumentException ex) {
                connection.rollback();
                throw ex;
            } catch (SQLException ex) {
                connection.rollback();
                if ("23505".equals(ex.getSQLState())) {
                    throw new IllegalArgumentException("Yêu cầu này đã có giảng viên nhận lớp");
                }
                throw new IllegalStateException("Không cập nhật được lời mời lớp bổ sung", ex);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Không cập nhật được lời mời lớp bổ sung", ex);
        }
    }

    /** Khóa dòng lời mời, hết hạn nếu đã quá giờ, rồi ghi nhận hoặc từ chối. */
    private void applyResponse(Connection connection, long teacherId, long offerId,
                               AssignmentOfferStatus decision, String note) throws SQLException {
        String status;
        boolean expired;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT status, expires_at <= CURRENT_TIMESTAMP AS expired
                FROM additional_class_assignment_offers
                WHERE offer_id = ? AND teacher_id = ?
                FOR UPDATE
                """)) {
            statement.setLong(1, offerId);
            statement.setLong(2, teacherId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("Không tìm thấy lời mời này");
                status = rows.getString("status");
                expired = rows.getBoolean("expired");
            }
        }
        if (!"PENDING".equals(status)) {
            throw new IllegalArgumentException("Lời mời này đã được phản hồi hoặc không còn hiệu lực");
        }
        if (expired) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE additional_class_assignment_offers
                    SET status = 'EXPIRED'
                    WHERE offer_id = ? AND status = 'PENDING'
                    """)) {
                statement.setLong(1, offerId);
                statement.executeUpdate();
            }
            throw new IllegalArgumentException("Lời mời đã hết hạn");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE additional_class_assignment_offers
                SET status = ?, responded_at = CURRENT_TIMESTAMP, response_note = ?
                WHERE offer_id = ? AND teacher_id = ? AND status = 'PENDING' AND expires_at > CURRENT_TIMESTAMP
                """)) {
            statement.setString(1, decision.name());
            statement.setString(2, note);
            statement.setLong(3, offerId);
            statement.setLong(4, teacherId);
            if (statement.executeUpdate() != 1) throw new IllegalArgumentException("Lời mời đã hết hạn");
        }
    }

    /** Ghép một dòng kết quả thành lời mời hiển thị trên màn giảng viên. */
    private static AssignmentOfferView map(ResultSet rows) throws SQLException {
        return new AssignmentOfferView(
                rows.getLong("offer_id"), rows.getLong("request_id"), rows.getString("status"),
                rows.getString("overflow_context"), rows.getString("class_code"), rows.getString("class_name"),
                rows.getString("subject_code"), rows.getString("subject_name"), rows.getString("offered_by_name"),
                ResultSetSupport.instant(rows, "sent_at"), ResultSetSupport.instant(rows, "expires_at"),
                ResultSetSupport.instant(rows, "responded_at"), rows.getString("response_note"));
    }
}
