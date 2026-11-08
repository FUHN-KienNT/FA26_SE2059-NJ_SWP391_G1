package vn.edu.fpt.ams.util;

import com.google.gson.JsonParser;
import java.util.Map;
import vn.edu.fpt.ams.dao.StudentResultDAO;
import vn.edu.fpt.ams.model.Assignment;
import vn.edu.fpt.ams.model.ResponseType;

/** A self-contained copy of the student's work, with no evaluation or answer-key projection. */
public final class StudentWorkExport {
    private StudentWorkExport() {}

    public static String html(StudentResultDAO.Row row) {
        var out = new StringBuilder("""
                <!doctype html><html lang="vi"><head><meta charset="UTF-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Bản sao bài làm</title><style>
                body{font:16px/1.65 Arial,sans-serif;color:#172f4d;max-width:960px;margin:40px auto;padding:0 24px}
                article,section{border:1px solid #ced9e5;border-radius:8px;padding:20px;margin:20px 0}
                p{white-space:pre-wrap;overflow-wrap:anywhere}img{max-width:100%;height:auto}li{margin:12px 0}
                </style></head><body><h1>Bản sao bài làm</h1>
                """);
        out.append("<h2>").append(escape(row.attempt().title())).append("</h2>");
        paragraph(out, "Lớp " + row.attempt().classCode());
        paragraph(out, "Đã nộp " + AssignmentListView.deadline(row.attempt().submittedAt())
                + " (UTC+07)");

        Assignment assignment = null;

        if (row.assignmentSnapshot() != null) {
            try {
                assignment = SubmissionContentSnapshot.assignment(
                        row.assignmentSnapshot(), row.attempt().assignmentId());
            } catch (IllegalStateException ignored) {
                paragraph(out, "Bản chụp đề bài cũ hiện không khả dụng.");
            }
        }

        if (assignment != null) {
            out.append("<section><h2>Tình huống chung</h2>");
            paragraph(out, assignment.title());
            paragraph(out, assignment.instructions());
            out.append("</section>");

            if (assignment.responseType() == ResponseType.ESSAY) {
                essay(out, assignment, row.content());
            } else {
                objective(out, row);
            }
        } else if (row.content() != null) {
            out.append("<section><h2>Bài làm đã lưu</h2>");
            paragraph(out, row.content());
            out.append("</section>");
        } else {
            paragraph(out, "Không có bản chụp câu hỏi để đối chiếu bài trắc nghiệm cũ.");
        }

        return out.append("</body></html>").toString();
    }

    private static void essay(StringBuilder out, Assignment assignment, String content) {
        String publicQuestions;

        try {
            publicQuestions = EssayQuestions.studentJson(assignment.taskChecklistJson());
        } catch (IllegalArgumentException ex) {
            paragraph(out, "Đề bài đã lưu không thể đối chiếu theo từng yêu cầu.");
            paragraph(out, content);

            return;
        }

        var essay = EssaySubmissionView.read(publicQuestions, content);

        for (var task : essay.items()) {
            out.append("<article><h2>Câu ").append(task.order()).append("</h2>");
            paragraph(out, task.title());

            for (var requirement : task.requirements()) {
                out.append("<section><h3>Yêu cầu ").append(requirement.order()).append("</h3>");
                paragraph(out, requirement.title());
                out.append("<h4>Câu trả lời của bạn</h4>");
                paragraph(out, requirement.answer());

                if (requirement.image() != null && !requirement.image().isBlank()) {
                    EssayAnswerCodec.validateImage(requirement.image());
                    out.append("<img alt=\"Sơ đồ bài làm\" src=\"")
                            .append(escape(requirement.image())).append("\">");
                }

                out.append("</section>");
            }

            if (task.answer() != null) paragraph(out, task.answer());

            out.append("</article>");
        }

        if (essay.legacyContent() != null) {
            out.append("<section><h2>Nội dung bài làm cũ</h2>");
            paragraph(out, essay.legacyContent());
            out.append("</section>");
        }
    }

    private static void objective(StringBuilder out, StudentResultDAO.Row row) {
        try {
            Map<Long, java.util.List<String>> selected = ObjectiveAnswerGrader.selections(row.answer());
            var questions = JsonParser.parseString(row.questionsSnapshot()).getAsJsonArray();

            for (var element : questions) {
                var question = element.getAsJsonObject();
                long id = question.get("question_id").getAsLong();

                if (question.get("assignment_id").getAsLong() != row.attempt().assignmentId())
                    throw new IllegalArgumentException();

                out.append("<article><h2>Câu ")
                        .append(question.get("question_order").getAsInt()).append("</h2>");
                paragraph(out, question.get("question_text").getAsString());
                out.append("<ul>");

                for (var optionElement : question.getAsJsonArray("options")) {
                    var option = optionElement.getAsJsonObject();
                    boolean chosen = selected.getOrDefault(id, java.util.List.of())
                            .contains(option.get("option_id").getAsString());
                    out.append("<li>").append(chosen ? "☑ " : "☐ ")
                            .append(escape(option.get("option_text").getAsString()))
                            .append(chosen ? " — Bạn đã chọn" : "").append("</li>");
                }

                out.append("</ul></article>");
            }
        } catch (RuntimeException ex) {
            paragraph(out, "Một phần lựa chọn cũ không thể đọc được.");
        }
    }

    private static void paragraph(StringBuilder out, String text) {
        out.append("<p>").append(escape(text == null || text.isBlank() ? "Chưa có nội dung." : text))
                .append("</p>");
    }

    private static String escape(String value) {
        return WebSupport.html(value);
    }
}
