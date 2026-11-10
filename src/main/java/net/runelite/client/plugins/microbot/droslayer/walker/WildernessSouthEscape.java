package net.runelite.client.plugins.microbot.droslayer.walker;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2MiniMap;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/** Broad southern minimap clicks; no route planner or per-tile movement sequence. */
final class WildernessSouthEscape
{
    private WildernessSouthEscape() { }

    static WorldPoint furthestSouth(WorldPoint here, int drift, Predicate<WorldPoint> visible)
    {
        for (int distance = 64; distance >= 5; distance--)
        {
            WorldPoint candidate = new WorldPoint(here.getX() + distance * drift / 16,
                here.getY() - distance, here.getPlane());
            if (visible.test(candidate)) return candidate;
        }
        return null;
    }

    static void clickSouth()
    {
        Rs2Player.toggleRunEnergy(true);
        int drift = ThreadLocalRandom.current().nextInt(-5, 6);
        Point click = Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            var client = Microbot.getClient();
            if (client == null || client.getLocalPlayer() == null || client.getLocalPlayer().isDead()) return null;
            WorldPoint here = client.getLocalPlayer().getWorldLocation();
            WorldPoint target = furthestSouth(here, drift, candidate ->
            {
                Point point = Rs2MiniMap.worldToMinimap(candidate);
                return point != null && Rs2MiniMap.isPointInsideMinimap(point);
            });
            return target == null ? null : Rs2MiniMap.worldToMinimap(target);
        }).orElse(null);
        if (click != null) Microbot.getMouse().click(click);
    }
}
