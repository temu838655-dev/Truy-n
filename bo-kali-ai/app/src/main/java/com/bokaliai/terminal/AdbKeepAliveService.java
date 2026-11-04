package com.bokaliai.terminal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.net.wifi.WifiManager;
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
    private WifiManager.WifiLock wifiLock;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            releaseLocks();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        android.content.SharedPreferences session =
            getSharedPreferences("bo_kali_adb_session", MODE_PRIVATE);
        boolean sessionRequested = session.getBoolean("keep_alive", false);
        String savedSerial = session.getString("active_serial", "");
        String serial = intent == null ? savedSerial : intent.getStringExtra(EXTRA_SERIAL);
        // Reject stale START intents after the user has stopped the session or switched devices.
        if (!sessionRequested || serial == null || serial.isEmpty()
            || !serial.equals(savedSerial)) {
            releaseLocks();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        Notification notification = buildNotification(serial);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        acquireLocks();
        return START_STICKY;
    }

    private void acquireLocks() {
        if (wakeLock == null || !wakeLock.isHeld()) {
            PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
            if (power != null) {
                wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BOKaliAI:AdbSession");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
        }
        if (wifiLock == null || !wifiLock.isHeld()) {
            WifiManager wifi = (WifiManager) getApplicationContext()
                .getSystemService(WIFI_SERVICE);
            if (wifi != null) {
                wifiLock = wifi.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF, "BOKaliAI:AdbWifiSession");
                wifiLock.setReferenceCounted(false);
                try {
                    wifiLock.acquire();
                } catch (RuntimeException error) {
                    // Keep the CPU lock even if the Android build refuses a Wi-Fi lock.
                    wifiLock = null;
                }
            }
        }
    }

    private void releaseLocks() {
        if (wifiLock != null && wifiLock.isHeld()) {
            try { wifiLock.release(); } catch (RuntimeException ignored) { }
        }
        wifiLock = null;
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
                .setContentText(detail + " · Giữ CPU/Wi-Fi khi khóa màn hình · Dừng trong ứng dụng để ngắt")
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true).setOnlyAlertOnce(true).build();
        }
        return new Notification.Builder(this)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("BO Kali AI · ADB đang hoạt động")
            .setContentText(detail + " · Giữ CPU/Wi-Fi khi khóa màn hình · Dừng trong ứng dụng để ngắt")
            .setOngoing(true).build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
            "Phiên kết nối ADB", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Giữ CPU và Wi-Fi hoạt động để duy trì phiên ADB khi màn hình tắt hoặc khóa");
        manager.createNotificationChannel(channel);
    }

    @Override public void onDestroy() {
        releaseLocks();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
