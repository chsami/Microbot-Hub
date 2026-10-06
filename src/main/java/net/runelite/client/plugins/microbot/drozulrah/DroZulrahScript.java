package net.runelite.client.plugins.microbot.drozulrah;

import com.google.inject.Inject;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.thralls.Rs2Thrall;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2MiniMap;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Singleton;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Singleton
public class DroZulrahScript extends Script
{
    public static final String BUILD = DroZulrahPlugin.class.getAnnotation(PluginDescriptor.class).version();
    private long lootClearSince;
    private long lootStartedAt;
    private boolean startupBankTrip;
    private boolean emergencyActive;
    private int emergencyBrews;
    private int emergencyBrewBefore = -1;
    private int emergencyRestoreBefore = -1;
    private int emergencyActionTick;
    private volatile String operation = "idle";
    private volatile Thread loopThread;
    private volatile long loopEnteredAt;
    private java.util.concurrent.ScheduledExecutorService watchdog;
    private long nextErrorRetryAt;
    private boolean gearPlansReady;
    private final ZulrahEntryGate entryGate = new ZulrahEntryGate();
    public static final int ZULRAH_RANGE = 2042;
    public static final int ZULRAH_MELEE = 2043;
    public static final int ZULRAH_MAGIC = 2044;
    public static final int PROJECTILE_RANGE = 1044;
    public static final int PROJECTILE_MAGIC = 1046;
    public static final int VENOM_CLOUD_OBJECT = 11700;

    private static final int ZUL_ANDRA_TELEPORT = 12938;
    private static final int[] SACRIFICIAL_BOATS = {46241, 46242, 10068};
    private static final int[] RINGS_OF_DUELING = {
            ItemID.RING_OF_DUELING_1, ItemID.RING_OF_DUELING_2, ItemID.RING_OF_DUELING_3,
            ItemID.RING_OF_DUELING_4, ItemID.RING_OF_DUELING_5, ItemID.RING_OF_DUELING_6,
            ItemID.RING_OF_DUELING_7, ItemID.RING_OF_DUELING_8
    };
    private static final WorldArea FEROX_ENCLAVE = new WorldArea(3123, 3617, 34, 29, 0);
    private static final WorldPoint FEROX_POOL_POINT = new WorldPoint(3128, 3637, 0);
    private static final int FEROX_REFRESHMENT_POOL = 39651;
    private static final WorldPoint FEROX_BANK_POINT = BankLocation.FEROX_ENCLAVE.getWorldPoint();
    private static final WorldArea ZUL_ANDRA_SHORE = new WorldArea(2180, 3040, 40, 40, 0);
    private static final long FEROX_POOL_SETTLE_MS = 3_400L;
    private static final long TELEPORT_TRANSITION_WAIT_MS = 5_000L;
    private static final long TELEPORT_RETRY_DELAY_MS = 1_250L;
    private static final long REATTACK_COOLDOWN_MS = 80L;
    private static final long LOOT_WINDOW_MS = 45_000L;

    @Inject private ItemManager itemManager;

    private DroZulrahConfig config;
    private Rs2InventorySetup inventorySetup;
    private InventorySetup selectedSetup;
    private BaseProfileDro profile;

    private volatile DroZulrahState state = DroZulrahState.IDLE;
    private volatile String status = "Idle";
    private volatile int currentNpcId = -1;
    private volatile int phaseIndex = -1;
    private volatile int phaseStartTick = -1;
    private volatile ZulrahPhaseSnapshot helperSnapshot = ZulrahPhaseSnapshot.EMPTY;
    private volatile LocalPoint currentNpcLocal;
    private volatile ZulrahRotation rotation;
    private final List<Integer> seenTypes = Collections.synchronizedList(new ArrayList<>());
    private final Set<ZulrahRotation> candidates = Collections.synchronizedSet(EnumSet.allOf(ZulrahRotation.class));
    private final Map<LocalPoint, Long> poisonClouds = new ConcurrentHashMap<>();

    private final Map<Integer, GearChoice> magicPlan = new HashMap<>();
    private final Map<Integer, GearChoice> rangePlan = new HashMap<>();
    private boolean hasMagicWeapon;
    private boolean hasRangeWeapon;

    private volatile boolean meleeDodgeRequested;
    private LocalPoint meleeDodgeOrigin;
    private long lastDodgeAttemptMs;
    private ZulrahRotation.Stand meleeHomeStand;
    private ZulrahRotation.Stand meleeStandOverride;
    private volatile Rs2PrayerEnum jadNextPrayer;
    private final ZulrahPrayerSequence jadSequence = new ZulrahPrayerSequence();
    private volatile int phaseStartCycle;

    private boolean runtimeInitialized;
    private boolean autoRetaliateConfigured;
    private long nextAutoRetaliateAttemptAt;
    private long lastDiagnosticLogAt;
    private boolean inventorySetupReady;
    private boolean feroxRestored;
    private long feroxBankReadyAt;
    private long poolDrinkIssuedAt;
    private long teleportClickIssuedAt;
    private long nextTeleportRetryAt;
    private int teleportAttempts;
    private long sceneTransitionGraceUntil;
    private boolean boatClickPending;
    private long boatClickIssuedAt;
    private boolean awaitingFightStart;
    private boolean fightContinueClicked;
    private boolean reengageAfterMovement;
    private WorldPoint boatApproach;
    private boolean boatApproachRequired;
    private boolean forceReengage;
    private volatile boolean inFight;
    private long lastZulrahSeenMs;
    private long lastAttackMs;
    private long lastMoveMs;
    private long lastSwitchMs;
    private CombatStyle lastSwitchStyle;
    private long lastSpecialMs;
    private final Map<Integer, ZulrahSpecGate> specGates = new HashMap<>();
    private long lastBankCloseMs;
    private long lastEatMs;
    private long lastPotionMs;
    private long lastThrallMs;
    private final Map<Rs2PrayerEnum, Long> prayerDispatchTimes = new EnumMap<>(Rs2PrayerEnum.class);
    private volatile long killLootUntil;
    private long lastLootAt;

    private int kills;
    private int trips;
    private int deaths;
    private long totalLootValue;
    private long travelCost;
    private long sessionStartedAt;
    private int startingMagicXp;
    private int startingRangedXp;

    public boolean run(DroZulrahConfig config)
    {
        this.config = config;
        this.startupBankTrip = false;
        this.emergencyActive = false;
        this.emergencyBrews = 0;
        this.emergencyBrewBefore = -1;
        this.emergencyRestoreBefore = -1;
        this.inventorySetup = null;
        this.selectedSetup = null;
        this.inventorySetupReady = false;
        this.hasMagicWeapon = false;
        this.hasRangeWeapon = false;
        this.gearPlansReady = false;
        this.lastSwitchStyle = null;
        this.lastSwitchMs = 0L;
        this.lastSpecialMs = 0L;
        this.specGates.clear();
        this.lastBankCloseMs = 0L;
        this.magicPlan.clear();
        this.rangePlan.clear();
        this.entryGate.reset();
        this.nextErrorRetryAt = 0L;
        resetFightTracking();

        // The plugin can be enabled before AutoLogin has produced a real player. Do not snapshot
        // location/XP here; doing so made the startup state depend on a pre-login null player.
        // initializeRuntimeAfterLogin() owns the first real player snapshot.
        this.runtimeInitialized = false;
        this.autoRetaliateConfigured = false;
        this.nextAutoRetaliateAttemptAt = 0L;
        this.lastDiagnosticLogAt = 0L;
        this.feroxRestored = false;
        this.feroxBankReadyAt = 0L;
        this.poolDrinkIssuedAt = 0L;
        this.teleportClickIssuedAt = 0L;
        this.nextTeleportRetryAt = 0L;
        this.teleportAttempts = 0;
        this.sceneTransitionGraceUntil = 0L;
        this.boatClickPending = false;
        this.boatClickIssuedAt = 0L;
        this.awaitingFightStart = false;
        this.fightContinueClicked = false;
        this.inFight = false;
        this.killLootUntil = 0L;
        this.lastLootAt = 0L;
        this.kills = 0;
        this.trips = 0;
        this.deaths = 0;
        this.totalLootValue = 0L;
        this.travelCost = 0L;
        this.sessionStartedAt = 0L;
        this.startingMagicXp = 0;
        this.startingRangedXp = 0;
        this.state = DroZulrahState.IDLE;
        this.status = "Waiting for login";

        profile = new BaseProfileDro(profileSettings(config));

        Microbot.log("[Dro] Zulrah " + BUILD + ": scheduler started; waiting for a logged-in player");
        if (watchdog != null) watchdog.shutdownNow();
        watchdog = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DroZulrah-watchdog");
            t.setDaemon(true);
            return t;
        });
        watchdog.scheduleWithFixedDelay(() -> {
            Thread t = loopThread;
            if (t != null && loopEnteredAt > 0 && System.currentTimeMillis() - loopEnteredAt > 8_000)
            {
                Microbot.log("[Dro] Zulrah slow operation=" + operation + " state=" + state
                        + " status=" + status + " stack=" + Arrays.toString(t.getStackTrace()));
            }
            if (mainScheduledFuture != null && mainScheduledFuture.isDone())
                Microbot.log("[Dro] Zulrah scheduler stopped unexpectedly; last operation=" + operation);
        }, 10, 10, TimeUnit.SECONDS);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            loopThread = Thread.currentThread();
            loopEnteredAt = System.currentTimeMillis();
            try
            {
                if (System.currentTimeMillis() < nextErrorRetryAt) return;
                GameState gameState = clientRead(() -> Microbot.getClient().getGameState(), GameState.UNKNOWN);
                if (gameState == GameState.LOADING || gameState == GameState.HOPPING
                        || gameState == GameState.CONNECTION_LOST)
                {
                    entryGate.reset();
                    state = DroZulrahState.TRAVELLING;
                    status = "Loading scene: " + gameState;
                    maybeLogHeartbeat(null);
                    return;
                }
                operation = "Microbot guard";
                if (!super.run())
                {
                    maybeLogHeartbeat("Microbot guard paused loop");
                    return;
                }
                loop();
                maybeLogHeartbeat(null);
            }
            catch (Exception | AssertionError e)
            {
                state = DroZulrahState.RECOVERING;
                status = "Recovering: " + e.getClass().getSimpleName();
                nextErrorRetryAt = System.currentTimeMillis() + 2_000L;
                Microbot.log("[Dro] Zulrah failure in " + operation + ": " + e
                        + " stack=" + Arrays.toString(e.getStackTrace()));
            }
            finally { loopEnteredAt = 0L; }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    static BaseProfileDro.Settings profileSettings(DroZulrahConfig config)
    {
        return new BaseProfileDro.Settings()
                .activity(Activity.GENERAL_COMBAT)
                .activityIntensity(ActivityIntensity.HIGH)
                .mouseActivity(BaseProfileDro.MouseActivity.ACTIVE)
                .startupCamera(100, 2500, 3064)
                .customBreaksEnabled(config.smartBreaks())
                .breakIntervals(config.minBreakIntervalMinutes(), config.maxBreakIntervalMinutes())
                .logoutBreakChance(config.logoutBreakChance())
                .afkBreakDuration(config.afkBreakMinMinutes(), config.afkBreakMaxMinutes())
                .logoutBreakDuration(config.logoutBreakMinMinutes(), config.logoutBreakMaxMinutes())
                .postLoginSettleSeconds(config.postLoginSettleSeconds())
                .overlayEnabled(false)
                .writeBreakStatusToMicrobot(false)
                .cameraNudgesEnabled(false)
                .parkSide(BaseProfileDro.AfkParkSide.NONE);
    }

    private void loop()
    {
        // Keep the cursor available for combat and travel, even after native profile updates.
        net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban.setActivityIntensity(ActivityIntensity.HIGH);
        net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings.moveMouseOffScreen = false;
        net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings.moveMouseOffScreenChance = 0.0;
        net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings.moveMouseRandomly = false;
        net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings.moveMouseRandomlyChance = 0.0;
        operation = "initialization";
        if (!runtimeInitialized)
        {
            if (!initializeRuntimeAfterLogin()) return;
        }

        if (!Microbot.isLoggedIn())
        {
            if (System.currentTimeMillis() < sceneTransitionGraceUntil)
            {
                state = DroZulrahState.TRAVELLING;
                status = "Loading scene transition";
                return;
            }
            if (profile != null && profile.isBreakActive()
                    && profile.tick(false, false, BaseProfileDro.MouseActivity.ACTIVE))
            {
                state = DroZulrahState.BREAK;
                status = profile.getProfileStatus();
            }
            else
            {
                state = DroZulrahState.IDLE;
                status = "Waiting for login";
            }
            return;
        }

        WorldPoint location = Rs2Player.getWorldLocation();
        if (location == null) return;

        // A Zul-andra teleport is only considered successful after the world location actually
        // leaves Ferox. This prevents Rs2Inventory.interact()'s "item found" return value from
        // being mistaken for a completed teleport.
        if (teleportClickIssuedAt > 0L && ZUL_ANDRA_SHORE.contains(location))
        {
            finishZulAndraTeleport();
        }

        // Ferox preparation is the active trip workflow, not an idle opportunity. Run it before
        // BaseProfileDro's optional camera/mouse/break work so that profile initialization can
        // never prevent an already-geared account from reaching teleport dispatch.
        if (startupBankTrip && !ZUL_ANDRA_SHORE.contains(location))
        {
            operation = "starting bank preparation";
            prepareTrip(location);
            return;
        }
        if (location != null && FEROX_ENCLAVE.contains(location))
        {
            operation = "Ferox profile";
            if (profile != null && profile.tick(true, false, BaseProfileDro.MouseActivity.ACTIVE))
            {
                state = DroZulrahState.BREAK;
                status = profile.getProfileStatus();
                return;
            }
            operation = "prepare trip";
            prepareTrip(location);
            return;
        }

        operation = "auto-retaliate";
        maintainAutoRetaliateOff();
        operation = "gear plans";
        if (!gearPlansReady)
        {
            selectedSetup = config.inventorySetup();
            buildGearPlans(selectedSetup);
            gearPlansReady = true;
        }
        operation = "NPC snapshot";
        Rs2NpcModel zulrah = clientRead(() -> Rs2Npc.getNpc("Zulrah", true), null);
        boolean arena = isInZulrahInstance();
        if (arena && !inFight && killLootUntil == 0L)
        {
            operation = "instance entry";
            if (!entryReady(zulrah)) return;
        }
        if (boatClickPending && !arena)
        {
            if (System.currentTimeMillis() - boatClickIssuedAt < 12_000L)
            {
                state = DroZulrahState.BOARDING;
                status = "Running to / boarding sacrificial boat";
                return;
            }
            // No observed instance transition: retry through a nearby randomized approach.
            boatClickPending = false;
            awaitingFightStart = false;
            boatApproachRequired = true;
            boatApproach = null;
            entryGate.reset();
        }
        if (zulrah != null && !zulrah.isDead())
        {
            fight(zulrah);
            return;
        }

        if (killLootUntil > 0L)
        {
            if (System.currentTimeMillis() < killLootUntil)
            {
                lootZulrah();
                return;
            }
            Microbot.log("[Dro] Zulrah: loot timeout reached; returning to bank");
            killLootUntil = 0L;
            returnToFerox("Kill complete");
            return;
        }

        if (inFight && arena)
        {
            // Remain responsive while submerged: do not reset rotation or ignore survival.
            if (handleEmergencyFood(null) || handlePrayerRestore(null)) return;
            if (handlePosition()) return;
            state = DroZulrahState.IDENTIFYING;
            status = "Waiting for next phase";
            return;
        }

        if (inFight)
        {
            inFight = false;
            resetFightTracking();
        }

        Rs2TileObjectModel boat = findBoat();
        if (boat != null)
        {
            travelToAndBoard(boat);
            return;
        }

        if (location != null && ZUL_ANDRA_SHORE.contains(location))
        {
            state = DroZulrahState.TRAVELLING;
            status = "At Zul-Andra - waiting for sacrificial boat";
            return;
        }

        // If a trip was geared but the teleport transition has not materialized yet, wait rather than
        // invoking a global walker that can rotate the camera or choose an unrelated transport.
        if (inventorySetupReady)
        {
            state = DroZulrahState.TELEPORTING;
            status = "Waiting for Zul-Andra teleport";
            return;
        }

        state = DroZulrahState.RECOVERING;
        status = "Return to Ferox to begin/regear";
    }

    private boolean initializeRuntimeAfterLogin()
    {
        if (!Microbot.isLoggedIn())
        {
            state = DroZulrahState.IDLE;
            status = "Waiting for login";
            return false;
        }

        WorldPoint startupLocation = Rs2Player.getWorldLocation();
        if (startupLocation == null)
        {
            state = DroZulrahState.IDLE;
            status = "Waiting for player location";
            return false;
        }

        // Match DroKBD's startup rule, but take the snapshot only now that a real player exists.
        // Starting in Ferox therefore skips the pool; later return/regear cycles still use it once.
        feroxRestored = FEROX_ENCLAVE.contains(startupLocation);
        startupBankTrip = !feroxRestored && !ZUL_ANDRA_SHORE.contains(startupLocation)
                && (Rs2Bank.isOpen() || Rs2Bank.isNearBank(12));
        if (startupBankTrip) feroxRestored = true;
        feroxBankReadyAt = 0L;
        poolDrinkIssuedAt = 0L;
        teleportClickIssuedAt = 0L;
        nextTeleportRetryAt = 0L;
        teleportAttempts = 0;
        sessionStartedAt = System.currentTimeMillis();
        startingMagicXp = skillXp(Skill.MAGIC);
        startingRangedXp = skillXp(Skill.RANGED);
        nextAutoRetaliateAttemptAt = System.currentTimeMillis() + 1_200L;
        autoRetaliateConfigured = false;

        if (profile != null) profile.start();
        // Same startup zoom as DroAgility; do not change yaw/pitch or enable camera nudges.
        Rs2Camera.setZoom(100);
        runtimeInitialized = true;
        state = feroxRestored ? DroZulrahState.BANKING : DroZulrahState.RECOVERING;
        status = feroxRestored ? "Logged in at bank - preparing trip" : "Start at a bank or Zul-Andra";
        Microbot.log("[Dro] Zulrah: runtime initialized at " + startupLocation
                + " | ferox=" + feroxRestored
                + " | setup=" + (config.inventorySetup() == null ? "none" : "configured"));
        return true;
    }

    private void maintainAutoRetaliateOff()
    {
        if (autoRetaliateConfigured) return;
        long now = System.currentTimeMillis();
        if (now < nextAutoRetaliateAttemptAt) return;

        boolean disabled = Rs2Combat.setAutoRetaliate(false);
        if (disabled)
        {
            autoRetaliateConfigured = true;
            Microbot.log("[Dro] Zulrah: auto-retaliate disabled");
        }
        else
        {
            nextAutoRetaliateAttemptAt = now + 2_000L;
        }
    }

    private void maybeLogHeartbeat(String override)
    {
        long now = System.currentTimeMillis();
        if (now - lastDiagnosticLogAt < 5_000L) return;
        lastDiagnosticLogAt = now;

        WorldPoint location = null;
        try
        {
            if (Microbot.isLoggedIn()) location = Rs2Player.getWorldLocation();
        }
        catch (Exception ignored)
        {
        }

        String detail = override == null ? status : override;
        Microbot.log("[Dro] Zulrah heartbeat: state=" + state
                + " | status=" + detail
                + " | loggedIn=" + Microbot.isLoggedIn()
                + " | location=" + location
                + " | rotation=" + rotation + " | phase=" + phaseIndex
                + " | initialized=" + runtimeInitialized
                + " | setupReady=" + inventorySetupReady);
    }

    private void prepareTrip(WorldPoint location)
    {
        if (!feroxRestored)
        {
            state = DroZulrahState.RESTORING;
            long now = System.currentTimeMillis();

            // Once the pool click has been issued, do not click it again. The old 100 ms loop
            // could re-enter the branch before the restore animation/state settled. One click is
            // sufficient; simply wait through the same settle window used by DroKBD.
            if (poolDrinkIssuedAt > 0L)
            {
                long elapsed = now - poolDrinkIssuedAt;
                if (elapsed < FEROX_POOL_SETTLE_MS)
                {
                    status = "Waiting for Ferox pool restore";
                    return;
                }

                feroxRestored = true;
                poolDrinkIssuedAt = 0L;
                feroxBankReadyAt = now + 250L;
                status = "Ferox restore complete";
                return;
            }

            if (location.distanceTo(FEROX_POOL_POINT) > 3)
            {
                status = "Walking to Ferox pool";
                action(BaseProfileDro.ActionPhase.SETUP, false, () -> walkNoCamera(FEROX_POOL_POINT));
                return;
            }

            Rs2TileObjectModel pool = Microbot.getRs2TileObjectCache().query().withId(FEROX_REFRESHMENT_POOL).nearest();
            if (pool == null)
            {
                pool = Microbot.getRs2TileObjectCache().query().withName("Pool of Refreshment").nearest();
            }
            if (pool != null)
            {
                final Rs2TileObjectModel poolToDrink = pool;
                status = "Drinking Ferox pool - one click";

                // Latch BEFORE invoking. Rs2TileObjectModel.click() reports true even if its
                // internal invoke throws, so the latch must be independent of its return value.
                poolDrinkIssuedAt = now;
                action(BaseProfileDro.ActionPhase.SETUP, false, () -> poolToDrink.click("Drink"));
                return;
            }
            status = "Waiting for Ferox pool";
            return;
        }

        if (System.currentTimeMillis() < feroxBankReadyAt)
        {
            state = DroZulrahState.RESTORING;
            status = "Finishing Ferox restore";
            return;
        }

        if (!inventorySetupReady)
        {
            state = DroZulrahState.BANKING;
            if (!startupBankTrip && location.distanceTo(FEROX_BANK_POINT) > 4)
            {
                status = "Walking to Ferox bank";
                action(BaseProfileDro.ActionPhase.SETUP, false, () -> walkNoCamera(FEROX_BANK_POINT));
                return;
            }

            selectedSetup = config.inventorySetup();
            if (selectedSetup == null)
            {
                status = "Select a Microbot Inventory Setup";
                return;
            }

            if (inventorySetup == null)
            {
                inventorySetup = new Rs2InventorySetup(selectedSetup, mainScheduledFuture);
            }

            // Identical completion contract to DroKBD. loadEquipment already equips items;
            // its false result is not permission to depart with an incomplete setup.
            state = DroZulrahState.REGEARING;
            operation = status = "Loading Zulrah equipment";
            if (!inventorySetup.loadEquipment()) return;
            operation = status = "Loading Zulrah inventory";
            if (!inventorySetup.loadInventory()) return;
            operation = status = "Equipping Zulrah setup";
            if (!inventorySetup.wearEquipment()) return;
            operation = "gear plans";
            buildGearPlans(selectedSetup);
            gearPlansReady = true;
            if (!validateTripLoadout()) return;
            inventorySetupReady = true;
            status = "Zulrah setup ready";
            Microbot.log("[Dro] Zulrah: regear verified; closing bank and departing");
        }

        if (Rs2Bank.isOpen())
        {
            operation = status = "Closing bank";
            closeBankStep();
            return;
        }

        useZulAndraTeleport();
    }

    private void closeBankStep()
    {
        long now = System.currentTimeMillis();
        if (now - lastBankCloseMs < 2_000L) return;
        Rectangle bounds = clientRead(() -> {
            Widget frame = Microbot.getClient().getWidget(786434);
            Widget close = frame == null ? null : frame.getChild(11);
            return close == null || close.isHidden() ? null : close.getBounds();
        }, null);
        lastBankCloseMs = now;
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
        {
            status = "Waiting for bank close button";
            return;
        }
        // One physical close click. The next loop observes Rs2Bank.isOpen() before teleporting.
        Microbot.getMouse().click(bounds);
    }

    private boolean validateTripLoadout()
    {
        if (!Rs2Inventory.contains(ZUL_ANDRA_TELEPORT))
        {
            status = "Inventory Setup needs Zul-andra teleport [12938]";
            return false;
        }
        if (!hasRingOfDueling())
        {
            status = "Inventory Setup needs a charged Ring of dueling";
            return false;
        }
        if (!hasMagicWeapon && !hasRangeWeapon)
        {
            status = "Inventory Setup needs a magic or ranged weapon";
            return false;
        }
        return true;
    }

    private void useZulAndraTeleport()
    {
        long now = System.currentTimeMillis();
        WorldPoint location = Rs2Player.getWorldLocation();

        if (teleportClickIssuedAt > 0L)
        {
            if (location != null && ZUL_ANDRA_SHORE.contains(location))
            {
                finishZulAndraTeleport();
                return;
            }

            long elapsed = now - teleportClickIssuedAt;
            if (elapsed < TELEPORT_TRANSITION_WAIT_MS)
            {
                state = DroZulrahState.TELEPORTING;
                status = "Teleport clicked - waiting for Zul-Andra";
                return;
            }

            // The click produced no location transition. Allow a deliberate retry rather than
            // hammering the item every loop. Keep the geared/setup-ready state intact.
            teleportClickIssuedAt = 0L;
            nextTeleportRetryAt = now + TELEPORT_RETRY_DELAY_MS;
            status = "Zul-andra teleport did not transition - retrying";
            Microbot.log("[Dro] Zulrah: teleport click produced no Ferox->Zul-Andra transition (attempt "
                    + teleportAttempts + ")");
            return;
        }

        if (now < nextTeleportRetryAt)
        {
            state = DroZulrahState.TELEPORTING;
            status = "Waiting before Zul-andra teleport retry";
            return;
        }

        Rs2ItemModel teleport = Rs2Inventory.get(ZUL_ANDRA_TELEPORT);
        if (teleport == null)
        {
            inventorySetupReady = false;
            inventorySetup = null;
            status = "Missing Zul-andra teleport - regear required";
            return;
        }

        String teleportAction = findInventoryAction(teleport, "Teleport");
        if (teleportAction == null)
        {
            status = "Zul-andra teleport action not found";
            Microbot.log("[Dro] Zulrah: Zul-andra teleport inventory actions="
                    + Arrays.toString(teleport.getInventoryActions()));
            return;
        }

        state = DroZulrahState.TELEPORTING;
        status = "Casting Zul-andra teleport";
        teleportAttempts++;
        teleportClickIssuedAt = now;
        sceneTransitionGraceUntil = now + 10_000L;
        Microbot.log("[Dro] Zulrah: dispatching Zul-andra teleport"
                + " | attempt=" + teleportAttempts
                + " | action=" + teleportAction
                + " | location=" + location
                + " | setupReady=" + inventorySetupReady);

        boolean invoked = action(BaseProfileDro.ActionPhase.SETUP, false,
                () -> Rs2Inventory.interact(teleport, teleportAction));
        if (!invoked)
        {
            teleportClickIssuedAt = 0L;
            nextTeleportRetryAt = now + TELEPORT_RETRY_DELAY_MS;
            status = "Zul-andra teleport click failed - retrying";
            return;
        }

        status = "Teleport clicked - waiting for Zul-Andra";
    }

    private void finishZulAndraTeleport()
    {
        if (teleportClickIssuedAt <= 0L) return;
        startupBankTrip = false;

        operation = "teleport arrival / price";
        trips++;
        travelCost += clientRead(() -> Math.max(0L, (long) itemManager.getItemPrice(ZUL_ANDRA_TELEPORT)), 0L);
        teleportClickIssuedAt = 0L;
        nextTeleportRetryAt = 0L;
        teleportAttempts = 0;
        inventorySetupReady = false;
        inventorySetup = null;
        boatClickPending = false;
        boatClickIssuedAt = 0L;
        awaitingFightStart = false;
        fightContinueClicked = false;
        state = DroZulrahState.TRAVELLING;
        status = "Arrived at Zul-Andra";
        Microbot.log("[Dro] Zulrah: teleport arrival confirmed; proceeding to boat");
    }

    private String findInventoryAction(Rs2ItemModel item, String wanted)
    {
        if (item == null || wanted == null) return null;
        String[] actions = item.getInventoryActions();
        if (actions == null) return null;
        for (String action : actions)
        {
            if (action != null && wanted.equalsIgnoreCase(action)) return action;
        }
        return null;
    }

    private void travelToAndBoard(Rs2TileObjectModel boat)
    {
        operation = "boat approach";
        if (boatClickPending) return;
        if (boat == null || boat.getWorldLocation() == null) return;
        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null) return;

        int distance = here.distanceTo(boat.getWorldLocation());
        boolean clickable = clientRead(() -> boat.getClickbox() != null
                && !boat.getClickbox().getBounds().isEmpty()
                && Rs2Camera.isTileOnScreen(boat.getLocalLocation()), false);
        if (ZulrahBoatRoute.shouldApproach(distance, clickable, boatApproachRequired))
        {
            state = DroZulrahState.TRAVELLING;
            status = "Running to sacrificial boat";
            if (System.currentTimeMillis() - lastMoveMs >= 450L)
            {
                if (boatApproach == null || here.distanceTo(boatApproach) <= 1)
                    boatApproach = randomBoatApproach(boat.getWorldLocation());
                // Randomization is optional: an unavailable route must never suppress travel.
                boolean dispatched = boatApproach != null && walkToBoatApproach(boatApproach);
                if (!dispatched) dispatched = walkNoCamera(boat.getWorldLocation());
                if (dispatched) lastMoveMs = System.currentTimeMillis();
                else status = "Waiting for boat approach scene";
            }
            return;
        }

        state = DroZulrahState.BOARDING;
        status = "Quick-boarding Zulrah - one click";
        boatApproachRequired = false;
        // Latch before invoking: tile-object click return values do not reliably indicate whether
        // the menu action was dispatched. This prevents the 100 ms loop from spam-clicking.
        boatApproach = null;
        boatClickPending = true;
        boatClickIssuedAt = System.currentTimeMillis();
        sceneTransitionGraceUntil = boatClickIssuedAt + 10_000L;
        awaitingFightStart = true;
        fightContinueClicked = false;
        entryGate.reset();
        operation = "boat click";
        action(BaseProfileDro.ActionPhase.SETUP, false, () -> boat.click("Quick-board"));
    }

    private boolean entryReady(Rs2NpcModel boss)
    {
        boolean dialogue = clientRead(Rs2Dialogue::hasContinue, false);
        boolean active = boss != null && clientRead(() -> !boss.isDead()
                && hasAttackAction(boss), false);
        ZulrahEntryGate.Step step = entryGate.observe(true, clientTick(), dialogue, active);
        state = DroZulrahState.STARTING_FIGHT;
        status = "Waiting for instance / encounter readiness";
        if (step == ZulrahEntryGate.Step.CONTINUE)
        {
            Rs2Dialogue.clickContinue();
            Microbot.log("[Dro] Zulrah: instance ready; Continue dispatched");
        }
        if (step != ZulrahEntryGate.Step.COMBAT) return false;
        awaitingFightStart = false;
        boatClickPending = false;
        fightContinueClicked = false;
        Microbot.log("[Dro] Zulrah: encounter active; combat enabled");
        return true;
    }

    private boolean hasAttackAction(NPC npc)
    {
        NPCComposition composition = npc.getTransformedComposition();
        return composition != null && composition.getActions() != null
                && Arrays.stream(composition.getActions()).anyMatch("Attack"::equalsIgnoreCase);
    }

    private void fight(Rs2NpcModel zulrah)
    {
        inFight = true;
        awaitingFightStart = false;
        boatClickPending = false;
        lastZulrahSeenMs = System.currentTimeMillis();
        if (!clientRead(() -> { observePhase(zulrah); return true; }, false)) return;

        // Preserve v1.1's phase destinations and uninterrupted direct walks. Consumables
        // and prayer/gear changes no longer defer attack resumption to another loop pass.
        operation = "protection prayer";
        handleProtectionPrayer(zulrah);
        operation = "dodge / movement";
        boolean moving = meleeDodgeRequested && handleMeleeDodge();
        if (!moving) moving = handlePosition();
        operation = "emergency food";
        boolean consumed = handleEmergencyFood(zulrah);
        operation = "survival consumables";
        if (!consumed) consumed = handlePrayerRestore(zulrah);
        if (!consumed) consumed = handleVenom(zulrah);
        if (!consumed) consumed = handleStatRecovery(zulrah);
        operation = "gear / offensive prayer";
        handleGearAndOffensivePrayer(zulrah);
        if (!consumed) consumed = handleNormalFood(zulrah);
        if (!consumed && !moving) consumed = handleBoosts(zulrah);

        if (shouldLeaveFight())
        {
            returnToFerox("Low supplies");
            return;
        }

        // An Attack click cancels an unfinished crossing/dodge. Resume as soon as the
        // original arrival tolerance is met, including the final tile while still moving.
        if (moving) return;
        operation = "blowpipe special";
        handleWeaponSpecial(zulrah);
        operation = "attack";
        if (reengage(zulrah, forceReengage, "Attacking Zulrah")) return;
        operation = "thrall";
        if (!consumed && !emergencyActive && isTargetingZulrah()) handleThrall(zulrah);
    }

    private void observePhase(NPC npc)
    {
        int id = npc.getId();
        LocalPoint local = npc.getLocalLocation();
        if (id == currentNpcId && sameLocal(local, currentNpcLocal)) return;

        currentNpcId = id;
        currentNpcLocal = local;
        phaseStartTick = clientTick();
        phaseStartCycle = clientRead(() -> Microbot.getClient().getGameCycle(), 0);
        seenTypes.add(id);
        phaseIndex = seenTypes.size() - 1;
        candidates.removeIf(candidate -> !candidate.matches(phaseIndex, id, local));

        if (candidates.size() == 1)
        {
            rotation = candidates.iterator().next();
        }
        else if (candidates.isEmpty())
        {
            seenTypes.clear();
            candidates.addAll(EnumSet.allOf(ZulrahRotation.class));
            seenTypes.add(id);
            phaseIndex = 0;
            candidates.removeIf(candidate -> !candidate.matches(0, id, local));
            rotation = candidates.size() == 1 ? candidates.iterator().next() : null;
        }

        state = DroZulrahState.IDENTIFYING;
        status = rotation == null ? "Identifying rotation" : "Rotation " + rotation.name() + " phase " + (phaseIndex + 1);
        meleeDodgeRequested = false;
        meleeDodgeOrigin = null;
        lastDodgeAttemptMs = 0L;
        meleeHomeStand = id == ZULRAH_MELEE ? desiredStand() : null;
        meleeStandOverride = meleeHomeStand;
        jadSequence.reset(id == ZULRAH_MAGIC);
        jadNextPrayer = isJad()
                ? (id == ZULRAH_MAGIC ? Rs2PrayerEnum.PROTECT_MAGIC : Rs2PrayerEnum.PROTECT_RANGE)
                : null;
        forceReengage = true;
        // Publish display data only. The helper never selects combat tiles or advances phases.
        ZulrahRotation[] options = candidates.toArray(new ZulrahRotation[0]);
        helperSnapshot = new ZulrahPhaseSnapshot(phaseIndex, phaseStartTick,
                options.length == 0 ? 24 : options[0].ticks(phaseIndex), rotation, options);
    }

    private boolean handlePosition()
    {
        ZulrahRotation.Stand stand = currentNpcId == ZULRAH_MELEE && meleeStandOverride != null
                ? meleeStandOverride : desiredMovementStand();
        if (isClouded(stand.local())) stand = stand.meleeAlternate();

        LocalPoint me = playerLocal();
        if (me == null) return true;
        if (localTileDistance(me, stand.local()) <= 1 && !isClouded(me)
                && ZulrahMeleeDodge.escaped(me, meleeDodgeOrigin)) return false;
        if (System.currentTimeMillis() - lastMoveMs < 450L) return true;

        final LocalPoint standPoint = stand.local();
        state = DroZulrahState.MOVING;
        status = "Moving to phase tile";
        // Preserve the original phase destinations: issue one raw walk request and consume
        // this loop iteration even if the helper has not yet reported arrival. Combat must never
        // fall through to Attack while the player is still off the required phase tile.
        walkLocalOriginal(standPoint);
        forceReengage = true;
        lastMoveMs = System.currentTimeMillis();
        return true;
    }

    private boolean handleMeleeDodge()
    {
        if (currentNpcId != ZULRAH_MELEE)
        {
            meleeDodgeRequested = false;
            return false;
        }

        LocalPoint me = playerLocal();
        if (me == null) return true;
        long now = System.currentTimeMillis();
        if (now - lastDodgeAttemptMs < 450L) return true;
        lastDodgeAttemptMs = now;
        ZulrahRotation.Stand home = meleeHomeStand != null ? meleeHomeStand : desiredStand();
        ZulrahRotation.Stand target = ZulrahMeleeDodge.choose(me, home, this::isClouded);
        if (target == null)
        {
            status = "Waiting for an unclouded melee escape tile";
            return true; // Keep the dodge pending; do not click the occupied tile or attack.
        }

        state = DroZulrahState.DODGING;
        status = "Dodging Zulrah melee";
        if (walkLocalOriginal(target.local()))
        {
            meleeDodgeOrigin = me;
            meleeStandOverride = target;
            forceReengage = true;
            lastMoveMs = System.currentTimeMillis();
            meleeDodgeRequested = false;
        }
        return true;
    }

    private WorldPoint randomBoatApproach(WorldPoint boat)
    {
        return clientRead(() -> {
            net.runelite.api.WorldView view = Microbot.getClient().getTopLevelWorldView();
            if (view == null || view.getCollisionMaps() == null
                    || Microbot.getClient().getLocalPlayer() == null) return null;
            LocalPoint here = Microbot.getClient().getLocalPlayer().getLocalLocation();
            LocalPoint destination = LocalPoint.fromWorld(view, boat);
            if (here == null || destination == null) return null;
            List<int[]> choices = ZulrahBoatRoute.approaches(here.getSceneX(), here.getSceneY(),
                    destination.getSceneX(), destination.getSceneY(), boatTravelEdge(view));
            if (choices.isEmpty()) return null;
            int[] chosen = choices.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(choices.size()));
            return WorldPoint.fromLocal(Microbot.getClient(), LocalPoint.fromScene(chosen[0], chosen[1]));
        }, null);
    }

    /** Client thread only. Both approach selection and movement use the same collision rules. */
    private ZulrahSafeRoute.Edge boatTravelEdge(net.runelite.api.WorldView view)
    {
        return (x, y, nx, ny) -> {
            WorldPoint from = WorldPoint.fromLocal(Microbot.getClient(), LocalPoint.fromScene(x, y));
            WorldPoint to = WorldPoint.fromLocal(Microbot.getClient(), LocalPoint.fromScene(nx, ny));
            return from != null && to != null && ZUL_ANDRA_SHORE.contains(to)
                    && from.toWorldArea().canTravelInDirection(view, nx - x, ny - y);
        };
    }

    private boolean walkToBoatApproach(WorldPoint target)
    {
        LocalPoint step = clientRead(() -> {
            net.runelite.api.WorldView view = Microbot.getClient().getTopLevelWorldView();
            if (view == null || view.getCollisionMaps() == null
                    || Microbot.getClient().getLocalPlayer() == null) return null;
            LocalPoint here = Microbot.getClient().getLocalPlayer().getLocalLocation();
            LocalPoint destination = LocalPoint.fromWorld(view, target);
            if (here == null || destination == null) return null;
            int[] next = ZulrahSafeRoute.next(here.getSceneX(), here.getSceneY(),
                    destination.getSceneX(), destination.getSceneY(), boatTravelEdge(view));
            return next == null ? null : LocalPoint.fromScene(next[0], next[1]);
        }, null);
        return step != null && walkLocalOriginal(step);
    }

    private boolean handleProtectionPrayer(Rs2NpcModel zulrah)
    {
        Rs2PrayerEnum wanted = null;
        if (isJad() && jadNextPrayer != null) wanted = jadNextPrayer;
        else if (currentNpcId == ZULRAH_RANGE) wanted = Rs2PrayerEnum.PROTECT_RANGE;
        else if (currentNpcId == ZULRAH_MAGIC) wanted = Rs2PrayerEnum.PROTECT_MAGIC;

        if (wanted == null)
        {
            Rs2PrayerEnum active = Rs2Prayer.getActiveProtectionPrayer();
            if (currentNpcId == ZULRAH_MELEE && active != null)
            {
                final Rs2PrayerEnum off = active;
                state = DroZulrahState.PRAYING;
                status = "Clearing overhead for red phase";
                return combatAction(zulrah, BaseProfileDro.ActionPhase.ACTIVE_WORK, true,
                        () -> dispatchPrayer(off, false));
            }
            return false;
        }

        if (Rs2Prayer.getActiveProtectionPrayer() != wanted)
        {
            final Rs2PrayerEnum target = wanted;
            state = DroZulrahState.PRAYING;
            status = "Protecting " + (wanted == Rs2PrayerEnum.PROTECT_MAGIC ? "Magic" : "Range");
            return combatAction(zulrah, BaseProfileDro.ActionPhase.ACTIVE_WORK, true,
                    () -> dispatchPrayer(target, true));
        }
        return false;
    }

    private boolean handleGearAndOffensivePrayer(Rs2NpcModel zulrah)
    {
        CombatStyle style = desiredCombatStyle();
        Map<Integer, GearChoice> plan = style == CombatStyle.RANGE ? rangePlan : magicPlan;

        long now = System.currentTimeMillis();
        if (style != lastSwitchStyle || now - lastSwitchMs >= 900L)
        {
            int sent = dispatchGearBatch(plan);
            if (sent > 0)
            {
                lastSwitchStyle = style;
                lastSwitchMs = now;
                forceReengage = true;
                state = DroZulrahState.SWITCHING;
                status = "Switching " + style.name().toLowerCase() + " (" + sent + " items)";
                return true;
            }
        }

        if (config.useOffensivePrayer())
        {
            Rs2PrayerEnum wanted = clientRead(() -> style == CombatStyle.RANGE
                    ? Rs2Prayer.getBestRangePrayer() : Rs2Prayer.getBestMagePrayer(), null);
            if (wanted != null)
            {
                if (!Rs2Prayer.isPrayerActive(wanted))
                {
                    final Rs2PrayerEnum prayer = wanted;
                    state = DroZulrahState.PRAYING;
                    status = "Offensive " + style.name().toLowerCase() + " prayer";
                    return combatAction(zulrah, BaseProfileDro.ActionPhase.ACTIVE_WORK, false,
                            () -> dispatchPrayer(prayer, true));
                }
            }
        }
        return false;
    }

    private int dispatchGearBatch(Map<Integer, GearChoice> plan)
    {
        boolean pending = plan.values().stream().anyMatch(choice -> !choice.isWearing() && choice.isInInventory());
        if (!pending) return 0;
        // Switch the tab once, outside the client thread. No sleeps or per-item mouse delays
        // inside the burst; subsequent loops verify actual equipment and retry missing pieces.
        Rs2Tab.switchToInventoryTab();
        return clientRead(() -> {
            if (Microbot.getClient().getGameState() != GameState.LOGGED_IN) return 0;
            Widget inventory = Microbot.getClient().getWidget(ComponentID.INVENTORY_CONTAINER);
            if (inventory == null || inventory.isHidden()) return 0;
            int sent = 0;
            for (int slot : switchOrder())
            {
                GearChoice choice = plan.get(slot);
                if (choice == null || choice.isWearing()) continue;
                Rs2ItemModel item = Rs2Inventory.get(choice.id);
                if (item == null && !choice.name.isEmpty()) item = Rs2Inventory.get(choice.name, false);
                if (item == null) continue;
                Widget child = inventory.getChild(item.getSlot());
                if (child == null || child.getItemId() != item.getId()) continue;
                String[] actions = child.getActions();
                if (actions == null) actions = item.getInventoryActions();
                if (actions == null) continue;
                for (int i = 0; i < actions.length; i++)
                {
                    String action = actions[i];
                    if (!"Wear".equalsIgnoreCase(action) && !"Wield".equalsIgnoreCase(action)
                            && !"Equip".equalsIgnoreCase(action)) continue;
                    Microbot.getClient().menuAction(item.getSlot(), ComponentID.INVENTORY_CONTAINER,
                            i < 10 ? MenuAction.CC_OP : MenuAction.CC_OP_LOW_PRIORITY,
                            i + 1, item.getId(), action, item.getName());
                    sent++;
                    break;
                }
            }
            return sent;
        }, 0);
    }

    private boolean handleWeaponSpecial(Rs2NpcModel zulrah)
    {
        Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        ZulrahSpecialWeapon special = weapon == null ? null : ZulrahSpecialWeapon.find(weapon.getName());
        if (special == null) return false;
        ZulrahSpecGate specGate = specGates.computeIfAbsent(weapon.getId(), id -> new ZulrahSpecGate());
        int[] spec = clientRead(() -> new int[]{
                Microbot.getClient().getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT),
                Microbot.getClient().getVarpValue(VarPlayer.SPECIAL_ATTACK_ENABLED)}, null);
        if (spec == null) return false;
        ZulrahSpecGate.Result result = specGate.observe(System.currentTimeMillis(), spec[0], spec[1] != 0);
        if (result != ZulrahSpecGate.Result.NONE)
            Microbot.log("[Dro] Zulrah: " + weapon.getName() + " spec confirmation=" + result + " energy=" + spec[0]
                    + (result == ZulrahSpecGate.Result.DISABLED ? "; this weapon disabled until script restart" : ""));
        if (!config.useSpecialAttacks() || special.magic != (desiredCombatStyle() == CombatStyle.MAGIC)
                || !specGate.canDispatch() || spec[0] < special.cost || spec[1] != 0
                || Rs2Player.isMoving() || !isTargetingZulrah()
                || System.currentTimeMillis() - lastSpecialMs < 1_800L) return false;
        Rectangle bounds = clientRead(() -> {
            Rs2ItemModel equipped = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
            if (equipped == null || equipped.getId() != weapon.getId()
                    || zulrah.isDead() || !hasAttackAction(zulrah)
                    || Microbot.getClient().getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT) < special.cost
                    || Microbot.getClient().getVarpValue(VarPlayer.SPECIAL_ATTACK_ENABLED) != 0) return null;
            Widget orb = Microbot.getClient().getWidget(10485795);
            return orb == null || orb.isHidden() ? null : orb.getBounds();
        }, null);
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) return false;
        lastSpecialMs = System.currentTimeMillis();
        specGate.dispatched(lastSpecialMs, spec[0], special.cost);
        // Match Microbot's spec helper: the orb is clicked normally, not forced through CC_OP.
        Microbot.getMouse().click(bounds);
        forceReengage = true;
        status = weapon.getName() + " special dispatched";
        Microbot.log("[Dro] Zulrah: " + weapon.getName() + " special clicked; cost=" + special.cost + "; awaiting confirmation");
        return true;
    }

    private boolean dispatchPrayer(Rs2PrayerEnum prayer, boolean enabled)
    {
        if (clientRead(() -> Rs2Prayer.isPrayerActive(prayer) == enabled || Rs2Prayer.getPrayerPoints() <= 0, true)) return false;
        long now = System.currentTimeMillis();
        if (now - prayerDispatchTimes.getOrDefault(prayer, 0L) < 900L) return false;
        if (Microbot.getClient().isClientThread()) return false;
        InterfaceTab previous = Rs2Tab.getCurrentTab();
        try
        {
            if (!openPrayerTab(InterfaceTab.PRAYER)) return false;
            Rectangle bounds = clientRead(() -> {
                Widget button = Microbot.getClient().getWidget(prayer.getIndex());
                if (button == null || button.isHidden() || Rs2Tab.getCurrentTab() != InterfaceTab.PRAYER
                        || Rs2Prayer.isPrayerActive(prayer) == enabled) return null;
                return ZulrahClickBounds.visible(button.getBounds(), Microbot.getClient().getCanvasWidth(),
                        Microbot.getClient().getCanvasHeight());
            }, null);
            if (bounds == null) return false;
            if (profile == null || !profile.clickHumanized(bounds)) return false;
            prayerDispatchTimes.put(prayer, now);
            return true;
        }
        finally
        {
            // Preserve the caller's tab so existing inventory/gear/food actions stay unchanged.
            if (previous != InterfaceTab.PRAYER && previous != InterfaceTab.NOTHING_SELECTED)
                openPrayerTab(previous);
        }
    }

    /** Called on the worker; use configured F-keys and wait only for the tab to be visible. */
    private boolean openPrayerTab(InterfaceTab tab)
    {
        if (Rs2Tab.getCurrentTab() == tab) return true;
        int key = clientRead(tab::getHotkey, -1);
        if (key >= 0) Rs2Keyboard.keyPress(key);
        else clientRead(() -> { Microbot.getClient().runScript(915, tab.getVarcIntIndex()); return true; }, false);
        return sleepUntil(() -> Rs2Tab.getCurrentTab() == tab, 180);
    }

    private boolean handleThrall(Rs2NpcModel zulrah)
    {
        if (!config.useThralls()) return false;
        if (System.currentTimeMillis() - lastThrallMs < 5_000L) return false;
        if (Rs2Thrall.isActive()) return false;

        Rs2Thrall thrall = bestAvailableThrall();
        if (thrall == null) return false;

        state = DroZulrahState.THRALL;
        status = "Summoning " + thrall.getName();
        lastThrallMs = System.currentTimeMillis();
        // Rs2Magic.cast reads Widget.getBounds() on the caller thread. Snapshot the widget
        // on the client thread, then dispatch the input on this worker without a blocking wait.
        int[] spell = clientRead(() -> {
            Widget root = Microbot.getClient().getWidget(218, 0);
            if (root == null || root.getStaticChildren() == null) return null;
            Widget widget = Rs2Widget.findWidget(thrall.getMagicAction().getName(),
                    Arrays.stream(root.getStaticChildren()).filter(java.util.Objects::nonNull)
                            .collect(java.util.stream.Collectors.toList()));
            if (widget == null || widget.isHidden()) return null;
            Rectangle bounds = widget.getBounds();
            if (bounds == null || bounds.width <= 0 || bounds.height <= 0) return null;
            return new int[]{widget.getId(), bounds.x, bounds.y, bounds.width, bounds.height};
        }, null);
        if (spell == null)
        {
            Rs2Tab.switchToMagicTab();
            return true;
        }
        Rectangle bounds = new Rectangle(spell[1], spell[2], spell[3], spell[4]);
        Microbot.doInvoke(new NewMenuEntry().option("Cast").param0(-1)
                .param1(spell[0]).opcode(MenuAction.CC_OP.getId())
                .identifier(1).itemId(-1).target(thrall.getName()), bounds);
        forceReengage = true;
        Microbot.log("[Dro] Zulrah: thrall cast dispatched; combat loop continues");
        return true;
    }

    private Rs2Thrall bestAvailableThrall()
    {
        Rs2Thrall[] order = {
                Rs2Thrall.GREATER_GHOST, Rs2Thrall.GREATER_SKELETON,
                Rs2Thrall.SUPERIOR_GHOST, Rs2Thrall.SUPERIOR_SKELETON,
                Rs2Thrall.LESSER_GHOST, Rs2Thrall.LESSER_SKELETON
        };
        for (Rs2Thrall thrall : order)
        {
            if (clientRead(() -> Rs2Thrall.canCast(thrall), false)) return thrall;
        }
        return null;
    }

    private boolean handleEmergencyFood(Rs2NpcModel zulrah)
    {
        int tick = clientTick();
        int brews = potionDoses("Saradomin brew");
        if (!emergencyActive)
        {
            if (Rs2Player.getHealthPercentage() > config.panicAt()) return false;
            if (System.currentTimeMillis() - lastEatMs < 1_800L) return false;
            emergencyActive = brews > 0;
            emergencyBrews = 0;
            emergencyBrewBefore = -1;
            emergencyRestoreBefore = -1;
            Rs2Tab.switchToInventoryTab();
            Rs2ItemModel food = Rs2Inventory.get("Manta ray", true);
            if (food == null) food = Rs2Inventory.getInventoryFood().stream()
                    .filter(i -> findInventoryAction(i, "Eat") != null).findFirst().orElse(null);
            Rs2ItemModel brew = System.currentTimeMillis() - lastPotionMs >= 1_800L
                    ? Rs2Inventory.get("Saradomin brew", false) : null;
            final Rs2ItemModel mainFood = food;
            int sent = clientRead(() -> {
                int result = dispatchConsumable(mainFood, "Eat") ? 1 : 0;
                if (dispatchConsumable(brew, "Drink")) result |= 2;
                return result;
            }, 0);
            if ((sent & 1) != 0) lastEatMs = System.currentTimeMillis();
            if ((sent & 2) != 0)
            {
                emergencyBrewBefore = brews;
                emergencyActionTick = tick;
                lastPotionMs = System.currentTimeMillis();
            }
            if (sent != 0) { forceReengage = true; status = "Emergency food then brew"; }
            return sent != 0;
        }
        if (emergencyBrewBefore >= 0 && brews < emergencyBrewBefore)
        {
            emergencyBrews += emergencyBrewBefore - brews;
            emergencyBrewBefore = -1;
        }
        if (emergencyRestoreBefore >= 0 && potionDoses("Super restore") < emergencyRestoreBefore)
        {
            emergencyActive = false;
            emergencyRestoreBefore = -1;
            forceReengage = true;
            return false;
        }
        // While waiting for the potion cooldown, keep protection and movement responsive.
        if (tick - emergencyActionTick < 3 || System.currentTimeMillis() - lastPotionMs < 1_800L) return false;
        emergencyBrewBefore = -1;
        emergencyRestoreBefore = -1;
        boolean needBrew = needsEmergencyBrew(emergencyBrews, Rs2Player.getHealthPercentage(), config.eatAt());
        String potion = needBrew && brews > 0 ? "Saradomin brew" : "Super restore";
        if (emergencyBrews == 0 && brews == 0) { emergencyActive = false; return false; }
        Rs2ItemModel item = Rs2Inventory.get(potion, false);
        if (item == null)
        {
            emergencyActive = false;
            status = "Emergency recovery: missing " + potion;
            return false;
        }
        int before = potionDoses(potion);
        if (!clientRead(() -> dispatchConsumable(item, "Drink"), false)) return false;
        if (potion.equals("Saradomin brew")) emergencyBrewBefore = before;
        else emergencyRestoreBefore = before;
        emergencyActionTick = tick;
        lastPotionMs = System.currentTimeMillis();
        forceReengage = true;
        state = DroZulrahState.EATING;
        status = potion.equals("Saradomin brew") ? "Emergency brew " + (emergencyBrews + 1) : "Emergency super restore";
        return true;
    }

    static boolean needsEmergencyBrew(int consumed, double hpPercent, int recoveryTarget)
    {
        return consumed == 0 || (consumed < 3 && hpPercent < recoveryTarget);
    }

    /** Client thread only; food then brew are queued in the same callback, without mouse waits. */
    private boolean dispatchConsumable(Rs2ItemModel item, String wanted)
    {
        if (item == null) return false;
        Widget inventory = Microbot.getClient().getWidget(ComponentID.INVENTORY_CONTAINER);
        Widget child = inventory == null ? null : inventory.getChild(item.getSlot());
        if (child == null || child.getItemId() != item.getId()) return false;
        String[] actions = child.getActions();
        if (actions == null) actions = item.getInventoryActions();
        if (actions == null) return false;
        for (int i = 0; i < actions.length; i++)
            if (wanted.equalsIgnoreCase(actions[i]))
            {
                Microbot.getClient().menuAction(item.getSlot(), ComponentID.INVENTORY_CONTAINER,
                        MenuAction.CC_OP, i + 1, item.getId(), actions[i], item.getName());
                return true;
            }
        return false;
    }

    private int potionDoses(String name)
    {
        return Rs2Inventory.items(i -> i.getName().startsWith(name + "(")).mapToInt(i -> {
            String n = i.getName();
            int p = n.lastIndexOf('(');
            return p >= 0 && p + 1 < n.length() && Character.isDigit(n.charAt(p + 1))
                    ? Character.digit(n.charAt(p + 1), 10) * i.getQuantity() : 0;
        }).sum();
    }

    private boolean handleNormalFood(Rs2NpcModel zulrah)
    {
        if (emergencyActive) return false;
        if (Rs2Player.getHealthPercentage() > config.eatAt()) return false;
        if (System.currentTimeMillis() - lastEatMs < 850L) return false;

        state = DroZulrahState.EATING;
        status = "Eating";
        boolean ate = combatAction(zulrah, BaseProfileDro.ActionPhase.ACTIVE_WORK, false, Rs2Player::useFood);
        if (ate) lastEatMs = System.currentTimeMillis();
        return ate;
    }

    private boolean handlePrayerRestore(Rs2NpcModel zulrah)
    {
        if (emergencyActive) return false;
        if (Rs2Prayer.getPrayerPoints() > config.restoreAt()) return false;
        if (System.currentTimeMillis() - lastPotionMs < 650L) return false;

        state = DroZulrahState.POTION;
        status = "Restoring prayer";
        boolean restored = combatAction(zulrah, BaseProfileDro.ActionPhase.ACTIVE_WORK, true, () -> {
            if (drinkContains("Super restore")) return true;
            if (drinkContains("Prayer potion")) return true;
            return false;
        });
        if (restored) lastPotionMs = System.currentTimeMillis();
        return restored;
    }

    private boolean handleStatRecovery(Rs2NpcModel zulrah)
    {
        if (emergencyActive) return false;
        if (System.currentTimeMillis() - lastPotionMs < 850L || !Rs2Inventory.contains("Super restore", false)) return false;
        int magicReal = Rs2Player.getRealSkillLevel(Skill.MAGIC);
        int magicBoosted = Rs2Player.getBoostedSkillLevel(Skill.MAGIC);
        int rangeReal = Rs2Player.getRealSkillLevel(Skill.RANGED);
        int rangeBoosted = Rs2Player.getBoostedSkillLevel(Skill.RANGED);
        if (magicBoosted >= magicReal - 4 && rangeBoosted >= rangeReal - 4) return false;

        state = DroZulrahState.POTION;
        status = "Restoring combat stats";
        boolean restored = combatAction(zulrah, BaseProfileDro.ActionPhase.ACTIVE_WORK, false,
                () -> drinkContains("Super restore"));
        if (restored) lastPotionMs = System.currentTimeMillis();
        return restored;
    }

    private boolean handleVenom(Rs2NpcModel zulrah)
    {
        if (emergencyActive) return false;
        if (Rs2Player.hasAntiVenomActive()) return false;
        if (System.currentTimeMillis() - lastPotionMs < 650L) return false;

        if (Rs2Inventory.contains("Anti-venom+", false) || Rs2Inventory.contains("Anti-venom", false))
        {
            state = DroZulrahState.POTION;
            status = "Drinking anti-venom";
            boolean drank = combatAction(zulrah, BaseProfileDro.ActionPhase.ACTIVE_WORK, true, () -> {
                if (drinkContains("Anti-venom+")) return true;
                return drinkContains("Anti-venom");
            });
            if (drank) lastPotionMs = System.currentTimeMillis();
            return drank;
        }
        return false;
    }

    private boolean handleBoosts(Rs2NpcModel zulrah)
    {
        if (emergencyActive) return false;
        if (System.currentTimeMillis() - lastPotionMs < 1_500L) return false;
        CombatStyle style = desiredCombatStyle();
        Skill skill = style == CombatStyle.RANGE ? Skill.RANGED : Skill.MAGIC;
        int real = Rs2Player.getRealSkillLevel(skill);
        int boosted = Rs2Player.getBoostedSkillLevel(skill);
        if (boosted >= real + 2) return false;

        state = DroZulrahState.POTION;
        status = "Boosting " + style.name().toLowerCase();
        boolean drank = combatAction(zulrah, BaseProfileDro.ActionPhase.ACTIVE_WORK, false,
                () -> Rs2Player.drinkCombatPotionAt(skill));
        if (drank) lastPotionMs = System.currentTimeMillis();
        return drank;
    }

    private boolean shouldLeaveFight()
    {
        boolean noFood = Rs2Inventory.getInventoryFood().isEmpty() && !Rs2Inventory.contains("Saradomin brew", false);
        boolean noPrayer = Rs2Prayer.getPrayerPoints() < 8
                && !Rs2Inventory.contains("Super restore", false)
                && !Rs2Inventory.contains("Prayer potion", false);
        return (noFood && Rs2Player.getHealthPercentage() < 65.0) || noPrayer;
    }

    private boolean combatAction(Rs2NpcModel zulrah, BaseProfileDro.ActionPhase phase,
                                 boolean urgent, java.util.function.BooleanSupplier action)
    {
        // Resume once after the complete action batch, not between gear items or before a dodge.
        boolean success = action.getAsBoolean();
        if (success) forceReengage = true;
        return success;
    }

    private boolean reengage(Rs2NpcModel zulrah, boolean forced, String reason)
    {
        if (zulrah == null || zulrah.isDead() || currentNpcId < 0) return false;
        if (currentNpcId == ZULRAH_MELEE && meleeDodgeRequested) return false;
        // handlePosition already protects the full crossing. Do not wait for isMoving to
        // clear after arrival, and never overwrite a walk issued in this same loop pass.
        if (System.currentTimeMillis() - lastMoveMs < REATTACK_COOLDOWN_MS) return false;
        if (!forced && isTargetingZulrah())
        {
            state = DroZulrahState.ATTACKING;
            status = "Fighting Zulrah";
            return false;
        }
        // Throttle duplicate dispatch only; the server enforces the weapon/food cooldown.
        if (System.currentTimeMillis() - lastAttackMs < REATTACK_COOLDOWN_MS) return false;

        state = DroZulrahState.ATTACKING;
        status = reason;
        boolean clicked = clickZulrahAttackNoCamera(zulrah);
        if (clicked)
        {
            lastAttackMs = System.currentTimeMillis();
            forceReengage = false;
        }
        return clicked;
    }

    /**
     * Force an Attack menu invoke directly. The normal Rs2Npc.attack helper refuses to click while
     * Rs2Combat.inCombat() is true, which is unsuitable at Zulrah because snakelings and an already
     * established Zulrah combat state can keep that flag true. Rs2Npc.interact also contains hidden
     * camera/walker recovery. This path does neither: it targets only this Zulrah NPC and leaves the
     * camera alone.
     */
    private boolean clickZulrahAttackNoCamera(Rs2NpcModel zulrah)
    {
        if (zulrah == null || zulrah.isDead()) return false;

        String[] actions = clientRead(() -> {
            NPCComposition composition = zulrah.getTransformedComposition();
            return composition == null || composition.getActions() == null
                    ? null : composition.getActions().clone();
        }, null);
        if (actions == null) return false;
        int attackIndex = -1;
        for (int i = 0; i < actions.length; i++)
        {
            if (actions[i] != null && "Attack".equalsIgnoreCase(actions[i]))
            {
                attackIndex = i;
                break;
            }
        }
        if (attackIndex < 0 || attackIndex > 4) return false;

        MenuAction menuAction;
        switch (attackIndex)
        {
            case 0: menuAction = MenuAction.NPC_FIRST_OPTION; break;
            case 1: menuAction = MenuAction.NPC_SECOND_OPTION; break;
            case 2: menuAction = MenuAction.NPC_THIRD_OPTION; break;
            case 3: menuAction = MenuAction.NPC_FOURTH_OPTION; break;
            case 4: menuAction = MenuAction.NPC_FIFTH_OPTION; break;
            default: return false;
        }

        Rectangle clickbox = clientRead(() -> Rs2UiHelper.getActorClickbox(zulrah), null);
        if (clickbox == null || clickbox.width <= 0 || clickbox.height <= 0)
        {
            clickbox = Rs2UiHelper.getDefaultRectangle();
        }

        Microbot.doInvoke(new NewMenuEntry()
                        .param0(0)
                        .param1(0)
                        .opcode(menuAction.getId())
                        .identifier(zulrah.getIndex())
                        .itemId(-1)
                        .target(zulrah.getName())
                        .actor(zulrah)
                        .option("Attack"),
                clickbox);
        return true;
    }

    private boolean isTargetingZulrah()
    {
        return clientRead(() -> {
            Actor interacting = Microbot.getClient().getLocalPlayer() == null
                    ? null : Microbot.getClient().getLocalPlayer().getInteracting();
            return interacting instanceof NPC && "Zulrah".equalsIgnoreCase(interacting.getName());
        }, false);
    }

    private void returnToFerox(String reason)
    {
        state = DroZulrahState.RETURNING;
        status = reason + " - returning to Ferox";
        disableCombatPrayers();
        Rs2Combat.setAutoRetaliate(false);

        boolean teleported = action(BaseProfileDro.ActionPhase.SETUP, false,
                () -> Rs2Inventory.interact(RINGS_OF_DUELING, "Ferox Enclave")
                        || Rs2Equipment.interact(RINGS_OF_DUELING, "Ferox Enclave"));
        if (teleported)
        {
            feroxRestored = false;
            feroxBankReadyAt = 0L;
            poolDrinkIssuedAt = 0L;
            teleportClickIssuedAt = 0L;
            nextTeleportRetryAt = 0L;
            teleportAttempts = 0;
            inventorySetupReady = false;
            inventorySetup = null;
            selectedSetup = null;
            boatClickPending = false;
            awaitingFightStart = false;
            fightContinueClicked = false;
            resetFightTracking();
        }
        else
        {
            state = DroZulrahState.OUT_OF_SUPPLIES;
            status = "Missing charged Ring of dueling for Ferox";
        }
    }

    private void lootZulrah()
    {
        state = DroZulrahState.LOOTING;
        status = "Collecting Zulrah loot";
        long now = System.currentTimeMillis();
        if (now - lastLootAt < 200L) return;

        Rs2TileItemModel loot = Microbot.getRs2TileItemCache().query()
                .fromWorldView()
                .within(64)
                .where(Rs2TileItemModel::isLootAble)
                .toList()
                .stream()
                .max(Comparator.comparingLong(Rs2TileItemModel::getTotalValue))
                .orElse(null);

        if (loot != null) lootClearSince = 0L;
        if (loot != null && (!Rs2Inventory.isFull()
                || Rs2Inventory.items(i -> i.getId() == loot.getId() && i.isStackable()).findAny().isPresent()))
        {
            long value = Math.max(0, loot.getTotalValue());
            if (action(BaseProfileDro.ActionPhase.SETUP, false, loot::pickup))
            {
                totalLootValue += value;
            }
        }
        else if (loot == null)
        {
            status = "Waiting for Zulrah drops";
            if (lootClearSince == 0L) lootClearSince = now;
            if (now - lootStartedAt >= 6_000L && now - lootClearSince >= 3_000L)
            {
                killLootUntil = 0L;
                Microbot.log("[Dro] Zulrah: loot area clear; returning to bank");
                returnToFerox("Loot collected");
            }
        }
        else
        {
            status = "Making room for Zulrah loot";
            if (now - lastEatMs >= 1_800L && Rs2Player.useFood()) lastEatMs = now;
            else status = "Loot remains; waiting for inventory space";
        }
        lastLootAt = now;
    }

    private void buildGearPlans(InventorySetup setup)
    {
        magicPlan.clear();
        rangePlan.clear();
        hasMagicWeapon = false;
        hasRangeWeapon = false;
        if (setup == null) return;

        Map<Integer, List<GearCandidate>> bySlot = new HashMap<>();
        addGearCandidates(bySlot, setup.getEquipment(), true);
        addGearCandidates(bySlot, setup.getInventory(), false);

        for (Map.Entry<Integer, List<GearCandidate>> entry : bySlot.entrySet())
        {
            int slot = entry.getKey();
            GearCandidate mage = bestCandidate(entry.getValue(), true);
            GearCandidate range = bestCandidate(entry.getValue(), false);
            if (mage != null) magicPlan.put(slot, mage.choice);
            if (range != null) rangePlan.put(slot, range.choice);
        }

        GearChoice mageWeapon = magicPlan.get(EquipmentInventorySlot.WEAPON.getSlotIdx());
        GearChoice rangeWeapon = rangePlan.get(EquipmentInventorySlot.WEAPON.getSlotIdx());
        hasMagicWeapon = mageWeapon != null && mageWeapon.magicScore > mageWeapon.rangeScore;
        hasRangeWeapon = rangeWeapon != null && rangeWeapon.rangeScore > rangeWeapon.magicScore;

        if (!hasMagicWeapon && mageWeapon != null && !looksRanged(mageWeapon.name)) hasMagicWeapon = true;
        if (!hasRangeWeapon && rangeWeapon != null && looksRanged(rangeWeapon.name)) hasRangeWeapon = true;

        omitShieldForTwoHandedWeapon(magicPlan);
        omitShieldForTwoHandedWeapon(rangePlan);
    }

    private void addGearCandidates(Map<Integer, List<GearCandidate>> bySlot,
                                   List<InventorySetupsItem> items, boolean baseEquipped)
    {
        if (items == null) return;
        for (InventorySetupsItem setupItem : items)
        {
            if (setupItem == null || setupItem.getId() <= 0) continue;
            ItemStats stats = clientRead(() -> itemManager.getItemStats(setupItem.getId()), null);
            if (stats == null || !stats.isEquipable() || stats.getEquipment() == null) continue;
            ItemEquipmentStats equipment = stats.getEquipment();
            int slot = equipment.getSlot();
            String name = setupItem.getName() == null ? "" : setupItem.getName();
            int mageScore = equipment.getAmagic() * 20 + Math.round(equipment.getMdmg() * 1000.0f)
                    + styleNameBias(name, true);
            int rangeScore = equipment.getArange() * 20 + equipment.getRstr() * 5
                    + styleNameBias(name, false);
            GearChoice choice = new GearChoice(setupItem.getId(), name, slot,
                    equipment.isTwoHanded(), mageScore, rangeScore);
            bySlot.computeIfAbsent(slot, key -> new ArrayList<>())
                    .add(new GearCandidate(choice, baseEquipped));
        }
    }

    private GearCandidate bestCandidate(List<GearCandidate> candidatesForSlot, boolean mage)
    {
        if (candidatesForSlot == null || candidatesForSlot.isEmpty()) return null;
        return candidatesForSlot.stream().max((a, b) -> {
            int as = mage ? a.choice.magicScore : a.choice.rangeScore;
            int bs = mage ? b.choice.magicScore : b.choice.rangeScore;
            if (as != bs) return Integer.compare(as, bs);
            if (a.baseEquipped != b.baseEquipped) return a.baseEquipped ? 1 : -1;
            return Integer.compare(a.choice.id, b.choice.id);
        }).orElse(null);
    }

    private void omitShieldForTwoHandedWeapon(Map<Integer, GearChoice> plan)
    {
        GearChoice weapon = plan.get(EquipmentInventorySlot.WEAPON.getSlotIdx());
        if (weapon != null && weapon.twoHanded)
        {
            plan.remove(EquipmentInventorySlot.SHIELD.getSlotIdx());
        }
    }

    private int styleNameBias(String itemName, boolean mage)
    {
        String name = itemName == null ? "" : itemName.toLowerCase();
        if (name.contains("void mage helm")) return mage ? 2_000 : -2_000;
        if (name.contains("void ranger helm")) return mage ? -2_000 : 2_000;
        if (looksRanged(name)) return mage ? -500 : 500;
        if (looksMagic(name)) return mage ? 500 : -500;
        return 0;
    }

    private boolean looksRanged(String itemName)
    {
        String name = itemName == null ? "" : itemName.toLowerCase();
        return name.contains("bow") || name.contains("crossbow") || name.contains("blowpipe")
                || name.contains("ballista") || name.contains("chinchompa")
                || name.contains("masori") || name.contains("armadyl") || name.contains("karil")
                || name.contains("crystal body") || name.contains("crystal legs") || name.contains("crystal helm")
                || name.contains("void ranger helm") || name.contains("ava's") || name.contains("assembler");
    }

    private boolean looksMagic(String itemName)
    {
        String name = itemName == null ? "" : itemName.toLowerCase();
        return name.contains("trident") || name.contains("staff") || name.contains("wand")
                || name.contains("sceptre") || name.contains("scepter") || name.contains("nightmare")
                || name.contains("sanguinesti") || name.contains("shadow") || name.contains("dawnbringer")
                || name.contains("ancestral") || name.contains("ahrim") || name.contains("virtus")
                || name.contains("mystic") || name.contains("infinity") || name.contains("dagon'hai")
                || name.contains("bloodbark") || name.contains("swampbark") || name.contains("blue moon")
                || name.contains("void mage helm") || name.contains("occult") || name.contains("tormented bracelet")
                || name.contains("god cape") || name.contains("imbued") && name.contains("cape");
    }

    private CombatStyle desiredCombatStyle()
    {
        // Blue/magic Zulrah has low ranged defence, while green/red are normally maged.
        if (currentNpcId == ZULRAH_MAGIC && hasRangeWeapon) return CombatStyle.RANGE;
        if (hasMagicWeapon) return CombatStyle.MAGIC;
        return CombatStyle.RANGE;
    }

    private int[] switchOrder()
    {
        return new int[]{
                EquipmentInventorySlot.WEAPON.getSlotIdx(),
                EquipmentInventorySlot.SHIELD.getSlotIdx(),
                EquipmentInventorySlot.HEAD.getSlotIdx(),
                EquipmentInventorySlot.CAPE.getSlotIdx(),
                EquipmentInventorySlot.AMULET.getSlotIdx(),
                EquipmentInventorySlot.BODY.getSlotIdx(),
                EquipmentInventorySlot.LEGS.getSlotIdx(),
                EquipmentInventorySlot.GLOVES.getSlotIdx(),
                EquipmentInventorySlot.BOOTS.getSlotIdx(),
                EquipmentInventorySlot.RING.getSlotIdx(),
                EquipmentInventorySlot.AMMO.getSlotIdx()
        };
    }

    private void disableCombatPrayers()
    {
        if (Microbot.getClient() == null) return;
        if (!clientRead(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN, false)) return;
        if (Microbot.getClient().isClientThread())
        {
            // Shutdown cannot run mouse gestures or tab waits on the client thread.
            InterfaceTab previous = Rs2Tab.getCurrentTab();
            Microbot.getClient().runScript(915, InterfaceTab.PRAYER.getVarcIntIndex());
            for (Rs2PrayerEnum prayer : Rs2PrayerEnum.values())
            {
                Widget button = Microbot.getClient().getWidget(prayer.getIndex());
                if (Rs2Prayer.isPrayerActive(prayer) && button != null && !button.isHidden()
                        && ZulrahClickBounds.visible(button.getBounds(), Microbot.getClient().getCanvasWidth(),
                        Microbot.getClient().getCanvasHeight()) != null)
                    Microbot.getClient().menuAction(-1, prayer.getIndex(), MenuAction.CC_OP, 1, -1, "Deactivate", prayer.getName());
            }
            if (previous != InterfaceTab.PRAYER && previous != InterfaceTab.NOTHING_SELECTED)
                Microbot.getClient().runScript(915, previous.getVarcIntIndex());
            return;
        }
        for (Rs2PrayerEnum prayer : Rs2PrayerEnum.values())
            if (clientRead(() -> Rs2Prayer.isPrayerActive(prayer), false)) dispatchPrayer(prayer, false);
    }

    private boolean action(BaseProfileDro.ActionPhase phase, boolean urgent,
                           java.util.function.BooleanSupplier action)
    {
        return action.getAsBoolean();
    }

    private boolean walkLocalNoCamera(LocalPoint localPoint)
    {
        if (localPoint == null) return false;
        WorldPoint worldPoint = clientRead(() -> WorldPoint.fromLocal(Microbot.getClient(), localPoint), null);
        return worldPoint != null && walkNoCamera(worldPoint);
    }

    /** Single raw walk action towards the original controller's local phase tile. */
    private boolean walkLocalOriginal(LocalPoint localPoint)
    {
        if (localPoint == null) return false;
        // A raw WALK menu action, not a generic left click (which can select scenery/NPCs).
        // Use the original local phase tile directly; no template/world conversion in instances.
        Point canvas = clientRead(() -> Perspective.localToCanvas(Microbot.getClient(), localPoint,
                Microbot.getClient().getTopLevelWorldView().getPlane()), null);
        boolean visible = clientRead(() -> canvas != null && Rs2Camera.isTileOnScreen(localPoint), false);
        if (visible)
        {
            Microbot.doInvoke(new NewMenuEntry().param0(canvas.getX()).param1(canvas.getY())
                    .type(MenuAction.WALK).identifier(0).itemId(-1).option("Walk here"),
                    new Rectangle(canvas.getX(), canvas.getY(), 1, 1));
            return true;
        }
        WorldPoint world = clientRead(() -> WorldPoint.fromLocal(Microbot.getClient(), localPoint), null);
        Point minimap = world == null ? null : clientRead(() -> Rs2MiniMap.worldToMinimap(world), null);
        if (minimap != null && clientRead(() -> Rs2MiniMap.isPointInsideMinimap(minimap), false))
        {
            Microbot.getMouse().click(minimap);
            return true;
        }
        return false;
    }

    /**
     * Raw local walking without Rs2Walker.walkTo()/walkMiniMap(). Microbot 2.6.24's raw minimap
     * helper calls alignCameraTowardWalkTarget(), which intentionally varies yaw/pitch. Zulrah does
     * not need that; all trip/fight legs here are short, so click the canvas/minimap directly and
     * never invoke the walker's camera alignment path.
     */
    private boolean walkNoCamera(WorldPoint finalTarget)
    {
        if (finalTarget == null) return false;
        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null || here.getPlane() != finalTarget.getPlane()) return false;

        WorldPoint target = finalTarget;
        int distance = here.distanceTo(finalTarget);
        if (distance > 10)
        {
            double scale = 10.0 / Math.max(1.0, distance);
            int x = here.getX() + (int)Math.round((finalTarget.getX() - here.getX()) * scale);
            int y = here.getY() + (int)Math.round((finalTarget.getY() - here.getY()) * scale);
            target = new WorldPoint(x, y, here.getPlane());
        }

        final WorldPoint stepTarget = target;
        LocalPoint local = clientRead(() -> LocalPoint.fromWorld(
                Microbot.getClient().getTopLevelWorldView(), stepTarget), null);
        if (local != null && clientRead(() -> Rs2Camera.isTileOnScreen(local), false))
        {
            Point canvas = clientRead(() -> Perspective.localToCanvas(Microbot.getClient(), local,
                    Microbot.getClient().getTopLevelWorldView().getPlane()), null);
            if (canvas != null)
            {
                Microbot.doInvoke(new NewMenuEntry().param0(canvas.getX()).param1(canvas.getY())
                        .type(MenuAction.WALK).identifier(0).itemId(-1).option("Walk here"),
                        new Rectangle(canvas.getX(), canvas.getY(), 1, 1));
                return true;
            }
        }

        Point minimap = clientRead(() -> Rs2MiniMap.worldToMinimap(stepTarget), null);
        if (minimap != null && clientRead(() -> Rs2MiniMap.isPointInsideMinimap(minimap), false))
        {
            Microbot.getMouse().click(minimap);
            return true;
        }
        return false;
    }

    private Rs2TileObjectModel findBoat()
    {
        for (int id : SACRIFICIAL_BOATS)
        {
            Rs2TileObjectModel boat = Microbot.getRs2TileObjectCache().query().withId(id).nearest();
            if (boat != null) return boat;
        }
        return null;
    }

    private boolean isInZulrahInstance()
    {
        return clientRead(() -> Microbot.getClient().getTopLevelWorldView() != null
                && Microbot.getClient().getTopLevelWorldView().getScene().isInstance(), false);
    }

    private boolean hasRingOfDueling()
    {
        for (int id : RINGS_OF_DUELING)
        {
            if (Rs2Inventory.contains(id) || Rs2Equipment.isWearing(id)) return true;
        }
        return false;
    }

    private boolean drinkContains(String name)
    {
        return Rs2Inventory.contains(name, false) && Rs2Inventory.interact(name, "Drink", false);
    }

    private ZulrahRotation.Stand desiredStand()
    {
        if (rotation != null) return rotation.stand(phaseIndex);
        if (!candidates.isEmpty())
        {
            ZulrahRotation.Stand stand = null;
            for (ZulrahRotation candidate : candidates)
            {
                ZulrahRotation.Stand candidateStand = candidate.stand(phaseIndex);
                if (stand == null) stand = candidateStand;
                else if (stand != candidateStand) return ZulrahRotation.Stand.SW;
            }
            if (stand != null) return stand;
        }
        return ZulrahRotation.Stand.SW;
    }

    private ZulrahRotation.Stand desiredMovementStand()
    {
        ZulrahRotation.Stand current = desiredStand();
        if (rotation == null || currentNpcId == ZULRAH_MELEE || phaseIndex < 0 || phaseIndex + 1 >= rotation.size())
            return current;
        int elapsed = clientTick() - phaseStartTick;
        int lead = Math.max(0, Math.min(6, config.prepositionTicks()));
        if (lead > 0 && elapsed >= Math.max(0, rotation.ticks(phaseIndex) - lead))
            return rotation.stand(phaseIndex + 1);
        return current;
    }

    private boolean isJad()
    {
        return rotation != null && rotation.isJad(phaseIndex);
    }

    private boolean sameLocal(LocalPoint a, LocalPoint b)
    {
        return a != null && b != null && Math.abs(a.getX() - b.getX()) < 64 && Math.abs(a.getY() - b.getY()) < 64;
    }

    private int localTileDistance(LocalPoint a, LocalPoint b)
    {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())) / 128;
    }

    private boolean isClouded(LocalPoint point)
    {
        long now = System.currentTimeMillis();
        poisonClouds.entrySet().removeIf(entry -> now - entry.getValue() > 22_000L);
        for (LocalPoint cloud : poisonClouds.keySet())
        {
            if (ZulrahMeleeDodge.cloudCovers(point, cloud)) return true;
        }
        return false;
    }

    public void onProjectileMoved(ProjectileMoved event)
    {
        Projectile projectile = event.getProjectile();
        if (projectile == null || !isJad()) return;
        int id = projectile.getId();
        if (id != PROJECTILE_RANGE && id != PROJECTILE_MAGIC) return;
        if (projectile.getTargetActor() != Microbot.getClient().getLocalPlayer()) return;
        int cycle = projectile.getStartCycle();
        // Accept one fresh projectile snapshot, not every movement event or old-phase missiles.
        if (cycle < phaseStartCycle || Microbot.getClient().getGameCycle() - cycle > 30) return;
        jadNextPrayer = jadSequence.projectile(cycle, id == PROJECTILE_RANGE)
                ? Rs2PrayerEnum.PROTECT_MAGIC : Rs2PrayerEnum.PROTECT_RANGE;
    }

    public void onZulrahAnimation(int animation)
    {
        // Matches the helper's 5069 attack-animation prayer flip. Projectile events confirm
        // the next style rather than making a late attempt to block a hit already launched.
        if (animation == 5069 && isJad())
            jadNextPrayer = jadSequence.attack(clientTick())
                    ? Rs2PrayerEnum.PROTECT_MAGIC : Rs2PrayerEnum.PROTECT_RANGE;
        if (currentNpcId == ZULRAH_MELEE && (animation == 5806 || animation == 5807))
        {
            meleeDodgeRequested = true;
        }
    }

    public void onZulrahDespawned(NPC npc)
    {
        if (npc != null && "Zulrah".equalsIgnoreCase(npc.getName()) && npc.isDead())
        {
            kills++;
            inFight = false;
            lootStartedAt = System.currentTimeMillis();
            lootClearSince = 0L;
            killLootUntil = System.currentTimeMillis() + LOOT_WINDOW_MS;
            state = DroZulrahState.LOOTING;
            status = "Zulrah defeated";
            resetFightTracking();
        }
    }

    public void onActorDeath(Actor actor)
    {
        if (actor != null && actor == Microbot.getClient().getLocalPlayer())
        {
            deaths++;
            inFight = false;
            killLootUntil = 0L;
            inventorySetupReady = false;
            inventorySetup = null;
            feroxRestored = false;
            poolDrinkIssuedAt = 0L;
            teleportClickIssuedAt = 0L;
            nextTeleportRetryAt = 0L;
            teleportAttempts = 0;
            state = DroZulrahState.RECOVERING;
            status = "Death detected - regear at Ferox";
            resetFightTracking();
        }
    }

    public void onCloudSpawn(LocalPoint point)
    {
        if (point != null) poisonClouds.put(point, System.currentTimeMillis());
    }

    public void onCloudDespawn(LocalPoint point)
    {
        if (point == null) return;
        poisonClouds.remove(point);
    }

    private void resetFightTracking()
    {
        helperSnapshot = ZulrahPhaseSnapshot.EMPTY;
        emergencyActive = false;
        emergencyBrews = 0;
        emergencyBrewBefore = -1;
        emergencyRestoreBefore = -1;
        currentNpcId = -1;
        phaseIndex = -1;
        phaseStartTick = -1;
        currentNpcLocal = null;
        rotation = null;
        seenTypes.clear();
        candidates.clear();
        candidates.addAll(EnumSet.allOf(ZulrahRotation.class));
        poisonClouds.clear();
        meleeDodgeRequested = false;
        meleeDodgeOrigin = null;
        lastDodgeAttemptMs = 0L;
        meleeHomeStand = null;
        meleeStandOverride = null;
        jadNextPrayer = null;
        jadSequence.reset(false);
        phaseStartCycle = 0;
        boatApproach = null;
        boatApproachRequired = false;
        reengageAfterMovement = false;
        forceReengage = false;
    }

    @Override
    public void shutdown()
    {
        if (watchdog != null) watchdog.shutdownNow();
        if (profile != null) profile.shutdown();
        disableCombatPrayers();
        super.shutdown();
        resetFightTracking();
        runtimeInitialized = false;
        autoRetaliateConfigured = false;
        state = DroZulrahState.IDLE;
        status = "Stopped";
    }

    private int skillXp(Skill skill)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getSkillExperience(skill)).orElse(0);
    }

    private <T> T clientRead(java.util.concurrent.Callable<T> read, T fallback)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(read).orElse(fallback);
    }

    private int clientTick() { return clientRead(() -> Microbot.getClient().getTickCount(), 0); }

    private LocalPoint playerLocal()
    {
        return clientRead(() -> Microbot.getClient().getLocalPlayer() == null ? null
                : Microbot.getClient().getLocalPlayer().getLocalLocation(), null);
    }

    private long xpPerHour(int currentXp, int startingXp)
    {
        long elapsed = getSessionElapsedMs();
        if (elapsed <= 0L) return 0L;
        return Math.max(0L, Math.round((currentXp - startingXp) * 3_600_000.0 / elapsed));
    }

    public DroZulrahState getState(){ return state; }
    public ZulrahPhaseSnapshot getHelperSnapshot(){ return helperSnapshot; }
    public Prayer getHelperPrayer(){
        ZulrahPhaseSnapshot snapshot = helperSnapshot;
        if (snapshot.index < 0) return null;
        if (snapshot.rotation != null && snapshot.rotation.isJad(snapshot.index) && jadNextPrayer != null)
            return jadNextPrayer == Rs2PrayerEnum.PROTECT_MAGIC ? Prayer.PROTECT_FROM_MAGIC : Prayer.PROTECT_FROM_MISSILES;
        ZulrahRotation[] options = snapshot.candidates();
        if (options.length == 0 || snapshot.index >= options[0].size()) return null;
        int type = options[0].types[snapshot.index];
        return type == ZULRAH_MAGIC ? Prayer.PROTECT_FROM_MAGIC : type == ZULRAH_RANGE ? Prayer.PROTECT_FROM_MISSILES : null;
    }
    public String getStatus(){ return status; }
    public ZulrahRotation getRotation(){ return rotation; }
    public int getPhaseIndex(){ return phaseIndex; }
    public int getKills(){ return kills; }
    public int getTrips(){ return trips; }
    public int getDeaths(){ return deaths; }
    public long getTotalLootValue(){ return totalLootValue; }
    public long getSessionElapsedMs(){ return sessionStartedAt <= 0 ? 0 : Math.max(0, System.currentTimeMillis() - sessionStartedAt); }
    public long getMagicXpPerHour(){ return xpPerHour(skillXp(Skill.MAGIC), startingMagicXp); }
    public long getRangedXpPerHour(){ return xpPerHour(skillXp(Skill.RANGED), startingRangedXp); }
    public long getEstimatedProfit(){ return totalLootValue - travelCost; }
    public long getEstimatedProfitPerHour(){
        long elapsed = getSessionElapsedMs();
        return elapsed <= 0 ? 0 : Math.round(getEstimatedProfit() * 3_600_000.0 / elapsed);
    }

    private enum CombatStyle { MAGIC, RANGE }

    private static final class GearCandidate
    {
        private final GearChoice choice;
        private final boolean baseEquipped;
        private GearCandidate(GearChoice choice, boolean baseEquipped)
        {
            this.choice = choice;
            this.baseEquipped = baseEquipped;
        }
    }

    private static final class GearChoice
    {
        private final int id;
        private final String name;
        private final int slot;
        private final boolean twoHanded;
        private final int magicScore;
        private final int rangeScore;

        private GearChoice(int id, String name, int slot, boolean twoHanded, int magicScore, int rangeScore)
        {
            this.id = id;
            this.name = name == null ? "" : name;
            this.slot = slot;
            this.twoHanded = twoHanded;
            this.magicScore = magicScore;
            this.rangeScore = rangeScore;
        }

        private boolean isWearing()
        {
            return Rs2Equipment.isWearing(id) || (!name.isEmpty() && Rs2Equipment.isWearing(name, false));
        }

        private boolean isInInventory()
        {
            return Rs2Inventory.contains(id) || (!name.isEmpty() && Rs2Inventory.contains(name, false));
        }

        private boolean equip()
        {
            Rs2ItemModel item = Rs2Inventory.get(id);
            if (item == null && !name.isEmpty()) item = Rs2Inventory.get(name, false);
            if (item == null || item.getInventoryActions() == null) return false;
            for (String action : item.getInventoryActions())
            {
                if ("Wear".equalsIgnoreCase(action) || "Wield".equalsIgnoreCase(action)
                        || "Equip".equalsIgnoreCase(action))
                    return Rs2Inventory.interact(item, action);
            }
            return false;
        }
    }
}
