package net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TempleDataTest
{
    private static WorldPoint at(int x, int y, int plane)
    {
        return new WorldPoint(x, y, plane);
    }

    @Test
    public void scrollArrivalIncludesItsEdgesAndExcludesOutside()
    {
        assertTrue(TempleData.atScrollArrival(at(4060, 4554, 0)));
        assertTrue(TempleData.atScrollArrival(at(4066, 4560, 0)));
        assertTrue(TempleData.atScrollArrival(TempleData.SCROLL_ARRIVAL));
        assertFalse(TempleData.atScrollArrival(at(4059, 4557, 0)));
        assertFalse(TempleData.atScrollArrival(at(4067, 4557, 0)));
        assertFalse(TempleData.atScrollArrival(at(4063, 4553, 0)));
        assertFalse(TempleData.atScrollArrival(at(4063, 4561, 0)));
        assertFalse(TempleData.atScrollArrival(at(4063, 4557, 1)));
        assertFalse(TempleData.atScrollArrival(null));
    }

    @Test
    public void theLedgeAndTopAreTheSameFootprintOnPlanesOneAndTwo()
    {
        assertTrue(TempleData.onChasmLedge(at(4055, 4544, 1), 1));
        assertTrue(TempleData.onChasmLedge(at(4070, 4560, 1), 1));
        assertTrue(TempleData.onChasmLedge(at(4062, 4550, 2), 2));
        assertFalse(TempleData.onChasmLedge(at(4062, 4550, 2), 1));
        assertFalse(TempleData.onChasmLedge(at(4062, 4550, 1), 2));
        assertFalse(TempleData.onChasmLedge(at(4054, 4550, 1), 1));
        assertFalse(TempleData.onChasmLedge(at(4071, 4550, 1), 1));
        assertFalse(TempleData.onChasmLedge(at(4062, 4543, 1), 1));
        assertFalse(TempleData.onChasmLedge(at(4062, 4561, 1), 1));
        assertFalse(TempleData.onChasmLedge(null, 1));
    }

    @Test
    public void theTempleCoversTheDemonRoomsOnTheGroundPlaneOnly()
    {
        assertTrue(TempleData.inTemple(at(4000, 4340, 0)));
        assertTrue(TempleData.inTemple(at(4170, 4475, 0)));
        assertTrue(TempleData.inTemple(TempleData.TEMPLE_ENTRY));
        for (DemonRoom room : DemonRoom.values())
        {
            assertTrue(TempleData.inTemple(room.getCenter()), room.getDisplayName());
        }
        assertFalse(TempleData.inTemple(at(3999, 4400, 0)));
        assertFalse(TempleData.inTemple(at(4171, 4400, 0)));
        assertFalse(TempleData.inTemple(at(4100, 4339, 0)));
        assertFalse(TempleData.inTemple(at(4100, 4476, 0)));
        assertFalse(TempleData.inTemple(at(4100, 4400, 1)));
        assertFalse(TempleData.inTemple(null));
    }

    @Test
    public void feroxEnclaveHasItsOwnBounds()
    {
        assertTrue(TempleData.atFerox(at(3120, 3620, 0)));
        assertTrue(TempleData.atFerox(at(3160, 3645, 0)));
        assertTrue(TempleData.atFerox(TempleData.FEROX_ARRIVAL));
        assertTrue(TempleData.atFerox(TempleData.FEROX_BANK));
        assertTrue(TempleData.atFerox(TempleData.FEROX_POOL));
        assertFalse(TempleData.atFerox(at(3119, 3630, 0)));
        assertFalse(TempleData.atFerox(at(3161, 3630, 0)));
        assertFalse(TempleData.atFerox(at(3130, 3619, 0)));
        assertFalse(TempleData.atFerox(at(3130, 3646, 0)));
        assertFalse(TempleData.atFerox(at(3130, 3630, 1)));
        assertFalse(TempleData.atFerox(null));
        assertFalse(TempleData.atFerox(TempleData.SCROLL_ARRIVAL));
    }

    @Test
    public void theRouteAreasDoNotOverlapTheTemple()
    {
        assertFalse(TempleData.inTemple(TempleData.SCROLL_ARRIVAL));
        assertTrue(TempleData.inTemple(TempleData.TEMPLE_ENTRY));
        assertFalse(TempleData.atScrollArrival(TempleData.TEMPLE_ENTRY));
    }

    @Test
    public void bothDemonFormsAreRecognised()
    {
        assertTrue(TempleData.DEMON_IDS.contains(13599));
        assertTrue(TempleData.DEMON_IDS.contains(13600));
        assertFalse(TempleData.DEMON_IDS.contains(1));
    }
}
