package net.runelite.client.plugins.microbot.animatedarmour;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;


public class AnimatedArmourScript extends Script {

    public static boolean test = false;

    // Armoury entrance double-door (plane 0). From the shortest-path transport data:
    //   2855 3546 0 <-> 2855 3545 0  (Open;Door;24309)
    //   2854 3546 0 <-> 2854 3545 0  (Open;Door;24306)
    // These are already in the walker's transport graph, so pathfinding opens them
    // automatically; the explicit interact below is only a fallback when the walker
    // stalls on the wrong side of the door.
    private static final int[] ARMOURY_DOOR_IDS = {24309, 24306};
    // Armour stand object tile (unchanged from the original plugin).
    private static final WorldPoint ARMOUR_STAND_TILE = new WorldPoint(2851, 3536, 0);

    public boolean run(AnimatedArmourConfig config) {
        Microbot.enableAutoRunOn = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                boolean hasArmorPieces = Rs2Inventory.contains(item -> item.getName().contains("platebody")) &&
                        Rs2Inventory.contains(item -> item.getName().contains("platelegs")) &&
                        Rs2Inventory.contains(item -> item.getName().contains("full helm"));

                if (hasArmorPieces) {
                    // Never bank mid-kill. Only restock food before animating the next
                    // suit, when fewer than two food remain.
                    if (Rs2Inventory.getInventoryFood().size() < 2 && config.foodAmount() > 0) {
                        bankForFood(config);
                    } else {
                        animateArmor(config);
                    }
                } else {
                    Rs2Player.eatAt(Rs2Random.randomGaussian(60, 5));
                    // Emergency only: if we ran dry mid-fight and are about to die,
                    // abandon the kill and bank rather than dying.
                    if (Rs2Inventory.getInventoryFood().isEmpty() && config.foodAmount() > 0
                            && Rs2Player.getHealthPercentage() <= Rs2Random.randomGaussian(20, 3)) {
                        bankForFood(config);
                    } else {
                        loot();
                    }
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                // A stop/restart can interrupt a client-thread call mid-flight (e.g. the
                // door-open fallback). Restore the flag and exit quietly instead of logging
                // a misleading ERROR stack trace.
                if (ex instanceof InterruptedException || ex.getCause() instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void bankForFood(AnimatedArmourConfig config) {
        Rs2Bank.walkToBank(BankLocation.WARRIORS_GUILD);
        Rs2Bank.openBank();
        Rs2Bank.withdrawX(config.food().getId(), config.foodAmount());
        Rs2Bank.closeBank();
        walkToArmourRoom();
    }

    private void walkToArmourRoom() {
        // Walk with a distance tolerance instead of demanding the exact tile -- the
        // exact-tile invocation is what made the short hop slow and prone to re-pathing.
        Rs2Walker.walkTo(ARMOUR_STAND_TILE, 4);
        // Fallback: if we stalled on the far side of the door, click it directly. The
        // door is located by id once it has loaded, so no approach tile is hardcoded.
        WorldPoint loc = Rs2Player.getWorldLocation();
        if (loc != null && loc.distanceTo(ARMOUR_STAND_TILE) > 4) {
            if (Rs2GameObject.interact(ARMOURY_DOOR_IDS, "Open")) {
                sleepUntil(() -> !Rs2Player.isMoving(), 3000);
                Rs2Walker.walkTo(ARMOUR_STAND_TILE, 4);
            }
        }
    }

    public void animateArmor(AnimatedArmourConfig config) {
        // Make sure we actually reached the stand before trying to target it.
        WorldPoint loc = Rs2Player.getWorldLocation();
        if (loc == null || loc.distanceTo(ARMOUR_STAND_TILE) > 8) {
            walkToArmourRoom();
            return;
        }
        Rs2TileObjectModel armorStand = Microbot.getRs2TileObjectCache().query().within(ARMOUR_STAND_TILE, 0).nearest();
        if (armorStand != null) {
            armorStand.click();
            // Eat one tick later, never on the same tick as the animate click (which
            // would cancel it). This uses the idle animate delay to top up HP, but
            // only when it won't be wasted (missing HP >= the food's heal amount).
            sleepUntilNextTick();
            eatDuringAnimateIfEfficient(config);
            Rs2Player.waitForAnimation();
        }
    }

    private void eatDuringAnimateIfEfficient(AnimatedArmourConfig config) {
        if (Rs2Inventory.getInventoryFood().isEmpty()) return;
        int missing = Rs2Player.getRealSkillLevel(Skill.HITPOINTS)
                - Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
        if (missing >= config.food().getHeal()) {
            Rs2Player.useFood();
        }
    }

    public void loot() {
        LootingParameters valueParams = new LootingParameters(
                5,
                1,
                1,
                1,
                false,
                true,
                "platebody,platelegs,full helm,Warrior guild token".split(",")
        );
        if (Rs2GroundItem.lootItemsBasedOnNames(valueParams)) {
            Microbot.pauseAllScripts.compareAndSet(true, false);
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
