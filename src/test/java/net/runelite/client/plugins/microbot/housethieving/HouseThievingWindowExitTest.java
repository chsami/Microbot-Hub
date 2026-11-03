package net.runelite.client.plugins.microbot.housethieving;

import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.event.Level;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HouseThievingWindowExitTest {

    private static final int WINDOW_ID = 52998;

    @Test
    void exitsThroughWindowOfferingExit() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            Rs2TileObjectQueryable query = stubWindow(microbot, Set.of("Exit"));

            assertTrue(HouseThievingScript.clickWindowExit(WINDOW_ID));
            verify(query).interact("Exit");
            verify(query, never()).interact("Exit-window");
        }
    }

    @Test
    void fallsBackToLegacyExitWindowAction() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            Rs2TileObjectQueryable query = stubWindow(microbot, Set.of("Exit-window"));

            assertTrue(HouseThievingScript.clickWindowExit(WINDOW_ID));
            verify(query).interact("Exit");
            verify(query).interact("Exit-window");
        }
    }

    @Test
    void reportsFailureWhenWindowOffersNoExit() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            stubWindow(microbot, Set.of("Examine"));

            assertFalse(HouseThievingScript.clickWindowExit(WINDOW_ID));
        }
    }

    @Test
    void leavesThroughWindowAndMovesToNextHouse() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Tile> rs2Tile = mockStatic(Rs2Tile.class);
             MockedStatic<Rs2Camera> rs2Camera = mockStatic(Rs2Camera.class);
             MockedStatic<Rs2Random> rs2Random = mockStatic(Rs2Random.class)) {
            stubWindow(microbot, Set.of("Exit"));
            stubWindowTile(rs2Tile, rs2Camera);
            HouseThievingScript script = scriptInLaviniaHouse();

            assertTrue(script.exitHouse(false));

            microbot.verify(() -> Microbot.log(anyString(), any(Level.class)), never());
            assertEquals(HouseThievingScript.State.FINDING_HOUSE, script.state);
            assertNotEquals(ThievingHouse.LAVINIA, script.currentThievingHouse);
        }
    }

    @Test
    void warnsOnceWhileWindowKeepsRefusingExit() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Tile> rs2Tile = mockStatic(Rs2Tile.class);
             MockedStatic<Rs2Camera> rs2Camera = mockStatic(Rs2Camera.class);
             MockedStatic<Rs2Random> rs2Random = mockStatic(Rs2Random.class)) {
            stubWindow(microbot, Set.of("Examine"));
            stubWindowTile(rs2Tile, rs2Camera);
            HouseThievingScript script = scriptInLaviniaHouse();

            assertFalse(script.exitHouse(false));
            assertFalse(script.exitHouse(false));
            assertFalse(script.exitHouse(false));

            microbot.verify(() -> Microbot.log(anyString(), eq(Level.WARN)), times(1));
            microbot.verify(() -> Microbot.log(contains("carries on once you're outside"), eq(Level.WARN)));
            assertEquals(HouseThievingScript.State.THIEVING_HOUSES, script.state);
            assertEquals(ThievingHouse.LAVINIA, script.currentThievingHouse);
        }
    }

    @Test
    void warnsThatOwnerIsComingHomeWhenWindowRefusesExit() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Tile> rs2Tile = mockStatic(Rs2Tile.class);
             MockedStatic<Rs2Camera> rs2Camera = mockStatic(Rs2Camera.class);
             MockedStatic<Rs2Random> rs2Random = mockStatic(Rs2Random.class)) {
            stubWindow(microbot, Set.of("Examine"));
            stubWindowTile(rs2Tile, rs2Camera);
            HouseThievingScript script = scriptInLaviniaHouse();

            assertFalse(script.exitHouse(true));

            microbot.verify(() -> Microbot.log(contains("Lavinia is coming home"), eq(Level.WARN)));
            assertEquals(HouseThievingScript.State.THIEVING_HOUSES, script.state);
        }
    }

    @Test
    void carriesOnOncePlayerLeftTheHouseThemselves() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Tile> rs2Tile = mockStatic(Rs2Tile.class);
             MockedStatic<Rs2Camera> rs2Camera = mockStatic(Rs2Camera.class);
             MockedStatic<Rs2Random> rs2Random = mockStatic(Rs2Random.class)) {
            stubWindow(microbot, Set.of("Examine"));
            stubWindowTile(rs2Tile, rs2Camera);
            HouseThievingScript script = scriptInLaviniaHouse();

            assertFalse(script.exitHouse(false));

            rs2Tile.when(() -> Rs2Tile.isTileReachable(ThievingHouse.LAVINIA.houseCenter)).thenReturn(false);

            assertTrue(script.exitHouse(false));
            microbot.verify(() -> Microbot.log(contains("Left Lavinia's house"), eq(Level.INFO)));
            assertEquals(HouseThievingScript.State.FINDING_HOUSE, script.state);
            assertNotEquals(ThievingHouse.LAVINIA, script.currentThievingHouse);
        }
    }

    private static HouseThievingScript scriptInLaviniaHouse() {
        HouseThievingScript script = new HouseThievingScript(null);
        script.currentThievingHouse = ThievingHouse.LAVINIA;
        script.state = HouseThievingScript.State.THIEVING_HOUSES;
        return script;
    }

    private static void stubWindowTile(MockedStatic<Rs2Tile> rs2Tile, MockedStatic<Rs2Camera> rs2Camera) {
        WallObject window = mock(WallObject.class);
        when(window.getId()).thenReturn(WINDOW_ID);
        Tile tile = mock(Tile.class);
        when(tile.getWallObject()).thenReturn(window);
        rs2Tile.when(() -> Rs2Tile.getTile(anyInt(), anyInt())).thenReturn(tile);
        rs2Tile.when(() -> Rs2Tile.isTileReachable(any(WorldPoint.class))).thenReturn(true);
        rs2Camera.when(() -> Rs2Camera.isTileOnScreen(any(LocalPoint.class))).thenReturn(true);
    }

    private static Rs2TileObjectQueryable stubWindow(MockedStatic<Microbot> microbot, Set<String> actions) {
        Rs2TileObjectQueryable query = mock(Rs2TileObjectQueryable.class);
        when(query.withId(anyInt())).thenReturn(query);
        when(query.interact(anyString())).thenAnswer(invocation -> actions.contains(invocation.<String>getArgument(0)));
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        when(cache.query()).thenReturn(query);
        microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
        return query;
    }
}
