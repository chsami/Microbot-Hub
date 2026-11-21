package net.runelite.client.plugins.microbot.drocolosseumprayers;

import java.util.ArrayDeque;

/**
 * Limits how often an ended decision session is replaced by a fresh one. One late frame should not
 * end prayer switching for the run, but a client that cannot keep up must not restart forever.
 */
final class ArenaSessionRestarts {
    static final int LIMIT = 3;
    static final long WINDOW_MILLIS = 60_000L;

    private final ArrayDeque<Long> recent = new ArrayDeque<>();

    /** Only failures of the decision loop itself are retried; a blocked action is a deliberate stop. */
    static boolean recoverable(String lastEnd) {
        return lastEnd != null && (lastEnd.contains("Decision ") || lastEnd.contains("snapshot capture failed"));
    }

    /** True, and counted, when another restart is allowed now. */
    boolean allow(long nowMillis) {
        while (!recent.isEmpty() && nowMillis - recent.peekFirst() >= WINDOW_MILLIS) recent.removeFirst();
        if (recent.size() >= LIMIT) return false;
        recent.addLast(nowMillis);
        return true;
    }

    void clear() { recent.clear(); }
}
