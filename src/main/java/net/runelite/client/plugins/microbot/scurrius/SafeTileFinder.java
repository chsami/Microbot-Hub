package net.runelite.client.plugins.microbot.scurrius;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

import java.util.Comparator;
import java.util.Set;
import java.util.function.Predicate;

/** Picks where to step when standing under falling rocks. Pure, so it is unit tested. */
public final class SafeTileFinder {
    public static final int MAX_RADIUS = 5;

    private SafeTileFinder() {
    }

    /**
     * @param player      the tile to leave
     * @param rocks       dangerous tiles
     * @param walkable    whether a tile can be stood on
     * @param room        the fight room; the result stays inside it
     * @param bossArea    Scurrius's footprint (null if unknown); never stood on
     * @param attackRange tiles the player can attack from (1 = melee, adjacent)
     * @return the best tile within {@link #MAX_RADIUS}, or null. Tiles from which Scurrius can still be attacked come
     * first, then the shortest distance, then a fixed order so the result is deterministic.
     */
    public static WorldPoint find(WorldPoint player, Set<WorldPoint> rocks, Predicate<WorldPoint> walkable,
                                  WorldArea room, WorldArea bossArea, int attackRange) {
        WorldPoint best = null;
        Comparator<WorldPoint> order = Comparator
                .comparing((WorldPoint p) -> !canAttackFrom(p, bossArea, attackRange))
                .thenComparingInt(p -> chebyshev(player, p))
                .thenComparingInt(p -> Math.abs(player.getX() - p.getX()) + Math.abs(player.getY() - p.getY()))
                .thenComparingInt(WorldPoint::getX)
                .thenComparingInt(WorldPoint::getY);
        for (int dx = -MAX_RADIUS; dx <= MAX_RADIUS; dx++) {
            for (int dy = -MAX_RADIUS; dy <= MAX_RADIUS; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                WorldPoint tile = new WorldPoint(player.getX() + dx, player.getY() + dy, player.getPlane());
                if (rocks.contains(tile) || !room.contains(tile) || !walkable.test(tile)) {
                    continue;
                }
                if (bossArea != null && bossArea.contains(tile)) {
                    continue;
                }
                if (best == null || order.compare(tile, best) < 0) {
                    best = tile;
                }
            }
        }
        return best;
    }

    /** Whether a tile can attack the boss: adjacent (not diagonal-only) for range 1, else within range of the footprint. */
    public static boolean canAttackFrom(WorldPoint tile, WorldArea bossArea, int attackRange) {
        if (bossArea == null) {
            return true;
        }
        int minX = bossArea.getX();
        int maxX = bossArea.getX() + bossArea.getWidth() - 1;
        int minY = bossArea.getY();
        int maxY = bossArea.getY() + bossArea.getHeight() - 1;
        int gapX = Math.max(0, Math.max(minX - tile.getX(), tile.getX() - maxX));
        int gapY = Math.max(0, Math.max(minY - tile.getY(), tile.getY() - maxY));
        if (gapX == 0 && gapY == 0) {
            return false; // inside the footprint
        }
        if (attackRange <= 1) {
            return gapX + gapY == 1;
        }
        return Math.max(gapX, gapY) <= attackRange;
    }

    private static int chebyshev(WorldPoint a, WorldPoint b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
    }
}
