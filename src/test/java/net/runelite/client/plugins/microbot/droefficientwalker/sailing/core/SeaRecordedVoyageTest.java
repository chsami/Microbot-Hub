package net.runelite.client.plugins.microbot.droefficientwalker.sailing.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SeaRecordedVoyageTest {
    private static SeaGrid littlePearl() throws Exception {
        Map<Long,Byte> cells=new HashMap<>();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(
                SeaRecordedVoyageTest.class.getResourceAsStream("/efficient-sailing/little-pearl-sea-chart.csv"),StandardCharsets.UTF_8))) {
            reader.readLine();
            for(String line;(line=reader.readLine())!=null;) {
                String[] p=line.split(",");cells.put(SeaPoint.key(Integer.parseInt(p[0]),Integer.parseInt(p[1])),Byte.parseByte(p[2]));
            }
        }
        return new SeaGrid() {
            public byte cell(int x,int y) {return cells.getOrDefault(SeaPoint.key(x,y),UNKNOWN);}
            public boolean live(int x,int y) {return cell(x,y)!=UNKNOWN;}
        };
    }
    @Test public void recordedLittlePearlCorridorClearsTheWholeNativeSloop() throws Exception {
        SeaGrid grid=littlePearl();Hull hull=Hull.nativeBounds(0,-256,384,1280,0);
        SeaPose start=new SeaPose(3437,2135,10);
        assertTrue(hull.clear(grid,start,true), hull.obstruction(grid,start,true));
        Hull lattice=hull.atLattice(start);
        SeaPathfinder.Result route=new SeaPathfinder().find(grid,lattice,start.tile(),10,
                new SeaPoint(3402,2135),6,60000,1000,p->0,()->false,3);
        assertEquals(SeaPathfinder.Outcome.COMPLETE, route.outcome, route.detail);
        int heading=10;
        for(int i=1;i<route.path.size();i++) {
            SeaPoint a=route.path.get(i-1),b=route.path.get(i);int next=Hull.heading(a,b);
            assertTrue(hull.stepClear(grid,a,b,heading,next,true));heading=next;
        }
    }
    @Test public void reportedIdlePositionCanUseAContinuousHullClearTurn() throws Exception {
        SeaGrid grid=littlePearl();Hull hull=Hull.nativeBounds(0,-256,384,1280,0);
        SeaPose start=new SeaPose(3420.25,2127,15);
        assertTrue(hull.clear(grid,start,true));
        assertTrue(hull.turnClear(grid,start,14,true), "The full native hull has a verified turn out of this berth");
        SeaPathfinder.Result route=new SeaPathfinder().find(grid,hull.atLattice(start),start.tile(),15,
                new SeaPoint(3402,2135),6,60000,1000,p->0,()->false,3);
        assertTrue(route.usable(), route.detail);
    }
    @Test public void anOlderShortHullFixtureCannotOverrideTheNativeHull() throws Exception {
        SeaGrid grid=littlePearl();Hull hull=Hull.nativeBounds(0,-256,384,1280,0);
        SeaPose start=new SeaPose(3437,2127,10);
        assertFalse(hull.clear(grid,start,true));
        SeaPathfinder.Result route=new SeaPathfinder().find(grid,hull,start.tile(),10,
                new SeaPoint(3402,2135),6,60000,1000,p->0,()->false,3);
        assertEquals(SeaPathfinder.Outcome.START_BLOCKED,route.outcome);
    }
    @Test public void reportedIdleBerthCanCompleteARecordedLocalVoyage() throws Exception {
        SeaVoyageVerificationTest.sail(littlePearl(),new SeaPose(3420.25,2127,15),new SeaPoint(3402,2135),0,2);
    }
    @Test public void knownCoarseVoyageHintsNeverGrantLiveMotionPermission() throws Exception {
        SeaGrid chart=littlePearl();
        GlobalSeaPlanner.Result voyage=new GlobalSeaPlanner(new SeaReferenceMap()).plan(chart,
                new SeaPoint(3437,2127),new SeaPoint(3310,2185),6,1500,()->false);
        assertTrue(voyage.usable(), voyage.detail);
        SeaGrid unloaded=new SeaGrid() {
            public byte cell(int x,int y) {return chart.cell(x,y);}
            public boolean live(int x,int y) {return false;}
        };
        SeaPilot pilot=new SeaPilot(new SeaPilot.Settings(6,28,0,60000,8000,180000,550,100),new SeaPoint(3310,2185),0);
        pilot.setVoyageRoute(voyage);
        SeaPilot.Decision decision=pilot.step(unloaded,Hull.nativeBounds(0,-256,384,1280,0),
                new SeaPose(3437,2127,10),0,3.5,.5,false,1,1000,600,()->false);
        assertEquals(SeaPilot.Action.WAIT,decision.action);
        assertTrue(decision.reason.contains("Live hull clearance"));
    }
}
