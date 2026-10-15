package vn.edu.fpt.ams.dto;

/** Một dòng CSV sau khi nhập: email, kết quả và lý do để giảng viên thấy ai bị bỏ qua hoặc từ chối. */
public record ImportLineResult(int line, String email, ImportOutcome outcome, String reason) {}
