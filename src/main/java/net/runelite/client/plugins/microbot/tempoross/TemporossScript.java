package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.tempoross.enums.HarpoonType;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.Microbot.log;

public class TemporossScript extends Script {

    // Version string
    public static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d+)");
    public static final int TEMPOROSS_REGION = 12076;

    // Game state variables

    // ---- Client-thread snapshot ---------------------------------------------------------------
    // The script loop runs on a scheduled executor, and client state (player, widgets, tick,
    // destination, skill levels) must only be read on the client thread. Everything the loop
    // needs is captured once per GameTick by refreshClientSnapshot(), then read as volatiles.
    public static volatile LocalPoint cachedPlayerLocal;
    /** Template-space location (Rs2Player) — lobby/overworld logic only, never instance math. */
    public static volatile WorldPoint cachedPlayerWorld;
    public static volatile LocalPoint cachedDestination;
    public static volatile boolean cachedPlayerExists;
    public static volatile int cachedTick;
    public static volatile int cachedWorld;
    /** NPC index the player is interacting with this tick, -1 when none. */
    public static volatile int cachedInteractingIndex = -1;
    /** NPC id and raw scene location of the interacting NPC — plain data, never the live object. */
    public static volatile int cachedInteractingId = -1;
    public static volatile WorldPoint cachedInteractingWorld;
    public static volatile int cachedFishingLevel = 1;
    public static volatile int cachedAttackLevel = 1;
    public static volatile int cachedAgilityLevel = 1;

    /** The tick an instant douse already went out, so a batch of spawn events sends one click. */
    private static int lastInstantDouseTick = -1;

    /**
     * Sub-tick fire response, CLIENT THREAD (NpcSpawned event). A strike can drop a fire ON the
     * player's tile with roughly one tick to douse before supplies burn, and the 300ms loop
     * cadence could eat most of that. Running from the spawn event puts the Douse click out the
     * same frame the fire comes into existence. Fires further than a tile away are left to the
     * normal hazards-first loop — this path is strictly for the on-top-of-us case.
     */
    public static void onFireSpawned(NPC npc) {
        if (!cachedInMinigame || workArea == null || npc == null) {
            return;
        }
        NPCComposition comp = npc.getComposition();
        if (comp == null || comp.getActions() == null
                || !Arrays.asList(comp.getActions()).contains("Douse")) {
            return;
        }
        Player local = Microbot.getClient().getLocalPlayer();
        LocalPoint playerLocal = local != null ? local.getLocalLocation() : null;
        LocalPoint fireLocal = npc.getLocalLocation();
        if (playerLocal == null || fireLocal == null
                || playerLocal.distanceTo(fireLocal) > 2 * Perspective.LOCAL_TILE_SIZE) {
            return;
        }
        if (!Rs2Inventory.contains(ItemID.BUCKET_OF_WATER)) {
            return;
        }
        int tick = Microbot.getClient().getTickCount();
        if (tick == lastInstantDouseTick) {
            return;
        }
        lastInstantDouseTick = tick;
        log("Fire spawned on top of us — dousing instantly");
        new Rs2NpcModel(npc).click("Douse");
    }

    // ---- Client-thread marshalled conversions -------------------------------------------------
    // Scene<->world conversions consult the client's world view and plane, so each runs as one
    // complete operation on the client thread (invoke executes inline when already there); the
    // executor only ever receives the finished plain coordinate.
    private static LocalPoint localFromWorld(WorldPoint point) {
        if (point == null) {
            return null;
        }
        return Microbot.getClientThread().invoke(() -> LocalPoint.fromWorld(Microbot.getClient(), point));
    }

    private static WorldPoint worldFromLocal(LocalPoint point) {
        if (point == null) {
            return null;
        }
        return Microbot.getClientThread().invoke(() -> WorldPoint.fromLocal(Microbot.getClient(), point));
    }

    private static WorldPoint templateFromLocal(LocalPoint point) {
        if (point == null) {
            return null;
        }
        return Microbot.getClientThread().invoke(() -> WorldPoint.fromLocalInstance(Microbot.getClient(), point));
    }

    /** CLIENT THREAD ONLY — called first thing from the plugin's GameTick subscriber. */
    public static void refreshClientSnapshot() {
        Player local = Microbot.getClient().getLocalPlayer();
        cachedPlayerExists = local != null;
        cachedPlayerLocal = local != null ? local.getLocalLocation() : null;
        cachedPlayerWorld = local != null ? Rs2Player.getWorldLocation() : null;
        cachedDestination = Microbot.getClient().getLocalDestinationLocation();
        cachedTick = Microbot.getClient().getTickCount();
        cachedWorld = Microbot.getClient().getWorld();
        Actor interacting = local != null ? local.getInteracting() : null;
        if (interacting instanceof NPC) {
            NPC interactingNpc = (NPC) interacting;
            cachedInteractingIndex = interactingNpc.getIndex();
            cachedInteractingId = interactingNpc.getId();
            cachedInteractingWorld = interactingNpc.getWorldLocation();
        } else {
            cachedInteractingIndex = -1;
            cachedInteractingId = -1;
            cachedInteractingWorld = null;
        }
        cachedFishingLevel = Rs2Player.getRealSkillLevel(Skill.FISHING);
        cachedAttackLevel = Rs2Player.getRealSkillLevel(Skill.ATTACK);
        cachedAgilityLevel = Rs2Player.getRealSkillLevel(Skill.AGILITY);
    }

    public static volatile int ENERGY;
    public static volatile int INTENSITY;
    public static volatile int ESSENCE;

    public static volatile TemporossConfig temporossConfig;
    public static volatile State state = State.INITIAL_CATCH;
    public static volatile TemporossWorkArea workArea = null;
    public static volatile boolean isFilling = false;
    public static boolean isFightingFire = false;
    public static HarpoonType harpoonType;
    // Set only when the configured harpoon genuinely can't be found, and cleared by reset() so the
    // next game retries the user's own harpoon instead of permanently rewriting their config.
    private static HarpoonType harpoonFallback = null;
    public static volatile TemporossNpcSnapshot temporossPool;
    public static volatile List<TemporossNpcSnapshot> sortedFires = Collections.emptyList();
    public static volatile List<TemporossCloudSnapshot> sortedClouds = Collections.emptyList();
    public static volatile List<TemporossNpcSnapshot> fishSpots = Collections.emptyList();
    // Identified by index + id rather than a cached NPC ref, which the client recycles.
    private static int lastCatchSpotIndex = -1;
    private static int lastCatchSpotId = -1;
    public static volatile List<WorldPoint> walkPath = Collections.emptyList();
    public static long startTime;
    // Written on the client thread (GameTick), read on the script executor - volatile like the
    // snapshot fields below, and the lists above are REPLACED whole, never mutated in place.
    public static volatile int cachedRawFish;
    public static volatile int cachedCookedFish;
    public static volatile int cachedAllFish;
    public static volatile int cachedTotalSlots;
    public static volatile boolean cachedInMinigame;

    // Per-game randomized thresholds (regenerated each game for humanization)
    public static volatile int thresholdForfeitIntensity = 94;
    private int thresholdLowEnergy = 2;
    private int thresholdAttackEnergy = 94;
    // Static so State's completion predicates read the same numbers this loop acts on.
    public static volatile int thresholdFullEnergy = 97;
    public static volatile int thresholdLoadEnergy = 49;
    // Strategy opening: 7 fish below 85 Fishing, 9 at 85+ where the extra catches still fit inside
    // the same cook cycle, so the double spot arrives with nothing wasted. Resolved once per game.
    public static volatile int openingCatchTarget = 7;
    /**
     * Third-phase batch size: catch this many, cook them, repeat — unless a double spot is up, in
     * which case keep fishing and fill the bag instead.
     */
    public static final int thirdCatchBatch = 7;
    private static long lastFishSpotDiagnostic = 0;
    private int thresholdEmergencyEnergyLow = 30;
    private int thresholdEmergencyEnergyHigh = 50;
    private int thresholdEmergencyFishMin = 6;

    public boolean run(TemporossConfig config) {
        temporossConfig = config;
        startTime = System.currentTimeMillis();
        ENERGY = 0;
        INTENSITY = 0;
        ESSENCE = 0;
        energyDrainPerTick = 0;
        lastDrainSample = 0;
        lastEnergySeen = -1;
        lastEnergyTick = -1;
        poolPhasesSeen = 0;
        energyRecoveryLatch = false;
        workArea = null;
        TemporossPlugin.incomingWave = false;
        TemporossPlugin.isTethered = false;
        TemporossPlugin.fireClouds = 0;
        TemporossPlugin.waves = 0;
        state = State.INITIAL_CATCH;
        startupHopDone = false;
        startupHopAttempts = 0;
        hopEligibleTick = -1;
        worldHopperPrimed = false;
        // Restart must retry these: the script bean is a singleton, so instance flags survive a
        // plugin stop/start and a stale 'done' skipped auto-equip silently on every restart.
        autoEquipDone = false;
        collectingRewards = false;
        loggedHoldingPermits = false;
        lobbyBankWalkFails = 0;
        equipRejected.clear();
        autoEquipStep = 0;
        autoEquipTries = 0;
        rewardSessionDone = false;
        preCollectionBanked = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->{
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (BreakHandlerScript.isBreakActive() || BreakHandlerScript.isMicroBreakActive()) return;

                if (!cachedInMinigame) {
                    // Every completed run deposits us at the dock IN a dialogue (post-cutscene),
                    // so clear it centrally before any handler tries to click anything.
                    if (dismissDialogue())
                        return;
                    if (handleAutoEquip())
                        return;
                    if (handleRewardCollection())
                        return;
                    // Hop LAST, right before boarding: banking and reward collection are
                    // world-agnostic, and by the time they finish the client is long past the
                    // welcome-banner window that made login-time hops fail. The settle/cooldown
                    // guards stay for the nothing-to-bank case.
                    if (handleStartupWorldHop())
                        return;
                    handleEnterMinigame();
                }
                if (cachedInMinigame) {
                    if (workArea == null) {
                        rewardSessionDone = false;  // fresh game: next lobby visit may collect again
                        loggedEndgameDump = false;
                        cameraPrepped = false;
                        determineWorkArea();
                        sleep(300, 600);
                    } else {
                        if (!cameraPrepped) {
                            cameraPrepped = true;
                            // Whole-side view, once per game: high pitch and a wide zoom keep every
                            // target's tile on screen, so walks almost never need the arrow-key
                            // camera turn (which runs at 3x speed and looks terrible).
                            Rs2Camera.setPitch(383);
                            Rs2Camera.setZoom(200);
                        }
                        if (TemporossPlugin.incomingWave) {
                            handleTether();
                            return;
                        }
                        if (handleWrongSideClick())
                            return;
                        // Hazards BEFORE actions: a fire must be doused the same tick it appears.
                        // With the state loop first, the pass where a fire spawned issued its click
                        // (walking straight into it) before the douse was ever considered —
                        // observed: struck while running to the cannons, doused too late.
                        if (handleCloudDodge())
                            return;
                        if (handleStandingInFire())
                            return;
                        if (handleNearbyFire())
                            return;
                        if (handleFireOnPath())
                            return;
                        handleMinigame();
                        handleStateLoop();
                        // Only wait on missing items while handleMinigame() is still willing to fetch
                        // them, otherwise this returns forever without anything ever restocking.
                        if(shouldFetchSupplies() && areItemsMissing() && (state == State.INITIAL_CATCH || state == State.SECOND_CATCH || state == State.THIRD_CATCH))
                            return;
                        handleFires();
                        handleTether();
                        if(isFightingFire)
                            return;
                        if (handleRepairs())
                            return;
                        if (handleMissingRope())
                            return;
                        handleForfeit();

                        finishGame();
                        handleMainLoop();
                    }
                }
            } catch (Exception e) {
                // Shutdown interrupts the script thread mid client-thread-invoke. Restore the flag
                // and leave quietly rather than dumping a stack trace on every plugin stop.
                if (e instanceof InterruptedException || e.getCause() instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
                log("Error in script: " + e.getMessage());
                e.printStackTrace();
            }

        }, 0, 300, TimeUnit.MILLISECONDS);
        return true;
    }

    private int getPhase() {
        return 1 + (TemporossPlugin.waves / 4); // every 4 waves, phase increases by 1
    }

    /**
     * Late in the game a supply run costs more than the missing item is worth. Both the fetch and
     * the main-loop guard that waits on it read this, so they can never disagree.
     */
    private boolean shouldFetchSupplies() {
        return getPhase() <= 2;
    }

    private static long lastInMinigameMs = 0;

    /**
     * CLIENT THREAD ONLY — reads game state and the local player directly. The plugin calls it
     * from GameTick into {@code cachedInMinigame}; everything on the script executor reads the
     * cache, never this.
     */
    static boolean isInMinigame() {
        // getLocalPlayer() is briefly null right after login even at GameState.LOGGED_IN, and the
        // player-state cache NPEs on it — guard here rather than crash the loop during that window.
        if (Microbot.getClient().getGameState() == GameState.LOGGED_IN
                && Microbot.getClient().getLocalPlayer() != null) {
            WorldPoint loc = Rs2Player.getWorldLocation();
            if (loc != null && loc.getRegionID() == TEMPOROSS_REGION) {
                lastInMinigameMs = System.currentTimeMillis();
                return true;
            }
        }
        // Debounced: a scene reload flashes LOADING (and can serve one stale location read) for a
        // tick. Treating a single bad read as "left the minigame" used to reset() mid-game — wave
        // counter back to zero, work area nulled — and then strand the bot at the shoreline, where
        // the ship NPCs needed to rebuild the work area are beyond NPC render distance. Only a
        // sustained out-of-game signal counts as actually having left.
        return lastInMinigameMs != 0 && System.currentTimeMillis() - lastInMinigameMs < 2000;
    }

    private boolean hasHarpoon() {
        if (harpoonType == HarpoonType.BAREHAND) {
            return true;
        }
        // getIds() also covers the uncharged/inactive forms — an infernal harpoon that ran out of
        // charges is still the harpoon the user brought, and must not trigger the crate fallback.
        int[] ids = harpoonType.getIds();
        return Rs2Inventory.contains(ids) || Rs2Equipment.isWearing(ids);
    }

    private void determineWorkArea() {
        if (workArea == null) {
            LocalPoint playerLocal = cachedPlayerLocal;
            if (playerLocal == null) return;

            List<TemporossNpcSnapshot> forfeitNpcs = snapshotNpcs(() ->
                    Microbot.getRs2NpcCache().query()
                            .where(npc -> npc.getNpc() != null && npc.getNpc().getComposition() != null
                                    && Arrays.asList(npc.getNpc().getComposition().getActions()).contains("Forfeit"))
                            .toList());

            // Same instance as before the reset? Restore the old work area outright — its anchors are
            // static for the instance's lifetime, and restoring needs no walk to re-sight the crate.
            if (previousWorkArea != null) {
                boolean sameInstance = forfeitNpcs.stream().anyMatch(npc ->
                        npc.worldLocation.distanceTo(previousWorkArea.exitNpc) <= TemporossWorkArea.TOTEM_EXIT_MAX_DISTANCE
                        || (previousWorkArea.getTotemExitNpc() != null
                            && npc.worldLocation.distanceTo(previousWorkArea.getTotemExitNpc()) <= TemporossWorkArea.TOTEM_EXIT_MAX_DISTANCE));
                if (sameInstance) {
                    workArea = previousWorkArea;
                    previousWorkArea = null;
                    log("Work area restored after mid-game reset");
                    return;
                }
            }

            TemporossNpcSnapshot forfeitNpc = forfeitNpcs.stream()
                    .filter(npc -> npc.localLocation != null)
                    .min(Comparator.comparingInt(npc -> playerLocal.distanceTo(npc.localLocation)))
                    .orElse(null);

            List<TemporossNpcSnapshot> ammoCrates = snapshotNpcs(() ->
                    Microbot.getRs2NpcCache().query()
                            .where(npc -> npc.getNpc() != null && npc.getNpc().getComposition() != null
                                    && Arrays.asList(npc.getNpc().getComposition().getActions()).contains("Fill"))
                            .toList());
            TemporossNpcSnapshot ammoCrate = ammoCrates.stream()
                    .filter(npc -> npc.localLocation != null)
                    .min(Comparator.comparingInt(npc -> playerLocal.distanceTo(npc.localLocation)))
                    .orElse(null);

            if (forfeitNpc == null || ammoCrate == null) {
                // Mid-game rebuild (only reachable after a reset away from the ship): the crate is an
                // NPC and simply is not rendered from the shoreline. Any visible exit NPC is within
                // ~17 tiles of the ship, so walking to it brings the crate into range.
                log("Can't rebuild work area (Forfeit NPCs visible=" + forfeitNpcs.size()
                        + ", Fill NPCs visible=" + ammoCrates.size() + ")"
                        + (forfeitNpc != null ? " — walking to the visible exit NPC" : ""));
                if (forfeitNpc != null && !Rs2Player.isMoving()) {
                    Rs2Walker.walkFastLocal(forfeitNpc.localLocation);
                }
                return;
            }
            boolean isWest = forfeitNpc.worldLocation.getX() < ammoCrate.worldLocation.getX();
            // The exit NPC's id alone identifies our side and every per-side id with it. Not distance:
            // measured in-game the west ship host sat 17 tiles from its own totem host and 18 from the
            // other side's, so any proximity pairing is a coin flip on a one-tile margin.
            TemporossSide side = TemporossSide.fromHostId(forfeitNpc.id);
            if (side == null) {
                log("Unrecognised exit NPC id " + forfeitNpc.getId() + " — cannot identify side, retrying");
                return;
            }
            // Our side's other exit NPC, matched by id rather than distance.
            WorldPoint totemExit = forfeitNpcs.stream()
                    .filter(npc -> npc.index != forfeitNpc.index)
                    .filter(npc -> npc.id == side.shipHostId || npc.id == side.totemHostId)
                    .map(npc -> npc.worldLocation)
                    .findFirst()
                    .orElse(null);
            workArea = new TemporossWorkArea(forfeitNpc.worldLocation, isWest, totemExit, side);
            previousWorkArea = null;
            permitsAtGameStart = rewardPermits();
            log("Side " + side + " (exit NPC " + forfeitNpc.id + ", spots " + side.fishingSpotId
                    + ", mast " + side.mastId + ", totem " + side.totemId + ") | permits="
                    + permitsAtGameStart);
            log("Other exit NPC: " + (totemExit != null ? totemExit : "not rendered yet, will capture when seen"));
            // Once per game, here rather than in reset() — reset() runs every loop while outside the
            // minigame, which re-rolled and re-logged the thresholds several times a second.
            randomizeThresholds();
            // Camera baseline once per game, not every loop: re-asserting zoom and pitch at 300ms
            // fought any manual camera adjustment for the whole round.
            Rs2Camera.resetZoom();
            Rs2Camera.resetPitch();
            log("Tempoross work area: " + (isWest ? "west" : "east"));
            log("Forfeit NPC at " + forfeitNpc.worldLocation + " | Ammo crate at " + ammoCrate.worldLocation);
            // NPC world locations and the player's are in different coordinate spaces inside the
            // instance. Print both so the offset between them is visible in the log.
            log("Player real loc=" + cachedPlayerWorld
                    + " | player local=" + (cachedPlayerLocal));
            log(workArea.getAllPointsAsString());
        }
    }

    private void finishGame() {
        if (workArea == null) {
            return;
        }
        LocalPoint playerLocal = cachedPlayerLocal;
        if (playerLocal == null) {
            return;
        }
        TemporossNpcSnapshot exitNpc = snapshotNpcs(() -> Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getNpc() != null && npc.getNpc().getComposition() != null
                        && npc.getNpc().getComposition().getActions() != null
                        && Arrays.asList(npc.getNpc().getComposition().getActions()).contains("Leave")
                        && npc.getNpc().getLocalLocation() != null)
                .toList()).stream()
                .min(Comparator.comparingInt(value -> playerLocal.distanceTo(value.localLocation)))
                .orElse(null);
        if (exitNpc != null) {
            int emptyBucketCount = Rs2Inventory.count(ItemID.BUCKET);
            if (emptyBucketCount > 0) {
                TemporossObjectSnapshot pump = snapshotObject(() ->
                        Microbot.getRs2TileObjectCache().query().withId(41004).nearest());
                if(clickObject(pump, "Fill-bucket"))
                    sleepUntil(() -> Rs2Inventory.count(ItemID.BUCKET) < 1);

            }

            if (clickNpc(exitNpc, "Leave", false)) {
                // Reset only once we are demonstrably out. Resetting on the click used to destroy the
                // work area while still standing in the arena whenever boarding was delayed or failed,
                // and the rebuild then stalled because the ammo crate is not rendered from the dock.
                if (sleepUntil(() -> !cachedInMinigame, 15000)) {
                    // Permits land as the game resolves, so read a beat after leaving.
                    sleep(1200);
                    int gained = rewardPermits() - permitsAtGameStart;
                    log("Game over: " + Microbot.getVarbitValue(VARB_CURRENT_POINTS) + " points, +"
                            + gained + " permits (total " + rewardPermits() + ")");
                    reset();
                    BreakHandlerScript.setLockState(false);
                    Rs2Antiban.takeMicroBreakByChance();
                } else {
                    log("Leave click did not get us out, keeping game state and retrying");
                }
            }
        }
    }

    // Stashed by reset() so a spurious mid-game reset can restore instead of rebuilding — the
    // anchors are static for the lifetime of an instance. Validated against a visible exit NPC
    // before restoring, because a NEW game is a new instance with entirely different raw coords.
    private static TemporossWorkArea previousWorkArea = null;

    private void reset(){
        previousWorkArea = workArea;
        ENERGY = 0;
        INTENSITY = 0;
        ESSENCE = 0;
        energyDrainPerTick = 0;
        lastDrainSample = 0;
        lastEnergySeen = -1;
        lastEnergyTick = -1;
        poolPhasesSeen = 0;
        energyRecoveryLatch = false;
        workArea = null;
        isFilling = false;
        isFightingFire = false;
        harpoonFallback = null;
        poolPhaseActive = false;

        lastCatchSpotIndex = -1;
        lastCatchSpotId = -1;
        walkPath = null;
        TemporossPlugin.incomingWave = false;
        TemporossPlugin.isTethered = false;
        TemporossPlugin.fireClouds = 0;
        TemporossPlugin.waves = 0;
        state = State.INITIAL_CATCH;
    }

    private void randomizeThresholds() {
        thresholdForfeitIntensity = Rs2Random.fancyNormalSample(91, 96);
        // Strategy: stage at the spirit pool around 5% energy and wait for it to open — 2-4% proved
        // late (still finishing a catch or a one-fish load when the pool spawned). Then harpoon it
        // back up to 97-98%, and stop catching at 49% so there is time to cook and load before the
        // last wave.
        // Wiki runs at 1-2%; 2-3 keeps a walking margin. The old 4-6 donated catching time.
        thresholdLowEnergy = Rs2Random.fancyNormalSample(2, 3);
        thresholdAttackEnergy = Rs2Random.fancyNormalSample(90, 96);
        thresholdFullEnergy = Math.max(thresholdAttackEnergy + 1, Rs2Random.fancyNormalSample(97, 98));
        thresholdLoadEnergy = Rs2Random.fancyNormalSample(47, 50);
        thresholdEmergencyEnergyLow = Rs2Random.fancyNormalSample(24, 36);
        thresholdEmergencyEnergyHigh = Math.max(thresholdEmergencyEnergyLow + 10, Rs2Random.fancyNormalSample(44, 56));
        thresholdEmergencyFishMin = Rs2Random.fancyNormalSample(4, 8);
        openingCatchTarget = cachedFishingLevel >= 85 ? 9 : 7;
        log("Game thresholds: forfeit=" + thresholdForfeitIntensity
                + " lowE=" + thresholdLowEnergy
                + " attackE=" + thresholdAttackEnergy
                + " fullE=" + thresholdFullEnergy
                + " loadE=" + thresholdLoadEnergy
                + " emergLow=" + thresholdEmergencyEnergyLow
                + " emergHigh=" + thresholdEmergencyEnergyHigh
                + " emergFish=" + thresholdEmergencyFishMin
                + " opening=" + openingCatchTarget);
    }

    public void handleForfeit() {
        if ((INTENSITY >= thresholdForfeitIntensity && state == State.THIRD_COOK)) {
            forfeit();
        }
    }

    private void forfeit() {
        LocalPoint playerLocal = cachedPlayerLocal;
        if (playerLocal == null) return;
        TemporossNpcSnapshot forfeitNpc = snapshotNpcs(() -> Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getNpc() != null && npc.getNpc().getComposition() != null
                        && Arrays.asList(npc.getNpc().getComposition().getActions()).contains("Forfeit"))
                .toList()).stream()
                .filter(npc -> npc.localLocation != null)
                .min(Comparator.comparingInt(npc -> playerLocal.distanceTo(npc.localLocation)))
                .orElse(null);
        if (forfeitNpc != null) {
            if (clickNpc(forfeitNpc, "Forfeit", false)) {
                sleepUntil(() -> !cachedInMinigame, 15000);
                reset();
                BreakHandlerScript.setLockState(false);
            }
        }
    }

    /**
     * Catches clicks that landed on the other ship. Every query is side-filtered, but a click is a
     * canvas event: with extended draw distance (GPU/117 HD) the other ship is rendered and clickable,
     * and a click against a target that moved or despawned in the same tick falls through to whatever
     * stood behind it on screen — across open water, that is the other ship. This cannot be prevented
     * at targeting time, so it is detected and cancelled one loop later.
     */
    private boolean handleWrongSideClick() {
        if (workArea == null) {
            return false;
        }
        WorldPoint targetLoc = cachedInteractingWorld;
        if (targetLoc != null && !workArea.isOnOurSide(targetLoc)) {
            log("Interacting with something on the other side at " + targetLoc + " — cancelling");
            cancelCurrentAction();
            return true;
        }
        LocalPoint dest = cachedDestination;
        WorldPoint destWorld = dest != null ? worldFromLocal(dest) : null;
        if (destWorld != null) {
            // Looser than isOnOurSide on purpose: short hops (cloud and fire dodges) may legally step
            // a few tiles past the anchor radius. 25+ tiles from BOTH anchors is nowhere on our side.
            boolean nearShip = destWorld.distanceTo(workArea.exitNpc) <= 25;
            boolean nearTotem = workArea.getTotemExitNpc() != null
                    && destWorld.distanceTo(workArea.getTotemExitNpc()) <= 25;
            if (!nearShip && !nearTotem) {
                log("Walking toward " + destWorld + ", which is not on our side — stopping");
                cancelCurrentAction();
                return true;
            }
        }
        return false;
    }

    /**
     * Turns the camera only when the target is actually off-screen. Rs2Camera.turnTo unconditionally
     * spins at triple camera speed until the target is within 40 degrees — called before every click,
     * that is a fast janky spin for targets that were already perfectly visible. The wider stop angle
     * also shortens the rotation when one is genuinely needed.
     */
    private static void faceIfNeeded(Actor actor) {
        if (actor == null) {
            return;
        }
        LocalPoint lp = actor.getLocalLocation();
        if (lp != null && Rs2Camera.isTileOnScreen(lp)) {
            return;
        }
        Rs2Camera.turnTo(actor, 70);
    }

    /**
     * Resolves and interacts with an NPC as one client-thread operation. The executor only keeps the
     * immutable identity/location snapshot; the live actor never escapes this callback.
     */
    private static boolean clickNpc(TemporossNpcSnapshot snapshot, String action, boolean face) {
        if (snapshot == null) {
            return false;
        }
        Supplier<Boolean> operation = () -> {
            Rs2NpcModel npc = Microbot.getRs2NpcCache().query()
                    .withId(snapshot.id)
                    .where(candidate -> candidate.getIndex() == snapshot.index)
                    .nearest();
            if (npc == null || npc.getNpc() == null) {
                return false;
            }
            if (face) {
                faceIfNeeded(npc.getNpc());
            }
            return npc.click(action);
        };
        Boolean clicked = Microbot.getClientThread().invoke(operation);
        return Boolean.TRUE.equals(clicked);
    }

    /** Resolves and clicks a tile object without retaining its live RuneLite wrapper. */
    private static boolean clickObject(TemporossObjectSnapshot snapshot, String action) {
        if (snapshot == null) {
            return false;
        }
        Supplier<Boolean> operation = () -> {
            Rs2TileObjectModel object = Microbot.getRs2TileObjectCache().query()
                    .withId(snapshot.id)
                    .where(candidate -> snapshot.worldLocation != null
                            && snapshot.worldLocation.equals(candidate.getWorldLocation()))
                    .nearest();
            return object != null && object.click(action);
        };
        return Boolean.TRUE.equals(Microbot.getClientThread().invoke(operation));
    }

    /** Runs an NPC lookup and copies every result before returning to the executor. */
    private static List<TemporossNpcSnapshot> snapshotNpcs(Supplier<List<Rs2NpcModel>> lookup) {
        Supplier<List<TemporossNpcSnapshot>> operation = () -> lookup.get().stream()
                .filter(Objects::nonNull)
                .map(npc -> new TemporossNpcSnapshot(npc.getId(), npc.getIndex(),
                        npc.getNpc() != null ? npc.getNpc().getLocalLocation() : null,
                        npc.getWorldLocation(), npc.getName()))
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
        return Microbot.getClientThread().invoke(operation);
    }

    /** Runs a tile-object lookup and copies the result before returning to the executor. */
    private static TemporossObjectSnapshot snapshotObject(Supplier<Rs2TileObjectModel> lookup) {
        Supplier<TemporossObjectSnapshot> operation = () -> {
            Rs2TileObjectModel object = lookup.get();
            return object == null ? null : new TemporossObjectSnapshot(
                    object.getId(), object.getLocalLocation(), object.getWorldLocation());
        };
        return Microbot.getClientThread().invoke(operation);
    }

    /** Fires the harpoon special (+3 Fishing) when it is charged and the harpoon is worn. */
    private void maybeUseHarpoonSpec() {
        if (!temporossConfig.enableHarpoonSpec() || harpoonType == null) {
            return;
        }
        if (harpoonType != HarpoonType.DRAGON_HARPOON && harpoonType != HarpoonType.INFERNAL_HARPOON
                && harpoonType != HarpoonType.CRYSTAL_HARPOON) {
            return;
        }
        if (!wearingType(harpoonType)) {
            return;     // the spec needs the harpoon in the weapon slot, carrying is not enough
        }
        // Wiki: the boost has no effect once the catch rate caps — 87+ Fishing for dragon and
        // infernal, 74+ for crystal — so firing it there is a wasted click.
        int specCapLevel = harpoonType == HarpoonType.CRYSTAL_HARPOON ? 74 : 87;
        if (cachedFishingLevel >= specCapLevel) {
            return;
        }
        if (Rs2Combat.getSpecEnergy() / 10 >= 100) {
            Rs2Combat.setSpecState(true, 100);
            sleep(600);
            log("Using harpoon special attack (+3 Fishing)");
        }
    }

    /**
     * Endgame gates, deliberately coarse and meant for tuning by trial and error. The boss does not
     * normally survive two pool phases, so essence at or under half after pool 1 means the NEXT
     * pool kills it; energy under ENERGY_ENDGAME means that pool is only a minute or two away. Both
     * together: everything in the bag goes into the cannon now — by arrival energy is lower still,
     * and fish not loaded when the boss dies score nothing. Essence only falls during pool phases,
     * so the essence half is readable well before the final pool.
     */
    private static final int ESSENCE_ENDGAME = 50;
    private static final int ENERGY_ENDGAME = 30;

    /** One log line per game for the endgame dump, not one per loop pass. */
    private boolean loggedEndgameDump = false;

    /**
     * Mid-walk fire guard. The per-handler fire checks run at CLICK time, and most handlers return
     * early while already moving — so a fire spawning on a committed path was simply run through
     * (observed: dodged a cloud, then ran back through the fresh fires twice). While moving, a live
     * fire near the remaining path is doused as soon as it is within reach; with no water, the walk
     * is broken off so the detour logic can route around it.
     */
    private boolean handleFireOnPath() {
        if (!Rs2Player.isMoving() || sortedFires.isEmpty()) {
            return false;
        }
        LocalPoint from = cachedPlayerLocal;
        LocalPoint dest = cachedDestination;
        if (from == null || dest == null) {
            return false;
        }
        List<LocalPoint> blocking = firesNearLine(from, dest);
        if (blocking.isEmpty()) {
            return false;
        }
        LocalPoint fire = blocking.stream()
                .min(Comparator.comparingInt(from::distanceTo))
                .orElse(null);
        if (fire == null || from.distanceTo(fire) > 6 * Perspective.LOCAL_TILE_SIZE) {
            return false;   // far ahead — re-evaluated every pass as we close in
        }
        if (Rs2Inventory.count(ItemID.BUCKET_OF_WATER) > 0) {
            TemporossNpcSnapshot fireNpc = sortedFires.stream()
                    .filter(f -> fire.equals(f.localLocation))
                    .findFirst()
                    .orElse(null);
            if (clickNpc(fireNpc, "Douse", false)) {
                log("Fire on our path — dousing it mid-walk");
                return true;
            }
        }
        log("Fire on our path and no water — stopping short");
        cancelCurrentAction();
        return true;
    }

    /** Walk-here on our own tile: stops both the current path and any interaction. */
    private void cancelCurrentAction() {
        LocalPoint playerLocal = cachedPlayerLocal;
        if (playerLocal != null) {
            Rs2Walker.walkFastLocal(playerLocal);
        }
    }

    private void handleMinigame()
    {
        // Resolve before the phase gate — hasHarpoon() dereferences this, and starting the plugin
        // mid-game past phase 2 would otherwise leave it null. Detected off what we actually hold,
        // not configured: the harpoon dropdown is gone, auto-equip supplies the best owned.
        harpoonType = temporossConfig.barehanded() ? HarpoonType.BAREHAND
                : harpoonFallback != null ? harpoonFallback : detectOwnedHarpoon();

        if (!shouldFetchSupplies())
            return;

        if (state == State.INITIAL_CATCH || state == State.SECOND_CATCH || state == State.THIRD_CATCH) {
            if (areItemsMissing()) {
                fetchMissingItems();
            }
        }
    }

    private boolean areItemsMissing()
    {
        // Check for harpoon
        if (!hasHarpoon() && harpoonType != HarpoonType.BAREHAND)
        {
            return true;
        }

        // Check bucket counts (empty or full)
        int bucketCount = Rs2Inventory.count(item ->
                item.getId() == ItemID.BUCKET || item.getId() == ItemID.BUCKET_OF_WATER);
        if ((bucketCount < temporossConfig.buckets() && state == State.INITIAL_CATCH) || bucketCount == 0)
        {
            return true;
        }

        // Check full buckets of water
        if (Rs2Inventory.count(ItemID.BUCKET_OF_WATER) <= 0)
        {
            return true;
        }

        // Check for rope
        if (temporossConfig.rope() && !wearingFullSpiritAngler() && !Rs2Inventory.contains(ItemID.ROPE))
        {
            return true;
        }

        // Check for hammer
        return needsInventoryHammer() && !Rs2Inventory.contains(ItemID.HAMMER);
    }

    private void fetchMissingItems()
    {
        LocalPoint playerLocal = cachedPlayerLocal;
        if (playerLocal == null) return;

        List<int[]> needed = new ArrayList<>();

        if (!hasHarpoon() && harpoonType != HarpoonType.BAREHAND) {
            LocalPoint lp = localFromWorld(workArea.harpoonPoint);
            needed.add(new int[]{0, lp != null ? playerLocal.distanceTo(lp) : Integer.MAX_VALUE});
        }

        int bucketCount = Rs2Inventory.count(item ->
                item.getId() == ItemID.BUCKET || item.getId() == ItemID.BUCKET_OF_WATER);
        boolean needBuckets = (bucketCount < temporossConfig.buckets() && state == State.INITIAL_CATCH) || bucketCount == 0;
        if (needBuckets) {
            LocalPoint lp = localFromWorld(workArea.bucketPoint);
            needed.add(new int[]{1, lp != null ? playerLocal.distanceTo(lp) : Integer.MAX_VALUE});
        }

        int fullBucketCount = Rs2Inventory.count(ItemID.BUCKET_OF_WATER);
        if (!needBuckets && fullBucketCount <= 0) {
            LocalPoint lp = localFromWorld(workArea.pumpPoint);
            needed.add(new int[]{2, lp != null ? playerLocal.distanceTo(lp) : Integer.MAX_VALUE});
        }

        if (temporossConfig.rope() && !wearingFullSpiritAngler() && !Rs2Inventory.contains(ItemID.ROPE)) {
            LocalPoint lp = localFromWorld(workArea.ropePoint);
            needed.add(new int[]{3, lp != null ? playerLocal.distanceTo(lp) : Integer.MAX_VALUE});
        }

        if (needsInventoryHammer() && !Rs2Inventory.contains(ItemID.HAMMER)) {
            LocalPoint lp = localFromWorld(workArea.hammerPoint);
            needed.add(new int[]{4, lp != null ? playerLocal.distanceTo(lp) : Integer.MAX_VALUE});
        }

        if (needed.isEmpty()) return;

        // Sort by distance, fetch closest
        needed.sort(Comparator.comparingInt(a -> a[1]));
        int closest = needed.get(0)[0];

        switch (closest) {
            case 0: // Harpoon
                harpoonFallback = HarpoonType.HARPOON;
                harpoonType = harpoonFallback;
                log("Missing selected harpoon, falling back to a crate harpoon for this game");
                fightFiresInPath(workArea.harpoonPoint);
                TemporossObjectSnapshot harpoonCrate = workArea.getHarpoonCrate();
                if (clickObject(harpoonCrate, "Take")) {
                    log("Taking harpoon");
                    sleepUntil(() -> hasHarpoon() || TemporossPlugin.incomingWave, 10000);
                }
                break;
            case 1: // Buckets
                fightFiresInPath(workArea.bucketPoint);
                sleepUntil(() -> Rs2Inventory.count(item ->
                        item.getId() == ItemID.BUCKET || item.getId() == ItemID.BUCKET_OF_WATER) >= temporossConfig.buckets()
                        || TemporossPlugin.incomingWave, () -> {
                    TemporossObjectSnapshot bucketCrate = workArea.getBucketCrate();
                    if (!TemporossPlugin.incomingWave && clickObject(bucketCrate, "Take")) {
                        log("Taking buckets");
                        Rs2Inventory.waitForInventoryChanges(3000);
                    }
                }, 10000, 300);
                break;
            case 2: // Fill buckets
                fightFiresInPath(workArea.pumpPoint);
                TemporossObjectSnapshot pump = workArea.getPump();
                if (clickObject(pump, "Use")) {
                    log("Filling buckets");
                    sleepUntil(() -> Rs2Inventory.count(ItemID.BUCKET) <= 0 || TemporossPlugin.incomingWave, 10000);
                }
                break;
            case 3: // Rope
                fightFiresInPath(workArea.ropePoint);
                TemporossObjectSnapshot ropeCrate = workArea.getRopeCrate();
                if (clickObject(ropeCrate, "Take")) {
                    log("Taking rope");
                    sleepUntil(() -> Rs2Inventory.contains(ItemID.ROPE) || TemporossPlugin.incomingWave, 10000);
                }
                break;
            case 4: // Hammer
                fightFiresInPath(workArea.hammerPoint);
                TemporossObjectSnapshot hammerCrate = workArea.getHammerCrate();
                if (clickObject(hammerCrate, "Take")) {
                    log("Taking hammer");
                    sleepUntil(() -> Rs2Inventory.contains(ItemID.HAMMER) || TemporossPlugin.incomingWave, 10000);
                }
                break;
        }
    }

    private int ineffectivePoolClicks = 0;
    /**
     * True once this pool phase is confirmed — energy hit the staging threshold or the pool NPC was
     * actually seen. Distinguishes "ATTACK entered early via the state chain after the final load"
     * (energy still falling, go fish instead of idling at the mark) from "pool open, energy
     * recharging while we walk" (never bail — that was the 2.6.1 bug).
     */
    private boolean poolPhaseActive = false;
    /** Consecutive ATTACK passes with no pool in sight while energy is past the approach window. */
    private int poolGonePasses = 0;
    /** The once-per-game camera setup (pitch/zoom) has run. */
    private boolean cameraPrepped = false;

    // Once per script start, not per game — leaving a round must not trigger a hop.
    private boolean startupHopDone = false;
    /** First tick a hop attempt is allowed; -1 until the player is first seen. */
    private int hopEligibleTick = -1;
    /** The world switcher panel has been opened ahead of the first attempt. */
    private boolean worldHopperPrimed = false;
    private int startupHopAttempts = 0;

    /**
     * Hops to the configured world before the first game. Only reachable outside the minigame, so a
     * script started mid-game never hops. Gives up after a few failed attempts (world full, member
     * restrictions, …) rather than blocking the session on it.
     */
    private boolean handleStartupWorldHop() {
        if (startupHopDone) {
            return false;
        }
        int target = temporossConfig.world();
        if (target <= 0 || cachedWorld == target) {
            startupHopDone = true;
            return false;
        }
        // Not fully in the world yet (loading): the world switcher cannot open, so an attempt now
        // is a guaranteed failure. Wait, without consuming an attempt.
        if (!cachedPlayerExists) {
            return true;
        }
        // The player existing is NOT enough: the welcome banner keeps initialising for several
        // seconds after login and the switcher cannot open through it — observed burning attempt 1
        // twice. Settle ~5s after the player first appears, and cool down ~6s between attempts.
        if (hopEligibleTick == -1) {
            hopEligibleTick = cachedTick + 8;
        }
        if (cachedTick < hopEligibleTick) {
            return true;
        }
        // Microbot.hopToWorld opens the world switcher and requests the hop in ONE client-thread
        // pass — the panel opens asynchronously, so the request is dropped before the switcher
        // exists and attempt 1 failed every single time (attempt 2 worked because attempt 1 left
        // the panel open). Prime the switcher a couple of ticks ahead instead.
        if (!worldHopperPrimed) {
            worldHopperPrimed = true;
            Microbot.getClientThread().invoke(() -> Microbot.getClient().openWorldHopper());
            hopEligibleTick = cachedTick + 3;
            return true;
        }
        if (startupHopAttempts >= 4) {
            log("World hop to " + target + " failed " + startupHopAttempts + " times, continuing on world "
                    + cachedWorld);
            startupHopDone = true;
            return false;
        }
        startupHopAttempts++;
        log("Hopping to world " + target + " (attempt " + startupHopAttempts + ")");
        boolean hopIssued = Microbot.hopToWorld(target);
        // Cooldown counts from the attempt's END — armed before it, the ~6s the call itself takes
        // had already burned the window and retries fired back to back.
        hopEligibleTick = cachedTick + 10;
        if (hopIssued) {
            if (sleepUntil(() -> Microbot.isLoggedIn() && cachedWorld == target, 20000)) {
                startupHopDone = true;
            }
        }
        return true;
    }

    /** Reward permits held. Verified live against the agent server. */
    public static final int VARB_REWARD_PERMITS = 11936;
    /** Points scored in the current game. */
    public static final int VARB_CURRENT_POINTS = 11897;
    private static final int SMALL_FISHING_NET = 303;
    private static final int SPIRIT_ANGLER_NPC = 10605;
    /**
     * The reward pool: base id 41356 plus nine numbered variants 41296-41304
     * (`tempoross_rewardpool_0` .. `_8`). Its appearance tracks how many permits are stored, and the
     * two sources disagree on which id is current — with 26 permits the object cache returned the
     * base 41356 while the in-game menu reported 41300 (`_4`). Matching all ten is correct whichever
     * one a given permit count produces.
     */
    private static final int[] REWARD_POOL_IDS = {
            41356, 41296, 41297, 41298, 41299, 41300, 41301, 41302, 41303, 41304
    };
    /** Stop collecting with this much room left, so a full bag never strands the next game. */
    private static final int MIN_FREE_SLOTS = 3;

    public static int rewardPermits() {
        return Microbot.getVarbitValue(VARB_REWARD_PERMITS);
    }

    /** Permits held when the current game started, so the per-game gain can be reported. */
    public static int permitsAtGameStart = 0;

    private boolean loggedHoldingPermits = false;
    /** True from the moment a collection run starts until the permits are gone. */
    private boolean collectingRewards = false;
    /** Bank chest beside the reward pool. */
    private static final int LOBBY_BANK_CHEST = 41315;

    /**
     * Drops the net once collecting is done. Dropped rather than banked or carried: the Spirit Angler
     * hands out a fresh one every time, so banking them just stockpiles hundreds, and carrying one
     * into a game costs an inventory slot that should be holding fish.
     */
    private void dropNetIfHeld() {
        if (Rs2Inventory.contains(SMALL_FISHING_NET)) {
            log("Done collecting — dropping the small net");
            Rs2Inventory.drop(SMALL_FISHING_NET);
            sleepUntil(() -> !Rs2Inventory.contains(SMALL_FISHING_NET), 3000);
        }
    }

    /**
     * Banks the collected rewards, keeping only what the next game needs: the configured harpoon,
     * our buckets (both empty and full), a rope and a hammer. Everything else — the fish the pool
     * just paid out — goes in.
     *
     * @return true while banking
     */
    /**
     * The tile directly in front of the lobby bank chest (real overworld coordinates — the Ruins of
     * Unkah dock is NOT instanced, so the global pathfinder is safe here, unlike inside the game).
     */
    private static final WorldPoint LOBBY_BANK_TILE = new WorldPoint(3156, 2836, 0);
    /** Consecutive failed pathing attempts toward the bank tile before giving up. */
    private int lobbyBankWalkFails = 0;

    /**
     * Opens the lobby bank chest, walking to {@link #LOBBY_BANK_TILE} first when the chest is not in
     * the scene or too far to click reliably. Lets the script start anywhere: it heads for the dock
     * and interacts once in range.
     *
     * @return true while still working (walking, clicking, waiting for the interface); false when the
     *         bank is open, or when pathing failed repeatedly and the caller should stop waiting
     */
    private boolean openLobbyBank() {
        if (Rs2Bank.isOpen()) {
            return false;
        }
        TemporossObjectSnapshot chest = snapshotObject(() ->
                Microbot.getRs2TileObjectCache().query().withId(LOBBY_BANK_CHEST).nearest());
        if (chest == null || cachedPlayerWorld == null
                || cachedPlayerWorld.distanceTo(LOBBY_BANK_TILE) > 10) {
            // Out of scene, or in scene but far enough that a canvas click on the chest is a gamble.
            if (!Rs2Player.isMoving()) {
                if (lobbyBankWalkFails >= 5) {
                    return false;
                }
                log("Walking to the lobby bank");
                if (!Rs2Walker.walkTo(LOBBY_BANK_TILE)) {
                    lobbyBankWalkFails++;
                }
            }
            return true;
        }
        lobbyBankWalkFails = 0;
        if (Rs2Player.isMoving()) {
            return true;
        }
        if (clickObject(chest, "Use")) {
            sleepUntil(Rs2Bank::isOpen, 8000);
        }
        return true;
    }

    /** Everything a collection session keeps out of the bank; all else is loot to deposit. */
    private List<Integer> rewardKeepList() {
        List<Integer> keep = new ArrayList<>();
        keep.add(ItemID.BUCKET);
        keep.add(ItemID.BUCKET_OF_WATER);
        keep.add(ItemID.ROPE);
        // Only worth carrying when nothing is worn for repairs — otherwise it is a wasted fish slot.
        if (needsInventoryHammer()) {
            keep.add(ItemID.HAMMER);
        }
        // Kept only for the rest of this collection session, so mid-session banking does not force a
        // trip back to the Angler. It gets dropped once collecting finishes.
        keep.add(SMALL_FISHING_NET);
        // Only the harpoon actually in use stays aboard — spare and downgraded ones are loot to
        // deposit, which is what lets the auto-equip sweep and reward banking clear them out.
        if (!temporossConfig.barehanded()) {
            for (int id : detectOwnedHarpoon().getIds()) {
                keep.add(id);
            }
        }
        return keep;
    }

    /** Anything in the bag that is not part of the working loadout — i.e. rewards worth banking. */
    private boolean hasLootToBank() {
        List<Integer> keep = rewardKeepList();
        return Rs2Inventory.all().stream()
                .anyMatch(item -> item != null && !keep.contains(item.getId()));
    }

    private boolean bankRewards() {
        if (Rs2Bank.isOpen()) {
            log("Banking rewards, keeping harpoon/buckets/rope/hammer");
            Rs2Bank.depositAllExcept(rewardKeepList().toArray(new Integer[0]));
            sleepUntil(() -> !hasLootToBank(), 5000);
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
            return true;
        }
        if (!openLobbyBank()) {
            log("Cannot reach the lobby bank — cannot bank rewards");
            return false;
        }
        return true;
    }

    /**
     * Dismisses a blocking chat dialogue. NPC clicks are inert while one is up, and the click
     * helper auto-walk then spams "walk rejected: null target" retrying — observed live, stuck in
     * dialogue at the Spirit Angler while re-clicking Take-net every pass.
     */
    private boolean dismissDialogue() {
        if (Rs2Dialogue.isInDialogue()) {
            Rs2Dialogue.clickContinue();
            return true;
        }
        return false;
    }

    /**
     * Spends permits at the reward pool between games.
     *
     * <p>Only reachable outside the minigame. The reward table is rolled from BASE Fishing level at
     * the moment of collection — not when the permits were earned, and boosts do not count — so
     * holding them until a higher level is strictly better, and up to 8000 rolls can be stored.
     *
     * <p>Session shape: deposit whatever we are holding so the whole bag is free, take a net,
     * big-search; a full bag is banked and — in drain mode — collection resumes until the permits
     * hit zero. EVERY exit path banks the loot first: the first version finished with "all permits
     * spent" and boarded the next game with a full inventory of rewards.
     *
     * @return true while collecting, so the caller does not board mid-collection
     */
    private boolean handleRewardCollection() {
        if (!temporossConfig.collectRewards()) {
            return false;
        }
        int permits = rewardPermits();
        int fishing = cachedFishingLevel;

        // The threshold gates STARTING, never continuing. Re-checking it every loop meant the first
        // permit spent dropped us under it and we boarded the boat mid-search. Once started, drain
        // to zero.
        if (!collectingRewards) {
            if (rewardSessionDone) {
                return false;       // dead-ended this visit (bank unreachable) — wait for a new game
            }
            // permits > 0 is required regardless of the threshold: with the threshold at 0 the
            // bare comparison started a zero-permit session every loop pass while in the lobby
            // (Collecting 0 permits / All permits spent, forever). Threshold 0 now simply means
            // "collect whenever there is anything to collect".
            if (permits <= 0 || permits < temporossConfig.permitThreshold()) {
                dropNetIfHeld();
                return false;
            }
            if (fishing < temporossConfig.minFishingLevel()) {
                if (!loggedHoldingPermits) {
                    loggedHoldingPermits = true;
                    log("Holding " + permits + " permits until Fishing " + temporossConfig.minFishingLevel()
                            + " (currently " + fishing + ") — rewards roll at collection time");
                }
                dropNetIfHeld();
                return false;
            }
            loggedHoldingPermits = false;
            collectingRewards = true;
            preCollectionBanked = false;
            log("Collecting " + permits + " permits at Fishing " + fishing);
        }

        // Never disturb a running big-search (one continuous animation that stops on its own when
        // the permits or the bag run out) or a walk already in progress.
        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
            return true;
        }

        // A chat dialogue freezes every click, including the Take-net retry loop — clear it first.
        if (dismissDialogue()) {
            return true;
        }

        // The session opens with a deposit so the whole bag is free for rewards.
        if (!preCollectionBanked) {
            if (hasLootToBank()) {
                if (bankRewards()) {
                    return true;
                }
                log("Bank unreachable — collecting with the space we have");
            }
            preCollectionBanked = true;
        }

        // A full bag always gets banked, then collection resumes until the permits hit zero.
        if (Rs2Inventory.emptySlotCount() < MIN_FREE_SLOTS) {
            if (bankRewards()) {
                return true;
            }
            log("Bank unreachable with a full inventory — stopping collection");
            rewardSessionDone = true;       // do not restart straight into the same dead end
            collectingRewards = false;
            dropNetIfHeld();
            return false;
        }

        // Drained. Bank the remainder, then hand the loop back.
        if (permits <= 0) {
            if (hasLootToBank() && bankRewards()) {
                return true;
            }
            log("All permits spent");
            collectingRewards = false;
            dropNetIfHeld();
            // The reward pool drops NO outfit pieces (verified against the wiki loot table — the
            // Spirit Angler set on that page is just the navbox), but its uniques include a DRAGON
            // HARPOON, and any drop was just banked with the loot — un-latch auto-equip so a
            // weapon upgrade goes on before boarding.
            autoEquipDone = false;
            autoEquipStep = 0;
            autoEquipTries = 0;
            return false;
        }

        // The pool needs a small net; the Spirit Angler hands them out.
        if (!Rs2Inventory.contains(SMALL_FISHING_NET)) {
            TemporossNpcSnapshot angler = snapshotNpcs(() ->
                    Microbot.getRs2NpcCache().query().withId(SPIRIT_ANGLER_NPC).toList())
                    .stream().findFirst().orElse(null);
            if (angler == null) {
                log("Spirit Angler not in range — cannot take a net");
                return false;
            }
            if (clickNpc(angler, "Take-net", false)) {
                log("Taking a small fishing net");
                sleepUntil(() -> Rs2Inventory.contains(SMALL_FISHING_NET), 5000);
            }
            return true;
        }

        TemporossObjectSnapshot pool = snapshotObject(() ->
                Microbot.getRs2TileObjectCache().query().withIds(REWARD_POOL_IDS).nearest());
        if (pool == null) {
            log("Reward pool not in range");
            return false;
        }
        if (clickObject(pool, "Big-search")) {
            // Logs the resolved pool id next to the permit count. The pool presents one of ten ids
            // (base 41356, or 41296-41304) and the id tracks stored permits, but the thresholds are
            // unverified — 26 permits presenting as 41300 is the only pairing measured so far. Every
            // collection prints another pairing, so the mapping falls out of normal use.
            log("Big-search at the reward pool (id " + pool.id + ", " + permits
                    + " permits, Fishing " + fishing + ")");
            // Only wait for the animation to START. The guard above then lets it run to completion
            // on its own — waiting here for permits to tick down returned after the first one and
            // put us straight back into a re-click.
            sleepUntil(Rs2Player::isAnimating, 5000);
        }
        return true;
    }

    /** Latched when collection dead-ends (bank unreachable); cleared when the next game starts. */
    private boolean rewardSessionDone = false;
    /** The session-opening deposit has run (or the bank was unreachable and we gave up on it). */
    private boolean preCollectionBanked = false;

    private boolean autoEquipDone = false;

    /**
     * An equipped Imcando hammer (off-hand) repairs from the equipment slot, so it makes the
     * inventory hammer redundant — and that freed slot holds a fish, which is points, which is
     * permits. It therefore overrides the hammer config rather than sitting alongside it.
     */
    private static boolean hasImcandoOffhand() {
        return Rs2Equipment.isWearing(29775);
    }

    /** Can we repair at all, from either source? */
    private static boolean canRepair() {
        return hasImcandoOffhand() || Rs2Inventory.contains(ItemID.HAMMER);
    }

    /** Do we need to carry a hammer? Only when repairs are wanted and nothing is worn for it. */
    private boolean needsInventoryHammer() {
        return temporossConfig.hammer() && !hasImcandoOffhand();
    }

    /**
     * Fits us out from the lobby bank, once per script start: deposit worn items, deposit the whole
     * inventory, then withdraw exactly the loadout — best outfit per slot, harpoon, buckets
     * (pre-filled with water where the bank has them), rope and hammer per the same rules the
     * in-game supply logic uses.
     *
     * <p>The clean slate is the point: the previous version diffed per slot and equipped upgrades,
     * but whatever got displaced (a graceful set, in practice) rode along in the inventory into the
     * game as dead fish slots.
     *
     * @return true while still working, so the caller does not board mid-equip
     */
    private boolean handleAutoEquip() {
        if (!temporossConfig.autoEquip() || autoEquipDone) {
            return false;
        }
        if (!Rs2Bank.isOpen()) {
            if (!openLobbyBank()) {
                log("Cannot reach the lobby bank — skipping auto-equip");
                autoEquipDone = true;
                return false;
            }
            return true;
        }

        // One step per pass. Every step caps its attempts so an empty bank tab or a failed widget
        // click can only stall briefly, never wedge the loop the way the old harpoon retry did.
        if (++autoEquipTries > 8) {
            log("Auto-equip step " + autoEquipStep + " not completing — moving on");
            advanceEquipStep();
        }

        switch (autoEquipStep) {
            case 0:     // deposit only what the loadout does not keep — no stripping, no churn.
                // Wintertodt-manager style: worn gear is never blanket-deposited (gear already at
                // its best tier stays exactly where it is), and supplies already in the bag skip
                // the old deposit-then-rewithdraw round trip.
                if (hasLootToBank()) {
                    Rs2Bank.depositAllExcept(rewardKeepList().toArray(new Integer[0]));
                    sleepUntil(() -> !hasLootToBank(), 3000);
                }
                if (!hasLootToBank()) {
                    advanceEquipStep();
                }
                return true;

            case 1:     // best outfit piece per slot, one equip per pass
                for (TemporossGear gear : TemporossGear.values()) {
                    for (int id : gear.getTiers()) {
                        // Interleaved best-to-worst walk. Wearing THIS tier means the slot is at
                        // this tier or better — stop. A banked copy seen first is an upgrade over
                        // whatever sits below it (checking worn tiers up front instead used to
                        // block a Spirit piece while an Angler one was worn).
                        if (Rs2Equipment.isWearing(id)) {
                            break;
                        }
                        if (equipRejected.contains(id) || !Rs2Bank.hasItem(id)) {
                            continue;
                        }
                        log("Equipping best " + gear.getLabel() + " (item " + id + ")");
                        Rs2Bank.withdrawAndEquip(id);
                        if (sleepUntil(() -> Rs2Equipment.isWearing(id), 3000)) {
                            autoEquipTries = 0;     // progress — do not count toward the cap
                        } else {
                            equipRejected.add(id);
                            log("Could not equip " + id + " — skipping it");
                        }
                        return true;
                    }
                }
                advanceEquipStep();
                return true;

            case 2: {   // harpoon — hold the best owned tier: wielded when possible, else carried
                if (temporossConfig.barehanded()) {
                    advanceEquipStep();
                    return true;
                }
                int wield = -1;
                int carry = -1;
                boolean settled = false;
                // Same interleaved walk as the gear slots, over held AND banked: holding a tier
                // settles the step, a banked better tier is an upgrade even over a harpoon already
                // in hand (a carried plain one used to block a banked infernal forever). Stat-gated
                // tiers still fish from the inventory, so they are carried, not skipped.
                for (HarpoonType type : HARPOON_TIERS) {
                    if (!canUse(type)) {
                        continue;   // a tool we lack the Fishing level for catches nothing
                    }
                    if (wearingType(type) || carriedType(type)) {
                        settled = true;
                        break;
                    }
                    int banked = firstBanked(type.getIds());
                    if (banked == -1) {
                        continue;
                    }
                    if (canWield(type)) {
                        wield = banked;
                    } else {
                        carry = banked;
                    }
                    break;
                }
                if (settled) {
                    advanceEquipStep();
                    return true;
                }
                if (wield == -1 && carry == -1) {
                    // Nothing better banked. A plain one only when nothing at all is in hand.
                    if (!wearingAnyHarpoon() && !carriedAnyHarpoon()
                            && !equipRejected.contains(ItemID.HARPOON) && Rs2Bank.hasItem(ItemID.HARPOON)) {
                        carry = ItemID.HARPOON;
                    } else {
                        advanceEquipStep();
                        return true;
                    }
                }
                if (wield != -1) {
                    final int w = wield;
                    log("Equipping harpoon (item " + w + ")");
                    Rs2Bank.withdrawAndEquip(w);
                    if (!sleepUntil(() -> Rs2Equipment.isWearing(w), 3000)) {
                        // Stat-gated (dragon/infernal need 60 Attack, crystal 70). It is in the
                        // inventory now and fishes fine from there — that IS our harpoon.
                        equipRejected.add(w);
                        log("Could not wield " + w + " — carrying it instead");
                    }
                    advanceEquipStep();
                    return true;
                }
                if (carry != -1) {
                    final int c = carry;
                    log("Withdrawing harpoon to carry (item " + c + ")");
                    Rs2Bank.withdrawOne(c);
                    sleepUntil(() -> Rs2Inventory.contains(c), 3000);
                }
                // Bank has none at all: the crate provides one in-game.
                advanceEquipStep();
                return true;
            }

            case 3: {   // buckets — pre-filled water first, empty ones for the remainder
                int want = temporossConfig.buckets();
                if (Rs2Inventory.count(ItemID.BUCKET_OF_WATER) + Rs2Inventory.count(ItemID.BUCKET) >= want) {
                    advanceEquipStep();
                    return true;
                }
                int bankWater = Rs2Bank.count(ItemID.BUCKET_OF_WATER);
                if (bankWater > 0 && Rs2Inventory.count(ItemID.BUCKET_OF_WATER) == 0) {
                    int take = Math.min(want, bankWater);
                    log("Withdrawing " + take + " pre-filled buckets of water");
                    Rs2Bank.withdrawX(ItemID.BUCKET_OF_WATER, take);
                    sleepUntil(() -> Rs2Inventory.count(ItemID.BUCKET_OF_WATER) > 0, 3000);
                    return true;
                }
                int remainder = want - Rs2Inventory.count(ItemID.BUCKET_OF_WATER);
                if (remainder > 0 && Rs2Inventory.count(ItemID.BUCKET) == 0 && Rs2Bank.count(ItemID.BUCKET) > 0) {
                    log("Withdrawing " + remainder + " empty buckets");
                    Rs2Bank.withdrawX(ItemID.BUCKET, remainder);
                    sleepUntil(() -> Rs2Inventory.count(ItemID.BUCKET) > 0, 3000);
                }
                // Short of the target only when the bank ran dry — the crates cover the rest.
                advanceEquipStep();
                return true;
            }

            case 4:     // rope — wanted unless the worn Spirit Angler set makes tethering free.
                // Detected live off the equipment (this step runs after the outfit ones, so a set
                // completed seconds ago already counts). The old config toggle could lie both ways.
                if (temporossConfig.rope() && !wearingFullSpiritAngler()
                        && !Rs2Inventory.contains(ItemID.ROPE) && Rs2Bank.hasItem(ItemID.ROPE)) {
                    log("Withdrawing a rope");
                    Rs2Bank.withdrawOne(ItemID.ROPE);
                    sleepUntil(() -> Rs2Inventory.contains(ItemID.ROPE), 3000);
                }
                advanceEquipStep();
                return true;

            case 5:     // hammer — pointless with the Imcando off-hand equipped
                if (temporossConfig.hammer() && !hasImcandoOffhand()
                        && !Rs2Inventory.contains(ItemID.HAMMER) && Rs2Bank.hasItem(ItemID.HAMMER)) {
                    log("Withdrawing a hammer");
                    Rs2Bank.withdrawOne(ItemID.HAMMER);
                    sleepUntil(() -> Rs2Inventory.contains(ItemID.HAMMER), 3000);
                }
                advanceEquipStep();
                return true;

            case 6:     // sweep — displaced gear, spare harpoons, and a hammer made redundant by
                // the Imcando all go back. The keep-list is recomputed HERE, post-equip, so it
                // reflects what the loadout actually needs now.
                if (hasLootToBank()) {
                    Rs2Bank.depositAllExcept(rewardKeepList().toArray(new Integer[0]));
                    sleepUntil(() -> !hasLootToBank(), 3000);
                }
                advanceEquipStep();
                return true;

            default:    // done
                autoEquipDone = true;
                log("Auto-equip complete");
                Rs2Bank.closeBank();
                sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
                return false;
        }
    }

    /** Which auto-equip step is running, and how many passes the current step has burned. */
    private int autoEquipStep = 0;
    private int autoEquipTries = 0;

    private void advanceEquipStep() {
        autoEquipStep++;
        autoEquipTries = 0;
    }

    /**
     * Harpoon tiers best-first FOR MAX PERMITS, per the wiki's recommended-equipment ranking on
     * Tempoross/Strategies (Solo Cooking + Firefighting (Max Permits)): the infernal is rank 1 —
     * inside Tempoross its passive cooks harpoonfish WITHOUT destroying them (saves ~3 ticks per
     * fish and yields the 65-point ammo for free). Crystal is only +10% here (not its usual 35%),
     * its crystallised fish grant bonus XP not points, and it burns shards even on pool harpooning.
     * Dragon's boost is likewise reduced; barb-tail is a wieldable plain harpoon.
     */
    private static final HarpoonType[] HARPOON_TIERS = {HarpoonType.INFERNAL_HARPOON,
            HarpoonType.CRYSTAL_HARPOON, HarpoonType.DRAGON_HARPOON, HarpoonType.BARBTAIL_HARPOON};

    /** Only these can go in the weapon slot — a plain harpoon and bare hands cannot be wielded. */
    private static boolean isWieldable(HarpoonType type) {
        for (HarpoonType tier : HARPOON_TIERS) {
            if (tier == type) {
                return true;
            }
        }
        return false;
    }

    /**
     * Wieldable AND meets the wield requirements — checked up front (the Wintertodt gear manager's
     * approach) rather than burning a failed equip attempt to find out. Dragon and infernal need
     * 60 Attack, crystal 70; the barb-tail has none. The equip-failure blacklist stays as the net
     * for anything this table does not know about.
     */
    private static boolean canWield(HarpoonType type) {
        if (!isWieldable(type) || !canUse(type)) {
            return false;
        }
        int attack = cachedAttackLevel;
        switch (type) {
            case DRAGON_HARPOON:
            case INFERNAL_HARPOON:
                return attack >= 60;
            case CRYSTAL_HARPOON:
                // Wiki: 71 Fishing, 70 Attack AND 50 Agility to wield.
                return attack >= 70 && cachedAgilityLevel >= 50;
            default:
                return true;
        }
    }

    /**
     * Meets the FISHING requirement to fish with the tool at all — separate from the wield gates.
     * Owning one without the level is rare but real: dragon harpoons are tradeable, and crystal
     * singing can be bypassed with extra shards. Wiki: dragon 61 Fishing to use, crystal 71.
     * The infernal cannot trip this (untradeable, 75 Fishing to craft), gated at 61 like its base.
     */
    private static boolean canUse(HarpoonType type) {
        int fishing = cachedFishingLevel;
        switch (type) {
            case CRYSTAL_HARPOON:
                return fishing >= 71;
            case DRAGON_HARPOON:
            case INFERNAL_HARPOON:
                return fishing >= 61;
            default:
                return true;
        }
    }

    /** The best harpoon actually in hand — worn or carried — by the same ranking; plain when none. */
    private static HarpoonType detectOwnedHarpoon() {
        for (HarpoonType type : HARPOON_TIERS) {
            if (!canUse(type)) {
                continue;
            }
            for (int id : type.getIds()) {
                if (id > 0 && (Rs2Equipment.isWearing(id) || Rs2Inventory.contains(id))) {
                    return type;
                }
            }
        }
        return HarpoonType.HARPOON;
    }

    /** Is this specific harpoon type in the weapon slot (any charge variant)? */
    private static boolean wearingType(HarpoonType type) {
        for (int id : type.getIds()) {
            if (id > 0 && Rs2Equipment.isWearing(id)) {
                return true;
            }
        }
        return false;
    }

    /** Is this specific harpoon type in the inventory (any charge variant)? */
    private static boolean carriedType(HarpoonType type) {
        for (int id : type.getIds()) {
            if (id > 0 && Rs2Inventory.contains(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Items whose equip attempt failed this session (unwieldable, or stat requirements we cannot
     * read from ids — dragon/infernal harpoons need 60 Attack, crystal 70). Retrying them forever
     * wedged the whole pre-game flow before the bank ever opened.
     */
    private final Set<Integer> equipRejected = new HashSet<>();

    /** First id in the list that the bank holds and has not been rejected, or -1. */
    private int firstBanked(int[] ids) {
        for (int id : ids) {
            if (!equipRejected.contains(id) && Rs2Bank.hasItem(id)) {
                return id;
            }
        }
        return -1;
    }

    private boolean wearingAnyHarpoon() {
        for (HarpoonType type : HarpoonType.values()) {
            for (int id : type.getIds()) {
                if (id > 0 && Rs2Equipment.isWearing(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean carriedAnyHarpoon() {
        for (HarpoonType type : HarpoonType.values()) {
            for (int id : type.getIds()) {
                if (id > 0 && Rs2Inventory.contains(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** All four outfit slots in the Spirit Angler tier (the off-hand hammer is not part of the set). */
    private boolean wearingFullSpiritAngler() {
        for (TemporossGear gear : TemporossGear.values()) {
            if (gear == TemporossGear.OFFHAND || gear == TemporossGear.RING) {
                continue;   // only the four outfit slots make the set
            }
            if (!Rs2Equipment.isWearing(gear.getTiers()[0])) {
                return false;
            }
        }
        return true;
    }

    private boolean isOnStartingBoat() {
        TemporossObjectSnapshot startingLadder = snapshotObject(() ->
                Microbot.getRs2TileObjectCache().query().withId(ObjectID.ROPE_LADDER_41305).nearest());
        if (startingLadder == null) {
            log("Failed to find starting ladder");
            return false;
        }
        LocalPoint playerLocal = cachedPlayerLocal;
        LocalPoint ladderLocal = startingLadder.localLocation;
        if (playerLocal == null || ladderLocal == null) return false;
        return playerLocal.getSceneX() < ladderLocal.getSceneX();
    }

    private void handleEnterMinigame() {
        // Reset state variables
        reset();

        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            return;
        }

        // Same protection while boarding: a leftover dialogue makes the ladder click inert.
        if (dismissDialogue()) {
            return;
        }
        TemporossObjectSnapshot startingLadder = snapshotObject(() ->
                Microbot.getRs2TileObjectCache().query().withId(ObjectID.ROPE_LADDER_41305).nearest());
        if (startingLadder == null) {
            log("Failed to find starting ladder");
            return;
        }
        int emptyBucketCount = Rs2Inventory.count(ItemID.BUCKET);
        // If we are east of the ladder, interact with it to get on the boat
        if (!isOnStartingBoat()) {
            if (clickObject(startingLadder,
                    ((emptyBucketCount > 0 && temporossConfig.solo()) || !temporossConfig.solo()) ? "Climb" : "Solo-start")) {
                BreakHandlerScript.setLockState(true);
                sleepUntil(() -> (isOnStartingBoat() || cachedInMinigame), 15000);
                return;
            }
        }

        TemporossObjectSnapshot waterPump = snapshotObject(() ->
                Microbot.getRs2TileObjectCache().query().withId(ObjectID.WATER_PUMP_41000).nearest());

        if (waterPump != null && emptyBucketCount > 0) {
            if (clickObject(waterPump, "Use")) {
                Rs2Player.waitForAnimation(5000);
            }
        }
        sleepUntil(() -> cachedInMinigame, 30000);
    }

    public static void handleWidgetInfo() {
        try {
            Widget energyWidget = Microbot.getClient().getWidget(InterfaceID.TEMPOROSS, 35);
            Widget essenceWidget = Microbot.getClient().getWidget(InterfaceID.TEMPOROSS, 45);
            Widget intensityWidget = Microbot.getClient().getWidget(InterfaceID.TEMPOROSS, 55);

            if (energyWidget == null || essenceWidget == null || intensityWidget == null) {
                if(Rs2AntibanSettings.devDebug)
                    log("Failed to find energy, essence, or intensity widget");
                return;
            }

            Matcher energyMatcher = DIGIT_PATTERN.matcher(energyWidget.getText());
            Matcher essenceMatcher = DIGIT_PATTERN.matcher(essenceWidget.getText());
            Matcher intensityMatcher = DIGIT_PATTERN.matcher(intensityWidget.getText());
            if (!energyMatcher.find() || !essenceMatcher.find() || !intensityMatcher.find())
            {
                if(Rs2AntibanSettings.devDebug)
                    log("Failed to parse energy, essence, or intensity");
                return;
            }

            ENERGY = Integer.parseInt(energyMatcher.group(0));
            ESSENCE = Integer.parseInt(essenceMatcher.group(0));
            INTENSITY = Integer.parseInt(intensityMatcher.group(0));
            trackEnergyDrain();
        } catch (NumberFormatException e) {
            if(Rs2AntibanSettings.devDebug)
                log("Failed to parse energy, essence, or intensity");
        }
    }

    /** Exponential moving average of energy drain in %/tick; <= 0 when unknown. */
    public static volatile double energyDrainPerTick = 0;
    private static volatile int lastEnergySeen = -1;
    private static volatile int lastEnergyTick = -1;
    /**
     * Pool phases completed-or-underway this game. Energy only recharges during a pool phase, so a
     * rise out of the low band IS one — counted from the widget, independent of our own staging
     * flags. Drives the hold-through-pool-1 loading strategy.
     */
    public static volatile int poolPhasesSeen = 0;
    private static volatile boolean energyRecoveryLatch = false;
    /** The newest raw drain sample, unsmoothed. On mass worlds the drain accelerates as the crates
     * fill, and the EMA lags behind — projections use whichever of the two is worse. */
    private static volatile double lastDrainSample = 0;

    /**
     * Learns how fast THIS game drains the boss's energy, sampled from widget changes. Mass worlds
     * vary wildly game to game, which is why the catch cutoff cannot stay a fixed percentage. A
     * rise (pool refill, new game) resets the estimate — the old rate belongs to a dead phase.
     */
    private static void trackEnergyDrain() {
        int tick = cachedTick;
        if (lastEnergySeen < 0 || tick <= lastEnergyTick) {
            lastEnergySeen = ENERGY;
            lastEnergyTick = tick;
            return;
        }
        if (ENERGY == lastEnergySeen) {
            return;     // sample only on change; the widget updates in steps
        }
        if (ENERGY > lastEnergySeen) {
            energyDrainPerTick = 0;
            lastDrainSample = 0;
            if (!energyRecoveryLatch && lastEnergySeen > 0 && lastEnergySeen <= 20) {
                energyRecoveryLatch = true;
                poolPhasesSeen++;
                log("Pool phase " + poolPhasesSeen + " (energy recovering from " + lastEnergySeen + "%)");
            }
        } else {
            energyRecoveryLatch = false;
            double sample = (double) (lastEnergySeen - ENERGY) / (tick - lastEnergyTick);
            lastDrainSample = sample;
            energyDrainPerTick = energyDrainPerTick <= 0 ? sample
                    : 0.7 * energyDrainPerTick + 0.3 * sample;
        }
        lastEnergySeen = ENERGY;
        lastEnergyTick = tick;
    }

    /** Ticks until energy reaches the target at the current drain rate; MAX_VALUE when unknown. */
    public static int ticksUntilEnergy(int targetPercent) {
        if (ENERGY > 0 && ENERGY <= targetPercent) {
            return 0;
        }
        double rate = Math.max(energyDrainPerTick, lastDrainSample);
        if (rate <= 0 || ENERGY <= 0) {
            return Integer.MAX_VALUE;
        }
        return (int) ((ENERGY - targetPercent) / rate);
    }

    public static void updateFireData(){
        List<Rs2NpcModel> allFires = Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getNpc() != null && npc.getNpc().getComposition() != null
                        && Arrays.asList(npc.getNpc().getComposition().getActions()).contains("Douse"))
                .toList();
        LocalPoint playerLocal = cachedPlayerLocal;
        int fireRadius = temporossConfig != null && temporossConfig.solo() ? 35 : 20;
        int fireRadiusLocal = fireRadius * Perspective.LOCAL_TILE_SIZE;
        sortedFires = allFires.stream()
                .filter(y -> {
                    if (playerLocal == null || y.getNpc() == null || y.getNpc().getLocalLocation() == null)
                        return false;
                    // Never our problem if it is burning on the other half of the arena.
                    if (workArea != null && !workArea.isOnOurSide(y.getWorldLocation()))
                        return false;
                    return y.getNpc().getLocalLocation().distanceTo(playerLocal) <= fireRadiusLocal;
                })
                .sorted(Comparator.comparingInt(x -> {
                    if (playerLocal == null || x.getNpc() == null || x.getNpc().getLocalLocation() == null)
                        return Integer.MAX_VALUE;
                    return x.getNpc().getLocalLocation().distanceTo(playerLocal);
                }))
                .map(npc -> new TemporossNpcSnapshot(npc.getId(), npc.getIndex(),
                        npc.getNpc().getLocalLocation(), npc.getWorldLocation(), npc.getName()))
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
        TemporossOverlay.setNpcList(sortedFires);
    }

    /** Lightning shadow, the ground telegraph we have always dodged. */
    private static final int CLOUD_SHADOW = NullObjectID.NULL_41006;
    /**
     * `lightning_shadow_short`. Never watched before. Suspected to be the imminent-strike marker —
     * the dodge firing early is consistent with 41006 being the earlier warning — but unproven, so it
     * is tracked and logged rather than trusted.
     */
    private static final int CLOUD_SHADOW_SHORT = NullObjectID.NULL_41007;

    /** Only the 41007 shadows, when any exist. */
    public static volatile List<TemporossCloudSnapshot> imminentClouds = Collections.emptyList();
    private static long lastCloudDiag = 0;
    /**
     * Telegraph measurement for the 41006 vs 41007 question: first-seen game tick per live shadow,
     * keyed by scene position. Logged on despawn — the lifetime IS the warning time that shadow
     * variant gives before the strike, which is the number the dodge policy needs. Lifetimes ≥100
     * ticks are discarded as cross-game garbage (tick count is client-global and never resets).
     */
    private static final Map<WorldPoint, int[]> cloudBirths = new HashMap<>();
    /** Absolute client tick of the next strike, computed and published by GameTick. */
    private static volatile int cachedSoonestStrikeTick = -1;

    /** Measured live: a 41006 shadow lives 16 game ticks before its strike (5/5 samples). */
    private static final int SHADOW_LIFETIME_TICKS = 16;
    /** Dodge with this many ticks still on the clock — the escape takes 1-2, the rest is margin. */
    private static final int SHADOW_DODGE_MARGIN_TICKS = 4;

    /**
     * Is this shadow about to strike? 41007 ("short") immediately — measured at ~2 ticks of warning.
     * For 41006 the clock is the BATCH deadline, not the shadow's own age: strikes land in
     * synchronized batches, and shadows spawning late in the cycle still pop with the group
     * (measured: 2- and 6-tick lifetimes despawning alongside a batch of 16s). So every shadow
     * becomes imminent together, when the oldest one's 16 ticks are nearly up.
     */
    private static boolean strikeImminent(TemporossCloudSnapshot cloud) {
        if (cloud.id == CLOUD_SHADOW_SHORT) {
            return true;
        }
        int soonest = soonestStrikeTicks();
        return soonest >= 0 && soonest <= SHADOW_DODGE_MARGIN_TICKS;
    }

    /** Ticks until the soonest tracked shadow pops; -1 with none tracked. */
    private static int soonestStrikeTicks() {
        return cachedSoonestStrikeTick < 0 ? -1 : cachedSoonestStrikeTick - cachedTick;
    }

    public static void updateCloudData(){
        List<GameObject> allClouds = Rs2GameObject.getGameObjects().stream()
                .filter(obj -> obj.getId() == CLOUD_SHADOW || obj.getId() == CLOUD_SHADOW_SHORT)
                .collect(Collectors.toList());
        LocalPoint playerLocal = cachedPlayerLocal;
        if (playerLocal == null) {
            sortedClouds = Collections.emptyList();
            imminentClouds = Collections.emptyList();
            return;
        }
        sortedClouds = allClouds.stream()
                .filter(y -> y.getLocalLocation() != null && playerLocal.distanceTo(y.getLocalLocation()) < 30 * 128)
                .sorted(Comparator.comparingInt(x -> playerLocal.distanceTo(x.getLocalLocation())))
                .map(cloud -> new TemporossCloudSnapshot(cloud.getId(), cloud.getLocalLocation(), cloud.getWorldLocation()))
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
        imminentClouds = sortedClouds.stream()
                .filter(c -> c.id == CLOUD_SHADOW_SHORT)
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));

        // Track from the UNfiltered list: a shadow leaving the 30-tile radius is not a despawn.
        int tick = cachedTick;
        Set<WorldPoint> alive = new HashSet<>();
        for (GameObject c : allClouds) {
            WorldPoint pos = c.getWorldLocation();
            alive.add(pos);
            cloudBirths.putIfAbsent(pos, new int[]{c.getId(), tick});
        }
        for (Iterator<Map.Entry<WorldPoint, int[]>> it = cloudBirths.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<WorldPoint, int[]> e = it.next();
            if (!alive.contains(e.getKey())) {
                int lifetime = tick - e.getValue()[1];
                if (lifetime > 0 && lifetime < 100) {
                    log("CLOUD-TELEGRAPH: " + e.getValue()[0] + " lived " + lifetime + " ticks");
                }
                it.remove();
            }
        }
        cachedSoonestStrikeTick = cloudBirths.values().stream()
                .mapToInt(birth -> birth[1] + SHADOW_LIFETIME_TICKS)
                .min()
                .orElse(-1);

        // Diagnostic for the open "dodges too early" question: does 41007 ever appear, and which
        // state actually costs us anything? Tracks INVENTORY, not HP — the strike deals no damage,
        // it destroys fish and supplies, so hitpoints never move and were the wrong signal entirely.
        // Losing cooked fish is losing points, which is losing permits, so this is the real cost.
        if (!sortedClouds.isEmpty() && System.currentTimeMillis() - lastCloudDiag > 1500) {
            lastCloudDiag = System.currentTimeMillis();
            long shortCount = imminentClouds.size();
            log("CLOUDS: " + (sortedClouds.size() - shortCount) + "x41006 " + shortCount + "x41007"
                    + " | onTile=" + onCloudTile(playerLocal)
                    + " adjacent=" + inCloud(playerLocal, 0)
                    + " imminent=" + inImminentCloud(playerLocal)
                    + " nextStrike=" + soonestStrikeTicks() + "t"
                    + " | fish=" + State.getAllFish() + " (" + State.getCookedFish() + " cooked)"
                    + " water=" + Rs2Inventory.count(ItemID.BUCKET_OF_WATER)
                    + " rope=" + (Rs2Inventory.contains(ItemID.ROPE) ? 1 : 0)
                    + " canRepair=" + canRepair());
        }
        TemporossOverlay.setCloudList(sortedClouds);
    }

    /** Standing exactly on a shadow — the strike lands here. */
    public static boolean onCloudTile(LocalPoint point) {
        if (point == null) {
            return false;
        }
        return sortedClouds.stream().anyMatch(c -> c.localLocation != null
                && point.distanceTo(c.localLocation) < Perspective.LOCAL_TILE_SIZE);
    }

    /** On or beside a 41007 shadow, if that id turns out to be the imminent marker. */
    public static boolean inImminentCloud(LocalPoint point) {
        if (point == null || imminentClouds.isEmpty()) {
            return false;
        }
        return imminentClouds.stream().anyMatch(c -> c.localLocation != null
                && point.distanceTo(c.localLocation) <= Perspective.LOCAL_TILE_SIZE);
    }

    // update ammo crate data
    public static void updateAmmoCrateData(){
        LocalPoint mastLocal = localFromWorld(workArea.mastPoint);
        List<Rs2NpcModel> ammoCrates = Microbot.getRs2NpcCache().query()
                .withIds(workArea.side.ammoCrateIdA, workArea.side.ammoCrateIdB)
                .where(npc -> npc.getNpc() != null && npc.getNpc().getComposition() != null
                        && mastLocal != null && npc.getNpc().getLocalLocation() != null
                        && npc.getNpc().getLocalLocation().distanceTo(mastLocal) <= 4 * 128
                        && !inImminentCloudArea(npc.getNpc().getLocalLocation(), 0))
                .toList();
        TemporossOverlay.setAmmoList(ammoCrates.stream()
                .map(npc -> new TemporossNpcSnapshot(npc.getId(), npc.getIndex(),
                        npc.getNpc().getLocalLocation(), npc.getWorldLocation(), npc.getName()))
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList)));
    }

    /**
     * Fills in the totem-side exit NPC if it was outside NPC render distance when the work area was
     * built. The distance gate inside the setter keeps the other side's exits out.
     */
    public static void updateTotemExitAnchor() {
        if (workArea == null || workArea.getTotemExitNpc() != null) {
            return;
        }
        Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getNpc() != null && npc.getNpc().getComposition() != null
                        && npc.getNpc().getComposition().getActions() != null
                        && Arrays.asList(npc.getNpc().getComposition().getActions()).contains("Forfeit")
                        && !npc.getWorldLocation().equals(workArea.exitNpc))
                .toList()
                .forEach(npc -> workArea.setTotemExitNpc(npc.getWorldLocation()));
        if (workArea.getTotemExitNpc() != null) {
            log("Totem-side exit NPC captured: " + workArea.getTotemExitNpc());
        }
    }

    public static void updateFishSpotData(){
        LocalPoint playerLocal = cachedPlayerLocal;
        // Single spots are keyed by side (10565 / 10568) so ours are selected by id, not geometry.
        // The double (10569) is a single shared id that spawns on BOTH sides — measured 40 tiles
        // apart in one game — so it is the one spot type that still needs a position check.
        List<Rs2NpcModel> allSpots = Microbot.getRs2NpcCache().query()
                .withIds(workArea.side.fishingSpotId, NpcID.FISHING_SPOT_10569)
                .toList();
        fishSpots = allSpots.stream()
                .filter(npc -> npc.getId() != NpcID.FISHING_SPOT_10569
                        || workArea.isOnOurSide(npc.getWorldLocation()))
                // Doubles first (worth crossing the boat for), then nearest. Distance must be
                // compared in local/scene space: inside the instance, NPC world locations and
                // Rs2Player.getWorldLocation() are in two different coordinate spaces.
                .sorted(Comparator
                        .comparingInt((Rs2NpcModel npc) -> npc.getId() == NpcID.FISHING_SPOT_10569 ? 0 : 1)
                        .thenComparingInt(npc -> {
                            LocalPoint spotLocal = npc.getNpc() != null ? npc.getNpc().getLocalLocation() : null;
                            return (playerLocal == null || spotLocal == null)
                                    ? Integer.MAX_VALUE : playerLocal.distanceTo(spotLocal);
                        }))
                .map(npc -> new TemporossNpcSnapshot(npc.getId(), npc.getIndex(),
                        npc.getNpc() != null ? npc.getNpc().getLocalLocation() : null,
                        npc.getWorldLocation(), npc.getName()))
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));

        // The cache holding spots that the rangePoint filter then throws away means the work area
        // geometry is wrong, not that the spots are missing. Report both so it is distinguishable.
        if (fishSpots.isEmpty() && !allSpots.isEmpty()
                && System.currentTimeMillis() - lastFishSpotDiagnostic > 5000) {
            lastFishSpotDiagnostic = System.currentTimeMillis();
            Rs2NpcModel nearest = allSpots.stream()
                    .min(Comparator.comparingInt(npc -> npc.getWorldLocation().distanceTo(workArea.exitNpc)))
                    .orElse(null);
            log("FISHSPOTS: " + allSpots.size() + " in cache, none on our side of exit=" + workArea.exitNpc
                    + " | nearest=" + (nearest != null ? nearest.getWorldLocation()
                    + " dist=" + nearest.getWorldLocation().distanceTo(workArea.exitNpc) : "none"));
        }
        TemporossOverlay.setFishList(fishSpots);
    }

    public static void updateLastWalkPath() {
        TemporossOverlay.setLastWalkPath(walkPath);
    }

    /**
     * In solo mode, fires are continuously handled.
     * In mass world mode, this continuous loop is disabled so that fire-fighting
     * is only triggered dynamically when an objective is set.
     */
    private void handleFires() {
        if (TemporossPlugin.incomingWave) {
            return;
        }
        if (sortedFires.isEmpty() || state == State.ATTACK_TEMPOROSS) {
            isFightingFire = false;
            return;
        }
        if (!temporossConfig.solo()) {
            isFightingFire = false;
            return;
        }
        // Without water the Douse click does nothing, and isFightingFire would keep blocking
        // cooking, filling and repairs for the rest of the game.
        if (Rs2Inventory.count(ItemID.BUCKET_OF_WATER) <= 0) {
            isFightingFire = false;
            return;
        }
        isFightingFire = true;
        for (TemporossNpcSnapshot fire : sortedFires) {
            if(isFilling){
                Microbot.log("Filling, skipping fire");
                return;
            }
            // Skip only if already dousing THIS specific fire — matched by the snapshot's
            // interacting index, never by holding the live actor on the executor.
            if (cachedInteractingIndex != -1 && cachedInteractingIndex == fire.index) {
                return;
            }
            if (clickNpc(fire, "Douse", false)) {
                log("Dousing fire");
                sleepUntil(() -> !Rs2Player.isInteracting(), 3000);
                return;
            }
        }
    }

    /**
     * Harpooning the pool is a fixed window that ends when Tempoross recharges — every second spent
     * elsewhere is lost outright, so nothing that involves walking away is worth doing during it.
     */
    private boolean isAttackingSpiritPool() {
        return state == State.ATTACK_TEMPOROSS && temporossPool != null;
    }

    /** Close enough for a Repair click; beyond this we walk to it first. */
    private static final int REPAIR_RANGE = 3 * Perspective.LOCAL_TILE_SIZE;
    /**
     * Furthest we will walk for a repair. Repairs are worth points but not a trek — the totem sits
     * between the range and the boat, so we pass inside this on every cycle anyway. A cap also means
     * a side-test miss can never turn into a walk to the other ship.
     */
    private static final int MAX_REPAIR_WALK = 10 * Perspective.LOCAL_TILE_SIZE;

    private boolean handleRepairs() {
        if (isAttackingSpiritPool() || !temporossConfig.hammer() || !canRepair()) {
            return false;
        }
        return handleDamaged(workArea::getBrokenMast, "mast")
                || handleDamaged(workArea::getBrokenTotem, "totem");
    }

    /**
     * Repairs earn points and keep the tether usable, so walk to the damaged object rather than only
     * fixing it when we happen to already be standing beside it — the totem in particular breaks while
     * we are at the range or the boat and would otherwise never be repaired at all.
     *
     * <p>The lookup is re-run while waiting so the repair is re-clicked if anything interrupted it,
     * instead of standing still for the full timeout.
     */
    private boolean handleDamaged(Supplier<TemporossObjectSnapshot> lookup, String label) {
        TemporossObjectSnapshot damaged = lookup.get();
        if (damaged == null) {
            return false;
        }
        LocalPoint playerLocal = cachedPlayerLocal;
        LocalPoint damagedLocal = damaged.localLocation;
        if (playerLocal == null || damagedLocal == null) {
            return false;
        }

        int distance = playerLocal.distanceTo(damagedLocal);
        if (distance > MAX_REPAIR_WALK) {
            // Too far to be worth it, and far enough to be suspicious. Leave it — we pass close to
            // both the mast and the totem every cycle, so it gets repaired then.
            return false;
        }
        if (distance > REPAIR_RANGE) {
            if (!Rs2Player.isMoving()) {
                log("Walking to the damaged " + label + " at " + damaged.worldLocation
                        + " (" + (distance / Perspective.LOCAL_TILE_SIZE) + " tiles)");
                walkToWorkAreaPoint(damaged.worldLocation, "Damaged " + label);
            }
            return true;
        }

        if (clickObject(damaged, "Repair")) {
            log("Repairing " + label);
            sleepUntil(() -> lookup.get() == null || TemporossPlugin.incomingWave,
                    () -> {
                        TemporossObjectSnapshot stillBroken = lookup.get();
                        if (stillBroken != null && !Rs2Player.isAnimating() && !TemporossPlugin.incomingWave) {
                            clickObject(stillBroken, "Repair");
                        }
                    }, 10000, 1200);
        }
        return true;
    }

    private TemporossObjectSnapshot lockedTether = null;

    /** Distance in tiles between two local points, for logging. */
    private static String tileDistance(LocalPoint a, LocalPoint b) {
        if (a == null || b == null) {
            return "?";
        }
        return String.valueOf(a.distanceTo(b) / Perspective.LOCAL_TILE_SIZE);
    }

    private void handleTether() {
        if (TemporossPlugin.incomingWave != TemporossPlugin.isTethered) {
            if (TemporossPlugin.incomingWave) {
                if (lockedTether == null) {
                    TemporossObjectSnapshot mast = workArea.getMast();
                    TemporossObjectSnapshot totem = workArea.getTotem();
                    lockedTether = workArea.getClosestTether();
                    // Distances in local space. Rs2Player.getWorldLocation() is in template space
                    // while object locations are not, so comparing the two printed a meaningless
                    // ~9800 for both tethers.
                    LocalPoint playerLocal = cachedPlayerLocal;
                    log("Tether decision: mast=" + (mast != null ? mast.worldLocation + " dist=" + tileDistance(playerLocal, mast.localLocation) : "NULL")
                            + " | totem=" + (totem != null ? totem.worldLocation + " dist=" + tileDistance(playerLocal, totem.localLocation) : "NULL")
                            + " | picked=" + (lockedTether != null ? lockedTether.worldLocation : "NULL"));
                }
                if (lockedTether == null) {
                    return;
                }
                ShortestPathPlugin.exit();
                Rs2Walker.setTarget(null);
                clickObject(lockedTether, "Tether");
                log("Tethering");
                sleepUntil(() -> TemporossPlugin.isTethered, () -> clickObject(lockedTether, "Tether"), 8000, Rs2Random.fancyNormalSample(1200, 2800));
            } else {
                lockedTether = null;
            }
        } else if (!TemporossPlugin.incomingWave) {
            lockedTether = null;
        }
    }

    private void handleStateLoop() {
        Supplier<TemporossNpcSnapshot> poolSnapshot = () -> {
            Rs2NpcModel livePool = Microbot.getRs2NpcCache().query().withId(NpcID.SPIRIT_POOL)
                .where(npc -> npc.getNpc() != null && npc.getNpc().getComposition() != null
                        && Arrays.asList(npc.getNpc().getComposition().getActions()).contains("Harpoon")
                        // Selected by the spiritPoolPoint mark, NOT by exit distance: the two docks
                        // face each other across the channel, and both sides' pools measured EXACTLY
                        // 10 tiles from our exit — a tie whose winner flipped with cache order, making
                        // the bot alternate between our pool and theirs. The mark is a mirrored offset
                        // verified twice in-game (1 and 3 tiles off the actual pool).
                        && npc.getWorldLocation().distanceTo(workArea.spiritPoolPoint) <= 6)
                .toList().stream()
                .min(Comparator.comparingInt(x -> workArea.spiritPoolPoint.distanceTo(x.getWorldLocation())))
                .orElse(null);
            return livePool == null ? null : new TemporossNpcSnapshot(livePool.getId(), livePool.getIndex(),
                    livePool.getNpc().getLocalLocation(), livePool.getWorldLocation(), livePool.getName());
        };
        temporossPool = Microbot.getClientThread().invoke(poolSnapshot);
        if (temporossPool != null) {
            poolGonePasses = 0;
        }
        boolean doubleFishingSpot = hasDoubleSpot();

        if (TemporossScript.state == State.INITIAL_COOK && doubleFishingSpot) {
            log("Double fishing spot detected, skipping initial cook");
            TemporossScript.state = TemporossScript.state.next;
        }

        // Late-game strategy: fish the double spot whenever it is up and cook in the gaps between
        // spots. Only worth leaving the range if there is somewhere to put the fish, and never once
        // energy has dropped past the load cutoff — from there the bag has to be cooked and loaded.
        if (TemporossScript.state == State.THIRD_COOK && doubleFishingSpot
                && cachedAllFish < cachedTotalSlots
                // down to the dump gate, not the load cutoff: below ENERGY_ENDGAME the dump owns
                // the bag anyway, so every tick above it with a double up belongs to the double.
                && TemporossScript.ENERGY > ENERGY_ENDGAME) {
            log("Double fishing spot up, interrupting cook to fish it");
            TemporossScript.state = State.THIRD_CATCH;
        }

        // Same rule for the mid-game cook: a double is never ignored while the bag has room.
        if (TemporossScript.state == State.SECOND_COOK && doubleFishingSpot
                && cachedAllFish < cachedTotalSlots
                // down to the dump gate, not the load cutoff: below ENERGY_ENDGAME the dump owns
                // the bag anyway, so every tick above it with a double up belongs to the double.
                && TemporossScript.ENERGY > ENERGY_ENDGAME) {
            log("Double fishing spot up, interrupting cook to fish it");
            TemporossScript.state = State.SECOND_CATCH;
        }

        // Pool-phase detection is energy-based, NOT pool-based: the pool is an NPC ~12 tiles from the
        // ship exit, so from the shoreline it is outside NPC render distance and temporossPool reads
        // null exactly when the pool opens. Energy at or near zero IS the pool phase — head for the
        // dock from any mid/late state and let the pool render on the way. (The early states are
        // excluded because ENERGY is a parsed-widget zero at game start; SECOND_FILL is excluded by
        // design — the final load finishes before the last pool.)
        // SECOND_FILL joins the pool trigger only when the remaining load is scraps — a real bag
        // still finishes loading first (the strategy's final-load rule), but a couple of leftover
        // fish are not worth a cannon trip while the pool opens.
        // Endgame dump: the next pool kills the boss (essence low) and the load window is already
        // here (energy past the cutoff) — anything not in the crate when it dies is wasted. Raw
        // loads for 20 points against 65 cooked, but fish stranded in the bag at round end are
        // worth zero (observed: 13). Overrides catching, cooking, and the double-spot pull.
        if (!temporossConfig.solo() && ESSENCE > 0 && ESSENCE <= ESSENCE_ENDGAME
                && ENERGY > 0 && ENERGY <= ENERGY_ENDGAME
                && cachedAllFish > 0
                && TemporossScript.state != State.EMERGENCY_FILL
                && TemporossScript.state != State.ATTACK_TEMPOROSS) {
            if (!loggedEndgameDump) {
                loggedEndgameDump = true;
                log("Endgame (essence " + ESSENCE + "%, energy " + ENERGY + "%) — dumping "
                        + cachedAllFish + " fish into the crate");
            }
            isFilling = false;
            TemporossScript.state = State.EMERGENCY_FILL;
        }

        boolean fillWithScraps = TemporossScript.state == State.SECOND_FILL && cachedAllFish <= 3;
        if ((TemporossScript.state == State.THIRD_CATCH || TemporossScript.state == State.EMERGENCY_FILL
                || TemporossScript.state == State.INITIAL_FILL || TemporossScript.state == State.THIRD_COOK
                || TemporossScript.state == State.SECOND_CATCH || TemporossScript.state == State.SECOND_COOK
                || fillWithScraps)
            && TemporossScript.ENERGY <= thresholdLowEnergy
            && !temporossConfig.solo()) {
            log("Energy " + TemporossScript.ENERGY + "% — pool phase, heading for the spirit pool");
            poolPhaseActive = true;
            TemporossScript.state = State.ATTACK_TEMPOROSS;
            return;
        }

        if (temporossPool != null && TemporossScript.state != State.SECOND_FILL && TemporossScript.state != State.ATTACK_TEMPOROSS && TemporossScript.ENERGY < thresholdAttackEnergy) {
            log("Tempoross pool detected, attacking Tempoross");
            poolPhaseActive = true;
            TemporossScript.state = State.ATTACK_TEMPOROSS;
            return;
        }

        // The low-energy arms never fire before pool 1 (the round cannot end there — essence
        // starts full — so held fish are safe, and early loading defeats the hold). A full,
        // FULLY-COOKED bag is the exception in every cycle: it has nothing left to do at any
        // energy, and loading it then refilling beats standing idle (observed: full after the
        // third batch pre-pool, a minute from the pool, waiting). Full with raw cooks first.
        if (((TemporossScript.ENERGY < thresholdEmergencyEnergyLow && cachedAllFish > thresholdEmergencyFishMin
                && poolPhasesSeen > 0)
            || (cachedAllFish >= cachedTotalSlots && cachedRawFish == 0)
            || (TemporossScript.ENERGY < thresholdEmergencyEnergyHigh && cachedAllFish >= cachedTotalSlots
                && poolPhasesSeen > 0))
            && !temporossConfig.solo()
            && TemporossScript.state != State.ATTACK_TEMPOROSS
            && TemporossScript.state != State.EMERGENCY_FILL) {
            log("Low energy, going for emergency fill");
            TemporossScript.state = State.EMERGENCY_FILL;
        }

    }

    private void handleMainLoop() {
        // ATTACK_TEMPOROSS parks state at null to mean "recompute", and onGameTick turns it back into
        // THIRD_CATCH. This loop runs twice per tick though, so it can arrive first — mirror the tick
        // handler's fallback rather than letting switch(null) throw into the catch block.
        if (state == null) {
            state = State.THIRD_CATCH;
        }
        // The pool phase is over the moment energy is back near full. The latch used to be cleared
        // only inside the pool-not-found branch, so cycle 2 entered ATTACK_TEMPOROSS with it still
        // set from cycle 1 and camped the empty pool from 26% down to zero (observed live).
        if (poolPhaseActive && ENERGY >= thresholdAttackEnergy) {
            poolPhaseActive = false;
        }
        // A strike batch landing this very tick: hold one pass so the fires exist before any
        // click paths us anywhere — the rope burned walking into a fire that spawned mid-route,
        // after the route's own fire checks had already passed.
        if (!sortedClouds.isEmpty() && soonestStrikeTicks() <= 0) {
            return;
        }
        switch (state) {
            case INITIAL_CATCH:
            case SECOND_CATCH:
            case THIRD_CATCH:
                isFilling = false;

                // Full bag: nothing to catch into. Reachable while holding through pool 1 with a
                // full cooked inventory — stand by instead of clicking spots that cannot pay out.
                if (cachedAllFish >= cachedTotalSlots) {
                    return;
                }

                // "Busy" means committed to a live spot: walking to one we just clicked, or
                // actually ENGAGED with it (interaction). Not the harpoon animation: spots RELOCATE
                // rather than despawn, so the alive-by-index check stayed true while the spot was
                // already tiles away, and the sticky animation kept us waiting it out. The
                // interaction drops the instant the spot leaves — move on right then.
                boolean engagedWithSpot = cachedInteractingIndex >= 0
                        && cachedInteractingIndex == lastCatchSpotIndex;
                // Engagement alone is not progress: a clicked spot with no walkable path leaves the
                // red interaction mark set while the player stands still forever (observed on a
                // fresh double). Busy needs movement, or engagement WITH the fishing animation —
                // a stuck stand falls through to the fire/detour logic and a fresh approach.
                if ((Rs2Player.isMoving() || (engagedWithSpot && Rs2Player.isAnimating()))
                        && lastCatchSpotAlive()) {
                    boolean atDouble = lastCatchSpotId == NpcID.FISHING_SPOT_10569;
                    if (atDouble || !hasDoubleSpot()) {
                        return;
                    }
                }

                long inCloudCount = fishSpots.stream().filter(npc -> inImminentCloudArea(npc, 1)).count();
                long fireCount = fishSpots.stream().filter(npc -> hasAdjacentFire(npc.worldLocation)).count();
                int emptySlots = cachedTotalSlots - cachedAllFish;
                var fishSpot = fishSpots.stream()
                        .filter(npc -> !inImminentCloudArea(npc, 1))
                        .filter(npc -> {
                            boolean fireAdjacent = hasAdjacentFire(npc.worldLocation);
                            return !fireAdjacent || Rs2Inventory.contains(ItemID.BUCKET_OF_WATER);
                        })
                        .findFirst()
                        .orElse(null);

                if (fishSpot == null && !fishSpots.isEmpty()) {
                    log("CATCH: " + fishSpots.size() + " spots found but all filtered (inCloud=" + inCloudCount + " fire=" + fireCount + ")");
                }

                if (fishSpot != null) {
                    TemporossNpcSnapshot adjacentFire = getAdjacentFire(fishSpot.worldLocation);
                    if (adjacentFire != null && Rs2Inventory.contains(ItemID.BUCKET_OF_WATER)) {
                        if (clickNpc(adjacentFire, "Douse", false)) {
                            log("Dousing fire adjacent to fish spot");
                            sleepUntil(() -> !Rs2Player.isInteracting(), 5000);
                        }
                        return;
                    }

                    if (!temporossConfig.solo()) {
                        if(!fightFiresInPath(fishSpot.worldLocation))
                            return;
                    }
                    if (detourAroundFires(fishSpot.localLocation, "fish spot"))
                        return;
                    // The spec's +3 Fishing speeds up catching here; it does nothing for pool
                    // shield depletion, so it fires at the spots and never at the pool.
                    maybeUseHarpoonSpec();
                    clickNpc(fishSpot, "Harpoon", true);
                    lastCatchSpotIndex = fishSpot.index;
                    lastCatchSpotId = fishSpot.id;
                    log("Interacting with " + (fishSpot.id == NpcID.FISHING_SPOT_10569 ? "double" : "single") + " fish spot");
                } else {
                    if (Rs2Player.isMoving()) {
                        return;
                    }
                    // Snapshot: the game-end handler nulls workArea from another thread mid-pass.
                    TemporossWorkArea area = workArea;
                    if (area == null) {
                        return;
                    }
                    WorldPoint totemLocation = area.getTotemLocation();
                    log("Can't find the fish spot, walking to the totem pole at " + totemLocation);
                    // No staging: the totem IS the staging anchor and must never recurse into itself.
                    walkToWorkAreaPoint(totemLocation, "Totem pole", false);
                    return;
                }
                break;

            case INITIAL_COOK:
            case SECOND_COOK:
            case THIRD_COOK:
                isFilling = false;
                int rawFishCount = Rs2Inventory.count(ItemID.RAW_HARPOONFISH);
                TemporossObjectSnapshot range = workArea != null ? workArea.getRange() : null;
                if (range != null && rawFishCount > 0) {
                    if (Rs2Player.getAnimation() == AnimationID.COOKING_RANGE || Rs2Player.isMoving()) {
                        return;
                    }
                    clickObject(range, "Cook-at");
                    log("Interacting with range");
                } else if (range == null) {
                    TemporossWorkArea cookArea = workArea;
                    if (cookArea == null) {
                        return;     // game ended mid-pass
                    }
                    log("Can't find the range, walking to the range point");
                    walkToWorkAreaPoint(cookArea.getRangeLocation(), "Range");
                }
                break;

            case EMERGENCY_FILL:
            case SECOND_FILL:
            case INITIAL_FILL:
                LocalPoint mastLocal = localFromWorld(workArea.mastPoint);
                // Crates are keyed by side too (A: 10576/10577, B: 10578/10579), so the other ship's
                // pair cannot be selected regardless of where we are standing.
                Supplier<List<TemporossNpcSnapshot>> crateSnapshots = () -> Microbot.getRs2NpcCache().query()
                        .withIds(workArea.side.ammoCrateIdA, workArea.side.ammoCrateIdB)
                        .where(npc -> npc.getNpc() != null && npc.getNpc().getLocalLocation() != null
                                && mastLocal != null
                                && npc.getNpc().getLocalLocation().distanceTo(mastLocal) <= 4 * 128)
                        .toList().stream()
                        .map(npc -> new TemporossNpcSnapshot(npc.getId(), npc.getIndex(),
                                npc.getNpc().getLocalLocation(), npc.getWorldLocation(), npc.getName()))
                        .collect(Collectors.toList());
                List<TemporossNpcSnapshot> cratesAtMast = Microbot.getClientThread().invoke(crateSnapshots);
                List<TemporossNpcSnapshot> ammoCrates = cratesAtMast.stream()
                        .filter(npc -> !inImminentCloudArea(npc, 0))
                        .collect(Collectors.toList());

                LocalPoint fillPlayerLocal = cachedPlayerLocal;
                if (ammoCrates.isEmpty()) {
                    // Clouds drift over the crates constantly. They are transient, so hold position
                    // and let it pass rather than abandoning the fill and retreating to the exit.
                    if (!cratesAtMast.isEmpty()) {
                        log("All " + cratesAtMast.size() + " ammo crates in a cloud, waiting for it to pass");
                        return;
                    }
                    if (!Rs2Player.isMoving()) {
                        // Expected while standing at the range: the crates are past NPC render
                        // distance from there, so they are not in the scene at all.
                        Supplier<Integer> fillNpcCount = () -> Microbot.getRs2NpcCache().query()
                                .where(npc -> npc.getNpc() != null && npc.getNpc().getComposition() != null
                                        && npc.getNpc().getComposition().getActions() != null
                                        && Arrays.asList(npc.getNpc().getComposition().getActions()).contains("Fill"))
                                .toList().size();
                        int fillNpcsInScene = Microbot.getClientThread().invoke(fillNpcCount);
                        // Two stages. From the range the mast is ~25 tiles out, too far for a single
                        // scene click to path sensibly, so head for the totem first — the same
                        // mid-side waypoint the catch loop already uses successfully. Once we are
                        // near it the mast is a short hop and behaves like the tether click during a
                        // wave; in practice the crates render on the way and get clicked before we
                        // ever arrive.
                        WorldPoint approach = workArea.getTotemLocation();
                        LocalPoint approachLocal = localFromWorld(approach);
                        if (approachLocal != null && fillPlayerLocal != null
                                && fillPlayerLocal.distanceTo(approachLocal) < 5 * Perspective.LOCAL_TILE_SIZE) {
                            approach = workArea.mastPoint;
                        }
                        log("Ammo crates not rendered yet (Fill NPCs in scene=" + fillNpcsInScene
                                + "), walking to " + approach);
                        walkToWorkAreaPoint(approach, "Ammo crate approach");
                    }
                    return;
                }

                if (cachedPlayerLocal != null && inImminentCloudArea(cachedPlayerLocal, 0)) {
                    log("In cloud, switching ammo crate");
                    TemporossNpcSnapshot ammoCrate = ammoCrates.stream()
                            .max(Comparator.comparingInt(value -> fillPlayerLocal != null && value.localLocation != null
                                    ? fillPlayerLocal.distanceTo(value.localLocation) : 0)).orElse(null);
                    if (ammoCrate != null) {
                        clickNpc(ammoCrate, "Fill", true);
                    }
                    isFilling = true;
                    return;
                }

                var ammoCrate = ammoCrates.stream()
                        .min(Comparator.comparingInt(value -> fillPlayerLocal != null && value.localLocation != null
                                ? fillPlayerLocal.distanceTo(value.localLocation) : Integer.MAX_VALUE)).orElse(null);

                // In mass world mode, clear fires along the path to the ammo crate before interacting.
                if (!temporossConfig.solo() && ammoCrate != null) {
                    if(!fightFiresInPath(ammoCrate.worldLocation))
                        return;

                }

                if (isFilling && (Rs2Player.isAnimating() || Rs2Player.isMoving())) {
                    break;
                }
                if (ammoCrate == null) {
                    break;
                }
                if (detourAroundFires(ammoCrate.localLocation, "ammo crate"))
                    return;
                clickNpc(ammoCrate, "Fill", true);
                log("Interacting with ammo crate");
                isFilling = true;
                break;

            case ATTACK_TEMPOROSS:
                isFilling = false;
                if (temporossPool != null) {
                    poolPhaseActive = true;
                    // Busy only counts when it is harpooning OUR pool — matched by id plus proximity
                    // to the spiritPoolPoint mark, never by reference (a reference compare against the
                    // re-queried model ping-ponged cancel/re-click when the query flip-flopped between
                    // the two side's pools, which tie on exit distance). Fishing a spot also reads as
                    // animating, so busyness must stay target-aware either way.
                    boolean busyWithPool = cachedInteractingId == NpcID.SPIRIT_POOL
                            && cachedInteractingWorld != null
                            && cachedInteractingWorld.distanceTo(workArea.spiritPoolPoint) <= 6;
                    boolean busyElsewhere = cachedInteractingIndex != -1 && !busyWithPool;
                    if ((Rs2Player.isAnimating() || Rs2Player.isMoving()) && !busyElsewhere) {
                        if (ENERGY >= thresholdFullEnergy) {
                            log("Energy is full, stopping attack");
                            poolPhaseActive = false;
                            state = null;
                        }
                        return;
                    }
                    // Break the competing interaction with an explicit stop rather than trusting the
                    // Harpoon click to displace it — observed: fishing carried on through repeated
                    // pool clicks, one per loop, none of them taking effect.
                    if (busyElsewhere) {
                        log("Breaking off current action to harpoon the pool");
                        cancelCurrentAction();
                        return;
                    }
                    // Last-line guard at the click itself: whatever the query said, never harpoon a
                    // pool that is not at OUR dock's mark. Exit distance cannot tell the pools apart
                    // (both measured exactly 10 from our exit), the mark can (3 vs 13).
                    WorldPoint poolLoc = temporossPool.worldLocation;
                    int poolToMark = poolLoc.distanceTo(workArea.spiritPoolPoint);
                    int poolToExit = poolLoc.distanceTo(workArea.exitNpc);
                    if (poolToMark > 6) {
                        log("REFUSING pool at " + poolLoc + " (dist to our mark=" + poolToMark
                                + ") — not ours. Walking to our dock instead");
                        walkToSpiritPool();
                        return;
                    }
                    log("Harpooning Tempoross at " + poolLoc
                            + " (poolToExit=" + poolToExit
                            + ", poolToTotemExit=" + (workArea.getTotemExitNpc() != null
                                    ? poolLoc.distanceTo(workArea.getTotemExitNpc()) : "?")
                            + ", playerToPool=" + (temporossPool.localLocation != null
                                    && cachedPlayerLocal != null
                                    ? cachedPlayerLocal
                                            .distanceTo(temporossPool.localLocation) / Perspective.LOCAL_TILE_SIZE
                                    : -1) + " tiles)");
                    if (clickNpc(temporossPool, "Harpoon", false)) {
                        // Wait for the click to take (walking to the pool, then the animation) so a
                        // slow approach is not machine-gunned with one extra click per loop.
                        boolean took = sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isMoving()
                                || TemporossPlugin.incomingWave, 3000);
                        if (!took && ++ineffectivePoolClicks >= 3) {
                            // Three clicks with zero effect — reposition at the dock rather than
                            // clicking from a spot the pathfinder cannot serve.
                            log("Pool clicks not taking effect, repositioning at the dock");
                            ineffectivePoolClicks = 0;
                            walkToSpiritPool();
                        } else if (took) {
                            ineffectivePoolClicks = 0;
                        }
                    }
                } else {
                    // Entered via the state chain after the final load, with the pool phase not
                    // actually here yet? Fish instead of idling at the mark — the chain used to park
                    // the bot at the pool from 20%+ down to zero doing nothing.
                    if (!poolPhaseActive && ENERGY > thresholdLowEnergy) {
                        log("Pool not open yet at " + ENERGY + "%, fishing until ~" + thresholdLowEnergy + "%");
                        state = State.THIRD_CATCH;
                        return;
                    }
                    poolPhaseActive = true;
                    // The phase can end below the ATTACK completion threshold (the recharge tops out
                    // around 97 against a sampled 98), leaving the latch set and the bot parked at an
                    // empty mark while energy climbed from 30% (observed). A pool that stays gone for
                    // ~2s with energy well past the approach window means the phase is simply over.
                    if (ENERGY > 10) {
                        if (++poolGonePasses >= 6) {
                            log("Pool gone at " + ENERGY + "% — phase over, back to fishing");
                            poolGonePasses = 0;
                            poolPhaseActive = false;
                            state = null;
                            return;
                        }
                    } else {
                        poolGonePasses = 0;
                    }
                    // Pool not rendered yet. Energy recharges the whole time the pool is open, so
                    // bailing at "energy above the low threshold" cancelled the dock walk within a
                    // second of it starting. Only give up once Tempoross has essentially recharged.
                    if (ENERGY >= thresholdAttackEnergy) {
                        log("Pool never found and energy is back to " + ENERGY + "%, resuming");
                        poolPhaseActive = false;
                        state = null;
                        return;
                    }
                    // No isMoving() gate: a walk to the range or a fish spot must be interrupted, not
                    // finished first. walkToWorkAreaPoint already no-ops when the dock is the current
                    // destination, so this does not spam clicks while en route.
                    walkToSpiritPool();
                }
                break;
        }
    }

    private static WorldPoint getTrueWorldPoint(WorldPoint point) {
        LocalPoint localPoint = localFromWorld(point);
        assert localPoint != null;
        return templateFromLocal(localPoint);
    }

    /**
     * Walk to a point in the work area, preferring a direct scene click and only falling back to the
     * global walker when the target is outside the loaded scene. No-ops when already there or already
     * on the way.
     */
    private void walkToWorkAreaPoint(WorldPoint target, String label) {
        walkToWorkAreaPoint(target, label, true);
    }

    private void walkToWorkAreaPoint(WorldPoint target, String label, boolean allowStaging) {
        // Put out anything burning on the way rather than running through it.
        if (!fightFiresInPath(target)) {
            return;
        }
        LocalPoint localPoint = localFromWorld(target);
        if (localPoint == null) {
            // NOT Rs2Walker.walkTo: our points are raw instance coordinates and the global pathfinder
            // reads them as real-world ones. That is what sent us running well past the range for ten
            // seconds before the wrong-side watchdog caught the destination.
            if (allowStaging && stageViaTotem(label)) {
                return;
            }
            log(label + " is outside the loaded scene and the totem cannot stage it — not walking");
            return;
        }
        // Before the dedup: fires can spawn on a route we are already committed to.
        if (detourAroundFires(localPoint, label))
            return;
        if (Objects.equals(cachedDestination, localPoint))
            return;
        LocalPoint playerLocal = cachedPlayerLocal;
        if (playerLocal != null && playerLocal.distanceTo(localPoint) < 3 * Perspective.LOCAL_TILE_SIZE)
            return;
        walkLocalSafe(localPoint, label, allowStaging);
    }

    /**
     * Walks to a scene tile, never handing {@link Rs2Walker#walkFastLocal} a tile it cannot click.
     *
     * <p>walkFastLocal builds its menu click from {@code Perspective.localToCanvas} and does not
     * null-check the result: a tile with no canvas projection — beyond draw distance, or off-screen —
     * is dispatched as {@code (-1, -1)}, which the client resolves to an arbitrary destination.
     * Observed live: a walk to the spirit pool mark, issued from the range, produced a destination 44
     * tiles off-side. (The fault is in the shared walker, which lives in the client jar and cannot be
     * patched from the Hub, so it is avoided here instead.)
     *
     * <p>Camera first, then a partial step along the same line, so a far target still makes progress.
     */
    private void walkLocalSafe(LocalPoint target, String label, boolean allowStaging) {
        if (target == null) {
            return;
        }
        if (!Rs2Camera.isTileOnScreen(target)) {
            Rs2Camera.turnTo(target, 70);
        }
        if (!Rs2Camera.isTileOnScreen(target)) {
            // ALL rungs each pass, not an else-if ladder: the ladder wedged whenever one rung
            // silently did nothing (observed live: pitch smoothing no-op, the camera never turned,
            // 12+ seconds of give-up logs). Redundant adjustments are cheap; stalls are not.
            if (Rs2Camera.getPitch() < 350) {
                Rs2Camera.setPitch(383);
            }
            if (Rs2Camera.getZoom() > 140) {
                Rs2Camera.setZoom(Math.max(140, Rs2Camera.getZoom() - 120));
            }
            Rs2Camera.turnTo(target, 15);
        }
        if (Rs2Camera.isTileOnScreen(target)) {
            Rs2Walker.walkFastLocal(target);
            return;
        }
        // Still not clickable. Stage via the totem rather than interpolating along the straight line —
        // the direct line from the range to the dock can cross water, the totem never does.
        if (allowStaging && stageViaTotem(label)) {
            return;
        }
        // Last resort, camera-independent: a MINIMAP click needs no 3D projection. Step a bounded
        // walkable leg along the line — shrinking the leg keeps it off water and fires — and repeat
        // from closer next pass. This is what finally breaks the fire-stop-then-never-restart stall.
        LocalPoint from = cachedPlayerLocal;
        if (from != null) {
            int dx = target.getX() - from.getX(), dy = target.getY() - from.getY();
            double len = Math.hypot(dx, dy);
            if (len > 0) {
                for (int tiles : new int[]{11, 8, 5, 3}) {
                    double scale = Math.min(1.0, tiles * Perspective.LOCAL_TILE_SIZE / len);
                    LocalPoint leg = new LocalPoint(from.getX() + (int) (dx * scale),
                            from.getY() + (int) (dy * scale), from.getWorldView());
                    if (!Rs2Tile.isWalkable(leg) || onFireTile(leg)) {
                        continue;
                    }
                    WorldPoint legWorld = worldFromLocal(leg);
                    if (Rs2Walker.walkMiniMap(legWorld)) {
                        log(label + " off-screen — minimap step toward it");
                        return;
                    }
                }
            }
        }
        log(label + " has no on-screen approach this tick — not walking");
    }

    /**
     * Walks to the totem, the mid-side anchor, as a staging point for anything we cannot reach or
     * click directly. Everything on our side is reachable from there, and the target usually renders
     * on the way, so the second leg is issued before we even arrive.
     *
     * @return true when a staging walk was issued
     */
    private boolean stageViaTotem(String label) {
        LocalPoint playerLocal = cachedPlayerLocal;
        LocalPoint totemLocal = localFromWorld(workArea.getTotemLocation());
        if (playerLocal == null || totemLocal == null) {
            return false;
        }
        // Already there — staging again would achieve nothing.
        if (playerLocal.distanceTo(totemLocal) < 5 * Perspective.LOCAL_TILE_SIZE) {
            return false;
        }
        if (!Rs2Camera.isTileOnScreen(totemLocal)) {
            Rs2Camera.turnTo(totemLocal, 70);
        }
        if (!Rs2Camera.isTileOnScreen(totemLocal)) {
            return false;
        }
        log(label + " out of reach — staging via the totem");
        Rs2Walker.walkFastLocal(totemLocal);
        return true;
    }

    private void walkToSafePoint() {
        walkToWorkAreaPoint(workArea.safePoint, "Safe point");
    }

    private void walkToSpiritPool() {
        // Straight to the mark. spiritPoolPoint is a mirrored offset verified in three separate games
        // (1, 3 and 1 tiles off the actual pool). The old safe-point target routed via the ship and
        // walked PAST the pool — triggered from the shoreline, that was a 19s detour for a pool ten
        // tiles away.
        walkToWorkAreaPoint(workArea.spiritPoolPoint, "Spirit pool");
    }


    private boolean handleCloudDodge() {
        // The player's own LocalPoint, never a conversion of Rs2Player.getWorldLocation(): that is in
        // template space and converting it against the live scene yields null, so this check silently
        // never fired and the bot stood in the cloud.
        LocalPoint playerLocal = cachedPlayerLocal;
        if (playerLocal == null) {
            return false;
        }
        // Strikes never land on the pool point (observed live), so a pool harpoon — the densest
        // points in the game — is never abandoned for a shadow.
        if (isAttackingSpiritPool()) {
            return false;
        }
        // Timed, not reflexive: only shadows inside their final margin (strikeImminent) matter.
        // On the shadow's tile always dodges; one tile away still steps out, since the fire the
        // strike leaves behind spreads from there.
        TemporossCloudSnapshot threat = null;
        boolean onTile = false;
        for (TemporossCloudSnapshot c : sortedClouds) {
            LocalPoint cl = c.localLocation;
            if (cl == null || !strikeImminent(c)) {
                continue;
            }
            int d = playerLocal.distanceTo(cl);
            if (d < Perspective.LOCAL_TILE_SIZE) {
                threat = c;
                onTile = true;
                break;
            }
            if (d < 2 * Perspective.LOCAL_TILE_SIZE && threat == null) {
                threat = c;
            }
        }
        if (threat == null) {
            return false;
        }
        // Already dodging — wait for movement to clear the cloud
        if (Rs2Player.isMoving()) {
            return true;
        }
        LocalPoint escape = findEscapeTile(playerLocal, threat.localLocation,
                candidate -> !inCloud(candidate, 0) && !onFireTile(candidate));
        if (escape != null) {
            log((onTile ? "Strike imminent on our tile — dodging to "
                    : "Strike imminent beside us — stepping to ") + escape);
            Rs2Walker.walkFastLocal(escape);
            return true;
        }
        return false;
    }

    /**
     * Shortest hop from the player to a tile {@code safe} accepts, searching outward ring by ring and
     * preferring the tile in that ring furthest from {@code hazard} so we move away from it rather
     * than across it.
     */
    private LocalPoint findEscapeTile(LocalPoint playerLocal, LocalPoint hazard, Predicate<LocalPoint> safe) {
        for (int ring = 1; ring <= 4; ring++) {
            LocalPoint best = null;
            int bestDistance = -1;
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dy = -ring; dy <= ring; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) {
                        continue;
                    }
                    LocalPoint candidate = new LocalPoint(
                            playerLocal.getX() + dx * Perspective.LOCAL_TILE_SIZE,
                            playerLocal.getY() + dy * Perspective.LOCAL_TILE_SIZE,
                            playerLocal.getWorldView());
                    // Walkability was not checked originally, and an escape tile in the water was
                    // clicked over and over without the character ever moving.
                    if (!safe.test(candidate) || !Rs2Tile.isWalkable(candidate)) {
                        continue;
                    }
                    int distance = hazard != null ? candidate.distanceTo(hazard) : 0;
                    if (distance > bestDistance) {
                        bestDistance = distance;
                        best = candidate;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    /** Distance from a point to the segment a-b, in local units. */
    private static int distanceToSegment(LocalPoint p, LocalPoint a, LocalPoint b) {
        double dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : ((p.getX() - a.getX()) * dx + (p.getY() - a.getY()) * dy) / len2;
        t = Math.max(0, Math.min(1, t));
        return (int) Math.hypot(p.getX() - (a.getX() + t * dx), p.getY() - (a.getY() + t * dy));
    }

    /** Fires lying within 1.5 tiles of the straight line from a to b. */
    private static List<LocalPoint> firesNearLine(LocalPoint a, LocalPoint b) {
        int margin = Perspective.LOCAL_TILE_SIZE * 3 / 2;
        return sortedFires.stream()
                .map(f -> f.localLocation)
                .filter(Objects::nonNull)
                .filter(fl -> distanceToSegment(fl, a, b) <= margin)
                .collect(Collectors.toList());
    }

    /**
     * Fires are walkable tiles that burn, so the client's pathfinder happily routes straight through
     * them — and with no water, {@link #fightFiresInPath} cannot clear them either, which used to mean
     * running through the flames. This sidesteps instead: a waypoint perpendicular to the route around
     * the nearest blocking fire, from which the next loop continues toward the target on a clean line.
     *
     * @return true when a detour is in progress and the caller should not walk or click yet
     */
    private boolean detourAroundFires(LocalPoint target, String label) {
        if (sortedFires.isEmpty() || target == null) {
            return false;
        }
        // Runs whether or not we have water. fightFiresInPath goes first and douses what it can, but
        // it only clears as many fires as we have full buckets — with one bucket and three fires on
        // the line it used to douse one and walk through the rest. Anything still burning on the
        // route gets walked around instead.
        LocalPoint playerLocal = cachedPlayerLocal;
        if (playerLocal == null || playerLocal.distanceTo(target) < 3 * Perspective.LOCAL_TILE_SIZE) {
            return false; // adjacent fires are the standing-in-fire handler's job
        }
        List<LocalPoint> blocking = firesNearLine(playerLocal, target);
        if (blocking.isEmpty()) {
            return false;
        }
        // Already travelling a clean sidestep leg — let it finish.
        LocalPoint dest = cachedDestination;
        if (Rs2Player.isMoving() && dest != null && !dest.equals(target)
                && firesNearLine(playerLocal, dest).isEmpty()) {
            return true;
        }
        LocalPoint fire = blocking.stream()
                .min(Comparator.comparingInt(playerLocal::distanceTo))
                .orElse(null);
        double dx = target.getX() - playerLocal.getX(), dy = target.getY() - playerLocal.getY();
        double len = Math.hypot(dx, dy);
        if (fire == null || len == 0) {
            return false;
        }
        double px = -dy / len, py = dx / len;
        for (int tiles = 3; tiles <= 5; tiles++) {
            for (int sign : new int[]{1, -1}) {
                LocalPoint candidate = new LocalPoint(
                        fire.getX() + (int) (px * sign * tiles * Perspective.LOCAL_TILE_SIZE),
                        fire.getY() + (int) (py * sign * tiles * Perspective.LOCAL_TILE_SIZE),
                        playerLocal.getWorldView());
                if (onFireTile(candidate) || inCloud(candidate, 0) || !Rs2Tile.isWalkable(candidate)) {
                    continue;
                }
                if (!firesNearLine(playerLocal, candidate).isEmpty()) {
                    continue;
                }
                log("Fire on the way to " + label + " and no water — sidestepping around it");
                Rs2Walker.walkFastLocal(candidate);
                return true;
            }
        }
        return false; // boxed in on all sides; pushing through beats standing in place
    }

    /** Is there a fire burning on this exact tile? */
    private static boolean onFireTile(LocalPoint point) {
        if (point == null || sortedFires.isEmpty()) {
            return false;
        }
        return sortedFires.stream().anyMatch(fire -> fire.localLocation != null
                && point.distanceTo(fire.localLocation) < Perspective.LOCAL_TILE_SIZE);
    }

    /** How close a fire has to be to be worth dousing on the spot rather than only when in the way. */
    private static final int NEARBY_FIRE_RANGE = 3 * Perspective.LOCAL_TILE_SIZE;

    /**
     * Douses a fire that has just appeared next to us — typically the one a lightning strike leaves
     * behind after we dodge it. In mass mode nothing else covers this: handleFires() is solo-only and
     * fightFiresInPath only clears fires that lie on a route we happen to be taking, so a fresh fire
     * beside us would burn untouched. Tightly bounded so it can never become a trip.
     */
    private boolean handleNearbyFire() {
        if (isAttackingSpiritPool() || sortedFires.isEmpty()) {
            return false;
        }
        if (Rs2Inventory.count(ItemID.BUCKET_OF_WATER) <= 0) {
            return false;
        }
        LocalPoint playerLocal = cachedPlayerLocal;
        if (playerLocal == null || Rs2Player.isMoving()) {
            return false;
        }
        TemporossNpcSnapshot fire = sortedFires.stream()
                .filter(f -> f.localLocation != null
                        && playerLocal.distanceTo(f.localLocation) <= NEARBY_FIRE_RANGE)
                .findFirst()
                .orElse(null);
        if (fire == null) {
            return false;
        }
        if (cachedInteractingIndex != -1 && cachedInteractingIndex == fire.index) {
            return true;   // already dousing this one
        }
        if (clickNpc(fire, "Douse", false)) {
            log("Fire beside us — dousing it");
            sleepUntil(() -> !Rs2Player.isInteracting() || TemporossPlugin.incomingWave, 3000);
            return true;
        }
        return false;
    }

    /**
     * Fires burn whoever stands in them. Dousing is preferred over stepping aside — it clears the tile,
     * scores points, and we are already standing next to it — but with no water left, move.
     */
    private boolean handleStandingInFire() {
        LocalPoint playerLocal = cachedPlayerLocal;
        if (playerLocal == null || sortedFires.isEmpty()) {
            return false;
        }
        TemporossNpcSnapshot fireOnUs = sortedFires.stream()
                .filter(fire -> fire.localLocation != null
                        && playerLocal.distanceTo(fire.localLocation) < Perspective.LOCAL_TILE_SIZE)
                .findFirst()
                .orElse(null);
        if (fireOnUs == null) {
            return false;
        }

        if (Rs2Inventory.count(ItemID.BUCKET_OF_WATER) > 0) {
            if (clickNpc(fireOnUs, "Douse", false)) {
                log("Standing in fire — dousing it");
                sleepUntil(() -> !Rs2Player.isInteracting() || TemporossPlugin.incomingWave, 3000);
                return true;
            }
        }

        if (Rs2Player.isMoving()) {
            return true;
        }
        LocalPoint escape = findEscapeTile(playerLocal, fireOnUs.localLocation,
                candidate -> !onFireTile(candidate) && !inCloud(candidate, 0));
        if (escape != null) {
            log("Standing in fire with no water — stepping off to " + escape);
            Rs2Walker.walkFastLocal(escape);
            return true;
        }
        return false;
    }

    /**
     * The rope is consumed on tethering, and without one the next wave hits for full damage. Unlike the
     * other supplies this is worth a trip from any state, not only while catching.
     */
    private boolean handleMissingRope() {
        if (!temporossConfig.rope() || wearingFullSpiritAngler() || isAttackingSpiritPool()) {
            return false;
        }
        if (Rs2Inventory.contains(ItemID.ROPE) || TemporossPlugin.incomingWave || !shouldFetchSupplies()) {
            return false;
        }
        if (!fightFiresInPath(workArea.ropePoint)) {
            return true;
        }
        TemporossObjectSnapshot ropeCrate = workArea.getRopeCrate();
        if (ropeCrate != null && clickObject(ropeCrate, "Take")) {
            log("Rope is gone, fetching a replacement before the next wave");
            sleepUntil(() -> Rs2Inventory.contains(ItemID.ROPE) || TemporossPlugin.incomingWave, 10000);
        }
        return true;
    }

    /**
     * Cloud test in local/scene space. Everything cloud-related should reach this overload, since
     * clouds only ever expose a LocalPoint and mixing in world coordinates crosses coordinate spaces.
     */
    public static boolean inCloud(LocalPoint point, int radius) {
        if (sortedClouds.isEmpty() || point == null) {
            return false;
        }
        int threshold = (radius + 1) * Perspective.LOCAL_TILE_SIZE;
        return sortedClouds.stream().anyMatch(cloud -> {
            LocalPoint cloudLocal = cloud.localLocation;
            return cloudLocal != null && point.distanceTo(cloudLocal) <= threshold;
        });
    }

    /**
     * Convenience for NPC-derived positions. Prefer {@link #inCloud(LocalPoint, int)} with the
     * entity's own local location where one is available.
     */
    public static boolean inCloud(WorldPoint point, int radius) {
        return inCloud(localFromWorld(point), radius);
    }

    private static boolean inCloud(TemporossNpcSnapshot npc, int radius) {
        return npc != null && inCloud(npc.localLocation, radius);
    }

    /**
     * Like {@link #inCloud(LocalPoint, int)} but only counts shadows inside their strike margin.
     * ELIGIBILITY checks use this: a spot or crate under a fresh shadow is workable for another
     * ~12 ticks, and treating shadows as poison from birth walked the bot off a 5-fish batch to
     * cook 9 seconds before anything erupted. Dodge/escape paths keep the unconditional test —
     * never move INTO a footprint, however young.
     */
    public static boolean inImminentCloudArea(LocalPoint point, int radius) {
        if (sortedClouds.isEmpty() || point == null) {
            return false;
        }
        int threshold = (radius + 1) * Perspective.LOCAL_TILE_SIZE;
        return sortedClouds.stream().anyMatch(cloud -> {
            if (!strikeImminent(cloud)) {
                return false;
            }
            LocalPoint cloudLocal = cloud.localLocation;
            return cloudLocal != null && point.distanceTo(cloudLocal) <= threshold;
        });
    }

    private static boolean inImminentCloudArea(TemporossNpcSnapshot npc, int radius) {
        return npc != null && inImminentCloudArea(npc.localLocation, radius);
    }

    /**
     * Is a usable double spot up? Filtered on IMMINENT shadows only — one under a fresh shadow is
     * still worth fishing for the next ~12 ticks, and only one we cannot stand at is a reason to keep
     * catching. Shared so the third-phase catch cutoff, the cook interrupt and the abandon-a-single
     * rule all agree on what "a double is available" means.
     */
    public static boolean hasDoubleSpot() {
        return fishSpots.stream()
                .anyMatch(npc -> npc.id == NpcID.FISHING_SPOT_10569 && !inImminentCloudArea(npc, 1));
    }

    /**
     * Is the spot we last clicked still in the world? fishSpots is rebuilt from the NPC cache every
     * game tick, so a depleted spot drops out of it immediately — well before the harpoon animation
     * finishes playing.
     */
    private boolean lastCatchSpotAlive() {
        if (lastCatchSpotIndex < 0) {
            return false;
        }
        return fishSpots.stream().anyMatch(npc -> npc.index == lastCatchSpotIndex);
    }

    private boolean hasAdjacentFire(WorldPoint point) {
        return sortedFires.stream()
                .anyMatch(fire -> fire.worldLocation != null && fire.worldLocation.distanceTo(point) <= 1);
    }

    private TemporossNpcSnapshot getAdjacentFire(WorldPoint point) {
        return sortedFires.stream()
                .filter(fire -> fire.worldLocation != null && fire.worldLocation.distanceTo(point) <= 1)
                .findFirst()
                .orElse(null);
    }

    // Dousing means walking to the fire — there is no ranged douse — so both budgets are deliberately
    // small. Fires are a hazard to avoid, not a points source to chase: the permit route scores on
    // fish, so anything we cannot douse in passing is walked around instead (detourAroundFires),
    // which costs nothing. These were 4 and 10, which added up to a genuine trip.
    /** Extra travel we will accept in order to douse a fire on the way to somewhere else. */
    private static final int MAX_FIRE_DETOUR = 2 * Perspective.LOCAL_TILE_SIZE;
    /** Hard cap on how far a fire can be and still count as "in path". */
    private static final int MAX_FIRE_DISTANCE = 5 * Perspective.LOCAL_TILE_SIZE;

    public boolean fightFiresInPath(WorldPoint location) {
        if (sortedFires.isEmpty() || isAttackingSpiritPool()) {
            return true;
        }

        LocalPoint playerLocal = cachedPlayerLocal;
        LocalPoint destLocal = localFromWorld(location);
        if (playerLocal == null || destLocal == null) {
            return true;
        }

        int distToDest = playerLocal.distanceTo(destLocal);
        int fullBucketCount = Rs2Inventory.count(ItemID.BUCKET_OF_WATER);

        List<TemporossNpcSnapshot> firesInPath = sortedFires.stream()
                .filter(fire -> {
                    if (fire.localLocation == null) return false;
                    LocalPoint fireLocal = fire.localLocation;
                    int distToFire = playerLocal.distanceTo(fireLocal);
                    // Never cross the arena for a fire, however well it happens to line up.
                    if (distToFire > MAX_FIRE_DISTANCE) {
                        return false;
                    }
                    int fireToDestDist = fireLocal.distanceTo(destLocal);
                    // Triangle inequality: going via the fire must barely lengthen the trip. The old
                    // test ("closer to me than the destination, and closer to the destination than I
                    // am") describes a lens covering half the arena, which is how fires on the
                    // opposite ship were getting doused.
                    return (distToFire + fireToDestDist - distToDest) <= MAX_FIRE_DETOUR;
                })
                .sorted(Comparator.comparingInt(fire ->
                        playerLocal.distanceTo(fire.localLocation)))
                .collect(Collectors.toList());

        if (firesInPath.isEmpty()) {
            return true;
        }

        if (firesInPath.size() > fullBucketCount) {
            firesInPath = firesInPath.subList(0, fullBucketCount);
        }

        for (TemporossNpcSnapshot fire : firesInPath) {
            if (TemporossPlugin.incomingWave) return false;
            if (clickNpc(fire, "Douse", false)) {
                log("Dousing fire in path (" + (playerLocal.distanceTo(fire.localLocation)
                        / Perspective.LOCAL_TILE_SIZE) + " tiles away)");
                sleepUntil(() -> Rs2Player.isInteracting() || TemporossPlugin.incomingWave, 2000);
                sleepUntil(() -> !Rs2Player.isInteracting() || TemporossPlugin.incomingWave, 5000);
            }
        }

        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        reset();
        BreakHandlerScript.setLockState(false);
        // Any cleanup code here
    }
}
