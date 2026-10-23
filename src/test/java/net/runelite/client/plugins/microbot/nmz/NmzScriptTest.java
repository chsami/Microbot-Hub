package net.runelite.client.plugins.microbot.nmz;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NmzScriptTest {

    @Test
    void unknownPlayerLocationIsNotOutside() {
        assertFalse(NmzScript.isNearLobby(null));
    }

    @Test
    void locationNearLobbyIsOutside() {
        assertTrue(NmzScript.isNearLobby(new WorldPoint(2609, 3114, 0)));
    }

    @Test
    void locationInsideDreamIsNotOutside() {
        assertFalse(NmzScript.isNearLobby(new WorldPoint(2273, 4694, 0)));
    }

    @Test
    void unknownPlayerLocationIsNotAwayFromCenter() {
        assertFalse(NmzScript.isAwayFromCenter(new WorldPoint(2273, 4694, 0), null));
    }

    @Test
    void playerFarFromCenterIsAwayFromCenter() {
        assertTrue(NmzScript.isAwayFromCenter(new WorldPoint(2273, 4694, 0), new WorldPoint(2290, 4694, 0)));
    }

    @Test
    void playerNearCenterIsNotAwayFromCenter() {
        assertFalse(NmzScript.isAwayFromCenter(new WorldPoint(2273, 4694, 0), new WorldPoint(2275, 4694, 0)));
    }
}
