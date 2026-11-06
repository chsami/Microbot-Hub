package net.runelite.client.plugins.microbot.fourtifysbarrows.util.scene;

import net.runelite.api.TileObject;
import java.util.Comparator;

import net.runelite.api.NPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;

public final class SceneQueries
{
    private SceneQueries()
    {
    }

    public static List<GameObject> gameObjects(Client client)
    {
        WorldView view = client.getTopLevelWorldView();
        if (view == null || view.getScene() == null)
        {
            return List.of();
        }
        Tile[][][] tiles = view.getScene().getTiles();
        int plane = view.getPlane();
        if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null)
        {
            return List.of();
        }
        Set<GameObject> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<GameObject> objects = new ArrayList<>();
        for (Tile[] row : tiles[plane])
        {
            if (row == null)
            {
                continue;
            }
            for (Tile tile : row)
            {
                if (tile == null || tile.getGameObjects() == null)
                {
                    continue;
                }
                for (GameObject object : tile.getGameObjects())
                {
                    if (object != null && seen.add(object))
                    {
                        objects.add(object);
                    }
                }
            }
        }
        return objects;
    }

    public static List<Tile> playerTiles(Client client)
    {
        List<Tile> result = new ArrayList<>();
        WorldView view = client.getTopLevelWorldView();
        if (view == null || view.getScene() == null)
        {
            return result;
        }
        Tile[][][] scene = view.getScene().getTiles();
        int plane = view.getPlane();
        if (plane < 0 || plane >= scene.length)
        {
            return result;
        }
        for (Tile[] row : scene[plane])
        {
            for (Tile tile : row)
            {
                if (tile != null)
                {
                    result.add(tile);
                }
            }
        }
        return result;
    }

    public static boolean isNpcLive(Client client, NPC target)
    {
        if (target == null)
        {
            return false;
        }
        WorldView view = client.getTopLevelWorldView();
        if (view != null && view.npcs() != null)
        {
            for (NPC npc : view.npcs())
            {
                if (npc == target)
                {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isObjectLive(Client client, TileObject target)
    {
        if (target == null)
        {
            return false;
        }
        for (Tile tile : playerTiles(client))
        {
            if (tile.getGroundObject() == target || tile.getWallObject() == target || tile.getDecorativeObject() == target)
            {
                return true;
            }
            for (GameObject object : tile.getGameObjects())
            {
                if (object == target)
                {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<GameObject> nearbyGameObjects(Client client)
    {
        List<GameObject> result = new ArrayList<>();
        for (GameObject object : gameObjects(client))
        {
            if (TargetRange.objectDistance(client, object) <= TargetRange.GAME_OBJECT_SEARCH_RADIUS)
            {
                result.add(object);
            }
        }
        result.sort(Comparator.comparingInt(object -> TargetRange.objectDistance(client, object)));
        return result;
    }
}
