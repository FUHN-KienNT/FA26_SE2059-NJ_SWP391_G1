package vn.edu.fpt.ams.dto;

import vn.edu.fpt.ams.model.Submission;

/** A student's saved attempt, or a roster entry with a null submission before starting. */
public record TeacherSubmissionRow(Submission submission, long studentId,
        String studentName, String studentEmail, String graderName,
        String assignmentSnapshot, String questionsSnapshot) {
    public TeacherSubmissionRow(Submission submission, long studentId,
            String studentName, String studentEmail, String graderName) {
        this(submission, studentId, studentName, studentEmail, graderName, null, null);
    }

    public boolean hasSubmission() { return submission != null; }
}
