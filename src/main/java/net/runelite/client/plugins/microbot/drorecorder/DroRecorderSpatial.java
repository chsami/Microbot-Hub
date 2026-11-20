package net.runelite.client.plugins.microbot.drorecorder;

import net.runelite.api.*;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Spatial capture/analysis for DroRecorder.
 *
 * The recorder deliberately keeps two spatial layers:
 *  1) a dense local collision window around the player, and
 *  2) a scene-wide snapshot containing collision RLE, objects and all loaded NPCs.
 *
 * NPC path analysis uses RuneLite's WorldArea collision checks. It intentionally
 * ignores actor-vs-actor blocking; that limitation is written into each result.
 */
final class DroRecorderSpatial
{
    private static final int[][] DIRECTIONS =
    {
        {-1, -1}, {-1, 0}, {-1, 1},
        {0, -1},           {0, 1},
        {1, -1},  {1, 0},  {1, 1}
    };

    private final Client client;
    private final DroRecorderConfig config;
    private final Map<String, Map<String, Object>> pathCache = new HashMap<>();
    private String cachedSceneKey;
    private final Map<TileObject, Integer> dynamicObjects = new WeakHashMap<>();

    DroRecorderSpatial(Client client, DroRecorderConfig config)
    {
        this.client = client;
        this.config = config;
    }

    String sceneKey()
    {
        Player player = client.getLocalPlayer();
        if (player == null || player.getWorldView() == null)
        {
            return "no-scene";
        }

        WorldView wv = player.getWorldView();
        return wv.getId()
            + ":" + wv.getBaseX()
            + ":" + wv.getBaseY()
            + ":" + wv.getPlane()
            + ":" + wv.getSizeX()
            + "x" + wv.getSizeY()
            + ":" + (wv.isInstance() ? Arrays.deepHashCode(wv.getInstanceTemplateChunks()) : 0);
    }

    Map<String, Object> sceneMetadata()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        Player player = client.getLocalPlayer();
        if (player == null || player.getWorldView() == null)
        {
            return result;
        }

        WorldView wv = player.getWorldView();
        Scene scene = wv.getScene();
        result.put("sceneKey", sceneKey());
        result.put("worldViewId", wv.getId());
        result.put("topLevel", wv.isTopLevel());
        result.put("baseX", wv.getBaseX());
        result.put("baseY", wv.getBaseY());
        result.put("plane", player.getWorldLocation().getPlane());
        result.put("sizeX", wv.getSizeX());
        result.put("sizeY", wv.getSizeY());
        result.put("instance", wv.isInstance());
        result.put("mapRegions", wv.getMapRegions() == null ? null : wv.getMapRegions().clone());
        if (scene != null)
        {
            result.put("drawDistance", scene.getDrawDistance());
        }
        return result;
    }

    void enrichNpcRecord(Map<String, Object> record, NPC npc, @Nullable Player player)
    {
        if (record == null || npc == null || player == null)
        {
            return;
        }

        WorldArea npcArea = npc.getWorldArea();
        WorldArea playerArea = player.getWorldArea();
        if (npcArea == null || playerArea == null)
        {
            return;
        }

        int directAreaDistance = npcArea.distanceTo(playerArea);
        record.put("areaDistanceToPlayer", directAreaDistance);
        record.put("inMeleeDistance", npcArea.isInMeleeDistance(playerArea));
        record.put("worldArea", worldAreaRecord(npcArea));

        if (config.recordLineOfSight())
        {
            Boolean npcToPlayer = safeLineOfSight(npc, player);
            Boolean playerToNpc = safeLineOfSight(player, npc);
            record.put("lineOfSightNpcToPlayer", npcToPlayer);
            record.put("lineOfSightPlayerToNpc", playerToNpc);
        }

        if (config.recordPathAnalysis())
        {
            record.put("pathToPlayer", pathToPlayer(npc, player));
        }
    }

    Map<String, Object> localCollisionWindow(int radius)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        Player player = client.getLocalPlayer();
        if (player == null || player.getLocalLocation() == null || player.getWorldView() == null)
        {
            return result;
        }

        WorldView wv = player.getWorldView();
        CollisionData[] maps = wv.getCollisionMaps();
        int plane = player.getWorldLocation().getPlane();
        if (maps == null || plane < 0 || plane >= maps.length || maps[plane] == null)
        {
            return result;
        }

        int[][] flags = maps[plane].getFlags();
        int centerX = player.getLocalLocation().getSceneX();
        int centerY = player.getLocalLocation().getSceneY();
        result.put("radius", radius);
        result.put("centerSceneX", centerX);
        result.put("centerSceneY", centerY);
        result.put("centerWorld", worldPointRecord(player.getWorldLocation()));
        result.put("plane", plane);

        List<Map<String, Object>> tiles = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dy = -radius; dy <= radius; dy++)
            {
                int sx = centerX + dx;
                int sy = centerY + dy;
                if (!inFlags(flags, sx, sy))
                {
                    continue;
                }

                Map<String, Object> tile = new LinkedHashMap<>();
                tile.put("dx", dx);
                tile.put("dy", dy);
                tile.put("sceneX", sx);
                tile.put("sceneY", sy);
                tile.put("worldX", wv.getBaseX() + sx);
                tile.put("worldY", wv.getBaseY() + sy);
                tile.put("flags", flags[sx][sy]);
                tile.put("tileKey", tileKey(new WorldPoint(wv.getBaseX() + sx, wv.getBaseY() + sy, plane)));
                tiles.add(tile);
            }
        }
        result.put("tiles", tiles);
        return result;
    }

    Map<String, Object> fullSceneSnapshot(String reason, List<Map<String, Object>> npcSnapshot, boolean includeCollision)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        dynamicObjects.clear();
        result.put("reason", reason);
        result.put("metadata", sceneMetadata());
        Player player = client.getLocalPlayer();
        if (player != null && player.getWorldView() != null && player.getWorldView().isInstance())
        {
            result.put("instanceTemplateChunks", copyChunks(player.getWorldView().getInstanceTemplateChunks()));
        }
        result.put("npcs", npcSnapshot == null ? new ArrayList<>() : npcSnapshot);

        if (config.recordSceneObjects())
        {
            result.put("objects", sceneObjects());
        }

        if (includeCollision && config.recordSceneCollision())
        {
            result.put("collision", sceneCollisionRle());
        }

        return result;
    }

    private Map<String, Object> pathToPlayer(NPC npc, Player player)
    {
        WorldView wv = npc.getWorldView();
        WorldArea start = npc.getWorldArea();
        WorldArea target = player.getWorldArea();

        String currentSceneKey = sceneKey();
        if (!currentSceneKey.equals(cachedSceneKey))
        {
            pathCache.clear();
            cachedSceneKey = currentSceneKey;
        }

        String cacheKey = pathCacheKey(npc, start, target, currentSceneKey);
        Map<String, Object> cached = pathCache.get(cacheKey);
        if (cached != null)
        {
            Map<String, Object> copy = new LinkedHashMap<>(cached);
            copy.put("cached", true);
            return copy;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ignoresActorBlocking", true);
        result.put("goal", "MELEE_ADJACENT_TO_PLAYER");
        result.put("cached", false);
        if (wv == null || start == null || target == null || player.getWorldView() == null)
        {
            result.put("status", "UNKNOWN");
            return cachePathResult(cacheKey, result);
        }

        if (wv.getId() != player.getWorldView().getId() || start.getPlane() != target.getPlane())
        {
            result.put("status", "DIFFERENT_WORLD_VIEW_OR_PLANE");
            result.put("reachable", false);
            return cachePathResult(cacheKey, result);
        }

        int directDistance = start.distanceTo(target);
        result.put("directAreaDistance", directDistance);
        if (directDistance <= 1)
        {
            result.put("status", "REACHABLE");
            result.put("reachable", true);
            result.put("stepsToMelee", 0);
            result.put("visitedNodes", 1);
            return cachePathResult(cacheKey, result);
        }

        int sizeX = Math.max(1, wv.getSizeX());
        int sizeY = Math.max(1, wv.getSizeY());
        int baseX = wv.getBaseX();
        int baseY = wv.getBaseY();
        int maxX = baseX + sizeX - start.getWidth();
        int maxY = baseY + sizeY - start.getHeight();

        if (start.getX() < baseX || start.getY() < baseY || start.getX() > maxX || start.getY() > maxY)
        {
            result.put("status", "OUTSIDE_SCENE");
            result.put("reachable", false);
            return cachePathResult(cacheKey, result);
        }

        boolean[][] visited = new boolean[sizeX][sizeY];
        ArrayDeque<PathNode> queue = new ArrayDeque<>();
        int startSceneX = start.getX() - baseX;
        int startSceneY = start.getY() - baseY;
        visited[startSceneX][startSceneY] = true;
        queue.add(new PathNode(start.getX(), start.getY(), 0));

        int visitedNodes = 0;
        int nodeLimit = Math.min(config.pathNodeLimit(), sizeX * sizeY);

        while (!queue.isEmpty() && visitedNodes < nodeLimit)
        {
            PathNode node = queue.removeFirst();
            visitedNodes++;
            WorldArea current = new WorldArea(node.x, node.y, start.getWidth(), start.getHeight(), start.getPlane());

            for (int[] direction : DIRECTIONS)
            {
                int dx = direction[0];
                int dy = direction[1];
                int nx = node.x + dx;
                int ny = node.y + dy;
                if (nx < baseX || ny < baseY || nx > maxX || ny > maxY)
                {
                    continue;
                }

                int sceneX = nx - baseX;
                int sceneY = ny - baseY;
                if (visited[sceneX][sceneY])
                {
                    continue;
                }

                boolean canTravel;
                try
                {
                    canTravel = current.canTravelInDirection(wv, dx, dy);
                }
                catch (RuntimeException ex)
                {
                    continue;
                }

                if (!canTravel)
                {
                    continue;
                }

                visited[sceneX][sceneY] = true;
                int nextDistance = node.distance + 1;
                WorldArea next = new WorldArea(nx, ny, start.getWidth(), start.getHeight(), start.getPlane());
                if (next.distanceTo(target) <= 1)
                {
                    result.put("status", "REACHABLE");
                    result.put("reachable", true);
                    result.put("stepsToMelee", nextDistance);
                    result.put("visitedNodes", visitedNodes);
                    return cachePathResult(cacheKey, result);
                }
                queue.addLast(new PathNode(nx, ny, nextDistance));
            }
        }

        result.put("reachable", false);
        result.put("visitedNodes", visitedNodes);
        if (visitedNodes >= nodeLimit && !queue.isEmpty())
        {
            result.put("status", "NODE_LIMIT_REACHED");
        }
        else
        {
            result.put("status", "TRAPPED_OR_UNREACHABLE");
        }
        return cachePathResult(cacheKey, result);
    }

    private String pathCacheKey(NPC npc, @Nullable WorldArea start, @Nullable WorldArea target, String sceneKey)
    {
        StringBuilder key = new StringBuilder(sceneKey)
            .append(':').append(npc.getId())
            .append(':').append(npc.getIndex());
        if (start != null)
        {
            key.append(':').append(start.getX()).append(':').append(start.getY())
                .append(':').append(start.getWidth()).append(':').append(start.getHeight())
                .append(':').append(start.getPlane());
        }
        if (target != null)
        {
            key.append(":p:").append(target.getX()).append(':').append(target.getY())
                .append(':').append(target.getWidth()).append(':').append(target.getHeight())
                .append(':').append(target.getPlane());
        }
        return key.toString();
    }

    private Map<String, Object> cachePathResult(String key, Map<String, Object> result)
    {
        if (pathCache.size() > 1024)
        {
            pathCache.clear();
        }
        pathCache.put(key, new LinkedHashMap<>(result));
        return result;
    }

    @Nullable
    private Boolean safeLineOfSight(Actor from, Actor to)
    {
        if (from == null || to == null || from.getWorldView() == null || from.getWorldArea() == null || to.getWorldArea() == null)
        {
            return null;
        }

        try
        {
            return from.getWorldArea().hasLineOfSightTo(from.getWorldView(), to.getWorldArea());
        }
        catch (RuntimeException ex)
        {
            return null;
        }
    }

    private Map<String, Object> sceneCollisionRle()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        Player player = client.getLocalPlayer();
        if (player == null || player.getWorldView() == null)
        {
            return result;
        }

        WorldView wv = player.getWorldView();
        CollisionData[] maps = wv.getCollisionMaps();
        int plane = player.getWorldLocation().getPlane();
        if (maps == null || plane < 0 || plane >= maps.length || maps[plane] == null)
        {
            return result;
        }

        int[][] flags = maps[plane].getFlags();
        result.put("encoding", "ROW_RLE");
        result.put("runFormat", "[startSceneY,length,flags]");
        result.put("baseX", wv.getBaseX());
        result.put("baseY", wv.getBaseY());
        result.put("plane", plane);
        result.put("sizeX", flags.length);
        result.put("sizeY", flags.length == 0 ? 0 : flags[0].length);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int x = 0; x < flags.length; x++)
        {
            if (flags[x] == null || flags[x].length == 0)
            {
                continue;
            }

            List<int[]> runs = new ArrayList<>();
            int startY = 0;
            int currentFlags = flags[x][0];
            for (int y = 1; y <= flags[x].length; y++)
            {
                if (y == flags[x].length || flags[x][y] != currentFlags)
                {
                    runs.add(new int[]{startY, y - startY, currentFlags});
                    if (y < flags[x].length)
                    {
                        startY = y;
                        currentFlags = flags[x][y];
                    }
                }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sceneX", x);
            row.put("worldX", wv.getBaseX() + x);
            row.put("runs", runs);
            rows.add(row);
        }
        result.put("rows", rows);
        return result;
    }

    private List<Map<String, Object>> sceneObjects()
    {
        List<Map<String, Object>> objects = new ArrayList<>();
        Player player = client.getLocalPlayer();
        if (player == null || player.getWorldView() == null || player.getWorldView().getScene() == null)
        {
            return objects;
        }

        WorldView wv = player.getWorldView();
        Tile[][][] tiles = wv.getScene().getTiles();
        int plane = player.getWorldLocation().getPlane();
        if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null)
        {
            return objects;
        }

        Set<String> seen = new HashSet<>();
        Tile[][] planeTiles = tiles[plane];
        for (int x = 0; x < planeTiles.length; x++)
        {
            if (planeTiles[x] == null)
            {
                continue;
            }
            for (int y = 0; y < planeTiles[x].length; y++)
            {
                Tile tile = planeTiles[x][y];
                if (tile == null)
                {
                    continue;
                }

                addObject(objects, seen, "wall", tile.getWallObject());
                addObject(objects, seen, "decorative", tile.getDecorativeObject());
                addObject(objects, seen, "ground", tile.getGroundObject());

                GameObject[] gameObjects = tile.getGameObjects();
                if (gameObjects != null)
                {
                    for (GameObject object : gameObjects)
                    {
                        addObject(objects, seen, "game", object);
                    }
                }
            }
        }
        return objects;
    }

    private void addObject(List<Map<String, Object>> output, Set<String> seen, String kind, @Nullable TileObject object)
    {
        if (object == null)
        {
            return;
        }

        WorldPoint wp = object.getWorldLocation();
        String key = kind + ':' + object.getHash() + ':' + object.getId()
            + ':' + (wp == null ? "?" : wp.getX() + ":" + wp.getY() + ":" + wp.getPlane());
        if (!seen.add(key))
        {
            return;
        }

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("kind", kind);
        record.put("id", object.getId());
        record.put("hash", object.getHash());
        record.put("world", worldPointRecord(object.getWorldLocation()));
        record.put("local", localPointRecord(object.getLocalLocation()));
        record.put("plane", object.getPlane());
        enrichObjectRecord(record, object);
        output.add(record);
    }

    // Collision/object changes invalidate path results even when scene base is unchanged.
    void invalidatePaths()
    {
        pathCache.clear();
    }

    void forgetObject(TileObject object)
    {
        dynamicObjects.remove(object);
    }

    void clearScene()
    {
        dynamicObjects.clear();
        pathCache.clear();
    }

    List<Map<String, Object>> objectTransformChanges()
    {
        List<Map<String, Object>> changes = new ArrayList<>();
        for (TileObject object : new ArrayList<>(dynamicObjects.keySet()))
        {
            ObjectComposition base = client.getObjectDefinition(object.getId());
            ObjectComposition transformed = base.getImpostor();
            int id = transformed == null ? -1 : transformed.getId();
            int previous = dynamicObjects.get(object);
            if (id == previous) { continue; }
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("id", object.getId());
            change.put("previousTransformedId", previous);
            change.put("transformedId", id);
            change.put("world", worldPointRecord(object.getWorldLocation()));
            change.put("local", localPointRecord(object.getLocalLocation()));
            enrichObjectRecord(change, object);
            changes.add(change);
            dynamicObjects.put(object, id);
            invalidatePaths();
        }
        return changes;
    }

    static long tileKey(WorldPoint point)
    {
        return ((long) (point.getPlane() & 3) << 28) | ((long) (point.getX() & 0x3fff) << 14) | (point.getY() & 0x3fff);
    }

    void enrichObjectRecord(Map<String, Object> record, TileObject object)
    {
        record.put("worldViewId", object.getWorldView() == null ? null : object.getWorldView().getId());
        record.put("templateWorld", object.getLocalLocation() == null || client.getWorldView(object.getLocalLocation().getWorldView()) == null ? null
            : worldPointRecord(WorldPoint.fromLocalInstance(client, object.getLocalLocation(), object.getPlane())));
        ObjectComposition definition = client.getObjectDefinition(object.getId());
        if (definition != null)
        {
            record.put("baseId", definition.getId());
            if (definition.getImpostorIds() != null)
            {
                definition = definition.getImpostor();
                dynamicObjects.put(object, definition == null ? -1 : definition.getId());
            }
            if (definition != null)
            {
                record.put("transformedId", definition.getId());
                record.put("name", definition.getName());
                record.put("actions", definition.getActions() == null ? null : definition.getActions().clone());
                record.put("sizeX", definition.getSizeX());
                record.put("sizeY", definition.getSizeY());
            }
        }
        if (object instanceof GameObject)
        {
            GameObject game = (GameObject) object;
            record.put("orientation", game.getOrientation());
            record.put("sceneMinX", game.getSceneMinLocation().getX());
            record.put("sceneMinY", game.getSceneMinLocation().getY());
            record.put("sceneMaxX", game.getSceneMaxLocation().getX());
            record.put("sceneMaxY", game.getSceneMaxLocation().getY());
        }
        if (object instanceof WallObject)
        {
            record.put("orientationA", ((WallObject) object).getOrientationA());
            record.put("orientationB", ((WallObject) object).getOrientationB());
        }
    }

    List<Map<String, Object>> objectsAt(WorldView view, int x, int y, int plane, int id)
    {
        List<Map<String, Object>> result = new ArrayList<>();
        Player player = client.getLocalPlayer();
        if (player == null || player.getWorldView() == null) { return result; }
        Tile[][][] tiles = view.getScene().getTiles();
        if (plane < 0 || plane >= tiles.length || x < 0 || x >= tiles[plane].length
            || tiles[plane][x] == null || y < 0 || y >= tiles[plane][x].length) { return result; }
        Tile tile = tiles[plane][x][y];
        if (tile == null) { return result; }
        Set<String> seen = new HashSet<>();
        for (TileObject object : new TileObject[]{tile.getWallObject(), tile.getDecorativeObject(), tile.getGroundObject()})
        {
            if (object != null && object.getId() == id) { addObject(result, seen, "scene", object); }
        }
        if (tile.getGameObjects() != null)
        {
            for (GameObject object : tile.getGameObjects())
            {
                if (object != null && object.getId() == id) { addObject(result, seen, "game", object); }
            }
        }
        return result;
    }

    List<Map<String, Object>> groundItems()
    {
        List<Map<String, Object>> result = new ArrayList<>();
        Player player = client.getLocalPlayer();
        if (player == null || player.getWorldView() == null || player.getWorldView().getScene() == null) { return result; }
        Microbot.getRs2TileItemCache().getStream().forEach(item ->
        {
            LocalPoint local = item.getLocalLocation();
            if (local == null || local.getWorldView() != player.getWorldView().getId()
                || item.getTile().getPlane() != player.getWorldLocation().getPlane()) { return; }
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", item.getId());
            record.put("quantity", item.getQuantity());
            record.put("world", worldPointRecord(item.getWorldLocation()));
            record.put("templateWorld", worldPointRecord(WorldPoint.fromLocalInstance(client, local, item.getTile().getPlane())));
            record.put("worldViewId", local.getWorldView());
            result.add(record);
        });
        return result;
    }

    private static int[][][] copyChunks(int[][][] source)
    {
        if (source == null) { return null; }
        int[][][] copy = new int[source.length][][];
        for (int p = 0; p < source.length; p++)
        {
            if (source[p] == null) { continue; }
            copy[p] = new int[source[p].length][];
            for (int x = 0; x < source[p].length; x++)
            {
                copy[p][x] = source[p][x] == null ? null : source[p][x].clone();
            }
        }
        return copy;
    }

    private static boolean inFlags(int[][] flags, int x, int y)
    {
        return flags != null
            && x >= 0
            && x < flags.length
            && flags[x] != null
            && y >= 0
            && y < flags[x].length;
    }

    private static Map<String, Object> worldAreaRecord(WorldArea area)
    {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", area.getX());
        map.put("y", area.getY());
        map.put("width", area.getWidth());
        map.put("height", area.getHeight());
        map.put("plane", area.getPlane());
        return map;
    }

    @Nullable
    private static Map<String, Object> worldPointRecord(@Nullable WorldPoint point)
    {
        if (point == null)
        {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", point.getX());
        map.put("y", point.getY());
        map.put("plane", point.getPlane());
        map.put("regionId", point.getRegionID());
        map.put("tileKey", tileKey(point));
        return map;
    }

    @Nullable
    private static Map<String, Object> localPointRecord(@Nullable LocalPoint point)
    {
        if (point == null)
        {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", point.getX());
        map.put("y", point.getY());
        map.put("sceneX", point.getSceneX());
        map.put("sceneY", point.getSceneY());
        return map;
    }

    private static final class PathNode
    {
        private final int x;
        private final int y;
        private final int distance;

        private PathNode(int x, int y, int distance)
        {
            this.x = x;
            this.y = y;
            this.distance = distance;
        }
    }
}
