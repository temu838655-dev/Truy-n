# BO Kali AI

Ứng dụng Android hỗ trợ ADB, chẩn đoán thiết bị, Kali Rootless và trợ lý AI thiên về lệnh ADB / code. Giao diện tiếng Việt dạng terminal.

## Tính năng hiện có

### Terminal và Kali
- Terminal lệnh chẩn đoán có xác nhận trước khi thực thi.
- Mở Termux và chuyển lệnh không tương tác sang Termux/Kali Rootless qua cơ chế `RUN_COMMAND` (cần Termux tương thích và người dùng tự bật quyền).
- Hướng dẫn cài Kali NetHunter Rootless bằng Termux/PRoot.
- Kiểm tra trạng thái Termux/Kali, quyền root Android, thông tin phần cứng, kernel, bootloader và tương thích Kali.
- Quản lý một số gói công cụ Termux/Kali theo danh sách cho phép, có xác nhận.
- Phân biệt rõ Android root với Kali Rootless: PRoot không cấp quyền root thật và không cung cấp quyền kernel.

### ADB và bảo mật thiết bị
- Kết nối thiết bị Android/Android TV qua ADB sau khi người dùng bật và chấp thuận kết nối hoặc ghép đôi.
- Chẩn đoán kết nối, thông tin thiết bị và một số thao tác theo quyền đã cấp.
- Đề xuất lệnh ADB do AI tạo phải qua kiểm tra phạm vi, xác nhận của người dùng và kiểm tra trạng thái thiết bị trước khi gửi.
- Kế hoạch nhiều bước bị giới hạn số bước; từng bước cần người dùng xác nhận riêng.
- Có nhật ký kiểm tra cục bộ; quyền phiên không được xem là quyền vĩnh viễn.
- Hồ sơ thiết bị phục vụ tra cứu root/unlock yêu cầu nhận dạng chính xác và nguồn bằng chứng được duyệt; không suy đoán thiết bị chỉ từ tên gần giống.

### Root, unlock bootloader và flash
- Có mục hướng dẫn/tra cứu root, unlock bootloader và flash kernel / boot image / recovery.
- Các mục này là hướng dẫn và kiểm tra tương thích, không phải công cụ root/unlock/flash tự động phổ quát.
- Không có một lệnh chung an toàn cho mọi hãng, model, biến thể, firmware và khu vực. Mở khóa có thể xóa dữ liệu; flash sai image hoặc sai phân vùng có thể làm thiết bị không khởi động.
- Chỉ làm theo hướng dẫn chính thức hoặc tài liệu dự án đáng tin cậy dành đúng model và bản firmware. Không flash khi chưa xác minh image, checksum, phân vùng và slot.

### Trợ lý AI
- Hỗ trợ các nhà cung cấp AI được tích hợp trong ứng dụng; người dùng tự cấu hình thông tin truy cập của nhà cung cấp.
- AI hỗ trợ giải thích kết quả, phân tích code và đề xuất thao tác ADB.
- AI không được tự ý thực thi lệnh chỉ vì nội dung phản hồi có yêu cầu gọi công cụ.
- Tích hợp GitHub cho đọc repository/tệp, tìm kiếm code và đề xuất thay đổi.
- Các thao tác ghi GitHub/PR yêu cầu xem trước và xác nhận; token GitHub không được gửi cho AI.
- Thay đổi do AI đề xuất không được tự commit trực tiếp vào nhánh gốc.

### GitHub
- Kiểm tra repository và đọc nội dung theo quyền token.
- Đọc nhiều tệp, tìm kiếm code và tạo đề xuất thay đổi.
- Tạo nhánh/commit/PR sau khi người dùng xem trước và xác nhận.
- Kiểm tra tên repository, nhánh, đường dẫn và kích thước nội dung trước khi ghi.
- Có cơ chế dọn nhánh tạm nếu tạo PR thất bại; nếu không dọn được sẽ báo để người dùng xử lý.

## Giới hạn quan trọng

- BO Kali AI hiện không phải Kali NetHunter Full và không có kernel NetHunter tùy biến tích hợp.
- Kali Rootless chạy trong Termux/PRoot; không có quyền kernel, Wi-Fi monitor/injection, HID/USB gadget hoặc các tính năng cần kernel tương thích.
- Ứng dụng không thể bảo đảm root, unlock bootloader hay flash trên tất cả thiết bị Android. Nhiều thiết bị bị OEM khóa hoặc không hỗ trợ mở khóa.
- ADB chỉ hoạt động khi thiết bị đích bật/chấp thuận ADB và cấp quyền phù hợp.
- Các thao tác nhạy cảm luôn cần người dùng kiểm tra và xác nhận. Chỉ sử dụng trên thiết bị, repository và hệ thống mà bạn sở hữu hoặc được ủy quyền.
- Một số chức năng điều khiển thiết bị/desktop và giao thức riêng của từng hãng chưa được hỗ trợ.

## Build và phát hành

GitHub Actions của BO Kali AI chạy kiểm thử unit test, Android Lint, build APK, tạo SHA-256 và báo cáo kiểm tra, sau đó tải artifact và cập nhật GitHub Release cố định `bo-kali-ai-latest`.

- [GitHub Releases](https://github.com/temu838655-dev/Truy-n/releases/tag/bo-kali-ai-latest)
- [GitHub Actions](https://github.com/temu838655-dev/Truy-n/actions)

## Bảo mật thông tin truy cập

- Chỉ nhập API key và GitHub token của chính bạn.
- Cấp quyền GitHub tối thiểu cần thiết cho repository mục tiêu; chỉ cấp quyền ghi khi thực sự cần.
- Không chia sẻ token, API key, mã ghép đôi hoặc dữ liệu bí mật vào prompt AI.
- Kiểm tra kỹ lệnh, diff, repository, nhánh và PR trước khi xác nhận.
