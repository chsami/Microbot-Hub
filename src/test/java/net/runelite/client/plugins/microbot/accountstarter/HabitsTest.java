package net.runelite.client.plugins.microbot.accountstarter;

import static org.junit.jupiter.api.Assertions.*;

import net.runelite.client.plugins.microbot.accountstarter.logic.ApPace;
import net.runelite.client.plugins.microbot.accountstarter.logic.Habits;
import java.util.Random;
import org.junit.jupiter.api.Test;

class HabitsTest {
    @Test
    void habitsStayInRange() {
        Random seeds = new Random(7);
        for (int i = 0; i < 1000; i++) {
            Habits h = Habits.draw(seeds.nextLong());
            assertTrue(h.getEatAtFraction() >= 0.35 && h.getEatAtFraction() <= 0.60);
            assertTrue(h.getBankAtFreeSlots() >= 0 && h.getBankAtFreeSlots() <= 3);
            assertTrue(h.getClaimBatch() >= 1 && h.getClaimBatch() <= 3);
            assertTrue(h.getBlockMinMs() >= 15 * 60_000L);
            assertTrue(h.getBlockMinMs() < h.getBlockMaxMs());
            assertTrue(h.getBlockMaxMs() <= 45 * 60_000L);
            assertTrue(h.getPlannerNoise() >= 0.2 && h.getPlannerNoise() <= 0.6);
            assertTrue(h.getGlanceAfterLevel() >= 0.2 && h.getGlanceAfterLevel() <= 0.7);
            assertTrue(h.getLootSkip() >= 0.05 && h.getLootSkip() <= 0.25);
            assertTrue(h.getGePremium() >= 1.05 && h.getGePremium() <= 1.15);
            assertTrue(h.getStuckAfterMs() >= 3 * 60_000L && h.getStuckAfterMs() <= 6 * 60_000L);
            assertTrue(h.getCheckPathsChance() >= 0.15 && h.getCheckPathsChance() <= 0.60);
            assertTrue(h.getBuyBuffer() >= 1.10 && h.getBuyBuffer() <= 1.40);
            assertTrue(h.getFoodStock() >= 6 && h.getFoodStock() <= 10);
            assertTrue(h.getRestMargin() >= 0.10 && h.getRestMargin() <= 0.25);
            assertEquals(h.getBuyBuffer(), h.tuning().getBuyBuffer());
            assertEquals(h.getClaimBatch(), h.tuning().getClaimBatch());
            ApPace p = ApPace.draw(seeds.nextLong());
            Random r = new Random(i);
            assertTrue(p.reactionMs(r) >= 140);
            assertTrue(p.readMs("some long text ".repeat(500), r) <= 2000);
        }
    }

    @Test
    void sameSeedSameHabits() {
        assertEquals(Habits.draw(42L), Habits.draw(42L));
        assertNotEquals(Habits.draw(42L), Habits.draw(43L));
    }
}
