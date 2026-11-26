package net.runelite.client.plugins.microbot.irkedmlm;

/** Pure decisions about breaks, stop goals and logging back in, kept apart so they can be checked without a client. */
final class MlmRunControl {
    private MlmRunControl() {}

    /**
     * A break may start unless pay-dirt is on the floor (being dropped, or waiting to be picked back up)
     * or a hopper, sack or repair trip is under way. Mining itself is always safe to walk away from.
     */
    static boolean safeForBreak(boolean dropping, boolean pileWaiting, boolean tripsIdle) {
        return !dropping && !pileWaiting && tripsIdle;
    }

    /** The goal reached, or null. 0 turns a goal off; play time is logged-in time, so breaks do not count. */
    static String goalReached(int stopAfterMinutes, long activeMs, int stopAtLevel, int miningLevel) {
        if (stopAtLevel > 0 && miningLevel >= stopAtLevel) return "reached " + stopAtLevel + " Mining";
        if (stopAfterMinutes > 0 && activeMs >= stopAfterMinutes * 60_000L) return "played for " + stopAfterMinutes + " minutes";
        return null;
    }

    /** How long a player takes to notice the login screen: five to forty seconds, mostly short. */
    static long reloginWaitMs(double roll) {
        return 5_000 + (long) (roll * roll * 35_000);
    }

    /** The gap after a failed login, longer each try, so a server down for an update is not hammered. */
    static long reloginGapMs(int attempt, double roll) {
        return (long) ((8_000 + roll * 7_000) * Math.max(1, Math.min(attempt, 8)));
    }

    /** Hard resets allowed with nothing working in between; after that the run stops instead of cycling. */
    static final int MAX_HARD_RESETS = 2;

    static boolean recoveryExhausted(int hardResetsSinceProgress) {
        return hardResetsSinceProgress >= MAX_HARD_RESETS;
    }

    /** Two to four minutes off a wheel we keep failing to repair, and long enough for the frozen hop when that is on. */
    static long repairBackoffMs(boolean hopWhenFrozen, long hopGraceSeconds, double roll) {
        long base = 120_000 + (long) (roll * 120_000);
        return hopWhenFrozen ? Math.max(base, hopGraceSeconds * 1000 + 30_000) : base;
    }

    /** Times the wheel may beat us (each time three failed repairs and a backoff) before the run stops. */
    static final int MAX_REPAIR_ROUNDS = 3;

    static boolean repairRoundsExhausted(int roundsSinceWheelTurned) {
        return roundsSinceWheelTurned >= MAX_REPAIR_ROUNDS;
    }

    /** A repair that ended with the water still frozen failed, whatever it called itself, unless it was left to someone else. */
    static boolean repairFailed(boolean sessionFailed, boolean leftToBystander, boolean stillFrozen) {
        return sessionFailed || (stillFrozen && !leftToBystander);
    }

    /** Active time with nothing working before the run stops. */
    static final long NO_PROGRESS_LIMIT_MS = 20 * 60_000L;
    /** A longer gap between ticks is a suspended client, not time spent stuck. */
    static final long NO_PROGRESS_MAX_STEP_MS = 60_000L;

    /** {@code lastTickMs} is 0 after a logout or pause, which do not count; nor does a legitimate wait. */
    static long addNoProgress(long soFarMs, long lastTickMs, long nowMs, boolean counts) {
        if (!counts || lastTickMs == 0L) return soFarMs;
        return soFarMs + Math.min(Math.max(0L, nowMs - lastTickMs), NO_PROGRESS_MAX_STEP_MS);
    }

    static boolean noProgressTooLong(long noProgressMs) {
        return noProgressMs >= NO_PROGRESS_LIMIT_MS;
    }

    /**
     * Pay-dirt that turned up while mining, the only sign of work at 200M XP. Counted only mid-vein, so
     * picking a dropped pile back up never passes for mining. {@code before} is -1 until first read.
     */
    static boolean minedMore(boolean miningVein, int before, int now) {
        return miningVein && before >= 0 && now > before;
    }

    /** A logout within this long of the player's own mouse or keys is theirs, and is not undone. */
    static boolean playerLoggedOut(boolean everLoggedIn, long lastHumanInputMs, long nowMs, long windowMs) {
        return everLoggedIn && lastHumanInputMs > 0 && nowMs - lastHumanInputMs < windowMs;
    }
}
