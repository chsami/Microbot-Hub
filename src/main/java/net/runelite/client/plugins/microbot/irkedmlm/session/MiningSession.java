package net.runelite.client.plugins.microbot.irkedmlm.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import java.awt.Rectangle;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.irkedmlm.IrkedMLMMapConstants;
import net.runelite.client.plugins.microbot.irkedmlm.SackState;
import net.runelite.client.plugins.microbot.irkedmlm.IrkedMLMConfig;
import net.runelite.client.plugins.microbot.irkedmlm.SessionPersonality;
import net.runelite.client.plugins.microbot.irkedmlm.enums.AfkParkSide;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MouseActivity;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import net.runelite.api.gameval.ObjectID;

/**
 * Behaviour engine for Motherlode Mine mining: vein selection, rockfall memory, path checks,
 * blacklists, scoring, floor transitions, and return-to-area via spot anchors only.
 * <p>
 * Uses {@link net.runelite.client.plugins.microbot.irkedmlm.enums.MLMMiningSpot} for
 * {@code contains()} and anchors — never for decisions beyond pure geometry.
 */
@Slf4j
public class MiningSession extends Session {

    public enum MiningSubState {
        IDLE,
        TRANSITIONING_FLOOR,
        WALKING,
        CLEARING_OBSTACLE,
        SELECTED,
        REPOSITIONING,
        CLICKED,
        ARRIVING,
        CONFIRMED,
        MINING,
        DEPLETED,
        INV_FULL,
        SACK_FULL,
        FAILED
    }

    /**
     * Active (non-depleted) ore vein object IDs: the single-wall vein plus the left/middle/right
     * shaped ones. Matched by ID rather than name because the object's internal name differs from the
     * menu target, and the numbers are hardcoded because the symbolic ObjectID.MOTHERLODE_ORE_*
     * gamevals can resolve differently across client versions.
     */
    private static final int[] ORE_VEIN_IDS = { 26661, 26662, 26663, 26664 };

    /** Object IDs for rockfalls (collapsed tunnels) in Motherlode Mine. */
    private static final int[] ROCKFALL_IDS = {26679, 26680};

    @Getter
    private MiningSubState subState = MiningSubState.IDLE;

    @Getter
    // The BFS runs on the client thread via a blocking invoke, so it must not run per tick. Not keyed
    // on the exact tile: reachability belongs to the connected region, so a step is no reason to redo
    // it. The TTL is what picks up a rockfall someone else mined.
    private static final int REACHABLE_SEARCH_RADIUS = 14;
    private static final int REACHABLE_RECOMPUTE_DISTANCE = 6;
    private static final long REACHABLE_CACHE_MS = 3000L;
    // volatile + immutable snapshot: the area overlay reads this from the render thread.
    private volatile Set<WorldPoint> reachableCache = null;
    private WorldPoint reachableCacheOrigin = null;
    private MLMMiningSpot reachableCacheSpot = null;
    private long reachableCacheMs = 0L;

    private WorldPoint targetVein;

    @Getter
    private int failedClicks = 0;

    private long lastXpTimestamp = 0L;

    /** Timestamp after which the current session/target is no longer "soft locked". */
    private long sessionLockUntil = 0L;
    /** Timestamp of the last issued walk command. */
    private long lastWalkCommandMs = 0L;
    private long lastMineClickMs = 0L;
    private boolean afkMouseOffscreen = false;
    /** Early per-vein mouse decision (AFK off-screen / rare early hover / leave) made once. */
    private boolean boutMouseDecided = false;
    /** Whether we've already pre-hovered the next vein this bout (prevents per-tick re-rolls). */
    private boolean hoverDoneThisBout = false;
    /** ms of continuous mining after which pre-hovering the next vein is natural (proxy for "nearly depleted"). */
    private long hoverEligibleAfterMs = 0L;
    /** Wall-clock throttle for the ambient inventory-glance so it never metronomes. */
    private long lastAmbientAttentionMs = 0L;
    /** Randomised gap until the next glance opportunity; re-rolled after each one. */
    private long nextAmbientAttentionGapMs = AMBIENT_ATTENTION_COOLDOWN_MS;
    private static final long AMBIENT_ATTENTION_COOLDOWN_MS = 25_000L;
    /** Spread on the above so glance opportunities aren't a 25s metronome. */
    private static final long AMBIENT_ATTENTION_JITTER_MS = 20_000L;
    /** Resolved AFK off-screen park edge for this login: -1 = left, +1 = right, 0 = not yet resolved.
     *  Off-screen AFK models the game on a second monitor, so the cursor only ever crosses one edge. */
    /** Concrete edge this login parks off; resolved once for RANDOM, null until then. */
    private AfkParkSide resolvedParkSide = null;

    /** Player must be this close (tiles) for the "just mine the rock next to me" nearest-vein rule.
     *  Set to 4 (was 2) so veins directly behind/beside the player in the stand area win over a
     *  higher-scored cluster around the corner — a human mines the rock next to them, not the far "better" one. */
    private static final int ADJACENT_DIST = 4;

    /** Session-long memory of rockfall tiles. Even if another player mines a rockfall,
     *  we continue to treat those tiles as blocked for the remainder of the session. */
    /**
     * Rebuilt each tick, not accumulated: it only ever added, so a rockfall someone mined stayed
     * "blocked" all session. An immutable snapshot because the overlay reads it from the render
     * thread - mutating it there threw ConcurrentModificationException and dropped the highlight.
     */
    private volatile Set<WorldPoint> rememberedRockfalls = Collections.emptySet();

    /** Veins that recently failed interaction (depleted, blocked, or click-failed).
     *  Key = vein WorldPoint, Value = timestamp of failure. */
    private final Map<WorldPoint, Long> recentFailures = new HashMap<>();

    /** How long to blacklist a vein after it fails interaction. */
    private static final long FAILURE_BLACKLIST_MS = 10_000L;
    private static final long FAILURE_BLACKLIST_UPPER_MS = 60_000L;

    /** Beyond this distance, lower-mine travel uses web-walk (canvas is unreliable across tunnels). */
    private static final int LOWER_WEB_WALK_MIN_DISTANCE = 14;
    private static final int UPPER_WEB_WALK_MIN_DISTANCE = 20;
    /** Max tiles to consider a vein for scoring (upper chamber + ladder landing). */
    private static final int UPPER_VEIN_MAX_SELECTION_DISTANCE = 12;
    /** Upper chambers: pool veins for scoring (selection prefers dist {@code <=2} click range). */
    private static final int UPPER_CHAMBER_VEIN_MAX_DISTANCE = 8;
    /** Max tile distance at which a Mine click on a vein is attempted directly instead of walking first. */
    private static final int VEIN_CLICK_MAX_DISTANCE = 8;
    private static final int LOWER_VEIN_MAX_SELECTION_DISTANCE = 14;

    /** A vein still not clicked after this long (it will not frame, or the walk gets nowhere) is given up on. */
    static final long CLICK_GIVE_UP_MS = 20_000L;
    /** Gems dropped per tick, so a load of them is not one burst that holds the executor. */
    static final int MAX_GEM_DROPS_PER_TICK = 4;

    /** Score penalties (lower score = more desirable). Blocked veins stay in the pool. */
    private static final int SCORE_FAILED_PENALTY     = 50;
    private static final int SCORE_OUT_OF_RANGE       = 500;
    /** Prefer veins that are part of a nearby cluster (around-the-corner groups). */
    private static final int SCORE_CLUSTER_BONUS_PER_NEIGHBOR = 3;
    private static final int SCORE_CLUSTER_BONUS_CAP = 9;

    /** No mining activity after click — mark vein failed (human "that one didn't work"). */
    private static final long CONFIRMED_FAIL_MS     = 8_000L;

    private final Rs2TileObjectCache   tileCache;

    public MiningSession(Rs2TileObjectCache tileCache, IrkedMLMConfig config) {
        super(config);
        this.tileCache = tileCache;
    }

    @Override
    public void reset() {
        super.reset();
        subState          = MiningSubState.IDLE;
        targetVein        = null;
        failedClicks      = 0;
        lastXpTimestamp   = 0L;
        sessionLockUntil  = 0L;
        lastWalkCommandMs = 0L;
        lastMineClickMs     = 0L;
        afkMouseOffscreen = false;
        boutMouseDecided = false;
        hoverDoneThisBout = false;
        hoverEligibleAfterMs = 0L;
        lastAmbientAttentionMs = 0L;
        rememberedRockfalls = Collections.emptySet();
        recentFailures.clear(); // full clear on reset (recovery/hard resolve) so stale per-vein blacklists don't prevent resolving
        // NOTE: afkExitSign is deliberately NOT reset here — reset() runs on mid-session recovery, and the
        // AFK park side must stay consistent for the whole login. It re-rolls per login in updatePersonality().
    }

    @Override
    public void updatePersonality(SessionPersonality p) {
        super.updatePersonality(p);
        resolvedParkSide = null; // new login → re-resolve the RANDOM park side
    }

    /** Bring the virtual mouse back after an AFK off-screen bout. Usually we do NOT pre-move: the very
     *  next interaction (the Mine click on the chosen vein, or a hopper/sack click) pulls the cursor
     *  straight onto the thing it needs to click — a human glancing back and going right for the rock.
     *  But not always: ~25% of the time we bring the mouse roughly back onto the canvas first, as if
     *  reaching for it before deciding where to click. */
    private void ensureMouseInGame() {
        if (!afkMouseOffscreen) {
            return;
        }
        afkMouseOffscreen = false;
        if (Rs2Random.between(0, 100) < 25) {
            // Re-enter from the SAME edge we parked on (a player reaching back from their side monitor),
            // a little way in. The other 75% of the time the next real click pulls the cursor in from that
            // edge naturally, which is already consistent because it's parked just off that side.
            AfkParkSide side = parkSide();
            int w = Microbot.getClient().getCanvasWidth();
            int h = Microbot.getClient().getCanvasHeight();
            int x;
            int y;
            if (side.isHorizontal()) {
                int margin = Math.max(3, w / 5);
                x = side.getDx() < 0
                        ? Rs2Random.between(2, margin)
                        : Rs2Random.between(Math.max(2, w - margin), Math.max(3, w - 1));
                y = Rs2Random.between(Math.max(1, h / 6), Math.max(2, h - h / 6));
            } else {
                int margin = Math.max(3, h / 5);
                y = side.getDy() < 0
                        ? Rs2Random.between(2, margin)
                        : Rs2Random.between(Math.max(2, h - margin), Math.max(3, h - 1));
                x = Rs2Random.between(Math.max(1, w / 6), Math.max(2, w - w / 6));
            }
            Microbot.naturalMouse.moveTo(x, y);
        }
    }

    /**
     * The canvas edge this login parks the AFK cursor off. A concrete side comes straight from config
     * (where the player's attention physically goes); RANDOM resolves once per login and is cached so
     * the side stays consistent for the whole session, re-rolled on the next login via
     * {@code updatePersonality}. NONE means the cursor never leaves the canvas at all.
     */
    private AfkParkSide parkSide() {
        AfkParkSide configured = config != null ? config.afkParkSide() : AfkParkSide.RANDOM;
        if (configured != AfkParkSide.RANDOM) {
            return configured;
        }
        if (resolvedParkSide == null) {
            resolvedParkSide = AfkParkSide.randomEdge();
            log.debug("[MiningSession] AFK park side resolved for this login: {}", resolvedParkSide);
        }
        return resolvedParkSide;
    }

    /**
     * Park the virtual mouse just off one canvas edge (AFK on a second monitor). Unlike the shared
     * {@code naturalMouse.moveOffScreen()} — which picks a random one of all four edges every time, an
     * un-human tell — this always exits the edge {@link #parkSide()} chose for this login, and the
     * position along that edge varies so it isn't the exact same pixel each bout.
     *
     * @return {@code true} if the cursor actually left the canvas; {@code false} for
     *         {@link AfkParkSide#NONE}, where the caller should just leave the cursor where it is.
     */
    private boolean parkOffScreenDirectional() {
        AfkParkSide side = parkSide();
        if (!side.parksOffScreen()) {
            return false; // NONE — the player never flings the cursor out of the client window.
        }
        int w = Microbot.getClient().getCanvasWidth();
        int h = Microbot.getClient().getCanvasHeight();
        // Vary BOTH how far past the bezel (depth) and the position along that edge, so successive AFK
        // bouts never park on the same off-screen pixel — a fixed target is trivially fingerprintable.
        // A real hand shoves the mouse a slightly different distance past the edge each time.
        int depth = Rs2Random.between(1, 45);
        int x;
        int y;
        if (side.isHorizontal()) {
            x = side.getDx() < 0 ? -depth : w + depth;
            y = Rs2Random.between(0, Math.max(1, h + 1));
        } else {
            x = Rs2Random.between(0, Math.max(1, w + 1));
            y = side.getDy() < 0 ? -depth : h + depth;
        }
        log.debug("[MiningSession] AFK park off-screen: side={} target=({},{}) canvas={}x{}",
                side, x, y, w, h);
        Microbot.naturalMouse.moveTo(x, y);
        return true;
    }

    /**
     * Human-like mouse behaviour while actively mining a vein. Off-screen AFK is the dominant
     * behaviour — most people watch an AFK skill on a second monitor or look away, cursor parked off
     * the game canvas. Behaviour is selected by the {@link MouseActivity} config (human-like only):
     * <ul>
     *   <li><b>AFK</b> (default): click, park off-screen, do nothing else — no hovering, no glancing.
     *       MLM is an AFK skill, so this is what a real player does: click, look away, click again.</li>
     *   <li><b>BALANCED</b>: parks at this login's {@link SessionPersonality#offScreenParkChance}, and the
     *       bouts left on-canvas behave normally — an occasional early pre-hover, and a late hover of the
     *       next vein as this one nears depletion ({@link #hoverEligibleAfterMs}).</li>
     *   <li><b>ACTIVE</b>: seldom parks, and is busy while watching — hover chances are scaled up.</li>
     * </ul>
     * With {@link AfkParkSide#NONE} the "park" outcome keeps the cursor inside the client instead,
     * resting wherever the click left it.
     * Fast mode never touches the mouse.
     */
    private void handleMiningMouseBehaviour(MLMMiningSpot spot) {
        if (!isHumanLikeEnabled()) {
            return;
        }
        // Only act once the mining animation/interaction is actually underway.
        if (!(Rs2Player.isInteracting() || Rs2Player.isAnimating(1200))) {
            return;
        }

        MouseActivity mode = config != null ? config.mouseActivity() : MouseActivity.AFK;

        // Decision 1 — early, once per bout: does the cursor leave the canvas, pre-hover, or sit still?
        // The park rate is the mode's (AFK always, BALANCED at this login's personality rate, ACTIVE
        // seldom); the hover rates are the personality's, scaled by how attentive the mode is.
        if (!boutMouseDecided) {
            boutMouseDecided = true;
            int roll = Rs2Random.between(0, 100);
            if (roll < mode.offScreenChance(personality)) {
                if (parkOffScreenDirectional()) {
                    afkMouseOffscreen = true;
                    hoverDoneThisBout = true; // parked off-screen; no hover this bout
                    return;
                }
                // AfkParkSide.NONE: the player keeps the cursor in the window. Treat it as "attention
                // elsewhere" anyway — the cursor simply rests where the click left it, and for pure AFK
                // there is nothing more to do this bout.
                if (mode.isPureAfk()) {
                    hoverDoneThisBout = true;
                    return;
                }
            } else if (roll < mode.offScreenChance(personality) + mode.earlyHoverChance(personality)) {
                if (hoverNextVein(spot)) {
                    hoverDoneThisBout = true;
                }
                return;
            }
            // else: leave the mouse where it is, on-screen.
        }

        // AFK never lines up the next vein — it either parked, or (with park side NONE) is sitting still.
        if (mode.isPureAfk()) {
            return;
        }

        // Decision 2 — late, once per bout: line up the next vein as this one nears depletion.
        if (!hoverDoneThisBout && !afkMouseOffscreen && subElapsed() >= hoverEligibleAfterMs) {
            hoverDoneThisBout = true; // one attempt; don't re-roll every tick
            if (personality.roll(mode.lateHoverChance(personality))) {
                hoverNextVein(spot); // if it fails (next vein off-screen/absent) we simply leave the mouse put
            }
        }
    }

    /**
     * Move the virtual mouse over the next candidate vein's clickbox (no click), as a human
     * lining up their next rock would. Returns true if the mouse was moved.
     */
    private boolean hoverNextVein(MLMMiningSpot spot) {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) {
            return false;
        }
        Rs2TileObjectModel next = null;
        for (RankedVein r : rankVeinsByScore(spot, player, queryVeinsInSpot(spot))) {
            WorldPoint vp = r.vein.getWorldLocation();
            if (targetVein != null && vp.equals(targetVein)) {
                continue; // skip the vein we're currently mining
            }
            next = r.vein;
            break;
        }
        if (next == null) {
            return false;
        }
        return hoverObject(next);
    }

    /**
     * Ambient, <em>correlated</em> attention behaviour while mining: an occasional inventory-glance — a
     * "how full am I?" look a present player makes between clicks. It fires only while the cursor is on the
     * canvas ({@code !afkMouseOffscreen}): a player who has parked the mouse off-screen (watching a second
     * monitor) is not glancing at their inventory, so an independent coin-flip there would be the un-human
     * tell this deliberately avoids. Throttled to one glance per {@link #AMBIENT_ATTENTION_COOLDOWN_MS}.
     * Fast mode never touches the mouse.
     *
     * <p>Deliberately glance-only: a real player sets zoom/pitch/yaw once and then leaves the camera
     * alone, so a periodic camera nudge would read as less human, not more.
     */
    private void handleAmbientAttention() {
        if (!isHumanLikeEnabled() || afkMouseOffscreen) {
            return; // fast mode, or AFK off-screen — no ambient attention (the correlation)
        }
        if (!(Rs2Player.isInteracting() || Rs2Player.isAnimating(1200))) {
            return; // only glance while actually mining
        }
        long now = System.currentTimeMillis();
        if (now - lastAmbientAttentionMs < nextAmbientAttentionGapMs) {
            return;
        }
        nextAmbientAttentionGapMs = AMBIENT_ATTENTION_COOLDOWN_MS
                + Rs2Random.between(0, (int) AMBIENT_ATTENTION_JITTER_MS);
        MouseActivity glanceMode = config != null ? config.mouseActivity() : MouseActivity.AFK;
        if (glanceMode.isPureAfk()) {
            return; // an AFK player is not looking at the screen, let alone their inventory
        }
        // Stamp the clock on every roll, win or lose. Stamping only on success made the cooldown gate
        // when rolling *starts* rather than how often it happens: the roll then ran every tick until
        // one landed, so a "16% chance" became a guaranteed glance a second after each cooldown
        // expired. Now a lost roll costs a full window, and the percentage means what it says.
        lastAmbientAttentionMs = now;
        if (personality.roll(glanceMode.inventoryGlanceChance(personality)) && glanceAtInventory()) {
            log.debug("[MiningSession] Ambient attention: inventory-glance");
        }
    }

    /**
     * Move the virtual mouse over a random interior point of the live inventory widget (no click) — a
     * "how full am I?" glance. Reads the inventory bounds on the client thread; no click, so it never
     * disturbs mining. Returns true if the mouse was moved.
     */
    private boolean glanceAtInventory() {
        Point pt = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget inv = Microbot.getClient().getWidget(InterfaceID.Inventory.ITEMS);
            if (inv == null || inv.isHidden()) {
                return null;
            }
            Rectangle b = inv.getBounds();
            if (b == null || b.width <= 0 || b.height <= 0) {
                return null;
            }
            return randomPointInShape(b, b);
        }).orElse(null);
        if (pt == null || pt.getX() < 0 || pt.getY() < 0) {
            return false;
        }
        Microbot.naturalMouse.moveTo(pt.getX(), pt.getY());
        return true;
    }

    /** abandon current vein target, mark it as failed, and return to SELECTED */
    private void abandonVein(MLMMiningSpot spot) {
        if (targetVein != null) {
            markFailed(targetVein);
        }
        targetVein = null;
        failedClicks = 0;
        transitionSub(MiningSubState.SELECTED);
    }

    @Override
    public void begin() {
        invalidateUpperFloorCache();
        if (!isIdle()) return;
        super.begin();
        transitionSub(MiningSubState.IDLE);
    }

    public void onMiningXp() {
        lastXpTimestamp = System.currentTimeMillis();
    }

    public boolean isActivelyMining() {
        return state == State.ACTIVE && (
                subState == MiningSubState.CLICKED
                        || subState == MiningSubState.CONFIRMED
                        || subState == MiningSubState.MINING);
    }

    public boolean isMiningComplete() {
        return subState == MiningSubState.INV_FULL
                || subState == MiningSubState.SACK_FULL;
    }

    /**
     * Returns true if the session ended because the sack was detected as full.
     * Used by the orchestrator to route directly to EMPTY_SACK without
     * re-querying the unreliable varbit.
     */
    public boolean endedWithSackFull() {
        return subState == MiningSubState.SACK_FULL;
    }

    /**
     * Returns true if the session ended because inventory was full of pay-dirt.
     */
    public boolean endedWithInvFull() {
        return subState == MiningSubState.INV_FULL;
    }

    /** Returns true if the session is currently in a "soft lock" window. */
    public boolean isLocked() {
        return System.currentTimeMillis() < sessionLockUntil;
    }

    /** Returns true if the session has committed to a target and is actively processing it. */
    public boolean isCommitted() {
        return subState == MiningSubState.CLICKED
                || subState == MiningSubState.CONFIRMED
                || subState == MiningSubState.MINING;
    }

    // -----------------------------------------------------------------------
    // Tick entry-point
    // -----------------------------------------------------------------------

    /**
     * Main tick method called by the orchestrator.
     */
    public void tick(MLMMiningSpot spot, SackState sackState, AtomicLong globalLastXpTime) {
        if (!isActive()) return;
        if (System.currentTimeMillis() < nextActionMs) return;
        if (spot == null) {
            log.warn("[MiningSession] tick() called with null spot - failing session");
            transitionSub(MiningSubState.FAILED);
            transition(State.FAILED);
            return;
        }

        try {
            tickMiningInternal(spot, sackState, globalLastXpTime, System.currentTimeMillis());
        } catch (Exception e) {
            if (isClientThreadInvokeInterrupted(e)) {
                log.debug("[MiningSession] Tick deferred - client thread busy/interrupted, retry next tick");
                scheduleNextAdaptive(180L, 600L);
                return;
            }
            log.error("[MiningSession] Tick crash", e);
            transitionSub(MiningSubState.FAILED);
            transition(State.FAILED);
        }
    }

    @Override
    protected void tickInternal() {
        // MiningSession uses its own tick() overload - no-op here.
    }

    // -----------------------------------------------------------------------
    // Core tick logic
    // -----------------------------------------------------------------------

    private void tickMiningInternal(MLMMiningSpot spot, SackState sackState,
                                    AtomicLong globalLastXpTime, long now) {

        updateRememberedRockfalls();
        cleanupRecentFailures();

        // Early exit if the orchestrator already knows the sack is full (or we were started while full).
        // Prevents walking to veins, selecting, and clicking "Mine" on upper/lower when we should be emptying.
        // The main script passes effective (current + any just-deposited projection).
        if (sackState.definitelyFull()) {
            log.debug("[MiningSession] Sack full ({}/{}) on tick entry - immediately completing as SACK_FULL",
                    sackState.countOrUnknown(), sackState.capacity());
            transitionSub(MiningSubState.SACK_FULL);
            transition(State.COMPLETE);
            return;
        }

        // localPlayerName is now pushed from IrkedMLMScript via updateCachedLocalPlayerName()
        // (no per-session client thread fetch here - reduces startup pressure + TimeoutExceptions)

        // ---------------------------------------------------------------------
        // Floor gate — must precede the switch, not live inside individual cases.
        //
        // MLM's two levels share x/y on plane 0 (the upper terrain is simply raised), so an upper
        // vein is perfectly resolvable by WorldPoint from downstairs: findVeinAt() returns it, the
        // click succeeds, and the player walks at those x/y on the LOWER level — into a wall. Only
        // IDLE and WALKING used to check the floor, so a session that survived a deposit trip in
        // SELECTED/CLICKED (see isCommitted()/isLocked() in IrkedMLMScript) resumed holding an
        // upstairs target and clicked it from the bottom of the ladder. EAST_UPPER showed it most
        // because its area overlaps the lower hub beside the ladder.
        // ---------------------------------------------------------------------
        if (subState != MiningSubState.TRANSITIONING_FLOOR) {
            Session.Floor floor = Session.currentFloor();
            if (floor == Session.Floor.UNKNOWN) {
                log.debug("[MiningSession] Floor unknown - holding before touching a vein");
                scheduleNextAdaptive(180L, 600L);
                return;
            }
            if (isWrongFloor(spot)) {
                targetVein = null; // chosen for the other level; must not survive the climb
                transitionSub(MiningSubState.TRANSITIONING_FLOOR);
                return;
            }
        }

        // Resolve the most recent XP timestamp from both local and global sources.
        long lastXpResolved = Math.max(lastXpTimestamp, globalLastXpTime.get());

        switch (subState) {

            // -----------------------------------------------------------------
            case IDLE:
                if (Rs2Inventory.isFull()) {
                    transitionSub(MiningSubState.INV_FULL);
                    break;
                }
                if (sackState.definitelyFull()) {
                    transitionSub(MiningSubState.SACK_FULL);
                    break;
                }
                // (Floor is gated for every sub-state before the switch — no check needed here.)
                transitionSub(miningEntrySubState(spot));
                break;

            // -----------------------------------------------------------------
            case TRANSITIONING_FLOOR:
                // While climbing (or recently started anim), don't re-attempt. Uses event-driven lastAnimationTime.
                // Combined with scheduleNext after click, covers gaps where raw isAnimating() might be false.
                // Guard is 1200ms (~2 ticks): a longer one holds the state machine here after the climb
                // has already finished, which makes ladder descents "hang" whatever the human toggle says.
                if (busyMoving(1200)) {
                    scheduleNextAdaptive(180L, 600L);
                    break;
                }
                invalidateUpperFloorCache();
                if (ensureFloor(spot.isUpstairs())) {
                    invalidateUpperFloorCache();
                    log.info("[MiningSession] Ladder transition done for {} (upper={})", spot, isUpperFloor());
                    transitionSub(miningEntrySubState(spot));
                }
                if (isStalled(20_000L)) {
                    log.warn("[MiningSession] Floor transition stalled - failing");
                    transitionSub(MiningSubState.FAILED);
                    transition(State.FAILED);
                }
                break;

            // -----------------------------------------------------------------
            case WALKING:
                // (Floor gated before the switch.)
                if (shouldSkipSpotWalk(spot)) {
                    transitionSub(MiningSubState.SELECTED);
                    break;
                }
                if (isNearSpot(spot)) {
                    transitionSub(MiningSubState.SELECTED);
                    break;
                }

                WorldPoint playerLoc = Rs2Player.getWorldLocation();
                RankedVein bestRanked = selectBestRankedVein(spot, playerLoc);
                Rs2TileObjectModel bestScored = bestRanked != null ? bestRanked.vein : null;
                int veinScore = -1;
                if (bestScored != null && playerLoc != null && !usesUpperStandMiningRules(spot)) {
                    veinScore = scoreVein(bestScored, spot, playerLoc);
                    WorldPoint vp = bestScored.getWorldLocation();
                    if (isReadyToClick(vp, playerLoc, spot)) {
                        log.info("[MiningSession] Score pick adjacent at {} (score={}) - skip walk",
                                vp, veinScore);
                        targetVein = vp;
                        transitionSub(MiningSubState.CLICKED);
                        scheduleNextAdaptive(350L, 700L);
                        break;
                    }
                }

                long walkCmdInterval = tickDelayMs(400L, 1200L);
                if (!Rs2Player.isMoving() && (now - lastWalkCommandMs >= walkCmdInterval)) {
                    WorldPoint walkTarget;
                    if (bestScored != null && !usesUpperStandMiningRules(spot)) {
                        walkTarget = walkTargetForVein(bestScored.getWorldLocation());
                        log.debug("[MiningSession] Walking toward score pick {} (score={})",
                                bestScored.getWorldLocation(), veinScore);
                    } else {
                        walkTarget = getFirstAnchor(spot);
                    }

                    if (walkTarget != null) {
                        walkTowardMiningTarget(walkTarget, spot, playerLoc);
                    } else {
                        log.warn("[MiningSession] Spot {} has no walk target and no visible veins", spot);
                        transitionSub(MiningSubState.FAILED);
                        transition(State.FAILED);
                        break;
                    }
                    lastWalkCommandMs = now;
                    scheduleNextAdaptive(180L, 600L);
                }
                if (isStalled(15_000L)) {
                    if (isNearSpot(spot)) {
                        log.info("[MiningSession] Walk timer elapsed but player is at spot {} - selecting vein",
                                spot);
                        transitionSub(MiningSubState.SELECTED);
                    } else {
                        log.warn("[MiningSession] Walking stalled - failing");
                        transitionSub(MiningSubState.FAILED);
                        transition(State.FAILED);
                    }
                }
                break;

            // -----------------------------------------------------------------
            case CLEARING_OBSTACLE: {
                Rs2TileObjectModel rockfall = findNearbyRockfall();
                if (rockfall == null) {
                    log.info("[MiningSession] Obstacle cleared - resuming mining");
                    transitionSub(miningEntrySubState(spot));
                    break;
                }
                if (Rs2Player.isAnimating()) {
                    scheduleNextAdaptive(180L, 600L);
                    break;
                }
                if (clickOnMyFloor(rockfall, "Mine")) {
                    applyActionCooldown();
                    scheduleNextAdaptive(350L, 1200L);
                }
                break;
            }

            // -----------------------------------------------------------------
            case SELECTED:
                if (Rs2Inventory.isFull()) {
                    transitionSub(MiningSubState.INV_FULL);
                    break;
                }
                WorldPoint selectPlayer = Rs2Player.getWorldLocation();
                targetVein = selectVein(spot);
                if (targetVein != null) {
                    log.info("[MiningSession] Vein selected at {}", targetVein);
                    transitionSub(MiningSubState.CLICKED);
                    scheduleNextAdaptive(350L, 700L);
                } else if (usesUpperStandMiningRules(spot) && selectPlayer != null
                        && nudgeTowardReachableVein(spot, selectPlayer)) {
                    scheduleNextAdaptive(180L, 600L);
                } else {
                    log.info("[MiningSession] No veins found in area");
                    WorldPoint here = Rs2Player.getWorldLocation();
                    if (usesUpperStandMiningRules(spot) && here != null && spot.contains(here)) {
                        scheduleNextAdaptive(350L, 700L);
                    } else {
                        transitionSub(MiningSubState.REPOSITIONING);
                    }
                }
                if (isStalled(10_000L)) {
                    WorldPoint checkHere = Rs2Player.getWorldLocation();
                    boolean waitingInSpot = usesUpperStandMiningRules(spot) && checkHere != null && spot.contains(checkHere);
                    if (waitingInSpot) {
                        log.debug("[MiningSession] Waiting for veins to spawn in upper area - clearing blacklists and resetting stall");
                        recentFailures.clear();
                        subStateEnteredMs = System.currentTimeMillis();
                    } else {
                        log.warn("[MiningSession] Vein selection stalled - failing");
                        transitionSub(MiningSubState.FAILED);
                        transition(State.FAILED);
                    }
                }
                break;

            // -----------------------------------------------------------------
            case REPOSITIONING:
                log.debug("[MiningSession] No veins found - repositioning");
                if (usesUpperStandMiningRules(spot)) {
                    WorldPoint repoPlayer = Rs2Player.getWorldLocation();
                    if (repoPlayer != null && !spot.contains(repoPlayer)) {
                        WorldPoint chamberAnchor = getFirstAnchor(spot);
                        if (chamberAnchor != null && repoPlayer.distanceTo(chamberAnchor) > 3) {
                            log.info("[MiningSession] Upper reposition: walking toward {} section ({})",
                                    spot, chamberAnchor);
                            walkTowardMiningTarget(chamberAnchor, spot, repoPlayer);
                            scheduleNextAdaptive(180L, 600L);
                            transitionSub(MiningSubState.WALKING);
                            break;
                        }
                    }
                    transitionSub(MiningSubState.SELECTED);
                    scheduleNextAdaptive(350L, 700L);
                    break;
                }
                if (shouldSkipSpotWalk(spot)) {
                    WorldPoint repoPlayer = Rs2Player.getWorldLocation();
                    RankedVein repoRanked = selectBestRankedVein(spot, repoPlayer);
                    Rs2TileObjectModel repoVein = repoRanked != null ? repoRanked.vein : null;
                    if (repoVein != null && repoPlayer != null
                            && repoPlayer.distanceTo(repoVein.getWorldLocation()) > 2) {
                        WorldPoint stand = walkTargetForVein(repoVein.getWorldLocation());
                        if (stand != null) {
                            log.info("[MiningSession] Upper reposition: walking toward scored vein at {} (score={})",
                                    stand, scoreVein(repoVein, spot, repoPlayer));
                            Rs2Walker.walkFastCanvas(stand);
                            scheduleNextAdaptive(180L, 600L);
                        }
                    } else if (repoVein == null) {
                        WorldPoint chamberAnchor = getFirstAnchor(spot);
                        if (chamberAnchor != null && repoPlayer != null
                                && repoPlayer.distanceTo(chamberAnchor) > 3) {
                            log.info("[MiningSession] Upper reposition: walking toward chamber anchor {}", chamberAnchor);
                            Rs2Walker.walkFastCanvas(chamberAnchor);
                            scheduleNextAdaptive(180L, 600L);
                        }
                    }
                    transitionSub(MiningSubState.SELECTED);
                    break;
                }
                WorldPoint repositionTarget = getFirstAnchor(spot);
                if (repositionTarget != null) {
                    walkTowardMiningTarget(repositionTarget, spot, Rs2Player.getWorldLocation());
                    scheduleNextAdaptive(500L, 2000L);
                    transitionSub(MiningSubState.WALKING);
                } else {
                    log.warn("[MiningSession] Spot {} has no reposition target", spot);
                    transitionSub(MiningSubState.FAILED);
                    transition(State.FAILED);
                }
                break;

            // -----------------------------------------------------------------
            case CLICKED: {
                if (isStalled(CLICK_GIVE_UP_MS)) {
                    log.info("[MiningSession] Could not click the vein at {} in {}s - picking another",
                            targetVein, CLICK_GIVE_UP_MS / 1000);
                    abandonVein(spot);
                    break;
                }
                long minClickGap = tickDelayMs(900L, 1400L);
                if (now - lastMineClickMs < minClickGap) {
                    scheduleNext(minClickGap - (now - lastMineClickMs));
                    break;
                }
                // Wait for prior interact/anim to finish before another Mine click
                if (Rs2Player.isInteracting() || Rs2Player.isAnimating(1200)) {
                    scheduleNextAdaptive(350L, 700L);
                    break;
                }

                Rs2TileObjectModel vein = findVeinAt(targetVein);
                if (vein == null) {
                    log.debug("[MiningSession] Vein at {} lost before click - clearing and reselecting", targetVein);
                    abandonVein(spot);
                    break;
                }

                WorldPoint playerLocClick = Rs2Player.getWorldLocation();
                if (!isVeinPresent(targetVein)) {
                    log.debug("[MiningSession] Vein at {} depleted before click - reselecting", targetVein);
                    abandonVein(spot);
                    break;
                }
                if (isVeinBarred(spot, playerLocClick, targetVein)) {
                    log.debug("[MiningSession] Vein at {} barred (rockfall tile/path) - reselecting", targetVein);
                    abandonVein(spot);
                    break;
                }
                int distToVein = playerLocClick != null ? playerLocClick.distanceTo(targetVein) : Integer.MAX_VALUE;
                if (distToVein > VEIN_CLICK_MAX_DISTANCE) {
                    if (usesUpperStandMiningRules(spot)) {
                        log.debug("[MiningSession] Upper chamber vein at {} too far to click (dist={}) — reselecting",
                                targetVein, distToVein);
                        abandonVein(spot);
                        break;
                    }
                    // Clicked from here only if it is already in view; otherwise walk, the camera can't
                    // be made to frame every distant vein.
                    if (!preferWalkBeforeMineClick(spot, distToVein) && hasSafeClickbox(vein)) {
                        if (attemptMineClick(vein, now, "direct")) {
                            break;
                        }
                        log.debug("[MiningSession] Direct Mine failed at dist {} - will walk or retry", distToVein);
                    }
                    WorldPoint stand = walkTargetForVein(targetVein);
                    if (stand != null && !Rs2Player.isMoving()) {
                        log.debug("[MiningSession] Walking to stand tile {} (dist={})", stand, distToVein);
                        Rs2Walker.walkFastCanvas(stand);
                    }
                    scheduleNextAdaptive(180L, 600L);
                    break;
                }

                if (!ensureClickable(vein)) {
                    scheduleNextAdaptive(120L, 400L);
                    break;
                }

                if (isHumanLikeEnabled() && personality.roll(personality.misclickChance())) {
                    log.debug("[MiningSession] Human-like: misclicked tile next to vein, re-aiming");
                    scheduleNextAdaptive(500L, 1200L);
                    break;
                }
                if (attemptMineClick(vein, now, "adjacent")) {
                    break;
                } else {
                    failedClicks++;
                    int maxClickFails = spot.isUpstairs() ? 1 : 3;
                    log.debug("[MiningSession] Click failed ({}/{})", failedClicks, maxClickFails);
                    if (failedClicks > maxClickFails) {
                        log.warn("[MiningSession] Too many failed clicks on vein at {} - picking a new one", targetVein);
                        abandonVein(spot);
                    } else {
                        scheduleNextAdaptive(250L, 800L);
                    }
                }
                break;
            }

            // -----------------------------------------------------------------
            case ARRIVING: {
                // Wait for player to stop moving, then check if mining started
                if (Rs2Player.isMoving()) {
                    scheduleNextAdaptive(180L, 600L);
                    break;
                }
                // Player stopped moving - check animation
                if (Rs2Player.isAnimating()) {
                    transitionSub(MiningSubState.MINING);
                    break;
                }
                // A rockfall can spawn while we're walking. If one now blocks the path, bail out
                // immediately instead of timing out in CONFIRMED.
                if (targetVein != null && isVeinBarred(spot, Rs2Player.getWorldLocation(), targetVein)) {
                    log.debug("[MiningSession] Barred path after arriving - reselecting");
                    markFailed(targetVein);
                    transitionSub(MiningSubState.SELECTED);
                    break;
                }
                // Not animating yet - give it a moment before declaring CONFIRMED
                if (subElapsed() > tickDelayMs(700L, 2000L)) {
                    log.debug("[MiningSession] Arrived but no animation yet - moving to CONFIRMED");
                    transitionSub(MiningSubState.CONFIRMED);
                    scheduleNextAdaptive(180L, 600L);
                }
                break;
            }

            // -----------------------------------------------------------------
            case CONFIRMED:
                if (Rs2Player.isMoving()) {
                    scheduleNextAdaptive(300L, 600L);
                    break;
                }
                if (Rs2Player.isInteracting() || Rs2Player.isAnimating(1200)) {
                    transitionSub(MiningSubState.MINING);
                    scheduleNextAdaptive(400L, 800L);
                    break;
                }
                if (targetVein != null && !isVeinPresent(targetVein)) {
                    log.debug("[MiningSession] Target vein depleted during CONFIRMED - next vein");
                    transitionSub(MiningSubState.DEPLETED);
                    break;
                }
                long xpAfterClick = Math.max(lastXpResolved, globalLastXpTime.get());
                if (xpAfterClick > lastMineClickMs) {
                    transitionSub(MiningSubState.MINING);
                    scheduleNextAdaptive(400L, 800L);
                    break;
                }
                if (targetVein != null && subElapsed() > 3_000L) {
                    if (isVeinBarred(spot, Rs2Player.getWorldLocation(), targetVein)) {
                        log.debug("[MiningSession] Path barred during CONFIRMED - reselecting");
                        markFailed(targetVein);
                        transitionSub(MiningSubState.DEPLETED);
                        break;
                    }
                }
                if (subElapsed() > CONFIRMED_FAIL_MS) {
                    log.debug("[MiningSession] No mining started within {}ms - marking vein failed and reselecting",
                            CONFIRMED_FAIL_MS);
                    markFailed(targetVein);
                    transitionSub(MiningSubState.DEPLETED);
                    break;
                }
                scheduleNextAdaptive(400L, 800L);
                break;

            // -----------------------------------------------------------------
            case MINING: {
                if (Rs2Inventory.isFull()) {
                    ensureMouseInGame();
                    transitionSub(MiningSubState.INV_FULL);
                    break;
                }
                if (sackState.definitelyFull()) {
                    ensureMouseInGame();
                    transitionSub(MiningSubState.SACK_FULL);
                    break;
                }

                // A gem has turned up mid-bout. Bin it and get straight back on the SAME vein.
                //
                // The re-click is the whole point: dropping breaks the mining animation, and without
                // it this state simply waits — the vein is still there and the stuck test needs ten
                // seconds of silence — so the bout would stall, then markFailed() would blacklist a
                // perfectly good vein and move to another one. CLICKED re-clicks targetVein and
                // already falls back to reselecting if the vein despawned while we were dropping.
                if (shouldDropGems() && dropGemsFromInventory()) {
                    lastMineClickMs = 0L; // clear the click-gap throttle so the resume is not delayed
                    transitionSub(MiningSubState.CLICKED);
                    scheduleNextAdaptive(180L, 420L);
                    break;
                }

                // Human-like: once per mining bout, pick a mouse behaviour (AFK off-screen /
                // pre-hover the next vein / leave it put) instead of always going off-screen.
                handleMiningMouseBehaviour(spot);

                // Human-like: occasional inventory-glance while mining. Called after the mouse decision
                // so a fresh off-screen park this tick suppresses it.
                handleAmbientAttention();

                // If player is moving, they haven't started mining yet - don't count
                // this time toward stuck detection. Transition back to ARRIVING.
                if (Rs2Player.isMoving()) {
                    log.debug("[MiningSession] Player moving during MINING - returning to ARRIVING");
                    transitionSub(MiningSubState.ARRIVING);
                    break;
                }

                // Check if player is too far from the vein (>2 tiles)
                if (targetVein != null && Rs2Player.getWorldLocation().distanceTo(targetVein) > 2) {
                    log.debug("[MiningSession] Player too far from vein - reselecting");
                    transitionSub(shouldSkipSpotWalk(spot) ? MiningSubState.SELECTED : MiningSubState.WALKING);
                    break;
                }

                if (Rs2Player.isInteracting() || Rs2Player.isAnimating(1200)) {
                    scheduleNextAdaptive(400L, 800L);
                    break;
                }

                boolean veinPresent = isVeinPresent(targetVein);
                long lastActivityResolved = Math.max(
                        Math.max(lastXpResolved, globalLastXpTime.get()),
                        subStateEnteredMs);

                // Pay-dirt XP can arrive slightly after the mining anim ends; don't re-click yet.
                long xpGraceMs = tickDelayMs(1200L, 2000L);
                if (lastActivityResolved <= lastMineClickMs && subElapsed() < xpGraceMs) {
                    scheduleNextAdaptive(400L, 800L);
                    break;
                }

                boolean isStuck = (now - lastActivityResolved > 10_000L)
                        && (subElapsed() > 3_000L);

                if (!veinPresent || isStuck) {
                    if (isStuck && veinPresent) {
                        log.debug("[MiningSession] Stuck on vein (no activity 10s) - reselecting");
                        markFailed(targetVein);
                    } else {
                        log.debug("[MiningSession] Vein depleted or inactive at {} - next vein", targetVein);
                    }
                    transitionSub(MiningSubState.DEPLETED);
                    scheduleNextAdaptive(450L, 900L);
                    break;
                }

                scheduleNextAdaptive(500L, 1000L);
                break;
            }

            // -----------------------------------------------------------------
            case DEPLETED:
                if (targetVein != null) {
                    markFailed(targetVein);
                }
                targetVein = null;
                ensureMouseInGame();
                scheduleNextAdaptive(500L, 1000L);
                transitionSub(MiningSubState.SELECTED);
                break;

            // -----------------------------------------------------------------
            case INV_FULL:
            case SACK_FULL:
                ensureMouseInGame();
                transition(State.COMPLETE);
                break;

            default:
                break;
        }
    }

    // -----------------------------------------------------------------------
    // Sub-state transition
    // -----------------------------------------------------------------------

    private void transitionSub(MiningSubState next) {
        if (subState == next && next != MiningSubState.IDLE) return;
        log.debug("[MiningSession] Sub-state: {} → {}", subState, next);
        subState          = next;
        subStateEnteredMs = System.currentTimeMillis();
        scheduleSubStateEntryDelay();

        switch (next) {
            case TRANSITIONING_FLOOR: updateStatus("Climbing Ladder");          break;
            case WALKING:             updateStatus("Walking to Veins");          break;
            case CLEARING_OBSTACLE:   updateStatus("Clearing Rockfall");         break;
            case SELECTED:            updateStatus("Selecting Vein");            break;
            case REPOSITIONING:       updateStatus("Repositioning");             break;
            case CLICKED:             updateStatus("Clicking Vein");             break;
            case ARRIVING:            updateStatus("Walking to Vein");           break;
            case CONFIRMED:           updateStatus("Waiting for Mining Start");  break;
            case MINING:              boutMouseDecided = false;
                                      hoverDoneThisBout = false;
                                      // The click that got us here pulled the cursor back on-screen, so a new
                                      // bout starts on-screen. Clearing this lets AFK mode re-park each bout
                                      // (and keeps the late-hover guard honest for BALANCED/ACTIVE).
                                      afkMouseOffscreen = false;
                                      // MLM veins are mined continuously for tens of seconds, so "nearly
                                      // depleted" is late in the bout — not 5s in. Often the vein collapses
                                      // before this elapses, which is fine: hovering the next vein is optional.
                                      hoverEligibleAfterMs = Rs2Random.between(22_000, 45_000);
                                      updateStatus("Mining Vein");               break;
            case DEPLETED:            updateStatus("Vein Depleted");             break;
            case INV_FULL:            updateStatus("Inventory Full");            break;
            case SACK_FULL:           updateStatus("Sack Full");                 break;
            default:                  updateStatus(formatEnum(next.name()));     break;
        }
    }

    // -----------------------------------------------------------------------
    // Failure blacklist helpers
    // -----------------------------------------------------------------------

    private void cleanupRecentFailures() {
        long now = System.currentTimeMillis();
        recentFailures.entrySet().removeIf(e -> now - e.getValue() > FAILURE_BLACKLIST_UPPER_MS);
    }

    private long failureBlacklistMs(MLMMiningSpot spot) {
        return spot != null && spot.isUpstairs() ? FAILURE_BLACKLIST_UPPER_MS : FAILURE_BLACKLIST_MS;
    }

    private boolean isRecentlyFailed(WorldPoint wp, MLMMiningSpot spot) {
        if (wp == null) return false;
        Long ts = recentFailures.get(wp);
        if (ts == null) return false;
        if (System.currentTimeMillis() - ts > failureBlacklistMs(spot)) {
            recentFailures.remove(wp);
            return false;
        }
        return true;
    }

    /** The game said the last thing we clicked can't be reached: skip that vein for a while. */
    public void onTargetUnreachable() {
        if (targetVein == null) return;
        log.info("[MiningSession] Vein at {} can't be reached — skipping it for now", targetVein);
        if (isIdle()) {
            markFailed(targetVein);
        } else {
            abandonVein(null); // drop the target and reselect, or the walk/click carries on at it
        }
    }

    private void markFailed(WorldPoint wp) {
        if (wp != null) {
            recentFailures.put(wp, System.currentTimeMillis());
        }
    }

    private static boolean isClientThreadInvokeInterrupted(Throwable t) {
        while (t != null) {
            if (t instanceof InterruptedException) {
                return true;
            }
            if (t instanceof RuntimeException && t.getMessage() != null
                    && t.getMessage().contains("Interrupted waiting for client thread")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private boolean isReadyToClick(WorldPoint veinLoc, WorldPoint playerLoc, MLMMiningSpot spot) {
        if (veinLoc == null || playerLoc == null || spot == null) return false;
        if (!isVeinInSelectableZone(spot, veinLoc)) {
            return false;
        }
        if (usesUpperStandMiningRules(spot)) {
            if (playerLoc.distanceTo(veinLoc) > VEIN_CLICK_MAX_DISTANCE
                    || isVeinBarred(spot, playerLoc, veinLoc)) {
                return false;
            }
            // If player is adjacent (within click range), allow a direct click even if the vein
            // isn't reachable from the preferred stand tiles. This lets a player standing next
            // to a vein simply mine it instead of forcing a walk to a stand tile.
            if (playerLoc.distanceTo(veinLoc) <= VEIN_CLICK_MAX_DISTANCE) {
                return true;
            }
            return isReachableFromPreferredStand(spot, veinLoc);
        }
        if (playerLoc.distanceTo(veinLoc) > VEIN_CLICK_MAX_DISTANCE) {
            return false;
        }
        return !isVeinBarred(spot, playerLoc, veinLoc);
    }

    /** Upper chambers: vein must be clickable from at least one preferred stand (dist + no rockfall path). */
    private boolean isReachableFromPreferredStand(MLMMiningSpot spot, WorldPoint veinLoc) {
        for (WorldPoint stand : spot.getPreferredOperatingTiles()) {
            if (stand != null
                    && stand.distanceTo(veinLoc) <= VEIN_CLICK_MAX_DISTANCE
                    && !isVeinBarred(spot, stand, veinLoc)) {
                return true;
            }
        }
        return false;
    }

    /**
     * When human-like: ~35% walk to stand first; otherwise try Mine on the vein (client paths if needed).
     */
    private boolean preferWalkBeforeMineClick(MLMMiningSpot spot, int distToVein) {
        if (spot != null && usesUpperStandMiningRules(spot)) {
            return false;
        }
        if (distToVein <= 2) {
            return false;
        }
        int maxDirectDist = spot != null && spot.isUpstairs()
                ? UPPER_VEIN_MAX_SELECTION_DISTANCE
                : LOWER_VEIN_MAX_SELECTION_DISTANCE;
        if (distToVein > maxDirectDist) {
            return true;
        }
        if (!isHumanLikeEnabled()) {
            return false;
        }
        return Rs2Random.between(0, 100) < 35;
    }

    /**
     * Single Mine click path — updates timestamps and sub-state. Returns true if click was sent.
     */
    private boolean attemptMineClick(Rs2TileObjectModel vein, long now, String mode) {
        if (vein == null || !clickOnMyFloor(vein, "Mine")) {
            return false;
        }
        lastMineClickMs  = now;
        applyActionCooldown();
        failedClicks     = 0;
        lastXpTimestamp  = now;
        sessionLockUntil = now + 5000L;
        log.debug("[MiningSession] Mine click sent ({})", mode);
        if (Rs2Player.isMoving()) {
            transitionSub(MiningSubState.ARRIVING);
            scheduleNextAdaptive(250L, 800L);
        } else {
            transitionSub(MiningSubState.CONFIRMED);
            scheduleNextAdaptive(350L, 1200L);
        }
        return true;
    }

    private WorldPoint walkTargetForVein(WorldPoint veinLoc) {
        if (veinLoc == null) return null;
        WorldPoint stand = Rs2Tile.getNearestWalkableTile(veinLoc);
        return stand != null ? stand : veinLoc;
    }

    // -----------------------------------------------------------------------
    // Depleted vein helper
    // -----------------------------------------------------------------------

    /**
     * Active ore veins only - uses ObjectIDs (never calls {@code getName()} on the executor thread;
     * that blocks the client thread and can throw "Interrupted waiting for client thread").
     * Depleted variants use separate MOTHERLODE_DEPLETED_* IDs and are excluded via {@link #isOreVein}.
     */
    private boolean isActiveVein(Rs2TileObjectModel o) {
        return o != null && isOreVein(o.getId());
    }

    /**
     * Accumulates rockfall footprint tiles for the session. Tiles are never removed when rockfalls
     * despawn (another player mining them), avoiding re-selection of veins behind a respawning wall.
     */
    private void updateRememberedRockfalls() {
        List<Rs2TileObjectModel> current = tileCache.query()
                .where(o -> isRockfall(o.getId()))
                .toList();

        Set<WorldPoint> rebuilt = new HashSet<>();
        for (Rs2TileObjectModel rock : current) {
            WorldPoint base = rock.getWorldLocation();
            if (base == null) continue;
            for (int dx = 0; dx < 2; dx++) {
                for (int dy = 0; dy < 2; dy++) {
                    rebuilt.add(new WorldPoint(base.getX() + dx, base.getY() + dy, base.getPlane()));
                }
            }
        }
        if (!rebuilt.equals(rememberedRockfalls)) {
            reachableCacheMs = 0L; // a rockfall fell or was mined: what can be walked to has changed
        }
        rememberedRockfalls = Collections.unmodifiableSet(rebuilt);
    }

    // -----------------------------------------------------------------------
    // Vein selection
    // -----------------------------------------------------------------------

    /**
     * Returns true if {@code id} is one of the active (non-depleted) ore-vein object IDs.
     */
    private boolean isOreVein(int id) {
        for (int veinId : ORE_VEIN_IDS) {
            if (id == veinId) return true;
        }
        return false;
    }

    /** Returns true if {@code id} is one of the rockfall IDs. */
    private boolean isRockfall(int id) {
        for (int rockId : ROCKFALL_IDS) {
            if (id == rockId) return true;
        }
        return false;
    }

    /**
     * Helper to safely get the first anchor point from a spot.
     */
    private WorldPoint getFirstAnchor(MLMMiningSpot spot) {
        if (spot == null) return null;
        List<WorldPoint> points = spot.getWorldPoint();
        if (points == null || points.isEmpty()) return null;
        return points.get(0);
    }

    /**
     * Skip walking to anchors when already inside the spot {@link MLMMiningSpot#contains(WorldPoint)}.
     */
    private boolean shouldSkipSpotWalk(MLMMiningSpot spot) {
        if (spot == null || !spot.isUpstairs() || !isUpperFloor()) {
            return false;
        }
        WorldPoint here = Rs2Player.getWorldLocation();
        return here != null && spot.contains(here);
    }

    /**
     * The ladder lands a tile outside the chamber (top y5675, EAST_UPPER y5669-5674), so requiring
     * spot.contains(here) sent every trip through WALKING first. Clicking a vein already walks us
     * there. Safe only because isVeinBarred() now rules out veins behind a rockfall.
     */
    private MiningSubState miningEntrySubState(MLMMiningSpot spot) {
        if (spot != null && spot.isUpstairs() && isUpperFloor()) {
            WorldPoint here = Rs2Player.getWorldLocation();
            if (here == null) {
                return MiningSubState.WALKING;
            }
            if (spot.contains(here) || !queryVeinsInSpot(spot).isEmpty()) {
                return MiningSubState.SELECTED;
            }
            return MiningSubState.WALKING;
        }
        return MiningSubState.WALKING;
    }

    /** Session policy: upper spots with preferred stand hints use stand-and-click mining. */
    private boolean usesUpperStandMiningRules(MLMMiningSpot spot) {
        return spot != null && spot.isUpstairs() && !spot.getPreferredOperatingTiles().isEmpty();
    }

    /** Vein plus precomputed effective score (lower = more desirable). */
    private static final class RankedVein {
        private final Rs2TileObjectModel vein;
        private final int effectiveScore;

        private RankedVein(Rs2TileObjectModel vein, int effectiveScore) {
            this.vein = vein;
            this.effectiveScore = effectiveScore;
        }
    }

    /**
     * Lower base score = more desirable. Barred/out-of-range veins return {@link #SCORE_OUT_OF_RANGE};
     * recently-failed veins stay in the pool with {@link #SCORE_FAILED_PENALTY}.
     */
    private int scoreVein(Rs2TileObjectModel vein, MLMMiningSpot spot, WorldPoint playerLoc) {
        if (vein == null || spot == null || playerLoc == null) {
            return SCORE_OUT_OF_RANGE;
        }
        WorldPoint vp = vein.getWorldLocation();
        if (!isVeinInSelectableZone(spot, vp) || !isActiveVein(vein)) {
            return SCORE_OUT_OF_RANGE;
        }
        int maxDist = usesUpperStandMiningRules(spot)
                ? UPPER_CHAMBER_VEIN_MAX_DISTANCE
                : (spot.isUpstairs() ? UPPER_VEIN_MAX_SELECTION_DISTANCE : LOWER_VEIN_MAX_SELECTION_DISTANCE);
        int dist;
        if (usesUpperStandMiningRules(spot)) {
            // For upper chambers prefer veins that are close to preferred operating tiles (stand positions)
            // rather than raw player distance. This biases selection toward veins that are easier to operate
            // from the configured stand hints and avoids picking a farther vein that happens to be nearer
            // to the player but less convenient from operating positions.
            Set<WorldPoint> stands = spot.getPreferredOperatingTiles();
            int minStandDist = Integer.MAX_VALUE;
            if (stands != null && !stands.isEmpty()) {
                for (WorldPoint s : stands) {
                    if (s == null) continue;
                    minStandDist = Math.min(minStandDist, s.distanceTo(vp));
                }
            }
            if (minStandDist == Integer.MAX_VALUE) {
                dist = playerLoc.distanceTo(vp);
            } else {
                dist = minStandDist;
            }

            // Player-local bias: when the player is already standing next to (or very close to) a valid
            // vein, strongly prefer mining that one rather than "optimising" for a perfect stand tile
            // around the corner. Without it, an adjacent vein loses to one with a better stand-distance
            // but a worse distance from where the player actually is.
            int pDist = playerLoc.distanceTo(vp);
            if (pDist <= VEIN_CLICK_MAX_DISTANCE && !isVeinBarred(spot, playerLoc, vp)) {
                if (pDist <= 2) {
                    // Anything the player can click *right now* (adjacent or 1-2 tiles) gets a massive
                    // priority boost (negative score) so it wins the ranking over any pure stand-0 that
                    // is farther from the current position.
                    dist = pDist - 20;
                } else {
                    // For still-direct but slightly farther, at least use the better of stand or player dist.
                    dist = Math.min(dist, pDist);
                }
            }
        } else {
            dist = playerLoc.distanceTo(vp);
        }
        if (dist > maxDist) {
            return SCORE_OUT_OF_RANGE;
        }
        if (!spot.isUpstairs()) {
            WorldPoint anchor = getFirstAnchor(spot);
            if (anchor != null && vp.distanceTo(anchor) > 12) {
                return SCORE_OUT_OF_RANGE;
            }
        }
        if (isVeinBarred(spot, playerLoc, vp)) {
            return SCORE_OUT_OF_RANGE;
        }
        int score = dist;
        if (isRecentlyFailed(vp, spot)) {
            score += SCORE_FAILED_PENALTY;
        }
        return score;
    }

    /**
     * True when we cannot walk to this vein. Asks the client's live scene collision (not the shipped
     * web-walk map, which has a known MLM gap) instead of the old hand-written survey, whose
     * EAST_UPPER tiles never even overlapped the spot area. Rockfalls are solid objects, so they are
     * in those flags automatically and leave them when mined.
     */
    private boolean isVeinBarred(MLMMiningSpot spot, WorldPoint playerLoc, WorldPoint veinLoc) {
        if (spot == null || playerLoc == null || veinLoc == null) {
            return true;
        }
        // Hard veto, checked FIRST and never overridden by reachability. These tiles are off-limits
        // whether or not a rockfall currently seals them: rockfalls respawn, so walking in while one
        // is open leaves us boxed in when it comes back. "Currently reachable" is not the same thing
        // as "safe to enter", and this rule is about the second.
        if (isPermanentRockfallBarrier(spot, veinLoc)) {
            return true;
        }
        Set<WorldPoint> reachable = reachableTilesFrom(spot, playerLoc);
        if (reachable.isEmpty()) {
            // BFS unavailable (scene not loaded, client read failed). Fall back to the session's own
            // observed rockfalls rather than blocking everything, which would stall mining outright.
            return pathCrossesRockfallMemory(playerLoc, veinLoc);
        }
        for (WorldPoint beside : adjacentTiles(veinLoc)) {
            if (reachable.contains(beside)) {
                return false;
            }
        }
        return true;
    }

    /** How far outside the spot the walk may stray, purely to bridge in from where we are standing. */
    private static final int SPOT_APPROACH_MARGIN = 3;

    /**
     * Re-walks the live-reachable set confined to the configured mining spot, so a vein only counts
     * as reachable if we can get to it <b>without leaving the area</b>.
     *
     * <p>Containment is the rule, not a tile list. The upstream Motherlode plugin reaches the same
     * conclusion — {@code MotherloadMineScript}: "Static areas for lower floor to avoid getting stuck
     * behind rockfall". Rockfalls respawn, so anything outside the spot is somewhere we can be sealed
     * into; whether it happens to be open right now is irrelevant. An earlier attempt vetoed the
     * rockfall's own tiles instead, which does not work: that list is a scatter of tiles, not a wall,
     * so with the rock cleared a path simply goes around it.
     *
     * <p>The small margin exists only so a walk that starts outside the area (stepping off the ladder)
     * can bridge inward. Barrier tiles stay vetoed inside the margin, which is what keeps the bridge
     * from becoming its own way into the corridor.
     */
    static Set<WorldPoint> confineToSpot(MLMMiningSpot spot, WorldPoint from, Set<WorldPoint> live,
                                                                                  BiPredicate<WorldPoint, WorldPoint> canStep) {
        if (live.isEmpty() || spot == null) {
            return live;
        }
        // The margin is only for bridging IN from outside. Once we are standing in the spot there is
        // nothing to bridge, so confine strictly — otherwise the bubble hangs a few tiles over the
        // area's edge for no reason, which is exactly the direction we do not want to drift.
        final int margin = spot.contains(from) ? 0 : SPOT_APPROACH_MARGIN;

        Set<WorldPoint> safe = new HashSet<>();
        java.util.ArrayDeque<WorldPoint> queue = new java.util.ArrayDeque<>();
        safe.add(from);
        queue.add(from);
        while (!queue.isEmpty()) {
            WorldPoint p = queue.poll();
            for (WorldPoint n : adjacentTiles(p)) {
                if (safe.contains(n) || !live.contains(n)) continue;
                if (IrkedMLMMapConstants.isRockfallBlockedTile(n)) continue;
                if (!spot.contains(n) && n.distanceTo(from) > margin) continue;
                if (!canStep.test(p, n)) continue; // both tiles reachable, but with a wall between them
                safe.add(n);
                queue.add(n);
            }
        }
        return safe;
    }

    /** Where a player stands to mine it; the vein's own tile is never walkable. */
    private static List<WorldPoint> adjacentTiles(WorldPoint p) {
        return Arrays.asList(
                new WorldPoint(p.getX() + 1, p.getY(), p.getPlane()),
                new WorldPoint(p.getX() - 1, p.getY(), p.getPlane()),
                new WorldPoint(p.getX(), p.getY() + 1, p.getPlane()),
                new WorldPoint(p.getX(), p.getY() - 1, p.getPlane()));
    }

    static boolean reachableCacheUsable(MLMMiningSpot cachedSpot, WorldPoint cachedOrigin, long ageMs,
                                                                                MLMMiningSpot spot, WorldPoint from) {
        return cachedOrigin != null
                && cachedSpot == spot
                // From outside the spot the search may use the approach margin; from inside it may not.
                && spot.contains(cachedOrigin) == spot.contains(from)
                && cachedOrigin.distanceTo(from) <= REACHABLE_RECOMPUTE_DISTANCE
                && ageMs < REACHABLE_CACHE_MS;
    }

    /** Computed once and reused - selection asks about many veins per tick. */
    private Set<WorldPoint> reachableTilesFrom(MLMMiningSpot spot, WorldPoint from) {
        long now = System.currentTimeMillis();
        if (reachableCache != null
                && reachableCacheUsable(reachableCacheSpot, reachableCacheOrigin, now - reachableCacheMs, spot, from)) {
            return reachableCache;
        }
        // One client-thread hop for the whole search: every step reads the live collision flags.
        Set<WorldPoint> tiles = Microbot.getClientThread().invoke(() -> confineToSpot(spot, from,
                Rs2Tile.getReachableTilesFromTile(from, REACHABLE_SEARCH_RADIUS).keySet(), Rs2Tile::isEdgePassable));
        reachableCache = Collections.unmodifiableSet(new HashSet<>(tiles));
        reachableCacheOrigin = from;
        reachableCacheSpot = spot;
        reachableCacheMs = now;
        return tiles;
    }

    /**
     * True when the player is within click range of an active ore vein in the configured spot.
     */
    public boolean isPlayerNextToMineableVein(MLMMiningSpot spot, WorldPoint playerLoc) {
        if (playerLoc == null || spot == null) {
            return false;
        }
        return tileCache.query()
                .where(this::isActiveVein)
                .toList()
                .stream()
                .anyMatch(o -> {
                    WorldPoint veinLoc = o.getWorldLocation();
                    return playerLoc.distanceTo(veinLoc) <= VEIN_CLICK_MAX_DISTANCE
                            && isVeinInSelectableZone(spot, veinLoc)
                            && !isVeinBarred(spot, playerLoc, veinLoc);
                });
    }

    /**
     * Whether pickaxe special may fire: actively mining and adjacent to session target or any mineable vein.
     */
    public boolean canAttemptPickaxeSpecial(MLMMiningSpot spot, WorldPoint playerLoc) {
        if (playerLoc == null || spot == null || subState != MiningSubState.MINING) {
            return false;
        }
        if (targetVein != null && playerLoc.distanceTo(targetVein) <= VEIN_CLICK_MAX_DISTANCE) {
            return true;
        }
        return isPlayerNextToMineableVein(spot, playerLoc);
    }

    private int clusterNeighborBonus(WorldPoint vp, List<Rs2TileObjectModel> allVeins) {
        int neighbors = 0;
        for (Rs2TileObjectModel other : allVeins) {
            if (other == null || !isActiveVein(other)) continue;
            WorldPoint op = other.getWorldLocation();
            if (op.equals(vp)) continue;
            if (vp.distanceTo(op) <= 3) {
                neighbors++;
            }
        }
        return Math.min(neighbors * SCORE_CLUSTER_BONUS_PER_NEIGHBOR, SCORE_CLUSTER_BONUS_CAP);
    }

    /**
     * Effective sort key: {@link #scoreVein} minus cluster bonus. Values {@code >= SCORE_OUT_OF_RANGE} are not rankable.
     */
    private int effectiveVeinScore(Rs2TileObjectModel vein, MLMMiningSpot spot, WorldPoint playerLoc,
                                   List<Rs2TileObjectModel> allVeins) {
        int score = scoreVein(vein, spot, playerLoc);
        if (score >= SCORE_OUT_OF_RANGE) {
            return score;
        }
        if (usesUpperStandMiningRules(spot)) {
            return score;
        }
        return score - clusterNeighborBonus(vein.getWorldLocation(), allVeins);
    }

    /**
     * Queries active veins in the spot and returns them sorted by effective score (ascending).
     */
    private List<RankedVein> rankVeinsByScore(MLMMiningSpot spot, WorldPoint playerLoc,
                                              List<Rs2TileObjectModel> allVeins) {
        if (spot == null || playerLoc == null || allVeins == null || allVeins.isEmpty()) {
            return Collections.emptyList();
        }
        List<RankedVein> ranked = new ArrayList<>();
        for (Rs2TileObjectModel vein : allVeins) {
            int effective = effectiveVeinScore(vein, spot, playerLoc, allVeins);
            if (effective < SCORE_OUT_OF_RANGE) {
                ranked.add(new RankedVein(vein, effective));
            }
        }
        ranked.sort(Comparator.comparingInt(r -> r.effectiveScore));
        return ranked;
    }

    /** Top scored vein for walk/reposition paths, or null when none are in range. */
    private RankedVein selectBestRankedVein(MLMMiningSpot spot, WorldPoint playerLoc) {
        List<RankedVein> ranked = rankVeinsByScore(spot, playerLoc, queryVeinsInSpot(spot));
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    /**
     * Active veins we can actually walk to. The walkability filter used to be skipped for upper
     * chambers, leaving the 7x6 box as the only test — so a vein sealed behind a rockfall was a valid
     * pick. Reachability is correct on both floors now, so they need no special case.
     */
    private List<Rs2TileObjectModel> queryVeinsInSpot(MLMMiningSpot spot) {
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        return tileCache.query()
                .where(o -> isActiveVein(o)
                        && isVeinInSelectableZone(spot, o.getWorldLocation())
                        && !isVeinBarred(spot, playerLoc, o.getWorldLocation()))
                .toList();
    }

    /** Veins must be inside spot {@link WorldArea} union (safe/selectable zones). */
    private boolean isVeinInSelectableZone(MLMMiningSpot spot, WorldPoint veinLoc) {
        if (spot == null || veinLoc == null) {
            return false;
        }
        return spot.contains(veinLoc);
    }

    /** Tiles the vein selector currently believes it can walk to, for the map overlay (read-only). */
    public Set<WorldPoint> getReachableTilesView() {
        Set<WorldPoint> snapshot = reachableCache;
        return snapshot != null ? snapshot : Collections.emptySet();
    }

    /** Session rockfall tiles for map overlay (read-only). */
    public Set<WorldPoint> getRememberedRockfallsView() {
        return rememberedRockfalls; // already an immutable snapshot
    }

    /**
     * Nearest reachable, non-barred, in-zone active vein within {@link #ADJACENT_DIST} of the player,
     * or null if none. Recently-failed veins are excluded so we don't re-pick one we just gave up on.
     * Anti-crash is deliberately ignored here: if we're already standing next to a rock, mining it is
     * the human move, and re-selecting to dodge a crowd would just thrash.
     */
    private WorldPoint nearestAdjacentVein(MLMMiningSpot spot, WorldPoint playerLoc,
                                           List<Rs2TileObjectModel> allVeins) {
        WorldPoint best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Rs2TileObjectModel v : allVeins) {
            if (!isActiveVein(v)) {
                continue;
            }
            WorldPoint vp = v.getWorldLocation();
            int d = playerLoc.distanceTo(vp);
            if (d > ADJACENT_DIST || d >= bestDist) {
                continue;
            }
            if (!isVeinInSelectableZone(spot, vp) || isVeinBarred(spot, playerLoc, vp)
                    || isRecentlyFailed(vp, spot)) {
                continue;
            }
            bestDist = d;
            best = vp;
        }
        return best;
    }

    private WorldPoint selectVein(MLMMiningSpot spot) {
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null) {
            return null;
        }

        List<Rs2TileObjectModel> allVeins = queryVeinsInSpot(spot);
        if (allVeins.isEmpty()) {
            log.info("[MiningSession] No active veins found in spot {}", spot);
            return null;
        }

        // Nearest-vein rule: a human standing next to a rock mines THAT rock (left/right/behind),
        // not a farther "better-scored" one. If a reachable, non-barred, in-zone active vein is
        // within ADJACENT_DIST, pick the nearest such vein and skip cluster/2nd-3rd randomization.
        WorldPoint adjacent = nearestAdjacentVein(spot, playerLoc, allVeins);
        if (adjacent != null && antiCrashApplies(spot) && isCrowded(adjacent)) {
            log.debug("[MiningSession] Adjacent vein at {} has another player on it — ranking the rest", adjacent);
            adjacent = null;
        }
        if (adjacent != null) {
            log.info("[MiningSession] Adjacent vein at {} (dist={}) — mining nearest, skipping ranking",
                    adjacent, playerLoc.distanceTo(adjacent));
            return adjacent;
        }

        List<RankedVein> ranked = rankVeinsByScore(spot, playerLoc, allVeins);
        if (ranked.isEmpty()) {
            log.warn("[MiningSession] All {} veins filtered for spot {} (player={}, inSection={})",
                    allVeins.size(), spot, playerLoc, spot.contains(playerLoc));
            return null;
        }

        List<RankedVein> pickPool = ranked;
        if (usesUpperStandMiningRules(spot)) {
            List<RankedVein> clickReady = new ArrayList<>();
            for (RankedVein r : ranked) {
                WorldPoint vp = r.vein.getWorldLocation();
                if (isReadyToClick(vp, playerLoc, spot)) {
                    clickReady.add(r);
                }
            }
            if (clickReady.isEmpty()) {
                log.info("[MiningSession] No click-ready veins in {} (best dist={}, pool={})",
                        spot,
                        playerLoc.distanceTo(ranked.get(0).vein.getWorldLocation()),
                        ranked.size());
                return null;
            }
            // Prefer the click-ready vein closest to the player's current position rather than the one
            // with the best stand-tile score, so veins behind or beside the player win over a
            // "perfect" stand-tile vein further away.
            clickReady.sort(Comparator.comparingInt(r -> playerLoc.distanceTo(r.vein.getWorldLocation())));
            pickPool = clickReady;
        }

        final WorldPoint finalPlayer = playerLoc;
        int num = pickPool.size();
        Rs2TileObjectModel chosen = pickPool.get(0).vein;
        if (isHumanLikeEnabled() && num > 1) {
            java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
            double r = rng.nextDouble();
            if (r < 0.25 && num >= 3) {
                chosen = pickPool.get(2).vein;
            } else if (r < 0.50) {
                chosen = pickPool.get(1).vein;
            } else if (r < 0.57) {
                int d0 = finalPlayer.distanceTo(chosen.getWorldLocation());
                int d1 = finalPlayer.distanceTo(pickPool.get(1).vein.getWorldLocation());
                if (d1 <= d0 + 2) {
                    chosen = pickPool.get(1).vein;
                }
            }
        }

        final Rs2TileObjectModel finalChosen = chosen;

        // Anti-crash (lower floor): take the first candidate nobody else is on; keep the pick if all are taken.
        if (antiCrashApplies(spot) && isCrowded(finalChosen.getWorldLocation())) {
            for (RankedVein candidate : pickPool) {
                if (candidate.vein != finalChosen && !isCrowded(candidate.vein.getWorldLocation())) {
                    log.debug("[MiningSession] Chosen vein at {} crowded — switching to {}",
                            finalChosen.getWorldLocation(), candidate.vein.getWorldLocation());
                    chosen = candidate.vein;
                    break;
                }
            }
        }

        WorldPoint chosenPoint = chosen.getWorldLocation();
        int chosenScore = effectiveVeinScore(chosen, spot, finalPlayer, allVeins);
        boolean ready = isReadyToClick(chosenPoint, finalPlayer, spot);
        log.info("[MiningSession] Score-selected vein at {} (dist={}, score={}, readyToClick={}, pool={})",
                chosenPoint,
                finalPlayer.distanceTo(chosenPoint),
                chosenScore,
                ready,
                num);
        return chosenPoint;
    }

    /**
     * Steps to another operating tile in the chamber (not a vein tile) to get within click range of wall veins.
     */
    private boolean nudgeTowardReachableVein(MLMMiningSpot spot, WorldPoint player) {
        if (spot == null || player == null || !usesUpperStandMiningRules(spot)) {
            return false;
        }
        List<RankedVein> ranked = rankVeinsByScore(spot, player, queryVeinsInSpot(spot));
        if (ranked.isEmpty()) {
            return false;
        }
        WorldPoint vein = null;
        for (RankedVein r : ranked) {
            WorldPoint vp = r.vein.getWorldLocation();
            if (isReachableFromPreferredStand(spot, vp)) {
                vein = vp;
                break;
            }
        }
        if (vein == null || isReadyToClick(vein, player, spot)) {
            return false;
        }
        WorldPoint stand = nearestOperatingTileTowardVein(spot, player, vein);
        if (stand == null || player.equals(stand) || player.distanceTo(stand) > 4) {
            return false;
        }
        log.info("[MiningSession] Stepping to operating tile {} (vein {} dist={})",
                stand, vein, player.distanceTo(vein));
        Rs2Walker.walkFastCanvas(stand);
        return true;
    }

    private WorldPoint nearestOperatingTileTowardVein(MLMMiningSpot spot, WorldPoint player, WorldPoint vein) {
        Set<WorldPoint> stands = spot.getPreferredOperatingTiles();
        if (stands.isEmpty()) {
            return null;
        }
        WorldPoint best = null;
        int bestStep = Integer.MAX_VALUE;
        int playerVeinDist = player.distanceTo(vein);
        for (WorldPoint tile : stands) {
            if (tile == null || tile.equals(player)) {
                continue;
            }
            int step = player.distanceTo(tile);
            if (step > 4) {
                continue;
            }
            if (tile.distanceTo(vein) < playerVeinDist && step < bestStep) {
                bestStep = step;
                best = tile;
            }
        }
        return best;
    }

    private static Set<WorldPoint> permanentRockfallBarriersFor(MLMMiningSpot spot) {
        return IrkedMLMMapConstants.permanentRockfallBarriersFor(spot);
    }

    /**
     * Tiles we never mine, whatever the collision map currently says. This is a safety rule about
     * where it is safe to stand, not a description of what happens to be open — the two are different
     * questions and only the barrier list answers the first.
     */
    private static boolean isPermanentRockfallBarrier(MLMMiningSpot spot, WorldPoint point) {
        return point != null && permanentRockfallBarriersFor(spot).contains(point);
    }

    // The straight-line pathCrossesBarriers() test was removed; live reachability replaces it for the
    // "can I get there" question. The barrier LIST is deliberately retained above as a hard veto.

    private boolean pathCrossesRockfallMemory(WorldPoint from, WorldPoint to) {
        if (from == null || to == null || rememberedRockfalls.isEmpty()) {
            return false;
        }
        for (WorldPoint rock : rememberedRockfalls) {
            if (pathCrossesTile(from, to, rock)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Bresenham: does the line from {@code from} to {@code to} pass through {@code probe}?
     * <p>Straight-line rasterization, not real pathfinding: a rockfall one tile off the line is missed,
     * so a vein that is actually barred can still be picked. That is acceptable here — a bad pick just
     * walks, and the recovery watchdog unsticks it. If it ever needs to be exact, MLM rockfalls do block
     * movement, so {@code Rs2Tile.isTileReachable} can replace this check outright.
     */
    private boolean pathCrossesTile(WorldPoint from, WorldPoint to, WorldPoint probe) {
        if (from == null || to == null || probe == null) {
            return false;
        }
        int dx = Math.abs(to.getX() - from.getX());
        int dz = Math.abs(to.getY() - from.getY());
        int sx = from.getX() < to.getX() ? 1 : -1;
        int sz = from.getY() < to.getY() ? 1 : -1;
        int err = dx - dz;
        int x = from.getX();
        int z = from.getY();

        while (true) {
            if (probe.getX() == x && probe.getY() == z && probe.getPlane() == from.getPlane()) {
                return true;
            }
            if (x == to.getX() && z == to.getY()) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                z += sz;
            }
        }
        return false;
    }

    /** Finds a nearby rockfall object. */
    private Rs2TileObjectModel findNearbyRockfall() {
        return tileCache.query()
                .where(o -> isRockfall(o.getId()) && o.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= 3)
                .nearest();
    }

    /**
     * Finds the vein object nearest to {@code wp} (within 2 tiles) by ore-vein ID.
     * Returns null if the vein is depleted.
     */
    private Rs2TileObjectModel findVeinAt(WorldPoint wp) {
        if (wp == null) return null;
        Rs2TileObjectModel vein = tileCache.query()
                .where(o -> isOreVein(o.getId())
                        && isActiveVein(o)
                        && o.getWorldLocation().equals(wp))
                .nearest();
        return vein != null && isActiveVein(vein) ? vein : null;
    }

    /** Uncut gems that turn up while mining. */
    private static final int[] GEM_IDS = {
            ItemID.UNCUT_SAPPHIRE, ItemID.UNCUT_EMERALD, ItemID.UNCUT_RUBY, ItemID.UNCUT_DIAMOND
    };

    /** The gems to drop this tick: each carried one once, up to the cap. */
    static <T> List<T> gemsToDrop(Stream<T> items, ToIntFunction<T> id) {
        return items.filter(i -> Arrays.stream(GEM_IDS).anyMatch(g -> g == id.applyAsInt(i)))
                .limit(MAX_GEM_DROPS_PER_TICK)
                .collect(Collectors.toList());
    }

    /** Whether the user wants gems binned. A gem bag in use makes dropping them pointless. */
    private boolean shouldDropGems() {
        return config != null && config.dropGems() && !config.useGemBag();
    }

    /**
     * Drops a few of the uncut gems carried, one click per slot. Usually there are only one or two; any
     * left over go on the next mining tick.
     *
     * @return {@code true} if anything was dropped
     */
    private boolean dropGemsFromInventory() {
        boolean dropped = false;
        for (Rs2ItemModel gem : gemsToDrop(Rs2Inventory.items(), Rs2ItemModel::getId)) {
            if (!Rs2Inventory.interact(gem, "Drop")) {
                break;
            }
            dropped = true;
        }
        if (dropped) {
            log.debug("[MiningSession] Dropped gems mid-bout — re-clicking the same vein at {}", targetVein);
        }
        return dropped;
    }


    /**
     * Returns {@code true} if any active (non-depleted) ore vein is present at
     * the given WorldPoint. MLM respawns veins (sometimes with different IDs
     * on the same tile), so we check for any of our active ore-vein IDs.
     */
    private boolean isVeinPresent(WorldPoint wp) {
        if (wp == null) return false;
        Rs2TileObjectModel vein = tileCache.query()
                .where(o -> isOreVein(o.getId()) && o.getWorldLocation().equals(wp))
                .nearest();
        return vein != null && isActiveVein(vein);
    }

    /** Returns {@code true} if the player is inside the mining spot area or near its anchors (correct floor). */
    private boolean isNearSpot(MLMMiningSpot spot) {
        if (spot == null) return false;
        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null) return false;
        if (isPlayerInSpotArea(spot, here)) {
            return true;
        }
        if (spot.isUpstairs() && !isUpperFloor()) {
            return false;
        }
        if (spot.isDownstairs() && isUpperFloor()) {
            return false;
        }
        int anchorRadius = spot.isUpstairs() ? 12 : 10;
        return isNearAnchor(spot, here, anchorRadius);
    }

    /** Area check plus MLM floor height (upper/lower share x,y on plane 0). */
    private boolean isPlayerInSpotArea(MLMMiningSpot spot, WorldPoint here) {
        if (spot == null || here == null) {
            return false;
        }
        if (!spot.contains(here)) {
            return false;
        }
        if (spot.isUpstairs()) {
            return isUpperFloor();
        }
        if (spot.isDownstairs()) {
            return !isUpperFloor();
        }
        return true;
    }

    private boolean isNearAnchor(MLMMiningSpot spot, WorldPoint here, int maxDist) {
        if (spot == null || here == null) return false;
        List<WorldPoint> anchors = spot.getWorldPoint();
        if (anchors == null) return false;
        for (WorldPoint anchor : anchors) {
            if (anchor != null && here.distanceTo(anchor) <= maxDist) {
                return true;
            }
        }
        return false;
    }

    /**
     * Walk toward a mining target: canvas for short hops, web-walk when far (including lower tunnels).
     * Clears stale web-walk targets when already at the goal (avoids pathfinder-still-null stall).
     */
    private void walkTowardMiningTarget(WorldPoint target, MLMMiningSpot spot, WorldPoint playerLoc) {
        if (target == null || playerLoc == null) return;
        int distance = playerLoc.distanceTo(target);
        if (distance <= 2) {
            Rs2Walker.setTarget(null, "mlm_mining_retarget");
            return;
        }
        Rs2Walker.setTarget(null, "mlm_mining_retarget");

        int webWalkThreshold = (spot != null && spot.isDownstairs())
                ? LOWER_WEB_WALK_MIN_DISTANCE
                : UPPER_WEB_WALK_MIN_DISTANCE;

        if (distance > webWalkThreshold) {
            log.debug("[MiningSession] Web-walk to {} ({} tiles, threshold={})",
                    target, distance, webWalkThreshold);
            Rs2Walker.walkTo(target);
        } else {
            log.debug("[MiningSession] walkFastCanvas to {} ({} tiles)", target, distance);
            Rs2Walker.walkFastCanvas(target);
        }
    }

    /**
     * Returns true if the player must use the ladder before mining this spot.
     * Proximity to an upper-spot anchor on the lower floor does not count (same x,y, different height).
     */
    private boolean isWrongFloor(MLMMiningSpot spot) {
        if (spot == null) return false;
        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null) return false;

        boolean onUpper = isUpperFloor();

        if (spot.isUpstairs()) {
            if (!onUpper) {
                log.info("[MiningSession] Spot {} is upstairs but player is on lower floor - climbing up first", spot);
                return true;
            }
            // On upper floor: correct floor even if not yet in the chamber (walking handles distance).
            return false;
        }

        if (spot.isDownstairs()) {
            if (onUpper) {
                log.info("[MiningSession] Spot {} is downstairs but player is on upper floor - climbing down first", spot);
                return true;
            }
            // On lower floor: correct floor; walking handles distance into tunnels/area.
            return false;
        }

        return !onUpper;
    }

    private boolean antiCrashApplies(MLMMiningSpot spot) {
        return config.useAntiCrash() && !spot.isUpstairs();
    }

    /** Another player standing within two tiles of the vein. */
    private boolean isCrowded(WorldPoint vein) {
        var playerCache = Microbot.getRs2PlayerCache();
        if (playerCache == null || vein == null) return false;
        return playerCache.query()
                .where(p -> {
                    String pName = p.getName();
                    WorldPoint at = p.getWorldLocation();
                    return pName != null && at != null
                            && !pName.equalsIgnoreCase(cachedLocalPlayerName)
                            && at.distanceTo(vein) <= 2;
                })
                .count() > 0;
    }
}
