package net.runelite.client.plugins.microbot.giantsfoundry;

import org.junit.jupiter.api.Test;

import static net.runelite.client.plugins.microbot.giantsfoundry.enums.Stage.GRINDSTONE;
import static net.runelite.client.plugins.microbot.giantsfoundry.enums.Stage.POLISHING_WHEEL;
import static net.runelite.client.plugins.microbot.giantsfoundry.enums.Stage.TRIP_HAMMER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GiantsFoundryStateTest {

    private static final int[] LOW_RANGE = {30, 300};
    private static final int[] MED_RANGE = {370, 630};
    private static final int[] HIGH_RANGE = {700, 966};

    @Test
    void polishingWheelHeatsBeforeTheNextActionDropsBelowRange() {
        int change = GiantsFoundryState.getHeatChangeNeeded(39, LOW_RANGE, POLISHING_WHEEL, 9, TRIP_HAMMER);

        assertTrue(change > 0);
    }

    @Test
    void tripHammerHeatsBeforeTheNextActionDropsBelowRange() {
        int change = GiantsFoundryState.getHeatChangeNeeded(716, HIGH_RANGE, TRIP_HAMMER, 5, POLISHING_WHEEL);

        assertTrue(change > 0);
    }

    @Test
    void grindstoneCoolsBeforeTheNextActionRisesAboveRange() {
        int change = GiantsFoundryState.getHeatChangeNeeded(620, MED_RANGE, GRINDSTONE, 4, POLISHING_WHEEL);

        assertTrue(change < 0);
    }

    @Test
    void lookaheadIncludesPassiveDecayOverTheToolCycle() {
        assertTrue(GiantsFoundryState.getHeatChangeNeeded(LOW_RANGE[0] + 17, LOW_RANGE, POLISHING_WHEEL, 9, TRIP_HAMMER) > 0);
        assertTrue(GiantsFoundryState.getHeatChangeNeeded(HIGH_RANGE[0] + 27, HIGH_RANGE, TRIP_HAMMER, 5, POLISHING_WHEEL) > 0);
        assertTrue(GiantsFoundryState.getHeatChangeNeeded(MED_RANGE[1] - 13, MED_RANGE, GRINDSTONE, 4, POLISHING_WHEEL) < 0);
    }

    @Test
    void staysWhenHeatLandsExactlyOnTheBoundAfterDecay() {
        assertEquals(0, GiantsFoundryState.getHeatChangeNeeded(LOW_RANGE[0] + 18, LOW_RANGE, POLISHING_WHEEL, 9, TRIP_HAMMER));
        assertEquals(0, GiantsFoundryState.getHeatChangeNeeded(HIGH_RANGE[0] + 28, HIGH_RANGE, TRIP_HAMMER, 5, POLISHING_WHEEL));
        assertEquals(0, GiantsFoundryState.getHeatChangeNeeded(MED_RANGE[1] - 14, MED_RANGE, GRINDSTONE, 4, POLISHING_WHEEL));
    }

    @Test
    void staysAtStationWhileTheNextActionRemainsInRange() {
        assertEquals(0, GiantsFoundryState.getHeatChangeNeeded(60, LOW_RANGE, POLISHING_WHEEL, 9, TRIP_HAMMER));
        assertEquals(0, GiantsFoundryState.getHeatChangeNeeded(800, HIGH_RANGE, TRIP_HAMMER, 5, POLISHING_WHEEL));
        assertEquals(0, GiantsFoundryState.getHeatChangeNeeded(500, MED_RANGE, GRINDSTONE, 4, POLISHING_WHEEL));
    }

    @Test
    void finishesTheLastActionOfAStageInPlaceWhenTheNextStageUsesAnotherStation() {
        assertEquals(0, GiantsFoundryState.getHeatChangeNeeded(39, LOW_RANGE, POLISHING_WHEEL, 1, TRIP_HAMMER));
        assertEquals(0, GiantsFoundryState.getHeatChangeNeeded(716, HIGH_RANGE, TRIP_HAMMER, 1, GRINDSTONE));
        assertEquals(0, GiantsFoundryState.getHeatChangeNeeded(620, MED_RANGE, GRINDSTONE, 1, null));
    }

    @Test
    void stillLooksAheadOnTheLastActionWhenTheNextStageUsesTheSameStation() {
        int change = GiantsFoundryState.getHeatChangeNeeded(716, HIGH_RANGE, TRIP_HAMMER, 1, TRIP_HAMMER);

        assertTrue(change > 0);
    }

    @Test
    void outOfRangeHeatStillMovesTowardTheRange() {
        assertEquals(10, GiantsFoundryState.getHeatChangeNeeded(20, LOW_RANGE, POLISHING_WHEEL, 1, TRIP_HAMMER));
        assertEquals(-10, GiantsFoundryState.getHeatChangeNeeded(310, LOW_RANGE, POLISHING_WHEEL, 9, TRIP_HAMMER));
        assertEquals(20, GiantsFoundryState.getHeatChangeNeeded(680, HIGH_RANGE, TRIP_HAMMER, 1, TRIP_HAMMER));
        assertEquals(-10, GiantsFoundryState.getHeatChangeNeeded(640, MED_RANGE, GRINDSTONE, 4, POLISHING_WHEEL));
    }
}
