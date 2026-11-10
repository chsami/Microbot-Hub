package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.walker.sailing.core.SeaPoint;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Direct, scoped land journey owned by Sailing; ordinary Walker requests keep their engine. */
final class SailingWalkerJourney {
    private final Consumer<String> diagnostic;
    private final EfficientWalker walker;
    private volatile WorldPoint ownedDestination;
    private volatile boolean ownBankRoute, yielded;
    private volatile BooleanSupplier permitted = () -> false;
    void permit(BooleanSupplier permission) { permitted = permission; }

    static final class Estimate {
        final WorldPoint approach; final int cost; final String chain;
        Estimate(WorldPoint approach, int cost, String chain) { this.approach = approach; this.cost = cost; this.chain = chain; }
    }
    static final class JourneyState {
        final String status, phase, failure;
        final WorldPoint destination;
        JourneyState(String status, String phase, String failure, WorldPoint destination) {
            this.status = status; this.phase = phase; this.failure = failure; this.destination = destination;
        }
    }
    SailingWalkerJourney(EfficientWalker walker, Consumer<String> diagnostic) {
        this.walker = Objects.requireNonNull(walker, "walker");
        this.diagnostic = diagnostic;
    }
    boolean connect() { return true; }
    String version() { return EfficientWalker.PORTED_VERSION; }
    String status() { return walker.getStatus().name(); }
    String phase() { return walker.getNavigationPhase(); }
    WorldPoint destination() {
        // A bank journey tracks its new engine request, not the preceding dock's logical target.
        return ownBankRoute ? walker.getDestination() : walker.getLogicalDestination();
    }
    boolean busy() { return busyState(status()); }
    private static boolean busyState(String state) {
        return state.equals("PLANNING") || state.equals("PREVIEW") || state.equals("WALKING");
    }
    boolean ownsRoute() { return !yielded && (ownedDestination != null || ownBankRoute); }
    void userTookOver() { yielded = true; ownedDestination = null; ownBankRoute = false; }
    boolean ownershipIntact() {
        return ownsRoute() && ownershipIntact(observe());
    }
    boolean walkTo(WorldPoint target) { return walkTo(target, false); }
    /** Only for a dock approach just verified reachable in the loaded scene. */
    boolean walkToPreparedLocal(WorldPoint target) { return walkTo(target, true); }
    private boolean walkTo(WorldPoint target, boolean preparedLocal) {
        final BooleanSupplier permission = permitted;
        if (!permission.getAsBoolean()) return false;
        // The maintained facade refreshes/scans the transport graph synchronously before
        // starting its planner. Keep that work on Sailing's worker; only its own bounded
        // client reads are marshalled by the maintained Walker implementation.
        if (target == null) return false;
        target = walker.resolveDestination(target);
        JourneyState state = observe();
        if (busyState(state.status) && !ownsRoute()) throw new IllegalStateException("Efficient Walker already has another journey");
        if (ownsRoute() && !ownershipIntact(state)) throw new IllegalStateException("Efficient Walker destination was replaced");
        if (ownsRoute() && target.equals(ownedDestination) && busyState(state.status)) return true;
        if (!permission.getAsBoolean()) return false;
        yielded = false; ownBankRoute = false; ownedDestination = target;
        boolean accepted = preparedLocal ? walker.walkToWithoutBankSupplies(target) : walker.walkTo(target);
        if (!accepted) { ownedDestination = null; return false; }
        diagnostic.accept("walker-request accepted target=" + target + " (not arrival)");
        return true;
    }
    boolean walkToBank() {
        final BooleanSupplier permission = permitted;
        if (!permission.getAsBoolean()) return false;
        JourneyState state = observe();
        if (busyState(state.status) && !ownsRoute()) throw new IllegalStateException("Efficient Walker already owns another journey");
        if (ownsRoute() && !ownershipIntact(state)) throw new IllegalStateException("Efficient Walker destination was replaced");
        if (!permission.getAsBoolean()) return false;
        yielded = false; ownBankRoute = true; ownedDestination = null;
        boolean accepted = walker.walkToNearestBank();
        if (accepted) ownedDestination = destination();
        else ownBankRoute = false;
        return accepted;
    }
    void releaseCompleted() { ownedDestination = null; ownBankRoute = false; yielded = false; }
    void cancelOwned() {
        try {
            Microbot.getClientThread().runOnClientThreadOptional(() -> {
                // Revalidate next to dispatch on the client thread so another route cannot
                // replace ours between a worker observation and the cancellation.
                if (ownsRoute() && ownershipIntact()) {
                    walker.cancel(); diagnostic.accept("walker-cancel own journey only");
                }
                return true;
            });
        } finally { releaseCompleted(); }
    }
    String planningFailure() { return walker.getPlanningFailure().name(); }

    /** Keep these reads within one client turn; asynchronous planner completion can still race. */
    JourneyState observe() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            String state = status();
            return new JourneyState(state, phase(), state.equals("BLOCKED") ? planningFailure() : "NONE", destination());
        }).orElseThrow(() -> new IllegalStateException("Walker state is temporarily unavailable"));
    }
    boolean ownershipIntact(JourneyState state) {
        if (!ownsRoute()) return false;
        if (ownBankRoute && ownedDestination == null && state.destination != null) ownedDestination = state.destination;
        return WalkerJourneyPolicy.ownershipIntact(ownBankRoute, ownedDestination, state.destination, state.status);
    }
    boolean arrivedOwned(JourneyState state, WorldPoint player) {
        return ownsRoute() && WalkerJourneyPolicy.arrivedOwned(ownBankRoute, ownedDestination,
                state.destination, state.status, player);
    }

    /** A dock object is an interaction target, not a tile to occupy. No journey is started here. */
    WorldPoint dockApproach(WorldPoint player, WorldPoint gangplank) {
        if (player == null || gangplank == null || player.getPlane() != gangplank.getPlane()) return null;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            EfficientWalker.CollisionProbe collision = walker.navigationCollision();
            if (collision == null) return null;
            Map<WorldPoint, Integer> reachable = Rs2Tile.getReachableTilesFromTile(player, 40);
            return WalkerJourneyPolicy.dockApproach(gangplank, reachable, collision::standable);
        }).orElse(null);
    }

    /** Live local reachability can verify a loaded dock without a global transport comparison. */
    Estimate estimateLoadedDock(WorldPoint player, WorldPoint gangplank) {
        if (gangplank == null || player == null || player.getPlane() != gangplank.getPlane()
                || player.distanceTo2D(gangplank) > 36) return null;
        WorldPoint approach = dockApproach(player, gangplank);
        if (approach == null) return null;
        // This estimate only ranks destination-nearest/fixed choices; it is not a journey ETA.
        return new Estimate(approach, player.distanceTo2D(approach) * 2, "loaded reachable dock");
    }

    /** Run only on the dedicated planning worker, never on the client thread. */
    Estimate estimateNearestDock(SeaPoint seaAnchor,WorldPoint start,WorldPoint gateway,BooleanSupplier cancelled) {
        if(busy())throw new IllegalStateException("Walker must be idle during port selection");
        check(cancelled);
        WorldPoint approach=gateway;
        if(approach!=null) {
            EfficientWalker.CollisionProbe collision=walker.navigationCollision();
            if(collision==null)throw new IllegalStateException("Efficient Walker collision planner is not ready");
            if(!collision.standable(approach))return null;
        } else {
            List<WorldPoint> shore=shoreCandidates(new WorldPoint(seaAnchor.x,seaAnchor.y,0),cancelled);
            if(shore.isEmpty())return null;
            approach=shore.get(0);
        }
        check(cancelled);
        // Closest-to-target uses sea distance only. The maintained Walker plans
        // and verifies its own land journey after accepting the selected dock.
        return new Estimate(approach,0,"land approach; route planned by Efficient Walker during travel");
    }

    /** Full cost comparison remains available to integrations which explicitly need it. */
    Estimate estimate(SeaPoint seaAnchor, WorldPoint start, WorldPoint override, BooleanSupplier cancelled) {
        if (busy()) throw new IllegalStateException("Walker must be idle during port comparison");
        check(cancelled);
        WorldPoint sea = new WorldPoint(seaAnchor.x, seaAnchor.y, 0);
        WeightedRouteAdvisor advisor = walker.navigationAdvisor(sea);
        List<WorldPoint> approaches = override != null ? Collections.singletonList(override) : shoreCandidates(sea, cancelled);
        int tried = 0;
        for (WorldPoint approach : approaches) {
            check(cancelled);
            if (busy()) throw new IllegalStateException("Walker was started by another caller during comparison");
            if (++tried > 3) break;
            WeightedRouteAdvisor.Advice advice = advisor.analyze(start, approach, true);
            if (advice.available()) return new Estimate(approach, advice.bestCost, advice.bestChain);
        }
        return null;
    }
    private List<WorldPoint> shoreCandidates(WorldPoint sea, BooleanSupplier cancelled) {
        EfficientWalker.CollisionProbe collision = walker.navigationCollision();
        if (collision == null) throw new IllegalStateException("Efficient Walker collision planner is not ready");
        List<WorldPoint> candidates = new ArrayList<>();
        for (int radius = 1; radius <= 36 && candidates.size() < 12; radius++) {
            check(cancelled);
            for (int dx = -radius; dx <= radius; dx++) for (int dy = -radius; dy <= radius; dy++) {
                if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
                WorldPoint point = sea.dx(dx).dy(dy);
                if (!collision.standable(point)) continue;
                int exits = 0;
                for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) {
                    if ((x != 0 || y != 0) && collision.canStep(point, point.dx(x).dy(y))) exits++;
                }
                if (exits >= 2) candidates.add(point);
            }
        }
        candidates.sort(Comparator.comparingInt(p -> p.distanceTo2D(sea)));
        if (candidates.isEmpty()) diagnostic.accept("No Walker-verified land approach near sea anchor " + sea);
        return candidates;
    }
    private static void check(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
    }
}
