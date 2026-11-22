package net.runelite.client.plugins.microbot.sailing.features.salvaging;

import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.sailing.SailingConfig;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalvagingScriptTest {

    @Test
    void isWithinSalvageAreaReturnsFalseWhenPlayerLocationIsNull() throws Exception {
        var wreck = wreckAt(new WorldPoint(3000, 3000, 0));

        assertFalse(isWithinSalvageArea(null, wreck));
    }

    @Test
    void isWithinSalvageAreaReturnsFalseWhenWreckLocationIsNull() throws Exception {
        var wreck = wreckAt(null);

        assertFalse(isWithinSalvageArea(new WorldPoint(3000, 3000, 0), wreck));
    }

    @Test
    void isWithinSalvageAreaUsesDistanceToWreck() throws Exception {
        var player = new WorldPoint(3000, 3000, 0);

        assertTrue(isWithinSalvageArea(player, wreckAt(new WorldPoint(3010, 3000, 0))));
        assertFalse(isWithinSalvageArea(player, wreckAt(new WorldPoint(3100, 3000, 0))));
    }

    @Test
    void nearestStationOnWorldViewIgnoresPortStationWhenOnBoat() {
        var boat = worldView(7);
        var topLevel = worldView(-1);
        var portStation = stationAt(topLevel, new WorldPoint(3063, 2991, 0));

        assertNull(SalvagingScript.nearestStationOnWorldView(
                List.of(portStation), boat, new WorldPoint(3060, 2990, 1)));
    }

    @Test
    void nearestStationOnWorldViewPicksStationOnPlayerBoat() {
        var boat = worldView(7);
        var topLevel = worldView(-1);
        var portStation = stationAt(topLevel, new WorldPoint(3061, 2990, 0));
        var boatStation = stationAt(boat, new WorldPoint(3070, 2995, 1));

        assertSame(boatStation, SalvagingScript.nearestStationOnWorldView(
                List.of(portStation, boatStation), boat, new WorldPoint(3060, 2990, 1)));
    }

    @Test
    void nearestStationOnWorldViewSkipsStationWithoutWorldView() {
        var boat = worldView(7);
        var station = stationAt(null, new WorldPoint(3060, 2990, 1));

        assertNull(SalvagingScript.nearestStationOnWorldView(
                List.of(station), boat, new WorldPoint(3060, 2990, 1)));
    }

    @Test
    void dropItemNamesTrimsAndSkipsEmptyEntries() {
        var config = mock(SailingConfig.class);
        when(config.dropItems()).thenReturn(" casket, ,Small salvage ,plank,");

        assertArrayEquals(new String[]{"casket", "Small salvage", "plank"}, SalvagingScript.dropItemNames(config));
    }

    @Test
    void dropItemNamesIsEmptyWhenDropListIsBlankOrMissing() {
        var config = mock(SailingConfig.class);
        when(config.dropItems()).thenReturn("  ");
        assertEquals(0, SalvagingScript.dropItemNames(config).length);

        when(config.dropItems()).thenReturn(null);
        assertEquals(0, SalvagingScript.dropItemNames(config).length);
    }

    @Test
    void depositSalvageOrDropUsesStationOnBoat() throws Exception {
        var station = mock(Rs2TileObjectModel.class);
        var config = configWithDropItems("Small salvage");
        var script = new SalvagingScript(null, null, null);
        var thread = clientThreadFinding(station);
        String previousStatus = Microbot.status;

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Global> global = mockStatic(Global.class)) {
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            inventory.when(() -> Rs2Inventory.hasItem(new String[]{"Small salvage"}, true)).thenReturn(true);

            depositSalvageOrDrop(script, config);

            verify(station).click();
            inventory.verify(() -> Rs2Inventory.dropAll(any(String[].class)), never());
            assertFalse(script.isWaitingForDropSetup());
        } finally {
            Microbot.status = previousStatus;
        }
    }

    @Test
    void depositSalvageOrDropDropsListedSalvageWhenBoatHasNoStation() throws Exception {
        var config = configWithDropItems("Small salvage");
        var script = new SalvagingScript(null, null, null);
        var thread = clientThreadFinding(null);
        String previousStatus = Microbot.status;

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Global> global = mockStatic(Global.class)) {
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            inventory.when(() -> Rs2Inventory.hasItem(new String[]{"Small salvage"}, true)).thenReturn(true);

            depositSalvageOrDrop(script, config);

            inventory.verify(() -> Rs2Inventory.dropAll(new String[]{"Small salvage"}));
            assertFalse(script.isWaitingForDropSetup());
        } finally {
            Microbot.status = previousStatus;
        }
    }

    @Test
    void depositSalvageOrDropWaitsWhenNothingListedIsInInventory() throws Exception {
        var config = configWithDropItems("Oyster pearls, Casket");
        var script = new SalvagingScript(null, null, null);
        var thread = clientThreadFinding(null);
        String previousStatus = Microbot.status;

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Global> global = mockStatic(Global.class)) {
            microbot.when(Microbot::getClientThread).thenReturn(thread);

            depositSalvageOrDrop(script, config);

            inventory.verify(() -> Rs2Inventory.dropAll(any(String[].class)), never());
            assertTrue(script.isWaitingForDropSetup());
            assertEquals(SalvagingScript.NO_STATION_NOTHING_TO_DROP_MESSAGE, Microbot.status);
        } finally {
            Microbot.status = previousStatus;
        }
    }

    @Test
    void noStationMessageDoesNotSuggestCargoHoldWhenItIsAlreadyOn() {
        assertTrue(SalvagingScript.noStationNothingToDropMessage(false).contains("turn on Use Cargo Hold"));
        assertFalse(SalvagingScript.noStationNothingToDropMessage(true).contains("turn on Use Cargo Hold"));
        assertTrue(SalvagingScript.noStationNothingToDropMessage(true).contains("exact salvage names"));
        assertTrue(SalvagingScript.noStationNothingToDropMessage(false).contains("exact salvage names"));
    }

    @Test
    void inventoryHasCleanupWorkIgnoresPartialDropListMatches() throws Exception {
        var config = configWithDropItems("plank, oak plank");
        var script = new SalvagingScript(null, null, null);

        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            inventory.when(() -> Rs2Inventory.hasItem("plank")).thenReturn(true);
            inventory.when(() -> Rs2Inventory.hasItem(new String[]{"plank", "oak plank"}, false)).thenReturn(true);

            assertFalse(inventoryHasCleanupWork(script, config));
        }
    }

    @Test
    void inventoryHasCleanupWorkFindsExactDropListMatches() throws Exception {
        var config = configWithDropItems("plank, oak plank");
        var script = new SalvagingScript(null, null, null);

        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            inventory.when(() -> Rs2Inventory.hasItem(new String[]{"plank", "oak plank"}, true)).thenReturn(true);

            assertTrue(inventoryHasCleanupWork(script, config));
        }
    }

    private static boolean inventoryHasCleanupWork(SalvagingScript script, SailingConfig config) throws Exception {
        Method method = SalvagingScript.class.getDeclaredMethod("inventoryHasCleanupWork", SailingConfig.class);
        method.setAccessible(true);
        return (boolean) method.invoke(script, config);
    }

    private static SailingConfig configWithDropItems(String dropItems) {
        var config = mock(SailingConfig.class);
        when(config.dropItems()).thenReturn(dropItems);
        return config;
    }

    @SuppressWarnings("unchecked")
    private static ClientThread clientThreadFinding(Rs2TileObjectModel station) {
        var thread = mock(ClientThread.class);
        when(thread.invoke(any(Supplier.class))).thenReturn(station);
        return thread;
    }

    private static void depositSalvageOrDrop(SalvagingScript script, SailingConfig config) throws Exception {
        Method method = SalvagingScript.class.getDeclaredMethod("depositSalvageOrDrop", SailingConfig.class);
        method.setAccessible(true);
        method.invoke(script, config);
    }

    private static WorldView worldView(int id) {
        var wv = mock(WorldView.class);
        when(wv.getId()).thenReturn(id);
        return wv;
    }

    private static Rs2TileObjectModel stationAt(WorldView wv, WorldPoint location) {
        var station = mock(Rs2TileObjectModel.class);
        when(station.getWorldView()).thenReturn(wv);
        when(station.getWorldLocation()).thenReturn(location);
        return station;
    }

    private static boolean isWithinSalvageArea(WorldPoint playerLocation, Rs2TileObjectModel wreck) throws Exception {
        var script = new SalvagingScript(null, null, null);
        Method method = SalvagingScript.class.getDeclaredMethod("isWithinSalvageArea", WorldPoint.class, Rs2TileObjectModel.class);
        method.setAccessible(true);
        return (boolean) method.invoke(script, playerLocation, wreck);
    }

    private static Rs2TileObjectModel wreckAt(WorldPoint location) {
        var wreck = mock(Rs2TileObjectModel.class);
        when(wreck.getWorldLocation()).thenReturn(location);
        return wreck;
    }
}
