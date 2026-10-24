package net.runelite.client.plugins.microbot.motherloadmine;

import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class MotherloadMineScriptTest {
    @Test
    void missingAntibanActivityFallsBackToGeneralMining() {
        assertEquals(Activity.GENERAL_MINING.getActivityIntensity(), MotherloadMineScript.miningIntensity(null));
    }

    @Test
    void usesConfiguredAntibanActivityIntensity() {
        assertEquals(Activity.GENERAL_COMBAT.getActivityIntensity(), MotherloadMineScript.miningIntensity(Activity.GENERAL_COMBAT));
    }

    @Test
    void missingLocalPlayerHasNoWorldLocation() {
        assertNull(MotherloadMineScript.worldLocationOf(null));
    }

    @Test
    void localPlayerWorldLocationIsReturned() {
        WorldPoint location = new WorldPoint(3750, 5670, 0);
        Player player = mock(Player.class);
        when(player.getWorldLocation()).thenReturn(location);
        assertEquals(location, MotherloadMineScript.worldLocationOf(player));
    }
}
