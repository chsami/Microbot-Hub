/*
 * DroSulliuscep - Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Created by DRO (droplugins). Free and open source.
 * Reuse is permitted only under LICENSE.txt: every copy, fork or excerpt of this
 * file must keep this notice, the author credit and the license text. See CREDITS.txt.
 */
package net.runelite.client.plugins.microbot.drosulliuscep;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.util.poh.PohPresence;
import net.runelite.client.plugins.microbot.util.poh.data.MountedDigsite;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlayStyle;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.mouse.MouseMotionVariation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;

import javax.inject.Singleton;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.drosulliuscep.BaseProfileDro.ActionPhase.*;

/**
 * Recording-mastered Sulliuscep loop.
 *
 * Primary behavior learned from the supplied full run:
 * - Script-owned pendant and Mushtree stages confirm each landing;
 * - Ferox startup restores missing HP, then prepares the bank loadout once;
 * - WebWalk owns ordinary bank and pool approaches;
 * - visible Ferox pool/bank objects are clicked directly before any walker route is submitted;
 * - once Sticky Swamp is reached, WebWalk is cancelled and never owns the Sulliuscep course;
 * - follow active-tree varbit through all six Sulliusceps, using Cut;
 * - current Cut target + decoded recording movement graph own the course, from any reachable entry;
 * - after #6 -> equipped Ring of dueling -> Ferox -> pool only when HP is missing -> bank;
 * - rake remains in inventory; travel gear may remain equipped;
 * - full inventory only drops the user-selected disposable mushroom/fungus types.
 */
@Slf4j
@Singleton
public class DroSulliuscepScript extends Script
{
    private int detectedActiveTree = -1;
    private long nextCoursePlanAt;
    private volatile Future<?> treeCameraTurn;
    private volatile boolean cameraEnabled;
    private volatile long cameraGeneration;
    private long nextTreeCameraAt;

    private static final long LOOP_MS = 40L;
    private static final long OBSTACLE_RETRY_MIN_MS = 10L;
    private static final long LOCAL_RECOVERY_RETRY_MIN_MS = 5L;
    private static final long LOCAL_RECOVERY_RETRY_MAX_MS = 15L;
    private static final long SERVICE_RETRY_MS = 1_500L;
    private static final long POST_POOL_BANK_DELAY_MIN_MS = 90L;
    private static final long POST_POOL_BANK_DELAY_MAX_MS = 180L;
    private static final long TREE_WATCHDOG_MIN_MS = 8_000L;
    private static final long TREE_WATCHDOG_MAX_MS = 10_000L;
    private static final long TELEPORT_RETRY_MS = 3_500L;
    private static final long SPECIAL_RETRY_MS = 1_800L;
    private static final int LOOP_MUSHROOM_TARGET = 9;
    private static final int BITTERCAP_MUSHROOM_ID = 6004;

    private static final int SWAMP_X_MIN = 3627;
    private static final int SWAMP_X_MAX = 3697;
    private static final int SWAMP_Y_MIN = 3725;
    private static final int SWAMP_Y_MAX = 3811;

    private DroSulliuscepConfig config;
    private DroSulliuscepTravelMode travelMode = DroSulliuscepTravelMode.FEROX_DUELING;
    private final TravelRetry bankTravel = new TravelRetry();
    private final TravelRetry bankOpening = new TravelRetry();
    private final TravelRetry houseEntry = new TravelRetry();
    private boolean housePoolUsed;
    private int[] housePoolBefore;
    private boolean housePoolAnimationSeen;
    private long houseEnterReadyAt = -1, poolClickReadyAt = -1, poolAdvanceReadyAt = -1;
    private final TravelRetry houseTravel = new TravelRetry();
    private final TravelRetry housePool = new TravelRetry();
    private final TravelRetry mountedTravel = new TravelRetry();
    private final TreeApproachWatch treeApproachWatch = new TreeApproachWatch();
    private final TravelRetry thirdTreeMove = new TravelRetry();
    private final ThirdTreeAttackWatch thirdTreeAttackWatch = new ThirdTreeAttackWatch();
    private boolean houseRestored;
    private boolean fossilHouseArrivalObserved;
    private boolean thirdTreeRecovery;
    static final WorldPoint THIRD_TREE_SAFE_TILE = new WorldPoint(3682, 3775, 0);
    private BaseProfileDro profile;
    private final DroSulliuscepWebWalk webWalk = new DroSulliuscepWebWalk();
    private final TravelRetry pendantTravel = new TravelRetry();
    private final TravelRetry mushtreeTravel = new TravelRetry();
    private long nextTravelMenuAt;
    private final TravelRetry approachObjectWait = new TravelRetry();
    private String approachObjectLabel;

    private volatile DroSulliuscepState state = DroSulliuscepState.INITIALIZING;
    private volatile String status = "Starting";

    private long runStartMs;
    private int startWoodcuttingXp;
    private int treesCompleted;
    private int cyclesCompleted;
    private int lastActiveTree = -1;

    private boolean autoRetaliateApplied;
    private long autoRetaliateBankCloseAt;
    private boolean feroxServiceRequired;
    // Normal Ferox service may be requested during a swamp run, but departure is only allowed
    // after the live Sulliuscep varbit rolls from tree #6 back to tree #1. Emergency HP
    // retreats explicitly bypass that cycle-end gate.
    private boolean feroxDepartureArmed;
    private boolean emergencyFeroxRetreat;
    private String feroxReason = "";
    private boolean poolRestoredThisVisit;
    private int poolAttemptsThisVisit;
    private boolean bankServicedThisVisit;
    private boolean bankDepositedThisVisit;
    private WorldPoint lastMovementTarget;
    private long lastMovementClickAt;
    private boolean startupPrepared;
    private boolean startupBankingRequired;
    private boolean startupBankOpenAttempted;
    private long startupBankOpenAttemptAt;
    // One-way travel latch: once Ferox service has completed and the Sticky Swamp return starts,
    // do not let normal service readiness checks send us back to Ferox from Fossil Island.
    private boolean returnToSwampInProgress;

    private boolean parkedAtFifthTree;
    private long nextTreeClickAt;
    private boolean treeClickIssued;
    private int failedFullInventoryDrops;
    private int mudPitFillAttempts;
    private long nextObstacleActionAt;
    private int obstacleActionLatched = -1;
    private long obstacleActionWatchdogAt;
    private long nextTeleportAttemptAt;
    private long nextMushtreeAttemptAt;
    private long nextSpecialAttemptAt;
    private long nextLocalRecoveryClickAt;
    private long nextServiceActionAt;
    private long bankNotBeforeAt;
    private boolean rubberLoopReturnPending;
    private boolean rubberLandingResetPending;
    // After a successful Rubber-cap landing, keep normal Ferox service deferred for the
    // entire fresh six-tree cycle. The next #6 -> #1 rollover is the only place that may
    // decide to rebank (for example when food is gone). This prevents a stale/temporary
    // ordinary service request from teleporting away after tree #1 has already resumed.
    private boolean rubberCycleContinuationActive;
    private boolean rubberJumpClickIssued;
    private long rubberJumpWatchdogAt;
    private long nextMudPitAttemptAt;
    private boolean mudPitFillClickIssued;
    private long mudPitFillWatchdogAt;

    private boolean profileBreakWasActive;
    private boolean logoutObservedDuringProfileBreak;
    private boolean requirementsValidated;
    private boolean fatalError;

    public boolean run(DroSulliuscepConfig config)
    {
        stopTreeCamera();
        webWalk.cancelIfActive();
        pendantTravel.reset();
        mushtreeTravel.reset();
        approachObjectWait.reset();
        approachObjectLabel = null;
        nextTravelMenuAt = 0L;
        cameraEnabled = true;
        nextTreeCameraAt = 0L;
        shutdownProfileOnly();
        this.config = config;
        travelMode = config.travelMode();
        bankTravel.reset();
        resetHouseTravel();
        thirdTreeMove.reset();
        thirdTreeRecovery = false;
        thirdTreeAttackWatch.reset();
        initializeProfile(config);

        lastMovementTarget = null;
        lastMovementClickAt = 0L;
        runStartMs = System.currentTimeMillis();
        startWoodcuttingXp = readXp();
        treesCompleted = 0;
        cyclesCompleted = 0;
        lastActiveTree = -1;
        failedFullInventoryDrops = 0;
        mudPitFillAttempts = 0;
        detectedActiveTree = -1;
        nextCoursePlanAt = 0L;
        autoRetaliateApplied = false;
        autoRetaliateBankCloseAt = 0L;
        feroxServiceRequired = false; // Infer service from the startup location below.
        feroxDepartureArmed = false;
        emergencyFeroxRetreat = false;
        feroxReason = "";
        poolRestoredThisVisit = false;
        poolAttemptsThisVisit = 0;
        bankServicedThisVisit = false;
        bankDepositedThisVisit = false;
        startupPrepared = false;
        startupBankingRequired = false;
        startupBankOpenAttempted = false;
        startupBankOpenAttemptAt = 0L;
        returnToSwampInProgress = false;
        parkedAtFifthTree = false;
        nextTreeClickAt = 0L;
        treeClickIssued = false;
        nextObstacleActionAt = 0L;
        obstacleActionLatched = -1;
        obstacleActionWatchdogAt = 0L;
        nextTeleportAttemptAt = 0L;
        nextMushtreeAttemptAt = 0L;
        nextSpecialAttemptAt = 0L;
        nextLocalRecoveryClickAt = 0L;
        nextServiceActionAt = 0L;
        bankNotBeforeAt = 0L;
        rubberLoopReturnPending = false;
        rubberLandingResetPending = false;
        rubberCycleContinuationActive = false;
        rubberJumpClickIssued = false;
        rubberJumpWatchdogAt = 0L;
        nextMudPitAttemptAt = 0L;
        mudPitFillClickIssued = false;
        mudPitFillWatchdogAt = 0L;
        profileBreakWasActive = false;
        logoutObservedDuringProfileBreak = false;
        requirementsValidated = false;
        fatalError = false;
        state = DroSulliuscepState.INITIALIZING;
        status = "Starting";

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            BaseProfileDro owner = profile;
            try (var variation = owner == null ? MouseMotionVariation.open(null) : owner.openMouseVariation())
            {
                tick();
            }
            catch (Exception ex)
            {
                state = DroSulliuscepState.ERROR;
                status = "Error: " + safeMessage(ex);
                log.error("[DroSulliuscep] tick failed", ex);
            }
        }, 0, LOOP_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private void initializeProfile(DroSulliuscepConfig config)
    {
        BaseProfileDro.Settings settings = new BaseProfileDro.Settings()
                .nativeAntibanEnabled(true)
                .actionDelayScale(0.75)
                .activity(Activity.GENERAL_WOODCUTTING)
                .activityIntensity(ActivityIntensity.MODERATE)
                .playStyle(PlayStyle.MODERATE)
                .mouseActivity(config.mouseActivity())
                .parkSide(config.parkSide())
                .parkChances(97, 75, 50, 50)
                .customBreaksEnabled(config.smartBreaks())
                .breakIntervals(config.breakIntervalMin(), config.breakIntervalMax())
                .logoutBreakChance(config.logoutBreakChance())
                .afkBreakDuration(config.afkBreakMin(), config.afkBreakMax())
                .logoutBreakDuration(config.logoutBreakMin(), config.logoutBreakMax())
                .postLoginSettleSeconds(config.postLoginSettleSeconds())
                .actionCameraNudges(false)
                .overlayEnabled(false)
                .writeBreakStatusToMicrobot(false);

        profile = new BaseProfileDro(settings)
                .setBreakSettingsUpdater(liveBreakSettings -> liveBreakSettings
                        .customBreaksEnabled(config.smartBreaks())
                        .breakIntervals(config.breakIntervalMin(), config.breakIntervalMax())
                        .logoutBreakChance(config.logoutBreakChance())
                        .afkBreakDuration(config.afkBreakMin(), config.afkBreakMax())
                        .logoutBreakDuration(config.logoutBreakMin(), config.logoutBreakMax())
                        .postLoginSettleSeconds(config.postLoginSettleSeconds()))
                .setNativeTemplateApplier(() -> Rs2Antiban.antibanSetupTemplates.applyWoodcuttingSetup());
        profile.setFastInputDelays(true);
        profile.start();
    }

    private void tick()
    {
        if (fatalError)
        {
            return;
        }

        boolean loggedIn = Microbot.isLoggedIn() && currentPlayerLocation() != null;
        boolean moving = loggedIn && Rs2Player.isMoving();
        boolean animating = loggedIn && Rs2Player.isAnimating();

        // The swamp is not a safe place to begin a long break. Breaks become eligible at the selected bank.
        boolean safeBreak = loggedIn && nearServiceBank() && !moving && !animating && !Rs2Bank.isOpen();
        boolean idleOpportunity = loggedIn && inSwamp() && isChopping() && !moving && !thirdTreeRecovery && !(activeTreeIndex() == 2 && underNpcAttack());

        if (profile != null)
        {
            boolean holding = profile.tick(safeBreak, idleOpportunity, config.mouseActivity());
            boolean breakNow = profile.isBreakActive();

            if (breakNow && !loggedIn)
            {
                logoutObservedDuringProfileBreak = true;
            }
            if (profileBreakWasActive && !breakNow && logoutObservedDuringProfileBreak)
            {
                // Resume from wherever the game actually logged back in. The active-tree varbit
                // is authoritative, so a logout break does not force an unnecessary Ferox bank.
                autoRetaliateApplied = false;
                logoutObservedDuringProfileBreak = false;
                nextTreeClickAt = 0L;
                treeClickIssued = false;
                nextObstacleActionAt = 0L;
                nextLocalRecoveryClickAt = 0L;
            }
            profileBreakWasActive = breakNow;

            if (holding)
            {
                cancelTreeCameraWork();
                state = DroSulliuscepState.PAUSED;
                status = profile.getProfileStatus();
                return;
            }
        }

        if (!loggedIn)
        {
            autoRetaliateApplied = false;
            state = DroSulliuscepState.PAUSED;
            status = "Logged out";
            return;
        }

        maintainRunEnergy();

        if (!ensureAutoRetaliateOff())
        {
            return;
        }
        if (!requirementsValidated)
        {
            if (Rs2Player.getRealSkillLevel(Skill.WOODCUTTING) < 65)
            {
                fail("65 Woodcutting is required for Sulliusceps");
                return;
            }
            requirementsValidated = true;
        }

        trackActiveTreeProgress();

        if (travelMode.poh && !inSwamp() &&
                (!feroxServiceRequired && (returnToSwampInProgress || insideHouse())))
        {
            travelToStickySwamp();
            return;
        }

        if (handleImmediateSafetyAndInventory())
        {
            cancelTreeCameraWork();
            return;
        }

        // Rubber-cap landing/reset owns the hand-off before ANY normal Ferox/outbound travel logic.
        // This must be evaluated before feroxServiceRequired: a stale deferred service request from
        // the just-finished course must never steal control after a successful Rubber-cap jump.
        // The landing is on plane 1 around 3664,3810; while there, NEVER submit an WebWalk
        // request. Let the landing transition settle locally, then resume tree #1. An empty
        // pit can be refilled after chopping supplies nine mushrooms; it must not block chopping.
        if (atRubberCapLanding())
        {
            rubberLandingResetPending = true;
            returnToSwampInProgress = false;
            feroxDepartureArmed = false;
            // If we successfully took the Rubber-cap loop, food/regear was already validated at
            // cycle rollover. Any ordinary deferred Ferox request is stale here. Preserve only a
            // real emergency retreat.
            if (!emergencyFeroxRetreat)
            {
                feroxServiceRequired = false;
                feroxReason = "";
            }
            webWalk.cancelIfActive();
        }

        if (feroxServiceRequired && (feroxDepartureArmed || emergencyFeroxRetreat)
                && !atRubberCapLanding())
        {
            handleFeroxService();
            return;
        }

        if (rubberLandingResetPending)
        {
            webWalk.cancelIfActive();

            if (!mudPitFilled() && countLoopMushrooms() >= LOOP_MUSHROOM_TARGET
                    && handleMudPitReload())
            {
                return;
            }

            if (inSwamp())
            {
                // Keep the local-reset latch alive until tree #1 has actually received its Cut click.
                // This prevents any generic outbound/walker branch from observing a transient state
                // between landing and the first tree interaction.
                startupPrepared = true;
                startupBankingRequired = false;
                returnToSwampInProgress = false;
                nextCoursePlanAt = 0L;
                nextLocalRecoveryClickAt = 0L;
                lastMovementTarget = null;
                state = DroSulliuscepState.TO_ACTIVE_TREE;
                status = "Rubber landing -> resume tree #1 locally";
                maybeUseAxeSpecial();
                handleSwampRun();
                return;
            }

            // The landing animation/plane transition has not finished yet.
            // Do nothing here: especially do not fall through into outboundTravelReady()/walker.
            state = DroSulliuscepState.TO_ACTIVE_TREE;
            status = "Rubber landing -> waiting for local return";
            return;
        }

        // Never leave the Sulliuscep swamp mid-cycle for NORMAL banking/supply service.
        // The Emergency Ferox HP threshold is intentionally different: it must remain a real
        // emergency escape and may leave immediately from any tree.
        boolean deferFeroxUntilCycleEnd = feroxServiceRequired
                && (inSwamp() || rubberCycleContinuationActive)
                && !feroxDepartureArmed
                && !emergencyFeroxRetreat;
        if (feroxServiceRequired && !deferFeroxUntilCycleEnd)
        {
            if (startupBankingRequired && nearServiceBank())
            {
                handleStartupFeroxBank();
            }
            else
            {
                handleFeroxService();
            }
            return;
        }

        if (inSwamp())
        {
            pendantTravel.reset();
            mushtreeTravel.reset();
            startupPrepared = true;
            startupBankingRequired = false;
            returnToSwampInProgress = false;
            webWalk.cancelIfActive();
            maybeUseAxeSpecial();
            handleSwampRun();
            return;
        }

        if (nearServiceBank())
        {
            // Never leave Ferox before checking HP and completing the startup bank setup.
            double feroxHp = currentHealthPercentage();
            if (feroxHp < 0)
            {
                state = DroSulliuscepState.INITIALIZING;
                status = "Waiting for HP state at selected bank";
                return;
            }
            if (!travelMode.poh && feroxHp < 99.0)
            {
                handleFeroxPool();
                return;
            }

            // Every Ferox startup gets one confirmed bank setup after its HP check.
            if (startupPrepared)
            {
                travelToStickySwamp();
            }
            else
            {
                startupBankingRequired = true;
                requestFeroxService("Preparing required trip items");
                handleStartupFeroxBank();
            }
            return;
        }

        // Fossil Island, House-on-the-Hill, and any other recoverable outside position:
        // once the return leg has started, keep going to Sticky Swamp. A Ring of dueling is
        // needed for the NEXT Ferox return, not for Fossil Island -> Sticky Swamp, so never
        // reverse direction mid-return merely because the ring is temporarily absent/not cached.
        if (returnToSwampInProgress || atFossilHouse() || outboundTravelReady())
        {
            travelToStickySwamp();
            return;
        }

        requestFeroxService("Recovering trip setup");
        handleFeroxService();
    }

    private boolean ensureAutoRetaliateOff()
    {
        if (autoRetaliateApplied)
        {
            return true;
        }

        state = DroSulliuscepState.INITIALIZING;
        if (Microbot.getVarbitPlayerValue(VarPlayerID.OPTION_NODEF) == 1)
        {
            autoRetaliateApplied = true;
            return true;
        }
        if (Rs2Bank.isOpen())
        {
            status = "Closing bank before disabling auto retaliate";
            long now = System.currentTimeMillis();
            if (now >= autoRetaliateBankCloseAt)
            {
                autoRetaliateBankCloseAt = now + 2_000L;
                doAction(SETUP, false, Rs2Bank::closeBank);
            }
            return false; // Observe the bank closed before trying to switch combat tabs.
        }
        status = "Auto retaliate OFF";
        if (doAction(SETUP, false, () -> Rs2Combat.setAutoRetaliate(false)))
        {
            autoRetaliateApplied = true;
            return true;
        }
        status = "Waiting to disable auto retaliate";
        return false;
    }

    private void trackActiveTreeProgress()
    {
        traceDroAction("trackActiveTreeProgress");
        int current = activeTreeIndex();
        if (lastActiveTree < 0)
        {
            lastActiveTree = current;
            return;
        }
        if (current == lastActiveTree)
        {
            return;
        }

        cancelTreeCameraWork();
        thirdTreeRecovery = false;
        thirdTreeAttackWatch.reset();
        thirdTreeMove.reset();
        int previous = lastActiveTree;
        lastActiveTree = current;
        treesCompleted++;
        nextTreeClickAt = 0L;
        treeClickIssued = false;
        nextObstacleActionAt = 0L;
        nextLocalRecoveryClickAt = 0L;

        Microbot.log("[DroSulliuscep] active tree " + (previous + 1) + " -> " + (current + 1));

        if (previous == 5 && current == 0)
        {
            cyclesCompleted++;
            // We have reached the one legitimate end-of-cycle decision point. A continuation
            // latch from the previous Rubber-cap landing no longer applies beyond this point.
            rubberCycleContinuationActive = false;
            // A loaded pit is enough for this jump. Tree #1 on the next loop supplies
            // mushrooms to refill it; spare mushrooms are not a departure requirement.
            boolean foodRebankRequired = config.useFood()
                    && !hasFoodOrSaradominBrew();
            boolean reserveRoom = enoughRoomForReserve(usableMushroomSlots(currentInventoryItems(), config.dropMode()));
            boolean rubberReady = reserveRoom && canJumpRubberCap(config.jumpRubberCapMushroom(),
                    foodRebankRequired, mudPitFilled());
            if (rubberReady)
            {
                // Exclusive hand-off: from this point until landing, the normal course navigator
                // must never issue a minimap/tree-#1 recovery click.
                rubberLoopReturnPending = true;
                rubberJumpClickIssued = false;
                rubberJumpWatchdogAt = 0L;
                nextMudPitAttemptAt = 0L;
                nextCoursePlanAt = Long.MAX_VALUE;
                nextLocalRecoveryClickAt = Long.MAX_VALUE;
                lastMovementTarget = null;
                feroxDepartureArmed = false;
                Microbot.log("[DroSulliuscep] six-tree cycle complete; Rubber cap object hand-off armed");
            }
            else
            {
                rubberLoopReturnPending = false;
                feroxDepartureArmed = true;
                String reason;
                if (foodRebankRequired)
                {
                    reason = " - no food remains; rebank required";
                }
                else if (!config.jumpRubberCapMushroom())
                {
                    reason = "";
                }
                else if (!reserveRoom)
                {
                    reason = " - 15 or fewer usable mushroom slots; rebank required";
                }
                else
                {
                    reason = " - mud pit not loaded";
                }
                requestFeroxService("Six-tree cycle complete" + reason);
                Microbot.log("[DroSulliuscep] six-tree cycle complete; selected bank reset armed" + reason);
            }
        }
    }

    private boolean handleImmediateSafetyAndInventory()
    {
        traceDroAction("handleImmediateSafetyAndInventory");
        double hp = currentHealthPercentage();
        if (hp < 0)
        {
            state = DroSulliuscepState.INITIALIZING;
            status = "Waiting for HP state";
            return true;
        }

        boolean initialFeroxStartup = nearServiceBank()
                && !startupPrepared
                && treesCompleted == 0
                && cyclesCompleted == 0;

        if (hp <= config.emergencyRetreatHp())
        {
            // If we are already at Ferox on initial startup, do not arm a full service cycle.
            // The Ferox pre-departure gate below restores HP first, then banks only if gear
            // or inventory cleanup is actually required.
            if (!initialFeroxStartup)
            {
                requestEmergencyFeroxRetreat("Emergency HP " + Math.round(hp) + "%");
            }
            return false;
        }

        // Do not begin or resume a swamp run already below the normal eating threshold.
        // This check applies even when optional food is disabled because Ferox is the recovery.
        if (treesCompleted == 0 && cyclesCompleted == 0 && hp <= config.eatAt())
        {
            if (!initialFeroxStartup)
            {
                requestFeroxService("Startup low HP " + Math.round(hp) + "%");
            }
            return false;
        }

        // Ferox service owns recovery and inventory; do not eat or drop bank supplies here.
        if (nearServiceBank()) return false;

        if (config.useFood() && hp <= config.eatAt() && hasFoodOrSaradominBrew())
        {
            state = DroSulliuscepState.EATING;
            status = "Eating at " + Math.round(hp) + "% HP";
            doAction(MAINTENANCE, true, this::consumeFoodOrSaradominBrew);
            return true;
        }

        // Preserve inventory only when Ferox departure can actually execute now. A NORMAL
        // Ferox request that is deferred until the #6 -> #1 rollover must not suppress poison
        // curing or full-inventory dropping during the active swamp cycle.
        boolean feroxDepartureImminent = feroxServiceRequired
                && (!inSwamp() || feroxDepartureArmed || emergencyFeroxRetreat);
        if (feroxDepartureImminent)
        {
            return false;
        }

        if (config.useAntipoison() && isPoisoned())
        {
            String potion = findAntiPoisonInInventory();
            if (potion != null)
            {
                state = DroSulliuscepState.CURING_POISON;
                status = "Curing poison";
                boolean drank = doAction(MAINTENANCE, true, () -> Rs2Inventory.interact(potion, "Drink"));
                if (drank)
                {
                    sleepUntil(() -> !isPoisoned(), 1_800);
                    nextTreeClickAt = System.currentTimeMillis()
                            + ThreadLocalRandom.current().nextLong(450L, 751L);
                }
                return true;
            }
            requestFeroxService("Antipoison enabled but none remains");
            return false;
        }

        return handleFullInventory();
    }

    static boolean enoughRoomForReserve(int usableSlots)
    {
        return usableSlots > 15;
    }

    static int usableMushroomSlots(List<Rs2ItemModel> items, DroSulliuscepDropMode mode)
    {
        int protectedSlots = 0;
        for (Rs2ItemModel item : items)
        {
            String name = item.getName();
            // Existing mushrooms and configured disposable drops are reclaimable padding slots.
            boolean usable = "Mushroom".equalsIgnoreCase(name)
                    || mode != null && ((mode.dropsMortMyre() && "Mort myre fungus".equalsIgnoreCase(name))
                    || mode.dropsFossil(name));
            if (!usable) protectedSlots++;
        }
        return Math.max(0, 28 - protectedSlots);
    }

    // Package visibility lets regression tests exercise the real recovery transition.
    boolean handleFullInventory()
    {
        if (inventorySlotCount() < 0)
        {
            status = "Waiting for live inventory";
            return true;
        }
        if (!inventoryIsFull())
        {
            failedFullInventoryDrops = 0;
            return false;
        }

        // Filling consumes nine mushrooms and can free space even when every other item
        // is protected. Do this before deciding that a full backpack requires banking.
        if (tryFullInventoryPitRefill()) return true;

        boolean attempted = dropConfiguredDisposables();
        if (attempted && !inventoryIsFull())
        {
            failedFullInventoryDrops = 0;
            treeClickIssued = false;
            nextTreeClickAt = 0L;
            return true;
        }
        if (attempted && ++failedFullInventoryDrops < 3)
        {
            status = "Inventory still full -> retrying Drop";
            return true;
        }

        requestBlockingFeroxService(attempted
                ? "Inventory remained full after 3 drop attempts"
                : "Inventory full - no configured disposable items");
        // Let the caller reach Ferox service this tick. Finishing the course is impossible
        // without a free slot; neither the cycle gate nor landing latch may defer recovery.
        return false;
    }

    boolean tryFullInventoryPitRefill()
    {
        return config.jumpRubberCapMushroom() && !rubberLoopReturnPending
                && detectActiveTree() == 0 && !mudPitFilled()
                && countLoopMushrooms() >= LOOP_MUSHROOM_TARGET && handleMudPitReload();
    }

    boolean inventoryIsFull()
    {
        int occupied = inventorySlotCount();
        return occupied < 0 || occupied >= 28;
    }

    int inventorySlotCount()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ItemContainer container = Rs2Inventory.inventory();
            return container == null ? -1 : occupiedInventorySlots(container.getItems());
        }).orElse(-1);
    }

    static int occupiedInventorySlots(Item[] items)
    {
        int occupied = 0;
        for (Item item : items)
        {
            if (item != null && item.getId() >= 0 && item.getQuantity() > 0) occupied++;
        }
        return occupied;
    }

    private List<Rs2ItemModel> currentInventoryItems()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ItemContainer container = Rs2Inventory.inventory();
            List<Rs2ItemModel> items = new ArrayList<>();
            if (container == null) return items;
            Item[] slots = container.getItems();
            for (int slot = 0; slot < slots.length; slot++)
            {
                Item item = slots[slot];
                if (item != null && item.getId() >= 0 && item.getQuantity() > 0)
                    items.add(new Rs2ItemModel(item,
                            Microbot.getClient().getItemDefinition(item.getId()), slot));
            }
            return items;
        }).orElse(Collections.emptyList());
    }

    boolean dropConfiguredDisposables()
    {
        DroSulliuscepDropMode mode = config.dropMode();

        // Reserve only at tree #6 / jump hand-off, then collect any shortfall at tree #1.
        // Elsewhere, a full inventory drops every configured disposable in one batch.
        if (mode != null && loopMushroomReserveActive()
                && inventoryIsFull() && fastDropLoopReserveDisposables(mode))
        {
            return true;
        }

        boolean hasDroppable = currentInventoryItems().stream().anyMatch(item -> shouldDrop(item, mode));
        if (!hasDroppable)
        {
            return false;
        }

        state = DroSulliuscepState.DROPPING;
        status = "Dropping " + mode;

        // Refresh the local drop snapshot from the live container, rather than repeatedly
        // selecting from an inventory event snapshot that may have missed an update.
        DroDrop.dropItems(currentInventoryItems().stream().filter(item -> shouldDrop(item, mode))
                .collect(Collectors.toList()), DroDrop.Pattern.CUSTOM, profile);
        sleepUntil(() -> !inventoryIsFull(), 2_000);
        nextTreeClickAt = System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(550L, 950L);

        return true;
    }

    boolean shouldDrop(Rs2ItemModel item, DroSulliuscepDropMode mode)
    {
        if (item == null || item.getName() == null || mode == null)
        {
            return false;
        }
        String name = item.getName();

        // Preserve even a partial reserve at tree #1 until the empty pit can be filled.
        // Once filled, ordinary batch dropping resumes immediately.
        boolean protectMushroomReserve = name.equalsIgnoreCase("Mushroom") && loopMushroomReserveActive();

        return (!protectMushroomReserve && mode.dropsMushrooms() && name.equalsIgnoreCase("Mushroom"))
                || (mode.dropsMortMyre() && name.equalsIgnoreCase("Mort myre fungus"))
                || mode.dropsFossil(name);
    }

    boolean loopMushroomReserveActive()
    {
        if (config == null || !config.jumpRubberCapMushroom()) return false;
        int active = detectActiveTree();
        return reserveLoopMushrooms(true, active, mudPitFilled(), rubberLoopReturnPending)
                && enoughRoomForReserve(usableMushroomSlots(currentInventoryItems(), config.dropMode()));
    }

    static boolean reserveLoopMushrooms(boolean enabled, int active, boolean pitFilled, boolean jumpPending)
    {
        return enabled && (jumpPending || active == 5 || active == 0 && !pitFilled);
    }

    private void handleSwampRun()
    {
        traceDroAction("handleSwampRun");
        // Recheck the live container immediately before course/tree work.
        if (inventoryIsFull())
        {
            handleFullInventory();
            return;
        }

        if (!hasRake())
        {
            requestBlockingFeroxService("Required rake missing");
            return;
        }
        if (!hasUsableAxe())
        {
            requestBlockingFeroxService("Usable axe missing");
            return;
        }

        if (config.jumpRubberCapMushroom() && rubberLoopReturnPending)
        {
            handleRubberCapReturn();
            return;
        }

        int active = detectActiveTree();

        // The active-tree varbit can roll 6 -> 1 between the top-of-tick progress sample and this
        // local course sample. Catch that transition HERE before any tree-#1 route/minimap logic
        // can run. This removes the stray minimap click immediately before the Rubber-cap jump.
        if (config.jumpRubberCapMushroom() && lastActiveTree == 5 && active == 0
                && !rubberLoopReturnPending)
        {
            trackActiveTreeProgress();
            if (rubberLoopReturnPending)
            {
                handleRubberCapReturn();
            }
            return;
        }

        if (active == 5 && loopMushroomReserveActive() && maintainLoopMushroomReserve())
        {
            return;
        }

        if (config.jumpRubberCapMushroom() && active == 0 && handleMudPitReload())
        {
            return;
        }
        if (active != detectedActiveTree)
        {
            detectedActiveTree = active;
            parkedAtFifthTree = false;
            nextCoursePlanAt = 0L;
            nextTreeClickAt = 0L;
            treeClickIssued = false;
            nextObstacleActionAt = 0L;
        }
        SulliuscepRouteData.TreeSpot tree = SulliuscepRouteData.tree(active);
        if (handleThirdTreeRecovery(active)) return;
        maybeTurnCameraToTree(active);

        WorldPoint current = currentPlayerLocation();
        if (treeClickIssued && !isChopping())
            nextTreeClickAt = treeApproachWatch.deadline(nextTreeClickAt, System.currentTimeMillis(), current);

        // After the one Cut click on tree #6, freeze course/minimap movement completely while
        // waiting for chopping/the active-tree rollover. Only the 8-10s tree watchdog may release
        // this latch for a true failed chop. This prevents the old tree-#1 minimap hand-off race.
        if (config.jumpRubberCapMushroom() && active == 5 && treeClickIssued)
        {
            long now = System.currentTimeMillis();
            if (isChopping() || now < nextTreeClickAt)
            {
                state = DroSulliuscepState.CHOPPING;
                status = "Tree #6 -> waiting for Rubber cap hand-off";
                return;
            }
            treeClickIssued = false;
            Microbot.log("[DroSulliuscep] tree #6 watchdog expired; permitting one recovery Cut");
        }

        if (active == 4 && !parkedAtFifthTree && profile != null
                && current != null && current.distanceTo(RecordedCourseNavigator.approach(active)) <= 2
                && isChopping())
        {
            parkedAtFifthTree = profile.forceParkCompletelyOffScreen(true);
        }

        // The live tree chooses the destination. The recorded movement graph chooses the
        // route from our CURRENT reachable position, including starts in the middle of a run.
        if (!thirdTreeRecovery && navigateRecordedCourse(active)) return;

        state = DroSulliuscepState.CHOPPING;
        status = "Cutting " + tree.displayName();

        // Once chopping starts, never touch the tree again. The watchdog only becomes
        // eligible after the original click has had 8-10 seconds to begin chopping.
        if (isChopping())
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (treeClickIssued)
        {
            if (now < nextTreeClickAt)
            {
                return;
            }

            // Watchdog expired and we are still not chopping: allow exactly one recovery click.
            treeClickIssued = false;
            Microbot.log("[DroSulliuscep] tree watchdog expired while not chopping -> " + tree.displayName());
        }

        TileObject object = findSulliuscepTree(tree);
        if (object == null)
        {
            // Tree is not interactable yet; keep moving to the recorded approach without
            // consuming the one tree click.
            clickRecordedTile(thirdTreeRecovery ? THIRD_TREE_SAFE_TILE : RecordedCourseNavigator.approach(tree.index()), "Approaching " + tree.displayName(),
                    DroSulliuscepState.TO_ACTIVE_TREE);
            return;
        }

        boolean clicked = courseObjectAction(object, SulliuscepRouteData.SULLIUSCEP_NAME, "Cut");
        if (clicked)
        {
            treeClickIssued = true;
            treeApproachWatch.issued(now, currentPlayerLocation());
            nextTreeClickAt = now + ThreadLocalRandom.current().nextLong(
                    TREE_WATCHDOG_MIN_MS, TREE_WATCHDOG_MAX_MS + 1L);
            if (tree.index() == 0 && rubberLandingResetPending)
            {
                rubberLandingResetPending = false;
                rubberCycleContinuationActive = true;
                mudPitFillClickIssued = false;
                mudPitFillWatchdogAt = 0L;
                feroxDepartureArmed = false;
                if (!emergencyFeroxRetreat)
                {
                    feroxServiceRequired = false;
                    feroxReason = "";
                }
                Microbot.log("[DroSulliuscep] Rubber landing reset complete; tree #1 resumed");
            }
            Microbot.log("[DroSulliuscep] single Cut -> " + tree.displayName());
            return;
        }

        // A failed interaction did not consume the tree click; reposition immediately.
        state = DroSulliuscepState.TO_ACTIVE_TREE;
        status = "Cut retry -> " + tree.displayName();
        clickRecordedTile(thirdTreeRecovery ? THIRD_TREE_SAFE_TILE : RecordedCourseNavigator.approach(tree.index()), "Repositioning for " + tree.displayName(),
                DroSulliuscepState.TO_ACTIVE_TREE);
    }

    static boolean canJumpRubberCap(boolean enabled, boolean foodRebankRequired, boolean pitFilled)
    {
        return enabled && !foodRebankRequired && pitFilled;
    }

    private boolean isChopping()
    {
        return isChoppingAnimation(Rs2Player.getAnimation());
    }

    // Hit reactions, eating and special attacks are animations too. Only an axe chop
    // proves that a Cut succeeded; damage must not keep a failed Cut latched forever.
    static boolean isChoppingAnimation(int animation)
    {
        switch (animation)
        {
            case AnimationID.WOODCUTTING_BRONZE:
            case AnimationID.WOODCUTTING_IRON:
            case AnimationID.WOODCUTTING_STEEL:
            case AnimationID.WOODCUTTING_BLACK:
            case AnimationID.WOODCUTTING_MITHRIL:
            case AnimationID.WOODCUTTING_ADAMANT:
            case AnimationID.WOODCUTTING_RUNE:
            case AnimationID.WOODCUTTING_GILDED:
            case AnimationID.WOODCUTTING_DRAGON:
            case AnimationID.WOODCUTTING_DRAGON_OR:
            case AnimationID.WOODCUTTING_INFERNAL:
            case AnimationID.WOODCUTTING_3A_AXE:
            case AnimationID.WOODCUTTING_CRYSTAL:
            case AnimationID.WOODCUTTING_TRAILBLAZER:
            case AnimationID.WOODCUTTING_2H_BRONZE:
            case AnimationID.WOODCUTTING_2H_IRON:
            case AnimationID.WOODCUTTING_2H_STEEL:
            case AnimationID.WOODCUTTING_2H_BLACK:
            case AnimationID.WOODCUTTING_2H_MITHRIL:
            case AnimationID.WOODCUTTING_2H_ADAMANT:
            case AnimationID.WOODCUTTING_2H_RUNE:
            case AnimationID.WOODCUTTING_2H_DRAGON:
            case AnimationID.WOODCUTTING_2H_CRYSTAL:
            case AnimationID.WOODCUTTING_2H_CRYSTAL_INACTIVE:
            case AnimationID.WOODCUTTING_2H_3A:
            case net.runelite.api.gameval.AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE:
            case net.runelite.api.gameval.AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE_NO_INFERNAL:
            case net.runelite.api.gameval.AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE:
                return true;
            default:
                return false;
        }
    }

    private int countLoopMushrooms()
    {
        return (int) currentInventoryItems().stream().filter(item -> item.getId() == BITTERCAP_MUSHROOM_ID).count();
    }

    /**
     * Keep up to nine mushrooms as a reserve only at the last tree, but do NOT trim
     * continuously. The inventory is allowed to fill back up. When full, drop every mushroom
     * above nine in one fast burst and resume the run immediately.
     */
    private boolean maintainLoopMushroomReserve()
    {
        if (!inventoryIsFull())
        {
            return false;
        }
        return fastDropLoopReserveDisposables(config.dropMode());
    }

    /**
     * Full-inventory cleanup at tree #6 or while collecting pit padding at tree #1. Keep the first nine mushrooms,
     * but fast-drop every other item selected by the configured drop mode in the same burst.
     * This is intentionally not DroDrop-paced: the run should immediately make room and resume.
     */
    private boolean fastDropLoopReserveDisposables(DroSulliuscepDropMode mode)
    {
        if (mode == null)
        {
            return false;
        }

        List<Rs2ItemModel> items = currentInventoryItems().stream()
                .filter(i -> i.getName() != null)
                .sorted(Comparator.comparingInt(Rs2ItemModel::getSlot))
                .collect(Collectors.toList());

        int mushroomsSeen = 0;
        List<Integer> slots = new ArrayList<>();
        for (Rs2ItemModel item : items)
        {
            String name = item.getName();
            if (item.getId() == BITTERCAP_MUSHROOM_ID || name.equalsIgnoreCase("Mushroom"))
            {
                mushroomsSeen++;
                if (mushroomsSeen > LOOP_MUSHROOM_TARGET && mode.dropsMushrooms())
                {
                    slots.add(item.getSlot());
                }
            }
            else if ((mode.dropsMortMyre() && name.equalsIgnoreCase("Mort myre fungus"))
                    || mode.dropsFossil(name))
            {
                slots.add(item.getSlot());
            }
        }

        if (slots.isEmpty())
        {
            return false;
        }

        state = DroSulliuscepState.DROPPING;
        status = "Fast drop reserve cleanup; keep 9 mushrooms";
        for (int slot : slots)
        {
            Rs2ItemModel selected = items.stream().filter(item -> item.getSlot() == slot).findFirst().orElse(null);
            if (selected != null) doAction(ACTIVE_WORK, true, () -> Rs2Inventory.interact(selected, "Drop"));
        }
        sleepUntil(() -> !inventoryIsFull(), 2_000);
        nextTreeClickAt = 0L;
        return true;
    }

    private boolean mudPitFilled()
    {
        return Microbot.getVarbitValue(SulliuscepRouteData.VARBIT_PIT_FILLED) > 0;
    }

    static boolean canInteractWithMudPit(WorldPoint player)
    {
        return player != null && player.getPlane() == 0;
    }

    private TileObject findMudPit()
    {
        return Rs2GameObject.getGameObject(
                o -> o.getId() == SulliuscepRouteData.PIT_OBJECT_ID, 30);
    }

    /**
     * When the loop option is enabled, load the landing pit before cutting tree #1.
     * A filled pit is left alone; filling consumes exactly nine bittercap mushrooms.
     */
    private boolean handleMudPitReload()
    {
        traceDroAction("handleMudPitReload");
        // The jump exposes pit objects while the player is still landing on plane 1.
        // Those Fill clicks are ignored and must not consume the three-attempt budget.
        if (!canInteractWithMudPit(currentPlayerLocation())) return false;
        if (mudPitFilled())
        {
            mudPitFillAttempts = 0;
            mudPitFillClickIssued = false;
            mudPitFillWatchdogAt = 0L;
            return false;
        }
        if (countLoopMushrooms() < LOOP_MUSHROOM_TARGET)
        {
            // Keep the partial reserve and continue cutting tree #1 until nine are available.
            return false;
        }

        TileObject pit = findMudPit();
        if (pit == null)
        {
            return false;
        }

        long now = System.currentTimeMillis();
        if (mudPitFillClickIssued)
        {
            // One Fill click per landing attempt. Let the interaction/varbit settle before a
            // watchdog retry; never hammer every visible mud-pit object each 40 ms loop.
            if (mudPitFilled())
            {
                mudPitFillAttempts = 0;
                mudPitFillClickIssued = false;
                mudPitFillWatchdogAt = 0L;
                return false;
            }
            if (Rs2Player.isMoving() || now < mudPitFillWatchdogAt)
            {
                state = DroSulliuscepState.TO_ACTIVE_TREE;
                status = "Mud pit fill in progress";
                return true;
            }
            mudPitFillClickIssued = false;
        }

        if (now < nextMudPitAttemptAt)
        {
            return true;
        }

        if (mudPitFillAttempts >= 3)
        {
            requestBlockingFeroxService("Mud pit did not fill after 3 attempts");
            return true;
        }
        state = DroSulliuscepState.TO_ACTIVE_TREE;
        status = "Filling mud pit with 9 mushrooms";
        boolean clicked = directObjectAction(pit, "Fill", ACTIVE_WORK, true);
        nextMudPitAttemptAt = now + (clicked ? 50L : 1_000L);
        mudPitFillAttempts++;
        if (clicked)
        {
            mudPitFillClickIssued = true;
            mudPitFillWatchdogAt = now + 3_000L;
        }
        return true;
    }

    private TileObject findRubberCapMushroom()
    {
        // Exact live object supplied from Object IDs overlay. Search the entire loaded scene rather
        // than a player-radius helper so #6 can hand directly to the Rubber cap without a minimap
        // recovery click. Resolve the cache model back to its real GameObject before interacting.
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var candidate = Microbot.getRs2TileObjectCache().query()
                    .where(o -> o.getId() == SulliuscepRouteData.RUBBER_CAP_MUSHROOM_OBJECT_ID)
                    .toListOnClientThread().stream()
                    .min(Comparator.comparingInt(o -> {
                        WorldPoint player = Rs2Player.getWorldLocation_Internal();
                        return player == null ? Integer.MAX_VALUE : o.getWorldLocation().distanceTo(player);
                    }))
                    .orElse(null);
            return candidate == null ? null : resolveCourseObject(candidate);
        }).orElse(null);
    }

    /**
     * Rubber-cap-only movement fallback. This deliberately clicks the GAME SCENE, never the minimap.
     * The supplied Object-ID overlay places 30606 at 3664,3800. Usually the object itself is already
     * loaded and this is unused; it only nudges toward that tile if the cache has not exposed it yet.
     */
    private boolean clickRubberCapSceneApproach()
    {
        WorldPoint player = currentPlayerLocation();
        WorldPoint target = SulliuscepRouteData.RUBBER_CAP_MUSHROOM;
        if (player == null || player.getPlane() != target.getPlane())
        {
            return false;
        }

        // Keep the scene click close enough to remain projectable. Never substitute a minimap click.
        int dx = target.getX() - player.getX();
        int dy = target.getY() - player.getY();
        int distance = Math.max(Math.abs(dx), Math.abs(dy));
        WorldPoint clickTarget = target;
        if (distance > 8)
        {
            double scale = 8.0 / distance;
            clickTarget = new WorldPoint(
                    player.getX() + (int) Math.round(dx * scale),
                    player.getY() + (int) Math.round(dy * scale),
                    player.getPlane());
        }

        final WorldPoint sceneTarget = clickTarget;
        Point canvas = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null || Microbot.getClient().getTopLevelWorldView() == null) return null;
            LocalPoint local = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), sceneTarget);
            if (local == null) return null;
            Point p = Perspective.localToCanvas(Microbot.getClient(), local, sceneTarget.getPlane());
            if (p == null) return null;
            int x = p.getX();
            int y = p.getY();
            int vx = Microbot.getClient().getViewportXOffset();
            int vy = Microbot.getClient().getViewportYOffset();
            int vw = Microbot.getClient().getViewportWidth();
            int vh = Microbot.getClient().getViewportHeight();
            return x >= vx && x < vx + vw && y >= vy && y < vy + vh ? p : null;
        }).orElse(null);

        if (canvas == null)
        {
            return false;
        }

        state = DroSulliuscepState.TO_ACTIVE_TREE;
        status = "Scene approach -> Rubber cap mushroom";
        return doAction(ACTIVE_WORK, true, () -> {
            Microbot.getMouse().click(canvas);
            return true;
        });
    }

    /** Finish #6 -> loaded-pit Rubber-cap jump -> tree #1 -> refill when mushrooms are available. */
    private void handleRubberCapReturn()
    {
        traceDroAction("handleRubberCapReturn");
        // With the mud pit loaded there is no warning dialogue: interaction should go straight
        // from object 30606 / "Jump on" to the landing sequence.

        // The mud pit is only visible at the landing/start area. Seeing it means the jump completed.
        if (findMudPit() != null)
        {
            rubberLoopReturnPending = false;
            rubberLandingResetPending = true;
            returnToSwampInProgress = false;
            feroxDepartureArmed = false;
            if (!emergencyFeroxRetreat)
            {
                feroxServiceRequired = false;
                feroxReason = "";
            }
            webWalk.cancelIfActive();
            rubberJumpClickIssued = false;
            rubberJumpWatchdogAt = 0L;
            nextMudPitAttemptAt = 0L;
            mudPitFillClickIssued = false;
            mudPitFillWatchdogAt = 0L;
            nextCoursePlanAt = 0L;
            nextLocalRecoveryClickAt = 0L;
            lastMovementTarget = null;
            status = "Rubber cap landed -> resume tree #1";
            handleMudPitReload();
            return;
        }

        if (Rs2Player.isMoving())
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (rubberJumpClickIssued && now < rubberJumpWatchdogAt)
        {
            return;
        }
        if (rubberJumpClickIssued)
        {
            rubberJumpClickIssued = false;
        }

        TileObject rubber = findRubberCapMushroom();
        if (rubber == null)
        {
            // Do NOT freeze and do NOT use the minimap. Keep advancing toward the exact Rubber-cap
            // tile through viewport/scene clicks until object 30606 is loaded, then Jump on it.
            if (!Rs2Player.isMoving())
            {
                clickRubberCapSceneApproach();
            }
            return;
        }

        state = DroSulliuscepState.TO_ACTIVE_TREE;
        status = "Jump on Rubber cap mushroom";
        clearCantReachRecovery();
        if (doAction(ACTIVE_WORK, true, () -> Rs2GameObject.interact(rubber, "Jump on", false)))
        {
            rubberJumpClickIssued = true;
            rubberJumpWatchdogAt = now + 8_000L;
        }
    }

    /**
     * Best-effort startup/post-run stock only. Rubber-cap mode NEVER requires mushrooms to start.
     * If none exist in inventory/bank, run the course normally and gather them from Sulliusceps;
     * once a later visit reaches tree #1 with nine, the mud pit is filled immediately.
     */
    private void ensureRubberLoopMushroomsAtBank()
    {
        // A loaded pit already contains the nine mushrooms needed for the next jump. Do not
        // withdraw another nine merely because we happened to rebank before that cycle.
        if (mudPitFilled())
        {
            return;
        }
        int current = countLoopMushrooms();
        int need = Math.max(0, LOOP_MUSHROOM_TARGET - current);
        if (need <= 0 || !Rs2Bank.hasBankItem("Mushroom", true))
        {
            return;
        }
        status = "Withdrawing 9 mushrooms for Rubber cap";
        final int amount = need;
        doAction(SETUP, false, () -> Rs2Bank.withdrawX("Mushroom", amount));
        // Do not stall startup waiting on the withdraw; the next 40 ms tick re-checks inventory.
    }

    private int detectActiveTree()
    {
        int indicated = activeTreeIndex();
        if (findSulliuscepTree(SulliuscepRouteData.tree(indicated)) != null) return indicated;
        int visible = -1;
        for (SulliuscepRouteData.TreeSpot spot : SulliuscepRouteData.TREES)
        {
            if (findSulliuscepTree(spot) == null) continue;
            // If multiple definitions expose Cut, keep the game's authoritative varbit.
            if (visible >= 0) return indicated;
            visible = spot.index();
        }
        return visible >= 0 ? visible : indicated;
    }

    private boolean navigateRecordedCourse(int activeTree)
    {
        WorldPoint player = currentPlayerLocation();
        if (player == null) return true;
        long now = System.currentTimeMillis();
        if (now < nextCoursePlanAt) return true;
        nextCoursePlanAt = now + 25L;

        Map<WorldPoint, Integer> reachable = Rs2Tile.getReachableTilesFromTile(player, 104, false);
        WorldPoint goal = RecordedCourseNavigator.approach(activeTree);
        if (reachable.containsKey(goal) && findSulliuscepTree(SulliuscepRouteData.tree(activeTree)) != null)
            return false;

        Set<Integer> blocked = new HashSet<>();
        for (int i = 0; i < SulliuscepRouteData.OBSTACLES.size(); i++)
            if (findRecordedObstacle(SulliuscepRouteData.OBSTACLES.get(i)) != null) blocked.add(i);
        if (obstacleActionLatched >= 0 && !blocked.contains(obstacleActionLatched))
        {
            // The interacted root/vine transformed or disappeared: release immediately so the
            // next route waypoint can be clicked on this same planning pass.
            obstacleActionLatched = -1;
            obstacleActionWatchdogAt = 0L;
            nextObstacleActionAt = 0L;
        }
        List<WorldPoint> path = RecordedCourseNavigator.plan(reachable, activeTree, blocked);
        if (path.isEmpty())
        {
            state = DroSulliuscepState.TO_ACTIVE_TREE;
            status = "Waiting for a reachable recorded path to tree #" + (activeTree + 1);
            return true;
        }

        WorldPoint waypoint = path.get(0);
        int walked = player.distanceTo(waypoint);
        for (int i = 1; i < path.size(); i++)
        {
            WorldPoint before = path.get(i - 1);
            int barrier = RecordedCourseNavigator.obstacleBetween(before, path.get(i));
            if (blocked.contains(barrier))
            {
                if (reachable.containsKey(before))
                {
                    SulliuscepRouteData.RouteObstacle obstacle = SulliuscepRouteData.OBSTACLES.get(barrier);
                    state = DroSulliuscepState.CLEARING_OBSTACLE;
                    status = obstacle.action() + " " + obstacle.displayName() + " -> tree #" + (activeTree + 1);
                    TileObject object = findRecordedObstacle(obstacle);
                    if (object == null)
                    {
                        obstacleActionLatched = -1;
                        obstacleActionWatchdogAt = 0L;
                        nextObstacleActionAt = 0L;
                        nextCoursePlanAt = 0L;
                        return true;
                    }

                    if (obstacleActionLatched == barrier)
                    {
                        // One-shot root/vine interaction latch. While the same obstacle still exists,
                        // never spam-click it. Only a genuine stalled interaction after the watchdog
                        // may receive one recovery click. Hit animations cannot extend this timeout.
                        if (Rs2Player.isMoving() || now < obstacleActionWatchdogAt)
                        {
                            return true;
                        }
                        obstacleActionLatched = -1;
                    }

                    if (now >= nextObstacleActionAt)
                    {
                        boolean clicked = courseObjectAction(object, obstacle.objectName(), obstacle.action());
                        nextObstacleActionAt = now + OBSTACLE_RETRY_MIN_MS;
                        if (clicked)
                        {
                            obstacleActionLatched = barrier;
                            obstacleActionWatchdogAt = now + 3_000L;
                        }
                    }
                    return true;
                }
                // Stop on the recorded near side. An existing barrier must get its action
                // before any movement click can cross it, in either travel direction.
                break;
            }
            walked += before.distanceTo(path.get(i));
            if (walked > 15 || !reachable.containsKey(path.get(i))) break;
            waypoint = path.get(i);
        }
        clickRecordedTile(waypoint, "Recorded maze -> tree #" + (activeTree + 1),
                DroSulliuscepState.TO_ACTIVE_TREE);
        return true;
    }

    private TileObject findSulliuscepTree(SulliuscepRouteData.TreeSpot tree)
    {
        if (tree == null)
        {
            return null;
        }

        return findCourseObject(SulliuscepRouteData.SULLIUSCEP_NAME, "Cut", tree.location(), 3);
    }

    private TileObject findRecordedObstacle(SulliuscepRouteData.RouteObstacle obstacle)
    {
        return obstacle == null ? null : findCourseObject(
                obstacle.objectName(), obstacle.action(), obstacle.location(), 0);
    }

    private TileObject findCourseObject(String name, String action, WorldPoint anchor, int radius)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null)
                return null;
            // Query current transformed definitions, not the legacy name-to-static-ID prefilter.
            // Action filtering excludes depleted trees and cleared vine transformations.
            var candidate = Microbot.getRs2TileObjectCache().query()
                    .where(o -> o.getWorldLocation().distanceTo(anchor) <= radius)
                    .where(o -> hasCourseAction(o, name, action))
                    .toListOnClientThread().stream()
                    .min(Comparator.comparingInt(o -> o.getWorldLocation().distanceTo(anchor)))
                    .orElse(null);
            if (candidate == null) return null;

            // Preserve the underlying GameObject subtype: the interaction helper needs its
            // footprint when building menu coordinates. Cache models implement only TileObject.
            TileObject object = resolveCourseObject(candidate);
            return object != null && hasCourseAction(object, name, action) ? object : null;
        }).orElse(null);
    }

    // Rs2GameObject.getTileObject only scans walls, ground objects and decorations in this
    // checkout. Trees and vines are GameObjects, so that lookup alone always loses them.
    // Use the candidate's local centre to avoid anchor conversion through the player cache.
    static TileObject resolveCourseObject(TileObject candidate)
    {
        if (candidate == null || candidate.getLocalLocation() == null) return null;
        TileObject object = Rs2GameObject.getGameObject(
                o -> o.getHash() == candidate.getHash(), candidate.getLocalLocation(), 128);
        return object != null ? object : Rs2GameObject.getTileObject(
                o -> o.getHash() == candidate.getHash(), candidate.getLocalLocation(), 128);
    }

    private WorldPoint currentPlayerLocation()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null
                    || Microbot.getClient().getTopLevelWorldView() == null) return null;
            // Keep the existing instance conversion, but guard and read within one client task.
            return Rs2Player.getWorldLocation_Internal();
        }).orElse(null);
    }

    private double currentHealthPercentage()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null)
            {
                return -1.0;
            }
            int maximum = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
            int current = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
            return maximum > 0 ? current * 100.0 / maximum : -1.0;
        }).orElse(-1.0);
    }

    // Called on the client thread so varbit-driven impostors are resolved consistently.
    private boolean hasCourseAction(TileObject object, String name, String action)
    {
        ObjectComposition definition = Microbot.getClient().getObjectDefinition(object.getId());
        if (definition != null && definition.getImpostorIds() != null)
        {
            definition = definition.getImpostor();
        }
        return definition != null && name.equalsIgnoreCase(definition.getName())
                && definition.getActions() != null
                && Arrays.stream(definition.getActions()).anyMatch(action::equalsIgnoreCase);
    }

    private boolean courseObjectAction(TileObject object, String name, String action)
    {
        boolean ready = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                if (Microbot.getClient().getLocalPlayer() == null) return false;
                LocalPoint player = Microbot.getClient().getLocalPlayer().getLocalLocation();
                LocalPoint target = object.getLocalLocation();
                return target != null && target.isInScene()
                        && object.getWorldView() == Microbot.getClient().getLocalPlayer().getWorldView()
                        && Math.abs(player.getSceneX() - target.getSceneX()) <= 50
                        && Math.abs(player.getSceneY() - target.getSceneY()) <= 50
                        && hasCourseAction(object, name, action);
        }).orElse(false);
        if (!ready) return false;
        // Keep the local DRO reaction distribution on course clicks as well.
        clearCantReachRecovery();
        return doAction(ACTIVE_WORK, false, () -> Rs2GameObject.interact(object, action, false));
    }

    private boolean directObjectAction(TileObject object, String action,
                                       BaseProfileDro.ActionPhase phase, boolean urgent)
    {
        if (object == null)
        {
            return false;
        }

        clearCantReachRecovery();
        return doAction(phase, urgent, () -> {
            // Microbot's generic object helper can enter its own walker recovery if a
            // previous interaction raised the global "can't reach" flag. This script owns
            // the local Sulliuscep route, so explicitly prevent that hidden hand-off.
            clearCantReachRecovery();
            return Rs2GameObject.interact(object, action);
        });
    }

    private void clearCantReachRecovery()
    {
        Microbot.cantReachTarget = false;
        Microbot.cantReachTargetRetries = 0;
    }

    private Point recordedWorldToMinimap(WorldPoint target)
    {
        if (target == null)
        {
            return null;
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null || Microbot.getClient().getTopLevelWorldView() == null)
            {
                return null;
            }
            LocalPoint local = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), target);
            if (local == null)
            {
                return null;
            }
            return Perspective.localToMinimap(Microbot.getClient(), local);
        }).orElse(null);
    }

    private boolean clickRecordedTile(WorldPoint target, String label, DroSulliuscepState movementState)
    {
        if (target == null)
        {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now < nextLocalRecoveryClickAt)
        {
            return false;
        }

        WorldPoint player = currentPlayerLocation();
        if (player == null || player.getPlane() != target.getPlane() || player.distanceTo(target) > 16)
        {
            return false;
        }

        if (target.equals(lastMovementTarget) && Rs2Player.isMoving()
                && now - lastMovementClickAt < 250L) return true;

        Point point = recordedWorldToMinimap(target);
        if (point == null)
        {
            return false;
        }

        state = movementState;
        status = label;
        // Keep a movement destination continuously queued during the dangerous course.
        boolean clicked = doAction(ACTIVE_WORK, false, () -> {
            Microbot.getMouse().click(point);
            return true;
        });
        if (clicked)
        {
            lastMovementTarget = target;
            lastMovementClickAt = System.currentTimeMillis();
        }

        nextLocalRecoveryClickAt = now + ThreadLocalRandom.current().nextLong(
                LOCAL_RECOVERY_RETRY_MIN_MS, LOCAL_RECOVERY_RETRY_MAX_MS + 1L);
        if (clicked)
        {
            Microbot.log("[DroSulliuscep] raw minimap recovery -> " + target + " | " + label);
        }
        return clicked;
    }

    boolean maybeUseAxeSpecial()
    {
        if (!config.useAxeSpecial()
                || (Rs2Equipment.isWearing("Dragon felling axe", false) && !isPoisoned())
                || !hasSpecialAxe())
        {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now < nextSpecialAttemptAt || Rs2Player.isMoving() || Rs2Player.isAnimating(0))
        {
            return false;
        }
        if (Rs2Combat.getSpecEnergy() < 1000)
        {
            return false;
        }

        state = DroSulliuscepState.USING_AXE_SPECIAL;
        status = "Using axe special";
        boolean used = doAction(MAINTENANCE, false, () -> Rs2Combat.setSpecState(true, 1000));
        nextSpecialAttemptAt = now + SPECIAL_RETRY_MS;
        if (used && !treeClickIssued)
        {
            nextTreeClickAt = 0L;
        }
        return used;
    }

    void requestBlockingFeroxService(String reason)
    {
        cancelTreeCameraWork();
        feroxDepartureArmed = true;
        rubberLoopReturnPending = false;
        rubberLandingResetPending = false;
        rubberCycleContinuationActive = false;
        Microbot.log("[DroSulliuscep] immediate bank recovery: " + reason);
        requestFeroxService(reason);
    }

    private void requestEmergencyFeroxRetreat(String reason)
    {
        cancelTreeCameraWork();
        emergencyFeroxRetreat = true;
        requestFeroxService(reason);
    }

    private void requestFeroxService(String reason)
    {
        boolean newlyRequested = !feroxServiceRequired;
        feroxServiceRequired = true;
        if (reason != null && !reason.isBlank())
        {
            feroxReason = reason;
        }
        if (newlyRequested)
        {
            bankTravel.reset();
            bankOpening.reset();
            webWalk.cancelIfActive();
            poolRestoredThisVisit = false;
            poolAttemptsThisVisit = 0;
            bankServicedThisVisit = false;
            bankDepositedThisVisit = false;
            nextServiceActionAt = 0L;
            nextLocalRecoveryClickAt = 0L;
        }
    }

    private void resetHouseTravel()
    {
        houseTravel.reset();
        houseEntry.reset();
        housePool.reset();
        mountedTravel.reset();
        houseRestored = false;
        fossilHouseArrivalObserved = false;
        housePoolUsed = false;
        housePoolBefore = null;
        housePoolAnimationSeen = false;
        houseEnterReadyAt = poolClickReadyAt = poolAdvanceReadyAt = -1;
        bankOpening.reset();
    }

    private boolean hasHouseTab()
    {
        return Rs2Inventory.items(item -> item.getId() == 8013 && !item.isNoted()).findAny().isPresent();
    }

    static boolean isCape(Rs2ItemModel item, String skill)
    {
        if (item == null || item.isNoted()) return false;
        // Construction's in-game name is abbreviated to "Construct. cape".
        if ("Construction".equals(skill))
            return item.getId() == ItemID.CONSTRUCT_CAPE || item.getId() == ItemID.CONSTRUCT_CAPET;
        if ("Crafting".equals(skill))
            return item.getId() == ItemID.CRAFTING_CAPE || item.getId() == ItemID.CRAFTING_CAPET;
        return false;
    }

    private boolean hasCape(String skill)
    {
        return Rs2Equipment.isWearing(item -> isCape(item, skill))
                || Rs2Inventory.items(item -> isCape(item, skill)).findAny().isPresent();
    }

    private boolean hasConstructionCape() { return hasCape("Construction"); }
    private boolean hasCraftingCape() { return hasCape("Crafting"); }

    private boolean withdrawCape(String skill)
    {
        traceDroAction("withdrawCape");
        if (hasCape(skill)) return true;
        int plain = "Construction".equals(skill) ? 9789 : 9780;
        int trimmed = plain + 1;
        int id = Rs2Bank.hasBankItem(trimmed, 1) ? trimmed : plain;
        if (!Rs2Bank.hasBankItem(id, 1)) return false;
        // Keep the worn cape in place; both capes support inventory teleports.
        return doAction(SETUP, false, () -> Rs2Bank.withdrawOne(id))
                && sleepUntil(() -> hasCape(skill), 2_800);
    }

    boolean ensureTravelItemsAtBank()
    {
        if (travelMode.crafting)
        {
            if (!withdrawCape("Crafting"))
            {
                closeBankBeforeFailure("Crafting cape required for Crafting Guild banking");
                return false;
            }
        }
        else if (!ensureDuelingRingAtBank())
        {
            closeBankBeforeFailure("Charged Ring of dueling required for the next bank return");
            return false;
        }

        if (travelMode.poh)
        {
            // Detect equipment/inventory first, then prefer an available bank cape over tablets.
            if (!hasConstructionCape()) withdrawCape("Construction");
            if (!hasConstructionCape() && !hasHouseTab())
            {
                if (!Rs2Bank.hasBankItem(8013, 1)
                        || !doAction(SETUP, false, () -> Rs2Bank.withdrawOne(8013))
                        || !sleepUntil(this::hasHouseTab, 2_800))
                {
                    closeBankBeforeFailure("POH return requires a Construction cape or house tablet");
                    return false;
                }
            }
        }
        else if (!ensureDigsitePendantAtBank())
        {
            closeBankBeforeFailure("Charged Digsite pendant required for Fossil Island return");
            return false;
        }
        return travelMode.travelItemsReady(hasDuelingRing(), hasCraftingCape(), hasDigsitePendant(),
                hasConstructionCape(), hasHouseTab());
    }

    boolean useCape(String skill, String action)
    {
        Rs2ItemModel cape = Rs2Inventory.items(item -> isCape(item, skill)).findFirst().orElse(null);
        if (cape != null) return doAction(SETUP, true, () -> Rs2Inventory.interact(cape, action));
        return doAction(SETUP, true, () -> Rs2Equipment.interact(item -> isCape(item, skill), action));
    }

    private void teleportToServiceBank()
    {
        traceDroAction("teleportToServiceBank");
        state = DroSulliuscepState.TO_BANK;
        status = "Returning: " + travelMode;
        webWalk.cancelIfActive();
        long now = System.currentTimeMillis();
        if (bankTravel.waiting(now)) return;
        if (bankTravel.exhausted(now))
        {
            fail("Selected bank landing not observed after 3 teleport attempts: " + travelMode);
            return;
        }
        if (travelMode.crafting ? !hasCraftingCape() : !hasDuelingRing())
        {
            fail("Missing bank teleport: " + (travelMode.crafting ? "Crafting cape" : "charged Ring of dueling"));
            return;
        }
        bankTravel.issued(now, 10_000L);
        if (travelMode.crafting)
        {
            useCape("Crafting", "Teleport");
        }
        else
        {
            String ring = findDuelingRingInInventory();
            if (ring != null)
                doAction(SETUP, true, () -> Rs2Inventory.interact(ring, travelMode.bankAction, true));
            else
                doAction(SETUP, true, () -> Rs2Equipment.interact("Ring of dueling", travelMode.bankAction));
        }
    }

    private void openSelectedBank()
    {
        traceDroAction("openSelectedBank");
        long now = System.currentTimeMillis();
        if (now < nextServiceActionAt) return;
        if (bankOpening.exhausted(now))
        {
            fail("Could not open selected bank after 3 attempts: " + travelMode);
            return;
        }
        if (bankOpening.waiting(now)) return;
        WorldPoint player = currentPlayerLocation();
        if (player == null) return;
        if (player.distanceTo(travelMode.bankTile) > 8)
        {
            checkTravelResult(webWalk.approach(travelMode.bankTile), "Approaching selected bank");
            return;
        }
        webWalk.cancelIfActive();
        bankOpening.issued(now, 6_000L);
        doAction(SETUP, false, Rs2Bank::openBank);
    }

    boolean insideHouse()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(PohPresence::isInHouse).orElse(false);
    }

    private void handleHouseReturn()
    {
        traceDroAction("handleHouseReturn");
        handleHouseReturn(System.currentTimeMillis());
    }

    void handleHouseReturn(long now)
    {
        if (!insideHouse())
        {
            if (houseRestored)
            {
                if (mountedTravel.waiting(now)) return;
                fail("Left POH without observing the Fossil Island landing");
                return;
            }
            state = DroSulliuscepState.TELEPORTING_HOUSE;
            status = "House teleport -> waiting for inside-house arrival";
            // A cape may respect the game's outside-house setting. Enter the actual home portal.
            var home = net.runelite.client.plugins.microbot.util.poh.data.HouseLocation.getHouseLocation();
            WorldPoint player = currentPlayerLocation();
            if (home != null && player != null && player.distanceTo(home.getPortalLocation()) <= 12)
            {
                if (houseEntry.waiting(now)) return;
                if (houseEntry.exhausted(now))
                {
                    fail("Own house entry not observed after 3 portal attempts");
                    return;
                }
                if (!houseEnterDelayElapsed(now)) return;
                houseEntry.issued(now, 10_000L);
                TileObject portal = Rs2GameObject.getGameObject(home.getPortalId());
                if (portal != null) directObjectAction(portal, "Home", SETUP, true);
                return;
            }
            if (houseTravel.waiting(now)) return;
            if (houseTravel.exhausted(now))
            {
                fail("House arrival not observed after 3 teleport attempts");
                return;
            }
            if (!hasConstructionCape() && !hasHouseTab())
            {
                requestBlockingFeroxService("House teleport missing before house arrival");
                return;
            }
            if (!houseEnterDelayElapsed(now)) return;
            houseTravel.issued(now, 12_000L);
            if (hasConstructionCape()) useCape("Construction", "Tele to POH");
            else doAction(SETUP, true, () -> Rs2Inventory.interact(8013, "Break"));
            return;
        }

        houseTravel.reset();
        houseEntry.reset();
        webWalk.cancelIfActive();
        if (!houseRestored)
        {
            state = DroSulliuscepState.RESTORING;
            status = "POH -> ornate pool restoration";
            TileObject pool = findHousePool();
            if (pool == null)
            {
                if (housePool.exhausted(now)) fail("POH requires an ornate rejuvenation pool; none found");
                else if (!housePool.waiting(now)) housePool.issued(now, 3_000L);
                return;
            }
            if (housePoolUsed && (poolAdvanceReadyAt >= 0 || housePoolRestorationObserved()))
            {
                if (poolAdvanceReadyAt < 0) poolAdvanceReadyAt = now + houseActionDelay();
                if (now < poolAdvanceReadyAt) return;
                houseRestored = true;
                mountedTravel.reset();
            }
            else
            {
                if (housePool.waiting(now)) return;
                if (housePool.exhausted(now))
                {
                    fail("Ornate pool did not restore stats after 3 attempts");
                    return;
                }
                if (poolClickReadyAt < 0) poolClickReadyAt = now + houseActionDelay();
                if (now < poolClickReadyAt) return;
                housePoolBefore = housePoolVitals();
                if (housePoolBefore == null) return;
                poolClickReadyAt = -1;
                housePoolAnimationSeen = false;
                housePool.issued(now, 8_000L);
                housePoolUsed = useHousePool(pool);
                return;
            }
        }

        handleMountedDigsite(now);
    }

    int houseActionDelay()
    {
        return ThreadLocalRandom.current().nextInt(50, 251);
    }

    boolean doubleClickHousePool()
    {
        return ThreadLocalRandom.current().nextInt(50) == 0;
    }

    private boolean houseEnterDelayElapsed(long now)
    {
        if (houseEnterReadyAt < 0) houseEnterReadyAt = now + houseActionDelay();
        if (now < houseEnterReadyAt) return false;
        houseEnterReadyAt = -1;
        return true;
    }

    int[] housePoolVitals()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var client = Microbot.getClient();
            var player = client.getLocalPlayer();
            return player == null ? null : new int[] {
                    client.getBoostedSkillLevel(Skill.HITPOINTS),
                    client.getBoostedSkillLevel(Skill.PRAYER), player.getAnimation(), client.getEnergy()};
        }).orElse(null);
    }

    private boolean housePoolRestorationObserved()
    {
        int[] current = housePoolVitals();
        if (current == null || housePoolBefore == null) return false;
        int drink = net.runelite.api.gameval.AnimationID.POH_POOL_DRINK;
        if (current[2] == drink) housePoolAnimationSeen = true;
        // A pre-existing full-stat condition is not proof that the pool click completed.
        return current[0] > housePoolBefore[0] || current[1] > housePoolBefore[1]
                // Normal run regeneration while approaching must not count as a pool restore.
                || (current[3] >= 10000 && current[3] > housePoolBefore[3])
                || (housePoolAnimationSeen && current[2] != drink && houseFullyRestored());
    }

    TileObject findHousePool()
    {
        return Rs2GameObject.getGameObject(o -> o.getId() == ObjectID.POH_POOL_REGENERATION
                || o.getId() == ObjectID.XMAS20_POH_POOL_REGENERATION, 60);
    }

    boolean useHousePool(TileObject pool)
    {
        if (pool == null) return false;
        return doAction(MAINTENANCE, true, () -> {
            clearCantReachRecovery();
            if (!Rs2GameObject.interact(pool, "Drink")) return false;
            if (doubleClickHousePool())
            {
                // Humanized double-click spacing on the script thread, not a game-state wait.
                try
                {
                    Thread.sleep(ThreadLocalRandom.current().nextInt(40, 91));
                }
                catch (InterruptedException interrupted)
                {
                    Thread.currentThread().interrupt();
                    return true;
                }
                clearCantReachRecovery();
                Rs2GameObject.interact(pool, "Drink");
            }
            return true;
        });
    }

    void handleMountedDigsite(long now)
    {
        state = DroSulliuscepState.TELEPORTING_FOSSIL_ISLAND;
        status = "Mounted Digsite -> waiting for Fossil Island arrival";
        if (now >= nextTravelMenuAt && mountedTravel.menuAllowed(now) && Rs2Widget.hasWidget("Fossil Island"))
        {
            if (doAction(SETUP, false, () -> Rs2Widget.clickWidget("Fossil Island"))) mountedTravel.menuIssued();
            nextTravelMenuAt = now + 1_000L;
            return;
        }
        if (mountedTravel.waiting(now)) return;
        if (mountedTravel.exhausted(now))
        {
            fail("Mounted Digsite pendant missing or Fossil Island arrival not observed after 3 attempts");
            return;
        }
        mountedTravel.issued(now, 12_000L);
        DecorativeObject pendant = Microbot.getClientThread()
                .runOnClientThreadOptional(MountedDigsite::getObject).orElse(null);
        if (pendant == null) return;
        String action = Microbot.getClientThread().runOnClientThreadOptional(() ->
                pendant.getId() == ObjectID.POH_AMULET_DIG_FOSSIL ? "Fossil Island" : "Teleport menu")
                .orElse("Teleport menu");
        directObjectAction(pendant, action, SETUP, false);
    }

    boolean houseFullyRestored()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var client = Microbot.getClient();
            return client.getBoostedSkillLevel(Skill.HITPOINTS) >= client.getRealSkillLevel(Skill.HITPOINTS)
                    && client.getBoostedSkillLevel(Skill.PRAYER) >= client.getRealSkillLevel(Skill.PRAYER)
                    && client.getEnergy() >= 10000 && client.getVarpValue(VarPlayerID.SA_ENERGY) >= 1000
                    && client.getVarpValue(VarPlayerID.POISON) <= 0
                    && Arrays.stream(new Skill[]{Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE,
                            Skill.RANGED, Skill.MAGIC, Skill.WOODCUTTING})
                    .allMatch(skill -> client.getBoostedSkillLevel(skill) >= client.getRealSkillLevel(skill));
        }).orElse(false);
    }

    boolean underNpcAttack()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player != null && Microbot.getRs2NpcCache().query()
                    .where(npc -> !npc.isDead() && npc.getInteracting() == player).firstOnClientThread() != null;
        }).orElse(false);
    }

    static boolean shouldRecoverThirdTree(int active, WorldPoint player, boolean attacked, boolean chopping)
    {
        return active == 2 && attacked && chopping && player != null && player.getPlane() == 0
                && player.distanceTo(THIRD_TREE_SAFE_TILE) <= 8;
    }

    // Called by the plugin's client-thread hitsplat event; poison is not evidence of melee contact.
    void onPlayerHitsplat(HitsplatApplied event)
    {
        if (!isRunning() || event.getActor() != Microbot.getClient().getLocalPlayer()
                || event.getHitsplat() == null || !isNpcCombatHit(event.getHitsplat().getHitsplatType())
                || activeTreeIndex() != 2) return;
        Player player = Microbot.getClient().getLocalPlayer();
        if (player != null && Microbot.getRs2NpcCache().query()
                .where(npc -> !npc.isDead() && npc.getInteracting() == player).firstOnClientThread() != null)
            thirdTreeAttackWatch.recordHit(System.currentTimeMillis());
    }

    static boolean isNpcCombatHit(int type)
    {
        return type == HitsplatID.BLOCK_ME || type == HitsplatID.BLOCK_OTHER
                || type == HitsplatID.DAMAGE_ME || type == HitsplatID.DAMAGE_OTHER
                || type == HitsplatID.DAMAGE_MAX_ME;
    }

    private boolean handleThirdTreeRecovery(int active)
    {
        traceDroAction("handleThirdTreeRecovery");
        WorldPoint player = currentPlayerLocation();
        long now = System.currentTimeMillis();
        boolean eligible = active == 2 && shouldRecoverThirdTree(active, player, underNpcAttack(),
                isChopping() || treeClickIssued && !Rs2Player.isMoving());
        if (!thirdTreeRecovery && thirdTreeAttackWatch.shouldRecover(eligible, now))
        {
            thirdTreeRecovery = true;
            treeClickIssued = false;
            nextTreeClickAt = 0L;
            thirdTreeMove.reset();
            cancelTreeCameraWork();
            Microbot.log("[DroSulliuscep] Combat hit after 8s observation at tree #3 -> recovery tile 3682,3775");
        }
        if (!thirdTreeRecovery || THIRD_TREE_SAFE_TILE.equals(player)) return false;
        if (thirdTreeMove.waiting(now)) return true;
        if (thirdTreeMove.exhausted(now))
        {
            requestBlockingFeroxService("Unable to reach tree #3 attack recovery tile");
            return true;
        }
        thirdTreeMove.issued(now, 4_000L);
        Point point = recordedWorldToMinimap(THIRD_TREE_SAFE_TILE);
        status = "Attacked at tree #3 -> moving to recovery tile";
        state = DroSulliuscepState.TO_ACTIVE_TREE;
        if (point != null) doAction(ACTIVE_WORK, true, () -> {
            Microbot.getMouse().click(point);
            return true;
        });
        return true;
    }

    private void handleFeroxService()
    {
        traceDroAction("handleFeroxService");
        if (!nearServiceBank())
        {
            teleportToServiceBank();
            return;
        }
        bankTravel.reset();

        // Pool is not a startup ritual. On normal cycling it is only used after at least
        // one completed six-tree run when HP is actually missing. Emergency low-HP returns
        // are also allowed to restore immediately.
        if (shouldUseFeroxPool())
        {
            handleFeroxPool();
            return;
        }

        poolRestoredThisVisit = true;
        handleFeroxBank();
    }

    private boolean shouldUseFeroxPool()
    {
        if (travelMode.poh || poolRestoredThisVisit)
        {
            return false;
        }

        double hp = currentHealthPercentage();
        if (hp < 0)
        {
            return true;
        }
        if (hp >= 99.0)
        {
            return false;
        }

        // Restore whenever HP is missing at Ferox, including startup.
        return true;
    }

    private void handleFeroxPool()
    {
        traceDroAction("handleFeroxPool");
        state = DroSulliuscepState.RESTORING;

        // Re-check every tick. If HP recovered by another means, do not keep chasing the pool.
        if (currentHealthPercentage() >= 99.0)
        {
            webWalk.cancelIfActive();
            poolRestoredThisVisit = true;
            poolAttemptsThisVisit = 0;
            nextServiceActionAt = 0L;
            return;
        }

        long now = System.currentTimeMillis();

        // Visible object ALWAYS wins over routing. Let the game's object interaction walk the
        // short final distance exactly like a normal click.
        TileObject pool = Rs2GameObject.getGameObject(
                o -> o.getId() == SulliuscepRouteData.POOL_OF_REFRESHMENT_OBJECT_ID, 30);
        if (pool == null)
        {
            pool = Rs2GameObject.getGameObject("Pool of Refreshment", false, 30);
        }

        if (pool != null)
        {
            webWalk.cancelIfActive();
            status = "Clicking visible Ferox pool";

            if (now < nextServiceActionAt)
            {
                return;
            }

            final TileObject poolObject = pool;
            boolean clicked = directObjectAction(poolObject, "Drink", MAINTENANCE, false);
            nextServiceActionAt = now + SERVICE_RETRY_MS;

            if (clicked)
            {
                sleepUntil(() -> currentHealthPercentage() >= 99.0, 5_500);

                if (currentHealthPercentage() >= 99.0)
                {
                    poolRestoredThisVisit = true;
                    poolAttemptsThisVisit = 0;
                    nextServiceActionAt = 0L;
                    bankNotBeforeAt = System.currentTimeMillis()
                            + ThreadLocalRandom.current().nextLong(
                                    POST_POOL_BANK_DELAY_MIN_MS, POST_POOL_BANK_DELAY_MAX_MS + 1L);
                }
                return;
            }

            poolAttemptsThisVisit++;
            status = "Ferox pool click retry " + poolAttemptsThisVisit;
            return;
        }

        // Pool is not in the scene yet. Now—and only now—ask WebWalk to approach
        // the recorded standable tile that brings it into view.
        state = DroSulliuscepState.TO_FEROX_POOL;
        status = "WebWalk -> Ferox pool";
        checkTravelResult(webWalk.approach(SulliuscepRouteData.FEROX_POOL_APPROACH), "Approaching Ferox pool");
    }

    private void handleStartupFeroxBank()
    {
        traceDroAction("handleStartupFeroxBank");
        state = DroSulliuscepState.BANKING;
        status = "Startup trip setup";

        // Once the startup bank work is complete and Escape has closed the interface, finalize
        // instead of falling back into another bank-open attempt on the next 220 ms tick.
        if (!Rs2Bank.isOpen() && bankServicedThisVisit)
        {
            finishStartupBanking();
            return;
        }

        if (!Rs2Bank.isOpen())
        {
            long now = System.currentTimeMillis();

            if (now < bankNotBeforeAt || Rs2Player.isAnimating(0))
            {
                status = "Waiting before banking";
                return;
            }

            if (travelMode.poh)
            {
                openSelectedBank();
                return;
            }

            // One bank-open action per attempt. Do not click the chest and then immediately call
            // openBank() again; that was the startup spam. A failed attempt gets one slow retry.
            if (startupBankOpenAttempted)
            {
                if (now - startupBankOpenAttemptAt < 6_000L)
                {
                    status = "Waiting for selected bank";
                    return;
                }
                startupBankOpenAttempted = false;
            }

            webWalk.cancelIfActive();
            state = DroSulliuscepState.TO_FEROX_BANK;
            status = "Opening selected bank once";
            startupBankOpenAttempted = true;
            startupBankOpenAttemptAt = now;
            doAction(SETUP, false, Rs2Bank::openBank);
            sleepUntil(Rs2Bank::isOpen, 4_500);
            return;
        }

        webWalk.cancelIfActive();
        bankOpening.reset();
        startupBankOpenAttempted = false;

        if (!bankServicedThisVisit)
        {
            // Startup cleanup is deliberately broader than normal post-run banking: clear every
            // inventory item except the rake, then rebuild only the required trip setup.
            if (!bankDepositedThisVisit)
            {
                if (!depositStartupInventoryExceptRake()) return;
                bankDepositedThisVisit = true;
            }

            if (!Rs2Bank.setWithdrawAs(false)) return;

            if (!ensureRakeAtBank())
            {
                fail("Rake required - none in inventory or bank");
                return;
            }
            if (!ensureAxeAtBank())
            {
                fail("No usable Woodcutting axe equipped/inventory/bank");
                return;
            }
            if (!ensureTravelItemsAtBank()) return;
            if (!ensureOptionalSuppliesAtBank())
            {
                return;
            }
            if (config.jumpRubberCapMushroom())
            {
                ensureRubberLoopMushroomsAtBank();
            }

            bankServicedThisVisit = true;
        }

        if (Rs2Bank.isOpen())
        {
            status = "Closing bank with Escape";
            doAction(SETUP, true, () -> {
                Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                return true;
            });
            sleepUntil(() -> !Rs2Bank.isOpen(), 2_000);
            return;
        }

        finishStartupBanking();
    }

    private void finishStartupBanking()
    {
        resetHouseTravel();
        mudPitFillAttempts = 0;
        failedFullInventoryDrops = 0;
        startupPrepared = true;
        startupBankingRequired = false;
        startupBankOpenAttempted = false;
        startupBankOpenAttemptAt = 0L;
        feroxServiceRequired = false;
        feroxReason = "";
        poolRestoredThisVisit = false;
        poolAttemptsThisVisit = 0;
        bankServicedThisVisit = false;
        bankDepositedThisVisit = false;
        nextTeleportAttemptAt = 0L;
        nextServiceActionAt = 0L;
        nextLocalRecoveryClickAt = 0L;
        bankNotBeforeAt = 0L;
        returnToSwampInProgress = true;
        status = "Ready -> Sticky Swamp";
    }

    private void handleFeroxBank()
    {
        traceDroAction("handleFeroxBank");
        state = DroSulliuscepState.BANKING;
        status = "Banking run loot";

        // Banking is already complete and the interface is closed: finish the Ferox service
        // before any bank-open code can run. This prevents the close -> reopen spam loop.
        if (!Rs2Bank.isOpen() && bankServicedThisVisit)
        {
            finishFeroxService();
            return;
        }

        if (!Rs2Bank.isOpen())
        {
            long now = System.currentTimeMillis();

            // Do not click the bank while the pool restore is still settling.
            if (now < bankNotBeforeAt || Rs2Player.isAnimating(0))
            {
                status = "Waiting before banking";
                return;
            }

            if (travelMode.poh)
            {
                openSelectedBank();
                return;
            }

            WorldPoint player = currentPlayerLocation();

            // First priority: click the actual chest if it is in the loaded scene. The supplied
            // recording used "Use Bank chest" on object 26711 at 3130,3632.
            TileObject bankChest = Rs2GameObject.getGameObject(
                    o -> o.getId() == SulliuscepRouteData.FEROX_BANK_CHEST_OBJECT_ID, 30);
            if (bankChest == null)
            {
                bankChest = Rs2GameObject.getGameObject("Bank chest", false, 30);
            }

            if (bankChest != null)
            {
                webWalk.cancelIfActive();
                state = DroSulliuscepState.TO_FEROX_BANK;
                status = "Using visible Ferox bank chest";

                if (now < nextServiceActionAt)
                {
                    return;
                }

                final TileObject chestObject = bankChest;
                boolean clicked = directObjectAction(chestObject, "Use", SETUP, false);
                nextServiceActionAt = now + SERVICE_RETRY_MS;

                if (clicked && sleepUntil(Rs2Bank::isOpen, 3_200))
                {
                    return;
                }

                // Some client/object variants expose the same chest through the generic bank
                // helper instead of the recorded "Use" action. If the visible click did not
                // open the interface, immediately let Rs2Bank target the nearby bank object/NPC.
                if (!Rs2Bank.isOpen())
                {
                    status = "Opening nearby Ferox bank";
                    doAction(SETUP, false, Rs2Bank::openBank);
                }
                return;
            }

            // If we already reached the recorded standable bank tile, don't keep asking the
            // walker to solve an object tile. Open the nearest bank directly.
            if (player != null && player.distanceTo(SulliuscepRouteData.FEROX_BANK_APPROACH) <= 8)
            {
                webWalk.cancelIfActive();
                state = DroSulliuscepState.TO_FEROX_BANK;
                status = "Opening nearby Ferox bank";

                if (now >= nextServiceActionAt)
                {
                    doAction(SETUP, false, Rs2Bank::openBank);
                    nextServiceActionAt = now + SERVICE_RETRY_MS;
                }
                return;
            }

            // Only use WebWalk to reach the standable approach tile. Never give it the
            // bank chest's unstandable object coordinate.
            state = DroSulliuscepState.TO_FEROX_BANK;
            status = "WebWalk -> Ferox bank";
            checkTravelResult(webWalk.approach(SulliuscepRouteData.FEROX_BANK_APPROACH), "Approaching Ferox bank");
            return;
        }

        webWalk.cancelIfActive();

        bankOpening.reset();
        if (!bankServicedThisVisit)
        {
            // Recording: bank only Sulliuscep-run loot; the rake remains in inventory.
            // Deposit one distinct item stack at a time and confirm it left the inventory.
            if (!bankDepositedThisVisit)
            {
                if (!depositRecordedRunLoot() || !depositSurplusDuelingRings()) return;
                bankDepositedThisVisit = true;
            }

            if (!Rs2Bank.setWithdrawAs(false)) return;

            if (!ensureRakeAtBank())
            {
                fail("Rake required - none in inventory or bank");
                return;
            }
            if (!ensureAxeAtBank())
            {
                fail("No usable Woodcutting axe equipped/inventory/bank");
                return;
            }
            if (!ensureTravelItemsAtBank()) return;
            if (!ensureOptionalSuppliesAtBank())
            {
                return;
            }
            if (config.jumpRubberCapMushroom())
            {
                ensureRubberLoopMushroomsAtBank();
            }

            bankServicedThisVisit = true;
        }

        if (Rs2Bank.isOpen())
        {
            status = "Closing bank";
            doAction(SETUP, true, Rs2Bank::closeBank);
            sleepUntil(() -> !Rs2Bank.isOpen(), 2_000);
            return;
        }
        finishFeroxService();
    }

    private void finishFeroxService()
    {
        pendantTravel.reset();
        mushtreeTravel.reset();
        resetHouseTravel();
        mudPitFillAttempts = 0;
        failedFullInventoryDrops = 0;
        startupPrepared = true;
        feroxServiceRequired = false;
        feroxDepartureArmed = false;
        emergencyFeroxRetreat = false;
        feroxReason = "";
        poolRestoredThisVisit = false;
        poolAttemptsThisVisit = 0;
        bankServicedThisVisit = false;
        bankDepositedThisVisit = false;
        nextTeleportAttemptAt = 0L;
        nextServiceActionAt = 0L;
        nextLocalRecoveryClickAt = 0L;
        bankNotBeforeAt = 0L;
        returnToSwampInProgress = true;
        status = "Ready -> Sticky Swamp";
    }

    private boolean depositStartupInventoryExceptRake()
    {
        traceDroAction("depositStartupInventoryExceptRake");
        Set<Integer> ids = Rs2Inventory.items(item -> item != null
                        && item.getName() != null
                        && !item.getName().equalsIgnoreCase("Rake"))
                .map(Rs2ItemModel::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (Integer id : ids)
        {
            if (id == null || id <= 0 || !Rs2Inventory.contains(id))
            {
                continue;
            }
            final int itemId = id;
            status = "Clearing startup inventory";
            if (!doBankDeposit(() -> Rs2Bank.depositAll(itemId))
                    || !sleepUntil(() -> !Rs2Inventory.contains(itemId), 2_800)) return false;
        }
        return true;
    }

    private boolean depositRecordedRunLoot()
    {
        traceDroAction("depositRecordedRunLoot");
        Set<Integer> ids = Rs2Inventory.items(this::isRunLoot)
                .map(Rs2ItemModel::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (Integer id : ids)
        {
            if (id == null || id <= 0 || !Rs2Inventory.contains(id))
            {
                continue;
            }
            final int itemId = id;
            status = "Depositing run loot";
            if (!doBankDeposit(() -> Rs2Bank.depositAll(itemId))
                    || !sleepUntil(() -> !Rs2Inventory.contains(itemId), 2_800)) return false;
        }
        return true;
    }

    private boolean isRunLoot(Rs2ItemModel item)
    {
        if (item == null || item.getName() == null)
        {
            return false;
        }
        String lower = item.getName().toLowerCase(Locale.ROOT);
        return lower.equals("mushroom")
                || lower.equals("mort myre fungus")
                || lower.equals("numulite")
                || lower.equals("sulliuscep cap")
                || (lower.contains("unidentified") && lower.contains("fossil"));
    }

    private boolean depositSurplusDuelingRings()
    {
        traceDroAction("depositSurplusDuelingRings");
        Rs2ItemModel keep = hasEquippedDuelingRing() ? null
                : Rs2Inventory.items(this::isChargedDuelingRing)
                        .max(Comparator.comparing(Rs2ItemModel::getName)).orElse(null);
        int keepId = keep == null ? -1 : keep.getId();
        Set<Integer> ids = Rs2Inventory.items(item -> item != null && item.getName() != null
                        && item.getName().toLowerCase(Locale.ROOT).contains("ring of dueling"))
                .map(Rs2ItemModel::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        for (int id : ids)
        {
            int retained = id == keepId ? 1 : 0;
            int excess = Rs2Inventory.count(id) - retained;
            if (excess <= 0) continue;
            status = "Banking surplus Ring of dueling";
            if (!doBankDeposit(() -> retained == 0
                    ? Rs2Bank.depositAll(id) : Rs2Bank.depositX(id, excess))
                    || !sleepUntil(() -> Rs2Inventory.count(id) <= retained, 2_800)) return false;
        }
        return true;
    }

    private boolean isChargedDuelingRing(Rs2ItemModel item)
    {
        return item != null && !item.isNoted() && item.getName() != null
                && item.getName().matches("(?i)Ring of dueling\\([1-8]\\)");
    }

    private boolean ensureRakeAtBank()
    {
        if (hasRake())
        {
            return true;
        }
        return Rs2Bank.hasBankItem("Rake", true)
                && doAction(SETUP, false, () -> Rs2Bank.withdrawOne("Rake", true))
                && sleepUntil(this::hasRake, 2_500);
    }

    private boolean ensureAxeAtBank()
    {
        if (hasUsableAxe())
        {
            return true;
        }

        int wc = Rs2Player.getRealSkillLevel(Skill.WOODCUTTING);
        AxeChoice[] order = {
                new AxeChoice("Crystal felling axe", 71),
                new AxeChoice("Crystal axe", 71),
                new AxeChoice("Dragon felling axe", 61),
                new AxeChoice("Infernal axe", 61),
                new AxeChoice("Dragon axe", 61),
                new AxeChoice("Rune felling axe", 41),
                new AxeChoice("Rune axe", 41),
                new AxeChoice("Adamant axe", 31),
                new AxeChoice("Mithril axe", 21),
                new AxeChoice("Black axe", 11),
                new AxeChoice("Steel axe", 6),
                new AxeChoice("Iron axe", 1),
                new AxeChoice("Bronze axe", 1)
        };

        for (AxeChoice choice : order)
        {
            if (wc < choice.level())
            {
                continue;
            }
            Rs2ItemModel bankItem = Rs2Bank.findBankItem(choice.name());
            if (bankItem != null && doAction(SETUP, false, () -> Rs2Bank.withdrawAndEquip(bankItem.getId())))
            {
                return sleepUntil(this::hasUsableAxe, 2_500);
            }
        }
        return false;
    }

    private boolean ensureDigsitePendantAtBank()
    {
        if (hasDigsitePendant())
        {
            equipInventoryItemIfNeeded("Digsite pendant");
            return true;
        }

        Rs2ItemModel pendant = Rs2Bank.findBankItem("Digsite pendant");
        if (pendant == null)
        {
            return false;
        }
        boolean equipped = doAction(SETUP, false, () -> Rs2Bank.withdrawAndEquip(pendant.getId()));
        return equipped && sleepUntil(this::hasDigsitePendant, 2_500);
    }

    private boolean ensureDuelingRingAtBank()
    {
        if (hasEquippedDuelingRing()) return true;

        Rs2ItemModel inventoryRing = Rs2Inventory.items(this::isChargedDuelingRing)
                .max(Comparator.comparing(Rs2ItemModel::getName)).orElse(null);
        if (inventoryRing != null)
        {
            String name = inventoryRing.getName();
            return doAction(SETUP, true, () -> Rs2Bank.wearItem(name, true))
                    && sleepUntil(this::hasEquippedDuelingRing, 2_500);
        }

        Rs2ItemModel bankRing = Rs2Bank.findBankItem("Ring of dueling(8)");
        if (bankRing == null) bankRing = Rs2Bank.findBankItem("Ring of dueling");
        if (bankRing == null || !isChargedDuelingRing(bankRing)) return false;
        int id = bankRing.getId();
        return doAction(SETUP, true, () -> Rs2Bank.withdrawAndEquip(id))
                && sleepUntil(this::hasEquippedDuelingRing, 3_500);
    }

    private void closeBankBeforeFailure(String message)
    {
        traceDroAction("closeBankBeforeFailure");
        if (Rs2Bank.isOpen())
        {
            status = "Closing bank after setup failure";
            doAction(SETUP, true, Rs2Bank::closeBank);
            if (Rs2Bank.isOpen()) return;
        }
        fail(message);
    }

    private boolean ensureOptionalSuppliesAtBank()
    {
        if (config.useAntipoison())
        {
            int current = countInventoryByFragment(config.antiPoisonName());
            int need = Math.max(0, config.antiPoisonAmount() - current);
            if (need > 0)
            {
                if (!Rs2Bank.hasBankItem(config.antiPoisonName(), false))
                {
                    fail("Antipoison enabled but bank is missing: " + config.antiPoisonName());
                    return false;
                }
                final int amount = need;
                if (!doAction(SETUP, false, () -> Rs2Bank.withdrawX(config.antiPoisonName(), amount))
                        || !sleepUntil(() -> countInventoryByFragment(config.antiPoisonName())
                                >= config.antiPoisonAmount(), 2_800))
                {
                    fail("Failed to withdraw antipoison");
                    return false;
                }
            }
        }

        if (config.useFood())
        {
            int current = countInventoryByFragment(config.foodName());
            int need = Math.max(0, config.foodAmount() - current);
            if (need > 0)
            {
                if (!Rs2Bank.hasBankItem(config.foodName(), false))
                {
                    fail("Food enabled but bank is missing: " + config.foodName());
                    return false;
                }
                final int amount = need;
                if (!doAction(SETUP, false, () -> Rs2Bank.withdrawX(config.foodName(), amount))
                        || !sleepUntil(() -> countInventoryByFragment(config.foodName())
                                >= config.foodAmount(), 2_800))
                {
                    fail("Failed to withdraw food");
                    return false;
                }
            }
        }
        return true;
    }

    private void equipInventoryItemIfNeeded(String partialName)
    {
        traceDroAction("equipInventoryItemIfNeeded");
        if (partialName == null || partialName.isBlank() || Rs2Equipment.isWearing(partialName, false))
        {
            return;
        }
        if (Rs2Inventory.contains(partialName))
        {
            // Jewellery uses Wear in the inventory. Failure is harmless because the same item's
            // direct inventory teleport remains supported by travel methods.
            doAction(SETUP, false, () -> Rs2Inventory.interact(partialName, "Wear"));
            sleep(120, 240);
        }
    }

    private boolean outboundTravelReady()
    {
        // Once outside Ferox, the ring is irrelevant to completing the Fossil Island -> swamp leg.
        return hasRake() && hasUsableAxe()
                && (travelMode.poh ? hasConstructionCape() || hasHouseTab() : hasDigsitePendant());
    }

    void travelToStickySwamp()
    {
        if (Rs2Bank.isOpen())
        {
            status = "Closing bank for Sticky Swamp";
            doAction(SETUP, false, Rs2Bank::closeBank);
            return;
        }

        if (inSwamp())
        {
            webWalk.cancelIfActive();
            return;
        }

        // Once the landing was observed, transient scene/plane changes during Mushtree
        // travel must never hand control back to the completed POH teleport stage.
        if (continueFossilHouseReturn()) return;

        if (!hasRake())
        {
            returnToSwampInProgress = false;
            requestFeroxService("Required rake missing");
            return;
        }
        if (!hasUsableAxe())
        {
            returnToSwampInProgress = false;
            requestFeroxService("Usable axe missing");
            return;
        }
        returnToSwampInProgress = true;

        if (travelMode.poh)
        {
            handleHouseReturn();
            return;
        }

        state = DroSulliuscepState.TELEPORTING_FOSSIL_ISLAND;
        status = "Digsite pendant -> waiting for Fossil Island landing";
        long now = System.currentTimeMillis();
        // The final charge may disappear before the landing is observed. Keep ownership
        // through that transition instead of treating the missing pendant as a supply failure.
        if (now >= nextTravelMenuAt && pendantTravel.menuAllowed(now)
                && Rs2Dialogue.hasDialogueOption("Fossil Island"))
        {
            if (doAction(SETUP, false, () -> Rs2Dialogue.clickOption("Fossil Island")))
                pendantTravel.menuIssued();
            nextTravelMenuAt = now + 1_000L;
            return;
        }
        if (pendantTravel.waiting(now)) return;
        if (pendantTravel.exhausted(now))
        {
            fail("Fossil Island landing not observed after 3 pendant attempts");
            return;
        }
        if (!hasDigsitePendant())
        {
            returnToSwampInProgress = false;
            requestBlockingFeroxService("Digsite pendant missing before Fossil Island arrival");
            return;
        }

        webWalk.cancelIfActive();
        pendantTravel.issued(now, 10_000L);
        Rs2ItemModel pendant = currentInventoryItems().stream()
                .filter(item -> item.getName() != null && item.getName().toLowerCase(Locale.ROOT)
                        .startsWith("digsite pendant(")).findFirst().orElse(null);
        if (pendant != null)
            doAction(SETUP, false, () -> Rs2Inventory.interact(pendant, "Rub"));
        else
            doAction(SETUP, false, () -> Rs2Equipment.interact("Digsite pendant", "Fossil Island"));
    }

    boolean continueFossilHouseReturn()
    {
        if (atFossilHouse())
        {
            fossilHouseArrivalObserved = true;
            pendantTravel.reset();
            mountedTravel.reset();
        }
        if (!fossilHouseArrivalObserved) return false;
        returnToSwampInProgress = true;
        handleHouseMushtree();
        return true;
    }

    void handleHouseMushtree()
    {
        state = DroSulliuscepState.USING_MUSHTREE;
        status = "House Mushtree -> waiting for Sticky Swamp landing";
        long now = System.currentTimeMillis();
        if (now >= nextTravelMenuAt && mushtreeTravel.menuAllowed(now) && Rs2Widget.hasWidget("Mycelium"))
        {
            if (doAction(SETUP, false, () -> Rs2Widget.clickWidget("Sticky Swamp")))
                mushtreeTravel.menuIssued();
            nextTravelMenuAt = now + 1_000L;
            return;
        }
        if (mushtreeTravel.waiting(now)) return;
        if (mushtreeTravel.exhausted(now))
        {
            fail("Sticky Swamp landing not observed after 3 Mushtree attempts");
            return;
        }
        TileObject tree = Rs2GameObject.getGameObject(
                object -> object.getId() == SulliuscepRouteData.MAGIC_MUSHTREE_HOUSE_OBJECT_ID, 30);
        if (tree == null)
        {
            checkTravelResult(webWalk.approach(SulliuscepRouteData.HOUSE_MUSHTREE_APPROACH),
                    "Approaching House Mushtree");
            return;
        }
        webWalk.cancelIfActive();
        mushtreeTravel.issued(now, 10_000L);
        if (Rs2Widget.hasWidget("Mycelium"))
        {
            if (doAction(SETUP, false, () -> Rs2Widget.clickWidget("Sticky Swamp")))
                mushtreeTravel.menuIssued();
        }
        else
            directObjectAction(tree, "Use", SETUP, false);
    }

    private void checkTravelResult(WalkerState result, String label)
    {
        status = label;
        if (!label.equals(approachObjectLabel) || result == WalkerState.MOVING)
        {
            approachObjectWait.reset();
            approachObjectLabel = label;
        }
        if (result == WalkerState.ARRIVED)
        {
            long now = System.currentTimeMillis();
            if (approachObjectWait.exhausted(now))
                fail(label + ": destination object did not load");
            else if (!approachObjectWait.waiting(now))
                approachObjectWait.issued(now, 3_000L);
        }
        if (result == WalkerState.UNREACHABLE || result == WalkerState.EXIT)
            fail(label + " failed after bounded WebWalk retries");
    }

    private boolean hasRake()
    {
        return Rs2Inventory.contains("Rake");
    }

    private boolean hasDigsitePendant()
    {
        return Rs2Equipment.isWearing("Digsite pendant", false)
                || Rs2Inventory.contains("Digsite pendant");
    }

    private boolean hasInventoryDuelingRing()
    {
        return findDuelingRingInInventory() != null;
    }

    private String findDuelingRingInInventory()
    {
        Rs2ItemModel ring = Rs2Inventory.items(this::isChargedDuelingRing)
                .findFirst()
                .orElse(null);
        return ring == null ? null : ring.getName();
    }

    private boolean hasEquippedDuelingRing()
    {
        return Rs2Equipment.isWearing(this::isChargedDuelingRing);
    }

    private boolean hasDuelingRing()
    {
        return hasEquippedDuelingRing() || hasInventoryDuelingRing();
    }

    private boolean hasUsableAxe()
    {
        int wc = Rs2Player.getRealSkillLevel(Skill.WOODCUTTING);
        return Rs2Equipment.all(item -> usableAxe(item, wc)).findAny().isPresent()
                || Rs2Inventory.items(item -> usableAxe(item, wc)).findAny().isPresent();
    }

    private boolean usableAxe(Rs2ItemModel item, int wc)
    {
        if (item == null || item.getName() == null)
        {
            return false;
        }
        String n = item.getName().toLowerCase(Locale.ROOT);
        if (!n.contains("axe")) return false;
        if (n.contains("crystal")) return wc >= 71;
        if (n.contains("dragon") || n.contains("infernal")) return wc >= 61;
        if (n.contains("rune")) return wc >= 41;
        if (n.contains("adamant")) return wc >= 31;
        if (n.contains("mithril")) return wc >= 21;
        if (n.contains("black")) return wc >= 11;
        if (n.contains("steel")) return wc >= 6;
        return n.contains("iron") || n.contains("bronze");
    }

    private boolean hasSpecialAxe()
    {
        return Rs2Equipment.all(item -> {
            if (item == null || item.getName() == null) return false;
            String n = item.getName().toLowerCase(Locale.ROOT);
            return n.contains("dragon axe") || n.contains("infernal axe") || n.contains("crystal axe")
                    || n.contains("dragon felling axe") || n.contains("crystal felling axe");
        }).findAny().isPresent();
    }

    private boolean hasFoodOrSaradominBrew()
    {
        return !Rs2Inventory.getInventoryFood().isEmpty() || findSaradominBrewInInventory() != null;
    }

    private boolean consumeFoodOrSaradominBrew()
    {
        if (!Rs2Inventory.getInventoryFood().isEmpty())
        {
            return Rs2Player.eatAt(config.eatAt(), false);
        }

        String brew = findSaradominBrewInInventory();
        return brew != null && Rs2Inventory.interact(brew, "Drink");
    }

    private String findSaradominBrewInInventory()
    {
        Rs2ItemModel brew = Rs2Inventory.items(item -> {
                    if (item == null || item.getName() == null) return false;
                    String name = item.getName().toLowerCase(Locale.ROOT);
                    return name.equals("saradomin brew(1)")
                            || name.equals("saradomin brew(2)")
                            || name.equals("saradomin brew(3)")
                            || name.equals("saradomin brew(4)");
                })
                .findFirst()
                .orElse(null);
        return brew == null ? null : brew.getName();
    }

    private String findAntiPoisonInInventory()
    {
        String configuredName = config.antiPoisonName();
        if (configuredName != null && !configuredName.isBlank())
        {
            String fragment = configuredName.toLowerCase(Locale.ROOT);
            Rs2ItemModel configured = Rs2Inventory.items(item -> item != null
                            && item.getName() != null
                            && item.getName().toLowerCase(Locale.ROOT).contains(fragment))
                    .findFirst()
                    .orElse(null);
            if (configured != null)
            {
                return configured.getName();
            }
        }

        // Explicit fallback for normal Antipoison dose items such as Antipoison(4).
        Rs2ItemModel antipoison = Rs2Inventory.items(item -> item != null
                        && item.getName() != null
                        && item.getName().toLowerCase(Locale.ROOT).startsWith("antipoison("))
                .findFirst()
                .orElse(null);
        if (antipoison != null)
        {
            return antipoison.getName();
        }

        for (String name : Rs2Potion.getAntiPoisonVariants())
        {
            Rs2ItemModel potion = Rs2Inventory.get(name, false);
            if (potion != null)
            {
                return potion.getName();
            }
        }
        return null;
    }

    boolean isPoisoned()
    {
        try
        {
            // RuneLite's poison plugin reads VarPlayerID.POISON directly from the client;
            // > 0 is poisoned (venom is also positive, at a much larger value).
            int poison = Microbot.getClientThread().runOnClientThreadOptional(
                    () -> Microbot.getClient().getVarpValue(VarPlayerID.POISON)).orElse(0);
            return poison > 0;
        }
        catch (Exception ex)
        {
            // Keep the existing Microbot player-state cache as a harmless fallback.
            try
            {
                return Microbot.getVarbitPlayerValue(VarPlayerID.POISON) > 0;
            }
            catch (Exception ignored)
            {
                return false;
            }
        }
    }

    private int activeTreeIndex()
    {
        int value = Microbot.getVarbitValue(SulliuscepRouteData.VARBIT_ACTIVE_SULLIUSCEP);
        return Math.max(0, Math.min(5, value));
    }

    private boolean atRubberCapLanding()
    {
        WorldPoint p = currentPlayerLocation();
        return p != null && p.getPlane() == 1
                && p.distanceTo(new WorldPoint(3664, 3810, 1)) <= 14;
    }

    private boolean inSwamp()
    {
        WorldPoint p = currentPlayerLocation();
        return p != null && p.getPlane() == 0
                && p.getX() >= SWAMP_X_MIN && p.getX() <= SWAMP_X_MAX
                && p.getY() >= SWAMP_Y_MIN && p.getY() <= SWAMP_Y_MAX;
    }

    boolean atFossilHouse()
    {
        WorldPoint p = currentPlayerLocation();
        return p != null && p.getPlane() == 1
                && p.distanceTo(SulliuscepRouteData.FOSSIL_ISLAND_LANDING) <= 18;
    }

    private boolean nearServiceBank()
    {
        return travelMode.bankNearby(currentPlayerLocation());
    }

    private int countInventoryByFragment(String fragment)
    {
        if (fragment == null || fragment.isBlank()) return 0;
        String f = fragment.toLowerCase(Locale.ROOT);
        return Rs2Inventory.items(item -> item != null && item.getName() != null
                        && item.getName().toLowerCase(Locale.ROOT).contains(f))
                .mapToInt(item -> Math.max(1, item.getQuantity()))
                .sum();
    }

    /**
     * Single gateway for every script-owned game action. Keep task code from
     * bypassing BaseProfileDro so mouse/reaction/humanisation behaviour is
     * consistently applied to cutting, local movement, banking, potions,
     * jewellery, special attacks, and other direct interactions.
     */
    private boolean doAction(BaseProfileDro.ActionPhase phase, boolean urgent, BooleanSupplier action)
    {
        if (action == null)
        {
            return false;
        }
        return profile == null
                ? action.getAsBoolean()
                : (phase == ACTIVE_WORK
                    ? profile.performAction(phase, urgent, action, 0.0)
                    : profile.performAction(phase, urgent, action));
    }

    private boolean doBankDeposit(BooleanSupplier action)
    {
        return doAction(SETUP, false, action);
    }

    private int readXp()
    {
        try
        {
            return Microbot.getClientThread().runOnClientThreadOptional(
                    () -> Microbot.getClient().getSkillExperience(Skill.WOODCUTTING)).orElse(0);
        }
        catch (Exception ex)
        {
            return 0;
        }
    }

    private void fail(String message)
    {
        webWalk.cancelIfActive();
        stopTreeCamera();
        fatalError = true;
        state = DroSulliuscepState.ERROR;
        status = message;
        Microbot.log("[DroSulliuscep] " + message);
    }

    private static String safeMessage(Exception ex)
    {
        return ex == null || ex.getMessage() == null ? "unknown" : ex.getMessage();
    }

    public DroSulliuscepState getState() { return state; }
    public String getStatus() { return status; }
    public int getTreesCompleted() { return treesCompleted; }
    public int getCyclesCompleted() { return cyclesCompleted; }
    public long getRuntimeMs() { return runStartMs <= 0 ? 0 : Math.max(0, System.currentTimeMillis() - runStartMs); }
    public String getActiveTreeDisplay() { return SulliuscepRouteData.tree(detectedActiveTree >= 0 ? detectedActiveTree : activeTreeIndex()).displayName(); }
    public String getDropModeDisplay() { return config == null || config.dropMode() == null ? "-" : config.dropMode().toString(); }
    public int getXpGained() { return Math.max(0, readXp() - startWoodcuttingXp); }
    public String getNextBreak() { return profile == null ? "-" : profile.getBreakDisplay(); }

    public int getXpPerHour()
    {
        long runtime = getRuntimeMs();
        if (runtime < 1_000) return 0;
        int gained = Math.max(0, readXp() - startWoodcuttingXp);
        return (int) Math.round(gained * 3_600_000.0 / runtime);
    }

    private boolean treeCameraAllowed(int active)
    {
        return cameraEnabled && !Thread.currentThread().isInterrupted()
                && Microbot.isLoggedIn() && !Microbot.pauseAllScripts.get() && !InputArbiter.isHuman()
                && !feroxDepartureArmed && !emergencyFeroxRetreat && !rubberLoopReturnPending
                && !rubberLandingResetPending && !Rs2Player.isMoving() && !inventoryIsFull()
                && activeTreeIndex() == active;
    }

    private LocalPoint offscreenTreeLocation(int active)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            TileObject tree = findSulliuscepTree(SulliuscepRouteData.tree(active));
            if (tree == null || tree.getWorldView() != Microbot.getClient().getTopLevelWorldView()) return null;
            java.awt.Rectangle viewport = new java.awt.Rectangle(
                    Microbot.getClient().getViewportXOffset(), Microbot.getClient().getViewportYOffset(),
                    Microbot.getClient().getViewportWidth(), Microbot.getClient().getViewportHeight());
            java.awt.Shape hull = tree instanceof GameObject ? ((GameObject) tree).getConvexHull() : null;
            return treeCompletelyOutside(hull, tree.getCanvasTilePoly(), viewport)
                    ? tree.getLocalLocation() : null;
        }).orElse(null);
    }

    static boolean treeCompletelyOutside(java.awt.Shape hull, java.awt.Shape tile, java.awt.Rectangle viewport)
    {
        if (viewport == null || viewport.isEmpty()) return false;
        return (hull == null || !hull.intersects(viewport))
                && (tile == null || !tile.intersects(viewport));
    }

    private void maybeTurnCameraToTree(int active)
    {
        long now = System.currentTimeMillis();
        if (now < nextTreeCameraAt || treeCameraTurn != null && !treeCameraTurn.isDone()
                || !treeCameraAllowed(active) || offscreenTreeLocation(active) == null) return;
        nextTreeCameraAt = now + ThreadLocalRandom.current().nextLong(6_000L, 18_001L);
        long generation = cameraGeneration;
        treeCameraTurn = scheduledExecutorService.schedule(() -> {
            if (generation != cameraGeneration || !treeCameraAllowed(active)) return;
            LocalPoint target = offscreenTreeLocation(active);
            // Recheck after the randomized reaction: a now-visible tree needs no turn.
            if (target != null) Rs2Camera.turnTo(target, ThreadLocalRandom.current().nextInt(25, 71));
        }, ThreadLocalRandom.current().nextLong(120L, 451L), TimeUnit.MILLISECONDS);
    }

    private void cancelTreeCameraWork()
    {
        cameraGeneration++;
        if (treeCameraTurn != null) treeCameraTurn.cancel(true);
        treeCameraTurn = null;
    }

    private void stopTreeCamera()
    {
        traceDroAction("stopTreeCamera");
        cameraEnabled = false;
        cancelTreeCameraWork();
    }

    private void shutdownProfileOnly()
    {
        if (profile != null)
        {
            profile.shutdown();
            profile = null;
        }
    }

    @Override
    public void shutdown()
    {
        thirdTreeAttackWatch.reset();
        stopTreeCamera();
        webWalk.cancelIfActive();
        shutdownProfileOnly();
        super.shutdown();
        state = DroSulliuscepState.PAUSED;
        status = "Stopped";
    }

    private static final class AxeChoice
    {
        private final String name;
        private final int level;

        private AxeChoice(String name, int level)
        {
            this.name = name;
            this.level = level;
        }

        private String name() { return name; }
        private int level() { return level; }
    }
}
