package net.runelite.client.plugins.microbot.GemCrabKiller;

import net.runelite.api.ItemID;
import net.runelite.api.Varbits;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class GemCrabKillerScript extends Script {
    private final int CAVE_ENTRANCE_ID = 57631;
    private final int CRAB_NPC_ID = 14779;
    private final int CRAB_NPC_DEAD_ID = 14780;
    private final int DIVINE_POTION_HP_COST = 10;
    // Bank threshold is 25% HP without food, plus a small margin
    private final double DIVINE_POTION_MIN_HP_PERCENT = 30;
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
                    if (config.inventorySetup() == null) {
                        Microbot.showMessage("Please select an inventory setup in the plugin settings. If you've already done so, please reselect the inventory setup in the plugin settings.");
                        shutdown();
                        return;
                    }
                    inventorySetup = new Rs2InventorySetup(config.inventorySetup(), mainScheduledFuture);
                }
                eatIfHurt(config);
                switch (gemCrabKillerState) {
                    case WALKING:
                        handleWalking();
                        break;
                    case FIGHTING:
                        Rs2NpcModel crab = Microbot.getRs2NpcCache().query().withId(CRAB_NPC_ID).nearest();
                        handlePotions(config, crab);
                        handleSafety(config);
                        handleFighting(config, crab);
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
        int currentHP = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        if (config.dharokMode()) {
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
        }
        boolean lowHp = isLowHp(config.dharokMode(), currentHP, Rs2Player.getHealthPercentage());
        if (Rs2Inventory.getInventoryFood().isEmpty() && lowHp) {
            gemCrabKillerState = GemCrabKillerState.BANKING;
        }
    }

    static boolean isLowHp(boolean dharokMode, int currentHP, double healthPercentage) {
        return dharokMode ? currentHP <= 2 : healthPercentage < 25d;
    }

    private void handlePotions(GemCrabKillerConfig config, Rs2NpcModel crab) {
        if (!config.useOffensivePotions() || crab == null) {
            return;
        }
        boolean canDrinkDivine = canAffordDivineHpCost(config);
        for (GemCrabKillerPotion.Boost boost : config.offensivePotion().boosts) {
            if (isDivineActive(boost.skill)) {
                continue;
            }
            Rs2ItemModel potion = findPotion(boost.itemNames, canDrinkDivine, isBoostRunOut(boost.skill));
            if (potion == null) {
                continue;
            }
            int boostedBefore = Rs2Player.getBoostedSkillLevel(boost.skill);
            if (Rs2Inventory.interact(potion, "Drink")) {
                // One dose per tick: wait for the boost to register so another potion isn't wasted on the same boost
                sleepUntil(() -> Rs2Player.getBoostedSkillLevel(boost.skill) > boostedBefore, 3000);
                eatIfHurt(config);
                return;
            }
        }
    }

    // Divine potions cost 10 HP: without food to heal it back, the drop must not push us towards a bank trip
    private boolean canAffordDivineHpCost(GemCrabKillerConfig config) {
        int hp = Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
        if (hp <= DIVINE_POTION_HP_COST) {
            return false;
        }
        boolean hasFood = !Rs2Inventory.getInventoryFood().isEmpty();
        double percentAfter = (hp - DIVINE_POTION_HP_COST) * 100d / Rs2Player.getRealSkillLevel(Skill.HITPOINTS);
        return (hasFood && !config.dharokMode()) || percentAfter >= DIVINE_POTION_MIN_HP_PERCENT;
    }

    // Divine potions drop HP, so eating is checked every tick and right after a dose, in any state
    private void eatIfHurt(GemCrabKillerConfig config) {
        if (!config.dharokMode()) {
            Rs2Player.eatAt(50);
        }
    }

    // A divine boost is held for 5 minutes and then drains, so it is only re-drunk once its timer varbit hits 0
    private boolean isDivineActive(Skill skill) {
        int[] timers;
        switch (skill) {
            case ATTACK:
                timers = new int[]{Varbits.DIVINE_SUPER_COMBAT, Varbits.DIVINE_SUPER_ATTACK};
                break;
            case STRENGTH:
                timers = new int[]{Varbits.DIVINE_SUPER_COMBAT, Varbits.DIVINE_SUPER_STRENGTH};
                break;
            case DEFENCE:
                timers = new int[]{Varbits.DIVINE_SUPER_COMBAT, Varbits.DIVINE_SUPER_DEFENCE, Varbits.DIVINE_BASTION, Varbits.DIVINE_BATTLEMAGE};
                break;
            case RANGED:
                timers = new int[]{Varbits.DIVINE_RANGING, Varbits.DIVINE_BASTION};
                break;
            case MAGIC:
                timers = new int[]{Varbits.DIVINE_MAGIC, Varbits.DIVINE_BATTLEMAGE};
                break;
            default:
                return false;
        }
        return Arrays.stream(timers).anyMatch(timer -> Microbot.getVarbitValue(timer) > 0);
    }

    private boolean isBoostRunOut(Skill skill) {
        int real = Rs2Player.getRealSkillLevel(skill);
        int boost = Rs2Player.getBoostedSkillLevel(skill) - real;
        // Magic potions give a flat +4 boost, the others 15% + 5 (re-drink at 20% of that)
        return boost <= (skill == Skill.MAGIC ? 1 : (real * 0.15 + 5) * 0.2);
    }

    // Names are best first. Divine potions are only drunk with enough HP, the rest only once the boost has run out
    private Rs2ItemModel findPotion(String[] itemNames, boolean canDrinkDivine, boolean boostRunOut) {
        for (String itemName : itemNames) {
            boolean divine = itemName.toLowerCase().startsWith("divine");
            if (divine ? !canDrinkDivine : !boostRunOut) {
                continue;
            }
            Rs2ItemModel potion = Rs2Inventory.get(item -> !item.isNoted()
                    && itemName.equalsIgnoreCase(item.getName().replaceAll("\\s*\\(\\d+\\)$", "")));
            if (potion != null) {
                return potion;
            }
        }
        return null;
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
                if (!equipmentMatches) {
                    equipmentMatches = inventorySetup.loadEquipment();
                }
                var inventoryMatches = inventorySetup.loadInventory();
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

    private void handleFighting(GemCrabKillerConfig config, Rs2NpcModel npc) {
        Rs2NpcModel deadNpc = Microbot.getRs2NpcCache().query().withId(CRAB_NPC_DEAD_ID).nearest();
        if (deadNpc != null) {
            totalCrabKills++;
            if (config.lootCrab() && (Rs2Inventory.hasItem(" pickaxe", false) || Rs2Equipment.isWearing(" pickaxe", false)) && !hasLooted) {
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