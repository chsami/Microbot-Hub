package net.runelite.client.plugins.microbot.fourtify.core.activity;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.game.Surroundings;

/**
 * A quest's progress: a varp or varbit that the game moves from 0 to an end value as the quest goes on (Cook's
 * Assistant: varp 29, 0 not started, 1 started, 2 done). Steps are gated by the value with {@link #at}, so each tick
 * runs the steps of where the account is now, and a quest picked up halfway resumes from there. Within one value,
 * steps choose by state in priority order (the most specific first, such as "no egg yet: get one" above "hand
 * them in").
 */
public final class Quest {
    private final String name;
    private final boolean varbit;
    private final int id;
    private final int endValue;

    private Quest(String name, boolean varbit, int id, int endValue) {
        this.name = name;
        this.varbit = varbit;
        this.id = id;
        this.endValue = endValue;
    }

    public static Quest varp(String name, int varp, int endValue) {
        return new Quest(name, false, varp, endValue);
    }

    public static Quest varbit(String name, int varbit, int endValue) {
        return new Quest(name, true, varbit, endValue);
    }

    public int progress(GameState state) {
        return varbit ? state.varbit(id) : state.varp(id);
    }

    public boolean isDone(GameState state) {
        return progress(state) >= endValue;
    }

    /** These steps, run only while the progress is {@code value}. */
    public Task[] at(int value, Task... steps) {
        Task[] gated = new Task[steps.length];
        for (int i = 0; i < steps.length; i++) {
            gated[i] = steps[i].when(state -> progress(state) == value);
        }
        return gated;
    }

    /** The quest as an activity that reads its progress and is done at the end value; add its needs and steps. */
    public Activity activity() {
        Surroundings.SurroundingsBuilder progress = Surroundings.builder();
        if (varbit) {
            progress.varbit(id);
        } else {
            progress.varp(id);
        }
        return Activity.named(name).keepsInventory().surroundings(progress.build()).doneWhen(this::isDone);
    }
}
