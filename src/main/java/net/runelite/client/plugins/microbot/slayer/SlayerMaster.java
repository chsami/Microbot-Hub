package net.runelite.client.plugins.microbot.slayer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;

@Getter
@RequiredArgsConstructor
public enum SlayerMaster {
    TURAEL("Turael", new WorldPoint(2931, 3536, 0), 1),
    SPRIA("Spria", new WorldPoint(2907, 3324, 0), 1),
    MAZCHNA("Mazchna", new WorldPoint(3510, 3507, 0), 20),
    VANNAKA("Vannaka", new WorldPoint(3145, 9914, 0), 40),
    CHAELDAR("Chaeldar", new WorldPoint(2445, 4431, 0), 70),
    NIEVE("Nieve", new WorldPoint(2432, 3423, 0), 85),
    STEVE("Steve", new WorldPoint(2432, 3423, 0), 85),
    DURADEL("Duradel", new WorldPoint(2869, 2982, 1), 100),
    KURADAL("Kuradal", new WorldPoint(2869, 2982, 1), 100); // Replaces Duradel after While Guthix Sleeps
    // KONAR not included due to location-specific task complexity

    private final String name;
    private final WorldPoint location;
    private final int combatLevelRequired;

    @Override
    public String toString() {
        return name;
    }
}
