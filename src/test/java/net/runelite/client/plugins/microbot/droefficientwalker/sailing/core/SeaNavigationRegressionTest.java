package net.runelite.client.plugins.microbot.droefficientwalker.sailing.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the imported 0.1.9 engine, which intentionally retains the earlier pilot policy. */
public class SeaNavigationRegressionTest {
    private static final Hull SLOOP = new Hull(3, 8, 0);
    private static final SeaGrid OPEN_SEA = new SeaGrid() {
        @Override public byte cell(int x, int y) { return OPEN; }
        @Override public boolean live(int x, int y) { return true; }
    };
    private static SeaPilot pilot(SeaPoint goal) {
        return new SeaPilot(new SeaPilot.Settings(2, 28, 3, 60000,
                8000, 180000, 550, 2000, 36), goal, 1000);
    }
    private static SeaPathfinder.Result route(SeaGrid grid, SeaPoint from, int heading, SeaPoint to) {
        return new SeaPathfinder().find(grid, SLOOP, from, heading, to, 2, 60000, 2000, p -> 0, () -> false);
    }
    private static void clearEdges(SeaGrid grid, SeaPathfinder.Result result, int heading) {
        for (int i = 1; i < result.path.size(); i++) {
            SeaPoint from = result.path.get(i - 1), to = result.path.get(i);
            int next = Hull.heading(from, to);
            assertTrue(SLOOP.stepClear(grid, from, to, heading, next, false), "Each imported A* edge must clear the hull");
            heading = next;
        }
    }
    @Test public void normalNavigationNeverDispatchesReverse() {
        SeaPilot pilot=pilot(new SeaPoint(140,90));
        for(int tick=1;tick<=20;tick++)assertNotEquals(SeaPilot.Action.REVERSE,pilot.step(OPEN_SEA,SLOOP,new SeaPoint(60,90),0,tick<4,false,tick,1000,1000+tick*1000L,()->false).action);
    }
    @Test public void observedReverseStopsBeforeForwardMotion() {
        SeaPilot pilot=pilot(new SeaPoint(140,90));
        assertEquals(SeaPilot.Action.STOP,pilot.step(OPEN_SEA,SLOOP,new SeaPoint(60,90),0,true,true,1,1000,1600,()->false).action);
        for(int tick=2;tick<=6;tick++){SeaPilot.Action action=pilot.step(OPEN_SEA,SLOOP,new SeaPoint(60,90),0,true,true,tick,1000,1000+tick*600L,()->false).action;assertTrue(action==SeaPilot.Action.STOP||action==SeaPilot.Action.WAIT||action==SeaPilot.Action.BLOCKED);}
    }
    @Test public void cancellationStopsObservedMotionBeforeBlocking() {
        SeaPilot pilot=pilot(new SeaPoint(140,90));
        assertEquals(SeaPilot.Action.STOP,pilot.step(OPEN_SEA,SLOOP,new SeaPoint(60,90),0,true,false,1,1000,1600,()->true).action);
        assertNotEquals(SeaPilot.Action.ARRIVED,pilot.step(OPEN_SEA,SLOOP,new SeaPoint(61,90),0,false,false,2,1000,2200,()->true).action);
        pilot.step(OPEN_SEA,SLOOP,new SeaPoint(61,90),0,false,false,3,1000,2800,()->true);
        assertEquals(SeaPilot.Action.BLOCKED,pilot.step(OPEN_SEA,SLOOP,new SeaPoint(61,90),0,false,false,4,1000,3400,()->true).action);
    }
    @Test public void pauseResumeDiscardsPendingCommandsAndOldRoute() {
        SeaPilot pilot=pilot(new SeaPoint(140,90));pilot.step(OPEN_SEA,SLOOP,new SeaPoint(60,90),0,false,false,1,1000,1600,()->false);
        pilot.resumeAfterPause(13000);assertTrue(pilot.localPath().isEmpty());
        SeaPilot.Decision resumed=pilot.step(OPEN_SEA,SLOOP,new SeaPoint(60,90),0,false,false,1,1000,13000,()->false);
        assertNotEquals(SeaPilot.Action.REVERSE,resumed.action);assertNotEquals(SeaPilot.Action.BLOCKED,resumed.action);
    }
    @Test public void resumeStopsAnObservedReverseBeforeAnyForwardCommand() {
        SeaPilot pilot=pilot(new SeaPoint(140,90));pilot.resumeAfterPause(13000);
        assertEquals(SeaPilot.Action.STOP,pilot.step(OPEN_SEA,SLOOP,new SeaPoint(60,90),0,true,true,1,1000,13000,()->false).action);
    }
    @Test public void oneAndTwoTilePeninsulasRetainHardHullClearance() {
        for (int width : new int[]{1, 2}) {
            SeaGrid grid = new SeaGrid() {
                @Override public byte cell(int x, int y) {
                    if (x < 20 || x > 200 || y < 20 || y > 200) return UNKNOWN;
                    return x >= 100 && x < 100 + width && y <= 100 ? BLOCKED : OPEN;
                }
                @Override public boolean live(int x, int y) { return cell(x, y) != UNKNOWN; }
            };
            SeaPathfinder.Result result = route(grid, new SeaPoint(60, 90), 0, new SeaPoint(140, 90));
            assertEquals(SeaPathfinder.Outcome.COMPLETE, result.outcome);
            clearEdges(grid, result, 0);
        }
    }
    @Test public void nativeSloopFitsTheExistingThreeTileChannel() {
        SeaGrid grid = new SeaGrid() {
            @Override public byte cell(int x, int y) { return y >= 100 && y <= 102 ? OPEN : BLOCKED; }
            @Override public boolean live(int x, int y) { return true; }
        };
        SeaPathfinder.Result result = route(grid, new SeaPoint(60, 101), 0, new SeaPoint(120, 101));
        assertEquals(SeaPathfinder.Outcome.COMPLETE, result.outcome);
        for (SeaPoint point : result.path) assertEquals(101, point.y);
        clearEdges(grid, result, 0);
    }
    @Test public void cancellationCannotReturnAnExecutableSeaRoute() {
        SeaPathfinder.Result result = new SeaPathfinder().find(OPEN_SEA, SLOOP, new SeaPoint(60, 90), 0,
                new SeaPoint(140, 90), 2, 60000, 2000, p -> 0, () -> true);
        assertEquals(SeaPathfinder.Outcome.CANCELLED, result.outcome);
        assertFalse(result.usable());
    }
    @Test public void unknownWaterCannotBecomeACompleteRoute() {
        SeaGrid grid = new SeaGrid() {
            @Override public byte cell(int x, int y) { return x <= 100 ? OPEN : UNKNOWN; }
            @Override public boolean live(int x, int y) { return cell(x, y) != UNKNOWN; }
        };
        SeaPathfinder.Result result = route(grid, new SeaPoint(60, 90), 0, new SeaPoint(140, 90));
        assertEquals(SeaPathfinder.Outcome.FRONTIER, result.outcome);
        clearEdges(grid, result, 0);
    }
    @Test public void arrivalRequiresTwoObservedStoppedTicks() {
        SeaPoint goal = new SeaPoint(100, 100);
        SeaPilot pilot = pilot(goal);
        assertEquals(SeaPilot.Action.STOP, pilot.step(OPEN_SEA, SLOOP, goal, 0, true, 1, 1000, 1600, () -> false).action);
        assertEquals(SeaPilot.Action.WAIT, pilot.step(OPEN_SEA, SLOOP, goal, 0, false, 2, 1000, 2200, () -> false).action);
        assertEquals(SeaPilot.Action.ARRIVED, pilot.step(OPEN_SEA, SLOOP, goal, 0, false, 3, 1000, 2800, () -> false).action);
    }
    @Test public void theSameTickCannotDispatchAnotherCommand() {
        SeaPilot pilot = pilot(new SeaPoint(140, 90));
        pilot.step(OPEN_SEA, SLOOP, new SeaPoint(60, 90), 0, false, 1, 1000, 1600, () -> false);
        SeaPilot.Decision next = pilot.step(OPEN_SEA, SLOOP, new SeaPoint(60, 90), 0, false, 1, 1000, 2200, () -> false);
        assertEquals(SeaPilot.Action.WAIT, next.action);
        assertTrue(next.reason.contains("server tick"));
    }
    @Test public void newlyUnknownLiveSceneOverridesPreviouslyClearAtlas() {
        OceanAtlas atlas = new OceanAtlas();
        atlas.merge(new SceneChart(1, 100, 100, 1, 1, new byte[]{SeaGrid.OPEN}));
        SeaGrid grid = atlas.snapshot(new SceneChart(2, 100, 100, 1, 1, new byte[]{SeaGrid.UNKNOWN}), Collections.emptyList());
        assertEquals(SeaGrid.UNKNOWN, grid.cell(100, 100)); assertFalse(grid.live(100, 100));
    }
    @Test public void recordedLittlePearlChartStillHasAClearHullRoute() throws Exception {
        byte[] cells = new byte[88 * 88];
        InputStream source = getClass().getResourceAsStream("/efficient-sailing/little-pearl-sea-chart.csv");
        assertNotNull(source);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8))) {
            reader.readLine();
            for (String line; (line = reader.readLine()) != null;) {
                String[] fields = line.split(",");
                cells[(Integer.parseInt(fields[0]) - 3376) * 88 + Integer.parseInt(fields[1]) - 2064] = Byte.parseByte(fields[2]);
            }
        }
        SeaGrid grid = new OceanAtlas().snapshot(new SceneChart(4905, 3376, 2064, 88, 88, cells), Collections.emptyList());
        SeaPathfinder.Result result = new SeaPathfinder().find(grid, SLOOP, new SeaPoint(3437, 2127), 10,
                new SeaPoint(3402, 2135), 6, 60000, 2000, p -> 0, () -> false);
        assertEquals(SeaPathfinder.Outcome.COMPLETE, result.outcome);
        clearEdges(grid, result, 10);
    }
}
