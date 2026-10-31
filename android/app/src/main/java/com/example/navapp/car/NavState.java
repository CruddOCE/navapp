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
    // For the car screen's map (CarMapRenderer): the route line as a JSON array
    // of [lat, lng] pairs, and the latest fix. Heading is NaN when unknown.
    private static volatile String routeJson = null;
    private static volatile boolean hasPosition = false;
    private static volatile double lat = 0;
    private static volatile double lng = 0;
    private static volatile double heading = Double.NaN;
    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public static String getRouteJson() {
        return routeJson;
    }

    public static boolean hasPosition() {
        return hasPosition;
    }

    public static double getLat() {
        return lat;
    }

    public static double getLng() {
        return lng;
    }

    public static double getHeading() {
        return heading;
    }

    // Set before update(), which tells the listeners.
    public static void setRoute(String geometryJson) {
        routeJson = geometryJson;
    }

    public static void setPosition(double latitude, double longitude, double bearing) {
        lat = latitude;
        lng = longitude;
        heading = bearing;
        hasPosition = true;
    }

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
