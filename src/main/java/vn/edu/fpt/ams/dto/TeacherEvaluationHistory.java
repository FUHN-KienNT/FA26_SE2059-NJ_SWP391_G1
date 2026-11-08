package vn.edu.fpt.ams.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Teacher-only grade journal, including records for work replaced by a later submission. */
public record TeacherEvaluationHistory(List<Entry> entries, long total, int page) {
    public static final int PAGE_SIZE = 10;

    public TeacherEvaluationHistory {
        entries = List.copyOf(entries);
    }

    public long pages() {
        return Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    public static TeacherEvaluationHistory empty() {
        return new TeacherEvaluationHistory(List.of(), 0, 1);
    }

    public record Entry(
            long id,
            Long submissionId,
            String actor,
            String action,
            String reason,
            BigDecimal previousScore,
            BigDecimal nextScore,
            String previousStatus,
            String nextStatus,
            Instant createdAt) {}
}
