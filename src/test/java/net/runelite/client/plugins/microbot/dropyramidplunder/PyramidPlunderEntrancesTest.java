package net.runelite.client.plugins.microbot.dropyramidplunder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.dropyramidplunder.PyramidPlunderEntrances.Door;
import org.junit.jupiter.api.Test;

class PyramidPlunderEntrancesTest {
    private static final WorldPoint NORTH_EXIT = new WorldPoint(3288, 2801, 0);
    private static final int[] NOBODY = {0, 0, 0, 0};

    @Test void theRecordedDoorObjectsAreTheFourSidesOfThePyramid() {
        assertEquals(26622, Door.NORTH.objectId);
        assertEquals(26623, Door.EAST.objectId);
        assertEquals(26624, Door.SOUTH.objectId);
        assertEquals(26625, Door.WEST.objectId);
    }

    @Test void withNothingKnownTheNearestDoorIsTried() {
        PyramidPlunderEntrances entrances = new PyramidPlunderEntrances();
        assertEquals(Door.NORTH, entrances.choose(NORTH_EXIT, NOBODY, 0));
        assertEquals(Door.WEST, new PyramidPlunderEntrances().choose(new WorldPoint(3281, 2795, 0), NOBODY, 0));
    }

    @Test void aCrowdAtOneDoorBeatsTheNearestDoor() {
        PyramidPlunderEntrances entrances = new PyramidPlunderEntrances();
        assertEquals(Door.WEST, entrances.choose(NORTH_EXIT, new int[] {1, 0, 0, 4}, 0));
        // One player is not a crowd, and a tie says nothing.
        assertEquals(Door.NORTH, new PyramidPlunderEntrances().choose(NORTH_EXIT, new int[] {0, 0, 1, 0}, 0));
        assertEquals(Door.NORTH, new PyramidPlunderEntrances().choose(NORTH_EXIT, new int[] {0, 3, 3, 0}, 0));
    }

    @Test void theDoorThatWorkedIsReusedUntilItStopsWorking() {
        PyramidPlunderEntrances entrances = new PyramidPlunderEntrances();
        entrances.choose(NORTH_EXIT, new int[] {0, 0, 0, 3}, 0);
        entrances.confirm(1_000);
        assertEquals(Door.WEST, entrances.lastGood());
        assertEquals(Door.WEST, entrances.choose(NORTH_EXIT, new int[] {0, 5, 0, 0}, 60_000));
        // After the 25-minute rotation a crowd elsewhere is believed first.
        long later = 1_000 + PyramidPlunderEntrances.ROTATION_MS + 1;
        assertEquals(Door.EAST, entrances.choose(NORTH_EXIT, new int[] {0, 5, 0, 0}, later));
        assertEquals(Door.WEST, entrances.choose(NORTH_EXIT, NOBODY, later));
        entrances.reject();
        assertNull(entrances.lastGood());
        assertNotEquals(Door.WEST, entrances.choose(NORTH_EXIT, NOBODY, later));
    }

    @Test void wrongDoorsAreNotRepeatedUntilAllFourFailed() {
        PyramidPlunderEntrances entrances = new PyramidPlunderEntrances();
        Set<Door> tried = EnumSet.noneOf(Door.class);
        for (int i = 0; i < 4; i++) {
            Door door = entrances.choose(NORTH_EXIT, NOBODY, 0);
            assertEquals(true, tried.add(door), "repeated " + door);
            entrances.reject();
        }
        assertEquals(EnumSet.allOf(Door.class), tried);
        assertEquals(Door.NORTH, entrances.choose(NORTH_EXIT, NOBODY, 0));
    }
}
