package net.runelite.client.plugins.microbot.drodriftnet;

import net.runelite.api.coords.WorldPoint;

import java.util.Collection;

/** Canonical (template) coordinates only; scoring is a heuristic, not a collision pathfinder. */
final class DriftNetTargeting
{
    private DriftNetTargeting() { }

    static boolean acceptsFish(DriftNetStatus status)
    {
        return status == DriftNetStatus.SET || status == DriftNetStatus.CATCH;
    }

    static boolean shouldEnableRun(boolean enabled, int energy)
    {
        return !enabled && energy > 30;
    }

    static boolean between(WorldPoint point, WorldPoint fish, WorldPoint net)
    {
        if (point == null || point.getPlane() != fish.getPlane()) return false;
        int dx = net.getX() - fish.getX(), dy = net.getY() - fish.getY();
        int px = point.getX() - fish.getX(), py = point.getY() - fish.getY();
        int lengthSquared = dx * dx + dy * dy;
        int dot = px * dx + py * dy;
        int cross = px * dy - py * dx;
        return dot > 0 && dot < lengthSquared && cross * cross <= lengthSquared;
    }

    static int alley(WorldPoint point, Collection<WorldPoint> acceptingTiles)
    {
        int best = Integer.MAX_VALUE, alley = -1;
        for (WorldPoint tile : acceptingTiles) {
            int distance = point.distanceTo(tile);
            if (distance < best) {
                best = distance;
                alley = tile.getY() >= 10297 ? 0 : 1;
            }
        }
        return alley;
    }

    static boolean nearbyOnSide(WorldPoint fish, WorldPoint player, Collection<WorldPoint> acceptingTiles)
    {
        return fish != null && fish.getX() >= 3740 && fish.distanceTo(player) <= 6
                && alley(fish, acceptingTiles) == alley(player, acceptingTiles);
    }

    static int score(WorldPoint fish, WorldPoint player, Collection<WorldPoint> acceptingTiles,
                     Collection<WorldPoint> shoals)
    {
        if (fish == null || player == null || fish.getPlane() != player.getPlane()
                || fish.getRegionID() != 15008 || fish.getX() < 3740)
            return Integer.MAX_VALUE;
        int best = Integer.MAX_VALUE;
        for (WorldPoint net : acceptingTiles)
        {
            if (net.getPlane() != fish.getPlane()) continue;
            int score = 4 * fish.distanceTo(net) + 3 * fish.distanceTo(player);
            if (between(player, fish, net)) score += 20;
            for (WorldPoint other : shoals)
                if (!fish.equals(other) && between(other, fish, net)) score += 4;
            best = Math.min(best, score);
        }
        return best;
    }
}
