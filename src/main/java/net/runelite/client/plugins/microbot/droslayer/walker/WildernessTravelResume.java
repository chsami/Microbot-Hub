package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;

/** Keeps intent through Walker's own safety logout, never through an explicit cancellation. */
final class WildernessTravelResume {
    private WorldPoint target;
    private long pausedAt;
    private boolean loggedOut;
    synchronized WorldPoint target() { return target; }
    synchronized boolean acceptRequest(WorldPoint destination) {
        if (target == null) return false;
        if (target.equals(destination)) return true;
        reset();
        return false;
    }
    synchronized void pause(WorldPoint destination, long now) {
        if (target != null || destination == null) return;
        target = destination; pausedAt = now; loggedOut = false;
    }
    synchronized void observe(GameState state) {
        if (target != null && (state == GameState.LOGIN_SCREEN || state == GameState.LOGIN_SCREEN_AUTHENTICATOR)) loggedOut = true;
    }
    synchronized boolean canResume(boolean loginReady, boolean threat, long now) {
        // If logout was rejected and the threat leaves, release the paused journey too.
        return target != null && loginReady && !threat && (loggedOut || now - pausedAt >= 8000L);
    }
    synchronized WorldPoint take() { WorldPoint result = target; reset(); return result; }
    synchronized void reset() { target = null; pausedAt = 0; loggedOut = false; }
}
