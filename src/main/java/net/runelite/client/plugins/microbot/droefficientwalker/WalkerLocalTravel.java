package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Local route restrictions are runtime leases, never persisted user preferences. */
final class WalkerLocalTravel
{
    private static final Set<String> DISABLED = Set.of(
        "useTeleportations", "useItemsFromBank", "useFairyRings", "useSpiritTrees",
        "useMinigameTeleports", "useCharterShips", "useHotAirBalloons",
        "useWildernessTeleports", "useWildernessLevers", "useWildernessObelisks");
    private final Set<Object> leases = Collections.newSetFromMap(new IdentityHashMap<>());
    private volatile boolean active;

    synchronized Runnable acquire()
    {
        Object identity = new Object();
        leases.add(identity);
        active = true;
        return () -> release(identity);
    }

    private synchronized void release(Object identity)
    {
        leases.remove(identity);
        active = !leases.isEmpty();
    }

    synchronized void clear()
    {
        leases.clear();
        active = false;
    }

    EfficientWalkerConfig effectiveConfig(EfficientWalkerConfig saved)
    {
        return (EfficientWalkerConfig) Proxy.newProxyInstance(EfficientWalkerConfig.class.getClassLoader(),
            new Class<?>[]{EfficientWalkerConfig.class}, (proxy, method, args) ->
            {
                if (active && DISABLED.contains(method.getName())) return false;
                try { return method.invoke(saved, args); }
                catch (InvocationTargetException error) { throw error.getCause(); }
            });
    }
}
