package com.example.navapp.nav;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// Converts between JSON and NavRoute, using Android's built-in org.json, which
// is why it lives apart from the navigator (org.json is only a stub in JVM unit
// tests). Two shapes:
// - the page's route, as routing.js returns it ({ distanceM, durationS,
//   geometry: [[lat, lng]], steps: [{ instruction, maneuver, location, ... }] }),
//   which the page hands to the service at Start;
// - a raw OSRM response, which the service gets when it reroutes by itself.
//   fromOsrm turns it into the page's shape, the same way routing.js does.
public final class RouteJson {

    private RouteJson() {}

    public static NavRoute toRoute(JSONObject json) throws JSONException {
        JSONArray geo = json.getJSONArray("geometry");
        double[][] geometry = new double[geo.length()][];
        for (int i = 0; i < geo.length(); i++) {
            JSONArray p = geo.getJSONArray(i);
            geometry[i] = new double[] { p.getDouble(0), p.getDouble(1) };
        }
        JSONArray st = json.getJSONArray("steps");
        List<NavRoute.Step> steps = new ArrayList<>();
        for (int i = 0; i < st.length(); i++) {
            JSONObject s = st.getJSONObject(i);
            JSONObject loc = s.getJSONObject("location");
            steps.add(new NavRoute.Step(
                    s.optString("instruction", ""),
                    s.optString("maneuver", ""),
                    loc.getDouble("lat"),
                    loc.getDouble("lng"),
                    s.optDouble("distanceM", 0),
                    s.optDouble("durationS", Double.NaN),
                    s.optString("modifier", ""),
                    s.optInt("exit", 0),
                    s.optString("drivingSide", "right")));
        }
        return new NavRoute(json.getDouble("distanceM"), json.getDouble("durationS"), geometry, steps);
    }

    // Same rules as parseOsrmResponse in routing.js: [lng, lat] becomes [lat, lng].
    public static JSONObject fromOsrm(JSONObject osrm) throws JSONException {
        JSONArray routes = osrm.optJSONArray("routes");
        if (!"Ok".equals(osrm.optString("code")) || routes == null || routes.length() == 0) {
            throw new JSONException("no route found (" + osrm.optString("code", "unknown") + ")");
        }
        JSONObject r = routes.getJSONObject(0);
        JSONArray coords = r.getJSONObject("geometry").getJSONArray("coordinates");
        JSONArray geometry = new JSONArray();
        for (int i = 0; i < coords.length(); i++) {
            JSONArray c = coords.getJSONArray(i);
            geometry.put(new JSONArray().put(c.getDouble(1)).put(c.getDouble(0)));
        }
        JSONArray steps = new JSONArray();
        JSONArray legs = r.getJSONArray("legs");
        for (int l = 0; l < legs.length(); l++) {
            JSONArray legSteps = legs.getJSONObject(l).getJSONArray("steps");
            for (int i = 0; i < legSteps.length(); i++) {
                JSONObject s = legSteps.getJSONObject(i);
                JSONObject m = s.getJSONObject("maneuver");
                JSONArray at = m.getJSONArray("location");
                String text = Instructions.forStep(m.optString("type"), m.optString("modifier"), s.optString("name"),
                        s.optString("ref"), m.optInt("exit", 0));
                steps.put(new JSONObject()
                        .put("instruction", text)
                        .put("distanceM", s.optDouble("distance", 0))
                        .put("durationS", s.optDouble("duration", 0))
                        .put("maneuver", m.optString("type"))
                        .put("modifier", m.optString("modifier", ""))
                        .put("exit", m.optInt("exit", 0))
                        .put("drivingSide", s.optString("driving_side", "right"))
                        .put("location", new JSONObject().put("lat", at.getDouble(1)).put("lng", at.getDouble(0))));
            }
        }
        return new JSONObject()
                .put("distanceM", r.getDouble("distance"))
                .put("durationS", r.getDouble("duration"))
                .put("geometry", geometry)
                .put("steps", steps);
    }
}
