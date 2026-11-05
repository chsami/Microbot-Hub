package net.runelite.client.plugins.microbot.birdhunter;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BirdHunterNullStartTileTest {

    private BirdHunterConfig config;

    @BeforeEach
    void setUp() throws Exception {
        setStartTile(null);
        config = mock(BirdHunterConfig.class);
        when(config.huntingRadiusValue()).thenReturn(2);
    }

    @AfterEach
    void tearDown() throws Exception {
        setStartTile(null);
    }

    @Test
    void updateHuntingAreaIsNoOpWhileStartTileIsNull() throws Exception {
        BirdHunterScript script = new BirdHunterScript();

        assertDoesNotThrow(() -> script.updateHuntingArea(config));

        assertNull(area(script));
        assertEquals(2, BirdHunterScript.getHuntingRadius());
    }

    @Test
    void ensureHuntingAreaWaitsForPlayerLocationThenBuildsArea() throws Exception {
        BirdHunterScript script = new BirdHunterScript();
        WorldPoint start = new WorldPoint(2600, 2900, 0);
        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(null);

            assertFalse(ensureHuntingArea(script));
            assertNull(BirdHunterScript.getInitialStartTile());
            assertNull(area(script));

            player.when(Rs2Player::getWorldLocation).thenReturn(start);

            assertTrue(ensureHuntingArea(script));
            assertEquals(start, BirdHunterScript.getInitialStartTile());
            WorldArea area = area(script);
            assertTrue(area.contains(start));
            assertTrue(area.contains(new WorldPoint(2602, 2902, 0)));
            assertFalse(area.contains(new WorldPoint(2603, 2900, 0)));
        }
    }

    private boolean ensureHuntingArea(BirdHunterScript script) throws Exception {
        Method method = BirdHunterScript.class.getDeclaredMethod("ensureHuntingArea", BirdHunterConfig.class);
        method.setAccessible(true);
        return (boolean) method.invoke(script, config);
    }

    private static WorldArea area(BirdHunterScript script) throws Exception {
        Field field = BirdHunterScript.class.getDeclaredField("dynamicHuntingArea");
        field.setAccessible(true);
        return (WorldArea) field.get(script);
    }

    private static void setStartTile(WorldPoint tile) throws Exception {
        Field field = BirdHunterScript.class.getDeclaredField("initialStartTile");
        field.setAccessible(true);
        field.set(null, tile);
    }
}
