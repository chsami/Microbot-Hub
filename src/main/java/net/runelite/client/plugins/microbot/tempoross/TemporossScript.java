package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.gpu.GpuPlugin;
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
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.*;
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
    public static Rs2NpcModel temporossPool;
    public static List<Rs2NpcModel> sortedFires = new ArrayList<>();
    public static List<GameObject> sortedClouds = new ArrayList<>();
    public static List<Rs2NpcModel> fishSpots = new ArrayList<>();
    private static NPC lastCatchSpotNpc = null;
    public static List<WorldPoint> walkPath = new ArrayList<>();
    public static long startTime;
    public static int cachedRawFish;
    public static int cachedCookedFish;
    public static int cachedAllFish;
    public static int cachedTotalSlots;
    public static boolean cachedInMinigame;

    // Per-game randomized thresholds (regenerated each game for humanization)
    public static int thresholdForfeitIntensity = 94;
    private int thresholdLowEnergy = 5;
    private int thresholdAttackEnergy = 94;
    private int thresholdFullEnergy = 95;
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
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->{
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (BreakHandlerScript.isBreakActive() || BreakHandlerScript.isMicroBreakActive()) return;

                if (!isInMinigame()) {
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
                        handleMinigame();
                        handleStateLoop();
                        if (handleCloudDodge())
                            return;
                        if(areItemsMissing() && (state == State.INITIAL_CATCH || state == State.SECOND_CATCH || state == State.THIRD_CATCH))
                            return;
                        handleFires();
                        handleTether();
                        if(isFightingFire)
                            return;
                        handleDamagedMast();
                        handleDamagedTotem();
                        handleForfeit();

                        finishGame();
                        handleMainLoop();
                    }
                }
            } catch (Exception e) {
                log("Error in script: " + e.getMessage());
                e.printStackTrace();
            }

        }, 0, 300, TimeUnit.MILLISECONDS);
        return true;
    }

    private int getPhase() {
        return 1 + (TemporossPlugin.waves / 4); // every 3 waves, phase increases by 1
    }

    static boolean isInMinigame() {
        if(Microbot.getClient().getGameState() != GameState.LOGGED_IN)
            return false;
        int regionId = Rs2Player.getWorldLocation().getRegionID();
        return regionId == TEMPOROSS_REGION;
    }

    private boolean hasHarpoon() {
        return Rs2Inventory.contains(harpoonType.getId()) || Rs2Equipment.isWearing(harpoonType.getId());
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
                log("Can't find forfeit NPC or ammo crate");
                return;
            }
            boolean isWest = forfeitNpc.getWorldLocation().getX() < ammoCrate.getWorldLocation().getX();
            workArea = new TemporossWorkArea(forfeitNpc.getWorldLocation(), isWest);
            log("Tempoross work area: " + (isWest ? "west" : "east"));
            log("Forfeit NPC at " + forfeitNpc.getWorldLocation() + " | Ammo crate at " + ammoCrate.getWorldLocation());
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
                reset();
                sleepUntil(() -> !isInMinigame(), 15000);
                BreakHandlerScript.setLockState(false);
                Rs2Antiban.takeMicroBreakByChance();
            }
        }
    }

    private void reset(){
        ENERGY = 0;
        INTENSITY = 0;
        ESSENCE = 0;
        workArea = null;
        isFilling = false;
        isFightingFire = false;

        lastCatchSpotNpc = null;
        walkPath = null;
        TemporossPlugin.incomingWave = false;
        TemporossPlugin.isTethered = false;
        TemporossPlugin.fireClouds = 0;
        TemporossPlugin.waves = 0;
        state = State.INITIAL_CATCH;
        randomizeThresholds();
    }

    private void randomizeThresholds() {
        thresholdForfeitIntensity = Rs2Random.fancyNormalSample(91, 96);
        thresholdLowEnergy = Rs2Random.fancyNormalSample(2, 8);
        thresholdAttackEnergy = Rs2Random.fancyNormalSample(90, 97);
        thresholdFullEnergy = Math.max(thresholdAttackEnergy + 1, Rs2Random.fancyNormalSample(92, 98));
        thresholdEmergencyEnergyLow = Rs2Random.fancyNormalSample(24, 36);
        thresholdEmergencyEnergyHigh = Math.max(thresholdEmergencyEnergyLow + 10, Rs2Random.fancyNormalSample(44, 56));
        thresholdEmergencyFishMin = Rs2Random.fancyNormalSample(4, 8);
        log("Game thresholds: forfeit=" + thresholdForfeitIntensity
                + " lowE=" + thresholdLowEnergy
                + " attackE=" + thresholdAttackEnergy
                + " fullE=" + thresholdFullEnergy
                + " emergLow=" + thresholdEmergencyEnergyLow
                + " emergHigh=" + thresholdEmergencyEnergyHigh
                + " emergFish=" + thresholdEmergencyFishMin);
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

    private void handleMinigame()
    {
        if (getPhase() > 2)
            return;

        harpoonType = temporossConfig.harpoonType();

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
            LocalPoint lp = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), workArea.harpoonPoint);
            needed.add(new int[]{0, lp != null ? playerLocal.distanceTo(lp) : Integer.MAX_VALUE});
        }

        int bucketCount = Rs2Inventory.count(item ->
                item.getId() == ItemID.BUCKET || item.getId() == ItemID.BUCKET_OF_WATER);
        boolean needBuckets = (bucketCount < temporossConfig.buckets() && state == State.INITIAL_CATCH) || bucketCount == 0;
        if (needBuckets) {
            LocalPoint lp = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), workArea.bucketPoint);
            needed.add(new int[]{1, lp != null ? playerLocal.distanceTo(lp) : Integer.MAX_VALUE});
        }

        int fullBucketCount = Rs2Inventory.count(ItemID.BUCKET_OF_WATER);
        if (!needBuckets && fullBucketCount <= 0) {
            LocalPoint lp = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), workArea.pumpPoint);
            needed.add(new int[]{2, lp != null ? playerLocal.distanceTo(lp) : Integer.MAX_VALUE});
        }

        if (temporossConfig.rope() && !temporossConfig.spiritAnglers() && !Rs2Inventory.contains(ItemID.ROPE)) {
            LocalPoint lp = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), workArea.ropePoint);
            needed.add(new int[]{3, lp != null ? playerLocal.distanceTo(lp) : Integer.MAX_VALUE});
        }

        if (temporossConfig.hammer() && !Rs2Inventory.contains(ItemID.HAMMER)) {
            LocalPoint lp = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), workArea.hammerPoint);
            needed.add(new int[]{4, lp != null ? playerLocal.distanceTo(lp) : Integer.MAX_VALUE});
        }

        if (needed.isEmpty()) return;

        // Sort by distance, fetch closest
        needed.sort(Comparator.comparingInt(a -> a[1]));
        int closest = needed.get(0)[0];

        switch (closest) {
            case 0: // Harpoon
                harpoonType = HarpoonType.HARPOON;
                log("Missing selected harpoon, setting to default harpoon");
                TemporossPlugin.setHarpoonType(harpoonType);
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
        LocalPoint exitLocal = workArea != null
                ? LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), workArea.exitNpc) : null;
        int fireRadius = temporossConfig != null && temporossConfig.solo() ? 35 : 20;
        int fireRadiusLocal = fireRadius * Perspective.LOCAL_TILE_SIZE;
        int workAreaRadius = 30 * Perspective.LOCAL_TILE_SIZE;
        sortedFires = allFires.stream()
                .filter(y -> {
                    if (playerLocal == null || y.getNpc() == null || y.getNpc().getLocalLocation() == null)
                        return false;
                    if (exitLocal != null && y.getNpc().getLocalLocation().distanceTo(exitLocal) > workAreaRadius)
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
        LocalPoint mastLocal = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), workArea.mastPoint);
        List<Rs2NpcModel> ammoCrates = Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getNpc() != null && npc.getNpc().getComposition() != null
                        && Arrays.asList(npc.getNpc().getComposition().getActions()).contains("Fill")
                        && mastLocal != null && npc.getNpc().getLocalLocation() != null
                        && npc.getNpc().getLocalLocation().distanceTo(mastLocal) <= 4 * 128
                        && !inCloud(npc.getWorldLocation(), 0))
                .toList();
        TemporossOverlay.setAmmoList(ammoCrates);
    }

    public static void updateFishSpotData(){
        fishSpots = Microbot.getRs2NpcCache().query()
                .withIds(NpcID.FISHING_SPOT_10569, NpcID.FISHING_SPOT_10568, NpcID.FISHING_SPOT_10565)
                .where(npc -> npc.getWorldLocation().distanceTo(workArea.rangePoint) <= 20)
                .toList().stream()
                .sorted(Comparator
                        .comparingInt(npc -> npc.getId() == NpcID.FISHING_SPOT_10569 ? 0 : 1))
                .collect(Collectors.toList());
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

    private void handleDamagedMast() {
        if ((temporossConfig.hammer() && !Rs2Inventory.contains("Hammer")) || !temporossConfig.hammer())
            return;

        Rs2TileObjectModel damagedMast = workArea.getBrokenMast();
        if(damagedMast == null)
            return;
        LocalPoint playerLocal = Microbot.getClient().getLocalPlayer() != null
                ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
        LocalPoint mastLocal = damagedMast.getLocalLocation();
        if (playerLocal != null && mastLocal != null && playerLocal.distanceTo(mastLocal) <= 5 * Perspective.LOCAL_TILE_SIZE) {
            if (damagedMast.click("Repair")) {
                log("Repairing mast");
                sleepUntil(() -> workArea.getBrokenMast() == null || TemporossPlugin.incomingWave, 5000);
            }
        }
    }

    private void handleDamagedTotem() {
        if ((temporossConfig.hammer() && !Rs2Inventory.contains("Hammer")) || !temporossConfig.hammer())
            return;

        Rs2TileObjectModel damagedTotem = workArea.getBrokenTotem();
        if(damagedTotem == null)
            return;
        LocalPoint playerLocal = Microbot.getClient().getLocalPlayer() != null
                ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
        LocalPoint totemLocal = damagedTotem.getLocalLocation();
        if (playerLocal != null && totemLocal != null && playerLocal.distanceTo(totemLocal) <= 5 * Perspective.LOCAL_TILE_SIZE) {
            if (damagedTotem.click("Repair")) {
                log("Repairing totem");
                sleepUntil(() -> workArea.getBrokenTotem() == null || TemporossPlugin.incomingWave, 5000);
            }
        }
    }

    private Rs2TileObjectModel lockedTether = null;

    private void handleTether() {
        if (TemporossPlugin.incomingWave != TemporossPlugin.isTethered) {
            if (TemporossPlugin.incomingWave) {
                if (lockedTether == null) {
                    Rs2TileObjectModel mast = workArea.getMast();
                    Rs2TileObjectModel totem = workArea.getTotem();
                    lockedTether = workArea.getClosestTether();
                    WorldPoint playerLoc = Rs2Player.getWorldLocation();
                    log("Tether decision: mast=" + (mast != null ? mast.getWorldLocation() + " dist=" + (playerLoc != null ? playerLoc.distanceTo(mast.getWorldLocation()) : "?") : "NULL")
                            + " | totem=" + (totem != null ? totem.getWorldLocation() + " dist=" + (playerLoc != null ? playerLoc.distanceTo(totem.getWorldLocation()) : "?") : "NULL")
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
                .where(npc -> npc.getWorldLocation().distanceTo(workArea.spiritPoolPoint) <= 15)
                .toList().stream()
                .min(Comparator.comparingInt(x -> workArea.spiritPoolPoint.distanceTo(x.getWorldLocation())))
                .orElse(null);
        boolean doubleFishingSpot = !fishSpots.isEmpty() && fishSpots.get(0).getId() == NpcID.FISHING_SPOT_10569;

        if (TemporossScript.state == State.INITIAL_COOK && doubleFishingSpot) {
            log("Double fishing spot detected, skipping initial cook");
            TemporossScript.state = TemporossScript.state.next;
        }

        if ((TemporossScript.state == State.THIRD_CATCH || TemporossScript.state == State.EMERGENCY_FILL)
            && TemporossScript.ENERGY <= ( isFilling ? 0 : thresholdLowEnergy)
            && !temporossConfig.solo()) {
            log("Very low energy, better wait on Tempoross pool");
            TemporossScript.state = State.ATTACK_TEMPOROSS;
            return;
        }

        if (temporossPool != null && TemporossScript.state != State.SECOND_FILL && TemporossScript.state != State.ATTACK_TEMPOROSS && TemporossScript.ENERGY < thresholdAttackEnergy) {
            log("Tempoross pool detected, attacking Tempoross");
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
        Rs2Camera.resetZoom();
        Rs2Camera.resetPitch();
        switch (state) {
            case INITIAL_CATCH:
            case SECOND_CATCH:
            case THIRD_CATCH:
                isFilling = false;

                if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
                    boolean atDouble = lastCatchSpotNpc != null && lastCatchSpotNpc.getId() == NpcID.FISHING_SPOT_10569;
                    boolean doubleAvailable = fishSpots.stream().anyMatch(
                            npc -> npc.getId() == NpcID.FISHING_SPOT_10569 && !inCloud(npc.getWorldLocation(), 1));
                    if (atDouble || !doubleAvailable) {
                        return;
                    }
                }

                long inCloudCount = fishSpots.stream().filter(npc -> inCloud(npc.getWorldLocation(), 1)).count();
                long fireCount = fishSpots.stream().filter(npc -> hasAdjacentFire(npc.getWorldLocation())).count();
                int emptySlots = cachedTotalSlots - cachedAllFish;
                var fishSpot = fishSpots.stream()
                        .filter(npc -> !inCloud(npc.getWorldLocation(), 1))
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
                    Rs2Camera.turnTo(fishSpot.getNpc());
                    fishSpot.click("Harpoon");
                    lastCatchSpotNpc = fishSpot.getNpc();
                    log("Interacting with " + (fishSpot.getId() == NpcID.FISHING_SPOT_10569 ? "double" : "single") + " fish spot");
                } else {
                    if (Rs2Player.isMoving()) {
                        return;
                    }
                    LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), workArea.totemPoint);
                    if (localPoint == null) {
                        Rs2Walker.walkTo(workArea.totemPoint);
                    } else {
                        Rs2Walker.walkFastLocal(localPoint);
                    }
                    log("Can't find the fish spot, walking to the totem pole");
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
                    LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), workArea.rangePoint);
                    if (localPoint == null) {
                        Rs2Walker.walkTo(workArea.rangePoint);
                    } else {
                        Rs2Camera.turnTo(localPoint);
                        Rs2Walker.walkFastLocal(localPoint);
                    }
                }
                break;

            case EMERGENCY_FILL:
            case SECOND_FILL:
            case INITIAL_FILL:
                LocalPoint mastLocal = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), workArea.mastPoint);
                List<Rs2NpcModel> ammoCrates = Microbot.getRs2NpcCache().query()
                        .where(npc -> npc.getNpc() != null && npc.getNpc().getComposition() != null
                                && npc.getNpc().getComposition().getActions() != null
                                && Arrays.asList(npc.getNpc().getComposition().getActions()).contains("Fill")
                                && mastLocal != null && npc.getNpc().getLocalLocation() != null
                                && npc.getNpc().getLocalLocation().distanceTo(mastLocal) <= 4 * 128
                                && !inCloud(npc.getWorldLocation(), 0))
                        .toList();

                LocalPoint fillPlayerLocal = Microbot.getClient().getLocalPlayer() != null
                        ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
                if (ammoCrates.isEmpty()) {
                    if (!Rs2Player.isMoving()) {
                        log("Can't find ammo crate, walking to the safe point");
                        walkToSafePoint();
                    }
                    return;
                }

                if (inCloud(Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getLocalLocation()))) {
                    log("In cloud, switching ammo crate");
                    Rs2NpcModel ammoCrate = ammoCrates.stream()
                            .max(Comparator.comparingInt(value -> fillPlayerLocal != null && value.getNpc().getLocalLocation() != null
                                    ? fillPlayerLocal.distanceTo(value.getNpc().getLocalLocation()) : 0)).orElse(null);
                    if (ammoCrate != null) {
                        Rs2Camera.turnTo(ammoCrate.getNpc());
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
                Rs2Camera.turnTo(ammoCrate.getNpc());
                ammoCrate.click("Fill");
                log("Interacting with ammo crate");
                isFilling = true;
                break;

            case ATTACK_TEMPOROSS:
                isFilling = false;
                if (temporossPool != null && temporossPool.getNpc() != null) {
                    if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
                        if (ENERGY >= thresholdFullEnergy) {
                            log("Energy is full, stopping attack");
                            state = null;
                        }
                        return;
                    }
                    if (temporossConfig.enableHarpoonSpec()
                            && (temporossConfig.harpoonType() == HarpoonType.DRAGON_HARPOON
                            || temporossConfig.harpoonType() == HarpoonType.INFERNAL_HARPOON
                            || temporossConfig.harpoonType() == HarpoonType.CRYSTAL_HARPOON)) {
                        int currentSpecEnergy = Rs2Combat.getSpecEnergy() / 10;
                        if (currentSpecEnergy >= 100) {
                            Rs2Combat.setSpecState(true, 100);
                            sleep(600);
                            log("Using harpoon special attack");
                        }
                    }
                    log("Harpooning Tempoross at " + temporossPool.getWorldLocation()
                            + " local=" + temporossPool.getNpc().getLocalLocation());
                    temporossPool.click("Harpoon");
                } else {
                    if (ENERGY > thresholdLowEnergy) {
                        state = null;
                        return;
                    }
                    if (!Rs2Player.isMoving()) {
                        walkToSpiritPool();
                    }
                }
                break;
        }
    }

    private static WorldPoint getTrueWorldPoint(WorldPoint point) {
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), point);
        assert localPoint != null;
        return WorldPoint.fromLocalInstance(Microbot.getClient(), localPoint);
    }

    /**
     * In mass world mode, before walking to the safe point, clear fires along the path.
     */
    private void walkToSafePoint() {
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), workArea.safePoint);
        if (localPoint == null) {
            log("Safe point off-screen, using Rs2Walker");
            Rs2Walker.walkTo(workArea.safePoint);
            return;
        }
        if (Objects.equals(Microbot.getClient().getLocalDestinationLocation(), localPoint))
            return;
        LocalPoint playerLocal = Microbot.getClient().getLocalPlayer() != null
                ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
        if (playerLocal != null && playerLocal.distanceTo(localPoint) < 3 * 128)
            return;
        Rs2Walker.walkFastLocal(localPoint);
    }

    /**
     * In mass world mode, before walking to the spirit pool, clear fires along the path.
     */
    private void walkToSpiritPool() {
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), workArea.spiritPoolPoint);
        if (localPoint == null) {
            log("Spirit pool off-screen, using Rs2Walker");
            Rs2Walker.walkTo(workArea.spiritPoolPoint);
            return;
        }
        if (Objects.equals(Microbot.getClient().getLocalDestinationLocation(), localPoint))
            return;
        LocalPoint playerLocal = Microbot.getClient().getLocalPlayer() != null
                ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
        if (playerLocal != null && playerLocal.distanceTo(localPoint) < 3 * 128)
            return;
        Rs2Walker.walkFastLocal(localPoint);
    }

    private boolean handleCloudDodge() {
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null) {
            return false;
        }
        if (!inCloud(playerLoc, 0)) {
            return false;
        }
        // Already dodging — wait for movement to clear the cloud
        if (Rs2Player.isMoving()) {
            return true;
        }
        // Find the cloud on/near the player using local coordinates
        LocalPoint playerLocal = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), playerLoc);
        if (playerLocal == null) {
            return false;
        }
        GameObject cloud = sortedClouds.stream()
                .filter(c -> c.getLocalLocation() != null && playerLocal.distanceTo(c.getLocalLocation()) <= 128)
                .findFirst()
                .orElse(sortedClouds.stream().findFirst().orElse(null));
        if (cloud != null) {
            log("Standing in fire cloud — dodging");
            Rs2Walker.walkNextToInstance(cloud);
            return true;
        }
        return false;
    }

    private boolean inCloud(LocalPoint point) {
        if(sortedClouds.isEmpty())
            return false;
        GameObject cloud = Rs2GameObject.getGameObject(point);
        return cloud != null && cloud.getId() == NullObjectID.NULL_41006;
    }

    public static boolean inCloud(WorldPoint point, int radius) {
        if (sortedClouds.isEmpty()) {
            return false;
        }
        LocalPoint playerLocal = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), point);
        if (playerLocal == null) {
            return false;
        }
        int threshold = (radius + 1) * 128;
        return sortedClouds.stream().anyMatch(cloud -> {
            LocalPoint cloudLocal = cloud.getLocalLocation();
            return cloudLocal != null && playerLocal.distanceTo(cloudLocal) <= threshold;
        });
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

    public boolean fightFiresInPath(WorldPoint location) {
        if (sortedFires.isEmpty()) {
            return true;
        }

        LocalPoint playerLocal = Microbot.getClient().getLocalPlayer() != null
                ? Microbot.getClient().getLocalPlayer().getLocalLocation() : null;
        LocalPoint destLocal = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), location);
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
                    int fireToDestDist = fireLocal.distanceTo(destLocal);
                    return distToFire < distToDest && fireToDestDist < distToDest;
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
                log("Dousing fire in path");
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
