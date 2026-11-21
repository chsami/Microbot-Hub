package net.runelite.client.plugins.microbot.scurrius;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreaksTest {
    @Test
    void tripTargetIsInRangeAndSwappedRangesAreFixed() {
        Random random = new Random(3);
        boolean[] seen = new boolean[5];
        for (int i = 0; i < 1000; i++) {
            int target = Breaks.rollTripTarget(4, 2, random);
            assertTrue(target >= 2 && target <= 4);
            seen[target] = true;
        }
        assertTrue(seen[2] && seen[3] && seen[4]);
        assertEquals(2, Breaks.rollTripTarget(2, 2, random));
    }

    @Test
    void lengthIsBetweenMinAndMaxMinutesToTheSecond() {
        Random random = new Random(9);
        boolean notWholeMinute = false;
        for (int i = 0; i < 2000; i++) {
            long ms = Breaks.rollLengthMs(5, 40, random);
            assertTrue(ms >= 5 * 60_000L && ms <= 40 * 60_000L);
            assertEquals(0, ms % 1000);
            notWholeMinute |= ms % 60_000 != 0;
        }
        assertTrue(notWholeMinute);
    }

    @Test
    void lengthSwapsAndClampsLikeTheOtherRolls() {
        Random random = new Random(1);
        for (int i = 0; i < 500; i++) {
            long ms = Breaks.rollLengthMs(40, 5, random);
            assertTrue(ms >= 300_000L && ms <= 2_400_000L);
            assertEquals(60_000L, Breaks.rollLengthMs(0, 0, random));
        }
    }

    @Test
    void breakStartsOnlyWhenDueAtTheBankAndNothingElseOwnsTheLogin() {
        assertTrue(Breaks.shouldBreakNow(true, 2, 2, false, false, false));
        assertTrue(Breaks.shouldBreakNow(true, 5, 2, false, false, false));
        assertFalse(Breaks.shouldBreakNow(false, 2, 2, false, false, false)); // disabled
        assertFalse(Breaks.shouldBreakNow(true, 1, 2, false, false, false));  // not enough trips
        assertFalse(Breaks.shouldBreakNow(true, 2, 2, true, false, false));   // already on a break
        assertFalse(Breaks.shouldBreakNow(true, 2, 2, false, true, false));   // never in the boss room
        assertFalse(Breaks.shouldBreakNow(true, 2, 2, false, false, true));   // Microbot's Break Handler is on a break
        assertFalse(Breaks.shouldBreakNow(true, 2, 0, false, false, false));  // no target
    }

    @Test
    void remainingTimeIsFormattedForTheOverlay() {
        assertEquals("25:00", Breaks.formatRemaining(25 * 60_000L));
        assertEquals("1:05", Breaks.formatRemaining(65_000));
        assertEquals("0:01", Breaks.formatRemaining(1));
        assertEquals("0:00", Breaks.formatRemaining(-500));
    }
}
