package com.example.navapp.nav;

// Java copy of instructionFor in web/js/routing.js, for routes the location
// service fetches itself when it reroutes with the page hidden. Covers every
// OSRM manoeuvre type; InstructionsTest holds the same cases as routing.test.js.
public final class Instructions {

    private Instructions() {}

    // Left or right only, for wording where "slight" or "sharp" reads badly.
    static String sideOf(String modifier) {
        if (modifier.contains("left")) return "left";
        if (modifier.contains("right")) return "right";
        return "";
    }

    private static String tidy(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    // exit is 0 when OSRM gives no roundabout exit number.
    public static String forStep(String type, String modifier, String name, String ref, int exit) {
        String mod = modifier == null ? "" : modifier;
        String refText = ref == null || ref.isEmpty() ? "" : (ref.matches("\\d+") ? "route " + ref : ref);
        String roadName = name != null && !name.isEmpty() ? name : refText;
        String onto = roadName.isEmpty() ? "" : " onto " + roadName;
        String road = roadName.isEmpty() ? "the road" : roadName;
        String side = sideOf(mod);
        String t = type == null ? "" : type;

        if (mod.equals("uturn") && !t.equals("depart") && !t.equals("arrive")) return "Make a U-turn" + onto;
        switch (t) {
            case "depart":
                return "Head " + (mod.isEmpty() ? "on" : mod) + " " + road;
            case "arrive":
                return "You have arrived";
            case "turn":
            case "end of road":
                return mod.equals("straight") ? "Go straight" + onto : tidy("Turn " + mod + onto);
            case "fork":
                return "Keep " + (side.isEmpty() ? "straight" : side) + onto;
            case "merge":
                return tidy("Merge " + side + onto);
            case "on ramp":
                return "Take the ramp" + (side.isEmpty() ? "" : " on the " + side) + onto;
            case "off ramp":
                return "Take the exit" + (side.isEmpty() ? "" : " on the " + side) + onto;
            case "roundabout":
            case "rotary":
                return exit > 0 ? "At the roundabout, take exit " + exit + onto : "Take the roundabout" + onto;
            case "roundabout turn":
                return tidy("At the roundabout, turn " + mod + onto);
            case "exit roundabout":
            case "exit rotary":
                return "Exit the roundabout" + onto;
            case "new name":
                return roadName.isEmpty() ? "Continue" : "Continue onto " + roadName;
            default:
                return "Continue on " + road;
        }
    }
}
