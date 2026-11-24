package net.runelite.client.plugins.microbot.wildernessagility;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class WildernessAgilityPhoenixMissingTest {

    private static final WorldPoint MAGE_BANK = new WorldPoint(2534, 4712, 0);
    private static final WorldPoint COURSE_PIPE = new WorldPoint(3004, 3936, 0);

    private final String originalStatus = Microbot.status;

    @AfterEach
    void restoreStatus() {
        Microbot.status = originalStatus;
    }

    @Test
    void clearingInventoryKeepsPhoenixNecklaceAndLootingBags() {
        assertTrue(WildernessAgilityScript.isKeptWhenClearingInventory(11090, -1));
        assertTrue(WildernessAgilityScript.isKeptWhenClearingInventory(11941, -1));
        assertTrue(WildernessAgilityScript.isKeptWhenClearingInventory(22586, -1));
        assertTrue(WildernessAgilityScript.isKeptWhenClearingInventory(946, -1));
        assertTrue(WildernessAgilityScript.isKeptWhenClearingInventory(24963, -1));
        assertTrue(WildernessAgilityScript.isKeptWhenClearingInventory(995, -1));
        assertTrue(WildernessAgilityScript.isKeptWhenClearingInventory(29460, -1));
        assertTrue(WildernessAgilityScript.isKeptWhenClearingInventory(12913, 12913));
    }

    @Test
    void clearingInventoryStillDropsOtherItems() {
        assertFalse(WildernessAgilityScript.isKeptWhenClearingInventory(24598, -1));
        assertFalse(WildernessAgilityScript.isKeptWhenClearingInventory(12913, -1));
    }

    @Test
    void missingNecklaceAtMageBankStopsInsteadOfWalkingBackToTheCourse() throws Exception {
        WildernessAgilityPlugin plugin = mock(WildernessAgilityPlugin.class);
        WildernessAgilityScript script = scriptWith(plugin);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Pvp> pvp = mockStatic(Rs2Pvp.class);
             MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(MAGE_BANK);

            assertFalse(script.triggerPhoenixEscape());

            microbot.verify(() -> Microbot.stopPlugin(plugin));
        }

        assertFalse((boolean) get(script, "emergencyEscapeTriggered"));
        assertTrue(Microbot.status.contains("Phoenix necklace"));
    }

    @Test
    void missingNecklaceOnTheCourseStartsTheEscape() throws Exception {
        WildernessAgilityPlugin plugin = mock(WildernessAgilityPlugin.class);
        WildernessAgilityScript script = scriptWith(plugin);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Pvp> pvp = mockStatic(Rs2Pvp.class);
             MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(COURSE_PIPE);
            pvp.when(() -> Rs2Pvp.getWildernessLevelFrom(COURSE_PIPE)).thenReturn(52);

            assertTrue(script.triggerPhoenixEscape());

            microbot.verify(() -> Microbot.stopPlugin(any(WildernessAgilityPlugin.class)), never());
        }

        assertTrue((boolean) get(script, "emergencyEscapeTriggered"));
        assertEquals("EMERGENCY_ESCAPE", get(script, "currentState").toString());
    }

    private static WildernessAgilityScript scriptWith(WildernessAgilityPlugin plugin) throws Exception {
        WildernessAgilityConfig config = mock(WildernessAgilityConfig.class);
        when(config.phoenixEscape()).thenReturn(true);
        WildernessAgilityScript script = new WildernessAgilityScript();
        set(script, "config", config);
        set(script, "plugin", plugin);
        return script;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = WildernessAgilityScript.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = WildernessAgilityScript.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
