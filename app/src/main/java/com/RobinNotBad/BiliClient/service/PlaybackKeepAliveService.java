package com.RobinNotBad.BiliClient.service;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.player.PlayerActivity;

public class PlaybackKeepAliveService extends Service {
    public static final String ACTION_RETURN_TO_PLAYER =
            "com.RobinNotBad.BiliClient.playback.RETURN_TO_PLAYER";
    private static final String ACTION_START = "com.RobinNotBad.BiliClient.playback.START";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_ALLOWED = "allowed";
    private static final String CHANNEL_ID = "biliterminal_playback";
    private static final int NOTIFICATION_ID = 1028;

    private PowerManager.WakeLock wakeLock;

    public static void start(Context context, String title, boolean allowed) {
        if (!allowed) {
            stop(context);
            return;
        }
        Intent intent = new Intent(context, PlaybackKeepAliveService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_TITLE, title == null ? "" : title);
        intent.putExtra(EXTRA_ALLOWED, true);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, PlaybackKeepAliveService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    getPackageName() + ":background-playback"
            );
            wakeLock.setReferenceCounted(false);
        }
    }

    @SuppressLint("WakelockTimeout")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null
                || !ACTION_START.equals(intent.getAction())
                || !intent.getBooleanExtra(EXTRA_ALLOWED, false)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String title = intent.getStringExtra(EXTRA_TITLE);
        startForeground(NOTIFICATION_ID, buildNotification(title));
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
        stopForeground(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private android.app.Notification buildNotification(String title) {
        Intent playerIntent = new Intent(this, PlayerActivity.class);
        playerIntent.setAction(ACTION_RETURN_TO_PLAYER);
        playerIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, playerIntent, pendingFlags);

        String displayTitle = title == null || title.trim().isEmpty() ? "哔哩终端" : title;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.icon)
                .setContentTitle(displayTitle)
                .setContentText("正在后台播放")
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSound(null)
                .setVibrate(null)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        builder.setContentIntent(contentIntent);
        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "后台播放",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("保持听视频或听直播模式在熄屏或返回桌面后继续播放");
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
    }
}
