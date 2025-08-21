package net.runelite.client.plugins.microbot.mke_wintertodt;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.StatChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.mke_wintertodt.enums.HealingMethod;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.mke_wintertodt.enums.State;
import net.runelite.client.plugins.microbot.mke_wintertodt.startup.WintertodtStartupManager;
import net.runelite.client.plugins.microbot.mke_wintertodt.startup.gear.WintertodtAxeManager;
import net.runelite.client.plugins.microbot.mke_wintertodt.startup.gear.WintertodtGearManager;
import net.runelite.client.plugins.microbot.mke_wintertodt.startup.location.WintertodtLocationManager;
import net.runelite.client.plugins.microbot.mke_wintertodt.startup.inventory.WintertodtInventoryManager;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.runelite.api.Constants.GAME_TICK_LENGTH;
import static net.runelite.api.ObjectID.*;
import static net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.isItemSelected;
import static net.runelite.client.plugins.microbot.util.player.Rs2Player.eatAt;

/**
 * Enhanced Wintertodt bot script with improved state management,
 * human-like behaviors, and robust error handling.
 *
 * Features:
 * - Intelligent state machine with proper state locking
 * - Human-like timing variations and micro-behaviors
 * - Comprehensive error handling and recovery
 * - Advanced warmth and health management
 * - Optimized resource collection and usage
 * - Anti-detection measures and natural mouse movements
 * - Integrated break system with WintertodtBreakManager support
 * - Proper break handler state management
 * - Emergency situation handling during breaks
 *
 * Break System Integration:
 * - Respects BreakHandlerScript lock states
 * - Supports Rs2Antiban features
 * - Handles break triggers from multiple sources
 * - Maintains critical functions during breaks (eating, emergency banking)
 * - Proper state transitions in and out of breaks
 *
 * Enhanced Antiban Integration:
 * - Uses Rs2Antiban firemaking setup template
 * - Proper action cooldown integration with custom logic
 * - Mouse movement randomization for natural behavior
 * - Overlay integration showing antiban status
 * - Activity-specific behavior patterns for Wintertodt
 *
 * WintertodtBreakManager:
 * - Smart custom break system with AFK and logout breaks
 * - Location-aware break triggers (only in safe areas)
 * - Configurable break intervals and durations
 * - Natural break activities and timing patterns
 * - Separate from Break Handler (for longer breaks)
 *
 * @version 2.1.0
 * @author MakeCD
 */
public class MKE_WintertodtScript extends Script {

    // State management
    public static State state = State.BANKING;
    public static boolean resetActions = false;
    private static boolean lockState = false;
    private static long lastStateChange = System.currentTimeMillis();

    // Configuration and plugin references
    static MKE_WintertodtConfig config;
    static MKE_WintertodtPlugin plugin;
    
    // Startup system
    private WintertodtStartupManager startupManager;
    private WintertodtInventoryManager inventoryManager;
    
    // Custom break management system
    private WintertodtBreakManager breakManager;

    // World locations for key areas
    private final WorldPoint BOSS_ROOM = new WorldPoint(1630, 3976, 0);
    private final WorldPoint CRATE_LOCATION = new WorldPoint(1634, 3982, 0);
    private final WorldPoint CRATE_STAND_LOCATION = new WorldPoint(1633, 3982, 0);
    private final WorldPoint SPROUTING_ROOTS = new WorldPoint(1635, 3978, 0);
    private final WorldPoint SPROUTING_ROOTS_STAND = new WorldPoint(1634, 3978, 0);
    private final WorldPoint BANK_LOCATION = new WorldPoint(1640, 3944, 0);

    // Reward Cart Looting Locations
    private final WorldPoint REWARD_CART_WEST = new WorldPoint(1635, 3944, 0);
    private final WorldPoint REWARD_CART_EAST = new WorldPoint(1637, 3944, 0);
    private final WorldPoint REWARD_CART_CENTER = new WorldPoint(1636, 3944, 0);

    // Game state tracking
    private GameObject currentBrazier;
    private boolean isInitialized = false;
    private boolean startupCompleted = false;
    private Random random = new Random();
    private boolean wasOnBreak = false;
    private boolean wasOnBreakHandlerBreak = false;
    private boolean worldChecked = false; // Track if we've already checked/hopped to Wintertodt world

    // Wintertodt round timer tracking
    private long roundEndTime = -1;
    private long nextRoundStartTime = -1;
    private boolean mouseMovedOffscreenForRound = false;
    private boolean hoveredForNextRound = false;
    private boolean isHoverBeforeStartTimeCalculated = false;
    private int hoverBeforeStartTime = 0; // Time in ms before round start to begin hovering
    private int lastKnownRemainingSeconds = -1;

    // Spam clicking variables for natural game start behavior
    private boolean spamClickingActive = false;
    private long spamClickStartTime = 0;
    private long spamClickEndTime = 0;
    private GameObject spamClickTarget = null;
    private int spamClicksPerformed = 0;
    private long lastSpamClick = 0;

    // For historical round time tracking
    private final java.util.List<Long> previousRoundDurations = new java.util.LinkedList<>();
    private long currentRoundStartTime = 0;

    // Flag to prioritize brazier lighting at round start
    private static boolean shouldPriorizeBrazierAtStart = false;

    // For overlay
    public static double historicalEstimateSecondsLeft = 0;

    // --- Action Timers based on XP Drops ---
    private static long lastWoodcuttingXpDropTime = 0;
    private static long lastFletchingXpDropTime = 0;
    private static long lastFiremakingXpDropTime = 0;

    // --- live HP-rate tracking & action duration averages --------------
    private int     prevWintertodtHp     = -1;
    private long    prevHpTimestamp      = 0;
    private double  hpPerSecond          = 0;      // averaged team DPS
    public static double  estimatedSecondsLeft = 999;
    private double addToEstimatedTimePerCycleMs = 1200;

    // average action durations (ms) – refined live while the script runs
    public static double  avgChopMs   = 2800;
    public static double  avgFletchMs = 2200;
    public static double  avgFeedMs   = 1600;

    public static double cycleTimeSec = 0;
    public static int maxCyclesPossible = 0;

    // --- Action Plan ---
    private static int targetRootsForThisRun = 0;
    public static int rootsToChopGoal = 0;
    public static int currentBurnableCount = 0;

    /* per-run fletch / feed goals & progress ----------------------*/
    public static int fletchGoal       = 0;
    public static int feedGoal         = 0;
    public static int fletchedThisRun  = 0;
    public static int fedThisRun       = 0;

    private static final double SAFETY_BUFFER_SEC = 5; // keep this free for walking, delays, flame-out, etc.
    
    /* How much extra we chop on top of the strict time calculation
       (makes sure we don't run out of logs when estimates are a bit optimistic) */
    public static final int EXTRA_ROOTS_BUFFER = 2;
    // --------------------------------------------------------------------------

    // Performance tracking
    private long lastPerformanceCheck = 0;
    private int actionsPerformed = 0;
    private int consecutiveFailures = 0;
    
    // Exit failure tracking for banking
    private int consecutiveExitFailures = 0;
    private static final int MAX_EXIT_FAILURES = 10;
    
    // Mouse and camera movement tracking
    private long lastMouseMovement = 0;
    private long lastCameraMovement = 0;

    // ────────────── HEALING STRATEGY VARIABLES ──────────────────────────────────
    private boolean usesPotions = false;
    private boolean autoAdjustedPotionUsage = false;

    // ────────────── REWARD CART LOOTING VARIABLES ────────────────────────────────
    public static boolean isLootingRewards = false;
    public static int targetRewardThreshold = 0; // Calculated with gaussian random
    public static int currentRewardCartRewards = 0; // Rewards owed from chat message
    /**
     * Flag that indicates the current reward cart has been fully emptied. We
     * use this to avoid needlessly walking back to the cart after we have
     * already confirmed that there are no rewards left.
     */
    private static boolean rewardCartExhausted = false;
    private long lastRewardCartInteraction = 0;
    private boolean wasInWintertodtBeforeRewards = false;
    private static final String REWARD_CART_NO_REWARDS_TEXT = "There are no rewards here for you.";
    private static final String REWARD_CART_FINISHED_TEXT = "You think you've taken as much as you're owed from the reward";
    private static final int REWARD_CART_WIDGET_PARENT = 229;
    private static final int REWARD_CART_WIDGET_CHILD = 3;

    /* Default round length (ms) used until we have real data */
    private static final long DEFAULT_ROUND_DURATION_MS = 250_000;

    /** max distance (tiles) from our brazier at which chopping is allowed */
    private static final int CHOPPING_RADIUS = 10;

    // Object IDs for rejuvenation potion creation
    private static final int CRATE_OBJECT_ID = 29320; // Crate for concoctions
    private static final int SPROUTING_ROOTS_OBJECT_ID = 29315; // Sprouting roots for herbs

    private final WorldPoint BREWMA_NPC_LOCATION = new WorldPoint(1635, 3986, 0);
    private final WorldPoint BREWMA_NPC_INTERACT_LOCATION = new WorldPoint(1634, 3986, 0);

    /**
     * Inner class to hold comprehensive game state information.
     */
    private static class GameState {
        boolean wintertodtRespawning;
        boolean isWintertodtAlive;
        int playerWarmth;
        GameObject brazier;
        GameObject brokenBrazier;
        GameObject burningBrazier;
        boolean needBanking;
        boolean needPotions = false; // For rejuvenation potion logic
        int wintertodtHp = -1;
        boolean inventoryFull;
        boolean hasItemsToBurn;
        boolean hasRootsToFletch;
    }
    
    /**
     * Executes logic specific to the current state.
     *
     * @param gameState Current game state
     */
    private void executeStateLogic(GameState gameState) {
        switch (state) {
            case BANKING:
                handleBankingState(gameState);
                break;
            case ENTER_ROOM:
                handleEnterRoomState(gameState);
                break;
            case WAITING:
                handleWaitingState(gameState);
                break;
            case LIGHT_BRAZIER:
                handleLightBrazierState(gameState);
                break;
            case CHOP_ROOTS:
                handleChopRootsState(gameState);
                break;
            case FLETCH_LOGS:
                handleFletchLogsState(gameState);
                break;
            case BURN_LOGS:
                handleBurnLogsState(gameState);
                break;
            case GET_CONCOCTIONS:
                handleGetConcoctionsState(gameState);
                break;
            case GET_HERBS:
                handleGetHerbsState(gameState);
                break;
            case MAKE_POTIONS:
                handleMakePotionsState(gameState);
                break;
            case WALKING_TO_SAFE_SPOT_FOR_BREAK:
                handleWalkingToSafeSpotForBreakState(gameState);
                break;
            case EXITING_FOR_REWARDS:
                handleExitingForRewardsState(gameState);
                break;
            case WALKING_TO_REWARDS_BANK:
                handleWalkingToRewardsBankState(gameState);
                break;
            case BANKING_FOR_REWARDS:
                handleBankingForRewardsState(gameState);
                break;
            case WALKING_TO_REWARD_CART:
                handleWalkingToRewardCartState(gameState);
                break;
            case LOOTING_REWARD_CART:
                handleLootingRewardCartState(gameState);
                break;
            case RETURNING_FROM_REWARDS:
                handleReturningFromRewardsState(gameState);
                break;
        }
    }

    /* Returns the closest Bruma root that is on the same side as the
       selected brazier (<= 8 tiles from that brazier). */
    private GameObject getOwnSideRoot()
    {
        WorldPoint ref = config.brazierLocation().getBRAZIER_LOCATION();
        return Rs2GameObject.getGameObjects(10).stream()
                .filter(o -> o.getId() == ObjectID.BRUMA_ROOTS)
                .filter(o -> o.getWorldLocation().distanceTo(ref) <= 10)
                .min(java.util.Comparator.comparingInt(o -> o.getWorldLocation()
                                                           .distanceTo(ref)))
                .orElse(null);
    }

    // ---------------------------------------------------------------

    // ----------------  event hook  ---------------------------------
    public static void onStatChanged(StatChanged event)
    {
        long now = System.currentTimeMillis();
        long duration;

        switch (event.getSkill()) {
            case WOODCUTTING:
                if (state == State.CHOP_ROOTS)
                {
                    // ---------- root counter -----------
                    if (targetRootsForThisRun > 0)
                        rootsChoppedThisRun++;

                    if (rootsChoppedThisRun >= targetRootsForThisRun && targetRootsForThisRun > 0)
                    {
                        setLockState(State.CHOP_ROOTS, false);
                        changeState(State.WAITING);
                        targetRootsForThisRun = 0;
                        rootsChoppedThisRun   = 0;
                        return;                           // stop further processing
                    }
                    // ----------------------------------------

                    if (lastWoodcuttingXpDropTime > 0)
                    {
                        duration = now - lastWoodcuttingXpDropTime;
                        if (duration > 600 && duration < 10000)
                            noteActionDuration("CHOP", duration);
                    }
                    lastWoodcuttingXpDropTime = now;
                }
                break;
            case FLETCHING:
                if (state == State.FLETCH_LOGS) {
                    if (fletchGoal > 0) fletchedThisRun++;   // progress
                    if (lastFletchingXpDropTime > 0) {
                        duration = now - lastFletchingXpDropTime;
                        if (duration > 600 && duration < 10000) { // Sanity check
                            noteActionDuration("FLETCH", duration);
                        }
                    }
                    lastFletchingXpDropTime = now;
                }
                break;
            case FIREMAKING:
                if (state == State.BURN_LOGS) {
                    if (feedGoal > 0)  fedThisRun++;        // progress
                    if (lastFiremakingXpDropTime > 0) {
                        duration = now - lastFiremakingXpDropTime;
                        if (duration > 600 && duration < 10000) { // Sanity check
                            noteActionDuration("FEED", duration);
                        }
                    }
                    lastFiremakingXpDropTime = now;
                }
                break;
        }
    }
    // ---------------------------------------------------------------

    // --------------- planning a new run ----------------------------
    public  static int rootsChoppedThisRun   = 0;


    /**
     * Enum for tracking what caused fletching to stop
     */
    public enum FletchingInterruptType {
        DAMAGE_COLD("Damaged by Wintertodt Cold"),
        DAMAGE_SNOWFALL("Damaged by Wintertodt Snowfall"), 
        DAMAGE_BRAZIER("Brazier Shattered"),
        OUT_OF_ROOTS("All roots fletched"),
        INVENTORY_FULL("Inventory became full"),
        ROUND_ENDED("Wintertodt round ended"),
        NO_MORE_ROOTS("No more roots to fletch"),
        PLAYER_DIED("Player died"),
        PLAYER_MOVED("Player started moving"),
        PLAYER_ATE("Player ate food"),
        BRAZIER_WENT_OUT("Brazier went out"),
        BRAZIER_BROKEN("Brazier broke"),
        MANUAL_STOP("Manually stopped"),
        TIMEOUT("Fletching timeout"),
        ANIMATION_TIMEOUT("No fletching animation for 3+ seconds"),
        UNKNOWN("Unknown cause");
        
        private final String description;
        
        FletchingInterruptType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }

    /**
     * Enum for tracking what caused feeding to stop
     */
    public enum FeedingInterruptType {
        DAMAGE_COLD("Damaged by Wintertodt Cold"),
        DAMAGE_SNOWFALL("Damaged by Wintertodt Snowfall"), 
        DAMAGE_BRAZIER("Brazier Shattered"),
        OUT_OF_ITEMS("All items fed"),
        INVENTORY_EMPTY("No more items to feed"),
        ROUND_ENDED("Wintertodt round ended"),
        NO_BURNING_BRAZIER("Brazier went out"),
        PLAYER_DIED("Player died"),
        PLAYER_MOVED("Player started moving"),
        PLAYER_ATE("Player ate food"),
        BRAZIER_WENT_OUT("Brazier went out"),
        BRAZIER_BROKEN("Brazier broke"),
        MANUAL_STOP("Manually stopped"),
        TIMEOUT("Feeding timeout"),
        ANIMATION_TIMEOUT("No feeding animation for 3+ seconds"),
        WARMTH_TOO_LOW("Warmth too low, need to eat"),
        UNKNOWN("Unknown cause");
        
        private final String description;
        
        FeedingInterruptType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }

    /**
     * Class to track fletching state robustly
     */
    public static class FletchingState {
        private boolean isActive = false;
        private long startTime = 0;
        private int rootsAtStart = 0;
        private WorldPoint startLocation = null;
        private double healthAtStart = 0;
        private int warmthAtStart = 0;
        private FletchingInterruptType lastInterruptType = null;
        private long lastInterruptTime = 0;
        
        public void startFletching() {
            isActive = true;
            startTime = System.currentTimeMillis();
            rootsAtStart = Rs2Inventory.count(ItemID.BRUMA_ROOT);
            startLocation = Rs2Player.getWorldLocation();
            healthAtStart = Rs2Player.getHealthPercentage();
            warmthAtStart = getWarmthLevel();
            lastInterruptType = null;
            Microbot.log("Fletching started - tracking " + rootsAtStart + " roots");
        }
        
        public void stopFletching(FletchingInterruptType reason) {
            if (isActive) {
                isActive = false;
                lastInterruptType = reason;
                lastInterruptTime = System.currentTimeMillis();
                long duration = lastInterruptTime - startTime;
                Microbot.log("Fletching stopped: " + reason.getDescription() + " (Duration: " + duration + "ms)");
            }
        }
        
        public boolean isActive() {
            return isActive;
        }
        
        public long getStartTime() {
            return startTime;
        }
        
        public int getRootsAtStart() {
            return rootsAtStart;
        }
        
        public WorldPoint getStartLocation() {
            return startLocation;
        }
        
        public double getHealthAtStart() {
            return healthAtStart;
        }

        public int getWarmthAtStart() {
            return warmthAtStart;
        }
        
        public FletchingInterruptType getLastInterruptType() {
            return lastInterruptType;
        }
        
        public long getDuration() {
            return isActive ? System.currentTimeMillis() - startTime : lastInterruptTime - startTime;
        }
    }

    /**
     * Class to track feeding state robustly
     */
    public static class FeedingState {
        private boolean isActive = false;
        private long startTime = 0;
        private int itemsAtStart = 0;
        private WorldPoint startLocation = null;
        private double healthAtStart = 0;
        private int warmthAtStart = 0;
        private FeedingInterruptType lastInterruptType = null;
        private long lastInterruptTime = 0;
        
        public void startFeeding() {
            isActive = true;
            startTime = System.currentTimeMillis();
            itemsAtStart = Rs2Inventory.count(ItemID.BRUMA_ROOT) + Rs2Inventory.count(ItemID.BRUMA_KINDLING);
            startLocation = Rs2Player.getWorldLocation();
            healthAtStart = Rs2Player.getHealthPercentage();
            warmthAtStart = getWarmthLevel();
            lastInterruptType = null;
            Microbot.log("Feeding started - tracking " + itemsAtStart + " items");
        }
        
        public void stopFeeding(FeedingInterruptType reason) {
            if (isActive) {
                isActive = false;
                lastInterruptType = reason;
                lastInterruptTime = System.currentTimeMillis();
                long duration = lastInterruptTime - startTime;
                Microbot.log("Feeding stopped: " + reason.getDescription() + " (Duration: " + duration + "ms)");
            }
        }
        
        public boolean isActive() {
            return isActive;
        }
        
        public long getStartTime() {
            return startTime;
        }
        
        public int getItemsAtStart() {
            return itemsAtStart;
        }
        
        public WorldPoint getStartLocation() {
            return startLocation;
        }
        
        public double getHealthAtStart() {
            return healthAtStart;
        }
        
        public int getWarmthAtStart() {
            return warmthAtStart;
        }
        
        public FeedingInterruptType getLastInterruptType() {
            return lastInterruptType;
        }
        
        public long getDuration() {
            return isActive ? System.currentTimeMillis() - startTime : lastInterruptTime - startTime;
        }
    }

    private static FletchingState fletchingState = new FletchingState();
    private static FeedingState feedingState = new FeedingState();

    /**
     * Comprehensive fletching state checker that only returns false when specific stopping conditions are met
     */
    private boolean isCurrentlyFletching() {
        // If we haven't started fletching, return false
        if (!fletchingState.isActive()) {
            return false;
        }
        // Check all possible interruption conditions
        FletchingInterruptType interruptType = checkFletchingInterruptions();
        if (interruptType != null) {
            fletchingState.stopFletching(interruptType);
            return false;
        }
        // If no interruptions detected and we started fletching, we're still fletching
        return true;
    }

    /**
     * Checks for all possible fletching interruption conditions
     */
    private FletchingInterruptType checkFletchingInterruptions() {
        // Check if we ran out of roots
        int currentRoots = Rs2Inventory.count(ItemID.BRUMA_ROOT);
        if (currentRoots == 0) {
            return FletchingInterruptType.OUT_OF_ROOTS;
        }
        
        // Check if round ended
        GameState gameState = analyzeGameState();
        if (!gameState.isWintertodtAlive || gameState.wintertodtHp == 0) {
            return FletchingInterruptType.ROUND_ENDED;
        }
        
        // Check if we have no more roots to fletch
        if (Rs2Inventory.count(ItemID.BRUMA_ROOT) == 0) {
            return FletchingInterruptType.NO_MORE_ROOTS;
        }
        
        // Check if player moved from start location
        WorldPoint currentLocation = Rs2Player.getWorldLocation();
        if (!fletchingState.getStartLocation().equals(currentLocation)) {
            return FletchingInterruptType.PLAYER_MOVED;
        }
        
        // Check if player ate (warmth increased) (Foods usually increase at least 30 warmth)
        if (getWarmthLevel() > fletchingState.getWarmthAtStart() + 29) {
            return FletchingInterruptType.PLAYER_ATE;
        }
        
        // Check for fletching animation timeout (no animation seen for 3+ seconds)
        long currentTime = System.currentTimeMillis();
        if (lastFletchingAnimationTime > 0 && 
            currentTime - lastFletchingAnimationTime > FLETCHING_ANIMATION_TIMEOUT) {
            return FletchingInterruptType.ANIMATION_TIMEOUT;
        }
        
        // Check for timeout (fletching taking too long)
        long duration = fletchingState.getDuration();
        if (duration > 60000) { // 60 seconds timeout
            return FletchingInterruptType.TIMEOUT;
        }
        
        return null; // No interruption detected
    }

    /**
     * Comprehensive feeding state checker that only returns false when specific stopping conditions are met
     */
    private boolean isCurrentlyFeeding() {
        // If we haven't started feeding, return false
        if (!feedingState.isActive()) {
            return false;
        }
        
        // Check all possible interruption conditions
        FeedingInterruptType interruptType = checkFeedingInterruptions();
        if (interruptType != null) {
            feedingState.stopFeeding(interruptType);
            return false;
        }
        
        // If no interruptions detected and we started feeding, we're still feeding
        return true;
    }

    /**
     * Checks for all possible feeding interruption conditions
     */
    private FeedingInterruptType checkFeedingInterruptions() {
        // Check if we ran out of items to feed
        int currentItems = Rs2Inventory.count(ItemID.BRUMA_ROOT) + Rs2Inventory.count(ItemID.BRUMA_KINDLING);
        if (currentItems == 0) {
            return FeedingInterruptType.OUT_OF_ITEMS;
        }
        
        // Check if round ended
        GameState gameState = analyzeGameState();
        if (!gameState.isWintertodtAlive || gameState.wintertodtHp == 0) {
            return FeedingInterruptType.ROUND_ENDED;
        }
        
        // Check if brazier went out or is broken
        if (gameState.burningBrazier == null) {
            if (gameState.brokenBrazier != null) {
                return FeedingInterruptType.BRAZIER_BROKEN;
            } else {
                return FeedingInterruptType.BRAZIER_WENT_OUT;
            }
        }
        
        // Check if player moved from start location
        WorldPoint currentLocation = Rs2Player.getWorldLocation();
        if (!feedingState.getStartLocation().equals(currentLocation)) {
            return FeedingInterruptType.PLAYER_MOVED;
        }
        
        // Check if player ate (warmth increased)
        if (getWarmthLevel() > feedingState.getWarmthAtStart() + 29) {
            return FeedingInterruptType.PLAYER_ATE;
        }
        
        // Check for feeding animation timeout (no animation seen for 3+ seconds)
        long currentTime = System.currentTimeMillis();
        if (lastFeedingAnimationTime > 0 && 
            currentTime - lastFeedingAnimationTime > FEEDING_ANIMATION_TIMEOUT) {
            return FeedingInterruptType.ANIMATION_TIMEOUT;
        }
        
        // Check for timeout (feeding taking too long)
        long duration = feedingState.getDuration();
        if (duration > 45000) { // 45 seconds timeout
            return FeedingInterruptType.TIMEOUT;
        }
        
        // Check if warmth is too low (need to eat)
        int currentWarmth = getWarmthLevel();
        if (currentWarmth <= config.eatAtWarmthLevel()) {
            return FeedingInterruptType.WARMTH_TOO_LOW;
        }
        
        return null; // No interruption detected
    }

    /**
     * Chat message handler for fletching and feeding interruptions
     */
    public static void handleChatInterruption(String message) {
        // Handle fletching interruptions
        if (fletchingState.isActive()) {
            FletchingInterruptType fletchingInterruptType = null;
            
            if (message.startsWith("The cold of")) {
                fletchingInterruptType = FletchingInterruptType.DAMAGE_COLD;
            } else if (message.startsWith("The freezing cold attack")) {
                fletchingInterruptType = FletchingInterruptType.DAMAGE_SNOWFALL;
            } else if (message.startsWith("The brazier is broken and shrapnel")) {
                fletchingInterruptType = FletchingInterruptType.DAMAGE_BRAZIER;
            } else if (message.startsWith("Your inventory is too full")) {
                fletchingInterruptType = FletchingInterruptType.INVENTORY_FULL;
            } else if (message.startsWith("You have run out of bruma roots")) {
                fletchingInterruptType = FletchingInterruptType.OUT_OF_ROOTS;
            } else if (message.startsWith("You fix the brazier")) {
                // Brazier was fixed - allow fletching to resume
                Microbot.log("Brazier fixed - fletching can resume");
                return; // Don't stop fletching, just log
            } else if (message.startsWith("You light the brazier")) {
                // Brazier was lit - allow fletching to resume
                Microbot.log("Brazier lit - fletching can resume");
                return; // Don't stop fletching, just log
            }
            
            if (fletchingInterruptType != null) {
                fletchingState.stopFletching(fletchingInterruptType);
                resetActions = true;
                // Reset animation tracking when interrupted
                lastFletchingAnimationTime = 0;
            }
        }
        
        // Handle feeding interruptions
        if (feedingState.isActive()) {
            FeedingInterruptType feedingInterruptType = null;
            
            if (message.startsWith("The cold of")) {
                feedingInterruptType = FeedingInterruptType.DAMAGE_COLD;
            } else if (message.startsWith("The freezing cold attack")) {
                feedingInterruptType = FeedingInterruptType.DAMAGE_SNOWFALL;
            } else if (message.startsWith("The brazier is broken and shrapnel")) {
                feedingInterruptType = FeedingInterruptType.DAMAGE_BRAZIER;
            } else if (message.startsWith("The brazier has gone out")) {
                feedingInterruptType = FeedingInterruptType.BRAZIER_WENT_OUT;
            } else if (message.startsWith("Your inventory is too full")) {
                feedingInterruptType = FeedingInterruptType.INVENTORY_EMPTY;
            } else if (message.startsWith("You have run out of bruma")) {
                feedingInterruptType = FeedingInterruptType.OUT_OF_ITEMS;
            } else if (message.startsWith("You fix the brazier")) {
                // Brazier was fixed - allow feeding to resume
                Microbot.log("Brazier fixed - feeding can resume");
                return; // Don't stop feeding, just log
            } else if (message.startsWith("You light the brazier")) {
                // Brazier was lit - allow feeding to resume
                Microbot.log("Brazier lit - feeding can resume");
                return; // Don't stop feeding, just log
            }
            
            if (feedingInterruptType != null) {
                feedingState.stopFeeding(feedingInterruptType);
                resetActions = true;
                // Reset animation tracking when interrupted
                lastFeedingAnimationTime = 0;
            }
        }

        // Handle reward cart rewards tracking
        if (message.contains("You're now owed") && message.contains("rewards")) {
            parseRewardCartMessage(message);
        }
    }

    /**
     * Parses reward cart message to extract the number of rewards owed
     * Message format: "You're owed an additional X rewards from the reward cart. You're now owed Y rewards."
     */
    private static void parseRewardCartMessage(String message) {
        try {
            // Look for the pattern "You're now owed X rewards"
            String[] parts = message.split("You're now owed ");
            if (parts.length >= 2) {
                String rewardPart = parts[1].trim();
                // Extract the number before " reward" or " rewards"
                String[] rewardSplit = rewardPart.split(" reward");
                if (rewardSplit.length >= 1) {
                    String numberStr = rewardSplit[0].trim();
                    int newRewardCount = Integer.parseInt(numberStr);
                    
                    if (newRewardCount != currentRewardCartRewards) {
                        int previousRewards = currentRewardCartRewards;
                        currentRewardCartRewards = newRewardCount;
                        
                        Microbot.log("Reward Cart: " + previousRewards + " -> " + currentRewardCartRewards + " rewards owed");
                        
                        // Calculate threshold if not set yet
                        if (targetRewardThreshold == 0) {
                            calculateRewardThreshold();
                        }
                    }
                }
            }
        } catch (Exception e) {
            Microbot.log("Failed to parse reward cart message: " + message + " - " + e.getMessage());
        }
    }

    /**
     * Changes the bot's current state with optional state locking.
     * Includes logging and timing for debugging purposes.
     *
     * @param newState The state to transition to
     */
    private static void changeState(State newState) {
        changeState(newState, false);
    }

    /**
     * Changes the bot's current state with optional state locking.
     *
     * @param newState The state to transition to
     * @param lock Whether to lock the state against changes
     */
    private static void changeState(State newState, boolean lock) {
        // Prevent state changes if currently locked or trying to change to same state
        if (state == newState || lockState) {
            return;
        }
        
        // Prevent reward cart states from being overridden by non-reward states
        if (isLootingRewards && isRewardCartState(state) && !isRewardCartState(newState)) {
            System.out.println("Reward cart state change blocked: " + state + " -> " + newState + " (reward looting in progress)");
            return;
        }

        /* ────────── graceful exit from FLETCH_LOGS ────────── */
        if (state == State.FLETCH_LOGS && fletchingState.isActive()) {
            // Make sure the tracking object is closed properly
            fletchingState.stopFletching(FletchingInterruptType.MANUAL_STOP);
            // Reset animation watchdog so stale timestamps aren't carried over
            lastFletchingAnimationTime = 0;
            // Ensure we never carry the lock of the previous state forward
            setLockState(State.FLETCH_LOGS, false);
        }
        
        /* ────────── graceful exit from BURN_LOGS ─────────── */
        if (state == State.BURN_LOGS && feedingState.isActive()) {
            // Make sure the feeding tracking object is closed properly
            feedingState.stopFeeding(FeedingInterruptType.MANUAL_STOP);
            // Reset animation watchdog so stale timestamps aren't carried over
            lastFeedingAnimationTime = 0;
            // Ensure we never carry the lock of the previous state forward
            setLockState(State.BURN_LOGS, false);
        }
        /* ─────────────────────────────────────────────────────────── */

        // Reset XP timers on state change to not carry over duration calculations
        if (state == State.CHOP_ROOTS)  lastWoodcuttingXpDropTime = 0;
        if (state == State.FLETCH_LOGS) lastFletchingXpDropTime   = 0;
        if (state == State.BURN_LOGS)   lastFiremakingXpDropTime  = 0;

        System.out.println(String.format("[%d] State transition: %s -> %s %s",
                System.currentTimeMillis(), state, newState, lock ? "(LOCKED)" : ""));

        state = newState;
        resetActions = true;
        lastStateChange = System.currentTimeMillis();
        setLockState(newState, lock);
    }

    /**
     * Sets the state lock to prevent unwanted state changes during critical operations.
     *
     * @param currentState The current state being locked/unlocked
     * @param lock Whether to enable or disable the lock
     */
    private static void setLockState(State currentState, boolean lock) {
        if (lockState == lock) return;

        lockState = lock;
        System.out.println(String.format("State %s has %s state locking",
                currentState.toString(), lock ? "ENABLED" : "DISABLED"));
    }

    /**
     * Determines if the bot should start fletching roots based on configuration and inventory.
     *
     * @return true if should fletch roots, false otherwise
     */
    private static boolean shouldFletchRoots() {
        // Check if fletching is enabled in config
        if (!config.fletchRoots()) {
            return false;
        }

        // Check if we have roots to fletch
        if (!Rs2Inventory.hasItem(ItemID.BRUMA_ROOT)) {
            setLockState(State.FLETCH_LOGS, false);
            return false;
        }

        // Check if we have a knife
        if (!Rs2Inventory.hasItem(WintertodtInventoryManager.knifeToUse)) {
            System.out.println("Fletching enabled but no knife found in inventory");
            return false;
        }

        changeState(State.FLETCH_LOGS, true);
        return true;
    }

    /**
     * Handles hitsplat applied events to trigger action resets when player takes damage.
     *
     * @param hitsplatApplied The hitsplat event
     */
    public static void onHitsplatApplied(HitsplatApplied hitsplatApplied) {
        Actor actor = hitsplatApplied.getActor();

        // Only process if it's the local player taking damage
        if (actor != Microbot.getClient().getLocalPlayer()) {
            return;
        }

        System.out.println("Player took damage");
        
        
        resetActions = true;
    }

    /**
     * Main script execution method with comprehensive error handling and state management.
     *
     * @param config Configuration object
     * @param plugin Plugin instance
     * @return true if script started successfully
     */
    public boolean run(MKE_WintertodtConfig config, MKE_WintertodtPlugin plugin) {
        // COMPLETE STATE RESET - Start completely fresh
        resetAllScriptState();
        
        // Reset lastStateChange immediately when plugin starts
        lastStateChange = System.currentTimeMillis();
        Microbot.log("Plugin started - all state reset to fresh start");
        
        // Store references for this run
        MKE_WintertodtScript.config = config;
        MKE_WintertodtScript.plugin = plugin;
        
        // Initialize startup manager
        startupManager = new WintertodtStartupManager(config);
        inventoryManager = startupManager.getInventoryManager();
        
        // Initialize break manager
        breakManager = new WintertodtBreakManager(config);
        
        // Always configure antiban on every plugin start
        configureAntibanSettings();
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                // Update break manager BEFORE login check so logout breaks can end
                if (!wasOnBreakHandlerBreak && breakManager != null && breakManager.update()) {
                    if (Microbot.isLoggedIn()) {
                        setLockState(state, false); // Force unlock current state
                        changeState(State.WALKING_TO_SAFE_SPOT_FOR_BREAK);
                    }
                    return;
                }

                // Pre-execution checks
                if (!Microbot.isLoggedIn()) {
                    // Only spam logs if we're not in an intentional logout break
                    if (!WintertodtBreakManager.isLogoutBreakActive() && !BreakHandlerScript.isBreakActive() && !wasOnBreak) {
                        isInitialized = false;
                        lastStateChange = System.currentTimeMillis(); // Reset on logout
                        Microbot.log("Player logged out - reset initialization flag");
                    } else if (BreakHandlerScript.isBreakActive()){
                        wasOnBreak = true;
                        wasOnBreakHandlerBreak = true;
                    } else {
                        wasOnBreak = true;
                    }
                    return;
                }

                if (!super.run()) return;

                // Performance monitoring
                long loopStartTime = System.currentTimeMillis();

                // Handle startup sequence first
                if (!startupCompleted) {
                    if (!executeStartupSequence()) {
                        return;
                    }
                }
                
                // Initialize script if needed
                if (!isInitialized) {
                    if (!initializeScript(config, plugin)) {
                        return;
                    }
                }

                if (!breakManager.isInSafeLocationForBreak()) {
                    if (!BreakHandlerScript.isLockState()) {
                        BreakHandlerScript.setLockState(true);
                        Microbot.log("Locking break handler");
                    }
                }

                // Gather current game state
                GameState gameState = analyzeGameState();

                // Emergency situation handling
                if (handleEmergencySituations(gameState)) {
                    return;
                }

                if (shouldPauseForBreaks()) {
                    if (!Rs2AntibanSettings.actionCooldownActive) {
                        wasOnBreak = true;
                    }
                    return; // Pause script if any break is active
                }

                if (wasOnBreak) {
                    Microbot.log("Resuming from break");
                    if (wasOnBreakHandlerBreak) {
                        Microbot.log("Sleeping for 10 seconds to ensure client is fully loaded");
                        sleep(10000);
                        wasOnBreakHandlerBreak = false;
                    } else {
                        sleep(2000);
                    }
                    lastStateChange = System.currentTimeMillis();
                    worldChecked = false; // Reset world check after break (might be on different world)
                    resetActionPlanning(); // Clear outdated action plans after break
                    Microbot.log("Resuming from break, resetting state timer, world check, and action plan.");
                    wasOnBreak = false;
                    return;
                }

                // Human-like behaviors
                performHumanLikeBehaviors();

                // Core maintenance tasks
                performMaintenanceTasks(gameState);

                // Main game logic based on current state
                executeMainGameLogic(gameState);

                // Performance tracking
                trackPerformance(loopStartTime);

            } catch (Exception ex) {
                handleScriptException(ex);
            }
        }, 0, 60, TimeUnit.MILLISECONDS);

        return true;
    }

    /**
     * Completely resets all script state to ensure fresh start.
     */
    private void resetAllScriptState() {
        Microbot.log("=== COMPLETE SCRIPT STATE RESET ===");
        
        // Reset main state variables
        state = State.BANKING;
        resetActions = false;
        lockState = false;
        lastStateChange = System.currentTimeMillis();
        
        // Reset initialization flags
        isInitialized = false;
        startupCompleted = false;
        
        // Reset round timer tracking
        roundEndTime = -1;
        nextRoundStartTime = -1;
        mouseMovedOffscreenForRound = false;
        hoveredForNextRound = false;
        isHoverBeforeStartTimeCalculated = false;
        hoverBeforeStartTime = 0;
        lastKnownRemainingSeconds = -1;
        currentRoundStartTime = 0;
        previousRoundDurations.clear();
        previousRoundDurations.add(DEFAULT_ROUND_DURATION_MS);
        
        // Reset spam clicking variables
        spamClickingActive = false;
        spamClickStartTime = 0;
        spamClickEndTime = 0;
        spamClickTarget = null;
        spamClicksPerformed = 0;
        lastSpamClick = 0;
        shouldPriorizeBrazierAtStart = false;
        
        // Reset HP tracking
        prevWintertodtHp = -1;
        prevHpTimestamp = 0;
        hpPerSecond = 0;
        estimatedSecondsLeft = 999;
        historicalEstimateSecondsLeft = 0;
        
        // Reset action timing averages
        avgChopMs = 2800;
        avgFletchMs = 2200;
        avgFeedMs = 1600;
        cycleTimeSec = 0;
        maxCyclesPossible = 0;
        
        // Reset action plan variables
        targetRootsForThisRun = 0;
        rootsToChopGoal = 0;
        fletchGoal = 0;
        feedGoal = 0;
        rootsChoppedThisRun = 0;
        fletchedThisRun = 0;
        fedThisRun = 0;
        currentBurnableCount = 0;
        
        // Reset XP drop timers
        lastWoodcuttingXpDropTime = 0;
        lastFletchingXpDropTime = 0;
        lastFiremakingXpDropTime = 0;
        
        // Reset feeding animation tracking
        lastFeedingAnimationTime = 0;
        
        // Reset human behavior variables
        lastMouseMovement = 0;
        lastCameraMovement = 0;
        
        // Reset performance tracking
        lastPerformanceCheck = 0;
        actionsPerformed = 0;
        consecutiveFailures = 0;
        
        // Reset fletching state
        if (fletchingState.isActive()) {
            fletchingState.stopFletching(FletchingInterruptType.MANUAL_STOP);
        }
        fletchingState = new FletchingState();
        
        // Reset feeding state
        if (feedingState.isActive()) {
            feedingState.stopFeeding(FeedingInterruptType.MANUAL_STOP);
        }
        feedingState = new FeedingState();
        
        // Reset waiting for round end flag
        waitingForRoundEnd = false;
        
        // Reset game state
        currentBrazier = null;

        // Reset startup manager if it exists
        if (startupManager != null) {
            startupManager.reset();
        }
        
        // Reset startup completion flag
        startupCompleted = false;
        
        // Reset world checking flag
        worldChecked = false;
        
        // Reset reward cart looting variables
        isLootingRewards = false;
        targetRewardThreshold = 0;
        currentRewardCartRewards = 0;
        rewardCartExhausted = false;
        lastRewardCartInteraction = 0;
        wasInWintertodtBeforeRewards = false;

        // Reset potion usage variables
        usesPotions = false;
        autoAdjustedPotionUsage = false;
        
        Microbot.log("All script state variables reset to default values");
    }

    /**
     * Configures antiban settings for Wintertodt specifically.
     * This method runs every time the plugin starts to ensure proper configuration.
     */
    private void configureAntibanSettings() {
        try {
            Microbot.log("Configuring antiban settings for Wintertodt...");
            
            // Reset and apply firemaking setup
            Rs2Antiban.resetAntibanSettings();
            Rs2Antiban.antibanSetupTemplates.applyFiremakingSetup();
            Rs2Antiban.setActivity(Activity.GENERAL_FIREMAKING);
            
            // Override some settings for Wintertodt-specific behavior
            Rs2AntibanSettings.takeMicroBreaks = false; // Disabled - using custom microbreak system
            Rs2AntibanSettings.microBreakChance = 0.0; // Disabled
            Rs2AntibanSettings.actionCooldownChance = 0.15; // 15% chance for action cooldown
            Rs2AntibanSettings.moveMouseRandomly = true;
            Rs2AntibanSettings.moveMouseRandomlyChance = 0.36;
            Rs2AntibanSettings.moveMouseOffScreen = true;
            Rs2AntibanSettings.moveMouseOffScreenChance = 0.38;
            Rs2AntibanSettings.naturalMouse = true;
            Rs2AntibanSettings.simulateMistakes = true;
            Rs2AntibanSettings.simulateFatigue = true;
            Rs2Antiban.setActivityIntensity(ActivityIntensity.HIGH);
            
            // Log antiban configuration
            Microbot.log("=== Antiban Configuration ===");
            Microbot.log("Activity: " + Rs2Antiban.getActivity().getMethod());
            Microbot.log("Play Style: " + Rs2Antiban.getPlayStyle().getName());
            Microbot.log("Micro Breaks: " + (Rs2AntibanSettings.takeMicroBreaks ? "Enabled" : "Disabled"));
            Microbot.log("Action Cooldown: " + (Rs2AntibanSettings.usePlayStyle ? "Enabled" : "Disabled"));
            Microbot.log("Mouse Randomization: " + (Rs2AntibanSettings.moveMouseRandomly ? "Enabled" : "Disabled"));
            Microbot.log("============================");
            
            Microbot.log("Antiban system configured for Wintertodt with firemaking profile");
            
        } catch (Exception e) {
            Microbot.log("Error configuring antiban settings: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Executes the smart startup sequence to prepare for Wintertodt.
     * This handles navigation from anywhere in the game, gear setup, and inventory preparation.
     *
     * @return true if startup completed successfully
     */
    private boolean executeStartupSequence() {
        try {
            Microbot.log("Executing smart startup sequence...");
            
            // Update Microbot status
            Microbot.status = startupManager.getCurrentPhase().getDescription();
            
            // Execute the startup sequence
            if (startupManager.executeStartupSequence()) {
                startupCompleted = true;
                Microbot.log("Smart startup sequence completed successfully!");
                return true;
            } else {
                Microbot.log("Smart startup sequence failed");
                return false;
            }
            
        } catch (Exception e) {
            Microbot.log("Error during startup sequence: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Initializes the script with proper validation and setup.
     *
     * @param config Configuration object
     * @param plugin Plugin instance
     * @return true if initialization successful
     */
    private boolean initializeScript(MKE_WintertodtConfig config, MKE_WintertodtPlugin plugin) {
        try {
            Microbot.log("Initializing Enhanced Wintertodt Script...");

            // Ensure lastStateChange is current (should already be set in run() method)
            lastStateChange = System.currentTimeMillis();
            Microbot.log("Script initialization - lastStateChange confirmed reset");

            // Close bank if it's open at script start
            if (Rs2Bank.isOpen()) {
                Microbot.log("Bank is open at script start - closing it");
                Rs2Bank.closeBank();
                sleepUntilTrue(() -> !Rs2Bank.isOpen(), 100, 3000);
            }

            // Initial state
            state = State.BANKING;

            // Validate equipment setup
            if (!validateEquipmentSetup()) {
                return false;
            }

            // Validate inventory setup
            if (!validateInventorySetup()) {
                return false;
            }

            /* -------- seed round-duration history with default -------- */
            previousRoundDurations.clear();
            previousRoundDurations.add(DEFAULT_ROUND_DURATION_MS);

            isInitialized = true;

            Microbot.log("Script initialized successfully!");
            
            // Fix camera settings on script start
            Microbot.log("Checking and adjusting camera settings...");
            fixCameraPitchIfNeeded();
            fixCameraZoomIfNeeded();

            resetActionPlanning();        // always start with a clean slate

            /* -------- Figure-out the correct starting state -------- */
            GameState gs = analyzeGameState();     // one cheap scan
            determineInitialState(gs);
            Microbot.log("Initial state decided: " + state);

            return true;

        } catch (Exception e) {
            Microbot.log("Failed to initialize script: " + e.getMessage());
            return false;
        }
    }

    /**
     * Validates the player's equipment setup with automatic axe detection.
     *
     * @return true if equipment is valid
     */
    private boolean validateEquipmentSetup() {
        // Get automatic axe decision
        WintertodtAxeManager.AxeDecision axeDecision = WintertodtAxeManager.determineOptimalAxeSetup();
        Microbot.log("Automatic axe validation: " + axeDecision.toString());
        
        if (axeDecision.shouldEquipAxe()) {
            if (!Rs2Equipment.isWearing(axeDecision.getAxeId())) {
                if (Rs2Inventory.hasItem(axeDecision.getAxeId())) {
                    Microbot.log("Equipping axe from inventory: " + axeDecision.getAxeName());
                    Rs2Inventory.wield(axeDecision.getAxeId());
                    sleepUntilTrue(() -> Rs2Equipment.isWearing(axeDecision.getAxeId()), 100, 5000);
                    return Rs2Equipment.isWearing(axeDecision.getAxeId());
                }
                Microbot.showMessage("No suitable axe found! The startup system should have handled this.");
                return false;
            }
            Microbot.log("Using equipped axe: " + axeDecision.getAxeName());
        } else {
            if (!Rs2Inventory.hasItem(axeDecision.getAxeId())) {
                Microbot.showMessage("Axe should be in inventory but not found! Startup system issue.");
                return false;
            }
            Microbot.log("Using axe from inventory: " + axeDecision.getAxeName());
        }

        return true;
    }

    /**
     * Validates the player's inventory setup.
     *
     * @return true if inventory is valid
     */
    private boolean validateInventorySetup() {
        // Check for required tools based on configuration
        if (config.fletchRoots() && !Rs2Inventory.hasItem(WintertodtInventoryManager.knifeToUse)) {
            Microbot.log("Fletching enabled but no knife in inventory - this is okay, will get one from bank");
        }

        if (config.fixBrazier() && !Rs2Inventory.hasItem(ItemID.HAMMER)) {
            Microbot.log("Brazier fixing enabled but no hammer in inventory - this is okay, will get one from bank");
        }

        // Check if we need tinderbox (only if not using bruma torch)
        if (!Rs2Equipment.isWearing(ItemID.BRUMA_TORCH) &&
                !Rs2Equipment.isWearing(ItemID.BRUMA_TORCH_OFFHAND) &&
                !Rs2Inventory.hasItem(ItemID.TINDERBOX)) {
            Microbot.log("No bruma torch equipped and no tinderbox in inventory - this is okay, will get one from bank");
        }

        return true;
    }

    /**
     * Analyzes the current game state and returns a comprehensive state object.
     */
    private GameState analyzeGameState() {
        GameState gameState = new GameState();

        try {
            /* ----- round-timer + HP based lifecycle detection ----- */
            int timerSeconds = getWintertodtRemainingTime();      //  -1 when timer widget not visible
            boolean timerVisible = timerSeconds > 0;              // visible  => round break

            // Read HP from the "Wintertodt's Energy" widget (396,26)
            int wtHp = -1;
            Widget energyWidget = Rs2Widget.getWidget(396, 26);
            if (energyWidget != null) {
                Matcher m = Pattern.compile("(\\d+)").matcher(energyWidget.getText());
                if (m.find()) {
                    wtHp = Integer.parseInt(m.group(1));
                }
            }

            // --- keep running HP/s statistics ----------------
            updateWintertodtHpTracking(wtHp);
            // -------------------------------------------------------

            boolean roundActive = !timerVisible && wtHp != 0;     // active only if timer gone AND HP not 0

            gameState.wintertodtRespawning = !roundActive;        // includes HP 0 or timer visible
            gameState.isWintertodtAlive   = roundActive;
            gameState.wintertodtHp        = wtHp;
            /* ----------------------------------------------------- */

            gameState.playerWarmth      = getWarmthLevel();

            // Object detection
            gameState.brazier        = Rs2GameObject.findObject(BRAZIER_29312,
                                          config.brazierLocation().getOBJECT_BRAZIER_LOCATION());
            gameState.brokenBrazier  = Rs2GameObject.findObject(BRAZIER_29313,
                                          config.brazierLocation().getOBJECT_BRAZIER_LOCATION());
            gameState.burningBrazier = Rs2GameObject.findObject(BURNING_BRAZIER_29314,
                                          config.brazierLocation().getOBJECT_BRAZIER_LOCATION());

            // Health and food management - determine healing strategy
            if (!autoAdjustedPotionUsage) {
                usesPotions = (config.healingMethod() == HealingMethod.POTIONS);
            }
            
            String foodType = usesPotions ? "Rejuvenation potion " : config.food().getName();
            int foodCount   = Rs2Inventory.count(foodType);     // current food in inventory
            boolean inBossRoom = WintertodtLocationManager.isInsideGameRoom();

            boolean lowAndOutOfFood =
                    (foodCount == 0) &&
                    (gameState.playerWarmth <= config.eatAtWarmthLevel());

            /* ─────────── BANKING RULES FOR REJUVENATION POTIONS ─────────── */
            if (usesPotions) {
                // When using rejuvenation potions, we make them instead of banking
                if (gameState.wintertodtRespawning) {
                    // During round break, make potions if we need them
                    gameState.needBanking = false; // Never bank with rejuv potions
                    gameState.needPotions = foodCount < config.minHealingItems();
                } else if (inBossRoom) {
                    // During active round, only make potions if we're out AND low warmth
                    gameState.needBanking = false;
                    gameState.needPotions = lowAndOutOfFood;
                } else {
                    // In lobby area with rejuv potions
                    gameState.needBanking = false;
                    gameState.needPotions = foodCount < config.minHealingItems();
                }
            } else {
                // Original banking logic for regular food
                if (gameState.wintertodtRespawning) {
                    gameState.needBanking = foodCount < config.minHealingItems();
                } else if (inBossRoom) {
                    gameState.needBanking = lowAndOutOfFood;
                } else {
                    gameState.needBanking = foodCount < config.minHealingItems();
                }
                gameState.needPotions = false;
            }

            // Inventory state
            gameState.inventoryFull = Rs2Inventory.isFull();
            gameState.hasItemsToBurn = Rs2Inventory.hasItem(ItemID.BRUMA_KINDLING) || Rs2Inventory.hasItem(ItemID.BRUMA_ROOT);
            gameState.hasRootsToFletch = Rs2Inventory.hasItem(ItemID.BRUMA_ROOT);

            // For overlay action plan
            currentBurnableCount = Rs2Inventory.count(ItemID.BRUMA_ROOT) + Rs2Inventory.count(ItemID.BRUMA_KINDLING);

        } catch (Exception e) {
            System.err.println("Error analyzing game state: " + e.getMessage());
        }

        return gameState;
    }

    /**
     * Handles emergency situations that require immediate attention.
     *
     * @param gameState Current game state
     * @return true if emergency was handled, false otherwise
     */
    private boolean handleEmergencySituations(GameState gameState) {
        // Don't handle emergencies during breaks unless critical
        if (BreakHandlerScript.isBreakActive() || Rs2AntibanSettings.microBreakActive) {
            // Only handle critical dialogs during breaks
            if (Rs2Widget.hasWidget("Leave and lose all progress")) {
                Microbot.log("Critical dialog detected during break, handling...");
                Rs2Keyboard.typeString("1");
                sleepGaussian(1600, 400);
                return true;
            }
            return false;
        }

        // Handle "Leave and lose all progress" dialog
        if (Rs2Widget.hasWidget("Leave and lose all progress")) {
            Microbot.log("Detected leave dialog, selecting option 1");
            Rs2Keyboard.typeString("1");
            sleepGaussian(1600, 400);
            return true;
        }

        // Handle emergency warmth during breaks - eat food or walk to safe spot
        if ((BreakHandlerScript.isBreakActive() || WintertodtBreakManager.isBreakActive() || Rs2AntibanSettings.microBreakActive) && gameState.playerWarmth < 30) {
            Microbot.log("EMERGENCY: Very low warmth (" + gameState.playerWarmth + ") during break!");
            
            // Try emergency eating first if we have food
            if (Rs2Inventory.hasItem(config.food().getName()) || (usesPotions && !Rs2Inventory.getPotions().isEmpty())) {
                Microbot.log("Emergency eating during break due to low warmth");
                if (handleEating(gameState)) {
                    return true;
                }
            }
            
            // If no food or eating failed, try to walk to safe spot (boss room area)
            WorldPoint playerLocation = Rs2Player.getWorldLocation();
            if (playerLocation != null && playerLocation.distanceTo(BOSS_ROOM) > 6) {
                Microbot.log("Emergency walk to safe spot (boss room) during break");
                Rs2Walker.walkTo(BOSS_ROOM);
                sleepGaussian(1200, 300);
                return true;
            }
            
            return false;
        }

        // Handle stuck state (no state change for too long) - but don't interfere with breaks
        if (System.currentTimeMillis() - lastStateChange > 120000 && !BreakHandlerScript.isLockState()) {
            Microbot.log("Possible stuck state detected after 2 minutes, resetting...");
            resetActions = true;
            setLockState(state, false);
            lastStateChange = System.currentTimeMillis(); // Reset the timer
            return true;
        }

        return false;
    }

    /**
     * Performs human-like behaviors to avoid detection.
     */
    private void performHumanLikeBehaviors() {
        if (!config.humanizedTiming()) return;

        long currentTime = System.currentTimeMillis();

        // Random mouse movements
        if (config.randomMouseMovements() && currentTime - lastMouseMovement > 15000 + random.nextInt(10000)) {
            if (random.nextInt(100) < 15) { // 15% chance
                // Simulate checking other areas occasionally
                if (currentBrazier != null && random.nextBoolean()) {
                    Rs2GameObject.hoverOverObject(currentBrazier);
                }
                lastMouseMovement = currentTime;
            }
        }

        // Random camera movements
        if (config.randomMouseMovements() && shouldPerformCameraMovement(currentTime)) {
            performHumanLikeCameraMovement();
        }

        // Variable delay based on recent actions
        if (resetActions && config.humanizedTiming()) {
            int baseDelay = 150;
            int variation = 50 + random.nextInt(100);
            sleepGaussian(baseDelay, variation);
        }
    }

    /**
     * Determines if we should perform a camera movement based on timing and bot state.
     * Only moves camera when bot is idle to avoid interrupting actions.
     */
    private boolean shouldPerformCameraMovement(long currentTime) {
        // Check if camera movements are disabled
        int cameraFrequencySeconds = config.cameraMovementFrequency();
        if (cameraFrequencySeconds <= 0) {
            return false;
        }

        // Convert frequency to milliseconds
        long cameraMovementMinDelay = cameraFrequencySeconds * 1000L;

        // Check if enough time has passed since last camera movement
        long timeSinceLastMove = currentTime - lastCameraMovement;
        if (timeSinceLastMove < cameraMovementMinDelay) {
            return false;
        }

        // Random chance that increases over time
        double baseChance = 0.001; // 0.1% base chance per call
        double timeMultiplier = Math.min(50.0, timeSinceLastMove / (double)cameraMovementMinDelay);
        double finalChance = baseChance * timeMultiplier;

        if (random.nextDouble() > finalChance) {
            return false;
        }

        // Only move camera when bot is in suitable states
        boolean isRestrictedState = (state == State.WAITING);

        return !isRestrictedState;
    }

    /**
     * Performs a natural, human-like camera movement by briefly holding the arrow
     * keys rather than teleporting the camera to a new angle.  Yaw and pitch
     * deltas follow Gaussian distributions so that small adjustments are common
     * while large "look around" sweeps are rare.
     */
    private void performHumanLikeCameraMovement()
    {
        try
        {
            /* ---------- decide yaw delta -------------------------------- */
            //  μ = 0°,  σ = 20°  →  68 % of moves within ±20°, rarely >±60°
            int yawDelta = (int) Math.round(random.nextGaussian() * 20);
            yawDelta = Math.max(-75, Math.min(75, yawDelta));      // clamp

            // 25 % chance to flip direction to make "about face" rarer
            if (yawDelta == 0 || (Math.abs(yawDelta) < 10 && random.nextInt(4) == 0))
            {
                yawDelta = (random.nextBoolean() ? 1 : -1) * (12 + random.nextInt(15));
            }

            int targetYaw = (Rs2Camera.getAngle() + yawDelta + 360) % 360;

            /* ---------- decide if we also adjust pitch ------------------ */
            boolean changePitch = random.nextInt(100) < 42;   // 42 % of the time
            float   targetPitchPct = Rs2Camera.cameraPitchPercentage();

            if (changePitch)
            {
                // μ = 0, σ = 10 % of full range, clamped to 30 – 90 %
                float deltaPct = (float) (random.nextGaussian() * 0.10);
                targetPitchPct = Math.max(0.30f, Math.min(0.90f, targetPitchPct + deltaPct));
            }

            /* ---------- make the movement with key taps ----------------- */
            // Hold key for a duration proportional to needed rotation
            int degreesToMove = Math.abs(Rs2Camera.getAngleTo(targetYaw));
            int pressTime = 80 + (int) (degreesToMove * 2.2);       // ms
            pressTime = (int) (pressTime * (0.8 + random.nextGaussian() * 0.1));
            pressTime = Math.max(60, pressTime);                    // safety

            if (Rs2Camera.getAngleTo(targetYaw) > 0)
            {
                Rs2Keyboard.keyHold(java.awt.event.KeyEvent.VK_LEFT);
                sleepGaussian(pressTime, (int) (pressTime * 0.15));
                Rs2Keyboard.keyRelease(java.awt.event.KeyEvent.VK_LEFT);
            }
            else
            {
                Rs2Keyboard.keyHold(java.awt.event.KeyEvent.VK_RIGHT);
                sleepGaussian(pressTime, (int) (pressTime * 0.15));
                Rs2Keyboard.keyRelease(java.awt.event.KeyEvent.VK_RIGHT);
            }

            /* small random pause between yaw and pitch */
            if (changePitch) sleepGaussian(120, 40);

            if (changePitch)
            {
                float now = Rs2Camera.cameraPitchPercentage();
                boolean pitchUp = now < targetPitchPct;
                int    pitchKey = pitchUp ? java.awt.event.KeyEvent.VK_UP
                                          : java.awt.event.KeyEvent.VK_DOWN;

                // Duration scaled to percentage difference
                int pctDiff    = (int) (Math.abs(targetPitchPct - now) * 100);
                int pitchTime  = 50 + (int) (pctDiff * 8);
                pitchTime = (int) (pitchTime * (0.85 + random.nextGaussian() * 0.12));
                pitchTime = Math.max(40, pitchTime);

                Rs2Keyboard.keyHold(pitchKey);
                sleepGaussian(pitchTime, (int) (pitchTime * 0.20));
                Rs2Keyboard.keyRelease(pitchKey);
            }

            // Update timestamp
            lastCameraMovement = System.currentTimeMillis();
            
            // Check and fix camera settings after movement
            sleepGaussian(200, 100); // Small delay before checking
            fixCameraPitchIfNeeded();
            fixCameraZoomIfNeeded();
        }
        catch (Exception ignored) {}
    }

    /**
     * Checks if camera pitch is too low and adjusts it if needed using keyboard simulation.
     */
    private void fixCameraPitchIfNeeded() {
        try {
            int currentPitch = Rs2Camera.getPitch();

            // If pitch is too low (camera looking too far down), adjust it to a medium-high angle
            if (currentPitch < 230) { // Adjust this threshold as needed
                Microbot.log("Camera pitch too low (" + currentPitch + "), adjusting...");

                // Adjust to a pitch between 60% and 90%
                float targetPitchPercent = 0.6f + random.nextFloat() * 0.3f;
                Rs2Camera.adjustPitch(targetPitchPercent);

                int newPitch = Rs2Camera.getPitch();
                Microbot.log("Camera pitch adjusted from " + currentPitch + " to " + newPitch);
            }

        } catch (Exception e) {
            System.err.println("Error fixing camera pitch: " + e.getMessage());
        }
    }

    /**
     * Checks if camera zoom is too close/far and adjusts it if needed.
     */
    private void fixCameraZoomIfNeeded() {
        try {
            int currentZoom = Rs2Camera.getZoom();
            
            // If zoom is too close (< 250) or too far (> 350), adjust to medium range
            if (currentZoom < 250 || currentZoom > 350) {
                
                // Set zoom to a value between 250-350 for good visibility
                int targetZoom = 250 + random.nextInt(100);
                Rs2Camera.setZoom(targetZoom);
                
                // Small delay after zoom adjustment
                sleepGaussian(300, 100);
            }

        } catch (Exception e) {
            System.err.println("Error fixing camera zoom: " + e.getMessage());
        }
    }

    /**
     * Performs regular maintenance tasks like dropping items and damage avoidance.
     *
     * @param gameState Current game state
     */
    private void performMaintenanceTasks(GameState gameState) {
        if (state == State.WALKING_TO_SAFE_SPOT_FOR_BREAK) {
            return;
        }
        
        // During emergency banking exit, only handle critical eating and skip state changes
        if (state == State.BANKING && isInsideWintertodtArea()) {
            // Only handle critical eating during banking exit
            if (gameState.playerWarmth <= 30) {
                handleEating(gameState);
            }
            // Skip the rest (dropping items, dodging, camera movements, banking checks)
            return;
        }
        
        // Skip maintenance during breaks except for critical tasks
        if (BreakHandlerScript.isBreakActive() || Rs2AntibanSettings.microBreakActive) {
            // Only handle critical eating during breaks
            if (gameState.playerWarmth <= 30) {
                handleEating(gameState);
            }
            return;
        }

        // Drop unnecessary items
        dropUnnecessaryItems();

        // Dodge falling snow/damage
        dodgeSnowfallDamage(gameState);

        // Handle eating
        handleEating(gameState);

        // Periodic camera check (every 2 minutes during normal operation)
        if (System.currentTimeMillis() - lastCameraMovement > 120000) {
            fixCameraPitchIfNeeded();
            fixCameraZoomIfNeeded();
            lastCameraMovement = System.currentTimeMillis(); // Reset timer to avoid frequent checks
        }

        // Banking check (respect break handler state) - BUT NOT for rejuvenation potions
        if (gameState.needBanking && !usesPotions) {
            setLockState(State.BANKING, false);
            changeState(State.BANKING);
        }
    }

    /**
     * Executes the main game logic based on current state.
     *
     * @param gameState Current game state
     */
    private void executeMainGameLogic(GameState gameState) {
        // During emergency banking exit, skip game activities but execute banking logic directly
        if (state == State.BANKING && isInsideWintertodtArea()) {
            // Skip round timer updates, reward cart checks, and main game activities
            // But execute the banking state logic directly to handle the actual exit
            Microbot.log("Emergency banking exit in progress - executing banking logic directly");
            executeStateLogic(gameState);
            return;
        }
        
        // Skip main game logic during breaks (except banking/potions)
        if ((BreakHandlerScript.isBreakActive() || Rs2AntibanSettings.microBreakActive) && 
            state != State.BANKING && state != State.GET_CONCOCTIONS && 
            state != State.GET_HERBS && state != State.MAKE_POTIONS) {
            Microbot.log("Skipping main game logic due to active break");
            return;
        }

        // Update round timer tracking
        if (!isRewardCartState(state) && state != State.BANKING && state != State.GET_CONCOCTIONS && state != State.GET_HERBS && state != State.MAKE_POTIONS && isInsideWintertodtArea()) {
            updateRoundTimer();
        }

        // Update game state
        gameState = analyzeGameState();

        // Handle Wintertodt down FIRST - this transitions to WAITING state
        if (!gameState.isWintertodtAlive && !gameState.needBanking && 
            state != State.GET_CONCOCTIONS && state != State.GET_HERBS && 
            state != State.MAKE_POTIONS && state != State.WALKING_TO_SAFE_SPOT_FOR_BREAK && 
            !isRewardCartState(state)) {
            handleWintertodtDown(gameState);
        }

        // Check if we should trigger reward cart looting (only if not already looting)
        // This now happens AFTER handleWintertodtDown, so state can be properly transitioned to WAITING
        if (!isLootingRewards && shouldLootRewardCart()) {
            startRewardCartLooting();
            return; // Let the reward cart states handle everything
        }

        // Handle potion creation if needed
        if (gameState.needPotions && usesPotions &&
            !isRewardCartState(state) &&
            state != State.BANKING && // BANKING state has its own potion gear-up logic
            state != State.GET_CONCOCTIONS &&
            state != State.GET_HERBS &&
            state != State.MAKE_POTIONS) {
            
            Microbot.log("EMERGENCY: Need potions for survival - interrupting current activity (state: " + state + ")");
            
            // Force unlock state locks for emergency potion creation
            if (lockState) {
                setLockState(state, false);
                Microbot.log("Emergency unlock of state lock for potion creation");
            }
            
            // Stop active fletching/feeding if needed
            if (fletchingState.isActive()) {
                fletchingState.stopFletching(FletchingInterruptType.MANUAL_STOP);
                Microbot.log("Stopped fletching for emergency potion creation");
            }
            if (feedingState.isActive()) {
                feedingState.stopFeeding(FeedingInterruptType.MANUAL_STOP);
                Microbot.log("Stopped feeding for emergency potion creation");
            }
            
            handlePotionCreation(gameState);
            return; // Don't continue with main game loop when starting potion creation
        }

        // Handle main game loop (only if Wintertodt is alive and other conditions are met)
        if (!gameState.needBanking && gameState.isWintertodtAlive && 
            state != State.GET_CONCOCTIONS && state != State.GET_HERBS && 
            state != State.MAKE_POTIONS && state != State.WALKING_TO_SAFE_SPOT_FOR_BREAK && 
            !isRewardCartState(state)) {
            handleMainGameLoop(gameState);
        }

        // Execute state-specific logic
        executeStateLogic(gameState);
    }

    /**
     * Handles the rejuvenation potion creation workflow
     */
    private void handlePotionCreation(GameState gameState) {
        // Check if we're already in a potion-making state
        if (state == State.GET_CONCOCTIONS || state == State.GET_HERBS || state == State.MAKE_POTIONS) {
            // Already in a potion state, let the state handlers do their work
            return;
        }

        // Determine what we need and start the appropriate state
        int concoctionCount = Rs2Inventory.count(ItemID.REJUVENATION_POTION_UNF);
        int herbCount = Rs2Inventory.count(ItemID.BRUMA_HERB);
        int currentPotions = getTotalRejuvenationPotions();
        int potionsNeeded = config.healingAmount() - currentPotions;

        // If we have enough potions, don't do anything
        if (potionsNeeded <= 0) {
            return;
        }

        // If we have both ingredients, make potions
        if (concoctionCount > 0 && herbCount > 0) {
            changeState(State.MAKE_POTIONS);
        }
        // If we need concoctions, get them first
        else if (concoctionCount < potionsNeeded) {
            changeState(State.GET_CONCOCTIONS);
        }
        // If we need herbs, get them
        else if (herbCount < potionsNeeded) {
            changeState(State.GET_HERBS);
        }
    }

    /**
     * Handles the situation when Wintertodt is down/respawning.
     *
     * @param gameState Current game state
     */
    private void handleWintertodtDown(GameState gameState) {
        // Don't interrupt potion creation when Wintertodt is down
        if (state == State.GET_CONCOCTIONS || state == State.GET_HERBS || state == State.MAKE_POTIONS) {
            return; // Continue with potion creation
        }
        
        if (state != State.ENTER_ROOM && state != State.WAITING && state != State.BANKING) {
            setLockState(State.GLOBAL, false);
            changeState(State.WAITING);
        }
    }

    /**
     * Handles the main game loop when Wintertodt is alive.
     *
     * @param gameState Current game state
     */
    private void handleMainGameLoop(GameState gameState)
    {
        /* If we're waiting for round to end, don't do any activities */
        if (waitingForRoundEnd) {
            return; // Just idle until round ends naturally
        }

        /* Prioritize brazier lighting immediately when round starts */
        if (shouldPriorizeBrazierAtStart) {
            Microbot.log("Round start priority active - checking if brazier needs lighting");
            if (shouldLightBrazier(gameState)) {
                Microbot.log("Prioritizing brazier lighting at round start (state: " + state + ")");
                return; // Light the brazier first, then resume normal flow next tick
            } else {
                // Brazier is already lit or doesn't need lighting, reset the flag
                shouldPriorizeBrazierAtStart = false;
                Microbot.log("Brazier lighting priority completed or not needed (state: " + state + ")");
            }
        }

        /*  If time is almost over just finish up     */
        if (estimatedSecondsLeft < 15)
        {
            if (gameState.hasItemsToBurn) {
                if (shouldBurnLogs(gameState)) {
                    Microbot.log("Time is almost over and we have items to burn, lets just burn them!");
                    return;
                }
            }
            return;                                       // otherwise idle / hover
        }

        // Normal flow (smart chop / fletch / burn)
        if (shouldStartOrContinueChopping(gameState)) return;
        if (shouldFletchRoots())        return;
        if (shouldBurnLogs(gameState))  return;

        // If we get here and have no items, reset the visual plan on the overlay
        if (!gameState.hasItemsToBurn) {
            targetRootsForThisRun = 0;
        }
    }

    /**
     * Handles the banking state logic.
     */
    private void handleBankingState(GameState gameState) {
        try {
            /* leave the arena BEFORE doing anything else */
            // Handle script-specific state management when leaving Wintertodt
            if (!isInsideWintertodtArea()) {
                if (previouslyInsideArena) {
                    resetActionPlanning();
                    previouslyInsideArena = false;
                    waitingForRoundEnd = false; // Reset waiting flag when outside
                }
                // Reset exit failure counter on successful exit
                consecutiveExitFailures = 0;
            } else {
                previouslyInsideArena = true;
                
                // Check points before leaving - wait for rewards if we have enough
                int currentPoints = getWintertodtPoints();
                if (currentPoints >= 500) {
                    Microbot.log("Have " + currentPoints + " points - waiting near door for round to end naturally");
                    waitingForRoundEnd = true; // Set flag to prevent resuming activities
                } else {
                    waitingForRoundEnd = false; // Reset waiting flag if points dropped below threshold
                }
                
                if (!WintertodtLocationManager.attemptLeaveWintertodt()) {
                    consecutiveExitFailures++;
                    if (consecutiveExitFailures >= MAX_EXIT_FAILURES) {
                        Microbot.log("CRITICAL: Too many exit failures (" + consecutiveExitFailures + ") - emergency logout to prevent infinite loop");
                        Rs2Player.logout();
                        shutdown();
                        return;
                    }
                    Microbot.log("Exit attempt failed (" + consecutiveExitFailures + "/" + MAX_EXIT_FAILURES + ") - retrying next tick");
                    return; // still inside → try again next tick
                }
            }

            // Check and hop to Wintertodt world if needed (safe location)
            if (!ensureWintertodtWorldSafely()) {
                return; // Script stopped due to hop failure
            }

            // Skip banking entirely if using rejuvenation potions
            if (usesPotions) {
                /*
                 * Even when using rejuvenation potions we STILL need to make sure the player
                 * has all mandatory tools (axe, tinderbox/torch, knife, hammer …) before
                 * heading back into the arena.  Therefore we perform a **minimal** banking
                 * sequence that only (re)sets up gear & inventory but skips the regular
                 * food-withdrawal logic.
                 */
                Microbot.log("Using rejuvenation potions - ensuring gear/tools via bank setup");

                // Walk to the bank chest/booth if we are not close enough yet
                if (Rs2Player.getWorldLocation().distanceTo(BANK_LOCATION) > 6) {
                    Rs2Walker.walkTo(BANK_LOCATION);
                    Rs2Player.waitForWalking();
                    return; // wait until we have arrived, continue next tick
                }

                // Open the bank if it is not open yet
                if (!Rs2Bank.isOpen()) {
                    Rs2Bank.useBank();
                    return; // open animation in progress – continue once open
                }

                // At this point the bank is open → run the usual gear/inventory setup
                if (!checkAndUpgradeGear()) {
                    Microbot.log("Gear setup failed for rejuvenation potion mode");
                    return;
                }

                // All done – close the bank again so we can continue
                Rs2Bank.closeBank();
                
                // Wait for bank to close before proceeding
                sleepUntilTrue(() -> !Rs2Bank.isOpen(), 100, 3000);

                Microbot.log("Rejuvenation potion gear/tool setup completed - proceeding to potion creation");

                // Handle break opportunity before starting potion creation
                if (handleBreakOpportunity()) {
                    return; // Break started, exit function
                }

                // Proceed with the potion-creation workflow
                changeState(State.GET_CONCOCTIONS);
                return;
            }

            // Execute banking: gear/tools setup + food withdrawal
            // Returns false if we need to wait for an action to complete
            if (!executeBankingLogic()) return;

            // Check if ready to continue
            String foodType = usesPotions ? "Rejuvenation potion " : config.food().getName();
            if (Rs2Player.isFullHealth() && Rs2Inventory.hasItemAmount(foodType, config.healingAmount(), false, true)) {
                plugin.setTimesBanked(plugin.getTimesBanked() + 1);

                // Handle break opportunity - returns true if break started
                if (handleBreakOpportunity()) {
                    return; // Break started, exit function
                }

                changeState(State.ENTER_ROOM);
            }

        } catch (Exception e) {
            System.err.println("Error in banking state: " + e.getMessage());
            consecutiveFailures++;
        }
    }

    /**
     * Handles the enter room state logic.
     */
    private void handleEnterRoomState(GameState gameState) {
        try {
            // Ensure we're on Wintertodt world before entering room
            if (!ensureWintertodtWorldSafely()) {
                return; // Script stopped due to hop failure
            }

            // Navigate to boss room
            if (!gameState.wintertodtRespawning && !gameState.isWintertodtAlive) {
                if (Rs2Player.getWorldLocation().distanceTo(BOSS_ROOM) > 8) {
                    Rs2Walker.walkTo(BOSS_ROOM, 4);
                    Rs2Player.waitForWalking();
                }
            } else {
                changeState(State.WAITING);
            }

        } catch (Exception e) {
            System.err.println("Error in enter room state: " + e.getMessage());
        }
    }

    /**
     * Handles the waiting state logic.
     */
    private void handleWaitingState(GameState gameState) {
        try {
            // Check and hop to Wintertodt world if needed (safe location inside Wintertodt)
            if (!ensureWintertodtWorldSafely()) {
                return; // Script stopped due to hop failure
            }

            // Execute spam clicking if in the appropriate time window (this runs during countdown)
            executeSpamClicking(gameState);

            // Move to brazier area
            navigateToBrazier();

            // Check if we should light brazier
            shouldLightBrazier(gameState);

            // Handle round timer-based mouse behavior
            handleRoundTimerMouseBehavior(gameState);

        } catch (Exception e) {
            System.err.println("Error in waiting state: " + e.getMessage());
        }
    }

    /**
     * Handles the light brazier state logic.
     */
    private void handleLightBrazierState(GameState gameState) {
        try {
            // Ensure we're on Wintertodt world before lighting brazier
            if (!ensureWintertodtWorldSafely()) {
                return; // Script stopped due to hop failure
            }

            // Abort lighting if the round has ended while we were in this state
            if (!gameState.isWintertodtAlive || gameState.wintertodtHp == 0) {
                setLockState(State.LIGHT_BRAZIER, false);
                shouldPriorizeBrazierAtStart = false; // Reset priority flag
                changeState(State.WAITING);
                return;
            }

            if (gameState.brazier != null) {
                if (Rs2GameObject.interact(gameState.brazier, "light")) {
                    Microbot.log("Lighting brazier");
                    
                    // Reset priority flag after successful lighting attempt
                    if (shouldPriorizeBrazierAtStart) {
                        shouldPriorizeBrazierAtStart = false;
                        Microbot.log("Brazier lighting priority completed - resuming normal flow");
                    }
                    
                    Rs2Player.waitForXpDrop(Skill.FIREMAKING, 3000);
                    actionsPerformed++;
                    
                    // CRITICAL FIX: Transition to appropriate next state after lighting
                    setLockState(State.LIGHT_BRAZIER, false);
                    
                    // Determine next state based on current situation
                    if (gameState.hasItemsToBurn) {
                        changeState(State.BURN_LOGS);
                        Microbot.log("Transitioning to BURN_LOGS after lighting brazier");
                    } else if (!gameState.inventoryFull) {
                        changeState(State.CHOP_ROOTS);
                        Microbot.log("Transitioning to CHOP_ROOTS after lighting brazier");
                    } else {
                        changeState(State.WAITING);
                        Microbot.log("Transitioning to WAITING after lighting brazier");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error in light brazier state: " + e.getMessage());
        }
    }

    /**
     * Handles the chop roots state logic.
     */
    private void handleChopRootsState(GameState gameState)
    {
        try
        {
            // Ensure we're on Wintertodt world before chopping
            if (!ensureWintertodtWorldSafely()) {
                return; // Script stopped due to hop failure
            }

            // stop when plan met (root-count already added in onStatChanged)
            if (targetRootsForThisRun > 0
                && (Rs2Inventory.count(ItemID.BRUMA_ROOT)
                    + Rs2Inventory.count(ItemID.BRUMA_KINDLING)) >= targetRootsForThisRun)
            {
                setLockState(State.CHOP_ROOTS, false);
                changeState(State.WAITING);
                targetRootsForThisRun = 0;
                rootsChoppedThisRun = 0;
                return;
            }

            /* stop chopping if inventory became full */
            if (gameState.inventoryFull) {
                setLockState(State.CHOP_ROOTS, false);
                changeState(State.WAITING);      // next loop decides what to do (usually burn)
                return;
            }

            /* safety: if we somehow got pushed away, move back */
            if (Rs2Player.getWorldLocation()
                         .distanceTo(config.brazierLocation().getBRAZIER_LOCATION()) > CHOPPING_RADIUS)
            {
                navigateToBrazier();        // walk back, try chopping next loop
                return;
            }

            if (!Rs2Player.isAnimating())
            {
                GameObject root = getOwnSideRoot();
                if (root != null && Rs2GameObject.interact(root, "Chop"))
                {
                    sleepUntilTrue(Rs2Player::isAnimating, 100, 3000);
                    maybeNudgeMouse();
                    resetActions = false;
                    actionsPerformed++;

                    if (Rs2AntibanSettings.usePlayStyle)
                        Rs2Antiban.actionCooldown();
                }
            }
        } catch (Exception e) {
            System.err.println("Error in chop roots state: " + e.getMessage());
        }
    }

    /**
     * Handles the fletch logs state logic.
     * Includes brazier maintenance (fixing and relighting) as priority actions
     * before continuing with fletching.
     */
    private void handleFletchLogsState(GameState gameState) {
        try {
            // Ensure we're on Wintertodt world before fletching
            if (!ensureWintertodtWorldSafely()) {
                return; // Script stopped due to hop failure
            }

            // Update fletching animation tracking
            updateFletchingAnimationTracking();
            
            /* ---------- PRIORITY BLOCK 1: FIX BROKEN BRAZIER FIRST ----------- */
            if (gameState.brokenBrazier != null && config.fixBrazier()) {

                sleepGaussian(200, 150);

                // Stop fletching temporarily to fix brazier
                if (fletchingState.isActive()) {
                    fletchingState.stopFletching(FletchingInterruptType.BRAZIER_BROKEN);
                }
                
                // Deselect any items before fixing
                deselectSelectedItem();
                
                Rs2GameObject.interact(gameState.brokenBrazier, "fix");
                Microbot.log("Fixing broken brazier (priority during fletching)");
                resetActions = true;
                actionsPerformed++;
                return; // Wait for next tick after the fix attempt
            }
            /* ----------------------------------------------------------------- */

            /* ---------- PRIORITY BLOCK 2: RELIGHT BRAZIER SECOND ------------ */
            if (gameState.burningBrazier == null && gameState.brazier != null && 
                config.relightBrazier() && gameState.isWintertodtAlive) {

                sleepGaussian(200, 150);
                
                // Stop fletching temporarily to relight brazier
                if (fletchingState.isActive()) {
                    fletchingState.stopFletching(FletchingInterruptType.BRAZIER_WENT_OUT);
                }
                
                // Deselect any items before relighting
                deselectSelectedItem();
                
                Rs2GameObject.interact(gameState.brazier, "light");
                Microbot.log("Relighting brazier (priority during fletching)");
                resetActions = true;
                actionsPerformed++;
                return; // Wait for next tick after the relight attempt
            }
            /* ----------------------------------------------------------------- */
            
            /* ------- Safety: nothing but the knife may be selected ----------- */
            if (isItemSelected() && !isKnifeSelected()) {
                deselectSelectedItem();
            }

            int rootCount = Rs2Inventory.count(ItemID.BRUMA_ROOT);

            /* --- recalculate threshold only when starting a new full inventory cycle --- */
            if (rootCount > lastInventoryCount) {
                // Root count increased = we just chopped new roots = start of new cycle
                knifePreselectThreshold = 1 + Rs2Random.randomGaussian(3, 10);  // 1-10 roots
                Microbot.log("New inventory cycle detected, knife threshold: " + knifePreselectThreshold);
            }
            lastInventoryCount = rootCount;

            /* END fletching as soon as no roots remain ---------------- */
            if (rootCount == 0) {
                if (fletchingState.isActive()) {
                    fletchingState.stopFletching(FletchingInterruptType.OUT_OF_ROOTS);
                }
                
                deselectSelectedItem();
                setLockState(State.FLETCH_LOGS, false);
                changeState(State.WAITING);
                return;
            }

            /* ---------- smart pre-selection when many roots ---------- */
            if (rootCount > knifePreselectThreshold) {
                /*  we postpone knife-selection until we have reached the brazier */
            } else {
                // Few roots left – ensure knife is un-selected for next tasks
                deselectSelectedItem();
            }

            /* ---------- start / continue fletching ------------------- */
            if (!isCurrentlyFletching() && gameState.burningBrazier != null) {
                sleepGaussian(250, 150);
                if (random.nextInt(100) < 10) {
                    sleepGaussian(400, 600);
                }
                navigateToBrazier();

                // Keep knife in slot-27 optimisation
                Rs2ItemModel knife = Rs2Inventory.get(WintertodtInventoryManager.knifeToUse);
                if (knife != null && knife.getSlot() != 27) {
                    sleepGaussian(GAME_TICK_LENGTH * 2, 200);
                    if (Rs2Inventory.moveItemToSlot(knife, 27)) {
                        sleepUntilTrue(() -> Rs2Inventory.slotContains(27, WintertodtInventoryManager.knifeToUse), 100, 5000);
                    }
                }

                // Perform the click and start tracking fletching
                boolean fletchingStarted = false;
                
                if (isKnifeSelected()) {
                    boolean rootStillThere = lastHoveredRoot != null
                            && Rs2Inventory.contains(r -> r.getSlot() == lastHoveredRoot.getSlot());

                    Rs2ItemModel targetRoot = rootStillThere
                            ? lastHoveredRoot
                            : Rs2Inventory.getRandom(ItemID.BRUMA_ROOT);

                    if (targetRoot != null) {
                        Rs2Inventory.interact(targetRoot);
                        fletchingStarted = true;
                    }

                    lastHoveredRoot = null;
                } else {
                    if (Rs2Inventory.combineClosest(WintertodtInventoryManager.knifeToUse, ItemID.BRUMA_ROOT)) {
                        fletchingStarted = true;
                    }
                }
                
                if (fletchingStarted) {
                    fletchingState.startFletching();
                    // Initialize animation tracking for new fletching session
                    lastFletchingAnimationTime = System.currentTimeMillis();
                    maybeNudgeMouse();
                    resetActions = false;
                    actionsPerformed++;

                    if (Rs2AntibanSettings.usePlayStyle) {
                        Rs2Antiban.actionCooldown();
                    }
                }
            }

            /* Pre-select knife and hover when we have many roots */
            if (rootCount > knifePreselectThreshold && !isKnifeSelected() && gameState.burningBrazier != null) {
                Rs2Inventory.interact(WintertodtInventoryManager.knifeToUse, "Use");
                sleepGaussian(120, 40);

                lastHoveredRoot = null;
                for (int slot = 27; slot >= 0; slot--) {
                    Rs2ItemModel cand = Rs2Inventory.getItemInSlot(slot);
                    if (cand != null && cand.getId() == ItemID.BRUMA_ROOT) {
                        lastHoveredRoot = cand;
                        Rs2Inventory.hover(cand);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error in fletch logs state: " + e.getMessage());
        }
    }

    /**
     * Handles the burn logs state logic.
     * Always fixes or relights the brazier first, then feeds.
     * Includes comprehensive feeding state tracking and interruption detection.
     */
    private void handleBurnLogsState(GameState gameState) {
        try {
            // Ensure we're on Wintertodt world before burning
            if (!ensureWintertodtWorldSafely()) {
                return; // Script stopped due to hop failure
            }

            // Update feeding animation tracking
            updateFeedingAnimationTracking();
            
            /* ---------- PRIORITY BLOCK 1: FIX BROKEN BRAZIER FIRST ----------- */
            if (gameState.brokenBrazier != null && config.fixBrazier()) {
                // Stop feeding temporarily to fix brazier
                if (feedingState.isActive()) {
                    feedingState.stopFeeding(FeedingInterruptType.BRAZIER_BROKEN);
                }
                
                Rs2GameObject.interact(gameState.brokenBrazier, "fix");
                Microbot.log("Fixing broken brazier");
                resetActions = true;
                actionsPerformed++;
                return; // Wait for next tick after the fix attempt
            }
            /* ----------------------------------------------------------------- */

            /* ---------- PRIORITY BLOCK 2: RELIGHT BRAZIER SECOND ------------ */
            TileObject burningBrazier = gameState.burningBrazier;  // side-specific
            if (burningBrazier == null && gameState.brazier != null && 
                config.relightBrazier() && gameState.isWintertodtAlive) {

                sleepGaussian(200, 150);
                
                // Stop feeding temporarily to relight brazier
                if (feedingState.isActive()) {
                    feedingState.stopFeeding(FeedingInterruptType.BRAZIER_WENT_OUT);
                }
                
                Rs2GameObject.interact(gameState.brazier, "light");
                Microbot.log("Relighting brazier");
                resetActions = true;
                actionsPerformed++;
                return; // Wait for next tick after the relight attempt
            }
            /* ----------------------------------------------------------------- */

            int currentItems = Rs2Inventory.count(ItemID.BRUMA_ROOT) + Rs2Inventory.count(ItemID.BRUMA_KINDLING);

            /* END feeding as soon as no items remain ---------------- */
            if (currentItems == 0) {
                if (feedingState.isActive()) {
                    feedingState.stopFeeding(FeedingInterruptType.OUT_OF_ITEMS);
                }
                return;
            }

            /* ---------- start / continue feeding ------------------- */
            if (!isCurrentlyFeeding() && 
                gameState.hasItemsToBurn) {

                sleepGaussian(200, 150);
                if (random.nextInt(100) < 10) {
                    sleepGaussian(400, 600);
                }
                
                if (Rs2GameObject.interact(burningBrazier, "feed")) {
                    feedingState.startFeeding();
                    // Initialize animation tracking for new feeding session
                    lastFeedingAnimationTime = System.currentTimeMillis();
                    resetActions = false;
                    actionsPerformed++;
                    Microbot.log("Started feeding brazier");
                    maybeNudgeMouse();
                }
            }

            /* reset the whole plan after last feed of the run */
            if (feedGoal > 0 && fedThisRun >= feedGoal) {
                targetRootsForThisRun = 0;
                rootsToChopGoal       = 0;
                fletchGoal            = 0;
                feedGoal              = 0;
                rootsChoppedThisRun   = 0;
                fletchedThisRun       = 0;
                fedThisRun            = 0;
            }

        } catch (Exception e) {
            System.err.println("Error in burn logs state: " + e.getMessage());
        }
    }


    /**
     * Gets total count of rejuvenation potions (all dose variations)
     */
    public static int getTotalRejuvenationPotions() {
        return Rs2Inventory.count("Rejuvenation potion (1)") + 
               Rs2Inventory.count("Rejuvenation potion (2)") + 
               Rs2Inventory.count("Rejuvenation potion (3)") + 
               Rs2Inventory.count("Rejuvenation potion (4)");
    }

    /**
     * Gets total count of actual rejuvenation potion doses
     */
    public static int getTotalRejuvenationDoses() {
        return (Rs2Inventory.count("Rejuvenation potion (1)") * 1) + 
               (Rs2Inventory.count("Rejuvenation potion (2)") * 2) + 
               (Rs2Inventory.count("Rejuvenation potion (3)") * 3) + 
               (Rs2Inventory.count("Rejuvenation potion (4)") * 4);
    }

    /**
     * Simplified - Handles getting concoctions from the crate
     */
    private void handleGetConcoctionsState(GameState gameState) {
        try {
            // Ensure we're on Wintertodt world before getting concoctions
            if (!ensureWintertodtWorldSafely()) {
                return; // Script stopped due to hop failure
            }

            // If we're outside, enter the game room first
            if (!WintertodtLocationManager.isInsideGameRoom() || Rs2Player.getWorldLocation().distanceTo(CRATE_STAND_LOCATION) > 6) {
                Rs2Walker.walkTo(CRATE_STAND_LOCATION, 6);
                Rs2Player.waitForWalking();
                return;
            }
            
            // Navigate to crate area
            if (Rs2Player.getWorldLocation().distanceTo(CRATE_STAND_LOCATION) <= 6 && Rs2Player.getWorldLocation().distanceTo(CRATE_STAND_LOCATION) > 1) {
                Rs2Walker.walkFastCanvas(CRATE_STAND_LOCATION);
                Rs2Player.waitForWalking();
                return;
            }

            // ROCK SOLID COUNTING
            int currentPotions = getTotalRejuvenationPotions();
            int currentConcoctions = Rs2Inventory.count(ItemID.REJUVENATION_POTION_UNF);
            int totalHealingItems = currentPotions + currentConcoctions; // What we can actually use
            int needed = config.healingAmount() - totalHealingItems;
            
            Microbot.log("CONCOCTIONS: Need " + needed + " more (potions: " + currentPotions + ", concoctions: " + currentConcoctions + ", target: " + config.healingAmount() + ")");
            
            if (needed <= 0) {
                Microbot.log("Have enough total healing items (" + totalHealingItems + "/" + config.healingAmount() + "), moving to herbs");
                changeState(State.GET_HERBS);
                return;
            }

            // Check inventory space and drop items if needed
            int emptySlots = Rs2Inventory.getEmptySlots();
            if (emptySlots == 0) {
                Microbot.log("Inventory full - dropping bruma items to make space for concoctions");
                
                // Drop bruma roots first
                if (Rs2Inventory.hasItem(ItemID.BRUMA_ROOT)) {
                    Rs2Inventory.drop(ItemID.BRUMA_ROOT);
                    sleepGaussian(300, 100);
                } 
                // If no roots, drop bruma kindlings
                else if (Rs2Inventory.hasItem(ItemID.BRUMA_KINDLING)) {
                    Rs2Inventory.drop(ItemID.BRUMA_KINDLING);
                    sleepGaussian(300, 100);
                } else {
                    Microbot.log("No bruma items to drop - inventory management issue");
                }
                return; // Try again next tick after dropping
            }

            // Find and interact with crate
            TileObject crate = Rs2GameObject.findObject(CRATE_OBJECT_ID, CRATE_LOCATION);
            if (crate == null) {
                crate = Rs2GameObject.getGameObjects(CRATE_OBJECT_ID).stream()
                    .filter(c -> c.getWorldLocation().distanceTo(CRATE_LOCATION) <= 2)
                    .findFirst()
                    .orElse(null);
            }
            
            if (crate != null) {
                if (Rs2GameObject.interact(crate, "Take-concoction")) {
                    // Wait for inventory change
                    int beforeCount = Rs2Inventory.count(ItemID.REJUVENATION_POTION_UNF);
                    sleepUntilTrue(() -> Rs2Inventory.count(ItemID.REJUVENATION_POTION_UNF) > beforeCount, 100, 3000);
                    actionsPerformed++;
                    Microbot.log("Got concoction!");
                } else {
                    Microbot.log("Failed to interact with crate, retrying...");
                }
            } else {
                Microbot.log("Could not find crate");
                // If we have some concoctions, try to proceed
                if (currentConcoctions > 0) {
                    changeState(State.GET_HERBS);
                } else {
                    sleepGaussian(2000, 500);
                }
            }

        } catch (Exception e) {
            System.err.println("Error in get concoctions state: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Simplified - Handles getting herbs from sprouting roots
     */
    private void handleGetHerbsState(GameState gameState) {
        try {
            // Ensure we're on Wintertodt world before getting herbs
            if (!ensureWintertodtWorldSafely()) {
                return; // Script stopped due to hop failure
            }

            // If we're outside, enter the game room first
            if (!WintertodtLocationManager.isInsideGameRoom() || Rs2Player.getWorldLocation().distanceTo(SPROUTING_ROOTS_STAND) > 6) {
                Rs2Walker.walkTo(SPROUTING_ROOTS_STAND, 6);
                Rs2Player.waitForWalking();
                return;
            }
            
            // ROCK SOLID COUNTING
            int currentPotions = getTotalRejuvenationPotions();
            int currentConcoctions = Rs2Inventory.count(ItemID.REJUVENATION_POTION_UNF);
            int currentHerbs = Rs2Inventory.count(ItemID.BRUMA_HERB);
            int totalHealingItems = currentPotions + currentConcoctions; // What we can actually use for healing
            int potionsWeMakeCanMake = Math.min(currentConcoctions, currentHerbs); // Potions we can make right now
            int totalAfterCombining = currentPotions + potionsWeMakeCanMake;
            
            Microbot.log("HERBS: Currently have " + totalHealingItems + "/" + config.healingAmount() + " healing items");
            Microbot.log("HERBS: After combining would have " + totalAfterCombining + "/" + config.healingAmount() + " (concoctions: " + currentConcoctions + ", herbs: " + currentHerbs + ")");
            
            // If we already have enough total healing items, go straight to combining
            if (totalAfterCombining >= config.healingAmount()) {
                Microbot.log("Have enough herbs for combining, moving to make potions");
                changeState(State.MAKE_POTIONS);
                return;
            }
            
            // We need more herbs - calculate how many
            int herbsNeeded = currentConcoctions - currentHerbs;
            
            // Navigate to sprouting roots
            if (Rs2Player.getWorldLocation().distanceTo(SPROUTING_ROOTS_STAND) <= 6 && Rs2Player.getWorldLocation().distanceTo(SPROUTING_ROOTS_STAND) > 1) {
                Rs2Walker.walkFastCanvas(SPROUTING_ROOTS_STAND);
                Rs2Player.waitForWalking();
                return;
            }

            // If we're animating (picking), just wait
            if (Rs2Player.isAnimating()) {
                Microbot.log("Picking herbs... (need " + herbsNeeded + " more)");
                return;
            }

            // Check inventory space and drop items if needed
            int emptySlots = Rs2Inventory.getEmptySlots();
            if (emptySlots == 0) {
                Microbot.log("Inventory full - dropping bruma items to make space for herbs");
                
                // Drop bruma roots first
                if (Rs2Inventory.hasItem(ItemID.BRUMA_ROOT)) {
                    Rs2Inventory.drop(ItemID.BRUMA_ROOT);
                    sleepGaussian(300, 100);
                } 
                // If no roots, drop bruma kindlings
                else if (Rs2Inventory.hasItem(ItemID.BRUMA_KINDLING)) {
                    Rs2Inventory.drop(ItemID.BRUMA_KINDLING);
                    sleepGaussian(300, 100);
                } else {
                    Microbot.log("No bruma items to drop - inventory management issue");
                }
                return; // Try again next tick after dropping
            }

            // Find sprouting roots and pick
            TileObject roots = Rs2GameObject.findObject(SPROUTING_ROOTS_OBJECT_ID, SPROUTING_ROOTS);
            if (roots == null) {
                roots = Rs2GameObject.getGameObjects(SPROUTING_ROOTS_OBJECT_ID).stream()
                    .filter(r -> r.getWorldLocation().distanceTo(SPROUTING_ROOTS) <= 2)
                    .findFirst()
                    .orElse(null);
            }
            
            if (roots != null) {
                Microbot.log("Picking herbs (need " + herbsNeeded + " more)");
                if (Rs2GameObject.interact(roots, "Pick")) {
                    sleepUntilTrue(() -> Rs2Player.isAnimating(), 100, 2000);
                    actionsPerformed++;
                } else {
                    Microbot.log("Failed to interact with sprouting roots");
                }
            } else {
                Microbot.log("Could not find sprouting roots");
                // If we have some herbs and concoctions, try to make potions
                if (currentHerbs > 0 && currentConcoctions > 0) {
                    changeState(State.MAKE_POTIONS);
                } else {
                    sleepGaussian(2000, 500);
                }
            }

        } catch (Exception e) {
            System.err.println("Error in get herbs state: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles combining concoctions and herbs WITHOUT animation checks
     */
    private void handleMakePotionsState(GameState gameState) {
        try {
            // Ensure we're on Wintertodt world before making potions
            if (!ensureWintertodtWorldSafely()) {
                return; // Script stopped due to hop failure
            }

            // ROCK SOLID COUNTING
            int currentPotions = getTotalRejuvenationPotions();
            int currentConcoctions = Rs2Inventory.count(ItemID.REJUVENATION_POTION_UNF);
            int currentHerbs = Rs2Inventory.count(ItemID.BRUMA_HERB);
            
            Microbot.log("COMBINING: potions: " + currentPotions + ", concoctions: " + currentConcoctions + ", herbs: " + currentHerbs + ", target: " + config.healingAmount());
            
            // Check if we have enough potions now
            if (currentPotions >= config.healingAmount()) {
                Microbot.log("Have enough rejuvenation potions (" + currentPotions + "/" + config.healingAmount() + "), cleaning up and organizing inventory");
                organizeRejuvenationPotionsInInventory();

                // Handle break opportunity after completing potion preparation
                if (handleBreakOpportunity()) {
                    return; // Break started, exit function
                }

                changeState(State.ENTER_ROOM);
                return;
            }
            
            // Check if we need more ingredients
            if (currentConcoctions <= 0 && currentPotions < config.healingAmount()) {
                Microbot.log("Need more concoctions to reach target");
                changeState(State.GET_CONCOCTIONS);
                return;
            }
            
            if (currentHerbs <= 0 && currentConcoctions > 0 && currentPotions < config.healingAmount()) {
                Microbot.log("Need more herbs to combine");
                changeState(State.GET_HERBS);
                return;
            }

            // Check if player can manually combine (has completed Druidic Ritual)
            boolean canManuallyMake = canManuallyMakePotions();
            
            if (currentConcoctions > 0 && currentHerbs > 0) {
                if (canManuallyMake) {
                    // Use manual combining method (preferred if available)
                    int potionsToMake = Math.min(currentConcoctions, currentHerbs);
                    Microbot.log("Using manual combination method (will make " + potionsToMake + " potions)");
                    if (Rs2Inventory.combineClosest(ItemID.REJUVENATION_POTION_UNF, ItemID.BRUMA_HERB)) {
                        // Small delay to let the combination process
                        sleepUntilTrue(() -> Rs2Inventory.count(ItemID.REJUVENATION_POTION_1) + Rs2Inventory.count(ItemID.REJUVENATION_POTION_2) + Rs2Inventory.count(ItemID.REJUVENATION_POTION_3) + Rs2Inventory.count(ItemID.REJUVENATION_POTION_4) >= potionsToMake, 100, 5000);
                        actionsPerformed++;
                        Microbot.log("Manual combination attempted - checking results next tick");
                    } else {
                        Microbot.log("Failed to start manual combining - retrying next tick");
                        sleepGaussian(1000, 300);
                    }
                } else {
                    // Use Brew'ma NPC method
                    Microbot.log("Player cannot manually combine potions - using Brew'ma NPC method");
                    if (useBreadmaNpcForPotions(currentConcoctions, currentHerbs)) {
                        Microbot.log("Brew'ma NPC combination completed");
                    } else {
                        Microbot.log("Failed to use Brew'ma NPC - retrying next tick");
                        sleepGaussian(1000, 300);
                    }
                }
            } else {
                // Shouldn't get here, but handle it
                Microbot.log("No valid combinations possible - checking what we need");
                if (currentPotions >= config.healingAmount()) {
                    cleanupExtraResources();
                    changeState(State.ENTER_ROOM);
                } else if (currentConcoctions <= 0) {
                    changeState(State.GET_CONCOCTIONS);
                } else {
                    changeState(State.GET_HERBS);
                }
            }
        } catch (Exception e) {
            System.err.println("Error in make potions state: " + e.getMessage());
        }
    }

    /**
     * Check if player can manually make rejuvenation potions
     * Returns true if player has completed Druidic Ritual
     */
    private boolean canManuallyMakePotions() {
        try {
            // Check if player has completed Druidic Ritual
            QuestState druidicRitual = Rs2Player.getQuestState(Quest.DRUIDIC_RITUAL);
            return druidicRitual == QuestState.FINISHED;
        } catch (Exception e) {
            Microbot.log("Error checking druidic ritual, defaulting to NPC method: " + e.getMessage());
            return false;
        }
    }

    /**
     * Use Brew'ma NPC to convert rejuvenation potions (unf) and herbs into complete potions
     */
    private boolean useBreadmaNpcForPotions(int concoctions, int herbs) {
        try {
            // Walk to Brew'ma NPC if not nearby
            if (Rs2Player.getWorldLocation().distanceTo(BREWMA_NPC_LOCATION) > 10) {
                Microbot.log("Walking to Brew'ma NPC at " + BREWMA_NPC_LOCATION);
                Rs2Walker.walkTo(BREWMA_NPC_INTERACT_LOCATION);
                sleepUntilTrue(() -> Rs2Player.getWorldLocation().distanceTo(BREWMA_NPC_LOCATION) <= 10, 100, 10000);
                return false; // Return false to retry next tick after walking
            }

            // Find Brew'ma NPC
            Rs2NpcModel brewmaNpc = Rs2Npc.getNpcInLineOfSight("Brew'ma");
            if (brewmaNpc == null) {
                Microbot.log("Could not find Brew'ma NPC - walking closer");
                Rs2Walker.walkFastCanvas(BREWMA_NPC_INTERACT_LOCATION);
                return false;
            }

            // Store counts before interaction
            int potionsBeforeInteraction = getTotalRejuvenationPotions();
            int concoctionsBeforeInteraction = Rs2Inventory.count(ItemID.REJUVENATION_POTION_UNF);
            int herbsBeforeInteraction = Rs2Inventory.count(ItemID.BRUMA_HERB);

            Microbot.log("Interacting with Brew'ma NPC to convert " + concoctions + " concoctions and " + herbs + " herbs");
            
            // First, select the herb from inventory
            if (Rs2Inventory.getSelectedItemId() != ItemID.BRUMA_HERB && !Rs2Inventory.interact(ItemID.BRUMA_HERB, "Use")) {
                Microbot.log("Failed to select herb from inventory");
                return false;
            }

            // Wait a moment for the herb to be selected
            sleepGaussian(900, 200);

            if (Rs2Inventory.getSelectedItemId() != ItemID.BRUMA_HERB) {
                Microbot.log("Failed to select herb from inventory, retrying...");
                return false;
            }

            Microbot.log("Selected herb from inventory");
            
            // Then click on the Brew'ma NPC while herb is selected
            if (Rs2Npc.interact(brewmaNpc, "Use")) {
                Microbot.log("Used herb on Brew'ma NPC - waiting for conversion");
                
                // Wait for the conversion to complete (should be instant according to user)
                sleepUntilTrue(() -> {
                    int currentPotions = getTotalRejuvenationPotions();
                    int currentConcoctions = Rs2Inventory.count(ItemID.REJUVENATION_POTION_UNF);
                    int currentHerbs = Rs2Inventory.count(ItemID.BRUMA_HERB);
                    
                    // Check if conversion happened (more potions, fewer ingredients)
                    return currentPotions > potionsBeforeInteraction || 
                           (currentConcoctions < concoctionsBeforeInteraction || currentHerbs < herbsBeforeInteraction);
                }, 100, 5000);

                // Verify the conversion worked
                int potionsAfterInteraction = getTotalRejuvenationPotions();
                int concoctionsAfterInteraction = Rs2Inventory.count(ItemID.REJUVENATION_POTION_UNF);
                int herbsAfterInteraction = Rs2Inventory.count(ItemID.BRUMA_HERB);

                if (potionsAfterInteraction > potionsBeforeInteraction) {
                    Microbot.log("Brew'ma NPC successfully converted ingredients! Potions: " + 
                               potionsBeforeInteraction + " -> " + potionsAfterInteraction);
                    actionsPerformed++;
                    return true;
                } else {
                    Microbot.log("Brew'ma NPC interaction may have failed - no potion increase detected");
                    return false;
                }
            } else {
                Microbot.log("Failed to use herb on Brew'ma NPC");
                return false;
            }

        } catch (Exception e) {
            Microbot.log("Error using Brew'ma NPC for potions: " + e.getMessage());
            return false;
        }
    }

    /**
     * Organizes rejuvenation potions in the inventory for optimal layout
     */
    private void organizeRejuvenationPotionsInInventory() {
        try {
            Microbot.log("Cleaning up extra resources before organizing potions...");
            
            // Clean up first to make space
            cleanupExtraResources();
            sleepGaussian(500, 200); // Wait for cleanup to complete
            
            Microbot.log("Organizing rejuvenation potions in inventory...");
            
            // Get all rejuvenation potions (all dose variations)
            List<Rs2ItemModel> allPotions = new ArrayList<>();
            for (Rs2ItemModel item : Rs2Inventory.all()) {
                if (item != null && item.getName() != null && item.getName().startsWith("Rejuvenation potion")) {
                    allPotions.add(item);
                }
            }
            
            if (allPotions.isEmpty()) {
                Microbot.log("No rejuvenation potions found to organize");
                return;
            }
            
            // Sort potions by dose (4-dose first, then 3, 2, 1)
            allPotions.sort((a, b) -> {
                int doseA = extractDoseFromName(a.getName());
                int doseB = extractDoseFromName(b.getName());
                return Integer.compare(doseB, doseA); // Higher dose first
            });
            
            // Target slots for food items (leftmost column: 0, 4, 8, 12, 16, 20, 24)
            int[] targetSlots = {0, 4, 8, 12, 16, 20, 24}; // Upper left column going down
            
            int slotIndex = 0;
            for (Rs2ItemModel potion : allPotions) {
                if (slotIndex >= targetSlots.length) {
                    break; // No more target slots available
                }
                
                int targetSlot = targetSlots[slotIndex];
                
                // Only move if not already in target position
                if (potion.getSlot() != targetSlot) {
                    Microbot.log("Moving " + potion.getName() + " from slot " + potion.getSlot() + " to slot " + targetSlot);
                    
                    if (Rs2Inventory.moveItemToSlot(potion, targetSlot)) {
                        sleepUntilTrue(() -> Rs2Inventory.slotContains(targetSlot, potion.getName()), 100, 2000);
                        sleepGaussian(50, 20); // Small delay between moves for natural behavior
                    }
                } else {
                    Microbot.log("Potion " + potion.getName() + " already in target slot " + targetSlot);
                }
                
                slotIndex++;
            }
            
            Microbot.log("Inventory organization completed - potions arranged in leftmost column starting from slot 0");
            
        } catch (Exception e) {
            Microbot.log("Error organizing rejuvenation potions: " + e.getMessage());
        }
    }
    
    /**
     * Extracts the dose number from a potion name (e.g., "Rejuvenation potion (4)" -> 4)
     */
    private int extractDoseFromName(String name) {
        try {
            if (name.contains("(4)")) return 4;
            if (name.contains("(3)")) return 3;
            if (name.contains("(2)")) return 2;
            if (name.contains("(1)")) return 1;
            return 0; // Unknown dose
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Simplified cleanup
     */
    private void cleanupExtraResources() {
        try {
            Microbot.log("Cleaning up extra resources...");
            
            // Drop extra concoctions
            if (Rs2Inventory.hasItem(ItemID.REJUVENATION_POTION_UNF)) {
                sleepGaussian(300, 100);
                Rs2Inventory.dropAll(ItemID.REJUVENATION_POTION_UNF);
            }
            
            // Drop extra herbs
            if (Rs2Inventory.hasItem(ItemID.BRUMA_HERB)) {
                sleepGaussian(300, 100);
                Rs2Inventory.dropAll(ItemID.BRUMA_HERB);
            }
            
            int finalPotions = getTotalRejuvenationPotions();
            Microbot.log("Cleanup completed - final potion count: " + finalPotions);
            
        } catch (Exception e) {
            Microbot.log("Error during cleanup: " + e.getMessage());
        }
    }

    /**
     * Determines if the player should start burning logs.
     *
     * @param gameState Current game state
     * @return true if should burn logs
     */
    private boolean shouldBurnLogs(GameState gameState) {
        if (!gameState.hasItemsToBurn) {
            setLockState(State.BURN_LOGS, false);
            return false;
        }
        changeState(State.BURN_LOGS, false);  // Don't lock - allows natural state transitions
        return true;
    }

    /**
     * Handles eating and health management.
     *
     * @param gameState Current game state
     * @return true if food was consumed
     */
    private boolean handleEating(GameState gameState) {
        if (gameState.playerWarmth <= config.eatAtWarmthLevel()) {
            try {
                /* Always deselect knife before eating */
                if (isKnifeSelected()) {
                    deselectSelectedItem();
                    sleepGaussian(100, 50);  // Small delay to ensure deselection
                }

                if (usesPotions) {
                    // Retry logic for potion consumption with dose verification
                    int maxRetries = 3;
                    for (int attempt = 1; attempt <= maxRetries; attempt++) {
                        List<Rs2ItemModel> rejuvenationPotions = Rs2Inventory.getPotions();
                        if (rejuvenationPotions.isEmpty()) {
                            break; // No potions available
                        }

                        // Count total doses before drinking
                        int dosesBeforeDrinking = getTotalRejuvenationDoses();
                        
                        // Attempt to drink potion
                        Rs2Inventory.interact(rejuvenationPotions.get(0), "Drink");
                        Rs2Inventory.waitForInventoryChanges(2000);
                        
                        // Count total doses after drinking
                        int dosesAfterDrinking = getTotalRejuvenationDoses();
                        int dosesConsumed = dosesBeforeDrinking - dosesAfterDrinking;
                        
                        if (dosesConsumed >= 1) {
                            // Successfully consumed at least 1 dose
                            plugin.setFoodConsumed(plugin.getFoodConsumed() + 1);
                            resetActions = true;
                            fletchingState.stopFletching(FletchingInterruptType.PLAYER_ATE);
                            return true;
                        } else {
                            // Failed to consume, retry if attempts remaining
                            System.out.println("Failed to consume potion dose on attempt " + attempt + 
                                             ". Doses before: " + dosesBeforeDrinking + 
                                             ", after: " + dosesAfterDrinking);
                            if (attempt < maxRetries) {
                                sleepGaussian(500, 200); // Small delay before retry
                            }
                        }
                    }
                } else {
                    // Retry logic for regular food consumption
                    int maxRetries = 3;
                    for (int attempt = 1; attempt <= maxRetries; attempt++) {
                        if (Rs2Player.useFood()) {
                            Rs2Inventory.waitForInventoryChanges(2000);
                            plugin.setFoodConsumed(plugin.getFoodConsumed() + 1);
                            Rs2Inventory.dropAll("jug");
                            resetActions = true;
                            fletchingState.stopFletching(FletchingInterruptType.PLAYER_ATE);
                            return true;
                        } else {
                            // Failed to eat food, retry if attempts remaining
                            System.out.println("Failed to consume food on attempt " + attempt);
                            if (attempt < maxRetries) {
                                sleepGaussian(500, 200); // Small delay before retry
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error handling eating: " + e.getMessage());
            }
        }
        return false;
    }

    /**
     * Determines if we should start a new run of chopping or continue the current one.
     * This method embodies the "Action Plan" logic.
     *
     * @param gameState Current game state
     * @return true if the bot should be in the CHOP_ROOTS state
     */
    private boolean shouldStartOrContinueChopping(GameState gameState) {
        // Don't chop if we are full or have items but our plan is complete.
        if (gameState.inventoryFull || (gameState.hasItemsToBurn && targetRootsForThisRun == 0)) {
            return false;
        }

        /* ensure we are close enough to the brazier side first */
        double dist = Rs2Player.getWorldLocation()
                               .distanceTo(config.brazierLocation().getBRAZIER_LOCATION());
        if (dist > CHOPPING_RADIUS) {
            navigateToBrazier();           // walk closer before planning chop
        }

        // --- Plan a new run if we don't have one ---
        if (targetRootsForThisRun == 0) {
            // Only plan a new run if we have nothing to burn.
            if (gameState.hasItemsToBurn) {
                return false;
            }

            // Calculate the total time it takes to get and process one log
            double singleLogProcessingTimeSec = (avgChopMs + (config.fletchRoots() ? avgFletchMs : 0) + avgFeedMs + addToEstimatedTimePerCycleMs) / 1000.0;
            if (singleLogProcessingTimeSec <= 0.1) {
                return false; // Avoid division by zero if averages are not ready
            }

            // Calculate how many logs we can realistically process in the time remaining
            double availableTimeSec = estimatedSecondsLeft - SAFETY_BUFFER_SEC; // Extra buffer for starting a new run
            int maxLogsInTime = (int) (availableTimeSec / singleLogProcessingTimeSec);

            // We can't chop more than our available inventory space
            int availableInvSlots = Rs2Inventory.getEmptySlots();

            // The plan is to chop the minimum of what we have time for vs what we have space for
            int calculatedTarget = Math.min(maxLogsInTime + EXTRA_ROOTS_BUFFER, availableInvSlots);

            if (calculatedTarget <= 0) {
                targetRootsForThisRun = 0;
                rootsChoppedThisRun = 0;
                return false; // Not enough time or space to even start
            }

            targetRootsForThisRun = calculatedTarget;
            rootsChoppedThisRun = 0;
            rootsToChopGoal = targetRootsForThisRun; // Update for overlay
            Microbot.log("New Action Plan: Chop " + targetRootsForThisRun + " roots.");

            /* set fletch / feed goals & reset progress  */
            fletchGoal      = config.fletchRoots() ? targetRootsForThisRun : 0;
            feedGoal        = targetRootsForThisRun;
            fletchedThisRun = 0;
            fedThisRun      = 0;
        }

        // --- Execute the current plan ---
        int currentRoots = Rs2Inventory.count(ItemID.BRUMA_ROOT) + Rs2Inventory.count(ItemID.BRUMA_KINDLING);
        if (currentRoots < targetRootsForThisRun) {
            changeState(State.CHOP_ROOTS, true);
            return true; // We need to chop more to meet our goal
        } else {
            // We have met our goal for this run. Reset the plan for the next run.
            targetRootsForThisRun = 0;
            rootsChoppedThisRun = 0;
            // Don't reset rootsToChopGoal here, so overlay shows the last goal until a new one is made.
            return false; // Goal met, stop chopping.
        }
    }

    /**
     * Determines if the player should light the brazier.
     *
     * @param gameState Current game state
     * @return true if should light brazier
     */
    private boolean shouldLightBrazier(GameState gameState) {
        // Don't try to light if already in lighting state to avoid infinite loops
        if (state == State.LIGHT_BRAZIER) {
            return false;
        }
        
        if (!gameState.isWintertodtAlive) return false;          // round not active
        if (gameState.wintertodtHp == 0)   return false;         // boss just died
        if (gameState.needBanking)         return false;
        
        // Allow lighting during CHOP_ROOTS only if it's a priority at round start
        if (state == State.CHOP_ROOTS && !shouldPriorizeBrazierAtStart) return false;

        if (gameState.brazier == null || gameState.burningBrazier != null) {
            setLockState(State.LIGHT_BRAZIER, false);
            // Reset priority flag if brazier is already lit or doesn't exist
            if (shouldPriorizeBrazierAtStart) {
                shouldPriorizeBrazierAtStart = false;
                Microbot.log("Brazier priority reset - brazier already lit or unavailable");
            }
            return false;
        }

        changeState(State.LIGHT_BRAZIER, true);
        return true;
    }

    /**
     * Drops unnecessary items based on configuration.
     */
    private void dropUnnecessaryItems() {
        try {
            // Drop knife if fletching is disabled - Here we only drop normal knife, we will not drop Fletching Knife
            if (!config.fletchRoots() && Rs2Inventory.hasItem(ItemID.KNIFE)) {
                sleepGaussian(300, 100);
                Rs2Inventory.drop(ItemID.KNIFE);
            }

            // Drop hammer if fixing is disabled
            if (!config.fixBrazier() && Rs2Inventory.hasItem(ItemID.HAMMER)) {
                sleepGaussian(300, 100);
                Rs2Inventory.drop(ItemID.HAMMER);
            }

            // Drop tinderbox if using bruma torch
            if ((Rs2Equipment.isWearing(ItemID.BRUMA_TORCH) ||
                    Rs2Equipment.isWearing(ItemID.BRUMA_TORCH_OFFHAND)) &&
                    Rs2Inventory.hasItem(ItemID.TINDERBOX)) {
                sleepGaussian(300, 100);
                Rs2Inventory.drop(ItemID.TINDERBOX);
            }

        } catch (Exception e) {
            System.err.println("Error dropping items: " + e.getMessage());
        }
    }

    /**
     * Navigates to the preferred brazier location.
     */
    private void navigateToBrazier() {
        try {
            WorldPoint brazierLocation = config.brazierLocation().getBRAZIER_LOCATION();
            double distance = Rs2Player.getWorldLocation().distanceTo(brazierLocation);

            if (!BreakHandlerScript.isLockState()) {
                BreakHandlerScript.setLockState(true);
                Microbot.log("Locking break handler");
            }

            if (distance > 8) {
                Rs2Walker.walkTo(brazierLocation, 3);
                Rs2Player.waitForWalking();
            } else if (distance > 2) {
                Rs2Walker.walkFastCanvas(brazierLocation);
                sleepGaussian(400, 100);
            }

        } catch (Exception e) {
            System.err.println("Error navigating to brazier: " + e.getMessage());
        }
    }

    /**
     * Dodges snowfall damage by tracking specific projectiles.
     */
    private void dodgeSnowfallDamage(GameState gameState) {
        try {
            if (!resetActions) {
                // Track projectiles to detect incoming snowfall damage
                Deque<Projectile> projectiles = Microbot.getClient().getProjectiles();
                
                for (Projectile projectile : projectiles) {
                    if (projectile.getId() == 501 && projectile.getHeight() == 150) { //Somewhere is said this -1268, by testing runelite shows 150
                        // Check if there's a graphics object with ID 502 within 1 tile
                        boolean hasNearbyGraphics = false;
                        WorldPoint playerLocation = Rs2Player.getWorldLocation();
                        
                        for (GraphicsObject graphicsObject : Microbot.getClient().getGraphicsObjects()) {
                            if (graphicsObject.getId() == 502) {
                                WorldPoint dangerZone = WorldPoint.fromLocalInstance(
                                        Microbot.getClient(), graphicsObject.getLocation());
                                
                                if (dangerZone.distanceTo(playerLocation) <= 1) {
                                    hasNearbyGraphics = true;
                                    break;
                                }
                            }
                        }
                        
                        if (hasNearbyGraphics) {
                            // 80% chance to dodge when all conditions are met
                            if (random.nextInt(100) < 80) {
                                // Dodge by moving one tile south
                                WorldPoint safeSpot = new WorldPoint(
                                        Rs2Player.getWorldLocation().getX(),
                                        Rs2Player.getWorldLocation().getY() - 1,
                                        Rs2Player.getWorldLocation().getPlane()
                                );

                                Rs2Walker.walkFastCanvas(safeSpot);
                                Rs2Player.waitForWalking(1500);
                                resetActions = true;
                                Microbot.log("Dodged snowfall damage (80% chance triggered)");
                                Microbot.log("Waiting for burning brazier to go out after snowfall damage...");
                                Rs2GameObject.hoverOverObject(Rs2GameObject.findReachableObject("brazier", false, 4, Rs2Player.getWorldLocation()));

                                boolean brazierWentOut = sleepUntilTrue(
                                        () -> {
                                            // Wait until burning brazier is gone OR we're no longer in burn state
                                            GameObject burningBrazier = Rs2GameObject.getGameObject(BURNING_BRAZIER_29314,5);
                                            return burningBrazier == null || (state != State.BURN_LOGS && state != State.FLETCH_LOGS);
                                        },
                                        100,  // Check every 100ms
                                        5000 // Timeout after 5 seconds
                                );

                                if (brazierWentOut) {
                                    Microbot.log("Brazier state changed, continuing...");
                                } else {
                                    Microbot.log("Timeout waiting for brazier change - continuing anyway");
                                }
                            } else {
                                Microbot.log("Snowfall detected but purposely NOT dodging (20% chance - staying put for realism ;) )");
                            }
                            
                            break; // Only need to process once per projectile detection
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error dodging snowfall: " + e.getMessage());
        }
    }

    /**
     * Handles the banking logic for food and supplies.
     * 
     * Flow:
     * 1. Walk to bank (if needed)
     * 2. Open bank (if needed) 
     * 3. Setup gear + tools via checkAndUpgradeGear() [includes inventoryManager.setupInventory()]
     * 4. Handle food withdrawal and healing
     * 
     * Returns false when waiting for an action to complete, true when banking is done.
     */
    private boolean executeBankingLogic() {
        try {
            // Navigate to bank first
            if (Rs2Player.getWorldLocation().distanceTo(BANK_LOCATION) > 6) {
                Rs2Walker.walkTo(BANK_LOCATION);
                Rs2Player.waitForWalking();
                return false; // Wait for walking to complete
            }

            // Prevent bug that causes bot to not being able to wear items in bank by adding inventory open command first
            if (!Rs2Inventory.isOpen()) {
                Rs2Inventory.open();
            }

            // Open bank
            if (!Rs2Bank.isOpen()) {
                Rs2Bank.useBank();
                return false; // Wait for bank to open
            }

            // Setup gear and tools (this already calls inventoryManager.setupInventory())
            if (!checkAndUpgradeGear()) {
                return false; // Gear setup failed
            }

            // Handle food and healing
            return handleFoodAndHealing();

        } catch (Exception e) {
            System.err.println("Error in banking logic: " + e.getMessage());
            return false;
        }
    }

    /**
     * Handles food withdrawal and healing logic.
     */
    private boolean handleFoodAndHealing() {
        try {
            String foodName = config.food().getName();
            int requiredAmount = config.healingAmount();

            // Heal up if needed and we have food
            if (!Rs2Player.isFullHealth() && Rs2Inventory.hasItem(foodName, false)) {
                eatAt(99);
                return false; // Wait for healing to complete
            }

            // Check if we already have enough food
            if (Rs2Inventory.hasItemAmount(foodName, requiredAmount)) {
                return true; // Banking complete
            }

            // Check food availability in bank
            if (!Rs2Bank.hasBankItem(foodName, requiredAmount, true)) {
                Microbot.showMessage("Insufficient food in bank! Please restock.");
                Microbot.pauseAllScripts.compareAndSet(false, true);
                return false;
            }

            // Calculate and withdraw needed food
            int currentFoodCount = (int) Rs2Inventory.getInventoryFood().stream().count();
            int foodNeeded = requiredAmount - currentFoodCount;
            
            if (foodNeeded > 0) {
                Rs2Bank.withdrawX(config.food().getId(), foodNeeded);
                // Wait for withdrawal to complete
                return sleepUntilTrue(
                    () -> Rs2Inventory.hasItemAmount(foodName, requiredAmount, false, true),
                    100, 5000
                );
            }

            return true; // No food needed

        } catch (Exception e) {
            System.err.println("Error in food and healing logic: " + e.getMessage());
            return false;
        }
    }


    /**
     * Gets the current warmth level from the game interface.
     *
     * @return warmth level percentage (0-100)
     */
    public static int getWarmthLevel() {
        try {
            String warmthWidgetText = Rs2Widget.getChildWidgetText(396, 20);

            if (warmthWidgetText == null || warmthWidgetText.isEmpty()) {
                return 100; // Default to full warmth if widget not found
            }

            // Primary pattern: digits before %
            Pattern pattern = Pattern.compile("(\\d+)%");
            Matcher matcher = pattern.matcher(warmthWidgetText);

            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }

            // Fallback pattern: any digits
            Pattern fallbackPattern = Pattern.compile("\\d+");
            Matcher fallbackMatcher = fallbackPattern.matcher(warmthWidgetText);

            if (fallbackMatcher.find()) {
                return Integer.parseInt(fallbackMatcher.group());
            }

            return 100; // Default if no pattern matches

        } catch (Exception e) {
            System.err.println("Error getting warmth level: " + e.getMessage());
            return 100; // Safe default
        }
    }

    /**
     * Tracks performance metrics and handles consecutive failures.
     *
     * @param loopStartTime Start time of the current loop
     */
    private void trackPerformance(long loopStartTime) {
        long loopTime = System.currentTimeMillis() - loopStartTime;

        // Log performance occasionally
        if (System.currentTimeMillis() - lastPerformanceCheck > 60000) { // Every minute
            System.out.println(String.format("Performance: %d actions in last minute, avg loop time: %dms",
                    actionsPerformed, loopTime));

            // Reset counters
            actionsPerformed = 0;
            lastPerformanceCheck = System.currentTimeMillis();

            // Reset consecutive failures if we're performing actions
            if (actionsPerformed > 0) {
                consecutiveFailures = 0;
            }
        }

        // Handle too many consecutive failures
        if (consecutiveFailures > 10) {
            Microbot.log("Too many consecutive failures, resetting state");
            setLockState(state, false);
            resetActions = true;
            consecutiveFailures = 0;
        }
    }



    /**
     * Determines if a break should be triggered based on various conditions.
     *
     * @return true if break should be triggered
     */
    private boolean shouldTriggerBreak() {
        // Check if new custom break system is active
        if (breakManager != null && WintertodtBreakManager.isBreakActive()) {
            return true;
        }
        
        // Check if break handler has a break active
        if (BreakHandlerScript.isBreakActive()) {
            return true;
        }
        
        return false;
    }

    /**
     * Handles the triggering and execution of breaks.
     */
    private void handleBreakTrigger() {
        try {
            // Handle new custom break system
            if (breakManager != null && WintertodtBreakManager.isBreakActive()) {
                // Custom break manager is handling the break, we just pause
                Microbot.log("Custom break system active, pausing script");
                return;
            }
            
            // If break handler has a break active, respect it
            if (BreakHandlerScript.isBreakActive()) {
                wasOnBreakHandlerBreak = true;
                Microbot.log("Break handler break active, pausing script");
                return;
            }
            
        } catch (Exception e) {
            System.err.println("Error handling break trigger: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Standardized break opportunity handler.
     * 
     * This function should be called at safe points where breaks are allowed to start.
     * It handles all the standard break preparation steps:
     * 1. Unlocks break handler if locked
     * 2. Waits briefly for break system to process
     * 3. Checks if breaks should trigger
     * 4. Handles break trigger if needed
     * 
     * @return true if a break has started (caller should return immediately), 
     *         false if no break started (safe to continue)
     */
    private boolean handleBreakOpportunity() {
        try {
            // Unlock break handler if it's locked to allow breaks to start
            if (BreakHandlerScript.isLockState()) {
                BreakHandlerScript.setLockState(false);
                Microbot.log("Unlocking break handler for break opportunity");
                sleep(1000); // Give it a moment to unlock
            }

            // Check if breaks should trigger and handle if needed
            if (shouldTriggerBreak()) {
                handleBreakTrigger();
                return true; // Break started, caller should return immediately
            }

            // Update break manager
            if (breakManager != null) {
                breakManager.update();
                sleep(200);
            }

            // Check if breaks should trigger and handle if needed
            if (shouldTriggerBreak()) {
                handleBreakTrigger();
                return true; // Break started, caller should return immediately
            }
            
            return false; // No break started, safe to continue
            
        } catch (Exception e) {
            System.err.println("Error in break opportunity handler: " + e.getMessage());
            e.printStackTrace();
            return false; // On error, assume no break and continue
        }
    }

    /**
     * Handles script exceptions with proper logging and recovery.
     *
     * @param ex The exception to handle
     */
    private void handleScriptException(Exception ex) {
        Microbot.log("Script error: " + ex.getMessage());
        ex.printStackTrace();
        consecutiveFailures++;

        // Reset state on critical errors
        if (consecutiveFailures > 5) {
            setLockState(state, false);
            resetActions = true;
        }
    }

    /**
     * Checks for gear upgrades during banking with robust retry logic.
     * Now returns success/failure status and retries on failures.
     */
    private boolean checkAndUpgradeGear() {
        /*
         * Enhanced version with retry logic:
         * Every time we stand in a bank we –
         *   1) run a FULL `WintertodtGearManager.setupOptimalGear()` pass
         *      (this returns quickly if equipment is already optimal),
         *   2) run `inventoryManager.setupInventory()` to ensure tools are in
         *      their designated slots on the right-hand column.
         * 
         * NEW: Now includes retry logic and proper error handling
         */
        
        int maxRetries = 3;
        int retryDelay = 1000; // 1 second between retries
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Microbot.log("Running gear + inventory setup (attempt " + attempt + "/" + maxRetries + ")...");

                // Ensure bank is still open
                if (!Rs2Bank.isOpen()) {
                    Microbot.log("Bank closed during gear setup - reopening...");
                    if (!Rs2Bank.useBank()) {
                        Microbot.log("Failed to reopen bank on attempt " + attempt);
                        if (attempt < maxRetries) {
                            sleepGaussian(retryDelay, 200);
                            continue;
                        }
                        return false;
                    }
                    sleepUntilTrue(Rs2Bank::isOpen, 100, 3000);
                }

                // --- 1) Gear (equipment) optimisation ---------------------------------
                WintertodtGearManager gearManager = new WintertodtGearManager(config);
                boolean gearPassOk = gearManager.setupOptimalGear();

                if (!gearPassOk) {
                    Microbot.log("Gear setup failed on attempt " + attempt);
                    if (attempt < maxRetries) {
                        sleepGaussian(retryDelay, 200);
                        continue;
                    }
                    Microbot.log("Gear setup failed after " + maxRetries + " attempts");
                    return false;
                }

                Microbot.log("Gear setup completed successfully");
                logCurrentGearSetup(gearManager);

                // --- 2) Inventory tool layout -----------------------------------------
                if (inventoryManager != null) {
                    boolean invOk = inventoryManager.setupInventory();
                    if (!invOk) {
                        Microbot.log("Inventory setup failed on attempt " + attempt);
                        if (attempt < maxRetries) {
                            sleepGaussian(retryDelay, 200);
                            continue;
                        }
                        Microbot.log("Inventory setup failed after " + maxRetries + " attempts");
                        return false;
                    }
                    
                    Microbot.log("Inventory setup completed successfully");
                } else {
                    Microbot.log("Warning: Inventory manager is null - cannot setup inventory");
                    return false;
                }



                // All steps succeeded!
                Microbot.log("Complete gear + inventory setup successful!");
                return true;

            } catch (Exception e) {
                Microbot.log("Error during gear/inventory setup (attempt " + attempt + "): " + e.getMessage());
                e.printStackTrace();
                
                if (attempt < maxRetries) {
                    sleepGaussian(retryDelay, 200);
                    continue;
                }
                
                Microbot.log("Gear setup failed with exception after " + maxRetries + " attempts");
                return false;
            }
        }
        
        return false; // Should never reach here, but just in case
    }


    
    /**
     * Logs current gear setup for debugging.
     */
    private void logCurrentGearSetup(WintertodtGearManager gearManager) {
        List<String> gearLog = gearManager.getGearAnalysisLog();
        for (String logEntry : gearLog) {
            Microbot.log("Gear: " + logEntry);
        }
    }

    /**
     * Ensures the player is on a Wintertodt world, hopping if necessary.
     * Only hops when in a safe location (not in combat, not interacting, etc.).
     * Stops script and logs out on failure.
     * 
     * @return true if on Wintertodt world or successfully hopped, false if failed (script should stop)
     */
    private boolean ensureWintertodtWorldSafely() {
        try {
            // Skip if we've already checked and confirmed Wintertodt world this session
            if (worldChecked) {
                return true;
            }
            
            // Wintertodt worlds (F2P and P2P)
            int[] wintertodtWorlds = {307, 309, 311, 389};
            int currentWorld = Rs2Player.getWorld();
            
            // Check if we're already on a Wintertodt world
            for (int world : wintertodtWorlds) {
                if (currentWorld == world) {
                    Microbot.log("Already on Wintertodt world " + currentWorld + " - continuing");
                    worldChecked = true; // Mark as checked
                    return true;
                }
            }
            
            // Check if it's safe to hop worlds
            if (!isSafeToHopWorlds()) {
                Microbot.log("Not safe to hop worlds currently (combat/interaction) - will try again later");
                return true; // Don't fail, just retry later
            }
            
            // Not on a Wintertodt world, need to hop
            Microbot.log("Not on a Wintertodt world (current: " + currentWorld + ") - hopping to a Wintertodt world...");
            
            boolean hopSuccess = hopToWintertodtWorldSafely(wintertodtWorlds);
            
            if (!hopSuccess) {
                // Hop failed - stop script and logout
                Microbot.log("CRITICAL: Failed to hop to Wintertodt world - stopping script and logging out");
                stopScriptAndLogout();
                return false;
            }
            
            worldChecked = true; // Mark as checked after successful hop
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error checking/hopping to Wintertodt world: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Checks if it's safe to hop worlds (not in combat, not animating, not interacting).
     * 
     * @return true if safe to hop, false otherwise
     */
    private boolean isSafeToHopWorlds() {
        try {
            // Check if player is in combat
            if (Rs2Player.isInCombat()) {
                return false;
            }
            
            // Check if player is interacting with something
            if (Rs2Player.isInteracting()) {
                return false;
            }
            
            // Check if world hopper is already open or hopping in progress
            if (Microbot.isHopping()) {
                return false;
            }

            // Check if bank is open
            if (Rs2Bank.isOpen()) {
                return false;
            }

            // check if we are logged in
            if (!Microbot.isLoggedIn()) {
                return false;
            }

            // Check if break is active
            if (BreakHandlerScript.isBreakActive() || WintertodtBreakManager.isLogoutBreakActive()) {
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error checking if safe to hop: " + e.getMessage());
            return false; // Assume not safe on error
        }
    }
    
    /**
     * Stops the script and logs out the player.
     */
    private void stopScriptAndLogout() {
        try {
            Microbot.log("Stopping script due to critical error...");
            
            // Stop the script
            this.shutdown();
            
            // Wait a moment for shutdown to process
            sleepGaussian(3000, 500);
            
            // Logout the player
            if (Microbot.isLoggedIn()) {
                Microbot.log("Logging out player...");
                Rs2Player.logout();
            }
            
        } catch (Exception e) {
            Microbot.log("Error during script stop and logout: " + e.getMessage());
        }
    }
    
    /**
     * Hops to a random Wintertodt world from the available options.
     * 
     * @param wintertodtWorlds Array of Wintertodt world numbers
     * @return true if successfully hopped, false if failed
     */
    private boolean hopToWintertodtWorldSafely(int[] wintertodtWorlds) {
        try {
            // Select a random Wintertodt world
            int targetWorld = wintertodtWorlds[Rs2Random.between(0, wintertodtWorlds.length - 1)];
            int originalWorld = Rs2Player.getWorld();
            
            Microbot.log("Attempting to hop to Wintertodt world " + targetWorld + "...");
            
            // Perform the world hop (NOTE: Microbot.hopToWorld() has a bug - returns false even on success)
            Microbot.hopToWorld(targetWorld);

            sleep(1000);

            // If the world hop failed, try again (This happens basically every time)
            if (Rs2Player.getWorld() != targetWorld) {
                Microbot.hopToWorld(targetWorld);
            }
            
            // Don't rely on return value - wait and check if world actually changed
            Microbot.log("World hop initiated, waiting for completion...");
            
            // Wait for world hop to complete (up to 15 seconds)
            long hopStartTime = System.currentTimeMillis();
            boolean hopCompleted = sleepUntilTrue(() -> {
                int currentWorld = Rs2Player.getWorld();
                // Success if we reach target world or any other Wintertodt world
                if (currentWorld == targetWorld && Microbot.isLoggedIn()) {
                    return true;
                }
                // Also accept if we ended up on any Wintertodt world
                for (int wintertodtWorld : wintertodtWorlds) {
                    if (currentWorld == wintertodtWorld && Microbot.isLoggedIn()) {
                        return true;
                    }
                }
                return false;
            }, 100, 15000);
            
            int finalWorld = Rs2Player.getWorld();
            
            if (hopCompleted) {
                Microbot.log("Successfully hopped to Wintertodt world " + finalWorld);
                return true;
            } else if (finalWorld != originalWorld) {
                // We hopped somewhere, but not to a Wintertodt world - this is partial success
                Microbot.log("Hopped to world " + finalWorld + " but not a Wintertodt world - trying to hop again");
                
                // Try once more to a Wintertodt world
                int retryWorld = wintertodtWorlds[Rs2Random.between(0, wintertodtWorlds.length - 1)];
                Microbot.log("Attempting second hop to Wintertodt world " + retryWorld + "...");
                Microbot.hopToWorld(retryWorld);
                
                boolean retryCompleted = sleepUntilTrue(() -> {
                    int currentWorld = Rs2Player.getWorld();
                    for (int wintertodtWorld : wintertodtWorlds) {
                        if (currentWorld == wintertodtWorld && Microbot.isLoggedIn()) {
                            return true;
                        }
                    }
                    return false;
                }, 100, 15000);
                
                if (retryCompleted) {
                    Microbot.log("Successfully hopped to Wintertodt world " + Rs2Player.getWorld() + " on second attempt");
                    return true;
                } else {
                    Microbot.log("Second hop attempt failed - still on world " + Rs2Player.getWorld());
                }
            } else {
                Microbot.log("World hop failed - still on original world " + originalWorld + " after 15 seconds");
            }
            
            // Final attempt with a different world if we have multiple options
            if (wintertodtWorlds.length > 1) {
                int finalAttemptWorld;
                do {
                    finalAttemptWorld = wintertodtWorlds[Rs2Random.between(0, wintertodtWorlds.length - 1)];
                } while (finalAttemptWorld == targetWorld && wintertodtWorlds.length > 1);
                
                Microbot.log("Final attempt - trying Wintertodt world " + finalAttemptWorld + "...");
                Microbot.hopToWorld(finalAttemptWorld);
                
                boolean finalCompleted = sleepUntilTrue(() -> {
                    int currentWorld = Rs2Player.getWorld();
                    for (int wintertodtWorld : wintertodtWorlds) {
                        if (currentWorld == wintertodtWorld && Microbot.isLoggedIn()) {
                            return true;
                        }
                    }
                    return false;
                }, 100, 15000);
                
                if (finalCompleted) {
                    Microbot.log("Successfully hopped to Wintertodt world " + Rs2Player.getWorld() + " on final attempt");
                    return true;
                }
            }
            
            Microbot.log("CRITICAL: Failed to hop to any Wintertodt world after multiple attempts");
            return false;
            
        } catch (Exception e) {
            Microbot.log("Error during world hop: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Cleanup method called when script shuts down.
     */
    @Override
    public void shutdown() {
        Microbot.log("Shutting down Enhanced Wintertodt Script");
        
        // Complete state reset to ensure clean shutdown
        resetAllScriptState();
        
        // Reset any active antiban states
        Rs2AntibanSettings.actionCooldownActive = false;

        
        // Unlock any break handler states
        if (BreakHandlerScript.isLockState()) {
            BreakHandlerScript.setLockState(false);
            Microbot.log("Unlocked break handler state during shutdown");
        }
        
        // Reset antiban settings but don't override user preferences
        Rs2Antiban.resetAntibanSettings(false);
        
        // Shutdown break manager
        if (breakManager != null) {
            breakManager.shutdown();
        }
        
        super.shutdown();
        Microbot.log("Script shutdown completed with full state reset");
    }

    /**
     * Gets the remaining time until next Wintertodt round from the game widget.
     * 
     * @return remaining seconds until next round, or -1 if not available
     */
    private int getWintertodtRemainingTime() {
        try {
            String timerText = Rs2Widget.getChildWidgetText(396, 26);
            if (timerText == null || timerText.isEmpty()) {
                return -1;
            }
            
            // Parse text like "The Wintertodt returns in: 0:05" or "The Wintertodt returns in: 1:23"
            String[] parts = timerText.split(":");
            if (parts.length >= 2) {
                String timePart = parts[parts.length - 1].trim(); // Get the last part after ":"
                String minutePart = parts[parts.length - 2].trim(); // Get the minute part
                
                // Extract just the numbers
                String minuteStr = minutePart.replaceAll("\\D+", "");
                String secondStr = timePart.replaceAll("\\D+", "");
                
                if (!minuteStr.isEmpty() && !secondStr.isEmpty()) {
                    int minutes = Integer.parseInt(minuteStr);
                    int seconds = Integer.parseInt(secondStr);
                    return (minutes * 60) + seconds;
                }
            }
            
            return -1;
            
        } catch (Exception e) {
            System.err.println("Error parsing Wintertodt timer: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Updates the round timer tracking based on current game state.
     */
    private void updateRoundTimer() {
        try {
            int remainingSeconds = getWintertodtRemainingTime();
            long currentTime = System.currentTimeMillis();

            // If we have a valid remaining time
            if (remainingSeconds > 0) {
                // Calculate when the next round will start
                nextRoundStartTime = currentTime + (remainingSeconds * 1000);
                
                // If this is the first time we're seeing the timer, record round end time
                if (roundEndTime == -1) {
                    roundEndTime = currentTime;
                    mouseMovedOffscreenForRound = false;
                    hoveredForNextRound = false;
                    isHoverBeforeStartTimeCalculated = false;
                    hoverBeforeStartTime = 0;
                    Microbot.log("New Wintertodt round ended, next round starts in " + remainingSeconds + " seconds");

                    // Setup spam clicking for next round start
                    setupSpamClickingForRoundStart();

                    // Record round duration
                    if (currentRoundStartTime > 0) {
                        long roundDuration = currentTime - currentRoundStartTime;
                        if (roundDuration > 30000 && roundDuration < 600000) { // 30s-10min sanity check
                            previousRoundDurations.add(roundDuration);
                            if (previousRoundDurations.size() > 10) { // Keep last 10 rounds
                                ((java.util.LinkedList<Long>) previousRoundDurations).removeFirst();
                            }
                            Microbot.log("Logged round duration: " + (roundDuration / 1000) + "s. History size: " + previousRoundDurations.size());
                        }
                        currentRoundStartTime = 0;
                    }
                }
                
                lastKnownRemainingSeconds = remainingSeconds;
            } else if (lastKnownRemainingSeconds > 0 && remainingSeconds == -1) {
                // Timer widget disappeared, round likely started
                roundEndTime = -1;
                nextRoundStartTime = -1;
                mouseMovedOffscreenForRound = false;
                hoveredForNextRound = false;
                isHoverBeforeStartTimeCalculated = false;
                hoverBeforeStartTime = 0;
                lastKnownRemainingSeconds = -1;
                currentRoundStartTime = System.currentTimeMillis();
                
                // Prioritize brazier lighting at round start
                shouldPriorizeBrazierAtStart = true;
                
                Microbot.log("Wintertodt round started! Prioritizing brazier lighting.");
            }
            
        } catch (Exception e) {
            System.err.println("Error updating round timer: " + e.getMessage());
        }
    }

    /**
     * Determines what the next interactive object should be for hovering.
     * 
     * @param gameState Current game state
     * @return GameObject to hover over, or null if none appropriate
     */
    private GameObject getNextInteractiveObject(GameState gameState) {
        try {
            // Priority 1: Broken brazier (if we can fix it)
            if (gameState.brokenBrazier != null && config.fixBrazier()) {
                Microbot.log("DEBUG: Will hover over broken brazier for fixing");
                return gameState.brokenBrazier;
            }
            
            // Priority 2: Unlit brazier (if we need to light it)
            if (gameState.brazier != null && gameState.burningBrazier == null && !gameState.needBanking && !gameState.needPotions) {
                Microbot.log("DEBUG: Will hover over unlit brazier for lighting");
                return gameState.brazier;
            }
            
            // Priority 3: Burning brazier (if we have items to burn)
            if (gameState.burningBrazier != null && gameState.hasItemsToBurn) {
                Microbot.log("DEBUG: Will hover over burning brazier for feeding");
                return gameState.burningBrazier;
            }
            
            // Priority 4: Bruma roots on our side (if we intend to chop)
            if (!gameState.inventoryFull && !gameState.hasItemsToBurn && !gameState.needBanking && !gameState.needPotions) {
                GameObject root = getOwnSideRoot();
                if (root != null) {
                    Microbot.log("DEBUG: Will hover over bruma roots for chopping");
                    return root;
                }
            }
            
            // Priority 5: Any available brazier as fallback (prefer burning > unlit > broken)
            if (gameState.burningBrazier != null) {
                Microbot.log("DEBUG: Fallback to burning brazier");
                return gameState.burningBrazier;
            }
            if (gameState.brazier != null) {
                Microbot.log("DEBUG: Fallback to unlit brazier");
                return gameState.brazier;
            }
            if (gameState.brokenBrazier != null) {
                Microbot.log("DEBUG: Fallback to broken brazier");
                return gameState.brokenBrazier;
            }
            
            Microbot.log("DEBUG: No interactive objects found for hovering");
            return null;
            
        } catch (Exception e) {
            System.err.println("Error determining next interactive object: " + e.getMessage());
            return null;
        }
    }

    /**
     * Handles round timer-based mouse behavior during waiting state.
     * 
     * @param gameState Current game state
     */
    private void handleRoundTimerMouseBehavior(GameState gameState) {
        try {
            // Only apply this behavior if we're in waiting state during a round break
            if (state != State.WAITING || nextRoundStartTime == -1) {
                return;
            }
            
            long currentTime = System.currentTimeMillis();
            long timeUntilStart = nextRoundStartTime - currentTime;
            
            // Move mouse offscreen after round ends (do this once per round)
            if (!mouseMovedOffscreenForRound && timeUntilStart > 10000) { // More than 10 seconds left
                sleepGaussian(2000, 1200);
                Rs2Antiban.moveMouseOffScreen(85);
                mouseMovedOffscreenForRound = true;
            }
            
            // Calculate hover time once per round (when to start hovering before round begins)
            if (!isHoverBeforeStartTimeCalculated && timeUntilStart > 1000) {
                isHoverBeforeStartTimeCalculated = true;
                // Random time between 1-8 seconds before round start to begin hovering
                hoverBeforeStartTime = Rs2Random.randomGaussian(1000, 8000);
                if (hoverBeforeStartTime <= 0) {
                    hoverBeforeStartTime = Rs2Random.randomGaussian(2000, 4000);
                }
                hoverBeforeStartTime = Math.max(500, Math.min(8000, hoverBeforeStartTime)); // Clamp to reasonable range
                Microbot.log("Will hover " + (hoverBeforeStartTime / 1000.0) + " seconds before round starts");
            }
            
            // Start hovering when time remaining reaches our calculated hover time (only if not spam clicking)
            if (!hoveredForNextRound && !spamClickingActive && timeUntilStart > 0 && timeUntilStart <= hoverBeforeStartTime) {
                GameObject nextObject = getNextInteractiveObject(gameState);
                if (nextObject != null) {
                    Rs2GameObject.hoverOverObject(nextObject);
                    hoveredForNextRound = true;
                    Microbot.log("Hovering over next interactive object: " + nextObject.getId() + 
                               " (" + (timeUntilStart / 1000.0) + "s before round start)");
                } else {
                    Microbot.log("Could not find interactive object to hover over");
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error handling round timer mouse behavior: " + e.getMessage());
        }
    }

    /* ---------------------------------------------------------
     *  estimate seconds until kill from live HP / DPS
     * --------------------------------------------------------- */
    private void updateWintertodtHpTracking(int currentHp)
    {
        long now = System.currentTimeMillis();

        if (prevWintertodtHp != -1 && currentHp < prevWintertodtHp)
        {
            int  diffHp = prevWintertodtHp - currentHp;
            long diffMs = now - prevHpTimestamp;
            if (diffMs > 400)                                      // ignore <½-tick noise
            {
                double newHpPerSec = diffHp / (diffMs / 1000d);
                // simple EMA to smooth the DPS figure
                hpPerSecond = hpPerSecond == 0 ? newHpPerSec
                                               : (hpPerSecond * 0.7) + (newHpPerSec * 0.3);
            }
        }

        prevWintertodtHp = currentHp;
        prevHpTimestamp  = now;

        if (hpPerSecond > 0.01) {
            double dpsBasedEstimate = currentHp / hpPerSecond;

            if (!previousRoundDurations.isEmpty()) {
                double avgDurationMs = previousRoundDurations.stream().mapToLong(Long::longValue).average().orElse(0);
                if (avgDurationMs > 0) {
                    // Scale historical average by current HP percentage.
                    double historicalScaledEstimate = (avgDurationMs / 1000.0) * (currentHp / 100.0);
                    historicalEstimateSecondsLeft = historicalScaledEstimate;

                    // Blend the two estimates. Give more weight to historical as it's more stable.
                    estimatedSecondsLeft = (dpsBasedEstimate * 0.2) + (historicalScaledEstimate * 0.8);
                } else {
                    estimatedSecondsLeft = dpsBasedEstimate;
                    historicalEstimateSecondsLeft = 0;
                }
            } else {
                estimatedSecondsLeft = dpsBasedEstimate;
                historicalEstimateSecondsLeft = 0;
            }
        } else {
            estimatedSecondsLeft = 999;
            historicalEstimateSecondsLeft = 0;
        }
    }

    /* ---- update average action durations live --------------------------- */
    private static void noteActionDuration(String type, long duration) {
        double dur = duration;
        switch (type) {
            case "CHOP":
                avgChopMs = (avgChopMs * 0.8) + (dur * 0.2);
                break;
            case "FLETCH":
                avgFletchMs = (avgFletchMs * 0.8) + (dur * 0.2);
                break;
            case "FEED":
                avgFeedMs = (avgFeedMs * 0.8) + (dur * 0.2);
                break;
        }

        /* ---- recompute full-cycle estimate for overlay ---- */
        cycleTimeSec = (avgChopMs
                       + (config != null && config.fletchRoots() ? avgFletchMs : 0)
                       + avgFeedMs) / 1000.0;
    }

    private void noteActionDuration(String type, long startMs, long endMs)
    {
        noteActionDuration(type, endMs - startMs);
    }

    /* --------------------------------------------------------------------- */

    /**
     * Chooses the proper starting state the very first time the script
     * enters the main loop.
     */
    private void determineInitialState(GameState gs)
    {
        /* If startup was skipped (we're already in game room and ready), go straight to game logic */
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation.getY() > 3967 && // Inside game room 
            playerLocation.distanceTo(config.brazierLocation().getBRAZIER_LOCATION()) <= 15) {
            
            Microbot.log("Starting in game room - skipping banking phase and going directly to game logic");
            
            // If using rejuvenation potions and we don't have enough, we need to get them first
            if (usesPotions) {
                int currentPotions = getTotalRejuvenationPotions();
                if (currentPotions < config.minHealingItems()) {
                    Microbot.log("Using rejuvenation potions but don't have enough - need to make them");
                    changeState(State.GET_CONCOCTIONS);
                    return;
                }
            }
            
            /* Go directly to appropriate game state */
            if (!gs.isWintertodtAlive) {
                changeState(State.WAITING);
                return;
            }
            
            if (gs.burningBrazier == null) {
                changeState(State.WAITING); // WAITING handler will light
                return;
            }
            
            if (gs.hasItemsToBurn) {
                changeState(State.BURN_LOGS);
                return;
            }
            
            if (!gs.inventoryFull) {
                changeState(State.CHOP_ROOTS);
                return;
            }
            
            changeState(State.WAITING);
            return;
        }
        
        /* 1. Bank first if food / tools are missing (but not for rejuvenation potions) */
        if (gs.needBanking)
        {
            changeState(State.BANKING);
            return;
        }

        /* Handle rejuvenation potions - if we need potions and we're using rejuv potions */
        if (usesPotions) {
            int currentPotions = getTotalRejuvenationPotions();
            if (currentPotions < config.minHealingItems()) {
                Microbot.log("Need rejuvenation potions - starting potion creation workflow");
                changeState(State.GET_CONCOCTIONS);
                return;
            }
        }

        /* 2. Make sure we are on the correct side of the arena */
        if (Rs2Player.getWorldLocation()
                     .distanceTo(config.brazierLocation().getBRAZIER_LOCATION()) > 12)
        {
            changeState(State.ENTER_ROOM);               // walk inside
            return;
        }

        /* 3. Round not active yet → wait / hover */
        if (!gs.isWintertodtAlive)
        {
            changeState(State.WAITING);
            return;
        }

        /* 4. Round active – decide first action */
        if (gs.burningBrazier == null)                // need to light or relight
        {
            changeState(State.WAITING);               // WAITING handler will light
            return;
        }

        if (gs.hasItemsToBurn)
        {
            changeState(State.BURN_LOGS);
            return;
        }

        if (!gs.inventoryFull)
        {
            changeState(State.CHOP_ROOTS);
            return;
        }

        /* fallback */
        changeState(State.WAITING);
    }

    /* ------------ tiny mouse nudge after some clicks ------------------- */
    private void maybeNudgeMouse()
    {
        if (!config.humanizedTiming()) return;

        /*  1. decide whether we nudge at all
              probability = 18 % + |N(0,1)|·10   (≈ 5 % – 40 %)           */
        int moveChance = (int) Math.round(18 + Math.abs(random.nextGaussian()) * 10);
        moveChance = Math.max(5, Math.min(40, moveChance));

        if (random.nextInt(100) >= moveChance)
        {
            return;                                 // no micro-movement this time
        }

        try
        {
            Point start = Microbot.getMouse().getMousePosition();
            if (start == null) return;

            /* 2. first micro-movement  (σ ≈ 20 px, capped ±80) */
            int dx = (int) (random.nextGaussian() * 20);
            int dy = (int) (random.nextGaussian() * 20);
            dx = Math.max(-80, Math.min(80, dx));
            dy = Math.max(-80, Math.min(80, dy));

            Microbot.getMouse().move(start.x + dx, start.y + dy);

            /* 3. ~50 % chance of a quick follow-up wobble             */
            if (random.nextBoolean())
            {
                sleepGaussian(30, 15);              // brief pause

                int dx2 = (int) (random.nextGaussian() * 10);
                int dy2 = (int) (random.nextGaussian() * 10);
                dx2 = Math.max(-30, Math.min(30, dx2));
                dy2 = Math.max(-30, Math.min(30, dy2));

                Microbot.getMouse().move(start.x + dx + dx2,
                                          start.y + dy + dy2);
            }
        }
        catch (Exception ignored) {}
    }

    /* ------------ knife selection helpers ------------------------------ */
    private boolean isKnifeSelected()
    {
        return Rs2Inventory.isItemSelected()
               && Rs2Inventory.getSelectedItemId() == WintertodtInventoryManager.knifeToUse;
    }

    public static void deselectSelectedItem()
    {
        // Clicking the selected item to unselect it
        if (Rs2Inventory.isItemSelected())
        {
            Rs2Inventory.interact(Rs2Inventory.getSelectedItemId(), "Use");
            sleepGaussian(80, 40);
        }
    }
    // -------------------------------------------------------------------------

    /* remembers the root we last hovered while knife was selected */
    private Rs2ItemModel lastHoveredRoot = null;

    /** randomized threshold for knife pre-selection (recalculated once per full inventory cycle) */
    private static int knifePreselectThreshold = 6;
    
    /** tracks the last inventory count to detect when we start a new full inventory cycle */
    private static int lastInventoryCount = 0;

    /* ═══════════════════  LEAVING THE ARENA  ═════════════════════ */

    /** Rough check: Y-coordinate north of the doors means we are still inside */
    private boolean isInsideWintertodtArea()
    {
        return WintertodtLocationManager.isInsideGameRoom();
    }



    /* ═════════════════════════════════════════════════════════════════════ */

    /** remembers whether the player was inside the arena in the last tick */
    private boolean previouslyInsideArena = true;

    /** tracks if we're waiting for round to end naturally to collect rewards */
    private static boolean waitingForRoundEnd = false;

    /* ═══════════════════  ACTION / PLAN RESET  ═════════════════════ */
    /** Clears all per-round goals, counters, locks & cooldown stamps */
    public static void resetActionPlanning()
    {
        Microbot.log("-- Resetting action plan & cooldowns");

        targetRootsForThisRun = 0;
        rootsToChopGoal       = 0;
        fletchGoal            = 0;
        feedGoal              = 0;

        rootsChoppedThisRun   = 0;
        fletchedThisRun       = 0;
        fedThisRun            = 0;
        currentBurnableCount  = 0;

        lastWoodcuttingXpDropTime = 0;
        lastFletchingXpDropTime   = 0;
        lastFiremakingXpDropTime  = 0;

        // Reset inventory cycle tracking
        lastInventoryCount = 0;
        knifePreselectThreshold = 6; // Reset to default

        // Reset waiting for round end flag
        waitingForRoundEnd = false;
        
        // Reset brazier priority flag
        shouldPriorizeBrazierAtStart = false;

        resetActions = true;             // force re-evaluation on next loop
    }
    /* ═══════════════════════════════════════════════════════════════ */

    /**
     * Gets the current Wintertodt points from the game interface.
     * 
     * @return current points, or 0 if not available
     */
    private int getWintertodtPoints() {
        try {
            String pointsText = Rs2Widget.getChildWidgetText(396, 6);
            if (pointsText == null || pointsText.isEmpty()) {
                return 0;
            }
            
            // Parse text like "Points<br>25" or just "25"
            String[] parts = pointsText.split("<br>");
            String numberPart = parts.length > 1 ? parts[1] : parts[0];
            
            // Extract just the numbers
            String pointsStr = numberPart.replaceAll("\\D+", "");
            
            if (!pointsStr.isEmpty()) {
                return Integer.parseInt(pointsStr);
            }
            
            return 0;
            
        } catch (Exception e) {
            System.err.println("Error getting Wintertodt points: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Gets the startup manager instance.
     * @return startup manager
     */
    public WintertodtStartupManager getStartupManager() {
        return startupManager;
    }
    
    /**
     * Checks if the startup sequence has been completed.
     * @return true if startup completed
     */
    public boolean isStartupCompleted() {
        return startupCompleted;
    }
    
    /**
     * Get the break manager instance
     * @return break manager
     */
    public WintertodtBreakManager getBreakManager() {
        return breakManager;
    }

    /**
     * Public wrapper so the plugin can forward chat messages
     * to the new feeding interrupt handler without accessing it directly.
     */
    public static void onChatMessage(String message)
    {
        handleChatInterruption(message);
    }

    // Add this as a class field near the top with other fields
    private static long lastFletchingAnimationTime = 0;
    private static final long FLETCHING_ANIMATION_TIMEOUT = 3000; // 3 seconds
    
    // Feeding animation tracking
    private static long lastFeedingAnimationTime = 0;
    private static final long FEEDING_ANIMATION_TIMEOUT = 3000; // 3 seconds

    /**
     * Updates fletching animation tracking to detect when fletching stops unexpectedly
     */
    private void updateFletchingAnimationTracking() {
        // Only track animation when we're actively fletching
        if (!fletchingState.isActive()) {
            return;
        }
        
        // Check if player is currently animating
        if (Rs2Player.isAnimating()) {
            lastFletchingAnimationTime = System.currentTimeMillis();
        }
        
        // If we just started fletching, initialize the timer
        if (lastFletchingAnimationTime == 0) {
            lastFletchingAnimationTime = System.currentTimeMillis();
        }
    }

    /**
     * Updates feeding animation tracking to detect when feeding stops unexpectedly
     */
    private void updateFeedingAnimationTracking() {
        // Only track animation when we're actively feeding
        if (!feedingState.isActive()) {
            return;
        }
        
        // Check if player is currently animating
        if (Rs2Player.isAnimating()) {
            lastFeedingAnimationTime = System.currentTimeMillis();
        }
        
        // If we just started feeding, initialize the timer
        if (lastFeedingAnimationTime == 0) {
            lastFeedingAnimationTime = System.currentTimeMillis();
        }
    }

    /**
     * Sets up spam clicking behavior for the upcoming round start.
     * This creates natural-looking clicking behavior before and after the game starts.
     */
    private void setupSpamClickingForRoundStart() {
        try {
            if (nextRoundStartTime <= 0) {
                return;
            }
            
            // Calculate when to start spam clicking (2-6 seconds before round start)
            int preStartDuration = 1000 + random.nextInt(3000); // 1-4 seconds
            spamClickStartTime = nextRoundStartTime - preStartDuration;
            
            // Calculate when to stop spam clicking (1-3 seconds after round start)
            int postStartDuration = 500 + random.nextInt(1500); // 0.5-2 seconds
            spamClickEndTime = nextRoundStartTime + postStartDuration;
            
            // Reset spam click tracking
            spamClickTarget = null;
            spamClicksPerformed = 0;
            lastSpamClick = 0;
            spamClickingActive = false;
            
            Microbot.log("Spam clicking scheduled: " + (preStartDuration / 1000.0) + "s before start, " + 
                        (postStartDuration / 1000.0) + "s after start");
            
        } catch (Exception e) {
            System.err.println("Error setting up spam clicking: " + e.getMessage());
        }
    }

    /**
     * Executes spam clicking behavior during the appropriate time window.
     */
    private void executeSpamClicking(GameState gameState) {
        try {
            long currentTime = System.currentTimeMillis();
            
            // Check if we should be spam clicking
            if (currentTime < spamClickStartTime || currentTime > spamClickEndTime) {
                if (spamClickingActive) {
                    spamClickingActive = false;
                    Microbot.log("Spam clicking finished. Performed " + spamClicksPerformed + " clicks.");
                }
                return;
            }
            
            // Activate spam clicking if not already active
            if (!spamClickingActive) {
                spamClickingActive = true;
                spamClickTarget = determineSpamClickTarget(gameState);
                Microbot.log("Started spam clicking on: " + 
                           (spamClickTarget != null ? spamClickTarget.getId() : "no target"));
            }
            
            // Perform spam clicks with natural timing
            if (spamClickTarget != null && currentTime - lastSpamClick > getNextSpamClickDelay()) {
                performSpamClick();
                lastSpamClick = currentTime;
                spamClicksPerformed++;
            }
            
        } catch (Exception e) {
            System.err.println("Error executing spam clicking: " + e.getMessage());
        }
    }

    /**
     * Determines the best target for spam clicking (usually the brazier we'll interact with).
     */
    private GameObject determineSpamClickTarget(GameState gameState) {
        // Priority 1: Unlit brazier (what we'll light when round starts)
        if (gameState.brazier != null && gameState.burningBrazier == null) {
            return gameState.brazier;
        }
        
        // Priority 2: Broken brazier (if we can fix it)
        if (gameState.brokenBrazier != null && config.fixBrazier()) {
            return gameState.brokenBrazier;
        }
        
        // Priority 3: Any brazier as fallback
        if (gameState.burningBrazier != null) {
            return gameState.burningBrazier;
        }
        
        return gameState.brazier;
    }

    /**
     * Performs a single spam click with natural variation.
     */
    private void performSpamClick() {
        try {
            if (spamClickTarget == null) {
                return;
            }
            
            // Just hover and click without actually interacting
            Rs2GameObject.hoverOverObject(spamClickTarget);
            
            // Small delay between hover and click for realism
            sleepGaussian(60, 40);

            Microbot.log("Spam clicking on: " + spamClickTarget.getId());
            
            // Perform a light click (we don't want to actually interact yet)
            Microbot.getMouse().click();
            
        } catch (Exception e) {
            // Ignore errors during spam clicking to avoid disrupting main logic
        }
    }

    /**
     * Gets the delay until the next spam click with natural variation.
     */
    private int getNextSpamClickDelay() {
        // Base delay: 100-300ms with Gaussian distribution
        int baseDelay = 150;
        int variation = 75;
        int delay = (int) (baseDelay + (random.nextGaussian() * variation));
        
        // Clamp to reasonable range
        return Math.max(50, Math.min(500, delay));
    }

    /**
     * Deposits any non-essential items from the inventory.
     * This is useful for cleaning up after gear swaps.
     */
    private void depositUnnecessaryItems() {
        if (!Rs2Bank.isOpen()) {
            return;
        }

        // Determine the list of essential items to keep
        WintertodtAxeManager.AxeDecision axeDecision = WintertodtAxeManager.determineOptimalAxeSetup();
        List<String> keepItems = new ArrayList<>(Arrays.asList("hammer", "tinderbox"));

        if (WintertodtInventoryManager.knifeToUse == ItemID.FLETCHING_KNIFE) {
            keepItems.add("fletching knife");
        }

        if (WintertodtInventoryManager.knifeToUse == ItemID.KNIFE) {
            keepItems.add("knife");
        }

        if (usesPotions) {
            keepItems.add("Rejuvenation potion");
        } else {
            keepItems.add(config.food().getName());
        }

        if (axeDecision.shouldKeepInInventory()) {
            keepItems.add(axeDecision.getAxeName());
        }

        // Deposit everything except the essential items
        Rs2Bank.depositAllExcept(keepItems.toArray(new String[0]));
        sleepGaussian(300, 100);
        Microbot.log("Deposited unnecessary items from inventory.");
    }

    private void handleWalkingToSafeSpotForBreakState(GameState gameState) {
        WorldPoint safeSpot = WintertodtBreakManager.BOSS_ROOM;
        int radius = WintertodtBreakManager.SAFE_SPOT_RADIUS;
        if (Rs2Player.getWorldLocation().distanceTo(safeSpot) > radius) {
            Rs2Walker.walkTo(safeSpot, radius);
            Rs2Player.waitForWalking();
        } else {
            // We have arrived. The break manager will detect this and start the break.
            // We can transition to WAITING state.
            changeState(State.WAITING);
        }
    }

    // ────────────── REWARD CART STATE HANDLERS ──────────────────────────────────

    /**
     * Handles exiting Wintertodt for reward collection
     */
    private void handleExitingForRewardsState(GameState gameState) {
        try {
            if (!WintertodtLocationManager.attemptLeaveWintertodt()) {
                return; // Still inside, try again next tick
            }
            
            Microbot.log("Successfully exited Wintertodt for reward collection");
            changeState(State.WALKING_TO_REWARDS_BANK);
            
        } catch (Exception e) {
            System.err.println("Error exiting for rewards: " + e.getMessage());
            finishRewardCartLooting(); // Fallback to normal execution
        }
    }

    /**
     * Handles walking to bank for reward collection preparation
     */
    private void handleWalkingToRewardsBankState(GameState gameState) {
        try {
            WorldPoint playerLocation = Rs2Player.getWorldLocation();
            
            if (playerLocation.distanceTo(BANK_LOCATION) > 6) {
                Rs2Walker.walkTo(BANK_LOCATION);
                Rs2Player.waitForWalking();
            } else if (playerLocation.distanceTo(BANK_LOCATION) <= 6 && playerLocation.distanceTo(BANK_LOCATION) > 1) {
                Rs2Walker.walkFastCanvas(BANK_LOCATION);
                Rs2Player.waitForWalking();
            } else {
                Microbot.log("Arrived at bank for reward collection");
                changeState(State.BANKING_FOR_REWARDS);
            }
            
        } catch (Exception e) {
            System.err.println("Error walking to rewards bank: " + e.getMessage());
            finishRewardCartLooting(); // Fallback
        }
    }

    /**
     * Handles banking all items before reward collection
     */
    private void handleBankingForRewardsState(GameState gameState) {
        try {
            if (!Rs2Bank.isOpen()) {
                Rs2Bank.openBank();
                sleepGaussian(600, 100);
                return;
            }

            // Bank all items for clean inventory
            if (!Rs2Inventory.isEmpty()) {
                Rs2Bank.depositAll();
                sleepGaussian(600, 100);
                Microbot.log("Deposited all items for reward collection");
                return;
            }

            // Close bank and proceed to reward cart
            Rs2Bank.closeBank();
            sleepGaussian(300, 50);
            if (rewardCartExhausted || currentRewardCartRewards <= 0) {
                // No more rewards to claim – finish up
                changeState(State.RETURNING_FROM_REWARDS);
            } else {
                // Still owed rewards, go back for another inventory
                changeState(State.WALKING_TO_REWARD_CART);
            }
            
        } catch (Exception e) {
            System.err.println("Error banking for rewards: " + e.getMessage());
            finishRewardCartLooting(); // Fallback
        }
    }

    /**
     * Handles walking to reward cart location
     */
    private void handleWalkingToRewardCartState(GameState gameState) {
        try {
            WorldPoint targetTile = getRewardCartTile();
            WorldPoint playerLocation = Rs2Player.getWorldLocation();
            
            if (playerLocation.distanceTo(targetTile) > 1) {
                Rs2Walker.walkFastCanvas(targetTile);
                Rs2Player.waitForWalking();
            } else {
                Microbot.log("Arrived at reward cart location");
                changeState(State.LOOTING_REWARD_CART);
            }
            
        } catch (Exception e) {
            System.err.println("Error walking to reward cart: " + e.getMessage());
            finishRewardCartLooting(); // Fallback
        }
    }

    /**
     * Handles the actual reward cart looting process
     */
    private void handleLootingRewardCartState(GameState gameState) {
        try {
            // Check if looting is finished based on widget text
            if (isRewardCartLootingFinished()) {
                rewardCartExhausted = true;
                currentRewardCartRewards = 0; // ensure counter reset immediately
                Microbot.log("Reward cart looting finished - no more rewards available");
                changeState(State.RETURNING_FROM_REWARDS);
                return;
            }

            // If inventory is full, go bank and return
            if (Rs2Inventory.isFull()) {
                Microbot.log("Inventory full - banking rewards and returning");
                changeState(State.WALKING_TO_REWARDS_BANK);
                return;
            }

            // If we haven't interacted recently or interaction is complete, interact again
            if (lastRewardCartInteraction == 0 || isRewardCartInteractionComplete()) {
                if (interactWithRewardCart()) {
                    sleepGaussian(1000, 800); // Wait a bit after interaction
                    Rs2Antiban.moveMouseOffScreen(60);
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error looting reward cart: " + e.getMessage());
            finishRewardCartLooting(); // Fallback
        }
    }

    /**
     * Handles returning from reward collection
     */
    private void handleReturningFromRewardsState(GameState gameState) {
        try {
            // If we still have items, go bank them first
            if (!Rs2Inventory.isEmpty()) {
                changeState(State.WALKING_TO_REWARDS_BANK);
                return;
            }

            // All rewards collected and banked, finish the sequence
            finishRewardCartLooting();
            
        } catch (Exception e) {
            System.err.println("Error returning from rewards: " + e.getMessage());
            finishRewardCartLooting(); // Fallback
        }
    }

    /**
     * Checks if the script should pause due to break system.
     *
     * @return true if script should pause
     */
    private boolean shouldPauseForBreaks() {
        // Pause if new custom break system is active
        if (breakManager != null && WintertodtBreakManager.isBreakActive()) {
            return true;
        }
        
        // Pause if break handler is active
        if (BreakHandlerScript.isBreakActive()) {
            return true;
        }

        // Pause if action cooldown is active
        if (Rs2AntibanSettings.actionCooldownActive) {
            return true;
        }

        // Pause if universal antiban has paused all scripts
        if (Microbot.pauseAllScripts.get()) {
            Microbot.log("Script is paused.");
            return true;
        }

        return false;
    }

    // ────────────── REWARD CART LOOTING METHODS ──────────────────────────────────

    /**
     * Checks if a state is a reward cart looting state
     */
    private static boolean isRewardCartState(State state) {
        return state == State.EXITING_FOR_REWARDS ||
               state == State.WALKING_TO_REWARDS_BANK ||
               state == State.BANKING_FOR_REWARDS ||
               state == State.WALKING_TO_REWARD_CART ||
               state == State.LOOTING_REWARD_CART ||
               state == State.RETURNING_FROM_REWARDS;
    }

    /**
     * Checks if we should trigger reward cart looting based on rewards and configuration
     */
    private boolean shouldLootRewardCart() {
        if (!config.enableRewardCartLooting() || isLootingRewards) {
            return false;
        }

        // Only check for rewards during safe states
        if (!isSafeStateForRewardLooting()) {
            return false;
        }

        // Calculate threshold with gaussian random if not set
        if (targetRewardThreshold == 0) {
            calculateRewardThreshold();
        }

        return currentRewardCartRewards >= targetRewardThreshold;
    }

    /**
     * Calculates the reward threshold with gaussian random variance
     */
    private static void calculateRewardThreshold() {
        if (config == null) {
            targetRewardThreshold = 3; // Default fallback
            return;
        }
        
        int baseRewards = config.minimumRewardsForCollection();
        int variance = config.rewardsVariance();
        
        // Generate gaussian random number (-1 to 1) and scale by variance
        Random staticRandom = new Random();
        double gaussianRandom = staticRandom.nextGaussian();
        int randomVariance = (int) (gaussianRandom * variance);
        
        targetRewardThreshold = Math.max(1, baseRewards + randomVariance); // Minimum 1 reward
        
        Microbot.log("Reward cart threshold set to: " + targetRewardThreshold + " rewards (base: " + baseRewards + ", variance: " + randomVariance + ")");
    }

    /**
     * Checks if current state is safe for starting reward cart looting
     */
    private boolean isSafeStateForRewardLooting() {
        return state == State.BANKING || 
               state == State.WAITING || 
               state == State.ENTER_ROOM ||
               state == State.WALKING_TO_SAFE_SPOT_FOR_BREAK;
    }

    /**
     * Initiates the reward cart looting sequence
     */
    private void startRewardCartLooting() {
        isLootingRewards = true;
        rewardCartExhausted = false;
        wasInWintertodtBeforeRewards = isInsideWintertodtArea();
        
        Microbot.log("Starting reward cart looting sequence - current rewards: " + currentRewardCartRewards + "/" + targetRewardThreshold);
        
        if (wasInWintertodtBeforeRewards) {
            changeState(State.EXITING_FOR_REWARDS);
        } else {
            changeState(State.WALKING_TO_REWARDS_BANK);
        }
    }

    /**
     * Completes the reward cart looting and returns to normal execution
     */
    private void finishRewardCartLooting() {
        isLootingRewards = false;
        rewardCartExhausted = false;
        targetRewardThreshold = 0; // Reset for next time
        
        Microbot.log("Reward cart looting completed - returning to normal execution");

        currentRewardCartRewards = 0;
        
        // Force complete startup sequence after reward collection
        // This ensures proper gear setup, inventory setup, and positioning
        startupCompleted = false;
        isInitialized = false;
        
        // Reset the startup manager for a fresh startup sequence
        if (startupManager != null) {
            startupManager.reset();
        }
        
        Microbot.log("Forcing complete startup sequence after reward collection to ensure proper setup");
        
        // Let the main loop handle startup sequence - don't change state here
        // The startup sequence will handle everything: location, gear, inventory, positioning
    }

    /**
     * Checks if reward cart looting is finished by reading widget text
     */
    private boolean isRewardCartLootingFinished() {
        String widgetText = Rs2Widget.getChildWidgetText(REWARD_CART_WIDGET_PARENT, REWARD_CART_WIDGET_CHILD);
        
        if (widgetText != null) {
            return widgetText.contains(REWARD_CART_NO_REWARDS_TEXT) || 
                   widgetText.contains(REWARD_CART_FINISHED_TEXT);
        }
        
        return false;
    }

    /**
     * Gets a suitable tile near the reward cart for interaction
     */
    private WorldPoint getRewardCartTile() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        
        // Choose the closest available tile
        WorldPoint[] rewardCartTiles = {REWARD_CART_CENTER, REWARD_CART_WEST, REWARD_CART_EAST};
        
        WorldPoint closestTile = REWARD_CART_CENTER;
        int shortestDistance = Integer.MAX_VALUE;
        
        for (WorldPoint tile : rewardCartTiles) {
            int distance = playerLocation.distanceTo(tile);
            if (distance < shortestDistance) {
                shortestDistance = distance;
                closestTile = tile;
            }
        }
        
        return closestTile;
    }

    /**
     * Handles the reward cart interaction and looting
     */
    private boolean interactWithRewardCart() {

        lastRewardCartInteraction = System.currentTimeMillis();

        // Try to interact with reward cart by searching for the "Reward" text on the object
        if (Rs2GameObject.interact("Reward", "Big-search", false)) {
            Microbot.status = "Interacting with reward cart";
            return true;
        }

        Microbot.status = "No reward cart found to interact with";
        return false;
    }

    /**
     * Checks if player has stopped animating for long enough or inventory is full
     */
    private boolean isRewardCartInteractionComplete() {
        long timeSinceInteraction = System.currentTimeMillis() - lastRewardCartInteraction;
        
        // If inventory is full, interaction is complete
        if (Rs2Inventory.isFull()) {
            return true;
        }
        
        // If player hasn't been animating for 1+ seconds, interaction is complete
        if (!Rs2Player.isAnimating(1000) && timeSinceInteraction > 2000) {
            return true;
        }

        // Timeout after 30 seconds
        if (timeSinceInteraction > 30000) {
            Microbot.log("Reward cart interaction timeout");
            return true;
        }
        
        return false;
    }

}