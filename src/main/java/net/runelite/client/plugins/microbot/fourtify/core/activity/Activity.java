package net.runelite.client.plugins.microbot.fourtify.core.activity;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Requirement;
import net.runelite.client.plugins.microbot.fourtify.core.engine.StopCondition;
import net.runelite.client.plugins.microbot.fourtify.core.engine.StuckTimer;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.TaskList;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.game.Surroundings;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.bank.BankTrip;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.bank.UnneededItems;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.inventory.DropItems;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.gear.Equip;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.guards.ConfirmWarning;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.guards.ContinueDialogue;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.guards.DismissRandomEvent;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.guards.Eat;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.guards.EnableRun;
import net.runelite.client.plugins.microbot.fourtify.core.tasks.guards.WaitForCutscene;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * One thing to do, such as "chop oaks to Woodcutting 30": what it requires, what it needs carried, its work
 * tasks, and its goal. Every activity runs the same way: the standard guards first (continue a dialogue with
 * nothing to choose, wait out a cutscene, eat when low if it has food, dismiss a random event, turn run on when rested), then a bank trip when a need is
 * missing (or when its deposit condition says so, such as a full inventory of logs), then its work tasks in
 * priority order, until the goal holds. An activity can leave the guards out, for one that handles dialogues
 * or eating itself.
 * <p>
 * An activity also says what the snapshot must hold ({@link Surroundings}) and its {@link StuckTimer} limits, both from
 * its own game data, so a queue of several activities can run them in one script.
 */
public final class Activity {
    private final String name;
    private final List<Requirement> requirements = new ArrayList<>();
    private final List<NeededItem> needs = new ArrayList<>();
    private final List<Task> work = new ArrayList<>();
    private Predicate<GameState> goal = state -> false;
    private Surroundings surroundings = Surroundings.none();
    private int idleLimit;
    private int gainLimit;
    private Set<Integer> deposit = Collections.emptySet();
    private String depositLabel = "items";
    private Predicate<GameState> depositWhen = state -> false;
    private final List<Integer> food = new ArrayList<>();
    private int eatAtPercent = EAT_AT_PERCENT;
    private boolean guards = true;
    private boolean keepsInventory;
    private boolean managesBanking;
    private final Set<Integer> dropped = new HashSet<>();
    private final Set<Integer> keeps = new HashSet<>();
    private final List<StopCondition> stops = new ArrayList<>();
    private final List<String> expectedWarnings = new ArrayList<>();
    private UnneededItems unneeded = UnneededItems.none();
    private ToDoubleFunction<GameState> rate;

    /**
     * Eat at about half hitpoints: one bite of most free-to-play food (shrimps heal 3, swordfish 14)
     * then still leaves room before the next hit. Our choice, not a game value.
     */
    static final int EAT_AT_PERCENT = 50;
    /** Each bite rolls the next threshold up to this many points either side of the set one. Our choice. */
    static final int EAT_SPREAD = 10;
    /** Turn run on once energy is back to a level rolled from this range: enough for a stretch of running. Our choice. */
    static final int RUN_AT_ENERGY_LOW = 20;
    static final int RUN_AT_ENERGY_HIGH = 40;

    private Activity(String name) {
        this.name = name;
    }

    public static Activity named(String name) {
        return new Activity(name);
    }

    /** Must all hold for the activity to start, such as a level or a members world. */
    public Activity requires(Requirement... more) {
        requirements.addAll(Arrays.asList(more));
        return this;
    }

    /** Items to carry or wear; missing ones are fetched from the bank before the work starts. */
    public Activity needs(NeededItem... more) {
        needs.addAll(Arrays.asList(more));
        return this;
    }

    /** Banks these items by ID whenever {@code when} holds, such as a full inventory of logs. */
    public Activity banks(Set<Integer> itemIds, String label, Predicate<GameState> when) {
        this.deposit = itemIds;
        this.depositLabel = label;
        this.depositWhen = when;
        return this;
    }

    /** Drops these items by ID when the inventory is full, such as logs, as the first of the work. */
    public Activity drops(Set<Integer> itemIds, String label) {
        dropped.addAll(itemIds);
        return work(new DropItems(itemIds, label));
    }

    /** The work, in priority order: the first task that can run, runs. */
    public Activity work(Task... tasks) {
        work.addAll(Arrays.asList(tasks));
        return this;
    }

    /**
     * Food carried for this activity, best first, eaten at about {@code eatAtPercent} of the maximum hitpoints:
     * each bite rolls the next threshold up to {@value #EAT_SPREAD} points either side, so it must stay above the
     * queue's low-hitpoints stop.
     */
    public Activity food(int eatAtPercent, Integer... foodIds) {
        if (eatAtPercent - EAT_SPREAD <= ActivityQueue.STOP_AT_PERCENT || eatAtPercent + EAT_SPREAD > 100) {
            throw new IllegalArgumentException("eat at " + eatAtPercent + "%: the rolled threshold must stay above the "
                    + ActivityQueue.STOP_AT_PERCENT + "% stop and at most 100%");
        }
        this.eatAtPercent = eatAtPercent;
        food.addAll(Arrays.asList(foodIds));
        return this;
    }

    /**
     * Keeps whatever the player carries. By default an activity first banks everything it does not need: its needs,
     * its food, the items it banks or drops itself (its products, already carried) and the bonds stay. A quest keeps
     * its inventory, which may hold items for it.
     */
    public Activity keepsInventory() {
        this.keepsInventory = true;
        return this;
    }

    /**
     * Keeps these items by ID when it first banks what it carries but does not need, such as items the work
     * brings that must never go to the bank. They are not needs: nothing is fetched for them.
     */
    public Activity keeps(Set<Integer> itemIds) {
        keeps.addAll(itemIds);
        return this;
    }

    /** Runs the activity's own bank tasks, instead of the standard supply/deposit trip. */
    public Activity managesBanking() {
        this.managesBanking = true;
        return this;
    }

    /** Leaves out the standard guards, for an activity that handles dialogues, eating or run itself. */
    public Activity withoutGuards() {
        this.guards = false;
        return this;
    }

    /** Ends the whole run when one of these holds, such as a quest going wrong. */
    /**
     * Confirms a warning screen whose text contains {@code textPart}, such as the one before a dangerous area the
     * activity works in. A queue stops on any other warning; none confirms the Wilderness warning.
     */
    public Activity confirmsWarning(String textPart) {
        expectedWarnings.add(textPart);
        return this;
    }

    public List<String> expectedWarnings() {
        return expectedWarnings;
    }

    public Activity stopWhen(StopCondition... more) {
        stops.addAll(Arrays.asList(more));
        return this;
    }

    public Activity doneWhen(Predicate<GameState> goal) {
        this.goal = goal;
        return this;
    }

    /** Adds what the snapshot must read for this activity, such as its objects, NPCs or quest progress. */
    public Activity surroundings(Surroundings surroundings) {
        this.surroundings = this.surroundings.plus(surroundings);
        return this;
    }

    /** Longest normal stretch with no change at all, and with no gain, from the activity's game data. */
    public Activity stuckTimer(int idleLimit, int gainLimit) {
        this.idleLimit = idleLimit;
        this.gainLimit = gainLimit;
        return this;
    }

    public String name() {
        return name;
    }

    /** The surroundings to read, plus the bank counts of every item the needs may fetch. */
    public Surroundings surroundings() {
        Set<Integer> bankIds = new LinkedHashSet<>();
        for (NeededItem need : needs) {
            bankIds.addAll(need.ids());
        }
        return surroundings.plus(Surroundings.builder().bankItemIds(bankIds).build());
    }

    public List<Integer> food() {
        return food;
    }

    public List<StopCondition> stops() {
        return stops;
    }

    public int idleLimit() {
        return idleLimit;
    }

    public int gainLimit() {
        return gainLimit;
    }

    /**
     * What the activity is worth right now, in a unit all the activities of a queue share (such as experience per tick).
     * A queue whose activities have a rate runs the one with the highest rate that can start, at every tick; see
     * {@link ActivityQueue}. Without a rate an activity keeps its place in the queue's order.
     */
    public Activity rate(ToDoubleFunction<GameState> rate) {
        this.rate = rate;
        return this;
    }

    boolean hasRate() {
        return rate != null;
    }

    double rate(GameState state) {
        return rate == null ? 0 : rate.applyAsDouble(state);
    }

    /** Another activity has run: what this one banks as unneeded is checked again at its next bank visit. */
    void rearm() {
        unneeded.rearm();
    }

    /** The activity as one task list: the guards, the bank trip, putting on gear, then the work, done at the goal. */
    public TaskList tasks() {
        return tasks(() -> { });
    }

    /** As {@link #tasks()}; {@code onRun} is called each tick this activity's list runs (the queue picked it). */
    TaskList tasks(Runnable onRun) {
        List<Task> all = new ArrayList<>();
        if (!expectedWarnings.isEmpty()) {
            all.add(new ConfirmWarning(expectedWarnings));
        }
        if (guards) {
            Random random = new Random();
            all.add(new ContinueDialogue());
            all.add(new WaitForCutscene());
            if (!food.isEmpty()) {
                all.add(new Eat(food, eatAtPercent - EAT_SPREAD, eatAtPercent + EAT_SPREAD, random));
            }
            all.add(new DismissRandomEvent());
            all.add(new EnableRun(RUN_AT_ENERGY_LOW, RUN_AT_ENERGY_HIGH, random));
        }
        Set<Integer> kept = new HashSet<>(food);
        needs.forEach(need -> kept.addAll(need.ids()));
        kept.addAll(deposit);
        kept.addAll(dropped);
        kept.addAll(keeps);
        UnneededItems unneeded = keepsInventory ? UnneededItems.none() : new UnneededItems(kept);
        this.unneeded = unneeded;
        if (!managesBanking) {
            all.add(BankTrip.of("Bank", deposit, depositLabel, depositWhen, needs, unneeded));
        }
        if (needs.stream().anyMatch(NeededItem::mustBeWorn)) {
            all.add(new Equip(needs));
        }
        all.addAll(work);
        TaskList list = new TaskList(name, all.toArray(new Task[0])) {
            @Override
            public WaitFor run(GameState state, Actions act) {
                onRun.run();
                return super.run(state, act);
            }
        }.doneWhen(goal);
        list.requires(requirements.toArray(new Requirement[0]));
        for (NeededItem need : needs) {
            // Blocks an activity whose bank trip cannot meet a need, with a reason the run's verdict shows.
            list.requires(new Requirement(need + " (carried, worn or in the bank, with a free inventory slot)" + need.insteadText(),
                    state -> need.isMet(state) || need.insteadHolds(state)
                            || BankTrip.canMeet(need, BankTrip.depositable(deposit, unneeded, state), state)));
            if (need.isTwoHanded()) {
                list.requires(new Requirement("room to wield " + need + " (a free inventory slot while a weapon and a shield are worn)",
                        state -> need.isWorn(state) || Equip.canWield(need, state) || state.hasAny(BankTrip.depositable(deposit, unneeded, state))));
            }
        }
        return list;
    }
}
