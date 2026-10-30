package com.example.navapp.nav;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

// Replays test/fixtures/nav-expected.json (generated from web/js/nav.js and
// voice.js by scripts/make-nav-expectations.js) through the Java Navigator and
// VoicePrompter. Every fix must give the same step, distances, off-route flag,
// arrival and spoken prompt as the JavaScript copies.
public class NavigatorParityTest {

    // Gradle runs unit tests from android/app; the fixtures live in navapp/test.
    private static final Path FIXTURES = Paths.get("..", "..", "test", "fixtures");
    // The fixture rounds distances and times to 3 decimal places.
    private static final double TOLERANCE = 0.002;

    private static JsonObject read(String name) throws IOException {
        return JsonParser.parseString(new String(Files.readAllBytes(FIXTURES.resolve(name)), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    static NavRoute route(JsonObject json) {
        JsonArray geo = json.getAsJsonArray("geometry");
        double[][] geometry = new double[geo.size()][];
        for (int i = 0; i < geo.size(); i++) {
            JsonArray p = geo.get(i).getAsJsonArray();
            geometry[i] = new double[] { p.get(0).getAsDouble(), p.get(1).getAsDouble() };
        }
        List<NavRoute.Step> steps = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("steps")) {
            JsonObject s = e.getAsJsonObject();
            JsonObject loc = s.getAsJsonObject("location");
            steps.add(new NavRoute.Step(s.get("instruction").getAsString(), s.get("maneuver").getAsString(),
                    loc.get("lat").getAsDouble(), loc.get("lng").getAsDouble(),
                    s.get("distanceM").getAsDouble(),
                    s.has("durationS") ? s.get("durationS").getAsDouble() : Double.NaN));
        }
        return new NavRoute(json.get("distanceM").getAsDouble(), json.get("durationS").getAsDouble(), geometry, steps);
    }

    @Test
    public void javaNavigatorAndVoiceMatchTheJavaScriptOnes() throws IOException {
        JsonObject expected = read("nav-expected.json");
        NavRoute route = route(read(expected.get("route").getAsString()).getAsJsonObject("route"));
        int checked = 0;
        for (JsonElement se : expected.getAsJsonArray("scenarios")) {
            JsonObject sc = se.getAsJsonObject();
            String name = sc.get("name").getAsString();
            Navigator nav = new Navigator(route);
            final String[] said = new String[1];
            VoicePrompter voice = new VoicePrompter(text -> said[0] = text);
            JsonArray fixes = sc.getAsJsonArray("fixes");
            JsonArray want = sc.getAsJsonArray("expected");
            for (int i = 0; i < fixes.size(); i++) {
                JsonObject f = fixes.get(i).getAsJsonObject();
                JsonObject w = want.get(i).getAsJsonObject();
                Navigator.State s = nav.update(f.get("lat").getAsDouble(), f.get("lng").getAsDouble());
                said[0] = null;
                voice.onUpdate(s);
                String at = name + ", fix " + i;
                assertEquals(at + ": step", w.get("stepIndex").getAsInt(), s.stepIndex);
                assertEquals(at + ": distance to next", w.get("distanceToNextM").getAsDouble(), s.distanceToNextM, TOLERANCE);
                assertEquals(at + ": remaining m", w.get("remainingM").getAsDouble(), s.remainingM, TOLERANCE);
                assertEquals(at + ": remaining s", w.get("remainingS").getAsDouble(), s.remainingS, TOLERANCE);
                assertEquals(at + ": off route", w.get("offRoute").getAsBoolean(), s.offRoute);
                assertEquals(at + ": arrived", w.get("arrived").getAsBoolean(), s.arrived);
                String spoken = w.get("spoken").isJsonNull() ? null : w.get("spoken").getAsString();
                assertEquals(at + ": spoken", spoken, said[0]);
                checked++;
            }
        }
        assertEquals("fixes replayed", 602, checked);
    }
}
