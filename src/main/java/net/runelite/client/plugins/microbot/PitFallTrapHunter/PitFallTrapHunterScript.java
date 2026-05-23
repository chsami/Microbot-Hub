package net.runelite.client.plugins.microbot.PitFallTrapHunter;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.hunter.HunterTrap;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PitFallTrapHunterScript extends Script {
    public static int creaturesCaught = 0;
    public boolean hasDied = false;
    public boolean forceBank = false;
    public boolean forceDrop = false;

    @Getter
    @Setter
    private PitFallState state = PitFallState.IDLE;

    // Track which pit we're currently working with
    private PitLocation activePit = null;

    public boolean run(PitFallTrapHunterConfig config, PitFallTrapHunterPlugin plugin) {
        Rs2Antiban.resetAntibanSettings();
        applyAntiBanSettings();
        Rs2Antiban.setActivity(Activity.GENERAL_HUNTER);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (!this.isRunning()) return;

                PitFallTrapHunting creature = config.pitFallTrapHunting();

                // --- Priority checks ---
                if (hasDied) {
                    Microbot.log("We died - Shutting down");
                    shutdown();
                    return;
                }
                if (forceBank) {
                    Microbot.log("Inventory too full - Banking");
                    state = PitFallState.BANKING;
                }
                if (Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) <= config.runToBankHP()) {
                    state = PitFallState.BANKING;
                }

                // --- Fletching check: when inventory nearly full and antler bolts enabled ---
                if (config.fletchAntlerBolts() && Rs2Inventory.count() >= 24
                        && Rs2Inventory.hasItem(creature.getAntlerItemId())
                        && Rs2Inventory.hasItem(ItemID.CHISEL)) {
                    state = PitFallState.FLETCHING;
                }

                // --- Supply checks ---
                if (state != PitFallState.BANKING) {
                    if ((Rs2Inventory.count("axe") < 1 && !Rs2Equipment.isWearing("axe")) && plugin.getTraps().isEmpty()) {
                        Microbot.log("No axe found - Banking");
                        state = PitFallState.BANKING;
                    }
                    if (!Rs2Inventory.hasItem("Knife")) {
                        Microbot.log("No knife found - Banking");
                        state = PitFallState.BANKING;
                    }
                    if (!hasTeasingTool()) {
                        Microbot.log("No teasing stick or hunter's spear found - Banking");
                        state = PitFallState.BANKING;
                    }
                    if (Rs2Inventory.isFull()) {
                        Microbot.log("Inventory full - Banking");
                        state = PitFallState.BANKING;
                    }
                }

                // --- Walk to area if far away ---
                if (!isNearArea(creature) && state != PitFallState.BANKING) {
                    Microbot.log("Walking to " + creature.getName() + " hunting area");
                    Rs2Walker.walkTo(creature.getHuntingPoint());
                    return;
                }

                // --- State machine ---
                switch (state) {
                    case BANKING:
                        handleBanking(config);
                        state = PitFallState.IDLE;
                        break;

                    case FLETCHING:
                        handleFletching(creature);
                        state = PitFallState.IDLE;
                        break;

                    case WOODCUTTING:
                        handleWoodCutting(config, plugin);
                        // Transition to IDLE once we have logs
                        if (Rs2Inventory.hasItem("logs")) {
                            state = PitFallState.IDLE;
                        }
                        break;

                    case SETTING_TRAP:
                        handleSettingTrap(creature, config);
                        break;

                    case TEASING:
                        handleTeasing(creature, plugin);
                        break;

                    case JUMPING:
                        handleJumping(creature, config);
                        break;

                    case CHECKING:
                        handleChecking(plugin, config);
                        break;

                    case IDLE:
                    default:
                        handleIdle(creature, plugin, config);
                        break;
                }

            } catch (Exception ex) {
                System.out.println("PitFallTrapHunter Script Error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    // ==================== STATE HANDLERS ====================

    /**
     * IDLE: Decide what to do next based on current trap state.
     */
    private void handleIdle(PitFallTrapHunting creature, PitFallTrapHunterPlugin plugin, PitFallTrapHunterConfig config) {
        // Check for full traps first (highest priority: collect loot)
        var fullTrap = plugin.getTraps().entrySet().stream()
                .filter(entry -> entry.getValue().getState() == HunterTrap.State.FULL)
                .findFirst().orElse(null);
        if (fullTrap != null) {
            state = PitFallState.CHECKING;
            return;
        }

        // Check for set/spiked traps that need teasing
        var setTrap = plugin.getTraps().entrySet().stream()
                .filter(entry -> entry.getValue().getState() == HunterTrap.State.EMPTY)
                .findFirst().orElse(null);
        if (setTrap != null) {
            // Trap is set, find NPC to tease toward it
            activePit = findPitForWorldPoint(creature, setTrap.getKey());
            if (activePit != null) {
                state = PitFallState.TEASING;
                return;
            }
        }

        // No active traps — need to set one
        if (!Rs2Inventory.hasItem("logs")) {
            state = PitFallState.WOODCUTTING;
        } else {
            state = PitFallState.SETTING_TRAP;
        }
    }

    /**
     * SETTING_TRAP: Walk to a pit and interact with "Trap" to set it (uses a log).
     */
    private void handleSettingTrap(PitFallTrapHunting creature, PitFallTrapHunterConfig config) {
        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) return;

        if (!Rs2Inventory.hasItem("logs")) {
            state = PitFallState.WOODCUTTING;
            return;
        }

        // Pick the nearest unset pit
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        activePit = creature.findNearestPit(playerLoc);
        if (activePit == null) {
            Microbot.log("No pit locations configured");
            return;
        }

        // Walk toward the pit's ground object if not close
        if (playerLoc.distanceTo(activePit.getGroundObjectPoint()) > 5) {
            Rs2Walker.walkTo(activePit.getGroundObjectPoint());
            return;
        }

        // Use Rs2TileObjectCache (Queryable API) for reliable trap setting — lesson from DeadFall
        if (Microbot.getRs2TileObjectCache().query().withId(creature.getPitObjectId()).count() > 0) {
            Microbot.getRs2TileObjectCache().query().interact(creature.getPitObjectId(), "Trap");
            Microbot.log("Setting pitfall trap");
            sleep(config.minSleepAfterLay(), config.maxSleepAfterLay());
            // After setting, transition to teasing
            state = PitFallState.TEASING;
        }
    }

    /**
     * TEASING: Find the nearest 2x2 NPC, determine which side of the pit it's on,
     * walk to the NPC's side of the pit, then tease it.
     */
    private void handleTeasing(PitFallTrapHunting creature, PitFallTrapHunterPlugin plugin) {
        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) return;

        // Find nearest NPC using the queryable API
        Rs2NpcModel npc = findNearestNpc(creature.getNpcName());
        if (npc == null) {
            Microbot.log("No " + creature.getNpcName() + " found nearby - waiting");
            return;
        }

        // Find the best pit for this NPC (closest set pit)
        if (activePit == null) {
            activePit = creature.findNearestPit(npc.getWorldLocation());
        }
        if (activePit == null) {
            Microbot.log("No pit location available");
            state = PitFallState.IDLE;
            return;
        }

        // Determine which side of the pit the NPC is on
        WorldPoint npcLocation = npc.getWorldLocation();
        PitLocation.Side npcSide = activePit.getNpcSide(npcLocation);
        WorldPoint teasingTile = activePit.getTeasingTile(npcSide);

        Microbot.log("NPC is on " + npcSide + " side of " + activePit.getOrientation() + " pit");

        // Walk to the teasing tile on the NPC's side if not already there
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc.distanceTo(teasingTile) > 2) {
            Rs2Walker.walkTo(teasingTile);
            return;
        }

        // Tease the NPC
        if (npc.click("Tease")) {
            Microbot.log("Teasing " + creature.getNpcName());
            Rs2Player.waitForAnimation();
            state = PitFallState.JUMPING;
        }
    }

    /**
     * JUMPING: Run to the spiked pit ground object and interact with "Jump".
     * The player crosses the pit, and the NPC follows and falls in.
     */
    private void handleJumping(PitFallTrapHunting creature, PitFallTrapHunterConfig config) {
        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) return;

        if (activePit == null) {
            state = PitFallState.IDLE;
            return;
        }

        // Walk toward the pit ground object if not close enough
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc.distanceTo(activePit.getGroundObjectPoint()) > 3) {
            Rs2Walker.walkTo(activePit.getGroundObjectPoint());
            return;
        }

        // Jump over the spiked pit
        if (Rs2GameObject.interact(creature.getSpikedPitObjectId(), "Jump")) {
            Microbot.log("Jumping over spiked pit");
            Rs2Player.waitForAnimation();
            sleep(1500, 2500);
            // After jump, NPC should fall in — transition to checking
            state = PitFallState.CHECKING;
        }
    }

    /**
     * CHECKING: Check a full pit to collect loot.
     * Uses Rs2TileObjectCache (Queryable API) for reliable object lookup — lesson from DeadFall.
     */
    private void handleChecking(PitFallTrapHunterPlugin plugin, PitFallTrapHunterConfig config) {
        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) return;

        // Sort by trap time descending to prioritize oldest trap (lesson from DeadFall)
        var fullTrap = plugin.getTraps().entrySet().stream()
                .filter(entry -> entry.getValue().getState() == HunterTrap.State.FULL)
                .sorted((a, b) -> Double.compare(b.getValue().getTrapTimeRelative(), a.getValue().getTrapTimeRelative()))
                .findFirst().orElse(null);

        if (fullTrap != null) {
            WorldPoint location = fullTrap.getKey();
            Rs2TileObjectModel gameObject = Microbot.getRs2TileObjectCache().query().within(location, 0).first();
            if (gameObject != null) {
                if (Rs2Inventory.count() > 24) {
                    forceDrop = true;
                    Rs2Inventory.waitForInventoryChanges(8000);
                }
                gameObject.click("Check");
                creaturesCaught++;
                Microbot.log("Checking pit - total caught: " + creaturesCaught);
                sleep(config.minSleepAfterCatch(), config.maxSleepAfterCatch());
            }
        }

        // Reset for next cycle
        activePit = null;
        state = PitFallState.IDLE;
    }

    // ==================== SUPPORT METHODS ====================

    /**
     * Find the nearest NPC matching the given name. Sunlight antelopes are 2x2 NPCs.
     */
    private Rs2NpcModel findNearestNpc(String npcName) {
        return Microbot.getRs2NpcCache().query()
                .withName(npcName)
                .where(npc -> !npc.isDead())
                .nearestOnClientThread();
    }

    /**
     * Find the PitLocation corresponding to a given world point (for trap tracking).
     */
    private PitLocation findPitForWorldPoint(PitFallTrapHunting creature, WorldPoint trapPoint) {
        return creature.getPitLocations().stream()
                .min(Comparator.comparingInt(pit -> pit.distanceTo(trapPoint)))
                .orElse(null);
    }

    /**
     * Check if player has a teasing stick or hunter's spear in inventory or equipment.
     */
    private boolean hasTeasingTool() {
        for (TeasingTool tool : TeasingTool.values()) {
            if (Rs2Inventory.hasItem(tool.getItemId()) || Rs2Equipment.isWearing(tool.getItemName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the first available teasing tool from equipment or inventory.
     */
    private TeasingTool findTeasingTool() {
        for (TeasingTool tool : TeasingTool.values()) {
            if (Rs2Equipment.isWearing(tool.getItemName()) || Rs2Inventory.hasItem(tool.getItemId())) {
                return tool;
            }
        }
        return null;
    }

    private boolean isNearArea(PitFallTrapHunting creature) {
        WorldPoint currentLocation = Rs2Player.getWorldLocation();
        return currentLocation.distanceTo(creature.getHuntingPoint()) <= 50;
    }

    // ==================== FLETCHING ====================

    private void handleFletching(PitFallTrapHunting creature) {
        if (!Rs2Inventory.hasItem(creature.getAntlerItemId()) || !Rs2Inventory.hasItem(ItemID.CHISEL)) {
            state = PitFallState.IDLE;
            return;
        }
        Microbot.log("Fletching sunlight antler bolts");
        Rs2Inventory.combineClosest(creature.getAntlerItemId(), ItemID.CHISEL);
        Rs2Player.waitForAnimation();
        sleep(1200, 2400);
    }

    // ==================== WOODCUTTING ====================

    private void handleWoodCutting(PitFallTrapHunterConfig config, PitFallTrapHunterPlugin plugin) {
        if (Rs2Player.isInCombat()) {
            Rs2Walker.walkTo(config.pitFallTrapHunting().getHuntingPoint());
            return;
        }

        if (Rs2Inventory.count("logs") > 5) {
            walkBackToHunterArea(config, plugin);
            return;
        }

        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            return;
        }

        GameObject tree = Rs2GameObject.findReachableObject("tree", true, 20, config.pitFallTrapHunting().getHuntingPoint());
        if (tree == null) {
            tree = Rs2GameObject.findReachableObject("dead tree", true, 20, config.pitFallTrapHunting().getHuntingPoint());
        }
        if (tree != null) {
            if (Rs2GameObject.interact(tree, "Chop down")) {
                Rs2Player.waitForAnimation();
                Rs2Antiban.actionCooldown();
            }
        } else {
            Microbot.log("No nearby tree found - Walking back to origin");
            walkBackToHunterArea(config, plugin);
        }
    }

    private void walkBackToHunterArea(PitFallTrapHunterConfig config, PitFallTrapHunterPlugin plugin) {
        if (!plugin.getTraps().isEmpty()) {
            var trap = plugin.getTraps().values().stream().findFirst().orElse(null);
            if (trap != null) {
                Rs2Walker.walkTo(trap.getWorldLocation());
            } else {
                Rs2Walker.walkTo(config.pitFallTrapHunting().getHuntingPoint());
            }
        } else {
            Rs2Walker.walkTo(config.pitFallTrapHunting().getHuntingPoint());
        }
    }

    // ==================== BANKING ====================

    private void handleBanking(PitFallTrapHunterConfig config) {
        Rs2Bank.walkToBank();
        Rs2Bank.openBank();

        PitFallTrapHunting creature = config.pitFallTrapHunting();

        // Handle meat pouch
        if (config.UseMeatPouch()) {
            if (!Rs2Inventory.hasItem(config.MeatPouch().getClosedItemID()) && !Rs2Inventory.hasItem(config.MeatPouch().getOpenItemID())) {
                Rs2Bank.withdrawOne(config.MeatPouch().getOpenItemID());
                sleep(600, 1200);
                Rs2Bank.withdrawOne(config.MeatPouch().getClosedItemID());
                sleep(600, 1200);
            }
            if (Rs2Inventory.hasItem(config.MeatPouch().getClosedItemID())) {
                Rs2Inventory.interact(config.MeatPouch().getClosedItemID(), "Open");
                sleep(400, 600);
            }
            if (Rs2Inventory.hasItem(config.MeatPouch().getOpenItemID())) {
                Rs2Inventory.interact(config.MeatPouch().getOpenItemID(), "Empty");
                sleep(400, 600);
            }
        }

        // Withdraw knife
        if (!Rs2Inventory.hasItem(ItemID.KNIFE)) {
            Rs2Bank.withdrawOne(ItemID.KNIFE);
        }

        // Withdraw chisel if fletching enabled
        if (config.fletchAntlerBolts() && !Rs2Inventory.hasItem(ItemID.CHISEL)) {
            Rs2Bank.withdrawOne(ItemID.CHISEL);
            sleep(300, 600);
        }

        // Ensure we have a teasing tool
        if (!hasTeasingTool()) {
            for (TeasingTool tool : TeasingTool.values()) {
                if (tool.hasRequirements()) {
                    Rs2Bank.withdrawOne(tool.getItemId());
                    sleep(600, 800);
                    if (Rs2Inventory.hasItem(tool.getItemId())) break;
                }
            }
        }

        // Handle axe
        getBestAxe(config);

        // Deposit loot
        var loot = creature.getLootId();
        if (loot != null && loot > 0) {
            Rs2Bank.depositAll(loot);
        }

        // Deposit fur if banking
        if (config.furHandling() == FurHandling.BANK && creature.getFurItemId() > 0) {
            Rs2Bank.depositAll(creature.getFurItemId());
        }

        // Deposit antler bolts (they're the product, always bank them)
        // TODO: deposit antler bolt item ID when known

        sleep(50, 1200);

        // Auto-eat until full
        if (config.AutoEat()) {
            while (!Rs2Player.isFullHealth()) {
                if (!isRunning()) break;
                Rs2Bank.withdrawOne(config.FoodToEatAtBank().getId());
                sleep(200, 400);
                Rs2Inventory.interact(config.FoodToEatAtBank().getId(), "Eat");
                sleep(200, 400);
            }
        }

        if (Rs2Inventory.contains(config.FoodToEatAtBank().getId())) {
            Rs2Bank.depositAll(config.FoodToEatAtBank().getId());
        }

        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            sleep(600, 900);
        }
        hasDied = false;
        forceBank = false;
    }

    // ==================== AXE MANAGEMENT ====================

    private void getBestAxe(PitFallTrapHunterConfig config) {
        Axe bestAxeInInventory = getBestAxe(Rs2Inventory.items().collect(Collectors.toList()), config);
        Axe bestAxeEquipped = getBestAxe(Rs2Equipment.items(), config);
        Axe bestAxeInBank = getBestAxe(Rs2Bank.bankItems(), config);

        Axe bestAxeOverall = null;
        if (bestAxeInInventory != null && (bestAxeOverall == null || bestAxeInInventory.getWoodcuttingLevel() > bestAxeOverall.getWoodcuttingLevel())) {
            bestAxeOverall = bestAxeInInventory;
        }
        if (bestAxeEquipped != null && (bestAxeOverall == null || bestAxeEquipped.getWoodcuttingLevel() > bestAxeOverall.getWoodcuttingLevel())) {
            bestAxeOverall = bestAxeEquipped;
        }
        if (bestAxeInBank != null && (bestAxeOverall == null || bestAxeInBank.getWoodcuttingLevel() > bestAxeOverall.getWoodcuttingLevel())) {
            bestAxeOverall = bestAxeInBank;
        }

        if (bestAxeOverall != null) {
            if (config.axeInInventory()) {
                if (bestAxeOverall == bestAxeEquipped && bestAxeInInventory == null) {
                    Rs2Equipment.unEquip(bestAxeOverall.getItemID());
                    sleep(600, 800);
                } else if (bestAxeOverall == bestAxeInBank && bestAxeInInventory == null) {
                    Rs2Bank.withdrawItem(bestAxeOverall.getItemID());
                    sleep(600, 800);
                }
            } else {
                if (bestAxeOverall == bestAxeInBank && bestAxeEquipped == null) {
                    Rs2Bank.withdrawItem(bestAxeOverall.getItemID());
                    sleep(600, 800);
                    Rs2Inventory.interact(bestAxeOverall.getItemID(), "Wield");
                    sleep(600, 800);
                } else if (bestAxeOverall == bestAxeInInventory && bestAxeEquipped == null) {
                    Rs2Inventory.interact(bestAxeOverall.getItemID(), "Wield");
                }
            }
        } else {
            Microbot.log("No suitable axe found in bank, inventory, or equipment");
            shutdown();
        }
    }

    private Axe getBestAxe(List<Rs2ItemModel> items, PitFallTrapHunterConfig config) {
        Axe best = null;
        for (Axe axe : Axe.values()) {
            if (items.stream().noneMatch(i -> i.getName().toLowerCase().contains(axe.getItemName()))) continue;
            if (axe.hasRequirements(config.axeInInventory())) {
                if (best == null || axe.getWoodcuttingLevel() > best.getWoodcuttingLevel()) {
                    best = axe;
                }
            }
        }
        return best;
    }

    // ==================== ANTIBAN ====================

    private void applyAntiBanSettings() {
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.actionCooldownChance = 0.1;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        creaturesCaught = 0;
        state = PitFallState.IDLE;
        activePit = null;
    }
}
