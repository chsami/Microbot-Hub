package net.runelite.client.plugins.custom.actions;

import java.util.List;

/**
 * Base state passed through an {@link Action} pipeline. It owns the per-tick list of
 * {@link ActionState}s and provides the common record/query logic as default methods, so a concrete
 * state object only has to expose its backing list (and whatever script-specific context it needs).
 */
public interface ScriptState {

    /** The mutable backing list of this tick's action results. */
    List<ActionState> actionStates();

    /** Records an action's outcome. Called by the runner after each action, executed or not. */
    default void record(String key, boolean executed, Object result) {
        actionStates().add(new ActionState(key, executed, result));
    }

    /** True if an earlier action this tick with the given key actually executed. */
    default boolean executed(String key) {
        return actionStates().stream().anyMatch(a -> a.getKey().equals(key) && a.isExecuted());
    }

    /** The value an earlier action returned this tick, or null if it didn't run / returned null. */
    default Object result(String key) {
        return actionStates().stream()
                .filter(a -> a.getKey().equals(key))
                .map(ActionState::getResult)
                .findFirst()
                .orElse(null);
    }
}
