package com.example.navapp.nav;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

// The same cases as "every OSRM manoeuvre type gets its own wording" and the
// route number test in test/routing.test.js, so the Java and JavaScript
// wording stay the same.
public class InstructionsTest {

    private static String w(String type, String modifier) {
        return Instructions.forStep(type, modifier, "High St", "", 0);
    }

    @Test
    public void everyManoeuvreTypeGetsItsOwnWording() {
        assertEquals("Turn left onto High St", w("turn", "left"));
        assertEquals("Go straight onto High St", w("turn", "straight"));
        assertEquals("Turn right onto High St", w("end of road", "right"));
        assertEquals("Make a U-turn onto High St", w("turn", "uturn"));
        assertEquals("Make a U-turn onto High St", w("continue", "uturn"));
        assertEquals("Keep left onto High St", w("fork", "slight left"));
        assertEquals("Keep straight onto High St", w("fork", "straight"));
        assertEquals("Merge right onto High St", w("merge", "slight right"));
        assertEquals("Take the ramp on the right onto High St", w("on ramp", "right"));
        assertEquals("Take the exit on the left onto High St", w("off ramp", "slight left"));
        assertEquals("At the roundabout, take exit 3 onto High St", Instructions.forStep("roundabout", "", "High St", "", 3));
        assertEquals("Take the roundabout onto High St", w("rotary", ""));
        assertEquals("At the roundabout, turn left onto High St", w("roundabout turn", "left"));
        assertEquals("Exit the roundabout onto High St", w("exit roundabout", ""));
        assertEquals("Exit the roundabout onto High St", w("exit rotary", ""));
        assertEquals("Continue onto High St", w("new name", ""));
        assertEquals("Continue on High St", w("continue", "straight"));
        assertEquals("Continue on High St", w("notification", ""));
        assertEquals("Head left Mounts Bay Rd", Instructions.forStep("depart", "left", "Mounts Bay Rd", "", 0));
        assertEquals("You have arrived", w("arrive", ""));
    }

    @Test
    public void unnamedRoadsUseTheirNumber() {
        assertEquals("Take the exit on the left onto route 1", Instructions.forStep("off ramp", "left", "", "1", 0));
        assertEquals("Merge left onto M1", Instructions.forStep("merge", "left", "", "M1", 0));
        assertEquals("Continue", Instructions.forStep("new name", "", "", "", 0));
    }
}
