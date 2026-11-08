package net.runelite.client.plugins.microbot.fourtifysagility.util.scene;

import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;

public final class TargetRange
{
    public static final int NPC_SEARCH_RADIUS = 12;
    public static final int GAME_OBJECT_SEARCH_RADIUS = 20;

    private TargetRange()
    {
    }

    public static int npcDistance(Client client, NPC npc)
    {
        Player player = client.getLocalPlayer();
        WorldView view = client.getTopLevelWorldView();
        if (player == null || npc == null || view == null || player.getWorldView() != view
                || npc.getWorldView() != view || player.getWorldLocation() == null || npc.getWorldLocation() == null
                || player.getWorldLocation().getPlane() != npc.getWorldLocation().getPlane())
        {
            return Integer.MAX_VALUE;
        }
        return player.getWorldLocation().distanceTo(npc.getWorldLocation());
    }

    public static int objectDistance(Client client, TileObject object)
    {
        Player player = client.getLocalPlayer();
        WorldView view = client.getTopLevelWorldView();
        if (player == null || object == null || view == null || player.getWorldView() != view
                || object.getWorldView() != view || object.getPlane() != player.getWorldLocation().getPlane()
                || player.getLocalLocation() == null)
        {
            return Integer.MAX_VALUE;
        }
        int x = player.getLocalLocation().getSceneX();
        int y = player.getLocalLocation().getSceneY();
        if (!(object instanceof GameObject) && object.getLocalLocation() == null)
        {
            return Integer.MAX_VALUE;
        }
        int minX = object instanceof GameObject ? 0 : object.getLocalLocation().getSceneX();
        int maxX = minX;
        int minY = object instanceof GameObject ? 0 : object.getLocalLocation().getSceneY();
        int maxY = minY;
        if (object instanceof GameObject)
        {
            GameObject gameObject = (GameObject) object;
            if (gameObject.getSceneMinLocation() == null || gameObject.getSceneMaxLocation() == null)
            {
                return Integer.MAX_VALUE;
            }
            minX = gameObject.getSceneMinLocation().getX();
            maxX = gameObject.getSceneMaxLocation().getX();
            minY = gameObject.getSceneMinLocation().getY();
            maxY = gameObject.getSceneMaxLocation().getY();
        }
        int dx = Math.max(Math.max(minX - x, x - maxX), 0);
        int dy = Math.max(Math.max(minY - y, y - maxY), 0);
        return Math.max(dx, dy);
    }
}
