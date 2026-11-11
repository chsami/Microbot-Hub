package net.runelite.client.plugins.microbot.dropyramidplunder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PyramidPlunderScriptActionsTest {
    private static Object nested(String name) throws Exception {
        Class<?> type = Class.forName(PyramidPlunderScript.class.getName() + "$" + name);
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

    private static Object invoke(PyramidPlunderScript script, String name, Object view) throws Exception {
        Method method = PyramidPlunderScript.class.getDeclaredMethod(name, view.getClass());
        method.setAccessible(true);
        return method.invoke(script, view);
    }

    @Test void zeroPrayerAndUnloadedSetupDoNotSendSuppliedPlayerToBank() throws Exception {
        PyramidPlunderScript script = new PyramidPlunderScript();
        PyramidPlunderConfig config = mock(PyramidPlunderConfig.class);
        when(config.minFood()).thenReturn(3);
        when(config.protectFromMelee()).thenReturn(true);
        when(config.restorePrayerBelow()).thenReturn(20);
        when(config.inventorySetup()).thenReturn(mock(InventorySetup.class));
        set(script, "config", config);
        Object view = nested("View");
        set(view, "prayerLevel", 99);
        set(view, "prayer", 0);
        try (var inventory = mockStatic(Rs2Inventory.class); var player = mockStatic(Rs2Player.class)) {
            inventory.when(Rs2Inventory::getInventoryFood)
                    .thenReturn(Collections.nCopies(4, mock(Rs2ItemModel.class)));
            assertEquals(false, invoke(script, "needsRestock", view));
            player.verifyNoInteractions();
            inventory.when(Rs2Inventory::getInventoryFood)
                    .thenReturn(Collections.nCopies(3, mock(Rs2ItemModel.class)));
            player.when(() -> Rs2Player.getQuestState(Quest.CONTACT)).thenReturn(QuestState.FINISHED);
            assertEquals(true, invoke(script, "needsRestock", view));
        }
    }

    @Test void prayerBookAndThreeFoodSkipStartupButTwoFoodStillBanks() throws Exception {
        PyramidPlunderScript script = new PyramidPlunderScript();
        PyramidPlunderConfig config = mock(PyramidPlunderConfig.class);
        when(config.minFood()).thenReturn(3);
        set(script, "config", config);
        Object view = nested("View");
        try (var inventory = mockStatic(Rs2Inventory.class); var player = mockStatic(Rs2Player.class)) {
            inventory.when(() -> Rs2Inventory.hasItem("Prayer book")).thenReturn(true);
            inventory.when(Rs2Inventory::getInventoryFood).thenReturn(Collections.nCopies(3, mock(Rs2ItemModel.class)));
            assertEquals(false, invoke(script, "needsRestock", view));
            player.verifyNoInteractions();

            player.when(() -> Rs2Player.getQuestState(Quest.CONTACT)).thenReturn(QuestState.FINISHED);
            set(script, "runs", 1);
            assertEquals(true, invoke(script, "needsRestock", view));

            set(script, "runs", 0);
            inventory.when(Rs2Inventory::getInventoryFood).thenReturn(Collections.nCopies(2, mock(Rs2ItemModel.class)));
            assertEquals(true, invoke(script, "needsRestock", view));
            inventory.when(Rs2Inventory::getInventoryFood).thenReturn(List.of());
            assertEquals(true, invoke(script, "needsRestock", view));
        }
    }

    @Test void overlayTimeLeftCountsDownLiveAndIsAbsentOutsideARun() throws Exception {
        PyramidPlunderScript script = new PyramidPlunderScript();
        assertEquals(-1, script.getSecondsLeft(), 0.001, "not in the pyramid");
        set(script, "inPyramid", true);
        assertEquals(-1, script.getSecondsLeft(), 0.001, "no run active");
        set(script, "runActive", true);
        set(script, "runStartedAt", System.currentTimeMillis() - 100_000L);
        assertEquals(200, script.getSecondsLeft(), 1.5);
        set(script, "runStartedAt", System.currentTimeMillis() - 400_000L);
        assertEquals(0, script.getSecondsLeft(), 0.001, "never negative");
    }

    @Test void poisonDoesNotInterruptTimedRoom() throws Exception {
        PyramidPlunderScript script = new PyramidPlunderScript();
        set(script, "config", mock(PyramidPlunderConfig.class));
        set(script, "eatAt", 55);
        Object view = nested("View");
        set(view, "poisoned", true);
        set(view, "hp", 90);
        set(view, "maxHp", 99);
        Method survive = PyramidPlunderScript.class.getDeclaredMethod("survive", view.getClass(), boolean.class);
        survive.setAccessible(true);
        try (var inventory = mockStatic(Rs2Inventory.class)) {
            assertEquals(false, survive.invoke(script, view, true));
            inventory.verifyNoInteractions();
        }
    }

    @SuppressWarnings("unchecked")
    @Test void lockSelectionSurvivesNearestDoorReorderingUntilOpened() throws Exception {
        PyramidPlunderScript script = new PyramidPlunderScript();
        Object scan = nested("RoomScan");
        List<Rs2TileObjectModel> closed = (List<Rs2TileObjectModel>) get(scan, "closedDoors");
        Rs2TileObjectModel first = mock(Rs2TileObjectModel.class);
        Rs2TileObjectModel second = mock(Rs2TileObjectModel.class);
        when(first.getId()).thenReturn(26618);
        when(second.getId()).thenReturn(26619);
        Method pick = PyramidPlunderScript.class.getDeclaredMethod("pickDoor", int.class, scan.getClass());
        pick.setAccessible(true);
        closed.add(first);
        closed.add(second);
        pick.invoke(script, 1, scan);
        assertEquals(26618, get(script, "selectedDoor"));
        Collections.reverse(closed);
        pick.invoke(script, 1, scan);
        assertEquals(26618, get(script, "selectedDoor"));
        closed.remove(first);
        List<Rs2TileObjectModel> open = (List<Rs2TileObjectModel>) get(scan, "openDoors");
        open.add(first);
        pick.invoke(script, 1, scan);
        assertEquals(26618, get(script, "selectedDoor")); // Wait for the opened door's outcome.
        script.onGameMessage("This door leads to a dead end");
        pick.invoke(script, 1, scan);
        assertEquals(26619, get(script, "selectedDoor"));
        verify(first, never()).click(anyString()); // No input owner was installed in this test.
    }
}
