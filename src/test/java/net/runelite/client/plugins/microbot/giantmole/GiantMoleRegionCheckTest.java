package net.runelite.client.plugins.microbot.giantmole;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class GiantMoleRegionCheckTest {

    private static final WorldPoint MOLE_TUNNEL_TILE = new WorldPoint(1760, 5190, 0);

    @Test
    void missingPlayerLocationIsNotInTheMoleTunnel() {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            assertFalse(GiantMoleScript.isInMoleTunnel());
        }
    }

    @Test
    void missingPlayerLocationIsNotInFalador() {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            assertFalse(new GiantMoleScript().isInFalador());
        }
    }

    @Test
    void missingPlayerLocationGivesNoMoleLocation() {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            assertNull(GiantMoleScript.getMoleLocation());
        }
    }

    @Test
    void knownLocationsStillMatchTheirRegions() {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            GiantMoleScript script = new GiantMoleScript();

            player.when(Rs2Player::getWorldLocation).thenReturn(MOLE_TUNNEL_TILE);
            assertEquals(6993, MOLE_TUNNEL_TILE.getRegionID());
            assertTrue(GiantMoleScript.isInMoleTunnel());
            assertFalse(script.isInFalador());

            player.when(Rs2Player::getWorldLocation).thenReturn(GiantMoleScript.FALADOR_PARK);
            assertTrue(script.isInFalador());
            assertFalse(GiantMoleScript.isInMoleTunnel());
        }
    }
}
