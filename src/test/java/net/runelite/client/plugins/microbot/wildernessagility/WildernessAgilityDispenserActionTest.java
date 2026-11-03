package net.runelite.client.plugins.microbot.wildernessagility;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WildernessAgilityDispenserActionTest {

    private static final WorldPoint DISPENSER_TILE = new WorldPoint(3004, 3936, 0);
    private static final WorldPoint PAST_ROCKS_TILE = new WorldPoint(2994, 3933, 0);

    @Test
    void dispenserActionIsTag() {
        assertEquals("Tag", WildernessAgilityScript.DISPENSER_ACTION);
    }

    @Test
    void dispenserStateTagsDispenser() throws Exception {
        Rs2TileObjectModel dispenser = dispenser();
        WildernessAgilityScript script = new WildernessAgilityScript();
        set(script, "cachedDispenserObj", dispenser);

        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(DISPENSER_TILE);
            inventory.when(Rs2Inventory::items).thenAnswer(i -> Stream.empty());

            script.handleDispenser();
        }

        verify(dispenser).click("Tag");
    }

    @Test
    void finishingRocksTagsDispenser() {
        Rs2TileObjectModel dispenser = dispenser();
        Rs2TileObjectQueryable query = mock(Rs2TileObjectQueryable.class);
        when(query.withId(anyInt())).thenReturn(query);
        when(query.nearest()).thenReturn(dispenser);
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        when(cache.query()).thenReturn(query);
        WildernessAgilityScript script = new WildernessAgilityScript();

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
            player.when(Rs2Player::getWorldLocation).thenReturn(PAST_ROCKS_TILE);
            inventory.when(Rs2Inventory::items).thenAnswer(i -> Stream.empty());

            script.handleRocks();
        }

        verify(dispenser).click("Tag");
    }

    private static Rs2TileObjectModel dispenser() {
        Rs2TileObjectModel dispenser = mock(Rs2TileObjectModel.class);
        when(dispenser.getWorldLocation()).thenReturn(DISPENSER_TILE);
        when(dispenser.click("Tag")).thenReturn(true);
        return dispenser;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = WildernessAgilityScript.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
