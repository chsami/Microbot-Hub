package net.runelite.client.plugins.microbot.blackjack;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakPreparation;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.awt.Rectangle;
import java.awt.Shape;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BlackjackScript extends Script
{
    private final BlackjackCameraTurn cameraTurn = new BlackjackCameraTurn();
    private enum BreakPhase { NONE, FINISHING_CYCLE, EXITING, CLIMBING, UPSTAIRS, DESCENDING, QUIET, RELEASED }
    private BreakPreparation.Handle breakPreparation;
    private BreakPhase breakPhase = BreakPhase.NONE;
    private long breakRequestedAt;
    private long breakPhaseAt;
    private long breakQuietSince;
    private BlackjackState breakResumeState;

    private static final WorldArea BANDIT_HOUSE = new WorldArea(3357, 2991, 4, 5, 0);
    private static final WorldPoint BANDIT_HOUSE_CENTRE = new WorldPoint(3358, 2993, 0);
    private static final WorldPoint SOUTH_THUG_TENT_CENTRE = new WorldPoint(3349, 2954, 0);
    private static final WorldArea SOUTH_THUG_LURE_SEARCH_AREA = new WorldArea(3346, 2942, 18, 25, 0);
    private static final WorldPoint SOUTH_THUG_LURE_SEARCH_TILE = new WorldPoint(3350, 2959, 0);
    private static final WorldPoint SOUTH_THUG_LURE_OUTSIDE_LEAD_TILE = new WorldPoint(3352, 2960, 0);
    private static final WorldPoint SOUTH_THUG_COMBAT_STAGING_TILE = new WorldPoint(3351, 2956, 0);
    private static final WorldPoint SOUTH_THUG_COMBAT_SAFE_TILE = new WorldPoint(3351, 2953, 0);
    private static final WorldPoint COMBAT_STAGING_TILE = new WorldPoint(3359, 2995, 0);
    private static final WorldPoint COMBAT_SAFE_TILE = new WorldPoint(3360, 2993, 0);
    private static final WorldPoint BANDIT_WINE_DOOR_INSIDE_TILE = COMBAT_SAFE_TILE;
    private static final WorldPoint BANDIT_WINE_DOOR_OUTSIDE_TILE = new WorldPoint(3362, 2993, 0);
    private static final WorldPoint SOUTH_THUG_WINE_DOOR_INSIDE_TILE = new WorldPoint(3350, 2956, 0);
    private static final WorldPoint SOUTH_THUG_WINE_DOOR_OUTSIDE_TILE = new WorldPoint(3350, 2958, 0);
    private static final WorldPoint SOUTH_THUG_WINE_CURTAIN_TILE = new WorldPoint(3350, 2957, 0);
    private static final WorldPoint SOUTH_THUG_INNER_CURTAIN_TILE = new WorldPoint(3350, 2952, 0);
    private static final int CLOSED_CURTAIN_ID = 1533;
    private static final int OPEN_CURTAIN_ID = 1534;
    private static final WorldPoint WINE_MERCHANT_TILE = new WorldPoint(3359, 2990, 0);

    private static final int WINE_ID = 1993;
    private static final int NOTED_WINE_ID = 1994;
    private static final int COINS_ID = 995;
    private static final int WINE_HEAL_AMOUNT = 11;
    private static final int WINE_EXCHANGE_COST = 5;
    private static final int EARLY_WINE_RESTOCK_HITPOINTS = 40;
    private static final int EARLY_WINE_RESTOCK_MAX_WINES = 1;

    private static final String PICKPOCKET_ACTION = "Pickpocket";
    private static final String KNOCKOUT_ACTION = "Knock-Out";
    private static final String LURE_ACTION = "Lure";

    private static final long FAILED_KNOCKOUT_RETRY_MS = 450;
    private static final long PICKPOCKET_BURST_TIMEOUT_MS = 2_800;
    private static final long KNOCKOUT_CONFIRM_TIMEOUT_MS = 1_500;
    private static final long KNOCKOUT_AIM_REACQUIRE_MS = 1_500;
    private static final long KNOCKOUT_AIM_CAMERA_RESET_MS = 3_500;
    private static final long KNOCKOUT_AIM_OBSTRUCTION_RECOVERY_MS = 6_000;
    private static final int TARGET_CLICK_VIEWPORT_MARGIN = 18;
    private static final int PRONE_ANCHOR_CORRECTION_RADIUS = 18;
    private static final int KNOCKOUT_DISPATCH_FALLBACK_MIN_MS = 35;
    private static final int KNOCKOUT_DISPATCH_FALLBACK_MAX_MS = 106;
    private static final long SECOND_PICKPOCKET_INTERACTION_TIMEOUT_MS = 2_400;
    private static final int FIRST_PICKPOCKET_DELAY_MIN_MS = 30;
    private static final int FIRST_PICKPOCKET_DELAY_MAX_MS = 71;
    private static final int PICKPOCKET_CLICK_DELAY_MIN_MS = 25;
    private static final int PICKPOCKET_CLICK_DELAY_MAX_MS = 100;
    private static final int BURST_WANDER_CHANCE_PERCENT = 82;
    private static final int BURST_WANDER_MAX_X = 8;
    private static final int BURST_WANDER_MAX_UP = 8;
    private static final int BURST_WANDER_MAX_DOWN = 4;
    private static final long INVENTORY_FULL_SIGNAL_MAX_AGE_MS = 1_000;
    private static final long ACTIVE_INTERACTION_GRACE_MS = 350;
    private static final long MISCLICK_MOVEMENT_WINDOW_MS = 800;
    private static final long DRINK_COOLDOWN_MS = 1_750;
    private static final long COMBAT_CLEAR_SETTLE_MS = 650;
    private static final long COMBAT_RESET_RETRY_MS = 2_500;
    private static final long COMBAT_UNEQUIP_TIMEOUT_MS = 1_200;
    private static final long COMBAT_DISARM_KNOCKOUT_TIMEOUT_MS = 1_500;
    private static final long COMBAT_REEQUIP_TIMEOUT_MS = 3_000;
    private static final long COMBAT_WINE_DROP_TIMEOUT_MS = 1_200;
    private static final long COMBAT_WINE_RECOVERY_TIMEOUT_MS = 8_000;
    private static final long FAILED_KNOCKOUT_RETALIATION_GRACE_MS = 1_800;
    private static final long SUSTAINED_NPC_ATTACK_MS = 3_000;
    private static final long CAMERA_REFACING_MIN_DELAY_MS = 450;
    private static final long CAMERA_REFACING_MAX_DELAY_MS = 951;
    private static final int CAMERA_PIVOT_THRESHOLD = 24;
    private static final long CAMERA_PITCH_COOLDOWN_MS = 1_000;
    private static final long CAMERA_ZOOM_COOLDOWN_MS = 1_000;
    private static final int CAMERA_ZOOM_TOLERANCE_PERCENT = 5;
    private static final long DOOR_INTERACTION_DELAY_MS = 650;
    private static final long WINE_DOOR_CROSSING_RETRY_MS = 350;
    private static final long WINE_DOOR_CROSSING_TIMEOUT_MS = 30_000;
    private static final long WINE_DOOR_RESOLUTION_TIMEOUT_MS = 12_000;
    private static final long WINE_MERCHANT_RESOLUTION_TIMEOUT_MS = 12_000;
    private static final long WINE_EXIT_STATE_TIMEOUT_MS = 20_000;
    private static final long WINE_EXIT_DIAGNOSTIC_INTERVAL_MS = 1_000;
    private static final long SOUTH_TENT_POPULATION_CHECK_MS = 500;
    private static final long SOUTH_TENT_PREPARATION_TIMEOUT_MS = 120_000;
    private static final long SOUTH_TENT_TRANSIT_TIMEOUT_MS = 20_000;
    private static final long SOUTH_TENT_PHASE_TIMEOUT_MS = 12_000;
    private static final long LURE_DIALOGUE_OPEN_TIMEOUT_MS = 4_000;
    private static final long LURE_DIALOGUE_QUIET_MS = 700;
    private static final long LURE_FOLLOW_CHECK_INTERVAL_MS = 500;
    private static final long LURE_FOLLOW_LOST_GRACE_MS = 1_500;
    private static final long LURE_RELEASE_TIMEOUT_MS = 12_000;
    private static final int LURE_MAX_ATTEMPTS = 5;
    private static final int LURE_FOLLOW_CONFIRMATIONS_REQUIRED = 3;
    private static final int LURE_FOLLOW_MAX_DISTANCE = 3;
    private static final int TARGET_CAMERA_PITCH = 332;
    private static final int TARGET_CAMERA_PITCH_TOLERANCE = 8;
    private static final long HUMANIZER_MOUSE_MIN_INTERVAL_MS = 45_000;
    private static final long HUMANIZER_MOUSE_MAX_INTERVAL_MS = 120_001;

    private enum KnockoutResult
    {
        NONE,
        PENDING,
        SUCCESS,
        FAILED
    }

    private enum CombatResetPhase
    {
        UNTRIED,
        DROPPING_WINE,
        UNEQUIPPING,
        KNOCKING_OUT,
        REEQUIPPING,
        RECOVERING_WINE,
        FALLBACK
    }

    private enum SouthernTentLureMode
    {
        NONE,
        INTO_TENT,
        OUT_OF_TENT
    }

    enum SouthernTentPhase
    {
        ASSESSING(SOUTH_TENT_PREPARATION_TIMEOUT_MS),
        LEAVING_TO_SEARCH(SOUTH_TENT_TRANSIT_TIMEOUT_MS),
        SEARCHING_TARGET(SOUTH_TENT_PREPARATION_TIMEOUT_MS),
        APPROACHING_TARGET(SOUTH_TENT_TRANSIT_TIMEOUT_MS),
        STARTING_LURE(SOUTH_TENT_PHASE_TIMEOUT_MS),
        WAITING_FOR_DIALOGUE(LURE_DIALOGUE_OPEN_TIMEOUT_MS),
        ADVANCING_DIALOGUE(SOUTH_TENT_PHASE_TIMEOUT_MS),
        VERIFYING_FOLLOW(SOUTH_TENT_PHASE_TIMEOUT_MS),
        MOVING_TO_CURTAIN(SOUTH_TENT_TRANSIT_TIMEOUT_MS),
        OPENING_CURTAIN(SOUTH_TENT_PHASE_TIMEOUT_MS),
        LEADING_THROUGH_CURTAIN(SOUTH_TENT_TRANSIT_TIMEOUT_MS),
        POSITIONING_TO_CLOSE(SOUTH_TENT_PHASE_TIMEOUT_MS),
        CLOSING_CURTAIN(SOUTH_TENT_PHASE_TIMEOUT_MS),
        WAITING_FOR_RELEASE(LURE_RELEASE_TIMEOUT_MS),
        CLIMBING_PUB_STAIRS(20_000),
        WAITING_UPSTAIRS(10_000),
        DESCENDING_PUB_STAIRS(20_000),
        RETURNING_TO_TENT(SOUTH_TENT_TRANSIT_TIMEOUT_MS);

        private final long timeoutMs;

        SouthernTentPhase(long timeoutMs)
        {
            this.timeoutMs = timeoutMs;
        }

        boolean timedOut(long elapsedMs)
        {
            return elapsedMs >= timeoutMs;
        }
    }

    private enum Outcome
    {
        KNOCKOUT_SUCCESS,
        KNOCKOUT_FAILED,
        PICKPOCKET_SUCCESS,
        PICKPOCKET_FAILED,
        STUNNED,
        INVENTORY_FULL,
        WINE_EXCHANGED
    }

    private final ConcurrentLinkedQueue<Outcome> outcomes = new ConcurrentLinkedQueue<>();

    @Getter
    private volatile BlackjackState state = BlackjackState.STOPPED;
    @Getter
    private volatile String nextAction = "Enable plugin";
    @Getter
    private volatile String targetDescription = "None";
    @Getter
    private volatile String stopReason = "";
    @Getter
    private volatile int successfulKnockouts;
    @Getter
    private volatile int failedKnockouts;
    @Getter
    private volatile int successfulPickpockets;
    @Getter
    private volatile int picksThisKnockout;
    @Getter
    private volatile String lastOutcome = "None";
    @Getter
    private volatile boolean combatSignal;
    @Getter
    private volatile int knockoutDispatchTimeouts;
    @Getter
    private volatile int burstTimeouts;
    @Getter
    private volatile int pickpocketClicks;
    @Getter
    private volatile int combatResetRetries;
    @Getter
    private volatile boolean wineRestockPending;
    @Getter
    private volatile int projectedWinesNeeded;
    @Getter
    private volatile String humanizerStatus = "Disabled";
    @Getter
    private volatile int humanizerEvents;

    private BlackjackConfig config;
    private Instant startTime;
    private volatile int startXp = -1;
    private volatile BlackjackTarget automaticTarget;
    private long banditDoorCloseAttemptAt;
    private int lastObservedThievingXp;
    private int targetIndex = -1;
    private int southernTentLureTargetIndex = -1;
    private NPC southernTentLureNpc;
    private Boolean autoRunBeforeLure;
    private SouthernTentLureMode southernTentLureMode = SouthernTentLureMode.NONE;
    private SouthernTentPhase southernTentPhase = SouthernTentPhase.ASSESSING;
    private long southernTentPhaseEnteredAt;
    private long southernCrossingStartedAt;
    private long lastSouthernLureWaitLogAt;
    private WorldPoint pendingSouthernCurtain;
    private String pendingSouthernCurtainAction;
    private long pendingSouthernCurtainAt;
    private long pubStairActionAt;
    private long pubStairDiagnosticAt;
    private long lastRunRestoreAttemptAt;
    private long lastBlackjackFeedbackAt;
    private long lastContinuousRecoveryAt;
    private int cameraFacingTargetIndex = -1;
    private int cameraStandingYaw;
    private boolean restoreZoomAfterTravel;
    private final BlackjackZoomRestore zoomRestore = new BlackjackZoomRestore();
    private long nextSouthernTentActionAt;
    private long nextSouthernTentPopulationCheckAt;
    private long lureDialogueLastSeenAt;
    private long lureFollowNextCheckAt;
    private long lureFollowLostSince;
    private long lureReleaseClearSince;
    private int lureAttempts;
    private int lureContinueClicks;
    private int lureFollowConfirmations;
    private long stateEnteredAt;
    private long lastInteractionAt;
    private long lastDrinkAt;
    private long combatClearSince;
    private long npcInteractionSince;
    private long ignoreCombatUntil;
    private long knockoutClickIssuedAt;
    private long knockoutAimStartedAt;
    private int knockoutAimFailures;
    private int knockoutAimRecoveryStage;
    private long knockoutBurstReleaseAt;
    private boolean knockoutFallbackReleased;
    private long pickpocketBurstStartedAt;
    private long nextPickpocketClickAt;
    private long lastPickpocketClickAt;
    private long nextKnockoutArmedAt;
    private long knockoutFailedAt;
    private boolean secondPickpocketInteractionSeen;
    private boolean secondPickpocketInteractionComplete;
    private long pendingInventoryFullAt;
    private long targetClearRecheckAt;
    private Point burstClickPoint;
    private Point standingClickAnchor;
    private int standingAnchorTargetIndex;
    private WorldPoint standingAnchorTargetTile;
    private int standingAnchorMenuMisses;
    private boolean curtainCameraAdjusted;
    private boolean healingRequired;
    private long nextCameraRefacingAt;
    private long lastCameraPitchAt;
    private long lastCameraZoomAt;
    private int targetCameraZoom;
    private int visibilityCameraZoom = -1;
    private int hiddenTargetZoomAttempts;
    private int hiddenTargetIndex = -1;
    private long nextHiddenTargetRecoveryAt;
    private long visibilityStableSince;
    private WorldPoint lastCameraTargetLocation;
    private String targetClearReason = "None";
    private boolean restockAfterCombatReset;
    private boolean waitingForRestockKnockout;
    private boolean emergencyWineExit;
    private boolean wineInventoryPrepared;
    private int restockTargetWineCount;
    private long wineExitCurtainOpenedAt;
    private int wineDoorCrossingAttempts;
    private long wineDoorCrossingStartedAt;
    private long wineDoorMissingSince;
    private long wineMerchantMissingSince;
    private long lastWineExitDiagnosticAt;
    private long nextHumanizerMouseAt;
    private long humanizerMouseRecoverAt;
    private long nextMicroBreakAt;
    private long nextSmallBreakAt;
    private long humanizerBreakUntil;
    private long knockoutAttemptReadyAt;
    private long knockoutRetryAt;
    private CombatResetPhase combatResetPhase = CombatResetPhase.UNTRIED;
    private long combatResetPhaseAt;
    private long combatResetKnockoutAt;
    private long combatResetClearSince;
    private boolean combatResetKnockoutSucceeded;
    private boolean combatWineDropped;
    private WorldPoint combatWineDropTile;
    private String humanizerBreakType = "None";
    private volatile KnockoutResult knockoutResult = KnockoutResult.NONE;
    private volatile boolean shutdownRequested;
    private String lastContinuousAction;

    @Inject
    public BlackjackScript()
    {
    }

    public boolean run(BlackjackConfig config)
    {
        cameraTurn.cancel();
        closeBreakPreparation();
        breakPreparation = BreakPreparation.register("blackjack");
        breakPhase = BreakPhase.NONE;
        breakRequestedAt = 0;
        this.config = config;
        automaticTarget = null;
        banditDoorCloseAttemptAt = 0;
        outcomes.clear();
        successfulKnockouts = 0;
        failedKnockouts = 0;
        successfulPickpockets = 0;
        picksThisKnockout = 0;
        lastOutcome = "None";
        combatSignal = false;
        knockoutDispatchTimeouts = 0;
        burstTimeouts = 0;
        pickpocketClicks = 0;
        combatResetRetries = 0;
        wineRestockPending = false;
        projectedWinesNeeded = 0;
        humanizerStatus = config.humanizerEnabled() ? "Scheduled" : "Disabled";
        humanizerEvents = 0;
        targetIndex = -1;
        resetSouthernTentPreparation();
        nextSouthernTentPopulationCheckAt = 0;
        npcInteractionSince = 0;
        ignoreCombatUntil = 0;
        knockoutClickIssuedAt = 0;
        knockoutAimStartedAt = 0;
        knockoutAimFailures = 0;
        knockoutAimRecoveryStage = 0;
        knockoutBurstReleaseAt = 0;
        knockoutFallbackReleased = false;
        pickpocketBurstStartedAt = 0;
        nextPickpocketClickAt = 0;
        lastPickpocketClickAt = 0;
        nextKnockoutArmedAt = 0;
        knockoutFailedAt = 0;
        secondPickpocketInteractionSeen = false;
        secondPickpocketInteractionComplete = false;
        pendingInventoryFullAt = 0;
        targetClearRecheckAt = 0;
        burstClickPoint = null;
        clearStandingClickAnchor();
        curtainCameraAdjusted = false;
        healingRequired = false;
        nextCameraRefacingAt = 0;
        lastCameraPitchAt = 0;
        lastCameraZoomAt = 0;
        targetCameraZoom = config.returnCameraZoom() > 0 ? config.returnCameraZoom() : Rs2Camera.getZoom();
        zoomRestore.reset();
        visibilityCameraZoom = -1;
        hiddenTargetZoomAttempts = 0;
        hiddenTargetIndex = -1;
        nextHiddenTargetRecoveryAt = 0;
        visibilityStableSince = 0;
        log.info("Captured operator blackjack zoom on enable: {}", targetCameraZoom);
        lastBlackjackFeedbackAt = 0;
        lastContinuousRecoveryAt = 0;
        cameraFacingTargetIndex = -1;
        restoreZoomAfterTravel = true;
        lastCameraTargetLocation = null;
        targetClearReason = "None";
        restockAfterCombatReset = false;
        waitingForRestockKnockout = false;
        emergencyWineExit = false;
        wineInventoryPrepared = false;
        restockTargetWineCount = 0;
        wineExitCurtainOpenedAt = 0;
        wineDoorCrossingAttempts = 0;
        wineDoorCrossingStartedAt = 0;
        wineDoorMissingSince = 0;
        wineMerchantMissingSince = 0;
        lastWineExitDiagnosticAt = 0;
        long now = System.currentTimeMillis();
        nextHumanizerMouseAt = scheduleFromNow(now,
                HUMANIZER_MOUSE_MIN_INTERVAL_MS, HUMANIZER_MOUSE_MAX_INTERVAL_MS);
        nextMicroBreakAt = now + configuredBreakInterval(true);
        nextSmallBreakAt = now + configuredBreakInterval(false);
        humanizerMouseRecoverAt = 0;
        humanizerBreakUntil = 0;
        knockoutAttemptReadyAt = 0;
        knockoutRetryAt = 0;
        resetDisarmedCombatRecovery();
        humanizerBreakType = "None";
        knockoutResult = KnockoutResult.NONE;
        lastContinuousAction = null;
        shutdownRequested = false;
        stopReason = "";
        startTime = Instant.now();
        startXp = -1;
        lastObservedThievingXp = 0;
        transition(BlackjackState.STARTING, "Validate setup");

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try
            {
                if (!super.run() || !Microbot.isLoggedIn())
                {
                    return;
                }

                processOutcomes();
                if (shutdownRequested || state == BlackjackState.ERROR || state == BlackjackState.STOPPED)
                {
                    return;
                }

                if (!ensureXpBaseline())
                {
                    nextAction = "Wait for logged-in Thieving XP snapshot";
                    return;
                }
                updateHealingRequirement();
                if (handleBreakPreparation())
                {
                    return;
                }
                maintainTopDownCamera();

                if (handleWineRestockPriority())
                {
                    if (healingRequired && isCombatSafetyState()
                            && drinkWineIfReady("Heal while securing wine run"))
                    {
                        return;
                    }
                    executeState();
                    return;
                }

                if (healingRequired)
                {
                    if (state == BlackjackState.PREPARING_SOUTHERN_TENT)
                    {
                        if (Rs2Inventory.count(WINE_ID) == 0)
                        {
                            heal();
                            return;
                        }
                        if (drinkWineIfReady("Heal during southern tent preparation"))
                        {
                            // Drinking can dismiss the dialogue; restart that exchange deliberately.
                            if (southernTentPhase == SouthernTentPhase.WAITING_FOR_DIALOGUE
                                    || southernTentPhase == SouthernTentPhase.ADVANCING_DIALOGUE)
                            {
                                retrySouthernLure("Healing interrupted Lure dialogue");
                            }
                        }
                        return;
                    }
                    if (isCombatSafetyState())
                    {
                        if (drinkWineIfReady("Heal while resetting combat"))
                        {
                            return;
                        }
                    }
                    else
                    {
                        if (state != BlackjackState.HEALING && state != BlackjackState.RESTOCKING_WINE)
                        {
                            transition(BlackjackState.HEALING, "Drink wine to configured HP");
                        }
                        executeState();
                        return;
                    }
                }

                if (handleSouthernTentPopulationPriority())
                {
                    executeState();
                    return;
                }

                if (state != BlackjackState.POSITIONING_COMBAT_RESET
                        && state != BlackjackState.ESCAPING_COMBAT
                        && state != BlackjackState.WAITING_FOR_COMBAT_CLEAR
                        && shouldEscapeCombat())
                {
                    transition(BlackjackState.POSITIONING_COMBAT_RESET, "Move to combat staging tile");
                }

                if (handleHumanizerPriority())
                {
                    executeState();
                    return;
                }

                executeState();
            }
            catch (Exception ex)
            {
                if (shutdownRequested || state == BlackjackState.STOPPED)
                {
                    return;
                }
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
                fail("Unexpected script error");
            }
        }, 0, 35, TimeUnit.MILLISECONDS);
        return true;
    }

    private void executeState()
    {
        switch (state)
        {
            case STARTING:
            case VALIDATING:
                validateSetup();
                break;
            case RETURNING_TO_HOUSE:
                returnToHouse();
                break;
            case PREPARING_SOUTHERN_TENT:
                prepareSouthernTent();
                break;
            case FINDING_TARGET:
                acquireTarget();
                break;
            case KNOCKING_OUT:
            case PICKPOCKETING:
                runContinuousBlackjackLoop();
                break;
            case WAITING_FOR_TARGET_CLEAR:
                waitForTargetClear();
                break;
            case HEALING:
                heal();
                break;
            case POSITIONING_COMBAT_RESET:
                positionCombatReset();
                break;
            case ESCAPING_COMBAT:
                escapeCombat();
                break;
            case WAITING_FOR_COMBAT_CLEAR:
                waitForCombatClear();
                break;
            case EXITING_FOR_WINE:
                exitHouseForWine();
                break;
            case SECURING_WINE_EXIT:
                secureWineExit();
                break;
            case RESTOCKING_WINE:
                restockWine();
                break;
            case RETURNING_WITH_WINE:
                returnWithWine();
                break;
            case SECURING_WINE_ENTRY:
                secureWineEntry();
                break;
            case HUMANIZER_MOUSE:
                recoverHumanizerMouse();
                break;
            case HUMANIZER_BREAK:
                runHumanizerBreak();
                break;
            default:
                break;
        }
    }

    private boolean handleBreakPreparation()
    {
        boolean requested = breakPreparation != null && breakPreparation.isRequested();
        if (!requested)
        {
            if (breakPhase == BreakPhase.NONE)
            {
                breakRequestedAt = 0;
                return false;
            }
            if (breakPhase != BreakPhase.RELEASED)
            {
                fail("Break preparation cancelled before reaching safety");
                return true;
            }
            breakPhase = BreakPhase.NONE;
            breakRequestedAt = 0;
            targetIndex = -1;
            clearStandingClickAnchor();
            transition(breakResumeState, "Resume after scheduled break");
            return true;
        }
        if (breakPhase == BreakPhase.RELEASED)
        {
            return true;
        }
        long now = System.currentTimeMillis();
        if (breakRequestedAt == 0)
        {
            breakRequestedAt = now;
        }
        if (now - breakRequestedAt >= 110_000)
        {
            fail("Unable to reach a safe break location; break request cancelled");
            return true;
        }
        // Finish luring/critical healing instead of abandoning a follower or food run.
        if (breakPhase == BreakPhase.NONE && (state == BlackjackState.PREPARING_SOUTHERN_TENT
                || (healingRequired && Rs2Inventory.count(WINE_ID) == 0)))
        {
            return false;
        }
        if (healingRequired && Rs2Inventory.count(WINE_ID) > 0)
        {
            drinkWineIfReady("Heal before scheduled break");
            return true;
        }
        if (breakPhase == BreakPhase.NONE)
        {
            breakResumeState = wineRestockPending || isWineRestockState()
                    ? BlackjackState.RESTOCKING_WINE : BlackjackState.RETURNING_TO_HOUSE;
            setBreakPhase(BreakPhase.FINISHING_CYCLE, "Finish current pickpocket window before break");
        }
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null)
        {
            return true;
        }
        switch (breakPhase)
        {
            case FINISHING_CYCLE:
                Rs2NpcModel target = currentTarget();
                if (target != null && target.getAnimation() == AnimationID.HUMAN_UNCONSCIOUS
                        && now - breakPhaseAt < 3_000)
                {
                    if (now >= nextPickpocketClickAt)
                    {
                        Point anchor = targetAnchor(target, burstClickPoint);
                        if (anchor != null)
                        {
                            moveAndLeftClickTargetOption(target, anchor, "Pickpocket");
                        }
                        nextPickpocketClickAt = now + randomBetween(100, 201);
                    }
                    return true;
                }
                // A completed outward wine leg already secured the curtain.
                if (!isInsideHouse() && (state == BlackjackState.RESTOCKING_WINE
                        || state == BlackjackState.RETURNING_WITH_WINE))
                {
                    setBreakPhase(BreakPhase.QUIET, "Wait for combat to clear before break");
                }
                else
                {
                    setBreakPhase(BreakPhase.EXITING, "Leave and secure tent before break");
                    transition(BlackjackState.EXITING_FOR_WINE, "Leave tent for scheduled break");
                }
                break;
            case EXITING:
                if (state == BlackjackState.EXITING_FOR_WINE)
                {
                    if (selectedTarget() != BlackjackTarget.MENAPHITE_THUG
                            || !openSouthernInnerCurtainIfNeeded(activeWineDoorOutsideTile()))
                    {
                        exitHouseForWine();
                    }
                }
                else if (state == BlackjackState.SECURING_WINE_EXIT)
                {
                    secureWineExit();
                }
                else if (state == BlackjackState.RESTOCKING_WINE)
                {
                    pubStairActionAt = 0;
                    setBreakPhase(selectedTarget() == BlackjackTarget.MENAPHITE_THUG
                            ? BreakPhase.CLIMBING : BreakPhase.QUIET, "Clear follower before break");
                }
                break;
            case CLIMBING:
                if (player.getPlane() == 1)
                {
                    setBreakPhase(BreakPhase.UPSTAIRS, "Wait upstairs to release follower");
                }
                else
                {
                    clickBreakStairs(player, 0);
                }
                break;
            case UPSTAIRS:
                if (player.getPlane() != 1)
                {
                    fail("Unexpected floor during break preparation");
                }
                else if (now - breakPhaseAt >= 3_000)
                {
                    pubStairActionAt = 0;
                    setBreakPhase(BreakPhase.DESCENDING, "Return downstairs before break");
                }
                break;
            case DESCENDING:
                if (player.getPlane() == 0)
                {
                    setBreakPhase(BreakPhase.QUIET, "Wait for combat to clear before break");
                }
                else
                {
                    clickBreakStairs(player, 1);
                }
                break;
            case QUIET:
                if (player.getPlane() != 0 || isInsideHouse() || Rs2Player.isMoving()
                        || Rs2Player.isInCombat() || Rs2Player.isInteracting() || isNpcTargetingPlayer())
                {
                    breakQuietSince = 0;
                }
                else if (breakQuietSince == 0)
                {
                    breakQuietSince = now;
                }
                else if (now - breakQuietSince >= 1_800)
                {
                    breakPreparation.ready();
                    setBreakPhase(BreakPhase.RELEASED, "Safe for scheduled break");
                    transition(BlackjackState.WAITING_FOR_BREAK, "Wait for Break Handler to finish");
                }
                break;
            default:
                break;
        }
        return true;
    }

    private void setBreakPhase(BreakPhase phase, String description)
    {
        log.info("Blackjack break preparation {} -> {} ({})", breakPhase, phase, description);
        breakPhase = phase;
        breakPhaseAt = System.currentTimeMillis();
        breakQuietSince = 0;
        nextAction = description;
    }

    private void clickBreakStairs(WorldPoint player, int plane)
    {
        if (player.getPlane() != plane)
        {
            fail("Unexpected floor approaching break staircase");
            return;
        }
        long now = System.currentTimeMillis();
        if (now - pubStairActionAt < 3_000)
        {
            return;
        }
        WorldPoint tile = new WorldPoint(3353, 2958, plane);
        if (player.distanceTo2D(tile) > 2)
        {
            walkSouthernRoute(tile, true, "Approach pub stairs for break");
            return;
        }
        Rs2TileObjectModel stairs = Microbot.getRs2TileObjectCache().query()
                .withId(plane == 0 ? 6242 : 6243)
                .where(object -> tile.equals(object.getWorldLocation())).first();
        if (stairs != null && clickVerifiedPubStaircase(stairs, plane == 0 ? "Climb-up" : "Climb-down"))
        {
            pubStairActionAt = now;
        }
    }

    private void closeBreakPreparation()
    {
        if (breakPreparation != null)
        {
            breakPreparation.close();
            breakPreparation = null;
        }
    }

    public String getBreakStatus()
    {
        if (state == BlackjackState.ERROR || state == BlackjackState.STOPPED)
        {
            return "Stopped";
        }
        return breakPhase == BreakPhase.NONE ? "Protected" : breakPhase.toString();
    }

    public int getWinesToHeal()
    {
        return config == null ? 0 : winesNeededToReach(currentHitpoints(), config.healToPercent());
    }

    private void validateSetup()
    {
        transition(BlackjackState.VALIDATING, "Check level and equipment");
        int level = Rs2Player.getRealSkillLevel(Skill.THIEVING);
        if (level < 45)
        {
            fail("45 Thieving required");
            return;
        }
        int targetRequirement = requiredThievingLevel(selectedTarget());
        if (level < targetRequirement)
        {
            fail(expectedTargetDescription() + " requires " + targetRequirement + " Thieving");
            return;
        }

        if (!hasBlackjackEquipped())
        {
            if (Rs2Inventory.items(item -> item.getName() != null
                    && item.getName().toLowerCase(Locale.ROOT).contains("blackjack")).findAny().isPresent())
            {
                if (readyForInteraction(700))
                {
                    Rs2Inventory.interact(item -> item.getName() != null
                            && item.getName().toLowerCase(Locale.ROOT).contains("blackjack"), "Wield");
                    lastInteractionAt = System.currentTimeMillis();
                }
                nextAction = "Equip blackjack";
                return;
            }
            fail("No blackjack equipped");
            return;
        }

        if (Rs2Inventory.count(WINE_ID) == 0)
        {
            if (config.autoRestockWine())
            {
                transition(BlackjackState.RESTOCKING_WINE, "Restock jug of wine");
            }
            else
            {
                fail("Out of jug of wine");
            }
            return;
        }

        if (selectedTarget() == BlackjackTarget.MENAPHITE_THUG)
        {
            beginSouthernTentPreparation("Prepare southern tent");
            return;
        }

        if (!isInsideHouse())
        {
            transition(BlackjackState.RETURNING_TO_HOUSE, "Return to marked house");
            return;
        }
        transition(BlackjackState.FINDING_TARGET, "Find level-appropriate target");
    }

    private void returnToHouse()
    {
        if (selectedTarget() == BlackjackTarget.MENAPHITE_THUG)
        {
            beginSouthernTentPreparation("Prepare southern tent");
            return;
        }
        if (isInsideHouse())
        {
            transition(BlackjackState.FINDING_TARGET, "Find level-appropriate target");
            return;
        }
        if (readyForInteraction(1_000))
        {
            Rs2Walker.walkTo(activeHouseCentre(), 0);
            lastInteractionAt = System.currentTimeMillis();
        }
    }

    private boolean handleSouthernTentPopulationPriority()
    {
        if (selectedTarget() != BlackjackTarget.MENAPHITE_THUG
                || state == BlackjackState.PREPARING_SOUTHERN_TENT
                || (state != BlackjackState.FINDING_TARGET
                && state != BlackjackState.KNOCKING_OUT
                && state != BlackjackState.PICKPOCKETING
                && state != BlackjackState.WAITING_FOR_TARGET_CLEAR))
        {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now < nextSouthernTentPopulationCheckAt)
        {
            return false;
        }
        nextSouthernTentPopulationCheckAt = now + SOUTH_TENT_POPULATION_CHECK_MS;

        List<Rs2NpcModel> occupants = southernTentTargets();
        if (occupants.size() <= 1)
        {
            return false;
        }

        log.info("Southern tent population changed to {} Menaphite Thugs; pausing blackjack to evict one",
                occupants.size());
        beginSouthernTentPreparation("Remove extra Menaphite Thug from southern tent");
        return true;
    }

    private void beginSouthernTentPreparation(String action)
    {
        if (state != BlackjackState.PREPARING_SOUTHERN_TENT)
        {
            resetSouthernTentPreparation();
            resetActiveBlackjackCycleForTentPreparation();
            transition(BlackjackState.PREPARING_SOUTHERN_TENT, action);
            return;
        }
        nextAction = action;
    }

    private void prepareSouthernTent()
    {
        if (selectedTarget() != BlackjackTarget.MENAPHITE_THUG)
        {
            resetSouthernTentPreparation();
            transition(BlackjackState.RETURNING_TO_HOUSE, "Return to marked Bandit house");
            return;
        }
        if (elapsedInState() >= SOUTH_TENT_PREPARATION_TIMEOUT_MS)
        {
            fail("Southern tent preparation timed out");
            return;
        }

        // Check before handlers: visible objects and dialogues must not bypass the deadline.
        if (southernCrossingStartedAt > 0
                && System.currentTimeMillis() - southernCrossingStartedAt >= 30_000)
        {
            fail("Southern curtain crossing did not complete; follower may still be attached");
            return;
        }
        if (southernTentPhase.timedOut(elapsedInSouthernTentPhase()))
        {
            if (southernTentPhase == SouthernTentPhase.WAITING_FOR_RELEASE
                    || southernTentPhase == SouthernTentPhase.CLIMBING_PUB_STAIRS
                    || southernTentPhase == SouthernTentPhase.WAITING_UPSTAIRS
                    || southernTentPhase == SouthernTentPhase.DESCENDING_PUB_STAIRS)
            {
                fail(southernTentPhase == SouthernTentPhase.WAITING_FOR_RELEASE
                        ? "Evicted Menaphite Thug did not stop following outside southern tent"
                        : "Pub follower reset timed out in " + southernTentPhase);
            }
            else if (southernTentPhase == SouthernTentPhase.WAITING_FOR_DIALOGUE
                    || southernTentPhase == SouthernTentPhase.ADVANCING_DIALOGUE
                    || southernTentPhase == SouthernTentPhase.VERIFYING_FOLLOW)
            {
                retrySouthernLure("Lure phase timed out: " + southernTentPhase);
            }
            else
            {
                restartSouthernTentAssessment("Southern tent phase timed out: " + southernTentPhase);
            }
            return;
        }

        switch (southernTentPhase)
        {
            case ASSESSING:
                assessSouthernTent();
                break;
            case LEAVING_TO_SEARCH:
                transitSouthernTent(false);
                break;
            case SEARCHING_TARGET:
                searchForSouthernLureTarget();
                break;
            case APPROACHING_TARGET:
                approachSouthernLureTarget();
                break;
            case STARTING_LURE:
                startSouthernLure();
                break;
            case WAITING_FOR_DIALOGUE:
                waitForSouthernLureDialogue();
                break;
            case ADVANCING_DIALOGUE:
                advanceSouthernLureDialogue();
                break;
            case VERIFYING_FOLLOW:
                verifySouthernLureFollow();
                break;
            case MOVING_TO_CURTAIN:
                moveLuredTargetToCurtain();
                break;
            case OPENING_CURTAIN:
                openSouthernTentCurtainForLure();
                break;
            case LEADING_THROUGH_CURTAIN:
                leadTargetThroughSouthernCurtain();
                break;
            case POSITIONING_TO_CLOSE:
                positionToCloseSouthernCurtain();
                break;
            case CLOSING_CURTAIN:
                closeSouthernCurtainAfterLure();
                break;
            case WAITING_FOR_RELEASE:
                waitForEvictedTargetRelease();
                break;
            case CLIMBING_PUB_STAIRS:
            case WAITING_UPSTAIRS:
            case DESCENDING_PUB_STAIRS:
                resetEvictedFollowerAtPub();
                break;
            case RETURNING_TO_TENT:
                transitSouthernTent(true);
                break;
            default:
                setSouthernTentPhase(SouthernTentPhase.ASSESSING, "Reassess southern tent");
                break;
        }
    }

    private void assessSouthernTent()
    {
        List<Rs2NpcModel> occupants = southernTentTargets();
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null)
        {
            nextAction = "Wait for player location";
            return;
        }

        if (occupants.isEmpty())
        {
            targetIndex = -1;
            targetDescription = expectedTargetDescription();
            setSouthernTentPhase(isInsideSouthTent(player)
                            ? SouthernTentPhase.LEAVING_TO_SEARCH
                            : SouthernTentPhase.SEARCHING_TARGET,
                    isInsideSouthTent(player)
                            ? "Leave empty southern tent to find a thug"
                            : "Find a Menaphite Thug to lure");
            return;
        }

        if (occupants.size() == 1)
        {
            Rs2NpcModel occupant = occupants.get(0);
            targetIndex = occupant.getIndex();
            targetDescription = occupant.getName() + " (level " + occupant.getCombatLevel() + ")";

            Rs2TileObjectModel closedCurtain = findSouthTentCurtain("Open");
            if (isInsideSouthTent(player) && closedCurtain != null)
            {
                log.info("Southern tent prepared with one Menaphite Thug at {}", occupant.getWorldLocation());
                resetSouthernTentPreparation();
                transition(BlackjackState.FINDING_TARGET, "Use isolated Menaphite Thug");
                return;
            }

            setSouthernTentPhase(SouthernTentPhase.RETURNING_TO_TENT,
                    isInsideSouthTent(player)
                            ? "Close southern tent curtain"
                            : "Enter southern tent with existing thug");
            return;
        }

        if (!isInsideSouthTent(player))
        {
            setSouthernTentPhase(SouthernTentPhase.RETURNING_TO_TENT,
                    "Enter southern tent before removing extra thug");
            return;
        }

        Rs2NpcModel retained = chooseRetainedSouthernTarget(occupants);
        Rs2NpcModel extra = chooseExtraSouthernTarget(occupants, retained == null ? -1 : retained.getIndex());
        if (retained == null || extra == null)
        {
            nextAction = "Wait for southern tent population to settle";
            return;
        }

        targetIndex = retained.getIndex();
        beginSouthernLure(SouthernTentLureMode.OUT_OF_TENT, extra,
                "Lure extra Menaphite Thug outside");
        log.info("Southern tent has {} thugs; retaining index={} at {} and evicting index={} at {}",
                occupants.size(), retained.getIndex(), retained.getWorldLocation(),
                extra.getIndex(), extra.getWorldLocation());
    }

    private void searchForSouthernLureTarget()
    {
        if (!southernTentTargets().isEmpty())
        {
            setSouthernTentPhase(SouthernTentPhase.ASSESSING,
                    "A Menaphite Thug entered the southern tent");
            return;
        }

        Rs2NpcModel candidate = findOutsideSouthernLureTarget();
        if (candidate != null)
        {
            beginSouthernLure(SouthernTentLureMode.INTO_TENT, candidate,
                    "Approach Menaphite Thug for Lure");
            return;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player != null && player.distanceTo2D(SOUTH_THUG_LURE_SEARCH_TILE) > 2)
        {
            walkSouthernRoute(SOUTH_THUG_LURE_SEARCH_TILE, true,
                    "Move outside southern tent to find a thug");
            return;
        }
        nextAction = "Waiting for reachable Menaphite Thug near southern tent";
    }

    private void beginSouthernLure(SouthernTentLureMode mode, Rs2NpcModel target, String action)
    {
        southernTentLureMode = mode;
        southernTentLureTargetIndex = target.getIndex();
        southernTentLureNpc = target.getNpc();
        lureAttempts = 0;
        lureContinueClicks = 0;
        lureFollowConfirmations = 0;
        lureDialogueLastSeenAt = 0;
        lureFollowNextCheckAt = 0;
        lureFollowLostSince = 0;
        lureReleaseClearSince = 0;
        setSouthernTentPhase(SouthernTentPhase.APPROACHING_TARGET, action);
    }

    private void approachSouthernLureTarget()
    {
        Rs2NpcModel target = southernTentLureTarget();
        if (target == null)
        {
            restartSouthernTentAssessment("Lure target disappeared while approaching");
            return;
        }
        WorldPoint player = Rs2Player.getWorldLocation();
        WorldPoint targetLocation = target.getWorldLocation();
        if (player == null || targetLocation == null)
        {
            nextAction = "Wait for lure target location";
            return;
        }

        if (target.getAnimation() == AnimationID.HUMAN_UNCONSCIOUS)
        {
            nextAction = "Wait for extra thug to stand before Lure";
            return;
        }
        if (target.getInteracting() != null && !target.isInteractingWithPlayer())
        {
            restartSouthernTentAssessment("Lure target is occupied by another actor");
            return;
        }
        if (openSouthernInnerCurtainIfNeeded(targetLocation))
        {
            return;
        }

        if (player.distanceTo2D(targetLocation) <= 2 && target.hasLineOfSight())
        {
            setSouthernTentPhase(SouthernTentPhase.STARTING_LURE, "Use Lure on Menaphite Thug");
            return;
        }
        walkSouthernRoute(targetLocation, true, "Approach Menaphite Thug for Lure");
    }

    private void startSouthernLure()
    {
        long now = System.currentTimeMillis();
        if (now < nextSouthernTentActionAt)
        {
            return;
        }

        Rs2NpcModel target = southernTentLureTarget();
        if (target == null)
        {
            restartSouthernTentAssessment("Lure target disappeared before interaction");
            return;
        }
        if (lureAttempts >= LURE_MAX_ATTEMPTS)
        {
            restartSouthernTentAssessment("Lure was not accepted after " + lureAttempts + " attempts");
            return;
        }

        lureAttempts++;
        if (!target.click(LURE_ACTION))
        {
            nextSouthernTentActionAt = now + randomBetween(600, 951);
            nextAction = "Retry Lure interaction (" + lureAttempts + "/" + LURE_MAX_ATTEMPTS + ")";
            return;
        }

        lastInteractionAt = now;
        lureContinueClicks = 0;
        lureDialogueLastSeenAt = 0;
        setSouthernTentPhase(SouthernTentPhase.WAITING_FOR_DIALOGUE,
                "Wait for Lure dialogue (" + lureAttempts + "/" + LURE_MAX_ATTEMPTS + ")");
        log.info("Lure dispatched: mode={} targetIndex={} attempt={}",
                southernTentLureMode, southernTentLureTargetIndex, lureAttempts);
    }

    private void waitForSouthernLureDialogue()
    {
        if (Rs2Dialogue.isInDialogue())
        {
            lureDialogueLastSeenAt = System.currentTimeMillis();
            nextSouthernTentActionAt = lureDialogueLastSeenAt + randomBetween(550, 901);
            setSouthernTentPhase(SouthernTentPhase.ADVANCING_DIALOGUE,
                    "Complete Lure dialogue deliberately");
            return;
        }
    }

    private void advanceSouthernLureDialogue()
    {
        long now = System.currentTimeMillis();
        if (Rs2Dialogue.isInDialogue())
        {
            lureDialogueLastSeenAt = now;
            if (Rs2Dialogue.hasSelectAnOption())
            {
                nextAction = "Wait for known Lure dialogue continuation";
                return;
            }
            if (Rs2Dialogue.hasContinue() && now >= nextSouthernTentActionAt)
            {
                Rs2Dialogue.clickContinue();
                lureContinueClicks++;
                lastInteractionAt = now;
                nextSouthernTentActionAt = now + randomBetween(550, 901);
                nextAction = "Continue Lure dialogue (" + lureContinueClicks + ")";
            }
            return;
        }

        if (now - lureDialogueLastSeenAt < LURE_DIALOGUE_QUIET_MS)
        {
            nextAction = "Wait for Lure dialogue to settle";
            return;
        }

        lureFollowConfirmations = 0;
        lureFollowNextCheckAt = now + LURE_FOLLOW_CHECK_INTERVAL_MS;
        setSouthernTentPhase(SouthernTentPhase.VERIFYING_FOLLOW,
                "Confirm Menaphite Thug is following");
    }

    private void verifySouthernLureFollow()
    {
        long now = System.currentTimeMillis();
        if (now < lureFollowNextCheckAt)
        {
            return;
        }
        lureFollowNextCheckAt = now + LURE_FOLLOW_CHECK_INTERVAL_MS;

        Rs2NpcModel target = southernTentLureTarget();
        WorldPoint player = Rs2Player.getWorldLocation();
        WorldPoint targetLocation = target == null ? null : target.getWorldLocation();
        boolean following = target != null && SouthernTentLureSafety.followingNearby(
                player, targetLocation, target.isInteractingWithPlayer(), LURE_FOLLOW_MAX_DISTANCE);
        if (!following)
        {
            lureFollowConfirmations = 0;
            nextAction = "Wait for Lure follow signal";
            return;
        }

        lureFollowConfirmations++;
        nextAction = "Confirm Lure follow " + lureFollowConfirmations
                + "/" + LURE_FOLLOW_CONFIRMATIONS_REQUIRED;
        if (lureFollowConfirmations < LURE_FOLLOW_CONFIRMATIONS_REQUIRED)
        {
            return;
        }

        suspendAutoRunForLure();
        Rs2Player.toggleRunEnergy(false);
        setSouthernTentPhase(SouthernTentPhase.MOVING_TO_CURTAIN,
                southernTentLureMode == SouthernTentLureMode.INTO_TENT
                        ? "Lead Menaphite Thug to southern curtain"
                        : "Lead extra Menaphite Thug to southern curtain");
        log.info("Lure confirmed after {} sustained follow checks: mode={} targetIndex={}",
                lureFollowConfirmations, southernTentLureMode, southernTentLureTargetIndex);
    }

    private void retrySouthernLure(String reason)
    {
        log.warn("{}: mode={} targetIndex={} attempt={}/{}",
                reason, southernTentLureMode, southernTentLureTargetIndex,
                lureAttempts, LURE_MAX_ATTEMPTS);
        lureContinueClicks = 0;
        lureFollowConfirmations = 0;
        lureDialogueLastSeenAt = 0;
        lureFollowNextCheckAt = 0;
        lureFollowLostSince = 0;
        nextSouthernTentActionAt = System.currentTimeMillis() + randomBetween(600, 951);
        if (lureAttempts >= LURE_MAX_ATTEMPTS || southernTentLureTarget() == null)
        {
            restartSouthernTentAssessment(reason);
            return;
        }
        setSouthernTentPhase(SouthernTentPhase.APPROACHING_TARGET,
                "Retry Lure (" + (lureAttempts + 1) + "/" + LURE_MAX_ATTEMPTS + ")");
    }

    private void moveLuredTargetToCurtain()
    {
        Rs2NpcModel target = southernTentLureTarget();
        if (!canContinueSouthernLureRoute(target))
        {
            long now = System.currentTimeMillis();
            if (southernTentPhase == SouthernTentPhase.MOVING_TO_CURTAIN
                    && now - lastSouthernLureWaitLogAt >= 5_000)
            {
                lastSouthernLureWaitLogAt = now;
                log.info("Southern lure approach waiting: reason={} player={} target={} following={} crossingAgeMs={}",
                        nextAction, Rs2Player.getWorldLocation(),
                        target == null ? null : target.getWorldLocation(),
                        target != null && target.isInteractingWithPlayer(),
                        southernCrossingStartedAt == 0 ? 0 : now - southernCrossingStartedAt);
            }
            return;
        }
        WorldPoint player = Rs2Player.getWorldLocation();

        WorldPoint approach = southernTentLureMode == SouthernTentLureMode.INTO_TENT
                ? SOUTH_THUG_WINE_DOOR_OUTSIDE_TILE
                : SOUTH_THUG_WINE_DOOR_INSIDE_TILE;
        if (player == null)
        {
            return;
        }
        if (player.distanceTo2D(approach) <= 1)
        {
            setSouthernTentPhase(SouthernTentPhase.OPENING_CURTAIN,
                    "Open southern curtain for lured thug");
            return;
        }
        walkSouthernRoute(approach, false, "Walk lured thug to southern curtain");
    }

    private boolean canContinueSouthernLureRoute(Rs2NpcModel target)
    {
        if (target == null)
        {
            restartSouthernTentAssessment("Lure target disappeared during route");
            return false;
        }
        long now = System.currentTimeMillis();
        if (!target.isInteractingWithPlayer())
        {
            if (lureFollowLostSince == 0)
            {
                lureFollowLostSince = now;
            }
            nextAction = "Wait for Lure follow signal to resume";
            if (now - lureFollowLostSince >= LURE_FOLLOW_LOST_GRACE_MS)
            {
                retrySouthernLure("Lured thug stopped following during route");
            }
            return false;
        }
        lureFollowLostSince = 0;
        if (openSouthernInnerCurtainIfNeeded(target.getWorldLocation()))
        {
            return false;
        }
        if (!SouthernTentLureSafety.followingNearby(Rs2Player.getWorldLocation(),
                target.getWorldLocation(), true, LURE_FOLLOW_MAX_DISTANCE))
        {
            nextAction = "Wait for lured thug to catch up";
            return false;
        }
        return true;
    }

    private void openSouthernTentCurtainForLure()
    {
        if (!canContinueSouthernLureRoute(southernTentLureTarget()))
        {
            return;
        }
        if (findSouthTentCurtain("Close") != null)
        {
            setSouthernTentPhase(SouthernTentPhase.LEADING_THROUGH_CURTAIN,
                    southernTentLureMode == SouthernTentLureMode.INTO_TENT
                            ? "Lead Menaphite Thug inside"
                            : "Lead extra Menaphite Thug outside");
            return;
        }

        Rs2TileObjectModel closedCurtain = findSouthTentCurtain("Open");
        if (closedCurtain != null)
        {
            interactWithSouthTentCurtain(closedCurtain, "Open", "Open southern curtain for Lure");
            return;
        }
        nextAction = "Wait for southern curtain to resolve";
    }

    private void leadTargetThroughSouthernCurtain()
    {
        Rs2NpcModel target = southernTentLureTarget();
        if (target == null)
        {
            restartSouthernTentAssessment("Lured target disappeared while crossing curtain");
            return;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        WorldPoint targetLocation = target.getWorldLocation();
        if (player == null || targetLocation == null)
        {
            nextAction = "Wait for curtain crossing location";
            return;
        }
        WorldPoint destination = southernTentLureMode == SouthernTentLureMode.INTO_TENT
                ? SOUTH_THUG_TENT_CENTRE
                : SOUTH_THUG_LURE_OUTSIDE_LEAD_TILE;
        boolean crossed = SouthernTentLureSafety.canClose(player, targetLocation,
                southernTentLureMode == SouthernTentLureMode.INTO_TENT);
        if (crossed && player.distanceTo2D(destination) <= 1)
        {
            setSouthernTentPhase(SouthernTentPhase.POSITIONING_TO_CLOSE,
                    "Turn back to close southern curtain");
            return;
        }
        if (!canContinueSouthernLureRoute(target))
        {
            return;
        }
        if (findSouthTentCurtain("Open") != null)
        {
            setSouthernTentPhase(SouthernTentPhase.OPENING_CURTAIN,
                    "Reopen southern curtain during crossing");
            return;
        }
        walkSouthernRoute(destination, false,
                southernTentLureMode == SouthernTentLureMode.INTO_TENT
                        ? "Lead Menaphite Thug a few tiles inside"
                        : "Lead extra Menaphite Thug a few tiles outside");
    }

    private void positionToCloseSouthernCurtain()
    {
        Rs2NpcModel target = southernTentLureTarget();
        WorldPoint player = Rs2Player.getWorldLocation();
        WorldPoint targetLocation = target == null ? null : target.getWorldLocation();
        if (target == null || player == null || targetLocation == null)
        {
            restartSouthernTentAssessment("Lured target disappeared before closing curtain");
            return;
        }

        boolean targetOnRequiredSide = SouthernTentLureSafety.canClose(player, targetLocation,
                southernTentLureMode == SouthernTentLureMode.INTO_TENT);
        if (!targetOnRequiredSide)
        {
            setSouthernTentPhase(SouthernTentPhase.LEADING_THROUGH_CURTAIN,
                    "Finish crossing before approaching curtain");
            return;
        }

        WorldPoint closePosition = southernTentLureMode == SouthernTentLureMode.INTO_TENT
                ? SOUTH_THUG_WINE_DOOR_INSIDE_TILE
                : SOUTH_THUG_WINE_DOOR_OUTSIDE_TILE;
        if (player.equals(closePosition))
        {
            setSouthernTentPhase(SouthernTentPhase.CLOSING_CURTAIN,
                    "Close southern curtain after Lure");
            return;
        }
        walkSouthernRoute(closePosition,
                southernTentLureMode == SouthernTentLureMode.OUT_OF_TENT,
                "Turn back toward southern curtain");
    }

    private void closeSouthernCurtainAfterLure()
    {
        Rs2NpcModel target = southernTentLureTarget();
        // A dispatched object click can place the player on the threshold. Confirm its result first.
        if (findSouthTentCurtain("Open") == null && southernCurtainActionPending())
        {
            nextAction = "Wait for southern curtain closure";
            return;
        }
        if (target == null || !SouthernTentLureSafety.onDestinationSide(
                target.getWorldLocation(), southernTentLureMode == SouthernTentLureMode.INTO_TENT))
        {
            setSouthernTentPhase(SouthernTentPhase.LEADING_THROUGH_CURTAIN,
                    "Lured thug moved back; finish curtain crossing");
            return;
        }
        if (findSouthTentCurtain("Open") != null)
        {
            clearSouthernCurtainAction();
            southernCrossingStartedAt = 0;
            if (southernTentLureMode == SouthernTentLureMode.OUT_OF_TENT)
            {
                lureReleaseClearSince = 0;
                pubStairActionAt = 0;
                setSouthernTentPhase(SouthernTentPhase.CLIMBING_PUB_STAIRS,
                        "Climb pub stairs to release evicted thug");
            }
            else
            {
                finishSouthernLureIntoTent();
            }
            return;
        }

        Rs2TileObjectModel openCurtain = findSouthTentCurtain("Close");
        if (openCurtain != null)
        {
            if (!SouthernTentLureSafety.onDestinationSide(Rs2Player.getWorldLocation(),
                    southernTentLureMode == SouthernTentLureMode.INTO_TENT))
            {
                setSouthernTentPhase(SouthernTentPhase.POSITIONING_TO_CLOSE,
                        "Return to closing side of southern curtain");
                return;
            }
            interactWithSouthTentCurtain(openCurtain, "Close", "Close southern curtain after Lure");
            return;
        }
        nextAction = "Confirm southern curtain closed";
    }

    private void finishSouthernLureIntoTent()
    {
        restoreAutoRunAfterLure();
        Rs2NpcModel target = southernTentLureTarget();
        if (target != null && isInsideSouthTent(target.getWorldLocation()))
        {
            targetIndex = target.getIndex();
        }
        log.info("Menaphite Thug secured inside southern tent: targetIndex={}", targetIndex);
        southernTentLureMode = SouthernTentLureMode.NONE;
        southernTentLureTargetIndex = -1;
        southernTentLureNpc = null;
        setSouthernTentPhase(SouthernTentPhase.ASSESSING,
                "Verify isolated Menaphite Thug");
    }

    private void waitForEvictedTargetRelease()
    {
        Rs2NpcModel target = southernTentLureTarget();
        long now = System.currentTimeMillis();
        if (target != null && !SouthernTentLureSafety.onDestinationSide(target.getWorldLocation(), false))
        {
            restartSouthernTentAssessment("Evicted thug returned before release was confirmed");
            return;
        }
        if (findSouthTentCurtain("Open") == null)
        {
            lureReleaseClearSince = 0;
            interactWithSouthTentCurtain(findSouthTentCurtain("Close"), "Close",
                    "Keep southern curtain closed while releasing extra thug");
            return;
        }
        if (target == null || !target.isInteractingWithPlayer())
        {
            if (lureReleaseClearSince == 0)
            {
                lureReleaseClearSince = now;
            }
            if (now - lureReleaseClearSince >= LURE_DIALOGUE_QUIET_MS)
            {
                log.info("Evicted Menaphite Thug released outside southern tent: targetIndex={}",
                        southernTentLureTargetIndex);
                restoreAutoRunAfterLure();
                southernTentLureMode = SouthernTentLureMode.NONE;
                southernTentLureTargetIndex = -1;
                southernTentLureNpc = null;
                setSouthernTentPhase(SouthernTentPhase.RETURNING_TO_TENT,
                        "Return inside to retained Menaphite Thug");
            }
            return;
        }

        lureReleaseClearSince = 0;
        nextAction = "Wait outside for evicted thug to stop following";
    }

    private void resetEvictedFollowerAtPub()
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null)
        {
            return;
        }
        if (southernTentPhase == SouthernTentPhase.CLIMBING_PUB_STAIRS && player.getPlane() == 1)
        {
            pubStairActionAt = 0;
            setSouthernTentPhase(SouthernTentPhase.WAITING_UPSTAIRS,
                    "Wait upstairs for evicted thug to stop following");
            return;
        }
        if (southernTentPhase == SouthernTentPhase.WAITING_UPSTAIRS)
        {
            if (player.getPlane() != 1)
            {
                fail("Unexpected floor during pub follower reset");
                return;
            }
            if (elapsedInSouthernTentPhase() >= 3_000)
            {
                pubStairActionAt = 0;
                setSouthernTentPhase(SouthernTentPhase.DESCENDING_PUB_STAIRS,
                        "Climb down pub stairs after follower reset");
            }
            return;
        }
        if (southernTentPhase == SouthernTentPhase.DESCENDING_PUB_STAIRS && player.getPlane() == 0)
        {
            pubStairActionAt = 0;
            lureReleaseClearSince = 0;
            setSouthernTentPhase(SouthernTentPhase.WAITING_FOR_RELEASE,
                    "Verify evicted thug released after pub stairs");
            return;
        }
        int plane = southernTentPhase == SouthernTentPhase.CLIMBING_PUB_STAIRS ? 0 : 1;
        if (player.getPlane() != plane)
        {
            fail("Unexpected floor approaching pub staircase");
            return;
        }
        long now = System.currentTimeMillis();
        if (pubStairActionAt > 0 && now - pubStairActionAt < 3_000)
        {
            return;
        }
        WorldPoint stairTile = new WorldPoint(3353, 2958, plane);
        if (player.distanceTo2D(stairTile) > 2)
        {
            walkSouthernRoute(stairTile, false, "Approach pub staircase before clicking");
            return;
        }
        Rs2TileObjectModel stairs = Microbot.getRs2TileObjectCache().query()
                .withId(plane == 0 ? 6242 : 6243)
                .where(object -> stairTile.equals(object.getWorldLocation()))
                .first();
        String action = plane == 0 ? "Climb-up" : "Climb-down";
        if (stairs != null && hasObjectAction(stairs, action))
        {
            if (clickVerifiedPubStaircase(stairs, action))
            {
                pubStairActionAt = now;
                log.info("Pub follower-reset staircase dispatched: action={} player={}", action, player);
            }
        }
        else if (player.distanceTo2D(stairTile) > 2)
        {
            walkSouthernRoute(stairTile, false, "Approach pub staircase for follower reset");
        }
        else
        {
            nextAction = "Wait for exact pub staircase action";
        }
    }

    private boolean clickVerifiedPubStaircase(Rs2TileObjectModel stairs, String action)
    {
        Shape hull = Microbot.getClientThread().runOnClientThreadOptional(stairs::getClickbox).orElse(null);
        if (hull == null || Microbot.getClient().isMenuOpen())
        {
            if (hull == null && System.currentTimeMillis() - pubStairDiagnosticAt >= 2_000)
            {
                pubStairDiagnosticAt = System.currentTimeMillis();
                Rs2Camera.turnTo(stairs);
                log.info("Pub staircase not visible; facing exact staircase {}", stairs.getWorldLocation());
            }
            nextAction = "Wait for visible pub staircase";
            return false;
        }
        Rectangle bounds = hull.getBounds();
        int centreX = (int) bounds.getCenterX();
        int centreY = (int) bounds.getCenterY();
        Point point = hull.contains(centreX, centreY) && centreX > 0 && centreY > 0
                && centreX < Microbot.getClient().getCanvasWidth()
                && centreY < Microbot.getClient().getCanvasHeight()
                ? new Point(centreX, centreY) : null;
        for (int y = bounds.y; y < bounds.y + bounds.height && point == null; y++)
        {
            for (int x = bounds.x; x < bounds.x + bounds.width; x++)
            {
                if (hull.contains(x, y) && x > 0 && y > 0
                        && x < Microbot.getClient().getCanvasWidth()
                        && y < Microbot.getClient().getCanvasHeight())
                {
                    point = new Point(x, y);
                    break;
                }
            }
        }
        if (point == null)
        {
            if (System.currentTimeMillis() - pubStairDiagnosticAt >= 2_000)
            {
                pubStairDiagnosticAt = System.currentTimeMillis();
                Rs2Camera.turnTo(stairs);
                log.info("Pub staircase outside viewport; facing exact staircase {}", stairs.getWorldLocation());
            }
            nextAction = "Pub staircase is outside viewport";
            return false;
        }
        Microbot.naturalMouse.moveTo(point.getX(), point.getY());
        boolean verified = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            MenuEntry[] entries = Microbot.getClient().getMenuEntries();
            Point cursor = Microbot.getClient().getMouseCanvasPosition();
            Shape liveHull = stairs.getClickbox();
            if (entries == null || entries.length == 0 || cursor == null || liveHull == null
                    || !liveHull.contains(cursor.getX(), cursor.getY()))
            {
                return false;
            }
            MenuEntry top = entries[entries.length - 1];
            WorldPoint menuTile = stairs.getWorldView() == null ? null : WorldPoint.fromScene(
                    stairs.getWorldView(), top.getParam0(), top.getParam1(), stairs.getPlane());
            boolean matches = stairs.getWorldLocation().equals(menuTile);
            if (System.currentTimeMillis() - pubStairDiagnosticAt >= 2_000)
            {
                pubStairDiagnosticAt = System.currentTimeMillis();
                log.info("Pub staircase menu check: option={} id={} type={} menuTile={} expected={} originMatches={}",
                        top.getOption(), top.getIdentifier(), top.getType(), menuTile,
                        stairs.getWorldLocation(), matches);
            }
            return action.equalsIgnoreCase(top.getOption())
                    && top.getIdentifier() == stairs.getId()
                    && top.getType() == net.runelite.api.MenuAction.GAME_OBJECT_FIRST_OPTION
                    && matches
                    && top.getWorldViewId() == stairs.getWorldView().getId();
        }).orElse(false);
        if (!verified)
        {
            nextAction = "Wait for verified pub staircase left-click option";
            return false;
        }
        Microbot.getMouse().click();
        return true;
    }

    private void transitSouthernTent(boolean entering)
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null)
        {
            nextAction = "Wait for southern curtain transit location";
            return;
        }

        boolean inside = isInsideSouthTent(player);
        Rs2TileObjectModel closedCurtain = findSouthTentCurtain("Open");
        Rs2TileObjectModel openCurtain = findSouthTentCurtain("Close");
        if (entering)
        {
            if (inside)
            {
                if (closedCurtain != null)
                {
                    setSouthernTentPhase(SouthernTentPhase.ASSESSING,
                            "Reassess secured southern tent");
                }
                else if (openCurtain != null)
                {
                    if (player.distanceTo2D(SOUTH_THUG_WINE_DOOR_INSIDE_TILE) > 2)
                    {
                        walkSouthernRoute(SOUTH_THUG_WINE_DOOR_INSIDE_TILE, true,
                                "Approach southern curtain from inside");
                    }
                    else
                    {
                        interactWithSouthTentCurtain(openCurtain, "Close",
                                "Close southern curtain behind player");
                    }
                }
                else
                {
                    nextAction = "Wait for southern curtain after entering";
                }
                return;
            }

            if (player.distanceTo2D(SOUTH_THUG_WINE_DOOR_OUTSIDE_TILE) > 2)
            {
                walkSouthernRoute(SOUTH_THUG_WINE_DOOR_OUTSIDE_TILE, true,
                        "Approach southern curtain");
                return;
            }

            if (openCurtain != null)
            {
                walkSouthernRoute(SOUTH_THUG_TENT_CENTRE, true,
                        "Step inside southern tent");
            }
            else if (closedCurtain != null)
            {
                interactWithSouthTentCurtain(closedCurtain, "Open",
                        "Open southern curtain to enter");
            }
            else
            {
                nextAction = "Wait for southern curtain to enter";
            }
            return;
        }

        if (inside && player.distanceTo2D(SOUTH_THUG_WINE_DOOR_INSIDE_TILE) > 2)
        {
            walkSouthernRoute(SOUTH_THUG_WINE_DOOR_INSIDE_TILE, true,
                    "Approach southern curtain from inside");
            return;
        }

        if (!inside)
        {
            if (openCurtain != null)
            {
                interactWithSouthTentCurtain(openCurtain, "Close",
                        "Close empty southern tent before searching");
            }
            else if (closedCurtain != null)
            {
                setSouthernTentPhase(SouthernTentPhase.SEARCHING_TARGET,
                        "Find a Menaphite Thug to lure");
            }
            else
            {
                nextAction = "Wait for southern curtain after leaving";
            }
            return;
        }

        if (openCurtain != null)
        {
            walkSouthernRoute(SOUTH_THUG_LURE_SEARCH_TILE, true,
                    "Step outside empty southern tent");
        }
        else if (closedCurtain != null)
        {
            interactWithSouthTentCurtain(closedCurtain, "Open",
                    "Open southern curtain to search outside");
        }
        else
        {
            nextAction = "Wait for southern curtain to leave";
        }
    }

    private void walkSouthernRoute(WorldPoint destination, boolean run, String action)
    {
        long now = System.currentTimeMillis();
        if (now < nextSouthernTentActionAt || Rs2Player.isMoving())
        {
            return;
        }
        if (openSouthernInnerCurtainIfNeeded(destination))
        {
            return;
        }
        if (!run)
        {
            Rs2Player.toggleRunEnergy(false);
        }
        boolean dispatched = Rs2Walker.walkFastCanvas(destination, run);
        if (!dispatched && run)
        {
            // The web walker may re-enable run and outrun the follower.
            dispatched = Rs2Walker.walkTo(destination, 0);
        }
        lastInteractionAt = now;
        nextSouthernTentActionAt = now + randomBetween(550, 851);
        nextAction = action;
        log.debug("Southern tent walk dispatched={} destination={} run={} phase={}",
                dispatched, destination, run, southernTentPhase);
    }

    private boolean openSouthernInnerCurtainIfNeeded(WorldPoint destination)
    {
        if (!SouthernTentLureSafety.crossesInnerCurtain(Rs2Player.getWorldLocation(), destination))
        {
            return false;
        }
        Rs2TileObjectModel innerCurtain = Microbot.getRs2TileObjectCache().query()
                .withId(CLOSED_CURTAIN_ID)
                .where(object -> SOUTH_THUG_INNER_CURTAIN_TILE.equals(object.getWorldLocation()))
                .first();
        if (innerCurtain == null)
        {
            return false;
        }
        interactWithSouthTentCurtain(innerCurtain, "Open", "Open inner curtain for rear-room route");
        return true;
    }

    private boolean interactWithSouthTentCurtain(
            Rs2TileObjectModel curtain, String action, String description)
    {
        long now = System.currentTimeMillis();
        if (southernCurtainActionPending())
        {
            nextAction = "Wait for southern curtain action to finish";
            return false;
        }
        if (curtain == null || now < nextSouthernTentActionAt
                || !readyForInteraction(DOOR_INTERACTION_DELAY_MS))
        {
            return false;
        }
        if (!curtain.click(action))
        {
            nextSouthernTentActionAt = now + randomBetween(500, 801);
            return false;
        }
        lastInteractionAt = now;
        pendingSouthernCurtain = curtain.getWorldLocation();
        pendingSouthernCurtainAction = action;
        pendingSouthernCurtainAt = now;
        nextSouthernTentActionAt = now + randomBetween(650, 951);
        nextAction = description;
        log.info("Southern curtain action dispatched: action={} phase={} player={}",
                action, southernTentPhase, Rs2Player.getWorldLocation());
        return true;
    }

    private boolean southernCurtainActionPending()
    {
        if (pendingSouthernCurtain == null)
        {
            return false;
        }
        if (System.currentTimeMillis() - pendingSouthernCurtainAt >= 3_000)
        {
            clearSouthernCurtainAction();
            return false;
        }
        boolean unchanged = Microbot.getRs2TileObjectCache().query()
                .where(object -> pendingSouthernCurtain.equals(object.getWorldLocation())
                        && hasObjectAction(object, pendingSouthernCurtainAction))
                .nearestOnClientThread() != null;
        if (!unchanged)
        {
            clearSouthernCurtainAction();
            return false;
        }
        return true;
    }

    private void clearSouthernCurtainAction()
    {
        pendingSouthernCurtain = null;
        pendingSouthernCurtainAction = null;
        pendingSouthernCurtainAt = 0;
    }

    private Rs2NpcModel chooseRetainedSouthernTarget(List<Rs2NpcModel> occupants)
    {
        for (Rs2NpcModel occupant : occupants)
        {
            if (occupant.getIndex() == targetIndex)
            {
                return occupant;
            }
        }
        return occupants.stream()
                .max(Comparator.comparingInt(npc -> npc.getWorldLocation()
                        .distanceTo2D(SOUTH_THUG_WINE_CURTAIN_TILE)))
                .orElse(null);
    }

    private Rs2NpcModel chooseExtraSouthernTarget(List<Rs2NpcModel> occupants, int retainedIndex)
    {
        return occupants.stream()
                .filter(npc -> npc.getIndex() != retainedIndex)
                .filter(npc -> npc.getInteracting() == null || npc.isInteractingWithPlayer())
                .min(Comparator
                        .comparingInt((Rs2NpcModel npc) ->
                                npc.getAnimation() == AnimationID.HUMAN_UNCONSCIOUS ? 1 : 0)
                        .thenComparingInt(npc -> npc.getWorldLocation()
                                .distanceTo2D(SOUTH_THUG_WINE_CURTAIN_TILE)))
                .orElse(null);
    }

    private Rs2NpcModel findOutsideSouthernLureTarget()
    {
        return Microbot.getRs2NpcCache().query()
                .where(npc -> isSelectedTargetType(npc)
                        && npc.getWorldLocation() != null
                        && SOUTH_THUG_LURE_SEARCH_AREA.contains(npc.getWorldLocation())
                        && !isInsideSouthTent(npc.getWorldLocation())
                        && npc.getAnimation() != AnimationID.HUMAN_UNCONSCIOUS
                        && npc.getInteracting() == null
                        && npc.hasLineOfSight())
                .toListOnClientThread().stream()
                .min(Comparator.comparingInt(Rs2NpcModel::getDistanceFromPlayer))
                .orElse(null);
    }

    private List<Rs2NpcModel> southernTentTargets()
    {
        return Microbot.getRs2NpcCache().query()
                .where(npc -> isSelectedTargetType(npc)
                        && isInsideSouthTent(npc.getWorldLocation()))
                .toListOnClientThread();
    }

    private Rs2NpcModel southernTentLureTarget()
    {
        if (southernTentLureTargetIndex < 0)
        {
            return null;
        }
        return Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getIndex() == southernTentLureTargetIndex
                        && npc.getNpc() == southernTentLureNpc
                        && isSelectedTargetType(npc))
                .nearestOnClientThread();
    }

    private void restartSouthernTentAssessment(String reason)
    {
        southernCrossingStartedAt = 0;
        clearSouthernCurtainAction();
        restoreAutoRunAfterLure();
        log.warn("Southern tent preparation recovery: {}", reason);
        southernTentLureMode = SouthernTentLureMode.NONE;
        southernTentLureTargetIndex = -1;
        southernTentLureNpc = null;
        lureContinueClicks = 0;
        lureFollowConfirmations = 0;
        lureDialogueLastSeenAt = 0;
        lureFollowNextCheckAt = 0;
        lureFollowLostSince = 0;
        lureReleaseClearSince = 0;
        setSouthernTentPhase(SouthernTentPhase.ASSESSING, "Recover: " + reason);
    }

    private void setSouthernTentPhase(SouthernTentPhase phase, String action)
    {
        if (phase == SouthernTentPhase.APPROACHING_TARGET)
        {
            // Re-luring abandons the old crossing; its timer must not consume the next attempt.
            southernCrossingStartedAt = 0;
            lastSouthernLureWaitLogAt = 0;
            clearSouthernCurtainAction();
        }
        if (phase == SouthernTentPhase.MOVING_TO_CURTAIN && southernCrossingStartedAt == 0)
        {
            southernCrossingStartedAt = System.currentTimeMillis();
        }
        if (southernTentPhase != phase)
        {
            log.info("Southern tent phase {} -> {} ({})", southernTentPhase, phase, action);
            southernTentPhase = phase;
            southernTentPhaseEnteredAt = System.currentTimeMillis();
        }
        nextAction = action;
        Microbot.status = "Blackjack: " + action;
    }

    private long elapsedInSouthernTentPhase()
    {
        return System.currentTimeMillis() - southernTentPhaseEnteredAt;
    }

    private void resetSouthernTentPreparation()
    {
        pubStairActionAt = 0;
        southernCrossingStartedAt = 0;
        clearSouthernCurtainAction();
        restoreAutoRunAfterLure();
        southernTentLureTargetIndex = -1;
        southernTentLureNpc = null;
        southernTentLureMode = SouthernTentLureMode.NONE;
        southernTentPhase = SouthernTentPhase.ASSESSING;
        southernTentPhaseEnteredAt = System.currentTimeMillis();
        nextSouthernTentActionAt = 0;
        lureDialogueLastSeenAt = 0;
        lureFollowNextCheckAt = 0;
        lureFollowLostSince = 0;
        lureReleaseClearSince = 0;
        lureAttempts = 0;
        lureContinueClicks = 0;
        lureFollowConfirmations = 0;
    }

    private void suspendAutoRunForLure()
    {
        if (autoRunBeforeLure == null)
        {
            autoRunBeforeLure = Microbot.enableAutoRunOn;
        }
        Microbot.enableAutoRunOn = false;
    }

    private void restoreAutoRunAfterLure()
    {
        if (autoRunBeforeLure != null)
        {
            Microbot.enableAutoRunOn = autoRunBeforeLure;
            autoRunBeforeLure = null;
            // Script.run persists this flag while the lure is active, so restore both copies.
            if (Microbot.getConfigManager() != null)
            {
                Microbot.getConfigManager().setConfiguration(MicrobotConfig.configGroup,
                        MicrobotConfig.keyEnableAutoRunOn, Microbot.enableAutoRunOn);
            }
        }
    }

    private void ensureRunAfterPreparation()
    {
        // Restore the actual run toggle, not just the saved auto-run preference.
        if (autoRunBeforeLure != null || !Microbot.isLoggedIn()
                || Microbot.getClient().isMenuOpen() || Rs2Player.isRunEnabled())
        {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRunRestoreAttemptAt < 2_000)
        {
            return;
        }
        lastRunRestoreAttemptAt = now;
        if (Rs2Player.toggleRunEnergy(true))
        {
            log.info("Run re-enable requested after lure/restock: state={}", state);
        }
    }

    private void resetActiveBlackjackCycleForTentPreparation()
    {
        knockoutResult = KnockoutResult.NONE;
        knockoutClickIssuedAt = 0;
        knockoutBurstReleaseAt = 0;
        knockoutFallbackReleased = false;
        pickpocketBurstStartedAt = 0;
        nextPickpocketClickAt = 0;
        lastPickpocketClickAt = 0;
        nextKnockoutArmedAt = 0;
        secondPickpocketInteractionSeen = false;
        secondPickpocketInteractionComplete = false;
        pendingInventoryFullAt = 0;
        picksThisKnockout = 0;
        burstClickPoint = null;
        clearStandingClickAnchor();
    }

    private void acquireTarget()
    {
        lastBlackjackFeedbackAt = System.currentTimeMillis();
        ensureRunAfterPreparation();
        if (selectedTarget() == BlackjackTarget.MENAPHITE_THUG)
        {
            List<Rs2NpcModel> occupants = southernTentTargets();
            if (!isInsideSouthTent(Rs2Player.getWorldLocation())
                    || occupants.size() != 1 || isSouthTentCurtainOpen())
            {
                beginSouthernTentPreparation(occupants.isEmpty()
                        ? "Lure a Menaphite Thug into the southern tent"
                        : occupants.size() > 1
                        ? "Remove extra Menaphite Thug from southern tent"
                        : "Secure southern tent curtain");
                return;
            }
        }

        if (selectedTarget() != BlackjackTarget.MENAPHITE_THUG)
        {
            if (!isInsideHouse())
            {
                transition(BlackjackState.RETURNING_TO_HOUSE, "Return inside marked Bandit house");
                return;
            }
            Rs2TileObjectModel openDoor = findWineDoor("Close");
            if (openDoor != null)
            {
                long now = System.currentTimeMillis();
                if (now - banditDoorCloseAttemptAt >= 3_000
                        && interactWithWineDoor(openDoor, "Close", "Secure Bandit house before blackjacking"))
                {
                    banditDoorCloseAttemptAt = now;
                }
                return;
            }
            if (findWineDoor("Open") == null)
            {
                waitForWineDoor("Resolve Bandit house door before blackjacking");
                return;
            }
            banditDoorCloseAttemptAt = 0;
        }

        Rs2NpcModel target = findEligibleTarget();
        if (target == null)
        {
            targetIndex = -1;
            targetDescription = expectedTargetDescription();
            nextAction = "Waiting for selected target";
            if (elapsedInState() > 30_000)
            {
                fail("No eligible target in marked house");
            }
            return;
        }

        targetIndex = target.getIndex();
        targetDescription = target.getName() + " (level " + target.getCombatLevel() + ")";
        if (recoverHiddenHouseTarget(target))
        {
            return;
        }
        maintainTargetCamera(target);
        transition(BlackjackState.KNOCKING_OUT, "Knock-Out target");
    }

    private void knockOutTarget()
    {
        Rs2NpcModel target = currentTarget();
        if (target == null)
        {
            transition(BlackjackState.FINDING_TARGET, "Refresh target");
            return;
        }

        if (knockoutResult == KnockoutResult.SUCCESS
                && picksThisKnockout < 2
                && nextKnockoutArmedAt == 0
                && target.getAnimation() == AnimationID.HUMAN_UNCONSCIOUS)
        {
            pickpocketBurstStartedAt = System.currentTimeMillis();
            transition(BlackjackState.PICKPOCKETING, "Continue current unconscious pickpocket burst");
            return;
        }

        maintainTargetCamera(target);

        if (System.currentTimeMillis() < knockoutAttemptReadyAt)
        {
            nextAction = "Pause before Knock-Out attempt";
            return;
        }

        if (nextKnockoutArmedAt != 0)
        {
            observeSecondPickpocketInteraction(target);
            if (!secondPickpocketInteractionSeen)
            {
                if (picksThisKnockout < 2
                        && System.currentTimeMillis() - nextKnockoutArmedAt
                        >= SECOND_PICKPOCKET_INTERACTION_TIMEOUT_MS)
                {
                    cancelSecondPickpocketPrearm("second pickpocket interaction was not observed");
                    transition(BlackjackState.PICKPOCKETING, "Resume unresolved pickpocket burst");
                    return;
                }
                if (picksThisKnockout >= 2 && allowSecondPickpocketInteractionFallback(target))
                {
                    // The confirmed second pickpocket can safely use the bounded interaction fallback.
                }
                else
                {
                    continuePrearmedPickpocketClicks(target);
                    nextAction = "Wait for second pickpocket interaction";
                    return;
                }
            }
            if (secondPickpocketInteractionSeen && !secondPickpocketInteractionComplete)
            {
                if (!continuePrearmedPickpocketClicks(target))
                {
                    prepositionPrearmedKnockoutCursor(target);
                }
                return;
            }
            if (picksThisKnockout >= 2
                    && secondPickpocketInteractionComplete
                    && isHumanizerEventDue(System.currentTimeMillis()))
            {
                burstClickPoint = null;
                transition(BlackjackState.FINDING_TARGET, "Yield completed burst to humanizer");
                return;
            }
        }

        Point anchor = targetAnchor(target, burstClickPoint);
        if (anchor == null)
        {
            nextAction = "Wait for safe NPC click point";
            handleKnockoutAimFailure(target, "no viewport-safe NPC anchor");
            return;
        }

        if (readyForInteraction(80))
        {
            if (!moveAndLeftClickTargetOption(target, anchor, KNOCKOUT_ACTION))
            {
                handleKnockoutAimFailure(target, "cursor or left-click option validation failed");
                return;
            }
            long now = System.currentTimeMillis();
            burstClickPoint = Microbot.getClient().getMouseCanvasPosition();
            lastInteractionAt = now;
            recordKnockoutDispatch(now);
        }
    }

    private void runContinuousBlackjackLoop()
    {
        Rs2NpcModel target = currentTarget();
        if (target == null)
        {
            lastContinuousAction = null;
            transition(BlackjackState.FINDING_TARGET, "Refresh target");
            return;
        }

        if (recoverHiddenHouseTarget(target))
        {
            return;
        }
        maintainTargetCamera(target);
        long now = System.currentTimeMillis();
        if (lastBlackjackFeedbackAt == 0)
        {
            lastBlackjackFeedbackAt = now;
        }
        if (continuousFeedbackTimedOut(now, lastBlackjackFeedbackAt, lastContinuousRecoveryAt))
        {
            lastContinuousRecoveryAt = now;
            burstClickPoint = null;
            clearStandingClickAnchor();
            faceKnockoutTarget(target);
            Rs2Camera.setPitch(TARGET_CAMERA_PITCH);
            if (targetCameraZoom >= 0)
            {
                Rs2Camera.setZoom(effectiveTargetZoom());
            }
            nextPickpocketClickAt = now + 80;
            log.warn("Continuous blackjack feedback stalled; recentering camera/cursor: ageMs={} targetIndex={}",
                    now - lastBlackjackFeedbackAt, target.getIndex());
            return;
        }
        if (now < nextPickpocketClickAt)
        {
            return;
        }

        String action = clickCurrentBlackjackOption(target);
        if (action == null)
        {
            handleKnockoutAimFailure(target, "continuous hull/menu validation failed");
            nextPickpocketClickAt = now + 35;
            return;
        }

        now = System.currentTimeMillis();
        resetKnockoutAimRecovery();
        lastInteractionAt = now;
        nextPickpocketClickAt = now + randomPickpocketDelay(false);

        if (KNOCKOUT_ACTION.equals(action))
        {
            if (!KNOCKOUT_ACTION.equals(lastContinuousAction))
            {
                recordKnockoutDispatch(now);
            }
            else
            {
                knockoutClickIssuedAt = now;
                knockoutResult = KnockoutResult.PENDING;
                knockoutFailedAt = 0;
                knockoutRetryAt = 0;
                lastOutcome = "Continuous Knock-Out click";
                nextAction = "Continue Knock-Out clicks";
            }
        }
        else
        {
            if (knockoutResult == KnockoutResult.PENDING)
            {
                confirmKnockout("entry swap");
            }
            if (pickpocketBurstStartedAt == 0)
            {
                pickpocketBurstStartedAt = now;
            }
            lastPickpocketClickAt = now;
            pickpocketClicks++;
            nextAction = "Continue Pickpocket clicks";
        }
        lastContinuousAction = action;
    }

    private String clickCurrentBlackjackOption(Rs2NpcModel target)
    {
        if (Microbot.getClient().isMenuOpen() || Microbot.naturalMouse == null)
        {
            return null;
        }

        Point anchor = burstWanderAnchor(target, burstClickPoint);
        if (anchor == null)
        {
            burstClickPoint = null;
            nextAction = "Reacquire safe NPC click point";
            return null;
        }

        Point current = Microbot.getClient().getMouseCanvasPosition();
        if (current == null
                || Math.abs(current.getX() - anchor.getX()) > 2
                || Math.abs(current.getY() - anchor.getY()) > 2)
        {
            Microbot.naturalMouse.moveTo(anchor.getX(), anchor.getY());
            current = Microbot.getClient().getMouseCanvasPosition();
            if (current == null)
            {
                current = anchor;
            }
        }

        if (!isInsideTargetHull(target, current))
        {
            burstClickPoint = null;
            nextAction = "Reacquire safe NPC click point";
            return null;
        }

        String action = topBlackjackOption(target);
        if (action == null)
        {
            noteStandingAnchorMenuMiss(target);
            burstClickPoint = null;
            nextAction = "Wait for Blackjack entry swap";
            return null;
        }
        standingAnchorMenuMisses = 0;

        Microbot.getMouse().click(current);
        burstClickPoint = current;
        return action;
    }

    private String topBlackjackOption(Rs2NpcModel target)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            MenuEntry[] entries = Microbot.getClient().getMenuEntries();
            if (entries == null || entries.length == 0)
            {
                return null;
            }
            MenuEntry entry = entries[entries.length - 1];
            NPC npc = entry.getNpc();
            if (npc == null || target == null || npc.getIndex() != target.getIndex())
            {
                return null;
            }
            if (KNOCKOUT_ACTION.equalsIgnoreCase(entry.getOption()))
            {
                return KNOCKOUT_ACTION;
            }
            if (PICKPOCKET_ACTION.equalsIgnoreCase(entry.getOption()))
            {
                return PICKPOCKET_ACTION;
            }
            return null;
        }).orElse(null);
    }

    private void recordKnockoutDispatch(long now)
    {
        resetKnockoutAimRecovery();
        knockoutClickIssuedAt = now;
        knockoutBurstReleaseAt = now + randomBetween(
                KNOCKOUT_DISPATCH_FALLBACK_MIN_MS,
                KNOCKOUT_DISPATCH_FALLBACK_MAX_MS);
        knockoutFallbackReleased = false;
        pickpocketBurstStartedAt = 0;
        nextPickpocketClickAt = knockoutBurstReleaseAt;
        lastPickpocketClickAt = 0;
        pendingInventoryFullAt = 0;
        picksThisKnockout = 0;
        nextKnockoutArmedAt = 0;
        knockoutFailedAt = 0;
        knockoutRetryAt = 0;
        secondPickpocketInteractionSeen = false;
        secondPickpocketInteractionComplete = false;
        knockoutResult = KnockoutResult.PENDING;
        curtainCameraAdjusted = false;
        lastOutcome = "Left-click Knock-Out dispatched";
        log.info("Left-click Knock-Out dispatched: dispatchAtMs={} fallbackInMs={}",
                knockoutClickIssuedAt, knockoutBurstReleaseAt - knockoutClickIssuedAt);
        transition(BlackjackState.PICKPOCKETING, "Spam pickpocket 1/2");
    }

    private boolean continuePrearmedPickpocketClicks(Rs2NpcModel target)
    {
        long now = System.currentTimeMillis();
        if (now < nextPickpocketClickAt || !clickAnchoredTarget(target))
        {
            return false;
        }

        now = System.currentTimeMillis();
        lastInteractionAt = now;
        lastPickpocketClickAt = now;
        nextPickpocketClickAt = now + randomPickpocketDelay(false);
        pickpocketClicks++;
        nextAction = "Continue pickpocket click cadence";
        return true;
    }

    private void runPickpocketBurst()
    {
        long now = System.currentTimeMillis();
        if (lastPickpocketClickAt > 0
                && now - lastPickpocketClickAt <= MISCLICK_MOVEMENT_WINDOW_MS
                && Rs2Player.isMoving())
        {
            log.info("Unexpected movement after pickpocket click; abandoning burst and re-aiming");
            lastOutcome = "Movement invalidated pickpocket anchor";
            waitForTargetClear("Unexpected movement", false);
            return;
        }

        Rs2NpcModel target = currentTarget();
        if (target == null)
        {
            transition(BlackjackState.FINDING_TARGET, "Refresh target");
            return;
        }

        if (knockoutResult == KnockoutResult.PENDING)
        {
            if (elapsedInState() >= KNOCKOUT_CONFIRM_TIMEOUT_MS)
            {
                knockoutDispatchTimeouts++;
                lastOutcome = "Knock-Out command not confirmed";
                log.warn("Knock-Out command was not confirmed: targetAnimation={} playerInteracting={}",
                        target.getAnimation(), isPlayerInteractingWithTarget(target));
                transition(BlackjackState.KNOCKING_OUT, "Retry unconfirmed Knock-Out");
                return;
            }
            if (now < knockoutBurstReleaseAt)
            {
                nextAction = "Confirm Knock-Out dispatch";
                return;
            }
            if (!knockoutFallbackReleased)
            {
                knockoutFallbackReleased = true;
                if (pickpocketBurstStartedAt == 0)
                {
                    pickpocketBurstStartedAt = now;
                }
                log.warn("Knock-Out signal still pending after {}ms; releasing safety pickpocket burst",
                        knockoutDispatchAge(now));
            }
            nextAction = "Safety pickpocket while confirming Knock-Out";
        }

        if (knockoutResult == KnockoutResult.FAILED
                && knockoutFailedAt > 0
                && now >= knockoutRetryAt)
        {
            transition(BlackjackState.KNOCKING_OUT, "Retry failed Knock-Out");
            return;
        }

        if (picksThisKnockout >= 2)
        {
            armNextKnockout(target);
            transition(BlackjackState.KNOCKING_OUT, "Next Knock-Out armed");
            return;
        }

        if (consumeInventoryFullResetIfIdle())
        {
            return;
        }

        long burstAge = pickpocketBurstStartedAt == 0 ? 0 : now - pickpocketBurstStartedAt;
        if (burstAge >= PICKPOCKET_BURST_TIMEOUT_MS)
        {
            if (knockoutResult == KnockoutResult.SUCCESS
                    && target.getAnimation() == AnimationID.HUMAN_UNCONSCIOUS)
            {
                pickpocketBurstStartedAt = now;
                nextAction = "Finish current unconscious pickpocket burst";
                log.info("Extending pickpocket burst while target remains unconscious: confirmedPicks={}",
                        picksThisKnockout);
                return;
            }
            burstTimeouts++;
            lastOutcome = "Burst timeout: " + picksThisKnockout + "/2";
            log.warn("Pickpocket burst timed out after {} confirmed picks", picksThisKnockout);
            transition(BlackjackState.KNOCKING_OUT, "Recover burst timing");
            return;
        }

        if (System.currentTimeMillis() >= nextPickpocketClickAt && clickAnchoredTarget(target))
        {
            now = System.currentTimeMillis();
            if (pickpocketBurstStartedAt == 0)
            {
                pickpocketBurstStartedAt = now;
            }
            lastInteractionAt = now;
            lastPickpocketClickAt = now;
            nextPickpocketClickAt = now + randomPickpocketDelay(false);
            pickpocketClicks++;
            nextAction = "Confirm pickpocket " + (picksThisKnockout + 1) + "/2";
        }
    }

    private void heal()
    {
        int hitpoints = currentHitpoints();
        if (hitpoints >= config.healToPercent())
        {
            healingRequired = false;
            pendingInventoryFullAt = 0;
            burstClickPoint = null;
            transition(BlackjackState.FINDING_TARGET, "Reacquire target after healing");
            return;
        }

        if (Rs2Inventory.count(WINE_ID) == 0)
        {
            if (config.autoRestockWine())
            {
                transition(BlackjackState.RESTOCKING_WINE, "Restock jug of wine");
            }
            else
            {
                fail("Out of jug of wine");
            }
            return;
        }

        drinkWineIfReady("Heal to " + config.healToPercent() + " HP");
    }

    private void positionCombatReset()
    {
        burstClickPoint = null;
        if (runDisarmedCombatReset())
        {
            return;
        }

        WorldPoint location = Rs2Player.getWorldLocation();
        WorldPoint stagingTile = activeCombatStagingTile();
        if (stagingTile.equals(location))
        {
            if (selectedTarget() == BlackjackTarget.MENAPHITE_THUG)
            {
                combatClearSince = 0;
                transition(BlackjackState.ESCAPING_COMBAT,
                        "Move to southern tent safespot");
                return;
            }
            Rs2NpcModel target = currentTarget();
            WorldPoint targetLocation = target == null ? null : target.getWorldLocation();
            if (targetLocation != null && targetLocation.getY() > COMBAT_SAFE_TILE.getY())
            {
                log.info("Combat reset staged: player={}, target={}, safespot={}",
                        location, targetLocation, COMBAT_SAFE_TILE);
                lastOutcome = "Target north of safespot";
                transition(BlackjackState.ESCAPING_COMBAT, "Run behind the bed");
            }
            else
            {
                nextAction = "Wait for target north of safespot";
            }
            return;
        }

        if (readyForInteraction(450))
        {
            Rs2Walker.walkFastCanvas(stagingTile);
            lastInteractionAt = System.currentTimeMillis();
            nextAction = "Move to combat reset position";
        }
    }

    private boolean runDisarmedCombatReset()
    {
        long now = System.currentTimeMillis();
        switch (combatResetPhase)
        {
            case UNTRIED:
                if (!hasBlackjackEquipped())
                {
                    startCombatResetPhase(CombatResetPhase.REEQUIPPING,
                            "Re-equip blackjack before combat reset");
                    return true;
                }
                if (Rs2Inventory.emptySlotCount() == 0)
                {
                    if (!config.dropWineForDisarmedReset()
                            || healingRequired
                            || Rs2Inventory.count(WINE_ID) == 0)
                    {
                        log.info("Disarmed combat reset cannot create a safe slot: dropEnabled={} healingRequired={} wines={}; using safespot fallback",
                                config.dropWineForDisarmedReset(), healingRequired, Rs2Inventory.count(WINE_ID));
                        combatResetPhase = CombatResetPhase.FALLBACK;
                        return false;
                    }
                    if (readyForInteraction(80) && Rs2Inventory.drop(WINE_ID))
                    {
                        combatWineDropTile = Rs2Player.getWorldLocation();
                        lastInteractionAt = now;
                        startCombatResetPhase(CombatResetPhase.DROPPING_WINE,
                                "Temporarily drop one wine for blackjack slot");
                        log.info("Dropping one wine at {} for disarmed combat reset", combatWineDropTile);
                    }
                    return true;
                }
                if (readyForInteraction(80)
                        && Rs2Equipment.unEquip(EquipmentInventorySlot.WEAPON))
                {
                    lastInteractionAt = now;
                    startCombatResetPhase(CombatResetPhase.UNEQUIPPING,
                            "Unequip blackjack to interrupt combat");
                }
                return true;
            case DROPPING_WINE:
                if (Rs2Inventory.emptySlotCount() > 0)
                {
                    combatWineDropped = true;
                    if (readyForInteraction(80)
                            && Rs2Equipment.unEquip(EquipmentInventorySlot.WEAPON))
                    {
                        lastInteractionAt = now;
                        startCombatResetPhase(CombatResetPhase.UNEQUIPPING,
                                "Unequip blackjack to interrupt combat");
                    }
                    return true;
                }
                if (now - combatResetPhaseAt >= COMBAT_WINE_DROP_TIMEOUT_MS)
                {
                    log.warn("Wine did not leave inventory within {}ms; using safespot fallback",
                            COMBAT_WINE_DROP_TIMEOUT_MS);
                    combatResetPhase = CombatResetPhase.FALLBACK;
                    return false;
                }
                return true;
            case UNEQUIPPING:
                if (!hasBlackjackEquipped())
                {
                    startCombatResetPhase(CombatResetPhase.KNOCKING_OUT,
                            "Left-click Knock-Out while unarmed");
                    return true;
                }
                if (now - combatResetPhaseAt >= COMBAT_UNEQUIP_TIMEOUT_MS)
                {
                    log.warn("Blackjack did not unequip within {}ms; using safespot fallback",
                            COMBAT_UNEQUIP_TIMEOUT_MS);
                    combatResetPhase = CombatResetPhase.FALLBACK;
                    return false;
                }
                return true;
            case KNOCKING_OUT:
                return runUnarmedKnockout(now);
            case REEQUIPPING:
                return reEquipAfterCombatReset(now);
            case RECOVERING_WINE:
                return recoverCombatResetWine(now);
            case FALLBACK:
            default:
                return false;
        }
    }

    private boolean runUnarmedKnockout(long now)
    {
        if (combatResetKnockoutAt == 0)
        {
            Rs2NpcModel target = currentTarget();
            if (target != null && combatVisibilityRecoveryAllowed(now, combatResetPhaseAt)
                    && recoverHiddenHouseTarget(target, false))
            {
                nextAction = "Recover view for disarmed Knock-Out";
                return true;
            }
            Point anchor = target == null ? null : targetAnchor(target, null);
            if (target == null || anchor == null)
            {
                log.warn("No safe target hull available for disarmed combat reset; re-equipping for fallback");
                startCombatResetPhase(CombatResetPhase.REEQUIPPING,
                        "Re-equip blackjack for safespot fallback");
                return true;
            }
            targetIndex = target.getIndex();
            targetDescription = target.getName() + " (level " + target.getCombatLevel() + ")";
            if (!readyForInteraction(80)
                    || !moveAndLeftClickTargetOption(target, anchor, KNOCKOUT_ACTION))
            {
                return true;
            }
            combatResetKnockoutAt = now;
            lastInteractionAt = now;
            lastOutcome = "Disarmed Knock-Out dispatched";
            log.info("Disarmed combat-reset Knock-Out dispatched at targetIndex={}", target.getIndex());
            nextAction = "Confirm disarmed Knock-Out";
            return true;
        }

        if (!isNpcTargetingPlayer())
        {
            if (combatResetClearSince == 0)
            {
                combatResetClearSince = now;
                nextAction = "Confirm disarmed Knock-Out cleared combat";
                return true;
            }
            if (now - combatResetClearSince < COMBAT_CLEAR_SETTLE_MS)
            {
                return true;
            }
            combatResetKnockoutSucceeded = true;
            startCombatResetPhase(CombatResetPhase.REEQUIPPING,
                    "Re-equip blackjack after combat cleared");
            return true;
        }
        combatResetClearSince = 0;
        if (now - combatResetKnockoutAt >= COMBAT_DISARM_KNOCKOUT_TIMEOUT_MS)
        {
            log.info("Disarmed Knock-Out did not clear combat within {}ms; re-equipping for safespot fallback",
                    COMBAT_DISARM_KNOCKOUT_TIMEOUT_MS);
            startCombatResetPhase(CombatResetPhase.REEQUIPPING,
                    "Re-equip blackjack for safespot fallback");
        }
        return true;
    }

    private boolean reEquipAfterCombatReset(long now)
    {
        if (hasBlackjackEquipped())
        {
            if (combatWineDropped)
            {
                startCombatResetPhase(CombatResetPhase.RECOVERING_WINE,
                        "Recover temporarily dropped wine");
                return true;
            }
            return finishCombatResetReequip(now);
        }

        if (now - combatResetPhaseAt >= COMBAT_REEQUIP_TIMEOUT_MS)
        {
            fail("Unable to re-equip blackjack after combat reset");
            return true;
        }
        if (readyForInteraction(80)
                && Rs2Inventory.interact(item -> item.getName() != null
                && item.getName().toLowerCase(Locale.ROOT).contains("blackjack"), "Wield"))
        {
            lastInteractionAt = now;
            nextAction = "Re-equip blackjack";
        }
        return true;
    }

    private boolean recoverCombatResetWine(long now)
    {
        Rs2TileItemModel wine = findDroppedCombatWine();
        if (Rs2Inventory.emptySlotCount() == 0 && wine == null)
        {
            combatWineDropped = false;
            combatWineDropTile = null;
            lastOutcome = "Recovered combat-reset wine";
            log.info("Recovered temporarily dropped wine after disarmed combat reset");
            return finishCombatResetReequip(now);
        }

        if (now - combatResetPhaseAt >= COMBAT_WINE_RECOVERY_TIMEOUT_MS)
        {
            fail("Unable to recover temporarily dropped wine");
            return true;
        }
        if (wine != null && readyForInteraction(250) && wine.pickup())
        {
            lastInteractionAt = now;
            nextAction = "Pick up temporarily dropped wine";
        }
        else
        {
            nextAction = "Wait for temporarily dropped wine";
        }
        return true;
    }

    private Rs2TileItemModel findDroppedCombatWine()
    {
        if (!combatWineDropped || combatWineDropTile == null)
        {
            return null;
        }
        return Microbot.getRs2TileItemCache().query()
                .withId(WINE_ID)
                .where(item -> item.isOwned()
                        && combatWineDropTile.equals(item.getWorldLocation()))
                .nearestOnClientThread(combatWineDropTile, 0);
    }

    private boolean finishCombatResetReequip(long now)
    {
        if (combatResetKnockoutSucceeded)
        {
            combatClearSince = now;
            combatSignal = false;
            npcInteractionSince = 0;
            ignoreCombatUntil = now + COMBAT_CLEAR_SETTLE_MS;
            transition(BlackjackState.WAITING_FOR_COMBAT_CLEAR,
                    "Confirm combat stayed clear after disarmed Knock-Out");
            return true;
        }
        combatResetPhase = CombatResetPhase.FALLBACK;
        return false;
    }

    private boolean hasPendingCombatEquipmentRecovery()
    {
        return state == BlackjackState.POSITIONING_COMBAT_RESET
                && (combatWineDropped
                || combatResetPhase == CombatResetPhase.DROPPING_WINE
                || combatResetPhase == CombatResetPhase.UNEQUIPPING
                || combatResetPhase == CombatResetPhase.KNOCKING_OUT
                || combatResetPhase == CombatResetPhase.REEQUIPPING
                || combatResetPhase == CombatResetPhase.RECOVERING_WINE);
    }

    private void startCombatResetPhase(CombatResetPhase phase, String action)
    {
        combatResetPhase = phase;
        combatResetPhaseAt = System.currentTimeMillis();
        nextAction = action;
    }

    private void resetDisarmedCombatRecovery()
    {
        combatResetPhase = CombatResetPhase.UNTRIED;
        combatResetPhaseAt = 0;
        combatResetKnockoutAt = 0;
        combatResetClearSince = 0;
        combatResetKnockoutSucceeded = false;
        combatWineDropped = false;
        combatWineDropTile = null;
    }

    private void escapeCombat()
    {
        burstClickPoint = null;
        WorldPoint location = Rs2Player.getWorldLocation();
        WorldPoint safeTile = activeCombatSafeTile();
        if (location != null && location.equals(safeTile))
        {
            combatClearSince = 0;
            transition(BlackjackState.WAITING_FOR_COMBAT_CLEAR, "Wait for combat to clear");
            return;
        }

        if (readyForInteraction(450))
        {
            Rs2Walker.walkFastCanvas(safeTile);
            lastInteractionAt = System.currentTimeMillis();
        }
    }

    private void waitForCombatClear()
    {
        boolean targetingPlayer = isNpcTargetingPlayer();
        combatSignal = targetingPlayer;
        if (targetingPlayer)
        {
            combatClearSince = 0;
            if (elapsedInState() >= COMBAT_RESET_RETRY_MS)
            {
                combatResetRetries++;
                targetIndex = -1;
                npcInteractionSince = 0;
                ignoreCombatUntil = System.currentTimeMillis() + 1_000;
                lastOutcome = "Combat reset probe";
                if (wineRestockPending)
                {
                    restockAfterCombatReset = false;
                    waitingForRestockKnockout = true;
                    transition(BlackjackState.FINDING_TARGET, "Knock-Out target before wine run");
                }
                else
                {
                    transition(BlackjackState.FINDING_TARGET, "Probe Knock-Out after reset");
                }
                return;
            }
            nextAction = "Wait behind bed (" + getStateAgeSeconds() + "s)";
            return;
        }

        if (combatClearSince == 0)
        {
            combatClearSince = System.currentTimeMillis();
        }
        if (System.currentTimeMillis() - combatClearSince >= COMBAT_CLEAR_SETTLE_MS)
        {
            targetIndex = -1;
            lastOutcome = "Combat signal cleared";
            if (wineRestockPending)
            {
                restockAfterCombatReset = false;
                waitingForRestockKnockout = true;
                transition(BlackjackState.FINDING_TARGET, "Knock-Out target before wine run");
            }
            else
            {
                transition(BlackjackState.FINDING_TARGET, "Reacquire target");
            }
        }
    }

    private boolean handleWineRestockPriority()
    {
        if (shutdownRequested || state == BlackjackState.ERROR || state == BlackjackState.STOPPED)
        {
            return false;
        }

        if (hasPendingCombatEquipmentRecovery())
        {
            if (wineRestockPending)
            {
                restockAfterCombatReset = true;
            }
            return true;
        }

        int wines = Rs2Inventory.count(WINE_ID);
        int hitpoints = currentHitpoints();
        projectedWinesNeeded = winesNeededToReach(hitpoints, config.healToPercent());

        if (!config.autoRestockWine())
        {
            return false;
        }

        boolean emergency = wines <= EARLY_WINE_RESTOCK_MAX_WINES
                && hitpoints < EARLY_WINE_RESTOCK_HITPOINTS;
        boolean projectedDepletion = healingRequired
                && projectedWinesNeeded > 0
                && wines <= projectedWinesNeeded;
        if (!wineRestockPending && wines > 0 && !projectedDepletion && !emergency)
        {
            return false;
        }

        if (!wineRestockPending)
        {
            wineRestockPending = true;
            emergencyWineExit = emergency;
            restockTargetWineCount = Math.max(1,
                    wines + Rs2Inventory.count(ItemID.JUG_EMPTY) + Rs2Inventory.emptySlotCount());
            log.info("Wine restock latched: wines={}, needed={}, hp={}, targetWines={}, emergency={}",
                    wines, projectedWinesNeeded, hitpoints, restockTargetWineCount, emergencyWineExit);
        }
        else if (emergency)
        {
            emergencyWineExit = true;
        }

        if (isWineRestockState())
        {
            return true;
        }

        if (emergencyWineExit)
        {
            restockAfterCombatReset = false;
            waitingForRestockKnockout = false;
            transition(BlackjackState.EXITING_FOR_WINE, "Emergency exit: low wine reserve below minimum HP");
            return true;
        }

        if (waitingForRestockKnockout)
        {
            if (shouldEscapeCombat())
            {
                waitingForRestockKnockout = false;
                restockAfterCombatReset = true;
                transition(BlackjackState.POSITIONING_COMBAT_RESET, "Reset renewed combat before wine run");
            }
            return true;
        }

        if (restockAfterCombatReset || isCombatSafetyState() || isNpcTargetingPlayer())
        {
            restockAfterCombatReset = true;
            if (!isCombatSafetyState())
            {
                transition(BlackjackState.POSITIONING_COMBAT_RESET, "Reset combat before wine run");
            }
            return true;
        }

        transition(BlackjackState.EXITING_FOR_WINE, "Exit house for wine");
        return true;
    }

    static int winesNeededToReach(int hitpoints, int targetHitpoints)
    {
        int missingHitpoints = Math.max(0, targetHitpoints - hitpoints);
        return (missingHitpoints + WINE_HEAL_AMOUNT - 1) / WINE_HEAL_AMOUNT;
    }

    private boolean isWineRestockState()
    {
        return state == BlackjackState.EXITING_FOR_WINE
                || state == BlackjackState.SECURING_WINE_EXIT
                || state == BlackjackState.RESTOCKING_WINE
                || state == BlackjackState.RETURNING_WITH_WINE
                || state == BlackjackState.SECURING_WINE_ENTRY;
    }

    private void exitHouseForWine()
    {
        ensureRunAfterPreparation();
        long now = System.currentTimeMillis();
        if (elapsedInState() >= WINE_EXIT_STATE_TIMEOUT_MS)
        {
            fail("Unable to leave blackjack tent through wine curtain");
            return;
        }
        if (!isInsideHouse())
        {
            wineExitCurtainOpenedAt = 0;
            wineDoorCrossingAttempts = 0;
            wineDoorCrossingStartedAt = 0;
            transition(BlackjackState.SECURING_WINE_EXIT, "Close wine curtain behind player");
            return;
        }

        Rs2TileObjectModel openDoor = findWineDoor("Close");
        if (openDoor != null)
        {
            WorldPoint outsideTile = activeWineDoorOutsideTile();
            if (wineExitCurtainOpenedAt == 0)
            {
                wineExitCurtainOpenedAt = System.currentTimeMillis();
                log.info("Wine curtain is open; crossing from {} to {}",
                        Rs2Player.getWorldLocation(), outsideTile);
            }
            walkAcrossWineDoor(outsideTile, "Step outside wine curtain");
            return;
        }

        Rs2TileObjectModel closedDoor = findWineDoor("Open");
        if (closedDoor != null)
        {
            logWineExitDiagnostic(now, "open-curtain", closedDoor);
            if (wineExitCurtainOpenedAt != 0)
            {
                log.info("East curtain closed again before crossing; reopening");
                wineExitCurtainOpenedAt = 0;
                wineDoorCrossingAttempts = 0;
                wineDoorCrossingStartedAt = 0;
            }
            if (interactWithWineDoor(closedDoor, "Open", "Open wine curtain"))
            {
                wineExitCurtainOpenedAt = System.currentTimeMillis();
            }
            return;
        }

        WorldPoint location = Rs2Player.getWorldLocation();
        WorldPoint insideTile = activeWineDoorInsideTile();
        if (location != null
                && location.distanceTo2D(insideTile) > 2
                && readyForInteraction(450))
        {
            boolean dispatched = Rs2Walker.walkFastCanvas(insideTile);
            lastInteractionAt = System.currentTimeMillis();
            nextAction = "Approach wine curtain";
            logWineExitDiagnostic(now, "approach-curtain dispatched=" + dispatched, null);
            return;
        }

        logWineExitDiagnostic(now, "curtain-not-resolved", null);
        waitForWineDoor("Open wine curtain");
    }

    private void secureWineExit()
    {
        if (isInsideHouse())
        {
            transition(BlackjackState.EXITING_FOR_WINE, "Finish leaving house");
            return;
        }

        Rs2TileObjectModel openDoor = findWineDoor("Close");
        if (openDoor != null)
        {
            interactWithWineDoor(openDoor, "Close", "Close wine curtain behind player");
            return;
        }

        if (findWineDoor("Open") != null)
        {
            wineDoorCrossingAttempts = 0;
            wineDoorCrossingStartedAt = 0;
            transition(BlackjackState.RESTOCKING_WINE, "Prepare space and exchange wine notes");
            return;
        }

        WorldPoint location = Rs2Player.getWorldLocation();
        WorldPoint outsideTile = activeWineDoorOutsideTile();
        if (location != null
                && location.distanceTo2D(outsideTile) > 2
                && readyForInteraction(450))
        {
            Rs2Walker.walkFastCanvas(outsideTile);
            lastInteractionAt = System.currentTimeMillis();
            nextAction = "Return beside wine curtain";
            return;
        }

        waitForWineDoor("Confirm wine curtain is closed");
    }

    private void restockWine()
    {
        ensureRunAfterPreparation();
        if (isInsideHouse())
        {
            transition(BlackjackState.EXITING_FOR_WINE, "Return outside before restocking");
            return;
        }

        if (findWineDoor("Close") != null)
        {
            transition(BlackjackState.SECURING_WINE_EXIT, "Close wine curtain before inventory changes");
            return;
        }

        int wines = Rs2Inventory.count(WINE_ID);
        if (wines >= restockTargetWineCount)
        {
            transition(BlackjackState.RETURNING_WITH_WINE, "Return to wine curtain");
            return;
        }

        if (!wineInventoryPrepared)
        {
            int emptyJugs = Rs2Inventory.count(ItemID.JUG_EMPTY);
            if (emptyJugs > 0 && Rs2Inventory.dropAll(ItemID.JUG_EMPTY))
            {
                lastInteractionAt = System.currentTimeMillis();
                log.info("Cleared {} empty jugs outside the secured door for wine exchange", emptyJugs);
                nextAction = "Clear empty jugs outside secured door";
            }
            wineInventoryPrepared = true;
            return;
        }

        if (Rs2Inventory.emptySlotCount() == 0)
        {
            fail("No safe inventory space for wine");
            return;
        }

        int coins = Rs2Inventory.itemQuantity(COINS_ID);
        if (coins < WINE_EXCHANGE_COST)
        {
            fail("Coins required to restock wine");
            return;
        }

        if (Rs2Inventory.hasItem(NOTED_WINE_ID))
        {
            exchangeNotedWine();
            return;
        }

        if (wines > 0)
        {
            restockTargetWineCount = wines;
            transition(BlackjackState.RETURNING_WITH_WINE, "Return with available wine");
            return;
        }

        buyWineFromFaisal();
    }

    private void returnWithWine()
    {
        if (Rs2Shop.isOpen())
        {
            Rs2Shop.closeShop();
            lastInteractionAt = System.currentTimeMillis();
            return;
        }

        if (isInsideHouse())
        {
            wineDoorCrossingAttempts = 0;
            wineDoorCrossingStartedAt = 0;
            transition(BlackjackState.SECURING_WINE_ENTRY, "Close wine curtain behind player");
            return;
        }

        WorldPoint location = Rs2Player.getWorldLocation();
        WorldPoint outsideTile = activeWineDoorOutsideTile();
        if (location != null && location.distanceTo2D(outsideTile) > 2)
        {
            if (readyForInteraction(450))
            {
                Rs2Walker.walkFastCanvas(outsideTile);
                lastInteractionAt = System.currentTimeMillis();
                nextAction = "Return to outside curtain tile";
            }
            return;
        }

        Rs2TileObjectModel closedDoor = findWineDoor("Open");
        if (closedDoor != null)
        {
            interactWithWineDoor(closedDoor, "Open", "Open wine curtain to re-enter");
            return;
        }

        if (findWineDoor("Close") != null && readyForInteraction(350))
        {
            walkAcrossWineDoor(activeWineDoorInsideTile(), "Step back inside wine curtain");
            return;
        }

        waitForWineDoor("Open wine curtain to re-enter");
    }

    private void secureWineEntry()
    {
        if (!isInsideHouse())
        {
            transition(BlackjackState.RETURNING_WITH_WINE, "Finish re-entering house");
            return;
        }

        Rs2TileObjectModel openDoor = findWineDoor("Close");
        if (openDoor != null)
        {
            interactWithWineDoor(openDoor, "Close", "Close wine curtain behind player");
            return;
        }

        if (findWineDoor("Open") == null)
        {
            waitForWineDoor("Confirm wine curtain is closed");
            return;
        }

        wineRestockPending = false;
        restockAfterCombatReset = false;
        waitingForRestockKnockout = false;
        emergencyWineExit = false;
        wineInventoryPrepared = false;
        restockTargetWineCount = 0;
        projectedWinesNeeded = winesNeededToReach(currentHitpoints(), config.healToPercent());
        lastOutcome = "Wine restock complete";
        maintainTargetZoom();
        if (currentHitpoints() < config.healToPercent())
        {
            healingRequired = true;
            transition(BlackjackState.HEALING, "Heal after secured wine run");
        }
        else
        {
            transition(BlackjackState.FINDING_TARGET, "Resume blackjacking");
        }
    }

    private Rs2TileObjectModel findWineDoor(String action)
    {
        Rs2TileObjectModel door;
        if (selectedTarget() == BlackjackTarget.MENAPHITE_THUG)
        {
            door = findSouthTentCurtain(action);
        }
        else
        {
            door = new Rs2TileObjectQueryable()
                    .where(object -> {
                        ObjectComposition composition = object.getObjectComposition();
                        return composition != null && isBanditWineDoor(composition.getName(),
                                object.getWorldLocation(), composition.getActions(), action);
                    })
                    .first();
        }
        if (door != null)
        {
            wineDoorMissingSince = 0;
        }
        return door;
    }

    static boolean isBanditWineDoor(String name, WorldPoint location, String[] actions, String action)
    {
        return ("Curtain".equalsIgnoreCase(name) || "Door".equalsIgnoreCase(name)) && location != null
                && location.getPlane() == BANDIT_WINE_DOOR_INSIDE_TILE.getPlane()
                && location.distanceTo2D(BANDIT_WINE_DOOR_INSIDE_TILE) <= 1
                && actions != null && Arrays.stream(actions).filter(Objects::nonNull)
                .anyMatch(candidate -> candidate.equalsIgnoreCase(action));
    }

    private Rs2TileObjectModel findSouthTentCurtain(String action)
    {
        int expectedId;
        if ("Open".equalsIgnoreCase(action))
        {
            expectedId = CLOSED_CURTAIN_ID;
        }
        else if ("Close".equalsIgnoreCase(action))
        {
            expectedId = OPEN_CURTAIN_ID;
        }
        else
        {
            return null;
        }
        return Microbot.getRs2TileObjectCache().query()
                .withId(expectedId)
                .where(object -> SOUTH_THUG_WINE_CURTAIN_TILE.equals(object.getWorldLocation()))
                .first();
    }

    private boolean isSouthTentCurtainOpen()
    {
        return findSouthTentCurtain("Close") != null;
    }

    private boolean hasObjectAction(Rs2TileObjectModel object, String action)
    {
        ObjectComposition composition = object.getObjectComposition();
        return composition != null
                && Arrays.stream(composition.getActions())
                        .filter(Objects::nonNull)
                        .anyMatch(candidate -> candidate.equalsIgnoreCase(action));
    }

    private boolean interactWithWineDoor(Rs2TileObjectModel door, String action, String description)
    {
        if (!readyForInteraction(DOOR_INTERACTION_DELAY_MS))
        {
            return false;
        }
        if (door.click(action))
        {
            lastInteractionAt = System.currentTimeMillis();
            nextAction = description;
            return true;
        }
        log.warn("Wine curtain interaction returned false: id={} location={} action={} state={}",
                door.getId(), door.getWorldLocation(), action, state);
        return false;
    }

    private void logWineExitDiagnostic(long now, String branch, Rs2TileObjectModel door)
    {
        if (now - lastWineExitDiagnosticAt < WINE_EXIT_DIAGNOSTIC_INTERVAL_MS)
        {
            return;
        }
        lastWineExitDiagnosticAt = now;
        log.info("Wine-exit branch={} player={} inside={} outside={} doorId={} doorLocation={} stateAgeMs={}",
                branch,
                Rs2Player.getWorldLocation(),
                activeWineDoorInsideTile(),
                activeWineDoorOutsideTile(),
                door == null ? -1 : door.getId(),
                door == null ? null : door.getWorldLocation(),
                elapsedInState());
    }

    private void walkAcrossWineDoor(WorldPoint destination, String description)
    {
        long now = System.currentTimeMillis();
        if (wineDoorCrossingStartedAt == 0)
        {
            wineDoorCrossingStartedAt = now;
        }
        if (now - wineDoorCrossingStartedAt >= WINE_DOOR_CROSSING_TIMEOUT_MS)
        {
            fail("Unable to cross east blackjack curtain");
            return;
        }
        if (!readyForInteraction(WINE_DOOR_CROSSING_RETRY_MS))
        {
            return;
        }

        boolean dispatched = Rs2Walker.walkFastCanvas(destination);
        wineDoorCrossingAttempts++;
        lastInteractionAt = System.currentTimeMillis();
        nextAction = description + " (attempt " + wineDoorCrossingAttempts + ")";
        if (wineDoorCrossingAttempts == 1 || wineDoorCrossingAttempts % 5 == 0)
        {
            log.info("Wine-door crossing attempt {} dispatched={} player={} destination={} doorOpen={}",
                    wineDoorCrossingAttempts,
                    dispatched,
                    Rs2Player.getWorldLocation(),
                    destination,
                    findWineDoor("Close") != null);
        }
    }

    private void waitForWineDoor(String action)
    {
        nextAction = action;
        long now = System.currentTimeMillis();
        if (wineDoorMissingSince == 0)
        {
            wineDoorMissingSince = now;
            log.debug("Wine curtain temporarily unresolved in state {}; waiting for cache transition", state);
            return;
        }
        if (now - wineDoorMissingSince > WINE_DOOR_RESOLUTION_TIMEOUT_MS)
        {
            fail("Blackjack wine curtain could not be resolved");
        }
    }

    private void exchangeNotedWine()
    {
        if (Rs2Dialogue.hasSelectAnOption())
        {
            int option = Rs2Inventory.emptySlotCount() > 1 ? 3 : 1;
            Rs2Dialogue.keyPressForDialogueOption(option);
            lastInteractionAt = System.currentTimeMillis();
            nextAction = option == 3 ? "Exchange all notes" : "Exchange one note";
            return;
        }

        Rs2NpcModel merchant = findWineMerchant();
        if (merchant == null)
        {
            WorldPoint playerLocation = Rs2Player.getWorldLocation();
            if (playerLocation == null || playerLocation.distanceTo2D(WINE_MERCHANT_TILE) > 2)
            {
                wineMerchantMissingSince = 0;
                if (readyForInteraction(700))
                {
                    Rs2Walker.walkTo(WINE_MERCHANT_TILE, 0);
                    lastInteractionAt = System.currentTimeMillis();
                    nextAction = "Walk to recorded note merchant tile";
                }
                return;
            }

            long now = System.currentTimeMillis();
            if (wineMerchantMissingSince == 0)
            {
                wineMerchantMissingSince = now;
                log.debug("Banknote Exchange Merchant temporarily unresolved at {}; waiting for NPC cache",
                        playerLocation);
            }
            else if (now - wineMerchantMissingSince > WINE_MERCHANT_RESOLUTION_TIMEOUT_MS)
            {
                fail("Banknote Exchange Merchant not found");
            }
            return;
        }
        wineMerchantMissingSince = 0;

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldPoint merchantLocation = merchant.getWorldLocation();
        if (playerLocation == null || merchantLocation == null)
        {
            nextAction = "Wait for note merchant location";
            return;
        }
        if (playerLocation.distanceTo2D(merchantLocation) > 2)
        {
            if (readyForInteraction(700))
            {
                Rs2Walker.walkTo(merchantLocation, 1);
                lastInteractionAt = System.currentTimeMillis();
                nextAction = "Walk to note merchant";
            }
            return;
        }

        if (readyForInteraction(700))
        {
            if (Rs2Inventory.use(NOTED_WINE_ID) && merchant.click())
            {
                lastInteractionAt = System.currentTimeMillis();
                nextAction = "Choose note quantity";
            }
        }
    }

    private Rs2NpcModel findWineMerchant()
    {
        Rs2NpcModel cachedMerchant = Microbot.getRs2NpcCache().query()
                .withName("Banknote Exchange Merchant")
                .nearestOnClientThread();
        if (cachedMerchant != null)
        {
            return cachedMerchant;
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            if (player == null || player.getWorldLocation() == null
                    || Microbot.getClient().getTopLevelWorldView() == null)
            {
                return null;
            }
            return Microbot.getClient().getTopLevelWorldView().npcs().stream()
                    .filter(Objects::nonNull)
                    .filter(npc -> "Banknote Exchange Merchant".equalsIgnoreCase(npc.getName()))
                    .min(Comparator.comparingInt(npc ->
                            npc.getWorldLocation().distanceTo2D(player.getWorldLocation())))
                    .map(Rs2NpcModel::new)
                    .orElse(null);
        }).orElse(null);
    }

    private void buyWineFromFaisal()
    {
        if (!Rs2Shop.isOpen())
        {
            if (readyForInteraction(900))
            {
                if (!Rs2Shop.openShop("Faisal", false))
                {
                    fail("Faisal's shop not found");
                    return;
                }
                lastInteractionAt = System.currentTimeMillis();
                nextAction = "Open Faisal's shop";
            }
            return;
        }

        if (readyForInteraction(700))
        {
            if (!Rs2Shop.buyItem("Jug of wine", "50"))
            {
                fail("Jug of wine unavailable in shop");
                return;
            }
            lastInteractionAt = System.currentTimeMillis();
            nextAction = "Buy jug of wine";
        }
    }

    private void processOutcomes()
    {
        Outcome outcome;
        while ((outcome = outcomes.poll()) != null)
        {
            if (outcome == Outcome.KNOCKOUT_SUCCESS || outcome == Outcome.KNOCKOUT_FAILED
                    || outcome == Outcome.PICKPOCKET_SUCCESS || outcome == Outcome.PICKPOCKET_FAILED
                    || outcome == Outcome.STUNNED)
            {
                lastBlackjackFeedbackAt = System.currentTimeMillis();
            }
            switch (outcome)
            {
                case KNOCKOUT_SUCCESS:
                    if (isDisarmedCombatKnockoutPending())
                    {
                        successfulKnockouts++;
                        combatResetKnockoutSucceeded = true;
                        lastOutcome = "Disarmed Knock-Out succeeded";
                        startCombatResetPhase(CombatResetPhase.REEQUIPPING,
                                "Re-equip blackjack after combat reset");
                        log.info("Disarmed combat-reset Knock-Out confirmed by success signal");
                        break;
                    }
                    if (state != BlackjackState.PICKPOCKETING || knockoutResult != KnockoutResult.PENDING)
                    {
                        log.debug("Ignoring stale knockout success in state {}", state);
                        break;
                    }
                    confirmKnockout("success signal");
                    break;
                case KNOCKOUT_FAILED:
                    if (isDisarmedCombatKnockoutPending())
                    {
                        failedKnockouts++;
                        lastOutcome = "Disarmed Knock-Out failed";
                        startCombatResetPhase(CombatResetPhase.REEQUIPPING,
                                "Re-equip blackjack for safespot fallback");
                        log.info("Disarmed combat-reset Knock-Out failed; restoring blackjack before fallback");
                        break;
                    }
                    if (state != BlackjackState.PICKPOCKETING || knockoutResult != KnockoutResult.PENDING)
                    {
                        log.debug("Ignoring stale knockout failure in state {}", state);
                        break;
                    }
                    knockoutResult = KnockoutResult.FAILED;
                    knockoutFailedAt = System.currentTimeMillis();
                    knockoutRetryAt = knockoutFailedAt + randomFailedKnockoutRetry();
                    nextPickpocketClickAt = knockoutFailedAt + randomPickpocketDelay(true);
                    knockoutBurstReleaseAt = 0;
                    knockoutFallbackReleased = false;
                    pickpocketBurstStartedAt = knockoutFailedAt;
                    failedKnockouts++;
                    lastOutcome = "Knock-Out failed";
                    ignoreCombatUntil = System.currentTimeMillis() + FAILED_KNOCKOUT_RETALIATION_GRACE_MS;
                    npcInteractionSince = 0;
                    nextAction = "Interrupt retaliation, then retry Knock-Out";
                    log.info("Knock-Out failure signal after {}ms; releasing retaliation pickpocket burst",
                            knockoutDispatchAge(knockoutFailedAt));
                    break;
                case PICKPOCKET_SUCCESS:
                    if (state == BlackjackState.PICKPOCKETING || isSecondPickpocketPrearmPending())
                    {
                        if (!hasCurrentKnockoutPickpocketClick())
                        {
                            log.debug("Ignoring pickpocket success before this Knock-Out issued a pickpocket click");
                            break;
                        }
                        successfulPickpockets++;
                        picksThisKnockout++;
                        pendingInventoryFullAt = 0;
                        lastOutcome = "Pickpocket " + picksThisKnockout + "/2";
                        if (picksThisKnockout >= 2)
                        {
                            armNextKnockout(currentTarget());
                            nextAction = secondPickpocketInteractionSeen
                                    ? "Pre-arm Knock-Out during second pickpocket"
                                    : "Wait for second pickpocket interaction";
                        }
                    }
                    break;
                case PICKPOCKET_FAILED:
                case STUNNED:
                    if (state != BlackjackState.PICKPOCKETING && !isSecondPickpocketPrearmPending())
                    {
                        log.debug("Ignoring stale pickpocket failure/stun in state {}", state);
                        break;
                    }
                    if (isSecondPickpocketPrearmPending())
                    {
                        cancelSecondPickpocketPrearm("second pickpocket failed or stunned");
                        transition(BlackjackState.PICKPOCKETING, "Resume unresolved pickpocket burst");
                    }
                    nextAction = "Continue pickpocket attempts while stunned";
                    lastOutcome = "Pickpocket failed/stunned";
                    break;
                case INVENTORY_FULL:
                    if (state == BlackjackState.PICKPOCKETING && lastPickpocketClickAt > 0)
                    {
                        if (pendingInventoryFullAt == 0)
                        {
                            pendingInventoryFullAt = System.currentTimeMillis();
                        }
                        lastOutcome = "Full-inventory signal observed";
                        nextAction = "Finish active pickpocket or reset aggression";
                    }
                    break;
                case WINE_EXCHANGED:
                    nextAction = "Return to marked house";
                    break;
                default:
                    break;
            }
        }
    }

    private boolean isDisarmedCombatKnockoutPending()
    {
        return state == BlackjackState.POSITIONING_COMBAT_RESET
                && combatResetPhase == CombatResetPhase.KNOCKING_OUT
                && combatResetKnockoutAt > 0;
    }

    public void onChatMessage(String rawMessage)
    {
        if (rawMessage == null)
        {
            return;
        }
        String message = rawMessage.toLowerCase(Locale.ROOT);
        if (message.contains("render them unconscious"))
        {
            outcomes.add(Outcome.KNOCKOUT_SUCCESS);
        }
        else if (message.contains("blow only glances"))
        {
            outcomes.add(Outcome.KNOCKOUT_FAILED);
        }
        else if (message.contains("you pick the") && message.contains("pocket"))
        {
            outcomes.add(Outcome.PICKPOCKET_SUCCESS);
        }
        else if (message.contains("fail to pick") && message.contains("pocket"))
        {
            outcomes.add(Outcome.PICKPOCKET_FAILED);
        }
        else if (message.contains("you've been stunned"))
        {
            outcomes.add(Outcome.STUNNED);
        }
        else if (message.contains("don't have enough inventory space")
                || message.contains("don't have enough space to do that"))
        {
            outcomes.add(Outcome.INVENTORY_FULL);
        }
        else if (message.contains("merchant converts your banknote"))
        {
            outcomes.add(Outcome.WINE_EXCHANGED);
        }
    }

    public void onOverheadTextChanged(NPC npc, String rawText)
    {
        if (npc == null || npc.getIndex() != targetIndex || rawText == null)
        {
            return;
        }

        String text = rawText.trim().toLowerCase(Locale.ROOT);
        if (text.matches("z{3,}.*"))
        {
            log.debug("Knock-Out success overhead observed: {}", rawText);
            outcomes.add(Outcome.KNOCKOUT_SUCCESS);
        }
        else if (text.contains("kill you for that"))
        {
            log.debug("Knock-Out failure overhead observed: {}", rawText);
            outcomes.add(Outcome.KNOCKOUT_FAILED);
        }
    }

    public void onStatChanged(Skill skill, int xp)
    {
        if (skill != Skill.THIEVING || startXp < 0)
        {
            return;
        }

        int previousXp = lastObservedThievingXp;
        lastObservedThievingXp = xp;
        if (previousXp > 0 && xp - previousXp == 10
                && state == BlackjackState.PICKPOCKETING
                && knockoutResult == KnockoutResult.PENDING)
        {
            log.debug("Knock-Out 10 XP signal observed");
            outcomes.add(Outcome.KNOCKOUT_SUCCESS);
        }
    }

    private Rs2NpcModel findEligibleTarget()
    {
        List<Rs2NpcModel> candidates = Microbot.getRs2NpcCache().query()
                .where(this::isEligibleTarget)
                .toListOnClientThread();
        return candidates.stream()
                .min(Comparator.comparingInt(Rs2NpcModel::getDistanceFromPlayer))
                .orElse(null);
    }

    private Rs2NpcModel currentTarget()
    {
        if (targetIndex < 0)
        {
            return findEligibleTarget();
        }
        return Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getIndex() == targetIndex && isEligibleTarget(npc))
                .nearestOnClientThread();
    }

    private boolean isEligibleTarget(Rs2NpcModel npc)
    {
        return isSelectedTargetType(npc) && isInsideActiveHouse(npc.getWorldLocation());
    }

    private boolean isSelectedTargetType(Rs2NpcModel npc)
    {
        if (npc == null || npc.getName() == null || npc.getWorldLocation() == null)
        {
            return false;
        }
        return selectedTarget().matches(npc.getName(), npc.getCombatLevel());
    }

    private String expectedTargetDescription()
    {
        switch (selectedTarget())
        {
            case BANDIT_41:
                return "Bandit (level 41)";
            case BANDIT_56:
                return "Bandit (level 56)";
            case MENAPHITE_THUG:
                return "Menaphite Thug";
            default:
                return "Selected target";
        }
    }

    private BlackjackTarget selectedTarget()
    {
        if ((config == null || config.target() == BlackjackTarget.AUTO) && automaticTarget != null)
        {
            return automaticTarget;
        }
        return selectTargetForLevel(config != null && config.target() != BlackjackTarget.AUTO
                ? 0 : Rs2Player.getRealSkillLevel(Skill.THIEVING));
    }

    BlackjackTarget selectTargetForLevel(int thievingLevel)
    {
        BlackjackTarget configuredTarget = config == null ? BlackjackTarget.AUTO : config.target();
        if (configuredTarget != BlackjackTarget.AUTO)
        {
            return configuredTarget;
        }
        if (automaticTarget == null && thievingLevel > 0)
        {
            automaticTarget = BlackjackTarget.forLevel(thievingLevel);
        }
        return automaticTarget == null ? BlackjackTarget.forLevel(thievingLevel) : automaticTarget;
    }

    private boolean hasCurrentKnockoutPickpocketClick()
    {
        return knockoutClickIssuedAt > 0 && lastPickpocketClickAt >= knockoutClickIssuedAt;
    }

    private int requiredThievingLevel(BlackjackTarget target)
    {
        return target.requiredThievingLevel();
    }

    private boolean hasBlackjackEquipped()
    {
        return Rs2Equipment.isWearing(item -> item.getName() != null
                && item.getName().toLowerCase(Locale.ROOT).contains("blackjack"));
    }

    private boolean isInsideHouse()
    {
        return isInsideActiveHouse(Rs2Player.getWorldLocation());
    }

    private boolean isInsideActiveHouse(WorldPoint location)
    {
        if (location == null)
        {
            return false;
        }
        if (selectedTarget() != BlackjackTarget.MENAPHITE_THUG)
        {
            return BANDIT_HOUSE.contains(location);
        }
        return isInsideSouthTent(location);
    }

    private boolean isInsideSouthTent(WorldPoint location)
    {
        return SouthernTentLureSafety.inside(location);
    }

    private WorldPoint activeHouseCentre()
    {
        return selectedTarget() == BlackjackTarget.MENAPHITE_THUG
                ? SOUTH_THUG_TENT_CENTRE
                : BANDIT_HOUSE_CENTRE;
    }

    public boolean shouldHighlightBanditHouse()
    {
        return config != null && config.showBanditHouse()
                && selectedTarget() != BlackjackTarget.MENAPHITE_THUG;
    }

    static WorldArea banditHouseArea()
    {
        return BANDIT_HOUSE;
    }

    private WorldPoint activeCombatStagingTile()
    {
        return selectedTarget() == BlackjackTarget.MENAPHITE_THUG
                ? SOUTH_THUG_COMBAT_STAGING_TILE
                : COMBAT_STAGING_TILE;
    }

    private WorldPoint activeCombatSafeTile()
    {
        return selectedTarget() == BlackjackTarget.MENAPHITE_THUG
                ? SOUTH_THUG_COMBAT_SAFE_TILE
                : COMBAT_SAFE_TILE;
    }

    private WorldPoint activeWineDoorInsideTile()
    {
        return selectedTarget() == BlackjackTarget.MENAPHITE_THUG
                ? SOUTH_THUG_WINE_DOOR_INSIDE_TILE
                : BANDIT_WINE_DOOR_INSIDE_TILE;
    }

    private WorldPoint activeWineDoorOutsideTile()
    {
        return selectedTarget() == BlackjackTarget.MENAPHITE_THUG
                ? SOUTH_THUG_WINE_DOOR_OUTSIDE_TILE
                : BANDIT_WINE_DOOR_OUTSIDE_TILE;
    }

    private boolean clickAnchoredTarget(Rs2NpcModel target)
    {
        if (Microbot.getClient().isMenuOpen())
        {
            return false;
        }
        Point anchor = burstWanderAnchor(target, burstClickPoint);
        if (anchor == null)
        {
            burstClickPoint = null;
            nextAction = "Wait for safe NPC click point";
            return false;
        }

        Point current = Microbot.getClient().getMouseCanvasPosition();
        if (current != null
                && Math.abs(current.getX() - anchor.getX()) <= 2
                && Math.abs(current.getY() - anchor.getY()) <= 2)
        {
            if (!isInsideTargetHull(target, current))
            {
                log.warn("Refusing target click outside NPC hull: expected={} actual={} targetIndex={}",
                        anchor, current, targetIndex);
                burstClickPoint = null;
                nextAction = "Reacquire safe NPC click point";
                return false;
            }
            if (!isTopTargetOption(target, PICKPOCKET_ACTION))
            {
                noteStandingAnchorMenuMiss(target);
                burstClickPoint = null;
                nextAction = "Wait for Pickpocket left-click swap";
                return false;
            }
            standingAnchorMenuMisses = 0;
            Microbot.getMouse().click(current);
            burstClickPoint = anchor;
        }
        else
        {
            if (!moveAndLeftClickTargetOption(target, anchor, PICKPOCKET_ACTION))
            {
                return false;
            }
            burstClickPoint = anchor;
        }
        return true;
    }

    private boolean moveAndLeftClickTargetOption(Rs2NpcModel target, Point point, String option)
    {
        if (Microbot.naturalMouse == null)
        {
            log.warn("Natural mouse unavailable; refusing direct target click");
            nextAction = "Wait for natural mouse";
            return false;
        }

        Microbot.naturalMouse.moveTo(point.getX(), point.getY());
        Point current = Microbot.getClient().getMouseCanvasPosition();
        if (current == null)
        {
            current = point;
        }
        if (!isInsideTargetHull(target, current))
        {
            log.warn("Natural mouse ended outside NPC hull; refusing {} click: expected={} actual={} targetIndex={}",
                    option, point, current, targetIndex);
            nextAction = "Reacquire safe NPC click point";
            return false;
        }
        if (!isTopTargetOption(target, option))
        {
            nextAction = "Wait for " + option + " left-click swap";
            return false;
        }
        Microbot.getMouse().click(current);
        return true;
    }

    private boolean isTopTargetOption(Rs2NpcModel target, String option)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            MenuEntry[] entries = Microbot.getClient().getMenuEntries();
            if (entries == null || entries.length == 0)
            {
                return false;
            }
            MenuEntry entry = entries[entries.length - 1];
            NPC npc = entry.getNpc();
            return option.equalsIgnoreCase(entry.getOption())
                    && npc != null
                    && target != null
                    && npc.getIndex() == target.getIndex();
        }).orElse(false);
    }

    private void prepositionPrearmedKnockoutCursor(Rs2NpcModel target)
    {
        Point current = Microbot.getClient().getMouseCanvasPosition();
        if (isInsideTargetHull(target, current))
        {
            nextAction = "Pre-arm Knock-Out cursor";
            return;
        }

        Point anchor = targetAnchor(target, burstClickPoint);
        if (anchor == null || Microbot.naturalMouse == null)
        {
            nextAction = "Wait for safe pre-armed Knock-Out cursor";
            return;
        }

        Microbot.naturalMouse.moveTo(anchor.getX(), anchor.getY());
        Point actual = Microbot.getClient().getMouseCanvasPosition();
        if (!isInsideTargetHull(target, actual))
        {
            log.warn("Pre-armed Knock-Out cursor ended outside NPC hull: expected={} actual={} targetIndex={}",
                    anchor, actual, targetIndex);
            return;
        }
        burstClickPoint = actual;
        nextAction = "Pre-arm Knock-Out cursor";
    }

    private boolean isInsideTargetHull(Rs2NpcModel target, Point point)
    {
        return target != null && isInsideClickViewport(point)
                && Microbot.getClientThread().runOnClientThreadOptional(() -> {
                    NPC npc = target.getNpc();
                    Shape hull = npc == null ? null : npc.getConvexHull();
                    return hull != null && hull.contains(point.getX(), point.getY());
                }).orElse(false);
    }

    private boolean isInsideClickViewport(Point point)
    {
        if (point == null)
        {
            return false;
        }
        int width = Microbot.getClient().getCanvasWidth();
        int height = Microbot.getClient().getCanvasHeight();
        return isInsideClickViewport(point.getX(), point.getY(), width, height);
    }

    private boolean isInsideClickViewport(int x, int y, int width, int height)
    {
        return x >= TARGET_CLICK_VIEWPORT_MARGIN
                && y >= TARGET_CLICK_VIEWPORT_MARGIN
                && x < width - TARGET_CLICK_VIEWPORT_MARGIN
                && y < height - TARGET_CLICK_VIEWPORT_MARGIN;
    }

    private void handleKnockoutAimFailure(Rs2NpcModel target, String reason)
    {
        long now = System.currentTimeMillis();
        if (knockoutAimStartedAt == 0)
        {
            knockoutAimStartedAt = now;
        }
        knockoutAimFailures++;
        long age = now - knockoutAimStartedAt;

        if (knockoutAimRecoveryStage < 1 && age >= KNOCKOUT_AIM_REACQUIRE_MS)
        {
            knockoutAimRecoveryStage = 1;
            burstClickPoint = null;
            faceKnockoutTarget(target);
            logKnockoutAimRecovery("reacquire", target, reason, age);
            nextAction = "Reface and reacquire Knock-Out target";
            return;
        }
        if (knockoutAimRecoveryStage < 2 && age >= KNOCKOUT_AIM_CAMERA_RESET_MS)
        {
            knockoutAimRecoveryStage = 2;
            burstClickPoint = null;
            faceKnockoutTarget(target);
            Rs2Camera.setPitch(TARGET_CAMERA_PITCH);
            if (targetCameraZoom >= 0)
            {
                Rs2Camera.setZoom(effectiveTargetZoom());
            }
            logKnockoutAimRecovery("camera-reset", target, reason, age);
            nextAction = "Restore camera for Knock-Out target";
            return;
        }
        if (knockoutAimRecoveryStage < 3 && age >= KNOCKOUT_AIM_OBSTRUCTION_RECOVERY_MS)
        {
            knockoutAimRecoveryStage = 3;
            logKnockoutAimRecovery("obstruction-recovery", target, reason, age);
            waitForTargetClear("Knock-Out cursor remained outside safe viewport", true);
        }
    }

    private void faceKnockoutTarget(Rs2NpcModel target)
    {
        if (target == null || target.getNpc() == null)
        {
            return;
        }
        int targetYaw = frontFacingTargetYaw(target);
        requestCameraTurn(targetYaw);
        nextCameraRefacingAt = 0;
        lastCameraTargetLocation = target.getWorldLocation();
    }

    private void logKnockoutAimRecovery(String stage, Rs2NpcModel target, String reason, long age)
    {
        String geometry = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            NPC npc = target == null ? null : target.getNpc();
            Shape hull = npc == null ? null : npc.getConvexHull();
            return "target=" + (target == null ? null : target.getWorldLocation())
                    + " hull=" + (hull == null ? null : hull.getBounds())
                    + " canvas=" + Microbot.getClient().getCanvasWidth()
                    + "x" + Microbot.getClient().getCanvasHeight();
        }).orElse("geometry unavailable");
        log.warn("Knock-Out pre-dispatch recovery stage={}: reason={} age={}ms failures={} {}",
                stage, reason, age, knockoutAimFailures, geometry);
    }

    private void resetKnockoutAimRecovery()
    {
        knockoutAimStartedAt = 0;
        knockoutAimFailures = 0;
        knockoutAimRecoveryStage = 0;
    }

    private boolean handleHumanizerPriority()
    {
        if (!config.humanizerEnabled())
        {
            humanizerStatus = "Disabled";
            if (isHumanizerState())
            {
                humanizerMouseRecoverAt = 0;
                humanizerBreakUntil = 0;
                transition(BlackjackState.FINDING_TARGET, "Humanizer disabled");
                return true;
            }
            return false;
        }

        long now = System.currentTimeMillis();
        normalizeInterruptedHumanizer(now);
        if (isHumanizerState())
        {
            return false;
        }
        if (!isSafeHumanizerBoundary())
        {
            return false;
        }

        if (config.humanizerBreaks() && now >= nextSmallBreakAt)
        {
            startHumanizerBreak("Small break", configuredBreakDuration(false));
            nextMicroBreakAt = humanizerBreakUntil + configuredBreakInterval(true);
            return true;
        }
        if (config.humanizerBreaks() && now >= nextMicroBreakAt)
        {
            startHumanizerBreak("Micro break", configuredBreakDuration(true));
            return true;
        }
        if (config.randomMouseRecovery() && now >= nextHumanizerMouseAt)
        {
            return startHumanizerMouse();
        }

        humanizerStatus = "Scheduled";
        return false;
    }

    private boolean isHumanizerEventDue(long now)
    {
        if (!config.humanizerEnabled())
        {
            return false;
        }
        return (config.humanizerBreaks() && (now >= nextSmallBreakAt || now >= nextMicroBreakAt))
                || (config.randomMouseRecovery() && now >= nextHumanizerMouseAt);
    }

    private boolean isSafeHumanizerBoundary()
    {
        return state == BlackjackState.FINDING_TARGET
                && !healingRequired
                && !wineRestockPending
                && !Rs2Player.isMoving()
                && !isNpcTargetingPlayer()
                && currentHitpoints() >= config.healBelowPercent();
    }

    private boolean isHumanizerState()
    {
        return state == BlackjackState.HUMANIZER_MOUSE
                || state == BlackjackState.HUMANIZER_BREAK;
    }

    private boolean startHumanizerMouse()
    {
        if (Microbot.naturalMouse == null)
        {
            nextHumanizerMouseAt = scheduleFromNow(System.currentTimeMillis(),
                    HUMANIZER_MOUSE_MIN_INTERVAL_MS, HUMANIZER_MOUSE_MAX_INTERVAL_MS);
            return false;
        }

        Point current = Microbot.getClient().getMouseCanvasPosition();
        int canvasWidth = Microbot.getClient().getCanvasWidth();
        int canvasHeight = Microbot.getClient().getCanvasHeight();
        int originX = current == null ? canvasWidth / 2 : current.getX();
        int originY = current == null ? canvasHeight / 2 : current.getY();
        int dx = randomBetween(80, 221) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int dy = randomBetween(40, 161) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int x = Math.max(8, Math.min(canvasWidth - 8, originX + dx));
        int y = Math.max(8, Math.min(canvasHeight - 8, originY + dy));
        Microbot.naturalMouse.moveTo(x, y);
        humanizerMouseRecoverAt = System.currentTimeMillis() + randomBetween(250, 901);
        humanizerStatus = "Mouse wander";
        humanizerEvents++;
        transition(BlackjackState.HUMANIZER_MOUSE, "Recover mouse toward target");
        return true;
    }

    private void recoverHumanizerMouse()
    {
        if (System.currentTimeMillis() < humanizerMouseRecoverAt)
        {
            nextAction = "Pause after mouse wander";
            return;
        }

        Rs2NpcModel target = currentTarget();
        Point anchor = target == null ? null : targetAnchor(target, null);
        if (anchor != null && Microbot.naturalMouse != null)
        {
            Microbot.naturalMouse.moveTo(anchor.getX(), anchor.getY());
        }
        humanizerMouseRecoverAt = 0;
        nextHumanizerMouseAt = scheduleFromNow(System.currentTimeMillis(),
                HUMANIZER_MOUSE_MIN_INTERVAL_MS, HUMANIZER_MOUSE_MAX_INTERVAL_MS);
        humanizerStatus = "Scheduled";
        transition(BlackjackState.FINDING_TARGET, "Resume after mouse recovery");
    }

    private void startHumanizerBreak(String type, long duration)
    {
        humanizerBreakType = type;
        humanizerBreakUntil = System.currentTimeMillis() + duration;
        humanizerStatus = type;
        humanizerEvents++;
        transition(BlackjackState.HUMANIZER_BREAK, type);
    }

    private void runHumanizerBreak()
    {
        long now = System.currentTimeMillis();
        if (now < humanizerBreakUntil)
        {
            long seconds = Math.max(1, (humanizerBreakUntil - now + 999) / 1_000);
            nextAction = humanizerBreakType + " (" + seconds + "s)";
            return;
        }

        if ("Micro break".equals(humanizerBreakType))
        {
            nextMicroBreakAt = now + configuredBreakInterval(true);
        }
        else
        {
            nextSmallBreakAt = now + configuredBreakInterval(false);
        }
        humanizerBreakUntil = 0;
        humanizerBreakType = "None";
        humanizerStatus = "Scheduled";
        transition(BlackjackState.FINDING_TARGET, "Resume after humanizer break");
    }

    private void normalizeInterruptedHumanizer(long now)
    {
        if (state != BlackjackState.HUMANIZER_MOUSE && humanizerMouseRecoverAt > 0)
        {
            humanizerMouseRecoverAt = 0;
            nextHumanizerMouseAt = scheduleFromNow(now,
                    HUMANIZER_MOUSE_MIN_INTERVAL_MS, HUMANIZER_MOUSE_MAX_INTERVAL_MS);
        }
        if (state != BlackjackState.HUMANIZER_BREAK && humanizerBreakUntil > 0)
        {
            humanizerBreakUntil = 0;
            humanizerBreakType = "None";
            nextMicroBreakAt = now + configuredBreakInterval(true);
            nextSmallBreakAt = now + configuredBreakInterval(false);
        }
    }

    private long configuredBreakInterval(boolean micro)
    {
        return randomBreakDelay(micro ? config.microBreakIntervalMin() : config.smallBreakIntervalMin(),
                micro ? config.microBreakIntervalMax() : config.smallBreakIntervalMax(), 60_000, 240);
    }

    private long configuredBreakDuration(boolean micro)
    {
        return randomBreakDelay(micro ? config.microBreakDurationMin() : config.smallBreakDurationMin(),
                micro ? config.microBreakDurationMax() : config.smallBreakDurationMax(), 1_000, 600);
    }

    static long randomBreakDelay(int minimum, int maximum, long unitMillis, int limit)
    {
        int first = Math.max(1, Math.min(limit, minimum));
        int second = Math.max(1, Math.min(limit, maximum));
        return ThreadLocalRandom.current().nextLong(Math.min(first, second) * unitMillis,
                Math.max(first, second) * unitMillis + 1);
    }

    private int randomPickpocketDelay(boolean firstClick)
    {
        int delay = firstClick
                ? randomBetween(FIRST_PICKPOCKET_DELAY_MIN_MS, FIRST_PICKPOCKET_DELAY_MAX_MS)
                : randomBetween(PICKPOCKET_CLICK_DELAY_MIN_MS, PICKPOCKET_CLICK_DELAY_MAX_MS);
        if (config.humanizerEnabled() && ThreadLocalRandom.current().nextInt(100) < 5)
        {
            delay += randomBetween(20, firstClick ? 61 : 81);
        }
        return delay;
    }

    private int randomFailedKnockoutRetry()
    {
        return config.humanizerEnabled()
                ? randomBetween(350, 751)
                : (int) FAILED_KNOCKOUT_RETRY_MS;
    }

    private int randomKnockoutAttemptDelay()
    {
        if (!config.humanizerEnabled())
        {
            return 80;
        }
        return picksThisKnockout >= 2
                ? randomBetween(25, 111)
                : randomBetween(70, 241);
    }

    private static int randomBetween(int minimumInclusive, int maximumExclusive)
    {
        return ThreadLocalRandom.current().nextInt(minimumInclusive, maximumExclusive);
    }

    private static long randomBetween(long minimumInclusive, long maximumExclusive)
    {
        return ThreadLocalRandom.current().nextLong(minimumInclusive, maximumExclusive);
    }

    private static long scheduleFromNow(long now, long minimumDelay, long maximumDelay)
    {
        return now + randomBetween(minimumDelay, maximumDelay);
    }

    private void maintainTargetCamera(Rs2NpcModel target)
    {
        if (target == null || target.getNpc() == null || target.getWorldLocation() == null)
        {
            return;
        }

        maintainTargetZoom();

        WorldPoint targetLocation = target.getWorldLocation();
        if (COMBAT_SAFE_TILE.equals(targetLocation))
        {
            lastCameraTargetLocation = targetLocation;
            nextCameraRefacingAt = 0;
            return;
        }

        long now = System.currentTimeMillis();
        if (nextCameraRefacingAt == 0)
        {
            nextCameraRefacingAt = now + randomBetween(
                    CAMERA_REFACING_MIN_DELAY_MS,
                    CAMERA_REFACING_MAX_DELAY_MS);
        }
        if (now < nextCameraRefacingAt || cameraTurn.isActive() || Microbot.getClient().isMenuOpen())
        {
            return;
        }

        int targetYaw = frontFacingTargetYaw(target);
        int currentYaw = Rs2Camera.getYaw();
        int delta = (targetYaw - currentYaw + 3_072) % 2_048 - 1_024;
        if (Math.abs(delta) >= CAMERA_PIVOT_THRESHOLD)
        {
            burstClickPoint = null;
            clearStandingClickAnchor();
            requestCameraTurn(targetYaw);
            log.debug("Refacing camera toward moved target: location={}, yaw={} -> {}",
                    targetLocation, currentYaw, targetYaw);
        }
        lastCameraTargetLocation = targetLocation;
        nextCameraRefacingAt = 0;
    }

    private void maintainTargetZoom()
    {
        if (!isBlackjackingCameraState(state) || !isInsideHouse() || Microbot.getClient().isMenuOpen())
        {
            return;
        }

        int currentZoom = Rs2Camera.getZoom();
        if (targetCameraZoom < 0)
        {
            targetCameraZoom = currentZoom;
            log.info("Captured blackjack camera zoom baseline: {}", targetCameraZoom);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastCameraZoomAt < CAMERA_ZOOM_COOLDOWN_MS)
        {
            return;
        }
        int desiredZoom = effectiveTargetZoom();
        int tolerance = Math.max(1, desiredZoom * CAMERA_ZOOM_TOLERANCE_PERCENT / 100);
        boolean drifted = Math.abs(currentZoom - desiredZoom) > tolerance;
        if (!restoreZoomAfterTravel)
        {
            lastCameraZoomAt = now;
            if (!drifted)
            {
                return;
            }
            restoreZoomAfterTravel = true;
            zoomRestore.reset();
        }
        if (drifted)
        {
            zoomRestore.reset();
            Rs2Camera.setZoom(desiredZoom);
            log.info("Restoring blackjack camera zoom: {} -> {}", currentZoom, desiredZoom);
        }
        else if (zoomRestore.confirm(currentZoom, desiredZoom, tolerance, now))
        {
            restoreZoomAfterTravel = false;
            log.info("Blackjack camera zoom restoration confirmed: {} (target {})", currentZoom, desiredZoom);
        }
        lastCameraZoomAt = now;
    }

    private int effectiveTargetZoom()
    {
        return visibilityCameraZoom >= 0 ? visibilityCameraZoom : targetCameraZoom;
    }

    private boolean recoverHiddenHouseTarget(Rs2NpcModel target)
    {
        return recoverHiddenHouseTarget(target, true);
    }

    private boolean recoverHiddenHouseTarget(Rs2NpcModel target, boolean allowApproach)
    {
        WorldPoint playerTile = Rs2Player.getWorldLocation();
        WorldPoint targetTile = target.getWorldLocation();
        if (!isInsideActiveHouse(playerTile) || !isInsideActiveHouse(targetTile))
        {
            return false;
        }
        if (targetAnchor(target, null) != null)
        {
            long now = System.currentTimeMillis();
            if (visibilityCameraZoom >= 0 && !Rs2Player.isMoving())
            {
                if (visibilityStableSince == 0)
                {
                    visibilityStableSince = now;
                }
                if (now - visibilityStableSince >= 8_000)
                {
                    visibilityCameraZoom = -1;
                    visibilityStableSince = 0;
                    restoreZoomAfterTravel = true;
                    zoomRestore.reset();
                    lastCameraZoomAt = 0;
                    log.info("Visible blackjack target stable; restoring preferred zoom {}", targetCameraZoom);
                }
            }
            else
            {
                visibilityStableSince = 0;
            }
            return false;
        }
        visibilityStableSince = 0;
        nextAction = "Recover off-screen blackjack target";
        long now = System.currentTimeMillis();
        if (Rs2Player.isMoving() || Microbot.getClient().isMenuOpen()
                || now < nextHiddenTargetRecoveryAt)
        {
            return true;
        }
        if (hiddenTargetIndex != target.getIndex())
        {
            hiddenTargetIndex = target.getIndex();
            hiddenTargetZoomAttempts = 0;
        }
        nextHiddenTargetRecoveryAt = now + 1_500;
        faceKnockoutTarget(target);
        Rs2Camera.setPitch(TARGET_CAMERA_PITCH);
        if (hiddenTargetZoomAttempts < 2)
        {
            int zoom = Rs2Camera.getZoom();
            visibilityCameraZoom = Math.max(zoomForHiddenTarget(zoom),
                    zoomForHiddenTarget(zoomForHiddenTarget(targetCameraZoom)));
            hiddenTargetZoomAttempts++;
            restoreZoomAfterTravel = true;
            zoomRestore.reset();
            Rs2Camera.setZoom(visibilityCameraZoom);
            lastCameraZoomAt = now;
            log.info("Off-screen blackjack target: zoom {} -> {} target={}", zoom, visibilityCameraZoom, targetTile);
        }
        else if (allowApproach && playerTile.distanceTo2D(targetTile) > 1)
        {
            if (selectedTarget() == BlackjackTarget.MENAPHITE_THUG)
            {
                walkSouthernRoute(targetTile, true, "Approach off-screen southern tent target");
            }
            else
            {
                Rs2Walker.walkFastCanvas(targetTile);
                lastInteractionAt = now;
                nextAction = "Approach off-screen Bandit inside marked house";
            }
        }
        return true;
    }

    static int zoomForHiddenTarget(int zoom)
    {
        return Math.max(200, zoom - Math.max(20, zoom / 10));
    }

    static boolean combatVisibilityRecoveryAllowed(long now, long phaseStartedAt)
    {
        return now - phaseStartedAt < 5_000;
    }

    static boolean isBlackjackingCameraState(BlackjackState state)
    {
        return state == BlackjackState.FINDING_TARGET
                || state == BlackjackState.KNOCKING_OUT
                || state == BlackjackState.PICKPOCKETING
                || state == BlackjackState.WAITING_FOR_TARGET_CLEAR;
    }

    private int frontFacingTargetYaw(Rs2NpcModel target)
    {
        if (cameraFacingTargetIndex != target.getIndex()
                || target.getAnimation() != AnimationID.HUMAN_UNCONSCIOUS)
        {
            cameraFacingTargetIndex = target.getIndex();
            cameraStandingYaw = Microbot.getClientThread().runOnClientThreadOptional(
                    () -> frontFacingCameraYaw(target.getNpc().getOrientation())).orElse(cameraStandingYaw);
        }
        return cameraStandingYaw;
    }

    static int frontFacingCameraYaw(int orientation)
    {
        // Actor: S=0,W=512,N=1024,E=1536; camera view: N=0,W=512,S=1024,E=1536.
        return (-orientation) & 2_047;
    }

    private void requestCameraTurn(int yaw)
    {
        Microbot.getClientThread().invoke(() -> {
            if (!shutdownRequested && state != BlackjackState.ERROR && state != BlackjackState.STOPPED)
            {
                cameraTurn.start(Rs2Camera.getYaw(), yaw, System.nanoTime() / 1_000_000,
                        randomBetween(900, 1_401));
            }
        });
    }

    public void onCameraClientTick()
    {
        if (shutdownRequested || state == BlackjackState.ERROR || state == BlackjackState.STOPPED
                || state == BlackjackState.WAITING_FOR_BREAK || !Microbot.isLoggedIn()
                || Microbot.pauseAllScripts.get() || Microbot.getClient().isMenuOpen())
        {
            cameraTurn.cancel();
            return;
        }
        Integer yaw = cameraTurn.sample(System.nanoTime() / 1_000_000);
        if (yaw != null)
        {
            // This writes only the current frame's target; it contains no sleeping interpolation.
            Rs2Camera.setYawInstant(yaw);
        }
    }

    static boolean continuousFeedbackTimedOut(long now, long feedbackAt, long recoveryAt)
    {
        return feedbackAt > 0 && now - feedbackAt >= 8_000 && now - recoveryAt >= 4_000;
    }

    private void maintainTopDownCamera()
    {
        long now = System.currentTimeMillis();
        if (now - lastCameraPitchAt < CAMERA_PITCH_COOLDOWN_MS || Microbot.getClient().isMenuOpen())
        {
            return;
        }
        if (Math.abs(Rs2Camera.getPitch() - TARGET_CAMERA_PITCH) > TARGET_CAMERA_PITCH_TOLERANCE)
        {
            Rs2Camera.setPitch(TARGET_CAMERA_PITCH);
            log.debug("Restoring target-facing camera pitch: {}", TARGET_CAMERA_PITCH);
        }
        lastCameraPitchAt = now;
    }

    private Point targetAnchor(Rs2NpcModel target, Point preferred)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (target.getNpc() == null || target.getNpc().getLocalLocation() == null
                    || Microbot.getClient().getTopLevelWorldView() == null)
            {
                return null;
            }
            Point feet = Perspective.localToCanvas(
                    Microbot.getClient(),
                    target.getNpc().getLocalLocation(),
                    Microbot.getClient().getTopLevelWorldView().getPlane(),
                    0);
            Shape hull = target.getNpc().getConvexHull();
            if (feet == null || hull == null)
            {
                return null;
            }

            Rectangle bounds = hull.getBounds();
            int canvasWidth = Microbot.getClient().getCanvasWidth();
            int canvasHeight = Microbot.getClient().getCanvasHeight();
            WorldPoint targetTile = target.getWorldLocation();
            boolean targetUnconscious = target.getAnimation() == AnimationID.HUMAN_UNCONSCIOUS;

            if (!isStandingAnchorTarget(target, targetTile))
            {
                clearStandingClickAnchor();
            }
            if (targetUnconscious && standingClickAnchor != null)
            {
                Point preserved = nearestSafeHullPoint(
                        hull,
                        standingClickAnchor,
                        canvasWidth,
                        canvasHeight,
                        PRONE_ANCHOR_CORRECTION_RADIUS);
                if (preserved != null)
                {
                    if (!preserved.equals(standingClickAnchor))
                    {
                        log.debug("Correcting prone target anchor: standing={} corrected={} targetIndex={}",
                                standingClickAnchor, preserved, target.getIndex());
                    }
                    return preserved;
                }
            }

            int lowerBodyTop = bounds.y + Math.max(2, bounds.height * 7 / 10);
            int lowerBodyBottom = bounds.y + Math.max(3, bounds.height * 93 / 100);

            if (preferred != null
                    && preferred.getY() >= lowerBodyTop
                    && preferred.getY() <= lowerBodyBottom
                    && hull.contains(preferred.getX(), preferred.getY())
                    && isInsideClickViewport(preferred.getX(), preferred.getY(), canvasWidth, canvasHeight))
            {
                rememberStandingClickAnchor(target, targetTile, preferred, targetUnconscious);
                return preferred;
            }

            Point currentMouse = Microbot.getClient().getMouseCanvasPosition();
            Point reference = preferred != null ? preferred : currentMouse;
            int referenceX = reference == null
                    ? bounds.x + bounds.width / 2
                    : Math.max(bounds.x + 2, Math.min(bounds.x + bounds.width - 3, reference.getX()));
            int referenceY = reference == null
                    ? lowerBodyTop + (lowerBodyBottom - lowerBodyTop) / 2
                    : Math.max(lowerBodyTop, Math.min(lowerBodyBottom, reference.getY()));

            Point nearest = null;
            long nearestDistanceSquared = Long.MAX_VALUE;
            int left = bounds.x + 2;
            int right = Math.max(left, bounds.x + bounds.width - 3);
            for (int y = lowerBodyTop; y <= lowerBodyBottom; y += 2)
            {
                for (int x = left; x <= right; x += 2)
                {
                    if (!hull.contains(x, y) || !isInsideClickViewport(x, y, canvasWidth, canvasHeight))
                    {
                        continue;
                    }
                    long dx = x - referenceX;
                    long dy = y - referenceY;
                    long distanceSquared = dx * dx + dy * dy;
                    if (distanceSquared < nearestDistanceSquared)
                    {
                        nearest = new Point(x, y);
                        nearestDistanceSquared = distanceSquared;
                    }
                }
            }
            if (nearest != null)
            {
                rememberStandingClickAnchor(target, targetTile, nearest, targetUnconscious);
                return nearest;
            }

            int[] yOffsets = {10, 13, 16, 19, 22, 25, 28};
            int[] xOffsets = {0, -3, 3, -5, 5};
            for (int yOffset : yOffsets)
            {
                for (int xOffset : xOffsets)
                {
                    Point candidate = new Point(feet.getX() + xOffset, feet.getY() - yOffset);
                    if (hull.contains(candidate.getX(), candidate.getY())
                            && isInsideClickViewport(candidate.getX(), candidate.getY(), canvasWidth, canvasHeight))
                    {
                        rememberStandingClickAnchor(target, targetTile, candidate, targetUnconscious);
                        return candidate;
                    }
                }
            }
            return null;
        }).orElse(null);
    }

    private Point nearestSafeHullPoint(Shape hull, Point reference, int canvasWidth, int canvasHeight, int radius)
    {
        if (hull == null || reference == null)
        {
            return null;
        }
        if (hull.contains(reference.getX(), reference.getY())
                && isInsideClickViewport(reference.getX(), reference.getY(), canvasWidth, canvasHeight))
        {
            return reference;
        }

        Point nearest = null;
        long nearestDistanceSquared = Long.MAX_VALUE;
        long maximumDistanceSquared = (long) radius * radius;
        int left = Math.max(TARGET_CLICK_VIEWPORT_MARGIN, reference.getX() - radius);
        int right = Math.min(canvasWidth - TARGET_CLICK_VIEWPORT_MARGIN - 1, reference.getX() + radius);
        int top = Math.max(TARGET_CLICK_VIEWPORT_MARGIN, reference.getY() - radius);
        int bottom = Math.min(canvasHeight - TARGET_CLICK_VIEWPORT_MARGIN - 1, reference.getY() + radius);
        for (int y = top; y <= bottom; y++)
        {
            for (int x = left; x <= right; x++)
            {
                if (!hull.contains(x, y))
                {
                    continue;
                }
                long dx = x - reference.getX();
                long dy = y - reference.getY();
                long distanceSquared = dx * dx + dy * dy;
                if (distanceSquared <= maximumDistanceSquared && distanceSquared < nearestDistanceSquared)
                {
                    nearest = new Point(x, y);
                    nearestDistanceSquared = distanceSquared;
                }
            }
        }
        return nearest;
    }

    private boolean isStandingAnchorTarget(Rs2NpcModel target, WorldPoint targetTile)
    {
        return standingClickAnchor != null
                && target != null
                && target.getIndex() == standingAnchorTargetIndex
                && Objects.equals(targetTile, standingAnchorTargetTile);
    }

    private void rememberStandingClickAnchor(
            Rs2NpcModel target,
            WorldPoint targetTile,
            Point anchor,
            boolean targetUnconscious)
    {
        if (targetUnconscious || target == null || targetTile == null || anchor == null)
        {
            return;
        }
        standingClickAnchor = anchor;
        standingAnchorTargetIndex = target.getIndex();
        standingAnchorTargetTile = targetTile;
        standingAnchorMenuMisses = 0;
    }

    private void noteStandingAnchorMenuMiss(Rs2NpcModel target)
    {
        if (target == null
                || target.getAnimation() != AnimationID.HUMAN_UNCONSCIOUS
                || target.getIndex() != standingAnchorTargetIndex)
        {
            return;
        }
        standingAnchorMenuMisses++;
        if (standingAnchorMenuMisses >= 3)
        {
            log.debug("Discarding preserved prone anchor after {} unresolved menu checks: targetIndex={}",
                    standingAnchorMenuMisses, target.getIndex());
            clearStandingClickAnchor();
        }
    }

    private void clearStandingClickAnchor()
    {
        standingClickAnchor = null;
        standingAnchorTargetIndex = -1;
        standingAnchorTargetTile = null;
        standingAnchorMenuMisses = 0;
    }

    private Point burstWanderAnchor(Rs2NpcModel target, Point preferred)
    {
        Point base = targetAnchor(target, preferred);
        if (base == null || preferred == null || config == null || !config.humanizerEnabled()
                || ThreadLocalRandom.current().nextInt(100) >= BURST_WANDER_CHANCE_PERCENT)
        {
            return base;
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (target.getNpc() == null)
            {
                return base;
            }
            Shape hull = target.getNpc().getConvexHull();
            if (hull == null)
            {
                return base;
            }

            Rectangle bounds = hull.getBounds();
            int canvasWidth = Microbot.getClient().getCanvasWidth();
            int canvasHeight = Microbot.getClient().getCanvasHeight();
            int wanderTop = bounds.y + Math.max(2, bounds.height * 55 / 100);
            int wanderBottom = bounds.y + Math.max(3, bounds.height * 93 / 100);
            for (int attempt = 0; attempt < 12; attempt++)
            {
                int dx = randomBetween(-BURST_WANDER_MAX_X, BURST_WANDER_MAX_X + 1);
                int directionRoll = ThreadLocalRandom.current().nextInt(100);
                int dy;
                if (directionRoll < 55)
                {
                    dy = -randomBetween(2, BURST_WANDER_MAX_UP + 1);
                }
                else if (directionRoll < 90)
                {
                    dy = randomBetween(-2, 3);
                }
                else
                {
                    dy = randomBetween(1, BURST_WANDER_MAX_DOWN + 1);
                }

                Point candidate = new Point(base.getX() + dx, base.getY() + dy);
                if (candidate.getY() >= wanderTop
                        && candidate.getY() <= wanderBottom
                        && hull.contains(candidate.getX(), candidate.getY())
                        && isInsideClickViewport(candidate.getX(), candidate.getY(), canvasWidth, canvasHeight))
                {
                    return candidate;
                }
            }
            return base;
        }).orElse(base);
    }

    private void waitForTargetClear(String reason, boolean rotateCamera)
    {
        pendingInventoryFullAt = 0;
        burstClickPoint = null;
        lastPickpocketClickAt = 0;
        targetClearReason = reason;
        targetClearRecheckAt = System.currentTimeMillis() + (rotateCamera
                ? ThreadLocalRandom.current().nextInt(700, 1_101)
                : ThreadLocalRandom.current().nextInt(100, 251));

        if (rotateCamera && !curtainCameraAdjusted)
        {
            int magnitude = ThreadLocalRandom.current().nextInt(90, 151);
            int direction = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
            int yaw = (Rs2Camera.getYaw() + direction * magnitude + 2_048) % 2_048;
            requestCameraTurn(yaw);
            curtainCameraAdjusted = true;
            log.info("Rotating camera to clear curtain obstruction: yaw={}", yaw);
        }

        transition(BlackjackState.WAITING_FOR_TARGET_CLEAR,
                rotateCamera ? "Rotate camera, then wait for clear target" : "Wait for movement to stop");
    }

    private void waitForTargetClear()
    {
        if (Rs2Player.isMoving())
        {
            nextAction = "Wait for movement to stop";
            return;
        }

        Rs2NpcModel target = currentTarget();
        if (target == null)
        {
            targetIndex = -1;
            transition(BlackjackState.FINDING_TARGET, "Reacquire target after obstruction");
            return;
        }

        long now = System.currentTimeMillis();
        if (now < targetClearRecheckAt)
        {
            return;
        }

        log.info("Retrying target after {}", targetClearReason);
        targetClearReason = "None";
        targetClearRecheckAt = 0;
        burstClickPoint = null;
        transition(BlackjackState.KNOCKING_OUT, "Retry Knock-Out after obstruction");
    }

    private boolean consumeInventoryFullResetIfIdle()
    {
        if (pendingInventoryFullAt == 0)
        {
            return false;
        }

        long now = System.currentTimeMillis();
        if (knockoutResult == KnockoutResult.PENDING)
        {
            pendingInventoryFullAt = 0;
            nextAction = now < knockoutBurstReleaseAt
                    ? "Confirm Knock-Out dispatch"
                    : "Safety pickpocket while confirming Knock-Out";
            return false;
        }
        if (now - pendingInventoryFullAt > INVENTORY_FULL_SIGNAL_MAX_AGE_MS)
        {
            pendingInventoryFullAt = 0;
            return false;
        }

        if (isPickpocketAnimationActive())
        {
            return false;
        }
        if (isRecentInteractionWithTarget(now))
        {
            nextAction = "Confirm active pickpocket before reset";
            return true;
        }

        log.info("Idle full-inventory signal confirmed after {}/2 pickpockets; retrying Knock-Out",
                picksThisKnockout);
        pendingInventoryFullAt = 0;
        burstClickPoint = null;
        transition(BlackjackState.KNOCKING_OUT, "Retry Knock-Out after idle inventory interrupt");
        return true;
    }

    private boolean isRecentInteractionWithTarget(long now)
    {
        if (now - lastPickpocketClickAt > ACTIVE_INTERACTION_GRACE_MS)
        {
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            if (player == null || !(player.getInteracting() instanceof NPC))
            {
                return false;
            }
            return ((NPC) player.getInteracting()).getIndex() == targetIndex;
        }).orElse(false);
    }

    private boolean isPickpocketAnimationActive()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player != null && player.getAnimation() == AnimationID.HUMAN_PICKPOCKET;
        }).orElse(false);
    }

    private void confirmKnockout(String signal)
    {
        if (knockoutResult != KnockoutResult.PENDING)
        {
            return;
        }
        knockoutResult = KnockoutResult.SUCCESS;
        long confirmedAt = System.currentTimeMillis();
        knockoutFailedAt = 0;
        nextPickpocketClickAt = confirmedAt + randomPickpocketDelay(true);
        knockoutBurstReleaseAt = 0;
        knockoutFallbackReleased = false;
        pickpocketBurstStartedAt = confirmedAt;
        successfulKnockouts++;
        pendingInventoryFullAt = 0;
        lastOutcome = "Knock-Out succeeded";
        log.info("Knock-Out confirmed by {} after {}ms", signal, knockoutDispatchAge(confirmedAt));
        if (wineRestockPending && waitingForRestockKnockout)
        {
            waitingForRestockKnockout = false;
            restockAfterCombatReset = false;
            transition(BlackjackState.EXITING_FOR_WINE, "Guard secured; exit for wine");
            return;
        }
        nextAction = "Spam pickpocket " + (picksThisKnockout + 1) + "/2";
    }

    private long knockoutDispatchAge(long now)
    {
        return knockoutClickIssuedAt == 0 ? -1 : Math.max(0, now - knockoutClickIssuedAt);
    }

    private boolean isPlayerInteractingWithTarget(Rs2NpcModel target)
    {
        if (target == null)
        {
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player != null
                    && player.getInteracting() instanceof NPC
                    && ((NPC) player.getInteracting()).getIndex() == target.getIndex();
        }).orElse(false);
    }

    private void armNextKnockout(Rs2NpcModel target)
    {
        if (nextKnockoutArmedAt != 0)
        {
            return;
        }

        nextKnockoutArmedAt = System.currentTimeMillis();
        secondPickpocketInteractionSeen = isPlayerInteractingWithTarget(target);
        secondPickpocketInteractionComplete = false;
        if (secondPickpocketInteractionSeen)
        {
            log.info("Second pickpocket interaction observed; pre-arming Knock-Out");
        }
    }

    private boolean isSecondPickpocketPrearmPending()
    {
        return nextKnockoutArmedAt != 0 && picksThisKnockout < 2;
    }

    private void cancelSecondPickpocketPrearm(String reason)
    {
        nextKnockoutArmedAt = 0;
        secondPickpocketInteractionSeen = false;
        secondPickpocketInteractionComplete = false;
        nextPickpocketClickAt = System.currentTimeMillis() + randomPickpocketDelay(false);
        log.info("Cancelling pre-armed Knock-Out: {}", reason);
    }

    private void observeSecondPickpocketInteraction(Rs2NpcModel target)
    {
        if (nextKnockoutArmedAt == 0 || secondPickpocketInteractionComplete)
        {
            return;
        }

        boolean interacting = isPlayerInteractingWithTarget(target);
        if (!secondPickpocketInteractionSeen)
        {
            if (interacting)
            {
                secondPickpocketInteractionSeen = true;
                log.info("Second pickpocket interaction observed; pre-arming Knock-Out");
            }
            return;
        }

        if (!interacting)
        {
            secondPickpocketInteractionComplete = true;
            log.info("Second pickpocket interaction completed; selecting Knock-Out");
        }
    }

    private boolean allowSecondPickpocketInteractionFallback(Rs2NpcModel target)
    {
        if (secondPickpocketInteractionComplete)
        {
            return true;
        }
        if (nextKnockoutArmedAt == 0
                || System.currentTimeMillis() - nextKnockoutArmedAt < SECOND_PICKPOCKET_INTERACTION_TIMEOUT_MS)
        {
            return false;
        }
        if (isPlayerInteractingWithTarget(target))
        {
            return false;
        }

        secondPickpocketInteractionComplete = true;
        log.warn("Second pickpocket interaction signal timed out after {}ms; player is idle, allowing Knock-Out fallback",
                System.currentTimeMillis() - nextKnockoutArmedAt);
        return true;
    }

    private boolean shouldEscapeCombat()
    {
        long now = System.currentTimeMillis();
        if (now < ignoreCombatUntil)
        {
            combatSignal = false;
            npcInteractionSince = 0;
            return false;
        }
        Rs2NpcModel attacker = findNpcTargetingPlayer();
        combatSignal = attacker != null;
        if (!combatSignal)
        {
            npcInteractionSince = 0;
            return false;
        }
        if (npcInteractionSince == 0)
        {
            npcInteractionSince = now;
        }
        if (now - npcInteractionSince < SUSTAINED_NPC_ATTACK_MS)
        {
            return false;
        }
        log.info("Combat reset triggered by blackjack NPC: name={} id={} index={} location={} targetingForMs={}",
                attacker.getName(), attacker.getId(), attacker.getIndex(), attacker.getWorldLocation(),
                now - npcInteractionSince);
        return true;
    }

    private boolean isNpcTargetingPlayer()
    {
        return findNpcTargetingPlayer() != null;
    }

    private Rs2NpcModel findNpcTargetingPlayer()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            if (player == null)
            {
                return null;
            }
            return Microbot.getRs2NpcCache().query().toList().stream()
                    .filter(npc -> isBlackjackAttacker(npc, player))
                    .findFirst().orElse(null);
        }).orElse(null);
    }

    boolean isBlackjackAttacker(Rs2NpcModel npc, Player player)
    {
        return player != null && npc != null && npc.getNpc() != null && isEligibleTarget(npc)
                && !(state == BlackjackState.PREPARING_SOUTHERN_TENT
                && npc.getIndex() == southernTentLureTargetIndex && npc.getNpc() == southernTentLureNpc)
                && npc.getNpc().getInteracting() == player;
    }

    private void updateHealingRequirement()
    {
        int hitpoints = currentHitpoints();
        if (hitpoints < config.healBelowPercent() && !healingRequired)
        {
            healingRequired = true;
            log.info("Healing latched at {} HP; configured below={}, heal-to={}",
                    hitpoints, config.healBelowPercent(), config.healToPercent());
        }
        else if (hitpoints >= config.healToPercent() && healingRequired)
        {
            healingRequired = false;
            log.info("Healing target reached at {} HP", hitpoints);
        }
    }

    private boolean drinkWineIfReady(String action)
    {
        if (Rs2Inventory.count(WINE_ID) == 0)
        {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastDrinkAt < DRINK_COOLDOWN_MS || !Rs2Inventory.interact(WINE_ID, "Drink"))
        {
            return false;
        }

        lastDrinkAt = now;
        lastInteractionAt = now;
        nextAction = action;
        return true;
    }

    private boolean isCombatSafetyState()
    {
        return state == BlackjackState.POSITIONING_COMBAT_RESET
                || state == BlackjackState.ESCAPING_COMBAT
                || state == BlackjackState.WAITING_FOR_COMBAT_CLEAR;
    }

    private int currentHitpoints()
    {
        return Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
    }

    private boolean readyForInteraction(long minimumDelay)
    {
        return System.currentTimeMillis() - lastInteractionAt >= minimumDelay;
    }

    private long elapsedInState()
    {
        return System.currentTimeMillis() - stateEnteredAt;
    }

    public long getStateAgeSeconds()
    {
        return Math.max(0, elapsedInState() / 1_000);
    }

    private void transition(BlackjackState newState, String action)
    {
        if (newState == BlackjackState.EXITING_FOR_WINE
                || newState == BlackjackState.RETURNING_WITH_WINE
                || newState == BlackjackState.PREPARING_SOUTHERN_TENT)
        {
            restoreZoomAfterTravel = true;
            zoomRestore.reset();
            visibilityCameraZoom = -1;
            hiddenTargetZoomAttempts = 0;
            visibilityStableSince = 0;
        }
        if (isBlackjackingCameraState(newState) && !isBlackjackingCameraState(state))
        {
            restoreZoomAfterTravel = true;
            zoomRestore.reset();
            lastCameraZoomAt = 0;
        }
        if (newState != BlackjackState.PREPARING_SOUTHERN_TENT)
        {
            restoreAutoRunAfterLure();
        }
        if (state != newState)
        {
            log.info("Blackjack state {} -> {} ({})", state, newState, action);
            if (newState == BlackjackState.KNOCKING_OUT)
            {
                knockoutAttemptReadyAt = System.currentTimeMillis() + randomKnockoutAttemptDelay();
                knockoutAimStartedAt = System.currentTimeMillis();
                knockoutAimFailures = 0;
                knockoutAimRecoveryStage = 0;
            }
            if (newState == BlackjackState.POSITIONING_COMBAT_RESET)
            {
                resetDisarmedCombatRecovery();
            }
            state = newState;
            stateEnteredAt = System.currentTimeMillis();
            wineDoorMissingSince = 0;
            wineMerchantMissingSince = 0;
        }
        nextAction = action;
        Microbot.status = "Blackjack: " + action;
    }

    private void fail(String reason)
    {
        cameraTurn.cancel();
        closeBreakPreparation();
        stopReason = reason;
        log.error("Blackjack stopped: {}", reason);
        transition(BlackjackState.ERROR, "Disable plugin and correct setup");
    }

    private int currentThievingXp()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getSkillExperience(Skill.THIEVING)).orElse(0);
    }

    private boolean ensureXpBaseline()
    {
        if (startXp >= 0)
        {
            return true;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (!Microbot.isLoggedIn())
            {
                return false;
            }
            return initializeXpBaseline(Microbot.getClient().getSkillExperience(Skill.THIEVING));
        }).orElse(false);
    }

    boolean initializeXpBaseline(int xp)
    {
        if (startXp >= 0)
        {
            return true;
        }
        // Supported targets require 45+ Thieving, so zero is an unpopulated login snapshot.
        if (xp <= 0)
        {
            return false;
        }
        lastObservedThievingXp = xp;
        startXp = xp;
        log.info("Initialized blackjack session Thieving XP baseline: {}", xp);
        return true;
    }

    public int getXpGained()
    {
        if (startXp < 0)
        {
            return 0;
        }
        return Math.max(0, currentThievingXp() - startXp);
    }

    public String getFormattedRuntime()
    {
        if (startTime == null)
        {
            return "00:00:00";
        }
        Duration duration = Duration.between(startTime, Instant.now());
        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();
        long seconds = duration.minusHours(hours).minusMinutes(minutes).getSeconds();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    @Override
    public void shutdown()
    {
        shutdownRequested = true;
        cameraTurn.cancel();
        closeBreakPreparation();
        restoreAutoRunAfterLure();
        if (!hasBlackjackEquipped()
                && combatResetPhase != CombatResetPhase.UNTRIED
                && Rs2Inventory.items(item -> item.getName() != null
                && item.getName().toLowerCase(Locale.ROOT).contains("blackjack")).findAny().isPresent())
        {
            Rs2Inventory.interact(item -> item.getName() != null
                    && item.getName().toLowerCase(Locale.ROOT).contains("blackjack"), "Wield");
        }
        outcomes.clear();
        targetIndex = -1;
        knockoutResult = KnockoutResult.NONE;
        knockoutClickIssuedAt = 0;
        resetKnockoutAimRecovery();
        knockoutBurstReleaseAt = 0;
        knockoutFallbackReleased = false;
        pickpocketBurstStartedAt = 0;
        combatSignal = false;
        burstClickPoint = null;
        clearStandingClickAnchor();
        wineRestockPending = false;
        restockAfterCombatReset = false;
        waitingForRestockKnockout = false;
        emergencyWineExit = false;
        wineInventoryPrepared = false;
        restockTargetWineCount = 0;
        humanizerStatus = "Disabled";
        humanizerMouseRecoverAt = 0;
        humanizerBreakUntil = 0;
        humanizerBreakType = "None";
        resetDisarmedCombatRecovery();
        transition(BlackjackState.STOPPED, "Enable plugin");
        super.shutdown();
    }
}
