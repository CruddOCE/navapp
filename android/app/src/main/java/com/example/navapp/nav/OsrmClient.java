package com.example.navapp.nav;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

// Fetches a route from OSRM for the location service, so it can reroute while
// the page is hidden. Same server and User-Agent as web/js/api.js: the public
// OSRM server answers 403 to Android's default User-Agent. Call it off the main
// thread.
public final class OsrmClient {

    static final String BASE_URL = "https://router.project-osrm.org";
    static final String USER_AGENT = "navapp/0.01 (personal test build; https://github.com/CruddOCE/navapp)";
    private static final int TIMEOUT_MS = 15000;

    private OsrmClient() {}

    // Returns the route in the page's shape (see RouteJson).
    public static JSONObject getRoute(double fromLat, double fromLng, double toLat, double toLng)
            throws IOException, JSONException {
        String url = String.format(Locale.ROOT, "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true",
                BASE_URL, fromLng, fromLat, toLng, toLat);
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("User-Agent", USER_AGENT);
        try {
            int status = c.getResponseCode();
            if (status != 200) throw new IOException("routing service returned " + status);
            return RouteJson.fromOsrm(new JSONObject(read(c.getInputStream())));
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
