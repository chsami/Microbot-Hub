package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;

import java.util.List;
import java.util.Collection;

/** Shared by the planner and the execution guard. Safe enclaves remain safe. */
public final class WildernessTravelPolicy
{
    private WildernessTravelPolicy() { }

    private static final ThreadLocal<PlanningScope> PLANNING = new ThreadLocal<>();

    /** A request may enter danger only when its destination actually requires it. */
    public static boolean requiresWilderness(WorldPoint point) {
        return isBlocked(point, false) || point != null && point.getPlane() == 0
            && point.getX() >= 2250 && point.getX() < 2315
            && point.getY() >= 4650 && point.getY() < 4720; // KBD lair, reached through Wilderness.
    }

    private static boolean explicitDestination(Collection<WorldPoint> targets) {
        return targets != null && !targets.isEmpty() && targets.stream().allMatch(WildernessTravelPolicy::requiresWilderness);
    }

    /** Called inside the planner on its own worker; no mutable account preferences change. */
    public static Object beginPlanning(Collection<WorldPoint> starts, Collection<WorldPoint> targets, boolean allowed) {
        return beginPlanning(starts, targets, allowed, null, null);
    }

    public static Object beginPlanning(Collection<WorldPoint> starts, Collection<WorldPoint> targets, boolean allowed,
        WorldPoint actualOrigin, WorldPoint actualTarget) {
        PlanningScope previous = PLANNING.get();
        int escapeLevel = starts == null || starts.isEmpty() || starts.stream().anyMatch(p -> !isBlocked(p, false))
            ? 0 : starts.stream().mapToInt(WildernessTravelPolicy::dangerLevel).max().orElse(0);
        // When a journey began deeper in the Wilderness, an intermediate replan
        // must not shrink its permitted escape level to the current route suffix.
        // A safe planner start still revokes this allowance (no re-entry).
        if (actualOrigin != null) escapeLevel = escapeLevel == 0 ? 0 : dangerLevel(actualOrigin);
        boolean dungeonExit = escapeLevel > 0 && (actualOrigin != null ? deepDungeonExitOrigin(actualOrigin)
            : starts != null && starts.stream().allMatch(WildernessTravelPolicy::deepDungeonExitOrigin));
        PLANNING.set(new PlanningScope(actualTarget != null ? requiresWilderness(actualTarget)
            : explicitDestination(targets), allowed, escapeLevel, dungeonExit));
        return previous;
    }

    public static void endPlanning(Object previous) {
        if (previous == null) PLANNING.remove(); else PLANNING.set((PlanningScope) previous);
    }

    /** Used by the engine's node admission, including every automatic replan. */
    public static boolean plannerBlocked(WorldPoint point, boolean allowed) {
        PlanningScope scope = PLANNING.get();
        if (scope == null) return isBlocked(point, allowed);
        if (!isBlocked(point, false)) return false;
        return scope.explicit ? !scope.allowed
            : escapeBlocked(point, scope.escapeLevel, scope.dungeonExit);
    }

    public static Collection<WorldPoint> planningStarts(Collection<WorldPoint> starts) {
        if (starts == null) return null;
        return starts.stream().filter(point -> !plannerBlocked(point, false)).collect(java.util.stream.Collectors.toList());
    }

    public static boolean plannedPathAllowed(List<WorldPoint> path) {
        PlanningScope scope = PLANNING.get();
        return scope == null || path == null || path.isEmpty()
            || (scope.explicit && scope.allowed || scope.escapeLevel > 0 || !containsWilderness(path))
                && routeAllowed(path, scope.explicit && scope.allowed, scope.escapeLevel, scope.dungeonExit);
    }

    /** A safe journey may leave an existing dangerous area, but cannot enter or re-enter one. */
    static boolean routeAllowed(List<WorldPoint> path, WorldPoint destination, boolean allowed) {
        if (requiresWilderness(destination) && !allowed && containsWilderness(path)) return false;
        return routeAllowed(path, allowed && requiresWilderness(destination));
    }

    private static boolean routeAllowed(List<WorldPoint> path, boolean explicit) {
        if (path == null || path.isEmpty()) return true;
        return routeAllowed(path, explicit,
            isBlocked(path.get(0), false) ? dangerLevel(path.get(0)) : 0,
            deepDungeonExitOrigin(path.get(0)));
    }

    /** Execution guard for the original logical request, not a shrinking engine route suffix. */
    static boolean routeAllowed(List<WorldPoint> path, WorldPoint destination, boolean allowed,
                                WorldPoint originalOrigin, boolean reachedSafety) {
        if (requiresWilderness(destination))
            return allowed || !containsWilderness(path);
        if (originalOrigin == null || !isBlocked(originalOrigin, false))
            return routeAllowed(path, false);
        return routeAllowed(path, false, reachedSafety ? 0 : dangerLevel(originalOrigin),
            !reachedSafety && deepDungeonExitOrigin(originalOrigin));
    }

    private static boolean routeAllowed(List<WorldPoint> path, boolean explicit,
                                        int escapeLevel, boolean dungeonExit) {
        if (path == null || path.isEmpty() || explicit) return true;
        boolean safe = escapeLevel == 0;
        for (WorldPoint point : path) {
            if (!isBlocked(point, false)) safe = true;
            else if (safe || escapeBlocked(point, escapeLevel, dungeonExit)) return false;
        }
        return true;
    }

    private static boolean escapeBlocked(WorldPoint point, int escapeLevel, boolean dungeonExit) {
        return escapeLevel == 0 || dangerLevel(point) > escapeLevel
            && !(dungeonExit && deepDungeonExitStep(point));
    }

    private static boolean deepDungeonExitOrigin(WorldPoint point) {
        return point != null && point.getPlane() == 0 && (point.getX() >= 3008 && point.getX() < 3072
            && point.getY() >= 10304 && point.getY() < 10368 || deepDungeonExitStep(point));
    }

    /** The bundled collision map requires northward steps to the stairs and around their surface wall. */
    private static boolean deepDungeonExitStep(WorldPoint point) {
        return point != null && point.getPlane() == 0
            && (point.getX() >= 3040 && point.getX() <= 3045
                && point.getY() >= 10319 && point.getY() <= 10323
                || point.getX() >= 3014 && point.getX() <= 3047
                && point.getY() >= 3920 && point.getY() <= 3929);
    }

    private static final class PlanningScope {
        final boolean explicit, allowed, dungeonExit;
        final int escapeLevel;
        PlanningScope(boolean explicit, boolean allowed, int escapeLevel, boolean dungeonExit) {
            this.explicit = explicit; this.allowed = allowed; this.escapeLevel = escapeLevel; this.dungeonExit = dungeonExit;
        }
    }

    public static boolean isBlocked(WorldPoint point, boolean explicitlyAllowed)
    {
        // The Slayer Tower basement shares the wilderness dungeon Y band, but is safe.
        boolean slayerTowerBasement = point != null && point.getPlane() == 3
            && point.getX() >= 3392 && point.getX() < 3456
            && point.getY() >= 9920 && point.getY() < 9984;
        // The ditch lands at Y=3523, two tiles below the PvP utility's above-ground cuboid.
        // Reject crossing itself, before a subsequent walking click reaches the dangerous band.
        boolean northOfDitch = point != null && point.getX() >= 2944 && point.getX() <= 3391
            && point.getY() >= 3523 && point.getY() < 3525;
        return !explicitlyAllowed && point != null && !slayerTowerBasement
            && (northOfDitch || Rs2Pvp.getWildernessLevelFrom(point) > 0);
    }

    private static int dangerLevel(WorldPoint point) {
        return point == null || !isBlocked(point, false) ? 0 : Math.max(1, Rs2Pvp.getWildernessLevelFrom(point));
    }

    /** All canoe trips need opt-in; only unsafe endpoints also need Wilderness permission. */
    public static boolean canoeAllowed(WorldPoint origin, WorldPoint destination, boolean useCanoes, boolean allowWilderness)
    {
        return useCanoes && (allowWilderness || (!isBlocked(origin, false) && !isBlocked(destination, false)));
    }

    static boolean isCanoe(String action, String target)
    {
        return "Canoe Station".equalsIgnoreCase(target) || "Canoe".equalsIgnoreCase(target)
            || "Paddle Canoe".equalsIgnoreCase(action);
    }

    static net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2RouteResult plan(
        net.runelite.client.plugins.microbot.util.walker.Rs2RouteRequest request, boolean explicitlyAllowed)
    {
        var result = net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2PathApi.plan(request);
        boolean explicit = explicitlyAllowed && explicitDestination(request.getTargets());
        return result != null && !routeAllowed(result.getPath(), explicit) ? null : result;
    }

    static boolean containsWilderness(List<WorldPoint> route)
    {
        if (route == null) return false;
        for (WorldPoint point : route)
            if (isBlocked(point, false)) return true;
        return false;
    }
}
