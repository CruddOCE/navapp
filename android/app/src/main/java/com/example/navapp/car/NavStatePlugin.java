package com.example.navapp.car;

import android.app.Activity;
import android.view.WindowManager;
import com.example.navapp.NavLocationService;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.json.JSONException;
import org.json.JSONObject;

// The page's link to native navigation (NavLocationService). map.js calls:
//   startRoute({ route, destination: { lat, lng } }) at Start,
//   stopRoute() at End,
//   getState() to catch up when the page is shown again,
// and listens for events: navState (each fix), rerouted ({ route }),
// message ({ text }) and ended ({ arrived }).
//
// While the page is hidden the WebView does not run it, so live events would
// only pile up and replay in a burst. They are held back instead, and the
// latest state is sent once when the page is shown again.
@CapacitorPlugin(name = "NavState")
public class NavStatePlugin extends Plugin implements NavLocationService.Listener {

    private volatile boolean visible = true;

    @Override
    public void load() {
        NavLocationService.setListener(this);
    }

    @Override
    protected void handleOnPause() {
        visible = false;
    }

    @Override
    protected void handleOnResume() {
        visible = true;
        JSONObject state = NavLocationService.lastState();
        if (state != null) emit("navState", state);
    }

    @PluginMethod
    public void startRoute(PluginCall call) {
        JSObject route = call.getObject("route");
        JSObject dest = call.getObject("destination");
        if (route == null || dest == null || !dest.has("lat") || !dest.has("lng")) {
            call.reject("startRoute needs a route and a destination");
            return;
        }
        NavLocationService.startRoute(getContext(), route.toString(), dest.optDouble("lat"), dest.optDouble("lng"));
        keepScreenOn(true);
        call.resolve();
    }

    @PluginMethod
    public void stopRoute(PluginCall call) {
        NavLocationService.stop(getContext());
        keepScreenOn(false);
        call.resolve();
    }

    @PluginMethod
    public void getState(PluginCall call) {
        JSONObject state = NavLocationService.lastState();
        try {
            call.resolve(state == null ? new JSObject().put("active", false) : JSObject.fromJSONObject(state));
        } catch (JSONException e) {
            call.reject("bad state");
        }
    }

    @Override
    public void onState(JSONObject state) {
        if (visible) emit("navState", state);
    }

    @Override
    public void onRerouted(JSONObject route) {
        // Sent even while hidden: the page needs the new line when it is shown.
        JSObject data = new JSObject();
        data.put("route", route);
        notifyListeners("rerouted", data, true);
    }

    @Override
    public void onMessage(String text) {
        if (!visible) return;
        JSObject data = new JSObject();
        data.put("text", text);
        notifyListeners("message", data);
    }

    @Override
    public void onEnded(boolean arrived) {
        keepScreenOn(false);
        JSObject data = new JSObject();
        data.put("arrived", arrived);
        notifyListeners("ended", data, true);
    }

    private void emit(String event, JSONObject json) {
        try {
            notifyListeners(event, JSObject.fromJSONObject(json));
        } catch (JSONException e) {
            // A state built by NavLocationService always converts.
        }
    }

    // A screen left on is no longer needed for navigation to work, but a phone
    // in a cradle is easier to follow when it stays on.
    private void keepScreenOn(boolean on) {
        Activity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (on) activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }
}
