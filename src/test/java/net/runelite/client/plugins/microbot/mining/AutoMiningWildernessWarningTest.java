package net.runelite.client.plugins.microbot.mining;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mining.data.LocationOption;
import net.runelite.client.plugins.microbot.mining.data.Rocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AutoMiningWildernessWarningTest {

    private static final LocationOption LAVA_MAZE = new LocationOption(
            new WorldPoint(3058, 3884, 0), "Lava Maze Runite Mine (Wilderness)", false);

    @Test
    void freeAccountOnFreeWorldIsOnlyToldToTurnOffProgressiveMode() {
        assertEquals(
                "Auto Mining: the nearest place to mine runite rocks on this free world is Lava Maze Runite Mine, in level 46 Wilderness. If you don't want to go there, stop the script, then turn off Progressive mode before you start it again.",
                AutoMiningScript.wildernessWarning(Rocks.RUNITE, LAVA_MAZE, false, false));
    }

    @Test
    void membersAccountOnFreeWorldIsAlsoOfferedAMembersWorld() {
        assertEquals(
                "Auto Mining: the nearest place to mine runite rocks on this free world is Lava Maze Runite Mine, in level 46 Wilderness. If you don't want to go there, stop the script, then log in to a members world or turn off Progressive mode before you start it again.",
                AutoMiningScript.wildernessWarning(Rocks.RUNITE, LAVA_MAZE, false, true));
    }

    @Test
    void membersWorldIsOnlyToldToTurnOffProgressiveMode() {
        assertEquals(
                "Auto Mining: the nearest place to mine runite rocks is Lava Maze Runite Mine, in level 46 Wilderness. If you don't want to go there, stop the script, then turn off Progressive mode before you start it again.",
                AutoMiningScript.wildernessWarning(Rocks.RUNITE, LAVA_MAZE, true, true));
    }

    @Test
    void noWarningOutsideWilderness() {
        LocationOption dwarvenMine = new LocationOption(new WorldPoint(3034, 9822, 0), "Dwarven Mine", false);

        assertNull(AutoMiningScript.wildernessWarning(Rocks.COAL, dwarvenMine, false, false));
        assertNull(AutoMiningScript.wildernessWarning(Rocks.COAL, null, false, false));
    }
}
