package net.runelite.client.plugins.microbot.gotr;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class GotrRegionCheckTest
{
    private final GotrScript script = new GotrScript();

    @Test
    void unknownLocationIsInNoArea()
    {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class))
        {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            assertFalse(script.isInHugeMine());
            assertFalse(GotrScript.isInLargeMine());
            assertFalse(GotrScript.isOutsideBarrier());
            assertFalse(GotrScript.isInMainRegion());
            assertFalse(GotrScript.isPlayerLocationKnown());
        }
    }

    @Test
    void knownLocationLetsLoopDecide()
    {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class))
        {
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3222, 3218, 0));

            assertTrue(GotrScript.isPlayerLocationKnown());
        }
    }

    @Test
    void hugeMineIsDetected()
    {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class))
        {
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3590, 9500, 0));

            assertTrue(script.isInHugeMine());
            assertFalse(GotrScript.isInLargeMine());
            assertTrue(GotrScript.isInMainRegion());
        }
    }

    @Test
    void largeMineIsDetected()
    {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class))
        {
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3640, 9500, 0));

            assertTrue(GotrScript.isInLargeMine());
            assertFalse(script.isInHugeMine());
        }
    }

    @Test
    void outsideBarrierIsDetected()
    {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class))
        {
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3615, 9480, 0));

            assertTrue(GotrScript.isOutsideBarrier());
            assertTrue(GotrScript.isInMainRegion());
        }
    }

    @Test
    void otherRegionIsInNoArea()
    {
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class))
        {
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3222, 3218, 0));

            assertFalse(script.isInHugeMine());
            assertFalse(GotrScript.isInLargeMine());
            assertFalse(GotrScript.isOutsideBarrier());
            assertFalse(GotrScript.isInMainRegion());
        }
    }
}
