package vn.edu.fpt.ams.util;

import java.util.Map;

/** Vietnamese presentation labels; persisted codes and authorization roles stay unchanged. */
public final class Vietnamese {
 private Vietnamese(){}
 private static final Map<String,String> LABELS=Map.ofEntries(
  Map.entry("REGULAR","Thông thường"),Map.entry("PRACTICE","Luyện tập"),Map.entry("ESSAY","Tự luận"),Map.entry("OBJECTIVE","Trắc nghiệm"),
  Map.entry("DRAFT","Bản nháp"),Map.entry("PUBLISHED","Đã công bố"),Map.entry("CANCELLED","Đã hủy"),
  Map.entry("IN_PROGRESS","Đang làm"),Map.entry("SUBMITTED","Đã nộp"),Map.entry("GRADED","Đã chấm"),Map.entry("RELEASED","Đã công bố kết quả"),Map.entry("EXPIRED","Đã hết hạn"),Map.entry("VOID","Đã vô hiệu hóa"),
  Map.entry("PENDING","Đang chờ"),Map.entry("ACCEPTED","Đã chấp nhận"),Map.entry("DECLINED","Đã từ chối"),
  Map.entry("PENDING_REVIEW","Chờ duyệt"),Map.entry("APPROVED","Đã duyệt"),Map.entry("REJECTED","Đã từ chối"),Map.entry("RESOLVED","Đã xử lý"),
  Map.entry("OPEN","Đang mở"),Map.entry("PROCESSING","Đang xử lý"),Map.entry("COMPLETED","Đã hoàn tất"),
  Map.entry("ADMIN","Quản trị viên"),Map.entry("EXPERT","Chuyên gia"),Map.entry("TEACHER","Giảng viên"),Map.entry("STUDENT","Sinh viên"),
  Map.entry("ACTIVE","Hoạt động"),Map.entry("INACTIVE","Ngừng hoạt động"),Map.entry("PRIMARY","Chính"),Map.entry("SUPPORTING","Hỗ trợ"),
  Map.entry("users","Tổng tài khoản"),Map.entry("subjects","Tổng môn học"),Map.entry("academic_terms","Tổng học kỳ"),Map.entry("subject_classes","Tổng lớp học"),Map.entry("assignment_templates","Tổng mẫu bài tập"),Map.entry("active_users","Tài khoản hoạt động"),Map.entry("pending_invitations","Lời mời đang chờ"),
  Map.entry("No active Current Term selected","Chưa chọn học kỳ hiện tại đang hoạt động"),
  Map.entry("No successful sign-in for at least 90 days","Không đăng nhập thành công trong ít nhất 90 ngày"),
  Map.entry("No active Subject responsibility","Không có phân công môn học đang hoạt động"),
  Map.entry("No active eligibility, Class or pending offer","Không có môn đủ điều kiện giảng dạy, lớp đang phụ trách hoặc đề nghị chờ phản hồi còn hạn"),
  Map.entry("More than 6 overdue accepted Essays awaiting release","Có hơn 6 bài tự luận đã nộp quá hạn chấm và chưa công bố kết quả"),
  Map.entry("At least 3 unanswered expired offers in one Term","Có ít nhất 3 đề nghị hết hạn mà chưa phản hồi trong cùng học kỳ"),
  Map.entry("At least 3 overdue Requests in one Term under Primary responsibility","Có ít nhất 3 yêu cầu duyệt quá hạn trong cùng học kỳ thuộc trách nhiệm chuyên gia chính"),
  Map.entry("Sign-in could not be completed. Check your credentials or authentication method.","Đăng nhập không thành công. Vui lòng kiểm tra tài khoản, mật khẩu hoặc phương thức đăng nhập."),
  Map.entry("Sign-in is temporarily unavailable. Try again later.","Đăng nhập tạm thời không khả dụng. Vui lòng thử lại sau."),
  Map.entry("Sign-in required","Vui lòng đăng nhập để tiếp tục."),
  Map.entry("Form token is invalid; refresh the page","Phiên biểu mẫu không hợp lệ. Vui lòng tải lại trang rồi thử lại."),
  Map.entry("An active account is required","Cần sử dụng tài khoản đang hoạt động."),
  Map.entry("Administrator access is required","Chỉ quản trị viên được thực hiện thao tác này."),
  Map.entry("Changes saved","Đã lưu thay đổi"),Map.entry("Template saved; existing Assignments remain unchanged","Đã lưu mẫu bài tập; các bài tập đã tạo giữ nguyên nội dung"),
  Map.entry("Account created; credential email sent","Đã tạo tài khoản và gửi email thông tin đăng nhập"),
  Map.entry("Account created, but email failed. Configure SMTP and use Reissue credential.","Đã tạo tài khoản nhưng gửi email thất bại. Kiểm tra cấu hình email rồi cấp lại mật khẩu."),
  Map.entry("New credential sent","Đã gửi thông tin đăng nhập mới"),Map.entry("Credential replaced, but email failed. Check SMTP and retry.","Đã thay mật khẩu nhưng gửi email thất bại. Kiểm tra cấu hình email rồi thử lại."),
  Map.entry("Subject, title (1–200 characters) and instructions are required","Cần chọn môn học, nhập tiêu đề từ 1–200 ký tự và hướng dẫn"),
  Map.entry("Maximum score requires a positive decimal with at most 2 decimal places","Điểm tối đa phải lớn hơn 0 và có tối đa 2 chữ số thập phân"),
  Map.entry("Valid email up to 100 characters required","Cần nhập email hợp lệ, tối đa 100 ký tự"),Map.entry("Full name requires 1–50 characters without digits","Họ tên cần có 1–50 ký tự và không chứa chữ số"),
  Map.entry("Cannot deactivate yourself","Không thể vô hiệu hóa tài khoản của chính bạn"),Map.entry("Replace the Teacher of active Classes first","Cần thay giảng viên của các lớp đang hoạt động trước"),
  Map.entry("An active Subject would lose its Primary Expert","Thao tác sẽ làm môn học đang hoạt động không còn chuyên gia chính"),Map.entry("An active Class would lose its Class Expert","Thao tác sẽ làm lớp đang hoạt động không còn chuyên gia phụ trách"),
  Map.entry("Resolve active responsibilities before changing role","Cần xử lý các phân công đang hoạt động trước khi đổi vai trò"),Map.entry("Cannot remove your own Admin role","Không thể bỏ vai trò quản trị viên của chính bạn"),
  Map.entry("Select an active Subject","Vui lòng chọn môn học đang hoạt động"),Map.entry("This identity already exists","Email hoặc danh tính này đã tồn tại"),Map.entry("Only staff accounts can be created","Chỉ được tạo tài khoản nội bộ cho quản trị viên, chuyên gia và giảng viên"),
  Map.entry("Cannot remove eligibility while teaching an active Class","Không thể bỏ điều kiện giảng dạy khi đang phụ trách lớp hoạt động"),Map.entry("Student Google identity and role cannot be changed","Không thể thay đổi email Google hoặc vai trò của sinh viên"),Map.entry("Staff accounts cannot become Google Students","Không thể chuyển tài khoản nội bộ thành sinh viên Google"),
  Map.entry("Role is required","Cần chọn vai trò"),Map.entry("Status required","Cần chọn trạng thái"),Map.entry("Active staff account required","Cần chọn tài khoản nội bộ đang hoạt động"),Map.entry("Eligibility belongs only to Teachers","Chỉ giảng viên được cấu hình môn đủ điều kiện giảng dạy"),Map.entry("Eligibility requires an active Teacher","Điều kiện giảng dạy yêu cầu giảng viên đang hoạt động"),Map.entry("Reactivate before changing staff role","Cần kích hoạt lại tài khoản trước khi đổi vai trò"),
  Map.entry("A positive record ID is required","Mã bản ghi phải là số nguyên dương"),Map.entry("Invalid page","Số trang không hợp lệ"),Map.entry("Search maximum is 50 characters","Từ khóa tìm kiếm tối đa 50 ký tự"),Map.entry("Maximum score is required","Cần nhập điểm tối đa")
 );
 public static String label(Object value){return value==null?"":LABELS.getOrDefault(value.toString(),value.toString());}
 public static String registrationStatus(String status){
  if(status==null)return "";
  return switch(status){
   case "SUBJECT_INACTIVE" -> "Môn học ngừng hoạt động";
   case "TERM_INACTIVE" -> "Học kỳ ngừng hoạt động";
   case "DISABLED" -> "Đã tắt đăng ký";
   case "UPCOMING" -> "Chưa mở";
   case "OPEN" -> "Đang mở";
   case "CLOSED" -> "Đã đóng";
   default -> label(status);
  };
 }
}
