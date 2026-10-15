package vn.edu.fpt.ams.service;

import java.util.List;
import vn.edu.fpt.ams.dao.AssignmentOfferDAO;
import vn.edu.fpt.ams.dto.AssignmentOfferView;
import vn.edu.fpt.ams.model.AssignmentOfferStatus;
import vn.edu.fpt.ams.util.TextMatch;

/** Giảng viên xem và phản hồi lời mời dạy lớp bổ sung gửi cho mình. */
public class AssignmentOfferService {
    private final AssignmentOfferDAO offerDAO = new AssignmentOfferDAO();
    private final TeacherDashboardService dashboardService = new TeacherDashboardService();

    /** Hết hạn các lời mời quá giờ rồi trả về những lời mời khớp từ khóa. */
    public List<AssignmentOfferView> list(long teacherId, String query) {
        dashboardService.requireActiveTeacher(teacherId);
        offerDAO.expireDue(teacherId);
        return offerDAO.listForTeacher(teacherId).stream()
                .filter(offer -> TextMatch.contains(query, offer.classCode(), offer.className(),
                        offer.subjectCode(), offer.subjectName(), offer.overflowContext(),
                        offer.offeredByName(), offer.status(), statusLabel(offer.status())))
                .toList();
    }

    /** Đếm lời mời còn hạn sau khi đã chuyển các lời mời quá giờ sang hết hạn. */
    public long openCount(long teacherId) {
        dashboardService.requireActiveTeacher(teacherId);
        offerDAO.expireDue(teacherId);
        return offerDAO.countOpen(teacherId);
    }

    /** Nhận hoặc từ chối một lời mời còn hạn. Ghi chú trống thì không lưu. */
    public void respond(long teacherId, long offerId, AssignmentOfferStatus decision, String note) {
        dashboardService.requireActiveTeacher(teacherId);
        if (offerId <= 0) throw new IllegalArgumentException("Offer ID must be positive");
        String stored = note == null || note.isBlank() ? null : note.trim();
        if (stored != null && stored.length() > 500) {
            throw new IllegalArgumentException("Ghi chú không được dài quá 500 ký tự");
        }
        offerDAO.respond(teacherId, offerId, decision, stored);
    }

    /** Nhãn tiếng Việt của trạng thái lời mời. */
    public static String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "PENDING" -> "Chờ phản hồi";
            case "ACCEPTED" -> "Đã nhận";
            case "DECLINED" -> "Đã từ chối";
            case "EXPIRED" -> "Hết hạn";
            case "CANCELLED" -> "Đã hủy";
            default -> status == null ? "" : status;
        };
    }
}
