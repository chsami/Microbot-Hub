package net.runelite.client.plugins.microbot.bluedragons;

import lombok.Getter;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class BlueDragonsScript extends Script {

    public static BlueDragonState currentState;
    private BlueDragonsConfig config;
    public static final WorldPoint SAFE_SPOT = new WorldPoint(2918, 9781, 0);
    @Getter
    private Integer currentTargetId = null;

    @Inject
    private BlueDragonsOverlay overlay;

    private long lastLootMessageTime = 0;

    private static final int BLUE_DRAGON_ID_1 = 265;
    private static final int BLUE_DRAGON_ID_2 = 266;
    private static final int BLUE_DRAGON_ID_3 = 267;
    private static final int MIN_WORLD = 302;
    private static final int MAX_WORLD = 580;

    private static final String[] ITEMS_TO_KEEP = {
            "Dusty key",
            "Rune pouch", "Divine rune pouch",
            "Law rune", "Water rune", "Air rune", "Dust rune",
            "Falador teleport"
    };

    public boolean run(BlueDragonsConfig config) {
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            mainScheduledFuture.cancel(true);
        }

        this.config = config;
        currentState = BlueDragonState.STARTING;

        if (overlay != null) {
            overlay.resetStats();
            overlay.setScript(this);
            overlay.setConfig(config);
        }

        final int[] consecutiveErrors = {0};
        final int MAX_CONSECUTIVE_ERRORS = 5;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn()) return;
                if (!isRunning()) return;

                if (isInventoryFull() && currentState != BlueDragonState.BANKING && currentState != BlueDragonState.STARTING) {
                    Microbot.log("Inventory full. Switching to BANKING.");
                    currentState = BlueDragonState.BANKING;
                }

                switch (currentState) {
                    case STARTING:
                        determineStartingState();
                        break;

                    case ESCAPE:
                        handleEscape();
                        break;

                    case BANKING:
                        handleBanking();
                        break;

                    case TRAVEL_TO_DRAGONS:
                        handleTravelToDragons();
                        break;

                    case FIGHTING:
                        handleFighting();
                        break;

                    case LOOTING:
                        handleLooting();
                        break;
                }

                consecutiveErrors[0] = 0;

            } catch (Exception ex) {
                consecutiveErrors[0]++;
                Microbot.log("Error in Blue Dragons script: " + ex.getMessage());

                if (config.debugLogs()) {
                    StringBuilder stackTrace = new StringBuilder();
                    for (StackTraceElement element : ex.getStackTrace()) {
                        stackTrace.append(element.toString()).append("\n");
                    }
                    Microbot.log("Stack trace: " + stackTrace);
                }

                if (consecutiveErrors[0] >= MAX_CONSECUTIVE_ERRORS) {
                    Microbot.log("Too many consecutive errors. Stopping script.");
                    shutdown();
                    return;
                }

                if (Rs2Player.isInCombat()) {
                    Rs2Player.eatAt(config.eatAtHealthPercent());
                }
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    private void determineStartingState() {
        boolean hasAgilityOrKey = Microbot.getClient().getRealSkillLevel(Skill.AGILITY) >= 70 || hasDustyKey();

        if (!hasAgilityOrKey) {
            Microbot.log("Requires Agility level 70 or a Dusty Key. Stopping.");
            shutdown();
            return;
        }

        boolean hasTeleport = hasTeleportToFalador();
        if (!hasTeleport) {
            Microbot.log("Missing Falador teleport (tab or runes). Stopping.");
            shutdown();
            return;
        }

        if (needsBanking()) {
            currentState = BlueDragonState.BANKING;
        } else {
            currentState = BlueDragonState.TRAVEL_TO_DRAGONS;
        }
    }

    private boolean needsBanking() {
        int foodCount = Rs2Inventory.getInventoryFood().size();
        boolean hasLoot = Rs2Inventory.contains("Dragon bones")
                || Rs2Inventory.contains("Blue dragonhide")
                || Rs2Inventory.contains("Scaly blue dragonhide")
                || Rs2Inventory.contains("Dragon spear")
                || Rs2Inventory.contains("Shield left half")
                || Rs2Inventory.contains("Ensouled dragon head");

        return hasLoot || foodCount < config.foodAmount();
    }

    private boolean isInventoryFull() {
        return Rs2Inventory.isFull() || Rs2Inventory.emptySlotCount() <= 0;
    }

    private boolean hasDustyKey() {
        return Rs2Inventory.contains("Dusty key");
    }

    private boolean hasTeleportToFalador() {
        if (Rs2Inventory.contains("Falador teleport")) {
            return true;
        }
        return Rs2Magic.canCast(MagicAction.FALADOR_TELEPORT);
    }

    private void handleEscape() {
        Rs2Magic.cast(MagicAction.FALADOR_TELEPORT);
    }

    private void handleBanking() {
        if (!Rs2Bank.walkToBankAndUseBank(BankLocation.FALADOR_WEST)) {
            return;
        }

        int foodCount = Rs2Inventory.getInventoryFood().size();
        boolean hasLoot = Rs2Inventory.contains("Dragon bones")
                || Rs2Inventory.contains("Blue dragonhide")
                || Rs2Inventory.contains("Scaly blue dragonhide")
                || Rs2Inventory.contains("Dragon spear")
                || Rs2Inventory.contains("Shield left half")
                || Rs2Inventory.contains("Ensouled dragon head");

        int foodNeeded = config.foodAmount() - foodCount;

        if (!hasLoot && foodNeeded <= 0) {
            Rs2Bank.closeBank();
            currentState = BlueDragonState.TRAVEL_TO_DRAGONS;
            return;
        }

        if (hasLoot) {
            Rs2Bank.depositAllExcept(ITEMS_TO_KEEP);
            Rs2Inventory.waitForInventoryChanges(600);
            sleep(450, 750);
        }

        foodCount = Rs2Inventory.getInventoryFood().size();
        foodNeeded = config.foodAmount() - foodCount;
        if (foodNeeded > 0) {
            if (!Rs2Bank.hasItem(config.foodType().getName())) {
                Microbot.log("No " + config.foodType().getName() + " in bank. Stopping.");
                Rs2Bank.closeBank();
                shutdown();
                return;
            }
            Rs2Bank.withdrawX(config.foodType().getId(), foodNeeded);
            Rs2Inventory.waitForInventoryChanges(600);
        }

        Rs2Bank.closeBank();
        currentState = BlueDragonState.TRAVEL_TO_DRAGONS;
    }

    private void handleTravelToDragons() {
        if (isPlayerAtSafeSpot()) {
            currentState = BlueDragonState.FIGHTING;
            return;
        }

        boolean walkAttemptSuccessful = Rs2Walker.walkTo(SAFE_SPOT, 0);

        if (!walkAttemptSuccessful) {
            return;
        }

        boolean reachedNearSafeSpot = sleepUntil(() -> Rs2Player.distanceTo(SAFE_SPOT) <= 5, 60000);

        if (reachedNearSafeSpot || Rs2Player.distanceTo(SAFE_SPOT) <= 20) {
            moveToSafeSpot();
        } else {
            int distance = Rs2Player.distanceTo(SAFE_SPOT);
            if (distance < 50) {
                moveToSafeSpot();
            } else {
                currentState = BlueDragonState.BANKING;
                return;
            }
        }

        if (hopIfPlayerAtSafeSpot()) {
            return;
        }

        currentState = BlueDragonState.FIGHTING;
    }

    private void handleFighting() {
        if (currentState != BlueDragonState.FIGHTING) {
            return;
        }

        if (isInventoryFull()) {
            currentState = BlueDragonState.BANKING;
            return;
        }

        Rs2Player.eatAt(config.eatAtHealthPercent());

        if (!isPlayerAtSafeSpot()) {
            moveToSafeSpot();
            return;
        }

        if (hasLootNearby()) {
            currentState = BlueDragonState.LOOTING;
            return;
        }

        if (!underAttack()) {
            Rs2NpcModel dragon = getAvailableDragon();
            if (dragon != null) {
                if (attackDragon(dragon)) {
                    currentTargetId = dragon.getId();
                }
            }
        }
    }

    private boolean hasLootNearby() {
        if (Rs2GroundItem.exists("Dragon bones", 15)) return true;
        if (Rs2GroundItem.exists("Dragon spear", 15)) return true;
        if (Rs2GroundItem.exists("Shield left half", 15)) return true;
        if (config.lootDragonhide() && Rs2GroundItem.exists("Blue dragonhide", 15)) return true;
        if (config.lootEnsouledHead() && Rs2GroundItem.exists("Ensouled dragon head", 15)) return true;
        return false;
    }

    private void handleLooting() {
        if (currentState != BlueDragonState.LOOTING) {
            return;
        }

        if (isInventoryFull()) {
            currentState = BlueDragonState.BANKING;
            return;
        }

        boolean lootedAnything = false;

        if (!isInventoryFull()) {
            lootedAnything |= lootItem("Dragon bones");
        }
        if (!isInventoryFull()) {
            lootedAnything |= lootItem("Dragon spear");
        }
        if (!isInventoryFull()) {
            lootedAnything |= lootItem("Shield left half");
        }

        if (config.lootDragonhide() && !isInventoryFull()) {
            lootedAnything |= lootItem("Blue dragonhide");
            lootedAnything |= lootItem("Scaly blue dragonhide");
        }

        if (config.lootEnsouledHead() && !isInventoryFull()) {
            lootedAnything |= lootItem("Ensouled dragon head");
        }

        if (config.lootUntradables() && !isInventoryFull()) {
            Rs2GroundItem.lootUntradables(new LootingParameters(15, 1, 1, 0, false, true));
        }

        if (config.lootMiscItems() && !isInventoryFull()) {
            Rs2GroundItem.lootItemBasedOnValue(new LootingParameters(config.lootValueThreshold(), 9999999, 8, 1, 1, false, true));
        }

        sleep(300, 500);

        if (isInventoryFull()) {
            currentState = BlueDragonState.BANKING;
            return;
        }

        if (!hasLootNearby() || !lootedAnything) {
            currentState = BlueDragonState.FIGHTING;
            currentTargetId = null;
        }
    }

    private boolean lootItem(String itemName) {
        if (isInventoryFull()) return false;

        LootingParameters params = new LootingParameters(15, 1, 1, 0, false, true, itemName);
        boolean looted = Rs2GroundItem.lootItemsBasedOnNames(params);
        if (looted) {
            if (itemName.equalsIgnoreCase("Dragon bones")) {
                BlueDragonsOverlay.bonesCollected++;
            } else if (itemName.equalsIgnoreCase("Blue dragonhide")) {
                BlueDragonsOverlay.hidesCollected++;
            }
        }
        return looted;
    }

    private Rs2NpcModel getAvailableDragon() {
        Rs2NpcModel dragon = Microbot.getRs2NpcCache().query().withName("Blue dragon").nearestOnClientThread();

        if (dragon != null) {
            boolean correctId = (dragon.getId() == BLUE_DRAGON_ID_1 || dragon.getId() == BLUE_DRAGON_ID_2 || dragon.getId() == BLUE_DRAGON_ID_3);
            boolean hasLineOfSight = dragon.hasLineOfSight();

            if (correctId && hasLineOfSight) {
                return dragon;
            }
        }
        return null;
    }

    private boolean attackDragon(Rs2NpcModel dragon) {
        final int dragonId = dragon.getId();

        if (Rs2Combat.inCombat() && !dragon.isInteractingWithPlayer()) {
            return false;
        }

        if (dragon.click("Attack")) {
            boolean dragonKilled = sleepUntil(() -> Microbot.getRs2NpcCache().query().withId(dragonId).nearest() == null, 60000);

            if (dragonKilled) {
                BlueDragonsOverlay.dragonKillCount++;
                sleep(600, 900);
                currentState = BlueDragonState.LOOTING;
            }

            return true;
        }
        return false;
    }

    private boolean isPlayerAtSafeSpot() {
        return SAFE_SPOT.equals(Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation()));
    }

    private void moveToSafeSpot() {
        Microbot.pauseAllScripts.compareAndSet(false, true);

        int distance = Rs2Player.distanceTo(SAFE_SPOT);

        if (distance > 15) {
            Rs2Walker.walkTo(SAFE_SPOT, 0);
            sleepUntil(() -> Rs2Player.distanceTo(SAFE_SPOT) <= 5, 30000);
        }

        if (!isPlayerAtSafeSpot()) {
            Rs2Walker.walkFastCanvas(SAFE_SPOT);
            sleepUntil(this::isPlayerAtSafeSpot, 15000);
        }

        if (hopIfPlayerAtSafeSpot()) {
            return;
        }

        Microbot.pauseAllScripts.compareAndSet(true, false);
    }

    private boolean hopIfPlayerAtSafeSpot() {
        boolean otherPlayersAtSafeSpot = false;
        List<Player> players = Rs2Player.getPlayers(it -> it != null).collect(Collectors.toList());

        for (Player player : players) {
            if (player != null &&
                    !player.equals(Microbot.getClient().getLocalPlayer()) &&
                    player.getWorldLocation().distanceTo(SAFE_SPOT) <= 1) {
                otherPlayersAtSafeSpot = true;
                break;
            }
        }

        if (otherPlayersAtSafeSpot) {
            Microbot.log("Player at safe spot. Hopping worlds.");
            Microbot.pauseAllScripts.set(true);

            boolean hopSuccess = Microbot.hopToWorld(findRandomWorld());
            sleep(5000);

            Microbot.pauseAllScripts.set(false);
            return hopSuccess;
        }

        return false;
    }

    private int findRandomWorld() {
        int currentWorld = Microbot.getClient().getWorld();
        int targetWorld;
        do {
            targetWorld = MIN_WORLD + new java.util.Random().nextInt(MAX_WORLD - MIN_WORLD);
        } while (targetWorld == currentWorld);
        return targetWorld;
    }

    public void updateConfig(BlueDragonsConfig config) {
        this.config = config;

        if (overlay != null) {
            overlay.setConfig(config);
        }
    }

    private boolean underAttack() {
        return Rs2Combat.inCombat();
    }

    public void shutdown() {
        super.shutdown();
        Rs2Walker.setTarget(null);
        Rs2Walker.disableTeleports = false;
        currentTargetId = null;
    }
}
