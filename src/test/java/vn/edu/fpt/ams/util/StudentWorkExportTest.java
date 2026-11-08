package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.ams.dao.StudentResultDAO;
import vn.edu.fpt.ams.dto.StudentResultPage;

class StudentWorkExportTest {
    private static JsonObject snapshot(String type, String tasks) {
        JsonObject result = new JsonObject();
        result.addProperty("assignment_id", 7);
        result.addProperty("class_id", 9);
        result.addProperty("template_id", 1);
        result.addProperty("title", "Tình huống chung");
        result.addProperty("assignment_purpose", "REGULAR");
        result.addProperty("response_type", type);
        result.addProperty("instructions", "Hướng dẫn đề bài");
        result.add("task_checklist", JsonParser.parseString(tasks));
        result.addProperty("maximum_score", 10);
        result.addProperty("status", "PUBLISHED");
        return result;
    }

    private static StudentResultDAO.Row row(String assignment, String questions, String content, String answers) {
        var attempt = new StudentResultPage.Attempt(8, 7, 9, "Tình huống chung", "Lớp A", 2,
                Instant.now().minusSeconds(3600), false, null, null);
        return new StudentResultDAO.Row(attempt, BigDecimal.TEN, assignment, questions,
                content, answers, "SECRET_UNRELEASED_FEEDBACK", "SECRET_EVALUATION", 0);
    }

    @Test
    void essayExportContainsOwnAnswersButNoTeacherCriteriaOrUnreleasedEvaluation() {
        String tasks = """
                [{"task_order":1,"title":"Câu hỏi công khai","maximum_points":10,
                  "rubric":"SECRET_QUESTION_RUBRIC","requirements":[
                    {"requirement_order":1,"title":"Yêu cầu công khai","maximum_points":10,
                     "answer_type":"TEXT","rubric":"SECRET_REQUIREMENT_RUBRIC"}]}]
                """;
        JsonObject content = new JsonObject();
        content.addProperty("format", EssayAnswerCodec.FORMAT);
        content.add("answers", JsonParser.parseString("""
                [{"task_order":1,"requirement_order":1,"text":"Bài làm <sinh viên>",
                  "diagram_xml":"","diagram_image":""}]
                """));
        String html = StudentWorkExport.html(row(snapshot("ESSAY", tasks).toString(), "[]",
                content.toString(), "[]"));

        assertTrue(html.contains("Câu hỏi công khai"));
        assertTrue(html.contains("Bài làm &lt;sinh viên&gt;"));
        assertFalse(html.contains("SECRET_"));
        assertFalse(html.contains("rubric"));
    }

    @Test
    void objectiveExportMarksOwnSelectionWithoutExportingAnswerKeysOrAwardedPoints() {
        String questions = """
                [{"question_id":11,"assignment_id":7,"question_order":1,"question_text":"Chọn đáp án",
                  "maximum_points":10,"options":[
                    {"option_id":"A","option_text":"Lựa chọn A","is_correct":false},
                    {"option_id":"B","option_text":"Lựa chọn B","is_correct":true}]}]
                """;
        String answers = """
                [{"question_id":11,"selected_option_ids":["A"],"awarded_points":0}]
                """;
        String html = StudentWorkExport.html(row(snapshot("OBJECTIVE", "[]").toString(),
                questions, null, answers));
        assertTrue(html.contains("☑ Lựa chọn A"));
        assertTrue(html.contains("☐ Lựa chọn B"));
        assertFalse(html.contains("is_correct"));
        assertFalse(html.contains("awarded_points"));
        assertFalse(html.contains("SECRET_"));
    }
}
