package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.client.plugins.microbot.droefficientwalker.sailing.core.SeaPoint;

import java.util.function.BooleanSupplier;

/**
 * Navigation integration contract exposed by the enabled EfficientSailing plugin.
 * Request acceptance is asynchronous; resume caller input only after status.released.
 * See docs/efficient-sailing-integration.md for the salvager handoff recipe.
 */
public interface SailingNavigationService {
    NavigationSessions.Caller registerCaller(Object pluginIdentity, BooleanSupplier inputSuspended);
    NavigationSessions.Result request(NavigationSessions.Caller caller, SeaPoint destination);
    NavigationSessions.Snapshot status(NavigationSessions.Session session);
    boolean cancel(NavigationSessions.Session session);
    boolean callerShutdown(NavigationSessions.Caller caller);
}
