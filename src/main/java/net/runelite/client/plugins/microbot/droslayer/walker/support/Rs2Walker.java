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
     * Explicit-zoom variant, kept for external callers that genuinely want a particular zoom. The
     * walker itself never uses it: {@code Perspective.localToMinimap} reads the LIVE zoom, so the
     * click math is correct at any setting, and pinning the minimap at max zoom on every click both
     * looked bot-like and fought the user's own zoom the moment they changed it.
     */
    public static boolean walkMiniMap(WorldPoint worldPoint, double zoomDistance) {
        if (Microbot.getClient().getMinimapZoom() != zoomDistance)
            Microbot.getClient().setMinimapZoom(zoomDistance);
        return walkMiniMap(worldPoint);
    }


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


    /**
     * Calculates the total number of tiles from a given path to a destination.
     * This method validates that the path can actually reach the destination by checking
     * if the path's endpoint intersects with the destination area.
     *
     * @param path A list of WorldPoint objects representing the calculated path
     * @param destination The target WorldPoint destination to validate against
     * @return The total number of tiles in the path if valid, or Integer.MAX_VALUE if the path
     *         is empty, on different planes, or doesn't reach the destination
     */
    public static int getTotalTilesFromPath(List<WorldPoint> path, WorldPoint destination) {
        if (path.isEmpty() || path.get(path.size() - 1).getPlane() != destination.getPlane()) return Integer.MAX_VALUE;

        // Create centered WorldAreas instead of corner-based
        WorldPoint pathEndpoint = path.get(path.size() - 1);
        WorldPoint pathSouthWest = new WorldPoint(
                pathEndpoint.getX() - 4,
                pathEndpoint.getY() - 4,
                pathEndpoint.getPlane()
        );
        WorldArea pathArea = new WorldArea(pathSouthWest, 8, 8);

        WorldPoint destSouthWest = new WorldPoint(
                destination.getX() - 4,
                destination.getY() - 4,
                destination.getPlane()
        );
        WorldArea objectArea = new WorldArea(destSouthWest, 8, 8);

        if (!pathArea.intersectsWith2D(objectArea)) {
            return Integer.MAX_VALUE;
        }
        return path.size();
    }


    /**
     * Retrieves all transports found along the given path starting from a specific index.
     * Uses the default preferred transport type of TELEPORTATION_ITEM.
     *
     * @param path A list of WorldPoint objects representing the path to analyze
     * @param indexOfStartPoint The starting index in the path to begin searching for transports
     * @return A list of Transport objects found along the path, prioritizing teleportation items
     */
    public static List<Transport> getTransportsForPath(List<WorldPoint> path, int indexOfStartPoint) {
        return getTransportsForPath(path, indexOfStartPoint, TransportType.TELEPORTATION_ITEM, false);
    }


    /**
     * Retrieves all transports found along the given path starting from a specific index.
     * Analyzes the path for available transport options, prioritizing the specified transport type.
     *
     * This method examines each point in the path starting from the given index and identifies
     * available transport options (teleportation items, spells, objects, etc.) that can be used
     * to optimize travel. Transport types are sorted with teleportation items getting highest priority.
     *
     * @param path A list of WorldPoint objects representing the path to analyze
     * @param indexOfStartPoint The starting index in the path to begin searching for transports
     * @param prefTransportType The preferred transport type to prioritize in the search
     * @return A list of Transport objects found along the path, sorted by transport type priority
     */
    public static List<Transport> getTransportsForPath(List<WorldPoint> path, int indexOfStartPoint, TransportType prefTransportType) {
        return getTransportsForPath(path, indexOfStartPoint, prefTransportType, false);
    }


    /**
     * Retrieves all transports found along the given path starting from a specific index.
     * Analyzes the path for available transport options, prioritizing the specified transport type.
     * This version applies filtering and requirement setup for transports that require items.
     *
     * This method examines each point in the path starting from the given index and identifies
     * available transport options (teleportation items, spells, objects, etc.) that can be used
     * to optimize travel. Transport types are sorted with teleportation items getting highest priority.
     *
     * @param path A list of WorldPoint objects representing the path to analyze
     * @param indexOfStartPoint The starting index in the path to begin searching for transports
     * @param prefTransportType The preferred transport type to prioritize in the search
     * @param applyFiltering Whether to apply transport filtering and requirement setup
     * @return A list of Transport objects found along the path, sorted by transport type priority
     */
    public static List<Transport> getTransportsForPath(List<WorldPoint> path, int indexOfStartPoint, TransportType prefTransportType, boolean applyFiltering) {
        List<Transport> transportList = new ArrayList<>();
        if (path == null || path.isEmpty() || indexOfStartPoint < 0 || indexOfStartPoint >= path.size()) {
            return transportList;
        }
        Map<WorldPoint, Integer> pathFirstIndex = buildPathFirstIndex(path);
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        int currentIndex = indexOfStartPoint;

        // Loop through the path until the end
        while (currentIndex < path.size()) {
            WorldPoint currentPoint = path.get(currentIndex);
            // Get any transports that start at this point (or keyed by this point)
            Set<Transport> transportsAtPoint = Rs2PathApi.getTransports().get(currentPoint);
            if (transportsAtPoint == null || transportsAtPoint.isEmpty()) {
                currentIndex++;
                continue;
            }
            boolean foundTransport = false;
            // sort by type to prioritize teleportation items first, then other types
            List<Transport> orderedTransports = new ArrayList<>(transportsAtPoint);
            orderedTransports.sort(Comparator.comparing(Transport::getType, (type1, type2) -> {
                // sort teleportation items by preference transport type for the current path point.
                if (type1 == prefTransportType && type2 != prefTransportType) {
                    return -1;
                }
                if (type2 == prefTransportType && type1 != prefTransportType) {
                    return 1;
                }
                // For all other types, use natural enum ordering
                return type1.compareTo(type2);
            }));
            // Iterate over each available transport
            for (Transport transport : orderedTransports) {

                // Special handling for teleportation-like transports (originless)
                // NOTE: Leagues "Area" teleports are injected as SEASONAL_TRANSPORT with null origin.
                String di = transport.getDisplayInfo();
                boolean isLeaguesAreaTeleport = transport.getType() == TransportType.SEASONAL_TRANSPORT
                        && di != null
                        && di.toLowerCase().startsWith("leagues area:");

                if (transport.getType() == TransportType.TELEPORTATION_ITEM
                        || transport.getType() == TransportType.TELEPORTATION_SPELL
                        || isLeaguesAreaTeleport)
                {
                    // For teleportation, we assume origin is null and simply check if the destination exists in the path.
                    Integer destIndex = pathFirstIndex.get(transport.getDestination());
                    if (destIndex != null) {
                        transportList.add(transport);
                        // Advance the current index to the destination tile (or at least one forward)
                        currentIndex = destIndex > currentIndex ? destIndex : currentIndex + 1;
                        foundTransport = true;
                        break;
                    }
                }

                // For non-teleportation transports (or if teleportation had a valid origin, though typically null):
                Collection<WorldPoint> originPoints;
                if (transport.getOrigin() == null) {
                    originPoints = Collections.singleton(null);
                } else {
                    originPoints = WorldPoint.toLocalInstance(
                            Microbot.getClient().getTopLevelWorldView(), transport.getOrigin());
                }

                for (WorldPoint origin : originPoints) {
                    // If an origin is defined but the player's plane doesn't match, skip it.
                    if (transport.getOrigin() != null && playerLoc != null
                            && playerLoc.getPlane() != transport.getOrigin().getPlane()) {
                        continue;
                    }

                    // For non-teleportation transports, ensure both origin and destination exist in the path
                    // and that the destination comes after the origin.
                    Integer indexOfDestinationValue = pathFirstIndex.get(transport.getDestination());
                    int indexOfDestination = indexOfDestinationValue != null ? indexOfDestinationValue : -1;
                    if (transport.getType() != TransportType.TELEPORTATION_ITEM
                            && transport.getType() != TransportType.TELEPORTATION_SPELL
                            && !isLeaguesAreaTeleport) {
                        Integer indexOfOriginValue = pathFirstIndex.get(transport.getOrigin());
                        int indexOfOrigin = indexOfOriginValue != null ? indexOfOriginValue : -1;
                        if (indexOfOrigin == -1 || indexOfDestination == -1 || indexOfDestination < indexOfOrigin) {
                            continue;
                        }
                    }

                    // If the current path point equals the transport's origin then add it.
                    if (currentPoint.equals(origin)) {
                        transportList.add(transport);
                        currentIndex = indexOfDestination > currentIndex ? indexOfDestination : currentIndex + 1;
                        foundTransport = true;
                        break;
                    }
                }
                if (foundTransport) {
                    break;
                }
            }

            if (!foundTransport) {
                currentIndex++;
            }
        }

        WebWalkLog.bankPathTransportsDebug(transportList.size(), path.get(0), path.get(path.size() - 1));

        // Apply filtering and requirement setup if requested
        if (applyFiltering) {
            transportList = applyTransportFiltering(transportList);
        }

        return transportList;
    }


    private static Map<WorldPoint, Integer> buildPathFirstIndex(List<WorldPoint> path) {
        Map<WorldPoint, Integer> pathFirstIndex = new HashMap<>(path.size());
        for (int i = 0; i < path.size(); i++) {
            pathFirstIndex.putIfAbsent(path.get(i), i);
        }
        return pathFirstIndex;
    }


    /**
     * Applies transport filtering and requirement setup for transport items.
     * This method filters transports to only include those that require items and
     * sets up item requirements for fairy rings and currency-based transports.
     *
     * @param transports The list of transports to filter and process
     * @return The filtered and processed list of transports
     */
    private static List<Transport> applyTransportFiltering(List<Transport> transports) {
        return Rs2WalkerBankingPlanner.applyTransportFiltering(transports);
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
