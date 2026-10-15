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
            super("Please correct the highlighted fields.");
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
            errors.put("title", "Enter a title of 1–200 characters.");
        AssignmentPurpose purpose = enumValue(AssignmentPurpose.class, form.value("purpose"), "purpose", errors);
        ResponseType response = enumValue(ResponseType.class, form.value("responseType"), "responseType", errors);
        if (purpose == AssignmentPurpose.PRACTICE && response != ResponseType.OBJECTIVE)
            errors.put("responseType", "Practice assignments must use Objective questions.");
        BigDecimal score = points(form.value("maximumScore"), "maximumScore", errors);
        Instant opens = time(form.value("availableFrom"), "availableFrom", errors);
        Instant due = time(form.value("dueAt"), "dueAt", errors);
        if (opens != null && due != null && !due.isAfter(opens))
            errors.put("dueAt", "Due at must be later than Available from.");
        Integer minutes = positiveInteger(form.value("timeLimitMinutes"), "timeLimitMinutes", errors);
        Integer attempts = positiveInteger(form.value("attemptLimit"), "attemptLimit", errors);
        if (purpose == AssignmentPurpose.REGULAR && attempts != null)
            errors.put("attemptLimit", "Attempt limit applies only to Practice assignments.");

        JsonArray tasks = new JsonArray();
        if (response == ResponseType.ESSAY) try {
            JsonArray entered = array(form.value("tasksJson"));
            for (int i = 0; i < entered.size(); i++) {
                JsonObject source = object(entered.get(i));
                JsonObject task = new JsonObject();
                task.addProperty("task_order", i + 1);
                task.addProperty("title", requiredText(source, "title"));
                task.addProperty("description", text(source, "description"));
                tasks.add(task);
            }
        } catch (IllegalArgumentException ex) {
            errors.put("tasksJson", "Each task needs a title. Check the task list and try again.");
        }

        List<Question> questions = new ArrayList<>();
        if (response == ResponseType.OBJECTIVE) try {
            JsonArray entered = array(form.value("questionsJson"));
            for (int i = 0; i < entered.size(); i++) {
                JsonObject source = object(entered.get(i));
                String questionText = requiredText(source, "question_text");
                BigDecimal questionPoints = points(text(source, "maximum_points"), "questionsJson", errors);
                JsonElement rawOptions = source.get("options");
                if (rawOptions == null || !rawOptions.isJsonArray()) throw new IllegalArgumentException();
                JsonArray options = new JsonArray();
                int correctCount = 0;
                for (JsonElement element : rawOptions.getAsJsonArray()) {
                    JsonObject input = object(element);
                    JsonElement correct = input.get("is_correct");
                    if (correct == null || !correct.isJsonPrimitive()
                            || !correct.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException();
                    if (correct.getAsBoolean()) correctCount++;
                    JsonObject option = new JsonObject();
                    // The server creates identities; future edits must preserve these IDs.
                    option.addProperty("option_id", options.size() + 1);
                    option.addProperty("option_order", options.size() + 1);
                    option.addProperty("option_text", requiredText(input, "option_text"));
                    option.addProperty("is_correct", correct.getAsBoolean());
                    options.add(option);
                }
                if (correctCount > 1)
                    errors.put("questionsJson", "Choose at most one correct answer for each question.");
                questions.add(new Question(0, 0, i + 1, questionText, questionPoints, options.toString()));
            }
        } catch (IllegalArgumentException ex) {
            errors.put("questionsJson", "Check question text, points and answer options. Remove empty rows before saving.");
        }
        if (!errors.isEmpty()) throw new ValidationException(errors);
        return new Draft(new Assignment(0, classId, templateId, title, purpose, response,
                form.value("instructions"), response == ResponseType.ESSAY ? form.value("scopeText") : "", tasks.toString(), score,
                AssignmentStatus.DRAFT, opens, due, minutes, attempts, null, null), List.copyOf(questions));
    }

    private static long id(String value, String field, Map<String, String> errors) {
        try { return WebSupport.id(value); }
        catch (IllegalArgumentException ex) { errors.put(field, "Select a valid " + (field.equals("classId") ? "class." : "template.")); return 0; }
    }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String field, Map<String, String> errors) {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException ex) { errors.put(field, "Select a valid value."); return null; }
    }
    private static BigDecimal points(String value, String field, Map<String, String> errors) {
        try {
            if (!value.strip().matches("\\d{1,8}(\\.\\d{1,2})?")) throw new IllegalArgumentException();
            BigDecimal number = new BigDecimal(value.strip()).setScale(2, RoundingMode.UNNECESSARY);
            if (number.signum() <= 0 || number.compareTo(new BigDecimal("99999999.99")) > 0) throw new IllegalArgumentException();
            return number;
        } catch (IllegalArgumentException | ArithmeticException ex) {
            errors.put(field, "Points must be greater than 0, at most 99999999.99, with up to two decimal places.");
            return null;
        }
    }
    private static Integer positiveInteger(String value, String field, Map<String, String> errors) {
        if (value.isBlank()) return null;
        try {
            int number = Integer.parseInt(value.strip());
            if (number <= 0) throw new IllegalArgumentException();
            return number;
        } catch (IllegalArgumentException ex) { errors.put(field, "Enter a positive whole number or leave blank."); return null; }
    }
    private static Instant time(String value, String field, Map<String, String> errors) {
        if (value.isBlank()) return null;
        try { return LocalDateTime.parse(value).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(); }
        catch (DateTimeException ex) { errors.put(field, "Enter a valid date and time (UTC+07)."); return null; }
    }
    private static JsonArray array(String value) {
        if (value.isBlank()) return new JsonArray();
        try {
            JsonElement root = JsonParser.parseString(value);
            if (!root.isJsonArray() || root.getAsJsonArray().size() > 1000) throw new IllegalArgumentException();
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
