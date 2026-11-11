package net.runelite.client.plugins.microbot.drocolosseumprayers;

/**
 * Decides when a plugin that stopped itself may start again without the player toggling it.
 * A stop is per run, not for good: a finished run re-arms once the player is outside, a lost
 * connection re-arms on the next reliable logged-in tick, and a lost arena reading re-arms as soon
 * as the arena is seen again.
 */
final class ArenaRearm {
    enum After {
        /** Stopped for a reason the player has to fix; only a plugin restart resumes. */
        NEVER,
        /** The run is over (death or completion): wait until the player has left. */
        OUTSIDE,
        /** The arena was no longer seen: resume at once if it is seen again, else once settled outside. */
        ARENA_SEEN_OR_OUTSIDE,
        /** The game was not logged in: resume on the next reliable tick, wherever the player is. */
        LOGGED_IN
    }

    /** A single outside reading can be a region reload, so a finished run needs a few in a row. */
    static final int OUTSIDE_TICKS = 3;

    private After after = After.NEVER;
    private int outsideTicks;

    void stopped(After mode) { after = mode; outsideTicks = 0; }
    void clear() { stopped(After.NEVER); }
    After pending() { return after; }

    /** Called once per game tick while stopped; true means restart now. */
    boolean due(boolean reliable, boolean inside) {
        if (after == After.NEVER || !reliable) return false;
        if (after == After.LOGGED_IN) return true;
        if (inside) { outsideTicks = 0; return after == After.ARENA_SEEN_OR_OUTSIDE; }
        return ++outsideTicks >= OUTSIDE_TICKS;
    }
}
