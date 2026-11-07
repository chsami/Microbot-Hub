package net.runelite.client.plugins.microbot.scurrius;

import java.util.Random;

/** Pure decisions for the breaks taken after restock trips. */
public final class Breaks {
    private Breaks() {
    }

    /** Restock trips (completed bank visits) before the next break: uniform in [min, max], at least 1, swapped range fixed. */
    public static int rollTripTarget(int min, int max, Random random) {
        return RestockTrips.rollTarget(min, max, random);
    }

    /**
     * Break length in milliseconds: uniform over whole seconds between min and max minutes (so minutes plus random
     * seconds), at least one minute, swapped range fixed.
     */
    public static long rollLengthMs(int minMinutes, int maxMinutes, Random random) {
        int low = Math.max(1, Math.min(minMinutes, maxMinutes)) * 60;
        int high = Math.max(1, Math.max(minMinutes, maxMinutes)) * 60;
        return (low + random.nextInt(high - low + 1)) * 1000L;
    }

    /**
     * Whether to start a break now. Called right after a bank visit: enough trips done, not already on a break, never in
     * the boss room, and never while Microbot's own break handler is on a break (it owns the login then).
     */
    public static boolean shouldBreakNow(boolean enabled, int tripsDone, int target, boolean onBreak, boolean inFightRoom,
                                         boolean globalBreakActive) {
        return enabled && !onBreak && target > 0 && tripsDone >= target && !inFightRoom && !globalBreakActive;
    }

    /** mm:ss for the overlay, rounded up to the next second, never negative. */
    public static String formatRemaining(long remainingMs) {
        long seconds = Math.max(0, (remainingMs + 999) / 1000);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
