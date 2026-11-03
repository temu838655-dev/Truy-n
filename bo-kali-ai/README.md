# BO Kali AI — Android MVP

Ứng dụng Android kiểu CMD/Terminal, có ô hỏi AI tiếng Việt và kiểm tra kết nối GitHub.

## Bản MVP hiện có
- Giao diện terminal nền tối, hai ô riêng: nhập lệnh và hỏi AI.
- Lệnh chẩn đoán an toàn: `help`, `device`, `net`, `wifi`, `bluetooth`.
- Gemini online qua API key do người dùng tự nhập.
- Kiểm tra GitHub và liệt kê tối đa 10 repository qua Personal Access Token.
- API key/token chỉ nằm trong bộ nhớ phiên, không ghi vào file, không nhúng sẵn trong APK.
- Tích hợp nút KALI SHELL để mở Termux, sao chép lệnh cài Kali NetHunter Rootless hoặc mở Kali trực tiếp qua Termux `RUN_COMMAND` sau khi người dùng bật quyền.
- Kali Shell dùng môi trường Termux/PRoot bên ngoài ứng dụng; người dùng tự xác nhận lệnh cài đặt. Không cần root, không flash kernel và không sửa phân vùng hệ thống.

## Build
Mở GitHub Actions → **Build BO Kali AI APK** → Run workflow. Tải artifact `BO-KALI-AI-DEBUG-APK`.

## Cấu hình AI
Tạo Gemini API key trong Google AI Studio, mở app → Kết nối AI / GitHub → nhập key.
Dùng model Gemini 2.5 Flash trong bản MVP. Hạn mức/giá phụ thuộc tài khoản và chính sách Google.

## Cấu hình GitHub
Tạo fine-grained Personal Access Token chỉ cấp quyền đọc Metadata và Contents cho repository cần xem. Nhập token trong app. Không cấp quyền ghi nếu chưa cần.

## Giới hạn
Kali Shell hiện được chuyển giao cho Termux/NetHunter Rootless; terminal tương tác chạy trong Termux, chưa được nhúng trực tiếp vào màn hình BO Kali AI và BO chưa đọc được stdout/stderr của phiên shell. Cần cài Termux và Kali trước, bật `allow-external-apps=true` cùng quyền `com.termux.permission.RUN_COMMAND`, sau đó dùng nút KALI SHELL → Vào Kali trực tiếp. Có phương án dự phòng mở Termux và chạy `nh` thủ công. Rootless có giới hạn phần cứng: không có Wi-Fi monitor/injection, HID/USB gadget hay quyền kernel như NetHunter Full có kernel tương thích. ADB host, console nhúng, đồng bộ kết quả shell với AI, OAuth và AI offline là các giai đoạn tiếp theo.
