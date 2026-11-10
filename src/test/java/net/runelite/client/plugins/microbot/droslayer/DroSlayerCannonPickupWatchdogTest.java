package net.runelite.client.plugins.microbot.droslayer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DroSlayerCannonPickupWatchdogTest {
    @Test public void roguesApproachCannotExhaustMissingObjectGracePeriod() {
        var watchdog = new DroSlayerCannonPickupWatchdog();
        assertFalse(watchdog.missing(0,false));
        assertFalse(watchdog.missing(6000,false)); // Original failure happened while still moving.
        assertFalse(watchdog.missing(9000,true));
        assertFalse(watchdog.missing(18999,true));
        assertTrue(watchdog.missing(19000,true));
    }
    @Test public void movementOrVisibleObjectResetsObservation() {
        var watchdog = new DroSlayerCannonPickupWatchdog();
        assertFalse(watchdog.missing(0,true));
        assertFalse(watchdog.missing(9000,false));
        assertFalse(watchdog.missing(10000,true));
        assertFalse(watchdog.missing(19000,true));
        watchdog.reset();
        assertFalse(watchdog.missing(21000,true));
    }
}
