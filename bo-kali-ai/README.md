# BO Kali AI — Android MVP

Ứng dụng Android kiểu CMD/Terminal, có ô hỏi AI tiếng Việt và kiểm tra kết nối GitHub.

## Bản MVP hiện có
- Giao diện terminal nền tối, hai ô riêng: nhập lệnh và hỏi AI.
- Lệnh chẩn đoán an toàn: `help`, `device`, `net`, `wifi`, `bluetooth`.
- Gemini online qua API key do người dùng tự nhập.
- Kiểm tra GitHub và liệt kê tối đa 10 repository qua Personal Access Token.
- API key/token chỉ nằm trong bộ nhớ phiên, không ghi vào file, không nhúng sẵn trong APK.
- Không root, không flash kernel, không chạy shell tùy ý và không tự điều khiển thiết bị khác.

## Build
Mở GitHub Actions → **Build BO Kali AI APK** → Run workflow. Tải artifact `BO-KALI-AI-DEBUG-APK`.

## Cấu hình AI
Tạo Gemini API key trong Google AI Studio, mở app → Kết nối AI / GitHub → nhập key.
Dùng model Gemini 2.5 Flash trong bản MVP. Hạn mức/giá phụ thuộc tài khoản và chính sách Google.

## Cấu hình GitHub
Tạo fine-grained Personal Access Token chỉ cấp quyền đọc Metadata và Contents cho repository cần xem. Nhập token trong app. Không cấp quyền ghi nếu chưa cần.

## Giới hạn
Đây chưa phải Kali NetHunter hoặc một shell Linux thật. Các lệnh hiện tại là lệnh chẩn đoán do ứng dụng xử lý. Kali ARM64/proot, ADB host, điều khiển thiết bị từ xa, OAuth và AI offline là các giai đoạn tiếp theo.
