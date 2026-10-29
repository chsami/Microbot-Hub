package net.runelite.client.plugins.microbot.fourtify.core.engine;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Tasks in priority order: the first one that can run, runs. Put urgent jobs (eat, escape) first.
 * <p>
 * A TaskList is itself a task, so lists nest ("Bandos &gt; Eat"). Give one a finish line with
 * {@link #doneWhen(Predicate)} and it becomes a goal, such as "Woodcutting 60".
 * <p>
 * A task that fails {@value #MAX_FAILURES} times while it stays picked is skipped for the rest of the
 * session. Running past its {@link Task#maxTicks()} counts as a failure, and so does a check (isDone, a
 * requirement or shouldRun) that throws. The count resets once the task stops being picked, because that
 * means it finished its job, or once its checks work again.
 * Failures are handled here and not passed up, so a parent list never skips a whole child list
 * because one of its tasks failed. A refusal ({@link WaitFor#refusedBy}) counts as a failure too, but its count
 * is kept while other tasks cut in and ends when an action of the task lands.
 * <p>
 * When a task's step returns a {@link WaitFor}, the list waits for that effect: the task is not run again
 * while the effect is pending, and the effect not showing within its wait ticks counts as a failure. A
 * higher-priority task can still cut in; the pending effect is then dropped, because the task that wanted it
 * is no longer the one picked.
 * <p>
 * A task can take a missed effect over ({@link WaitFor#whenMissed}), when what a miss means is the task's to judge,
 * such as a miss at one work spot among several: the miss is then not counted as a failure here.
 */
@Slf4j
public class TaskList implements Task {
    static final int MAX_FAILURES = 3;

    private final String name;
    private final List<Task> tasks;
    private final Map<Task, Integer> failures = new HashMap<>();
    /**
     * Refusals in a row, kept apart from {@link #failures}: the task that answers a refusal (a dialogue to click
     * through) cuts in above the refused one, which would otherwise forget its count each time. Cleared when
     * an action of the task lands.
     */
    private final Map<Task, Integer> refusals = new HashMap<>();
    private final Map<Task, String> skipped = new LinkedHashMap<>();
    private Predicate<GameState> doneWhen = state -> false;
    private Predicate<GameState> gate = state -> true;
    private final List<Requirement> requirements = new ArrayList<>();
    private Task active;
    private int activeTicks;
    private Pending pending;
    private String terminalFailure;
    private Consumer<String> events = event -> { };

    /** An action's expected effect, the snapshot from just before the action, and the first tick seen after it. */
    @Data
    private static class Pending {
        final Task task;
        final WaitFor waitFor;
        final GameState before;
        int firstTickAfter = -1;
    }

    public TaskList(String name, Task... tasks) {
        this.name = name;
        this.tasks = Arrays.asList(tasks);
    }

    /**
     * Receives one line per checked effect, such as "ChopTree: start chopping, after 2 ticks", for the whole
     * tree of lists. The runner records them, so live runs show how long each kind of action takes.
     */
    public void onEvent(Consumer<String> events) {
        this.events = events;
        for (Task task : tasks) {
            if (task instanceof TaskList) {
                ((TaskList) task).onEvent(events);
            }
        }
    }

    /** Turns this list into a goal: once the condition holds it is done and never picked again. */
    public TaskList doneWhen(Predicate<GameState> condition) {
        this.doneWhen = condition;
        return this;
    }

    /** Must all be met before this list is picked, such as an activity's level or tools. */
    public TaskList requires(Requirement... more) {
        requirements.addAll(Arrays.asList(more));
        return this;
    }

    @Override
    public List<Requirement> requirements() {
        return requirements;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean isDone(GameState state) {
        return terminalFailure() == null && !hasPendingTransaction() && doneWhen.test(state);
    }

    /** Bank and inventory updates can arrive separately; a goal cannot overtake their acknowledgment. */
    public boolean hasPendingTransaction() {
        if (pending != null && pending.getWaitFor().isStopOnTimeout()) { return true; }
        for (Task task : tasks) {
            if (task instanceof TaskList && ((TaskList) task).hasPendingTransaction()) { return true; }
        }
        return false;
    }

    @Override
    public boolean shouldRun(GameState state) {
        return hasPendingTransaction() || gate.test(state) && pick(state, false) != null;
    }

    /** This list, picked only while {@code gate} holds; it stays a list, so its events and pending effects still work. */
    @Override
    public TaskList when(Predicate<GameState> gate) {
        this.gate = this.gate.and(gate);
        return this;
    }

    @Override
    public boolean canBreak() {
        return active == null || active.canBreak();
    }

    /**
     * Runs one step of the first runnable task, unless that task is still waiting for the effect of its last
     * action. Does nothing, and becomes idle, when none can run. Always returns null: a list's tasks check
     * their own effects.
     */
    @Override
    public WaitFor run(GameState state, Actions act) {
        if (terminalFailure() != null) {
            return null;
        }
        try {
            settle(state);
        } catch (Exception e) {
            Task task = pending.getTask();
            boolean once = pending.getWaitFor().isStopOnTimeout();
            pending = null;
            if (once) {
                terminalFailure = task.name() + ": transaction effect check failed, " + reason(e);
            } else {
                fail(task, "effect check failed, " + reason(e), state);
            }
        }
        if (terminalFailure() != null) {
            return null;
        }
        // A transaction can remove inventory before the offer update arrives. Hold its acknowledgment
        // even when its task no longer wants to run; another action must not overtake it.
        if (pending != null && pending.getWaitFor().isStopOnTimeout()) {
            return null;
        }
        if (isDone(state)) {
            active = null;
            return null;
        }
        Task next = pick(state, true);
        if (next != active) {
            // The previous task stopped being picked, so it finished its job: forget its failures.
            failures.remove(active);
            if (active instanceof TaskList) {
                // A list that is not picked does not run, so its pending effect would only go stale.
                ((TaskList) active).dropPending();
            }
            active = next;
            activeTicks = 0;
        }
        if (pending != null && pending.getTask() != next) {
            // Not a failure: the task that wanted the effect is no longer picked. Say so, so a run log shows
            // why an action has no timing.
            events.accept(pending.getTask().name() + ": " + pending.getWaitFor().getWhat() + ", not checked, "
                    + (next == null ? "nothing" : next.name()) + " took over");
            pending = null;
        }
        if (next == null) {
            return null;
        }
        try {
            if (next.maxTicks() > 0 && ++activeTicks > next.maxTicks()) {
                activeTicks = 0;
                pending = null;
                throw new IllegalStateException("timed out after " + next.maxTicks() + " ticks");
            }
            if (pending != null) {
                return null;
            }
            WaitFor waitFor = next.run(state, act);
            if (waitFor != null) {
                pending = new Pending(next, waitFor, state);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            fail(next, reason(e), state);
        }
        return null;
    }

    /** Checks the pending effect against this tick's state: it showed, it is overdue, or it may still come. */
    private void settle(GameState state) {
        if (pending == null) {
            return;
        }
        WaitFor waitFor = pending.getWaitFor();
        if (pending.getFirstTickAfter() < 0) {
            pending.setFirstTickAfter(state.getTick());
        }
        String refused;
        if (waitFor.landed(pending.getBefore(), state)) {
            int took = state.getTick() - pending.getBefore().getTick();
            events.accept(pending.getTask().name() + ": " + waitFor.getWhat() + ", after " + took + " ticks");
            refusals.remove(pending.getTask());
            pending = null;
        } else if ((refused = waitFor.refused(pending.getBefore(), state)) != null) {
            Task task = pending.getTask();
            pending = null;
            fail(task, refused, state, refusals);
        } else if (state.getTick() - pending.getFirstTickAfter() >= waitFor.getWaitTicks()) {
            Task task = pending.getTask();
            pending = null;
            if (waitFor.isStopOnTimeout()) {
                terminalFailure = task.name() + ": unconfirmed " + waitFor.getWhat();
                events.accept(terminalFailure);
                return;
            }
            String reason = "no sign of " + waitFor.getWhat() + " within " + waitFor.getWaitTicks() + " ticks";
            String note = waitFor.missed();
            if (note != null) {
                // The task judges this miss itself: it is not one of the failures that skip it.
                events.accept(task.name() + ": " + reason + ", " + note);
                log.info("[{}] {}: {}, {}{}", name, task.name(), reason, note, context(task, state));
            } else {
                fail(task, reason, state);
            }
        }
    }

    /**
     * Forgets pending effects here and in nested lists, such as after a pause in which the player may have
     * done anything; checking them against the game afterwards would judge actions nobody is waiting for.
     */
    public void dropPending() {
        if (pending != null && pending.getWaitFor().isStopOnTimeout()) {
            // Keep the transaction across a manual pause, but do not charge paused ticks to its deadline.
            pending.setFirstTickAfter(-1);
        } else {
            pending = null;
        }
        for (Task task : tasks) {
            if (task instanceof TaskList) {
                ((TaskList) task).dropPending();
            }
        }
    }

    /** The effect this list is waiting for, such as "ChopTree: start chopping", or null. */
    public String waitingFor() {
        if (pending != null) {
            return pending.getTask().name() + ": " + pending.getWaitFor().getWhat();
        }
        return active instanceof TaskList ? ((TaskList) active).waitingFor() : null;
    }

    /** True when this list stopped running the task for the rest of the session, after repeated failures. */
    public boolean skips(Task task) {
        return skipped.containsKey(task);
    }

    /** True when this list or a list inside it stopped running a task for the rest of the session, after repeated failures. */
    public boolean hasSkipped() {
        if (!skipped.isEmpty()) {
            return true;
        }
        for (Task task : tasks) {
            if (task instanceof TaskList && ((TaskList) task).hasSkipped()) {
                return true;
            }
        }
        return false;
    }

    /** True when the last {@link #run()} found nothing to do. */
    public boolean isIdle() {
        return active == null;
    }

    public String terminalFailure() {
        if (terminalFailure != null) { return terminalFailure; }
        for (Task task : tasks) {
            if (task instanceof TaskList) {
                String failure = ((TaskList) task).terminalFailure();
                if (failure != null) { return failure; }
            }
        }
        return null;
    }

    /** Where the last step ran, such as "Combat &gt; Eat". */
    public String path() {
        if (active == null) {
            return name;
        }
        return name + " > " + (active instanceof TaskList ? ((TaskList) active).path() : active.name());
    }

    /** Why tasks cannot run: unmet requirements and tasks skipped after repeated failures. */
    public List<String> blockers(GameState state) {
        List<String> out = new ArrayList<>();
        skipped.forEach((task, reason) -> out.add(task.name() + ": skipped, " + reason));
        for (Task task : tasks) {
            if (skipped.containsKey(task) || task.isDone(state)) {
                continue;
            }
            try {
                for (Requirement requirement : task.requirements()) {
                    if (!requirement.isMet(state)) {
                        out.add(task.name() + ": needs " + requirement.text(state));
                    }
                }
            } catch (Exception e) {
                out.add(task.name() + ": check failed, " + reason(e));
            }
            if (task instanceof TaskList) {
                for (String blocker : ((TaskList) task).blockers(state)) {
                    out.add(task.name() + " > " + blocker);
                }
            }
        }
        return out;
    }

    /**
     * Why each task before {@code upTo} was not picked: "yes", "no" (shouldRun false), "done",
     * "skipped" or "needs ...". Pass null to explain every task, such as when nothing ran.
     */
    public String explain(Task upTo, GameState state) {
        List<String> out = new ArrayList<>();
        for (Task task : tasks) {
            if (task == upTo) {
                break;
            }
            out.add(task.name() + ": " + state(task, state));
        }
        return String.join(", ", out);
    }

    private String state(Task task, GameState state) {
        if (skipped.containsKey(task)) {
            return "skipped";
        }
        try {
            if (task.isDone(state)) {
                return "done";
            }
            for (Requirement requirement : task.requirements()) {
                if (!requirement.isMet(state)) {
                    return "needs " + requirement.text(state);
                }
            }
            return task.shouldRun(state) ? "yes" : "no";
        } catch (Exception e) {
            return "check failed, " + reason(e);
        }
    }

    /**
     * The first task that is not skipped or done, has its requirements met and wants to run.
     * A check that throws (isDone, a requirement or shouldRun) counts as a failure of that task when
     * {@code record} is true, which run() uses; shouldRun() only looks, so a check is counted once per tick.
     */
    private Task pick(GameState state, boolean record) {
        for (Task task : tasks) {
            if (task instanceof TaskList && ((TaskList) task).hasPendingTransaction()) { return task; }
        }
        for (Task task : tasks) {
            if (skipped.containsKey(task)) {
                continue;
            }
            boolean wants;
            try {
                wants = !task.isDone(state) && requirementsMet(task, state) && task.shouldRun(state);
            } catch (Exception e) {
                if (record) {
                    fail(task, "check failed, " + reason(e), state);
                }
                continue;
            }
            if (record && task != active) {
                // Its checks work again: forget failures from earlier checks.
                failures.remove(task);
            }
            if (wants) {
                return task;
            }
        }
        return null;
    }

    private static String reason(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private static boolean requirementsMet(Task task, GameState state) {
        for (Requirement requirement : task.requirements()) {
            if (!requirement.isMet(state)) {
                return false;
            }
        }
        return true;
    }

    private void fail(Task task, String reason, GameState state) {
        fail(task, reason, state, failures);
    }

    private void fail(Task task, String reason, GameState state, Map<Task, Integer> counts) {
        int count = counts.merge(task, 1, Integer::sum);
        String context = context(task, state);
        if (count >= MAX_FAILURES) {
            failures.remove(task);
            refusals.remove(task);
            skipped.put(task, reason);
            events.accept(task.name() + " skipped after " + count + " failures: " + reason);
            log.error("[{}] Skipping {} for this session after {} failures: {}{}", name, task.name(), count, reason, context);
        } else {
            events.accept(task.name() + " failed (" + count + "/" + MAX_FAILURES + "): " + reason);
            log.info("[{}] {} failed ({}/{}): {}{}", name, task.name(), count, MAX_FAILURES, reason, context);
        }
    }

    /** What the tasks above the failed one said, plus game state, so a log line explains itself. */
    private String context(Task task, GameState state) {
        String above = explain(task, state);
        return (above.isEmpty() ? "" : " | above: " + above) + " | " + state.summary();
    }
}
