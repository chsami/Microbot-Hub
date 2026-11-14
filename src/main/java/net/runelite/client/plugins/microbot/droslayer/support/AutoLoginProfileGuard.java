package net.runelite.client.plugins.microbot.droslayer.support;

import java.util.function.BooleanSupplier;

/**
 * Hub build stub. The droplugins Microbot client lets a Dro profile hold Auto Login during smart
 * breaks; the released Microbot client has no such check, so this does nothing.
 */
public final class AutoLoginProfileGuard {
    public AutoLoginProfileGuard(BooleanSupplier waitingForBreak) {
    }

    public void start() {
    }

    public void shutdown() {
    }
}
