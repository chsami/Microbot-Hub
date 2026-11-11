package net.runelite.client.plugins.microbot.droefficientwalker.sailing.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SeaPlanningHoldTest {
    private static SeaGrid basin(boolean opened) {
        return new SeaGrid() {
            public byte cell(int x,int y) {
                return x>=40&&x<=100&&y>=40&&y<=60&&(opened||x!=80)?OPEN:BLOCKED;
            }
            public boolean live(int x,int y) {return true;}
        };
    }
    @Test public void disconnectedKnownWaypointRetainsOnlyHullClearProgress() {
        SeaGrid grid=basin(false);
        Hull hull=new Hull(1,2,0);
        SeaPoint start=new SeaPoint(50,50),goal=new SeaPoint(90,50);
        SeaPathfinder.Result route=new SeaPathfinder().find(grid,hull,start,0,goal,1,
                20000,1000,p->0,()->false,0);
        assertEquals(SeaPathfinder.Outcome.FRONTIER,route.outcome);
        assertTrue(route.usable());
        assertTrue(route.path.get(route.path.size()-1).distance(goal)<start.distance(goal)-8);
        int heading=0;
        for(int i=1;i<route.path.size();i++) {
            SeaPoint a=route.path.get(i-1),b=route.path.get(i);
            int next=Hull.heading(a,b);
            assertTrue(hull.turnClear(grid,a,heading,next,true));
            assertTrue(hull.translationClear(grid,a,b,next,true));
            assertTrue(b.x<80);
            heading=next;
        }
    }
    private static SeaPilot pilot() {
        return new SeaPilot(new SeaPilot.Settings(2,28,0,20000,8000,180000,550,75,
                36,0,2),new SeaPoint(90,50),0);
    }
    private static SeaGrid berth(boolean opened) {
        return new SeaGrid() {
            public byte cell(int x,int y) {
                return opened||x>=48&&x<=52&&y>=48&&y<=52||x==90&&y==50?OPEN:BLOCKED;
            }
            public boolean live(int x,int y) {return true;}
        };
    }
    @Test public void stationaryHoldSurvivesEightSecondsAndResumesOnFreshClearWater() {
        SeaPilot pilot=pilot();Hull hull=new Hull(1,2,0);SeaPose pose=new SeaPose(50,50,0);
        for(int tick=1;tick<=30;tick++) {
            SeaPilot.Decision d=pilot.step(berth(false),hull,pose,0,3.5,.5,false,
                    tick,100,tick*600L,()->false);
            assertEquals(SeaPilot.Action.WAIT,d.action);
        }
        assertTrue(pilot.needsVoyageReplan());
        SeaPilot.Decision resumed=pilot.step(berth(true),hull,pose,0,3.5,.5,false,
                31,100,18600,()->false);
        assertEquals(SeaPilot.Action.START,resumed.action);
    }
    @Test public void holdStillEndsAfterTheFullRecoveryWindowWithoutUnsafeInput() {
        SeaPilot pilot=pilot();Hull hull=new Hull(1,2,0);SeaPose pose=new SeaPose(50,50,0);
        pilot.step(berth(false),hull,pose,0,3.5,.5,false,1,100,600,()->false);
        SeaPilot.Decision confirming=pilot.step(berth(false),hull,pose,0,3.5,.5,false,
                2,100,181201,()->false);
        assertEquals(SeaPilot.Action.WAIT,confirming.action);
        SeaPilot.Decision failed=pilot.step(berth(false),hull,pose,0,3.5,.5,false,
                3,100,181801,()->false);
        assertEquals(SeaPilot.Action.BLOCKED,failed.action);
        assertTrue(failed.reason.contains("progress")||failed.reason.contains("recovery window"));
    }
    @Test public void forwardStallStopsThenReplansWithoutPermanentlyEndingTheVoyage() {
        SeaPilot pilot=pilot();Hull hull=new Hull(1,2,0);SeaPose pose=new SeaPose(50,50,0);
        pilot.step(berth(true),hull,pose,2,3.5,.5,false,1,100,600,()->false);
        SeaPilot.Decision stopped=pilot.step(berth(true),hull,pose,2,3.5,.5,false,
                15,100,9000,()->false);
        assertEquals(SeaPilot.Action.STOP,stopped.action);
        assertTrue(pilot.needsVoyageReplan());
        SeaPilot.Decision resumed=pilot.step(berth(true),hull,pose,0,3.5,.5,false,
                16,100,9600,()->false);
        assertEquals(SeaPilot.Action.START,resumed.action);
    }
}
