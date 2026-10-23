package net.runelite.client.plugins.microbot.fishingtrawler;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class FishingTrawlerGroundFloorTest
{
    @Test
    void unknownLocationIsNotGroundFloor()
    {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class))
        {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            assertFalse(FishingTrawlerScript.isOnGroundFloor());
        }
    }

    @Test
    void planeZeroIsGroundFloor()
    {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class))
        {
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(1884, 4826, 0));

            assertTrue(FishingTrawlerScript.isOnGroundFloor());
        }
    }

    @Test
    void upperPlaneIsNotGroundFloor()
    {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class))
        {
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(1885, 4827, 1));

            assertFalse(FishingTrawlerScript.isOnGroundFloor());
        }
    }
}
