package com.bokaliai.terminal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class AdbKeepAliveService extends Service {
    public static final String ACTION_START = "com.bokaliai.terminal.ADB_KEEPALIVE_START";
    public static final String ACTION_STOP = "com.bokaliai.terminal.ADB_KEEPALIVE_STOP";
    public static final String EXTRA_SERIAL = "serial";
    private static final String CHANNEL_ID = "bo_adb_session";
    private static final int NOTIFICATION_ID = 27183;
    private PowerManager.WakeLock wakeLock;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            releaseWakeLock();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        android.content.SharedPreferences session =
            getSharedPreferences("bo_kali_adb_session", MODE_PRIVATE);
        boolean sessionRequested = session.getBoolean("keep_alive", false);
        String serial = intent == null
            ? session.getString("active_serial", "")
            : intent.getStringExtra(EXTRA_SERIAL);
        if (intent == null && !sessionRequested) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (serial == null) serial = "";
        Notification notification = buildNotification(serial);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        acquireWakeLock();
        return START_STICKY;
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        if (power == null) return;
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BOKaliAI:AdbSession");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try { wakeLock.release(); } catch (RuntimeException ignored) { }
        }
        wakeLock = null;
    }

    private Notification buildNotification(String serial) {
        String detail = serial == null || serial.isEmpty()
            ? "Phiên ADB đang được duy trì" : "Đang duy trì ADB với " + serial;
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("BO Kali AI · ADB đang hoạt động")
                .setContentText(detail + " · Vẫn giữ ADB khi khóa màn hình · Dừng trong ứng dụng để ngắt")
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true).setOnlyAlertOnce(true).build();
        }
        return new Notification.Builder(this)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("BO Kali AI · ADB đang hoạt động")
            .setContentText(detail + " · Vẫn giữ ADB khi khóa màn hình · Dừng trong ứng dụng để ngắt")
            .setOngoing(true).build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
            "Phiên kết nối ADB", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Duy trì phiên ADB khi màn hình điện thoại tắt hoặc khóa");
        manager.createNotificationChannel(channel);
    }

    @Override public void onDestroy() {
        releaseWakeLock();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
