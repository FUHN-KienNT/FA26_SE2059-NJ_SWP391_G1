package vn.edu.fpt.ams.dto;

import java.time.Instant;

/** Một lời mời dạy lớp bổ sung, kèm lớp nguồn và người gửi để giảng viên đọc trước khi nhận hoặc từ chối. */
public record AssignmentOfferView(long offerId, long requestId, String status,
                                  String overflowContext, String classCode, String className,
                                  String subjectCode, String subjectName, String offeredByName,
                                  Instant sentAt, Instant expiresAt, Instant respondedAt,
                                  String responseNote) {
    /** Còn là lời mời PENDING nên giảng viên vẫn nhận hoặc từ chối được. */
    public boolean open() {
        return "PENDING".equals(status);
    }
}
