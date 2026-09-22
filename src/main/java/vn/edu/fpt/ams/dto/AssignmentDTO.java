package vn.edu.fpt.ams.dto;

import vn.edu.fpt.ams.model.*;

public record AssignmentDTO(long assignmentId, long classId, long templateId, String title, AssignmentPurpose purpose, ResponseType responseType, AssignmentStatus status) {}
