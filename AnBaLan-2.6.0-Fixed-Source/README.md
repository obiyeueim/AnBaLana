# AnBaLan

Ứng dụng khách WireGuard cho Android, giao diện tối–đỏ, tác giả An.

## Chức năng

- Nhập file WireGuard `.conf` một lần và lưu mã hóa bằng Android Keystore.
- Kết nối/ngắt kết nối bằng một nút, xin quyền VPN đúng chuẩn Android.
- Game Mode nhẹ: không polling thống kê lưu lượng, không quét ping nền.
- Tự chuẩn hóa MTU 1280 và PersistentKeepalive 25 khi nhập cấu hình.
- Nút “Bật VPN & mở Free Fire” đợi tunnel ổn định trước khi khởi chạy game.
- Tab nổi ON/OFF kết nối hoặc ngắt tunnel trực tiếp trong game.
- Màn hình quyền bắt buộc: hiển thị trên ứng dụng khác và quyền VPN Android.
- Màn hình mở TikTok một lần với liên kết `https://vt.tiktok.com/ZSXRafKgQ/`.
- Tab nổi kéo thả; chạm ON/OFF để kết nối hoặc ngắt VPN, mở app hoặc đóng nhanh.
- Trạng thái tab nổi cập nhật theo sự kiện StateFlow; nút ON/OFF không can thiệp gói game.
- Không can thiệp trò chơi, không chứa tính năng cheat và không tự cung cấp máy chủ VPN.

## Build

Mở dự án bằng Android Studio hoặc chạy:

```bash
./gradlew --no-daemon lintDebug assembleDebug
```

APK cài được nằm tại `app/build/outputs/apk/debug/app-debug.apk`. Workflow
GitHub Actions tại `.github/workflows/build-apk.yml` tự kiểm tra lint, build và
tạo artifact `AnBaLan-2.6.0-APK`.

Khi đưa source lên GitHub, đặt `app`, `gradle`, `.github`, `gradlew` và các file
Gradle trực tiếp ở thư mục gốc của repository. Không lồng thêm nhiều thư mục
`NovaVPN-WireGuard`.

## Lưu ý TikTok

Ứng dụng chỉ ghi nhận liên kết TikTok đã được mở. Việc xác minh người dùng thực
sự follow cần TikTok OAuth/API và không được mô phỏng trong ứng dụng.
