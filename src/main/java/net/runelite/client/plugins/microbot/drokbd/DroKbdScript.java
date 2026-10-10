package net.runelite.client.plugins.microbot.drokbd;

/*
 * BETA: INVENTORY SET-UP ONLY.
 *
 * This script always uses the selected Microbot Inventory Setup.
 * Manual/budget loadouts and Diamond ammunition support have been removed.
 */

import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
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
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class DroKbdScript extends Script
{
    private static final int[] BURNING_AMULETS = {
            ItemID.BURNING_AMULET1, ItemID.BURNING_AMULET2, ItemID.BURNING_AMULET3,
            ItemID.BURNING_AMULET4, ItemID.BURNING_AMULET5
    };
    private static final int[] RINGS_OF_DUELING = {
            ItemID.RING_OF_DUELING1, ItemID.RING_OF_DUELING2, ItemID.RING_OF_DUELING3,
            ItemID.RING_OF_DUELING4, ItemID.RING_OF_DUELING5, ItemID.RING_OF_DUELING6,
            ItemID.RING_OF_DUELING7, ItemID.RING_OF_DUELING8
    };
    private static final WorldArea FEROX_ENCLAVE = new WorldArea(3123, 3617, 34, 29, 0);
    private static final WorldPoint FEROX_POOL_POINT = new WorldPoint(3128, 3637, 0);
    private static final int FEROX_REFRESHMENT_POOL = 39651;
    private static final WorldArea LUMBRIDGE = new WorldArea(3190, 3190, 50, 55, 0);
    private static final WorldArea LUMBRIDGE_MIDDLE = new WorldArea(3190, 3190, 50, 55, 1);
    private static final WorldArea EDGEVILLE = new WorldArea(3060, 3450, 66, 70, 0);
    private static final WorldPoint SOUTH_LUMBRIDGE_STAIR = new WorldPoint(3205, 3208, 0);
    private static final WorldPoint NORTH_LUMBRIDGE_STAIR = new WorldPoint(3205, 3229, 0);
    private static final WorldPoint[] SOUTH_LUMBRIDGE_ROUTE = {
            new WorldPoint(3216, 3215, 0), new WorldPoint(3214, 3211, 0),
            new WorldPoint(3210, 3211, 0), new WorldPoint(3207, 3209, 0),
            new WorldPoint(3205, 3208, 0)
    };
    private static final WorldPoint[] NORTH_LUMBRIDGE_ROUTE = {
            new WorldPoint(3214, 3221, 0), new WorldPoint(3214, 3225, 0),
            new WorldPoint(3210, 3228, 0), new WorldPoint(3205, 3229, 0)
    };
    private static final WorldArea KBD_LAIR = new WorldArea(2250, 4650, 65, 70, 0);
    private static final WorldPoint KBD_LAIR_CENTER = new WorldPoint(2273, 4695, 0);
    private static final int KBD_MIN_DISTANCE = 6; // stay strictly outside a 5-tile radius
    private static final int KBD_POSITION_RADIUS = 7;

    // Blowpipe must use extended super antifire. The 4-dose item is ID 22209.
    private static final int[] EXTENDED_SUPER_ANTIFIRE_DOSES = {
            22209, // Extended super antifire(4)
            22212, // Extended super antifire(3)
            22215, // Extended super antifire(2)
            22218  // Extended super antifire(1)
    };

    // Divine combat boosts are timer-driven: one sip lasts five minutes.
    private static final int[] DIVINE_RANGING_DOSES = {
            ItemID.DIVINE_RANGING_POTION4, ItemID.DIVINE_RANGING_POTION3,
            ItemID.DIVINE_RANGING_POTION2, ItemID.DIVINE_RANGING_POTION1
    };
    private static final int[] DIVINE_SUPER_COMBAT_DOSES = {
            ItemID.DIVINE_SUPER_COMBAT_POTION4, ItemID.DIVINE_SUPER_COMBAT_POTION3,
            ItemID.DIVINE_SUPER_COMBAT_POTION2, ItemID.DIVINE_SUPER_COMBAT_POTION1
    };
    private static final int[] SUPER_COMBAT_DOSES = {
            ItemID.SUPER_COMBAT_POTION4, ItemID.SUPER_COMBAT_POTION3,
            ItemID.SUPER_COMBAT_POTION2, ItemID.SUPER_COMBAT_POTION1
    };

    // Cure KBD poison with either potion family when the user brings it.
    // Include every dose explicitly so partially-used potions are supported.
    private static final int[] ANTIPOISON_DOSES = {
            ItemID.ANTIPOISON4, ItemID.ANTIPOISON3,
            ItemID.ANTIPOISON2, ItemID.ANTIPOISON1
    };
    private static final int[] SUPERANTIPOISON_DOSES = {
            ItemID.SUPERANTIPOISON4, ItemID.SUPERANTIPOISON3,
            ItemID.SUPERANTIPOISON2, ItemID.SUPERANTIPOISON1
    };

    private static final long DIVINE_COMBAT_DURATION_MS = 5 * 60 * 1000L;

    // Rs2Combat uses a 0-1000 special-energy scale.
    private static final int DRAGON_CROSSBOW_SPEC_COST = 600;
    private static final int ARMADYL_CROSSBOW_SPEC_COST = 500;
    private static final int ZARYTE_CROSSBOW_SPEC_COST = 750;
    private static final int TOXIC_BLOWPIPE_SPEC_COST = 500;

    // User-verified Lava Maze surface route. One target from each group is chosen
    // once per trip. The choices are kept until the ladder transition completes,
    // so the route varies without changing destination every script tick.
    private static final WorldPoint[] KBD_ROUTE_POINT_1_VARIANTS = {
            new WorldPoint(3007, 3837, 0),
            new WorldPoint(3008, 3838, 0),
            new WorldPoint(3008, 3839, 0),
            new WorldPoint(3009, 3838, 0),
            new WorldPoint(3009, 3837, 0),
            new WorldPoint(3010, 3837, 0)
    };

    // User-confirmed two-tile KBD fence gate.  Keep both tiles because the
    // open/closed object can be exposed on either half of the gate.
    private static final WorldPoint KBD_GATE_OBJECT_TILE = new WorldPoint(3007, 3849, 0);
    private static final WorldPoint KBD_GATE_OBJECT_TILE_NORTH = new WorldPoint(3007, 3850, 0);
    private static final WorldPoint[] KBD_ROUTE_POINT_2_VARIANTS = {
            new WorldPoint(3007, 3846, 0),
            new WorldPoint(3006, 3846, 0),
            new WorldPoint(3006, 3847, 0),
            new WorldPoint(3007, 3847, 0),
            new WorldPoint(3006, 3849, 0)
    };

    // The ladder is intentionally approached from the north side.
    private static final WorldPoint KBD_LADDER_TILE = new WorldPoint(3017, 3849, 0);
    private static final WorldPoint[] KBD_ROUTE_POINT_3_VARIANTS = {
            new WorldPoint(3018, 3849, 0),
            new WorldPoint(3016, 3850, 0),
            new WorldPoint(3017, 3850, 0),
            new WorldPoint(3018, 3850, 0),
            new WorldPoint(3016, 3851, 0),
            new WorldPoint(3017, 3851, 0),
            new WorldPoint(3018, 3851, 0)
    };

    private static final WorldPoint KBD_LEVER_INTERACTION_TILE = new WorldPoint(3067, 10253, 0);
    private static final WorldPoint KBD_LEVER_OBJECT_TILE = new WorldPoint(3067, 10251, 0);

    // Small local variations after the ladder. These are only staging tiles; the
    // script still stands on the exact interaction tile before pulling the lever.
    private static final WorldPoint[] KBD_LEVER_STAGING_VARIANTS = {
            new WorldPoint(3066, 10253, 0),
            new WorldPoint(3068, 10253, 0),
            new WorldPoint(3066, 10254, 0),
            new WorldPoint(3067, 10254, 0),
            new WorldPoint(3068, 10254, 0),
            KBD_LEVER_INTERACTION_TILE
    };

    private static final WorldArea LAVA_DUNGEON = new WorldArea(3000, 10180, 100, 110, 0);

    private DroKbdConfig config;
    private Rs2InventorySetup inventorySetup;
    private final AtomicBoolean threatLogoutActive = new AtomicBoolean(false);
    private boolean bankCleared;
    private boolean combatMouseHandled;
    private final DroKbdPvpSurrender pvpSurrender = new DroKbdPvpSurrender();
    private DroKbdAntibanSnapshot antibanSnapshot;
    private volatile boolean stopping = true;
    private boolean wasInWilderness;
    private boolean wasAlive;
    private boolean feroxRestored;
    private Boolean useNorthLumbridgeRoute;
    private boolean deathRecoveryPending;
    private boolean inventorySetupReadyForDeparture;
    private boolean initialLairCenteringPending;
    private WorldPoint initialLairCenterTarget;
    private int lastRangingPotionBoostLevel;
    private long divineCombatPotionUntil;
    private boolean lastCombatPotionWasDivine;
    private int divineSipMinHpPercent;

    // Occupied-lair recovery state.
    // If a player is present we hop immediately unless already fighting KBD.
    // Two failed hop attempts escalate to Ferox -> world hop -> repeat trip.
    private boolean occupiedLairHopPending;
    private int occupiedLairHopFailures;
    private long lastOccupiedLairHopAttemptAt;
    private boolean hopAfterOccupiedEscape;

    // Per-trip wilderness route state. Current targets reset each trip; the
    // previous targets remain so consecutive trips never select the same tile
    // for the same route stage during this script session.
    private int wildernessRouteStage;
    private WorldPoint routePoint1Target;
    private WorldPoint routePoint2Target;
    private WorldPoint routePoint3Target;
    private WorldPoint previousRoutePoint1Target;
    private WorldPoint previousRoutePoint2Target;
    private WorldPoint previousRoutePoint3Target;
    private WorldPoint lastSurfaceClickTarget;
    private long lastSurfaceClickAt;
    private WorldPoint leverStagingTarget;
    private boolean leverStagingVisited;

    /*
     * INVENTORY SET-UP ONLY.
     *
     * Crossbow ammunition is prepared BEFORE Rs2InventorySetup is allowed to regear.
     * Blowpipe and melee modes bypass this bootstrap entirely.
     */
    private int ammoPrepStage;
    private int ammoRubyTarget;
    private long ammoEquipIssuedAt;

    // Prevent first-entry NPC cache delay from being mistaken for a completed kill.
    private boolean kbdSeenAliveThisCycle;
    private long kbdLootUntil;

    // Lair-entry grace protects against a transient false antifire read directly
    // after the lever transition.
    private long lairEnteredAt;
    private long antifireMissingSince;

    // Pool -> bank needs a real wall-clock gate because prepareTrip() does not
    // globally honor nextActionAt for every banking branch.
    private long feroxBankReadyAt;

    private long nextActionAt;
    private long lastLootAt;

    @Getter private volatile DroKbdState state = DroKbdState.RECOVERING;
    @Getter private volatile String status = "Idle";
    @Getter private volatile int kills;
    @Getter private volatile int trips;
    @Getter private volatile int deaths;
    @Getter private volatile long totalLootValue;
    private long sessionStartedAt;
    private int startingRangedXp;

    public boolean run(DroKbdConfig config)
    {
        this.config = config;
        bankCleared = false;
        inventorySetup = null;
        combatMouseHandled = false;
        configureAdaptiveProfile();
        stopping = false;
        pvpSurrender.reset();
        wasInWilderness = false;
        wasAlive = true;

        // Startup-only Ferox behavior:
        // If the script is launched while already in Ferox, do not touch the pool.
        // Later trip/death resets still set feroxRestored=false so normal pool use resumes.
        WorldPoint startupLocation = location();
        feroxRestored = startupLocation != null && FEROX_ENCLAVE.contains(startupLocation);

        useNorthLumbridgeRoute = null;
        deathRecoveryPending = false;
        inventorySetupReadyForDeparture = false;
        initialLairCenteringPending = true;
        initialLairCenterTarget = null;
        lastRangingPotionBoostLevel = -1;
        divineCombatPotionUntil = 0L;
        lastCombatPotionWasDivine = false;
        divineSipMinHpPercent = 0;
        occupiedLairHopPending = false;
        occupiedLairHopFailures = 0;
        lastOccupiedLairHopAttemptAt = 0L;
        hopAfterOccupiedEscape = false;
        wildernessRouteStage = 0;
        routePoint1Target = null;
        routePoint2Target = null;
        routePoint3Target = null;
        previousRoutePoint1Target = null;
        previousRoutePoint2Target = null;
        previousRoutePoint3Target = null;
        lastSurfaceClickTarget = null;
        lastSurfaceClickAt = 0L;
        leverStagingTarget = null;
        leverStagingVisited = false;
        ammoPrepStage = 0;
        ammoRubyTarget = 0;
        ammoEquipIssuedAt = 0L;
        kbdSeenAliveThisCycle = false;
        kbdLootUntil = 0L;
        lairEnteredAt = 0L;
        antifireMissingSince = 0L;
        feroxBankReadyAt = 0L;
        nextActionAt = 0L;
        lastLootAt = 0L;
        kills = 0;
        trips = 0;
        deaths = 0;
        totalLootValue = 0L;
        sessionStartedAt = System.currentTimeMillis();
        startingRangedXp = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getSkillExperience(Skill.RANGED)).orElse(0);
        Rs2Camera.setZoom(Rs2Random.between(155, 170));
        int startupPitch = Rs2Random.between(2800, 3064);
        Microbot.getClientThread().invoke(() ->
                Microbot.getClient().setCameraPitchTarget(startupPitch));
        net.runelite.client.plugins.microbot.util.walker.Rs2PathApi.exit();

        scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::watchWilderness, 0, 100, TimeUnit.MILLISECONDS);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try
            {
                if (stopping || !Microbot.isLoggedIn() || !super.run() || threatLogoutActive.get()) return;
                tick();
            }
            catch (Exception e)
            {
                status = "Recovering: " + e.getClass().getSimpleName();
                Microbot.log("DroKBD loop: " + e.getMessage());
            }
        }, 0, 250, TimeUnit.MILLISECONDS);
        return true;
    }

    private void tick()
    {
        WorldPoint location = location();
        if (location == null) return;
        boolean alive = Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) > 0;
        if (wasAlive && !alive)
        {
            deaths++;
            deathRecoveryPending = true;
        }
        wasAlive = alive;
        if (!alive) return;

        boolean inWild = wilderness(location);
        if (deathRecoveryPending && isDeathRecoveryArea(location))
        {
            resetTripAfterDeath();
        }
        else if (wasInWilderness && !inWild && isDeathRecoveryArea(location))
        {
            // Covers a fast respawn where no zero-HP client tick was observable.
            deaths++;
            resetTripAfterDeath();
        }
        boolean inLair = inKbdLair(location);
        // Do not enable auto-retaliate merely because we crossed the lever.
        // Occupancy must be checked first so an occupied public lair can be
        // abandoned without retaliating or taking an unnecessary step inward.
        if (!inLair)
        {
            Rs2Combat.setAutoRetaliate(false);
        }
        wasInWilderness = inWild;

        if (updatePvpSurrender(inWild))
        {
            state = DroKbdState.SURRENDERING;
            status = "PvP combat - accepting death";
            Rs2Combat.setAutoRetaliate(false);
            return;
        }

        if (inLair)
        {
            fightKbd();
            return;
        }
        if (LAVA_DUNGEON.contains(location))
        {
            enterLair();
            return;
        }
        if (inWild)
        {
            crossWilderness(location);
            return;
        }
        prepareTrip(location);
    }

    private void prepareTrip(WorldPoint location)
    {
        if (hopAfterOccupiedEscape)
        {
            hopOutsideLairAfterOccupiedEscape();
            return;
        }

        boolean recovering = isDeathRecoveryArea(location);
        state = recovering ? DroKbdState.RECOVERING : DroKbdState.BANKING;
        status = LUMBRIDGE.contains(location) ? "Recovering from Lumbridge"
                : EDGEVILLE.contains(location) ? "Recovering from Edgeville" : "Preparing KBD setup";
        if (LUMBRIDGE.contains(location) || LUMBRIDGE_MIDDLE.contains(location))
        {
            walkUpstairsLumbridge(location);
            return;
        }
        if (FEROX_ENCLAVE.contains(location) && !feroxRestored)
        {
            if (location.distanceTo(FEROX_POOL_POINT) > 8)
            {
                status = "Walking to Ferox restoration pool";
                Rs2Walker.walkTo(FEROX_POOL_POINT, 2);
                delay(700);
                return;
            }
            Rs2TileObjectModel pool = Microbot.getRs2TileObjectCache().query()
                    .withId(FEROX_REFRESHMENT_POOL).nearest();
            if (pool == null)
            {
                pool = Microbot.getRs2TileObjectCache().query()
                        .withName("Pool of Refreshment").nearest();
            }
            if (pool != null)
            {
                status = "Restoring stats at Ferox";
                if (pool.click("Drink"))
                {
                    feroxRestored = true;

                    // Give the pool animation/stat restore time to complete, then
                    // add a small action-to-action variation before touching bank.
                    long poolPause = Rs2Random.between(2600, 3800)
                            + Rs2Random.between(75, 250);
                    feroxBankReadyAt = System.currentTimeMillis() + poolPause;

                    status = "Restored at Ferox - pausing before bank";
                    delay(poolPause);
                }
                return;
            }
            status = "Waiting for Ferox restoration pool";
            return;
        }
        if (FEROX_ENCLAVE.contains(location)
                && feroxBankReadyAt > System.currentTimeMillis())
        {
            status = "Waiting after Ferox pool";
            return;
        }

        if (!Rs2Bank.isNearBank(18))
        {
            Rs2Bank.walkToBank(FEROX_ENCLAVE.contains(location) ? BankLocation.FEROX_ENCLAVE
                    : LUMBRIDGE.contains(location) ? BankLocation.LUMBRIDGE_TOP
                    : EDGEVILLE.contains(location) ? BankLocation.EDGEVILLE : Rs2Bank.getNearestBank());
            return;
        }
        if (inventorySetupReadyForDeparture)
        {
            finishPreparation();
            return;
        }

        InventorySetup selectedSetup = config.inventorySetup();
        if (selectedSetup == null)
        {
            status = "Select a Microbot Inventory Setup";
            return;
        }

        /*
         * Crossbow modes only:
         * bank -> withdraw selected enchanted bolts -> close bank -> equip bolts
         * -> reopen bank -> normal Inventory Setup regear.
         * Blowpipe and melee skip this step.
         */
        if (!prepareAmmoBeforeInventorySetup(selectedSetup)) return;

        if (inventorySetup == null)
        {
            inventorySetup = new Rs2InventorySetup(sanitizeTripSetup(selectedSetup), mainScheduledFuture);
        }

        if (!inventorySetup.loadEquipment()) return;
        if (!inventorySetup.loadInventory()) return;
        if (!inventorySetup.wearEquipment()) return;

        if (selectedCombatMode().isAmmunitionBootstrap()
                && !Rs2Equipment.isWearing(selectedAmmoId()))
        {
            status = "Inventory Setup must keep " + selectedAmmoName() + " equipped";
            return;
        }
        if (selectedCombatMode() == DroKbdAmmo.TOXIC_BLOWPIPE
                && !Rs2Equipment.isWearing(ItemID.TOXIC_BLOWPIPE))
        {
            status = "Inventory Setup must equip a charged Toxic blowpipe";
            return;
        }

        if (!validateTripLoadout()) return;
        inventorySetupReadyForDeparture = true;
        finishPreparation();
        return;
    }

    private void finishPreparation()
    {
        if (Rs2Bank.isOpen()) Rs2Bank.closeBank();

        if (!hasRequiredKbdAntifireActive())
        {
            status = selectedCombatMode() == DroKbdAmmo.TOXIC_BLOWPIPE
                    ? "Drinking extended super antifire"
                    : "Drinking selected setup antifire";
            if (!drinkKbdAntifire()) return;
            delay(700);
            return;
        }

        teleportToLavaMaze();
    }

    private void teleportToLavaMaze()
    {
        if (!ready()) return;
        state = DroKbdState.TELEPORTING;
        status = "Teleporting to Lava Maze";
        boolean used = Rs2Equipment.interact(BURNING_AMULETS, "Lava Maze")
                || Rs2Inventory.interact(BURNING_AMULETS, "Lava Maze");
        if (used && sleepUntil(Rs2Dialogue::isInDialogue, 2000))
        {
            Rs2Dialogue.clickOption("Okay, teleport to level");
            boolean arrived = sleepUntil(() -> wilderness(location()), 6000);
            if (arrived)
            {
                resetCurrentWildernessRoute();
                trips++;
            }
        }
        delay(650 + Rs2Random.between(75, 225));
    }

    private boolean validateTripLoadout()
    {
        if (!hasBurningAmulet())
        {
            status = "Loadout needs a charged Burning amulet";
            return false;
        }
        if (selectedCombatMode() != DroKbdAmmo.TOXIC_BLOWPIPE
                && !Rs2Equipment.isWearing(ItemID.ANTIDRAGON_SHIELD)
                && !Rs2Equipment.isWearing(ItemID.DRAGONFIRE_SHIELD))
        {
            status = "Loadout must wear an anti-dragon or Dragonfire shield";
            return false;
        }
        boolean hasDuelingRing = false;
        for (int ring : RINGS_OF_DUELING)
            hasDuelingRing |= Rs2Equipment.isWearing(ring) || Rs2Inventory.contains(ring);
        if (!hasDuelingRing)
        {
            status = "Loadout needs a charged Ring of dueling";
            return false;
        }
        if (selectedCombatMode() == DroKbdAmmo.TOXIC_BLOWPIPE)
        {
            if (!Rs2Player.hasSuperAntiFireActive() && !hasExtendedSuperAntifireDose())
            {
                status = "Blowpipe mode needs Extended super antifire (4)-(1)";
                return false;
            }
        }
        else if (!Rs2Player.hasAntiFireActive() && !Rs2Inventory.contains(false, "antifire"))
        {
            status = "Loadout needs an antifire potion";
            return false;
        }
        if (selectedCombatMode().isAmmunitionBootstrap()
                && !Rs2Equipment.isWearing("crossbow", false)
                && !Rs2Equipment.isWearing("bow", false))
        {
            status = "Loadout needs a ranged weapon equipped";
            return false;
        }
        return true;
    }

    private boolean restorePrayer()
    {
        return Rs2Player.drinkPrayerPotionAt(config.prayerAt());
    }

    private void crossWilderness(WorldPoint location)
    {
        state = DroKbdState.CROSSING_WILDERNESS;
        status = "Crossing to KBD ladder";

        if (!ready()) return;

        ensureWildernessRouteTargets(location);

        // If the gate is observed closed after we thought we passed it, immediately
        // return to the gate stage. This prevents ladder clicks through the fence.
        if (wildernessRouteStage >= 3 && location.getX() <= 3010)
        {
            Rs2TileObjectModel visibleGate = findKbdSurfaceGate();
            if (visibleGate != null && hasAction(visibleGate, "Open"))
            {
                wildernessRouteStage = 2;
            }
        }

        switch (wildernessRouteStage)
        {
            case 0:
                if (location.distanceTo(routePoint1Target) <= 2)
                {
                    clearSurfaceWalkTarget();
                    wildernessRouteStage = 1;
                    routeHandoffDelay();
                    return;
                }
                status = "Running Lava Maze route - point 1";
                clickSurfaceRouteTarget(routePoint1Target);
                routeTravelDelay();
                return;

            case 1:
                if (location.distanceTo(routePoint2Target) <= 2)
                {
                    clearSurfaceWalkTarget();
                    wildernessRouteStage = 2;
                    routeHandoffDelay();
                    return;
                }
                status = "Running Lava Maze route - gate approach";
                clickSurfaceRouteTarget(routePoint2Target);
                routeTravelDelay();
                return;

            case 2:
            {
                clearSurfaceWalkTarget();
                Rs2TileObjectModel gate = findKbdSurfaceGate();

                if (gate == null)
                {
                    status = "Waiting for KBD gate";
                    delay(Rs2Random.between(180, 320));
                    return;
                }

                if (hasAction(gate, "Open"))
                {
                    status = "Opening KBD fence gate";

                    // A successful click is NOT enough to advance. During the gate
                    // morph the object cache can briefly return null, which previously
                    // looked like an open gate and let stage 3 run into the fence.
                    // Remain in the gate stage until we positively see Close or the
                    // player is already east/through the gate.
                    if (gate.click("Open"))
                    {
                        sleepUntil(() -> isKbdGateConfirmedOpen() || isPastKbdGate(location()), 2400);
                    }

                    if (isKbdGateConfirmedOpen() || isPastKbdGate(location()))
                    {
                        wildernessRouteStage = 3;
                        status = "KBD gate open - continuing to ladder";
                        delay(Rs2Random.between(75, 150));
                    }
                    else
                    {
                        // Keep stage 2 and retry Open next pass. Do not ever treat a
                        // transient null object as proof that the gate opened.
                        status = "KBD gate still closed - retrying Open";
                        delay(Rs2Random.between(180, 320));
                    }
                    return;
                }

                // Close means the gate is already open. Never close it; hand off
                // immediately to the randomized north-side ladder approach.
                if (hasAction(gate, "Close"))
                {
                    wildernessRouteStage = 3;
                    delay(Rs2Random.between(75, 150));
                    return;
                }

                status = "Confirming KBD gate state";
                delay(Rs2Random.between(180, 320));
                return;
            }

            case 3:
                if (location.distanceTo(routePoint3Target) <= 2)
                {
                    clearSurfaceWalkTarget();
                    wildernessRouteStage = 4;
                    routeHandoffDelay();
                    return;
                }
                status = "Running through gate to KBD ladder";
                clickSurfaceRouteTarget(routePoint3Target);
                routeTravelDelay();
                return;

            default:
            {
                // Last safety check before ladder interaction.
                Rs2TileObjectModel gate = location.getX() <= 3010 ? findKbdSurfaceGate() : null;
                if (gate != null && hasAction(gate, "Open"))
                {
                    wildernessRouteStage = 2;
                    return;
                }

                Rs2TileObjectModel ladder = findKbdSurfaceLadder();
                if (ladder != null
                        && hasAction(ladder, "Climb-down")
                        && location.distanceTo(ladder.getWorldLocation()) <= 7)
                {
                    clearSurfaceWalkTarget();
                    state = DroKbdState.ENTERING_DUNGEON;
                    status = "Climbing into Lava Maze Dungeon";

                    if (ladder.click("Climb-down"))
                    {
                        boolean entered = sleepUntil(() -> {
                            WorldPoint current = location();
                            return current != null && LAVA_DUNGEON.contains(current);
                        }, 6000);

                        if (entered)
                        {
                            clearSurfaceWalkTarget();
                            status = "Entered Lava Maze Dungeon";
                            delay(Rs2Random.between(175, 325));
                        }
                        else
                        {
                            status = "Ladder transition not confirmed - retrying";
                            delay(Rs2Random.between(250, 425));
                        }
                    }
                    else
                    {
                        status = "KBD ladder click failed - retrying";
                        delay(Rs2Random.between(180, 320));
                    }
                    return;
                }

                status = "Moving into north-side ladder range";
                clickSurfaceRouteTarget(routePoint3Target);
                routeTravelDelay();
                return;
            }
        }
    }

    private void ensureWildernessRouteTargets(WorldPoint here)
    {
        if (routePoint1Target != null) return;

        routePoint1Target = randomDifferentPoint(KBD_ROUTE_POINT_1_VARIANTS, previousRoutePoint1Target);
        routePoint2Target = randomDifferentPoint(KBD_ROUTE_POINT_2_VARIANTS, previousRoutePoint2Target);
        routePoint3Target = randomDifferentPoint(KBD_ROUTE_POINT_3_VARIANTS, previousRoutePoint3Target);

        previousRoutePoint1Target = routePoint1Target;
        previousRoutePoint2Target = routePoint2Target;
        previousRoutePoint3Target = routePoint3Target;

        // Sensible restart recovery without making normal teleport starts skip ahead.
        if (here != null && here.getY() >= 3847 && here.getX() >= 3012)
        {
            wildernessRouteStage = 3;
        }
        else if (here != null && here.getY() >= 3845
                && here.getX() >= 3004 && here.getX() <= 3010)
        {
            wildernessRouteStage = 2;
        }
        else if (here != null && here.getY() >= 3841
                && here.getX() >= 3004 && here.getX() <= 3012)
        {
            wildernessRouteStage = 1;
        }

        Microbot.log("DroKBD wilderness route: " + routePoint1Target + " -> "
                + routePoint2Target + " -> gate -> " + routePoint3Target + " -> ladder");
    }

    private WorldPoint randomDifferentPoint(WorldPoint[] points, WorldPoint previous)
    {
        if (points == null || points.length == 0) return null;
        if (points.length == 1) return points[0];

        int start = Rs2Random.between(0, points.length);
        for (int offset = 0; offset < points.length; offset++)
        {
            WorldPoint candidate = points[(start + offset) % points.length];
            if (previous == null || !previous.equals(candidate)) return candidate;
        }
        return points[start];
    }

    /**
     * Wilderness travel deliberately avoids a full ShortestPath route here. The
     * supplied route is already broken into safe click-sized legs. A click target
     * is sticky for a short window so the 250 ms main loop cannot spam/re-aim it.
     */
    private boolean clickSurfaceRouteTarget(WorldPoint target)
    {
        if (target == null) return false;

        long now = System.currentTimeMillis();
        if (target.equals(lastSurfaceClickTarget)
                && now - lastSurfaceClickAt < 1400L
                && Rs2Player.isMoving())
        {
            return true;
        }

        boolean clicked = Rs2Walker.walkMiniMap(target);
        if (!clicked) clicked = Rs2Walker.walkFastCanvas(target);

        if (clicked)
        {
            lastSurfaceClickTarget = target;
            lastSurfaceClickAt = now;
        }
        return clicked;
    }

    private void clearSurfaceWalkTarget()
    {
        Rs2Walker.setTarget(null);
        lastSurfaceClickTarget = null;
        lastSurfaceClickAt = 0L;
    }

    private void routeTravelDelay()
    {
        delay(Rs2Random.between(225, 375));
    }

    private void routeHandoffDelay()
    {
        delay(Rs2Random.between(75, 160));
    }

    private Rs2TileObjectModel findKbdSurfaceGate()
    {
        // Prefer an actionable gate object on either of the two user-confirmed
        // gate tiles. This avoids selecting a nearby fence/gate-shaped object.
        Rs2TileObjectModel gate = Microbot.getRs2TileObjectCache().query()
                .within(KBD_GATE_OBJECT_TILE, 2)
                .where(object -> object.getWorldLocation() != null
                        && (object.getWorldLocation().distanceTo(KBD_GATE_OBJECT_TILE) <= 1
                        || object.getWorldLocation().distanceTo(KBD_GATE_OBJECT_TILE_NORTH) <= 1)
                        && (hasAction(object, "Open") || hasAction(object, "Close")))
                .nearest();

        if (gate == null)
        {
            // Name fallback for cache/composition oddities, still tightly bounded
            // to the actual gate instead of searching the surrounding fence.
            gate = Microbot.getRs2TileObjectCache().query()
                    .withName("Gate")
                    .within(KBD_GATE_OBJECT_TILE, 2)
                    .nearest();
        }
        return gate;
    }

    private boolean isKbdGateConfirmedOpen()
    {
        Rs2TileObjectModel gate = findKbdSurfaceGate();
        return gate != null && hasAction(gate, "Close") && !hasAction(gate, "Open");
    }

    private boolean isPastKbdGate(WorldPoint point)
    {
        // The verified route approaches from the west/south-west and exits east
        // toward the ladder. X >= 3009 is safely beyond the gate interaction line.
        return point != null && point.getX() >= 3009 && point.getY() >= 3848;
    }

    private Rs2TileObjectModel findKbdSurfaceLadder()
    {
        Rs2TileObjectModel ladder = Microbot.getRs2TileObjectCache().query()
                .withId(ObjectID.LADDER_18987)
                .where(object -> object.getWorldLocation() != null
                        && object.getWorldLocation().distanceTo(KBD_LADDER_TILE) <= 1
                        && hasAction(object, "Climb-down"))
                .nearest();

        if (ladder == null)
        {
            ladder = Microbot.getRs2TileObjectCache().query()
                    .withName("Ladder")
                    .where(object -> object.getWorldLocation() != null
                            && object.getWorldLocation().distanceTo(KBD_LADDER_TILE) <= 1
                            && hasAction(object, "Climb-down"))
                    .nearest();
        }
        return ladder;
    }

    private void resetCurrentWildernessRoute()
    {
        wildernessRouteStage = 0;
        routePoint1Target = null;
        routePoint2Target = null;
        routePoint3Target = null;
        lastSurfaceClickTarget = null;
        lastSurfaceClickAt = 0L;
        leverStagingTarget = null;
        leverStagingVisited = false;
        Rs2Walker.setTarget(null);
    }

    private void enterLair()
    {
        state = DroKbdState.ENTERING_LAIR;

        WorldPoint here = location();
        if (here == null) return;

        // Never pull the lair lever without the protection required by this combat mode.
        if (!hasRequiredKbdAntifireActive())
        {
            status = selectedCombatMode() == DroKbdAmmo.TOXIC_BLOWPIPE
                    ? "Activating extended super antifire before KBD lair"
                    : "Activating antifire before KBD lair";
            if (drinkKbdAntifire())
            {
                delay(650 + Rs2Random.between(75, 250));
            }
            return;
        }

        /*
         * After climbing down, stay entirely on local clicks. A tiny staging
         * variation is selected once per trip, then the script moves to the exact
         * interaction tile before pulling. This changes the dungeon approach without
         * letting the global pathfinder choose the ladder back to the surface.
         */
        if (leverStagingTarget == null)
        {
            leverStagingTarget = chooseLeverStagingTarget();
        }

        if (!leverStagingVisited
                && leverStagingTarget != null
                && !KBD_LEVER_INTERACTION_TILE.equals(leverStagingTarget)
                && here.distanceTo(leverStagingTarget) > 0)
        {
            status = "Approaching KBD lever";
            localTravelClick(leverStagingTarget);
            delay(Rs2Random.between(160, 300));
            return;
        }
        leverStagingVisited = true;

        int leverDistance = here.distanceTo(KBD_LEVER_INTERACTION_TILE);
        if (leverDistance > 0)
        {
            status = "Stepping to KBD lever";
            if (localTravelClick(KBD_LEVER_INTERACTION_TILE))
            {
                sleepUntil(() -> {
                    WorldPoint current = location();
                    return current != null
                            && current.distanceTo(KBD_LEVER_INTERACTION_TILE) == 0;
                }, 2200);
            }

            delay(Rs2Random.between(140, 260));
            return;
        }

        if (!ready()) return;

        status = "Pulling KBD lever";

        // Bind to the wall location as well as the Pull action. No object ID needed.
        Rs2TileObjectModel lever = Microbot.getRs2TileObjectCache().query()
                .where(object -> object.getWorldLocation() != null
                        && object.getWorldLocation().distanceTo(KBD_LEVER_OBJECT_TILE) <= 1
                        && hasAction(object, "Pull"))
                .nearest();

        // Conservative fallback: we are standing on the known adjacent tile,
        // so a nearby object with Pull is still safe to use.
        if (lever == null)
        {
            lever = Microbot.getRs2TileObjectCache().query()
                    .where(object -> object.getWorldLocation() != null
                            && object.getWorldLocation().distanceTo(KBD_LEVER_INTERACTION_TILE) <= 3
                            && hasAction(object, "Pull"))
                    .nearest();
        }

        if (lever == null)
        {
            status = "KBD lever not found - retrying";
            delay(550);
            return;
        }

        if (!lever.click("Pull"))
        {
            status = "KBD lever interaction failed - retrying";
            delay(500);
            return;
        }

        boolean entered = sleepUntil(() -> inKbdLair(location()), 6000);
        if (!entered)
        {
            status = "Lever clicked - entry not confirmed";
            delay(600);
            return;
        }

        // We have just crossed the lever. Freeze combat behavior and check the
        // public lair immediately, before any center movement can be started.
        Rs2Combat.setAutoRetaliate(false);
        initialLairCenteringPending = true;
        initialLairCenterTarget = null;
        kbdSeenAliveThisCycle = false;
        lairEnteredAt = System.currentTimeMillis();
        antifireMissingSince = 0L;
        occupiedLairHopPending = false;
        occupiedLairHopFailures = 0;
        lastOccupiedLairHopAttemptAt = 0L;
        status = "Entered KBD lair - checking world";

        if (hasOtherPlayerInLair())
        {
            occupiedLairHopPending = true;
            handleOccupiedLairHop();
            return;
        }
    }

    private WorldPoint chooseLeverStagingTarget()
    {
        int start = Rs2Random.between(0, KBD_LEVER_STAGING_VARIANTS.length);
        for (int offset = 0; offset < KBD_LEVER_STAGING_VARIANTS.length; offset++)
        {
            WorldPoint candidate = KBD_LEVER_STAGING_VARIANTS[
                    (start + offset) % KBD_LEVER_STAGING_VARIANTS.length];
            if (KBD_LEVER_INTERACTION_TILE.equals(candidate)
                    || Rs2Walker.isWalkableInCollisionMap(candidate))
            {
                return candidate;
            }
        }
        return KBD_LEVER_INTERACTION_TILE;
    }

    private boolean localTravelClick(WorldPoint target)
    {
        if (target == null) return false;
        boolean walked = Rs2Walker.walkFastCanvas(target);
        if (!walked) walked = Rs2Walker.walkMiniMap(target);
        return walked;
    }

    private void fightKbd()
    {
        pvpSurrender.reset();

        // Check occupancy before movement or new attacks.
        // If KBD has already engaged us, finish that fight rather than trying
        // to hop through combat. As soon as combat ends, the pending hop wins.
        boolean occupiedLair = hasOtherPlayerInLair();
        if (occupiedLair)
        {
            occupiedLairHopPending = true;

            if (!isActivelyFightingKbd())
            {
                handleOccupiedLairHop();
                return;
            }

            status = "Player in KBD lair - finishing current fight before hop";
        }
        else
        {
            occupiedLairHopPending = false;
            occupiedLairHopFailures = 0;
            lastOccupiedLairHopAttemptAt = 0L;
        }

        // Empty world, or an already-established KBD fight that must be finished.
        Rs2Combat.setAutoRetaliate(true);
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
        toggleBestOffensivePrayer(true);

        /*
         * Do the one-time center run immediately after confirming the lair is
         * empty. This MUST NOT depend on KBD already being present in the NPC
         * cache; otherwise the script can idle beside the lever waiting for KBD.
         */
        if (initialLairCenteringPending)
        {
            if (!moveToInitialLairCenter())
            {
                return;
            }
        }

        Rs2NpcModel kbd = Microbot.getRs2NpcCache().query()
                .withId(NpcID.KING_BLACK_DRAGON)
                .nearest();

        /*
         * Once the one-time center run is complete, a temporarily missing KBD
         * means wait at the center rather than looting or leaving.
         */
        if (kbd == null)
        {
            if (kbdSeenAliveThisCycle && kbdLootUntil == 0L)
            {
                kbdLootUntil = System.currentTimeMillis() + 12000L;
            }
            if (System.currentTimeMillis() < kbdLootUntil)
            {
                loot();
            }
            else
            {
                status = "Waiting for KBD at center";
                delay(Rs2Random.between(75, 250));
            }
            return;
        }

        if (!kbd.isDead() && kbd.getHealthRatio() > 0)
        {
            kbdSeenAliveThisCycle = true;
            kbdLootUntil = 0L;
        }

        // After the initial center run, only react if KBD gets inside the
        // requested 5-tile exclusion zone. Do not keep returning to center.
        if (selectedCombatMode() != DroKbdAmmo.MELEE
                && !kbd.isDead() && kbd.getHealthRatio() > 0 && !maintainKbdSpacing(kbd))
        {
            return;
        }
        if (Rs2Player.getHealthPercentage() <= config.eatAt() && Rs2Player.eatAt(config.eatAt()))
        {
            status = "Eating";
            delay(700);
            return;
        }
        if (Rs2Player.getBoostedSkillLevel(Skill.PRAYER) <= config.prayerAt() && restorePrayer())
        {
            status = "Restoring prayer";
            delay(700);
            return;
        }
        if (!hasRequiredKbdAntifireActive())
        {
            /*
             * Do not instantly teleport on one false-negative buff read after
             * the lever transition. First try to drink another dose if one is
             * actually available. If that fails, require the condition to stay
             * missing for several seconds before treating it as a real safety
             * failure. Blowpipe mode only accepts extended super antifire.
             */
            if (drinkKbdAntifire())
            {
                status = "Refreshing antifire";
                antifireMissingSince = 0L;
                delay(650 + Rs2Random.between(75, 250));
                return;
            }

            long now = System.currentTimeMillis();

            if (antifireMissingSince == 0L)
            {
                antifireMissingSince = now;
            }

            boolean justEnteredLair = lairEnteredAt > 0L
                    && now - lairEnteredAt < 8000L;
            boolean confirmedMissing = now - antifireMissingSince >= 5000L;

            if (justEnteredLair || !confirmedMissing)
            {
                status = "Confirming antifire protection";
                delay(Rs2Random.between(75, 250));
                return;
            }

            escape("Confirmed no antifire protection");
            return;
        }
        else
        {
            antifireMissingSince = 0L;
        }
        if (Rs2Player.hasAntiPoisonActive() && ready())
        {
            if (drinkBroughtAntipoison())
            {
                delay(650 + Rs2Random.between(75, 250));
                return;
            }
        }
        if (kbd.isDead() || kbd.getHealthRatio() <= 0)
        {
            combatMouseHandled = false;

            // Start looting immediately after an observed KBD kill.
            if (kbdSeenAliveThisCycle && kbdLootUntil == 0L)
            {
                kbdLootUntil = System.currentTimeMillis() + 12000L;
            }

            loot();
            return;
        }

        if (selectedCombatMode().isAmmunitionBootstrap()
                && Rs2Inventory.contains(selectedAmmoId())
                && !Rs2Equipment.isWearing(selectedAmmoId()))
        {
            status = "Re-equipping " + selectedAmmoName();
            Rs2Inventory.interact(selectedAmmoId(), "Wield");
            delay(600);
            return;
        }

        Skill combatBoostSkill = selectedCombatMode() == DroKbdAmmo.MELEE
                ? Skill.STRENGTH : Skill.RANGED;
        int currentCombatBoost = Rs2Player.getBoostedSkillLevel(combatBoostSkill);
        long now = System.currentTimeMillis();

        /*
         * Divine potions are strictly timer-driven: one successful divine sip
         * blocks another divine sip for five minutes. Normal potions retain the
         * existing four-level re-dose rule.
         */
        boolean divineAvailable = hasDivineCombatPotion();
        boolean divineCombatActive = now < divineCombatPotionUntil;
        boolean combatDoseDue = divineAvailable
                ? !divineCombatActive
                : (lastRangingPotionBoostLevel < 0
                || currentCombatBoost <= lastRangingPotionBoostLevel - 4);

        if (combatDoseDue)
        {
            if (divineAvailable)
            {
                // Choose a fresh safety floor for each divine dose and keep it
                // stable until that dose is actually consumed.
                if (divineSipMinHpPercent <= 0)
                {
                    divineSipMinHpPercent = Rs2Random.between(30, 46);
                }

                double hpPercent = Rs2Player.getHealthPercentage();
                if (hpPercent < divineSipMinHpPercent)
                {
                    int hpDisplay = (int) Math.floor(hpPercent);
                    status = "Eating before divine potion (" + hpDisplay + "% < "
                            + divineSipMinHpPercent + "%)";

                    if (Rs2Inventory.getInventoryFood().isEmpty())
                    {
                        escape("Not enough HP/food for divine potion");
                        return;
                    }

                    if (Rs2Player.eatAt(100))
                    {
                        delay(650 + Rs2Random.between(75, 200));
                    }
                    else
                    {
                        delay(Rs2Random.between(150, 300));
                    }
                    return;
                }
            }

            int beforeDrink = currentCombatBoost;

            if (drinkKbdCombatPotion())
            {
                status = lastCombatPotionWasDivine
                        ? (selectedCombatMode() == DroKbdAmmo.MELEE
                        ? "Drinking divine super combat potion"
                        : "Drinking divine ranging potion")
                        : (selectedCombatMode() == DroKbdAmmo.MELEE
                        ? "Drinking melee combat potion"
                        : "Drinking ranging potion");

                sleepUntil(() ->
                        Rs2Player.getBoostedSkillLevel(combatBoostSkill) > beforeDrink, 2500);

                int afterDrink = Rs2Player.getBoostedSkillLevel(combatBoostSkill);
                lastRangingPotionBoostLevel = Math.max(afterDrink, beforeDrink);
                divineSipMinHpPercent = 0;

                delay(700);
                return;
            }
        }
        if (Rs2Inventory.getInventoryFood().isEmpty() && Rs2Player.getHealthPercentage() < 70)
        {
            escape("Out of food");
            return;
        }

        // Arm supported ranged specials whenever enough energy is available.
        // No weapon swapping: this only applies to the weapon already equipped.
        if (tryEnableSupportedSpecialAttack())
        {
            return;
        }

        state = DroKbdState.FIGHTING;
        status = "Fighting King Black Dragon";
        if (Rs2Combat.inCombat() && !combatMouseHandled)
        {
            combatMouseHandled = true;
            if (Math.random() < 0.38) Rs2Antiban.moveMouseOffScreen();
        }
        if (!Rs2Combat.inCombat() && ready())
        {
            kbd.click("Attack");
            delay(900);
        }
    }

    /**
     * One-time entry movement after an empty KBD world is confirmed.
     * Chooses a small randomized tile around the configured center anchor and
     * runs there even when KBD has not loaded into the NPC cache yet.
     *
     * @return true once the initial center movement is complete.
     */
    private boolean moveToInitialLairCenter()
    {
        WorldPoint here = location();
        if (here == null) return false;

        if (initialLairCenterTarget == null)
        {
            // Small randomized center area rather than one repeated exact tile.
            int offsetX = Rs2Random.between(-4, 4);
            int offsetY = Rs2Random.between(-4, 4);

            WorldPoint candidate = new WorldPoint(
                    KBD_LAIR_CENTER.getX() + offsetX,
                    KBD_LAIR_CENTER.getY() + offsetY,
                    KBD_LAIR_CENTER.getPlane());

            initialLairCenterTarget = KBD_LAIR.contains(candidate)
                    ? candidate
                    : KBD_LAIR_CENTER;
        }

        if (here.distanceTo(initialLairCenterTarget) <= 2)
        {
            initialLairCenteringPending = false;
            initialLairCenterTarget = null;
            status = "KBD center position reached";
            delay(Rs2Random.between(75, 250));
            return true;
        }

        if (!ready()) return false;

        status = "Running to KBD center";

        /*
         * This is a short movement inside one room, so prefer a local click
         * and avoid invoking transport/path logic unnecessarily.
         */
        boolean walked = Rs2Walker.walkMiniMap(initialLairCenterTarget);
        if (!walked)
        {
            walked = Rs2Walker.walkFastCanvas(initialLairCenterTarget);
        }

        if (!walked)
        {
            // Fallback only if the local click methods cannot place the target.
            Rs2Walker.walkTo(initialLairCenterTarget, 2);
        }

        delay(450 + Rs2Random.between(75, 250));
        return false;
    }

    private boolean hasOtherPlayerInLair()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Client client = Microbot.getClient();
            Player local = client == null ? null : client.getLocalPlayer();
            if (local == null) return false;

            WorldPoint localLocation = local.getWorldLocation();
            if (localLocation == null || !inKbdLair(localLocation)) return false;

            /*
             * In the KBD room, any other loaded player is enough to reject the
             * world. The previous KBD_LAIR.contains(otherPlayerLocation) check
             * was too strict and could miss a visible player because of the
             * scene/world-coordinate representation used after the lever.
             */
            for (Player player : client.getPlayers())
            {
                if (player == null || player == local || player.getName() == null) continue;

                WorldPoint other = player.getWorldLocation();
                if (other != null && other.getPlane() == localLocation.getPlane())
                {
                    return true;
                }
            }

            return false;
        }).orElse(false);
    }

    private boolean isActivelyFightingKbd()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Client client = Microbot.getClient();
            Player local = client == null ? null : client.getLocalPlayer();
            if (local == null || !(local.getInteracting() instanceof NPC)) return false;

            NPC npc = (NPC) local.getInteracting();
            return npc.getId() == NpcID.KING_BLACK_DRAGON && !npc.isDead();
        }).orElse(false);
    }

    /**
     * Occupied-lair policy:
     *  1) hop immediately when free to do so;
     *  2) if KBD already has us in combat, finish that fight and retry;
     *  3) after two failed hop attempts while the player is still present,
     *     teleport to Ferox, hop there, and repeat the trip.
     */
    private void handleOccupiedLairHop()
    {
        occupiedLairHopPending = true;

        if (isActivelyFightingKbd())
        {
            status = "Player in KBD lair - finishing current fight before hop";
            return;
        }

        Rs2Combat.setAutoRetaliate(false);
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, false);
        toggleBestOffensivePrayer(false);

        long now = System.currentTimeMillis();
        if (now - lastOccupiedLairHopAttemptAt < 650L)
        {
            status = "Player in KBD lair - waiting to retry hop";
            return;
        }
        lastOccupiedLairHopAttemptAt = now;

        int currentWorld = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient() == null ? -1 : Microbot.getClient().getWorld()).orElse(-1);

        int targetWorld = chooseDifferentWorld(currentWorld);
        if (targetWorld <= 0)
        {
            registerOccupiedLairHopFailure("Could not select another world");
            return;
        }

        status = "Player in KBD lair - hopping";

        boolean hopStarted = Microbot.hopToWorld(targetWorld);
        boolean worldChanged = hopStarted && sleepUntil(() ->
                Microbot.getClientThread().runOnClientThreadOptional(
                        () -> Microbot.getClient() != null
                                && Microbot.getClient().getWorld() != currentWorld).orElse(false), 10000);

        if (worldChanged)
        {
            occupiedLairHopPending = false;
            occupiedLairHopFailures = 0;
            lastOccupiedLairHopAttemptAt = 0L;
            initialLairCenteringPending = true;
            initialLairCenterTarget = null;
            kbdSeenAliveThisCycle = false;
            kbdLootUntil = 0L;
            lairEnteredAt = System.currentTimeMillis();
            antifireMissingSince = 0L;
            status = "Hopped from occupied KBD lair - checking new world";
            delay(500 + Rs2Random.between(75, 250));
            return;
        }

        registerOccupiedLairHopFailure(hopStarted
                ? "World hop did not complete"
                : "World hop was blocked");
    }

    private void registerOccupiedLairHopFailure(String reason)
    {
        occupiedLairHopFailures++;

        if (occupiedLairHopFailures >= 2 && hasOtherPlayerInLair())
        {
            status = "Second occupied-lair hop failed - teleporting out";
            hopAfterOccupiedEscape = true;

            // Escape must win immediately over any small hop retry delay.
            nextActionAt = 0L;
            escape("Occupied KBD lair after two failed hops");
            return;
        }

        // A hop can fail because KBD tagged us just before the hop request.
        // Re-enable combat protection/retaliation so that combat lock can resolve,
        // then the occupied-lair check will retry the hop immediately afterward.
        Rs2Combat.setAutoRetaliate(true);
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
        toggleBestOffensivePrayer(true);
        status = reason + " - finishing combat if locked, then retrying";
        delay(550 + Rs2Random.between(75, 225));
    }

    private int chooseDifferentWorld(int currentWorld)
    {
        for (int attempt = 0; attempt < 8; attempt++)
        {
            int candidate = LoginManager.getRandomWorld(Rs2Player.isMember());
            if (candidate > 0 && candidate != currentWorld)
            {
                return candidate;
            }
        }
        return -1;
    }

    private void hopOutsideLairAfterOccupiedEscape()
    {
        state = DroKbdState.ESCAPING;

        if (!ready()) return;

        int currentWorld = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient() == null ? -1 : Microbot.getClient().getWorld()).orElse(-1);
        int targetWorld = chooseDifferentWorld(currentWorld);

        if (targetWorld <= 0)
        {
            status = "Selecting world after occupied-lair escape";
            delay(750);
            return;
        }

        status = "Hopping after occupied-lair escape";

        if (!Microbot.hopToWorld(targetWorld))
        {
            status = "Outside hop blocked - retrying";
            delay(750);
            return;
        }

        boolean changed = sleepUntil(() ->
                Microbot.getClientThread().runOnClientThreadOptional(
                        () -> Microbot.getClient() != null
                                && Microbot.getClient().getWorld() != currentWorld).orElse(false), 10000);

        if (!changed)
        {
            status = "Outside hop not confirmed - retrying";
            delay(750);
            return;
        }

        hopAfterOccupiedEscape = false;
        occupiedLairHopPending = false;
        occupiedLairHopFailures = 0;
        lastOccupiedLairHopAttemptAt = 0L;
        status = "World changed - repeating KBD trip";
        delay(500 + Rs2Random.between(75, 250));
    }

    /**
     * Keeps the player on a central ring around KBD. A radius of 6+ means
     * the script never intentionally occupies a tile within 5 tiles of KBD.
     *
     * @return true when already safely positioned; false when a move was issued.
     */
    private boolean maintainKbdSpacing(Rs2NpcModel kbd)
    {
        WorldPoint here = location();
        WorldPoint kbdLocation = kbd == null ? null : kbd.getWorldLocation();
        if (here == null || kbdLocation == null) return true;

        boolean tooClose = here.distanceTo(kbdLocation) < KBD_MIN_DISTANCE;
        if (!tooClose) return true;
        if (!ready()) return false;

        WorldPoint target = safestKbdTile(here, kbdLocation, false);
        if (target == null) return true;

        status = "Creating distance from KBD";

        if (!Rs2Walker.walkMiniMap(target))
        {
            Rs2Walker.walkFastCanvas(target);
        }

        delay(450 + Rs2Random.between(75, 250));
        return false;
    }

    private WorldPoint safestKbdTile(
            WorldPoint here,
            WorldPoint kbdLocation,
            boolean favorCenter)
    {
        if (here == null || kbdLocation == null) return null;

        WorldPoint best = null;
        int bestScore = Integer.MAX_VALUE;

        // Candidate positions outside the 5-tile exclusion zone.
        int[][] offsets = {
                { KBD_POSITION_RADIUS, 0 },
                {-KBD_POSITION_RADIUS, 0 },
                {0,  KBD_POSITION_RADIUS},
                {0, -KBD_POSITION_RADIUS},
                { KBD_POSITION_RADIUS,  KBD_POSITION_RADIUS},
                { KBD_POSITION_RADIUS, -KBD_POSITION_RADIUS},
                {-KBD_POSITION_RADIUS,  KBD_POSITION_RADIUS},
                {-KBD_POSITION_RADIUS, -KBD_POSITION_RADIUS}
        };

        for (int[] offset : offsets)
        {
            WorldPoint candidate = new WorldPoint(
                    kbdLocation.getX() + offset[0],
                    kbdLocation.getY() + offset[1],
                    kbdLocation.getPlane());

            if (!KBD_LAIR.contains(candidate)) continue;
            if (candidate.distanceTo(kbdLocation) < KBD_MIN_DISTANCE) continue;

            /*
             * First entry: favor the lair center.
             * Later spacing corrections: favor the nearest safe tile so there
             * is no repeated "run back to center" behavior.
             */
            int score = favorCenter
                    ? candidate.distanceTo(KBD_LAIR_CENTER)
                    : candidate.distanceTo(here);

            if (score < bestScore)
            {
                best = candidate;
                bestScore = score;
            }
        }

        return best;
    }

    private void loot()
    {
        state = DroKbdState.LOOTING;
        status = "Looting KBD";
        long now = System.currentTimeMillis();
        if (now - lastLootAt < 250) return;
        int minimumLootValue = Math.max(5000, config.minimumLootValue());

        Rs2TileItemModel loot = Microbot.getRs2TileItemCache().query()
                .fromWorldView()
                .within(10)
                .where(Rs2TileItemModel::isLootAble)
                .where(item -> "Dragon bones".equalsIgnoreCase(item.getName())
                        || "Black dragonhide".equalsIgnoreCase(item.getName())
                        || item.getTotalValue() >= minimumLootValue)
                .toList()
                .stream()
                .max(Comparator.comparingLong(Rs2TileItemModel::getTotalValue))
                .orElse(null);
        if (loot != null)
        {
            if (Rs2Inventory.isFull())
            {
                /*
                 * Inventory fullness is NOT an escape condition.
                 *
                 * For now, if a manta ray is available, drop one to make room.
                 * If there is no disposable food slot, simply leave the drop
                 * on the ground and continue fighting rather than teleporting.
                 * Higher-value replacement logic can be added separately.
                 */
                if (Rs2Inventory.contains(ItemID.MANTA_RAY))
                {
                    status = "Making room for KBD loot";
                    Rs2Inventory.drop(ItemID.MANTA_RAY);
                    lastLootAt = now;
                    delay(500);
                    return;
                }

                status = "Inventory full - leaving KBD loot";
                lastLootAt = now;
                return;
            }

            if (loot.pickup())
            {
                totalLootValue += Math.max(0, loot.getTotalValue());
                status = "Collecting KBD loot";
            }
        }
        lastLootAt = now;

        // Drops can appear a few ticks after the death animation. During the
        // post-kill window, keep checking immediately instead of waiting for respawn.
        if (loot == null)
        {
            if (now < kbdLootUntil)
            {
                status = "Waiting for KBD drop";
                return;
            }

            kbdSeenAliveThisCycle = false;
            kbdLootUntil = 0L;
        }
    }

    /**
     * Replenishes an equipped bolt stack to the exact quantity saved in the
     * selected Inventory Setup. It never invents a target quantity.
     *
     * Example: setup says 40 Ruby bolts (e), 27 remain equipped -> withdraw 13
     * and equip them, ending at the user's saved 40.
     */
    /**
     * Inventory Setup ammo bootstrap. This happens before Rs2InventorySetup
     * is allowed to perform the normal regear.
     *
     * Order:
     * bank -> Ruby [9242] -> equip Ruby -> regear.
     */
    private boolean prepareAmmoBeforeInventorySetup(InventorySetup setup)
    {
        if (setup == null) return false;

        // Blowpipe and melee do not use an equipment-slot bolt stack.
        if (!selectedCombatMode().isAmmunitionBootstrap())
        {
            ammoPrepStage = 6;
            ammoRubyTarget = 0;
            return true;
        }

        int ammoId = selectedAmmoId();
        String ammoName = selectedAmmoName();

        if (ammoRubyTarget <= 0)
        {
            ammoRubyTarget = setupQuantity(setup.getEquipment(), ammoId);

            if (ammoRubyTarget <= 0)
            {
                status = "Inventory Setup must contain " + ammoName + " in equipment";
                return false;
            }
        }

        switch (ammoPrepStage)
        {
            case 0:
                if (!Rs2Bank.isOpen())
                {
                    status = "Opening bank for " + ammoName;
                    Rs2Bank.openBank();
                    delay(700 + Rs2Random.between(75, 250));
                    return false;
                }
                ammoPrepStage = 1;
                return false;

            case 1:
            {
                Rs2ItemModel equippedAmmo = Rs2Equipment.get(ammoId);
                int equipped = equippedAmmo == null ? 0 : equippedAmmo.getQuantity();
                int inventory = Rs2Inventory.count(ammoId);
                int missing = Math.max(0, ammoRubyTarget - equipped - inventory);

                if (missing > 0)
                {
                    if (!Rs2Bank.hasItem(ammoId))
                    {
                        status = "Missing " + ammoName;
                        return false;
                    }

                    status = "Withdrawing " + ammoName;
                    Rs2Bank.withdrawX(ammoId, missing);
                    ammoPrepStage = 2;
                    delay(650 + Rs2Random.between(75, 250));
                    return false;
                }

                ammoPrepStage = 2;
                return false;
            }

            case 2:
                if (Rs2Bank.isOpen())
                {
                    status = "Closing bank to equip " + ammoName;
                    Rs2Bank.closeBank();
                    ammoPrepStage = 3;
                    delay(500 + Rs2Random.between(75, 250));
                    return false;
                }

                ammoPrepStage = 3;
                return false;

            case 3:
            {
                Rs2ItemModel equippedAmmo = Rs2Equipment.get(ammoId);
                int equipped = equippedAmmo == null ? 0 : equippedAmmo.getQuantity();

                if (Rs2Inventory.contains(ammoId))
                {
                    status = "Equipping " + ammoName;
                    if (Rs2Inventory.interact(ammoId, "Wield"))
                    {
                        ammoEquipIssuedAt = System.currentTimeMillis();
                        ammoPrepStage = 4;
                    }
                    delay(650 + Rs2Random.between(75, 250));
                    return false;
                }

                if (equipped >= ammoRubyTarget)
                {
                    ammoPrepStage = 5;
                    return false;
                }

                status = "Waiting for " + ammoName;
                delay(Rs2Random.between(75, 250));
                return false;
            }

            case 4:
            {
                Rs2ItemModel equippedAmmo = Rs2Equipment.get(ammoId);
                int equipped = equippedAmmo == null ? 0 : equippedAmmo.getQuantity();

                if (equipped >= ammoRubyTarget)
                {
                    ammoPrepStage = 5;
                    return false;
                }

                if (System.currentTimeMillis() - ammoEquipIssuedAt >= 1200L
                        && Rs2Inventory.contains(ammoId))
                {
                    status = "Retrying " + ammoName + " equip";
                    Rs2Inventory.interact(ammoId, "Wield");
                    ammoEquipIssuedAt = System.currentTimeMillis();
                    delay(650 + Rs2Random.between(75, 250));
                    return false;
                }

                status = "Confirming " + ammoName + " equipped";
                delay(Rs2Random.between(75, 250));
                return false;
            }

            case 5:
                if (!Rs2Bank.isOpen())
                {
                    status = "Reopening bank for Inventory Setup";
                    Rs2Bank.openBank();
                    ammoPrepStage = 6;
                    delay(650 + Rs2Random.between(75, 250));
                    return false;
                }

                ammoPrepStage = 6;
                return false;

            default:
                return true;
        }
    }

    private int setupQuantity(List<InventorySetupsItem> items, int itemId)
    {
        if (items == null) return 0;

        for (InventorySetupsItem item : items)
        {
            if (item != null && item.getId() == itemId)
            {
                return Math.max(0, item.getQuantity());
            }
        }

        return 0;
    }

    /**
     * Inventory Setup quantities are authoritative.
     * Do not rewrite ammunition counts saved by the user (for example 40 bolts).
     */
    private InventorySetup sanitizeTripSetup(InventorySetup source)
    {
        return new InventorySetup(
                copySetupItems(source.getInventory()),
                copySetupItems(source.getEquipment()),
                copySetupItems(source.getRune_pouch()),
                copySetupItems(source.getBoltPouch()),
                copySetupItems(source.getQuiver()),
                source.getAdditionalFilteredItems(),
                source.getName(),
                source.getNotes(),
                source.getHighlightColor(),
                source.isHighlightDifference(),
                source.getDisplayColor(),
                source.isFilterBank(),
                source.isUnorderedHighlight(),
                source.getSpellBook(),
                source.isFavorite(),
                source.getIconID());
    }

    private static List<InventorySetupsItem> copySetupItems(List<InventorySetupsItem> source)
    {
        if (source == null) return null;
        List<InventorySetupsItem> copy = new ArrayList<>(source.size());
        for (InventorySetupsItem item : source)
        {
            copy.add(item == null ? null : new InventorySetupsItem(
                    item.getId(), item.getName(), item.getQuantity(), item.isFuzzy(),
                    item.getStackCompare(), item.isLocked(), item.getSlot()));
        }
        return copy;
    }

    private void escape(String reason)
    {
        state = DroKbdState.ESCAPING;
        status = reason + " - teleporting";
        Microbot.log("DroKBD escape: " + reason);
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, false);
        toggleBestOffensivePrayer(false);
        if (!ready()) return;
        if (Rs2Inventory.interact(RINGS_OF_DUELING, "Ferox Enclave")
                || Rs2Equipment.interact(RINGS_OF_DUELING, "Ferox Enclave"))
        {
            bankCleared = false;
            inventorySetup = null;
            inventorySetupReadyForDeparture = false;
            feroxRestored = false;
            initialLairCenteringPending = true;
            initialLairCenterTarget = null;
            lastRangingPotionBoostLevel = -1;
            divineCombatPotionUntil = 0L;
            lastCombatPotionWasDivine = false;
            divineSipMinHpPercent = 0;
            occupiedLairHopPending = false;
            lastOccupiedLairHopAttemptAt = 0L;
            resetCurrentWildernessRoute();
            ammoPrepStage = 0;
            ammoRubyTarget = 0;
            ammoEquipIssuedAt = 0L;
            kbdSeenAliveThisCycle = false;
            lairEnteredAt = 0L;
            antifireMissingSince = 0L;
            feroxBankReadyAt = 0L;
            sleepUntil(() -> !inKbdLair(location()), 8000);
        }
        else
        {
            status = "Missing charged Ring of dueling for Ferox";
        }
        delay(1000);
    }

    private void watchWilderness()
    {
        try
        {
            if (stopping) return;
            if (!Microbot.isLoggedIn())
            {
                threatLogoutActive.set(false);
                pvpSurrender.reset();
                return;
            }
            boolean inWild = wilderness(location());
            if (updatePvpSurrender(inWild) || !inWild) return;
            if (hasNearbyThreat()) requestThreatLogout();
        }
        catch (Exception ignored)
        {
        }
    }

    private void requestThreatLogout()
    {
        if (!threatLogoutActive.compareAndSet(false, true)) return;
        try
        {
            status = "Threat detected - logging out";
            Rs2Player.logout();
            sleep(500, 800);
        }
        finally
        {
            if (Microbot.isLoggedIn()) threatLogoutActive.set(false);
        }
    }

    private boolean updatePvpSurrender(boolean inWild)
    {
        if (stopping) return false;
        if (!inWild)
        {
            pvpSurrender.reset();
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Client client = Microbot.getClient();
            Player local = client == null ? null : client.getLocalPlayer();
            if (local == null) return false;
            // Interaction alone includes following and trading. Require an animation
            // as well; each fresh combat action renews the bounded surrender window.
            boolean attacker = Microbot.getRs2PlayerCache().getStream()
                    .map(model -> model.getPlayer())
                    .anyMatch(player -> player != null && player != local
                            && DroKbdPvpSurrender.isAttackAction(player.getInteracting() == local, player.getAnimation()));
            return pvpSurrender.observe(true, attacker, client.getTickCount());
        }).orElse(pvpSurrender.isActive());
    }

    private boolean hasNearbyThreat()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Client client = Microbot.getClient();
            Player local = client == null ? null : client.getLocalPlayer();
            if (local == null) return false;
            WorldPoint here = local.getWorldLocation();
            int wildLevel = Rs2Pvp.getWildernessLevelFrom(here);
            if (wildLevel <= 0) return false;
            int range = wildLevel + (WorldType.isPvpWorld(client.getWorldType()) ? 15 : 0);
            int minimum = WorldType.isDeadmanWorld(client.getWorldType()) ? 3 : Math.max(3, local.getCombatLevel() - range);
            int maximum = WorldType.isDeadmanWorld(client.getWorldType()) ? 126 : Math.min(126, local.getCombatLevel() + range + config.upperCombatBuffer());
            for (Player player : client.getPlayers())
            {
                if (player == null || player == local || player.getName() == null) continue;
                int level = player.getCombatLevel();
                if (level >= minimum && level <= maximum && here.distanceTo(player.getWorldLocation()) <= config.threatRadius()) return true;
            }
            return false;
        }).orElse(false);
    }

    private boolean hasBurningAmulet()
    {
        return Rs2Inventory.contains(false, "burning amulet") || Rs2Equipment.isWearing("burning amulet", false);
    }

    private boolean hasAction(Rs2TileObjectModel object, String action)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var composition = object.getObjectComposition();
            return composition != null && composition.getActions() != null
                    && Arrays.stream(composition.getActions()).anyMatch(value -> action.equalsIgnoreCase(value));
        }).orElse(false);
    }

    private boolean hasDragonfireProtection()
    {
        return Rs2Player.hasAntiFireActive()
                || Rs2Inventory.contains(false, "antifire", "dragonfire shield", "anti-dragon shield")
                || Rs2Equipment.isWearing("dragonfire shield", false)
                || Rs2Equipment.isWearing("anti-dragon shield", false);
    }

    private void configureAdaptiveProfile()
    {
        if (antibanSnapshot != null || Rs2AntibanSettings.overwriteScriptSettings) return;
        antibanSnapshot = DroKbdAntibanSnapshot.capture();
        if (antibanSnapshot == null)
        {
            Microbot.log("[Dro] KBD: Antiban snapshot unavailable; keeping the existing global profile.");
            return;
        }
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCombatSetup();
        Rs2Antiban.setActivity(Activity.GENERAL_COMBAT);
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.microBreakChance = 0.0;
        Rs2AntibanSettings.actionCooldownChance = 0.15;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.36;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.moveMouseOffScreenChance = 0.38;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2Antiban.setActivityIntensity(ActivityIntensity.HIGH);
    }

    private DroKbdAmmo selectedCombatMode()
    {
        return config == null || config.ammunition() == null
                ? DroKbdAmmo.RUBY_BOLTS_E : config.ammunition();
    }

    private int selectedAmmoId()
    {
        return selectedCombatMode().getItemId();
    }

    private String selectedAmmoName()
    {
        return selectedCombatMode().getDisplayName();
    }

    private boolean hasRequiredKbdAntifireActive()
    {
        return selectedCombatMode() == DroKbdAmmo.TOXIC_BLOWPIPE
                ? Rs2Player.hasSuperAntiFireActive()
                : Rs2Player.hasAntiFireActive();
    }

    private boolean hasExtendedSuperAntifireDose()
    {
        return Arrays.stream(EXTENDED_SUPER_ANTIFIRE_DOSES).anyMatch(Rs2Inventory::contains);
    }

    private boolean drinkPotionById(int[] doses)
    {
        Rs2ItemModel potion = Rs2Inventory.get(item -> item != null && !item.isNoted()
                && Arrays.stream(doses).anyMatch(id -> id == item.getId()));
        return potion != null && Rs2Inventory.interact(potion, "Drink");
    }

    private boolean drinkBroughtAntipoison()
    {
        // Prefer Superantipoison when both types are present because its protection
        // lasts longer; otherwise use any Antipoison dose the user brought.
        if (Arrays.stream(SUPERANTIPOISON_DOSES).anyMatch(Rs2Inventory::contains))
        {
            if (drinkPotionById(SUPERANTIPOISON_DOSES))
            {
                status = "Drinking Superantipoison";
                return true;
            }
        }

        if (Arrays.stream(ANTIPOISON_DOSES).anyMatch(Rs2Inventory::contains))
        {
            if (drinkPotionById(ANTIPOISON_DOSES))
            {
                status = "Drinking Antipoison";
                return true;
            }
        }

        return false;
    }

    private boolean drinkKbdAntifire()
    {
        if (selectedCombatMode() == DroKbdAmmo.TOXIC_BLOWPIPE)
        {
            if (Rs2Player.hasSuperAntiFireActive()) return true;
            return drinkPotionById(EXTENDED_SUPER_ANTIFIRE_DOSES);
        }

        if (Rs2Player.hasAntiFireActive()) return true;
        if (drinkPotionById(EXTENDED_SUPER_ANTIFIRE_DOSES)) return true;
        return Rs2Player.drinkAntiFirePotion();
    }

    private boolean hasDivineCombatPotion()
    {
        int[] divineDoses = selectedCombatMode() == DroKbdAmmo.MELEE
                ? DIVINE_SUPER_COMBAT_DOSES
                : DIVINE_RANGING_DOSES;

        return Arrays.stream(divineDoses).anyMatch(Rs2Inventory::contains);
    }

    private boolean drinkKbdCombatPotion()
    {
        lastCombatPotionWasDivine = false;

        int[] divineDoses = selectedCombatMode() == DroKbdAmmo.MELEE
                ? DIVINE_SUPER_COMBAT_DOSES
                : DIVINE_RANGING_DOSES;

        if (Arrays.stream(divineDoses).anyMatch(Rs2Inventory::contains))
        {
            if (drinkPotionById(divineDoses))
            {
                lastCombatPotionWasDivine = true;
                divineCombatPotionUntil = System.currentTimeMillis() + DIVINE_COMBAT_DURATION_MS;
                return true;
            }
            return false;
        }

        divineCombatPotionUntil = 0L;

        if (selectedCombatMode() == DroKbdAmmo.MELEE)
        {
            return drinkPotionById(SUPER_COMBAT_DOSES)
                    || Rs2Player.drinkCombatPotionAt(Skill.STRENGTH);
        }

        return Rs2Player.drinkCombatPotionAt(Skill.RANGED);
    }

    private int supportedSpecialAttackCost()
    {
        if (Rs2Equipment.isWearing("Zaryte crossbow", false))
        {
            return ZARYTE_CROSSBOW_SPEC_COST;
        }
        if (Rs2Equipment.isWearing("Dragon crossbow", false))
        {
            return DRAGON_CROSSBOW_SPEC_COST;
        }
        if (Rs2Equipment.isWearing("Armadyl crossbow", false))
        {
            return ARMADYL_CROSSBOW_SPEC_COST;
        }
        if (Rs2Equipment.isWearing("Toxic blowpipe", false))
        {
            return TOXIC_BLOWPIPE_SPEC_COST;
        }
        return -1;
    }

    private String supportedSpecialAttackWeapon()
    {
        if (Rs2Equipment.isWearing("Zaryte crossbow", false)) return "Zaryte crossbow";
        if (Rs2Equipment.isWearing("Dragon crossbow", false)) return "Dragon crossbow";
        if (Rs2Equipment.isWearing("Armadyl crossbow", false)) return "Armadyl crossbow";
        if (Rs2Equipment.isWearing("Toxic blowpipe", false)) return "Toxic blowpipe";
        return null;
    }

    private boolean tryEnableSupportedSpecialAttack()
    {
        if (selectedCombatMode() == DroKbdAmmo.MELEE) return false;

        int cost = supportedSpecialAttackCost();
        if (cost <= 0) return false;

        // If already armed, do not block the fight loop; the next attack consumes it.
        if (Rs2Combat.getSpecState()) return false;
        if (Rs2Combat.getSpecEnergy() < cost) return false;

        if (Rs2Combat.setSpecState(true, cost))
        {
            String weapon = supportedSpecialAttackWeapon();
            status = "Arming " + (weapon == null ? "ranged" : weapon) + " special attack";
            delay(125 + Rs2Random.between(75, 175));
            return true;
        }

        return false;
    }

    private void toggleBestOffensivePrayer(boolean enabled)
    {
        if (enabled)
        {
            Rs2PrayerEnum best = selectedCombatMode() == DroKbdAmmo.MELEE
                    ? Rs2Prayer.getBestMeleePrayer()
                    : Rs2Prayer.getBestRangePrayer();
            if (best != null) Rs2Prayer.toggle(best, true);
            return;
        }
        Rs2Prayer.toggle(Rs2PrayerEnum.RIGOUR, false);
        Rs2Prayer.toggle(Rs2PrayerEnum.DEAD_EYE, false);
        Rs2Prayer.toggle(Rs2PrayerEnum.EAGLE_EYE, false);
        Rs2Prayer.toggle(Rs2PrayerEnum.HAWK_EYE, false);
        Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, false);
        Rs2Prayer.toggle(Rs2PrayerEnum.CHIVALRY, false);
        Rs2Prayer.toggle(Rs2PrayerEnum.ULTIMATE_STRENGTH, false);
    }

    private boolean hasExtendedAntifireDose()
    {
        return Rs2Inventory.contains(ItemID.EXTENDED_ANTIFIRE4)
                || Rs2Inventory.contains(ItemID.EXTENDED_ANTIFIRE3)
                || Rs2Inventory.contains(ItemID.EXTENDED_ANTIFIRE2)
                || Rs2Inventory.contains(ItemID.EXTENDED_ANTIFIRE1)
                || hasExtendedSuperAntifireDose();
    }

    private boolean inKbdLair(WorldPoint point)
    {
        return point != null && (KBD_LAIR.contains(point)
                || Microbot.getRs2NpcCache().query().withId(NpcID.KING_BLACK_DRAGON).first() != null);
    }

    private boolean isDeathRecoveryArea(WorldPoint point)
    {
        return point != null && (LUMBRIDGE.contains(point) || LUMBRIDGE_MIDDLE.contains(point)
                || (point.getPlane() == 2 && point.getX() >= 3190 && point.getX() < 3240
                && point.getY() >= 3190 && point.getY() < 3245)
                || EDGEVILLE.contains(point));
    }

    private void resetTripAfterDeath()
    {
        deathRecoveryPending = false;
        pvpSurrender.reset();
        bankCleared = false;
        inventorySetup = null;
        inventorySetupReadyForDeparture = false;
        feroxRestored = false;
        initialLairCenteringPending = true;
        initialLairCenterTarget = null;
        lastRangingPotionBoostLevel = -1;
        divineCombatPotionUntil = 0L;
        lastCombatPotionWasDivine = false;
        divineSipMinHpPercent = 0;
        occupiedLairHopPending = false;
        occupiedLairHopFailures = 0;
        lastOccupiedLairHopAttemptAt = 0L;
        hopAfterOccupiedEscape = false;
        resetCurrentWildernessRoute();
        kbdSeenAliveThisCycle = false;
        kbdLootUntil = 0L;
        lairEnteredAt = 0L;
        antifireMissingSince = 0L;
        feroxBankReadyAt = 0L;
    }

    private void walkUpstairsLumbridge(WorldPoint location)
    {
        int plane = location.getPlane();
        if (plane >= 2)
        {
            useNorthLumbridgeRoute = null;
            return;
        }
        if (useNorthLumbridgeRoute == null)
        {
            int southDistance = location.distanceTo(onPlane(SOUTH_LUMBRIDGE_STAIR, plane));
            int northDistance = location.distanceTo(onPlane(NORTH_LUMBRIDGE_STAIR, plane));
            useNorthLumbridgeRoute = Math.abs(northDistance - southDistance) <= 2
                    ? Math.random() < 0.5 : northDistance < southDistance;
        }
        WorldPoint stairPoint = onPlane(
                useNorthLumbridgeRoute ? NORTH_LUMBRIDGE_STAIR : SOUTH_LUMBRIDGE_STAIR, plane);
        int staircaseId = plane == 0 ? 56230 : 16672;
        String climbAction = plane == 0 ? "Top-floor" : "Climb-up";
        Rs2TileObjectModel staircase = Microbot.getRs2TileObjectCache().query()
                .withId(staircaseId)
                .where(object -> object.getWorldLocation() != null
                        && object.getWorldLocation().getPlane() == plane
                        && object.getWorldLocation().distanceTo(stairPoint) <= 2)
                .nearest();
        status = "Walking upstairs to Lumbridge bank";
        if (staircase != null && location.distanceTo(stairPoint) <= 6)
        {
            staircase.click(climbAction);
            sleepUntil(() -> {
                WorldPoint current = location();
                return current != null && current.getPlane() > plane;
            }, 5000);
            delay(700);
            return;
        }
        WorldPoint[] route = useNorthLumbridgeRoute ? NORTH_LUMBRIDGE_ROUTE : SOUTH_LUMBRIDGE_ROUTE;
        WorldPoint target = stairPoint;
        for (WorldPoint waypoint : route)
        {
            WorldPoint candidate = onPlane(waypoint, plane);
            if (location.distanceTo(candidate) > 2
                    && candidate.distanceTo(stairPoint) < location.distanceTo(stairPoint))
            {
                target = candidate;
                break;
            }
        }
        Rs2Walker.walkTo(target, 1);
        delay(900);
    }

    private static WorldPoint onPlane(WorldPoint point, int plane)
    {
        return new WorldPoint(point.getX(), point.getY(), plane);
    }

    private boolean wilderness(WorldPoint point)
    {
        return point != null && (Rs2Pvp.isInWilderness() || Rs2Pvp.getWildernessLevelFrom(point) > 0);
    }

    private WorldPoint location()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient() == null ? null : Microbot.getClient().getLocalPlayer();
            return player == null ? null : player.getWorldLocation();
        }).orElse(null);
    }

    private boolean ready()
    {
        return System.currentTimeMillis() >= nextActionAt;
    }

    private void delay(long milliseconds)
    {
        nextActionAt = System.currentTimeMillis() + milliseconds;
    }

    public void onChatMessage(String message)
    {
        String lower = message == null ? "" : message.toLowerCase();
        if (lower.contains("your king black dragon kill count is") || lower.contains("you have defeated the king black dragon")) kills++;
        if (lower.contains("oh dear, you are dead")) pvpSurrender.reset();
    }

    public long getSessionElapsedMs()
    {
        return sessionStartedAt <= 0L ? 0L : Math.max(0L, System.currentTimeMillis() - sessionStartedAt);
    }

    public int getRangedXpGained()
    {
        int current = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getSkillExperience(Skill.RANGED)).orElse(startingRangedXp);
        return Math.max(0, current - startingRangedXp);
    }

    public long getRangedXpPerHour()
    {
        long elapsed = getSessionElapsedMs();
        return elapsed == 0L ? 0L : getRangedXpGained() * 3_600_000L / elapsed;
    }

    public long getLootValuePerHour()
    {
        long elapsed = getSessionElapsedMs();
        return elapsed == 0L ? 0L : totalLootValue * 3_600_000L / elapsed;
    }

    @Override
    public void shutdown()
    {
        stopping = true;
        // Cancel both loops before prayer helpers, which may wait for game state.
        if (scheduledFuture != null) scheduledFuture.cancel(true);
        super.shutdown();
        threatLogoutActive.set(false);
        pvpSurrender.reset();
        try
        {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, false);
            toggleBestOffensivePrayer(false);
        }
        finally
        {
            if (antibanSnapshot != null)
            {
                antibanSnapshot.restore();
                antibanSnapshot = null;
            }
        }
    }
}
