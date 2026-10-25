# MANGA WORLD

Ứng dụng thư viện truyện tranh responsive cho web/PWA và Android/iOS qua Capacitor.

## Tính năng hiện có
- Giao diện premium, responsive desktop/mobile; chuyển sáng/tối.
- Tìm kiếm theo tên, tác giả, thể loại.
- Bộ lọc truyện màu, đen trắng, dài tập, truyện ngắn.
- Tủ truyện, yêu thích, lịch sử đọc.
- Nhập file CBZ/ZIP chứa ảnh PNG/JPG/WEBP/GIF hoặc nhập từng ảnh.
- Lưu dữ liệu truyện trong IndexedDB; đọc offline sau khi đã nhập.
- Service worker cache app shell để hỗ trợ mở lại ứng dụng khi offline sau lần truy cập đầu.
- Trình đọc cuộn dọc, lưu thư viện trên thiết bị.

Các tựa Conan, Doraemon, Naruto và những truyện khác trên trang chủ là thẻ giới thiệu mẫu, không chứa trang truyện có bản quyền. Muốn đọc nội dung, hãy nhập bản CBZ/ZIP/ảnh mà bạn có quyền sử dụng hoặc tích hợp API/nguồn phân phối được cấp phép.

## Yêu cầu
- Node.js 20 LTS trở lên (khuyến nghị).
- npm.
- Android Studio để build Android; Xcode trên macOS để build iOS.

## Chạy thử trên Windows 11 / macOS / Linux
1. Giải nén project.
2. Mở Terminal/PowerShell tại thư mục `manga-world`.
3. Cài thư viện:
   ```bash
   npm install
   ```
4. Chạy môi trường phát triển:
   ```bash
   npm run dev
   ```
5. Mở URL Vite in ra, thường là `http://localhost:5173`.
6. Build bản web:
   ```bash
   npm run build
   npm run preview
   ```

## Cài như ứng dụng PWA
- Deploy thư mục `dist` lên hosting có HTTPS (hoặc chạy localhost).
- Trên Android: mở website bằng Chrome → menu → Install app / Add to Home screen.
- Trên iPhone: mở bằng Safari → Share → Add to Home Screen.
- Trên desktop: dùng biểu tượng Install trên thanh địa chỉ trình duyệt hỗ trợ.

## Tạo ứng dụng Android
```bash
npm install
npm run build
npx cap add android
npx cap sync android
npx cap open android
```
Trong Android Studio, chọn thiết bị hoặc emulator rồi nhấn Run. Lần đầu cần Android SDK phù hợp.

## Tạo ứng dụng iOS (cần máy Mac)
```bash
npm install
npm run build
npx cap add ios
npx cap sync ios
npx cap open ios
```
Trong Xcode, chọn Team / Signing và chạy trên iPhone hoặc Simulator. Không thể build iOS native bằng Windows; trên Windows có thể chạy PWA hoặc build Android.

## Nhập truyện
- CBZ: file ZIP đổi đuôi `.cbz`, bên trong chứa ảnh trang truyện.
- ZIP: chứa ảnh trang truyện.
- Có thể chọn nhiều ảnh cùng lúc; mỗi ảnh sẽ được tạo thành một mục truyện riêng.
- Tên file được dùng làm tên truyện; các trang trong CBZ sắp xếp theo tên tự nhiên.

## Lưu ý kỹ thuật / giới hạn bản 1
- Dữ liệu được lưu cục bộ trong IndexedDB của trình duyệt/WebView; xóa dữ liệu ứng dụng có thể xóa thư viện. Hãy giữ bản sao file gốc.
- Chưa có tài khoản, đồng bộ cloud, backend catalog, DRM, thanh toán hoặc nguồn truyện có bản quyền. Đây là các module cần triển khai riêng.
- Trên iOS, để phân phối App Store cần tài khoản Apple Developer và hoàn tất quy trình ký/phát hành.

## Build APK tự động bằng GitHub Actions

1. Tạo repository GitHub mới, ví dụ `manga-world`.
2. Upload toàn bộ mã nguồn dự án lên repository (không upload `node_modules`).
3. Đảm bảo workflow `.github/workflows/android-apk.yml` có trong nhánh `main`.
4. Mở tab **Actions** → **Build MANGA WORLD Android APK** → **Run workflow**.
5. Chờ workflow chạy xong. Mở lần chạy thành công và tải artifact `manga-world-debug-apk` ở cuối trang.
6. Giải nén artifact, chép `app-debug.apk` sang điện thoại Android và cài đặt. Có thể cần cho phép cài ứng dụng từ nguồn này.

Workflow dùng GitHub-hosted Ubuntu runner để cài Node.js, Java và Android SDK; sau đó chạy Vite build, tạo Android project bằng Capacitor và chạy Gradle `assembleDebug`. APK này là bản debug để thử nghiệm, chưa phải bản release đã ký để phát hành Google Play.
