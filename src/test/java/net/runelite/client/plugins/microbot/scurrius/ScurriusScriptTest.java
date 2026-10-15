package net.runelite.client.plugins.microbot.scurrius;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScurriusScriptTest {

    private final WorldArea fightRoom = new WorldArea(new WorldPoint(3290, 9860, 0), 17, 17);

    @Test
    void thresholdStaysInRangeWhenMinExceedsMax() {
        for (int i = 0; i < 500; i++) {
            int threshold = ScurriusScript.randomThreshold(60, 40);
            assertTrue(threshold >= 40 && threshold <= 60);
        }
    }

    @Test
    void thresholdStaysInRangeWhenOrdered() {
        for (int i = 0; i < 500; i++) {
            int threshold = ScurriusScript.randomThreshold(40, 60);
            assertTrue(threshold >= 40 && threshold <= 60);
        }
    }

    @Test
    void equalBoundsReturnThatValue() {
        assertEquals(50, ScurriusScript.randomThreshold(50, 50));
    }

    @Test
    void nullLocationIsNotInFightRoom() {
        assertFalse(ScurriusScript.isInArea(fightRoom, null));
    }

    @Test
    void locationInsideFightRoomIsDetected() {
        assertTrue(ScurriusScript.isInArea(fightRoom, new WorldPoint(3295, 9865, 0)));
        assertFalse(ScurriusScript.isInArea(fightRoom, new WorldPoint(3279, 9869, 0)));
    }
}
