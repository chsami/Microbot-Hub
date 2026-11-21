package net.runelite.client.plugins.microbot.droslayer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DroSlayerCannonReclaimTest {
    @Test public void failedReclaimKeepsLastKnownSiteUntilConfirmedFound() {
        var recovery = new DroSlayerCannonReclaim();
        var tile = new net.runelite.api.coords.WorldPoint(3287,3944,0);
        recovery.remember(624,tile);
        recovery.missing(1);
        long now = 1 + DroSlayerCannonReclaim.DECAY_WAIT_MS;
        for (int i=0; i<3; i++) {
            assertTrue(recovery.startIfDue(now,true));
            recovery.retryLater(now);
            now += 600000;
        }
        assertEquals(624,recovery.lastWorld());
        assertEquals(tile,recovery.lastTile());
        recovery.found();
        assertEquals(0,recovery.lastWorld());
        assertNull(recovery.lastTile());
    }

    @Test public void waitsPastFullDecayAndDoesNotExtendOnRepeatedFailures() {
        var recovery = new DroSlayerCannonReclaim();
        recovery.missing(1000);
        recovery.missing(100000);
        assertFalse(recovery.startIfDue(1000 + 35 * 60_000L, true));
        assertFalse(recovery.active());
        assertFalse(recovery.startIfDue(1000 + DroSlayerCannonReclaim.DECAY_WAIT_MS, false));
        assertTrue(recovery.startIfDue(1000 + DroSlayerCannonReclaim.DECAY_WAIT_MS, true));
        assertEquals(DroSlayerCannonReclaim.Phase.BANK, recovery.phase());
    }
    @Test public void retriesAreBoundedAndFoundPartsCancelRecovery() {
        var recovery = new DroSlayerCannonReclaim();
        recovery.missing(1);
        long now = 1 + DroSlayerCannonReclaim.DECAY_WAIT_MS;
        for (int attempt = 0; attempt < 3; attempt++) {
            assertTrue(recovery.startIfDue(now, true));
            recovery.bankChecked();
            assertFalse(recovery.timedOut(now + 299999));
            assertTrue(recovery.timedOut(now + 300000));
            recovery.retryLater(now + 300000);
            now += 900000;
        }
        assertEquals(DroSlayerCannonReclaim.Phase.NONE, recovery.phase());
        recovery.missing(now);
        recovery.found();
        assertFalse(recovery.startIfDue(Long.MAX_VALUE, true));
    }
}
