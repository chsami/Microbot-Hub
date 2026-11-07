package net.runelite.client.plugins.microbot.scurrius;

import net.runelite.client.plugins.microbot.scurrius.PrayerFlickTiming.Decision;
import net.runelite.client.plugins.microbot.scurrius.PrayerFlickTiming.Style;
import net.runelite.client.plugins.microbot.scurrius.enums.PrayerMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrayerFlickTimingTest {
    private static final int DELAY = 2;

    /** An attack of {@code style} starts at {@code start} and its hit lands DELAY ticks later, fully blocked. */
    private static void attack(PrayerFlickTiming timing, int start, Style style) {
        timing.onAttackStarted(start, style);
        timing.onHit(start + DELAY, 0, style, false);
    }

    private static PrayerFlickTiming learned() {
        PrayerFlickTiming timing = new PrayerFlickTiming();
        int tick = 100;
        for (Style style : Style.values()) {
            attack(timing, tick, style);
            attack(timing, tick + 5, style);
            tick += 10;
        }
        return timing;
    }

    @Test
    void alwaysOnKeepsLastStyleAndDefaultsToMelee() {
        PrayerFlickTiming timing = new PrayerFlickTiming();
        assertEquals(Style.MELEE, timing.decide(1, PrayerMode.ALWAYS_ON, false).style);
        timing.onAttackStarted(2, Style.MAGIC);
        Decision decision = timing.decide(3, PrayerMode.ALWAYS_ON, false);
        assertEquals(Style.MAGIC, decision.style);
        assertFalse(decision.pulse);
    }

    @Test
    void delayIsTrustedOnlyAfterTwoEqualObservations() {
        PrayerFlickTiming timing = new PrayerFlickTiming();
        attack(timing, 10, Style.RANGE);
        assertEquals(0, timing.confirmedDelay(Style.RANGE));
        attack(timing, 20, Style.RANGE);
        assertEquals(DELAY, timing.confirmedDelay(Style.RANGE));
        assertFalse(timing.isLearned());
    }

    @Test
    void differingObservationsResetTheStreak() {
        PrayerFlickTiming timing = new PrayerFlickTiming();
        timing.onAttackStarted(10, Style.RANGE);
        timing.onHit(12, 0, Style.RANGE, false);
        timing.onAttackStarted(20, Style.RANGE);
        timing.onHit(23, 0, Style.RANGE, false);
        assertEquals(0, timing.confirmedDelay(Style.RANGE));
    }

    @Test
    void lazyFlickStaysOnUntilEveryStyleIsLearned() {
        PrayerFlickTiming timing = new PrayerFlickTiming();
        attack(timing, 10, Style.MELEE);
        attack(timing, 20, Style.MELEE);
        Decision decision = timing.decide(30, PrayerMode.LAZY_FLICK, false);
        assertEquals(Style.MELEE, decision.style);
        assertFalse(decision.pulse);
    }

    @Test
    void lazyFlickIsOnFromTheAnimationTickUntilTheHitLands() {
        PrayerFlickTiming timing = learned();
        timing.onAttackStarted(200, Style.MAGIC); // lands at 202
        assertEquals(Style.MAGIC, timing.decide(200, PrayerMode.LAZY_FLICK, false).style);
        assertNull(timing.decide(199, PrayerMode.LAZY_FLICK, false).style);
        assertEquals(Style.MAGIC, timing.decide(201, PrayerMode.LAZY_FLICK, false).style);
        assertEquals(Style.MAGIC, timing.decide(202, PrayerMode.LAZY_FLICK, false).style);
        timing.onHit(202, 0, Style.MAGIC, false);
        assertNull(timing.decide(202, PrayerMode.LAZY_FLICK, false).style);
        assertNull(timing.decide(203, PrayerMode.LAZY_FLICK, false).style);
    }

    @Test
    void lazyFlickDropsAMissedHitAfterTheWindow() {
        PrayerFlickTiming timing = learned();
        timing.onAttackStarted(200, Style.MELEE);
        assertEquals(Style.MELEE, timing.decide(202, PrayerMode.LAZY_FLICK, false).style);
        assertNull(timing.decide(203, PrayerMode.LAZY_FLICK, false).style);
    }

    @Test
    void oneTickFlickPulsesOnceAStyleIsKnown() {
        PrayerFlickTiming timing = new PrayerFlickTiming();
        assertFalse(timing.decide(1, PrayerMode.ONE_TICK_FLICK, false).pulse);
        timing.onAttackStarted(2, Style.RANGE);
        Decision decision = timing.decide(3, PrayerMode.ONE_TICK_FLICK, false);
        assertEquals(Style.RANGE, decision.style);
        assertTrue(decision.pulse);
    }

    @Test
    void ratsHoldThePrayerOnWithoutFlicking() {
        PrayerFlickTiming timing = learned();
        timing.onAttackStarted(200, Style.RANGE);
        for (PrayerMode mode : new PrayerMode[]{PrayerMode.LAZY_FLICK, PrayerMode.ONE_TICK_FLICK}) {
            Decision decision = timing.decide(200, mode, true);
            assertEquals(Style.RANGE, decision.style);
            assertFalse(decision.pulse);
        }
    }

    @Test
    void unprotectedHitHoldsPrayerOnAndForgetsThatStyle() {
        PrayerFlickTiming timing = learned();
        timing.onAttackStarted(200, Style.MELEE);
        timing.onHit(202, 14, null, false); // prayer was off: took damage
        assertEquals(0, timing.confirmedDelay(Style.MELEE));
        assertFalse(timing.isLearned());
        Decision decision = timing.decide(203, PrayerMode.LAZY_FLICK, false);
        assertEquals(Style.MELEE, decision.style);
        assertFalse(decision.pulse);
        assertEquals(Style.MELEE, timing.decide(200 + 2 + PrayerFlickTiming.UNPROTECTED_HOLD_TICKS - 1,
                PrayerMode.ONE_TICK_FLICK, false).style);
        assertFalse(timing.decide(202 + PrayerFlickTiming.UNPROTECTED_HOLD_TICKS - 1, PrayerMode.ONE_TICK_FLICK, false).pulse);
    }

    @Test
    void ambiguousHitsDoNotTeachOrPunish() {
        PrayerFlickTiming timing = new PrayerFlickTiming();
        timing.onAttackStarted(10, Style.RANGE);
        timing.onHit(11, 5, null, true); // a rock or a rat
        timing.onHit(12, 0, Style.RANGE, false);
        timing.onAttackStarted(20, Style.RANGE);
        timing.onHit(22, 0, Style.RANGE, false);
        assertEquals(2, timing.confirmedDelay(Style.RANGE));
    }

    @Test
    void damagingHitWithNoKnownAttackHoldsThePrayerOn() {
        PrayerFlickTiming timing = learned();
        timing.onHit(300, 9, null, false);
        assertEquals(Style.MAGIC, timing.decide(301, PrayerMode.LAZY_FLICK, false).style);
    }

    @Test
    void clearPendingKeepsLearnedDelays() {
        PrayerFlickTiming timing = learned();
        timing.onAttackStarted(200, Style.MELEE);
        timing.clearPending();
        assertTrue(timing.isLearned());
        assertNull(timing.decide(201, PrayerMode.LAZY_FLICK, false).style);
        timing.reset();
        assertFalse(timing.isLearned());
    }

    @Test
    void animationIdsMapToStyles() {
        assertEquals(Style.MELEE, ScurriusTickDriver.styleFor(10692));
        assertEquals(Style.MELEE, ScurriusTickDriver.styleFor(10693));
        assertEquals(Style.RANGE, ScurriusTickDriver.styleFor(10694));
        assertEquals(Style.RANGE, ScurriusTickDriver.styleFor(10695));
        assertEquals(Style.MAGIC, ScurriusTickDriver.styleFor(10696));
        assertEquals(Style.MAGIC, ScurriusTickDriver.styleFor(10697));
        assertNull(ScurriusTickDriver.styleFor(10698)); // stomp: not a prayer attack
        assertNull(ScurriusTickDriver.styleFor(-1));
    }

    @Test
    void projectileProvesTheStyleWhenTheAnimationShowedAnother() {
        PrayerFlickTiming timing = new PrayerFlickTiming();
        timing.onStyleSignal(10, Style.MELEE);          // animation
        timing.onStyleSignal(11, Style.MAGIC);          // projectile of the same attack corrects it
        assertEquals(Style.MAGIC, timing.decide(11, PrayerMode.ALWAYS_ON, false).style);
        assertEquals(Style.MAGIC, timing.decide(11, PrayerMode.ONE_TICK_FLICK, false).style);
    }

    @Test
    void projectileAloneStartsAnAttackWhenTheAnimationWasMissed() {
        PrayerFlickTiming timing = new PrayerFlickTiming();
        assertEquals(Style.MELEE, timing.decide(5, PrayerMode.ALWAYS_ON, false).style);
        timing.onStyleSignal(6, Style.MAGIC);
        assertEquals(Style.MAGIC, timing.decide(6, PrayerMode.ALWAYS_ON, false).style);
    }

    @Test
    void signalsOfTheSameStyleAreOneAttack() {
        PrayerFlickTiming timing = learned();
        timing.onStyleSignal(200, Style.RANGE);
        timing.onStyleSignal(201, Style.RANGE); // projectile of the same attack
        timing.onHit(202, 0, Style.RANGE, false);
        assertNull(timing.decide(203, PrayerMode.LAZY_FLICK, false).style); // nothing left pending
    }

    @Test
    void aSignalAfterTheWindowIsANewAttack() {
        PrayerFlickTiming timing = learned();
        timing.onStyleSignal(200, Style.RANGE);
        timing.onHit(202, 0, Style.RANGE, false);
        timing.onStyleSignal(205, Style.MAGIC);
        assertEquals(Style.MAGIC, timing.decide(205, PrayerMode.LAZY_FLICK, false).style);
    }

    @Test
    void projectileIdsMapToStyles() {
        assertEquals(Style.MAGIC, ScurriusTickDriver.projectileStyle(2640));
        assertEquals(Style.RANGE, ScurriusTickDriver.projectileStyle(2642));
        assertNull(ScurriusTickDriver.projectileStyle(2644)); // falling debris
    }
}
