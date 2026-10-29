package net.runelite.client.plugins.microbot.fourtify.core.tasks.guards;

import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.Random;

/**
 * Turns run on once run energy is back to a level rolled anew in its range each time. Run switches itself
 * off at 0 energy, and running covers 2 tiles a tick instead of 1, so walks to the bank and back take about half
 * as long. Waiting for some energy before switching on keeps it from flicking on and off at empty; the level
 * is our choice, not a game value.
 */
public class EnableRun implements Task {
    static final int WAIT_TICKS = 2;

    private final int lowEnergy;
    private final int highEnergy;
    private final Random random;
    private int minEnergy;

    public EnableRun(int lowEnergy, int highEnergy, Random random) {
        this.lowEnergy = lowEnergy;
        this.highEnergy = highEnergy;
        this.random = random;
        this.minEnergy = roll();
    }

    private int roll() {
        return lowEnergy + random.nextInt(highEnergy - lowEnergy + 1);
    }

    @Override
    public boolean shouldRun(GameState state) {
        return !state.isRunEnabled() && state.getRunEnergy() >= minEnergy;
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        act.setRun(true);
        minEnergy = roll();
        return new WaitFor("turn run on", WAIT_TICKS, (before, now) -> now.isRunEnabled());
    }
}
