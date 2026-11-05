package com.bokaliai.terminal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

public class TermuxResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Bundle result = intent == null ? null : intent.getBundleExtra("result");
        final int maxChars = 24000;
        StringBuilder output = new StringBuilder();
        int exitCode = -1;
        boolean screenCaptureSaved = false;
        boolean scrcpyReady = false;
        String scrcpyToken = "";

        if (result == null) {
            output.append("Termux không trả về gói kết quả. Có thể lệnh chưa chạy, Termux quá cũ hoặc quyền RUN_COMMAND chưa được cấp.\n");
            if (intent != null) {
                String error = intent.getStringExtra("errmsg");
                if (error != null && !error.isEmpty()) output.append("Chi tiết: ").append(error).append("\n");
            }
        } else {
            String stdout = result.getString("stdout", "");
            String stderr = result.getString("stderr", "");
            // Capture compact machine-readable callbacks before stdout is truncated.
            // AI plan output may be printed after a long CLI explanation; preserve the exact
            // bounded marker so the Activity can still validate request ID and command policy.
            String aiExecutionMarker = "";
            java.util.regex.Matcher aiExecutionMatcher = java.util.regex.Pattern
                .compile("BO_AI_EXEC_RESULT:([0-9a-fA-F-]{36}):(-?[0-9]+):([A-Za-z_-]+)")
                .matcher(stdout);
            if (aiExecutionMatcher.find()) aiExecutionMarker = aiExecutionMatcher.group();
            String aiPlanMarker = "";
            java.util.regex.Matcher aiPlanMatcher = java.util.regex.Pattern
                .compile("(?m)^BO_AI_PLAN:([0-9a-fA-F-]{36}):(\\{[^\\r\\n]{1,5000}\\})\\s*$")
                .matcher(stdout);
            if (aiPlanMatcher.find()) aiPlanMarker = aiPlanMatcher.group();
            // Screenshot Base64 is decoded below; never copy its multi-megabyte payload
            // into the human-readable transcript builder.
            boolean screenshotPayload = stdout.startsWith("BO_SCREEN_BASE64:");
            if (!screenshotPayload && stdout.length() > maxChars) {
                stdout = stdout.substring(0, maxChars)
                    + "\\n[STDOUT đã được giới hạn ở 24.000 ký tự.]";
            }
            if (stderr.length() > maxChars) {
                stderr = stderr.substring(0, maxChars)
                    + "\\n[STDERR đã được giới hạn ở 24.000 ký tự.]";
            }
            java.util.regex.Matcher requestMatcher = java.util.regex.Pattern
                .compile("BO_SCRCPY_REQUEST:([0-9a-fA-F-]{36})").matcher(stdout);
            if (requestMatcher.find()) scrcpyToken = requestMatcher.group(1);
            java.util.regex.Matcher readyMatcher = java.util.regex.Pattern
                .compile("BO_SCRCPY_READY:([0-9a-fA-F-]{36}):[0-9]+").matcher(stdout);
            scrcpyReady = readyMatcher.find();
            if (scrcpyReady) scrcpyToken = readyMatcher.group(1);
            exitCode = result.getInt("exitCode", -1);
            int err = result.getInt("err", -1);
            String errmsg = result.getString("errmsg", "");
            output.append("Mã thoát: ").append(exitCode);
            // Keep machine-readable markers at the beginning of the bounded transcript.
            // Otherwise the final 24k display cap could cut off a marker after long stdout.
            if (!aiExecutionMarker.isEmpty()) output.append("\n").append(aiExecutionMarker).append("\n");
            if (!aiPlanMarker.isEmpty()) output.append("\n").append(aiPlanMarker).append("\n");
            if (exitCode == 0) output.append(" (thành công)");
            else if (exitCode > 0) output.append(" (lệnh kết thúc với lỗi)");
            output.append("\n");
            if (!stdout.isEmpty() && !screenshotPayload) output.append("STDOUT:\n").append(stdout).append("\n");
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

        // Bound the transcript before persisting it. Termux stdout/stderr can be very large;
        // truncating only after SharedPreferences writes would still allow storage exhaustion.
        if (output.length() > maxChars) {
            output.setLength(maxChars);
            output.append("\n[Đã cắt bớt kết quả sau 24.000 ký tự để tránh quá tải.]");
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

        Intent show = new Intent(context, MainActivity.class);
        show.setAction("com.bokaliai.terminal.TERMUX_RESULT");
        show.putExtra("termux_output", output.toString());
        show.putExtra("bo_screen_capture", screenCaptureSaved);
        show.putExtra("bo_scrcpy_ready", scrcpyReady);
        show.putExtra("bo_scrcpy_token", scrcpyToken);
        show.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        // Persist before attempting to launch the Activity. If the process was not alive,
        // Android creates it through onCreate/onResume rather than onNewIntent; the result
        // must therefore already be available in either lifecycle path.
        // Accumulate callbacks that arrive close together so one pending result cannot
        // overwrite another before the Activity is allowed to resume.
        String previousPending = resultPrefs.getString("pending_output", "");
        String combinedPending = previousPending.isEmpty() ? output.toString()
            : previousPending + "\n\n--- KẾT QUẢ TERMUX TIẾP THEO ---\n" + output;
        final int maxPendingChars = 48000;
        if (combinedPending.length() > maxPendingChars) {
            // Preserve every ADB execution result marker before trimming human-readable output.
            // The Activity needs these UUID/exit/device-state tuples to release its single-flight
            // guard and advance or stop a queued plan; losing one could leave the queue ambiguous.
            java.util.regex.Matcher markers = java.util.regex.Pattern
                .compile("BO_AI_EXEC_RESULT:([0-9a-fA-F-]{36}):(-?[0-9]+):([A-Za-z_-]+)")
                .matcher(combinedPending);
            StringBuilder preservedMarkers = new StringBuilder();
            java.util.HashSet<String> seenMarkers = new java.util.HashSet<>();
            while (markers.find()) {
                String marker = markers.group();
                if (seenMarkers.add(marker)) preservedMarkers.append(marker).append("\\n");
            }
            String prefix = "[Một số kết quả Termux cũ đã được lược bớt để giới hạn bộ nhớ.]\\n"
                + preservedMarkers;
            int tailLength = Math.max(0, maxPendingChars - prefix.length());
            String tail = combinedPending.substring(Math.max(0, combinedPending.length() - tailLength));
            combinedPending = prefix + tail;
        }
        boolean previousScreenCapture = resultPrefs.getBoolean("pending_screen_capture", false);
        boolean previousScrcpyReady = resultPrefs.getBoolean("pending_scrcpy_ready", false);
        String previousScrcpyToken = resultPrefs.getString("pending_scrcpy_token", "");
        boolean pendingResultSaved = resultPrefs.edit().putString("pending_output", combinedPending)
            .putBoolean("pending_screen_capture", previousScreenCapture || screenCaptureSaved)
            .putBoolean("pending_scrcpy_ready", previousScrcpyReady || scrcpyReady)
            .putString("pending_scrcpy_token", scrcpyReady ? scrcpyToken : previousScrcpyToken).commit();
        if (!pendingResultSaved) {
            output.append("\nCẢNH BÁO: Android không xác nhận lưu bền kết quả chờ; mã tác vụ vẫn có thể cần kiểm tra thủ công.");
            show.putExtra("termux_output", output.toString());
        }
        try {
            context.startActivity(show);
        } catch (Exception ignored) {
            // Background activity launches may be blocked. The pending result remains
            // stored and is consumed the next time the user opens BO Kali AI.
        }
    }
}