package net.runelite.client.plugins.microbot.geflipper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Offer-aware second-screen / semi-AFK controller.
 *
 * Completes all current Copilot suggestions in a human-like check-in burst, waits for the GE
 * overview to stay settled in WAIT for a brief window, then enters a randomized AFK snooze
 * (either staying in-game with the cursor off-screen or logging out between check-ins).
 */
final class SecondScreenAfk {
    static final long SETTLE_MIN_MS = 8_000L;
    static final long SETTLE_MAX_MS = 18_000L;
    static final long LOGOUT_RETRY_MS = 4_500L;
    static final long LOGIN_RETRY_MS = 8_000L;
    static final long MIN_SNOOZE_MS = 30_000L;

    enum Phase {
        WORKING,
        SNOOZING,
        LOGGING_IN
    }

    interface Port {
        boolean loggedIn();
        int currentWorld();
        boolean exchangeOrBankOpen();
        void closeInterfaces();
        void moveMouseOffScreen();
        void logout();
        void login(int world);
    }

    interface RandomLong {
        long inclusive(long min, long max);
    }

    private final Port port;
    private final RandomLong random;

    private volatile Phase phase = Phase.WORKING;
    private volatile String status = "";
    private long settledSince = -1;
    private long settleDelayMs;
    private long snoozeStartedAt;
    private long snoozeUntil;
    private boolean offScreenMoved;
    private long nextLogoutAt;
    private long nextLoginAt;
    private int rememberedWorld;

    SecondScreenAfk(Port port) {
        this(port, (min, max) -> max <= min ? min : ThreadLocalRandom.current().nextLong(min, max + 1));
    }

    SecondScreenAfk(Port port, RandomLong random) {
        this.port = port;
        this.random = random;
    }

    Phase phase() {
        return phase;
    }

    String status() {
        return status;
    }

    /** True while intentionally staying logged out (or snoozing) so BreakHandlerV2 does not force a login. */
    boolean holdingLogout() {
        return phase == Phase.SNOOZING;
    }

    /** Expedites only a login already owned by an AFK check-in; Finish cannot create login intent. */
    boolean resumeForFinish() {
        if (phase == Phase.WORKING) return false;
        phase = Phase.LOGGING_IN;
        nextLoginAt = 0;
        status = "Second-screen AFK: returning to finish offers.";
        return true;
    }

    void reset() {
        phase = Phase.WORKING;
        settledSince = -1;
        settleDelayMs = 0;
        snoozeStartedAt = 0;
        snoozeUntil = 0;
        offScreenMoved = false;
        nextLogoutAt = 0;
        nextLoginAt = 0;
        status = "";
    }

    /** Resets the post-burst settle timer whenever an offer or UI action is performed. */
    void recordActivity() {
        settledSince = -1;
        settleDelayMs = 0;
        if (phase == Phase.WORKING) {
            status = "";
        }
    }

    /**
     * Runs while the client is logged out. Returns true when Second-Screen AFK owns the tick
     * (counting down a logout snooze or logging back in for the next check-in).
     */
    boolean tickLoggedOut(FlipperConfig.SecondScreenAfkMode mode, int intervalMinutes,
                          boolean suppressed, long now) {
        if (mode == null || mode == FlipperConfig.SecondScreenAfkMode.OFF || suppressed) {
            reset();
            return false;
        }
        if (phase == Phase.SNOOZING) {
            capSnoozeToInterval(intervalMinutes);
            if (now < snoozeUntil) {
                status = "Second-screen AFK (logged out): next check-in in " + formatRemaining(snoozeUntil - now);
                return true;
            }
            phase = Phase.LOGGING_IN;
            nextLoginAt = now;
        }
        if (phase == Phase.LOGGING_IN) {
            if (now >= nextLoginAt) {
                nextLoginAt = now + random.inclusive(LOGIN_RETRY_MS, LOGIN_RETRY_MS + 4_000L);
                status = "Second-screen AFK: logging in to check offers.";
                port.login(rememberedWorld);
            } else {
                status = "Second-screen AFK: waiting to log in.";
            }
            return true;
        }
        return false;
    }

    /**
     * Runs while logged in before trading actions. Returns true when Second-Screen AFK is currently
     * snoozing (ignoring offers or logging out) and should skip normal Copilot actions.
     */
    boolean tickBeforeTrading(FlipperConfig.SecondScreenAfkMode mode, int intervalMinutes,
                              boolean suppressed, long now) {
        if (mode == null || mode == FlipperConfig.SecondScreenAfkMode.OFF || suppressed) {
            reset();
            return false;
        }
        int world = port.currentWorld();
        if (world > 0) {
            rememberedWorld = world;
        }
        if (phase == Phase.LOGGING_IN) {
            phase = Phase.WORKING;
            settledSince = -1;
            settleDelayMs = 0;
            status = "";
        }
        if (phase != Phase.SNOOZING) {
            return false;
        }
        capSnoozeToInterval(intervalMinutes);
        if (now >= snoozeUntil) {
            phase = Phase.WORKING;
            settledSince = -1;
            settleDelayMs = 0;
            offScreenMoved = false;
            status = "";
            return false;
        }
        long remaining = snoozeUntil - now;
        if (mode == FlipperConfig.SecondScreenAfkMode.LOGOUT_BETWEEN_CHECKS) {
            status = "Second-screen AFK: logging out (" + formatRemaining(remaining) + ")";
            if (port.exchangeOrBankOpen()) {
                port.closeInterfaces();
                nextLogoutAt = now + random.inclusive(600L, 1_400L);
            } else if (now >= nextLogoutAt) {
                nextLogoutAt = now + random.inclusive(LOGOUT_RETRY_MS, LOGOUT_RETRY_MS + 2_500L);
                port.logout();
            }
            return true;
        }
        status = "Second-screen AFK: next check-in in " + formatRemaining(remaining);
        if (!offScreenMoved) {
            offScreenMoved = true;
            port.moveMouseOffScreen();
        }
        return true;
    }

    /**
     * Runs at the end of MONITORING_COPILOT when no offer or UI action was needed on this tick.
     * Returns true when this tick was a confirmed settled wait or transitioned into snoozing.
     */
    boolean onSettledWaitTick(FlipperConfig.SecondScreenAfkMode mode, int intervalMinutes,
                              boolean suppressed, boolean settledWait, long now) {
        if (mode == null || mode == FlipperConfig.SecondScreenAfkMode.OFF || suppressed) {
            reset();
            return false;
        }
        if (!settledWait) {
            recordActivity();
            return false;
        }
        int world = port.currentWorld();
        if (world > 0) {
            rememberedWorld = world;
        }
        if (phase != Phase.WORKING) {
            return true;
        }
        if (settledSince < 0) {
            settledSince = now;
            settleDelayMs = random.inclusive(SETTLE_MIN_MS, SETTLE_MAX_MS);
            status = "Second-screen AFK: finishing check-in (" + formatRemaining(settleDelayMs) + ")";
            return true;
        }
        long elapsed = now - settledSince;
        if (elapsed < settleDelayMs) {
            status = "Second-screen AFK: finishing check-in (" + formatRemaining(settleDelayMs - elapsed) + ")";
            return true;
        }
        long snoozeMs = computeSnoozeMs(intervalMinutes);
        phase = Phase.SNOOZING;
        snoozeStartedAt = now;
        snoozeUntil = now + snoozeMs;
        settledSince = -1;
        settleDelayMs = 0;
        offScreenMoved = false;
        nextLogoutAt = now;
        if (mode == FlipperConfig.SecondScreenAfkMode.LOGOUT_BETWEEN_CHECKS) {
            status = "Second-screen AFK: logging out (" + formatRemaining(snoozeMs) + ")";
        } else {
            status = "Second-screen AFK: next check-in in " + formatRemaining(snoozeMs);
            offScreenMoved = true;
            port.moveMouseOffScreen();
        }
        return true;
    }

    private void capSnoozeToInterval(int intervalMinutes) {
        int clamped = Math.max(1, Math.min(60, intervalMinutes));
        long maxAllowedMs = Math.max(MIN_SNOOZE_MS, Math.round(clamped * 60_000L * 1.35));
        if (snoozeStartedAt > 0 && snoozeUntil - snoozeStartedAt > maxAllowedMs) {
            snoozeUntil = snoozeStartedAt + maxAllowedMs;
        }
    }

    long computeSnoozeMs(int intervalMinutes) {
        int clamped = Math.max(1, Math.min(60, intervalMinutes));
        long baseMs = clamped * 60_000L;
        long minMs = Math.max(MIN_SNOOZE_MS, Math.round(baseMs * 0.65));
        long maxMs = Math.max(minMs, Math.round(baseMs * 1.35));
        return random.inclusive(minMs, maxMs);
    }

    static String formatRemaining(long remainingMs) {
        long totalSeconds = Math.max(1L, (remainingMs + 999L) / 1000L);
        long mins = totalSeconds / 60L;
        long secs = totalSeconds % 60L;
        return mins > 0 ? mins + "m " + secs + "s" : secs + "s";
    }
}
