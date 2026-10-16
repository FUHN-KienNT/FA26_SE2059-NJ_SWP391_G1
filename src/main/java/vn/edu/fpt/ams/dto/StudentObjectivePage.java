package vn.edu.fpt.ams.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import vn.edu.fpt.ams.model.Assignment;
import vn.edu.fpt.ams.model.SubmissionStatus;

/** Student-facing data deliberately contains no answer key or unreleased score. */
public record StudentObjectivePage(Assignment assignment, String classCode,
        long submissionId, int attemptNumber, SubmissionStatus status, Instant deadline,
        List<Item> questions, Instant serverTime, Map<Long,List<String>> selected, long answerRevision) {
    public StudentObjectivePage {
        questions = List.copyOf(questions);
        selected = Map.copyOf(selected);
    }
    public record Option(String id, String text) { }
    public record Item(long id, int order, String text, BigDecimal points,
            boolean multiple, List<Option> options) {
        public Item { options = List.copyOf(options); }
    }
}
