package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.SceneObjects;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Narrow recovery for Tirannwn's random-failure leaf traps.
 *
 * <p>The walker remains responsible for planning and every normal movement click. This
 * helper only acts when a Tirannwn route is active: it climbs out of a leaf-trap pit whenever the
 * confirmed escape object is visible, or jumps a nearby leaf trap after the route has stopped
 * making progress. After either interaction it asks the existing planner to reassess from the
 * player's actual landing tile, because a failed jump can leave the player on either side.</p>
 */
@Singleton
final class TirannwnObstacleSupport
{
    private static final int LEAF_STALL_TICKS = 5;
    private static final int INTERACTION_TIMEOUT_TICKS = 18;
    private static final int RETRY_COOLDOWN_TICKS = 3;
    private static final int FORWARD_OBSTACLE_ORIGIN_RADIUS = 2;
    private static final int TRIPWIRE_ORIGIN_RADIUS = 3;
    private static final int POST_FOREST_MIN_ROUTE_STEP = 4;
    private static final int POST_FOREST_MAX_ROUTE_STEP = 10;
    private static final int POST_FOREST_TIMEOUT_TICKS = 24;
    private static final int CROSSED_EDGE_GUARD_CLEAR_DISTANCE = 9;

    private final Client client;
    private final EfficientWalker walker;
    private final Map<WorldPoint, Set<Transport>> transports = net.runelite.client.plugins.microbot.droefficientwalker.support.TransportResources.loadAllFromResources();

    private WorldPoint lastLocation;
    private int stationaryTicks;
    private int cooldownTicks;
    private boolean awaitingTraversal;
    private int traversalStartedTick;
    private WorldPoint traversalStart;
    private WorldPoint traversalTarget;
    private boolean traversalPitEscape;
    private WorldPoint traversalObjectLocation;
    private WorldPoint rejectedEscapeRock;
    private boolean awaitingDenseForest;
    private WorldPoint denseForestOrigin;
    private WorldPoint denseForestLanding;
    private int denseForestStepX;
    private int denseForestStepY;
    private boolean awaitingPostForestEgress;
    private WorldPoint postForestFinalTarget;
    private WorldPoint postForestWaypoint;
    private int postForestStartedTick;
    private ForwardTransport observedNativeForest;
    private WorldPoint observedNativeForestTarget;
    private boolean awaitingLeafCrossing;
    private WorldPoint leafOrigin;
    private WorldPoint leafLanding;
    private WorldPoint recentLeafOrigin;
    private WorldPoint recentLeafLanding;
    private WorldPoint recentLeafTarget;
    private WorldPoint recentTripwireOrigin;
    private WorldPoint recentTripwireLanding;
    private WorldPoint recentDenseForestOrigin;
    private WorldPoint recentDenseForestLanding;
    private int recentDenseForestStepX;
    private int recentDenseForestStepY;
    private boolean traversalTripwire;
    private WorldPoint traversalExpectedLanding;

    @Inject
    TirannwnObstacleSupport(Client client, EfficientWalker walker)
    {
        this.client = client;
        this.walker = walker;
    }

    /** @return true when this helper owns the current tick and the engine should wait. */
    boolean tick()
    {
        WorldPoint target = walker.getLogicalDestination();
        Player player = client.getLocalPlayer();
        WorldPoint current = player == null ? null : player.getWorldLocation();

        if (target == null || current == null || !isTirannwnRoute(target, current))
        {
            reset();
            return false;
        }

        if (cooldownTicks > 0)
        {
            cooldownTicks--;
        }

        if (awaitingTraversal)
        {
            return awaitTraversal(current);
        }

        if (awaitingDenseForest)
        {
            return awaitDenseForest(current, player);
        }

        if (awaitingPostForestEgress)
        {
            return awaitPostForestEgress(current, player);
        }

        if (awaitingLeafCrossing)
        {
            return awaitLeafCrossing(current, player);
        }

        if (observeNativeForestExit(current, target, player)) return true;
        updateStationary(current);
        updateCrossedEdgeGuards(current);

        // Tripwires need to be intercepted while the walker is still approaching them.
        // Unlike leaves/forest, the live collision route often keeps issuing ordinary walk clicks
        // right up to the wire, so waiting for five fully-stationary ticks means this helper never
        // gets ownership.  The candidate is still route-directed (origin -> landing) and local, so
        // this does not turn into a generic nearby-object click.
        if (cooldownTicks == 0
                && player.getAnimation() == -1
                && walker.getStatus() == EfficientWalker.Status.WALKING
                && tryForwardSimpleObstacle(current, target, "Tripwire", "Step-over", "tripwire",
                TRIPWIRE_ORIGIN_RADIUS))
        {
            return true;
        }

        if (Rs2Player.isMoving() || player.getAnimation() != -1 || cooldownTicks > 0)
        {
            return false;
        }

        TileObject escape = preferredEscapeRock(10);
        if (escape != null)
        {
            return interact(escape, "Climb", "leaf-trap pit escape", target, current);
        }

        if (tryForwardDenseForest(current, target))
        {
            return true;
        }

        if (tryForwardLeaf(current, target))
        {
            return true;
        }

        if (stationaryTicks < LEAF_STALL_TICKS
                || (walker.getStatus() != EfficientWalker.Status.WALKING
                && walker.getStatus() != EfficientWalker.Status.BLOCKED))
        {
            return false;
        }

        if (tryForwardSimpleObstacle(current, target, "Tripwire", "Step-over", "tripwire"))
        {
            return true;
        }

        if (tryForwardSimpleObstacle(current, target, "Sticks", "Pass", "stick trap"))
        {
            return true;
        }

        return false;
    }

    /**
     * Surface obstacles recorded in the route resources also need to be admitted into the
     * executable graph. CompleteRouteDiscovery can otherwise select them while the native
     * Walker graph cannot execute the corresponding Object interaction.
     * Keep the admission limited to Tirannwn and these four known obstacle families.
     */
    static boolean isRecordedSurfaceLink(Transport edge)
    {
        if (edge == null || edge.getType() != net.runelite.client.plugins.microbot.shortestpath.TransportType.TRANSPORT
            || edge.getOrigin() == null || edge.getDestination() == null || edge.getObjectId() <= 0)
            return false;
        WorldPoint origin = edge.getOrigin();
        WorldPoint destination = edge.getDestination();
        if (!inTirannwn(origin) || !inTirannwn(destination)) return false;
        String name = edge.getName();
        String action = edge.getAction();
        return (edge.getObjectId() == 8742 && "Tree".equalsIgnoreCase(name) && "Pass".equals(action))
            || ("Dense forest".equalsIgnoreCase(name)
                && ("Enter".equals(action) || "Pass-through".equals(action)))
            || ("Tripwire".equalsIgnoreCase(name) && "Step-over".equals(action))
            || ("Sticks".equalsIgnoreCase(name) && "Pass".equals(action));
    }

    private static boolean inTirannwn(WorldPoint point)
    {
        return point.getPlane() == 0 && point.getX() >= 2100 && point.getX() <= 2400
            && point.getY() >= 3050 && point.getY() <= 3350;
    }

    /** The engine can cross the forest without this helper issuing Enter. */
    private boolean observeNativeForestExit(WorldPoint current, WorldPoint target, Player player)
    {
        if (!target.equals(observedNativeForestTarget)) observedNativeForest = null;
        if (lastLocation != null && !lastLocation.equals(current))
        {
            for (int dx = -2; dx <= 2; dx++)
            {
                for (int dy = -2; dy <= 2; dy++)
                {
                    WorldPoint origin = new WorldPoint(lastLocation.getX() + dx,
                            lastLocation.getY() + dy, lastLocation.getPlane());
                    for (Transport edge : transports.getOrDefault(origin, Set.of()))
                    {
                        if ("Dense forest".equalsIgnoreCase(edge.getName())
                                && "Enter".equalsIgnoreCase(edge.getAction())
                                && current.equals(edge.getDestination())
                                && routeDirection(origin, current) >= 0
                                && directionalProgress(origin, current, target) > 0)
                        {
                            observedNativeForest = new ForwardTransport(origin, edge);
                            observedNativeForestTarget = target;
                        }
                    }
                }
            }
        }
        if (observedNativeForest == null || !current.equals(observedNativeForest.transport.getDestination())) return false;
        int dx = current.getX() - observedNativeForest.origin.getX();
        int dy = current.getY() - observedNativeForest.origin.getY();
        // Leave the three-tree corridor to its current owner until its final landing is idle.
        if (findDenseForestContinuation(current, dx, dy) != null
                || Rs2Player.isMoving() || player.getAnimation() != -1) return false;
        recentDenseForestOrigin = observedNativeForest.origin;
        recentDenseForestLanding = current;
        recentDenseForestStepX = dx;
        recentDenseForestStepY = dy;
        observedNativeForest = null;
        lastLocation = current;
        stationaryTicks = 0;
        beginPostForestEgress(current, target);
        return true;
    }

    private void beginPostForestEgress(WorldPoint current, WorldPoint target)
    {
        awaitingPostForestEgress = true;
        postForestFinalTarget = target;
        postForestWaypoint = null;
        postForestStartedTick = client.getTickCount();
        walker.resumeCommittedTirannwnWalk(target);
        Microbot.log("[EfficientWalker][Tirannwn] Dense forest chain completed at " + current
                + "; limiting route steps to 10 tiles before the next obstacle for " + target);
    }

    private boolean awaitTraversal(WorldPoint current)
    {
        Player player = client.getLocalPlayer();
        boolean busy = Rs2Player.isMoving() || (player != null && player.getAnimation() != -1);
        boolean moved = traversalStart != null && !traversalStart.equals(current);
        boolean timedOut = client.getTickCount() - traversalStartedTick >= INTERACTION_TIMEOUT_TICKS;

        if (!timedOut && (busy || !moved))
        {
            return true;
        }

        WorldPoint target = traversalTarget;
        boolean pitEscape = traversalPitEscape;
        boolean tripwire = traversalTripwire;
        WorldPoint expectedLanding = traversalExpectedLanding;
        WorldPoint attemptedObject = traversalObjectLocation;
        awaitingTraversal = false;
        traversalStart = null;
        traversalTarget = null;
        traversalPitEscape = false;
        traversalTripwire = false;
        traversalExpectedLanding = null;
        traversalObjectLocation = null;
        stationaryTicks = 0;
        lastLocation = current;
        cooldownTicks = RETRY_COOLDOWN_TICKS;

        if (!moved && timedOut && pitEscape && attemptedObject != null)
        {
            rejectedEscapeRock = attemptedObject;
            Microbot.log("[EfficientWalker][Tirannwn] protruding rocks at " + attemptedObject
                    + " produced no movement; trying the other reachable side.");
        }
        else if (moved && pitEscape)
        {
            rejectedEscapeRock = null;
        }

        if (tripwire)
        {
            if (moved && expectedLanding != null
                    && current.getPlane() == expectedLanding.getPlane()
                    && current.distanceTo2D(expectedLanding) <= 1)
            {
                // The exact planned edge was stored before interaction; keep it guarded locally.
                Microbot.log("[EfficientWalker][Tirannwn] Tripwire crossed; guarding reverse edge "
                        + recentTripwireLanding + " -> " + recentTripwireOrigin);
            }
            else if (!moved && timedOut)
            {
                recentTripwireOrigin = null;
                recentTripwireLanding = null;
            }
        }

        if (moved && target != null)
        {
            // A jump failure is not assumed to mean either side. Replan from the observed tile.
            walker.resumeCommittedTirannwnWalk(target);
            Microbot.log("[EfficientWalker][Tirannwn] obstacle traversal ended at " + current
                    + "; replanning the unchanged destination " + target);
        }
        return true;
    }

    private boolean interact(TileObject object, String action, String label,
                             WorldPoint target, WorldPoint current)
    {
        if (!SceneObjects.interact(object, action))
        {
            cooldownTicks = RETRY_COOLDOWN_TICKS;
            return false;
        }

        awaitingTraversal = true;
        traversalStartedTick = client.getTickCount();
        traversalStart = current;
        traversalTarget = target;
        traversalPitEscape = "leaf-trap pit escape".equals(label);
        traversalTripwire = false;
        traversalExpectedLanding = null;
        traversalObjectLocation = object.getWorldLocation();
        stationaryTicks = 0;
        Microbot.log("[EfficientWalker][Tirannwn] " + action + " " + label
                + " at " + object.getWorldLocation());
        return true;
    }

    private TileObject preferredEscapeRock(int distance)
    {
        WorldPoint player = client.getLocalPlayer().getWorldLocation();
        List<TileObject> rocks = confirmedObjects("Protruding rocks", "Climb", distance);
        if (rocks.isEmpty())
        {
            return null;
        }

        List<TileObject> reachable = rocks.stream()
                .filter(object -> SceneObjects.canReach(object.getWorldLocation()))
                .collect(Collectors.toList());
        List<TileObject> candidates = reachable.isEmpty() ? rocks : reachable;
        if (candidates.size() > 1 && rejectedEscapeRock != null)
        {
            List<TileObject> alternatives = candidates.stream()
                    .filter(object -> !rejectedEscapeRock.equals(object.getWorldLocation()))
                    .collect(Collectors.toList());
            if (!alternatives.isEmpty())
            {
                candidates = alternatives;
            }
        }

        return candidates.stream()
                .min(Comparator.comparingInt(object -> object.getWorldLocation().distanceTo2D(player)))
                .orElse(null);
    }

    private List<TileObject> confirmedObjects(String name, String action, int distance)
    {
        return SceneObjects.getAll(object -> {
            ObjectComposition composition = SceneObjects.convertToObjectComposition(object);
            return composition != null
                    && name.equalsIgnoreCase(composition.getName())
                    && SceneObjects.hasAction(composition, action, true);
        }, distance);
    }

    private boolean tryForwardDenseForest(WorldPoint current, WorldPoint target)
    {
        if (walker.getStatus() != EfficientWalker.Status.WALKING)
        {
            return false;
        }

        ForwardTransport candidate = findForwardTransport(
                current, target, "Dense forest", "Enter", FORWARD_OBSTACLE_ORIGIN_RADIUS);
        if (candidate == null)
        {
            return false;
        }
        if (isReverseOfRecentEdge(candidate.origin, candidate.transport.getDestination(),
                recentDenseForestOrigin, recentDenseForestLanding))
        {
            Microbot.log("[EfficientWalker][Tirannwn] reject reverse Dense forest edge "
                    + candidate.origin + " -> " + candidate.transport.getDestination()
                    + "; replanning unchanged destination " + target);
            walker.resumeCommittedTirannwnWalk(target);
            cooldownTicks = RETRY_COOLDOWN_TICKS;
            return true;
        }
        Transport best = candidate.transport;

        GameObject forest = nearestObjectId(best.getObjectId(), 6);
        if (forest == null || !SceneObjects.hasAction(forest, "Enter"))
        {
            return false;
        }
        if (!SceneObjects.interact(forest, "Enter"))
        {
            cooldownTicks = RETRY_COOLDOWN_TICKS;
            return true;
        }

        walker.commitTirannwnWalking(target);
        awaitingDenseForest = true;
        denseForestOrigin = candidate.origin;
        denseForestLanding = best.getDestination();
        denseForestStepX = denseForestLanding.getX() - candidate.origin.getX();
        denseForestStepY = denseForestLanding.getY() - candidate.origin.getY();
        traversalStartedTick = client.getTickCount();
        stationaryTicks = 0;
        Microbot.log("[EfficientWalker][Tirannwn] Enter Dense forest id=" + best.getObjectId()
                + " transportOrigin=" + candidate.origin + " player=" + current
                + " expectedLanding=" + denseForestLanding + " destination=" + target);
        return true;
    }

    private boolean tryForwardLeaf(WorldPoint current, WorldPoint target)
    {
        if (walker.getStatus() != EfficientWalker.Status.WALKING
                && walker.getStatus() != EfficientWalker.Status.BLOCKED)
        {
            return false;
        }

        ForwardTransport candidate = findForwardTransport(
                current, target, "Leaves", "Jump", FORWARD_OBSTACLE_ORIGIN_RADIUS);
        if (candidate == null)
        {
            return false;
        }

        WorldPoint candidateLanding = candidate.transport.getDestination();
        if (target.equals(recentLeafTarget)
                && isReverseOfRecentEdge(candidate.origin, candidateLanding,
                recentLeafOrigin, recentLeafLanding))
        {
            // We just crossed this exact leaf trap for the same logical destination.  The live
            // route is frequently shortened enough that it no longer contains the leaf endpoints,
            // so the generic target-vector fallback can mistakenly decide that jumping straight
            // back is a tiny improvement.  Do not own the tick: let the walker continue
            // along its freshly replanned route away from the landing side.
            Microbot.log("[EfficientWalker][Tirannwn] ignore reverse Leaves edge "
                    + candidate.origin + " -> " + candidateLanding
                    + "; already crossed " + recentLeafOrigin + " -> " + recentLeafLanding
                    + " for destination " + target + "; continuing route");
            cooldownTicks = RETRY_COOLDOWN_TICKS;
            return false;
        }

        Transport best = candidate.transport;

        TileObject leaves = nearestObjectIdAnyType(best.getObjectId(), 5);
        if (leaves == null || !SceneObjects.hasAction(leaves, "Jump"))
        {
            return false;
        }

        if (!target.equals(walker.getDestination()))
        {
            walker.resumeCommittedTirannwnWalk(target);
        }
        else
        {
            walker.commitTirannwnWalking(target);
        }
        if (!SceneObjects.interact(leaves, "Jump"))
        {
            cooldownTicks = RETRY_COOLDOWN_TICKS;
            return true;
        }

        awaitingLeafCrossing = true;
        leafOrigin = current;
        leafLanding = best.getDestination();
        traversalStartedTick = client.getTickCount();
        stationaryTicks = 0;
        Microbot.log("[EfficientWalker][Tirannwn] Jump forward Leaves transportOrigin="
                + candidate.origin + " player=" + current + " expectedLanding=" + leafLanding
                + " logicalDestination=" + target);
        return true;
    }

    private boolean awaitLeafCrossing(WorldPoint current, Player player)
    {
        boolean busy = Rs2Player.isMoving() || player.getAnimation() != -1;
        if (!busy && !current.equals(leafOrigin) && !current.equals(leafLanding))
        {
            TileObject escape = preferredEscapeRock(10);
            if (escape != null)
            {
                WorldPoint target = walker.getLogicalDestination();
                clearLeafCrossing();
                return interact(escape, "Climb", "leaf-trap pit escape", target, current);
            }
        }

        if (current.equals(leafLanding))
        {
            if (busy)
            {
                return true;
            }
            WorldPoint target = walker.getLogicalDestination();
            recentLeafOrigin = leafOrigin;
            recentLeafLanding = leafLanding;
            recentLeafTarget = target;
            clearLeafCrossing();
            lastLocation = current;
            stationaryTicks = 0;
            cooldownTicks = RETRY_COOLDOWN_TICKS;
            if (target != null)
            {
                walker.resumeCommittedTirannwnWalk(target);
                Microbot.log("[EfficientWalker][Tirannwn] Leaves landed at " + current
                        + "; guarding reverse edge " + recentLeafLanding + " -> " + recentLeafOrigin
                        + " and continuing/replanning " + target);
            }
            return true;
        }

        boolean timedOut = client.getTickCount() - traversalStartedTick >= INTERACTION_TIMEOUT_TICKS;
        if (!timedOut || busy)
        {
            return true;
        }

        clearLeafCrossing();
        lastLocation = current;
        stationaryTicks = 0;
        cooldownTicks = RETRY_COOLDOWN_TICKS;
        return true;
    }

    private boolean tryForwardSimpleObstacle(WorldPoint current, WorldPoint target,
                                             String name, String action, String label)
    {
        return tryForwardSimpleObstacle(
                current, target, name, action, label, FORWARD_OBSTACLE_ORIGIN_RADIUS);
    }

    private boolean tryForwardSimpleObstacle(WorldPoint current, WorldPoint target,
                                             String name, String action, String label,
                                             int originRadius)
    {
        ForwardTransport candidate = findForwardTransport(
                current, target, name, action, originRadius);
        if (candidate == null)
        {
            return false;
        }

        if ("Tripwire".equalsIgnoreCase(name))
        {
            WorldPoint landing = candidate.transport.getDestination();

            // A tripwire object is bidirectional even when the selected transport row is not.
            // Interacting with the same object from the landing side traverses it backwards.
            // Live-route shortening can omit the exact obstacle endpoints, so routeDirection() may
            // be unable to prove this and can otherwise re-select origin -> landing while the
            // player is already standing at landing.  Require the player to actually be on the
            // origin side before proactively taking this edge.
            int distanceToOrigin = current.distanceTo2D(candidate.origin);
            int distanceToLanding = current.distanceTo2D(landing);
            if (distanceToOrigin >= distanceToLanding)
            {
                Microbot.log("[EfficientWalker][Tirannwn] ignore Tripwire edge "
                        + candidate.origin + " -> " + landing
                        + "; player already on landing side at " + current);
                return false;
            }

            if (isReverseOfRecentEdge(candidate.origin, landing,
                    recentTripwireOrigin, recentTripwireLanding))
            {
                Microbot.log("[EfficientWalker][Tirannwn] reject reverse Tripwire edge "
                        + candidate.origin + " -> " + landing
                        + "; replanning unchanged destination " + target);
                walker.resumeCommittedTirannwnWalk(target);
                cooldownTicks = RETRY_COOLDOWN_TICKS;
                return true;
            }
        }

        TileObject object = nearestObjectIdAnyType(candidate.transport.getObjectId(), 5);
        if (object == null || !SceneObjects.hasAction(object, action))
        {
            return false;
        }

        Microbot.log("[EfficientWalker][Tirannwn] forward " + name + " candidate origin="
                + candidate.origin + " landing=" + candidate.transport.getDestination()
                + " player=" + current);
        boolean interacted = interact(object, action, label, target, current);
        if (interacted && "Tripwire".equalsIgnoreCase(name))
        {
            recentTripwireOrigin = candidate.origin;
            recentTripwireLanding = candidate.transport.getDestination();
            traversalTripwire = true;
            traversalExpectedLanding = candidate.transport.getDestination();
        }
        return interacted;
    }

    /**
     * Finds a nearby transport that moves in the same general direction as the requested
     * destination.  Tirannwn's live collision map can stop the route one tile short of
     * an obstacle, so requiring transports.get(current) misses valid leaves and lets a minimap
     * click run through the trap.  Searching only a two-tile origin radius keeps this local while
     * the directional test prevents selecting the reverse side after an obstacle was just crossed.
     */
    private ForwardTransport findForwardTransport(WorldPoint current, WorldPoint target,
                                                  String name, String action, int originRadius)
    {
        if (current == null || target == null)
        {
            return null;
        }

        ForwardTransport best = null;
        int bestRouteAuthority = Integer.MIN_VALUE;
        int bestProgress = Integer.MIN_VALUE;
        int bestOriginDistance = Integer.MAX_VALUE;
        int bestTargetDistance = Integer.MAX_VALUE;

        for (int dx = -originRadius; dx <= originRadius; dx++)
        {
            for (int dy = -originRadius; dy <= originRadius; dy++)
            {
                WorldPoint origin = new WorldPoint(
                        current.getX() + dx, current.getY() + dy, current.getPlane());
                int originDistance = origin.distanceTo2D(current);
                if (originDistance > originRadius)
                {
                    continue;
                }

                for (Transport transport : transports.getOrDefault(origin, Set.of()))
                {
                    if (transport == null
                            || !name.equalsIgnoreCase(transport.getName())
                            || !action.equalsIgnoreCase(transport.getAction())
                            || transport.getDestination() == null)
                    {
                        continue;
                    }

                    WorldPoint landing = transport.getDestination();
                    int routeAuthority = routeDirection(origin, landing);
                    if (routeAuthority < 0)
                    {
                        // The active EfficientWalker route contains this edge in the opposite
                        // order. Never override the planner with the nearby reverse transport.
                        continue;
                    }

                    int progress = directionalProgress(origin, landing, target);
                    if (routeAuthority == 0 && progress <= 0)
                    {
                        // Live-route shortening can omit the obstacle endpoints. Only in that
                        // case fall back to the final-target vector as a secondary signal.
                        continue;
                    }

                    int targetDistance = landing.distanceTo2D(target);
                    if (best == null
                            || routeAuthority > bestRouteAuthority
                            || (routeAuthority == bestRouteAuthority && originDistance < bestOriginDistance)
                            || (routeAuthority == bestRouteAuthority && originDistance == bestOriginDistance
                            && progress > bestProgress)
                            || (routeAuthority == bestRouteAuthority && originDistance == bestOriginDistance
                            && progress == bestProgress && targetDistance < bestTargetDistance))
                    {
                        best = new ForwardTransport(origin, transport);
                        bestRouteAuthority = routeAuthority;
                        bestOriginDistance = originDistance;
                        bestProgress = progress;
                        bestTargetDistance = targetDistance;
                    }
                }
            }
        }
        return best;
    }

    private static final class ForwardTransport
    {
        private final WorldPoint origin;
        private final Transport transport;

        private ForwardTransport(WorldPoint origin, Transport transport)
        {
            this.origin = origin;
            this.transport = transport;
        }
    }

    /**
     * @return 1 when the active route contains origin before landing, -1 when it contains the
     * reverse order, and 0 when live-route shortening means the edge cannot be proven either way.
     */
    private int routeDirection(WorldPoint origin, WorldPoint landing)
    {
        List<WorldPoint> path = walker.getRemainingRoute();
        if (path == null || path.isEmpty())
        {
            return 0;
        }
        int originIndex = -1;
        int landingIndex = -1;
        for (int i = 0; i < path.size(); i++)
        {
            WorldPoint point = path.get(i);
            if (originIndex < 0 && origin.equals(point))
            {
                originIndex = i;
            }
            if (landingIndex < 0 && landing.equals(point))
            {
                landingIndex = i;
            }
            if (originIndex >= 0 && landingIndex >= 0)
            {
                break;
            }
        }
        if (originIndex < 0 || landingIndex < 0 || originIndex == landingIndex)
        {
            return 0;
        }
        return landingIndex > originIndex ? 1 : -1;
    }

    private int directionalProgress(WorldPoint origin, WorldPoint landing, WorldPoint target)
    {
        int stepX = landing.getX() - origin.getX();
        int stepY = landing.getY() - origin.getY();
        int targetX = target.getX() - origin.getX();
        int targetY = target.getY() - origin.getY();
        return stepX * targetX + stepY * targetY;
    }

    private TileObject nearestObjectIdAnyType(int objectId, int distance)
    {
        WorldPoint player = client.getLocalPlayer().getWorldLocation();
        return SceneObjects.getAll(object -> object.getId() == objectId, distance).stream()
                .min(Comparator.comparingInt(object -> object.getWorldLocation().distanceTo2D(player)))
                .orElse(null);
    }

    private void clearLeafCrossing()
    {
        awaitingLeafCrossing = false;
        leafOrigin = null;
        leafLanding = null;
    }

    private boolean awaitDenseForest(WorldPoint current, Player player)
    {
        boolean busy = Rs2Player.isMoving() || player.getAnimation() != -1;
        if (current.equals(denseForestLanding))
        {
            if (busy)
            {
                return true;
            }

            // Dense forest is a three-edge corridor.  Once the first edge establishes the
            // direction, every following edge must preserve that exact step vector.  Do not
            // re-decide direction from the final destination on each tree: that is what allowed
            // a westbound run to enter one tree and immediately select the eastbound reverse edge.
            ForwardTransport continuation = findDenseForestContinuation(
                    current, denseForestStepX, denseForestStepY);
            if (continuation != null)
            {
                GameObject forest = nearestObjectId(continuation.transport.getObjectId(), 6);
                if (forest != null && SceneObjects.hasAction(forest, "Enter"))
                {
                    if (SceneObjects.interact(forest, "Enter"))
                    {
                        denseForestOrigin = continuation.origin;
                        denseForestLanding = continuation.transport.getDestination();
                        traversalStartedTick = client.getTickCount();
                        stationaryTicks = 0;
                        Microbot.log("[EfficientWalker][Tirannwn] Continue Dense forest id="
                                + continuation.transport.getObjectId() + " origin=" + continuation.origin
                                + " expectedLanding=" + denseForestLanding + " direction=("
                                + denseForestStepX + "," + denseForestStepY + ")");
                        return true;
                    }
                    cooldownTicks = RETRY_COOLDOWN_TICKS;
                    return true;
                }
            }

            WorldPoint target = walker.getLogicalDestination();
            recentDenseForestOrigin = denseForestOrigin;
            recentDenseForestLanding = denseForestLanding;
            recentDenseForestStepX = denseForestStepX;
            recentDenseForestStepY = denseForestStepY;
            Microbot.log("[EfficientWalker][Tirannwn] Dense forest exited; guarding reverse edge "
                    + recentDenseForestLanding + " -> " + recentDenseForestOrigin);
            clearDenseForestCrossing();
            lastLocation = current;
            stationaryTicks = 0;
            cooldownTicks = RETRY_COOLDOWN_TICKS;

            if (target != null)
            {
                // Replan immediately to erase the engine's stale first-tree transition,
                // but intercept its first post-forest movement click.  Tirannwn's live route can
                // advertise a reachable end ~20-25 tiles away even when that minimap click is not
                // accepted.  The next state picks a <=10 tile point from THIS SAME planned route,
                // walks that short leg with EfficientWalker, then resumes the unchanged target.
                beginPostForestEgress(current, target);
            }
            return true;
        }

        boolean timedOut = client.getTickCount() - traversalStartedTick >= INTERACTION_TIMEOUT_TICKS;
        if (!timedOut || busy)
        {
            return true;
        }

        clearDenseForestCrossing();
        lastLocation = current;
        stationaryTicks = 0;
        cooldownTicks = RETRY_COOLDOWN_TICKS;
        return true;
    }

    private ForwardTransport findDenseForestContinuation(WorldPoint current, int stepX, int stepY)
    {
        if (current == null)
        {
            return null;
        }
        for (Transport transport : transports.getOrDefault(current, Set.of()))
        {
            if (transport == null
                    || transport.getDestination() == null
                    || !"Dense forest".equalsIgnoreCase(transport.getName())
                    || !"Enter".equalsIgnoreCase(transport.getAction()))
            {
                continue;
            }
            WorldPoint landing = transport.getDestination();
            if (landing.getX() - current.getX() == stepX
                    && landing.getY() - current.getY() == stepY)
            {
                return new ForwardTransport(current, transport);
            }
        }
        return null;
    }

    /**
     * Owns only the decision points of the short post-forest leg. Returning false deliberately
     * allows the EfficientWalker engine to receive its normal game tick and perform the
     * actual movement.
     */
    private boolean awaitPostForestEgress(WorldPoint current, Player player)
    {
        WorldPoint finalTarget = postForestFinalTarget;
        if (finalTarget == null)
        {
            clearPostForestEgress();
            return false;
        }

        boolean busy = Rs2Player.isMoving() || player.getAnimation() != -1;
        if (client.getTickCount() - postForestStartedTick >= POST_FOREST_TIMEOUT_TICKS)
        {
            clearPostForestEgress();
            walker.resumeCommittedTirannwnWalk(finalTarget);
            return true;
        }
        if (!busy && tryForwardSimpleObstacle(current, finalTarget, "Tripwire", "Step-over", "tripwire",
                TRIPWIRE_ORIGIN_RADIUS))
        {
            clearPostForestEgress();
            return true;
        }
        if (postForestWaypoint != null)
        {
            boolean waypointReached = current.getPlane() == postForestWaypoint.getPlane()
                    && current.distanceTo2D(postForestWaypoint) <= 1;
            if (waypointReached && !busy)
            {
                WorldPoint reached = postForestWaypoint;
                postForestWaypoint = null;
                walker.resumeCommittedTirannwnWalk(finalTarget);
                Microbot.log("[EfficientWalker][Tirannwn] bounded post-forest waypoint reached "
                        + reached + "; checking the next short approach to " + finalTarget);
                return true;
            }

            if (walker.getStatus() == EfficientWalker.Status.BLOCKED)
            {
                clearPostForestEgress();
                walker.resumeCommittedTirannwnWalk(finalTarget);
                Microbot.log("[EfficientWalker][Tirannwn] bounded post-forest waypoint timed out; "
                        + "resuming final destination " + finalTarget);
                return true;
            }

            return false;
        }

        EfficientWalker.Status status = walker.getStatus();
        if (status == EfficientWalker.Status.PLANNING || status == EfficientWalker.Status.PREVIEW)
        {
            return false;
        }

        if (status == EfficientWalker.Status.WALKING)
        {
            WorldPoint waypoint = selectBoundedRouteWaypoint(current);
            if (waypoint != null)
            {
                postForestWaypoint = waypoint;
                if (walker.walkCommittedTirannwnWaypoint(waypoint, finalTarget))
                {
                    Microbot.log("[EfficientWalker][Tirannwn] limiting first post-forest route leg "
                            + current + " -> " + waypoint + " before resuming " + finalTarget);
                    return true;
                }
                postForestWaypoint = null;
            }

            if (plannedRouteBacktracksAcrossRecentForest(current))
            {
                walker.resumeCommittedTirannwnWalk(finalTarget);
                Microbot.log("[EfficientWalker][Tirannwn] rejected post-forest route that points "
                        + "back across the forest; replanning " + finalTarget);
                return true;
            }

            // If the planned path is already local enough, no special leg is necessary.
            clearPostForestEgress();
            return false;
        }

        if (client.getTickCount() - postForestStartedTick >= POST_FOREST_TIMEOUT_TICKS)
        {
            clearPostForestEgress();
        }
        return false;
    }

    private WorldPoint selectBoundedRouteWaypoint(WorldPoint current)
    {
        List<WorldPoint> path = walker.getRemainingRoute();
        if (path == null || path.isEmpty() || current == null)
        {
            return null;
        }

        int anchor = -1;
        int anchorDistance = Integer.MAX_VALUE;
        for (int i = 0; i < path.size(); i++)
        {
            WorldPoint point = path.get(i);
            if (point == null || point.getPlane() != current.getPlane())
            {
                continue;
            }
            int distance = point.distanceTo2D(current);
            if (distance < anchorDistance)
            {
                anchor = i;
                anchorDistance = distance;
            }
        }
        if (anchor < 0)
        {
            return null;
        }

        Set<WorldPoint> transportTiles = walker.getTransportEndpoints();
        WorldPoint best = null;
        WorldPoint fallback = null;
        for (int i = anchor + 1; i < path.size(); i++)
        {
            WorldPoint point = path.get(i);
            if (point == null || point.getPlane() != current.getPlane())
            {
                continue;
            }
            int distance = point.distanceTo2D(current);
            if (distance <= 0)
            {
                continue;
            }
            if (isBehindRecentDenseForestExit(point))
            {
                continue;
            }
            if (distance > POST_FOREST_MAX_ROUTE_STEP)
            {
                if (best != null || fallback != null)
                {
                    break;
                }
                continue;
            }
            if (transportTiles == null || !transportTiles.contains(point))
            {
                fallback = point;
                if (distance >= POST_FOREST_MIN_ROUTE_STEP)
                {
                    best = point;
                }
            }
        }
        return best != null ? best : fallback;
    }

    private void updateCrossedEdgeGuards(WorldPoint current)
    {
        if (current == null)
        {
            return;
        }

        if (recentTripwireLanding != null
                && (current.getPlane() != recentTripwireLanding.getPlane()
                || current.distanceTo2D(recentTripwireLanding) > CROSSED_EDGE_GUARD_CLEAR_DISTANCE))
        {
            recentTripwireOrigin = null;
            recentTripwireLanding = null;
        }

        if (recentLeafLanding != null
                && (current.getPlane() != recentLeafLanding.getPlane()
                || current.distanceTo2D(recentLeafLanding) > CROSSED_EDGE_GUARD_CLEAR_DISTANCE))
        {
            recentLeafOrigin = null;
            recentLeafLanding = null;
            recentLeafTarget = null;
        }

        if (recentDenseForestLanding != null
                && (current.getPlane() != recentDenseForestLanding.getPlane()
                || current.distanceTo2D(recentDenseForestLanding) > CROSSED_EDGE_GUARD_CLEAR_DISTANCE))
        {
            recentDenseForestOrigin = null;
            recentDenseForestLanding = null;
            recentDenseForestStepX = 0;
            recentDenseForestStepY = 0;
        }
    }

    private boolean isReverseOfRecentEdge(WorldPoint origin, WorldPoint landing,
                                          WorldPoint recentOrigin, WorldPoint recentLanding)
    {
        return origin != null && landing != null && recentOrigin != null && recentLanding != null
                && origin.equals(recentLanding) && landing.equals(recentOrigin);
    }

    private boolean plannedRouteBacktracksAcrossRecentForest(WorldPoint current)
    {
        if (current == null || recentDenseForestLanding == null)
        {
            return false;
        }
        List<WorldPoint> path = walker.getRemainingRoute();
        if (path == null || path.isEmpty())
        {
            return false;
        }
        for (WorldPoint point : path)
        {
            if (point == null || point.getPlane() != current.getPlane())
            {
                continue;
            }
            int distance = point.distanceTo2D(current);
            if (distance > 0 && distance <= POST_FOREST_MAX_ROUTE_STEP
                    && isBehindRecentDenseForestExit(point))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isBehindRecentDenseForestExit(WorldPoint point)
    {
        if (point == null || recentDenseForestLanding == null
                || point.getPlane() != recentDenseForestLanding.getPlane())
        {
            return false;
        }
        int dx = point.getX() - recentDenseForestLanding.getX();
        int dy = point.getY() - recentDenseForestLanding.getY();
        return dx * recentDenseForestStepX + dy * recentDenseForestStepY < 0;
    }

    private void clearDenseForestCrossing()
    {
        awaitingDenseForest = false;
        denseForestOrigin = null;
        denseForestLanding = null;
        denseForestStepX = 0;
        denseForestStepY = 0;
    }

    private void clearPostForestEgress()
    {
        awaitingPostForestEgress = false;
        postForestFinalTarget = null;
        postForestWaypoint = null;
        postForestStartedTick = 0;
    }

    private GameObject nearestObjectId(int objectId, int distance)
    {
        WorldPoint player = client.getLocalPlayer().getWorldLocation();
        return SceneObjects.getGameObjects(object -> object.getId() == objectId, distance).stream()
                .min(Comparator.comparingInt(object -> object.getWorldLocation().distanceTo2D(player)))
                .orElse(null);
    }

    private void updateStationary(WorldPoint current)
    {
        if (current.equals(lastLocation))
        {
            stationaryTicks++;
        }
        else
        {
            lastLocation = current;
            stationaryTicks = 1;
        }
    }

    private boolean isTirannwnRoute(WorldPoint target, WorldPoint current)
    {
        return isTirannwnSurface(target) || isTirannwnSurface(current);
    }

    private boolean isTirannwnSurface(WorldPoint point)
    {
        return point != null && point.getPlane() == 0
                && point.getX() >= 2100 && point.getX() <= 2400
                && point.getY() >= 3050 && point.getY() <= 3350;
    }

    private void reset()
    {
        walker.releaseTirannwnWalkingCommit();
        lastLocation = null;
        stationaryTicks = 0;
        cooldownTicks = 0;
        awaitingTraversal = false;
        traversalStart = null;
        traversalTarget = null;
        traversalPitEscape = false;
        traversalTripwire = false;
        traversalExpectedLanding = null;
        traversalObjectLocation = null;
        rejectedEscapeRock = null;
        recentTripwireOrigin = null;
        recentTripwireLanding = null;
        recentLeafOrigin = null;
        recentLeafLanding = null;
        recentLeafTarget = null;
        recentDenseForestOrigin = null;
        recentDenseForestLanding = null;
        recentDenseForestStepX = 0;
        recentDenseForestStepY = 0;
        observedNativeForest = null;
        observedNativeForestTarget = null;
        clearDenseForestCrossing();
        clearPostForestEgress();
        clearLeafCrossing();
    }
}
