package com.example.navapp.car;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FollowStateTest {

    @Test
    public void followsUntilTouched() {
        FollowState f = new FollowState();
        assertTrue(f.isFollowing());
        assertFalse(f.dueToReturn(1_000_000));
        f.touched(1000);
        assertFalse(f.isFollowing());
    }

    @Test
    public void goesBackToFollowingFifteenSecondsAfterTheLastTouch() {
        FollowState f = new FollowState();
        f.touched(1000);
        assertFalse(f.dueToReturn(1000 + FollowState.RETURN_AFTER_MS - 1));
        // Another touch restarts the wait.
        f.touched(10_000);
        assertFalse(f.dueToReturn(1000 + FollowState.RETURN_AFTER_MS));
        assertTrue(f.dueToReturn(10_000 + FollowState.RETURN_AFTER_MS));
    }

    @Test
    public void recenterFollowsAtOnce() {
        FollowState f = new FollowState();
        f.touched(1000);
        f.recenter();
        assertTrue(f.isFollowing());
        assertFalse(f.dueToReturn(1000 + FollowState.RETURN_AFTER_MS));
    }
}
