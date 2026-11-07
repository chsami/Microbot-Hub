package net.runelite.client.plugins.microbot.scurrius;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeTileFinderTest {
    private final WorldArea room = new WorldArea(new WorldPoint(3290, 9860, 0), 17, 17);
    private final WorldArea boss = new WorldArea(new WorldPoint(3296, 9866, 0), 5, 5); // x 3296..3300, y 9866..9870
    private final Predicate<WorldPoint> everywhere = p -> true;

    private static WorldPoint wp(int x, int y) {
        return new WorldPoint(x, y, 0);
    }

    private static int chebyshev(WorldPoint a, WorldPoint b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
    }

    @Test
    void neverReturnsARockTile() {
        WorldPoint player = wp(3295, 9868); // adjacent to the boss, west side
        Set<WorldPoint> rocks = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                rocks.add(wp(player.getX() + dx, player.getY() + dy));
            }
        }
        WorldPoint safe = SafeTileFinder.find(player, rocks, everywhere, room, boss, 1);
        assertNotNull(safe);
        assertFalse(rocks.contains(safe));
    }

    @Test
    void prefersAMeleeAdjacentTileOverACloserOneOutOfRange() {
        WorldPoint player = wp(3295, 9868); // west of the boss, adjacent
        Set<WorldPoint> rocks = Set.of(player, wp(3295, 9867), wp(3295, 9869));
        WorldPoint safe = SafeTileFinder.find(player, rocks, everywhere, room, boss, 1);
        assertTrue(SafeTileFinder.canAttackFrom(safe, boss, 1), "got " + safe);
        assertEquals(3295, safe.getX());
    }

    @Test
    void rangedPlayerKeepsDistanceWithinWeaponRange() {
        WorldPoint player = wp(3292, 9868);
        WorldPoint safe = SafeTileFinder.find(player, Set.of(player), everywhere, room, boss, 7);
        assertEquals(1, chebyshev(safe, player));
        assertTrue(SafeTileFinder.canAttackFrom(safe, boss, 7));
    }

    @Test
    void fallsBackToTheNearestTileWhenNoneCanAttack() {
        WorldPoint player = wp(3290, 9860); // room corner, far from the boss
        WorldPoint safe = SafeTileFinder.find(player, Set.of(player), everywhere, room, boss, 1);
        assertNotNull(safe);
        assertFalse(SafeTileFinder.canAttackFrom(safe, boss, 1));
        assertEquals(1, chebyshev(safe, player));
    }

    @Test
    void wallsAreNotWalkedOnAndTheRoomEdgeIsRespected() {
        WorldPoint player = wp(3290, 9860);
        Predicate<WorldPoint> noWalkEast = p -> p.getX() <= 3290;
        WorldPoint safe = SafeTileFinder.find(player, Set.of(player), noWalkEast, room, boss, 1);
        assertEquals(wp(3290, 9861), safe);
        assertNull(SafeTileFinder.find(player, Set.of(player), p -> false, room, boss, 1));
    }

    @Test
    void neverStandsInsideTheBossFootprint() {
        WorldPoint player = wp(3295, 9868);
        WorldPoint safe = SafeTileFinder.find(player, Set.of(player), everywhere, room, boss, 3);
        assertFalse(boss.contains(safe));
    }

    @Test
    void meleeRangeExcludesDiagonalCornerTiles() {
        assertTrue(SafeTileFinder.canAttackFrom(wp(3295, 9868), boss, 1));
        assertFalse(SafeTileFinder.canAttackFrom(wp(3295, 9865), boss, 1));
        assertTrue(SafeTileFinder.canAttackFrom(wp(3295, 9865), boss, 2));
        assertFalse(SafeTileFinder.canAttackFrom(wp(3298, 9868), boss, 5)); // inside the footprint
    }

    @Test
    void unknownBossMeansAnyTileIsFine() {
        WorldPoint player = wp(3300, 9860);
        WorldPoint safe = SafeTileFinder.find(player, Set.of(player), everywhere, room, null, 1);
        assertEquals(1, chebyshev(safe, player));
    }
}
