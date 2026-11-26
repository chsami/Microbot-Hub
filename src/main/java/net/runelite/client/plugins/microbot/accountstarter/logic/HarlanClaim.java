package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.function.LongConsumer;

public final class HarlanClaim {
    static final long COOLDOWN_MS = 30 * 60_000L;

    private static volatile long claimedAt;
    private static volatile LongConsumer saver = ms -> { };

    private HarlanClaim() {}

    public static void load(long lastClaimMs, LongConsumer save) {
        claimedAt = lastClaimMs;
        saver = save;
    }

    public static void claimed(long nowMs) {
        claimedAt = nowMs;
        saver.accept(nowMs);
    }

    public static boolean ready(long nowMs) {
        return ready(claimedAt, nowMs);
    }

    static boolean ready(long lastClaimMs, long nowMs) {
        return lastClaimMs == 0 || nowMs - lastClaimMs >= COOLDOWN_MS;
    }
}
