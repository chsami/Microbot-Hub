package net.runelite.client.plugins.microbot.wildernessagility;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

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
    private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);
    private static final int PHOENIX_NECKLACE = 11090;

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
        assertTrue(Microbot.status.contains("not wearing a Phoenix necklace"));
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

    @Test
    void bankTripWithdrawsTheNecklaceDepositAllBankedFromTheInventory() throws Exception {
        WildernessAgilityPlugin plugin = mock(WildernessAgilityPlugin.class);
        WildernessAgilityScript script = scriptWith(plugin);
        setState(script, "BANKING");
        Rs2ItemModel loot = mock(Rs2ItemModel.class);
        when(loot.getId()).thenReturn(24598);
        AtomicBoolean necklaceInInventory = new AtomicBoolean(false);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            bank.when(() -> Rs2Bank.withdrawOne(PHOENIX_NECKLACE)).thenAnswer(invocation -> {
                necklaceInInventory.set(true);
                return true;
            });
            inventory.when(Rs2Inventory::items).thenAnswer(invocation -> Stream.of(loot));
            inventory.when(() -> Rs2Inventory.hasItem(PHOENIX_NECKLACE)).thenAnswer(invocation -> necklaceInInventory.get());

            script.handleBanking();

            bank.verify(Rs2Bank::depositAll);
            bank.verify(() -> Rs2Bank.withdrawOne(PHOENIX_NECKLACE));
            inventory.verify(() -> Rs2Inventory.wield(PHOENIX_NECKLACE));
            microbot.verify(() -> Microbot.stopPlugin(any(WildernessAgilityPlugin.class)), never());
        }

        assertEquals("POST_BANK_CONFIG", get(script, "currentState").toString());
    }

    @Test
    void bankTripDoesNotReequipANecklaceAlreadyWorn() throws Exception {
        WildernessAgilityScript script = scriptWith(mock(WildernessAgilityPlugin.class));
        setState(script, "BANKING");

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            equipment.when(() -> Rs2Equipment.isWearing("Phoenix necklace")).thenReturn(true);
            inventory.when(Rs2Inventory::items).thenAnswer(invocation -> Stream.empty());

            script.handleBanking();

            bank.verify(() -> Rs2Bank.withdrawOne(PHOENIX_NECKLACE), never());
            inventory.verify(() -> Rs2Inventory.wield(PHOENIX_NECKLACE), never());
        }

        assertEquals("POST_BANK_CONFIG", get(script, "currentState").toString());
    }

    @Test
    void bankWithoutNecklaceStopsWithABankingReason() throws Exception {
        WildernessAgilityPlugin plugin = mock(WildernessAgilityPlugin.class);
        WildernessAgilityScript script = scriptWith(plugin);
        setState(script, "BANKING");

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            inventory.when(Rs2Inventory::items).thenAnswer(invocation -> Stream.empty());

            script.handleBanking();
            script.handleBanking();
            microbot.verify(() -> Microbot.stopPlugin(any(WildernessAgilityPlugin.class)), never());
            script.handleBanking();

            microbot.verify(() -> Microbot.stopPlugin(plugin));
        }

        assertTrue(Microbot.status.contains("Phoenix necklace"));
        assertTrue(Microbot.status.contains("'Phoenix Escape' under Escape Settings"));
    }

    @Test
    void missingNecklaceCheckWaitsForTheBankOutsideTheWilderness() throws Exception {
        WildernessAgilityScript script = scriptWith(mock(WildernessAgilityPlugin.class));

        try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Pvp> pvp = mockStatic(Rs2Pvp.class)) {
            player.when(Rs2Player::getWorldLocation).thenReturn(LUMBRIDGE);

            setState(script, "BANKING");
            assertTrue(script.isHeadingToBankOutsideWilderness());
            setState(script, "WALK_TO_LEVER");
            assertTrue(script.isHeadingToBankOutsideWilderness());
            setState(script, "START");
            assertFalse(script.isHeadingToBankOutsideWilderness());

            player.when(Rs2Player::getWorldLocation).thenReturn(COURSE_PIPE);
            pvp.when(() -> Rs2Pvp.getWildernessLevelFrom(COURSE_PIPE)).thenReturn(52);
            setState(script, "WALK_TO_LEVER");
            assertFalse(script.isHeadingToBankOutsideWilderness());
        }
    }

    private static WildernessAgilityScript scriptWith(WildernessAgilityPlugin plugin) throws Exception {
        WildernessAgilityConfig config = mock(WildernessAgilityConfig.class);
        when(config.phoenixEscape()).thenReturn(true);
        when(config.withdrawVenomProtection()).thenReturn(WildernessAgilityConfig.VenomProtectionOption.None);
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setState(WildernessAgilityScript script, String state) throws Exception {
        Field field = WildernessAgilityScript.class.getDeclaredField("currentState");
        field.setAccessible(true);
        field.set(script, Enum.valueOf((Class) field.getType(), state));
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = WildernessAgilityScript.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
