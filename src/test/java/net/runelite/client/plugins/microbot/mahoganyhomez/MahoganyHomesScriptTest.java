package net.runelite.client.plugins.microbot.mahoganyhomez;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MahoganyHomesScriptTest {

    @Test
    void closestContractLocationIsNullWithoutPlayerLocation() {
        assertNull(MahoganyHomesScript.getClosestContractLocation(null));
    }

    @Test
    void closestContractLocationPicksNearestOffice() {
        assertEquals(ContractLocation.MAHOGANY_HOMES_VARROCK.getLocation(),
                MahoganyHomesScript.getClosestContractLocation(new WorldPoint(3213, 3424, 0)));
        assertEquals(ContractLocation.MAHOGANY_HOMES_FALADOR.getLocation(),
                MahoganyHomesScript.getClosestContractLocation(new WorldPoint(2965, 3380, 0)));
        assertEquals(ContractLocation.MAHOGANY_HOMES_ARDOUGNE.getLocation(),
                MahoganyHomesScript.getClosestContractLocation(new WorldPoint(2662, 3305, 0)));
        assertEquals(ContractLocation.MAHOGANY_HOMES_HOSIDIUS.getLocation(),
                MahoganyHomesScript.getClosestContractLocation(new WorldPoint(1760, 3600, 0)));
    }

    @Test
    void isAwayFromHomeIsFalseWithoutHomeOrPlayerLocation() {
        MahoganyHomesScript script = scriptWithPlugin();
        assertFalse(script.isAwayFromHome(null, new WorldPoint(3213, 3424, 0)));
        assertFalse(script.isAwayFromHome(Home.JESS, null));
        assertFalse(script.isAwayFromHome(null, null));
    }

    @Test
    void isAwayFromHomeComparesPlayerLocationToHomeArea() {
        MahoganyHomesScript script = scriptWithPlugin();
        assertTrue(script.isAwayFromHome(Home.JESS, new WorldPoint(3213, 3424, 0)));
        assertFalse(script.isAwayFromHome(Home.JESS, new WorldPoint(2615, 3293, 1)));
    }

    @Test
    void fixableObjectIdsSkipsHotspotsBeyondHomeObjectCount() {
        Set<Integer> ids = MahoganyHomesScript.getFixableObjectIds(Arrays.asList(Hotspot.values()), HotspotObjects.ROSS);
        assertEquals(HotspotObjects.ROSS.objects.length, ids.size());
        assertTrue(ids.contains(HotspotObjects.ROSS.objects[6].getObjectId()));
    }

    @Test
    void fixableObjectIdsSkipsHotspotsBeyondSixObjectHome() {
        Set<Integer> ids = MahoganyHomesScript.getFixableObjectIds(
                Arrays.asList(Hotspot.MAHOGANY_HOMES_HOTSPOT_1, Hotspot.MAHOGANY_HOMES_HOTSPOT_7, Hotspot.MAHOGANY_HOMES_HOTSPOT_8),
                HotspotObjects.SARAH);
        assertEquals(Set.of(HotspotObjects.SARAH.objects[0].getObjectId()), ids);
    }

    @Test
    void fixableObjectIdsMapsAllHotspotsForEightObjectHome() {
        Set<Integer> ids = MahoganyHomesScript.getFixableObjectIds(Arrays.asList(Hotspot.values()), HotspotObjects.JESS);
        assertEquals(8, ids.size());
        assertTrue(ids.contains(HotspotObjects.JESS.objects[7].getObjectId()));
    }

    private static MahoganyHomesScript scriptWithPlugin() {
        MahoganyHomesScript script = new MahoganyHomesScript();
        script.plugin = new MahoganyHomesPlugin();
        return script;
    }
}
