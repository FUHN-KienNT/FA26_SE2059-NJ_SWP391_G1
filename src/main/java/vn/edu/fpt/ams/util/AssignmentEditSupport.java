package vn.edu.fpt.ams.util;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import vn.edu.fpt.ams.dto.AssignmentForm;
import vn.edu.fpt.ams.model.*;
import vn.edu.fpt.ams.util.AssignmentDraftValidator.ValidationException;

/** Prefill a stored Draft and preserve its Question/option identities during editing. */
public final class AssignmentEditSupport {
    private AssignmentEditSupport() {}

    public static AssignmentForm form(Assignment a, List<Question> questions) {
        Map<String,String> values = new HashMap<>();
        values.put("assignmentId", Long.toString(a.assignmentId()));
        values.put("classId", Long.toString(a.classId()));
        values.put("templateId", Long.toString(a.templateId()));
        values.put("title", text(a.title())); values.put("instructions", text(a.instructions()));
        values.put("purpose", a.assignmentPurpose().name()); values.put("responseType", a.responseType().name());
        values.put("maximumScore", a.maximumScore().stripTrailingZeros().toPlainString());
        values.put("availableFrom", localTime(a.availableFrom())); values.put("dueAt", localTime(a.dueAt()));
        values.put("timeLimitMinutes", text(a.timeLimitMinutes())); values.put("attemptLimit", text(a.attemptLimit()));
        JsonArray tasks = new JsonArray();
        for (JsonElement element : array(a.taskChecklistJson())) {
            JsonObject task = new JsonObject();
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                task.addProperty("title", element.getAsString()); task.addProperty("description", "");
                task.addProperty("maximum_points", "");
            } else if (element.isJsonObject()) {
                JsonObject source = element.getAsJsonObject();
                String title = "";
                for (String key : List.of("title", "text", "task", "description")) {
                    title = string(source, key);
                    if (!title.isBlank()) break;
                }
                String description = string(source, "description");
                if (!description.isBlank() && !description.equals(title)) title += "\n" + description;
                task.addProperty("title", title); task.addProperty("description", "");
                JsonElement points = source.get("maximum_points");
                task.addProperty("maximum_points", points != null && points.isJsonPrimitive()
                        ? points.getAsString() : "");
            } else throw new IllegalStateException("Nội dung câu hỏi tự luận đã lưu cần được sửa lại.");
            tasks.add(task);
        }
        values.put("tasksJson", tasks.toString());
        JsonArray editorQuestions = new JsonArray();
        for (Question question : questions) {
            JsonObject q = new JsonObject();
            q.addProperty("question_id", question.questionId()); q.addProperty("question_text", question.questionText());
            q.addProperty("maximum_points", question.maximumPoints().toPlainString());
            JsonArray options = new JsonArray();
            for (JsonElement element : array(question.options())) {
                JsonObject option = new JsonObject();
                if (element.isJsonObject()) {
                    JsonObject source = element.getAsJsonObject();
                    if (source.has("option_id")) option.add("option_id", source.get("option_id").deepCopy());
                    option.addProperty("option_text", string(source,"option_text"));
                    JsonElement correct = source.get("is_correct");
                    if (correct == null || !correct.isJsonPrimitive() || !correct.getAsJsonPrimitive().isBoolean())
                        throw new IllegalStateException("Các lựa chọn trả lời đã lưu cần được sửa lại.");
                    option.addProperty("is_correct", correct.getAsBoolean());
                } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    // Support old text-only option arrays when the Teacher repairs a Draft.
                    option.addProperty("option_text", element.getAsString()); option.addProperty("is_correct", false);
                } else throw new IllegalStateException("Các lựa chọn trả lời đã lưu cần được sửa lại.");
                options.add(option);
            }
            q.add("options", options); editorQuestions.add(q);
        }
        values.put("questionsJson", editorQuestions.toString());
        values.put("revision", revision(a, questions));
        return new AssignmentForm(values);
    }

    public static String revision(Assignment a, List<Question> questions) {
        // Length-safe scalar serialization; includes every saved field and nested row.
        List<String> fields = new ArrayList<>(List.of(Long.toString(a.assignmentId()), Long.toString(a.classId()),
                Long.toString(a.templateId()), text(a.title()), a.assignmentPurpose().name(), a.responseType().name(),
                text(a.instructions()), text(a.scopeText()), text(a.taskChecklistJson()), a.maximumScore().toPlainString(),
                a.status().name(), text(a.availableFrom()), text(a.dueAt()), text(a.timeLimitMinutes()),
                text(a.attemptLimit()), text(a.publishedAt()), text(a.cancelledAt())));
        for (Question q : questions) fields.addAll(List.of(Long.toString(q.questionId()), Long.toString(q.assignmentId()),
                Integer.toString(q.questionOrder()), text(q.questionText()), q.maximumPoints().toPlainString(), text(q.options())));
        try {
            byte[] json = new Gson().toJson(fields).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    public static AssignmentDraftValidator.Draft validate(AssignmentForm form, List<Question> existing) {
        var draft = AssignmentDraftValidator.validate(form);
        if (draft.assignment().responseType() != ResponseType.OBJECTIVE) return draft;
        Map<Long,Question> byId = new HashMap<>(); existing.forEach(q -> byId.put(q.questionId(),q));
        Set<Long> retained = new HashSet<>(); List<Question> saved = new ArrayList<>();
        try {
            JsonArray inputs = array(form.value("questionsJson"));
            for (int i=0; i<inputs.size(); i++) {
                JsonObject input = inputs.get(i).getAsJsonObject();
                long id = input.has("question_id") ? Long.parseLong(input.get("question_id").getAsString()) : 0;
                if (id < 0 || (id > 0 && (!byId.containsKey(id) || !retained.add(id)))) throw new IllegalArgumentException();
                Map<String,JsonElement> oldIds = new HashMap<>();
                if (id > 0) for (JsonElement option : array(byId.get(id).options())) {
                    if (option.isJsonObject() && option.getAsJsonObject().has("option_id")) {
                        JsonElement oldId = option.getAsJsonObject().get("option_id");
                        oldIds.put(optionKey(oldId), oldId);
                    }
                }
                Question validated = draft.questions().get(i);
                JsonArray options = array(validated.options()); JsonArray rawOptions = input.getAsJsonArray("options");
                Set<String> keptOptions = new HashSet<>();
                for (int j=0; j<options.size(); j++) {
                    JsonObject raw = rawOptions.get(j).getAsJsonObject();
                    JsonObject option = options.get(j).getAsJsonObject();
                    if (raw.has("option_id")) {
                        String key = optionKey(raw.get("option_id"));
                        if (!oldIds.containsKey(key) || !keptOptions.add(key)) throw new IllegalArgumentException();
                        option.add("option_id", oldIds.get(key).deepCopy());
                    } else option.addProperty("option_id", UUID.randomUUID().toString());
                }
                saved.add(new Question(id, 0, i+1, validated.questionText(), validated.maximumPoints(), options.toString()));
            }
        } catch (RuntimeException ex) {
            throw new ValidationException(Map.of("questionsJson", "Mã câu hỏi hoặc lựa chọn không hợp lệ. Chỉ sử dụng các câu hỏi và lựa chọn thuộc bản nháp này."));
        }
        return new AssignmentDraftValidator.Draft(draft.assignment(), List.copyOf(saved));
    }

    private static String optionKey(JsonElement id) {
        if (id == null || !id.isJsonPrimitive() || !(id.getAsJsonPrimitive().isString() || id.getAsJsonPrimitive().isNumber())
                || id.getAsString().isBlank()) throw new IllegalArgumentException();
        return id.getAsString();
    }
    private static String string(JsonObject object, String key) {
        JsonElement value=object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
    private static String text(Object value) { return value == null ? "" : value.toString(); }
    private static String localTime(Instant value) {
        return value == null ? "" : LocalDateTime.ofInstant(value, ZoneId.of("Asia/Ho_Chi_Minh")).toString();
    }
    private static JsonArray array(String json) {
        if (json == null || json.isBlank()) return new JsonArray();
        try { return JsonParser.parseString(json).getAsJsonArray(); }
        catch (RuntimeException ex) { throw new IllegalStateException("Nội dung bản nháp đã lưu cần được sửa lại.",ex); }
    }
}
