package net.runelite.client.plugins.microbot.scurrius;

import com.google.inject.Inject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.scurrius.enums.State;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class ScurriusScript extends Script {

    @Inject
    private ScurriusConfig config;

    private long lastEatTime = -1;
    private long lastPrayerTime = -1;
    private static final int EAT_COOLDOWN_MS = 2000;
    private static final int PRAYER_COOLDOWN_MS = 2000;

    final WorldPoint bossLocation = new WorldPoint(3279, 9869, 0);
    static final WorldArea FIGHT_ROOM = new WorldArea(new WorldPoint(3290, 9860, 0), 17, 17);
    final WorldArea fightRoom = FIGHT_ROOM;
    public static State state = State.BANKING;
    Rs2NpcModel scurrius = null;
    private State previousState = null;
    private boolean hasLoggedRespawnWait = false;
    private Boolean previousInFightRoom = null;
    private static final int TELEPORT_TAB_ID = ItemID.POH_TABLET_VARROCKTELEPORT;
    private static final long TELEPORT_RETRY_COOLDOWN_MS = 5000;
    /** Set when the script decided to teleport out; stops the fight rules pulling the state back until the room is left. */
    private boolean emergencyActive = false;
    private long emergencyRetryAt = 0;
    private boolean loggedNoTeleport = false;
    /** Percent thresholds for the next eat and the next prayer potion; -1 until rolled. */
    private int eatThreshold = -1;
    private int prayerThreshold = -1;
    /** Prayer flicking and rock dodging run on game ticks in this driver, owned by the plugin. */
    private volatile ScurriusTickDriver tickDriver;

    public void setTickDriver(ScurriusTickDriver tickDriver) {
        this.tickDriver = tickDriver;
    }

    private boolean isAttackHeld() {
        ScurriusTickDriver driver = tickDriver;
        return driver != null && driver.isAttackHeld();
    }

    public boolean run(ScurriusConfig config) {
        this.config = config;
        Microbot.enableAutoRunOn = true;
        List<Integer> importantItems = List.of(config.foodSelection().getId(), config.potionSelection().getItemId(), ItemID.POH_TABLET_VARROCKTELEPORT);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (Rs2Player.getWorldLocation() == null) return;
                long startTime = System.currentTimeMillis();
                long currentTime = System.currentTimeMillis();

                if (state != previousState) {
                    Microbot.log("State changed to: " + getStateDescription(state));
                    previousState = state;
                }

                scurrius = Microbot.getRs2NpcCache().query().withName("Scurrius").nearestOnClientThread();

                boolean hasFood = !Rs2Inventory.getInventoryFood().isEmpty();
                boolean hasPrayerPotions = Rs2Inventory.hasItem("prayer potion") || Rs2Inventory.hasItem("super restore");
                boolean isScurriusPresent = scurrius != null;
                boolean isInFightRoom = isInFightRoom();
                boolean hasLineOfSightWithScurrius = scurrius != null && scurrius.hasLineOfSight();

                if (previousInFightRoom == null || isInFightRoom != previousInFightRoom) {
                    Microbot.log(isInFightRoom ? "Player has entered the boss room." : "Player has exited the boss room.");
                    previousInFightRoom = isInFightRoom;
                }

                if (emergencyActive && !isInFightRoom) {
                    emergencyActive = false; // the teleport worked: the normal rules apply again (bank, walk back)
                }

                if (!isScurriusPresent && !hasFood) {
                    if (isInFightRoom) {
                        if (Rs2Inventory.hasItem(TELEPORT_TAB_ID)) {
                            emergencyActive = true;
                            state = State.TELEPORT_AWAY;
                        } else if (!loggedNoTeleport) {
                            Microbot.log("No teleport available. Attempting to walk to bank (will likely fail).");
                            loggedNoTeleport = true;
                        }
                    } else {
                        state = State.BANKING;
                    }
                }

                if (state == State.FIGHTING) {
                    if (!isScurriusPresent && hasFood && hasPrayerPotions && isInFightRoom) {
                        state = State.WAITING_FOR_BOSS;
                        hasLoggedRespawnWait = false;
                    }
                }

                if (state != State.WAITING_FOR_BOSS) {
                    // Inside the room Scurrius is attackable without line of sight (the Attack click walks to it); a
                    // teleport that was just decided on must not be undone by this rule.
                    if (isScurriusPresent && hasFood && (hasLineOfSightWithScurrius || isInFightRoom)
                            && !emergencyActive && state != State.TELEPORT_AWAY) {
                        state = State.FIGHTING;
                    }

                    if (isScurriusPresent && !hasFood && Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) < 25
                            && Rs2Inventory.hasItem(TELEPORT_TAB_ID)) {
                        emergencyActive = true;
                        state = State.TELEPORT_AWAY;
                    }

                    if (!isScurriusPresent && !isInFightRoom && hasFood && hasPrayerPotions) {
                        if (!hasRequiredSupplies()) {
                            Microbot.log("Missing supplies, returning to BANKING.");
                            state = State.BANKING;
                        } else {
                            state = State.WALK_TO_BOSS;
                        }
                    }
                }

                switch (state) {
                    case BANKING:
                        boolean isCloseToBank = Rs2Bank.walkToBank();
                        if (isCloseToBank) {
                            Rs2Bank.openBank();
                        }
                        if (Rs2Bank.isOpen()) {
                            Rs2Bank.depositAllExcept(importantItems.toArray(new Integer[0]));

                            int requiredFoodAmount = config.foodAmount();
                            int requiredPotionAmount = config.prayerPotionAmount();
                            int requiredTeleports = config.teleportAmount();

                            if (!Rs2Bank.withdrawDeficit(config.foodSelection().getId(), requiredFoodAmount)) {
                                Microbot.showMessage("Missing Food in Bank");
                                shutdown();
                                return;
                            }
                            if (!Rs2Bank.withdrawDeficit(config.potionSelection().getItemId(), requiredPotionAmount)) {
                                Microbot.showMessage("Missing Potion in Bank");
                                shutdown();
                                return;
                            }
                            if (!Rs2Bank.withdrawDeficit(ItemID.POH_TABLET_VARROCKTELEPORT, requiredTeleports)) {
                                Microbot.showMessage("Missing Teleports in Bank");
                                shutdown();
                                return;
                            }

                            Rs2Bank.closeBank();
                            sleepUntil(() -> !Rs2Bank.isOpen());
                        }
                        break;

                    case FIGHTING:
                        if (tryEmergencyTeleport(currentTime)) break;
                        // Eating and drinking are not gated on animating: with a blowpipe the player animates all the time.
                        // The threshold is a percent of the real level, rolled again after each eat or drink.
                        if (currentTime - lastEatTime > EAT_COOLDOWN_MS) {
                            if (eatThreshold < 0) eatThreshold = randomThreshold(config.minEatPercent(), config.maxEatPercent());
                            int hp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
                            int maxHp = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);

                            if (shouldEat(hp, maxHp, eatThreshold) && Rs2Player.useFood()) {
                                Microbot.log("Eating at " + hp + "/" + maxHp + " hp (" + (hp * 100 / maxHp) + "%), threshold "
                                        + eatThreshold + "%.");
                                lastEatTime = currentTime;
                                eatThreshold = -1;
                            }
                        }

                        if (currentTime - lastPrayerTime > PRAYER_COOLDOWN_MS) {
                            if (prayerThreshold < 0) prayerThreshold = randomThreshold(config.minPrayerPercent(), config.maxPrayerPercent());
                            int prayer = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
                            int maxPrayer = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);

                            // drinkPrayerPotionAt takes prayer points and drinks when the level is not above them
                            if (shouldEat(prayer, maxPrayer, prayerThreshold) && Rs2Player.drinkPrayerPotionAt(prayer)) {
                                Microbot.log("Drinking a prayer potion at " + prayer + "/" + maxPrayer + " prayer ("
                                        + (prayer * 100 / maxPrayer) + "%), threshold " + prayerThreshold + "%.");
                                lastPrayerTime = currentTime;
                                prayerThreshold = -1;
                            }
                        }

                        // Rocks falling on the player: the driver walks off them; no Attack click until clear.
                        if (isAttackHeld()) break;

                        Rs2NpcModel giantRatModel = Microbot.getRs2NpcCache().query()
                                .where(ScurriusScript::isLiveGiantRat)
                                .nearest();
                        if (giantRatModel != null) {
                            boolean didWeAttackAGiantRat = scurrius != null && config.prioritizeRats() && giantRatModel.click("Attack");
                            if (didWeAttackAGiantRat) return;
                        }

                        if (scurrius != null && !Microbot.getClient().getLocalPlayer().isInteracting()) {
                            scurrius.click("Attack");
                        }
                        break;

                    case TELEPORT_AWAY:
                        int tabsBeforeTeleport = Rs2Inventory.count(TELEPORT_TAB_ID);
                        if (tabsBeforeTeleport == 0) {
                            Microbot.log("No Varrock teleport tab to break, staying in the fight.");
                            emergencyActive = false;
                            state = isInFightRoom ? State.FIGHTING : State.BANKING;
                            break;
                        }
                        Rs2Inventory.interact("Varrock teleport", "break");
                        boolean leftFightRoom = sleepUntil(() -> !isInFightRoom(), 10_000);
                        if (!leftFightRoom) {
                            Microbot.log("Teleport did not take us out of the boss room (tabs " + tabsBeforeTeleport
                                    + " -> " + Rs2Inventory.count(TELEPORT_TAB_ID) + "), back to fighting.");
                            emergencyActive = false;
                            emergencyRetryAt = System.currentTimeMillis() + TELEPORT_RETRY_COOLDOWN_MS;
                            state = State.FIGHTING;
                            break;
                        }
                        sleep(600 * 2);
                        disableAllPrayers();
                        state = State.BANKING;
                        break;

                    case WALK_TO_BOSS:
                        if (!hasRequiredSupplies()) {
                            Microbot.log("Missing supplies, restarting pathfinding and returning to Bank.");
                            Rs2Walker.setTarget(null);
                            state = State.BANKING;
                            return;
                        }

                        Rs2Walker.walkTo(bossLocation);
                        String interactionType = config.bossRoomEntryType().getInteractionText();
                        Microbot.getRs2TileObjectCache().query().withId(ObjectID.RAT_BOSS_ENTRANCE).interact(interactionType);
                        sleepUntil(this::isInFightRoom);
                        break;

                    case WAITING_FOR_BOSS:
                        attemptLooting(config);
                        if (!hasLoggedRespawnWait) {
                            Microbot.log("Waiting for Scurris to respawn...");
                            hasLoggedRespawnWait = true;
                            disableAllPrayers();
                        }
                        if (isScurriusPresent) {
                            state = State.FIGHTING;
                            Microbot.log("Scurris has respawned, switching to FIGHTING.");
                        }
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 400, TimeUnit.MILLISECONDS);
        return true;
    }

    /** Teleports out when the food is nearly gone and health is low. Returns true when it switched to TELEPORT_AWAY. */
    private boolean tryEmergencyTeleport(long now) {
        if (now < emergencyRetryAt) return false;
        int foodLeft = Rs2Inventory.getInventoryFood().size();
        int hp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int maxHp = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
        if (!shouldEmergencyTeleport(foodLeft, hp, maxHp, config)) return false;
        if (!Rs2Inventory.hasItem(TELEPORT_TAB_ID)) {
            if (!loggedNoTeleport) {
                Microbot.log("Emergency teleport wanted (food " + foodLeft + ", hp " + hp + "/" + maxHp
                        + ") but there is no Varrock teleport tab. Eating what is left.");
                loggedNoTeleport = true;
            }
            return false;
        }
        loggedNoTeleport = false;
        Microbot.log("Emergency teleport: food " + foodLeft + ", hp " + hp + "/" + maxHp + ".");
        emergencyActive = true;
        state = State.TELEPORT_AWAY;
        return true;
    }

    /**
     * Whether to teleport out: food left at or under the threshold and hitpoints under the percent of real max hitpoints.
     * Pure; hp is the boosted level, maxHp the real level.
     */
    static boolean shouldEmergencyTeleport(int foodLeft, int hp, int maxHp, ScurriusConfig cfg) {
        if (maxHp <= 0) return false;
        return foodLeft <= cfg.emergencyFoodLeft() && hp * 100 < maxHp * cfg.emergencyHpPercent();
    }

    private String getStateDescription(State state) {
        switch (state) {
            case BANKING:
                return "Out of food or prayer potions. Banking.";
            case TELEPORT_AWAY:
                return "Out of food or low on food and health. Teleporting away.";
            case WALK_TO_BOSS:
                return "Scurris is not present, walking to boss.";
            case FIGHTING:
                return "Engaging with Scurris.";
            case WAITING_FOR_BOSS:
                return "Waiting for Scurris to respawn.";
            default:
                return "Unknown state.";
        }
    }

    private boolean isInFightRoom() {
        return isInArea(fightRoom, Rs2Player.getWorldLocation());
    }

    static boolean isInArea(WorldArea area, WorldPoint location) {
        return location != null && area.contains(location);
    }

    static boolean isLiveGiantRat(Rs2NpcModel npc) {
        String name = npc.getName();
        return name != null && name.toLowerCase().contains("giant rat") && !npc.getNpc().isDead();
    }

    /** Whether a level (hitpoints or prayer) is under the percent of its real maximum. Pure. */
    static boolean shouldEat(int level, int maxLevel, int thresholdPercent) {
        return maxLevel > 0 && level * 100 < maxLevel * thresholdPercent;
    }

    static int randomThreshold(int first, int second) {
        int low = Math.min(first, second);
        int high = Math.max(first, second);
        return ThreadLocalRandom.current().nextInt(low, high + 1);
    }

    private boolean hasRequiredSupplies() {
        int foodAmount = config.foodAmount();
        int foodItemId = config.foodSelection().getId();
        int prayerPotionAmount = config.prayerPotionAmount();
        int potionItemId = config.potionSelection().getItemId();

        int currentFoodCount = Rs2Inventory.count(foodItemId);
        if (currentFoodCount < foodAmount) {
            Microbot.log("Not enough food in inventory. Expected: " + foodAmount + ", Found: " + currentFoodCount);
            return false;
        }

        int currentPrayerPotionCount = Rs2Inventory.count(potionItemId);
        if (currentPrayerPotionCount < prayerPotionAmount) {
            Microbot.log("Not enough prayer potions in inventory. Expected: " + prayerPotionAmount + ", Found: " + currentPrayerPotionCount);
            return false;
        }

        return true;
    }

    private void attemptLooting(ScurriusConfig config) {
        List<String> lootItems = parseLootItems(config.lootItems());
        LootingParameters nameParams = new LootingParameters(10, 1, 1, 0, false, true, lootItems.toArray(new String[0]));
        Rs2GroundItem.lootItemsBasedOnNames(nameParams);
        LootingParameters valueParams = new LootingParameters(10, 1, config.lootValueThreshold(), 0, false, true);
        Rs2GroundItem.lootItemBasedOnValue(valueParams);
    }

    private List<String> parseLootItems(String lootFilter) {
        return Arrays.asList(lootFilter.toLowerCase().split(","));
    }

    public void disableAllPrayers() {
        Rs2Prayer.disableAllPrayers();
        Microbot.log("All prayers disabled to preserve prayer points.");
    }

    @Override
    public void shutdown() {
        super.shutdown();
        disableAllPrayers();
    }
}
