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
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.cluesolver.ClueSolverPlugin;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public class HotColdClueTask extends ClueTask {
    public static final String STATUS_WAITING_FOR_DEVICE = "Hot Cold: use Strange device until spot is marked";
    public static final String STATUS_WALKING = "Hot Cold: walking to dig spot";
    public static final String STATUS_NO_SPADE = "Hot Cold: no Spade, add one to dig";
    public static final String STATUS_DUG = "Hot Cold: dug, waiting";
    public static final String STATUS_DUG_AGAIN = "Hot Cold: clue unchanged, dug again";
    public static final String STATUS_KILL_WIZARD = "Hot Cold: kill the wizard, then I dig again";
    public static final String STATUS_CANNOT_REACH = "Hot Cold: can't reach dig spot, walk closer";
    public static final String START_MESSAGE = "Hot Cold clue: use your Strange device until the dig spot is marked. The solver then walks there and digs.";
    public static final int DIG_RESULT_TICKS = 5;

    private final HotColdClue clue;
    private final EventBus eventBus;
    private final ExecutorService backgroundExecutor;
    private final Consumer<String> statusUpdater;
    private final AtomicBoolean completed = new AtomicBoolean();
    private Future<?> currentTask;
    private WorldPoint digLocation;
    private String lastStatus;
    private boolean spadeWarningLogged;
    private boolean dug;
    private boolean dugBefore;
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
        Microbot.log(START_MESSAGE);
        return true;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (completed.get() || (currentTask != null && !currentTask.isDone())) {
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

        if (dugBefore && isNpcAttackingPlayer()) {
            setStatus(STATUS_KILL_WIZARD);
            return;
        }

        WorldPoint spot = clue.getLocation(clueScrollPlugin);
        if (spot == null) {
            setStatus(STATUS_WAITING_FOR_DEVICE);
            return;
        }
        if (!spot.equals(digLocation)) {
            digLocation = spot;
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
            boolean walked = Rs2Walker.walkTo(digLocation, 0);
            if (!ClueSolverPlugin.isSolverRunning) {
                completeTask(false);
                return;
            }
            if (!walked) {
                setStatus(STATUS_CANNOT_REACH);
                Microbot.log(STATUS_CANNOT_REACH + " " + digLocation);
                completeTask(false);
                return;
            }
        }

        if (Rs2Inventory.interact(ItemID.SPADE, "Dig")) {
            dug = true;
            ticksSinceDig = 0;
            setStatus(dugBefore ? STATUS_DUG_AGAIN : STATUS_DUG);
            dugBefore = true;
        }
    }

    private boolean isNpcAttackingPlayer() {
        return Microbot.getRs2NpcCache().query()
                .where(npc -> !npc.isDead() && npc.isInteractingWithPlayer())
                .first() != null;
    }

    private void setStatus(String status) {
        if (!Objects.equals(status, lastStatus)) {
            lastStatus = status;
            statusUpdater.accept(status);
        }
    }

    @Override
    protected void completeTask(boolean success) {
        if (completed.getAndSet(true)) {
            return;
        }
        super.completeTask(success);
        eventBus.unregister(this);
        log.info("Hot Cold clue task completed with status: {}", success ? "Success" : "Failure");
    }
}
