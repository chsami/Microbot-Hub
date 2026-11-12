package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;

/** Time in half ticks (300 ms): running one tile = 1, one game tick = 2. No GP pricing. */
public final class RouteTimeCost
{
    public static final int MINIGAME = 80; // Conservative 24-second activation/arrival allowance.
    public static final int BANK = 12;
    public static final int ITEM_TELEPORT = 10;
    private RouteTimeCost() { }
    public static int transport(Transport edge) {
        return transport(edge.getType().name(), edge.getDuration());
    }
    public static int transport(String type, int ticks) {
        int time = (int)Math.min(Integer.MAX_VALUE / 4L, 2L * Math.max(1, ticks));
        return "TELEPORTATION_MINIGAME".equals(type) ? Math.max(MINIGAME, time) : time;
    }
    public static int walk(WorldPoint from, WorldPoint to) {
        if (from == null || to == null || from.getPlane() != to.getPlane()) return Integer.MAX_VALUE / 4;
        return Math.max(Math.abs(from.getX()-to.getX()),Math.abs(from.getY()-to.getY()));
    }
    public static long seconds(int cost) { return (cost * 3L + 9) / 10; }
}
