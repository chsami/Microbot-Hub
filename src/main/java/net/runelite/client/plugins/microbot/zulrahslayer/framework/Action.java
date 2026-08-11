package net.runelite.client.plugins.microbot.zulrahslayer.framework;

/**
 * A single unit of per-tick behaviour, generic over the {@link ScriptState} it operates on so the
 * pipeline can be reused across scripts. Each tick the runner evaluates actions in ascending
 * {@link #order()}. For each, it calls {@link #needsExecution(ScriptState)}; if true it calls
 * {@link #execute(ScriptState)} and stores the returned value in the state under {@link #key()}.
 * Both methods receive the state of every action that ran before this one on this tick.
 *
 * @param <T> the concrete state type (e.g. {@code ZulrahState})
 */
public interface Action<T extends ScriptState> {

    /** Evaluation order; lower runs first. */
    int order();

    /** Unique identifier; used to look this action's result up in the state. */
    String key();

    /** Whether {@link #execute(ScriptState)} should run this tick. */
    boolean needsExecution(T state);

    /** Performs the action. The return value becomes this action's state entry for this tick. */
    Object execute(T state);
}
