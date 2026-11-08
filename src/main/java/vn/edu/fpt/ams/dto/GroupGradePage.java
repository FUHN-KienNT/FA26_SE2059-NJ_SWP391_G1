package vn.edu.fpt.ams.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Frozen group roster and individual results for one teacher-owned shared submission. */
public record GroupGradePage(long targetId, String groupName, BigDecimal teacherWeight,
        BigDecimal peerWeight, long revision, boolean gradingOpen, Instant peerClosedAt,
        List<Member> members) {
    public GroupGradePage { members = List.copyOf(members); }

    public record Member(long studentId, String name, BigDecimal peerScore, BigDecimal peerSum,
            int receivedReviews, int eligibleReviews, BigDecimal finalScore) {}
}
