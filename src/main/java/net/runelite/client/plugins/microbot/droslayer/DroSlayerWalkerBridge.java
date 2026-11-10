package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.walker.DroSlayerWalkerHost;
import net.runelite.client.plugins.microbot.droslayer.walker.EfficientWalker;
import net.runelite.client.plugins.microbot.droslayer.walker.WildernessCombatContext;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Single-owner integration with Dro Slayer's own walker ({@code droslayer.walker}).
 *
 * A destination is submitted once and then polled. Dro Slayer does not repeatedly resubmit the
 * same route while the walker is planning, coordinating a bank teleport, or actively walking.
 * After Dro Slayer performs a hard-coded dungeon entrance interaction, the interior destination
 * is submitted back through this same bridge.
 */
final class DroSlayerWalkerBridge {
    /** What the bridge needs from the walker; tests supply fakes. */
    interface Engine {
        boolean available();
        WorldPoint resolveDestination(WorldPoint target);
        boolean walkTo(WorldPoint target);
        /** Navigation phase: engine status, or BANK_* / PAUSED_* while a coordinator owns it. */
        String status();
        String planningFailure();
        void cancel();
        Lease acquireWildernessContext(String owner);
        boolean isWildernessPlayerAttackActive();
        String wildernessEscapeState();
        void acknowledgeWildernessEscape();
    }

    /** One Wilderness ownership lease. */
    interface Lease extends AutoCloseable {
        boolean isActive();
        void setCombatActive(boolean active);
        void setLogoutWhenOutOfCombat(boolean enabled);
        @Override void close();
    }

    private Engine engine;
    private Lease wildernessContext;

    private WorldPoint activeTarget;
    private int activeRadius;
    private DroSlayerTravelMode activeMode;
    private WorldPoint lastObservedLocation;
    private long lastProgressAt;
    private long nextUnavailableLogAt;
    private long lastUnavailableFailureAt;
    private int consecutiveFailures;
    private volatile boolean stopped;

    DroSlayerWalkerBridge(DroSlayerWalkerHost host) {
        this(host == null ? null : hostEngine(host));
    }

    DroSlayerWalkerBridge(Engine engine) {
        this.engine = engine;
    }

    boolean tickTo(WorldPoint target, int arrivalRadius, DroSlayerTravelMode requestedMode, int stallSeconds) {
        if (target == null || stopped || Thread.currentThread().isInterrupted()) return false;

        if (engineAvailable()) {
            try {
                target = engine.resolveDestination(target);
            } catch (Exception ex) {
                Microbot.log("[DroSlayer] Could not resolve destination: " + rootMessage(ex));
                return false;
            }
        }

        final int radius = Math.max(0, arrivalRadius);
        final DroSlayerTravelMode mode = requestedMode == null
                ? DroSlayerTravelMode.FULL_TRAVEL
                : requestedMode;
        WorldPoint here = Rs2Player.getWorldLocation();

        if (within(here, target, radius)) {
            finishRoute(true, "arrived");
            return true;
        }

        if (!engineAvailable()) {
            recordUnavailableFailure();
            logUnavailableThrottled("Walker is unavailable");
            return false;
        }

        if (activeTarget != null
                && (!target.equals(activeTarget) || mode != activeMode || radius != activeRadius)) {
            finishRoute(false, mode != activeMode ? "travel policy changed" : "destination changed");
        }

        if (activeTarget != null) {
            observeProgress(here);
            String status = status();

            // The navigation phase keeps BANK_* ownership visible while the bank coordinator is
            // opening a bank, withdrawing a teleport, or committing the route. Never cancel that
            // in-flight coordinator merely because the walking engine is idle underneath it.
            if (status != null && (status.startsWith("BANK_") || status.startsWith("PAUSED_"))) {
                lastProgressAt = System.currentTimeMillis();
                return false;
            }

            if ("ARRIVED".equalsIgnoreCase(status)) {
                boolean closeEnough = within(here, target, Math.max(radius, 2));
                finishRoute(closeEnough, "walker arrived");
                return closeEnough;
            }

            if ("BLOCKED".equalsIgnoreCase(status)) {
                consecutiveFailures++;
                Microbot.log("[DroSlayer] Walker blocked target=" + target
                        + " mode=" + mode + " failure=" + planningFailure());
                finishRoute(false, "blocked");
                return false;
            }

            if ("IDLE".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
                consecutiveFailures++;
                finishRoute(false, "walker terminal " + status);
                return false;
            }

            long timeoutMs = Math.max(15, stallSeconds) * 1000L;
            if (lastProgressAt > 0 && System.currentTimeMillis() - lastProgressAt > timeoutMs) {
                consecutiveFailures++;
                Microbot.log("[DroSlayer] Walker stalled target=" + target
                        + " mode=" + mode + " status=" + status);
                finishRoute(false, "stalled");
            }
            return false;
        }

        Engine requested;
        synchronized (this) {
            if (stopped || Thread.currentThread().isInterrupted() || engine == null) return false;
            requested = engine;
            // Own the pending request before invoking: shutdown must cancel even when
            // walkTo is still gathering its client-thread planning snapshot.
            activeTarget = target;
            activeRadius = radius;
            activeMode = mode;
            lastObservedLocation = here;
            lastProgressAt = System.currentTimeMillis();
        }
        try {
            boolean accepted = requested.walkTo(target);
            if (stopped || Thread.currentThread().isInterrupted()) return false;
            if (!accepted) {
                consecutiveFailures++;
                Microbot.log("[DroSlayer] Walker rejected target=" + target
                        + " mode=" + mode + " failure=" + planningFailure());
                finishRoute(false, "request rejected");
                return false;
            }

            Microbot.log("[DroSlayer] Walker route -> " + target + " mode=" + mode);
            return false;
        } catch (Exception ex) {
            if (stopped || Thread.currentThread().isInterrupted()) return false;
            consecutiveFailures++;
            finishRoute(false, "request failed");
            Microbot.log("[DroSlayer] Walker request failed: " + rootMessage(ex));
            return false;
        }
    }

    int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    void clearFailureBudget() {
        consecutiveFailures = 0;
    }

    boolean isRouteActive() {
        return activeTarget != null;
    }

    DroSlayerTravelMode activeMode() {
        return activeMode;
    }

    String status() {
        if (!engineAvailable()) return "UNAVAILABLE";
        try {
            return String.valueOf(engine.status());
        } catch (Exception ignored) {
            return "UNKNOWN";
        }
    }

    String planningFailure() {
        if (!engineAvailable()) return "UNAVAILABLE";
        try {
            return String.valueOf(engine.planningFailure());
        } catch (Exception ignored) {
            return "UNKNOWN";
        }
    }

    void cancel() {
        finishRoute(false, "cancelled by Dro Slayer");
    }

    void setWildernessContext(boolean enabled, boolean combatActive) {
        setWildernessContext(enabled, combatActive, false);
    }

    void setWildernessContext(boolean enabled, boolean combatActive, boolean preferLogout) {
        if (!enabled) { releaseWildernessContext(); return; }
        if (!engineAvailable()) return;
        try {
            if (wildernessContext == null || !wildernessContext.isActive()) {
                releaseWildernessContext();
                wildernessContext = engine.acquireWildernessContext("DroSlayer-Krystilia");
            }
            wildernessContext.setCombatActive(combatActive);
            wildernessContext.setLogoutWhenOutOfCombat(preferLogout);
        } catch (Exception ex) {
            logUnavailableThrottled("Wilderness walker context unavailable: " + rootMessage(ex));
        }
    }

    void releaseWildernessContext() {
        if (wildernessContext != null) {
            try { wildernessContext.close(); }
            catch (Exception ex) { Microbot.log("[DroSlayer] Wilderness context release failed: " + rootMessage(ex)); }
            wildernessContext = null;
        }
    }

    boolean isWildernessPlayerAttackActive() {
        if (engine == null || wildernessContext == null) return false;
        try { return engine.isWildernessPlayerAttackActive(); }
        catch (Exception ignored) { return false; }
    }

    String wildernessEscapeState() {
        if (engine == null || wildernessContext == null) return "IDLE";
        try { return String.valueOf(engine.wildernessEscapeState()); }
        catch (Exception ex) { return "UNAVAILABLE"; }
    }

    void acknowledgeWildernessEscape() {
        if (engine == null) return;
        try { engine.acknowledgeWildernessEscape(); }
        catch (Exception ex) { Microbot.log("[DroSlayer] Wilderness escape acknowledgement failed: " + rootMessage(ex)); }
    }

    void shutdown() {
        synchronized (this) { stopped = true; }
        finishRoute(false, "shutdown");
        releaseWildernessContext();
        clearRoute();
        engine = null;
    }

    private boolean engineAvailable() {
        if (stopped || Thread.currentThread().isInterrupted() || engine == null) return false;
        try { return engine.available(); }
        catch (Exception ex) { return false; }
    }

    private void observeProgress(WorldPoint here) {
        if (here == null) return;
        if (lastObservedLocation == null || !here.equals(lastObservedLocation)) {
            lastObservedLocation = here;
            lastProgressAt = System.currentTimeMillis();
        }
    }

    private void finishRoute(boolean success, String reason) {
        if (engine != null && activeTarget != null) {
            try {
                engine.cancel();
            } catch (Exception ignored) {
            }
        }

        if (activeTarget != null) {
            Microbot.log("[DroSlayer] Walker release target=" + activeTarget
                    + " mode=" + activeMode + " reason=" + reason);
        }

        clearRoute();
        if (success) consecutiveFailures = 0;
    }

    private void clearRoute() {
        activeTarget = null;
        activeRadius = 0;
        activeMode = null;
        lastObservedLocation = null;
        lastProgressAt = 0L;
    }

    private void recordUnavailableFailure() {
        long now = System.currentTimeMillis();
        if (now - lastUnavailableFailureAt >= 5_000L) {
            lastUnavailableFailureAt = now;
            consecutiveFailures++;
        }
    }

    private void logUnavailableThrottled(String message) {
        long now = System.currentTimeMillis();
        if (now >= nextUnavailableLogAt) {
            nextUnavailableLogAt = now + 10_000L;
            Microbot.log("[DroSlayer] " + message);
        }
    }

    private static boolean within(WorldPoint a, WorldPoint b, int radius) {
        if (a == null || b == null || a.getPlane() != b.getPlane()) return false;
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())) <= radius;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current == null ? null : current.getMessage();
        return message == null || message.isBlank()
                ? (current == null ? "unknown" : current.getClass().getSimpleName())
                : message;
    }

    /** Engine backed by Dro Slayer's own walker host. */
    static Engine hostEngine(DroSlayerWalkerHost host) {
        return new Engine() {
            private EfficientWalker walker() {
                EfficientWalker walker = host.getWalker();
                if (walker == null) throw new IllegalStateException("Dro Slayer walker is stopped");
                return walker;
            }
            @Override public boolean available() { return host.getWalker() != null; }
            @Override public WorldPoint resolveDestination(WorldPoint target) { return walker().resolveDestination(target); }
            @Override public boolean walkTo(WorldPoint target) { return walker().walkTo(target); }
            @Override public String status() { return walker().getNavigationPhase(); }
            @Override public String planningFailure() { return String.valueOf(walker().getPlanningFailure()); }
            @Override public void cancel() { EfficientWalker walker = host.getWalker(); if (walker != null) walker.cancel(); }
            @Override public Lease acquireWildernessContext(String owner) {
                WildernessCombatContext context = host.acquireWildernessContext(owner);
                return new Lease() {
                    @Override public boolean isActive() { return context.isActive(); }
                    @Override public void setCombatActive(boolean active) { context.setCombatActive(active); }
                    @Override public void setLogoutWhenOutOfCombat(boolean enabled) { context.setLogoutWhenOutOfCombat(enabled); }
                    @Override public void close() { context.close(); }
                };
            }
            @Override public boolean isWildernessPlayerAttackActive() { return host.isWildernessPlayerAttackActive(); }
            @Override public String wildernessEscapeState() { return host.getWildernessCombatEscapeState(); }
            @Override public void acknowledgeWildernessEscape() { host.acknowledgeWildernessCombatEscape(); }
        };
    }
}
