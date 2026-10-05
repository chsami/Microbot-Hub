package net.runelite.client.plugins.microbot.construction;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.construction.ConstructionConfig;
import net.runelite.client.plugins.microbot.construction.enums.ConstructionState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

import javax.swing.SwingUtilities;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ConstructionScript extends Script {

    private static final int DEFAULT_DELAY = 600;
    private static final int MISSING_HOTSPOT_TICKS = 3;
    private static final int MAX_HOUSE_RETURN_ATTEMPTS = 2;
    private static final String NO_HOTSPOT_MESSAGE = "Cannot find the build hotspot or the house portal. Start inside your house in build mode next to the hotspot.";
    private static final int[] OTHER_SERVANT_IDS = {
            NpcID.POH_SERVANT_DOGSBODY, NpcID.POH_SERVANT_MULTI_DOGSBODY,
            NpcID.POH_SERVANT_WAITER_WOMAN, NpcID.POH_SERVANT_MULTI_WAITER_WOMAN,
            NpcID.POH_SERVANT_COOK_WOMAN, NpcID.POH_SERVANT_MULTI_COOK_WOMAN,
            NpcID.POH_SERVANT_MAITRE_D_MAN, NpcID.POH_SERVANT_MULTI_MAITRE_D_MAN
    };
    private ConstructionState state = ConstructionState.Idle;
    private WorldPoint workingTile = null;
    private final ServantRestock restock = new ServantRestock();
    private int missingHotspotTicks = 0;
    private int houseReturnAttempts = 0;
    private volatile String statusMessage = "";

    // NOTE: For the arrays below, the first ID is the BUILD OBJECT ID, the second is the EMPTY OBJECT ID
    private static final List<Integer> OAK_DUNGEON_DOOR = List.of(13344, 15328);
    private static final List<Integer> OAK_LARDER = List.of(13566, 15403);
    private static final List<Integer> MAHOGANY_TABLE = List.of(13298, 15298);
    private static final List<Integer> MYTHICAL_CAPE_MOUNT = List.of(15394, 31986);

    public Rs2TileObjectModel getClosestTile(List<Integer> objIDs) {
        int[] ids = objIDs.stream().mapToInt(Integer::intValue).toArray();
        return Microbot.getRs2TileObjectCache().query().withIds(ids).nearest();
    }

    public Rs2NpcModel getButler() {
        return Microbot.getRs2NpcCache().query().withName("Demon butler").nearestOnClientThread();
    }

    public Rs2NpcModel getOtherServant() {
        return Microbot.getRs2NpcCache().query().withIds(OTHER_SERVANT_IDS).nearestOnClientThread();
    }

    public boolean hasDialogueOptionToUnnote() {
        return Rs2Widget.findWidget("Un-note", null) != null;
    }

    public boolean hasDialogueRepeatLastTask() { return Rs2Widget.hasWidget("Repeat last task?"); }

    public boolean hasPayButlerDialogue() {
        return Rs2Widget.findWidget("must render unto me the 10,000 coins that are due", null) != null;
    }

    public boolean hasDialogueOptionToPay() {
        return Rs2Widget.findWidget("Okay, here's 10,000 coins.", null) != null;
    }

    public boolean hasFurnitureInterfaceOpen() {
        Widget furnitureWidget = Rs2Widget.findWidget("Furniture", null);
        if (furnitureWidget != null) {
            System.out.println("Furniture interface is open.");
            return true;
        }
        System.out.println("Furniture interface is not open.");
        return false;
    }

    public boolean hasRemoveDoorInterfaceOpen() {
        return Rs2Widget.findWidget("Really remove it?", null) != null;
    }

    public boolean hasRemoveLarderInterfaceOpen() {
        return Rs2Widget.findWidget("Really remove it?", null) != null;
    }

    public boolean hasRemoveTableInterfaceOpen() {
        return Rs2Widget.findWidget("Really remove it?", null) != null;
    }

    public boolean hasRemoveCapeMountInterfaceOpen() {
        return Rs2Widget.findWidget("Really remove it?", null) != null;
    }

    public boolean run(net.runelite.client.plugins.microbot.construction.ConstructionConfig config) {
        int actionDelay = config.useCustomDelay() ? config.actionDelay() : DEFAULT_DELAY;
        resetRestock();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                Rs2Tab.switchTo(InterfaceTab.INVENTORY);
                calculateState(config);
                switch (state) {
                    case Build:
                        grabPlanksWhileWeBuild(config, actionDelay);
                        if (state == ConstructionState.Stopped) break;
                        buildSpace(config, actionDelay);
                        break;
                    case Remove:
                        removeSpace(config, actionDelay);
                        break;
                    case Butler:
                        grabPlanksWhileWeBuild(config, actionDelay);
                        break;
                    case ReturnToHouse:
                        returnToTheHouse();
                        break;
                    default:
                        break;
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, actionDelay, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        resetRestock();
        state = ConstructionState.Idle;
        super.shutdown();
    }

    private void resetRestock() {
        restock.reset();
        workingTile = null;
        missingHotspotTicks = 0;
        houseReturnAttempts = 0;
        statusMessage = "";
    }

    public void grabPlanksWhileWeBuild(net.runelite.client.plugins.microbot.construction.ConstructionConfig config, int actionDelay){
        int plankId = config.selectedMode().getPlankItemId();
        int planks = Rs2Inventory.count(plankId);
        int notedPlanks = Rs2Inventory.itemQuantity(plankId + 1);
        boolean needsPlanks = state == ConstructionState.Butler;
        Rs2NpcModel butler = getButler();
        restock.observe(planks, butler != null, System.currentTimeMillis(), needsPlanks);

        ServantRestock.Servant servant = servantKind(butler);
        ServantRestock.Problem problem = restock.problem(servant, notedPlanks, false, 0, needsPlanks);
        if (problem != ServantRestock.Problem.NONE) {
            stop(problem.getMessage());
            return;
        }

        if (restock.isAway()) {
            statusMessage = "Waiting for the demon butler";
            if (restock.hasReturned(butler != null, Rs2Dialogue.isInDialogue())) {
                restock.onReturned();
                butler(config, butler);
            }
            return;
        }

        boolean butlerTalking = Rs2Dialogue.isInDialogue() || (butler != null && butler.isInteractingWithPlayer());
        boolean wantsMore = servant != ServantRestock.Servant.OTHER && notedPlanks > 0 && restock.canAttempt() && planks <= Rs2Random.between(0, 18);
        if (butlerTalking || needsPlanks || wantsMore) {
            statusMessage = "Restocking planks";
            butler(config, butler);
        }
    }

    private ServantRestock.Servant servantKind(Rs2NpcModel butler) {
        if (butler != null) return ServantRestock.Servant.DEMON_BUTLER;
        if (getOtherServant() != null) return ServantRestock.Servant.OTHER;
        return ServantRestock.Servant.NONE;
    }

    private void stop(String message) {
        state = ConstructionState.Stopped;
        statusMessage = message;
        super.shutdown();
        Microbot.log(message);
        Microbot.getNotifier().notify(message);
        SwingUtilities.invokeLater(() -> Microbot.showMessage(message));
    }

    private void calculateState(net.runelite.client.plugins.microbot.construction.ConstructionConfig config) {
        List<Integer> objectIDs;
        int requiredPlanks;
        switch (config.selectedMode()) {
            case OAK_DUNGEON_DOOR:
                objectIDs = OAK_DUNGEON_DOOR;
                requiredPlanks = 10;
                break;
            case OAK_LARDER:
                objectIDs = OAK_LARDER;
                requiredPlanks = 8;
                break;
            case MAHOGANY_TABLE:
                objectIDs = MAHOGANY_TABLE;
                requiredPlanks = 6;
                break;
            default:
                return;
        }
        boolean hasRequiredPlanks = Rs2Inventory.hasItemAmount(config.selectedMode().getPlankItemId(), requiredPlanks);

        Rs2TileObjectModel objOnWorkingTile = getObjectOnWorkingTile();
        if (objOnWorkingTile == null || !objectIDs.contains(objOnWorkingTile.getId())) {
            Rs2TileObjectModel closest = getClosestTile(objectIDs);
            workingTile = closest != null ? closest.getWorldLocation() : null;
            objOnWorkingTile = closest;
        }

        boolean hotspotFound = objOnWorkingTile != null;
        if (hotspotFound) {
            missingHotspotTicks = 0;
            houseReturnAttempts = 0;
        } else {
            missingHotspotTicks++;
        }
        state = ConstructionState.next(hotspotFound, hotspotFound && objOnWorkingTile.getId() == objectIDs.get(0), hasRequiredPlanks);
    }

    private Rs2TileObjectModel getObjectOnWorkingTile() {
        if (workingTile == null) return null;
        return Microbot.getRs2TileObjectCache().query()
                .where(o -> workingTile.equals(o.getWorldLocation()))
                .nearest();
    }

    private void returnToTheHouse(){
        if (missingHotspotTicks < MISSING_HOTSPOT_TICKS) return;
        if (houseReturnAttempts >= MAX_HOUSE_RETURN_ATTEMPTS) {
            stop(NO_HOTSPOT_MESSAGE);
            return;
        }
        Microbot.getNotifier().notify("Looks like we are no longer in our house.");
        Rs2TileObjectModel housePortal = Microbot.getRs2TileObjectCache().query().withName("Portal").nearestOnClientThread();
        if (housePortal == null || !housePortal.click("Build mode")) {
            stop(NO_HOTSPOT_MESSAGE);
            return;
        }
        houseReturnAttempts++;
        missingHotspotTicks = 0;
        sleepUntil(()-> Rs2Player.getWorldLocation() != null
                && Rs2Player.getWorldLocation().getRegionX() == 29
                    && Rs2Player.getWorldLocation().getRegionY() == 89, Rs2Random.between(10000,20000));
        sleep(2000,5000);
    }

    private void buildSpace(net.runelite.client.plugins.microbot.construction.ConstructionConfig config, int actionDelay) {
        Rs2TileObjectModel space = Microbot.getRs2TileObjectCache().query()
                .where(o -> o.getWorldLocation().equals(workingTile))
                .nearest();
        int spaceId = space != null ? space.getId() : -1;
        char buildKey = '1';

        switch (config.selectedMode()) {
            case OAK_DUNGEON_DOOR:
                buildKey = '1';
                break;
            case OAK_LARDER:
                buildKey = '2';
                break;
            case MAHOGANY_TABLE:
                buildKey = '6';
                break;
            default:
                return;
        }

        if (space == null) return;
        if (space.click("Build")) {
            System.out.println("Interacted with build space: " + space.getId());
            sleepUntilOnClientThread(this::hasFurnitureInterfaceOpen, 2500);
            System.out.println("Pressing key: " + buildKey);
            Rs2Keyboard.keyPress(buildKey); // Ensure this is the correct key for the selected build option
            sleepUntilOnClientThread(() -> spaceId != space.getId(), 2500);
            System.out.println("Built object: " + config.selectedMode());
        } else {
            System.out.println("Failed to interact with build space: " + space.getId());
        }
    }

    private void removeSpace(net.runelite.client.plugins.microbot.construction.ConstructionConfig config, int actionDelay) {
        Rs2TileObjectModel builtObject = Microbot.getRs2TileObjectCache().query()
                .where(o -> o.getWorldLocation().equals(workingTile))
                .nearest();
        int spaceId = builtObject != null ? builtObject.getId() : -1;

        if (builtObject == null) return;
        if(builtObject.getId() == 15328 || builtObject.getId() == 15403 || builtObject.getId() == 15298 || builtObject.getId() == 31986) return;

        if (builtObject.click("Remove")) {
            System.out.println("Interacted with remove option: " + builtObject.getId());
            sleepUntilOnClientThread(() -> hasRemoveInterfaceOpen(config), 2500);
            Rs2Keyboard.keyPress('1');
            sleepUntilOnClientThread(() -> spaceId != builtObject.getId(), 2500);
            System.out.println("Removed object: " + config.selectedMode());
        } else {
            System.out.println("Failed to interact with remove option: " + builtObject.getId());
        }
    }

    private void butler(net.runelite.client.plugins.microbot.construction.ConstructionConfig config, Rs2NpcModel butler) {
        if (!Rs2Dialogue.isInDialogue()) {
            if (shouldCallServant(butler)) {
                if (!callServant()) {
                    restock.onFailedAttempt();
                    return;
                }
            } else if (!butler.click("Talk-to") || !sleepUntil(Rs2Dialogue::isInDialogue, Rs2Random.between(2000, 5000))) {
                restock.onFailedAttempt();
                return;
            }
            butler = getButler();
        }

        int notedPlankId = config.selectedMode().getPlankItemId() + 1;
        sleep(500);
        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        sleep(400, 1000);
        if (Rs2Widget.findWidget("Go to the bank", null) != null) {
            if (butler == null || Rs2Inventory.itemQuantity(notedPlankId) <= 0) {
                restock.onFailedAttempt();
                return;
            }
            if (!Rs2Inventory.useItemOnNpc(notedPlankId, butler.getId())
                    || !sleepUntil(() -> Rs2Widget.hasWidget("Dost thou wish me to exchange that certificate"))) {
                restock.onFailedAttempt();
                return;
            }
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            if (!sleepUntil(() -> Rs2Widget.hasWidget("Select an option"))) {
                restock.onFailedAttempt();
                return;
            }
            Rs2Keyboard.typeString("1");
            if (!sleepUntil(() -> Rs2Widget.hasWidget("Enter amount:"))) {
                restock.onFailedAttempt();
                return;
            }
            Rs2Keyboard.typeString("28");
            Rs2Keyboard.enter();
            restock.onSent(System.currentTimeMillis());
        } else if (hasDialogueOptionToUnnote()) {
            Rs2Keyboard.keyPress('1');
            sleepUntilOnClientThread(() -> !hasDialogueOptionToUnnote());
        } else if (hasPayButlerDialogue() || hasDialogueOptionToPay()) {
            int coins = Rs2Inventory.itemQuantity(ItemID.COINS);
            ServantRestock.Problem problem = restock.problem(ServantRestock.Servant.DEMON_BUTLER, Rs2Inventory.itemQuantity(notedPlankId), true, coins, false);
            if (problem != ServantRestock.Problem.NONE) {
                stop(problem.getMessage());
                return;
            }
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            sleep(400, 1000);
            if (hasDialogueOptionToPay()) {
                Rs2Keyboard.keyPress('1');
            }
        } else if(hasDialogueRepeatLastTask()){
            if (Rs2Inventory.itemQuantity(notedPlankId) <= 0) {
                restock.onFailedAttempt();
                return;
            }
            Rs2Keyboard.keyPress('1');
            restock.onSent(System.currentTimeMillis());
        }
    }

    private boolean shouldCallServant(Rs2NpcModel butler) {
        if (butler == null) return true;
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldPoint butlerLocation = butler.getWorldLocation();
        return playerLocation == null || butlerLocation == null || butlerLocation.distanceTo(playerLocation) > 3;
    }

    private boolean callServant() {
        Rs2Tab.switchTo(InterfaceTab.SETTINGS);
        if (!sleepUntil(() -> Rs2Widget.isWidgetVisible(InterfaceID.SettingsSide.HOUSEOPTIONS), Rs2Random.between(2000, 5000))) {
            return false;
        }
        Widget houseOptions = Rs2Widget.getWidget(InterfaceID.SettingsSide.HOUSEOPTIONS);
        if (houseOptions == null || !Rs2Widget.clickWidget(houseOptions)) {
            return false;
        }
        if (!sleepUntil(() -> Rs2Widget.isWidgetVisible(InterfaceID.PohOptions.CALL_SERVANT), Rs2Random.between(2000, 5000))) {
            return false;
        }
        Widget callServant = Rs2Widget.getWidget(InterfaceID.PohOptions.CALL_SERVANT);
        if (callServant == null || !Rs2Widget.clickWidget(callServant)) {
            return false;
        }
        return sleepUntil(Rs2Dialogue::isInDialogue, Rs2Random.between(2000, 5000));
    }

    private boolean hasRemoveInterfaceOpen(ConstructionConfig config) {
        switch (config.selectedMode()) {
            case OAK_DUNGEON_DOOR:
                return hasRemoveDoorInterfaceOpen();
            case OAK_LARDER:
                return hasRemoveLarderInterfaceOpen();
            case MAHOGANY_TABLE:
                return hasRemoveTableInterfaceOpen();
            // case MYTHICAL_CAPE:
            // return hasRemoveCapeMountInterfaceOpen();
            default:
                return false;
        }
    }

    public ConstructionState getState() {
        return state;
    }

    public String getStatusMessage() {
        return statusMessage;
    }
}
