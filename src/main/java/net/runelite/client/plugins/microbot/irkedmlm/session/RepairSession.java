package net.runelite.client.plugins.microbot.irkedmlm.session;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.irkedmlm.HumanBehaviorProfile;
import net.runelite.client.plugins.microbot.irkedmlm.IrkedMLMMapConstants;
import net.runelite.client.plugins.microbot.irkedmlm.DroppedPayDirt;
import net.runelite.client.plugins.microbot.irkedmlm.IrkedMLMConfig;
import net.runelite.client.plugins.microbot.irkedmlm.MlmRepairEtiquette;
import net.runelite.client.plugins.microbot.irkedmlm.SessionPersonality;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Tick-progressive RepairSession for Motherlode Mine.
 * Handles fetching a hammer and repairing broken struts on the waterwheel.
 */
@Slf4j
public class RepairSession extends Session {

    public enum RepairSubState {
        IDLE,
        TRANSITIONING_FLOOR,
        EVALUATE,
        NEED_HAMMER,
        FETCH_HAMMER_EVAL,
        FETCH_HAMMER_CLEAR_SPACE,
        FETCH_HAMMER_WALK_CRATE,
        FETCH_HAMMER_SEARCH_CRATE,
        FETCH_HAMMER_VERIFY,
        WALK_TO_STRUT,
        CLICK_STRUT,
        ANIMATING,
        VERIFY,
        CLEANUP,
        DONE,
        FAILED
    }

    private static final long WALK_THROTTLE_MS   = 1400L;
    private static final long SETTLE_DELAY_MS    = 600L;

    /**
     * How long to wait for the repair animation to START after clicking the strut.
     */
    private static final long ANIM_START_TIMEOUT_MS  = 10000L;

    /**
     * How long to wait for the repair animation to FINISH once it has started.
     */
    private static final long ANIM_FINISH_TIMEOUT_MS = 8000L;

    /** Fast/optimal-mode fallback give-up (no personality to draw from) — generous, since repair is a
     *  skill roll needing several attempts on average; this is a safety net against a genuinely stuck
     *  state, not a normal exit path. See {@link SessionPersonality#repairPersistenceMs()} for human mode. */
    private static final long REPAIR_GIVE_UP_FALLBACK_MS = 90_000L;
    private static final int MAX_CRATE_SEARCHES  = 3;

    /** The MLM hammer supply crate (generic crate id, confirmed in-game for this scene). */
    private static final int SUPPLY_CRATE_ID = 357;
    private static final int STRUT_ID        = ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN;

    @Getter
    private RepairSubState repairSubState = RepairSubState.IDLE;

    private final Rs2TileObjectCache tileCache;

    private WorldPoint targetStrutPoint;
    private int        targetStrutId = -1;

    /** Last repaired strut (persists across jobs for alternate-waterwheel preference; dist>6 heuristic). */
    private WorldPoint lastRepairedStrutPoint = null;

    private int  repairAttempts;
    private int  crateSearchAttempts;
    private int  brokenBeforeClick = -1;

    /** Wall-clock start of the current repair job (set in {@link #begin()}), used by the
     *  time-based give-up in {@link #tickVerify()} instead of a fixed attempt count. */
    private long repairJobStartMs = 0L;

    private boolean animStartObserved = false;
    private boolean postRepairPaused = false;

    /** One-shot: cursor/camera already led ahead to the next target during the ladder descent. */
    private boolean descentOrientDone = false;
    /** One-shot: rolled (once per descent) whether to anticipate at all this trip. */
    private boolean descentHoverDecided = false;
    /** Result of that roll — false means "just wait for the climb and click on arrival". */
    private boolean descentHoverWanted = false;
    private boolean hadOwnHammer = false;
    /** Ended because somebody else is at the wheel, which is not a failed repair. */
    @Getter
    private boolean leftToBystander = false;

    /** When true, do not back off for players standing near the wheel — the orchestrator has
     *  determined (via a deferral timeout) that the bystander is idle, not repairing. */
    private boolean ignoreNearbyPlayers = false;

    /** Shared bystander policy, owned by the orchestrator so both sides agree on "at the wheel". */
    private final MlmRepairEtiquette etiquette;

    /** The script's claim on pay-dirt left on the floor, so the one dropped here is picked up again too. */
    private final DroppedPayDirt droppedPayDirt;

    public RepairSession(Rs2TileObjectCache tileCache, IrkedMLMConfig config, MlmRepairEtiquette etiquette,
                                              DroppedPayDirt droppedPayDirt) {
        super(config);
        this.tileCache = tileCache;
        this.etiquette = etiquette;
        this.droppedPayDirt = droppedPayDirt;
    }

    /** {@inheritDoc} A broken wheel stops the pay-dirt flow, so the player fixes it with full attention. */
    @Override
    protected boolean isFocusedTask() {
        return true;
    }

    @Override
    protected long maxSessionMs() {
        return 240_000L; // crate walk + up to 90s of repair rolls
    }

    @Override
    public void reset() {
        super.reset();
        repairSubState      = RepairSubState.IDLE;
        repairAttempts      = 0;
        crateSearchAttempts = 0;
        targetStrutPoint    = null;
        targetStrutId       = -1;
        brokenBeforeClick   = -1;
        animStartObserved   = false;
        postRepairPaused    = false;
        ignoreNearbyPlayers = false;
        repairJobStartMs    = 0L;
        descentOrientDone   = false;
        descentHoverDecided = false;
        descentHoverWanted  = false;
        hadOwnHammer        = false;
        leftToBystander     = false;
        // lastRepairedStrutPoint intentionally persists (for wheel swap); cleared only on new instance.
    }

    /**
     * @param ignoreNearby when true, the session will not defer to players standing near the wheel
     *                     (the orchestrator decided they are idle, not repairing).
     */
    public void begin(boolean ignoreNearby) {
        if (!isIdle()) {
            log.warn("[RepairSession] begin() called while not idle");
            return;
        }
        this.ignoreNearbyPlayers = ignoreNearby;
        begin();
    }

    @Override
    public void begin() {
        if (!isIdle()) {
            log.warn("[RepairSession] begin() called while not idle");
            return;
        }
        // A plain hammer the player brought is theirs; only one fetched from the crate gets dropped.
        hadOwnHammer        = Rs2Inventory.hasItem(ItemID.HAMMER);
        repairAttempts      = 0;
        crateSearchAttempts = 0;
        targetStrutPoint    = null;
        targetStrutId       = -1;
        brokenBeforeClick   = -1;
        animStartObserved   = false;
        descentOrientDone   = false;
        descentHoverDecided = false;
        descentHoverWanted  = false;
        repairJobStartMs    = System.currentTimeMillis();
        // lastRepairedStrutPoint persists for alternation (see selectTargetStrut).
        transitionSub(RepairSubState.IDLE);
        transition(State.ACTIVE);
    }

    private static final long STALL_THRESHOLD_MS = 5000L;

    @Override
    protected long postLadderSettleDelayMs() {
        return HumanBehaviorProfile.repairPostLadderSettleDelayMs(isHumanLikeEnabled());
    }

    @Override
    protected void tickInternal() {
        if (waitForFloorReading()) return;
        switch (repairSubState) {

            case IDLE:
                if (isUpperFloor()) {
                    transitionSub(RepairSubState.TRANSITIONING_FLOOR);
                } else {
                    transitionSub(RepairSubState.EVALUATE);
                }
                break;

            case TRANSITIONING_FLOOR:
                // 1200ms (~2 ticks) is deliberate: a longer guard idles here after the climb and stacks
                // with the notice and settle delays into a very visible wait before the crate click.
                if (busyMoving(1200)) {
                    // Eyes lead the hand: while still on the ladder, drift the cursor/camera toward the
                    // thing we'll interact with next (crate if we need a hammer, else the strut). The
                    // lower-floor scene around the ladder is already loaded during the climb, so on
                    // landing the target is framed and the cursor is on it — a fast, committed click
                    // instead of the robotic land → find → turn → settle → click.
                    preemptiveOrientToTarget();
                    scheduleNextAdaptive(180L, 600L);
                    break;
                }
                if (ensureFloor(false)) {
                    transitionSub(RepairSubState.EVALUATE);
                }
                break;

            case EVALUATE:
                tickEvaluate();
                break;

            case NEED_HAMMER:
                tickNeedHammer();
                break;

            case FETCH_HAMMER_EVAL:
                tickFetchHammerEval();
                break;

            case FETCH_HAMMER_CLEAR_SPACE:
                tickFetchHammerClearSpace();
                break;

            case FETCH_HAMMER_WALK_CRATE:
                tickFetchHammerWalkCrate();
                break;

            case FETCH_HAMMER_SEARCH_CRATE:
                tickFetchHammerSearchCrate();
                break;

            case FETCH_HAMMER_VERIFY:
                tickFetchHammerVerify();
                break;

            case WALK_TO_STRUT:
                tickWalkToStrut();
                break;

            case CLICK_STRUT:
                tickClickStrut();
                break;

            case ANIMATING:
                tickAnimating();
                break;

            case VERIFY:
                tickVerify();
                break;

            case CLEANUP:
                tickCleanup();
                break;

            default:
                break;
        }
    }

    // -----------------------------------------------------------------------
    // Sub-state handlers
    // -----------------------------------------------------------------------

    private void tickEvaluate() {
        if (countBrokenStruts() == 0) {
            transitionSub(RepairSubState.CLEANUP);
            return;
        }
        if (hasHammer()) {
            selectTargetStrut();

            if (isObjectInScene(STRUT_ID)) {
                log.info("[RepairSession] Strut detected in scene — moving to CLICK_STRUT");
                transitionSub(RepairSubState.CLICK_STRUT);
            } else {
                transitionSub(RepairSubState.WALK_TO_STRUT);
            }
        } else {
            // Snappy hammer fetch: skip the NEED_HAMMER → FETCH_HAMMER_EVAL routing hops (each burned a
            // full orchestrator tick — ~0.5-1s of dead time before we even moved toward the crate).
            // Reset the search counter here (what NEED_HAMMER did) and route straight to searching the
            // crate if it's already in view, else walking to it. Every acting state still schedules with
            // randomized delays, so this is snappier without becoming metronomic.
            crateSearchAttempts = 0;
            transitionSub(isObjectInScene(SUPPLY_CRATE_ID)
                    ? RepairSubState.FETCH_HAMMER_SEARCH_CRATE
                    : RepairSubState.FETCH_HAMMER_WALK_CRATE);
        }
    }

    private void tickNeedHammer() {
        if (hasHammer()) {
            selectTargetStrut();
            transitionSub(RepairSubState.WALK_TO_STRUT);
            return;
        }
        crateSearchAttempts = 0;
        transitionSub(RepairSubState.FETCH_HAMMER_EVAL);
    }

    private void tickFetchHammerEval() {
        if (crateSearchAttempts < MAX_CRATE_SEARCHES) {
            if (isObjectInScene(SUPPLY_CRATE_ID)) {
                transitionSub(RepairSubState.FETCH_HAMMER_SEARCH_CRATE);
            } else {
                transitionSub(RepairSubState.FETCH_HAMMER_WALK_CRATE);
            }
        } else {
            transitionSub(RepairSubState.FAILED);
            transition(State.FAILED);
        }
    }

    private void tickFetchHammerClearSpace() {
        if (!Rs2Inventory.isFull()) {
            transitionSub(RepairSubState.FETCH_HAMMER_WALK_CRATE);
            return;
        }
        // Only a drop on a known tile and floor, standing still, can be found again.
        WorldPoint here = Rs2Player.getWorldLocation();
        Floor floor = currentFloor();
        if (here == null || floor == Floor.UNKNOWN || Rs2Player.isMoving()) {
            scheduleNextAdaptive(180L, 600L);
            return;
        }
        long now = System.currentTimeMillis();
        // Never a second pile elsewhere. Failing here is bounded, repeated repair failures back off.
        if (!droppedPayDirt.canAdd(here, floor == Floor.UPPER, now)) {
            log.info("[RepairSession] No room for a hammer, and pay-dirt is already waiting elsewhere — leaving the repair for now");
            transitionSub(RepairSubState.FAILED);
            transition(State.FAILED);
            return;
        }
        if (Rs2Inventory.drop(ItemID.PAYDIRT)) {
            droppedPayDirt.add(here, floor == Floor.UPPER, 1, now);
            scheduleNextAdaptive(180L, 600L);
            transitionSub(RepairSubState.FETCH_HAMMER_WALK_CRATE);
        } else {
            transitionSub(RepairSubState.FAILED);
            transition(State.FAILED);
        }
    }

    private void tickFetchHammerWalkCrate() {
        if (isObjectInScene(SUPPLY_CRATE_ID)) {
            log.info("[RepairSession] Crate detected during walk — interrupting.");
            transitionSub(RepairSubState.FETCH_HAMMER_SEARCH_CRATE);
            return;
        }

        if (Rs2Player.isMoving()) {
            scheduleNextAdaptive(180L, 600L);
            return;
        }

        if (mayAct(tickDelayMs(400L, WALK_THROTTLE_MS))) {
            Rs2Walker.walkFastCanvas(IrkedMLMMapConstants.SUPPLY_CRATE_POINT, true);
        }

        if (isStalled(STALL_THRESHOLD_MS)) {
            log.warn("[RepairSession] Walk to crate stalled");
            transitionSub(RepairSubState.FAILED);
            transition(State.FAILED);
        }
    }

    private void tickFetchHammerSearchCrate() {
        if (Rs2Inventory.isFull()) {
            transitionSub(RepairSubState.FETCH_HAMMER_CLEAR_SPACE);
            return;
        }

        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            scheduleNextAdaptive(180L, 600L);
            return;
        }

        var crate = findSupplyCrate();
        if (crate == null) {
            log.debug("[RepairSession] Crate lost from view — falling back to walk");
            transitionSub(RepairSubState.FETCH_HAMMER_WALK_CRATE);
            return;
        }

        // On-screen before clicking so the natural mouse glides onto the crate instead of the (1,1)
        // corner fallback in Microbot.doInvoke.
        if (!Rs2Camera.isTileOnScreen(crate.getLocalLocation())) {
            Rs2Camera.turnTo(crate);
            scheduleNextAdaptive(150L, 450L);
            return;
        }

        log.debug("[RepairSession] Searching crate (ID: {})", SUPPLY_CRATE_ID);
        if (clickOnMyFloor(crate, "Search")) {
            applyActionCooldown();
            crateSearchAttempts++;
            scheduleNextAdaptive(350L, 1000L);
            transitionSub(RepairSubState.FETCH_HAMMER_VERIFY);
            return;
        }

        if (isStalled(STALL_THRESHOLD_MS)) {
            transitionSub(RepairSubState.FAILED);
            transition(State.FAILED);
        }
    }

    private void tickFetchHammerVerify() {
        if (hasHammer()) {
            selectTargetStrut();
            transitionSub(RepairSubState.WALK_TO_STRUT);
            return;
        }
        // Give the search time to resolve before deciding to search again — one search yields the
        // hammer a tick or two later. Re-clicking immediately is what double-searched the crate.
        if (Rs2Player.isInteracting() || Rs2Player.isAnimating() || Rs2Player.isMoving()) {
            scheduleNextAdaptive(180L, 600L);
            return;
        }
        if (subElapsed() < 1800L) {
            scheduleNextAdaptive(180L, 600L);
            return;
        }
        if (crateSearchAttempts < MAX_CRATE_SEARCHES) {
            transitionSub(RepairSubState.FETCH_HAMMER_EVAL);
        } else {
            transitionSub(RepairSubState.FAILED);
            transition(State.FAILED);
        }
    }

    private void tickWalkToStrut() {
        if (countBrokenStruts() == 0) {
            transitionSub(RepairSubState.CLEANUP);
            return;
        }

        if (isAnotherPlayerRepairing()) {
            log.info("[RepairSession] Another player is repairing — backing off");
            leftToBystander = true;
            transitionSub(RepairSubState.CLEANUP);
            return;
        }

        if (!hasHammer()) {
            log.info("[RepairSession] No hammer when about to walk to strut — must fetch from crate first");
            transitionSub(RepairSubState.NEED_HAMMER);
            return;
        }

        if (isObjectInScene(STRUT_ID)) {
            log.info("[RepairSession] Strut detected during walk — interrupting.");
            selectTargetStrut();
            transitionSub(RepairSubState.CLICK_STRUT);
            return;
        }

        if (Rs2Player.isMoving()) {
            scheduleNextAdaptive(180L, 600L);
            return;
        }

        if (targetStrutPoint == null) {
            selectTargetStrut();
            if (targetStrutPoint == null) {
                transitionSub(RepairSubState.CLEANUP);
                return;
            }
        }

        if (mayAct(tickDelayMs(400L, WALK_THROTTLE_MS))) {
            Rs2Walker.walkFastCanvas(targetStrutPoint, true);
        }

        if (isStalled(STALL_THRESHOLD_MS)) {
            log.warn("[RepairSession] Walk to strut stalled");
            transitionSub(RepairSubState.FAILED);
            transition(State.FAILED);
        }
    }

    private void tickClickStrut() {
        if (countBrokenStruts() == 0) {
            transitionSub(RepairSubState.CLEANUP);
            return;
        }

        if (isAnotherPlayerRepairing()) {
            log.info("[RepairSession] Another player started repairing — backing off");
            leftToBystander = true;
            transitionSub(RepairSubState.CLEANUP);
            return;
        }

        if (!hasHammer()) {
            log.info("[RepairSession] No hammer when about to click repair on strut — must fetch from crate first");
            transitionSub(RepairSubState.NEED_HAMMER);
            return;
        }

        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            scheduleNextAdaptive(180L, 600L);
            return;
        }

        var target = findStrutAt(targetStrutPoint, targetStrutId);
        if (target == null) {
            transitionSub(RepairSubState.WALK_TO_STRUT);
            return;
        }

        // Ensure the strut is actually on-screen before clicking. Otherwise its clickbox is off-canvas
        // and Microbot.doInvoke falls back to clicking the (1,1) corner instead of gliding the natural
        // mouse onto the strut. Turn the camera and retry next tick (mirrors MiningSession's vein click).
        // ensureClickable turns, and pulls the camera back when turning cannot frame it — a strut you
        // are stood on top of never fits the clickbox test at close zoom, which used to stall the repair.
        if (!ensureClickable(target)) {
            scheduleNextAdaptive(150L, 450L);
            return;
        }

        log.debug("[RepairSession] Repairing strut (ID: {})", targetStrutId);
        if (clickOnMyFloor(target, "Repair")) {
            applyActionCooldown();
            brokenBeforeClick   = countBrokenStruts();
            animStartObserved   = false;
            transitionSub(RepairSubState.ANIMATING);
            scheduleNextAdaptive(180L, 600L);
            return;
        }

        if (isStalled(STALL_THRESHOLD_MS)) {
            transitionSub(RepairSubState.FAILED);
            transition(State.FAILED);
        }
    }

    private void tickAnimating() {
        long animElapsed = subElapsed();

        if (!animStartObserved) {
            if (Rs2Player.isAnimating()) {
                animStartObserved = true;
                log.debug("[RepairSession] Repair animation started");
                subStateEnteredMs = System.currentTimeMillis();
            } else if (Rs2Player.isInteracting() || Rs2Player.isMoving()) {
                scheduleNextAdaptive(180L, 600L);
            } else if (animElapsed > ANIM_START_TIMEOUT_MS) {
                log.warn("[RepairSession] Repair animation never started after {}ms — proceeding to verify",
                        ANIM_START_TIMEOUT_MS);
                scheduleNextAdaptive(200L, SETTLE_DELAY_MS);
                transitionSub(RepairSubState.VERIFY);
            }
        } else {
            if (!Rs2Player.isAnimating()) {
                log.debug("[RepairSession] Repair animation finished");
                scheduleNextAdaptive(200L, SETTLE_DELAY_MS);
                transitionSub(RepairSubState.VERIFY);
            } else if (animElapsed > ANIM_FINISH_TIMEOUT_MS) {
                log.warn("[RepairSession] Repair animation still running after {}ms — forcing verify",
                        ANIM_FINISH_TIMEOUT_MS);
                scheduleNextAdaptive(200L, SETTLE_DELAY_MS);
                transitionSub(RepairSubState.VERIFY);
            }
        }
    }

    private void tickVerify() {
        int broken = countBrokenStruts();

        if (brokenBeforeClick >= 0 && broken < brokenBeforeClick) {
            if (targetStrutPoint != null) {
                lastRepairedStrutPoint = targetStrutPoint;
            }
            repairAttempts   = 0;
            targetStrutPoint = null;
            targetStrutId    = -1;

            // Only keep going while the water is still frozen. Once a wheel is turning again the
            // hopper processes pay-dirt, so repairing the other (still-broken) wheel would be
            // repairing while one is already running — exactly what we must not do.
            if (broken > 0 && !isWaterRunning() && isHumanLikeEnabled() && Rs2Random.between(0, 100) < 70) {
                log.info("[RepairSession] Water still frozen; continuing to next broken strut");
                transitionSub(RepairSubState.EVALUATE);
            } else {
                log.info("[RepairSession] Water running again; repair complete — moving to cleanup");
                transitionSub(RepairSubState.CLEANUP);
            }
            return;
        }

        repairAttempts++;
        if (repairGiveUpElapsed()) {
            log.warn("[RepairSession] Giving up after {} attempts / {}ms — strut still broken (repair is a Smithing skill-roll, not guaranteed per click)",
                    repairAttempts, System.currentTimeMillis() - repairJobStartMs);
            transitionSub(RepairSubState.CLEANUP);
        } else {
            targetStrutPoint = null;
            targetStrutId    = -1;
            transitionSub(RepairSubState.WALK_TO_STRUT);
        }
    }

    /**
     * True once we've spent long enough failing to fix the strut that giving up (and re-fetching a
     * hammer from scratch next cycle) is more sensible than continuing. Time-based rather than a fixed
     * attempt count: the repair click itself is a Smithing-gated skill roll (~12% success at level 1,
     * ~28% at 99), needing several attempts on average — a small fixed cap gave up mid-job most of the
     * time, which is why hammer-fetching looked so frequent/slow. See {@link SessionPersonality#repairPersistenceMs()}.
     */
    private boolean repairGiveUpElapsed() {
        if (repairJobStartMs == 0L) {
            return false; // job not started yet (reset state) — elapsed would be epoch-huge; never give up here
        }
        long limitMs = isHumanLikeEnabled() ? personality.repairPersistenceMs() : REPAIR_GIVE_UP_FALLBACK_MS;
        return System.currentTimeMillis() - repairJobStartMs >= limitMs;
    }

    private void tickCleanup() {
        if (isHumanLikeEnabled() && Rs2Random.between(0, 100) < 20 && !postRepairPaused) {
            postRepairPaused = true;
            long pauseMs = Rs2Random.between(1000, 2000);
            log.debug("[RepairSession] Post-repair admiration pause: {}ms", pauseMs);
            scheduleNext(pauseMs);
            return;
        }

        // Only ever drop the plain hammer we fetched from the crate. Never drop an Imcando hammer —
        // it's the player's own (100M+) item and works from the inventory, so it must survive cleanup.
        if (!hadOwnHammer && Rs2Inventory.hasItem(ItemID.HAMMER)) {
            log.debug("[RepairSession] Cleanup: dropping fetched hammer");
            Rs2Inventory.dropAll(ItemID.HAMMER);
            scheduleNext(1200L);
            return;
        }
        log.debug("[RepairSession] Cleanup: hammer gone — finishing");
        transitionSub(RepairSubState.DONE);
        transition(State.COMPLETE);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    public int countBrokenStruts() {
        return tileCache.query()
                .where(o -> o.getId() == ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN)
                .count();
    }

    /** True while at least one waterwheel is turning (water flowing, hopper processing). Pay-dirt
     *  only freezes when both wheels are broken, so this is the real "repair no longer needed" signal. */
    private boolean isWaterRunning() {
        return tileCache.query()
                .where(o -> o.getId() == ObjectID.MOTHERLODE_WHEEL_FIXED)
                .count() > 0;
    }

    private void selectTargetStrut() {
        var struts = tileCache.query()
                .where(o -> o.getId() == ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN)
                .toList();
        if (struts.isEmpty()) return;

        Rs2TileObjectModel chosen;
        if (struts.size() > 1 && lastRepairedStrutPoint != null) {
            java.util.List<Rs2TileObjectModel> otherWheel = new java.util.ArrayList<>();
            for (var s : struts) {
                if (s.getWorldLocation().distanceTo(lastRepairedStrutPoint) > 6) {
                    otherWheel.add(s);
                }
            }
            if (!otherWheel.isEmpty()) {
                chosen = otherWheel.get(Rs2Random.between(0, otherWheel.size()));
                targetStrutPoint = chosen.getWorldLocation();
                targetStrutId    = chosen.getId();
                log.info("[RepairSession] Selected strut from OTHER waterwheel (swap) at {} (last was {})",
                        targetStrutPoint, lastRepairedStrutPoint);
                return;
            }
        }

        chosen = struts.get(Rs2Random.between(0, struts.size()));
        targetStrutPoint = chosen.getWorldLocation();
        targetStrutId    = chosen.getId();
        log.debug("[RepairSession] Selected random strut at {} (ID: {}) from {} options",
                targetStrutPoint, targetStrutId, struts.size());
    }

    private Rs2TileObjectModel findStrutAt(WorldPoint wp, int id) {
        return tileCache.query()
                .where(o -> o.getId() == id && (wp == null || wp.equals(o.getWorldLocation())))
                .nearest();
    }

    private Rs2TileObjectModel findSupplyCrate() {
        return tileCache.query()
                .where(o -> o.getId() == SUPPLY_CRATE_ID)
                .nearest();
    }

    private Rs2TileObjectModel findNearestBrokenStrut() {
        return tileCache.query()
                .where(o -> o.getId() == ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN)
                .nearest();
    }

    /**
     * Anticipatory targeting during the ladder descent — the "eyes lead the hand" beat. Once per
     * descent (human-like only), pick the next thing we'll act on (the strut if we already have a
     * hammer, otherwise the supply crate) and either turn the camera to frame it or, once it's on
     * screen, rest the cursor inside its clickbox. On landing the interaction is then a short,
     * confident click rather than a fresh find/turn/settle. If the object isn't loaded yet we just
     * return and retry on the next descent tick.
     */
    private void preemptiveOrientToTarget() {
        if (!isHumanLikeEnabled() || descentOrientDone) {
            return;
        }

        // Decide ONCE per descent whether to anticipate at all. A human doesn't always lead the
        // cursor — sometimes they just watch the climb and click on arrival. Reuses the same
        // "hover-ahead" trait the mining loop uses, so a given player is consistent across both.
        if (!descentHoverDecided) {
            descentHoverDecided = true;
            descentHoverWanted  = personality.roll(personality.lateHoverChance());
            if (!descentHoverWanted) {
                descentOrientDone = true; // opt out for this trip
                return;
            }
        }

        // Correct-level guard: only lead toward the lower-floor target once we've actually left the
        // upper floor. Never turn the camera / hover while still up top — the target isn't on our
        // level yet and may not be reachable. The crate and struts exist only on the lower floor, so
        // once we're down the id lookup is unambiguous.
        if (!onFloor(currentFloor(), false)) {
            return;
        }

        Rs2TileObjectModel target = hasHammer() ? findNearestBrokenStrut() : findSupplyCrate();
        if (target == null || target.getLocalLocation() == null) {
            return; // scene not loaded around the ladder yet — try again next tick
        }
        if (!Rs2Camera.isTileOnScreen(target.getLocalLocation())) {
            Rs2Camera.turnTo(target); // pre-frame; hover on a subsequent tick once it's on screen
            return;
        }
        if (hoverObject(target)) {
            descentOrientDone = true;
        }
    }

    private boolean hasHammer() {
        // Imcando hammer (main-hand 25644 or off-hand 29775) only counts when the player opted in via
        // config — that is the "I brought my own, don't fetch from the crate" signal. A plain hammer
        // always counts (it's what we fetch from the crate).
        if (config.useImcandoHammer() && hasImcandoHammer()) {
            return true;
        }
        return Rs2Equipment.isWearing(ItemID.HAMMER)
                || Rs2Inventory.hasItem(ItemID.HAMMER);
    }

    private boolean hasImcandoHammer() {
        return Rs2Equipment.isWearing(ItemID.IMCANDO_HAMMER, ItemID.IMCANDO_HAMMER_OFFHAND)
                || Rs2Inventory.hasItem(ItemID.IMCANDO_HAMMER, ItemID.IMCANDO_HAMMER_OFFHAND);
    }

    /**
     * True when another player is at the waterwheel and we should leave the job to them.
     *
     * <p>Checked at both {@code WALK_TO_STRUT} and {@code CLICK_STRUT} so we also back off if someone
     * walks up mid-job. The radius and the local-name exclusion live in
     * {@link net.runelite.client.plugins.microbot.irkedmlm.MlmRepairEtiquette} so the orchestrator's
     * pre-deposit gate and this in-session check can never disagree about what "at the wheel" means.
     */
    private boolean isAnotherPlayerRepairing() {
        if (ignoreNearbyPlayers) {
            // Orchestrator decided the bystander is idle (deferral timed out) — take over the repair.
            return false;
        }
        if (!config.waitForOthersToRepair()) {
            return false; // the user would rather we just fixed it
        }
        int nearby = etiquette.playersAtWheel(cachedLocalPlayerName);
        if (nearby > 0) {
            log.debug("[RepairSession] {} other player(s) at the waterwheel — deferring repair", nearby);
            return true;
        }
        return false;
    }

    public boolean canFastComplete() {
        return countBrokenStruts() == 0 || isWaterRunning();
    }

    private void transitionSub(RepairSubState next) {
        log.debug("[RepairSession] Sub-state: {} → {}", repairSubState, next);
        repairSubState    = next;
        subStateEnteredMs = System.currentTimeMillis();

        switch (next) {
            case TRANSITIONING_FLOOR:    updateStatus("Climbing Ladder"); break;
            case FETCH_HAMMER_WALK_CRATE: updateStatus("Walking to Hammer Crate"); break;
            case FETCH_HAMMER_SEARCH_CRATE: updateStatus("Searching Hammer Crate"); break;
            case WALK_TO_STRUT:          updateStatus("Walking to Waterwheel"); break;
            case CLICK_STRUT:            updateStatus("Repairing Waterwheel"); break;
            case ANIMATING:              updateStatus("Fixing Strut"); break;
            case CLEANUP:                updateStatus("Dropping Hammer"); break;
            case DONE:                   updateStatus("Repair Complete"); break;
            case EVALUATE:               updateStatus("Evaluating Struts"); break;
            case NEED_HAMMER:            updateStatus("Checking for Hammer"); break;
            default:                     updateStatus(formatEnum(next.name())); break;
        }
    }
}
