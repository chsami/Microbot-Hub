package net.runelite.client.plugins.microbot.droelf;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ObjectComposition;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Self-contained Prifddinas route planner/executor.
 *
 * No Efficient Walker plugin/JAR is used at runtime. The embedded map is used
 * for global planning, while the loaded scene is consulted just before taking
 * the next edge. Live collision wins when it disagrees with the static map.
 */
@Slf4j
final class DroElfPrifWalker {
    private static final int ARRIVAL_DISTANCE = 2;
    private static final int MIN_LOOKAHEAD = 7;
    private static final int MAX_LOOKAHEAD = 11;
    private static final int MAX_ROUTE_ITERATIONS = 40;
    private static final long MOVE_START_TIMEOUT_MS = 1_500L;
    private static final long WAYPOINT_TIMEOUT_MS = 5_500L;

    /**
     * Run must stay off for this long before we click the orb. A run click from anywhere else
     * (the client's auto-run, another script, the player) shows up within a game tick, so
     * waiting past two ticks means we never click on top of a toggle that is already on its way.
     */
    private static final long RUN_OFF_SETTLE_MS = 1_300L;
    /** Wait after our own run click before trying again, so a slow varp update can't double-toggle. */
    private static final long RUN_RETRY_MS = 2_500L;
    private static volatile long lastRunRequestAt;

    private final DroElfPrifPathfinder pathfinder = new DroElfPrifPathfinder();

    /**
     * Turns run back on before a walk click when the game turned it off (energy ran out and has
     * since regenerated). Reads the live run state first: if run is already on — whether the
     * Microbot client's own auto-run, another script or the player turned it on — nothing is
     * clicked, so this never toggles run back off. When run is off it watches for
     * {@link #RUN_OFF_SETTLE_MS}, and returns without clicking if anything else turns it on in
     * that time. Energy at or below the client's run threshold is left alone, and after its own
     * click it waits {@link #RUN_RETRY_MS} so a pending toggle is not repeated.
     */
    static void restoreRunIfOff() {
        try {
            if (!Microbot.isLoggedIn() || Rs2Player.isRunEnabled()) return;
            if (System.currentTimeMillis() - lastRunRequestAt < RUN_RETRY_MS) return;
            if (Rs2Player.getRunEnergy() * 100 <= Math.max(0, Microbot.runEnergyThreshold)) return;
            // Give the client's own auto-run (or anything else) the chance to turn it on first.
            if (sleepUntil(Rs2Player::isRunEnabled, (int) RUN_OFF_SETTLE_MS)) return;
            if (Rs2Player.isRunEnabled()) return;
            lastRunRequestAt = System.currentTimeMillis();
            Rs2Player.toggleRunEnergy(true);
        } catch (Exception ex) {
            log.debug("[DroElf][PrifWalker] run restore skipped: {}", ex.getMessage());
        }
    }

    boolean supports(WorldPoint point) {
        return DroElfPrifCollisionMap.supports(point);
    }

    boolean walkTo(
            WorldPoint requestedTarget,
            BooleanSupplier keepRunning,
            Runnable beforeAction,
            Consumer<String> statusConsumer) {
        return walkTo(requestedTarget, keepRunning, beforeAction, statusConsumer, ARRIVAL_DISTANCE);
    }

    /**
     * Same route executor with a caller-selected arrival radius. Existing callers retain
     * the original 2-tile behavior; LOS recovery can request an exact positioning tile.
     */
    boolean walkTo(
            WorldPoint requestedTarget,
            BooleanSupplier keepRunning,
            Runnable beforeAction,
            Consumer<String> statusConsumer,
            int arrivalDistance) {
        if (requestedTarget == null || !supports(requestedTarget)) return false;
        final int acceptedArrivalDistance = Math.max(0, arrivalDistance);

        final Set<Long> blockedEdges = new HashSet<>();
        int iterations = 0;

        while (keepRunning.getAsBoolean() && Microbot.isLoggedIn() && iterations++ < MAX_ROUTE_ITERATIONS) {
            final WorldPoint player = Rs2Player.getWorldLocation();
            if (player == null || player.getPlane() != requestedTarget.getPlane() || !supports(player)) return false;
            if (player.distanceTo(requestedTarget) <= acceptedArrivalDistance) return true;

            final List<WorldPoint> path = pathfinder.findPath(player, requestedTarget, blockedEdges);
            if (path.size() < 2) {
                log.debug("[DroElf][PrifWalker] no path {} -> {}, blockedEdges={}",
                        player, requestedTarget, blockedEdges.size());
                return false;
            }

            statusConsumer.accept("Prif route");
            log.debug("[DroElf][PrifWalker] plan {} -> {} tiles={} replan={}",
                    player, requestedTarget, path.size(), iterations - 1);

            // Closed doors are represented as normally traversable route edges in the
            // static map. Open one only when it actually lies on the next path segment.
            if (openRelevantDoor(path, beforeAction, statusConsumer)) {
                continue;
            }

            // This is the key protection against shipped-map/live-scene disagreement:
            // validate only the immediate next edge in the loaded scene. If live
            // collision says no, blacklist that edge and plan around it.
            if (isImmediateEdgeBlockedLive(path.get(0), path.get(1))) {
                final long edge = DroElfPrifPathfinder.edgeKey(path.get(0), path.get(1));
                blockedEdges.add(edge);
                log.debug("[DroElf][PrifWalker] live collision blocked {} -> {}; replanning",
                        path.get(0), path.get(1));
                continue;
            }

            final int maxStep = Math.min(MAX_LOOKAHEAD, path.size() - 1);
            final int minStep = Math.min(MIN_LOOKAHEAD, maxStep);
            final int step = minStep == maxStep
                    ? maxStep
                    : ThreadLocalRandom.current().nextInt(minStep, maxStep + 1);
            final WorldPoint destination = path.get(step);
            final int beforeTargetDistance = player.distanceTo(requestedTarget);

            statusConsumer.accept("Walking Prif route");
            if (beforeAction != null) beforeAction.run();
            restoreRunIfOff();

            boolean clicked = Rs2Walker.walkMiniMap(destination);
            if (!clicked) clicked = Rs2Walker.walkFastCanvas(destination);
            if (!clicked) {
                blockedEdges.add(DroElfPrifPathfinder.edgeKey(path.get(0), path.get(1)));
                continue;
            }

            final long moveStart = System.currentTimeMillis();
            boolean startedMoving = false;
            while (keepRunning.getAsBoolean()
                    && System.currentTimeMillis() - moveStart < MOVE_START_TIMEOUT_MS) {
                final WorldPoint now = Rs2Player.getWorldLocation();
                if (now == null) return false;
                if (now.distanceTo(destination) <= 2 || now.distanceTo(requestedTarget) <= acceptedArrivalDistance) {
                    startedMoving = true;
                    break;
                }
                if (Rs2Player.isMoving()) {
                    startedMoving = true;
                    break;
                }
                sleep(40);
            }

            if (!startedMoving) {
                // If the next static edge looked valid but the click did nothing, the
                // first edge is the most useful thing to invalidate for this plan.
                blockedEdges.add(DroElfPrifPathfinder.edgeKey(path.get(0), path.get(1)));
                continue;
            }

            // Do not wait for a full stop. Re-evaluate as soon as we are close to the
            // waypoint, which keeps long Prif trips flowing naturally.
            final long waypointStart = System.currentTimeMillis();
            int bestTargetDistance = beforeTargetDistance;
            long lastProgressAt = waypointStart;

            while (keepRunning.getAsBoolean()
                    && System.currentTimeMillis() - waypointStart < WAYPOINT_TIMEOUT_MS) {
                final WorldPoint now = Rs2Player.getWorldLocation();
                if (now == null) return false;
                if (now.distanceTo(requestedTarget) <= acceptedArrivalDistance) return true;
                if (now.distanceTo(destination) <= 2) break;

                final int targetDistance = now.distanceTo(requestedTarget);
                if (targetDistance < bestTargetDistance) {
                    bestTargetDistance = targetDistance;
                    lastProgressAt = System.currentTimeMillis();
                }

                if (!Rs2Player.isMoving() && System.currentTimeMillis() - lastProgressAt > 450L) break;
                if (System.currentTimeMillis() - lastProgressAt > 2_500L) break;
                sleep(45);
            }

            final WorldPoint after = Rs2Player.getWorldLocation();
            if (after == null) return false;

            if (after.distanceTo(requestedTarget) >= beforeTargetDistance
                    && after.distanceTo(destination) > 3) {
                blockedEdges.add(DroElfPrifPathfinder.edgeKey(path.get(0), path.get(1)));
            }

        }

        return Rs2Player.getWorldLocation() != null
                && Rs2Player.getWorldLocation().distanceTo(requestedTarget) <= acceptedArrivalDistance;
    }

    private boolean openRelevantDoor(
            List<WorldPoint> path,
            Runnable beforeAction,
            Consumer<String> statusConsumer) {
        final WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return false;

        final int inspect = Math.min(path.size() - 1, 12);
        final Rs2TileObjectModel door = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getRs2TileObjectCache().query()
                        .within(player, 6)
                        .where(this::hasOpenAction)
                        .toList().stream()
                        .filter(object -> {
                            final WorldPoint doorPoint = object.getWorldLocation();
                            if (doorPoint == null) return false;
                            for (int i = 1; i <= inspect; i++) {
                                if (doorPoint.distanceTo(path.get(i)) <= 1) return true;
                            }
                            return false;
                        })
                        .min(Comparator.comparingInt(object -> object.getWorldLocation().distanceTo(player)))
                        .orElse(null)
        ).orElse(null);

        if (door == null) return false;

        statusConsumer.accept("Opening Prif route door");
        if (beforeAction != null) beforeAction.run();

        final WorldPoint doorPoint = door.getWorldLocation();
        if (!door.click("Open")) return false;

        final long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 1_800L) {
            if (!hasClosedDoorNear(doorPoint)) break;
            sleep(50);
        }
        sleep(ThreadLocalRandom.current().nextInt(60, 181));
        return true;
    }

    private boolean hasClosedDoorNear(WorldPoint point) {
        if (point == null) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getRs2TileObjectCache().query()
                        .within(point, 1)
                        .where(this::hasOpenAction)
                        .toList()
                        .size() > 0
        ).orElse(false);
    }

    private boolean hasOpenAction(Rs2TileObjectModel object) {
        if (object == null) return false;
        ObjectComposition composition = object.getObjectComposition();
        if (composition == null) return false;
        if (composition.getImpostorIds() != null && composition.getImpostor() != null) {
            composition = composition.getImpostor();
        }

        final String name = composition.getName();
        final String[] actions = composition.getActions();
        if (name == null || actions == null || !name.toLowerCase().contains("door")) return false;

        for (String action : actions) {
            if (action != null && action.equalsIgnoreCase("Open")) return true;
        }
        return false;
    }

    private boolean isImmediateEdgeBlockedLive(WorldPoint from, WorldPoint to) {
        if (from == null || to == null || from.getPlane() != to.getPlane()) return false;
        final int dx = Integer.compare(to.getX(), from.getX());
        final int dy = Integer.compare(to.getY(), from.getY());

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            final WorldView worldView = Microbot.getClient().getTopLevelWorldView();
            if (worldView == null || worldView.getPlane() != from.getPlane()) return false;

            // Null means that edge is outside the currently loaded scene, so the static
            // Prif graph remains authoritative for now.
            final LocalPoint fromLocal = LocalPoint.fromWorld(worldView, from);
            final LocalPoint toLocal = LocalPoint.fromWorld(worldView, to);
            if (fromLocal == null || toLocal == null) return false;

            return !from.toWorldArea().canTravelInDirection(worldView, dx, dy);
        }).orElse(false);
    }
}
