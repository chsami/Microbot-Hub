package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.coords.WorldPoint;

import java.util.Comparator;
import java.util.Map;
import java.util.function.Predicate;

/** Pure rules for the maintained Walker's logical journey and nearby-target fallback. */
final class WalkerJourneyPolicy {
    private WalkerJourneyPolicy() { }

    static boolean ownershipIntact(boolean bank, WorldPoint requested, WorldPoint current, String status) {
        if (current != null) return requested == null ? bank : requested.equals(current);
        // A completed route deliberately clears its destination. A cleared active land
        // route is not silently reclaimed; the bank coordinator may be between legs.
        return "ARRIVED".equals(status) || bank && ("PLANNING".equals(status)
                || "PREVIEW".equals(status) || "WALKING".equals(status));
    }

    static boolean arrivedOwned(boolean bank, WorldPoint requested, WorldPoint current,
                                String status, WorldPoint player) {
        return "ARRIVED".equals(status) && ownershipIntact(bank, requested, current, status)
                && requested != null && player != null && player.getPlane() == requested.getPlane()
                && player.distanceTo2D(requested) <= 2;
    }

    static boolean retryTransition(String status, String failure) {
        return "BLOCKED".equals(status) && "NONE".equals(failure);
    }

    static WorldPoint dockApproach(WorldPoint gangplank, Map<WorldPoint, Integer> reachable,
                                   Predicate<WorldPoint> standable) {
        if (gangplank == null || reachable == null) return null;
        return reachable.keySet().stream()
                .filter(point -> point.getPlane() == gangplank.getPlane())
                .filter(point -> point.distanceTo2D(gangplank) >= 1 && point.distanceTo2D(gangplank) <= 4)
                .filter(standable)
                .min(Comparator.comparingInt((WorldPoint point) -> point.distanceTo2D(gangplank))
                        .thenComparingInt(point -> reachable.get(point))
                        .thenComparingInt(WorldPoint::getX).thenComparingInt(WorldPoint::getY))
                .orElse(null);
    }
}
