package net.runelite.client.plugins.microbot.dropyramidplunder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.function.Predicate;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** A bank trip never ends with food still at the restock threshold: it either restocks or stops with the cause. */
class PyramidPlunderRestockTest {
    private static Object view() throws Exception {
        Class<?> type = Class.forName(PyramidPlunderScript.class.getName() + "$View");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void invoke(PyramidPlunderScript script, String name, Object view) throws Exception {
        Method method = PyramidPlunderScript.class.getDeclaredMethod(name, view.getClass());
        method.setAccessible(true);
        method.invoke(script, view);
    }

    private static PyramidPlunderConfig config(int foodAmount, int minFood) {
        PyramidPlunderConfig config = mock(PyramidPlunderConfig.class);
        when(config.foodAmount()).thenReturn(foodAmount);
        when(config.minFood()).thenReturn(minFood);
        when(config.food()).thenReturn(Rs2Food.KARAMBWAN);
        return config;
    }

    private static PyramidPlunderScript script(PyramidPlunderConfig config) throws Exception {
        PyramidPlunderScript script = new PyramidPlunderScript();
        set(script, "config", config);
        set(script, "restocking", true);
        return script;
    }

    private static void carryFood(MockedStatic<Rs2Inventory> inventory, int food) {
        inventory.when(Rs2Inventory::getInventoryFood).thenReturn(Collections.nCopies(food, mock(Rs2ItemModel.class)));
    }

    @Test void thresholdAtFoodAmountStopsOutsideInsteadOfIdling() throws Exception {
        PyramidPlunderScript script = script(config(14, 14));
        Object view = view();
        set(view, "prayer", 50);
        set(view, "prayerLevel", 50);
        try (var inventory = mockStatic(Rs2Inventory.class); var player = mockStatic(Rs2Player.class);
             var microbot = mockStatic(Microbot.class)) {
            carryFood(inventory, 14);
            player.when(() -> Rs2Player.getQuestState(Quest.CONTACT)).thenReturn(QuestState.FINISHED);
            invoke(script, "restockOnSurface", view);
        }
        assertEquals("Restock at or below (14) must be lower than Food amount (14)", get(script, "error"));
    }

    @Test void emptyBankAtTheThresholdStopsWithTheFoodName() throws Exception {
        PyramidPlunderScript script = script(config(14, 3));
        try (var inventory = mockStatic(Rs2Inventory.class); var bank = mockStatic(Rs2Bank.class);
             var microbot = mockStatic(Microbot.class)) {
            carryFood(inventory, 3);
            inventory.when(Rs2Inventory::emptySlotCount).thenReturn(10);
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            bank.when(() -> Rs2Bank.count(anyString())).thenReturn(0);
            invoke(script, "bankRoom", view());
        }
        assertEquals("Out of " + Rs2Food.KARAMBWAN.getName()
                + " in the bank. Withdraw food or pick another Food, then restart", get(script, "error"));
        assertTrue((Boolean) get(script, "restocking"), "the failed trip is not reported as done");
    }

    @Test void fullInventoryAtTheThresholdStops() throws Exception {
        PyramidPlunderScript script = script(config(14, 3));
        try (var inventory = mockStatic(Rs2Inventory.class); var bank = mockStatic(Rs2Bank.class);
             var microbot = mockStatic(Microbot.class)) {
            carryFood(inventory, 2);
            inventory.when(Rs2Inventory::emptySlotCount).thenReturn(0);
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            bank.when(() -> Rs2Bank.count(anyString())).thenReturn(500);
            invoke(script, "bankRoom", view());
        }
        assertEquals("No free inventory slots for food", get(script, "error"));
    }

    @Test void emptyBankAboveTheThresholdStillFinishesTheTrip() throws Exception {
        PyramidPlunderScript script = script(config(14, 3));
        try (var inventory = mockStatic(Rs2Inventory.class); var bank = mockStatic(Rs2Bank.class);
             var microbot = mockStatic(Microbot.class)) {
            carryFood(inventory, 6);
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            bank.when(() -> Rs2Bank.count(anyString())).thenReturn(0);
            invoke(script, "bankRoom", view());
        }
        assertNull(get(script, "error"));
        assertFalse((Boolean) get(script, "restocking"));
    }

    @Test void setupWithTooLittleFoodStops() throws Exception {
        PyramidPlunderConfig config = config(14, 3);
        InventorySetup setup = mock(InventorySetup.class);
        when(config.inventorySetup()).thenReturn(setup);
        PyramidPlunderScript script = script(config);
        try (var inventory = mockStatic(Rs2Inventory.class); var bank = mockStatic(Rs2Bank.class);
             var microbot = mockStatic(Microbot.class);
             var loader = mockConstruction(Rs2InventorySetup.class, (mock, context) -> {
                 when(mock.loadEquipment()).thenReturn(true);
                 when(mock.loadInventory()).thenReturn(true);
             })) {
            carryFood(inventory, 2);
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            invoke(script, "bankRoom", view());
        }
        assertEquals("Inventory Setup has too little food for Restock at or below (3)", get(script, "error"));
    }

    @Test void setupThatKeepsFailingToLoadStopsAfterAFewTries() throws Exception {
        PyramidPlunderConfig config = config(14, 3);
        InventorySetup setup = mock(InventorySetup.class);
        when(setup.getName()).thenReturn("Plunder");
        when(config.inventorySetup()).thenReturn(setup);
        PyramidPlunderScript script = script(config);
        try (var inventory = mockStatic(Rs2Inventory.class); var bank = mockStatic(Rs2Bank.class);
             var microbot = mockStatic(Microbot.class);
             var loader = mockConstruction(Rs2InventorySetup.class,
                     (mock, context) -> when(mock.loadEquipment()).thenReturn(false))) {
            inventory.when(() -> Rs2Inventory.count(any(Predicate.class))).thenReturn(0);
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            for (int i = 1; i < PyramidPlunderScript.SETUP_LOAD_ATTEMPTS; i++) {
                invoke(script, "bankRoom", view());
                assertNull(get(script, "error"), "a single failed load is retried");
            }
            invoke(script, "bankRoom", view());
        }
        assertEquals("Could not load Inventory Setup Plunder: items missing in the bank", get(script, "error"));
    }
}
