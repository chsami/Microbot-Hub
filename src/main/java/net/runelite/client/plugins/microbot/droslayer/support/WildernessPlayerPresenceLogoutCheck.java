package net.runelite.client.plugins.microbot.droslayer.support;

/** Synchronous client-thread query for an active activity's confirmed safe presence area.
 * This veto applies only to Walker's nearby-player logout, never PvP, health or escape actions.
 */
public final class WildernessPlayerPresenceLogoutCheck {
    private boolean suppressed;

    public void suppressNearbyPlayerLogout() { suppressed = true; }
    public boolean isSuppressed() { return suppressed; }
}
