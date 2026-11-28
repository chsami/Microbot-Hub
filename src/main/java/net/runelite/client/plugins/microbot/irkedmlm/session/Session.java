package net.runelite.client.plugins.microbot.irkedmlm.session;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.irkedmlm.HumanBehaviorProfile;
import net.runelite.client.plugins.microbot.irkedmlm.IrkedMLMConfig;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.api.Point;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;

/**
 * Abstract base class for all tick-progressive Motherlode Mine sessions.
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Subclasses MUST call {@link #transition(State)} to change outer state.</li>
 *   <li>Subclasses MUST implement {@link #tickInternal()} (may be a no-op if the
 *       session defines its own parameterised tick).</li>
 *   <li>No {@code Thread.sleep}, no {@code while}-loops, no {@code sleepUntil}.</li>
 *   <li>Use {@link #scheduleNext(long)} to throttle repeated actions.</li>
 *   <li>Use {@link #subElapsed()} to detect phase time-outs.</li>
 * </ul>
 */
@Slf4j
public abstract class Session {

    // ------------------------------------------------------------------
    // Outer lifecycle state
    // ------------------------------------------------------------------

    public enum State {
        IDLE,
        ACTIVE,
        COMPLETE,
        FAILED
    }

    @Getter
    protected State state = State.IDLE;

    // ------------------------------------------------------------------
    // Timing helpers (ms)
    // ------------------------------------------------------------------

    /** Absolute ms at which the current {@link #state} was entered. */
    protected long stateEnteredMs = 0L;

    /**
     * Absolute ms at which the current <em>sub</em>-state was entered.
     * Sub-classes write this directly when they transition sub-states.
     */
    protected long subStateEnteredMs = 0L;

    /** The earliest ms at which the next action may fire. */
    protected long nextActionMs = 0L;

    // ------------------------------------------------------------------
    // Overlay label
    // ------------------------------------------------------------------

    /** Human-readable current phase label used by the overlay. */
    @Getter
    private String phaseLabel = "idle";

    // ------------------------------------------------------------------
    // Floor & Navigation constants (MLM-specific)
    // ------------------------------------------------------------------

    protected static final int UPPER_FLOOR_HEIGHT_THRESHOLD = -490;
    protected static final net.runelite.api.coords.WorldPoint LADDER_BOTTOM_WALK =
            net.runelite.client.plugins.microbot.irkedmlm.IrkedMLMMapConstants.LADDER_BOTTOM_WALK;
    protected static final net.runelite.api.coords.WorldPoint LADDER_TOP_WALK =
            net.runelite.client.plugins.microbot.irkedmlm.IrkedMLMMapConstants.LADDER_TOP_WALK;

    // ------------------------------------------------------------------
    // Floor detection cache (rate-limited client thread access)
    // Same pattern as script's varbit/XP caches to avoid TimeoutException
    // spam and breakhandler modals when client thread is busy (startup, events).
    // ------------------------------------------------------------------
    // STATIC: one player, one floor. Per-instance caches let sessions disagree after a climb.
    private static volatile long lastUpperFloorCheckMs = 0L;
    private static volatile Floor lastFloorResult = Floor.UNKNOWN;
    private static final long UPPER_FLOOR_CACHE_MS = 1200L; // ~2 ticks, floor doesn't flip often

    /** UNKNOWN means the client read failed — callers must wait, not guess. */
    public enum Floor { LOWER, UPPER, UNKNOWN }

    // ------------------------------------------------------------------
    // Player name cache (pushed from script; sessions never do their own client thread fetch)
    // ------------------------------------------------------------------
    /**
     * Local player name kept up-to-date by the orchestrator script.
     * Sessions (Mining/Repair) use this for anti-crash and "another player repairing" checks
     * instead of each doing their own runOnClientThreadOptional + getLocalPlayer.
     */
    protected String cachedLocalPlayerName = "";

    /**
     * Per-login behavioural identity, pushed from the orchestrator script (same pattern as the player
     * name). Defaults to a neutral/average player so timing/odds match the original hard-coded constants
     * until a real personality is rolled at login. Never null.
     */
    protected net.runelite.client.plugins.microbot.irkedmlm.SessionPersonality personality =
            net.runelite.client.plugins.microbot.irkedmlm.SessionPersonality.neutral();

    /**
     * Injected config reference (for human-like behavior gating).
     * Subclasses that need per-decision human checks (vein selection, 1-strut skip, admire, etc.)
     * call isHumanLikeEnabled(). Base uses it for ladder hesitation.
     */
    protected final IrkedMLMConfig config;

    public Session(IrkedMLMConfig config) {
        this.config = config;
    }

    // ------------------------------------------------------------------
    // State predicates
    // ------------------------------------------------------------------

    public boolean isIdle()     { return state == State.IDLE;     }
    public boolean isActive()   { return state == State.ACTIVE;   }
    public boolean isComplete() { return state == State.COMPLETE; }
    public boolean isFailed()   { return state == State.FAILED;   }

    /**
     * Returns whether MLM-specific human-like behavior is enabled (our pauses, variation, imperfection).
     * Used to gate random waits / glances / 1-strut skip / admire / jitter etc.
     * When false we use small consistent delays + optimal choices.
     */
    protected boolean isHumanLikeEnabled() {
        return config != null && config.enableHumanLikeBehavior();
    }

    /** Milliseconds to wait before the next action (fast vs human-like), with timing variation applied. */
    protected long tickDelayMs(long fastMs, long humanMs) {
        return jitter(isHumanLikeEnabled() ? humanMs : fastMs);
    }

    /**
     * Adds timing variation to a base delay so actions never fire on a fixed cadence.
     * <ul>
     *   <li>Fast mode: tight ±10% — still "sure fast", just not a perfect metronome
     *       (a perfectly periodic click cadence is itself a detection signal).</li>
     *   <li>Human mode: wide 0.65x–1.35x band, with a 12% chance of a 1.5x–2.2x slow
     *       outlier (glance away / distraction) so speeds are genuinely mixed.</li>
     * </ul>
     * Safety timeouts and stall detectors use raw literals (not this path), so they are
     * unaffected; spam guards keep their floor because fast jitter never drops below 0.9x.
     */
    private long jitter(long baseMs) {
        if (baseMs <= 0) {
            return baseMs;
        }
        if (!isHumanLikeEnabled()) {
            return Rs2Random.between((int) (baseMs * 0.90), (int) (baseMs * 1.10) + 1);
        }
        // Reaction speed, slow-outlier likelihood, and band width are all this player's fixed traits,
        // so a "fast, confident" run stays snappy and a "slow, relaxed" run stays languid all session.
        long scaled = personality.reaction(baseMs);
        if (isFocusedTask()) {
            return focusedJitter(scaled);
        }
        if (Rs2Random.between(0, 100) < personality.slowOutlierChance()) {
            return Rs2Random.between((int) (scaled * 1.5), (int) (scaled * 2.2) + 1);
        }
        return Rs2Random.between(
                (int) (scaled * personality.jitterLow()),
                (int) (scaled * personality.jitterHigh()) + 1);
    }

    /**
     * Timing for a {@linkplain #isFocusedTask() focused} chore: still randomised, but compressed and
     * without the long distraction tail. Attention is a real player's scarcest resource — they spend
     * it in bursts. Mining is the AFK part of MLM (park the cursor, click a vein every 20-odd
     * seconds), so when a sack/hopper/repair trip interrupts that, the player is looking at the
     * screen and works through it briskly before going back to idling. Applying the AFK-flavoured
     * distribution to those trips is what made the bot feel sluggish.
     */
    private long focusedJitter(long scaled) {
        long focused = Math.max(FOCUS_FLOOR_MS, Math.round(scaled * FOCUS_SPEEDUP));
        // The distraction tail survives but is rare and shallow: even a focused player occasionally
        // glances away mid-chore, they just don't do it for two seconds.
        if (Rs2Random.between(0, 100) < Math.max(2, personality.slowOutlierChance() / 3)) {
            return Rs2Random.between((int) (focused * 1.25), (int) (focused * 1.7) + 1);
        }
        return Rs2Random.between((int) (focused * FOCUS_BAND_LOW), (int) (focused * FOCUS_BAND_HIGH) + 1);
    }

    /**
     * Whether this session is a short, deliberate chore (hopper trip, sack emptying, water-wheel
     * repair) rather than the AFK mining loop. Focused sessions get {@link #focusedJitter} timing.
     * Default false; MiningSession deliberately keeps the relaxed profile.
     */
    protected boolean isFocusedTask() {
        return false;
    }

    protected void scheduleNextAdaptive(long fastMs, long humanMs) {
        scheduleNext(tickDelayMs(fastMs, humanMs));
    }

    /**
     * Re-click guard for the ladder, keyed on the <b>outcome</b> rather than on elapsed time.
     *
     * <p>Two earlier attempts used a fixed dead time after the click. That cannot work: the click is
     * followed by a walk whose length depends on how far away the ladder is, and the moment the
     * player <em>arrives</em> they are briefly neither moving nor animating while the floor has not
     * changed yet — by which point any fixed window has long expired, so the state machine clicked
     * again. The window was guarding the start of the climb; the second click happens at the end.
     *
     * <p>So instead: once a climb is asked for it stays pending until we actually see it land (the
     * floor changed) or the player has been completely still for {@link #LADDER_QUIET_MIN_MS}-ish,
     * which is the point a real player would conclude the click missed and try again. Walking and
     * animating both count as the click being acted on and keep pushing the quiet window back.
     *
     * <p>Static because there is one player and one ladder but four Session instances that each call
     * {@link #ensureFloor}; as per-instance state, a handover mid-climb reset the guard to zero.
     */
    private static volatile long ladderClickMs = 0L;
    /** Last tick at which the player was visibly acting on the climb (walking or animating). */
    private static volatile long ladderProgressMs = 0L;
    /** How long the player must be completely still before we accept the click missed. Randomised. */
    private static volatile long ladderQuietMs = 0L;

    private static final int LADDER_QUIET_MIN_MS = 1_900;
    private static final int LADDER_QUIET_MAX_MS = 3_100;

    /**
     * Pure decision for the guard above, so it is checkable without a client.
     *
     * @param arrived   whether the player is already on the floor the climb was asked for
     * @param acting    whether the player is currently walking or animating
     * @param quietMs   how long stillness must last before a retry is allowed
     * @return {@code true} while the climb is still in flight and must not be re-clicked
     */
    static boolean climbStillPending(long clickMs, long progressMs, long nowMs,
                                     long quietMs, boolean arrived, boolean acting) {
        if (clickMs <= 0L) {
            return false; // nothing asked for
        }
        if (arrived) {
            return false; // it landed
        }
        if (acting) {
            return true;  // walking to the ladder, or climbing it
        }
        return nowMs - Math.max(clickMs, progressMs) < quietMs;
    }

    private void noteLadderClick() {
        long now = System.currentTimeMillis();
        ladderClickMs = now;
        ladderProgressMs = now;
        ladderQuietMs = Rs2Random.between(LADDER_QUIET_MIN_MS, LADDER_QUIET_MAX_MS);
    }

    /**
     * Pulls the camera back one step, the way a player does when what they want to click is too close
     * to frame. Bounded by {@link #MIN_ZOOM} so we never zoom out to a uselessly tiny scene, and
     * randomised so it is not a fixed keystroke every time.
     */
    private void zoomOutAStep() {
        int current = Rs2Camera.getZoom();
        if (current <= MIN_ZOOM) {
            log.debug("[Session] Already at minimum zoom — cannot pull back further");
            return;
        }
        int target = Math.max(MIN_ZOOM,
                current - Rs2Random.between(ZOOM_OUT_STEP_MIN, ZOOM_OUT_STEP_MAX));
        log.info("[Session] Target will not frame at zoom {} — pulling back to {}", current, target);
        Rs2Camera.setZoom(target);
    }

    /** True while walking or animating. Counts as progress on a climb in flight, so its retry clock starts over. */
    protected boolean busyMoving(int animationMs) {
        boolean busy = Rs2Player.isMoving() || Rs2Player.isAnimating(animationMs);
        if (busy && ladderClickMs != 0L) {
            ladderProgressMs = System.currentTimeMillis();
        }
        return busy;
    }

    private void clearLadderClick() {
        ladderClickMs = 0L;
        ladderProgressMs = 0L;
    }

    /**
     * True while a previously-requested climb is still in flight. Also advances the progress clock, so
     * a long walk to a distant ladder never times out mid-stride.
     */
    private boolean ladderClimbPending(boolean targetIsUp) {
        if (ladderClickMs == 0L) {
            return false;
        }
        invalidateUpperFloorCache();
        boolean acting = busyMoving(1200);
        long now = System.currentTimeMillis();
        boolean arrived = onFloor(currentFloor(), targetIsUp);
        boolean pending = climbStillPending(ladderClickMs, ladderProgressMs, now,
                ladderQuietMs, arrived, acting);
        if (!pending) {
            if (arrived) {
                log.debug("[Session] Climb landed (upper={})", targetIsUp);
            } else {
                log.debug("[Session] Climb produced no movement for {}ms — allowing a retry", ladderQuietMs);
            }
            clearLadderClick();
        }
        return pending;
    }

    /** Delay multiplier applied while running a focused chore (see {@link #isFocusedTask()}). */
    private static final double FOCUS_SPEEDUP   = 0.55;
    /** Jitter band for focused chores — narrower than the AFK band, but never a fixed cadence. */
    private static final double FOCUS_BAND_LOW  = 0.80;
    private static final double FOCUS_BAND_HIGH = 1.20;
    /** Floor so the speed-up can never collapse a delay into click-spam territory. */
    private static final long   FOCUS_FLOOR_MS  = 60L;

    /** Minimum pause after sub-state changes so fast mode does not mass-click. */
    protected void scheduleSubStateEntryDelay() {
        // Fast floor lowered to ~90ms so human-like OFF is genuinely fast between sub-states.
        scheduleNextAdaptive(90L, 400L);
    }

    // ------------------------------------------------------------------
    // Floor helpers
    // ------------------------------------------------------------------

    /** Authoritative floor, shared by all sessions. A failed read is never cached. */
    public static Floor currentFloor() {
        if (!net.runelite.client.plugins.microbot.Microbot.isLoggedIn()) {
            // Early exit avoids queuing client thread work during login and other blocking events,
            // which otherwise produces TimeoutException and breakhandler spam at startup.
            return Floor.UNKNOWN;
        }
        long now = System.currentTimeMillis();
        if (lastFloorResult != Floor.UNKNOWN && now - lastUpperFloorCheckMs < UPPER_FLOOR_CACHE_MS) {
            return lastFloorResult;
        }
        Floor result = readFloor();
        if (result == Floor.UNKNOWN) {
            return Floor.UNKNOWN;
        }
        lastUpperFloorCheckMs = now;
        if (result != lastFloorResult) {
            log.debug("[Session] Floor changed: {}", result);
        }
        lastFloorResult = result;
        return result;
    }

    /** True only for a definite reading of that floor: UNKNOWN is neither, so it never counts as having arrived. */
    static boolean onFloor(Floor floor, boolean upper) {
        return floor == (upper ? Floor.UPPER : Floor.LOWER);
    }

    /** True (and the next action pushed back) while the floor can't be read, so a tick can wait instead of guessing. */
    protected boolean waitForFloorReading() {
        if (currentFloor() != Floor.UNKNOWN) {
            return false;
        }
        scheduleNextAdaptive(180L, 600L);
        return true;
    }

    /** Returns true if the player is on the upper floor of MLM. UNKNOWN reads as lower — see {@link Floor}. */
    protected boolean isUpperFloor() {
        return currentFloor() == Floor.UPPER;
    }

    /**
     * MLM's two levels are the <b>same plane</b> at different tile heights — the ladder's foot and head
     * are two world tiles apart — so nothing derived from {@link net.runelite.api.coords.WorldPoint} can
     * tell the floors apart.
     *
     * <p>Terrain height is the actual mechanic, not a heuristic: MLM's upper level is raised ground on
     * the same plane, so there is no plane, region or varbit to read. Matches RuneLite's own
     * MotherlodePlugin.isUpstairs (UPPER_FLOOR_HEIGHT = -490).
     */
    private static Floor readFloor() {
        return net.runelite.client.plugins.microbot.Microbot.getClientThread().runOnClientThreadOptional(() -> {
            net.runelite.api.Client client = net.runelite.client.plugins.microbot.Microbot.getClient();
            if (client == null || client.getLocalPlayer() == null) return Floor.UNKNOWN;
            LocalPoint localLoc = client.getLocalPlayer().getLocalLocation();
            if (localLoc == null) return Floor.UNKNOWN;
            int height = Perspective.getTileHeight(client, localLoc, 0);
            return height < UPPER_FLOOR_HEIGHT_THRESHOLD ? Floor.UPPER : Floor.LOWER;
        }).orElse(Floor.UNKNOWN);
    }

    /** Which level an object sits on. A given (x,y) belongs to one level or the other, never both. */
    public static Floor floorOf(Rs2TileObjectModel model) {
        if (model == null) return Floor.UNKNOWN;
        return net.runelite.client.plugins.microbot.Microbot.getClientThread().runOnClientThreadOptional(() -> {
            net.runelite.api.Client client = net.runelite.client.plugins.microbot.Microbot.getClient();
            if (client == null) return Floor.UNKNOWN;
            LocalPoint lp = model.getLocalLocation();
            if (lp == null) return Floor.UNKNOWN;
            return Perspective.getTileHeight(client, lp, 0) < UPPER_FLOOR_HEIGHT_THRESHOLD
                    ? Floor.UPPER : Floor.LOWER;
        }).orElse(Floor.UNKNOWN);
    }

    /**
     * The gate for every world-object click: is this thing on my level? Refuses rather than guess.
     * The ladder is the one deliberate exception — spanning levels is its job.
     */
    protected boolean clickOnMyFloor(Rs2TileObjectModel model, String action) {
        if (model == null) return false;
        Floor mine = currentFloor();
        Floor theirs = floorOf(model);
        if (mine == Floor.UNKNOWN || theirs == Floor.UNKNOWN) {
            log.debug("[Session] Refusing click on {}: floor unknown (me={}, target={})",
                    model.getId(), mine, theirs);
            return false;
        }
        if (mine != theirs) {
            log.warn("[Session] Refusing click on {} at {}: it is on the {} level and I am on the {} — "
                            + "clicking would walk me into a wall",
                    model.getId(), model.getWorldLocation(), theirs, mine);
            return false;
        }
        return (action == null || action.isEmpty()) ? model.click() : model.click(action);
    }

    /** Drop the shared reading — call after a ladder climb. */
    public void invalidateUpperFloorCache() {
        lastUpperFloorCheckMs = 0L;
        lastFloorResult = Floor.UNKNOWN;
    }

    /** Canvas edge inset (px) applied before measuring how much of a clickbox is on-screen. */
    private static final int SAFE_CLICK_MARGIN_PX = 6;
    /** A clickbox must have at least this fraction of its area inside the (inset) canvas before we
     *  trust a sampled click point to land on it. See {@link #hasSafeClickbox(Rs2TileObjectModel)}. */
    private static final double MIN_ONSCREEN_CLICKBOX_FRACTION = 0.5;

    /** Consecutive camera turns that failed to produce a clickable box for the current target. */
    private int clickboxTurnAttempts = 0;
    /** Turns to try before concluding the problem is zoom rather than angle. */
    private static final int MAX_CLICKBOX_TURNS = 2;
    /** How far to pull the camera back per step, and the closest we will ever leave it. */
    private static final int ZOOM_OUT_STEP_MIN = 90;
    private static final int ZOOM_OUT_STEP_MAX = 170;
    // Lower = further out. 200 was resetZoom()'s normal working zoom, not a floor — a strut that
    // would not frame at 200 could never be framed, so repairs failed when zoomed in.
    private static final int MIN_ZOOM = 110;

    /**
     * A pre-click sanity check {@link Rs2Camera#isTileOnScreen} does not give: isTileOnScreen only
     * confirms the tile roughly faces the camera (turnTo's tolerance is ~40°), so a clickbox can still
     * be mostly off-canvas right after a turn "completes". The <b>actual</b> (1,1) miss is a
     * <b>null</b> clickbox — {@code Rs2UiHelper.getObjectClickbox} returns the (1,1) sentinel rectangle
     * only when the object has no clickbox, and {@code getClickingPoint} then clicks screen point (1,1)
     * and misses. A clickbox that merely clips a canvas edge is still clickable — {@code getClickingPoint}
     * samples the real bounds — so we deliberately do NOT require full containment: that would spin the
     * camera forever on a large/near vein, strut or ladder whose box legitimately overflows the canvas
     * (turnTo centres it, making it fill more screen, never less). We require only that a solid majority
     * of the box sits on-canvas, enough that a sampled click point lands on it.
     */
    protected boolean hasSafeClickbox(Rs2TileObjectModel model) {
        if (model == null) {
            return false;
        }
        Rectangle bounds = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Shape clickbox = model.getClickbox();
            return clickbox != null ? clickbox.getBounds() : null;
        }).orElse(null);
        if (bounds == null) {
            return false; // null clickbox → the real (1,1) miss; turn and let it render
        }
        return clickboxMostlyOnScreen(bounds,
                Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight())
                && clickboxClearOfPanel(bounds, openPanelBounds());
    }

    /**
     * Screen bounds of an open deposit box panel, or {@code null} when the world is unobstructed.
     * The panel is drawn over the viewport: anything behind it is unclickable for a human, and a
     * click aimed there lands on the interface instead of the object.
     */
    protected static Rectangle openPanelBounds() {
        if (!Rs2DepositBox.isOpen()) {
            return null;
        }
        return Microbot.getClientThread()
                .runOnClientThreadOptional(Rs2DepositBox::getDepositBoxBounds)
                .orElse(null);
    }

    /**
     * Pure geometry for the panel half of {@link #hasSafeClickbox}: the box must not touch the panel
     * at all. Partial overlap is not good enough — the click point is sampled from anywhere inside the
     * box, so any covered area is a chance to click the interface instead of the object.
     */
    static boolean clickboxClearOfPanel(Rectangle bounds, Rectangle panel) {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return false;
        }
        if (panel == null || panel.isEmpty()) {
            return true;
        }
        return !bounds.intersects(panel);
    }

    /**
     * The camera fix-up every object click goes through. Returns {@code true} when {@code model} can
     * actually be clicked right now; otherwise it acts (turns the camera, or closes the deposit box)
     * and the caller should retry on a later tick.
     *
     * <p>Two distinct misses are covered:
     * <ul>
     *   <li>Off-screen / edge-clipped clickbox — {@code getClickingPoint} then clicks screen (1,1).</li>
     *   <li>Clickbox behind an open deposit box panel — the click hits the interface, not the object.
     *       That is the ladder "misclick" on the way back up to the mine, and the sack being emptied
     *       straight through the panel.</li>
     * </ul>
     */
    protected boolean ensureClickable(Rs2TileObjectModel model) {
        if (model == null) {
            return false;
        }
        if (!Rs2Camera.isTileOnScreen(model.getLocalLocation())) {
            log.debug("[Session] Target off-screen — turning camera");
            Rs2Camera.turnTo(model);
            return false;
        }
        if (hasSafeClickbox(model)) {
            clickboxTurnAttempts = 0;
            return true;
        }
        if (openPanelBounds() == null) {
            // Edge-clipped rather than covered. Turning centres the object, which makes it fill MORE
            // of the screen, not less — so if the box is clipped because the camera is zoomed too far
            // in, no amount of turning can ever satisfy the check and the session spins until it
            // stalls. That is the live "went down the ladder but never clicked the strut" case, and it
            // cleared the moment the screen was zoomed out by hand. So: turn a couple of times, and if
            // that has not worked, pull the camera back instead.
            if (++clickboxTurnAttempts <= MAX_CLICKBOX_TURNS) {
                log.debug("[Session] Clickbox clipped by canvas edge — turning camera (attempt {})",
                        clickboxTurnAttempts);
                Rs2Camera.turnTo(model);
                return false;
            }
            clickboxTurnAttempts = 0;
            zoomOutAStep();
            return false;
        }
        // Close it rather than swinging the camera: in fixed mode the panel is nearly viewport-wide
        // so swings rarely cleared it, and the retry counter reset each pass — that was the camera
        // spinning all through EMPTY_SACK. The box is reopened next pass anyway.
        log.debug("[Session] Target behind the deposit box panel — closing the box");
        Rs2DepositBox.closeDepositBox();
        return false;
    }

    /** Furthest the camera turns to bring an object out from behind the deposit box, in yaw units (2048 a circle). */
    private static final int MAX_CLEARING_TURN = 512;
    private static final int CLEARING_TURN_STEP = 32;
    /** Room left around the panel: the estimate treats the view as straight down, which it nearly is. */
    private static final int CLEARING_MARGIN_PX = 20;

    /**
     * Smallest yaw change that should move {@code target} clear of {@code panel}, or 0 if none up to a quarter
     * turn does (fixed mode, usually). Turning the camera swings everything on screen around the player.
     */
    static int clearingTurn(Point player, Rectangle target, Rectangle panel, int canvasWidth, int canvasHeight) {
        double dx = target.getCenterX() - player.getX();
        double dy = target.getCenterY() - player.getY();
        for (int turn = CLEARING_TURN_STEP; turn <= MAX_CLEARING_TURN; turn += CLEARING_TURN_STEP) {
            for (int yaw : new int[]{turn, -turn}) {
                double a = yaw * 2 * Math.PI / 2048;
                Rectangle moved = new Rectangle(target);
                moved.setLocation((int) Math.round(player.getX() + dx * Math.cos(a) - dy * Math.sin(a) - target.width / 2.0),
                        (int) Math.round(player.getY() + dx * Math.sin(a) + dy * Math.cos(a) - target.height / 2.0));
                Rectangle roomy = new Rectangle(moved);
                roomy.grow(CLEARING_MARGIN_PX, CLEARING_MARGIN_PX);
                if (clickboxClearOfPanel(roomy, panel) && clickboxMostlyOnScreen(moved, canvasWidth, canvasHeight)) {
                    return yaw;
                }
            }
        }
        return 0;
    }

    /** One turn to bring {@code model} out from behind the open deposit box. False if it isn't behind it or no turn would. */
    protected boolean turnClearOfPanel(Rs2TileObjectModel model) {
        Rectangle panel = openPanelBounds();
        if (panel == null) {
            return false;
        }
        Integer turn = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Shape clickbox = model.getClickbox();
            var client = Microbot.getClient();
            var me = client.getLocalPlayer();
            if (clickbox == null || me == null || clickboxClearOfPanel(clickbox.getBounds(), panel)) {
                return 0;
            }
            Point player = Perspective.localToCanvas(client, me.getLocalLocation(), client.getTopLevelWorldView().getPlane());
            return player == null ? 0
                    : clearingTurn(player, clickbox.getBounds(), panel, client.getCanvasWidth(), client.getCanvasHeight());
        }).orElse(0);
        if (turn == 0) {
            return false;
        }
        int targetDegrees = Math.floorMod(Rs2Camera.getYaw() + turn, 2048) * 360 / 2048;
        int gap = Rs2Camera.getAngleTo(targetDegrees);
        if (gap == 0) {
            return false;
        }
        log.debug("[Session] Target behind the deposit box panel - turning {} to clear it", turn);
        int key = gap > 0 ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT;
        Rs2Keyboard.keyHold(key);
        try {
            // Stops once it is clear, or at the planned angle (or just past it).
            Global.sleepUntil(() -> hasSafeClickbox(model)
                    || Integer.signum(Rs2Camera.getAngleTo(targetDegrees)) != Integer.signum(gap), 2_500);
        } finally {
            Rs2Keyboard.keyRelease(key);
        }
        return true;
    }

    /**
     * Pure geometry for {@link #hasSafeClickbox}: true when at least
     * {@link #MIN_ONSCREEN_CLICKBOX_FRACTION} of {@code bounds}' area lies inside the canvas (inset by
     * {@link #SAFE_CLICK_MARGIN_PX}). No client access, so it is unit-testable.
     */
    static boolean clickboxMostlyOnScreen(Rectangle bounds, int canvasWidth, int canvasHeight) {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return false;
        }
        Rectangle canvas = new Rectangle(
                SAFE_CLICK_MARGIN_PX, SAFE_CLICK_MARGIN_PX,
                Math.max(0, canvasWidth - 2 * SAFE_CLICK_MARGIN_PX),
                Math.max(0, canvasHeight - 2 * SAFE_CLICK_MARGIN_PX));
        Rectangle shown = bounds.intersection(canvas);
        if (shown.isEmpty()) {
            return false;
        }
        double full = (double) bounds.width * bounds.height;
        double onScreen = (double) shown.width * shown.height;
        return onScreen >= full * MIN_ONSCREEN_CLICKBOX_FRACTION;
    }

    /**
     * Rests the natural cursor on a random interior point of the object's clickbox. Used to line up the
     * next target without clicking it.
     */
    protected boolean hoverObject(Rs2TileObjectModel model) {
        if (model == null) {
            return false;
        }
        Point pt = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Shape cb = model.getClickbox();
            if (cb != null) {
                Rectangle b = cb.getBounds();
                if (b.width > 0 && b.height > 0) {
                    return randomPointInShape(cb, b);
                }
            }
            return model.getCanvasLocation();
        }).orElse(null);
        if (pt == null || pt.getX() < 0 || pt.getY() < 0) {
            return false;
        }
        Microbot.naturalMouse.moveTo(pt.getX(), pt.getY());
        return true;
    }

    /**
     * Random point inside {@code shape}, biased to the interior by a ~20% inset so it is neither the
     * exact centre nor an edge pixel — repeatedly hitting dead centre is a detection tell. Falls back to
     * the bounds centre when the box is too small to inset. Pure geometry, safe on any thread.
     */
    protected static Point randomPointInShape(Shape shape, Rectangle b) {
        int insetX = Math.max(1, b.width / 5);
        int insetY = Math.max(1, b.height / 5);
        int minX = b.x + insetX, maxX = b.x + b.width - insetX;
        int minY = b.y + insetY, maxY = b.y + b.height - insetY;
        if (maxX > minX && maxY > minY) {
            for (int i = 0; i < 6; i++) {
                int x = Rs2Random.between(minX, maxX);
                int y = Rs2Random.between(minY, maxY);
                if (shape.contains(x, y)) {
                    return new Point(x, y);
                }
            }
        }
        return new Point((int) b.getCenterX(), (int) b.getCenterY());
    }

    /**
     * Navigates to the target floor.
     *
     * @param targetIsUp {@code true} = upper floor, {@code false} = lower floor.
     * @return {@code true} if already on the target floor; {@code false} if a
     *         transition was initiated (caller should wait another tick).
     */
    /** Climbs toward a floor for the script (used to go back for a pile on the other level). True once there. */
    public boolean climbTowards(boolean upper) {
        return ensureFloor(upper);
    }

    protected boolean ensureFloor(boolean targetIsUp) {
        if (waitForFloorReading()) return false;
        if (isUpperFloor() == targetIsUp) return true;
        if (targetIsUp) climbUp();
        else            climbDown();
        return false;
    }

    /**
     * Returns {@code true} if an object with the given ID is present and
     * reachable in the current scene.
     */
    protected boolean isObjectInScene(int objectId) {
        return net.runelite.client.plugins.microbot.Microbot.getRs2TileObjectCache()
                .query()
                .withId(objectId)
                .nearestReachable() != null;
    }

    public void updateCachedLocalPlayerName(String name) {
        this.cachedLocalPlayerName = (name != null) ? name : "";
    }

    public void updatePersonality(net.runelite.client.plugins.microbot.irkedmlm.SessionPersonality p) {
        if (p != null) {
            this.personality = p;
        }
    }

    /**
     * Pause between the camera settling on the ladder and the click. Climbing is part of a chore, so
     * it is brisk with only an occasional short double-take — not the long AFK-style dither the
     * mining loop uses.
     */
    private int ladderHesitationMs() {
        if (!isHumanLikeEnabled()) {
            return Rs2Random.between(35, 90);
        }
        int hesitation = Rs2Random.between(120, 320);
        if (Rs2Random.between(0, 100) < 15) {
            hesitation += Rs2Random.between(220, 520); // occasional "double check"
        }
        return hesitation;
    }

    /**
     * Distance beyond which we web-walk toward the ladder instead of clicking it directly. Inside
     * this range a single click on the ladder is enough — the game walks the player there itself.
     */
    private static final int LADDER_DIRECT_CLICK_RANGE = 15;

    /**
     * Finds a ladder by <b>scene presence</b>, not collision-map reachability.
     *
     * <p>{@code nearestReachable()} asks the collision map, and the Motherlode Mine interior is a
     * known gap in it — the ladder came back null even while standing next to it, so the climb fell
     * through to {@code walkFastCanvas}, clicked the floor tile in front of the ladder, and only
     * clicked the ladder itself on the following tick. Two clicks where a player uses one. Clicking
     * a game object already makes the game walk the player to it, so scene presence is the right
     * question to ask.
     */
    private Rs2TileObjectModel findLadder(int objectId) {
        return net.runelite.client.plugins.microbot.Microbot.getRs2TileObjectCache()
                .query()
                .withId(objectId)
                .nearest();
    }

    /** True when the ladder is far enough that a web-walk beats relying on the game's own pathing. */
    private boolean shouldWalkToLadder(Rs2TileObjectModel ladder) {
        var playerLoc = Rs2Player.getWorldLocation();
        return playerLoc != null
                && playerLoc.distanceTo(ladder.getWorldLocation()) > LADDER_DIRECT_CLICK_RANGE;
    }

    protected void climbUp() {
        climb(true, net.runelite.api.gameval.ObjectID.MOTHERLODE_LADDER_BOTTOM,
                LADDER_BOTTOM_WALK, "Climb-up");
    }

    /**
     * Climbs the top ladder to reach the lower floor. Clicks the ladder object directly and lets the
     * game path to it; the walker is only used when the ladder is outside the loaded scene or far away.
     */
    protected void climbDown() {
        climb(false, net.runelite.api.gameval.ObjectID.MOTHERLODE_LADDER_TOP,
                LADDER_TOP_WALK, "Climb-down");
    }

    /**
     * The single climb implementation. Up and down were duplicated line for line, which is how the
     * re-click guard ended up subtly different between them; there is one copy now.
     *
     * @param targetIsUp the floor we want to end up on
     * @param ladderId   the ladder object for that direction
     * @param walkPoint  fallback walk target when the ladder is out of scene or far away
     * @param action     the menu action to click
     */
    private void climb(boolean targetIsUp, int ladderId,
                       net.runelite.api.coords.WorldPoint walkPoint, String action) {
        // A climb we already asked for is still happening (walking to it, or on the ladder). Never
        // issue a second click while one is in flight — this is the guard, not a timing window.
        if (ladderClimbPending(targetIsUp)) {
            scheduleNextAdaptive(200L, 400L);
            return;
        }

        if (Rs2Player.isMoving() || Rs2Player.isAnimating(1200)) {
            scheduleNext(600L);
            return;
        }

        invalidateUpperFloorCache();
        if (waitForFloorReading()) {
            return;
        }
        if (isUpperFloor() == targetIsUp) {
            log.debug("[Session] Already on target floor (upper={}) — skip climb", targetIsUp);
            clearLadderClick();
            return;
        }

        var ladder = findLadder(ladderId);

        if (ladder == null) {
            // Genuinely not in the loaded scene — get closer before we can click anything.
            net.runelite.client.plugins.microbot.util.walker.Rs2Walker.walkTo(walkPoint);
            scheduleNext(1200L);
            return;
        }
        if (shouldWalkToLadder(ladder)) {
            net.runelite.client.plugins.microbot.util.walker.Rs2Walker.walkTo(walkPoint);
            scheduleNext(1200L);
            return;
        }

        // The ladder must be genuinely clickable before we click it: off-canvas or edge-clipped and the
        // natural mouse has no target, so Microbot.doInvoke clicks the (1,1) corner and silently misses;
        // behind an open deposit box panel the click hits the interface instead. ensureClickable turns
        // or swings the camera — or closes the panel when no angle can clear it — and we retry.
        if (!ensureClickable(ladder)) {
            scheduleNext(Rs2Random.between(150, 450));
            return;
        }

        scheduleNext(ladderHesitationMs());

        // The one click that deliberately bypasses clickOnMyFloor(): a ladder is always on the floor
        // we are leaving, so gating it would make climbing impossible. Do not "fix" this to match.
        if (ladder.click(action)) {
            log.info("[Session] {} ladder", action);
            noteLadderClick();
            applyActionCooldown();
            // The outcome guard owns re-click safety from here; this is only a polling cadence.
            scheduleNext(postLadderSettleDelayMs());
        }
    }

    protected long postLadderSettleDelayMs() {
        return HumanBehaviorProfile.postLadderSettleDelayMs(isHumanLikeEnabled());
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Resets the session back to {@link State#IDLE}.
     * Sub-classes MUST call {@code super.reset()} first.
     */
    public void reset() {
        state             = State.IDLE;
        stateEnteredMs    = 0L;
        subStateEnteredMs = 0L;
        nextActionMs      = 0L;
        phaseLabel        = "idle";
        // NOTE: the ladder climb guard is deliberately NOT cleared — it is static and tracks a click
        // that has already gone to the server. Clearing it on a mid-climb session handover is exactly
        // how the second click got through.
        clickboxTurnAttempts = 0;
    }

    /**
     * Begins the session ({@link State#IDLE} → {@link State#ACTIVE}).
     */
    public void begin() {
        if (!isIdle()) {
            log.warn("[{}] begin() called while not idle (state={})",
                    getClass().getSimpleName(), state);
            return;
        }
        transition(State.ACTIVE);
    }

    // ------------------------------------------------------------------
    // Internal tick
    // ------------------------------------------------------------------

    /**
     * Called by {@link #tick()} on the executor thread.
     * Sessions that define their own parameterised tick must still
     * implement this (may be a no-op body).
     */
    protected abstract void tickInternal();

    /** Hard ceiling on one session run; 0 means none. Chores override it, mining does not. */
    protected long maxSessionMs() {
        return 0L;
    }

    /**
     * The script's watchdog only runs between sessions, so a sub-state that keeps re-arming itself
     * (a refused click, a floor read that never resolves) would otherwise spin forever. Every tick
     * entry point must call this.
     */
    protected boolean failIfOverCap() {
        long cap = maxSessionMs();
        if (cap > 0L && stateEnteredMs > 0L && System.currentTimeMillis() - stateEnteredMs > cap) {
            log.warn("[{}] Session ran past {}s — failing it so the script can recover",
                    getClass().getSimpleName(), cap / 1000);
            transition(State.FAILED);
            return true;
        }
        return false;
    }

    /** Moves the session clock forward by a pause, so a break does not count against the cap. */
    public void excludePause(long pausedMs) {
        if (pausedMs <= 0L) return;
        if (stateEnteredMs > 0L) stateEnteredMs += pausedMs;
        if (subStateEnteredMs > 0L) subStateEnteredMs += pausedMs; // sub-state stall checks too
    }

    /** Clears the shared floor cache and ladder guard; call when a new run starts. */
    public static void resetSharedState() {
        lastUpperFloorCheckMs = 0L;
        lastFloorResult = Floor.UNKNOWN;
        ladderClickMs = 0L;
        ladderProgressMs = 0L;
        ladderQuietMs = 0L;
    }

    /**
     * No-arg tick entry-point used by sessions without extra parameters.
     */
    public void tick() {
        if (!isActive()) return;
        if (failIfOverCap()) return;
        if (System.currentTimeMillis() < nextActionMs) return;
        try {
            tickInternal();
        } catch (Exception e) {
            log.error("[{}] Tick crash", getClass().getSimpleName(), e);
            transition(State.FAILED);
        }
    }

    // ------------------------------------------------------------------
    // Timing helpers
    // ------------------------------------------------------------------

    /** Prevents any action from firing for at least {@code delayMs}. */
    protected void scheduleNext(long delayMs) {
        nextActionMs = System.currentTimeMillis() + delayMs;
    }


    /**
     * Milliseconds elapsed since the current sub-state was entered.
     * Sub-classes must keep {@link #subStateEnteredMs} up-to-date.
     */
    protected long subElapsed() {
        return System.currentTimeMillis() - subStateEnteredMs;
    }

    /**
     * Returns {@code true} when the sub-state has been active for longer
     * than {@code thresholdMs} without progressing — useful for stall detection.
     */
    protected boolean isStalled(long thresholdMs) {
        return subElapsed() > thresholdMs;
    }

    /**
     * Returns {@code true} if at least {@code throttleMs} milliseconds have
     * elapsed since {@code subStateEnteredMs}. Used to gate walk/click actions
     * so they don't fire every 600 ms tick.
     */
    protected boolean mayAct(long throttleMs) {
        return subElapsed() >= throttleMs || System.currentTimeMillis() >= nextActionMs;
    }

    // ------------------------------------------------------------------
    // Antiban helpers
    // ------------------------------------------------------------------

    /**
     * Applies a post-action cooldown via Rs2Antiban.
     * <p>
     * Wrapped in try/catch: {@code PlayStyle.getRandomTickInterval} throws
     * {@code IllegalArgumentException} when {@code minTicks >= maxTicks}, which
     * can happen if {@code usePlayStyle} is true but the play-style was not
     * initialised with valid bounds.  Rather than crashing the session we log a
     * warning and skip the cooldown for that tick only.
     */
    protected void applyActionCooldown() {
        if (!isHumanLikeEnabled()) {
            return;
        }
        try {
            Rs2Antiban.actionCooldown();
        } catch (IllegalArgumentException e) {
            log.warn("[{}] applyActionCooldown skipped — invalid antiban bounds: {}",
                    getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * Sets {@link net.runelite.client.plugins.microbot.Microbot#status} to the
     * given label so the RuneLite overlay picks it up.
     */
    protected void updateStatus(String label) {
        phaseLabel = label;
        net.runelite.client.plugins.microbot.Microbot.status = label;
    }

    /**
     * Converts a SCREAMING_SNAKE_CASE enum name to Title Case for display.
     * e.g. {@code "WALK_TO_SACK"} → {@code "Walk To Sack"}.
     */
    protected String formatEnum(String name) {
        if (name == null || name.isEmpty()) return name;
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1).toLowerCase());
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------
    // State transition
    // ------------------------------------------------------------------

    /** Transitions to {@code next}, recording the entry timestamp. */
    protected void transition(State next) {
        log.debug("[{}] {} -> {}", getClass().getSimpleName(), state, next);
        state          = next;
        stateEnteredMs = System.currentTimeMillis();
    }

}
