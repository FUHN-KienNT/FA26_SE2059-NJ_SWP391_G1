package vn.edu.fpt.ams.dto;

import vn.edu.fpt.ams.model.Submission;

/** A saved attempt with the student's identity and optional grader name. */
public record TeacherSubmissionRow(Submission submission, long studentId,
        String studentName, String studentEmail, String graderName) {}
