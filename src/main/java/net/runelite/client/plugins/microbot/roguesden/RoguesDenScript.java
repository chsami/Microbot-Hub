package net.runelite.client.plugins.microbot.roguesden;

import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static net.runelite.client.plugins.microbot.roguesden.Obstacles.OBSTACLES;

public class RoguesDenScript extends Script {

    /** Start topping up run energy below this... */
    private static final int RUN_ENERGY_THRESHOLD = 70;
    /** ...and once at the bank, keep drinking until completely full. Kept separate from the
     *  trigger so the few percent spent walking to the maze entrance can never send us back. */
    private static final int RUN_ENERGY_TARGET = 100;
    private static final List<String> STAMINA_DOSES = dosesLowestFirst(Collections.singletonList(Rs2Potion.getStaminaPotion()));
    private static final List<String> ENERGY_DOSES = dosesLowestFirst(Rs2Potion.getRestoreEnergyPotionsVariants());

    @Getter
    public static int mazeRunsCompleted = 0;
    int currentObstacleIndex;
    boolean hasStaminaPotionInBank = true;
    boolean hasEnergyPotionInBank = true;

    boolean init = false;

    private void initObstacles() {
        currentObstacleIndex = 0;
        Obstacles.Obstacle[] originalObstacles = new Obstacles.Obstacle[]{
                new Obstacles.Obstacle(3048, 4997, 7251),
                new Obstacles.Obstacle(3039, 4999, "Stand"),
                new Obstacles.Obstacle(3029, 5003, "Run"),
                new Obstacles.Obstacle(3023, 5001, "Open", 7255, 0),
                new Obstacles.Obstacle(3011, 5005, "Run"),
                new Obstacles.Obstacle(3004, 5003, "Run"),
                new Obstacles.Obstacle(2988, 5004, 7240),
                new Obstacles.Obstacle(2969, 5016, "Stand"),
                new Obstacles.Obstacle(2967, 5016, "Stand"),
                new Obstacles.Obstacle(2958, 5031, 7239),
                new Obstacles.Obstacle(2958, 5035, "Stand"),
                new Obstacles.Obstacle(2962, 5050, "Stand"),
                new Obstacles.Obstacle(2963, 5056, "Run"),
                new Obstacles.Obstacle(2968, 5061, "Stand", 7246, 0), // Above 80 thieving
                new Obstacles.Obstacle(2974, 5059, "Stand", 7251, 0),  // Above 80 thieving
                new Obstacles.Obstacle(2989, 5058, "Stand"),  // Above 80 thieving
                new Obstacles.Obstacle(2990, 5058, "Open", 7255, 0),  // Above 80 thieving
                new Obstacles.Obstacle(2957, 5072, "Open", 7219, 0),
                new Obstacles.Obstacle(2957, 5076, "Run", 2000),
                new Obstacles.Obstacle(2955, 5092, "Stand"),
                new Obstacles.Obstacle(2955, 5098, "Open", 7219, 0),
                new Obstacles.Obstacle(2963, 5105, "Stand"),
                new Obstacles.Obstacle(2972, 5098, "Stand"),
                new Obstacles.Obstacle(2972, 5094, "Open", 7219, 0),
                new Obstacles.Obstacle(2972, 5093, "Open", 7255, 0),
                new Obstacles.Obstacle(2976, 5087, "Stand", 250),
                new Obstacles.Obstacle(2982, 5087, "Climb", 7240, 250),
                new Obstacles.Obstacle(2992, 5088, "Stand"),
                new Obstacles.Obstacle(2992, 5088, "Search", 7249, 750),
                new Obstacles.Obstacle(2993, 5088, "Stand"),
                new Obstacles.Obstacle(2997, 5088, "Run"),
                new Obstacles.Obstacle(3006, 5088, "Run"),
                new Obstacles.Obstacle(3018, 5080, "tile"),
                new Obstacles.Obstacle(3024, 5082, "Stand"),
                new Obstacles.Obstacle(3031, 5079, "Open", 7255, 0),
                new Obstacles.Obstacle(3032, 5078, "Stand"),
                new Obstacles.Obstacle(3032, 5077, "Open", 7255, 0),
                new Obstacles.Obstacle(3036, 5076, "Stand"),
                new Obstacles.Obstacle(3037, 5076, "Open", 7255, 0),
                new Obstacles.Obstacle(3039, 5079, "Stand"),
                new Obstacles.Obstacle(3040, 5079, "Open", 7255, 0),
                new Obstacles.Obstacle(3042, 5076, "Stand"),
                new Obstacles.Obstacle(3043, 5076, "Open", 7255, 0),
                new Obstacles.Obstacle(3044, 5069, "Stand"),
                new Obstacles.Obstacle(3044, 5068, "Open", 7255, 0),
                new Obstacles.Obstacle(3041, 5068, "Stand"),
                new Obstacles.Obstacle(3041, 5069, "Open", 7255, 0),
                new Obstacles.Obstacle(3040, 5070, "Stand"),
                new Obstacles.Obstacle(3039, 5070, "Open", 7255, 0),
                new Obstacles.Obstacle(3038, 5069, "Stand"),
                new Obstacles.Obstacle(3038, 5068, "Open", 7255, 0),
                new Obstacles.Obstacle(3037, 5052, "Run"), // waypoint so the leg after the door maze stays within canvas-click range
                new Obstacles.Obstacle(3034, 5033, "Stand"),
                new Obstacles.Obstacle(3028, 5034, "Stand"),
                new Obstacles.Obstacle(3024, 5034, "Run"),
                new Obstacles.Obstacle(3014, 5033, "Open", 7255, 0),
                new Obstacles.Obstacle(3010, 5033, "Stand"),
                new Obstacles.Obstacle(3009, 5033, "Open", 7255, 0),
                new Obstacles.Obstacle(3000, 5034, "Run"),
                new Obstacles.Obstacle(2992, 5045, "Stand"),
                new Obstacles.Obstacle(2992, 5053, "Run"),
                new Obstacles.Obstacle(2992, 5067, "Stand"),
                new Obstacles.Obstacle(2992, 5075, "Run"),
                new Obstacles.Obstacle(3009, 5063, "Take"),
                new Obstacles.Obstacle(3028, 5056, "Run"),
                new Obstacles.Obstacle(3028, 5047, "Walk", 1200),
                new Obstacles.Obstacle(3018, 5047, "Crack", 7237, 2000)
        };

        // Temporary list to store filtered obstacles based on Thieving level
        List<Obstacles.Obstacle> filteredObstacles = new ArrayList<>();

        // Iterate through the original array and conditionally add obstacles
        for (Obstacles.Obstacle obstacle : originalObstacles) {
            if (Microbot.getClient().getRealSkillLevel(Skill.THIEVING) < 80) {
                // Include all obstacles except those marked for 80+ thieving
                if (!isAbove80ThievingObstacle(obstacle)) {
                    filteredObstacles.add(obstacle);
                }
            } else {
                // Include all obstacles except those marked for below 80 thieving
                if (!isBelow80ThievingObstacle(obstacle)) {
                    filteredObstacles.add(obstacle);
                }
            }
        }

        // Convert the list back
        OBSTACLES = filteredObstacles.toArray(new Obstacles.Obstacle[0]);
    }

    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                if (!init) {
                    if (!Rs2Player.getSkillRequirement(Skill.THIEVING, 50)
                            && !Rs2Player.getSkillRequirement(Skill.AGILITY, 50)) {
                        Microbot.showMessage("Rogues Den requires at least 50 thieving and agility. Shutting down.");
                        shutdown();
                        return;
                    }

                    hasStaminaPotionInBank = true;
                    hasEnergyPotionInBank = true;
                    Rs2Camera.setPitch(Rs2Random.between(300, 383));
                    initObstacles();
                    Microbot.enableAutoRunOn = false;
                    Rs2Walker.disableTeleports = true;
                    init = true;
                }

                if (Rs2Player.isAnimating() || Rs2Player.isMoving()) return;

                boolean isInMinigame = Rs2Inventory.hasItem(ItemID.ROGUESDEN_GEM);

                if (!isInMinigame) {
                    currentObstacleIndex = 0;

                    if (storeAllItemsInBank()) return;
                    if (useEnergyPotions()) return;
                    if (useStaminaPotion()) return;

                    enterMinigame();
                    return;
                }

                if (useFlashPowder()) return;
                if (useTileObject()) return;

                if (clickObstacle()) return;


                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                ex.printStackTrace();
                Microbot.log(ex.fillInStackTrace().getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean clickObstacle() {
        int closestIndex = IntStream.range(currentObstacleIndex, OBSTACLES.length)
                .boxed()
                .min((i1, i2) -> {
                    double distanceToO1 = Rs2Player.getWorldLocation().distanceTo(OBSTACLES[i1].getTile());
                    double distanceToO2 = Rs2Player.getWorldLocation().distanceTo(OBSTACLES[i2].getTile());
                    return Double.compare(distanceToO1, distanceToO2);
                })
                .orElse(-1); // Return null if OBSTACLES array is empty
        if (closestIndex + 1 > OBSTACLES.length) {
            Microbot.log("wrong index!");
            return true;
        }

        currentObstacleIndex = closestIndex;

        Obstacles.Obstacle currentObstacle = OBSTACLES[closestIndex];
        Obstacles.Obstacle nextObstacle = OBSTACLES[closestIndex + 1];

        if (closestIndex == 0 && !Rs2Player.getWorldLocation().equals(currentObstacle.getTile())) {
            Microbot.getRs2TileObjectCache().query().interact(currentObstacle.getObjectId());
            sleepUntil(() -> Rs2Player.getWorldLocation().equals(currentObstacle.getTile()));
            return true;
        }

        if (Rs2Player.getWorldLocation().equals(currentObstacle.getTile())) {
            // interact with nextObstacle
            handleObstacle(nextObstacle);
        } else {
            if (currentObstacle.getTile().equals(new WorldPoint(3028, 5056, 0))) { //item requirement before going to obstacle
                if (!Rs2Inventory.hasItem(ItemID.ROGUESDEN_FLASH_POWDER)) {
                    handleObstacle(currentObstacle);
                    return true;
                }
            }

            // interact with currentObstacle
            handleObstacle(currentObstacle);
        }
        return false;
    }

    private boolean useFlashPowder() {
        if (Rs2Inventory.hasItem(ItemID.ROGUESDEN_FLASH_POWDER) && Rs2Player.getWorldLocation().getX() < 3026) {
            Microbot.log("Stunning guard");
            Rs2NpcModel guard = Rs2Npc.getNpc(NpcID.ROGUESDEN_GUARD2);
            if (guard == null) return false;

            // A prior stun click that got "I can't reach that!" leaves cantReachTarget set, and
            // interact() then walks to the guard first - a click that drops the item selection.
            // The follow-up click on the guard fires with nothing selected, and since the guard
            // has no left-click actions it dies with "Action not found. Actions=[]". Clearing the
            // flag keeps the select-then-click sequence atomic: the game itself runs us to the
            // guard and applies the powder on arrival.
            Microbot.cantReachTarget = false;
            Microbot.cantReachTargetRetries = 0;

            if (!Rs2Inventory.use(ItemID.ROGUESDEN_FLASH_POWDER)) return false;
            if (!sleepUntil(Rs2Inventory::isItemSelected, 600)) return false;

            if (Rs2Npc.interact(guard, "")) {
                if (Rs2Inventory.waitForInventoryChanges(5000)) {
                    handleObstacle(OBSTACLES[OBSTACLES.length - 3]);
                }
                return true;
            }
        }
        return false;
    }

    private boolean useTileObject() {
        if (Rs2Inventory.hasItem(ItemID.ROGUESDEN_PUZZLE_MOSAIC_TILE1)) {
            Microbot.log("Handle tile door");
            Microbot.getRs2TileObjectCache().query().interact(7234, "Open");
            Rs2Widget.sleepUntilHasWidget("Select");
            Rs2Widget.clickWidget("Select");
            Rs2Inventory.waitForInventoryChanges(3000);
            sleep(1250, 1650);
        }
        return false;
    }

    private static boolean storeAllItemsInBank() {
        sleep(150,300); // some time it does not detect the inventory is empty or naked
        while (!Rs2Inventory.isEmpty() || Rs2Equipment.isWearing())
        {
            if (Rs2Bank.walkToBankAndUseBank())
            {
                if (!Rs2Inventory.isEmpty())
                {
                    Rs2Bank.depositAll();
                }
                if (Rs2Equipment.isWearing())
                {
                    Rs2Bank.depositEquipment();
                }
                sleepGaussian(1200, 400);
                return true;
            }
            sleep(300);
        }
        return false;
    }

    /**
     * Builds the exact potion names we are willing to drink, lowest dose first.
     *
     * <p>Rs2Bank resolves a partial name like "stamina potion" by picking the shortest matching
     * item name, and every dose of a potion has the same name length, so the tie is broken by
     * bank slot - which is always the untouched (4), because the partials we deposit afterwards
     * land in later slots. Each maze run therefore drank a single dose off a fresh (4) and
     * banked the rest, leaving the (3)/(2)/(1) potions to pile up unused. Asking for the exact
     * dose names in order makes us finish those partials first.</p>
     */
    private static List<String> dosesLowestFirst(List<String> potionNames) {
        List<String> doses = new ArrayList<>();
        for (int dose = 1; dose <= 4; dose++) {
            for (String potionName : potionNames) {
                doses.add(potionName + "(" + dose + ")");
            }
        }
        return doses;
    }

    private static boolean hasPotion(List<String> doses) {
        return doses.stream().anyMatch(name -> Rs2Inventory.hasItem(name, true) || Rs2Bank.hasItem(name, true));
    }

    /**
     * Drinks a single dose, using the potion already in the inventory before withdrawing a new one.
     *
     * @return true if a dose was drunk
     */
    private static boolean drinkPotion(List<String> doses) {
        String potion = doses.stream()
                .filter(name -> Rs2Inventory.hasItem(name, true))
                .findFirst()
                .orElse(null);

        if (potion == null) {
            potion = doses.stream()
                    .filter(name -> Rs2Bank.hasItem(name, true))
                    .findFirst()
                    .orElse(null);

            if (potion == null) return false;

            final String toWithdraw = potion;
            Rs2Bank.withdrawOne(toWithdraw, true);
            if (!sleepUntil(() -> Rs2Inventory.hasItem(toWithdraw, true), 3000)) return false;
        }

        if (!Rs2Inventory.interact(potion, "Drink", true)) return false;
        return Rs2Inventory.waitForInventoryChanges(3000);
    }

    private boolean useStaminaPotion() {
        if (Rs2Player.hasStaminaActive() || !hasStaminaPotionInBank) return false;

        // walks to the bank first when we are not standing at one - after a maze run the
        // inventory is often already empty, so nothing else gets us there
        if (Rs2Bank.walkToBankAndUseBank()) {
            if (!Rs2Bank.isOpen()) return true;
            Microbot.log("Looking to withdraw stamina potion...");
            if (!hasPotion(STAMINA_DOSES)) {
                hasStaminaPotionInBank = false;
                Microbot.log("No stamina potion found in the bank. Continue without it...");
                Rs2Bank.depositAll();
                sleepGaussian(600, 150);
                return false;
            }
            drinkPotion(STAMINA_DOSES);
            sleepGaussian(600, 150);
            return true;
        }
        return false;
    }

    private boolean useEnergyPotions() {
        if (Rs2Player.getRunEnergy() >= RUN_ENERGY_THRESHOLD || !hasEnergyPotionInBank) return false;

        if (!Rs2Bank.walkToBankAndUseBank()) return true;
        if (!Rs2Bank.isOpen()) return true;
        Microbot.log("Looking to withdraw energy potion...");

        // Keep drinking from the potion we are holding instead of withdrawing a fresh one per dose:
        // a dose only restores 10% run energy (20% for super energy), so topping up takes several.
        // Withdraws the next potion automatically whenever the held one runs out.
        while (Rs2Player.getRunEnergy() < RUN_ENERGY_TARGET) {
            if (!hasPotion(ENERGY_DOSES)) {
                hasEnergyPotionInBank = false;
                Microbot.log("No energy potion found in the bank. Continue without it...");
                break;
            }
            if (!drinkPotion(ENERGY_DOSES)) break;
            sleepGaussian(600, 150);
        }

        Rs2Bank.depositAll();
        sleepGaussian(600, 150);
        return true;
    }

    private void enterMinigame() {
        WalkerState state = Rs2Walker.walkWithState(new WorldPoint(3056, 4991, 1));
        if (state == WalkerState.ARRIVED) {
            Microbot.getRs2TileObjectCache().query().interact(ObjectID.ROGUESDEN_MAZEENTRANCE);
            sleepUntil(() -> Rs2Inventory.hasItem(ItemID.ROGUESDEN_GEM));
        }
    }

    private void handleObstacle(Obstacles.Obstacle obstacle) {
        if (obstacle.getHint().equalsIgnoreCase("Crack"))
            mazeRunsCompleted++;

        if ((obstacle != OBSTACLES[OBSTACLES.length - 3]) && (obstacle.getHint().equalsIgnoreCase("run") || obstacle.getHint().equalsIgnoreCase("take")) && Rs2Player.getRunEnergy() < 10) {
            Microbot.log("Restoring run energy...");
            sleep(60_000, 120_000);
            return;
        }
        if (Rs2Random.between(1, 10) == 7) {
            Rs2Camera.angleToTile(obstacle.getTile());
            Microbot.log("Rotating camera");
        }

        if (obstacle.getObjectId() != -1) {
            if (obstacle.getObjectId() == 7249) {
                Microbot.getRs2TileObjectCache().query().interact(obstacle.getObjectId(), "search"); // Handles wall searching
            } else {
                Microbot.getRs2TileObjectCache().query().interact(obstacle.getObjectId());
            }
            Rs2Player.waitForWalking();

        } else if (obstacle.getHint().equalsIgnoreCase("take") && !Rs2Inventory.hasItem(ItemID.ROGUESDEN_FLASH_POWDER)) {
            Rs2GroundItem.lootItemsBasedOnLocation(obstacle.getTile(), ItemID.ROGUESDEN_FLASH_POWDER);
            sleepUntil(() -> Rs2Inventory.hasItem(ItemID.ROGUESDEN_FLASH_POWDER));
        } else if (obstacle.getHint().equalsIgnoreCase("tile") && !Rs2Inventory.hasItem(ItemID.ROGUESDEN_PUZZLE_MOSAIC_TILE1)) {
            Rs2GroundItem.lootItemsBasedOnLocation(obstacle.getTile(), ItemID.ROGUESDEN_PUZZLE_MOSAIC_TILE1);
            sleepUntil(() -> Rs2Inventory.hasItem(ItemID.ROGUESDEN_PUZZLE_MOSAIC_TILE1));
        } else {
            if (!Rs2Walker.walkFastCanvas(obstacle.getTile())) {
                Rs2Walker.walkTo(obstacle.getTile());
            }
        }

        sleepGaussian(obstacle.getWait(), obstacle.getWait() / 4);
    }

    // Helper method to check if an obstacle is for above 80 thieving
    private static boolean isAbove80ThievingObstacle(Obstacles.Obstacle obstacle) {
        return (obstacle.tile.getX() == 2968 && obstacle.tile.getY() == 5061) ||
                (obstacle.tile.getX() == 2974 && obstacle.tile.getY() == 5059) ||
                (obstacle.tile.getX() == 2989 && obstacle.tile.getY() == 5058) ||
                (obstacle.tile.getX() == 2990 && obstacle.tile.getY() == 5058);
    }

    // Helper method to check if an obstacle is for below 80 thieving
    private static boolean isBelow80ThievingObstacle(Obstacles.Obstacle obstacle) {
        return (obstacle.tile.getX() == 2957 && obstacle.tile.getY() == 5072) ||
                (obstacle.tile.getX() == 2957 && obstacle.tile.getY() == 5076) ||
                (obstacle.tile.getX() == 2955 && obstacle.tile.getY() == 5092) ||
                (obstacle.tile.getX() == 2955 && obstacle.tile.getY() == 5098) ||
                (obstacle.tile.getX() == 2963 && obstacle.tile.getY() == 5105) ||
                (obstacle.tile.getX() == 2972 && obstacle.tile.getY() == 5098) ||
                (obstacle.tile.getX() == 2972 && obstacle.tile.getY() == 5094) ||
                (obstacle.tile.getX() == 2972 && obstacle.tile.getY() == 5093) ||
                (obstacle.tile.getX() == 2976 && obstacle.tile.getY() == 5087) ||
                (obstacle.tile.getX() == 2982 && obstacle.tile.getY() == 5087) ||
                (obstacle.tile.getX() == 2992 && obstacle.tile.getY() == 5088) ||
                (obstacle.tile.getX() == 2993 && obstacle.tile.getY() == 5088) ||
                (obstacle.tile.getX() == 2997 && obstacle.tile.getY() == 5088) ||
                (obstacle.tile.getX() == 3006 && obstacle.tile.getY() == 5088) ||
                (obstacle.tile.getX() == 3018 && obstacle.tile.getY() == 5080) ||
                (obstacle.tile.getX() == 3024 && obstacle.tile.getY() == 5082) ||
                (obstacle.tile.getX() == 3031 && obstacle.tile.getY() == 5079) ||
                (obstacle.tile.getX() == 3032 && obstacle.tile.getY() == 5078) ||
                (obstacle.tile.getX() == 3032 && obstacle.tile.getY() == 5077) ||
                (obstacle.tile.getX() == 3036 && obstacle.tile.getY() == 5076) ||
                (obstacle.tile.getX() == 3037 && obstacle.tile.getY() == 5076) ||
                (obstacle.tile.getX() == 3039 && obstacle.tile.getY() == 5079) ||
                (obstacle.tile.getX() == 3040 && obstacle.tile.getY() == 5079) ||
                (obstacle.tile.getX() == 3042 && obstacle.tile.getY() == 5076) ||
                (obstacle.tile.getX() == 3043 && obstacle.tile.getY() == 5076) ||
                (obstacle.tile.getX() == 3044 && obstacle.tile.getY() == 5069) ||
                (obstacle.tile.getX() == 3044 && obstacle.tile.getY() == 5068) ||
                (obstacle.tile.getX() == 3041 && obstacle.tile.getY() == 5068) ||
                (obstacle.tile.getX() == 3041 && obstacle.tile.getY() == 5069) ||
                (obstacle.tile.getX() == 3040 && obstacle.tile.getY() == 5070) ||
                (obstacle.tile.getX() == 3039 && obstacle.tile.getY() == 5070) ||
                (obstacle.tile.getX() == 3038 && obstacle.tile.getY() == 5069) ||
                (obstacle.tile.getX() == 3038 && obstacle.tile.getY() == 5068) ||
                (obstacle.tile.getX() == 3037 && obstacle.tile.getY() == 5052) ||
                (obstacle.tile.getX() == 3034 && obstacle.tile.getY() == 5033) ||
                (obstacle.tile.getX() == 3028 && obstacle.tile.getY() == 5034) ||
                (obstacle.tile.getX() == 3024 && obstacle.tile.getY() == 5034) ||
                (obstacle.tile.getX() == 3014 && obstacle.tile.getY() == 5033) ||
                (obstacle.tile.getX() == 3010 && obstacle.tile.getY() == 5033) ||
                (obstacle.tile.getX() == 3009 && obstacle.tile.getY() == 5033) ||
                (obstacle.tile.getX() == 3000 && obstacle.tile.getY() == 5034) ||
                (obstacle.tile.getX() == 2992 && obstacle.tile.getY() == 5045) ||
                (obstacle.tile.getX() == 2992 && obstacle.tile.getY() == 5053);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        init = false;
    }
}
