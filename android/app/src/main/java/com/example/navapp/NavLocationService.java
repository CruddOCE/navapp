package com.example.navapp;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import com.example.navapp.car.NavState;
import com.example.navapp.nav.AndroidSpeaker;
import com.example.navapp.nav.NavRoute;
import com.example.navapp.nav.Navigator;
import com.example.navapp.nav.OsrmClient;
import com.example.navapp.nav.RouteJson;
import com.example.navapp.nav.TurnTypes;
import com.example.navapp.nav.VoicePrompter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONException;
import org.json.JSONObject;

// Runs navigation while a route is active: GPS fixes, turn tracking, voice
// prompts, the car screen and rerouting. It lives here, not in the web page,
// because Android's WebView stops running a page that has been hidden for about
// a minute (measured 2026-10-30), while this foreground service keeps getting
// fixes with the phone locked. The page only displays what this sends it.
//
// Being a foreground service of type location also keeps the app's
// while-in-use location permission valid in the background. Android requires
// the notification for any foreground service.
public class NavLocationService extends Service {

    // What the page (through NavStatePlugin) hears about. Called on the main thread.
    public interface Listener {
        void onState(JSONObject state);
        void onRerouted(JSONObject route);
        void onMessage(String text);
        void onEnded(boolean arrived);
    }

    private static final String TAG = "NavEngine";
    private static final String CHANNEL_ID = "navigation";
    private static final int NOTIFICATION_ID = 1;
    private static final long FIX_INTERVAL_MS = 1000;
    private static final long REROUTE_MIN_GAP_MS = 10000;
    // Long enough for "You have arrived" to finish before the service goes.
    private static final long END_AFTER_ARRIVAL_MS = 4000;

    // Handed over by NavStatePlugin at Start; read by the service when it starts.
    private static String pendingRouteJson;
    private static double destLat;
    private static double destLng;
    private static volatile Listener listener;
    private static volatile JSONObject lastState;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private LocationManager locations;
    private AndroidSpeaker speaker;
    private VoicePrompter voice;
    private Navigator navigator;
    private long lastRerouteAt = 0;
    private boolean rerouting = false;
    private boolean ended = false;
    private double lastLat = Double.NaN;
    private double lastLng = Double.NaN;
    private String networkCountry = "";
    // For dump(): proves the engine is running while the page cannot be asked.
    private int fixesHandled = 0;
    private long lastFixAt = 0;
    private int promptsSpoken = 0;
    private String lastPrompt = "";
    private int reroutes = 0;

    private final LocationListener onFix = this::onLocation;

    public static void startRoute(Context context, String routeJson, double toLat, double toLng) {
        pendingRouteJson = routeJson;
        destLat = toLat;
        destLng = toLng;
        lastState = null;
        ContextCompat.startForegroundService(context, new Intent(context, NavLocationService.class));
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, NavLocationService.class));
    }

    public static void setListener(Listener l) {
        listener = l;
    }

    // The latest navigation state, for the page to catch up when it is shown again.
    public static JSONObject lastState() {
        return lastState;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0;
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type);
        try {
            navigator = new Navigator(RouteJson.toRoute(new JSONObject(pendingRouteJson)));
        } catch (JSONException | RuntimeException e) {
            Log.w(TAG, "bad route from the page", e);
            stopSelf();
            return START_NOT_STICKY;
        }
        ended = false;
        // Which side traffic drives on comes from the mobile network's country
        // (OSRM's own value is unreliable); empty when there is no network.
        android.telephony.TelephonyManager phone = getSystemService(android.telephony.TelephonyManager.class);
        networkCountry = phone == null ? "" : phone.getNetworkCountryIso();
        if (speaker == null) {
            speaker = new AndroidSpeaker(this);
            voice = new VoicePrompter(text -> {
                promptsSpoken++;
                lastPrompt = text;
                speaker.speak(text);
            });
        }
        startFixes();
        // Not sticky: if Android kills the app, a restarted service would hold the
        // notification up with no route behind it.
        return START_NOT_STICKY;
    }

    private void startFixes() {
        if (locations != null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "no location permission");
            return;
        }
        locations = getSystemService(LocationManager.class);
        // The fused provider (Android 12+) blends GPS, Wi-Fi and motion sensors.
        String provider = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? LocationManager.FUSED_PROVIDER : LocationManager.GPS_PROVIDER;
        locations.requestLocationUpdates(provider, FIX_INTERVAL_MS, 0, onFix, Looper.getMainLooper());
    }

    private void onLocation(Location fix) {
        if (navigator == null || ended) return;
        lastLat = fix.getLatitude();
        lastLng = fix.getLongitude();
        fixesHandled++;
        lastFixAt = SystemClock.elapsedRealtime();
        Navigator.State s = navigator.update(lastLat, lastLng);
        voice.onUpdate(s);
        NavRoute.Step next = navigator.step(s.stepIndex);
        String side = TurnTypes.drivingSide(networkCountry, next.drivingSide);
        NavState.update(!s.arrived, s.instruction, (int) Math.round(s.distanceToNextM), TurnTypes.of(next, side), next.exit);
        lastState = toJson(s, fix);
        Listener l = listener;
        if (l != null) l.onState(lastState);

        if (s.arrived) {
            ended = true;
            main.postDelayed(() -> {
                Listener now = listener;
                if (now != null) now.onEnded(true);
                stopSelf();
            }, END_AFTER_ARRIVAL_MS);
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (s.offRoute && !rerouting && now - lastRerouteAt >= REROUTE_MIN_GAP_MS) reroute(now);
    }

    // Fetches a new route from here to the destination off the main thread, then
    // switches the navigator over on the main thread.
    private void reroute(long now) {
        lastRerouteAt = now;
        rerouting = true;
        final double fromLat = lastLat;
        final double fromLng = lastLng;
        network.execute(() -> {
            try {
                JSONObject json = OsrmClient.getRoute(fromLat, fromLng, destLat, destLng);
                NavRoute route = RouteJson.toRoute(json);
                main.post(() -> {
                    rerouting = false;
                    if (ended) return;
                    navigator = new Navigator(route);
                    reroutes++;
                    Listener l = listener;
                    if (l != null) {
                        l.onRerouted(json);
                        l.onMessage("New route found.");
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "reroute failed", e);
                main.post(() -> {
                    rerouting = false;
                    Listener l = listener;
                    if (l != null) l.onMessage("Off route. Reroute failed, still following old route.");
                });
            }
        });
    }

    private static JSONObject toJson(Navigator.State s, Location fix) {
        JSONObject j = new JSONObject();
        try {
            j.put("active", !s.arrived)
                    .put("stepIndex", s.stepIndex)
                    .put("instruction", s.instruction)
                    .put("distanceToNextM", s.distanceToNextM)
                    .put("remainingM", s.remainingM)
                    .put("remainingS", s.remainingS)
                    .put("offRoute", s.offRoute)
                    .put("arrived", s.arrived)
                    .put("lat", fix.getLatitude())
                    .put("lng", fix.getLongitude());
            if (fix.hasBearing()) j.put("heading", fix.getBearing());
        } catch (JSONException e) {
            // Only thrown for NaN or infinite numbers, which the navigator never returns.
        }
        return j;
    }

    @Override
    public void onDestroy() {
        if (locations != null) locations.removeUpdates(onFix);
        locations = null;
        main.removeCallbacksAndMessages(null);
        network.shutdownNow();
        if (speaker != null) speaker.shutdown();
        NavState.update(false, "", 0);
        lastState = null;
        Listener l = listener;
        if (l != null && !ended) l.onEnded(false);
        super.onDestroy();
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

    // adb shell dumpsys activity service com.example.navapp/.NavLocationService
    // Shows the engine working even while the web page is not running.
    @Override
    protected void dump(java.io.FileDescriptor fd, java.io.PrintWriter out, String[] args) {
        long ago = lastFixAt == 0 ? -1 : (SystemClock.elapsedRealtime() - lastFixAt) / 1000;
        out.println("navapp engine: fixes=" + fixesHandled + " lastFixSecondsAgo=" + ago
                + " prompts=" + promptsSpoken + " lastPrompt=\"" + lastPrompt + "\" reroutes=" + reroutes
                + " ended=" + ended);
        out.println("state: " + lastState);
    }

    private Notification buildNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Navigation", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shown while a route is running, so navigation keeps going with the screen off.");
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
