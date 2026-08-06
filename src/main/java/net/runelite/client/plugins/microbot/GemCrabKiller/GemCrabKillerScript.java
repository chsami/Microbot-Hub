package net.runelite.client.plugins.microbot.GemCrabKiller;

import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class GemCrabKillerScript extends Script {
    private final int CAVE_ENTRANCE_ID = 57631;
    private final int CRAB_NPC_ID = 14779;
    private final int CRAB_NPC_DEAD_ID = 14780;
    private final WorldPoint CLOSEST_CRAB_LOCATION_TO_BANK = new WorldPoint(1274, 3168, 0);
    public GemCrabKillerState gemCrabKillerState = GemCrabKillerState.WALKING;
    public int totalCrabKills = 0;
    private Rs2InventorySetup inventorySetup = null;
    private boolean hasLooted = false;
    private Instant waitingTimeStart = null;

    public boolean run(GemCrabKillerConfig config) {
        if (config.overrideState()) {
            gemCrabKillerState = config.startState();
        }
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (config.useInventorySetup()) {
                    inventorySetup = new Rs2InventorySetup(config.inventorySetup(), mainScheduledFuture);
                }
                switch (gemCrabKillerState) {
                    case WALKING:
                        handleWalking();
                        break;
                    case FIGHTING:
                        handlePotions(config);
                        handleSafety(config);
                        handleFighting(config);
                        break;
                    case BANKING:
                        handleBanking(config);
                        break;
                    case WAITING:
                        handleWaiting(config);
                        break;
                }

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleSafety(GemCrabKillerConfig config) {
        if (config.dharokMode()) {
            int currentHP = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
            if (currentHP > 10) {
                if (Rs2Inventory.hasItem(ItemID.LOCATOR_ORB)) {
                    Rs2Inventory.interact(ItemID.LOCATOR_ORB, "feel");
                } else if (Rs2Inventory.hasItem(ItemID.DWARVEN_ROCK_CAKE_7510)) {
                    Rs2Inventory.interact(ItemID.DWARVEN_ROCK_CAKE_7510, "guzzle");
                }
            }
            if (currentHP <= 2) {
                Rs2Player.eatAt(100);
            }
            int prayerLevel = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);
            if (prayerLevel >= 25) {
                if (Rs2Random.between(1, 50) > 4) {
                    Rs2Prayer.toggle(Rs2PrayerEnum.RAPID_HEAL, true);
                    sleep(300, 600);
                    Rs2Prayer.toggle(Rs2PrayerEnum.RAPID_HEAL, false);
                }
            }
        } else {
            Rs2Player.eatAt(50);
        }
        var hasFood = !Rs2Inventory.getInventoryFood().isEmpty();
        var healthPercentage = Rs2Player.getHealthPercentage();
        if (!hasFood && healthPercentage < 25d) {
            gemCrabKillerState = GemCrabKillerState.BANKING;
        }
    }

    private void handlePotions(GemCrabKillerConfig config) {
        if (config.useOffensivePotions() && Rs2Combat.inCombat()) {
            if (Rs2Player.drinkCombatPotionAt(Skill.RANGED, false)) {
                Rs2Player.waitForAnimation();
            }
            if (Rs2Player.drinkCombatPotionAt(Skill.MAGIC, false)) {
                Rs2Player.waitForAnimation();
            }
            if (Rs2Player.drinkCombatPotionAt(Skill.STRENGTH)) {
                Rs2Player.waitForAnimation();
            }
            if (Rs2Player.drinkCombatPotionAt(Skill.ATTACK)) {
                Rs2Player.waitForAnimation();
            }
            if (Rs2Player.drinkCombatPotionAt(Skill.DEFENCE)) {
                Rs2Player.waitForAnimation();
            }
        }
    }

    private void handleWaiting(GemCrabKillerConfig config) {
        if (waitingTimeStart == null) {
            waitingTimeStart = Instant.now();
        }
        if (Microbot.getRs2NpcCache().query().withId(CRAB_NPC_ID).nearest() != null) {
            gemCrabKillerState = GemCrabKillerState.FIGHTING;
            waitingTimeStart = null;
            return;
        }
        if (Instant.now().isAfter(waitingTimeStart.plusSeconds(15))) {
            waitingTimeStart = null;
            gemCrabKillerState = GemCrabKillerState.WALKING;
        }
    }

    private void handleBanking(GemCrabKillerConfig config) {
        Rs2Bank.walkToBank(BankLocation.TAL_TEKLAN);
        Rs2Bank.openBank();
        sleepUntil(Rs2Bank::isOpen, 2000);
        if (Rs2Bank.isOpen()) {
            if (config.useInventorySetup()) {
                if (config.useInventorySetup() && config.inventorySetup() == null) {
                    Microbot.showMessage("Please select an inventory setup in the plugin settings. If you've already done so, please reselect the inventory setup in the plugin settings.");
                    shutdown();
                    return;
                }
                var equipmentMatches = inventorySetup.doesEquipmentMatch();
                var inventoryMatches = inventorySetup.doesInventoryMatch();
                if (!equipmentMatches) {
                    equipmentMatches = inventorySetup.loadEquipment();
                }
                if (!inventoryMatches) {
                    inventoryMatches = inventorySetup.loadInventory();
                }
                if (equipmentMatches && inventoryMatches) {
                    Rs2Bank.closeBank();
                    gemCrabKillerState = GemCrabKillerState.WALKING;
                    return;
                } else {
                    Microbot.showMessage("Unable to load inventory setup. Shutting down.");
                    shutdown();
                }
            } else {
                Rs2Bank.depositAllExcept(false, " pickaxe");
                gemCrabKillerState = GemCrabKillerState.WALKING;
            }
        }
        Rs2Bank.closeBank();
    }

    private void handleFighting(GemCrabKillerConfig config) {
        Rs2NpcModel npc = Microbot.getRs2NpcCache().query().withId(CRAB_NPC_ID).nearest();
        Rs2NpcModel deadNpc = Microbot.getRs2NpcCache().query().withId(CRAB_NPC_DEAD_ID).nearest();
        if (deadNpc != null) {
            totalCrabKills++;
            if (config.lootCrab() && Rs2Inventory.hasItem(" pickaxe", false) && !hasLooted) {
                deadNpc.click("Mine");
                Rs2Inventory.waitForInventoryChanges(2400);
                sleep(3000, 5000);
                hasLooted = true;
                if (Rs2Inventory.isFull()) {
                    gemCrabKillerState = GemCrabKillerState.BANKING;
                    return;
                }
            }
            Microbot.getRs2TileObjectCache().query().withId(CAVE_ENTRANCE_ID).interact("Crawl-through");
            gemCrabKillerState = GemCrabKillerState.WAITING;
            return;
        } else {
            hasLooted = false;
        }
        if (npc == null) {
            gemCrabKillerState = GemCrabKillerState.WALKING;
            return;
        }
        if (!Rs2Player.isInCombat()) {
            npc.click("Attack");
        } else {
            waitingTimeStart = null;
        }
    }

    private void handleWalking() {
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
        }


        Rs2NpcModel npc = Microbot.getRs2NpcCache().query().withId(CRAB_NPC_ID).nearest();
        if (npc != null) {
            gemCrabKillerState = GemCrabKillerState.FIGHTING;
            return;
        }

        Rs2TileObjectModel caveEntrance = Microbot.getRs2TileObjectCache().query().withId(CAVE_ENTRANCE_ID).nearest();
        if (caveEntrance != null) {
            var composition = caveEntrance.getObjectComposition();
            if (composition != null && java.util.Arrays.stream(composition.getActions()).anyMatch("Crawl-through"::equals)) {
                Microbot.getRs2TileObjectCache().query().withId(CAVE_ENTRANCE_ID).interact("Crawl-through");
                sleepUntil(() -> Microbot.getRs2NpcCache().query().withId(CRAB_NPC_ID).nearest() != null, 5000);
                if (Microbot.getRs2NpcCache().query().withId(CRAB_NPC_ID).nearest() != null) {
                    gemCrabKillerState = GemCrabKillerState.FIGHTING;
                }
                return;
            }
        }
        if (npc == null) {
            Rs2Walker.walkTo(CLOSEST_CRAB_LOCATION_TO_BANK);
        }

    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}