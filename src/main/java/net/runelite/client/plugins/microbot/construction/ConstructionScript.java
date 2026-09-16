package net.runelite.client.plugins.microbot.construction;

import java.util.List;
import java.util.concurrent.TimeUnit;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.construction.enums.ConstructionState;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

public class ConstructionScript extends Script {

    private static final int DEFAULT_DELAY = 600;

    private static final int HOUSE_OPTIONS_WIDGET_ID = 7602207;
    private static final int CALL_SERVANT_WIDGET_ID = 24248342;

    private static final List<Integer> OAK_DUNGEON_DOOR =
            List.of(13344, 15328);

    private static final List<Integer> OAK_LARDER =
            List.of(13566, 15403);

    private static final List<Integer> MAHOGANY_TABLE =
            List.of(13298, 15298);

    private ConstructionState state;
    private WorldPoint workingTile;

    private boolean waitingForButlerReturn = false;
    private boolean butlerDepartureConfirmed = false;

    public ConstructionScript() {
        this.state = ConstructionState.Idle;
        this.workingTile = null;
    }

    public Rs2TileObjectModel getClosestTile(List<Integer> objIDs) {
        int[] ids = objIDs.stream()
                .mapToInt(Integer::intValue)
                .toArray();

        return (Rs2TileObjectModel)
                ((Rs2TileObjectQueryable)
                        Microbot.getRs2TileObjectCache()
                                .query()
                                .withIds(ids))
                        .nearest();
    }

    public Rs2NpcModel getButler() {
        return (Rs2NpcModel)
                ((Rs2NpcQueryable)
                        Microbot.getRs2NpcCache()
                                .query()
                                .withName("Demon butler"))
                        .nearestOnClientThread();
    }

    public boolean hasDialogueOptionToUnnote() {
        return Rs2Widget.findWidget("Un-note", null) != null;
    }

    public boolean hasDialogueRepeatLastTask() {
        return Rs2Widget.hasWidget("Repeat last task?");
    }

    public boolean hasPayButlerDialogue() {
        return Rs2Widget.findWidget(
                "must render unto me the 10,000 coins that are due",
                null
        ) != null;
    }

    public boolean hasDialogueOptionToPay() {
        return Rs2Widget.findWidget(
                "Okay, here's 10,000 coins.",
                null
        ) != null;
    }

    public boolean hasFurnitureInterfaceOpen() {
        return Rs2Widget.findWidget("Furniture", null) != null;
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

    public boolean run(ConstructionConfig config) {

        int actionDelay =
                config.useCustomDelay()
                        ? config.actionDelay()
                        : DEFAULT_DELAY;

        this.mainScheduledFuture =
                this.scheduledExecutorService.scheduleWithFixedDelay(() -> {

                    try {

                        if (!Microbot.isLoggedIn()) {
                            return;
                        }

                        if (!super.run()) {
                            return;
                        }

                        Rs2Tab.switchTo(InterfaceTab.INVENTORY);

                        this.calculateState(config);

                        switch (this.state) {

                            case Build:
                                this.grabPlanksWhileWeBuild(config, actionDelay);
                                this.buildSpace(config);
                                break;

                            case Remove:
                                this.removeSpace(config);
                                break;

                            case Butler:
                                this.grabPlanksWhileWeBuild(config, actionDelay);
                                break;

                            case Idle:
                            default:
                                break;
                        }

                    } catch (Exception ex) {

                        Microbot.logStackTrace(
                                this.getClass().getSimpleName(),
                                ex
                        );
                    }

                }, 0L, actionDelay, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown() {
        waitingForButlerReturn = false;
        butlerDepartureConfirmed = false;
        workingTile = null;
        state = ConstructionState.Idle;

        super.shutdown();
    }

    public void grabPlanksWhileWeBuild(
            ConstructionConfig config,
            int actionDelay
    ) {

        if (waitingForButlerReturn) {

            if (!butlerDepartureConfirmed) {

                if (!Rs2Dialogue.isInDialogue()) {
                    butlerDepartureConfirmed = true;

                    System.out.println(
                            "Construction: Butler departure confirmed."
                    );
                }

                return;
            }

            if (Rs2Dialogue.isInDialogue()) {

                waitingForButlerReturn = false;
                butlerDepartureConfirmed = false;

                System.out.println(
                        "Construction: Butler return dialogue detected."
                );

                this.butler(config, actionDelay);
                return;
            }

            Rs2NpcModel returnedButler = this.getButler();

            if (returnedButler == null) {
                return;
            }

            waitingForButlerReturn = false;
            butlerDepartureConfirmed = false;

            System.out.println(
                    "Construction: Butler has returned. Opening dialogue manually."
            );

            if (returnedButler.click("Talk-to")) {
                sleepUntil(
                        Rs2Dialogue::isInDialogue,
                        Rs2Random.between(2000, 5000)
                );
            }

            this.butler(config, actionDelay);
            return;
        }

        Rs2NpcModel butler = this.getButler();

        int plankCount =
                Rs2Inventory.count(
                        config.selectedMode().getPlankItemId()
                );

        int callThreshold =
                Rs2Random.between(0, 18);

        if (butler == null) {

            if (plankCount <= callThreshold) {
                this.butler(config, actionDelay);
            }

            return;
        }

        sleepUntil(() -> {

            Rs2NpcModel currentButler =
                    this.getButler();

            return currentButler != null
                    && currentButler.isInteractingWithPlayer();

        }, Rs2Random.between(750, 1500));

        butler = this.getButler();

        boolean interacting =
                butler != null
                        && butler.isInteractingWithPlayer();

        if (interacting || plankCount <= callThreshold) {
            this.butler(config, actionDelay);
        }
    }

    private void calculateState(
            ConstructionConfig config
    ) {

        List<Integer> objectIDs;
        boolean hasRequiredPlanks;

        switch (config.selectedMode()) {

            case OAK_DUNGEON_DOOR:

                objectIDs = OAK_DUNGEON_DOOR;

                hasRequiredPlanks =
                        Rs2Inventory.hasItemAmount(
                                config.selectedMode().getPlankItemId(),
                                10
                        );

                break;

            case OAK_LARDER:

                objectIDs = OAK_LARDER;

                hasRequiredPlanks =
                        Rs2Inventory.hasItemAmount(
                                config.selectedMode().getPlankItemId(),
                                8
                        );

                break;

            case MAHOGANY_TABLE:

                objectIDs = MAHOGANY_TABLE;

                hasRequiredPlanks =
                        Rs2Inventory.hasItemAmount(
                                config.selectedMode().getPlankItemId(),
                                6
                        );

                break;

            default:

                this.state = ConstructionState.Idle;
                return;
        }

        Rs2TileObjectModel closest =
                this.getClosestTile(objectIDs);

        if (closest == null) {
            this.state = ConstructionState.Idle;
            return;
        }

        if (this.workingTile == null) {
            this.workingTile = closest.getWorldLocation();
        }

        Rs2TileObjectModel objOnWorkingTile =
                this.getObjectOnWorkingTile();

        if (objOnWorkingTile == null
                || !objectIDs.contains(objOnWorkingTile.getId())) {

            closest =
                    this.getClosestTile(objectIDs);

            if (closest == null) {
                this.state = ConstructionState.Idle;
                return;
            }

            this.workingTile = closest.getWorldLocation();

            objOnWorkingTile =
                    this.getObjectOnWorkingTile();
        }

        if (objOnWorkingTile == null) {
            this.state = ConstructionState.Idle;
            return;
        }

        int objectId =
                objOnWorkingTile.getId();

        int builtObjectId =
                objectIDs.get(0);

        int buildSpaceId =
                objectIDs.get(1);

        if (objectId == builtObjectId) {

            this.state = ConstructionState.Remove;

        } else if (
                objectId == buildSpaceId
                        && hasRequiredPlanks
        ) {

            this.state = ConstructionState.Build;

        } else if (
                objectId == buildSpaceId
        ) {

            this.state = ConstructionState.Butler;

        } else {

            this.state = ConstructionState.Idle;
        }
    }

    private Rs2TileObjectModel getObjectOnWorkingTile() {

        if (this.workingTile == null) {
            return null;
        }

        return (Rs2TileObjectModel)
                ((Rs2TileObjectQueryable)
                        Microbot.getRs2TileObjectCache()
                                .query()
                                .where(o ->
                                        o.getWorldLocation()
                                                .equals(this.workingTile)
                                ))
                        .nearest();
    }

    private void returnToTheHouse() {

        Rs2TileObjectModel housePortal =
                (Rs2TileObjectModel)
                        ((Rs2TileObjectQueryable)
                                Microbot.getRs2TileObjectCache()
                                        .query()
                                        .withName("Portal"))
                                .nearestOnClientThread();

        if (housePortal != null) {

            if (housePortal.click("Build mode")) {

                sleepUntil(() ->

                                Rs2Player.getWorldLocation() != null
                                        && Rs2Player
                                        .getWorldLocation()
                                        .getRegionX() == 29
                                        && Rs2Player
                                        .getWorldLocation()
                                        .getRegionY() == 89,

                        Rs2Random.between(
                                10000,
                                20000
                        )
                );

                sleep(2000, 5000);
            }

        } else {

            Microbot.getNotifier()
                    .notify(
                            "Can't find the house portal!"
                    );

            this.shutdown();
        }
    }

    private void buildSpace(
            ConstructionConfig config
    ) {

        Rs2TileObjectModel space =
                this.getObjectOnWorkingTile();

        if (space == null) {
            return;
        }

        int oldObjectId =
                space.getId();

        char buildKey;

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

        if (!space.click("Build")) {

            System.out.println(
                    "Failed to interact with build space: "
                            + space.getId()
            );

            return;
        }

        System.out.println(
                "Interacted with build space: "
                        + space.getId()
        );

        sleepUntilOnClientThread(
                this::hasFurnitureInterfaceOpen,
                2500
        );

        Rs2Keyboard.keyPress(buildKey);

        sleepUntilOnClientThread(() -> {

            Rs2TileObjectModel current =
                    this.getObjectOnWorkingTile();

            return current != null
                    && current.getId() != oldObjectId;

        }, 2500);

        System.out.println(
                "Built object: "
                        + config.selectedMode()
        );
    }

    private void removeSpace(
            ConstructionConfig config
    ) {

        Rs2TileObjectModel builtObject =
                this.getObjectOnWorkingTile();

        if (builtObject == null) {
            return;
        }

        int oldObjectId =
                builtObject.getId();

        if (oldObjectId == 15328
                || oldObjectId == 15403
                || oldObjectId == 15298
                || oldObjectId == 31986) {

            return;
        }

        if (!builtObject.click("Remove")) {

            System.out.println(
                    "Failed to interact with remove option: "
                            + builtObject.getId()
            );

            return;
        }

        System.out.println(
                "Interacted with remove option: "
                        + builtObject.getId()
        );

        sleepUntilOnClientThread(
                () -> this.hasRemoveInterfaceOpen(config),
                2500
        );

        Rs2Keyboard.keyPress('1');

        sleepUntilOnClientThread(() -> {

            Rs2TileObjectModel current =
                    this.getObjectOnWorkingTile();

            return current != null
                    && current.getId() != oldObjectId;

        }, 2500);

        System.out.println(
                "Removed object: "
                        + config.selectedMode()
        );
    }

    private void butler(
            ConstructionConfig config,
            int actionDelay
    ) {

        if (waitingForButlerReturn) {

            if (!butlerDepartureConfirmed) {

                if (!Rs2Dialogue.isInDialogue()) {
                    butlerDepartureConfirmed = true;

                    System.out.println(
                            "Construction: Butler departure confirmed."
                    );
                }

                return;
            }

            if (Rs2Dialogue.isInDialogue()) {

                waitingForButlerReturn = false;
                butlerDepartureConfirmed = false;

                System.out.println(
                        "Construction: Butler return dialogue detected."
                );

            } else {

                Rs2NpcModel returnedButler =
                        this.getButler();

                if (returnedButler == null) {
                    return;
                }

                waitingForButlerReturn = false;
                butlerDepartureConfirmed = false;

                System.out.println(
                        "Construction: Butler returned without active dialogue."
                );

                if (!returnedButler.click("Talk-to")) {
                    return;
                }

                if (!sleepUntil(
                        Rs2Dialogue::isInDialogue,
                        Rs2Random.between(2000, 5000)
                )) {
                    return;
                }
            }
        }

        Rs2NpcModel butler =
                this.getButler();

        if (!Rs2Dialogue.isInDialogue()) {

            if (this.shouldCallServant(butler)) {

                if (!this.callServant()) {
                    return;
                }

            } else {

                if (butler == null
                        || !butler.click("Talk-to")) {

                    return;
                }

                if (!sleepUntil(
                        Rs2Dialogue::isInDialogue,
                        Rs2Random.between(2000, 5000)
                )) {
                    return;
                }
            }
        }

        if (butler == null) {

            sleepUntil(
                    () -> this.getButler() != null,
                    2500
            );

            butler = this.getButler();
        }

        if (!Rs2Dialogue.isInDialogue()) {
            return;
        }

        sleep(500);

        Rs2Keyboard.keyPress(32);

        sleep(400, 1000);

        if (Rs2Widget.findWidget(
                "Go to the bank",
                null
        ) != null) {

            if (butler == null) {
                return;
            }

            Rs2Inventory.useItemOnNpc(
                    config.selectedMode()
                            .getPlankItemId() + 1,
                    butler.getId()
            );

            sleepUntilOnClientThread(
                    () ->
                            Rs2Widget.hasWidget(
                                    "Dost thou wish me to exchange that certificate"
                            )
            );

            Rs2Keyboard.keyPress(32);

            sleepUntilOnClientThread(
                    () ->
                            Rs2Widget.hasWidget(
                                    "Select an option"
                            )
            );

            Rs2Keyboard.typeString("1");

            sleepUntilOnClientThread(
                    () ->
                            Rs2Widget.hasWidget(
                                    "Enter amount:"
                            )
            );

            Rs2Keyboard.typeString("28");

            waitingForButlerReturn = true;
            butlerDepartureConfirmed = false;

            System.out.println(
                    "Construction: Butler sent to bank. Waiting for departure."
            );

            Rs2Keyboard.enter();

            return;

        } else if (
                this.hasDialogueOptionToUnnote()
        ) {

            Rs2Keyboard.keyPress('1');

            sleepUntilOnClientThread(
                    () ->
                            !this.hasDialogueOptionToUnnote()
            );

        } else if (
                this.hasPayButlerDialogue()
                        || this.hasDialogueOptionToPay()
        ) {

            Rs2Keyboard.keyPress(32);

            sleep(400, 1000);

            if (this.hasDialogueOptionToPay()) {
                Rs2Keyboard.keyPress('1');
            }

        } else if (
                this.hasDialogueRepeatLastTask()
        ) {

            waitingForButlerReturn = true;
            butlerDepartureConfirmed = false;

            System.out.println(
                    "Construction: Repeating Butler bank task. Waiting for departure."
            );

            Rs2Keyboard.keyPress('1');

            return;
        }
    }

    private boolean shouldCallServant(
            Rs2NpcModel butler
    ) {

        if (waitingForButlerReturn) {
            return false;
        }

        if (butler == null) {
            return true;
        }

        return Microbot.getClientThread()
                .runOnClientThreadOptional(() -> {

                    if (Microbot
                            .getClient()
                            .getLocalPlayer() == null

                            || Microbot
                            .getClient()
                            .getLocalPlayer()
                            .getWorldLocation() == null

                            || butler
                            .getWorldLocation() == null) {

                        return true;
                    }

                    return butler
                            .getWorldLocation()
                            .distanceTo(
                                    Microbot
                                            .getClient()
                                            .getLocalPlayer()
                                            .getWorldLocation()
                            ) > 3;

                })
                .orElse(true);
    }

    private boolean callServant() {

        if (waitingForButlerReturn) {
            return false;
        }

        Rs2Tab.switchTo(
                InterfaceTab.SETTINGS
        );

        int timeout =
                Rs2Random.between(
                        2000,
                        5000
                );

        boolean houseOptionsAvailable =
                sleepUntil(
                        () ->
                                Rs2Widget.isWidgetVisible(
                                        HOUSE_OPTIONS_WIDGET_ID
                                )

                                        || Rs2Widget.findWidget(
                                        "House Options",
                                        null
                                ) != null,

                        timeout
                );

        if (!houseOptionsAvailable) {

            System.out.println(
                    "Construction: House Options widget did not appear."
            );

            return false;
        }

        Widget houseOptions =
                Rs2Widget.getWidget(
                        HOUSE_OPTIONS_WIDGET_ID
                );

        if (houseOptions == null) {

            houseOptions =
                    Rs2Widget.findWidget(
                            "House Options",
                            null
                    );
        }

        if (houseOptions == null
                || !Rs2Widget.clickWidget(
                houseOptions
        )) {

            System.out.println(
                    "Construction: Failed to click House Options."
            );

            return false;
        }

        timeout =
                Rs2Random.between(
                        2000,
                        5000
                );

        boolean callServantAvailable =
                sleepUntil(
                        () ->
                                Rs2Widget.isWidgetVisible(
                                        CALL_SERVANT_WIDGET_ID
                                )

                                        || Rs2Widget.findWidget(
                                        "Call Servant",
                                        null
                                ) != null,

                        timeout
                );

        if (!callServantAvailable) {

            System.out.println(
                    "Construction: Call Servant widget did not appear."
            );

            return false;
        }

        Widget callServant =
                Rs2Widget.getWidget(
                        CALL_SERVANT_WIDGET_ID
                );

        if (callServant == null) {

            callServant =
                    Rs2Widget.findWidget(
                            "Call Servant",
                            null
                    );
        }

        if (callServant == null
                || !Rs2Widget.clickWidget(
                callServant
        )) {

            System.out.println(
                    "Construction: Failed to click Call Servant."
            );

            return false;
        }

        return sleepUntil(
                Rs2Dialogue::isInDialogue,
                Rs2Random.between(
                        2000,
                        5000
                )
        );
    }

    private boolean hasRemoveInterfaceOpen(
            ConstructionConfig config
    ) {

        switch (config.selectedMode()) {

            case OAK_DUNGEON_DOOR:
                return this.hasRemoveDoorInterfaceOpen();

            case OAK_LARDER:
                return this.hasRemoveLarderInterfaceOpen();

            case MAHOGANY_TABLE:
                return this.hasRemoveTableInterfaceOpen();

            default:
                return false;
        }
    }

    public ConstructionState getState() {
        return this.state;
    }
}