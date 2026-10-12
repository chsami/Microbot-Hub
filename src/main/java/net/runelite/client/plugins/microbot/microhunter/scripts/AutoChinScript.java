package net.runelite.client.plugins.microbot.microhunter.scripts;

import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.hunter.HunterPlugin;
import net.runelite.client.plugins.hunter.HunterTrap;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.microhunter.AutoHunterConfig;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

import java.awt.Rectangle;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

public class AutoChinScript extends Script {
    public enum State {
        MONITORING,
        BUILDING_LAYOUT,
        MOVING,
        REACTING,
        WAITING_FOR_CONFIRMATION,
        SCANNING_HUNTING_AREA,
        HOPPING_WORLD,
        VERIFYING_WORLD,
        BREAK_RECOVERY,
        BREAK_PENDING,
        STOPPED
    }

    private enum Action {
        RESET_CAUGHT("Reset"),
        RESET("Reset"),
        CHECK("Check"),
        DISMANTLE("Dismantle"),
        TAKE("Take"),
        LAY("Lay");

        private final String menuAction;

        Action(String menuAction) {
            this.menuAction = menuAction;
        }
    }

    private static final long ACTION_TIMEOUT_MS = 6_000;
    private static final long REBUILD_TIMEOUT_MS = 9_000;
    private static final long SCENE_BASELINE_MS = 10_000;
    private static final long SPAWN_EXPIRY_MS = 600_000;
    private static final long MOUSE_WANDER_MIN_INTERVAL_MS = 45_000;
    private static final long MOUSE_WANDER_MAX_INTERVAL_MS = 120_001;
    private static final long LOGIN_SCENE_SETTLE_MS = 1_800;
    private static final long OCCUPANCY_SCAN_MS = 7_000;
    private static final long PLAYER_PERSISTENCE_MS = 3_500;
    private static final long WORLD_HOP_TIMEOUT_MS = 15_000;
    private static final long REJECTED_WORLD_COOLDOWN_MS = 600_000;
    private static final long BREAK_RECOVERY_CLEAR_MS = 1_500;
    private static final int MAX_WORLD_HOP_ATTEMPTS = 5;
    private static final EnumSet<WorldType> UNSAFE_WORLD_TYPES = EnumSet.of(
            WorldType.PVP, WorldType.BOUNTY, WorldType.PVP_ARENA, WorldType.SKILL_TOTAL,
            WorldType.QUEST_SPEEDRUNNING, WorldType.HIGH_RISK, WorldType.LAST_MAN_STANDING,
            WorldType.BETA_WORLD, WorldType.LEGACY_ONLY, WorldType.EOC_ONLY, WorldType.NOSAVE_MODE,
            WorldType.TOURNAMENT, WorldType.FRESH_START_WORLD, WorldType.DEADMAN, WorldType.SEASONAL);
    private final Set<WorldPoint> managedTiles = ConcurrentHashMap.newKeySet();
    private final List<WorldPoint> layoutSlots = new ArrayList<>();
    private final Map<WorldPoint, SpawnObservation> spawnObservations = new ConcurrentHashMap<>();
    private final Map<WorldPoint, String> observedTrapSignatures = new ConcurrentHashMap<>();
    @Inject private HunterPlugin hunterPlugin;
    private volatile State currentState = State.MONITORING;
    private volatile String nextAction = "Initialising";
    private volatile String stopReason = "";
    private volatile WorldPoint bestSpawnTile;
    private volatile WorldPoint bestRingTile;
    private volatile String spawnSummary = "none";
    private volatile int catches;
    private volatile int resets;
    private volatile int activeTraps;
    private volatile int trapLimit = 1;
    private volatile int huntingRadius = 6;
    private volatile boolean humanizerEnabled = true;
    private volatile PendingAction pending;
    private volatile WorldPoint moveTarget;
    private long moveTargetStartedAt;
    private int moveTargetClickAttempts;
    private WorldPoint blockedSetupTile;
    private long blockedSetupTileUntil;
    private WorldPoint startTile;
    private WorldPoint layoutCenter;
    private long baselineUntil;
    private long nextRingEvaluationAt;
    private long nextMouseWanderAt;
    private long mouseWanderPauseUntil;
    private Action delayedAction;
    private WorldPoint delayedActionTile;
    private long delayedActionReadyAt;
    private WorldPoint lastCanvasMoveTile;
    private long lastCanvasMoveAt;
    private volatile WorldPoint preparedTrapTile;
    private long preparedTrapExpiresAt;
    private boolean preparedTrapNeedsReacquire;
    private final Map<Integer, Long> rejectedWorlds = new ConcurrentHashMap<>();
    private boolean wasLoggedIn;
    private boolean worldArrivalPending;
    private int observedWorld = -1;
    private long scanReadyAt;
    private long scanEndsAt;
    private long nearbyPlayerSeenSince;
    private int worldHopAttempts;
    private int hopTargetWorld = -1;
    private int hopSourceWorld = -1;
    private long hopStartedAt;
    private boolean breakRecoveryRequested;
    private boolean breakRecoveryComplete;
    private boolean breakRecoveryLockHeld;
    private long breakRecoveryClearSince;
    private int breakRecoveryRecoveredCount;

    public boolean run(AutoHunterConfig config) {
        resetSession();
        Rs2Antiban.setActivityIntensity(ActivityIntensity.HIGH);
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> pulse(config),
                0, 250, TimeUnit.MILLISECONDS);
        Microbot.log("AutoHunter started: source-only red-chin state machine; no existing traps adopted");
        return true;
    }

    private void resetSession() {
        managedTiles.clear();
        layoutSlots.clear();
        spawnObservations.clear();
        observedTrapSignatures.clear();
        currentState = State.MONITORING;
        nextAction = "Waiting for client state";
        stopReason = "";
        bestSpawnTile = null;
        bestRingTile = null;
        spawnSummary = "none";
        catches = 0;
        resets = 0;
        activeTraps = 0;
        pending = null;
        moveTarget = null;
        moveTargetStartedAt = 0;
        moveTargetClickAttempts = 0;
        blockedSetupTile = null;
        blockedSetupTileUntil = 0;
        startTile = Microbot.isLoggedIn() ? Rs2Player.getWorldLocation() : null;
        if (startTile != null) Microbot.log("AutoHunter session origin: " + startTile);
        layoutCenter = null;
        baselineUntil = System.currentTimeMillis() + SCENE_BASELINE_MS;
        nextRingEvaluationAt = 0;
        nextMouseWanderAt = scheduleFromNow(System.currentTimeMillis(),
                MOUSE_WANDER_MIN_INTERVAL_MS, MOUSE_WANDER_MAX_INTERVAL_MS);
        mouseWanderPauseUntil = 0;
        lastCanvasMoveTile = null;
        lastCanvasMoveAt = 0;
        clearPreparedTrap();
        clearDelayedAction();
        rejectedWorlds.clear();
        wasLoggedIn = false;
        worldArrivalPending = false;
        observedWorld = -1;
        worldHopAttempts = 0;
        hopTargetWorld = -1;
        hopSourceWorld = -1;
        hopStartedAt = 0;
        breakRecoveryRequested = false;
        breakRecoveryComplete = false;
        breakRecoveryLockHeld = false;
        breakRecoveryClearSince = 0;
        breakRecoveryRecoveredCount = 0;
    }

    private void pulse(AutoHunterConfig config) {
        try {
            if (!Microbot.isLoggedIn()) {
                wasLoggedIn = false;
                return;
            }
            if (startTile == null) {
                startTile = Rs2Player.getWorldLocation();
                baselineUntil = System.currentTimeMillis() + SCENE_BASELINE_MS;
                if (startTile != null) Microbot.log("AutoHunter session origin: " + startTile);
            }
            boolean breakImminent = BreakHandlerScript.breakIn > 0
                    && BreakHandlerScript.breakIn <= 60;
            if (breakImminent && !breakRecoveryComplete) requestBreakRecovery();
            if (!breakImminent && !BreakHandlerScript.isBreakActive()
                    && breakRecoveryComplete) {
                breakRecoveryComplete = false;
            }
            int currentWorld = Microbot.getClient().getWorld();
            if (!wasLoggedIn || currentWorld != observedWorld) {
                observedWorld = currentWorld;
                worldArrivalPending = true;
            }
            wasLoggedIn = true;
            if (!super.run()) return;
            if (currentState == State.STOPPED) return;

            huntingRadius = Math.max(1, config.huntingRadius());
            humanizerEnabled = config.humanizerEnabled();
            if (worldArrivalPending) {
                beginWorldArrival(config, currentWorld);
                worldArrivalPending = false;
            }
            if (currentState == State.HOPPING_WORLD) {
                monitorWorldHop();
                return;
            }
            if (currentState == State.SCANNING_HUNTING_AREA
                    || currentState == State.VERIFYING_WORLD) {
                scanHuntingArea(config);
                return;
            }

            if (Microbot.getClient().isMenuOpen()) {
                clearPreparedTrap();
            } else if (Rs2Player.isMoving() && preparedTrapTile != null) {
                preparedTrapNeedsReacquire = true;
                preparedTrapExpiresAt = System.currentTimeMillis() + 3_000;
            }
            trapLimit = AutoHunterPlanner.normalBoxTrapLimit(Rs2Player.getRealSkillLevel(Skill.HUNTER));
            expireSpawnObservations();
            updateSpawnRing(config);
            activeTraps = countActiveManagedTraps();

            if (pending != null) {
                confirmOrTimeoutPending();
                return;
            }

            if (breakRecoveryRequested) {
                recoverTrapsForBreak();
                return;
            }

            if (Rs2Inventory.emptySlotCount() == 0) {
                stopSafely("Inventory full; catches are never dropped or banked automatically");
                return;
            }

            if (currentState == State.BREAK_PENDING) transition(State.MONITORING, "Break window cleared");

            if (moveTarget != null) {
                handleMoveTarget();
                return;
            }

            if (AutoHunterPlanner.shouldBootstrap(managedTiles.size(), trapLimit)) {
                // A fallen owned trap can despawn, so recovering it is the only
                // maintenance action allowed to interrupt the initial fill.
                if (recoverFallenManagedTrap()) return;
                if (layMissingManagedTrap()) return;
                prepareNewTrap(config);
                return;
            }

            // Restore an empty layout slot before servicing occupied traps. A
            // fallen trap is the only owned state with an item-expiry clock.
            if (recoverFallenManagedTrap()) return;
            if (layMissingManagedTrap()) return;
            if (interactWithOldestActionableTrap()) return;
            if (runIdleMouseWander(config)) return;
            transition(State.MONITORING, "Monitoring four-trap layout");
        } catch (Exception ex) {
            Microbot.logStackTrace(getClass().getSimpleName(), ex);
        }
    }

    private void beginWorldArrival(AutoHunterConfig config, int world) {
        boolean verifyingHop = hopSourceWorld > 0 && world != hopSourceWorld;
        clearWorldSessionState();
        baselineUntil = System.currentTimeMillis() + SCENE_BASELINE_MS;
        hopTargetWorld = -1;
        hopSourceWorld = -1;
        hopStartedAt = 0;
        nearbyPlayerSeenSince = 0;
        if (!config.avoidOccupiedWorlds()) {
            worldHopAttempts = 0;
            transition(State.MONITORING, "World occupancy scan disabled");
            return;
        }
        long now = System.currentTimeMillis();
        scanReadyAt = now + LOGIN_SCENE_SETTLE_MS;
        scanEndsAt = now + OCCUPANCY_SCAN_MS;
        transition(verifyingHop ? State.VERIFYING_WORLD : State.SCANNING_HUNTING_AREA,
                "Waiting for world " + world + " scene to settle");
        Microbot.log("AutoHunter occupancy: scanning world " + world
                + (verifyingHop ? " after hop" : " after login"));
    }

    private void clearWorldSessionState() {
        managedTiles.clear();
        layoutSlots.clear();
        spawnObservations.clear();
        observedTrapSignatures.clear();
        pending = null;
        moveTarget = null;
        moveTargetStartedAt = 0;
        moveTargetClickAttempts = 0;
        blockedSetupTile = null;
        blockedSetupTileUntil = 0;
        layoutCenter = null;
        bestSpawnTile = null;
        bestRingTile = null;
        spawnSummary = "none";
        activeTraps = 0;
        clearPreparedTrap();
        clearDelayedAction();
    }

    private void scanHuntingArea(AutoHunterConfig config) {
        long now = System.currentTimeMillis();
        if (now < scanReadyAt) return;
        WorldPoint center = startTile == null ? Rs2Player.getWorldLocation() : startTile;
        if (center == null) {
            transition(currentState, "Waiting for local player location before occupancy scan");
            return;
        }

        int nearbyPlayers = countNearbyPlayers(center, huntingRadius);
        int nearbyTraps = countNearbyTrapEvidence(center, huntingRadius);
        if (nearbyPlayers > 0) {
            if (nearbyPlayerSeenSince == 0) nearbyPlayerSeenSince = now;
        } else {
            nearbyPlayerSeenSince = 0;
        }

        boolean persistentPlayer = nearbyPlayerSeenSince > 0
                && now - nearbyPlayerSeenSince >= PLAYER_PERSISTENCE_MS;
        if (AutoHunterPlanner.isHuntingAreaOccupied(nearbyPlayers, nearbyTraps, persistentPlayer)) {
            String reason = "occupied: players=" + nearbyPlayers + " traps=" + nearbyTraps;
            rejectCurrentWorld(reason);
            requestAustralianWorldHop(reason);
            return;
        }

        if (now >= scanEndsAt) {
            int world = Microbot.getClient().getWorld();
            worldHopAttempts = 0;
            nearbyPlayerSeenSince = 0;
            transition(State.MONITORING, "World " + world + " hunting area is clear");
            Microbot.log("AutoHunter occupancy: world " + world + " clear; starting layout");
        } else {
            transition(currentState, "Scanning area: players=" + nearbyPlayers + " traps=" + nearbyTraps);
        }
    }

    private int countNearbyPlayers(WorldPoint center, int radius) {
        Rs2PlayerModel local = Rs2Player.getLocalPlayer();
        return (int) Microbot.getRs2PlayerCache().query()
                .where(player -> player.getWorldLocation() != null
                        && player.getWorldLocation().getPlane() == center.getPlane()
                        && player.getWorldLocation().distanceTo(center) <= radius
                        && (local == null || player.getId() != local.getId()))
                .toList().size();
    }

    private int countNearbyTrapEvidence(WorldPoint center, int radius) {
        Set<WorldPoint> trapTiles = ConcurrentHashMap.newKeySet();
        Microbot.getRs2TileObjectCache().query().within(center, radius)
                .where(this::isBoxTrapObject)
                .toList().forEach(object -> addTrapTileIfPresent(trapTiles, object.getWorldLocation()));
        Microbot.getRs2TileItemCache().query().withId(ItemID.BOX_TRAP).within(center, radius)
                .toList().forEach(item -> addTrapTileIfPresent(trapTiles, item.getWorldLocation()));
        return trapTiles.size();
    }

    private void addTrapTileIfPresent(Set<WorldPoint> trapTiles, WorldPoint tile) {
        if (tile != null) trapTiles.add(tile);
    }

    private boolean isBoxTrapObject(Rs2TileObjectModel object) {
        if (object == null) return false;
        String name = object.getName();
        return name != null && ("Box trap".equalsIgnoreCase(name)
                || "Shaking box".equalsIgnoreCase(name));
    }

    private void rejectCurrentWorld(String reason) {
        int world = Microbot.getClient().getWorld();
        rejectedWorlds.put(world, System.currentTimeMillis() + REJECTED_WORLD_COOLDOWN_MS);
        Microbot.log("AutoHunter occupancy: rejecting world " + world + " (" + reason + ")");
    }

    private void requestAustralianWorldHop(String reason) {
        if (worldHopAttempts >= MAX_WORLD_HOP_ATTEMPTS) {
            stopSafely("No clear Australian hunting world after " + MAX_WORLD_HOP_ATTEMPTS + " attempts");
            return;
        }
        Integer target = selectAustralianWorld();
        if (target == null) {
            stopSafely("No eligible Australian world is currently available");
            return;
        }
        worldHopAttempts++;
        hopSourceWorld = Microbot.getClient().getWorld();
        hopTargetWorld = target;
        hopStartedAt = System.currentTimeMillis();
        transition(State.HOPPING_WORLD, "Hopping to Australian world " + target + " (" + reason + ")");
        Microbot.log("AutoHunter occupancy: hopping " + hopSourceWorld + " -> " + target
                + " attempt=" + worldHopAttempts);
        Microbot.hopToWorld(target);
    }

    private Integer selectAustralianWorld() {
        long now = System.currentTimeMillis();
        rejectedWorlds.entrySet().removeIf(entry -> entry.getValue() <= now);
        WorldResult result = Microbot.getWorldService() == null ? null : Microbot.getWorldService().getWorlds();
        if (result == null || result.getWorlds() == null) return null;
        boolean members = Rs2Player.isMember();
        int currentWorld = Microbot.getClient().getWorld();
        List<World> candidates = new ArrayList<>();
        for (World world : result.getWorlds()) {
            if (world == null || world.getId() == currentWorld || world.getRegion() != WorldRegion.AUSTRALIA) continue;
            Set<WorldType> types = world.getTypes();
            if (types == null || types.contains(WorldType.MEMBERS) != members) continue;
            if (!Collections.disjoint(types, UNSAFE_WORLD_TYPES)) continue;
            if (world.getPlayers() < 0 || world.getPlayers() >= 1_900) continue;
            if (rejectedWorlds.containsKey(world.getId())) continue;
            candidates.add(world);
        }
        Collections.shuffle(candidates);
        return candidates.isEmpty() ? null : candidates.get(0).getId();
    }

    private void monitorWorldHop() {
        int currentWorld = Microbot.getClient().getWorld();
        if (hopSourceWorld > 0 && currentWorld != hopSourceWorld) return;
        if (System.currentTimeMillis() - hopStartedAt < WORLD_HOP_TIMEOUT_MS) return;
        if (hopTargetWorld > 0) {
            rejectedWorlds.put(hopTargetWorld, System.currentTimeMillis() + REJECTED_WORLD_COOLDOWN_MS);
            Microbot.log("AutoHunter occupancy: hop to world " + hopTargetWorld + " timed out");
        }
        requestAustralianWorldHop("previous hop timed out");
    }

    private void requestBreakRecovery() {
        if (!breakRecoveryRequested) {
            breakRecoveryRequested = true;
            breakRecoveryClearSince = 0;
            breakRecoveryRecoveredCount = 0;
            clearMoveTarget();
            clearPreparedTrap();
            clearDelayedAction();
            Microbot.log("AutoHunter break recovery: locking Break Handler until traps are in inventory");
        }
        BreakHandlerScript.setLockState(true);
        breakRecoveryLockHeld = true;
        transition(State.BREAK_RECOVERY, "Recovering traps before break");
    }

    private void recoverTrapsForBreak() {
        transition(State.BREAK_RECOVERY, "Recovering " + managedTiles.size() + " trap tile(s) before break");

        if (recoverFallenTrapForBreak()) {
            breakRecoveryClearSince = 0;
            return;
        }

        WorldPoint tile = oldestBreakRecoveryTrap();
        if (tile != null) {
            breakRecoveryClearSince = 0;
            Rs2TileObjectModel trap = trapAt(tile);
            AutoHunterPlanner.TrapState state = classify(trap);
            Action action = state == AutoHunterPlanner.TrapState.CAUGHT
                    ? Action.CHECK : Action.DISMANTLE;
            if (!readyForHumanizedAction(action, tile)) return;
            if (trap != null && trap.click(action.menuAction)) {
                clearDelayedAction();
                beginPending(action, tile, trapSignature(trap), true);
                Microbot.log("AutoHunter break recovery: " + action + " dispatched at " + tile);
            } else {
                clearDelayedAction();
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (breakRecoveryClearSince == 0) {
            breakRecoveryClearSince = now;
            transition(State.BREAK_RECOVERY, "Verifying all traps are back in inventory");
            return;
        }
        if (now - breakRecoveryClearSince < BREAK_RECOVERY_CLEAR_MS) return;

        managedTiles.clear();
        layoutSlots.clear();
        layoutCenter = null;
        breakRecoveryRequested = false;
        breakRecoveryComplete = true;
        breakRecoveryClearSince = 0;
        releaseBreakRecoveryLock();
        transition(State.BREAK_PENDING, "All traps recovered; Break Handler unlocked");
        Microbot.log("AutoHunter break recovery: complete; recovered " + breakRecoveryRecoveredCount
                + " trap(s) and unlocked Break Handler");
    }

    private boolean recoverFallenTrapForBreak() {
        for (WorldPoint tile : managedTiles) {
            if (hasAnyObjectAt(tile)) continue;
            if (Microbot.getRs2TileItemCache().query().withId(ItemID.BOX_TRAP)
                    .within(tile, 0).count() == 0) continue;
            if (!readyForHumanizedAction(Action.TAKE, tile)) return true;
            if (Microbot.getRs2TileItemCache().query().withId(ItemID.BOX_TRAP)
                    .within(tile, 0).interact("Take")) {
                clearDelayedAction();
                beginPending(Action.TAKE, tile, "ground-item", true);
            } else {
                clearDelayedAction();
            }
            return true;
        }
        return false;
    }

    private WorldPoint oldestBreakRecoveryTrap() {
        Map<WorldPoint, HunterTrap> timers = hunterPlugin == null ? new HashMap<>()
                : Microbot.getClientThread().runOnClientThreadOptional(
                () -> new HashMap<>(hunterPlugin.getTraps())).orElseGet(HashMap::new);
        return managedTiles.stream()
                .filter(tile -> {
                    AutoHunterPlanner.TrapState state = classify(trapAt(tile));
                    return state == AutoHunterPlanner.TrapState.CAUGHT
                            || state == AutoHunterPlanner.TrapState.FAILED
                            || state == AutoHunterPlanner.TrapState.ACTIVE;
                })
                .min(Comparator.comparing(tile -> {
                    HunterTrap timer = timers.get(tile);
                    return timer == null ? java.time.Instant.MAX : timer.getPlacedOn();
                })).orElse(null);
    }

    private void releaseBreakRecoveryLock() {
        if (!breakRecoveryLockHeld) return;
        BreakHandlerScript.setLockState(false);
        breakRecoveryLockHeld = false;
    }

    private boolean interactWithManagedTrap(AutoHunterPlanner.TrapState targetState, Action action) {
        for (WorldPoint tile : managedTiles) {
            Rs2TileObjectModel trap = trapAt(tile);
            if (trap == null || classify(trap) != targetState) continue;
            if (!readyForHumanizedAction(action, tile)) return true;
            if (trap.click(action.menuAction)) {
                clearDelayedAction();
                beginPending(action, tile, trapSignature(trap));
                return true;
            }
            clearDelayedAction();
        }
        return false;
    }

    private boolean interactWithOldestActionableTrap() {
        Map<WorldPoint, HunterTrap> timers = hunterPlugin == null ? new HashMap<>()
                : Microbot.getClientThread().runOnClientThreadOptional(
                () -> new HashMap<>(hunterPlugin.getTraps())).orElseGet(HashMap::new);
        WorldPoint tile = managedTiles.stream()
                .filter(point -> {
                    AutoHunterPlanner.TrapState state = classify(trapAt(point));
                    return state == AutoHunterPlanner.TrapState.CAUGHT
                            || state == AutoHunterPlanner.TrapState.FAILED;
                })
                .min(Comparator.comparing(point -> {
                    HunterTrap timer = timers.get(point);
                    return timer == null ? java.time.Instant.MAX : timer.getPlacedOn();
                })).orElse(null);
        if (tile == null) return false;
        Rs2TileObjectModel trap = trapAt(tile);
        AutoHunterPlanner.TrapState state = classify(trap);
        Action action = state == AutoHunterPlanner.TrapState.CAUGHT ? Action.RESET_CAUGHT : Action.RESET;
        if (!readyForHumanizedAction(action, tile)) return true;
        if (trap != null && trap.click(action.menuAction)) {
            clearDelayedAction();
            beginPending(action, tile, trapSignature(trap));
            HunterTrap timer = timers.get(tile);
            Microbot.log("AutoHunter priority: " + action + " at " + tile + " decay="
                    + (timer == null ? "unknown" : Math.round(timer.getTrapTimeRelative() * 100) + "%"));
            return true;
        }
        clearDelayedAction();
        return false;
    }

    private boolean recoverFallenManagedTrap() {
        for (WorldPoint tile : managedTiles) {
            if (hasAnyObjectAt(tile)) continue;
            if (Microbot.getRs2TileItemCache().query().withId(ItemID.BOX_TRAP).within(tile, 0).count() == 0) continue;
            if (!readyForHumanizedAction(Action.TAKE, tile)) return true;
            if (Microbot.getRs2TileItemCache().query().withId(ItemID.BOX_TRAP).within(tile, 0).interact("Take")) {
                clearDelayedAction();
                beginPending(Action.TAKE, tile, "ground-item");
                return true;
            }
            clearDelayedAction();
        }
        return false;
    }

    private boolean layMissingManagedTrap() {
        if (!Rs2Inventory.contains(ItemID.BOX_TRAP)) return false;
        for (WorldPoint tile : managedTiles) {
            if (!hasAnyObjectAt(tile)
                    && Microbot.getRs2TileItemCache().query().withId(ItemID.BOX_TRAP).within(tile, 0).count() == 0) {
                setMoveTarget(tile);
                transition(State.MOVING, setupProgress() + ": restore owned trap tile " + tile);
                return true;
            }
        }
        return false;
    }

    private void prepareNewTrap(AutoHunterConfig config) {
        if (!Rs2Inventory.contains(ItemID.BOX_TRAP)) {
            transition(State.MONITORING, "Need a box trap in inventory");
            return;
        }
        if (!ensureLayout(config)) {
            transition(State.BUILDING_LAYOUT, setupProgress() + ": waiting for a clear reachable layout");
            return;
        }
        WorldPoint player = Rs2Player.getWorldLocation();
        WorldPoint target = layoutSlots.stream()
                .filter(tile -> !managedTiles.contains(tile))
                .filter(tile -> !isTemporarilyBlockedSetupTile(tile))
                .filter(tile -> isSafePlacementTile(tile, false))
                .min(Comparator.comparingInt(player::distanceTo))
                .orElse(null);
        if (target == null) {
            transition(State.BUILDING_LAYOUT, setupProgress() + ": layout slot is temporarily blocked");
            return;
        }
        if (!isSafePlacementTile(target, false)) {
            transition(State.MONITORING, "Current placement tile is occupied or unreachable");
            return;
        }
        setMoveTarget(target);
        transition(State.BUILDING_LAYOUT, setupProgress() + ": move to lay box trap at " + target);
    }

    private boolean ensureLayout(AutoHunterConfig config) {
        if (layoutCenter != null && layoutSlots.size() == trapLimit) return true;

        WorldPoint preferredCenter = config.useSpawnRing() ? bestSpawnTile : startTile;
        if (preferredCenter == null) return false;

        List<WorldPoint> candidateCenters = new ArrayList<>();
        candidateCenters.add(preferredCenter);
        if (!config.useSpawnRing()) {
            AutoHunterPlanner.placementGrid(preferredCenter, 2).stream()
                    .filter(center -> !center.equals(preferredCenter))
                    .sorted(Comparator.comparingInt(preferredCenter::distanceTo))
                    .forEach(candidateCenters::add);
        }

        for (WorldPoint center : candidateCenters) {
            List<WorldPoint> candidateSlots = AutoHunterPlanner.fiveDotLayout(center, trapLimit);
            if (candidateSlots.size() == trapLimit
                    && candidateSlots.stream().allMatch(tile -> managedTiles.contains(tile)
                    || (!isTemporarilyBlockedSetupTile(tile) && isSafePlacementTile(tile, false)))) {
                layoutCenter = center;
                layoutSlots.clear();
                layoutSlots.addAll(candidateSlots);
                bestRingTile = candidateSlots.get(0);
                Microbot.log("AutoHunter layout: center=" + layoutCenter + " slots=" + layoutSlots);
                return true;
            }
        }
        return false;
    }

    private String setupProgress() {
        return "Initial setup " + managedTiles.size() + "/" + trapLimit;
    }

    private void handleMoveTarget() {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (!player.equals(moveTarget)) {
            if ((!managedTiles.contains(moveTarget) && !isSafePlacementTile(moveTarget, false))
                    || System.currentTimeMillis() - moveTargetStartedAt >= 6_000
                    || moveTargetClickAttempts >= 5) {
                WorldPoint blocked = moveTarget;
                clearMoveTarget();
                blockedSetupTile = blocked;
                blockedSetupTileUntil = System.currentTimeMillis() + 15_000;
                layoutCenter = null;
                layoutSlots.clear();
                clearDelayedAction();
                transition(State.BUILDING_LAYOUT, "Abandoned blocked setup tile " + blocked);
                return;
            }
            if (System.currentTimeMillis() - lastCanvasMoveAt >= 1_000) moveTargetClickAttempts++;
            if (!clickCanvasTile(moveTarget)) {
                transition(State.MOVING, "Target tile is not visible on the game canvas: " + moveTarget);
            }
            return;
        }
        WorldPoint layTile = moveTarget;
        clearMoveTarget();
        if (layTile.equals(blockedSetupTile)) {
            blockedSetupTile = null;
            blockedSetupTileUntil = 0;
        }
        if (!isSafePlacementTile(layTile, managedTiles.contains(layTile))
                || !Rs2Inventory.contains(ItemID.BOX_TRAP)) {
            clearDelayedAction();
            transition(State.MONITORING, "Lay tile became unavailable");
            return;
        }
        if (!readyForHumanizedAction(Action.LAY, layTile)) {
            setMoveTarget(layTile);
            return;
        }
        if (Rs2Inventory.interact(ItemID.BOX_TRAP, "Lay")) {
            clearDelayedAction();
            beginPending(Action.LAY, layTile, "empty");
        } else {
            clearDelayedAction();
            transition(State.MONITORING, "Lay interaction was not dispatched");
        }
    }

    private void setMoveTarget(WorldPoint tile) {
        if (!tile.equals(moveTarget)) {
            moveTargetStartedAt = System.currentTimeMillis();
            moveTargetClickAttempts = 0;
        }
        moveTarget = tile;
    }

    private void clearMoveTarget() {
        moveTarget = null;
        moveTargetStartedAt = 0;
        moveTargetClickAttempts = 0;
        lastCanvasMoveTile = null;
        lastCanvasMoveAt = 0;
    }

    private boolean isTemporarilyBlockedSetupTile(WorldPoint tile) {
        if (blockedSetupTile == null) return false;
        if (System.currentTimeMillis() >= blockedSetupTileUntil) {
            blockedSetupTile = null;
            blockedSetupTileUntil = 0;
            return false;
        }
        return blockedSetupTile.equals(tile);
    }

    private boolean clickCanvasTile(WorldPoint tile) {
        long now = System.currentTimeMillis();
        if (tile != null && tile.equals(lastCanvasMoveTile) && now - lastCanvasMoveAt < 1_000) return true;
        if (tile == null || Microbot.getClient().getTopLevelWorldView() == null) return false;
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), tile);
        if (localPoint == null) return false;
        Polygon tilePoly = Perspective.getCanvasTilePoly(Microbot.getClient(), localPoint);
        if (tilePoly == null || tilePoly.npoints < 3) return false;
        int sumX = 0;
        int sumY = 0;
        for (int i = 0; i < tilePoly.npoints; i++) {
            sumX += tilePoly.xpoints[i];
            sumY += tilePoly.ypoints[i];
        }
        Point canvasPoint = new Point(sumX / tilePoly.npoints, sumY / tilePoly.npoints);
        if (!tilePoly.contains(canvasPoint.getX(), canvasPoint.getY())
                || canvasPoint.getX() < 0 || canvasPoint.getY() < 0) return false;

        NewMenuEntry entry = new NewMenuEntry()
                .param0(canvasPoint.getX())
                .param1(canvasPoint.getY())
                .type(MenuAction.WALK)
                .identifier(0)
                .itemId(0)
                .option("Walk here");
        Microbot.doInvoke(entry, new Rectangle(canvasPoint.getX(), canvasPoint.getY(), 1, 1));
        lastCanvasMoveTile = tile;
        lastCanvasMoveAt = now;
        Microbot.log("AutoHunter movement: canvas click " + tile + " at " + canvasPoint);
        return true;
    }

    private boolean readyForHumanizedAction(Action action, WorldPoint tile) {
        long now = System.currentTimeMillis();
        boolean prepared = tile.equals(preparedTrapTile) && now < preparedTrapExpiresAt;
        if (preparedTrapTile != null && !prepared) clearPreparedTrap();
        if (prepared && preparedTrapNeedsReacquire) {
            if (!reacquirePreparedTrap(tile)) clearPreparedTrap();
            else preparedTrapNeedsReacquire = false;
            return preparedTrapTile != null;
        }
        // The confirmation-phase pre-hover already supplies the reaction delay.
        // Do not schedule another delay before dispatching the prepared reset.
        if (prepared) return true;
        if (delayedAction != action || !tile.equals(delayedActionTile)) {
            delayedAction = action;
            delayedActionTile = tile;
            delayedActionReadyAt = now + randomActionDelay(action);
        }
        if (now < delayedActionReadyAt) {
            transition(State.REACTING, "Reacting to " + action.menuAction.toLowerCase() + " at " + tile);
            return false;
        }
        return true;
    }

    private int randomActionDelay(Action action) {
        if (!humanizerEnabled) return 0;
        int delay;
        switch (action) {
            case RESET_CAUGHT:
            case RESET:
                delay = randomBetween(120, 421);
                break;
            case TAKE:
            case CHECK:
            case DISMANTLE:
                delay = randomBetween(180, 521);
                break;
            default:
                delay = randomBetween(240, 701);
        }
        return ThreadLocalRandom.current().nextInt(100) < 7
                ? delay + randomBetween(100, 351) : delay;
    }

    private boolean runIdleMouseWander(AutoHunterConfig config) {
        long now = System.currentTimeMillis();
        if (!config.humanizerEnabled()) {
            mouseWanderPauseUntil = 0;
            nextMouseWanderAt = scheduleFromNow(now,
                    MOUSE_WANDER_MIN_INTERVAL_MS, MOUSE_WANDER_MAX_INTERVAL_MS);
            return false;
        }
        if (mouseWanderPauseUntil > 0) {
            if (now < mouseWanderPauseUntil) {
                transition(State.MONITORING, "Brief pause after mouse wander");
                return true;
            }
            mouseWanderPauseUntil = 0;
            nextMouseWanderAt = scheduleFromNow(now,
                    MOUSE_WANDER_MIN_INTERVAL_MS, MOUSE_WANDER_MAX_INTERVAL_MS);
            return false;
        }
        if (now < nextMouseWanderAt || Microbot.naturalMouse == null
                || Microbot.getClient().isMenuOpen() || Rs2Player.isMoving()) return false;

        net.runelite.api.Point current = Microbot.getClient().getMouseCanvasPosition();
        int width = Microbot.getClient().getCanvasWidth();
        int height = Microbot.getClient().getCanvasHeight();
        int originX = current == null ? width / 2 : current.getX();
        int originY = current == null ? height / 2 : current.getY();
        int dx = randomBetween(70, 201) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int dy = randomBetween(35, 141) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        int x = Math.max(8, Math.min(width - 8, originX + dx));
        int y = Math.max(8, Math.min(height - 8, originY + dy));
        Microbot.naturalMouse.moveTo(x, y);
        mouseWanderPauseUntil = now + randomBetween(250, 901);
        transition(State.MONITORING, "Mouse wandered while monitoring traps");
        return true;
    }

    private void clearDelayedAction() {
        delayedAction = null;
        delayedActionTile = null;
        delayedActionReadyAt = 0;
    }

    private void clearPreparedTrap() {
        preparedTrapTile = null;
        preparedTrapExpiresAt = 0;
        preparedTrapNeedsReacquire = false;
    }

    private static int randomBetween(int minimumInclusive, int maximumExclusive) {
        return ThreadLocalRandom.current().nextInt(minimumInclusive, maximumExclusive);
    }

    private static long scheduleFromNow(long now, long minimumDelay, long maximumDelay) {
        return now + ThreadLocalRandom.current().nextLong(minimumDelay, maximumDelay);
    }

    private void beginPending(Action action, WorldPoint tile, String beforeSignature) {
        beginPending(action, tile, beforeSignature, false);
    }

    private void beginPending(Action action, WorldPoint tile, String beforeSignature,
                              boolean breakRecovery) {
        long now = System.currentTimeMillis();
        pending = new PendingAction(action, tile, beforeSignature, Rs2Inventory.count(),
                Rs2Inventory.count(ItemID.BOX_TRAP), breakRecovery, now,
                now + (humanizerEnabled ? randomBetween(220, 651) : 0));
        transition(State.WAITING_FOR_CONFIRMATION, action + " dispatched at " + tile);
        Microbot.log("AutoHunter action: " + action + " dispatched at " + tile);
    }

    private void confirmOrTimeoutPending() {
        PendingAction action = pending;
        if (action == null) return;
        Rs2TileObjectModel object = trapAt(action.tile);
        String currentSignature = trapSignature(object);
        boolean inventoryChanged = Rs2Inventory.count() != action.inventoryCount;
        boolean objectChanged = !currentSignature.equals(action.beforeSignature);
        boolean confirmed;
        switch (action.action) {
            case LAY:
                action.sawTransition |= inventoryChanged || objectChanged || Rs2Player.getAnimation() == 5208;
                confirmed = action.sawTransition && classify(object) == AutoHunterPlanner.TrapState.ACTIVE
                        && Rs2Player.getAnimation() == -1
                        && Microbot.getRs2TileItemCache().query().withId(ItemID.BOX_TRAP)
                        .within(action.tile, 0).count() == 0;
                break;
            case TAKE:
                confirmed = inventoryChanged || Microbot.getRs2TileItemCache().query()
                        .withId(ItemID.BOX_TRAP).within(action.tile, 0).count() == 0;
                break;
            case CHECK:
            case DISMANTLE:
                action.sawTransition |= objectChanged || Rs2Player.getAnimation() == 5207
                        || Rs2Player.getAnimation() == 5212 || Rs2Player.getAnimation() == 5208;
                confirmed = action.sawTransition
                        && Rs2Inventory.count(ItemID.BOX_TRAP) > action.boxTrapCount
                        && Rs2Player.getAnimation() == -1
                        && object == null
                        && Microbot.getRs2TileItemCache().query().withId(ItemID.BOX_TRAP)
                        .within(action.tile, 0).count() == 0;
                break;
            default:
                action.sawTransition |= inventoryChanged || objectChanged
                        || Rs2Player.getAnimation() == 5212 || Rs2Player.getAnimation() == 5208;
                confirmed = action.sawTransition && classify(object) == AutoHunterPlanner.TrapState.ACTIVE
                        && Rs2Player.getAnimation() == -1
                        && Microbot.getRs2TileItemCache().query().withId(ItemID.BOX_TRAP)
                        .within(action.tile, 0).count() == 0;
        }

        if (isResetAction(action.action) && action.sawTransition) {
            preHoverNextReset(action);
        }

        if (confirmed) {
            if (action.action == Action.LAY) managedTiles.add(action.tile);
            if (action.action == Action.RESET_CAUGHT) catches++;
            if (action.action == Action.RESET) resets++;
            if (action.breakRecovery) {
                managedTiles.remove(action.tile);
                breakRecoveryRecoveredCount++;
            }
            pending = null;
            transition(action.breakRecovery ? State.BREAK_RECOVERY : State.MONITORING,
                    action.action + (action.breakRecovery ? " recovery" : " full rebuild")
                            + " confirmed at " + action.tile);
            Microbot.log("AutoHunter action: " + action.action + " confirmed at " + action.tile);
        } else if (System.currentTimeMillis() - action.startedAt >=
                (isResetAction(action.action) ? REBUILD_TIMEOUT_MS : ACTION_TIMEOUT_MS)) {
            pending = null;
            transition(action.breakRecovery ? State.BREAK_RECOVERY : State.MONITORING,
                    action.action + " timed out at " + action.tile);
            Microbot.log("AutoHunter action: " + action.action + " bounded timeout at " + action.tile);
        }
    }

    private boolean isResetAction(Action action) {
        return action == Action.RESET || action == Action.RESET_CAUGHT;
    }

    private void preHoverNextReset(PendingAction current) {
        if (current.preHoverComplete) {
            runPreHoverFidget(current);
            return;
        }
        if (System.currentTimeMillis() < current.nextPreHoverAttemptAt) return;
        if (Microbot.naturalMouse == null || Microbot.getClient().isMenuOpen() || Rs2Player.isMoving()) {
            current.preHoverTile = null;
            current.preHoverCorrectionAt = 0;
            current.nextPreHoverAttemptAt = System.currentTimeMillis() + 250;
            clearPreparedTrap();
            return;
        }

        if (current.preHoverTile == null) {
            current.preHoverTile = oldestActionableTrapExcluding(current.tile);
            if (current.preHoverTile == null) {
                current.nextPreHoverAttemptAt = System.currentTimeMillis() + 250;
                return;
            }
        }

        Rs2TileObjectModel target = trapAt(current.preHoverTile);
        AutoHunterPlanner.TrapState targetState = classify(target);
        if (target == null || (targetState != AutoHunterPlanner.TrapState.CAUGHT
                && targetState != AutoHunterPlanner.TrapState.FAILED)) {
            current.preHoverTile = null;
            current.preHoverCorrectionAt = 0;
            current.nextPreHoverAttemptAt = System.currentTimeMillis() + 150;
            clearPreparedTrap();
            return;
        }

        Rectangle bounds = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            java.awt.Shape clickbox = target.getClickbox();
            return clickbox == null ? null : clickbox.getBounds();
        }).orElse(null);
        if (bounds == null || bounds.width < 2 || bounds.height < 2) {
            current.preHoverTile = null;
            current.nextPreHoverAttemptAt = System.currentTimeMillis() + 250;
            return;
        }

        long now = System.currentTimeMillis();
        if (current.preHoverCorrectionAt > 0 && now < current.preHoverCorrectionAt) return;

        int insetX = Math.max(1, bounds.width / 5);
        int insetY = Math.max(1, bounds.height / 5);
        int minX = bounds.x + insetX;
        int maxX = Math.max(minX + 1, bounds.x + bounds.width - insetX);
        int minY = bounds.y + insetY;
        int maxY = Math.max(minY + 1, bounds.y + bounds.height - insetY);
        int targetX = humanizerEnabled ? randomBetween(minX, maxX) : bounds.x + bounds.width / 2;
        int targetY = humanizerEnabled ? randomBetween(minY, maxY) : bounds.y + bounds.height / 2;

        if (humanizerEnabled && current.preHoverCorrectionAt == 0
                && ThreadLocalRandom.current().nextInt(100) < 22) {
            int approachX = Math.max(8, Math.min(Microbot.getClient().getCanvasWidth() - 8,
                    targetX + randomBetween(8, 24) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1)));
            int approachY = Math.max(8, Math.min(Microbot.getClient().getCanvasHeight() - 8,
                    targetY + randomBetween(5, 17) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1)));
            Microbot.naturalMouse.moveTo(approachX, approachY);
            current.preHoverCorrectionAt = now + randomBetween(90, 241);
            return;
        }

        Microbot.naturalMouse.moveTo(targetX, targetY);
        current.preHoverComplete = true;
        current.preHoverFidgetsRemaining = humanizerEnabled ? randomBetween(1, 3) : 0;
        current.nextPreHoverFidgetAt = now + (humanizerEnabled ? randomBetween(180, 651) : 0);
        preparedTrapTile = current.preHoverTile;
        preparedTrapExpiresAt = now + 3_000;
        Microbot.log("AutoHunter pre-hover: next reset " + preparedTrapTile);
    }

    private void runPreHoverFidget(PendingAction current) {
        long now = System.currentTimeMillis();
        if (!humanizerEnabled || current.preHoverFidgetsRemaining <= 0
                || now < current.nextPreHoverFidgetAt || Microbot.naturalMouse == null
                || Microbot.getClient().isMenuOpen() || Rs2Player.isMoving()) return;
        Rectangle bounds = trapClickboxBounds(current.preHoverTile);
        if (bounds == null) return;
        Point mouse = Microbot.getClient().getMouseCanvasPosition();
        int baseX = mouse == null ? bounds.x + bounds.width / 2 : mouse.getX();
        int baseY = mouse == null ? bounds.y + bounds.height / 2 : mouse.getY();
        int x = Math.max(bounds.x + 1, Math.min(bounds.x + bounds.width - 1,
                baseX + randomBetween(3, 11) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1)));
        int y = Math.max(bounds.y + 1, Math.min(bounds.y + bounds.height - 1,
                baseY + randomBetween(2, 8) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1)));
        Microbot.naturalMouse.moveTo(x, y);
        current.preHoverFidgetsRemaining--;
        current.nextPreHoverFidgetAt = now + randomBetween(180, 651);
        preparedTrapExpiresAt = now + 3_000;
    }

    private boolean reacquirePreparedTrap(WorldPoint tile) {
        Rectangle bounds = trapClickboxBounds(tile);
        if (bounds == null || Microbot.naturalMouse == null || Rs2Player.isMoving()) return false;
        int centerX = bounds.x + bounds.width / 2;
        int centerY = bounds.y + bounds.height / 2;
        int sideX = Math.max(8, Math.min(Microbot.getClient().getCanvasWidth() - 8,
                centerX + randomBetween(10, 27) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1)));
        int sideY = Math.max(8, Math.min(Microbot.getClient().getCanvasHeight() - 8,
                centerY + randomBetween(4, 13) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1)));
        Microbot.naturalMouse.moveTo(sideX, sideY);
        preparedTrapExpiresAt = System.currentTimeMillis() + 3_000;
        Microbot.log("AutoHunter pre-hover: reacquired after movement " + tile);
        return true;
    }

    private Rectangle trapClickboxBounds(WorldPoint tile) {
        Rs2TileObjectModel target = trapAt(tile);
        if (target == null) return null;
        Rectangle bounds = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            java.awt.Shape clickbox = target.getClickbox();
            return clickbox == null ? null : clickbox.getBounds();
        }).orElse(null);
        return bounds == null || bounds.width < 3 || bounds.height < 3 ? null : bounds;
    }

    /** Follow the prepared trap's projection while reset movement shifts the camera. */
    public void onClientTick() {
        WorldPoint tile = preparedTrapTile;
        if (tile == null || !Microbot.isLoggedIn() || Microbot.getClient().isMenuOpen()
                || currentState == State.STOPPED || breakRecoveryRequested) {
            return;
        }
        if (!Rs2Player.isMoving()) {
            return;
        }
        Rs2TileObjectModel target = trapAt(tile);
        AutoHunterPlanner.TrapState state = classify(target);
        if (target == null || (state != AutoHunterPlanner.TrapState.CAUGHT
                && state != AutoHunterPlanner.TrapState.FAILED)) {
            clearPreparedTrap();
            return;
        }
        java.awt.Shape shape = target.getClickbox();
        if (shape == null) return;
        Rectangle bounds = shape.getBounds();
        int targetX = bounds.x + bounds.width / 2;
        int targetY = bounds.y + bounds.height / 2;
        if (!shape.contains(targetX, targetY)) return;
        java.awt.Point cursor = Microbot.getMouse().getMousePosition();
        if (cursor == null) return;
        int x = cursor.x + (int) Math.round((targetX - cursor.x) * 0.25);
        int y = cursor.y + (int) Math.round((targetY - cursor.y) * 0.25);
        if (preparedTrapTile != null && preparedTrapTile.equals(tile)) {
            Microbot.getMouse().move(x, y);
            preparedTrapExpiresAt = System.currentTimeMillis() + 3_000;
        }
    }

    private WorldPoint oldestActionableTrapExcluding(WorldPoint excluded) {
        Map<WorldPoint, HunterTrap> timers = hunterPlugin == null ? new HashMap<>()
                : Microbot.getClientThread().runOnClientThreadOptional(
                () -> new HashMap<>(hunterPlugin.getTraps())).orElseGet(HashMap::new);
        return managedTiles.stream()
                .filter(tile -> !tile.equals(excluded))
                .filter(tile -> {
                    AutoHunterPlanner.TrapState state = classify(trapAt(tile));
                    return state == AutoHunterPlanner.TrapState.CAUGHT
                            || state == AutoHunterPlanner.TrapState.FAILED;
                })
                .min(Comparator.comparing(tile -> {
                    HunterTrap timer = timers.get(tile);
                    return timer == null ? java.time.Instant.MAX : timer.getPlacedOn();
                })).orElse(null);
    }

    private Rs2TileObjectModel trapAt(WorldPoint tile) {
        return Microbot.getRs2TileObjectCache().query().within(tile, 0)
                .where(object -> classify(object) != AutoHunterPlanner.TrapState.UNKNOWN).first();
    }

    private boolean hasAnyObjectAt(WorldPoint tile) {
        return Microbot.getRs2TileObjectCache().query().within(tile, 0)
                .where(this::isTrapOrNamedObject).count() > 0;
    }

    private boolean isTrapOrNamedObject(Rs2TileObjectModel object) {
        if (classify(object) != AutoHunterPlanner.TrapState.UNKNOWN) return true;
        String name = object.getName();
        return name != null && !name.isEmpty() && !"null".equalsIgnoreCase(name);
    }

    private AutoHunterPlanner.TrapState classify(Rs2TileObjectModel object) {
        if (object == null) return AutoHunterPlanner.TrapState.UNKNOWN;
        ObjectComposition composition = object.getObjectComposition();
        return composition == null
                ? AutoHunterPlanner.TrapState.UNKNOWN
                : AutoHunterPlanner.classifyActions(composition.getActions());
    }

    private String trapSignature(Rs2TileObjectModel object) {
        if (object == null) return "none";
        ObjectComposition composition = object.getObjectComposition();
        String[] actions = composition == null ? null : composition.getActions();
        return object.getId() + ":" + classify(object) + ":" + Arrays.toString(actions);
    }

    private int countActiveManagedTraps() {
        int count = 0;
        for (WorldPoint tile : managedTiles) {
            Rs2TileObjectModel trap = trapAt(tile);
            String signature = trapSignature(trap);
            String previous = observedTrapSignatures.put(tile, signature);
            if (!signature.equals(previous)) {
                Microbot.log("AutoHunter trap: " + tile + " -> " + signature);
            }
            if (classify(trap) == AutoHunterPlanner.TrapState.ACTIVE) count++;
        }
        return count;
    }

    public void onNpcSpawned(NPC npc) {
        if (npc == null || !AutoHunterPlanner.isRedChinchompaTarget(npc.getId(), npc.getName())
                || startTile == null) return;
        WorldPoint tile = npc.getWorldLocation();
        if (tile == null || tile.getPlane() != startTile.getPlane()) return;
        if (tile.distanceTo(startTile) > huntingRadius || System.currentTimeMillis() < baselineUntil) return;
        SpawnObservation observation = spawnObservations.compute(tile, (ignored, existing) -> {
            if (existing == null) return new SpawnObservation(1, System.currentTimeMillis());
            existing.appearances++;
            existing.lastSeen = System.currentTimeMillis();
            return existing;
        });
        Microbot.log("AutoHunter spawn: " + tile + " appearances=" + observation.appearances);
    }

    private void expireSpawnObservations() {
        long cutoff = System.currentTimeMillis() - SPAWN_EXPIRY_MS;
        spawnObservations.entrySet().removeIf(entry -> entry.getValue().lastSeen < cutoff);
    }

    private void updateSpawnRing(AutoHunterConfig config) {
        WorldPoint player = Rs2Player.getWorldLocation();
        long now = System.currentTimeMillis();
        if (now < nextRingEvaluationAt) return;
        nextRingEvaluationAt = now + 2_000;
        Map.Entry<WorldPoint, SpawnObservation> best = spawnObservations.entrySet().stream()
                .filter(entry -> entry.getValue().appearances >= 2)
                .filter(entry -> startTile == null || entry.getKey().distanceTo(startTile) <= config.huntingRadius())
                .max(Comparator.comparingDouble(entry -> AutoHunterPlanner.spawnScore(
                        entry.getValue().appearances, now - entry.getValue().lastSeen,
                        player.distanceTo(entry.getKey())))).orElse(null);
        bestSpawnTile = best == null ? null : best.getKey();
        spawnSummary = best == null ? "none" : bestSpawnTile + " x" + best.getValue().appearances
                + " score=" + Math.round(AutoHunterPlanner.spawnScore(best.getValue().appearances,
                now - best.getValue().lastSeen, player.distanceTo(bestSpawnTile)));
        if (config.useSpawnRing() && layoutCenter == null && bestSpawnTile != null) {
            List<WorldPoint> preferredLayout = AutoHunterPlanner.fiveDotLayout(bestSpawnTile, trapLimit);
            bestRingTile = preferredLayout.isEmpty() ? null : preferredLayout.get(0);
        }
    }

    private boolean isSafePlacementTile(WorldPoint tile, boolean allowManagedTile) {
        if (tile == null || (!allowManagedTile && managedTiles.contains(tile))) return false;
        if (startTile == null || tile.getPlane() != startTile.getPlane()
                || tile.distanceTo(startTile) > huntingRadius) return false;
        if (hasAnyObjectAt(tile)) return false;
        if (Microbot.getRs2TileItemCache().query().withId(ItemID.BOX_TRAP).within(tile, 0).count() > 0) return false;
        Rs2PlayerModel localPlayer = Rs2Player.getLocalPlayer();
        if (Rs2Player.getPlayers(player -> tile.equals(player.getWorldLocation())
                && (localPlayer == null || player.getId() != localPlayer.getId())).findAny().isPresent()) return false;
        return Rs2Tile.isWalkable(tile) && Rs2Tile.isTileReachable(tile);
    }

    private void stopSafely(String reason) {
        stopReason = reason;
        pending = null;
        moveTarget = null;
        clearDelayedAction();
        transition(State.STOPPED, reason);
        Microbot.log("AutoHunter stopped: " + reason);
    }

    private void transition(State state, String action) {
        if (currentState != state || !nextAction.equals(action)) {
            Microbot.log("AutoHunter state: " + currentState + " -> " + state + "; " + action);
        }
        currentState = state;
        nextAction = action;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        releaseBreakRecoveryLock();
        managedTiles.clear();
        layoutSlots.clear();
        spawnObservations.clear();
        observedTrapSignatures.clear();
        pending = null;
        moveTarget = null;
        layoutCenter = null;
        clearDelayedAction();
    }

    public State getCurrentState() { return currentState; }
    public String getNextAction() { return nextAction; }
    public String getStopReason() { return stopReason; }
    public int getManagedTrapCount() { return managedTiles.size(); }
    public int getActiveTrapCount() { return activeTraps; }
    public int getTrapLimit() { return trapLimit; }
    public int getCatches() { return catches; }
    public int getResets() { return resets; }
    public WorldPoint getBestSpawnTile() { return bestSpawnTile; }
    public WorldPoint getBestRingTile() { return bestRingTile; }
    public String getSpawnSummary() { return spawnSummary; }
    public WorldPoint getLayoutCenter() { return layoutCenter; }

    private static final class PendingAction {
        private final Action action;
        private final WorldPoint tile;
        private final String beforeSignature;
        private final int inventoryCount;
        private final int boxTrapCount;
        private final boolean breakRecovery;
        private final long startedAt;
        private boolean sawTransition;
        private WorldPoint preHoverTile;
        private long preHoverCorrectionAt;
        private long nextPreHoverAttemptAt;
        private boolean preHoverComplete;
        private int preHoverFidgetsRemaining;
        private long nextPreHoverFidgetAt;

        private PendingAction(Action action, WorldPoint tile, String beforeSignature,
                              int inventoryCount, int boxTrapCount, boolean breakRecovery,
                              long startedAt, long nextPreHoverAttemptAt) {
            this.action = action;
            this.tile = tile;
            this.beforeSignature = beforeSignature;
            this.inventoryCount = inventoryCount;
            this.boxTrapCount = boxTrapCount;
            this.breakRecovery = breakRecovery;
            this.startedAt = startedAt;
            this.nextPreHoverAttemptAt = nextPreHoverAttemptAt;
        }
    }

    private static final class SpawnObservation {
        private int appearances;
        private long lastSeen;

        private SpawnObservation(int appearances, long lastSeen) {
            this.appearances = appearances;
            this.lastSeen = lastSeen;
        }
    }
}
