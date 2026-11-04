package com.bokaliai.terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.concurrent.atomic.AtomicInteger;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.Context;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import java.net.InetAddress;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final int BG = Color.rgb(8, 15, 24), PANEL = Color.rgb(18, 31, 45);
    private final int GREEN = Color.rgb(87, 225, 157), WHITE = Color.rgb(232, 240, 248);
    private LinearLayout root, log;
    private ScrollView scroll;
    private EditText commandInput, questionInput;
    private String geminiKey = "", githubToken = "", lastCommandOutput = "";
    private static final AtomicInteger TERMUX_REQUEST = new AtomicInteger(2000);
    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener adbDiscoveryListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean adbScanActive = false;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3101);
        }
        line("BO Kali AI v0.1.0 — Android safe terminal", GREEN);
        line("Đây là bản điều khiển chẩn đoán không root. Chưa chạy shell tùy ý.", WHITE);
        line("Gõ help để xem lệnh khả dụng. Kết nối AI/GitHub trong Cài đặt kết nối.", GREEN);
    }

    private void buildUi() {
        root = new LinearLayout(this); root.setOrientation(1); root.setPadding(18,18,18,14); root.setBackgroundColor(BG);
        TextView title = text("BO KALI AI  •  UNIVERSAL TERMINAL", GREEN, 18, true);
        root.addView(title);
        TextView subtitle = text("ANDROID · ROOTLESS · TIẾNG VIỆT", 0xff91a9bd, 11, true);
        root.addView(subtitle);
        LinearLayout bar = new LinearLayout(this); bar.setOrientation(0);
        Button settings = button("Kết nối AI / GitHub");
        settings.setOnClickListener(v -> connectionSettings());
        bar.addView(settings, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button kali = button("KALI SHELL");
        kali.setOnClickListener(v -> kaliShellMenu());
        bar.addView(kali, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button adb = button("QUÉT ADB");
        adb.setOnClickListener(v -> scanAdbServices());
        bar.addView(adb, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button clear = button("Xóa");
        clear.setOnClickListener(v -> log.removeAllViews());
        bar.addView(clear, new LinearLayout.LayoutParams(dp(62), dp(44)));
        root.addView(bar);
        LinearLayout adbRow = new LinearLayout(this); adbRow.setOrientation(0);
        Button pairAdb = button("GHÉP ĐÔI ADB");
        pairAdb.setOnClickListener(v -> showAdbPairDialog());
        adbRow.addView(pairAdb, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button connectAdb = button("KẾT NỐI IP");
        connectAdb.setOnClickListener(v -> showAdbConnectDialog());
        adbRow.addView(connectAdb, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button listAdb = button("THIẾT BỊ");
        listAdb.setOnClickListener(v -> showAdbDeviceManager());
        adbRow.addView(listAdb, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button shellAdb = button("ADB SHELL");
        shellAdb.setOnClickListener(v -> showAdbShellDialog());
        adbRow.addView(shellAdb, new LinearLayout.LayoutParams(0, dp(42), 1));
        root.addView(adbRow);
        Button shareReport = button("CHIA SẺ KẾT QUẢ TERMUX");
        shareReport.setOnClickListener(v -> shareLastTermuxReport());
        root.addView(shareReport, new LinearLayout.LayoutParams(-1, dp(42)));
        Button firmware = button("FIRMWARE / OTA");
        firmware.setOnClickListener(v -> showFirmwareManager());
        root.addView(firmware, new LinearLayout.LayoutParams(-1, dp(44)));
        Button updates = button("TRUNG TÂM CẬP NHẬT • APP / ADB / KALI / TERMUX / AI");
        updates.setOnClickListener(v -> showUpdateManager());
        root.addView(updates, new LinearLayout.LayoutParams(-1, dp(46)));
        scroll = new ScrollView(this); scroll.setFillViewport(true);
        log = new LinearLayout(this); log.setOrientation(1); log.setPadding(12,12,12,12); log.setBackgroundColor(PANEL);
        scroll.addView(log);
        root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        TextView commandLabel = text("Ô 1 · TERMINAL COMMAND", GREEN, 12, true); root.addView(commandLabel);
        LinearLayout commandRow = new LinearLayout(this); commandRow.setOrientation(0);
        commandInput = input("Nhập lệnh: help, device, net, wifi, bluetooth...");
        commandRow.addView(commandInput, new LinearLayout.LayoutParams(0,dp(50),1));
        Button run = button("CHẠY"); run.setOnClickListener(v -> runCommand());
        commandRow.addView(run, new LinearLayout.LayoutParams(dp(68),dp(50)));
        Button kaliRun = button("KALI");
        kaliRun.setOnClickListener(v -> confirmKaliCommand());
        commandRow.addView(kaliRun, new LinearLayout.LayoutParams(dp(68),dp(50)));
        root.addView(commandRow);
        TextView aiLabel = text("Ô 2 · HỎI AI BẰNG TIẾNG VIỆT", 0xff79c9ff, 12, true); root.addView(aiLabel);
        LinearLayout aiRow = new LinearLayout(this); aiRow.setOrientation(0);
        questionInput = input("Ví dụ: Lệnh nào kiểm tra địa chỉ IP?");
        aiRow.addView(questionInput, new LinearLayout.LayoutParams(0,dp(50),1));
        Button ask = button("HỎI AI"); ask.setOnClickListener(v -> askAi());
        aiRow.addView(ask, new LinearLayout.LayoutParams(dp(88),dp(50)));
        root.addView(aiRow);
        Button explain = button("GIẢI THÍCH KẾT QUẢ KALI BẰNG AI");
        explain.setOnClickListener(v -> explainLastOutput());
        root.addView(explain, new LinearLayout.LayoutParams(-1, dp(44)));
        Button copyOutput = button("SAO CHÉP KẾT QUẢ ADB / KALI GẦN NHẤT");
        copyOutput.setOnClickListener(v -> copyLastCommandOutput());
        root.addView(copyOutput, new LinearLayout.LayoutParams(-1, dp(42)));
        setContentView(root);
    }


    // Discovers Wireless Debugging endpoints; discovery alone does not pair or authenticate.
    private void scanAdbServices() {
        if (adbScanActive) { line("Đang quét ADB, vui lòng chờ.", 0xffffca70); return; }
        nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
        if (nsdManager == null) { line("Thiết bị không hỗ trợ Network Service Discovery.", 0xffff8080); return; }
        adbScanActive = true;
        line("Đang tìm Wireless debugging trên cùng mạng Wi-Fi...", GREEN);
        line("Tìm thấy endpoint chưa có nghĩa là đã ghép đôi.", 0xffffca70);
        adbDiscoveryListener = new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String type) { line("Đang quét " + type, 0xff79c9ff); }
            @Override public void onServiceFound(NsdServiceInfo service) {
                final String serviceName = service.getServiceName();
                nsdManager.resolveService(service, new NsdManager.ResolveListener() {
                    @Override public void onResolveFailed(NsdServiceInfo info, int error) { line("Không đọc được ADB endpoint " + serviceName + ", mã " + error, 0xffffca70); }
                    @Override public void onServiceResolved(NsdServiceInfo info) {
                        InetAddress host = info.getHost();
                        String ip = host == null ? "IP không rõ" : host.getHostAddress();
                        line("Tìm thấy ADB: " + serviceName + " — " + ip + ":" + info.getPort(), GREEN);
                        line("Để ghép đôi Android 11+, cần mã ghép đôi và cổng pairing riêng.", WHITE);
                    }
                });
            }
            @Override public void onServiceLost(NsdServiceInfo service) { line("Mất dịch vụ: " + service.getServiceName(), 0xffffca70); }
            @Override public void onStartDiscoveryFailed(String type, int error) { adbScanActive = false; try { nsdManager.stopServiceDiscovery(this); } catch (Exception ignored) {} line("Quét ADB thất bại, mã " + error, 0xffff8080); }
            @Override public void onStopDiscoveryFailed(String type, int error) { adbScanActive = false; try { nsdManager.stopServiceDiscovery(this); } catch (Exception ignored) {} }
            @Override public void onDiscoveryStopped(String type) { adbScanActive = false; line("Đã kết thúc quét ADB.", GREEN); }
        };
        try {
            nsdManager.discoverServices("_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, adbDiscoveryListener);
            mainHandler.postDelayed(() -> { if (adbScanActive && adbDiscoveryListener != null) { try { nsdManager.stopServiceDiscovery(adbDiscoveryListener); } catch (Exception ignored) { adbScanActive = false; } } }, 12000);
        } catch (Exception e) { adbScanActive = false; line("Lỗi quét ADB: " + e.getMessage(), 0xffff8080); }
    }


    private void showUpdateManager() {
        String[] choices = {
            "Quét cập nhật tất cả công cụ (chỉ kiểm tra)",
            "Kiểm tra phiên bản BO Kali AI mới nhất",
            "Cập nhật toàn bộ gói Termux (bao gồm ADB nếu đã cài)",
            "Cập nhật gói Kali NetHunter",
            "Cài / cập nhật Android Platform Tools (ADB)",
            "Kiểm tra cấu hình và phiên bản AI",
            "Lịch sử cập nhật"
        };
        new AlertDialog.Builder(this).setTitle("Trung tâm cập nhật BO Kali AI")
            .setItems(choices, (dialog, which) -> {
                if (which == 0) checkAllToolUpdates();
                else if (which == 1) checkAppUpdate();
                else if (which == 2) confirmUpdateCommand("Cập nhật Termux",
                    "Làm mới danh sách và nâng cấp các gói đã cài. Cần Internet.",
                    "pkg update -y && pkg upgrade -y");
                else if (which == 3) confirmUpdateCommand("Cập nhật Kali NetHunter",
                    "Chỉ cập nhật gói trong Kali rootless; không nâng cấp firmware Android.",
                    "nh apt update && nh apt full-upgrade -y");
                else if (which == 4) confirmUpdateCommand("Cài / cập nhật ADB",
                    "Cài hoặc nâng cấp android-tools trong Termux.",
                    "pkg update -y && pkg install -y android-tools && pkg upgrade -y android-tools && adb version");
                else if (which == 5) showAiUpdateInfo();
                else showUpdateHistory();
            }).show();
    }

    private void checkAllToolUpdates() {
        new AlertDialog.Builder(this).setTitle("Quét cập nhật công cụ")
            .setMessage("App sẽ làm mới danh sách gói Termux và Kali, sau đó liệt kê gói có thể nâng cấp. Không cài đặt/nâng cấp gói nào trong bước này. Cần Internet.")
            .setPositiveButton("BẮT ĐẦU QUÉT", (d,w) -> {
                String cmd = "echo '=== TERMUX: UPDATE INDEX ==='; pkg update -y; " +
                    "echo '=== TERMUX: UPGRADABLE ==='; pkg list-upgradable; " +
                    "echo '=== ADB VERSION ==='; if command -v adb >/dev/null 2>&1; then adb version; else echo 'ADB chưa cài'; fi; " +
                    "echo '=== KALI: UPGRADABLE ==='; if command -v nh >/dev/null 2>&1; then nh apt update && nh apt list --upgradable; else echo 'Kali NetHunter chưa cài'; fi";
                line("Đang kiểm tra danh sách cập nhật Termux / Kali / ADB...", GREEN);
                saveUpdateHistory("Quét phiên bản Termux / Kali / ADB", "Đã gửi yêu cầu quét; xem kết quả ở nhật ký terminal.");
                runTermuxCommand(cmd);
                line("AI: model chạy qua API nhà cung cấp; kiểm tra kết nối tại mục AI, không có gói model cục bộ để apt nâng cấp.", 0xff79c9ff);
            }).setNegativeButton("HỦY", null).show();
    }

    private void saveUpdateHistory(String title, String detail) {
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_update_history", MODE_PRIVATE);
        String old = prefs.getString("entries", "");
        String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(new java.util.Date());
        String entry = stamp + " | " + title + " | " + detail;
        String next = entry + (old.isEmpty() ? "" : "\n" + old);
        String[] rows = next.split("\n");
        StringBuilder kept = new StringBuilder();
        for (int i=0; i<rows.length && i<30; i++) {
            if (kept.length()>0) kept.append("\n");
            kept.append(rows[i]);
        }
        prefs.edit().putString("entries", kept.toString()).apply();
    }

    private void showUpdateHistory() {
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_update_history", MODE_PRIVATE);
        String history = prefs.getString("entries", "");
        if (history.isEmpty()) history = "Chưa có tác vụ cập nhật nào được ghi lại.";
        final String historyToCopy = history;
        new AlertDialog.Builder(this).setTitle("Lịch sử cập nhật")
            .setMessage(history + "\n\nLưu ý: mỗi tác vụ có thể có hai dòng: lúc gửi lệnh và lúc Termux trả kết quả. Chỉ trạng thái exit 0 mới xác nhận lệnh kết thúc thành công; vẫn cần đọc đầu ra để biết gói nào đã được cập nhật.")
            .setNeutralButton("SAO CHÉP", (d,w) ->
                copyText(historyToCopy, "Đã sao chép lịch sử cập nhật vào clipboard."))
            .setPositiveButton("XÓA LỊCH SỬ", (d,w) -> new AlertDialog.Builder(this)
                .setTitle("Xóa lịch sử?").setMessage("Chỉ xóa nhật ký cập nhật trên ứng dụng này.")
                .setPositiveButton("XÓA", (x,y) -> {
                    getSharedPreferences("bo_kali_update_history", MODE_PRIVATE).edit().clear().apply();
                    line("Đã xóa lịch sử cập nhật.", GREEN);
                }).setNegativeButton("HỦY", null).show())
            .setNegativeButton("ĐÓNG", null).show();
    }

    private void confirmUpdateCommand(String title, String description, String command) {
        new AlertDialog.Builder(this).setTitle(title)
            .setMessage(description + "\n\nChỉ chạy khi bạn đồng ý. Nên dùng Wi-Fi và pin đủ.")
            .setPositiveButton("CẬP NHẬT", (d,w) -> {
                line("Bắt đầu: " + title, GREEN);
                saveUpdateHistory(title, "Đã gửi lệnh nâng cấp qua Termux; cần kiểm tra kết quả trả về.");
                runTermuxCommand(command);
            }).setNegativeButton("ĐỂ SAU", null).show();
    }

    private void checkAppUpdate() {
        line("Đang kiểm tra bản BO Kali AI mới trên GitHub...", GREEN);
        worker.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://api.github.com/repos/temu838655-dev/Truy-n/releases/tags/bo-kali-ai-latest");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(12000); conn.setReadTimeout(15000);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                int code = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                    code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8));
                StringBuilder body = new StringBuilder(); String row;
                while ((row = br.readLine()) != null) body.append(row);
                if (code >= 200 && code < 300) {
                    JSONObject release = new JSONObject(body.toString());
                    String tag = release.optString("tag_name", "không rõ");
                    String name = release.optString("name", tag);
                    String html = release.optString("html_url", "https://github.com/temu838655-dev/Truy-n/releases");
                    String notes = release.optString("body", "");
                    JSONArray assets = release.optJSONArray("assets");
                    boolean hasApk = false;
                    long apkSize = 0;
                    if (assets != null) for (int i=0;i<assets.length();i++) {
                        JSONObject a = assets.getJSONObject(i);
                        if ("app-debug.apk".equals(a.optString("name"))) {
                            hasApk = true; apkSize = a.optLong("size"); break;
                        }
                    }
                    int latestCode = -1;
                    java.util.regex.Matcher matcher = java.util.regex.Pattern
                        .compile("(?i)Version code:\\s*(\\d+)").matcher(notes);
                    if (matcher.find()) latestCode = Integer.parseInt(matcher.group(1));
                    android.content.pm.PackageInfo installedInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                    int installedCode = installedInfo.versionCode;
                    String installedName = installedInfo.versionName;
                    final boolean updateAvailable = latestCode > installedCode;
                    final String status;
                    if (latestCode < 0) status = "Release chưa có metadata versionCode. Hãy build lại bằng workflow mới để bật so sánh phiên bản.";
                    else if (updateAvailable) status = "CÓ BẢN MỚI! Version code " + latestCode + " cao hơn bản đang cài.";
                    else status = "Ứng dụng đang ở bản mới nhất hoặc ngang phiên bản (version code " + latestCode + ").";
                    final String message = "Đang cài: " + installedName + " (code " + installedCode + ")"
                        + "\nBản GitHub: " + name + " (" + tag + ")"
                        + (latestCode >= 0 ? "\nVersion code mới: " + latestCode : "")
                        + "\n" + status
                        + "\nAPK: " + (hasApk ? (apkSize + " byte") : "chưa đính kèm");
                    final String page = html;
                    runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle(updateAvailable ? "Có cập nhật BO Kali AI" : "Kiểm tra BO Kali AI")
                        .setMessage(message)
                        .setPositiveButton(updateAvailable ? "MỞ TẢI BẢN MỚI" : "MỞ GITHUB RELEASE", (d,w) ->
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(page))))
                        .setNegativeButton("ĐÓNG", null).show());
                } else {
                    final String err = "GitHub trả HTTP " + code + ". Có thể chưa tạo release hoặc mạng lỗi.";
                    runOnUiThread(() -> line(err, 0xffffca70));
                }
            } catch (Exception e) {
                runOnUiThread(() -> line("Không kiểm tra được cập nhật app: " + e.getMessage(), 0xffff8080));
            } finally { if (conn != null) conn.disconnect(); }
        });
    }

    private void showAiUpdateInfo() {
        String info = "AI gọi qua API nên model do nhà cung cấp cập nhật phía máy chủ. GPT / Claude / Codex cần nhà cung cấp, tài khoản và API tương ứng. App có thể cập nhật bộ kết nối, nhưng không thể tự nâng cấp model phía hãng hoặc bỏ qua giới hạn miễn phí. API key hiện chỉ giữ trong bộ nhớ phiên.";
        new AlertDialog.Builder(this).setTitle("Cập nhật AI")
            .setMessage(info + "\n\nPhiên bản Android app hiện tại: " + getVersionName())
            .setPositiveButton("ĐÃ HIỂU", null).show();
    }

    private String getVersionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "không rõ"; }
    }

    // Reads the selected device's installed firmware metadata over authorized ADB.
    // OTA availability and installation remain OEM-controlled; never flash generic images.
    private void showFirmwareManager() {
        EditText serial = input("Serial ADB (xem bằng nút THIẾT BỊ)");
        new AlertDialog.Builder(this).setTitle("Quản lý Firmware / OTA")
            .setMessage("Nhập serial của thiết bị đã ghép đôi và kết nối. App sẽ đọc Android version, hãng, model, build fingerprint và bản vá bảo mật. Sau đó có thể mở màn hình cập nhật chính thức của thiết bị. Không tự flash firmware.")
            .setView(serial)
            .setPositiveButton("KIỂM TRA PHIÊN BẢN", (d,w) -> {
                String id = serial.getText().toString().trim();
                if (id.isEmpty() || !id.matches("[A-Za-z0-9._:-]+")) {
                    line("Serial ADB không hợp lệ.", 0xffff8080); return;
                }
                String props = "for p in ro.product.manufacturer ro.product.brand ro.product.model ro.product.device ro.build.version.release ro.build.version.sdk ro.build.display.id ro.build.fingerprint ro.build.version.incremental ro.build.version.security_patch; do echo \"$p=$(getprop $p)\"; done";
                runTermuxCommand("adb -s " + shellQuote(id) + " shell " + shellQuote(props));
                line("Đã gửi yêu cầu đọc thông tin firmware của " + id + ".", GREEN);
                line("Lưu ý: đây là phiên bản ĐANG CÀI, không phải xác nhận có OTA mới.", 0xffffca70);
            })
            .setNeutralButton("MỞ KIỂM TRA OTA", (d,w) -> {
                String id = serial.getText().toString().trim();
                if (id.isEmpty() || !id.matches("[A-Za-z0-9._:-]+")) {
                    line("Serial ADB không hợp lệ.", 0xffff8080); return;
                }
                new AlertDialog.Builder(this)
                    .setTitle("Mở trình cập nhật của thiết bị?")
                    .setMessage("Lệnh chỉ mở trang cập nhật hệ thống trên thiết bị đã kết nối. Việc tải/cài đặt do Android và hãng thiết bị quyết định.")
                    .setPositiveButton("MỞ", (x,y) ->
                        runTermuxCommand("adb -s " + shellQuote(id) + " shell am start -a android.settings.SYSTEM_UPDATE_SETTINGS"))
                    .setNegativeButton("HỦY", null).show();
            })
            .setNegativeButton("ĐÓNG", null).show();
    }

    private void showAdbPairDialog() {
        LinearLayout form = new LinearLayout(this); form.setOrientation(1); form.setPadding(dp(16), dp(8), dp(16), 0);
        EditText endpoint = input("IP: CỔNG GHÉP ĐÔI (ví dụ 192.168.1.20:37123)");
        EditText code = input("Mã ghép đôi 6 chữ số"); code.setInputType(2);
        form.addView(endpoint); form.addView(code);
        new AlertDialog.Builder(this).setTitle("Ghép đôi ADB không dây")
            .setMessage("Trên thiết bị cần điều khiển: bật Developer options → Wireless debugging → Pair device with pairing code. Hai thiết bị phải cùng mạng Wi-Fi. Nhập đúng IP:cổng pairing và mã hiện trên thiết bị đó.")
            .setView(form).setPositiveButton("GHÉP ĐÔI", (d,w) -> {
                String ep = endpoint.getText().toString().trim(); String pin = code.getText().toString().trim();
                if (!ep.matches("[A-Za-z0-9.:-]+:[0-9]{1,5}") || !pin.matches("[0-9]{6}")) { line("IP:cổng hoặc mã ghép đôi không hợp lệ.", 0xffff8080); return; }
                runTermuxCommand("pkg install -y android-tools && adb pair " + shellQuote(ep) + " " + shellQuote(pin));
            }).setNegativeButton("HỦY", null).show();
    }

    private void showAdbConnectDialog() {
        EditText endpoint = input("IP:CỔNG KẾT NỐI (không phải cổng pairing)");
        new AlertDialog.Builder(this).setTitle("Kết nối ADB")
            .setMessage("Sau khi ghép đôi thành công, quay lại Wireless debugging để xem IP address & port. Nhập endpoint kết nối hiển thị ở màn hình chính Wireless debugging.")
            .setView(endpoint).setPositiveButton("KẾT NỐI", (d,w) -> {
                String ep = endpoint.getText().toString().trim();
                if (!ep.matches("[A-Za-z0-9.:-]+:[0-9]{1,5}")) { line("Địa chỉ IP:cổng không hợp lệ.", 0xffff8080); return; }
                runTermuxCommand("pkg install -y android-tools && adb connect " + shellQuote(ep) + " ; adb devices -l");
            }).setNegativeButton("HỦY", null).show();
    }

    private void runTermuxCommand(String command) {
        try {
            int request = TERMUX_REQUEST.incrementAndGet();
            Intent callback = new Intent(this, TermuxResultReceiver.class);
            PendingIntent resultPending = PendingIntent.getBroadcast(this, request, callback,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            Intent intent = new Intent("com.termux.RUN_COMMAND");
            intent.setClassName("com.termux", "com.termux.app.RunCommandService");
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", command});
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            intent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", resultPending);
            intent.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "BO Kali AI - ADB do người dùng xác nhận");
            startService(intent);
            line("Đã gửi tác vụ ADB đến Termux. Đang chờ kết quả...", GREEN);
        } catch (SecurityException e) { line("Termux chưa bật allow-external-apps hoặc chưa cấp quyền RUN_COMMAND.", 0xffffca70); openTermux(); }
          catch (Exception e) { line("Không chạy được ADB trong Termux: " + e.getMessage(), 0xffff8080); }
    }

    private void showAdbDeviceManager() {
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
        java.util.ArrayList<String> devices = new java.util.ArrayList<>(
            prefs.getStringSet("serials", new java.util.HashSet<>()));
        java.util.Collections.sort(devices);
        long lastScan = prefs.getLong("last_scan_time", 0L);
        String scanLabel = lastScan == 0L ? "Chưa có lần quét thành công nào được ghi nhận."
            : "Lần quét gần nhất: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date(lastScan)) + ".";
        showAdbDevicePicker(devices, scanLabel, true);
    }

    private void showAdbDevicePicker(java.util.ArrayList<String> devices, String scanLabel, boolean allowClear) {
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
        java.util.ArrayList<String> filtered = new java.util.ArrayList<>(devices);
        java.util.Collections.sort(filtered);
        String[] labels = new String[filtered.size()];
        for (int i = 0; i < filtered.size(); i++) {
            String serial = filtered.get(i);
            String state = prefs.getString("state_" + serial, "chưa kiểm tra");
            long seen = prefs.getLong("seen_" + serial, 0L);
            String seenLabel = seen == 0L ? "chưa ghi nhận"
                : new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                    .format(new java.util.Date(seen));
            String alias = prefs.getString("alias_" + serial, "");
            labels[i] = (alias.isEmpty() ? serial : alias + "  ·  " + serial)
                + "  [" + adbStateLabel(state) + "]  •  " + seenLabel;
        }
        String message = filtered.isEmpty()
            ? "Không có thiết bị khớp bộ lọc. " + scanLabel
            : "Hiển thị " + filtered.size() + "/" + devices.size() + " thiết bị đã lưu. " + scanLabel
                + " Trạng thái không đảm bảo thiết bị còn trực tuyến.";
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle("Quản lý thiết bị ADB")
            .setMessage(message)
            .setItems(labels, (d, which) -> showSavedAdbDeviceActions(filtered.get(which)))
            .setPositiveButton("TÌM / LỌC", (d, w) -> {
                EditText query = input("Tên gợi nhớ hoặc serial ADB");
                new AlertDialog.Builder(this).setTitle("Tìm thiết bị ADB").setView(query)
                    .setPositiveButton("TÌM", (x, y) -> {
                        String term = query.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
                        java.util.ArrayList<String> matches = new java.util.ArrayList<>();
                        for (String serial : devices) {
                            String alias = prefs.getString("alias_" + serial, "");
                            if (term.isEmpty() || serial.toLowerCase(java.util.Locale.ROOT).contains(term)
                                || alias.toLowerCase(java.util.Locale.ROOT).contains(term)) matches.add(serial);
                        }
                        showAdbDevicePicker(matches, scanLabel, allowClear);
                    }).setNegativeButton("HỦY", null).show();
            })
            .setNeutralButton("QUÉT LẠI", (d, w) -> runTermuxCommand("pkg install -y android-tools && adb devices -l"))
            .setNegativeButton(allowClear ? "XÓA THIẾT BỊ LƯU" : "QUAY LẠI", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(b -> {
            dialog.dismiss();
            if (!allowClear) { showAdbDeviceManager(); return; }
            new AlertDialog.Builder(this).setTitle("Xóa danh sách đã lưu?")
                .setMessage("Chỉ xóa serial khỏi BO Kali AI, không ngắt kết nối hay thay đổi thiết bị.")
                .setPositiveButton("XÓA", (x, y) -> {
                    prefs.edit().remove("serials").apply();
                    line("Đã xóa danh sách serial ADB đã lưu.", GREEN);
                }).setNegativeButton("HỦY", null).show();
        }));
        dialog.show();
    }

    private String adbStateLabel(String state) {
        if ("device".equals(state)) return "ĐANG KẾT NỐI";
        if ("offline".equals(state)) return "OFFLINE";
        if ("unauthorized".equals(state)) return "CHƯA CẤP QUYỀN";
        if ("not_listed".equals(state)) return "KHÔNG THẤY KHI QUÉT";
        return "CHƯA KIỂM TRA";
    }

    private void showSavedAdbDeviceActions(String serial) {
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
        String state = prefs.getString("state_" + serial, "unknown");
        long seen = prefs.getLong("seen_" + serial, 0L);
        String seenLabel = seen == 0L ? "Chưa ghi nhận lần xuất hiện."
            : "Lần cuối thấy thiết bị: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date(seen)) + ".";
        String alias = prefs.getString("alias_" + serial, "");
        AlertDialog actions = new AlertDialog.Builder(this).setTitle(alias.isEmpty() ? "Thiết bị: " + serial : alias + " • " + serial)
            .setMessage("Trạng thái lần quét gần nhất: " + adbStateLabel(state) + ". " + seenLabel
                + " Hãy kiểm tra lại trước khi chạy lệnh.")
            .setPositiveButton("CHẨN ĐOÁN", (d, w) -> showAdbReadOnlyTools(serial))
            .setNeutralButton("ADB SHELL", (d, w) -> showAdbShellDialogFor(serial))
            .setNegativeButton("QUẢN LÝ", null).create();
        actions.setOnShowListener(v -> actions.getButton(AlertDialog.BUTTON_NEGATIVE)
            .setOnClickListener(b -> {
                actions.dismiss();
                new AlertDialog.Builder(this).setTitle("Quản lý " + serial)
                    .setItems(new String[]{"Kiểm tra kết nối thiết bị này", "Sao chép serial ADB", "Đặt / đổi tên gợi nhớ", "Báo cáo chẩn đoán tổng hợp", "Ngắt kết nối ADB", "Xóa riêng thiết bị khỏi danh sách lưu"}, (dialog, which) -> {
                        if (which == 0) checkSavedAdbDevice(serial);
                        else if (which == 1) copyText(serial, "Đã sao chép serial ADB.");
                        else if (which == 2) showAdbAliasDialog(serial);
                        else if (which == 3) showAdbFullDiagnosticReport(serial);
                        else if (which == 4) confirmAdbDisconnect(serial);
                        else confirmForgetAdbDevice(serial);
                    }).setNegativeButton("ĐÓNG", null).show();
            }));
        actions.show();
    }

    private void checkSavedAdbDevice(String serial) {
        String command = "echo '===== KIEM TRA ADB ====='; "
            + "adb -s " + shellQuote(serial) + " get-state; "
            + "adb -s " + shellQuote(serial) + " shell getprop ro.product.model; "
            + "echo '\\n===== DANH SACH ADB ====='; adb devices -l";
        new AlertDialog.Builder(this).setTitle("Kiểm tra kết nối")
            .setMessage("Kiểm tra serial: " + serial
                + "\\nĐọc trạng thái kết nối, model và làm mới danh sách thiết bị. Không thay đổi dữ liệu trên thiết bị.")
            .setPositiveButton("KIỂM TRA", (d, w) -> runTermuxCommand(command))
            .setNegativeButton("HỦY", null).show();
    }

    private void showAdbAliasDialog(String serial) {
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
        EditText name = input("Ví dụ: TV phòng khách");
        name.setText(prefs.getString("alias_" + serial, ""));
        new AlertDialog.Builder(this).setTitle("Tên gợi nhớ thiết bị")
            .setMessage("Tên chỉ dùng để hiển thị trong BO Kali AI. Serial ADB gốc vẫn được giữ nguyên.")
            .setView(name)
            .setPositiveButton("LƯU", (d, w) -> {
                String alias = name.getText().toString().trim();
                if (alias.length() > 40) alias = alias.substring(0, 40);
                if (alias.isEmpty()) prefs.edit().remove("alias_" + serial).apply();
                else prefs.edit().putString("alias_" + serial, alias).apply();
                line(alias.isEmpty() ? "Đã bỏ tên gợi nhớ cho " + serial : "Đã đặt tên " + alias + " cho " + serial, GREEN);
            }).setNegativeButton("HỦY", null).show();
    }

    private void confirmForgetAdbDevice(String serial) {
        new AlertDialog.Builder(this).setTitle("Xóa thiết bị đã lưu?")
            .setMessage("Chỉ xóa serial " + serial + " cùng trạng thái và thời điểm ghi nhớ khỏi BO Kali AI. Không ngắt kết nối ADB, không xóa dữ liệu trên thiết bị.")
            .setPositiveButton("XÓA KHỎI DANH SÁCH", (d, w) -> {
                android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
                java.util.Set<String> saved = new java.util.HashSet<>(prefs.getStringSet("serials", new java.util.HashSet<>()));
                saved.remove(serial);
                prefs.edit().putStringSet("serials", saved)
                    .remove("state_" + serial).remove("seen_" + serial).remove("alias_" + serial).apply();
                line("Đã xóa " + serial + " khỏi danh sách thiết bị lưu. Kết nối ADB không bị ảnh hưởng.", GREEN);
            }).setNegativeButton("HỦY", null).show();
    }

    private void showAdbFullDiagnosticReport(String serial) {
        String command = "echo '===== BO KALI AI - BAO CAO CHAN DOAN ====='; "
            + "echo '\\n[THIET BI / ANDROID]'; "
            + "echo MODEL; getprop ro.product.model; echo BRAND; getprop ro.product.brand; "
            + "echo ANDROID; getprop ro.build.version.release; echo SDK; getprop ro.build.version.sdk; "
            + "echo BUILD; getprop ro.build.display.id; echo SECURITY_PATCH; getprop ro.build.version.security_patch; "
            + "echo '\\n[PIN / NHIET DO]'; dumpsys battery; "
            + "echo '\\n[BO NHO]'; df -h; "
            + "echo '\\n[CPU / SOC]'; getprop ro.product.cpu.abi; getprop ro.soc.model; "
            + "grep -c '^processor' /proc/cpuinfo; "
            + "echo '\\n[MAN HINH]'; wm size; wm density; "
            + "echo '\\n[THOI GIAN HOAT DONG]'; uptime; "
            + "echo '\\n[NETWORK]'; ip addr";
        String alias = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE)
            .getString("alias_" + serial, "");
        new AlertDialog.Builder(this).setTitle("Báo cáo tổng hợp")
            .setMessage("Thiết bị: " + (alias.isEmpty() ? serial : alias + " • " + serial)
                + "\\nBáo cáo chỉ đọc thông tin hệ thống; không thay đổi cài đặt hoặc dữ liệu.")
            .setPositiveButton("TẠO BÁO CÁO", (d, w) ->
                runTermuxCommand("adb -s " + shellQuote(serial) + " shell " + shellQuote(command)))
            .setNegativeButton("HỦY", null).show();
    }

    private void showAdbReadOnlyTools(String serial) {
        String[] tools = {
            "Thông tin thiết bị / Android",
            "Pin và nhiệt độ",
            "Dung lượng lưu trữ",
            "Địa chỉ mạng",
            "Danh sách ứng dụng bên thứ ba",
            "Màn hình và độ phân giải",
            "Thời gian hoạt động",
            "Thông tin CPU"
        };
        String[] commands = {
            "echo 'MODEL'; getprop ro.product.model; echo 'BRAND'; getprop ro.product.brand; echo 'ANDROID'; getprop ro.build.version.release; echo 'SDK'; getprop ro.build.version.sdk; echo 'BUILD'; getprop ro.build.display.id; echo 'PATCH'; getprop ro.build.version.security_patch",
            "dumpsys battery",
            "df -h",
            "ip addr",
            "pm list packages -3",
            "echo 'DISPLAY'; wm size; wm density; echo 'REFRESH RATE'; dumpsys display | grep -i 'refreshRate\\|fps' | head -20",
            "echo 'UPTIME'; uptime; cat /proc/uptime",
            "echo 'CPU'; getprop ro.product.cpu.abi; echo 'CORES'; grep -c '^processor' /proc/cpuinfo; echo 'SOC'; getprop ro.soc.model; echo 'HARDWARE'; getprop ro.hardware"
        };
        new AlertDialog.Builder(this).setTitle("Chẩn đoán chỉ đọc • " + serial)
            .setItems(tools, (d, which) -> {
                String command = commands[which];
                new AlertDialog.Builder(this).setTitle("Chạy kiểm tra?")
                    .setMessage("Thiết bị: " + serial + "\nLệnh chỉ đọc: " + command
                        + "\n\nLệnh này đọc thông tin, không chủ động thay đổi cài đặt hay dữ liệu.")
                    .setPositiveButton("CHẠY", (x, y) ->
                        runTermuxCommand("adb -s " + shellQuote(serial) + " shell " + shellQuote(command)))
                    .setNegativeButton("HỦY", null).show();
            }).setNegativeButton("ĐÓNG", null).show();
    }

    private void confirmAdbDisconnect(String serial) {
        new AlertDialog.Builder(this).setTitle("Ngắt kết nối ADB?")
            .setMessage("Chỉ ngắt phiên ADB với " + serial + " trên Termux. Không xóa dữ liệu, không tắt Wireless debugging và không thay đổi thiết bị.")
            .setPositiveButton("NGẮT KẾT NỐI", (d, w) ->
                runTermuxCommand("adb disconnect " + shellQuote(serial) + "; adb devices -l"))
            .setNegativeButton("HỦY", null).show();
    }

    private void showAdbShellDialogFor(String serialValue) {
        LinearLayout form = new LinearLayout(this); form.setOrientation(1);
        form.setPadding(dp(16), dp(8), dp(16), 0);
        EditText remoteCommand = input("Lệnh trên thiết bị, ví dụ: getprop ro.product.model");
        form.addView(remoteCommand);
        new AlertDialog.Builder(this).setTitle("ADB Shell • " + serialValue)
            .setMessage("Chỉ chạy trên thiết bị bạn sở hữu/quản lý. Xem lại tác động của lệnh trước khi xác nhận.")
            .setView(form).setPositiveButton("XEM LẠI LỆNH", (d, w) -> {
                String cmd = remoteCommand.getText().toString().trim();
                if (cmd.isEmpty()) { line("Chưa nhập lệnh ADB.", 0xffff8080); return; }
                new AlertDialog.Builder(this).setTitle("Xác nhận ADB Shell")
                    .setMessage("Thiết bị: " + serialValue + "\nLệnh: adb -s " + serialValue + " shell " + cmd
                        + "\n\nLệnh có thể thay đổi thiết bị từ xa.")
                    .setPositiveButton("CHẠY", (x, y) ->
                        runTermuxCommand("adb -s " + shellQuote(serialValue) + " shell " + shellQuote(cmd)))
                    .setNegativeButton("HỦY", null).show();
            }).setNegativeButton("HỦY", null).show();
    }

    private void handleAdbDevicesOutput(String output) {
        if (output == null || !output.contains("List of devices attached")) return;
        java.util.HashSet<String> found = new java.util.HashSet<>();
        String[] rows = output.split("\\n");
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putLong("last_scan_time", System.currentTimeMillis());
        java.util.Set<String> previouslySaved = prefs.getStringSet("serials", new java.util.HashSet<>());
        for (String serial : previouslySaved) editor.putString("state_" + serial, "not_listed");
        for (String row : rows) {
            String trimmed = row.trim();
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^([^\\s]+)\\s+(device|offline|unauthorized)\\b").matcher(trimmed);
            if (matcher.find()) {
                String serial = matcher.group(1);
                String state = matcher.group(2);
                found.add(serial);
                editor.putString("state_" + serial, state);
                if ("device".equals(state)) editor.putLong("seen_" + serial, System.currentTimeMillis());
            }
        }
        java.util.HashSet<String> merged = new java.util.HashSet<>(prefs.getStringSet("serials", new java.util.HashSet<>()));
        merged.addAll(found);
        editor.putStringSet("serials", merged).apply();
        line("ADB: tìm thấy " + found.size() + " thiết bị trong lần quét này; đã ghi nhớ serial để chọn nhanh.", 0xff79c9ff);
        if (found.isEmpty()) line("Không thấy thiết bị. Kiểm tra Wi-Fi, Wireless debugging và chạy adb connect nếu cần.", 0xffffca70);
    }

    private void showAdbShellDialog() {
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
        java.util.ArrayList<String> saved = new java.util.ArrayList<>(
            prefs.getStringSet("serials", new java.util.HashSet<>()));
        java.util.Collections.sort(saved);
        if (saved.isEmpty()) {
            showAdbShellManualDialog();
            return;
        }
        String[] choices = saved.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Chọn thiết bị ADB")
            .setItems(choices, (dialog, which) -> showAdbShellDialogFor(choices[which]))
            .setPositiveButton("NHẬP SERIAL", (dialog, which) -> showAdbShellManualDialog())
            .setNeutralButton("QUÉT LẠI", (dialog, which) ->
                runTermuxCommand("pkg install -y android-tools && adb devices -l"))
            .setNegativeButton("HỦY", null).show();
    }

    private void showAdbShellManualDialog() {
        LinearLayout form = new LinearLayout(this); form.setOrientation(1);
        form.setPadding(dp(16), dp(8), dp(16), 0);
        EditText serial = input("Serial thiết bị (xem trong THIẾT BỊ)");
        EditText remoteCommand = input("Lệnh trên thiết bị, ví dụ: getprop ro.product.model");
        form.addView(serial); form.addView(remoteCommand);
        new AlertDialog.Builder(this).setTitle("ADB Shell • Thiết bị đã ghép đôi")
            .setMessage("Chọn đúng serial từ danh sách adb devices -l. Lệnh sẽ chạy trên thiết bị bạn sở hữu/quản lý. Không nhập lệnh phá hoại hoặc xóa dữ liệu nếu chưa hiểu rõ.")
            .setView(form).setPositiveButton("XEM LẠI LỆNH", (d,w) -> {
                String id = serial.getText().toString().trim();
                String cmd = remoteCommand.getText().toString().trim();
                if (id.isEmpty() || !id.matches("[A-Za-z0-9._:-]+") || cmd.isEmpty()) {
                    line("Cần serial hợp lệ và lệnh ADB.", 0xffff8080); return;
                }
                new AlertDialog.Builder(this).setTitle("Xác nhận chạy ADB Shell")
                    .setMessage("Thiết bị: " + id + "\nLệnh: adb -s " + id + " shell " + cmd
                        + "\n\nLệnh có thể thay đổi thiết bị từ xa. Chỉ tiếp tục nếu bạn hiểu tác động.")
                    .setPositiveButton("CHẠY", (dialog, which) ->
                        runTermuxCommand("adb -s " + shellQuote(id) + " shell " + shellQuote(cmd)))
                    .setNegativeButton("HỦY", null).show();
            }).setNegativeButton("HỦY", null).show();
    }

    private static final String KALI_INSTALL_COMMAND =
        "pkg update -y && pkg install -y wget && " +
        "wget -O install-nethunter-termux https://offs.ec/2MceZWr && " +
        "chmod +x install-nethunter-termux && ./install-nethunter-termux";

    private void kaliShellMenu() {
        new AlertDialog.Builder(this)
            .setTitle("Kali Shell • NetHunter Rootless")
            .setMessage("BO Kali AI dùng Termux làm bộ chạy shell Linux. Sau khi cài Kali và bật quyền chạy lệnh bên ngoài trong Termux, có thể mở Kali trực tiếp từ đây. Cài lần đầu cần Internet, dung lượng trống lớn và thao tác xác nhận. Không cần root.")
            .setPositiveButton("Vào Kali trực tiếp", (d, w) -> runKaliDirectly())
            .setNeutralButton("Cài Kali Rootless", (d, w) -> {
                copyText(KALI_INSTALL_COMMAND, "Đã sao chép lệnh cài Kali. Dán vào Termux để bắt đầu.");
                openTermux();
            })
            .setNegativeButton("Hướng dẫn", (d, w) -> showKaliInstructions())
            .show();
    }


    private void confirmKaliCommand() {
        String command = commandInput.getText().toString().trim();
        if (command.isEmpty()) {
            line("Nhập lệnh Kali trước. Ví dụ: uname -a hoặc ip addr", 0xffffca70);
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Xác nhận chạy lệnh Kali")
            .setMessage("Lệnh sẽ chạy trong Kali Rootless qua Termux:\\n\\n" + command + "\\n\\nChỉ chạy lệnh bạn hiểu và trên thiết bị bạn có quyền quản lý. Kết quả sẽ được đưa về BO Kali AI.")
            .setPositiveButton("Chạy lệnh", (d,w) -> runKaliCommand(command))
            .setNegativeButton("Hủy", null).show();
    }

    private void runKaliCommand(String command) {
        try {
            int request = TERMUX_REQUEST.incrementAndGet();
            Intent callback = new Intent(this, TermuxResultReceiver.class);
            PendingIntent resultPending = PendingIntent.getBroadcast(this, request, callback,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            Intent intent = new Intent("com.termux.RUN_COMMAND");
            intent.setClassName("com.termux", "com.termux.app.RunCommandService");
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", "nh " + shellQuote(command)});
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            intent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", resultPending);
            intent.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "BO Kali AI - lệnh do người dùng xác nhận");
            startService(intent);
            line("Đã gửi lệnh Kali: " + command, GREEN);
            commandInput.setText("");
        } catch (SecurityException e) {
            line("Termux chưa cấp quyền RUN_COMMAND hoặc chưa bật allow-external-apps. Mở Hướng dẫn.", 0xffffca70);
            openTermux();
        } catch (Exception e) {
            line("Không gửi được lệnh Kali: " + e.getMessage(), 0xffff8080);
        }
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void explainLastOutput() {
        if (lastCommandOutput.isEmpty()) {
            line("Chưa có kết quả Kali để giải thích. Chạy một lệnh Kali trước.", 0xffffca70);
            return;
        }
        String outputForAi = lastCommandOutput;
        if (outputForAi.length() > 12000) {
            outputForAi = outputForAi.substring(0, 12000)
                + "\\n[Đã rút gọn kết quả còn 12.000 ký tự để AI phân tích ổn định.]";
        }
        questionInput.setText("Hãy giải thích kết quả/lỗi Kali dưới đây bằng tiếng Việt, chỉ ra nguyên nhân, mức độ rủi ro và cách xử lý an toàn. Không giả định lệnh đã làm gì ngoài nội dung kết quả:\\n\\n" + outputForAi);
        askAi();
    }


    @Override protected void onResume() {
        super.onResume();
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_results", MODE_PRIVATE);
        String pending = prefs.getString("pending_output", null);
        if (pending != null && !pending.isEmpty()) {
            prefs.edit().remove("pending_output").apply();
            lastCommandOutput = pending;
            handleAdbDevicesOutput(pending);
            line("KẾT QUẢ TỪ KALI / TERMUX:", GREEN);
            for (String part : pending.split("\\n")) line(part, WHITE);
            line("Nhấn GIẢI THÍCH KẾT QUẢ KALI BẰNG AI để phân tích.", 0xff79c9ff);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && "com.bokaliai.terminal.TERMUX_RESULT".equals(intent.getAction())) {
            lastCommandOutput = intent.getStringExtra("termux_output");
            if (lastCommandOutput == null) lastCommandOutput = "Termux trả về kết quả trống.";
            handleAdbDevicesOutput(lastCommandOutput);
            line("KẾT QUẢ TỪ KALI / TERMUX:", GREEN);
            for (String part : lastCommandOutput.split("\\n")) line(part, WHITE);
            line("Nhấn GIẢI THÍCH KẾT QUẢ KALI BẰNG AI để phân tích.", 0xff79c9ff);
        }
    }

    private void runKaliDirectly() {
        try {
            Intent intent = new Intent("com.termux.RUN_COMMAND");
            intent.setClassName("com.termux", "com.termux.app.RunCommandService");
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", "nh"});
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
            intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
            startService(intent);
            line("Đã gửi yêu cầu mở Kali trong Termux. Nếu Android báo thiếu quyền, làm theo Hướng dẫn.", GREEN);
        } catch (SecurityException e) {
            line("Termux chưa cấp quyền RUN_COMMAND hoặc chưa bật allow-external-apps. Mở Hướng dẫn để thiết lập.", 0xffffca70);
            openTermux();
        } catch (Exception e) {
            line("Không mở được Kali trực tiếp: " + e.getMessage() + ". Đang mở Termux thay thế.", 0xffff8080);
            openTermux();
        }
    }

    private void showKaliInstructions() {
        new AlertDialog.Builder(this)
            .setTitle("Thiết lập Kali Shell")
            .setMessage("1. Cài Termux từ GitHub chính thức hoặc F-Droid (không dùng bản Play Store cũ).\n" +
                "2. Mở Termux một lần, chờ hoàn tất thiết lập.\n" +
                "3. Trong BO Kali AI → KALI SHELL → Cài Kali Rootless.\n" +
                "4. Chuyển sang Termux, dán lệnh đã sao chép và nhấn Enter.\n" +
                "5. Làm theo câu hỏi của trình cài đặt; không tắt ứng dụng khi tải rootfs.\n" +
                "6. Sau khi cài xong, trong Termux chạy: mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties rồi đóng/mở lại Termux.\n" +
                "7. Vào Cài đặt Android → Ứng dụng → BO Kali AI → Quyền → Quyền bổ sung → cho phép chạy lệnh trong Termux (nếu mục này xuất hiện).\n" +
                "8. Mở BO Kali AI → KALI SHELL → Vào Kali trực tiếp. Nếu chưa bật quyền, mở Termux và nhập nh.\n\n" +
                "Lệnh nh vào Kali, exit để thoát. Cài lần đầu cần mạng và có thể tải nhiều GB nếu cài thêm bộ công cụ.")
            .setPositiveButton("Mở trang Termux", (d, w) ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/termux/termux-app/releases"))))
            .setNegativeButton("Đóng", null).show();
    }

    private void copyLastCommandOutput() {
        if (lastCommandOutput == null || lastCommandOutput.trim().isEmpty()) {
            line("Chưa có kết quả ADB / Kali để sao chép. Hãy chạy lệnh hoặc quét thiết bị trước.", 0xffffca70);
            return;
        }
        copyText(lastCommandOutput, "Đã sao chép kết quả ADB / Kali gần nhất vào clipboard.");
    }

    private void shareLastTermuxReport() {
        String report = getSharedPreferences("bo_kali_results", MODE_PRIVATE).getString("last_output", "");
        if (report == null || report.trim().isEmpty()) {
            line("Chưa có kết quả Termux để chia sẻ. Hãy chạy một lệnh ADB/Kali trước.", 0xffffca70);
            return;
        }
        String preview = report.length() > 1400 ? report.substring(0, 1400) + "\n… (đã rút gọn phần xem trước)" : report;
        new AlertDialog.Builder(this)
            .setTitle("Kiểm tra trước khi chia sẻ")
            .setMessage("Đây là kết quả Termux gần nhất, không nhất thiết là báo cáo chẩn đoán đầy đủ. Nội dung có thể chứa serial ADB, địa chỉ IP, tên thiết bị hoặc dữ liệu riêng tư. Hãy xem lại trước khi gửi.\n\n" + preview)
            .setPositiveButton("CHIA SẺ", (d, w) -> {
                new AlertDialog.Builder(this).setTitle("Chọn mức chia sẻ")
                    .setItems(new String[]{"Chia sẻ nguyên bản", "Ẩn IP và serial ADB trước khi chia sẻ"}, (choiceDialog, which) -> {
                        String content = which == 0 ? report : redactNetworkIdentifiers(report);
                        Intent share = new Intent(Intent.ACTION_SEND);
                        share.setType("text/plain");
                        share.putExtra(Intent.EXTRA_SUBJECT, "BO Kali AI - Kết quả Termux");
                        share.putExtra(Intent.EXTRA_TEXT, content);
                        try {
                            startActivity(Intent.createChooser(share, "Chia sẻ kết quả Termux"));
                        } catch (Exception e) {
                            line("Không mở được ứng dụng chia sẻ: " + e.getMessage(), 0xffff8080);
                        }
                    }).setNegativeButton("HỦY", null).show();
            })
            .setNeutralButton("SAO CHÉP", (d, w) -> copyText(report, "Đã sao chép kết quả Termux."))
            .setNegativeButton("HỦY", null)
            .show();
    }

    private String redactNetworkIdentifiers(String value) {
        if (value == null) return "";
        java.util.regex.Pattern serialPattern = java.util.regex.Pattern.compile(
            "(?m)^([A-Za-z0-9._:-]+)(\\s+(?:device|offline|unauthorized)\\b)");
        java.util.regex.Matcher serialMatcher = serialPattern.matcher(value);
        StringBuffer serialSafe = new StringBuffer();
        while (serialMatcher.find()) {
            serialMatcher.appendReplacement(serialSafe,
                java.util.regex.Matcher.quoteReplacement("[SERIAL ĐÃ ẨN]" + serialMatcher.group(2)));
        }
        serialMatcher.appendTail(serialSafe);
        String redacted = serialSafe.toString().replaceAll(
            "(?<![0-9])(?:25[0-5]|2[0-4][0-9]|1?[0-9]{1,2})(?:\\.(?:25[0-5]|2[0-4][0-9]|1?[0-9]{1,2})){3}(?![0-9])",
            "[IP ĐÃ ẨN]");
        redacted = redacted.replaceAll(
            "(?i)\\b(?:[0-9a-f]{1,4}:){2,}[0-9a-f:]{0,}[0-9a-f]\\b",
            "[IPv6 ĐÃ ẨN]");
        return redacted;
    }

    private void copyText(String value, String message) {
        ClipboardManager cm = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("BO Kali command", value));
        line(message, GREEN);
    }

    private void openTermux() {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.termux");
            if (launch == null) {
                line("Termux chưa được cài. Mở trang tải Termux chính thức.", 0xffffca70);
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/termux/termux-app/releases")));
                return;
            }
            startActivity(launch);
            line("Đã mở Termux. Lệnh cần chạy đã được sao chép; nhấn giữ trong terminal và chọn Dán.", GREEN);
        } catch (Exception e) {
            line("Không mở được Termux: " + e.getMessage(), 0xffff8080);
        }
    }

    private void runCommand() {
        String cmd = commandInput.getText().toString().trim().toLowerCase();
        if (cmd.isEmpty()) return;
        line("bo@kali:~$ " + cmd, GREEN);
        switch(cmd) {
            case "help":
                line("help — danh sách lệnh", WHITE);
                line("device — thông tin model / Android / kernel", WHITE);
                line("net — trạng thái kết nối mạng", WHITE);
                line("wifi — trạng thái Wi-Fi (không bật/tắt)", WHITE);
                line("bluetooth — trạng thái Bluetooth (không quét thiết bị)", WHITE);
                line("ai — hướng dẫn hỏi AI ở ô bên dưới", WHITE);
                line("Lệnh shell Kali thực sự cần cài môi trường Linux riêng; bản này không giả vờ thực thi shell.", 0xffffca70);
                break;
            case "device":
                line("Model: " + android.os.Build.MODEL, WHITE);
                line("Manufacturer: " + android.os.Build.MANUFACTURER, WHITE);
                line("Android: " + android.os.Build.VERSION.RELEASE + " (SDK " + android.os.Build.VERSION.SDK_INT + ")", WHITE);
                line("Kernel: " + System.getProperty("os.version"), WHITE);
                line("ABI: " + android.os.Build.SUPPORTED_ABIS[0], WHITE);
                break;
            case "net": showNetwork(); break;
            case "wifi": showWifi(); break;
            case "bluetooth": showBluetooth(); break;
            case "ai": line("Nhập câu hỏi ở Ô 2 rồi nhấn HỎI AI.", 0xff79c9ff); break;
            default: line("Lệnh chưa được hỗ trợ trong bản rootless MVP. Gõ help hoặc hỏi AI cách thực hiện an toàn.", 0xffffca70);
        }
        commandInput.setText("");
    }

    private void showNetwork() {
        try {
            ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
            if (c == null) { line("Không có kết nối mạng hoạt động.", 0xffffca70); return; }
            line("Internet: " + (c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ? "có mạng" : "không có Internet"), WHITE);
            line("Wi-Fi: " + c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI), WHITE);
            line("Di động: " + c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR), WHITE);
            line("Ethernet: " + c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET), WHITE);
        } catch(Exception e) { line("Lỗi đọc mạng: " + e.getMessage(), 0xffff8080); }
    }

    private void showWifi() {
        try {
            WifiManager wm = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            line("Wi-Fi đang bật: " + (wm != null && wm.isWifiEnabled()), WHITE);
            line("Android giới hạn một số thông tin Wi-Fi; ứng dụng không tự bật monitor mode/injection.", 0xffffca70);
        } catch(Exception e) { line("Không đọc được Wi-Fi: " + e.getMessage(), 0xffff8080); }
    }

    private void showBluetooth() {
        try {
            BluetoothManager bm = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
            BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
            line("Bluetooth adapter: " + (ad == null ? "không có" : "có"), WHITE);
            line("Bluetooth đang bật: " + (ad != null && ad.isEnabled()), WHITE);
            line("Chưa quét thiết bị; cần xin quyền và sự đồng ý trước khi triển khai.", 0xffffca70);
        } catch(SecurityException e) { line("Android không cấp quyền đọc trạng thái Bluetooth: " + e.getMessage(), 0xffffca70); }
        catch(Exception e) { line("Lỗi Bluetooth: " + e.getMessage(), 0xffff8080); }
    }

    private void askAi() {
        String q = questionInput.getText().toString().trim();
        if (q.isEmpty()) return;
        line("Bạn hỏi AI: " + q, 0xff79c9ff);
        if (geminiKey.isEmpty()) {
            line("Chưa cấu hình Gemini API key. Mở 'Kết nối AI / GitHub'. Không có khóa thì AI online chưa thể gọi.", 0xffffca70);
            return;
        }
        questionInput.setText("");
        line("AI đang phân tích...", GREEN);
        worker.execute(() -> {
            try {
                String prompt = "Bạn là trợ lý BO Kali AI. Trả lời bằng tiếng Việt dễ hiểu. "
                    + "Giải thích lệnh, từng tham số, quyền cần có và rủi ro. Không tự nhận đã chạy lệnh. "
                    + "Chỉ hướng dẫn quản trị thiết bị/mạng do người dùng sở hữu hoặc được ủy quyền. Câu hỏi: " + q;
                JSONObject part = new JSONObject().put("text", prompt);
                JSONObject body = new JSONObject().put("contents", new JSONArray().put(new JSONObject().put("parts", new JSONArray().put(part))));
                URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiKey);
                HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                conn.setRequestMethod("POST"); conn.setConnectTimeout(15000); conn.setReadTimeout(30000);
                conn.setDoOutput(true); conn.setRequestProperty("Content-Type","application/json");
                try(OutputStream os = conn.getOutputStream()) { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
                int code = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder(); String s; while((s=br.readLine())!=null) sb.append(s);
                conn.disconnect();
                JSONObject result = new JSONObject(sb.toString());
                String answer;
                if(code >= 200 && code < 300) {
                    JSONArray candidates = result.optJSONArray("candidates");
                    if (candidates != null && candidates.length() > 0) {
                        JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
                        JSONArray parts = content == null ? null : content.optJSONArray("parts");
                        answer = (parts != null && parts.length() > 0)
                            ? parts.getJSONObject(0).optString("text", "AI không trả nội dung văn bản.")
                            : "Gemini không trả nội dung văn bản. Có thể yêu cầu bị chặn hoặc cần thử lại.";
                    } else {
                        answer = "Gemini không trả câu trả lời. Phản hồi: " + result.toString();
                    }
                } else answer = "Gemini API báo lỗi HTTP " + code + ": " + result.toString();
                final String out = answer;
                runOnUiThread(() -> line("AI: " + out, WHITE));
            } catch(Exception e) { runOnUiThread(() -> line("Lỗi kết nối AI: " + e.getMessage(), 0xffff8080)); }
        });
    }

    private void connectionSettings() {
        LinearLayout box = new LinearLayout(this); box.setPadding(24,8,24,8); box.setOrientation(1);
        EditText gem = input("Gemini API key (chỉ lưu trong bộ nhớ phiên này)");
        gem.setText(geminiKey); box.addView(gem);
        EditText gh = input("GitHub Personal Access Token (chỉ lưu trong bộ nhớ phiên này)");
        gh.setText(githubToken); box.addView(gh);
        new AlertDialog.Builder(this).setTitle("Kết nối AI / GitHub")
            .setMessage("Gemini: dùng API key từ Google AI Studio. GitHub: token chỉ cần quyền đọc repo để thử kết nối. Không chia sẻ token cho ai.")
            .setView(box)
            .setPositiveButton("Lưu và kiểm tra GitHub", (d,w) -> {
                geminiKey = gem.getText().toString().trim();
                githubToken = gh.getText().toString().trim();
                if(githubToken.isEmpty()) { line("Đã cập nhật Gemini key trong bộ nhớ phiên (nếu có). Chưa nhập GitHub token.", GREEN); return; }
                checkGithub();
            }).setNegativeButton("Hủy", null).show();
    }

    private void checkGithub() {
        line("Đang kiểm tra GitHub...", GREEN);
        worker.execute(() -> {
            try {
                URL url = new URL("https://api.github.com/user/repos?per_page=10");
                HttpURLConnection c = (HttpURLConnection)url.openConnection();
                c.setRequestProperty("Authorization","Bearer " + githubToken);
                c.setRequestProperty("Accept","application/vnd.github+json");
                c.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
                c.setConnectTimeout(15000); c.setReadTimeout(20000);
                int code = c.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(code>=200 && code<300 ? c.getInputStream() : c.getErrorStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder(); String s; while((s=br.readLine())!=null) sb.append(s);
                c.disconnect();
                String msg;
                if(code>=200 && code<300) {
                    JSONArray arr = new JSONArray(sb.toString());
                    StringBuilder names = new StringBuilder("GitHub kết nối thành công. Repository gần đây:\n");
                    for(int i=0;i<Math.min(arr.length(),10);i++) names.append("• ").append(arr.getJSONObject(i).optString("full_name")).append("\n");
                    msg=names.toString();
                } else msg="GitHub trả HTTP " + code + ". Kiểm tra token/quyền truy cập.";
                final String out=msg; runOnUiThread(() -> line(out, WHITE));
            } catch(Exception e) { runOnUiThread(() -> line("Lỗi GitHub: " + e.getMessage(), 0xffff8080)); }
        });
    }

    private EditText input(String hint) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setTextColor(WHITE); e.setHintTextColor(0xff91a9bd);
        e.setHint(hint); e.setTextSize(13); e.setPadding(12,4,12,4); e.setBackgroundColor(0xff1b2d40); return e;
    }
    private Button button(String s) {
        Button b = new Button(this); b.setText(s); b.setTextSize(11); b.setAllCaps(false); b.setTextColor(WHITE); return b;
    }
    private TextView text(String s,int color,int size,boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextColor(color); t.setTextSize(size);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setPadding(2,5,2,5); return t;
    }
    private void line(String s,int color) {
        TextView t = text(s,color,12,false); t.setTypeface(Typeface.MONOSPACE); t.setPadding(2,5,2,5);
        log.addView(t); if(scroll!=null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }
    private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
    @Override protected void onDestroy() { super.onDestroy(); worker.shutdownNow(); geminiKey=""; githubToken=""; }
}
