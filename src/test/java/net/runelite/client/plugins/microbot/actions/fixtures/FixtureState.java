package net.runelite.client.plugins.microbot.actions.fixtures;

import net.runelite.client.plugins.microbot.actions.ActionState;
import net.runelite.client.plugins.microbot.actions.ScriptState;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal {@link ScriptState} used by the framework tests: it just owns the per-tick list the
 * pipeline records into, plus a live log of the order in which actions actually executed (so tests
 * can assert ordering independently of the recorded state).
 */
public class FixtureState implements ScriptState {

    private final List<ActionState> states = new ArrayList<>();
    private final List<String> executionOrder = new ArrayList<>();

    @Override
    public List<ActionState> actionStates() {
        return states;
    }

    /** Called by fixture actions from their {@code execute} so tests can see execution order. */
    public void markExecuted(String key) {
        executionOrder.add(key);
    }

    public List<String> executionOrder() {
        return executionOrder;
    }
}
