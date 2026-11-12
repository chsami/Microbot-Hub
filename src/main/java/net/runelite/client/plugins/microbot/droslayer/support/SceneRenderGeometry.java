package net.runelite.client.plugins.microbot.droslayer.support;

import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;

/** Stateless render bounds shared by independent movement owners. */
public final class SceneRenderGeometry
{
    public static final int SOFTWARE_DRAW_DISTANCE = 25;
    public static final int DRAW_DISTANCE_MARGIN_TILES = 2;

    private SceneRenderGeometry() {}

    public static int renderedDrawDistance(boolean gpu, int sceneDrawDistance)
    {
        return !gpu || sceneDrawDistance <= 0 ? SOFTWARE_DRAW_DISTANCE : sceneDrawDistance;
    }

    public static boolean isWithinRenderedArea(int cameraLocalX, int cameraLocalY,
        LocalPoint target, int drawDistance)
    {
        if (target == null) return false;
        int tiles = Math.max(Math.abs(target.getX() - cameraLocalX), Math.abs(target.getY() - cameraLocalY))
            / Perspective.LOCAL_TILE_SIZE;
        return tiles <= drawDistance - DRAW_DISTANCE_MARGIN_TILES;
    }
}
