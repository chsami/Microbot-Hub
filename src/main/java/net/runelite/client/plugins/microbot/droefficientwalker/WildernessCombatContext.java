package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Scoped routing permission and combat-only threat response owned by one script. */
public final class WildernessCombatContext implements AutoCloseable
{
    private final Consumer<Boolean> combatChanged;
    private final Runnable release;
    private volatile boolean closed;
    private volatile boolean logoutWhenOutOfCombat;
    private volatile BooleanSupplier nearbyLogoutSuppressed = () -> false;
    private volatile BooleanSupplier quietPvpResumeAllowed = () -> false;
    private final BooleanSupplier current;

    WildernessCombatContext(Consumer<Boolean> combatChanged, Runnable release, BooleanSupplier current)
    {
        this.combatChanged = combatChanged;
        this.release = release;
        this.current = current;
    }

    public boolean isActive() { return !closed && current.getAsBoolean(); }

    public synchronized void setCombatActive(boolean active)
    {
        if (!closed) combatChanged.accept(active);
    }

    public synchronized void setLogoutWhenOutOfCombat(boolean enabled)
    {
        if (!closed) logoutWhenOutOfCombat = enabled;
    }

    boolean prefersLogoutWhenOutOfCombat() { return isActive() && logoutWhenOutOfCombat; }

    /** Only nearby-player presence is exempted; the caller is evaluated on the watcher client tick. */
    public synchronized void suppressNearbyPlayerLogoutWhile(BooleanSupplier confirmedActivityArea)
    {
        if (!closed) nearbyLogoutSuppressed = java.util.Objects.requireNonNull(confirmedActivityArea);
    }

    boolean suppressesNearbyPlayerLogout()
    { return isActive() && nearbyLogoutSuppressed.getAsBoolean(); }

    /** Explicit task-area opt-in; actual player attack activity still takes precedence. */
    public synchronized void allowQuietPvpResumeWhile(BooleanSupplier confirmedActivityArea)
    {
        if (!closed) quietPvpResumeAllowed = java.util.Objects.requireNonNull(confirmedActivityArea);
    }

    boolean allowsQuietPvpResume()
    { return isActive() && quietPvpResumeAllowed.getAsBoolean(); }

    @Override public synchronized void close()
    {
        if (closed) return;
        closed = true;
        release.run();
    }
}
