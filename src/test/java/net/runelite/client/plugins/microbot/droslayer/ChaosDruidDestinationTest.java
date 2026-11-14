package net.runelite.client.plugins.microbot.droslayer;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public class ChaosDruidDestinationTest {
    @Test public void selectedEldersUseTempleAndNativeCannonSpot() {
        var normal = DroSlayerWildernessTasks.forAssignedTask("Chaos druids");
        var selected = DroSlayerWildernessTasks.forChaosDruidPreference(normal,true);
        assertEquals("Chaos Temple - Elder Chaos druids",selected.getLocationName());
        assertEquals(new WorldPoint(3237,3622,0),selected.getCannonSpot());
        assertTrue(selected.isCannonAllowed());
        assertTrue(selected.getTargetNames().contains("Elder Chaos druid"));
        assertEquals("Wilderness Edgeville Dungeon - Chaos druids", normal.getLocationName());
    }
    @Test public void defaultUsesOrdinaryDungeonWithCannonAndLeavesOtherTasksUnchanged() {
        assertFalse(new DroSlayerConfig() {}.wildernessElderChaosDruids());
        var normal = DroSlayerWildernessTasks.forAssignedTask("Chaos druids");
        assertSame(normal,DroSlayerWildernessTasks.forChaosDruidPreference(normal,false));
        assertTrue(normal.isCannonAllowed());
        assertEquals(new WorldPoint(3111,9933,0),normal.getDestination());
        assertEquals(new WorldPoint(3111,9933,0),normal.getCannonSpot());
        assertTrue(normal.getCannonSpotEvidence().contains("User-verified"));
        var other = DroSlayerWildernessTasks.forAssignedTask("Greater demons");
        assertSame(other,DroSlayerWildernessTasks.forChaosDruidPreference(other,true));
    }
}
