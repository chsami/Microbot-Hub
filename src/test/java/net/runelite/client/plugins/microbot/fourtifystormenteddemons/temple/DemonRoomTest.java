package net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DemonRoomTest
{
    @Test
    public void everyRoomContainsItsCentre()
    {
        for (DemonRoom room : DemonRoom.values())
        {
            assertTrue(room.contains(room.getCenter()), room.getDisplayName());
        }
    }

    @Test
    public void plinthChamberBoundsIncludeTheEdgeMargin()
    {
        DemonRoom room = DemonRoom.PLINTH_CHAMBER;
        assertTrue(room.contains(new WorldPoint(4107, 4353, 0)));
        assertTrue(room.contains(new WorldPoint(4159, 4409, 0)));
        assertFalse(room.contains(new WorldPoint(4106, 4380, 0)));
        assertFalse(room.contains(new WorldPoint(4160, 4380, 0)));
        assertFalse(room.contains(new WorldPoint(4130, 4352, 0)));
        assertFalse(room.contains(new WorldPoint(4130, 4410, 0)));
    }

    @Test
    public void aFireBombStepFromTheSpawnAreaEdgeStaysInTheRoom()
    {
        assertTrue(DemonRoom.PLINTH_CHAMBER.contains(new WorldPoint(4109, 4385, 0)));
        assertTrue(DemonRoom.PLINTH_CHAMBER.contains(new WorldPoint(4108, 4385, 0)));
    }

    @Test
    public void westRoomsBoundsIncludeTheEdgeMargin()
    {
        assertTrue(DemonRoom.WEST_NORTH.contains(new WorldPoint(4027, 4438, 0)));
        assertTrue(DemonRoom.WEST_NORTH.contains(new WorldPoint(4049, 4460, 0)));
        assertFalse(DemonRoom.WEST_NORTH.contains(new WorldPoint(4050, 4449, 0)));
        assertTrue(DemonRoom.WEST_SOUTH.contains(new WorldPoint(4029, 4408, 0)));
        assertTrue(DemonRoom.WEST_SOUTH.contains(new WorldPoint(4051, 4430, 0)));
        assertFalse(DemonRoom.WEST_SOUTH.contains(new WorldPoint(4040, 4431, 0)));
    }

    @Test
    public void theWestRoomsDoNotOverlap()
    {
        for (int y = 4400; y <= 4470; y++)
        {
            assertFalse(DemonRoom.WEST_NORTH.contains(new WorldPoint(4040, y, 0))
                && DemonRoom.WEST_SOUTH.contains(new WorldPoint(4040, y, 0)), "y " + y);
        }
    }

    @Test
    public void otherPlanesAndMissingPointsAreOutside()
    {
        assertFalse(DemonRoom.PLINTH_CHAMBER.contains(new WorldPoint(4133, 4382, 1)));
        assertFalse(DemonRoom.PLINTH_CHAMBER.contains(null));
    }

    @Test
    public void aCentreBelongsToNoOtherRoom()
    {
        for (DemonRoom room : DemonRoom.values())
        {
            for (DemonRoom other : DemonRoom.values())
            {
                assertEquals(room == other, other.contains(room.getCenter()));
            }
        }
    }

    @Test
    public void roomsPrintTheirDisplayName()
    {
        assertEquals("Plinth chamber", DemonRoom.PLINTH_CHAMBER.toString());
        assertEquals("West room, north", DemonRoom.WEST_NORTH.getDisplayName());
    }
}
