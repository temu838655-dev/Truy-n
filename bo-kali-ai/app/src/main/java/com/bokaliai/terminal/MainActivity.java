package com.bokaliai.terminal;

import android.app.Activity;
import android.app.AlertDialog;
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
    private String geminiKey = "", githubToken = "";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
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
        Button clear = button("Xóa");
        clear.setOnClickListener(v -> log.removeAllViews());
        bar.addView(clear, new LinearLayout.LayoutParams(dp(62), dp(44)));
        root.addView(bar);
        scroll = new ScrollView(this); scroll.setFillViewport(true);
        log = new LinearLayout(this); log.setOrientation(1); log.setPadding(12,12,12,12); log.setBackgroundColor(PANEL);
        scroll.addView(log);
        root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        TextView commandLabel = text("Ô 1 · TERMINAL COMMAND", GREEN, 12, true); root.addView(commandLabel);
        LinearLayout commandRow = new LinearLayout(this); commandRow.setOrientation(0);
        commandInput = input("Nhập lệnh: help, device, net, wifi, bluetooth...");
        commandRow.addView(commandInput, new LinearLayout.LayoutParams(0,dp(50),1));
        Button run = button("CHẠY"); run.setOnClickListener(v -> runCommand());
        commandRow.addView(run, new LinearLayout.LayoutParams(dp(82),dp(50)));
        root.addView(commandRow);
        TextView aiLabel = text("Ô 2 · HỎI AI BẰNG TIẾNG VIỆT", 0xff79c9ff, 12, true); root.addView(aiLabel);
        LinearLayout aiRow = new LinearLayout(this); aiRow.setOrientation(0);
        questionInput = input("Ví dụ: Lệnh nào kiểm tra địa chỉ IP?");
        aiRow.addView(questionInput, new LinearLayout.LayoutParams(0,dp(50),1));
        Button ask = button("HỎI AI"); ask.setOnClickListener(v -> askAi());
        aiRow.addView(ask, new LinearLayout.LayoutParams(dp(88),dp(50)));
        root.addView(aiRow);
        setContentView(root);
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
                if(code >= 200 && code < 300) answer = result.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).optString("text","AI không trả nội dung.");
                else answer = "Gemini API báo lỗi HTTP " + code + ": " + result.toString();
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
