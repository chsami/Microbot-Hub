package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/** Script ownership changes effective routing only; saved Walker preferences are never written. */
final class WildernessThreatOwnership
{
    private volatile WildernessCombatContext active;
    private volatile boolean combatActive;
    private volatile boolean southEscape;

    synchronized WildernessCombatContext acquire(String owner)
    {
        if (owner == null || owner.trim().isEmpty()) throw new IllegalArgumentException("Owner is required");
        if (active != null) throw new IllegalStateException("Wilderness combat response already owned");
        final WildernessCombatContext[] identity = new WildernessCombatContext[1];
        WildernessCombatContext lease = new WildernessCombatContext(enabled -> setCombat(identity[0], enabled),
            () -> release(identity[0]), () -> active == identity[0]);
        identity[0] = lease;
        active = lease;
        southEscape = "DroSlayer-Krystilia".equals(owner);
        combatActive = false;
        return lease;
    }

    private synchronized void setCombat(WildernessCombatContext lease, boolean enabled)
    {
        if (active == lease) combatActive = enabled;
    }

    private synchronized void release(WildernessCombatContext lease)
    {
        if (active != lease) return;
        combatActive = false;
        active = null;
    }

    boolean isCombatActive() { return active != null && combatActive; }
    boolean usesSouthEscape() { return isCombatActive() && southEscape; }
    boolean prefersLogoutWhenOutOfCombat() {
        WildernessCombatContext lease = active;
        return isCombatActive() && lease != null && lease.prefersLogoutWhenOutOfCombat();
    }
    boolean isOwned() { return active != null; }
    boolean suppressesNearbyPlayerLogout() {
        WildernessCombatContext lease = active;
        return lease != null && lease.suppressesNearbyPlayerLogout();
    }
    boolean allowsQuietPvpResume() {
        WildernessCombatContext lease = active;
        return lease != null && lease.allowsQuietPvpResume();
    }
    synchronized void clear() { combatActive = false; active = null; }

    EfficientWalkerConfig effectiveConfig(EfficientWalkerConfig delegate)
    {
        return (EfficientWalkerConfig) Proxy.newProxyInstance(EfficientWalkerConfig.class.getClassLoader(),
            new Class<?>[]{EfficientWalkerConfig.class}, (proxy, method, args) ->
            {
                if ("allowWildernessTravel".equals(method.getName()) && isOwned()) return true;
                try { return method.invoke(delegate, args); }
                catch (InvocationTargetException ex) { throw ex.getCause(); }
            });
    }
}
