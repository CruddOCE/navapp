package com.example.navapp.car;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Presentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.core.content.ContextCompat;
import java.util.Locale;

// Draws the map on the Android Auto car screen, behind the host's turn card.
// The host hands over a Surface; a VirtualDisplay on that Surface shows a
// Presentation holding a WebView, which runs web/car-map.html: the same
// MapLibre map and style as the phone, so the car gets real roads and labels.
//
// The map is there whenever navapp is open on the car, not only during a
// route, and always shows your position: during a route from the navigation
// service (NavState), otherwise from its own location watch here. The page
// opens at your last known position, so its first frame is already in place.
// Drags and pinches come in through the SurfaceCallback; zoom and recenter
// come from NavScreen's map buttons.
public final class CarMapRenderer implements SurfaceCallback {

    // Tells NavScreen to redraw its template, so the Recenter button shows or hides.
    public interface FollowListener {
        void onFollowChanged(boolean following);
    }

    // Capacitor bundles web/ into the APK under assets/public.
    private static final String PAGE = "file:///android_asset/public/car-map.html";
    private static final long IDLE_FIX_INTERVAL_MS = 1000;

    private final Context context;
    private final FollowListener followListener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final FollowState follow = new FollowState();
    private VirtualDisplay display;
    private Presentation presentation;
    private WebView web;
    private String routeSent = null;
    private LocationManager locations;
    private final LocationListener idleFix = this::onIdleFix;
    private final Runnable returnCheck = this::checkReturn;

    public CarMapRenderer(Context context, FollowListener followListener) {
        this.context = context;
        this.followListener = followListener;
    }

    public boolean isFollowing() {
        return follow.isFollowing();
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onSurfaceAvailable(@NonNull SurfaceContainer surface) {
        if (surface.getSurface() == null) return;
        release();
        display = context.getSystemService(DisplayManager.class).createVirtualDisplay(
                "navapp-car-map", surface.getWidth(), surface.getHeight(), surface.getDpi(), surface.getSurface(), 0);
        presentation = new Presentation(context, display.getDisplay());
        web = new WebView(presentation.getContext());
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        // The page and MapLibre are bundled files; map tiles load over https.
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                routeSent = null;
                onNavChanged();
            }
        });
        presentation.setContentView(web);
        presentation.show();
        web.loadUrl(PAGE + startHash());
        startIdleFixes();
    }

    // "#lat,lng,heading" for the best position known now, so the page's first
    // frame is at your position: the route's latest fix, else Android's last one.
    private String startHash() {
        if (NavState.hasPosition()) {
            double h = NavState.getHeading();
            return String.format(Locale.ROOT, "#%.7f,%.7f%s", NavState.getLat(), NavState.getLng(),
                    Double.isNaN(h) ? "" : String.format(Locale.ROOT, ",%.1f", h));
        }
        Location last = lastKnown();
        if (last == null) return "";
        return String.format(Locale.ROOT, "#%.7f,%.7f%s", last.getLatitude(), last.getLongitude(),
                last.hasBearing() ? String.format(Locale.ROOT, ",%.1f", last.getBearing()) : "");
    }

    private boolean mayUseLocation() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private Location lastKnown() {
        if (!mayUseLocation()) return null;
        LocationManager lm = context.getSystemService(LocationManager.class);
        Location best = null;
        String[] providers = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? new String[] { LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER }
                : new String[] { LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER };
        for (String p : providers) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (IllegalArgumentException e) {
                // Provider not on this phone.
            }
        }
        return best;
    }

    // Outside a route the navigation service is not running, so the car map keeps
    // its own location watch to always show where you are. During a route the
    // service's fixes take over (onIdleFix ignores these then).
    @SuppressLint("MissingPermission")
    private void startIdleFixes() {
        if (locations != null || !mayUseLocation()) return;
        locations = context.getSystemService(LocationManager.class);
        String provider = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? LocationManager.FUSED_PROVIDER : LocationManager.GPS_PROVIDER;
        locations.requestLocationUpdates(provider, IDLE_FIX_INTERVAL_MS, 0, idleFix, Looper.getMainLooper());
    }

    private void onIdleFix(Location fix) {
        if (NavState.isActive()) return;
        sendPosition(fix.getLatitude(), fix.getLongitude(), fix.hasBearing() ? fix.getBearing() : Double.NaN);
    }

    @Override
    public void onVisibleAreaChanged(@NonNull Rect area) {
        js(String.format(Locale.ROOT, "carMap.setArea(%d,%d,%d,%d)", area.left, area.top, area.right, area.bottom));
    }

    @Override
    public void onStableAreaChanged(@NonNull Rect area) {
        // The visible area is what the map follows; the stable one adds nothing here.
    }

    @Override
    public void onScroll(float distanceX, float distanceY) {
        touched();
        js(String.format(Locale.ROOT, "carMap.pan(%.1f,%.1f)", distanceX, distanceY));
    }

    @Override
    public void onScale(float focusX, float focusY, float scaleFactor) {
        touched();
        js(String.format(Locale.ROOT, "carMap.scale(%.4f,%.1f,%.1f)", scaleFactor, focusX, focusY));
    }

    // NavScreen's zoom buttons.
    public void zoomStep(int step) {
        touched();
        js("carMap.zoomStep(" + step + ")");
    }

    public void recenter() {
        follow.recenter();
        main.removeCallbacks(returnCheck);
        js("carMap.follow()");
        followListener.onFollowChanged(true);
    }

    private void touched() {
        boolean was = follow.isFollowing();
        follow.touched(SystemClock.elapsedRealtime());
        main.removeCallbacks(returnCheck);
        main.postDelayed(returnCheck, FollowState.RETURN_AFTER_MS);
        if (was) followListener.onFollowChanged(false);
    }

    private void checkReturn() {
        if (follow.dueToReturn(SystemClock.elapsedRealtime())) recenter();
    }

    @Override
    public void onSurfaceDestroyed(@NonNull SurfaceContainer surface) {
        release();
    }

    // Called by NavScreen on every NavState change. The route line is only sent
    // when it changes (start, reroute, end), the position on every fix.
    public void onNavChanged() {
        if (web == null) return;
        String route = NavState.getRouteJson();
        if (route != null && !route.equals(routeSent)) {
            routeSent = route;
            js("carMap.setRoute(" + route + ")");
        }
        if (NavState.isActive() && NavState.hasPosition()) {
            sendPosition(NavState.getLat(), NavState.getLng(), NavState.getHeading());
        }
    }

    private void sendPosition(double lat, double lng, double heading) {
        js(String.format(Locale.ROOT, "carMap.setPosition(%.7f,%.7f,%s)", lat, lng,
                Double.isNaN(heading) ? "null" : String.format(Locale.ROOT, "%.1f", heading)));
    }

    private void js(String script) {
        main.post(() -> {
            if (web != null) web.evaluateJavascript("window.carMap && " + script, null);
        });
    }

    private void release() {
        if (locations != null) locations.removeUpdates(idleFix);
        locations = null;
        if (presentation != null) presentation.dismiss();
        if (web != null) web.destroy();
        if (display != null) display.release();
        presentation = null;
        web = null;
        display = null;
        routeSent = null;
    }

    // Called when the car screen goes away.
    public void destroy() {
        main.removeCallbacksAndMessages(null);
        release();
    }
}
