package net.runelite.client.plugins.microbot.AmmoniteCrabs;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

class AmmoniteCrabScriptTest {

    private final WorldPoint fightLocation = new WorldPoint(3733, 3846, 0);

    @Test
    void nullPlayerLocationIsNotWithinRange() {
        assertFalse(AmmoniteCrabScript.isWithin(null, fightLocation, 3));
    }

    @Test
    void playerLocationWithinRangeIsDetected() {
        assertTrue(AmmoniteCrabScript.isWithin(new WorldPoint(3735, 3846, 0), fightLocation, 3));
        assertFalse(AmmoniteCrabScript.isWithin(new WorldPoint(3740, 3846, 0), fightLocation, 3));
    }

    @Test
    void nullLocationDetectsNoOtherPlayerWithoutQueryingPlayers() {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            assertFalse(AmmoniteCrabScript.otherPlayerDetected(null));
            player.verify(() -> Rs2Player.getPlayers(any()), never());
        }
    }
}
