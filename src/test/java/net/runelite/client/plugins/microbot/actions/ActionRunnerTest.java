package net.runelite.client.plugins.microbot.actions;

import net.runelite.client.plugins.microbot.actions.fixtures.FixtureState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ActionRunner}: actions run in ascending {@link Action#order()} regardless of the order they
 * were supplied, and one action throwing (in either {@code needsExecution} or {@code execute}) never
 * aborts the rest of the tick.
 */
class ActionRunnerTest {

    /** A configurable action so each test can declare order/needs/behaviour inline. */
    private static final class FixtureAction implements Action<FixtureState> {
        private final int order;
        private final String key;
        private final boolean needs;
        private final Runnable onNeeds;   // optional side effect / throw during needsExecution
        private final Runnable onExecute; // optional side effect / throw during execute

        FixtureAction(int order, String key, boolean needs, Runnable onNeeds, Runnable onExecute) {
            this.order = order;
            this.key = key;
            this.needs = needs;
            this.onNeeds = onNeeds;
            this.onExecute = onExecute;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public boolean needsExecution(FixtureState state) {
            if (onNeeds != null) {
                onNeeds.run();
            }
            return needs;
        }

        @Override
        public Object execute(FixtureState state) {
            if (onExecute != null) {
                onExecute.run();
            }
            state.markExecuted(key);
            return key;
        }
    }

    @Test
    void runsActionsInAscendingOrderRegardlessOfInputOrder() {
        FixtureState state = new FixtureState();
        // Supplied out of order on purpose: 30, 10, 20.
        List<Action<FixtureState>> actions = Arrays.asList(
                new FixtureAction(30, "c", true, null, null),
                new FixtureAction(10, "a", true, null, null),
                new FixtureAction(20, "b", true, null, null));

        new ActionRunner<>(actions).run(state);

        assertEquals(Arrays.asList("a", "b", "c"), state.executionOrder(),
                "actions must execute sorted by order(), not by supplied order");

        // The per-tick recorded state is appended in processing order too.
        List<String> recordedKeys = state.actionStates().stream()
                .map(ActionState::getKey)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList("a", "b", "c"), recordedKeys);
    }

    @Test
    void oneThrowingActionDoesNotAbortTheRestOfTheTick() {
        FixtureState state = new FixtureState();
        List<Action<FixtureState>> actions = Arrays.asList(
                new FixtureAction(10, "throw-needs", true,
                        () -> { throw new RuntimeException("boom in needsExecution"); }, null),
                new FixtureAction(20, "throw-execute", true,
                        null, () -> { throw new IllegalStateException("boom in execute"); }),
                new FixtureAction(30, "normal", true, null, null));

        new ActionRunner<>(actions).run(state);

        // needsExecution threw -> treated as "does not need to run" and never executed.
        assertFalse(state.executed("throw-needs"), "a throwing needsExecution should be treated as false");

        // needsExecution returned true, execute threw -> recorded as executed, but with a null result.
        assertTrue(state.executed("throw-execute"), "an action whose execute throws is still marked executed");
        assertNull(state.result("throw-execute"), "a thrown execute yields no result");

        // The action AFTER the throwers still ran to completion — the tick was not aborted.
        assertTrue(state.executed("normal"), "later actions must still run after an earlier one throws");
        assertEquals("normal", state.result("normal"));
        assertEquals(List.of("normal"), state.executionOrder(),
                "only the non-throwing action produced a completed execution");
    }
}
