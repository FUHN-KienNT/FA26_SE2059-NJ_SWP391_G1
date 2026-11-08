package vn.edu.fpt.ams.dto;

import java.math.BigDecimal;
import java.util.List;

/** Only the requesting student's own reviews are projected into this page. */
public record PeerReviewPage(List<Target> targets, long total, int page, Target selected,
        List<Review> reviews, int reviewPage, long reviewTotal) {
    public PeerReviewPage { targets=List.copyOf(targets); reviews=List.copyOf(reviews); }
    public int pages() { return (int)Math.max(1,(total+9)/10); }
    public int reviewPages() { return (int)Math.max(1,(reviewTotal+9)/10); }

    public record Target(long targetId, long assignmentId, String title, String groupName,
            String classCode, BigDecimal maximumScore, BigDecimal teacherWeight,
            BigDecimal peerWeight, boolean editable, String state) {}

    public record Review(long studentId, String name, BigDecimal score, String feedback, long revision) {}
}
