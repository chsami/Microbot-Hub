package net.runelite.client.plugins.microbot.droslayer;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro;
import net.runelite.client.plugins.microbot.droslayer.walker.DroSlayerWalkerHost;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlayStyle;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.LoginManager;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

/** Shared autonomous services for the standalone Dro Slayer plugin. */
public final class DroSlayerRuntime {
    private static BaseProfileDro profile;
    private static DroSlayerWalkerBridge walker;
    private static volatile boolean profileHoldingWork;
    private static volatile BankLocation bankTarget;
    private static volatile boolean everLoggedIn;
    private static volatile long loggedOutSince;
    private static volatile long nextReconnectAttemptAt;
    private static volatile int reconnectAttempt;
    private static volatile DroSlayerState lastState;

    // Per-travel dungeon handoff state. Once Dro Slayer has performed the curated
    // entrance interaction, Dro Slayer's walker owns the real interior destination,
    // including any internal stairs/shortcuts/plane changes.
    private static volatile WorldPoint hybridApproach;
    private static volatile WorldPoint hybridTarget;
    private static volatile String hybridLocationName;
    private static volatile boolean hybridEntranceCommitted;

    private static final long UNEXPECTED_LOGOUT_GRACE_MS = 12_000L;

    private DroSlayerRuntime() {}

    public static synchronized void start(DroSlayerConfig config, ConfigManager configManager,
                                          DroSlayerWalkerHost walkerHost) {
        shutdown();

        BaseProfileDro.Settings settings = new BaseProfileDro.Settings()
                .nativeAntibanEnabled(true)
                .activity(Activity.GENERAL_SLAYER)
                .activityIntensity(ActivityIntensity.MODERATE)
                .playStyle(PlayStyle.MODERATE)
                .mouseActivity(config.mouseActivity())
                .parkSide(config.parkSide())
                .customBreaksEnabled(config.smartBreaks())
                .breakIntervals(config.breakIntervalMin(), config.breakIntervalMax())
                .logoutBreakChance(config.logoutBreakChance())
                .afkBreakDuration(config.afkBreakMin(), config.afkBreakMax())
                .logoutBreakDuration(config.logoutBreakMin(), config.logoutBreakMax())
                .postLoginSettleSeconds(config.postLoginSettleSeconds())
                .overlayEnabled(false)
                .writeBreakStatusToMicrobot(true);

        profile = new BaseProfileDro(settings)
                .setBreakSettingsUpdater(liveBreakSettings -> liveBreakSettings
                        .customBreaksEnabled(config.smartBreaks())
                        .breakIntervals(config.breakIntervalMin(), config.breakIntervalMax())
                        .logoutBreakChance(config.logoutBreakChance())
                        .afkBreakDuration(config.afkBreakMin(), config.afkBreakMax())
                        .logoutBreakDuration(config.logoutBreakMin(), config.logoutBreakMax())
                        .postLoginSettleSeconds(config.postLoginSettleSeconds()))
                .setNativeTemplateApplier(() -> Rs2Antiban.antibanSetupTemplates.applyCombatSetup());
        profile.start();
        walker = new DroSlayerWalkerBridge(walkerHost);
        profileHoldingWork = false;
        bankTarget = null;
        everLoggedIn = Microbot.isLoggedIn();
        loggedOutSince = 0L;
        nextReconnectAttemptAt = 0L;
        reconnectAttempt = 0;
        lastState = null;
        resetHybridHandoff();
        DroSlayerLocalNavigator.reset();
    }

    public static synchronized void shutdown() {
        profileHoldingWork = false;
        bankTarget = null;
        everLoggedIn = false;
        loggedOutSince = 0L;
        nextReconnectAttemptAt = 0L;
        reconnectAttempt = 0;
        lastState = null;
        resetHybridHandoff();
        DroSlayerLocalNavigator.reset();
        if (walker != null) {
            walker.shutdown();
            walker = null;
        }
        if (profile != null) {
            profile.shutdown();
            profile = null;
        }
    }

    public static void tick(DroSlayerConfig config) {
        BaseProfileDro p = profile;
        if (p == null || config == null) return;

        boolean loggedIn = Microbot.isLoggedIn();

        // BaseProfileDro owns its scheduled-break logout/login cycle. This second path is only for
        // an unexpected disconnect after Dro Slayer has already been running successfully. The
        // grace period prevents a normal world hop from being treated as a disconnect.
        if (loggedIn) {
            everLoggedIn = true;
            loggedOutSince = 0L;
            nextReconnectAttemptAt = 0L;
            reconnectAttempt = 0;
        } else if (config.autoRecoverDisconnects() && everLoggedIn && !p.isBreakActive()) {
            long now = System.currentTimeMillis();
            if (loggedOutSince == 0L) loggedOutSince = now;
            profileHoldingWork = true;
            if (now - loggedOutSince >= UNEXPECTED_LOGOUT_GRACE_MS && now >= nextReconnectAttemptAt) {
                reconnectAttempt++;
                nextReconnectAttemptAt = now + reconnectBackoffMs(reconnectAttempt);
                try {
                    boolean initiated = LoginManager.login();
                    Microbot.log("[DroSlayer] Unexpected logout recovery attempt " + reconnectAttempt
                            + " initiated=" + initiated);
                } catch (Exception ex) {
                    Microbot.log("[DroSlayer] Unexpected logout recovery failed: " + ex.getMessage());
                }
            }
            return;
        }

        boolean moving = loggedIn && Rs2Player.isMoving();
        boolean inCombat = loggedIn && Rs2Combat.inCombat();
        boolean bankOpen = loggedIn && Rs2Bank.isOpen();
        DroSlayerState state = DroSlayerPlugin.getState();

        // Every new TRAVELING leg starts outside the dungeon contract again. This prevents
        // a previous task trip (or a bank/regear trip for the same task) from leaving the
        // interior handoff latched.
        if (state == DroSlayerState.TRAVELING && lastState != DroSlayerState.TRAVELING) {
            resetHybridHandoff();
        }
        if (state != lastState) {
            Microbot.log("[DroSlayer] State " + lastState + " -> " + state);
        }
        lastState = state;

        boolean safeState = !wildernessSafetyOwnsActions() && (state == DroSlayerState.IDLE
                || state == DroSlayerState.BANKING
                || state == DroSlayerState.TRAVELING
                || state == DroSlayerState.GETTING_TASK);
        boolean safeToBreak = loggedIn && safeState && !moving && !inCombat && !bankOpen
                && (walker == null || !walker.isRouteActive());
        boolean idleOpportunity = !wildernessSafetyOwnsActions() && loggedIn && inCombat && !bankOpen
                && (walker == null || !walker.isRouteActive());
        profileHoldingWork = p.tick(safeToBreak, idleOpportunity, config.mouseActivity());
    }

    public static void syncWildernessContext(DroSlayerConfig config, boolean combatAllowed, boolean preferLogout) {
        if (walker == null) return;
        DroSlayerState state = DroSlayerPlugin.getState();
        boolean enabled = config != null && config.enablePlugin() && config.slayerMaster() == DroSlayerMaster.KRYSTILIA;
        boolean combat = combatAllowed && (state == DroSlayerState.AT_LOCATION || state == DroSlayerState.FIGHTING
                || state == DroSlayerState.WILDERNESS_ESCAPE);
        walker.setWildernessContext(enabled, combat, preferLogout);
    }

    public static void releaseWildernessContext() { if (walker != null) walker.releaseWildernessContext(); }
    public static boolean isWildernessPlayerAttackActive() { return walker != null && walker.isWildernessPlayerAttackActive(); }
    public static String wildernessEscapeState() { return walker == null ? "IDLE" : walker.wildernessEscapeState(); }
    public static void acknowledgeWildernessEscape() { if (walker != null) walker.acknowledgeWildernessEscape(); }
    public static boolean wildernessSafetyOwnsActions() {
        DroSlayerState state = DroSlayerPlugin.getState();
        return state == DroSlayerState.WILDERNESS_RECOVERY || state == DroSlayerState.WILDERNESS_ESCAPE
                || !"IDLE".equals(wildernessEscapeState());
    }

    public static boolean shouldPauseWork() { return profileHoldingWork; }

    public static String profileStatus() {
        return profile == null ? "Starting" : profile.getProfileStatus();
    }

    public static String breakDisplay() {
        return profile == null ? "Off" : profile.getBreakDisplay();
    }

    private static String actionSource() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if (frame.getClassName().startsWith("net.runelite.client.plugins.microbot.droslayer.")
                    && !frame.getClassName().equals(DroSlayerRuntime.class.getName())) {
                return frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1)
                        + "." + frame.getMethodName() + ":" + frame.getLineNumber();
            }
        }
        return "runtime";
    }

    public static boolean performAction(BaseProfileDro.ActionPhase phase, boolean urgent, BooleanSupplier action) {
        if (action == null || profileHoldingWork) return false;
        String source = actionSource();
        BooleanSupplier logged = () -> {
            Microbot.log("[DroSlayer] Action start phase=" + phase + " urgent=" + urgent + " source=" + source);
            try {
                boolean result = action.getAsBoolean();
                Microbot.log("[DroSlayer] Action result=" + result + " source=" + source);
                return result;
            } catch (RuntimeException ex) {
                Microbot.log("[DroSlayer] Action failed source=" + source + " error=" + ex.getClass().getSimpleName());
                throw ex;
            }
        };
        BaseProfileDro p = profile;
        return p == null ? logged.getAsBoolean() : p.performAction(phase, urgent, logged);
    }

    public static void performAction(BaseProfileDro.ActionPhase phase, boolean urgent, Runnable action) {
        if (action == null || profileHoldingWork) return;
        String source = actionSource();
        Runnable logged = () -> {
            Microbot.log("[DroSlayer] Action start phase=" + phase + " urgent=" + urgent + " source=" + source);
            try {
                action.run();
                Microbot.log("[DroSlayer] Action dispatched source=" + source);
            } catch (RuntimeException ex) {
                Microbot.log("[DroSlayer] Action failed source=" + source + " error=" + ex.getClass().getSimpleName());
                throw ex;
            }
        };
        BaseProfileDro p = profile;
        if (p == null) logged.run(); else p.performAction(phase, urgent, logged);
    }

    /** Local collision-map click cadence retained for the optional legacy navigator. */
    public static int sampleLocalWalkCooldownMs() {
        return ThreadLocalRandom.current().nextInt(520, 781);
    }

    public static boolean walkAdaptive(DroSlayerConfig config, WorldPoint target, int radius, String locationName) {
        return walkFull(config, target, radius);
    }

    /** Long-range travel stays on Efficient Walker. */
    public static boolean walkFull(DroSlayerConfig config, WorldPoint target, int radius) {
        if (profileHoldingWork || target == null || walker == null) return false;
        return walker.tickTo(target, radius, DroSlayerTravelMode.FULL_TRAVEL, config.routeStallSeconds());
    }

    /** Compatibility entry point; Efficient Walker owns short-distance movement too. */
    public static boolean walkLocal(DroSlayerConfig config, WorldPoint target, int radius) {
        return walkFull(config, target, radius);
    }

    /** Compatibility entry point; submit only the final destination to Efficient Walker. */
    public static boolean walkHybridTask(DroSlayerConfig config,
                                         WorldPoint approach,
                                         WorldPoint target,
                                         int targetRadius,
                                         String locationName) {
        return walkFull(config, target, targetRadius);
    }

    private static boolean isKaruulmLocation(DroSlayerLocation location) {
        if (location == null) return false;
        switch (location) {
            case KARUULM:
            case KARUULM_HYDRAS:
            case KARUULM_DRAKES:
            case KARUULM_WYRMS:
            case KARUULM_SULPHUR_LIZARDS:
            case ALCHEMICAL_HYDRA:
                return true;
            default:
                return false;
        }
    }

    private static boolean isInsideKaruulmDungeon(WorldPoint point) {
        return point != null
                && point.getPlane() == 0
                && point.getX() >= 1240 && point.getX() <= 1365
                && point.getY() >= 10140 && point.getY() <= 10270;
    }

    private static void resetHybridHandoff() {
        hybridApproach = null;
        hybridTarget = null;
        hybridLocationName = null;
        hybridEntranceCommitted = false;
    }

    private static boolean samePoint(WorldPoint a, WorldPoint b) {
        return a == null ? b == null : a.equals(b);
    }

    private static boolean sameText(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }

    private static boolean isCatacombsLocation(DroSlayerLocation location) {
        if (location == null) return false;
        switch (location) {
            case CATACOMBS:
            case CATACOMBS_DUST_DEVILS:
            case CATACOMBS_NECHRYAEL:
            case CATACOMBS_ABYSSAL_DEMONS:
            case CATACOMBS_HELLHOUNDS:
            case CATACOMBS_BLACK_DEMONS:
            case CATACOMBS_GREATER_DEMONS:
            case CATACOMBS_FIRE_GIANTS:
            case CATACOMBS_ANKOU:
            case CATACOMBS_DAGANNOTH:
            case CATACOMBS_MUTATED_BLOODVELDS:
            case CATACOMBS_BRUTAL_BLUE_DRAGONS:
                return true;
            default:
                return false;
        }
    }

    public static boolean walkToBankAndOpen(DroSlayerConfig config, String contextName) {
        if (profileHoldingWork || walker == null) return false;
        if (Rs2Bank.isOpen()) {
            walker.cancel();
            bankTarget = null;
            return true;
        }

        BankLocation bank = bankTarget;
        if (bank == null || bank.getWorldPoint() == null) {
            bank = Rs2Bank.getNearestBank();
            bankTarget = bank;
        }
        if (bank == null || bank.getWorldPoint() == null) {
            bankTarget = null;
            return false;
        }

        if (!walkAdaptive(config, bank.getWorldPoint(), 6, contextName)) return false;
        walker.cancel();
        boolean opened = performAction(BaseProfileDro.ActionPhase.SETUP, false, (BooleanSupplier) Rs2Bank::openBank);
        if (opened) bankTarget = null;
        return opened;
    }

    public static void cancelWalk() {
        if (walker != null) walker.cancel();
    }

    /** Death changes the route origin; choose a fresh bank and discard the old trip's failure budget. */
    public static void resetRecoveryRoute() {
        cancelWalk();
        bankTarget = null;
        clearRouteFailures();
    }

    public static boolean routeActive() { return walker != null && walker.isRouteActive(); }
    public static String walkerStatus() { return walker == null ? "UNAVAILABLE" : walker.status(); }
    public static DroSlayerTravelMode walkerMode() { return walker == null ? null : walker.activeMode(); }
    public static int routeFailures() { return walker == null ? 0 : walker.getConsecutiveFailures(); }
    public static void clearRouteFailures() { if (walker != null) walker.clearFailureBudget(); }
    private static long reconnectBackoffMs(int attempt) {
        if (attempt <= 1) return 5_000L;
        if (attempt == 2) return 15_000L;
        if (attempt == 3) return 60_000L;
        return 300_000L;
    }

}
