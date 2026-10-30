package com.example.navapp.car;

import com.example.navapp.NavLocationService;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

// Lets the web app report navigation state to the car screen:
// NavState.update({ active, instruction, distanceM }) from map.js.
// It also starts and stops the location service, so GPS keeps running with the
// screen locked for as long as a route is active.
@CapacitorPlugin(name = "NavState")
public class NavStatePlugin extends Plugin {

    @PluginMethod
    public void update(PluginCall call) {
        boolean active = call.getBoolean("active", false);
        String instruction = call.getString("instruction", "");
        Integer distanceM = call.getInt("distanceM", 0);
        // map.js sends this on every GPS fix, so the service only changes on a
        // start or an end, not on each update.
        boolean wasActive = NavState.isActive();
        NavState.update(active, instruction, distanceM == null ? 0 : distanceM);
        if (active && !wasActive) NavLocationService.start(getContext());
        else if (!active && wasActive) NavLocationService.stop(getContext());
        call.resolve(new JSObject());
    }
}
