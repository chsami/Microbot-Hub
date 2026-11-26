package net.runelite.client.plugins.microbot.irkedmlm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionPersonalityTest {

    /** The neutral/average player must reproduce the original hard-coded constants exactly, so
     *  swapping literals for personality accessors is behaviour-preserving at trait=0.5. */
    @Test
    void neutralReproducesBaseConstants() {
        SessionPersonality p = SessionPersonality.neutral();
        assertEquals(70, p.offScreenParkChance());
        assertEquals(10, p.earlyHoverChance());
        assertEquals(55, p.lateHoverChance());
        assertEquals(5,  p.misclickChance());
        assertEquals(12, p.slowOutlierChance());
        assertEquals(25, p.gearUpPauseChance());
        assertEquals(15, p.specHesitationChance());
        assertEquals(90, p.oneStrutSkipChance());
        assertEquals(8,  p.inventoryGlanceChance());
        assertEquals(1000L, p.reaction(1000L));
        assertEquals(67_500L, p.repairPersistenceMs());
    }

    /** Same seed → identical character (within-session stability is the whole point). */
    @Test
    void seededRollIsDeterministic() {
        SessionPersonality a = SessionPersonality.roll(1234L);
        SessionPersonality b = SessionPersonality.roll(1234L);
        assertEquals(a.getSpeed(), b.getSpeed());
        assertEquals(a.getAfkTendency(), b.getAfkTendency());
        assertEquals(a.offScreenParkChance(), b.offScreenParkChance());
    }

    /** Different seeds must actually produce different players (between-session variance). */
    @Test
    void differentSeedsDiffer() {
        boolean anyDifferent = false;
        int base = SessionPersonality.roll(1L).offScreenParkChance();
        for (long s = 2; s < 40; s++) {
            if (SessionPersonality.roll(s).offScreenParkChance() != base) {
                anyDifferent = true;
                break;
            }
        }
        assertTrue(anyDifferent, "personality should vary across logins");
    }

    /** Every rolled knob stays within its documented bounds and speed stays sane. */
    @Test
    void allKnobsStayInBounds() {
        for (long s = 0; s < 500; s++) {
            SessionPersonality p = SessionPersonality.roll(s);
            assertInRange("offScreen", p.offScreenParkChance(), 45, 88);
            assertInRange("earlyHover", p.earlyHoverChance(), 4, 18);
            assertInRange("lateHover", p.lateHoverChance(), 40, 72);
            assertInRange("misclick", p.misclickChance(), 1, 12);
            assertInRange("slowOutlier", p.slowOutlierChance(), 6, 20);
            assertInRange("oneStrutSkip", p.oneStrutSkipChance(), 82, 96);
            assertInRange("inventoryGlance", p.inventoryGlanceChance(), 3, 16);
            assertTrue(p.repairPersistenceMs() >= 45_000L && p.repairPersistenceMs() <= 90_000L,
                    "repairPersistenceMs out of [45000,90000]: " + p.repairPersistenceMs());
            assertTrue(p.getSpeed() >= 0.80 && p.getSpeed() <= 1.30, "speed in range");
            assertTrue(p.reaction(1000L) >= 800 && p.reaction(1000L) <= 1300, "reaction scaled by speed");
        }
    }

    private static void assertInRange(String name, int v, int min, int max) {
        assertTrue(v >= min && v <= max, name + " out of [" + min + "," + max + "]: " + v);
    }
}
