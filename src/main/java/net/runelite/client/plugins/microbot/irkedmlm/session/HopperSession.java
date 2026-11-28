package net.runelite.client.plugins.microbot.irkedmlm.session;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.irkedmlm.IrkedMLMMapConstants;
import net.runelite.client.plugins.microbot.irkedmlm.IrkedMLMConfig;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Tick-progressive HopperSession for Motherlode Mine.
 * Handles walking to and depositing pay-dirt into the hopper.
 *
 * <pre>
 * IDLE → TRANSITIONING_FLOOR → WALKING → DEPOSITING → VERIFYING → COMPLETE
 * </pre>
 *
 * Hopper floor policy: {@link #useUpperHopperForDeposit(IrkedMLMConfig, MLMMiningSpot)}.
 * We climb via {@link #ensureFloor(boolean)} before walk/click so interactions never
 * target the wrong-floor hopper (no-op clicks, stalled verify, recovery loops).
 *
 * Detection: CLICK-FIRST when the desired-floor hopper is in scene ({@link #findDesiredHopper()}),
 * else walk toward {@link #expectedHopperPoint()} (same coordinates as script {@code hopperWalkTargetFor()}).
 */
@Slf4j
public class HopperSession extends Session {

    public enum HopperSubState {
        IDLE,
        TRANSITIONING_FLOOR,
        WALKING,
        DEPOSITING,
        VERIFYING,
        COMPLETE,
        FAILED
    }

    private static final long WALK_THROTTLE_MS   = 600L;
    private static final long CLICK_THROTTLE_MS  = 600L;
    public static final int MAX_DEPOSIT_RETRIES = 3;

    /** Max tile distance from {@link #expectedHopperPoint()} when resolving the hopper object. */
    private static final int HOPPER_LOC_TOLERANCE = IrkedMLMMapConstants.HOPPER_LOC_TOLERANCE;

    @Getter
    private HopperSubState hopperSubState = HopperSubState.IDLE;

    @Getter
    private int initialPayDirtCount = 0;

    /** One-shot: whether we've already taken the occasional "beat before depositing" pause this trip. */
    private boolean preDepositPaused = false;

    /** Active mining area; downstairs spots always use the lower hopper. */
    private MLMMiningSpot depositMiningSpot;

    private final Rs2TileObjectCache tileCache;

    public HopperSession(Rs2TileObjectCache tileCache, IrkedMLMConfig config) {
        super(config);
        this.tileCache = tileCache;
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    /** {@inheritDoc} A hopper trip is a short, deliberate errand between AFK mining bouts — run it briskly. */
    @Override
    protected boolean isFocusedTask() {
        return true;
    }

    @Override
    public void reset() {
        super.reset();
        log.debug("[HopperSession] Resetting session");
        hopperSubState       = HopperSubState.IDLE;
        initialPayDirtCount  = 0;
        preDepositPaused     = false;
        depositMiningSpot    = null;
    }

    @Override
    protected long maxSessionMs() {
        return 150_000L;
    }

    public void begin(MLMMiningSpot miningSpot) {
        this.depositMiningSpot = miningSpot;
        begin();
    }

    @Override
    public void begin() {
        if (!isIdle()) {
            log.warn("[HopperSession] begin() called while not idle");
            return;
        }
        initialPayDirtCount = Rs2Inventory.itemQuantity(ItemID.PAYDIRT);
        log.info("[HopperSession] Beginning session with {} pay-dirt", initialPayDirtCount);
        transitionSub(HopperSubState.IDLE);
        transition(State.ACTIVE);
    }

    private static final long STALL_THRESHOLD_MS = 4000L;
    private static final long DEPOSIT_STALL_MS   = 8000L;

    @Override
    protected void tickInternal() {
        if (waitForFloorReading()) return;
        switch (hopperSubState) {

            case IDLE: {
                if (beginFloorCorrectionIfNeeded()) {
                    break;
                }
                if (tryTransitionToDepositingWhenHopperVisible()) {
                    break;
                }
                transitionSub(HopperSubState.WALKING);
                break;
            }

            case TRANSITIONING_FLOOR: {
                // 1200ms (~2 ticks) is deliberate: a longer guard here stalls the whole deposit trip
                // for its full duration after every ladder climb.
                if (busyMoving(1200)) {
                    scheduleNextAdaptive(180L, 600L);
                    break;
                }
                // Climb to the *desired* hopper floor (upper if config enabled, else lower).
                // After this succeeds we are guaranteed on the correct floor before any walk/click.
                invalidateUpperFloorCache();
                if (ensureFloor(desiredFloorIsUpper())) {
                    invalidateUpperFloorCache();
                    log.info("[HopperSession] Now on correct hopper floor (upper={}) after ladder", desiredFloorIsUpper());
                    transitionSub(HopperSubState.IDLE);
                }
                if (isStalled(20_000L)) {
                    log.warn("[HopperSession] Floor transition stalled — failing");
                    transitionSub(HopperSubState.FAILED);
                    transition(State.FAILED);
                }
                break;
            }

            // ----------------------------------------------------------------
            case WALKING: {
                if (beginFloorCorrectionIfNeeded()) {
                    break;
                }
                if (tryTransitionToDepositingWhenHopperVisible()) {
                    break;
                }

                if (Rs2Player.isMoving()) {
                    scheduleNextAdaptive(180L, 600L);
                    break;
                }

                // Dynamic fallback: if after walking toward the *desired* hopper loc for 4s we still
                // see no hopper in scene, we are probably on the wrong floor for the actual unlocked state.
                // Transition so ensureFloor can correct it before we click the wrong hopper.
                if (subElapsed() > 4_000L && !isDesiredHopperInScene()) {
                    boolean currentUp = isUpperFloor();
                    boolean wantUp = desiredFloorIsUpper();
                    if (currentUp != wantUp) {
                        log.warn("[HopperSession] Desired hopper not visible after 4s and floor mismatch (onUpper={}, want={}) — climbing", currentUp, wantUp);
                        transitionSub(HopperSubState.TRANSITIONING_FLOOR);
                        break;
                    } else if (currentUp) {
                        // Config says upper but no hopper visible on upper after walk — fall back down,
                        // and keep using the lower hopper: without the override ensureFloor(true) was
                        // already satisfied and the trip walked the upper floor forever.
                        log.warn("[HopperSession] Upper hopper not found after 4s on upper — using the lower hopper from now on");
                        upperHopperUnavailable = true;
                        transitionSub(HopperSubState.TRANSITIONING_FLOOR);
                        break;
                    }
                }

                WorldPoint target = expectedHopperPoint();
                if (mayAct(tickDelayMs(250L, WALK_THROTTLE_MS))) {
                    Rs2Walker.walkFastCanvas(target);
                    scheduleNextAdaptive(250L, WALK_THROTTLE_MS);
                }

                if (isStalled(STALL_THRESHOLD_MS)) {
                    log.warn("[HopperSession] Walk to hopper stalled");
                    transitionSub(HopperSubState.FAILED);
                    transition(State.FAILED);
                }
                break;
            }

            // ----------------------------------------------------------------
            case DEPOSITING: {
                if (beginFloorCorrectionIfNeeded()) {
                    break;
                }

                if (Rs2Player.isInteracting() || Rs2Player.isAnimating() || Rs2Player.isMoving()) {
                    scheduleNextAdaptive(180L, 600L);
                    break;
                }

                // Human variation: ~30% of the time take a short, once-per-trip beat before depositing
                // so the deposit cadence isn't uniform. Fast mode never does this.
                if (isHumanLikeEnabled() && !preDepositPaused && Rs2Random.between(0, 100) < 30) {
                    preDepositPaused = true;
                    scheduleNext(Rs2Random.between(350, 1400));
                    break;
                }

                Rs2TileObjectModel hopper = findDesiredHopper();
                if (hopper == null) {
                    retreatFromMissingHopper();
                    break;
                }

                if (clickOnMyFloor(hopper, "Deposit")) {
                    log.info("[HopperSession] Deposit interaction sent");
                    applyActionCooldown();
                    // Wider human gap so repeated deposit clicks (retries) aren't metronomic.
                    scheduleNextAdaptive(280L, 950L);
                    transitionSub(HopperSubState.VERIFYING);
                } else {
                    scheduleNextAdaptive(280L, CLICK_THROTTLE_MS);
                }

                if (isStalled(DEPOSIT_STALL_MS)) {
                    log.debug("[HopperSession] Deposit stalled — retrying interaction");
                    subStateEnteredMs = System.currentTimeMillis();
                }
                break;
            }

            // ----------------------------------------------------------------
            case VERIFYING: {
                // Wait for deposit animation to finish before checking inventory
                if (Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
                    scheduleNextAdaptive(180L, 600L);
                    break;
                }
                int currentCount = Rs2Inventory.itemQuantity(ItemID.PAYDIRT);
                if (currentCount < initialPayDirtCount) {
                    log.info("[HopperSession] Deposit confirmed ({} → {} pay-dirt)",
                            initialPayDirtCount, currentCount);
                    transitionSub(HopperSubState.COMPLETE);
                    transition(State.COMPLETE);
                } else if (isStalled(DEPOSIT_STALL_MS)) {
                    // The hopper took nothing: fail at once rather than click again. A full sack refuses
                    // every click, and the script's deposit audit decides between a retry, the wheel and
                    // emptying the sack.
                    log.warn("[HopperSession] Verify stalled with no progress ({}→{}) — failing session (sack likely full, avoid click spam)",
                            initialPayDirtCount, currentCount);
                    transitionSub(HopperSubState.FAILED);
                    transition(State.FAILED);
                }
                break;
            }

            default:
                break;
        }
    }

    // ── hopper floor & object resolution ─────────────────────────────────────

    private boolean desiredFloorIsUpper() {
        return !upperHopperUnavailable && useUpperHopperForDeposit(config, depositMiningSpot);
    }

    /** Set once the upper hopper proved missing; kept across trips until the plugin restarts. */
    private boolean upperHopperUnavailable = false;

    /** Called when a new run starts, so the fallback doesn't outlive the run that found it. */
    public void resetForNewRun() {
        upperHopperUnavailable = false;
    }

    /** Walk/click target for the hopper on the desired floor. */
    private WorldPoint expectedHopperPoint() {
        return desiredFloorIsUpper() ? IrkedMLMMapConstants.UPPER_HOPPER_WALK : IrkedMLMMapConstants.LOWER_HOPPER_WALK;
    }

    /** Whether deposits for this spot should use the upper hopper (config + spot floor). */
    public static boolean useUpperHopperForDeposit(IrkedMLMConfig config, MLMMiningSpot spot) {
        return spot != null && spot.isUpstairs() && config.upstairsHopperUnlocked();
    }

    /** Walk target for the hopper matching spot floor and unlock state. */
    public static WorldPoint hopperWalkTargetFor(IrkedMLMConfig config, MLMMiningSpot spot) {
        return useUpperHopperForDeposit(config, spot)
                ? IrkedMLMMapConstants.UPPER_HOPPER_WALK
                : IrkedMLMMapConstants.LOWER_HOPPER_WALK;
    }

    private boolean needsFloorTransition() {
        return !isOnCorrectHopperFloor();
    }

    /**
     * Hopper on the desired floor near {@link #expectedHopperPoint()}. Returns null when off-floor
     * or not in scene — both floors use the same object ID, so floor + position filter are required.
     */
    private Rs2TileObjectModel findDesiredHopper() {
        if (!isOnCorrectHopperFloor()) {
            return null;
        }
        WorldPoint anchor = expectedHopperPoint();
        return tileCache.query()
                .where(o -> o.getId() == IrkedMLMMapConstants.HOPPER_OBJECT_ID && o.getWorldLocation().distanceTo(anchor) <= HOPPER_LOC_TOLERANCE)
                .nearest();
    }

    private boolean isDesiredHopperInScene() {
        return findDesiredHopper() != null;
    }

    /**
     * Exposed for the orchestrating script (logging / pre-begin checks) without duplicating floor math.
     */
    public boolean isOnCorrectHopperFloor() {
        return onFloor(currentFloor(), desiredFloorIsUpper());
    }

    // ── shared sub-state guards (IDLE / WALKING / DEPOSITING) ────────────────

    /** Climb first when we are on the wrong floor for the hopper we intend to use. */
    private boolean beginFloorCorrectionIfNeeded() {
        if (!needsFloorTransition()) {
            return false;
        }
        log.debug("[HopperSession] Wrong floor for the desired hopper (onUpper={}, wantUpper={}) — climbing first",
                isUpperFloor(), desiredFloorIsUpper());
        transitionSub(HopperSubState.TRANSITIONING_FLOOR);
        return true;
    }

    /** CLICK-FIRST: promote to DEPOSITING when the correct-floor hopper enters the scene. */
    private boolean tryTransitionToDepositingWhenHopperVisible() {
        if (!isDesiredHopperInScene()) {
            return false;
        }
        log.info("[HopperSession] Desired hopper at {} in scene — depositing", expectedHopperPoint());
        transitionSub(HopperSubState.DEPOSITING);
        return true;
    }

    private void retreatFromMissingHopper() {
        if (beginFloorCorrectionIfNeeded()) {
            return;
        }
        log.debug("[HopperSession] Desired hopper not at {} — falling back to WALKING",
                expectedHopperPoint());
        transitionSub(HopperSubState.WALKING);
    }


    private void transitionSub(HopperSubState next) {
        log.debug("[HopperSession] Sub-state: {} → {}", hopperSubState, next);
        hopperSubState    = next;
        subStateEnteredMs = System.currentTimeMillis();

        switch (next) {
            case TRANSITIONING_FLOOR: updateStatus("Climbing Ladder"); break;
            case WALKING:             updateStatus("Walking to Hopper"); break;
            case DEPOSITING:          updateStatus("Depositing Pay-dirt"); break;
            case VERIFYING:           updateStatus("Verifying Deposit"); break;
            case COMPLETE:            updateStatus("Deposit Complete"); break;
            default:                  updateStatus(formatEnum(next.name())); break;
        }
    }

}
