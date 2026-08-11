package net.runelite.client.plugins.microbot.zulrahslayer.framework;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Runs an ordered {@link Action} pipeline over a {@link ScriptState}. Reusable across scripts: a
 * concrete script builds a runner with its own actions (bound to its own state type) and calls
 * {@link #run(ScriptState)} once per tick with a fresh state.
 *
 * <p>Actions are sorted by {@link Action#order()} once at construction. Each is asked
 * {@link Action#needsExecution}; if true, {@link Action#execute} runs and its return value is
 * recorded under the action's key. Both calls are isolated so one throwing action can't abort the
 * rest of the tick.
 *
 * @param <S> the state type shared by all actions in this runner
 */
@Slf4j
public class ActionRunner<S extends ScriptState> {

    private final List<Action<S>> actions;

    public ActionRunner(List<? extends Action<S>> actions) {
        this.actions = new ArrayList<>(actions);
        this.actions.sort(Comparator.comparingInt(Action::order));
    }

    public void run(S state) {
        for (Action<S> action : actions) {
            boolean needs;
            try {
                needs = action.needsExecution(state);
            } catch (Exception e) {
                log.error("[action:{}] needsExecution failed", action.key(), e);
                needs = false;
            }
            Object result = null;
            if (needs) {
                try {
                    result = action.execute(state);
                } catch (Exception e) {
                    log.error("[action:{}] execute failed", action.key(), e);
                }
            }
            state.record(action.key(), needs, result);
        }
    }
}
