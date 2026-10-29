package net.runelite.client.plugins.microbot.fourtify.core.engine;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.fourtify.core.client.TaskScript;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * The per-tick loop with no client in it: given this tick's {@link GameState} it runs one step of the
 * task list and says whether the run is over. {@link TaskScript} drives it from the live game; tests drive
 * it from hand-built or recorded ticks, so they exercise exactly the code that runs live.
 * <p>
 * The run ends when the root list is done, when a {@link StopCondition} holds (such as the player dying), when nothing
 * has been able to run for {@value #IDLE_TICKS_BEFORE_STOP} ticks in a row, or when the {@link StuckTimer} sees
 * no progress.
 */
@Slf4j
public class Runner {
    static final int IDLE_TICKS_BEFORE_STOP = 10;
    static final int TRACE_SIZE = 50;

    private final TaskList root;
    private final Actions act;
    private final StuckTimer stuckTimer;
    private final List<StopCondition> stops;
    private final Deque<String> trace = new ArrayDeque<>();
    private final List<String> events = new ArrayList<>();
    private int idleTicks;
    private String lastPath;

    public Runner(TaskList root, Actions act, StuckTimer stuckTimer) {
        this(root, act, stuckTimer, Collections.emptyList());
    }

    public Runner(TaskList root, Actions act, StuckTimer stuckTimer, List<StopCondition> stops) {
        this.root = root;
        this.act = act;
        this.stuckTimer = stuckTimer;
        this.stops = stops;
        root.onEvent(events::add);
    }

    /** One tick. The record's {@link TickEntry#getStop()} says why the run is over, or is null to keep going. */
    public TickEntry tick(GameState state) {
        events.clear();
        String stop;
        try {
            stop = step(state);
        } catch (RuntimeException e) {
            // Only a goal or stuck timer check can get here (task failures are handled in the lists); stop and say why.
            log.error("[{}] Check failed | {}", root.name(), state.summary(), e);
            stop = "Check failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        return new TickEntry(state, root.path(), root.waitingFor(), new ArrayList<>(events), stop);
    }

    /**
     * The script skipped game ticks (paused, a pop-up handled, the player took over). Whatever happened then
     * is neither a missed effect nor a lack of progress, so pending effects are dropped and the stuck timer
     * starts counting again.
     */
    public void interrupted() {
        root.dropPending();
        stuckTimer.reset();
    }

    private String step(GameState state) {
        if (root.isDone(state)) {
            return "Finished " + root.name();
        }
        for (StopCondition stop : stops) {
            if (stop.holds(state)) {
                log.error("[{}] Stopped: {} | {}", root.name(), stop.reason(), state.summary());
                return "Stopped: " + stop.reason();
            }
        }
        String stuck = stuckTimer.check(state);
        if (stuck != null) {
            log.error("[{}] Stuck at {}: {} | {}", root.name(), root.path(), stuck, state.summary());
            return "Stuck at " + root.path() + ": " + stuck;
        }
        root.run(state, act);
        if (root.terminalFailure() != null) {
            return "Stopped: " + root.terminalFailure();
        }
        if (root.isIdle()) {
            if (++idleTicks >= IDLE_TICKS_BEFORE_STOP) {
                List<String> blockers = root.blockers(state);
                log.info("[{}] Tasks: {} | {}", root.name(), root.explain(null, state), state.summary());
                return "Nothing to do in " + root.name() + (blockers.isEmpty() ? "" : ": " + String.join("; ", blockers));
            }
        } else {
            idleTicks = 0;
        }
        String path = root.path();
        if (!path.equals(lastPath)) {
            log.info("[{}] {}", root.name(), path);
            trace.addLast("tick " + state.getTick() + " " + path);
            if (trace.size() > TRACE_SIZE) {
                trace.removeFirst();
            }
            lastPath = path;
        }
        return null;
    }

    public TaskList root() {
        return root;
    }

    /** False while the current task says a break would be unsafe. */
    public boolean canBreak() {
        return root.canBreak();
    }

    /** Where this tick's step ran, such as "Woodcutting > ChopTree". */
    public String path() {
        return root.path();
    }

    /** The last {@value #TRACE_SIZE} changes of task path, oldest first. */
    public List<String> trace() {
        return new ArrayList<>(trace);
    }
}
