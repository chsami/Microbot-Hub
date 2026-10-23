package net.runelite.client.plugins.microbot.autochompykiller;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;

import java.util.concurrent.TimeUnit;

@Slf4j
public class AutoChompyKillerScript extends Script {

    /** How close we aim to get to a swamp bubble before clicking it. */
    private static final int BELLOWS_INTERACT_DISTANCE = 8;

    /** How long FILLING_BELLOWS may stall before the loop logs a retry (fills are checked often). */
    private static final long FILLING_BELLOWS_TIMEOUT_MS = 15_000;

    /** How long any other state may make no progress before the loop falls back to FILLING_BELLOWS. */
    private static final long STATE_TIMEOUT_MS = 60_000;

    /**
     * Upper bound on how long a single interaction may hold the loop tick. Once it elapses the tick
     * returns and the next tick observes whether the action landed, so the loop never blocks for the
     * full length of an animation - which keeps the chompy check below responsive.
     */
    private static final int ACTION_START_TIMEOUT_MS = 1_500;

    public static int chompyKills = 0;
    public static long startTime = 0;
    public AutoChompyKillerState state = AutoChompyKillerState.FILLING_BELLOWS;
    private long stateStartTime = System.currentTimeMillis();
    private AutoChompyKillerConfig config;

    private boolean isBloatedToadOnGround() {
        long toadCount = Microbot.getRs2NpcCache().query().withId(NpcID.BLOATED_TOAD).where(element -> element.getWorldLocation().equals(Rs2Player.getWorldLocation())).count();

        return toadCount > 0;
    }

    private boolean isDeadChompyNearby() {
        long deadChompyCount = Microbot.getRs2NpcCache().query().withId(NpcID.CHOMPYBIRD_DEAD).where(element -> Rs2Player.getWorldLocation().distanceTo(element.getWorldLocation()) <= 5).count();

        return deadChompyCount > 0;
    }

    private boolean hasFilledBellows() {
        return Rs2Inventory.hasItem(ItemID.FILLED_OGRE_BELLOW1, ItemID.FILLED_OGRE_BELLOW2, ItemID.FILLED_OGRE_BELLOW3);
    }

    /** Total number of ogre bellows carried (empty or filled). */
    private int countBellows() {
        return Rs2Inventory.count(ItemID.EMPTY_OGRE_BELLOWS)
                + Rs2Inventory.count(ItemID.FILLED_OGRE_BELLOW1)
                + Rs2Inventory.count(ItemID.FILLED_OGRE_BELLOW2)
                + Rs2Inventory.count(ItemID.FILLED_OGRE_BELLOW3);
    }

    /** Ogre bellows that are not yet at 3/3 loads (empty, 1, or 2 charges). */
    private int countUnfilledBellows() {
        return Rs2Inventory.count(ItemID.EMPTY_OGRE_BELLOWS)
                + Rs2Inventory.count(ItemID.FILLED_OGRE_BELLOW1)
                + Rs2Inventory.count(ItemID.FILLED_OGRE_BELLOW2);
    }

    /** True when at least one ogre bellows is carried and every one of them is full (3 loads). */
    private boolean allBellowsFilled() {
        return countBellows() > 0 && countUnfilledBellows() == 0;
    }

    /**
     * Returns the swamp bubbles to fill the bellows from: the nearest one that can actually be
     * reached, falling back to the nearest one regardless. Tries both known object IDs, then a
     * name match in case the IDs change. Returns null when no bubbles are in the scene.
     *
     * <p>{@code nearestReachable()} checks reachability once per object; sorting on
     * {@code isReachable()} instead would run a scene BFS inside the comparator and make a single
     * loop tick take tens of seconds.
     */
    private Rs2TileObjectModel findSwampBubbles() {
        Rs2TileObjectModel bubble = Microbot.getRs2TileObjectCache().query()
                .withIds(ObjectID.SWAMPBUBBLES, ObjectID.SWAMPBUBBLES_SWAMP)
                .nearestReachable();
        if (bubble != null) {
            return bubble;
        }

        // Bubbles sit in water and may never be flagged reachable; fall back to the nearest one
        // (straight-line distance) and let the walker get us close enough to click it.
        bubble = Microbot.getRs2TileObjectCache().query()
                .withIds(ObjectID.SWAMPBUBBLES, ObjectID.SWAMPBUBBLES_SWAMP)
                .nearest();
        if (bubble != null) {
            return bubble;
        }

        return Microbot.getRs2TileObjectCache().query()
                .withName("Swamp bubbles")
                .nearest();
    }

    private Rs2NpcModel getNearestReachableNpc(int npcId) {
        Rs2NpcModel npc = Microbot.getRs2NpcCache().query().withId(npcId).nearestReachable();
        if (npc != null) {
            return npc;
        }
        return Microbot.getRs2NpcCache().query().withId(npcId).nearest();
    }

    private void manageRunEnergy(AutoChompyKillerConfig config) {
        if (Microbot.getClient().getEnergy() >= 20) return;

        AutoChompyKillerConfig.RunEnergyOption energyOption = config.runEnergyOption();
        switch (energyOption) {
            case STAMINA_POTION:
                if (Rs2Inventory.contains(ItemID._4DOSESTAMINA, ItemID._3DOSESTAMINA, ItemID._2DOSESTAMINA, ItemID._1DOSESTAMINA)) {
                    Rs2Inventory.interact(ItemID._4DOSESTAMINA, "Drink");
                    if (!Rs2Inventory.contains(ItemID._4DOSESTAMINA)) {
                        Rs2Inventory.interact(ItemID._3DOSESTAMINA, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._3DOSESTAMINA)) {
                        Rs2Inventory.interact(ItemID._2DOSESTAMINA, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._2DOSESTAMINA)) {
                        Rs2Inventory.interact(ItemID._1DOSESTAMINA, "Drink");
                    }
                }
                break;
            case SUPER_ENERGY_POTION:
                if (Rs2Inventory.contains(ItemID._4DOSE2ENERGY, ItemID._3DOSE2ENERGY, ItemID._2DOSE2ENERGY, ItemID._1DOSE2ENERGY)) {
                    Rs2Inventory.interact(ItemID._4DOSE2ENERGY, "Drink");
                    if (!Rs2Inventory.contains(ItemID._4DOSE2ENERGY)) {
                        Rs2Inventory.interact(ItemID._3DOSE2ENERGY, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._3DOSE2ENERGY)) {
                        Rs2Inventory.interact(ItemID._2DOSE2ENERGY, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._2DOSE2ENERGY)) {
                        Rs2Inventory.interact(ItemID._1DOSE2ENERGY, "Drink");
                    }
                }
                break;
            case ENERGY_POTION:
                if (Rs2Inventory.contains(ItemID._4DOSE1ENERGY, ItemID._3DOSE1ENERGY, ItemID._2DOSE1ENERGY, ItemID._1DOSE1ENERGY)) {
                    Rs2Inventory.interact(ItemID._4DOSE1ENERGY, "Drink");
                    if (!Rs2Inventory.contains(ItemID._4DOSE1ENERGY)) {
                        Rs2Inventory.interact(ItemID._3DOSE1ENERGY, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._3DOSE1ENERGY)) {
                        Rs2Inventory.interact(ItemID._2DOSE1ENERGY, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._2DOSE1ENERGY)) {
                        Rs2Inventory.interact(ItemID._1DOSE1ENERGY, "Drink");
                    }
                }
                break;
            case STRANGE_FRUIT:
                if (Rs2Inventory.contains(ItemID.MACRO_TRIFFIDFRUIT)) {
                    Rs2Inventory.interact(ItemID.MACRO_TRIFFIDFRUIT, "Eat");
                }
                break;
            case NONE:
            default:
                break;
        }
    }

    private void dropConfiguredItems(AutoChompyKillerConfig config) {
        if (config.dropEmptyVials()) {
            dropIfPresent(ItemID.VIAL_EMPTY);
        }
    }

    private void dropIfPresent(int... itemIds) {
        for (int itemId : itemIds) {
            if (Rs2Inventory.contains(itemId)) {
                Rs2Inventory.drop(itemId);
            }
        }
    }

    private void handleLogoutOnCompletion(String reason) {
        Microbot.showMessage(reason + " - waiting to logout...");
        Microbot.status = "Waiting to logout";

        sleepUntil(() -> !Rs2Player.isInCombat(), 30000);

        if (Rs2Player.isInCombat()) {
            Microbot.showMessage("Still in combat after 30 seconds - logging out anyway...");
        }

        Microbot.status = "Logging out";
        Rs2Player.logout();
        sleepUntil(() -> !Microbot.isLoggedIn(), 5000);

        if (Microbot.isLoggedIn()) {
            Microbot.showMessage("Logout failed - stopping script...");
        }

        Microbot.status = "IDLE";
    }

    private boolean checkStopConditions(AutoChompyKillerConfig config) {
        if (config.stopOnKillCount() && chompyKills >= config.killCount()) {
            String reason = "Kill count reached (" + chompyKills + "/" + config.killCount() + ")";
            if (config.logoutOnCompletion()) {
                handleLogoutOnCompletion(reason);
            } else {
                Microbot.showMessage(reason + " - stopping");
                Microbot.status = "IDLE";
            }
            return true;
        }

        if (config.stopOnChompyChickPet()) {
            if (Rs2Inventory.contains(ItemID.CHOMPYBIRD_PET)) {
                String reason = "Chompy chick pet received";
                if (config.logoutOnCompletion()) {
                    handleLogoutOnCompletion(reason);
                } else {
                    Microbot.showMessage(reason + " - stopping");
                    Microbot.status = "IDLE";
                }
                return true;
            }
        }

        return false;
    }

    private void changeState(AutoChompyKillerState newState) {
        if (newState != state) {
            log.info("State change: {} -> {}", state, newState);
            state = newState;
            stateStartTime = System.currentTimeMillis();
        }
    }

    private boolean validateConfig() {
        if (config == null) {
            log.info("Config is null");
            Microbot.showMessage("Configuration error");
            return false;
        }
        log.info("Config validation complete");
        return true;
    }

    public boolean run(AutoChompyKillerConfig config) {
        this.config = config;

        if (!validateConfig()) {
            shutdown();
            return false;
        }

        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) {
                    log.info("Super.run() returned false");
                    return;
                }
                if (!Microbot.isLoggedIn()) {
                    log.info("Not logged in");
                    return;
                }

                long startTime = System.currentTimeMillis();

                if (!Rs2Player.isMoving() && !Rs2Player.isInteracting()) {
                    dropConfiguredItems(config);
                    manageRunEnergy(config);
                }

                if (checkStopConditions(config)) {
                    shutdown();
                    return;
                }

                // A chompy bird only lingers ~60s after it appears, so a spawned bird outranks
                // everything else. An attack or pluck that is already underway is part of killing
                // one, so those states keep focus; every other state yields to it immediately,
                // even mid-walk to the swamp bubbles.
                if (state != AutoChompyKillerState.ATTACKING && state != AutoChompyKillerState.PLUCKING) {
                    if (getNearestReachableNpc(NpcID.CHOMPYBIRD) != null) {
                        log.info("Chompy bird spotted - prioritising it over {}", state);
                        changeState(AutoChompyKillerState.ATTACKING);
                    }
                }

                long timeoutMs = state == AutoChompyKillerState.FILLING_BELLOWS
                        ? FILLING_BELLOWS_TIMEOUT_MS : STATE_TIMEOUT_MS;
                if (System.currentTimeMillis() - stateStartTime > timeoutMs) {
                    if (state == AutoChompyKillerState.FILLING_BELLOWS) {
                        // changeState() is a no-op when already in this state, so reset the timer
                        // directly. FILLING_BELLOWS legitimately spans several ticks while walking
                        // to the bubbles, so this is a stall retry rather than a real transition.
                        log.info("Stuck in FILLING_BELLOWS for over {}s - retrying", FILLING_BELLOWS_TIMEOUT_MS / 1000);
                        stateStartTime = System.currentTimeMillis();
                    } else {
                        log.info("State timeout - resetting to FILLING_BELLOWS");
                        changeState(AutoChompyKillerState.FILLING_BELLOWS);
                    }
                    return;
                }

                if (Rs2Player.isMoving() || (Rs2Player.isAnimating() && state != AutoChompyKillerState.INFLATING && state != AutoChompyKillerState.ATTACKING) ||
                    (Rs2Player.isInteracting() && state != AutoChompyKillerState.INFLATING && state != AutoChompyKillerState.ATTACKING)) {
                    return;
                }

                if (!Rs2Equipment.isWearing(EquipmentInventorySlot.AMMO)) {
                    log.info("No ammo equipped - shutting down");
                    Microbot.showMessage("No ammo - aborting...");
                    Microbot.status = "IDLE";
                    // wait briefly before shutdown to display message
                    sleepUntil(() -> true, 3000);
                    shutdown();
                    return;
                }

                if (!Rs2Equipment.isWearing(ItemID.OGRE_BOW) && !Rs2Equipment.isWearing(ItemID.ZOGRE_BOW)) {
                    log.info("No ogre bow equipped - shutting down");
                    Microbot.showMessage("No ogre bow equipped - aborting...");
                    Microbot.status = "IDLE";
                    // wait briefly before shutdown to display message
                    sleepUntil(() -> true, 3000);
                    shutdown();
                    return;
                }

                switch (state) {
                    case FILLING_BELLOWS: {
                        Microbot.status = "Filling bellows";

                        if (Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
                            break; // a 'Suck' is still resolving
                        }

                        if (countBellows() == 0) {
                            log.info("No ogre bellows in inventory");
                            changeState(AutoChompyKillerState.INFLATING);
                            break;
                        }

                        if (countUnfilledBellows() == 0) {
                            log.info("All {} ogre bellows already filled", countBellows());
                            changeState(AutoChompyKillerState.INFLATING);
                            break;
                        }

                        Rs2TileObjectModel bubble = findSwampBubbles();
                        if (bubble == null) {
                            log.info("No swamp bubbles found nearby");
                            break;
                        }

                        WorldPoint bubbleLocation = bubble.getWorldLocation();
                        if (Rs2Player.getWorldLocation().distanceTo(bubbleLocation) > BELLOWS_INTERACT_DISTANCE) {
                            // Non-blocking single-step approach; subsequent loop ticks continue the walk.
                            WalkerState walkState = Rs2Walker.walkStep(bubbleLocation, BELLOWS_INTERACT_DISTANCE - 1);
                            if (walkState == WalkerState.UNREACHABLE) {
                                log.info("No walkable route to swamp bubbles");
                            }
                            break;
                        }

                        int unfilledBefore = countUnfilledBellows();
                        log.info("Sucking swamp bubbles (id {}) at {} - {}/{} ogre bellows unfilled",
                                bubble.getId(), bubbleLocation, unfilledBefore, countBellows());
                        // One 'Suck' fills every ogre bellows in the inventory; confirm via the count.
                        if (Rs2GameObject.interact(bubble, "Suck", false)) {
                            sleepUntil(() -> countUnfilledBellows() < unfilledBefore, ACTION_START_TIMEOUT_MS);
                        }

                        if (allBellowsFilled()) {
                            log.info("All {} ogre bellows filled", countBellows());
                            Rs2Walker.setTarget(null, "chompy:bellows-filled");
                            changeState(AutoChompyKillerState.INFLATING);
                        } else {
                            log.info("Bellows not fully filled yet - {}/{} unfilled",
                                    countUnfilledBellows(), countBellows());
                        }
                        break;
                    }

                    case INFLATING:
                        Microbot.status = "Inflating toads";

                        if (Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
                            break; // inflate/drop still resolving
                        }

                        if (config.pluckChompys() && isDeadChompyNearby()) {
                            log.info("Dead chompy nearby - switching to plucking");
                            changeState(AutoChompyKillerState.PLUCKING);
                            break;
                        }

                        if (Rs2Inventory.hasItem(ItemID.BLOATED_TOAD) && !isBloatedToadOnGround()) {
                            log.info("Dropping bloated toad");
                            // Dropping is an instant menu action with no animation, so do not wait;
                            // the next tick sees the toad on the ground and moves on.
                            Rs2Inventory.drop(ItemID.BLOATED_TOAD);
                            break;
                        }

                        // check if we have filled bellows to use
                        if (!hasFilledBellows()) {
                            // check if we need to fill empty bellows
                            if (countBellows() > 0) {
                                log.info("Bellows need filling - {}/{} unfilled", countUnfilledBellows(), countBellows());
                                changeState(AutoChompyKillerState.FILLING_BELLOWS);
                            } else {
                                log.info("No bellows found in inventory, shutting down script");
                                Microbot.status = "IDLE";
                                // wait before shutting down to display message
                                sleepUntil(() -> true, 10000);
                                shutdown();
                                return;
                            }
                            break;
                        }

                        // find a swamp toad to inflate
                        Rs2NpcModel swampToad = getNearestReachableNpc(NpcID.TOAD);
                        if (swampToad == null) {
                            log.info("No swamp toads found nearby, shutting down script");
                            Microbot.status = "IDLE";
                            // wait before shutting down to display message
                            sleepUntil(() -> true, 10000);
                            shutdown();
                            return;
                        }

                        log.info("Inflating swamp toad");
                        if (swampToad.click("Inflate")) {
                            sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting(), ACTION_START_TIMEOUT_MS);
                        } else {
                            log.info("Failed to interact with swamp toad");
                        }
                        break;

                    case ATTACKING:
                        Microbot.status = "Attacking chompy";

                        if (Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
                            break; // attack still resolving
                        }

                        if (config.pluckChompys() && isDeadChompyNearby()) {
                            log.info("Dead chompy nearby - switching to plucking");
                            changeState(AutoChompyKillerState.PLUCKING);
                            break;
                        }

                        Rs2NpcModel targetChompy = getNearestReachableNpc(NpcID.CHOMPYBIRD);
                        if (targetChompy == null) {
                            log.info("No chompy found - returning to inflating");
                            changeState(AutoChompyKillerState.INFLATING);
                            break;
                        }

                        log.info("Attacking chompy");
                        if (targetChompy.click("Attack")) {
                            sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting(), ACTION_START_TIMEOUT_MS);
                        } else {
                            log.info("Failed to attack chompy");
                        }
                        break;

                    case PLUCKING:
                        Microbot.status = "Plucking chompy";

                        if (Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
                            break; // pluck still resolving
                        }

                        Rs2NpcModel deadChompy = getNearestReachableNpc(NpcID.CHOMPYBIRD_DEAD);
                        if (deadChompy != null) {
                            log.info("Plucking dead chompy");
                            if (deadChompy.click("Pluck")) {
                                sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting(), ACTION_START_TIMEOUT_MS);
                            } else {
                                log.info("Failed to pluck dead chompy");
                            }
                            break; // let the pluck resolve before deciding what is next
                        }

                        // Nothing left to pluck - a newly spawned chompy wins, otherwise inflate again.
                        if (getNearestReachableNpc(NpcID.CHOMPYBIRD) != null) {
                            log.info("Chompy nearby - switching to attacking");
                            changeState(AutoChompyKillerState.ATTACKING);
                        } else {
                            log.info("No chompy nearby - switching to inflating");
                            changeState(AutoChompyKillerState.INFLATING);
                        }
                        break;

                    case STOPPED:
                        log.info("State: STOPPED");
                        return;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                log.info("Total time for loop: {}ms", totalTime);

            } catch (Exception ex) {
                log.error("Error in main loop", ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    public void startup() {
        chompyKills = 0;
        startTime = System.currentTimeMillis();
    }

    public void incrementChompyKills() {
        chompyKills += 1;
    }

    public void handleNotMyChompy() {
        Microbot.showMessage("Someone else is hunting chompys in this world - aborting...");
        Microbot.status = "IDLE";
        shutdown();
    }

    public void handleBowNotPowerfulEnough() {
        Microbot.showMessage("Your bow isn't powerful enough for those arrows - aborting...");
        Microbot.status = "IDLE";
        shutdown();
    }

    public void handlePetReceived(boolean logoutOnCompletion) {
        String reason = "Chompy chick pet received from chat message";
        if (logoutOnCompletion) {
            handleLogoutOnCompletion(reason);
        } else {
            Microbot.showMessage(reason + " - stopping...");
            Microbot.status = "IDLE";
        }
        shutdown();
    }

    public void handleCantReachBubbles() {
        // 'can't reach that' is a generic game message: it may come from the bubbles, a toad or a
        // chompy. Only react when we were actually filling, otherwise ignore it so an unreachable
        // chompy/toad does not yank us out of the kill. The NPC/object click helpers already walk
        // closer on their own.
        if (state != AutoChompyKillerState.FILLING_BELLOWS) {
            return;
        }
        // Reset the fill attempt so the loop retries (a fresh walk/scan), rather than blind-clicking
        // a random bubble or bouncing to INFLATING.
        log.info("Received 'can't reach that' - retrying bellows fill");
        stateStartTime = System.currentTimeMillis();
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
