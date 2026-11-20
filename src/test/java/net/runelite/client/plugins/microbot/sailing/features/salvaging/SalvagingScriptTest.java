package net.runelite.client.plugins.microbot.sailing.features.salvaging;

import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.sailing.SailingConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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
