package net.runelite.client.plugins.microbot.looter;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.looter.enums.FlaxLocations;
import net.runelite.client.plugins.microbot.looter.enums.LooterState;
import net.runelite.client.plugins.microbot.looter.scripts.DefaultScript;
import net.runelite.client.plugins.microbot.looter.scripts.FlaxScript;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.AfterEach;
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

class LooterScriptsNullPlayerLocationTest {

    private static final WorldPoint FLAX_TILE = FlaxLocations.SEERS_VILLAGE.getWorldPoint();
    private static final WorldPoint BASE_TILE = new WorldPoint(3200, 3200, 0);

    @AfterEach
    void clearInitialPlayerLocation() throws Exception {
        setInitialPlayerLocation(null);
    }

    @Test
    void isNearFlaxIsFalseWhenThePlayerLocationIsNull() throws Exception {
        FlaxScript script = new FlaxScript();
        AutoLooterConfig config = flaxConfig();
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            assertFalse((Boolean) isNearFlaxMethod().invoke(script, config, 6));
        }
    }

    @Test
    void isNearFlaxComparesTheDistanceToTheFlaxField() throws Exception {
        FlaxScript script = new FlaxScript();
        AutoLooterConfig config = flaxConfig();
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(FLAX_TILE.dx(3));
            assertTrue((Boolean) isNearFlaxMethod().invoke(script, config, 6));

            player.when(Rs2Player::getWorldLocation).thenReturn(FLAX_TILE.dx(20));
            assertFalse((Boolean) isNearFlaxMethod().invoke(script, config, 6));
        }
    }

    @Test
    void flaxGetStateFallsBackToWalkingWhenThePlayerLocationIsNull() throws Exception {
        FlaxScript script = new FlaxScript();
        AutoLooterConfig config = flaxConfig();
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            Method getState = FlaxScript.class.getDeclaredMethod("getState", AutoLooterConfig.class);
            getState.setAccessible(true);
            getState.invoke(script, config);

            assertEquals(LooterState.WALKING, field(script, "state"));
            assertEquals(false, field(script, "init"));
        }
    }

    @Test
    void defaultIsAwayFromBaseWhenThePlayerLocationIsNull() throws Exception {
        DefaultScript script = new DefaultScript();
        AutoLooterConfig config = defaultConfig();
        setInitialPlayerLocation(BASE_TILE);
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::isMoving).thenReturn(false);
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            assertTrue((Boolean) isAwayFromBaseMethod().invoke(script, config));
        }
    }

    @Test
    void defaultIsAwayFromBaseComparesTheDistanceToTheBase() throws Exception {
        DefaultScript script = new DefaultScript();
        AutoLooterConfig config = defaultConfig();
        setInitialPlayerLocation(BASE_TILE);
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::isMoving).thenReturn(false);
            player.when(Rs2Player::getWorldLocation).thenReturn(BASE_TILE.dx(3));
            assertFalse((Boolean) isAwayFromBaseMethod().invoke(script, config));

            player.when(Rs2Player::getWorldLocation).thenReturn(BASE_TILE.dx(20));
            assertTrue((Boolean) isAwayFromBaseMethod().invoke(script, config));
        }
    }

    private static AutoLooterConfig flaxConfig() {
        AutoLooterConfig config = mock(AutoLooterConfig.class);
        when(config.flaxLocation()).thenReturn(FlaxLocations.SEERS_VILLAGE);
        return config;
    }

    private static AutoLooterConfig defaultConfig() {
        AutoLooterConfig config = mock(AutoLooterConfig.class);
        when(config.distanceToStray()).thenReturn(10);
        return config;
    }

    private static Method isNearFlaxMethod() throws Exception {
        Method method = FlaxScript.class.getDeclaredMethod("isNearFlaxLocation", AutoLooterConfig.class, int.class);
        method.setAccessible(true);
        return method;
    }

    private static Method isAwayFromBaseMethod() throws Exception {
        Method method = DefaultScript.class.getDeclaredMethod("isAwayFromBase", AutoLooterConfig.class);
        method.setAccessible(true);
        return method;
    }

    private static void setInitialPlayerLocation(WorldPoint location) throws Exception {
        Field field = Script.class.getDeclaredField("initialPlayerLocation");
        field.setAccessible(true);
        field.set(null, location);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
