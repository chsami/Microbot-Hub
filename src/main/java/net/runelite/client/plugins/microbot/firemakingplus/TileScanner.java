// Adapted from the leaguesfiremaking plugin (TileScanner).
package net.runelite.client.plugins.microbot.firemakingplus;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds horizontal lines of open tiles for line firemaking and detects fire tiles.
 */
@Slf4j
public class TileScanner {

    private static final int FIRE_ID = ObjectID.FIRE;
    private static final int FIRE_ID_ALT = 49927;

    private enum TileState {
        OPEN,
        FIRE,
        BLOCKED
    }

    private static TileState classifyTile(WorldPoint point, Set<WorldPoint> fireTiles, Set<WorldPoint> objectTiles) {
        if (fireTiles.contains(point)) return TileState.FIRE;
        if (objectTiles.contains(point)) return TileState.BLOCKED;
        if (!Rs2Tile.isWalkable(point)) return TileState.BLOCKED;
        return TileState.OPEN;
    }

    public static List<FireLine> findFireLines(WorldPoint center, int radius) {
        final Set<WorldPoint> fireTiles = new HashSet<>();
        final Set<WorldPoint> objectTiles = new HashSet<>();

        // Consume the live scene stream on the client thread; the grid loop below is safe off-thread
        // because classifyTile's Rs2Tile.isWalkable self-guards to the client thread per tile.
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
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
            return Boolean.TRUE;
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

        // Score lines: balance length vs proximity to start position.
        // A nearby shorter line beats a far-away longer one.
        lines.sort(Comparator.comparingDouble((FireLine l) -> {
            int distance = center.distanceTo(l.getEastEnd());
            return -(l.getLength() - distance * 0.5);
        }));

        return lines;
    }

    public static boolean hasFire(WorldPoint point) {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getRs2TileObjectCache().getStream()
                        .anyMatch(obj -> obj.getWorldLocation().equals(point)
                                && (obj.getId() == FIRE_ID || obj.getId() == FIRE_ID_ALT))
        ).orElse(false);
    }
}
