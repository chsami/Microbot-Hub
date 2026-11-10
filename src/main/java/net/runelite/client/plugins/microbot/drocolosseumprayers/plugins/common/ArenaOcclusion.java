package net.runelite.client.plugins.microbot.drocolosseumprayers.plugins.common;

import java.awt.Shape;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

/** Arena-only optional wire hint. All scene/model reads happen on the existing snapshot client thread. */
final class ArenaOcclusion
{
    private static final int MARGIN = 6;
    private final Client client;
    private final BiFunction<Integer, Integer, int[]> scene;
    private final BiFunction<LocalPoint, Integer, Point> project;

    ArenaOcclusion(Client client, TemplateTiles tiles)
    {
        this(client, tiles::scene, (local, plane) -> Perspective.localToCanvas(client, local, plane));
    }

    // Pure mapping/projection seams keep offline geometry tests independent of the renderer.
    ArenaOcclusion(Client client, BiFunction<Integer, Integer, int[]> scene,
        BiFunction<LocalPoint, Integer, Point> project)
    {
        this.client = client;
        this.scene = scene;
        this.project = project;
    }

    byte[] mask(int[] rectangle, Set<Integer> ids)
    {
        int width = rectangle[2] - rectangle[0] + 1;
        int height = rectangle[3] - rectangle[1] + 1;
        byte[] bits = new byte[(width * height + 7) / 8];
        WorldView view = client.getTopLevelWorldView();
        if (ids.isEmpty() || view == null || view.getScene() == null) return bits;
        int plane = view.getPlane();
        Tile[][][] all = view.getScene().getTiles();
        if (all == null || plane < 0 || plane >= all.length || all[plane] == null) return bits;
        Tile[][] grid = all[plane];

        // Screen coordinates are template tiles, not a contiguous scene rectangle in rotated instances.
        Set<Long> nearby = new HashSet<>();
        for (int tx = rectangle[0] - MARGIN; tx <= rectangle[2] + MARGIN; tx++)
        {
            for (int ty = rectangle[1] - MARGIN; ty <= rectangle[3] + MARGIN; ty++)
            {
                int[] tile = scene.apply(tx, ty);
                if (valid(grid, tile)) nearby.add(key(tile[0], tile[1]));
            }
        }
        double cameraX = client.getCameraX(), cameraY = client.getCameraY();
        List<Occluder> hulls = new ArrayList<>();
        Set<TileObject> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (long key : nearby)
        {
            Tile tile = grid[(int) (key >> 32)][(int) key];
            if (tile == null) continue;
            collect(tile.getWallObject(), view, plane, ids, nearby, seen, hulls, cameraX, cameraY);
            collect(tile.getDecorativeObject(), view, plane, ids, nearby, seen, hulls, cameraX, cameraY);
            GameObject[] objects = tile.getGameObjects();
            if (objects != null) for (GameObject object : objects)
                collect(object, view, plane, ids, nearby, seen, hulls, cameraX, cameraY);
        }
        if (hulls.isEmpty()) return bits;
        for (int i = 0; i < width * height; i++)
        {
            int[] tile = scene.apply(rectangle[0] + i % width, rectangle[1] + i / width);
            if (!valid(grid, tile)) continue;
            LocalPoint local = LocalPoint.fromScene(tile[0], tile[1], view);
            Point canvas = project.apply(local, plane);
            if (canvas == null || canvas.getX() < 0 || canvas.getY() < 0
                || canvas.getX() >= client.getViewportWidth() || canvas.getY() >= client.getViewportHeight()) continue;
            double distance = squared(local.getX() - cameraX, local.getY() - cameraY);
            for (Occluder hull : hulls)
            {
                if (hull.distance < distance && hull.shape.contains(canvas.getX(), canvas.getY()))
                {
                    bits[i >> 3] |= (byte) (1 << (i & 7));
                    break;
                }
            }
        }
        return bits;
    }

    private static void collect(TileObject object, WorldView view, int plane, Set<Integer> ids,
        Set<Long> nearby, Set<TileObject> seen, List<Occluder> hulls, double cameraX, double cameraY)
    {
        if (object == null || !seen.add(object) || !ids.contains(object.getId())
            || object.getPlane() != plane || object.getWorldView() != view) return;
        LocalPoint local = object.getLocalLocation();
        if (local == null || local.getWorldView() != view.getId()
            || !nearby.contains(key(local.getSceneX(), local.getSceneY()))) return;
        // Both object centres and camera X/Y are local 1/128-tile units, never template/world tiles.
        double distance = squared(local.getX() - cameraX, local.getY() - cameraY);
        if (object instanceof GameObject)
            add(hulls, ((GameObject) object).getConvexHull(), distance);
        else if (object instanceof WallObject)
        {
            add(hulls, ((WallObject) object).getConvexHull(), distance);
            add(hulls, ((WallObject) object).getConvexHull2(), distance);
        }
        else if (object instanceof DecorativeObject)
        {
            add(hulls, ((DecorativeObject) object).getConvexHull(), distance);
            add(hulls, ((DecorativeObject) object).getConvexHull2(), distance);
        }
    }

    private static void add(List<Occluder> hulls, Shape shape, double distance)
    {
        if (shape != null) hulls.add(new Occluder(shape, distance));
    }

    private static boolean valid(Tile[][] grid, int[] tile)
    {
        return tile != null && tile.length >= 2 && tile[0] >= 0 && tile[0] < grid.length
            && grid[tile[0]] != null && tile[1] >= 0 && tile[1] < grid[tile[0]].length;
    }

    private static double squared(double x, double y) { return x * x + y * y; }
    private static long key(int x, int y) { return ((long) x << 32) | (y & 0xffffffffL); }

    private static final class Occluder
    {
        private final Shape shape;
        private final double distance;
        private Occluder(Shape shape, double distance) { this.shape = shape; this.distance = distance; }
    }
}
