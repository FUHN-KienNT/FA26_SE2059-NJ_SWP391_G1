package vn.edu.fpt.ams.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import vn.edu.fpt.ams.dto.AssignmentDetailPage;
import vn.edu.fpt.ams.dto.TeacherSubmissionRow;
import vn.edu.fpt.ams.model.*;

/** Restores the content saved before a previously published assignment was reopened for editing. */
public final class SubmissionContentSnapshot {
    private SubmissionContentSnapshot() { }

    public static AssignmentDetailPage displayPage(AssignmentDetailPage current, TeacherSubmissionRow row) {
        if (row.assignmentSnapshot() == null && row.questionsSnapshot() == null) return current;
        try {
            JsonObject a = JsonParser.parseString(row.assignmentSnapshot()).getAsJsonObject();
            long id = a.get("assignment_id").getAsLong();
            if (id != current.assignment().assignmentId() || id != row.submission().assignmentId())
                throw new IllegalArgumentException("Snapshot assignment mismatch.");
            Assignment assignment = new Assignment(id, a.get("class_id").getAsLong(),
                    a.get("template_id").getAsLong(), text(a, "title"),
                    AssignmentPurpose.valueOf(text(a, "assignment_purpose")),
                    ResponseType.valueOf(text(a, "response_type")), text(a, "instructions"),
                    text(a, "scope_text"), json(a, "task_checklist"), a.get("maximum_score").getAsBigDecimal(),
                    AssignmentStatus.valueOf(text(a, "status")), instant(a, "available_from"),
                    instant(a, "due_at"), integer(a, "time_limit_minutes"), integer(a, "attempt_limit"),
                    instant(a, "published_at"), instant(a, "cancelled_at"));
            List<Question> questions = new ArrayList<>();
            for (JsonElement element : JsonParser.parseString(row.questionsSnapshot()).getAsJsonArray()) {
                JsonObject q = element.getAsJsonObject();
                if (q.get("assignment_id").getAsLong() != id)
                    throw new IllegalArgumentException("Snapshot question mismatch.");
                questions.add(new Question(q.get("question_id").getAsLong(), id,
                        q.get("question_order").getAsInt(), text(q, "question_text"),
                        q.get("maximum_points").getAsBigDecimal(), json(q, "options")));
            }
            return new AssignmentDetailPage(assignment, current.classCode(), current.className(),
                    current.subjectCode(), current.subjectName(), current.termCode(), current.templateTitle(),
                    questions, current.canViewSubmissions());
        } catch (RuntimeException ex) {
            // Do not silently pair historical student answers with the edited current questions.
            throw new IllegalStateException("Saved submission content could not be loaded.", ex);
        }
    }

    public static BigDecimal maximumScore(TeacherSubmissionRow row, BigDecimal currentMaximum) {
        if (row.assignmentSnapshot() == null) return currentMaximum;
        try {
            return JsonParser.parseString(row.assignmentSnapshot()).getAsJsonObject()
                    .get("maximum_score").getAsBigDecimal();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Saved submission score limit could not be loaded.", ex);
        }
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }
    private static String json(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.toString();
    }
    private static Integer integer(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsInt();
    }
    private static Instant instant(JsonObject object, String key) {
        String value = text(object, key);
        return value == null ? null : OffsetDateTime.parse(value).toInstant();
    }
}
