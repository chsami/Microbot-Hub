package net.runelite.client.plugins.microbot.fourtify.core.tasks.guards;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.StopCondition;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.List;
import java.util.Random;

/**
 * Eats when hitpoints are at or below a percentage of the maximum, the first of these foods carried, and
 * never with the bank open, which shows the inventory with deposit options instead of "Eat". Most
 * food delays the next bite by 3 ticks, so it worked when hitpoints rose or the food was
 * used up within {@value #WAIT_TICKS} ticks. The percentage is rolled anew in its range after each bite, so
 * the player does not always eat at the same hitpoints.
 */
public class Eat implements Task {
    static final int WAIT_TICKS = 3;

    private final List<Integer> foodIds;
    private final int lowPercent;
    private final int highPercent;
    private final Random random;
    private int percent;

    public Eat(List<Integer> foodIds, int lowPercent, int highPercent, Random random) {
        this.foodIds = foodIds;
        this.lowPercent = lowPercent;
        this.highPercent = highPercent;
        this.random = random;
        this.percent = roll();
    }

    private int roll() {
        return lowPercent + random.nextInt(highPercent - lowPercent + 1);
    }

    @Override
    public boolean shouldRun(GameState state) {
        return !state.isBankOpen() && StopCondition.hitpointsPercent(state) <= percent && food(state) != null;
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        int food = food(state);
        if (!act.useItem(food, "Eat")) {
            throw new IllegalStateException("could not eat item " + food);
        }
        percent = roll();
        return new WaitFor("eat", WAIT_TICKS,
                (before, now) -> now.hitpoints() > before.hitpoints() || now.count(food) < before.count(food));
    }

    private Integer food(GameState state) {
        for (int id : foodIds) {
            if (state.count(id) > 0) {
                return id;
            }
        }
        return null;
    }
}
