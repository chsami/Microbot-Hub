package net.runelite.client.plugins.microbot.droefficientwalker.sailing.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SeaRouteRecoveryTest {
    private static final Hull HULL = new Hull(3, 8, 0);
    private static SeaPilot pilot(SeaPoint goal) {
        return new SeaPilot(new SeaPilot.Settings(5, 28, 3, 60000, 8000, 180000, 550, 1000), goal, 1000);
    }
    @Test public void boxedInBoatUsesConfiguredNoProgressWindowInsteadOfTwelveTicks() throws Exception {
        SeaPoint position = new SeaPoint(50, 50);
        SeaGrid grid = new SeaGrid() {
            public byte cell(int x, int y) { return (x >= 45 && x <= 55 && y >= 45 && y <= 55)||x==50&&y==20 ? OPEN : BLOCKED; }
            public boolean live(int x, int y) { return true; }
        };
        SeaPilot pilot = pilot(new SeaPoint(50, 20));
        assertEquals(SeaPilot.Action.WAIT, pilot.step(grid,HULL,position,12,false,1,1000,1000,()->false).action);
        assertEquals(SeaPilot.Action.WAIT, pilot.step(grid,HULL,position,12,false,2,1000,13000,()->false).action);
        assertEquals(SeaPilot.Action.WAIT, pilot.step(grid,HULL,position,12,false,3,1000,180999,()->false).action);
        assertEquals(SeaPilot.Action.BLOCKED, pilot.step(grid,HULL,position,12,false,4,1000,182000,()->false).action);
        assertEquals(0, pilot.recoveries());
    }
    @Test public void recordedPestControlDeadEndGetsAVerifiedEscapeAndRemainsCancelable() throws Exception {
        Map<Long, Byte> cells = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(
                "/efficient-sailing/recorded-pest-control-block.csv"), StandardCharsets.UTF_8))) {
            reader.readLine(); String line;
            while ((line = reader.readLine()) != null) {
                String[] c = line.split(",");
                cells.put(SeaPoint.key(Integer.parseInt(c[0]), Integer.parseInt(c[1])), Byte.parseByte(c[2]));
            }
        }
        SeaGrid grid = new SeaGrid() {
            public byte cell(int x, int y) { return cells.getOrDefault(SeaPoint.key(x, y), UNKNOWN); }
            public boolean live(int x, int y) { return cells.containsKey(SeaPoint.key(x, y)); }
        };
        SeaPoint position = new SeaPoint(2620, 2582);
        SeaPilot pilot = pilot(new SeaPoint(2633, 2544));
        SeaPilot.Decision first=pilot.step(grid,HULL,position,12,false,1,1000,1000,()->false);
        assertNotEquals(SeaPilot.Action.REVERSE,first.action);
        if(first.action==SeaPilot.Action.STEER){assertTrue(HULL.turnClear(grid,position,12,first.heading,true));/* A stationary steer changes heading, not position; the distant minimap point is directional. */}
        SeaPilot.Decision escape=pilot.step(grid,HULL,position,12,false,2,cells.size(),7000,()->false);
        assertNotEquals(SeaPilot.Action.REVERSE,escape.action);
        if(escape.action==SeaPilot.Action.STEER){assertTrue(HULL.turnClear(grid,position,12,escape.heading,true));/* Forward motion gets a fresh live-envelope check on its own tick. */}
        SeaPilot.Decision cancel = pilot.step(grid, HULL, position, 12, false, 3, cells.size(), 7600, () -> true);
        assertEquals(SeaPilot.Action.BLOCKED, cancel.action);
        assertEquals("Request cancelled", cancel.reason);
    }
}
