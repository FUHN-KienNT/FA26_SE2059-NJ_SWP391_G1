package vn.edu.fpt.ams.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import vn.edu.fpt.ams.dto.TeacherSubmissionFilter;
import vn.edu.fpt.ams.model.Question;
import vn.edu.fpt.ams.model.SubmissionStatus;

/** Plain display values. The views escape them before writing HTML. */
public final class TeacherSubmissionView {
    private TeacherSubmissionView() {}
    /** A null correct value means the stored answer key cannot be trusted. */
    public record Choice(String label, String text, boolean selected, Boolean correct) {}
    public record Answer(String question, String selectedOption, String awardedPoints, List<Choice> choices) {
        public Answer { choices = List.copyOf(choices); }
    }
    public record Answers(List<Answer> items, boolean invalid) {
        public Answers { items = List.copyOf(items); }
    }

    public static String statusLabel(SubmissionStatus status) {
        if (status == null) return "All";
        return switch (status) {
            case IN_PROGRESS -> "In progress";
            case SUBMITTED -> "Submitted";
            case GRADED -> "Graded";
            case RELEASED -> "Released";
            case EXPIRED -> "Expired";
            case VOID -> "Void";
        };
    }
    public static String submittedAt(Instant instant) {
        return instant == null ? "Not submitted" : AssignmentListView.deadline(instant);
    }
    public static String score(BigDecimal value) {
        return value == null ? "Not graded" : value.stripTrailingZeros().toPlainString();
    }
    public static String listUrl(String base, long assignmentId, TeacherSubmissionFilter filter) {
        String url = base + "/teacher/submissions?assignmentId=" + assignmentId;
        if (!filter.search().isEmpty())
            url += "&search=" + URLEncoder.encode(filter.search(), StandardCharsets.UTF_8);
        if (filter.status() != null) url += "&status=" + filter.status().name();
        return url;
    }
    public static String detailUrl(String base, long assignmentId, long submissionId, TeacherSubmissionFilter filter) {
        return listUrl(base, assignmentId, filter) + "&mode=view&submissionId=" + submissionId;
    }

    /** Stored URLs become links only when they are absolute HTTP(S) URLs. */
    public static String safeLink(String value) {
        if (value == null || value.isBlank()) return "";
        String link = value.strip();
        if (link.codePoints().anyMatch(Character::isISOControl)) return "";
        try {
            URI uri = new URI(link);
            if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null) return link;
        } catch (Exception ignored) { }
        return "";
    }

    /** Shows the frozen questions/options and saved selections without recalculating scores. */
    public static Answers answers(String json, List<Question> questions) {
        SavedAnswers saved = savedAnswers(json);
        Map<Long, SavedAnswer> remaining = new LinkedHashMap<>(saved.items());
        List<Answer> out = new ArrayList<>();
        boolean invalid = saved.invalid();
        List<Question> ordered = questions.stream()
                .sorted(Comparator.comparingInt(Question::questionOrder).thenComparingLong(Question::questionId))
                .toList();
        for (Question question : ordered) {
            SavedAnswer answer = remaining.remove(question.questionId());
            String selectedId = answer == null ? null : answer.optionId();
            StoredOptions options = options(question.options());
            invalid |= options.invalid();
            List<StoredOption> selected = options.items().stream()
                    .filter(option -> selectedId != null && selectedId.equals(option.id())).toList();
            boolean resolved = selected.size() == 1;
            String selectedText = selectedId == null ? "No answer selected"
                    : resolved ? selected.get(0).text() : "Option #" + selectedId + " (unavailable)";
            invalid |= selectedId != null && !resolved;
            List<Choice> choices = new ArrayList<>();
            for (int i = 0; i < options.items().size(); i++) {
                StoredOption option = options.items().get(i);
                choices.add(new Choice(choiceLabel(i), option.text(),
                        resolved && selectedId.equals(option.id()), options.invalid() ? null : option.correct()));
            }
            out.add(new Answer("Question " + question.questionOrder() + " — " + question.questionText(),
                    selectedText, answer == null ? "Not graded" : answer.points(), choices));
        }
        // Keep orphaned historical answers visible instead of silently losing submitted work.
        for (var entry : remaining.entrySet()) {
            SavedAnswer answer = entry.getValue();
            out.add(new Answer("Question #" + entry.getKey() + " (unavailable)",
                    answer.optionId() == null ? "No answer selected" : "Option #" + answer.optionId() + " (unavailable)",
                    answer.points(), List.of()));
            invalid = true;
        }
        return new Answers(out, invalid);
    }

    private record SavedAnswer(String optionId, String points) {}
    private record SavedAnswers(Map<Long, SavedAnswer> items, boolean invalid) {}

    private static SavedAnswers savedAnswers(String json) {
        Map<Long, SavedAnswer> items = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return new SavedAnswers(items, false);
        boolean invalid = false;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) return new SavedAnswers(items, true);
            for (JsonElement element : root.getAsJsonArray()) {
                try {
                    if (!element.isJsonObject()) throw new IllegalArgumentException();
                    var object = element.getAsJsonObject();
                    String idText = identity(object.get("question_id"));
                    if (idText == null || !idText.matches("[1-9][0-9]*")) throw new IllegalArgumentException();
                    long id = Long.parseLong(idText);
                    if (items.containsKey(id)) throw new IllegalArgumentException();
                    JsonElement savedOption = object.get("selected_option_id");
                    String selected = null;
                    if (savedOption != null && !savedOption.isJsonNull()) {
                        selected = identity(savedOption);
                        if (selected == null || selected.isBlank()) { selected = null; invalid = true; }
                    }
                    JsonElement points = object.get("awarded_points");
                    String awarded = "Not graded";
                    if (points != null && !points.isJsonNull()) {
                        try {
                            awarded = savedPoints(points);
                        } catch (RuntimeException ex) { invalid = true; }
                    }
                    items.put(id, new SavedAnswer(selected, awarded));
                } catch (RuntimeException ex) { invalid = true; }
            }
        } catch (RuntimeException ex) { invalid = true; }
        return new SavedAnswers(items, invalid);
    }

    private static String savedPoints(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || value.getAsString().length() > 64) throw new IllegalArgumentException();
        BigDecimal amount = value.getAsBigDecimal();
        // Bound the plain-text conversion before expanding a corrupt exponent such as 1e999999999.
        if (amount.signum() < 0 || amount.precision() > 20 || amount.scale() < -20 || amount.scale() > 20)
            throw new IllegalArgumentException();
        return score(amount);
    }

    private record StoredOption(String id, String text, Boolean correct, int order, int index) {}
    private record StoredOptions(List<StoredOption> items, boolean invalid) {}

    private static StoredOptions options(String json) {
        List<StoredOption> items = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        boolean invalid = false;
        if (json == null || json.isBlank()) return new StoredOptions(items, true);
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) return new StoredOptions(items, true);
            int index = 0;
            for (JsonElement element : root.getAsJsonArray()) {
                index++;
                if (!element.isJsonObject()) { invalid = true; continue; }
                var object = element.getAsJsonObject();
                String id = identity(object.get("option_id"));
                if (id == null || id.isBlank() || !ids.add(id)) invalid = true;
                JsonElement text = object.get("option_text");
                if (text == null || !text.isJsonPrimitive() || !text.getAsJsonPrimitive().isString()
                        || text.getAsString().isBlank()) { invalid = true; continue; }
                JsonElement correct = object.get("is_correct");
                Boolean key = correct != null && correct.isJsonPrimitive() && correct.getAsJsonPrimitive().isBoolean()
                        ? correct.getAsBoolean() : null;
                if (key == null) invalid = true;
                items.add(new StoredOption(id, text.getAsString(), key, optionOrder(object.get("option_order"), index), index));
            }
        } catch (RuntimeException ex) { invalid = true; }
        if (items.size() < 2 || items.stream().filter(option -> Boolean.TRUE.equals(option.correct())).count() != 1)
            invalid = true;
        items.sort(Comparator.comparingInt(StoredOption::order).thenComparingInt(StoredOption::index));
        return new StoredOptions(items, invalid);
    }

    private static int optionOrder(JsonElement value, int fallback) {
        String text = identity(value);
        if (text != null && text.matches("[1-9][0-9]*")) {
            try { return Integer.parseInt(text); } catch (NumberFormatException ignored) { }
        }
        return fallback;
    }

    private static String choiceLabel(int index) {
        StringBuilder label = new StringBuilder();
        for (int number = index + 1; number > 0; number = (number - 1) / 26)
            label.append((char) ('A' + (number - 1) % 26));
        return label.reverse().toString();
    }

    private static String identity(JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) return null;
        var primitive = value.getAsJsonPrimitive();
        return primitive.isString() || primitive.isNumber() ? primitive.getAsString() : null;
    }
}
