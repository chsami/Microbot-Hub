package net.runelite.client.plugins.microbot.cluesolver.cluetask;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.AnagramClue;
import net.runelite.client.plugins.microbot.cluesolver.ClueSolverPlugin;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Slf4j
public class AnagramClueTask extends ClueTask {
    private final AnagramClue clue;
    private final EventBus eventBus;
    private final ExecutorService backgroundExecutor;
    private WorldPoint location;
    private Future<?> currentTask;
    private String npcName;
    private String answer;
    private enum State {
        WALKING_TO_LOCATION,
        INTERACTING_WITH_OBJECT,
        INTERACTING_WITH_NPC,
        HANDLING_DIALOGUE,
        COMPLETED
    }

    private State state = State.WALKING_TO_LOCATION;

    public AnagramClueTask(Client client, AnagramClue clue, ClueScrollPlugin clueScrollPlugin,
                           ClueSolverPlugin clueSolverPlugin, EventBus eventBus, ExecutorService backgroundExecutor) {
        super(client, clueScrollPlugin, clueSolverPlugin);
        this.clue = clue;
        this.eventBus = eventBus;
        this.backgroundExecutor = backgroundExecutor;
        this.location = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> clue.getLocation(clueScrollPlugin))
                .orElse(null);
    }

    @Override
    protected boolean executeTask() throws Exception {
        eventBus.register(this);
        log.info("Executing AnagramClueTask.");
        walkToLocation();
        return true; // Task runs asynchronously; completion managed in onGameTick.
    }

    private void walkToLocation() {
        if (location == null) {
            log.error("Clue location is null.");
            completeTask(false);
            return;
        }
        log.info("Walking to location: {}", location);
        boolean startedWalking = Rs2Walker.walkTo(location, 2);
        if (!startedWalking) {
            log.error("Failed to initiate walking to location: {}", location);
            completeTask(false);
        }
    }

    @SneakyThrows
    @Subscribe
    public void onGameTick(GameTick event) {
        if (currentTask != null && !currentTask.isDone()) {
            log.warn("Previous task is still running, skipping this tick.");
            return;
        }
        currentTask = backgroundExecutor.submit(() -> {
            try {
                processGameTick(event);
            } catch (Exception ex) {
                log.error("Error processing game tick", ex);
            }
        });
    }

    private void processGameTick(GameTick event) {
        // v1.0.5 fix: client-thread-safe location read. processGameTick runs in the
        // background executor; direct client.getLocalPlayer() throws IllegalStateException.
        net.runelite.api.coords.WorldPoint playerLocation = getPlayerLocationSafe();
        if (playerLocation == null) return;

        switch (state) {
            case WALKING_TO_LOCATION:
                if (playerLocation.equals(location)) {
                    transitionToInteractionState();
                } else if (isWithinRadius(location, playerLocation, 3)) {
                    Rs2Walker.walkFastCanvas(location);
                }
                break;

            case INTERACTING_WITH_OBJECT:
                if (interactWithObject()) {
                    completeTask(true);
                    state = State.COMPLETED;
                } else {
                    completeTask(false);
                }
                break;

            case INTERACTING_WITH_NPC:
                if (interactWithNpc()) {
                    state = State.HANDLING_DIALOGUE;
                } else {
                    completeTask(false);
                }
                break;

            case HANDLING_DIALOGUE:
                if (handleDialogue()) {
                    completeTask(true);
                    state = State.COMPLETED;
                } else {
                    completeTask(false);
                }
                break;

            case COMPLETED:
                completeTask(true);
                break;

            default:
                log.warn("Unknown state encountered in AnagramClueTask: {}", state);
                completeTask(false);
                break;
        }
    }


    private void transitionToInteractionState() {
        if (clue.getObjectId() != -1) {
            state = State.INTERACTING_WITH_OBJECT;
        } else if (clue.getNpcProvider() != null && resolveNpcName() != null
                && Microbot.getRs2NpcCache().query().withName(resolveNpcName()).nearestOnClientThread() != null) {
            state = State.INTERACTING_WITH_NPC;
        } else {
            log.warn("No valid interaction target found.");
            completeTask(false);
        }
    }

    private String resolveNpcName() {
        if (npcName == null) {
            npcName = Microbot.getClientThread()
                    .runOnClientThreadOptional(() -> clue.getNpcName(clueScrollPlugin))
                    .orElse(null);
        }
        return npcName;
    }

    private String resolveAnswer() {
        if (answer == null) {
            answer = Microbot.getClientThread()
                    .runOnClientThreadOptional(() -> clue.getAnswer(clueScrollPlugin))
                    .orElse(null);
        }
        return answer;
    }

    private boolean interactWithObject() {
        int targetObject = clue.getObjectId();
        boolean interacted = Microbot.getRs2TileObjectCache().query().interact(targetObject, "Search")
                || Microbot.getRs2TileObjectCache().query().interact(targetObject, "Investigate")
                || Microbot.getRs2TileObjectCache().query().interact(targetObject, "Examine")
                || Microbot.getRs2TileObjectCache().query().interact(targetObject, "Look-at")
                || Microbot.getRs2TileObjectCache().query().interact(targetObject, "Open");

        if (interacted) {
            log.info("Interacted with object for clue.");
        } else {
            log.warn("Object interaction failed.");
        }
        return interacted;
    }

    private boolean interactWithNpc() {
        String targetName = resolveNpcName();
        if (targetName == null) {
            log.warn("NPC name for anagram clue is unavailable.");
            return false;
        }
        var targetNpc = Microbot.getRs2NpcCache().query().withName(targetName).nearestOnClientThread();
        if (targetNpc == null) {
            log.warn("NPC {} not found.", targetName);
            return false;
        }

        boolean interacted = targetNpc.click("Talk-to");
        if (interacted) {
            log.info("Talking to NPC: {}", targetName);
            Rs2Dialogue.sleepUntilInDialogue();
        } else {
            log.warn("Failed to talk to NPC: {}", targetName);
        }
        return interacted;
    }

    private boolean handleDialogue() {
        String answer = resolveAnswer();
        log.info("Answering dialogue: {}", answer);

        if (Rs2Dialogue.isInDialogue()) {
            if (Rs2Dialogue.hasContinue()) {
                Rs2Dialogue.clickContinue();
            }

            if (answer != null && !answer.isBlank()) {
                Rs2Keyboard.typeString(answer);
                Rs2Keyboard.enter();
                Rs2Dialogue.sleepUntilHasContinue();
            }

            if (Rs2Dialogue.hasContinue()) {
                Rs2Dialogue.clickContinue();
            }
            return true;
        }
        log.warn("Dialogue handling failed.");
        return false;
    }

    private boolean isWithinRadius(WorldPoint targetLocation, WorldPoint playerLocation, int radius) {
        int deltaX = Math.abs(targetLocation.getX() - playerLocation.getX());
        int deltaY = Math.abs(targetLocation.getY() - playerLocation.getY());
        return deltaX <= radius && deltaY <= radius;
    }

    @Override
    protected void completeTask(boolean success) {
        super.completeTask(success);
        eventBus.unregister(this);
        log.info("Anagram clue task completed with status: {}", success ? "Success" : "Failure");
    }
}
