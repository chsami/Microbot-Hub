package net.runelite.client.plugins.microbot.droefficientwalker.sailing.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SeaLaunchRegressionTest {
    private static final Hull SLOOP = new Hull(3, 8, 0);
    private static SeaPilot pilot() {
        SeaPilot pilot = new SeaPilot(new SeaPilot.Settings(2, 28, 3, 60000,
                8000, 180000, 550, 2000, 36), new SeaPoint(120, 90), 1000);
        pilot.seedCruiseSpeed(4);
        return pilot;
    }
    private static SeaGrid coast() {
        return new SeaGrid() {
            public byte cell(int x, int y) {
                return x >= 70 && x <= 71 && y >= 79 && y <= 89 ? BLOCKED : OPEN;
            }
            public boolean live(int x, int y) { return true; }
        };
    }
    @Test public void anticipatesThePlannedCoastalBendBeforeLaunchingAtObservedCruiseSpeed() {
        SeaGrid grid = coast();
        SeaPoint start = new SeaPoint(60, 90);
        assertTrue(SLOOP.translationClear(grid, start, new SeaPoint(64, 90), 0, true));
        assertFalse(SLOOP.translationClear(grid, start, new SeaPoint(70, 90), 0, true));
        SeaPilot pilot = pilot();
        SeaPilot.Decision turn = pilot.step(grid, SLOOP, start, 0, false, 1, 1000, 1600, () -> false);
        assertEquals(SeaPilot.Action.STEER, turn.action);
        assertEquals(1, turn.heading);
        assertTrue(SLOOP.turnClear(grid, start, 0, turn.heading, true));
        assertTrue(SLOOP.translationClear(grid, start, turn.waypoint, turn.heading, true));
        SeaPilot.Decision launch = pilot.step(grid, SLOOP, start, turn.heading, false, 2, 1000, 2200, () -> false);
        assertEquals(SeaPilot.Action.SLOW, launch.action);
    }
    @Test public void lackOfALiveBrakingCorridorNeverRaisesSailsIntoUnknownWater() {
        SeaGrid grid = new SeaGrid() {
            public byte cell(int x, int y) { return x < 69 ? OPEN : UNKNOWN; }
            public boolean live(int x, int y) { return x < 69; }
        };
        SeaPilot.Decision next = pilot().step(grid, SLOOP, new SeaPoint(60, 90), 0,
                false, 1, 1000, 1600, () -> false);
        assertNotEquals(SeaPilot.Action.START, next.action);
    }
    @Test public void blockedDestinationStillFailsInsteadOfChangingTheRequestedMapPoint() {
        SeaGrid grid = new SeaGrid() {
            public byte cell(int x, int y) { return x == 120 && y == 90 ? BLOCKED : OPEN; }
            public boolean live(int x, int y) { return true; }
        };
        SeaPilot pilot = pilot();
        assertEquals(SeaPilot.Action.WAIT, pilot.step(grid, SLOOP, new SeaPoint(60, 90), 0,
                false, 1, 1000, 1600, () -> false).action);
        pilot.step(grid,SLOOP,new SeaPoint(60,90),0,false,2,1000,2200,()->false);
        SeaPilot.Decision failure = pilot.step(grid, SLOOP, new SeaPoint(60, 90), 0,
                false, 3, 1000, 2800, () -> false);
        assertEquals(SeaPilot.Action.BLOCKED, failure.action);
        assertTrue(failure.reason.contains("destination")&&failure.reason.contains("blocked"));
        assertEquals(new SeaPoint(120, 90), pilot.destination());
    }
    @Test public void recordedCoastalFootprintReportsTheActualConflictingTile() throws Exception {
        Map<Long, Byte> cells = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(
                "/efficient-sailing/coastal-hull-clearance.csv"), StandardCharsets.UTF_8))) {
            reader.readLine();
            for (String line; (line = reader.readLine()) != null;) {
                String[] fields = line.split(",");
                cells.put(SeaPoint.key(Integer.parseInt(fields[0]), Integer.parseInt(fields[1])), Byte.parseByte(fields[2]));
            }
        }
        SeaGrid grid = new SeaGrid() {
            public byte cell(int x, int y) { return cells.getOrDefault(SeaPoint.key(x, y), UNKNOWN); }
            public boolean live(int x, int y) { return cells.containsKey(SeaPoint.key(x, y)); }
        };
        SeaPoint boat = new SeaPoint(3054, 3129);
        assertFalse(SLOOP.clear(grid, boat, 10, true));
        String reason = SLOOP.obstruction(grid, boat, 10, true);
        assertTrue(reason.contains("cell=2"));
        assertTrue(reason.contains("pose=")&&reason.contains("tile="));
    }
}
