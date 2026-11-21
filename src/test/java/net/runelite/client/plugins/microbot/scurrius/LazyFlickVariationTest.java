package net.runelite.client.plugins.microbot.scurrius;

import net.runelite.client.plugins.microbot.scurrius.PrayerFlickTiming.Decision;
import net.runelite.client.plugins.microbot.scurrius.PrayerFlickTiming.Style;
import net.runelite.client.plugins.microbot.scurrius.enums.PrayerMode;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lazy flick hold and variation: random, but a hit is never left unprotected and the prayer never churns. */
class LazyFlickVariationTest {
    private static PrayerFlickTiming learned(long seed, int delay, int holdMin, int holdMax, boolean variation) {
        PrayerFlickTiming timing = new PrayerFlickTiming(new Random(seed));
        timing.setLazyHold(holdMin, holdMax);
        timing.setLazyVariation(variation);
        int tick = 100;
        for (Style style : Style.values()) {
            for (int i = 0; i < 2; i++) {
                timing.onAttackStarted(tick, style);
                timing.onHit(tick + delay, 0, style, false);
                tick += 20;
            }
        }
        assertTrue(timing.isLearned());
        return timing;
    }

    /**
     * Plays attacks every {@code period} ticks and returns how many hits were unprotected. The prayer state at server
     * tick t is the decision made at tick t - 1 (a click is applied a tick later). Events of a tick come before its decision.
     */
    private static int unprotectedHits(PrayerFlickTiming timing, int delay, int period, int attacks, long styleSeed) {
        Random styles = new Random(styleSeed);
        int first = 1000;
        Style[] plan = new Style[attacks];
        for (int i = 0; i < attacks; i++) {
            plan[i] = Style.values()[styles.nextInt(3)];
        }
        Style active;             // prayer state during the current server tick
        Style decided = null;     // decision made last tick
        int unprotected = 0;
        int last = first + attacks * period + delay + 12;
        for (int tick = first - 3; tick <= last; tick++) {
            active = decided;
            int offset = tick - first;
            if (offset >= 0 && offset % period == 0 && offset / period < attacks) {
                timing.onStyleSignal(tick, plan[offset / period]);
            }
            // hits: the attack that started `delay` ticks ago lands now
            int hitOffset = tick - delay - first;
            if (hitOffset >= 0 && hitOffset % period == 0 && hitOffset / period < attacks) {
                Style style = plan[hitOffset / period];
                boolean protectedHit = active == style;
                if (!protectedHit) {
                    unprotected++;
                }
                timing.onHit(tick, protectedHit ? 0 : 12, active, false);
            }
            Decision decision = timing.decide(tick, PrayerMode.LAZY_FLICK, false);
            decided = decision.style;
        }
        return unprotected;
    }

    @Test
    void variationNeverLeavesAHitUnprotectedForAnyDelayHoldOrSeed() {
        for (int delay = 1; delay <= 6; delay++) {
            for (int holdMin = 0; holdMin <= 5; holdMin += 1) {
                for (int holdMax = holdMin; holdMax <= 5; holdMax += 2) {
                    for (long seed = 0; seed < 25; seed++) {
                        for (boolean variation : new boolean[]{true, false}) {
                            for (int period : new int[]{4, 5, 7}) {
                                PrayerFlickTiming timing = learned(seed, delay, holdMin, holdMax, variation);
                                int bad = unprotectedHits(timing, delay, period, 40, seed * 31 + delay);
                                assertEquals(0, bad, "delay " + delay + " hold " + holdMin + ".." + holdMax + " seed " + seed
                                        + " variation " + variation + " period " + period);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void holdKeepsThePrayerOnForTheRolledTicksAfterTheHit() {
        PrayerFlickTiming timing = learned(1, 2, 2, 2, false);
        timing.onStyleSignal(200, Style.RANGE); // lands 202
        timing.onHit(202, 0, Style.RANGE, false);
        assertEquals(2, timing.lastHoldTicks());
        assertEquals(Style.RANGE, timing.decide(202, PrayerMode.LAZY_FLICK, false).style);
        assertEquals(Style.RANGE, timing.decide(203, PrayerMode.LAZY_FLICK, false).style);
        assertNull(timing.decide(204, PrayerMode.LAZY_FLICK, false).style);
    }

    @Test
    void holdLengthStaysInTheConfiguredRange() {
        PrayerFlickTiming timing = learned(7, 2, 1, 3, false);
        boolean[] seen = new boolean[4];
        for (int i = 0; i < 200; i++) {
            int tick = 300 + i * 10;
            timing.onStyleSignal(tick, Style.MELEE);
            timing.onHit(tick + 2, 0, Style.MELEE, false);
            int held = timing.lastHoldTicks();
            assertTrue(held >= 1 && held <= 3, "held " + held);
            seen[held] = true;
        }
        assertTrue(seen[1] && seen[2] && seen[3]);
    }

    @Test
    void swappedHoldRangeIsPutRight() {
        PrayerFlickTiming timing = learned(3, 2, 4, 1, false);
        timing.onStyleSignal(400, Style.MELEE);
        timing.onHit(402, 0, Style.MELEE, false);
        assertTrue(timing.lastHoldTicks() >= 1 && timing.lastHoldTicks() <= 4);
    }

    @Test
    void anAttackDuringTheHoldOnlySwitchesTheStyleNeverOffThenOn() {
        PrayerFlickTiming timing = learned(1, 2, 3, 3, false);
        timing.onStyleSignal(200, Style.MELEE);
        timing.onHit(202, 0, Style.MELEE, false); // hold until 205
        assertEquals(Style.MELEE, timing.decide(202, PrayerMode.LAZY_FLICK, false).style);
        assertEquals(Style.MELEE, timing.decide(203, PrayerMode.LAZY_FLICK, false).style);
        timing.onStyleSignal(204, Style.MAGIC);   // lands 206, on from 205: the style switches at once, no gap
        assertEquals(Style.MAGIC, timing.decide(204, PrayerMode.LAZY_FLICK, false).style);
        assertEquals(Style.MAGIC, timing.decide(205, PrayerMode.LAZY_FLICK, false).style);
        assertNotNull(timing.decide(206, PrayerMode.LAZY_FLICK, false).style);
    }

    @Test
    void anUnseenHitStartsTheHoldTheTickAfterItShouldHaveLanded() {
        PrayerFlickTiming timing = learned(1, 2, 2, 2, false);
        timing.onStyleSignal(200, Style.RANGE); // lands 202, never seen
        assertEquals(Style.RANGE, timing.decide(202, PrayerMode.LAZY_FLICK, false).style);
        assertEquals(Style.RANGE, timing.decide(203, PrayerMode.LAZY_FLICK, false).style);
        assertEquals(Style.RANGE, timing.decide(204, PrayerMode.LAZY_FLICK, false).style);
        assertNull(timing.decide(205, PrayerMode.LAZY_FLICK, false).style);
    }

    @Test
    void variationChoosesEarlyImmediateAndKeepOnAttacksButNeverLate() {
        boolean sawEarly = false;
        boolean sawImmediate = false;
        boolean sawKeepOn = false;
        boolean sawNormal = false;
        for (long seed = 0; seed < 400; seed++) {
            PrayerFlickTiming timing = learned(seed, 4, 0, 0, true); // lands start + 4
            timing.onStyleSignal(500, Style.MAGIC);
            int onFrom = -1;
            String tag = "";
            for (int tick = 500; tick <= 504; tick++) {
                Decision decision = timing.decide(tick, PrayerMode.LAZY_FLICK, false);
                if (decision.style != null) {
                    onFrom = tick;
                    tag = decision.tag;
                    break;
                }
            }
            assertTrue(onFrom >= 500 && onFrom <= 503, "on from " + onFrom); // never later than landing - 1
            if (tag.equals("kept on")) sawKeepOn = true;
            else if (onFrom == 500) sawImmediate = true;
            else if (onFrom == 502) sawEarly = true;
            else if (onFrom == 503) sawNormal = true;
        }
        assertTrue(sawEarly && sawImmediate && sawKeepOn && sawNormal);
    }

    @Test
    void keepOnAttackStaysOnUntilTheNextAttack() {
        for (long seed = 0; seed < 400; seed++) {
            PrayerFlickTiming timing = learned(seed, 2, 0, 0, true);
            timing.onStyleSignal(600, Style.RANGE);
            Decision first = timing.decide(600, PrayerMode.LAZY_FLICK, false);
            if (!"kept on".equals(first.tag)) {
                continue;
            }
            timing.onHit(602, 0, Style.RANGE, false);
            for (int tick = 602; tick <= 620; tick++) {
                assertEquals(Style.RANGE, timing.decide(tick, PrayerMode.LAZY_FLICK, false).style, "seed " + seed + " tick " + tick);
            }
            return;
        }
        throw new AssertionError("no keep-on attack found in 400 seeds");
    }

    @Test
    void delaysStayInsideTheirRangesAndTheTick() {
        Random random = new Random(5);
        for (int i = 0; i < 2000; i++) {
            int on = PrayerFlickTiming.onDelayMs(random);
            int off = PrayerFlickTiming.offDelayMs(random);
            assertTrue(on >= 0 && on <= 120);
            assertTrue(off >= 30 && off <= 150);
        }
    }

    @Test
    void variationOffIsAlwaysOnAtTheTickBeforeTheHit() {
        for (long seed = 0; seed < 50; seed++) {
            PrayerFlickTiming timing = learned(seed, 3, 0, 0, false);
            timing.onStyleSignal(700, Style.MELEE); // lands 703
            assertNull(timing.decide(701, PrayerMode.LAZY_FLICK, false).style);
            assertEquals(Style.MELEE, timing.decide(702, PrayerMode.LAZY_FLICK, false).style);
        }
    }
}
