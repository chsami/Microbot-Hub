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
    public static final String STATUS_WAITING_FOR_DEVICE = "Hot Cold: use Strange device until it is visibly shaking";
    public static final String STATUS_WALKING = "Hot Cold: walking to dig spot";
    public static final String STATUS_NO_SPADE = "Hot Cold: get a Spade to dig";
    public static final String STATUS_DUG = "Hot Cold: dug, checking result";
    public static final String STATUS_DUG_AGAIN = "Hot Cold: digging again";
    public static final String STATUS_UNDER_ATTACK = "Hot Cold: kill the attacker to dig again";
    public static final String STATUS_CANNOT_REACH = "Hot Cold: no path, walk closer and stand still";
    public static final String STATUS_STOPPED_AFTER_ERROR = "Hot Cold: stopped after an error, restart solver";
    public static final String START_MESSAGE = "Hot Cold clue: the solver does not search for you. Walk to the area in the clue hint and use your Strange device. When it says the device is visibly shaking, the solver digs there for you.";
    public static final String NO_SPADE_MESSAGE = "Hot Cold clue: you have no Spade. Bring one before the device shakes, or the solver cannot dig.";
    public static final String CANNOT_REACH_MESSAGE = "Hot Cold clue: can't find a path to the dig spot (the spade marker on the ground). Walk closer to the spade marker and stand still for a moment; the solver will try again.";
    public static final String ERROR_MESSAGE = "Hot Cold clue: something went wrong, so the solver stopped this clue. Stop and start the Clue Solver to try again.";
    public static final int DIG_RESULT_TICKS = 5;
    public static final int RETRY_STILL_TICKS = 2;

    private final HotColdClue clue;
    private final EventBus eventBus;
    private final ExecutorService backgroundExecutor;
    private final Consumer<String> statusUpdater;
    private final AtomicBoolean completed = new AtomicBoolean();
    private volatile boolean failedWithError;
    private volatile Future<?> currentTask;
    private WorldPoint digLocation;
    private WorldPoint unreachableSpot;
    private WorldPoint unreachableFrom;
    private WorldPoint lastPlayerLocation;
    private int stillTicks;
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
        if (!Rs2Inventory.contains("Spade")) {
            Microbot.log(NO_SPADE_MESSAGE);
        }
        return true;
    }

    public void stop() {
        completeTask(false);
        Future<?> tick = currentTask;
        if (tick != null) {
            tick.cancel(true);
        }
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
                if (!completed.get()) {
                    failedWithError = true;
                    Microbot.log(ERROR_MESSAGE);
                }
                completeTask(false);
            }
        });
    }

    public boolean hasFailedWithError() {
        return failedWithError;
    }

    private void processGameTick() {
        if (!ClueSolverPlugin.isSolverRunning) {
            completeTask(false);
            return;
        }

        if (!clue.equals(clueScrollPlugin.getClue())) {
            completeTask(dugBefore);
            return;
        }

        if (dug) {
            if (++ticksSinceDig < DIG_RESULT_TICKS) {
                return;
            }
            dug = false;
        }

        if (dugBefore && isNpcAttackingPlayer()) {
            setStatus(STATUS_UNDER_ATTACK);
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
            if (digLocation.equals(unreachableSpot) && !hasStoppedElsewhere(playerLocation)) {
                setStatus(STATUS_CANNOT_REACH);
                return;
            }
            setStatus(STATUS_WALKING);
            boolean walked = Rs2Walker.walkTo(digLocation, 0);
            if (completed.get() || !ClueSolverPlugin.isSolverRunning) {
                completeTask(false);
                return;
            }
            if (!walked) {
                WorldPoint stoppedAt = getPlayerLocationSafe();
                unreachableFrom = stoppedAt != null ? stoppedAt : playerLocation;
                lastPlayerLocation = unreachableFrom;
                stillTicks = 0;
                if (!digLocation.equals(unreachableSpot)) {
                    log.info("Hot Cold no path to dig spot {} from {}", digLocation, unreachableFrom);
                    Microbot.log(CANNOT_REACH_MESSAGE);
                }
                unreachableSpot = digLocation;
                setStatus(STATUS_CANNOT_REACH);
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

    private boolean hasStoppedElsewhere(WorldPoint playerLocation) {
        if (playerLocation.equals(lastPlayerLocation)) {
            stillTicks++;
        } else {
            lastPlayerLocation = playerLocation;
            stillTicks = 0;
        }
        return stillTicks >= RETRY_STILL_TICKS && !playerLocation.equals(unreachableFrom);
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
