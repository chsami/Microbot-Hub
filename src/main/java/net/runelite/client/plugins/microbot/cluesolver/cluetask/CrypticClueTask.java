package net.runelite.client.plugins.microbot.cluesolver.cluetask;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.CrypticClue;
import net.runelite.client.plugins.microbot.cluesolver.ClueSolverPlugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Slf4j
public class CrypticClueTask extends ClueTask {
    private Future<?> currentTask;
    private final CrypticClue clue;
    private final EventBus eventBus;
    private final ExecutorService backgroundExecutor;
    private enum State { WALKING_TO_LOCATION, KILLING_ENEMY, LOOTING_ITEM, INTERACTING_WITH_OBJECT, INTERACTING_WITH_NPC, HANDLING_DIALOGUE, COMPLETED }
    private State state = State.WALKING_TO_LOCATION;

    public CrypticClueTask(Client client, CrypticClue clue, ClueScrollPlugin clueScrollPlugin,
                           ClueSolverPlugin clueSolverPlugin, EventBus eventBus, ExecutorService backgroundExecutor) {
        super(client, clueScrollPlugin, clueSolverPlugin);
        this.clue = clue;
        this.eventBus = eventBus;
        this.backgroundExecutor = backgroundExecutor;
    }

    @Override
    protected boolean executeTask() throws Exception {
        eventBus.register(this);
        log.info("Starting CrypticClueTask.");
        walkToLocation();
        return true; // Task runs asynchronously, lifecycle managed by `onGameTick`.
    }

    private void walkToLocation() {
        WorldPoint location = clue.getLocation(clueScrollPlugin);
        if (location == null) {
            log.error("Clue location is null.");
            completeTask(false);
            return;
        }

        log.info("Walking to clue location: {}", location);

            if (!Rs2Walker.walkTo(location)) {
                log.error("Failed to initiate walking to location: {}", location);
                completeTask(false);
            }

    }

    private boolean isWithinRadius(WorldPoint targetLocation, WorldPoint playerLocation, int radius) {
        int deltaX = Math.abs(targetLocation.getX() - playerLocation.getX());
        int deltaY = Math.abs(targetLocation.getY() - playerLocation.getY());
        return deltaX <= radius && deltaY <= radius;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (currentTask != null && !currentTask.isDone()) {
            log.warn("Previous task is still running, skipping this tick.");
            return;
        }
        currentTask = backgroundExecutor.submit(() -> {
            try {
                processGameTick(event);
            } catch (Exception e) {
                log.error("Error processing game tick: {}", e.getMessage(), e);
                completeTask(false);
            }
        });
    }

    private void processGameTick(GameTick event) {
        Player player = client.getLocalPlayer();
        WorldPoint playerLocation = player.getWorldLocation();
        WorldPoint clueLocation = clue.getLocation(clueScrollPlugin);

        switch (state) {
            case WALKING_TO_LOCATION:
                if (isWithinRadius(Objects.requireNonNull(clueLocation), playerLocation, 30)) {
                    log.info("Arrived at clue location.");
                    transitionToNextState();
                }
                break;

            case KILLING_ENEMY:
                if (killEnemy()) {
                    state = State.LOOTING_ITEM;
                }
                break;

            case LOOTING_ITEM:
                if (lootGroundItem()) {
                    transitionToNextState();
                }
                break;

            case INTERACTING_WITH_OBJECT:
                if (interactWithObject()) {
                    state = State.COMPLETED;
                    completeTask(true);
                } else {
                    log.warn("Failed to interact with object.");
                    completeTask(false);
                }
                break;

            case INTERACTING_WITH_NPC:
                if (interactWithNpc()) {
                    state = State.HANDLING_DIALOGUE;
                } else {
                    log.warn("Failed to interact with NPC.");
                    completeTask(false);
                }
                break;

            case HANDLING_DIALOGUE:
                if (handleDialogue()) {
                    state = State.COMPLETED;
                    completeTask(true);
                } else {
                    log.warn("Dialogue handling failed.");
                    completeTask(false);
                }
                break;

            case COMPLETED:
                log.info("Cryptic clue task completed.");
                completeTask(true);
                break;

            default:
                log.error("Unknown state: {}", state);
                completeTask(false);
                break;
        }
    }

    private void transitionToNextState() {
        if (clue.getEnemy() != null) {
            state = State.KILLING_ENEMY;
        } else if (clue.getObjectId() != -1) {
            state = State.INTERACTING_WITH_OBJECT;
        } else if (clue.getNpc(clueScrollPlugin) != null && Microbot.getRs2NpcCache().query().withName(clue.getNpc(clueScrollPlugin)).nearestOnClientThread() != null) {
            state = State.INTERACTING_WITH_NPC;
        } else {
            state = State.COMPLETED;
            completeTask(true);
        }
    }

    private boolean killEnemy() {
        Rs2NpcModel enemy = Microbot.getRs2NpcCache().query().withName(clue.getEnemy().name()).nearestOnClientThread();
        if (enemy == null || enemy.getNpc().getHealthRatio() <= 0) {
            log.info("Enemy {} is defeated. Searching for loot.", clue.getEnemy());
            return true;
        }
        if (enemy.click("Attack")) {
            log.info("Started attacking enemy: {}", clue.getEnemy());
        } else {
            log.warn("Failed to attack enemy: {}", clue.getEnemy());
        }
        return false;
    }

    private boolean lootGroundItem() {
        var groundItems = Microbot.getRs2TileItemCache().query().within(10).toList();
        boolean anyLooted = false;

        for (var item : groundItems) {
            if (item.click("Take")) {
                log.info("Successfully picked up item: {}", item);
                anyLooted = true;
            } else {
                log.warn("Item {} not found or could not be picked up.", item);
            }
        }
        return anyLooted;
    }

    private boolean interactWithObject() {
        int targetObject = clue.getObjectId();
        if (Microbot.getRs2TileObjectCache().query().interact(targetObject, "Search")
                || Microbot.getRs2TileObjectCache().query().interact(targetObject, "Investigate")
                || Microbot.getRs2TileObjectCache().query().interact(targetObject, "Examine")
                || Microbot.getRs2TileObjectCache().query().interact(targetObject, "Look-at")
                || Microbot.getRs2TileObjectCache().query().interact(targetObject, "Open")) {
            log.info("Interacted with required object for the clue.");
            return true;
        }
        log.warn("Required object not found for interaction.");
        return false;
    }

    private boolean interactWithNpc() {
        Rs2NpcModel targetNpc = Microbot.getRs2NpcCache().query().withName(clue.getNpc(clueScrollPlugin)).nearestOnClientThread();
        if (targetNpc == null) {
            log.warn("NPC {} not found at the location.", clue.getNpc(clueScrollPlugin));
            return false;
        }
        return targetNpc.click("Talk-to");
    }

    private boolean handleDialogue() {
        Rs2Dialogue.sleepUntilInDialogue();
        if (Rs2Dialogue.isInDialogue() && Rs2Dialogue.hasContinue()) {
            Rs2Dialogue.clickContinue();
            log.info("Handled dialogue continue.");
            return true;
        }
        log.warn("Dialogue with NPC did not progress as expected.");
        return false;
    }

    @Override
    protected void completeTask(boolean success) {
        super.completeTask(success);
        eventBus.unregister(this);
        log.info("Cryptic clue task completed with status: {}", success ? "Success" : "Failure");
    }
}
