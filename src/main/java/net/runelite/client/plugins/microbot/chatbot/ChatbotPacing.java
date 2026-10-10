package net.runelite.client.plugins.microbot.chatbot;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongBinaryOperator;
import java.util.function.LongSupplier;

/** One request deadline shared by successful replies, failures and quota pauses. */
final class ChatbotPacing {
    private static final long MAX_BACKOFF_MS = 15 * 60_000L;
    private final LongSupplier clock;
    private final LongBinaryOperator random;
    private long nextRequestTime;
    private int consecutiveFailures;

    ChatbotPacing() {
        this(System::currentTimeMillis, (min, max) -> ThreadLocalRandom.current().nextLong(min, max + 1));
    }

    ChatbotPacing(LongSupplier clock, LongBinaryOperator random) {
        this.clock = clock;
        this.random = random;
    }

    synchronized void success(long cooldownMs) {
        consecutiveFailures = 0;
        defer(cooldownMs);
    }

    synchronized long failure(int httpStatus, long retryAfterMs, long normalCooldownMs) {
        consecutiveFailures = Math.min(10, consecutiveFailures + 1);
        long base = httpStatus == 429 || httpStatus == 503 ? 30_000L : 5_000L;
        long backoff = Math.min(MAX_BACKOFF_MS, base * (1L << (consecutiveFailures - 1)));
        long jitter = random.applyAsLong(0, Math.min(5_000L, backoff / 4));
        long wait = Math.max(Math.max(0, retryAfterMs), Math.max(normalCooldownMs, backoff + jitter));
        defer(wait);
        return wait;
    }

    synchronized void defer(long delayMs) {
        long now = clock.getAsLong();
        long boundedDelay = Math.max(0, delayMs);
        long deadline = boundedDelay > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + boundedDelay;
        nextRequestTime = Math.max(nextRequestTime, deadline);
    }

    synchronized void reset() {
        consecutiveFailures = 0;
        nextRequestTime = 0;
    }

    synchronized long remainingMs() {
        return Math.max(0, nextRequestTime - clock.getAsLong());
    }

    synchronized int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    static long randomCooldownMs(int minSeconds, int maxSeconds) {
        int min = Math.max(5, Math.min(120, minSeconds));
        int max = Math.max(min, Math.max(5, Math.min(300, maxSeconds)));
        return ThreadLocalRandom.current().nextLong(min * 1_000L, max * 1_000L + 1);
    }

    static long untilGeminiDailyResetMs(long epochMs) {
        ZonedDateTime now = Instant.ofEpochMilli(epochMs).atZone(ZoneId.of("America/Los_Angeles"));
        // A small margin avoids retrying while Google's quota reset is propagating.
        long reset = now.toLocalDate().plusDays(1).atStartOfDay(now.getZone()).toInstant().toEpochMilli();
        return Math.max(1_000L, reset - epochMs + 60_000L);
    }
}
