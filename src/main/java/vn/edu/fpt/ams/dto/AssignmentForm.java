package vn.edu.fpt.ams.dto;

import java.util.List;
import java.util.Map;

/** Keep entered text unchanged so a validation error does not erase the form. */
public record AssignmentForm(Map<String, String> values) {
    public static final List<String> FIELDS =
            List.of(
                    "classId",
                    "targetClassIds",
                    "templateId",
                    "title",
                    "instructions",
                    "purpose",
                    "responseType",
                    "maximumScore",
                    "availableFrom",
                    "dueAt",
                    "timeLimitMinutes",
                    "attemptLimit",
                    "tasksJson",
                    "questionsJson",
                    "assignmentId",
                    "revision");

    public AssignmentForm {
        values = Map.copyOf(values);
    }

    public String value(String name) {
        return values.getOrDefault(name, "");
    }

    public List<Long> selectedClassIds() {
        if (value("targetClassIds").isBlank())
            return List.of(vn.edu.fpt.ams.util.WebSupport.id(value("classId")));

        java.util.Set<Long> ids = new java.util.TreeSet<>();
        String[] entries = value("targetClassIds").split(",", -1);
        if (entries.length > 500) throw new IllegalArgumentException("Chọn tối đa 500 lớp mỗi lần.");
        for (String entry : entries) ids.add(vn.edu.fpt.ams.util.WebSupport.id(entry));

        return List.copyOf(ids);
    }

    public static AssignmentForm empty(long classId) {
        return new AssignmentForm(
                Map.of(
                        "classId",
                        Long.toString(classId),
                        "purpose",
                        "REGULAR",
                        "responseType",
                        "ESSAY",
                        "tasksJson",
                        "[]",
                        "questionsJson",
                        "[]"));
    }
}
