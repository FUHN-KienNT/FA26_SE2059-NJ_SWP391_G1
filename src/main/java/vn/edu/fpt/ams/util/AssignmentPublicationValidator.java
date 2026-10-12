package vn.edu.fpt.ams.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private static final BigDecimal MAXIMUM_POINTS = new BigDecimal("99999999.99");
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();

    private AssignmentPublicationValidator() {}

    public static Map<String, String> errors(Assignment assignment, List<Question> questions, Instant now) {
        Objects.requireNonNull(now, "Current time is required.");
        Map<String, String> errors = new LinkedHashMap<>();
        if (assignment == null) {
            errors.put("assignmentId", "Select a saved assignment to publish.");
            return immutable(errors);
        }

        if (assignment.assignmentId() <= 0)
            errors.put("assignmentId", "Select a saved assignment to publish.");
        if (assignment.classId() <= 0) errors.put("classId", "Select a valid class.");
        if (assignment.templateId() <= 0) errors.put("templateId", "Select a valid source template.");
        String title = assignment.title();
        if (blank(title) || title.codePointCount(0, title.length()) > 200)
            errors.put("title", "Enter a title of 1–200 characters.");
        if (assignment.assignmentPurpose() == null) errors.put("purpose", "Select a valid assignment purpose.");
        if (assignment.responseType() == null) errors.put("responseType", "Select a valid response type.");
        if (assignment.assignmentPurpose() == AssignmentPurpose.PRACTICE
                && assignment.responseType() != ResponseType.OBJECTIVE)
            errors.put("responseType", "Practice assignments must use Objective questions.");
        boolean validMaximum = validPoints(assignment.maximumScore());
        if (!validMaximum) errors.put("maximumScore", pointsMessage());
        if (blank(assignment.instructions()))
            errors.put("instructions", "Enter instructions before publishing.");
        if (assignment.availableFrom() == null)
            errors.put("availableFrom", "Enter Available from before publishing.");
        if (assignment.dueAt() == null) errors.put("dueAt", "Enter Due at before publishing.");
        else if (!assignment.dueAt().isAfter(now)
                || (assignment.availableFrom() != null && !assignment.dueAt().isAfter(assignment.availableFrom())))
            errors.put("dueAt", "Due at must be later than Available from and the current time.");
        if (assignment.timeLimitMinutes() != null && assignment.timeLimitMinutes() <= 0)
            errors.put("timeLimitMinutes", "Enter a positive time limit or leave it blank.");
        if (assignment.assignmentPurpose() == AssignmentPurpose.REGULAR && assignment.attemptLimit() != null)
            errors.put("attemptLimit", "Attempt limit applies only to Practice assignments.");
        if (assignment.assignmentPurpose() == AssignmentPurpose.PRACTICE
                && (assignment.attemptLimit() == null || assignment.attemptLimit() <= 0))
            errors.put("attemptLimit", "Enter a positive attempt limit for Practice assignments.");

        List<Question> savedQuestions = questions == null ? List.of() : questions;
        if (assignment.responseType() == ResponseType.ESSAY) {
            if (blank(assignment.scopeText())) errors.put("scopeText", "Enter Essay scope before publishing.");
            validateTasks(assignment.taskChecklistJson(), errors);
            if (!savedQuestions.isEmpty())
                errors.put("questionsJson", "Essay assignments cannot contain Objective questions. Edit the draft to remove them.");
        } else if (assignment.responseType() == ResponseType.OBJECTIVE) {
            if (!blank(assignment.scopeText()))
                errors.put("scopeText", "Objective assignments cannot contain Essay scope. Edit the draft to remove it.");
            validateNoTasks(assignment.taskChecklistJson(), errors);
            validateQuestions(assignment, savedQuestions, validMaximum, errors);
        }
        return immutable(errors);
    }

    private static void validateTasks(String json, Map<String, String> errors) {
        JsonArray tasks = array(json);
        if (tasks == null) {
            errors.put("tasksJson", "Check the saved task list. Each task needs a title and a positive, unique order.");
            return;
        }
        if (tasks.isEmpty()) {
            errors.put("tasksJson", "Add at least one Essay task before publishing.");
            return;
        }
        Set<Integer> orders = new HashSet<>();
        for (int i = 0; i < tasks.size(); i++) {
            JsonElement element = tasks.get(i);
            // Existing detail/edit views support legacy text tasks, whose order is their array position.
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                if (blank(element.getAsString()) || !orders.add(i + 1))
                    taskError(errors, i, "needs a title and a unique order.");
            } else if (element.isJsonObject()) {
                JsonObject task = element.getAsJsonObject();
                Integer order = positiveInteger(task.get("task_order"));
                if (order == null || !orders.add(order)) taskError(errors, i, "needs a positive, unique order.");
                if (!requiredText(task.get("title"))) taskError(errors, i, "needs a nonblank title.");
                JsonElement description = task.get("description");
                if (description != null && !description.isJsonNull() && !string(description))
                    taskError(errors, i, "has an invalid description. Enter text or leave it blank.");
            } else taskError(errors, i, "is invalid. Enter a task title and order.");
        }
    }

    private static void validateNoTasks(String json, Map<String, String> errors) {
        JsonArray tasks = array(json);
        if (tasks == null || !tasks.isEmpty())
            errors.put("tasksJson", "Objective assignments cannot contain Essay tasks. Edit the draft to remove them.");
    }

    private static void validateQuestions(Assignment assignment, List<Question> questions, boolean validMaximum,
            Map<String, String> errors) {
        if (questions.isEmpty()) {
            errors.put("questionsJson", "Add at least one Objective question before publishing.");
            return;
        }
        Set<Long> ids = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean validTotal = true;
        for (int i = 0; i < questions.size(); i++) {
            Question question = questions.get(i);
            if (question == null) {
                questionError(errors, i, "is missing. Repair the saved question list.");
                validTotal = false;
                continue;
            }
            if (question.questionId() <= 0 || !ids.add(question.questionId()))
                questionError(errors, i, "needs a positive, unique saved identity.");
            if (question.assignmentId() <= 0 || question.assignmentId() != assignment.assignmentId())
                questionError(errors, i, "does not belong to this assignment.");
            if (question.questionOrder() <= 0 || !orders.add(question.questionOrder()))
                questionError(errors, i, "needs a positive, unique order.");
            if (blank(question.questionText())) questionError(errors, i, "needs nonblank question text.");
            if (!validPoints(question.maximumPoints())) {
                questionError(errors, i, "has invalid points. " + pointsMessage());
                validTotal = false;
            } else total = total.add(question.maximumPoints());
            validateOptions(question.options(), i, errors);
        }
        if (validMaximum && validTotal && total.compareTo(assignment.maximumScore()) != 0)
            errors.putIfAbsent("questionsJson", "Question points must total the assignment maximum score.");
    }

    private static void validateOptions(String json, int questionIndex, Map<String, String> errors) {
        JsonArray options = array(json);
        if (options == null || options.size() < 2) {
            questionError(errors, questionIndex, "needs at least two valid answer options.");
            return;
        }
        Set<String> ids = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        int correctCount = 0;
        for (int i = 0; i < options.size(); i++) {
            JsonElement element = options.get(i);
            String prefix = "answer " + (i + 1) + " ";
            if (!element.isJsonObject()) {
                questionError(errors, questionIndex, prefix + "is invalid. Repair the saved answer options.");
                continue;
            }
            JsonObject option = element.getAsJsonObject();
            JsonElement id = option.get("option_id");
            if (id == null || !id.isJsonPrimitive()
                    || !(id.getAsJsonPrimitive().isString() || id.getAsJsonPrimitive().isNumber())
                    || blank(id.getAsString()) || !ids.add(id.getAsString()))
                questionError(errors, questionIndex, prefix + "needs a nonblank, unique saved identity.");
            Integer order = positiveInteger(option.get("option_order"));
            if (order == null || !orders.add(order))
                questionError(errors, questionIndex, prefix + "needs a positive, unique order.");
            if (!requiredText(option.get("option_text")))
                questionError(errors, questionIndex, prefix + "needs nonblank answer text.");
            JsonElement correct = option.get("is_correct");
            if (correct == null || !correct.isJsonPrimitive() || !correct.getAsJsonPrimitive().isBoolean())
                questionError(errors, questionIndex, prefix + "needs a valid correct-answer selection.");
            else if (correct.getAsBoolean()) correctCount++;
        }
        if (correctCount != 1) questionError(errors, questionIndex, "must have exactly one correct answer.");
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
        if (points == null || points.signum() <= 0 || points.compareTo(MAXIMUM_POINTS) > 0) return false;
        try {
            points.setScale(2, RoundingMode.UNNECESSARY);
            return true;
        } catch (ArithmeticException ex) {
            return false;
        }
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
        return "Points must be greater than 0, at most 99999999.99, with up to two decimal places.";
    }
    private static void taskError(Map<String, String> errors, int index, String message) {
        errors.putIfAbsent("tasksJson", "Task " + (index + 1) + " " + message);
    }
    private static void questionError(Map<String, String> errors, int index, String message) {
        errors.putIfAbsent("questionsJson", "Question " + (index + 1) + " " + message);
    }
    private static Map<String, String> immutable(Map<String, String> errors) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }
}
