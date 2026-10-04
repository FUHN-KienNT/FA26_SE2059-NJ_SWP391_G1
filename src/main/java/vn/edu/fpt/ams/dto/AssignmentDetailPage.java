package vn.edu.fpt.ams.dto;

import java.util.List;
import vn.edu.fpt.ams.model.Assignment;
import vn.edu.fpt.ams.model.Question;

/** Data for one assignment that the current Teacher is allowed to view. */
public record AssignmentDetailPage(Assignment assignment, String classCode, String className,
        String subjectCode, String subjectName, String termCode, String templateTitle,
        List<Question> questions) {
    public AssignmentDetailPage {
        questions = List.copyOf(questions);
    }
}
