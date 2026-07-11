# AnBaLan

Ứng dụng khách WireGuard cho Android, giao diện tối–đỏ, tác giả An.

## Chức năng

- Nhập file WireGuard `.conf` một lần và lưu mã hóa bằng Android Keystore.
- Kết nối/ngắt kết nối bằng một nút, xin quyền VPN đúng chuẩn Android.
- Hiển thị endpoint, thời gian phiên và thống kê lưu lượng thực tế.
- Màn hình mở TikTok một lần với liên kết `https://vt.tiktok.com/ZSXRafKgQ/`.
- Tab nổi kéo thả để mở AnBaLan, mở cài đặt VPN hoặc đóng nhanh.
- Không can thiệp trò chơi, không chứa tính năng cheat và không tự cung cấp máy chủ VPN.

## Build

Mở dự án bằng Android Studio hoặc chạy:

```bash
./gradlew assembleRelease
```

APK nằm trong `app/build/outputs/apk/release/`. Workflow GitHub Actions tại
`.github/workflows/build-apk.yml` cũng tự build và tạo artifact `AnBaLan-APK`.

## Lưu ý TikTok

Ứng dụng chỉ ghi nhận liên kết TikTok đã được mở. Việc xác minh người dùng thực
sự follow cần TikTok OAuth/API và không được mô phỏng trong ứng dụng.
