package net.runelite.client.plugins.microbot.droefficientwalker.sailing.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SeaClearanceRegressionTest {
    private static final Hull SLOOP = new Hull(3, 8, 0);
    private static SeaPathfinder.Result find(SeaGrid grid, SeaPoint start, int heading, SeaPoint goal) {
        return new SeaPathfinder().find(grid, SLOOP, start, heading, goal, 1, 60000, 3000, p -> 0, () -> false);
    }
    private static SeaGrid recorded(String name) throws IOException {
        Map<Long, Byte> cells = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                SeaClearanceRegressionTest.class.getResourceAsStream("/efficient-sailing/" + name), StandardCharsets.UTF_8))) {
            reader.readLine(); String line;
            while ((line = reader.readLine()) != null) {
                String[] c = line.split(",");
                cells.put(SeaPoint.key(Integer.parseInt(c[0]), Integer.parseInt(c[1])), Byte.parseByte(c[2]));
            }
        }
        return new SeaGrid() {
            public byte cell(int x, int y) { return cells.getOrDefault(SeaPoint.key(x, y), UNKNOWN); }
            public boolean live(int x, int y) { return cells.containsKey(SeaPoint.key(x, y)); }
        };
    }
    private static void sweptEdges(SeaGrid grid, SeaPathfinder.Result route, int heading) {
        assertTrue(route.usable());
        for (int i = 1; i < route.path.size(); i++) {
            SeaPoint a = route.path.get(i - 1), b = route.path.get(i);
            int next = Hull.heading(a, b);
            assertTrue(SLOOP.turnClear(grid, a, heading, next, true));
            assertTrue(SLOOP.translationClear(grid, a, b, next, true));
            heading = next;
        }
    }
    @Test public void recordedDeadEndsHaveVerifiedShallowDepartures() throws Exception {
        SeaGrid first = recorded("recorded-shallow-departure.csv");
        SeaPathfinder.Result departure = find(first, new SeaPoint(2970, 2595), 2, new SeaPoint(2979, 2591));
        assertEquals(SeaPathfinder.Outcome.COMPLETE, departure.outcome);
        assertEquals(15, Hull.heading(departure.path.get(0), departure.path.get(1)));
        sweptEdges(first, departure, 2);
        SeaGrid second = recorded("recorded-shallow-recovery.csv");
        SeaPathfinder.Result recovery = find(second, new SeaPoint(3084, 2415), 12, new SeaPoint(3080, 2406));
        assertEquals(SeaPathfinder.Outcome.COMPLETE, recovery.outcome);
        assertEquals(11, Hull.heading(recovery.path.get(0), recovery.path.get(1)));
        sweptEdges(second, recovery, 12);
    }
    @Test public void broadWaterRouteKeepsExtraHullClearanceAroundARock() {
        SeaGrid grid = new SeaGrid() {
            public byte cell(int x, int y) { return x >= 75 && x <= 85 && y >= 85 && y <= 95 ? BLOCKED : OPEN; }
            public boolean live(int x, int y) { return true; }
        };
        SeaPathfinder.Result result = find(grid, new SeaPoint(55, 90), 0, new SeaPoint(115, 90));
        assertEquals(SeaPathfinder.Outcome.COMPLETE, result.outcome);
        Hull buffered = new Hull(3, 8, 3);
        int heading = 0;
        for (int i = 1; i < result.path.size(); i++) {
            SeaPoint a = result.path.get(i - 1), b = result.path.get(i);
            heading = Hull.heading(a, b);
            assertTrue(buffered.clear(grid, b, heading, true), "Route should leave three extra hull layers near the rock: " + b);
        }
        sweptEdges(grid, result, 0);
    }
    @Test public void softBufferDoesNotCloseAPhysicallySafeNarrowChannel() {
        SeaGrid grid = new SeaGrid() {
            public byte cell(int x, int y) { return x >= 40 && x <= 50 && (y < 47 || y > 53) ? BLOCKED : OPEN; }
            public boolean live(int x, int y) { return true; }
        };
        SeaPathfinder.Result result = find(grid, new SeaPoint(25, 50), 0, new SeaPoint(65, 50));
        assertEquals(SeaPathfinder.Outcome.COMPLETE, result.outcome);
        sweptEdges(grid, result, 0);
        assertTrue(result.path.get(0).x<40&&result.path.get(result.path.size()-1).x>50);
    }
    @Test public void skippedUnsafeNearNodeCannotHideAClearForwardWaypoint() {
        SeaGrid grid=new SeaGrid(){public byte cell(int x,int y){return x==55&&y==89?BLOCKED:OPEN;}public boolean live(int x,int y){return true;}};
        SeaPoint start=new SeaPoint(60,90);SeaPilot pilot=new SeaPilot(new SeaPilot.Settings(2,28,3,60000,8000,180000,550,2000),new SeaPoint(100,90),1000);
        SeaPilot.Decision decision=pilot.step(grid,SLOOP,start,0,false,1,1000,1600,()->false);
        assertNotEquals(SeaPilot.Action.BLOCKED,decision.action);
        if(decision.action==SeaPilot.Action.STEER)assertTrue(SLOOP.translationClear(grid,start,decision.waypoint,decision.heading,true));
        assertTrue(pilot.lastRoute()!=null&&pilot.lastRoute().usable());
    }
    @Test public void steeringShortcutCannotEraseHardHullClearance() {
        SeaGrid grid=new SeaGrid(){public byte cell(int x,int y){return x>=75&&x<=85&&y>=85&&y<=95?BLOCKED:OPEN;}public boolean live(int x,int y){return true;}};
        SeaPoint start=new SeaPoint(55,90);SeaPilot pilot=new SeaPilot(new SeaPilot.Settings(1,80,3,60000,8000,180000,550,2000,64),new SeaPoint(115,90),1000);
        SeaPilot.Decision decision=pilot.step(grid,SLOOP,start,0,false,1,1000,1600,()->false);
        assertTrue(pilot.lastRoute()!=null&&pilot.lastRoute().usable());sweptEdges(grid,pilot.lastRoute(),0);
        if(decision.action==SeaPilot.Action.STEER){assertTrue(SLOOP.turnClear(grid,start,0,decision.heading,true));assertTrue(SLOOP.translationClear(grid,start,decision.waypoint,decision.heading,true));}
    }

    @Test public void arrivalWaitsForStationaryPositionEvenWhenMovementFlagClearsEarly() {
        SeaGrid grid=new SeaGrid(){public byte cell(int x,int y){return OPEN;}public boolean live(int x,int y){return true;}};
        SeaPilot pilot=new SeaPilot(new SeaPilot.Settings(3,28,3,60000,8000,180000,550,2000),new SeaPoint(100,90),1000);
        assertEquals(SeaPilot.Action.STOP,pilot.step(grid,SLOOP,new SeaPoint(98,90),0,true,1,1000,1600,()->false).action);
        assertNotEquals(SeaPilot.Action.ARRIVED,pilot.step(grid,SLOOP,new SeaPoint(99,90),0,false,2,1000,2200,()->false).action);
        assertNotEquals(SeaPilot.Action.ARRIVED,pilot.step(grid,SLOOP,new SeaPoint(100,90),0,false,3,1000,2800,()->false).action);
        assertNotEquals(SeaPilot.Action.ARRIVED,pilot.step(grid,SLOOP,new SeaPoint(100,90),0,false,4,1000,3400,()->false).action);
        assertEquals(SeaPilot.Action.ARRIVED,pilot.step(grid,SLOOP,new SeaPoint(100,90),0,false,5,1000,4000,()->false).action);
    }
}
