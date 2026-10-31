package net.runelite.client.plugins.microbot.cluesolver.cluetask;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.HotColdClue;
import net.runelite.client.plugins.microbot.cluesolver.ClueSolverPlugin;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Slf4j
public class HotColdClueTask extends ClueTask {
    private final HotColdClue clue;
    private final EventBus eventBus;
    private final ExecutorService backgroundExecutor;
    private Future<?> currentTask;
    private WorldPoint digLocation;

    public HotColdClueTask(Client client, HotColdClue clue, ClueScrollPlugin clueScrollPlugin,
                           ClueSolverPlugin clueSolverPlugin, EventBus eventBus, ExecutorService backgroundExecutor) {
        super(client, clueScrollPlugin, clueSolverPlugin);
        this.clue = clue;
        this.eventBus = eventBus;
        this.backgroundExecutor = backgroundExecutor;
    }

    @Override
    protected boolean executeTask() throws Exception {
        eventBus.register(this);
        log.info("Starting HotColdClueTask, waiting for the strange device to find the dig spot.");
        return true;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (currentTask != null && !currentTask.isDone()) {
            return;
        }
        currentTask = backgroundExecutor.submit(() -> {
            try {
                processGameTick();
            } catch (Exception ex) {
                log.error("Error processing Hot Cold clue tick", ex);
                completeTask(false);
            }
        });
    }

    private void processGameTick() {
        if (!ClueSolverPlugin.isSolverRunning || !clue.equals(clueScrollPlugin.getClue())) {
            completeTask(false);
            return;
        }

        if (digLocation == null) {
            digLocation = clue.getLocation(clueScrollPlugin);
            if (digLocation == null) {
                return;
            }
            log.info("Hot Cold dig spot found: {}", digLocation);
        }

        WorldPoint playerLocation = getPlayerLocationSafe();
        if (playerLocation == null) {
            return;
        }

        if (!digLocation.equals(playerLocation) && !Rs2Walker.walkTo(digLocation, 0)) {
            log.error("Failed to walk to Hot Cold dig spot: {}", digLocation);
            completeTask(false);
            return;
        }

        if (!Rs2Inventory.contains("Spade")) {
            log.warn("Spade not found in inventory.");
            completeTask(false);
            return;
        }

        if (Rs2Inventory.interact(ItemID.SPADE, "Dig")) {
            completeTask(true);
        }
    }

    @Override
    protected void completeTask(boolean success) {
        super.completeTask(success);
        eventBus.unregister(this);
        log.info("Hot Cold clue task completed with status: {}", success ? "Success" : "Failure");
    }
}
