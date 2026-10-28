package net.runelite.client.plugins.microbot.tithefarming;

import net.runelite.client.plugins.microbot.tithefarming.enums.TitheFarmLanes;
import net.runelite.client.plugins.microbot.tithefarming.models.TitheFarmPlant;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class TitheFarmPlantLimits {
    private TitheFarmPlantLimits() {}

    public static int dropRegionX(TitheFarmLanes lane) {
        if (lane == null) {
            return -1;
        }
        switch (lane) {
            case LANE_1_2:
                return 45;
            case LANE_2_3:
                return 35;
            case LANE_3_4:
                return 40;
            case LANE_4_5:
                return 45;
            default:
                return -1;
        }
    }

    public static List<TitheFarmPlant> apply16PlantLimit(List<TitheFarmPlant> plants, TitheFarmLanes lane) {
        int dropX = dropRegionX(lane);
        if (dropX < 0 || plants == null) {
            return plants == null ? new ArrayList<>() : new ArrayList<>(plants);
        }
        return plants.stream()
                .filter(p -> p.regionX != dropX)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
