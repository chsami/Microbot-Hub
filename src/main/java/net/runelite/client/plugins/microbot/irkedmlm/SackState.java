package net.runelite.client.plugins.microbot.irkedmlm;

import java.util.OptionalInt;
import lombok.extern.slf4j.Slf4j;

/**
 * Everything the plugin knows about the pay-dirt sack: how much is in it, how big it is, and whether
 * that knowledge can still be trusted.
 *
 * <h3>Why this replaced six fields</h3>
 * Sack fullness used to be decided by {@code config.sackSize()}, {@code maxSackSize},
 * {@code measuredSackCapacity}, a cached varbit, a sticky {@code sackIsFullFlag} that overrode the
 * count outright, and a 30-second wall-clock projection. They disagreed whenever the world did
 * something unpredicted, and each call site acted on whichever source happened to win there.
 *
 * <h3>The one rule</h3>
 * The count is <b>observed or unknown</b>. It becomes known only through {@link #observe} (varbit
 * 5558, with capacity from varbit 5556) or through a hard fact — the hopper refusing pay-dirt, or the
 * game saying the sack is full. Anything that breaks continuity calls {@link #invalidate} and it goes
 * back to {@link #UNKNOWN} until re-observed.
 *
 * <p>This matters because the sack keeps filling while the player is away: the wiki is explicit that
 * ores waiting to be collected are still there on return. Nothing known before a gap survives it.
 *
 * <h3>Why there is no projection</h3>
 * The old projection existed to answer "is the sack full?" during the few ticks varbit 5558 lags
 * behind a deposit. That question has an observed answer: pay-dirt left in the inventory means the
 * hopper refused, which means the sack is full. And after a <em>clean</em> deposit the inventory is
 * empty of pay-dirt, so nothing needs the number until the next full load — minutes later, long after
 * the varbit settles. Hence {@link #onCleanDeposit()} sets UNKNOWN rather than guessing a total.
 *
 * <h3>Threading</h3>
 * Varbit and chat events arrive on the client thread; the script reads from the executor thread. Every
 * method is {@code synchronized} so a reader can never see a half-applied two-field update (new
 * capacity with an old count). The object is tiny and uncontended, so the cost is irrelevant.
 */
@Slf4j
public final class SackState {

    /** Sentinel for "not observed". Distinct from 0, which means "observed, and empty". */
    public static final int UNKNOWN = -1;

    public static final int SACK_SIZE = 108;
    public static final int SACK_LARGE_SIZE = 189;

    private int count = UNKNOWN;
    private int capacity = SACK_SIZE;
    private boolean capacityKnown = false;
    private long asOfMs = 0L;
    private boolean nearlyFullWarned = false;
    /** A hard "full" fact the lagging varbit has not caught up with yet. */
    private boolean fullPending = false;
    private int lastVarbitCount = UNKNOWN;

    // ── Capacity hint ───────────────────────────────────────────────────────

    /**
     * Pre-login capacity guess, from config. Applied only while capacity is still unobserved, so the
     * overlay has a number on a cold start; varbit 5556 replaces it on the first {@link #observe}
     * and a later hint can never undo that.
     */
    public synchronized void setCapacityHint(int capacityHint) {
        if (!capacityKnown && capacityHint > 0) {
            capacity = capacityHint;
        }
    }

    // ── Queries ─────────────────────────────────────────────────────────────

    public synchronized boolean isKnown()         { return count != UNKNOWN; }
    public synchronized int     countOrUnknown()  { return count; }
    public synchronized int     capacity()        { return capacity; }
    public synchronized boolean isCapacityKnown() { return capacityKnown; }
    public synchronized long    asOfMs()          { return asOfMs; }
    public synchronized boolean wasNearlyFullWarned() { return nearlyFullWarned; }

    /** Free space in the sack, or empty when the count is unknown. */
    public synchronized OptionalInt room() {
        return isKnown() ? OptionalInt.of(Math.max(0, capacity - count)) : OptionalInt.empty();
    }

    /** True only when we have actually observed a full sack. Unknown is never "full". */
    public synchronized boolean definitelyFull() {
        return isKnown() && count >= capacity;
    }

    /** True only when we know there is room for this much pay-dirt. Unknown is never "has room". */
    public synchronized boolean definitelyHasRoomFor(int payDirt) {
        return isKnown() && (capacity - count) >= payDirt;
    }

    /**
     * Core RuneLite's overflow test ({@code MotherlodePlugin.java:261}): would depositing this much
     * pay-dirt fill the sack? Unknown answers false — callers must gate on {@link #isKnown()} and not
     * read a false here as "there is room".
     */
    public synchronized boolean wouldFillSack(int payDirt) {
        return isKnown() && payDirt >= (capacity - count);
    }

    // ── Observation: the only path to knowledge ─────────────────────────────

    /**
     * Records a live reading of both sack varbits. Must be called with values read on the client
     * thread while {@link IrkedMLMMapConstants#inMine()} is true — outside the mine varbit 5558 can
     * read 0, which is indistinguishable from an empty sack.
     *
     * @param countVarbit      {@code VarbitID.MOTHERLODE_SACK_TRANSMIT}
     * @param biggerSackVarbit {@code VarbitID.MOTHERLODE_BIGGERSACK}; 1 means the 200-nugget upgrade
     * @param nowMs            observation time
     */
    public synchronized void observe(int countVarbit, int biggerSackVarbit, long nowMs) {
        capacity = (biggerSackVarbit == 1) ? SACK_LARGE_SIZE : SACK_SIZE;
        capacityKnown = true;
        if (countVarbit > capacity) {
            // Holding more than the upgrade flag claims fits. The count is the harder fact — this is
            // the old "config understated it, the varbit is proof" correction, minus the circularity.
            log.info("[MLM] Sack holds {} > capacity {} — capacity must be the upgraded sack",
                    countVarbit, capacity);
            capacity = SACK_LARGE_SIZE;
        }
        int reading = Math.max(0, countVarbit);
        // A full sack learnt from the chat or a hopper refusal outranks a varbit that still lags below
        // capacity (ore in the water). It lasts until the varbit agrees, or falls (the sack is being
        // emptied), so the live count still drops during a withdraw.
        boolean falling = lastVarbitCount != UNKNOWN && reading < lastVarbitCount;
        if (fullPending && (reading >= capacity || falling)) {
            fullPending = false;
        }
        lastVarbitCount = reading;
        count = fullPending ? capacity : reading;
        asOfMs = nowMs;
        nearlyFullWarned = false;
    }

    // ── Hard facts ──────────────────────────────────────────────────────────

    /** The game said the sack is full. Authoritative. */
    public synchronized void onSackIsFullChat(long nowMs) {
        markFull(nowMs, "game message");
    }

    /**
     * The hopper refused pay-dirt. Authoritative, and it beats the varbit and any configured
     * capacity. Callers must only invoke this when a water wheel is actually running — a
     * both-wheels-broken hopper also stops accepting, and that is not a full sack.
     */
    public synchronized void onHopperRefusal(long nowMs) {
        markFull(nowMs, "hopper refused pay-dirt with a wheel running");
    }

    private void markFull(long nowMs, String why) {
        count = capacity;
        fullPending = true;
        asOfMs = nowMs;
        nearlyFullWarned = false;
        log.info("[MLM] Sack is full at {} ({})", capacity, why);
    }

    /**
     * The game warned the sack <em>will</em> be full. A warning, not a fact — it deliberately does
     * not touch the count. Pinning the count here is what used to make a sack at 100/108 report 108,
     * which then got read back as evidence about capacity.
     */
    public synchronized void onNearlyFullChat() {
        nearlyFullWarned = true;
    }

    // ── Loss of knowledge ───────────────────────────────────────────────────

    /** Continuity broke (logout, disconnect, hop, region change, restart, long tick gap). */
    public synchronized void invalidate(String reason) {
        if (count != UNKNOWN) {
            log.info("[MLM] Sack count is now unknown: {}", reason);
        }
        count = UNKNOWN;
        asOfMs = 0L;
        nearlyFullWarned = false;
        fullPending = false;
        lastVarbitCount = UNKNOWN;
    }

    /**
     * All offered pay-dirt was accepted. The total went up by a known amount but the new total has
     * not been transmitted yet, so we do not know it — and crucially do not need to, because the
     * inventory is now empty of pay-dirt.
     */
    public synchronized void onCleanDeposit() {
        invalidate("pay-dirt accepted; new total not transmitted yet");
    }
}
