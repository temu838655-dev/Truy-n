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
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.text.InputType;
import android.widget.ImageView;
import android.graphics.BitmapFactory;
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
    private String pendingExportText = "";
    private static final AtomicInteger TERMUX_REQUEST = new AtomicInteger(2000);
    private NsdManager nsdManager;
    private WifiManager.MulticastLock hubMulticastLock;
    private final java.util.ArrayDeque<Runnable> adbResolveQueue = new java.util.ArrayDeque<>();
    private final java.util.HashSet<String> adbResolveQueuedKeys = new java.util.HashSet<>();
    private boolean adbResolveInFlight = false;
    private int adbResolveTaskToken = 0;
    private String adbResolveCurrentKey = "";
    private long adbResolveDrainDeadline = 0L;
    private Runnable adbResolveTimeout;
    private NsdManager.DiscoveryListener adbDiscoveryListener;
    private NsdManager.DiscoveryListener adbPairingDiscoveryListener;
    private Runnable adbDiscoveryTimeout;
    private Runnable adbPairingDiscoveryTimeout;
    private boolean adbPairingScanActive = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean adbScanActive = false;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3101);
        }
        line("BO Kali AI v" + getAppVersionName() + " — Android safe terminal", GREEN);
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
        Button dualWorkspace = button("MÀN HÌNH KÉP");
        dualWorkspace.setOnClickListener(v -> showDualWorkspace());
        root.addView(dualWorkspace, new LinearLayout.LayoutParams(-1, dp(44)));
        Button carPlay = button("BO CARPLAY RECEIVER • iPHONE USB / KHÔNG DÂY");
        carPlay.setOnClickListener(v -> showCarPlayReceiverSetup());
        root.addView(carPlay, new LinearLayout.LayoutParams(-1, dp(46)));
        Button carPlayAuth = button("CARPLAY • BỘ XÁC THỰC TỰ ĐỘNG");
        carPlayAuth.setOnClickListener(v -> manageCarPlayAuthorization());
        root.addView(carPlayAuth, new LinearLayout.LayoutParams(-1, dp(42)));
        Button deviceHub = button("TRUNG TÂM THIẾT BỊ ĐA NỀN TẢNG");
        deviceHub.setOnClickListener(v -> showUniversalDeviceHub());
        root.addView(deviceHub, new LinearLayout.LayoutParams(-1, dp(46)));
        Button routerWifi = button("KẾT NỐI MODEM / ROUTER WI-FI");
        routerWifi.setOnClickListener(v -> showWifiRouterManager());
        root.addView(routerWifi, new LinearLayout.LayoutParams(-1, dp(46)));

        Button batchAdb = button("KIỂM TRA NHIỀU THIẾT BỊ");
        batchAdb.setOnClickListener(v -> showAdbBatchCheck());
        root.addView(batchAdb);
        Button adbBackup = button("SAO LƯU / KHÔI PHỤC ADB");
        adbBackup.setOnClickListener(v -> showAdbBackupMenu());
        root.addView(adbBackup);
        Button scanPairing = button("QUÉT CỔNG GHÉP ĐÔI");
        scanPairing.setOnClickListener(v -> scanAdbPairingServices());
        root.addView(scanPairing, new LinearLayout.LayoutParams(-1, dp(40)));
        Button mdnsCheck = button("KIỂM TRA mDNS / ADB");
        mdnsCheck.setOnClickListener(v -> checkAdbMdns());
        root.addView(mdnsCheck, new LinearLayout.LayoutParams(-1, dp(40)));
        Button shareReport = button("CHIA SẺ KẾT QUẢ TERMUX");
        shareReport.setOnClickListener(v -> shareLastTermuxReport());
        root.addView(shareReport, new LinearLayout.LayoutParams(-1, dp(42)));
        Button exportReport = button("XUẤT KẾT QUẢ ADB / KALI THÀNH TỆP TXT");
        exportReport.setOnClickListener(v -> exportLastResult());
        root.addView(exportReport, new LinearLayout.LayoutParams(-1, dp(42)));
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


    // Dual workspace: the device pane is available only for a recently verified ADB device.
    private boolean isFreshAdbDevice(String serial) {
        if (serial == null || serial.isEmpty()) return false;
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
        long scannedAt = prefs.getLong("last_scan_time", 0L);
        return "device".equals(prefs.getString("state_" + serial, "unknown"))
            && scannedAt > 0L && System.currentTimeMillis() - scannedAt <= 90_000L;
    }

    private java.util.ArrayList<String> getFreshAdbDevices() {
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        for (String serial : prefs.getStringSet("serials", new java.util.HashSet<>())) {
            if (isFreshAdbDevice(serial)) result.add(serial);
        }
        java.util.Collections.sort(result);
        return result;
    }

    private ScrcpyMirrorView activeMirrorView;
    private String activeMirrorSerial = "";
    private Runnable scrcpyResumeRetryRunnable;

    private void cancelScrcpyResumeRetry() {
        if (scrcpyResumeRetryRunnable != null) {
            mainHandler.removeCallbacks(scrcpyResumeRetryRunnable);
            scrcpyResumeRetryRunnable = null;
        }
    }

    // Stable per-device scrcpy identity prevents targets from sharing one socket/port.
    // ADB forward ports are host-wide, so allocate and persist collision-free ports.
    private int scrcpyPortFor(String serial) {
        if (serial == null || serial.isEmpty()) return 20001;
        android.content.SharedPreferences ports =
            getSharedPreferences("bo_kali_scrcpy_ports", MODE_PRIVATE);
        String key = "port_" + android.util.Base64.encodeToString(
            serial.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
        int saved = ports.getInt(key, -1);
        java.util.HashSet<Integer> used = new java.util.HashSet<>();
        for (java.util.Map.Entry<String, ?> entry : ports.getAll().entrySet()) {
            if (!entry.getKey().startsWith("port_") || entry.getKey().equals(key)) continue;
            Object value = entry.getValue();
            if (value instanceof Integer) used.add((Integer) value);
        }
        if (saved >= 20000 && saved <= 59999 && !used.contains(saved)) return saved;

        int seed = serial.hashCode() & 0x7fffffff;
        int candidate = 20000 + (seed % 40000);
        for (int attempt = 0; attempt < 40000; attempt++) {
            int port = 20000 + ((candidate - 20000 + attempt) % 40000);
            if (!used.contains(port)) {
                ports.edit().putInt(key, port).apply();
                return port;
            }
        }
        // Practically unreachable; retain a deterministic fallback for graceful failure.
        return candidate;
    }

    private String scrcpyIdFor(String serial) {
        int id = serial == null ? 1 : (serial.hashCode() & 0x7fffffff);
        if (id == 0) id = 1;
        return String.format(java.util.Locale.US, "%08x", id);
    }

    private void runAdbMirrorStart(String serial) {
        if (!isFreshAdbDevice(serial)) {
            line("Không thể truyền màn hình: thiết bị ADB không còn trực tuyến.", 0xffffca70);
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Bắt đầu điều khiển trực tiếp")
            .setMessage("BO Kali AI sẽ tải máy chủ scrcpy chính thức từ GitHub, khởi chạy tạm thời trên thiết bị đã kết nối và truyền hình ảnh cùng thao tác chạm qua ADB. Chỉ tiếp tục khi bạn sở hữu thiết bị hoặc có sự đồng ý của chủ thiết bị.")
            .setPositiveButton("BẮT ĐẦU", (d, w) -> {
                cancelScrcpyResumeRetry();
                adbMirrorResumeRetries = 0;
                adbMirrorResumePending = false;
                activeMirrorSerial = serial;
                startAdbKeepAlive(serial);
                String scrcpyRequestToken = java.util.UUID.randomUUID().toString();
                getSharedPreferences("bo_kali_adb_session", MODE_PRIVATE).edit()
                    .putString("active_serial", serial).putString("active_request_token", scrcpyRequestToken)
                    .putBoolean("keep_alive", true).apply();
                if (activeMirrorView != null) activeMirrorView.setStatus("Đang chuẩn bị máy chủ truyền màn hình...");
                String serverHash = "84924bd564a1eb6089c872c7521f968058977f91f5ff02514a8c74aff3210f3a";
                String serverUrl = "https://github.com/Genymobile/scrcpy/releases/download/v4.0/scrcpy-server-v4.0";
                final int mirrorPort = scrcpyPortFor(serial);
                final String mirrorId = scrcpyIdFor(serial);
                getSharedPreferences("bo_kali_adb_session", MODE_PRIVATE).edit()
                    .putInt("active_port", mirrorPort).putString("active_scrcpy_id", mirrorId).apply();
                String remoteStart = "CLASSPATH=/data/local/tmp/bo-kali-scrcpy-server.jar app_process / com.genymobile.scrcpy.Server 4.0 scid=" + mirrorId + " tunnel_forward=true audio=false video_codec=h264 max_size=1280 video_bit_rate=4000000 max_fps=30 send_device_meta=false send_dummy_byte=false cleanup=true >/dev/null 2>&1 </dev/null &";
                String socketName = "scrcpy_" + mirrorId;
                String serialArg = shellQuote(serial);
                String command = "echo BO_SCRCPY_REQUEST:" + scrcpyRequestToken + "; set -e; pkg install -y android-tools curl coreutils >/dev/null; "
                    + "mkdir -p \"$HOME/.bo-kali-ai\"; SERVER=\"$HOME/.bo-kali-ai/scrcpy-server-v4.0\"; "
                    + "if [ ! -s \"$SERVER\" ] || [ \"$(sha256sum \"$SERVER\" | cut -d' ' -f1)\" != \"" + serverHash + "\" ]; then "
                    + "curl -fL --retry 3 \"" + serverUrl + "\" -o \"$SERVER.tmp\"; "
                    + "echo \"" + serverHash + "  $SERVER.tmp\" | sha256sum -c -; mv \"$SERVER.tmp\" \"$SERVER\"; fi; "
                    + "adb -s " + serialArg + " forward --remove tcp:" + mirrorPort + " >/dev/null 2>&1 || true; "
                    + "adb -s " + serialArg + " push \"$SERVER\" /data/local/tmp/bo-kali-scrcpy-server.jar >/dev/null; "
                    + "adb -s " + serialArg + " forward tcp:" + mirrorPort + " localabstract:" + socketName + "; "
                    + "adb -s " + serialArg + " shell " + shellQuote(remoteStart) + "; "
                    + "if adb -s " + serialArg + " shell " + shellQuote("for i in 1 2 3 4 5; do grep -F '" + socketName + "' /proc/net/unix >/dev/null 2>&1 && exit 0; sleep 1; done; exit 1") + "; then "
                    + "echo BO_SCRCPY_READY:" + scrcpyRequestToken + ":" + mirrorPort + "; "
                    + "else echo 'BO_SCRCPY_ERROR: Máy chủ scrcpy không mở socket'; exit 1; fi";
                runTermuxCommand(command);
            })
            .setNegativeButton("HỦY", null).show();
    }

    private void startAdbKeepAlive(String serial) {
        Intent keepAlive = new Intent(this, AdbKeepAliveService.class);
        keepAlive.setAction(AdbKeepAliveService.ACTION_START);
        keepAlive.putExtra(AdbKeepAliveService.EXTRA_SERIAL, serial);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(keepAlive);
        else startService(keepAlive);
    }

    private void stopAdbKeepAlive() {
        Intent keepAlive = new Intent(this, AdbKeepAliveService.class);
        keepAlive.setAction(AdbKeepAliveService.ACTION_STOP);
        startService(keepAlive);
    }

    private void stopAdbMirror(String serial) {
        android.content.SharedPreferences session =
            getSharedPreferences("bo_kali_adb_session", MODE_PRIVATE);
        String activeSerial = session.getString("active_serial", "");
        boolean stoppingActiveSession = serial != null && serial.equals(activeSerial);

        if (stoppingActiveSession) {
            cancelScrcpyResumeRetry();
            adbMirrorResumePending = false;
            if (activeMirrorView != null && serial.equals(activeMirrorSerial)) {
                activeMirrorView.stop();
            }
            // Invalidate in-flight Termux results so a late callback cannot revive a stopped session.
            session.edit().putBoolean("keep_alive", false)
                .remove("active_serial").remove("active_port").remove("active_scrcpy_id")
                .remove("active_request_token").apply();
            stopAdbKeepAlive();
            activeMirrorSerial = "";
        }

        String stopCommand;
        if (serial != null && !serial.isEmpty()) {
            int mirrorPort = scrcpyPortFor(serial);
            stopCommand = "adb -s " + shellQuote(serial)
                + " forward --remove tcp:" + mirrorPort + " >/dev/null 2>&1 || true; "
                + "adb disconnect " + shellQuote(serial) + " >/dev/null 2>&1 || true; ";
        } else {
            stopCommand = "echo 'BO_ADB_STOPPED: Không có serial để ngắt'; ";
        }
        // Stopping a non-active target must not interrupt the foreground keep-alive
        // service that is protecting another device's session.
        runTermuxCommand(stopCommand + "echo BO_ADB_STOPPED");
    }


    // Router management starts from the active Wi-Fi DHCP gateway. It never guesses
    // credentials or attempts to bypass the router's administrator authentication.
    private String getWifiGatewayAddress() {
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi == null || !wifi.isWifiEnabled()) return "";
            android.net.DhcpInfo dhcp = wifi.getDhcpInfo();
            if (dhcp == null || dhcp.gateway == 0) return "";
            int g = dhcp.gateway;
            return (g & 0xff) + "." + ((g >> 8) & 0xff) + "."
                + ((g >> 16) & 0xff) + "." + ((g >> 24) & 0xff);
        } catch (Exception ignored) {
            return "";
        }
    }

    private void showWifiRouterManager() {
        String gateway = getWifiGatewayAddress();
        String wifiName = "";
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null && wifi.isWifiEnabled() && Build.VERSION.SDK_INT < 31) {
                android.net.wifi.WifiInfo info = wifi.getConnectionInfo();
                if (info != null && info.getSSID() != null) wifiName = info.getSSID().replace("\"", "");
            }
        } catch (Exception ignored) { }
        String detail = gateway.isEmpty()
            ? "Chưa lấy được địa chỉ gateway. Hãy kết nối điện thoại vào Wi-Fi của modem rồi thử lại. Nếu Android đang dùng dữ liệu di động, hãy chuyển sang Wi-Fi."
            : "Wi-Fi hiện tại: " + (wifiName.isEmpty() ? "(Android ẩn tên mạng)" : wifiName)
                + "\nGateway/modem thường là: " + gateway
                + "\nĐây là địa chỉ mạng nội bộ, không phải mật khẩu quản trị.";
        String[] actions = gateway.isEmpty()
            ? new String[]{"NHẬP IP MODEM THỦ CÔNG", "ĐÓNG"}
            : new String[]{"MỞ TRANG QUẢN TRỊ HTTP", "MỞ TRANG QUẢN TRỊ HTTPS",
                "LƯU MODEM VÀO DANH SÁCH", "NHẬP IP MODEM KHÁC", "ĐÓNG"};
        new AlertDialog.Builder(this).setTitle("QUẢN LÝ MODEM / ROUTER WI-FI")
            .setMessage(detail)
            .setItems(actions, (dialog, which) -> {
                if (gateway.isEmpty()) {
                    if (which == 0) promptRouterAddress();
                    return;
                }
                if (which == 0) openRouterAdmin("http://" + gateway);
                else if (which == 1) openRouterAdmin("https://" + gateway);
                else if (which == 2) saveRouterProfile(gateway);
                else if (which == 3) promptRouterAddress();
            })
            .setPositiveButton("KIỂM TRA LẠI", (d, w) -> showWifiRouterManager())
            .setNegativeButton("ĐÓNG", null).show();
        if (!gateway.isEmpty()) line("Đã đọc gateway Wi-Fi: " + gateway + ". Chưa xác thực vào modem.", GREEN);
    }

    private boolean isValidRouterHost(String rawHost) {
        if (rawHost == null) return false;
        String host = rawHost.trim();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (host.isEmpty() || host.length() > 253 || host.contains("/") || host.contains("@")) return false;
        if (host.indexOf(':') >= 0) {
            try {
                return InetAddress.getByName(host) instanceof java.net.Inet6Address;
            } catch (Exception invalidIpv6) {
                return false;
            }
        }
        return host.matches("(?i)[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?");
    }

    private String routerWebUrl(String scheme, String host) {
        return scheme + "://" + formatEndpointHost(host);
    }

    private void openRouterAdmin(String address) {
        try {
            Uri uri = Uri.parse(address);
            if ((!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null) {
                line("Địa chỉ quản trị modem không hợp lệ.", 0xffff8080);
                return;
            }
            Intent browser = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(browser);
            line("Đã mở địa chỉ quản trị modem. Hãy đăng nhập bằng tài khoản quản trị do bạn sở hữu.", GREEN);
        } catch (Exception error) {
            line("Không mở được trang quản trị: " + error.getMessage(), 0xffff8080);
        }
    }

    private void saveRouterProfile(String gateway) {
        DeviceHubProfile.save(this, "Modem Wi-Fi (" + gateway + ")",
            DeviceHubProfile.WEB, gateway, 80, "");
        line("Đã lưu địa chỉ modem. Profile chỉ lưu IP/cổng, không lưu mật khẩu.", GREEN);
    }

    private void promptRouterAddress() {
        EditText address = input("IP modem, ví dụ 192.168.1.1");
        address.setSingleLine(true);
        String gateway = getWifiGatewayAddress();
        if (!gateway.isEmpty()) address.setText(gateway);
        new AlertDialog.Builder(this).setTitle("ĐỊA CHỈ MODEM / ROUTER")
            .setMessage("Nhập IP hoặc hostname của modem bạn có quyền quản trị.")
            .setView(address)
            .setPositiveButton("MỞ HTTP", (d, w) -> {
                String host = address.getText().toString().trim();
                if (!isValidRouterHost(host)) {
                    line("Địa chỉ modem không hợp lệ. Có thể nhập IPv4, IPv6 hoặc hostname.", 0xffff8080);
                    return;
                }
                openRouterAdmin(routerWebUrl("http", host));
            })
            .setNeutralButton("MỞ HTTPS", (d, w) -> {
                String host = address.getText().toString().trim();
                if (!isValidRouterHost(host)) {
                    line("Địa chỉ modem không hợp lệ. Có thể nhập IPv4, IPv6 hoặc hostname.", 0xffff8080);
                    return;
                }
                openRouterAdmin(routerWebUrl("https", host));
            })
            .setNegativeButton("HỦY", null).show();
    }

    private void showUniversalDeviceHub() {
        java.util.ArrayList<DeviceHubProfile> profiles = DeviceHubProfile.load(this);
        String[] labels = new String[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            DeviceHubProfile p = profiles.get(i);
            String type = DeviceHubProfile.ANDROID_ADB.equals(p.platform) ? "Android / TV · ADB"
                : DeviceHubProfile.ROUTER_SSH.equals(p.platform) ? "Router / Modem · SSH"
                : DeviceHubProfile.SSH.equals(p.platform) ? "SSH · " + p.username
                : DeviceHubProfile.HOME_ASSISTANT.equals(p.platform) ? "Home Assistant"
                : "Web / Router / NAS";
            labels[i] = p.name + "  ·  " + type + "  ·  " + p.host;
        }
        String message = "Kết nối theo giao thức mà từng thiết bị hỗ trợ. Android/TV dùng ADB; Windows cần bật OpenSSH Server nếu dùng SSH; Linux/macOS dùng SSH; Home Assistant và router/NAS mở giao diện web. BO Kali AI không thể vượt qua yêu cầu cấp quyền hoặc khóa bảo mật.";
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle("TRUNG TÂM THIẾT BỊ ĐA NỀN TẢNG")
            .setMessage(message)
            .setPositiveButton("THÊM THIẾT BỊ", (d, w) -> showAddDeviceProfileDialog())
            .setNeutralButton("QUÉT MẠNG LAN", (d, w) -> scanUniversalNetworkServices())
            .setNegativeButton("ĐÓNG", null);
        if (!profiles.isEmpty()) {
            builder.setItems(labels, (d, which) -> showDeviceProfileActions(profiles.get(which)));
        }
        builder.show();
    }

    private static final class HubDiscoveredService {
        final String name;
        final String type;
        final String host;
        final int port;
        HubDiscoveredService(String name, String type, String host, int port) {
            this.name = name; this.type = type; this.host = host; this.port = port;
        }
        String label() { return name + " · " + host + ":" + port + " · " + type; }
    }

    private final java.util.ArrayList<NsdManager.DiscoveryListener> hubDiscoveryListeners = new java.util.ArrayList<>();
    private final java.util.LinkedHashMap<String, HubDiscoveredService> hubDiscoveredServices = new java.util.LinkedHashMap<>();
    private final java.util.ArrayDeque<NsdServiceInfo> hubResolveQueue = new java.util.ArrayDeque<>();
    private final java.util.HashSet<String> hubSeenServiceKeys = new java.util.HashSet<>();
    private boolean hubDiscoveryActive = false;
    private boolean hubResolvingService = false;
    private int hubResolveGeneration = 0;
    private int hubResolveTaskToken = 0;
    private Runnable hubResolveTimeout;
    private long hubResolveDrainDeadline = 0L;
    private Runnable hubScanTimeout;

    private void acquireHubMulticastLock() {
        try {
            if (hubMulticastLock != null && hubMulticastLock.isHeld()) return;
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) return;
            hubMulticastLock = wifi.createMulticastLock("BoKaliAI-mDNS");
            hubMulticastLock.setReferenceCounted(false);
            hubMulticastLock.acquire();
        } catch (Exception error) {
            line("Không lấy được quyền nhận multicast; kết quả mDNS có thể bị hạn chế: " + error.getMessage(), 0xffffca70);
            releaseHubMulticastLock();
        }
    }

    private void scanUniversalNetworkServices() {
        if (hubDiscoveryActive) {
            line("Đang quét dịch vụ mạng cục bộ. Vui lòng đợi.", 0xffffca70);
            return;
        }
        nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
        if (nsdManager == null) {
            line("Thiết bị không hỗ trợ Network Service Discovery.", 0xffff8080);
            return;
        }
        hubResolveGeneration++;
        if (hubResolveTimeout != null) mainHandler.removeCallbacks(hubResolveTimeout);
        hubResolveTimeout = null;
        hubResolveTaskToken++;
        hubResolveDrainDeadline = 0L;
        hubDiscoveredServices.clear();
        hubResolveQueue.clear();
        hubSeenServiceKeys.clear();
        hubResolvingService = false;
        hubDiscoveryListeners.clear();
        hubDiscoveryActive = true;
        acquireHubMulticastLock();
        line("Đang tìm dịch vụ SSH, HTTP/HTTPS và Home Assistant trong mạng Wi-Fi cục bộ...", GREEN);
        String[] serviceTypes = {"_ssh._tcp.", "_http._tcp.", "_https._tcp.", "_home-assistant._tcp."};
        for (String serviceType : serviceTypes) {
            NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
                @Override public void onDiscoveryStarted(String type) { }
                @Override public void onServiceFound(NsdServiceInfo service) {
                    if (service == null || service.getServiceName() == null || !hubDiscoveryActive) return;
                    String actualType = service.getServiceType();
                    if (actualType == null || !actualType.contains(serviceType.substring(0, serviceType.length() - 1))) return;
                    // Deduplicate repeated mDNS announcements and cap work per scan.
                    String serviceKey = actualType + "|" + service.getServiceName();
                    if (hubSeenServiceKeys.size() < 64 && hubSeenServiceKeys.add(serviceKey)) {
                        hubResolveQueue.offer(service);
                        resolveNextHubService();
                    }
                }
                @Override public void onServiceLost(NsdServiceInfo service) { }
                @Override public void onDiscoveryStopped(String type) { }
                @Override public void onStartDiscoveryFailed(String type, int errorCode) {
                    try { nsdManager.stopServiceDiscovery(this); } catch (Exception ignored) { }
                }
                @Override public void onStopDiscoveryFailed(String type, int errorCode) {
                    try { nsdManager.stopServiceDiscovery(this); } catch (Exception ignored) { }
                }
            };
            hubDiscoveryListeners.add(listener);
            try { nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener); }
            catch (Exception error) { line("Không quét được " + serviceType + ": " + error.getMessage(), 0xffffca70); }
        }
        hubScanTimeout = () -> finishUniversalNetworkScan();
        mainHandler.postDelayed(hubScanTimeout, 10000);
    }

    private void finishHubServiceResolve(int generation, int taskToken) {
        if (generation != hubResolveGeneration || taskToken != hubResolveTaskToken
            || !hubResolvingService) return;
        if (hubResolveTimeout != null) mainHandler.removeCallbacks(hubResolveTimeout);
        hubResolveTimeout = null;
        hubResolvingService = false;
        hubResolveTaskToken++;
        resolveNextHubService();
    }

    private void resolveNextHubService() {
        if (!hubDiscoveryActive || hubResolvingService || hubResolveQueue.isEmpty() || nsdManager == null) return;
        final int generation = hubResolveGeneration;
        final int taskToken = ++hubResolveTaskToken;
        NsdServiceInfo next = hubResolveQueue.poll();
        if (next == null) return;
        hubResolvingService = true;
        hubResolveTimeout = () -> {
            if (generation != hubResolveGeneration || taskToken != hubResolveTaskToken
                || !hubResolvingService) return;
            hubResolveTimeout = null;
            hubResolvingService = false;
            hubResolveTaskToken++;
            line("Hết thời gian phân giải dịch vụ mạng "
                + next.getServiceName() + "; bỏ qua mục này và tiếp tục quét.", 0xffffca70);
            resolveNextHubService();
        };
        mainHandler.postDelayed(hubResolveTimeout, 5000L);
        try {
            nsdManager.resolveService(next, new NsdManager.ResolveListener() {
                @Override public void onResolveFailed(NsdServiceInfo failed, int errorCode) {
                    if (generation != hubResolveGeneration || taskToken != hubResolveTaskToken
                        || !hubResolvingService) return;
                    finishHubServiceResolve(generation, taskToken);
                }
                @Override public void onServiceResolved(NsdServiceInfo resolved) {
                    if (generation != hubResolveGeneration || taskToken != hubResolveTaskToken
                        || !hubResolvingService) return;
                    if (hubDiscoveryActive && resolved != null) {
                        InetAddress address = resolved.getHost();
                        if (address != null && resolved.getPort() > 0 && hubDiscoveredServices.size() < 64) {
                            String host = address.getHostAddress();
                            String type = resolved.getServiceType();
                            if (type == null) type = "";
                            HubDiscoveredService found = new HubDiscoveredService(
                                resolved.getServiceName(), type, host, resolved.getPort());
                            String key = type + "|" + host + "|" + resolved.getPort();
                            hubDiscoveredServices.put(key, found);
                        }
                    }
                    finishHubServiceResolve(generation, taskToken);
                }
            });
        } catch (Exception error) {
            if (generation != hubResolveGeneration || taskToken != hubResolveTaskToken) return;
            if (hubResolveTimeout != null) mainHandler.removeCallbacks(hubResolveTimeout);
            hubResolveTimeout = null;
            hubResolvingService = false;
            hubResolveTaskToken++;
            line("Không phân giải được dịch vụ mạng " + next.getServiceName()
                + ": " + error.getMessage(), 0xffffca70);
            // Avoid a tight retry loop if the platform rejects resolveService synchronously.
            mainHandler.postDelayed(() -> {
                if (generation == hubResolveGeneration) resolveNextHubService();
            }, 250L);
        }
    }

    private void stopHubDiscovery() {
        if (hubScanTimeout != null) {
            mainHandler.removeCallbacks(hubScanTimeout);
            hubScanTimeout = null;
        }
        if (nsdManager != null) {
            for (NsdManager.DiscoveryListener listener : new java.util.ArrayList<>(hubDiscoveryListeners)) {
                try { nsdManager.stopServiceDiscovery(listener); } catch (Exception ignored) { }
            }
        }
        hubDiscoveryListeners.clear();
        hubDiscoveryActive = false;
        hubResolveGeneration++;
        if (hubResolveTimeout != null) mainHandler.removeCallbacks(hubResolveTimeout);
        hubResolveTimeout = null;
        hubResolveTaskToken++;
        hubResolveQueue.clear();
        hubSeenServiceKeys.clear();
        hubResolvingService = false;
        releaseHubMulticastLock();
    }

    private void releaseHubMulticastLock() {
        if (hubDiscoveryActive || adbScanActive || adbPairingScanActive
            || adbResolveInFlight || !adbResolveQueue.isEmpty()) return;
        WifiManager.MulticastLock lock = hubMulticastLock;
        hubMulticastLock = null;
        if (lock != null) {
            try { if (lock.isHeld()) lock.release(); } catch (RuntimeException ignored) { }
        }
    }

    private void finishUniversalNetworkScan() {
        // Start the drain window when discovery itself ends, not when the scan begins.
        if (hubDiscoveryActive && (!hubResolveQueue.isEmpty() || hubResolvingService)) {
            long now = android.os.SystemClock.elapsedRealtime();
            if (hubResolveDrainDeadline == 0L) hubResolveDrainDeadline = now + 8000L;
            if (now < hubResolveDrainDeadline) {
                hubScanTimeout = this::finishUniversalNetworkScan;
                mainHandler.postDelayed(hubScanTimeout, 750L);
                return;
            }
        }
        stopHubDiscovery();
        if (hubDiscoveredServices.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Không tìm thấy dịch vụ được quảng bá")
                .setMessage("Không có SSH/HTTP/Home Assistant nào được phát hiện qua mDNS trong mạng hiện tại. Một số thiết bị không quảng bá dịch vụ hoặc chặn multicast. Bạn vẫn có thể thêm IP thủ công.")
                .setPositiveButton("THÊM THỦ CÔNG", (d, w) -> showAddDeviceProfileDialog())
                .setNegativeButton("ĐÓNG", null).show();
            return;
        }
        java.util.ArrayList<HubDiscoveredService> found = new java.util.ArrayList<>(hubDiscoveredServices.values());
        String[] labels = new String[found.size()];
        for (int i = 0; i < found.size(); i++) labels[i] = found.get(i).label();
        new AlertDialog.Builder(this).setTitle("DỊCH VỤ ĐÃ TÌM THẤY · " + found.size())
            .setItems(labels, (d, which) -> showDiscoveredServiceActions(found.get(which)))
            .setPositiveButton("QUÉT LẠI", (d, w) -> scanUniversalNetworkServices())
            .setNegativeButton("ĐÓNG", null).show();
        line("Quét mạng hoàn tất: tìm thấy " + found.size() + " dịch vụ mDNS. Đây là phát hiện dịch vụ, chưa phải quyền điều khiển.", GREEN);
    }

    private void showDiscoveredServiceActions(HubDiscoveredService service) {
        String[] actions = {"LƯU THÀNH THIẾT BỊ", "MỞ GIAO DIỆN WEB", "HỦY"};
        new AlertDialog.Builder(this).setTitle(service.label())
            .setItems(actions, (d, which) -> {
                if (which == 0) {
                    boolean ssh = service.type.startsWith("_ssh");
                    boolean ha = service.type.startsWith("_home-assistant");
                    EditText displayName = input("Tên thiết bị");
                    displayName.setText(service.name);
                    EditText user = input("Tài khoản SSH");
                    LinearLayout form = new LinearLayout(this);
                    form.setOrientation(1);
                    form.setPadding(dp(18), dp(8), dp(18), 0);
                    form.addView(displayName);
                    if (ssh) form.addView(user);
                    new AlertDialog.Builder(this).setTitle("LƯU PROFILE")
                        .setMessage(service.host + ":" + service.port)
                        .setView(form)
                        .setPositiveButton("LƯU", (x, y) -> {
                            String n = displayName.getText().toString().trim();
                            String u = user.getText().toString().trim();
                            if (n.isEmpty() || (ssh && !u.matches("[A-Za-z0-9._-]{1,64}"))) {
                                line("Tên hoặc tài khoản SSH không hợp lệ.", 0xffff8080);
                                return;
                            }
                            String platform = ssh ? DeviceHubProfile.SSH
                                : ha ? DeviceHubProfile.HOME_ASSISTANT : DeviceHubProfile.WEB;
                            String savedHost = service.host;
                            if (!ssh) {
                                String scheme = service.type.startsWith("_https") ? "https://" : "http://";
                                savedHost = scheme + formatEndpointHost(service.host) + ":" + service.port;
                            }
                            DeviceHubProfile.save(this, n, platform, savedHost, service.port, u);
                            line("Đã lưu dịch vụ mDNS " + n + ". Cần xác thực trước khi sử dụng.", GREEN);
                        }).setNegativeButton("HỦY", null).show();
                } else if (which == 1) {
                    if (service.type.startsWith("_http") || service.type.startsWith("_https")
                        || service.type.startsWith("_home-assistant")) {
                        String scheme = service.type.startsWith("_https") ? "https://" : "http://";
                        // DNS-SD may return an IPv6 literal; bracket it before appending the port.
                        String endpointHost = formatEndpointHost(service.host);
                        try { startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse(scheme + endpointHost + ":" + service.port))); }
                        catch (Exception error) { line("Không mở được giao diện web.", 0xffff8080); }
                    } else {
                        line("Dịch vụ SSH không có giao diện web. Hãy lưu profile và kết nối bằng SSH.", 0xffffca70);
                    }
                }
            }).show();
    }

    private void showAddDeviceProfileDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(1);
        form.setPadding(dp(18), dp(8), dp(18), 0);

        String[] platformLabels = {
            "Android / Android TV (ADB)",
            "Windows (SSH / OpenSSH Server)",
            "Linux / Kali / macOS (SSH)",
            "Home Assistant",
            "Router / NAS / giao diện Web",
            "Modem / Router (SSH terminal)"
        };
        String[] platformValues = {
            DeviceHubProfile.ANDROID_ADB,
            DeviceHubProfile.SSH,
            DeviceHubProfile.SSH,
            DeviceHubProfile.HOME_ASSISTANT,
            DeviceHubProfile.WEB,
            DeviceHubProfile.ROUTER_SSH
        };
        Spinner platform = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, platformLabels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        platform.setAdapter(adapter);
        form.addView(platform, new LinearLayout.LayoutParams(-1, dp(48)));

        EditText name = input("Tên dễ nhớ, ví dụ: PC phòng làm việc");
        form.addView(name, new LinearLayout.LayoutParams(-1, dp(48)));
        EditText host = input("IP / tên miền / URL");
        host.setSingleLine(true);
        form.addView(host, new LinearLayout.LayoutParams(-1, dp(48)));
        EditText port = input("Cổng: ADB 5555 · SSH 22 · Home Assistant 8123");
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        port.setText("22");
        form.addView(port, new LinearLayout.LayoutParams(-1, dp(48)));
        EditText username = input("Tên đăng nhập SSH (không lưu mật khẩu)");
        username.setSingleLine(true);
        form.addView(username, new LinearLayout.LayoutParams(-1, dp(48)));

        platform.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String defaultPort = position == 0 ? "5555" : position == 3 ? "8123" : position == 4 ? "80" : "22";
                port.setText(defaultPort);
                username.setVisibility((position == 1 || position == 2 || position == 5) ? View.VISIBLE : View.GONE);
                host.setHint(position == 4 ? "http://192.168.1.1 hoặc https://..." : "IP / tên miền");
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        new AlertDialog.Builder(this).setTitle("THÊM THIẾT BỊ / DỊCH VỤ")
            .setMessage("Chỉ lưu thông tin định tuyến, không lưu mật khẩu, mã ghép đôi hay token.")
            .setView(form)
            .setPositiveButton("LƯU", (d, w) -> {
                int selected = platform.getSelectedItemPosition();
                String n = name.getText().toString().trim();
                String h = host.getText().toString().trim();
                String u = username.getText().toString().trim();
                int p;
                try { p = Integer.parseInt(port.getText().toString().trim()); }
                catch (Exception error) { p = -1; }
                String proto = platformValues[selected];
                boolean webEndpoint = DeviceHubProfile.WEB.equals(proto)
                    || DeviceHubProfile.HOME_ASSISTANT.equals(proto);
                boolean validHost = h.matches("[A-Za-z0-9._:%\\[\\]-]{1,253}");
                if (webEndpoint && (h.startsWith("http://") || h.startsWith("https://"))) {
                    try {
                        Uri webUri = Uri.parse(h);
                        String scheme = webUri.getScheme();
                        validHost = ("http".equals(scheme) || "https".equals(scheme))
                            && webUri.getHost() != null && !webUri.getHost().isEmpty()
                            && webUri.getUserInfo() == null
                            && webUri.getPort() <= 65535
                            && webUri.getFragment() == null;
                    } catch (Exception invalidUrl) {
                        validHost = false;
                    }
                }
                if (n.isEmpty() || !validHost || p < 1 || p > 65535
                    || ((DeviceHubProfile.SSH.equals(proto) || DeviceHubProfile.ROUTER_SSH.equals(proto)) && !u.matches("[A-Za-z0-9._-]{1,64}"))) {
                    line("Thông tin thiết bị chưa hợp lệ. Kiểm tra tên, địa chỉ, cổng và tài khoản SSH.", 0xffff8080);
                    return;
                }
                DeviceHubProfile.save(this, n, proto, h, p, u);
                line("Đã lưu profile thiết bị: " + n + ". Chưa có kết nối nào được mở.", GREEN);
                showUniversalDeviceHub();
            })
            .setNegativeButton("HỦY", null).show();
    }

    private void showDeviceProfileActions(DeviceHubProfile profile) {
        boolean isHomeAssistant = DeviceHubProfile.HOME_ASSISTANT.equals(profile.platform);
        String[] actions = isHomeAssistant
            ? new String[]{"MỞ GIAO DIỆN", "LƯU / THAY TOKEN API", "KIỂM TRA TOKEN (HTTPS)", "ĐỌC TRẠNG THÁI ENTITY", "ĐIỀU KHIỂN ĐÈN / CÔNG TẮC", "XÓA TOKEN API", "XÓA PROFILE", "HỦY"}
            : new String[]{"KẾT NỐI", "XÓA PROFILE", "HỦY"};
        new AlertDialog.Builder(this).setTitle(profile.name)
            .setItems(actions, (d, which) -> {
                if (isHomeAssistant && which == 1) {
                    EditText tokenInput = input("Long-Lived Access Token");
                    tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    new AlertDialog.Builder(this).setTitle("TOKEN HOME ASSISTANT")
                        .setMessage("Token được mã hóa cục bộ bằng AES-GCM và khóa Android Keystore. Không chia sẻ token hoặc đưa token lên GitHub.")
                        .setView(tokenInput)
                        .setPositiveButton("LƯU AN TOÀN", (x, y) -> {
                            String token = tokenInput.getText().toString().trim();
                            if (token.length() < 20 || token.length() > 8192 || token.indexOf('\\n') >= 0) {
                                line("Token không hợp lệ hoặc vượt giới hạn.", 0xffff8080);
                                return;
                            }
                            try {
                                HomeAssistantTokenStore.save(this, profile.id, token);
                                line("Đã lưu token mã hóa cho " + profile.name + ".", GREEN);
                            } catch (Exception error) {
                                line("Không lưu được token: " + error.getMessage(), 0xffff8080);
                            }
                        }).setNegativeButton("HỦY", null).show();
                } else if (isHomeAssistant && which == 2) {
                    testHomeAssistantApi(profile);
                } else if (isHomeAssistant && which == 3) {
                    loadHomeAssistantEntities(profile);
                } else if (isHomeAssistant && which == 4) {
                    showHomeAssistantControlDialog(profile);
                } else if (isHomeAssistant && which == 5) {
                    HomeAssistantTokenStore.delete(this, profile.id);
                    line("Đã xóa token API của " + profile.name + ".", GREEN);
                } else if ((isHomeAssistant && which == 6) || (!isHomeAssistant && which == 1)) {
                    new AlertDialog.Builder(this)
                        .setTitle("Xóa profile?")
                        .setMessage("Chỉ xóa thông tin kết nối đã lưu trong BO Kali AI. Không xóa dữ liệu trên thiết bị.")
                        .setPositiveButton("XÓA", (x, y) -> {
                            if (isHomeAssistant) HomeAssistantTokenStore.delete(this, profile.id);
                            DeviceHubProfile.delete(this, profile.id);
                            line("Đã xóa profile " + profile.name + ".", GREEN);
                            showUniversalDeviceHub();
                        }).setNegativeButton("HỦY", null).show();
                } else if (which == 0) {
                    connectDeviceProfile(profile);
                }
            }).show();
    }

    private void loadHomeAssistantEntities(DeviceHubProfile profile) {
        if (!HomeAssistantTokenStore.has(this, profile.id)) {
            line("Profile chưa có token API. Hãy lưu token trước.", 0xffffca70);
            return;
        }
        final String apiUrl;
        try {
            String endpoint = profile.host == null ? "" : profile.host.trim();
            Uri parsed = Uri.parse(endpoint.contains("://")
                ? endpoint : "https://" + formatEndpointHost(endpoint) + ":" + profile.port);
            if (!"https".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null
                    || parsed.getUserInfo() != null || parsed.getPort() > 65535
                    || parsed.getPort() == 0 || parsed.getFragment() != null) {
                line("Đọc entity chỉ cho phép HTTPS hợp lệ để bảo vệ token.", 0xffff8080);
                return;
            }
            int port = parsed.getPort() < 0 ? 443 : parsed.getPort();
            apiUrl = new URL("https", parsed.getHost(), port, "/api/states").toString();
        } catch (Exception invalid) {
            line("Địa chỉ Home Assistant không hợp lệ.", 0xffff8080);
            return;
        }
        line("Đang đọc danh sách entity qua HTTPS…", WHITE);
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String token = HomeAssistantTokenStore.load(this, profile.id);
                if (token == null || token.isEmpty()) throw new IllegalStateException("Không tìm thấy token.");
                connection = (HttpURLConnection) new URL(apiUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setRequestProperty("Accept", "application/json");
                int status = connection.getResponseCode();
                if (status != 200) {
                    String message = status == 401 || status == 403
                        ? "Token không được Home Assistant chấp nhận (HTTP " + status + ")."
                        : "Home Assistant trả HTTP " + status + ".";
                    runOnUiThread(() -> line(message, 0xffffca70));
                    return;
                }
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder body = new StringBuilder();
                String row;
                while ((row = reader.readLine()) != null) {
                    body.append(row);
                    if (body.length() > 4 * 1024 * 1024)
                        throw new IllegalStateException("Phản hồi entity vượt giới hạn 4 MB.");
                }
                reader.close();
                JSONArray entities = new JSONArray(body.toString());
                final StringBuilder report = new StringBuilder();
                int count = Math.min(entities.length(), 100);
                for (int i = 0; i < count; i++) {
                    JSONObject entity = entities.optJSONObject(i);
                    if (entity == null) continue;
                    String id = entity.optString("entity_id", "unknown");
                    String state = entity.optString("state", "unknown");
                    JSONObject attrs = entity.optJSONObject("attributes");
                    String friendly = attrs == null ? "" : attrs.optString("friendly_name", "");
                    report.append(id).append(" · ").append(state);
                    if (!friendly.isEmpty() && !friendly.equals(id))
                        report.append(" · ").append(friendly);
                    report.append("\n");
                }
                final String result = report.length() == 0
                    ? "Không tìm thấy entity." : report.toString();
                final int total = entities.length();
                runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle("HOME ASSISTANT · ENTITIES (" + total + ")")
                    .setMessage(result + (total > count ? "\n… và " + (total - count) + " entity khác." : ""))
                    .setPositiveButton("ĐÓNG", null)
                    .setNeutralButton("SAO CHÉP", (dialog, which) ->
                        copyText(result, "Đã sao chép danh sách entity hiển thị."))
                    .show());
            } catch (Exception error) {
                String message = error.getMessage() == null ? "Lỗi đọc entity." : error.getMessage();
                runOnUiThread(() -> line("Không đọc được entity Home Assistant: " + message, 0xffff8080));
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "bo-ha-entities").start();
    }

    private void showHomeAssistantControlDialog(DeviceHubProfile profile) {
        if (!HomeAssistantTokenStore.has(this, profile.id)) {
            line("Profile chưa có token API. Hãy lưu token trước.", 0xffffca70);
            return;
        }
        final String apiUrl;
        try {
            String endpoint = profile.host == null ? "" : profile.host.trim();
            Uri parsed = Uri.parse(endpoint.contains("://") ? endpoint : "https://" + formatEndpointHost(endpoint) + ":" + profile.port);
            if (!"https".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null || parsed.getUserInfo() != null
                    || parsed.getPort() > 65535 || parsed.getPort() == 0 || parsed.getFragment() != null) {
                line("Tải entity điều khiển chỉ cho phép HTTPS hợp lệ.", 0xffff8080);
                return;
            }
            int port = parsed.getPort() < 0 ? 443 : parsed.getPort();
            apiUrl = new URL("https", parsed.getHost(), port, "/api/states").toString();
        } catch (Exception invalid) {
            line("Địa chỉ Home Assistant không hợp lệ.", 0xffff8080);
            return;
        }
        line("Đang tải entity có thể điều khiển từ Home Assistant…", WHITE);
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String token = HomeAssistantTokenStore.load(this, profile.id);
                if (token == null || token.isEmpty()) throw new IllegalStateException("Không tìm thấy token.");
                connection = (HttpURLConnection) new URL(apiUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setRequestProperty("Accept", "application/json");
                int status = connection.getResponseCode();
                if (status != 200) {
                    String message = status == 401 || status == 403
                        ? "Home Assistant từ chối token/quyền (HTTP " + status + ")."
                        : "Không tải được entity (HTTP " + status + ").";
                    runOnUiThread(() -> line(message, 0xffff8080));
                    return;
                }
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String row;
                    while ((row = reader.readLine()) != null) {
                        body.append(row);
                        if (body.length() > 4 * 1024 * 1024) throw new IllegalStateException("Danh sách entity vượt giới hạn 4 MB.");
                    }
                }
                JSONArray entities = new JSONArray(body.toString());
                java.util.ArrayList<String> ids = new java.util.ArrayList<>();
                java.util.ArrayList<String> labels = new java.util.ArrayList<>();
                for (int i = 0; i < entities.length() && ids.size() < 200; i++) {
                    JSONObject entity = entities.optJSONObject(i);
                    if (entity == null) continue;
                    String id = entity.optString("entity_id", "");
                    if (!id.matches("^(light|switch|input_boolean|fan)\\.[a-z0-9_]+$")) continue;
                    JSONObject attrs = entity.optJSONObject("attributes");
                    String friendly = attrs == null ? "" : attrs.optString("friendly_name", "");
                    String state = entity.optString("state", "unknown");
                    ids.add(id);
                    labels.add((friendly.isEmpty() ? id : friendly) + "  ·  " + state + "  [" + id + "]");
                }
                final String[] labelArray = labels.toArray(new String[0]);
                final String[] idArray = ids.toArray(new String[0]);
                runOnUiThread(() -> {
                    if (idArray.length == 0) {
                        line("Không tìm thấy entity light/switch/input_boolean/fan để điều khiển.", 0xffffca70);
                        return;
                    }
                    new AlertDialog.Builder(this)
                        .setTitle("CHỌN THIẾT BỊ (" + idArray.length + ")")
                        .setItems(labelArray, (dialog, which) -> showHomeAssistantEntityActions(profile, idArray[which]))
                        .setNegativeButton("ĐÓNG", null)
                        .show();
                });
            } catch (Exception error) {
                String message = error.getMessage() == null ? "Lỗi tải entity." : error.getMessage();
                runOnUiThread(() -> line("Không tải được entity điều khiển: " + message, 0xffff8080));
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "bo-ha-control-entities").start();
    }

    private void showHomeAssistantEntityActions(DeviceHubProfile profile, String entityId) {
        String[] choices = {"BẬT THIẾT BỊ", "TẮT THIẾT BỊ"};
        new AlertDialog.Builder(this).setTitle(entityId)
            .setItems(choices, (dialog, action) -> {
                String service = action == 0 ? "turn_on" : "turn_off";
                String verb = action == 0 ? "BẬT" : "TẮT";
                new AlertDialog.Builder(this).setTitle("XÁC NHẬN LỆNH")
                    .setMessage(verb + " " + entityId + " trên " + profile.name + "?")
                    .setPositiveButton("XÁC NHẬN VÀ GỬI", (confirm, ignored) -> callHomeAssistantService(profile, entityId, service))
                    .setNegativeButton("KHÔNG", null)
                    .show();
            })
            .setNegativeButton("HỦY", null)
            .show();
    }

    private void callHomeAssistantService(DeviceHubProfile profile, String entityId, String service) {
        if (!HomeAssistantTokenStore.has(this, profile.id)) { line("Không tìm thấy token API; chưa gửi lệnh.", 0xffffca70); return; }
        if (entityId == null || !entityId.matches("^(light|switch|input_boolean|fan)\\.[a-z0-9_]+$")
                || !("turn_on".equals(service) || "turn_off".equals(service))) {
            line("Lệnh điều khiển không hợp lệ; đã chặn.", 0xffff8080); return;
        }
        final String apiUrl;
        try {
            String endpoint = profile.host == null ? "" : profile.host.trim();
            Uri parsed = Uri.parse(endpoint.contains("://") ? endpoint : "https://" + formatEndpointHost(endpoint) + ":" + profile.port);
            if (!"https".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null || parsed.getUserInfo() != null
                    || parsed.getPort() > 65535 || parsed.getPort() == 0 || parsed.getFragment() != null) {
                line("Điều khiển chỉ cho phép HTTPS hợp lệ để bảo vệ token.", 0xffff8080); return;
            }
            int port = parsed.getPort() < 0 ? 443 : parsed.getPort();
            String domain = entityId.substring(0, entityId.indexOf(46));
            apiUrl = new URL("https", parsed.getHost(), port, "/api/services/" + domain + "/" + service).toString();
        } catch (Exception invalid) { line("Địa chỉ Home Assistant không hợp lệ; chưa gửi lệnh.", 0xffff8080); return; }
        line("Đang gửi lệnh HTTPS tới Home Assistant: " + entityId + "…", WHITE);
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String token = HomeAssistantTokenStore.load(this, profile.id);
                if (token == null || token.isEmpty()) throw new IllegalStateException("Không tìm thấy token.");
                JSONObject payload = new JSONObject(); payload.put("entity_id", entityId);
                byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                connection = (HttpURLConnection) new URL(apiUrl).openConnection();
                connection.setRequestMethod("POST"); connection.setConnectTimeout(10000); connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(false); connection.setDoOutput(true);
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); output.flush(); }
                int status = connection.getResponseCode();
                if (status >= 200 && status < 300) {
                    StringBuilder responseBody = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        String row;
                        while ((row = reader.readLine()) != null) {
                            responseBody.append(row);
                            if (responseBody.length() > 1024 * 1024)
                                throw new IllegalStateException("Phản hồi điều khiển vượt giới hạn 1 MB.");
                        }
                    }
                    String verifiedState = null;
                    if (responseBody.length() > 0) {
                        try {
                            JSONArray changed = new JSONArray(responseBody.toString());
                            for (int i = 0; i < changed.length(); i++) {
                                JSONObject stateObject = changed.optJSONObject(i);
                                if (stateObject != null && entityId.equals(stateObject.optString("entity_id"))) {
                                    verifiedState = stateObject.optString("state", null);
                                    break;
                                }
                            }
                        } catch (Exception ignored) {
                            // Some integrations return an empty or non-standard success body.
                        }
                    }
                    final String stateResult = verifiedState;
                    final String successMessage = stateResult == null || stateResult.isEmpty()
                        ? "Home Assistant đã chấp nhận lệnh " + service + " cho " + entityId
                            + " (HTTP " + status + "); API không trả trạng thái để xác minh."
                        : "Home Assistant xác nhận " + entityId + " hiện ở trạng thái "
                            + stateResult + " (HTTP " + status + ").";
                    runOnUiThread(() -> line(successMessage, GREEN));
                } else {
                    String message = status == 401 || status == 403 ? "Home Assistant từ chối token/quyền (HTTP " + status + ")." : "Home Assistant không thực hiện lệnh (HTTP " + status + ").";
                    runOnUiThread(() -> line(message, 0xffff8080));
                }
            } catch (Exception error) {
                runOnUiThread(() -> line("Gửi lệnh Home Assistant thất bại: " + (error.getMessage() == null ? "lỗi kết nối" : error.getMessage()), 0xffff8080));
            } finally { if (connection != null) connection.disconnect(); }
        }, "bo-ha-service-control").start();
    }

    private void testHomeAssistantApi(DeviceHubProfile profile) {
        if (!HomeAssistantTokenStore.has(this, profile.id)) {
            line("Profile chưa có token API. Hãy lưu token trước.", 0xffffca70);
            return;
        }
        final String endpoint = profile.host == null ? "" : profile.host.trim();
        final String apiUrl;
        try {
            Uri parsed = Uri.parse(endpoint.contains("://")
                ? endpoint : "https://" + formatEndpointHost(endpoint) + ":" + profile.port);
            if (!"https".equalsIgnoreCase(parsed.getScheme())
                    || parsed.getHost() == null || parsed.getUserInfo() != null
                    || parsed.getPort() > 65535 || parsed.getPort() == 0
                    || parsed.getFragment() != null) {
                line("Kiểm tra API token chỉ cho phép HTTPS hợp lệ để tránh gửi token qua mạng không mã hóa.", 0xffff8080);
                return;
            }
            int port = parsed.getPort() < 0 ? 443 : parsed.getPort();
            apiUrl = new java.net.URL("https", parsed.getHost(), port, "/api/").toString();
        } catch (Exception invalid) {
            line("Địa chỉ Home Assistant không hợp lệ.", 0xffff8080);
            return;
        }
        line("Đang kiểm tra kết nối HTTPS tới Home Assistant…", WHITE);
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String token = HomeAssistantTokenStore.load(this, profile.id);
                if (token == null || token.isEmpty()) throw new IllegalStateException("Không tìm thấy token.");
                connection = (HttpURLConnection) new URL(apiUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setRequestProperty("Accept", "application/json");
                int status = connection.getResponseCode();
                if (status == 200) {
                    runOnUiThread(() -> line("Home Assistant xác thực token thành công (HTTP 200).", GREEN));
                } else if (status == 401 || status == 403) {
                    runOnUiThread(() -> line("Home Assistant từ chối token (HTTP " + status + "). Kiểm tra hoặc thay token.", 0xffff8080));
                } else {
                    runOnUiThread(() -> line("Home Assistant trả về HTTP " + status + ". Kiểm tra URL và quyền API.", 0xffffca70));
                }
            } catch (Exception error) {
                String message = error.getMessage() == null ? "Lỗi kết nối" : error.getMessage();
                runOnUiThread(() -> line("Không kiểm tra được Home Assistant: " + message, 0xffff8080));
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "bo-ha-token-test").start();
    }

    private String formatEndpointHost(String host) {
        String value = host == null ? "" : host.trim();
        if (value.startsWith("[") && value.endsWith("]")) return value;
        // IPv6 literals must be bracketed when followed by a TCP port.
        if (value.indexOf(':') >= 0) return "[" + value + "]";
        return value;
    }

    private void connectDeviceProfile(DeviceHubProfile profile) {
        if (DeviceHubProfile.ANDROID_ADB.equals(profile.platform)) {
            String endpoint = formatEndpointHost(profile.host) + ":" + profile.port;
            new AlertDialog.Builder(this).setTitle("Kết nối Android / Android TV")
                .setMessage("ADB sẽ kết nối tới " + endpoint + ". Thiết bị phải bật Wireless debugging/ADB và chấp nhận khóa RSA hoặc đã ghép đôi. Không thể kết nối trái phép.")
                .setPositiveButton("KẾT NỐI", (d, w) -> runTermuxCommand(
                    "pkg install -y android-tools && adb connect " + shellQuote(endpoint) + " && adb devices -l"))
                .setNegativeButton("HỦY", null).show();
            return;
        }
        if ((DeviceHubProfile.SSH.equals(profile.platform) || DeviceHubProfile.ROUTER_SSH.equals(profile.platform))) {
            String sshHost = formatEndpointHost(profile.host);
            // Force a PTY so interactive SSH authentication and router CLIs work in Termux.
            String command = "pkg install -y openssh && ssh -tt -p " + profile.port
                + " -o ConnectTimeout=10 -o ServerAliveInterval=30 -o ServerAliveCountMax=3 "
                + shellQuote(profile.username + "@" + sshHost);
            new AlertDialog.Builder(this).setTitle((DeviceHubProfile.ROUTER_SSH.equals(profile.platform) ? "SSH MODEM / ROUTER · " : "Kết nối SSH · ") + profile.name)
                .setMessage("Mở phiên SSH trong Termux. Router phải bật SSH Server và cho phép tài khoản này đăng nhập. Lần đầu xác nhận host key; nhập mật khẩu trực tiếp trong Termux. BO Kali AI không lưu mật khẩu. Quyền chạy lệnh phụ thuộc tài khoản và firmware router.")
                .setPositiveButton("MỞ PHIÊN SSH", (d, w) -> runTermuxInteractiveCommand(command))
                .setNegativeButton("HỦY", null).show();
            return;
        }
        String target = profile.host;
        if (DeviceHubProfile.HOME_ASSISTANT.equals(profile.platform) || DeviceHubProfile.WEB.equals(profile.platform)) {
            if (!target.startsWith("http://") && !target.startsWith("https://")) {
                target = "http://" + formatEndpointHost(target) + ":" + profile.port;
            }
        }
        try {
            Uri uri = Uri.parse(target);
            String scheme = uri.getScheme();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                line("Chỉ mở giao diện web qua HTTP/HTTPS.", 0xffff8080);
                return;
            }
            Intent browser = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(browser);
            line("Đã yêu cầu mở giao diện " + profile.name + ". Đăng nhập theo quyền của dịch vụ.", GREEN);
        } catch (Exception error) {
            line("Không mở được giao diện web: " + error.getMessage(), 0xffff8080);
        }
    }

    private void runTermuxInteractiveCommand(String command) {
        try {
            Intent intent = new Intent("com.termux.RUN_COMMAND");
            intent.setClassName("com.termux", "com.termux.app.RunCommandService");
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", command});
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
            intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
            startService(intent);
            line("Đã mở yêu cầu phiên tương tác trong Termux.", GREEN);
        } catch (SecurityException error) {
            line("Termux chưa cấp quyền RUN_COMMAND hoặc chưa bật allow-external-apps.", 0xffffca70);
            openTermux();
        } catch (Exception error) {
            line("Không mở được phiên Termux: " + error.getMessage(), 0xffff8080);
        }
    }

    private void showUniversalDualWorkspace() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(8), dp(8), dp(8), dp(8));
        page.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        Button back = button("QUAY LẠI");
        back.setOnClickListener(v -> setContentView(root));
        top.addView(back, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button add = button("THÊM THIẾT BỊ");
        add.setOnClickListener(v -> showAddDeviceProfileDialog());
        top.addView(add, new LinearLayout.LayoutParams(0, dp(44), 1));
        page.addView(top);

        LinearLayout split = new LinearLayout(this);
        split.setOrientation(LinearLayout.HORIZONTAL);
        split.setWeightSum(2f);

        LinearLayout codePane = new LinearLayout(this);
        codePane.setOrientation(LinearLayout.VERTICAL);
        codePane.setPadding(dp(7), dp(8), dp(7), dp(8));
        codePane.setBackgroundColor(PANEL);
        codePane.addView(text("MÀN HÌNH CHÍNH · CODE / KALI / AI", GREEN, 13, true));
        codePane.addView(text("Khu vực làm việc độc lập, không cần thiết bị ngoài kết nối.", WHITE, 11, false));
        Button terminal = button("MỞ CODE / TERMINAL");
        terminal.setOnClickListener(v -> setContentView(root));
        codePane.addView(terminal, new LinearLayout.LayoutParams(-1, dp(48)));
        Button kali = button("MỞ KALI SHELL");
        kali.setOnClickListener(v -> kaliShellMenu());
        codePane.addView(kali, new LinearLayout.LayoutParams(-1, dp(44)));
        Button ai = button("HỎI AI");
        ai.setOnClickListener(v -> { setContentView(root); questionInput.requestFocus(); });
        codePane.addView(ai, new LinearLayout.LayoutParams(-1, dp(44)));
        split.addView(codePane, new LinearLayout.LayoutParams(0, -1, 1f));

        LinearLayout devicePane = new LinearLayout(this);
        devicePane.setOrientation(LinearLayout.VERTICAL);
        devicePane.setPadding(dp(7), dp(8), dp(7), dp(8));
        devicePane.setBackgroundColor(0xff101f2e);
        devicePane.addView(text("MÀN HÌNH PHỤ · THIẾT BỊ", 0xff79c9ff, 13, true));
        devicePane.addView(text("Chưa có Android ADB đang trực tuyến. Chọn profile để mở giao thức tương ứng.", WHITE, 11, false));
        Button scanLan = button("QUÉT MẠNG LAN");
        scanLan.setOnClickListener(v -> scanUniversalNetworkServices());
        devicePane.addView(scanLan, new LinearLayout.LayoutParams(-1, dp(42)));
        Button scanAdb = button("QUÉT ANDROID / TV");
        scanAdb.setOnClickListener(v -> runTermuxCommand("pkg install -y android-tools && adb devices -l"));
        devicePane.addView(scanAdb, new LinearLayout.LayoutParams(-1, dp(42)));

        ScrollView profileScroll = new ScrollView(this);
        LinearLayout profileList = new LinearLayout(this);
        profileList.setOrientation(LinearLayout.VERTICAL);
        java.util.ArrayList<DeviceHubProfile> profiles = DeviceHubProfile.load(this);
        if (profiles.isEmpty()) {
            profileList.addView(text("Chưa có profile. Thêm Android, PC, máy chủ, Home Assistant, router hoặc NAS.", 0xffb4c7d8, 11, false));
        } else {
            for (DeviceHubProfile profile : profiles) {
                Button item = button(profile.name + "\\n" + profile.host + ":" + profile.port);
                item.setOnClickListener(v -> showDeviceProfileActions(profile));
                profileList.addView(item, new LinearLayout.LayoutParams(-1, dp(58)));
            }
        }
        profileScroll.addView(profileList);
        devicePane.addView(profileScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        Button hub = button("QUẢN LÝ PROFILE");
        hub.setOnClickListener(v -> showUniversalDeviceHub());
        devicePane.addView(hub, new LinearLayout.LayoutParams(-1, dp(44)));
        split.addView(devicePane, new LinearLayout.LayoutParams(0, -1, 1f));

        page.addView(split, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(page);
    }

    private void showDualWorkspace() {
        java.util.ArrayList<String> connected = getFreshAdbDevices();
        if (connected.isEmpty()) {
            showUniversalDualWorkspace();
            return;
        }
        final String[] deviceLabels = new String[connected.size()];
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
        for (int i = 0; i < connected.size(); i++) {
            String serial = connected.get(i);
            String alias = prefs.getString("alias_" + serial, "");
            deviceLabels[i] = (alias.isEmpty() ? serial : alias + " · " + serial);
        }
        String selected = connected.contains(dualWorkspaceSerial) ? dualWorkspaceSerial : connected.get(0);
        showDualWorkspaceForDevice(selected, connected, deviceLabels);
    }

    private String dualWorkspaceSerial = "";

    private void showDualWorkspaceForDevice(String serial, java.util.ArrayList<String> connected, String[] deviceLabels) {
        if (!isFreshAdbDevice(serial)) {
            line("Thiết bị không còn được xác nhận trực tuyến. Quét ADB lại trước khi mở màn phụ.", 0xffffca70);
            return;
        }
        dualWorkspaceSerial = serial;
        // A workspace switch replaces the single visible decoder. Stop its local
        // stream and remove only that device's port-forward; keep its ADB pairing alive.
        if (activeMirrorView != null && !serial.equals(activeMirrorSerial)) {
            activeMirrorView.stop();
            activeMirrorView = null;
            String previousSerial = activeMirrorSerial;
            if (previousSerial != null && !previousSerial.isEmpty()) {
                int previousPort = scrcpyPortFor(previousSerial);
                runTermuxCommand("adb -s " + shellQuote(previousSerial)
                    + " forward --remove tcp:" + previousPort
                    + " >/dev/null 2>&1 || true; echo BO_SCRCPY_FORWARD_CLOSED");
            }
        }
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(1);
        page.setPadding(dp(10), dp(10), dp(10), dp(10));
        page.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(0);
        Button back = button("QUAY LẠI");
        back.setOnClickListener(v -> setContentView(root));
        top.addView(back, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button choose = button("CHỌN THIẾT BỊ");
        choose.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Thiết bị ADB đang kết nối")
            .setItems(deviceLabels, (d, which) -> showDualWorkspaceForDevice(connected.get(which), connected, deviceLabels))
            .setNegativeButton("ĐÓNG", null).show());
        top.addView(choose, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button refresh = button("KIỂM TRA LẠI");
        refresh.setOnClickListener(v -> runTermuxCommand("adb devices -l"));
        top.addView(refresh, new LinearLayout.LayoutParams(0, dp(44), 1));
        page.addView(top);

        LinearLayout split = new LinearLayout(this);
        split.setOrientation(LinearLayout.HORIZONTAL);
        split.setWeightSum(5f);

        LinearLayout codePane = new LinearLayout(this);
        codePane.setOrientation(1);
        codePane.setPadding(dp(8), dp(8), dp(8), dp(8));
        codePane.setBackgroundColor(PANEL);
        TextView codeTitle = text("MÀN HÌNH CHÍNH · CODE / KALI / AI", GREEN, 13, true);
        codePane.addView(codeTitle);
        TextView codeInfo = text("Khu vực làm việc chính. Mở terminal đầy đủ để viết/chạy lệnh, dùng Kali và hỏi AI. Màn hình này không phụ thuộc thiết bị ADB.", WHITE, 12, false);
        codePane.addView(codeInfo);
        Button openTerminal = button("MỞ CODE / TERMINAL");
        openTerminal.setOnClickListener(v -> setContentView(root));
        codePane.addView(openTerminal, new LinearLayout.LayoutParams(-1, dp(48)));
        Button kali = button("MỞ KALI SHELL");
        kali.setOnClickListener(v -> kaliShellMenu());
        codePane.addView(kali, new LinearLayout.LayoutParams(-1, dp(44)));
        Button ai = button("HỎI AI");
        ai.setOnClickListener(v -> { setContentView(root); questionInput.requestFocus(); });
        codePane.addView(ai, new LinearLayout.LayoutParams(-1, dp(44)));

        LinearLayout devicePane = new LinearLayout(this);
        devicePane.setOrientation(1);
        devicePane.setPadding(dp(8), dp(8), dp(8), dp(8));
        devicePane.setBackgroundColor(0xff101f2e);
        TextView deviceTitle = text("MÀN HÌNH PHỤ · THIẾT BỊ", 0xff79c9ff, 13, true);
        devicePane.addView(deviceTitle);
        activeMirrorView = new ScrcpyMirrorView(this);
        devicePane.addView(activeMirrorView, new LinearLayout.LayoutParams(-1, dp(260)));
        Button startMirror = button("BẮT ĐẦU TRUYỀN & ĐIỀU KHIỂN TRỰC TIẾP");
        startMirror.setOnClickListener(v -> runAdbMirrorStart(serial));
        devicePane.addView(startMirror, new LinearLayout.LayoutParams(-1, dp(48)));
        LinearLayout remoteKeys = new LinearLayout(this);
        remoteKeys.setOrientation(LinearLayout.HORIZONTAL);
        Button keyBack = button("BACK");
        keyBack.setOnClickListener(v -> activeMirrorView.sendKey(4));
        Button keyHome = button("HOME");
        keyHome.setOnClickListener(v -> activeMirrorView.sendKey(3));
        Button keyRecent = button("RECENT");
        keyRecent.setOnClickListener(v -> activeMirrorView.sendKey(187));
        remoteKeys.addView(keyBack, new LinearLayout.LayoutParams(0, dp(40), 1));
        remoteKeys.addView(keyHome, new LinearLayout.LayoutParams(0, dp(40), 1));
        remoteKeys.addView(keyRecent, new LinearLayout.LayoutParams(0, dp(40), 1));
        devicePane.addView(remoteKeys);
        LinearLayout textEntry = new LinearLayout(this);
        textEntry.setOrientation(LinearLayout.HORIZONTAL);
        EditText remoteText = input("Nhập chữ để gửi tới thiết bị...");
        textEntry.addView(remoteText, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button sendText = button("GỬI CHỮ");
        sendText.setOnClickListener(v -> {
            if (activeMirrorView != null) activeMirrorView.sendText(remoteText.getText().toString());
            remoteText.setText("");
        });
        textEntry.addView(sendText, new LinearLayout.LayoutParams(dp(88), dp(46)));
        devicePane.addView(textEntry);
        Button stopMirror = button("TẮT PHIÊN ADB + SCRCPY HOÀN TOÀN");
        stopMirror.setOnClickListener(v -> stopAdbMirror(serial));
        devicePane.addView(stopMirror, new LinearLayout.LayoutParams(-1, dp(42)));
        android.content.SharedPreferences devicePrefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
        String alias = devicePrefs.getString("alias_" + serial, "");
        TextView deviceInfo = text("Đã xác nhận ADB: " + (alias.isEmpty() ? serial : alias + " · " + serial)
            + "\nTrạng thái: device\n\nHình ảnh truyền trực tiếp và thao tác chạm được xử lý qua scrcpy; một số ứng dụng có DRM có thể chặn hình ảnh.", WHITE, 12, false);
        devicePane.addView(deviceInfo);
        Button launch = button("CHỌN / MỞ ỨNG DỤNG");
        launch.setOnClickListener(v -> showAdbLaunchAppDialog(serial));
        devicePane.addView(launch, new LinearLayout.LayoutParams(-1, dp(48)));
        Button diagnostics = button("THÔNG TIN THIẾT BỊ");
        diagnostics.setOnClickListener(v -> showAdbReadOnlyTools(serial));
        devicePane.addView(diagnostics, new LinearLayout.LayoutParams(-1, dp(44)));
        Button shell = button("LỆNH ADB SHELL");
        shell.setOnClickListener(v -> showAdbShellDialogFor(serial));
        devicePane.addView(shell, new LinearLayout.LayoutParams(-1, dp(44)));

        final boolean[] deviceExpanded = {false};
        final boolean[] swapped = {false};
        Button expandCode = button("PHÓNG TO CODE");
        Button expandDevice = button("PHÓNG TO THIẾT BỊ");
        Button splitAgain = button("CHIA ĐÔI");
        Button swap = button("ĐỔI VỊ TRÍ");
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(0);
        controls.addView(expandCode, new LinearLayout.LayoutParams(0, dp(42), 1));
        controls.addView(expandDevice, new LinearLayout.LayoutParams(0, dp(42), 1));
        controls.addView(splitAgain, new LinearLayout.LayoutParams(0, dp(42), 1));
        controls.addView(swap, new LinearLayout.LayoutParams(0, dp(42), 1));
        page.addView(controls);

        LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(0, -1, 1f);
        LinearLayout.LayoutParams deviceParams = new LinearLayout.LayoutParams(0, -1, 1f);
        split.addView(codePane, codeParams);
        split.addView(devicePane, deviceParams);
        page.addView(split, new LinearLayout.LayoutParams(-1, 0, 1f));

        Runnable applySplit = () -> {
            codePane.setVisibility(View.VISIBLE);
            devicePane.setVisibility(View.VISIBLE);
            codePane.setLayoutParams(new LinearLayout.LayoutParams(0, -1, deviceExpanded[0] ? 1f : 1f));
            devicePane.setLayoutParams(new LinearLayout.LayoutParams(0, -1, deviceExpanded[0] ? 4f : 1f));
            if (deviceExpanded[0]) {
                codePane.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1f));
                devicePane.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 4f));
            }
            if (swapped[0]) {
                split.removeAllViews();
                if (deviceExpanded[0]) {
                    split.addView(devicePane, new LinearLayout.LayoutParams(0, -1, 4f));
                    split.addView(codePane, new LinearLayout.LayoutParams(0, -1, 1f));
                } else {
                    split.addView(devicePane, new LinearLayout.LayoutParams(0, -1, 1f));
                    split.addView(codePane, new LinearLayout.LayoutParams(0, -1, 1f));
                }
            } else {
                split.removeAllViews();
                split.addView(codePane, new LinearLayout.LayoutParams(0, -1, deviceExpanded[0] ? 1f : 1f));
                split.addView(devicePane, new LinearLayout.LayoutParams(0, -1, deviceExpanded[0] ? 4f : 1f));
            }
        };
        expandCode.setOnClickListener(v -> {
            deviceExpanded[0] = false;
            codePane.setVisibility(View.VISIBLE);
            devicePane.setVisibility(View.VISIBLE);
            split.removeAllViews();
            split.addView(codePane, new LinearLayout.LayoutParams(0, -1, 4f));
            split.addView(devicePane, new LinearLayout.LayoutParams(0, -1, 1f));
        });
        expandDevice.setOnClickListener(v -> {
            if (!isFreshAdbDevice(serial)) {
                devicePane.setVisibility(View.GONE);
                line("Kết nối ADB đã hết hiệu lực; màn phụ được khóa. Hãy quét lại.", 0xffffca70);
                return;
            }
            deviceExpanded[0] = true;
            split.removeAllViews();
            if (swapped[0]) {
                split.addView(devicePane, new LinearLayout.LayoutParams(0, -1, 4f));
                split.addView(codePane, new LinearLayout.LayoutParams(0, -1, 1f));
            } else {
                split.addView(codePane, new LinearLayout.LayoutParams(0, -1, 1f));
                split.addView(devicePane, new LinearLayout.LayoutParams(0, -1, 4f));
            }
        });
        splitAgain.setOnClickListener(v -> {
            deviceExpanded[0] = false;
            applySplit.run();
        });
        swap.setOnClickListener(v -> {
            swapped[0] = !swapped[0];
            applySplit.run();
        });

        setContentView(page);
        // If Android recreated the Activity while the ADB session stayed alive,
        // opening the same device workspace re-establishes the scrcpy transport.
        android.content.SharedPreferences activeSession =
            getSharedPreferences("bo_kali_adb_session", MODE_PRIVATE);
        if (activeSession.getBoolean("keep_alive", false)
            && serial.equals(activeSession.getString("active_serial", ""))) {
            resumeAdbMirrorAfterUnlock();
        }
    }

    // Find the pairing-code service advertised while the target pairing dialog is open.
    private void enqueueAdbServiceResolve(NsdServiceInfo service, boolean pairing) {
        if (service == null) return;
        String name = service.getServiceName();
        String type = service.getServiceType();
        if (name == null || type == null) return;
        String key = (pairing ? "pair:" : "connect:") + type + "|" + name;
        if (adbResolveQueuedKeys.contains(key)) return;
        if (adbResolveQueue.size() >= 24) {
            line("Đã đạt giới hạn 24 dịch vụ ADB trong một lượt quét; bỏ qua mục mới để tránh kéo dài thời gian chờ.", 0xffffca70);
            return;
        }
        adbResolveQueuedKeys.add(key);
        adbResolveQueue.offer(() -> resolveAdbServiceNow(service, pairing, key));
        resolveNextAdbService();
    }

    private void resolveNextAdbService() {
        if (!adbResolveInFlight && adbResolveDrainDeadline > 0L
            && android.os.SystemClock.elapsedRealtime() >= adbResolveDrainDeadline
            && !adbResolveQueue.isEmpty()) {
            int dropped = adbResolveQueue.size();
            adbResolveQueue.clear();
            adbResolveQueuedKeys.clear();
            adbResolveDrainDeadline = 0L;
            line("Đã hết thời gian chờ bổ sung sau khi dừng quét; bỏ qua "
                + dropped + " dịch vụ ADB chưa phân giải.", 0xffffca70);
        }
        if (adbResolveInFlight || adbResolveQueue.isEmpty()) {
            if (!adbResolveInFlight && adbResolveQueue.isEmpty()) {
                adbResolveDrainDeadline = 0L;
                releaseHubMulticastLock();
            }
            return;
        }
        Runnable task = adbResolveQueue.poll();
        if (task == null) return;
        adbResolveInFlight = true;
        int token = ++adbResolveTaskToken;
        adbResolveTimeout = () -> {
            if (!adbResolveInFlight || token != adbResolveTaskToken) return;
            String timedOutKey = adbResolveCurrentKey;
            adbResolveTimeout = null;
            adbResolveInFlight = false;
            adbResolveCurrentKey = "";
            // Invalidate the expired resolver immediately, even when the queue is empty.
            // A late NSD callback must never be mistaken for a current resolution.
            adbResolveTaskToken++;
            line("Hết thời gian phân giải dịch vụ ADB "
                + (timedOutKey.isEmpty() ? "(không rõ tên)" : timedOutKey)
                + "; bỏ qua dịch vụ này và tiếp tục hàng đợi.", 0xffffca70);
            resolveNextAdbService();
        };
        // The task starts the NSD resolver. The timeout only invalidates the current
        // callback and advances the queue; task.run() is invoked below once.
        mainHandler.postDelayed(adbResolveTimeout, 5000L);
        task.run();
    }

    private void finishAdbServiceResolve(String key, int token) {
        if (!adbResolveInFlight || token != adbResolveTaskToken) return;
        if (adbResolveTimeout != null) mainHandler.removeCallbacks(adbResolveTimeout);
        adbResolveTimeout = null;
        adbResolveInFlight = false;
        adbResolveCurrentKey = "";
        resolveNextAdbService();
    }

    private void resolveAdbServiceNow(NsdServiceInfo service, boolean pairing, String key) {
        adbResolveCurrentKey = key;
        final int token = adbResolveTaskToken;
        final String name = service.getServiceName();
        try {
            nsdManager.resolveService(service, new NsdManager.ResolveListener() {
                @Override public void onResolveFailed(NsdServiceInfo info, int error) {
                    if (token != adbResolveTaskToken || !adbResolveInFlight) return;
                    line((pairing ? "Không phân giải được dịch vụ pairing: " : "Không đọc được ADB endpoint ")
                        + name + ", mã " + error, 0xffffca70);
                    finishAdbServiceResolve(key, token);
                }
                @Override public void onServiceResolved(NsdServiceInfo info) {
                    if (token != adbResolveTaskToken || !adbResolveInFlight) return;
                    InetAddress host = info.getHost();
                    String endpoint = host == null ? "" : formatAdbEndpoint(host, info.getPort());
                    if (!isValidAdbEndpoint(endpoint)) {
                        line("Dịch vụ ADB trả về địa chỉ/cổng không hợp lệ: " + name, 0xffffca70);
                        finishAdbServiceResolve(key, token);
                        return;
                    }
                    android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
                    if (pairing) {
                        java.util.Set<String> saved = new java.util.HashSet<>(prefs.getStringSet("pairing_endpoints", new java.util.HashSet<>()));
                        boolean added = saved.add(endpoint);
                        prefs.edit().putStringSet("pairing_endpoints", saved)
                            .putString("pairing_service_" + endpoint, name)
                            .putLong("pairing_discovered_at_" + endpoint, System.currentTimeMillis()).apply();
                        if (added) {
                            line("Đã tìm thấy cổng ghép đôi: " + name + " — " + endpoint, GREEN);
                            line("Cổng này dùng cho adb pair; cổng KẾT NỐI IP là một cổng khác.", 0xff79c9ff);
                        }
                    } else {
                        java.util.Set<String> saved = new java.util.HashSet<>(prefs.getStringSet("discovered_endpoints", new java.util.HashSet<>()));
                        boolean added = saved.add(endpoint);
                        prefs.edit().putStringSet("discovered_endpoints", saved)
                            .putString("service_" + endpoint, name)
                            .putLong("discovered_at_" + endpoint, System.currentTimeMillis()).apply();
                        if (added) {
                            line("Tìm thấy ADB: " + name + " — " + endpoint, GREEN);
                            line("Đã lưu endpoint kết nối; nếu thiết bị chưa ghép đôi, hãy dùng GHÉP ĐÔI ADB với cổng pairing riêng.", WHITE);
                            line("Mở KẾT NỐI IP để chọn endpoint vừa tìm thấy.", 0xff79c9ff);
                        }
                    }
                    finishAdbServiceResolve(key, token);
                }
            });
        } catch (Exception error) {
            if (token == adbResolveTaskToken && adbResolveInFlight) {
                line("Lỗi phân giải dịch vụ ADB " + name + ": " + error.getMessage(), 0xffffca70);
                finishAdbServiceResolve(key, token);
            }
        }
    }

    private void stopAdbServiceScans() {
        NsdManager.DiscoveryListener connectListener = adbDiscoveryListener;
        NsdManager.DiscoveryListener pairingListener = adbPairingDiscoveryListener;
        adbDiscoveryListener = null;
        adbPairingDiscoveryListener = null;
        if (adbDiscoveryTimeout != null) mainHandler.removeCallbacks(adbDiscoveryTimeout);
        if (adbPairingDiscoveryTimeout != null) mainHandler.removeCallbacks(adbPairingDiscoveryTimeout);
        adbDiscoveryTimeout = null;
        adbPairingDiscoveryTimeout = null;
        if (nsdManager != null) {
            if (connectListener != null) {
                try { nsdManager.stopServiceDiscovery(connectListener); } catch (Exception ignored) { }
            }
            if (pairingListener != null) {
                try { nsdManager.stopServiceDiscovery(pairingListener); } catch (Exception ignored) { }
            }
        }
        adbScanActive = false;
        adbPairingScanActive = false;
        if (adbResolveTimeout != null) mainHandler.removeCallbacks(adbResolveTimeout);
        adbResolveTimeout = null;
        adbResolveTaskToken++;
        adbResolveInFlight = false;
        adbResolveCurrentKey = "";
        adbResolveQueue.clear();
        adbResolveQueuedKeys.clear();
        adbResolveDrainDeadline = 0L;
        releaseHubMulticastLock();
    }

    private void scanAdbPairingServices() {
        if (adbPairingScanActive || adbScanActive || adbResolveInFlight || !adbResolveQueue.isEmpty()) {
            line("Đang có một lượt quét/phân giải ADB khác. Hãy đợi lượt đó kết thúc.", 0xffffca70);
            return;
        }
        adbResolveQueuedKeys.clear();
        adbResolveDrainDeadline = 0L;
        nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
        if (nsdManager == null) { line("Thiết bị không hỗ trợ Network Service Discovery.", 0xffff8080); return; }
        adbPairingScanActive = true;
        acquireHubMulticastLock();
        line("Đang tìm dịch vụ ADB pairing trên mạng Wi-Fi...", GREEN);
        final NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String type) {
                if (adbPairingDiscoveryListener == this) line("Đang quét " + type, 0xff79c9ff);
            }
            @Override public void onServiceFound(NsdServiceInfo service) {
                if (adbPairingDiscoveryListener == this && adbPairingScanActive) enqueueAdbServiceResolve(service, true);
            }
            @Override public void onServiceLost(NsdServiceInfo service) {
                if (adbPairingDiscoveryListener == this) line("Dịch vụ pairing đã ngừng quảng bá: " + service.getServiceName(), 0xffffca70);
            }
            @Override public void onStartDiscoveryFailed(String type, int error) {
                if (adbPairingDiscoveryListener != this) return;
                adbPairingScanActive = false;
                if (adbPairingDiscoveryTimeout != null) mainHandler.removeCallbacks(adbPairingDiscoveryTimeout);
                adbPairingDiscoveryTimeout = null;
                adbPairingDiscoveryListener = null;
                try { nsdManager.stopServiceDiscovery(this); } catch (Exception ignored) { }
                releaseHubMulticastLock();
                line("Quét pairing thất bại, mã " + error, 0xffff8080);
            }
            @Override public void onStopDiscoveryFailed(String type, int error) {
                if (adbPairingDiscoveryListener != this) return;
                adbPairingScanActive = false;
                if (adbPairingDiscoveryTimeout != null) mainHandler.removeCallbacks(adbPairingDiscoveryTimeout);
                adbPairingDiscoveryTimeout = null;
                adbPairingDiscoveryListener = null;
                if (adbResolveInFlight || !adbResolveQueue.isEmpty()) {
                    adbResolveDrainDeadline = android.os.SystemClock.elapsedRealtime() + 15000L;
                }
                try { nsdManager.stopServiceDiscovery(this); } catch (Exception ignored) { }
                releaseHubMulticastLock();
                line("Không dừng được lượt quét pairing bình thường, mã " + error
                    + "; vẫn giới hạn thời gian xử lý endpoint đã tìm thấy.", 0xffffca70);
            }
            @Override public void onDiscoveryStopped(String type) {
                if (adbPairingDiscoveryListener != this) return;
                adbPairingScanActive = false;
                if (adbPairingDiscoveryTimeout != null) mainHandler.removeCallbacks(adbPairingDiscoveryTimeout);
                adbPairingDiscoveryTimeout = null;
                adbPairingDiscoveryListener = null;
                if (adbResolveInFlight || !adbResolveQueue.isEmpty()) {
                    adbResolveDrainDeadline = android.os.SystemClock.elapsedRealtime() + 15000L;
                }
                releaseHubMulticastLock();
                line("Đã kết thúc quét pairing; đang xử lý nốt các endpoint đã tìm thấy.", GREEN);
            }
        };
        adbPairingDiscoveryListener = listener;
        try {
            nsdManager.discoverServices("_adb-tls-pairing._tcp.", NsdManager.PROTOCOL_DNS_SD, listener);
            adbPairingDiscoveryTimeout = () -> {
                if (!adbPairingScanActive || adbPairingDiscoveryListener != listener) return;
                adbPairingDiscoveryTimeout = null;
                try { nsdManager.stopServiceDiscovery(listener); }
                catch (Exception error) {
                    if (adbPairingDiscoveryListener == listener) {
                        adbPairingScanActive = false;
                        adbPairingDiscoveryListener = null;
                        releaseHubMulticastLock();
                        line("Không thể kết thúc lượt quét pairing: " + error.getMessage(), 0xffffca70);
                    }
                }
            };
            mainHandler.postDelayed(adbPairingDiscoveryTimeout, 12000L);
        } catch (Exception e) {
            if (adbPairingDiscoveryListener == listener) {
                adbPairingScanActive = false;
                adbPairingDiscoveryListener = null;
            }
            if (adbPairingDiscoveryTimeout != null) mainHandler.removeCallbacks(adbPairingDiscoveryTimeout);
            adbPairingDiscoveryTimeout = null;
            releaseHubMulticastLock();
            line("Lỗi quét pairing: " + e.getMessage(), 0xffff8080);
        }
    }

    // Read-only diagnostics for ADB's mDNS stack and discovered service endpoints.
    private void checkAdbMdns() {
        line("Đang kiểm tra mDNS của ADB trong Termux (chỉ đọc, không ghép đôi/ngắt kết nối)...", GREEN);
        String cmd = "echo '=== ADB AVAILABILITY ==='; " +
            "if ! command -v adb >/dev/null 2>&1; then echo 'ADB chưa cài. Vào Trung tâm cập nhật để cài android-tools.'; exit 127; fi; " +
            "echo '=== ADB VERSION ==='; adb version; " +
            "echo '=== mDNS STACK ==='; adb mdns check; " +
            "echo '=== DISCOVERED mDNS SERVICES ==='; adb mdns services; " +
            "echo '=== CONNECTED DEVICES ==='; adb devices -l";
        runTermuxCommand(cmd);
    }

    // Discovers Wireless Debugging endpoints; discovery alone does not pair or authenticate.
    private void scanAdbServices() {
        if (adbScanActive || adbPairingScanActive || adbResolveInFlight || !adbResolveQueue.isEmpty()) {
            line("Đang có một lượt quét/phân giải ADB khác. Hãy đợi lượt đó kết thúc.", 0xffffca70);
            return;
        }
        adbResolveQueuedKeys.clear();
        adbResolveDrainDeadline = 0L;
        nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
        if (nsdManager == null) { line("Thiết bị không hỗ trợ Network Service Discovery.", 0xffff8080); return; }
        adbScanActive = true;
        acquireHubMulticastLock();
        line("Đang tìm Wireless debugging trên cùng mạng Wi-Fi...", GREEN);
        line("Tìm thấy endpoint chưa có nghĩa là đã ghép đôi.", 0xffffca70);
        final NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String type) {
                if (adbDiscoveryListener == this) line("Đang quét " + type, 0xff79c9ff);
            }
            @Override public void onServiceFound(NsdServiceInfo service) {
                if (adbDiscoveryListener == this && adbScanActive) enqueueAdbServiceResolve(service, false);
            }
            @Override public void onServiceLost(NsdServiceInfo service) {
                if (adbDiscoveryListener == this) line("Mất dịch vụ: " + service.getServiceName(), 0xffffca70);
            }
            @Override public void onStartDiscoveryFailed(String type, int error) {
                if (adbDiscoveryListener != this) return;
                adbScanActive = false;
                if (adbDiscoveryTimeout != null) mainHandler.removeCallbacks(adbDiscoveryTimeout);
                adbDiscoveryTimeout = null;
                adbDiscoveryListener = null;
                try { nsdManager.stopServiceDiscovery(this); } catch (Exception ignored) { }
                releaseHubMulticastLock();
                line("Quét ADB thất bại, mã " + error, 0xffff8080);
            }
            @Override public void onStopDiscoveryFailed(String type, int error) {
                if (adbDiscoveryListener != this) return;
                adbScanActive = false;
                if (adbDiscoveryTimeout != null) mainHandler.removeCallbacks(adbDiscoveryTimeout);
                adbDiscoveryTimeout = null;
                adbDiscoveryListener = null;
                if (adbResolveInFlight || !adbResolveQueue.isEmpty()) {
                    adbResolveDrainDeadline = android.os.SystemClock.elapsedRealtime() + 15000L;
                }
                try { nsdManager.stopServiceDiscovery(this); } catch (Exception ignored) { }
                releaseHubMulticastLock();
                line("Không dừng được lượt quét ADB bình thường, mã " + error
                    + "; vẫn giới hạn thời gian xử lý endpoint đã tìm thấy.", 0xffffca70);
            }
            @Override public void onDiscoveryStopped(String type) {
                if (adbDiscoveryListener != this) return;
                adbScanActive = false;
                if (adbDiscoveryTimeout != null) mainHandler.removeCallbacks(adbDiscoveryTimeout);
                adbDiscoveryTimeout = null;
                adbDiscoveryListener = null;
                if (adbResolveInFlight || !adbResolveQueue.isEmpty()) {
                    adbResolveDrainDeadline = android.os.SystemClock.elapsedRealtime() + 15000L;
                }
                releaseHubMulticastLock();
                line("Đã kết thúc quét ADB; đang xử lý nốt các endpoint đã tìm thấy.", GREEN);
            }
        };
        adbDiscoveryListener = listener;
        try {
            nsdManager.discoverServices("_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, listener);
            adbDiscoveryTimeout = () -> {
                if (!adbScanActive || adbDiscoveryListener != listener) return;
                adbDiscoveryTimeout = null;
                try { nsdManager.stopServiceDiscovery(listener); }
                catch (Exception error) {
                    if (adbDiscoveryListener == listener) {
                        adbScanActive = false;
                        adbDiscoveryListener = null;
                        releaseHubMulticastLock();
                        line("Không thể kết thúc lượt quét ADB: " + error.getMessage(), 0xffffca70);
                    }
                }
            };
            mainHandler.postDelayed(adbDiscoveryTimeout, 12000L);
        } catch (Exception e) {
            if (adbDiscoveryListener == listener) {
                adbScanActive = false;
                adbDiscoveryListener = null;
            }
            if (adbDiscoveryTimeout != null) mainHandler.removeCallbacks(adbDiscoveryTimeout);
            adbDiscoveryTimeout = null;
            releaseHubMulticastLock();
            line("Lỗi quét ADB: " + e.getMessage(), 0xffff8080);
        }
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
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
        java.util.ArrayList<String> endpoints = new java.util.ArrayList<>(
            prefs.getStringSet("pairing_endpoints", new java.util.HashSet<>()));
        java.util.Collections.sort(endpoints);
        if (endpoints.isEmpty()) {
            showAdbPairForm("");
            return;
        }
        String[] choices = new String[endpoints.size()];
        for (int i = 0; i < endpoints.size(); i++) {
            String ep = endpoints.get(i);
            String service = prefs.getString("pairing_service_" + ep, "");
            long found = prefs.getLong("pairing_discovered_at_" + ep, 0L);
            String when = found == 0L ? "chưa rõ thời điểm" :
                new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(found));
            boolean stale = found == 0L || System.currentTimeMillis() - found > 10L * 60L * 1000L;
            choices[i] = ep + (service.isEmpty() ? "" : "  ·  " + service) + "  ·  thấy lần cuối " + when + (stale ? "  ·  CÓ THỂ CŨ" : "  ·  MỚI");
        }
        new AlertDialog.Builder(this).setTitle("Chọn cổng ghép đôi đã quét")
            .setItems(choices, (d, which) -> showAdbPairForm(endpoints.get(which)))
            .setPositiveButton("NHẬP THỦ CÔNG", (d,w) -> showAdbPairForm(""))
            .setNeutralButton("QUẢN LÝ ENDPOINT", (d,w) -> showManageAdbEndpoints(true))
            .setNegativeButton("HỦY", null).show();
    }

    private void showAdbPairForm(String presetEndpoint) {
        LinearLayout form = new LinearLayout(this); form.setOrientation(1); form.setPadding(dp(16), dp(8), dp(16), 0);
        EditText endpoint = input("IP: CỔNG GHÉP ĐÔI (ví dụ 192.168.1.20:37123)");
        if (presetEndpoint != null && !presetEndpoint.isEmpty()) endpoint.setText(presetEndpoint);
        EditText code = input("Mã ghép đôi 6 chữ số"); code.setInputType(2);
        form.addView(endpoint); form.addView(code);
        new AlertDialog.Builder(this).setTitle("Ghép đôi ADB không dây")
            .setMessage("Trên thiết bị đích, mở Developer options → Wireless debugging → Pair device with pairing code. Nhập đúng cổng pairing và mã 6 số đang hiển thị. Cổng pairing khác cổng kết nối ADB.")
            .setView(form).setPositiveButton("GHÉP ĐÔI", (d,w) -> {
                String ep = endpoint.getText().toString().trim(); String pin = code.getText().toString().trim();
                if (!isValidAdbEndpoint(ep) || !pin.matches("[0-9]{6}")) { line("IP:cổng hoặc mã ghép đôi không hợp lệ. Cổng phải từ 1 đến 65535.", 0xffff8080); return; }
                long foundAt = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE)
                    .getLong("pairing_discovered_at_" + ep, 0L);
                boolean stale = foundAt == 0L || System.currentTimeMillis() - foundAt > 10L * 60L * 1000L;
                if (stale) {
                    new AlertDialog.Builder(this).setTitle("Cổng ghép đôi có thể đã cũ")
                        .setMessage("Cổng " + ep + " chưa được tìm thấy trong 10 phút gần đây hoặc không có thời điểm quét. Hãy kiểm tra lại cổng và mã 6 số trên màn hình Wireless debugging trước khi tiếp tục.")
                        .setPositiveButton("VẪN GHÉP ĐÔI", (confirm, which) -> runTermuxCommand(
                            "pkg install -y android-tools && adb pair " + shellQuote(ep) + " " + shellQuote(pin)))
                        .setNegativeButton("HỦY", null).show();
                } else {
                    runTermuxCommand("pkg install -y android-tools && adb pair " + shellQuote(ep) + " " + shellQuote(pin));
                }
            }).setNegativeButton("HỦY", null).show();
    }

    private void showAdbConnectDialog() {
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
        java.util.ArrayList<String> discovered = new java.util.ArrayList<>(
            prefs.getStringSet("discovered_endpoints", new java.util.HashSet<>()));
        java.util.Collections.sort(discovered);
        String[] choices = new String[discovered.size()];
        for (int i = 0; i < discovered.size(); i++) {
            String ep = discovered.get(i);
            String service = prefs.getString("service_" + ep, "");
            long found = prefs.getLong("discovered_at_" + ep, 0L);
            String when = found == 0L ? "chưa rõ thời điểm" :
                new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(found));
            choices[i] = ep + (service.isEmpty() ? "" : "  ·  " + service) + "  ·  thấy lần cuối " + when;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle("Kết nối ADB");
        if (choices.length > 0) {
            builder.setItems(choices, (d, which) -> connectAdbEndpoint(discovered.get(which)));
            builder.setPositiveButton("NHẬP IP:CỔNG", (d, w) -> showManualAdbConnectDialog());
            builder.setNeutralButton("QUẢN LÝ ENDPOINT", (d, w) -> showManageAdbEndpoints(false));
            builder.setNegativeButton("ĐÓNG", null).show();
        } else {
            showManualAdbConnectDialog();
        }
    }

    // Remove only locally saved discovery endpoints; this never disconnects an ADB device.
    private void showManageAdbEndpoints(boolean pairing) {
        final String listKey = pairing ? "pairing_endpoints" : "discovered_endpoints";
        final String servicePrefix = pairing ? "pairing_service_" : "service_";
        final String title = pairing ? "Endpoint ghép đôi đã lưu" : "Endpoint kết nối đã lưu";
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
        java.util.ArrayList<String> endpoints = new java.util.ArrayList<>(
            prefs.getStringSet(listKey, new java.util.HashSet<>()));
        java.util.Collections.sort(endpoints);
        if (endpoints.isEmpty()) {
            new AlertDialog.Builder(this).setTitle(title)
                .setMessage("Không có endpoint nào được lưu.")
                .setPositiveButton("ĐÓNG", null).show();
            return;
        }
        String[] labels = new String[endpoints.size()];
        boolean[] checked = new boolean[endpoints.size()];
        for (int i = 0; i < endpoints.size(); i++) {
            String endpoint = endpoints.get(i);
            String service = prefs.getString(servicePrefix + endpoint, "");
            labels[i] = endpoint + (service.isEmpty() ? "" : "\\n" + service);
        }
        new AlertDialog.Builder(this).setTitle(title)
            .setMessage("Chọn endpoint muốn xóa khỏi danh sách lưu. Thao tác này chỉ xóa địa chỉ đã lưu trong BO Kali AI; không ngắt kết nối ADB và không thay đổi thiết bị.")
            .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
            .setPositiveButton("XÓA ĐÃ CHỌN", (dialog, which) -> {
                java.util.HashSet<String> remaining = new java.util.HashSet<>(
                    prefs.getStringSet(listKey, new java.util.HashSet<>()));
                android.content.SharedPreferences.Editor editor = prefs.edit();
                int removed = 0;
                for (int i = 0; i < endpoints.size(); i++) {
                    if (checked[i] && remaining.remove(endpoints.get(i))) {
                        editor.remove(servicePrefix + endpoints.get(i));
                        editor.remove((pairing ? "pairing_discovered_at_" : "discovered_at_") + endpoints.get(i));
                        removed++;
                    }
                }
                editor.putStringSet(listKey, remaining).apply();
                line(removed == 0 ? "Chưa chọn endpoint nào để xóa." :
                    "Đã xóa " + removed + " endpoint khỏi danh sách lưu. Kết nối ADB hiện có không bị ngắt.", GREEN);
            })
            .setNeutralButton("DỌN ENDPOINT CŨ", (dialog, which) -> cleanupOldAdbEndpoints(pairing))
            .setNegativeButton("ĐÓNG", null).show();
    }

    private void cleanupOldAdbEndpoints(boolean pairing) {
        final String listKey = pairing ? "pairing_endpoints" : "discovered_endpoints";
        final String servicePrefix = pairing ? "pairing_service_" : "service_";
        final String timePrefix = pairing ? "pairing_discovered_at_" : "discovered_at_";
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
        java.util.Set<String> current = prefs.getStringSet(listKey, new java.util.HashSet<>());
        long cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
        java.util.ArrayList<String> stale = new java.util.ArrayList<>();
        for (String endpoint : current) {
            long foundAt = prefs.getLong(timePrefix + endpoint, 0L);
            if (foundAt == 0L || foundAt < cutoff) stale.add(endpoint);
        }
        if (stale.isEmpty()) {
            line("Không có endpoint cũ hơn 24 giờ cần dọn.", GREEN);
            return;
        }
        java.util.Collections.sort(stale);
        StringBuilder preview = new StringBuilder();
        int shown = Math.min(stale.size(), 8);
        for (int i = 0; i < shown; i++) preview.append("• ").append(stale.get(i)).append("\\n");
        if (stale.size() > shown) preview.append("… và ").append(stale.size() - shown).append(" endpoint khác\\n");
        new AlertDialog.Builder(this).setTitle("Xác nhận dọn endpoint cũ")
            .setMessage("Tìm thấy " + stale.size() + " endpoint đã quá 24 giờ hoặc không có thời điểm quét:\\n\\n" +
                preview + "\\nChỉ xóa địa chỉ lưu trong ứng dụng. Không ngắt kết nối ADB và không tác động thiết bị.")
            .setPositiveButton("XÓA ENDPOINT CŨ", (dialog, which) -> {
                java.util.HashSet<String> remaining = new java.util.HashSet<>(
                    prefs.getStringSet(listKey, new java.util.HashSet<>()));
                android.content.SharedPreferences.Editor editor = prefs.edit();
                int removed = 0;
                for (String endpoint : stale) {
                    if (remaining.remove(endpoint)) {
                        editor.remove(servicePrefix + endpoint);
                        editor.remove(timePrefix + endpoint);
                        removed++;
                    }
                }
                editor.putStringSet(listKey, remaining).apply();
                line("Đã dọn " + removed + " endpoint cũ khỏi danh sách lưu. Kết nối ADB hiện có không bị ngắt.", GREEN);
            })
            .setNegativeButton("GIỮ LẠI", null).show();
    }

    private void showManualAdbConnectDialog() {
        EditText endpoint = input("IP:CỔNG KẾT NỐI (không phải cổng pairing)");
        new AlertDialog.Builder(this).setTitle("Nhập endpoint ADB")
            .setMessage("Dùng cổng kết nối hiển thị trên màn hình Wireless debugging, không dùng cổng Pair device.")
            .setView(endpoint).setPositiveButton("KẾT NỐI", (d,w) -> {
                String ep = endpoint.getText().toString().trim();
                if (!isValidAdbEndpoint(ep)) { line("Địa chỉ IP:cổng không hợp lệ. Cổng phải từ 1 đến 65535.", 0xffff8080); return; }
                connectAdbEndpoint(ep);
            }).setNegativeButton("HỦY", null).show();
    }

    // ADB accepts IPv6 endpoints in [address]:port form; keep IPv4/host:port too.
    private String formatAdbEndpoint(InetAddress host, int port) {
        if (host == null || port < 1 || port > 65535) return "";
        String address = host.getHostAddress();
        if (address == null || address.isEmpty()) return "";
        return (host instanceof java.net.Inet6Address ? "[" + address + "]" : address) + ":" + port;
    }

    private boolean isValidAdbEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) return false;
        String host;
        String portText;
        if (endpoint.startsWith("[")) {
            int close = endpoint.indexOf(']');
            if (close < 0 || close + 1 >= endpoint.length() || endpoint.charAt(close + 1) != ':') return false;
            host = endpoint.substring(1, close);
            portText = endpoint.substring(close + 2);
            if (!host.matches("[0-9A-Fa-f:.%a-zA-Z_-]+") || host.indexOf(':') < 0) return false;
        } else {
            int colon = endpoint.lastIndexOf(':');
            if (colon <= 0 || endpoint.indexOf(':') != colon) return false;
            host = endpoint.substring(0, colon);
            portText = endpoint.substring(colon + 1);
            if (!host.matches("[A-Za-z0-9.-]+")) return false;
        }
        if (!portText.matches("[0-9]{1,5}")) return false;
        try {
            int port = Integer.parseInt(portText);
            return port >= 1 && port <= 65535;
        } catch (Exception e) { return false; }
    }

    private void connectAdbEndpoint(String endpoint) {
        if (!isValidAdbEndpoint(endpoint)) {
            line("Endpoint ADB không hợp lệ hoặc cổng ngoài phạm vi 1–65535.", 0xffff8080);
            return;
        }
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
        long foundAt = prefs.getLong("discovered_at_" + endpoint, 0L);
        long age = foundAt == 0L ? Long.MAX_VALUE : Math.max(0L, System.currentTimeMillis() - foundAt);
        if (age > 10L * 60L * 1000L) {
            String ageText = foundAt == 0L ? "Không có thời điểm quét được lưu." :
                "Lần cuối tìm thấy cách đây " + android.text.format.DateUtils.getRelativeTimeSpanString(
                    foundAt, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS) + ".";
            new AlertDialog.Builder(this).setTitle("Endpoint có thể đã cũ")
                .setMessage(endpoint + "\\n" + ageText +
                    "\\nĐịa chỉ hoặc cổng có thể đã thay đổi sau khi thiết bị khởi động lại hoặc đổi mạng. Bạn nên quét ADB lại trước.")
                .setPositiveButton("VẪN THỬ KẾT NỐI", (d, w) -> confirmAdbEndpointConnection(endpoint))
                .setNeutralButton("QUÉT LẠI", (d, w) -> scanAdbServices())
                .setNegativeButton("HỦY", null).show();
            return;
        }
        confirmAdbEndpointConnection(endpoint);
    }

    private void confirmAdbEndpointConnection(String endpoint) {
        new AlertDialog.Builder(this).setTitle("Xác nhận kết nối ADB")
            .setMessage("Kết nối tới " + endpoint + "? Đây là cổng kết nối, không phải cổng ghép đôi. Thiết bị phải được ghép đôi và cho phép Wireless debugging.")
            .setPositiveButton("KẾT NỐI", (d, w) ->
                runTermuxCommand("pkg install -y android-tools && adb connect " + shellQuote(endpoint) + " && adb devices -l"))
            .setNegativeButton("HỦY", null).show();
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
            intent.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "BO Kali AI - tác vụ do người dùng yêu cầu");
            startService(intent);
            line("Đã gửi tác vụ đến Termux. Đang chờ kết quả...", GREEN);
        } catch (SecurityException e) { line("Termux chưa bật allow-external-apps hoặc chưa cấp quyền RUN_COMMAND.", 0xffffca70); openTermux(); }
          catch (Exception e) { line("Không gửi được tác vụ đến Termux: " + e.getMessage(), 0xffff8080); }
    }

    
    
    private void showAdbBatchCheck() {
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
        java.util.ArrayList<String> saved = new java.util.ArrayList<>(
            prefs.getStringSet("serials", new java.util.HashSet<>()));
        java.util.Collections.sort(saved);
        if (saved.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Kiểm tra nhiều thiết bị")
                .setMessage("Chưa có thiết bị đã lưu. Hãy ghép đôi/kết nối ADB rồi quét danh sách trước.")
                .setPositiveButton("ĐÓNG", null).show();
            return;
        }
        String[] labels = new String[saved.size()];
        boolean[] checked = new boolean[saved.size()];
        for (int i = 0; i < saved.size(); i++) {
            String serial = saved.get(i);
            String alias = prefs.getString("alias_" + serial, "");
            String state = adbStateLabel(prefs.getString("state_" + serial, "unknown"));
            labels[i] = (alias.isEmpty() ? serial : alias + " · " + serial) + " [" + state + "]";
        }
        new AlertDialog.Builder(this).setTitle("Chọn thiết bị cần kiểm tra")
            .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
            .setPositiveButton("TIẾP TỤC", (dialog, which) -> {
                java.util.ArrayList<String> selected = new java.util.ArrayList<>();
                for (int i = 0; i < saved.size(); i++) if (checked[i]) selected.add(saved.get(i));
                if (selected.isEmpty()) {
                    line("Chưa chọn thiết bị nào.", 0xffffca70);
                    return;
                }
                if (selected.size() > 8) {
                    line("Chỉ kiểm tra tối đa 8 thiết bị mỗi lượt để tránh quá tải Termux.", 0xffffca70);
                    return;
                }
                StringBuilder command = new StringBuilder("pkg install -y android-tools >/dev/null 2>&1; ");
                StringBuilder summary = new StringBuilder();
                for (String serial : selected) {
                    String alias = prefs.getString("alias_" + serial, "");
                    summary.append("\n• ").append(alias.isEmpty() ? serial : alias + " (" + serial + ")");
                    String quoted = shellQuote(serial);
                    String remoteReadOnly = "echo MODEL:; getprop ro.product.model; "
                        + "echo BRAND:; getprop ro.product.brand; "
                        + "echo ANDROID:; getprop ro.build.version.release; "
                        + "echo SDK:; getprop ro.build.version.sdk; "
                        + "echo SECURITY_PATCH:; getprop ro.build.version.security_patch; "
                        + "echo BATTERY:; dumpsys battery | grep -E 'level:|temperature:' | head -n 2; "
                        + "echo STORAGE_DATA:; df -h /data | tail -n 1";
                    command.append("printf '\\n===== DEVICE ").append(serial).append(" =====\\n'; ")
                        .append("adb -s ").append(quoted).append(" get-state 2>&1; ")
                        .append("adb -s ").append(quoted).append(" shell ").append(shellQuote(remoteReadOnly))
                        .append(" 2>&1; ");
                }
                command.append("printf '\\n===== ADB DEVICES =====\\n'; adb devices -l");
                new AlertDialog.Builder(this).setTitle("Xác nhận kiểm tra hàng loạt")
                    .setMessage("Sẽ đọc trạng thái, model, hãng, phiên bản Android, SDK, bản vá bảo mật, pin và dung lượng /data của "
                        + selected.size() + " thiết bị:" + summary
                        + "\n\nChỉ đọc dữ liệu. Không cài ứng dụng, không đổi cài đặt và không xóa dữ liệu trên thiết bị.")
                    .setPositiveButton("CHẠY KIỂM TRA", (d, w) -> runTermuxCommand(command.toString()))
                    .setNegativeButton("HỦY", null).show();
            }).setNegativeButton("HỦY", null).show();
    }

    private static final int REQ_ADB_BACKUP_EXPORT = 4101;
    private static final int REQ_ADB_BACKUP_IMPORT = 4102;
    private static final int REQ_RESULT_EXPORT = 4103;

    private void showAdbBackupMenu() {
        new AlertDialog.Builder(this).setTitle("Sao lưu / khôi phục ADB")
            .setMessage("Tệp sao lưu chứa serial, tên gợi nhớ, trạng thái, endpoint Wi-Fi và lệnh thường dùng đã lưu. Hãy giữ tệp riêng tư.")
            .setItems(new String[]{"Xuất bản sao lưu JSON", "Nhập bản sao lưu JSON"}, (d, which) -> {
                if (which == 0) {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(Intent.EXTRA_TITLE, "bo-kali-adb-backup.json");
                    startActivityForResult(intent, REQ_ADB_BACKUP_EXPORT);
                } else {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    startActivityForResult(intent, REQ_ADB_BACKUP_IMPORT);
                }
            }).setNegativeButton("ĐÓNG", null).show();
    }

    private JSONObject createAdbBackupJson() throws Exception {
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
        JSONObject rootJson = new JSONObject();
        rootJson.put("format", "bo-kali-adb-backup");
        rootJson.put("version", 1);
        rootJson.put("createdAt", System.currentTimeMillis());
        JSONObject data = new JSONObject();
        java.util.Map<String, ?> all = prefs.getAll();
        for (java.util.Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if ("serials".equals(key) || "discovered_endpoints".equals(key) || "pairing_endpoints".equals(key)) {
                JSONArray array = new JSONArray();
                if (value instanceof java.util.Set) {
                    for (Object item : (java.util.Set<?>) value) array.put(String.valueOf(item));
                }
                data.put(key, array);
            } else if ("last_scan_time".equals(key) || key.startsWith("state_") || key.startsWith("seen_")
                || key.startsWith("alias_") || key.startsWith("preset_") || key.startsWith("service_")
                || key.startsWith("discovered_at_") || key.startsWith("pairing_service_")
                || key.startsWith("pairing_discovered_at_")) {
                if (value instanceof String || value instanceof Number || value instanceof Boolean) data.put(key, value);
            }
        }
        rootJson.put("data", data);
        return rootJson;
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_RESULT_EXPORT) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new java.io.IOException("Không mở được tệp đích");
                out.write(pendingExportText.getBytes(StandardCharsets.UTF_8));
                out.flush();
                line("Đã xuất kết quả ADB / Kali thành tệp TXT.", GREEN);
                pendingExportText = "";
            } catch (Exception e) {
                line("Không xuất được báo cáo TXT: " + e.getMessage(), 0xffff8080);
            }
        } else if (requestCode == REQ_ADB_BACKUP_EXPORT) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new java.io.IOException("Không mở được tệp đích");
                out.write(createAdbBackupJson().toString(2).getBytes(StandardCharsets.UTF_8));
                out.flush();
                line("Đã xuất bản sao lưu cấu hình ADB dạng JSON.", GREEN);
                new AlertDialog.Builder(this).setMessage("Đã tạo tệp sao lưu. Hãy lưu ở nơi an toàn; tệp có thể chứa địa chỉ mạng và lệnh riêng của bạn.")
                    .setPositiveButton("ĐÓNG", null).show();
            } catch (Exception e) { line("Không xuất được sao lưu: " + e.getMessage(), 0xffff8080); }
        } else if (requestCode == REQ_ADB_BACKUP_IMPORT) {
            new AlertDialog.Builder(this).setTitle("Khôi phục cấu hình ADB?")
                .setMessage("Dữ liệu trong tệp sẽ được gộp vào danh sách hiện tại; không tự kết nối hoặc chạy lệnh. Chỉ nhập tệp sao lưu BO Kali AI đáng tin cậy.")
                .setPositiveButton("NHẬP / GỘP", (d, w) -> importAdbBackup(uri))
                .setNegativeButton("HỦY", null).show();
        } else if (requestCode == PICK_CARPLAY_IDENTITY || requestCode == PICK_CARPLAY_CERTIFICATE) {
            final boolean identity = requestCode == PICK_CARPLAY_IDENTITY;
            final Uri selected = uri;
            worker.execute(() -> {
                try (java.io.InputStream in = getContentResolver().openInputStream(selected);
                     java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                    if (in == null) throw new IllegalStateException("Không mở được tệp.");
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (out.size() + n > 256 * 1024) throw new IllegalArgumentException("Tệp vượt giới hạn 256 KB.");
                        out.write(buffer, 0, n);
                    }
                    byte[] bytes = out.toByteArray();
                    if (identity) {
                        String ascii = new String(bytes, StandardCharsets.US_ASCII);
                        if (ascii.contains("-----BEGIN") || bytes.length < 64)
                            throw new IllegalArgumentException("identity.pk8 phải là khóa PKCS#8 DER, không phải PEM.");
                        CarPlayCredentialStore.save(this, "identity_pk8", bytes);
                    } else {
                        java.util.Collection<? extends java.security.cert.Certificate> parsed =
                            java.security.cert.CertificateFactory.getInstance("X.509")
                                .generateCertificates(new java.io.ByteArrayInputStream(bytes));
                        if (parsed.isEmpty()) throw new IllegalArgumentException("Không đọc được chứng chỉ X.509/PKCS#7.");
                        CarPlayCredentialStore.save(this, "certificate_p7b", bytes);
                    }
                    runOnUiThread(() -> {
                        line("Đã lưu " + (identity ? "identity.pk8" : "certificate.p7b") + " bằng Android Keystore.", GREEN);
                        manageCarPlayAuthorization();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> line("Lỗi nhập bộ xác thực: " + e.getMessage(), 0xffff8080));
                }
            });
        }
    }

    private void importAdbBackup(Uri uri) {
        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new java.io.IOException("Không đọc được tệp");
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096]; int count; int total = 0;
            while ((count = in.read(chunk)) != -1) {
                total += count;
                if (total > 1024 * 1024) throw new java.io.IOException("Tệp sao lưu vượt quá 1 MB");
                buffer.write(chunk, 0, count);
            }
            JSONObject rootJson = new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
            if (!"bo-kali-adb-backup".equals(rootJson.optString("format")) || rootJson.optInt("version") != 1)
                throw new IllegalArgumentException("Định dạng hoặc phiên bản sao lưu không được hỗ trợ");
            JSONObject data = rootJson.getJSONObject("data");
            android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();
            java.util.HashSet<String> importedSerials = new java.util.HashSet<>();
            String[] setKeys = {"serials", "discovered_endpoints", "pairing_endpoints"};
            for (String key : setKeys) {
                JSONArray values = data.optJSONArray(key);
                if (values == null) continue;
                java.util.HashSet<String> merged = new java.util.HashSet<>(prefs.getStringSet(key, new java.util.HashSet<>()));
                for (int i = 0; i < values.length(); i++) {
                    String value = values.optString(i, "").trim();
                    if (value.isEmpty() || value.length() > 512) continue;
                    if ("serials".equals(key)) {
                        if (value.matches("[A-Za-z0-9._:-]{1,200}")) {
                            merged.add(value);
                            importedSerials.add(value);
                        }
                    } else if (isValidAdbEndpoint(value)) {
                        merged.add(value);
                    }
                }
                editor.putStringSet(key, merged);
            }
            java.util.Iterator<String> keys = data.keys();
            int restored = 0;
            while (keys.hasNext()) {
                String key = keys.next();
                if ("serials".equals(key) || "discovered_endpoints".equals(key) || "pairing_endpoints".equals(key)) continue;
                if (key.length() > 600) continue;
                Object value = data.get(key);
                if ("last_scan_time".equals(key)) {
                    // Never restore a scan timestamp: it would make old connection data look current.
                    continue;
                } else if (key.startsWith("state_")) {
                    String serial = key.substring("state_".length());
                    if (!serial.matches("[A-Za-z0-9._:-]{1,200}") || !(value instanceof String)) continue;
                    String state = (String) value;
                    if (!("device".equals(state) || "offline".equals(state)
                        || "unauthorized".equals(state) || "not_listed".equals(state))) continue;
                    editor.putString(key, state);
                } else if (key.startsWith("seen_")) {
                    String serial = key.substring("seen_".length());
                    if (!serial.matches("[A-Za-z0-9._:-]{1,200}") || !(value instanceof Number)) continue;
                    long timestamp = ((Number) value).longValue();
                    if (timestamp < 0L) continue;
                    editor.putLong(key, timestamp);
                } else if (key.startsWith("alias_") || key.startsWith("preset_")) {
                    String serial = key.substring(key.indexOf('_') + 1);
                    if (!serial.matches("[A-Za-z0-9._:-]{1,200}") || !(value instanceof String)) continue;
                    String stringValue = (String) value;
                    int limit = key.startsWith("preset_") ? 2000 : 80;
                    editor.putString(key, stringValue.substring(0, Math.min(stringValue.length(), limit)));
                } else if (key.startsWith("service_") || key.startsWith("pairing_service_")) {
                    String prefix = key.startsWith("pairing_service_") ? "pairing_service_" : "service_";
                    String endpoint = key.substring(prefix.length());
                    if (!isValidAdbEndpoint(endpoint) || !(value instanceof String)) continue;
                    String serviceName = (String) value;
                    editor.putString(key, serviceName.substring(0, Math.min(serviceName.length(), 160)));
                } else if (key.startsWith("discovered_at_") || key.startsWith("pairing_discovered_at_")) {
                    String prefix = key.startsWith("pairing_discovered_at_")
                        ? "pairing_discovered_at_" : "discovered_at_";
                    String endpoint = key.substring(prefix.length());
                    if (!isValidAdbEndpoint(endpoint) || !(value instanceof Number)) continue;
                    long timestamp = ((Number) value).longValue();
                    if (timestamp < 0L) continue;
                    editor.putLong(key, timestamp);
                } else continue;
                restored++;
            }
            // Trạng thái trực tuyến trong bản sao lưu có thể đã cũ; yêu cầu quét lại trước khi dùng.
            for (String serial : importedSerials) editor.putString("state_" + serial, "not_listed");
            editor.remove("last_scan_time");
            editor.apply();
            line("Đã khôi phục/gộp cấu hình ADB. Trạng thái thiết bị được đánh dấu cần quét lại; không tự kết nối hoặc chạy lệnh.", GREEN);
            new AlertDialog.Builder(this).setMessage("Đã nhập dữ liệu sao lưu. Hãy quét ADB lại để cập nhật trạng thái trực tuyến.")
                .setPositiveButton("ĐÓNG", null).show();
        } catch (Exception e) { line("Không khôi phục được sao lưu: " + e.getMessage(), 0xffff8080); }
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
                    java.util.Set<String> saved = new java.util.HashSet<>(
                        prefs.getStringSet("serials", new java.util.HashSet<>()));
                    android.content.SharedPreferences.Editor editor = prefs.edit().remove("serials");
                    for (String serial : saved) {
                        editor.remove("state_" + serial)
                            .remove("seen_" + serial)
                            .remove("alias_" + serial)
                            .remove("preset_" + serial);
                    }
                    editor.apply();
                    line("Đã xóa " + saved.size() + " thiết bị cùng trạng thái, tên gợi nhớ và lệnh lưu tương ứng. Không ngắt kết nối ADB và không tác động thiết bị.", GREEN);
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
                    .setItems(new String[]{"Kiểm tra kết nối thiết bị này", "Sao chép serial ADB", "Đặt / đổi tên gợi nhớ", "Lưu / chạy lệnh thường dùng", "Báo cáo chẩn đoán tổng hợp", "Đọc Logcat gần đây", "Xuất Logcat gần nhất thành TXT", "Liệt kê ứng dụng", "Mở ứng dụng theo package", "Ngắt kết nối ADB", "Xóa riêng thiết bị khỏi danh sách lưu"}, (dialog, which) -> {
                        if (which == 0) checkSavedAdbDevice(serial);
                        else if (which == 1) copyText(serial, "Đã sao chép serial ADB.");
                        else if (which == 2) showAdbAliasDialog(serial);
                        else if (which == 3) showAdbCommandPreset(serial);
                        else if (which == 4) showAdbFullDiagnosticReport(serial);
                        else if (which == 5) showAdbLogcatDialog(serial);
                        else if (which == 6) exportLastAdbLogcat(serial);
                        else if (which == 7) showAdbInstalledApps(serial);
                        else if (which == 8) showAdbLaunchAppDialog(serial);
                        else if (which == 9) confirmAdbDisconnect(serial);
                        else confirmForgetAdbDevice(serial);
                    }).setNegativeButton("ĐÓNG", null).show();
            }));
        actions.show();
    }

    // Read-only inventory of third-party packages installed on the selected authorized device.
    private void showAdbInstalledApps(String serial) {
        final String[] labels = {
            "Ứng dụng người dùng (bên thứ ba)",
            "Ứng dụng hệ thống",
            "Tất cả ứng dụng"
        };
        final String[] flags = {"-3", "-s", ""};
        new AlertDialog.Builder(this)
            .setTitle("Danh sách ứng dụng • " + serial)
            .setItems(labels, (dialog, which) -> {
                final String selectedLabel = labels[which];
                final String selectedFlag = flags[which];
                String command = "adb -s " + shellQuote(serial)
                    + " shell pm list packages -f"
                    + (selectedFlag.isEmpty() ? "" : " " + selectedFlag);
                new AlertDialog.Builder(this)
                    .setTitle("Xác nhận đọc danh sách")
                    .setMessage("Thiết bị: " + serial + "\nLoại: " + selectedLabel
                        + "\n\nChỉ đọc tên package và đường dẫn APK nếu Android cho phép. Không cài, gỡ hoặc thay đổi ứng dụng. Danh sách có thể chứa thông tin riêng tư.")
                    .setPositiveButton("ĐỌC", (d, w) -> {
                        line("Đang đọc " + selectedLabel.toLowerCase() + " trên " + serial + "...", GREEN);
                        runTermuxCommand(command);
                    })
                    .setNegativeButton("HỦY", null)
                    .show();
            })
            .setNegativeButton("ĐÓNG", null)
            .show();
    }

    // Launch a user-selected launcher activity on the explicitly selected authorized device.
    private void showAdbLaunchAppDialog(String serial) {
        EditText packageInput = input("Tên package, ví dụ com.android.settings");
        new AlertDialog.Builder(this)
            .setTitle("Mở ứng dụng trên thiết bị")
            .setMessage("Thiết bị: " + serial + "\nNhập package đã cài. BO Kali AI chỉ gửi yêu cầu mở ứng dụng; không cài/gỡ hoặc thay đổi dữ liệu.")
            .setView(packageInput)
            .setPositiveButton("TIẾP TỤC", (d, w) -> {
                String packageName = packageInput.getText().toString().trim();
                if (!packageName.matches("[A-Za-z0-9_]+(\\\\.[A-Za-z0-9_]+)+")) {
                    line("Tên package không hợp lệ. Ví dụ: com.android.settings", 0xffffca70);
                    return;
                }
                String command = "adb -s " + shellQuote(serial)
                    + " shell monkey -p " + shellQuote(packageName)
                    + " -c android.intent.category.LAUNCHER 1";
                new AlertDialog.Builder(this)
                    .setTitle("Xác nhận mở ứng dụng")
                    .setMessage("Thiết bị: " + serial + "\\nPackage: " + packageName
                        + "\\n\\nLệnh sẽ yêu cầu Android mở ứng dụng nếu package có màn hình Launcher. Không xóa dữ liệu hay thay đổi cài đặt.")
                    .setPositiveButton("MỞ ỨNG DỤNG", (confirm, choice) -> {
                        line("Đang yêu cầu mở " + packageName + " trên " + serial + "...", GREEN);
                        runTermuxCommand(command);
                    })
                    .setNegativeButton("HỦY", null)
                    .show();
            })
            .setNegativeButton("HỦY", null)
            .show();
    }

    // Read a bounded recent logcat snapshot; never clears logs or changes device state.
    private void showAdbLogcatDialog(String serial) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(1);
        fields.setPadding(24, 8, 24, 8);
        android.content.SharedPreferences filterPrefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
        EditText countInput = input("Số dòng log (50–1000, mặc định 300)");
        countInput.setText(filterPrefs.getString("logcat_count_" + serial, "300"));
        EditText priorityInput = input("Mức lọc: V, D, I, W, E hoặc F (mặc định V)");
        priorityInput.setText(filterPrefs.getString("logcat_priority_" + serial, "V"));
        EditText bufferInput = input("Log buffer: main, system, crash, events, radio (mặc định main)");
        bufferInput.setText(filterPrefs.getString("logcat_buffer_" + serial, "main"));
        EditText tagInput = input("Lọc theo Log tag (để trống = mọi tag)");
        tagInput.setText(filterPrefs.getString("logcat_tag_" + serial, ""));
        EditText packageInput = input("Lọc theo package (để trống = tất cả ứng dụng)");
        packageInput.setText(filterPrefs.getString("logcat_package_" + serial, ""));
        fields.addView(countInput);
        fields.addView(priorityInput);
        fields.addView(bufferInput);
        fields.addView(tagInput);
        fields.addView(packageInput);
        new AlertDialog.Builder(this)
            .setTitle("Đọc Logcat • " + serial)
            .setMessage("Logcat có thể chứa tên ứng dụng, địa chỉ, mã phiên hoặc dữ liệu riêng tư. Giới hạn 1.000 dòng; thao tác chỉ đọc, không xóa log hay thay đổi thiết bị.\n\nMức lọc: V=tất cả, D=debug, I=info, W=cảnh báo, E=lỗi, F=lỗi nghiêm trọng.")
            .setView(fields)
            .setPositiveButton("TIẾP TỤC", (d, w) -> {
                String raw = countInput.getText().toString().trim();
                int count;
                try { count = raw.isEmpty() ? 300 : Integer.parseInt(raw); }
                catch (NumberFormatException e) {
                    line("Số dòng Logcat không hợp lệ.", 0xffff8080);
                    return;
                }
                if (count < 50 || count > 1000) {
                    line("Chỉ chấp nhận từ 50 đến 1.000 dòng để giới hạn dữ liệu.", 0xffffca70);
                    return;
                }
                String priority = priorityInput.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);
                if (priority.isEmpty()) priority = "V";
                if (!priority.matches("[VDIWEF]")) {
                    line("Mức lọc phải là V, D, I, W, E hoặc F.", 0xffffca70);
                    return;
                }
                final String selectedPriority = priority;
                String buffer = bufferInput.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
                if (buffer.isEmpty()) buffer = "main";
                if (!buffer.matches("main|system|crash|events|radio")) {
                    line("Log buffer chỉ nhận main, system, crash, events hoặc radio.", 0xffffca70);
                    return;
                }
                final String selectedBuffer = buffer;
                String tag = tagInput.getText().toString().trim();
                if (!tag.isEmpty() && !tag.matches("[A-Za-z0-9_.-]{1,80}")) {
                    line("Log tag không hợp lệ. Chỉ dùng chữ, số, dấu chấm, gạch dưới hoặc gạch ngang (tối đa 80 ký tự).", 0xffffca70);
                    return;
                }
                final String selectedTag = tag;
                String packageName = packageInput.getText().toString().trim();
                if (!packageName.isEmpty() && !packageName.matches("[A-Za-z0-9_]+(\\\\.[A-Za-z0-9_]+)+")) {
                    line("Tên package không hợp lệ. Ví dụ: com.android.settings", 0xffffca70);
                    return;
                }
                final String selectedPackage = packageName;
                String filter = (selectedTag.isEmpty() ? "*:" : selectedTag + ":") + selectedPriority;
                String command;
                if (selectedPackage.isEmpty()) {
                    command = "adb -s " + shellQuote(serial)
                        + " logcat -b " + selectedBuffer + " -d -v threadtime -t " + count + " " + shellQuote(filter);
                } else {
                    String pidLookup = "pid=$(adb -s " + shellQuote(serial) + " shell pidof "
                        + shellQuote(selectedPackage) + " 2>/dev/null | tr -d '\\r' | tr ' ' ','); "
                        + "if [ -n \"$pid\" ]; then adb -s " + shellQuote(serial)
                        + " logcat -b " + selectedBuffer + " -d -v threadtime -t " + count + " --pid=\"$pid\" "
                        + shellQuote(filter)
                        + "; else echo 'Không tìm thấy PID đang chạy cho package " + selectedPackage + "'; fi";
                    command = pidLookup;
                }
                new AlertDialog.Builder(this)
                    .setTitle("Xác nhận đọc Logcat")
                    .setMessage("Thiết bị: " + serial + "\\nSố dòng tối đa: " + count
                        + "\\nMức lọc: " + selectedPriority
                        + "\\nLog buffer: " + selectedBuffer
                        + "\\nLog tag: " + (selectedTag.isEmpty() ? "Tất cả" : selectedTag)
                        + "\\nPackage: " + (selectedPackage.isEmpty() ? "Tất cả ứng dụng" : selectedPackage)
                        + "\\n\\nLệnh chỉ đọc log, không dùng logcat -c. Lọc package dựa trên PID của ứng dụng đang chạy; ứng dụng chưa chạy có thể không có kết quả.")
                    .setPositiveButton("ĐỌC LOG", (x, y) -> {
                        line("Đang đọc tối đa " + count + " dòng Logcat (buffer " + selectedBuffer + ", mức " + selectedPriority
                            + (selectedTag.isEmpty() ? "" : ", tag " + selectedTag)
                            + (selectedPackage.isEmpty() ? "" : ", package " + selectedPackage) + ") từ " + serial + "...", GREEN);
                        filterPrefs.edit()
                            .putString("logcat_count_" + serial, String.valueOf(count))
                            .putString("logcat_priority_" + serial, selectedPriority)
                            .putString("logcat_buffer_" + serial, selectedBuffer)
                            .putString("logcat_tag_" + serial, selectedTag)
                            .putString("logcat_package_" + serial, selectedPackage)
                            .apply();
                        getSharedPreferences("bo_kali_results", MODE_PRIVATE).edit()
                            .putString("last_logcat_serial", serial)
                            .putLong("last_logcat_requested_at", System.currentTimeMillis() - 1)
                            .apply();
                        runTermuxCommand(command);
                    })
                    .setNegativeButton("HỦY", null)
                    .show();
            })
            .setNeutralButton("ĐẶT LẠI BỘ LỌC", (d, w) -> {
                filterPrefs.edit()
                    .remove("logcat_count_" + serial)
                    .remove("logcat_priority_" + serial)
                    .remove("logcat_buffer_" + serial)
                    .remove("logcat_tag_" + serial)
                    .remove("logcat_package_" + serial)
                    .apply();
                line("Đã xóa bộ lọc Logcat đã lưu riêng cho " + serial + ". Mở lại Logcat để dùng giá trị mặc định.", GREEN);
            })
            .setNegativeButton("HỦY", null)
            .show();
    }

    // Export only the most recent result that arrived after a Logcat request for this device.
    private void exportLastAdbLogcat(String serial) {
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_results", MODE_PRIVATE);
        String savedSerial = prefs.getString("last_logcat_serial", "");
        long requestedAt = prefs.getLong("last_logcat_requested_at", 0L);
        long outputAt = prefs.getLong("last_output_at", 0L);
        String report = prefs.getString("last_output", "");
        if (!serial.equals(savedSerial) || requestedAt <= 0L || outputAt < requestedAt
                || report == null || report.trim().isEmpty()) {
            line("Chưa có kết quả Logcat mới cho thiết bị này. Hãy chọn Đọc Logcat gần đây và đợi Termux trả kết quả.", 0xffffca70);
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Xuất Logcat • " + serial)
            .setMessage("Logcat có thể chứa dữ liệu riêng tư, mã phiên hoặc thông tin mạng. Bạn có thể xuất nguyên bản hoặc ẩn IP và serial ADB. Việc ẩn tự động không loại bỏ mọi dữ liệu nhạy cảm.")
            .setItems(new String[]{"Xuất nguyên bản", "Ẩn IP và serial ADB rồi xuất"}, (d, which) -> {
                pendingExportText = which == 0 ? report : redactNetworkIdentifiers(report);
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TITLE, "bo-kali-logcat.txt");
                try {
                    startActivityForResult(intent, REQ_RESULT_EXPORT);
                } catch (Exception e) {
                    pendingExportText = "";
                    line("Không mở được trình lưu Logcat: " + e.getMessage(), 0xffff8080);
                }
            })
            .setNegativeButton("HỦY", null)
            .show();
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

    private void showAdbCommandPreset(String serial) {
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_adb_devices", MODE_PRIVATE);
        String saved = prefs.getString("preset_" + serial, "");
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(1);
        form.setPadding(dp(16), dp(8), dp(16), 0);
        EditText command = input("Ví dụ: getprop ro.product.model");
        command.setSingleLine(false);
        command.setMinLines(2);
        command.setText(saved);
        form.addView(command);
        new AlertDialog.Builder(this).setTitle("Lệnh thường dùng • " + serial)
            .setMessage("Lưu một lệnh riêng cho thiết bị này. Lệnh chỉ chạy khi bạn nhấn CHẠY và xác nhận ở bước tiếp theo. Không lưu mật khẩu hoặc mã bí mật.")
            .setView(form)
            .setPositiveButton("LƯU LỆNH", (d, w) -> {
                String value = command.getText().toString().trim();
                if (value.length() > 2000) {
                    line("Lệnh quá dài (tối đa 2.000 ký tự).", 0xffff8080);
                    return;
                }
                if (value.isEmpty()) prefs.edit().remove("preset_" + serial).apply();
                else prefs.edit().putString("preset_" + serial, value).apply();
                line(value.isEmpty() ? "Đã xóa lệnh thường dùng của " + serial : "Đã lưu lệnh thường dùng cho " + serial, GREEN);
            })
            .setNeutralButton(saved.isEmpty() ? "ĐÓNG" : "CHẠY LỆNH ĐÃ LƯU", (d, w) -> {
                if (!saved.isEmpty()) confirmRunAdbPreset(serial, saved);
            })
            .setNegativeButton("HỦY", null).show();
    }

    private void confirmRunAdbPreset(String serial, String command) {
        new AlertDialog.Builder(this).setTitle("Xác nhận lệnh đã lưu")
            .setMessage("Thiết bị: " + serial + "\\nLệnh: " + command
                + "\\n\\nLệnh có thể thay đổi dữ liệu hoặc cài đặt trên thiết bị. Hãy đọc kỹ trước khi chạy.")
            .setPositiveButton("CHẠY", (d, w) ->
                runTermuxCommand("adb -s " + shellQuote(serial) + " shell " + shellQuote(command)))
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
                    .remove("state_" + serial).remove("seen_" + serial).remove("alias_" + serial).remove("preset_" + serial).apply();
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
            "Thông tin CPU",
            "RAM hệ thống",
            "Tiến trình đang chạy (tối đa 80)"
        };
        String[] commands = {
            "echo 'MODEL'; getprop ro.product.model; echo 'BRAND'; getprop ro.product.brand; echo 'ANDROID'; getprop ro.build.version.release; echo 'SDK'; getprop ro.build.version.sdk; echo 'BUILD'; getprop ro.build.display.id; echo 'PATCH'; getprop ro.build.version.security_patch",
            "dumpsys battery",
            "df -h",
            "ip addr",
            "pm list packages -3",
            "echo 'DISPLAY'; wm size; wm density; echo 'REFRESH RATE'; dumpsys display | grep -i 'refreshRate\\|fps' | head -20",
            "echo 'UPTIME'; uptime; cat /proc/uptime",
            "echo 'CPU'; getprop ro.product.cpu.abi; echo 'CORES'; grep -c '^processor' /proc/cpuinfo; echo 'SOC'; getprop ro.soc.model; echo 'HARDWARE'; getprop ro.hardware",
            "echo 'SYSTEM MEMORY'; cat /proc/meminfo | head -20",
            "echo 'RUNNING PROCESSES (MAX 80)'; ps -A | head -80"
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
        final String[] actions = {
            "KIỂM TRA TERMUX / KALI",
            "VÀO KALI TRỰC TIẾP",
            "CÀI KALI ROOTLESS",
            "HƯỚNG DẪN THIẾT LẬP"
        };
        new AlertDialog.Builder(this)
            .setTitle("Kali Shell • NetHunter Rootless")
            .setMessage("BO Kali AI dùng Termux làm bộ chạy shell Linux. Kiểm tra môi trường trước khi chạy lệnh; cài lần đầu cần Internet và dung lượng trống lớn. Không cần root.")
            .setItems(actions, (dialog, which) -> {
                if (which == 0) checkKaliEnvironment();
                else if (which == 1) runKaliDirectly();
                else if (which == 2) {
                    copyText(KALI_INSTALL_COMMAND, "Đã sao chép lệnh cài Kali. Dán vào Termux để bắt đầu.");
                    openTermux();
                } else showKaliInstructions();
            })
            .setNegativeButton("ĐÓNG", null)
            .show();
    }

    // Read-only environment check; it never installs packages or changes the system.
    private void checkKaliEnvironment() {
        String command =
            "echo '=== TERMUX ==='; " +
            "echo \"PREFIX=$PREFIX\"; " +
            "if [ -x \"$PREFIX/bin/bash\" ]; then echo 'TERMUX_SHELL=OK'; else echo 'TERMUX_SHELL=MISSING'; fi; " +
            "echo '=== NETHUNTER LAUNCHER ==='; " +
            "if command -v nh >/dev/null 2>&1; then command -v nh; echo 'NETHUNTER_LAUNCHER=FOUND'; " +
            "echo '=== KALI ROOTFS ==='; " +
            "if nh bash -lc 'if [ -r /etc/os-release ]; then . /etc/os-release; printf \"KALI_OS=%s\\n\" \"$PRETTY_NAME\"; else echo KALI_OS_FILE=MISSING; exit 2; fi; id; uname -m'; then " +
            "echo 'KALI_ROOTFS=READY'; else echo 'KALI_ROOTFS=ERROR'; fi; " +
            "else echo 'NETHUNTER_LAUNCHER=MISSING'; echo 'Kali chưa được cài hoặc lệnh nh chưa có trong PATH.'; fi; " +
            "echo '=== TERMUX STORAGE ==='; df -h \"$HOME\" 2>&1 | tail -n 2; " +
            "echo '=== TERMUX ARCH ==='; uname -m";
        line("Đang kiểm tra Termux và Kali NetHunter Rootless (chỉ đọc)...", GREEN);
        runTermuxCommand(command);
    }


    private void confirmKaliCommand() {
        String command = commandInput.getText().toString().trim();
        if (command.isEmpty()) {
            line("Nhập lệnh Kali trước. Ví dụ: uname -a hoặc ip addr", 0xffffca70);
            return;
        }
        if (command.length() > 8192 || command.indexOf(0) >= 0) {
            line("Lệnh quá dài hoặc chứa ký tự không hợp lệ (tối đa 8.192 ký tự).", 0xffff8080);
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Xác nhận chạy lệnh Kali")
            .setMessage("Lệnh sẽ chạy trong Kali NetHunter Rootless qua Termux:\n\n" + command
                + "\n\nChỉ chạy lệnh bạn hiểu và trên thiết bị bạn có quyền quản lý. Kết quả sẽ được gửi về BO Kali AI.")
            .setPositiveButton("Chạy lệnh", (d, w) -> runKaliCommand(command))
            .setNegativeButton("Hủy", null).show();
    }

    private void runKaliCommand(String command) {
        if (command == null || command.trim().isEmpty() || command.length() > 8192 || command.indexOf(0) >= 0) {
            line("Lệnh Kali không hợp lệ hoặc vượt giới hạn 8.192 ký tự.", 0xffff8080);
            return;
        }
        try {
            int request = TERMUX_REQUEST.incrementAndGet();
            Intent callback = new Intent(this, TermuxResultReceiver.class);
            PendingIntent resultPending = PendingIntent.getBroadcast(this, request, callback,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            Intent intent = new Intent("com.termux.RUN_COMMAND");
            intent.setClassName("com.termux", "com.termux.app.RunCommandService");
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            // NetHunter accepts a command after "nh". Pass it as a bash -lc payload
            // so spaces, pipes and multi-word commands remain one safely quoted argument.
            String kaliCommand = "nh bash -lc " + shellQuote(command);
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", kaliCommand});
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            intent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", resultPending);
            intent.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "BO Kali AI - lệnh do người dùng xác nhận");
            startService(intent);
            line("Đã gửi lệnh vào môi trường Kali: " + command, GREEN);
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
                + "\n[Đã rút gọn kết quả còn 12.000 ký tự để AI phân tích ổn định.]";
        }
        questionInput.setText("Hãy giải thích kết quả/lỗi Kali dưới đây bằng tiếng Việt, chỉ ra nguyên nhân, mức độ rủi ro và cách xử lý an toàn. Không giả định lệnh đã làm gì ngoài nội dung kết quả:\n\n" + outputForAi);
        askAi();
    }


    private boolean adbMirrorResumePending = false;
    private int adbMirrorResumeRetries = 0;

    private void scheduleScrcpyResumeRetry(String output) {
        if (output == null || !output.contains("BO_SCRCPY_ERROR:")) return;
        if (adbMirrorResumeRetries >= 2) {
            line("Không thể tự khôi phục scrcpy sau 2 lần thử. ADB vẫn được giữ; hãy kiểm tra Wi-Fi/thiết bị rồi thử truyền lại.", 0xffffca70);
            return;
        }
        android.content.SharedPreferences session =
            getSharedPreferences("bo_kali_adb_session", MODE_PRIVATE);
        final String expectedSerial = session.getString("active_serial", "");
        final String expectedRequestToken = session.getString("active_request_token", "");
        if (expectedSerial == null || expectedSerial.isEmpty()
            || expectedRequestToken == null || expectedRequestToken.isEmpty()) return;
        cancelScrcpyResumeRetry();
        adbMirrorResumeRetries++;
        final int attempt = adbMirrorResumeRetries;
        line("Khôi phục scrcpy chưa thành công. Sẽ tự thử lại lần " + attempt + "/2 sau 5 giây.", 0xffffca70);
        scrcpyResumeRetryRunnable = () -> {
            scrcpyResumeRetryRunnable = null;
            android.content.SharedPreferences current =
                getSharedPreferences("bo_kali_adb_session", MODE_PRIVATE);
            if (!current.getBoolean("keep_alive", false)
                || !expectedSerial.equals(current.getString("active_serial", ""))
                || !expectedRequestToken.equals(current.getString("active_request_token", ""))
                || !expectedSerial.equals(dualWorkspaceSerial)
                || activeMirrorView == null || activeMirrorView.isRunning()
                || adbMirrorResumePending) return;
            resumeAdbMirrorAfterUnlock();
        };
        mainHandler.postDelayed(scrcpyResumeRetryRunnable, 5000L);
    }

    @Override protected void onResume() {
        super.onResume();
        android.content.SharedPreferences prefs = getSharedPreferences("bo_kali_results", MODE_PRIVATE);
        String pending = prefs.getString("pending_output", null);
        boolean pendingScreenCapture = prefs.getBoolean("pending_screen_capture", false);
        boolean pendingScrcpyReady = prefs.getBoolean("pending_scrcpy_ready", false);
        String pendingScrcpyToken = prefs.getString("pending_scrcpy_token", "");
        if ((pending != null && !pending.isEmpty()) || pendingScreenCapture || pendingScrcpyReady) {
            prefs.edit().remove("pending_output").remove("pending_screen_capture")
                .remove("pending_scrcpy_ready").remove("pending_scrcpy_token").apply();
            if (pending != null && !pending.isEmpty()) {
                lastCommandOutput = pending;
                handleAdbDevicesOutput(pending);
                line("KẾT QUẢ TỪ KALI / TERMUX:", GREEN);
                for (String part : pending.split("\\n")) line(part, WHITE);
                line("Nhấn GIẢI THÍCH KẾT QUẢ KALI BẰNG AI để phân tích.", 0xff79c9ff);
            }
            if (pendingScreenCapture) showDualWorkspace();
            if (pendingScrcpyReady) {
                android.content.SharedPreferences session =
                    getSharedPreferences("bo_kali_adb_session", MODE_PRIVATE);
                boolean currentRequest = !pendingScrcpyToken.isEmpty()
                    && pendingScrcpyToken.equals(session.getString("active_request_token", ""));
                adbMirrorResumePending = false;
                if (currentRequest) {
                    adbMirrorResumeRetries = 0;
                    String readySerial = session.getString("active_serial", "");
                    if (activeMirrorView != null && readySerial != null
                        && readySerial.equals(dualWorkspaceSerial)
                        && readySerial.equals(activeMirrorSerial)) {
                        activeMirrorView.start(session.getInt("active_port", scrcpyPortFor(readySerial)));
                    }
                } else {
                    line("Bỏ qua kết quả scrcpy cũ vì phiên thiết bị đã thay đổi.", 0xffffca70);
                }
            } else if (pending != null) {
                android.content.SharedPreferences session =
                    getSharedPreferences("bo_kali_adb_session", MODE_PRIVATE);
                boolean currentRequest = !pendingScrcpyToken.isEmpty()
                    && pendingScrcpyToken.equals(session.getString("active_request_token", ""));
                adbMirrorResumePending = false;
                if (currentRequest) scheduleScrcpyResumeRetry(pending);
            }
        }
        resumeAdbMirrorAfterUnlock();
    }

    /**
     * A SurfaceView may be destroyed while the phone is locked. The foreground service
     * keeps the ADB session alive, while this method recreates the scrcpy server/forward
     * after the Activity becomes visible again. It never disconnects or kills ADB.
     */
    private void resumeAdbMirrorAfterUnlock() {
        android.content.SharedPreferences session =
            getSharedPreferences("bo_kali_adb_session", MODE_PRIVATE);
        if (!session.getBoolean("keep_alive", false)) return;
        String serial = session.getString("active_serial", "");
        if (serial == null || serial.isEmpty() || activeMirrorView == null) return;
        // Never attach a background session to a different device's visible workspace.
        if (dualWorkspaceSerial == null || !serial.equals(dualWorkspaceSerial)) return;
        activeMirrorSerial = serial;
        if (activeMirrorView.isRunning() || adbMirrorResumePending) return;

        adbMirrorResumePending = true;
        activeMirrorView.prepareForReconnect();
        final String scrcpyRequestToken = java.util.UUID.randomUUID().toString();
        session.edit().putString("active_request_token", scrcpyRequestToken).apply();
        final int mirrorPort = scrcpyPortFor(serial);
        final String mirrorId = scrcpyIdFor(serial);
        session.edit().putInt("active_port", mirrorPort).putString("active_scrcpy_id", mirrorId).apply();
        String serialArg = shellQuote(serial);
        String remoteStart = "CLASSPATH=/data/local/tmp/bo-kali-scrcpy-server.jar app_process / com.genymobile.scrcpy.Server 4.0 scid=" + mirrorId + " tunnel_forward=true audio=false video_codec=h264 max_size=1280 video_bit_rate=4000000 max_fps=30 send_device_meta=false send_dummy_byte=false cleanup=true >/dev/null 2>&1 </dev/null &";
        String socketName = "scrcpy_" + mirrorId;
        String portArg = "tcp:" + mirrorPort;
        String socketCheck = "grep -F '" + socketName + "' /proc/net/unix >/dev/null 2>&1";
        String waitForSocket = "for i in 1 2 3 4 5 6 7 8 9 10; do grep -F '" + socketName + "' /proc/net/unix >/dev/null 2>&1 && exit 0; sleep 1; done; exit 1";
        String command = "echo BO_SCRCPY_REQUEST:" + scrcpyRequestToken + "; adb -s " + serialArg + " get-state >/dev/null 2>&1 || { echo 'BO_SCRCPY_ERROR: Thiết bị ADB chưa trực tuyến'; exit 1; }; "
            + "adb -s " + serialArg + " forward --remove tcp:" + mirrorPort + " >/dev/null 2>&1 || true; "
            + "adb -s " + serialArg + " forward tcp:" + mirrorPort + " localabstract:" + socketName + " || exit 1; "
            + "if ! adb -s " + serialArg + " shell " + shellQuote(socketCheck) + "; then "
            + "adb -s " + serialArg + " shell " + shellQuote(remoteStart) + "; fi; "
            + "if adb -s " + serialArg + " shell " + shellQuote(waitForSocket) + "; then "
            + "echo BO_SCRCPY_READY:" + scrcpyRequestToken + ":" + mirrorPort + "; else echo 'BO_SCRCPY_ERROR: Không khôi phục được socket scrcpy'; exit 1; fi";
        runTermuxCommand(command);
        line("Đang khôi phục luồng scrcpy sau khi mở khóa; phiên ADB vẫn được giữ nguyên.", 0xff79c9ff);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && "com.bokaliai.terminal.TERMUX_RESULT".equals(intent.getAction())) {
            // This callback consumes the same result that was persisted by the receiver.
            // Clear it here so a later onResume does not replay the transcript twice.
            getSharedPreferences("bo_kali_results", MODE_PRIVATE).edit()
                .remove("pending_output").remove("pending_screen_capture")
                .remove("pending_scrcpy_ready").remove("pending_scrcpy_token").apply();
            lastCommandOutput = intent.getStringExtra("termux_output");
            if (lastCommandOutput == null) lastCommandOutput = "Termux trả về kết quả trống.";
            if (intent.getBooleanExtra("bo_screen_capture", false)) showDualWorkspace();
            if (adbMirrorResumePending) adbMirrorResumePending = false;
            if (intent.getBooleanExtra("bo_scrcpy_ready", false)) {
                android.content.SharedPreferences session =
                    getSharedPreferences("bo_kali_adb_session", MODE_PRIVATE);
                String resultToken = intent.getStringExtra("bo_scrcpy_token");
                boolean currentRequest = resultToken != null && !resultToken.isEmpty()
                    && resultToken.equals(session.getString("active_request_token", ""));
                adbMirrorResumePending = false;
                if (currentRequest) {
                    adbMirrorResumeRetries = 0;
                    String readySerial = session.getString("active_serial", "");
                    if (activeMirrorView != null && readySerial != null
                        && readySerial.equals(dualWorkspaceSerial)
                        && readySerial.equals(activeMirrorSerial)) {
                        activeMirrorView.start(session.getInt("active_port", scrcpyPortFor(readySerial)));
                    }
                } else {
                    line("Bỏ qua kết quả scrcpy cũ vì phiên thiết bị đã thay đổi.", 0xffffca70);
                }
            } else {
                android.content.SharedPreferences session =
                    getSharedPreferences("bo_kali_adb_session", MODE_PRIVATE);
                String resultToken = intent.getStringExtra("bo_scrcpy_token");
                if (resultToken != null && resultToken.equals(session.getString("active_request_token", ""))) {
                    scheduleScrcpyResumeRetry(lastCommandOutput);
                }
            }
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

    private void exportLastResult() {
        String report = getSharedPreferences("bo_kali_results", MODE_PRIVATE)
            .getString("last_output", "");
        if (report == null || report.trim().isEmpty()) report = lastCommandOutput;
        if (report == null || report.trim().isEmpty()) {
            line("Chưa có kết quả ADB / Kali để xuất. Hãy chạy lệnh hoặc kiểm tra thiết bị trước.", 0xffffca70);
            return;
        }
        final String original = report;
        new AlertDialog.Builder(this)
            .setTitle("Bảo vệ dữ liệu báo cáo")
            .setMessage("Báo cáo có thể chứa serial ADB, địa chỉ IP, tên thiết bị hoặc dữ liệu riêng tư. Bạn có thể xuất nguyên bản hoặc ẩn IP và serial trước khi lưu. Việc ẩn tự động không bảo đảm loại bỏ mọi thông tin cá nhân.")
            .setItems(new String[]{"Xuất nguyên bản", "Ẩn IP và serial ADB rồi xuất"}, (dialog, which) -> {
                pendingExportText = which == 0 ? original : redactNetworkIdentifiers(original);
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TITLE, "bo-kali-ai-report.txt");
                try {
                    startActivityForResult(intent, REQ_RESULT_EXPORT);
                } catch (Exception e) {
                    pendingExportText = "";
                    line("Không mở được trình lưu tệp: " + e.getMessage(), 0xffff8080);
                }
            })
            .setNegativeButton("HỦY", null)
            .show();
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

    // CarPlay receiver onboarding. This UI exposes both connection paths without
    // pretending that Android Bluetooth/USB alone implements Apple's authenticated protocol.

    private static final int PICK_CARPLAY_IDENTITY = 7311;
    private static final int PICK_CARPLAY_CERTIFICATE = 7312;

    private void manageCarPlayAuthorization() {
        boolean hasIdentity = CarPlayCredentialStore.has(this, "identity_pk8");
        boolean hasCertificate = CarPlayCredentialStore.has(this, "certificate_p7b");
        new AlertDialog.Builder(this)
            .setTitle("Tự động xác thực CarPlay")
            .setMessage("identity.pk8: " + (hasIdentity ? "Đã lưu an toàn" : "Chưa nhập")
                + "\\ncertificate.p7b: " + (hasCertificate ? "Đã lưu an toàn" : "Chưa nhập")
                + "\\n\\nNhập bộ xác thực được cấp phép một lần. BO lưu mã hóa trong vùng riêng và có thể đọc lại khi receiver khởi tạo phiên. Khóa riêng không được đóng gói vào APK/GitHub.")
            .setPositiveButton("CHỌN identity.pk8", (d, w) -> chooseCarPlayAsset(true))
            .setNeutralButton("CHỌN certificate.p7b", (d, w) -> chooseCarPlayAsset(false))
            .setNegativeButton("ĐÓNG", null).show();
    }

    private void chooseCarPlayAsset(boolean identity) {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");
        startActivityForResult(pick, identity ? PICK_CARPLAY_IDENTITY : PICK_CARPLAY_CERTIFICATE);
    }

    private void showCarPlayReceiverSetup() {
        final String[] modes = {
            "ƯU TIÊN KHÔNG DÂY • USB DỰ PHÒNG",
            "KẾT NỐI KHÔNG DÂY",
            "KẾT NỐI USB"
        };
        new AlertDialog.Builder(this)
            .setTitle("BO CarPlay Receiver • iPhone")
            .setMessage("Chọn cách ghép iPhone. Hai phương thức đều được hiển thị trong BO Kali AI.\n\n"
                + "• Không dây: Bluetooth dùng để phát hiện/ghép thiết bị; Wi-Fi cần cho luồng dữ liệu.\n"
                + "• USB: cần Android có USB host/OTG và phần cứng nhận CarPlay tương thích.\n\n"
                + "LƯU Ý: Đây là trung tâm thiết lập kết nối. Chỉ mở Bluetooth hoặc cắm USB chưa biến Android thành đầu nhận CarPlay. Hiển thị CarPlay thật cần receiver protocol và xác thực/phần cứng hợp lệ; app hiện chưa có chứng nhận MFi hay bộ giải mã luồng CarPlay.")
            .setItems(modes, (dialog, which) -> {
                if (which == 0 || which == 1) {
                    line("Đã chọn chế độ CarPlay " + (which == 0
                        ? "không dây ưu tiên, USB dự phòng." : "không dây.") , GREEN);
                    line("Bước tiếp theo: ghép iPhone qua Bluetooth, sau đó dùng Wi-Fi theo hướng dẫn của receiver tương thích.", WHITE);
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
                    } catch (Exception e) {
                        line("Không mở được cài đặt Bluetooth: " + e.getMessage(), 0xffffca70);
                    }
                } else {
                    line("Đã chọn CarPlay qua USB. Dùng cáp dữ liệu và cổng USB host/OTG; cấp quyền USB khi Android hỏi.", GREEN);
                    line("Nếu không có receiver/phần cứng CarPlay được xác thực tương thích, BO Kali AI chưa thể giải mã và hiển thị giao diện CarPlay thật.", 0xffffca70);
                    new AlertDialog.Builder(this)
                        .setTitle("Thiết lập CarPlay USB")
                        .setMessage("1. Dùng cáp Lightning/USB-C truyền dữ liệu, không phải cáp chỉ sạc.\n"
                            + "2. Android phải hỗ trợ USB host/OTG và cấp nguồn ổn định.\n"
                            + "3. Receiver phải hỗ trợ giao thức CarPlay và xác thực hợp lệ.\n"
                            + "4. Chấp nhận quyền USB khi hệ thống yêu cầu.\n\n"
                            + "Kết nối USB thông thường không tự cung cấp chứng nhận CarPlay.")
                        .setPositiveButton("ĐÃ HIỂU", null).show();
                }
            }).setNegativeButton("Đóng", null).show();
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
                URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent");
                HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                conn.setRequestMethod("POST"); conn.setRequestProperty("x-goog-api-key", geminiKey); conn.setConnectTimeout(15000); conn.setReadTimeout(30000);
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
    private String getAppVersionName() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "unknown" : info.versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
    @Override protected void onDestroy() {
        stopHubDiscovery();
        stopAdbServiceScans();
        super.onDestroy();
        worker.shutdownNow();
        geminiKey = "";
        githubToken = "";
    }
}
