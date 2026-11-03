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
    void warnsAboutWildernessMineOnFreeWorld() {
        assertEquals(
                "Auto Mining: the nearest place to mine runite rocks on this free world is Lava Maze Runite Mine (Wilderness), in level 46 Wilderness. Hop to a members world or turn off Progressive mode if you don't want to go there.",
                AutoMiningScript.wildernessWarning(Rocks.RUNITE, LAVA_MAZE, false));
    }

    @Test
    void warnsAboutWildernessMineOnMembersWorld() {
        assertEquals(
                "Auto Mining: the nearest place to mine runite rocks is Lava Maze Runite Mine (Wilderness), in level 46 Wilderness. Turn off Progressive mode if you don't want to go there.",
                AutoMiningScript.wildernessWarning(Rocks.RUNITE, LAVA_MAZE, true));
    }

    @Test
    void noWarningOutsideWilderness() {
        LocationOption dwarvenMine = new LocationOption(new WorldPoint(3034, 9822, 0), "Dwarven Mine", false);

        assertNull(AutoMiningScript.wildernessWarning(Rocks.COAL, dwarvenMine, false));
        assertNull(AutoMiningScript.wildernessWarning(Rocks.COAL, null, false));
    }
}
