package net.runelite.client.plugins.microbot.droslayer.walker;

/** Bounded retry policy for a house exit that may finish after the scene transition. */
final class HousePortalRecoveryPolicy {
    private static final int MAX_TICKS = 110;
    private static final int IN_FLIGHT_GRACE_TICKS = 25;
    private static final int MAX_ATTEMPTS = 6;
    private static final int RETRY_INTERVAL_TICKS = 10;

    private HousePortalRecoveryPolicy() { }

    static boolean expired(int elapsedTicks, boolean actionPending) {
        return elapsedTicks > MAX_TICKS + (actionPending ? IN_FLIGHT_GRACE_TICKS : 0);
    }

    static boolean shouldAttempt(int attempts, int sinceLastAttemptTicks) {
        return attempts < MAX_ATTEMPTS && (attempts == 0 || sinceLastAttemptTicks >= RETRY_INTERVAL_TICKS);
    }
}
