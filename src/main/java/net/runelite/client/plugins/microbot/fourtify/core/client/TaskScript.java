package net.runelite.client.plugins.microbot.fourtify.core.client;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.fourtify.core.activity.ActivityQueue;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.GameReader;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Runner;
import net.runelite.client.plugins.microbot.fourtify.core.engine.StopCondition;
import net.runelite.client.plugins.microbot.fourtify.core.engine.StuckTimer;
import net.runelite.client.plugins.microbot.fourtify.core.engine.TaskList;
import net.runelite.client.plugins.microbot.fourtify.core.engine.TickEntry;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs a {@link Runner} against the live game: once per game tick, on this script's own thread (never the
 * client thread), it reads the game through a {@link GameReader} and hands the snapshot to the runner.
 * <p>
 * Each tick it first lets Microbot's own checks pause it (pop-ups, death, manual pause, human input), and it
 * holds the break-handler lock while the current task says a break is unsafe. All decisions live in
 * {@link Runner} and the tasks, so this class only connects them to the client.
 * <p>
 * Every tick goes to a {@link RunLog} (a JSON-lines file and the agent server's result store), and the run
 * ends with a verdict there, so a live run can be followed and judged without a screen. When it stops it also
 * logs a summary: run time, XP gained per skill and the last task path changes.
 */
@Slf4j
public class TaskScript extends Script {
    private static final long POLL_MS = 50;

    private Runner runner;
    private GameReader reader;
    private int lastTick = -1;
    private final BreakLock breakLock = new BreakLock();
    private GameState first;
    private GameState last;
    private Instant startedAt;
    private volatile RunLog runLog;
    /** A tick was skipped because Microbot paused the script; the next one tells the runner. */
    private boolean interrupted;
    private String stopReason;
    private boolean finished;

    /** Runs a queue of activities against the live game, reading what all of them need each tick. */
    public boolean run(ActivityQueue queue) {
        return run(queue.tasks(), new ClientReader(queue.surroundings()), new ClientActions(), queue.stuckTimer(), queue.stops());
    }

    public boolean run(TaskList root, GameReader reader, StuckTimer stuckTimer) {
        return run(root, reader, new ClientActions(), stuckTimer);
    }

    public boolean run(TaskList root, GameReader reader, Actions act, StuckTimer stuckTimer) {
        return run(root, reader, act, stuckTimer, Collections.emptyList());
    }

    public boolean run(TaskList root, GameReader reader, Actions act, StuckTimer stuckTimer, List<StopCondition> stops) {
        this.runner = new Runner(root, act, stuckTimer, stops);
        this.reader = reader;
        this.runLog = new RunLog(getClass().getName(), RunLog.defaultDir(), root.name());
        log.info("[{}] Run log: {}", root.name(), runLog.file());
        lastTick = -1;
        interrupted = false;
        first = null;
        last = null;
        stopReason = null;
        finished = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                tick();
            } catch (Exception e) {
                if (mainScheduledFuture.isCancelled()) {
                    // Stopped mid-tick: the stop interrupts a read waiting for the client thread. Not a failure.
                    return;
                }
                log.error("[{}] Tick failed | {}", root.name(), last == null ? "no state yet" : last.summary(), e);
            }
        }, 0, POLL_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    private void tick() {
        if (!Microbot.isLoggedIn()) {
            return;
        }
        int tickCount = Microbot.getClient().getTickCount();
        if (tickCount == lastTick) {
            return;
        }
        lastTick = tickCount;
        if (!super.run()) {
            interrupted = true;
            return;
        }
        if (interrupted) {
            runner.interrupted();
            interrupted = false;
        }
        GameState state = reader.read();
        if (first == null) {
            first = state;
            startedAt = Instant.now();
        }
        last = state;

        TickEntry record = runner.tick(state);
        RunLog out = runLog;
        if (out != null) {
            out.record(record);
        }
        if (record.getStop() != null) {
            finished = record.getStop().startsWith("Finished");
            stop(record.getStop());
            return;
        }
        breakLock.hold(!runner.canBreak());
        Microbot.status = runner.path();
    }

    private void stop(String reason) {
        stopReason = reason;
        Microbot.log(reason);
        Microbot.status = reason;
        shutdown();
    }

    @Override
    public void shutdown() {
        RunLog out = runLog;
        runLog = null;
        if (out != null) {
            out.verdict(stopReason != null ? stopReason : "Stopped before finishing", finished, first, last);
        }
        logSummary();
        breakLock.release();
        super.shutdown();
    }

    /** Once per run: how long it ran, XP gained per skill, and the last task path changes. */
    private void logSummary() {
        if (runner == null || first == null) {
            return;
        }
        List<String> gains = new ArrayList<>();
        for (Map.Entry<Skill, Integer> entry : last.getXp().entrySet()) {
            int before = first.xp(entry.getKey());
            int after = entry.getValue();
            if (after > before) {
                gains.add(String.format("%s +%,d xp (%d -> %d)", entry.getKey().getName(), after - before,
                        Experience.getLevelForXp(before), Experience.getLevelForXp(after)));
            }
        }
        Duration ran = Duration.between(startedAt, Instant.now());
        log.info("[{}] Stopped after {}m {}s. XP: {}. Last steps:\n  {}", runner.root().name(), ran.toMinutes(), ran.toSecondsPart(),
                gains.isEmpty() ? "none" : String.join(", ", gains), String.join("\n  ", runner.trace()));
        first = null;
    }
}
