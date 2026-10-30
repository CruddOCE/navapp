package com.example.navapp.nav;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

// Java copy of web/js/voice.js: each manoeuvre is announced once at 400 m,
// 100 m and 20 m, and a jump past several thresholds speaks only the closest.
// A turn is known by its instruction and position, not its step number, since a
// reroute restarts the numbering. Kept in step with voice.js by the same
// fixture as Navigator (NavigatorParityTest).
public final class VoicePrompter {

    public interface Speaker {
        void speak(String text);
    }

    static final int[] PROMPT_DISTANCES_M = { 400, 100, 20 };

    private final Speaker speaker;
    private String stepKey = null;
    private Set<Object> spoken = new HashSet<>();

    public VoicePrompter(Speaker speaker) {
        this.speaker = speaker;
    }

    static String keyFor(Navigator.State s) {
        return s.instruction + "@" + s.stepLat + "," + s.stepLng;
    }

    public void onUpdate(Navigator.State s) {
        String key = keyFor(s);
        if (!key.equals(stepKey)) {
            stepKey = key;
            spoken = new HashSet<>();
        }
        if (s.arrived) {
            if (!spoken.contains("arrived")) {
                spoken.add("arrived");
                speaker.speak("You have arrived");
            }
            return;
        }
        int closest = Integer.MAX_VALUE;
        for (int t : PROMPT_DISTANCES_M) {
            if (s.distanceToNextM <= t && !spoken.contains(t) && t < closest) closest = t;
        }
        if (closest == Integer.MAX_VALUE) return;
        for (int t : PROMPT_DISTANCES_M) if (t >= closest) spoken.add(t);
        speaker.speak(closest == PROMPT_DISTANCES_M[2]
                ? s.instruction
                : "In " + closest + " metres, " + lowerFirst(s.instruction));
    }

    private static String lowerFirst(String s) {
        return s == null || s.isEmpty() ? s : s.substring(0, 1).toLowerCase(Locale.ROOT) + s.substring(1);
    }
}
