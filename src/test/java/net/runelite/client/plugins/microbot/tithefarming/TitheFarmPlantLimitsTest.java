package net.runelite.client.plugins.microbot.tithefarming;

import net.runelite.client.plugins.microbot.tithefarming.enums.TitheFarmLanes;
import net.runelite.client.plugins.microbot.tithefarming.models.TitheFarmPlant;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitheFarmPlantLimitsTest {

    @Test
    void dropRegionX_matchesSpec() {
        assertEquals(45, TitheFarmPlantLimits.dropRegionX(TitheFarmLanes.LANE_1_2));
        assertEquals(35, TitheFarmPlantLimits.dropRegionX(TitheFarmLanes.LANE_2_3));
        assertEquals(40, TitheFarmPlantLimits.dropRegionX(TitheFarmLanes.LANE_3_4));
        assertEquals(45, TitheFarmPlantLimits.dropRegionX(TitheFarmLanes.LANE_4_5));
        assertEquals(-1, TitheFarmPlantLimits.dropRegionX(TitheFarmLanes.Randomize));
    }

    @Test
    void apply16PlantLimit_lane12_dropsOuterColumnTo16() {
        List<TitheFarmPlant> plants = lane12FullList();
        assertEquals(20, plants.size());

        List<TitheFarmPlant> limited = TitheFarmPlantLimits.apply16PlantLimit(plants, TitheFarmLanes.LANE_1_2);

        assertEquals(16, limited.size());
        assertTrue(limited.stream().noneMatch(p -> p.regionX == 45));
        assertEquals(20, plants.size()); // original unchanged
    }

    @Test
    void apply16PlantLimit_otherLanes_yield16() {
        assertEquals(16, TitheFarmPlantLimits.apply16PlantLimit(lane23FullList(), TitheFarmLanes.LANE_2_3).size());
        assertEquals(16, TitheFarmPlantLimits.apply16PlantLimit(lane34FullList(), TitheFarmLanes.LANE_3_4).size());
        assertEquals(16, TitheFarmPlantLimits.apply16PlantLimit(lane45FullList(), TitheFarmLanes.LANE_4_5).size());
    }

    private static List<TitheFarmPlant> lane12FullList() {
        return new ArrayList<>(Arrays.asList(
                new TitheFarmPlant(35, 25, 15),
                new TitheFarmPlant(40, 25, 16),
                new TitheFarmPlant(35, 28, 17),
                new TitheFarmPlant(40, 28, 18),
                new TitheFarmPlant(35, 31, 19),
                new TitheFarmPlant(40, 31, 20),
                new TitheFarmPlant(35, 34, 1),
                new TitheFarmPlant(40, 34, 2),
                new TitheFarmPlant(35, 40, 3),
                new TitheFarmPlant(40, 40, 4),
                new TitheFarmPlant(35, 43, 5),
                new TitheFarmPlant(40, 43, 6),
                new TitheFarmPlant(35, 46, 7),
                new TitheFarmPlant(40, 46, 8),
                new TitheFarmPlant(35, 49, 9),
                new TitheFarmPlant(40, 49, 10),
                new TitheFarmPlant(45, 49, 11),
                new TitheFarmPlant(45, 46, 12),
                new TitheFarmPlant(45, 43, 13),
                new TitheFarmPlant(45, 40, 14)));
    }

    private static List<TitheFarmPlant> lane23FullList() {
        return new ArrayList<>(Arrays.asList(
                new TitheFarmPlant(35, 31, -2),
                new TitheFarmPlant(35, 28, -1),
                new TitheFarmPlant(35, 25, 0),
                new TitheFarmPlant(40, 25, 1),
                new TitheFarmPlant(45, 25, 2),
                new TitheFarmPlant(40, 28, 3),
                new TitheFarmPlant(45, 28, 4),
                new TitheFarmPlant(40, 31, 5),
                new TitheFarmPlant(45, 31, 6),
                new TitheFarmPlant(40, 34, 7),
                new TitheFarmPlant(45, 34, 8),
                new TitheFarmPlant(40, 40, 9),
                new TitheFarmPlant(45, 40, 10),
                new TitheFarmPlant(40, 43, 11),
                new TitheFarmPlant(45, 43, 12),
                new TitheFarmPlant(40, 46, 13),
                new TitheFarmPlant(45, 46, 14),
                new TitheFarmPlant(40, 49, 15),
                new TitheFarmPlant(45, 49, 16)));
    }

    private static List<TitheFarmPlant> lane34FullList() {
        return new ArrayList<>(Arrays.asList(
                new TitheFarmPlant(40, 31, -2),
                new TitheFarmPlant(40, 28, -1),
                new TitheFarmPlant(40, 25, 0),
                new TitheFarmPlant(45, 25, 1),
                new TitheFarmPlant(50, 25, 2),
                new TitheFarmPlant(45, 28, 3),
                new TitheFarmPlant(50, 28, 4),
                new TitheFarmPlant(45, 31, 5),
                new TitheFarmPlant(50, 31, 6),
                new TitheFarmPlant(45, 34, 7),
                new TitheFarmPlant(50, 34, 8),
                new TitheFarmPlant(45, 40, 9),
                new TitheFarmPlant(50, 40, 10),
                new TitheFarmPlant(45, 43, 11),
                new TitheFarmPlant(50, 43, 12),
                new TitheFarmPlant(45, 46, 13),
                new TitheFarmPlant(50, 46, 14),
                new TitheFarmPlant(45, 49, 15),
                new TitheFarmPlant(50, 49, 16)));
    }

    private static List<TitheFarmPlant> lane45FullList() {
        return new ArrayList<>(Arrays.asList(
                new TitheFarmPlant(45, 31, 0),
                new TitheFarmPlant(45, 28, 1),
                new TitheFarmPlant(45, 25, 2),
                new TitheFarmPlant(50, 25, 3),
                new TitheFarmPlant(55, 25, 4),
                new TitheFarmPlant(50, 28, 5),
                new TitheFarmPlant(55, 28, 6),
                new TitheFarmPlant(50, 31, 7),
                new TitheFarmPlant(55, 31, 8),
                new TitheFarmPlant(50, 34, 9),
                new TitheFarmPlant(55, 34, 10),
                new TitheFarmPlant(50, 40, 11),
                new TitheFarmPlant(55, 40, 12),
                new TitheFarmPlant(50, 43, 13),
                new TitheFarmPlant(55, 43, 14),
                new TitheFarmPlant(50, 46, 15),
                new TitheFarmPlant(55, 46, 16),
                new TitheFarmPlant(50, 49, 17),
                new TitheFarmPlant(55, 49, 18)));
    }
}
