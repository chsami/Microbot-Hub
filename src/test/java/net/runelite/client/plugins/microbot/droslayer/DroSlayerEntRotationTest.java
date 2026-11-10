package net.runelite.client.plugins.microbot.droslayer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DroSlayerEntRotationTest {
    @Test public void revenantRotationStopsAtTwoKillsOrOneKillWithNoLiveTargets() {
        var rotation = new DroSlayerEntRotation();
        assertFalse(rotation.readyToHop(337, 2, false));
        rotation.killed(337, 1);
        assertFalse(rotation.readyToHop(337, 2, true));
        assertTrue(rotation.readyToHop(337, 2, false));
        rotation.killed(337, 1);
        assertFalse(rotation.readyToHop(337, 2, true));
        rotation.killed(337, 2);
        assertTrue(rotation.readyToHop(337, 2, true));
        assertFalse(rotation.readyToHop(338, 2, false));
    }

    @Test public void oneConfirmedKillAllowsRotationAndWorldChangeClearsIt() {
        DroSlayerEntRotation rotation = new DroSlayerEntRotation();
        assertFalse(rotation.pending(337));
        rotation.killed(337, 1);
        assertTrue(rotation.pending(337));
        rotation.killed(337, 1);
        assertTrue(rotation.pending(337));
        rotation.killed(337, 2);
        assertTrue(rotation.pending(337));
        assertFalse(rotation.pending(-1));
        assertTrue(rotation.pending(337));
        assertFalse(rotation.pending(338));
    }
    @Test public void failedHopsAreSpacedAndConfirmedWorldChangeResetsBudget() {
        DroSlayerEntRotation rotation = new DroSlayerEntRotation();
        rotation.killed(337, 1);
        rotation.killed(337, 2);
        assertTrue(rotation.tryHop(1000));
        assertFalse(rotation.tryHop(5999));
        assertTrue(rotation.pending(337));
        assertTrue(rotation.tryHop(6000));
        assertFalse(rotation.pending(338));
        rotation.killed(338, 3);
        assertTrue(rotation.pending(338));
    }
    @Test public void taskNamesAreExactAndCaseInsensitive() {
        assertTrue(DroSlayerEntRotation.isEntTask("ENTS"));
        assertTrue(DroSlayerEntRotation.isEntTask(" Ent "));
        assertFalse(DroSlayerEntRotation.isEntTask("Ancient wyverns"));
        assertFalse(DroSlayerEntRotation.isEntTask(null));
    }
}
