package com.zs.wordecho.playback;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.zs.wordecho.MainActivity;
import com.zs.wordecho.R;

/**
 * Lightweight foreground keep-alive for screen-off listening. TTS remains owned by
 * the activity; this service gives the process foreground priority and holds a
 * partial wake lock so playback can continue while the display is off.
 */
public final class PlaybackKeepAliveService extends Service {
    public static final String ACTION_START = "com.zs.wordecho.action.START_KEEP_ALIVE";
    public static final String ACTION_STOP = "com.zs.wordecho.action.STOP_KEEP_ALIVE";
    public static final String ACTION_STOP_PLAYBACK = "com.zs.wordecho.action.STOP_PLAYBACK";
    public static final String EXTRA_WORD = "word";

    private static final String CHANNEL_ID = "word_echo_playback";
    private static final int NOTIFICATION_ID = 4101;
    private static final String WAKE_TAG = "WordEcho:playback";

    private PowerManager.WakeLock wakeLock;

    public static void start(Context context, String word) {
        Intent intent = new Intent(context, PlaybackKeepAliveService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_WORD, word);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, PlaybackKeepAliveService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG);
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            sendBroadcast(new Intent(ACTION_STOP_PLAYBACK).setPackage(getPackageName()));
            stopSelf();
            return START_NOT_STICKY;
        }
        String word = intent == null ? null : intent.getStringExtra(EXTRA_WORD);
        startForegroundCompat(word);
        return START_NOT_STICKY;
    }

    private void startForegroundCompat(String word) {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0;
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(word), type);
    }

    private Notification buildNotification(String word) {
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, PlaybackKeepAliveService.class)
                .setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = word == null || word.isEmpty()
                ? getString(R.string.playback_notification_text)
                : getString(R.string.playback_notification_text_word, word);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_headphones)
                .setContentTitle(getString(R.string.playback_notification_title))
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(R.drawable.ic_pause, getString(R.string.stop_playback), stopPendingIntent)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.playback_channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.screen_off_desc));
        manager.createNotificationChannel(channel);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
        super.onDestroy();
    }
}
