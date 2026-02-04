package net.runelite.client.plugins.microbot.mahoganyhomez.v2;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class MahoganyHomesScript extends Script {

    @Inject
    DonderMahoganyHomesPlugin plugin;

    @Inject
    private Rs2TileObjectCache rs2TileObjectCache;

    @Inject
    private Rs2NpcCache rs2NpcCache;

    public boolean run(MahoganyHomesConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) {
                    return;
                }

                checkPlankSack();
                fix();
                finish();
                getNewContract();
                bank();
                walkToHome();


            } catch (Exception ex) {
                log.error("Error during mahogany homes cycle", ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private List<GameObject> getFixableObjects() {
        List<GameObject> objects = plugin.getObjectsToMark();
        List<Hotspot> fixableHotspots = Hotspot.getBrokenHotspots();
        HotspotObjects hotspotObjects = plugin.getCurrentHome().getHotspotObjects();

        // Precompute the set of IDs
        Set<Integer> ids = fixableHotspots.stream()
                .map(hotspot -> hotspotObjects.objects[hotspot.ordinal()].getObjectId())
                .collect(Collectors.toSet());

        // Filter using the precomputed set
        return objects.stream()
                .filter(Objects::nonNull)
                .filter(o -> ids.contains(o.getId()))
                .collect(Collectors.toList());
    }

    private void checkPlankSack() {
        if (plugin.getConfig().usePlankSack() && plugin.getPlankCount() == -1) {
            if (Rs2Inventory.contains(ItemID.PLANK_SACK)) {
                Rs2ItemModel plankSack = Rs2Inventory.get(ItemID.PLANK_SACK);
                if (plankSack != null) {
                    Rs2Inventory.interact(plankSack, "Check");
                    sleep(Rs2Random.randomGaussian(800, 200));
                }
            }
        }
    }

    private int planksInPlankSack() {
        if (plugin.getPlankCount() == -1) {
            log.info("No planks in sack.");
            return 0;
        }
        log.info("Planks in sack: {}", plugin.getPlankCount());
        return plugin.getPlankCount();
    }

    private void tryToUseLadder() {
        log.info("Walker missing transport, trying to find ladder manually.");
        int plane = Rs2Player.getWorldLocation().getPlane();
        Rs2TileObjectModel objectModel = rs2TileObjectCache.query()
                .where(obj -> Home.isLadder(obj.getId(), true))
                .nearestOnClientThread();
        Microbot.getClientThread().invoke(() -> objectModel.click());
        sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() != plane, 5000);
        sleep(200, 600);
    }

    private void fix() {
        if (plugin.getCurrentHome() == null
                || !plugin.getCurrentHome().isInside(Rs2Player.getWorldLocation())
                || Hotspot.isEverythingFixed()) {
            return;
        }

        Rs2WorldPoint playerLocation = Rs2Player.getRs2WorldPoint();
        MahoganyHomesOverlay.setFixableObjects(getFixableObjects());

        // Sort fixable objects by plane and distance
        List<GameObject> sortedObjects = getFixableObjects().stream()
                .sorted(Comparator.comparingInt(TileObject::getPlane).thenComparingInt(o -> o.getWorldLocation().distanceTo2D(playerLocation.getWorldPoint())))
                .collect(Collectors.toList());

        GameObject object = sortedObjects.stream()
                .findFirst()
                .orElse(null);

        if (object == null) {
            log.info("No fixable objects found.");
            return;
        }

        // Find the closest walkable tile around the object
        Rs2WorldPoint objectLocation = Rs2Tile.getNearestWalkableTile(object);

        int pathDistance = objectLocation != null ? objectLocation.distanceToPath(playerLocation.getWorldPoint()) : Integer.MAX_VALUE;
        log.debug("Local Path Distance: {}", pathDistance);

        if (pathDistance > 20) {
            if (Rs2Player.getWorldLocation().getPlane() != object.getWorldLocation().getPlane()) {
                tryToUseLadder();
            } else {
                openDoorToObject(object, objectLocation);
            }
            log.info("Walking to object");

            Rs2Walker.walkTo(object.getWorldLocation(), 3);
        }

        interactWithObject(object);

    }

    private boolean openDoorToObject(GameObject object, Rs2WorldPoint worldPoint) {
        if (Rs2Player.getWorldLocation().getPlane() != object.getWorldLocation().getPlane()) {
            return false;
        }

        log.info("Object location: {}", object.getWorldLocation());
        List<WorldPoint> walkerPath = Rs2Walker.getWalkPath(worldPoint.getWorldPoint());
        log.info("Steps to location: {}", walkerPath.size());
        List<TileObject> doors = getDoorsOnPath(walkerPath);
        log.info("Doors on path: {}", doors.size());

        for (TileObject door : doors) {
            ObjectComposition doorComp = Rs2GameObject.getObjectComposition(door.getId());
            List<String> actions = null;
            if (doorComp != null) {
                actions = Arrays.asList(doorComp.getActions());
            }
            if (actions != null && actions.contains("Open")) {
                log.info("Opening door at: {}", door.getWorldLocation());
                if (Rs2GameObject.interact(door, "Open")) {
                    Rs2Player.waitForWalking();
                    sleep(200, 500);
                    // if it's the last door in the list return true
                    if (door.equals(doors.get(doors.size() - 1)))
                        return true;
                }
            }
        }
        return false;
    }

    @Nonnull
    private static List<TileObject> getDoorsOnPath(List<WorldPoint> walkerPath) {
        List<TileObject> doors = new ArrayList<>();
        for (WorldPoint wp : walkerPath) {
            TileObject door = null;
            var tile = Rs2Walker.getTile(wp);

            if (tile != null) {
                door = tile.getWallObject();
            }

            if (door == null) {
                continue;
            }

            var objectComp = Rs2GameObject.getObjectComposition(door.getId());
            if (objectComp == null) {
                continue;
            }

            String name = objectComp.getName();

            if (Arrays.asList(objectComp.getActions()).contains("Open") && !name.equalsIgnoreCase("Chest")) {
                doors.add(door);
            }

        }
        return doors;
    }

    private void interactWithObject(GameObject object) {
        Hotspot hotspot = Hotspot.getByObjectId(object.getId());
        String action = Objects.requireNonNull(hotspot).getRequiredAction();
        if (Rs2GameObject.interact(object, action)) {
            sleepUntil(() -> {
                String newAction = Objects.requireNonNull(Hotspot.getByObjectId(object.getId())).getRequiredAction();
                return !newAction.equals(action);
            }, 5000);
            sleep(200, 600);
        }

    }

    // Finish by talking to the NPC
    private void finish() {
        if (plugin.getCurrentHome() != null
                && plugin.getCurrentHome().isInside(Rs2Player.getWorldLocation())
                && Hotspot.isEverythingFixed()) {
            if(plugin.getConfig().usePlankSack() && planksInPlankSack() > 0 && !Rs2Inventory.isFull()){
                if (Rs2Inventory.contains(ItemID.PLANK_SACK) && Rs2Inventory.contains(ItemID.STEEL_BAR)) {
                    Rs2ItemModel plankSack = Rs2Inventory.get(ItemID.PLANK_SACK);
                    if (plankSack != null) {
                        Rs2Inventory.interact(plankSack, "Empty");
                        sleep(Rs2Random.randomGaussian(800, 200));
                        Rs2Inventory.interact(plankSack, "Check");
                    }
                }
            }

            var npc = rs2NpcCache.query().withId(plugin.getCurrentHome().getNpcId()).nearestOnClientThread();

            if (npc == null && Rs2Player.getWorldLocation().getPlane() > 0) {
                log.info("We are on the wrong floor, Trying to find ladder to go down");
                tryToUseLadder();
                npc = rs2NpcCache.query().withId(plugin.getCurrentHome().getNpcId()).nearestOnClientThread();
            }

            if (npc != null) {
                Rs2WorldPoint npcLocation = new Rs2WorldPoint(npc.getWorldLocation());
                log.info("Local NPC path distance: {}", npcLocation.distanceToPath(Rs2Player.getWorldLocation()));
                if (npcLocation.distanceToPath(Rs2Player.getWorldLocation()) < 20) {
                    Rs2NpcModel finalNpc = npc;
                    Microbot.getClientThread().invoke(() -> finalNpc.click("Talk-to"));
                    log.info("Getting reward from NPC");
                    sleepUntil(Rs2Dialogue::hasContinue, 10000);
                    if (Rs2Dialogue.hasDialogueText("Please excuse me, I'm rather busy.")) {
                        plugin.setCurrentHome(null);
                    }
                    sleepUntil(() -> !Rs2Dialogue.isInDialogue(), Rs2Dialogue::clickContinue, 6000, 300);
                    sleep(600, 1200);
                } else {
                    log.info("Local NPC path distance is too far, switching to WebWalker.");
                    Rs2Walker.walkTo(npc.getWorldLocation());
                    sleep(1200, 2200);
                }
            }
        }
    }

    // Get new contract
    private void getNewContract() {
        if (plugin.getCurrentHome() == null) {
            if(plugin.getConfig().useNpcContact()) {
                if (Rs2Magic.npcContact("amy")) {
                    handleContractDialogue();
                }
                return;
            }
            WorldPoint contractLocation = getClosestContractLocation();
            if (contractLocation.distanceTo2D(Rs2Player.getWorldLocation()) > 10) {
                log.info("Walking to contract NPC");
                Rs2Walker.walkTo(contractLocation, 5);

            } else {
                log.info("Getting new contract");


                // Search for Mahogany Homes contract NPCs directly by name
                var npc = Rs2Npc.getNpcs()
                    .filter(n -> n.getName() != null &&
                           (n.getName().equals("Amy") ||
                            n.getName().equals("Marlo") ||
                            n.getName().equals("Ellie") ||
                            n.getName().equals("Angelo")))
                    .findFirst()
                    .orElse(null);

                if (npc == null) {
                    log.info("No contract NPC found, waiting before retry");
                    sleep(2000, 3000);  // Wait 2-3 seconds to prevent spam
                    return;
                }

                log.info("NPC found: {}", npc.getName());
                if (Rs2Npc.interact(npc, "Contract")) {
                    handleContractDialogue();
                }

            }

        }

    }

    public void handleContractDialogue() {
        // Reduced timeout and early return if dialogue not available
        if (!sleepUntil(Rs2Dialogue::hasSelectAnOption, Rs2Dialogue::clickContinue, 5000, 300)) {
            log.info("No dialogue options available, returning early");
            return;
        }
        Rs2Dialogue.keyPressForDialogueOption(plugin.getConfig().currentTier().getPlankSelection().getChatOption());
        sleepUntil(Rs2Dialogue::hasContinue, 5000);
        sleep(400, 800);
        sleepUntil(() -> !Rs2Dialogue.isInDialogue(), Rs2Dialogue::clickContinue, 6000, 300);
        sleep(1200, 2200);
    }

    // Bank if we need to
    private void bank() {
        Home currentHome = plugin.getCurrentHome();
        if (currentHome != null
                && plugin.distanceBetween(currentHome.getArea(), Rs2Player.getWorldLocation()) > 0
                && isMissingItems()) {
            ShortestPathPlugin.getPathfinderConfig().setIgnoreTeleportAndItems(true);
            BankLocation bankLocation = Rs2Bank.getNearestBank(currentHome.getLocation());
            ShortestPathPlugin.getPathfinderConfig().setIgnoreTeleportAndItems(false);
            if (Rs2Bank.walkToBank(bankLocation)) {
                if(Rs2Bank.openBank()) {
                    sleepUntil(Rs2Bank::isOpen);
                    if (Rs2Bank.count(plugin.getConfig().currentTier().getPlankSelection().getPlankId()) <= 28 || Rs2Bank.count(ItemID.STEEL_BAR) <= 4 ){
                        System.out.println("Out of Plank or Steel Bar");
                        Microbot.stopPlugin(plugin);
                        return;
                    }
                    if (plugin.getConfig().usePlankSack()) {
                        if (Rs2Inventory.isFull() && !Rs2Inventory.contains(ItemID.STEEL_BAR)) {
                            Rs2Bank.depositAll(plugin.getConfig().currentTier().getPlankSelection().getPlankId());
                            Rs2Inventory.waitForInventoryChanges(5000);
                        }
                        if (steelBarsInInventory() < 4) {
                            Rs2Bank.withdrawX(ItemID.STEEL_BAR, 4 - steelBarsInInventory());
                            Rs2Inventory.waitForInventoryChanges(5000);
                        }

                        Global.sleepUntil(() -> planksInPlankSack() == 28, () -> {
                            Rs2Bank.withdrawAll(plugin.getConfig().currentTier().getPlankSelection().getPlankId());
                            Rs2Inventory.waitForInventoryChanges(1000);
                            sleep(Rs2Random.randomGaussian(800, 200));
                            Rs2ItemModel plankSack = Rs2Inventory.get(ItemID.PLANK_SACK);
                            if (plankSack != null) {
                                NewMenuEntry plankSackEntry = new NewMenuEntry();
                                plankSackEntry.setOption("Use");
                                plankSackEntry.setTarget("<col=ff9040>Plank sack</col>");
                                plankSackEntry.setIdentifier(9);
                                plankSackEntry.setType(MenuAction.CC_OP);
                                plankSackEntry.setParam0(plankSack.getSlot());
                                plankSackEntry.setParam1(983043);
                                plankSackEntry.setItemId(plankSack.getId());
                                plankSackEntry.setWorldViewId(-1);
                                plankSackEntry.setForceLeftClick(false);
                                plankSackEntry.setDeprioritized(false);
                                Microbot.doInvoke(plankSackEntry,Rs2Inventory.itemBounds(plankSack));
                                Rs2Inventory.waitForInventoryChanges(1000);
                                if (Rs2Inventory.isFull()) {
                                    plugin.setPlankCount(28);
                                }
                                log.info("Checking plank sack...");
                            }
                        }, 20000, 1000);

                        if (Rs2Inventory.emptySlotCount() > 0) {
                            Rs2Bank.openBank();
                            Rs2Bank.withdrawAll(plugin.getConfig().currentTier().getPlankSelection().getPlankId());
                            Rs2Bank.closeBank();
                        }
                    } else {
                        // Withdraw steel bars first if needed
                        if (steelBarsNeeded() > steelBarsInInventory()) {
                            Rs2Bank.withdrawX(ItemID.STEEL_BAR, steelBarsNeeded() - steelBarsInInventory());
                            Rs2Inventory.waitForInventoryChanges(5000);
                        }

                        // Calculate if we'll have enough space for planks after steel bars
                        int freeSlots = Rs2Inventory.emptySlotCount();
                        int currentPlanks = planksInInventory() + planksInPlankSack();
                        int additionalPlanksNeeded = planksNeeded() - currentPlanks;

                        if (additionalPlanksNeeded <= 0) {
                            // We already have enough planks
                            log.info("Already have sufficient planks: {}/{}", currentPlanks, planksNeeded());
                        } else if (freeSlots >= additionalPlanksNeeded) {
                            // Withdraw all planks to fill inventory
                            Rs2Bank.withdrawAll(plugin.getConfig().currentTier().getPlankSelection().getPlankId());
                            Rs2Inventory.waitForInventoryChanges(5000);
                        } else {
                            // This should never happen - inventory can't fit required materials
                            log.info("CRITICAL ERROR: Need {} more planks but only {} slots available!", additionalPlanksNeeded, freeSlots);
                            Microbot.showMessage("Please free up inventory space! Need " + additionalPlanksNeeded + " more planks but only " + freeSlots + " slots available. Stopping script.");
                            shutdown();
                            return;
                        }
                    }
                    Rs2Bank.closeBank();
                }

            }
        }
    }

    // Walk to current home
    private void walkToHome() {
        Home currentHome = plugin.getCurrentHome();
        if (currentHome != null
                && plugin.distanceBetween(currentHome.getArea(), Rs2Player.getWorldLocation()) > 0
                && !isMissingItems()) {
            Rs2Walker.walkTo(plugin.getCurrentHome().getLocation(), 3);
        }
    }

    private boolean isMissingItems() {
        return (planksInInventory() + planksInPlankSack()) < planksNeeded()
                || steelBarsInInventory() < steelBarsNeeded();
    }

    private int planksNeeded() {
        return plugin.getCurrentHome().getRequiredPlanks(plugin.getContractTier());
    }

    private int steelBarsNeeded() {
        return plugin.getCurrentHome().getRequiredSteelBars(plugin.getContractTier());
    }

    private int planksInInventory() {
        return Rs2Inventory.count(plugin.getConfig().currentTier().getPlankSelection().getPlankId());
    }

    private int steelBarsInInventory() {
        return Rs2Inventory.count(ItemID.STEEL_BAR);
    }

    // Get closest contract location
    private WorldPoint getClosestContractLocation() {
        List<WorldPoint> contractLocations = new ArrayList<>();
        contractLocations.add(ContractLocation.MAHOGANY_HOMES_ARDOUGNE.getLocation());
        contractLocations.add(ContractLocation.MAHOGANY_HOMES_FALADOR.getLocation());
        contractLocations.add(ContractLocation.MAHOGANY_HOMES_HOSIDIUS.getLocation());
        contractLocations.add(ContractLocation.MAHOGANY_HOMES_VARROCK.getLocation());

        return contractLocations.stream()
                .min(Comparator.comparingInt(wp -> wp.distanceTo2D(Rs2Player.getWorldLocation())))
                .orElse(null);
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
