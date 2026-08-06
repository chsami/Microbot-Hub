package net.runelite.client.plugins.microbot.housethieving;

import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.TileObject;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.slf4j.event.Level;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.housethieving.HouseThievingConstants.*;

public class HouseThievingScript extends Script {
    private final HouseThievingPlugin plugin;

    public HouseThievingScript(final HouseThievingPlugin plugin) {
        this.plugin = plugin;
    }

    enum State {
        PICKPOCKETING,
        FINDING_HOUSE,
        THIEVING_HOUSES,
        BANKING
    }

    State state = State.PICKPOCKETING;
    private ThievingHouse currentThievingHouse = null;
    private Rs2NpcModel pickpocketNpc = null;
    private boolean clickedThisDistraction = false;
    // Furniture we last searched, and the piece the game told us is emptied ("nothing else worth taking").
    // We skip the emptied piece so we switch to another instead of spam-clicking it; it refills while we loot elsewhere.
    private volatile WorldPoint lastSearchedValuables = null;
    private volatile WorldPoint exhaustedValuables = null;
    // True once a search has started auto-looting a piece. One click loots a piece for ~50-60s with no further
    // input, so we leave it alone until the game reports the piece is emptied (onValuablesExhausted) - the same
    // "click once, the game auto-repeats" mechanic as a distracted pickpocket. Gating on animation is wrong here:
    // the auto-loot has long stretches with no animation, so any isAnimating() window goes false mid-loot and re-clicks.
    private volatile boolean searchingPiece = false;
    private final static String HOUSE_KEYS = "House keys";
    private final static String DODGY_NECKLACE = "Dodgy necklace";
    private final static String COIN_POUCH = "Coin pouch";
    private final static String WEALTHY_CITIZEN = "Wealthy citizen";

    public boolean run(HouseThievingConfig config) {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        Microbot.enableAutoRunOn = false;
        initialPlayerLocation = null;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.naturalMouse = true;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;

                if (state == null)
                    state = State.BANKING;

                if (state != State.THIEVING_HOUSES) {
                    if (BreakHandlerScript.isLockState()) {
                        BreakHandlerScript.setLockState(false);
                    }
                } else {
                    if (!BreakHandlerScript.isLockState()) {
                        BreakHandlerScript.setLockState(true);
                    }
                }

                if (currentThievingHouse == null)
                    currentThievingHouse = getThievingHouse();

                switch (state) {
                    case PICKPOCKETING:
                        handlePickPocketing(config);
                        break;
                    case FINDING_HOUSE:
                        handleFindingHouse(config);
                        break;
                    case THIEVING_HOUSES:
                        var houseNpc = Microbot.getRs2NpcCache().query().withName(currentThievingHouse.npcName).nearestOnClientThread();
                        handleThievingHouse(houseNpc);
                        break;
                    case BANKING:
                        handleBanking(config);
                        break;
                }

            } catch (Exception ex) {
                Microbot.log("Error: " + ex.getMessage(), Level.ERROR);
                ex.printStackTrace();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handlePickPocketing(HouseThievingConfig config) {
        if (Rs2Player.isAnimating()) return;
        var hasFood = !Rs2Inventory.getInventoryFood().isEmpty();
        var hasDodgyNecklaceInv = getDodgyNecklaceAmount() > 0;
        var hasDodgyNecklaceEquipped = Rs2Equipment.isWearing(DODGY_NECKLACE);
        if (hasMaxHouseKeys(config) && (hasFood || hasDodgyNecklaceInv)) {
            Microbot.log("Have enough house keys, depositing inventory at bank", Level.INFO);
            state = State.BANKING;
            return;
        } else if (hasMaxHouseKeys(config) && !hasFood && !hasDodgyNecklaceInv) {
            Microbot.log("Have enough house keys, thieving houses", Level.INFO);
            state = State.FINDING_HOUSE;
            return;
        } else if (!hasMaxHouseKeys(config) && (!hasFood || (!hasDodgyNecklaceInv && !hasDodgyNecklaceEquipped && config.useDodgyNecklace())) && !config.worldHopForDistractedCitizens() && !config.onlyPickpocketDistracted() && config.pickpocketFoodAmount() > 0) {
            Microbot.log("Need food or dodgy necklace to keep pickpocketing, withdrawing from bank", Level.INFO);
            state = State.BANKING;
            return;
        }

        if (!hasDodgyNecklaceEquipped && hasDodgyNecklaceInv) {
            var dodgyNecklace = Rs2Inventory.get(DODGY_NECKLACE);
            if (dodgyNecklace != null)
                Rs2Inventory.interact(dodgyNecklace, "Wear");
        }

        if (Rs2Player.getWorldLocation().distanceTo(PICKPOCKET_LOCATION) > 8) {
            Microbot.log("Walking to pickpocket location", Level.INFO);
            Rs2Walker.walkTo(PICKPOCKET_LOCATION, 5);
        }

        if (Rs2Inventory.hasItem(COIN_POUCH)) {
            var coinPouches = Rs2Inventory.get(COIN_POUCH);
            if (coinPouches.getQuantity() > 27) {
                Rs2Inventory.interact(coinPouches, "Open-all");
                Rs2Random.waitEx(200.0, 200.0);
                clickedThisDistraction = false; // opening pouches interrupts auto-pickpocket; re-click needed
            }
        }

        var aureliaNpc = Microbot.getRs2NpcCache().query().withName("Aurelia").nearestOnClientThread();
        Rs2NpcModel distractedWealthyCitizen = null;
        if (aureliaNpc != null) {
            var aureliaAnim = aureliaNpc.getAnimation();
            if (aureliaAnim == 866 || aureliaAnim == 860) {
                distractedWealthyCitizen = getDistractedWealthyCitizen(aureliaNpc);
                if (distractedWealthyCitizen != null)
                    pickpocketNpc = null;
            } else {
                clickedThisDistraction = false; // distraction ended; allow a click when the next one starts
                if (pickpocketNpc == null) {
                    var nearbyWealthyCitizens = Microbot.getRs2NpcCache().query().withName(WEALTHY_CITIZEN).toListOnClientThread().stream();
                    var aureliaLocation = aureliaNpc.getWorldLocation();
                    var closestWealthyCitizen = nearbyWealthyCitizens.min(Comparator.comparingInt(a -> a.getWorldLocation().distanceTo(aureliaLocation)));
                    closestWealthyCitizen.ifPresent(rs2NpcModel -> pickpocketNpc = rs2NpcModel);
                }
            }
            if (config.worldHopForDistractedCitizens()) {
                Microbot.log(String.format("Waiting %s seconds for Aurelia pickpocket", config.worldHopWaitTime()), Level.INFO);
                var waitForPickpocket = sleepUntil(() -> aureliaNpc.getAnimation() == 866 || aureliaNpc.getAnimation() == 860, config.worldHopWaitTime() * 1000);
                Microbot.log(String.format("Finished waiting %s seconds for Aurelia pickpocket", config.worldHopWaitTime()), Level.INFO);
                if (!waitForPickpocket) {
                    doWorldHop();
                    return;
                } else
                    distractedWealthyCitizen = getDistractedWealthyCitizen(aureliaNpc);
            }
        }

        if (config.onlyPickpocketDistracted() && distractedWealthyCitizen == null)
            return;

        if (distractedWealthyCitizen != null) {
            if (clickedThisDistraction)
                return; // already pickpocketing this distraction; the game auto-repeats for ~24s
            distractedWealthyCitizen.click("Pickpocket");
            clickedThisDistraction = true;
            return;
        }

        if (pickpocketNpc != null) {
            if (Rs2Inventory.getInventoryFood().isEmpty()) {
                state = State.BANKING;
                return;
            }
            if (Rs2Player.getHealthPercentage() <= config.foodEatPercentage()) {
                Rs2Player.useFood();
                Rs2Inventory.waitForInventoryChanges(600);
            }
            if (Rs2Player.isStunned())
                sleepUntil(() -> !Rs2Player.isStunned(), 600);
            pickpocketNpc.click("Pickpocket");
            Rs2Random.waitEx(600.0, 200.0);
            sleepUntil(() -> !Rs2Player.isAnimating(), 10000);
        }

        if (distractedWealthyCitizen != null && config.worldHopForDistractedCitizens()) {
            if (distractedWealthyCitizen.getOverheadText().contains("strange")) {
                doWorldHop();
            }
        }
    }

    private void doWorldHop() {
        Microbot.log("World hopping to check for distracted citizen event", Level.INFO);
        boolean hoppedWorlds = Microbot.hopToWorld(Login.getRandomWorld(Rs2Player.isMember()));
        if (hoppedWorlds) {
            sleepUntil(() -> Microbot.getClient().getGameState() == GameState.HOPPING);
            sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN);
            pickpocketNpc = null;
        }
    }

    @Nullable
    private static Rs2NpcModel getDistractedWealthyCitizen(Rs2NpcModel aureliaNpc) {
        var aureliaLoc = aureliaNpc.getWorldLocation();
        return Microbot.getRs2NpcCache().query()
                .withName(WEALTHY_CITIZEN)
                .nearestOnClientThread(aureliaLoc, 5);
    }

    private void handleFindingHouse(HouseThievingConfig config) {
        if (Rs2Inventory.emptySlotCount() < 5) {
            state = State.BANKING;
            return;
        }

        var houseKeys = Rs2Inventory.get(HOUSE_KEYS);
        if (houseKeys == null || houseKeys.getQuantity() < config.minHouseKeys()) {
            state = State.PICKPOCKETING;
            return;
        }

        var distanceToScout = Rs2Player.getWorldLocation().distanceTo(currentThievingHouse.scoutPosition);
        var distanceToDoor = Rs2Player.getWorldLocation().distanceTo(currentThievingHouse.lockedDoorEntrance);
        if (distanceToScout >= 3 && distanceToDoor >= 3) {
            Rs2Walker.walkTo(currentThievingHouse.scoutPosition, 2);
            sleepUntil(() -> !Rs2Player.isAnimating());
            Rs2Random.waitEx(800.0, 200.0);
        }

        var lockedDoorTile = Rs2Tile.getTile(
                currentThievingHouse.lockedDoorEgress.getX(),
                currentThievingHouse.lockedDoorEgress.getY());
        if (lockedDoorTile != null) {
            var wallObject = lockedDoorTile.getWallObject();
            var houseNpc = Microbot.getRs2NpcCache().query().withName(currentThievingHouse.npcName).nearestOnClientThread();
            if (wallObject != null && wallObject.getId() == LOCKED_DOOR_ID) {
                Microbot.log(currentThievingHouse.npcName + " house can be thieved", Level.INFO);
                attemptWaitForHouseNpc(houseNpc);

                if (!Rs2Tile.isTileReachable(currentThievingHouse.houseCenter)) {
                    Microbot.getRs2TileObjectCache().query().withId(wallObject.getId()).interact();
                    Rs2Random.waitEx(2000.0, 100.0);
                    sleepUntil(() -> !Rs2Player.isAnimating(600));
                    Rs2Random.waitEx(2000.0, 100.0);
                    sleepUntil(() -> Rs2Tile.isTileReachable(currentThievingHouse.houseCenter), 10000);
                    Microbot.log("Entered " + currentThievingHouse.npcName + " house", Level.INFO);
                }
                if (Rs2Tile.isTileReachable(currentThievingHouse.houseCenter)) {
                    lastSearchedValuables = null;
                    exhaustedValuables = null;
                    searchingPiece = false;
                    state = State.THIEVING_HOUSES;
                    Microbot.log("Starting thieving in " + currentThievingHouse.npcName + " house.", Level.INFO);
                    return;
                }
                Microbot.log("Failed to enter " + currentThievingHouse.npcName + " house, trying again..", Level.INFO);
            } else {
                Rs2Random.waitEx(3000.0, 100.0);
                setNextThievingHouse();
                Microbot.log("Checking " + currentThievingHouse.npcName + " house for thieving..", Level.INFO);
            }
        }
    }

    private void attemptWaitForHouseNpc(Rs2NpcModel houseNpc) {
        // Try to determine if NPC is leaving or not - could maybe look at overhead text?
        if (houseNpc != null) {
            var overheadText = houseNpc.getOverheadText();
            if (overheadText != null && !overheadText.isEmpty()) {
                Microbot.log("House NPC is talking about leaving, waiting for them to leave..", Level.INFO);
                Rs2Random.waitEx(5000.0, 200.0);
            }
        }
        var distanceToHouseNpc = houseNpc != null ? currentThievingHouse.lockedDoorEntrance.distanceTo(houseNpc.getWorldLocation()) : 100;
        Microbot.log("Distance from house NPC to Door: " + distanceToHouseNpc, Level.INFO);
        if (distanceToHouseNpc < 6) {
            Microbot.log("House NPC is too close to locked door, waiting for them to leave..", Level.INFO);
            Rs2Walker.walkTo(currentThievingHouse.lockedDoorEntrance);
            sleepUntil(() -> (houseNpc != null ? currentThievingHouse.lockedDoorEntrance.distanceTo(houseNpc.getWorldLocation()) : 100) > 6);
        }
    }

    private void handleThievingHouse(Rs2NpcModel houseNpc) {
        if (Rs2Inventory.emptySlotCount() < 1) {
            if (!exitHouse())
                return;
            state = State.BANKING;
            return;
        }

        if (houseNpc != null) {
            var laviniaWorldLoc = houseNpc.getWorldLocation();
            if (laviniaWorldLoc.distanceTo(currentThievingHouse.lockedDoorEntrance) < 5) {
                Microbot.log("Owner in house or approaching currently", Level.INFO);
                if (exitHouse())
                    return;
            }
        }

        // A flashing hint arrow ("You notice something shine somewhere else in the house") marks a piece that
        // grants a one-time bonus (+14 valuables, +630 xp) for switching to it. Claim it even mid-loot - the
        // game rewards the switch. The arrow clears ~7s after we search it, and we skip it once we're already on
        // that piece, so this fires once per highlight rather than spam-clicking.
        var hintArrow = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getHintArrowPoint()).orElse(null);
        if (hintArrow != null && (lastSearchedValuables == null || lastSearchedValuables.distanceTo(hintArrow) > 2)) {
            if (Rs2Player.isMoving())
                return; // en route to the bonus piece
            var bonusPiece = Microbot.getRs2TileObjectCache().query()
                    .withIds(VALUABLES_OBJECT_IDS)
                    .nearestOnClientThread(hintArrow, 2);
            if (bonusPiece != null) {
                lastSearchedValuables = bonusPiece.getWorldLocation();
                exhaustedValuables = null; // the arrow can re-highlight a refilled piece we'd otherwise skip
                if (!Rs2Camera.isTileOnScreen(bonusPiece.getLocalLocation()))
                    Rs2Camera.turnTo(bonusPiece);
                bonusPiece.click("Search");
                searchingPiece = sleepUntil(() -> Rs2Player.isAnimating(), 5000);
                return;
            }
        }

        // Already auto-looting a piece: one click keeps looting for ~50-60s, so don't touch it. We only act
        // again when the game tells us the piece is emptied (onValuablesExhausted clears searchingPiece below).
        if (searchingPiece)
            return;

        // Walking to the piece we just clicked - let the click register instead of firing another.
        if (Rs2Player.isMoving())
            return;

        // Search the nearest piece of furniture, skipping the one the game just told us is emptied so we move
        // on instead of spam-clicking it. It refills while we loot another piece.
        var valuables = Microbot.getRs2TileObjectCache().query()
                .withIds(VALUABLES_OBJECT_IDS)
                .where(o -> exhaustedValuables == null || !exhaustedValuables.equals(o.getWorldLocation()))
                .nearestOnClientThread();
        if (valuables == null) {
            exhaustedValuables = null; // nothing else within reach; let the emptied piece refill and retry it
            return;
        }
        lastSearchedValuables = valuables.getWorldLocation();
        if (!Rs2Camera.isTileOnScreen(valuables.getLocalLocation()))
            Rs2Camera.turnTo(valuables);
        valuables.click("Search");
        // Confirm the search actually started (covers the walk to the piece) before marking it active; if it
        // never starts we retry next loop instead of standing idle. This is a timeout, not a state-modeling cooldown.
        searchingPiece = sleepUntil(() -> Rs2Player.isAnimating(), 5000);
    }

    /** True if the tile object exposes a "Bank" menu action (mirrors how Rs2TileObjectModel.click resolves it). */
    private static boolean hasBankAction(Rs2TileObjectModel object) {
        var comp = object.getObjectComposition();
        if (comp == null)
            return false;
        var actions = (comp.getImpostorIds() != null && comp.getImpostor() != null)
                ? comp.getImpostor().getActions() : comp.getActions();
        if (actions == null)
            return false;
        for (var action : actions)
            if ("Bank".equalsIgnoreCase(action))
                return true;
        return false;
    }

    /** Called when the game reports the current piece of furniture is emptied ("nothing else worth taking"). */
    void onValuablesExhausted() {
        exhaustedValuables = lastSearchedValuables;
        searchingPiece = false; // auto-loot ended; let the loop search a different, non-exhausted piece
    }

    private boolean exitHouse() {
        var windowTile = Rs2Tile.getTile(currentThievingHouse.windowEntrance.getX(), currentThievingHouse.windowEntrance.getY());
        if (windowTile != null) {
            var windowTileWallObject = windowTile.getWallObject();
            if (windowTileWallObject != null) {
                if (!Rs2Camera.isTileOnScreen(windowTileWallObject.getLocalLocation())) {
                    Rs2Camera.turnTo(windowTileWallObject);
                }
                Microbot.getRs2TileObjectCache().query().withId(windowTileWallObject.getId()).interact("Exit-window");
                Rs2Random.waitEx(5000.0, 200.0);
                searchingPiece = false;
                setNextThievingHouse();
                state = State.FINDING_HOUSE;
                return true;
            }
        }
        return false;
    }

    private boolean hasMaxHouseKeys(HouseThievingConfig config) {
        var houseKeys = Rs2Inventory.get(HOUSE_KEYS);
        return houseKeys != null && houseKeys.getQuantity() >= config.maxHouseKeys();
    }

    private void handleBanking(HouseThievingConfig config) {
        var hasFood = Rs2Inventory.getInventoryFood().size() >= config.pickpocketFoodAmount();
        var hasAnyFood = !Rs2Inventory.getInventoryFood().isEmpty();
        var currentDodgyNecklace = getDodgyNecklaceAmount();
        var dodgyNecklaceReqsMet = !config.useDodgyNecklace() || (config.useDodgyNecklace() && currentDodgyNecklace >= config.dodgyNecklaceAmount());

        if (hasMaxHouseKeys(config) && !hasAnyFood && currentDodgyNecklace < 1 && Rs2Inventory.emptySlotCount() > 5) {
            state = State.FINDING_HOUSE;
            return;
        }

        if (!hasMaxHouseKeys(config) && ((hasFood && dodgyNecklaceReqsMet) || config.worldHopForDistractedCitizens() || config.onlyPickpocketDistracted())) {
            state = State.PICKPOCKETING;
            return;
        }

        if (Rs2Player.getWorldLocation().distanceTo(BANKING_LOCATION) > 3) {
            Rs2Walker.walkTo(BANKING_LOCATION, 2);
        }

        if (!Rs2Bank.isOpen() && Rs2Player.distanceTo(BANKING_LOCATION) <= 3) {
            // Resolve the actual bank object near the counter. The previous unfiltered nearest() grabbed whatever
            // tile object was closest - often a floor/decoration with no "Bank" option - so click() fell back to
            // that object's first menu entry ("Walk here") and the bank never opened. Filter to an object that
            // actually has a "Bank" action (this counter's id isn't in Rs2Bank's known-bank set, so the no-arg
            // openBank() can't find it either). openBank(object) waits internally for the bank to open.
            var bankObject = Microbot.getRs2TileObjectCache().query()
                    .where(HouseThievingScript::hasBankAction)
                    .nearestOnClientThread(BANKING_TILE_LOCATION, 5);
            if (bankObject != null)
                Rs2Bank.openBank(bankObject);
            else
                Rs2Bank.openBank();
        }

        if (Rs2Bank.isOpen()) {
            if (!hasMaxHouseKeys(config))
                Rs2Bank.depositAllExcept("Coins", COIN_POUCH, HOUSE_KEYS, config.foodSelection().getName(), DODGY_NECKLACE);
            else
                Rs2Bank.depositAllExcept("Coins", COIN_POUCH, HOUSE_KEYS);
            Rs2Inventory.waitForInventoryChanges(600);

            if (!hasMaxHouseKeys(config)) {
                if (!hasFood && !config.worldHopForDistractedCitizens() && !config.onlyPickpocketDistracted()) {
                    if (!Rs2Bank.hasItem(config.foodSelection().getId())) {
                        Microbot.showMessage("No food found in bank!");
                        shutdown();
                        return;
                    }
                    Rs2Bank.withdrawX(config.foodSelection().getId(), config.pickpocketFoodAmount());
                    Rs2Inventory.waitForInventoryChanges(600);
                }
                if (config.useDodgyNecklace() && !config.worldHopForDistractedCitizens() && !config.onlyPickpocketDistracted()) {
                    if (!Rs2Bank.hasItem(DODGY_NECKLACE)) {
                        Microbot.showMessage("No dodgy necklace found in bank!");
                        shutdown();
                        return;
                    }
                    if (currentDodgyNecklace < config.dodgyNecklaceAmount()) {
                        Rs2Bank.withdrawX(DODGY_NECKLACE, config.dodgyNecklaceAmount());
                        Rs2Inventory.waitForInventoryChanges(600);
                    }
                }
                if (Rs2Bank.hasItem(HOUSE_KEYS)) {
                    Rs2Bank.withdrawAll(HOUSE_KEYS);
                    Rs2Inventory.waitForInventoryChanges(600);
                }
            }
        }
    }

    private static int getDodgyNecklaceAmount() {
        var dodgyNecklaces = Rs2Inventory.items(
                        (item) -> item.getName().equalsIgnoreCase(DODGY_NECKLACE))
                .collect(Collectors.toList());
        return dodgyNecklaces.size();
    }

    private void setNextThievingHouse() {
        if (currentThievingHouse == ThievingHouse.LAVINIA) {
            currentThievingHouse = ThievingHouse.VICTOR;
            if (hasLockedDoor(ThievingHouse.CAIUS))
                currentThievingHouse = ThievingHouse.CAIUS;
        } else if (currentThievingHouse == ThievingHouse.VICTOR) {
            currentThievingHouse = ThievingHouse.CAIUS;
            if (hasLockedDoor(ThievingHouse.LAVINIA))
                currentThievingHouse = ThievingHouse.LAVINIA;
        } else if (currentThievingHouse == ThievingHouse.CAIUS) {
            currentThievingHouse = ThievingHouse.LAVINIA;
            if (hasLockedDoor(ThievingHouse.VICTOR))
                currentThievingHouse = ThievingHouse.VICTOR;
        }
    }

    private static ThievingHouse getThievingHouse() {
        ThievingHouse closestHouse = null;
        for (var house : ThievingHouse.values()) {
            if (hasLockedDoor(house))
                return house;
            if (closestHouse == null)
                closestHouse = house;
            var houseDistance = Rs2Player.getWorldLocation().distanceTo(house.lockedDoorEntrance);
            var shortestHouseDistance = Rs2Player.getWorldLocation().distanceTo(closestHouse.lockedDoorEntrance);
            if (houseDistance < shortestHouseDistance) {
                closestHouse = house;
            }
        }
        return closestHouse;
    }

    private static boolean hasLockedDoor(ThievingHouse thievingHouse) {
        var lockedDoorTile = Rs2Tile.getTile(
                thievingHouse.lockedDoorEgress.getX(),
                thievingHouse.lockedDoorEgress.getY());
        if (lockedDoorTile == null) return false;
        var wallObject = lockedDoorTile.getWallObject();
        return wallObject != null && wallObject.getId() == LOCKED_DOOR_ID;
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }

}
