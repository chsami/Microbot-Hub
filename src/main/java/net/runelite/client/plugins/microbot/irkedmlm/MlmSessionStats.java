package net.runelite.client.plugins.microbot.irkedmlm;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntUnaryOperator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ItemID;

/**
 * Everything the session counts: Mining XP, golden nuggets, per-ore totals and their GE value.
 *
 * <h3>Why this is its own class</h3>
 * These fifteen-odd fields only ever talk to each other and to the inventory, but they used to sit
 * among eighty others on {@code IrkedMLMScript}. Two separate bugs hid in exactly that crowd — the
 * XP baseline and the price cache were each initialised once behind an {@code isLoggedIn()} guard
 * that silently never ran when the plugin was started from the login screen, and neither was
 * visible to the other. Gathered here, both baselines are one method with one rule.
 *
 * <h3>Testability</h3>
 * The two things that need a live client — reading an inventory quantity and looking up a GE price —
 * are injected as {@link IntUnaryOperator}s, so the whole accounting model runs headless.
 *
 * <h3>Counting rules</h3>
 * <ul>
 *   <li><b>Quantities, never slot counts.</b> Every read goes through the injected quantity lookup,
 *       which must be {@code Rs2Inventory.itemQuantity}. Golden nuggets are stackable, so
 *       {@code Rs2Inventory.count} returns 1 no matter how many are held and the gain never moves.</li>
 *   <li><b>Rises only.</b> Ores and nuggets are counted when the carried amount goes up; the falls
 *       (banking, spending nuggets on an upgrade) are ignored, never subtracted.</li>
 *   <li><b>Inventory only.</b> These are session figures, not totals owned. The bank is never read —
 *       it cannot be, since this plugin banks through a deposit box and OSRS only sends the bank
 *       container while the bank interface is open.</li>
 * </ul>
 */
@Slf4j
public final class MlmSessionStats {

    /** Ores tracked individually, in overlay display order. Index order is the public contract here. */
    static final int[] ORE_IDS = {
            ItemID.RUNITE_ORE, ItemID.ADAMANTITE_ORE, ItemID.MITHRIL_ORE, ItemID.GOLD_ORE, ItemID.COAL
    };

    /**
     * Every item whose inventory quantity is watched. Ores and gems contribute to
     * {@link #getTotalValueGained()}; nuggets are untradeable so they price at 0; pay-dirt is watched
     * only to keep its baseline current and is explicitly excluded from value.
     */
    private static final int[] TRACKED_ITEMS = {
            ItemID.MOTHERLODE_NUGGET,
            ItemID.RUNITE_ORE, ItemID.ADAMANTITE_ORE, ItemID.MITHRIL_ORE, ItemID.GOLD_ORE, ItemID.COAL,
            ItemID.UNCUT_SAPPHIRE, ItemID.UNCUT_EMERALD, ItemID.UNCUT_RUBY, ItemID.UNCUT_DIAMOND,
            ItemID.PAYDIRT
    };

    /** Mining XP is re-read at most this often; the reading itself needs a client-thread hop. */
    private static final long XP_CACHE_MS = 900L;

    private final IntUnaryOperator inventoryQuantity;
    private final IntUnaryOperator priceLookup;

    // ── XP ──────────────────────────────────────────────────────────────────
    /** Mining XP when this run started. 0 means "not baselined yet", never "started from zero". */
    @Getter private int startXp;
    /** Most recent Mining XP reading; -1 before the first one. */
    @Getter private int currentXp = -1;
    private long lastXpReadMs;
    private int cachedXp = -1;

    // ── Nuggets ─────────────────────────────────────────────────────────────
    @Getter private int gainedNuggets;
    /** Carried nugget quantity at the previous reading; -1 until the baseline is taken. */
    private int lastNuggetInv = -1;

    // ── Ore and value ───────────────────────────────────────────────────────
    private final int[] oreGained = new int[ORE_IDS.length];
    @Getter private long totalValueGained;
    private final Map<Integer, Integer> lastInventoryCounts = new HashMap<>();
    private final Map<Integer, Integer> priceCache = new HashMap<>();
    /** {time, amount} per ore gain, read by the overlay's sparkline from another thread. */
    private final List<long[]> oreCredits = new CopyOnWriteArrayList<>();
    private static final long CREDIT_WINDOW_MS = 130L * 60_000L;

    // ── Logged-in time ──────────────────────────────────────────────────────
    private long activeBankedMs;
    private long activeSinceMs;

    public MlmSessionStats(IntUnaryOperator inventoryQuantity, IntUnaryOperator priceLookup) {
        this.inventoryQuantity = inventoryQuantity;
        this.priceLookup = priceLookup;
    }

    /**
     * Clears every session figure for a fresh run. The script is a {@code @Singleton} that survives
     * stop/start, so without this a previous run's totals leak into the next one's rates.
     */
    public void reset() {
        startXp = 0;
        currentXp = -1;
        lastXpReadMs = 0L;
        cachedXp = -1;
        gainedNuggets = 0;
        lastNuggetInv = -1;
        totalValueGained = 0L;
        java.util.Arrays.fill(oreGained, 0);
        priceCache.clear();
        oreCredits.clear();
        clearActive();
        rebaselineInventory();
    }

    /** Takes the inventory baseline, so items already carried at start are not counted as gains. */
    public void rebaselineInventory() {
        lastInventoryCounts.clear();
        for (int itemId : TRACKED_ITEMS) {
            lastInventoryCounts.put(itemId, inventoryQuantity.applyAsInt(itemId));
        }
    }

    // ── XP ──────────────────────────────────────────────────────────────────

    /**
     * Feeds in a Mining XP reading and reports whether it went up (an ore was mined).
     *
     * <p>The first positive reading becomes the baseline. That is deliberately not conditional on
     * being logged in: the old code baselined once in {@code run()} behind an {@code isLoggedIn()}
     * guard, so starting the plugin at the login screen left the baseline at 0 and every later
     * reading counted the account's <em>entire</em> Mining XP as this session's gain — which the
     * overlay rendered as a huge XP/hr that visibly counted down as runtime grew.
     *
     * @return {@code true} if this reading is higher than the previous one
     */
    public boolean recordXp(int xp) {
        if (xp < 0) {
            return false;
        }
        if (startXp <= 0 && xp > 0) {
            startXp = xp;
            currentXp = xp;
            log.info("[MLM] Mining XP baseline for this session: {}", xp);
            return false;
        }
        boolean gained = currentXp != -1 && xp > currentXp;
        currentXp = xp;
        return gained;
    }

    /**
     * True when the cached Mining XP reading is stale and the caller should do the client-thread read.
     * Keeps the 900ms throttle that stops the hop running every tick.
     */
    public boolean needsXpRead(long nowMs) {
        return cachedXp == -1 || nowMs - lastXpReadMs >= XP_CACHE_MS;
    }

    /** The last XP value read, for use while {@link #needsXpRead} is false. */
    public int cachedXp() {
        return cachedXp;
    }

    /** Stores a fresh client-thread XP reading against the throttle clock. */
    public void cacheXp(int xp, long nowMs) {
        cachedXp = xp;
        lastXpReadMs = nowMs;
    }

    /** Mining XP gained this session; 0 until a baseline exists, never negative. */
    public int xpGained() {
        if (startXp <= 0 || currentXp < 0) {
            return 0;
        }
        return Math.max(0, currentXp - startXp);
    }

    /** XP to publish to the overlay — the live reading, falling back to the baseline before one exists. */
    public int displayXp() {
        return currentXp > 0 ? currentXp : startXp;
    }

    // ── Per-tick accounting ─────────────────────────────────────────────────

    /**
     * Reads the inventory once and folds every rise into the session totals. Safe to call every tick;
     * items that have not moved cost one quantity lookup each.
     */
    public void update() {
        for (int itemId : TRACKED_ITEMS) {
            int current = inventoryQuantity.applyAsInt(itemId);
            int last = lastInventoryCounts.getOrDefault(itemId, 0);
            if (current > last && itemId != ItemID.MOTHERLODE_NUGGET) {
                // Nuggets are handled by updateNuggets() below, which owns their running total.
                addGain(itemId, current - last);
            }
            lastInventoryCounts.put(itemId, current);
        }
        updateNuggets();
    }

    /**
     * Folds in ores a Deposit-All banked before the tick loop ever saw them in the inventory.
     *
     * @param snapshot per-ore counts captured just before the deposit, indexed like {@link #ORE_IDS}
     */
    public void applyDepositSnapshot(int[] snapshot) {
        if (snapshot == null) {
            return;
        }
        for (int i = 0; i < snapshot.length && i < ORE_IDS.length; i++) {
            if (snapshot[i] <= 0) {
                continue;
            }
            int oreId = ORE_IDS[i];
            int current = inventoryQuantity.applyAsInt(oreId);
            int lastSeen = lastInventoryCounts.getOrDefault(oreId, 0);
            // If the ore is still in the inventory, the normal delta accounting in update() will pick
            // it up. Only use the snapshot when the deposit was fast enough that it never appeared.
            if (current == 0 && lastSeen < snapshot[i]) {
                addGain(oreId, snapshot[i] - lastSeen);
                lastInventoryCounts.put(oreId, 0);
            }
        }
    }

    /** Counts an ore/gem gain and its GE value. Pay-dirt is worthless and never contributes. */
    private void addGain(int itemId, int added) {
        if (added <= 0) {
            return;
        }
        int oreIndex = oreIndexOf(itemId);
        if (oreIndex >= 0) {
            oreGained[oreIndex] += added;
            long now = System.currentTimeMillis();
            oreCredits.add(new long[]{now, added});
            // The sparkline shows two hours; anything older only costs memory and render time.
            oreCredits.removeIf(c -> now - c[0] > CREDIT_WINDOW_MS);
        }
        if (itemId != ItemID.PAYDIRT) {
            totalValueGained += (long) price(itemId) * added;
        }
    }

    private void updateNuggets() {
        int inv = inventoryQuantity.applyAsInt(ItemID.MOTHERLODE_NUGGET);
        if (lastNuggetInv < 0) {
            lastNuggetInv = inv;
            log.info("[MLM] Nugget baseline for this session: {} already carried", inv);
            return;
        }
        int gain = nuggetsGained(lastNuggetInv, inv);
        if (gain > 0) {
            gainedNuggets += gain;
            log.debug("[MLM] Nuggets +{} (inventory {} -> {}), session total {}",
                    gain, lastNuggetInv, inv, gainedNuggets);
        }
        lastNuggetInv = inv;
    }

    /** Nuggets added between two readings — increases only, never negative. */
    public static int nuggetsGained(int prevInv, int curInv) {
        return Math.max(0, curInv - prevInv);
    }

    private static int oreIndexOf(int itemId) {
        for (int i = 0; i < ORE_IDS.length; i++) {
            if (ORE_IDS[i] == itemId) {
                return i;
            }
        }
        return -1;
    }

    // ── Logged-in time ──────────────────────────────────────────────────────

    /** Feeds the login state; time only accrues between a logged-in reading and the next logged-out one. */
    public synchronized void trackLoggedIn(boolean loggedIn, long nowMs) {
        if (loggedIn && activeSinceMs == 0L) {
            activeSinceMs = nowMs;
        } else if (!loggedIn && activeSinceMs != 0L) {
            activeBankedMs += Math.max(0L, nowMs - activeSinceMs);
            activeSinceMs = 0L;
        }
    }

    private synchronized void clearActive() {
        activeBankedMs = 0L;
        activeSinceMs = 0L;
    }

    /** Session time spent logged in. */
    public synchronized long activeMs(long nowMs) {
        return activeBankedMs + (activeSinceMs != 0L ? Math.max(0L, nowMs - activeSinceMs) : 0L);
    }

    /** Timestamped ore gains, oldest first; safe to iterate from any thread. */
    public List<long[]> oreCredits() {
        return Collections.unmodifiableList(oreCredits);
    }

    // ── Prices ──────────────────────────────────────────────────────────────

    private int price(int itemId) {
        return resolvePrice(priceCache, itemId, priceLookup);
    }

    /**
     * Price cache policy, with the lookup injected so it is checkable without a client. The rule that
     * matters: <b>never memoise a 0</b>. A plain {@code computeIfAbsent} would, and one cold read at
     * startup then pins GP/hr to zero for the rest of the session.
     */
    static int resolvePrice(Map<Integer, Integer> cache, int itemId, IntUnaryOperator lookup) {
        Integer cached = cache.get(itemId);
        if (cached != null) {
            return cached;
        }
        int price = lookup.applyAsInt(itemId);
        if (price > 0) {
            cache.put(itemId, price);
        }
        return price;
    }

    // ── Ore accessors (index order matches ORE_IDS) ─────────────────────────

    public int getRunite()     { return oreGained[0]; }
    public int getAdamantite() { return oreGained[1]; }
    public int getMithril()    { return oreGained[2]; }
    public int getGold()       { return oreGained[3]; }
    public int getCoal()       { return oreGained[4]; }

    /** Every ore counted this session, for the lifetime totals. */
    public int totalOres() {
        int total = 0;
        for (int count : oreGained) {
            total += count;
        }
        return total;
    }
}
