package net.runelite.client.plugins.microbot.scurrius;

import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XpTrackerTest {
    @Test
    void ratePerHourScalesTheGainOverTheElapsedTime() {
        assertEquals(36_000, XpTracker.perHour(1_000, 100_000)); // 1000 xp in 100 s
        assertEquals(1_000, XpTracker.perHour(1_000, 3_600_000));
        assertEquals(0, XpTracker.perHour(1_000, 0));
        assertEquals(0, XpTracker.perHour(0, 60_000));
    }

    @Test
    void formatsWithThousandsSeparatorsAndClockTime() {
        assertEquals("1,234,567", XpTracker.format(1_234_567));
        assertEquals("12", XpTracker.format(12));
        assertEquals("1:02:03", XpTracker.formatTime(3_723_000));
        assertEquals("0:00:09", XpTracker.formatTime(9_999));
        assertEquals("12,345 xp (45,600/h)", XpTracker.line(12_345, 12_345L * 3_600_000L / 45_600));
    }

    @Test
    void gainIsMeasuredFromTheXpTakenAtTheFirstLogin() {
        XpTracker tracker = new XpTracker();
        tracker.reset(0);
        tracker.startIfNeeded(false, skill -> 999); // logged out: nothing taken
        assertFalse(tracker.isStarted());
        assertEquals(0, tracker.gained(Skill.RANGED, 5_000));
        tracker.startIfNeeded(true, skill -> skill == Skill.RANGED ? 1_000 : 50);
        tracker.startIfNeeded(true, skill -> 777_777); // only once
        assertTrue(tracker.isStarted());
        assertEquals(500, tracker.gained(Skill.RANGED, 1_500));
        assertEquals(0, tracker.gained(Skill.MAGIC, 50)); // no gain: hidden by the overlay
        assertEquals(0, tracker.gained(Skill.RANGED, 900)); // never negative
        assertEquals(0, tracker.gained(Skill.PRAYER, 9_999)); // not tracked
    }

    @Test
    void runtimeCountsFromResetAndSurvivesBreaks() {
        XpTracker tracker = new XpTracker();
        tracker.reset(10_000);
        assertEquals(5_000, tracker.elapsedMs(15_000));
        assertEquals(0, tracker.elapsedMs(5_000));
        tracker.reset(20_000);
        tracker.startIfNeeded(true, skill -> 0);
        assertEquals(0, tracker.gained(Skill.HITPOINTS, 0));
    }
}
