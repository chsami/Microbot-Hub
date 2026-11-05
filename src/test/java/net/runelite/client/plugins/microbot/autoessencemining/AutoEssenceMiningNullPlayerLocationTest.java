package net.runelite.client.plugins.microbot.autoessencemining;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

class AutoEssenceMiningNullPlayerLocationTest {

    private static final WorldPoint ESSENCE_MINE_TILE = WorldPoint.fromRegion(11595, 32, 32, 0);
    private static final WorldPoint VARROCK_TILE = new WorldPoint(3253, 3410, 0);

    @Test
    void isInEssenceMineIsFalseWhenThePlayerLocationIsNull() throws Exception {
        AutoEssenceMiningScript script = new AutoEssenceMiningScript();
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            assertFalse((Boolean) method("isInEssenceMine").invoke(script));
        }
    }

    @Test
    void isInEssenceMineFollowsTheRegionOfThePlayerLocation() throws Exception {
        AutoEssenceMiningScript script = new AutoEssenceMiningScript();
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(ESSENCE_MINE_TILE);
            assertTrue((Boolean) method("isInEssenceMine").invoke(script));

            player.when(Rs2Player::getWorldLocation).thenReturn(VARROCK_TILE);
            assertFalse((Boolean) method("isInEssenceMine").invoke(script));
        }
    }

    @Test
    void walkingToAuburySkipsTheTickWhenThePlayerLocationIsNull() throws Exception {
        AutoEssenceMiningScript script = new AutoEssenceMiningScript();
        setField(script, "lastAuburyTeleportAttempt", System.currentTimeMillis());
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            Method method = method("handleWalkingToAubury");
            assertDoesNotThrow(() -> method.invoke(script));

            walker.verify(() -> Rs2Walker.walkMiniMap(any(WorldPoint.class)), never());
        }
    }

    @Test
    void miningEssenceSkipsTheTickWhenThePlayerLocationIsNull() throws Exception {
        AutoEssenceMiningScript script = new AutoEssenceMiningScript();
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            Method method = method("handleMiningEssence");
            assertDoesNotThrow(() -> method.invoke(script));

            inventory.verify(Rs2Inventory::isFull, never());
        }
    }

    @Test
    void usingPortalSkipsTheTickWhenThePlayerLocationIsNull() throws Exception {
        AutoEssenceMiningScript script = new AutoEssenceMiningScript();
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            Method method = method("handleUsingPortal");
            assertDoesNotThrow(() -> method.invoke(script));
        }
    }

    @Test
    void bankingSkipsTheTickWhenThePlayerLocationIsNull() throws Exception {
        AutoEssenceMiningScript script = new AutoEssenceMiningScript();
        AutoEssenceMiningConfig config = mock(AutoEssenceMiningConfig.class);
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            Method method = AutoEssenceMiningScript.class.getDeclaredMethod("handleBanking", AutoEssenceMiningConfig.class);
            method.setAccessible(true);
            assertDoesNotThrow(() -> method.invoke(script, config));

            bank.verify(Rs2Bank::isOpen, never());
            bank.verify(Rs2Bank::openBank, never());
            walker.verify(() -> Rs2Walker.walkMiniMap(any(WorldPoint.class)), never());
        }
    }

    private static Method method(String name) throws Exception {
        Method method = AutoEssenceMiningScript.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
