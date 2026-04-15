package net.runelite.client.plugins.microbot.birdhunter;

import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class BirdHunterScript extends Script {

    private WorldArea dynamicHuntingArea;
    @Getter
    private static WorldPoint initialStartTile;
    @Getter
    private static int huntingRadius;
    @Getter
    private static int randomHandleInventoryTriggerThreshold;
    @Getter
    private static int randomBoneThreshold;

    private final Pair<Integer, Integer> boneThresholdRange = Pair.of(3, 10);
    private final Pair<Integer, Integer> HandleInventoryThresholdRange = Pair.of(18, 25);

    private BirdHunterPlugin plugin;

    public boolean run(BirdHunterConfig config, BirdHunterPlugin plugin) {
        this.plugin = plugin;
        Microbot.log("Bird Hunter script started.");

        initialStartTile = Rs2Player.getWorldLocation();

        randomBoneThreshold = ThreadLocalRandom.current().nextInt(boneThresholdRange.getLeft(), boneThresholdRange.getRight());
        randomHandleInventoryTriggerThreshold = ThreadLocalRandom.current().nextInt(
                HandleInventoryThresholdRange.getLeft(), HandleInventoryThresholdRange.getRight()
        );
        updateHuntingArea(config);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            Rs2Antiban.resetAntibanSettings();
            Rs2Antiban.antibanSetupTemplates.applyHunterSetup();
            Rs2AntibanSettings.actionCooldownChance = 0.1;

            try {
                if (!super.run() || !Microbot.isLoggedIn()) return;

                if (!hasRequiredSnares()) {
                    int required = getAvailableTraps(Rs2Player.getRealSkillLevel(Skill.HUNTER));
                    Microbot.showMessage("Bird Hunter needs at least " + required
                            + " bird snares in inventory for your Hunter level. Stopping plugin.");
                    Microbot.stopPlugin(plugin);
                    return;
                }

                if (!isInHuntingArea()) {
                    Microbot.log("Player is outside the designated hunting area.");
                    walkBackToArea();
                    return;
                }

                handleTraps(config);
                checkForBonesAndHandleInventory(config);

            } catch (Exception ex) {
                Microbot.log(ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        return true;
    }

    private boolean hasRequiredSnares() {
        int hunterLevel = Rs2Player.getRealSkillLevel(Skill.HUNTER);
        int allowedSnares = getAvailableTraps(hunterLevel);

        int snaresInInventory = Rs2Inventory.itemQuantity(ItemID.HUNTING_OJIBWAY_BIRD_SNARE);
        Microbot.log("Allowed snares: " + allowedSnares + ", Snares in inventory: " + snaresInInventory);

        return snaresInInventory >= allowedSnares;
    }

    public void updateHuntingArea(BirdHunterConfig config) {
        huntingRadius = config.huntingRadiusValue();
        int side = (2 * huntingRadius) + 1;
        dynamicHuntingArea = new WorldArea(
                initialStartTile.getX() - huntingRadius,
                initialStartTile.getY() - huntingRadius,
                side, side,
                initialStartTile.getPlane()
        );
    }

    private boolean isInHuntingArea() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        return dynamicHuntingArea.contains(playerLocation);
    }

    private void walkBackToArea() {
        WorldPoint walkableTile = getNearestSafeWalkableTileInArea(dynamicHuntingArea);

        if (walkableTile != null) {
            Rs2Walker.walkFastCanvas(walkableTile);
            Rs2Player.waitForWalking();
        } else {
            Microbot.log("No safe walkable tile found inside the hunting area.");
        }
    }

    private WorldPoint getNearestSafeWalkableTileInArea(WorldArea huntingArea) {
        WorldPoint from = Rs2Player.getWorldLocation();
        WorldPoint nearest = null;
        int bestDist = Integer.MAX_VALUE;

        for (int x = initialStartTile.getX() - huntingRadius; x <= initialStartTile.getX() + huntingRadius; x++) {
            for (int y = initialStartTile.getY() - huntingRadius; y <= initialStartTile.getY() + huntingRadius; y++) {
                WorldPoint candidate = new WorldPoint(x, y, huntingArea.getPlane());
                LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), candidate);
                if (localPoint == null || !huntingArea.contains(candidate)) continue;
                if (!Rs2Tile.isWalkable(localPoint) || isGameObjectAt(candidate)) continue;

                int dist = from.distanceTo(candidate);
                if (dist < bestDist) {
                    bestDist = dist;
                    nearest = candidate;
                }
            }
        }
        return nearest;
    }

    private void handleTraps(BirdHunterConfig config) {
        List<Rs2TileObjectModel> successfulTraps = new ArrayList<>();
        successfulTraps.addAll(Microbot.getRs2TileObjectCache().query().withId(ObjectID.HUNTING_OJIBWAY_TRAP_TRAPPING_JUNGLE).toList());
        successfulTraps.addAll(Microbot.getRs2TileObjectCache().query().withId(ObjectID.HUNTING_OJIBWAY_TRAP_TRAPPING_COLOURED).toList());
        successfulTraps.addAll(Microbot.getRs2TileObjectCache().query().withId(ObjectID.HUNTING_OJIBWAY_TRAP_FULL_DESERT).toList());
        successfulTraps.addAll(Microbot.getRs2TileObjectCache().query().withId(ObjectID.HUNTING_OJIBWAY_TRAP_FULL_WOODLAND).toList());
        successfulTraps.addAll(Microbot.getRs2TileObjectCache().query().withId(ObjectID.HUNTING_OJIBWAY_TRAP_FULL_POLAR).toList());
        successfulTraps.addAll(Microbot.getRs2TileObjectCache().query().withId(ObjectID.HUNTING_OJIBWAY_TRAP_FULL_JUNGLE).toList());
        successfulTraps.addAll(Microbot.getRs2TileObjectCache().query().withId(ObjectID.HUNTING_OJIBWAY_TRAP_FULL_COLOURED).toList());

        List<Rs2TileObjectModel> catchingTraps = new ArrayList<>();
        catchingTraps.addAll(Microbot.getRs2TileObjectCache().query().withId(ObjectID.HUNTING_OJIBWAY_TRAP_FULL_COLOURED).toList());
        catchingTraps.addAll(Microbot.getRs2TileObjectCache().query().withId(ObjectID.HUNTING_OJIBWAY_TRAP_TRAPPING_DESERT).toList());
        catchingTraps.addAll(Microbot.getRs2TileObjectCache().query().withId(ObjectID.HUNTING_OJIBWAY_TRAP_TRAPPING_WOODLAND).toList());
        catchingTraps.addAll(Microbot.getRs2TileObjectCache().query().withId(ObjectID.HUNTING_OJIBWAY_TRAP_TRAPPING_POLAR).toList());
        catchingTraps.addAll(Microbot.getRs2TileObjectCache().query().withId(ObjectID.HUNTING_OJIBWAY_TRAP_FULL_JUNGLE).toList());

        List<Rs2TileObjectModel> failedTraps = new ArrayList<>(Microbot.getRs2TileObjectCache().query().withId(ObjectID.HUNTING_OJIBWAY_TRAP_BROKEN).toList());
        List<Rs2TileObjectModel> idleTraps = new ArrayList<>(Microbot.getRs2TileObjectCache().query().withId(ObjectID.HUNTING_OJIBWAY_TRAP).toList());
        idleTraps.addAll(Microbot.getRs2TileObjectCache().query().withId(ObjectID.HUNTING_OJIBWAY_TRAP_FAILING).toList());

        // Ownership filter: the plugin records a trap's WorldPoint when it spawns
        // on the player's previous-tick tile. Skip everything else — other players'
        // snares should not be clicked, and they must not inflate totalTraps below.
        Set<WorldPoint> owned = plugin.getTraps().keySet();
        Predicate<Rs2TileObjectModel> mine = t -> owned.contains(t.getWorldLocation());
        successfulTraps.removeIf(mine.negate());
        catchingTraps.removeIf(mine.negate());
        failedTraps.removeIf(mine.negate());
        idleTraps.removeIf(mine.negate());

        int availableTraps = getAvailableTraps(Rs2Player.getRealSkillLevel(Skill.HUNTER));
        int totalTraps = successfulTraps.size() + failedTraps.size() + idleTraps.size() + catchingTraps.size();

        if (Microbot.getRs2TileItemCache().query().withId(ItemID.HUNTING_OJIBWAY_BIRD_SNARE).within(20).count() > 0) {
            pickUpBirdSnare();
            return;
        }

        if (totalTraps < availableTraps) {
            setTrap(config);
            return;
        }

        if (!successfulTraps.isEmpty()) {
            for (Rs2TileObjectModel successfulTrap : successfulTraps) {
                if (interactWithTrap(successfulTrap)) {
                    setTrap(config);
                    return;
                }
            }
        }

        if (!failedTraps.isEmpty()) {
            for (Rs2TileObjectModel failedTrap : failedTraps) {
                if (interactWithTrap(failedTrap)) {
                    setTrap(config);
                    return;
                }
            }
        }
    }


    private void setTrap(BirdHunterConfig config) {
        if (!Rs2Inventory.contains(ItemID.HUNTING_OJIBWAY_BIRD_SNARE)) return;

        // Rs2Player.isStandingOnGameObject() also returns true for ground items
        // (dropped loot), which don't actually block snare placement in-game.
        // Only skip the tile when there's a real game object on it (existing
        // trap, tree, rock).
        if (isGameObjectAt(Rs2Player.getWorldLocation())) {
            if (!movePlayerOffObject())
                return;
        }

        layBirdSnare();
    }

    private void layBirdSnare() {
        Rs2ItemModel birdSnare = Rs2Inventory.get(ItemID.HUNTING_OJIBWAY_BIRD_SNARE);
        if (Rs2Inventory.interact(birdSnare, "Lay")) {
            if (sleepUntil(Rs2Player::isAnimating, 2000)) {
                sleepUntil(() -> !Rs2Player.isAnimating(), 3000);
                sleep(1000, 1500);
            }
        } else {
            Microbot.log("Failed to interact with the bird snare.");
        }
    }

    private boolean isGameObjectAt(WorldPoint point) {
        return Microbot.getRs2TileObjectCache().query().within(point, 0).count() > 0;
    }


    private WorldPoint getSafeWalkableTile(WorldArea huntingArea) {
        List<WorldPoint> candidates = new ArrayList<>();

        // Collect all valid candidate tiles
        for (int x = initialStartTile.getX() - huntingRadius; x <= initialStartTile.getX() + huntingRadius; x++) {
            for (int y = initialStartTile.getY() - huntingRadius; y <= initialStartTile.getY() + huntingRadius; y++) {
                WorldPoint candidateTile = new WorldPoint(x, y, huntingArea.getPlane());
                LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), candidateTile);

                if (localPoint != null && huntingArea.contains(candidateTile)) {
                    if (Rs2Tile.isWalkable(localPoint) && !isGameObjectAt(candidateTile)) {
                        candidates.add(candidateTile);
                    }
                }
            }
        }

        System.out.println("Valid candidates:");
        for (WorldPoint candidate : candidates) {
            System.out.println("Candidate tile: " + candidate);
        }

        // If there are valid candidates, return a random one
        if (!candidates.isEmpty()) {
            Random random = new Random();
            return candidates.get(random.nextInt(candidates.size()));
        }

        // If no valid candidates are found, return null
        return null;
    }

    private boolean movePlayerOffObject() {
        WorldPoint nearestWalkable = getSafeWalkableTile(dynamicHuntingArea);
        if (nearestWalkable != null) {
            Rs2Walker.walkFastCanvas(nearestWalkable);
            Rs2Player.waitForWalking();
            return true;
        } else {
            Microbot.log("No safe walkable tile found inside the hunting area.");
        }
        return false;
    }


    private boolean interactWithTrap(Rs2TileObjectModel birdSnare) {
        if (!plugin.getTraps().containsKey(birdSnare.getWorldLocation())) return false;

        // Retry the click until inventory changes (snare returned / loot received).
        // Previously a single click with a 7s inventory-changes wait and 2×2s
        // gaussian sleeps meant ~13s of stall on a missed click.
        int invBefore = Rs2Inventory.count();
        for (int attempt = 0; attempt < 3; attempt++) {
            birdSnare.click();
            if (sleepUntil(() -> Rs2Inventory.count() != invBefore, 2500)) break;
        }
        sleep(Rs2Random.randomGaussian(600, 200));
        return false;
    }

    private void pickUpBirdSnare() {
        if (Microbot.getRs2TileItemCache().query().withId(ItemID.HUNTING_OJIBWAY_BIRD_SNARE).interact("Take")) {
            sleepUntil(() -> Rs2Inventory.contains(ItemID.HUNTING_OJIBWAY_BIRD_SNARE), 2000);
        }
    }

    private void checkForBonesAndHandleInventory(BirdHunterConfig config) {
        if (Rs2Inventory.count("Bones") >= randomBoneThreshold) {
            buryBones(config);
            randomBoneThreshold = ThreadLocalRandom.current().nextInt(boneThresholdRange.getLeft(), boneThresholdRange.getRight());
        }
        if (Rs2Inventory.count() >= randomHandleInventoryTriggerThreshold) {
            handleInventory(config);
            randomHandleInventoryTriggerThreshold = ThreadLocalRandom.current().nextInt(
                    HandleInventoryThresholdRange.getLeft(), HandleInventoryThresholdRange.getRight()
            );
            randomBoneThreshold = ThreadLocalRandom.current().nextInt(boneThresholdRange.getLeft(), boneThresholdRange.getRight());
        }
    }

    private void handleInventory(BirdHunterConfig config) {
        if (config.buryBones()) {
            buryBones(config);
        }
        dropItems(config);
    }

    private void buryBones(BirdHunterConfig config) {
        if (!config.buryBones() || !Rs2Inventory.hasItem("Bones")) return;

        List<Rs2ItemModel> bones = Rs2Inventory.getBones();
        for (Rs2ItemModel bone : bones) {
            if (Rs2Inventory.interact(bone, "Bury")) {
                Rs2Player.waitForXpDrop(Skill.PRAYER, true);
            }
            sleep(Rs2Random.randomGaussian(500, 200));
        }
    }

    // Strict drop whitelist. Replaces an earlier dropAllExcept(keepList) that would
    // nuke the entire inventory if the keep list was misconfigured. Bird snaring
    // only produces Raw bird meat, Bones, and feathers — feathers stack so we let
    // them ride; bones are buried when the config is enabled, dropped otherwise.
    private void dropItems(BirdHunterConfig config) {
        if (config.buryBones()) {
            Rs2Inventory.dropAll("Raw bird meat");
        } else {
            Rs2Inventory.dropAll("Raw bird meat", "Bones");
        }
    }

    public int getAvailableTraps(int hunterLevel) {
        if (hunterLevel >= 80) return 5;
        if (hunterLevel >= 60) return 4;
        if (hunterLevel >= 40) return 3;
        if (hunterLevel >= 20) return 2;
        return 1;
    }
}
