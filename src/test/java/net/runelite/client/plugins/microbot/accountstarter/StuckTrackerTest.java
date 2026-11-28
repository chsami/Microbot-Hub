package net.runelite.client.plugins.microbot.accountstarter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.accountstarter.logic.StuckTracker;
import org.junit.jupiter.api.Test;

class StuckTrackerTest {
    private static final long AFTER = 60_000L;
    private static final WorldPoint A = new WorldPoint(3200, 3200, 0);
    private static final WorldPoint B = new WorldPoint(3201, 3200, 0);

    @Test
    void movingIsNotStuck() {
        StuckTracker t = new StuckTracker();
        for (long ms = 0; ms <= 3 * AFTER; ms += 10_000L) {
            t.observe(ms, (ms / 10_000L) % 2 == 0 ? A : B, 100, "CHOP");
            assertFalse(t.stuck(ms, AFTER));
        }
    }

    @Test
    void xpIsNotStuck() {
        StuckTracker t = new StuckTracker();
        for (long ms = 0; ms <= 3 * AFTER; ms += 10_000L) {
            t.observe(ms, A, 100 + ms / 10_000L, "CHOP");
            assertFalse(t.stuck(ms, AFTER));
        }
    }

    @Test
    void standingStillIsStuck() {
        StuckTracker t = new StuckTracker();
        t.observe(0, A, 100, "CHOP");
        t.observe(AFTER - 1, A, 100, "CHOP");
        assertFalse(t.stuck(AFTER - 1, AFTER));
        t.observe(AFTER, A, 100, "CHOP");
        assertTrue(t.stuck(AFTER, AFTER));
        assertFalse(t.stuck(AFTER + 1, AFTER));
    }

    @Test
    void strikesCountPerActivity() {
        StuckTracker t = new StuckTracker();
        t.observe(0, A, 100, "CHOP");
        assertTrue(t.stuck(AFTER, AFTER));
        assertTrue(t.stuck(2 * AFTER, AFTER));
        t.observe(2 * AFTER, A, 100, "MINE");
        assertFalse(t.stuck(2 * AFTER + 1, AFTER));
        assertTrue(t.stuck(3 * AFTER, AFTER));
        assertEquals(2, t.strikes("CHOP"));
        assertEquals(1, t.strikes("MINE"));
        assertEquals(0, t.strikes("FISH"));
        t.reset();
        assertEquals(0, t.strikes("CHOP"));
    }

    @Test
    void pausedTimeIsNotStuckTime() {
        StuckTracker t = new StuckTracker();
        t.observe(0, A, 100, "CHOP");
        t.pause();
        t.observe(10 * AFTER, A, 100, "CHOP");
        assertFalse(t.stuck(10 * AFTER, AFTER));
        assertTrue(t.stuck(11 * AFTER, AFTER));
    }

    @Test
    void xpClearsOldStrikes() {
        StuckTracker t = new StuckTracker();
        t.observe(0, A, 100, "CHOP");
        assertTrue(t.stuck(AFTER, AFTER));
        assertEquals(1, t.strikes("CHOP"));
        t.observe(AFTER + 1, A, 150, "CHOP");
        assertEquals(0, t.strikes("CHOP"));
    }
}
