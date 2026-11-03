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

        if (result == null) {
            output.append("Termux không trả về gói kết quả. Có thể lệnh chưa chạy, Termux quá cũ hoặc quyền RUN_COMMAND chưa được cấp.\n");
            if (intent != null) {
                String error = intent.getStringExtra("errmsg");
                if (error != null && !error.isEmpty()) output.append("Chi tiết: ").append(error).append("\n");
            }
        } else {
            String stdout = result.getString("stdout", "");
            String stderr = result.getString("stderr", "");
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
        show.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            context.startActivity(show);
        } catch (Exception ignored) {
            // Android may prevent background UI launches; the result is stored for the next foreground opening.
            context.getSharedPreferences("bo_kali_results", Context.MODE_PRIVATE)
                .edit().putString("pending_output", output.toString()).apply();
        }
    }
}