package com.example.navapp.nav;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// Every OSRM manoeuvre gets a real Android Auto turn type, never UNKNOWN,
// because the car screen shows no turn card for UNKNOWN without an icon.
public class TurnTypesTest {

    private static int t(String type, String modifier) {
        return TurnTypes.of(type, modifier, 0, "left");
    }

    @Test
    public void turnsByDirectionAndSharpness() {
        assertEquals(TurnTypes.TURN_NORMAL_LEFT, t("turn", "left"));
        assertEquals(TurnTypes.TURN_NORMAL_RIGHT, t("turn", "right"));
        assertEquals(TurnTypes.TURN_SLIGHT_LEFT, t("turn", "slight left"));
        assertEquals(TurnTypes.TURN_SLIGHT_RIGHT, t("turn", "slight right"));
        assertEquals(TurnTypes.TURN_SHARP_LEFT, t("turn", "sharp left"));
        assertEquals(TurnTypes.TURN_SHARP_RIGHT, t("turn", "sharp right"));
        assertEquals(TurnTypes.STRAIGHT, t("turn", "straight"));
        assertEquals(TurnTypes.TURN_NORMAL_RIGHT, t("end of road", "right"));
        assertEquals(TurnTypes.STRAIGHT, t("continue", "straight"));
    }

    @Test
    public void uTurnSwingsAcrossTheOncomingLanes() {
        assertEquals(TurnTypes.U_TURN_RIGHT, TurnTypes.of("turn", "uturn", 0, "left"));
        assertEquals(TurnTypes.U_TURN_LEFT, TurnTypes.of("turn", "uturn", 0, "right"));
    }

    @Test
    public void forksMergesAndRamps() {
        assertEquals(TurnTypes.FORK_LEFT, t("fork", "slight left"));
        assertEquals(TurnTypes.FORK_RIGHT, t("fork", "right"));
        assertEquals(TurnTypes.MERGE_RIGHT, t("merge", "slight right"));
        assertEquals(TurnTypes.MERGE_SIDE_UNSPECIFIED, t("merge", ""));
        assertEquals(TurnTypes.ON_RAMP_SLIGHT_LEFT, t("on ramp", "slight left"));
        assertEquals(TurnTypes.ON_RAMP_NORMAL_RIGHT, t("on ramp", "right"));
        assertEquals(TurnTypes.OFF_RAMP_SLIGHT_LEFT, t("off ramp", "slight left"));
        assertEquals(TurnTypes.OFF_RAMP_NORMAL_RIGHT, t("off ramp", "right"));
    }

    @Test
    public void roundaboutsFollowTheTrafficSide() {
        int cw = TurnTypes.of("roundabout", "", 2, "left");
        assertEquals(TurnTypes.ROUNDABOUT_ENTER_AND_EXIT_CW, cw);
        assertTrue(TurnTypes.needsExitNumber(cw));
        assertEquals(TurnTypes.ROUNDABOUT_ENTER_AND_EXIT_CCW, TurnTypes.of("rotary", "", 1, "right"));
        assertEquals(TurnTypes.ROUNDABOUT_EXIT_CW, TurnTypes.of("exit roundabout", "", 0, "left"));
        assertFalse(TurnTypes.needsExitNumber(TurnTypes.ROUNDABOUT_EXIT_CW));
    }

    @Test
    public void departArriveAndEverythingElse() {
        assertEquals(TurnTypes.DEPART, t("depart", "left"));
        assertEquals(TurnTypes.DESTINATION_LEFT, t("arrive", "left"));
        assertEquals(TurnTypes.DESTINATION_RIGHT, t("arrive", "right"));
        assertEquals(TurnTypes.DESTINATION_STRAIGHT, t("arrive", "straight"));
        assertEquals(TurnTypes.DESTINATION, t("arrive", ""));
        assertEquals(TurnTypes.NAME_CHANGE, t("new name", ""));
        assertEquals(TurnTypes.STRAIGHT, t("notification", ""));
        assertEquals(TurnTypes.STRAIGHT, t("something new", ""));
    }

    @Test
    public void trafficSideComesFromTheNetworkCountryWhenKnown() {
        // OSRM's public server says "right" even in Australia.
        assertEquals("left", TurnTypes.drivingSide("au", "right"));
        assertEquals("left", TurnTypes.drivingSide("GB", "right"));
        assertEquals("right", TurnTypes.drivingSide("us", "left"));
        assertEquals("right", TurnTypes.drivingSide("", "right"));
        assertEquals("left", TurnTypes.drivingSide(null, "left"));
    }

    @Test
    public void numbersMatchTheCarAppLibrary() {
        assertEquals(androidx.car.app.navigation.model.Maneuver.TYPE_UNKNOWN, TurnTypes.UNKNOWN);
        assertEquals(androidx.car.app.navigation.model.Maneuver.TYPE_DEPART, TurnTypes.DEPART);
        assertEquals(androidx.car.app.navigation.model.Maneuver.TYPE_NAME_CHANGE, TurnTypes.NAME_CHANGE);
        assertEquals(androidx.car.app.navigation.model.Maneuver.TYPE_TURN_SLIGHT_LEFT, TurnTypes.TURN_SLIGHT_LEFT);
        assertEquals(androidx.car.app.navigation.model.Maneuver.TYPE_TURN_NORMAL_RIGHT, TurnTypes.TURN_NORMAL_RIGHT);
        assertEquals(androidx.car.app.navigation.model.Maneuver.TYPE_TURN_SHARP_LEFT, TurnTypes.TURN_SHARP_LEFT);
        assertEquals(androidx.car.app.navigation.model.Maneuver.TYPE_U_TURN_RIGHT, TurnTypes.U_TURN_RIGHT);
        assertEquals(androidx.car.app.navigation.model.Maneuver.TYPE_ON_RAMP_SHARP_RIGHT, TurnTypes.ON_RAMP_SHARP_RIGHT);
        assertEquals(androidx.car.app.navigation.model.Maneuver.TYPE_OFF_RAMP_NORMAL_RIGHT, TurnTypes.OFF_RAMP_NORMAL_RIGHT);
        assertEquals(androidx.car.app.navigation.model.Maneuver.TYPE_FORK_RIGHT, TurnTypes.FORK_RIGHT);
        assertEquals(androidx.car.app.navigation.model.Maneuver.TYPE_MERGE_SIDE_UNSPECIFIED, TurnTypes.MERGE_SIDE_UNSPECIFIED);
        assertEquals(androidx.car.app.navigation.model.Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW, TurnTypes.ROUNDABOUT_ENTER_AND_EXIT_CW);
        assertEquals(androidx.car.app.navigation.model.Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW, TurnTypes.ROUNDABOUT_ENTER_AND_EXIT_CCW);
        assertEquals(androidx.car.app.navigation.model.Maneuver.TYPE_ROUNDABOUT_EXIT_CW, TurnTypes.ROUNDABOUT_EXIT_CW);
        assertEquals(androidx.car.app.navigation.model.Maneuver.TYPE_ROUNDABOUT_EXIT_CCW, TurnTypes.ROUNDABOUT_EXIT_CCW);
        assertEquals(androidx.car.app.navigation.model.Maneuver.TYPE_STRAIGHT, TurnTypes.STRAIGHT);
        assertEquals(androidx.car.app.navigation.model.Maneuver.TYPE_DESTINATION, TurnTypes.DESTINATION);
        assertEquals(androidx.car.app.navigation.model.Maneuver.TYPE_DESTINATION_RIGHT, TurnTypes.DESTINATION_RIGHT);
    }

    @Test
    public void neverUnknown() {
        String[] types = { "turn", "end of road", "continue", "fork", "merge", "on ramp", "off ramp", "roundabout",
                "rotary", "roundabout turn", "exit roundabout", "exit rotary", "new name", "depart", "arrive",
                "notification", "", "weird" };
        String[] mods = { "", "left", "right", "slight left", "slight right", "sharp left", "sharp right", "straight", "uturn" };
        for (String type : types) {
            for (String mod : mods) {
                assertTrue(type + "/" + mod, TurnTypes.of(type, mod, 0, "left") != TurnTypes.UNKNOWN);
            }
        }
    }
}
