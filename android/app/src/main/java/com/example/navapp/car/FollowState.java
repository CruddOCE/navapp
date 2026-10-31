package com.example.navapp.car;

// Whether the car map follows your position. Dragging or zooming stops it, so
// you can look around; Recenter, or RETURN_AFTER_MS without a touch, starts it
// again, so a quick look can never leave the driver watching the wrong place.
// Plain Java with an injected clock, so FollowStateTest can run it.
public final class FollowState {

    public static final long RETURN_AFTER_MS = 15000;

    private boolean following = true;
    private long lastTouchAt = 0;

    public boolean isFollowing() {
        return following;
    }

    // A drag, pinch or zoom button.
    public void touched(long now) {
        following = false;
        lastTouchAt = now;
    }

    public void recenter() {
        following = true;
    }

    // True once, when the quiet time has passed; the caller then recenters.
    public boolean dueToReturn(long now) {
        return !following && now - lastTouchAt >= RETURN_AFTER_MS;
    }
}
