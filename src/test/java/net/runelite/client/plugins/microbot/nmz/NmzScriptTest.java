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
}
