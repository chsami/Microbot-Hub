package net.runelite.client.plugins.microbot.droslayer;


import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro;
import net.runelite.client.plugins.microbot.droslayer.profile.DroSlayerProfileManager;
import net.runelite.client.plugins.microbot.droslayer.profile.DroSlayerTaskProfileJson;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.client.plugins.microbot.droslayer.DroInventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.droslayer.DroCannon;
import net.runelite.client.plugins.microbot.droslayer.support.SceneObjects;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2LootEngine;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.npc.MonsterLocation;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.plugins.slayer.Task;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.runelite.api.gameval.VarbitID.*;

@Slf4j
public class DroSlayerScript extends Script {
    private String terminalStopMessage;

    private DroSlayerConfig config;
    private WorldPoint taskDestination = null;
    private DroSlayerGodWarsAccess.Step godWarsAccessStep;
    private long godWarsAccessClickAt;
    private int godWarsAccessAttempts;
    private long godWarsAccessDiagnosticAt;
    private long deepWildernessStairClickAt;
    private int deepWildernessStairAttempts;
    private String taskLocationName = "";
    private boolean initialSetupDone = false;
    private int taskLocationResolutionFailures = 0;
    private String unresolvedTaskName = null;
    private int bankLoadFailures = 0;
    private long bankSupplyRetryAt;
    private final DroSlayerCannonPurchase cannonPurchase = new DroSlayerCannonPurchase();
    private String bankSupplyWaitReason;
    private long lastBankLoadFailureAt = 0L;
    private long lastLootTime = 0;
    private final DroSlayerCluePickup cluePickup = new DroSlayerCluePickup();
    private long templeRestoreDeadline;
    private long templeRestoreLastClick;
    private long templeRestoreCooldown;
    private static final int ARRIVAL_DISTANCE = 10;
    private static final int LOOT_DELAY_MS = 600;
    private static final int SLAYER_REWARD_POINTS_VARBIT = 4068;
    private static final int SKIP_TASK_COST = 30;
    private static final int MAX_SKIP_ATTEMPTS = 10;
    private int skipAttemptCounter = 0;
    private String taskBeingSkipped = "";

    // Block task state tracking
    private static final int BLOCK_TASK_COST = 100;
    private static final int MAX_BLOCK_ATTEMPTS = 10;
    private int blockAttemptCounter = 0;
    private String taskBeingBlocked = "";
    private final DroSlayerRewards rewardsInterface = new DroSlayerRewards();
    private long rewardsAttemptAt;

    // Duradel travel anchors. Known task interiors are exited locally first; Efficient Walker
    // is only ever given an overworld route. It then owns the long route to the confirmed
    // ground-floor house tile, after which Dro Slayer owns the ladder/upstairs movement.
    private static final WorldPoint DURADEL_HOUSE_APPROACH = new WorldPoint(2870, 2968, 0);
    private static final WorldPoint DURADEL_LONG_RANGE_HANDOFF = DURADEL_HOUSE_APPROACH;
    private static final WorldPoint DURADEL_LADDER_TILE = new WorldPoint(2871, 2971, 0);
    private static final WorldPoint DURADEL_UPSTAIRS_APPROACH = new WorldPoint(2869, 2981, 1);

    // Post-task escape policy: never ask Efficient Walker to solve a route from deep dungeon
    // coordinates when an inventory teleport can put us beside a bank first.  This is intentionally
    // independent from Rs2Walker/minigame teleports: these are direct inventory-item interactions only.
    private static final int DUNGEON_INTERIOR_Y = 6400;
    private static final WorldPoint FEROX_LANDING = new WorldPoint(3151, 3635, 0);
    private static final WorldPoint FEROX_POOL = new WorldPoint(3128, 3637, 0);
    private DroSlayerState stateAfterFerox = DroSlayerState.BANKING;
    private boolean baseBankLoadComplete;
    private volatile boolean tentacleDegraded;
    private boolean tentacleBankCheck;
    private boolean escapeAmmoRestock;
    private boolean escapeAmmoClosing;
    private final DroSlayerTentacleRepair tentacleRepair = new DroSlayerTentacleRepair();

    public void onTentacleDegraded() {
        tentacleDegraded = true;
    }

    private boolean feroxRestored;
    private long braceletBankReportAt = Long.MIN_VALUE;
    private long lastFeroxPoolClickAt;
    private static final WorldPoint EDGEVILLE_LANDING = new WorldPoint(3087, 3496, 0);
    private static final WorldPoint GRAND_EXCHANGE_LANDING = new WorldPoint(3164, 3478, 0);
    // Emergency escape policy: leave immediately when food is exhausted, or use this
    // lower hard-safety HP floor even when food remains.  A carried bank-adjacent
    // teleport is required in either case.
    private static final int EMERGENCY_ESCAPE_HP_PERCENT = 20;
    private static final long EMERGENCY_ESCAPE_RETRY_MS = 2500L;
    private boolean postTaskEscapeAttempted = false;
    private boolean wildernessMasterBeforeRestore;
    private long lastEmergencyEscapeAttemptAt = 0L;

    // POH Constants
    private static final int HOUSE_PORTAL_ID = 4525;
    private static final int[] REJUVENATION_POOL_IDS = {29241, 29240, 29239, 29238, 29237};
    // Portal nexus IDs (Basic, Marble, Gilded)
    private static final int[] PORTAL_NEXUS_IDS = {33366, 33408, 33410};
    // Mounted glory IDs (various tiers)
    private static final int[] MOUNTED_GLORY_IDS = {13523, 13524, 13525, 13526};
    // Mounted ring of wealth
    private static final int MOUNTED_WEALTH_ID = 29156;

    // POH state tracking
    private DroSlayerState stateAfterPoh = DroSlayerState.BANKING; // What to do after POH restoration
    private boolean pohTeleportAttempted = false;

    // Spellbook swap retry tracking
    private int spellbookSwapAttempts = 0;
    private static final int MAX_SPELLBOOK_SWAP_ATTEMPTS = 5;

    // Break handler resume tracking
    private boolean wasLoggedOut = false;

    // Cannon state tracking
    private boolean isUsingCannon = false;
    private final DroSlayerCannonPolicy cannonPolicy = new DroSlayerCannonPolicy();
    private final java.util.concurrent.atomic.AtomicInteger sessionEscapes = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger sessionDeaths = new java.util.concurrent.atomic.AtomicInteger();

    public String getEscapesDeathsDisplay() {
        return sessionEscapes.get() + "/" + sessionDeaths.get();
    }

    public String getCannonWorldDisplay() {
        int world = cannonDeploymentWorld;
        if (world > 0) return String.valueOf(world);
        if (cannonReclaim.lastWorld() > 0) return cannonReclaim.lastWorld() + " (unconfirmed)";
        return cannonBuildStage() > 0 ? "Unknown" : "—";
    }

    public String getCannonTileDisplay() {
        WorldPoint tile = lastCannonPlacementTile != null ? lastCannonPlacementTile : cannonReclaim.lastTile();
        return tile == null ? "—" : tile.getX() + ", " + tile.getY() + ", " + tile.getPlane();
    }

    public void recordSessionDeath() {
        sessionDeaths.incrementAndGet();
    }
    private String cannonWithdrawalItem;
    private int cannonWithdrawalFailures;
    private long cannonWithdrawalRetryAt;
    private long cannonFireAttemptStarted;
    private long cannonDiagnosticAt;
    private enum TempleDeparture { NONE, PICKUP, RUN_SOUTH, HOP }
    private TempleDeparture templeDeparture = TempleDeparture.NONE;
    private WorldPoint templeSouthTarget;
    private int templeHopWorld;
    private long templeHopAttemptAt;
    private int templeHopAttempts;
    private boolean templeDepartureComplete;
    private int cannonRefillThreshold = ThreadLocalRandom.current().nextInt(7, 13);
    private int cannonPreviousAmmo = -1;
    private long cannonPickupStarted;
    private boolean cannonAssemblyRecovery;
    private boolean cannonStartupPickup;
    private boolean cannonRecoveredForBank;
    private final DroSlayerCannonPickupWatchdog cannonPickupWatchdog = new DroSlayerCannonPickupWatchdog();
    private final DroSlayerCannonReclaim cannonReclaim = new DroSlayerCannonReclaim();
    private long cannonReclaimActionAt;
    private volatile boolean cannonPlacementRejected;
    private volatile WorldPoint lastCannonPlacementTile;
    private final Set<WorldPoint> rejectedCannonTiles = new HashSet<>();

    public void onCannonPlacementRejected() {
        if (lastCannonPlacementTile != null) cannonPlacementRejected = true;
    }

    private boolean cannonPlaced = false;
    private long cannonAssemblyProgressAt;
    private long cannonAssemblyAttemptAt;
    private int cannonAssemblyStage = -1;

    private boolean cannonFired = false;
    private boolean cannonRepairPending;
    private WorldPoint cannonSpot = null;

    // Crash detection / world hop tracking
    private long crashDetectionStartTime = 0;
    private long lastWorldHopTime = 0;
    private long occupiedHopAttemptTime;
    private long taskHopAttemptAt;
    private boolean occupiedAreaPending;
    private boolean isSearchingForTargets = false;
    private static final int CRASH_HOP_DELAY_MS = 20000; // 20 seconds before hopping
    private static final int WORLD_HOP_COOLDOWN_MS = 60000; // 1 minute cooldown between hops

    // Task completion loot delay
    private long taskCompletedTime = 0;
    private boolean taskCompletedLooting = false;
    private DroSlayerPrayer departureProtectionPrayer;
    private static final int TASK_COMPLETE_LOOT_DELAY_MS = 5000; // 5 seconds to loot after task complete

    // JSON profile tracking
    private DroSlayerProfileManager profileManager = new DroSlayerProfileManager();
    private volatile java.util.Map<String, DroSlayerPrayer> editedTaskPrayers = java.util.Map.of();

    private volatile java.util.Map<String, DroSlayerOffensivePrayer> editedOffensivePrayers = java.util.Map.of();
    private Rs2PrayerEnum lastOffensiveOverride;
    private volatile boolean profileReloadPending;

    public void onLootMessage(String message) {
        if (cluePickup.onMessage(message, System.currentTimeMillis()))
            log.info("Clue pickup tier limit reached; skipping that tier until Slayer restarts");
    }

    public void onWildernessWeaponMessage(String message) {
        wildernessWeaponCharge.onChatMessage(message);
    }

    public void updateTaskPrayers(java.util.Map<String, DroSlayerPrayer> prayers,
                                  java.util.Map<String, DroSlayerOffensivePrayer> offensive) {
        editedOffensivePrayers = java.util.Map.copyOf(offensive);
        editedTaskPrayers = java.util.Map.copyOf(prayers);
        profileReloadPending = true;
    }

    private DroSlayerTaskProfileJson activeJsonProfile = null;
    private boolean missingAncientSpellbookLogged;
    private boolean dustBurstFallback;
    private boolean dustBurstFallbackPending;
    private int dustBurstFallbackExitAttempts;
    private long dustBurstSwapStarted;
    private final DroSlayerEntRotation entRotation = new DroSlayerEntRotation();
    private final DroSlayerEntRotation revenantRotation = new DroSlayerEntRotation();
    private DroSlayerWildernessTasks.Assignment wildernessAssignment;
    private final DroSlayerWildernessRecovery wildernessRecovery = new DroSlayerWildernessRecovery();
    private final DroSlayerWildernessSettings wildernessSettings = new DroSlayerWildernessSettings();
    private final DroSlayerWildernessWeaponCharge wildernessWeaponCharge = new DroSlayerWildernessWeaponCharge();
    private boolean wildernessBankChargeStarted;
    private volatile WorldPoint pendingWildernessDeath;
    private volatile boolean wildernessDeathPending;
    private final DroSlayerRevenantEntry revenantEntry = new DroSlayerRevenantEntry();
    private boolean wildernessMode;
    private boolean wildernessHopRequired;
    private int wildernessHopTarget;
    private int wildernessHopAttempts;
    private long wildernessHopAttemptAt;
    private WorldPoint abandonedWildernessCannon;
    private boolean livingLogoutReturn;
    private volatile int cannonDeploymentWorld;
    private long cannonWorldReturnAttemptAt;
    private int cannonWorldReturnAttempts;
    private boolean cannonWorldReturnSuppressed;
    private long cannonMaintenanceRetryAt;
    private long nextWildernessSafetyActionAt;
    private boolean wildernessHopExhaustionLogged;
    private boolean wildernessProtectItemOwned;

    public boolean isWildernessSlayer() {
        return config != null && config.slayerMaster() == DroSlayerMaster.KRYSTILIA;
    }

    /** Event callback records intent only; recovery actions run on the script executor. */
    public boolean onWildernessDeath(WorldPoint location) {
        if (!isWildernessSlayer()) return false;
        revenantEntry.onDeath(location);
        pendingWildernessDeath = location;
        wildernessDeathPending = true;
        return true;
    }


    // All superior slayer monster names (requires "Bigger and Badder" unlock)
    private static final Set<String> SUPERIOR_MONSTERS = new HashSet<>(Arrays.asList(
            // Regular superiors
            "Crushing hand",           // Crawling hand
            "Chasm Crawler",           // Cave crawler
            "Screaming banshee",       // Banshee
            "Screaming twisted banshee", // Twisted banshee
            "Giant rockslug",          // Rockslug
            "Cockathrice",             // Cockatrice
            "Flaming pyrelord",        // Pyrefiend
            "Monstrous basilisk",      // Basilisk
            "Malevolent Mage",         // Infernal Mage
            "Insatiable Bloodveld",    // Bloodveld
            "Insatiable mutated Bloodveld", // Mutated Bloodveld
            "Vitreous Jelly",          // Jelly
            "Vitreous warped Jelly",   // Warped Jelly
            "Cave abomination",        // Cave horror
            "Abhorrent spectre",       // Aberrant spectre
            "Repugnant spectre",       // Deviant spectre
            "Choke devil",             // Dust devil
            "King kurask",             // Kurask
            "Marble gargoyle",         // Gargoyle
            "Nechryarch",              // Nechryael
            "Greater abyssal demon",   // Abyssal demon
            "Night beast",             // Dark beast
            "Nuclear smoke devil",     // Smoke devil
            // Kebos/newer superiors
            "Shadow Wyrm",             // Wyrm
            "Guardian Drake",          // Drake
            "Colossal Hydra",          // Hydra
            "Basilisk Sentinel"        // Basilisk Knight
    ));

    @Inject private net.runelite.client.config.ConfigManager cannonConfigManager;
    private String cannonProfileKey;
    private String lastSavedCannon;
    private boolean cannonStartupCheck;

    private String cannonSnapshot() {
        WorldPoint tile = lastCannonPlacementTile;
        String deployed = cannonDeploymentWorld > 0 && tile != null
                ? cannonDeploymentWorld + "," + tile.getX() + "," + tile.getY() + "," + tile.getPlane() : "-";
        return deployed + ";" + cannonReclaim.snapshot();
    }

    private void restoreCannonRecord() {
        traceDroAction("restoreCannonRecord");
        if (cannonConfigManager == null) return;
        String profile = cannonConfigManager.getRSProfileKey();
        if (profile == null || profile.equals(cannonProfileKey)) return;
        // Never carry one account's cannon into another account's session.
        clearCannonDeployment();
        cannonReclaim.found();
        cannonAssemblyRecovery = false;
        cannonProfileKey = profile;
        cannonStartupPickup = false;
        cannonRecoveredForBank = false;
        cannonStartupCheck = false;
        String saved = cannonConfigManager.getConfiguration("droslayer", profile, "cannonRecoveryV1");
        if (saved != null) {
            try {
                String[] record = saved.split(";");
                cannonReclaim.restore(record[1]);
                if (!record[0].equals("-")) {
                    String[] site = record[0].split(",");
                    cannonDeploymentWorld = Integer.parseInt(site[0]);
                    lastCannonPlacementTile = new WorldPoint(Integer.parseInt(site[1]), Integer.parseInt(site[2]), Integer.parseInt(site[3]));
                    cannonPlaced = true;
                    cannonAssemblyRecovery = true;
                    cannonStartupPickup = true;
                }
                log.info("Restored cannon recovery record: world={} tile={} reclaim={}",
                        getCannonWorldDisplay(), getCannonTileDisplay(), cannonReclaim.phase());
            } catch (RuntimeException ex) {
                clearCannonDeployment();
                cannonReclaim.found();
                cannonAssemblyRecovery = false;
                cannonStartupPickup = false;
                log.warn("Invalid saved cannon recovery record; ignoring it");
            }
        }
        cannonStartupCheck = cannonDeploymentWorld > 0 || cannonReclaim.lastWorld() > 0
                || cannonReclaim.phase() != DroSlayerCannonReclaim.Phase.NONE;
        lastSavedCannon = cannonSnapshot();
    }

    /** Verify carried/banked parts before acting on a record restored after restart. */
    private boolean checkStartupCannonSupplies() {
        boolean observeManualCannon = !cannonPlaced && cannonDeploymentWorld == 0
                && cannonReclaim.phase() == DroSlayerCannonReclaim.Phase.NONE;
        if (!cannonStartupCheck && !observeManualCannon) return false;
        // A restart beside this account's live cannon is not a lost-cannon recovery.
        // Keep the saved-world guard in findOwnedCannon; a foreign scene object cannot
        // establish ownership without the account's live assembly/coordinate receipt.
        int stage = DroSlayerStartupLocation.atCurrentTask(config) ? cannonBuildStage() : 0;
        if (stage > 0) {
            var observed = findOwnedCannon(50);
            WorldPoint owner = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                int coord = Microbot.getClient().getVarpValue(net.runelite.api.VarPlayer.CANNON_COORD);
                return coord > 0 ? WorldPoint.fromCoord(coord) : null;
            }).orElse(null);
            if (observed != null && observed.getWorldLocation().equals(owner)) {
                cannonDeploymentWorld = currentWorld();
                lastCannonPlacementTile = owner;
                cannonPlaced = stage == 4;
                cannonAssemblyStage = stage;
                cannonAssemblyRecovery = false;
                cannonStartupPickup = false;
                cannonStartupCheck = false;
                cannonReclaim.found();
                saveCannonRecord();
                log.info("Startup cannon check: owned cannon observed at task; continuing native validation");
                return false;
            }
        }
        if (!cannonStartupCheck) return false;
        boolean complete = hasCannonParts();
        if (!complete && !Rs2Bank.isOpen()) {
            DroSlayerRuntime.walkToBankAndOpen(config, "Verify saved cannon recovery supplies");
            return true;
        }
        if (!complete) {
            complete = Arrays.stream(new String[]{"Cannon base", "Cannon stand", "Cannon barrels", "Cannon furnace"})
                    .allMatch(part -> Rs2Inventory.hasItem(part) || Rs2Bank.hasItem(part));
        }
        cannonStartupCheck = false;
        if (complete) {
            cannonAssemblyRecovery = false;
            cannonStartupPickup = false;
            clearCannonDeployment();
            cannonReclaim.found();
            DroSlayerRuntime.cancelWalk();
            log.info("Startup cannon check: all four parts confirmed in inventory/bank; saved recovery cancelled");
        } else {
            log.info("Startup cannon check: complete cannon absent from inventory/bank; retaining saved recovery");
        }
        return false;
    }

    private void saveCannonRecord() {
        if (cannonConfigManager == null || cannonProfileKey == null
                || !cannonProfileKey.equals(cannonConfigManager.getRSProfileKey())) return;
        String snapshot = cannonSnapshot();
        if (snapshot.equals(lastSavedCannon)) return;
        cannonConfigManager.setConfiguration("droslayer", cannonProfileKey, "cannonRecoveryV1", snapshot);
        lastSavedCannon = snapshot;
    }

    @Inject
    public DroSlayerScript() {
    }

    public boolean run(DroSlayerConfig config) {
        cannonProfileKey = null;
        lastSavedCannon = null;
        revenantEntry.reset();
        cannonAssemblyRecovery = false;
        cannonPickupWatchdog.reset();
        cannonStartupPickup = false;
        cannonRecoveredForBank = false;
        cannonReclaim.found();
        cluePickup.reset();
        cannonPurchase.reset();
        bankSupplyRetryAt = 0L;
        bankSupplyWaitReason = null;
        this.config = config;
        resetSkipState();
        resetBlockState();
        sessionEscapes.set(0);
        sessionDeaths.set(0);
        wildernessMasterBeforeRestore = false;
        cannonPolicy.reset();
        resetCannonWithdrawal();
        clearDepartureProtection();
        rejectedCannonTiles.clear();
        cannonPlacementRejected = false;
        lastCannonPlacementTile = null;
        occupiedAreaPending = false;
        occupiedHopAttemptTime = 0L;
        cannonAssemblyStage = -1;
        cannonAssemblyProgressAt = 0L;
        cannonAssemblyAttemptAt = 0L;
        DroSlayerPlugin.setState(DroSlayerState.IDLE);
        initialSetupDone = false;
        baseBankLoadComplete = false;
        tentacleDegraded = false;
        tentacleBankCheck = false;
        escapeAmmoRestock = false;
        escapeAmmoClosing = false;
        tentacleRepair.reset();
        feroxRestored = false;
        lastEmergencyEscapeAttemptAt = 0L;
        templeDeparture = TempleDeparture.NONE;
        templeDepartureComplete = false;
        templeSouthTarget = null;
        templeHopWorld = 0;
        templeHopAttempts = 0;
        templeHopAttemptAt = 0;
        lastFeroxPoolClickAt = 0L;

        // Load NPC location data required for Rs2Slayer.getSlayerTaskLocation()
        try {
            Rs2NpcManager.loadJson();
            log.info("NPC location data loaded successfully");
        } catch (Exception e) {
            log.error("Failed to load NPC location data: ", e);
        }

        // Load JSON profiles from file
        profileManager.loadProfiles();
        wildernessMode = isWildernessSlayer();
        wildernessAssignment = null;
        revenantRotation.reset();
        templeRestoreDeadline = 0;
        templeRestoreCooldown = 0;
        templeRestoreLastClick = 0;
        wildernessDeathPending = false;
        wildernessHopRequired = false;
        wildernessHopTarget = 0;
        wildernessHopAttempts = 0;
        wildernessHopAttemptAt = 0L;
        wildernessHopExhaustionLogged = false;
        abandonedWildernessCannon = null;
        livingLogoutReturn = false;
        cannonDeploymentWorld = 0;
        cannonWorldReturnAttemptAt = 0L;
        cannonWorldReturnAttempts = 0;
        cannonWorldReturnSuppressed = false;
        cannonMaintenanceRetryAt = 0L;
        nextWildernessSafetyActionAt = 0L;
        nextProtectItemReleaseAttemptAt = 0L;
        clearPreCombatHold();
        wildernessProtectItemOwned = false;
        wildernessSettings.reset();
        wildernessWeaponCharge.reset();
        wildernessBankChargeStarted = false;
        wildernessRecovery.cancel();


        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!config.enablePlugin()) {
                    DroSlayerRuntime.releaseWildernessContext();
                    return;
                }

                if (wildernessMode != isWildernessSlayer()) {
                    clearDepartureProtection();
                    wildernessMode = isWildernessSlayer();
                    wildernessSettings.reset();
                    wildernessWeaponCharge.reset();
                    wildernessBankChargeStarted = false;
                    wildernessRecovery.cancel();
                    wildernessHopRequired = false;
                    wildernessHopTarget = 0;
                    wildernessHopAttempts = 0;
                    wildernessHopExhaustionLogged = false;
                    abandonedWildernessCannon = null;
                    cannonDeploymentWorld = 0;
                    resetTravelData();
                    initialSetupDone = false;
                    DroSlayerPlugin.setState(DroSlayerState.IDLE);
                }
                DroSlayerRuntime.syncWildernessContext(config, !wildernessHopRequired && !wildernessRecovery.isActive(),
                        isSouthernRevenantGoblinTask());
                if (isWildernessSlayer() && "LOGGED_OUT".equals(DroSlayerRuntime.wildernessEscapeState())) {
                    wildernessHopRequired = true;
                    livingLogoutReturn = true;
                    abandonedWildernessCannon = cannonPlaced || cannonAssemblyStage > 0 ? lastCannonPlacementTile : null;
                    DroSlayerRuntime.acknowledgeWildernessEscape();
                    DroSlayerPlugin.setState(DroSlayerState.WILDERNESS_ESCAPE);
                }
                // BaseProfileDro owns breaks/login recovery and must be ticked even while logged out.
                DroSlayerRuntime.tick(config);
                if (DroSlayerRuntime.shouldPauseWork()) { wildernessRecovery.suspend(); logRecoveryHold("profile/break gate"); return; }

                if (!Microbot.isLoggedIn()) {
                    wildernessRecovery.suspend();
                    logRecoveryHold("not logged in");
                    wasLoggedOut = true;
                    return;
                }
                if (!super.run()) { wildernessRecovery.suspend(); logRecoveryHold("base script gate: " + Microbot.status); return; }
                if (isWildernessSlayer() && !wildernessDeathPending && !wildernessRecovery.isActive()) {
                    revenantEntry.observeLocation(Rs2Player.getWorldLocation());
                }
                restoreCannonRecord();
                reconcileCarriedCannon();
                if (handleWildernessLifecycle()) {
                    reportPreCombatHold("Wilderness escape or recovery holds actions");
                    return;
                }
                if (checkStartupCannonSupplies()) {
                    reportPreCombatHold("Saved cannon supply verification");
                    return;
                }
                if (handleCannonReclaim()) {
                    reportPreCombatHold("Cannon reclaim in progress");
                    return;
                }
                if (cannonAssemblyRecovery) {
                    reportPreCombatHold("Cannon assembly recovery");
                    if (isWildernessSlayer()) maintainWildernessEscapeSurvival();
                    if (handleCannonPickup()) {
                        finishCannonAssemblyRecovery();
                    }
                    return;
                }
                DroSlayerState cannonReturnState = DroSlayerPlugin.getState();
                if ((cannonReturnState == DroSlayerState.TRAVELING || cannonReturnState == DroSlayerState.AT_LOCATION
                        || cannonReturnState == DroSlayerState.FIGHTING)
                        && holdForCannonWorldReturn()) {
                    reportPreCombatHold("Waiting for deployed cannon's original world");
                    return;
                }
                maintainDepartureProtection();
                if (isWildernessSlayer() && !wildernessSettings.ensureSafeSettings()) {
                    DroSlayerRuntime.cancelWalk();
                    if (wildernessSettings.isBlocked()) Microbot.status = wildernessSettings.getFailureReason();
                    reportPreCombatHold("Wilderness skull-prevention / Accept Aid settings");
                    return;
                }
                clearPreCombatHold();
                if (profileReloadPending) {
                    profileReloadPending = false;
                    profileManager.loadProfiles();
                    activeJsonProfile = profileManager.findProfile(currentTaskProfileName());
                    log.info("Task prayer/bracelet edits reloaded for {}", currentTaskName());
                }
                if (tentacleDegraded) {
                    tentacleDegraded = false;
                    tentacleBankCheck = true;
                    baseBankLoadComplete = false;
                    DroSlayerRuntime.cancelWalk();
                    setAutoRetaliateHumanized(false);
                    log.warn("Abyssal tentacle degraded: stopping combat and banking for replacement");
                    if (hasBankEscapeTeleportInInventory()) {
                        beginFeroxRecovery(DroSlayerState.BANKING, "abyssal tentacle degraded");
                    } else {
                        DroSlayerPlugin.setState(DroSlayerState.BANKING);
                    }
                    return;
                }


                // Handle resume after break/logout
                if (wasLoggedOut) {
                    wasLoggedOut = false;
                    DroSlayerState currentState = DroSlayerPlugin.getState();
                    // If we were in a transient state, reset to re-evaluate
                    if (currentState != DroSlayerState.IDLE &&
                        currentState != DroSlayerState.BANKING &&
                        currentState != DroSlayerState.RESTORING_AT_FEROX
                        && currentState != DroSlayerState.WILDERNESS_RECOVERY
                        && currentState != DroSlayerState.WILDERNESS_ESCAPE) {
                        log.info("Resuming after logout - resetting to DETECTING_TASK to re-evaluate");
                        DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK);
                    }
                }

                // Read slayer points
                int slayerPoints = Microbot.getVarbitValue(SLAYER_REWARD_POINTS_VARBIT);
                DroSlayerPlugin.setSlayerPoints(slayerPoints);

                // Detect task
                boolean hasTask = Rs2Slayer.hasSlayerTask();
                String taskName = "";
                int remaining = 0;

                if (hasTask) {
                    taskName = currentTaskName();
                    remaining = Rs2Slayer.getSlayerTaskSize();
                }

                DroSlayerPlugin.updateTaskInfo(hasTask, taskName, remaining);

                // Recovery owns the tick until teleport arrival and restoration are observed.
                DroSlayerState observedState = DroSlayerPlugin.getState();
                if (observedState == DroSlayerState.AT_LOCATION || observedState == DroSlayerState.FIGHTING)
                    templeDepartureComplete = false;
                if (!templeDepartureComplete && atElderChaosTemple()
                        && !(wildernessMasterBeforeRestore && observedState == DroSlayerState.GETTING_TASK)
                        && (observedState == DroSlayerState.BANKING
                            || observedState == DroSlayerState.RESTORING_AT_POH
                            || observedState == DroSlayerState.GETTING_TASK)) {
                    beginFeroxRecovery(observedState, "leaving Elder Chaos druids");
                    observedState = DroSlayerState.RESTORING_AT_FEROX;
                }
                if (observedState != DroSlayerState.BANKING) {
                    bankSupplyRetryAt = 0L;
                    bankSupplyWaitReason = null;
                    baseBankLoadComplete = false;
                    if (!wildernessWeaponCharge.isBusy()) wildernessBankChargeStarted = false;
                }
                if (observedState == DroSlayerState.TRAVELING
                        || observedState == DroSlayerState.AT_LOCATION
                        || observedState == DroSlayerState.FIGHTING) {
                    feroxRestored = false;
                    if ((!hasTask || remaining <= 0) && !cannonPlaced) {
                        finishTaskAndChooseNextStop();
                    } else if (handleEmergencyLowHpEscape("active trip")) {
                        return;
                    }
                }
                if ((observedState == DroSlayerState.BANKING || observedState == DroSlayerState.RESTORING_AT_POH)
                        && !wildernessWeaponCharge.isBusy()
                        && (initialSetupDone || isAlreadyInsideCurrentTaskArea())
                        && !feroxRestored && needsFeroxRecoveryBeforeDeparture()
                        && hasBankEscapeTeleportInInventory()) {
                    beginFeroxRecovery(DroSlayerState.BANKING, "pre-bank resource threshold (70%)");
                }

                // State machine
                DroSlayerState currentState = DroSlayerPlugin.getState();

                if (hasTask && remaining > 0 && handleOccupiedTaskArea(currentState)) return;

                switch (currentState) {
                    case IDLE:
                        handleIdleState(hasTask, taskName);
                        break;
                    case GETTING_TASK:
                        handleGettingTaskState(hasTask, taskName);
                        break;
                    case SKIPPING_TASK:
                        handleSkippingTaskState();
                        break;
                    case BLOCKING_TASK:
                        handleBlockingTaskState();
                        break;
                    case DETECTING_TASK:
                        handleDetectingTaskState(hasTask);
                        break;
                    case RESTORING_AT_FEROX:
                        handleFeroxRecovery();
                        break;
                    case RESTORING_AT_POH:
                        handleRestoringAtPohState();
                        break;
                    case BANKING:
                        handleBankingState();
                        break;
                    case SWAPPING_SPELLBOOK:
                        handleSwappingSpellbookState();
                        break;
                    case TRAVELING:
                        handleTravelingState();
                        break;
                    case AT_LOCATION:
                        handleAtLocationState(hasTask, remaining);
                        break;
                    case FIGHTING:
                        handleFightingState(hasTask, remaining);
                        break;
                }

            } catch (Exception ex) {
                log.error("Slayer script error: ", ex);
            } finally {
                saveCannonRecord();
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean cannonEnabledForMaster() {
        return isWildernessSlayer() ? config.wildernessPreferCannon() : config.enableCannon();
    }

    private List<Rs2ItemModel> taskFood() {
        return Rs2Inventory.getList(item -> DroSlayerWildernessSupplies.isFood(item, isWildernessSlayer()));
    }

    private List<Rs2ItemModel> usableFood() {
        return Rs2Inventory.getList(item -> DroSlayerWildernessSupplies.isFood(item, Rs2Pvp.isInWilderness()));
    }

    private boolean hasRequiredEscapeSupplies() {
        return isWildernessSlayer() ? hasBankEscapeTeleportInInventory() : duelingChargesCarried() >= 2;
    }

    private void ensureWildernessEscapeAtBank() {
        if (!Rs2Bank.isOpen() || hasBankEscapeTeleportInInventory()) return;
        Rs2ItemModel escape = Rs2Bank.bankItems().stream()
                .filter(item -> item.getQuantity() > 0 && !item.isNoted() && DroSlayerWalkerItems.isLevel30Escape(item.getName()))
                .min(Comparator.comparingInt(item -> DroSlayerWalkerItems.escapePreference(item.getName()))).orElse(null);
        if (escape == null) {
            // Standalone Slayer holds until the player supplies an escape item.
            Microbot.status = "Krystilia needs a level-30 escape item (seed pod or charged dragonstone jewellery)";
            log.warn("Krystilia departure held: no level-30 escape item in bank");
            unresolvedTaskName = currentTaskName();
            DroSlayerPlugin.setState(DroSlayerState.IDLE);
            return;
        }
        if (Rs2Inventory.isFull() && !ensureFreeInventorySlotForTaskItem()) return;
        DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                (java.util.function.BooleanSupplier) () -> Rs2Bank.withdrawOne(escape.getId()));
        sleepUntil(this::hasBankEscapeTeleportInInventory, 1800);
    }

    private String wildernessAccessProblem() {
        if (wildernessAssignment == null) return null;
        String task = wildernessAssignment.getTaskName().toLowerCase(Locale.ROOT);
        if (wildernessAssignment.getLocationName().toLowerCase(Locale.ROOT).contains("god wars")
                && Math.max(Rs2Player.getRealSkillLevel(Skill.AGILITY), Rs2Player.getRealSkillLevel(Skill.STRENGTH)) < 60) {
            return "Wilderness God Wars entrance needs 60 Agility or Strength";
        }
        if (task.contains("earth warrior") && Rs2Player.getRealSkillLevel(Skill.AGILITY) < 15) {
            return "Earth warriors need 15 Agility for the monkey bars";
        }
        if (task.contains("magic axe") && Rs2Player.getRealSkillLevel(Skill.THIEVING) < 23) {
            return "Magic axe hut needs 23 Thieving";
        }
        if (task.contains("pirate") && Rs2Player.getRealSkillLevel(Skill.THIEVING) < 39) {
            return "Pirates' Hideout needs 39 Thieving";
        }
        return null;
    }

    private void holdWildernessTask(String reason) {
        unresolvedTaskName = currentTaskName();
        Microbot.status = reason;
        log.warn("Krystilia task held: {}", reason);
        DroSlayerPlugin.setState(DroSlayerState.IDLE);
    }

    private boolean hasWildernessCombatLoadout() {
        if (isWildernessSlayer() && wildernessAssignment != null
                && wildernessAssignment.getRequirements().contains(DroSlayerWildernessTasks.Requirement.RANGED_OR_MAGIC)
                && detectCurrentCombatStyle() != DroSlayerCombatStyle.RANGED) {
            holdWildernessTask("Equip a ranged weapon in rangewildy for Aviansies");
            return false;
        }
        return true;
    }

    private boolean ensureWildernessSuppliesAtBank() {
        if (!isWildernessSlayer()) return true;
        // Keep prayer supplies available for player attacks and configured task prayers.
        int dosesWanted = Math.max(4, config.potionThreshold() + 4);
        if (getPrayerPotionDoses() < dosesWanted) {
            Rs2ItemModel restore = Rs2Bank.bankItems().stream()
                    .filter(item -> DroSlayerWildernessSupplies.isPrayerPotion(item, true))
                    .min(Comparator.comparingInt(item ->
                            (DroSlayerWildernessSupplies.isBlighted(item.getName()) ? 0 : 10)
                                    - DroSlayerWildernessSupplies.prayerDoses(item.getName(), true))).orElse(null);
            if (restore == null) {
                unresolvedTaskName = currentTaskName();
                Microbot.status = "Krystilia needs prayer supplies in bank";
                DroSlayerPlugin.setState(DroSlayerState.IDLE);
                log.warn("Krystilia departure held: prayer supply stock exhausted");
                terminalStopMessage = Microbot.supplyMessage("prayer potions or restores");
                stopAndLogout(true);
                return false;
            }
            if (Rs2Inventory.isFull() && !ensureFreeInventorySlotForTaskItem()) return false;
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                    (java.util.function.BooleanSupplier) () -> Rs2Bank.withdrawOne(restore.getId()));
            return false;
        }
        if (shouldUseAoeCombat() && config.wildernessIceSacks() > 0) {
            int have = Rs2Inventory.itemQuantity(DroSlayerWildernessSupplies.ICE_SACK_ID);
            int missing = config.wildernessIceSacks() - have;
            int available = Rs2Bank.count(DroSlayerWildernessSupplies.ICE_SACK_ID);
            if (missing > 0 && available > 0) {
                if (Rs2Inventory.isFull() && !ensureFreeInventorySlotForTaskItem()) return false;
                DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                        (java.util.function.BooleanSupplier) () -> Rs2Bank.withdrawX(DroSlayerWildernessSupplies.ICE_SACK_ID, Math.min(missing, available)));
                return false;
            }
        }
        if (wildernessAssignment != null && wildernessAssignment.getRequirements()
                .contains(DroSlayerWildernessTasks.Requirement.REVENANT_ENTRY_FEE)
                && revenantEntry.needsCoins()
                && !isAlreadyInsideCurrentTaskArea() && Rs2Inventory.itemQuantity("Coins", true) < 100000) {
            int missing = 100000 - Rs2Inventory.itemQuantity("Coins", true);
            if (Rs2Bank.count("Coins") < missing) {
                return stopForBankSupply("Coins (100,000 required for Revenant Cave entry)");
            }
            if (Rs2Inventory.isFull() && !ensureFreeInventorySlotForTaskItem()) return false;
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                    (java.util.function.BooleanSupplier) () -> Rs2Bank.withdrawX("Coins", missing));
            return false;
        }
        return true;
    }

    private long nextRecoveryHoldLogAt;
    private long nextPreCombatHoldLogAt;
    private String lastPreCombatHold = "";
    private long nextProtectItemReleaseAttemptAt;

    /** No generic movement/combat retries may bypass a Wilderness safety hold. */
    private void reportPreCombatHold(String gate) {
        DroSlayerState state = DroSlayerPlugin.getState();
        if (state != DroSlayerState.AT_LOCATION && state != DroSlayerState.FIGHTING) return;
        long now = System.currentTimeMillis();
        if (gate.equals(lastPreCombatHold) && now < nextPreCombatHoldLogAt) return;
        lastPreCombatHold = gate;
        nextPreCombatHoldLogAt = now + 12_000L;
        WorldPoint here = Rs2Player.getWorldLocation();
        log.warn("[DroSlayer][PreCombat] held gate={} task={} state={} player={} "
                + "cannonSpot={} cannonEnabled={} deployed={} stage={} walker={} escape={} "
                + "safetyBlocked={} safetyReason={}",
                gate, currentTaskName(), state, here, cannonSpot, isUsingCannon, cannonPlaced,
                cannonBuildStage(), DroSlayerRuntime.walkerStatus(),
                DroSlayerRuntime.wildernessEscapeState(), wildernessSettings.isBlocked(),
                wildernessSettings.getFailureReason());
        // Preserve the original Wilderness threat/safety status; this is diagnostic only.
    }

    private void clearPreCombatHold() {
        lastPreCombatHold = "";
        nextPreCombatHoldLogAt = 0L;
    }

    private void logRecoveryHold(String reason) {
        if (!wildernessDeathPending && !wildernessRecovery.isActive()) return;
        long now = System.currentTimeMillis();
        if (now < nextRecoveryHoldLogAt) return;
        nextRecoveryHoldLogAt = now + 15_000L;
        log.warn("Wilderness recovery held before lifecycle: {} paused={} interrupted={}",
                reason, Microbot.pauseAllScripts.get(), Thread.currentThread().isInterrupted());
    }

    /** Normal Slayer states cannot issue actions while Walker is escaping or recovery owns the trip. */
    private boolean handleWildernessLifecycle() {
        traceDroAction("handleWildernessLifecycle");
        if (!isWildernessSlayer()) return false;
        if (wildernessDeathPending) {
            cannonPurchase.reset();
            wildernessDeathPending = false;
            livingLogoutReturn = false;
            clearDepartureProtection();
            wildernessWeaponCharge.reset();
            wildernessBankChargeStarted = false;
            WorldPoint deployed = wildernessRecovery.isActive() ? wildernessRecovery.getCannonTile()
                    : cannonPlaced || cannonAssemblyStage > 0 ? lastCannonPlacementTile : abandonedWildernessCannon;
            wildernessRecovery.begin(pendingWildernessDeath, deployed, cannonDeploymentWorld, wildernessNeedsLockpick());
            templeDeparture = TempleDeparture.NONE;
            templeDepartureComplete = false;
            taskCompletedLooting = false;
            DroSlayerRuntime.acknowledgeWildernessEscape();
            DroSlayerPlugin.setState(DroSlayerState.WILDERNESS_RECOVERY);
            resetCannonState();
            occupiedAreaPending = false;
        }
        if (wildernessRecovery.isActive()) {
            DroSlayerPlugin.setState(DroSlayerState.WILDERNESS_RECOVERY);
            DroSlayerWildernessRecovery.Result result = wildernessRecovery.tick(config);
            if (result == DroSlayerWildernessRecovery.Result.BLOCKED) {
                log.error("Wilderness recovery stopped: {}", wildernessRecovery.getFailureReason());
                terminalStopMessage = "Wilderness recovery needs attention: " + wildernessRecovery.getFailureReason();
                stopAndLogout(true);
            } else if (result == DroSlayerWildernessRecovery.Result.COMPLETE) {
                clearDepartureProtection();
                if (wildernessRecovery.wasCannonUnavailable()) {
                    if (wildernessRecovery.getCannonTile() != null) cannonReclaim.missing(System.currentTimeMillis());
                    disableCannonForTrip("Optional cannon recovery unavailable");
                }
                else clearCannonDeployment();
                wildernessRecovery.cancel();
                abandonedWildernessCannon = null;
                wildernessHopRequired = true;
                wildernessHopTarget = 0;
                wildernessHopAttempts = 0;
                initialSetupDone = false;
                baseBankLoadComplete = false;
                resetCannonState();
                DroSlayerPlugin.setState(DroSlayerState.WILDERNESS_ESCAPE);
            }
            return true;
        }
        maintainWildernessProtectItem();
        String escape = DroSlayerRuntime.wildernessEscapeState();
        if ("ESCAPED".equals(escape)) {
            sessionEscapes.incrementAndGet();
            templeDeparture = TempleDeparture.NONE;
            templeDepartureComplete = false;
            DroSlayerRuntime.acknowledgeWildernessEscape();
            wildernessHopRequired = false;
            wildernessHopTarget = 0;
            wildernessHopAttempts = 0;
            wildernessHopExhaustionLogged = false;
            abandonedWildernessCannon = null;
            // A living teleport escape keeps the current loadout and deployed cannon.
            // Walker owns the return route and its existing player-alert travel policy.
            DroSlayerRuntime.cancelWalk();
            log.info("Wilderness teleport escape complete; returning to task {} with current gear and cannon", taskDestination);
            escapeAmmoRestock = (isUsingCannon || cannonPlaced && cannonEnabledForMaster()) && getCannonballCount()
                    < DroSlayerCannonPolicy.restockThreshold(true, config.cannonballThreshold());
            escapeAmmoClosing = false;
            log.info("Escape cannon supply check: using={} deployed={} carried={} loaded={} threshold={} restock={}",
                    isUsingCannon, cannonPlaced, getCannonballCount(), loadedCannonballCount(),
                    DroSlayerCannonPolicy.restockThreshold(true, config.cannonballThreshold()), escapeAmmoRestock);
            DroSlayerPlugin.setState(escapeAmmoRestock ? DroSlayerState.BANKING : DroSlayerState.TRAVELING);
            return true;
        }
        if ("ESCAPING".equals(escape) || "LOGOUT_WAIT_COMBAT".equals(escape) || "BLOCKED".equals(escape)) {
            maintainDepartureProtection();
            DroSlayerPlugin.setState(DroSlayerState.WILDERNESS_ESCAPE);
            setAutoRetaliateHumanized(false);
            maintainWildernessEscapeSurvival(false);
            // No movement, attack, cannon toggle, or competing teleport while Walker owns escape.
            return true;
        }
        if (wildernessHopRequired) return handleWildernessResumeHop();
        if (DroSlayerPlugin.getState() == DroSlayerState.WILDERNESS_ESCAPE && "IDLE".equals(escape)) {
            // A threat that cleared without an escape releases the task back to its normal setup/combat state.
            DroSlayerPlugin.setState(DroSlayerState.AT_LOCATION);
        }
        return false;
    }

    private boolean handleWildernessResumeHop() {
        traceDroAction("handleWildernessResumeHop");
        long now = System.currentTimeMillis();
        if (wildernessHopTarget > 0 && Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getGameState() == GameState.LOGGED_IN
                        && Microbot.getClient().getWorld() == wildernessHopTarget).orElse(false)) {
            log.info("Wilderness recovery/escape resumed in world {}", wildernessHopTarget);
            wildernessHopRequired = false;
            wildernessHopTarget = 0;
            wildernessHopAttempts = 0;
            wildernessHopExhaustionLogged = false;
            initialSetupDone = false;
            baseBankLoadComplete = false;
            DroSlayerPlugin.setState(DroSlayerState.BANKING);
            return true;
        }
        if (livingLogoutReturn || abandonedWildernessCannon != null) {
            // A living logout keeps the combat loadout. The normal return guard safely rejoins
            // the deployment world; death recovery alone owns stripping/regearing.
            livingLogoutReturn = false;
            wildernessHopRequired = false;
            wildernessHopTarget = 0;
            wildernessHopAttempts = 0;
            abandonedWildernessCannon = null;
            DroSlayerPlugin.setState(DroSlayerState.TRAVELING);
            return true;
        }
        // Move out of aggressive NPC reach before opening the hopper. No retries during combat.
        if (Rs2Pvp.isInWilderness()) {
            handleEating(); handlePotions();
            if (!DroSlayerRuntime.walkToBankAndOpen(config, "Wilderness escape bank")) handleRouteFailureBudget("Wilderness escape bank");
            return true;
        }
        if (Rs2Player.isInCombat() || isBeingAttacked()) return true;
        if (Rs2Bank.isOpen()) {
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, (Runnable) Rs2Bank::closeBank);
            return true;
        }
        if (now - wildernessHopAttemptAt < DroSlayerHopPolicy.RETRY_GAP_MS) return true;
        if (wildernessHopAttempts >= config.maxRecoveryAttempts()) {
            if (!wildernessHopExhaustionLogged) {
                wildernessHopExhaustionLogged = true;
                log.warn("Wilderness resume hop watchdog exhausted; task held safely");
            }
            Microbot.status = "Wilderness world hop needs attention";
            return true;
        }
        if (wildernessHopTarget == 0) wildernessHopTarget = safeWildernessHopWorld();
        if (wildernessHopTarget <= 0) { wildernessHopAttemptAt = now; return true; }
        wildernessHopAttemptAt = now;
        wildernessHopAttempts++;
        DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                (java.util.function.BooleanSupplier) () -> Microbot.hopToWorld(wildernessHopTarget));
        return true;
    }

    private String currentTaskName() {
        return isWildernessSlayer() ? DroSlayerWildernessTaskReader.getTaskName() : Rs2Slayer.getSlayerTask();
    }

    private boolean wildernessNeedsLockpick() {
        return wildernessAssignment != null && wildernessAssignment.getRequirements()
                .contains(DroSlayerWildernessTasks.Requirement.LOCKPICK);
    }

    private void maintainWildernessProtectItem() {
        boolean attacked = Rs2Pvp.isInWilderness() && DroSlayerRuntime.isWildernessPlayerAttackActive();
        if (attacked && Rs2Player.getRealSkillLevel(Skill.PRAYER) >= 25
                && Rs2Player.getBoostedSkillLevel(Skill.PRAYER) > 0
                && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_ITEM)) {
            if (togglePrayerHumanized(Rs2PrayerEnum.PROTECT_ITEM, true)) wildernessProtectItemOwned = true;
        } else if (!attacked && wildernessProtectItemOwned) {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_ITEM)) {
                wildernessProtectItemOwned = false;
                nextProtectItemReleaseAttemptAt = 0L;
            } else if (System.currentTimeMillis() >= nextProtectItemReleaseAttemptAt) {
                // A failed toggle must not monopolize the urgent maintenance input lane.
                // The old loop attempted to disable Protect Item every 600 ms indefinitely,
                // leaving cannon placement and attack inputs starved on contested clients.
                nextProtectItemReleaseAttemptAt = System.currentTimeMillis() + 3000L;
                if (togglePrayerHumanized(Rs2PrayerEnum.PROTECT_ITEM, false)) {
                    wildernessProtectItemOwned = false;
                    nextProtectItemReleaseAttemptAt = 0L;
                }
            }
        }
    }

    private int wildernessPrayerThreshold() {
        return isWildernessSlayer() && DroSlayerRuntime.isWildernessPlayerAttackActive()
                ? playerAttackPrayerThreshold(Rs2Player.getRealSkillLevel(Skill.PRAYER), config.drinkPrayerAt())
                : config.drinkPrayerAt();
    }

    static int playerAttackPrayerThreshold(int prayerLevel, int configured) {
        return Math.max(configured, (int) Math.ceil(Math.max(0, prayerLevel) * 0.70));
    }

    private int currentWorld() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getWorld()).orElse(0);
    }

    /** One survival action per observation interval; never issue a competing teleport or combat buff. */
    private void maintainWildernessEscapeSurvival() {
        maintainWildernessEscapeSurvival(false);
    }

    private void maintainWildernessEscapeSurvival(boolean surrender) {
        long now = System.currentTimeMillis();
        if (now < nextWildernessSafetyActionAt || Rs2Player.isAnimating()) return;
        nextWildernessSafetyActionAt = now + 1800L;
        if (!surrender && handleEating()) return;
        if (Rs2Player.getBoostedSkillLevel(Skill.PRAYER) <= wildernessPrayerThreshold()) {
            Rs2ItemModel restore = Rs2Inventory.get(item ->
                    DroSlayerWildernessSupplies.isPrayerPotion(item, Rs2Pvp.isInWilderness()));
            if (restore != null && DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, false,
                    () -> Rs2Inventory.interact(restore, "Drink"))) return;
        }
    }

    static boolean isEligibleWildernessTarget(String name, int slayerLevel) {
        if (name == null) return false;
        switch (name.toLowerCase(Locale.ROOT)) {
            case "spiritual ranger": return slayerLevel >= 63;
            case "spiritual warrior": return slayerLevel >= 68;
            case "spiritual mage": return slayerLevel >= 83;
            default: return true;
        }
    }

    private int safeWildernessHopWorld() {
        var result = Microbot.getWorldService().getWorlds();
        if (result == null) return 0;
        int current = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getWorld()).orElse(0);
        boolean members = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.MEMBERS)).orElse(true);
        var candidates = result.getWorlds().stream()
                .filter(world -> DroSlayerHopPolicy.eligible(world, members, current))
                .filter(world -> world.getRegion() == net.runelite.http.api.worlds.WorldRegion.UNITED_STATES_OF_AMERICA)
                .collect(Collectors.toList());
        return candidates.isEmpty() ? 0 : candidates.get(new Random().nextInt(candidates.size())).getId();
    }

    private void handleIdleState(boolean hasTask, String taskName) {
        traceDroAction("handleIdleState");
        if (unresolvedTaskName != null) {
            if (hasTask && taskName != null && taskName.equalsIgnoreCase(unresolvedTaskName)) {
                // Stay paused on the same unresolved task instead of retrying every tick or logging out.
                return;
            }
            unresolvedTaskName = null;
        }

        if (hasTask) {
            // Check block/skip lists before proceeding with an existing task
            if (shouldBlockTask(taskName)) {
                log.info("Existing task '{}' is on block list, transitioning to BLOCKING_TASK", taskName);
                DroSlayerPlugin.setState(DroSlayerState.BLOCKING_TASK);
            } else if (isTaskOnBlockList(taskName)) {
                log.warn("Existing task '{}' is on block list but cannot afford to block, checking skip", taskName);
                if (shouldSkipTask(taskName)) {
                    log.info("Falling back to skipping existing task '{}'", taskName);
                    DroSlayerPlugin.setState(DroSlayerState.SKIPPING_TASK);
                } else {
                    log.info("Cannot afford to block/skip task '{}', proceeding with task", taskName);
                    DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK);
                }
            } else if (shouldSkipTask(taskName)) {
                log.info("Existing task '{}' is on skip list, transitioning to SKIPPING_TASK", taskName);
                DroSlayerPlugin.setState(DroSlayerState.SKIPPING_TASK);
            } else {
                log.info("Task '{}' detected, transitioning to DETECTING_TASK", taskName);
                DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK);
            }
        } else if (config.getNewTask()) {
            log.info("No task, transitioning to GETTING_TASK");
            DroSlayerPlugin.setState(DroSlayerState.GETTING_TASK);
        }
    }

    private void handleGettingTaskState(boolean hasTask, String taskName) {
        traceDroAction("handleGettingTaskState");
        if (Rs2Widget.isWidgetVisible(SLAYER_REWARDS_GROUP_ID, 0)) {
            if (rewardsAttemptReady()) rewardsInterface.close();
            return;
        }
        // Handle dialogue if we're in one
        if (Rs2Dialogue.isInDialogue()) {
            handleSlayerDialogue();
            return;
        }

        // If we now have a task, check if we should block or skip it
        // Block takes priority over skip since it's permanent
        if (hasTask) {
            if (shouldBlockTask(taskName)) {
                // Can afford to block - go block it
                log.info("Task '{}' is on block list, transitioning to BLOCKING_TASK", taskName);
                DroSlayerPlugin.setState(DroSlayerState.BLOCKING_TASK);
            } else if (isTaskOnBlockList(taskName)) {
                // Task is on block list but can't afford to block - try to skip instead
                log.warn("Task '{}' is on block list but cannot afford to block, checking skip option", taskName);
                if (shouldSkipTask(taskName)) {
                    log.info("Falling back to skipping task '{}'", taskName);
                    DroSlayerPlugin.setState(DroSlayerState.SKIPPING_TASK);
                } else if (isTaskOnSkipList(taskName)) {
                    // Also on skip list but can't afford that either
                    log.error("Task '{}' cannot be blocked or skipped - insufficient points!", taskName);
                    terminalStopMessage = Microbot.supplyMessage("Slayer points");
                    stopAndLogout();
                } else {
                    // Can't block, not on skip list - just do the task
                    log.info("Cannot afford to block task '{}', proceeding with task", taskName);
                    DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK);
                }
            } else if (shouldSkipTask(taskName)) {
                log.info("Task '{}' is on skip list, transitioning to SKIPPING_TASK", taskName);
                DroSlayerPlugin.setState(DroSlayerState.SKIPPING_TASK);
            } else if (isTaskOnSkipList(taskName)) {
                // Task is on skip list but we can't afford to skip (insufficient points)
                log.error("Task '{}' is on skip list but cannot skip - insufficient points!", taskName);
                terminalStopMessage = Microbot.supplyMessage("Slayer points");
                stopAndLogout();
            } else {
                log.info("Task '{}' received, transitioning to DETECTING_TASK", taskName);
                DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK);
            }
            return;
        }

        DroSlayerMaster master = config.slayerMaster();
        WorldPoint masterLocation = master.getLocation();
        DroSlayerPlugin.setCurrentLocation(master.getName());

        if (wildernessMasterBeforeRestore && handleEmergencyLowHpEscape("getting next Wilderness task")) {
            stateAfterFerox = DroSlayerState.GETTING_TASK;
            return;
        }

        // Check if we're close to the master
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return;
        }

        // A completed dungeon task may leave us on a disconnected underground coordinate map.
        // First preference is a direct inventory jewellery teleport to a bank-adjacent surface tile;
        // only if no usable item exists do we fall back to the task-specific hard-coded cave exit.
        if (handlePostTaskEscapeToSurface(playerLocation)) {
            return;
        }

        if (handleDuradelAccess(master, "getting task")) {
            return;
        }

        // Refresh after staged Duradel handling in case the ladder transition completed.
        playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return;
        }
        int distance = playerLocation.distanceTo(masterLocation);

        if (distance <= 5) {
            if (DroSlayerMasterInteraction.request(master, "Assignment")) {
                log.info("Requesting task from {}", master.getName());
                sleepUntil(Rs2Dialogue::isInDialogue, 3000);
            }
        } else {
            // Poll the active Efficient Walker route every tick; the bridge submits only once.
            log.debug("Traveling to slayer master: {}", master.getName());
            DroSlayerRuntime.walkFull(config, masterLocation, 5);
            handleRouteFailureBudget("slayer master");
        }
    }

    /**
     * When a task ends inside a dungeon, prefer a direct inventory teleport to a bank-adjacent
     * surface tile before handing the new-master route to Efficient Walker.  This deliberately
     * does not call Rs2Walker, minigame teleports, camera code, or the Efficient Walker planner.
     *
     * Returns true when this tick is consumed by escape handling.
     */
    private boolean handlePostTaskEscapeToSurface(WorldPoint playerLocation) {
        traceDroAction("handlePostTaskEscapeToSurface");
        if (playerLocation == null) return true;
        if (wildernessMasterBeforeRestore) return false;
        if (isLikelyDungeonInterior(playerLocation) && hasBankEscapeTeleportInInventory()) {
            beginFeroxRecovery(DroSlayerState.GETTING_TASK, "post-task dungeon escape");
            return true;
        }
        return false;
    }

    private boolean isLikelyDungeonInterior(WorldPoint point) {
        return point != null && point.getY() >= DUNGEON_INTERIOR_Y;
    }

    /**
     * Emergency combat escape.  The combat loadout already carries one supported
     * bank-adjacent teleport when available; use it before another combat/eating tick
     * once HP is critically low.  POH restoration remains the normal non-critical path.
     */
    private boolean handleEmergencyLowHpEscape(String context) {
        traceDroAction("handleEmergencyLowHpEscape");
        double healthPercent = Rs2Player.getHealthPercentage();
        if (isWildernessSlayer() && !Rs2Pvp.isInWilderness()) return false;
        List<Rs2ItemModel> food = usableFood();
        boolean noFoodLeft = food == null || food.isEmpty();
        boolean criticalHp = healthPercent <= EMERGENCY_ESCAPE_HP_PERCENT;

        if ((!noFoodLeft && !criticalHp) || !hasBankEscapeTeleportInInventory()) {
            return false;
        }

        beginFeroxRecovery(DroSlayerState.BANKING, "emergency escape: " + context + "; noFood=" + noFoodLeft + "; criticalHp=" + criticalHp);
        return true;
    }

    private void beginFeroxRecovery(DroSlayerState next, String reason) {
        holdDepartureProtection();
        log.info("Ferox recovery requested: reason={} fromState={} resumeState={} {}",
                reason, DroSlayerPlugin.getState(), next, recoveryResourceSummary());
        DroSlayerRuntime.cancelWalk();
        DroSlayerRuntime.clearRouteFailures();
        stateAfterFerox = next;
        feroxRestored = false;
        baseBankLoadComplete = false;
        if (!templeDepartureComplete && templeDeparture == TempleDeparture.NONE && atElderChaosTemple()) {
            templeDeparture = TempleDeparture.PICKUP;
            templeHopWorld = 0;
            templeHopAttempts = 0;
            templeHopAttemptAt = 0;
        }
        DroSlayerPlugin.setState(DroSlayerState.RESTORING_AT_FEROX);
    }

    private boolean atElderChaosTemple() {
        WorldPoint here = Rs2Player.getWorldLocation();
        return isWildernessSlayer() && here != null && here.getPlane() == 0
                && here.getX() >= 3210 && here.getX() <= 3260
                && here.getY() >= 3600 && here.getY() <= 3640;
    }

    /** Pickup, southward retreat, observed combat clearance, then hop before teleport routing. */
    private boolean handleTempleDeparture() {
        traceDroAction("handleTempleDeparture");
        if (templeDeparture == TempleDeparture.NONE) return false;
        if (templeDeparture == TempleDeparture.HOP && !closeBankBeforeHop(() ->
                DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                        (java.util.function.BooleanSupplier) Rs2Bank::closeBank))) return true;
        setAutoRetaliateHumanized(false);
        maintainWildernessEscapeSurvival();
        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null) return true;
        if (templeDeparture == TempleDeparture.PICKUP) {
            if (cannonPlaced && !handleCannonPickup()) return true;
            if (cannonPlaced && findOwnedCannon(50) != null) {
                Microbot.status = "Chaos Temple departure: cannon pickup needs attention";
                return true;
            }
            cannonPlaced = false;
            cannonFired = false;
            DroSlayerRuntime.cancelWalk();
            // Retreat beyond the southern edge of the entire temple area (y=3600),
            // leaving a 24-tile margin even when pickup began near the northern end.
            templeSouthTarget = new WorldPoint(here.getX(), 3576, 0);
            templeDeparture = TempleDeparture.RUN_SOUTH;
        }
        if (templeDeparture == TempleDeparture.RUN_SOUTH) {
            if (here.getY() > templeSouthTarget.getY() + 2) {
                if (!Rs2Player.isRunEnabled() && Rs2Player.getRunEnergy() > 0)
                    Rs2Player.toggleRunEnergy(true);
                Microbot.status = "Leaving Elder Chaos druids: running south";
                DroSlayerRuntime.walkAdaptive(config, templeSouthTarget, 2, "Chaos Temple south retreat");
                return true;
            }
            DroSlayerRuntime.cancelWalk();
            templeDeparture = TempleDeparture.HOP;
        }
        if (templeHopWorld > 0 && currentWorld() == templeHopWorld) {
            templeDeparture = TempleDeparture.NONE;
            templeDepartureComplete = true;
            templeSouthTarget = null;
            return false;
        }
        Microbot.status = "Leaving Elder Chaos druids: waiting to hop out of combat";
        long now = System.currentTimeMillis();
        if (Rs2Player.isInCombat() || isBeingAttacked() || Rs2Player.isMoving()) {
            return true;
        }
        if (now - templeHopAttemptAt < DroSlayerHopPolicy.RETRY_GAP_MS) return true;
        if (templeHopAttempts >= config.maxRecoveryAttempts()) {
            Microbot.status = "Chaos Temple departure: world hop needs attention";
            return true;
        }
        if (templeHopWorld == 0) templeHopWorld = safeWildernessHopWorld();
        templeHopAttemptAt = now;
        if (templeHopWorld <= 0) return true;
        templeHopAttempts++;
        DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                (java.util.function.BooleanSupplier) () -> Microbot.hopToWorld(templeHopWorld));
        return true;
    }

    private String recoveryResourceSummary() {
        traceDroAction("recoveryResourceSummary");
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            int hp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
            int maxHp = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
            int prayer = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
            int maxPrayer = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);
            int run = Rs2Player.getRunEnergy();
            return String.format(java.util.Locale.ROOT,
                    "hp=%d/%d (%.1f%%) prayer=%d/%d (%.1f%%) run=%d%% lowHp=%s lowPrayer=%s lowRun=%s",
                    hp, maxHp, 100.0 * hp / Math.max(1, maxHp),
                    prayer, maxPrayer, 100.0 * prayer / Math.max(1, maxPrayer), run,
                    hp * 100L <= maxHp * 70L, prayer * 100L <= maxPrayer * 70L, run <= 70);
        }).orElse("resource snapshot unavailable");
    }

    private boolean atFerox() {
        WorldPoint here = Rs2Player.getWorldLocation();
        return here != null && here.getPlane() == 0 && here.getX() >= 3123
                && here.getX() <= 3158 && here.getY() >= 3617 && here.getY() <= 3646;
    }

    private boolean needsFeroxRecoveryBeforeDeparture() {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                DroSlayerRecoveryPolicy.needsRecovery(
                        Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS),
                        Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS),
                        Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER),
                        Microbot.getClient().getRealSkillLevel(Skill.PRAYER),
                        Rs2Player.getRunEnergy())).orElse(true);
    }

    private boolean isFullyRestored() {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) >= Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS)
                && Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) >= Microbot.getClient().getRealSkillLevel(Skill.PRAYER))
                .orElse(false) && Rs2Player.getRunEnergy() >= 99;
    }

    private boolean canUseFeroxRing() {
        if (duelingChargesCarried() <= 0 || Rs2Player.isTeleBlocked()) return false;
        if (!Rs2Pvp.isInWilderness()) return true;
        WorldPoint location = Rs2Player.getWorldLocation();
        if (location == null) return false;
        int level = Rs2Pvp.getWildernessLevelFrom(location);
        return level > 0 && level <= 20;
    }

    private void handleFeroxRecovery() {
        traceDroAction("handleFeroxRecovery");
        if (handleTempleDeparture()) return;
        boolean directFeroxRing = canUseFeroxRing();
        if (isWildernessSlayer() && !atFerox() && !directFeroxRing) {
            // Walker handles exits where the ring cannot teleport. At level 20 or below,
            // use a charged ring directly unless teleblocked.
            handleEating();
            handlePotions();
            if (!DroSlayerRuntime.walkFull(config, FEROX_POOL, 6)) handleRouteFailureBudget("Wilderness Ferox recovery");
            return;
        }
        if (!atFerox()) {
            // Never resume combat or submit a dungeon walking route after an interrupted teleport.
            DroSlayerRuntime.cancelWalk();
            long now = System.currentTimeMillis();
            if (now - lastEmergencyEscapeAttemptAt < EMERGENCY_ESCAPE_RETRY_MS) return;
            lastEmergencyEscapeAttemptAt = now;
            if (Rs2Bank.isOpen()) {
                DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                        (Runnable) () -> Rs2Bank.closeBank());
                return;
            }
            if (!directFeroxRing && !hasBankEscapeTeleportInInventory()) {
                Microbot.status = "Restocking missing escape ring before recovery";
                DroSlayerPlugin.setState(DroSlayerState.BANKING);
                return;
            }
            tryInventoryBankEscapeTeleport();
            return;
        }
        deactivateProfilePrayer();
        boolean restored = isFullyRestored();
        if (!restored) {
            if (Rs2Bank.isOpen()) {
                DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                        (Runnable) () -> Rs2Bank.closeBank());
                return;
            }
            if (Rs2Player.getWorldLocation().distanceTo(FEROX_POOL) > 6) {
                DroSlayerRuntime.walkFull(config, FEROX_POOL, 1);
                return;
            }
            var pool = Microbot.getRs2TileObjectCache().query().withName("Pool of Refreshment").nearestOnClientThread();
            if (pool != null && pool.isReachable()) {
                long now = System.currentTimeMillis();
                if (now - lastFeroxPoolClickAt < 3600L) return;
                DroSlayerRuntime.cancelWalk();
                lastFeroxPoolClickAt = now;
                DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> pool.click("Drink"));
            } else {
                DroSlayerRuntime.walkFull(config, FEROX_POOL, 1);
            }
            return;
        }
        feroxRestored = true;
        log.info("Ferox recovery complete: resumeState={} {}", stateAfterFerox, recoveryResourceSummary());
        if (stateAfterFerox == DroSlayerState.GETTING_TASK || stateAfterFerox == DroSlayerState.IDLE) {
            resetTravelData();
            initialSetupDone = false;
        }
        DroSlayerPlugin.setState(stateAfterFerox);
    }

    /**
     * Direct inventory-only escape order:
     *   Ring of dueling -> Ferox Enclave
     *
     * Jewellery may expose the destination directly or behind a Rub/Teleport/Invoke dialogue;
     * both forms are handled.  A successful click owns this tick even while the teleport animates.
     */
    private boolean tryInventoryBankEscapeTeleport() {
        DroSlayerRuntime.cancelWalk();
        if (Rs2Equipment.isWearing("Ring of dueling", false)) {
            if (!DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, true,
                    (java.util.function.BooleanSupplier) () -> Rs2Equipment.interact("Ring of dueling", "Ferox Enclave"))) return false;
            return sleepUntil(this::atFerox, 10000);
        }
        return tryInventoryJewelleryTeleport("Ring of dueling", "Ferox Enclave", FEROX_LANDING);
    }

    private boolean tryInventoryJewelleryTeleport(String itemName, String destination, WorldPoint landing) {
        Rs2ItemModel item = Rs2Inventory.get(candidate ->
                candidate != null && candidate.getName() != null && candidate.getName().startsWith(itemName));
        if (item == null) return false;

        WorldPoint before = Rs2Player.getWorldLocation();
        String action = item.getAction(destination.toLowerCase(java.util.Locale.ROOT));
        if (action == null && item.getIndexOfSubAction(destination) != null) {
            action = destination;
        }
        boolean needsDialogue = false;

        if (action == null) {
            action = item.getActionFromList(Arrays.asList("rub", "teleport", "invoke"));
            needsDialogue = action != null;
        }
        if (action == null) return false;

        final String selectedAction = action;
        boolean clicked = DroSlayerRuntime.performAction(
                BaseProfileDro.ActionPhase.SETUP,
                true,
                (java.util.function.BooleanSupplier) () -> Rs2Inventory.interact(item, selectedAction));
        if (!clicked) return false;

        if (needsDialogue) {
            if (!Rs2Dialogue.sleepUntilSelectAnOption()) {
                log.warn("{} did not open its destination dialogue", item.getName());
                return false;
            }
            if (!DroSlayerRuntime.performAction(
                    BaseProfileDro.ActionPhase.SETUP, true,
                    (java.util.function.BooleanSupplier) () -> Rs2Dialogue.clickOption(destination))) {
                log.warn("Could not select '{}' from {}", destination, item.getName());
                return false;
            }
        }

        log.info("Post-task escape: {} -> {}", item.getName(), destination);
        boolean landed = sleepUntil(() -> {
            WorldPoint now = Rs2Player.getWorldLocation();
            if (now == null) return false;
            if (atFerox()) return true;
            if (landing != null && now.getPlane() == landing.getPlane() && now.distanceTo2D(landing) <= 12) return true;
            return false;
        }, 10000);

        if (landed) {
            DroSlayerRuntime.clearRouteFailures();
            postTaskEscapeAttempted = false;
        } else {
            log.warn("Inventory escape teleport did not leave the dungeon; local exit fallback remains available");
        }
        return landed;
    }

    /**
     * Special staged access for Duradel. Returns true when this tick was consumed by
     * Duradel travel/ladder handling; returns false once we are close enough upstairs
     * for the caller to interact with Duradel normally.
     */
    private boolean handleDuradelAccess(DroSlayerMaster master, String context) {
        traceDroAction("handleDuradelAccess");
        if (master != DroSlayerMaster.DURADEL) return false;
        boolean arrived = DroSlayerRuntime.walkFull(config, DURADEL_UPSTAIRS_APPROACH, 2);
        if (!arrived) handleRouteFailureBudget(context + " Duradel");
        return !arrived;
    }

    /**
     * Checks if the boss task amount input dialogue is open.
     * This is specifically for when selecting how many boss kills for a boss slayer task.
     * The dialogue asks "How many would you like to slay?" or "How many would you like to hunt?" with a number input.
     */
    private void handleSlayerDialogue() {
        traceDroAction("handleSlayerDialogue");
        if (Rs2Dialogue.hasContinue()) {
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, (Runnable) Rs2Dialogue::clickContinue);
            return;
        }
        String assignedTask = currentTaskName();
        if (shouldSkipTask(assignedTask) && !shouldBlockTask(assignedTask)
                && Rs2Dialogue.hasDialogueOption("skip for now")) {
            log.info("Skipping newly assigned '{}' through the 30-point dialogue option", assignedTask);
            if (DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                    (java.util.function.BooleanSupplier) () -> Rs2Dialogue.clickOption("skip for now", false))) {
                taskBeingSkipped = assignedTask;
                skipAttemptCounter = 0;
                DroSlayerPlugin.setState(DroSlayerState.SKIPPING_TASK);
            }
            return; // Do not accept the task if the input action was deferred.
        }
        for (String option : new String[]{"I need another assignment", "assignment", "easier", "Okay", "great"}) {
            if (Rs2Dialogue.hasDialogueOption(option)) {
                DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                        () -> { Rs2Dialogue.clickOption(option, false); });
                return;
            }
        }
        if (Rs2Slayer.hasSlayerTask() && Rs2Dialogue.isInDialogue()) {
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                    () -> { Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE); });
        }
    }

    private boolean isBossAmountInputOpen() {
        try {
            // Check for the specific chatbox input widget that appears for number entry
            // Widget 162, 42 is the chatbox input title
            String chatText = Rs2Widget.getChildWidgetText(162, 42);
            if (chatText == null) {
                return false;
            }

            // Only trigger for specific boss task amount prompts
            // The prompt varies: "How many would you like to slay?" or "How many would you like to hunt?"
            // NOT for general "Enter amount" or dialogue that just mentions "boss"
            String lowerText = chatText.toLowerCase();
            return lowerText.contains("how many would you like to slay") ||
                   lowerText.contains("how many would you like to hunt");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Handles the boss task amount selection by entering a value in the valid range
     * Boss tasks typically allow selecting between 3-55, we pick a low number to complete quickly
     */
    private void handleBossAmountInput() {
        traceDroAction("handleBossAmountInput");
        log.info("Boss task amount input detected, entering minimum amount");
        // Enter the minimum amount (3 is typical minimum for boss tasks)
        // We use the minimum so the task is quick to skip
        DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Keyboard.typeString("3"); });
        sleep(200, 400);
        DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Keyboard.keyPress(KeyEvent.VK_ENTER); });
        sleep(600, 1000);
        log.info("Boss task amount entered, task will be assigned then skipped");
    }

    private void handleSkippingTaskState() {
        traceDroAction("handleSkippingTaskState");
        // Check if we still have a task (skip might have completed)
        String currentTask = currentTaskName();
        if (!Rs2Slayer.hasSlayerTask()) {
            log.info("Task '{}' skipped successfully, transitioning to GETTING_TASK", taskBeingSkipped);
            resetSkipState();
            DroSlayerPlugin.setState(DroSlayerState.GETTING_TASK);
            return;
        }

        // Check if we got a different task (skip worked and we auto-got new task)
        if (!taskBeingSkipped.isEmpty() && !taskBeingSkipped.equalsIgnoreCase(currentTask)) {
            log.info("Task changed from '{}' to '{}' - skip successful", taskBeingSkipped, currentTask);
            resetSkipState();
            // Check if new task should also be skipped
            if (shouldSkipTask(currentTask)) {
                log.info("New task '{}' is also on skip list", currentTask);
                taskBeingSkipped = currentTask;
            } else {
                DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK);
            }
            return;
        }

        // Track which task we're trying to skip
        if (taskBeingSkipped.isEmpty()) {
            taskBeingSkipped = currentTask;
            log.info("Starting skip process for task: {}", taskBeingSkipped);
        }

        // Check if we've exceeded max attempts
        if (skipAttemptCounter >= MAX_SKIP_ATTEMPTS) {
            log.warn("Failed to skip task after {} attempts, proceeding with task instead", MAX_SKIP_ATTEMPTS);
            resetSkipState();
            DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK);
            return;
        }

        // Check if we have enough points to skip
        int slayerPoints = DroSlayerPlugin.getSlayerPoints();
        if (slayerPoints < SKIP_TASK_COST || slayerPoints < config.minPointsToSkip()) {
            log.error("CANNOT SKIP TASK - Not enough slayer points! Have: {}, Need: {}, Min reserve: {}",
                    slayerPoints, SKIP_TASK_COST, config.minPointsToSkip());
            log.error("Task '{}' is on skip list but cannot be skipped. Stopping plugin and logging out.", taskBeingSkipped);
            terminalStopMessage = Microbot.supplyMessage("Slayer points");
            resetSkipState();
            stopAndLogout();
            return;
        }

        // The retry/point guards apply while the rewards screen is open, too.
        if (Rs2Dialogue.isInDialogue() || Rs2Widget.isWidgetVisible(SLAYER_REWARDS_GROUP_ID, 0)) {
            if (!rewardsAttemptReady()) return;
            skipAttemptCounter++;
            handleSkipDialogue();
            return;
        }

        DroSlayerMaster master = config.slayerMaster();
        WorldPoint masterLocation = master.getLocation();
        DroSlayerPlugin.setCurrentLocation(master.getName() + " (Skipping)");

        // Check if we're close to the master
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return;
        }

        if (handleDuradelAccess(master, "skipping task")) {
            return;
        }

        playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return;
        }
        int distance = playerLocation.distanceTo(masterLocation);

        if (distance <= 5) {
            // We're at the master, interact to open rewards
            var masterNpc = Microbot.getRs2NpcCache().query().withName(master.getName()).nearestOnClientThread();
            if (masterNpc != null) {
                if (DroSlayerRuntime.performAction(
                        BaseProfileDro.ActionPhase.SETUP, false,
                        (java.util.function.BooleanSupplier) () -> masterNpc.click("Rewards"))) {
                    log.info("Opening rewards menu to skip task (attempt {})", skipAttemptCounter + 1);
                    sleepUntil(() -> Rs2Dialogue.isInDialogue() || Rs2Widget.isWidgetVisible(SLAYER_REWARDS_GROUP_ID, 0), 3000);
                }
            } else {
                log.warn("Could not find slayer master: {}", master.getName());
                skipAttemptCounter++;
            }
        } else {
            DroSlayerRuntime.walkFull(config, masterLocation, 5);
            handleRouteFailureBudget("skip master");
        }
    }

    private void resetSkipState() {
        rewardsInterface.reset();
        rewardsAttemptAt = 0L;
        skipAttemptCounter = 0;
        taskBeingSkipped = "";
    }

    // ==================== POH Methods ====================

    private void handleRestoringAtPohState() {
        traceDroAction("handleRestoringAtPohState");
        // Direct POH/spellbook actions own travel now; release any Efficient Walker policy first.
        DroSlayerRuntime.cancelWalk();
        DroSlayerPlugin.setCurrentLocation("POH");

        // Check if we're in the house (house portal exists)
        boolean inHouse = isInPoh();

        if (!inHouse) {
            // Need to teleport to house
            if (!pohTeleportAttempted) {
                if (teleportToHouse()) {
                    pohTeleportAttempted = true;
                    sleepUntil(this::isInPoh, 5000);
                } else {
                    log.warn("Failed to teleport to house, falling back to banking");
                    resetPohState();
                    DroSlayerPlugin.setState(stateAfterPoh);
                }
            } else {
                // Teleport was attempted but we're not in house - wait a bit more
                sleep(600, 1000);
                if (!isInPoh()) {
                    log.warn("Still not in house after teleport, falling back");
                    resetPohState();
                    DroSlayerPlugin.setState(stateAfterPoh);
                }
            }
            return;
        }

        // We're in the house - find and use the pool
        if (!Rs2Player.isFullHealth() || !isFullPrayer() || Rs2Player.getRunEnergy() < 100) {
            if (useRejuvenationPool()) {
                log.info("Using rejuvenation pool");
                sleepUntil(() -> Rs2Player.isFullHealth() && isFullPrayer(), 5000);
            } else {
                log.warn("Could not find rejuvenation pool in house");
            }
        }

        // Check if we're fully restored
        if (Rs2Player.isFullHealth() && isFullPrayer()) {
            log.info("Fully restored at POH, leaving house");
            leaveHouse();
            sleepUntil(() -> !isInPoh(), 3000);
            resetPohState();

            // Transition to next state
            log.info("Transitioning to {}", stateAfterPoh);
            DroSlayerPlugin.setState(stateAfterPoh);
        }
    }

    private boolean isInPoh() {
        return Microbot.getRs2TileObjectCache().query().withId(HOUSE_PORTAL_ID).nearestOnClientThread() != null;
    }

    private boolean isFullPrayer() {
        int currentPrayer = Rs2Player.getBoostedSkillLevel(Skill.PRAYER);
        int maxPrayer = Rs2Player.getRealSkillLevel(Skill.PRAYER);
        return currentPrayer >= maxPrayer;
    }

    private boolean teleportToHouse() {
        traceDroAction("teleportToHouse");
        PohTeleportMethod method = config.pohTeleportMethod();
        log.info("Teleporting to house using: {}", method.getDisplayName());

        switch (method) {
            case SPELL:
                if (Rs2Magic.canCast(MagicAction.TELEPORT_TO_HOUSE)) {
                    DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> {
                        Rs2Magic.cast(MagicAction.TELEPORT_TO_HOUSE);
                    });
                    return true;
                }
                log.warn("Cannot cast Teleport to House spell");
                break;

            case HOUSE_TAB:
                if (Rs2Inventory.hasItem("Teleport to house")) {
                    DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> {
                        Rs2Inventory.interact("Teleport to house", "Break");
                    });
                    return true;
                }
                log.warn("No house tabs in inventory");
                break;

            case CONSTRUCTION_CAPE:
                if (Rs2Inventory.hasItem("Construct. cape") || Rs2Inventory.hasItem("Construct. cape(t)")) {
                    String capeName = Rs2Inventory.hasItem("Construct. cape(t)") ? "Construct. cape(t)" : "Construct. cape";
                    DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> {
                        Rs2Inventory.interact(capeName, "Tele to POH");
                    });
                    return true;
                }
                // Also check if worn
                // For now, just check inventory
                log.warn("No construction cape in inventory");
                break;

            case MAX_CAPE:
                if (Rs2Inventory.hasItem("Max cape")) {
                    DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> {
                        Rs2Inventory.interact("Max cape", "Tele to POH");
                    });
                    return true;
                }
                log.warn("No max cape in inventory");
                break;
        }

        // Fallback to house tab if primary method fails
        if (method != PohTeleportMethod.HOUSE_TAB && Rs2Inventory.hasItem("Teleport to house")) {
            log.info("Falling back to house tab");
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> {
                Rs2Inventory.interact("Teleport to house", "Break");
            });
            return true;
        }

        return false;
    }

    private boolean useRejuvenationPool() {
        for (int poolId : REJUVENATION_POOL_IDS) {
            if (Microbot.getRs2TileObjectCache().query().withId(poolId).nearestOnClientThread() != null) {
                return DroSlayerRuntime.performAction(
                        BaseProfileDro.ActionPhase.SETUP, false,
                        (java.util.function.BooleanSupplier) () -> Microbot.getRs2TileObjectCache().query().interact(poolId, "Drink"));
            }
        }
        return false;
    }

    private void leaveHouse() {
        // Try to use portal nexus to teleport to Grand Exchange (near bank)
        if (teleportViaPortalNexus()) {
            log.info("Leaving house via portal nexus to Grand Exchange");
            return;
        }

        // Try mounted glory to Edgeville (near GE)
        if (teleportViaMountedGlory()) {
            log.info("Leaving house via mounted glory to Edgeville");
            return;
        }

        // Try mounted ring of wealth to GE
        if (teleportViaMountedWealth()) {
            log.info("Leaving house via mounted ring of wealth to Grand Exchange");
            return;
        }

        // Fallback to house portal exit
        if (DroSlayerRuntime.performAction(
                BaseProfileDro.ActionPhase.SETUP, false,
                (java.util.function.BooleanSupplier) () -> Microbot.getRs2TileObjectCache().query().interact(HOUSE_PORTAL_ID, "Enter"))) {
            log.info("Leaving house via portal (no teleport options found)");
        }
    }

    /**
     * Attempts to teleport to the Grand Exchange via the portal nexus
     * @return true if interaction was successful, false if nexus not found
     */
    private boolean teleportViaPortalNexus() {
        traceDroAction("teleportViaPortalNexus");
        // Try to find any tier of portal nexus
        for (int nexusId : PORTAL_NEXUS_IDS) {
            if (Microbot.getRs2TileObjectCache().query().withId(nexusId).nearestOnClientThread() != null) {
                if (DroSlayerRuntime.performAction(
                        BaseProfileDro.ActionPhase.SETUP, false,
                        (java.util.function.BooleanSupplier) () -> Microbot.getRs2TileObjectCache().query().interact(nexusId, "Grand Exchange"))) {
                    return true;
                }
                if (DroSlayerRuntime.performAction(
                        BaseProfileDro.ActionPhase.SETUP, false,
                        (java.util.function.BooleanSupplier) () -> Microbot.getRs2TileObjectCache().query().interact(nexusId, "Varrock Grand Exchange"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Attempts to teleport via mounted glory to Edgeville (close to GE)
     * @return true if interaction was successful, false if mounted glory not found
     */
    private boolean teleportViaMountedGlory() {
        traceDroAction("teleportViaMountedGlory");
        for (int gloryId : MOUNTED_GLORY_IDS) {
            if (Microbot.getRs2TileObjectCache().query().withId(gloryId).nearestOnClientThread() != null) {
                if (DroSlayerRuntime.performAction(
                        BaseProfileDro.ActionPhase.SETUP, false,
                        (java.util.function.BooleanSupplier) () -> Microbot.getRs2TileObjectCache().query().interact(gloryId, "Edgeville"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Attempts to teleport via mounted ring of wealth to Grand Exchange
     * @return true if interaction was successful, false if mounted wealth not found
     */
    private boolean teleportViaMountedWealth() {
        traceDroAction("teleportViaMountedWealth");
        if (Microbot.getRs2TileObjectCache().query().withId(MOUNTED_WEALTH_ID).nearestOnClientThread() != null) {
            if (DroSlayerRuntime.performAction(
                    BaseProfileDro.ActionPhase.SETUP, false,
                    (java.util.function.BooleanSupplier) () -> Microbot.getRs2TileObjectCache().query().interact(MOUNTED_WEALTH_ID, "Grand Exchange"))) {
                return true;
            }
        }
        return false;
    }

    private void resetPohState() {
        pohTeleportAttempted = false;
    }

    /**
     * Checks if we should use POH for restoration based on current state
     */
    private boolean shouldUsePoh() {
        if (!config.usePohPool()) {
            return false;
        }

        // Check if we have a way to teleport
        PohTeleportMethod method = config.pohTeleportMethod();
        switch (method) {
            case SPELL:
                if (!Rs2Magic.canCast(MagicAction.TELEPORT_TO_HOUSE)) {
                    return Rs2Inventory.hasItem("Teleport to house"); // Fallback check
                }
                return true;
            case HOUSE_TAB:
                return Rs2Inventory.hasItem("Teleport to house");
            case CONSTRUCTION_CAPE:
                return Rs2Inventory.hasItem("Construct. cape") ||
                       Rs2Inventory.hasItem("Construct. cape(t)") ||
                       Rs2Inventory.hasItem("Teleport to house");
            case MAX_CAPE:
                return Rs2Inventory.hasItem("Max cape") ||
                       Rs2Inventory.hasItem("Teleport to house");
            default:
                return Rs2Inventory.hasItem("Teleport to house");
        }
    }

    /**
     * Checks if we need POH restoration based on HP/Prayer thresholds
     */
    private boolean needsPohRestoration() {
        if (!config.usePohPool() || !config.usePohBeforeBanking()) {
            return false;
        }

        int threshold = config.pohRestoreThreshold();
        if (threshold <= 0) {
            return false;
        }

        double healthPercent = Rs2Player.getHealthPercentage();
        return healthPercent < threshold && shouldUsePoh();
    }

    // ==================== End POH Methods ====================

    // Slayer rewards interface widget constants (group 426)
    // Slayer rewards interface (widget group 426)
    private static final int SLAYER_REWARDS_GROUP_ID = 426;

    private boolean rewardsAttemptReady() {
        long now = System.currentTimeMillis();
        if (now < rewardsAttemptAt) return false;
        rewardsAttemptAt = now + 1_500L;
        return true;
    }

    private void handleSkipDialogue() {
        traceDroAction("handleSkipDialogue");
        // Check if slayer rewards interface is open (widget-based, not dialogue)
        if (Rs2Widget.isWidgetVisible(SLAYER_REWARDS_GROUP_ID, 0)) {
            handleSlayerRewardsInterface();
            return;
        }
        // Click through continue prompts first
        if (Rs2Dialogue.hasContinue()) {
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Dialogue.clickContinue(); });
            sleep(300, 600);
            return;
        }


        if (Rs2Dialogue.hasDialogueOption("skip for now")) {
            log.info("Choosing the 30-point skip-for-now dialogue option");
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                    (java.util.function.BooleanSupplier) () -> Rs2Dialogue.clickOption("skip for now", false));
            return;
        }

        // Step 1: Look for "Assignment" or "Assignments" option in main Rewards menu
        // This takes us to the task management submenu
        if (Rs2Dialogue.hasDialogueOption("Assignment")) {
            log.info("Clicking Assignment option");
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Dialogue.clickOption("Assignment"); });
            sleep(300, 600);
            return;
        }

        // Step 2: Look for task cancellation options
        // Different masters/interfaces may use different wording
        if (Rs2Dialogue.hasDialogueOption("Cancel current task")) {
            log.info("Clicking Cancel current task option (costs 30 points)");
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Dialogue.clickOption("Cancel current task"); });
            sleep(300, 600);
            return;
        }

        // Alternative wording - some interfaces say "Skip task"
        if (Rs2Dialogue.hasDialogueOption("Skip task")) {
            log.info("Clicking Skip task option");
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Dialogue.clickOption("Skip task"); });
            sleep(300, 600);
            return;
        }

        // Partial match for "cancel" in case of different wording
        if (Rs2Dialogue.hasDialogueOption("cancel")) {
            log.info("Clicking cancel option (partial match)");
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Dialogue.clickOption("cancel", false); });
            sleep(300, 600);
            return;
        }

        // Step 3: Confirm the skip
        if (Rs2Dialogue.hasDialogueOption("Yes")) {
            log.info("Confirming task skip");
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Dialogue.clickOption("Yes"); });
            sleep(600, 1000);
            return;
        }

        // If we see a "No" option but no "Yes", we might be in wrong menu - go back
        if (Rs2Dialogue.hasDialogueOption("No") && !Rs2Dialogue.hasDialogueOption("Yes")) {
            log.warn("Found No but not Yes - might be in wrong dialogue state");
        }
    }

    /**
     * Handles the slayer rewards widget interface for skipping/cancelling a task.
     * Flow: Task tab -> Cancel -> Confirm
     */
    private void handleSlayerRewardsInterface() {
        traceDroAction("handleSlayerRewardsInterface");
        rewardsInterface.clickNext(false, currentTaskName());
    }

    /**
     * Checks if the given task name should be skipped based on config
     */
    private boolean shouldSkipTask(String taskName) {
        if (!config.enableAutoSkip() || taskName == null || taskName.isEmpty()) {
            return false;
        }

        String skipList = config.skipTaskList();
        if (skipList == null || skipList.isEmpty()) {
            return false;
        }

        // Check if we have enough points
        int slayerPoints = DroSlayerPlugin.getSlayerPoints();
        if (slayerPoints < SKIP_TASK_COST || slayerPoints < config.minPointsToSkip()) {
            return false;
        }

        // Parse skip list and check if task is in it
        String taskLower = taskName.toLowerCase().trim();
        return Arrays.stream(skipList.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .anyMatch(skipTask -> taskLower.contains(skipTask) || skipTask.contains(taskLower));
    }

    /**
     * Checks if a task is on the skip list (ignoring point requirements)
     * Used to determine if we should stop when we can't afford to skip
     */
    private boolean isTaskOnSkipList(String taskName) {
        if (!config.enableAutoSkip() || taskName == null || taskName.isEmpty()) {
            return false;
        }

        String skipList = config.skipTaskList();
        if (skipList == null || skipList.isEmpty()) {
            return false;
        }

        // Just check if task is in the skip list, don't check points
        String taskLower = taskName.toLowerCase().trim();
        return Arrays.stream(skipList.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .anyMatch(skipTask -> taskLower.contains(skipTask) || skipTask.contains(taskLower));
    }

    // ==================== Block Task Methods ====================

    private void handleBlockingTaskState() {
        traceDroAction("handleBlockingTaskState");
        // Check if we still have a task (block might have completed)
        String currentTask = currentTaskName();
        if (!Rs2Slayer.hasSlayerTask()) {
            log.info("Task '{}' blocked successfully, transitioning to GETTING_TASK", taskBeingBlocked);
            resetBlockState();
            DroSlayerPlugin.setState(DroSlayerState.GETTING_TASK);
            return;
        }

        // Check if we got a different task (block worked and we auto-got new task)
        if (!taskBeingBlocked.isEmpty() && !taskBeingBlocked.equalsIgnoreCase(currentTask)) {
            log.info("Task changed from '{}' to '{}' - block successful", taskBeingBlocked, currentTask);
            resetBlockState();
            // Check if new task should also be blocked or skipped
            if (shouldBlockTask(currentTask)) {
                log.info("New task '{}' is also on block list", currentTask);
                taskBeingBlocked = currentTask;
            } else if (shouldSkipTask(currentTask)) {
                log.info("New task '{}' is on skip list", currentTask);
                DroSlayerPlugin.setState(DroSlayerState.SKIPPING_TASK);
            } else {
                DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK);
            }
            return;
        }

        // Track which task we're trying to block
        if (taskBeingBlocked.isEmpty()) {
            taskBeingBlocked = currentTask;
            log.info("Starting block process for task: {}", taskBeingBlocked);
        }

        // Check if we've exceeded max attempts
        if (blockAttemptCounter >= MAX_BLOCK_ATTEMPTS) {
            log.warn("Failed to block task after {} attempts, trying to skip instead", MAX_BLOCK_ATTEMPTS);
            resetBlockState();
            // Fall back to skipping if we can
            if (shouldSkipTask(currentTask)) {
                DroSlayerPlugin.setState(DroSlayerState.SKIPPING_TASK);
            } else {
                DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK);
            }
            return;
        }

        // Check if we have enough points to block
        int slayerPoints = DroSlayerPlugin.getSlayerPoints();
        if (slayerPoints < BLOCK_TASK_COST || slayerPoints < config.minPointsToBlock()) {
            log.warn("Cannot block task - not enough points. Have: {}, Need: {}, Min reserve: {}",
                    slayerPoints, BLOCK_TASK_COST, config.minPointsToBlock());
            log.info("Checking if task can be skipped instead...");
            resetBlockState();
            // Try to skip instead if on skip list and can afford
            if (shouldSkipTask(currentTask)) {
                DroSlayerPlugin.setState(DroSlayerState.SKIPPING_TASK);
            } else if (isTaskOnSkipList(currentTask)) {
                // On skip list but can't afford skip either
                log.error("Task '{}' cannot be blocked or skipped - insufficient points!", currentTask);
                terminalStopMessage = Microbot.supplyMessage("Slayer points");
                stopAndLogout();
            } else {
                // Not on skip list, just do the task
                DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK);
            }
            return;
        }

        if (Rs2Dialogue.isInDialogue() || Rs2Widget.isWidgetVisible(SLAYER_REWARDS_GROUP_ID, 0)) {
            if (!rewardsAttemptReady()) return;
            blockAttemptCounter++;
            handleBlockDialogue();
            return;
        }

        DroSlayerMaster master = config.slayerMaster();
        WorldPoint masterLocation = master.getLocation();
        DroSlayerPlugin.setCurrentLocation(master.getName() + " (Blocking)");

        // Check if we're close to the master
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return;
        }

        if (handleDuradelAccess(master, "blocking task")) {
            return;
        }

        playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return;
        }
        int distance = playerLocation.distanceTo(masterLocation);

        if (distance <= 5) {
            // We're at the master, interact to open rewards
            var masterNpc = Microbot.getRs2NpcCache().query().withName(master.getName()).nearestOnClientThread();
            if (masterNpc != null) {
                if (DroSlayerRuntime.performAction(
                        BaseProfileDro.ActionPhase.SETUP, false,
                        (java.util.function.BooleanSupplier) () -> masterNpc.click("Rewards"))) {
                    log.info("Opening rewards menu to block task (attempt {})", blockAttemptCounter + 1);
                    sleepUntil(() -> Rs2Dialogue.isInDialogue() || Rs2Widget.isWidgetVisible(SLAYER_REWARDS_GROUP_ID, 0), 3000);
                }
            } else {
                log.warn("Could not find slayer master: {}", master.getName());
                blockAttemptCounter++;
            }
        } else {
            DroSlayerRuntime.walkFull(config, masterLocation, 5);
            handleRouteFailureBudget("block master");
        }
    }

    private void handleBlockDialogue() {
        traceDroAction("handleBlockDialogue");
        // Check if slayer rewards interface is open (widget-based, not dialogue)
        if (Rs2Widget.isWidgetVisible(SLAYER_REWARDS_GROUP_ID, 0)) {
            handleSlayerRewardsBlockInterface();
            return;
        }
        // Click through continue prompts first
        if (Rs2Dialogue.hasContinue()) {
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Dialogue.clickContinue(); });
            sleep(300, 600);
            return;
        }


        // Standard dialogue options (fallback for older interfaces)
        if (Rs2Dialogue.hasDialogueOption("Assignment")) {
            log.info("Clicking Assignment option");
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Dialogue.clickOption("Assignment"); });
            sleep(300, 600);
            return;
        }

        if (Rs2Dialogue.hasDialogueOption("Block current task")) {
            log.info("Clicking Block current task option (costs 100 points)");
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Dialogue.clickOption("Block current task"); });
            sleep(300, 600);
            return;
        }

        if (Rs2Dialogue.hasDialogueOption("Block task")) {
            log.info("Clicking Block task option");
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Dialogue.clickOption("Block task"); });
            sleep(300, 600);
            return;
        }

        if (Rs2Dialogue.hasDialogueOption("block")) {
            log.info("Clicking block option (partial match)");
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Dialogue.clickOption("block", false); });
            sleep(300, 600);
            return;
        }

        // Confirm the block
        if (Rs2Dialogue.hasDialogueOption("Yes")) {
            log.info("Confirming task block");
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Dialogue.clickOption("Yes"); });
            sleep(600, 1000);
            return;
        }
    }

    /**
     * Handles the slayer rewards widget interface for blocking a task.
     * Flow: Task tab -> Block -> Confirm
     */
    private void handleSlayerRewardsBlockInterface() {
        traceDroAction("handleSlayerRewardsBlockInterface");
        rewardsInterface.clickNext(true, currentTaskName());
    }

    private void resetBlockState() {
        rewardsInterface.reset();
        rewardsAttemptAt = 0L;
        blockAttemptCounter = 0;
        taskBeingBlocked = "";
    }

    /**
     * Checks if the given task name should be blocked based on config.
     * Block takes priority over skip since it's a permanent solution.
     */
    private boolean shouldBlockTask(String taskName) {
        if (!config.enableAutoBlock() || taskName == null || taskName.isEmpty()) {
            return false;
        }

        String blockList = config.blockTaskList();
        if (blockList == null || blockList.isEmpty()) {
            return false;
        }

        // Check if we have enough points to block
        int slayerPoints = DroSlayerPlugin.getSlayerPoints();
        if (slayerPoints < BLOCK_TASK_COST || slayerPoints < config.minPointsToBlock()) {
            return false;
        }

        // Parse block list and check if task is in it
        String taskLower = taskName.toLowerCase().trim();
        return Arrays.stream(blockList.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .anyMatch(blockTask -> taskLower.contains(blockTask) || blockTask.contains(taskLower));
    }

    /**
     * Checks if a task is on the block list (ignoring point requirements)
     * Used to determine if we should try to skip instead when we can't afford to block
     */
    private boolean isTaskOnBlockList(String taskName) {
        if (!config.enableAutoBlock() || taskName == null || taskName.isEmpty()) {
            return false;
        }

        String blockList = config.blockTaskList();
        if (blockList == null || blockList.isEmpty()) {
            return false;
        }

        // Just check if task is in the block list, don't check points
        String taskLower = taskName.toLowerCase().trim();
        return Arrays.stream(blockList.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .anyMatch(blockTask -> taskLower.contains(blockTask) || blockTask.contains(taskLower));
    }

    private void handleDetectingTaskState(boolean hasTask) {
        traceDroAction("handleDetectingTaskState");
        // Dismiss any open dialogue first (e.g., residual slayer master dialogue)
        if (Rs2Dialogue.isInDialogue()) {
            if (Rs2Dialogue.hasContinue()) {
                DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Dialogue.clickContinue(); });
                sleep(300, 600);
            } else {
                // Try pressing escape to close dialogue
                DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE); });
                sleep(300, 600);
            }
            return;
        }

        if (!hasTask) {
            log.info("No task found, returning to IDLE");
            DroSlayerPlugin.setState(DroSlayerState.IDLE);
            resetTravelData();
            return;
        }

        String taskName = currentTaskName();
        if (isWildernessSlayer() && !DroSlayerWildernessTaskReader.isKrystiliaAssignment()) {
            holdWildernessTask("Finish or cancel the current ordinary assignment before selecting Krystilia");
            return;
        }

        // Look up JSON profile first (preferred)
        activeJsonProfile = profileManager.findProfile(taskName);
        if (activeJsonProfile != null) {
            log.info("Found JSON profile for '{}': setup={}, prayer={}, cannon={}, antipoison={}, antivenom={}",
                    taskName, activeJsonProfile.getSetup(), activeJsonProfile.getPrayer(),
                    activeJsonProfile.isCannon(), activeJsonProfile.isAntipoison(), activeJsonProfile.isAntivenom());
        } else {
            log.warn("No JSON profile found for task '{}' - prayer/location settings may not apply. " +
                    "Add a profile to ~/.runelite/droslayer2-profiles.json or delete the file to regenerate defaults.",
                    taskName);
        }

        // Resolve the explicit location choice before applying its monster config.
        wildernessAssignment = isWildernessSlayer() ? DroSlayerWildernessTasks.forChaosDruidPreference(
                DroSlayerWildernessTasks.forAssignedTask(taskName), config.wildernessElderChaosDruids()) : null;
        if (isWildernessSlayer()) {
            if (wildernessAssignment == null || !wildernessAssignment.isSupported()) {
                unresolvedTaskName = taskName;
                String reason = wildernessAssignment == null ? "No Krystilia assignment entry" : wildernessAssignment.getUnsupportedReason();
                DroSlayerPlugin.setCurrentLocation("UNCONFIGURED: " + taskName);
                log.warn("Wilderness task paused: {} ({})", taskName, reason);
                Microbot.showMessage("Krystilia task '" + taskName + "' needs manual handling or skip/block: " + reason);
                DroSlayerPlugin.setState(DroSlayerState.IDLE);
                return;
            }
            // Ordinary-master location/variant preferences must never send a Krystilia task outside Wilderness.
            taskDestination = wildernessAssignment.getDestination();
            taskLocationName = wildernessAssignment.getLocationName();
            DroSlayerPlugin.setCurrentLocation(taskLocationName);
            String accessProblem = wildernessAccessProblem();
            if (accessProblem != null) {
                holdWildernessTask(accessProblem);
                return;
            }
        }

        if (isElderChaosDruidLocation()) {
            activeJsonProfile = profileManager.findProfile(currentTaskProfileName());
            log.info("Using monster config profile '{}'", currentTaskProfileName());
        }
        DroSlayerPrayer selectedPrayer = getActiveProtectionPrayer();
        if (selectedPrayer != null && selectedPrayer != DroSlayerPrayer.NONE) {
            DroSlayerPlugin.getFlickerScript().setActivePrayer(selectedPrayer);
        } else {
            DroSlayerPlugin.getFlickerScript().clearActiveProfile();
        }

        // Determine if we should use cannon for this task
        isUsingCannon = shouldUseCannon(taskName);
        if (isWildernessSlayer()) {
            cannonSpot = isUsingCannon ? wildernessAssignment.getCannonSpot() : null;
            if (isUsingCannon && cannonSpot == null) cannonSpot = taskDestination;
            if (isUsingCannon) {
                taskDestination = cannonSpot;
                log.info("Krystilia cannon/walking target={} source={}", cannonSpot, wildernessAssignment.getCannonSpotEvidence());
            }
        }

        // Get task location
        if (taskDestination == null) {
            // Priority 1: If using cannon and profile has cannonLocation, use that
            if (isUsingCannon && activeJsonProfile != null && activeJsonProfile.hasCannonLocation()) {
                DroSlayerLocation cannonLocation = DroSlayerLocation.fromName(activeJsonProfile.getCannonLocation());
                if (cannonLocation != null) {
                    taskDestination = cannonLocation.getWorldPoint();
                    cannonSpot = taskDestination; // Cannon will be placed here
                    taskLocationName = cannonLocation.getDisplayName();
                    DroSlayerPlugin.setCurrentLocation(taskLocationName + " (Cannon)");
                    log.info("Using profile cannon location for {}: {} at {}", taskName, taskLocationName, taskDestination);
                } else {
                    log.warn("Unknown profile cannon location '{}', falling back to auto-selection", activeJsonProfile.getCannonLocation());
                }
            }

            // Priority 2: If not using cannon and profile has location, use that
            if (taskDestination == null && !isUsingCannon && activeJsonProfile != null && activeJsonProfile.hasLocation()) {
                DroSlayerLocation profileLocation = DroSlayerLocation.fromName(activeJsonProfile.getLocation());
                if (profileLocation != null) {
                    if (profileLocation == DroSlayerLocation.ZANARIS
                            && taskName != null && taskName.toLowerCase(java.util.Locale.ROOT).contains("zygomite")) {
                        profileLocation = DroSlayerLocation.ZANARIS_ZYGOMITES;
                    }
                    taskDestination = profileLocation.getWorldPoint();
                    taskLocationName = profileLocation.getDisplayName();
                    DroSlayerPlugin.setCurrentLocation(taskLocationName);
                    log.info("Using profile location for {}: {} at {}", taskName, taskLocationName, taskDestination);
                } else {
                    log.warn("Unknown profile location '{}', falling back to auto-selection", activeJsonProfile.getLocation());
                }
            }

            // Priority 3: If using cannon but no cannonLocation, try CannonSpot enum
            if (taskDestination == null && isUsingCannon) {
                WorldPoint playerLocation = Rs2Player.getWorldLocation();
                cannonSpot = CannonSpot.getClosestSpot(taskName, playerLocation);
                if (cannonSpot != null) {
                    taskDestination = cannonSpot;
                    taskLocationName = "Cannon spot";
                    DroSlayerPlugin.setCurrentLocation(taskLocationName + " (Cannon)");
                    log.info("Cannon spot found for {}: {}", taskName, cannonSpot);
                } else {
                    log.warn("No cannon spot found for task: {}, using regular location", taskName);
                    isUsingCannon = false;
                }
            }

            // Priority 4: Fall back to Dro's task-category lookup.
            if (taskDestination == null) {
                MonsterLocation monsterLocation = Rs2Slayer.getSlayerTaskLocation(3, true);

                // Some newer tasks have a category-name mismatch or were added before the
                // slayer location index was updated. Resolve those through the same NPC data
                // using normalized category/NPC candidates before declaring the task unknown.
                if (monsterLocation == null) {
                    monsterLocation = resolveTaskLocationFromNpcData(taskName);
                }

                if (monsterLocation != null && monsterLocation.getBestClusterCenter() != null) {
                    taskDestination = monsterLocation.getBestClusterCenter();
                    taskLocationName = monsterLocation.getLocationName();
                    DroSlayerPlugin.setCurrentLocation(taskLocationName);
                    log.info("Task location found: {} at {}", taskLocationName, taskDestination);
                } else {
                    taskLocationResolutionFailures++;
                    log.warn("Could not find location for slayer task '{}' (attempt {}/{})",
                            taskName, taskLocationResolutionFailures, config.maxRecoveryAttempts());
                    if (taskLocationResolutionFailures >= config.maxRecoveryAttempts()) {
                        unresolvedTaskName = taskName;
                        DroSlayerPlugin.setCurrentLocation("UNCONFIGURED: " + taskName);
                        Microbot.showMessage("[Dro] Slayer 2 has no usable location for '" + taskName
                                + "'. The task is paused; add/override a profile location to continue.");
                        log.error("UNCONFIGURED TASK LOCATION: {}. Pausing this task instead of logging out.", taskName);
                        DroSlayerPlugin.setState(DroSlayerState.IDLE);
                    }
                    return;
                }
            }
        }

        taskLocationResolutionFailures = 0;

        // Check if we need to bank first. The selected Inventory Setup is the BASE loadout;
        // task-specific mandatory items are layered on top at the bank. On plugin restart inside
        // the current task cave/room, do NOT force a bank trip merely because initialSetupDone
        // reset to false. Only bank there when supplies or a real task requirement are missing.
        var detectedSetup = getActiveInventorySetup();
        if (isWildernessSlayer() && detectedSetup == null) {
            unresolvedTaskName = taskName;
            Microbot.status = isWildernessAviansieTask()
                    ? "Aviansies require a saved rangewildy setup with a ranged weapon"
                    : wildernessBurstSelected()
                    ? "Wilderness bursting requires a saved burstwildy setup"
                    : "Select an available Wilderness inventory setup";
            log.warn("Krystilia departure held: {}", Microbot.status);
            DroSlayerPlugin.setState(DroSlayerState.IDLE);
            return;
        }
        if (config.enableAutoBanking()) {
            if (activeJsonProfile != null && activeJsonProfile.hasSetup() && detectedSetup == null) {
                log.error("Required inventory setup '{}' is missing for task '{}'",
                        activeJsonProfile.getSetup(), taskName);
                terminalStopMessage = "[Dro] Slayer 2 is missing required inventory setup: "
                        + activeJsonProfile.getSetup() + ". Logging out safely.";
                stopAndLogout();
                return;
            }

            boolean alreadyInTaskArea = isAlreadyInsideCurrentTaskArea();
            if (wildernessMasterBeforeRestore) {
                wildernessMasterBeforeRestore = false;
                log.info("Next Wilderness assignment detected; restoring and banking at Ferox before departure");
                beginFeroxRecovery(DroSlayerState.BANKING, "new Wilderness assignment received");
                return;
            }
            boolean initialBankNeeded = detectedSetup != null && !initialSetupDone && !alreadyInTaskArea;
            boolean suppliesOrRequirementsMissing = cannonRecoveredForBank || needsBanking();

            if (initialBankNeeded || suppliesOrRequirementsMissing) {
                cannonRecoveredForBank = false;
                log.info("Banking required (initial={}, supplies/requirements={}), transitioning to BANKING",
                        initialBankNeeded, suppliesOrRequirementsMissing);
                DroSlayerPlugin.setState(DroSlayerState.BANKING);
                return;
            }

            if (!initialSetupDone && alreadyInTaskArea) {
                initialSetupDone = true;
                log.info("Restarted inside current task area; keeping current loadout and skipping startup banking");
            }
        }

        // Go to traveling
        if (config.enableAutoTravel()) {
            log.info("Transitioning to TRAVELING");
            DroSlayerPlugin.setState(DroSlayerState.TRAVELING);
        } else {
            log.info("Auto travel disabled, staying in DETECTING_TASK");
        }
    }

    private void handleBankingState() {
        traceDroAction("handleBankingState");
        // Rebuilding owns its confirmation dialogue. Generic bank dialogue dismissal
        // must not click Continue/Escape before the repair can confirm consuming the whip.
        if (tentacleRepair.isActive()) {
            stepTentacleRepair();
            return;
        }
        if (cannonPurchase.isActive()) {
            cannonPurchase.tick(config);
            if (!cannonPurchase.isActive()) {
                cannonPolicy.leaveBank();
                resetCannonWithdrawal();
                bankSupplyRetryAt = 0L;
                bankSupplyWaitReason = null;
            }
            return;
        }
        if (escapeAmmoRestock) {
            handleEscapeAmmoRestock();
            return;
        }
        if (System.currentTimeMillis() < bankSupplyRetryAt) {
            Microbot.status = "Waiting for bank supplies: " + bankSupplyWaitReason;
            return;
        }
        // Charge prompts and bank closing belong exclusively to the charging controller.
        if (isWildernessSlayer() && (wildernessWeaponCharge.isBusy() || wildernessWeaponCharge.isBlocked())
                && !ensureWildernessWeaponAtBank(getActiveInventorySetup())) return;

        // Dismiss any open dialogue first (e.g., residual slayer master dialogue)
        if (Rs2Dialogue.isInDialogue()) {
            if (Rs2Dialogue.hasContinue()) {
                DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Dialogue.clickContinue(); });
                sleep(300, 600);
            } else {
                // Try pressing escape to close dialogue
                DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE); });
                sleep(300, 600);
            }
            return;
        }

        // Keep protection while escaping an active fight to reach the bank.
        if (Rs2Player.isInCombat()) {
            if (config.prayerFlickStyle() != PrayerFlickStyle.OFF) {
                activatePrayer(getActiveProtectionPrayer());
            }
        } else {
            deactivateProfilePrayer();
        }

        // Preserve a known Wilderness deployment across living escape resupply trips.
        // Ordinary Slayer retains its existing pickup/reset behavior.
        if (cannonPlaced && !hasDeployedWildernessCannon()) {
            if (isCannonPlacedNearby()) {
                log.info("Cannon found nearby at bank, picking up");
                if (pickupCannon()) {
                    sleepUntil(() -> !isCannonPlacedNearby(), 3000);
                }
            } else {
                log.warn("Cannon was left at task location (not nearby), resetting cannon state");
            }
            cannonPlaced = false;
            cannonFired = false;
        }

        // Get the active inventory setup (cannon or regular)
        var activeSetup = getActiveInventorySetup();
        if (activeSetup == null) {
            if (wildernessBurstSelected()) {
                unresolvedTaskName = currentTaskName();
                Microbot.status = "Wilderness bursting needs a saved burstwildy inventory setup";
                log.warn("Krystilia departure held: selected burst setup unavailable");
                DroSlayerPlugin.setState(DroSlayerState.IDLE);
                return;
            }
            if (isWildernessSlayer()) {
                unresolvedTaskName = currentTaskName();
                Microbot.status = isWildernessAviansieTask()
                        ? "Aviansies require a saved rangewildy setup with a ranged weapon"
                        : "Selected Wilderness inventory setup is unavailable";
                log.warn("Krystilia departure held: configured Wilderness inventory setup unavailable");
                DroSlayerPlugin.setState(DroSlayerState.IDLE);
                return;
            }
            if (activeJsonProfile != null && activeJsonProfile.hasSetup()) {
                log.error("Profile requires inventory setup '{}' but it is unavailable", activeJsonProfile.getSetup());
                terminalStopMessage = "[Dro] Slayer 2 is missing inventory setup: " + activeJsonProfile.getSetup();
                stopAndLogout();
                return;
            }
            log.info("No inventory setup configured; applying task requirements to current loadout");
        }

        // One movement owner: Efficient Walker routes to a latched bank. If we are still inside
        // a dungeon it runs in DUNGEON_LOCAL mode; once we exit, FULL_TRAVEL is restored automatically.
        if (!Rs2Bank.isOpen()) {
            if (!DroSlayerRuntime.walkToBankAndOpen(config, taskLocationName)) {
                log.debug("Traveling to bank via Efficient Walker (mode={})", DroSlayerRuntime.walkerMode());
                handleRouteFailureBudget("bank");
                return;
            }
            sleepUntil(Rs2Bank::isOpen, 3000);
            if (!Rs2Bank.isOpen()) return;
        }

        if (isWildernessSlayer() && !wildernessBankChargeStarted) {
            wildernessWeaponCharge.beginBankVisit();
            wildernessBankChargeStarted = true;
        }
        if (!ensureWildernessWeaponAtBank(activeSetup)) return;
        if (!keepSingleChargedGloryAtBank()) return;

        if (!CannonSpot.isAllowed(taskDestination, taskLocationName)) {
            isUsingCannon = false;
            cannonSpot = null;
        }

        if (tentacleBankCheck || Rs2Equipment.isWearing(net.runelite.api.ItemID.KRAKEN_TENTACLE)
                || Rs2Inventory.hasItem(net.runelite.api.ItemID.KRAKEN_TENTACLE)) {
            int weaponSlot = net.runelite.api.EquipmentInventorySlot.WEAPON.getSlotIdx();
            var requiredWeapon = activeSetup != null && activeSetup.getEquipment() != null
                    && activeSetup.getEquipment().size() > weaponSlot
                    ? activeSetup.getEquipment().get(weaponSlot) : null;
            boolean requiresTentacle = requiredWeapon != null && requiredWeapon.getName() != null
                    && requiredWeapon.getName().toLowerCase(java.util.Locale.ROOT).contains("abyssal tentacle");
            if (requiresTentacle && !Rs2Equipment.isWearing(requiredWeapon.getId())
                    && !Rs2Inventory.hasItem(requiredWeapon.getId())
                    && Rs2Bank.count(requiredWeapon.getId()) == 0) {
                if (requiredWeapon.getId() == net.runelite.api.ItemID.ABYSSAL_TENTACLE
                        && (Rs2Equipment.isWearing(net.runelite.api.ItemID.KRAKEN_TENTACLE)
                            || Rs2Inventory.hasItem(net.runelite.api.ItemID.KRAKEN_TENTACLE)
                            || Rs2Bank.count(net.runelite.api.ItemID.KRAKEN_TENTACLE) > 0)
                        && (Rs2Inventory.hasItem(net.runelite.api.ItemID.ABYSSAL_WHIP)
                            || Rs2Bank.count(net.runelite.api.ItemID.ABYSSAL_WHIP) > 0)) {
                    tentacleRepair.begin();
                    stepTentacleRepair();
                    return;
                }
                log.error("Required abyssal tentacle replacement unavailable in bank; stopping and logging out");
                Microbot.status = "Required abyssal tentacle unavailable; logging out";
                stopAndLogout();
                return;
            }
            log.info("Tentacle bank check complete: requiredBySetup={}; loading active setup", requiresTentacle);
            tentacleBankCheck = false;
        }

        // Bank is open, load inventory setup
        log.info("Loading inventory setup: {} (cannon: {})", activeSetup == null ? "current loadout" : activeSetup.getName(), isUsingCannon);
        DroInventorySetup inventorySetup = activeSetup == null ? null : new DroInventorySetup(withoutSetupFood(activeSetup), mainScheduledFuture);

        // Once a task-required wearable has deliberately replaced the setup item in that slot,
        // do not let the base Inventory Setup "correct" it back on later bank trips. The full
        // setup was already loaded before initialSetupDone became true; from then on the required
        // Slayer equipment is authoritative for its occupied slot while inventory supplies can
        // still be refreshed normally.
        boolean preserveRequiredEquipment = initialSetupDone && hasSatisfiedRequiredEquipmentOverlay();
        boolean equipmentMatches = inventorySetup == null || preserveRequiredEquipment || inventorySetup.doesEquipmentMatch();
        if (preserveRequiredEquipment) {
            log.debug("Preserving task-required equipped override while refreshing Inventory Setup");
        }

        // Check if already matching
        if (baseBankLoadComplete || (equipmentMatches && (inventorySetup == null || inventorySetup.doesInventoryMatch()))) {
            // Matching required rows does not mean there are no leftovers from the previous task.
            // The normal loader keeps the runtime supply overlay and deposits only unwanted extras.
            if (!baseBankLoadComplete && inventorySetup != null && !DroSlayerRuntime.performAction(
                    BaseProfileDro.ActionPhase.SETUP, false,
                    (java.util.function.BooleanSupplier) inventorySetup::loadInventory)) return;
            baseBankLoadComplete = true;
            log.info("Inventory setup matches");
            bankLoadFailures = 0;
            prepareTaskBraceletAtBank(false);
            if (!ensureCannonPolicyAtBank()) return;
            if (!ensureTaskRequirementsAtBank()) return;
            ensureEscapeTeleportAtBank();
            if (!hasRequiredEscapeSupplies()) return;
            if (!ensureWildernessSuppliesAtBank()) return;
            if (!hasWildernessCombatLoadout()) return;
            if (!fillRemainingSpaceWithFood()) return;
            prepareTaskBraceletAtBank(true);
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Bank.closeBank(); });
            if (!sleepUntil(() -> !Rs2Bank.isOpen(), 2000)) return;
            sleepUntil(() -> currentTaskRequirements().stream().allMatch(this::isTaskRequirementSatisfied), 1800);
            if (!hasAllTaskRequirementsForCurrentTask()) {
                log.warn("Task requirements were not satisfied after closing bank; retrying banking without accepting the loadout");
                return;
            }
            initialSetupDone = true;

            // A successful bank load is not proof that health/prayer were restored.
            // This also covers starting at a bank with initialSetupDone initially false.
            if (needsFeroxRecoveryBeforeDeparture()) {
                DroSlayerState next = needsSpellbookSwap() ? DroSlayerState.SWAPPING_SPELLBOOK
                        : config.enableAutoTravel() ? DroSlayerState.TRAVELING : DroSlayerState.DETECTING_TASK;
                beginFeroxRecovery(next, "departure resource threshold (70%)");
                return;
            }

            // Check if we need to swap spellbook based on inventory setup
            if (needsSpellbookSwap()) {
                log.info("Inventory setup requires spellbook swap - transitioning to SWAPPING_SPELLBOOK");
                DroSlayerPlugin.setState(DroSlayerState.SWAPPING_SPELLBOOK);
            } else if (config.enableAutoTravel()) {
                DroSlayerPlugin.setState(DroSlayerState.TRAVELING);
            } else {
                DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK);
            }
            return;
        }

        // Load equipment and inventory. If mandatory Slayer equipment is already layered and
        // this task's base setup has previously been established, leave that equipment alone.
        boolean equipmentLoaded = equipmentMatches || DroSlayerRuntime.performAction(
                BaseProfileDro.ActionPhase.SETUP, false,
                (java.util.function.BooleanSupplier) inventorySetup::loadEquipment);
        // Do not deposit pending gear or refill its free slots while equipment is incomplete.
        boolean inventoryLoaded = equipmentLoaded && DroSlayerRuntime.performAction(
                BaseProfileDro.ActionPhase.SETUP, false,
                (java.util.function.BooleanSupplier) inventorySetup::loadInventory);

        if (equipmentLoaded && inventoryLoaded) {
            baseBankLoadComplete = true;
            log.info("Inventory setup loaded successfully");
            bankLoadFailures = 0;
            prepareTaskBraceletAtBank(false);
            if (!ensureCannonPolicyAtBank()) return;
            if (!ensureTaskRequirementsAtBank()) return;
            ensureEscapeTeleportAtBank();
            if (!hasRequiredEscapeSupplies()) return;
            if (!ensureWildernessSuppliesAtBank()) return;
            if (!hasWildernessCombatLoadout()) return;
            if (!fillRemainingSpaceWithFood()) return;
            prepareTaskBraceletAtBank(true);
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Bank.closeBank(); });
            if (!sleepUntil(() -> !Rs2Bank.isOpen(), 2000)) return;
            sleepUntil(() -> currentTaskRequirements().stream().allMatch(this::isTaskRequirementSatisfied), 1800);
            if (!hasAllTaskRequirementsForCurrentTask()) {
                log.warn("Task requirements were not satisfied after closing bank; retrying banking without accepting the loadout");
                return;
            }
            initialSetupDone = true;

            // A successful bank load is not proof that health/prayer were restored.
            // This also covers starting at a bank with initialSetupDone initially false.
            if (needsFeroxRecoveryBeforeDeparture()) {
                DroSlayerState next = needsSpellbookSwap() ? DroSlayerState.SWAPPING_SPELLBOOK
                        : config.enableAutoTravel() ? DroSlayerState.TRAVELING : DroSlayerState.DETECTING_TASK;
                beginFeroxRecovery(next, "departure resource threshold (70%)");
                return;
            }

            // Check if we need to swap spellbook based on inventory setup
            if (needsSpellbookSwap()) {
                log.info("Inventory setup requires spellbook swap - transitioning to SWAPPING_SPELLBOOK");
                DroSlayerPlugin.setState(DroSlayerState.SWAPPING_SPELLBOOK);
            } else if (config.enableAutoTravel()) {
                DroSlayerPlugin.setState(DroSlayerState.TRAVELING);
            } else {
                DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK);
            }
        } else {
            long now = System.currentTimeMillis();
            if (now - lastBankLoadFailureAt >= 3500L) {
                lastBankLoadFailureAt = now;
                bankLoadFailures++;
            }
            log.warn("Failed to load inventory setup completely: stage={} (failure {}/{})",
                    equipmentLoaded ? "inventory" : "equipment", bankLoadFailures, config.maxRecoveryAttempts());
            if (bankLoadFailures >= config.maxRecoveryAttempts()) {
                terminalStopMessage = "[Dro] Slayer 2 could not build the required bank loadout. Logging out safely.";
                stopAndLogout();
            }
        }
    }

    private void prepareTaskBraceletAtBank(boolean allowSpare) {
        traceDroAction("prepareTaskBraceletAtBank");
        long bankSession = Rs2Bank.getBankLastOpenedAt();
        if (Rs2Bank.isOpen() && braceletBankReportAt != bankSession) {
            braceletBankReportAt = bankSession;
            String requested = DroSlayerBracelets.requestedBracelet(activeJsonProfile, isWildernessSlayer());
            log.info("Task bracelet bank check: task={} wilderness={} selected={} stock={} carried={} worn={} freeSlots={}",
                    currentTaskProfileName(), isWildernessSlayer(), requested,
                    requested == null ? 0 : Rs2Bank.count(requested, true),
                    requested == null ? 0 : Rs2Inventory.itemQuantity(requested, true),
                    requested != null && Rs2Equipment.isWearing(requested, true), Rs2Inventory.getEmptySlots());
        }
        // Optional bracelets must not consume the task's minimum food reserve.
        if (Rs2Inventory.isFull() && taskFood().size() <= Math.max(1, config.foodThreshold())) {
            DroSlayerBracelets.equipCarried(activeJsonProfile, isWildernessSlayer());
            return;
        }
        if (allowSpare) DroSlayerBracelets.ensureAtBank(activeJsonProfile, isWildernessSlayer());
        else DroSlayerBracelets.equipAtBank(activeJsonProfile, isWildernessSlayer());
    }

    private boolean ensureWildernessWeaponAtBank(
            net.runelite.client.plugins.microbot.inventorysetups.InventorySetup setup) {
        if (!isWildernessSlayer()) return true;
        int desired = 0;
        int slot = net.runelite.api.EquipmentInventorySlot.WEAPON.getSlotIdx();
        if (setup != null && setup.getEquipment() != null && setup.getEquipment().size() > slot
                && setup.getEquipment().get(slot) != null) desired = setup.getEquipment().get(slot).getId();
        DroSlayerWildernessWeaponCharge.Result result = wildernessWeaponCharge.ensureAtBank(
                config, Rs2Slayer.getSlayerTaskSize(), desired);
        if (result == DroSlayerWildernessWeaponCharge.Result.BLOCKED)
            Microbot.status = "Wilderness weapon charging held: " + wildernessWeaponCharge.getFailureReason();
        return result == DroSlayerWildernessWeaponCharge.Result.READY;
    }

    /**
     * Build the task requirement list from automatic safety rules plus any explicit JSON
     * profile flags. This keeps older/custom profiles authoritative while making common
     * survival supplies automatic even when the user only maintains one base Inventory Setup.
     */
    private List<DroSlayerTaskRequirements.Requirement> currentTaskRequirements() {
        String taskName = currentTaskName();
        boolean forceAntipoison = activeJsonProfile != null && activeJsonProfile.isAntipoison();
        boolean forceAntivenom = activeJsonProfile != null && activeJsonProfile.isAntivenom();
        boolean babyDragon = isBabyDragonTarget();
        boolean forceAntifire = !babyDragon && activeJsonProfile != null && activeJsonProfile.isSuperAntifire();
        List<DroSlayerTaskRequirements.Requirement> requirements = new ArrayList<>(DroSlayerTaskRequirements.forTask(
                babyDragon ? "baby dragons" : taskName, taskLocationName,
                forceAntipoison, forceAntivenom, forceAntifire));
        if (isWildernessSlayer() && wildernessAssignment != null) {
            // Wilderness God Wars uses its own entrance; an ordinary GWD rope is irrelevant.
            requirements.removeIf(item -> "God Wars entrance rope".equals(item.getLabel()));
            if (wildernessAssignment.getRequirements().contains(DroSlayerWildernessTasks.Requirement.LOCKPICK)) {
                requirements.add(DroSlayerTaskRequirements.Requirement.inventory("Wilderness hut lockpick", "Lockpick", 1, "Lockpick"));
            }
            if (wildernessAssignment.getRequirements().contains(DroSlayerWildernessTasks.Requirement.WEB_CUTTING)
                    && !DroSlayerWalkerItems.hasSlashWeapon()) {
                requirements.add(DroSlayerTaskRequirements.Requirement.inventory("Wilderness web knife", "Knife", 1, "Knife"));
            }
        }
        return requirements;
    }

    /**
     * The selected Inventory Setup is always loaded first. This second pass layers only
     * Slayer-mandatory task items on top of it (protective gear, lure tools, finishing items,
     * and survival potions). That keeps the user's setup authoritative while still making each
     * task usable without creating a separate Inventory Setup for every monster.
     */
    private net.runelite.client.plugins.microbot.inventorysetups.InventorySetup withoutSetupFood(
            net.runelite.client.plugins.microbot.inventorysetups.InventorySetup original) {
        // Work on a private copy; other scripts keep the user's saved setup unchanged.
        var copy = DroSlayerLoadout.copyForFoodOverlay(original);
        boolean wilderness = isWildernessSlayer();
        String braceletName = DroSlayerBracelets.requestedBracelet(activeJsonProfile, wilderness);
        if (braceletName != null) {
            Rs2ItemModel bracelet = Rs2Equipment.get(net.runelite.api.EquipmentInventorySlot.GLOVES);
            if (bracelet == null || !braceletName.equalsIgnoreCase(bracelet.getName())) {
                bracelet = Rs2Inventory.get(item -> !item.isNoted() && braceletName.equalsIgnoreCase(item.getName()));
            }
            if (bracelet == null && Rs2Bank.isOpen()) {
                bracelet = Rs2Bank.bankItems().stream().filter(item -> !item.isNoted()
                        && item.getQuantity() > 0 && braceletName.equalsIgnoreCase(item.getName())).findFirst().orElse(null);
            }
            // A real row makes the selected bracelet valid during selective equipment repair.
            // A dummy row is ignored by that repair and would cause an already worn bracelet to be deposited.
            DroSlayerLoadout.overrideHands(copy, bracelet);
        }
        boolean cannon = shouldUseCannon(currentTaskName()) && CannonSpot.isAllowed(taskDestination, taskLocationName);
        List<DroSlayerTaskRequirements.Requirement> requirements = departureTaskRequirements();
        DroSlayerLoadout.removeUnneededAntifire(copy, requirements);
        Rs2ItemModel carriedGlory = DroSlayerBankSupplies.chargedGlory(Rs2Inventory.getList(item -> true));
        boolean setupHasGlory = copy.getInventory().stream().anyMatch(row -> row != null
                && row.getId() > 0 && DroSlayerBankSupplies.isGlory(row.getName()));
        Rs2ItemModel preferredGlory = carriedGlory != null ? carriedGlory : setupHasGlory && Rs2Bank.isOpen()
                ? DroSlayerBankSupplies.chargedGlory(new ArrayList<>(Rs2Bank.bankItems())) : null;
        DroSlayerLoadout.limitInventoryGlories(copy,preferredGlory);
        List<Rs2ItemModel> retained = Rs2Inventory.getList(item -> !item.isNoted()
                && (!DroSlayerBankSupplies.isGlory(item.getName())
                    || carriedGlory != null && item.getId() == carriedGlory.getId())
                && (DroSlayerBankSupplies.keep(item.getName(), wilderness, cannon, braceletName, requirements)
                || DroSlayerWildernessSupplies.isFood(item, wilderness)));
        DroSlayerLoadout.retainCarried(copy, retained);
        DroSlayerLoadout.reuseChargedJewelry(copy, Rs2Inventory.getList(item -> true));
        if (copy.getInventory().stream().anyMatch(row -> row != null
                && "Super combat potion(4)".equalsIgnoreCase(row.getName()))
                && Rs2Bank.isOpen()) DroSlayerLoadout.fallbackSuperCombat(copy,
                Rs2Inventory.getList(item -> true), new ArrayList<>(Rs2Bank.bankItems()));
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            int weaponSlot = net.runelite.api.EquipmentInventorySlot.WEAPON.getSlotIdx();
            if (isWildernessSlayer() && copy.getEquipment().size() > weaponSlot) {
                var weapon = copy.getEquipment().get(weaponSlot);
                int charged = weapon == null ? 0 : DroSlayerWildernessWeaponCharge.chargedWeaponId(weapon.getId());
                if (weapon != null && charged != weapon.getId()) {
                    copy.getEquipment().set(weaponSlot,
                            new net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem(
                                    charged, Microbot.getClient().getItemDefinition(charged).getName(),
                                    weapon.getQuantity(), weapon.isFuzzy(), weapon.getStackCompare(),
                                    weapon.isLocked(), weapon.getSlot()));
                }
            }
            copy.getInventory().replaceAll(item -> {
                if (item == null || item.getId() <= 0) return item;
                var definition = Microbot.getClient().getItemDefinition(item.getId());
                String[] actions = definition.getInventoryActions();
                boolean edible = actions != null && Arrays.stream(actions)
                        .anyMatch(action -> "Eat".equalsIgnoreCase(action));
                if (edible || "Jug of wine".equalsIgnoreCase(definition.getName())
                        || DroSlayerCannonPolicy.isSupply(definition.getName())) {
                    return net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem.getDummyItem();
                }
                return item;
            });
            return true;
        });
        return copy;
    }

    private boolean keepSingleChargedGloryAtBank() {
        if (!Rs2Bank.isOpen()) return false;
        Map<Integer,Integer> excess = DroSlayerBankSupplies.excessGlories(Rs2Inventory.getList(item -> true));
        if (excess.isEmpty()) return true;
        var deposit = excess.entrySet().iterator().next();
        int id = deposit.getKey(), amount = deposit.getValue();
        int before = Rs2Inventory.itemQuantity(id);
        log.info("Banking surplus/spent glories item={} amount={}; keeping one charged glory",id,amount);
        boolean requested = DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP,false,
                (java.util.function.BooleanSupplier) () -> Rs2Bank.depositX(id,amount));
        if (!requested || !sleepUntil(() -> Rs2Inventory.itemQuantity(id) <= before - amount,1800))
            Microbot.status = "Waiting to bank surplus/spent glories";
        // Re-query the observed inventory on the next tick before loading or departing.
        return false;
    }

    private void handleEscapeAmmoRestock() {
        traceDroAction("handleEscapeAmmoRestock");
        if (System.currentTimeMillis() < bankSupplyRetryAt) return;
        Microbot.status = "Restocking cannonballs after escape";
        if (escapeAmmoClosing) {
            if (!Rs2Bank.isOpen()) finishEscapeAmmoRestock();
            else DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                    (java.util.function.BooleanSupplier) Rs2Bank::closeBank);
            return;
        }
        if (!Rs2Bank.isOpen()) {
            DroSlayerRuntime.walkToBankAndOpen(config, taskLocationName);
            return;
        }
        int carried = getCannonballCount();
        if (tryBuyCannonballsAtBank()) return;
        String ammo = Rs2Bank.count("Steel cannonball", false) > 0 ? "Steel cannonball"
                : Rs2Bank.count("Cannonball", false) > 0 ? "Cannonball"
                : Rs2Bank.count("Granite cannonball", false) > 0 ? "Granite cannonball" : null;
        if (carried == 0 && ammo == null) {
            waitForBankSupplies("No cannonballs in bank after escape");
            return;
        }
        int target = DroSlayerCannonPolicy.ammunitionTarget(true, config.cannonballThreshold());
        if (carried < target && ammo != null) {
            if (Rs2Inventory.isFull() && !ensureFreeInventorySlotForTaskItem()) return;
            if (System.currentTimeMillis() < cannonWithdrawalRetryAt) return;
            int quantity = Math.min(target - carried, Rs2Bank.count(ammo, false));
            cannonWithdrawalRetryAt = System.currentTimeMillis() + 2_000L;
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                    (java.util.function.BooleanSupplier) () -> {
                        Rs2Bank.setWithdrawAsItem();
                        return Rs2Bank.withdrawX(ammo, quantity);
                    });
            return;
        }
        // This supply-only visit must not reset the deployed cannon or reload equipment.
        escapeAmmoClosing = true;
        DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                (java.util.function.BooleanSupplier) Rs2Bank::closeBank);
    }

    private void finishEscapeAmmoRestock() {
        int carried = getCannonballCount();
        escapeAmmoRestock = false;
        escapeAmmoClosing = false;
        bankSupplyRetryAt = 0L;
        bankSupplyWaitReason = null;
        cannonPolicy.leaveBank();
        cannonPolicy.observeBank(true, carried);
        cannonPolicy.observeRestock(carried, DroSlayerCannonPolicy.restockThreshold(true, config.cannonballThreshold()));
        isUsingCannon = true;
        resetCannonWithdrawal();
        log.info("Escape ammunition restocked: carried={}; returning with existing gear and cannon", carried);
        DroSlayerPlugin.setState(DroSlayerState.TRAVELING);
    }

    /** Hub build: the droplugins client's diagnostics trace is not available, so this does nothing. */
    private void traceDroAction(String method) {
    }

    private static String supplyName(int itemId) {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getItemDefinition(itemId).getName()).orElse("item " + itemId);
    }

    /** Requests terminal logout before notifying. Call from the script's worker. */
    private static void stopForSupplies(String supplies) {
        String message = Microbot.supplyMessage(supplies);
        Microbot.status = message;
        try {
            Rs2Player.logoutWithoutAutoLogin();
        } catch (RuntimeException ex) {
            log.warn("Supply-stop logout attempt failed", ex);
        }
        Microbot.showMessage(message);
    }

    private boolean stopForBankSupply(String supplies) {
        traceDroAction("stopForBankSupply");
        DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, true,
                () -> stopForSupplies(supplies));
        shutdown();
        DroSlayerRuntime.shutdown();
        return false;
    }

    private boolean waitForBankSupplies(String reason) {
        if (!reason.equals(bankSupplyWaitReason)) log.warn("Bank supply wait: {} (rechecking every 10 seconds)", reason);
        bankSupplyWaitReason = reason;
        bankSupplyRetryAt = System.currentTimeMillis() + 10_000L;
        Microbot.status = "Waiting for bank supplies: " + reason;
        // Keep the successful base load and retry supply preparation rather than latching IDLE.
        DroSlayerPlugin.setState(DroSlayerState.BANKING);
        return false;
    }

    private boolean fillRemainingSpaceWithFood() {
        // Keep two slots available for travel withdrawals and loot.
        int free = Math.max(0, Rs2Inventory.emptySlotCount() - 2);
        int carried = taskFood().size();
        if (free == 0) {
            if (carried >= Math.max(1, config.foodThreshold())) return true;
            return waitForBankSupplies("Not enough room for food after required supplies");
        }
        Rs2ItemModel food = Rs2Bank.bankItems().stream()
                .filter(item -> !item.isStackable() && !item.isNoted()
                        && DroSlayerWildernessSupplies.isFood(item, isWildernessSlayer()))
                .min(Comparator.comparingInt(item -> DroSlayerWildernessSupplies.foodPreference(item.getName(), isWildernessSlayer())))
                .orElse(null);
        if (food == null) {
            if (carried >= Math.max(1, config.foodThreshold())) return true;
            return stopForBankSupply("usable food");
        }
        int quantity = Math.min(free, food.getQuantity());
        boolean requested = DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                (java.util.function.BooleanSupplier) () -> Rs2Bank.withdrawX(food.getId(), quantity));
        if (requested) sleepUntil(() -> taskFood().size() >= carried + quantity, 1800);
        // Re-evaluate remaining bank food and free slots on the next tick.
        return false;
    }

    private boolean hasDeployedWildernessCannon() {
        return isWildernessSlayer() && (cannonPlaced || cannonAssemblyStage > 0)
                && cannonDeploymentWorld > 0 && lastCannonPlacementTile != null;
    }

    private boolean ensureCannonPolicyAtBank() {
        cannonPolicy.selectTask(currentTaskName());
        if (tryBuyCannonballsAtBank()) return false;
        String[] parts = {"Cannon base", "Cannon stand", "Cannon barrels", "Cannon furnace"};
        boolean deployed = hasDeployedWildernessCannon();
        boolean availableParts = deployed || Arrays.stream(parts).allMatch(part ->
                Rs2Inventory.hasItem(part) || Rs2Bank.count(part, false) > 0);
        int availableAmmo = getCannonballCount() + Rs2Bank.count("Steel cannonball", false)
                + Rs2Bank.count("Cannonball", false) + Rs2Bank.count("Granite cannonball", false);
        if (deployed && cannonEnabledForMaster() && availableAmmo == 0)
            return waitForBankSupplies("Cannon remains deployed; waiting for cannonballs before returning");
        cannonPolicy.observeBank(availableParts, availableAmmo);
        if (!cannonPolicy.isDisabled() && shouldUseCannon(currentTaskName())
                && CannonSpot.isAllowed(taskDestination, taskLocationName)) {
            isUsingCannon = true;
            if (cannonSpot == null) cannonSpot = isWildernessSlayer() && wildernessAssignment != null
                    && wildernessAssignment.getCannonSpot() != null ? wildernessAssignment.getCannonSpot() : taskDestination;
            if (!DroSlayerCannonPolicy.canDeploy(availableParts, availableAmmo)) {
                disableCannonForTrip("Cannon parts or ammunition unavailable in bank");
            } else {
                for (String part : deployed ? new String[0] : parts) {
                    if (!ensureCannonItemAtBank(part, 1)) return false;
                    if (!isUsingCannon) break;
                }
                int ammoTarget = DroSlayerCannonPolicy.ammunitionTarget(isWildernessSlayer(), config.cannonballThreshold());
                int carriedAmmo = getCannonballCount();
                if (isUsingCannon && DroSlayerCannonPolicy.needsBankTopUp(deployed || config.bankForCannonballs(),
                        carriedAmmo, isWildernessSlayer(), config.cannonballThreshold())) {
                    String ammunition = Rs2Bank.count("Steel cannonball", false) > 0 ? "Steel cannonball"
                            : Rs2Bank.count("Cannonball", false) > 0 ? "Cannonball"
                            : Rs2Bank.count("Granite cannonball", false) > 0 ? "Granite cannonball" : null;
                    int carriedVariant = ammunition == null ? 0 : Rs2Inventory.itemQuantity(ammunition, false);
                    int desiredAmmo = ammunition == null ? 0 : isWildernessSlayer()
                            ? carriedVariant + Math.min(Rs2Bank.count(ammunition, false), ammoTarget - carriedAmmo)
                            : Math.min(Rs2Bank.count(ammunition, false) + carriedVariant, ammoTarget);
                    if (ammunition != null && desiredAmmo > carriedVariant)
                        log.info("Topping up cannon ammunition at bank: carried={} target={} withdrawing={}",
                                carriedAmmo, ammoTarget, desiredAmmo - carriedVariant);
                    if (ammunition != null && !ensureCannonItemAtBank(ammunition,
                            desiredAmmo)) return false;
                    if (getCannonballCount() == 0) disableCannonForTrip("Cannon ammunition unavailable");
                }
                if (isUsingCannon) {
                    if (deployed) log.info("Resuming deployed cannon after banking: tile={} world={} carried={}",
                            lastCannonPlacementTile, cannonDeploymentWorld, getCannonballCount());
                    cannonPolicy.observeRestock(getCannonballCount(),
                            DroSlayerCannonPolicy.restockThreshold(isWildernessSlayer(), config.cannonballThreshold()));
                    return true;
                }
            }
        }
        isUsingCannon = false;
        cannonSpot = null;
        for (Rs2ItemModel item : Rs2Inventory.getList(i -> DroSlayerCannonPolicy.isSupply(i.getName()))) {
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                    (java.util.function.BooleanSupplier) () -> Rs2Bank.depositAll(item.getId()));
            if (!sleepUntil(() -> !Rs2Inventory.hasItem(item.getId()), 1800)) return false;
        }
        return true;
    }

    private boolean tryBuyCannonballsAtBank() {
        if (!config.autoBuyCannonballs() || !config.bankForCannonballs() || !config.enableAutoBanking()
                || !cannonEnabledForMaster() || !shouldUseCannon(currentTaskName(), true) || !Rs2Bank.isOpen()
                || !CannonSpot.isAllowed(taskDestination, taskLocationName)) return false;
        if (!hasDeployedWildernessCannon() && !Arrays.stream(new String[]{"Cannon base", "Cannon stand", "Cannon barrels", "Cannon furnace"})
                .allMatch(part -> Rs2Inventory.hasItem(part) || Rs2Bank.count(part, false) > 0)) return false;
        int remaining = Rs2Slayer.getSlayerTaskSize();
        if (remaining >= 0 && remaining < 10) return false;
        int stock = Rs2Bank.count("Steel cannonball", true) + Rs2Bank.count("Cannonball", true)
                + Rs2Bank.count("Granite cannonball", true);
        int target = DroSlayerCannonPolicy.ammunitionTarget(isWildernessSlayer(), config.cannonballThreshold());
        if (!DroSlayerCannonPolicy.needsPurchase(stock, getCannonballCount(), target)) return false;
        log.info("Cannon purchase check: bank={} carried={} tripTarget={} taskRemaining={}",
                stock, getCannonballCount(), target, remaining);
        if (!Rs2Inventory.hasItem(net.runelite.api.ItemID.COINS_995) && !ensureFreeInventorySlotForTaskItem()) return false;
        return cannonPurchase.begin();
    }

    private boolean ensureCannonItemAtBank(String name, int desired) {
        if (!Rs2Bank.isOpen()) return false;
        int carried = Rs2Inventory.itemQuantity(name, false);
        if (carried >= desired) {
            if (name.equals(cannonWithdrawalItem)) resetCannonWithdrawal();
            return true;
        }
        int available = Rs2Bank.count(name, false);
        if (available <= 0) {
            disableCannonForTrip("Missing optional cannon supply: " + name);
            return true;
        }
        if (carried == 0 && !ensureFreeInventorySlotForTaskItem()) {
            disableCannonForTrip("No inventory space for optional cannon supply: " + name);
            return true;
        }
        if (!name.equals(cannonWithdrawalItem)) {
            resetCannonWithdrawal();
            cannonWithdrawalItem = name;
        }
        if (System.currentTimeMillis() < cannonWithdrawalRetryAt) return false;
        boolean[] attempted = {false};
        boolean requested = DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                (java.util.function.BooleanSupplier) () -> {
                    attempted[0] = true;
                    return Rs2Bank.withdrawX(name, Math.min(available, desired - carried), false);
                });
        // An input/profile hold is not a failed bank click and must not consume retries.
        if (!attempted[0]) {
            cannonWithdrawalRetryAt = System.currentTimeMillis() + 2000L;
            return false;
        }
        if (Rs2Inventory.itemQuantity(name, false) > carried
                || requested && sleepUntil(() -> Rs2Inventory.itemQuantity(name, false) > carried, 1800)) {
            resetCannonWithdrawal();
            return Rs2Inventory.itemQuantity(name, false) >= desired;
        }
        cannonWithdrawalRetryAt = System.currentTimeMillis() + 2000L;
        if (++cannonWithdrawalFailures >= 3) {
            disableCannonForTrip("Optional cannon withdrawal failed after three spaced attempts: " + name);
            resetCannonWithdrawal();
            return true;
        }
        log.warn("Cannon withdrawal not observed: item={} attempt={}/3; retrying at bank", name, cannonWithdrawalFailures);
        Microbot.status = "Retrying cannon withdrawal: " + name;
        return false;
    }

    private void resetCannonWithdrawal() {
        cannonWithdrawalItem = null;
        cannonWithdrawalFailures = 0;
        cannonWithdrawalRetryAt = 0L;
    }

    private List<DroSlayerTaskRequirements.Requirement> departureTaskRequirements() {
        String taskName = currentTaskName();
        List<DroSlayerTaskRequirements.Requirement> bankRequirements = new java.util.ArrayList<>(currentTaskRequirements());
        if ("Custodian Stalkers".equalsIgnoreCase(taskName)) {
            // Departure supply only: consuming the tablet must not trigger another bank trip.
            bankRequirements.add(DroSlayerTaskRequirements.Requirement.inventory(
                    "Civitas travel tablet", "Civitas illa fortis teleport", 1,
                    "Civitas illa fortis teleport"));
        }
        if ("Wyrms".equalsIgnoreCase(taskName)) {
            bankRequirements.add(DroSlayerTaskRequirements.Requirement.inventory(
                    "Wyrms travel necklace", "Skills necklace(1)", 1,
                    "Skills necklace(1)", "Skills necklace(2)", "Skills necklace(3)",
                    "Skills necklace(4)", "Skills necklace(5)", "Skills necklace(6)"));
        }
        String travelLocation = taskLocationName == null ? "" : taskLocationName.toLowerCase(java.util.Locale.ROOT);
        if (travelLocation.contains("taverley") || travelLocation.contains("taverly")) {
            bankRequirements.add(DroSlayerTaskRequirements.Requirement.inventory(
                    "Taverley travel tablet", "Taverley teleport", 1, "Taverley teleport"));
        }
        if (travelLocation.contains("brimhaven")) {
            bankRequirements.add(DroSlayerTaskRequirements.Requirement.inventory(
                    "Brimhaven travel tablet", "Brimhaven teleport", 1, "Brimhaven teleport"));
        }
        return bankRequirements;
    }

    /** Send one bank action, then require the next inventory observation before departure. */
    private boolean bankUnneededAntifire(List<DroSlayerTaskRequirements.Requirement> requirements) {
        if (DroSlayerBankSupplies.needsAntifire(requirements)) return true;
        Rs2ItemModel leftover = Rs2Inventory.get(item -> DroSlayerBankSupplies.isAntifire(item.getName()));
        if (leftover == null) return true;
        Microbot.status = "Banking unneeded antifire for current Slayer task";
        DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                (java.util.function.BooleanSupplier) () -> Rs2Bank.depositAll(leftover.getId()));
        return false;
    }

    private boolean ensureTaskRequirementsAtBank() {
        if (!Rs2Bank.isOpen()) return false;

        String taskName = currentTaskName();
        List<DroSlayerTaskRequirements.Requirement> bankRequirements = departureTaskRequirements();
        if (!bankUnneededAntifire(bankRequirements)) return false;
        for (DroSlayerTaskRequirements.Requirement requirement : bankRequirements) {
            if (isTaskRequirementSatisfied(requirement)) continue;
            // Preferred departure teleports are conveniences, never mandatory task gear.
            if (requirement.getLabel().contains("travel ") && findBankItemForRequirement(requirement) == null) {
                continue;
            }

            if (requirement.isEquipped()) {
                Rs2ItemModel carried = Rs2Inventory.get(item -> item.getName() != null
                        && Arrays.stream(requirement.getAcceptedNames()).anyMatch(name ->
                        item.getName().toLowerCase().contains(name.toLowerCase())));
                if (carried != null) {
                    Rs2Inventory.wield(carried.getName());
                    if (!sleepUntil(() -> isTaskRequirementSatisfied(requirement), 1800)) return false;
                    continue;
                }
            }
            String bankName = findBankItemForRequirement(requirement);
            if (bankName == null) {
                log.error("Missing required Slayer item '{}' for task '{}'", requirement.getLabel(), taskName);
                terminalStopMessage = Microbot.supplyMessage(requirement.getLabel());
                stopAndLogout(true);
                return false;
            }

            if (!ensureFreeInventorySlotForTaskItem()) {
                log.error("No inventory slot available for required Slayer item '{}'", requirement.getLabel());
                terminalStopMessage = "[Dro] Slayer 2 needs one inventory slot for: "
                        + requirement.getLabel() + ". Logging out safely.";
                stopAndLogout();
                return false;
            }

            List<Rs2ItemModel> previouslyEquipped = new java.util.ArrayList<>();
            if (requirement.isEquipped()) {
                for (net.runelite.api.EquipmentInventorySlot slot : net.runelite.api.EquipmentInventorySlot.values()) {
                    Rs2ItemModel worn = Rs2Equipment.get(slot);
                    if (worn != null) previouslyEquipped.add(worn);
                }
            }
            boolean requested;
            if (requirement.isEquipped()) {
                log.info("Layering required Slayer equipment '{}' onto Inventory Setup", bankName);
                requested = DroSlayerRuntime.performAction(
                        BaseProfileDro.ActionPhase.SETUP, false,
                        (java.util.function.BooleanSupplier) () -> Rs2Bank.withdrawAndEquip(bankName));
            } else {
                int already = Rs2Inventory.itemQuantity(bankName, false);
                int needed = Math.max(0, requirement.getQuantity() - already);
                if (needed <= 0) continue;
                log.info("Layering required Slayer inventory item '{}' x{} onto Inventory Setup", bankName, needed);
                requested = DroSlayerRuntime.performAction(
                        BaseProfileDro.ActionPhase.SETUP, false,
                        (java.util.function.BooleanSupplier) () -> Rs2Bank.withdrawX(bankName, needed, false));
            }

            if (!requested) {
                log.warn("Bank rejected required Slayer item '{}'", bankName);
                return false;
            }

            if (requirement.isEquipped()) {
                // Equipment state can lag or be hidden while the bank interface is open. A
                // successful withdrawAndEquip also displaces the setup's old slot item into
                // inventory, so re-running the base setup here can immediately undo the required
                // gear and create a bank loop. Defer the authoritative equipment check until the
                // bank is closed. If the item was only withdrawn, explicitly wear it first.
                sleepUntil(() -> isTaskRequirementSatisfied(requirement) || Rs2Inventory.hasItem(bankName), 1800);
                if (!isTaskRequirementSatisfied(requirement) && Rs2Inventory.hasItem(bankName)) {
                    final String equipmentToWear = bankName;
                    DroSlayerRuntime.performAction(
                            BaseProfileDro.ActionPhase.SETUP, false,
                            (java.util.function.BooleanSupplier) () -> Rs2Inventory.wield(equipmentToWear));
                }
                if (!sleepUntil(() -> isTaskRequirementSatisfied(requirement), 1800)) return false;
                for (Rs2ItemModel displaced : previouslyEquipped) {
                    if (!Rs2Equipment.isWearing(displaced.getId()) && Rs2Inventory.hasItem(displaced.getId())) {
                        Rs2Bank.depositOne(displaced.getId());
                        if (!sleepUntil(() -> !Rs2Inventory.hasItem(displaced.getId()), 1800)) return false;
                    }
                }
                continue;
            }

            sleepUntil(() -> isTaskRequirementSatisfied(requirement), 1800);
            if (!isTaskRequirementSatisfied(requirement)) {
                log.warn("Required Slayer item '{}' was not satisfied after bank action", requirement.getLabel());
                return false;
            }
        }

        var setup = getActiveInventorySetup();
        if (setup != null && setup.getEquipment() != null) {
            for (var saved : setup.getEquipment()) {
                if (saved == null || saved.getId() <= 0 || Rs2Equipment.isWearing(saved.getId())) continue;
                if (Rs2Inventory.hasItem(saved.getId()) && hasSatisfiedRequiredEquipmentOverlay()) {
                    Rs2Bank.depositOne(saved.getId());
                    if (!sleepUntil(() -> !Rs2Inventory.hasItem(saved.getId()), 1800)) return false;
                }
            }
        }
        return ensureWeaknessItemsAtBank();
    }

    /**
     * Keep one direct, bank-adjacent escape teleport in the combat loadout when possible.
     * Inventory Setup remains authoritative: this only layers a single item when the setup
     * contains none of the supported escape jewellery.  Failure is non-fatal because local
     * cave-exit handling remains the fallback.
     */
    private void ensureEscapeTeleportAtBank() {
        if (isWildernessSlayer()) {
            ensureWildernessEscapeAtBank();
            return;
        }
        if (!Rs2Bank.isOpen() || duelingChargesCarried() >= 2) return;

        String escapeItem = Rs2Bank.bankItems().stream()
                .filter(item -> item.getQuantity() > 0 && duelingCharges(item.getName()) >= 2)
                .max(Comparator.comparingInt(item -> duelingCharges(item.getName())))
                .map(Rs2ItemModel::getName).orElse(null);

        if (escapeItem == null) {
            Microbot.status = "Banking needs a charged ring of dueling for Ferox recovery";
            log.warn("No charged ring of dueling found; keeping the task at the bank");
            return;
        }

        if (Rs2Inventory.isFull() && !ensureFreeInventorySlotForTaskItem()) {
            log.warn("Inventory Setup is full and has no replaceable food slot for escape teleport '{}'; keeping setup unchanged",
                    escapeItem);
            return;
        }

        log.info("Layering post-task escape teleport '{}' onto Inventory Setup", escapeItem);
        final String escapeItemToWithdraw = escapeItem;
        if (!DroSlayerRuntime.performAction(
                BaseProfileDro.ActionPhase.SETUP, false,
                (java.util.function.BooleanSupplier) () -> Rs2Bank.withdrawOne(escapeItemToWithdraw, false))) {
            log.warn("Could not withdraw escape teleport '{}'", escapeItem);
            return;
        }
        sleepUntil(this::hasBankEscapeTeleportInInventory, 1800);
    }

    private static int duelingCharges(String name) {
        if (name == null || !name.startsWith("Ring of dueling(")) return 0;
        int index = name.indexOf('(') + 1;
        if (index >= name.length()) return 0;
        char charge = name.charAt(index);
        return charge >= '1' && charge <= '8' ? charge - '0' : 0;
    }

    private int duelingChargesCarried() {
        int charges = Rs2Inventory.items().mapToInt(item -> duelingCharges(item.getName()) * item.getQuantity()).sum();
        var ring = Rs2Equipment.get(net.runelite.api.EquipmentInventorySlot.RING);
        return charges + (ring == null ? 0 : duelingCharges(ring.getName()));
    }

    private boolean hasBankEscapeTeleportInInventory() {
        if (isWildernessSlayer()) return Rs2Inventory.get(item -> !item.isNoted() && item.getQuantity() > 0
                && DroSlayerWalkerItems.isLevel30Escape(item.getName())) != null;
        return Rs2Inventory.hasItem("Ring of dueling") || Rs2Equipment.isWearing("Ring of dueling", false);
    }

    /**
     * True only when at least one mandatory task equipment requirement is physically being worn
     * and every other non-waived mandatory equipment requirement is also satisfied. This is used
     * after the base setup has already been established so subsequent food/supply rebanks do not
     * replace task-required gear with the setup's original slot item.
     */
    private boolean hasSatisfiedRequiredEquipmentOverlay() {
        boolean hasPhysicalOverlay = false;

        for (DroSlayerTaskRequirements.Requirement requirement : currentTaskRequirements()) {
            if (requirement == null || !requirement.isEquipped()) continue;

            boolean physicallyWorn = isRequiredEquipmentActuallyWorn(requirement);
            if (physicallyWorn) {
                hasPhysicalOverlay = true;
                continue;
            }

            // A requirement can be legitimately waived (for example Karuulm protection after
            // the elite diary). A waived requirement should not by itself suppress setup gear
            // loading, but an unsatisfied requirement must.
            if (!isTaskRequirementSatisfied(requirement)) {
                return false;
            }
        }

        return hasPhysicalOverlay;
    }

    private boolean isRequiredEquipmentActuallyWorn(DroSlayerTaskRequirements.Requirement requirement) {
        if (requirement == null || !requirement.isEquipped()) return false;

        if (requirement.isSlayerHelmetCovers() && Rs2Equipment.isWearing("slayer helmet", false)) {
            return true;
        }

        for (String name : requirement.getAcceptedNames()) {
            if (name != null && !name.isBlank() && Rs2Equipment.isWearing(name, false)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTaskRequirementSatisfied(DroSlayerTaskRequirements.Requirement requirement) {
        if (requirement == null) return true;

        if ("dungeon light source".equals(requirement.getLabel())
                && (Rs2Equipment.isWearing("Bruma torch", false)
                    || Rs2Equipment.isWearing("Kandarin headgear", false))) return true;

        // Kourend & Kebos Elite permanently removes Karuulm's protective-boot requirement.
        if (requirement.getLabel() != null
                && requirement.getLabel().toLowerCase().contains("karuulm")
                && Microbot.getVarbitValue(KOUREND_DIARY_ELITE_COMPLETE) == 1) {
            return true;
        }

        // Serpentine helm provides complete poison/venom immunity, so do not replace food
        // with a cure potion when the equipped setup already supplies that protection.
        if (requirement.getLabel() != null) {
            String label = requirement.getLabel().toLowerCase();
            if ((label.contains("poison") || label.contains("venom"))
                    && Rs2Equipment.isWearing("Serpentine helm", false)) {
                return true;
            }
        }

        if (requirement.isEquipped() && requirement.isSlayerHelmetCovers()
                && Rs2Equipment.isWearing("slayer helmet", false)) {
            return true;
        }

        for (String name : requirement.getAcceptedNames()) {
            if (name == null || name.isBlank()) continue;
            if (requirement.isEquipped()) {
                if (Rs2Equipment.isWearing(name, false)) return true;
            } else if (Rs2Inventory.itemQuantity(name, false) >= requirement.getQuantity()) {
                return true;
            }
        }
        return false;
    }

    private String findBankItemForRequirement(DroSlayerTaskRequirements.Requirement requirement) {
        if (requirement == null || !Rs2Bank.isOpen()) return null;

        if (requirement.getPreferredName() != null
                && Rs2Bank.hasBankItem(requirement.getPreferredName(), requirement.getQuantity(), false)) {
            return requirement.getPreferredName();
        }
        for (String name : requirement.getAcceptedNames()) {
            if (name != null && !name.isBlank()
                    && Rs2Bank.hasBankItem(name, requirement.getQuantity(), false)) {
                return name;
            }
        }
        return null;
    }

    /**
     * Inventory setups are often intentionally 28/28. Sacrifice one food item when a mandatory
     * Slayer tool needs a slot; after the task setup is loaded this is the least disruptive item
     * to replace and avoids rejecting an otherwise valid setup.
     */
    private boolean ensureFreeInventorySlotForTaskItem() {
        return ensureFreeInventorySlotsForTaskItems(1);
    }

    private boolean ensureFreeInventorySlotsForTaskItems(int needed) {
        if (Rs2Inventory.emptySlotCount() >= needed) return true;
        List<Rs2ItemModel> food = taskFood();
        if (food == null || food.isEmpty()) return false;
        Rs2ItemModel sacrifice = food.get(food.size() - 1);
        if (sacrifice == null) return false;
        int expectedFree = Math.min(needed, Rs2Inventory.emptySlotCount() + 1);
        if (!DroSlayerRuntime.performAction(
                BaseProfileDro.ActionPhase.SETUP, false,
                (java.util.function.BooleanSupplier) () -> Rs2Bank.depositOne(sacrifice.getId()))) return false;
        sleepUntil(() -> Rs2Inventory.emptySlotCount() >= expectedFree, 1800);
        return Rs2Inventory.emptySlotCount() >= needed;
    }

    private boolean hasAllTaskRequirementsForCurrentTask() {
        String taskName = currentTaskName();
        for (DroSlayerTaskRequirements.Requirement requirement : currentTaskRequirements()) {
            if (!isTaskRequirementSatisfied(requirement)) {
                log.info("Missing required Slayer item '{}' for task '{}'", requirement.getLabel(), taskName);
                return false;
            }
        }
        return hasRequiredWeaknessSupplies();
    }

    /** RuneLite's Task table is the source of truth for finishing/weakness items. */
    private int getRequiredFinishingItemId() {
        String taskName = currentTaskName();
        Task task = taskName == null ? null : Task.getTask(taskName);
        if (task == null || task.getWeaknessItem() < 0) return -1;

        int itemId = task.getWeaknessItem();
        return isWeaknessAutoFinished(itemId) ? -1 : itemId;
    }

    private boolean isWeaknessAutoFinished(int itemId) {
        if (itemId == ItemID.SLAYER_ROCK_HAMMER) {
            return Microbot.getVarbitValue(SLAYER_AUTOKILL_GARGOYLES) != 0;
        }
        if (itemId == ItemID.SLAYER_BAG_OF_SALT) {
            return Microbot.getVarbitValue(SLAYER_AUTOKILL_ROCKSLUGS) != 0;
        }
        if (itemId == ItemID.SLAYER_ICY_WATER) {
            return Microbot.getVarbitValue(SLAYER_AUTOKILL_DESERTLIZARDS) != 0;
        }
        return false;
    }

    private boolean hasRequiredWeaknessSupplies() {
        int itemId = getRequiredFinishingItemId();
        if (itemId < 0) return true;

        int remaining = Math.max(1, Rs2Slayer.getSlayerTaskSize());
        if (itemId == ItemID.SLAYER_SPRAY_PUMP_0) {
            Rs2ItemModel spray = getFungicideSpray();
            int charges = spray == null ? 0 : Math.max(0, parseFungicideCharges(spray.getName()));
            int refills = Rs2Inventory.itemQuantity("Fungicide", true);
            int totalFinishes = charges + (refills * 10);
            if (totalFinishes < remaining) {
                log.info("Fungicide capacity ({}) below task remaining ({})", totalFinishes, remaining);
                return false;
            }
            return true;
        }

        int have = Rs2Inventory.itemQuantity(itemId);
        int needed = itemId == ItemID.SLAYER_ROCK_HAMMER ? 1 : remaining;
        if (have < needed) {
            log.info("Slayer weakness item {} count ({}) below required ({})", itemId, have, needed);
            return false;
        }
        return true;
    }

    private boolean ensureWeaknessItemsAtBank() {
        int itemId = getRequiredFinishingItemId();
        if (itemId < 0) return true;

        int remaining = Math.max(1, Rs2Slayer.getSlayerTaskSize());
        if (itemId == ItemID.SLAYER_SPRAY_PUMP_0) {
            return ensureFungicideSuppliesAtBank(remaining);
        }

        int have = Rs2Inventory.itemQuantity(itemId);
        int required = itemId == ItemID.SLAYER_ROCK_HAMMER ? 1 : remaining;
        int needed = Math.max(0, required - have);
        if (needed <= 0) return true;

        if (have == 0 && !ensureFreeInventorySlotForTaskItem()) {
            return false;
        }
        if (!Rs2Bank.hasBankItem(itemId, needed)) {
            log.error("Required Slayer weakness item (ID {}) x{} is not available in the bank", itemId, needed);
            terminalStopMessage = Microbot.supplyMessage(supplyName(itemId));
            stopAndLogout(true);
            return false;
        }

        log.info("Withdrawing Slayer weakness item {} x{} (have {}, task remaining {})",
                itemId, needed, have, remaining);
        if (!DroSlayerRuntime.performAction(
                BaseProfileDro.ActionPhase.SETUP, false,
                (java.util.function.BooleanSupplier) () -> Rs2Bank.withdrawX(itemId, needed))) return false;
        sleepUntil(() -> Rs2Inventory.itemQuantity(itemId) >= required, 3000);
        return Rs2Inventory.itemQuantity(itemId) >= required;
    }

    private Rs2ItemModel getFungicideSpray() {
        return Rs2Inventory.items()
                .filter(item -> item != null && item.getName() != null
                        && item.getName().toLowerCase().startsWith("fungicide spray"))
                .max(Comparator.comparingInt(item -> parseFungicideCharges(item.getName())))
                .orElse(null);
    }

    private boolean ensureFungicideSuppliesAtBank(int remaining) {
        Rs2ItemModel spray = getFungicideSpray();
        int charges = spray == null ? 0 : Math.max(0, parseFungicideCharges(spray.getName()));

        if (charges <= 0) {
            if (spray == null && !ensureFreeInventorySlotForTaskItem()) return false;

            String chargedSprayName = null;
            for (int charge = 10; charge >= 1; charge--) {
                String candidate = "Fungicide spray " + charge;
                if (Rs2Bank.hasBankItem(candidate, 1, true)) {
                    chargedSprayName = candidate;
                    break;
                }
            }
            if (chargedSprayName == null && Rs2Bank.hasBankItem("Fungicide spray")) {
                chargedSprayName = "Fungicide spray";
            }
            if (chargedSprayName == null) {
                log.error("No charged fungicide spray available for Mutated zygomites");
                terminalStopMessage = Microbot.supplyMessage("charged fungicide spray");
                stopAndLogout(true);
                return false;
            }
            final String chargedSprayToWithdraw = chargedSprayName;
            final boolean chargedSprayExactName = chargedSprayToWithdraw.matches(".*\\d+$");
            if (!DroSlayerRuntime.performAction(
                    BaseProfileDro.ActionPhase.SETUP, false,
                    (java.util.function.BooleanSupplier) () -> Rs2Bank.withdrawOne(chargedSprayToWithdraw, chargedSprayExactName))) return false;
            sleep(150, 300);
            spray = getFungicideSpray();
            charges = spray == null ? 0 : Math.max(0, parseFungicideCharges(spray.getName()));
        }

        int existingRefills = Rs2Inventory.itemQuantity("Fungicide", true);
        // Carry a spare full refill to cover wasted charges or refilling a partial spray.
        int extraFinishes = Math.max(0, remaining + 10 - charges - existingRefills * 10);
        int refillsNeeded = (extraFinishes + 9) / 10;
        if (refillsNeeded <= 0) return charges > 0;

        if (!ensureFreeInventorySlotsForTaskItems(refillsNeeded)) return false;
        if (!Rs2Bank.hasBankItem("Fungicide", refillsNeeded, true)) {
            log.error("Not enough fungicide refills for Mutated zygomites (need {})", refillsNeeded);
            terminalStopMessage = Microbot.supplyMessage("fungicide refills");
            stopAndLogout(true);
            return false;
        }
        if (!DroSlayerRuntime.performAction(
                BaseProfileDro.ActionPhase.SETUP, false,
                (java.util.function.BooleanSupplier) () -> Rs2Bank.withdrawX("Fungicide", refillsNeeded, true))) return false;
        int expectedRefills = existingRefills + refillsNeeded;
        return sleepUntil(() -> Rs2Inventory.itemQuantity("Fungicide", true) >= expectedRefills, 3000)
                && Rs2Inventory.itemQuantity("Fungicide", true) >= expectedRefills
                && hasRequiredWeaknessSupplies();
    }

    // Spellbook integer values from InventorySetup
    // 0=Standard, 1=Ancient, 2=Lunar, 3=Arceuus, 4=None
    private static final int SPELLBOOK_STANDARD = 0;
    private static final int SPELLBOOK_ANCIENT = 1;
    private static final int SPELLBOOK_LUNAR = 2;
    private static final int SPELLBOOK_ARCEUUS = 3;
    private static final int SPELLBOOK_NONE = 4;

    // Track the current inventory setup for spellbook checking
    private net.runelite.client.plugins.microbot.inventorysetups.InventorySetup currentInventorySetup = null;

    /**
     * Checks if the current inventory setup requires a spellbook swap.
     * Returns true if the inventory setup has a spellbook defined that doesn't match current.
     */
    private boolean needsSpellbookSwap() {
        if (isWildernessSlayer() && !wildernessBurstSelected()) return false;
        if (wildernessBurstSelected()) {
            currentInventorySetup = getActiveInventorySetup();
            if (!wildernessBurstSelected()) return false;
            return !Rs2Magic.isSpellbook(Rs2Spellbook.ANCIENT);
        }
        // Get the active inventory setup
        var setup = getActiveInventorySetup();
        if (setup == null) {
            log.debug("No inventory setup, no spellbook swap needed");
            return false;
        }

        // Store reference for the spellbook swap handler
        currentInventorySetup = setup;

        // Create DroInventorySetup to check spellbook
        DroInventorySetup inventorySetup = new DroInventorySetup(setup, mainScheduledFuture);

        // Check if spellbook matches - if it does, no swap needed
        if (inventorySetup.hasSpellBook()) {
            log.debug("Spellbook matches inventory setup, no swap needed");
            return false;
        }

        // Spellbook doesn't match - need to swap
        // Get the required spellbook from the inventory setup
        int requiredSpellbook = setup.getSpellBook();
        if (requiredSpellbook == SPELLBOOK_NONE || requiredSpellbook < 0) {
            log.debug("Inventory setup has no spellbook requirement (value: {})", requiredSpellbook);
            return false;
        }

        log.info("Inventory setup requires spellbook {} but player is on different spellbook - swap required",
                getSpellbookName(requiredSpellbook));
        return true;
    }

    /**
     * Gets a human-readable name for a spellbook integer value.
     */
    private String getSpellbookName(int spellbook) {
        switch (spellbook) {
            case SPELLBOOK_STANDARD: return "Standard";
            case SPELLBOOK_ANCIENT: return "Ancient";
            case SPELLBOOK_LUNAR: return "Lunar";
            case SPELLBOOK_ARCEUUS: return "Arceuus";
            default: return "Unknown (" + spellbook + ")";
        }
    }

    /**
     * Gets the target spellbook from the current inventory setup.
     * Returns null if no setup or no spellbook requirement.
     * Spellbook values: 0=Standard, 1=Ancient, 2=Lunar, 3=Arceuus, 4=None
     */
    private Rs2Spellbook getRequiredSpellbook() {
        if (wildernessBurstSelected()) return Rs2Spellbook.ANCIENT;
        if (currentInventorySetup == null) {
            return null;
        }

        int spellbook = currentInventorySetup.getSpellBook();

        // If spellbook is NONE or invalid, no requirement
        if (spellbook == SPELLBOOK_NONE || spellbook < 0) {
            return null;
        }

        // Convert InventorySetup spellbook int to Rs2Spellbook
        switch (spellbook) {
            case SPELLBOOK_ANCIENT:
                return Rs2Spellbook.ANCIENT;
            case SPELLBOOK_LUNAR:
                return Rs2Spellbook.LUNAR;
            case SPELLBOOK_ARCEUUS:
                return Rs2Spellbook.ARCEUUS;
            case SPELLBOOK_STANDARD:
                return Rs2Spellbook.MODERN;
            default:
                return null;
        }
    }

    // Occult Altar IDs for POH (Basic, Marble, Gilded, Ancient variants)
    private static final int[] OCCULT_ALTAR_IDS = {29149, 29150, 29151, 29152};

    /**
     * Handles swapping spellbook in POH based on inventory setup requirements.
     * Uses the occult altar to switch to the required spellbook.
     */
    private void handleSwappingSpellbookState() {
        traceDroAction("handleSwappingSpellbookState");
        if (dustBurstFallbackPending) { advanceDustBurstFallback(); return; }
        if (wildernessBurstSelected() && !Rs2Magic.isSpellbook(Rs2Spellbook.ANCIENT)) {
            long now = System.currentTimeMillis();
            if (dustBurstSwapStarted == 0) dustBurstSwapStarted = now;
            if (now - dustBurstSwapStarted >= 30000L) {
                fallbackDustBurstSetup("Ancient spellbook swap watchdog expired");
                return;
            }
        }
        // Direct POH/spellbook actions own travel now; release any Efficient Walker policy first.
        DroSlayerRuntime.cancelWalk();
        DroSlayerPlugin.setCurrentLocation("POH (Spellbook)");

        // Get the required spellbook from inventory setup
        Rs2Spellbook requiredSpellbook = getRequiredSpellbook();
        if (requiredSpellbook == null) {
            log.warn("No spellbook requirement found, skipping swap");
            resetPohState();
            currentInventorySetup = null;
            if (config.enableAutoTravel()) {
                DroSlayerPlugin.setState(DroSlayerState.TRAVELING);
            } else {
                DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK);
            }
            return;
        }

        // If we're already on the required spellbook, skip to traveling
        if (Rs2Magic.isSpellbook(requiredSpellbook)) {
            log.info("Already on {} spellbook, transitioning to TRAVELING", requiredSpellbook);
            resetPohState();
            currentInventorySetup = null;
            if (config.enableAutoTravel()) {
                DroSlayerPlugin.setState(DroSlayerState.TRAVELING);
            } else {
                DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK);
            }
            return;
        }

        // Check if we're in the house (house portal exists)
        boolean inHouse = isInPoh();

        if (!inHouse) {
            // Need to teleport to house
            if (!pohTeleportAttempted) {
                if (teleportToHouse()) {
                    pohTeleportAttempted = true;
                    sleepUntil(this::isInPoh, 5000);
                } else {
                    if (fallbackDustBurstSetup("Could not teleport to house for Ancient spellbook")) return;
                    log.warn("Failed to teleport to house for spellbook swap, falling back to TRAVELING");
                    resetPohState();
                    currentInventorySetup = null;
                    if (config.enableAutoTravel()) {
                        DroSlayerPlugin.setState(DroSlayerState.TRAVELING);
                    } else {
                        DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK);
                    }
                }
            } else {
                // Teleport was attempted but we're not in house - wait a bit more
                sleep(600, 1000);
                if (!isInPoh()) {
                    if (fallbackDustBurstSetup("House teleport did not arrive for Ancient spellbook")) return;
                    log.warn("Still not in house after teleport for spellbook swap, falling back");
                    resetPohState();
                    currentInventorySetup = null;
                    if (config.enableAutoTravel()) {
                        DroSlayerPlugin.setState(DroSlayerState.TRAVELING);
                    } else {
                        DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK);
                    }
                }
            }
            return;
        }

        // Check if the occult altar dialog is genuinely open (only if a "Select an Option" dialog is visible)
        if (Rs2Dialogue.hasSelectAnOption()) {
            log.info("Occult altar dialog already open, handling spellbook selection");
            if (handleSpellbookWidget(requiredSpellbook)) {
                return;
            }
        }

        // Find and interact with the occult altar
        Rs2TileObjectModel occultAltar = findOccultAltar();
        if (occultAltar != null) {
            String altarOption = getAltarOptionForSpellbook(requiredSpellbook);
            log.info("Attempting occult altar right-click option: '{}'", altarOption);

            if (DroSlayerRuntime.performAction(
                    BaseProfileDro.ActionPhase.SETUP, false,
                    (java.util.function.BooleanSupplier) () -> occultAltar.click(altarOption))) {
                sleepUntil(() -> Rs2Magic.isSpellbook(requiredSpellbook) || Rs2Dialogue.hasSelectAnOption(), 3000);

                if (Rs2Magic.isSpellbook(requiredSpellbook)) {
                    log.info("Successfully switched to {} spellbook via right-click", requiredSpellbook);
                    finishSpellbookSwap();
                    return;
                }

                if (Rs2Dialogue.hasSelectAnOption() && handleSpellbookWidget(requiredSpellbook)) {
                    return;
                }
            } else {
                log.info("Right-click option '{}' not found, trying Venerate", altarOption);
                if (DroSlayerRuntime.performAction(
                        BaseProfileDro.ActionPhase.SETUP, false,
                        (java.util.function.BooleanSupplier) () -> occultAltar.click("Venerate"))) {
                    sleepUntil(Rs2Dialogue::hasSelectAnOption, 3000);
                    if (!handleSpellbookWidget(requiredSpellbook)) {
                        log.warn("Venerate used but could not handle spellbook dialog");
                    }
                } else {
                    log.info("Trying plain interact on occult altar");
                    DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { occultAltar.click(); });
                    sleepUntil(Rs2Dialogue::hasSelectAnOption, 3000);
                    handleSpellbookWidget(requiredSpellbook);
                }
            }
        } else {
            log.warn("Could not find occult altar in house - ensure POH has an occult altar");
            spellbookSwapAttempts++;
            if (spellbookSwapAttempts >= MAX_SPELLBOOK_SWAP_ATTEMPTS) {
                if (fallbackDustBurstSetup("Occult altar unavailable after bounded attempts")) return;
                log.warn("Max spellbook swap attempts reached, skipping swap");
                spellbookSwapAttempts = 0;
                leaveHouse();
                sleepUntil(() -> !isInPoh(), 3000);
                resetPohState();
                currentInventorySetup = null;
                if (config.enableAutoTravel()) {
                    DroSlayerPlugin.setState(DroSlayerState.TRAVELING);
                } else {
                    DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK);
                }
            }
        }
    }

    /**
     * Handles a spellbook selection widget/dialog if one is open.
     * The occult altar can open a dialog with options like "Ancient Magicks", "Lunar", etc.
     * Returns true if the widget was handled (regardless of success), false if no widget found.
     */
    private boolean handleSpellbookWidget(Rs2Spellbook requiredSpellbook) {
        traceDroAction("handleSpellbookWidget");
        // Check for a "Select an Option" dialog (standard game dialog)
        if (Rs2Dialogue.hasSelectAnOption()) {
            String dialogOption = getDialogOptionForSpellbook(requiredSpellbook);
            log.info("Spellbook selection dialog detected, clicking option: '{}'", dialogOption);
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Dialogue.clickOption(dialogOption); });
            sleepUntil(() -> Rs2Magic.isSpellbook(requiredSpellbook), 3000);

            if (Rs2Magic.isSpellbook(requiredSpellbook)) {
                log.info("Successfully switched to {} spellbook via dialog", requiredSpellbook);
                finishSpellbookSwap();
            } else {
                log.warn("Dialog option clicked but spellbook didn't change");
            }
            return true;
        }

        // Check for widget-based spellbook selection (search by text)
        String widgetText = getWidgetTextForSpellbook(requiredSpellbook);
        Widget spellbookWidget = Rs2Widget.findWidget(widgetText);
        if (spellbookWidget != null) {
            log.info("Found spellbook widget with text '{}', clicking", widgetText);
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> { Rs2Widget.clickWidget(spellbookWidget); });
            sleepUntil(() -> Rs2Magic.isSpellbook(requiredSpellbook), 3000);

            if (Rs2Magic.isSpellbook(requiredSpellbook)) {
                log.info("Successfully switched to {} spellbook via widget", requiredSpellbook);
                finishSpellbookSwap();
            } else {
                log.warn("Widget clicked but spellbook didn't change");
            }
            return true;
        }

        return false;
    }

    /**
     * Completes the spellbook swap by leaving POH and transitioning to next state.
     */
    private void finishSpellbookSwap() {
        dustBurstSwapStarted = 0;
        spellbookSwapAttempts = 0;

        // Set up autocast while stationary at POH (before leaving house)
        setupAutoCastIfNeeded();

        leaveHouse();
        sleepUntil(() -> !isInPoh(), 3000);
        resetPohState();
        currentInventorySetup = null;

        if (config.enableAutoTravel()) {
            DroSlayerPlugin.setState(DroSlayerState.TRAVELING);
        } else {
            DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK);
        }
    }

    /**
     * Gets the occult altar right-click menu option for the given spellbook.
     */
    private String getAltarOptionForSpellbook(Rs2Spellbook spellbook) {
        switch (spellbook) {
            case ARCEUUS:
                return "Arceuus";
            case ANCIENT:
                return "Ancient Magicks";
            case LUNAR:
                return "Lunar";
            case MODERN:
            default:
                return "Standard";
        }
    }

    /**
     * Gets the dialog option text for selecting a spellbook in a selection dialog.
     * Uses partial match so it works with Rs2Dialogue.clickOption().
     */
    private String getDialogOptionForSpellbook(Rs2Spellbook spellbook) {
        switch (spellbook) {
            case ARCEUUS:
                return "Arceuus";
            case ANCIENT:
                return "Ancient";
            case LUNAR:
                return "Lunar";
            case MODERN:
            default:
                return "Standard";
        }
    }

    /**
     * Gets the widget text to search for when clicking a spellbook option in a widget.
     */
    private String getWidgetTextForSpellbook(Rs2Spellbook spellbook) {
        switch (spellbook) {
            case ARCEUUS:
                return "Arceuus";
            case ANCIENT:
                return "Ancient Magicks";
            case LUNAR:
                return "Lunar";
            case MODERN:
            default:
                return "Standard";
        }
    }

    /**
     * Finds the occult altar in the POH.
     */
    private Rs2TileObjectModel findOccultAltar() {
        for (int altarId : OCCULT_ALTAR_IDS) {
            Rs2TileObjectModel altar = Microbot.getRs2TileObjectCache().query().withId(altarId).nearestOnClientThread();
            if (altar != null) {
                return altar;
            }
        }
        return null;
    }

    private void handleTravelingState() {
        traceDroAction("handleTravelingState");
        DroSlayerPlugin.setCurrentLocation(taskLocationName);
        if (!hasBankEscapeTeleportInInventory()) {
            DroSlayerRuntime.cancelWalk();
            log.info("Task departure blocked: restocking escape ring");
            DroSlayerPlugin.setState(DroSlayerState.BANKING);
            return;
        }
        if (taskDestination == null) {
            log.warn("No destination set, returning to DETECTING_TASK");
            DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK);
            return;
        }

        if (handleDeepWildernessDungeonEntrance()) return;
        if (handleGodWarsEntrance()) return;

        // Check if we've arrived. Catacombs routing intentionally finishes on the
        // exact registered monster tile so Efficient Walker is not released early nearby.
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        boolean catacombsTask = taskLocationName != null
                && taskLocationName.toLowerCase().contains("catacombs");
        int arrivalRadius = catacombsTask ? 0 : ARRIVAL_DISTANCE;
        if (playerLocation != null && playerLocation.distanceTo(taskDestination) <= arrivalRadius) {
            log.info("Arrived at task location: {}", taskLocationName);
            DroSlayerRuntime.cancelWalk();
            DroSlayerPlugin.setState(DroSlayerState.AT_LOCATION);
            return;
        }

        if (DroSlayerRuntime.walkFull(config, taskDestination, arrivalRadius)) {
            DroSlayerRuntime.cancelWalk();
            DroSlayerPlugin.setState(DroSlayerState.AT_LOCATION);
            return;
        }
        handleRouteFailureBudget("task travel");
    }

    /**
     * The Wilderness dungeon's staircase is an object transition, not a walkable surface-to-
     * dungeon tile. At its known surface landing we must interact with the stairs explicitly;
     * handing the underground cannon tile to the generic walker can leave Slayer waiting at
     * the entrance until its unrelated route-failure budget expires.
     */
    private boolean handleDeepWildernessDungeonEntrance() {
        WorldPoint here = Rs2Player.getWorldLocation();
        if (!DroSlayerDeepWildernessStairs.isDungeonTask(taskLocationName, taskDestination)) {
            deepWildernessStairAttempts = 0;
            deepWildernessStairClickAt = 0;
            return false;
        }
        if (DroSlayerDeepWildernessStairs.isInside(here)) {
            if (deepWildernessStairAttempts > 0) {
                log.info("Deep Wilderness stairs confirmed underground landing={} after {} attempts",
                    here, deepWildernessStairAttempts);
                DroSlayerRuntime.clearRouteFailures();
            }
            deepWildernessStairAttempts = 0;
            deepWildernessStairClickAt = 0;
            return false;
        }
        if (!DroSlayerDeepWildernessStairs.nearSurfaceStairs(here)) return false;
        Microbot.status = "Deep Wilderness Dungeon: entering the staircase";
        // Do not issue a second click while a teleport/scene transition is still loading.
        if (here.distanceTo2D(DroSlayerDeepWildernessStairs.SURFACE) > 2) {
            DroSlayerRuntime.walkFull(config, DroSlayerDeepWildernessStairs.SURFACE, 1);
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - deepWildernessStairClickAt < 5000L) return true;
        // A queued click must not race the final walk/animation into the stair tile.
        boolean busy = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var player = Microbot.getClient().getLocalPlayer();
            return player == null || player.getAnimation() != -1 || Rs2Player.isMoving();
        }).orElse(true);
        if (busy) return true;
        if (deepWildernessStairAttempts >= Math.max(3, config.maxRecoveryAttempts())) {
            holdWildernessTask("Deep Wilderness stair did not load the dungeon after "
                + deepWildernessStairAttempts + " attempts; task retained");
            return true;
        }
        DroSlayerRuntime.cancelWalk();
        var stairs = Microbot.getClientThread().runOnClientThreadOptional(() ->
            Microbot.getRs2TileObjectCache().query()
                .withId(DroSlayerDeepWildernessStairs.STAIRCASE_OBJECT_ID)
                .nearestOnClientThread(DroSlayerDeepWildernessStairs.SURFACE, 8)).orElse(null);
        deepWildernessStairClickAt = now;
        deepWildernessStairAttempts++;
        if (stairs == null) {
            log.warn("Deep Wilderness stairs unavailable at={} expectedObject={} attempt={}",
                here, DroSlayerDeepWildernessStairs.STAIRCASE_OBJECT_ID, deepWildernessStairAttempts);
            return true;
        }
        log.info("Deep Wilderness stairs click player={} object={} tile={} attempt={}",
            here, stairs.getId(), stairs.getWorldLocation(), deepWildernessStairAttempts);
        DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.ACTIVE_WORK, false,
            () -> { stairs.click("Climb-down"); });
        // Success is acknowledged only on a later tick after observing an underground tile.
        return true;
    }

    /** Split GWD entry from the final monster route so banking only plans to the surface cave. */
    private boolean handleGodWarsEntrance() {
        traceDroAction("handleGodWarsEntrance");
        if (!isWildernessSlayer() || taskLocationName == null
                || !taskLocationName.contains("Wilderness God Wars")) return false;
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return true;
        var step = DroSlayerGodWarsAccess.step(player);
        if (step != godWarsAccessStep) {
            DroSlayerRuntime.cancelWalk();
            godWarsAccessStep = step;
            godWarsAccessClickAt = 0;
            godWarsAccessAttempts = 0;
            log.info("Wilderness God Wars entrance step={}", step);
        }
        if (step == DroSlayerGodWarsAccess.Step.INSIDE) return false;
        var approach = DroSlayerGodWarsAccess.approach(step);
        String action = DroSlayerGodWarsAccess.action(step);
        String name = DroSlayerGodWarsAccess.objectName(step);
        Microbot.status = "GWD entrance: " + action + " " + name;
        if (step == DroSlayerGodWarsAccess.Step.CAVE && player.distanceTo(approach) > 10) {
            DroSlayerRuntime.walkFull(config, approach, 3);
            handleRouteFailureBudget("GWD entrance approach");
            return true;
        }
        // Check animation before issuing either movement or an object action.
        boolean busy = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var local = Microbot.getClient().getLocalPlayer();
            return local == null || local.getAnimation() != -1 || Rs2Player.isMoving();
        }).orElse(true);
        if (busy) {
            Microbot.status = "GWD entrance: waiting for movement/animation before " + action + " " + name;
            if (System.currentTimeMillis() - godWarsAccessDiagnosticAt >= 8000L) {
                godWarsAccessDiagnosticAt = System.currentTimeMillis();
                Microbot.getClientThread().runOnClientThreadOptional(() -> {
                    var local = Microbot.getClient().getLocalPlayer();
                    log.info("GWD entrance waiting step={} player={} animation={} moving={}",
                        step, player, local == null ? null : local.getAnimation(), Rs2Player.isMoving());
                    return true;
                });
            }
            return true;
        }
        if (System.currentTimeMillis() - godWarsAccessClickAt < 4000L) return true;
        if (step == DroSlayerGodWarsAccess.Step.BOULDER) {
            var boulder = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getRs2NpcCache().query().withId(6621)
                    .within(approach, 16).nearestOnClientThread(approach, 16)).orElse(null);
            DroSlayerRuntime.cancelWalk();
            if (boulder == null) {
                godWarsAccessClickAt = System.currentTimeMillis();
                godWarsAccessAttempts++;
                Microbot.status = "GWD entrance: waiting for Boulder NPC";
                log.warn("GWD entrance missing Boulder NPC 6621 player={}", player);
            } else if (godWarsAccessAttempts < Math.max(3, config.maxRecoveryAttempts())) {
                DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.ACTIVE_WORK, false, () -> {
                    godWarsAccessClickAt = System.currentTimeMillis();
                    godWarsAccessAttempts++;
                    return boulder.click("Move");
                });
                return true;
            }
            if (godWarsAccessAttempts >= Math.max(3, config.maxRecoveryAttempts()))
                holdWildernessTask("GWD entrance could not Move Boulder NPC; check the entrance");
            return true;
        }
        var object = Microbot.getClientThread().runOnClientThreadOptional(() ->
            Microbot.getRs2TileObjectCache().query()
                .where(candidate -> name.equalsIgnoreCase(candidate.getName()))
                .where(candidate -> candidate.getWorldLocation().distanceTo(approach) <= 16)
                .where(candidate -> SceneObjects.hasAction(candidate, action, true))
                .nearestOnClientThread(approach, 16)).orElse(null);
        if (object == null || player.distanceTo(object.getWorldLocation()) > 20) {
            if (step == DroSlayerGodWarsAccess.Step.CAVE && player.distanceTo(approach) > 4) {
                DroSlayerRuntime.walkFull(config, approach, 3);
                handleRouteFailureBudget("GWD entrance approach");
                return true;
            }
            // The entrance obstacles are loaded in this small cave. A full-travel request
            // to their blocked object tiles can teleport outside to re-enter the cave.
            DroSlayerRuntime.cancelWalk();
            godWarsAccessClickAt = System.currentTimeMillis();
            godWarsAccessAttempts++;
            Microbot.getClientThread().runOnClientThreadOptional(() -> {
                var nearby = Microbot.getRs2TileObjectCache().query()
                    .where(candidate -> candidate.getWorldLocation().distanceTo(player) <= 32)
                    .where(candidate -> "Boulder".equalsIgnoreCase(candidate.getName())
                        || "Crevice".equalsIgnoreCase(candidate.getName()))
                    .toListOnClientThread();
                String details = nearby.stream().limit(8).map(candidate -> {
                    var composition = candidate.getObjectComposition();
                    return candidate.getId() + ":" + candidate.getName() + "@" + candidate.getWorldLocation()
                        + " actions=" + (composition == null ? "unavailable"
                            : java.util.Arrays.toString(composition.getActions()));
                }).collect(java.util.stream.Collectors.joining("; "));
                log.warn("GWD entrance lookup failed step={} player={} anchor={} candidates=[{}]",
                    step, player, approach, details);
                return true;
            });
            Microbot.status = "GWD entrance: looking for " + name + " with " + action;
            if (godWarsAccessAttempts >= Math.max(3, config.maxRecoveryAttempts()))
                holdWildernessTask("GWD entrance could not find " + name + "; check the entrance");
            return true;
        }
        DroSlayerRuntime.cancelWalk();
        if (godWarsAccessAttempts >= Math.max(3, config.maxRecoveryAttempts())) {
            holdWildernessTask("GWD entrance could not " + action + " " + name + "; check the entrance");
            return true;
        }
        DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.ACTIVE_WORK, false, () -> {
            godWarsAccessClickAt = System.currentTimeMillis();
            godWarsAccessAttempts++;
            Microbot.getClientThread().runOnClientThreadOptional(() -> {
                log.info("GWD entrance click step={} player={} object={} at={} action={}",
                    step, player, object.getId(), object.getWorldLocation(), action);
                return true;
            });
            return object.click(action);
        });
        return true;
    }

    private void handleAtLocationState(boolean hasTask, int remaining) {
        traceDroAction("handleAtLocationState");
        DroSlayerPlugin.setCurrentLocation(taskLocationName);
        if (!hasTask || remaining <= 0) {
            holdDepartureProtection();
            if (isWildernessSlayer()) maintainWildernessEscapeSurvival();
            log.info("Task complete or no task");
            if (cannonPlaced) {
                if (!handleCannonPickup()) return;
                cannonPlaced = false;
                cannonFired = false;
            }
            resetTravelData();
            initialSetupDone = false;

            finishTaskAndChooseNextStop();
            return;
        }

        if (!hasWildernessCombatLoadout()) return;

        if (handleEmergencyLowHpEscape("at task location")) {
            return;
        }

        if (cannonPlacementRejected && cannonBuildStage() == 0) {
            cannonPlacementRejected = false;
            rejectedCannonTiles.add(lastCannonPlacementTile);
            WorldPoint anchor = taskDestination != null ? taskDestination : cannonSpot;
            WorldPoint replacement = null;
            if (anchor != null && rejectedCannonTiles.size() < 8) {
                for (int radius = 1; radius <= 4 && replacement == null; radius++) {
                    for (int dx = -radius; dx <= radius && replacement == null; dx++) {
                        for (int dy = -radius; dy <= radius; dy++) {
                            WorldPoint candidate = new WorldPoint(anchor.getX() + dx, anchor.getY() + dy, anchor.getPlane());
                            if (!rejectedCannonTiles.contains(candidate)) { replacement = candidate; break; }
                        }
                    }
                }
            }
            if (replacement == null) {
                disableCannonForTrip("Cannon placement rejected repeatedly");
            } else {
                cannonSpot = replacement;
                cannonAssemblyProgressAt = 0;
                log.info("Cannon placement rejected at {}; trying nearby tile {}", lastCannonPlacementTile, replacement);
            }
        }
        // Never interrupt the cannon assembly animation to replace an optional bracelet.
        int assembly = cannonBuildStage();
        if (assembly == 0 || assembly == 4)
            DroSlayerBracelets.equipCarried(activeJsonProfile, isWildernessSlayer());
        cannonPolicy.leaveBank();
        if (isUsingCannon && cannonBuildStage() == 0
                && !shouldRestockCannonballs()
                && !DroSlayerCannonPolicy.canDeploy(hasCannonParts(), getCannonballCount())) {
            disableCannonForTrip("Optional cannon parts or ammunition missing at task area");
        }
        if (config.enableAutoCombat() && cannonPickupStarted == 0L && (assembly == 0 || assembly == 4)
                && (isUsingCannon && cannonPlaced || isWildernessChaosDruidTask() || isEntTask()))
            setAutoRetaliateHumanized(!isElderChaosDruidLocation());

        // Protect before cannon placement or other setup can delay combat handling.
        activateProfilePrayer();

        // Place cannon if using cannon and not yet placed
        if (isUsingCannon && !cannonPlaced && cannonSpot != null
                && (cannonBuildStage() > 0 || !shouldRestockCannonballs())) {
            WorldPoint playerLocation = Rs2Player.getWorldLocation();
            if (cannonBuildStage() > 0 || (playerLocation != null && playerLocation.distanceTo(cannonSpot) == 0)) {
                DroSlayerRuntime.cancelWalk();
                if (cannonBuildStage() > 0) cannonPlacementRejected = false;
                if (!advanceCannonAssembly()) return;

            } else {
                // Keep this local to the task environment; the bridge itself avoids resubmission spam.
                log.debug("Walking to cannon spot, distance: {}", playerLocation != null ? playerLocation.distanceTo(cannonSpot) : "unknown");
                DroSlayerRuntime.walkAdaptive(config, cannonSpot, 0, taskLocationName);
                handleRouteFailureBudget("cannon spot");
                return;
            }
        }

        if (isUsingCannon && cannonPlaced && (isWildernessSlayer() || !cannonFired)) handleCannonMaintenance();

        checkAndDisableCannonIfLow();

        // Check if we should use POH for restoration (before banking)
        if (config.usePohPool() && config.usePohBeforeBanking() && needsPohRestoration()) {
            log.info("Low HP, restoring at POH");
            stateAfterPoh = DroSlayerState.AT_LOCATION; // Return to location after POH
            DroSlayerPlugin.setState(DroSlayerState.RESTORING_AT_POH);
            return;
        }

        // Check if we need to bank for supplies
        if (config.enableAutoBanking() && needsBanking()) {
            // Pick up cannon before leaving - it can't be picked up from the bank
            if (cannonPlaced) {
                log.info("Picking up cannon before banking (at location)");
                if (!handleCannonPickup()) {
                    return; // Cannon pickup in progress
                }
                cannonPlaced = false;
                cannonFired = false;
            }
            // Go to POH first to heal before banking (saves supplies)
            if (config.usePohPool() && shouldUsePoh()) {
                log.info("Low on supplies - healing at POH before banking");
                stateAfterPoh = DroSlayerState.BANKING;
                DroSlayerPlugin.setState(DroSlayerState.RESTORING_AT_POH);
            } else {
                log.info("Low on supplies, transitioning to BANKING");
                DroSlayerPlugin.setState(DroSlayerState.BANKING);
            }
            return;
        }

        // Set up autocast before entering combat (player is stationary at task location)
        setupAutoCastIfNeeded();

        // Transition to fighting if auto combat is enabled
        if (config.enableAutoCombat()) {
            DroSlayerPlugin.setState(DroSlayerState.FIGHTING);
        }
    }

    /** A safe-area teleport during Wilderness Slayer retains the assignment and restock intent. */
    private boolean recoverDisplacedTask(boolean hasTask, int remaining) {
        if (!isWildernessSlayer() || Rs2Pvp.isInWilderness()) return false;
        if (!hasTask || remaining <= 0 || taskDestination == null || !config.enableAutoTravel()) return false;
        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null || Rs2Player.isMoving() || Rs2Player.isInCombat()) return false;
        if (here.getPlane() == taskDestination.getPlane()
                && here.distanceTo2D(taskDestination) <= Math.max(64, config.attackRadius() + 10)) return false;
        boolean replenish = config.enableAutoBanking() && config.bankForCannonballs()
                && remaining >= 10 && shouldUseCannon(currentTaskName(), true)
                && getCannonballCount() < DroSlayerCannonPolicy.restockThreshold(isWildernessSlayer(), config.cannonballThreshold());
        boolean bank = config.enableAutoBanking() && (replenish || needsBanking());
        DroSlayerRuntime.cancelWalk();
        DroSlayerRuntime.clearRouteFailures();
        baseBankLoadComplete = false;
        DroSlayerPlugin.setState(bank ? DroSlayerState.BANKING : DroSlayerState.TRAVELING);
        log.info("Task relocation observed: position={} destination={} remaining={} cannonRestock={} nextState={}; assignment retained",
                here, taskDestination, remaining, replenish, bank ? "BANKING" : "TRAVELING");
        return true;
    }

    private void handleFightingState(boolean hasTask, int remaining) {
        traceDroAction("handleFightingState");
        DroSlayerPlugin.setCurrentLocation(taskLocationName);
        cannonPolicy.leaveBank();
        if (recoverDisplacedTask(hasTask, remaining)) return;
        // An active-task pickup was requested for banking. Preserve that intent even if
        // returned loaded ammunition would now make needsBanking() false.
        if (cannonPickupStarted != 0L && hasTask && remaining > 0) {
            if (isWildernessSlayer()) maintainWildernessEscapeSurvival();
            if (!handleCannonPickup()) return;
            DroSlayerPlugin.setState(DroSlayerState.BANKING);
            return;
        }
        int assembly = cannonBuildStage();
        if (config.enableAutoCombat() && cannonPickupStarted == 0L && (assembly == 0 || assembly == 4)
                && (isUsingCannon || isWildernessChaosDruidTask() || isEntTask()))
            setAutoRetaliateHumanized(!isElderChaosDruidLocation());
        // Check if task is complete
        if (!hasTask || remaining <= 0) {
            holdDepartureProtection();
            if (isWildernessSlayer()) maintainWildernessEscapeSurvival();
            // Start the loot delay timer if not already started
            if (!taskCompletedLooting) {
                log.info("Task complete - looting for {} seconds before transitioning",
                        TASK_COMPLETE_LOOT_DELAY_MS / 1000);
                taskCompletedLooting = true;
                taskCompletedTime = System.currentTimeMillis();
                deactivateProfilePrayer();
            }

            // Continue looting during the delay period
            long timeSinceComplete = System.currentTimeMillis() - taskCompletedTime;
            if (timeSinceComplete < TASK_COMPLETE_LOOT_DELAY_MS) {
                // Handle looting during the delay
                if (config.enableLooting()) {
                    handleLooting();
                }
                return;
            }

            // Loot delay complete, now transition
            log.info("Loot delay complete, transitioning");

            // Pick up cannon if placed - must complete before transitioning
            if (cannonPlaced) {
                if (!handleCannonPickup()) {
                    // Cannon pickup in progress, don't transition yet
                    return;
                }
                // Cannon picked up successfully
                cannonPlaced = false;
                cannonFired = false;
            }

            resetTravelData();
            initialSetupDone = false;

            finishTaskAndChooseNextStop();
            return;
        }

        // Reset task completed flag if task is not complete (e.g., got a new task)
        if (taskCompletedLooting) {
            taskCompletedLooting = false;
        }

        if (handleEmergencyLowHpEscape("fighting")) {
            return;
        }

        if (restoreCombatWeaponAfterTravel()) return;
        setupAutoCastIfNeeded();

        // IMMEDIATELY activate prayer when in FIGHTING state
        // This ensures prayer is on as soon as we enter combat area
        activateProfilePrayer();

        // Ensure flicker script has the prayer actually selected in config/task profile.
        DroSlayerPrayer combatPrayer = getActiveProtectionPrayer();
        if (combatPrayer != DroSlayerPrayer.NONE) {
            DroSlayerPlugin.getFlickerScript().setActivePrayer(combatPrayer);
        } else {
            DroSlayerPlugin.getFlickerScript().clearActiveProfile();
        }

        // Own the tick while approaching/restoring at the local altar so combat cannot interrupt it.
        if (restoreElderTemplePrayer()) return;

        // Handle drinking potions EARLY - before combat checks
        // This ensures super antifire, combat potions, etc. are active
        handlePotions();

        // Handle fungicide spray refill (for zygomite tasks)
        handleFungicideRefill();

        // Handle slayer finishing items (ice cooler, bag of salt, rock hammer)
        handleSlayerFinishingItems();

        // Handle eating food BEFORE POH check
        // This ensures we eat food first, only use POH if still low HP after eating
        boolean ateFood = handleEating();

        // Handle cannon maintenance (repair and refill)
        if (cannonPlaced && handleCannonMaintenance()) return;

        checkAndDisableCannonIfLow();

        // Check if we should use POH for restoration (before banking)
        // Skip POH if we just ate food - give it time to heal before teleporting
        // POH acts as a "panic teleport" if HP drops dangerously low even after eating
        if (!ateFood && config.usePohPool() && config.usePohBeforeBanking() && needsPohRestoration()) {
            log.info("Low HP - using POH as panic teleport");
            deactivateProfilePrayer();
            stateAfterPoh = DroSlayerState.TRAVELING; // Return to task after POH
            DroSlayerPlugin.setState(DroSlayerState.RESTORING_AT_POH);
            return;
        }

        DroSlayerBracelets.equipCarried(activeJsonProfile, isWildernessSlayer());

        // Check if we need to bank
        if (config.enableAutoBanking() && needsBanking()) {
            // Pick up cannon before leaving - it can't be picked up from the bank
            if (cannonPlaced) {
                log.info("Picking up cannon before banking");
                if (!handleCannonPickup()) {
                    return; // Cannon pickup in progress
                }
                cannonPlaced = false;
                cannonFired = false;
            }
            deactivateProfilePrayer();
            // Go to POH first to heal before banking (saves supplies)
            if (config.usePohPool() && shouldUsePoh()) {
                log.info("Low on supplies - healing at POH before banking");
                stateAfterPoh = DroSlayerState.BANKING;
                DroSlayerPlugin.setState(DroSlayerState.RESTORING_AT_POH);
            } else {
                log.info("Low on supplies, transitioning to BANKING");
                DroSlayerPlugin.setState(DroSlayerState.BANKING);
            }
            return;
        }

        // Handle looting (combat check is done inside handleLooting based on forceLoot setting)
        if (config.enableLooting()) {
            handleLooting();
        }

        if (rotateEntWorld() || rotateRevenantWorld()) return;

        // With a deployed cannon, let auto-retaliate choose targets. Manual attacks
        // can pull us out of position or interrupt the fight selected by retaliation.
        if (!isWildernessChaosDruidTask()
                && DroSlayerCannonPolicy.suppressManualAttacks(isUsingCannon, cannonPlaced, DroCannon.isFiring())
                && Microbot.getVarbitPlayerValue(net.runelite.api.gameval.VarPlayerID.OPTION_NODEF) == 0) {
            return;
        }

        // Handle combat
        handleCombat();
    }

    /**
     * Handles eating food when HP is low.
     * @return true if food was eaten, false otherwise
     */
    private boolean handleEating() {
        traceDroAction("handleEating");
        double healthPercent = Rs2Player.getHealthPercentage();
        if (healthPercent < config.eatAtHealthPercent()) {
            List<Rs2ItemModel> food = usableFood();
            if (!food.isEmpty()) {
                Rs2ItemModel foodItem = food.get(0);
                if (DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, false, () -> Rs2Inventory.interact(foodItem, "Eat"))) {
                    log.info("Eating {} at {}% HP", foodItem.getName(), healthPercent);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean restoreElderTemplePrayer() {
        traceDroAction("restoreElderTemplePrayer");
        if (!isElderChaosDruidLocation() || DroSlayerRuntime.isWildernessPlayerAttackActive()) return false;
        long now = System.currentTimeMillis();
        int prayer = Rs2Player.getBoostedSkillLevel(Skill.PRAYER);
        int threshold = Math.max(10, wildernessPrayerThreshold());
        if (prayer >= threshold) { templeRestoreDeadline = 0; return false; }
        if (now < templeRestoreCooldown) return false;
        if (templeRestoreDeadline != 0 && now >= templeRestoreDeadline) {
            log.warn("Chaos Temple altar restore timed out; falling back to prayer supplies");
            templeRestoreDeadline = 0;
            templeRestoreCooldown = now + 30000L;
            return false;
        }
        var altar = Microbot.getClientThread().runOnClientThreadOptional(() ->
            Microbot.getRs2TileObjectCache().query()
                .where(object -> "Altar".equalsIgnoreCase(object.getName())
                        || "Chaos altar".equalsIgnoreCase(object.getName()))
                .where(object -> taskDestination != null && object.getWorldLocation().distanceTo(taskDestination) <= 15)
                .where(object -> SceneObjects.hasAction(object, "Pray-at", true))
                .firstOnClientThread()).orElse(null);
        if (altar == null) return false;
        if (templeRestoreDeadline == 0) {
            templeRestoreDeadline = now + 10000L;
            log.info("Restoring prayer at Chaos Temple altar: points={} threshold={}", prayer, threshold);
        }
        if (!Rs2Player.isMoving() && now - templeRestoreLastClick >= 3500L) {
            templeRestoreLastClick = now;
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, false,
                () -> altar.click("Pray-at"));
        }
        return true;
    }

    private void handlePotions() {
        traceDroAction("handlePotions");
        // Only drink one potion per tick - return after each successful drink

        // Handle prayer potions
        int prayerThreshold = wildernessPrayerThreshold();
        if (DroSlayerRuntime.isWildernessPlayerAttackActive() || config.prayerFlickStyle() != PrayerFlickStyle.OFF || prayerThreshold > 0) {
            int prayerPoints = Rs2Player.getBoostedSkillLevel(Skill.PRAYER);
            if (prayerPoints < prayerThreshold) {
                Rs2ItemModel prayerPotion = Rs2Inventory.get(item ->
                        DroSlayerWildernessSupplies.isPrayerPotion(item, Rs2Pvp.isInWilderness()));
                if (prayerPotion != null) {
                    if (DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, false, () -> Rs2Inventory.interact(prayerPotion, "Drink"))) {
                        log.info("Drinking {} at {} prayer points", prayerPotion.getName(), prayerPoints);
                        return; // One potion per tick
                    }
                }
            }
        }

        // Handle combat potions (super combat, divine, ranging, magic, etc.)
        if (config.useCombatPotions()) {
            // Check melee potions - uses Attack skill
            int attackLevel = Rs2Player.getRealSkillLevel(Skill.ATTACK);
            int boostedAttack = Rs2Player.getBoostedSkillLevel(Skill.ATTACK);
            if (boostedAttack <= attackLevel) {
                Rs2ItemModel meleePotion = Rs2Inventory.get(item ->
                        item != null && item.getName() != null &&
                        (item.getName().toLowerCase().contains("super combat") ||
                         item.getName().toLowerCase().contains("divine super combat") ||
                         item.getName().toLowerCase().contains("super attack")));
                if (meleePotion != null) {
                    if (DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, false, () -> Rs2Inventory.interact(meleePotion, "Drink"))) {
                        log.info("Drinking {} (attack boost worn off)", meleePotion.getName());
                        return; // One potion per tick
                    }
                }
            }

            // Check ranging potions - uses Ranged skill
            int rangedLevel = Rs2Player.getRealSkillLevel(Skill.RANGED);
            int boostedRanged = Rs2Player.getBoostedSkillLevel(Skill.RANGED);
            if (boostedRanged <= rangedLevel) {
                Rs2ItemModel rangingPotion = Rs2Inventory.get(item ->
                        item != null && item.getName() != null &&
                        (item.getName().toLowerCase().contains("ranging potion") ||
                         item.getName().toLowerCase().contains("divine ranging") ||
                         item.getName().toLowerCase().contains("bastion potion") ||
                         item.getName().toLowerCase().contains("divine bastion")));
                if (rangingPotion != null) {
                    if (DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, false, () -> Rs2Inventory.interact(rangingPotion, "Drink"))) {
                        log.info("Drinking {} (ranged boost worn off)", rangingPotion.getName());
                        return; // One potion per tick
                    }
                }
            }

            // Check magic potions - uses Magic skill
            int magicLevel = Rs2Player.getRealSkillLevel(Skill.MAGIC);
            int boostedMagic = Rs2Player.getBoostedSkillLevel(Skill.MAGIC);
            if (boostedMagic <= magicLevel) {
                Rs2ItemModel magicPotion = Rs2Inventory.get(item ->
                        item != null && item.getName() != null &&
                        (item.getName().toLowerCase().contains("magic potion") ||
                         item.getName().toLowerCase().contains("divine magic") ||
                         item.getName().toLowerCase().contains("battlemage potion") ||
                         item.getName().toLowerCase().contains("divine battlemage")));
                if (magicPotion != null) {
                    if (DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, false, () -> Rs2Inventory.interact(magicPotion, "Drink"))) {
                        log.info("Drinking {} (magic boost worn off)", magicPotion.getName());
                        return; // One potion per tick
                    }
                }
            }
        }

        // Handle antipoison when poisoned - check poison status first
        // Poison varp: 0 = not poisoned, >0 and <1000000 = poisoned, >1000000 = venomed
        int poisonVarp = Microbot.getClient().getVarpValue(102);
        if (poisonVarp > 0 && poisonVarp < 1000000) {
            // Actually poisoned - try to cure
            if (Rs2Player.drinkAntiPoisonPotion()) {
                log.info("Drank antipoison (poison varp={})", poisonVarp);
                return; // One potion per tick
            } else {
                // Fallback: manually check for poison and drink antidote
                if (handleAntipoison()) {
                    return; // One potion per tick
                }
            }
        }

        // Handle antivenom when venomed (poison varp > 1000000)
        if (poisonVarp > 1000000) {
            if (handleAntivenom()) {
                return; // One potion per tick
            }
        }

        // Handle goading potions (from JSON profile) - for burst/barrage tasks
        // Rs2Player.drinkGoadingPotion() checks if goaded buff has worn off
        if (activeJsonProfile != null && activeJsonProfile.shouldUseGoading()) {
            if (Rs2Player.drinkGoadingPotion()) {
                log.info("Drank goading potion (task profile requires goading)");
                return; // One potion per tick
            }
        }

        // Dragonfire safety is automatic from the task/location registry. Explicit JSON
        // superAntifire=true still forces the same behavior for custom profiles.
        String currentTask = currentTaskName();
        boolean needsAntifire = (activeJsonProfile != null && activeJsonProfile.isSuperAntifire())
                || DroSlayerTaskRequirements.requiresDragonfireProtection(currentTask, taskLocationName);
        if (needsAntifire) {
            handleSuperAntifire();
        }
    }

    /**
     * Handles refilling the fungicide spray during zygomite tasks.
     * The auto-spray slayer perk consumes charges automatically, so we just
     * need to use fungicide on the spray when charges get low.
     */
    private void handleFungicideRefill() {
        traceDroAction("handleFungicideRefill");
        // Find fungicide spray in inventory
        Rs2ItemModel spray = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                item.getName().toLowerCase().startsWith("fungicide spray"));

        if (spray == null) {
            return; // No spray in inventory, not a zygomite task
        }

        // Parse charge count from name (e.g., "Fungicide spray 5")
        int charges = parseFungicideCharges(spray.getName());
        if (charges > 2) {
            return; // Spray still has enough charges
        }

        // Find fungicide refill in inventory
        Rs2ItemModel fungicide = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                item.getName().equalsIgnoreCase("fungicide"));

        if (fungicide == null) {
            log.debug("Fungicide spray low ({} charges) but no fungicide refills in inventory", charges);
            return;
        }

        log.info("Refilling fungicide spray ({} charges)", charges);
        if (DroSlayerRuntime.performAction(
                BaseProfileDro.ActionPhase.ACTIVE_WORK, false,
                () -> Rs2Inventory.combine(fungicide, spray))) {
            sleep(600, 900);
        }
    }

    /**
     * Parses the charge count from a fungicide spray item name.
     * Expected format: "Fungicide spray X" where X is 0-10.
     */
    private int parseFungicideCharges(String name) {
        if (name == null) return -1;
        int end = name.length() - 1;
        while (end >= 0 && !Character.isDigit(name.charAt(end))) end--;
        if (end < 0) return -1;
        int start = end;
        while (start > 0 && Character.isDigit(name.charAt(start - 1))) start--;
        try {
            return Integer.parseInt(name.substring(start, end + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static <T> List<T> sortBySnapshotKeys(List<T> items, java.util.function.ToIntFunction<T> primary, java.util.function.ToIntFunction<T> secondary) {
        List<int[]> keyed = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            T item = items.get(i);
            keyed.add(new int[]{primary.applyAsInt(item), secondary.applyAsInt(item), i});
        }
        keyed.sort(Comparator.<int[]>comparingInt(k -> k[0]).thenComparingInt(k -> k[1]));
        List<T> sorted = new ArrayList<>(keyed.size());
        for (int[] k : keyed) {
            sorted.add(items.get(k[2]));
        }
        return sorted;
    }

    private static final List<String> LIZARD_VARIANTS = Arrays.asList("Lizard", "Desert Lizard", "Small Lizard");

    /**
     * Applies the current task's RuneLite weakness/finishing item when a target reaches
     * the finisher state. Auto-finisher unlocks are filtered by getRequiredFinishingItemId().
     */
    private void handleSlayerFinishingItems() {
        traceDroAction("handleSlayerFinishingItems");
        final Player localPlayer = Microbot.getClient().getLocalPlayer();
        if (localPlayer == null) return;

        int finishingItemId = getRequiredFinishingItemId();
        if (finishingItemId < 0) return;

        Rs2NpcModel npc = Microbot.getRs2NpcCache().query()
                .where(n -> n.isDead() && java.util.Objects.equals(n.getInteracting(), localPlayer))
                .firstOnClientThread();
        if (npc == null) return;
        String npcName = npc.getName();
        if (npcName == null) return;
        net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel legacyNpc =
                new net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel(npc.getNpc());

        if (finishingItemId == ItemID.SLAYER_ICY_WATER
                && LIZARD_VARIANTS.contains(npcName) && npc.getHealthRatio() < 5) {
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.ACTIVE_WORK, true, () -> Rs2Inventory.useItemOnNpc(ItemID.SLAYER_ICY_WATER, legacyNpc));
            Rs2Player.waitForAnimation();
            return;
        }
        if (finishingItemId == ItemID.SLAYER_BAG_OF_SALT
                && "Rockslug".equalsIgnoreCase(npcName) && npc.getHealthRatio() < 5) {
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.ACTIVE_WORK, true, () -> Rs2Inventory.useItemOnNpc(ItemID.SLAYER_BAG_OF_SALT, legacyNpc));
            Rs2Player.waitForAnimation();
            return;
        }
        if (finishingItemId == ItemID.SLAYER_ROCK_HAMMER
                && ("Gargoyle".equalsIgnoreCase(npcName)
                    || "Dusk".equalsIgnoreCase(npcName)
                    || "Dawn".equalsIgnoreCase(npcName))
                && npc.getHealthRatio() < 3) {
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.ACTIVE_WORK, true, () -> Rs2Inventory.useItemOnNpc(ItemID.SLAYER_ROCK_HAMMER, legacyNpc));
            Rs2Player.waitForAnimation();
            return;
        }
        if (finishingItemId == ItemID.SLAYER_SPRAY_PUMP_0
                && (npcName.toLowerCase().contains("zygomite")
                    || npcName.toLowerCase().contains("fungi"))
                && npc.getHealthRatio() < 5) {
            Rs2ItemModel spray = getFungicideSpray();
            if (spray != null && parseFungicideCharges(spray.getName()) > 0) {
                DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.ACTIVE_WORK, true, () -> Rs2Inventory.useItemOnNpc(spray.getId(), legacyNpc));
                Rs2Player.waitForAnimation();
            }
        }
    }

    /**
     * Varbit for antifire protection.
     * 0 = no protection, >0 = protected (ticks remaining)
     */
    private static final int ANTIFIRE_VARBIT = 3981;
    private static final int SUPER_ANTIFIRE_VARBIT = 6101;

    /**
     * Handles drinking super antifire potions when protection wears off.
     * Checks the antifire varbit to see if we still have protection.
     */
    private void handleSuperAntifire() {
        traceDroAction("handleSuperAntifire");
        // Check if we have super antifire protection active
        int superAntifireTimer = Microbot.getVarbitValue(SUPER_ANTIFIRE_VARBIT);
        int regularAntifireTimer = Microbot.getVarbitValue(ANTIFIRE_VARBIT);

        // If we have protection, no need to drink
        if (superAntifireTimer > 0 || regularAntifireTimer > 0) {
            return;
        }

        // Try extended super antifire first (longer duration)
        Rs2ItemModel extendedSuperAntifire = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                item.getName().toLowerCase().contains("extended super antifire"));

        if (extendedSuperAntifire != null) {
            if (DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, false, () -> Rs2Inventory.interact(extendedSuperAntifire, "Drink"))) {
                log.info("Drinking {} for dragon protection", extendedSuperAntifire.getName());
            }
            return;
        }

        // Fall back to regular super antifire
        Rs2ItemModel superAntifire = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                item.getName().toLowerCase().contains("super antifire") &&
                !item.getName().toLowerCase().contains("extended"));

        if (superAntifire != null) {
            if (DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, false, () -> Rs2Inventory.interact(superAntifire, "Drink"))) {
                log.info("Drinking {} for dragon protection", superAntifire.getName());
            }
            return;
        }

        // Fall back to regular antifire if no super antifire available
        Rs2ItemModel antifire = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                (item.getName().toLowerCase().contains("antifire") ||
                 item.getName().toLowerCase().contains("anti-fire")) &&
                !item.getName().toLowerCase().contains("super"));

        if (antifire != null) {
            if (DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, false, () -> Rs2Inventory.interact(antifire, "Drink"))) {
                log.info("Drinking {} for dragon protection (no super antifire)", antifire.getName());
            }
        } else {
            log.warn("No antifire potions in inventory for dragon task!");
        }
    }

    /**
     * Handles drinking antivenom when envenomed.
     * Uses poison varbit value > 1000000 to detect venom (poison is < 1000000).
     * Supports antivenom, antivenom+, and anti-venom+ potions.
     * @return true if a potion was drunk, false otherwise
     */
    private boolean handleAntivenom() {
        traceDroAction("handleAntivenom");
        // Check if envenomed - venom is poison varbit > 1000000
        int poisonVarp = Microbot.getClient().getVarpValue(102); // VarPlayerID.POISON
        if (poisonVarp <= 1000000) {
            // Not venomed - if still poisoned, let antipoison handle it
            return false;
        }

        // Try antivenom potions first
        Rs2ItemModel antivenomPotion = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                (item.getName().toLowerCase().contains("anti-venom") ||
                 item.getName().toLowerCase().contains("antivenom")));

        if (antivenomPotion != null) {
            if (DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, false, () -> Rs2Inventory.interact(antivenomPotion, "Drink"))) {
                log.info("Drinking {} (venomed)", antivenomPotion.getName());
                return true;
            }
        } else {
            // Any poison cure can reduce venom to ordinary poison. The next combat tick then
            // runs the normal poison handler and consumes the second dose if needed. Prefer
            // long-lasting antidotes first, but accept whatever safe equivalent was banked.
            Rs2ItemModel fallback = Rs2Inventory.get(item -> {
                if (item == null || item.getName() == null) return false;
                String name = item.getName().toLowerCase();
                return name.contains("antidote++")
                        || name.contains("antidote+")
                        || name.contains("superantipoison")
                        || name.contains("antipoison")
                        || name.contains("sanfew serum");
            });

            if (fallback != null) {
                if (DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, false, () -> Rs2Inventory.interact(fallback, "Drink"))) {
                    log.info("Drinking {} to reduce/cure venom (no anti-venom available)", fallback.getName());
                    return true;
                }
            } else {
                log.warn("Venomed but no anti-venom/antidote/antipoison in inventory!");
            }
        }
        return false;
    }

    /**
     * Varbit for poison status.
     * 0 = not poisoned, >0 = poisoned (damage value)
     * Values > 1000000 indicate venom (handled by handleAntivenom)
     */
    private static final int POISON_VARP = 102;

    /**
     * Handles drinking antipoison/antidote potions when poisoned.
     * This is a fallback if Rs2Player.drinkAntiPoisonPotion() doesn't work.
     * @return true if a potion was drunk, false otherwise
     */
    private boolean handleAntipoison() {
        traceDroAction("handleAntipoison");
        // Check if poisoned (but not venomed - venom is > 1000000)
        int poisonVarp = Microbot.getClient().getVarpValue(POISON_VARP);
        if (poisonVarp <= 0 || poisonVarp > 1000000) {
            // Not poisoned (or venomed, which is handled separately)
            return false;
        }

        log.info("Poisoned (varp={}), looking for antipoison/antidote", poisonVarp);

        // Try antidote++ first (best)
        Rs2ItemModel antidotePlusPlus = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                item.getName().toLowerCase().contains("antidote++"));

        if (antidotePlusPlus != null) {
            if (DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, false, () -> Rs2Inventory.interact(antidotePlusPlus, "Drink"))) {
                log.info("Drinking {} (poisoned)", antidotePlusPlus.getName());
                return true;
            }
            return false;
        }

        // Try antidote+
        Rs2ItemModel antidotePlus = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                item.getName().toLowerCase().contains("antidote+") &&
                !item.getName().toLowerCase().contains("antidote++"));

        if (antidotePlus != null) {
            if (DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, false, () -> Rs2Inventory.interact(antidotePlus, "Drink"))) {
                log.info("Drinking {} (poisoned)", antidotePlus.getName());
                return true;
            }
            return false;
        }

        // Try superantipoison
        Rs2ItemModel superAntipoison = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                item.getName().toLowerCase().contains("superantipoison"));

        if (superAntipoison != null) {
            if (DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, false, () -> Rs2Inventory.interact(superAntipoison, "Drink"))) {
                log.info("Drinking {} (poisoned)", superAntipoison.getName());
                return true;
            }
            return false;
        }

        // Try regular antipoison
        Rs2ItemModel antipoison = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                item.getName().toLowerCase().contains("antipoison") &&
                !item.getName().toLowerCase().contains("super"));

        if (antipoison != null) {
            if (DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, false, () -> Rs2Inventory.interact(antipoison, "Drink"))) {
                log.info("Drinking {} (poisoned)", antipoison.getName());
                return true;
            }
            return false;
        }

        // Try any antidote (generic)
        Rs2ItemModel antidote = Rs2Inventory.get(item ->
                item != null && item.getName() != null &&
                item.getName().toLowerCase().contains("antidote"));

        if (antidote != null) {
            if (DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, false, () -> Rs2Inventory.interact(antidote, "Drink"))) {
                log.info("Drinking {} (poisoned)", antidote.getName());
                return true;
            }
            return false;
        }

        // Last-resort equivalents accepted by the automatic banker. Anti-venom variants
        // cure ordinary poison too, and Sanfew serum also cures poison.
        Rs2ItemModel alternateCure = Rs2Inventory.get(item -> {
            if (item == null || item.getName() == null) return false;
            String name = item.getName().toLowerCase();
            return name.contains("anti-venom")
                    || name.contains("antivenom")
                    || name.contains("sanfew serum");
        });
        if (alternateCure != null) {
            if (DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, false, () -> Rs2Inventory.interact(alternateCure, "Drink"))) {
                log.info("Drinking {} (poisoned, fallback cure)", alternateCure.getName());
                return true;
            }
            return false;
        }

        log.warn("Poisoned but no antipoison/antidote/anti-venom cure in inventory!");
        return false;
    }

    private static final int MIN_ARROW_STACK = 9;  // Loot 10+ arrows
    private static final int MIN_RUNE_STACK = 1;   // Loot 2+ runes

    private void handleLooting() {
        traceDroAction("handleLooting");
        // Delay between looting to avoid spam
        if (System.currentTimeMillis() - lastLootTime < LOOT_DELAY_MS) {
            return;
        }

        // Don't loot if inventory is full (unless we can eat for space)
        if (Rs2Inventory.isFull() && !config.eatForLootSpace()) {
            return;
        }

        // Don't loot while in combat unless force loot is enabled
        if (Rs2Combat.inCombat() && !config.forceLoot()) {
            return;
        }

        // Parse exclusion list
        Set<String> excludedItems = parseItemList(config.lootExcludeList());

        // Build looting parameters
        int maxPrice = config.maxLootValue() > 0 ? config.maxLootValue() : Integer.MAX_VALUE;
        LootingParameters params = new LootingParameters(
                config.minLootValue(),
                maxPrice,
                config.attackRadius(),
                1,  // minQuantity
                1,  // minInvSlots
                config.delayedLooting(),
                config.onlyLootMyItems()
        );
        params.setEatFoodForSpace(config.eatForLootSpace());

        // Build the loot engine with exclusion filter
        Rs2LootEngine.Builder builder = Rs2LootEngine.with(params)
                .withLootAction(groundItem -> {
                    // Check exclusion list before looting
                    if (isItemExcluded(groundItem.getName(), excludedItems)) {
                        log.debug("Skipping excluded item: {}", groundItem.getName());
                        return; // Don't loot excluded items
                    }
                    if (DroSlayerCluePickup.tier(groundItem.getName()) != null) {
                        DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.ACTIVE_WORK, false, () -> {
                            if (!cluePickup.mayAttempt(groundItem.getName(), System.currentTimeMillis())) return;
                            // A single Take click lets rejection chat stop future attempts;
                            // coreLoot's despawn wait retries internally when the item cannot be taken.
                            Rs2GroundItem.interact(groundItem);
                            lastLootTime = System.currentTimeMillis();
                        });
                        return;
                    }
                    DroSlayerRuntime.performAction(
                            BaseProfileDro.ActionPhase.ACTIVE_WORK, false,
                            () -> Rs2GroundItem.coreLoot(groundItem));
                });

        // Add custom item list if using ITEM_LIST or MIXED style
        LootStyle style = config.lootStyle();
        if (style == LootStyle.ITEM_LIST || style == LootStyle.MIXED) {
            addCustomItemList(builder, config.lootItemList());
        }

        // Add GE price-based looting if using GE_PRICE_RANGE or MIXED style
        // Also add value filtering if minLootValue > 0 to ensure all looting respects the min value
        if (style == LootStyle.GE_PRICE_RANGE || style == LootStyle.MIXED || config.minLootValue() > 0) {
            builder.addByValue();
        }

        // Add specific loot types based on config
        if (config.lootBones() && config.buryBones()) {
            builder.addBones();  // This will also bury them
        } else if (config.lootBones()) {
            // Just loot bones without burying - add as custom filter
            addBonesWithoutBury(builder);
        }

        if (config.scatterAshes()) {
            builder.addAshes();
        }

        if (config.lootCoins()) {
            int minCoinStack = config.minCoinStack();
            if (minCoinStack > 0) {
                // Use custom filter with minimum stack size
                Predicate<GroundItem> coinFilter = gi -> {
                    if (gi.getName() == null) return false;
                    String name = gi.getName().toLowerCase();
                    return name.equals("coins") && gi.getQuantity() >= minCoinStack;
                };
                builder.addCustom("coins", coinFilter, null);
            } else {
                // Loot all coins
                builder.addCoins();
            }
        }

        if (config.lootUntradables()) {
            builder.addUntradables();
        }

        if (config.lootArrows()) {
            builder.addArrows(MIN_ARROW_STACK);
        }

        if (config.lootRunes()) {
            builder.addRunes(MIN_RUNE_STACK);
        }

        // Execute the looting pass
        boolean looted = DroSlayerRuntime.performAction(
                BaseProfileDro.ActionPhase.ACTIVE_WORK, false,
                (java.util.function.BooleanSupplier) builder::loot);
        if (looted) {
            lastLootTime = System.currentTimeMillis();
        }
    }

    /**
     * Parses the exclusion list from config into a set of lowercase item names.
     */
    private Set<String> parseItemList(String csvNames) {
        Set<String> items = new HashSet<>();
        if (csvNames == null || csvNames.trim().isEmpty()) {
            return items;
        }

        Arrays.stream(csvNames.split(","))
                .map(s -> s == null ? "" : s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .forEach(items::add);

        return items;
    }

    /**
     * Matches an item name against a pattern.
     * Uses exact matching by default. Supports wildcards (*) for partial matching.
     * Examples: "bones" matches only "bones", "*bones" matches "superior dragon bones",
     *           "dragon*" matches "dragon dagger", "*dragon*" matches "superior dragon bones".
     */
    private boolean matchesItemPattern(String itemName, String pattern) {
        if (pattern.equals("*")) {
            return true;
        }

        // Exact match when no wildcards
        if (!pattern.contains("*")) {
            return itemName.equals(pattern);
        }

        // Convert wildcard pattern to regex
        String regex = pattern
                .replace(".", "\\.")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("*", ".*");

        return itemName.matches(regex);
    }

    /**
     * Checks if an item name matches any entry in the exclusion list.
     * Uses exact matching by default. Use wildcards (*) for partial matching.
     */
    private boolean isItemExcluded(String itemName, Set<String> excludedItems) {
        if (itemName == null || excludedItems.isEmpty()) {
            return false;
        }

        String lowerName = itemName.trim().toLowerCase();
        for (String pattern : excludedItems) {
            if (matchesItemPattern(lowerName, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds custom item names from the config to the loot engine.
     * Uses exact matching by default. Use wildcards (*) for partial matching.
     */
    private void addCustomItemList(Rs2LootEngine.Builder builder, String csvNames) {
        if (csvNames == null || csvNames.trim().isEmpty()) {
            return;
        }

        final Set<String> itemPatterns = parseItemList(csvNames);

        if (itemPatterns.isEmpty()) {
            return;
        }

        Predicate<GroundItem> byNames = gi -> {
            final String name = gi.getName() == null ? "" : gi.getName().trim().toLowerCase();
            for (String pattern : itemPatterns) {
                if (matchesItemPattern(name, pattern)) {
                    return true;
                }
            }
            return false;
        };

        builder.addCustom("itemList", byNames, null);
    }

    /**
     * Adds bones to the loot engine without the auto-bury feature.
     */
    private void addBonesWithoutBury(Rs2LootEngine.Builder builder) {
        Predicate<GroundItem> isBones = gi -> {
            final String name = gi.getName() == null ? "" : gi.getName().trim().toLowerCase();
            return name.contains("bones") && !name.contains("bonemeal");
        };

        builder.addCustom("bones", isBones, null);
    }

    /** Unrelated or level-ineligible Wilderness NPCs must not bypass assignment targeting. */
    static boolean allowsRetaliationTarget(boolean wilderness, String npcName, List<String> eligibleTargets) {
        if (!wilderness) return true;
        return npcName != null && eligibleTargets != null && (eligibleTargets.stream()
                .anyMatch(target -> target != null && DroSlayerTargetNames.sameVariant(npcName, target))
                || eligibleWildernessSuperior(npcName, eligibleTargets));
    }

    private void handleCombat() {
        traceDroAction("handleCombat");
        // Check if we're using AoE burst/barrage combat style
        if (shouldUseAoeCombat()) {
            handleAoeCombat();
            return;
        }

        // Get target monster names (use variant if specified, otherwise use slayer task monsters)
        List<String> targetMonsters = getTargetMonsterNames();

        // Log target monsters for debugging
        if (targetMonsters == null || targetMonsters.isEmpty()) {
            log.warn("No target monsters found for current task. Task: {}, Profile: {}",
                    currentTaskName(),
                    activeJsonProfile != null ? "found" : "null");

            // Fallback: try to attack anything that's attacking us ONLY at task location
            if (taskDestination != null) {
                WorldPoint playerLocation = Rs2Player.getWorldLocation();
                if (playerLocation != null && playerLocation.distanceTo(taskDestination) <= config.attackRadius()) {
                    Rs2NpcModel anyAttacker = findAnyNpcAttackingUs();
                    if (anyAttacker != null && allowsRetaliationTarget(isWildernessSlayer(), anyAttacker.getName(), targetMonsters)) {
                        log.info("Fallback: Attacking {} that is attacking us (at task location)", anyAttacker.getName());
                        if (attackNpc(anyAttacker)) {
                            sleepUntil(Rs2Player::isInteracting, 1000);
                        }
                    }
                }
            }
            return;
        }

        log.debug("Target monsters: {}", targetMonsters);

        // Check for superiors first - they take priority over everything
        if (config.prioritizeSuperiors()) {
            Rs2NpcModel superior = findNearbySuperior();
            if (superior != null) {
                log.info("Superior monster detected: {}! Attacking.", superior.getName());
                if (attackNpc(superior)) {
                    sleepUntil(Rs2Player::isInteracting, 1000);
                }
                return;
            }
        }

        // Check if we're already in combat with a living target
        Actor currentInteracting = Rs2Player.getInteracting();
        if (currentInteracting != null) {
            boolean targetAlive = true;
            if (currentInteracting instanceof net.runelite.api.NPC) {
                net.runelite.api.NPC npc = (net.runelite.api.NPC) currentInteracting;
                targetAlive = !npc.isDead() && npc.getHealthRatio() != 0;
            }
            if (targetAlive && allowsRetaliationTarget(isWildernessSlayer(), currentInteracting.getName(), targetMonsters)) {
                log.debug("In combat with: {}", currentInteracting.getName());
                return;
            }
            log.debug("Current target {} is dead or outside the eligible assignment, finding new target", currentInteracting.getName());
        }

        // Check if we're being attacked but not fighting back
        boolean beingAttacked = isBeingAttacked();
        if (beingAttacked) {
            // First try to find attacker from target list
            Rs2NpcModel attacker = findNpcAttackingUs(targetMonsters);
            if (attacker != null) {
                log.info("Retaliating against target monster: {}", attacker.getName());
                if (attackNpc(attacker)) {
                    sleepUntil(Rs2Player::isInteracting, 1000);
                }
                return;
            }

            // Fallback: attack ANY NPC that is attacking us ONLY if we're at the task location
            if (taskDestination != null) {
                WorldPoint playerLocation = Rs2Player.getWorldLocation();
                if (playerLocation != null && playerLocation.distanceTo(taskDestination) <= config.attackRadius()) {
                    Rs2NpcModel anyAttacker = findAnyNpcAttackingUs();
                    if (anyAttacker != null && allowsRetaliationTarget(isWildernessSlayer(), anyAttacker.getName(), targetMonsters)) {
                        log.info("Fallback: Retaliating against {} (at task location, not in target list)", anyAttacker.getName());
                        if (attackNpc(anyAttacker)) {
                            sleepUntil(Rs2Player::isInteracting, 1000);
                        }
                        return;
                    }
                }
            }
            // No attacker found, fall through to find new targets proactively
        }

        // Find attackable NPCs matching target monsters
        // Prioritize NPCs that are already attacking us (interacting with player)
        List<Rs2NpcModel> attackableNpcs = Microbot.getRs2NpcCache().query()
                .where(npc -> !npc.isDead() && npc.getHealthRatio() != 0)
                .where(npc -> npc.getName() != null)
                .where(npc -> targetMonsters.stream()
                        .anyMatch(monster -> matchesTargetMonster(npc.getName(), monster)))
                .where(npc -> taskDestination == null ||
                        npc.getWorldLocation().distanceTo(taskDestination) <= config.attackRadius())
                .where(this::canAttackNpc)
                .toListOnClientThread();
        final List<Rs2NpcModel> targetCandidates = attackableNpcs;
        attackableNpcs = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            WorldPoint location = Rs2Player.getWorldLocation();
            if (player == null || location == null) return Collections.<Rs2NpcModel>emptyList();
            return sortBySnapshotKeys(targetCandidates,
                    npc -> npc.getInteracting() == player ? 0 : 1,
                    npc -> npc.getWorldLocation() == null ? Integer.MAX_VALUE
                            : location.distanceTo(npc.getWorldLocation()));
        }).orElseGet(Collections::emptyList);

        if (attackableNpcs.isEmpty()) {
            if (revealZygomite()) return;

            log.debug("No attackable slayer monsters found nearby matching: {}", targetMonsters);

            // Debug: log nearby NPCs to help diagnose
            List<String> nearbyNpcNames = Microbot.getRs2NpcCache().query()
                    .where(npc -> !npc.isDead())
                    .where(npc -> npc.getName() != null)
                    .where(npc -> taskDestination == null ||
                            npc.getWorldLocation().distanceTo(taskDestination) <= config.attackRadius())
                    .toListOnClientThread()
                    .stream()
                    .map(Rs2NpcModel::getName)
                    .distinct()
                    .collect(Collectors.toList());
            if (!nearbyNpcNames.isEmpty()) {
                log.info("Nearby attackable NPCs: {} (looking for: {})", nearbyNpcNames, targetMonsters);
            }

            // Crash detection - check if we've been unable to find targets for too long
            if (config.hopWhenCrashed() && !occupiedAreaPending && taskDestination != null) {
                WorldPoint playerLocation = Rs2Player.getWorldLocation();
                boolean atTaskLocation = playerLocation != null &&
                        playerLocation.distanceTo(taskDestination) <= config.attackRadius();

                if (atTaskLocation) {
                    // Start or check crash detection timer
                    if (!isSearchingForTargets) {
                        isSearchingForTargets = true;
                        crashDetectionStartTime = System.currentTimeMillis();
                        log.debug("Started crash detection timer at task location");
                    } else {
                        long timeSinceStart = System.currentTimeMillis() - crashDetectionStartTime;
                        if (timeSinceStart >= CRASH_HOP_DELAY_MS) {
                            // Check cooldown
                            long timeSinceLastHop = System.currentTimeMillis() - lastWorldHopTime;
                            if (timeSinceLastHop >= WORLD_HOP_COOLDOWN_MS) {
                                log.info("No targets found for {} seconds - hopping worlds (likely crashed)",
                                        CRASH_HOP_DELAY_MS / 1000);
                                hopToNewWorld();
                                return;
                            } else {
                                log.debug("Would hop but on cooldown ({} seconds remaining)",
                                        (WORLD_HOP_COOLDOWN_MS - timeSinceLastHop) / 1000);
                            }
                        }
                    }
                }
            }
            return;
        }

        // Found targets - reset crash detection timer
        if (isSearchingForTargets) {
            isSearchingForTargets = false;
            crashDetectionStartTime = 0;
            log.debug("Reset crash detection timer - found targets");
        }

        // Attack the first NPC (prioritizes those attacking us, then closest)
        Rs2NpcModel target = attackableNpcs.get(0);
        if (attackNpc(target)) {
            log.info("Attacking {}", target.getName());
            sleepUntil(Rs2Player::isInteracting, 1000);
        }
    }

    private boolean handleOccupiedTaskArea(DroSlayerState state) {
        traceDroAction("handleOccupiedTaskArea");
        if (isWildernessSlayer()) return false; // Walker owns Wilderness threat escapes; never compete with them.
        if (!config.hopWhenCrashed() || taskDestination == null
                || (state != DroSlayerState.TRAVELING && state != DroSlayerState.AT_LOCATION
                && state != DroSlayerState.FIGHTING)) {
            occupiedAreaPending = false;
            return false;
        }
        boolean occupied = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var local = Microbot.getClient().getLocalPlayer();
            if (local == null || local.getWorldLocation().distanceTo(taskDestination) > config.attackRadius()) return false;
            return Microbot.getRs2PlayerCache().query()
                    .where(player -> player.getName() != null && !player.getName().equals(local.getName()))
                    .where(player -> player.getWorldLocation() != null
                            && player.getWorldLocation().distanceTo(taskDestination) <= config.attackRadius())
                    .count() > 0;
        }).orElse(false);
        if (!occupied) {
            occupiedAreaPending = false;
            return false;
        }
        if (!occupiedAreaPending) {
            occupiedAreaPending = true;
            log.info("Task area occupied by another player; waiting for first available world hop");
            DroSlayerRuntime.cancelWalk();
        }
        // Continue survival/combat handling until the current fight ends.
        if (Rs2Player.isInCombat() || isBeingAttacked()) return false;
        if (cannonPlaced || cannonBuildStage() > 0) {
            if (!handleCannonPickup()) return true;
            cannonPlaced = false;
            cannonFired = false;
        }
        if (System.currentTimeMillis() - occupiedHopAttemptTime < DroSlayerHopPolicy.RETRY_GAP_MS) return true;
        occupiedHopAttemptTime = System.currentTimeMillis();
        hopToNewWorld();
        return true;
    }

    /**
     * Hops to a new world to avoid crashed spots.
     * Uses the configured world list if provided, otherwise picks a random members world.
     */
    private void hopToNewWorld() {
        try {
            if (Rs2Player.isInCombat() || isBeingAttacked()) return;
            if (!closeBankBeforeHop(() -> DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP,
                    false, (java.util.function.BooleanSupplier) Rs2Bank::closeBank))) return;
            long now = System.currentTimeMillis();
            if (now - taskHopAttemptAt < DroSlayerHopPolicy.RETRY_GAP_MS) return;
            taskHopAttemptAt = now;
            DroSlayerRuntime.cancelWalk();
            // Disable prayers before hopping
            deactivateProfilePrayer();
            deactivateOffensivePrayers();

            // Get world from config list or random
            int world = getHopWorld();
            if (world <= 0) {
                log.warn("Could not determine a valid world to hop to");
                return;
            }
            log.info("Hopping to world {}", world);

            // The shared hop helper already handles its prompt and waits for arrival.
            // Preserve its boolean result instead of adding another 5 + 15 second wait after failure.
            boolean arrived = DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                    (java.util.function.BooleanSupplier) () -> Microbot.hopToWorld(world));
            boolean confirmed = Microbot.getClientThread().runOnClientThreadOptional(() ->
                    Microbot.getClient().getGameState() == GameState.LOGGED_IN
                            && Microbot.getClient().getWorld() == world).orElse(false);
            if (!confirmed) {
                log.warn("World hop to {} not completed (action result={}); retry after combat clears", world, arrived);
                return;
            }
            DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK);

            // Reset crash detection state
            isSearchingForTargets = false;
            crashDetectionStartTime = 0;
            lastWorldHopTime = System.currentTimeMillis();

            log.info("Successfully hopped to world {}", world);
        } catch (Exception e) {
            log.error("Error hopping worlds: {}", e.getMessage());
            // Reset state even on error
            isSearchingForTargets = false;
            crashDetectionStartTime = 0;
        }
    }

    /**
     * Gets a world to hop to from the config list, or a random members world if list is empty.
     * Avoids hopping to the current world.
     */
    private int getHopWorld() {
        String worldList = config.hopWorldList();
        int currentWorld = currentWorld();
        boolean members = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.MEMBERS)).orElse(true);
        var availableWorlds = Microbot.getWorldService().getWorlds();
        if (availableWorlds == null) return 0;

        if (worldList != null && !worldList.trim().isEmpty()) {
            // Parse comma-separated world list
            List<Integer> worlds = Arrays.stream(worldList.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> {
                        try {
                            return Integer.parseInt(s);
                        } catch (NumberFormatException e) {
                            log.warn("Invalid world number in hop list: {}", s);
                            return -1;
                        }
                    })
                    .filter(w -> DroSlayerHopPolicy.eligible(availableWorlds.findWorld(w), members, currentWorld))
                    .collect(Collectors.toList());

            if (!worlds.isEmpty()) {
                int selectedWorld = worlds.get(new Random().nextInt(worlds.size()));
                log.debug("Selected world {} from config list (available: {})", selectedWorld, worlds);
                return selectedWorld;
            } else {
                log.warn("No valid worlds in hop list (or all filtered out), falling back to random");
            }
        }

        // Random ordinary US world matching membership; warning and total-level worlds are excluded.
        return safeWildernessHopWorld();
    }

    /**
     * Checks if any NPC is currently attacking the player.
     */
    private boolean isBeingAttacked() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getInteracting() == Microbot.getClient().getLocalPlayer())
                .where(this::canAttackNpc)
                .count()).orElse(0) > 0;
    }

    /**
     * Finds an NPC from the target list that is currently attacking the player.
     * Used when we're "in combat" (being attacked) but not attacking back.
     */
    private Rs2NpcModel findNpcAttackingUs(List<String> targetMonsters) {
        return Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getName() != null)
                .where(npc -> npc.getInteracting() == Microbot.getClient().getLocalPlayer())
                .where(npc -> targetMonsters.stream()
                        .anyMatch(monster -> matchesTargetMonster(npc.getName(), monster)))
                .where(npc -> taskDestination == null ||
                        npc.getWorldLocation().distanceTo(taskDestination) <= config.attackRadius() + 5)
                .where(this::canAttackNpc)
                .firstOnClientThread();
    }

    /**
     * Finds ANY NPC that is currently attacking the player (regardless of target list).
     * Used as a fallback when variant/target list doesn't match.
     */
    private Rs2NpcModel findAnyNpcAttackingUs() {
        return Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getName() != null)
                .where(npc -> npc.getInteracting() == Microbot.getClient().getLocalPlayer())
                .where(npc -> !npc.isDead() && npc.getHealthRatio() != 0)
                .where(this::canAttackNpc)
                .firstOnClientThread();
    }

    /** Failed optional bursting restores the ordinary saved loadout instead of idling or retrying forever. */
    private boolean fallbackDustBurstSetup(String reason) {
        if (!wildernessBurstSelected()) return false;
        dustBurstFallback = true;
        dustBurstFallbackPending = true;
        dustBurstFallbackExitAttempts = 0;
        initialSetupDone = false;
        baseBankLoadComplete = false;
        currentInventorySetup = null;
        spellbookSwapAttempts = 0;
        DroSlayerRuntime.cancelWalk();
        log.warn("{}; Wilderness burst task reverting to selected Wilderness inventory setup for this task", reason);
        if (getActiveInventorySetup() == null) {
            dustBurstFallbackPending = false;
            unresolvedTaskName = currentTaskName();
            Microbot.status = "Wilderness burst task fallback needs a selected Wilderness inventory setup";
            DroSlayerPlugin.setState(DroSlayerState.IDLE);
            return true;
        }
        advanceDustBurstFallback();
        return true;
    }

    private void advanceDustBurstFallback() {
        if (isInPoh()) {
            if (++dustBurstFallbackExitAttempts > 3) {
                dustBurstFallbackPending = false;
                unresolvedTaskName = currentTaskName();
                Microbot.status = "Wilderness burst task fallback: could not leave house";
                DroSlayerPlugin.setState(DroSlayerState.IDLE);
                return;
            }
            leaveHouse();
            if (!sleepUntil(() -> !isInPoh(), 3000)) {
                DroSlayerPlugin.setState(DroSlayerState.SWAPPING_SPELLBOOK);
                return;
            }
        }
        dustBurstFallbackPending = false;
        dustBurstSwapStarted = 0;
        resetPohState();
        Microbot.status = "Reloading normal Wilderness setup after failed Ancient swap";
        DroSlayerPlugin.setState(DroSlayerState.BANKING);
    }

    private boolean isWildernessAviansieTask() {
        if (!isWildernessSlayer()) return false;
        String name = currentTaskName();
        return name != null && Task.getTask(name) == Task.AVIANSIES;
    }

    private boolean wildernessBurstSelected() {
        if (config == null || dustBurstFallback || !isWildernessSlayer()
                || (!config.wildernessBurstDustDevils() && !config.wildernessBurstJellies())) return false;
        String name = currentTaskName();
        Task task = name == null ? null : Task.getTask(name);
        return task == Task.DUST_DEVILS && config.wildernessBurstDustDevils()
                || task == Task.JELLIES && config.wildernessBurstJellies();
    }

    private boolean hasAncientAutocastWeapon() {
        return isWearingAnyWeapon("staff", "wand", "kodai", "sceptre", "scepter");
    }

    private boolean shouldUseAoeCombat() {
        // A normal-master burst profile must not override the weapon in a Wilderness setup.
        boolean selected = isWildernessSlayer() ? wildernessBurstSelected()
                : activeJsonProfile != null && activeJsonProfile.isAoeStyle();
        return selected && hasAncientAutocastWeapon();
    }

    private boolean ancientAutocastReady() {
        if (Rs2Magic.isSpellbook(Rs2Spellbook.ANCIENT)) {
            missingAncientSpellbookLogged = false;
            return true;
        }
        Microbot.status = "Burst/barrage requires Ancient spellbook";
        if (!missingAncientSpellbookLogged) {
            missingAncientSpellbookLogged = true;
            log.warn("Burst/barrage setup requires Ancient spellbook; autocast retries held until corrected");
        }
        return false;
    }

    private void handleAoeCombat() {
        traceDroAction("handleAoeCombat");
        // Ensure we have a valid profile for AoE combat
        if (activeJsonProfile == null) {
            log.warn("No active profile for AoE combat");
            return;
        }

        // Get the combat style and min stack size from profile
        DroSlayerCombatStyle style = wildernessBurstSelected()
                ? DroSlayerCombatStyle.BURST : activeJsonProfile.getParsedStyle();
        if (style == null) {
            log.warn("No combat style defined in profile for AoE combat");
            return;
        }

        // Determine which spell to use based on style
        Rs2CombatSpells spell = (style == DroSlayerCombatStyle.BARRAGE)
                ? Rs2CombatSpells.ICE_BARRAGE
                : Rs2CombatSpells.ICE_BURST;

        if (!ancientAutocastReady()) return;

        // Safety check: re-set autocast if it was cleared (e.g., weapon swap mid-combat)
        if (Rs2Magic.getCurrentAutoCastSpell() != spell) {
            log.info("Autocast was reset, re-setting to {}", spell.name());
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> {
                Rs2Combat.setAutoCastSpell(spell, false);
            });
            sleepUntil(() -> Rs2Magic.getCurrentAutoCastSpell() == spell, 3000);
            return;
        }

        int minStackSize = activeJsonProfile.getMinStackSize();
        if (minStackSize < 1) {
            minStackSize = 3; // Default minimum
        }

        // Activate protection prayer
        activateProfilePrayer();

        // If already in combat with a valid target, let autocast handle it
        if (Rs2Player.isInteracting()) {
            Actor currentTarget = Rs2Player.getInteracting();
            if (currentTarget != null && !currentTarget.isDead() && currentTarget.getHealthRatio() != 0) {
                log.debug("Already in combat with {} - autocast will continue", currentTarget.getName());
                return;
            }
        }

        // Get target monster names (use variant if specified)
        List<String> targetMonsters = getTargetMonsterNames();
        if (targetMonsters == null || targetMonsters.isEmpty()) {
            log.warn("No target monsters found for current task");
            return;
        }

        // Find all nearby target monsters (including those already in combat with us)
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return;
        }

        List<Rs2NpcModel> nearbyMonsters = Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getInteracting() == Microbot.getClient().getLocalPlayer())
                .where(npc -> npc.getName() != null)
                .where(npc -> targetMonsters.stream()
                        .anyMatch(monster -> matchesTargetMonster(npc.getName(), monster)))
                .where(npc -> !npc.isDead() && npc.getHealthRatio() != 0)
                .where(npc -> taskDestination == null ||
                        npc.getWorldLocation().distanceTo(taskDestination) <= config.attackRadius())
                .toListOnClientThread();

        if (nearbyMonsters.isEmpty()) {
            log.debug("No target monsters found nearby for AoE combat");
            return;
        }

        // Count monsters within AoE range (3x3 for burst, 5x5 for barrage centered on target)
        int aoeRange = (style == DroSlayerCombatStyle.BARRAGE) ? 2 : 1;

        // Find the best target (one with most monsters stacked nearby)
        Rs2NpcModel bestTarget = null;
        int maxStackedCount = 0;

        for (Rs2NpcModel potentialTarget : nearbyMonsters) {
            WorldPoint targetLoc = potentialTarget.getWorldLocation();
            int stackedCount = 0;

            for (Rs2NpcModel monster : nearbyMonsters) {
                WorldPoint monsterLoc = monster.getWorldLocation();
                // Check if within AoE range of the potential target
                if (Math.abs(targetLoc.getX() - monsterLoc.getX()) <= aoeRange &&
                    Math.abs(targetLoc.getY() - monsterLoc.getY()) <= aoeRange &&
                    targetLoc.getPlane() == monsterLoc.getPlane()) {
                    stackedCount++;
                }
            }

            if (stackedCount > maxStackedCount) {
                maxStackedCount = stackedCount;
                bestTarget = potentialTarget;
            }
        }

        // Check if we have enough monsters stacked
        if (maxStackedCount < minStackSize) {
            // If using goading, we need to attack first to trigger the aggro effect
            // Goading works by attracting NPCs when you're already in combat
            if (activeJsonProfile.shouldUseGoading() && bestTarget != null) {
                log.info("Attacking {} to trigger goading aggro ({} monsters nearby)",
                        bestTarget.getName(), nearbyMonsters.size());
                if (attackNpc(bestTarget)) {
                    sleepUntil(Rs2Player::isInteracting, 1000);
                }
                return;
            }

            log.debug("Waiting for monsters to stack: {}/{} (goading active: {})",
                    maxStackedCount, minStackSize, activeJsonProfile.shouldUseGoading());
            // Just wait for more monsters to come
            return;
        }

        if (bestTarget == null) {
            log.debug("No suitable target found for AoE combat");
            return;
        }

        // Attack the best target - autocast will handle spell casting
        log.info("Attacking {} with autocast {} ({} monsters stacked)",
                bestTarget.getName(), spell.name(), maxStackedCount);

        if (attackNpc(bestTarget)) {
            sleepUntil(Rs2Player::isInteracting, 1000);
        }
    }

    private long lastZygomitePick;

    private boolean revealZygomite() {
        String task = currentTaskName();
        if (task == null || !task.toLowerCase(java.util.Locale.ROOT).contains("zygomite")) return false;
        if (System.currentTimeMillis() - lastZygomitePick < 3500) return true;
        Rs2NpcModel mushroom = Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getId() == 533 || npc.getId() == 538 || npc.getId() == 8690)
                .where(npc -> taskDestination != null
                        && npc.getWorldLocation().distanceTo(taskDestination) <= config.attackRadius())
                .firstOnClientThread();
        if (mushroom == null) return false;
        if (DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.ACTIVE_WORK, false,
                () -> mushroom.click("Pick"))) {
            lastZygomitePick = System.currentTimeMillis();
        }
        return true;
    }

    /** Never enter combat with temporary fairy-ring equipment in place of the setup weapon. */
    private boolean restoreCombatWeaponAfterTravel() {
        traceDroAction("restoreCombatWeaponAfterTravel");
        Rs2ItemModel weapon = Rs2Equipment.get(net.runelite.api.EquipmentInventorySlot.WEAPON);
        if (weapon == null || (weapon.getId() != 772 && weapon.getId() != 9084)) return false;
        var activeSetup = getActiveInventorySetup();
        if (activeSetup == null || activeSetup.getEquipment() == null) return false;
        int slot = net.runelite.api.EquipmentInventorySlot.WEAPON.getSlotIdx();
        if (activeSetup.getEquipment().size() <= slot) return false;
        var desired = activeSetup.getEquipment().get(slot);
        if (desired == null || desired.getId() <= 0 || desired.getId() == weapon.getId()) return false;
        Rs2ItemModel replacement = Rs2Inventory.get(item -> item.getId() == desired.getId()
                || (desired.isFuzzy() && item.getName() != null && desired.getName() != null
                    && item.getName().equalsIgnoreCase(desired.getName())));
        if (replacement == null) {
            Microbot.status = "Combat weapon missing after travel; returning to bank.";
            DroSlayerPlugin.setState(DroSlayerState.BANKING);
            return true;
        }
        Microbot.status = "Restoring combat weapon after fairy ring.";
        DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                () -> Rs2Inventory.equip(replacement.getId()));
        return true;
    }

    /** Interaction alone does not imply combat: harmless NPCs can follow/look at the player. */
    private boolean canAttackNpc(Rs2NpcModel npc) {
        if (npc == null) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (npc.isDead()) return false;
            // Match the definition used by Rs2NpcModel.click, before activating the mouse.
            net.runelite.api.NPCComposition definition = Microbot.getClient().getNpcDefinition(npc.getId());
            if (definition == null || definition.getActions() == null) return false;
            return Arrays.stream(definition.getActions()).anyMatch("Attack"::equalsIgnoreCase);
        }).orElse(false);
    }

    private boolean attackNpc(Rs2NpcModel npc) {
        traceDroAction("attackNpc");
        if (occupiedAreaPending && !isBeingAttacked()) return false;
        if (!canAttackNpc(npc)) return false;
        return DroSlayerRuntime.performAction(
                BaseProfileDro.ActionPhase.ACTIVE_WORK, false, () -> npc.click("Attack"));
    }

    /**
     * Gets the list of monster names to target.
     * If a variant is specified in the JSON profile, uses that instead of the slayer task monsters.
     * This allows targeting specific monsters like "rune dragons" when task is "metal dragons".
     */
    private List<String> getTargetMonsterNames() {
        if (isWildernessSlayer() && wildernessAssignment != null && !wildernessAssignment.getTargetNames().isEmpty()) {
            int slayerLevel = Rs2Player.getRealSkillLevel(Skill.SLAYER);
            return wildernessAssignment.getTargetNames().stream()
                    .filter(name -> isEligibleWildernessTarget(name, slayerLevel)).collect(Collectors.toList());
        }
        // Check if profile specifies a variant
        if (activeJsonProfile != null && activeJsonProfile.hasVariant()) {
            String variant = activeJsonProfile.getVariant();
            log.debug("Using variant monster: {}", variant);
            return Arrays.asList(variant);
        }

        // Try standard slayer monster list first
        List<String> monsters = Rs2Slayer.getSlayerMonsters();
        if (monsters != null && !monsters.isEmpty()) {
            return monsters;
        }

        // Fallback: convert task name from plural to singular
        // e.g., "Frost Dragons" -> "Frost dragon"
        String taskName = currentTaskName();
        if (taskName != null && !taskName.isEmpty()) {
            String singularName = convertTaskNameToNpcName(taskName);
            log.debug("Using fallback NPC name: {} (from task: {})", singularName, taskName);
            return Arrays.asList(singularName);
        }

        return null;
    }

    /**
     * Returns true if using a specific variant from the profile (exact match required).
     */
    private boolean isUsingVariant() {
        return isWildernessSlayer() ? wildernessAssignment != null : activeJsonProfile != null && activeJsonProfile.hasVariant();
    }

    /**
     * Checks if an NPC name matches the target monster name.
     * Uses exact matching for variants, broader matching for general task names.
     */
    private boolean matchesTargetMonster(String npcName, String targetMonster) {
        if (npcName == null || targetMonster == null) {
            return false;
        }
        // This assignment was verified in-game to advance on Scarab Mages only.
        // Apply the same filter to selection and scripted retaliation.
        if (currentTaskName() != null
                && "Scabarites".equalsIgnoreCase(currentTaskName())) {
            return "Scarab Mage".equalsIgnoreCase(npcName);
        }
        String npcLower = npcName.toLowerCase();
        String targetLower = targetMonster.toLowerCase();

        if (isUsingVariant()) {
            // Exact match for variants (e.g., "baby blue dragon" should NOT match "blue dragon")
            return DroSlayerTargetNames.sameVariant(npcLower, targetLower);
        } else {
            // Broader match for general task names
            // e.g., task "blue dragons" with monster "blue dragon" should match NPC "Blue dragon"
            return npcLower.contains(targetLower) || targetLower.contains(npcLower);
        }
    }

    /**
     * Secondary task-location resolver for new or mismatched Slayer categories.
     * It stays inside Dro's bundled NPC-location database; no hardcoded route
     * is required for every ordinary task.
     */
    private MonsterLocation resolveTaskLocationFromNpcData(String taskName) {
        if (taskName == null || taskName.isBlank()) {
            return null;
        }

        LinkedHashSet<String> candidateNames = new LinkedHashSet<>();
        String singularName = convertTaskNameToNpcName(taskName);

        // Try category aliases first. This catches category singular/plural drift, e.g.
        // "Custodian Stalkers" (assignment) vs "Custodian Stalker" (NPC stats category).
        for (String category : Arrays.asList(taskName, singularName)) {
            if (category == null || category.isBlank()) continue;
            List<String> categoryMonsters = Rs2NpcManager.getSlayerMonstersByCategory(category);
            if (categoryMonsters != null) {
                candidateNames.addAll(categoryMonsters);
            }
        }

        if (singularName != null && !singularName.isBlank()) {
            candidateNames.add(singularName);
        }

        Task runeLiteTask = Task.getTask(taskName);
        if (runeLiteTask != null && runeLiteTask.getTargetNames() != null) {
            candidateNames.addAll(Arrays.asList(runeLiteTask.getTargetNames()));
        }

        // Explicit aliases for the newer Stalker Den assignment. These are harmless if
        // the local NPC-location database does not contain one of the variants.
        if (taskName.equalsIgnoreCase("Custodian Stalkers")) {
            candidateNames.add("Juvenile custodian stalker");
            candidateNames.add("Mature custodian stalker");
            candidateNames.add("Elder custodian stalker");
            candidateNames.add("Ancient Custodian");
        }

        // Prefer dense clusters, then relax for sparse/newer task locations.
        int[] clusterThresholds = {3, 1, 0};
        for (int minCluster : clusterThresholds) {
            for (String npcName : candidateNames) {
                MonsterLocation location = Rs2NpcManager.getClosestLocation(npcName, minCluster, true);
                if (location != null && location.getBestClusterCenter() != null) {
                    log.info("Resolved task '{}' through NPC location fallback: '{}' -> {}",
                            taskName, npcName, location.getLocationName());
                    return location;
                }
            }
        }

        return null;
    }

    /**
     * Converts a slayer task name (often plural) to the likely NPC name (often singular).
     * Examples: "Frost Dragons" -> "Frost dragon", "Abyssal Demons" -> "Abyssal demon"
     */
    private String convertTaskNameToNpcName(String taskName) {
        if (taskName == null || taskName.isEmpty()) {
            return taskName;
        }

        String result = taskName.trim();

        // Remove trailing 's' for simple plurals (Dragons -> Dragon)
        if (result.endsWith("s") && !result.endsWith("ss")) {
            result = result.substring(0, result.length() - 1);
        }

        // Convert to title case (first letter uppercase, rest lowercase for each word)
        // "FROST DRAGON" -> "Frost dragon", "frost dragon" -> "Frost dragon"
        String[] words = result.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (words[i].length() > 0) {
                if (i == 0) {
                    // First word: capitalize first letter
                    sb.append(Character.toUpperCase(words[i].charAt(0)));
                    if (words[i].length() > 1) {
                        sb.append(words[i].substring(1).toLowerCase());
                    }
                } else {
                    // Subsequent words: all lowercase
                    sb.append(" ").append(words[i].toLowerCase());
                }
            }
        }

        return sb.toString();
    }

    static boolean eligibleWildernessSuperior(String superior, List<String> targets) {
        if (superior == null || targets == null) return false;
        String parent;
        switch (superior.toLowerCase(java.util.Locale.ROOT)) {
            case "insatiable bloodveld": parent = "Bloodveld"; break;
            case "vitreous jelly": parent = "Jelly"; break;
            case "choke devil": parent = "Dust devil"; break;
            case "nechryarch": parent = "Greater Nechryael"; break;
            case "greater abyssal demon": parent = "Abyssal demon"; break;
            default: return false;
        }
        return targets.stream().anyMatch(target -> DroSlayerTargetNames.sameVariant(parent, target));
    }

    /**
     * Finds a nearby superior slayer monster that is attackable
     * @return The superior NPC if found, null otherwise
     */
    private Rs2NpcModel findNearbySuperior() {
        return Microbot.getRs2NpcCache().query()
                .where(npc -> !npc.isDead() && npc.getHealthRatio() != 0)
                .where(npc -> npc.getName() != null)
                .where(npc -> SUPERIOR_MONSTERS.contains(npc.getName()))
                .where(npc -> !isWildernessSlayer()
                        || eligibleWildernessSuperior(npc.getName(), getTargetMonsterNames()))
                .where(npc -> taskDestination == null ||
                        npc.getWorldLocation().distanceTo(taskDestination) <= config.attackRadius() + 5)
                .toListOnClientThread()
                .stream()
                .min(Comparator.comparingInt(npc ->
                        Rs2Player.getWorldLocation().distanceTo(npc.getWorldLocation())))
                .orElse(null);
    }

    /**
     * Checks if we need to bank based on food, potion, and cannonball thresholds
     */
    private boolean needsBanking() {
        if (wildernessBurstSelected() && (!hasAncientAutocastWeapon()
                || !Rs2Magic.isSpellbook(Rs2Spellbook.ANCIENT))) {
            log.info("Wilderness bursting selected but weapon/Ancient spellbook missing; banking for burst setup");
            return true;
        }
        if (shouldRestockCannonballs()) {
            log.info("Cannon ammunition below replenishment threshold; carried={} loaded={} threshold={} taskRemaining={}; banking required",
                    getCannonballCount(), loadedCannonballCount(),
                    DroSlayerCannonPolicy.restockThreshold(isWildernessSlayer(), config.cannonballThreshold()),
                    Rs2Slayer.getSlayerTaskSize());
            return true;
        }
        if (isWildernessSlayer() && wildernessWeaponCharge.needsRecharge()) {
            log.info("Wilderness weapon requires a verified charge reserve; banking required");
            return true;
        }
        if (!hasBankEscapeTeleportInInventory()) {
            log.info("Escape ring missing; banking required before continuing the task");
            return true;
        }
        // Check food threshold
        int remainingKills = Rs2Slayer.getSlayerTaskSize();
        boolean finishingTask = remainingKills > 0 && remainingKills < 10;
        if (finishingTask && taskFood().isEmpty() && getPrayerPotionDoses() == 0) {
            log.info("Both food and prayer supplies exhausted with {} kills remaining; banking required", remainingKills);
            return true;
        }
        int foodThreshold = config.foodThreshold();
        if (!finishingTask && foodThreshold > 0) {
            int foodCount = taskFood().size();
            if (foodCount < foodThreshold) {
                log.info("Food count ({}) below threshold ({})", foodCount, foodThreshold);
                return true;
            }
        }

        // Prayer supplies only matter when this task is actually configured to use prayer.
        // This prevents a restart inside a task cave from immediately becoming BANKING simply
        // because the generic prayer-potion threshold is non-zero while prayer is disabled.
        boolean usesPrayer = isWildernessSlayer() || config.useOffensivePrayers()
                || (config.prayerFlickStyle() != PrayerFlickStyle.OFF
                    && getActiveProtectionPrayer() != DroSlayerPrayer.NONE);
        int potionThreshold = isWildernessSlayer() ? Math.max(1, config.potionThreshold()) : config.potionThreshold();
        if (!finishingTask && usesPrayer && potionThreshold > 0) {
            int prayerDoses = getPrayerPotionDoses();
            if (prayerDoses < potionThreshold) {
                log.info("Prayer potion doses ({}) below threshold ({})", prayerDoses, potionThreshold);
                return true;
            }
        }

        // Missing optional cannon supplies fall back to ordinary attacks, not a banking trip.
        if (isUsingCannon && !cannonPlaced && cannonBuildStage() == 0
                && !DroSlayerCannonPolicy.canDeploy(hasCannonParts(), getCannonballCount())) {
            disableCannonForTrip("Optional cannon supplies exhausted");
        }

        if (!hasAllTaskRequirementsForCurrentTask()) {
            return true;
        }

        return false;
    }

    /**
     * Gets total prayer potion doses in inventory (prayer potions and super restores)
     */
    private int getPrayerPotionDoses() {
        return Rs2Inventory.items().mapToInt(item ->
                DroSlayerWildernessSupplies.prayerDoses(item.getName(), isWildernessSlayer()) * item.getQuantity()).sum();
    }

    // BaseProfileDro owns smart breaks/login recovery; legacy BreakHandler locking was intentionally removed.

    private void handleRouteFailureBudget(String context) {
        traceDroAction("handleRouteFailureBudget");
        int failures = DroSlayerRuntime.routeFailures();
        if (failures < config.maxRecoveryAttempts()) return;

        log.error("Route recovery budget exhausted during {} ({} failures)", context, failures);
        terminalStopMessage = "[Dro] Slayer 2 could not recover " + context + ". Logging out safely.";
        DroSlayerRuntime.cancelWalk();
        DroSlayerRuntime.clearRouteFailures();
        stopAndLogout();
    }

    /**
     * Detect an already-running task inside its destination coordinate space. This is used only
     * to suppress the artificial "initial setup" bank trip after a plugin restart; real missing
     * food/potions/task items are still allowed to send us to the bank through needsBanking().
     */
    private boolean isAlreadyInsideCurrentTaskArea() {
        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null || taskDestination == null) return false;

        // Startup readiness is broader than combat arrival: retain a supplied loadout
        // within a local scene's distance, then let normal travel reach the exact task spot.
        if (here.getPlane() == taskDestination.getPlane()
                && here.distanceTo2D(taskDestination) <= Math.max(64, config.attackRadius() + 10)) {
            return true;
        }

        DroSlayerLocation location = DroSlayerLocation.fromName(taskLocationName);
        if (location == null) return false;
        WorldPoint target = location.getWorldPoint();
        WorldPoint approach = location.getEfficientApproachWorldPoint();
        if (target == null || approach == null || approach.equals(target)) return false;

        // Dungeon coordinates are commonly thousands of Y tiles away from their surface entrance.
        // Match the same handoff-space test used by DroSlayerRuntime.
        return here.getPlane() == target.getPlane()
                && here.distanceTo2D(target) <= 1024
                && here.distanceTo2D(approach) > 512;
    }

    private void finishTaskAndChooseNextStop() {
        if (isWildernessSlayer() && config.getNewTask()) {
            resetTravelData();
            initialSetupDone = false;
            wildernessMasterBeforeRestore = true;
            log.info("Wilderness task complete; getting next assignment before Ferox restoration and banking");
            DroSlayerPlugin.setState(DroSlayerState.GETTING_TASK);
            return;
        }
        beginFeroxRecovery(config.getNewTask() ? DroSlayerState.GETTING_TASK : DroSlayerState.IDLE, "task complete");
    }

    private void resetTravelData() {
        // Clearing the assignment also ends its optional cannon disable/restock policy.
        // Both completion paths and detection of no remaining task reach this boundary.
        cannonPolicy.reset();
        resetCannonWithdrawal();
        DroSlayerRuntime.cancelWalk();
        taskDestination = null;
        godWarsAccessStep = null;
        godWarsAccessDiagnosticAt = 0;
        godWarsAccessClickAt = 0;
        godWarsAccessAttempts = 0;
        deepWildernessStairClickAt = 0;
        deepWildernessStairAttempts = 0;
        taskLocationName = "";
        taskLocationResolutionFailures = 0;
        unresolvedTaskName = null;
        bankLoadFailures = 0;
        lastBankLoadFailureAt = 0L;
        DroSlayerPlugin.setCurrentLocation("");
        // Reset cannon state
        isUsingCannon = false;
        cannonSpot = null;
        // Reset active JSON profile
        activeJsonProfile = null;
        missingAncientSpellbookLogged = false;
        dustBurstFallback = false;
        dustBurstFallbackPending = false;
        dustBurstFallbackExitAttempts = 0;
        dustBurstSwapStarted = 0;
        wildernessAssignment = null;
        revenantRotation.reset();
        templeRestoreDeadline = 0;
        templeRestoreCooldown = 0;
        templeRestoreLastClick = 0;
        // Reset task completion loot delay
        taskCompletedLooting = false;
        postTaskEscapeAttempted = false;
        // Note: cannonPlaced is NOT reset here - we track actual cannon placement separately
    }

    // ==================== Prayer Methods ====================

    /**
     * Sets up autocast spell for AoE (burst/barrage) profiles.
     * Should be called when the player is stationary (at POH after spellbook swap,
     * or at task location before combat) to avoid UI interaction being interrupted by movement.
     */
    private void setupAutoCastIfNeeded() {
        if (isWildernessSlayer() && !wildernessBurstSelected()) return;
        if (!hasAncientAutocastWeapon()) return;
        DroSlayerCombatStyle style = activeJsonProfile == null ? null : activeJsonProfile.getParsedStyle();
        var setup = getActiveInventorySetup();
        String setupName = setup == null ? "" : setup.getName().toLowerCase(java.util.Locale.ROOT);
        if (setupName.contains("barrage")) style = DroSlayerCombatStyle.BARRAGE;
        else if (setupName.contains("burst")) style = DroSlayerCombatStyle.BURST;
        if (wildernessBurstSelected()) style = DroSlayerCombatStyle.BURST;
        if (style != DroSlayerCombatStyle.BURST && style != DroSlayerCombatStyle.BARRAGE) return;
        if (!ancientAutocastReady()) return;

        Rs2CombatSpells spell = (style == DroSlayerCombatStyle.BARRAGE)
                ? Rs2CombatSpells.ICE_BARRAGE
                : Rs2CombatSpells.ICE_BURST;

        Rs2CombatSpells currentSpell = Rs2Magic.getCurrentAutoCastSpell();
        log.debug("setupAutoCastIfNeeded: Current autocast={}, desired={}",
                currentSpell != null ? currentSpell.name() : "NONE", spell.name());

        if (currentSpell != spell) {
            log.info("Setting autocast to {} (current: {})", spell.name(),
                    currentSpell != null ? currentSpell.name() : "NONE");

            // Use Rs2Combat API (requires Combat Options tab to have a hotkey assigned)
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> {
                Rs2Combat.setAutoCastSpell(spell, false);
            });
            sleepUntil(() -> Rs2Magic.getCurrentAutoCastSpell() == spell, 5000);

            Rs2CombatSpells afterSpell = Rs2Magic.getCurrentAutoCastSpell();
            if (afterSpell == spell) {
                log.info("Autocast set to {} successfully", spell.name());
            } else {
                log.warn("Failed to set autocast to {} - current is now: {}. " +
                        "Ensure Combat Options tab has a hotkey assigned in OSRS settings.", spell.name(),
                        afterSpell != null ? afterSpell.name() : "NONE");
            }
        } else {
            log.debug("Autocast already set to {}", spell.name());
        }
    }

    /**
     * Activates the appropriate prayer based on the active JSON profile and prayer style.
     * For flicking modes, prayer activation is handled by the flicker script.
     */
    private boolean isElderChaosDruidLocation() {
        return isWildernessSlayer() && wildernessAssignment != null
            && wildernessAssignment.getLocationName().equals("Chaos Temple - Elder Chaos druids");
    }

    private boolean isWildernessChaosDruidTask() {
        if (!isWildernessSlayer()) return false;
        String task = currentTaskName();
        return task != null && Task.getTask(task) == Task.CHAOS_DRUIDS;
    }

    private String currentTaskProfileName() {
        return isElderChaosDruidLocation()
                ? DroSlayerProfileManager.ELDER_CHAOS_DRUID_PROFILE : currentTaskName();
    }

    private DroSlayerPrayer getActiveProtectionPrayer() {
        DroSlayerPrayer configured = config == null
                ? DroSlayerPrayer.TASK_PROFILE
                : config.selectedProtectionPrayer();
        if (configured == null) configured = DroSlayerPrayer.TASK_PROFILE;

        if (configured == DroSlayerPrayer.TASK_PROFILE) {
            String task = currentTaskProfileName();
            DroSlayerPrayer edited = task == null ? null
                    : editedTaskPrayers.get(task.trim().toLowerCase(java.util.Locale.ROOT));
            if (edited != null) return edited;

            if (activeJsonProfile != null && activeJsonProfile.hasPrayer()) {
                DroSlayerPrayer profilePrayer = activeJsonProfile.getParsedPrayer();
                return profilePrayer == null ? DroSlayerPrayer.NONE : profilePrayer;
            }
            return DroSlayerPrayer.NONE;
        }
        return configured;
    }

    private void activateProfilePrayer() {
        PrayerFlickStyle style = config.prayerFlickStyle();

        // Offensive prayers are independent of the selected protection prayer.
        activateOffensivePrayer();

        if (style == PrayerFlickStyle.OFF) return;

        // Flicking modes are handled by FlickerScript, not here.
        if (style == PrayerFlickStyle.LAZY_FLICK
                || style == PrayerFlickStyle.PERFECT_LAZY_FLICK
                || style == PrayerFlickStyle.MIXED_LAZY_FLICK) {
            return;
        }

        DroSlayerPrayer selected = getActiveProtectionPrayer();
        if (selected != DroSlayerPrayer.NONE) {
            activatePrayer(selected);
        } else if (style == PrayerFlickStyle.ALWAYS_ON) {
            log.debug("ALWAYS_ON selected with Protection Prayer=None");
        }
    }

    /**
     * Prayer changes are owned by BaseProfileDro. Exact flick modes use the urgent
     * maintenance path so they keep their game-tick timing without a second behavior profile.
     */
    private boolean setAutoRetaliateHumanized(boolean enabled) {
        // General combat preferences must not interrupt assembly, pickup or repair.
        boolean desired = autoRetaliateTarget(enabled && !cannonRepairPending, isUsingCannon,
                cannonBuildStage(), cannonPickupStarted != 0L);
        Boolean current = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getVarpValue(net.runelite.api.gameval.VarPlayerID.OPTION_NODEF) == 0).orElse(null);
        if (current == null) return false;
        if (current == desired) return true;
        return DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                (java.util.function.BooleanSupplier) () -> Rs2Combat.setAutoRetaliate(desired));
    }

    static boolean autoRetaliateTarget(boolean requested, boolean usingCannon, int stage, boolean pickingUp) {
        return requested && !pickingUp && !(usingCannon && stage < 4);
    }

    private boolean togglePrayerHumanized(Rs2PrayerEnum prayer, boolean enabled) {
        if (prayer == null) return false;
        return DroSlayerRuntime.performAction(
                BaseProfileDro.ActionPhase.MAINTENANCE, true,
                () -> Rs2Prayer.toggle(prayer, enabled, true));
    }

    /**
     * Helper method to activate a specific DroSlayerPrayer
     */
    private void activatePrayer(DroSlayerPrayer slayerPrayer) {
        if (slayerPrayer.getPrayer() != null) {
            Rs2PrayerEnum prayer = slayerPrayer.getPrayer();
            if (!Rs2Prayer.isPrayerActive(prayer)) {
                togglePrayerHumanized(prayer, true);
                log.debug("Activated prayer: {}", slayerPrayer.getDisplayName());
            }
        }
    }

    /**
     * Deactivates profile-specific prayers.
     */
    private void deactivateProfilePrayer() {
        DroSlayerPrayer selected = getActiveProtectionPrayer();
        if (departureProtectionPrayer == null && selected != DroSlayerPrayer.NONE && selected.getPrayer() != null
                && Rs2Prayer.isPrayerActive(selected.getPrayer())) {
            togglePrayerHumanized(selected.getPrayer(), false);
        }

        if (config.useOffensivePrayers()) {
            deactivateOffensivePrayers();
        }
    }

    private void holdDepartureProtection() {
        if (departureProtectionPrayer != null || !isWildernessSlayer()
                || !Rs2Pvp.isInWilderness() || atFerox()
                || config.prayerFlickStyle() == PrayerFlickStyle.OFF) return;
        DroSlayerPrayer prayer = getActiveProtectionPrayer();
        if (prayer == null || prayer.getPrayer() == null) return;
        departureProtectionPrayer = prayer;
        DroSlayerPlugin.getFlickerScript().holdDeparturePrayer(prayer);
        activatePrayer(prayer);
    }

    private void maintainDepartureProtection() {
        if (departureProtectionPrayer == null) return;
        if (atFerox()) {
            DroSlayerPrayer prayer = departureProtectionPrayer;
            clearDepartureProtection();
            if (Rs2Prayer.isPrayerActive(prayer.getPrayer())) togglePrayerHumanized(prayer.getPrayer(), false);
        } else {
            activatePrayer(departureProtectionPrayer);
        }
    }

    private void clearDepartureProtection() {
        departureProtectionPrayer = null;
        DroSlayerPlugin.getFlickerScript().holdDeparturePrayer(null);
    }

    // Varbits for prayer unlock status
    private static final int RIGOUR_UNLOCKED_VARBIT = 5451;
    private static final int AUGURY_UNLOCKED_VARBIT = 5452;

    /**
     * Activates the best offensive prayer for the combat style actually being used.
     * Equipment/autocast takes priority over the task profile so a profile such as
     * Hellhounds = ranged cannot force Eagle Eye while the player is meleeing.
     */
    private void activateOffensivePrayer() {
        if (!config.useOffensivePrayers()) {
            return;
        }

        String task = currentTaskProfileName();
        DroSlayerOffensivePrayer selection = editedOffensivePrayers.getOrDefault(
                task == null ? "" : task.trim().toLowerCase(java.util.Locale.ROOT),
                activeJsonProfile == null ? DroSlayerOffensivePrayer.AUTOMATIC
                        : activeJsonProfile.getParsedOffensivePrayer());
        Rs2PrayerEnum override = selection.getPrayer();
        if (lastOffensiveOverride != null && lastOffensiveOverride != override
                && Rs2Prayer.isPrayerActive(lastOffensiveOverride)) {
            togglePrayerHumanized(lastOffensiveOverride, false);
            return;
        }
        lastOffensiveOverride = override;
        if (selection == DroSlayerOffensivePrayer.NONE) {
            for (DroSlayerOffensivePrayer option : DroSlayerOffensivePrayer.values()) {
                Rs2PrayerEnum prayer = option.getPrayer();
                if (prayer != null && Rs2Prayer.isPrayerActive(prayer)) {
                    togglePrayerHumanized(prayer, false);
                    return;
                }
            }
            return;
        }
        if (override != null) {
            // Clear previous offensive boosts without changing protection prayers.
            for (DroSlayerOffensivePrayer option : DroSlayerOffensivePrayer.values()) {
                Rs2PrayerEnum other = option.getPrayer();
                if (other != null && other.getIndex() != override.getIndex() && Rs2Prayer.isPrayerActive(other)) {
                    togglePrayerHumanized(other, false);
                    return;
                }
            }
            if (!Rs2Prayer.isPrayerActive(override)) togglePrayerHumanized(override, true);
            return;
        }

        DroSlayerCombatStyle combatStyle = detectCurrentCombatStyle();
        int prayerLevel = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);

        switch (combatStyle) {
            case MELEE:
                activateBestMeleePrayer(prayerLevel);
                break;
            case RANGED:
                activateBestRangedPrayer(prayerLevel);
                break;
            case MAGIC:
            case BURST:
            case BARRAGE:
                activateBestMagicPrayer(prayerLevel);
                break;
        }
    }

    /**
     * Detects the player's current fighting style from the weapon actually equipped.
     * Active autocast is authoritative for magic. Unrecognised weapons fall back to
     * melee rather than blindly trusting a task profile that may describe another setup.
     */
    private DroSlayerCombatStyle detectCurrentCombatStyle() {
        if (Rs2Magic.getCurrentAutoCastSpell() != null) {
            if (activeJsonProfile != null && activeJsonProfile.hasStyle()) {
                DroSlayerCombatStyle profileStyle = activeJsonProfile.getParsedStyle();
                if (profileStyle == DroSlayerCombatStyle.BURST || profileStyle == DroSlayerCombatStyle.BARRAGE) {
                    return profileStyle;
                }
            }
            return DroSlayerCombatStyle.MAGIC;
        }

        if (isWearingAnyWeapon(
                "bow", "crossbow", "blowpipe", "ballista", "atlatl",
                "chinchompa", "dart", "knife", "javelin", "thrownaxe",
                "toktz-xil-ul")) {
            return DroSlayerCombatStyle.RANGED;
        }

        if (isWearingAnyWeapon(
                "staff", "wand", "trident", "sceptre", "scepter", "kodai",
                "nightmare", "tumeken", "sanguinesti", "dawnbringer")) {
            return DroSlayerCombatStyle.MAGIC;
        }

        return DroSlayerCombatStyle.MELEE;
    }

    private boolean isWearingAnyWeapon(String... names) {
        Rs2ItemModel weapon = Rs2Equipment.get(net.runelite.api.EquipmentInventorySlot.WEAPON);
        if (weapon == null || weapon.getName() == null) return false;
        for (String name : names) {
            if (weapon.getName().toLowerCase(java.util.Locale.ROOT).contains(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Activates the best melee offensive prayer available.
     * Priority: Piety > Chivalry > Ultimate Strength + Incredible Reflexes
     */
    private void activateBestMeleePrayer(int prayerLevel) {
        // Piety requires 70 Prayer and Knight Waves completion
        if (prayerLevel >= 70) {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PIETY)) {
                togglePrayerHumanized(Rs2PrayerEnum.PIETY, true);
                if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PIETY)) {
                    log.debug("Activated Piety");
                    return;
                }
            } else {
                return; // Already active
            }
        }

        // Chivalry requires 60 Prayer and Knight Waves completion
        if (prayerLevel >= 60) {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.CHIVALRY)) {
                togglePrayerHumanized(Rs2PrayerEnum.CHIVALRY, true);
                if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.CHIVALRY)) {
                    log.debug("Activated Chivalry");
                    return;
                }
            } else {
                return;
            }
        }

        // Fallback to separate strength + attack prayers
        if (prayerLevel >= 31 && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.ULTIMATE_STRENGTH)) {
            togglePrayerHumanized(Rs2PrayerEnum.ULTIMATE_STRENGTH, true);
        }
        if (prayerLevel >= 34 && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.INCREDIBLE_REFLEXES)) {
            togglePrayerHumanized(Rs2PrayerEnum.INCREDIBLE_REFLEXES, true);
        }
    }

    /**
     * Activates the best ranged offensive prayer available.
     * Priority: Rigour > Eagle Eye > Hawk Eye > Sharp Eye
     */
    private void activateBestRangedPrayer(int prayerLevel) {
        // Rigour requires 74 Prayer and Rigour scroll
        if (prayerLevel >= 74) {
            boolean rigourUnlocked = Microbot.getVarbitValue(RIGOUR_UNLOCKED_VARBIT) == 1;
            if (rigourUnlocked) {
                if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.RIGOUR)) {
                    togglePrayerHumanized(Rs2PrayerEnum.RIGOUR, true);
                    log.debug("Activated Rigour");
                }
                return;
            }
        }

        // Eagle Eye requires 44 Prayer
        if (prayerLevel >= 44) {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.EAGLE_EYE)) {
                togglePrayerHumanized(Rs2PrayerEnum.EAGLE_EYE, true);
                log.debug("Activated Eagle Eye");
            }
            return;
        }

        // Hawk Eye requires 26 Prayer
        if (prayerLevel >= 26) {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.HAWK_EYE)) {
                togglePrayerHumanized(Rs2PrayerEnum.HAWK_EYE, true);
                log.debug("Activated Hawk Eye");
            }
            return;
        }

        // Sharp Eye requires 8 Prayer
        if (prayerLevel >= 8) {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.SHARP_EYE)) {
                togglePrayerHumanized(Rs2PrayerEnum.SHARP_EYE, true);
                log.debug("Activated Sharp Eye");
            }
        }
    }

    /**
     * Activates the best magic offensive prayer available.
     * Priority: Augury > Mystic Might > Mystic Lore > Mystic Will
     */
    private void activateBestMagicPrayer(int prayerLevel) {
        // Augury requires 77 Prayer and Augury scroll
        if (prayerLevel >= 77) {
            boolean auguryUnlocked = Microbot.getVarbitValue(AUGURY_UNLOCKED_VARBIT) == 1;
            if (auguryUnlocked) {
                if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.AUGURY)) {
                    togglePrayerHumanized(Rs2PrayerEnum.AUGURY, true);
                    log.debug("Activated Augury");
                }
                return;
            }
        }

        // Mystic Might requires 45 Prayer
        if (prayerLevel >= 45) {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.MYSTIC_MIGHT)) {
                togglePrayerHumanized(Rs2PrayerEnum.MYSTIC_MIGHT, true);
                log.debug("Activated Mystic Might");
            }
            return;
        }

        // Mystic Lore requires 27 Prayer
        if (prayerLevel >= 27) {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.MYSTIC_LORE)) {
                togglePrayerHumanized(Rs2PrayerEnum.MYSTIC_LORE, true);
                log.debug("Activated Mystic Lore");
            }
            return;
        }

        // Mystic Will requires 9 Prayer
        if (prayerLevel >= 9) {
            if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.MYSTIC_WILL)) {
                togglePrayerHumanized(Rs2PrayerEnum.MYSTIC_WILL, true);
                log.debug("Activated Mystic Will");
            }
        }
    }

    /**
     * Deactivates all offensive prayers.
     */
    private void deactivateOffensivePrayers() {
        if (lastOffensiveOverride != null && Rs2Prayer.isPrayerActive(lastOffensiveOverride)) {
            togglePrayerHumanized(lastOffensiveOverride, false);
        }
        // Melee prayers
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PIETY)) {
            togglePrayerHumanized(Rs2PrayerEnum.PIETY, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.CHIVALRY)) {
            togglePrayerHumanized(Rs2PrayerEnum.CHIVALRY, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.ULTIMATE_STRENGTH)) {
            togglePrayerHumanized(Rs2PrayerEnum.ULTIMATE_STRENGTH, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.INCREDIBLE_REFLEXES)) {
            togglePrayerHumanized(Rs2PrayerEnum.INCREDIBLE_REFLEXES, false);
        }

        // Ranged prayers
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.RIGOUR)) {
            togglePrayerHumanized(Rs2PrayerEnum.RIGOUR, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.EAGLE_EYE)) {
            togglePrayerHumanized(Rs2PrayerEnum.EAGLE_EYE, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.HAWK_EYE)) {
            togglePrayerHumanized(Rs2PrayerEnum.HAWK_EYE, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.SHARP_EYE)) {
            togglePrayerHumanized(Rs2PrayerEnum.SHARP_EYE, false);
        }

        // Magic prayers
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.AUGURY)) {
            togglePrayerHumanized(Rs2PrayerEnum.AUGURY, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.MYSTIC_MIGHT)) {
            togglePrayerHumanized(Rs2PrayerEnum.MYSTIC_MIGHT, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.MYSTIC_LORE)) {
            togglePrayerHumanized(Rs2PrayerEnum.MYSTIC_LORE, false);
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.MYSTIC_WILL)) {
            togglePrayerHumanized(Rs2PrayerEnum.MYSTIC_WILL, false);
        }
    }

    /**
     * Checks if prayer should be active based on style and combat state
     */
    private boolean shouldPrayerBeActive() {
        PrayerFlickStyle style = config.prayerFlickStyle();

        switch (style) {
            case OFF:
                return false;
            case ALWAYS_ON:
                return true;
            default:
                // Flicking modes handled separately
                return false;
        }
    }

    // ==================== Cannon Helper Methods ====================

    /**
     * Checks if we should use cannon for the given task
     */
    private boolean shouldUseCannon(String taskName) {
        return shouldUseCannon(taskName, false);
    }

    private boolean shouldUseCannon(String taskName, boolean ignoreTripDisabled) {
        if (taskName == null || taskName.isEmpty()) return false;
        cannonPolicy.selectTask(taskName);
        // Record even non-cannon tasks so the next task cannot inherit an old disabled trip.
        if (DroSlayerEntRotation.isEntTask(taskName) || "Black knights".equalsIgnoreCase(taskName.trim())
                || !ignoreTripDisabled && cannonPolicy.isDisabled()) return false;

        // MASTER TOGGLE: Config enableCannon must be ON for any cannon usage
        if (!cannonEnabledForMaster()) {
            return false;
        }

        if (isWildernessSlayer()) {
            return wildernessAssignment != null && wildernessAssignment.isCannonAllowed();
        }
        // Check JSON profile for cannon setting (per-task preference)
        if (activeJsonProfile != null) {
            if (activeJsonProfile.isCannon()) {
                // Profile wants cannon - verify we have a spot for it
                if (CannonSpot.hasSpotForTask(taskName) || activeJsonProfile.hasCannonLocation()) {
                    log.info("Using cannon for '{}' based on JSON profile", taskName);
                    return true;
                } else {
                    log.warn("JSON profile specifies cannon for '{}' but no cannon spot/location found", taskName);
                    return false;
                }
            } else {
                // Profile exists but cannon is disabled for this task
                return false;
            }
        }

        // No profile found - fall back to cannon task list filter
        // Check if task has a predefined cannon spot
        if (!CannonSpot.hasSpotForTask(taskName)) {
            return false;
        }

        // Check if cannon task list is configured (optional filter)
        String cannonTaskList = config.cannonTaskList();
        if (cannonTaskList != null && !cannonTaskList.isEmpty()) {
            String taskLower = taskName.toLowerCase().trim();
            boolean isInList = Arrays.stream(cannonTaskList.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .anyMatch(cannonTask -> taskLower.contains(cannonTask) || cannonTask.contains(taskLower));
            if (!isInList) {
                log.info("Task '{}' not in cannon task list, skipping cannon", taskName);
                return false;
            }
        }

        // Check if we have an inventory setup configured in the profile
        if (activeJsonProfile == null || !activeJsonProfile.hasSetup()) {
            log.warn("Cannon enabled but no inventory setup configured in profile");
            return false;
        }

        return true;
    }

    /**
     * Gets the active inventory setup based on task profile, cannon usage, and config.
     * If cannon is being used, automatically tries to find a cannon variant by appending
     * "-cannon" suffix (e.g., "melee" -> "melee-cannon", "ranged" -> "ranged-cannon").
     * Checks JSON profile first, then falls back to text-based profile.
     */
    private boolean isBabyDragonTarget() {
        if (isWildernessSlayer()) return false;
        String selected = activeJsonProfile != null && activeJsonProfile.hasVariant()
                ? activeJsonProfile.getVariant() : currentTaskName();
        String name = selected == null ? "" : selected.toLowerCase(java.util.Locale.ROOT);
        if (name.contains("baby") && name.contains("dragon")) return true;
        String location = taskLocationName == null ? "" : taskLocationName.toLowerCase(java.util.Locale.ROOT);
        return location.contains("baby") && location.contains("dragon");
    }

    private net.runelite.client.plugins.microbot.inventorysetups.InventorySetup getActiveInventorySetup() {
        // Wilderness setup resolution is isolated from every ordinary profile/default fallback.
        if (isWildernessSlayer()) {
            if (isWildernessAviansieTask()) return findInventorySetupByName("rangewildy");
            if (wildernessBurstSelected()) {
                var burst = findInventorySetupByName("burstwildy");
                if (burst != null) return burst;
                // Latch ordinary Wilderness combat for this task, including spellbook checks.
                dustBurstFallback = true;
                initialSetupDone = false;
                baseBankLoadComplete = false;
                currentInventorySetup = null;
                log.warn("burstwildy setup missing; using selected Wilderness setup without bursting for this task");
            }
            var configured = config.wildernessInventorySetup();
            return configured == null || configured.getName() == null
                    ? null : findInventorySetupByName(configured.getName());
        }
        String setupName = null;

        // Task-specific profile setup has first priority. These are logical names such as
        // melee, ranged, ven, dragon, burst, etc.
        if (!isBabyDragonTarget() && activeJsonProfile != null && activeJsonProfile.hasSetup()) {
            setupName = activeJsonProfile.getSetup();
            log.debug("Using setup name '{}' from JSON profile", setupName);
        }

        if (setupName != null && !setupName.isEmpty()) {
            // If using cannon, prefer a dedicated cannon variant when one exists.
            if (isUsingCannon && !setupName.toLowerCase().endsWith("-cannon")) {
                String cannonName = setupName + "-cannon";
                net.runelite.client.plugins.microbot.inventorysetups.InventorySetup cannonSetup = findInventorySetupByName(cannonName);
                if (cannonSetup != null) {
                    log.info("Using cannon inventory setup '{}' (cannon enabled)", cannonName);
                    return cannonSetup;
                }
                log.debug("Cannon setup '{}' not found, falling back to '{}'", cannonName, setupName);
            }

            net.runelite.client.plugins.microbot.inventorysetups.InventorySetup profileSetup = findInventorySetupByName(setupName);
            if (profileSetup != null) {
                log.debug("Using inventory setup '{}' from profile", setupName);
                return profileSetup;
            }

            log.debug("Task-specific inventory setup '{}' not found; trying configured Default setup", setupName);
        }

        // Visible config fallback. This avoids requiring the user to hand-edit droslayer2-profiles.json.
        // Resolve by name against the live Inventory Setups list because config stores a serialized snapshot.
        net.runelite.client.plugins.microbot.inventorysetups.InventorySetup configured = config.defaultInventorySetup();
        if (configured != null && configured.getName() != null && !configured.getName().trim().isEmpty()) {
            net.runelite.client.plugins.microbot.inventorysetups.InventorySetup liveSetup =
                    findInventorySetupByName(configured.getName().trim());
            if (liveSetup != null) {
                if (setupName == null || setupName.isEmpty()) {
                    String currentTaskName = currentTaskName();
                    log.info("Using configured default inventory setup '{}' for task '{}'",
                            liveSetup.getName(), currentTaskName == null ? "unknown" : currentTaskName);
                } else {
                    log.info("Using configured default inventory setup '{}' because task setup '{}' was unavailable",
                            liveSetup.getName(), setupName);
                }
                return liveSetup;
            }
            log.warn("Configured Default setup '{}' no longer exists in Inventory Setups", configured.getName());
        }

        if (setupName != null && !setupName.isEmpty()) {
            log.warn("No usable Inventory Setup found for required task setup '{}'. Select a fallback under Inventory Setup -> Default setup.", setupName);
        } else {
            log.warn("No Inventory Setup selected for this task. Select one under Inventory Setup -> Default setup, or use Save current loadout. Keeping current gear/inventory for now.");
        }
        return null;
    }

    /**
     * Finds an inventory setup by name from the inventory setups plugin
     */
    private net.runelite.client.plugins.microbot.inventorysetups.InventorySetup findInventorySetupByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        // Look up from InventorySetupsPlugin by name
        var allSetups = MInventorySetupsPlugin.getInventorySetups();
        if (allSetups != null) {
            var found = allSetups.stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(setup -> setup.getName().equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);
            if (found != null) {
                log.debug("Found inventory setup '{}' from InventorySetupsPlugin", name);
                return found;
            }
        }

        log.debug("Inventory setup '{}' not found; optional task setup lookup will fall back when possible", name);
        return null;
    }

    /**
     * Checks if player has cannon parts in inventory
     */
    private boolean hasCannonParts() {
        return Rs2Inventory.hasItem("Cannon base") &&
               Rs2Inventory.hasItem("Cannon stand") &&
               Rs2Inventory.hasItem("Cannon barrels") &&
               Rs2Inventory.hasItem("Cannon furnace");
    }

    /**
     * Gets the count of cannonballs in inventory
     */
    private int getCannonballCount() {
        return Rs2Inventory.itemQuantity("Steel cannonball", true)
                + Rs2Inventory.itemQuantity("Cannonball", true)
                + Rs2Inventory.itemQuantity("Granite cannonball", true);
    }

    /** Optional stock-only bank trips share the existing supply banking flow. */
    private boolean shouldRestockCannonballs() {
        int remaining = Rs2Slayer.getSlayerTaskSize();
        if (remaining >= 0 && remaining < 10) return false;
        return cannonPolicy.needsRestock(config.bankForCannonballs(), config.enableAutoBanking(),
                isUsingCannon, getCannonballCount() + loadedCannonballCount(),
                isWildernessSlayer(), config.cannonballThreshold());
    }

    private int loadedCannonballCount() {
        return cannonPlaced ? Math.max(0, Microbot.getVarbitPlayerValue(net.runelite.api.VarPlayer.CANNON_AMMO)) : 0;
    }

    private boolean isEntTask() {
        return DroSlayerEntRotation.isEntTask(currentTaskName());
    }

    private boolean isSouthernRevenantGoblinTask() {
        return isWildernessSlayer() && wildernessAssignment != null
                && "Revenant Caves - Southern goblins".equals(wildernessAssignment.getLocationName());
    }

    /** Called by ActorDeath on the client thread; records only our own nearby rotation targets. */
    public void onEntDeath(net.runelite.api.NPC npc) {
        boolean revenants = isSouthernRevenantGoblinTask();
        String target = revenants ? "Revenant goblin" : "Ent";
        if ((!revenants && !isEntTask()) || DroSlayerPlugin.getState() != DroSlayerState.FIGHTING
                || !target.equalsIgnoreCase(npc.getName())
                || npc.getInteracting() != Microbot.getClient().getLocalPlayer()
                || taskDestination == null
                || npc.getWorldLocation().distanceTo(taskDestination) > config.attackRadius()) return;
        (revenants ? revenantRotation : entRotation).killed(Microbot.getClient().getWorld(), npc.getIndex());
    }

    private boolean rotateEntWorld() {
        if (!isEntTask()) { entRotation.reset(); return false; }
        int world = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getWorld()).orElse(-1);
        if (!entRotation.pending(world)) return false;
        boolean liveEntNearby = !Microbot.getRs2NpcCache().query()
                .where(npc -> "Ent".equalsIgnoreCase(npc.getName()) && !npc.isDead()
                        && npc.getHealthRatio() != 0)
                .where(npc -> taskDestination != null
                        && npc.getWorldLocation().distanceTo(taskDestination) <= config.attackRadius())
                .toListOnClientThread().isEmpty();
        if (liveEntNearby) return false;
        Microbot.status = "Ent defeated, no live Ents nearby; waiting to hop out of combat";
        if (isBeingAttacked() || Rs2Player.isInCombat()) return true;
        if (entRotation.tryHop(System.currentTimeMillis())) hopToNewWorld();
        return true;
    }

    private boolean rotateRevenantWorld() {
        if (!isSouthernRevenantGoblinTask()) { revenantRotation.reset(); return false; }
        int world = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getWorld()).orElse(-1);
        if (!revenantRotation.pending(world)) return false;
        boolean liveGoblinNearby = !Microbot.getRs2NpcCache().query()
                .where(npc -> "Revenant goblin".equalsIgnoreCase(npc.getName()) && !npc.isDead()
                        && npc.getHealthRatio() != 0)
                .where(npc -> taskDestination != null
                        && npc.getWorldLocation().distanceTo(taskDestination) <= config.attackRadius())
                .toListOnClientThread().isEmpty();
        if (!revenantRotation.readyToHop(world, 2, liveGoblinNearby)) return false;
        Microbot.status = "Revenant goblins cleared or two kills reached; waiting to hop out of combat";
        if (isBeingAttacked() || Rs2Player.isInCombat()) return true;
        if (revenantRotation.tryHop(System.currentTimeMillis())) hopToNewWorld();
        return true;
    }

    /** Exhausted ammunition disables only cannon actions unless replenishment is enabled. */
    private boolean checkAndDisableCannonIfLow() {
        if (!isUsingCannon || !cannonEnabledForMaster() || shouldRestockCannonballs()) return false;
        int loaded = loadedCannonballCount();
        if (getCannonballCount() + loaded > 0) return false;
        disableCannonForTrip("Cannon ammunition depleted; continuing ordinary attacks");
        return true;
    }

    /** The cannon is a preference; preserve the task destination and any deployed cannon for later pickup. */
    private void disableCannonForTrip(String reason) {
        cannonRepairPending = false;
        cannonPolicy.selectTask(currentTaskName());
        cannonPolicy.disable();
        isUsingCannon = false;
        cannonFired = false;
        cannonSpot = null;
        cannonFireAttemptStarted = 0L;
        log.warn("{}; cannon disabled for this trip, normal Slayer combat continues", reason);
    }

    /** Return true while refilling/restarting so combat cannot cancel the cannon interaction. */
    private boolean handleCannonMaintenance() {
        traceDroAction("handleCannonMaintenance");
        if (cannonPickupStarted != 0L || !isUsingCannon || !cannonPlaced) {
            cannonRepairPending = false;
            return false;
        }
        long checkedAt = System.currentTimeMillis();
        if (checkedAt < cannonMaintenanceRetryAt) return false;
        if (checkedAt >= cannonDiagnosticAt) {
            cannonDiagnosticAt = checkedAt + 15_000L;
            log.info("Cannon diagnostic: firedFlag={} carried={} {}", cannonFired,
                    getCannonballCount(), DroCannon.diagnostic());
        }
        if (!DroCannon.isPresent()) {
            cannonRepairPending = false;
            cannonFired = false;
            cannonFireAttemptStarted = 0L;
            cannonMaintenanceRetryAt = System.currentTimeMillis() + 5_000L;
            return false; // Optional missing cannon must not consume the combat tick.
        }
        boolean broken = DroCannon.isBroken();
        long now = System.currentTimeMillis();
        if (broken) {
            if (cannonMaintenanceTimedOut(now)) return false;
            cannonRepairPending = true;
            cannonFired = false;
            Microbot.status = "Repairing owned cannon";
            // Aggressive NPCs can cancel the approach just like cannon assembly.
            if (!setAutoRetaliateHumanized(false)) return true;
            // Let the issued interaction reach the cannon before retrying it.
            if (Rs2Player.isMoving()) return true;
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.ACTIVE_WORK, false,
                    (java.util.function.BooleanSupplier) DroCannon::repair);
            return true;
        }
        if (cannonRepairPending) {
            // A present working object is the repair receipt; a click return is not.
            cannonRepairPending = false;
            cannonFireAttemptStarted = 0L;
        }
        if (shouldRestockCannonballs()) return false;
        checkAndDisableCannonIfLow();
        if (!isUsingCannon) return false;
        int ammo = Microbot.getVarbitPlayerValue(net.runelite.api.VarPlayer.CANNON_AMMO);
        if (cannonPreviousAmmo >= 0 && ammo > cannonPreviousAmmo)
            cannonRefillThreshold = ThreadLocalRandom.current().nextInt(7, 13);
        cannonPreviousAmmo = ammo;
        if (DroCannon.isFiring() && (!isWildernessSlayer()
                || ammo > cannonRefillThreshold || getCannonballCount() == 0)) {
            cannonFired = true;
            cannonFireAttemptStarted = 0L;
            return false;
        }
        if (cannonMaintenanceTimedOut(now)) return false;
        DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.ACTIVE_WORK, false,
                () -> DroCannon.repair() || DroCannon.refill(isWildernessSlayer() ? cannonRefillThreshold : 15));
        cannonFired = DroCannon.isFiring();
        return true;
    }

    private boolean cannonMaintenanceTimedOut(long now) {
        if (cannonFireAttemptStarted == 0L) cannonFireAttemptStarted = now;
        if (now - cannonFireAttemptStarted <= 30_000L) return false;
        log.warn("Cannon repair/firing not observed; allowing combat before retrying maintenance");
        cannonRepairPending = false;
        cannonFireAttemptStarted = 0L;
        cannonMaintenanceRetryAt = now + 10_000L;
        return true;
    }

    /**
     * Sets up the cannon by using the cannon base from inventory
     * @return true if setup was initiated successfully
     */
    private int cannonBuildStage() {
        return Microbot.getVarbitPlayerValue(net.runelite.api.gameval.VarPlayerID.DROPCANNON);
    }

    /** One observed assembly step per script tick; ordinary combat cannot interrupt it. */
    private boolean advanceCannonAssembly() {
        int stage = cannonBuildStage();
        if (isWildernessSlayer() && stage > 0 && cannonDeploymentWorld == 0) {
            // The assembly varp survives world changes. Only a local owned object can
            // establish a deployment world after a script restart.
            var observed = findOwnedCannon(50);
            if (observed == null) {
                disableCannonForTrip("Existing cannon world is unknown and no owned cannon is visible");
                return true;
            }
            cannonDeploymentWorld = currentWorld();
            lastCannonPlacementTile = observed.getWorldLocation();
        }
        long now = System.currentTimeMillis();
        if (stage != cannonAssemblyStage || cannonAssemblyProgressAt == 0) {
            cannonAssemblyStage = stage;
            if (stage == 4) cannonFireAttemptStarted = 0L;
            if (stage > 0 && cannonDeploymentWorld == 0) cannonDeploymentWorld = currentWorld();
            cannonAssemblyProgressAt = now;
            cannonAssemblyAttemptAt = now;
            log.info("Cannon watchdog: assembly stage={} (0=carried, 1=base, 2=stand, 3=barrels, 4=complete)", stage);
        }
        if (stage == 4) {
            cannonPlaced = true;
            cannonFired = false;
            if (config.enableAutoCombat()) setAutoRetaliateHumanized(!isElderChaosDruidLocation());
            return true;
        }
        Microbot.status = "Cannon assembly: stage " + stage;
        if (now - cannonAssemblyProgressAt > 30000L) {
            cannonPlaced = stage > 0;
            if (stage > 0) {
                cannonAssemblyRecovery = true;
                cannonStartupPickup = false;
                log.warn("Cannon assembly stalled at stage {}; collecting partial cannon before continuing", stage);
                return false;
            }
            disableCannonForTrip("Cannon assembly stalled at stage " + stage);
            return true;
        }
        // Auto-retaliation can cancel construction. Restore it only after stage 4.
        if (!setAutoRetaliateHumanized(false)) return false;
        if (now - cannonAssemblyAttemptAt < 3000L) return false;
        boolean idle = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getLocalPlayer() != null
                    && Microbot.getClient().getLocalPlayer().getAnimation() == -1).orElse(false);
        if (!idle || Rs2Player.isMoving()) return false;
        cannonAssemblyAttemptAt = now;
        if (stage == 0) {
            if (hasCannonParts()) setupCannon();
            return false;
        }
        Integer[] partialIds = {7, 8, 9, 43029, 43030, 43031};
        var partial = SceneObjects.findObject(partialIds);
        WorldPoint owned = Microbot.getClientThread().runOnClientThreadOptional(() ->
                WorldPoint.fromCoord(Microbot.getClient().getVarpValue(net.runelite.api.VarPlayer.CANNON_COORD))).orElse(null);
        if (partial == null || owned == null || partial.getWorldLocation().distanceTo(owned) > 2) {
            log.info("Cannon watchdog: waiting for owned partial cannon at {}", owned);
            return false;
        }
        if (SceneObjects.hasAction(partial, "Build", true)) {
            boolean clicked = DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, true,
                    () -> SceneObjects.interact(partial, "Build"));
            log.info("Cannon watchdog: resume Build stage={} dispatched={}", stage, clicked);
        } else {
            log.warn("Cannon watchdog: partial object {} has no Build action", partial.getId());
        }
        return false;
    }

    private boolean setupCannon() {
        lastCannonPlacementTile = Rs2Player.getWorldLocation();
        // Use cannon base from inventory to start setup
        if (DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.ACTIVE_WORK, false, () -> Rs2Inventory.interact("Cannon base", "Set-up"))) {
            cannonDeploymentWorld = currentWorld();
            cannonWorldReturnAttempts = 0;
            cannonWorldReturnSuppressed = false;
            log.info("Setting up cannon...");
            return true;
        }
        return false;
    }

    /**
     * Starts the cannon by refilling it with cannonballs.
     * Uses DroCannon.refill() which handles loading cannonballs and starting the cannon.
     * @return true if cannon was refilled/started successfully
     */
    private boolean fireCannon() {
        // Just use DroCannon.refill() - this handles loading cannonballs and starting the cannon
        // This is the same approach used by AIOFighter
        if (DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.ACTIVE_WORK, false, () -> { return DroCannon.refill(); })) {
            log.info("Cannon refilled and started via DroCannon.refill()");
            return true;
        }
        log.debug("DroCannon.refill() returned false - cannon may already be full or not placed");
        return false;
    }

    /**
     * Handles the full cannon pickup process including walking to it if needed.
     * @return true if cannon was successfully picked up (no longer exists), false if still in progress
     */
    private boolean handleCannonPickup() {
        traceDroAction("handleCannonPickup");
        if (reconcileCarriedCannon()) return true;
        if (holdForCannonWorldReturn()) return false;
        long now = System.currentTimeMillis();
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return false;
        // Scene absence at another task/bank is not evidence that the cannon is gone.
        if (lastCannonPlacementTile != null
                && player.distanceTo(lastCannonPlacementTile) > 5) {
            if (cannonPickupStarted == 0L) cannonPickupStarted = now;
            if (now - cannonPickupStarted > 90_000L)
                return abandonCannonPickup("Could not reach the saved cannon tile within 90 seconds");
            DroSlayerRuntime.walkAdaptive(config, lastCannonPlacementTile, 2, taskLocationName);
            return false;
        }
        if (cannonPickupStarted == 0L) cannonPickupStarted = now;
        var cannon = findOwnedCannon(50);
        if (cannon == null) {
            if (lastCannonPlacementTile != null) {
                boolean atSite = player.distanceTo(lastCannonPlacementTile) <= 2;
                boolean moving = Rs2Player.isMoving();
                if (!atSite) {
                    cannonPickupWatchdog.missing(now, false);
                    DroSlayerRuntime.walkAdaptive(config, lastCannonPlacementTile, 1, taskLocationName);
                    if (now - cannonPickupStarted > 90_000L)
                        return abandonCannonPickup("Could not settle at the saved cannon site within 90 seconds");
                    return false;
                }
                DroSlayerRuntime.cancelWalk();
                if (!cannonPickupWatchdog.missing(now, !moving)) {
                    if (now - cannonPickupStarted > 90_000L)
                        return abandonCannonPickup("Movement prevented a settled cannon search within 90 seconds");
                    return false;
                }
                log.warn("Cannon lookup unconfirmed after ten stationary seconds: player={} saved={} world={} stage={}",
                        player, lastCannonPlacementTile, cannonDeploymentWorld, cannonBuildStage());
            }
            return abandonCannonPickup("Owned cannon absent after a settled search at its saved site");
        }
        cannonPickupWatchdog.reset();
        if (now - cannonPickupStarted > 90_000L)
            return abandonCannonPickup("Cannon pickup not confirmed after 90 seconds");
        // Retaliation can repeatedly interrupt the pickup while aggressive NPCs attack.
        if (!setAutoRetaliateHumanized(false)) return false;
        int pickupSlots = getCannonballCount() > 0 ? 4 : 5;
        if (Rs2Inventory.emptySlotCount() < pickupSlots) {
            List<Rs2ItemModel> food = usableFood();
            if (!food.isEmpty()) {
                DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, false,
                        (java.util.function.BooleanSupplier) () -> Rs2Inventory.interact(food.get(0), "Eat"));
                return false;
            }
            return abandonCannonPickup("No inventory room for cannon pickup");
        }
        WorldPoint site = cannon.getWorldLocation();
        if (player.distanceTo(site) > 2) {
            DroSlayerRuntime.walkAdaptive(config, site, 1, taskLocationName);
            return false;
        }
        DroSlayerRuntime.cancelWalk();
        DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.ACTIVE_WORK, false,
                (java.util.function.BooleanSupplier) () -> cannon.click("Pick-up"));
        if (sleepUntil(this::hasCannonParts, 1800)) {
            clearCannonDeployment();
            log.info("Verified all cannon parts after pickup");
            return true;
        }
        return false;
    }

    /** A recovered saved cannon resumes normal supply preparation; stalled builds retain their fallback. */
    private void finishCannonAssemblyRecovery() {
        boolean retrievedStartupCannon = cannonStartupPickup && hasCannonParts();
        cannonStartupPickup = false;
        cannonAssemblyRecovery = false;
        if (retrievedStartupCannon) {
            cannonPolicy.reset();
            cannonRecoveredForBank = true;
            log.info("Startup cannon retrieval verified; detecting task and preparing ammunition before redeployment");
            DroSlayerPlugin.setState(DroSlayerState.DETECTING_TASK);
        } else {
            disableCannonForTrip("Stalled cannon assembly recovery finished");
        }
    }

    /** A failed retrieval is unavailable, never a usable deployment for the next task. */
    private boolean abandonCannonPickup(String reason) {
        log.warn("Cannon retrieval failed: {} tile={} world={}; continuing without cannon. "
                + "If it has decayed, reclaim it from Nulodion.", reason, lastCannonPlacementTile, cannonDeploymentWorld);
        cannonReclaim.remember(cannonDeploymentWorld, lastCannonPlacementTile);
        cannonReclaim.missing(System.currentTimeMillis());
        log.info("Cannon second watchdog armed: bank/reclaim check after 36 minutes");
        clearCannonDeployment();
        disableCannonForTrip(reason);
        return true;
    }

    /** Delayed recovery owns movement only after ordinary retrieval failed. Escapes take priority. */
    private boolean handleCannonReclaim() {
        traceDroAction("handleCannonReclaim");
        long now = System.currentTimeMillis();
        if (cannonReclaim.phase() == DroSlayerCannonReclaim.Phase.NONE && cannonReclaim.lastWorld() == 0) return false;
        if (hasCannonParts()) {
            boolean recovering = cannonReclaim.active();
            cannonReclaim.found();
            if (recovering) finishCannonReclaim();
            return recovering;
        }
        // A new observed deployment supersedes the missing one; never reclaim a working cannon.
        if (cannonPlaced || cannonAssemblyStage > 0) {
            cannonReclaim.found();
            return false;
        }
        if (cannonReclaim.startIfDue(now, Rs2Bank.isOpen())) {
            DroSlayerRuntime.cancelWalk();
            cannonReclaimActionAt = 0L;
            log.info("Cannon second watchdog: checking bank before Nulodion reclaim");
        }
        if (!cannonReclaim.active()) return false;
        if (cannonReclaim.timedOut(now)) {
            DroSlayerRuntime.cancelWalk();
            cannonReclaim.retryLater(now);
            log.warn("Cannon reclaim timed out; {}", cannonReclaim.phase() == DroSlayerCannonReclaim.Phase.NONE
                    ? "three attempts exhausted; manual reclaim required" : "retrying in ten minutes");
            DroSlayerPlugin.setState(DroSlayerState.BANKING);
            return true;
        }
        Microbot.status = "Recovering missing cannon from Nulodion";
        if (cannonReclaim.phase() == DroSlayerCannonReclaim.Phase.BANK) {
            if (!Rs2Bank.isOpen()) {
                DroSlayerRuntime.walkToBankAndOpen(config, "Cannon reclaim bank check");
                return true;
            }
            String[] parts = {"Cannon base", "Cannon stand", "Cannon barrels", "Cannon furnace"};
            if (Arrays.stream(parts).allMatch(part -> Rs2Inventory.hasItem(part) || Rs2Bank.hasItem(part))) {
                cannonReclaim.found();
                finishCannonReclaim();
                return true;
            }
            // Four parts plus possible returned ammunition. Free slots without discarding gear.
            if (Rs2Inventory.emptySlotCount() < 5) {
                // Depositing food is allowed even where blighted food cannot be eaten.
                var food = Rs2Inventory.getList(item -> DroSlayerWildernessSupplies.isFood(item, true));
                if (food.isEmpty()) {
                    cannonReclaim.retryLater(now);
                    log.warn("Cannon reclaim deferred: need five free slots and no food can be banked; resuming ordinary banking");
                    DroSlayerPlugin.setState(DroSlayerState.BANKING);
                    return true;
                }
                if (now >= cannonReclaimActionAt) {
                    log.info("Cannon reclaim: banking food item={} to make space (freeSlots={}/5)",
                            food.get(0).getId(), Rs2Inventory.emptySlotCount());
                    cannonReclaimActionAt = now + 1800L;
                    DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, false,
                            () -> Rs2Bank.depositOne(food.get(0).getId()));
                }
                return true;
            }
            DroSlayerRuntime.cancelWalk();
            cannonReclaim.bankChecked();
            log.info("Cannon reclaim: bank check complete; closing bank and requesting travel to Nulodion");
            return true;
        }
        if (Rs2Bank.isOpen()) {
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                    (java.util.function.BooleanSupplier) Rs2Bank::closeBank);
            return true;
        }
        WorldPoint outside = new WorldPoint(3015, 3454, 0);
        WorldPoint inside = new WorldPoint(3014, 3453, 0);
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return true;
        if (player.distanceTo(outside) > 6) {
            DroSlayerRuntime.walkAdaptive(config, outside, 1, "Nulodion cannon reclaim");
            return true;
        }
        if (now < cannonReclaimActionAt) return true;
        cannonReclaimActionAt = now + 1800L;
        if (Rs2Dialogue.hasContinue()) {
            DroSlayerRuntime.cancelWalk();
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                    (Runnable) Rs2Dialogue::clickContinue);
            return true;
        }
        if (Rs2Dialogue.isInDialogue()) {
            DroSlayerRuntime.cancelWalk();
            if (Rs2Dialogue.hasDialogueOption("I've lost my cannon."))
                DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                        () -> { Rs2Dialogue.clickOption("I've lost my cannon."); });
            return true; // Never select buy/trade or an unrecognised option.
        }
        // Recorder: Open object 3 at 3015,3453; the door closes after passing through.
        boolean inHouse = player.getX() <= 3014 && player.getY() >= 3450 && player.getY() <= 3455;
        if (!inHouse) {
            var door = Microbot.getRs2TileObjectCache().query().where(object -> object.getId() == 3
                    && object.getWorldLocation().equals(new WorldPoint(3015, 3453, 0))).nearestOnClientThread();
            if (door != null) {
                DroSlayerRuntime.cancelWalk();
                DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> door.click("Open"));
            }
            else DroSlayerRuntime.walkAdaptive(config, inside, 0, "Nulodion doorway");
            return true;
        }
        var dwarf = Microbot.getRs2NpcCache().query().withName("Nulodion").nearestOnClientThread();
        if (dwarf != null)
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false, () -> DroSlayerCannonNpcApproach.talk(config, dwarf));
        return true;
    }

    private void finishCannonReclaim() {
        DroSlayerRuntime.cancelWalk();
        clearCannonDeployment();
        cannonPolicy.reset();
        escapeAmmoRestock = false;
        isUsingCannon = shouldUseCannon(currentTaskName());
        cannonSpot = null; // Normal banking restores the task-specific cannon spot.
        log.info("Cannon recovery confirmed; banking for task equipment and ammunition before resuming");
        DroSlayerPlugin.setState(DroSlayerState.BANKING);
    }

    private boolean pickupCannon() {
        traceDroAction("pickupCannon");
        var cannon = findOwnedCannon(50);
        return cannon != null && DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.ACTIVE_WORK, false,
                (java.util.function.BooleanSupplier) () -> cannon.click("Pick-up"));
    }

    private net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel findOwnedCannon(int radius) {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || (cannonDeploymentWorld > 0 && cannonDeploymentWorld != currentWorld())) return null;
        WorldPoint owner = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            int coord = Microbot.getClient().getVarpValue(net.runelite.api.VarPlayer.CANNON_COORD);
            return coord > 0 ? WorldPoint.fromCoord(coord) : null;
        }).orElse(null);
        WorldPoint anchor = lastCannonPlacementTile != null ? lastCannonPlacementTile : owner;
        if (anchor == null) return null;
        return Microbot.getRs2TileObjectCache().query().where(object ->
                Arrays.asList(5, 6, 7, 8, 9, 14916, 43027, 43028, 43029, 43030, 43031).contains(object.getId())
                        && DroSlayerWildernessRecovery.matchesCannonSite(object.getWorldLocation(), anchor, owner))
                .within(player, radius).nearestOnClientThread();
    }

    /** A missing object on another world must never discard the saved deployment. */
    private boolean holdForCannonWorldReturn() {
        if (reconcileCarriedCannon()) return false;
        if (cannonDeploymentWorld <= 0
                || !(cannonPlaced || cannonAssemblyStage > 0)) return false;
        if (!Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getGameState() == GameState.LOGGED_IN).orElse(false)) return true;
        int world = currentWorld();
        if (world == cannonDeploymentWorld) {
            cannonWorldReturnAttemptAt = 0L;
            cannonWorldReturnAttempts = 0;
            cannonWorldReturnSuppressed = false;
            return false;
        }
        if (cannonWorldReturnSuppressed) return false;
        // A safety logout may resume on another world at the same Wilderness tile.
        // Let Walker finish its safety response before cancelling movement or hopping.
        if (Rs2Pvp.isInWilderness() && (DroSlayerRuntime.wildernessSafetyOwnsActions()
                || DroSlayerRuntime.isWildernessPlayerAttackActive()
                || "PAUSED_SAFETY_LOGOUT".equals(DroSlayerRuntime.walkerStatus()))) {
            Microbot.status = "Waiting for safety before returning to cannon world " + cannonDeploymentWorld;
            maintainWildernessEscapeSurvival(false);
            return true;
        }
        DroSlayerRuntime.cancelWalk();
        Microbot.status = "Returning to cannon world " + cannonDeploymentWorld;
        if (world <= 0) return true;
        if (Rs2Player.isInCombat() || isBeingAttacked()) {
            maintainWildernessEscapeSurvival(false);
            return true;
        }
        boolean interacting = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getLocalPlayer() == null
                        || (Microbot.getClient().getLocalPlayer().getInteracting() != null
                            && Microbot.getClient().getLocalPlayer().getInteracting().getCombatLevel() > 0)).orElse(true);
        if (interacting) return true;
        long now = System.currentTimeMillis();
        if (now < cannonWorldReturnAttemptAt) return true;
        if (cannonWorldReturnAttempts >= Math.max(1, config.maxRecoveryAttempts())) {
            cannonWorldReturnSuppressed = true;
            disableCannonForTrip("Cannot return to cannon world " + cannonDeploymentWorld + "; retaining saved deployment and continuing combat");
            return false;
        }
        cannonWorldReturnAttemptAt = now + DroSlayerHopPolicy.RETRY_GAP_MS;
        if (Rs2Bank.isOpen()) {
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                    (java.util.function.BooleanSupplier) Rs2Bank::closeBank);
            return true;
        }
        var worlds = Microbot.getWorldService().getWorlds();
        cannonWorldReturnAttempts++;
        boolean members = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.MEMBERS)).orElse(true);
        if (worlds == null || !DroSlayerHopPolicy.eligible(worlds.findWorld(cannonDeploymentWorld), members, world)) {
            Microbot.status = "Waiting for cannon world " + cannonDeploymentWorld + " to become available";
            return true;
        }
        log.info("Returning to deployed cannon: fromWorld={} cannonWorld={} tile={}", world, cannonDeploymentWorld, lastCannonPlacementTile);
        DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.SETUP, false,
                (java.util.function.BooleanSupplier) () -> Microbot.hopToWorld(cannonDeploymentWorld));
        return true;
    }

    /** Inventory confirmation also handles manual pickup/reclaim without restarting Slayer. */
    private boolean reconcileCarriedCannon() {
        if (cannonBuildStage() != 0 || !hasCannonParts()) return false;
        boolean wasDeployed = cannonPlaced || cannonAssemblyStage > 0
                || cannonDeploymentWorld > 0 && cannonAssemblyProgressAt == 0L;
        if (!wasDeployed) return true;
        clearCannonDeployment();
        if (wasDeployed && isUsingCannon && DroSlayerPlugin.getState() == DroSlayerState.FIGHTING)
            DroSlayerPlugin.setState(DroSlayerState.AT_LOCATION);
        return true;
    }

    private void clearCannonDeployment() {
        cannonRepairPending = false;
        cannonPlaced = false;
        cannonFired = false;
        cannonAssemblyStage = 0;
        cannonAssemblyProgressAt = 0L;
        cannonAssemblyAttemptAt = 0L;
        cannonPickupStarted = 0L;
        cannonPickupWatchdog.reset();
        cannonFireAttemptStarted = 0L;
        cannonMaintenanceRetryAt = 0L;
        cannonDeploymentWorld = 0;
        cannonWorldReturnAttemptAt = 0L;
        cannonWorldReturnAttempts = 0;
        cannonWorldReturnSuppressed = false;
        lastCannonPlacementTile = null;
        abandonedWildernessCannon = null;
    }

    private boolean isCannonPlacedNearby() { return findOwnedCannon(10) != null; }

    /**
     * Resets cannon state
     */
    private void resetCannonState() {
        cannonRepairPending = false;
        cannonAssemblyStage = 0;
        cannonAssemblyProgressAt = 0L;
        cannonAssemblyAttemptAt = 0L;
        cannonFireAttemptStarted = 0L;
        cannonMaintenanceRetryAt = 0L;
        cannonPreviousAmmo = -1;
        cannonRefillThreshold = ThreadLocalRandom.current().nextInt(7, 13);
        isUsingCannon = false;
        cannonPlaced = false;
        cannonFired = false;
        cannonSpot = null;
    }

    // ==================== End Cannon Helper Methods ====================

    /**
     * Stops the plugin and logs out the player.
     * Used when we can't complete a required action (e.g., can't skip a task on skip list)
     */
    private void stopAndLogout() {
        traceDroAction("stopAndLogout");
        stopAndLogout(true);
    }

    private void stopAndLogout(boolean suppressAutoLogin) {
        traceDroAction("stopAndLogout");
        log.info("Stopping plugin and logging out...");

        // Disable prayers
        deactivateProfilePrayer();

        // Set state to idle
        DroSlayerPlugin.setState(DroSlayerState.IDLE);

        // Wait briefly for combat to end if we're fighting
        if (Rs2Combat.inCombat()) {
            log.info("Waiting for combat to end before logout...");
            sleepUntil(() -> !Rs2Combat.inCombat(), 10000);
        }

        // Logout
        DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, true, () -> {
            if (suppressAutoLogin) Rs2Player.logoutWithoutAutoLogin();
            else Rs2Player.logout();
        });
        sleep(1000, 2000);
        if (terminalStopMessage != null) {
            Microbot.showMessage(terminalStopMessage);
            terminalStopMessage = null;
        }

        // Stop the whole plugin/runtime so auxiliary combat scripts cannot keep acting.
        shutdown();
        DroSlayerRuntime.shutdown();
        try {
            Plugin plugin = Microbot.getPlugin(DroSlayerPlugin.class.getName());
            if (plugin != null) Microbot.stopPlugin(plugin);
        } catch (Exception ignored) {
        }
    }

    private void stepTentacleRepair() {
        Microbot.status = "Rebuilding abyssal tentacle with a banked whip";
        DroSlayerTentacleRepair.Result result = tentacleRepair.step();
        if (result == DroSlayerTentacleRepair.Result.READY) {
            tentacleBankCheck = false;
            baseBankLoadComplete = false;
            log.info("Abyssal tentacle rebuild observed; resuming inventory setup loading");
        } else if (result == DroSlayerTentacleRepair.Result.BLOCKED) {
            Microbot.status = tentacleRepair.failureReason();
            log.error("Tentacle rebuild stopped: {}", tentacleRepair.failureReason());
            stopAndLogout();
        }
    }

    @Override
    public void shutdown() {
        log.info("Slayer script shutting down");
        cannonPurchase.reset();
        saveCannonRecord();
        cannonProfileKey = null;
        entRotation.reset();
        revenantRotation.reset();
        wildernessWeaponCharge.reset();
        tentacleRepair.reset();
        escapeAmmoRestock = false;
        escapeAmmoClosing = false;
        wildernessBankChargeStarted = false;
        wildernessRecovery.cancel();
        wildernessDeathPending = false;
        wildernessHopRequired = false;
        DroSlayerRuntime.releaseWildernessContext();
        // Disable prayers on shutdown
        deactivateProfilePrayer();
        clearDepartureProtection();
        DroSlayerRuntime.cancelWalk();
        // Note: We don't pick up cannon on shutdown - user may want to keep it
        resetTravelData();
        resetSkipState();
        resetPohState();
        resetCannonState();
        initialSetupDone = false;
        super.shutdown();
    }

    /** Close the bank as its own step; the next observed tick may hop. */
    private static boolean closeBankBeforeHop(java.util.function.BooleanSupplier closeBank) {
        if (!Rs2Bank.isOpen()) return true;
        closeBank.getAsBoolean();
        return false;
    }
}
