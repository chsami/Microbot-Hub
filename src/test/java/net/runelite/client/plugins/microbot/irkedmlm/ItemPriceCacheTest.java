package net.runelite.client.plugins.microbot.irkedmlm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pins the GP/hr price cache.
 *
 * <p>The bug this exists for: prices were fetched once in {@code run()} behind an
 * {@code isLoggedIn()} guard. Started from the login screen that never ran, every lookup fell back
 * to 0, and GP/hr read zero for the entire session. The fix is a lazy cache that treats 0 as "not
 * known yet" — so the rule to defend is that a zero is <em>never</em> memoised, which is exactly
 * what a "tidy-up" to {@code computeIfAbsent} would break.
 */
class ItemPriceCacheTest {

    private static final int RUNITE = 451;

    @Test
    void aColdZeroPriceIsRetriedNotMemoised() {
        Map<Integer, Integer> cache = new HashMap<>();
        AtomicInteger calls = new AtomicInteger();
        // ItemManager is cold for the first two reads, then warms up.
        java.util.function.IntUnaryOperator lookup = id -> calls.incrementAndGet() <= 2 ? 0 : 11_000;

        assertEquals(0, MlmSessionStats.resolvePrice(cache, RUNITE, lookup));
        assertEquals(0, MlmSessionStats.resolvePrice(cache, RUNITE, lookup));
        assertTrue(cache.isEmpty(), "a cold 0 was cached — GP/hr would stay at zero all session");

        assertEquals(11_000, MlmSessionStats.resolvePrice(cache, RUNITE, lookup));
        assertEquals(Integer.valueOf(11_000), cache.get(RUNITE));
    }

    @Test
    void aRealPriceIsCachedAndTheLookupStops() {
        Map<Integer, Integer> cache = new HashMap<>();
        AtomicInteger calls = new AtomicInteger();
        java.util.function.IntUnaryOperator lookup = id -> {
            calls.incrementAndGet();
            return 150;
        };

        for (int i = 0; i < 5; i++) {
            assertEquals(150, MlmSessionStats.resolvePrice(cache, RUNITE, lookup));
        }
        assertEquals(1, calls.get()); // one client-thread hop per item per session, not one per tick
    }

    @Test
    void untradeablesStayAtZeroWithoutPollutingTheCache() {
        // Golden nuggets price at 0 forever; they must contribute nothing and never fill the cache.
        Map<Integer, Integer> cache = new HashMap<>();
        assertEquals(0, MlmSessionStats.resolvePrice(cache, RUNITE, id -> 0));
        assertTrue(cache.isEmpty(), "an untradeable poisoned the price cache");
    }
}
