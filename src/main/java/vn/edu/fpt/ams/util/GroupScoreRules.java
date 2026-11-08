package vn.edu.fpt.ams.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Scores remain on the assignment scale; only the final weighted score is rounded. */
public final class GroupScoreRules {
    private GroupScoreRules() {}

    public static BigDecimal average(List<BigDecimal> received) {
        if(received.isEmpty()) return BigDecimal.ZERO;
        return received.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(received.size()), 16, RoundingMode.HALF_UP);
    }

    public static BigDecimal finalScore(BigDecimal teacher, BigDecimal peer,
            BigDecimal teacherWeight, BigDecimal peerWeight) {
        if(teacher==null || peer==null || teacherWeight==null || peerWeight==null
                || teacher.signum()<0 || peer.signum()<0 || teacherWeight.signum()<0
                || peerWeight.signum()<0 || teacherWeight.add(peerWeight).signum()<=0)
            throw new IllegalArgumentException("Điểm hoặc hệ số không hợp lệ.");
        weights(teacherWeight,peerWeight);
        return teacher.multiply(teacherWeight).add(peer.multiply(peerWeight))
                .divide(teacherWeight.add(peerWeight),2,RoundingMode.HALF_UP);
    }

    public static BigDecimal finalScoreFromTotals(BigDecimal teacher,BigDecimal peerSum,long received,
            BigDecimal teacherWeight,BigDecimal peerWeight) {
        weights(teacherWeight,peerWeight);
        if(teacher==null || peerSum==null || teacher.signum()<0 || peerSum.signum()<0 || received<0)
            throw new IllegalArgumentException("Điểm hoặc số đánh giá không hợp lệ.");
        if(received==0) return finalScore(teacher,BigDecimal.ZERO,teacherWeight,peerWeight);
        BigDecimal count=BigDecimal.valueOf(received);
        // Keep the peer mean as an exact rational until the final two-decimal rounding.
        return teacher.multiply(teacherWeight).multiply(count).add(peerSum.multiply(peerWeight))
                .divide(teacherWeight.add(peerWeight).multiply(count),2,RoundingMode.HALF_UP);
    }

    private static void weights(BigDecimal teacher,BigDecimal peer) {
        if(teacher==null || peer==null || teacher.signum()<0 || peer.signum()<0
                || teacher.compareTo(new BigDecimal("100"))>0 || peer.compareTo(new BigDecimal("100"))>0
                || teacher.stripTrailingZeros().scale()>2 || peer.stripTrailingZeros().scale()>2
                || teacher.add(peer).compareTo(new BigDecimal("100"))!=0)
            throw new IllegalArgumentException("Tổng tỉ trọng giáo viên và sinh viên phải bằng 100%.");
    }

    public static BigDecimal score(String raw, BigDecimal maximum) {
        if(raw==null || !raw.matches("[0-9]{1,3}(\\.[0-9]{1,2})?"))
            throw new IllegalArgumentException("Điểm phải là số từ 0 đến điểm tối đa, có tối đa 2 chữ số thập phân.");
        var value=new BigDecimal(raw);
        if(value.compareTo(maximum)>0)
            throw new IllegalArgumentException("Điểm không được vượt quá điểm tối đa của bài.");
        return value;
    }
}
