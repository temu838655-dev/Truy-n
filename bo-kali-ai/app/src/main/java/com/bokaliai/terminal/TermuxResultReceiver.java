package com.bokaliai.terminal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

public class TermuxResultReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "kali_command_results";

    @Override public void onReceive(Context context, Intent intent) {
        Bundle result = intent == null ? null : intent.getBundleExtra("result");
        if (result == null) {
            result = intent == null ? null : intent.getExtras();
        }
        String stdout = result == null ? "" : result.getString("stdout", "");
        String stderr = result == null ? "" : result.getString("stderr", "");
        int exitCode = result == null ? -1 : result.getInt("exitCode", -1);
        int err = result == null ? -1 : result.getInt("err", -1);
        String errmsg = result == null ? "Termux không trả về bundle kết quả." : result.getString("errmsg", "");
        StringBuilder output = new StringBuilder();
        output.append("Mã thoát: ").append(exitCode).append("\n");
        if (!stdout.isEmpty()) output.append("STDOUT:\n").append(stdout).append("\n");
        if (!stderr.isEmpty()) output.append("STDERR:\n").append(stderr).append("\n");
        if (err != -1 || !errmsg.isEmpty()) output.append("Lỗi Termux: ").append(errmsg).append(" (").append(err).append(")\n");
        if (stdout.isEmpty() && stderr.isEmpty() && errmsg.isEmpty()) output.append("Không có nội dung đầu ra. Kiểm tra Termux, NetHunter và quyền RUN_COMMAND.");

        String text = output.toString();
        context.getSharedPreferences("bo_kali_results", Context.MODE_PRIVATE)
            .edit().putString("pending_output", text).apply();

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Kết quả lệnh Kali", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Thông báo khi lệnh Kali trả kết quả về BO Kali AI");
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(context, 4101, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(context, CHANNEL_ID)
            : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.stat_notify_shell_script)
            .setContentTitle("BO Kali AI • Đã có kết quả")
            .setContentText("Nhấn để xem stdout, stderr và mã thoát")
            .setAutoCancel(true)
            .setContentIntent(content);
        manager.notify(4101, builder.build());
    }
}
