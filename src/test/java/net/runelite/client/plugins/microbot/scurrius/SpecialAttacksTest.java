package net.runelite.client.plugins.microbot.scurrius;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialAttacksTest {
    /** Everything in order for a spec at 50 energy needed 50: enabled, alive, attacking him, not held, no attempt pending. */
    private static boolean spec(int energy, int needed, boolean alive, boolean attackingBoss, boolean held, boolean pending,
                                boolean cooldownOver, boolean delayElapsed, int hp, int skip) {
        return SpecialAttacks.shouldSpec(true, energy, needed, alive, attackingBoss, held, pending, cooldownOver, delayElapsed, hp, skip);
    }

    @Test
    void specsWhenEnergyIsEnoughAndNothingBlocksIt() {
        assertTrue(spec(50, 50, true, true, false, false, true, true, 100, 0));
        assertTrue(spec(100, 50, true, true, false, false, true, true, 40, 0));
        assertFalse(spec(49, 50, true, true, false, false, true, true, 100, 0));
    }

    @Test
    void neverOnRatsDeadBossHeldAttacksOrAPendingAttempt() {
        assertFalse(spec(100, 50, true, false, false, false, true, true, 100, 0)); // not attacking Scurrius (a rat)
        assertFalse(spec(100, 50, false, true, false, false, true, true, 100, 0)); // dead
        assertFalse(spec(100, 50, true, true, true, false, true, true, 100, 0));   // dodging
        assertFalse(spec(100, 50, true, true, false, true, true, true, 100, 0));   // still verifying one
        assertFalse(spec(100, 50, true, true, false, false, false, true, 100, 0)); // retry cooldown
        assertFalse(spec(100, 50, true, true, false, false, true, false, 100, 0)); // human delay not over
        assertFalse(SpecialAttacks.shouldSpec(false, 100, 50, true, true, false, false, true, true, 100, 0)); // disabled
    }

    @Test
    void skipsWhenTheBossIsNearlyDeadIfConfigured() {
        assertFalse(spec(100, 50, true, true, false, false, true, true, 9, 10));
        assertTrue(spec(100, 50, true, true, false, false, true, true, 10, 10));
        assertTrue(spec(100, 50, true, true, false, false, true, true, 5, 0)); // 0 = never skip
    }

    @Test
    void neededEnergyPrefersTheConfigThenTheWeaponCostThenFifty() {
        assertEquals(35, SpecialAttacks.neededPercent(35, 500));
        assertEquals(50, SpecialAttacks.neededPercent(0, 500));  // toxic blowpipe
        assertEquals(25, SpecialAttacks.neededPercent(0, 250));  // dragon dagger
        assertEquals(100, SpecialAttacks.neededPercent(0, 1000));
        assertEquals(55, SpecialAttacks.neededPercent(0, 550));
        assertEquals(50, SpecialAttacks.neededPercent(0, 0));    // unknown weapon
        assertEquals(100, SpecialAttacks.neededPercent(250, 0)); // clamped
    }

    @Test
    void humanDelayIsUnderOneAndAHalfSecondsAndZeroWhenOff() {
        Random random = new Random(8);
        boolean varied = false;
        for (int i = 0; i < 1000; i++) {
            int ms = SpecialAttacks.delayMs(random, true);
            assertTrue(ms >= 0 && ms <= 1500);
            varied |= ms > 0;
            assertEquals(0, SpecialAttacks.delayMs(random, false));
        }
        assertTrue(varied);
    }

    @Test
    void aSpecCountsAsFiredOnlyWhenTheEnergyDropped() {
        assertTrue(SpecialAttacks.fired(1000, 500));
        assertFalse(SpecialAttacks.fired(1000, 1000));
        assertFalse(SpecialAttacks.fired(500, 510)); // regeneration is not a spec
    }
}
