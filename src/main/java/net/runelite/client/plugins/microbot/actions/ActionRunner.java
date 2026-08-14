package net.runelite.client.plugins.microbot.actions;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        rejectDuplicateKeys(this.actions);
    }

    /**
     * {@link Action#key()} is the identifier a tick's results are recorded and looked up under, so two
     * actions sharing a key would silently shadow each other in the state. The interface documents keys
     * as unique but cannot enforce it; we fail fast here (deterministically, listing the offenders)
     * rather than let one action's result masquerade as another's at runtime.
     */
    private static void rejectDuplicateKeys(List<? extends Action<?>> actions) {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = actions.stream()
                .map(Action::key)
                .filter(key -> !seen.add(key))
                .distinct()
                .collect(Collectors.toList());
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate action key(s) not allowed: " + duplicates);
        }
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
