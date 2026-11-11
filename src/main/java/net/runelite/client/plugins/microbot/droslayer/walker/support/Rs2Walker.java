package net.runelite.client.plugins.microbot.droslayer.walker.support;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.walker.support.banking.Rs2WalkerBankingPlanner;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.coords.Rs2LocalPoint;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * TODO:
 * 1. fix teleports starting from inside the POH
 * <p>
 * Seasonal handlers ({@link Rs2LeaguesTransport#tryHandleLeaguesAreaTransport}, MoA) must not run on the client thread — same contract as {@link Rs2LeaguesTransport#leaguesTeleport}.
 */
@Slf4j
public class Rs2Walker {




    /**
     * Clicks {@code worldPoint} on the minimap at whatever zoom the user has. Zoom only moves the
     * trade-off between reach and pixel precision — zoomed IN shrinks clickable range (~16 tiles at
     * zoom 5, ~40 zoomed out), zoomed out shrinks pixels-per-tile — and every caller already has a
     * fallback for an unclickable point (nearer route point, canvas click), which is exactly what a
     * human at that zoom would do. Tile-exact clicks near walls use the canvas path, which is
     * pixel-precise at any zoom.
     */
    public static boolean walkMiniMap(WorldPoint worldPoint) {
        Point point = Rs2MiniMap.worldToMinimap(worldPoint);

        if (point == null) return false;
        if (!Rs2MiniMap.isPointInsideMinimap(point)) return false;

        Microbot.getMouse().click(point);
        
        return true;
    }


    public static boolean walkFastCanvas(WorldPoint worldPoint) {
        return walkFastCanvas(worldPoint, true);
    }




    public static boolean walkFastCanvas(WorldPoint worldPoint, boolean toggleRun) {
        if (worldPoint == null) {
            log.debug("[Walker] walkFastCanvas rejected: null worldPoint");
            return false;
        }
        Rs2Player.toggleRunEnergy(toggleRun);
        Point canv;
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), worldPoint);

        if (Microbot.getClient().getTopLevelWorldView().isInstance() && localPoint == null) {
            localPoint = Rs2LocalPoint.fromWorldInstance(worldPoint);
        }

        if (localPoint == null) {
            WorldPoint playerLoc = Rs2Player.getWorldLocation();
            if (playerLoc != null
                    && playerLoc.getPlane() == worldPoint.getPlane()
                    && walkMiniMapToward(worldPoint, playerLoc, 13)) {
                return true;
            }
            log.debug("[Walker] walkFastCanvas localpoint null for {}", worldPoint);
            return false;
        }

        canv = Perspective.localToCanvas(Microbot.getClient(), localPoint, Microbot.getClient().getTopLevelWorldView().getPlane());

        int canvasX = canv != null ? canv.getX() : -1;
        int canvasY = canv != null ? canv.getY() : -1;

        //if the tile is not on screen, use minimap
        if (!Rs2Camera.isTileOnScreen(localPoint) || canvasX < 0 || canvasY < 0) {
            WorldPoint playerLoc = Rs2Player.getWorldLocation();
            if (playerLoc != null
                    && playerLoc.getPlane() == worldPoint.getPlane()
                    && walkMiniMapToward(worldPoint, playerLoc, 13)) {
                return true;
            }
            return Rs2Walker.walkMiniMap(worldPoint);
        }

        NewMenuEntry entry = new NewMenuEntry()
                .param0(canvasX)
                .param1(canvasY)
                .type(MenuAction.WALK)
                .identifier(0)
                .itemId(0)
                .option("Walk here");

        Microbot.doInvoke(entry,
                new Rectangle(canvasX, canvasY, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
        return true;
    }








    // findForwardRecoveryIndex extracted to recovery/RouteRecovery (P1 walker decomposition)


    // interpolateClickableTarget extracted to recovery/RouteRecovery (P1)

    // clampToEuclideanRadius extracted to recovery/RouteRecovery (P1)

    static int euclideanSq(WorldPoint a, WorldPoint b) {
        int dx = a.getX() - b.getX();
        int dy = a.getY() - b.getY();
        return dx * dx + dy * dy;
    }




    static String normalizeCharterWidgetText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return Rs2UiHelper.stripTagsToSpace(text)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }


    static boolean walkMiniMapToward(WorldPoint target, WorldPoint playerLoc, int maxEuclidean) {
        if (target == null || playerLoc == null || target.getPlane() != playerLoc.getPlane()) {
            return false;
        }

        int dx = target.getX() - playerLoc.getX();
        int dy = target.getY() - playerLoc.getY();
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance <= 1) {
            return false;
        }

        if (walkReachableMiniMapToward(target, playerLoc, maxEuclidean)) {
            return true;
        }

        int cappedRadius = Math.max(2, maxEuclidean);
        // The scaled-radius points below are geometric guesses toward an off-clip target. Right
        // after a teleport (or when the target sits behind a wall) that guess can be an unreachable
        // tile far off the route, producing the "random click far from the path" behaviour. Only
        // click a guess that is actually reachable from the player.
        Set<WorldPoint> reachable = Rs2Tile
                .getReachableTilesFromTile(playerLoc, Math.max(2, cappedRadius)).keySet();
        int[] radii = new int[] {cappedRadius, 10, 8, 6, 4};
        for (int radius : radii) {
            if (radius >= distance) {
                continue;
            }

            double scale = radius / distance;
            WorldPoint fallback = new WorldPoint(
                    playerLoc.getX() + (int) Math.round(dx * scale),
                    playerLoc.getY() + (int) Math.round(dy * scale),
                    playerLoc.getPlane());
            if (fallback.equals(playerLoc)) {
                continue;
            }
            if (!reachable.contains(fallback)) {
                continue;
            }
            if (Rs2Walker.walkMiniMap(fallback)) {
                log.info("[Walker] Minimap click target {} was outside clip; used fallback {}", target, fallback);
                return true;
            }
        }

        return false;
    }


    static boolean walkReachableMiniMapToward(WorldPoint target, WorldPoint playerLoc, int maxEuclidean) {
        int currentDistance = euclideanSq(playerLoc, target);
        return Rs2Tile.getReachableTilesFromTile(playerLoc, Math.max(2, maxEuclidean)).keySet().stream()
                .filter(tile -> tile != null
                        && tile.getPlane() == playerLoc.getPlane()
                        && !tile.equals(playerLoc)
                        && euclideanSq(playerLoc, tile) <= maxEuclidean * maxEuclidean
                        && euclideanSq(tile, target) < currentDistance)
                .sorted(Comparator
                        .comparingInt((WorldPoint tile) -> euclideanSq(tile, target))
                        .thenComparing(Comparator.comparingInt((WorldPoint tile) -> euclideanSq(playerLoc, tile)).reversed()))
                .filter(Rs2Walker::walkMiniMap)
                .findFirst()
                .map(tile -> {
                    log.info("[Walker] Minimap click target {} was outside clip; used reachable fallback {}", target, tile);
                    return true;
                })
                .orElse(false);
    }
}
