package net.runelite.client.plugins.microbot.actions;

import net.runelite.client.plugins.microbot.actions.fixtures.FixtureState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void laterActionsCanReadEarlierResultsFromTheSameState() {
        FixtureState state = new FixtureState();
        AtomicReference<Object> seenByLater = new AtomicReference<>("UNSET");

        // The earlier action (order 10) records "producer" as its result; the later action (order 20)
        // reads it back out of the shared state during its own execute.
        Action<FixtureState> earlier = new FixtureAction(10, "producer", true, null, null);
        Action<FixtureState> later = new FixtureAction(20, "consumer", true, null,
                () -> seenByLater.set(state.result("producer")));

        new ActionRunner<>(Arrays.asList(later, earlier)).run(state);

        assertEquals("producer", seenByLater.get(),
                "a later action must see the result an earlier action recorded this tick");
    }

    @Test
    void needsExecutionFalseRecordsASkipWithoutCallingExecute() {
        FixtureState state = new FixtureState();
        AtomicBoolean executeCalled = new AtomicBoolean(false);

        Action<FixtureState> skipped = new FixtureAction(10, "skipped", false, null,
                () -> executeCalled.set(true));
        Action<FixtureState> runs = new FixtureAction(20, "runs", true, null, null);

        new ActionRunner<>(Arrays.asList(skipped, runs)).run(state);

        assertFalse(executeCalled.get(), "execute must not run when needsExecution is false");
        assertFalse(state.executed("skipped"), "a skipped action is recorded as not executed");
        // It is still RECORDED (present in the tick's state), just with executed=false and no result.
        assertTrue(state.actionStates().stream().anyMatch(a -> a.getKey().equals("skipped")),
                "a skipped action must still be recorded for the tick");
        assertNull(state.result("skipped"));
        assertFalse(state.executionOrder().contains("skipped"));
        assertTrue(state.executed("runs"), "a needed action still runs in the same tick");
    }

    @Test
    void duplicateActionKeysAreRejectedDeterministically() {
        List<Action<FixtureState>> withDuplicate = Arrays.asList(
                new FixtureAction(10, "dup", true, null, null),
                new FixtureAction(20, "unique", true, null, null),
                new FixtureAction(30, "dup", true, null, null));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new ActionRunner<>(withDuplicate),
                "constructing a runner with duplicate action keys must fail fast");
        assertTrue(ex.getMessage().contains("dup"), "the offending key should be named: " + ex.getMessage());
    }
}
