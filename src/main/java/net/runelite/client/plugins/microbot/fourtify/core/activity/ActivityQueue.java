package net.runelite.client.plugins.microbot.fourtify.core.activity;

import net.runelite.client.plugins.microbot.fourtify.core.engine.StopCondition;
import net.runelite.client.plugins.microbot.fourtify.core.engine.StuckTimer;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.TaskList;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Requirement;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.game.Surroundings;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.guards.ConfirmWarning;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Activities in order, such as "chop to Woodcutting 30, then mine to Mining 30". Each tick the first activity
 * whose goal is not reached and whose requirements are met runs; one that cannot start (a level too low, a
 * missing tool) is passed over until it can. The plan is done when every activity is.
 * <p>
 * Activities that have a {@link Activity#rate} are not in an order of the queue's: they are alternatives, and each tick
 * the one with the highest rate that can start runs (a tie goes to the earlier one), and one that is running keeps its
 * place unless another is better by {@value #SWITCH_GAIN} times. The rates are asked at that tick, so the one that runs
 * changes when a level makes another better. An alternative is passed over while it cannot start (its
 * requirements, tools and bait among them) or has a task that failed too often, so when the best has no supplies the next
 * best runs. Only the requirements decide, never whether the best has something to do this very tick: a spot that has
 * just moved must not hand the turn to a worse method, which would bank the best one's tool and fetch its own.
 * <p>
 * The script reads one snapshot for all of them (every activity's {@link Surroundings}) and uses one {@link StuckTimer}
 * with the widest limits of the activities, so a slow activity is never stopped by a fast one's limits.
 * <p>
 * Every queue stops when the player dies, and when hitpoints are at {@value #STOP_AT_PERCENT}% or less with
 * none of the queue's food left, when the game says the bank is full, and when a warning screen none of its
 * activities expects opens, plus any stops its activities add.
 */
public final class ActivityQueue {
    private final String name;
    private final List<Activity> activities;

    private ActivityQueue(String name, List<Activity> activities) {
        this.name = name;
        this.activities = activities;
    }

    public static ActivityQueue of(String name, Activity... activities) {
        return new ActivityQueue(name, new ArrayList<>(Arrays.asList(activities)));
    }

    /** A queue of one activity, named after it. */
    public static ActivityQueue of(Activity activity) {
        return new ActivityQueue(activity.name(), Collections.singletonList(activity));
    }

    /**
     * One task list for the whole queue. Each activity's list is a child of it, even a queue of one: a list checks
     * only its children's requirements, so a lone activity would otherwise run with its level, tool and need
     * checks never looked at.
     * <p>
     * Whenever an activity runs, every other activity's unneeded items are checked again at its next visit
     * ({@link Activity#rearm()}): when the queue falls back to an activity that ran before, what the other
     * activity brought (its tool, bait, catch) is unneeded there and goes to the bank.
     */
    public TaskList tasks() {
        List<TaskList> lists = new ArrayList<>();
        int[] running = {-1};
        for (int i = 0; i < activities.size(); i++) {
            Activity activity = activities.get(i);
            int me = i;
            lists.add(activity.tasks(() -> {
                running[0] = me;
                activities.stream().filter(other -> other != activity).forEach(Activity::rearm);
            }));
        }
        TaskList queue = new TaskList(name, lists.toArray(new Task[0]))
                .doneWhen(state -> lists.stream().allMatch(list -> list.isDone(state)));
        if (activities.stream().anyMatch(Activity::hasRate)) {
            rank(queue, lists, running);
        }
        return queue;
    }

    /**
     * Moving to another alternative costs a bank trip and a walk: the running one's tool and catch go to the bank and the
     * new one's come out, which is a minute or so. An alternative that is better by less than this factor does not take
     * over from the one that ran last, so a method that wins a few levels by a few percent (small net over bait at Fishing
     * 15 to 19) does not send the player back and forth. Our choice. The rates are fixed at one snapshot, so two
     * alternatives never both rank above the other, and a switch to a better one is never undone at the same levels.
     */
    static final double SWITCH_GAIN = 1.1;

    /**
     * Lets an activity run only while no activity that ranks above it can start. One ranks above another by its rate (the
     * one that ran last counts {@value #SWITCH_GAIN} times its own), or by coming first at an equal rate, so two never rank
     * above each other. The rates are asked once per snapshot.
     */
    private void rank(TaskList queue, List<TaskList> lists, int[] running) {
        GameState[] seen = {null};
        double[][] rates = {null};
        for (int i = 0; i < lists.size(); i++) {
            int me = i;
            lists.get(i).when(state -> {
                if (seen[0] != state) {
                    rates[0] = activities.stream().mapToDouble(activity -> activity.rate(state)).toArray();
                    seen[0] = state;
                }
                double mine = rates[0][me] * (running[0] == me ? SWITCH_GAIN : 1);
                for (int j = 0; j < lists.size(); j++) {
                    double theirs = rates[0][j] * (running[0] == j ? SWITCH_GAIN : 1);
                    boolean above = theirs > mine || theirs == mine && j < me;
                    if (above && canStart(queue, lists.get(j), state)) {
                        return false;
                    }
                }
                return true;
            });
        }
    }

    /** The list's goal is not reached, its requirements are met and neither it nor the queue gave up on it. */
    private static boolean canStart(TaskList queue, TaskList list, GameState state) {
        if (queue.skips(list) || list.hasSkipped() || list.isDone(state)) {
            return false;
        }
        for (Requirement requirement : list.requirements()) {
            if (!requirement.isMet(state)) {
                return false;
            }
        }
        return true;
    }

    public Surroundings surroundings() {
        Surroundings surroundings = Surroundings.none();
        for (Activity activity : activities) {
            surroundings = surroundings.plus(activity.surroundings());
        }
        return surroundings;
    }

    /** Low enough that one more hit could kill, with no food to eat. Our choice, not a game value. */
    static final int STOP_AT_PERCENT = 20;

    public List<StopCondition> stops() {
        Set<Integer> food = new LinkedHashSet<>();
        List<StopCondition> stops = new ArrayList<>();
        stops.add(StopCondition.died());
        for (Activity activity : activities) {
            food.addAll(activity.food());
        }
        stops.add(StopCondition.lowHitpointsWithoutFood(STOP_AT_PERCENT, food));
        stops.add(StopCondition.bankFull());
        List<String> warnings = new ArrayList<>();
        for (Activity activity : activities) {
            warnings.addAll(activity.expectedWarnings());
        }
        stops.add(new StopCondition("a warning screen no activity expects is open",
                state -> state.getWarning() != null && !ConfirmWarning.expects(warnings, state.getWarning())));
        for (Activity activity : activities) {
            stops.addAll(activity.stops());
        }
        return stops;
    }

    public StuckTimer stuckTimer() {
        int idle = 0;
        int gain = 0;
        for (Activity activity : activities) {
            idle = Math.max(idle, activity.idleLimit());
            gain = Math.max(gain, activity.gainLimit());
        }
        return new StuckTimer(idle, gain);
    }

    public String name() {
        return name;
    }
}
