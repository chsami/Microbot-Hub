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

    public static int ENERGY;
    public static int INTENSITY;
    public static int ESSENCE;

    public static TemporossConfig temporossConfig;
    public static State state = State.INITIAL_CATCH;
    public static TemporossWorkArea workArea = null;
    public static boolean isFilling = false;
    public static boolean isFightingFire = false;
    public static HarpoonType harpoonType;
    // Set only when the configured harpoon genuinely can't be found, and cleared by reset() so the
    // next game retries the user's own harpoon instead of permanently rewriting their config.
    private static HarpoonType harpoonFallback = null;
    public static Rs2NpcModel temporossPool;
    public static List<Rs2NpcModel> sortedFires = new ArrayList<>();
    public static List<GameObject> sortedClouds = new ArrayList<>();
    public static List<Rs2NpcModel> fishSpots = new ArrayList<>();
    // Identified by index + id rather than a cached NPC ref, which the client recycles.
    private static int lastCatchSpotIndex = -1;
    private static int lastCatchSpotId = -1;
    public static List<WorldPoint> walkPath = new ArrayList<>();
    public static long startTime;
    public static int cachedRawFish;
    public static int cachedCookedFish;
    public static int cachedAllFish;
    public static int cachedTotalSlots;
    public static boolean cachedInMinigame;

    // Per-game randomized thresholds (regenerated each game for humanization)
    public static int thresholdForfeitIntensity = 94;
    private int thresholdLowEnergy = 2;
    private int thresholdAttackEnergy = 94;
    // Static so State's completion predicates read the same numbers this loop acts on.
    public static int thresholdFullEnergy = 97;
    public static int thresholdLoadEnergy = 49;
    // Strategy opening: 7 fish below 85 Fishing, 9 at 85+ where the extra catches still fit inside
    // the same cook cycle, so the double spot arrives with nothing wasted. Resolved once per game.
    public static int openingCatchTarget = 7;
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
        workArea = null;
        TemporossPlugin.incomingWave = false;
        TemporossPlugin.isTethered = false;
        TemporossPlugin.fireClouds = 0;
        TemporossPlugin.waves = 0;
        state = State.INITIAL_CATCH;
        startupHopDone = false;
        startupHopAttempts = 0;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->{
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (BreakHandlerScript.isBreakActive() || BreakHandlerScript.isMicroBreakActive()) return;

                if (!isInMinigame()) {
                    if (handleStartupWorldHop())
                        return;
                    handleEnterMinigame();
                }
                if (isInMinigame()) {
                    if (workArea == null) {
                        determineWorkArea();
                        sleep(300, 600);
                    } else {

                        if (TemporossPlugin.incomingWave) {
                            handleTether();
                            return;
                        }
                        if (handleWrongSideClick())
                            return;
                        handleMinigame();
                        handleStateLoop();
                        if (handleCloudDodge())
                            return;
                        if (handleStandingInFire())
                            return;
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
            LocalPoint playerLocal = Microbot.getClient().getLocalPlayer() != null
                    ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
            if (playerLocal == null) return;

            List<Rs2NpcModel> forfeitNpcs = Microbot.getRs2NpcCache().query()
                    .where(npc -> npc.getNpc() != null && npc.getNpc().getComposition() != null
                            && Arrays.asList(npc.getNpc().getComposition().getActions()).contains("Forfeit"))
                    .toList();

            // Same instance as before the reset? Restore the old work area outright — its anchors are
            // static for the instance's lifetime, and restoring needs no walk to re-sight the crate.
            if (previousWorkArea != null) {
                boolean sameInstance = forfeitNpcs.stream().anyMatch(npc ->
                        npc.getWorldLocation().distanceTo(previousWorkArea.exitNpc) <= TemporossWorkArea.TOTEM_EXIT_MAX_DISTANCE
                        || (previousWorkArea.getTotemExitNpc() != null
                            && npc.getWorldLocation().distanceTo(previousWorkArea.getTotemExitNpc()) <= TemporossWorkArea.TOTEM_EXIT_MAX_DISTANCE));
                if (sameInstance) {
                    workArea = previousWorkArea;
                    previousWorkArea = null;
                    log("Work area restored after mid-game reset");
                    return;
                }
            }

            Rs2NpcModel forfeitNpc = forfeitNpcs.stream()
                    .filter(npc -> npc.getNpc().getLocalLocation() != null)
                    .min(Comparator.comparingInt(npc -> playerLocal.distanceTo(npc.getNpc().getLocalLocation())))
                    .orElse(null);

            List<Rs2NpcModel> ammoCrates = Microbot.getRs2NpcCache().query()
                    .where(npc -> npc.getNpc() != null && npc.getNpc().getComposition() != null
                            && Arrays.asList(npc.getNpc().getComposition().getActions()).contains("Fill"))
                    .toList();
            Rs2NpcModel ammoCrate = ammoCrates.stream()
                    .filter(npc -> npc.getNpc().getLocalLocation() != null)
                    .min(Comparator.comparingInt(npc -> playerLocal.distanceTo(npc.getNpc().getLocalLocation())))
                    .orElse(null);

            if (forfeitNpc == null || ammoCrate == null) {
                // Mid-game rebuild (only reachable after a reset away from the ship): the crate is an
                // NPC and simply is not rendered from the shoreline. Any visible exit NPC is within
                // ~17 tiles of the ship, so walking to it brings the crate into range.
                log("Can't rebuild work area (Forfeit NPCs visible=" + forfeitNpcs.size()
                        + ", Fill NPCs visible=" + ammoCrates.size() + ")"
                        + (forfeitNpc != null ? " — walking to the visible exit NPC" : ""));
                if (forfeitNpc != null && !Rs2Player.isMoving()) {
                    Rs2Walker.walkFastLocal(forfeitNpc.getNpc().getLocalLocation());
                }
                return;
            }
            boolean isWest = forfeitNpc.getWorldLocation().getX() < ammoCrate.getWorldLocation().getX();
            // Each side has TWO exit NPCs: the ship one (nearest, beside the ammo crate) and one by
            // the totem ~17 tiles away. The totem one is our second side-anchor — it sits right by
            // the fishing area. Anything further than the gate belongs to the other side.
            WorldPoint totemExit = forfeitNpcs.stream()
                    .filter(npc -> npc.getNpc() != null && npc.getIndex() != forfeitNpc.getIndex())
                    .map(Rs2NpcModel::getWorldLocation)
                    .filter(p -> p.distanceTo(forfeitNpc.getWorldLocation()) <= TemporossWorkArea.TOTEM_EXIT_MAX_DISTANCE)
                    .max(Comparator.comparingInt(p -> p.distanceTo(forfeitNpc.getWorldLocation())))
                    .orElse(null);
            workArea = new TemporossWorkArea(forfeitNpc.getWorldLocation(), isWest, totemExit);
            previousWorkArea = null;
            log("Totem-side exit NPC: " + (totemExit != null ? totemExit : "not rendered yet, will capture when seen"));
            // Once per game, here rather than in reset() — reset() runs every loop while outside the
            // minigame, which re-rolled and re-logged the thresholds several times a second.
            randomizeThresholds();
            // Camera baseline once per game, not every loop: re-asserting zoom and pitch at 300ms
            // fought any manual camera adjustment for the whole round.
            Rs2Camera.resetZoom();
            Rs2Camera.resetPitch();
            log("Tempoross work area: " + (isWest ? "west" : "east"));
            log("Forfeit NPC at " + forfeitNpc.getWorldLocation() + " | Ammo crate at " + ammoCrate.getWorldLocation());
            // NPC world locations and the player's are in different coordinate spaces inside the
            // instance. Print both so the offset between them is visible in the log.
            log("Player real loc=" + Rs2Player.getWorldLocation()
                    + " | player local=" + (Microbot.getClient().getLocalPlayer() != null
                    ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null));
            log(workArea.getAllPointsAsString());
        }
    }

    private void finishGame() {
        if (workArea == null) {
            return;
        }
        LocalPoint playerLocal = Microbot.getClient().getLocalPlayer() != null
                ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
        if (playerLocal == null) {
            return;
        }
        Rs2NpcModel exitNpc = Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getNpc() != null && npc.getNpc().getComposition() != null
                        && npc.getNpc().getComposition().getActions() != null
                        && Arrays.asList(npc.getNpc().getComposition().getActions()).contains("Leave")
                        && npc.getNpc().getLocalLocation() != null)
                .toList().stream()
                .min(Comparator.comparingInt(value -> playerLocal.distanceTo(value.getNpc().getLocalLocation())))
                .orElse(null);
        if (exitNpc != null) {
            int emptyBucketCount = Rs2Inventory.count(ItemID.BUCKET);
            if (emptyBucketCount > 0) {
                if(Microbot.getRs2TileObjectCache().query().interact(41004, "Fill-bucket"))
                    sleepUntil(() -> Rs2Inventory.count(ItemID.BUCKET) < 1);

            }

            if (exitNpc.click("Leave")) {
                // Reset only once we are demonstrably out. Resetting on the click used to destroy the
                // work area while still standing in the arena whenever boarding was delayed or failed,
                // and the rebuild then stalled because the ammo crate is not rendered from the dock.
                if (sleepUntil(() -> !isInMinigame(), 15000)) {
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
        thresholdLowEnergy = Rs2Random.fancyNormalSample(4, 6);
        thresholdAttackEnergy = Rs2Random.fancyNormalSample(90, 96);
        thresholdFullEnergy = Math.max(thresholdAttackEnergy + 1, Rs2Random.fancyNormalSample(97, 98));
        thresholdLoadEnergy = Rs2Random.fancyNormalSample(47, 50);
        thresholdEmergencyEnergyLow = Rs2Random.fancyNormalSample(24, 36);
        thresholdEmergencyEnergyHigh = Math.max(thresholdEmergencyEnergyLow + 10, Rs2Random.fancyNormalSample(44, 56));
        thresholdEmergencyFishMin = Rs2Random.fancyNormalSample(4, 8);
        openingCatchTarget = Rs2Player.getRealSkillLevel(Skill.FISHING) >= 85 ? 9 : 7;
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
        LocalPoint playerLocal = Microbot.getClient().getLocalPlayer() != null
                ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
        if (playerLocal == null) return;
        var forfeitNpc = Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getNpc() != null && npc.getNpc().getComposition() != null
                        && Arrays.asList(npc.getNpc().getComposition().getActions()).contains("Forfeit"))
                .toList().stream()
                .filter(npc -> npc.getNpc().getLocalLocation() != null)
                .min(Comparator.comparingInt(npc -> playerLocal.distanceTo(npc.getNpc().getLocalLocation())))
                .orElse(null);
        if (forfeitNpc != null) {
            if (forfeitNpc.click("Forfeit")) {
                sleepUntil(() -> !isInMinigame(), 15000);
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
        Actor interacting = Rs2Player.getInteracting();
        if (interacting instanceof NPC) {
            WorldPoint targetLoc = ((NPC) interacting).getWorldLocation();
            if (!workArea.isOnOurSide(targetLoc)) {
                log("Interacting with something on the other side at " + targetLoc + " — cancelling");
                cancelCurrentAction();
                return true;
            }
        }
        LocalPoint dest = Microbot.getClient().getLocalDestinationLocation();
        if (dest != null) {
            WorldPoint destWorld = WorldPoint.fromLocal(Microbot.getClient(), dest);
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

    /** Walk-here on our own tile: stops both the current path and any interaction. */
    private void cancelCurrentAction() {
        LocalPoint playerLocal = Microbot.getClient().getLocalPlayer() != null
                ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
        if (playerLocal != null) {
            Rs2Walker.walkFastLocal(playerLocal);
        }
    }

    private void handleMinigame()
    {
        // Resolve before the phase gate — hasHarpoon() dereferences this, and starting the plugin
        // mid-game past phase 2 would otherwise leave it null.
        harpoonType = harpoonFallback != null ? harpoonFallback : temporossConfig.harpoonType();

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
        if (temporossConfig.rope() && !temporossConfig.spiritAnglers() && !Rs2Inventory.contains(ItemID.ROPE))
        {
            return true;
        }

        // Check for hammer
        return temporossConfig.hammer() && !Rs2Inventory.contains(ItemID.HAMMER);
    }

    private void fetchMissingItems()
    {
        LocalPoint playerLocal = Microbot.getClient().getLocalPlayer() != null
                ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
        if (playerLocal == null) return;

        List<int[]> needed = new ArrayList<>();

        if (!hasHarpoon() && harpoonType != HarpoonType.BAREHAND) {
            LocalPoint lp = LocalPoint.fromWorld(Microbot.getClient(),workArea.harpoonPoint);
            needed.add(new int[]{0, lp != null ? playerLocal.distanceTo(lp) : Integer.MAX_VALUE});
        }

        int bucketCount = Rs2Inventory.count(item ->
                item.getId() == ItemID.BUCKET || item.getId() == ItemID.BUCKET_OF_WATER);
        boolean needBuckets = (bucketCount < temporossConfig.buckets() && state == State.INITIAL_CATCH) || bucketCount == 0;
        if (needBuckets) {
            LocalPoint lp = LocalPoint.fromWorld(Microbot.getClient(),workArea.bucketPoint);
            needed.add(new int[]{1, lp != null ? playerLocal.distanceTo(lp) : Integer.MAX_VALUE});
        }

        int fullBucketCount = Rs2Inventory.count(ItemID.BUCKET_OF_WATER);
        if (!needBuckets && fullBucketCount <= 0) {
            LocalPoint lp = LocalPoint.fromWorld(Microbot.getClient(),workArea.pumpPoint);
            needed.add(new int[]{2, lp != null ? playerLocal.distanceTo(lp) : Integer.MAX_VALUE});
        }

        if (temporossConfig.rope() && !temporossConfig.spiritAnglers() && !Rs2Inventory.contains(ItemID.ROPE)) {
            LocalPoint lp = LocalPoint.fromWorld(Microbot.getClient(),workArea.ropePoint);
            needed.add(new int[]{3, lp != null ? playerLocal.distanceTo(lp) : Integer.MAX_VALUE});
        }

        if (temporossConfig.hammer() && !Rs2Inventory.contains(ItemID.HAMMER)) {
            LocalPoint lp = LocalPoint.fromWorld(Microbot.getClient(),workArea.hammerPoint);
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
                if (workArea.getHarpoonCrate() != null && workArea.getHarpoonCrate().click("Take")) {
                    log("Taking harpoon");
                    sleepUntil(() -> hasHarpoon() || TemporossPlugin.incomingWave, 10000);
                }
                break;
            case 1: // Buckets
                fightFiresInPath(workArea.bucketPoint);
                sleepUntil(() -> Rs2Inventory.count(item ->
                        item.getId() == ItemID.BUCKET || item.getId() == ItemID.BUCKET_OF_WATER) >= temporossConfig.buckets()
                        || TemporossPlugin.incomingWave, () -> {
                    if (!TemporossPlugin.incomingWave && workArea.getBucketCrate() != null && workArea.getBucketCrate().click("Take")) {
                        log("Taking buckets");
                        Rs2Inventory.waitForInventoryChanges(3000);
                    }
                }, 10000, 300);
                break;
            case 2: // Fill buckets
                fightFiresInPath(workArea.pumpPoint);
                if (workArea.getPump() != null && workArea.getPump().click("Use")) {
                    log("Filling buckets");
                    sleepUntil(() -> Rs2Inventory.count(ItemID.BUCKET) <= 0 || TemporossPlugin.incomingWave, 10000);
                }
                break;
            case 3: // Rope
                fightFiresInPath(workArea.ropePoint);
                if (workArea.getRopeCrate() != null && workArea.getRopeCrate().click("Take")) {
                    log("Taking rope");
                    sleepUntil(() -> Rs2Inventory.contains(ItemID.ROPE) || TemporossPlugin.incomingWave, 10000);
                }
                break;
            case 4: // Hammer
                fightFiresInPath(workArea.hammerPoint);
                if (workArea.getHammerCrate() != null && workArea.getHammerCrate().click("Take")) {
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

    // Once per script start, not per game — leaving a round must not trigger a hop.
    private boolean startupHopDone = false;
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
        if (target <= 0 || Microbot.getClient().getWorld() == target) {
            startupHopDone = true;
            return false;
        }
        if (startupHopAttempts >= 3) {
            log("World hop to " + target + " failed " + startupHopAttempts + " times, continuing on world "
                    + Microbot.getClient().getWorld());
            startupHopDone = true;
            return false;
        }
        startupHopAttempts++;
        log("Hopping to world " + target + " (attempt " + startupHopAttempts + ")");
        if (Microbot.hopToWorld(target)) {
            if (sleepUntil(() -> Microbot.isLoggedIn() && Microbot.getClient().getWorld() == target, 20000)) {
                startupHopDone = true;
            }
        }
        return true;
    }

    private boolean isOnStartingBoat() {
        Rs2TileObjectModel startingLadder = Microbot.getRs2TileObjectCache().query().withId(ObjectID.ROPE_LADDER_41305).nearest();
        if (startingLadder == null) {
            log("Failed to find starting ladder");
            return false;
        }
        LocalPoint playerLocal = Microbot.getClient().getLocalPlayer() != null
                ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
        LocalPoint ladderLocal = startingLadder.getLocalLocation();
        if (playerLocal == null || ladderLocal == null) return false;
        return playerLocal.getSceneX() < ladderLocal.getSceneX();
    }

    private void handleEnterMinigame() {
        // Reset state variables
        reset();

        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            return;
        }
        Rs2TileObjectModel startingLadder = Microbot.getRs2TileObjectCache().query().withId(ObjectID.ROPE_LADDER_41305).nearest();
        if (startingLadder == null) {
            log("Failed to find starting ladder");
            return;
        }
        int emptyBucketCount = Rs2Inventory.count(ItemID.BUCKET);
        // If we are east of the ladder, interact with it to get on the boat
        if (!isOnStartingBoat()) {
            if (startingLadder.click(((emptyBucketCount > 0 && temporossConfig.solo()) || !temporossConfig.solo()) ? "Climb" : "Solo-start")) {
                BreakHandlerScript.setLockState(true);
                sleepUntil(() -> (isOnStartingBoat() || isInMinigame()), 15000);
                return;
            }
        }

        Rs2TileObjectModel waterPump = Microbot.getRs2TileObjectCache().query().withId(ObjectID.WATER_PUMP_41000).nearest();

        if (waterPump != null && emptyBucketCount > 0) {
            if (waterPump.click("Use")) {
                Rs2Player.waitForAnimation(5000);
            }
        }
        sleepUntil(TemporossScript::isInMinigame, 30000);
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
        } catch (NumberFormatException e) {
            if(Rs2AntibanSettings.devDebug)
                log("Failed to parse energy, essence, or intensity");
        }
    }

    public static void updateFireData(){
        List<Rs2NpcModel> allFires = Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getNpc() != null && npc.getNpc().getComposition() != null
                        && Arrays.asList(npc.getNpc().getComposition().getActions()).contains("Douse"))
                .toList();
        LocalPoint playerLocal = Microbot.getClient().getLocalPlayer() != null
                ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
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
                .collect(Collectors.toList());
        TemporossOverlay.setNpcList(sortedFires);
    }

    public static void updateCloudData(){
        List<GameObject> allClouds = Rs2GameObject.getGameObjects().stream()
                .filter(obj -> obj.getId() == NullObjectID.NULL_41006)
                .collect(Collectors.toList());
        LocalPoint playerLocal = Microbot.getClient().getLocalPlayer() != null
                ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
        if (playerLocal == null) {
            sortedClouds = Collections.emptyList();
            return;
        }
        sortedClouds = allClouds.stream()
                .filter(y -> y.getLocalLocation() != null && playerLocal.distanceTo(y.getLocalLocation()) < 30 * 128)
                .sorted(Comparator.comparingInt(x -> playerLocal.distanceTo(x.getLocalLocation())))
                .collect(Collectors.toList());
        TemporossOverlay.setCloudList(sortedClouds);
    }

    // update ammo crate data
    public static void updateAmmoCrateData(){
        LocalPoint mastLocal = LocalPoint.fromWorld(Microbot.getClient(),workArea.mastPoint);
        List<Rs2NpcModel> ammoCrates = Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getNpc() != null && npc.getNpc().getComposition() != null
                        && Arrays.asList(npc.getNpc().getComposition().getActions()).contains("Fill")
                        && mastLocal != null && npc.getNpc().getLocalLocation() != null
                        && npc.getNpc().getLocalLocation().distanceTo(mastLocal) <= 4 * 128
                        && !inCloud(npc, 0))
                .toList();
        TemporossOverlay.setAmmoList(ammoCrates);
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
        LocalPoint playerLocal = Microbot.getClient().getLocalPlayer() != null
                ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
        List<Rs2NpcModel> allSpots = Microbot.getRs2NpcCache().query()
                .withIds(NpcID.FISHING_SPOT_10569, NpcID.FISHING_SPOT_10568, NpcID.FISHING_SPOT_10565)
                .toList();
        // Anchored on the exit NPC, never on the range. Our spots sit 15-21 tiles from the exit NPC
        // and the opposite side's are 40+, so this has a ~20 tile margin. Anchoring on the range gave
        // a 4 tile margin and one wrong shrine lookup rejected every spot for the rest of the game.
        fishSpots = allSpots.stream()
                .filter(npc -> workArea.isOnOurSide(npc.getWorldLocation()))
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
                .collect(Collectors.toList());

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
        for (Rs2NpcModel fire : sortedFires) {
            if(isFilling){
                Microbot.log("Filling, skipping fire");
                return;
            }
            // Skip only if already dousing THIS specific fire. Target-identity
            // check is reliable; the outer isInteracting() gate is not.
            Actor current = Rs2Player.getInteracting();
            if (current != null && current == fire.getNpc()) {
                return;
            }
            if (fire.click("Douse")) {
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
        if (isAttackingSpiritPool() || !temporossConfig.hammer() || !Rs2Inventory.contains(ItemID.HAMMER)) {
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
    private boolean handleDamaged(Supplier<Rs2TileObjectModel> lookup, String label) {
        Rs2TileObjectModel damaged = lookup.get();
        if (damaged == null) {
            return false;
        }
        LocalPoint playerLocal = Microbot.getClient().getLocalPlayer() != null
                ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
        LocalPoint damagedLocal = damaged.getLocalLocation();
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
                log("Walking to the damaged " + label + " at " + damaged.getWorldLocation()
                        + " (" + (distance / Perspective.LOCAL_TILE_SIZE) + " tiles)");
                walkToWorkAreaPoint(damaged.getWorldLocation(), "Damaged " + label);
            }
            return true;
        }

        if (damaged.click("Repair")) {
            log("Repairing " + label);
            sleepUntil(() -> lookup.get() == null || TemporossPlugin.incomingWave,
                    () -> {
                        Rs2TileObjectModel stillBroken = lookup.get();
                        if (stillBroken != null && !Rs2Player.isAnimating() && !TemporossPlugin.incomingWave) {
                            stillBroken.click("Repair");
                        }
                    }, 10000, 1200);
        }
        return true;
    }

    private Rs2TileObjectModel lockedTether = null;

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
                    Rs2TileObjectModel mast = workArea.getMast();
                    Rs2TileObjectModel totem = workArea.getTotem();
                    lockedTether = workArea.getClosestTether();
                    // Distances in local space. Rs2Player.getWorldLocation() is in template space
                    // while object locations are not, so comparing the two printed a meaningless
                    // ~9800 for both tethers.
                    LocalPoint playerLocal = Microbot.getClient().getLocalPlayer() != null
                            ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
                    log("Tether decision: mast=" + (mast != null ? mast.getWorldLocation() + " dist=" + tileDistance(playerLocal, mast.getLocalLocation()) : "NULL")
                            + " | totem=" + (totem != null ? totem.getWorldLocation() + " dist=" + tileDistance(playerLocal, totem.getLocalLocation()) : "NULL")
                            + " | picked=" + (lockedTether != null ? lockedTether.getWorldLocation() : "NULL"));
                }
                if (lockedTether == null) {
                    return;
                }
                ShortestPathPlugin.exit();
                Rs2Walker.setTarget(null);
                lockedTether.click("Tether");
                log("Tethering");
                sleepUntil(() -> TemporossPlugin.isTethered, () -> lockedTether.click("Tether"), 8000, Rs2Random.fancyNormalSample(1200, 2800));
            } else {
                lockedTether = null;
            }
        } else if (!TemporossPlugin.incomingWave) {
            lockedTether = null;
        }
    }

    private void handleStateLoop() {
        temporossPool = Microbot.getRs2NpcCache().query().withId(NpcID.SPIRIT_POOL)
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
        boolean doubleFishingSpot = !fishSpots.isEmpty() && fishSpots.get(0).getId() == NpcID.FISHING_SPOT_10569;

        if (TemporossScript.state == State.INITIAL_COOK && doubleFishingSpot) {
            log("Double fishing spot detected, skipping initial cook");
            TemporossScript.state = TemporossScript.state.next;
        }

        // Late-game strategy: fish the double spot whenever it is up and cook in the gaps between
        // spots. Only worth leaving the range if there is somewhere to put the fish, and never once
        // energy has dropped past the load cutoff — from there the bag has to be cooked and loaded.
        if (TemporossScript.state == State.THIRD_COOK && doubleFishingSpot
                && cachedAllFish < cachedTotalSlots
                && TemporossScript.ENERGY > thresholdLoadEnergy) {
            log("Double fishing spot up, interrupting cook to fish it");
            TemporossScript.state = State.THIRD_CATCH;
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

        if (((TemporossScript.ENERGY < thresholdEmergencyEnergyLow && cachedAllFish > thresholdEmergencyFishMin)
            || (TemporossScript.ENERGY < thresholdEmergencyEnergyHigh && cachedAllFish >= cachedTotalSlots))
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
        switch (state) {
            case INITIAL_CATCH:
            case SECOND_CATCH:
            case THIRD_CATCH:
                isFilling = false;

                // "Busy" means committed to a live spot: walking to one we just clicked, or fishing it.
                // Two things that are not busy, both of which used to be treated as such:
                //   - moving with no target, i.e. walking to the totem because nothing was in range.
                //     That made the bot ignore spots the instant they rendered and finish the walk.
                //   - the harpoon animation, which keeps playing for a beat after a spot depletes and
                //     which isAnimating() reports for a further 600ms.
                if ((Rs2Player.isMoving() || Rs2Player.isAnimating()) && lastCatchSpotAlive()) {
                    boolean atDouble = lastCatchSpotId == NpcID.FISHING_SPOT_10569;
                    boolean doubleAvailable = fishSpots.stream().anyMatch(
                            npc -> npc.getId() == NpcID.FISHING_SPOT_10569 && !inCloud(npc, 1));
                    if (atDouble || !doubleAvailable) {
                        return;
                    }
                }

                long inCloudCount = fishSpots.stream().filter(npc -> inCloud(npc, 1)).count();
                long fireCount = fishSpots.stream().filter(npc -> hasAdjacentFire(npc.getWorldLocation())).count();
                int emptySlots = cachedTotalSlots - cachedAllFish;
                var fishSpot = fishSpots.stream()
                        .filter(npc -> !inCloud(npc, 1))
                        .filter(npc -> {
                            boolean fireAdjacent = hasAdjacentFire(npc.getWorldLocation());
                            return !fireAdjacent || Rs2Inventory.contains(ItemID.BUCKET_OF_WATER);
                        })
                        .findFirst()
                        .orElse(null);

                if (fishSpot == null && !fishSpots.isEmpty()) {
                    log("CATCH: " + fishSpots.size() + " spots found but all filtered (inCloud=" + inCloudCount + " fire=" + fireCount + ")");
                }

                if (fishSpot != null && fishSpot.getNpc() != null) {
                    Rs2NpcModel adjacentFire = getAdjacentFire(fishSpot.getWorldLocation());
                    if (adjacentFire != null && Rs2Inventory.contains(ItemID.BUCKET_OF_WATER)) {
                        if (adjacentFire.click("Douse")) {
                            log("Dousing fire adjacent to fish spot");
                            sleepUntil(() -> !Rs2Player.isInteracting(), 5000);
                        }
                        return;
                    }

                    if (!temporossConfig.solo()) {
                        if(!fightFiresInPath(fishSpot.getWorldLocation()))
                            return;
                    }
                    if (detourAroundFires(fishSpot.getNpc().getLocalLocation(), "fish spot"))
                        return;
                    faceIfNeeded(fishSpot.getNpc());
                    fishSpot.click("Harpoon");
                    lastCatchSpotIndex = fishSpot.getIndex();
                    lastCatchSpotId = fishSpot.getId();
                    log("Interacting with " + (fishSpot.getId() == NpcID.FISHING_SPOT_10569 ? "double" : "single") + " fish spot");
                } else {
                    if (Rs2Player.isMoving()) {
                        return;
                    }
                    WorldPoint totemLocation = workArea.getTotemLocation();
                    log("Can't find the fish spot, walking to the totem pole at " + totemLocation);
                    walkToWorkAreaPoint(totemLocation, "Totem pole");
                    return;
                }
                break;

            case INITIAL_COOK:
            case SECOND_COOK:
            case THIRD_COOK:
                isFilling = false;
                int rawFishCount = Rs2Inventory.count(ItemID.RAW_HARPOONFISH);
                Rs2TileObjectModel range = workArea != null ? workArea.getRange() : null;
                if (range != null && rawFishCount > 0) {
                    if (Rs2Player.getAnimation() == AnimationID.COOKING_RANGE || Rs2Player.isMoving()) {
                        return;
                    }
                    range.click("Cook-at");
                    log("Interacting with range");
                } else if (range == null) {
                    log("Can't find the range, walking to the range point");
                    walkToWorkAreaPoint(workArea.getRangeLocation(), "Range");
                }
                break;

            case EMERGENCY_FILL:
            case SECOND_FILL:
            case INITIAL_FILL:
                LocalPoint mastLocal = LocalPoint.fromWorld(Microbot.getClient(),workArea.mastPoint);
                List<Rs2NpcModel> cratesAtMast = Microbot.getRs2NpcCache().query()
                        .where(npc -> npc.getNpc() != null && npc.getNpc().getComposition() != null
                                && npc.getNpc().getComposition().getActions() != null
                                && Arrays.asList(npc.getNpc().getComposition().getActions()).contains("Fill")
                                && mastLocal != null && npc.getNpc().getLocalLocation() != null
                                && npc.getNpc().getLocalLocation().distanceTo(mastLocal) <= 4 * 128)
                        .toList();
                List<Rs2NpcModel> ammoCrates = cratesAtMast.stream()
                        .filter(npc -> !inCloud(npc, 0))
                        .collect(Collectors.toList());

                LocalPoint fillPlayerLocal = Microbot.getClient().getLocalPlayer() != null
                        ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
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
                        long fillNpcsInScene = Microbot.getRs2NpcCache().query()
                                .where(npc -> npc.getNpc() != null && npc.getNpc().getComposition() != null
                                        && npc.getNpc().getComposition().getActions() != null
                                        && Arrays.asList(npc.getNpc().getComposition().getActions()).contains("Fill"))
                                .toList().size();
                        // Two stages. From the range the mast is ~25 tiles out, too far for a single
                        // scene click to path sensibly, so head for the totem first — the same
                        // mid-side waypoint the catch loop already uses successfully. Once we are
                        // near it the mast is a short hop and behaves like the tether click during a
                        // wave; in practice the crates render on the way and get clicked before we
                        // ever arrive.
                        WorldPoint approach = workArea.getTotemLocation();
                        LocalPoint approachLocal = LocalPoint.fromWorld(Microbot.getClient(), approach);
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

                if (inCloud(Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getLocalLocation()), 0)) {
                    log("In cloud, switching ammo crate");
                    Rs2NpcModel ammoCrate = ammoCrates.stream()
                            .max(Comparator.comparingInt(value -> fillPlayerLocal != null && value.getNpc().getLocalLocation() != null
                                    ? fillPlayerLocal.distanceTo(value.getNpc().getLocalLocation()) : 0)).orElse(null);
                    if (ammoCrate != null) {
                        faceIfNeeded(ammoCrate.getNpc());
                        ammoCrate.click("Fill");
                    }
                    isFilling = true;
                    return;
                }

                var ammoCrate = ammoCrates.stream()
                        .min(Comparator.comparingInt(value -> fillPlayerLocal != null && value.getNpc().getLocalLocation() != null
                                ? fillPlayerLocal.distanceTo(value.getNpc().getLocalLocation()) : Integer.MAX_VALUE)).orElse(null);

                // In mass world mode, clear fires along the path to the ammo crate before interacting.
                if (!temporossConfig.solo() && ammoCrate != null) {
                    if(!fightFiresInPath(ammoCrate.getWorldLocation()))
                        return;

                }

                if (isFilling && (Rs2Player.isAnimating() || Rs2Player.isMoving())) {
                    break;
                }
                if (ammoCrate == null || ammoCrate.getNpc() == null) {
                    break;
                }
                if (detourAroundFires(ammoCrate.getNpc().getLocalLocation(), "ammo crate"))
                    return;
                faceIfNeeded(ammoCrate.getNpc());
                ammoCrate.click("Fill");
                log("Interacting with ammo crate");
                isFilling = true;
                break;

            case ATTACK_TEMPOROSS:
                isFilling = false;
                if (temporossPool != null && temporossPool.getNpc() != null) {
                    poolPhaseActive = true;
                    // Busy only counts when it is harpooning OUR pool — matched by id plus proximity
                    // to the spiritPoolPoint mark, never by reference (a reference compare against the
                    // re-queried model ping-ponged cancel/re-click when the query flip-flopped between
                    // the two side's pools, which tie on exit distance). Fishing a spot also reads as
                    // animating, so busyness must stay target-aware either way.
                    Actor current = Rs2Player.getInteracting();
                    boolean busyWithPool = current instanceof NPC && ((NPC) current).getId() == NpcID.SPIRIT_POOL
                            && ((NPC) current).getWorldLocation() != null
                            && ((NPC) current).getWorldLocation().distanceTo(workArea.spiritPoolPoint) <= 6;
                    boolean busyElsewhere = current != null && !busyWithPool;
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
                    if (temporossConfig.enableHarpoonSpec()
                            && (harpoonType == HarpoonType.DRAGON_HARPOON
                            || harpoonType == HarpoonType.INFERNAL_HARPOON
                            || harpoonType == HarpoonType.CRYSTAL_HARPOON)) {
                        int currentSpecEnergy = Rs2Combat.getSpecEnergy() / 10;
                        if (currentSpecEnergy >= 100) {
                            Rs2Combat.setSpecState(true, 100);
                            sleep(600);
                            log("Using harpoon special attack");
                        }
                    }
                    // Last-line guard at the click itself: whatever the query said, never harpoon a
                    // pool that is not at OUR dock's mark. Exit distance cannot tell the pools apart
                    // (both measured exactly 10 from our exit), the mark can (3 vs 13).
                    WorldPoint poolLoc = temporossPool.getWorldLocation();
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
                            + ", playerToPool=" + (temporossPool.getNpc().getLocalLocation() != null
                                    && Microbot.getClient().getLocalPlayer() != null
                                    ? Microbot.getClient().getLocalPlayer().getLocalLocation()
                                            .distanceTo(temporossPool.getNpc().getLocalLocation()) / Perspective.LOCAL_TILE_SIZE
                                    : -1) + " tiles)");
                    if (temporossPool.click("Harpoon")) {
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
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient(),point);
        assert localPoint != null;
        return WorldPoint.fromLocalInstance(Microbot.getClient(), localPoint);
    }

    /**
     * Walk to a point in the work area, preferring a direct scene click and only falling back to the
     * global walker when the target is outside the loaded scene. No-ops when already there or already
     * on the way.
     */
    private void walkToWorkAreaPoint(WorldPoint target, String label) {
        // Put out anything burning on the way rather than running through it.
        if (!fightFiresInPath(target)) {
            return;
        }
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient(), target);
        if (localPoint == null) {
            log(label + " off-screen, using Rs2Walker");
            Rs2Walker.walkTo(target);
            return;
        }
        // Before the dedup: fires can spawn on a route we are already committed to.
        if (detourAroundFires(localPoint, label))
            return;
        if (Objects.equals(Microbot.getClient().getLocalDestinationLocation(), localPoint))
            return;
        LocalPoint playerLocal = Microbot.getClient().getLocalPlayer() != null
                ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
        if (playerLocal != null && playerLocal.distanceTo(localPoint) < 3 * Perspective.LOCAL_TILE_SIZE)
            return;
        Rs2Walker.walkFastLocal(localPoint);
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
        LocalPoint playerLocal = Microbot.getClient().getLocalPlayer() != null
                ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
        if (playerLocal == null) {
            return false;
        }
        if (!inCloud(playerLocal, 0)) {
            return false;
        }
        // Already dodging — wait for movement to clear the cloud
        if (Rs2Player.isMoving()) {
            return true;
        }

        LocalPoint nearestCloud = sortedClouds.stream()
                .map(GameObject::getLocalLocation)
                .filter(Objects::nonNull)
                .min(Comparator.comparingInt(playerLocal::distanceTo))
                .orElse(null);

        LocalPoint escape = findEscapeTile(playerLocal, nearestCloud, candidate -> !inCloud(candidate, 0));
        if (escape != null) {
            log("Standing in fire cloud — dodging to " + escape);
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
                .map(f -> f.getNpc() != null ? f.getNpc().getLocalLocation() : null)
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
        if (Rs2Inventory.count(ItemID.BUCKET_OF_WATER) > 0) {
            return false; // dousing handles the route
        }
        LocalPoint playerLocal = Microbot.getClient().getLocalPlayer() != null
                ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
        if (playerLocal == null || playerLocal.distanceTo(target) < 3 * Perspective.LOCAL_TILE_SIZE) {
            return false; // adjacent fires are the standing-in-fire handler's job
        }
        List<LocalPoint> blocking = firesNearLine(playerLocal, target);
        if (blocking.isEmpty()) {
            return false;
        }
        // Already travelling a clean sidestep leg — let it finish.
        LocalPoint dest = Microbot.getClient().getLocalDestinationLocation();
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
        return sortedFires.stream().anyMatch(fire -> fire.getNpc() != null
                && fire.getNpc().getLocalLocation() != null
                && point.distanceTo(fire.getNpc().getLocalLocation()) < Perspective.LOCAL_TILE_SIZE);
    }

    /**
     * Fires burn whoever stands in them. Dousing is preferred over stepping aside — it clears the tile,
     * scores points, and we are already standing next to it — but with no water left, move.
     */
    private boolean handleStandingInFire() {
        LocalPoint playerLocal = Microbot.getClient().getLocalPlayer() != null
                ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
        if (playerLocal == null || sortedFires.isEmpty()) {
            return false;
        }
        Rs2NpcModel fireOnUs = sortedFires.stream()
                .filter(fire -> fire.getNpc() != null && fire.getNpc().getLocalLocation() != null
                        && playerLocal.distanceTo(fire.getNpc().getLocalLocation()) < Perspective.LOCAL_TILE_SIZE)
                .findFirst()
                .orElse(null);
        if (fireOnUs == null) {
            return false;
        }

        if (Rs2Inventory.count(ItemID.BUCKET_OF_WATER) > 0) {
            if (fireOnUs.click("Douse")) {
                log("Standing in fire — dousing it");
                sleepUntil(() -> !Rs2Player.isInteracting() || TemporossPlugin.incomingWave, 3000);
                return true;
            }
        }

        if (Rs2Player.isMoving()) {
            return true;
        }
        LocalPoint escape = findEscapeTile(playerLocal, fireOnUs.getNpc().getLocalLocation(),
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
        if (!temporossConfig.rope() || temporossConfig.spiritAnglers() || isAttackingSpiritPool()) {
            return false;
        }
        if (Rs2Inventory.contains(ItemID.ROPE) || TemporossPlugin.incomingWave || !shouldFetchSupplies()) {
            return false;
        }
        if (!fightFiresInPath(workArea.ropePoint)) {
            return true;
        }
        Rs2TileObjectModel ropeCrate = workArea.getRopeCrate();
        if (ropeCrate != null && ropeCrate.click("Take")) {
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
            LocalPoint cloudLocal = cloud.getLocalLocation();
            return cloudLocal != null && point.distanceTo(cloudLocal) <= threshold;
        });
    }

    /**
     * Convenience for NPC-derived positions. Prefer {@link #inCloud(LocalPoint, int)} with the
     * entity's own local location where one is available.
     */
    public static boolean inCloud(WorldPoint point, int radius) {
        return inCloud(LocalPoint.fromWorld(Microbot.getClient(), point), radius);
    }

    private static boolean inCloud(Rs2NpcModel npc, int radius) {
        return npc != null && npc.getNpc() != null && inCloud(npc.getNpc().getLocalLocation(), radius);
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
        return fishSpots.stream().anyMatch(npc -> npc.getIndex() == lastCatchSpotIndex);
    }

    private boolean hasAdjacentFire(WorldPoint point) {
        return sortedFires.stream()
                .anyMatch(fire -> fire.getNpc() != null && fire.getNpc().getComposition() != null
                        && fire.getWorldLocation().distanceTo(point) <= 1);
    }

    private Rs2NpcModel getAdjacentFire(WorldPoint point) {
        return sortedFires.stream()
                .filter(fire -> fire.getNpc() != null && fire.getNpc().getComposition() != null
                        && fire.getWorldLocation().distanceTo(point) <= 1)
                .findFirst()
                .orElse(null);
    }

    /** Extra travel we will accept in order to douse a fire on the way to somewhere else. */
    private static final int MAX_FIRE_DETOUR = 4 * Perspective.LOCAL_TILE_SIZE;
    /** Hard cap on how far a fire can be and still count as "in path". */
    private static final int MAX_FIRE_DISTANCE = 10 * Perspective.LOCAL_TILE_SIZE;

    public boolean fightFiresInPath(WorldPoint location) {
        if (sortedFires.isEmpty() || isAttackingSpiritPool()) {
            return true;
        }

        LocalPoint playerLocal = Microbot.getClient().getLocalPlayer() != null
                ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
        LocalPoint destLocal = LocalPoint.fromWorld(Microbot.getClient(),location);
        if (playerLocal == null || destLocal == null) {
            return true;
        }

        int distToDest = playerLocal.distanceTo(destLocal);
        int fullBucketCount = Rs2Inventory.count(ItemID.BUCKET_OF_WATER);

        List<Rs2NpcModel> firesInPath = sortedFires.stream()
                .filter(fire -> {
                    if (fire.getNpc() == null || fire.getNpc().getLocalLocation() == null) return false;
                    LocalPoint fireLocal = fire.getNpc().getLocalLocation();
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
                        playerLocal.distanceTo(fire.getNpc().getLocalLocation())))
                .collect(Collectors.toList());

        if (firesInPath.isEmpty()) {
            return true;
        }

        if (firesInPath.size() > fullBucketCount) {
            firesInPath = firesInPath.subList(0, fullBucketCount);
        }

        for (Rs2NpcModel fire : firesInPath) {
            if (TemporossPlugin.incomingWave) return false;
            if (fire.click("Douse")) {
                log("Dousing fire in path (" + (playerLocal.distanceTo(fire.getNpc().getLocalLocation())
                        / Perspective.LOCAL_TILE_SIZE) + " tiles away)");
                sleepUntil(() -> Rs2Player.isInteracting() || TemporossPlugin.incomingWave, 2000);
                sleepUntil(() -> !Rs2Player.isInteracting() || TemporossPlugin.incomingWave, 5000);
                sortedFires.remove(fire);
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
