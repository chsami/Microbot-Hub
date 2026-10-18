package net.runelite.client.plugins.microbot.virewatch;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PVirewatchScriptTest {

    private final WorldPoint start = new WorldPoint(3600, 3360, 0);
    private final WorldArea fightArea = new WorldArea(3598, 3358, 5, 5, 0);

    @Test
    void fightAreaNotYetSetByFirstGameTickSkipsTheLoop() {
        assertFalse(PVirewatchScript.hasFightState(start, null, null));
        assertFalse(PVirewatchScript.hasFightState(start, null, start));
    }

    @Test
    void missingLocalPlayerSkipsTheLoop() {
        assertFalse(PVirewatchScript.hasFightState(null, fightArea, start));
    }

    @Test
    void fightStateIsReadyOncePlayerAreaAndStartAreSet() {
        assertTrue(PVirewatchScript.hasFightState(start, fightArea, start));
    }

    @Test
    void standingOnAnEqualStartTileIsNotAwayFromStart() {
        assertFalse(PVirewatchScript.isAwayFromStart(new WorldPoint(3600, 3360, 0), start));
    }

    @Test
    void anotherTileIsAwayFromStart() {
        assertTrue(PVirewatchScript.isAwayFromStart(new WorldPoint(3601, 3360, 0), start));
    }
}
