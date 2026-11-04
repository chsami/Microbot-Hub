package net.runelite.client.plugins.microbot.giantsfoundry;

import net.runelite.client.plugins.microbot.giantsfoundry.enums.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GiantsFoundryStateTest {

    private static final int[] LOW_RANGE = {30, 300};
    private static final int[] MED_RANGE = {370, 630};
    private static final int[] HIGH_RANGE = {700, 966};

    @Test
    void polishingWheelHeatsBeforeTheNextActionDropsBelowRange() {
        int change = GiantsFoundryState.getHeatChangeNeeded(39, Stage.POLISHING_WHEEL.getHeatChange(), LOW_RANGE);

        assertTrue(change > 0);
    }

    @Test
    void tripHammerHeatsBeforeTheNextActionDropsBelowRange() {
        int change = GiantsFoundryState.getHeatChangeNeeded(716, Stage.TRIP_HAMMER.getHeatChange(), HIGH_RANGE);

        assertTrue(change > 0);
    }

    @Test
    void grindstoneCoolsBeforeTheNextActionRisesAboveRange() {
        int change = GiantsFoundryState.getHeatChangeNeeded(620, Stage.GRINDSTONE.getHeatChange(), MED_RANGE);

        assertTrue(change < 0);
    }

    @Test
    void staysAtStationWhileTheNextActionRemainsInRange() {
        assertEquals(0, GiantsFoundryState.getHeatChangeNeeded(60, Stage.POLISHING_WHEEL.getHeatChange(), LOW_RANGE));
        assertEquals(0, GiantsFoundryState.getHeatChangeNeeded(800, Stage.TRIP_HAMMER.getHeatChange(), HIGH_RANGE));
        assertEquals(0, GiantsFoundryState.getHeatChangeNeeded(500, Stage.GRINDSTONE.getHeatChange(), MED_RANGE));
    }

    @Test
    void outOfRangeHeatStillMovesTowardTheRange() {
        assertEquals(10, GiantsFoundryState.getHeatChangeNeeded(20, Stage.POLISHING_WHEEL.getHeatChange(), LOW_RANGE));
        assertEquals(-10, GiantsFoundryState.getHeatChangeNeeded(310, Stage.POLISHING_WHEEL.getHeatChange(), LOW_RANGE));
        assertEquals(20, GiantsFoundryState.getHeatChangeNeeded(680, Stage.TRIP_HAMMER.getHeatChange(), HIGH_RANGE));
        assertEquals(-10, GiantsFoundryState.getHeatChangeNeeded(640, Stage.GRINDSTONE.getHeatChange(), MED_RANGE));
    }
}
