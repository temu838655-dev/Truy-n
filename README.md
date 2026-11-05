# BO Kali AI

Ứng dụng Android tiếng Việt tập trung vào ADB, terminal, chẩn đoán Android và Kali Rootless, cùng trợ lý AI hỗ trợ lệnh ADB và code.

## Tải APK mới nhất

- [GitHub Release — BO Kali AI](https://github.com/temu838655-dev/Truy-n/releases/tag/bo-kali-ai-latest)
- [Build và kiểm thử GitHub Actions](https://github.com/temu838655-dev/Truy-n/actions)

## Tính năng

- Kết nối và chẩn đoán thiết bị Android / Android TV qua ADB khi người dùng bật và cấp quyền.
- Đề xuất lệnh ADB có kiểm tra phạm vi, quyền và xác nhận riêng trước khi thực thi.
- Terminal hỗ trợ Termux và Kali NetHunter Rootless qua Termux/PRoot.
- Kiểm tra thông tin thiết bị, phần cứng, kernel, bootloader và tương thích Kali.
- Tra cứu hướng dẫn root, unlock bootloader và flash theo đúng model/firmware.
- Danh mục thiết bị đã xác minh tích hợp sẵn hiện đang trống; app chưa có cơ sở để tuyên bố hỗ trợ root/unlock/flash đã xác minh cho model cụ thể nếu chưa có hồ sơ và nguồn bằng chứng khớp chính xác.
- Trợ lý AI hỗ trợ phân tích code, giải thích lệnh và tích hợp GitHub theo quyền người dùng cấp.
- Đọc repository, đề xuất bản vá và tạo Pull Request sau khi người dùng xem trước, xác nhận.

## Giới hạn an toàn

- Đây không phải Kali NetHunter Full; Kali Rootless chạy bằng Termux/PRoot và không có quyền kernel.
- Không có phương pháp root, unlock bootloader hoặc flash phổ quát cho mọi thiết bị Android.
- Tính năng flash cần đúng model, biến thể, firmware, phân vùng, slot và image đã xác minh. Thao tác sai có thể làm thiết bị không khởi động.
- ADB chỉ hoạt động khi thiết bị đích cho phép kết nối và cấp quyền.
- AI không tự ý thực thi lệnh hoặc ghi repository khi chưa được người dùng xác nhận.
- Chỉ sử dụng trên thiết bị và hệ thống mà bạn sở hữu hoặc được ủy quyền.

## Cấu trúc

- `bo-kali-ai/`: mã nguồn Android, cấu hình Gradle, tài liệu và kiểm thử.
- `.github/workflows/build-bo-kali-ai.yml`: kiểm thử, lint, build APK, checksum và cập nhật GitHub Release.

## Build cục bộ

Yêu cầu Java 17, Android SDK và Gradle 8.13.

```bash
cd bo-kali-ai
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

APK debug được tạo tại:

`bo-kali-ai/app/build/outputs/apk/debug/app-debug.apk`
