package net.runelite.client.plugins.microbot.bluedragons;

import lombok.Getter;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
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

    private boolean waitingForLoot = false;
    private long waitForLootStart = 0;
    private long lastActionClickTime = 0;
    private static final long WAIT_FOR_LOOT_TIMEOUT = 5000;
    private static final long ACTION_GRACE = 2400;

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

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn()) return;
                if (!isRunning()) return;

                switch (currentState) {
                    case STARTING:
                        determineStartingState();
                        break;
                    case BANKING:
                        handleBanking();
                        break;
                    case TRAVEL_TO_DRAGONS:
                        handleTravelToDragons();
                        break;
                    case COMBAT:
                        handleCombat();
                        break;
                }

                consecutiveErrors[0] = 0;

            } catch (Exception ex) {
                consecutiveErrors[0]++;
                Microbot.log("Error in Blue Dragons script: " + ex.getMessage());
                if (consecutiveErrors[0] >= 5) {
                    Microbot.log("Too many consecutive errors. Stopping script.");
                    shutdown();
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

        if (!hasTeleportToFalador()) {
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

    private boolean hasDustyKey() {
        return Rs2Inventory.contains("Dusty key");
    }

    private boolean hasTeleportToFalador() {
        return Rs2Inventory.contains("Falador teleport") || Rs2Magic.canCast(MagicAction.FALADOR_TELEPORT);
    }

    private void handleBanking() {
        if (!Rs2Bank.walkToBankAndUseBank(BankLocation.FALADOR_WEST)) return;

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
            currentState = BlueDragonState.COMBAT;
            return;
        }

        Rs2Walker.walkTo(SAFE_SPOT, 0);
        sleepUntil(() -> Rs2Player.distanceTo(SAFE_SPOT) <= 5, 60000);

        if (Rs2Player.distanceTo(SAFE_SPOT) <= 15) {
            Rs2Walker.walkFastCanvas(SAFE_SPOT);
            sleepUntil(this::isPlayerAtSafeSpot, 10000);
        }

        if (isPlayerAtSafeSpot()) {
            hopIfPlayerAtSafeSpot();
            currentState = BlueDragonState.COMBAT;
        }
    }

    /**
     * Non-blocking combat loop — one action per tick, like AIO Fighter.
     * Priority: eat → bank if full → loot → safe spot → attack
     */
    private void handleCombat() {
        Rs2Player.eatAt(config.eatAtHealthPercent());

        if (Rs2Inventory.isFull()) {
            if (config.eatForLoot() && !Rs2Inventory.getInventoryFood().isEmpty()) {
                Rs2Player.eatAt(100);
            } else if (needsBanking()) {
                currentState = BlueDragonState.BANKING;
                return;
            }
        }

        if (config.waitForLoot() && waitingForLoot) {
            if (hasLootNearby()) {
                waitingForLoot = false;
                lootItems();
                return;
            }
            if (System.currentTimeMillis() - waitForLootStart > WAIT_FOR_LOOT_TIMEOUT) {
                waitingForLoot = false;
            }
            return;
        }

        if (hasLootNearby()) {
            lootItems();
            return;
        }

        if (System.currentTimeMillis() - lastActionClickTime < ACTION_GRACE) return;

        if (!isPlayerAtSafeSpot()) {
            Rs2Walker.walkFastCanvas(SAFE_SPOT);
            lastActionClickTime = System.currentTimeMillis();
            return;
        }

        if (config.waitForLoot() && currentTargetId != null) {
            Rs2NpcModel target = Microbot.getRs2NpcCache().query().withId(currentTargetId).nearestOnClientThread();
            if (target != null && target.isDead()) {
                waitingForLoot = true;
                waitForLootStart = System.currentTimeMillis();
                BlueDragonsOverlay.dragonKillCount++;
                currentTargetId = null;
                return;
            }
        }

        if (!Rs2Combat.inCombat() && isPlayerAtSafeSpot()) {
            Rs2NpcModel dragon = getAvailableDragon();
            if (dragon != null) {
                dragon.click("Attack");
                currentTargetId = dragon.getId();
                lastActionClickTime = System.currentTimeMillis();
            }
        }
    }

    private boolean hasLootNearby() {
        if (Rs2GroundItem.exists("Dragon bones", 15)) return true;
        if (Rs2GroundItem.exists("Dragon spear", 15)) return true;
        if (Rs2GroundItem.exists("Shield left half", 15)) return true;
        if (config.lootDragonhide() && Rs2GroundItem.exists("Blue dragonhide", 15)) return true;
        if (config.lootDragonhide() && Rs2GroundItem.exists("Scaly blue dragonhide", 15)) return true;
        if (config.lootEnsouledHead() && Rs2GroundItem.exists("Ensouled dragon head", 15)) return true;
        return false;
    }

    private void lootItems() {
        lootItem("Dragon bones");
        lootItem("Dragon spear");
        lootItem("Shield left half");

        if (config.lootDragonhide()) {
            lootItem("Blue dragonhide");
            lootItem("Scaly blue dragonhide");
        }

        if (config.lootEnsouledHead()) {
            lootItem("Ensouled dragon head");
        }

        if (config.lootUntradables() && (!Rs2Inventory.isFull() || config.eatForLoot())) {
            LootingParameters untradableParams = new LootingParameters(15, 1, 1, 0, false, true);
            untradableParams.setEatFoodForSpace(config.eatForLoot());
            Rs2GroundItem.lootUntradables(untradableParams);
        }

        if (config.lootMiscItems() && (!Rs2Inventory.isFull() || config.eatForLoot())) {
            String[] ignored = config.lootDragonhide() ? new String[0]
                    : new String[]{"Blue dragonhide", "Scaly blue dragonhide"};
            Microbot.log("[Loot] Value-based loot pass (threshold=" + config.lootValueThreshold()
                    + ", lootDragonhide=" + config.lootDragonhide()
                    + ", ignored=" + java.util.Arrays.toString(ignored) + ")");
            LootingParameters valueParams = new LootingParameters(
                    config.lootValueThreshold(), 9999999, 15, 1, 1, false, true, ignored);
            valueParams.setEatFoodForSpace(config.eatForLoot());
            Rs2GroundItem.lootItemBasedOnValue(valueParams);
        }
    }

    private void lootItem(String itemName) {
        if (Rs2Inventory.isFull() && !config.eatForLoot()) return;
        LootingParameters params = new LootingParameters(15, 1, 1, 0, false, true, itemName);
        params.setEatFoodForSpace(config.eatForLoot());
        if (Rs2GroundItem.lootItemsBasedOnNames(params)) {
            Microbot.log("[Loot] Picked up: " + itemName);
            if (itemName.equalsIgnoreCase("Dragon bones")) {
                BlueDragonsOverlay.bonesCollected++;
            } else if (itemName.equalsIgnoreCase("Blue dragonhide")) {
                BlueDragonsOverlay.hidesCollected++;
            }
        }
    }

    private Rs2NpcModel getAvailableDragon() {
        Rs2NpcModel dragon = Microbot.getRs2NpcCache().query()
                .withName("Blue dragon")
                .where(npc -> !npc.isDead())
                .nearestOnClientThread();
        if (dragon == null) return null;

        int id = dragon.getId();
        if ((id == BLUE_DRAGON_ID_1 || id == BLUE_DRAGON_ID_2 || id == BLUE_DRAGON_ID_3) && dragon.hasLineOfSight()) {
            return dragon;
        }
        return null;
    }

    private boolean isPlayerAtSafeSpot() {
        return SAFE_SPOT.equals(Microbot.getClientThread().invoke(() ->
                Microbot.getClient().getLocalPlayer().getWorldLocation()));
    }

    private void hopIfPlayerAtSafeSpot() {
        List<Player> players = Rs2Player.getPlayers(it -> it != null).collect(Collectors.toList());
        boolean occupied = players.stream().anyMatch(p ->
                p != null && !p.equals(Microbot.getClient().getLocalPlayer())
                        && p.getWorldLocation().distanceTo(SAFE_SPOT) <= 1);

        if (occupied) {
            Microbot.log("Player at safe spot. Hopping worlds.");
            Microbot.hopToWorld(findRandomWorld());
            sleep(5000);
        }
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
        if (overlay != null) overlay.setConfig(config);
    }

    public void shutdown() {
        super.shutdown();
        Rs2Walker.setTarget(null);
        Rs2Walker.disableTeleports = false;
        currentTargetId = null;
    }
}
