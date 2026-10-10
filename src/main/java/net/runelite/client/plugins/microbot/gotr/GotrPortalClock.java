package net.runelite.client.plugins.microbot.gotr;

import java.time.Duration;
import java.time.Instant;

public final class GotrPortalClock {
    static final int FIRST_PORTAL_ADJUSTMENT_SECONDS = 40;
    static final int STALE_AFTER_SECONDS = 180;

    private Instant lastPortalEvent;
    private boolean firstPortal = true;

    public synchronized void roundStarted(Instant now) {
        lastPortalEvent = now;
        firstPortal = true;
    }

    public synchronized void portalSpawned(Instant now) {
        lastPortalEvent = now;
        firstPortal = false;
    }

    public synchronized void portalDespawned(Instant now) {
        if (lastPortalEvent != null) {
            lastPortalEvent = now;
        }
    }

    public synchronized void reset() {
        lastPortalEvent = null;
        firstPortal = true;
    }

    public synchronized boolean isFirstPortal() {
        return firstPortal;
    }

    public synchronized int secondsSincePortal(Instant now) {
        if (lastPortalEvent == null) {
            return -1;
        }
        long elapsed = Duration.between(lastPortalEvent, now).getSeconds();
        if (elapsed < 0 || elapsed > STALE_AFTER_SECONDS) {
            return -1;
        }
        return (int) elapsed - (firstPortal ? FIRST_PORTAL_ADJUSTMENT_SECONDS : 0);
    }
}
