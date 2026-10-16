package vn.edu.fpt.ams.util;

import com.google.gson.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import vn.edu.fpt.ams.dto.AssignmentForm;
import vn.edu.fpt.ams.model.*;

/** Draft validation is deliberately separate from publication readiness. */
public final class AssignmentDraftValidator {
    private AssignmentDraftValidator() {}
    public record Draft(Assignment assignment, List<Question> questions) {}
    public static final class ValidationException extends IllegalArgumentException {
        private final Map<String, String> errors;
        public ValidationException(Map<String, String> errors) {
            super("Vui lòng sửa các trường được đánh dấu.");
            this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
        }
        public Map<String, String> errors() { return errors; }
    }

    public static Draft validate(AssignmentForm form) {
        Map<String, String> errors = new LinkedHashMap<>();
        long classId = id(form.value("classId"), "classId", errors);
        long templateId = id(form.value("templateId"), "templateId", errors);
        String title = form.value("title").strip();
        if (title.isBlank() || title.codePointCount(0, title.length()) > 200)
            errors.put("title", "Nhập tiêu đề từ 1 đến 200 ký tự.");
        AssignmentPurpose purpose = enumValue(AssignmentPurpose.class, form.value("purpose"), "purpose", errors);
        ResponseType response = enumValue(ResponseType.class, form.value("responseType"), "responseType", errors);
        if (purpose == AssignmentPurpose.PRACTICE && response != ResponseType.OBJECTIVE)
            errors.put("responseType", "Bài luyện tập phải sử dụng câu hỏi trắc nghiệm.");
        BigDecimal score = maximumScore(form.value("maximumScore"), errors);
        if (AssignmentRules.length(form.value("instructions")) > AssignmentRules.MAX_INSTRUCTIONS)
            errors.put("instructions", "Hướng dẫn không được vượt quá 500 ký tự.");
        Instant opens = time(form.value("availableFrom"), "availableFrom", errors);
        Instant due = time(form.value("dueAt"), "dueAt", errors);
        if (opens != null && due != null && !due.isAfter(opens))
            errors.put("dueAt", "Hạn nộp phải sau thời gian mở bài.");
        Integer minutes = positiveInteger(form.value("timeLimitMinutes"), "timeLimitMinutes", errors);
        if (minutes != null && (minutes < AssignmentRules.MIN_MINUTES || minutes > AssignmentRules.MAX_MINUTES))
            errors.put("timeLimitMinutes", "Thời gian làm bài phải từ 20 đến 720 phút (12 giờ).");
        Integer attempts = positiveInteger(form.value("attemptLimit"), "attemptLimit", errors);
        if (purpose == AssignmentPurpose.PRACTICE && attempts == null)
            errors.put("attemptLimit", "Số lượt làm tối đa phải là số nguyên từ 1 trở lên.");
        if (purpose == AssignmentPurpose.REGULAR && attempts != null)
            errors.put("attemptLimit", "Giới hạn số lượt làm chỉ áp dụng cho bài luyện tập.");

        JsonArray tasks = new JsonArray();
        if (response == ResponseType.ESSAY) try {
            JsonArray entered = array(form.value("tasksJson"), AssignmentRules.MAX_TASKS);
            if (entered.isEmpty()) errors.put("tasksJson", "Thêm từ 1 đến 200 câu hỏi tự luận.");
            BigDecimal total = BigDecimal.ZERO;
            boolean validTotal = true;
            for (int i = 0; i < entered.size(); i++) {
                JsonObject source = object(entered.get(i));
                JsonObject task = new JsonObject();
                task.addProperty("task_order", i + 1);
                String content = requiredText(source, "title");
                String oldDescription = text(source, "description").strip();
                // Older forms stored a separate description. Keep that text in the question.
                if (!oldDescription.isBlank() && !oldDescription.equals(content)) content += "\n" + oldDescription;
                task.addProperty("title", AssignmentRules.normalizeLines(content));
                task.addProperty("description", "");
                BigDecimal taskPoints = points(text(source, "maximum_points"), "tasksJson", errors);
                if (taskPoints == null) validTotal = false;
                else { task.addProperty("maximum_points", taskPoints); total = total.add(taskPoints); }
                tasks.add(task);
            }
            if (validTotal && score != null && total.compareTo(score) != 0)
                errors.putIfAbsent("tasksJson", "Tổng điểm các câu hỏi phải bằng điểm tối đa của bài tập.");
        } catch (IllegalArgumentException ex) {
            errors.putIfAbsent("tasksJson", "Nhập từ 1 đến 200 câu hỏi tự luận, mỗi câu có nội dung và điểm hợp lệ.");
        }

        List<Question> questions = new ArrayList<>();
        if (response == ResponseType.OBJECTIVE) try {
            JsonArray entered = array(form.value("questionsJson"), AssignmentRules.MAX_QUESTIONS);
            if (entered.isEmpty()) errors.put("questionsJson", "Thêm từ 1 đến 500 câu hỏi trắc nghiệm.");
            BigDecimal total = BigDecimal.ZERO;
            int missingPoints = 0, missingIndex = -1;
            boolean validTotal = true;
            for (int i = 0; i < entered.size(); i++) {
                JsonObject source = object(entered.get(i));
                String questionText = requiredText(source, "question_text");
                String rawPoints = text(source, "maximum_points");
                BigDecimal questionPoints = null;
                if (rawPoints.isBlank()) { missingPoints++; missingIndex = i; }
                else {
                    questionPoints = points(rawPoints, "questionsJson", errors);
                    if (questionPoints == null) validTotal = false;
                    else total = total.add(questionPoints);
                }
                JsonElement rawOptions = source.get("options");
                if (rawOptions == null || !rawOptions.isJsonArray()) throw new IllegalArgumentException();
                if (rawOptions.getAsJsonArray().size() > AssignmentRules.MAX_OPTIONS) {
                    errors.put("questionsJson", "Mỗi câu hỏi có tối đa 10 lựa chọn trả lời.");
                    throw new IllegalArgumentException();
                }
                JsonArray options = new JsonArray();
                for (JsonElement element : rawOptions.getAsJsonArray()) {
                    JsonObject input = object(element);
                    JsonElement correct = input.get("is_correct");
                    if (correct == null || !correct.isJsonPrimitive()
                            || !correct.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException();
                    JsonObject option = new JsonObject();
                    // The server creates identities; future edits must preserve these IDs.
                    option.addProperty("option_id", options.size() + 1);
                    option.addProperty("option_order", options.size() + 1);
                    option.addProperty("option_text", requiredText(input, "option_text"));
                    option.addProperty("is_correct", correct.getAsBoolean());
                    options.add(option);
                }
                questions.add(new Question(0, 0, i + 1, questionText, questionPoints, options.toString()));
            }
            if (missingPoints == 1 && validTotal && score != null) {
                BigDecimal remainder = score.subtract(total);
                if (!AssignmentRules.validPoints(remainder)) {
                    errors.putIfAbsent("questionsJson", "Câu hỏi còn lại phải có ít nhất 0.01 điểm. Hãy điều chỉnh điểm các câu khác.");
                    validTotal = false;
                } else {
                    Question q = questions.get(missingIndex);
                    questions.set(missingIndex, new Question(q.questionId(), q.assignmentId(), q.questionOrder(),
                            q.questionText(), remainder, q.options()));
                    total = total.add(remainder);
                }
            } else if (missingPoints > 0) {
                errors.putIfAbsent("questionsJson", "Nhập điểm câu hỏi. Chỉ được để trống điểm của một câu để tự điền phần điểm còn lại.");
                validTotal = false;
            }
            if (validTotal && score != null && total.compareTo(score) != 0)
                errors.putIfAbsent("questionsJson", "Tổng điểm các câu hỏi phải bằng điểm tối đa của bài tập.");
        } catch (IllegalArgumentException ex) {
            errors.putIfAbsent("questionsJson", "Nhập từ 1 đến 500 câu hỏi trắc nghiệm, có nội dung, điểm hợp lệ và tối đa 10 lựa chọn mỗi câu.");
        }
        if (!errors.isEmpty()) throw new ValidationException(errors);
        return new Draft(new Assignment(0, classId, templateId, title, purpose, response,
                AssignmentRules.normalizeLines(form.value("instructions")),
                "", tasks.toString(), score,
                AssignmentStatus.DRAFT, opens, due, minutes, attempts, null, null), List.copyOf(questions));
    }

    private static long id(String value, String field, Map<String, String> errors) {
        try { return WebSupport.id(value); }
        catch (IllegalArgumentException ex) { errors.put(field, field.equals("classId") ? "Chọn lớp hợp lệ." : "Chọn mẫu bài tập hợp lệ."); return 0; }
    }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String field, Map<String, String> errors) {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException ex) { errors.put(field, "Chọn một giá trị hợp lệ."); return null; }
    }
    private static BigDecimal maximumScore(String value, Map<String, String> errors) {
        try {
            if (!value.strip().matches("[0-9]{1,3}")) throw new IllegalArgumentException();
            BigDecimal number = new BigDecimal(value.strip());
            if (!AssignmentRules.validMaximum(number)) throw new IllegalArgumentException();
            return number;
        } catch (IllegalArgumentException ex) {
            errors.put("maximumScore", "Điểm tối đa phải là số nguyên từ 1 đến 100.");
            return null;
        }
    }
    private static BigDecimal points(String value, String field, Map<String, String> errors) {
        try {
            if (!value.strip().matches("[0-9]{1,3}(\\.[0-9]{1,2})?")) throw new IllegalArgumentException();
            BigDecimal number = new BigDecimal(value.strip()).setScale(2, RoundingMode.UNNECESSARY);
            if (!AssignmentRules.validPoints(number)) throw new IllegalArgumentException();
            return number;
        } catch (IllegalArgumentException | ArithmeticException ex) {
            errors.putIfAbsent(field, "Điểm phải từ 0.01 đến 100, có tối đa 2 chữ số thập phân.");
            return null;
        }
    }
    private static Integer positiveInteger(String value, String field, Map<String, String> errors) {
        if (value.isBlank()) return null;
        try {
            if (!value.strip().matches("[0-9]{1,10}")) throw new IllegalArgumentException();
            int number = Integer.parseInt(value.strip());
            if (number <= 0) throw new IllegalArgumentException();
            return number;
        } catch (IllegalArgumentException ex) { errors.put(field, "Nhập một số nguyên dương hoặc để trống."); return null; }
    }
    private static Instant time(String value, String field, Map<String, String> errors) {
        if (value.isBlank()) return null;
        try { return LocalDateTime.parse(value).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(); }
        catch (DateTimeException ex) { errors.put(field, "Nhập ngày và giờ hợp lệ theo giờ Việt Nam (UTC+07)."); return null; }
    }
    private static JsonArray array(String value, int maximum) {
        if (value.isBlank()) return new JsonArray();
        try {
            JsonElement root = JsonParser.parseString(value);
            if (!root.isJsonArray() || root.getAsJsonArray().size() > maximum) throw new IllegalArgumentException();
            return root.getAsJsonArray();
        } catch (JsonParseException ex) { throw new IllegalArgumentException(ex); }
    }
    private static JsonObject object(JsonElement value) {
        if (!value.isJsonObject()) throw new IllegalArgumentException();
        return value.getAsJsonObject();
    }
    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null) return "";
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
        return value.getAsString();
    }
    private static String requiredText(JsonObject object, String key) {
        String value = text(object, key).strip();
        if (value.isBlank()) throw new IllegalArgumentException();
        return value;
    }
}
