package net.runelite.client.plugins.microbot.irkedmlm.session;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.irkedmlm.IrkedMLMMapConstants;
import net.runelite.client.plugins.microbot.irkedmlm.SackState;
import net.runelite.client.plugins.microbot.irkedmlm.IrkedMLMConfig;
import net.runelite.client.plugins.microbot.irkedmlm.enums.DepositMethod;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Gembag;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.List;

/**
 * Tick-progressive SackSession for Motherlode Mine.
 * Handles the withdraw-from-sack → deposit-to-deposit-box loop until the sack is empty.
 *
 * <p>Handles walking to the sack if necessary (using explicit target from cache or
 * known location), then the withdraw loop, then deposit box (which has its own close-range
 * walk logic). Do not rely on click() for long distance travel.
 */
@Slf4j
public class SackSession extends Session {

    public enum SackSubState {
        IDLE,
        TRANSITIONING_FLOOR,
        EVALUATE,
        WITHDRAW,
        DEPOSIT,
        CHECK_EMPTY,
        RETURN_TO_SPOT,
        COMPLETE,
        FAILED
    }

    private static final long CLICK_THROTTLE_MS  = 1000L;
    private static final long STALL_THRESHOLD_MS = 12_000L;

    private static final int MAX_WITHDRAW_RETRIES = 3;

    /** Runaway backstop on gem-bag Empty passes per sack trip: 300 gems / ~27 free slots, plus slack. */
    private static final int MAX_GEMBAG_EMPTY_PASSES     = 14;
    /** Free slots required before dumping the bag out — gems need somewhere to land. */
    private static final int GEMBAG_EMPTY_MIN_FREE_SLOTS = 5;

    /** Ore only, and index order is a contract: it matches {@link MlmSessionStats#ORE_IDS} and
     *  indexes {@link #pendingOreDepositSnapshot}. Never reorder or extend this list. */
    private static final List<Integer> ORE_IDS = Arrays.asList(
            ItemID.RUNITE_ORE, ItemID.ADAMANTITE_ORE, ItemID.MITHRIL_ORE,
            ItemID.GOLD_ORE, ItemID.COAL
    );

    /** Uncut gems MLM veins yield (1/256 per mining attempt, 1/86 with a charged glory). */
    private static final int[] GEM_IDS = {
            ItemID.UNCUT_SAPPHIRE, ItemID.UNCUT_EMERALD, ItemID.UNCUT_RUBY, ItemID.UNCUT_DIAMOND
    };

    /**
     * Everything the deposit box should take off us. Gems belong here: a user who turns "Drop Gems"
     * off wants them <em>banked</em>, and when they were missing from this list they were never
     * banked at all under DEPOSIT_ITEMS. Uncut gems don't stack, so free slots then decayed about
     * one per two minutes (with a glory) until the inventory was full of gems, at which point the
     * sack trip had nothing to deposit and the EVALUATE/WITHDRAW handlers ping-ponged forever.
     */
    public static final List<Integer> DEPOSIT_IDS = java.util.Collections.unmodifiableList(
            Stream.concat(ORE_IDS.stream(), Arrays.stream(GEM_IDS).boxed()).collect(Collectors.toList()));

    private static final int SACK_ID         = ObjectID.MOTHERLODE_SACK;
    private static final int SACK_ID_GRAPHIC  = ObjectID.MOTHERLODE_SACK_GRAPHIC;
    private static final int DEPOSIT_BOX_ID   = ObjectID.KR_BANK_DEPOSIT_BOX;

    private static final WorldPoint DEPOSIT_BOX_LOC = IrkedMLMMapConstants.DEPOSIT_BOX_LOC;

    @Getter
    private SackSubState sackSubState = SackSubState.IDLE;

    private MLMMiningSpot originalMiningSpot    = null;
    /** Deposit-box "Empty" passes used this sack trip; see the DEPOSIT state for why one is not enough. */
    private int           gemBagEmptyPasses     = 0;
    private int           withdrawRetryCount    = 0;
    private final int[] pendingOreDepositSnapshot = new int[ORE_IDS.size()];
    /** Progress tracking to detect when withdraws are not actually reducing the sack (stale varbit
     *  or wrong action). Used to break the "Sack still has 189/189 ore — looping" forever case
     *  even after live count fixes, and to try the "Empty sack" explicit option seen in logs. */
    private int lastSeenSackCount = -1;
    private int noProgressWithdraws = 0;
    /** Sack clicks since the sack last got emptier; past the limit the trip fails instead of clicking on. */
    static final int MAX_WITHDRAWS_WITHOUT_PROGRESS = 9;
    int withdrawsWithoutProgress = 0;
    private int lastTickSackCount = -1;
    private boolean madeProgress = false;
    /** The one camera turn a trip gets to bring the sack out from behind the deposit box. */
    private boolean clearTurnTried = false;
    /** Waiting longer than this for a readable sack count fails the trip. */
    static final long UNKNOWN_LIMIT_MS = 30_000L;
    private long unknownSinceMs = 0L;
    /** Set when the inventory is full and none of it is ours to deposit; survives reset() so the script can stop. */
    @Getter
    private boolean inventoryClogged = false;

    /** What the Deposit Inventory button may sweep: ore, gems, nuggets, and the gem bag (slot-locked). */
    private static final java.util.Set<Integer> SWEEPABLE_IDS = java.util.Collections.unmodifiableSet(
            Stream.concat(DEPOSIT_IDS.stream(), Stream.of(ItemID.MOTHERLODE_NUGGET, ItemID.GEM_BAG, ItemID.GEM_BAG_OPEN))
                    .collect(Collectors.toSet()));

    /**
     * True when Deposit Inventory would only take things we mean to bank. A pickaxe the player can't
     * wield, their own hammer or a coal bag would otherwise be swept into the box, and the run then
     * stops for a missing pickaxe.
     */
    static boolean onlySweepableItems(java.util.stream.IntStream inventoryIds) {
        return inventoryIds.allMatch(SWEEPABLE_IDS::contains);
    }

    private final Rs2TileObjectCache   tileCache;

    private java.util.function.BooleanSupplier gemBagSlotSupplier;
    private java.util.function.BooleanSupplier gemBagLockSupplier;

    public SackSession(Rs2TileObjectCache tileCache, IrkedMLMConfig config) {
        super(config);
        this.tileCache = tileCache;
    }

    /** Wires the live gem-bag slot/lock facts used to gate Deposit-All. Set once from the script;
     *  keeps this session's own logic (and depositAllSafe()) testable without live client state. */
    public void setGemBagSuppliers(java.util.function.BooleanSupplier inSlot0, java.util.function.BooleanSupplier locked) {
        this.gemBagSlotSupplier = inSlot0;
        this.gemBagLockSupplier = locked;
    }

    /** The effective (possibly preflight-degraded ALL→ITEMS) deposit method. Falls back to config. */
    private java.util.function.Supplier<DepositMethod> depositMethodSupplier;
    public void setDepositMethodSupplier(java.util.function.Supplier<DepositMethod> s) { this.depositMethodSupplier = s; }
    private DepositMethod depositMethod() {
        return depositMethodSupplier != null ? depositMethodSupplier.get() : config.depositMethod();
    }

    /** Whether a Deposit-All is safe for the gem bag. Only meaningful when method == ALL. */
    public static boolean depositAllSafe(boolean useGemBag, DepositMethod method,
                                         boolean bagInInventory, boolean bagInSlot0, boolean slot0Locked) {
        if (!useGemBag) return true;                   // nothing to protect
        if (method != DepositMethod.ALL) return true;  // ITEMS path never sweeps the bag
        if (!bagInInventory) return true;              // bag already banked / not carried — nothing to sweep
        return bagInSlot0 && slot0Locked;              // protected only when in slot 0 and locked
    }

    /** {@inheritDoc} Emptying the sack is a focused bank trip, not part of the AFK mining loop. */
    @Override
    protected boolean isFocusedTask() {
        return true;
    }

    @Override
    public void reset() {
        super.reset();
        sackSubState              = SackSubState.IDLE;
        originalMiningSpot        = null;
        gemBagEmptyPasses         = 0;
        withdrawRetryCount        = 0;
        java.util.Arrays.fill(pendingOreDepositSnapshot, 0);
        lastSeenSackCount         = -1;
        noProgressWithdraws       = 0;
        withdrawsWithoutProgress  = 0;
        lastTickSackCount         = -1;
        madeProgress              = false;
        clearTurnTried            = false;
        unknownSinceMs            = 0L;
    }

    /** Feeds in whether the sack count is readable; true once it has not been for too long. */
    boolean unknownTooLong(boolean known, long nowMs) {
        if (known) {
            unknownSinceMs = 0L;
        } else if (unknownSinceMs == 0L) {
            unknownSinceMs = nowMs;
        }
        return unknownSinceMs != 0L && nowMs - unknownSinceMs > UNKNOWN_LIMIT_MS;
    }

    /** Tracks whether the sack is getting emptier, which is the only proof a withdraw click did anything. */
    void noteSackCount(int count) {
        if (lastTickSackCount >= 0 && count < lastTickSackCount) {
            withdrawsWithoutProgress = 0;
            madeProgress = true;
        }
        lastTickSackCount = count;
    }

    /** True once after the sack got emptier; the script's recovery budget starts over on it. */
    public boolean consumeProgress() {
        boolean progress = madeProgress;
        madeProgress = false;
        return progress;
    }

    @Override
    protected long maxSessionMs() {
        return 300_000L; // a 189 sack is several withdraw/deposit laps
    }

    public void markInventoryClogged() {
        inventoryClogged = true;
    }

    /** A new run starts clean; the clog flag only exists to stop the run that hit it. */
    public void clearInventoryClogged() {
        inventoryClogged = false;
    }

    public void begin(MLMMiningSpot currentSpot) {
        if (!isIdle()) {
            log.warn("[SackSession] begin() called while not idle");
            return;
        }
        this.originalMiningSpot = currentSpot;
        inventoryClogged = false;
        lastSeenSackCount = -1;
        noProgressWithdraws = 0;
        transitionSub(SackSubState.IDLE);
        transition(State.ACTIVE);
    }

    @Override
    protected void tickInternal() {
        // Uses tick(int, int) overload
    }

    public void tick(SackState sackState) {
        if (!isActive()) return;
        if (failIfOverCap()) return;
        if (System.currentTimeMillis() < nextActionMs) return;

        try {
            tickSackInternal(sackState);
        } catch (Exception e) {
            log.error("[SackSession] Tick crash", e);
            transitionSub(SackSubState.FAILED);
            transition(State.FAILED);
        }
    }

    private void tickSackInternal(SackState sackState) {
        // ONE guard instead of auditing every comparison below. SackState.UNKNOWN is -1, so an
        // unguarded "currentSackCount <= 0" would read an unobserved sack as an empty one and
        // complete the trip — exactly the 0-means-two-things bug this rework exists to remove.
        // While emptying, the script re-observes every tick, so unknown here is brief: wait it out.
        // Read once: the client thread can make it unknown again between two reads.
        final int currentSackCount = sackState.countOrUnknown();
        if (unknownTooLong(currentSackCount != SackState.UNKNOWN, System.currentTimeMillis())) {
            log.warn("[SackSession] Sack count not readable for {}s — failing", UNKNOWN_LIMIT_MS / 1000);
            transitionSub(SackSubState.FAILED);
            transition(State.FAILED);
            return;
        }
        if (currentSackCount == SackState.UNKNOWN) {
            log.debug("[SackSession] Sack count not observed yet — waiting rather than concluding");
            scheduleNextAdaptive(180L, 600L);
            return;
        }
        final int maxSackSize = sackState.capacity();
        noteSackCount(currentSackCount);
        if (waitForFloorReading()) return;
        switch (sackSubState) {

            case IDLE:
                if (isUpperFloor()) {
                    transitionSub(SackSubState.TRANSITIONING_FLOOR);
                } else {
                    transitionSub(SackSubState.EVALUATE);
                }
                break;

            case TRANSITIONING_FLOOR:
                if (busyMoving(5000)) {
                    scheduleNextAdaptive(180L, 600L);
                    break;
                }
                if (ensureFloor(false)) {
                    transitionSub(SackSubState.EVALUATE);
                }
                if (isStalled(20_000L)) {
                    log.warn("[SackSession] Floor transition stalled — failing");
                    transitionSub(SackSubState.FAILED);
                    transition(State.FAILED);
                }
                break;

            case EVALUATE:
                // If we somehow have pay-dirt, abort sack session and let main script reroute
                if (Rs2Inventory.itemQuantity(ItemID.PAYDIRT) > 0) {
                    log.warn("[SackSession] Pay-dirt detected in inventory during sack emptying — aborting to deposit");
                    transition(State.FAILED); // Let main script reroute to DEPOSIT_HOPPER
                    break;
                }
                if (currentSackCount <= 0 && !hasDepositablesInInventory()) {
                    log.debug("[SackSession] Sack empty ({}/{}), no ore in inventory — done", currentSackCount, maxSackSize);
                    noProgressWithdraws = 0;
                    lastSeenSackCount = currentSackCount;
                    transitionSub(SackSubState.RETURN_TO_SPOT);
                    break;
                }
                if (hasDepositablesInInventory()) {
                    log.debug("[SackSession] Ore in inventory — depositing");
                    transitionSub(SackSubState.DEPOSIT);
                } else {
                    withdrawRetryCount = 0;
                    transitionSub(SackSubState.WITHDRAW);
                }
                break;

            case WITHDRAW:
                if (isUpperFloor()) {
                    // Safety: if we somehow entered/continued WITHDRAW while on upper floor
                    // (e.g. ladder position "in front" made previous dist check think we were close,
                    // or status transition happened at the ladder), force the floor transition
                    // instead of spamming clicks on the lower sack object (which produces no progress
                    // and the empty-option MenuEntry spam).
                    log.debug("[SackSession] WITHDRAW on upper floor — forcing floor transition before sack clicks");
                    transitionSub(SackSubState.TRANSITIONING_FLOOR);
                    break;
                }
                if (Rs2Inventory.isFull()) {
                    transitionSub(SackSubState.DEPOSIT);
                    break;
                }
                if (currentSackCount <= 0) {
                    log.info("[SackSession] Sack empty (varbit) — checking");
                    transitionSub(SackSubState.CHECK_EMPTY);
                    break;
                }
                if (Rs2Player.isInteracting() || Rs2Player.isAnimating() || Rs2Player.isMoving()) {
                    scheduleNextAdaptive(180L, 600L);
                    break;
                }

                // Progress tracking: if the sack count is not strictly decreasing after a withdraw we
                // may be stuck (stale varbit, or the default click not resolving to the empty action).
                if (lastSeenSackCount > 0 && currentSackCount >= lastSeenSackCount) {
                    noProgressWithdraws++;
                }
                lastSeenSackCount = currentSackCount;

                if (withdrawsWithoutProgress >= MAX_WITHDRAWS_WITHOUT_PROGRESS) {
                    log.warn("[SackSession] Sack still at {} after {} clicks — failing", currentSackCount, withdrawsWithoutProgress);
                    transitionSub(SackSubState.FAILED);
                    transition(State.FAILED);
                    break;
                }

                if (noProgressWithdraws > 4) {
                    log.warn("[SackSession] No progress emptying sack (count still {} after {} withdraws). Trying explicit 'Empty sack'/'Search' action.", currentSackCount, noProgressWithdraws);
                }

                // Proactively walk toward the sack if we are too far, or it is not yet reachable or
                // clickable. This matters when EMPTY_SACK is entered from a distant mining spot (south or
                // west lower, or right after climbing down) — a long-range click alone is unreliable.
                //
                // IMPORTANT: The query() builder + terminal ops (nearest*) operate on a one-shot stream.
                // Reusing the same query instance for two .nearest* calls causes "stream has already been
                // operated upon or closed". Build independent queries.
                var sackForClick = tileCache.query()
                        .where(o -> o.getId() == SACK_ID || o.getId() == SACK_ID_GRAPHIC)
                        .nearestReachable();
                var sackForTarget = tileCache.query()
                        .where(o -> o.getId() == SACK_ID || o.getId() == SACK_ID_GRAPHIC)
                        .nearest(); // any sack in scene for walk target (full scene is scanned)

                WorldPoint sackLoc = (sackForTarget != null) ? sackForTarget.getWorldLocation() : null;
                int distToSack = (sackLoc != null && Rs2Player.getWorldLocation() != null)
                        ? Rs2Player.getWorldLocation().distanceTo(sackLoc) : Integer.MAX_VALUE;

                // Only proactively walk if we have no reachable sack *and* we are not already
                // in the deposit-box / sack area. After finishing a deposit (interface still open
                // or just acted on), a human will just click the sack object directly from here.
                // The sack click will auto-close the deposit box interface. This avoids the
                // "walk to sack loc then click" behavior when we are already standing at the box.
                boolean nearDepositBox = Rs2Player.getWorldLocation() != null
                        && Rs2Player.getWorldLocation().distanceTo(DEPOSIT_BOX_LOC) <= 7;
                boolean shouldWalkFirst = (sackForClick == null)
                        && (sackLoc != null)
                        && (distToSack > 8)
                        && !nearDepositBox;

                if (sackForTarget == null && !nearDepositBox && Rs2Player.getWorldLocation() != null) {
                    // Too far off for the sack to be loaded: the deposit box stands beside it.
                    log.debug("[SackSession] Sack not in view — walking to the deposit box beside it");
                    Rs2Walker.walkTo(DEPOSIT_BOX_LOC);
                    subStateEnteredMs = System.currentTimeMillis(); // the wait for it to load starts on arrival
                    scheduleNextAdaptive(350L, 1200L);
                    break;
                }

                if (shouldWalkFirst) {
                    if (distToSack > 15) {
                        log.debug("[SackSession] Sack {} tiles away — using webwalker to target", distToSack);
                        Rs2Walker.walkTo(sackLoc);
                    } else {
                        log.debug("[SackSession] Sack {} tiles away — walking closer", distToSack);
                        Rs2Walker.walkFastCanvas(sackLoc);
                    }
                    scheduleNextAdaptive(350L, 1200L);
                    break;
                }

                // Now close enough — use the reachable one for the actual click if available
                var sack = (sackForClick != null) ? sackForClick : sackForTarget;

                if (sack != null) {
                    // The deposit box is normally still open here — clicking the sack closes it and
                    // empties in one go, which is fine, but only if the sack is actually visible. When
                    // the panel is drawn over it the click lands on the interface, not the sack, so the
                    // camera turns once a trip to put the sack beside the panel. Still behind it (or
                    // in fixed mode), ensureClickable closes the box instead of turning again.
                    if (!clearTurnTried && Rs2DepositBox.isOpen() && !hasSafeClickbox(sack)) {
                        clearTurnTried = true;
                        if (turnClearOfPanel(sack)) {
                            scheduleNextAdaptive(180L, 600L);
                            break;
                        }
                    }
                    if (!ensureClickable(sack)) {
                        scheduleNextAdaptive(180L, 600L);
                        break;
                    }

                    // Prefer explicit action names observed in game ("Empty sack" when it becomes the
                    // primary option, or "Search"). Fall back to default click(). This helps when
                    // bare click() doesn't do what we expect on the sack object.
                    boolean actionTaken = false;
                    if (noProgressWithdraws > 4) {
                        actionTaken = clickOnMyFloor(sack, "Empty sack") || clickOnMyFloor(sack, "Search");
                    }
                    if (!actionTaken) {
                        actionTaken = clickOnMyFloor(sack, null);
                    }
                    if (actionTaken) {
                        applyActionCooldown();
                        scheduleNextAdaptive(350L, CLICK_THROTTLE_MS);
                        withdrawRetryCount++;
                        withdrawsWithoutProgress++;
                        if (withdrawRetryCount >= MAX_WITHDRAW_RETRIES) {
                            transitionSub(SackSubState.CHECK_EMPTY);
                        }
                        // If we had high no-progress, give one more cycle then let the CHECK_EMPTY
                        // + next WITHDRAW re-evaluate the (hopefully now live + reduced) count.
                        if (noProgressWithdraws > 4) {
                            noProgressWithdraws = 3; // back off a bit, not zero
                        }
                    } else {
                        // click() returned false — player is walking toward sack
                        scheduleNextAdaptive(250L, 800L);
                    }
                } else {
                    log.debug("[SackSession] Sack not in cache — waiting for scene load");
                    scheduleNextAdaptive(250L, 800L);
                    if (isStalled(STALL_THRESHOLD_MS)) {
                        log.warn("[SackSession] Sack not found for {}ms — failing", STALL_THRESHOLD_MS);
                        transitionSub(SackSubState.FAILED);
                        transition(State.FAILED);
                    }
                }
                break;

            case DEPOSIT:
                if (!Rs2DepositBox.isOpen()) {
                    // Wait for any movement/animation to finish before clicking
                    if (Rs2Player.isInteracting() || Rs2Player.isAnimating() || Rs2Player.isMoving()) {
                        scheduleNextAdaptive(180L, 600L);
                        break;
                    }

                    // Before the box opens, for either deposit method: with it open an item has no Drop.
                    if (config.dropGems() && !config.useGemBag() && dropGemsFromInventory()) {
                        scheduleNextAdaptive(180L, 600L);
                        break;
                    }

                    var box = tileCache.query()
                            .where(o -> o.getId() == DEPOSIT_BOX_ID)
                            .nearestReachable();

                    if (box == null) {
                        // Deposit box not in scene — walk closer
                        int dist = Rs2Player.getWorldLocation().distanceTo(DEPOSIT_BOX_LOC);
                        if (dist > 15) {
                            log.debug("[SackSession] Deposit box {} tiles away — using webwalker", dist);
                            Rs2Walker.walkTo(DEPOSIT_BOX_LOC);
                        } else if (dist > 3) {
                            log.debug("[SackSession] Deposit box {} tiles away — walking closer", dist);
                            Rs2Walker.walkFastCanvas(DEPOSIT_BOX_LOC);
                        } else {
                            log.debug("[SackSession] Deposit box very close ({}) but not in cache — waiting", dist);
                        }
                        scheduleNextAdaptive(350L, 1200L);
                        if (isStalled(STALL_THRESHOLD_MS)) {
                            log.warn("[SackSession] Deposit box not found for {}ms — failing", STALL_THRESHOLD_MS);
                            transitionSub(SackSubState.FAILED);
                            transition(State.FAILED);
                        }
                        break;
                    }

                    if (isHumanLikeEnabled()) {
                        scheduleNext(java.util.concurrent.ThreadLocalRandom.current().nextInt(400, 900));
                    } else {
                        scheduleNextAdaptive(80L, 400L);
                    }

                    log.info("[SackSession] Opening deposit box (ID {})", DEPOSIT_BOX_ID);
                    if (clickOnMyFloor(box, "Deposit")) {
                        applyActionCooldown();
                        scheduleNext(1800L); // longer wait after click for interface to open
                    } else {
                        scheduleNext(1000L);
                    }
                    break;
                }

                // "Empty" banks the bag's contents directly only with the BANK interface open; a
                // deposit box is not that, so here it moves gems into the INVENTORY instead. That makes
                // this empty-then-deposit, repeatedly: a full bag is 300 gems (60 per type) against ~27
                // free slots, so it takes a dozen passes. The one-shot flag this replaces could only
                // ever clear the first load, and its isAnyGemSlotFull() trigger needs an exact tracked
                // 60 — which under-counts every gem found while the bag was shut, so a topped-out bag
                // silently spilled into the inventory instead. Rs2Gembag decrements its own counts as
                // gems land in the inventory, so an emptying bag walks getTotalGemCount() down to 0 and
                // ends the loop on its own; the pass cap is only a runaway backstop.
                if (config.useGemBag() && Rs2Gembag.hasGemBag() && Rs2Gembag.isGemBagOpen()) {
                    if (Rs2Gembag.isUnknown()) {
                        Rs2Gembag.checkGemBag();
                        scheduleNextAdaptive(350L, CLICK_THROTTLE_MS);
                        log.debug("[SackSession] Gem bag contents unknown — checking before emptying");
                        break;
                    }
                    // Deliberately gated on free slots, not on fullness: with no room the gems would
                    // have nowhere to land. The ore/gem deposit below frees the slots for the next pass.
                    if (Rs2Gembag.getTotalGemCount() > 0
                            && Rs2Inventory.emptySlotCount() >= GEMBAG_EMPTY_MIN_FREE_SLOTS
                            && gemBagEmptyPasses < MAX_GEMBAG_EMPTY_PASSES) {
                        log.debug("[SackSession] Emptying gem bag into the inventory ({} gems tracked, pass {}/{})",
                                Rs2Gembag.getTotalGemCount(), gemBagEmptyPasses + 1, MAX_GEMBAG_EMPTY_PASSES);
                        Rs2Inventory.interact(ItemID.GEM_BAG_OPEN, "Empty");
                        gemBagEmptyPasses++;
                        scheduleNextAdaptive(350L, CLICK_THROTTLE_MS);
                        break;
                    }
                }

                // Deposit ores — respect the effective (preflight-resolved) deposit method
                boolean sweepSafe;
                {
                    boolean bagIn0 = gemBagSlotSupplier != null && gemBagSlotSupplier.getAsBoolean();
                    boolean s0Lock = gemBagLockSupplier != null && gemBagLockSupplier.getAsBoolean();
                    log.info("[SackSession] Deposit decision: method={} useGemBag={} bagInInv={} bagInSlot0={} slot0Locked={}",
                            depositMethod(), config.useGemBag(), Rs2Gembag.hasGemBag(), bagIn0, s0Lock);
                    // A bag whose slot came unlocked mid-run is banked around, item by item, like a carried pickaxe.
                    sweepSafe = onlySweepableItems(Rs2Inventory.items().mapToInt(i -> i.getId()))
                            && depositAllSafe(config.useGemBag(), DepositMethod.ALL, Rs2Gembag.hasGemBag(), bagIn0, s0Lock);
                }
                if (depositMethod() == DepositMethod.ALL && !sweepSafe) {
                    log.info("[SackSession] Inventory holds items Deposit-All would bank (pickaxe, hammer, bag) — depositing item by item this time");
                }
                if (depositMethod() == DepositMethod.ALL && sweepSafe) {
                    captureOreDepositSnapshot();
                    Rs2DepositBox.depositAll();
                    scheduleNextAdaptive(350L, CLICK_THROTTLE_MS);
                    transitionSub(SackSubState.CHECK_EMPTY);
                } else {
                    // Natural mouse + human variety: deposit one item type per tick.
                    // Shuffle a copy of the list each time we enter this phase so the order
                    // isn't robotic (humans don't always do runite first, then adamant, etc.).

                    java.util.List<Integer> depositOrder = new java.util.ArrayList<>(DEPOSIT_IDS);
                    java.util.Collections.shuffle(depositOrder, java.util.concurrent.ThreadLocalRandom.current());

                    for (int itemId : depositOrder) {
                        if (Rs2Inventory.contains(itemId)) {
                            captureOreDepositSnapshot(itemId); // no-ops for a gem id
                            Rs2Inventory.interact(itemId, "Deposit-All");
                            applyActionCooldown();
                            scheduleNextAdaptive(180L, 600L);
                            return; // one item type per tick, come back next tick
                        }
                    }
                    // Nothing left to deposit. Nuggets are deliberately left in the inventory: they are
                    // not sent to the deposit box like ores, so they accumulate for the sack/ladder
                    // upgrades. The overlay counts them from the owned total either way.
                    if (Rs2Inventory.isFull()) {
                        // Full, and none of it is ours to bank: WITHDRAW would bounce straight back here forever.
                        log.warn("[SackSession] Inventory full with nothing to deposit — cannot empty the sack");
                        inventoryClogged = true;
                        transitionSub(SackSubState.FAILED);
                        transition(State.FAILED);
                        break;
                    }
                    scheduleNextAdaptive(350L, CLICK_THROTTLE_MS);
                    transitionSub(SackSubState.CHECK_EMPTY);
                }
                break;

            case CHECK_EMPTY:
                // NOTE: We do NOT explicitly close the deposit box here.
                // Clicking the sack (in WITHDRAW state) automatically closes any
                // open interface. This saves a tick and avoids invoke-based menu
                // interactions that bypass natural mouse movement.
                if (currentSackCount < lastSeenSackCount) {
                    noProgressWithdraws = 0;
                    lastSeenSackCount = currentSackCount;
                }
                if (currentSackCount > 0) {
                    log.info("[SackSession] Sack still has {}/{} ore — looping", currentSackCount, maxSackSize);
                    transitionSub(SackSubState.EVALUATE);
                } else if (hasDepositablesInInventory()) {
                    transitionSub(SackSubState.DEPOSIT);
                } else {
                    log.info("[SackSession] Sack empty — session complete");
                    transitionSub(SackSubState.RETURN_TO_SPOT);
                }
                break;

            case RETURN_TO_SPOT:
                // A click on a game object (ladder, vein, hopper) while the deposit box is still open
                // just closes the interface instead of performing the action — it "eats" the click.
                // We're handing control back to the orchestrator now, which may immediately walk/climb/
                // click something else, so this is the one place that must guarantee the interface is
                // actually gone before reporting complete, regardless of what happens next.
                if (Rs2DepositBox.isOpen()) {
                    log.debug("[SackSession] Deposit box still open on completion — closing before handing back control");
                    Rs2DepositBox.closeDepositBox();
                    scheduleNextAdaptive(150L, 400L);
                    break;
                }
                transition(State.COMPLETE);
                break;

            default:
                break;
        }
    }

    private void transitionSub(SackSubState next) {
        log.debug("[SackSession] Sub-state: {} -> {}", sackSubState, next);
        sackSubState      = next;
        subStateEnteredMs = System.currentTimeMillis();

        switch (next) {
            case TRANSITIONING_FLOOR: updateStatus("Climbing Ladder");          break;
            case WITHDRAW:            updateStatus("Withdrawing Ores");          break;
            case DEPOSIT:             updateStatus("Depositing Ores");         break;
            case RETURN_TO_SPOT:      updateStatus("Returning to Mining Spot"); break;
            default:                  updateStatus(formatEnum(next.name()));     break;
        }
    }

    /**
     * Drops one gem type per call, so the executor thread is never blocked dropping a full load.
     *
     * @return {@code true} if something was dropped (caller should come back next tick)
     */
    private boolean dropGemsFromInventory() {
        for (int gemId : GEM_IDS) {
            if (Rs2Inventory.hasItem(gemId) && Rs2Inventory.interact(gemId, "Drop")) {
                log.debug("[SackSession] Dropped gem {} (Drop Gems is on and no gem bag in use)", gemId);
                applyActionCooldown();
                return true;
            }
        }
        return false;
    }

    /** Anything the deposit box should take — ore or gems. Gates EVALUATE and CHECK_EMPTY, so a
     *  gem-only inventory now routes to DEPOSIT instead of completing the trip with gems still held. */
    private boolean hasDepositablesInInventory() {
        for (int itemId : DEPOSIT_IDS) {
            if (Rs2Inventory.contains(itemId)) return true;
        }
        return false;
    }

    private void captureOreDepositSnapshot() {
        for (int i = 0; i < ORE_IDS.size(); i++) {
            pendingOreDepositSnapshot[i] = Math.max(pendingOreDepositSnapshot[i], Rs2Inventory.itemQuantity(ORE_IDS.get(i)));
        }
    }

    private void captureOreDepositSnapshot(int oreId) {
        int idx = ORE_IDS.indexOf(oreId);
        if (idx >= 0) {
            pendingOreDepositSnapshot[idx] = Math.max(pendingOreDepositSnapshot[idx], Rs2Inventory.itemQuantity(oreId));
        }
    }

    public int[] consumePendingOreDepositSnapshot() {
        int[] copy = java.util.Arrays.copyOf(pendingOreDepositSnapshot, pendingOreDepositSnapshot.length);
        java.util.Arrays.fill(pendingOreDepositSnapshot, 0);
        return copy;
    }

    public WorldPoint getReturnPoint() {
        if (originalMiningSpot == null) return null;
        List<WorldPoint> pts = originalMiningSpot.getWorldPoint();
        return (pts != null && !pts.isEmpty()) ? pts.get(0) : null;
    }
}
