package net.runelite.client.plugins.microbot.leaguesfiremaking;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class TileScanner {

    private static final int FIRE_ID = 26185;
    private static final int FIRE_ID_ALT = 49927;

    public enum TileState {
        OPEN,
        FIRE,
        BLOCKED
    }

    public static TileState classifyTile(WorldPoint point, Set<WorldPoint> fireTiles, Set<WorldPoint> objectTiles) {
        if (fireTiles.contains(point)) return TileState.FIRE;
        if (objectTiles.contains(point)) return TileState.BLOCKED;
        if (!Rs2Tile.isWalkable(point)) return TileState.BLOCKED;
        return TileState.OPEN;
    }

    public static List<FireLine> findFireLines(WorldPoint center, int radius) {
        Set<WorldPoint> fireTiles = new HashSet<>();
        Set<WorldPoint> objectTiles = new HashSet<>();

        Microbot.getRs2TileObjectCache().getStream()
                .filter(obj -> obj.getWorldLocation().distanceTo(center) <= radius)
                .forEach(obj -> {
                    int id = obj.getId();
                    WorldPoint loc = obj.getWorldLocation();
                    if (id == FIRE_ID || id == FIRE_ID_ALT) {
                        fireTiles.add(loc);
                    } else {
                        objectTiles.add(loc);
                    }
                });

        List<FireLine> lines = new ArrayList<>();
        int plane = center.getPlane();

        for (int y = center.getY() - radius; y <= center.getY() + radius; y++) {
            int runStartX = -1;
            int runLength = 0;

            for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
                WorldPoint point = new WorldPoint(x, y, plane);
                TileState state = classifyTile(point, fireTiles, objectTiles);

                if (state == TileState.OPEN) {
                    if (runStartX == -1) {
                        runStartX = x;
                    }
                    runLength++;
                } else {
                    if (runLength >= 5) {
                        lines.add(new FireLine(
                                new WorldPoint(runStartX, y, plane),
                                new WorldPoint(runStartX + runLength - 1, y, plane),
                                runLength
                        ));
                    }
                    runStartX = -1;
                    runLength = 0;
                }
            }
            if (runLength >= 5) {
                lines.add(new FireLine(
                        new WorldPoint(runStartX, y, plane),
                        new WorldPoint(runStartX + runLength - 1, y, plane),
                        runLength
                ));
            }
        }

        // Score lines: balance length vs proximity to start position
        // A nearby shorter line beats a far-away longer one
        lines.sort(Comparator.comparingDouble((FireLine l) -> {
            int distance = center.distanceTo(l.getEastEnd());
            // Penalize distance heavily: each tile away reduces effective score
            return -(l.getLength() - distance * 0.5);
        }));

        return lines;
    }

    public static FireLine findBestLine(WorldPoint center, int radius) {
        List<FireLine> lines = findFireLines(center, radius);
        return lines.isEmpty() ? null : lines.get(0);
    }

    public static boolean hasFire(WorldPoint point) {
        return Microbot.getRs2TileObjectCache().getStream()
                .anyMatch(obj -> obj.getWorldLocation().equals(point)
                        && (obj.getId() == FIRE_ID || obj.getId() == FIRE_ID_ALT));
    }

    public static Set<WorldPoint> buildFireSet(WorldPoint center, int radius) {
        Set<WorldPoint> fireTiles = new HashSet<>();
        Microbot.getRs2TileObjectCache().getStream()
                .filter(obj -> obj.getWorldLocation().distanceTo(center) <= radius)
                .filter(obj -> obj.getId() == FIRE_ID || obj.getId() == FIRE_ID_ALT)
                .forEach(obj -> fireTiles.add(obj.getWorldLocation()));
        return fireTiles;
    }

    public static Set<WorldPoint> buildObjectSet(WorldPoint center, int radius) {
        Set<WorldPoint> objectTiles = new HashSet<>();
        Microbot.getRs2TileObjectCache().getStream()
                .filter(obj -> obj.getWorldLocation().distanceTo(center) <= radius)
                .filter(obj -> obj.getId() != FIRE_ID && obj.getId() != FIRE_ID_ALT)
                .forEach(obj -> objectTiles.add(obj.getWorldLocation()));
        return objectTiles;
    }
}
