package net.runelite.client.plugins.microbot.anvilsmither;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.anvilsmither.enums.AnvilItem;
import net.runelite.client.plugins.microbot.anvilsmither.enums.AnvilLocation;
import net.runelite.client.plugins.microbot.anvilsmither.enums.Bars;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.breakhandler.BreakPreparation;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2Plugin;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2State;
import net.runelite.client.plugins.microbot.accountselector.AutoLoginPlugin;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.inventory.RunePouchType;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Staff;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.util.Text;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.player.LogoutRequested;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AnvilSmitherScript extends Script {

    enum State {
        DETERMINE,
        BANKING,
        SMITHING,
        ALCHING,
        RECOVERY
    }

    private static final int ANVIL_WIDGET_GROUP = InterfaceID.Smithing.MAKE_ALL >>> 16;   // 312
    private static final int BANK_WIDGET_GROUP = InterfaceID.Bankmain.QUANTITYX >>> 16;   // 12
    private static final int BANK_QUANTITY_X_CHILD = InterfaceID.Bankmain.QUANTITYX & 0xFFFF;   // 35
    private static final int BANK_QUANTITY_X = 3;          // BANK_QUANTITY_TYPE value for the X button
    private static final int LAST_BAR_VARBIT = VarbitID.SMITHING_BAR_LASTTYPE;     // 13894
    private static final int LAST_ITEM_VARBIT = VarbitID.SMITHING_ITEM_LASTTYPE;   // 13895
    private static final int ANVIL_WIDGET_ALL_CHILD = InterfaceID.Smithing.MAKE_ALL & 0xFFFF;   // 7
    private static final int LOGOUT_GROUP = InterfaceID.Logout.LOGOUT >>> 16;          // the red logout button is 182:8
    private static final int LOGOUT_BUTTON_CHILD = InterfaceID.Logout.LOGOUT & 0xFFFF;

    private static final int WIDGET_OPEN_TIMEOUT = 8000;
    private static final int BANK_TIMEOUT = 3500;

    private static final int MAKE_X_QUANTITY_VARP = VarPlayerID.MAKEXCRAFTING;   // 2224

    // Without a cap, an impossible task loops DETERMINE->SMITHING->RECOVERY forever.
    private static final int MAX_CONSECUTIVE_RECOVERIES = 5;
    // Casts in a row that gave no Magic XP, or trips that brought nothing back to alch.
    private static final int MAX_ALCH_MISSES = 5;
    // High Alchemy recasts every 5 ticks (OSRS Wiki): item clicks are spaced just past this.
    private static final long ALCH_COOLDOWN_MS = 3000;

    // An inventory of 27 one-bar items is 81 seconds, so SMITHING legitimately runs over a minute.
    private static final long STATE_WATCHDOG_MS = 240_000L;

    // Logging back in: gaps grow with each failed try; about eight minutes in all before it gives up.
    private static final int MAX_LOGIN_ATTEMPTS = 10;
    // Real mouse or keys this recently before the login screen: the player logged out themselves.
    private static final long PLAYER_LOGOUT_WINDOW_MS = 5_000;

    // Quest progress loads a moment after login; past this the start-up checks are not going to pass.
    private static final long PREFLIGHT_LIMIT_MS = 30_000L;

    /**
     * How often the script looks at the world. Pacing does not come from this: it comes from
     * {@link #react(int)} before each action. Polling slowly does not look human, it just looks slow.
     */
    private static final long TICK_PERIOD_MS = 200;

    // Both ends on screen means an object click never has to turn the camera.
    // Legacy yaw: North 0/2048, East 1536, South 1024, West 512.
    private static final int YAW_NORTH = 0;
    private static final int YAW_EAST = 1536;
    private static final int YAW_SOUTH = 1024;
    private static final int YAW_WEST = 512;
    // Same numbers as Herbi Hunter, which framed well live: pull back to 100-125 when above 140.
    private static final int MIN_ZOOM = 100;          // furthest the client usefully pulls back
    private static final int ZOOMED_OUT = 140;        // at or below this the view counts as pulled back
    private static final int MAX_PITCH = 383;         // straight down
    private static final int MAX_FRAMING_STEPS = 4;

    // A repaired Imcando hammer smiths equipped or not, in either variant. The broken one (25633)
    // is deliberately absent: it does not work until repaired.
    public static final int[] HAMMER_IDS = {
            ItemID.HAMMER,
            ItemID.IMCANDO_HAMMER,
            ItemID.IMCANDO_HAMMER_OFFHAND
    };

    private static final int WALK_FAST_CANVAS_DISTANCE = 15;
    private static final int ANVIL_INTERACT_DISTANCE = 12;
    private static final int BANK_INTERACT_DISTANCE = 12;
    private static final int OBJECT_SEARCH_RADIUS = 15;

    /** What the inventory is filled with: bars by id, or keel parts by exact name for large keels. */
    private static class MatContext {
        final String name;
        final boolean useName;
        final int id;

        MatContext(String name, boolean useName, int id) {
            this.name = name;
            this.useName = useName;
            this.id = id;
        }
    }

    private static class ScriptContext {
        volatile long lastMakeStarted;   // also read by the overlay
        boolean selectionComplete;
        String lastSelectedItem;
        long smithingStartTime;
        volatile int maxFullItems;
        int materialsAtStart;
        long makeSessionTimeoutMs;
        boolean progressSeen;
        long idleMoveAtMs;
        boolean cursorLeftWindow;

        void resetSession() {
            // Only the make-session timing: the remembered selection is what makes space-repeat work.
            lastMakeStarted = 0;
            smithingStartTime = 0;
            maxFullItems = 0;
            makeSessionTimeoutMs = 0;
            idleMoveAtMs = 0;
        }

        void resetAll() {
            resetSession();
            selectionComplete = false;
            lastSelectedItem = "";
        }

        void startSession(int maxItems, int materials, long idleMoveAt) {
            materialsAtStart = materials;
            maxFullItems = maxItems;
            makeSessionTimeoutMs = (maxItems * 4000L) + 15000L;
            lastMakeStarted = System.currentTimeMillis();
            smithingStartTime = lastMakeStarted;
            progressSeen = false;
            cursorLeftWindow = false;
            idleMoveAtMs = idleMoveAt;
        }
    }

    @Getter
    private volatile State state = State.DETERMINE;
    private volatile boolean logout = true;
    private volatile long currentTaskId = 0;

    private AnvilSmitherConfig config;
    private final ScriptContext ctx = new ScriptContext();
    private final Random rng = new Random();
    private AnvilHabits habits = AnvilHabits.draw(rng);

    // Set once per run from the configured location.
    private WorldPoint anvilLocation;
    private WorldPoint bankLocation;

    private int consecutiveRecoveries = 0;
    private String lastLogged = "";
    private boolean wasLoggedIn = false;
    private boolean preflightDone = false;
    private long preflightStartedMs = 0;
    private long stateEnteredMs = 0;
    private boolean cameraSettled = false;
    private int framedZoom;
    private AnvilLocation activeLocation;
    /** Failed banking sub-steps in a row; a few send it to RECOVERY instead of looping for minutes. */
    private int bankFailures = 0;
    private static final int MAX_BANK_FAILURES = 6;
    /** The last detail line (logged or not) and the one that led into RECOVERY, for the give-up message. */
    private volatile String lastDetail;
    private volatile String lastFailure;
    /** The player's mouse settings before start: naturalMouse, simulateFatigue, simulateMistakes, moveMouseRandomly, moveMouseOffScreen, takeMicroBreaks. */
    private boolean[] savedAntiban;
    // The recipe in use. Settings are only adopted between loads, so one load is one recipe.
    private volatile Bars activeBar;
    private volatile AnvilItem activeItem;
    private boolean antibanConfigured;
    private int runBackOnAt = 5;
    private int xpReadAttempts = 0;
    private final AtomicBoolean executing = new AtomicBoolean(false);
    // Breaks: both break handlers ask through this before they pause or log out a script.
    private BreakPreparation.Handle breakPrep;
    private volatile boolean waitingForBreak;
    // Logging back in after a disconnect or the 6-hour logout.
    private long loggedOutSeenMs = 0;   // 0 while in game
    private long nextLoginAt = 0;
    private int loginAttempts = 0;
    private boolean playerLoggedOut = false;
    private boolean everLoggedIn = false;   // this run; started at the login screen, the toggle click is not a logout
    private long lastHumanInputMs = 0;

    private volatile long startTime = 0;
    private volatile int startXp = -1;   // -1 == not captured yet
    private final AtomicInteger itemsMade = new AtomicInteger(0);
    // Panel data: {time, items} per finished load, and materials left as last seen with the bank open.
    private final List<long[]> credits = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile int materialsLeft = -1;   // -1 == bank not seen yet
    // What this run made, by the game's own item name: the alch stage takes these back out as notes.
    private final Set<String> madeNames = new LinkedHashSet<>();
    private Set<Integer> idsBeforeLoad = Collections.emptySet();
    private volatile boolean alching = false;
    private int alchSlotTries = 0;
    private volatile int alchCasts = 0;
    private int alchMisses = 0;
    private long lastAlchClickMs = 0;
    private double pressLatencyMs = 0;   // what the client's click call takes, learned as it goes
    // Panel data for the alch stage, the counterpart of credits and materialsLeft.
    private final List<long[]> alchCredits = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile long alchStartMs = 0;
    private volatile int alchStartXp = 0;
    private volatile int alchLeft = -1;
    private volatile String alchName = "";
    @Getter
    private volatile boolean running = false;

    public boolean run(AnvilSmitherConfig config) {
        if (mainScheduledFuture != null) {
            mainScheduledFuture.cancel(true);
            mainScheduledFuture = null;
        }

        currentTaskId = System.currentTimeMillis();
        long myTaskId = currentTaskId;

        this.config = config;
        AnvilLocation location = config.sLocation();
        activeLocation = location;
        anvilLocation = location.getAnvil();
        bankLocation = location.getBank();
        logout = config.sLogout();
        state = State.DETERMINE;
        lastLogged = "";
        wasLoggedIn = false;
        preflightDone = false;
        preflightStartedMs = 0;
        cameraSettled = false;
        xpReadAttempts = 0;
        consecutiveRecoveries = 0;
        stateEnteredMs = 0;
        startXp = -1;
        startTime = 0;
        framedZoom = 0;
        bankFailures = 0;
        lastDetail = null;
        lastFailure = null;
        ctx.resetAll();

        String rejection = rejectionReason(config.sBarType(), config.sAnvilItem());
        if (rejection != null) {
            Microbot.log("AnvilSmither cannot start: " + rejection);
            Microbot.showMessage(rejection);
            return false;
        }

        activeBar = config.sBarType();
        activeItem = config.sAnvilItem();
        itemsMade.set(0);
        credits.clear();
        materialsLeft = -1;
        madeNames.clear();
        alching = false;
        alchCasts = 0;
        alchMisses = 0;
        alchCredits.clear();
        alchStartMs = 0;
        alchLeft = -1;
        running = true;
        habits = AnvilHabits.draw(rng);
        loggedOutSeenMs = 0;
        nextLoginAt = 0;
        loginAttempts = 0;
        playerLoggedOut = false;
        everLoggedIn = false;
        waitingForBreak = false;
        // Any earlier script may have left the lock on, and then no break would ever start.
        BreakHandlerScript.setLockState(false);
        if (breakPrep != null) breakPrep.close();
        breakPrep = BreakPreparation.register("anvil-smither");

        // Put back exactly these at stop, so the next plugin finds the player's own settings.
        savedAntiban = new boolean[]{Rs2AntibanSettings.naturalMouse, Rs2AntibanSettings.simulateFatigue,
                Rs2AntibanSettings.simulateMistakes, Rs2AntibanSettings.moveMouseRandomly,
                Rs2AntibanSettings.moveMouseOffScreen, Rs2AntibanSettings.takeMicroBreaks};
        // "Overwrite script settings" on the Antiban panel means the player's settings win: leave them.
        if (!Rs2AntibanSettings.overwriteScriptSettings) {
            // The Antiban panel's micro-break switch is the player's choice; everything else starts clean.
            boolean microBreaks = Rs2AntibanSettings.takeMicroBreaks;
            Rs2Antiban.resetAntibanSettings();
            Rs2AntibanSettings.takeMicroBreaks = microBreaks;
            Rs2AntibanSettings.naturalMouse = true;
            Rs2AntibanSettings.simulateFatigue = true;
            // As Herbi Hunter: our pace owns the mouse. The client's slips, random moves and off-screen
            // trips fight it; with mistakes on, the cursor visibly missed the booth and the anvil live.
            Rs2AntibanSettings.simulateMistakes = false;
            Rs2AntibanSettings.moveMouseRandomly = false;
            Rs2AntibanSettings.moveMouseOffScreen = false;
        }
        Rs2Antiban.setActivity(Activity.GENERAL_SMITHING);
        antibanConfigured = true;

        info("Started: " + effectiveItemName(config.sAnvilItem(), config.sBarType()) + " from "
                + config.sBarType() + " at " + location);
        // describe() doubles its percent signs because Microbot.log treats the line as a format string.
        info("This session: " + habits.describe());

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (myTaskId != currentTaskId) return;

            // A tick can block for seconds; never let two drive the interface at once.
            if (!executing.compareAndSet(false, true)) return;
            try {
                doTick(myTaskId);
            } catch (Exception e) {
                Microbot.log("AnvilSmither tick failed: " + e);
                Microbot.logStackTrace("AnvilSmither", e);
            } finally {
                executing.set(false);
                // What the client reports it is doing (the launcher's Clients view shows it). The click helpers
                // write their own text here and nothing clears it, so an old "Travel Spirit tree" would stay up.
                // Not after a stop: shutdown has already set it to idle.
                if (running && myTaskId == currentTaskId) Microbot.status = getActivity();
            }
        }, 0, TICK_PERIOD_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    private void doTick(long myTaskId) {
        // Read before the gate below: it holds every script while the player has the mouse.
        if (InputArbiter.isHuman()) lastHumanInputMs = System.currentTimeMillis();
        // Logged out comes before the client's gate: since core #2302 that gate returns false whenever
        // the player is logged out, which left the relog below unreachable.
        if (!Microbot.isLoggedIn() || Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) {
            wasLoggedIn = false;
            BreakHandlerScript.setLockState(false);   // no load is being hammered while logged out
            holdClocks();
            answerBreakRequest();   // logged out is always a safe place for a break
            if (!Microbot.pauseAllScripts.get() && Microbot.getClient() != null
                    && Microbot.getClient().getGameState() == GameState.LOGIN_SCREEN) {
                logBackInWhenDue();
            }
            return;
        }

        // A load being hammered is finished before any break: a player does not log out mid-load.
        boolean midLoad = state == State.SMITHING && ctx.lastMakeStarted > 0;
        BreakHandlerScript.setLockState(midLoad);

        // The client's own gate: Break Handler pauses, the pause button, a player taking the mouse,
        // the welcome screen. Time spent held here is not time spent stuck.
        if (!super.run() || Rs2AntibanSettings.microBreakActive) {
            holdClocks();
            if (!midLoad) answerBreakRequest();
            return;
        }
        loggedOutSeenMs = 0;
        loginAttempts = 0;
        playerLoggedOut = false;
        everLoggedIn = true;

        // The client's welcome-screen handler clicks Play. It looks every few seconds, so the first
        // ticks in game can come before it does: nothing of ours may click behind the screen.
        if (Rs2Widget.isWidgetVisible(InterfaceID.WelcomeScreen.PLAY)) {
            holdClocks();
            return;
        }

        // A break asked for, or under way: answer once the load is done, then do nothing until it is over.
        boolean breakAsked = (breakPrep != null && breakPrep.isRequested()) || breakActive();
        if (holdsForBreak(breakAsked, midLoad)) {
            answerBreakRequest();
            if (!waitingForBreak) info("Break due, waiting for the break handler");
            waitingForBreak = true;
            holdClocks();
            return;
        }
        waitingForBreak = false;

        // Coming back from a disconnect, logout or world hop: everything remembered about the
        // in-flight make session is now a guess. Re-derive it from what the world actually shows.
        if (!wasLoggedIn) {
            wasLoggedIn = true;
            if (ctx.lastMakeStarted > 0 || state != State.DETERMINE) {
                info("Back online, checking everything again from scratch");
            }
            ctx.resetAll();
            consecutiveRecoveries = 0;
            preflightDone = false;
            preflightStartedMs = 0;
            cameraSettled = false;
            state = State.DETERMINE;
        }

        if (startXp < 0) {
            Integer xp = Microbot.getClientThread().invoke(() -> Microbot.getClient().getSkillExperience(Skill.SMITHING));
            // Accept a genuine 0 once a few ticks have passed: a fresh account has no Smithing XP.
            if (xp != null && (xp > 0 || ++xpReadAttempts >= 5)) {
                startXp = xp;
                startTime = System.currentTimeMillis();
            }
        }

        if (!cameraSettled) {
            settleCamera();
        }

        // Establish a known-good starting state once: no stale dialogue, quests and level are
        // sufficient, and any carried Imcando hammer is worn rather than occupying a slot.
        if (!preflightDone && !runPreflight()) {
            return;
        }

        // Nothing has changed state for a long time.
        if (state != State.RECOVERY && stateEnteredMs > 0
                && System.currentTimeMillis() - stateEnteredMs > STATE_WATCHDOG_MS) {
            info("Stuck in " + state + " for " + (STATE_WATCHDOG_MS / 60_000) + " minutes, recovering");
            ctx.resetSession();
            setState(State.RECOVERY);
            return;
        }

        if (Rs2Dialogue.hasContinue()) {
            react(500);
            Rs2Dialogue.clickContinue();
            return;
        }

        try {
            State next = tick(state, activeBar, activeItem);
            // A tick blocks for seconds; one that outlived its run must not write into the next.
            if (myTaskId != currentTaskId) return;
            if (next == State.RECOVERY && state != State.RECOVERY) lastFailure = lastDetail;
            if (next != State.BANKING && next != State.RECOVERY) bankFailures = 0;
            setState(next);
        } catch (Exception e) {
            Microbot.log("AnvilSmither tick crashed in " + state + ": " + e);
            Microbot.logStackTrace("AnvilSmither", e);
            lastFailure = "an error in " + state + ": " + e;
            ctx.resetSession();
            setState(State.RECOVERY);
        }
    }

    /** Time held for a break, a pause or a screen is not time spent stuck, nor failing to get ready. */
    private void holdClocks() {
        long now = System.currentTimeMillis();
        if (stateEnteredMs > 0) stateEnteredMs = now;
        if (preflightStartedMs > 0) preflightStartedMs = now;
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

    /** A break waits for the load on the anvil; otherwise it is taken now. Pure. */
    static boolean holdsForBreak(boolean breakAsked, boolean midLoad) {
        return breakAsked && !midLoad;
    }

    /** How long a player takes to notice the login screen: five to forty seconds, mostly short. Pure. */
    static long reloginWaitMs(double roll) {
        return 5_000 + (long) (roll * roll * 35_000);
    }

    /** The gap after a failed login, longer each try: a server down for an update is not hammered. Pure. */
    static long reloginGapMs(int attempt, double roll) {
        return (long) ((8_000 + roll * 7_000) * Math.max(1, Math.min(attempt, 8)));
    }

    /**
     * At the login screen. A disconnect or the 6-hour logout is undone; not a logout the player made
     * (the mouse or keys were in use just before), not during a break (the break handler logs back in)
     * and not when the Auto Login plugin is on (it does this itself).
     */
    private void logBackInWhenDue() {
        long now = System.currentTimeMillis();
        boolean autoLogin = Microbot.isPluginEnabled(AutoLoginPlugin.class);
        if (loggedOutSeenMs == 0) {
            loggedOutSeenMs = now;
            playerLoggedOut = everLoggedIn && lastHumanInputMs > 0 && now - lastHumanInputMs < PLAYER_LOGOUT_WINDOW_MS;
            // Turned on at the login screen: the player is waiting, so no pause to notice a logout.
            long wait = everLoggedIn ? reloginWaitMs(rng.nextDouble()) : Rs2Random.between(1500, 3000);
            nextLoginAt = now + wait;
            if (playerLoggedOut) info("You logged out, so it is not logging back in");
            else if (autoLogin) info("Logged out, the Auto Login plugin will log back in");
            else if (!breakActive()) info((everLoggedIn ? "Logged out, logging back in" : "At the login screen, logging in") + " in about " + wait / 1000 + "s");
        }
        if (playerLoggedOut || autoLogin || breakActive() || LoginManager.isLoginAttemptActive() || now < nextLoginAt) return;
        if (LoginManager.getActiveProfile() == null) {
            stop("Logged out, and there is no login profile to log back in with. Set one up in the Microbot profile panel.", false);
            return;
        }
        if (loginAttempts >= MAX_LOGIN_ATTEMPTS) {
            stop("Could not log back in after " + MAX_LOGIN_ATTEMPTS + " tries.", false);
            return;
        }
        loginAttempts++;
        info("Logging back in (try " + loginAttempts + " of " + MAX_LOGIN_ATTEMPTS + ")");
        LoginManager.login();
        nextLoginAt = now + reloginGapMs(loginAttempts, rng.nextDouble());
    }

    /**
     * Frame bank and anvil together. Every object click turns the camera when its target is off
     * screen, so a zoomed-in camera snapped round on every leg of the walk; framed wide it never
     * has to. Must stay off the client thread: smoothTo() degrades to an instant jump there.
     */
    private void settleCamera() {
        // Only once we are at the work area: the tiles must be in the loaded scene, and a player
        // sets their view up on arrival, not from across the map.
        if (!isNearBank() && !isNearAnvil()) return;

        final LocalPoint bankTile = toLocal(bankLocation);
        final LocalPoint anvilTile = toLocal(anvilLocation);
        if (bankTile == null || anvilTile == null) return; // scene not ready, retry next tick

        cameraSettled = true;
        // A view that shows both and is already pulled back is left alone, the way a player would
        // leave it. Both tiles on screen is not enough: zoomed in, the objects sit at the edges.
        boolean visible = bothVisible(bankTile, anvilTile);
        if (visible && Rs2Camera.getZoom() <= ZOOMED_OUT && Rs2Camera.getPitch() >= 300) {
            framedZoom = Rs2Camera.getZoom();
            info("Camera left as it is (zoom " + framedZoom + ", pitch " + Rs2Camera.getPitch() + ")");
            return;
        }
        try {
            // 1. Turn only when needed, to face across the bank-to-anvil corridor so it lies along
            //    the wider axis. Not turnTo(): it forces a fast camera speed and snaps.
            if (!visible) {
                int[] yaws = framingYaws(bankLocation, anvilLocation);
                int baseYaw = yaws[rng.nextInt(yaws.length)];
                humanYaw(((baseYaw + Rs2Random.between(-90, 90)) % 2048 + 2048) % 2048);
                sleepGaussian(360, 120);
            }

            // 2. Zoom well out. Lower value = further out.
            int zoom = Math.min(Rs2Camera.getZoom(), Rs2Random.between(MIN_ZOOM, 125));
            humanZoom(zoom);
            sleepGaussian(300, 100);

            // 3. Pitch down. 128 = horizon, 383 = straight down.
            int pitch = Math.max(Rs2Camera.getPitch(), Rs2Random.between(325, 372));
            humanPitch(pitch);

            // 4. Keep widening until both really are in frame.
            boolean framed = bothVisible(bankTile, anvilTile);
            for (int step = 0; step < MAX_FRAMING_STEPS && !framed; step++) {
                zoom = Math.max(MIN_ZOOM, zoom - Rs2Random.between(22, 38));
                pitch = Math.min(MAX_PITCH, pitch + Rs2Random.between(5, 11));
                Rs2Camera.setZoom(zoom);
                sleepGaussian(180, 60);
                Rs2Camera.setPitch(pitch);
                sleepGaussian(240, 80);
                framed = bothVisible(bankTile, anvilTile);
            }

            // What the client clamped it to: holdView keeps us here.
            framedZoom = Rs2Camera.getZoom();
            if (framed) {
                info("Camera set: bank and anvil both in view (zoom " + zoom + ", pitch " + pitch + ")");
            } else {
                info("Camera set, but bank and anvil do not both fit (zoom " + zoom
                        + ", pitch " + pitch + "), the client may still turn to reach them");
            }
        } catch (Exception e) {
            // Not fatal: the client falls back to turning toward targets itself.
            Microbot.log("AnvilSmither: could not set the camera: " + e.getMessage());
        }
    }

    /**
     * Between loads: put back what drifts. Higher zoom is closer, and a relog or a stray scroll can
     * leave it zoomed in. The game turns run off at 0 energy and nothing turns it back on, which made
     * every bank trip a walk. Both as Herbi Hunter does them.
     */
    private void holdView() {
        if (framedZoom > 0 && Rs2Camera.getZoom() > framedZoom + 10) {
            info("Zoomed in to " + Rs2Camera.getZoom() + " (framed at " + framedZoom + "), zooming back out");
            humanZoom(framedZoom);
        }
        if (!Rs2Player.isRunEnabled() && Rs2Player.getRunEnergy() >= runBackOnAt) {
            info("Run is off, turning it back on");
            react(400);
            Rs2Widget.clickWidget(InterfaceID.Orbs.RUNBUTTON);
            sleepUntil(Rs2Player::isRunEnabled, 1200);
            runBackOnAt = Rs2Random.between(3, 10);
        }
    }

    /**
     * Pause before an action, the way a player reacts to something they were already expecting.
     * The medians at the call sites were set before a live run and felt slow; 0.7 brings the usual
     * click to about 270-340 ms, where Herbi Hunter's focused pace (250-310) sits.
     */
    private void react(int medianMs) {
        sleep((int) habits.reactionMs(rng, medianMs * 7 / 10));
    }

    /**
     * The two yaws that lay the bank-to-anvil corridor across the wider (horizontal) screen axis.
     * Varrock's pair sits on a north-south line and Prifddinas's east-west, so this is worked out
     * from the tiles rather than fixed.
     *
     * @return the two opposite yaws perpendicular to the corridor's dominant axis
     */
    public static int[] framingYaws(WorldPoint bank, WorldPoint anvil) {
        int dx = Math.abs(anvil.getX() - bank.getX());
        int dy = Math.abs(anvil.getY() - bank.getY());
        // Corridor runs mostly east-west -> look north or south across it, and vice versa.
        return dx >= dy ? new int[]{YAW_NORTH, YAW_SOUTH} : new int[]{YAW_EAST, YAW_WEST};
    }

    /** One eased turn: setYaw is already a smooth move, and two of them stop dead halfway (judder). */
    private void humanYaw(int targetYaw) {
        Rs2Camera.setYaw(((targetYaw % 2048) + 2048) % 2048);
    }

    private void humanPitch(int targetPitch) {
        Rs2Camera.setPitch(targetPitch);
    }

    /** Turn roughly towards a tile with one eased move, so its outline comes on screen. */
    private void faceGently(WorldPoint target) {
        if (target == null) return;
        int degrees = Rs2Camera.angleToTile(target);   // 0 = east, counter-clockwise
        int yaw = (int) Math.round(((degrees - 90 + 360) % 360) * 2048 / 360.0);   // camera: 0 = north, 512 = west
        humanYaw(yaw + Rs2Random.between(-80, 80));
        sleepGaussian(260, 80);
    }

    /** Stepped like scroll-wheel notches, because that is how a player actually zooms. */
    private void humanZoom(int targetZoom) {
        int from = Rs2Camera.getZoom();
        int notches = Rs2Random.between(3, 6);
        for (int i = 1; i <= notches; i++) {
            Rs2Camera.setZoom(from + Math.round((targetZoom - from) * (i / (float) notches)));
            sleepGaussian(150, 50);
        }
    }

    /** One client-thread hop; tiles resolved by the caller rather than on every call. */
    private boolean bothVisible(LocalPoint bank, LocalPoint anvil) {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Rs2Camera.isTileOnScreen(bank) && Rs2Camera.isTileOnScreen(anvil)).orElse(false);
    }

    private LocalPoint toLocal(WorldPoint worldPoint) {
        return Microbot.getClientThread().invoke(() -> {
            Client client = Microbot.getClient();
            if (client == null) return null;
            return LocalPoint.fromWorld(client.getTopLevelWorldView(), worldPoint);
        });
    }

    /** The only place state changes, so the watchdog's dwell time can never drift. */
    private void setState(State next) {
        if (next == null || next == state) return;
        State previous = state;
        state = next;
        stateEnteredMs = System.currentTimeMillis();
        lastLogged = "";
        if (config != null && config.sDebug()) {
            Microbot.log("AnvilSmither [" + previous + " -> " + next + "]");
        }
    }

    /** One-shot startup gate. Stops only on things no retry can fix. */
    private boolean runPreflight() {
        long now = System.currentTimeMillis();
        if (preflightStartedMs == 0) preflightStartedMs = now;
        if (now - preflightStartedMs > PREFLIGHT_LIMIT_MS) {
            stop("Could not get ready: quest progress did not load, or the Imcando hammer would not equip.", false);
            return false;
        }

        if (Rs2Dialogue.hasContinue()) {
            debug("Preflight: clearing a leftover dialogue");
            Rs2Dialogue.clickContinue();
            return false;
        }

        AnvilLocation location = activeLocation;
        AnvilItem item = activeItem;
        Bars bar = activeBar;
        Quest[] gates = {location.getRequiredQuest(), item.isKeelItem() ? Quest.PANDEMONIUM : item.getRequiredQuest()};
        for (Quest gate : gates) {
            if (gate == null) continue;
            QuestState progress = Rs2Player.getQuestState(gate);
            if (progress == null) return false; // not loaded yet, look again next tick
            if (progress != QuestState.FINISHED) {
                stop((gate == location.getRequiredQuest() ? location.toString() : effectiveItemName(item, bar))
                        + " needs " + gate.getName() + " completed.", false);
                return false;
            }
        }

        int level = Microbot.getClientThread().invoke(() -> Microbot.getClient().getRealSkillLevel(Skill.SMITHING));
        int required = item.levelFor(bar);
        if (level < required) {
            stop("Smithing " + level + " is below the " + required + " needed for "
                    + effectiveItemName(item, bar) + " from " + bar + ".", false);
            return false;
        }

        if (!equipCarriedImcando()) {
            return false;
        }

        preflightDone = true;
        preflightStartedMs = 0;
        info("Ready, " + describeHammer());
        // Bars and a hammer already carried are smithed first; anything else in the inventory is
        // banked on the first trip. Without them DETERMINE sends us to the bank anyway.
        setState(State.DETERMINE);
        return true;
    }

    /** Wear a carried Imcando hammer: same result, one more inventory slot. False = retry next tick. */
    private boolean equipCarriedImcando() {
        if (alching) return true;   // a main-hand hammer would push the fire staff back off
        for (int variant : new int[]{ItemID.IMCANDO_HAMMER_OFFHAND, ItemID.IMCANDO_HAMMER}) {
            final int id = variant;
            if (Rs2Equipment.isWearing(id)) return true;
            if (!Rs2Inventory.hasItem(id)) continue;
            if (Rs2Bank.isOpen()) {
                Rs2Bank.closeBank();
                return false;
            }
            debug("Equipping the Imcando hammer to free an inventory slot");
            react(450);
            Rs2Inventory.interact(id, "Wield");
            return sleepUntil(() -> Rs2Equipment.isWearing(id), 2500);
        }
        return true;
    }

    private String describeHammer() {
        if (Rs2Equipment.isWearing(ItemID.IMCANDO_HAMMER_OFFHAND)) return "Imcando hammer equipped (off-hand)";
        if (Rs2Equipment.isWearing(ItemID.IMCANDO_HAMMER)) return "Imcando hammer equipped (main-hand)";
        if (Rs2Equipment.isWearing(ItemID.HAMMER)) return "hammer equipped";
        if (Rs2Inventory.hasItem(HAMMER_IDS)) return "hammer in inventory";
        return "no hammer yet, will withdraw one";
    }

    private State tick(State currentState, Bars barType, AnvilItem anvilItem) {
        switch (currentState) {
            case DETERMINE:  return doDetermine(barType, anvilItem);
            case BANKING:    return doBanking(barType, anvilItem);
            case SMITHING:   return doSmithing(barType, anvilItem);
            case ALCHING:    return doAlching();
            case RECOVERY:   return doRecovery();
            default:         return State.DETERMINE;
        }
    }

    private State doDetermine(Bars barType, AnvilItem anvilItem) {
        if (Rs2Bank.isOpen()) {
            debug("Determine: bank is open, closing first");
            Rs2Bank.closeBank();
            return State.BANKING;
        }

        // Changed settings are picked up here, between loads, and checked the way a start is.
        logout = config.sLogout();
        if (config.sBarType() != activeBar || config.sAnvilItem() != activeItem || config.sLocation() != activeLocation) {
            String rejection = rejectionReason(config.sBarType(), config.sAnvilItem());
            if (rejection != null) {
                stop(rejection, false);
                return State.DETERMINE;
            }
            activeBar = config.sBarType();
            activeItem = config.sAnvilItem();
            if (config.sLocation() != activeLocation) {
                activeLocation = config.sLocation();
                anvilLocation = activeLocation.getAnvil();
                bankLocation = activeLocation.getBank();
                cameraSettled = false;
                framedZoom = 0;
            }
            ctx.resetAll();
            preflightDone = false;
            materialsLeft = -1;
            madeNames.clear();   // the alch stage takes only what the new setting makes
            info("Settings changed: now making " + effectiveItemName(activeItem, activeBar) + " from " + activeBar + " at " + activeLocation);
            return State.DETERMINE;
        }

        holdView();
        int currentMat = countMaterial(getMatContext(barType, anvilItem));
        int required = anvilItem.getRequiredBars();
        boolean hasHammer = hasHammer();
        boolean ready = currentMat >= required && hasHammer;
        debug("Determine -> " + (ready ? "SMITHING" : "BANKING")
                + " (mats=" + currentMat + "/" + required + ", hammer=" + hasHammer + ")");
        return ready ? State.SMITHING : State.BANKING;
    }

    private State doBanking(Bars barType, AnvilItem anvilItem) {
        ctx.resetSession();

        if (Rs2Bank.isOpen()) {
            // Bank counts are only trustworthy once a bank snapshot has arrived.
            if (!Rs2Bank.verifyBankMirrorAfterOpen(true, 0)) {
                Rs2Bank.closeBank();
                return bankRetry("The bank did not load");
            }
            return performBankingOperations(barType, anvilItem);
        }

        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null) {
            debug("Banking: waiting for player location to load...");
            return State.BANKING;
        }

        if (!isNearBank()) {
            debug("Walking to bank... (dist=" + playerLoc.distanceTo(bankLocation) + ")");
            walkTowards(bankLocation, this::isNearBank);
            return State.BANKING;
        }

        debug("Clicking the bank, dist=" + playerLoc.distanceTo(bankLocation));
        // Resting on the booth already: the pause after the load was the noticing, so press.
        if (!leftClickDoes(topMenuEntry(), "Bank", -1)) react(480);

        int epochBeforeOpen = Rs2Bank.getBankLiveEpoch();
        if (!interactWithBank()) {
            if (Rs2Player.isMoving()) return State.BANKING;
            debug("Bank interact failed");
            return State.RECOVERY;
        }

        // The clock restarts while we are still walking over: with run off the trip outlasts 5s.
        if (!sleepUntilTrue(Rs2Bank::isOpen, Rs2Player::isMoving, 100, 5000)) {
            debug("Bank failed to open after click");
            return State.RECOVERY;
        }

        // An empty mirror reads as zero bars and would stop the script with "Out of materials."
        if (!sleepUntil(() -> Rs2Bank.verifyBankMirrorAfterOpen(false, epochBeforeOpen), 1800)) {
            Rs2Bank.closeBank();
            return bankRetry("The bank did not load");
        }

        return performBankingOperations(barType, anvilItem);
    }

    /** A banking step that did not take. Tried again a few times, then RECOVERY (which is capped). */
    private State bankRetry(String why) {
        debug(why + ", trying again");
        if (++bankFailures < MAX_BANK_FAILURES) return State.BANKING;
        bankFailures = 0;
        info(why + " (" + MAX_BANK_FAILURES + " times in a row), recovering");
        return State.RECOVERY;
    }

    private State performBankingOperations(Bars barType, AnvilItem anvilItem) {
        final MatContext mat = getMatContext(barType, anvilItem);

        if (config.sAlch() && resumeAlchLoad(mat, anvilItem.getRequiredBars())) return State.ALCHING;

        // Keep a hammer only if none is worn, plus whatever material is already carried.
        final boolean hammerWorn = Rs2Equipment.isWearing(HAMMER_IDS);
        final Predicate<Rs2ItemModel> keep = item ->
                keepsItem(hammerWorn, mat.id, mat.useName ? mat.name : null, item.getId(), item.getName());
        final Predicate<Rs2ItemModel> surplus = keep.negate();
        if (Rs2Inventory.count(surplus) > 0) {
            react(380);
            // Nothing to keep: the deposit-inventory button, one click, as a player does it.
            if (hammerWorn && countMaterial(mat) == 0) {
                Rs2Bank.depositAll();
            } else {
                Rs2Bank.depositAllExcept(keep);
            }
            // The deposit lands a tick later; withdrawing into a still-full inventory does nothing.
            if (!sleepUntil(() -> Rs2Inventory.count(surplus) == 0, 2400)) {
                return bankRetry("The deposit did not settle");
            }
        }

        // Left on "Note", every withdrawal below comes out as paper nothing can be smithed from.
        if (Rs2Bank.hasWithdrawAsNote()) {
            react(380);
            Rs2Bank.setWithdrawAsItem();
            if (!sleepUntil(Rs2Bank::hasWithdrawAsItem, 1800)) return bankRetry("The bank stayed on Note");
        }

        // An Imcando hammer in the bank beats a carried plain one: worn, it frees a slot.
        if (!Rs2Equipment.isWearing(HAMMER_IDS) && Rs2Inventory.hasItem(ItemID.HAMMER)
                && (Rs2Bank.hasItem(ItemID.IMCANDO_HAMMER_OFFHAND) || Rs2Bank.hasItem(ItemID.IMCANDO_HAMMER))) {
            info("Swapping the plain hammer for the Imcando hammer in the bank");
            react(380);
            Rs2Bank.depositAll(ItemID.HAMMER);
            if (!sleepUntil(() -> !Rs2Inventory.hasItem(ItemID.HAMMER), 2400)) return bankRetry("The plain hammer did not deposit");
        }

        // An equipped hammer already satisfies this and needs no inventory slot.
        if (!hasHammer()) {
            // 28 carried bars leave no room for it; they come back out in whole items below.
            if (Rs2Inventory.isFull()) {
                react(380);
                if (mat.useName) {
                    Rs2Bank.depositAll(mat.name, true);
                } else {
                    Rs2Bank.depositAll(mat.id);
                }
                if (!sleepUntil(() -> !Rs2Inventory.isFull(), 2400)) return bankRetry("No room was made for the hammer");
            }
            for (int hammerId : new int[]{ItemID.IMCANDO_HAMMER_OFFHAND, ItemID.IMCANDO_HAMMER, ItemID.HAMMER}) {
                if (!Rs2Bank.hasItem(hammerId)) continue;
                react(380);
                Rs2Bank.withdrawOne(hammerId);
                sleepUntil(this::hasHammer, BANK_TIMEOUT);
                if (hasHammer()) break;
            }
            if (!hasHammer()) {
                Rs2Bank.closeBank();
                stop("No hammer in the bank, inventory or worn. A plain hammer or an Imcando hammer works.");
                return State.DETERMINE;
            }
            // Wield needs the bank closed, so this settles on the next pass.
            if (Rs2Inventory.hasItem(ItemID.IMCANDO_HAMMER, ItemID.IMCANDO_HAMMER_OFFHAND)) {
                preflightDone = false;
            }
        }

        final int currentMat = countMaterial(mat);
        int matsPerItem = anvilItem.getRequiredBars();
        int bankCount = mat.useName ? Rs2Bank.count(mat.name, true) : Rs2Bank.count(mat.id);
        materialsLeft = currentMat + bankCount;

        if (currentMat + bankCount < matsPerItem) {
            if (config.sAlch() && fetchAlchLoad()) return State.ALCHING;
            Rs2Bank.closeBank();
            stop("Out of materials: fewer than " + matsPerItem + " " + mat.name + " left.");
            return State.DETERMINE;
        }

        // Fill what is really free, in whole items: a second carried hammer or a stray kept item
        // takes a slot the old fixed sum did not know about.
        int target = ((currentMat + Rs2Inventory.emptySlotCount()) / matsPerItem) * matsPerItem;
        final int toWithdraw = Math.min(target - currentMat, bankCount);
        if (toWithdraw > 0) {
            react(380);
            if (mat.useName) {
                Rs2Bank.withdrawX(mat.name, toWithdraw, true);
            } else {
                Rs2Bank.withdrawX(mat.id, toWithdraw);
            }
            if (!sleepUntil(() -> countMaterial(mat) >= currentMat + toWithdraw, BANK_TIMEOUT)) {
                return bankRetry("The withdrawal of " + toWithdraw + " did not settle");
            }
            // The bank remembers X once it has been typed. With the quantity button on X as well,
            // every later withdrawal is an ordinary left click for exactly that many, which is how
            // a player sets the bank up for a long session.
            if (Microbot.getVarbitValue(VarbitID.BANK_REQUESTEDQUANTITY) == toWithdraw
                    && Microbot.getVarbitValue(VarbitID.BANK_QUANTITY_TYPE) != BANK_QUANTITY_X) {
                info("Setting the bank quantity button to X (" + toWithdraw + ")");
                react(380);
                Rs2Widget.clickWidget(BANK_WIDGET_GROUP, BANK_QUANTITY_X_CHILD);
                sleepUntil(() -> Microbot.getVarbitValue(VarbitID.BANK_QUANTITY_TYPE) == BANK_QUANTITY_X, 1200);
            }
        }

        // Ready, and the hammer is not waiting to be wielded (that needs the bank shut): straight to
        // the anvil, the bank closes as the player walks off. Seen live: close, decide, react and
        // aim took 5 ticks between the withdraw and the anvil click.
        if (hasHammer() && countMaterial(mat) >= matsPerItem
                && !Rs2Inventory.hasItem(ItemID.IMCANDO_HAMMER, ItemID.IMCANDO_HAMMER_OFFHAND)) {
            react(380);
            if (clickAnvilFromBank()) {
                sleepUntilTrue(() -> isSmithingWidgetOpen() || Rs2Dialogue.hasContinue(), Rs2Player::isMoving,
                        100, WIDGET_OPEN_TIMEOUT);
                return State.SMITHING;   // the open interface is picked up there; if not, it clicks again
            }
        } else {
            react(420);
        }
        Rs2Bank.closeBank();
        if (!sleepUntil(() -> !Rs2Bank.isOpen(), 3000)) {
            return bankRetry("The bank stayed open after closing it");
        }

        int finalMatCount = countMaterial(mat);
        if (!hasHammer() || finalMatCount < matsPerItem) {
            return bankRetry("Banked but not ready (mats " + finalMatCount + "/" + matsPerItem + ", hammer=" + hasHammer() + ")");
        }
        // Straight on: DETERMINE would only send us to SMITHING a loop later.
        debug("Banking complete -> SMITHING (mats now " + finalMatCount + ")");
        return State.SMITHING;
    }

    private State doSmithing(Bars barType, AnvilItem anvilItem) {
        if (Rs2Bank.isOpen()) {
            debug("Smithing: bank is open, closing first");
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
            return State.DETERMINE;
        }

        MatContext mat = getMatContext(barType, anvilItem);
        int currentMat = countMaterial(mat);
        int req = anvilItem.getRequiredBars();
        String itemName = effectiveItemName(anvilItem, barType);

        if (ctx.lastMakeStarted > 0) {
            // Only bars actually being used proves the click started something.
            if (currentMat < ctx.materialsAtStart) {
                ctx.progressSeen = true;
                consecutiveRecoveries = 0;
            }

            if (currentMat < req) {
                info("Finished a load (" + ctx.maxFullItems + " x " + itemName + ")");
                learnProduct();
                boolean cursorLeftWindow = ctx.cursorLeftWindow;
                long loadMs = System.currentTimeMillis() - ctx.smithingStartTime;
                creditSession(currentMat, req);
                pauseAfterLoad(cursorLeftWindow, loadMs);
                return State.DETERMINE;
            }

            // Without this, an animation that never ends parks us in SMITHING indefinitely.
            long sessionAge = System.currentTimeMillis() - ctx.smithingStartTime;
            if (ctx.makeSessionTimeoutMs > 0 && sessionAge > ctx.makeSessionTimeoutMs) {
                info("Make session ran " + (sessionAge / 1000) + "s with materials left, restarting it");
                creditSession(currentMat, req);
                return State.DETERMINE;
            }

            if (Rs2Player.isAnimating()) {
                debug("Smithing (" + currentMat + " " + mat.name.toLowerCase() + " left)");
                idleCursor();
                return State.SMITHING;
            }

            // The animation drops between swings; an XP drop says the load is still going.
            if (Rs2Player.waitForXpDrop(Skill.SMITHING, Rs2Random.between(3600, 5400))) {
                ctx.progressSeen = true;
                consecutiveRecoveries = 0;
                return State.SMITHING;
            }

            // The wait above blocks for seconds, so the count taken before it can be stale: seen live
            // as "stopped with 5 bars left" on a load that had just finished. Look again first.
            if (countMaterial(mat) != currentMat) return State.SMITHING;

            if (!ctx.progressSeen) {
                // The click made nothing. Forget the remembered pick so the next try selects the item.
                // Seen once in ~130 loads with space: the interface closed but no bar was used.
                // The detail is here to tell a swallowed key from a closed interface next time.
                info("Nothing was smithed after the click, starting over (interface "
                        + (isSmithingWidgetOpen() ? "open" : "closed") + ", "
                        + (Rs2Player.isMoving() ? "moving" : "standing") + ", "
                        + (Rs2Dialogue.hasContinue() ? "dialogue up" : "no dialogue") + ")");
                ctx.resetAll();
                return State.RECOVERY;
            }

            // Stopped part-way with bars left: a level-up message does this. Click the anvil again.
            debug("Smithing stopped with " + currentMat + " " + mat.name.toLowerCase() + " left");
            creditSession(currentMat, req);
            react(700);
            return State.DETERMINE;
        }

        if (currentMat < req) {
            debug("Low mats on entry, going to DETERMINE");
            return State.DETERMINE;
        }

        if (!isNearAnvil()) {
            debug("Walking to anvil...");
            walkTowards(anvilLocation, this::isNearAnvil);
            return State.SMITHING;
        }

        // An interface that is already open (a failed pick, a player's own click) is used as it is:
        // clicking the anvil through it lands on the interface instead.
        if (isSmithingWidgetOpen()) {
            debug("Smithing interface already open, picking the item");
        } else {
            debug("Clicking the anvil");
            react(480);

            if (!interactWithAnvil()) {
                if (Rs2Player.isMoving()) return State.SMITHING;
                debug("Anvil interact failed");
                return State.RECOVERY;
            }

            // As with the bank: the clock restarts while we are still walking over.
            sleepUntilTrue(() -> isSmithingWidgetOpen() || Rs2Dialogue.hasContinue(), Rs2Player::isMoving,
                    100, WIDGET_OPEN_TIMEOUT);
        }

        int continues = 0;
        while (Rs2Dialogue.hasContinue() && continues < 6) {
            react(500);
            Rs2Dialogue.clickContinue();
            continues++;
        }

        if (!isSmithingWidgetOpen()) {
            if (continues > 0) {
                debug("Dialogue cleared, clicking the anvil again");
                return State.SMITHING;
            }
            debug("Smithing interface did not open after the anvil click");
            return State.RECOVERY;
        }

        int fullItems = currentMat / req;
        String itemKey = anvilItem.getName() + "_" + barType;
        react(430);

        // Quantity first, as a player does. The game remembers it, so only click when it is lower
        // than what we are about to smith; otherwise it is one item per trip.
        if (Microbot.getVarbitPlayerValue(MAKE_X_QUANTITY_VARP) < fullItems) {
            debug("Setting quantity to All");
            Rs2Widget.clickWidget(ANVIL_WIDGET_GROUP, ANVIL_WIDGET_ALL_CHILD);
            react(380);
        }

        boolean pickedThisSession = ctx.selectionComplete && itemKey.equals(ctx.lastSelectedItem);
        // Where the game's memory can be read it decides: a player's own click in between, or a
        // changed setting, would otherwise have space make the wrong item.
        if (anvilItem.hasValidChildId() ? gameRemembers(barType, anvilItem) : pickedThisSession) {
            // The interface remembers the last item, across sessions too, so space makes it again.
            if (!pickedThisSession) debug("The game already remembers " + itemName + ", pressing space");
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            // Smithing closes the interface. Still open means the press did nothing: click the item.
            if (sleepUntil(() -> !isSmithingWidgetOpen(), Rs2Random.between(1500, 2100))) {
                startLoad(fullItems, currentMat);
                ctx.selectionComplete = true;
                ctx.lastSelectedItem = itemKey;
                info("Smithing " + fullItems + " x " + itemName + " (repeat)");
                return State.SMITHING;
            }
            info("Space did not repeat the item, clicking it");
            ctx.selectionComplete = false;
        }

        // Stable child index where there is one; otherwise match the text (other_N slots move).
        boolean itemClicked = anvilItem.hasValidChildId()
                ? Rs2Widget.clickWidget(ANVIL_WIDGET_GROUP, anvilItem.getChildId())
                : clickItemByName(anvilItem.isKeelItem() ? itemName : anvilItem.getSearch(), itemName);
        if (!itemClicked) {
            debug("Could not find '" + itemName + "' in the anvil interface");
            return State.RECOVERY;
        }

        startLoad(fullItems, currentMat);
        ctx.selectionComplete = true;
        ctx.lastSelectedItem = itemKey;
        info("Smithing " + fullItems + " x " + itemName);
        return State.SMITHING;
    }

    private void startLoad(int fullItems, int currentMat) {
        idsBeforeLoad = Rs2Inventory.items().map(Rs2ItemModel::getId).collect(Collectors.toSet());
        long idleAt = rollIdleMove(fullItems);
        ctx.startSession(fullItems, currentMat, idleAt);
        debug(idleAt == 0 ? "Idle: cursor stays this load" : "Idle: cursor moves in " + (idleAt - System.currentTimeMillis()) + " ms");
    }

    /**
     * What appeared in the inventory during the load is what was made. Kept by the game's own name,
     * which a note shares: "Plate body" here is "Adamant platebody" there, so it is never guessed.
     * Something carried before the load is never learned, so stray items are not alched.
     */
    private void learnProduct() {
        // boat parts are for building; stackables (nails, tips, knives, bolts) cost a nature rune each for a few gp
        if (activeItem.isKeelItem() || activeItem.getOutputPerAction() > 1) return;
        Rs2Inventory.items().filter(i -> !idsBeforeLoad.contains(i.getId()))
                .map(Rs2ItemModel::getName).findFirst().ifPresent(madeNames::add);
    }

    // Where the game spells the item differently from the anvil interface: "Rune 2h sword".
    private static final Map<AnvilItem, String> GAME_WORDS = Map.of(
            AnvilItem.TWO_HAND_SWORD, "2h sword", AnvilItem.MEDIUM_HELM, "med helm",
            AnvilItem.SQUARE_SHIELD, "sq shield", AnvilItem.DART_TIPS, "dart tip",
            AnvilItem.KNIVES, "knife", AnvilItem.LIMBS, "limbs");

    private static String nameKey(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /**
     * Whether a game item name is what this bar and item make, so items from an earlier run are
     * alched too. Spacing and brackets are ignored ("Plate body" is "Adamant platebody", and it is
     * "Adamant bolts(unf)" but "Mithril bolts (unf)"), and either metal word fits ("Adamantite
     * nails", "Runite limbs"). Only a whole-name match counts; a miss just means not alched.
     */
    public static boolean isProductName(Bars bar, AnvilItem item, String gameName) {
        if (bar == null || item == null || item.isKeelItem() || item.getOutputPerAction() > 1) return false;
        String key = nameKey(gameName);
        if (item.getOnlyBar() != null) return key.equals(nameKey(item.getName()));
        String word = nameKey(GAME_WORDS.getOrDefault(item, item.getName()));
        return key.equals(nameKey(bar.getKeelMetal()) + word)
                || key.equals(nameKey(bar.toString().replace(" bar", "")) + word);
    }

    private boolean isMade(Rs2ItemModel item) {
        return madeNames.contains(item.getName()) || isProductName(activeBar, activeItem, item.getName());
    }

    /** High Alchemy from 55 Magic, Low Alchemy from 21, nothing below. */
    public static Rs2Spells alchSpell(int magicLevel) {
        return magicLevel >= 55 ? Rs2Spells.HIGH_LEVEL_ALCHEMY : magicLevel >= 21 ? Rs2Spells.LOW_LEVEL_ALCHEMY : null;
    }

    private Rs2Spells alchSpell() {
        return alchSpell(Microbot.getClientThread().invoke(() -> Microbot.getClient().getRealSkillLevel(Skill.MAGIC)));
    }

    /**
     * Bank open, bars gone: take out what this run made, as notes, with what it takes to alch it:
     * the rune pouch, a fire staff, loose runes, whichever the bank has. False when there is nothing
     * left to alch or no way to cast, which leaves the ordinary out-of-materials stop.
     */
    private boolean fetchAlchLoad() {
        // quantity: a bank placeholder is an item with none of it
        Rs2ItemModel banked = Rs2Bank.get(i -> i.getQuantity() > 0 && isMade(i));
        if (banked == null) return false;
        final String name = banked.getName();
        Rs2Spells spell = alchSpell();
        if (spell == null || !spell.hasRequiredSpellbook()) {
            info("Not alching: it needs 21 Magic and the standard spellbook");
            return false;
        }

        // The pouch is banked with everything else each load; its runes only count when carried.
        if (!Rs2Inventory.hasRunePouch()) {
            for (int pouchId : RunePouchType.getPouchIds()) {
                if (!Rs2Bank.hasItem(pouchId)) continue;
                react(380);
                Rs2Bank.withdrawOne(pouchId);
                sleepUntil(Rs2Inventory::hasRunePouch, BANK_TIMEOUT);
                break;
            }
        }

        int staffId = -1;
        int casts = Rs2Bank.count(name, true);
        for (Runes rune : Rs2Magic.getMissingRunes(spell, casts).keySet()) {
            if (rune == Runes.FIRE) {
                // A staff in the bank beats five fire runes a cast.
                staffId = Rs2Magic.findStavesByRunes(List.of(Runes.FIRE)).stream().map(Rs2Staff::getItemID)
                        .filter(Rs2Bank::hasItem).findFirst().orElse(-1);
                if (staffId > 0) {
                    final int id = staffId;
                    react(380);
                    Rs2Bank.withdrawOne(id);
                    if (!sleepUntil(() -> Rs2Inventory.hasItem(id), BANK_TIMEOUT)) staffId = -1;
                    continue;
                }
            }
            if (!Rs2Bank.hasItem(rune.getItemId())) continue;
            react(380);
            Rs2Bank.withdrawAll(rune.getItemId());
            sleepUntil(() -> Rs2Inventory.hasItem(rune.getItemId()), BANK_TIMEOUT);
        }

        Map<Runes, Integer> missing = Rs2Magic.getMissingRunes(spell, 1);
        if (staffId > 0) missing.remove(Runes.FIRE);   // worn once the bank is closed
        if (!missing.isEmpty()) {
            info("Not alching: no " + missing.keySet() + " runes in the bank, the rune pouch or a staff");
            return false;
        }

        react(380);
        Rs2Bank.setWithdrawAsNote();
        react(380);
        Rs2Bank.withdrawAll(name, true);
        // A miss here is caught in ALCHING: nothing to alch sends us back, a few times at most.
        sleepUntil(() -> Rs2Inventory.hasItem(name, true), BANK_TIMEOUT);
        react(380);
        Rs2Bank.setWithdrawAsItem();   // not left on Note for the player's next visit
        react(420);
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);

        if (staffId > 0) {
            final int id = staffId;
            react(450);
            Rs2Inventory.interact(id, "Wield");
            sleepUntil(() -> Rs2Equipment.isWearing(id), 2500);
        }
        beginAlch(name, casts);
        return true;
    }

    private void beginAlch(String name, int left) {
        if (!alching) {
            info("Out of " + getMaterialWord() + ", alching " + left + " x " + name);
            alchStartMs = System.currentTimeMillis();
            alchStartXp = Microbot.getClientThread().invoke(() -> Microbot.getClient().getSkillExperience(Skill.MAGIC));
        }
        alchName = name;
        alchLeft = left;
        lastAlchClickMs = 0;
        alching = true;
        alchSlotTries = 0;
    }

    /**
     * Bank open: the bars are gone and the stack and its runes are already carried (a restart
     * part-way through, or the player set it up). Banking it all to take it straight back out is
     * what no player does, so the bank is just closed.
     */
    private boolean resumeAlchLoad(MatContext mat, int matsPerItem) {
        Rs2ItemModel held = Rs2Inventory.get(this::isMade);
        if (held == null) return false;
        int bars = countMaterial(mat) + (mat.useName ? Rs2Bank.count(mat.name, true) : Rs2Bank.count(mat.id));
        if (bars >= matsPerItem) return false;
        Rs2Spells spell = alchSpell();
        if (spell == null || !spell.hasRequiredSpellbook() || !Rs2Magic.hasRequiredRunes(spell)) return false;
        react(420);
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
        beginAlch(held.getName(), held.getQuantity());
        return true;
    }

    private State doAlching() {
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
            return State.ALCHING;
        }

        Rs2ItemModel item = Rs2Inventory.get(this::isMade);
        if (item == null) {
            if (++alchMisses > MAX_ALCH_MISSES) {
                stop("Alching stopped after " + alchCasts + " items: nothing came out of the bank to alch.");
                return State.DETERMINE;
            }
            return State.BANKING;   // for the next item made, or to find there is none and stop
        }

        Rs2Spells spell = alchSpell();
        if (spell == null || !Rs2Magic.hasRequiredRunes(spell)) {
            stop("Out of runes to alch with, " + alchCasts + " items alched.");
            return State.DETERMINE;
        }

        // Under the spell's icon, where alchers keep the stack: the cursor barely moves between the
        // two clicks. Same slots as AIO Magic. A few tries; alching works from any slot.
        // The inventory tab first: live, with the magic tab left open by an earlier alch, the item
        // had no place on screen and the drag was silently skipped.
        final int slot = spell == Rs2Spells.HIGH_LEVEL_ALCHEMY ? 12 : 4;
        if (alchSlotTries < 3 && item.getSlot() != slot) {
            alchSlotTries++;
            if (Rs2Tab.getCurrentTab() != InterfaceTab.INVENTORY) {
                react(350);
                Rs2Tab.switchTo(InterfaceTab.INVENTORY);
                sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY, 1500);
            }
            react(450);
            Rs2Inventory.moveItemToSlot(item, slot);
            boolean moved = sleepUntil(() -> {
                Rs2ItemModel inSlot = Rs2Inventory.getItemInSlot(slot);
                return inSlot != null && isMade(inSlot);
            }, 1800);
            info(moved ? "Moved the stack under the spell (slot " + slot + ")"
                    : "The stack did not move to slot " + slot + " (try " + alchSlotTries + "/3, tab " + Rs2Tab.getCurrentTab() + ")");
            return State.ALCHING;
        }

        // As fast as the game allows without a beat to it: each click is aimed a different, skewed
        // margin past the cooldown, scaled by this session's pace, and the client's own two pauses
        // are drawn fresh each cast.
        long gap = AnvilHabits.logNormal(rng, 90, 380);   // spell click to item click
        long itemClickAt = System.currentTimeMillis() + gap;
        if (lastAlchClickMs == 0) {
            react(450);
        } else {
            long margin = (long) (AnvilHabits.logNormal(rng, 40, 420) * habits.pace);
            // Now and then a hesitation, and less often the player has looked away. Added here, not
            // slept after the cast: live, a pause taken inside the cooldown changed nothing and
            // every cast fell between 3.05 and 3.5 seconds.
            int roll = rng.nextInt(300);
            if (roll < habits.afkPct) margin += AnvilHabits.logNormal(rng, 1_500, 9_000);
            else if (roll < habits.afkPct + 24) margin += AnvilHabits.logNormal(rng, 250, 1_200);
            itemClickAt = Math.max(itemClickAt, lastAlchClickMs + ALCH_COOLDOWN_MS + margin);
            // the spell press and its menu check take about 120 ms of the gap themselves
            long wait = itemClickAt - gap - 120 - System.currentTimeMillis();
            if (wait > 0) sleep((int) wait);
        }
        if (!alchInPlace(spell, item, itemClickAt)) {
            // Turned off mid-cast (seen on every live reload): no parting click from a stopped plugin.
            if (!running || Thread.currentThread().isInterrupted()) return State.ALCHING;
            debug("Alch in place did not work, using the client's alch");
            Rs2Magic.alch(item, 90, 220);
            lastAlchClickMs = System.currentTimeMillis();
        }
        if (Rs2Player.waitForXpDrop(Skill.MAGIC, 1800, false)) {
            alchCasts++;
            alchCredits.add(new long[]{System.currentTimeMillis(), 1});
            if (alchLeft > 0) alchLeft--;
            alchMisses = 0;
            consecutiveRecoveries = 0;
            stateEnteredMs = System.currentTimeMillis();   // a long stack is not a stuck state
        } else if (++alchMisses <= MAX_ALCH_MISSES) {
            info("No Magic XP after a cast (" + alchMisses + " in a row)");
            lastAlchClickMs = 0;   // nothing was cast, so there is no cooldown to wait out
        } else {
            stop("Alching stopped after " + alchCasts + " items: " + MAX_ALCH_MISSES + " casts in a row did nothing. "
                    + "Check " + item.getName() + " can be alched and no warning is on screen.");
            return State.DETERMINE;
        }
        return State.ALCHING;
    }

    /**
     * One alch the way a player does a stack: the cursor rests where the spell icon and the stack
     * overlap and is only clicked, twice. The client's alch picks a new point on the spell and on
     * the item every time, which was a visible shuffle twice a cast and cost over a second (live:
     * 4.6 s a cast). Each press uses the game's own entry under the cursor, as clickLikeAPlayer
     * does. False = it could not be done this way and nothing may have been cast.
     */
    private boolean alchInPlace(Rs2Spells spell, Rs2ItemModel item, long itemClickAt) {
        final String spellName = spell == Rs2Spells.HIGH_LEVEL_ALCHEMY ? "High Level Alchemy" : "Low Level Alchemy";
        if (Rs2Tab.getCurrentTab() != InterfaceTab.MAGIC) {
            Rs2Tab.switchTo(InterfaceTab.MAGIC);
            if (!sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.MAGIC, 1500)) return false;
        }
        Widget spellWidget = Rs2Widget.getWidget(spell.getMagicAction().getWidgetId());
        if (spellWidget == null) return false;
        Rectangle spot = spellWidget.getBounds();

        // A hand on a mouse is not perfectly still: now and then the cursor shifts a pixel or two.
        java.awt.Point at = Microbot.getMouse().getMousePosition();
        if (at != null && rng.nextInt(100) < 12) {
            int x = at.x + (int) Math.round(rng.nextGaussian() * 2.5), y = at.y + (int) Math.round(rng.nextGaussian() * 2.5);
            if (spot.contains(x, y)) Microbot.getNaturalMouse().moveTo(x, y);
        }

        NewMenuEntry cast = awaitCast(120, spellName);
        if (cast == null || Text.removeTags(cast.getTarget()).contains("->")) {
            java.awt.Point aim = pointInside(spot);
            if (aim == null) return false;
            Microbot.getNaturalMouse().moveTo(aim.x, aim.y);
            cast = awaitCast(600, spellName);
            if (cast == null || Text.removeTags(cast.getTarget()).contains("->")) return false;
        }
        pressHere(cast);

        // Polled fast: the stock wait looks every 100 ms, which was most of the lost time per cast.
        long tabBy = System.currentTimeMillis() + 1500;
        while (Rs2Tab.getCurrentTab() != InterfaceTab.INVENTORY) {
            if (System.currentTimeMillis() > tabBy) return false;
            sleep(Rs2Random.between(10, 16));
        }
        // The client's click call takes a while itself (live: a quarter second a cast went on it);
        // most of what it has been taking is allowed for, so the click still lands past the cooldown.
        sleep((int) Math.max(itemClickAt - (long) (pressLatencyMs * 0.8) - System.currentTimeMillis(), 40 + rng.nextInt(50)));
        NewMenuEntry on = awaitCast(150, spellName, "->", item.getName());
        if (on == null) {
            // The stack is not under the cursor: go to it, where it overlaps the spell if it does.
            Rectangle box = Rs2Inventory.itemBounds(item);
            if (box == null) return false;
            java.awt.Point aim = pointInside(box.intersects(spot) ? box.intersection(spot) : box);
            if (aim == null) return false;
            Microbot.getNaturalMouse().moveTo(aim.x, aim.y);
            on = awaitCast(600, spellName, "->", item.getName());
            if (on == null) return false;
        }
        long pressAt = System.currentTimeMillis();
        pressHere(on);
        long now = System.currentTimeMillis();
        pressLatencyMs = pressLatencyMs == 0 ? now - pressAt : pressLatencyMs * 0.8 + (now - pressAt) * 0.2;
        if (lastAlchClickMs > 0) debug("Alch " + (now - lastAlchClickMs) + " ms after the last (click call " + (now - pressAt) + " ms)");
        lastAlchClickMs = now;
        return true;
    }

    /** The game's left-click entry once it is a spell cast whose target has every word, else null. */
    private NewMenuEntry awaitCast(int timeoutMs, String... words) {
        long until = System.currentTimeMillis() + timeoutMs;
        while (true) {
            NewMenuEntry top = topMenuEntry();
            if (top != null && "Cast".equalsIgnoreCase(top.getOption()) && top.getTarget() != null) {
                String target = Text.removeTags(top.getTarget());
                boolean all = true;
                for (String word : words) all &= target.contains(word);
                if (all) return top;
            }
            if (System.currentTimeMillis() >= until) return null;
            sleep(Rs2Random.between(10, 16));   // under a client frame
        }
    }

    private void pressHere(NewMenuEntry entry) {
        java.awt.Point at = Microbot.getMouse().getMousePosition();
        Microbot.getMouse().click(new Point(at.x, at.y), entry);
    }

    /**
     * Whether space would make this item: the game keeps the last bar and item smithed in two
     * varbits. Read live with adamantite plate body selected: bar 5, item 14, which is the metal's
     * place in the list and the item's interface child less 8 (dagger is child 9, plate body 22).
     * Only those two values were seen, so the items in the movable other_N slots are never trusted
     * here and are clicked instead. A wrong guess is caught: space that starts nothing leaves the
     * interface open and the item is clicked.
     */
    private boolean gameRemembers(Bars bar, AnvilItem item) {
        return rememberedMatches(Microbot.getVarbitValue(LAST_BAR_VARBIT), Microbot.getVarbitValue(LAST_ITEM_VARBIT),
                bar.ordinal() + 1, item.hasValidChildId() ? item.getChildId() : -1);
    }

    public static boolean rememberedMatches(int lastBar, int lastItem, int barIndex, int childId) {
        return childId > 0 && lastBar == barIndex && lastItem == childId - 8;
    }

    /**
     * Click the one interface entry for this item. The lookup is a contains-match, so "Bronze keel
     * parts" also finds "Large bronze keel parts"; clicking every match would make the wrong thing.
     */
    private boolean clickItemByName(String search, String wanted) {
        List<Widget> matches = new ArrayList<>(
                Rs2Widget.findWidgetsWithAction(search, ANVIL_WIDGET_GROUP, 0, false).keySet());
        List<String> labels = new ArrayList<>();
        for (Widget widget : matches) {
            String name = widget.getName();
            String text = widget.getText();
            labels.add(Text.removeTags(name != null && !name.isEmpty() ? name : text == null ? "" : text));
        }
        int pick = pickMatch(labels, wanted);
        return pick >= 0 && Rs2Widget.clickWidget(matches.get(pick));
    }

    /** The entry named exactly as wanted, else the shortest label (the plain item, not a variant). */
    public static int pickMatch(List<String> labels, String wanted) {
        int best = -1;
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i) == null ? "" : labels.get(i);
            if (label.equalsIgnoreCase(wanted)) return i;
            if (best < 0 || label.length() < labels.get(best).length()) best = i;
        }
        return best;
    }

    /** Count what the session really used: a level-up or a timeout ends a load part-way. */
    private void creditSession(int materialsLeft, int materialsPerItem) {
        int used = Math.max(0, ctx.materialsAtStart - materialsLeft);
        int made = used / materialsPerItem * activeItem.getOutputPerAction();   // nails come 15 a bar
        itemsMade.addAndGet(made);
        if (made > 0) credits.add(new long[]{System.currentTimeMillis(), made});
        if (this.materialsLeft >= 0) this.materialsLeft = Math.max(0, this.materialsLeft - used);
        ctx.resetSession();
    }

    /** When, if at all, the cursor does something during this load. 0 = it stays where it is. */
    private long rollIdleMove(int items) {
        if (rng.nextInt(100) >= habits.idleMovePct) return 0;
        // Towards the end, ready for the trip. An item is 5 ticks and the last bar goes about a
        // tick after its swing starts: live, items x 3 s landed after the load was over.
        long lead = AnvilHabits.logNormal(rng, 1_200, 6_000);
        return System.currentTimeMillis() + Math.max(1500, (items - 1) * 3000L - lead);
    }

    /**
     * A load is a minute of nothing to do. Mostly the cursor stays where the last click left it;
     * some loads it drifts to the bank ready for the next trip, or leaves the window altogether.
     */
    private void idleCursor() {
        if (ctx.idleMoveAtMs == 0 || System.currentTimeMillis() < ctx.idleMoveAtMs) return;
        ctx.idleMoveAtMs = 0;
        if (rng.nextInt(100) < habits.offScreenPct) {
            debug("Idle: cursor off the window");
            Rs2Antiban.moveMouseOffScreen();
            ctx.cursorLeftWindow = true;
            return;
        }
        // Aimed as the click is, so the click finds the cursor already on it and presses in place.
        Rs2TileObjectModel booth = choose(nearby("Bank booth", bankLocation), bankLocation);
        Shape outline = booth == null ? null : booth.getClickbox();
        java.awt.Point aim = outline == null ? null : pointInside(outline);
        debug("Idle: resting on the bank booth" + (aim == null ? " - no booth on screen (" + (booth == null ? "none found" : "no clickbox spot") + ")" : " at " + aim.x + "," + aim.y));
        if (aim != null) Microbot.getNaturalMouse().moveTo(aim.x, aim.y);
    }

    /** The gap between the last bar and the next action, or now and then a short break. */
    private void pauseAfterLoad(boolean cursorLeftWindow, long loadMs) {
        if (Rs2AntibanSettings.takeMicroBreaks && Microbot.isPluginEnabled(BreakHandlerPlugin.class)
                && rng.nextDouble() < habits.microBreakChance) {
            // Only the Break Handler ends a micro break, which is why it must be on to start one.
            info("Taking a short break");
            Rs2AntibanSettings.microBreakActive = true;
            BreakHandlerScript.setLockState(false);
            return;
        }
        sleep((int) habits.loadEndDelayMs(rng, cursorLeftWindow, loadMs));
    }

    private State doRecovery() {
        ctx.resetSession();

        // Each pass through here without an intervening success is a failed attempt. Bail out rather
        // than loop forever on something that is never going to work.
        if (++consecutiveRecoveries > MAX_CONSECUTIVE_RECOVERIES) {
            stop("Gave up after " + MAX_CONSECUTIVE_RECOVERIES + " failed attempts in a row"
                    + (lastFailure != null ? " (last problem: " + lastFailure + "). " : ". ")
                    + "Check that " + effectiveItemName(activeItem, activeBar)
                    + " can be made from " + activeBar + " at your Smithing level.");
            return State.DETERMINE;
        }

        info("Recovering (attempt " + consecutiveRecoveries + "/" + MAX_CONSECUTIVE_RECOVERIES + ")");

        if (Rs2Bank.isOpen()) {
            debug("Recovery: closing bank");
            if (System.currentTimeMillis() - stateEnteredMs < 10_000) consecutiveRecoveries--;
            Rs2Bank.closeBank();
            return State.RECOVERY;
        }

        if (Rs2Player.getWorldLocation() == null) {
            debug("Recovery: waiting for location...");
            return State.RECOVERY;
        }

        if (isNearAnvil() || isNearBank()) {
            debug("Recovery: at a known spot, re-evaluating");
            react(900);
            return State.DETERMINE;
        }

        debug("Recovery: walking back to the bank");
        Rs2Walker.walkTo(bankLocation);
        cameraSettled = false; // the walker moves the camera; frame the work area again on arrival
        return State.DETERMINE;
    }

    /**
     * What Deposit-All leaves in the inventory. A hammer in the equipment slot already smiths, so a
     * carried one is dead weight then; with nothing worn, every usable hammer is kept. The material
     * is matched by exact name when one is given (large keels), otherwise by id.
     */
    public static boolean keepsItem(boolean hammerWorn, int materialId, String materialName,
                                    int itemId, String itemName) {
        if (!hammerWorn && isHammer(itemId)) return true;
        return materialName != null ? materialName.equals(itemName) : itemId == materialId;
    }

    public static boolean isHammer(int id) {
        for (int hammerId : HAMMER_IDS) {
            if (hammerId == id) return true;
        }
        return false;
    }

    private boolean hasHammer() {
        return Rs2Inventory.hasItem(HAMMER_IDS) || Rs2Equipment.isWearing(HAMMER_IDS);
    }

    /** Exact name for keel parts: a contains-match would count "Large bronze keel parts" too. */
    private int countMaterial(MatContext mat) {
        return mat.useName ? Rs2Inventory.count(mat.name, true) : Rs2Inventory.count(mat.id);
    }

    private boolean isNearAnvil() {
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        return playerLoc != null && playerLoc.distanceTo(anvilLocation) <= ANVIL_INTERACT_DISTANCE;
    }

    private boolean isNearBank() {
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        return playerLoc != null && playerLoc.distanceTo(bankLocation) <= BANK_INTERACT_DISTANCE;
    }

    /**
     * Issue a walk, but only when we are not already on our way: the tick loop calls this every
     * time it finds us short of the target, and re-issuing it each tick spams clicks.
     */
    private void walkTowards(WorldPoint target, BooleanSupplier arrived) {
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null || Rs2Player.isMoving()) return;
        react(450);
        if (playerLoc.distanceTo(target) <= WALK_FAST_CANVAS_DISTANCE) {
            if (!Rs2Walker.walkFastCanvas(target)) Rs2Walker.walkTo(target);
        } else {
            Rs2Walker.walkTo(target);
            cameraSettled = false; // the walker moves the camera; frame the work area again on arrival
        }
        sleepUntil(() -> Rs2Player.isMoving() || arrived.getAsBoolean(), 2000);
    }

    private boolean isSmithingWidgetOpen() {
        return Rs2Widget.isSmithingWidgetOpen()
                || Rs2Widget.isWidgetVisible(ANVIL_WIDGET_GROUP, ANVIL_WIDGET_ALL_CHILD);
    }

    /** Objects of this name around a stand tile, nearest first. */
    private List<Rs2TileObjectModel> nearby(String name, WorldPoint anchor) {
        List<Rs2TileObjectModel> found = new ArrayList<>(Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .withName(name)
                .within(anchor, OBJECT_SEARCH_RADIUS)
                .toListOnClientThread());
        found.sort(Comparator.comparingInt(o -> o.getWorldLocation().distanceTo(anchor)));
        return found;
    }

    /** The nearest one, or now and then the next along when it is about as close. */
    private Rs2TileObjectModel choose(List<Rs2TileObjectModel> nearestFirst, WorldPoint anchor) {
        if (nearestFirst.isEmpty()) return null;
        if (nearestFirst.size() > 1 && rng.nextInt(100) < habits.otherSpotPct
                && nearestFirst.get(1).getWorldLocation().distanceTo(anchor)
                <= nearestFirst.get(0).getWorldLocation().distanceTo(anchor) + 2) {
            return nearestFirst.get(1);
        }
        return nearestFirst.get(0);
    }

    private boolean interactWithAnvil() {
        try {
            Rs2TileObjectModel anvil = choose(nearby("Anvil", anvilLocation), anvilLocation);
            if (anvil == null) {
                debug("No anvil found");
                return false;
            }
            return clickLikeAPlayer(anvil, "Smith");
        } catch (Exception ex) {
            info("Anvil click failed: " + ex);
            Microbot.logStackTrace("AnvilSmither", ex);
            return false;
        }
    }

    private boolean interactWithBank() {
        try {
            Rs2TileObjectModel booth = choose(nearby("Bank booth", bankLocation), bankLocation);
            if (booth != null) {
                return clickLikeAPlayer(booth, "Bank");
            }
            // Fall back to a banker, NOT to a loose name match: Prifddinas keeps a "Bank deposit box"
            // 3 tiles from the counter, and a deposit box leaves the bank unreadable.
            Rs2NpcModel banker = Microbot.getRs2NpcCache().query()
                    .withNameContains("Banker")
                    .nearestOnClientThread(bankLocation, OBJECT_SEARCH_RADIUS);
            if (banker == null) {
                debug("No bank booth or banker found");
                return false;
            }
            return banker.click("Bank");
        } catch (Exception ex) {
            info("Bank click failed: " + ex);
            Microbot.logStackTrace("AnvilSmither", ex);
            return false;
        }
    }

    /**
     * Herbi Hunter's click, cut down for a target that does not move: aim inside the outline, let
     * the game show the option under the cursor, and only then press, with the game's own entry.
     * The client's click sends its entry wherever the cursor lands; the live probe showed the booth
     * click landing on the poll booth beside it and presses arriving with nothing under the cursor.
     * When the option never shows (covered, off screen) the client's click is the fallback.
     */
    private boolean clickLikeAPlayer(Rs2TileObjectModel object, String action) {
        // Already resting on it (the idle hover over the bank): press where it is. Aiming again at
        // the thing under the cursor was a visible shuffle on the booth before every such click.
        NewMenuEntry resting = topMenuEntry();
        if (leftClickDoes(resting, action, object.getId())) {
            java.awt.Point at = Microbot.getMouse().getMousePosition();
            Microbot.getMouse().click(new Point(at.x, at.y), resting);
            return true;
        }
        // Off screen: one eased turn towards it. The client's fallback click below turns at 3x speed.
        if (object.getClickbox() == null) faceGently(object.getWorldLocation());
        for (int attempt = 0; attempt < 3; attempt++) {
            Shape outline = object.getClickbox();
            java.awt.Point aim = outline == null ? null : pointInside(outline);
            if (aim == null) break;
            // The natural mouse walks the cursor there. getMouse().move() is the single step it is
            // built from: used for the whole trip, the cursor jumped straight to the target.
            Microbot.getNaturalMouse().moveTo(aim.x, aim.y);
            sleep(Rs2Random.between(70, 110));
            long until = System.currentTimeMillis() + Rs2Random.between(380, 520);
            NewMenuEntry top = topMenuEntry();
            while (!leftClickDoes(top, action, object.getId()) && System.currentTimeMillis() < until) {
                sleep(Rs2Random.between(10, 16));   // under a client frame
                top = topMenuEntry();
            }
            if (leftClickDoes(top, action, object.getId())) {
                java.awt.Point at = Microbot.getMouse().getMousePosition();
                Microbot.getMouse().click(new Point(at.x, at.y), top);
                return true;
            }
            debug(action + ": the cursor was not on it" + (top == null ? "" : " (" + top.getOption() + " " + top.getTarget() + ")") + ", aiming again");
        }
        debug(action + ": could not get it under the cursor, using the client's click");
        return object.click(action);
    }

    /**
     * The anvil clicked past the open bank window. Only where it shows beside the window, and only
     * once the game's own left click there is Smith: anything else and nothing is pressed (false).
     */
    private boolean clickAnvilFromBank() {
        Rs2TileObjectModel anvil = choose(nearby("Anvil", anvilLocation), anvilLocation);
        Shape outline = anvil == null ? null : anvil.getClickbox();
        java.awt.Point aim = outline == null ? null : pointInside(outline);
        if (aim == null) return false;
        Widget frame = Rs2Widget.getWidget(InterfaceID.Bankmain.FRAME);
        Rectangle covered = frame == null ? null : frame.getBounds();
        if (covered == null || covered.contains(aim)) {
            debug("Anvil behind the bank window, closing it first");
            return false;
        }
        Microbot.getNaturalMouse().moveTo(aim.x, aim.y);
        long until = System.currentTimeMillis() + Rs2Random.between(380, 520);
        NewMenuEntry top = topMenuEntry();
        while (!leftClickDoes(top, "Smith", anvil.getId()) && System.currentTimeMillis() < until) {
            sleep(Rs2Random.between(10, 16));
            top = topMenuEntry();
        }
        if (!leftClickDoes(top, "Smith", anvil.getId())) {
            debug("Anvil from the bank: left click there is " + (top == null ? "nothing" : top.getOption() + " " + top.getTarget()));
            return false;
        }
        java.awt.Point at = Microbot.getMouse().getMousePosition();
        Microbot.getMouse().click(new Point(at.x, at.y), top);
        debug("Clicked the anvil from the bank");
        return true;
    }

    /** What a left click would do right now. A copy: the client reuses its entry objects. */
    private static NewMenuEntry topMenuEntry() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            MenuEntry[] entries = Microbot.getClient().getMenu().getMenuEntries();
            if (entries.length == 0) return null;
            MenuEntry e = entries[entries.length - 1];
            return new NewMenuEntry(e.getOption(), e.getParam0(), e.getParam1(), e.getType().getId(),
                    e.getIdentifier(), e.getItemId(), e.getTarget());
        }).orElse(null);
    }

    /** id -1 = any object with that action. */
    static boolean leftClickDoes(MenuEntry top, String action, int id) {
        return top != null && top.getOption() != null && top.getOption().equalsIgnoreCase(action)
                && (id == -1 || top.getIdentifier() == id);
    }

    /**
     * Where the cursor lands on a target: scattered about its middle the way a hand does, a sixth of
     * its size either way. The client's own pick put every anvil click in the same 4 by 3 pixels
     * (live probe, 9 clicks). A point on a thin edge reads "Walk here"; the caller aims again.
     */
    static java.awt.Point pointInside(Shape outline) {
        Rectangle r = outline.getBounds();
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < 40; i++) {
            int x = (int) Math.round(r.getCenterX() + rnd.nextGaussian() * r.width / 6.0);
            int y = (int) Math.round(r.getCenterY() + rnd.nextGaussian() * r.height / 6.0);
            if (outline.contains(x, y)) return new java.awt.Point(x, y);
        }
        return null;
    }

    /** Per-tick detail, logged only when the detailed log is on. */
    private void debug(String msg) {
        lastDetail = msg;
        if (config != null && config.sDebug()) {
            write(msg);
        }
    }

    /** Milestones: sessions, loads, recoveries, stops. */
    private void info(String msg) {
        write(msg);
    }

    /** Collapses consecutive repeats and tags the state, or the fast loop would spam. */
    private void write(String msg) {
        String line = "[" + state + "] " + msg;
        if (line.equals(lastLogged)) return;
        lastLogged = line;
        Microbot.log("AnvilSmither " + line);
    }

    public void stop(String message) {
        stop(message, true);
    }

    /**
     * @param mayLogout false for stops at set-up (level, quest, a recipe that cannot work): the
     *                  player is at the keyboard and has only to change a setting
     */
    public void stop(String message, boolean mayLogout) {
        Microbot.log("AnvilSmither stopped: " + message
                + " (ran " + formatDuration(getElapsedTime())
                + ", " + itemsMade.get() + " items, " + getXpGained() + " xp"
                + (alchCasts > 0 ? ", " + alchCasts + " alched" : "") + ")");
        running = false;

        if (logout && mayLogout) {
            logOutLikeAPlayer();
        }
        // A pop-up only for set-up stops, where the player is there and has a setting to change.
        // Running out is in the log line above; nobody is there to click a pop-up away.
        if (!mayLogout) javax.swing.SwingUtilities.invokeLater(() -> Microbot.showMessage(message));

        shutdown();
        Microbot.stopPlugin(AnvilSmitherPlugin.class);
    }

    /**
     * The client's logout sends its click from a random point anywhere on the canvas, and the stop
     * used to fire it in the same second as the last bank click: seen live as the cursor flying off
     * and the account logging out with the bank still open. Close the bank, take a moment to notice
     * the bars are gone, then click the logout button itself.
     */
    private void logOutLikeAPlayer() {
        if (!Microbot.isLoggedIn()) return;
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
        }
        sleep((int) AnvilHabits.logNormal(rng, 2_500, 12_000));
        // The client drops our clicks while the player has the mouse, and cuts the wait above short.
        // Seen live: the stack ran out, the player took the mouse, no logout and no word why.
        if (InputArbiter.isHuman()) {
            Microbot.log("AnvilSmither: you have the mouse, so not logging out");
            return;
        }
        Microbot.log("AnvilSmither: logging out");
        Microbot.getEventBus().post(new LogoutRequested());   // tells the client's auto-login this is meant
        Rs2Keyboard.resetEnter();
        Rs2Tab.switchTo(InterfaceTab.LOGOUT);
        react(600);
        if (Rs2Widget.isWidgetVisible(LOGOUT_GROUP, LOGOUT_BUTTON_CHILD)
                && Rs2Widget.clickWidget(LOGOUT_GROUP, LOGOUT_BUTTON_CHILD)
                && sleepUntil(() -> !Microbot.isLoggedIn(), 4000)) {
            return;
        }
        // World switcher showing, or the click did not take: the client's way still logs out.
        Rs2Player.logout();
    }

    private static String formatDuration(long ms) {
        long s = ms / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    @Override
    public void shutdown() {
        currentTaskId = 0;
        ctx.resetSession();
        running = false;
        // The base class cancels the loop and clears what a stopped script can leave behind.
        super.shutdown();
        waitingForBreak = false;
        BreakHandlerScript.setLockState(false);
        if (breakPrep != null) {
            breakPrep.close();
            breakPrep = null;
        }
        if (!antibanConfigured) return;   // a rejected start changed nothing
        antibanConfigured = false;
        boolean microBreaks = Rs2AntibanSettings.takeMicroBreaks;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.takeMicroBreaks = microBreaks;
        boolean[] saved = savedAntiban;
        if (saved != null) {
            Rs2AntibanSettings.naturalMouse = saved[0];
            Rs2AntibanSettings.simulateFatigue = saved[1];
            Rs2AntibanSettings.simulateMistakes = saved[2];
            Rs2AntibanSettings.moveMouseRandomly = saved[3];
            Rs2AntibanSettings.moveMouseOffScreen = saved[4];
            savedAntiban = null;
        }
    }

    public long getElapsedTime() {
        return startTime > 0 ? System.currentTimeMillis() - startTime : 0;
    }

    public int getXpGained() {
        if (startXp < 0) return 0;
        if (!Microbot.isLoggedIn() || Microbot.getClient() == null) return 0;
        return Math.max(0, Microbot.getClient().getSkillExperience(Skill.SMITHING) - startXp);
    }

    public int getXpPerHour() {
        int gained = getXpGained();
        if (gained <= 0) return 0;
        long elapsed = getElapsedTime();
        if (elapsed < 5000) return 0;   // first seconds inflate the rate
        double hours = elapsed / 3600000.0;
        return (int) (gained / hours);
    }

    /** True once the bars are gone and the alch stage has begun: the panel then shows alch figures. */
    public boolean isAlching() {
        return alching;
    }

    public int getAlchCasts() {
        return alchCasts;
    }

    public long getAlchElapsed() {
        return alchStartMs > 0 ? System.currentTimeMillis() - alchStartMs : 0;
    }

    public int getAlchXpGained() {
        if (alchStartMs == 0 || !Microbot.isLoggedIn() || Microbot.getClient() == null) return 0;
        return Math.max(0, Microbot.getClient().getSkillExperience(Skill.MAGIC) - alchStartXp);
    }

    /** {time, 1} for each alch, oldest first. */
    public List<long[]> getAlchCredits() {
        return alchCredits;
    }

    /** Items still to alch, -1 before the first stack is out. */
    public int getAlchLeft() {
        return alchLeft;
    }

    public String getAlchName() {
        return alchName;
    }

    /** {time, items} for each finished load, oldest first. */
    public List<long[]> getCredits() {
        return credits;
    }

    /** Bars (or keel parts) left in the bank and inventory, -1 until the bank has been opened. */
    public int getMaterialsLeft() {
        return materialsLeft;
    }

    /** "bars", or "keel parts" for large keels, which are made from those. */
    public String getMaterialWord() {
        return activeItem != null && activeItem.isLargeKeel() ? "keel parts" : "bars";
    }

    /** Bars (or keel parts) one item takes. */
    public int getMaterialsPerItem() {
        return activeItem == null ? 1 : Math.max(1, activeItem.getRequiredBars());
    }

    public int getItemsMade() {
        return itemsMade.get();
    }

    /** Items in the load being smithed right now, 0 between loads. */
    public int getPendingItems() {
        return ctx.lastMakeStarted > 0 ? ctx.maxFullItems : 0;
    }

    /** What the panel says the player is doing, in plain words. */
    public String getActivity() {
        if (Rs2AntibanSettings.microBreakActive || waitingForBreak) return "Taking a break";
        switch (state) {
            case SMITHING:
                return ctx.lastMakeStarted > 0 ? "Smithing " + getTargetName() : "Going to the anvil";
            case BANKING:
                return "Banking";
            case ALCHING:
                return "Alching " + alchName;
            case RECOVERY:
                return "Recovering";
            default:
                return "Checking the inventory";
        }
    }

    /** The item with its metal, for the panel: "Rune scimitar", "Adamant keel parts", "Iron spit". */
    public String getTargetName() {
        return activeItem == null ? "-" : displayName(activeItem, activeBar);
    }

    public static String displayName(AnvilItem item, Bars bar) {
        if (item.isKeelItem() || item.getOnlyBar() != null) return effectiveItemName(item, bar);
        return capitalise(bar.getKeelMetal()) + " " + item.getName().toLowerCase();
    }

    /**
     * The item name as the anvil interface spells it. Keel names use the game's metal word, which is
     * NOT the bar name minus " bar": an Adamantite bar makes "Adamant keel parts" and a Runite bar
     * makes "Rune keel parts". Large variants keep the metal lowercase ("Large adamant keel parts").
     */
    public static String effectiveItemName(AnvilItem item, Bars bar) {
        if (!item.isKeelItem()) return item.getName();
        String metal = bar.getKeelMetal();
        return item.isLargeKeel() ? "Large " + metal + " keel parts" : capitalise(metal) + " keel parts";
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Non-null describes why this bar/item pairing can never work, for an up-front stop. */
    public static String rejectionReason(Bars bar, AnvilItem item) {
        Bars only = item.getOnlyBar();
        if (only != null && only != bar) {
            return item.getName() + " can only be made from a " + only + ". Change the bar, or pick another item.";
        }
        return null;
    }

    /** What we consume: bars for everything except large keels, which use regular keel parts. */
    private MatContext getMatContext(Bars bar, AnvilItem item) {
        if (item.isLargeKeel()) {
            return new MatContext(capitalise(bar.getKeelMetal()) + " keel parts", true, 0);
        }
        return new MatContext(bar.toString(), false, bar.getId());
    }
}
