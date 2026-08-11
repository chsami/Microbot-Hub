package net.runelite.client.plugins.microbot.zulrahslayer.framework;

import java.util.List;

/**
 * Base state passed through an {@link Action} pipeline. It owns the per-tick list of
 * {@link ActionState}s and provides the common record/query logic as default methods, so a concrete
 * state object only has to expose its backing list (and whatever script-specific context it needs).
 */
public interface ScriptState {

    List<ActionState> actionStates();

    default void record(String key, boolean executed, Object result) {
        actionStates().add(new ActionState(key, executed, result));
    }

    default boolean executed(String key) {
        return actionStates().stream().anyMatch(a -> a.getKey().equals(key) && a.isExecuted());
    }

    default Object result(String key) {
        return actionStates().stream()
                .filter(a -> a.getKey().equals(key))
                .map(ActionState::getResult)
                .findFirst()
                .orElse(null);
    }
}
