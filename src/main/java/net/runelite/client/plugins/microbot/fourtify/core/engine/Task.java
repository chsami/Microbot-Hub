package net.runelite.client.plugins.microbot.fourtify.core.engine;

import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * One small job, such as "eat", "bank the logs" or "chop a tree".
 * <p>
 * Each game tick the runner reads the game once into a {@link GameState}, walks its {@link TaskList} and
 * calls {@link #run(GameState, Actions)} once on the first task that is not done, has its requirements met
 * and answers {@link #shouldRun(GameState)} with true. Tasks act only through {@link Actions} and say what
 * each action should change with a {@link WaitFor}, so a click that does nothing is noticed.
 * Tasks keep no progress of their own: they decide from the snapshot, so a restart, death or manual
 * takeover resumes from wherever the account actually is, and tests can hand them any state they like.
 */
public interface Task {
    /** Quick check with no side effects and no clicks: is this job needed right now? */
    boolean shouldRun(GameState state);

    /**
     * Do one short step and return. Keep it brief so higher-priority tasks, such as eating, can cut in next tick.
     * Return the effect the step's action should have, or null when it took none. While the effect is
     * pending this task is not run again; it counts as a failure if the effect does not show in time.
     * Throw to report a failure; the exception message becomes the reason.
     */
    WaitFor run(GameState state, Actions act) throws Exception;

    default String name() {
        return getClass().getSimpleName();
    }

    /** The finish line. A done task is never picked again. */
    default boolean isDone(GameState state) {
        return false;
    }

    /** False while a break or logout would be unsafe, such as mid-fight or mid-round. */
    default boolean canBreak() {
        return true;
    }

    /** Ticks this task may stay picked in a row before it counts as a failure. 0 means no limit. */
    default int maxTicks() {
        return 0;
    }

    /** All must be met before the task can be picked. */
    default List<Requirement> requirements() {
        return Collections.emptyList();
    }

    /**
     * This task, picked only while {@code gate} holds, such as one quest stage (a progress value) or one minigame
     * phase (in the lobby, in a round). Otherwise it answers as this task does.
     */
    default Task when(Predicate<GameState> gate) {
        Task task = this;
        return new Task() {
            @Override
            public boolean shouldRun(GameState state) {
                return gate.test(state) && task.shouldRun(state);
            }

            @Override
            public WaitFor run(GameState state, Actions act) throws Exception {
                return task.run(state, act);
            }

            @Override
            public String name() {
                return task.name();
            }

            @Override
            public boolean isDone(GameState state) {
                return task.isDone(state);
            }

            @Override
            public boolean canBreak() {
                return task.canBreak();
            }

            @Override
            public int maxTicks() {
                return task.maxTicks();
            }

            @Override
            public List<Requirement> requirements() {
                return task.requirements();
            }
        };
    }
}
