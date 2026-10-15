package vn.edu.fpt.ams.dto;

import java.util.List;
import java.util.Map;

/** Keep entered text unchanged so a validation error does not erase the form. */
public record AssignmentForm(Map<String, String> values) {
    public static final List<String> FIELDS = List.of("classId", "templateId", "title", "instructions",
            "purpose", "responseType", "maximumScore", "scopeText", "availableFrom", "dueAt",
            "timeLimitMinutes", "attemptLimit", "tasksJson", "questionsJson", "assignmentId", "revision");

    public AssignmentForm { values = Map.copyOf(values); }
    public String value(String name) { return values.getOrDefault(name, ""); }
    public static AssignmentForm empty(long classId) {
        return new AssignmentForm(Map.of("classId", Long.toString(classId), "purpose", "REGULAR",
                "responseType", "ESSAY", "tasksJson", "[]", "questionsJson", "[]"));
    }
}
