package net.runelite.client.plugins.microbot.droefficientwalker.sailing.core;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SeaAngledChannelTest {
    @Test public void aDiagonalHullSweepDoesNotRequireUnrelatedSidewaysDisplacements() {
        Hull hull=Hull.nativeBounds(0,-256,384,1280,0);
        SeaPoint start=new SeaPoint(100,100),end=new SeaPoint(128,72);
        Set<Long> water=new HashSet<>();
        SeaGrid collect=new SeaGrid() {
            public byte cell(int x,int y) {water.add(SeaPoint.key(x,y));return OPEN;}
            public boolean live(int x,int y) {return true;}
        };
        assertTrue(hull.translationClear(collect,start,end,14,true));
        SeaGrid channel=new SeaGrid() {
            public byte cell(int x,int y) {return water.contains(SeaPoint.key(x,y))?OPEN:BLOCKED;}
            public boolean live(int x,int y) {return true;}
        };
        assertTrue(hull.translationClear(channel,start,end,14,true));
        assertFalse(hull.clear(channel,start.add(1,0),14,true), "Sideways motion is not part of the clear diagonal sweep");
        SeaPathfinder.Result route=new SeaPathfinder().find(channel,hull,start,14,end,1,
                60000,1000,p->0,()->false,0);
        assertEquals(SeaPathfinder.Outcome.COMPLETE, route.outcome, route.detail);
        for(int i=1;i<route.path.size();i++)
            assertTrue(hull.translationClear(channel,route.path.get(i-1),route.path.get(i),14,true));
    }
}
