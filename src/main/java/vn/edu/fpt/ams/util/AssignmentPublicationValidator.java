package vn.edu.fpt.ams.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import vn.edu.fpt.ams.model.Assignment;
import vn.edu.fpt.ams.model.AssignmentPurpose;
import vn.edu.fpt.ams.model.Question;
import vn.edu.fpt.ams.model.ResponseType;

/** Checks saved publication content without rewriting fields, order values or identities. */
public final class AssignmentPublicationValidator {
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();

    private AssignmentPublicationValidator() {}

    public static Map<String, String> errors(Assignment assignment, List<Question> questions, Instant now) {
        Objects.requireNonNull(now, "Cần có thời gian hiện tại.");
        Map<String, String> errors = new LinkedHashMap<>();
        if (assignment == null) {
            errors.put("assignmentId", "Chọn một bài tập đã lưu để công bố.");
            return immutable(errors);
        }

        if (assignment.assignmentId() <= 0)
            errors.put("assignmentId", "Chọn một bài tập đã lưu để công bố.");
        if (assignment.classId() <= 0) errors.put("classId", "Chọn lớp hợp lệ.");
        if (assignment.templateId() <= 0) errors.put("templateId", "Chọn mẫu bài tập hợp lệ.");
        String title = assignment.title();
        if (blank(title) || title.codePointCount(0, title.length()) > 200)
            errors.put("title", "Nhập tiêu đề từ 1 đến 200 ký tự.");
        if (assignment.assignmentPurpose() == null) errors.put("purpose", "Chọn mục đích bài tập hợp lệ.");
        if (assignment.responseType() == null) errors.put("responseType", "Chọn hình thức trả lời hợp lệ.");
        if (assignment.assignmentPurpose() == AssignmentPurpose.PRACTICE
                && assignment.responseType() != ResponseType.OBJECTIVE)
            errors.put("responseType", "Bài luyện tập phải sử dụng câu hỏi trắc nghiệm.");
        boolean validMaximum = AssignmentRules.validMaximum(assignment.maximumScore());
        if (!validMaximum) errors.put("maximumScore", "Điểm tối đa phải là số nguyên từ 1 đến 100.");
        if (blank(assignment.instructions()))
            errors.put("instructions", "Nhập hướng dẫn trước khi công bố.");
        else if (AssignmentRules.length(assignment.instructions()) > AssignmentRules.MAX_INSTRUCTIONS)
            errors.put("instructions", "Hướng dẫn không được vượt quá 500 ký tự.");
        if (assignment.availableFrom() == null)
            errors.put("availableFrom", "Nhập thời gian mở bài trước khi công bố.");
        if (assignment.dueAt() == null) errors.put("dueAt", "Nhập hạn nộp trước khi công bố.");
        else if (!assignment.dueAt().isAfter(now)
                || (assignment.availableFrom() != null && !assignment.dueAt().isAfter(assignment.availableFrom())))
            errors.put("dueAt", "Hạn nộp phải sau thời gian mở bài và thời gian hiện tại.");
        if (assignment.timeLimitMinutes() != null && (assignment.timeLimitMinutes() < AssignmentRules.MIN_MINUTES
                || assignment.timeLimitMinutes() > AssignmentRules.MAX_MINUTES))
            errors.put("timeLimitMinutes", "Thời gian làm bài phải từ 20 đến 720 phút (12 giờ).");
        if (assignment.assignmentPurpose() == AssignmentPurpose.REGULAR && assignment.attemptLimit() != null)
            errors.put("attemptLimit", "Giới hạn số lượt làm chỉ áp dụng cho bài luyện tập.");
        if (assignment.assignmentPurpose() == AssignmentPurpose.PRACTICE
                && (assignment.attemptLimit() == null || assignment.attemptLimit() <= 0))
            errors.put("attemptLimit", "Nhập số lượt làm tối đa từ 1 trở lên cho bài luyện tập.");

        List<Question> savedQuestions = questions == null ? List.of() : questions;
        if (assignment.responseType() == ResponseType.ESSAY) {
            validateTasks(assignment, errors);
            if (!savedQuestions.isEmpty())
                errors.put("questionsJson", "Bài tự luận không được chứa câu hỏi trắc nghiệm. Hãy sửa bản nháp để loại bỏ chúng.");
        } else if (assignment.responseType() == ResponseType.OBJECTIVE) {
            validateNoTasks(assignment.taskChecklistJson(), errors);
            validateQuestions(assignment, savedQuestions, validMaximum, errors);
        }
        return immutable(errors);
    }

    private static void validateTasks(Assignment assignment, Map<String, String> errors) {
        JsonArray tasks = array(assignment.taskChecklistJson());
        if (tasks == null) {
            errors.put("tasksJson", "Kiểm tra các câu hỏi tự luận đã lưu. Mỗi câu cần nội dung và số thứ tự nguyên dương, không trùng nhau.");
            return;
        }
        if (tasks.isEmpty() || tasks.size() > AssignmentRules.MAX_TASKS) {
            errors.put("tasksJson", "Thêm từ 1 đến 200 câu hỏi tự luận trước khi công bố.");
            return;
        }
        Set<Integer> orders = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean validTotal = true;
        for (int i = 0; i < tasks.size(); i++) {
            JsonElement element = tasks.get(i);
            // Existing detail/edit views support legacy text tasks, whose order is their array position.
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                taskError(errors, i, "chưa có điểm. Hãy sửa bản nháp để thêm điểm.");
                validTotal = false;
            } else if (element.isJsonObject()) {
                JsonObject task = element.getAsJsonObject();
                Integer order = positiveInteger(task.get("task_order"));
                if (order == null || !orders.add(order)) taskError(errors, i, "cần số thứ tự nguyên dương, không trùng nhau.");
                if (!requiredText(task.get("title"))) taskError(errors, i, "cần nội dung không để trống.");
                JsonElement description = task.get("description");
                if (description != null && !description.isJsonNull() && !string(description))
                    taskError(errors, i, "có nội dung mô tả cũ không hợp lệ. Hãy sửa lại bản nháp.");
                BigDecimal points = decimal(task.get("maximum_points"));
                if (!validPoints(points)) { taskError(errors, i, "cần điểm hợp lệ với tối đa 2 chữ số thập phân."); validTotal = false; }
                else total = total.add(points);
            } else taskError(errors, i, "không hợp lệ. Hãy kiểm tra nội dung và thứ tự câu hỏi.");
        }
        if (validTotal && AssignmentRules.validMaximum(assignment.maximumScore())
                && total.compareTo(assignment.maximumScore()) != 0)
            errors.putIfAbsent("tasksJson", "Tổng điểm các câu hỏi phải bằng điểm tối đa của bài tập.");
    }

    private static void validateNoTasks(String json, Map<String, String> errors) {
        JsonArray tasks = array(json);
        if (tasks == null || !tasks.isEmpty())
            errors.put("tasksJson", "Bài trắc nghiệm không được chứa câu hỏi tự luận. Hãy sửa bản nháp để loại bỏ chúng.");
    }

    private static void validateQuestions(Assignment assignment, List<Question> questions, boolean validMaximum,
            Map<String, String> errors) {
        if (questions.isEmpty() || questions.size() > AssignmentRules.MAX_QUESTIONS) {
            errors.put("questionsJson", "Thêm từ 1 đến 500 câu hỏi trắc nghiệm trước khi công bố.");
            return;
        }
        Set<Long> ids = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean validTotal = true;
        for (int i = 0; i < questions.size(); i++) {
            Question question = questions.get(i);
            if (question == null) {
                questionError(errors, i, "bị thiếu. Hãy sửa danh sách câu hỏi đã lưu.");
                validTotal = false;
                continue;
            }
            if (question.questionId() <= 0 || !ids.add(question.questionId()))
                questionError(errors, i, "cần mã định danh nguyên dương, không trùng nhau.");
            if (question.assignmentId() <= 0 || question.assignmentId() != assignment.assignmentId())
                questionError(errors, i, "không thuộc bài tập này.");
            if (question.questionOrder() <= 0 || !orders.add(question.questionOrder()))
                questionError(errors, i, "cần số thứ tự nguyên dương, không trùng nhau.");
            if (blank(question.questionText())) questionError(errors, i, "cần nội dung câu hỏi không để trống.");
            if (!validPoints(question.maximumPoints())) {
                questionError(errors, i, "có điểm không hợp lệ. " + pointsMessage());
                validTotal = false;
            } else total = total.add(question.maximumPoints());
            validateOptions(question.options(), i, errors);
        }
        if (validMaximum && validTotal && total.compareTo(assignment.maximumScore()) != 0)
            errors.putIfAbsent("questionsJson", "Tổng điểm các câu hỏi phải bằng điểm tối đa của bài tập.");
    }

    private static void validateOptions(String json, int questionIndex, Map<String, String> errors) {
        JsonArray options = array(json);
        if (options == null || options.size() < 2 || options.size() > AssignmentRules.MAX_OPTIONS) {
            questionError(errors, questionIndex, "cần từ 2 đến 10 lựa chọn trả lời hợp lệ.");
            return;
        }
        Set<String> ids = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        int correctCount = 0;
        for (int i = 0; i < options.size(); i++) {
            JsonElement element = options.get(i);
            String prefix = "lựa chọn " + (i + 1) + " ";
            if (!element.isJsonObject()) {
                questionError(errors, questionIndex, prefix + "không hợp lệ. Hãy sửa các lựa chọn đã lưu.");
                continue;
            }
            JsonObject option = element.getAsJsonObject();
            JsonElement id = option.get("option_id");
            if (id == null || !id.isJsonPrimitive()
                    || !(id.getAsJsonPrimitive().isString() || id.getAsJsonPrimitive().isNumber())
                    || blank(id.getAsString()) || !ids.add(id.getAsString()))
                questionError(errors, questionIndex, prefix + "cần mã định danh không để trống và không trùng nhau.");
            Integer order = positiveInteger(option.get("option_order"));
            if (order == null || !orders.add(order))
                questionError(errors, questionIndex, prefix + "cần số thứ tự nguyên dương, không trùng nhau.");
            if (!requiredText(option.get("option_text")))
                questionError(errors, questionIndex, prefix + "cần nội dung trả lời không để trống.");
            JsonElement correct = option.get("is_correct");
            if (correct == null || !correct.isJsonPrimitive() || !correct.getAsJsonPrimitive().isBoolean())
                questionError(errors, questionIndex, prefix + "cần trạng thái đáp án đúng hợp lệ.");
            else if (correct.getAsBoolean()) correctCount++;
        }
        if (correctCount < 1) questionError(errors, questionIndex, "phải có ít nhất một đáp án đúng.");
    }

    private static JsonArray array(String json) {
        if (blank(json)) return new JsonArray();
        try {
            JsonElement root = JSON.fromJson(json, JsonElement.class);
            return root != null && root.isJsonArray() ? root.getAsJsonArray() : null;
        } catch (JsonParseException | IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean validPoints(BigDecimal points) {
        return AssignmentRules.validPoints(points);
    }

    private static BigDecimal decimal(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return null;
        try { return value.getAsBigDecimal(); } catch (NumberFormatException ex) { return null; }
    }

    private static Integer positiveInteger(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return null;
        try {
            int value = element.getAsBigDecimal().intValueExact();
            return value > 0 ? value : null;
        } catch (ArithmeticException | NumberFormatException ex) {
            return null;
        }
    }

    private static boolean requiredText(JsonElement value) { return string(value) && !blank(value.getAsString()); }
    private static boolean string(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String pointsMessage() {
        return "Điểm phải từ 0.01 đến 100, có tối đa 2 chữ số thập phân.";
    }
    private static void taskError(Map<String, String> errors, int index, String message) {
        errors.putIfAbsent("tasksJson", "Câu tự luận " + (index + 1) + " " + message);
    }
    private static void questionError(Map<String, String> errors, int index, String message) {
        errors.putIfAbsent("questionsJson", "Câu hỏi " + (index + 1) + " " + message);
    }
    private static Map<String, String> immutable(Map<String, String> errors) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }
}
