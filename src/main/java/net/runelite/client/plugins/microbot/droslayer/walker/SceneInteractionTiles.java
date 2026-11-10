package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;

import java.util.*;

/**
 * Scene interaction-tile helper for Efficient Walker.
 *
 * <p>This helper enumerates tiles from which a wall or game object can be
 * interacted with while respecting the live scene collision map, bridge-plane settings and
 * door/gate occupancy.</p>
 */
final class SceneInteractionTiles
{
    private static final int[] DELTA_X = {-1, 1, 0, 0};
    private static final int[] DELTA_Y = {0, 0, -1, 1};
    private static final int[] DIRECTION_MASKS = {128, 8, 32, 2};

    private static final int BLOCKED_TILE_MASK = 19136768;
    private static final int BRIDGE_BIT = 4;

    private SceneInteractionTiles()
    {
        // Utility class.
    }

    /** Returns the square neighborhood around {@code origin}, preserving v1.11.2 ordering. */
    static List<WorldPoint> tilesAround(WorldPoint origin, int radius)
    {
        List<WorldPoint> points = new ArrayList<>();
        points.add(origin);

        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dy = -radius; dy <= radius; dy++)
            {
                if (dx != 0 || dy != 0)
                {
                    points.add(origin.dx(dx).dy(dy));
                }
            }
        }

        return points;
    }

    static List<WorldPoint> findApproachTiles(Client client, TileObject object)
    {
        WorldView worldView = client.getTopLevelWorldView();
        WorldPoint worldPoint = object.getWorldLocation();

        if (object.getWorldView() != worldView || worldPoint == null)
        {
            return Collections.emptyList();
        }

        if (object instanceof WallObject)
        {
            return findApproachTiles(client, worldPoint, object);
        }

        Point min;
        if (object instanceof GameObject)
        {
            min = ((GameObject) object).getSceneMinLocation();
        }
        else
        {
            min = new Point(worldPoint.getX() - worldView.getBaseX(), worldPoint.getY() - worldView.getBaseY());
        }

        Point max = object instanceof GameObject
            ? ((GameObject) object).getSceneMaxLocation()
            : min;

        if (min == null
            || max == null
            || min.getX() < 0
            || min.getY() < 0
            || max.getX() < min.getX()
            || max.getY() < min.getY()
            || max.getX() >= worldView.getSizeX()
            || max.getY() >= worldView.getSizeY())
        {
            return Collections.emptyList();
        }

        Set<WorldPoint> points = new LinkedHashSet<>();
        for (int sceneX = min.getX(); sceneX <= max.getX(); sceneX++)
        {
            for (int sceneY = min.getY(); sceneY <= max.getY(); sceneY++)
            {
                if (sceneX != min.getX()
                    && sceneX != max.getX()
                    && sceneY != min.getY()
                    && sceneY != max.getY())
                {
                    continue;
                }

                points.addAll(findApproachTiles(
                    client,
                    new WorldPoint(worldView.getBaseX() + sceneX, worldView.getBaseY() + sceneY, worldPoint.getPlane()),
                    object));
            }
        }

        return new ArrayList<>(points);
    }

    static List<WorldPoint> findApproachTiles(Client client, WorldPoint worldPoint)
    {
        return findApproachTiles(client, worldPoint, null);
    }

    private static List<WorldPoint> findApproachTiles(Client client, WorldPoint worldPoint, TileObject object)
    {
        WorldView worldView = client.getTopLevelWorldView();
        CollisionData[] collisionMaps = worldView.getCollisionMaps();
        byte[][][] tileSettings = worldView.getTileSettings();
        Scene scene = worldView.getScene();
        Tile[][][] sceneTiles = scene == null ? null : scene.getTiles();
        int plane = worldPoint.getPlane();

        if (collisionMaps == null
            || tileSettings == null
            || sceneTiles == null
            || plane < 0
            || plane >= collisionMaps.length
            || collisionMaps[plane] == null
            || plane >= tileSettings.length
            || plane >= sceneTiles.length)
        {
            return Collections.emptyList();
        }

        int sceneX = worldPoint.getX() - worldView.getBaseX();
        int sceneY = worldPoint.getY() - worldView.getBaseY();
        int[][] flags = collisionMaps[plane].getFlags();

        if (!isInsideGrid(flags, sceneX, sceneY)
            || sceneX >= tileSettings[plane].length
            || sceneY >= tileSettings[plane][sceneX].length)
        {
            return Collections.emptyList();
        }

        Set<Integer> wallEdges = new LinkedHashSet<>();
        boolean isWall = object instanceof WallObject;

        if (isWall)
        {
            Tile tile = getTileOrNull(sceneTiles[plane], sceneX, sceneY);
            if (tile == null || tile.getWallObject() != object)
            {
                return Collections.emptyList();
            }

            WallObject wall = (WallObject) object;
            LocalCollisionGrid.forEachDirectionalEdge(flags.length, flags[0].length, sceneX, sceneY, wall.getOrientationA(), wallEdges::add);
            LocalCollisionGrid.forEachDirectionalEdge(flags.length, flags[0].length, sceneX, sceneY, wall.getOrientationB(), wallEdges::add);

            if (wallEdges.isEmpty())
            {
                return Collections.emptyList();
            }
        }

        List<WorldPoint> result = new ArrayList<>();

        if (isTileWalkable(flags[sceneX][sceneY])
            && (!isWall || !isApproachBlockedByOtherObject(client, getTileOrNull(sceneTiles[plane], sceneX, sceneY), object)))
        {
            result.add(worldPoint);
        }

        for (int direction = 0; direction < DELTA_X.length; direction++)
        {
            int nextX = sceneX + DELTA_X[direction];
            int nextY = sceneY + DELTA_Y[direction];

            if (!isInsideGrid(flags, nextX, nextY)
                || nextX >= tileSettings[plane].length
                || nextY >= tileSettings[plane][nextX].length)
            {
                continue;
            }

            if (isWall)
            {
                if (!isTileWalkable(flags[nextX][nextY]))
                {
                    continue;
                }

                int edge = LocalCollisionGrid.cardinalEdgeIndex(flags.length, flags[0].length, sceneX, sceneY, nextX, nextY);
                if (!wallEdges.contains(edge)
                    && (((flags[sceneX][sceneY] & DIRECTION_MASKS[direction]) != 0)
                    || ((flags[nextX][nextY] & DIRECTION_MASKS[direction ^ 1]) != 0)))
                {
                    continue;
                }
            }
            else if (!canApproachAcrossEdge(
                flags[sceneX][sceneY],
                flags[nextX][nextY],
                tileSettings[plane][sceneX][sceneY],
                tileSettings[plane][nextX][nextY],
                direction))
            {
                continue;
            }

            if (isApproachBlockedByOtherObject(client, getTileOrNull(sceneTiles[plane], sceneX, sceneY), object)
                || isApproachBlockedByOtherObject(client, getTileOrNull(sceneTiles[plane], nextX, nextY), object))
            {
                continue;
            }

            result.add(worldPoint.dx(DELTA_X[direction]).dy(DELTA_Y[direction]));
        }

        if (isWall)
        {
            return result;
        }

        // Diagonals are accepted only when both adjacent cardinal approach tiles already passed.
        for (int horizontalDirection = 0; horizontalDirection < 2; horizontalDirection++)
        {
            for (int verticalDirection = 2; verticalDirection < 4; verticalDirection++)
            {
                int nextX = sceneX + DELTA_X[horizontalDirection];
                int nextY = sceneY + DELTA_Y[verticalDirection];

                int sourceDiagonalMask;
                int destinationDiagonalMask;
                if (horizontalDirection == 0)
                {
                    sourceDiagonalMask = verticalDirection == 2 ? 64 : 1;
                    destinationDiagonalMask = verticalDirection == 2 ? 4 : 16;
                }
                else
                {
                    sourceDiagonalMask = verticalDirection == 2 ? 16 : 4;
                    destinationDiagonalMask = verticalDirection == 2 ? 1 : 64;
                }

                if (!result.contains(worldPoint.dx(DELTA_X[horizontalDirection]))
                    || !result.contains(worldPoint.dy(DELTA_Y[verticalDirection]))
                    || !isInsideGrid(flags, nextX, nextY)
                    || nextX >= tileSettings[plane].length
                    || nextY >= tileSettings[plane][nextX].length
                    || (flags[sceneX][sceneY] & sourceDiagonalMask) != 0
                    || (flags[nextX][nextY] & destinationDiagonalMask) != 0
                    || !canApproachAcrossEdge(
                        flags[nextX][sceneY],
                        flags[nextX][nextY],
                        tileSettings[plane][nextX][sceneY],
                        tileSettings[plane][nextX][nextY],
                        verticalDirection)
                    || !canApproachAcrossEdge(
                        flags[sceneX][nextY],
                        flags[nextX][nextY],
                        tileSettings[plane][sceneX][nextY],
                        tileSettings[plane][nextX][nextY],
                        horizontalDirection)
                    || isApproachBlockedByOtherObject(client, getTileOrNull(sceneTiles[plane], nextX, nextY), object))
                {
                    continue;
                }

                result.add(worldPoint.dx(DELTA_X[horizontalDirection]).dy(DELTA_Y[verticalDirection]));
            }
        }

        return result;
    }

    static boolean canApproachAcrossEdge(int sourceFlags, int destinationFlags, int sourceSettings, int destinationSettings, int direction)
    {
        return isTileWalkable(destinationFlags)
            && (sourceFlags & DIRECTION_MASKS[direction]) == 0
            && (destinationFlags & DIRECTION_MASKS[direction ^ 1]) == 0
            && (sourceSettings & BRIDGE_BIT) == (destinationSettings & BRIDGE_BIT);
    }

    private static boolean isTileWalkable(int flags)
    {
        return (flags & BLOCKED_TILE_MASK) == 0;
    }

    private static boolean isInsideGrid(int[][] flags, int x, int y)
    {
        return flags != null
            && x >= 0
            && y >= 0
            && x < flags.length
            && y < flags[x].length;
    }

    private static Tile getTileOrNull(Tile[][] tiles, int x, int y)
    {
        if (x < 0 || y < 0 || x >= tiles.length || y >= tiles[x].length)
        {
            return null;
        }

        return tiles[x][y];
    }

    /**
     * Returns whether another scene object makes the tile unsuitable as an approach tile.
     * v1.11.2 deliberately treats non-target walls, unresolved object definitions and
     * door/gate game objects as blockers; other ordinary game objects do not block here.
     */
    private static boolean isApproachBlockedByOtherObject(Client client, Tile tile, TileObject target)
    {
        if (tile == null)
        {
            return true;
        }

        if (tile.getWallObject() != null && tile.getWallObject() != target)
        {
            return true;
        }

        for (GameObject gameObject : tile.getGameObjects())
        {
            if (gameObject == null || gameObject == target)
            {
                continue;
            }

            ObjectComposition composition = client.getObjectDefinition(gameObject.getId());
            if (composition != null && composition.getImpostorIds() != null)
            {
                composition = composition.getImpostor();
                if (composition == null)
                {
                    continue;
                }
            }

            if (composition == null || composition.getName() == null)
            {
                return true;
            }

            String name = composition.getName().toLowerCase(Locale.ROOT);
            if (name.contains("door") || name.contains("gate"))
            {
                return true;
            }
        }

        return false;
    }
}
