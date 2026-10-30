package com.example.navapp.nav;

import java.util.Collections;
import java.util.List;

// A route in the same shape the web page uses (server/web routing.js output):
// total distance and time, the line as [lat, lng] pairs, and the manoeuvres.
// Plain Java with no Android types, so the navigator runs in JVM unit tests.
public final class NavRoute {

    public static final class Step {
        public final String instruction;
        public final String maneuver;
        public final double lat;
        public final double lng;
        public final double distanceM;
        // NaN when the route has no per-step times (older or hand-made routes).
        public final double durationS;

        public Step(String instruction, String maneuver, double lat, double lng, double distanceM, double durationS) {
            this.instruction = instruction;
            this.maneuver = maneuver;
            this.lat = lat;
            this.lng = lng;
            this.distanceM = distanceM;
            this.durationS = durationS;
        }
    }

    public final double distanceM;
    public final double durationS;
    public final double[][] geometry;
    public final List<Step> steps;

    public NavRoute(double distanceM, double durationS, double[][] geometry, List<Step> steps) {
        this.distanceM = distanceM;
        this.durationS = durationS;
        this.geometry = geometry;
        this.steps = Collections.unmodifiableList(steps);
    }
}
