package com.example.navapp.car;

import android.app.Activity;
import android.view.WindowManager;
import com.example.navapp.NavLocationService;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

// Lets the web app report navigation state to the car screen:
// NavState.update({ active, instruction, distanceM }) from map.js.
// It also starts and stops the location service, and keeps the screen on for as
// long as a route is active: navigation runs in the web page, and a hidden page
// stops running after about a minute, so the banner, voice and car screen would
// freeze with the screen off. Moving navigation into the service is the real fix.
@CapacitorPlugin(name = "NavState")
public class NavStatePlugin extends Plugin {

    @PluginMethod
    public void update(PluginCall call) {
        boolean active = call.getBoolean("active", false);
        String instruction = call.getString("instruction", "");
        Integer distanceM = call.getInt("distanceM", 0);
        // map.js sends this on every GPS fix, so the service and the screen flag
        // only change on a start or an end, not on each update.
        boolean wasActive = NavState.isActive();
        NavState.update(active, instruction, distanceM == null ? 0 : distanceM);
        if (active && !wasActive) {
            NavLocationService.start(getContext());
            keepScreenOn(true);
        } else if (!active && wasActive) {
            NavLocationService.stop(getContext());
            keepScreenOn(false);
        }
        call.resolve(new JSObject());
    }

    private void keepScreenOn(boolean on) {
        Activity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (on) activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }
}
