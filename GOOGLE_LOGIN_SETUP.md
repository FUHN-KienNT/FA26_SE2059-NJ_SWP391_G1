# Cấu hình đăng nhập Google cho Student

Admin, Expert và Teacher dùng tài khoản nội bộ. Student chọn **Đăng nhập bằng Google** và sử dụng Gmail cá nhân. AMS không yêu cầu nhập mật khẩu Gmail vào hệ thống.

## Tạo OAuth client của nhóm

1. Mở [Google Cloud Console](https://console.cloud.google.com/), đăng nhập Gmail của bạn. Chọn project hiện có hoặc tạo project tên `AMS-G1`.
2. Vào **Google Auth platform → Branding**. Nếu chưa cấu hình, bấm **Get Started**. App name: `AMS G1`; User support email và Contact email: email của bạn. Chọn Audience **External** để dùng Gmail cá nhân, hoàn tất wizard.
3. Trong **Audience**, khi ứng dụng đang Testing, thêm Gmail của bạn và các bạn trong nhóm vào **Test users** rồi Save. Hướng dẫn màn hình và test users: [Google OAuth consent](https://developers.google.com/workspace/guides/configure-oauth-consent).
4. Trong **Data Access**, chọn quyền cơ bản `openid`, email và profile. Project chỉ dùng danh tính đăng nhập, không cần quyền đọc hộp thư Gmail.
5. Vào **Clients → Create Client**, chọn **Web application**, tên `AMS Local`. Authorized redirect URIs phải có chính xác:

   ```text
   http://localhost:8080/ams/auth/google/callback
   ```

   Bấm Create và lưu **Client ID**, **Client Secret** ngay khi được hiển thị. Tạo client và đăng ký callback theo [Google Web Server OAuth](https://developers.google.com/identity/protocols/oauth2/web-server).

6. Mở file local `src/main/resources/application.properties`, thay các giá trị sau bằng thông tin vừa tạo:

   ```properties
   google.clientId=CLIENT_ID_CUA_NHOM
   google.clientSecret=CLIENT_SECRET_CUA_NHOM
   google.redirectUri=http://localhost:8080/ams/auth/google/callback
   ```

   Không gửi Client Secret vào chat hoặc commit lên Git. OAuth client xác định ứng dụng của nhóm; từng Student vẫn đăng nhập bằng Gmail riêng của mình.
7. Lưu file, chạy `mvn clean package`, sau đó chép `target/ams.war` vào thư mục `webapps` của Tomcat 10.1 và khởi động Tomcat.
8. Mở http://localhost:8080/ams/auth/signin, bấm **Đăng nhập bằng Google**, chọn Gmail đã thêm vào Test users. Callback xác minh token, tạo hoặc dùng lại Student và điều hướng `/ams/student/dashboard`.

## Kiểm tra lỗi cấu hình

`redirect_uri_mismatch`: kiểm tra callback URL, port 8080, `/ams`, `/auth/google/callback` và không thêm dấu `/` cuối. Mở ứng dụng bằng `localhost` đúng với callback đã đăng ký.

`access_denied`: kiểm tra Audience External và Test users, dùng đúng Gmail đã thêm khi đang Testing.

HTTP 503 ngay khi bấm Google: Client ID/Secret chưa được cấu hình hoặc server đang dùng WAR cũ.

Nếu Gmail trùng email tài khoản nội bộ, callback từ chối liên kết. Student Google không được chuyển thành Admin, Expert hoặc Teacher.
