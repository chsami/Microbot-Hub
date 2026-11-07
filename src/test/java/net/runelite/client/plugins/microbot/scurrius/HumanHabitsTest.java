package net.runelite.client.plugins.microbot.scurrius;

import net.runelite.client.plugins.microbot.scurrius.PrayerClickPlan.Plan;
import net.runelite.client.plugins.microbot.scurrius.enums.PrayerMode;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumanHabitsTest {
    @Test
    void pausesAreRareBetweenOneAndEightSecondsAndOffWhenDisabled() {
        Random random = new Random(1);
        int pauses = 0;
        for (int i = 0; i < 4000; i++) {
            int ms = HumanHabits.pauseMs(random, true);
            if (ms != 0) {
                pauses++;
                assertTrue(ms >= 1_000 && ms <= 8_000);
            }
            assertEquals(0, HumanHabits.pauseMs(random, false));
        }
        assertTrue(pauses > 1200 && pauses < 2000, "pauses " + pauses); // about 40%
    }

    @Test
    void lateReclickIsUnderOneTickAndSeldom() {
        Random random = new Random(2);
        int late = 0;
        for (int i = 0; i < 4000; i++) {
            int ms = HumanHabits.reclickDelayMs(random, true);
            if (ms != 0) {
                late++;
                assertTrue(ms >= 100 && ms < 600);
            }
            assertEquals(0, HumanHabits.reclickDelayMs(random, false));
        }
        assertTrue(late > 250 && late < 750, "late " + late); // about 12%
    }

    @Test
    void earlyEatStaysAboveTheBaseAndUnderNinetyNine() {
        Random random = new Random(3);
        boolean raised = false;
        for (int i = 0; i < 2000; i++) {
            int threshold = HumanHabits.eatThreshold(70, random, true);
            assertTrue(threshold >= 70 && threshold <= 78);
            raised |= threshold > 70;
            assertEquals(70, HumanHabits.eatThreshold(70, random, false));
            assertTrue(HumanHabits.eatThreshold(98, random, true) <= 99);
        }
        assertTrue(raised);
    }

    @Test
    void cameraNudgeIsSmallAndNeverZero() {
        Random random = new Random(4);
        boolean left = false;
        boolean right = false;
        for (int i = 0; i < 1000; i++) {
            int degrees = HumanHabits.cameraNudgeDegrees(random);
            assertNotEquals(0, degrees);
            assertTrue(Math.abs(degrees) <= 30);
            left |= degrees < 0;
            right |= degrees > 0;
        }
        assertTrue(left && right);
    }

    @Test
    void mixedModePicksByWeightAndNeverPicksOneFlickMode() {
        Random random = new Random(5);
        int always = 0;
        for (int i = 0; i < 4000; i++) {
            PrayerMode mode = HumanHabits.pickKillMode(50, 50, random);
            assertTrue(mode == PrayerMode.ALWAYS_ON || mode == PrayerMode.LAZY_FLICK);
            if (mode == PrayerMode.ALWAYS_ON) always++;
        }
        assertTrue(always > 1700 && always < 2300, "always " + always);
        for (int i = 0; i < 200; i++) {
            assertEquals(PrayerMode.ALWAYS_ON, HumanHabits.pickKillMode(10, 0, random));
            assertEquals(PrayerMode.LAZY_FLICK, HumanHabits.pickKillMode(0, 10, random));
            assertEquals(PrayerMode.LAZY_FLICK, HumanHabits.pickKillMode(0, 0, random));
            assertEquals(PrayerMode.LAZY_FLICK, HumanHabits.pickKillMode(-5, -5, random));
        }
    }

    @Test
    void mouseClickIsOnlyUsedWithTimeLeftInTheTick() {
        assertEquals(Plan.INSTANT, PrayerClickPlan.decide(false, true, 0));
        assertEquals(Plan.MOUSE, PrayerClickPlan.decide(true, true, 100));
        assertEquals(Plan.MOUSE, PrayerClickPlan.decide(true, true, PrayerClickPlan.MOUSE_DEADLINE_MS));
        assertEquals(Plan.INSTANT, PrayerClickPlan.decide(true, true, PrayerClickPlan.MOUSE_DEADLINE_MS + 1));
        assertEquals(Plan.TAB_THEN_MOUSE, PrayerClickPlan.decide(true, false, 50));
        // a tab switch needed late falls back to the instant menu click
        assertEquals(Plan.INSTANT, PrayerClickPlan.decide(true, false, PrayerClickPlan.TAB_SWITCH_DEADLINE_MS + 1));
        assertTrue(PrayerClickPlan.mouseStillInTime(PrayerClickPlan.AFTER_TAB_DEADLINE_MS));
        assertFalse(PrayerClickPlan.mouseStillInTime(PrayerClickPlan.AFTER_TAB_DEADLINE_MS + 1));
    }
}
