package net.runelite.client.plugins.microbot.irkedmlm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import net.runelite.client.plugins.microbot.irkedmlm.enums.AfkParkSide;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MouseActivity;

/**
 * Pins that the mouse settings are genuinely different settings.
 *
 * <p>Before this, AFK/BALANCED/ACTIVE were the same behaviour at three park rates, and the park side
 * was hard-wired to left/right with no way to opt out. The point of these checks is that picking a
 * different option must actually change what the cursor does — not just how often it does one thing.
 */
class MouseBehaviourTest {

    // ── AfkParkSide ─────────────────────────────────────────────────────────

    @Test
    void everyParkSideHasACoherentDirection() {
        assertEquals(-1, AfkParkSide.LEFT.getDx());
        assertEquals(0, AfkParkSide.LEFT.getDy());
        assertEquals(1, AfkParkSide.RIGHT.getDx());
        assertEquals(-1, AfkParkSide.TOP.getDy());
        assertEquals(0, AfkParkSide.TOP.getDx());
        assertEquals(1, AfkParkSide.BOTTOM.getDy());

        assertTrue(AfkParkSide.LEFT.isHorizontal(), "LEFT must exit a vertical edge");
        assertTrue(AfkParkSide.RIGHT.isHorizontal(), "RIGHT must exit a vertical edge");
        assertTrue(!AfkParkSide.TOP.isHorizontal(), "TOP must exit a horizontal edge");
        assertTrue(!AfkParkSide.BOTTOM.isHorizontal(), "BOTTOM must exit a horizontal edge");
    }

    @Test
    void noneOptsOutOfLeavingTheCanvas() {
        assertTrue(!AfkParkSide.NONE.parksOffScreen(), "NONE must keep the cursor in the client");
        for (AfkParkSide side : EnumSet.of(AfkParkSide.LEFT, AfkParkSide.RIGHT,
                AfkParkSide.TOP, AfkParkSide.BOTTOM)) {
            assertTrue(side.parksOffScreen(), side + " should park off-screen");
        }
    }

    @Test
    void randomOnlyEverResolvesToARealEdge() {
        EnumSet<AfkParkSide> seen = EnumSet.noneOf(AfkParkSide.class);
        for (int i = 0; i < 500; i++) {
            AfkParkSide edge = AfkParkSide.randomEdge();
            assertTrue(edge != AfkParkSide.RANDOM && edge != AfkParkSide.NONE,
                    "randomEdge() returned the non-edge value " + edge);
            seen.add(edge);
        }
        // All four must be reachable, or "Random" silently means "Left or Right" again.
        assertEquals(4, seen.size());
    }

    // ── MouseActivity ───────────────────────────────────────────────────────

    @Test
    void theThreeMouseModesParkAtDifferentRates() {
        SessionPersonality p = SessionPersonality.neutral();
        int afk = MouseActivity.AFK.offScreenChance(p);
        int balanced = MouseActivity.BALANCED.offScreenChance(p);
        int active = MouseActivity.ACTIVE.offScreenChance(p);

        assertEquals(100, afk);
        assertTrue(active < balanced, "ACTIVE must park less than BALANCED (" + active + " vs " + balanced + ")");
        assertTrue(balanced < afk, "BALANCED must park less than AFK (" + balanced + " vs " + afk + ")");
        assertTrue(active > 0, "ACTIVE parking never is itself a tell");
    }

    @Test
    void afkDoesNothingOnCanvasWhileActiveDoesTheMost() {
        SessionPersonality p = SessionPersonality.neutral();

        assertTrue(MouseActivity.AFK.isPureAfk(), "AFK must short-circuit the on-canvas behaviour");
        assertEquals(0, MouseActivity.AFK.earlyHoverChance(p));
        assertEquals(0, MouseActivity.AFK.lateHoverChance(p));
        assertEquals(0, MouseActivity.AFK.inventoryGlanceChance(p));

        assertTrue(!MouseActivity.BALANCED.isPureAfk(), "BALANCED must keep its on-canvas behaviour");
        assertTrue(!MouseActivity.ACTIVE.isPureAfk(), "ACTIVE must keep its on-canvas behaviour");

        // The second axis: ACTIVE is not just BALANCED that parks less, it is busier while watching.
        assertTrue(MouseActivity.ACTIVE.earlyHoverChance(p) > MouseActivity.BALANCED.earlyHoverChance(p),
                "ACTIVE should pre-hover more than BALANCED");
        assertTrue(MouseActivity.ACTIVE.lateHoverChance(p) > MouseActivity.BALANCED.lateHoverChance(p),
                "ACTIVE should line up the next vein more than BALANCED");
        assertTrue(MouseActivity.ACTIVE.inventoryGlanceChance(p) > MouseActivity.BALANCED.inventoryGlanceChance(p),
                "ACTIVE should check the inventory more than BALANCED");
    }

    @Test
    void modeMultipliersStayWithinPercentBounds() {
        // Personalities are rolled per login; no trait may push a scaled chance outside 0..100.
        for (long seed = 0; seed < 400; seed++) {
            SessionPersonality p = SessionPersonality.roll(seed);
            for (MouseActivity mode : MouseActivity.values()) {
                assertInPercentRange(mode.offScreenChance(p), mode + ".offScreenChance");
                assertInPercentRange(mode.earlyHoverChance(p), mode + ".earlyHoverChance");
                assertInPercentRange(mode.lateHoverChance(p), mode + ".lateHoverChance");
                assertInPercentRange(mode.inventoryGlanceChance(p), mode + ".inventoryGlanceChance");
            }
        }
    }

    private static void assertInPercentRange(int value, String what) {
        assertTrue(value >= 0 && value <= 100, what + " out of range: " + value);
    }
}
