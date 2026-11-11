package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.lang.reflect.Method;

/**
 * Single-owner integration with Efficient Walker.
 *
 * A destination is submitted once and then polled through Efficient Walker's public facade. Dro Slayer
 * does not repeatedly resubmit the same route while Efficient Walker is planning, coordinating a bank
 * teleport, or actively walking. After Dro Slayer performs a hard-coded dungeon entrance
 * interaction, the interior destination is submitted back through this same facade by default.
 */
final class DroSlayerEfficientWalkerBridge {
    private static final String PLUGIN_CLASS = DroSlayerWalkerRequirement.PLUGIN_CLASS;

    @SuppressWarnings("unused")
    private final ConfigManager configManager;

    private AutoCloseable wildernessContext;
    private Object contextIdentity;
    private Plugin plugin;
    private Object walker;
    private Method walkTo;
    private Method getStatus;
    private Method getNavigationPhase;
    private Method getPlanningFailure;
    private Method cancel;

    private WorldPoint activeTarget;
    private int activeRadius;
    private DroSlayerTravelMode activeMode;
    private WorldPoint lastObservedLocation;
    private long lastProgressAt;
    private long nextLookupAt;
    private long nextUnavailableLogAt;
    private long lastUnavailableFailureAt;
    private int consecutiveFailures;
    private volatile boolean stopped;

    DroSlayerEfficientWalkerBridge(ConfigManager configManager) {
        // Kept in the constructor signature so DroSlayerRuntime does not need another lifecycle
        // dependency. Dro Slayer deliberately does not rewrite any Efficient Walker configuration.
        this.configManager = configManager;
    }

    boolean tickTo(WorldPoint target, int arrivalRadius, DroSlayerTravelMode requestedMode, int stallSeconds) {
        if (target == null || stopped || Thread.currentThread().isInterrupted()) return false;

        if (ensureWalker()) {
            try {
                Method resolver = walker.getClass().getMethod("resolveDestination", WorldPoint.class);
                target = (WorldPoint) resolver.invoke(walker, target);
            } catch (NoSuchMethodException ignored) {
                // Older walker versions retain their original destination contract.
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

        if (!ensureWalker()) {
            recordUnavailableFailure();
            logUnavailableThrottled("Efficient Walker is unavailable");
            return false;
        }

        if (activeTarget != null
                && (!target.equals(activeTarget) || mode != activeMode || radius != activeRadius)) {
            finishRoute(false, mode != activeMode ? "travel policy changed" : "destination changed");
        }

        if (activeTarget != null) {
            observeProgress(here);
            String status = status();

            // Efficient Walker's public navigation phase keeps BANK_* ownership visible while its bank
            // coordinator is opening a bank, withdrawing a teleport, or committing the route.
            // Never cancel that in-flight coordinator merely because the preserved walking engine
            // is temporarily idle underneath it.
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
                Microbot.log("[DroSlayer] Efficient Walker blocked target=" + target
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
                Microbot.log("[DroSlayer] Efficient Walker stalled target=" + target
                        + " mode=" + mode + " status=" + status);
                finishRoute(false, "stalled");
            }
            return false;
        }

        Object requestedWalker;
        Method requestMethod;
        synchronized (this) {
            if (stopped || Thread.currentThread().isInterrupted() || walker == null) return false;
            requestedWalker = walker;
            requestMethod = walkTo;
            // Own the pending request before invoking: shutdown must cancel even when
            // walkTo is still gathering its client-thread planning snapshot.
            activeTarget = target;
            activeRadius = radius;
            activeMode = mode;
            lastObservedLocation = here;
            lastProgressAt = System.currentTimeMillis();
        }
        try {
            Object accepted = requestMethod.invoke(requestedWalker, target);
            if (stopped || Thread.currentThread().isInterrupted()) return false;
            if (Boolean.FALSE.equals(accepted)) {
                consecutiveFailures++;
                Microbot.log("[DroSlayer] Efficient Walker rejected target=" + target
                        + " mode=" + mode + " failure=" + planningFailure());
                finishRoute(false, "request rejected");
                return false;
            }

            Microbot.log("[DroSlayer] Efficient Walker route -> " + target + " mode=" + mode);
            return false;
        } catch (Exception ex) {
            if (stopped || Thread.currentThread().isInterrupted()) return false;
            consecutiveFailures++;
            finishRoute(false, "request failed");
            Microbot.log("[DroSlayer] Efficient Walker request failed: " + rootMessage(ex));
            clearWalkerOnly();
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
        if (walker == null || getStatus == null) return "UNAVAILABLE";
        try {
            if (getNavigationPhase != null) {
                return String.valueOf(getNavigationPhase.invoke(walker));
            }
            return String.valueOf(getStatus.invoke(walker));
        } catch (Exception ignored) {
            try {
                return String.valueOf(getStatus.invoke(walker));
            } catch (Exception ignoredAgain) {
                return "UNKNOWN";
            }
        }
    }

    String planningFailure() {
        if (walker == null || getPlanningFailure == null) return "UNAVAILABLE";
        try {
            return String.valueOf(getPlanningFailure.invoke(walker));
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
        if (!ensureWalker()) return;
        try {
            boolean liveContext = wildernessContext != null && contextIdentity == plugin
                    && Boolean.TRUE.equals(wildernessContext.getClass().getMethod("isActive").invoke(wildernessContext));
            if (!liveContext) {
                releaseWildernessContext();
                wildernessContext = (AutoCloseable) plugin.getClass()
                        .getMethod("acquireWildernessContext", String.class).invoke(plugin, "DroSlayer-Krystilia");
                contextIdentity = plugin;
            }
            wildernessContext.getClass().getMethod("setCombatActive", boolean.class).invoke(wildernessContext, combatActive);
            try {
                wildernessContext.getClass().getMethod("setLogoutWhenOutOfCombat", boolean.class)
                        .invoke(wildernessContext, preferLogout);
            } catch (NoSuchMethodException ignored) {
                // Older Walker versions retain their existing combat response.
            }
        } catch (Exception ex) {
            logUnavailableThrottled("Wilderness Walker context unavailable: " + rootMessage(ex));
        }
    }

    void releaseWildernessContext() {
        if (wildernessContext != null) {
            try { wildernessContext.close(); }
            catch (Exception ex) { Microbot.log("[DroSlayer] Wilderness context release failed: " + rootMessage(ex)); }
            wildernessContext = null;
            contextIdentity = null;
        }
    }

    boolean isWildernessPlayerAttackActive() {
        if (plugin == null || wildernessContext == null) return false;
        try { return Boolean.TRUE.equals(plugin.getClass().getMethod("isWildernessPlayerAttackActive").invoke(plugin)); }
        catch (Exception ignored) { return false; }
    }

    String wildernessEscapeState() {
        if (plugin == null || wildernessContext == null) return "IDLE";
        try { return String.valueOf(plugin.getClass().getMethod("getWildernessCombatEscapeState").invoke(plugin)); }
        catch (Exception ex) { return "UNAVAILABLE"; }
    }

    void acknowledgeWildernessEscape() {
        if (plugin == null) return;
        try { plugin.getClass().getMethod("acknowledgeWildernessCombatEscape").invoke(plugin); }
        catch (Exception ex) { Microbot.log("[DroSlayer] Wilderness escape acknowledgement failed: " + rootMessage(ex)); }
    }

    void shutdown() {
        synchronized (this) { stopped = true; }
        finishRoute(false, "shutdown");
        clearWalkerOnly();
        plugin = null;
    }

    private void observeProgress(WorldPoint here) {
        if (here == null) return;
        if (lastObservedLocation == null || !here.equals(lastObservedLocation)) {
            lastObservedLocation = here;
            lastProgressAt = System.currentTimeMillis();
        }
    }

    private void finishRoute(boolean success, String reason) {
        if (walker != null && cancel != null && activeTarget != null) {
            try {
                cancel.invoke(walker);
            } catch (Exception ignored) {
            }
        }

        if (activeTarget != null) {
            Microbot.log("[DroSlayer] Efficient Walker release target=" + activeTarget
                    + " mode=" + activeMode + " reason=" + reason);
        }

        activeTarget = null;
        activeRadius = 0;
        activeMode = null;
        lastObservedLocation = null;
        lastProgressAt = 0L;
        if (success) consecutiveFailures = 0;
    }

    private boolean ensureWalker() {
        if (stopped || Thread.currentThread().isInterrupted()) return false;
        if (walker != null && plugin != null && Microbot.isPluginEnabled(plugin.getClass())) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (now < nextLookupAt) return false;
        nextLookupAt = now + 1500L;

        try {
            plugin = findPlugin();
            if (plugin == null) {
                clearWalkerOnly();
                return false;
            }

            // Slayer never turns Efficient Walker on by itself; the user enables it.
            if (!Microbot.isPluginEnabled(plugin.getClass())) {
                clearWalkerOnly();
                return false;
            }

            Method getWalker = plugin.getClass().getMethod("getWalker");
            Object candidate = getWalker.invoke(plugin);
            if (candidate == null) {
                clearWalkerOnly();
                return false;
            }

            walker = candidate;
            Class<?> type = candidate.getClass();
            walkTo = type.getMethod("walkTo", WorldPoint.class);
            getStatus = type.getMethod("getStatus");
            try {
                getNavigationPhase = type.getMethod("getNavigationPhase");
            } catch (NoSuchMethodException ignored) {
                getNavigationPhase = null;
            }
            getPlanningFailure = type.getMethod("getPlanningFailure");
            cancel = type.getMethod("cancel");
            return true;
        } catch (Exception ex) {
            logUnavailableThrottled("Efficient Walker API unavailable: " + rootMessage(ex));
            clearWalkerOnly();
            return false;
        }
    }

    private Plugin findPlugin() {
        try {
            for (Plugin candidate : Microbot.getPluginManager().getPlugins()) {
                if (candidate != null && PLUGIN_CLASS.equals(candidate.getClass().getName())) {
                    return candidate;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
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

    private void clearWalkerOnly() {
        releaseWildernessContext();
        walker = null;
        walkTo = null;
        getStatus = null;
        getNavigationPhase = null;
        getPlanningFailure = null;
        cancel = null;
        activeTarget = null;
        activeRadius = 0;
        activeMode = null;
        lastObservedLocation = null;
        lastProgressAt = 0L;
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
}
