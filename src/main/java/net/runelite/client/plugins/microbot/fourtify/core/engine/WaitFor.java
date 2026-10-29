package net.runelite.client.plugins.microbot.fourtify.core.engine;

import lombok.Value;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * The effect an action should have, such as "the logs are gone" or "the player starts chopping", and how
 * many ticks it may take to show. The {@link TaskList} compares the snapshot from before the action with
 * each later one. The wait ticks count from the first snapshot after the action returned, because some
 * actions block for many ticks (dropping a full inventory, walking to a bank). If the effect has not shown
 * by then, that counts as a failure of the task, and the task is tried again.
 * <p>
 * An action can also name what shows the game refused it ({@link #refusedBy}), such as a dialogue after a click.
 * That is a failure at once, and it is counted across the higher-priority tasks that cut in to answer it.
 * <p>
 * A task that judges a miss itself, such as per work spot, can take it over with {@link #whenMissed}: the miss is then
 * not counted as one of the failures that skip the task.
 */
@Value
public class WaitFor {
    String what;
    int waitTicks;
    BiPredicate<GameState, GameState> landed;
    boolean stopOnTimeout;
    /** Gives the reason when a snapshot shows the action was refused, else null; null for most actions. */
    BiFunction<GameState, GameState, String> refusal;
    /** Runs when the effect did not show in time and says how the task dealt with it, or null to leave it a failure; null for most actions. */
    Supplier<String> missed;

    private WaitFor(String what, int waitTicks, BiPredicate<GameState, GameState> landed, boolean stopOnTimeout,
                    BiFunction<GameState, GameState, String> refusal, Supplier<String> missed) {
        this.what = what;
        this.waitTicks = waitTicks;
        this.landed = landed;
        this.stopOnTimeout = stopOnTimeout;
        this.refusal = refusal;
        this.missed = missed;
    }

    public WaitFor(String what, int waitTicks, BiPredicate<GameState, GameState> landed, boolean stopOnTimeout) {
        this(what, waitTicks, landed, stopOnTimeout, null, null);
    }

    public WaitFor(String what, int waitTicks, BiPredicate<GameState, GameState> landed) {
        this(what, waitTicks, landed, false);
    }

    /** An uncertain transaction must be inspected, rather than sending the action again. */
    public static WaitFor once(String what, int ticks, BiPredicate<GameState, GameState> landed) {
        return new WaitFor(what, ticks, landed, true);
    }

    public boolean landed(GameState before, GameState now) {
        return landed.test(before, now);
    }

    /** The same wait, also failing at once with the reason {@code refusal} gives (checked after the effect has not landed). */
    public WaitFor refusedBy(BiFunction<GameState, GameState, String> refusal) {
        return new WaitFor(what, waitTicks, landed, stopOnTimeout, refusal, missed);
    }

    /**
     * The same wait, but when the effect does not show in time {@code handler} runs instead of a failure being
     * counted. It returns a note for the run log (such as "miss 2 of 3"), or null to leave the miss a failure.
     */
    public WaitFor whenMissed(Supplier<String> handler) {
        return new WaitFor(what, waitTicks, landed, stopOnTimeout, refusal, handler);
    }

    /** Called once when the effect did not show in time: the task's note on how it dealt with the miss, or null for a failure. */
    public String missed() {
        return missed == null ? null : missed.get();
    }

    /** Why the game refused the action, or null. */
    public String refused(GameState before, GameState now) {
        return refusal == null ? null : refusal.apply(before, now);
    }
}
