package net.runelite.client.plugins.microbot.droslayer;

/**
 * Why the script is waiting on the player, so the overlay never just says "Idle" or "Banking" while
 * nothing happens. A hold belongs to the state it was raised in and ends when the state changes. A hold
 * raised in a state that keeps working (banking retries) also ends when it is not raised again.
 */
final class DroSlayerHold {
    /** Banking rechecks supplies every 10 seconds; a hold not raised again within this long is over. */
    static final long REFRESH_MILLIS = 15_000L;

    private static String reason;
    private static String action;
    private static DroSlayerState heldIn;
    private static long raisedAt;
    private static String announced;

    private DroSlayerHold() {
    }

    /**
     * @param reason what is missing, in the player's words
     * @param action what the player should do about it
     * @return the one chat line to show, or null when this hold was already announced
     */
    static synchronized String raise(DroSlayerState state, String reason, String action, long now) {
        DroSlayerHold.reason = reason;
        DroSlayerHold.action = action;
        heldIn = state;
        raisedAt = now;
        String line = "[Dro] AIO Slayer is waiting: " + reason + ". " + action;
        if (line.equals(announced)) return null;
        announced = line;
        return line;
    }

    static synchronized void stateChanged(DroSlayerState state) {
        if (reason != null && state != heldIn) clear();
    }

    static synchronized void clear() {
        reason = null;
        action = null;
        heldIn = null;
        announced = null;
    }

    /** The reason to draw in the overlay header, or null when the script is not waiting on the player. */
    static synchronized String current(DroSlayerState state, long now) {
        if (reason == null || state != heldIn) return null;
        if (state != DroSlayerState.IDLE && now - raisedAt > REFRESH_MILLIS) return null;
        return reason;
    }

    static synchronized String currentAction(DroSlayerState state, long now) {
        return current(state, now) == null ? null : action;
    }
}
