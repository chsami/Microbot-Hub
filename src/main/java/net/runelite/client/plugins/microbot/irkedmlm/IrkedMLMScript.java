package net.runelite.client.plugins.microbot.irkedmlm;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.accountselector.AutoLoginPlugin;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.breakhandler.BreakPreparation;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2Plugin;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2State;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.LogoutRequested;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.irkedmlm.enums.DepositMethod;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MLMSackSize;
import net.runelite.client.plugins.microbot.irkedmlm.enums.MLMStatus;
import net.runelite.client.plugins.microbot.irkedmlm.enums.Pickaxe;
import net.runelite.client.plugins.microbot.irkedmlm.session.HopperSession;
import net.runelite.client.plugins.microbot.irkedmlm.session.MiningSession;
import net.runelite.client.plugins.microbot.irkedmlm.session.RepairSession;
import net.runelite.client.plugins.microbot.irkedmlm.session.SackSession;
import net.runelite.client.plugins.microbot.irkedmlm.session.Session;
import net.runelite.client.plugins.microbot.irkedmlm.session.SessionSnapshot;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Gembag;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.settings.Rs2Settings;
import net.runelite.client.plugins.microbot.util.world.Rs2WorldUtil;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

@Slf4j
@Singleton
public class IrkedMLMScript extends Script {

    // --- Sack ---
    private static final int SACK_LARGE_SIZE = 189;
    private static final int SACK_SIZE = 108;
    /** Varbit ID for the amount of ore currently in the sack. */
    /** Sack fill level. Sourced from gameval so there is no magic number to drift. */
    public static final int SACK_COUNT_VARBIT = VarbitID.MOTHERLODE_SACK_TRANSMIT;

    // --- Recovery ---
    private static final int MAX_RECOVERY_ATTEMPTS = 5;

    // --- Special attack ---
    /** OSRS player 300 — special attack energy, scaled 0–1000 (100% = 1000). */
    private static final int VARP_SPEC_ENERGY = 300;
    private static final int SPEC_ENERGY_CRYSTAL = 1000;
    private static final int SPEC_ENERGY_DRAGON_INFERNAL = 500;
    private static final int SPEC_ENERGY_FULL = 1000;
    private static final long SPEC_COOLDOWN_MS = 60_000L;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    @Getter private MLMStatus     status     = MLMStatus.IDLE;
    @Getter private MLMMiningSpot miningSpot = null;

    /** Atomic reference so overlay always sees latest snapshot without stale refs. */
    private final AtomicReference<SessionSnapshot> snapshotRef = new AtomicReference<>(SessionSnapshot.empty());


    private int tickBrokenStrutCount = 0;

    /** Number of waterwheels currently running (MOTHERLODE_WHEEL_FIXED in scene). Pay-dirt only
     *  freezes when this reaches 0 (both wheels broken); while ≥1 wheel turns, water still flows. */
    private int tickRunningWheelCount = 0;

    /** When the water first froze (both wheels broken; 0 = at least one wheel running). Used to stop
     *  indefinitely deferring repair to a player standing near the wheel who turns out to be idle. */
    private long brokenStrutsObservedSinceMs = 0L;


    /**
     * How close we must be to the recorded drop tile before looting. Mirrors the tolerance
     * HopperSession resolves its hopper with: anchor on the expected point, act only within a tile or
     * two of it, never sweep a wide radius.
     */
    private static final int PAYDIRT_PICKUP_TOLERANCE = 3;
    /** Same-floor distance worth walking back for our own pile: the drop is at the vein, the sack trip ends at the box. */
    private static final int PAYDIRT_RECLAIM_RANGE = 40;

    /** Pay-dirt dropped to make room for emptying the sack, so it can be reclaimed afterwards. */
    private final DroppedPayDirt droppedPayDirt = new DroppedPayDirt();

    /** Shared "is somebody else already on the wheel, and how long do we leave them to it" policy. */
    private final MlmRepairEtiquette repairEtiquette = new MlmRepairEtiquette();
    /** After this long with struts still broken, a "nearby" player clearly isn't repairing — we do it. */
    private static final long REPAIR_DEFER_TIMEOUT_MS = 25_000L;

    /**
     * Cached local player name to avoid background thread violations.
     */
    private String localPlayerName = "";

    /**
     * Set to true when a chat message indicates the sack is full or will be full.
     * Reset when the sack is confirmed empty.
     */

    // -------------------------------------------------------------------------
    // Sessions
    // -------------------------------------------------------------------------

    private final IrkedMLMPlugin plugin;
    private final IrkedMLMConfig config;

    private final MiningSession  miningSession;
    private final HopperSession  hopperSession;
    private final SackSession    sackSession;
    private final RepairSession  repairSession;

    /** Per-login behavioural identity (reaction speed, patience, AFK/hesitation/mouse habits, etc.).
     *  Rolled once in {@link #initialise()} and pushed to every session so the whole run behaves like
     *  one consistent, session-unique player instead of independent per-decision coin-flips. */
    private SessionPersonality personality = SessionPersonality.neutral();

    // -------------------------------------------------------------------------
    // Timing / counters
    // -------------------------------------------------------------------------

    private final AtomicLong    globalLastXpTime = new AtomicLong(0L);
    private final AtomicInteger recoveryAttempts = new AtomicInteger(0);
    /** Hard resets since anything last worked (XP, pay-dirt accepted, the sack emptied); past the limit the run stops. */
    private int hardResets = 0;
    /** Last tick spent logged out or paused: time away is not time stuck. */
    private long lastHeldMs = 0L;
    /** No progress for this long in one working status means recovery. */
    private static final long STUCK_MS = 240_000L;
    /** Active time since anything last worked, however much recovery was tried in it. */
    private long noProgressMs = 0L;
    private long lastActiveTickMs = 0L; // 0 while logged out or paused
    private int lastPayDirtCount = -1;

    private MLMStatus lastLoggedStatus = null;
    private long      recoveryWaitUntil = 0L;
    private long      statusEnteredMs   = System.currentTimeMillis();

    // Cached values to reduce client thread calls (prevents TimeoutExceptions and breakhandler popups at startup)

    /** Tick counter for the execute loop. Used to force fresh sack reads on the first few ticks
     *  after start so that "start plugin with sack already full" reliably detects via varbit
     *  instead of a potentially stale first client read causing it to go mine. */
    private int scriptTicks = 0;

    /** One-time-per-run gate: becomes true once we've authoritatively read the sack at startup. */

    /** Guard against concurrent execution of executeTask from multiple scheduled threads
     *  (which can happen if run() is invoked multiple times without proper shutdown,
     *  leading to duplicate clicks, ladder spamming, etc.). */
    private final java.util.concurrent.atomic.AtomicBoolean executing = new java.util.concurrent.atomic.AtomicBoolean(false);

    /** Used to early-exit executeTask during/after shutdown to avoid races with Rs2Antiban reset etc. */
    private volatile boolean running = false;

    /** A new run sets itself up on its first tick, after a tick left over from the last run has ended. */
    private volatile boolean initPending = false;
    /** Set by shutdown(); done as soon as no tick is running. Only touched with {@link #executing} held. */
    private final java.util.concurrent.atomic.AtomicBoolean tearDownPending = new java.util.concurrent.atomic.AtomicBoolean(false);
    private boolean initialised = false;

    // XP read cache (same treatment as varbit/sack/floor to cut client thread pressure)

    /** Timestamp of the last hopper/sack begin() (ladder throttle). */
    private long lastLadderInteractionMs = 0L;

    private long lastSpecTime = 0L;
    /** Random variance added to spec cooldown so it's not robotic. */
    private long specCooldownVarianceMs = 0L;
    /** When the spec bar was first seen full this cycle (0 = not full / already consumed). */
    private long specBarFullSinceMs = 0L;
    /** Randomized wait after the bar fills before we actually spec, so it's never instant-on-full. */
    private long specReadinessDelayMs = 0L;
    /** After using spec, wait for animation before MiningSession resumes the vein click. */
    private boolean needPostSpecMiningResume = false;

    // -------------------------------------------------------------------------
    // Session tracking
    // -------------------------------------------------------------------------

    private long startTimeMs                   = 0L;

    /** Cross-session persisted totals + intended loadout. Loaded (as this session's immutable baseline)
     *  in {@link #initialise()}, recomputed from that baseline + session deltas in {@link #saveStats()}. */
    private static final String STATS_KEY = "stats.mlm";
    private MlmStats stats = new MlmStats();
    private int sessionSacksEmptied = 0;
    /** Golden nuggets gained this session: the running sum of every rise in the carried count. */
    @Getter
    /** Last observed carried nugget count; -1 until the first reading establishes the baseline. */

    /** Initial pay-dirt count captured before hopper reset, for post-deposit delta calc. */
    private int lastHopperDepositInitial = 0;

    /** Deposit-trip retry count. Owned by the script rather than HopperSession because each retry is a
     *  reset()+begin() pair and begin() zeroes the session's own counter, so a session-level count could
     *  never reach the RECOVERY backstop. Incremented by the audit on a rejected/partial deposit; reset
     *  by setStatus() on any transition out of DEPOSIT_HOPPER. */
    private int hopperDepositRetries = 0;

    /** Owns the post-deposit sack-count projection (compensates for the laggy sack varbit) and the
     *  last-deposit timestamp. See {@link SackState}. */
    private final SackState sackState = new SackState();

    /** When the sack count first became unknown, for the escalation timer; 0 while known. */
    private long sackUnknownSinceMs = 0L;
    /**
     * How long to tolerate an unknown sack while in the mine before going to look. Emptying is the
     * one sack-dependent action that is safe without knowing the count: it is correct whether the
     * sack is empty (a cheap no-op trip) or full.
     */
    private static final long SACK_UNKNOWN_ESCALATE_MS = 15_000L;

    /** Previous tick's wall clock, for the stall watchdog; 0 before the first tick. */
    /** When the last hopper deposit was issued, for the recovery re-click window; 0 if never. */
    private long lastHopperDepositMs = 0L;
    private long lastTickMs = 0L;
    /**
     * A tick gap this long means we lost continuity without a game-state event (frozen or suspended
     * client). 30s because scheduleWithFixedDelay serialises ticks and one runOnClientThreadOptional
     * can legitimately block for its own 10s timeout. A false trip is cheap — re-observed next tick.
     */
    private static final long TICK_GAP_INVALIDATE_MS = 30_000L;

    /**
     * Session counting — XP, nuggets, per-ore totals and their GE value. Owns its own baselines and
     * price cache; see {@link MlmSessionStats} for the counting rules.
     */
    private final MlmSessionStats sessionStats = new MlmSessionStats(
            Rs2Inventory::itemQuantity,
            // ItemManager.getItemPrice() returns long as of RuneLite 1.13.0 / Microbot 2.6.25
            // (upstream 376527a "api: update 241"). Against the old int signature this is a
            // NoSuchMethodError at runtime, which runOnClientThreadOptional swallows into an empty
            // Optional -> every price 0 -> GP/hr pinned at 0. Narrowing is safe: MLM only prices
            // ores and uncut gems, all four figures.
            itemId -> Microbot.getClientThread()
                    .runOnClientThreadOptional(() -> Microbot.getItemManager().getItemPrice(itemId))
                    .orElse(0L).intValue());

    // -------------------------------------------------------------------------
    // Mining spot shuffle timer
    // -------------------------------------------------------------------------

    private long lastSpotShuffleMs = 0L;
    private static final long SPOT_SHUFFLE_INTERVAL_MS = 300_000L; // 5 minutes
    private WorldPoint currentAnchor = null;

    // -------------------------------------------------------------------------
    // Gem bag tracking
    // -------------------------------------------------------------------------
    private long lastGemBagCheckMs = 0L;
    private static final long GEMBAG_RECHECK_COOLDOWN_MS = 8000L;
    /** One-shot per run: have we confirmed a gem bag is in the inventory (or the feature is off)? */
    private boolean gemBagEnsured = false;
    private boolean preflightDone = false;
    private long preflightStartedMs = 0L;
    private long pausedSinceMs = 0L;
    private boolean dropInProgress = false;
    // Where the drop under way started: the claim on the pile, even if we are logged out before it ends.
    private WorldPoint dropTile = null;
    private boolean dropOnUpperFloor = false;
    private long lastDropMs = 0L; // last pay-dirt dropped, or when the drop was asked for
    /** Longest wait to be standing still on the drop tile before giving up on the drop. */
    private static final long DROP_SETTLE_MS = 10_000L;
    /** A pile already waiting this close gets the rest of the load, so there is still one claim. */
    private static final int DROP_SAME_PILE_RANGE = 10;
    /** Drop clicks before giving up on a load, with room for slots clicked again before they emptied. */
    private static final int MAX_DROP_CLICKS = 80;
    /** The hopper refused a load because no wheel turns, so another trip before the repair is pointless. */
    private boolean hopperRejectedFrozen = false;
    private boolean recoveryResumePending = false;
    private int droppedThisRun = 0;
    private boolean pickaxeUpgradeChecked = false;
    private boolean inventorySetupLoaded = false;
    private boolean miningLevelChecked = false;
    private int consecutiveRepairFailures = 0;
    /** After repeated failed repairs, no new try before this; 0 when not backing off. */
    private long repairBackoffUntilMs = 0L;
    private int repairBackoffRounds = 0;
    private static final long PREFLIGHT_GIVE_UP_MS = 180_000L;
    /** Extra time after that for a bank that will not close; nothing is clicked in the world with it open. */
    private static final long BANK_CLOSE_GIVE_UP_MS = 60_000L;
    private static final String STUCK_MESSAGE =
            "MLM: stuck, and starting over did not help. Check where the character is standing, then start again.";
    private static final int MAX_CONSECUTIVE_REPAIR_FAILURES = 3;
    private static final int UPPER_FLOOR_MINING_LEVEL = 57;
    private boolean preflightBankVisitDone = false;

    // Breaks: both break handlers ask through this before they pause or log out a script.
    private BreakPreparation.Handle breakPrep;
    // The player's own antiban settings before start, every one MLM can change, put back at stop.
    private java.util.Map<java.lang.reflect.Field, Object> savedAntiban;
    /** Live state of the client's antiban, not the player's choice: never saved or put back. */
    private static final java.util.Set<String> ANTIBAN_RUNTIME = java.util.Set.of(
            "actionCooldownActive", "microBreakActive", "overwriteScriptSettings");
    // Logging back in after a disconnect or the 6-hour logout.
    private long loggedOutSeenMs = 0L;   // 0 while in game
    private long nextLoginAt = 0L;
    private int loginAttempts = 0;
    private boolean playerLoggedOut = false;
    private boolean everLoggedIn = false;
    private long lastHumanInputMs = 0L;
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long PLAYER_LOGOUT_WINDOW_MS = 5_000L;
    private static final int LOGOUT_GROUP = InterfaceID.Logout.LOGOUT >>> 16;   // the red logout button
    private static final int LOGOUT_BUTTON_CHILD = InterfaceID.Logout.LOGOUT & 0xFFFF;
    // Session history: earlier sessions for the panel, and why this one ended.
    private static final String HISTORY_KEY = "history.mlm";
    private MlmHistory history = new MlmHistory();
    @Getter
    private volatile String historyLine;
    private volatile String stopReason;
    private DepositMethod effectiveDepositMethod = DepositMethod.ITEMS;

    // -------------------------------------------------------------------------

    @Inject
    public IrkedMLMScript(IrkedMLMPlugin plugin, IrkedMLMConfig config) {
        this.plugin = plugin;
        this.config = config;

        var tileCache = Microbot.getRs2TileObjectCache();
        this.miningSession = new MiningSession(tileCache, config);
        this.hopperSession = new HopperSession(tileCache, config);
        this.sackSession   = new SackSession(tileCache, config);
        this.repairSession = new RepairSession(tileCache, config, repairEtiquette, droppedPayDirt);
    }

    // =========================================================================
    // Debug logging wrapper
    // =========================================================================

    // Written when Log detail is Detailed; the plugin sets this package's logger level from it.
    private void debug(String msg) {
        log.debug(msg);
    }

    private void debug(String msg, Object... args) {
        log.debug(msg, args);
    }

    private void humanPause(int minMs, int maxMs) {
        humanPause(minMs, maxMs, false);
    }

    private void humanPause(int minMs, int maxMs, boolean urgent) {
        if (!isHumanLikeEnabled()) {
            return;
        }
        int delay = getHumanizedDelay(minMs, maxMs, urgent);
        debug("[MLM] humanPause: {}ms (min={}, max={}, urgent={})", delay, minMs, maxMs, urgent);
        try {
            Rs2Antiban.actionCooldown();
        } catch (IllegalArgumentException e) {
            debug("[MLM] humanPause antiban cooldown skipped: {}", e.getMessage());
        }
        sleep(delay);
    }

    private int getHumanizedDelay(int minMs, int maxMs, boolean urgent) {
        return HumanBehaviorProfile.pauseDelayMs(isHumanLikeEnabled(), minMs, maxMs, urgent);
    }

    private boolean isHumanLikeEnabled() {
        return config != null && config.enableHumanLikeBehavior();
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public boolean run() {
        // Prevent duplicate scheduled tasks (which cause concurrent executeTask from multiple threads,
        // resulting in duplicate clicks, repeated ladder climbs, vein spam, etc.).
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            log.warn("[MLM] run() called while executor already active — cancelling previous task to avoid concurrency");
            mainScheduledFuture.cancel(true);
        }

        log.info("Starting Motherlode Mine script v{} (session-oriented)", IrkedMLMPlugin.version);
        running = true;
        initPending = true;
        long tickIntervalMs = isHumanLikeEnabled() ? 600L : 250L;
        log.info("[MLM] Main executor started — ticking every {}ms (humanLike={})",
                tickIntervalMs, isHumanLikeEnabled());
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                this::executeTask, 0, tickIntervalMs, TimeUnit.MILLISECONDS);
        return true;
    }

    private void initialise() {
        log.debug("Initialising MLM runtime state");
        // Saved once per run: a second run() without a stop would otherwise save our own settings.
        if (savedAntiban == null) savedAntiban = saveAntibanSettings();
        initialised = true;
        resetAllSessions();
        configureAntibanSettings();
        // Any earlier script may have left the lock on, and then no break would ever start.
        BreakHandlerScript.setLockState(false);
        if (breakPrep != null) breakPrep.close();
        breakPrep = BreakPreparation.register("irked-mlm");
        loggedOutSeenMs = 0L;
        nextLoginAt = 0L;
        loginAttempts = 0;
        playerLoggedOut = false;
        everLoggedIn = false;
        lastHumanInputMs = 0L;
        stopReason = null;

        // Roll one behavioural identity for this login and push it to every session. From here on all
        // "human" odds/timings are a stable function of these traits, so the run has a consistent
        // character and differs from the next login. See SessionPersonality.
        personality = SessionPersonality.roll();
        log.info("MLM {}", personality);
        miningSession.updatePersonality(personality);
        hopperSession.updatePersonality(personality);
        sackSession.updatePersonality(personality);
        repairSession.updatePersonality(personality);
        sackSession.setGemBagSuppliers(
                () -> { Rs2ItemModel b = firstGemBagItem(); return b != null && b.getSlot() == GEM_BAG_SLOT; },
                this::isGemBagInSlotLocked);
        sackSession.setDepositMethodSupplier(() -> effectiveDepositMethod);

        gemBagEnsured                = false; // re-verify each run (singleton survives stop/start)
        preflightDone                = false; // one-shot startup gate re-runs each fresh start
        preflightBankVisitDone       = false; // Phase A (single bank session) not yet done this run
        cameraFramed                 = false; // frame the camera once per run
        cameraFrameDueMs             = 0L;
        effectiveDepositMethod       = config.depositMethod();
        startTimeMs                  = System.currentTimeMillis();
        // Re-baseline every run(): the script is a @Singleton whose instance survives stop/start, so a
        // stale baseline would inflate this run's rates. The XP baseline is taken from the first real
        // reading in the tick loop rather than here, so it works when started at the login screen too.
        sessionStats.reset();
        sessionSacksEmptied          = 0;
        try {
            MlmStats loaded = (Microbot.getConfigManager() != null)
                    ? Microbot.getConfigManager().getConfiguration(IrkedMLMConfig.configGroup, STATS_KEY, MlmStats.class)
                    : null;
            stats = (loaded != null) ? loaded : new MlmStats();
        } catch (Exception e) {
            stats = new MlmStats();
        }
        log.info("[MLM] Loaded persisted stats: ores={} nuggets={} xp={} sacks={}",
                stats.oresMined, stats.nuggets, stats.xpGained, stats.sacksEmptied);
        try {
            MlmHistory loadedHistory = (Microbot.getConfigManager() != null)
                    ? Microbot.getConfigManager().getConfiguration(IrkedMLMConfig.configGroup, HISTORY_KEY, MlmHistory.class)
                    : null;
            history = (loadedHistory != null && loadedHistory.sessions != null) ? loadedHistory : new MlmHistory();
        } catch (Exception e) {
            history = new MlmHistory();
        }
        historyLine = MlmHistory.line(history.before(startTimeMs));
        miningSpot                   = null;
        lastLoggedStatus             = null;
        sackState.invalidate("script initialise");
        sackState.setCapacityHint(config.sackSize() == MLMSackSize.UPGRADED
                ? SackState.SACK_LARGE_SIZE : SackState.SACK_SIZE);
        lastHopperDepositInitial     = 0;
        hopperDepositRetries         = 0;
        // Clear leftover sack state from a prior run (singleton survives stop/start).
        brokenStrutsObservedSinceMs  = 0L;
        droppedPayDirt.clear();
        repairEtiquette.reset();
        noteProgress();
        lastActiveTickMs             = 0L;
        lastPayDirtCount             = -1;
        repairBackoffRounds          = 0;
        repairBackoffUntilMs         = 0L;
        hopperRejectedFrozen         = false;
        recoveryWaitUntil            = 0L;
        statusEnteredMs              = System.currentTimeMillis();
        lastLadderInteractionMs      = 0L;
        lastSpotShuffleMs            = System.currentTimeMillis();
        currentAnchor                = null;
        lastSpecTime                 = 0L;
        specCooldownVarianceMs       = Rs2Random.between(-5000, 10000);
        specBarFullSinceMs           = 0L;
        specReadinessDelayMs         = 0L;
        needPostSpecMiningResume     = false;

        if (Microbot.isLoggedIn()) {
            localPlayerName = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                var lp = Microbot.getClient().getLocalPlayer();
                return (lp != null) ? lp.getName() : "";
            }).orElse("");
        } else {
            localPlayerName = "";
        }
        miningSession.updateCachedLocalPlayerName(localPlayerName);
        repairSession.updateCachedLocalPlayerName(localPlayerName);

        sessionStats.rebaselineInventory();
        // The gem bag check clicks items, so it waits for the first executor tick instead of running here
        // on the plugin start-up thread.
        globalLastXpTime.set(0L);
        Session.resetSharedState();
        sackSession.clearInventoryClogged();
        hopperSession.resetForNewRun();
        preflightStartedMs = 0L;
        pausedSinceMs = 0L;
        dropInProgress = false;
        dropTile = null;
        droppedThisRun = 0;
        recoveryResumePending = false;
        miningLevelChecked = false;
        pickaxeUpgradeChecked = false;
        inventorySetupLoaded = false;
        consecutiveRepairFailures = 0;

        if (miningSpot == null) {
            selectMiningSpotFromConfig();
        }

        status = MLMStatus.IDLE;
        lastLoggedStatus = MLMStatus.IDLE; // prevent duplicate null->IDLE log spam at startup

        updateSnapshot();

        log.info("MLM Initialised — startXp={}, sackSize={}/{}, spot={}",
                sessionStats.getStartXp(), sackState.countOrUnknown(), sackState.capacity(), miningSpot);
    }

    private void configureAntibanSettings() {
        // "Overwrite script settings" on the Antiban panel means the player's settings win: leave them.
        if (Rs2AntibanSettings.overwriteScriptSettings) {
            Rs2Antiban.setActivity(Activity.GENERAL_MINING);
            log.info("MLM Antiban left as you set it (Overwrite script settings is on)");
            return;
        }
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyMiningSetup();

        boolean human = isHumanLikeEnabled();
        Rs2AntibanSettings.usePlayStyle = human;

        Rs2Antiban.setActivity(Activity.GENERAL_MINING);
        Rs2Antiban.setActivityIntensity(human ? ActivityIntensity.MODERATE : ActivityIntensity.HIGH);

        Rs2AntibanSettings.dynamicIntensity = human;
        Rs2AntibanSettings.dynamicActivity = human;

        Rs2AntibanSettings.naturalMouse = human;
        Rs2AntibanSettings.simulateMistakes = human;
        Rs2AntibanSettings.simulateFatigue = human;

        // Off-screen mouse is handled by MiningSession only (once, while actively mining a vein).
        // The global antiban off-screen fires on ANY action cooldown (deposits, walking, sack
        // emptying), which is not what we want — keep it disabled so off-screen == mining.
        Rs2AntibanSettings.moveMouseOffScreen = false;
        Rs2AntibanSettings.moveMouseOffScreenChance = 0.0;

        Rs2AntibanSettings.moveMouseRandomly = human;
        Rs2AntibanSettings.moveMouseRandomlyChance = human ? 0.15 : 0.0;

        Rs2AntibanSettings.actionCooldownChance = human ? 0.15 : 0.0;

        Rs2AntibanSettings.behavioralVariability = human;
        Rs2AntibanSettings.nonLinearIntervals = human;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.contextualVariability = human;

        log.info("MLM Antiban configured — humanLike={}, actionCooldownChance={}",
                human, Rs2AntibanSettings.actionCooldownChance);
    }

    // =========================================================================
    // Main loop
    // =========================================================================

    private void executeTask() {
        if (!executing.compareAndSet(false, true)) {
            debug("Execution skipped: already running (concurrent tick avoided to prevent duplicate clicks)");
            return;
        }
        try {
            if (tearDownPending.getAndSet(false)) tearDown();
            if (!running) return;
            if (initPending) {
                initialise();
                initPending = false;
            }
            // Read before the client's gate below: it holds every script while the player has the mouse.
            if (InputArbiter.isHuman()) lastHumanInputMs = System.currentTimeMillis();
            if (!Microbot.isLoggedIn()) {
                debug("Execution paused: not logged in");
                lastHeldMs = System.currentTimeMillis();
                lastActiveTickMs = 0L;
                preflightStartedMs = 0L; // time logged out is not time at the bank
                if (dropInProgress) finishPayDirtDrop(); // keep the claim on what was already dropped
                resetAllSessions();
                updateSnapshot();
                BreakHandlerScript.setLockState(false);
                answerBreakRequest(); // logged out is always a safe place for a break
                if (!Microbot.pauseAllScripts.get() && Microbot.getClient() != null
                        && Microbot.getClient().getGameState() == GameState.LOGIN_SCREEN) {
                    logBackInWhenDue();
                }
                return;
            }

            scriptTicks++;

            // Stall watchdog: catches a frozen or suspended client that fires no game-state event.
            long nowTick = System.currentTimeMillis();
            if (lastTickMs != 0L && nowTick - lastTickMs > TICK_GAP_INVALIDATE_MS) {
                sackState.invalidate("executor stalled for " + (nowTick - lastTickMs) + "ms");
            }
            lastTickMs = nowTick;

            // Observe the sack before any decision. Mining stays allowed while it is unknown — filling
            // your own inventory is harmless, and the sack only matters at the hopper — but depositing
            // blind is what overfills a sack and strands pay-dirt, so dispatchByStatus() gates that.
            observeSack();
            // While emptying, the count must be live every tick so SackSession can watch it fall. The
            // old varbit cache needed a forceRead flag for exactly this; re-observing is the same thing
            // without a cache to reason about. This is the "stayed full at 189" infinite-loop guard.
            if (status == MLMStatus.EMPTY_SACK || !sackSession.isIdle() || sackState.wasNearlyFullWarned()) {
                observeSack();
            }
            if (!sackState.isKnown()) {
                if (sackUnknownSinceMs == 0L) {
                    sackUnknownSinceMs = nowTick;
                } else if (sackUnknownTooLong()
                        && status != MLMStatus.EMPTY_SACK
                        && Boolean.TRUE.equals(Microbot.getClientThread()
                                .runOnClientThreadOptional(IrkedMLMMapConstants::inMine).orElse(Boolean.FALSE))) {
                    // Deliberate override of the deposit gate, and the only one: emptying is the single
                    // sack-dependent action that is safe without knowing the count — correct whether the
                    // sack is empty (a cheap no-op trip) or full. If the varbit never resolves, go look.
                    log.warn("[MLM] Sack count still unknown after {}ms in the mine — going to look",
                            nowTick - sackUnknownSinceMs);
                    setStatus(MLMStatus.EMPTY_SACK);
                }
            } else {
                sackUnknownSinceMs = 0L;
            }

            tickBrokenStrutCount = queryBrokenStrutCount();  // early for accurate logging
            tickRunningWheelCount = queryRunningWheelCount();
            // Pay-dirt only freezes when BOTH wheels stop (no running wheel). While one wheel turns,
            // water still flows and the hopper keeps processing — so we only "need repair", and only
            // start the deferral timer, when no wheel is running.
            if (tickBrokenStrutCount > 0 && tickRunningWheelCount == 0) {
                if (brokenStrutsObservedSinceMs == 0L) {
                    brokenStrutsObservedSinceMs = System.currentTimeMillis();
                }
            } else {
                brokenStrutsObservedSinceMs = 0L;  // a wheel is running (we/someone fixed one) — reset timer
                repairEtiquette.clearYield();      // the next breakdown gets its own wait
                repairBackoffUntilMs = 0L;
                hopperRejectedFrozen = false;
                if (tickRunningWheelCount > 0) { // not just a scene that has not loaded its wheels yet
                    repairBackoffRounds = 0;
                    consecutiveRepairFailures = 0;
                }
            }

            debug("[MLM] executeTask tick | status={} | miningSpot={} | sack={}/{} | paydirt={} | brokenStruts={} | runningWheels={}",
                    status, miningSpot, sackState.countOrUnknown(), sackState.capacity(), payDirtCount(), tickBrokenStrutCount, tickRunningWheelCount);

            // Pay-dirt on the floor or a trip under way is finished before any break: logging out
            // then loses the pile, or leaves a hammer or a half-done deposit behind.
            boolean breakSafe = MlmRunControl.safeForBreak(dropInProgress, droppedPayDirt.isPending(nowTick),
                    hopperSession.isIdle() && sackSession.isIdle() && repairSession.isIdle());
            BreakHandlerScript.setLockState(!breakSafe);

            if (!super.run()) {
                // A pause (break, blocking event, input takeover) keeps the sessions: wiping them lost the
                // deposit audit and the ore snapshot mid-trip. Logout still resets, above.
                lastHeldMs = nowTick;
                lastActiveTickMs = 0L;
                if (pausedSinceMs == 0L) pausedSinceMs = nowTick;
                if (breakSafe) answerBreakRequest();
                debug("Execution paused: script not runnable");
                return;
            }
            loggedOutSeenMs = 0L;
            loginAttempts = 0;
            playerLoggedOut = false;
            everLoggedIn = true;
            if (pausedSinceMs != 0L) {
                long pausedMs = nowTick - pausedSinceMs;
                pausedSinceMs = 0L;
                if (preflightStartedMs != 0L) preflightStartedMs += pausedMs;
                miningSession.excludePause(pausedMs);
                hopperSession.excludePause(pausedMs);
                sackSession.excludePause(pausedMs);
                repairSession.excludePause(pausedMs);
            }

            if (localPlayerName.isEmpty()) {
                localPlayerName = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                    var lp = Microbot.getClient().getLocalPlayer();
                    return lp != null ? lp.getName() : "";
                }).orElse("");
                debug("[MLM] Refreshed localPlayerName for anti-crash/repair checks: {}", localPlayerName);
            }
            miningSession.updateCachedLocalPlayerName(localPlayerName);
            repairSession.updateCachedLocalPlayerName(localPlayerName);

            // "I can't reach that!" sets a client-wide flag, and the client's next object click would walk
            // toward the object on its own and, after a few tries, pause every script. We handle it here
            // instead: clear the flag before our clicks and steer the mining choice away from that vein.
            if (Microbot.cantReachTarget) {
                Microbot.cantReachTarget = false;
                log.info("[MLM] Game said a target can't be reached — choosing around it");
                miningSession.onTargetUnreachable();
            }

            // A break handler wants to start a break: once nothing is left half done, say so and start
            // nothing new until it pauses us.
            BreakPreparation.Handle prep = breakPrep;
            if (prep != null && prep.isRequested() && breakSafe) {
                prep.ready();
                debug("[MLM] Break requested — safe, holding until it starts");
                lastActiveTickMs = 0L;
                updateSnapshot();
                return;
            }

            // Whatever recovery tries, a run where nothing works for this long is over. Waiting for someone to
            // fix the wheel, the bank visit at the start and an antiban break are not being stuck.
            noProgressMs = MlmRunControl.addNoProgress(noProgressMs, lastActiveTickMs, nowTick, preflightDone
                    && status != MLMStatus.WAITING_FOR_REPAIR && !Rs2AntibanSettings.microBreakActive);
            lastActiveTickMs = nowTick;
            if (MlmRunControl.noProgressTooLong(noProgressMs)) {
                log.warn("[MLM] Nothing mined, deposited or emptied for {} minutes — stopping", noProgressMs / 60_000L);
                finish(STUCK_MESSAGE, true);
                return;
            }

            // Finish dropping a load before anything else acts on the inventory.
            if (dropInProgress) {
                dropAllPayDirt();
                return;
            }

            // Handle post-spec mining resumption first
            if (needPostSpecMiningResume) {
                handlePostSpecMining();
                return;
            }

            sessionStats.update();

            // Mining XP, throttled to one client-thread read per XP_CACHE_MS.
            long nowXp = System.currentTimeMillis();
            if (sessionStats.needsXpRead(nowXp)) {
                sessionStats.cacheXp(Microbot.getClientThread()
                        .runOnClientThreadOptional(() -> Microbot.getClient().getSkillExperience(Skill.MINING))
                        .orElse(-1), nowXp);
            }
            int payDirt = payDirtCount();
            boolean mined = MlmRunControl.minedMore(
                    miningSession.getSubState() == MiningSession.MiningSubState.MINING, lastPayDirtCount, payDirt);
            lastPayDirtCount = payDirt;
            boolean xpRose = sessionStats.recordXp(sessionStats.cachedXp());
            if (xpRose || mined) { // at 200M XP only the pay-dirt shows it
                globalLastXpTime.set(System.currentTimeMillis());
                miningSession.onMiningXp();
                noteProgress();
            }

            // Stop goals wait for a safe point too: a pile on the floor or a trip under way is finished first.
            if (breakSafe && sessionStats.cachedXp() > 0) {
                String goal = MlmRunControl.goalReached(config.stopAfterMinutes(), sessionStats.activeMs(nowTick),
                        config.stopAtLevel(), Experience.getLevelForXp(sessionStats.cachedXp()));
                if (goal != null) {
                    finish("Finished: " + goal + ".", true);
                    return;
                }
            }

            final boolean antibanDispatchPause = isHumanLikeEnabled()
                    && Rs2AntibanSettings.actionCooldownActive;

            if (!miningLevelChecked && !hasMiningLevelForSpot()) {
                log.warn("[MLM] Upper-level mining area chosen below {} Mining — stopping", UPPER_FLOOR_MINING_LEVEL);
                finish("MLM: the upper level needs " + UPPER_FLOOR_MINING_LEVEL
                        + " Mining. Pick a lower-level mining area.", false);
                return;
            }

            if (sackSession.isInventoryClogged()) {
                log.warn("[MLM] Inventory clogged with non-depositable items — stopping");
                finish("MLM: the inventory is full of items the deposit box won't take. "
                        + "Make room and start again.", true);
                return;
            }

            // Before preflight a missing pickaxe is fetched from the bank; after it, there is none to use.
            if (preflightDone && !hasRequiredTools()) {
                log.warn("Missing required tools (Pickaxe), stopping plugin");
                finish("MLM: no pickaxe you can use in your inventory, equipment or bank.", true);
                return;
            }

            // One-shot startup preflight: establish the gem bag, the off-hand Imcando hammer, and the
            // locked gem-bag slot, and resolve the deposit method that is actually safe. It must run to
            // completion once and never re-enter — re-checking the slot lock on the per-tick path walks
            // the player to the bank mid-run and freezes the deposit loop.
            if (!preflightDone) {
                if (preflightStartedMs == 0L) preflightStartedMs = System.currentTimeMillis();
                if (System.currentTimeMillis() - preflightStartedMs > PREFLIGHT_GIVE_UP_MS) {
                    // The bank never opened (PIN, blocked path). Carry on without the extras rather than
                    // idling forever: no bag lock means Deposit-All degrades to depositing item by item.
                    if (config.useInventorySetup() && config.inventorySetup() != null && !inventorySetupLoaded) {
                        log.warn("[MLM] Preflight timed out before the inventory setup loaded — stopping");
                        finish("MLM: couldn't reach the bank to load your inventory setup.", false);
                        return;
                    }
                    log.warn("[MLM] Preflight did not finish in {}s — continuing without the bank extras",
                            PREFLIGHT_GIVE_UP_MS / 1000);
                    // The PIN pad hides the bank from isOpen(), and a click in the world would land on its digits.
                    if (Rs2Bank.isBankPinWidgetVisible()) {
                        finish("MLM: the bank asks for a PIN. Enter it, or save it in your Microbot profile, then start again.", false);
                        return;
                    }
                    if (Rs2Bank.isOpen() && !Rs2Bank.closeBank()) {
                        if (System.currentTimeMillis() - preflightStartedMs > PREFLIGHT_GIVE_UP_MS + BANK_CLOSE_GIVE_UP_MS) {
                            finish("MLM: the bank would not close. Close it and start again.", false);
                        }
                        return;
                    }
                    resolveEffectiveDeposit();
                    preflightDone = true;
                } else {
                    if (!runPreflight()) return; // still settling (withdraw/wield/lock) or stopped
                    preflightDone = true;
                }
            }

            if (stopped()) return; // the bank visit can outlast a stop
            frameCameraOnce();

            if (config.useGemBag()
                    && Rs2Gembag.isUnknown()
                    && (System.currentTimeMillis() - lastGemBagCheckMs > GEMBAG_RECHECK_COOLDOWN_MS)) {
                lastGemBagCheckMs = System.currentTimeMillis();
                Rs2Gembag.checkGemBag();
            }

            // -------------------------------------------------------------------------
            // PRIORITY 1 — tick active sessions
            // -------------------------------------------------------------------------

            if (!miningSession.isIdle()) {
                if (status != MLMStatus.MINING && !miningSession.isLocked() && !miningSession.isCommitted()) {
                    miningSession.reset();
                } else {
                    if (miningSpot == null) {
                        log.warn("[MLM] Mining session active but miningSpot is null — aborting session");
                        miningSession.reset();
                        setStatus(MLMStatus.RECOVERY);
                        return;
                    }
                    if (config.miningArea() != null && !configAllows(miningSpot)) {
                        log.info("[MLM] Config area {} != active spot {} — re-syncing (prevents wrong-floor ladder)",
                                config.miningArea(), miningSpot);
                        selectMiningSpotFromConfig();
                        miningSession.reset();
                        setStatus(MLMStatus.MINING);
                        handleMiningStatus();
                        return;
                    }
                    // The watchdog below only runs between sessions, and a mining session has no time cap.
                    long lastProgressMs = Math.max(globalLastXpTime.get(), Math.max(statusEnteredMs, lastHeldMs));
                    if (status == MLMStatus.MINING && nowTick - lastProgressMs > STUCK_MS) {
                        log.warn("[MLM] Nothing mined for {}s — moving to recovery", (nowTick - lastProgressMs) / 1000);
                        miningSession.reset();
                        setStatus(MLMStatus.RECOVERY);
                        return;
                    }
                    // Spec must run while the mining session is active (handleMiningStatus only
                    // runs when the session is idle). Attempt before tick so we are at-vein mid-cycle.
                    if (status == MLMStatus.MINING && canAttemptPickaxeSpec()) {
                        handlePickaxeSpec();
                        if (needPostSpecMiningResume) {
                            return;
                        }
                    }
                    miningSession.tick(miningSpot, sackState, globalLastXpTime);
                    if (miningSession.isMiningComplete() || miningSession.isFailed()) {
                        boolean failed = miningSession.isFailed();
                        boolean sackFull = miningSession.endedWithSackFull();
                        boolean invFull = miningSession.endedWithInvFull();
                        miningSession.reset();
                        if (failed) {
                            if (shouldRetryMiningAfterSessionFail()) {
                                log.info("[MLM] Mining session failed near spot with inventory room — retrying MINING (not recovery/deposit)");
                                setStatus(MLMStatus.MINING);
                                handleMiningStatus();
                                return;
                            }
                            setStatus(MLMStatus.RECOVERY);
                        } else if (sackFull) {
                            // MiningSession detected sack full — route directly to EMPTY_SACK.
                            // Do NOT re-query sackState.countOrUnknown() which is unreliable due to varbit lag.
                            // However, if we still have pay-dirt, drop it now (hopper/sack won't accept more
                            // when full) so the EMPTY_SACK handler can proceed to empty instead of flipping
                            // back to DEPOSIT_HOPPER.
                            if (payDirtCount() > 0) {
                                log.info("[MLM] Mining ended with SACK_FULL but pay-dirt remains — dropping pay-dirt before emptying sack");
                                dropAllPayDirt();
                            }
                            log.info("[MLM] MiningSession ended with SACK_FULL — routing to EMPTY_SACK");
                            setStatus(MLMStatus.EMPTY_SACK);
                        } else if (invFull) {
                            // Full of pay-dirt goes to the hopper; full of anything else must not, or the
                            // hopper guard sends it straight back to MINING and the two ping-pong forever.
                            setStatus(determineNextStatusAfterMining());
                        } else {
                            setStatus(determineNextStatusAfterMining());
                        }
                    }
                    return;
                }
            }

            if (!sackSession.isIdle()) {
                sackSession.tick(sackState);
                if (sackSession.consumeProgress()) noteProgress();
                sessionStats.applyDepositSnapshot(sackSession.consumePendingOreDepositSnapshot());
                sessionStats.update();
                if (sackSession.isComplete() || sackSession.isFailed()) {
                    boolean    failed      = sackSession.isFailed();
                    WorldPoint returnPoint = sackSession.getReturnPoint();
                    sackSession.reset();
                    if (failed && payDirtCount() > 0) {
                        // SackSession aborts when pay-dirt is in the inventory, and says so: it wants a
                        // hopper trip, not recovery. Honour that instead of treating it as a fault.
                        log.info("[MLM] Sack session aborted holding {} pay-dirt — depositing at the hopper first",
                                payDirtCount());
                        setStatus(MLMStatus.DEPOSIT_HOPPER);
                    } else if (failed) {
                        log.warn("[MLM] Sack session failed - moving to recovery");
                        setStatus(MLMStatus.RECOVERY);
                    } else {
                        if (returnPoint != null) {
                            // Sack always returns to lower; upper returnPoints need ladder handling on next cycle.
                            WorldPoint walkTarget = returnPoint;
                            if (returnPoint.getY() >= IrkedMLMMapConstants.UPPER_HUB_MIN_Y) {
                                walkTarget = IrkedMLMMapConstants.LADDER_BOTTOM_SAFE;
                            }
                            Rs2Walker.walkFastCanvas(walkTarget);
                        }
                        sackState.invalidate("sack emptied");
                        setStatus(MLMStatus.IDLE);
                        sessionSacksEmptied++;
                        saveStats();
                    }
                }
                updateSnapshot();
                return;
            }

            if (!hopperSession.isIdle()) {
                HopperSession.HopperSubState beforeSub = hopperSession.getHopperSubState();

                hopperSession.tick();

                HopperSession.HopperSubState afterSub = hopperSession.getHopperSubState();

                if (beforeSub == HopperSession.HopperSubState.TRANSITIONING_FLOOR
                        && afterSub != HopperSession.HopperSubState.TRANSITIONING_FLOOR) {
                    debug("[MLM] HopperSession: ladder complete, now on correct floor for hopper (upper={})",
                            HopperSession.useUpperHopperForDeposit(config, miningSpot));
                    int sackNow = sackState.countOrUnknown();
                    boolean sackFullNow = sackState.definitelyFull() || sackNow >= sackState.capacity();
                    if (sackFullNow) {
                        log.info("[MLM] Post-ladder: sack full/flag ({} / {}) — abort to EMPTY_SACK",
                                sackNow, sackState.capacity());
                        if (payDirtCount() > 0) {
                            dropAllPayDirt();
                        }
                        hopperSession.reset();
                        setStatus(MLMStatus.EMPTY_SACK);
                        return;
                    }
                    if (payDirtCount() == 0) {
                        log.info("[MLM] Post-ladder: no pay-dirt — back to MINING");
                        hopperSession.reset();
                        setStatus(MLMStatus.MINING);
                        handleMiningStatus();
                        return;
                    }
                    debug("[MLM] Post-ladder: sack has room ({}), proceeding to deposit", sackNow);
                }

                if (hopperSession.isComplete() || hopperSession.isFailed()) {
                    boolean wasComplete = hopperSession.isComplete();
                    // Capture the initial pay-dirt count BEFORE reset() zeroes it — for BOTH paths. The
                    // audit compares residual vs initial to route (rejected/partial/repair/recover); a
                    // failed deposit that read 0 here made residual!=initial always true, skipping the
                    // frozen-wheel + repair gates and dropping into an infinite blind-retry loop.
                    lastHopperDepositInitial = hopperSession.getInitialPayDirtCount();
                    hopperSession.reset();

                    handlePostDepositAudit();
                    if (wasComplete && status == MLMStatus.MINING) {
                        debug("[MLM] Fast-starting mining session immediately after deposit");
                        handleMiningStatus();
                    }
                }
                return;
            }

            if (!repairSession.isIdle()) {
                repairSession.tick();
                if (repairSession.isComplete() || repairSession.isFailed()) {
                    boolean repairFailed = MlmRunControl.repairFailed(repairSession.isFailed(), repairSession.isLeftToBystander(),
                            waterwheelNeedsRepair(tickRunningWheelCount, tickBrokenStrutCount));
                    if (!repairFailed && !repairSession.isLeftToBystander() && tickRunningWheelCount > 0) noProgressMs = 0L;
                    repairSession.reset();
                    statusEnteredMs = System.currentTimeMillis(); // a fresh try is not the old one still stuck
                    consecutiveRepairFailures = repairFailed ? consecutiveRepairFailures + 1 : 0;
                    if (repairFailed && consecutiveRepairFailures >= MAX_CONSECUTIVE_REPAIR_FAILURES
                            && tickRunningWheelCount == 0) {
                        // Deposit and repair otherwise alternate forever: each status change clears the
                        // hopper retry count and the watchdog never sees a stuck status.
                        log.warn("[MLM] {} repairs failed in a row — waiting for the wheel instead", consecutiveRepairFailures);
                        consecutiveRepairFailures = 0;
                        if (MlmRunControl.repairRoundsExhausted(++repairBackoffRounds)) {
                            finish("MLM: both wheels have stopped and the repairs keep failing. "
                                    + "Check for a hammer or try another world, then start again.", true);
                            return;
                        }
                        repairBackoffUntilMs = System.currentTimeMillis() + MlmRunControl.repairBackoffMs(
                                config.hopWhenFrozen(), config.frozenHopGraceSeconds(), Math.random());
                        setStatus(MLMStatus.WAITING_FOR_REPAIR);
                    } else {
                        setStatus(determineNextStatusAfterRepair());
                    }
                }
                return;
            }

            if (antibanDispatchPause) {
                return;
            }

            // -------------------------------------------------------------------------
            // Animation guard — only blocks starting new sessions while animating.
            // IDLE and MINING are allowed through so the state machine never stalls.
            // -------------------------------------------------------------------------
            if ((Rs2Player.isAnimating() || Rs2Player.isInteracting())
                    && status != MLMStatus.MINING && status != MLMStatus.IDLE) {
                return;
            }

            // -------------------------------------------------------------------------
            // PRIORITY 1.5 — reclaim pay-dirt dropped for the sack trip
            // -------------------------------------------------------------------------
            // The sack trip is finished (every session above is idle) and the slots it needed are free
            // again, so go back for the pile rather than leaving several minutes of mining on the floor.
            // Deliberately after the session block: collecting mid-trip would refill the very slots the
            // sack emptying needs.
            if (droppedPayDirt.isPending(System.currentTimeMillis())
                    && status != MLMStatus.EMPTY_SACK
                    && status != MLMStatus.RECOVERY
                    && sackSession.isIdle() && hopperSession.isIdle() && repairSession.isIdle()) {
                if (collectDroppedPayDirt()) {
                    updateSnapshot();
                    return;
                }
            }

            // -------------------------------------------------------------------------
            // PRIORITY 2 — dispatch
            // -------------------------------------------------------------------------
            if (stopped()) return;
            dispatchByStatus();

            // Progress watchdog (>4min in working status without change -> RECOVERY).
            // statusEnteredMs is maintained by setStatus() at the exact moment status changes (including
            // changes made inside the handle* methods called above), so this reflects true dwell time.
            if (status != MLMStatus.IDLE && status != MLMStatus.RECOVERY
                    && status != MLMStatus.WAITING_FOR_REPAIR && statusEnteredMs > 0) {
                long stuckMs = System.currentTimeMillis() - Math.max(statusEnteredMs, lastHeldMs);
                if (stuckMs > STUCK_MS) {  // 4 minutes no status change / no completion — very generous for long walks on south spots
                    log.warn("[MLM] WATCHDOG: no status progress in {} for {}s — forcing RECOVERY (universal resolver for any reason/any spot)", status, stuckMs / 1000);
                    setStatus(MLMStatus.RECOVERY);
                }
            }
        } catch (Exception e) {
            log.error("[MLM] executeTask crash", e);
        } finally {
            // Always update snapshot once running to keep overlay fresh, but client thread reads inside are now heavily cached
            // to prevent the startup TimeoutExceptions that were triggering breakhandler dialogs and blocking events.
            updateSnapshot();
            executing.set(false);
            runPendingTearDown(); // stopped while this tick ran
        }
    }

    // =========================================================================
    // Special Attack
    // =========================================================================

    /**
     * Dragon/infernal/crystal pickaxe special. Requires {@link #canAttemptPickaxeSpec()} (in mining
     * area, {@link MiningSession.MiningSubState#MINING} only). Sets {@link #needPostSpecMiningResume}
     * so we wait out the spec animation before the next Mine click (no duplicate click).
     */
    private void handlePickaxeSpec() {
        long now = System.currentTimeMillis();
        long effectiveCooldown = SPEC_COOLDOWN_MS + specCooldownVarianceMs;

        if (now - lastSpecTime < effectiveCooldown) {
            return;
        }

        if (status != MLMStatus.MINING) {
            debug("[MLM] Spec skipped: not actively mining (status={})", status);
            return;
        }
        // Avoid firing spec on the very first status transition at startup before we've even begun a mining session.
        if (startTimeMs == 0 || System.currentTimeMillis() - startTimeMs < 3000L) {
            debug("[MLM] Spec skipped: too soon after startup");
            return;
        }

        if (!canAttemptPickaxeSpec()) {
            debug("[MLM] Spec skipped: not positioned at a mineable vein");
            return;
        }

        int specEnergy = Microbot.getRs2PlayerStateCache().getVarpValue(VARP_SPEC_ENERGY);
        if (specEnergy < SPEC_ENERGY_FULL) {
            debug("[MLM] Spec skipped: need full spec bar ({}/{})", specEnergy, SPEC_ENERGY_FULL);
            specBarFullSinceMs = 0L; // bar not full — reset so the readiness delay re-rolls when it refills
            return;
        }

        // Spec is NOT instant-on-full: once the bar reaches full, wait a randomized readiness window of
        // continued mining before actually speccing (re-rolled each time the bar refills). Applies in
        // both modes so it never robotically fires the moment the bar tops off.
        if (specBarFullSinceMs == 0L) {
            specBarFullSinceMs = now;
            specReadinessDelayMs = Rs2Random.between(3_000, 25_000);
            debug("[MLM] Spec bar full — deferring spec by {}ms (not instant)", specReadinessDelayMs);
            return;
        }
        if (now - specBarFullSinceMs < specReadinessDelayMs) {
            return;
        }

        boolean specUsed = false;

        // Energy is already confirmed full (>= SPEC_ENERGY_FULL) above, so no per-pickaxe energy
        // threshold check is needed here.
        Pickaxe.Spec wielded = Pickaxe.wieldedSpec();
        if (wielded == Pickaxe.Spec.CRYSTAL) {
            // 15% chance to skip this tick even if conditions met (human hesitation / imperfection).
            // Only when human-like enabled; when off we spec as soon as energy + next-to-vein (optimal).
            if (isHumanLikeEnabled() && personality.roll(personality.specHesitationChance())) {
                debug("[MLM] Spec hesitation: crystal pickaxe, skipping this tick");
                return;
            }
            humanPause(150, 350, false);
            Rs2Combat.setSpecState(true, SPEC_ENERGY_CRYSTAL);
            lastSpecTime = now;
            specCooldownVarianceMs = Rs2Random.between(-5000, 15000); // -5s to +15s
            specUsed = true;
            log.info("[MLM] Crystal pickaxe spec activated (energy={}/1000)", specEnergy);
        } else if (wielded == Pickaxe.Spec.DRAGON) {
            if (isHumanLikeEnabled() && personality.roll(personality.specHesitationChance())) {
                debug("[MLM] Spec hesitation: dragon/infernal pickaxe, skipping this tick");
                return;
            }
            humanPause(150, 350, false);
            Rs2Combat.setSpecState(true, SPEC_ENERGY_DRAGON_INFERNAL);
            lastSpecTime = now;
            specCooldownVarianceMs = Rs2Random.between(-5000, 15000);
            specUsed = true;
            log.info("[MLM] Dragon/Infernal pickaxe spec activated (energy={}/1000)", specEnergy);
        }
        // (no per-tick "not wearing a spec pickaxe" log — it fired every tick since spec energy is always
        //  full for non-spec pickaxes; pure noise.)

        if (specUsed) {
            specBarFullSinceMs = 0L; // consumed — re-roll the readiness delay next time the bar refills
            needPostSpecMiningResume = true;
            // Brief pause to let spec animation start. Human gated for likeness (when off: shorter consistent to feel attentive).
            if (isHumanLikeEnabled()) {
                sleep(Rs2Random.between(800, 1200));
            } else {
                sleep(Rs2Random.between(120, 220));
            }
        }
    }

    /** Wait out spec animation; MiningSession issues the next Mine click (no duplicate here). */
    private void handlePostSpecMining() {
        debug("[MLM] Post-spec: clearing resume flag (MiningSession owns the next Mine click)");

        // Mining animates continuously, so "until not animating" held the whole state machine for a
        // full mining bout. The spec animation itself is short.
        if (Rs2Player.isAnimating() && System.currentTimeMillis() - lastSpecTime < 1_500L) {
            debug("[MLM] Post-spec: waiting for spec animation to finish");
            return;
        }

        needPostSpecMiningResume = false;
    }

    // =========================================================================
    // Hopper floor policy (mirrors HopperSession#desiredFloorIsUpper)
    // =========================================================================

    static WorldPoint recoverySafeWalkPoint(MLMMiningSpot spot) {
        if (spot != null && spot.isDownstairs()) {
            List<WorldPoint> pts = spot.getWorldPoint();
            if (pts != null && !pts.isEmpty()) {
                return pts.get(0);
            }
        }
        return IrkedMLMMapConstants.LADDER_BOTTOM_SAFE;
    }

    private WorldPoint hopperWalkTarget() {
        return HopperSession.hopperWalkTargetFor(config, miningSpot);
    }

    /** Hopper deposit is only warranted when the inventory is full of pay-dirt. */
    private boolean shouldDepositPayDirtAtHopper() {
        return Rs2Inventory.isFull() && payDirtCount() > 0;
    }

    /**
     * Returns true if player is in a mining area (near ore veins).
     * Used to prevent spec activation while walking to hopper/sack.
     */
    private boolean isInMiningArea() {
        var playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null || miningSpot == null) return false;

        if (miningSpot.contains(playerLoc)) {
            return true;
        }

        // Fallback to anchor distance (for compatibility / legacy spots)
        List<WorldPoint> anchors = miningSpot.getWorldPoint();
        if (anchors == null || anchors.isEmpty()) return false;
        for (WorldPoint anchor : anchors) {
            if (playerLoc.distanceTo(anchor) <= 10) {
                return true;
            }
        }
        return false;
    }

    /**
     * After a mining-session fail (e.g. web-walk pathfinder-null while already at the spot),
     * retry mining instead of recovery→hopper when we still have inventory room.
     */
    private boolean shouldRetryMiningAfterSessionFail() {
        if (miningSpot == null || Rs2Inventory.isFull()) {
            return false;
        }
        var here = Rs2Player.getWorldLocation();
        if (here == null) {
            return false;
        }
        boolean inArea = miningSpot.contains(here);
        if (!inArea) {
            List<WorldPoint> anchors = miningSpot.getWorldPoint();
            boolean nearAnchor = false;
            if (anchors != null) {
                for (WorldPoint anchor : anchors) {
                    if (anchor != null && here.distanceTo(anchor) <= 12) {
                        nearAnchor = true;
                        break;
                    }
                }
            }
            if (!nearAnchor) {
                return false;
            }
        }
        return true;
    }

    /**
     * True when the player is at a vein and may use pickaxe special (dragon/infernal/crystal).
     * Uses the session target first (wall veins may sit outside a strict mesh tile but still be mined).
     */
    private boolean canAttemptPickaxeSpec() {
        if (!isInMiningArea()) {
            return false;
        }
        if (miningSession.isIdle()) {
            return false;
        }
        var playerLoc = Rs2Player.getWorldLocation();
        return miningSession.canAttemptPickaxeSpecial(miningSpot, playerLoc);
    }

    // =========================================================================
    // Economy & Stats
    // =========================================================================

    /**
     * Continuity break. The sack keeps filling while we are away, so everything believed about it is
     * void until re-observed. One rule covers logout, disconnect, hop, and leaving the mine.
     */
    public void onGameStateChanged(net.runelite.api.GameState state) {
        switch (state) {
            case LOGIN_SCREEN:
            case CONNECTION_LOST:
            case HOPPING:
                sackState.invalidate("game state " + state);
                break;
            case LOADING:
                if (!IrkedMLMMapConstants.inMine()) {
                    sackState.invalidate("loaded a region outside the mine");
                }
                break;
            default:
                break;
        }
    }

    /** Game message: the sack is full. Authoritative. Called on the client thread. */
    public void onSackIsFullChat() {
        sackState.onSackIsFullChat(System.currentTimeMillis());
        updateSnapshot();
    }

    /** Game message: the sack will be full. A warning only — it must not touch the count. */
    public void onNearlyFullChat() {
        sackState.onNearlyFullChat();
    }

    /**
     * Varbit 5558 moved. Runs on the client thread, so read the companion varbit directly without a
     * hop — but only trust either inside the mine, where the value is meaningful.
     */
    public void onSackVarbitChanged(int value) {
        if (!IrkedMLMMapConstants.inMine()) {
            return;
        }
        sackState.observe(value, Microbot.getClient().getVarbitValue(VarbitID.MOTHERLODE_BIGGERSACK),
                System.currentTimeMillis());
        updateSnapshot();
    }

    /**
     * The one place sack knowledge enters the script. Reads both sack varbits in a single
     * client-thread hop, gated on actually being in the mine, and feeds them to {@link SackState}.
     *
     * @return true when the sack count is known after this call
     */
    private boolean observeSack() {
        if (!Microbot.isLoggedIn()) {
            return sackState.isKnown();
        }
        // Stored inside the callback: a hop or logout invalidates on the client thread too, so a reading
        // from before it can't be stored after it.
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (IrkedMLMMapConstants.inMine()) {
                sackState.observe(Microbot.getClient().getVarbitValue(VarbitID.MOTHERLODE_SACK_TRANSMIT),
                        Microbot.getClient().getVarbitValue(VarbitID.MOTHERLODE_BIGGERSACK), System.currentTimeMillis());
            }
            return true;
        });
        return sackState.isKnown(); // not in the mine, or the read failed — never guess
    }

    // =========================================================================
    // Post-deposit audit
    // =========================================================================

    private void handlePostDepositAudit() {
        int residual = payDirtCount();
        int initial  = lastHopperDepositInitial > 0 ? lastHopperDepositInitial : hopperSession.getInitialPayDirtCount();
        lastHopperDepositInitial = 0; // consume the snapshot for this audit
        log.info("[MLM] Post-deposit audit: residual={} (started with {})", residual, initial);
        if (residual < initial) {
            noteProgress();
            repairBackoffRounds = 0;
        }
        hopperRejectedFrozen = residual > 0 && residual == initial
                && waterwheelNeedsRepair(tickRunningWheelCount, tickBrokenStrutCount);

        if (residual == 0) {
            hopperDepositRetries = 0;
            // Every offered pay-dirt was accepted. The total rose, but the new total has not been
            // transmitted yet — and nothing needs it, because the inventory is now empty of pay-dirt
            // and the next load is minutes away. So: unknown, never projected. This is what makes the
            // old double-count bug impossible (real 186 + stale projection 27 = 213 >= cap, which
            // sent us to EMPTY_SACK while there was still room).
            sackState.onCleanDeposit();
            log.info("[MLM] Post-deposit: all {} pay-dirt accepted; sack total pending re-read", initial);

            // THE single repair gate. The hopper only stalls when BOTH wheels are broken — one
            // running wheel still washes pay-dirt into the sack. Repair only when the sack has room
            // (processing is pointless if it is full → empty instead) and no one else is fixing it.
            if (shouldRepairStrutsBeforeDeposit()
                    && !sackState.definitelyFull()
                    && !(Rs2Inventory.isFull() && payDirtCount() > 0)) {
                log.info("[MLM] Post-deposit: struts broken and clear — repairing so the hopper processes the deposit");
                setStatus(MLMStatus.FIXING_WATERWHEEL);
                return;
            }

            if (Rs2Inventory.isFull() && payDirtCount() > 0) {
                if (sackState.definitelyFull()) {
                    log.info("[MLM] Sack full but still holding pay-dirt — dropping then emptying sack");
                    dropAllPayDirt();
                    setStatus(MLMStatus.EMPTY_SACK);
                } else {
                    setStatus(MLMStatus.DEPOSIT_HOPPER);
                }
            } else if (sackState.definitelyFull() || hasOreInInventory()) {
                setStatus(MLMStatus.EMPTY_SACK);
            } else {
                log.debug("[MLM] Post-deposit: resuming mining (repair, if needed, happens on the next deposit trip)");
                setStatus(MLMStatus.MINING);

                if (currentAnchor != null) {
                    WorldPoint here = Rs2Player.getWorldLocation();
                    if (here != null && here.distanceTo(currentAnchor) > 5) {
                        Rs2Walker.walkFastCanvas(currentAnchor);
                    }
                }
            }
            return;
        }

        if (sackState.definitelyFull()) {
            log.info("[MLM] Audit: {} pay-dirt remains and the sack is full ({}) — clearing sack",
                    residual, sackState.capacity());

            // Always drop: SackSession refuses to run while pay-dirt is held (it needs the slots), so
            // carrying it in "because there are ores too" ended the trip in RECOVERY. The pile is
            // reclaimed after the sack is emptied, so dropping costs nothing.
            dropAllPayDirt();
            setStatus(MLMStatus.EMPTY_SACK);
            return;
        }

        // NOTE: a partial deposit (0 < residual < initial) is NOT evidence of a full sack. The hopper
        // accepts pay-dirt in chunks, so partial acceptance is the normal multi-click case and is
        // handled by the tail of this method, which simply re-enters DEPOSIT_HOPPER for the rest.
        // Fullness is only ever concluded from a hard signal: the chat message, the varbit, or two
        // consecutive TOTAL refusals with a wheel running (below).

        if (residual == initial) {
            // Deposit rejected with nothing accepted — the hopper is jammed. It only stalls when the water
            // freezes (both wheels broken), so it's backed up and won't accept pay-dirt until a wheel turns.
            // Retrying the click does nothing; the wheel has to be dealt with first.
            if (shouldRepairStrutsBeforeDeposit() && !sackState.definitelyFull()) {
                log.info("[MLM] Deposit rejected while wheel frozen — repairing so the hopper drains and accepts pay-dirt");
                setStatus(MLMStatus.FIXING_WATERWHEEL);
                return;
            }
            if (shouldWaitForOthersToRepair()) {
                log.info("[MLM] Deposit rejected while wheel frozen (repairs off) — waiting for another player to repair");
                hopperSession.reset();
                setStatus(MLMStatus.WAITING_FOR_REPAIR);
                return;
            }
            hopperDepositRetries++;

            // Two identical TOTAL refusals with a wheel turning. The hopper refuses everything for
            // exactly two reasons — frozen water (both gates above) or a full sack — so it is the
            // sack, whatever the varbit says. The retry-once is what distinguishes this from a click
            // that simply missed, and it must not be dropped: a single total refusal is ambiguous.
            if (tickRunningWheelCount > 0 && hopperDepositRetries > 1) {
                log.info("[MLM] Hopper refused all {} pay-dirt twice with a wheel running — sack is full",
                        initial);
                sackState.onHopperRefusal(System.currentTimeMillis());
                dropAllPayDirt();
                setStatus(MLMStatus.EMPTY_SACK);
                return;
            }

            if (hopperDepositRetries >= HopperSession.MAX_DEPOSIT_RETRIES) {
                log.error("[MLM] Deposit rejected {} times (wheel not frozen / repairs off) — entering recovery", HopperSession.MAX_DEPOSIT_RETRIES);
                setStatus(MLMStatus.RECOVERY);
                return;
            }
            if (!shouldDepositPayDirtAtHopper()) {
                log.info("[MLM] Audit: deposit failed but inv not full ({} remain) — resuming MINING", residual);
                hopperSession.reset();
                setStatus(MLMStatus.MINING);
                return;
            }
            log.info("[MLM] Audit: deposit rejected ({} / {} remain) — retrying ({}/{})",
                    residual, initial, hopperDepositRetries, HopperSession.MAX_DEPOSIT_RETRIES);
            setStatus(MLMStatus.DEPOSIT_HOPPER);
            return;
        }

        // residual < initial here: pay-dirt WAS accepted, so this is normal multi-click depositing, not a
        // stall. Progress clears the no-progress retry budget (the residual==initial branch above owns
        // stall escalation); pay-dirt is finite and decreasing, so this can't loop forever.
        hopperDepositRetries = 0;
        if (!shouldDepositPayDirtAtHopper()) {
            log.info("[MLM] Audit: partial deposit ({} remain) but inv not full — resuming MINING", residual);
            hopperSession.reset();
            setStatus(MLMStatus.MINING);
            return;
        }
        // Re-enter DEPOSIT_HOPPER to deposit the rest.
        log.info("[MLM] Audit: partial deposit ({} / {} remain, progress made) — depositing the rest",
                residual, initial);
        setStatus(MLMStatus.DEPOSIT_HOPPER);
    }

    private void dropAllPayDirt() {
        int count = payDirtCount();
        if (count == 0 && !dropInProgress) return;

        if (!dropInProgress) {
            log.info("[MLM] Dropping {} pay-dirt to clear inventory for sack emptying", count);
            dropInProgress = true;
            droppedThisRun = 0;
            dropTile = null;
            lastDropMs = System.currentTimeMillis();
        }

        // A few drops per tick, at a shift-click pace, then hand the tick back. The old single loop either
        // fired ~27 clicks with no gap (fast mode) or held the executor for seconds (human mode). The
        // state machine waits at the top of executeTask() until the whole load is down.
        // One click per slot: the inventory only catches up a tick later, so asking for "a pay-dirt" again
        // would click the same one.
        List<Rs2ItemModel> batch = Rs2Inventory.items(i -> i.getId() == ItemID.PAYDIRT)
                .limit(Rs2Random.between(3, 7)).collect(java.util.stream.Collectors.toList());
        for (Rs2ItemModel payDirt : batch) {
            if (stopped()) return;
            if (!standingOnDropTile()) {
                if (System.currentTimeMillis() - lastDropMs > DROP_SETTLE_MS) {
                    log.warn("[MLM] Could not stand still on one tile to drop the pay-dirt — moving to recovery");
                    finishPayDirtDrop(); // keeps the claim on what is already down
                    setStatus(MLMStatus.RECOVERY);
                }
                return;
            }
            if (!Rs2Inventory.interact(payDirt, "Drop")) {
                log.warn("[MLM] Failed to drop pay-dirt after {}, stopping the drop", droppedThisRun);
                finishPayDirtDrop();
                return;
            }
            droppedThisRun++;
            lastDropMs = System.currentTimeMillis();
            sleep(isHumanLikeEnabled() ? Rs2Random.between(70, 160) : Rs2Random.between(40, 90));
        }

        if (payDirtCount() == 0 || droppedThisRun >= MAX_DROP_CLICKS) {
            finishPayDirtDrop();
        }
    }

    /**
     * The whole load must land on one known tile on a known floor, or the pile can't be found again. Picks
     * that tile at the first drop; moved off it, walks back.
     */
    private boolean standingOnDropTile() {
        // Read together, so the tile, the floor and standing still belong to the same moment.
        miningSession.invalidateUpperFloorCache();
        java.util.Map.Entry<WorldPoint, Session.Floor> at = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            WorldPoint here = Rs2Player.getWorldLocation();
            return here == null || Rs2Player.isMoving() ? null : java.util.Map.entry(here, Session.currentFloor());
        }).orElse(null);
        if (at == null || at.getValue() == Session.Floor.UNKNOWN) return false;
        WorldPoint here = at.getKey();
        boolean upper = at.getValue() == Session.Floor.UPPER;
        if (dropTile == null) {
            dropTile = droppedPayDirt.dropTile(here, upper, DROP_SAME_PILE_RANGE, System.currentTimeMillis());
            dropOnUpperFloor = upper;
        }
        if (upper == dropOnUpperFloor && here.equals(dropTile)) return true;
        if (upper == dropOnUpperFloor) Rs2Walker.walkFastCanvas(dropTile);
        return false;
    }

    private void finishPayDirtDrop() {
        dropInProgress = false;
        log.info("[MLM] Pay-dirt drop complete, dropped={}, remaining: {}", droppedThisRun, payDirtCount());

        if (droppedThisRun > 0) {
            // Remember the pile so it can be picked back up once the sack is clear — that is several
            // minutes of mining lying on the floor, and a player would never just walk off from it.
            droppedPayDirt.add(dropTile, dropOnUpperFloor, droppedThisRun, System.currentTimeMillis());
        }
        droppedThisRun = 0;
        dropTile = null;
    }

    /**
     * Collects pay-dirt we dropped to free slots for emptying the sack.
     *
     * @return {@code true} while still collecting (the caller should not move on yet)
     */
    private boolean collectDroppedPayDirt() {
        long now = System.currentTimeMillis();

        if (!droppedPayDirt.isPending(now)) {
            droppedPayDirt.clear();
            return false;
        }

        // Floor first, and it must be the height-based test: both MLM levels are the same plane, so a
        // pile dropped upstairs sits only ~12 world tiles from the downstairs deposit box. Every
        // WorldPoint distance check reads that as "right there", and the tile-item cache happily finds the
        // pile in the loaded scene — so the bot stood on the lower floor clicking pay-dirt it could
        // only reach by ladder, forever. If we are on the wrong level, do nothing and keep the claim:
        // the run climbs back up to its mining spot on its own, and collection resumes there.
        Session.Floor floorNow = Session.currentFloor();
        if (floorNow == Session.Floor.UNKNOWN) {
            return true; // ask again next tick; the claim's own expiry bounds this
        }
        boolean upstairsNow = floorNow == Session.Floor.UPPER;
        if (upstairsNow != droppedPayDirt.isOnUpperFloor()) {
            // Go back up (or down) for it now: waiting until the next mining bout reached that floor meant
            // the bout filled the inventory first and the pile was written off.
            if (Rs2Inventory.emptySlotCount() <= 0 || droppedPayDirt.collectTimedOut(now)) {
                debug("[MLM] Dropped pay-dirt on the other floor is not worth the climb now — writing it off");
                droppedPayDirt.clear();
                return false;
            }
            droppedPayDirt.beginCollecting(now);
            debug("[MLM] Dropped pay-dirt is on the {} floor — climbing back for it",
                    droppedPayDirt.isOnUpperFloor() ? "upper" : "lower");
            miningSession.climbTowards(droppedPayDirt.isOnUpperFloor());
            return true;
        }

        WorldPoint here = Rs2Player.getWorldLocation();
        WorldPoint pile = droppedPayDirt.getWhere();
        boolean nearPile = here != null && pile != null && here.distanceTo(pile) <= PAYDIRT_RECLAIM_RANGE;

        if (!droppedPayDirt.shouldCollect(now, Rs2Inventory.emptySlotCount(), nearPile)) {
            if (!nearPile) {
                debug("[MLM] Dropped pay-dirt is out of range now — writing it off");
            }
            droppedPayDirt.clear();
            return false;
        }

        droppedPayDirt.beginCollecting(now);

        // Anchor to the tile we dropped on, the same shape HopperSession uses to tell the two
        // same-id hoppers apart: walk to the expected point, then only act within a tight tolerance
        // of it. A wide sweep would happily grab a stranger's pile — or ours from the wrong side of
        // the chamber — and the drop put every one of our items on a single known tile.
        if (here != null && pile != null && here.distanceTo(pile) > PAYDIRT_PICKUP_TOLERANCE) {
            debug("[MLM] Walking to the dropped pay-dirt at {} ({} tiles)", pile, here.distanceTo(pile));
            Rs2Walker.walkFastCanvas(pile);
            return true;
        }

        // One lookup serves both the existence check and the click. The old ground-item helper is
        // deprecated for removal (client 2.1.0) in favour of the tile-item cache, and its
        // exists()+loot() pair rescanned the scene, so the pile could despawn between the two.
        // within() reads the player's world location, so the whole query is a single client-thread hop.
        Rs2TileItemModel payDirtItem = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getRs2TileItemCache().query()
                        .withId(ItemID.PAYDIRT)
                        .where(i -> pile.equals(i.getWorldLocation()))
                        .first()).orElse(null);

        if (payDirtItem == null) {
            log.info("[MLM] Collected the dropped pay-dirt — depositing it before returning to mine");
            droppedPayDirt.clear();
            routeReclaimedPayDirt();
            return false;
        }
        if (Rs2Inventory.emptySlotCount() <= 0) {
            log.info("[MLM] Inventory full while collecting dropped pay-dirt — depositing what we have");
            droppedPayDirt.clear();
            routeReclaimedPayDirt();
            return false;
        }

        if (payDirtItem.pickup()) {
            humanPause(120, 340, true);
        } else {
            debug("[MLM] Pay-dirt pickup click did not register — retrying");
            sleep(Rs2Random.between(250, 600));
        }
        return true;
    }

    // =========================================================================
    // Status determination
    // =========================================================================

    private MLMStatus determineNextStatusAfterMining() {
        int effectiveSackCount = sackState.countOrUnknown();
        boolean sackFull = sackState.capacity() > 0 && effectiveSackCount >= sackState.capacity();

        // Priority 1: Sack full → must empty. Emptying does NOT need the waterwheel; repair is only
        // ever done on the deposit trip, so no repair routing here.
        if (sackFull || hasOreInInventory()) {
            // If we also have pay-dirt, drop it first so sack emptying can proceed
            if (payDirtCount() > 0) {
                log.info("[MLM] Sack full with pay-dirt in inventory — dropping pay-dirt before emptying sack");
                dropAllPayDirt();
            }
            return MLMStatus.EMPTY_SACK;
        }

        // Gems before the hopper trip: this check used to sit *below* the deposit branch, which
        // matches on any full inventory and so made it unreachable. Never carry gems to the hopper.
        if (config.dropGems() && !config.useGemBag() && hasGemsInInventory()) {
            return MLMStatus.DROP_GEMS;
        }

        // Never deposit into a sack we cannot measure: that is what overfills it and strands pay-dirt.
        // Mining stays allowed while unknown, so this defers the trip rather than stalling the bot.
        if (Rs2Inventory.isFull() && payDirtCount() > 0 && !sackState.isKnown()) {
            debug("[MLM] Inventory full but the sack count is unknown — holding off the hopper trip");
            return MLMStatus.IDLE;
        }

        // Inventory full of pay-dirt — deposit at hopper. Repair check happens in handler.
        if (Rs2Inventory.isFull() && payDirtCount() > 0) {
            return MLMStatus.DEPOSIT_HOPPER;
        }

        // Full, but not of pay-dirt: junk, nuggets and anything else the user is carrying have taken
        // every slot. Returning MINING here span forever — MiningSession immediately reports INV_FULL,
        // the session completes, and we land right back here. Emptying the sack is the only move that
        // frees space, and it is harmless when the sack is already empty.
        if (Rs2Inventory.isFull()) {
            log.warn("[MLM] Inventory full with no pay-dirt to deposit — going to empty the sack to free space");
            return MLMStatus.EMPTY_SACK;
        }

        return MLMStatus.MINING;
    }

    private MLMStatus determineNextStatusAfterRepair() {
        // After repair (or repair abort e.g. another player near wheel), re-check sack state with
        // highest priority. Sack full (detected via flag or count) always wins over resuming mining.
        // If we have leftover pay-dirt from hammer-fetch drop, deposit ONLY if sack not full.
        boolean sackFull = sackState.definitelyFull();
        if (Rs2Inventory.isFull() && payDirtCount() > 0) {
            if (sackFull) {
                log.info("[MLM] Post-repair: pay-dirt remains but sack is full — dropping pay-dirt then EMPTY_SACK");
                dropAllPayDirt();
                return MLMStatus.EMPTY_SACK;
            }
            return MLMStatus.DEPOSIT_HOPPER;
        }

        if (sackFull || hasOreInInventory()) {
            return MLMStatus.EMPTY_SACK;
        }
        return MLMStatus.MINING;
    }

    // =========================================================================
    // Status dispatch
    // =========================================================================

    private void dispatchByStatus() {
        switch (status) {
            case IDLE:
                // The startup full-sack probe that used to live here is gone: observeSack() runs every
                // tick in executeTask(), so by the time IDLE evaluates, the count is either observed or
                // explicitly unknown. That also removes the duplicate of ensureStartupSackChecked().
                if (sackState.definitelyFull() || hasOreInInventory()) {
                    if (payDirtCount() > 0) {
                        log.info("[MLM] IDLE eval: sack full with pay-dirt in inventory — dropping it before emptying sack");
                        dropAllPayDirt();
                    }
                    // Emptying the sack doesn't need the waterwheel; repair only happens on the deposit trip.
                    // Small human "time to go banking" pause instead of instant action from idle.
                    // Urgent because sack is (or just became) full — bias quick when human on.
                    humanPause(300, 800, true);
                    setStatus(MLMStatus.EMPTY_SACK);
                    log.info("[MLM] IDLE eval -> {} (sack={}/{})", status, sackState.countOrUnknown(), sackState.capacity());
                } else if (Rs2Inventory.isFull() && payDirtCount() > 0) {
                    if (!sackState.isKnown()) {
                        debug("[MLM] IDLE: full of pay-dirt but the sack count is unknown — waiting for a reading");
                        return;
                    }
                    humanPause(200, 600);
                    setStatus(MLMStatus.DEPOSIT_HOPPER);
                    log.info("[MLM] IDLE eval -> DEPOSIT_HOPPER");
                } else if (config.dropGems() && !config.useGemBag() && hasGemsInInventory()) {
                    setStatus(MLMStatus.DROP_GEMS);
                } else {
                    // On a true cold start (plugin just enabled, no prior deposits this run, low tick count)
                    // we want to begin the first mining cycle promptly so it doesn't "feel slow to find
                    // a mining area". Only apply the small random human "gear up" pause on subsequent cycles.
                    boolean coldStart = scriptTicks < 4;
                    // 25% "gear up / orient" pause only when human-like enabled (imperfection / relaxed start).
                    // When disabled or cold: prompt start (attentive).
                    boolean doGearUp = isHumanLikeEnabled() && !coldStart
                            && personality.roll(personality.gearUpPauseChance());
                    if (doGearUp) {
                        humanPause(150, 400);
                    } else if (coldStart) {
                        debug("[MLM] Cold start IDLE -> MINING (skipping gear-up pause for responsive first cycle)");
                    } else {
                        debug("[MLM] IDLE -> MINING (no extra gear-up pause this time)");
                    }
                    if (coldStart) {
                        log.info("[MLM] Cold start: proceeding to first mining cycle (spot pre-selected at init)");
                    }
                    setStatus(MLMStatus.MINING);
                    handleMiningStatus();
                }
                break;
            case MINING:            handleMiningStatus();        break;
            case EMPTY_SACK:        handleEmptySackStatus();     break;
            case DEPOSIT_HOPPER:    handleDepositHopperStatus(); break;
            case FIXING_WATERWHEEL: handleRepairStatus();        break;
            case WAITING_FOR_REPAIR: handleWaitingForRepair();   break;
            case DROP_GEMS:         handleDropGemsStatus();      break;
            case RECOVERY:          handleRecoveryStatus();      break;
            default:                                             break;
        }
    }

    private void handleMiningStatus() {
        // GUARD: never start mining while holding a full inventory of pay-dirt.
        // This catches edge cases where status was set to MINING but pay-dirt
        // remains (e.g. after repair dropped some for hammer, or recovery).
        // If sack is also full, drop the pay-dirt and go empty sack instead of trying to deposit
        // (hopper will reject when sack at capacity).
        if (Rs2Inventory.isFull() && payDirtCount() > 0) {
            if (sackState.definitelyFull()) {
                log.info("[MLM] Mining blocked: full pay-dirt but sack full — dropping pay-dirt then emptying sack");
                dropAllPayDirt();
                setStatus(MLMStatus.EMPTY_SACK);
            } else {
                log.info("[MLM] Mining blocked: full inventory of pay-dirt — depositing first");
                setStatus(MLMStatus.DEPOSIT_HOPPER);
            }
            return;
        }

        // CRITICAL: if sack is full (flag or count), do not start mining at all — go empty first.
        // (Emptying doesn't need the waterwheel; repair is only ever done on the deposit trip.)
        if (sackState.definitelyFull()) {
            log.info("[MLM] Sack full at mining status — routing to EMPTY_SACK instead of mining");
            setStatus(MLMStatus.EMPTY_SACK);
            return;
        }

        // Check spec BEFORE starting mining session
        handlePickaxeSpec();

        // If we just used spec, don't start a new mining session yet
        if (needPostSpecMiningResume) {
            debug("[MLM] Mining status: waiting for post-spec resume");
            return;
        }

        debug("[MLM] handleMiningStatus: proceeding to mine | spot={} | anchor={} | brokenStruts={}",
                miningSpot, currentAnchor, tickBrokenStrutCount);

        // Guard against NPE if Rs2Antiban activity was reset (e.g. shutdown race or external reset).
        // This line re-syncs intensity from the Activity; safe to skip if not configured.
        try {
            Activity act = Rs2Antiban.getActivity();
            if (act != null) {
                Rs2Antiban.setActivityIntensity(act.getActivityIntensity());
            }
        } catch (Exception e) {
            debug("[MLM] Antiban intensity sync skipped during mining status: {}", e.getMessage());
        }

        if (config.useAntiCrash() && miningSpot != null && isPlayerAtMiningSpot()) {
            debug("[MLM] Anti-crash: player detected at mining spot, re-selecting");
            selectMiningSpotFromConfig();
            // Human "oh, someone else is here — I'll move" reaction time.
            humanPause(350, 950);
        } else if (config.useAntiCrash() && miningSpot != null) {
            debug("[MLM] Anti-crash check passed: no crowding at anchor {}", currentAnchor);
        }

        if (miningSpot == null || shouldRefreshMiningSpot()) {
            if (miningSpot != null) {
                debug("[MLM] Refreshing mining spot (config change or shuffle jitter)");
            }
            selectMiningSpotFromConfig();
        }

        if (miningSpot == null) {
            log.warn("[MLM] Cannot start mining — no valid spot assigned");
            selectMiningSpotFromConfig();
            if (miningSpot == null) {
                log.error("[MLM] Spot selection failed — entering recovery");
                setStatus(MLMStatus.RECOVERY);
                return;
            }
        }

        if (miningSession.isIdle()) {
            log.info("[MLM] Beginning MiningSession for spot {} (anchor {})", miningSpot, currentAnchor);
            miningSession.begin();
        }
    }

    private boolean shouldRefreshMiningSpot() {
        if (!configAllows(miningSpot)) {
            log.info("[MLM] Mining area changed in config — forcing spot refresh");
            return true;
        }
        // Human "getting bored of this exact spot" – interval has natural jitter (only when human enabled).
        // When disabled: exact 5min (predictable, attentive, no artificial "bored" shuffle).
        long jitter = isHumanLikeEnabled() ? Rs2Random.between(-45000, 75000) : 0L; // +/- 45-75s around 5min
        long elapsed = System.currentTimeMillis() - lastSpotShuffleMs;
        if (elapsed > (SPOT_SHUFFLE_INTERVAL_MS + jitter)) {
            log.info("[MLM] Spot shuffle due to time + jitter (elapsed={}ms, jitter={})", elapsed, jitter);
            lastSpotShuffleMs = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    private boolean isPlayerAtMiningSpot() {
        if (currentAnchor == null) return false;
        String localName = localPlayerName;
        int others = Microbot.getRs2PlayerCache().query()
                .where(p -> {
                    String name = p.getName();
                    return name != null
                            && !name.equalsIgnoreCase(localName)
                            && p.getWorldLocation().distanceTo(currentAnchor) <= 2;
                })
                .count();
        if (others > 0) {
            debug("[MLM] Anti-crash: {} player(s) at anchor {}", others, currentAnchor);
        }
        return others > 0;
    }

    /** The active spot is what the config asks for; ANY accepts any real lower area. */
    private boolean configAllows(MLMMiningSpot spot) {
        MLMMiningSpot wanted = config.miningArea();
        if (wanted == MLMMiningSpot.ANY) {
            return spot != null && MLMMiningSpot.LOWER_AREAS.contains(spot);
        }
        return wanted == spot;
    }

    private void selectMiningSpotFromConfig() {
        MLMMiningSpot selected = config.miningArea();
        if (selected == MLMMiningSpot.ANY) {
            // A fresh lower area, not the one we just left, so "getting bored of a spot" really moves.
            List<MLMMiningSpot> pool = new java.util.ArrayList<>(MLMMiningSpot.LOWER_AREAS);
            if (pool.size() > 1) pool.remove(miningSpot);
            selected = pool.get(Rs2Random.between(0, pool.size()));
        }

        if (selected == null) {
            log.error("No mining area selected in config");
            Microbot.showMessage("MLM: Please select a mining area in the config.");
            return;
        }

        List<WorldPoint> anchorPoints = selected.getWorldPoint();

        if (anchorPoints == null || anchorPoints.isEmpty()) {
            log.error("Selected mining spot {} has no coordinates", selected);
            Microbot.showMessage("MLM: Invalid mining spot configuration.");
            return;
        }

        if (miningSpot != selected) {
            miningSession.invalidateUpperFloorCache();
            miningLevelChecked = false; // an upper area picked mid-run needs the 57 Mining check again
        }
        miningSpot = selected;
        List<WorldPoint> mutableAnchors = new java.util.ArrayList<>(anchorPoints);
        Collections.shuffle(mutableAnchors);

        WorldPoint newAnchor = mutableAnchors.get(0);
        if (currentAnchor != null && mutableAnchors.size() > 1) {
            for (WorldPoint wp : mutableAnchors) {
                if (!wp.equals(currentAnchor)) {
                    newAnchor = wp;
                    break;
                }
            }
        }
        currentAnchor = newAnchor;

        log.info("Selected mining spot: {} at anchor {}", miningSpot, currentAnchor);
        debug("[MLM] Spot selection complete — anchors available: {}", miningSpot != null ? miningSpot.getWorldPoint() : "none");
    }

    private boolean sackUnknownTooLong() {
        return sackUnknownSinceMs != 0L && System.currentTimeMillis() - sackUnknownSinceMs > SACK_UNKNOWN_ESCALATE_MS;
    }

    /** Where pay-dirt in hand sends an EMPTY_SACK: EMPTY_SACK means drop it and carry on. */
    static MLMStatus payDirtRoute(boolean sackFull, boolean sackUnknownTooLong) {
        if (sackFull) return MLMStatus.EMPTY_SACK;
        return sackUnknownTooLong ? MLMStatus.RECOVERY : MLMStatus.DEPOSIT_HOPPER;
    }

    /** Anything but a known empty sack, or a full inventory holding gems to bank. */
    static boolean sackTripNeeded(boolean oreHeld, int sackCount, boolean inventoryFull, boolean gemsHeld) {
        return oreHeld || sackCount != 0 || (inventoryFull && gemsHeld);
    }

    private void handleEmptySackStatus() {
        // GUARD: never empty sack while holding pay-dirt unless the sack itself is full.
        // If sack is full we *cannot* deposit pay-dirt (hopper rejects), so drop it instead
        // and proceed to empty. Otherwise deposit the pay-dirt first (deposit box won't take pay-dirt).
        if (payDirtCount() > 0) {
            MLMStatus route = payDirtRoute(sackState.definitelyFull(), sackUnknownTooLong());
            if (route == MLMStatus.RECOVERY) {
                // Can't deposit blind, and a trip can't read the sack either: keep the load, let recovery bound it.
                log.warn("[MLM] Sack count unreadable while holding {} pay-dirt — moving to recovery", payDirtCount());
                sackUnknownSinceMs = System.currentTimeMillis(); // the next round waits again
                setStatus(MLMStatus.RECOVERY);
                return;
            }
            if (route == MLMStatus.EMPTY_SACK) {
                log.info("[MLM] Pay-dirt ({}) during EMPTY_SACK but the sack is full — cannot deposit, dropping pay-dirt to proceed with empty",
                        payDirtCount());
                dropAllPayDirt();
                if (dropInProgress) return; // walking off now would split the pile
                if (payDirtCount() > 0) { // it would not drop, and the sack trip can't run with it
                    setStatus(MLMStatus.RECOVERY);
                    return;
                }
            } else {
                log.info("[MLM] Pay-dirt ({}) during EMPTY_SACK — depositing at hopper first before going down",
                        payDirtCount());
                setStatus(MLMStatus.DEPOSIT_HOPPER);
                return;
            }
        }

        boolean full = Rs2Inventory.isFull();
        if (sackTripNeeded(hasOreInInventory(), sackState.countOrUnknown(), full, hasGemsInInventory())) {
            // Small human pause to "decide it's time to go empty the sack" instead of instant transition.
            humanPause(200, 650);

            if (sackSession.isIdle() && canInteractWithLadder()) {
                lastLadderInteractionMs = System.currentTimeMillis();
                if (miningSpot == null) {
                    selectMiningSpotFromConfig();
                }
                // We are committing to emptying the sack now. The flag's effect is bypassed
                // in sackState.countOrUnknown() while emptying, so the live (decreasing) sack count is visible
                // to the sackSession (to prevent "stayed full at 189" infinite loop) and to the
                // overlay. The flag remains true in case the session fails/interrupts.
                sackSession.begin(miningSpot);
            } else if (sackSession.isIdle()) {
                // Cooldown active (or session busy); still move toward the sack so we don't idle
                // at a distant mining spot while the ladder throttle elapses.
                var anySack = Microbot.getRs2TileObjectCache().query()
                        .where(o -> o.getId() == ObjectID.MOTHERLODE_SACK
                                || o.getId() == ObjectID.MOTHERLODE_SACK_GRAPHIC)
                        .nearest();
                if (anySack != null) {
                    WorldPoint loc = anySack.getWorldLocation();
                    int d = Rs2Player.getWorldLocation() != null ? Rs2Player.getWorldLocation().distanceTo(loc) : 0;
                    if (d > 10) {
                        if (d > 15) Rs2Walker.walkTo(loc); else Rs2Walker.walkFastCanvas(loc);
                    }
                }
            }
        } else if (full) {
            // Nothing in the sack and nothing to bank: mining would send us straight back here. Stops next tick.
            sackSession.markInventoryClogged();
        } else {
            sackState.invalidate("sack emptied");
            setStatus(MLMStatus.IDLE);
        }
    }

    private void handleDepositHopperStatus() {
        // GUARD 1: Do we still need to deposit?
        if (!shouldDepositPayDirtAtHopper()) {
            log.info("[MLM] Aborting hopper deposit — inventory not full (pay-dirt={})", payDirtCount());
            hopperSession.reset();
            setStatus(MLMStatus.MINING);
            return;
        }

        // GUARD 2: Can we even deposit? (sack not full). Emptying the sack does NOT need the
        // waterwheel (struts only gate hopper→sack processing), so a full sack just routes to
        // EMPTY_SACK regardless of strut state.
        if ((sackState.definitelyFull()) && payDirtCount() > 0) {
            log.info("[MLM] Sack full — hopper cannot accept pay-dirt. Dropping {} pay-dirt to proceed to EMPTY_SACK.",
                    payDirtCount());
            dropAllPayDirt();
            setStatus(MLMStatus.EMPTY_SACK);
            return;
        }

        // It has already refused this load: only a turning wheel changes that.
        if (hopperRejectedFrozen && waterwheelNeedsRepair(tickRunningWheelCount, tickBrokenStrutCount)) {
            setStatus(config.repairStruts() && !repairBackingOff() ? MLMStatus.FIXING_WATERWHEEL : MLMStatus.WAITING_FOR_REPAIR);
            return;
        }

        // Depositing blind is what overfills a sack. The unknown-sack timer sends us to look instead.
        if (!sackState.isKnown()) {
            debug("[MLM] Sack count is unknown — holding off the hopper trip");
            return;
        }

        // (Repair is handled AFTER the deposit — see handlePostDepositAudit. Repairing before we've
        // emptied the pay-dirt would leave a full inventory that bounces us straight back here.)

        // Extra guard: if we've been retrying deposits and the hopper session
        // has exceeded its retry limit, something is wrong. Check if sack is full
        // as a last resort before entering infinite retry.
        if (hopperDepositRetries >= HopperSession.MAX_DEPOSIT_RETRIES - 1) {
            log.warn("[MLM] Deposit retries nearly exhausted — checking if sack is actually full");
            if (sackState.room().orElse(Integer.MAX_VALUE) <= 5) { // Within 5 of full
                log.info("[MLM] Sack near/full capacity detected on retry — routing to EMPTY_SACK");
                if (payDirtCount() > 0) {
                    dropAllPayDirt();
                }
                setStatus(MLMStatus.EMPTY_SACK);
                hopperSession.reset();
                return;
            }
        }

        if (hopperSession.isIdle() && canInteractWithLadder()) {
            lastLadderInteractionMs = System.currentTimeMillis();

            // Extra small human "deciding to go deposit" pause + the existing random checks.
            // The 5% pre-deposit linger + variance is human likeness only.
            humanPause(150, 450);

            if (isHumanLikeEnabled() && Rs2Random.between(0, 100) < 5) {
                debug("[MLM] Pre-deposit inventory check");
                sleep(Rs2Random.between(200, 500));
            }

            if (isHumanLikeEnabled()) {
                long variance = Rs2Random.between(-200, 400);
                if (variance > 0) {
                    sleep((int) variance);
                }
            }

            // No pre-deposit sack capture any more: nothing projects from it, and an orphaned
            // capture surviving a mid-deposit disconnect poisoned the *next* deposit's maths.
            lastHopperDepositMs = System.currentTimeMillis();
            hopperSession.begin(miningSpot);
        } else if (hopperSession.isIdle()) {
            // Cooldown active; move toward the (correct/desired floor) hopper target while waiting
            // so we don't stand idle far from the facility. Target chosen from config, not current Y
            // (prevents nudging toward lower when we actually want to use upstairs hopper).
            WorldPoint hopperTarget = hopperWalkTarget();
            var p = Rs2Player.getWorldLocation();
            int d = p != null ? p.distanceTo(hopperTarget) : 0;
            if (d > 10) {
                if (d > 15) Rs2Walker.walkTo(hopperTarget); else Rs2Walker.walkFastCanvas(hopperTarget);
            }
        }
    }

    /**
     * Wall-clock throttle between hopper/sack begin() calls (prevents ladder spam on the
     * 600ms executor). Jittered per check; shorter when human-like is off.
     */
    private long ladderInteractionCooldownMs() {
        return HumanBehaviorProfile.ladderInteractionCooldownMs(isHumanLikeEnabled());
    }

    private boolean canInteractWithLadder() {
        if (lastLadderInteractionMs == 0L) {
            return true;
        }
        long elapsed = System.currentTimeMillis() - lastLadderInteractionMs;
        long required = ladderInteractionCooldownMs();
        boolean ready = elapsed >= required;
        if (!ready && (status == MLMStatus.EMPTY_SACK || status == MLMStatus.DEPOSIT_HOPPER)) {
            debug("[MLM] Ladder cooldown active for {} — {}ms left", status, required - elapsed);
        }
        return ready;
    }

    /** A full sack or a hopper that already refused the load (no wheel turning) makes the trip pointless. */
    static boolean depositBeforeRepair(int payDirt, boolean sackFull, boolean rejectedFrozen) {
        return payDirt > 0 && !sackFull && !rejectedFrozen;
    }

    private void handleRepairStatus() {
        // 1-strut skip only when human-like enabled AND sack is not full (no urgency).
        // When the sack is full, even 1 broken strut blocks the hopper and must be fixed.
        boolean skipOneStrut = !sackState.definitelyFull() && !hopperRejectedFrozen
                && isHumanLikeEnabled() && personality.roll(personality.oneStrutSkipChance());
        if (tickBrokenStrutCount == 1 && skipOneStrut) {
            debug("[MLM] Skipping repair with 1 broken strut (90% chance) — only {} strut(s) broken", tickBrokenStrutCount);
            repairSession.reset();
            setStatus(determineNextStatusAfterRepair());
            return;
        }

        if (repairSession.isIdle()) {
            // Quick "notice the broken wheel and go" reaction — repair only happens right after a deposit
            // now, so it's an in-flow action, not something to dither over. Short with light randomness.
            humanPause(150, 550, true);
            debug("[MLM] Repair notice/orientation pause done");
        }

        boolean clear = isWaterwheelClear();
        log.debug("[MLM] handleRepairStatus: brokenStruts={}, clear={}, sessionIdle={}",
                tickBrokenStrutCount, clear, repairSession.isIdle());

        if (repairSession.canFastComplete()) {
            repairSession.reset();
            setStatus(determineNextStatusAfterRepair());
        } else if (!clear && repairSession.isIdle()) {
            // Someone is stood at the wheel. This check used to be computed for the log line above and
            // then thrown away, so the bot barged in and repaired alongside them every time. Park and
            // let them finish; MlmRepairEtiquette's patience window reclaims the job if they turn out
            // to be idle.
            log.info("[MLM] Another player is at the waterwheel ({}s) — waiting rather than repairing over them",
                    repairEtiquette.yieldedSeconds());
            setStatus(MLMStatus.WAITING_FOR_REPAIR);
        } else if (repairSession.isIdle()) {
            // Deposit pay-dirt before going down to repair, unless the hopper won't take it.
            if (depositBeforeRepair(payDirtCount(), sackState.definitelyFull(), hopperRejectedFrozen)) {
                log.info("[MLM] Pay-dirt ({}) before repair — depositing at hopper first", payDirtCount());
                setStatus(MLMStatus.DEPOSIT_HOPPER);
                return;
            }
            if (miningSpot == null) {
                selectMiningSpotFromConfig();
            }
            // RepairSession will handle: if no hammer, fetch from supply crate first (may drop pay-dirt
            // to make space), search crate, verify, then walk to strut(s), repair (1 or both randomly),
            // then cleanup drops hammer (if we fetched it), then we return here to decide next (deposit
            // any remaining pay from hammer fetch, or empty sack, or back to MINING + climb if upper).
            // We only get here when the wheel is clear, so the session may ignore anyone who wanders
            // up *after* our patience already ran out — otherwise it would back off again on arrival.
            log.info("[MLM] Starting repair session (post-deposit) — will fetch hammer from crate if needed");
            repairSession.begin(repairEtiquette.yieldExpired());
        }
    }

    private void handleDropGemsStatus() {
        dropGems();
        // Straight back to mining rather than via IDLE: dropping gems is a two-second housekeeping
        // task, not a reason to re-evaluate the whole run.
        setStatus(payDirtCount() > 0 && Rs2Inventory.isFull() ? MLMStatus.DEPOSIT_HOPPER : MLMStatus.MINING);
    }

    /** Something worked, so recovery starts counting again. */
    private void noteProgress() {
        recoveryAttempts.set(0);
        hardResets = 0;
        noProgressMs = 0L;
    }

    /** Stopped, or a tick of the last run still winding down: the stop interrupts it, which also ends its sleeps and walks. */
    private boolean stopped() {
        return !running || Thread.currentThread().isInterrupted();
    }

    private void handleRecoveryStatus() {
        if (System.currentTimeMillis() < recoveryWaitUntil) return;
        if (recoveryResumePending) {
            recoveryResumePending = false;
            setStatus(MLMStatus.MINING);
            return;
        }

        // Always ensure we have a spot selected — recovery must be able to drive us back to *any* configured
        // location (upper west tested; lower east/west, east upper, south spots, mid etc. all go through here).
        if (miningSpot == null) {
            selectMiningSpotFromConfig();
        }

        // Counted before the routes below: a trip that fails every time comes back here through them.
        int attempts = recoveryAttempts.incrementAndGet();
        log.info("[MLM] RECOVERY attempt {}/{} | lastStatus={} | sack={}/{} | paydirt={} | spot={}",
                attempts, MAX_RECOVERY_ATTEMPTS, lastLoggedStatus, sackState.countOrUnknown(), sackState.capacity(), payDirtCount(), miningSpot);
        boolean hardReset = attempts > MAX_RECOVERY_ATTEMPTS;
        if (hardReset && MlmRunControl.recoveryExhausted(hardResets)) {
            log.warn("[MLM] Still stuck after {} hard resets with no progress — stopping", hardResets);
            finish(STUCK_MESSAGE, true);
            return;
        }

        // Recovery: only route to hopper when pay-dirt must be cleared (full inv or sack full).
        // A few pieces from an interrupted mining walk should not trigger a deposit run.
        if (!hardReset && payDirtCount() > 0) {
            if (sackState.definitelyFull()) {
                log.info("[MLM] Recovery: pay-dirt but sack full — dropping pay-dirt then emptying sack");
                dropAllPayDirt();
                sackSession.reset();
                setStatus(MLMStatus.EMPTY_SACK);
                return;
            }
            if (Rs2Inventory.isFull()) {
                log.info("[MLM] Recovery: inventory full of pay-dirt — prioritizing DEPOSIT_HOPPER");
                hopperSession.reset();
                setStatus(MLMStatus.DEPOSIT_HOPPER);
                return;
            }
            debug("[MLM] Recovery: {} pay-dirt but inventory not full — continuing recovery (will resume mining)",
                    payDirtCount());
        }

        if (!hardReset && (sackState.definitelyFull() || hasOreInInventory())) {
            log.info("[MLM] Recovery: ores detected in inventory/sack — prioritizing EMPTY_SACK");
            sackSession.reset();
            setStatus(MLMStatus.EMPTY_SACK);
            return;
        }

        // On too many recoveries (any reason, any location: ladder fail, no veins, path stall, etc.) we nuke
        // transient state and force a clean cycle from a known safe point. Bounded: see recoveryExhausted.
        if (hardReset) {
            hardResets++;
            log.warn("[MLM] Exceeded {} recoveries — hard reset {}: re-selecting spot, clearing flags/blacklists, walking to safe, forcing MINING", MAX_RECOVERY_ATTEMPTS, hardResets);

            resetAllSessions();
            miningSpot = null;
            currentAnchor = null;
            sackState.invalidate("hard recovery");
            lastHopperDepositInitial = 0;
            // Note: crate blacklists live on the plugin; we don't clear here (small list, per-run intent).
            // A full plugin restart or future "reset crates" would clear them.
            recoveryAttempts.set(0);

            selectMiningSpotFromConfig();
            if (miningSpot == null) {
                // last resort default
                miningSpot = MLMMiningSpot.WEST_LOWER;
                currentAnchor = miningSpot.getWorldPoint().get(0);
            }

            Rs2Walker.setTarget(null, "mlm_route_reset");

            WorldPoint safe = recoverySafeWalkPoint(miningSpot);
            log.info("[MLM] Hard recovery: walking to safe {} for spot {}", safe, miningSpot);
            Rs2Walker.walkTo(safe, 6);
            recoveryWaitUntil = System.currentTimeMillis() + 8000L;
            int freshSack = sackState.countOrUnknown();
            if ((sackState.capacity() > 0 && freshSack >= sackState.capacity()) || hasOreInInventory()) {
                log.info("[MLM] Hard recovery found sack/ores still need clearing ({}/{}) — routing to EMPTY_SACK, not MINING",
                        freshSack, sackState.capacity());
                if (sackState.capacity() > 0 && freshSack >= sackState.capacity()) {
                    sackState.onSackIsFullChat(System.currentTimeMillis());
                }
                setStatus(MLMStatus.EMPTY_SACK);
                return;
            }
            setStatus(MLMStatus.MINING);
            return;
        }

        if (attempts == 1 && shouldDepositPayDirtAtHopper()
                && lastHopperDepositMs > 0
                && System.currentTimeMillis() - lastHopperDepositMs < 15000L) {
            debug("[MLM] Recovery: attempting hopper re-click before full recovery (inv full)");
            hopperSession.reset();
            setStatus(MLMStatus.DEPOSIT_HOPPER);
            return;
        }

        resetAllSessions();
        Rs2Walker.setTarget(null, "mlm_route_reset");

        if (attempts >= 2) {
            log.info("[MLM] Recovery attempt {} — walking to safe area (resolver for any location)", attempts);
            Rs2Walker.walkTo(recoverySafeWalkPoint(miningSpot), 3);
            recoveryWaitUntil = System.currentTimeMillis() + 5000L;
            recoveryResumePending = true; // stay in RECOVERY for the wait, then try mining again
            return;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isMoving() || Rs2Player.isInteracting()) {
            recoveryWaitUntil = System.currentTimeMillis() + 1200L;
            return;
        }

        recoveryWaitUntil = System.currentTimeMillis() + 600L;
        setStatus(MLMStatus.MINING);
    }

    // =========================================================================
    // Inventory & tools
    // =========================================================================

    /** Upper-level spots need 57 Mining; below that the ladder just refuses and the run loops on it. */
    private boolean hasMiningLevelForSpot() {
        MLMMiningSpot spot = miningSpot != null ? miningSpot : config.miningArea();
        if (spot == null || spot.isDownstairs()) {
            miningLevelChecked = true;
            return true;
        }
        int level = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getRealSkillLevel(Skill.MINING))
                .orElse(-1);
        if (level < 0) return true; // no reading yet: ask again next tick rather than guess
        miningLevelChecked = true;
        return level >= UPPER_FLOOR_MINING_LEVEL;
    }

    private boolean hasRequiredTools() {
        // Exact ids miss gilded, 3rd age, (or) and inactive crystal pickaxes, so fall back to the name.
        // Every variant is listed with its Mining level, wielded or carried.
        boolean usable = Pickaxe.hasItem();
        if (!usable) {
            debug("[MLM] No usable pickaxe found in inventory or equipment");
        }
        return usable;
    }

    private int payDirtCount() {
        return Rs2Inventory.itemQuantity(ItemID.PAYDIRT);
    }

    private boolean hasOreInInventory() {
        return Rs2Inventory.contains(
                ItemID.RUNITE_ORE, ItemID.ADAMANTITE_ORE, ItemID.MITHRIL_ORE,
                ItemID.GOLD_ORE, ItemID.COAL);
    }

    private boolean hasGemsInInventory() {
        return Rs2Inventory.contains(
                ItemID.UNCUT_SAPPHIRE, ItemID.UNCUT_EMERALD,
                ItemID.UNCUT_RUBY, ItemID.UNCUT_DIAMOND);
    }

    private void dropGems() {
        if (!hasGemsInInventory()) return;
        log.info("[MLM] Dropping gems to save space");
        humanPause(150, 450);
        int[] gems = {ItemID.UNCUT_SAPPHIRE, ItemID.UNCUT_EMERALD, ItemID.UNCUT_RUBY, ItemID.UNCUT_DIAMOND};
        for (int gem : gems) {
            // Attempt-cap + break on failure so a rejected click (open interface, etc.) can't spin
            // the executor thread forever. Mirrors dropAllPayDirt().
            int attempts = 0;
            while (!stopped() && Rs2Inventory.hasItem(gem) && attempts < 28) {
                if (!Rs2Inventory.interact(gem, "Drop")) {
                    log.warn("[MLM] Failed to drop gem {}, breaking drop loop", gem);
                    break;
                }
                attempts++;
                // Quick successive drops, not a per-item pause+cooldown that blocks the executor.
                if (isHumanLikeEnabled()) {
                    sleep(Rs2Random.between(60, 150));
                }
            }
        }
    }

    /**
     * After reclaiming dropped pay-dirt, send it to the hopper rather than carrying it back to the
     * veins. We are stood at the facility with the sack freshly emptied, so this is both the cheapest
     * and the most natural moment to deposit — walking back to mine with a part-load, then walking
     * here again, is neither.
     */
    private void routeReclaimedPayDirt() {
        if (payDirtCount() <= 0) {
            return;
        }
        if (sackState.definitelyFull()) {
            debug("[MLM] Reclaimed pay-dirt but the sack is still full — leaving routing to the audit");
            return;
        }
        log.info("[MLM] Reclaimed {} pay-dirt — depositing at the hopper before mining", payDirtCount());
        setStatus(MLMStatus.DEPOSIT_HOPPER);
    }

    private int queryBrokenStrutCount() {
        return Microbot.getRs2TileObjectCache().query()
                .withId(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN)
                .toList()
                .size();
    }

    /** Number of running (turning) waterwheels in scene. Water flows — and the hopper processes
     *  pay-dirt — as long as this is ≥1; it only freezes when both wheels are broken. */
    private int queryRunningWheelCount() {
        return Microbot.getRs2TileObjectCache().query()
                .withId(ObjectID.MOTHERLODE_WHEEL_FIXED)
                .toList()
                .size();
    }

    /** Our own repairs keep failing: separate from the wait for a bystander, which clears as soon as they leave. */
    private boolean repairBackingOff() {
        return System.currentTimeMillis() < repairBackoffUntilMs;
    }

    private boolean isWaterwheelClear() {
        return !repairEtiquette.shouldYield(config.waitForOthersToRepair(), localPlayerName);
    }

    /**
     * True once struts have been broken longer than {@link #REPAIR_DEFER_TIMEOUT_MS}. Used only as a
     * backstop for deposit thrash; the bystander decision now uses {@link MlmRepairEtiquette}'s own
     * clock, which starts when we begin yielding rather than when the strut broke.
     */
    private boolean repairDeferralExpired() {
        return brokenStrutsObservedSinceMs > 0L
                && System.currentTimeMillis() - brokenStrutsObservedSinceMs > REPAIR_DEFER_TIMEOUT_MS;
    }

    /**
     * True when we should repair struts before attempting to deposit.
     * Broken waterwheels block the hopper; if no other player is fixing them, we must.
     */
    private boolean shouldRepairStrutsBeforeDeposit() {
        return config.repairStruts()
                && waterwheelNeedsRepair(tickRunningWheelCount, tickBrokenStrutCount)
                && !repairBackingOff()
                && isWaterwheelClear();
    }

    /**
     * Pure repair decision: the hopper only stalls when the water freezes, which the OSRS mechanic
     * ties to <b>both</b> wheels being broken. So a repair is needed only when no wheel is running
     * (and there is a broken strut to actually click). One running wheel keeps pay-dirt flowing —
     * repairing then is wasted effort and jumps in on another player already fixing the broken wheel.
     */
    static boolean waterwheelNeedsRepair(int runningWheels, int brokenStruts) {
        return runningWheels == 0 && brokenStruts > 0;
    }

    /**
     * True when the wheel is frozen, we won't repair it ourselves ('Repair Struts' off), and the user
     * opted to wait for other players (optionally hopping). This is the signal to park in
     * {@link MLMStatus#WAITING_FOR_REPAIR} instead of thrashing failed deposits into RECOVERY.
     */
    private boolean shouldWaitForOthersToRepair() {
        // Frozen water and we are not repairing (repairs off, or yielding to someone at the wheel):
        // the hopper can't drain, so the only sane move is to wait. Retrying the hopper here looped
        // deposit -> recovery -> deposit forever.
        return waterwheelNeedsRepair(tickRunningWheelCount, tickBrokenStrutCount)
                && (!config.repairStruts() || repairBackingOff() || !isWaterwheelClear());
    }

    /**
     * Idle while the wheel is frozen and we're relying on other players to fix it (repairs off). Polls
     * each tick: the moment a wheel is running again (someone repaired it — repairs are world-wide) we
     * resume (depositing first if we're still holding pay-dirt). If 'Hop World When Frozen' is on and the
     * freeze outlasts the grace period, hop to the most-populated world (progress is saved across hops)
     * and re-evaluate on arrival.
     */
    private void handleWaitingForRepair() {
        if (tickRunningWheelCount > 0 || tickBrokenStrutCount == 0) {
            log.info("[MLM] Wheel running again — resuming from wait");
            setStatus(payDirtCount() > 0 ? MLMStatus.DEPOSIT_HOPPER : MLMStatus.MINING);
            return;
        }

        // If we are only waiting because someone was stood at the wheel, take the job back as soon as
        // the deferral times out — otherwise a bystander who never repairs parks us here forever.
        if (config.repairStruts() && !repairBackingOff() && isWaterwheelClear()) {
            log.info("[MLM] Wheel is free — taking the waterwheel job back");
            setStatus(MLMStatus.FIXING_WATERWHEEL);
            return;
        }

        long waitedMs = System.currentTimeMillis() - statusEnteredMs;

        if (config.hopWhenFrozen()
                && waitedMs >= Math.max(0L, config.frozenHopGraceSeconds()) * 1000L) {
            int target = Rs2WorldUtil.getMostPopulatedAccessibleWorld();
            if (target > 0 && target != Rs2Player.getWorld()) {
                log.info("[MLM] Frozen {}s with no repair — hopping to busy world {}", waitedMs / 1000, target);
                Microbot.hopToWorld(target);
                boolean hopped = sleepUntil(() -> Rs2Player.getWorld() == target, 8000);
                // Re-evaluate on the new world: restart the grace clock directly, because setStatus to
                // the same status is a no-op. A failed hop keeps the clock so it retries promptly.
                if (hopped) {
                    statusEnteredMs = System.currentTimeMillis();
                    repairBackoffUntilMs = 0L; // another world's wheel
                    repairBackoffRounds = 0;
                    droppedPayDirt.clear(); // left behind in the old world
                    hopperRejectedFrozen = false;
                }
            } else {
                log.warn("[MLM] Hop requested but no better world found — continuing to wait");
                sleep(2000);
            }
            return;
        }

        debug("[MLM] Waiting for another player to repair the wheel ({}s elapsed)", waitedMs / 1000);
        sleep(Rs2Random.between(2000, 4000));
    }

    /** Whether the off-hand Imcando hammer is wanted this run (repair + Imcando both on). */
    private boolean wantImcando() { return config.repairStruts() && config.useImcandoHammer(); }

    /** Gem bag enabled means the bag is always kept locked in slot 1, whatever the deposit method. */
    private boolean wantSlotLock() {
        return config.useGemBag();
    }

    /** Withdraw a gem bag from the already-open bank; safe-degrades to no-bag handling if none exists. */
    private void withdrawGemBag() {
        for (int id : Rs2Gembag.getGemBagItemIds()) {
            if (Rs2Bank.hasBankItem(id, 1)) {
                Rs2Bank.withdrawItem(id);
                sleepUntil(Rs2Gembag::hasGemBag, 3000);
                if (Rs2Gembag.hasGemBag()) { log.info("[MLM] Withdrew gem bag from the bank chest"); return; }
            }
        }
        log.warn("[MLM] Use Gem Bag is ON but no gem bag in inventory or bank — running without gem-bag handling this session");
    }

    /** Ensure the (already-present) gem bag is open so mined gems auto-store. Returns true when open. */
    private boolean ensureGemBagOpen() {
        if (Rs2Gembag.isGemBagOpen()) return true;
        Rs2Inventory.interact(net.runelite.api.gameval.ItemID.GEM_BAG, "Open");
        sleepUntil(Rs2Gembag::isGemBagOpen, 3000);
        return Rs2Gembag.isGemBagOpen();
    }

    /** Protected inventory slot index for the gem bag (slot "1" in the UI = index 0). */
    private static final int GEM_BAG_SLOT = 0;

    private Rs2ItemModel firstGemBagItem() {
        for (int id : Rs2Gembag.getGemBagItemIds()) {
            Rs2ItemModel m = Rs2Inventory.get(id);
            if (m != null) return m;
        }
        return null;
    }

    /**
     * True when the gem bag is in slot 0 AND slot 0 is locked, verified via the BANK_LOCKED_SLOTS varp
     * (works at the deposit box where the bank widget is absent).
     */
    private boolean isGemBagInSlotLocked() {
        Rs2ItemModel bag = firstGemBagItem();
        if (bag == null || bag.getSlot() != GEM_BAG_SLOT) return false;
        // The lock BIT is inert unless inventory-slot-locking is enabled, and a stale bit on its own is
        // enough for Deposit-All to bank the bag. Require both.
        if (!Rs2Settings.isBankSlotLockingEnabled()) return false;
        int mask = Microbot.getVarbitPlayerValue(VarPlayerID.BANK_LOCKED_SLOTS);
        return (mask & (1 << GEM_BAG_SLOT)) != 0;
    }

    private boolean imcandoResolvedToCrate = false;

    /** Open the MLM bank chest for the one preflight bank session. Returns true once open. */
    private boolean reachBankChest() {
        return Rs2Bank.isOpen() || Rs2Bank.walkToBankAndUseBank(BankLocation.MOTHERLOAD);
    }

    /** Withdraw an Imcando hammer from the already-open bank (off-hand preferred); wield happens later, post-close. */
    private void withdrawImcando() {
        final int OFF = ItemID.IMCANDO_HAMMER_OFFHAND, MAIN = ItemID.IMCANDO_HAMMER;
        if (Rs2Equipment.isWearing(OFF) || Rs2Equipment.isWearing(MAIN)
                || Rs2Inventory.hasItem(OFF) || Rs2Inventory.hasItem(MAIN)) return;
        int want = Rs2Bank.hasBankItem(OFF, 1) ? OFF : (Rs2Bank.hasBankItem(MAIN, 1) ? MAIN : -1);
        if (want == -1) {
            log.warn("[MLM] Use Imcando Hammer is ON but no Imcando hammer in inventory, equipment, or bank — will fall back to a regular hammer from the supply crate");
            return; // ensureImcandoWielded resolves the crate fallback
        }
        Rs2Bank.withdrawOne(want);
        int w = want;
        sleepUntil(() -> Rs2Inventory.hasItem(w), 2500);
    }

    /**
     * Wield the off-hand Imcando (deposit-safe shield slot). Bank must be CLOSED — you can't "Wield" a
     * bank-interface item, which is why this is a separate post-bank phase. Crate fallback if none.
     */
    private boolean ensureImcandoWielded() {
        if (!wantImcando()) { imcandoResolvedToCrate = false; return true; }
        final int OFF = ItemID.IMCANDO_HAMMER_OFFHAND, MAIN = ItemID.IMCANDO_HAMMER;
        boolean offEq = Rs2Equipment.isWearing(OFF), mainEq = Rs2Equipment.isWearing(MAIN);
        boolean offInv = Rs2Inventory.hasItem(OFF), mainInv = Rs2Inventory.hasItem(MAIN);
        MlmPreflightLogic.HammerStep step = MlmPreflightLogic.nextHammerStep(offEq, mainEq, offInv, mainInv, false);
        log.info("[MLM] Imcando wield: offEquip={} mainEquip={} offInv={} mainInv={} -> {}", offEq, mainEq, offInv, mainInv, step);
        switch (step) {
            case DONE:
                imcandoResolvedToCrate = false;
                return true;
            case WIELD_OFFHAND:
                Rs2Inventory.interact(OFF, "Wield");
                return sleepUntil(() -> Rs2Equipment.isWearing(OFF), 2500);
            case SWAP_THEN_WIELD:
                // Main-hand Imcando in inv → "Swap" converts it to the off-hand variant, then wield next tick.
                Rs2Inventory.interact(MAIN, "Swap");
                sleepUntil(() -> Rs2Inventory.hasItem(OFF), 2500);
                return false;
            case WITHDRAW:      // bank already closed in this phase — nothing to withdraw from
            case FALLBACK_CRATE:
            default:
                log.warn("[MLM] No Imcando hammer available — falling back to a regular hammer from the supply crate");
                imcandoResolvedToCrate = true;
                return true;
        }
    }

    /**
     * One-shot startup preflight. Establishes every invariant before mining and NEVER runs on the
     * per-tick path afterwards (that on-path slot-lock walk was the Deposit-All freeze). Safe-degrades
     * where a safe path exists; stops only when unworkable. Returns true when fully settled.
     */
    private boolean runPreflight() {
        // ---- Phase B (bank closed): settle inventory-side invariants and finish. ----
        if (preflightBankVisitDone) {
            // Not closed yet: try again next tick. The preflight time limit bounds it.
            if (Rs2Bank.isOpen() && !Rs2Bank.closeBank()) return false;
            if (!ensureImcandoWielded()) return false;                              // wield off-hand from inv
            if (config.useGemBag() && Rs2Gembag.hasGemBag() && !ensureGemBagOpen()) return false; // open the bag
            gemBagEnsured = true;
            resolveEffectiveDeposit();
            logResolvedState();
            return true;
        }

        // ---- Phase A (one bank session): withdraws + slot lock, one open, one close. Needs are read
        // from LIVE game state, so anything already present is skipped — no pointless bank trip. ----
        boolean needLock   = wantSlotLock() && !isGemBagInSlotLocked();
        boolean needSetup  = config.useInventorySetup() && config.inventorySetup() != null && !inventorySetupLoaded;
        boolean needPickaxe = !Pickaxe.hasItem();
        boolean wantBetterPickaxe = !needSetup && !needPickaxe && !pickaxeUpgradeChecked && betterPickaxeIsPossible();

        if (gemBagMissing() || imcandoMissing() || needLock || needSetup || needPickaxe || wantBetterPickaxe) {
            if (!reachBankChest()) return false;                    // walk/open across as many ticks as needed
            if (needSetup && !loadInventorySetup()) return false;   // stopped the plugin with a message
            // Asked again here: the setup may have banked or brought any of these.
            if (!Pickaxe.hasItem() || wantBetterPickaxe) takeBestPickaxeFromBank();
            if (gemBagMissing())  withdrawGemBag();                 // withdraws share the one open bank...
            if (imcandoMissing()) withdrawImcando();
            if (wantSlotLock() && !isGemBagInSlotLocked()) lockGemBagSlot();  // ...and so does the slot lock
        }
        preflightBankVisitDone = true;
        return false; // next tick: Phase B, which closes the bank first (required to wield)
    }

    private boolean gemBagMissing() {
        return config.useGemBag() && !Rs2Gembag.hasGemBag();
    }

    private boolean imcandoMissing() {
        return wantImcando()
                && !Rs2Equipment.isWearing(ItemID.IMCANDO_HAMMER_OFFHAND)
                && !Rs2Equipment.isWearing(ItemID.IMCANDO_HAMMER)   // equipped either variant survives Deposit-All
                && !Rs2Inventory.hasItem(ItemID.IMCANDO_HAMMER_OFFHAND)
                && !Rs2Inventory.hasItem(ItemID.IMCANDO_HAMMER);
    }

    /** True when a pickaxe above the carried one is within the player's Mining level, so the bank is worth a look. */
    private boolean betterPickaxeIsPossible() {
        Rs2ItemModel carried = Pickaxe.getBestPickaxe();
        if (carried == null) return false;
        boolean possible = Pickaxe.hasUsableBetterTier(carried.getId(),
                level -> Rs2Player.getSkillRequirement(Skill.MINING, level));
        if (!possible) pickaxeUpgradeChecked = true;
        return possible;
    }

    /**
     * With the bank open: take the best usable pickaxe if it beats what we carry (or we carry none).
     * Wielded when the Attack level allows, otherwise carried, since a pickaxe mines from the inventory.
     * The one it replaces goes back in the bank so it can't be swept by a deposit or take a slot.
     */
    private void takeBestPickaxeFromBank() {
        pickaxeUpgradeChecked = true;
        Rs2ItemModel carried = Pickaxe.getBestPickaxe();
        Rs2ItemModel banked = Pickaxe.getBestPickaxeFromBank();
        if (banked == null || (carried != null && !Pickaxe.isBetterPickaxe(banked.getId(), carried.getId()))) {
            if (carried == null) log.warn("[MLM] No pickaxe you can use in the bank either");
            return;
        }
        if (Rs2Inventory.isFull()) {
            log.warn("[MLM] Inventory full — can't take the {} from the bank", banked.getName());
            return;
        }
        int bankId = banked.getId();
        if (Pickaxe.canWield(bankId)) {
            Rs2Bank.withdrawAndEquip(bankId);
            sleepUntil(() -> Rs2Equipment.isWearing(bankId), 3000);
        } else {
            Rs2Bank.withdrawOne(bankId);
            sleepUntil(() -> Rs2Inventory.hasItem(bankId), 3000);
        }
        boolean got = Rs2Equipment.isWearing(bankId) || Rs2Inventory.hasItem(bankId);
        if (!got) {
            log.warn("[MLM] Could not take the {} from the bank", banked.getName());
            return;
        }
        log.info("[MLM] Took the {} from the bank{}", banked.getName(), carried != null ? " (better than the " + carried.getName() + ")" : "");
        if (carried != null && Rs2Inventory.hasItem(carried.getId())) {
            int oldId = carried.getId();
            Rs2Bank.depositOne(oldId);
            Rs2Inventory.waitForInventoryChanges(3000);
        }
    }

    /** Loads the chosen Inventory Setups loadout with the bank open; stops the plugin if it can't. */
    private boolean loadInventorySetup() {
        Rs2InventorySetup setup = new Rs2InventorySetup(config.inventorySetup(), mainScheduledFuture);
        boolean equipmentOk = setup.doesEquipmentMatch() || setup.loadEquipment();
        boolean inventoryOk = setup.doesInventoryMatch() || setup.loadInventory();
        if (!equipmentOk || !inventoryOk) {
            log.warn("[MLM] Inventory setup failed (equipment={}, inventory={}) — stopping", equipmentOk, inventoryOk);
            finish("MLM: couldn't load the inventory setup \"" + config.inventorySetup().getName()
                    + "\". Check it has a pickaxe and that the items are in your bank.", false);
            return false;
        }
        inventorySetupLoaded = true;
        pickaxeUpgradeChecked = true; // the setup decides the pickaxe
        log.info("[MLM] Inventory setup \"{}\" loaded", config.inventorySetup().getName());
        return true;
    }

    /** Resolve the effective (possibly degraded ALL→ITEMS) deposit method from the settled state. */
    private void resolveEffectiveDeposit() {
        boolean haveBag = config.useGemBag() && Rs2Gembag.hasGemBag();
        boolean lockEstablished = !wantSlotLock() || isGemBagInSlotLocked();
        effectiveDepositMethod = MlmPreflightLogic.resolveEffectiveDepositMethod(
                config.depositMethod(), haveBag, lockEstablished);
        if (config.depositMethod() == DepositMethod.ALL && effectiveDepositMethod == DepositMethod.ITEMS) {
            log.warn("[MLM] Preflight: Deposit-All unsafe for the gem bag (slot 1 not locked / not requested) — using Deposit-Items this run");
        }
    }

    /**
     * Move the gem bag to slot 0 and lock slot 0 (and enable inventory-slot-locking if off). Bank-phase
     * helper: assumes the bank is open and never opens/closes it — the caller owns the single session.
     * Locking needs the bank interface (the deposit box can't). Safe-degrades (leaves the lock off, which
     * resolveEffectiveDeposit reads as ITEMS) rather than failing when slot locking is unavailable.
     */
    private void lockGemBagSlot() {
        if (!Rs2Settings.isBankSlotLockingEnabled() && !Rs2Settings.enableBankSlotLocking()) {
            log.warn("[MLM] Bank slot locking unavailable — can't protect the gem bag; will use Deposit-Items");
            return;
        }
        Rs2ItemModel bag = firstGemBagItem();
        if (bag == null) return;
        if (bag.getSlot() != GEM_BAG_SLOT) {
            Rs2Inventory.moveItemToSlot(bag, GEM_BAG_SLOT);
            sleepUntil(() -> {
                Rs2ItemModel b = firstGemBagItem();
                return b != null && b.getSlot() == GEM_BAG_SLOT;
            }, 2000);
        }
        if (!Rs2Bank.isLockedSlot(GEM_BAG_SLOT)) {
            Rs2Bank.lockAllBySlot(GEM_BAG_SLOT);
            sleepUntil(() -> Rs2Bank.isLockedSlot(GEM_BAG_SLOT), 2000);
        }
        Rs2ItemModel after = firstGemBagItem();
        boolean varpLocked = isGemBagInSlotLocked();
        log.info("[MLM] Slot-lock attempt: bagSlot={} widgetLockedSlot0={} varpLocked={} -> {}",
                after != null ? after.getSlot() : -1, Rs2Bank.isLockedSlot(GEM_BAG_SLOT), varpLocked,
                varpLocked ? "LOCKED" : "NOT LOCKED (will degrade to Deposit-Items)");
    }

    /** One-shot INFO block: full config + resolved preflight state, so live reports are one glance. */
    private void logResolvedState() {
        int off = ItemID.IMCANDO_HAMMER_OFFHAND;
        int main = ItemID.IMCANDO_HAMMER;
        log.info("[MLM] ===== Preflight resolved (v{}) =====", IrkedMLMPlugin.version);
        log.info("[MLM] config: depositMethod={} useGemBag={} repairStruts={} useImcando={} logDetail={} stopAfter={}m stopAtLevel={} logout={}",
                config.depositMethod(), config.useGemBag(),
                config.repairStruts(), config.useImcandoHammer(), config.logDetail(),
                config.stopAfterMinutes(), config.stopAtLevel(), config.logoutWhenFinished());
        log.info("[MLM] state: imcando(offEquipped={} mainEquipped={} crateFallback={}) gemBag(present={} slot0Locked={}) effectiveDeposit={} freeSlots={}",
                Rs2Equipment.isWearing(off), Rs2Equipment.isWearing(main), imcandoResolvedToCrate,
                Rs2Gembag.hasGemBag(), isGemBagInSlotLocked(), effectiveDepositMethod,
                Rs2Inventory.emptySlotCount());
    }



    // =========================================================================
    // Snapshot & overlay
    // =========================================================================

    private void updateSnapshot() {
        try {
            // Play time: logged in and not paused, so a break spent in game does not count either.
            sessionStats.trackLoggedIn(Microbot.isLoggedIn() && pausedSinceMs == 0L, System.currentTimeMillis());
            if (!Microbot.isLoggedIn()) {
                // Avoid client thread calls at startup or when not ready; prevents TimeoutExceptions
                // that were triggering breakhandler dialogs and spamming errors.
                snapshotRef.set(SessionSnapshot.empty());
                return;
            }

            int currentXp = sessionStats.displayXp();

            SessionSnapshot snap = new SessionSnapshot(
                    status,
                    miningSpot,
                    // Observed count or UNKNOWN; the overlay renders "?" for the latter rather than a
                    // 0 that would read as an empty sack. SackState's reads are synchronized, so this
                    // is safe from the client thread (varbit/chat events) as well as the executor.
                    sackState.countOrUnknown(),
                    sackState.capacity(),
                    !sackState.isCapacityKnown(),
                    startTimeMs,
                    sessionStats.getTotalValueGained(),
                    sessionStats.getGainedNuggets(),
                    sessionStats.getStartXp(),
                    currentXp,
                    sessionStats.getRunite(),
                    sessionStats.getAdamantite(),
                    sessionStats.getMithril(),
                    sessionStats.getGold(),
                    sessionStats.getCoal(),
                    getCurrentSubStateLabel(),
                    statusEnteredMs,
                    globalLastXpTime.get()
            ).withActiveMs(sessionStats.activeMs(System.currentTimeMillis()))
             .withSacksEmptied(sessionSacksEmptied)
             .withOreCredits(sessionStats.oreCredits());

            snapshotRef.set(snap);
        } catch (Exception e) {
            log.error("[MLM] updateSnapshot crash — overlay will show stale data", e);
        }
    }

    public SessionSnapshot getSnapshot() {
        SessionSnapshot snap = snapshotRef.get();
        return snap != null ? snap : SessionSnapshot.empty();
    }

    /** Active spot for area overlay, else configured mining area. */
    public MLMMiningSpot getAreaOverlaySpot() {
        return miningSpot != null ? miningSpot : config.miningArea();
    }

    public java.util.Set<WorldPoint> getRememberedRockfallTiles() {
        return miningSession.getRememberedRockfallsView();
    }

    /** What the vein selector currently thinks it can walk to — drawn blue by the area overlay. */
    public java.util.Set<WorldPoint> getReachableTiles() {
        return miningSession.getReachableTilesView();
    }

    private String getCurrentSubStateLabel() {
        switch (status) {
            case MINING:            return miningSession.getPhaseLabel();
            case DEPOSIT_HOPPER:    return hopperSession.getPhaseLabel();
            case EMPTY_SACK:        return sackSession.getPhaseLabel();
            case FIXING_WATERWHEEL: return repairSession.getPhaseLabel();
            case WAITING_FOR_REPAIR: return config.hopWhenFrozen() ? "Waiting/Hopping (frozen)" : "Waiting for repair";
            case RECOVERY:          return "Recovering (" + recoveryAttempts.get() + ")";
            default:                return "Idle";
        }
    }

    // =========================================================================
    // Logging & utilities
    // =========================================================================

    /**
     * Single choke point for every status change. Logs the transition and — critically —
     * resets statusEnteredMs at the exact moment status changes, so the watchdog's dwell-time
     * measurement is always accurate no matter which handle* method (or early-return branch)
     * triggers the change. Direct {@code status = ...} assignment must never be used elsewhere.
     */
    private void setStatus(MLMStatus newStatus) {
        if (newStatus == status) return;
        log.info("MLM status transition: {} -> {} | sack={}/{} | paydirt={} | oresInInv={} | brokenStruts={}",
                status, newStatus,
                sackState.countOrUnknown(), sackState.capacity(),
                payDirtCount(),
                hasOreInInventory(),
                tickBrokenStrutCount);
        // Leaving the deposit loop (to mining/empty/repair/wait/recovery) clears the trip-retry count.
        // Staying in DEPOSIT_HOPPER can't reach here (the same-status guard above returns early), so the
        // count accumulates across retries exactly as the RECOVERY backstop expects.
        if (newStatus != MLMStatus.DEPOSIT_HOPPER) {
            hopperDepositRetries = 0;
        }
        status = newStatus;
        lastLoggedStatus = newStatus;
        statusEnteredMs = System.currentTimeMillis();
    }

    /**
     * Pull the camera back so the whole work area is on screen.
     *
     * <p>Not cosmetic: {@code ensureClickable()} turns the camera for any off-screen target, and
     * {@code turnTo} forces 3x speed and blocks up to 5s. Framed wide, it stops firing — and a strut
     * that will not frame cannot be repaired at all.
     *
     * <p>Once per run only. Must stay off the client thread: {@code setYaw} snaps instantly there.
     */
    private void frameCameraOnce() {
        if (cameraFramed) {
            return;
        }
        // Not on the first tick: a player gets going, then adjusts a few seconds later.
        if (cameraFrameDueMs == 0L) {
            cameraFrameDueMs = System.currentTimeMillis() + Rs2Random.between(4_000, 18_000);
            return;
        }
        if (System.currentTimeMillis() < cameraFrameDueMs) {
            return;
        }
        Session.Floor floor = Session.currentFloor();
        if (floor == Session.Floor.UNKNOWN) {
            return; // wait for a definite reading rather than framing blind
        }
        cameraFramed = true;
        try {
            // Lower value = further out; stepped like a scroll wheel.
            int from = Rs2Camera.getZoom();
            int targetZoom = Rs2Random.between(120, 200);
            int notches = Rs2Random.between(3, 5);
            for (int i = 1; i <= notches; i++) {
                Rs2Camera.setZoom(from + Math.round((targetZoom - from) * (i / (float) notches)));
                sleep(110, 190);
            }

            // adjustPitch holds the arrow key at the game's own speed; setPitch would whip via
            // smoothTo. Kept below 1.0 or it holds the key for the full 5s timeout.
            Rs2Camera.adjustPitch(Rs2Random.between(88, 96) / 100f);
            sleep(150, 300);

            // Near top-down and wide, yaw barely changes what is visible, so it can be random.
            Rs2Camera.setYaw(Rs2Random.between(0, 2047));

            log.info("[MLM] Camera framed on the {} floor (zoom {}, pitch {})",
                    floor, Rs2Camera.getZoom(), Rs2Camera.getPitch());
        } catch (Exception e) {
            log.warn("[MLM] Could not frame the camera: {}", e.getMessage());
        }
    }

    /** The camera is framed exactly once per run, a random few seconds in. */
    private boolean cameraFramed = false;
    private long cameraFrameDueMs = 0L;

    private void resetAllSessions() {
        miningSession.reset();
        hopperSession.reset();
        sackSession.reset();
        repairSession.reset();
        lastHopperDepositInitial = 0;
    }

    /** Persist lifetime totals as baseline + session-delta (idempotent — repeated saves don't double-count). */
    private void saveStats() {
        if (Microbot.getConfigManager() == null) return;
        MlmStats out = new MlmStats();
        out.oresMined        = stats.oresMined + sessionStats.totalOres();
        out.xpGained         = stats.xpGained + sessionStats.xpGained();
        out.runtimeMs        = stats.runtimeMs + Math.max(0L, System.currentTimeMillis() - startTimeMs);
        out.sacksEmptied     = stats.sacksEmptied + sessionSacksEmptied;
        out.nuggets          = stats.nuggets + sessionStats.getGainedNuggets(); // session gain, same baseline+delta as the rest

        out.lastSavedEpochMs = System.currentTimeMillis();
        out.loadoutUseImcando = config.useImcandoHammer();
        try {
            Microbot.getConfigManager().setConfiguration(IrkedMLMConfig.configGroup, STATS_KEY, out);
        } catch (Exception e) {
            log.warn("[MLM] Failed to persist stats", e);
        }
        // This session is saved as it goes, so a client crash still leaves it in the history.
        MlmHistory.Entry e = new MlmHistory.Entry();
        e.startMs  = startTimeMs;
        e.activeMs = sessionStats.activeMs(System.currentTimeMillis());
        e.xp       = sessionStats.xpGained();
        e.ores     = sessionStats.totalOres();
        e.nuggets  = sessionStats.getGainedNuggets();
        e.sacks    = sessionSacksEmptied;
        e.gp       = sessionStats.getTotalValueGained();
        e.reason   = stopReason;
        if (e.activeMs < 60_000 && e.xp == 0) return; // a start and stop is not a session
        history.upsert(e);
        try {
            Microbot.getConfigManager().setConfiguration(IrkedMLMConfig.configGroup, HISTORY_KEY, history);
        } catch (Exception ex) {
            log.warn("[MLM] Failed to save the session history", ex);
        }
    }

    /** Every plain setting on the client's antiban panel (switches, chances, durations), by field. */
    private static java.util.Map<java.lang.reflect.Field, Object> saveAntibanSettings() {
        java.util.Map<java.lang.reflect.Field, Object> out = new java.util.HashMap<>();
        for (java.lang.reflect.Field f : Rs2AntibanSettings.class.getDeclaredFields()) {
            int m = f.getModifiers();
            boolean plain = f.getType() == boolean.class || f.getType() == int.class || f.getType() == double.class;
            if (!java.lang.reflect.Modifier.isStatic(m) || java.lang.reflect.Modifier.isFinal(m)
                    || !java.lang.reflect.Modifier.isPublic(m) || !plain || ANTIBAN_RUNTIME.contains(f.getName())) continue;
            try {
                out.put(f, f.get(null));
            } catch (IllegalAccessException e) {
                log.debug("[MLM] Antiban setting {} not saved: {}", f.getName(), e.getMessage());
            }
        }
        return out;
    }

    private static void restoreAntibanSettings(java.util.Map<java.lang.reflect.Field, Object> saved) {
        for (java.util.Map.Entry<java.lang.reflect.Field, Object> e : saved.entrySet()) {
            try {
                e.getKey().set(null, e.getValue());
            } catch (IllegalAccessException ex) {
                log.debug("[MLM] Antiban setting {} not restored: {}", e.getKey().getName(), ex.getMessage());
            }
        }
    }

    /** Tells a waiting break handler it may go ahead; ignored when none has asked. */
    private void answerBreakRequest() {
        BreakPreparation.Handle handle = breakPrep;
        if (handle != null && handle.isRequested()) handle.ready();
    }

    /** Either break handler is mid-break or about to start one. Only an enabled one counts: a disabled handler's state is stale. */
    private static boolean breakActive() {
        return (Microbot.isPluginEnabled(BreakHandlerPlugin.class) && BreakHandlerScript.isBreakActive())
                || (Microbot.isPluginEnabled(BreakHandlerV2Plugin.class) && BreakHandlerV2State.isBreakActive());
    }

    /**
     * At the login screen. A disconnect or the 6-hour logout is undone; not a logout the player made
     * (the mouse or keys were in use just before), not during a break (the break handler logs back in)
     * and not when the Auto Login plugin is on (it does this itself).
     */
    private void logBackInWhenDue() {
        long now = System.currentTimeMillis();
        boolean autoLogin = Microbot.isPluginEnabled(AutoLoginPlugin.class);
        if (loggedOutSeenMs == 0L) {
            loggedOutSeenMs = now;
            playerLoggedOut = MlmRunControl.playerLoggedOut(everLoggedIn, lastHumanInputMs, now, PLAYER_LOGOUT_WINDOW_MS);
            // Started at the login screen: the player is waiting, so no pause to notice a logout.
            long wait = everLoggedIn ? MlmRunControl.reloginWaitMs(Math.random()) : Rs2Random.between(1500, 3000);
            nextLoginAt = now + wait;
            if (playerLoggedOut) log.info("[MLM] You logged out, so it is not logging back in");
            else if (autoLogin) log.info("[MLM] Logged out, the Auto Login plugin will log back in");
            else if (!breakActive()) log.info("[MLM] {} in about {}s",
                    everLoggedIn ? "Logged out, logging back in" : "At the login screen, logging in", wait / 1000);
        }
        // The player took the mouse or keys at the login screen: this logout is theirs now.
        if (!playerLoggedOut && InputArbiter.isHuman()) {
            playerLoggedOut = true;
            log.info("[MLM] You are using the login screen, so it is not logging back in");
        }
        if (playerLoggedOut || autoLogin || breakActive() || LoginManager.isLoginAttemptActive() || now < nextLoginAt) return;
        if (LoginManager.getActiveProfile() == null) {
            finish("MLM: logged out, and there is no login profile to log back in with. Set one up in the Microbot profile panel.", false);
            return;
        }
        if (loginAttempts >= MAX_LOGIN_ATTEMPTS) {
            finish("MLM: could not log back in after " + MAX_LOGIN_ATTEMPTS + " tries.", false);
            return;
        }
        loginAttempts++;
        log.info("[MLM] Logging back in (try {} of {})", loginAttempts, MAX_LOGIN_ATTEMPTS);
        LoginManager.login();
        nextLoginAt = now + MlmRunControl.reloginGapMs(loginAttempts, Math.random());
    }

    /**
     * Ends the run. {@code mayLogout} is false for set-up stops (a level, a setting, a loadout): the player is
     * at the keyboard and only has a setting to change. Other stops log out when Log out when finished is on.
     */
    private void finish(String message, boolean mayLogout) {
        if (stopped()) return; // already stopped: this could end the run after it
        stopReason = message;
        log.info("[MLM] Stopped: {} (played {}, {} xp, {} ores)", message,
                IrkedMLMOverlay.hoursMinutes(sessionStats.activeMs(System.currentTimeMillis())),
                sessionStats.xpGained(), sessionStats.totalOres());
        running = false;
        if (mayLogout && config.logoutWhenFinished()) logOutLikeAPlayer();
        Microbot.showMessage(message, 8000);
        Microbot.stopPlugin(plugin);
    }

    /**
     * The client's logout sends its click from a random point anywhere on the canvas, straight after the
     * last action. Take a moment, as a player would, then click the logout button itself.
     */
    private void logOutLikeAPlayer() {
        if (!Microbot.isLoggedIn()) return;
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
        }
        sleep(Rs2Random.between(2_500, 12_000));
        if (Thread.currentThread().isInterrupted()) return; // the player stopped the plugin meanwhile
        // The client drops our clicks while the player has the mouse.
        if (InputArbiter.isHuman()) {
            log.info("[MLM] You have the mouse, so not logging out");
            return;
        }
        log.info("[MLM] Logging out");
        Microbot.getEventBus().post(new LogoutRequested()); // tells the client's auto-login this is meant
        Rs2Keyboard.resetEnter();
        Rs2Tab.switchTo(InterfaceTab.LOGOUT);
        sleep(Rs2Random.between(400, 900));
        if (Rs2Widget.isWidgetVisible(LOGOUT_GROUP, LOGOUT_BUTTON_CHILD)
                && Rs2Widget.clickWidget(LOGOUT_GROUP, LOGOUT_BUTTON_CHILD)
                && sleepUntil(() -> !Microbot.isLoggedIn(), 4000)) {
            return;
        }
        // World switcher showing, or the click did not take: the client's way still logs out.
        Rs2Player.logout();
    }

    @Override
    public void shutdown() {
        log.info("Starting MLM script shutdown");
        running = false;
        if (stopReason == null) stopReason = "stopped";
        super.shutdown();
        BreakHandlerScript.setLockState(false);
        Rs2Walker.setTarget(null, "mlm_route_reset");
        tearDownPending.set(true);
        runPendingTearDown();
    }

    /**
     * Saves and puts everything back once no tick is running. A tick can outlive the stop (a walk, a wait),
     * and this is never waited for: if one is running, it calls this again on its way out.
     */
    private void runPendingTearDown() {
        if (!tearDownPending.get() || !executing.compareAndSet(false, true)) return;
        try {
            if (tearDownPending.getAndSet(false)) tearDown();
        } finally {
            executing.set(false);
        }
    }

    private void tearDown() {
        if (!initialised) return; // stopped before its first tick: nothing to save or put back
        initialised = false;
        saveStats();
        BreakHandlerScript.setLockState(false);
        if (breakPrep != null) {
            breakPrep.close();
            breakPrep = null;
        }
        Rs2Antiban.resetAntibanSettings();
        if (savedAntiban != null && !Rs2AntibanSettings.overwriteScriptSettings) restoreAntibanSettings(savedAntiban);
        savedAntiban = null;
        Rs2Walker.setTarget(null, "mlm_route_reset");
        resetAllSessions();
        log.info("MLM script shutdown complete");
    }
}
