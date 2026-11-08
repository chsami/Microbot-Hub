package net.runelite.client.plugins.microbot.banksshopper;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BanksShopperWalkWaitTest {

    private static final WorldPoint TARGET = new WorldPoint(3212, 3246, 0);

    @Test
    void nullPlayerLocationDuringHopIsNotArrived() throws Exception {
        assertEquals(Boolean.FALSE, arrived(null, null));
    }

    @Test
    void playerNearTargetIsArrived() throws Exception {
        assertEquals(Boolean.TRUE, arrived(null, new WorldPoint(3214, 3246, 0)));
    }

    @Test
    void playerFarFromTargetIsNotArrived() throws Exception {
        assertEquals(Boolean.FALSE, arrived(null, new WorldPoint(3230, 3246, 0)));
    }

    @Test
    void shopNpcInSceneEndsWaitEvenWithoutPlayerLocation() throws Exception {
        assertEquals(Boolean.TRUE, arrived(mock(Rs2NpcModel.class), null));
    }

    private static Object arrived(Rs2NpcModel npc, WorldPoint playerLocation) throws Exception {
        BanksShopperPlugin plugin = mock(BanksShopperPlugin.class);
        when(plugin.getNpcName()).thenReturn("Shop keeper");
        BanksShopperScript script = new BanksShopperScript(plugin);
        try (MockedStatic<Rs2Shop> shop = mockStatic(Rs2Shop.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            shop.when(() -> Rs2Shop.getNearestShopNpc(any(), anyBoolean())).thenReturn(npc);
            player.when(Rs2Player::getWorldLocation).thenReturn(playerLocation);

            Method method = BanksShopperScript.class.getDeclaredMethod("isShopNpcInSceneOrArrived", WorldPoint.class);
            method.setAccessible(true);
            return method.invoke(script, TARGET);
        }
    }
}
