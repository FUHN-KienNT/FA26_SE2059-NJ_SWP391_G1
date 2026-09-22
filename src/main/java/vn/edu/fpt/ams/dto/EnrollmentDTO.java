package vn.edu.fpt.ams.dto;

import vn.edu.fpt.ams.model.EnrollmentStatus;

/** Teacher roster row obtained from Enrollment, Registration and User. */
public record EnrollmentDTO(long enrollmentId, long registrationId, long classId,
                            long studentId, String studentName, String studentEmail,
                            EnrollmentStatus status) {}
