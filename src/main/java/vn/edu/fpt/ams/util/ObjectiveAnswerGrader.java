package vn.edu.fpt.ams.util;

import com.google.gson.*;
import java.math.BigDecimal;
import java.util.*;
import vn.edu.fpt.ams.dto.StudentObjectivePage;
import vn.edu.fpt.ams.model.Question;

/** Exact-set marking: all correct options and no incorrect options earn the question's points. */
public final class ObjectiveAnswerGrader {
    private ObjectiveAnswerGrader() { }
    public record Result(String answerJson, BigDecimal score) { }
    private record Key(StudentObjectivePage.Item display, Set<String> correct) { }

    public static List<StudentObjectivePage.Item> display(List<Question> questions) {
        return keys(questions).stream().map(Key::display).toList();
    }

    public static Result grade(List<Question> questions, Map<Long,List<String>> answers) {
        List<Key> keys = keys(questions);
        Set<Long> validQuestions = new HashSet<>();
        keys.forEach(key -> validQuestions.add(key.display().id()));
        if (!validQuestions.containsAll(answers.keySet()))
            throw new IllegalArgumentException("Câu trả lời thuộc câu hỏi không có trong đề.");
        BigDecimal total = BigDecimal.ZERO;
        JsonArray saved = new JsonArray();
        for (Key key : keys) {
            var question = key.display();
            List<String> selected = answers.getOrDefault(question.id(), List.of());
            Set<String> unique = new HashSet<>(selected);
            Set<String> available = new HashSet<>();
            question.options().forEach(option -> available.add(option.id()));
            if (unique.size() != selected.size() || !available.containsAll(unique)
                    || (!question.multiple() && selected.size() > 1))
                throw new IllegalArgumentException("Vui lòng chọn đáp án hợp lệ cho từng câu hỏi.");
            BigDecimal points = unique.equals(key.correct()) ? question.points() : BigDecimal.ZERO;
            total = total.add(points);
            JsonObject answer = new JsonObject();
            answer.addProperty("question_id", question.id());
            JsonArray options = new JsonArray(); selected.forEach(options::add);
            answer.add("selected_option_ids", options);
            answer.addProperty("awarded_points", points);
            saved.add(answer);
        }
        return new Result(saved.toString(), total);
    }

    /** Validate against the frozen question set, but never store scores while work is in progress. */
    public static String draft(List<Question> questions, Map<Long,List<String>> answers) {
        JsonArray saved = JsonParser.parseString(grade(questions, answers).answerJson()).getAsJsonArray();
        saved.forEach(answer -> answer.getAsJsonObject().remove("awarded_points"));
        return saved.toString();
    }

    public static Map<Long,List<String>> selections(String answerJson) {
        Map<Long,List<String>> result = new LinkedHashMap<>();
        try {
            for (JsonElement element : JsonParser.parseString(answerJson).getAsJsonArray()) {
                JsonObject answer = element.getAsJsonObject();
                String questionId = identity(answer.get("question_id"));
                if(!questionId.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException();
                long question = Long.parseLong(questionId); // Never truncate decimal or overflowing IDs.
                List<String> options = new ArrayList<>();
                JsonElement selected = answer.get("selected_option_ids");
                JsonElement legacy = answer.get("selected_option_id");
                if(selected!=null && !selected.isJsonNull()) {
                    if(legacy!=null && !legacy.isJsonNull()) throw new IllegalArgumentException();
                    for(JsonElement option:selected.getAsJsonArray()) options.add(identity(option));
                } else if(legacy!=null && !legacy.isJsonNull()) {
                    options.add(identity(legacy));
                }
                if(new HashSet<>(options).size()!=options.size()) throw new IllegalArgumentException();
                if (result.put(question, List.copyOf(options)) != null) throw new IllegalArgumentException();
            }
            return Map.copyOf(result);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Không đọc được câu trả lời đã lưu. Vui lòng liên hệ giảng viên.", ex);
        }
    }

    private static String identity(JsonElement value) {
        if(value==null || !value.isJsonPrimitive()) throw new IllegalArgumentException();
        JsonPrimitive primitive=value.getAsJsonPrimitive();
        if(!primitive.isString() && !primitive.isNumber()) throw new IllegalArgumentException();
        String id=primitive.getAsString();
        if(id.isBlank()) throw new IllegalArgumentException();
        return id;
    }

    private static List<Key> keys(List<Question> questions) {
        if (questions.isEmpty() || questions.size() > AssignmentRules.MAX_QUESTIONS)
            throw new IllegalStateException("Danh sách câu hỏi hiện không khả dụng.");
        List<Key> result = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        try {
            for (Question question : questions) {
                if (question.questionId() <= 0 || !ids.add(question.questionId())
                        || !AssignmentRules.validPoints(question.maximumPoints())) throw new IllegalArgumentException();
                JsonArray options = JsonParser.parseString(question.options()).getAsJsonArray();
                if (options.size() < 2 || options.size() > AssignmentRules.MAX_OPTIONS) throw new IllegalArgumentException();
                Set<String> optionIds = new HashSet<>(), correct = new HashSet<>();
                List<StudentObjectivePage.Option> displayed = new ArrayList<>();
                for (JsonElement element : options) {
                    JsonObject option = element.getAsJsonObject();
                    String id = identity(option.get("option_id"));
                    if (id.isBlank() || !optionIds.add(id)) throw new IllegalArgumentException();
                    JsonPrimitive flag = option.getAsJsonPrimitive("is_correct");
                    if (!flag.isBoolean()) throw new IllegalArgumentException();
                    if (flag.getAsBoolean()) correct.add(id);
                    displayed.add(new StudentObjectivePage.Option(id, option.get("option_text").getAsString()));
                }
                if (correct.isEmpty()) throw new IllegalArgumentException();
                result.add(new Key(new StudentObjectivePage.Item(question.questionId(), question.questionOrder(),
                        question.questionText(), question.maximumPoints(), correct.size() > 1, displayed), correct));
            }
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Đáp án của đề không hợp lệ. Vui lòng liên hệ giảng viên.", ex);
        }
        return result;
    }
}
