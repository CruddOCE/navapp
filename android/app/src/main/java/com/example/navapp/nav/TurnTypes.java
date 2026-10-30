package com.example.navapp.nav;

// Maps an OSRM manoeuvre (type, direction, roundabout exit, traffic side) to
// Android Auto's Maneuver type. The car screen will not show a turn card for
// TYPE_UNKNOWN without an icon, so every step gets a real type; anything this
// does not recognise becomes straight. The numbers are Maneuver.TYPE_* in the
// Car App Library (copied so this runs in JVM tests without Android classes),
// and TurnTypesTest checks the mapping.
public final class TurnTypes {

    public static final int UNKNOWN = 0;
    public static final int DEPART = 1;
    public static final int NAME_CHANGE = 2;
    public static final int KEEP_LEFT = 3;
    public static final int KEEP_RIGHT = 4;
    public static final int TURN_SLIGHT_LEFT = 5;
    public static final int TURN_SLIGHT_RIGHT = 6;
    public static final int TURN_NORMAL_LEFT = 7;
    public static final int TURN_NORMAL_RIGHT = 8;
    public static final int TURN_SHARP_LEFT = 9;
    public static final int TURN_SHARP_RIGHT = 10;
    public static final int U_TURN_LEFT = 11;
    public static final int U_TURN_RIGHT = 12;
    public static final int ON_RAMP_SLIGHT_LEFT = 13;
    public static final int ON_RAMP_SLIGHT_RIGHT = 14;
    public static final int ON_RAMP_NORMAL_LEFT = 15;
    public static final int ON_RAMP_NORMAL_RIGHT = 16;
    public static final int ON_RAMP_SHARP_LEFT = 17;
    public static final int ON_RAMP_SHARP_RIGHT = 18;
    public static final int OFF_RAMP_SLIGHT_LEFT = 21;
    public static final int OFF_RAMP_SLIGHT_RIGHT = 22;
    public static final int OFF_RAMP_NORMAL_LEFT = 23;
    public static final int OFF_RAMP_NORMAL_RIGHT = 24;
    public static final int FORK_LEFT = 25;
    public static final int FORK_RIGHT = 26;
    public static final int MERGE_LEFT = 27;
    public static final int MERGE_RIGHT = 28;
    public static final int MERGE_SIDE_UNSPECIFIED = 29;
    public static final int ROUNDABOUT_ENTER_AND_EXIT_CW = 32;
    public static final int ROUNDABOUT_ENTER_AND_EXIT_CCW = 34;
    public static final int STRAIGHT = 36;
    public static final int DESTINATION = 39;
    public static final int DESTINATION_STRAIGHT = 40;
    public static final int DESTINATION_LEFT = 41;
    public static final int DESTINATION_RIGHT = 42;
    public static final int ROUNDABOUT_EXIT_CW = 44;
    public static final int ROUNDABOUT_EXIT_CCW = 46;

    private TurnTypes() {}

    // Countries that drive on the left, by ISO 3166 code. OSRM's public server
    // reports driving_side "right" even in Australia (checked 2026-10-31), so the
    // phone's network country decides when it is known.
    private static final java.util.Set<String> LEFT_HAND_TRAFFIC = new java.util.HashSet<>(java.util.Arrays.asList(
            "ag", "ai", "au", "bb", "bd", "bm", "bn", "bs", "bt", "bw", "cc", "ck", "cx", "cy", "dm", "fj", "fk",
            "gb", "gd", "gg", "gy", "hk", "id", "ie", "im", "in", "je", "jm", "jp", "ke", "ki", "kn", "ky", "lc",
            "lk", "ls", "mo", "ms", "mt", "mu", "mv", "mw", "my", "mz", "na", "nf", "np", "nr", "nz", "pg", "pk",
            "pn", "sb", "sc", "sg", "sh", "sr", "sz", "tc", "th", "tl", "to", "tt", "tv", "tz", "ug", "vc", "vg",
            "vi", "ws", "za", "zm", "zw"));

    // "left" or "right" for a network country code, or the route's own value
    // when the country is not known (no SIM, flight mode).
    public static String drivingSide(String countryIso, String fromRoute) {
        if (countryIso == null || countryIso.isEmpty()) return fromRoute;
        return LEFT_HAND_TRAFFIC.contains(countryIso.toLowerCase(java.util.Locale.ROOT)) ? "left" : "right";
    }

    public static int of(NavRoute.Step step, String drivingSide) {
        return of(step.maneuver, step.modifier, step.exit, drivingSide);
    }

    public static int of(String type, String modifier, int exit, String drivingSide) {
        String t = type == null ? "" : type;
        String m = modifier == null ? "" : modifier;
        boolean left = m.contains("left");
        boolean right = m.contains("right");
        // Traffic on the left (Australia) goes round a roundabout clockwise.
        boolean clockwise = "left".equals(drivingSide);

        if (m.equals("uturn") && !t.equals("depart") && !t.equals("arrive")) {
            // A U-turn swings across the oncoming lanes: right where traffic keeps left.
            return clockwise ? U_TURN_RIGHT : U_TURN_LEFT;
        }
        switch (t) {
            case "depart":
                return DEPART;
            case "arrive":
                if (left) return DESTINATION_LEFT;
                if (right) return DESTINATION_RIGHT;
                if (m.equals("straight")) return DESTINATION_STRAIGHT;
                return DESTINATION;
            case "turn":
            case "end of road":
            case "continue":
                return turn(m, left, right);
            case "fork":
                return left ? FORK_LEFT : right ? FORK_RIGHT : STRAIGHT;
            case "merge":
                return left ? MERGE_LEFT : right ? MERGE_RIGHT : MERGE_SIDE_UNSPECIFIED;
            case "on ramp":
                if (m.startsWith("slight")) return left ? ON_RAMP_SLIGHT_LEFT : ON_RAMP_SLIGHT_RIGHT;
                if (m.startsWith("sharp")) return left ? ON_RAMP_SHARP_LEFT : ON_RAMP_SHARP_RIGHT;
                if (left) return ON_RAMP_NORMAL_LEFT;
                if (right) return ON_RAMP_NORMAL_RIGHT;
                return STRAIGHT;
            case "off ramp":
                if (m.startsWith("slight")) return left ? OFF_RAMP_SLIGHT_LEFT : OFF_RAMP_SLIGHT_RIGHT;
                if (left) return OFF_RAMP_NORMAL_LEFT;
                if (right) return OFF_RAMP_NORMAL_RIGHT;
                return STRAIGHT;
            case "roundabout":
            case "rotary":
            case "roundabout turn":
                return clockwise ? ROUNDABOUT_ENTER_AND_EXIT_CW : ROUNDABOUT_ENTER_AND_EXIT_CCW;
            case "exit roundabout":
            case "exit rotary":
                return clockwise ? ROUNDABOUT_EXIT_CW : ROUNDABOUT_EXIT_CCW;
            case "new name":
                return NAME_CHANGE;
            default:
                return STRAIGHT;
        }
    }

    private static int turn(String m, boolean left, boolean right) {
        if (m.startsWith("slight")) return left ? TURN_SLIGHT_LEFT : TURN_SLIGHT_RIGHT;
        if (m.startsWith("sharp")) return left ? TURN_SHARP_LEFT : TURN_SHARP_RIGHT;
        if (left) return TURN_NORMAL_LEFT;
        if (right) return TURN_NORMAL_RIGHT;
        return STRAIGHT;
    }

    // Whether the car screen needs a roundabout exit number for this type.
    public static boolean needsExitNumber(int type) {
        return type == ROUNDABOUT_ENTER_AND_EXIT_CW || type == ROUNDABOUT_ENTER_AND_EXIT_CCW;
    }
}
