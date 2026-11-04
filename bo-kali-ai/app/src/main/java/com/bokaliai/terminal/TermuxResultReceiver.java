package com.bokaliai.terminal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

public class TermuxResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Bundle result = intent == null ? null : intent.getBundleExtra("result");
        StringBuilder output = new StringBuilder();
        int exitCode = -1;
        boolean screenCaptureSaved = false;
        boolean scrcpyReady = false;

        if (result == null) {
            output.append("Termux không trả về gói kết quả. Có thể lệnh chưa chạy, Termux quá cũ hoặc quyền RUN_COMMAND chưa được cấp.\n");
            if (intent != null) {
                String error = intent.getStringExtra("errmsg");
                if (error != null && !error.isEmpty()) output.append("Chi tiết: ").append(error).append("\n");
            }
        } else {
            String stdout = result.getString("stdout", "");
            String stderr = result.getString("stderr", "");
            scrcpyReady = stdout.contains("BO_SCRCPY_READY:");
            exitCode = result.getInt("exitCode", -1);
            int err = result.getInt("err", -1);
            String errmsg = result.getString("errmsg", "");
            output.append("Mã thoát: ").append(exitCode);
            if (exitCode == 0) output.append(" (thành công)");
            else if (exitCode > 0) output.append(" (lệnh kết thúc với lỗi)");
            output.append("\n");
            if (!stdout.isEmpty()) output.append("STDOUT:\n").append(stdout).append("\n");
            if (!stderr.isEmpty()) output.append("STDERR:\n").append(stderr).append("\n");
            if (err != -1 || !errmsg.isEmpty()) {
                output.append("Lỗi Termux: ").append(errmsg).append(" (").append(err).append(")\n");
            }
            if (stdout.isEmpty() && stderr.isEmpty() && errmsg.isEmpty()) {
                output.append("Lệnh không tạo đầu ra. Kiểm tra mã thoát và thử lệnh khác nếu cần.\n");
            }
        }

        // ADB screenshot payload is explicitly marked; decode it to app-private storage instead of
        // forwarding a huge Base64 string through the activity transcript.
        if (result != null) {
            String stdout = result.getString("stdout", "");
            final String prefix = "BO_SCREEN_BASE64:";
            if (stdout.startsWith(prefix)) {
                String encoded = stdout.substring(prefix.length()).trim();
                try {
                    if (encoded.length() > 8_000_000) throw new IllegalArgumentException("Ảnh vượt giới hạn 6 MB.");
                    byte[] png = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT);
                    if (png.length < 8 || png.length > 6 * 1024 * 1024
                        || png[0] != (byte) 0x89 || png[1] != 0x50 || png[2] != 0x4e || png[3] != 0x47) {
                        throw new IllegalArgumentException("Dữ liệu không phải ảnh PNG hợp lệ hoặc ảnh quá lớn.");
                    }
                    try (java.io.FileOutputStream stream = context.openFileOutput("adb_screen.png", Context.MODE_PRIVATE)) {
                        stream.write(png);
                        stream.flush();
                    }
                    screenCaptureSaved = true;
                    output.setLength(0);
                    output.append("Đã nhận ảnh chụp màn hình ADB (").append(png.length).append(" byte).");
                } catch (Exception e) {
                    output.setLength(0);
                    output.append("Không giải mã được ảnh màn hình: ").append(e.getMessage());
                }
            }
        }

        // Keep the latest result available for an explicit user-initiated share action.
        SharedPreferences resultPrefs = context.getSharedPreferences("bo_kali_results", Context.MODE_PRIVATE);
        resultPrefs.edit().putString("last_output", output.toString())
            .putLong("last_output_at", System.currentTimeMillis()).apply();

        // Store the actual Termux result so the update history distinguishes a sent command from a completed one.
        SharedPreferences prefs = context.getSharedPreferences("bo_kali_update_history", Context.MODE_PRIVATE);
        String old = prefs.getString("entries", "");
        String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(new java.util.Date());
        String status = result == null ? "KHÔNG NHẬN ĐƯỢC KẾT QUẢ" :
            (exitCode == 0 ? "HOÀN THÀNH (exit 0)" : "CẦN KIỂM TRA (exit " + exitCode + ")");
        String entry = stamp + " | Kết quả tác vụ Termux | " + status;
        String combined = entry + (old.isEmpty() ? "" : "\n" + old);
        String[] rows = combined.split("\n");
        StringBuilder kept = new StringBuilder();
        for (int i = 0; i < rows.length && i < 30; i++) {
            if (kept.length() > 0) kept.append("\n");
            kept.append(rows[i]);
        }
        prefs.edit().putString("entries", kept.toString()).apply();

        // Avoid passing an unbounded transcript back into the activity or a later AI request.
        final int maxChars = 24000;
        if (output.length() > maxChars) {
            output.setLength(maxChars);
            output.append("\n[Đã cắt bớt kết quả sau 24.000 ký tự để tránh quá tải.]");
        }

        Intent show = new Intent(context, MainActivity.class);
        show.setAction("com.bokaliai.terminal.TERMUX_RESULT");
        show.putExtra("termux_output", output.toString());
        show.putExtra("bo_screen_capture", screenCaptureSaved);
        show.putExtra("bo_scrcpy_ready", scrcpyReady);
        show.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        // Persist before attempting to launch the Activity. If the process was not alive,
        // Android creates it through onCreate/onResume rather than onNewIntent; the result
        // must therefore already be available in either lifecycle path.
        resultPrefs.edit().putString("pending_output", output.toString())
            .putBoolean("pending_screen_capture", screenCaptureSaved)
            .putBoolean("pending_scrcpy_ready", scrcpyReady).apply();
        try {
            context.startActivity(show);
        } catch (Exception ignored) {
            // Background activity launches may be blocked. The pending result remains
            // stored and is consumed the next time the user opens BO Kali AI.
        }
    }
}