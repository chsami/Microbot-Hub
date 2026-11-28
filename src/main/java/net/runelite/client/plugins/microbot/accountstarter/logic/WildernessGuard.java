package net.runelite.client.plugins.microbot.accountstarter.logic;

import net.runelite.api.coords.WorldPoint;

import java.util.List;
import java.util.function.Predicate;

public final class WildernessGuard {
    private WildernessGuard() {
    }

    public static boolean destinationSafe(WorldPoint p, Predicate<WorldPoint> isWild) {
        return p != null && !isWild.test(p);
    }

    public static boolean routeSafe(List<WorldPoint> path, Predicate<WorldPoint> isWild) {
        return path != null && path.stream().allMatch(p -> destinationSafe(p, isWild));
    }
}
