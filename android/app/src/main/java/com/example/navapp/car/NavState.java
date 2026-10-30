package com.example.navapp.car;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// The phone's navigation state, shared with the car screen. The web app pushes
// it through the NavState plugin on each navigation update; the car screen
// listens and redraws. Kept in memory only: nothing is stored on disk.
public final class NavState {

    public interface Listener {
        void onChanged();
    }

    private static volatile boolean active = false;
    private static volatile String instruction = "";
    private static volatile int distanceM = 0;
    // Android Auto Maneuver type and roundabout exit for the turn card (TurnTypes).
    private static volatile int turnType = 0;
    private static volatile int exitNumber = 0;
    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private NavState() {}

    public static boolean isActive() {
        return active;
    }

    public static String getInstruction() {
        return instruction;
    }

    public static int getDistanceM() {
        return distanceM;
    }

    public static int getTurnType() {
        return turnType;
    }

    public static int getExitNumber() {
        return exitNumber;
    }

    public static void update(boolean nowActive, String text, int meters) {
        update(nowActive, text, meters, 0, 0);
    }

    public static void update(boolean nowActive, String text, int meters, int turn, int exit) {
        active = nowActive;
        instruction = text == null ? "" : text;
        distanceM = Math.max(0, meters);
        turnType = turn;
        exitNumber = exit;
        for (Listener l : listeners) l.onChanged();
    }

    public static void addListener(Listener l) {
        listeners.add(l);
    }

    public static void removeListener(Listener l) {
        listeners.remove(l);
    }
}
