package net.runelite.client.plugins.microbot.microhunter.scripts;

import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.List;

/** Pure decision helpers kept separate from live client state. */
public final class AutoHunterPlanner {
    private static final int CARNIVOROUS_CHINCHOMPA_ID = 2911;

    public enum TrapState {
        CAUGHT,
        FAILED,
        ACTIVE,
        UNKNOWN
    }

    private AutoHunterPlanner() {
    }

    static boolean shouldBootstrap(int managedTrapCount, int trapLimit) {
        return managedTrapCount < trapLimit;
    }

    static boolean isHuntingAreaOccupied(int nearbyPlayers, int nearbyTraps,
                                         boolean playerPersisted) {
        return (nearbyPlayers > 0 && nearbyTraps > 0)
                || nearbyTraps >= 2
                || playerPersisted;
    }

    public static int normalBoxTrapLimit(int hunterLevel) {
        if (hunterLevel >= 80) return 5;
        if (hunterLevel >= 60) return 4;
        if (hunterLevel >= 40) return 3;
        if (hunterLevel >= 20) return 2;
        return 1;
    }

    public static TrapState classifyActions(String[] actions) {
        if (hasAction(actions, "check")) return TrapState.CAUGHT;
        if (hasAction(actions, "reset")) return TrapState.FAILED;
        if (hasAction(actions, "dismantle")) return TrapState.ACTIVE;
        return TrapState.UNKNOWN;
    }

    public static boolean hasAction(String[] actions, String expected) {
        if (actions == null || expected == null) return false;
        for (String action : actions) {
            if (action != null && action.equalsIgnoreCase(expected)) return true;
        }
        return false;
    }

    public static List<WorldPoint> ring(WorldPoint center) {
        List<WorldPoint> result = new ArrayList<>(8);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) result.add(center.dx(dx).dy(dy));
            }
        }
        return result;
    }

    public static List<WorldPoint> fiveDotLayout(WorldPoint center, int trapLimit) {
        List<WorldPoint> result = new ArrayList<>(Math.min(5, Math.max(0, trapLimit)));
        if (center == null || trapLimit <= 0) return result;

        result.add(center.dx(-1).dy(1));
        if (trapLimit == 1) return result;
        result.add(center.dx(1).dy(1));
        if (trapLimit == 2) return result;
        result.add(center.dx(1).dy(-1));
        if (trapLimit == 3) return result;
        result.add(center.dx(-1).dy(-1));
        if (trapLimit >= 5) result.add(center);
        return result;
    }

    public static List<WorldPoint> placementGrid(WorldPoint center, int radius) {
        List<WorldPoint> result = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                result.add(center.dx(dx).dy(dy));
            }
        }
        return result;
    }

    public static boolean isRedChinchompaTarget(int npcId, String npcName) {
        return npcId == CARNIVOROUS_CHINCHOMPA_ID
                || "Carnivorous chinchompa".equalsIgnoreCase(npcName)
                || "Red chinchompa".equalsIgnoreCase(npcName);
    }

    public static double spawnScore(int appearances, long ageMillis, int distance) {
        double freshness = Math.max(0.0, 1.0 - (ageMillis / 600_000.0));
        return appearances * 100.0 + freshness * 20.0 - distance;
    }
}
