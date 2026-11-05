package net.runelite.client.plugins.microbot.slayer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;

import java.util.Arrays;
import java.util.Comparator;

@Getter
@RequiredArgsConstructor
public enum SlayerMaster {
    TURAEL("Turael", new WorldPoint(2931, 3536, 0), 1, 1, ""),
    SPRIA("Spria", new WorldPoint(3092, 3267, 0), 1, 1, ""),
    MAZCHNA("Mazchna", new WorldPoint(3510, 3507, 0), 20, 1, "Priest in Peril"),
    VANNAKA("Vannaka", new WorldPoint(3145, 9914, 0), 40, 1, ""),
    CHAELDAR("Chaeldar", new WorldPoint(2445, 4431, 0), 70, 1, "Lost City"),
    NIEVE("Nieve", new WorldPoint(2432, 3423, 0), 85, 1, ""),
    STEVE("Steve", new WorldPoint(2432, 3423, 0), 85, 1, "Monkey Madness II"),
    DURADEL("Duradel", new WorldPoint(2869, 2982, 1), 100, 50, "Shilo Village"),
    KURADAL("Kuradal", new WorldPoint(2869, 2982, 1), 100, 50, "Shilo Village"); // Replaces Duradel after While Guthix Sleeps
    // KONAR not included due to location-specific task complexity

    private final String name;
    private final WorldPoint location;
    private final int combatLevelRequired;
    private final int slayerLevelRequired;
    private final String questRequired;

    public boolean isAvailableAt(int combatLevel, int slayerLevel) {
        return combatLevel >= combatLevelRequired && slayerLevel >= slayerLevelRequired;
    }

    public boolean needsQuest() {
        return !questRequired.isEmpty();
    }

    public static SlayerMaster highestAvailableAt(int combatLevel, int slayerLevel, boolean includeQuestMasters) {
        return Arrays.stream(values())
                .filter(master -> master.isAvailableAt(combatLevel, slayerLevel))
                .filter(master -> includeQuestMasters || !master.needsQuest())
                .max(Comparator.comparingInt(SlayerMaster::getCombatLevelRequired))
                .orElse(TURAEL);
    }

    @Override
    public String toString() {
        return name;
    }
}
