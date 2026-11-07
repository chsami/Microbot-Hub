package net.runelite.client.plugins.microbot.looter;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.looter.enums.LooterState;
import net.runelite.client.plugins.microbot.looter.enums.NatureRuneChestLocations;
import net.runelite.client.plugins.microbot.looter.scripts.NatureRuneChestScript;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class NatureRuneChestNullPlayerLocationTest {

    private static final WorldPoint CHEST_TILE = NatureRuneChestLocations.EAST_ARDOUGNE.getWorldPoint();

    @Test
    void isNearChestIsFalseWhenThePlayerLocationIsNull() throws Exception {
        NatureRuneChestScript script = new NatureRuneChestScript();
        AutoLooterConfig config = config();
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            assertFalse((Boolean) isNearMethod().invoke(script, config, 6));
        }
    }

    @Test
    void isNearChestComparesTheDistanceToTheChest() throws Exception {
        NatureRuneChestScript script = new NatureRuneChestScript();
        AutoLooterConfig config = config();
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(CHEST_TILE.dx(3));
            assertTrue((Boolean) isNearMethod().invoke(script, config, 6));

            player.when(Rs2Player::getWorldLocation).thenReturn(CHEST_TILE.dx(20));
            assertFalse((Boolean) isNearMethod().invoke(script, config, 6));
        }
    }

    @Test
    void getStateFallsBackToWalkingWhenThePlayerLocationIsNull() throws Exception {
        NatureRuneChestScript script = new NatureRuneChestScript();
        AutoLooterConfig config = config();
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            Method getState = NatureRuneChestScript.class.getDeclaredMethod("getState", AutoLooterConfig.class);
            getState.setAccessible(true);
            getState.invoke(script, config);

            assertEquals(LooterState.WALKING, field(script, "state"));
            assertEquals(false, field(script, "init"));
        }
    }

    private static AutoLooterConfig config() {
        AutoLooterConfig config = mock(AutoLooterConfig.class);
        when(config.natureRuneChestLocation()).thenReturn(NatureRuneChestLocations.EAST_ARDOUGNE);
        return config;
    }

    private static Method isNearMethod() throws Exception {
        Method method = NatureRuneChestScript.class.getDeclaredMethod("isNearNatureRuneChest", AutoLooterConfig.class, int.class);
        method.setAccessible(true);
        return method;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
