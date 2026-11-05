package vn.edu.fpt.ams.dto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import vn.edu.fpt.ams.util.AssignmentRules;

/** Immutable, versioned Essay evaluation. A null score is an unfinished requirement. */
public record SubmissionEvaluation(long revision, List<SubmissionEvaluation.Mark> marks, String feedback) {
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    public static final String FORMAT = "ams-evaluation-v1";

    public SubmissionEvaluation {
        if (revision < 0) throw invalid("Phiên bản chấm bài không hợp lệ.");
        Objects.requireNonNull(marks, "Cần có danh sách điểm.");
        if (marks.size() > AssignmentRules.MAX_TASKS * AssignmentRules.MAX_REQUIREMENTS)
            throw invalid("Có quá nhiều điểm yêu cầu.");
        var keys = new HashSet<String>();
        for (Mark mark : marks) {
            if (mark == null || !keys.add(mark.taskOrder() + ":" + mark.requirementOrder()))
                throw invalid("Yêu cầu chấm điểm không được trùng nhau.");
        }
        marks = marks.stream().sorted(Comparator.comparingInt(Mark::taskOrder)
                .thenComparingInt(Mark::requirementOrder)).toList();
        feedback = text(feedback, 5000, "Nhận xét chung chỉ được có tối đa 5000 ký tự.");
    }

    public record Mark(int taskOrder, int requirementOrder, BigDecimal score, String feedback) {
        public Mark {
            if (taskOrder <= 0 || requirementOrder <= 0)
                throw invalid("Số thứ tự câu hỏi và yêu cầu phải là số nguyên dương.");
            if (score != null && (score.signum() < 0 || score.compareTo(BigDecimal.valueOf(100)) > 0
                    || score.stripTrailingZeros().scale() > 2))
                throw invalid("Điểm yêu cầu phải từ 0 đến 100 và có tối đa 2 chữ số thập phân.");
            if (score != null) score = score.setScale(2);
            feedback = text(feedback, 2000, "Nhận xét yêu cầu chỉ được có tối đa 2000 ký tự.");
        }
    }

    public static SubmissionEvaluation read(String json, long revision, String feedback) {
        try {
            JsonElement parsed = JSON.fromJson(json, JsonElement.class);
            if (parsed == null || !parsed.isJsonObject()) throw invalid("Dữ liệu chấm bài không hợp lệ.");
            JsonObject root = parsed.getAsJsonObject();
            if (!FORMAT.equals(string(root.get("format"))) || !root.has("marks") || !root.get("marks").isJsonArray())
                throw invalid("Định dạng chấm bài không hợp lệ.");
            var result = new java.util.ArrayList<Mark>();
            for (JsonElement entry : root.getAsJsonArray("marks")) {
                if (!entry.isJsonObject()) throw invalid("Điểm yêu cầu không hợp lệ.");
                JsonObject mark = entry.getAsJsonObject();
                JsonElement rawScore = mark.get("score");
                BigDecimal score = rawScore == null || rawScore.isJsonNull() ? null : number(rawScore);
                result.add(new Mark(number(mark.get("task_order")).intValueExact(),
                        number(mark.get("requirement_order")).intValueExact(), score,
                        string(mark.get("feedback"))));
            }
            return new SubmissionEvaluation(revision, result, feedback);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Dữ liệu chấm bài đã lưu không hợp lệ.", ex);
        }
    }

    public String json() {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        JsonArray entries = new JsonArray();
        for (Mark mark : marks) {
            JsonObject entry = new JsonObject();
            entry.addProperty("task_order", mark.taskOrder());
            entry.addProperty("requirement_order", mark.requirementOrder());
            entry.addProperty("score", mark.score());
            entry.addProperty("feedback", mark.feedback());
            entries.add(entry);
        }
        root.add("marks", entries);
        return root.toString();
    }

    public BigDecimal total() {
        return marks.stream().map(Mark::score).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
    }

    public Mark mark(int taskOrder, int requirementOrder) {
        return marks.stream().filter(mark -> mark.taskOrder() == taskOrder && mark.requirementOrder() == requirementOrder)
                .findFirst().orElse(null);
    }

    private static BigDecimal number(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw invalid("Điểm và số thứ tự phải là số hợp lệ.");
        return value.getAsBigDecimal();
    }

    private static String string(JsonElement value) {
        if (value == null || value.isJsonNull()) return "";
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw invalid("Nhận xét phải là văn bản.");
        return value.getAsString();
    }

    private static String text(String value, int maximum, String message) {
        String text = AssignmentRules.normalizeLines(value);
        if (AssignmentRules.length(text) > maximum) throw invalid(message);
        return text;
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
