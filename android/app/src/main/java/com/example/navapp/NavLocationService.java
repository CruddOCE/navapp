package com.example.navapp;

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
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

// Keeps the app counted as "in use" while a route is running, so Android keeps
// delivering GPS fixes with the screen locked or another app in front. The app
// only has while-in-use location; without this service the fixes stop as soon
// as the phone locks, which is how it sits in a car running Android Auto.
// The service does no location work itself: the Geolocation plugin's watch keeps
// running because the app stays in the foreground. Android requires the
// notification for any foreground service.
public class NavLocationService extends Service {

    private static final String CHANNEL_ID = "navigation";
    private static final int NOTIFICATION_ID = 1;

    public static void start(Context context) {
        ContextCompat.startForegroundService(context, new Intent(context, NavLocationService.class));
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, NavLocationService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0;
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type);
        // Not sticky: if Android kills the app, a restarted service would hold the
        // notification up with no route behind it.
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Swiping the app away ends the route, so the notification goes too.
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Navigation", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shown while a route is running, so GPS keeps working with the screen off.");
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_nav)
                .setContentTitle("navapp is navigating")
                .setContentText("GPS stays on until you end the route.")
                .setContentIntent(tap)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
