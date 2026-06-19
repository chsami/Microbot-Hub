package net.runelite.client.plugins.microbot.herbrun;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Rs2Leprechaun;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.CropState;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingHandler;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingPatch;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingWorld;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.timetracking.Tab;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.Microbot.log;

@Slf4j
public class HerbrunScript extends Script {
    @Inject
    private ConfigManager configManager;
    @Inject
    private FarmingWorld farmingWorld;
    private FarmingHandler farmingHandler;
    private final HerbrunPlugin plugin;
    private final HerbrunConfig config;
    private HerbPatch currentPatch;
    @Inject
    ClientThread clientThread;
    private boolean initialized = false;

    private enum LocationPhase { HERB, FLOWER, ALLOTMENT, DONE }
    private LocationPhase currentPhase = LocationPhase.HERB;

    private static final Set<String> ALLOTMENT_FLOWER_REGIONS = Set.of(
            "Ardougne", "Catherby", "Civitas illa Fortis", "Falador", "Kourend", "Morytania"
    );

    @Inject
    public HerbrunScript(HerbrunPlugin plugin, HerbrunConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    private final List<HerbPatch> herbPatches = new ArrayList<>();
    private final Map<String, List<FarmingPatch>> allotmentsByRegion = new HashMap<>();
    private final Map<String, FarmingPatch> flowerByRegion = new HashMap<>();
    private final Set<Integer> handledAllotmentIds = new HashSet<>();
    private int currentAllotmentId = -1;

    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!Microbot.isLoggedIn()) return;
            if (!super.run()) return;
            if (!initialized) {
                initialized = true;
                HerbrunPlugin.status = "Gearing up";
                populatePatches();

                if (!sleepUntil(() -> !herbPatches.isEmpty(), 1000)) {
                    if (herbPatches.isEmpty()) {
                        return;
                    }
                }

                if (config.useInventorySetup()) {
                    var inventorySetup = new Rs2InventorySetup(config.inventorySetup(), mainScheduledFuture);
                    if (!inventorySetup.doesInventoryMatch() || !inventorySetup.doesEquipmentMatch()) {
                        Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint(), 20);
                        if (!inventorySetup.loadEquipment() || !inventorySetup.loadInventory()) {
                            return;
                        }
                        Rs2Bank.closeBank();
                    }
                } else {
                    if (!setupAutoInventory()) {
                        return;
                    }
                }

                int allotmentRegions = allotmentsByRegion.size();
                int flowerCount = flowerByRegion.size();
                log("Will visit " + herbPatches.size() + " herb patches, " + allotmentRegions + " allotment locations, " + flowerCount + " flower patches");
            }

            if (currentPatch == null) {
                getNextPatch();
                currentPhase = LocationPhase.HERB;
            }
            if (currentPatch == null) {
                HerbrunPlugin.status = "Finishing up";
                if (config.goToBank()) {
                    Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint());
                    if (!Rs2Bank.isOpen()) Rs2Bank.openBank();
                    Rs2Bank.depositAll();
                }
                HerbrunPlugin.status = "Finished";
                Microbot.stopPlugin(plugin);
                return;
            }

            if (!currentPatch.isEnabled()) {
                currentPatch = null;
                return;
            }

            if (!currentPatch.isInRange(40)) {
                HerbrunPlugin.status = "Walking to " + currentPatch.getRegionName();
                Rs2Walker.walkTo(currentPatch.getLocation(), 20);
                return;
            }

            String region = currentPatch.getRegionName();

            switch (currentPhase) {
                case HERB:
                    HerbrunPlugin.status = "Farming herbs at " + region;
                    if (handleHerbPatch()) {
                        currentPhase = LocationPhase.FLOWER;
                    }
                    break;
                case FLOWER:
                    if (!config.enableFlowers() || !flowerByRegion.containsKey(region)) {
                        log("Skipping flowers at " + region + " (enabled=" + config.enableFlowers() + " hasRegion=" + flowerByRegion.containsKey(region) + " keys=" + flowerByRegion.keySet() + ")");
                        currentPhase = LocationPhase.ALLOTMENT;
                    } else {
                        HerbrunPlugin.status = "Farming flowers at " + region;
                        if (handleFlowerPatch(region)) {
                            currentPhase = LocationPhase.ALLOTMENT;
                        }
                    }
                    break;
                case ALLOTMENT:
                    if (!config.enableAllotments() || !allotmentsByRegion.containsKey(region)) {
                        log("Skipping allotments at " + region + " (enabled=" + config.enableAllotments() + " hasRegion=" + allotmentsByRegion.containsKey(region) + " keys=" + allotmentsByRegion.keySet() + ")");
                        currentPhase = LocationPhase.DONE;
                    } else {
                        HerbrunPlugin.status = "Farming allotments at " + region;
                        if (handleAllotmentPatches(region)) {
                            currentPhase = LocationPhase.DONE;
                        }
                    }
                    break;
                case DONE:
                    currentPatch = null;
                    currentPhase = LocationPhase.HERB;
                    handledAllotmentIds.clear();
                    currentAllotmentId = -1;
                    break;
            }

        }, 0, 1000, TimeUnit.MILLISECONDS);

        return true;
    }

    private void populatePatches() {
        this.farmingHandler = new FarmingHandler(Microbot.getClient(), configManager);
        herbPatches.clear();
        allotmentsByRegion.clear();
        flowerByRegion.clear();

        clientThread.runOnClientThreadOptional(() -> {
            Map<String, HerbPatch> allHerbsByRegion = new HashMap<>();

            for (FarmingPatch patch : farmingWorld.getTabs().get(Tab.HERB)) {
                HerbPatch _patch = new HerbPatch(patch, config, farmingHandler);
                if (!_patch.isEnabled()) continue;
                allHerbsByRegion.put(_patch.getRegionName(), _patch);
                if (_patch.getPrediction() != CropState.GROWING) {
                    herbPatches.add(_patch);
                }
            }

            if (config.enableAllotments()) {
                for (FarmingPatch patch : farmingWorld.getTabs().get(Tab.ALLOTMENT)) {
                    String region = patch.getRegion().getName();
                    if (!ALLOTMENT_FLOWER_REGIONS.contains(region)) continue;
                    CropState prediction = farmingHandler.predictPatch(patch);
                    if (prediction != CropState.GROWING) {
                        allotmentsByRegion.computeIfAbsent(region, k -> new ArrayList<>()).add(patch);
                    }
                }
            }

            if (config.enableFlowers()) {
                for (FarmingPatch patch : farmingWorld.getTabs().get(Tab.FLOWER)) {
                    String region = patch.getRegion().getName();
                    if (!ALLOTMENT_FLOWER_REGIONS.contains(region)) continue;
                    CropState prediction = farmingHandler.predictPatch(patch);
                    if (prediction != CropState.GROWING) {
                        flowerByRegion.put(region, patch);
                    }
                }
            }

            for (String region : allHerbsByRegion.keySet()) {
                boolean alreadyInList = herbPatches.stream().anyMatch(p -> p.getRegionName().equals(region));
                if (alreadyInList) continue;
                boolean hasAllotmentWork = allotmentsByRegion.containsKey(region);
                boolean hasFlowerWork = flowerByRegion.containsKey(region);
                if (hasAllotmentWork || hasFlowerWork) {
                    herbPatches.add(allHerbsByRegion.get(region));
                }
            }

            return true;
        });
    }

    private void getNextPatch() {
        if (currentPatch == null) {
            if (herbPatches.isEmpty()) {
                return;
            }

            currentPatch = herbPatches.stream()
                    .filter(patch -> patch.isEnabled() && Objects.equals(patch.getRegionName(), "Weiss"))
                    .findFirst()
                    .orElseGet(() -> herbPatches.stream()
                            .filter(HerbPatch::isEnabled)
                            .findFirst()
                            .orElse(null));

            if (currentPatch != null) {
                herbPatches.remove(currentPatch);
            }
        }
    }

    private static final int[] ALLOTMENT_PATCH_IDS = {
            ObjectID.FARMING_VEG_PATCH_1, ObjectID.FARMING_VEG_PATCH_2,
            ObjectID.FARMING_VEG_PATCH_3, ObjectID.FARMING_VEG_PATCH_4,
            ObjectID.FARMING_VEG_PATCH_5, ObjectID.FARMING_VEG_PATCH_6,
            ObjectID.FARMING_VEG_PATCH_7, ObjectID.FARMING_VEG_PATCH_8,
            ObjectID.FARMING_VEG_PATCH_9, ObjectID.FARMING_VEG_PATCH_10,
            ObjectID.FARMING_VEG_PATCH_11, ObjectID.FARMING_VEG_PATCH_12,
            ObjectID.FARMING_VEG_PATCH_13, ObjectID.FARMING_VEG_PATCH_14,
            ObjectID.FARMING_VEG_PATCH_15, ObjectID.FARMING_VEG_PATCH_16,
            ObjectID.FARMING_VEG_PATCH_17
    };

    private static final int[] HERB_PATCH_IDS = {
            ObjectID.MYARM_HERBPATCH,
            ObjectID.FARMING_HERB_PATCH_2,
            ObjectID.FARMING_HERB_PATCH_4,
            ObjectID.FARMING_HERB_PATCH_8,
            ObjectID.FARMING_HERB_PATCH_6,
            ObjectID.FARMING_HERB_PATCH_3,
            ObjectID.FARMING_HERB_PATCH_1,
            ObjectID.FARMING_HERB_PATCH_7,
            ObjectID.MY2ARM_HERBPATCH,
            ObjectID.FARMING_HERB_PATCH_5
    };

    // --- Herb patch handling (existing logic, refactored for shared compost/noting) ---

    private boolean handleHerbPatch() {
        if (!ensureInventorySpace()) return false;

        final var obj = Microbot.getRs2TileObjectCache().query().withIds(HERB_PATCH_IDS).nearest();
        if (obj == null) return true;
        String state = getHerbPatchState(obj);

        if (state.equals("Harvestable")) {
            obj.click("Pick");
            Rs2Player.waitForWalking();
            sleepUntil(() -> getHerbPatchState(obj).equals("Empty") || Rs2Inventory.isFull(), 20000);
            return false;
        }

        if (state.equals("Weeds")) {
            obj.click("Rake");
            Rs2Player.waitForWalking();
            sleepUntil(() -> !getHerbPatchState(obj).equals("Weeds"), 15000);
            state = getHerbPatchState(obj);
        }

        if (state.equals("Dead")) {
            obj.click("Clear");
            Rs2Player.waitForWalking();
            sleepUntil(() -> getHerbPatchState(obj).equals("Empty"), 10000);
            state = getHerbPatchState(obj);
        }

        if (state.equals("Empty")) {
            if (Rs2Inventory.hasItem("Weeds")) Rs2Inventory.dropAll("Weeds");
            HerbSeedType seedInInventory = getFirstHerbSeedInInventory();
            if (seedInInventory == null) {
                log("No herb seeds found in inventory, skipping patch");
                return true;
            }
            if (!applyCompost(obj)) return false;
            Rs2Inventory.use(seedInInventory.getItemId());
            obj.click("Plant");
            Rs2Player.waitForWalking();
            sleepUntil(() -> getHerbPatchState(obj).equals("Growing"), 10000);
            return false;
        }

        return true;
    }

    private static String getHerbPatchState(TileObject rs2TileObject) {
        var game_obj = Rs2GameObject.convertToObjectComposition(rs2TileObject, true);
        var varbitValue = Microbot.getVarbitValue(game_obj.getVarbitId());

        if ((varbitValue >= 0 && varbitValue < 3) ||
                (varbitValue >= 60 && varbitValue <= 67) ||
                (varbitValue >= 173 && varbitValue <= 191) ||
                (varbitValue >= 204 && varbitValue <= 219) ||
                (varbitValue >= 221 && varbitValue <= 255)) {
            return "Weeds";
        }

        if ((varbitValue >= 4 && varbitValue <= 7) ||
                (varbitValue >= 11 && varbitValue <= 14) ||
                (varbitValue >= 18 && varbitValue <= 21) ||
                (varbitValue >= 25 && varbitValue <= 28) ||
                (varbitValue >= 32 && varbitValue <= 35) ||
                (varbitValue >= 39 && varbitValue <= 42) ||
                (varbitValue >= 46 && varbitValue <= 49) ||
                (varbitValue >= 53 && varbitValue <= 56) ||
                (varbitValue >= 68 && varbitValue <= 71) ||
                (varbitValue >= 75 && varbitValue <= 78) ||
                (varbitValue >= 82 && varbitValue <= 85) ||
                (varbitValue >= 89 && varbitValue <= 92) ||
                (varbitValue >= 96 && varbitValue <= 99) ||
                (varbitValue >= 103 && varbitValue <= 106) ||
                (varbitValue >= 192 && varbitValue <= 195)) {
            return "Growing";
        }

        if ((varbitValue >= 8 && varbitValue <= 10) ||
                (varbitValue >= 15 && varbitValue <= 17) ||
                (varbitValue >= 22 && varbitValue <= 24) ||
                (varbitValue >= 29 && varbitValue <= 31) ||
                (varbitValue >= 36 && varbitValue <= 38) ||
                (varbitValue >= 43 && varbitValue <= 45) ||
                (varbitValue >= 50 && varbitValue <= 52) ||
                (varbitValue >= 57 && varbitValue <= 59) ||
                (varbitValue >= 72 && varbitValue <= 74) ||
                (varbitValue >= 79 && varbitValue <= 81) ||
                (varbitValue >= 86 && varbitValue <= 88) ||
                (varbitValue >= 93 && varbitValue <= 95) ||
                (varbitValue >= 100 && varbitValue <= 102) ||
                (varbitValue >= 107 && varbitValue <= 109) ||
                (varbitValue >= 196 && varbitValue <= 197)) {
            return "Harvestable";
        }

        if ((varbitValue >= 128 && varbitValue <= 169) ||
                (varbitValue >= 198 && varbitValue <= 200)) {
            return "Diseased";
        }

        if ((varbitValue >= 170 && varbitValue <= 172) ||
                (varbitValue >= 201 && varbitValue <= 203)) {
            return "Dead";
        }

        return "Empty";
    }

    // --- Flower patch handling ---

    private static final int[] FLOWER_PATCH_IDS = {
            ObjectID.FARMING_FLOWER_PATCH_1, ObjectID.FARMING_FLOWER_PATCH_2,
            ObjectID.FARMING_FLOWER_PATCH_3, ObjectID.FARMING_FLOWER_PATCH_4,
            ObjectID.FARMING_FLOWER_PATCH_5, ObjectID.FARMING_FLOWER_PATCH_6,
            ObjectID.FARMING_FLOWER_PATCH_7, ObjectID.FARMING_FLOWER_PATCH_8,
            ObjectID.FARMING_FLOWER_PATCH_9
    };

    private boolean handleFlowerPatch(String region) {
        if (!ensureInventorySpace()) return false;

        final var obj = Microbot.getRs2TileObjectCache().query().withIds(FLOWER_PATCH_IDS).nearest();
        if (obj == null) {
            log("[Flower] No flower patch object found at " + region);
            return true;
        }

        String state = getPatchState(obj);
        log("[Flower] Patch at " + obj.getWorldLocation() + " state=" + state);

        switch (state) {
            case "Harvestable":
                // A flower patch harvests in one action. Free as much space as possible first so a
                // bulk yield (e.g. limpwurt roots) fits rather than overflowing onto the ground.
                noteProduceViaLeprechaun();
                obj.click("Pick");
                Rs2Player.waitForWalking();
                sleepUntil(() -> getPatchState(obj).equals("Empty") || Rs2Inventory.isFull(), 10000);
                recoverOwnLimpwurtDrops();
                return false;
            case "Weeds":
                obj.click("Rake");
                Rs2Player.waitForWalking();
                sleepUntil(() -> !getPatchState(obj).equals("Weeds"), 15000);
                return false;
            case "Dead":
                obj.click("Clear");
                Rs2Player.waitForWalking();
                sleepUntil(() -> getPatchState(obj).equals("Empty"), 10000);
                return false;
            case "Empty":
                if (Rs2Inventory.hasItem("Weeds")) Rs2Inventory.dropAll("Weeds");
                FlowerSeedType flowerSeed = config.flowerSeedType();
                if (!Rs2Inventory.hasItem(flowerSeed.getItemId())) {
                    log("[Flower] No " + flowerSeed.getSeedName() + " in inventory, skipping");
                    return true;
                }
                if (!applyCompost(obj)) return false;
                Rs2Inventory.use(flowerSeed.getItemId());
                obj.click("Plant");
                Rs2Player.waitForWalking();
                // Only advance once Growing is confirmed; otherwise retry next tick (weeds may have
                // regrown between clearing and planting, or the click missed).
                if (sleepUntil(() -> getPatchState(obj).equals("Growing"), 10000)) {
                    log("[Flower] Planted at " + obj.getWorldLocation());
                    return true;
                }
                log("[Flower] Plant not confirmed (state=" + getPatchState(obj) + "), retrying");
                return false;
            default:
                log("[Flower] Patch is " + state + ", skipping");
                return true;
        }
    }

    // --- Allotment patch handling ---

    /**
     * A multi-tile allotment patch shares one ObjectID across ~12 tiles. Interior tiles cannot be
     * interacted with because the player has no adjacent tile to stand on — all four neighbours are
     * other (blocked) patch tiles. An edge tile is interactable: at least one orthogonal neighbour is
     * open ground (passes the BLOCK_MOVEMENT_FULL collision check). isReachable() can't distinguish
     * these (it returns true for any same-worldview object), so we test for a standable neighbour here.
     */
    private static boolean hasStandableNeighbor(WorldPoint tile) {
        if (tile == null) return false;
        return Rs2Tile.isWalkable(tile.dx(1))
                || Rs2Tile.isWalkable(tile.dx(-1))
                || Rs2Tile.isWalkable(tile.dy(1))
                || Rs2Tile.isWalkable(tile.dy(-1));
    }

    /** Squared Euclidean distance — finer than WorldPoint.distanceTo (Chebyshev), so ties between a
     *  diagonal and an orthogonal tile resolve toward the orthogonal (genuinely nearest) one. */
    private static int sqDist(WorldPoint a, WorldPoint b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        int dx = a.getX() - b.getX();
        int dy = a.getY() - b.getY();
        return dx * dx + dy * dy;
    }

    private boolean handleAllotmentPatches(String region) {
        if (!ensureInventorySpace()) return false;

        var allObjects = Microbot.getRs2TileObjectCache().query().withIds(ALLOTMENT_PATCH_IDS).toList();
        if (allObjects == null || allObjects.isEmpty()) {
            log("[Allotment] No allotment objects found in scene");
            currentAllotmentId = -1;
            return true;
        }

        // distanceTo() is Chebyshev (max(|dx|,|dy|)), so many tiles tie and the tie is broken by list
        // order — picking a diagonal tile over the orthogonally-adjacent one. Compare by squared
        // Euclidean distance instead so we land on the genuinely-nearest tile at decision time.
        final WorldPoint playerLoc = Rs2Player.getWorldLocation();
        Rs2TileObjectModel pinned = null;
        if (currentAllotmentId >= 0) {
            pinned = allObjects.stream()
                    .filter(o -> o.getId() == currentAllotmentId && hasStandableNeighbor(o.getWorldLocation()))
                    .min(Comparator.comparingInt(o -> sqDist(o.getWorldLocation(), playerLoc)))
                    .orElse(null);
            if (pinned == null || handledAllotmentIds.contains(currentAllotmentId)) {
                log("[Allotment] Finished patch id=" + currentAllotmentId + ", looking for next");
                currentAllotmentId = -1;
                pinned = null;
            }
        }
        if (currentAllotmentId < 0) {
            pinned = allObjects.stream()
                    .filter(o -> !handledAllotmentIds.contains(o.getId()) && hasStandableNeighbor(o.getWorldLocation()))
                    .min(Comparator.comparingInt(o -> sqDist(o.getWorldLocation(), playerLoc)))
                    .orElse(null);
            if (pinned == null) {
                log("[Allotment] All patches handled at " + region);
                return true;
            }
            currentAllotmentId = pinned.getId();
            log("[Allotment] Starting patch id=" + currentAllotmentId + " near " + pinned.getWorldLocation());
        }
        final var obj = pinned;

        String state = getPatchState(obj);
        log("[Allotment] Patch id=" + currentAllotmentId + " state=" + state);

        switch (state) {
            case "Harvestable":
                obj.click("Pick");
                Rs2Player.waitForWalking();
                sleepUntil(() -> getPatchState(obj).equals("Empty") || Rs2Inventory.isFull(), 20000);
                return false;
            case "Weeds":
                obj.click("Rake");
                Rs2Player.waitForWalking();
                sleepUntil(() -> !getPatchState(obj).equals("Weeds"), 15000);
                return false;
            case "Dead":
                obj.click("Clear");
                Rs2Player.waitForWalking();
                sleepUntil(() -> getPatchState(obj).equals("Empty"), 10000);
                return false;
            case "Empty":
                if (Rs2Inventory.hasItem("Weeds")) Rs2Inventory.dropAll("Weeds");
                AllotmentSeedType allotmentSeed = getFirstAllotmentSeedInInventory();
                if (allotmentSeed == null || Rs2Inventory.itemQuantity(allotmentSeed.getItemId()) < 3) {
                    log("[Allotment] Not enough seeds (need 3), skipping id=" + currentAllotmentId);
                    handledAllotmentIds.add(currentAllotmentId);
                    currentAllotmentId = -1;
                    return false;
                }
                if (!applyCompost(obj)) return false;
                Rs2Inventory.use(allotmentSeed.getItemId());
                obj.click("Plant");
                Rs2Player.waitForWalking();
                // Only mark done once the patch is confirmed Growing. If the plant didn't take
                // (e.g. weeds regrew between clearing and planting, or the click missed), leave the
                // patch pinned so the next tick re-rakes/re-plants instead of silently giving up.
                if (sleepUntil(() -> getPatchState(obj).equals("Growing"), 10000)) {
                    log("[Allotment] Planted id=" + currentAllotmentId);
                    handledAllotmentIds.add(currentAllotmentId);
                    currentAllotmentId = -1;
                } else {
                    log("[Allotment] Plant not confirmed (state=" + getPatchState(obj) + "), retrying id=" + currentAllotmentId);
                }
                return false;
            default:
                log("[Allotment] Patch id=" + currentAllotmentId + " is " + state + ", marking done");
                handledAllotmentIds.add(currentAllotmentId);
                currentAllotmentId = -1;
                return false;
        }
    }

    // --- Shared patch state detection via game object actions ---

    private static String getPatchState(TileObject tileObj) {
        if (Rs2GameObject.hasAction(tileObj, "Rake")) return "Weeds";
        if (Rs2GameObject.hasAction(tileObj, "Pick")) return "Harvestable";
        if (Rs2GameObject.hasAction(tileObj, "Harvest")) return "Harvestable";
        if (Rs2GameObject.hasAction(tileObj, "Clear")) return "Dead";

        var comp = Rs2GameObject.convertToObjectComposition(tileObj, true);
        int varbit = Microbot.getVarbitValue(comp.getVarbitId());
        if (varbit <= 5) return "Empty";

        return "Growing";
    }

    // --- Shared compost application ---

    private boolean applyCompost(Rs2TileObjectModel obj) {
        CompostType compost = config.compostType();
        if (compost == CompostType.NONE) return true;

        if (compost.isBottomless()) {
            if (!Rs2Inventory.hasItem(compost.getItemId())) {
                log("Bottomless compost bucket not found in inventory");
                return false;
            }
        } else {
            if (!Rs2Inventory.hasItem(compost.getItemId())) {
                if (!Rs2Leprechaun.withdrawCompost(compost.getItemId())) {
                    log("Failed to withdraw " + compost.getCompostName() + " from leprechaun");
                    return false;
                }
                return false;
            }
        }

        int xpBefore = Microbot.getClient().getSkillExperience(Skill.FARMING);
        Rs2Inventory.use(compost.getItemId());
        obj.click("Compost");
        Rs2Player.waitForWalking();
        boolean applied = sleepUntil(
                () -> Microbot.getClient().getSkillExperience(Skill.FARMING) > xpBefore, 5000);

        if (!applied) {
            log("Patch already composted, skipping");
        } else if (config.dropEmptyBuckets() && !compost.isBottomless()) {
            Rs2Inventory.drop(ItemID.BUCKET_EMPTY);
        }
        return true;
    }

    // --- Shared inventory space management ---

    private boolean ensureInventorySpace() {
        if (!Rs2Inventory.isFull()) return true;
        return noteProduceViaLeprechaun();
    }

    private boolean noteProduceViaLeprechaun() {
        Rs2NpcModel leprechaun = Microbot.getRs2NpcCache().query().withName("Tool leprechaun").nearestOnClientThread();
        if (leprechaun == null) return false;

        // Note EVERY notable produce type we hold more than one of, not just the patch we're currently
        // harvesting. Otherwise produce from an earlier patch (e.g. grimy herbs) keeps occupying slots
        // and space never actually gets freed. Using an item on the leprechaun notes its whole stack.
        Set<Integer> seen = new HashSet<>();
        List<Rs2ItemModel> toNote = new ArrayList<>();
        for (Rs2ItemModel item : Rs2Inventory.all()) {
            if (item == null || item.isNoted() || item.getName() == null) continue;
            if (!isNotableProduce(item.getName())) continue;
            if (Rs2Inventory.count(item.getId()) <= 1) continue;
            if (seen.add(item.getId())) toNote.add(item);
        }

        boolean notedAny = false;
        for (Rs2ItemModel item : toNote) {
            Rs2Inventory.use(item);
            leprechaun.click("Talk-to");
            Rs2Inventory.waitForInventoryChanges(10000);
            notedAny = true;
        }
        if (notedAny) return true;

        if (Rs2Inventory.hasItem("Weeds")) {
            Rs2Inventory.dropAll("Weeds");
            return true;
        }
        if (Rs2Inventory.hasItem(ItemID.BUCKET_EMPTY)) {
            Rs2Inventory.drop(ItemID.BUCKET_EMPTY);
            return true;
        }
        return false;
    }

    /** A limpwurt patch harvests in bulk; if the inventory fills mid-harvest the surplus roots drop
     *  to the ground. Reclaim ONLY our own dropped roots (never another player's), noting between
     *  pickups to make room. Bounded loop so a contested/uncollectable drop can't spin forever. */
    private void recoverOwnLimpwurtDrops() {
        for (int i = 0; i < 10; i++) {
            Rs2TileItemModel root = Microbot.getRs2TileItemCache().query()
                    .withId(ItemID.LIMPWURT_ROOT)
                    .where(Rs2TileItemModel::isOwned)
                    .within(3)
                    .nearest();
            if (root == null) return;
            if (Rs2Inventory.isFull() && !noteProduceViaLeprechaun()) return; // can't free space, give up
            if (!root.pickup()) return;
            Rs2Inventory.waitForInventoryChanges(3000);
        }
    }

    private static final String[] ALLOTMENT_PRODUCE = {
            "Potato", "Onion", "Cabbage", "Tomato", "Sweetcorn", "Strawberry", "Watermelon", "Snape grass"
    };
    private static final String[] FLOWER_PRODUCE = {
            "Marigold", "Rosemary", "Nasturtium", "Woad leaf", "Limpwurt root", "White lily"
    };

    /** A farming product worth noting for space: any grimy herb, or allotment/flower produce. Produce
     *  names are matched exactly so seeds ("Potato seed", "Marigold seed") are never caught. */
    private static boolean isNotableProduce(String name) {
        if (name.startsWith("Grimy")) return true;
        for (String p : ALLOTMENT_PRODUCE) if (name.equals(p)) return true;
        for (String p : FLOWER_PRODUCE) if (name.equals(p)) return true;
        return false;
    }

    // --- Seed helpers ---

    private HerbSeedType getFirstHerbSeedInInventory() {
        for (HerbSeedType herbType : HerbSeedType.values()) {
            if (herbType != HerbSeedType.BEST && Rs2Inventory.hasItem(herbType.getItemId())) {
                return herbType;
            }
        }
        return null;
    }

    private AllotmentSeedType getFirstAllotmentSeedInInventory() {
        if (config.allotmentSeedType() != AllotmentSeedType.BEST) {
            AllotmentSeedType selected = config.allotmentSeedType();
            if (Rs2Inventory.hasItem(selected.getItemId())) return selected;
            return null;
        }
        for (AllotmentSeedType seed : AllotmentSeedType.getPlantableSeeds(
                Microbot.getClient().getRealSkillLevel(Skill.FARMING))) {
            if (Rs2Inventory.hasItem(seed.getItemId())) return seed;
        }
        return null;
    }

    // --- Banking ---

    private boolean setupAutoInventory() {
        Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint(), 20);

        if (!Rs2Bank.openBank()) {
            log("Failed to open bank");
            return false;
        }
        if (!sleepUntil(Rs2Bank::isOpen, 10000)) {
            log("Timeout waiting for bank to open after 10 seconds");
            return false;
        }

        Rs2Bank.depositAll();
        Rs2Inventory.waitForInventoryChanges(5000);

        int herbPatchCount = (int) herbPatches.stream().filter(HerbPatch::isEnabled).count();
        int allotmentLocationCount = countEnabledAllotmentFlowerLocations();

        boolean toolsOk = true;
        toolsOk &= Rs2Bank.withdrawX(ItemID.RAKE, 1);
        toolsOk &= Rs2Bank.withdrawX(ItemID.SPADE, 1);
        toolsOk &= Rs2Bank.withdrawX(ItemID.DIBBER, 1);
        if (!toolsOk) {
            log("Missing farming tools in bank (rake/spade/dibber)");
            return false;
        }

        if (Rs2Bank.hasItem(ItemID.FAIRY_ENCHANTED_SECATEURS)) {
            Rs2Bank.withdrawX(ItemID.FAIRY_ENCHANTED_SECATEURS, 1);
        }

        boolean missingRunes = false;
        missingRunes |= !Rs2Bank.withdrawX(ItemID.LAWRUNE, 20);
        missingRunes |= !Rs2Bank.withdrawX(ItemID.AIRRUNE, 50);
        missingRunes |= !Rs2Bank.withdrawX(ItemID.EARTHRUNE, 50);
        missingRunes |= !Rs2Bank.withdrawX(ItemID.FIRERUNE, 50);
        missingRunes |= !Rs2Bank.withdrawX(ItemID.WATERRUNE, 50);

        if (missingRunes) {
            log("Missing teleportation runes - cannot complete herb run");
            return false;
        }

        if (config.enableMorytania() && Rs2Bank.hasItem(ItemID.ECTOPHIAL)) {
            Rs2Bank.withdrawX(ItemID.ECTOPHIAL, 1);
        }

        // Withdraw herb seeds
        HerbSeedType seedType = config.herbSeedType();
        if (seedType == HerbSeedType.BEST) {
            if (!withdrawBestAvailableSeeds(herbPatchCount)) {
                log("Failed to withdraw best available herb seeds for " + herbPatchCount + " patches");
                return false;
            }
        } else {
            if (!withdrawSpecificSeeds(seedType.getItemId(), seedType.getSeedName(), herbPatchCount,
                    seedType.getLevelRequired(), config.allowPartialRuns())) {
                return false;
            }
        }

        // Withdraw allotment seeds
        if (config.enableAllotments() && allotmentLocationCount > 0) {
            int allotmentSeedsNeeded = 3 * 2 * allotmentLocationCount;
            AllotmentSeedType allotmentSeed = config.allotmentSeedType();
            if (allotmentSeed == AllotmentSeedType.BEST) {
                if (!withdrawBestAvailableAllotmentSeeds(allotmentSeedsNeeded)) {
                    log("Failed to withdraw allotment seeds");
                    if (!config.allowPartialRuns()) return false;
                }
            } else {
                if (!withdrawSpecificSeeds(allotmentSeed.getItemId(), allotmentSeed.getSeedName(),
                        allotmentSeedsNeeded, allotmentSeed.getLevelRequired(), config.allowPartialRuns())) {
                    if (!config.allowPartialRuns()) return false;
                }
            }
        }

        // Withdraw flower seeds
        if (config.enableFlowers() && allotmentLocationCount > 0) {
            FlowerSeedType flowerSeed = config.flowerSeedType();
            int flowerSeedsNeeded = allotmentLocationCount;
            if (!withdrawSpecificSeeds(flowerSeed.getItemId(), flowerSeed.getSeedName(),
                    flowerSeedsNeeded, flowerSeed.getLevelRequired(), config.allowPartialRuns())) {
                if (!config.allowPartialRuns()) return false;
            }
        }

        // Withdraw compost (bottomless only — non-bottomless comes from leprechaun)
        CompostType compostType = config.compostType();
        if (compostType != CompostType.NONE && compostType.isBottomless()) {
            if (!Rs2Bank.withdrawX(compostType.getItemId(), 1)) {
                log("Failed to withdraw bottomless compost bucket");
                return false;
            }
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 5000);

        log("Inventory setup complete - starting farm run");
        return true;
    }

    private boolean withdrawSpecificSeeds(int itemId, String seedName, int count, int levelRequired, boolean allowPartial) {
        int farmingLevel = Microbot.getClient().getRealSkillLevel(Skill.FARMING);
        if (farmingLevel < levelRequired) {
            log("Cannot plant " + seedName + " - requires Farming level " + levelRequired + " (you have " + farmingLevel + ")");
            return false;
        }

        if (!Rs2Bank.withdrawX(itemId, count)) {
            if (!allowPartial) {
                log("Failed to withdraw " + count + " " + seedName);
                return false;
            }
            int available = Rs2Bank.count(itemId);
            if (available > 0) {
                int toWithdraw = Math.min(available, count);
                Rs2Bank.withdrawX(itemId, toWithdraw);
                log("Partial run: withdrew " + toWithdraw + " " + seedName + " instead of " + count);
            } else {
                log("No " + seedName + " available in bank");
                return false;
            }
        }
        Rs2Inventory.waitForInventoryChanges(2000);
        return true;
    }

    private boolean withdrawBestAvailableSeeds(int patchCount) {
        int farmingLevel = Microbot.getClient().getRealSkillLevel(Skill.FARMING);
        List<HerbSeedType> plantableHerbs = HerbSeedType.getPlantableHerbs(farmingLevel);

        if (plantableHerbs.isEmpty()) {
            log("No herbs can be planted at farming level " + farmingLevel);
            return false;
        }

        int seedsWithdrawn = 0;

        for (HerbSeedType herb : plantableHerbs) {
            if (seedsWithdrawn >= patchCount) break;
            int availableSeeds = Rs2Bank.count(herb.getItemId());
            if (availableSeeds > 0) {
                int toWithdraw = Math.min(availableSeeds, patchCount - seedsWithdrawn);
                if (Rs2Bank.withdrawX(herb.getItemId(), toWithdraw)) {
                    seedsWithdrawn += toWithdraw;
                    log("Withdrew " + toWithdraw + " " + herb.getSeedName());
                }
            }
        }

        if (seedsWithdrawn < patchCount && !config.allowPartialRuns()) {
            return false;
        }
        return seedsWithdrawn > 0;
    }

    private boolean withdrawBestAvailableAllotmentSeeds(int totalSeedsNeeded) {
        int farmingLevel = Microbot.getClient().getRealSkillLevel(Skill.FARMING);
        List<AllotmentSeedType> plantableSeeds = AllotmentSeedType.getPlantableSeeds(farmingLevel);

        if (plantableSeeds.isEmpty()) {
            log("No allotment seeds can be planted at farming level " + farmingLevel);
            return false;
        }

        int seedsWithdrawn = 0;

        for (AllotmentSeedType seed : plantableSeeds) {
            if (seedsWithdrawn >= totalSeedsNeeded) break;
            int available = Rs2Bank.count(seed.getItemId());
            if (available > 0) {
                int toWithdraw = Math.min(available, totalSeedsNeeded - seedsWithdrawn);
                if (Rs2Bank.withdrawX(seed.getItemId(), toWithdraw)) {
                    seedsWithdrawn += toWithdraw;
                    log("Withdrew " + toWithdraw + " " + seed.getSeedName());
                }
            }
        }

        if (seedsWithdrawn < totalSeedsNeeded && !config.allowPartialRuns()) {
            return false;
        }
        return seedsWithdrawn > 0;
    }

    private int countEnabledAllotmentFlowerLocations() {
        int count = 0;
        if (config.enableArdougne()) count++;
        if (config.enableCatherby()) count++;
        if (config.enableVarlamore()) count++;
        if (config.enableFalador()) count++;
        if (config.enableHosidius()) count++;
        if (config.enableMorytania()) count++;
        return count;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        initialized = false;
        allotmentsByRegion.clear();
        flowerByRegion.clear();
        currentPhase = LocationPhase.HERB;
        handledAllotmentIds.clear();
        currentAllotmentId = -1;
    }
}
