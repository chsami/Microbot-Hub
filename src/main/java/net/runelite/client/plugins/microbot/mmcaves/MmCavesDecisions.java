package net.runelite.client.plugins.microbot.mmcaves;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mmcaves.enums.Mode;
import net.runelite.client.plugins.microbot.mmcaves.enums.LightSources;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

final class MmCavesDecisions {
    private MmCavesDecisions() {
    }

    static boolean hasLightSource(Predicate<String> inInventory, Predicate<String> equipped) {
        return Arrays.stream(LightSources.values())
                .map(LightSources::getItemName)
                .anyMatch(name -> inInventory.test(name) || equipped.test(name));
    }

    static boolean shouldHeal(double healthPercentage) {
        return healthPercentage <= 50;
    }

    static StackCounts countStack(List<WorldPoint> monkeyTiles, WorldPoint fightingTile, int nearbyRadius) {
        List<WorldPoint> nearby = new java.util.ArrayList<>();
        for (WorldPoint tile : monkeyTiles) {
            if (tile != null && tile.getPlane() == fightingTile.getPlane()
                    && tile.distanceTo(fightingTile) <= nearbyRadius) {
                nearby.add(tile);
            }
        }
        int bestStack = 0;
        for (WorldPoint center : nearby) {
            int inChinArea = 0;
            for (WorldPoint tile : nearby) {
                if (tile.distanceTo(center) <= 1) inChinArea++;
            }
            bestStack = Math.max(bestStack, inChinArea);
        }
        return new StackCounts(bestStack, nearby.size() - bestStack);
    }

    static final class StackCounts {
        final int stacked;
        final int outside;

        StackCounts(int stacked, int outside) {
            this.stacked = stacked;
            this.outside = outside;
        }

        boolean ready(int minimumStackSize, int maximumOutsideStack) {
            return stacked >= minimumStackSize && outside <= maximumOutsideStack;
        }
    }

    static boolean magicSuppliesMissing(boolean hasRequiredRunes, boolean onAncientSpellbook) {
        return !hasRequiredRunes || !onAncientSpellbook;
    }

    static boolean useDirectMagicCast(Mode mode, boolean autoCastEnabled) {
        return mode == Mode.MAGIC && !autoCastEnabled;
    }

    static int selectUncheckedWorld(IntSupplier worldSupplier, Set<Integer> checkedWorlds, int maxAttempts) {
        for (int attempts = 0; attempts < maxAttempts; attempts++) {
            int candidate = worldSupplier.getAsInt();
            if (candidate > 0 && !checkedWorlds.contains(candidate)) {
                return candidate;
            }
        }
        return -1;
    }
}
