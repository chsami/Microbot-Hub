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

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

@Slf4j
public class HotColdClueTask extends ClueTask {
    public static final String STATUS_WAITING_FOR_DEVICE = "Hot Cold clue: use your Strange device until the dig spot is marked. I will walk there and dig.";
    public static final String STATUS_WALKING = "Hot Cold clue: walking to dig spot";
    public static final String STATUS_NO_SPADE = "Hot Cold clue: no Spade in inventory. Add a Spade to dig.";
    public static final String STATUS_DUG = "Hot Cold clue: dug, waiting for result";
    public static final int DIG_RESULT_TICKS = 5;

    private final HotColdClue clue;
    private final EventBus eventBus;
    private final ExecutorService backgroundExecutor;
    private final Consumer<String> statusUpdater;
    private Future<?> currentTask;
    private WorldPoint digLocation;
    private String lastStatus;
    private boolean spadeWarningLogged;
    private boolean dug;
    private int ticksSinceDig;

    public HotColdClueTask(Client client, HotColdClue clue, ClueScrollPlugin clueScrollPlugin,
                           ClueSolverPlugin clueSolverPlugin, EventBus eventBus, ExecutorService backgroundExecutor,
                           Consumer<String> statusUpdater) {
        super(client, clueScrollPlugin, clueSolverPlugin);
        this.clue = clue;
        this.eventBus = eventBus;
        this.backgroundExecutor = backgroundExecutor;
        this.statusUpdater = statusUpdater;
    }

    @Override
    protected boolean executeTask() throws Exception {
        setStatus(STATUS_WAITING_FOR_DEVICE);
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
        if (!ClueSolverPlugin.isSolverRunning) {
            completeTask(false);
            return;
        }

        if (!clue.equals(clueScrollPlugin.getClue())) {
            completeTask(dug);
            return;
        }

        if (dug) {
            if (++ticksSinceDig < DIG_RESULT_TICKS) {
                return;
            }
            dug = false;
        }

        if (digLocation == null) {
            digLocation = clue.getLocation(clueScrollPlugin);
            if (digLocation == null) {
                return;
            }
            log.info("Hot Cold dig spot found: {}", digLocation);
        }

        if (!Rs2Inventory.contains("Spade")) {
            if (!spadeWarningLogged) {
                log.warn("Spade not found in inventory.");
                spadeWarningLogged = true;
            }
            setStatus(STATUS_NO_SPADE);
            return;
        }

        WorldPoint playerLocation = getPlayerLocationSafe();
        if (playerLocation == null) {
            return;
        }

        if (!digLocation.equals(playerLocation)) {
            setStatus(STATUS_WALKING);
            if (!Rs2Walker.walkTo(digLocation, 0)) {
                log.error("Failed to walk to Hot Cold dig spot: {}", digLocation);
                completeTask(false);
                return;
            }
        }

        if (Rs2Inventory.interact(ItemID.SPADE, "Dig")) {
            dug = true;
            ticksSinceDig = 0;
            setStatus(STATUS_DUG);
        }
    }

    private void setStatus(String status) {
        if (!Objects.equals(status, lastStatus)) {
            lastStatus = status;
            statusUpdater.accept(status);
        }
    }

    @Override
    protected void completeTask(boolean success) {
        super.completeTask(success);
        eventBus.unregister(this);
        log.info("Hot Cold clue task completed with status: {}", success ? "Success" : "Failure");
    }
}
