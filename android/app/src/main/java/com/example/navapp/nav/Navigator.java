package com.example.navapp.nav;

// Java copy of web/js/nav.js, run by the location service so navigation keeps
// going while the web page is hidden (a hidden WebView page stops running).
// It must give the same answers as nav.js: NavigatorParityTest replays
// test/fixtures/nav-expected.json, which is generated from nav.js. Change the
// two together, regenerate the fixture, and run both test suites.
//
// Progress is measured along the route line, not by circles around each turn,
// so a GPS gap that jumps the car past a turn cannot leave the prompt behind.
public final class Navigator {

    static final double ARRIVE_RADIUS_M = 25;
    static final double OFF_ROUTE_M = 40;
    static final int OFF_ROUTE_FIXES = 3;
    static final int BACKTRACK_SEGMENTS = 2;

    public static final class State {
        public final int stepIndex;
        public final String instruction;
        public final double stepLat;
        public final double stepLng;
        public final double distanceToNextM;
        public final double remainingM;
        public final double remainingS;
        public final boolean offRoute;
        public final boolean arrived;

        State(int stepIndex, NavRoute.Step step, double distanceToNextM, double remainingM, double remainingS,
              boolean offRoute, boolean arrived) {
            this.stepIndex = stepIndex;
            this.instruction = step.instruction;
            this.stepLat = step.lat;
            this.stepLng = step.lng;
            this.distanceToNextM = distanceToNextM;
            this.remainingM = remainingM;
            this.remainingS = remainingS;
            this.offRoute = offRoute;
            this.arrived = arrived;
        }
    }

    private final NavRoute route;
    private final double[][] g;
    private final int last;
    private final double[] cumM;
    private final double lineM;
    private final boolean hasLine;
    private final double[] stepAtM;
    private final boolean haveStepTimes;

    private int stepIndex = 0;
    private int missedFixes = 0;
    private boolean arrived = false;
    private boolean firstFix = true;
    private int progressSeg = 0;
    private double progressM = 0;
    // See nav.js: until the first fix on the route, only moving further away
    // from it counts as off route, so a start off the road does not reroute.
    private boolean joined = false;
    private double startOffsetM = Double.NaN;

    public Navigator(NavRoute route) {
        if (route == null || route.steps.isEmpty()) throw new IllegalArgumentException("navigator needs a route with steps");
        this.route = route;
        this.g = route.geometry == null ? new double[0][] : route.geometry;
        this.last = route.steps.size() - 1;

        cumM = new double[Math.max(1, g.length)];
        for (int i = 1; i < g.length; i++) cumM[i] = cumM[i - 1] + distanceM(g[i - 1][0], g[i - 1][1], g[i][0], g[i][1]);
        lineM = cumM[cumM.length - 1];
        hasLine = g.length >= 2 && lineM > 0;

        stepAtM = new double[route.steps.size()];
        int from = 0;
        for (int s = 0; s < route.steps.size(); s++) {
            NavRoute.Step step = route.steps.get(s);
            int best = from;
            double bestD = Double.POSITIVE_INFINITY;
            for (int i = from; i < g.length; i++) {
                double d = distanceM(step.lat, step.lng, g[i][0], g[i][1]);
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            stepAtM[s] = hasLine ? cumM[best] : 0;
            from = best;
        }

        boolean times = true;
        for (NavRoute.Step s : route.steps) if (Double.isNaN(s.durationS) || Double.isInfinite(s.durationS)) times = false;
        haveStepTimes = times;
    }

    public static double distanceM(double aLat, double aLng, double bLat, double bLng) {
        final double r = 6371000;
        double dLat = Math.toRadians(bLat - aLat);
        double dLng = Math.toRadians(bLng - aLng);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(aLat)) * Math.cos(Math.toRadians(bLat)) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * r * Math.asin(Math.sqrt(h));
    }

    // Closest point to (lat, lng) on segment a-b: { distance m, t along it 0 to 1 }.
    static double[] projectOnSegment(double lat, double lng, double[] a, double[] b) {
        final double metresPerDegLat = 111195;
        double metresPerDegLng = metresPerDegLat * Math.cos(lat * Math.PI / 180);
        double ax = (a[1] - lng) * metresPerDegLng;
        double ay = (a[0] - lat) * metresPerDegLat;
        double bx = (b[1] - lng) * metresPerDegLng;
        double by = (b[0] - lat) * metresPerDegLat;
        double dx = bx - ax;
        double dy = by - ay;
        double lenSq = dx * dx + dy * dy;
        double t = lenSq == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / lenSq));
        double px = ax + t * dx;
        double py = ay + t * dy;
        return new double[] { Math.sqrt(px * px + py * py), t };
    }

    private double distanceToRouteM(double lat, double lng) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < g.length - 1; i++) {
            double d = projectOnSegment(lat, lng, g[i], g[i + 1])[0];
            if (d < best) best = d;
        }
        return best;
    }

    // Searches forward from current progress; the first stretch of segments
    // within OFF_ROUTE_M, and the closest point inside it. { seg, along m } or null.
    private double[] locate(double lat, double lng) {
        double[] found = null;
        double foundD = 0;
        for (int i = Math.max(0, progressSeg - BACKTRACK_SEGMENTS); i < g.length - 1; i++) {
            double[] p = projectOnSegment(lat, lng, g[i], g[i + 1]);
            if (p[0] <= OFF_ROUTE_M) {
                if (found == null || p[0] < foundD) {
                    found = new double[] { i, cumM[i] + p[1] * (cumM[i + 1] - cumM[i]) };
                    foundD = p[0];
                }
            } else if (found != null) {
                break;
            }
        }
        return found;
    }

    private double remainingMetres() {
        if (arrived) return 0;
        if (!hasLine) return route.distanceM;
        return Math.max(0, lineM - progressM) * (route.distanceM / lineM);
    }

    private double remainingSeconds(double remainingM) {
        if (arrived) return 0;
        if (!haveStepTimes || !hasLine || stepIndex == 0) {
            return route.distanceM > 0 ? route.durationS * (remainingM / route.distanceM) : route.durationS;
        }
        int prev = stepIndex - 1;
        double stretchM = stepAtM[stepIndex] - stepAtM[prev];
        double leftOnStretch = stretchM > 0 ? Math.max(0, Math.min(1, (stepAtM[stepIndex] - progressM) / stretchM)) : 0;
        double s = leftOnStretch * route.steps.get(prev).durationS;
        for (int j = stepIndex; j <= last; j++) s += route.steps.get(j).durationS;
        return s;
    }

    public State update(double lat, double lng) {
        double fromLineM = distanceToRouteM(lat, lng);
        boolean onRoute = fromLineM <= OFF_ROUTE_M;
        if (onRoute) joined = true;
        if (Double.isNaN(startOffsetM)) startOffsetM = fromLineM;
        boolean away = joined ? !onRoute : fromLineM > startOffsetM + OFF_ROUTE_M;
        missedFixes = away ? missedFixes + 1 : 0;

        if (onRoute && hasLine) {
            double[] at = locate(lat, lng);
            if (at != null && at[1] > progressM) {
                progressM = at[1];
                progressSeg = (int) at[0];
            }
        }

        NavRoute.Step first = route.steps.get(0);
        if (firstFix && "depart".equals(first.maneuver) && last > 0) {
            if (distanceM(lat, lng, first.lat, first.lng) > ARRIVE_RADIUS_M) stepIndex = 1;
        }
        firstFix = false;

        while (stepIndex < last
                && ((hasLine && stepAtM[stepIndex] < progressM)
                    || distanceM(lat, lng, route.steps.get(stepIndex).lat, route.steps.get(stepIndex).lng) <= ARRIVE_RADIUS_M)) {
            stepIndex++;
        }
        NavRoute.Step end = route.steps.get(last);
        if (stepIndex == last && distanceM(lat, lng, end.lat, end.lng) <= ARRIVE_RADIUS_M) arrived = true;

        NavRoute.Step next = route.steps.get(stepIndex);
        double distanceToNextM = 0;
        if (!arrived) {
            distanceToNextM = onRoute && hasLine
                    ? Math.max(0, stepAtM[stepIndex] - progressM)
                    : distanceM(lat, lng, next.lat, next.lng);
        }
        double remainingM = remainingMetres();
        return new State(stepIndex, next, distanceToNextM, remainingM, remainingSeconds(remainingM),
                missedFixes >= OFF_ROUTE_FIXES, arrived);
    }
}
