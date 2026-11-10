package net.runelite.client.plugins.microbot.droinfernoprayers;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

public final class InfernoBoundary {
    private InfernoBoundary() { }
    public static boolean templateTile(int x, int y, int plane) {
        return plane == 0 && x >= 2240 && x < 2304 && y >= 5312 && y < 5376;
    }

    public static boolean inside(Client c) {
        if (c == null || c.getGameState() != GameState.LOGGED_IN || c.getLocalPlayer() == null
            || c.getTopLevelWorldView() == null || !c.getTopLevelWorldView().isInstance()
            || c.getBoostedSkillLevel(Skill.HITPOINTS) <= 0 || c.getLocalPlayer().getLocalLocation() == null) return false;
        WorldPoint p = WorldPoint.fromLocalInstance(c, c.getLocalPlayer().getLocalLocation());
        return p != null && templateTile(p.getX(), p.getY(), p.getPlane());
    }
}
