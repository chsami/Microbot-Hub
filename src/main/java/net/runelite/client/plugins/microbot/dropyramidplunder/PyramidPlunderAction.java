package net.runelite.client.plugins.microbot.dropyramidplunder;

/** Receipt/settling gate, polled by the script worker; never sleeps on the client thread. */
final class PyramidPlunderAction {
    enum Result { WAIT, COMPLETE, RETRY, TIMEOUT }

    /** A received action whose player stays busy (a mummy fight, a looping animation) still completes. */
    static final long RECEIPT_BUSY_MS = 3_000;
    static final long TIMEOUT_MS = 30_000;
    /** Consecutive unsettled actions recovered in place before the activity reports a failure. */
    static final int MAX_RECOVERIES = 4;

    /** Urns: idle time after a failed search before searching again (other actions settle for 600 ms). */
    static final long QUICK_IDLE_MS = 300;
    /** Urns: the next urn counts as following a loot, and skips the working pause, within one game tick. */
    static final long URN_CHAIN_MS = 600;

    private final long startedAt;
    private final boolean quick;
    private long lastBusyAt;
    private long receivedAt = -1;

    PyramidPlunderAction(long now) {
        this(now, false);
    }

    /** {@code quick}: an urn; its experience drop completes it at once and a failed search retries sooner. */
    PyramidPlunderAction(long now, boolean quick) {
        startedAt = now;
        lastBusyAt = now;
        this.quick = quick;
    }

    Result poll(long now, boolean busy, boolean receipt) {
        if (receipt && receivedAt < 0) receivedAt = now;
        boolean received = receivedAt >= 0;
        // A looted urn is done; the next urn can be clicked without waiting for the animation to settle.
        if (quick && received) return Result.COMPLETE;
        if (busy) lastBusyAt = now;
        long idle = quick ? QUICK_IDLE_MS : 600;
        if (!busy && now - lastBusyAt >= idle && (received || now - startedAt >= 1200)) {
            return received ? Result.COMPLETE : Result.RETRY;
        }
        // The game already changed; staying busy afterwards is not this action.
        if (received && now - receivedAt >= RECEIPT_BUSY_MS) return Result.COMPLETE;
        return now - startedAt >= TIMEOUT_MS ? Result.TIMEOUT : Result.WAIT;
    }

    /** An experience drop since the click: the urn was looted, whatever its object still shows. */
    static boolean xpGained(int xp, int xpAtClick) {
        return xpAtClick > 0 && xp > xpAtClick;
    }

    static boolean chained(long now, long lootedAt) {
        return lootedAt > 0 && now - lootedAt < URN_CHAIN_MS;
    }

    static boolean changed(int state, int original) {
        return state >= 0 && state != original;
    }

    static boolean needsFood(int food, int threshold) {
        return food <= Math.max(0, threshold);
    }
}
