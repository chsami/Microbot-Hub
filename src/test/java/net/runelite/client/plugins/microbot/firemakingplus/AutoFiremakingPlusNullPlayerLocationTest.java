package net.runelite.client.plugins.microbot.firemakingplus;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AutoFiremakingPlusNullPlayerLocationTest {

    @Test
    void lineBurnSkipsTheTickWhenThePlayerLocationIsNull() throws Exception {
        AutoFiremakingPlusScript script = new AutoFiremakingPlusScript();
        setField(script, "activeLog", Logs.LOGS);
        setField(script, "state", State.BURNING);
        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class,
                     invocation -> invocation.getMethod().getReturnType() == boolean.class
                             ? Boolean.TRUE
                             : RETURNS_DEFAULTS.answer(invocation));
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<TileScanner> scanner = mockStatic(TileScanner.class);
             MockedStatic<Rs2Tile> tile = mockStatic(Rs2Tile.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            assertDoesNotThrow(() -> invoke(script, "lineBurn"));

            scanner.verify(() -> TileScanner.hasFire(any()), never());
            tile.verify(() -> Rs2Tile.isWalkable(any(WorldPoint.class)), never());
            inventory.verify(() -> Rs2Inventory.combine(anyString(), anyString()), never());
            assertEquals(State.BURNING, getField(script, "state"));
            assertEquals(Integer.MIN_VALUE, getField(script, "blockedLineY"));
            assertEquals("Waiting for player to load", Microbot.status);
        }
    }

    @Test
    void lineWalkToSkipsTheTickWhenThePlayerLocationIsNull() throws Exception {
        AutoFiremakingPlusScript script = new AutoFiremakingPlusScript();
        setField(script, "state", State.WALKING_TO_LINE);
        setField(script, "currentLine", new FireLine(
                new WorldPoint(3200, 3430, 0), new WorldPoint(3210, 3430, 0), 11));
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            assertDoesNotThrow(() -> invoke(script, "lineWalkTo"));

            walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt()), never());
            assertEquals(State.WALKING_TO_LINE, getField(script, "state"));
            assertEquals("Waiting for player to load", Microbot.status);
        }
    }

    @Test
    void lineWalkBackSkipsTheTickWhenThePlayerLocationIsNull() throws Exception {
        AutoFiremakingPlusScript script = new AutoFiremakingPlusScript();
        setField(script, "state", State.WALKING_BACK);
        setField(script, "startPosition", new WorldPoint(3205, 3430, 0));
        AutoFiremakingPlusConfig config = mock(AutoFiremakingPlusConfig.class);
        when(config.scanRadius()).thenReturn(10);
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            Method method = AutoFiremakingPlusScript.class
                    .getDeclaredMethod("lineWalkBack", AutoFiremakingPlusConfig.class);
            method.setAccessible(true);
            assertDoesNotThrow(() -> method.invoke(script, config));

            walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt()), never());
            assertEquals(State.WALKING_BACK, getField(script, "state"));
            assertEquals("Waiting for player to load", Microbot.status);
        }
    }

    @Test
    void runCampfireSkipsTheTickWhenThePlayerLocationIsNull() throws Exception {
        AutoFiremakingPlusScript script = new AutoFiremakingPlusScript();
        setField(script, "activeLog", Logs.LOGS);
        setField(script, "state", State.BURNING);
        AutoFiremakingPlusConfig config = mock(AutoFiremakingPlusConfig.class);
        when(config.method()).thenReturn(FiremakingMethod.CAMPFIRE);
        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class,
                     invocation -> invocation.getMethod().getReturnType() == boolean.class
                             ? Boolean.TRUE
                             : RETURNS_DEFAULTS.answer(invocation));
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<TileScanner> scanner = mockStatic(TileScanner.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            Method method = AutoFiremakingPlusScript.class
                    .getDeclaredMethod("runCampfire", AutoFiremakingPlusConfig.class);
            method.setAccessible(true);
            assertDoesNotThrow(() -> method.invoke(script, config));

            inventory.verify(() -> Rs2Inventory.combine(anyString(), anyString()), never());
            inventory.verify(() -> Rs2Inventory.use(anyInt()), never());
            scanner.verify(() -> TileScanner.fireTilesNear(any(), anyInt()), never());
            walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt()), never());
            assertEquals(State.BURNING, getField(script, "state"));
            assertEquals(-1, getField(script, "lastLogCount"));
            assertEquals("Waiting for player to load", Microbot.status);
        }
    }

    private static void invoke(AutoFiremakingPlusScript script, String name) throws Exception {
        Method method = AutoFiremakingPlusScript.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(script);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
