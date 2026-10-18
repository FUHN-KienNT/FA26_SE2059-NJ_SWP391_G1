package vn.edu.fpt.ams.model;

public enum SettingType {
  USER_ROLE("Vai trò tài khoản"),
  ASSIGNMENT_TYPE("Loại bài tập"),
  RESPONSE_TYPE("Hình thức trả lời");

  private final String label;

  SettingType(String label) { this.label = label; }

  public String label() { return label; }
}
